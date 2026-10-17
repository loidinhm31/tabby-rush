package dev.tabbyrush.android

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AuthState
import kotlinx.coroutines.CancellationException

/**
 * TabbyRushSyncWorker executes background sync via WorkManager.
 * Invariants:
 * - Validates active AccountKey and generation counter before accessing resources.
 * - Asserts active auth session matches worker account.
 * - Fails closed if account has a pending wipe marker.
 * - Shares the same process container and coordinator mutex as foreground.
 * - Respects coroutine cancellation; coordinator handles in-flight push cleanup.
 */
class TabbyRushSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "TabbyRushSyncWorker"
    }

    override suspend fun doWork(): Result {
        val app = TabbyRushApplication.instance
        val accountKeyValue = inputData.getString("accountKey")
            ?: app.authSession.currentAccountKey.value?.value
            ?: return Result.failure()

        val accountKey = AccountKey(accountKeyValue)

        if (app.accountFiles.hasPendingWipe(accountKey)) {
            Log.w(TAG, "Account $accountKeyValue has pending wipe marker. Aborting worker.")
            return Result.failure()
        }

        // If session is unauthenticated, allow retry if active account matches, else fail
        if (app.authSession.state.value != AuthState.AUTHENTICATED) {
            Log.i(TAG, "Auth session not authenticated for $accountKeyValue. Retrying.")
            return Result.retry()
        }

        if (app.authSession.currentAccountKey.value != accountKey) {
            Log.w(TAG, "Auth session account mismatch for $accountKeyValue. Dropping work.")
            return Result.failure()
        }

        val expectedGeneration = inputData.getLong("generation", -1L)
        val currentPersistedGen = app.accountFiles.readGeneration(accountKey)
        if (expectedGeneration != currentPersistedGen) {
            Log.w(TAG, "Generation mismatch in worker for $accountKeyValue (expected=$expectedGeneration, current=$currentPersistedGen). Dropping stale work.")
            return Result.failure()
        }

        return try {
            val runtime = app.getOrCreateAccountRuntime(accountKey)
            if (runtime.generation != currentPersistedGen) {
                Log.w(TAG, "Runtime generation mismatch. Dropping worker execution.")
                return Result.failure()
            }

            runtime.coordinator.reconcilePending()
            try {
                (app as? TaskSurfaceHostOwner)?.taskSurfaces?.refresh(SurfaceRefreshReason.Data)
            } catch (_: Exception) {}
            Result.success()
        } catch (ce: CancellationException) {
            Log.i(TAG, "TabbyRushSyncWorker cancelled: ${ce.message}")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error executing TabbyRushSyncWorker for $accountKeyValue: ${e.message}", e)
            Result.retry()
        }
    }
}
