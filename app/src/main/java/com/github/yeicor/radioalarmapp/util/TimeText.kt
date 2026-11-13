package com.github.yeicor.radioalarmapp.util

import java.time.Duration
import java.time.ZonedDateTime

object TimeText {
    fun until(target: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
        val duration = Duration.between(now, target)
        if (duration.isNegative) return "now"
        val totalMinutes = duration.toMinutes()
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "in ${days}d ${hours}h"
            hours > 0 -> "in ${hours}h ${minutes}m"
            minutes > 0 -> "in ${minutes}m"
            else -> "in less than a minute"
        }
    }

    fun relativeDay(target: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
        val targetDate = target.toLocalDate()
        val today = now.toLocalDate()
        return when (targetDate) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> target.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        }
    }

    fun minutesLabel(minutes: Int): String = if (minutes == 1) "1 minute" else "$minutes minutes"
}
