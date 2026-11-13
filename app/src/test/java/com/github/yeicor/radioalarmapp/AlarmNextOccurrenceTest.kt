package com.github.yeicor.radioalarmapp

import com.github.yeicor.radioalarmapp.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmNextOccurrenceTest {
    private val utc = ZoneId.of("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, utc)

    @Test
    fun weekdays_morningBeforeAlarm_returnsToday() {
        // Monday 2026-11-09 06:00
        val now = at(2026, 11, 9, 6, 0)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))
        assertEquals(at(2026, 11, 9, 7, 0), alarm.nextOccurrence(now))
    }

    @Test
    fun weekdays_afterAlarm_skipsToNextWeekday() {
        // Monday 2026-11-09 08:00
        val now = at(2026, 11, 9, 8, 0)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))
        assertEquals(at(2026, 11, 10, 7, 0), alarm.nextOccurrence(now))
    }

    @Test
    fun fridayAfterAlarm_jumpsToMonday() {
        // Friday 2026-11-13 09:00
        val now = at(2026, 11, 13, 9, 0)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))
        assertEquals(at(2026, 11, 16, 7, 0), alarm.nextOccurrence(now))
    }

    @Test
    fun exactlyAtAlarmTime_schedulesFollowingOccurrence() {
        val now = at(2026, 11, 9, 7, 0)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))
        assertEquals(at(2026, 11, 10, 7, 0), alarm.nextOccurrence(now))
    }

    @Test
    fun oneTimeBeforeTime_returnsToday() {
        val now = at(2026, 11, 9, 6, 30)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = emptySet())
        assertEquals(at(2026, 11, 9, 7, 0), alarm.nextOccurrence(now))
    }

    @Test
    fun oneTimeAfterTime_returnsTomorrow() {
        val now = at(2026, 11, 9, 7, 30)
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = emptySet())
        assertEquals(at(2026, 11, 10, 7, 0), alarm.nextOccurrence(now))
    }

    @Test
    fun weekendOnly_returnsSaturday() {
        // Thursday 2026-11-12 23:00
        val now = at(2026, 11, 12, 23, 0)
        val alarm = Alarm(hour = 8, minute = 15, repeatDays = setOf(6, 7))
        assertEquals(at(2026, 11, 14, 8, 15), alarm.nextOccurrence(now))
    }

    @Test
    fun repeatLabel_isReadable() {
        assertEquals("Once", Alarm(repeatDays = emptySet()).repeatLabel())
        assertEquals("Every day", Alarm(repeatDays = setOf(1, 2, 3, 4, 5, 6, 7)).repeatLabel())
        assertEquals("Weekdays", Alarm(repeatDays = setOf(1, 2, 3, 4, 5)).repeatLabel())
        assertEquals("Weekends", Alarm(repeatDays = setOf(6, 7)).repeatLabel())
    }
}
