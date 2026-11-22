package com.github.yeicor.radioalarmapp.data

import java.time.Instant
import java.time.ZonedDateTime

/** Scheduling rules that are independent of Android and easy to test. */
object AlarmSchedule {
    /**
     * Where the next schedule should resume after this alarm has rung.
     *
     * A widget shift replaces one natural occurrence. Resuming from the shifted time would
     * still see the natural time later that day, so a shift resumes from the natural
     * occurrence it replaced. A skip resumes from the occurrence it moved to. Without an
     * override, the usual one-minute guard protects against early broadcasts.
     */
    fun afterFiring(
        override: AlarmOverride?,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): ZonedDateTime = when (override?.kind) {
        OverrideKind.SKIP -> Instant.ofEpochMilli(override.effectiveAt).atZone(now.zone)
        OverrideKind.SHIFT -> Instant.ofEpochMilli(override.naturalAt).atZone(now.zone)
        null -> now.plusMinutes(1)
    }
}
