package com.github.yeicor.radioalarmapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class AlarmOverridesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val alarm = Alarm(
        id = 42L,
        hour = 7,
        minute = 0,
        repeatDays = setOf(1, 2, 3, 4, 5, 6, 7),
        enabled = true,
    )

    // Friday 13 November 2026, 06:00 local time.
    private val now = ZonedDateTime.of(2026, 11, 13, 6, 0, 0, 0, ZoneId.systemDefault())

    @Before
    fun clearBefore() = AlarmOverrides.clear(context)

    @After
    fun clearAfter() = AlarmOverrides.clear(context)

    @Test
    fun skipThenShiftKeepsSkipAndSmallOffset() {
        assertTrue(AlarmOverrides.skip(context, alarm, now))
        val skipped = AlarmOverrides.get(context, alarm.id)
        assertNotNull(skipped)
        assertEquals(OverrideKind.SKIP, skipped!!.kind)

        assertTrue(AlarmOverrides.shift(context, alarm, AlarmOverrides.SHIFT_MINUTES, now))
        val shifted = AlarmOverrides.get(context, alarm.id)
        assertNotNull(shifted)
        assertEquals(OverrideKind.SKIP, shifted!!.kind)
        assertEquals(AlarmOverrides.SHIFT_MINUTES, shifted.offsetMinutes)
        assertEquals(7, at(shifted).hour)
        assertEquals(10, at(shifted).minute)
        assertTrue(AlarmOverrides.canUndoSkip(context, alarm.id, now))
    }

    @Test
    fun undoAfterSkipRestoresNaturalOccurrence() {
        assertTrue(AlarmOverrides.skip(context, alarm, now))
        assertTrue(AlarmOverrides.undoSkip(context, alarm, now))
        assertNull(AlarmOverrides.get(context, alarm.id))
    }

    @Test
    fun shiftThenSkipMovesToNextDayWithoutHugeOffset() {
        assertTrue(AlarmOverrides.shift(context, alarm, AlarmOverrides.SHIFT_MINUTES, now))
        assertTrue(AlarmOverrides.skip(context, alarm, now))
        val skipped = AlarmOverrides.get(context, alarm.id)
        assertNotNull(skipped)
        assertEquals(OverrideKind.SKIP, skipped!!.kind)
        assertEquals(0, skipped.offsetMinutes)
        assertEquals(7, at(skipped).hour)
        assertEquals(0, at(skipped).minute)
        assertEquals(14, at(skipped).dayOfMonth)
    }

    @Test
    fun undoAfterShiftThenSkipRestoresTheShift() {
        assertTrue(AlarmOverrides.shift(context, alarm, AlarmOverrides.SHIFT_MINUTES, now))
        assertTrue(AlarmOverrides.skip(context, alarm, now))
        assertTrue(AlarmOverrides.undoSkip(context, alarm, now))
        val restored = AlarmOverrides.get(context, alarm.id)
        assertNotNull(restored)
        assertEquals(OverrideKind.SHIFT, restored!!.kind)
        assertEquals(AlarmOverrides.SHIFT_MINUTES, restored.offsetMinutes)
        assertEquals(7, at(restored).hour)
        assertEquals(10, at(restored).minute)
    }

    private fun at(override: AlarmOverride): ZonedDateTime =
        Instant.ofEpochMilli(override.effectiveAt).atZone(now.zone)
}
