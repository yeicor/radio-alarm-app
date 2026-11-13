package com.github.yeicor.radioalarmapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.yeicor.radioalarmapp.data.AlarmRepository

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE = "snooze"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
        private const val TAG = "RadioAlarmAppReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0) return
        val repo = AlarmRepository.get(context)
        val alarm = repo.getAlarm(alarmId) ?: return
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)

        if (!snooze) {
            if (!alarm.enabled) return
            // The widget override has been delivered; the next schedule must be natural again.
            com.github.yeicor.radioalarmapp.data.AlarmOverrides.remove(context, alarm.id)
            if (alarm.isOneTime) {
                repo.setEnabled(alarm.id, false)
                // Also removes any pending intent that a different process-start path could
                // have scheduled, so a one-shot alarm can never repeat tomorrow.
                AlarmScheduler.cancelAlarm(context, alarm.id)
            } else {
                AlarmScheduler.scheduleNext(context, alarm)
            }
            AlarmScheduler.cancelSnooze(context, alarm.id)
        } else {
            SnoozeStore.remove(context, alarm.id)
        }

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AlarmService.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmService.EXTRA_SNOOZE_COUNT, snoozeCount)
            putExtra(AlarmService.EXTRA_TEST, false)
        }
        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }.onFailure {
            Log.e(TAG, "Could not start alarm service", it)
            NotificationHelper.ensureChannels(context)
            NotificationHelper.showSafetyWarning(
                context,
                SafetyReport(
                    checkedAt = System.currentTimeMillis(),
                    nextAlarmId = alarm.id,
                    nextAlarmAt = System.currentTimeMillis(),
                    nextAlarmLabel = "${alarm.label.ifBlank { "Alarm" }} at ${alarm.timeLabel()}",
                    batteryPercent = null,
                    charging = false,
                    alarmVolumePercent = null,
                    alarmVolumeMuted = false,
                    streamReachable = null,
                    localFallbackCount = 0,
                    fallbackSummary = "phone and built-in sounds",
                    issues = listOf(
                        SafetyIssue(
                            "start_failed",
                            CheckSeverity.CRITICAL,
                            "Alarm could not start",
                            "Android blocked alarm playback. Open Radio Alarm App and check reliability permissions.",
                            SafetyFix.NOTIFICATIONS,
                            "Open Radio Alarm App",
                        ),
                    ),
                ),
            )
        }
    }
}
