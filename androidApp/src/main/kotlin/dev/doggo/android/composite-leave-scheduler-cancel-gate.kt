package dev.doggo.android

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.sync.LeaveSchedulerCancelGate
import dev.doggo.shared.sync.ReminderDeliveryGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

/**
 * Composite cancel gate coordinating clean quiescence across:
 * 1. ReminderDeliveryGate (stops new deliveries, drains active callbacks)
 * 2. Schedule observation job (cancels reactive schedule updates)
 * 3. SyncSchedulerCancelGate (cancels WorkManager tasks, awaits active sync rounds)
 * 4. ReminderCoordinatorCancelGate (cancels AlarmManager alarms, removes notifications)
 *
 * If any cancellation step fails, throws an exception so AccountLeaveProtocol
 * remains fail-closed with wipe markers intact.
 */
class CompositeLeaveSchedulerCancelGate(
    private val deliveryGate: ReminderDeliveryGate,
    private val scheduleObserverJob: Job,
    private val syncCancelGate: LeaveSchedulerCancelGate,
    private val reminderCancelGate: LeaveSchedulerCancelGate
) : LeaveSchedulerCancelGate {

    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        // 1. Close delivery gate and drain in-flight delivery admissions
        val drained = deliveryGate.closeAndDrain()
        if (!drained) {
            throw IllegalStateException("Timed out draining in-flight reminder deliveries for $accountKey")
        }

        // 2. Stop reactive schedule observation
        scheduleObserverJob.cancelAndJoin()

        // 3. Cancel and await background WorkManager sync jobs
        syncCancelGate.cancelAndAwait(accountKey)

        // 4. Cancel all OS alarms and clear posted notifications
        reminderCancelGate.cancelAndAwait(accountKey)
    }
}
