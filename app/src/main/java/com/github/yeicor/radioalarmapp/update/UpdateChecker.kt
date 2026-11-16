package com.github.yeicor.radioalarmapp.update

import android.content.Context
import com.github.yeicor.radioalarmapp.BuildConfig
import com.github.yeicor.radioalarmapp.data.RadioAlarmPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

enum class UpdateChannel { STABLE, NIGHTLY }

data class UpdateInfo(
    val channel: UpdateChannel,
    val versionLabel: String,  // e.g. "v0.0.6" or "nightly #42 (2026-11-15)"
    val downloadUrl: String,   // direct APK URL for stable; direct nightly.link ZIP URL for nightly
    val isNewer: Boolean,
)

object UpdateChecker {
    const val OWNER = "yeicor"
    const val REPO = "radio-alarm-app"
    private const val MIN_CHECK_INTERVAL_MS = 15 * 60 * 1000L
    private const val KEY_LAST_CHECK = "update_last_check_at"
    private const val KEY_DISMISSED_VERSION = "update_dismissed_version"
    private const val KEY_LAST_STABLE_PUBLISHED_AT = "update_last_stable_published_at"
    private const val KEY_LAST_STABLE_TAG = "update_last_stable_tag"

    data class StableDetails(
        val tag: String,
        val publishedAtMs: Long,
        val apkUrl: String,
    )

    data class NightlyDetails(
        val label: String,
        val runId: Long,
        val headSha: String,
        val createdAtMs: Long,
        val downloadUrl: String,
    )

    suspend fun check(
        context: Context,
        channel: UpdateChannel,
        force: Boolean = false,
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!force && !shouldCheck(context)) return@withContext null
        markChecked(context)
        try {
            when (channel) {
                UpdateChannel.STABLE -> checkStable(context)
                UpdateChannel.NIGHTLY -> checkNightly(context)
            }
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

    fun parseIsoDate(iso: String): Long = runCatching {
        Instant.parse(iso).toEpochMilli()
    }.getOrDefault(0L)

    fun fetchStableDetails(): StableDetails? {
        val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "RadioAlarmApp/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        return try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "")
            if (tag.isBlank()) return null
            val publishedAt = json.optString("published_at", "")
            val publishedAtMs = parseIsoDate(publishedAt)
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
            StableDetails(tag, publishedAtMs, apkUrl)
        } finally {
            conn.disconnect()
        }
    }

    fun isStableNewer(
        releaseTag: String,
        releasePublishedAtMs: Long,
        localVersionName: String,
        localBuildTimeMs: Long,
    ): Boolean {
        val tagNewer = isTagNewer(releaseTag, localVersionName)

        // When jumping from nightly to stable:
        // If the stable release was published before our current build was compiled, it's an older release.
        if (releasePublishedAtMs > 0L && localBuildTimeMs > 0L) {
            if (releasePublishedAtMs < localBuildTimeMs - 60_000L) {
                return false
            }
        }

        if (tagNewer) return true

        // If same version tag, only newer if official release was published after an early nightly build
        if (releasePublishedAtMs > 0L && localBuildTimeMs > 0L &&
            releasePublishedAtMs > localBuildTimeMs + 60_000L &&
            releaseTag.trimStart('v') == localVersionName.trimStart('v')
        ) {
            return true
        }

        return false
    }

    private fun checkStable(context: Context): UpdateInfo? {
        val details = fetchStableDetails() ?: return null
        RadioAlarmPrefs.get(context).edit()
            .putLong(KEY_LAST_STABLE_PUBLISHED_AT, details.publishedAtMs)
            .putString(KEY_LAST_STABLE_TAG, details.tag)
            .apply()

        val isNewer = isStableNewer(
            releaseTag = details.tag,
            releasePublishedAtMs = details.publishedAtMs,
            localVersionName = BuildConfig.VERSION_NAME,
            localBuildTimeMs = BuildConfig.BUILD_TIME,
        )
        val dismissed = isDismissed(context, details.tag)
        return UpdateInfo(UpdateChannel.STABLE, details.tag, details.apkUrl, isNewer && !dismissed)
    }

    fun isNightlyNewer(
        artifactCreatedAtMs: Long,
        artifactHeadSha: String,
        localBuildTimeMs: Long,
        localGitCommitSha: String,
        latestStablePublishedAtMs: Long,
        localVersionName: String,
        latestStableTag: String,
    ): Boolean {
        // 1. Same commit SHA is never an update
        if (artifactHeadSha.isNotBlank() && localGitCommitSha.isNotBlank() &&
            artifactHeadSha.equals(localGitCommitSha, ignoreCase = true)
        ) {
            return false
        }

        // 2. Artifact created before or at our build compilation time is not newer
        if (artifactCreatedAtMs > 0L && localBuildTimeMs > 0L &&
            artifactCreatedAtMs <= localBuildTimeMs + 60_000L
        ) {
            return false
        }

        // 3. Jumping from stable to nightly: don't detect the same released version as a nightly update.
        // If user is on the latest stable release (or newer), and this nightly was created around or
        // before that release was published (e.g. within 5-minute build window)
        val onLatestStable = !isTagNewer(latestStableTag, localVersionName)
        if (onLatestStable && latestStablePublishedAtMs > 0L && artifactCreatedAtMs > 0L) {
            if (artifactCreatedAtMs <= latestStablePublishedAtMs + 5 * 60 * 1000L) {
                return false
            }
        }

        return true
    }

    private fun checkNightly(context: Context): UpdateInfo? {
        val prefs = RadioAlarmPrefs.get(context)
        var stablePublishedAt = prefs.getLong(KEY_LAST_STABLE_PUBLISHED_AT, 0L)
        var stableTag = prefs.getString(KEY_LAST_STABLE_TAG, null)

        // If not cached yet, fetch stable release details to know the latest release date
        if (stablePublishedAt == 0L || stableTag == null) {
            fetchStableDetails()?.let {
                stablePublishedAt = it.publishedAtMs
                stableTag = it.tag
                prefs.edit()
                    .putLong(KEY_LAST_STABLE_PUBLISHED_AT, it.publishedAtMs)
                    .putString(KEY_LAST_STABLE_TAG, it.tag)
                    .apply()
            }
        }

        // 1. First check if a rolling nightly release exists on GitHub with direct APK download
        val directReleaseInfo = checkNightlyRelease(context, stablePublishedAt, stableTag)
        if (directReleaseInfo != null) return directReleaseInfo

        // 2. Fall back to CI workflow artifacts via nightly.link
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
                val headSha = workflowRun.optString("head_sha", "")
                val createdAt = art.optString("created_at", "")
                val createdAtMs = parseIsoDate(createdAt)
                val runNumber = workflowRun.optInt("run_number", 0)
                val label = if (runNumber > 0) "nightly #$runNumber (${createdAt.take(10)})" else "nightly (${createdAt.take(10)})"

                // Direct download access to CI artifact via nightly.link
                val downloadUrl = if (runId > 0L) {
                    "https://nightly.link/$OWNER/$REPO/actions/runs/$runId/radio-alarm-app-debug.zip"
                } else {
                    "https://nightly.link/$OWNER/$REPO/workflows/ci/main/radio-alarm-app-debug.zip"
                }

                val isNewer = isNightlyNewer(
                    artifactCreatedAtMs = createdAtMs,
                    artifactHeadSha = headSha,
                    localBuildTimeMs = BuildConfig.BUILD_TIME,
                    localGitCommitSha = BuildConfig.GIT_COMMIT_SHA,
                    latestStablePublishedAtMs = stablePublishedAt,
                    localVersionName = BuildConfig.VERSION_NAME,
                    latestStableTag = stableTag ?: BuildConfig.VERSION_NAME,
                )
                val dismissed = isDismissed(context, label)
                return UpdateInfo(UpdateChannel.NIGHTLY, label, downloadUrl, isNewer && !dismissed)
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun checkNightlyRelease(
        context: Context,
        stablePublishedAt: Long,
        stableTag: String?,
    ): UpdateInfo? {
        val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases/tags/nightly")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "RadioAlarmApp/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        return try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val publishedAt = json.optString("published_at", "")
            val publishedAtMs = parseIsoDate(publishedAt)
            val commitSha = json.optString("target_commitish", "")
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
            if (apkUrl.isNullOrBlank()) return null
            val label = "nightly (${publishedAt.take(10)})"
            val isNewer = isNightlyNewer(
                artifactCreatedAtMs = publishedAtMs,
                artifactHeadSha = commitSha,
                localBuildTimeMs = BuildConfig.BUILD_TIME,
                localGitCommitSha = BuildConfig.GIT_COMMIT_SHA,
                latestStablePublishedAtMs = stablePublishedAt,
                localVersionName = BuildConfig.VERSION_NAME,
                latestStableTag = stableTag ?: BuildConfig.VERSION_NAME,
            )
            val dismissed = isDismissed(context, label)
            UpdateInfo(UpdateChannel.NIGHTLY, label, apkUrl, isNewer && !dismissed)
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun isTagNewer(tag: String, currentVersion: String): Boolean {
        if (tag.isBlank() || currentVersion.isBlank()) return false
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
