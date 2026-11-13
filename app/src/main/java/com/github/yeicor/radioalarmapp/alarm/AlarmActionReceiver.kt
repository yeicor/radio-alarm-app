package com.github.yeicor.radioalarmapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.yeicor.radioalarmapp.data.AlarmRepository

class AlarmActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE = "com.github.yeicor.radioalarmapp.action.SNOOZE"
        const val ACTION_DISMISS = "com.github.yeicor.radioalarmapp.action.DISMISS"
        const val ACTION_ALARM_ENDED = "com.github.yeicor.radioalarmapp.action.ALARM_ENDED"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
        const val EXTRA_TEST = "test_alarm"
        const val EXTRA_RESULT = "result"
        private const val TAG = "RadioAlarmAppAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val test = intent.getBooleanExtra(EXTRA_TEST, false)
        if (alarmId <= 0) return
        val repo = AlarmRepository.get(context)
        val alarm = repo.getAlarm(alarmId)

        when (intent.action) {
            ACTION_SNOOZE -> {
                if (test || alarm == null) {
                    dismiss(context, alarmId)
                    return
                }
                val count = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
                if (count >= alarm.maxSnoozes) {
                    Log.i(TAG, "Snooze limit reached for ${alarm.id}")
                    return
                }
                AlarmScheduler.snooze(context, alarm, alarm.snoozeMinutes, count + 1)
                context.stopService(Intent(context, AlarmService::class.java))
                NotificationHelper.cancelAlarmNotification(context, alarm.id)
                notifyEnded(context, alarm.id, "snoozed")
            }

            ACTION_DISMISS -> dismiss(context, alarmId)
        }
    }

    private fun dismiss(context: Context, alarmId: Long) {
        AlarmScheduler.cancelSnooze(context, alarmId)
        context.stopService(Intent(context, AlarmService::class.java))
        NotificationHelper.cancelAlarmNotification(context, alarmId)
        notifyEnded(context, alarmId, "dismissed")
    }

    private fun notifyEnded(context: Context, alarmId: Long, result: String) {
        val ended = Intent(ACTION_ALARM_ENDED)
            .setPackage(context.packageName)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_RESULT, result)
        context.sendBroadcast(ended)
    }
}
