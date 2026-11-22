package com.github.yeicor.radioalarmapp

import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AlarmOverride
import com.github.yeicor.radioalarmapp.data.AlarmSchedule
import com.github.yeicor.radioalarmapp.data.OverrideKind
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmScheduleTest {
    private val utc = ZoneId.of("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, utc)

    private fun override(
        kind: OverrideKind,
        naturalAt: ZonedDateTime,
        effectiveAt: ZonedDateTime,
    ): AlarmOverride = AlarmOverride(
        alarmId = 1L,
        naturalAt = naturalAt.toInstant().toEpochMilli(),
        effectiveAt = effectiveAt.toInstant().toEpochMilli(),
        kind = kind,
    )

    @Test
    fun shiftEarlierDoesNotRingAgainAtNaturalTimeSameDay() {
        // Monday: natural 08:00 moved to 07:50, and it has just fired.
        val now = at(2026, 11, 9, 7, 50)
        val natural = at(2026, 11, 9, 8, 0)
        val shifted = at(2026, 11, 9, 7, 50)
        val alarm = Alarm(hour = 8, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))

        val after = AlarmSchedule.afterFiring(override(OverrideKind.SHIFT, natural, shifted), now)

        assertEquals(at(2026, 11, 10, 8, 0), alarm.nextOccurrence(after))
    }

    @Test
    fun shiftLaterDoesNotRingAgainAtNaturalTimeSameDay() {
        // Monday: natural 08:00 moved to 08:10, and it has just fired.
        val now = at(2026, 11, 9, 8, 10)
        val natural = at(2026, 11, 9, 8, 0)
        val shifted = at(2026, 11, 9, 8, 10)
        val alarm = Alarm(hour = 8, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))

        val after = AlarmSchedule.afterFiring(override(OverrideKind.SHIFT, natural, shifted), now)

        assertEquals(at(2026, 11, 10, 8, 0), alarm.nextOccurrence(after))
    }

    @Test
    fun skipResumesAfterTheOccurrenceItMovedTo() {
        // Monday's alarm was skipped to Tuesday 08:00, and Tuesday's alarm has just fired.
        val now = at(2026, 11, 10, 8, 0)
        val natural = at(2026, 11, 9, 8, 0)
        val skippedTo = at(2026, 11, 10, 8, 0)
        val alarm = Alarm(hour = 8, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))

        val after = AlarmSchedule.afterFiring(override(OverrideKind.SKIP, natural, skippedTo), now)

        assertEquals(at(2026, 11, 11, 8, 0), alarm.nextOccurrence(after))
    }

    @Test
    fun noOverrideKeepsTheEarlyBroadcastGuard() {
        val now = at(2026, 11, 9, 8, 0)
        val alarm = Alarm(hour = 8, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))

        val after = AlarmSchedule.afterFiring(null, now)

        assertEquals(now.plusMinutes(1), after)
        assertEquals(at(2026, 11, 10, 8, 0), alarm.nextOccurrence(after))
    }
}
