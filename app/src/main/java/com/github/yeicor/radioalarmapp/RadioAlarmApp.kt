package com.github.yeicor.radioalarmapp

import android.app.Application
import android.util.Log
import com.github.yeicor.radioalarmapp.alarm.NotificationHelper
import com.github.yeicor.radioalarmapp.alarm.SafetyChecker
import com.github.yeicor.radioalarmapp.alarm.SafetyChecks
import com.github.yeicor.radioalarmapp.data.AlarmRepository

class RadioAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // This also runs from LOCKED_BOOT_COMPLETED, before the user unlocks. Never let a
        // failure here (channels, prefs) take down the process that has to restore alarms.
        runCatching {
            AlarmRepository.get(this)
            SafetyChecker.initialize(this)
        }.onFailure { Log.e("RadioAlarmApp", "Could not initialise data", it) }
        runCatching {
            NotificationHelper.ensureChannels(this)
        }.onFailure { Log.e("RadioAlarmApp", "Could not create notification channels", it) }
        runCatching {
            SafetyChecks.ensureScheduled(this)
        }.onFailure { Log.e("RadioAlarmApp", "Could not schedule safety checks", it) }
    }
}
