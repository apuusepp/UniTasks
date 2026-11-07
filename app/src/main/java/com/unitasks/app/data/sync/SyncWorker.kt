package com.unitasks.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unitasks.app.UniTasksApp
import com.unitasks.app.data.AuthMode
import com.unitasks.app.data.Settings
import com.unitasks.app.data.SyncRequester
import java.util.concurrent.TimeUnit

/** Runs [SyncEngine.sync] in the background. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as UniTasksApp).container
        if (container.settings.authMode.value != AuthMode.GOOGLE) return Result.success()
        return when (val result = container.syncEngine.sync()) {
            SyncResult.Success -> Result.success()
            SyncResult.AuthRequired -> Result.failure()
            is SyncResult.Failure -> if (result.retryable && runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}

/** Debounced, network-constrained sync requests via WorkManager. */
class WorkManagerSyncRequester(private val context: Context, private val settings: Settings) : SyncRequester {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun requestSync() {
        if (settings.authMode.value != AuthMode.GOOGLE) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic() {
        if (settings.authMode.value != AuthMode.GOOGLE) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    private companion object {
        const val ONE_TIME = "unitasks-sync"
        const val PERIODIC = "unitasks-periodic-sync"
        // Short debounce so bursts of edits (and "Undo") collapse into one sync.
        const val DEBOUNCE_SECONDS = 4L
    }
}
