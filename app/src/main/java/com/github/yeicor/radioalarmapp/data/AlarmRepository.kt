package com.github.yeicor.radioalarmapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AlarmRepository private constructor(context: Context) {
    private val prefs = RadioAlarmPrefs.get(context)
    private val lock = Any()

    private val _alarms = MutableStateFlow(loadAlarms())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun getAlarm(id: Long): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun nextAlarm(after: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Alarm? =
        _alarms.value
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { alarm -> alarm.nextOccurrence(after)?.let { alarm to it } }
            .minByOrNull { it.second }
            ?.first

    fun addAlarm(alarm: Alarm): Alarm = synchronized(lock) {
        val id = if (alarm.id != 0L) alarm.id else nextId()
        val saved = alarm.copy(id = id, createdAt = if (alarm.createdAt == 0L) System.currentTimeMillis() else alarm.createdAt)
        val updated = _alarms.value.toMutableList()
        val existing = updated.indexOfFirst { it.id == id }
        if (existing >= 0) updated[existing] = saved else updated += saved
        persistAlarms(updated)
        saved
    }

    fun updateAlarm(alarm: Alarm): Alarm = addAlarm(alarm)

    fun deleteAlarm(id: Long) = synchronized(lock) {
        persistAlarms(_alarms.value.filterNot { it.id == id })
    }

    fun setEnabled(id: Long, enabled: Boolean) = synchronized(lock) {
        val updated = _alarms.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persistAlarms(updated)
    }

    fun duplicateAlarm(id: Long): Alarm? {
        val original = getAlarm(id) ?: return null
        return original.copy(
            id = 0L,
            label = if (original.label.isBlank()) "Copy" else "${original.label} copy",
            enabled = false,
            createdAt = 0L,
        )
    }

    fun updateSettings(settings: AppSettings) = synchronized(lock) {
        val json = JSONObject().apply {
            put("defaultRampSeconds", settings.defaultRampSeconds)
            put("defaultVolumePercent", settings.defaultVolumePercent)
            put("defaultSnoozeMinutes", settings.defaultSnoozeMinutes)
            put("defaultMaxSnoozes", settings.defaultMaxSnoozes)
            put("defaultVibrate", settings.defaultVibrate)
            put("defaultOverrideDnd", settings.defaultOverrideDnd)
            put("fallbackFolderUri", settings.fallbackFolderUri ?: "")
            put("systemAlarmSoundUri", settings.systemAlarmSoundUri ?: "")
            put("audibleWarnings", settings.audibleWarnings)
        }
        prefs.edit().putString(KEY_SETTINGS, json.toString()).commit()
        _settings.value = settings
    }

    private fun nextId(): Long = synchronized(lock) {
        val next = prefs.getLong(KEY_NEXT_ID, 1L).coerceAtLeast(1L)
        prefs.edit().putLong(KEY_NEXT_ID, next + 1L).commit()
        next
    }

    private fun persistAlarms(alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.sortedBy { it.createdAt }.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ALARMS, array.toString()).commit()
        _alarms.value = alarms.sortedBy { it.createdAt }
    }

    private fun loadAlarms(): List<Alarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { add(Alarm.fromJson(it)) }
                }
            }.sortedBy { it.createdAt }
        }.getOrDefault(emptyList())
    }

    private fun loadSettings(): AppSettings {
        val stored = prefs.getString(KEY_SETTINGS, null)
        val json = runCatching { stored?.let(::JSONObject) }.getOrNull()
        return AppSettings(
            defaultRampSeconds = json?.optInt("defaultRampSeconds", 30) ?: 30,
            defaultVolumePercent = json?.optInt("defaultVolumePercent", 90) ?: 90,
            defaultSnoozeMinutes = json?.optInt("defaultSnoozeMinutes", 5) ?: 5,
            defaultMaxSnoozes = json?.optInt("defaultMaxSnoozes", 3) ?: 3,
            defaultVibrate = json?.optBoolean("defaultVibrate", true) ?: true,
            defaultOverrideDnd = json?.optBoolean("defaultOverrideDnd", true) ?: true,
            fallbackFolderUri = if (json?.has("fallbackFolderUri") == true) json.optString("fallbackFolderUri").ifBlank { null } else null,
            systemAlarmSoundUri = if (json?.has("systemAlarmSoundUri") == true) json.optString("systemAlarmSoundUri").ifBlank { null } else null,
            audibleWarnings = json?.optBoolean("audibleWarnings", true) ?: true,
        )
    }

    companion object {
        private const val KEY_ALARMS = "alarms_json"
        private const val KEY_NEXT_ID = "next_alarm_id"
        private const val KEY_SETTINGS = "app_settings_json"

        @Volatile
        private var instance: AlarmRepository? = null

        fun get(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(context.applicationContext).also { instance = it }
            }
    }
}
