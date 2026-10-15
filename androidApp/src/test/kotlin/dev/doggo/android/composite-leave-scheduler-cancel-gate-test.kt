package dev.doggo.android

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.sync.LeaveSchedulerCancelGate
import dev.doggo.shared.sync.ReminderDeliveryGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompositeLeaveSchedulerCancelGateTest {

    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

    @Test
    fun testProductionGateDrainsAndCancelsInOrder() = runTest {
        val actions = mutableListOf<String>()

        val gate = ReminderDeliveryGate(
            accountKey = testAccountKey,
            getPersistedGeneration = { 1L },
            hasPendingWipeMarker = { false }
        )

        val observerJob = launch {
            // Simulated active observer coroutine
        }

        val syncCancelGate = LeaveSchedulerCancelGate {
            actions.add("sync_cancelled")
        }

        val reminderCancelGate = LeaveSchedulerCancelGate {
            actions.add("reminders_cancelled")
        }

        // Test the production CompositeLeaveSchedulerCancelGate directly
        val compositeGate = CompositeLeaveSchedulerCancelGate(
            deliveryGate = gate,
            scheduleObserverJob = observerJob,
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate
        )

        compositeGate.cancelAndAwait(testAccountKey)

        assertEquals(
            listOf("sync_cancelled", "reminders_cancelled"),
            actions
        )
        assertTrue(observerJob.isCancelled, "Observer job must be cancelled and joined")
        assertEquals(dev.doggo.shared.sync.DeliveryGateState.CLOSED, gate.getState())
    }

    @Test
    fun testProductionGateFailsClosedWhenDrainTimesOut() = runTest {
        val gate = ReminderDeliveryGate(
            accountKey = testAccountKey,
            getPersistedGeneration = { 1L },
            hasPendingWipeMarker = { false }
        )

        // Simulate blocked delivery admission that prevents draining
        val blockCompleted = CompletableDeferred<Unit>()
        val inFlightJob = launch {
            gate.withDeliveryAdmission(expectedGeneration = 1L) {
                blockCompleted.await()
            }
        }
        yield()

        val observerJob = launch {}

        // Mock delivery gate with zero timeout by closing ahead
        gate.closeAndDrain(timeoutMs = 10L)

        val compositeGate = CompositeLeaveSchedulerCancelGate(
            deliveryGate = gate,
            scheduleObserverJob = observerJob,
            syncCancelGate = { /* should not be called */ },
            reminderCancelGate = { /* should not be called */ }
        )

        assertFailsWith<IllegalStateException> {
            compositeGate.cancelAndAwait(testAccountKey)
        }

        blockCompleted.complete(Unit)
        inFlightJob.join()
    }

    @Test
    fun testProductionGateFailsClosedWhenSyncCancelThrows() = runTest {
        val gate = ReminderDeliveryGate(
            accountKey = testAccountKey,
            getPersistedGeneration = { 1L },
            hasPendingWipeMarker = { false }
        )
        val observerJob = launch {}

        val compositeGate = CompositeLeaveSchedulerCancelGate(
            deliveryGate = gate,
            scheduleObserverJob = observerJob,
            syncCancelGate = { throw IllegalStateException("Sync cancellation failure") },
            reminderCancelGate = { /* should not be reached */ }
        )

        assertFailsWith<IllegalStateException> {
            compositeGate.cancelAndAwait(testAccountKey)
        }
    }
}
