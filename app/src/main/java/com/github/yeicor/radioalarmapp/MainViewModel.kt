package com.github.yeicor.radioalarmapp

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.yeicor.radioalarmapp.alarm.AlarmScheduler
import com.github.yeicor.radioalarmapp.alarm.AlarmService
import com.github.yeicor.radioalarmapp.alarm.NotificationHelper
import com.github.yeicor.radioalarmapp.alarm.SafetyChecker
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AlarmOverrides
import com.github.yeicor.radioalarmapp.data.AlarmRepository
import com.github.yeicor.radioalarmapp.data.AppSettings
import com.github.yeicor.radioalarmapp.data.withDefaults
import com.github.yeicor.radioalarmapp.update.UpdateChecker
import com.github.yeicor.radioalarmapp.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = AlarmRepository.get(app)

    val alarms = repository.alarms
    val settings = repository.settings
    val safetyReport = SafetyChecker.report
    val playback = AlarmService.playback
    val liveRms = AlarmService.liveRms

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    init {
        viewModelScope.launch {
            val stale = System.currentTimeMillis() - SafetyChecker.lastCheckedAt(app) > 30 * 60 * 1000L
            if (stale && AlarmOverrides.next(app, repository.alarms.value) != null) {
                runCheck(notify = false)
            }
        }
        checkForUpdate(force = false)
    }

    fun nextAlarm(now: ZonedDateTime = ZonedDateTime.now()): Alarm? =
        AlarmOverrides.next(app, repository.alarms.value, now)?.first

    fun saveAlarm(alarm: Alarm): Alarm {
        // Editing an alarm invalidates any one-shot widget override for it.
        AlarmOverrides.remove(app, alarm.id)
        val saved = repository.addAlarm(alarm)
        try {
            AlarmScheduler.scheduleAll(app)
        } catch (t: Throwable) {
            Log.e("Radio Alarm App", "Could not reschedule after save", t)
        }
        if (saved.enabled) {
            // Warn immediately after setting the alarm, not only on the periodic check.
            runCheck(notify = true)
        }
        return saved
    }

    fun deleteAlarm(alarm: Alarm) {
        AlarmService.stopIfActive(app, alarm.id)
        AlarmScheduler.cancelAlarm(app, alarm.id)
        repository.deleteAlarm(alarm.id)
        AlarmScheduler.scheduleAll(app)
        NotificationHelper.cancelAlarmNotification(app, alarm.id)
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        AlarmOverrides.remove(app, alarm.id)
        repository.setEnabled(alarm.id, enabled)
        if (enabled) {
            AlarmScheduler.scheduleAll(app)
            runCheck(notify = true)
        } else {
            AlarmService.stopIfActive(app, alarm.id)
            AlarmScheduler.cancelAlarm(app, alarm.id)
            NotificationHelper.cancelAlarmNotification(app, alarm.id)
            AlarmScheduler.scheduleAll(app)
        }
    }

    fun updateSettings(settings: AppSettings) {
        repository.updateSettings(settings)
        NotificationHelper.ensureChannels(app)
    }

    fun testAlarm(alarm: Alarm? = null) {
        val target = alarm ?: nextAlarm() ?: repository.alarms.value.firstOrNull() ?: Alarm(
            id = -1L,
            label = "Test Alarm",
            streamUrl = Alarm.DEFAULT_STREAM_URL,
        ).withDefaults(repository.settings.value)
        NotificationHelper.ensureChannels(app)
        val intent = Intent(app, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AlarmService.EXTRA_ALARM_ID, target.id)
            putExtra(AlarmService.EXTRA_TEST, true)
            putExtra(AlarmService.EXTRA_SNOOZE_COUNT, 0)
        }
        runCatching {
            ContextCompat.startForegroundService(app, intent)
        }.onFailure {
            Log.e("Radio Alarm App", "Could not start test alarm", it)
        }
        runCheck(notify = false)
    }

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            val channel = settings.value.updateChannel
            val info = UpdateChecker.check(app, channel, force = force)
            if (info != null && info.isNewer) {
                _updateInfo.value = info
            }
        }
    }

    fun dismissUpdate(info: UpdateInfo) {
        UpdateChecker.dismiss(app, info.versionLabel)
        _updateInfo.value = null
    }

    fun runCheck(notify: Boolean = false) {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            try {
                SafetyChecker.checkAndNotify(app, notify = notify)
            } finally {
                _checking.value = false
            }
        }
    }

    fun stopTest() {
        runCatching {
            app.stopService(Intent(app, AlarmService::class.java))
        }
    }
}
