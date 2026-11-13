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
    private val audioExtensions = setOf(
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "mid", "midi", "3gp", "mp4", "ts", "m4b",
    )

    fun resolve(context: Context, alarm: Alarm, settings: AppSettings): List<ResolvedAudio> {
        val configured = alarm.localFallbackUris
            .ifEmpty { listOfNotNull(settings.fallbackFolderUri) }
        if (configured.isEmpty()) return emptyList()

        val found = linkedMapOf<String, ResolvedAudio>()
        configured.forEach { raw ->
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@forEach
            runCatching {
                if (isTreeUri(uri)) {
                    collectFromTree(context, uri, found, depth = 0)
                } else {
                    collectSingle(context, uri, found)
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

    fun describeConfigured(context: Context, alarm: Alarm, settings: AppSettings): String {
        val resolved = resolve(context, alarm, settings)
        if (alarm.localFallbackUris.isEmpty() && settings.fallbackFolderUri != null) {
            val folder = displayName(context, settings.fallbackFolderUri)
            return if (resolved.isEmpty()) "$folder (unavailable)" else "$folder · ${resolved.size} track${if (resolved.size == 1) "" else "s"}"
        }
        if (alarm.localFallbackUris.isEmpty()) return "Not configured"
        if (resolved.isEmpty()) return "Selected backups are unavailable"
        val first = alarm.localFallbackUris.firstOrNull()?.let { displayName(context, it) } ?: "Local audio"
        val extra = alarm.localFallbackUris.size - 1
        return if (extra > 0) "$first +$extra more" else "$first · ${resolved.size} track${if (resolved.size == 1) "" else "s"}"
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
        uri: Uri,
        found: MutableMap<String, ResolvedAudio>,
        depth: Int,
    ) {
        if (depth > 2) return
        val root = DocumentFile.fromTreeUri(context, uri) ?: return
        val children = runCatching { root.listFiles() }.getOrNull() ?: return
        children
            .filter { !it.name.isNullOrBlank() }
            .sortedBy { it.name?.lowercase() }
            .forEach { child ->
                if (child.isDirectory) {
                    collectFromTree(context, child.uri, found, depth + 1)
                } else if (child.isFile && isAudio(child.name, child.type)) {
                    found[child.uri.toString()] = ResolvedAudio(child.uri, child.name ?: "Local audio")
                }
            }
    }

    private fun collectSingle(context: Context, uri: Uri, found: MutableMap<String, ResolvedAudio>) {
        val doc = DocumentFile.fromSingleUri(context, uri)
        val name = queryName(context, uri) ?: doc?.name ?: uri.lastPathSegment ?: "Local audio"
        val type = context.contentResolver.getType(uri)
        if (doc?.exists() == false) return
        if (doc?.isDirectory == true) {
            collectFromTree(context, uri, found, depth = 0)
            return
        }
        if (isAudio(name, type) || doc?.isFile == true) {
            found[uri.toString()] = ResolvedAudio(uri, name)
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
