package com.github.yeicor.radioalarmapp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
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
import kotlin.math.min
import kotlin.math.roundToInt

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
                ids.forEach { id ->
                    manager.updateAppWidget(id, buildViews(context, manager.getAppWidgetOptions(id)))
                }
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
        internal fun buildViews(context: Context, options: Bundle? = null): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.radio_alarm_widget)
            applySizing(context, views, options)
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
            val shiftMinutes = override?.offsetMinutes ?: 0
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

            val canUndo = AlarmOverrides.canUndoSkip(context, alarm.id, now)
            val middleAction = if (canUndo) {
                AlarmWidgetActionReceiver.ACTION_UNDO
            } else {
                AlarmWidgetActionReceiver.ACTION_SKIP
            }

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
            views.setImageViewResource(
                R.id.widget_middle,
                if (canUndo) R.drawable.ic_widget_undo else R.drawable.ic_widget_skip,
            )
            views.setContentDescription(
                R.id.widget_middle,
                context.getString(if (canUndo) R.string.widget_action_undo else R.string.widget_action_skip),
            )
            return views
        }

        /**
         * Scale type and button padding with the widget's current size. The weighted layout
         * already gives the buttons most of the height; this keeps the type readable and the
         * buttons easy to hit at every size from 2x1 upwards.
         */
        private fun applySizing(context: Context, views: RemoteViews, options: Bundle?) {
            val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 180
            val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 50
            val width = widthDp.coerceIn(100, 400)
            val height = heightDp.coerceIn(40, 240)
            val textSp = min(height * 0.28f, width * 0.13f).coerceIn(11f, 30f)
            views.setTextViewTextSize(R.id.widget_alarm_text, TypedValue.COMPLEX_UNIT_SP, textSp)

            val density = context.resources.displayMetrics.density
            val paddingPx = ((height * 0.12f).coerceIn(3f, 14f) * density).roundToInt()
            listOf(R.id.widget_minus, R.id.widget_middle, R.id.widget_plus).forEach { id ->
                views.setViewPadding(id, paddingPx, paddingPx, paddingPx, paddingPx)
            }
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, newOptions))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            refresh(context)
            return
        }
        super.onReceive(context, intent)
    }
}
