package com.github.yeicor.radioalarmapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.github.yeicor.radioalarmapp.MainActivity
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AlarmOverrides
import com.github.yeicor.radioalarmapp.data.AlarmRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class SnoozeRecord(
    val alarmId: Long,
    val triggerAt: Long,
    val count: Int,
)

object SnoozeStore {
    private const val KEY = "pending_snoozes_json"

    fun list(context: Context): List<SnoozeRecord> {
        val raw = com.github.yeicor.radioalarmapp.data.RadioAlarmPrefs.get(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        SnoozeRecord(
                            alarmId = obj.optLong("alarmId"),
                            triggerAt = obj.optLong("triggerAt"),
                            count = obj.optInt("count", 1),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, record: SnoozeRecord) {
        val updated = list(context).filterNot { it.alarmId == record.alarmId } + record
        persist(context, updated)
    }

    fun remove(context: Context, alarmId: Long) {
        persist(context, list(context).filterNot { it.alarmId == alarmId })
    }

    fun rescheduleAll(context: Context) {
        list(context).forEach { record ->
            val alarm = AlarmRepository.get(context).getAlarm(record.alarmId)
            if (alarm == null) {
                remove(context, record.alarmId)
            } else {
                AlarmScheduler.scheduleSnooze(context, alarm, record.triggerAt, record.count)
            }
        }
    }

    private fun persist(context: Context, records: List<SnoozeRecord>) {
        val array = JSONArray()
        records.forEach {
            array.put(
                JSONObject().apply {
                    put("alarmId", it.alarmId)
                    put("triggerAt", it.triggerAt)
                    put("count", it.count)
                },
            )
        }
        com.github.yeicor.radioalarmapp.data.RadioAlarmPrefs.get(context)
            .edit().putString(KEY, array.toString()).commit()
    }
}

object AlarmScheduler {
    const val ACTION_FIRE = "com.github.yeicor.radioalarmapp.action.FIRE_ALARM"
    const val ACTION_FIRE_SNOOZE = "com.github.yeicor.radioalarmapp.action.FIRE_SNOOZE"

    fun scheduleAll(context: Context) {
        val repo = AlarmRepository.get(context)
        val enabled = repo.alarms.value.filter { it.enabled }
        enabled.forEach { scheduleAlarm(context, it) }
        com.github.yeicor.radioalarmapp.widget.AlarmWidgetProvider.refresh(context)
    }

    fun scheduleAlarm(context: Context, alarm: Alarm) {
        if (!alarm.enabled) {
            cancelAlarm(context, alarm.id)
            return
        }
        scheduleAlarmAfter(context, alarm, ZonedDateTime.now())
    }

    fun scheduleNext(context: Context, alarm: Alarm) {
        if (alarm.enabled) {
            // Broadcasts can arrive a second or two early. Re-scheduling from "now" would
            // then compute the occurrence that just fired and fire the alarm in a loop, so
            // require at least one minute of headroom.
            scheduleAlarmAfter(context, alarm, ZonedDateTime.now().plusMinutes(1))
        }
        com.github.yeicor.radioalarmapp.widget.AlarmWidgetProvider.refresh(context)
    }

    private fun scheduleAlarmAfter(context: Context, alarm: Alarm, after: ZonedDateTime) {
        val next = AlarmOverrides.effectiveOccurrence(context, alarm, after) ?: return
        scheduleAt(context, alarm, next.toInstant().toEpochMilli(), snooze = false, snoozeCount = 0)
    }

    fun scheduleSnooze(context: Context, alarm: Alarm, triggerAt: Long, count: Int) {
        val intent = alarmIntent(context, alarm, ACTION_FIRE_SNOOZE, snooze = true, snoozeCount = count)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarmClock(context, triggerAt, pending, alarm)
    }

    fun snooze(context: Context, alarm: Alarm, minutes: Int, newCount: Int) {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        SnoozeStore.add(context, SnoozeRecord(alarm.id, triggerAt, newCount))
        scheduleSnooze(context, alarm, triggerAt, newCount)
    }

    fun cancelAlarm(context: Context, alarmId: Long) {
        cancel(context, alarmId, ACTION_FIRE)
        cancel(context, alarmId, ACTION_FIRE_SNOOZE)
        SnoozeStore.remove(context, alarmId)
        AlarmOverrides.remove(context, alarmId)
        com.github.yeicor.radioalarmapp.widget.AlarmWidgetProvider.refresh(context)
    }

    fun cancelSnooze(context: Context, alarmId: Long) {
        cancel(context, alarmId, ACTION_FIRE_SNOOZE)
        SnoozeStore.remove(context, alarmId)
    }

    private fun cancel(context: Context, alarmId: Long, action: String) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    private fun scheduleAt(
        context: Context,
        alarm: Alarm,
        triggerAt: Long,
        snooze: Boolean,
        snoozeCount: Int,
    ) {
        val action = if (snooze) ACTION_FIRE_SNOOZE else ACTION_FIRE
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.id),
            alarmIntent(context, alarm, action, snooze, snoozeCount),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarmClock(context, triggerAt, pending, alarm)
    }

    private fun setAlarmClock(context: Context, triggerAt: Long, pending: PendingIntent, alarm: Alarm) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val show = PendingIntent.getActivity(
            context,
            requestCode(alarm.id),
            Intent(context, MainActivity::class.java)
                .setAction("com.github.yeicor.radioalarmapp.action.SHOW_ALARM")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pending)
        }.onFailure {
            runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
                .onFailure {
                    runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
                }
        }
    }

    private fun alarmIntent(
        context: Context,
        alarm: Alarm,
        action: String,
        snooze: Boolean,
        snoozeCount: Int,
    ): Intent = Intent(context, AlarmReceiver::class.java).apply {
        this.action = action
        putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
    }

    fun requestCode(alarmId: Long): Int = (alarmId and 0x3FFFFFFFL).toInt()

    fun triggerAt(alarm: Alarm): ZonedDateTime? = alarm.nextOccurrence()

    fun triggerInstant(alarm: Alarm): Instant? = triggerAt(alarm)?.toInstant()

    fun isExactAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else true

    fun zone(): ZoneId = ZoneId.systemDefault()
}
