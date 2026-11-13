package com.github.yeicor.radioalarmapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class Alarm(
    val id: Long = 0L,
    val label: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    /** ISO day numbers, Monday = 1 ... Sunday = 7. Empty means a one-time alarm. */
    val repeatDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val enabled: Boolean = true,
    val streamUrl: String = DEFAULT_STREAM_URL,
    val localFallbackUris: List<String> = emptyList(),
    val rampSeconds: Int = 30,
    /** Legacy field kept for saved alarms. Playback always ramps its player volume to 100%. */
    val targetVolumePercent: Int = 90,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val vibrate: Boolean = true,
    val overrideDnd: Boolean = true,
    val useSystemFallback: Boolean = true,
    val createdAt: Long = 0L,
) {
    val isOneTime: Boolean get() = repeatDays.isEmpty()

    fun repeatLabel(): String = when {
        repeatDays.isEmpty() -> "Once"
        repeatDays.size == 7 -> "Every day"
        repeatDays == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        repeatDays == setOf(6, 7) -> "Weekends"
        else -> repeatDays.sorted().joinToString(" ") {
            DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }

    fun timeLabel(): String = LocalTime.of(hour, minute).format(TIME_FORMAT)

    fun streamHost(): String = streamUrl.substringAfter("://", streamUrl).substringBefore("/")

    fun nextOccurrence(after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val today = after.toLocalDate()
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            val candidate = date.atTime(hour, minute).atZone(after.zone)
            if (!candidate.isAfter(after)) continue
            if (repeatDays.isEmpty() || candidate.dayOfWeek.value in repeatDays) return candidate
        }
        return null
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("hour", hour)
        put("minute", minute)
        put("repeatDays", JSONArray(repeatDays.sorted()))
        put("enabled", enabled)
        put("streamUrl", streamUrl)
        put("localFallbackUris", JSONArray(localFallbackUris))
        put("rampSeconds", rampSeconds)
        put("targetVolumePercent", targetVolumePercent)
        put("snoozeMinutes", snoozeMinutes)
        put("maxSnoozes", maxSnoozes)
        put("vibrate", vibrate)
        put("overrideDnd", overrideDnd)
        put("useSystemFallback", useSystemFallback)
        put("createdAt", createdAt)
    }

    fun toJsonString(): String = toJson().toString()

    companion object {
        const val DEFAULT_STREAM_URL = "https://ice1.somafm.com/groovesalad-128-mp3"

        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun fromJson(json: JSONObject): Alarm {
            val days = mutableSetOf<Int>()
            json.optJSONArray("repeatDays")?.let { array ->
                for (i in 0 until array.length()) days += array.optInt(i)
            }
            val uris = mutableListOf<String>()
            json.optJSONArray("localFallbackUris")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(uris::add)
                }
            }
            return Alarm(
                id = json.optLong("id", 0L),
                label = json.optString("label", ""),
                hour = json.optInt("hour", 7).coerceIn(0, 23),
                minute = json.optInt("minute", 0).coerceIn(0, 59),
                repeatDays = days,
                enabled = json.optBoolean("enabled", true),
                streamUrl = json.optString("streamUrl", DEFAULT_STREAM_URL),
                localFallbackUris = uris,
                rampSeconds = json.optInt("rampSeconds", 30).coerceIn(0, 300),
                targetVolumePercent = json.optInt("targetVolumePercent", 90).coerceIn(10, 100),
                snoozeMinutes = json.optInt("snoozeMinutes", 5).coerceIn(1, 60),
                maxSnoozes = json.optInt("maxSnoozes", 3).coerceIn(0, 10),
                vibrate = json.optBoolean("vibrate", true),
                overrideDnd = json.optBoolean("overrideDnd", true),
                useSystemFallback = json.optBoolean("useSystemFallback", true),
                createdAt = json.optLong("createdAt", 0L),
            )
        }

        fun fromJsonString(raw: String?): Alarm? = runCatching {
            if (raw.isNullOrBlank()) null else fromJson(JSONObject(raw))
        }.getOrNull()
    }
}

data class AppSettings(
    val defaultRampSeconds: Int = 30,
    val defaultVolumePercent: Int = 90,
    val defaultSnoozeMinutes: Int = 5,
    val defaultMaxSnoozes: Int = 3,
    val defaultVibrate: Boolean = true,
    val defaultOverrideDnd: Boolean = true,
    val fallbackFolderUri: String? = null,
    val systemAlarmSoundUri: String? = null,
    val audibleWarnings: Boolean = true,
)

fun Alarm.withDefaults(settings: AppSettings): Alarm = copy(
    rampSeconds = settings.defaultRampSeconds,
    targetVolumePercent = settings.defaultVolumePercent,
    snoozeMinutes = settings.defaultSnoozeMinutes,
    maxSnoozes = settings.defaultMaxSnoozes,
    vibrate = settings.defaultVibrate,
    overrideDnd = settings.defaultOverrideDnd,
)
