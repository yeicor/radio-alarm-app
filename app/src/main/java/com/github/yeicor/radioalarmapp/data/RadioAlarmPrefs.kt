package com.github.yeicor.radioalarmapp.data

import android.content.Context
import android.content.SharedPreferences

/**
 * All alarm data lives in device-protected storage so exact alarms can be restored
 * from LOCKED_BOOT_COMPLETED before the user unlocks the phone after a reboot.
 */
object RadioAlarmPrefs {
    private const val FILE_NAME = "radio_alarm_app"

    fun get(context: Context): SharedPreferences {
        val storage = context.createDeviceProtectedStorageContext()
        return storage.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }
}
