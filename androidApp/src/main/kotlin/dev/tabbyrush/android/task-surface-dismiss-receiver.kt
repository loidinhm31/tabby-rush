package dev.tabbyrush.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Private, unexported receiver handling notification dismissal (DeleteIntent) triggered by user.
 * Invariants:
 * - Dismissal routes through TaskSurfaceNotificationCoordinator.dismiss() to ensure atomic serialization
 *   with concurrent refresh calls.
 * - Dismissal requires valid active post receipt matching currently posted notification instance.
 * - Stale receipts (e.g. from prior replaced or programmatically cancelled posts) are harmlessly rejected.
 * - Atomically persists suppression before dispatching surface refresh.
 */
class TaskSurfaceDismissReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskSurfaceDismissRcvr"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskSurfaceIntents.ACTION_DISMISS_SURFACE_NOTIFICATION) {
            return
        }

        val accountKeyValue = intent.getStringExtra(TaskSurfaceIntents.EXTRA_ACCOUNT_KEY) ?: return
        val generation = intent.getLongExtra(TaskSurfaceIntents.EXTRA_GENERATION, -1L)
        if (generation < 0L) return
        val receipt = intent.getStringExtra(TaskSurfaceIntents.EXTRA_RECEIPT) ?: return
        val selectionKeyRaw = intent.getStringExtra(TaskSurfaceIntents.EXTRA_SELECTION_KEY) ?: return

        val selectionKey = DeadlineSelectionKeyCodec.decode(selectionKeyRaw)
        if (selectionKey == null) {
            Log.w(TAG, "Dismissal received with corrupt or invalid selection key. Dropping.")
            return
        }

        val hostOwner = context.applicationContext as? TaskSurfaceHostOwner
        if (hostOwner == null) {
            Log.w(TAG, "ApplicationContext does not implement TaskSurfaceHostOwner. Dropping dismiss action.")
            return
        }

        val host = hostOwner.taskSurfaces
        val accountKey = AccountKey(accountKeyValue)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val readiness = host.access.awaitReadiness(timeoutMs = 4_000L)
                if (readiness !is SurfaceReadiness.Ready) {
                    Log.w(TAG, "Surface is not ready ($readiness). Dropping dismissal for $accountKey.")
                    return@launch
                }

                val activeToken = readiness.token
                if (activeToken.accountKey != accountKey || activeToken.generation != generation) {
                    Log.w(
                        TAG,
                        "Token mismatch in dismissal (expected $accountKey gen $generation, active $activeToken). Stale dismissal ignored."
                    )
                    return@launch
                }

                val preferences = TaskSurfacePreferences(context.applicationContext)
                val coordinator = TaskSurfaceNotificationCoordinator(
                    context = context.applicationContext,
                    host = host,
                    preferences = preferences
                )
                val suppressed = coordinator.dismiss(selectionKey, receipt)
                if (suppressed) {
                    host.refresh(SurfaceRefreshReason.Data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing dismissal: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
