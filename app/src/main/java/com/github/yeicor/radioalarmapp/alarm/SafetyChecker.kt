package com.github.yeicor.radioalarmapp.alarm

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import com.github.yeicor.radioalarmapp.audio.LocalAudio
import com.github.yeicor.radioalarmapp.audio.StreamProbe
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AlarmOverrides
import com.github.yeicor.radioalarmapp.data.AlarmRepository
import com.github.yeicor.radioalarmapp.data.RadioAlarmPrefs
import com.github.yeicor.radioalarmapp.util.Permissions
import com.github.yeicor.radioalarmapp.util.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime

enum class CheckSeverity { OK, NOTICE, WARNING, CRITICAL }

enum class SafetyFix {
    NONE, EXACT_ALARMS, NOTIFICATIONS, EDIT_ALARM, CHARGE, ALARM_VOLUME,
}

data class SafetyIssue(
    val id: String,
    val severity: CheckSeverity,
    val title: String,
    val message: String,
    val fix: SafetyFix = SafetyFix.NONE,
    val fixLabel: String = "",
)

data class BatteryState(val percent: Int, val charging: Boolean)

data class AlarmVolumeState(
    val index: Int,
    val maxIndex: Int,
    val percent: Int,
    val muted: Boolean,
) {
    val audible: Boolean get() = percent > 0 && !muted
}

data class SafetyReport(
    val checkedAt: Long,
    val nextAlarmId: Long?,
    val nextAlarmAt: Long?,
    val nextAlarmLabel: String,
    val batteryPercent: Int?,
    val charging: Boolean,
    val alarmVolumePercent: Int?,
    val alarmVolumeMuted: Boolean,
    val streamReachable: Boolean?,
    val localFallbackCount: Int,
    val fallbackSummary: String,
    val issues: List<SafetyIssue>,
) {
    val criticalCount: Int get() = issues.count { it.severity == CheckSeverity.CRITICAL }
    val warningCount: Int get() = issues.count { it.severity >= CheckSeverity.WARNING }
    val isReady: Boolean get() = criticalCount == 0 && warningCount == 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("checkedAt", checkedAt)
        put("nextAlarmId", nextAlarmId ?: -1L)
        put("nextAlarmAt", nextAlarmAt ?: -1L)
        put("nextAlarmLabel", nextAlarmLabel)
        put("batteryPercent", batteryPercent ?: -1)
        put("charging", charging)
        put("alarmVolumePercent", alarmVolumePercent ?: -1)
        put("alarmVolumeMuted", alarmVolumeMuted)
        put("streamReachable", streamReachable?.toString() ?: "null")
        put("localFallbackCount", localFallbackCount)
        put("fallbackSummary", fallbackSummary)
        put("issues", JSONArray().apply {
            issues.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("severity", it.severity.name)
                    put("title", it.title)
                    put("message", it.message)
                    put("fix", it.fix.name)
                    put("fixLabel", it.fixLabel)
                })
            }
        })
    }

    companion object {
        fun fromJson(raw: String?): SafetyReport? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)
                val issues = mutableListOf<SafetyIssue>()
                json.optJSONArray("issues")?.let { array ->
                    for (i in 0 until array.length()) {
                        val issue = array.optJSONObject(i) ?: continue
                        issues += SafetyIssue(
                            id = issue.optString("id"),
                            severity = runCatching { CheckSeverity.valueOf(issue.optString("severity")) }.getOrDefault(CheckSeverity.NOTICE),
                            title = issue.optString("title"),
                            message = issue.optString("message"),
                            fix = runCatching { SafetyFix.valueOf(issue.optString("fix")) }.getOrDefault(SafetyFix.NONE),
                            fixLabel = issue.optString("fixLabel"),
                        )
                    }
                }
                SafetyReport(
                    checkedAt = json.optLong("checkedAt"),
                    nextAlarmId = json.optLong("nextAlarmId", -1).takeIf { it > 0 },
                    nextAlarmAt = json.optLong("nextAlarmAt", -1).takeIf { it > 0 },
                    nextAlarmLabel = json.optString("nextAlarmLabel"),
                    batteryPercent = json.optInt("batteryPercent", -1).takeIf { it >= 0 },
                    charging = json.optBoolean("charging"),
                    alarmVolumePercent = json.optInt("alarmVolumePercent", -1).takeIf { it >= 0 },
                    alarmVolumeMuted = json.optBoolean("alarmVolumeMuted"),
                    streamReachable = when (json.optString("streamReachable")) {
                        "null" -> null
                        "true" -> true
                        else -> false
                    },
                    localFallbackCount = json.optInt("localFallbackCount"),
                    fallbackSummary = json.optString("fallbackSummary"),
                    issues = issues,
                )
            }.getOrNull()
        }
    }
}

object SafetyChecker {
    private const val KEY_LAST_REPORT = "last_safety_report"
    private const val KEY_LAST_CHECK = "last_safety_check"

    private val mutex = Mutex()
    private val _report = MutableStateFlow<SafetyReport?>(null)
    val report: StateFlow<SafetyReport?> = _report.asStateFlow()

    fun initialize(context: Context) {
        if (_report.value == null) {
            _report.value = SafetyReport.fromJson(RadioAlarmPrefs.get(context).getString(KEY_LAST_REPORT, null))
        }
    }

    suspend fun checkAndNotify(
        context: Context,
        notify: Boolean,
        forceStreamCheck: Boolean = true,
    ): SafetyReport = mutex.withLock {
        val result = buildReport(context, forceStreamCheck)
        val prefs = RadioAlarmPrefs.get(context)
        prefs.edit()
            .putString(KEY_LAST_REPORT, result.toJson().toString())
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .commit()
        _report.value = result
        if (result.warningCount == 0 || result.nextAlarmAt == null) {
            NotificationHelper.cancelSafetyWarning(context)
        } else if (notify) {
            NotificationHelper.showSafetyWarning(context, result)
        }
        result
    }

    suspend fun checkNow(context: Context, forceStreamCheck: Boolean = true): SafetyReport {
        val result = checkAndNotify(context, notify = false, forceStreamCheck = forceStreamCheck)
        return result
    }

    fun lastCheckedAt(context: Context): Long =
        RadioAlarmPrefs.get(context).getLong(KEY_LAST_CHECK, 0L)

    private suspend fun buildReport(context: Context, checkStream: Boolean): SafetyReport =
        withContext(Dispatchers.IO) {
            val repo = AlarmRepository.get(context)
            val now = ZonedDateTime.now()
            val alarm = AlarmOverrides.next(context, repo.alarms.value, now)?.first
            val issues = mutableListOf<SafetyIssue>()
            val battery = readBattery(context)
            val alarmVolume = readAlarmVolume(context)

            if (alarm != null) {
                val nextOccurrence = AlarmOverrides.effectiveOccurrence(context, alarm, now)

                if (!alarmVolume.audible) {
                    issues += SafetyIssue(
                        "alarm_volume_zero", CheckSeverity.CRITICAL,
                        if (alarmVolume.muted) "Alarm volume is muted" else "Alarm volume is at zero",
                        "Every sound source would be silent. Raise the alarm volume.",
                        SafetyFix.ALARM_VOLUME, "Raise alarm volume",
                    )
                } else if (alarmVolume.percent < 30) {
                    issues += SafetyIssue(
                        "alarm_volume_low", CheckSeverity.WARNING,
                        "Alarm volume is low (${alarmVolume.percent}%)",
                        "Raise it so the alarm is reliably loud.",
                        SafetyFix.ALARM_VOLUME, "Raise alarm volume",
                    )
                }

                if (!Permissions.canScheduleExactAlarms(context)) {
                    issues += SafetyIssue(
                        "exact_alarm", CheckSeverity.CRITICAL,
                        "Exact alarms are blocked",
                        "Android may delay or skip the alarm.",
                        SafetyFix.EXACT_ALARMS, "Allow exact alarms",
                    )
                }
                if (!Permissions.notificationsEnabled(context)) {
                    issues += SafetyIssue(
                        "notifications", CheckSeverity.CRITICAL,
                        "Notifications are off",
                        "The ringing screen and snooze controls cannot appear.",
                        SafetyFix.NOTIFICATIONS, "Turn on notifications",
                    )
                }
                if (battery.percent in 0..24 && !battery.charging) {
                    val hoursAway = ((nextOccurrence?.toInstant()?.toEpochMilli() ?: 0L) -
                        System.currentTimeMillis()) / 3_600_000L
                    if (hoursAway in 0..24) {
                        issues += SafetyIssue(
                            "battery", CheckSeverity.CRITICAL,
                            "Battery is low (${battery.percent}%)",
                            "Alarm is ${TimeText.until(nextOccurrence!!)} away. Charge before sleeping.",
                            SafetyFix.CHARGE, "Charge your phone",
                        )
                    }
                }

                val local = LocalAudio.resolve(context, alarm, repo.settings.value)
                if (alarm.localFallbackUris.isNotEmpty() || repo.settings.value.fallbackFolderUri != null) {
                    if (local.isEmpty()) {
                        issues += SafetyIssue(
                            "local_backup", CheckSeverity.WARNING,
                            "Local backup is unavailable",
                            "The selected files or folder cannot be read. " +
                                if (alarm.useSystemFallback) {
                                    "Phone alarm and built-in sounds remain."
                                } else {
                                    "The built-in sound remains."
                                },
                            SafetyFix.EDIT_ALARM, "Review backup audio",
                        )
                    }
                }

                val fallbackParts = buildList {
                    if (local.isNotEmpty()) add("${local.size} local track${if (local.size == 1) "" else "s"}")
                    if (alarm.useSystemFallback) add("phone alarm sound")
                    add("built-in Radio Alarm App sound")
                }
                val fallbackSummary = fallbackParts.joinToString(", ")

                var streamReachable: Boolean? = null
                if (alarm.streamUrl.isNotBlank()) {
                    if (checkStream) {
                        val probe = StreamProbe.probe(alarm.streamUrl, 7_000)
                        streamReachable = probe.ok
                        if (!probe.ok) {
                            issues += SafetyIssue(
                                "stream", CheckSeverity.WARNING,
                                "Radio stream is unreachable",
                                "${probe.detail}. Fallbacks ready: $fallbackSummary.",
                                SafetyFix.EDIT_ALARM, "Review alarm",
                            )
                        }
                    }
                } else if (local.isEmpty()) {
                    issues += SafetyIssue(
                        "no_primary", CheckSeverity.NOTICE,
                        "No radio or local music selected",
                        if (alarm.useSystemFallback) {
                            "Phone alarm and built-in sounds will be used."
                        } else {
                            "The built-in sound will be used."
                        },
                        SafetyFix.EDIT_ALARM, "Add audio",
                    )
                }

                val nextAt = nextOccurrence?.toInstant()?.toEpochMilli()
                return@withContext SafetyReport(
                    checkedAt = System.currentTimeMillis(),
                    nextAlarmId = alarm.id,
                    nextAlarmAt = nextAt,
                    nextAlarmLabel = "${alarm.label.ifBlank { "Alarm" }} at " +
                        (nextOccurrence?.format(Alarm.TIME_FORMAT) ?: alarm.timeLabel()),
                    batteryPercent = battery.percent,
                    charging = battery.charging,
                    alarmVolumePercent = alarmVolume.percent,
                    alarmVolumeMuted = alarmVolume.muted,
                    streamReachable = streamReachable,
                    localFallbackCount = local.size,
                    fallbackSummary = fallbackSummary,
                    issues = issues.sortedByDescending { it.severity },
                )
            }

            SafetyReport(
                checkedAt = System.currentTimeMillis(),
                nextAlarmId = null,
                nextAlarmAt = null,
                nextAlarmLabel = "",
                batteryPercent = battery.percent,
                charging = battery.charging,
                alarmVolumePercent = alarmVolume.percent,
                alarmVolumeMuted = alarmVolume.muted,
                streamReachable = null,
                localFallbackCount = 0,
                fallbackSummary = "phone alarm sound, built-in Radio Alarm App sound",
                issues = emptyList(),
            )
        }

    fun readBattery(context: Context): BatteryState {        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return BatteryState(percent, charging)
    }

    fun readAlarmVolume(context: Context): AlarmVolumeState {
        val manager = context.getSystemService(AudioManager::class.java)
        val maxIndex = manager?.getStreamMaxVolume(AudioManager.STREAM_ALARM)?.coerceAtLeast(1) ?: 1
        val index = manager?.getStreamVolume(AudioManager.STREAM_ALARM)?.coerceIn(0, maxIndex) ?: 0
        val muted = runCatching { manager?.isStreamMute(AudioManager.STREAM_ALARM) == true }
            .getOrDefault(false)
        return AlarmVolumeState(
            index = index,
            maxIndex = maxIndex,
            percent = index * 100 / maxIndex,
            muted = muted,
        )
    }

    fun nextAlarmInstant(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Instant? =
        alarm.nextOccurrence(now)?.toInstant()
}
