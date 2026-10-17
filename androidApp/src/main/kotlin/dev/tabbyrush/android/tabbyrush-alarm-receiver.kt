package dev.tabbyrush.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import dev.tabbyrush.android.AndroidNotificationIntentFactory.ACTION_DELIVER_REMINDER
import dev.tabbyrush.android.AndroidNotificationIntentFactory.EXTRA_ACCOUNT_KEY
import dev.tabbyrush.android.AndroidNotificationIntentFactory.EXTRA_GENERATION
import dev.tabbyrush.android.AndroidNotificationIntentFactory.EXTRA_OCCURRENCE_INDEX
import dev.tabbyrush.android.AndroidNotificationIntentFactory.EXTRA_SCHEDULE_TOKEN
import dev.tabbyrush.android.AndroidNotificationIntentFactory.EXTRA_TASK_ID
import dev.tabbyrush.shared.contract.AccountKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Non-exported BroadcastReceiver handling AlarmManager reminder delivery.
 * Receives explicit intents, executes fail-closed unlock/wipe/generation verification,
 * and delivers reminders via ReminderSchedulingCoordinator under ReminderDeliveryGate.
 */
class TabbyRushAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_DELIVER_REMINDER) return

        // 1. Direct-boot / storage barrier: refuse DB access if user storage is locked
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        if (userManager != null && !userManager.isUserUnlocked) {
            Log.w(TAG, "Storage locked; ignoring reminder alarm until unlock")
            return
        }

        // 2. Parse identity extras (no user text in OS intent payload)
        val accountKeyStr = intent.getStringExtra(EXTRA_ACCOUNT_KEY)
        val generation = intent.getLongExtra(EXTRA_GENERATION, -1L)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        val scheduleToken = intent.getStringExtra(EXTRA_SCHEDULE_TOKEN)
        val occurrenceIndex = intent.getLongExtra(EXTRA_OCCURRENCE_INDEX, -1L).toInt()

        if (accountKeyStr.isNullOrBlank() || generation < 0L || taskId.isNullOrBlank() ||
            scheduleToken.isNullOrBlank() || occurrenceIndex < 0
        ) {
            Log.w(TAG, "Malformed reminder alarm intent payload; dropped")
            return
        }

        val app = context.applicationContext as? TabbyRushApplication ?: return
        val accountKey = AccountKey(accountKeyStr)

        // 3. Fail-closed wipe and generation barriers
        if (app.accountFiles.hasPendingWipe(accountKey)) {
            Log.w(TAG, "Pending wipe marker present for $accountKey; dropping alarm")
            return
        }

        val persistedGen = app.accountFiles.readGeneration(accountKey)
        if (persistedGen != generation) {
            Log.w(TAG, "Alarm generation mismatch for $accountKey (alarm=$generation, store=$persistedGen); dropping")
            return
        }

        // 4. Verify persisted account identity without fallback to default
        val active = app.readActiveAccountSync()
        if (active == null || active.first != accountKey) {
            Log.w(TAG, "Active account mismatch for $accountKey; dropping alarm")
            return
        }

        // 5. Bounded asynchronous execution
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                withTimeoutOrNull(EXECUTION_TIMEOUT_MS) {
                    val runtime = app.getOrCreateAccountRuntime(accountKey)
                    runtime.reminderCoordinator.deliverReminder(
                        taskId = taskId,
                        scheduleToken = scheduleToken,
                        occurrenceIndex = occurrenceIndex,
                        triggerGeneration = generation
                    )
                    runtime.reminderCoordinator.reconcile()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error executing reminder delivery: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TabbyRushAlarmReceiver"
        private const val EXECUTION_TIMEOUT_MS = 9_000L
    }
}
