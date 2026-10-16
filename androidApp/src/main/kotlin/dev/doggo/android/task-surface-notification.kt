package dev.doggo.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.DeadlineSelectionKey
import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.contract.SurfaceSnapshot
import dev.doggo.shared.contract.TaskDeadline
import dev.doggo.shared.contract.selectNextDeadline
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Coordinator for the quiet task surface deadline notification.
 * Invariants:
 * - Operates on a dedicated low-importance, no-sound, no-vibration channel.
 * - Selection includes incomplete saved deadlines and unresolved conflicts, independent of reminder rules.
 * - Generates private detailed notification and generic public lock-screen version.
 * - Entire refresh, dismissal, and cancel pipeline is serialized under a coordinator mutex to prevent races.
 * - Pre-persists post receipt before calling OS notify() and fails closed on write failure.
 * - Reuses active post receipt across identical-selection refreshes to avoid race conditions with swiping.
 * - Token admission fence via host.access.publishIfCurrent before posting.
 * - Stale token refreshes are fenced before any destructive side effects or cross-account cancellations.
 */
class TaskSurfaceNotificationCoordinator(
    private val context: Context,
    private val host: AndroidTaskSurfaceHost,
    private val preferences: TaskSurfacePreferences
) {

    companion object {
        private const val TAG = "TaskSurfaceNotifCoord"
        private val coordinatorMutex = Mutex()

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val existing = nm.getNotificationChannel(TaskSurfaceIntents.CHANNEL_ID)
                if (existing != null && existing.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                    nm.deleteNotificationChannel(TaskSurfaceIntents.CHANNEL_ID)
                }
                if (nm.getNotificationChannel(TaskSurfaceIntents.CHANNEL_ID) == null) {
                    val name = try {
                        context.getString(R.string.task_surface_channel_name)
                    } catch (_: Exception) {
                        "Next task deadline"
                    }
                    val description = try {
                        context.getString(R.string.task_surface_channel_description)
                    } catch (_: Exception) {
                        "Quiet notification showing the next overdue or upcoming task deadline"
                    }

                    val channel = NotificationChannel(
                        TaskSurfaceIntents.CHANNEL_ID,
                        name,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        this.description = description
                        enableLights(false)
                        enableVibration(false)
                        setSound(null, null)
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }

        fun resolveNotificationIcon(context: Context): Int {
            return try {
                val resId = context.resources.getIdentifier(
                    "task_surface_notification_icon",
                    "drawable",
                    context.packageName
                )
                if (resId != 0) resId else android.R.drawable.ic_dialog_info
            } catch (_: Exception) {
                android.R.drawable.ic_dialog_info
            }
        }

        fun formatDeadlineDisplay(context: Context, deadline: TaskDeadline, nowEpochMs: Long): String {
            return if (nowEpochMs >= deadline.overdueEpochMs) {
                try {
                    context.getString(R.string.task_surface_overdue)
                } catch (_: Exception) {
                    "Overdue"
                }
            } else {
                val formattedTarget = if (deadline.time != null) {
                    "${deadline.date} ${deadline.time}"
                } else {
                    deadline.date
                }
                try {
                    context.getString(R.string.task_surface_due, formattedTarget)
                } catch (_: Exception) {
                    "Due $formattedTarget"
                }
            }
        }
    }

    suspend fun refresh(
        token: SurfaceAccountToken,
        snapshot: SurfaceSnapshot,
        nowEpochMs: Long
    ) = coordinatorMutex.withLock {
        // Strict readiness and token fence before any destructive side effects
        val readiness = host.access.readiness.value
        if (readiness !is SurfaceReadiness.Ready || readiness.token != token) {
            Log.w(TAG, "Dropping stale refresh: host readiness is $readiness, requested token is $token")
            return@withLock
        }

        if (snapshot.token != token) {
            Log.w(TAG, "Snapshot token ${snapshot.token} does not match target token $token. Dropping refresh.")
            return@withLock
        }

        ensureNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return@withLock

        // 1. Check feature opt-in preference
        val prefState = preferences.read(token.accountKey)
        if (!prefState.enabled) {
            TaskSurfaceRefreshWorker.cancelBoundaryWake(context, token.accountKey)
            if (prefState.postedKey != null || prefState.postedReceipt != null) {
                preferences.invalidatePost(token.accountKey)
                nm.cancel(TaskSurfaceIntents.NOTIFICATION_TAG, TaskSurfaceIntents.NOTIFICATION_ID)
            }
            return@withLock
        }

        // 2. Check capability and permissions (preserve suppression on capability changes)
        val availability = TaskSurfacePermissionController.evaluateAvailability(
            context = context,
            enabled = true,
            channelId = TaskSurfaceIntents.CHANNEL_ID
        )
        if (availability != TaskSurfaceAvailability.Available) {
            Log.d(TAG, "Surface notification unavailable ($availability). Cancelling notification if posted by this account.")
            TaskSurfaceRefreshWorker.cancelBoundaryWake(context, token.accountKey)
            if (prefState.postedKey != null || prefState.postedReceipt != null) {
                preferences.invalidatePost(token.accountKey)
                nm.cancel(TaskSurfaceIntents.NOTIFICATION_TAG, TaskSurfaceIntents.NOTIFICATION_ID)
            }
            return@withLock
        }

        // 3. Selection from base + submitted schedules (no drafts, includes conflicts)
        val selection = selectNextDeadline(snapshot.schedules, nowEpochMs)
        if (selection == null) {
            // Selection disappeared => clear suppression and active post
            TaskSurfaceRefreshWorker.cancelBoundaryWake(context, token.accountKey)
            preferences.reconcileSelection(token, null)
            nm.cancel(TaskSurfaceIntents.NOTIFICATION_TAG, TaskSurfaceIntents.NOTIFICATION_ID)
            return@withLock
        }

        val targetKey = DeadlineSelectionKey(
            token = token,
            taskId = selection.taskId,
            deadline = selection.deadline
        )

        // 4. Reconcile selection state in durable preferences
        val updatedPrefs = preferences.reconcileSelection(token, targetKey)
        if (updatedPrefs.suppressed == targetKey || updatedPrefs.hasCorruptSuppression) {
            Log.d(TAG, "Selection key for task ${selection.taskId} is suppressed by user dismissal.")
            if (updatedPrefs.postedKey != null || updatedPrefs.postedReceipt != null) {
                preferences.invalidatePost(token.accountKey)
                nm.cancel(TaskSurfaceIntents.NOTIFICATION_TAG, TaskSurfaceIntents.NOTIFICATION_ID)
            }
            return@withLock
        }

        // 5. Stable receipt allocation: reuse active receipt if same key is already posted
        val receipt = if (updatedPrefs.postedKey == targetKey && !updatedPrefs.postedReceipt.isNullOrBlank()) {
            updatedPrefs.postedReceipt
        } else {
            val freshReceipt = UUID.randomUUID().toString()
            try {
                preferences.recordPost(targetKey, freshReceipt)
                freshReceipt
            } catch (e: Exception) {
                Log.e(TAG, "Failed committing post receipt: ${e.message}. Failing closed without posting.", e)
                return@withLock
            }
        }

        // 6. Build private and public generic notification instances
        val smallIcon = resolveNotificationIcon(context)
        val publicTitle = try {
            context.getString(R.string.task_surface_public_title)
        } catch (_: Exception) {
            "Next task deadline"
        }
        val publicContent = try {
            context.getString(R.string.task_surface_public_content)
        } catch (_: Exception) {
            "Open Doggo to view"
        }

        val publicVersion = Notification.Builder(context, TaskSurfaceIntents.CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(publicTitle)
            .setContentText(publicContent)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        val conflictSuffix = if (selection.hasUnresolvedConflict) {
            try {
                context.getString(R.string.task_surface_conflict_suffix)
            } catch (_: Exception) {
                " (Sync conflict)"
            }
        } else {
            ""
        }
        val contentTitle = selection.title + conflictSuffix
        val contentText = formatDeadlineDisplay(context, selection.deadline, nowEpochMs)

        val completePendingIntent = TaskSurfaceIntents.createCompletePendingIntent(
            context,
            token,
            selection.taskId
        )
        val completeLabel = try {
            context.getString(R.string.task_surface_action_complete)
        } catch (_: Exception) {
            "Complete"
        }
        val completeAction = Notification.Action.Builder(
            null,
            completeLabel,
            completePendingIntent
        ).build()

        val openPendingIntent = TaskSurfaceIntents.createOpenPendingIntent(
            context,
            token,
            selection.taskId
        )
        val dismissPendingIntent = TaskSurfaceIntents.createDismissPendingIntent(
            context,
            targetKey,
            receipt
        )

        val privateNotification = Notification.Builder(context, TaskSurfaceIntents.CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(completeAction)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        // 7. Gate final publication through current token admission
        val published = host.access.publishIfCurrent(token) {
            try {
                nm.notify(
                    TaskSurfaceIntents.NOTIFICATION_TAG,
                    TaskSurfaceIntents.NOTIFICATION_ID,
                    privateNotification
                )
            } catch (se: SecurityException) {
                Log.w(TAG, "SecurityException posting task surface notification: ${se.message}")
                preferences.invalidatePost(token.accountKey)
            }
        }

        if (!published) {
            Log.w(TAG, "Publication rejected for token $token. Invalidating local post receipt.")
            preferences.invalidatePost(token.accountKey)
            // Do NOT cancel notification globally: another account token may have published!
            return@withLock
        }

        // 8. Schedule boundary wake for future deadline if applicable
        if (selection.deadline.overdueEpochMs > nowEpochMs) {
            TaskSurfaceRefreshWorker.scheduleBoundaryWake(
                context = context,
                accountKey = token.accountKey,
                generation = token.generation,
                targetEpochMs = selection.deadline.overdueEpochMs,
                nowEpochMs = nowEpochMs
            )
        } else {
            TaskSurfaceRefreshWorker.cancelBoundaryWake(context, token.accountKey)
        }
    }

    /**
     * Atomically processes notification dismissal inside the coordinator mutex,
     * verifying active token readiness and receipt match before persisting suppression
     * and removing the notification.
     */
    suspend fun dismiss(key: DeadlineSelectionKey, receipt: String): Boolean = coordinatorMutex.withLock {
        val readiness = host.access.readiness.value
        if (readiness !is SurfaceReadiness.Ready || readiness.token != key.token) {
            Log.w(TAG, "Dismissal rejected: token ${key.token} is not current active readiness ($readiness)")
            return@withLock false
        }

        val suppressed = preferences.suppressIfCurrent(key, receipt)
        if (!suppressed) {
            Log.i(TAG, "Dismissal receipt $receipt did not match active post receipt. Stale dismissal ignored.")
            return@withLock false
        }

        TaskSurfaceRefreshWorker.cancelBoundaryWake(context, key.token.accountKey)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(TaskSurfaceIntents.NOTIFICATION_TAG, TaskSurfaceIntents.NOTIFICATION_ID)

        Log.i(TAG, "Successfully suppressed selection key for task ${key.taskId} via coordinator.")
        true
    }

    suspend fun cancelForAccount(accountKey: AccountKey) = coordinatorMutex.withLock {
        TaskSurfaceRefreshWorker.cancelBoundaryWake(context, accountKey)

        val prefState = preferences.read(accountKey)
        if (prefState.postedKey != null || prefState.postedReceipt != null) {
            try {
                preferences.invalidatePost(accountKey)
            } catch (e: Exception) {
                Log.w(TAG, "Error invalidating post during cancelForAccount: ${e.message}")
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(TaskSurfaceIntents.NOTIFICATION_TAG, TaskSurfaceIntents.NOTIFICATION_ID)
        }

        try {
            preferences.clear(accountKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing preferences for $accountKey during cancelForAccount: ${e.message}", e)
            throw e
        }
    }
}
