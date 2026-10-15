package dev.doggo.desktop

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.sync.LeaveSchedulerCancelGate
import dev.doggo.shared.sync.ReminderDeliveryGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

/**
 * Composite cancel gate coordinating clean quiescence across desktop runtime producers:
 * 1. [ReminderDeliveryGate] (stops new deliveries, drains active callbacks)
 * 2. Schedule observation job (cancels reactive Room schedule updates)
 * 3. [DesktopReminderScheduler] (cancels desktop wake loop and clears pending alarm handles)
 * 4. Sync scheduler cancel gate (cancels periodic/immediate sync rounds, awaits active sync jobs)
 * 5. Reminder coordinator cancel gate (cancels and clears native platform notifications)
 *
 * Invariants:
 * If any cancellation step fails, throws an exception so [dev.doggo.shared.sync.AccountLeaveProtocol]
 * remains fail-closed with wipe markers intact and database files protected.
 */
class CompositeDesktopLeaveCancelGate(
    private val deliveryGate: ReminderDeliveryGate,
    private val scheduleObserverJob: Job,
    private val reminderScheduler: DesktopReminderScheduler,
    private val syncCancelGate: LeaveSchedulerCancelGate,
    private val reminderCancelGate: LeaveSchedulerCancelGate,
    private val drainTimeoutMs: Long = 5000L
) : LeaveSchedulerCancelGate {

    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        // 1. Close delivery gate and drain in-flight delivery admissions
        val drained = deliveryGate.closeAndDrain(drainTimeoutMs)
        if (!drained) {
            throw IllegalStateException("Timed out draining in-flight reminder deliveries for $accountKey")
        }

        // 2. Stop reactive schedule observation
        scheduleObserverJob.cancelAndJoin()

        // 3. Stop desktop wake loop and clear pending alarms
        reminderScheduler.cancelAndAwait(accountKey)

        // 4. Cancel and await sync jobs
        syncCancelGate.cancelAndAwait(accountKey)

        // 5. Cancel and remove all platform notifications
        reminderCancelGate.cancelAndAwait(accountKey)
    }
}
