package dev.doggo.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.SyncScheduler
import dev.doggo.shared.sync.LeaveSchedulerCancelGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * AndroidSyncScheduler schedules background synchronization via WorkManager.
 * Invariants:
 * - All tasks tagged by account: "doggo_sync_account_<key>" for foolproof cancellation.
 * - One-shot unique work with network CONNECTED constraint and generation fencing.
 * - Periodic 15-minute background sync per active account with generation fencing.
 * - LeaveSchedulerCancelGate cancelAndAwait ensures background jobs quiesce before teardown.
 */
class AndroidSyncScheduler(
    private val context: Context
) : SyncScheduler, LeaveSchedulerCancelGate {

    companion object {
        private const val TAG_PREFIX = "doggo_sync_account_"
        private const val WORK_NAME_ONESHOT_PREFIX = "doggo_sync_oneshot_"
        private const val WORK_NAME_PERIODIC_PREFIX = "doggo_sync_periodic_"
    }

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private fun getAccountTag(accountKey: AccountKey) = "$TAG_PREFIX${accountKey.value}"

    override fun triggerImmediate(accountKey: AccountKey) {
        val app = DoggoApplication.instance
        val runtime = app.getActiveRuntime()
        val generation = if (runtime?.accountKey == accountKey) runtime.generation else app.accountFiles.readGeneration(accountKey)

        val uniqueWorkName = "$WORK_NAME_ONESHOT_PREFIX${accountKey.value}_$generation"
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val inputData = workDataOf("accountKey" to accountKey.value, "generation" to generation)

        val request = OneTimeWorkRequestBuilder<DoggoSyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(getAccountTag(accountKey))
            .build()

        workManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.KEEP, request)
    }

    override fun schedulePeriodic(accountKey: AccountKey) {
        val app = DoggoApplication.instance
        val runtime = app.getActiveRuntime()
        val generation = if (runtime?.accountKey == accountKey) runtime.generation else app.accountFiles.readGeneration(accountKey)

        val uniqueWorkName = "$WORK_NAME_PERIODIC_PREFIX${accountKey.value}"
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val inputData = workDataOf("accountKey" to accountKey.value, "generation" to generation)

        val request = PeriodicWorkRequestBuilder<DoggoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(getAccountTag(accountKey))
            .build()

        workManager.enqueueUniquePeriodicWork(uniqueWorkName, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel(accountKey: AccountKey) {
        workManager.cancelAllWorkByTag(getAccountTag(accountKey))
    }

    override suspend fun cancelAndAwait(accountKey: AccountKey): Unit = withContext(Dispatchers.Default) {
        val tag = getAccountTag(accountKey)
        workManager.cancelAllWorkByTag(tag)

        val deadline = System.currentTimeMillis() + 5000L
        var allFinished = false
        while (System.currentTimeMillis() < deadline) {
            val infos = workManager.getWorkInfosByTag(tag).get()
            if (infos.all { it.state.isFinished }) {
                allFinished = true
                break
            }
            delay(100L)
        }

        if (!allFinished) {
            val activeInfos = workManager.getWorkInfosByTag(tag).get().filter { !it.state.isFinished }
            if (activeInfos.isNotEmpty()) {
                throw IllegalStateException("cancelAndAwait timed out waiting for work termination: $activeInfos")
            }
        }

        val runtime = DoggoApplication.instance.getActiveRuntime()
        if (runtime != null && runtime.accountKey == accountKey) {
            val mutex = runtime.coordinator.coordinatorMutex
            val mutexDeadline = System.currentTimeMillis() + 5000L
            var acquired = mutex.tryLock()
            while (!acquired && System.currentTimeMillis() < mutexDeadline) {
                delay(50L)
                acquired = mutex.tryLock()
            }
            if (!acquired) {
                throw IllegalStateException("cancelAndAwait timed out waiting for coordinator quiescence")
            }
            mutex.unlock()
        }
    }
}
