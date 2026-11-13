package com.github.yeicor.radioalarmapp.alarm

import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.github.yeicor.radioalarmapp.data.RadioAlarmPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A notification listener used only to observe Do Not Disturb changes (notification content
 * is explicitly disabled in the manifest metadata). This is the closest public Android signal
 * to "the user is going to sleep".
 */
class DndWatcherService : NotificationListenerService() {
    companion object {
        private const val KEY_LAST_FILTER = "sleep_guard_last_filter"
        private const val KEY_LAST_TRIGGER = "sleep_guard_last_trigger"
        private const val THROTTLE_MS = 10 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val current = manager.currentInterruptionFilter
        val prefs = RadioAlarmPrefs.get(this)
        val hadPrevious = prefs.contains(KEY_LAST_FILTER)
        val previous = prefs.getInt(KEY_LAST_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        prefs.edit().putInt(KEY_LAST_FILTER, current).commit()

        // If the listener was reconnected while DND was already on, still offer a check
        // once in a while so a missed transition does not silently skip the safety pass.
        if (isDnd(current) && (!hadPrevious || !isDnd(previous))) {
            maybeRunCheck()
        }
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        super.onInterruptionFilterChanged(interruptionFilter)
        val prefs = RadioAlarmPrefs.get(this)
        val previous = prefs.getInt(KEY_LAST_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        prefs.edit().putInt(KEY_LAST_FILTER, interruptionFilter).commit()
        if (isDnd(interruptionFilter) && !isDnd(previous)) {
            maybeRunCheck()
        }
    }

    private fun maybeRunCheck() {
        val now = System.currentTimeMillis()
        val last = RadioAlarmPrefs.get(this).getLong(KEY_LAST_TRIGGER, 0L)
        if (now - last < THROTTLE_MS) return
        RadioAlarmPrefs.get(this).edit().putLong(KEY_LAST_TRIGGER, now).commit()
        safetyScopeLaunch()
    }

    private fun safetyScopeLaunch() {
        scope.launch {
            runCatching { SafetyChecker.checkAndNotify(applicationContext, notify = true) }
        }
    }

    private fun isDnd(filter: Int): Boolean =
        filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            filter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            (android.os.Build.VERSION.SDK_INT >= 28 && filter == NotificationManager.INTERRUPTION_FILTER_ALARMS)

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
