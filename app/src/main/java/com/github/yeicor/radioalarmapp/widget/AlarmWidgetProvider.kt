package com.github.yeicor.radioalarmapp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.github.yeicor.radioalarmapp.MainActivity
import com.github.yeicor.radioalarmapp.R
import com.github.yeicor.radioalarmapp.data.AlarmOverrides
import com.github.yeicor.radioalarmapp.data.AlarmRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A deliberately tiny next-alarm widget: one line of short text and one line with
 * - (10 minutes earlier), >> (skip the next occurrence) and + (10 minutes later).
 * After a skip the middle button becomes << until the skipped time has passed.
 */
class AlarmWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.github.yeicor.radioalarmapp.action.WIDGET_REFRESH"
        private const val REQUEST_OPEN = 9_250
        private const val REQUEST_REFRESH = 9_254
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, AlarmWidgetProvider::class.java),
                )
                if (ids.isEmpty()) return
                scheduleUndoRefresh(context)
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        /**
         * Keep the middle button honest: the << undo must disappear as soon as the skipped
         * time passes, even if the launcher never asks for another update.
         */
        private fun scheduleUndoRefresh(context: Context) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_REFRESH,
                Intent(context, AlarmWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val now = ZonedDateTime.now()
            val nextAlarm = AlarmOverrides.next(context, AlarmRepository.get(context).alarms.value, now)
            val override = nextAlarm?.first?.let { AlarmOverrides.get(context, it.id) }
            val skippedAt = override?.takeIf {
                it.kind == com.github.yeicor.radioalarmapp.data.OverrideKind.SKIP
            }?.skippedAt
            if (skippedAt != null && skippedAt > System.currentTimeMillis()) {
                runCatching {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, skippedAt + 1_000L, pending)
                }
            } else {
                manager.cancel(pending)
            }
        }

        @androidx.annotation.VisibleForTesting
        internal fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.radio_alarm_widget)
            val now = ZonedDateTime.now()
            val repo = AlarmRepository.get(context)
            val next = AlarmOverrides.next(context, repo.alarms.value, now)

            if (next == null) {
                views.setTextViewText(R.id.widget_alarm_text, context.getString(R.string.widget_no_alarm))
                views.setViewVisibility(R.id.widget_buttons, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_alarm_text, openApp(context))
                return views
            }

            val (alarm, target) = next
            val override = AlarmOverrides.get(context, alarm.id)
            val shiftMinutes = if (override?.kind == com.github.yeicor.radioalarmapp.data.OverrideKind.SHIFT) {
                ((override.effectiveAt - override.naturalAt) / 60_000L).toInt()
            } else {
                0
            }
            val day = when (target.toLocalDate()) {
                now.toLocalDate() -> context.getString(R.string.widget_today)
                now.toLocalDate().plusDays(1) -> context.getString(R.string.widget_tomorrow)
                else -> target.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
            val shortText = buildString {
                append(target.format(TIME_FORMAT))
                append(" · ")
                append(day)
                if (shiftMinutes != 0) {
                    append(if (shiftMinutes > 0) " +" else " ")
                    append(shiftMinutes)
                    append('m')
                }
            }
            views.setTextViewText(R.id.widget_alarm_text, shortText)
            views.setViewVisibility(R.id.widget_buttons, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_alarm_text, openApp(context))

            val middleAction = if (AlarmOverrides.canUndoSkip(context, alarm.id, now)) {
                AlarmWidgetActionReceiver.ACTION_UNDO
            } else {
                AlarmWidgetActionReceiver.ACTION_SKIP
            }
            val middleText = if (middleAction == AlarmWidgetActionReceiver.ACTION_UNDO) "<<" else ">>"

            views.setOnClickPendingIntent(
                R.id.widget_minus,
                action(context, AlarmWidgetActionReceiver.ACTION_MINUS, 9_251),
            )
            views.setOnClickPendingIntent(
                R.id.widget_middle,
                action(context, middleAction, 9_252),
            )
            views.setOnClickPendingIntent(
                R.id.widget_plus,
                action(context, AlarmWidgetActionReceiver.ACTION_PLUS, 9_253),
            )
            views.setTextViewText(R.id.widget_middle, middleText)
            return views
        }

        private fun action(context: Context, action: String, request: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                request,
                Intent(context, AlarmWidgetActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            refresh(context)
            return
        }
        super.onReceive(context, intent)
    }
}
