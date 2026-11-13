package com.github.yeicor.radioalarmapp.audio

import android.content.Context
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AppSettings

data class ResolvedAudio(val uri: Uri, val label: String)

object LocalAudio {
    /** Runtime tries at most this many local tracks before moving to the phone alarm sound. */
    const val MAX_LOCAL_ATTEMPTS = 30

    /**
     * Folder scans are bounded: we stop after finding enough tracks or inspecting a
     * reasonable number of entries. A folder with thousands of songs is never fully
     * enumerated just to play the alarm.
     */
    private const val MAX_SCAN_ENTRIES = 1_000
    private const val MAX_FOLDER_DEPTH = 2

    private val audioExtensions = setOf(
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "mid", "midi", "3gp", "mp4", "ts", "m4b",
    )

    /**
     * Returns up to [limit] local candidates, without walking the whole folder. The
     * first entry is enough for the periodic check; runtime uses the rest for retries.
     */
    fun candidates(
        context: Context,
        alarm: Alarm,
        settings: AppSettings,
        limit: Int = MAX_LOCAL_ATTEMPTS,
    ): List<ResolvedAudio> {
        val configured = alarm.localFallbackUris
            .ifEmpty { listOfNotNull(settings.fallbackFolderUri) }
        if (configured.isEmpty() || limit <= 0) return emptyList()

        val found = linkedMapOf<String, ResolvedAudio>()
        configured.forEach { raw ->
            if (found.size >= limit) return@forEach
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@forEach
            runCatching {
                if (isTreeUri(uri)) {
                    collectFromTree(context, uri, found, limit)
                } else {
                    collectSingle(context, uri, found, limit)
                }
            }
        }
        return found.values.toList()
    }

    fun displayName(context: Context, rawUri: String): String {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return "Audio"
        return runCatching {
            if (isTreeUri(uri)) {
                val doc = DocumentFile.fromTreeUri(context, uri)
                doc?.name ?: "Folder"
            } else {
                queryName(context, uri) ?: uri.lastPathSegment ?: "Audio"
            }
        }.getOrDefault("Audio")
    }

    fun systemSources(context: Context, settings: AppSettings): List<ResolvedAudio> {
        val candidates = linkedSetOf<Uri>()
        settings.systemAlarmSoundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let(candidates::add)
        listOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_NOTIFICATION,
            RingtoneManager.TYPE_RINGTONE,
        ).forEach { type ->
            runCatching { RingtoneManager.getDefaultUri(type) }.getOrNull()?.let(candidates::add)
        }
        return candidates.map { ResolvedAudio(it, "Phone alarm sound") }
    }

    fun embeddedSource(context: Context): ResolvedAudio =
        ResolvedAudio(
            Uri.parse("android.resource://${context.packageName}/${com.github.yeicor.radioalarmapp.R.raw.fallback_alarm}"),
            "Built-in Radio Alarm App sound",
        )

    internal fun isTreeUri(uri: Uri): Boolean = runCatching { DocumentsContract.isTreeUri(uri) }.getOrDefault(false)

    private fun collectFromTree(
        context: Context,
        treeUri: Uri,
        found: MutableMap<String, ResolvedAudio>,
        limit: Int,
    ) {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootId to 0)
        var scanned = 0

        while (queue.isNotEmpty() && found.size < limit && scanned < MAX_SCAN_ENTRIES) {
            val (documentId, depth) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = runCatching {
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null,
                    null,
                    null,
                )
            }.getOrNull() ?: continue

            cursor.use {
                while (it.moveToNext() && found.size < limit && scanned < MAX_SCAN_ENTRIES) {
                    scanned++
                    val childId = it.getString(0) ?: continue
                    val name = it.getString(1)
                    val mime = it.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_FOLDER_DEPTH) queue.add(childId to depth + 1)
                    } else if (isAudio(name, mime)) {
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        found[childUri.toString()] = ResolvedAudio(childUri, name ?: "Local audio")
                    }
                }
            }
        }
    }

    private fun collectSingle(
        context: Context,
        uri: Uri,
        found: MutableMap<String, ResolvedAudio>,
        limit: Int,
    ) {
        val name = queryName(context, uri) ?: uri.lastPathSegment ?: "Local audio"
        val type = context.contentResolver.getType(uri)
        if (isAudio(name, type)) {
            found[uri.toString()] = ResolvedAudio(uri, name)
            return
        }
        val doc = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
        if (doc?.isDirectory == true) {
            collectDirectoryFallback(doc, found, limit, depth = 0)
        } else if (doc?.isFile == true) {
            found[uri.toString()] = ResolvedAudio(uri, name)
        }
    }

    private fun collectDirectoryFallback(
        directory: DocumentFile,
        found: MutableMap<String, ResolvedAudio>,
        limit: Int,
        depth: Int,
    ) {
        if (depth > MAX_FOLDER_DEPTH || found.size >= limit) return
        val children = runCatching { directory.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (found.size >= limit) return
            val childName = child.name ?: continue
            if (child.isDirectory) {
                collectDirectoryFallback(child, found, limit, depth + 1)
            } else if (child.isFile && isAudio(childName, child.type)) {
                found[child.uri.toString()] = ResolvedAudio(child.uri, childName)
            }
        }
    }

    private fun isAudio(name: String?, mime: String?): Boolean {
        if (mime?.startsWith("audio/") == true || mime == "application/ogg") return true
        val extension = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return extension in audioExtensions
    }

    private fun queryName(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }
}
