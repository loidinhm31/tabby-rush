package dev.tabbyrush.android

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Non-exported BroadcastReceiver handling device restoration and system configuration events:
 * - BOOT_COMPLETED / USER_UNLOCKED (after credential-encrypted storage becomes accessible)
 * - MY_PACKAGE_REPLACED (app upgrades)
 * - TIME_SET / TIMEZONE_CHANGED (recalculates wake from stored instants)
 * - SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (exact alarm grant/revocation)
 *
 * Enforces wipe-first ordering and skips locked direct-boot storage fail-closed.
 */
class ReminderRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        if (!SUPPORTED_ACTIONS.contains(action)) {
            Log.w(TAG, "Unsupported broadcast action: $action; ignored")
            return
        }

        // Direct-boot storage barrier: refuse credential-encrypted DB access before unlock
        if (action != Intent.ACTION_USER_UNLOCKED) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager != null && !userManager.isUserUnlocked) {
                Log.w(TAG, "Storage locked; deferring restore until USER_UNLOCKED")
                return
            }
        }

        val app = context.applicationContext as? TabbyRushApplication ?: return

        // Bounded asynchronous restoration and wipe completion
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                withTimeoutOrNull(EXECUTION_TIMEOUT_MS) {
                    // 1. Wipe-first ordering: complete pending wipes before accessing account resources
                    app.completePendingWipesSuspend()
                    // 2. Identify active persisted account without guessing or default fallback
                    val active = app.readActiveAccountSync()
                    if (active == null) {
                        Log.i(TAG, "No active persisted account found; skipping restore")
                        TaskWidgetUpdater.clearAll(context)
                        return@withTimeoutOrNull
                    }

                    val accountKey = active.first
                    if (app.accountFiles.hasPendingWipe(accountKey)) {
                        Log.w(TAG, "Pending wipe marker present for $accountKey; skipping restore")
                        return@withTimeoutOrNull
                    }

                    val runtime = app.getOrCreateAccountRuntime(accountKey)
                    runtime.reminderCoordinator.reconcile()
                    Log.i(TAG, "Reminders reconciled successfully for $accountKey on $action")

                    val refreshReason = when (action) {
                        Intent.ACTION_BOOT_COMPLETED,
                        Intent.ACTION_USER_UNLOCKED,
                        Intent.ACTION_MY_PACKAGE_REPLACED -> SurfaceRefreshReason.Startup
                        Intent.ACTION_TIME_CHANGED,
                        Intent.ACTION_TIMEZONE_CHANGED -> SurfaceRefreshReason.Clock
                        ACTION_EXACT_ALARM_CHANGED -> SurfaceRefreshReason.Capability
                        else -> SurfaceRefreshReason.Startup
                    }
                    try {
                        app.taskSurfaces.refresh(refreshReason)
                    } catch (re: Exception) {
                        Log.w(TAG, "Error refreshing surfaces on $action: ${re.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reconciling reminders on $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderRestoreReceiver"
        private const val EXECUTION_TIMEOUT_MS = 9_000L

        private const val ACTION_EXACT_ALARM_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_CHANGED
        )
    }
}
