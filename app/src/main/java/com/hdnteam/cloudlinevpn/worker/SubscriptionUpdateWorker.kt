package com.hdnteam.cloudlinevpn.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hdnteam.cloudlinevpn.data.repository.SubscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class SubscriptionUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "subscription_auto_update"
        const val TAG = "SubUpdateWorker"

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting subscription auto-update")
        return try {
            val subs = subscriptionRepository.subscriptions.first()
            var failed = 0
            subs.forEach { sub ->
                val result = subscriptionRepository.fetchAndUpdate(sub)
                if (result.isFailure) failed++
            }
            Log.d(TAG, "Update complete. Failed: $failed / ${subs.size}")
            if (failed == 0) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Worker error", e)
            Result.retry()
        }
    }
}
