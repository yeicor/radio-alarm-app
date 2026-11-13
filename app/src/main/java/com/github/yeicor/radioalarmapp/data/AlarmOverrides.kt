package com.github.yeicor.radioalarmapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime

/** How a one-shot override changed the next occurrence of an alarm. */
enum class OverrideKind { SHIFT, SKIP }

/**
 * A one-shot change made from the home-screen widget. It replaces the next natural
 * occurrence of one alarm and is consumed when that occurrence fires (or when the user
 * undoes a skip).
 *
 * [offsetMinutes] is the shift applied on top of the target occurrence. It is stored
 * separately from [naturalAt] so a skip followed by +10 minutes still shows +10, not the
 * whole distance to the skipped-to day.
 */
data class AlarmOverride(
    val alarmId: Long,
    /** The alarm's natural next occurrence when the override was created. */
    val naturalAt: Long,
    /** The time the alarm will actually fire. */
    val effectiveAt: Long,
    val kind: OverrideKind,
    /** For SKIP: the occurrence that was skipped. Undo is offered until this time. */
    val skippedAt: Long = 0L,
    /** For SKIP: the schedule to restore if the user presses undo. */
    val previousEffectiveAt: Long = 0L,
    /** Minutes added to the target occurrence (can be negative). */
    val offsetMinutes: Int = 0,
)

object AlarmOverrides {
    private const val KEY = "alarm_overrides_json"
    const val SHIFT_MINUTES = 10

    fun all(context: Context): List<AlarmOverride> {
        val raw = RadioAlarmPrefs.get(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.optJSONObject(i) ?: continue
                    val kind = runCatching {
                        OverrideKind.valueOf(json.optString("kind"))
                    }.getOrDefault(OverrideKind.SHIFT)
                    val naturalAt = json.optLong("naturalAt")
                    val effectiveAt = json.optLong("effectiveAt")
                    val offsetMinutes = if (json.has("offsetMinutes")) {
                        json.optInt("offsetMinutes")
                    } else if (kind == OverrideKind.SHIFT) {
                        ((effectiveAt - naturalAt) / 60_000L).toInt()
                    } else {
                        0
                    }
                    add(
                        AlarmOverride(
                            alarmId = json.optLong("alarmId"),
                            naturalAt = naturalAt,
                            effectiveAt = effectiveAt,
                            kind = kind,
                            skippedAt = json.optLong("skippedAt"),
                            previousEffectiveAt = json.optLong("previousEffectiveAt"),
                            offsetMinutes = offsetMinutes,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, alarmId: Long): AlarmOverride? =
        all(context).firstOrNull { it.alarmId == alarmId }

    fun put(context: Context, override: AlarmOverride) {
        val updated = all(context).filterNot { it.alarmId == override.alarmId } + override
        persist(context, updated)
    }

    fun remove(context: Context, alarmId: Long) {
        val current = all(context)
        if (current.none { it.alarmId == alarmId }) return
        persist(context, current.filterNot { it.alarmId == alarmId })
    }

    fun clear(context: Context) = persist(context, emptyList())

    /**
     * The occurrence Radio Alarm App should actually schedule for this alarm, taking a widget
     * override into account. Expired overrides are discarded automatically.
     */
    fun effectiveOccurrence(
        context: Context,
        alarm: Alarm,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): ZonedDateTime? {
        if (!alarm.enabled) return null
        val natural = alarm.nextOccurrence(now) ?: return null
        val override = get(context, alarm.id) ?: return natural
        val nowMs = now.toInstant().toEpochMilli()
        if (override.effectiveAt > nowMs) {
            return Instant.ofEpochMilli(override.effectiveAt).atZone(now.zone)
        }
        remove(context, alarm.id)
        return natural
    }

    fun next(
        context: Context,
        alarms: List<Alarm>,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Pair<Alarm, ZonedDateTime>? =
        alarms.asSequence()
            .filter { it.enabled }
            .mapNotNull { alarm ->
                effectiveOccurrence(context, alarm, now)?.let { alarm to it }
            }
            .minByOrNull { it.second }

    /**
     * Adds or subtracts minutes from the currently effective next occurrence. An existing
     * skip is preserved, and the stored offset stays relative to the skipped-to occurrence
     * so the widget does not display a huge multi-day shift.
     */
    fun shift(
        context: Context,
        alarm: Alarm,
        minutes: Int,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Boolean {
        val current = effectiveOccurrence(context, alarm, now) ?: return false
        val nowMs = now.toInstant().toEpochMilli()
        val targetMs = current.plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
        if (targetMs <= nowMs + 30_000L) return false

        val existing = get(context, alarm.id)?.takeIf { it.effectiveAt > nowMs }
        if (existing != null) {
            put(
                context,
                existing.copy(
                    effectiveAt = targetMs,
                    offsetMinutes = existing.offsetMinutes + minutes,
                ),
            )
        } else {
            val natural = alarm.nextOccurrence(now) ?: return false
            put(
                context,
                AlarmOverride(
                    alarmId = alarm.id,
                    naturalAt = natural.toInstant().toEpochMilli(),
                    effectiveAt = targetMs,
                    kind = OverrideKind.SHIFT,
                    offsetMinutes = minutes,
                ),
            )
        }
        return true
    }

    /**
     * Skips the currently effective occurrence. Repeating alarms move to their next valid
     * repeat day (for example Friday to Monday); one-time alarms move one day later.
     */
    fun skip(
        context: Context,
        alarm: Alarm,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Boolean {
        val current = effectiveOccurrence(context, alarm, now) ?: return false
        val natural = alarm.nextOccurrence(now) ?: return false
        val next = if (alarm.isOneTime) {
            current.plusDays(1)
        } else {
            alarm.nextOccurrence(current.plusMinutes(1))
        } ?: return false
        val currentMs = current.toInstant().toEpochMilli()
        put(
            context,
            AlarmOverride(
                alarmId = alarm.id,
                naturalAt = natural.toInstant().toEpochMilli(),
                effectiveAt = next.toInstant().toEpochMilli(),
                kind = OverrideKind.SKIP,
                skippedAt = currentMs,
                previousEffectiveAt = currentMs,
            ),
        )
        return true
    }

    /** True while the widget should show the undo (<<) button for a skipped occurrence. */
    fun canUndoSkip(
        context: Context,
        alarmId: Long,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Boolean {
        val override = get(context, alarmId) ?: return false
        return override.kind == OverrideKind.SKIP &&
            override.skippedAt > now.toInstant().toEpochMilli()
    }

    /**
     * Restores the schedule that existed before the skip. Returns false if the skipped
     * occurrence is already in the past.
     */
    fun undoSkip(
        context: Context,
        alarm: Alarm,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Boolean {
        val override = get(context, alarm.id) ?: return false
        val nowMs = now.toInstant().toEpochMilli()
        if (override.kind != OverrideKind.SKIP || override.skippedAt <= nowMs) {
            return false
        }
        val restoreMs = override.previousEffectiveAt
        if (restoreMs <= nowMs) return false

        val naturalMs = alarm.nextOccurrence(now)?.toInstant()?.toEpochMilli() ?: return false
        if (restoreMs == naturalMs) {
            remove(context, alarm.id)
        } else {
            put(
                context,
                AlarmOverride(
                    alarmId = alarm.id,
                    naturalAt = naturalMs,
                    effectiveAt = restoreMs,
                    kind = OverrideKind.SHIFT,
                    offsetMinutes = ((restoreMs - naturalMs) / 60_000L).toInt(),
                ),
            )
        }
        return true
    }

    private fun persist(context: Context, overrides: List<AlarmOverride>) {
        val array = JSONArray()
        overrides.forEach { override ->
            array.put(
                JSONObject().apply {
                    put("alarmId", override.alarmId)
                    put("naturalAt", override.naturalAt)
                    put("effectiveAt", override.effectiveAt)
                    put("kind", override.kind.name)
                    put("skippedAt", override.skippedAt)
                    put("previousEffectiveAt", override.previousEffectiveAt)
                    put("offsetMinutes", override.offsetMinutes)
                },
            )
        }
        RadioAlarmPrefs.get(context).edit().putString(KEY, array.toString()).commit()
    }
}
