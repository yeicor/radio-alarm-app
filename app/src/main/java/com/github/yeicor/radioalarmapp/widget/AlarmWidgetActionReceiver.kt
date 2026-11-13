package com.github.yeicor.radioalarmapp.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.github.yeicor.radioalarmapp.alarm.AlarmScheduler
import com.github.yeicor.radioalarmapp.data.AlarmOverrides
import com.github.yeicor.radioalarmapp.data.AlarmRepository
import java.time.ZonedDateTime

/**
 * Widget button handling lives in an unexported receiver. The exported
 * [AlarmWidgetProvider] only receives APPWIDGET_UPDATE broadcasts from the launcher.
 */
class AlarmWidgetActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MINUS = "com.github.yeicor.radioalarmapp.action.WIDGET_MINUS"
        const val ACTION_PLUS = "com.github.yeicor.radioalarmapp.action.WIDGET_PLUS"
        const val ACTION_SKIP = "com.github.yeicor.radioalarmapp.action.WIDGET_SKIP"
        const val ACTION_UNDO = "com.github.yeicor.radioalarmapp.action.WIDGET_UNDO"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(ACTION_MINUS, ACTION_PLUS, ACTION_SKIP, ACTION_UNDO)) return

        val now = ZonedDateTime.now()
        val repo = AlarmRepository.get(context)
        val alarm = AlarmOverrides.next(context, repo.alarms.value, now)?.first
        if (alarm == null) {
            AlarmWidgetProvider.refresh(context)
            return
        }

        val changed = when (action) {
            ACTION_MINUS -> AlarmOverrides.shift(context, alarm, -AlarmOverrides.SHIFT_MINUTES, now)
            ACTION_PLUS -> AlarmOverrides.shift(context, alarm, AlarmOverrides.SHIFT_MINUTES, now)
            ACTION_SKIP -> AlarmOverrides.skip(context, alarm, now)
            ACTION_UNDO -> AlarmOverrides.undoSkip(context, alarm, now)
            else -> false
        }

        if (changed) {
            AlarmScheduler.scheduleAll(context)
        } else {
            if (action == ACTION_MINUS || action == ACTION_PLUS) {
                Toast.makeText(context, "That would move the alarm into the past.", Toast.LENGTH_SHORT).show()
            }
            AlarmWidgetProvider.refresh(context)
        }
    }
}
