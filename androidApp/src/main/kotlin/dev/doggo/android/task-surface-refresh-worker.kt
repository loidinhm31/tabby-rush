package dev.doggo.android

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Inexact, battery-friendly CoroutineWorker scheduled for future deadline boundaries.
 * Invariants:
 * - Unique, one-time work tagged per account; replaces any existing scheduled boundary.
 * - Minimum delay of 15 minutes to prevent catch-up loops.
 * - No network constraints or exact alarm requirements.
 * - Validates host readiness and token generation before dispatching refresh.
 * - Propagates coroutine cancellation cleanly.
 */
class TaskSurfaceRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "TaskSurfaceRefreshWorker"
        const val MIN_DELAY_MS = 15 * 60 * 1000L // 15 minutes

        fun getUniqueWorkName(accountKey: AccountKey): String =
            "doggo_task_surface_boundary_${accountKey.value}"

        fun getAccountTag(accountKey: AccountKey): String =
            "doggo_task_surface_${accountKey.value}"

        fun scheduleBoundaryWake(
            context: Context,
            accountKey: AccountKey,
            generation: Long,
            targetEpochMs: Long,
            nowEpochMs: Long
        ) {
            val delayMs = targetEpochMs - nowEpochMs
            if (delayMs <= 0) {
                // Past or immediate boundary does not warrant a deferred worker
                return
            }
            val effectiveDelayMs = maxOf(MIN_DELAY_MS, delayMs)
            val workRequest = OneTimeWorkRequestBuilder<TaskSurfaceRefreshWorker>()
                .setInitialDelay(effectiveDelayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        TaskSurfaceIntents.EXTRA_ACCOUNT_KEY to accountKey.value,
                        TaskSurfaceIntents.EXTRA_GENERATION to generation
                    )
                )
                .addTag(getAccountTag(accountKey))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                getUniqueWorkName(accountKey),
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelBoundaryWake(context: Context, accountKey: AccountKey) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(getUniqueWorkName(accountKey))
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling boundary worker for $accountKey: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        val accountKeyValue = inputData.getString(TaskSurfaceIntents.EXTRA_ACCOUNT_KEY)
            ?: return Result.failure()
        val expectedGeneration = inputData.getLong(TaskSurfaceIntents.EXTRA_GENERATION, -1L)
        if (expectedGeneration < 0L) {
            return Result.failure()
        }

        val accountKey = AccountKey(accountKeyValue)
        val hostOwner = applicationContext as? TaskSurfaceHostOwner
        if (hostOwner == null) {
            Log.w(TAG, "ApplicationContext does not implement TaskSurfaceHostOwner. Dropping worker.")
            return Result.failure()
        }

        val host = hostOwner.taskSurfaces
        val readiness = host.access.awaitReadiness(timeoutMs = 4_000L)
        if (readiness !is SurfaceReadiness.Ready) {
            Log.w(TAG, "Host readiness is $readiness (expected Ready). Dropping boundary wake.")
            return Result.failure()
        }

        val activeToken = readiness.token
        if (activeToken.accountKey != accountKey || activeToken.generation != expectedGeneration) {
            Log.w(
                TAG,
                "Token mismatch in worker (expected $accountKey gen $expectedGeneration, got ${activeToken.accountKey} gen ${activeToken.generation}). Dropping stale worker."
            )
            return Result.failure()
        }

        return try {
            host.refresh(SurfaceRefreshReason.Clock)
            Result.success()
        } catch (ce: CancellationException) {
            Log.i(TAG, "TaskSurfaceRefreshWorker cancelled: ${ce.message}")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error running TaskSurfaceRefreshWorker for $accountKey: ${e.message}", e)
            Result.failure()
        }
    }
}
