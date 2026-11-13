package com.github.yeicor.radioalarmapp.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.yeicor.radioalarmapp.MainActivity
import com.github.yeicor.radioalarmapp.R
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.util.Permissions

object NotificationHelper {
    const val CHANNEL_RING = "radio_alarm_app_ringing_v2"
    private const val CHANNEL_RING_BYPASS_LEGACY = "radio_alarm_app_ringing_bypass_v1"
    private const val CHANNEL_RING_LEGACY = "radio_alarm_app_ringing_v1"
    const val CHANNEL_SAFETY = "radio_alarm_app_safety_v1"
    private const val SAFETY_NOTIFICATION_ID = 8100
    private const val ALARM_BASE_ID = 100_000
    private const val SNOOZE_REQUEST_BASE = 200_000
    private const val DISMISS_REQUEST_BASE = 400_000
    private const val ALARM_ID_MOD = 100_000L

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val ringing = NotificationChannel(
            CHANNEL_RING,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(ringing)

        // Remove channels left behind by older builds.
        runCatching { manager.deleteNotificationChannel(CHANNEL_RING_BYPASS_LEGACY) }
        runCatching { manager.deleteNotificationChannel(CHANNEL_RING_LEGACY) }

        val safety = NotificationChannel(
            CHANNEL_SAFETY,
            context.getString(R.string.safety_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.safety_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 100, 180)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(true)
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (sound != null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(sound, attrs)
            }
        }
        manager.createNotificationChannel(safety)
    }

    fun alarmNotificationId(alarmId: Long): Int = ALARM_BASE_ID + (alarmId % ALARM_ID_MOD).toInt()

    fun showAlarmNotification(
        context: Context,
        alarm: Alarm,
        snoozeCount: Int,
        test: Boolean,
        status: String,
        fullScreen: Boolean,
    ) {
        notifySafely(
            context,
            alarmNotificationId(alarm.id),
            buildAlarmNotification(context, alarm, snoozeCount, test, status, fullScreen),
        )
    }

    fun buildAlarmNotification(
        context: Context,
        alarm: Alarm,
        snoozeCount: Int,
        test: Boolean,
        status: String,
        fullScreen: Boolean,
    ): android.app.Notification {
        ensureChannels(context)
        val ringIntent = RingActivity.intent(context, alarm, snoozeCount, test)
        val content = PendingIntent.getActivity(
            context,
            alarmNotificationId(alarm.id),
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channelId = CHANNEL_RING
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_wake)
            .setContentTitle(if (test) "Test: ${alarm.displayLabel()}" else alarm.displayLabel())
            .setContentText(status)
            .setSubText(if (test) "Test alarm" else "Alarm • ${alarm.timeLabel()}")
            .setContentIntent(content)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Do not mark the notification as silent: the ringing channel has no sound of
            // its own (Radio Alarm App plays verified alarm audio), but a silent notification is
            // collapsed by the system instead of being shown as a heads-up alarm.
            .setSound(null)
            .setVibrate(null)
            .setAutoCancel(false)

        if (!test && alarm.maxSnoozes > snoozeCount) {
            val snooze = PendingIntent.getBroadcast(
                context,
                SNOOZE_REQUEST_BASE + (alarm.id % ALARM_ID_MOD).toInt(),
                Intent(context, AlarmActionReceiver::class.java)
                    .setAction(AlarmActionReceiver.ACTION_SNOOZE)
                    .putExtra(AlarmActionReceiver.EXTRA_ALARM_ID, alarm.id)
                    .putExtra(AlarmActionReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
                    .putExtra(AlarmActionReceiver.EXTRA_TEST, test)
                    .setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                0,
                context.getString(R.string.notification_snooze),
                snooze,
            )
        }

        val dismiss = PendingIntent.getBroadcast(
            context,
            DISMISS_REQUEST_BASE + (alarm.id % ALARM_ID_MOD).toInt(),
            Intent(context, AlarmActionReceiver::class.java)
                .setAction(AlarmActionReceiver.ACTION_DISMISS)
                .putExtra(AlarmActionReceiver.EXTRA_ALARM_ID, alarm.id)
                .putExtra(AlarmActionReceiver.EXTRA_TEST, test)
                .setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(0, context.getString(R.string.notification_dismiss), dismiss)

        if (fullScreen) {
            builder.setFullScreenIntent(content, true)
        }
        return builder.build()
    }

    fun cancelAlarmNotification(context: Context, alarmId: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(alarmNotificationId(alarmId)) }
    }

    fun showSafetyWarning(context: Context, report: SafetyReport) {
        if (report.nextAlarmAt == null || report.issues.none { it.severity >= CheckSeverity.WARNING }) return
        ensureChannels(context)
        val warnings = report.issues
            .filter { it.severity >= CheckSeverity.WARNING }
            .sortedByDescending { it.severity }
        val title = if (warnings.any { it.severity == CheckSeverity.CRITICAL }) {
            "Your next alarm needs attention"
        } else {
            "Next alarm: ${report.nextAlarmLabel.ifBlank { "check required" }}"
        }
        val detail = buildString {
            append(report.nextAlarmLabel.ifBlank { "Your alarm" })
            append(" is at risk.\n\n")
            warnings.forEach { issue ->
                append("• ")
                append(issue.title)
                append(" — ")
                append(issue.message)
                append('\n')
            }
            append("\nFallbacks ready: ")
            append(report.fallbackSummary)
        }
        val open = PendingIntent.getActivity(
            context,
            8101,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_SAFETY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_SAFETY)
            .setSmallIcon(R.drawable.ic_stat_wake)
            .setContentTitle(title)
            .setContentText(warnings.first().title + " — " + warnings.first().message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .addAction(0, "Open Radio Alarm App", open)
        notifySafely(context, SAFETY_NOTIFICATION_ID, builder.build())
    }

    fun cancelSafetyWarning(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(SAFETY_NOTIFICATION_ID) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun notifySafely(context: Context, id: Int, notification: android.app.Notification) {
        if (!Permissions.notificationsEnabled(context)) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun Alarm.displayLabel(): String = label.ifBlank { "Radio Alarm App alarm" }
}
