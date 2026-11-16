package com.github.yeicor.radioalarmapp.alarm

import org.json.JSONArray
import org.json.JSONObject

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
