package dev.tabbyrush.android

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.sync.LeaveSchedulerCancelGate
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
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

        val observerStarted = CompletableDeferred<Unit>()
        val observerJob = launch {
            try {
                observerStarted.complete(Unit)
                awaitCancellation()
            } finally {
                actions.add("schedule_observer_cleaned_up")
            }
        }
        observerStarted.await()

        val syncCancelGate = LeaveSchedulerCancelGate {
            actions.add("sync_cancelled")
        }

        val reminderCancelGate = LeaveSchedulerCancelGate {
            actions.add("reminders_cancelled")
        }

        val compositeGate = CompositeLeaveSchedulerCancelGate(
            deliveryGate = gate,
            scheduleObserverJob = observerJob,
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate
        )

        compositeGate.cancelAndAwait(testAccountKey)

        assertEquals(
            listOf("schedule_observer_cleaned_up", "sync_cancelled", "reminders_cancelled"),
            actions
        )
        assertTrue(observerJob.isCancelled, "Observer job must be cancelled and joined")
        assertEquals(dev.tabbyrush.shared.sync.DeliveryGateState.CLOSED, gate.getState())
    }

    @Test
    fun testProductionGateCancelsSurfaceObserverAndMaintainsQuiescenceOrder() = runTest {
        val actions = mutableListOf<String>()

        val gate = ReminderDeliveryGate(
            accountKey = testAccountKey,
            getPersistedGeneration = { 1L },
            hasPendingWipeMarker = { false }
        )

        val scheduleStarted = CompletableDeferred<Unit>()
        val scheduleJob = launch {
            try {
                scheduleStarted.complete(Unit)
                awaitCancellation()
            } finally {
                actions.add("schedule_observer_cleaned_up")
            }
        }
        scheduleStarted.await()

        val surfaceStarted = CompletableDeferred<Unit>()
        val surfaceJob = launch {
            try {
                surfaceStarted.complete(Unit)
                awaitCancellation()
            } finally {
                actions.add("surface_observer_cleaned_up")
            }
        }
        surfaceStarted.await()

        val syncCancelGate = LeaveSchedulerCancelGate {
            actions.add("sync_cancelled")
        }

        val reminderCancelGate = LeaveSchedulerCancelGate {
            actions.add("reminders_cancelled")
        }

        val compositeGate = CompositeLeaveSchedulerCancelGate(
            deliveryGate = gate,
            scheduleObserverJob = scheduleJob,
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate,
            surfaceObserverJob = surfaceJob
        )

        compositeGate.cancelAndAwait(testAccountKey)

        assertEquals(
            listOf(
                "schedule_observer_cleaned_up",
                "surface_observer_cleaned_up",
                "sync_cancelled",
                "reminders_cancelled"
            ),
            actions
        )
        assertTrue(scheduleJob.isCancelled, "Schedule observer must be cancelled")
        assertTrue(surfaceJob.isCancelled, "Surface observer must be cancelled and joined")
        assertEquals(dev.tabbyrush.shared.sync.DeliveryGateState.CLOSED, gate.getState())
    }

    @Test
    fun testProductionGateFailsClosedWhenDrainTimesOut() = runTest {
        val gate = ReminderDeliveryGate(
            accountKey = testAccountKey,
            getPersistedGeneration = { 1L },
            hasPendingWipeMarker = { false }
        )

        val inFlightAdmission = CompletableDeferred<Unit>()
        val releaseAdmission = CompletableDeferred<Unit>()

        // Hold an in-flight delivery admission
        val holderJob = launch {
            gate.withDeliveryAdmission(1L) {
                inFlightAdmission.complete(Unit)
                releaseAdmission.await()
            }
        }

        inFlightAdmission.await()

        val observerJob = launch {}
        val syncCancelGate = LeaveSchedulerCancelGate {}
        val reminderCancelGate = LeaveSchedulerCancelGate {}

        val compositeGate = CompositeLeaveSchedulerCancelGate(
            deliveryGate = gate,
            scheduleObserverJob = observerJob,
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate
        )

        // Drain timeout must throw and fail-closed
        assertFailsWith<IllegalStateException> {
            compositeGate.cancelAndAwait(testAccountKey)
        }

        // Release the held admission and wait for coroutine to finish
        releaseAdmission.complete(Unit)
        holderJob.join()
    }

    @Test
    fun testProductionGateFailsClosedWhenSyncCancelThrows() = runTest {
        val gate = ReminderDeliveryGate(
            accountKey = testAccountKey,
            getPersistedGeneration = { 1L },
            hasPendingWipeMarker = { false }
        )

        val observerJob = launch {}
        val syncCancelGate = LeaveSchedulerCancelGate {
            throw RuntimeException("Simulated WorkManager cancellation failure")
        }
        val reminderCancelGate = LeaveSchedulerCancelGate {}

        val compositeGate = CompositeLeaveSchedulerCancelGate(
            deliveryGate = gate,
            scheduleObserverJob = observerJob,
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate
        )

        assertFailsWith<RuntimeException> {
            compositeGate.cancelAndAwait(testAccountKey)
        }
    }
}
