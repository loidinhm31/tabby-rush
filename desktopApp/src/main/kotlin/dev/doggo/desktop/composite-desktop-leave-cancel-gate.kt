package dev.doggo.desktop

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.sync.LeaveSchedulerCancelGate
import dev.doggo.shared.sync.ReminderDeliveryGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

/**
 * Composite cancel gate coordinating clean quiescence across desktop runtime producers:
 * 1. [ReminderDeliveryGate] (stops new reminder deliveries, drains active callbacks)
 * 2. Surface cancel gate (stops new surface operations/mutations, drains in-flight surface work)
 * 3. Surface observer job (cancels reactive Room surface todos observation)
 * 4. Schedule observation job (cancels reactive Room schedule updates)
 * 5. [DesktopReminderScheduler] (cancels desktop wake loop and clears pending alarm handles)
 * 6. Sync scheduler cancel gate (cancels periodic/immediate sync rounds, awaits active sync jobs)
 * 7. Reminder coordinator cancel gate (cancels and clears native platform notifications)
 *
 * Invariants:
 * If any cancellation or drain step fails or times out, throws an exception so
 * [dev.doggo.shared.sync.AccountLeaveProtocol] remains fail-closed with wipe markers intact
 * and database files protected.
 */
class CompositeDesktopLeaveCancelGate(
    private val deliveryGate: ReminderDeliveryGate,
    private val scheduleObserverJob: Job,
    private val reminderScheduler: DesktopReminderScheduler,
    private val syncCancelGate: LeaveSchedulerCancelGate,
    private val reminderCancelGate: LeaveSchedulerCancelGate,
    private val surfaceObserverJob: Job? = null,
    private val surfaceCancelGate: LeaveSchedulerCancelGate? = null,
    private val drainTimeoutMs: Long = 5000L
) : LeaveSchedulerCancelGate {

    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        // 1. Close delivery gate and drain in-flight delivery admissions
        val drained = deliveryGate.closeAndDrain(drainTimeoutMs)
        if (!drained) {
            throw IllegalStateException("Timed out draining in-flight reminder deliveries for $accountKey")
        }

        // 2. Drain surface admissions before stopping observers
        surfaceCancelGate?.cancelAndAwait(accountKey)

        // 3. Stop reactive task surface observer
        surfaceObserverJob?.cancelAndJoin()

        // 4. Stop reactive schedule observation
        scheduleObserverJob.cancelAndJoin()

        // 5. Stop desktop wake loop and clear pending alarms
        reminderScheduler.cancelAndAwait(accountKey)

        // 6. Cancel and await sync jobs
        syncCancelGate.cancelAndAwait(accountKey)

        // 7. Cancel and remove all platform notifications
        reminderCancelGate.cancelAndAwait(accountKey)
    }
}
