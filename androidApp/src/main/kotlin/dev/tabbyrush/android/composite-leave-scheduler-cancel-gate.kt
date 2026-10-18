package dev.tabbyrush.android

import android.app.NotificationManager
import android.content.Context
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.RemoteInboxController
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteQuiesceResult
import dev.tabbyrush.shared.sync.LeaveSchedulerCancelGate
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

/**
 * Composite cancel gate coordinating clean quiescence across:
 * 1. ReminderDeliveryGate (stops new deliveries, drains active admissions)
 * 2. Schedule and task surface observation jobs (cancels reactive updates)
 * 3. SyncSchedulerCancelGate (cancels WorkManager tasks, awaits active sync rounds)
 * 4. ReminderCoordinatorCancelGate (cancels AlarmManager alarms, removes notifications)
 * 5. Task surface OS state: WorkManager boundary wake, quiet task notification,
 *    all installed widget instances (clearAllInstances), and account preferences.
 *
 * If any cancellation step fails, throws an exception so AccountLeaveProtocol
 * remains fail-closed with wipe markers intact.
 */
class CompositeLeaveSchedulerCancelGate(
    private val deliveryGate: ReminderDeliveryGate,
    private val scheduleObserverJob: Job,
    private val syncCancelGate: LeaveSchedulerCancelGate,
    private val reminderCancelGate: LeaveSchedulerCancelGate,
    private val surfaceObserverJob: Job? = null,
    private val context: Context? = null,
    private val host: AndroidTaskSurfaceHost? = null,
    private val remoteController: RemoteInboxController? = null,
    private val remoteAdapter: RemoteNotificationAdapter? = null,
    private val installCoordinator: RemoteInstallCoordinator? = null,
    private val userId: String? = null,
    private val persistedGen: Long = 0L,
    private val capturedAccessToken: String? = null
) : LeaveSchedulerCancelGate {

    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        // 1. Close delivery gate and drain in-flight delivery admissions
        val drained = deliveryGate.closeAndDrain()
        if (!drained) {
            throw IllegalStateException("Timed out draining in-flight reminder deliveries for $accountKey")
        }

        // 2. Quiesce remote controller and drain in-flight remote operations
        remoteController?.let { controller ->
            val remoteQuiesce = controller.quiesce()
            if (remoteQuiesce is RemoteQuiesceResult.TimedOut) {
                throw IllegalStateException("Timed out quiescing remote inbox controller for $accountKey; failing closed")
            }
            controller.close()
        }
        // 2. Stop reactive schedule and surface observation
        scheduleObserverJob.cancelAndJoin()
        surfaceObserverJob?.cancelAndJoin()

        // 3. Cancel and await background WorkManager sync jobs
        syncCancelGate.cancelAndAwait(accountKey)

        // 4. Cancel all OS alarms and clear posted reminder notifications
        reminderCancelGate.cancelAndAwait(accountKey)

        // 5. Clear OS task surface state and cancel scheduled boundary wake
        context?.let { ctx ->
            TaskSurfaceRefreshWorker.cancelBoundaryWake(ctx, accountKey)

            if (host != null) {
                val preferences = TaskSurfacePreferences(ctx)
                val notificationCoordinator = TaskSurfaceNotificationCoordinator(
                    context = ctx,
                    host = host,
                    preferences = preferences
                )
                notificationCoordinator.cancelForAccount(accountKey)
            } else {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(TaskSurfaceIntents.NOTIFICATION_TAG, TaskSurfaceIntents.NOTIFICATION_ID)
            }

            TaskWidgetUpdater.clearAllInstances(ctx)
            TaskSurfacePreferences(ctx).clear(accountKey)
        }

        // 6. Clear posted remote announcement notifications (scoped to remote tags only)
        remoteAdapter?.clearRemote(persistedGen)

        // 7. Enqueue best-effort install unbind (non-blocking for local leave)
        if (installCoordinator != null && !userId.isNullOrBlank()) {
            installCoordinator.scheduleBestEffortUnbind(
                accountKey = accountKey.value,
                userId = userId,
                persistedGen = persistedGen,
                capturedAccessToken = capturedAccessToken
            )
        }
    }
}
