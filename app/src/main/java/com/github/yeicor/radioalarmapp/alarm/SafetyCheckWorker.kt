package com.github.yeicor.radioalarmapp.alarm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Low-power safety pass. WorkManager batches it with other wake-ups and only runs it while
 * the battery is not low, so ten checks a day cost very little.
 */
class SafetyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { SafetyChecker.checkAndNotify(applicationContext, notify = true) }
        return Result.success()
    }
}

object SafetyChecks {
    private const val WORK_NAME = "periodic_safety_check"

    /** 144 minutes is exactly ten checks per day. */
    private const val INTERVAL_MINUTES = 144L

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<SafetyCheckWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
