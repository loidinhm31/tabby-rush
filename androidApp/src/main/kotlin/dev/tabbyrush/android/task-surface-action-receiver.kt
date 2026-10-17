package dev.tabbyrush.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Private, unexported receiver handling explicit Complete actions triggered from
 * the quiet task surface notification.
 * Enforces token and generation validation against active host readiness before mutation.
 */
class TaskSurfaceActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskSurfaceActionRcvr"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskSurfaceIntents.ACTION_COMPLETE_SURFACE_TASK) {
            return
        }

        val accountKeyValue = intent.getStringExtra(TaskSurfaceIntents.EXTRA_ACCOUNT_KEY) ?: return
        val generation = intent.getLongExtra(TaskSurfaceIntents.EXTRA_GENERATION, -1L)
        if (generation < 0L) return
        val taskId = intent.getStringExtra(TaskSurfaceIntents.EXTRA_TASK_ID) ?: return

        val hostOwner = context.applicationContext as? TaskSurfaceHostOwner
        if (hostOwner == null) {
            Log.w(TAG, "ApplicationContext does not implement TaskSurfaceHostOwner. Dropping complete action.")
            return
        }

        val host = hostOwner.taskSurfaces
        val accountKey = AccountKey(accountKeyValue)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val readiness = host.access.awaitReadiness(timeoutMs = 4_000L)
                if (readiness !is SurfaceReadiness.Ready) {
                    Log.w(TAG, "Surface is not ready ($readiness). Dropping complete action for $taskId.")
                    return@launch
                }

                val activeToken = readiness.token
                if (activeToken.accountKey != accountKey || activeToken.generation != generation) {
                    Log.w(
                        TAG,
                        "Token mismatch in complete action (expected $accountKey gen $generation, active $activeToken). Stale callback harmlessly ignored."
                    )
                    return@launch
                }

                val nowEpochMs = System.currentTimeMillis()
                val result = host.access.setCompleted(
                    token = activeToken,
                    taskId = taskId,
                    completed = true,
                    nowEpochMs = nowEpochMs
                )

                if (result is SurfaceResult.Value) {
                    host.refresh(SurfaceRefreshReason.Data)
                } else {
                    Log.w(TAG, "Complete action rejected or unavailable: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing complete action for $taskId: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
