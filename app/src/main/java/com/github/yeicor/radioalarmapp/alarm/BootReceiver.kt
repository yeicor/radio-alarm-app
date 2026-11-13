package com.github.yeicor.radioalarmapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.yeicor.radioalarmapp.data.AlarmRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                runCatching { AlarmRepository.get(context) }
                runCatching { NotificationHelper.ensureChannels(context) }
                // Scheduling is the critical part: keep it independent of the optional
                // channel/DND repair so one failure cannot skip every alarm.
                runCatching { AlarmScheduler.scheduleAll(context) }
                runCatching { SnoozeStore.rescheduleAll(context) }
            }
        }
    }
}
