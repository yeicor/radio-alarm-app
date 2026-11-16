package com.github.yeicor.radioalarmapp.update

import android.content.Context
import com.github.yeicor.radioalarmapp.BuildConfig
import com.github.yeicor.radioalarmapp.data.RadioAlarmPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateChannel { STABLE, NIGHTLY }

data class UpdateInfo(
    val channel: UpdateChannel,
    val versionLabel: String,  // e.g. "v0.0.6" or "nightly #42 (2026-11-15)"
    val downloadUrl: String,   // direct APK URL for stable; GH Actions run URL for nightly
    val isNewer: Boolean,
)

object UpdateChecker {
    private const val OWNER = "yeicor"
    private const val REPO = "radio-alarm-app"
    private const val MIN_CHECK_INTERVAL_MS = 15 * 60 * 1000L
    private const val KEY_LAST_CHECK = "update_last_check_at"
    private const val KEY_DISMISSED_VERSION = "update_dismissed_version"
    private const val KEY_LAST_SEEN_NIGHTLY = "update_last_seen_nightly"

    suspend fun check(
        context: Context,
        channel: UpdateChannel,
        force: Boolean = false,
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!force && !shouldCheck(context)) return@withContext null
        markChecked(context)
        try {
            val info = when (channel) {
                UpdateChannel.STABLE -> checkStable(context)
                UpdateChannel.NIGHTLY -> checkNightly(context)
            }
            info
        } catch (_: Exception) {
            null
        }
    }

    fun shouldCheck(context: Context): Boolean {
        val last = RadioAlarmPrefs.get(context).getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > MIN_CHECK_INTERVAL_MS
    }

    fun markChecked(context: Context) {
        RadioAlarmPrefs.get(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun isDismissed(context: Context, versionLabel: String): Boolean {
        val dismissed = RadioAlarmPrefs.get(context).getString(KEY_DISMISSED_VERSION, null)
        return dismissed == versionLabel
    }

    fun dismiss(context: Context, versionLabel: String) {
        RadioAlarmPrefs.get(context).edit().putString(KEY_DISMISSED_VERSION, versionLabel).apply()
    }

    private fun checkStable(context: Context): UpdateInfo? {
        val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "RadioAlarmApp/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        return try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "") // e.g. "v0.0.6"
            if (tag.isBlank()) return null
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith("-release.apk") || name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        if (name.endsWith("-release.apk")) break
                    }
                }
            }
            if (apkUrl.isBlank()) apkUrl = "https://github.com/$OWNER/$REPO/releases/tag/$tag"
            val isNewer = isTagNewer(tag, BuildConfig.VERSION_NAME)
            val dismissed = isDismissed(context, tag)
            UpdateInfo(UpdateChannel.STABLE, tag, apkUrl, isNewer && !dismissed)
        } finally {
            conn.disconnect()
        }
    }

    private fun checkNightly(context: Context): UpdateInfo? {
        val url = URL("https://api.github.com/repos/$OWNER/$REPO/actions/artifacts?name=radio-alarm-app-debug&per_page=5")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "RadioAlarmApp/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        return try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val artifacts = json.optJSONArray("artifacts") ?: return null
            for (i in 0 until artifacts.length()) {
                val art = artifacts.getJSONObject(i)
                if (art.optBoolean("expired", true)) continue
                val workflowRun = art.optJSONObject("workflow_run") ?: continue
                val branch = workflowRun.optString("head_branch", "")
                if (branch != "main") continue
                val runId = workflowRun.optLong("id", 0L)
                if (runId == 0L) continue
                val createdAt = art.optString("created_at", "")
                val runNumber = workflowRun.optInt("run_number", 0)
                val label = "nightly #$runNumber (${createdAt.take(10)})"
                val runUrl = "https://github.com/$OWNER/$REPO/actions/runs/$runId"

                val prefs = RadioAlarmPrefs.get(context)
                val lastSeenNightly = prefs.getString(KEY_LAST_SEEN_NIGHTLY, null)
                val dismissed = isDismissed(context, label)
                val isNewer = label != lastSeenNightly && !dismissed
                return UpdateInfo(UpdateChannel.NIGHTLY, label, runUrl, isNewer)
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    fun isTagNewer(tag: String, currentVersion: String): Boolean {
        val tagParts = tag.trimStart('v').split(".").mapNotNull { it.toIntOrNull() }
        val curParts = currentVersion.trimStart('v').split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(tagParts.size, curParts.size)) {
            val t = tagParts.getOrElse(i) { 0 }
            val c = curParts.getOrElse(i) { 0 }
            if (t > c) return true
            if (t < c) return false
        }
        return false
    }
}
