package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.AccountKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderDeliveryGateTest {

    private val accountKey = AccountKey.create("http://localhost:3000", "user-123")

    @Test
    fun testMutationAdmissionAllowedWhenOpenAndRejectedWhenClosing() = runTest {
        var generation = 1L
        var wipeMarker = false

        val gate = ReminderDeliveryGate(
            accountKey = accountKey,
            getPersistedGeneration = { generation },
            hasPendingWipeMarker = { wipeMarker }
        )

        // Open: mutation runs
        val result = gate.withMutationAdmission { "success" }
        assertEquals("success", result)

        // Close gate
        gate.closeAndDrain()
        assertEquals(DeliveryGateState.CLOSED, gate.getState())

        // Mutating after close throws IllegalStateException
        assertFailsWith<IllegalStateException> {
            gate.withMutationAdmission { "should fail" }
        }
    }

    @Test
    fun testDeliveryFailsClosedWhenWipeMarkerPresent() = runTest {
        val gate = ReminderDeliveryGate(
            accountKey = accountKey,
            getPersistedGeneration = { 1L },
            hasPendingWipeMarker = { true } // Wipe in progress!
        )

        val deliveryResult = gate.withDeliveryAdmission(expectedGeneration = 1L) {
            "delivered"
        }
        assertNull(deliveryResult) // Fails closed!
    }

    @Test
    fun testDeliveryFailsClosedOnGenerationMismatch() = runTest {
        var generation = 2L

        val gate = ReminderDeliveryGate(
            accountKey = accountKey,
            getPersistedGeneration = { generation },
            hasPendingWipeMarker = { false }
        )

        // Alarm callback was scheduled under generation 1L
        val deliveryResult = gate.withDeliveryAdmission(expectedGeneration = 1L) {
            "delivered"
        }
        assertNull(deliveryResult) // Stale callback fails closed!
    }

    @Test
    fun testCloseAndDrainAwaitsInFlightDeliveries() = runTest {
        val gate = ReminderDeliveryGate(
            accountKey = accountKey,
            getPersistedGeneration = { 1L },
            hasPendingWipeMarker = { false }
        )

        val deliveryStarted = CompletableDeferred<Unit>()
        val deliveryCanFinish = CompletableDeferred<Unit>()
        var deliveryCompleted = false

        val deliveryJob = async {
            gate.withDeliveryAdmission(expectedGeneration = 1L) {
                deliveryStarted.complete(Unit)
                deliveryCanFinish.await()
                deliveryCompleted = true
                "done"
            }
        }

        deliveryStarted.await()

        // In-flight delivery is running; start drain concurrently
        val drainJob = async {
            gate.closeAndDrain(timeoutMs = 1000L)
        }
        yield()

        // New delivery attempts during closing fail closed immediately
        val rejectedDelivery = gate.withDeliveryAdmission(expectedGeneration = 1L) {
            "should not run"
        }
        assertNull(rejectedDelivery)

        assertFalse(deliveryCompleted)

        // Let in-flight delivery complete
        deliveryCanFinish.complete(Unit)
        deliveryJob.await()
        assertTrue(deliveryCompleted)

        val drained = drainJob.await()
        assertTrue(drained)
        assertEquals(DeliveryGateState.CLOSED, gate.getState())
    }
}
