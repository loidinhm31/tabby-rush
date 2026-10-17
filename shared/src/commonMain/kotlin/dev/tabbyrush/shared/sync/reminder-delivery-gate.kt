package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.AccountKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * State of the ReminderDeliveryGate.
 */
enum class DeliveryGateState {
    OPEN,
    CLOSING,
    CLOSED
}

/**
 * ReminderDeliveryGate coordinates concurrency between:
 * 1. Store mutations (local save/delete/reconcile/scan/transition)
 * 2. Delivery callbacks (alarm triggered, claiming, and native posting)
 * 3. Account teardown (leave, logout, wipe)
 *
 * Enforces strict lock ordering:
 *   delivery gate admission -> store mutex -> DB transaction
 *   (Never inverse; never wait for network under gate admission).
 */
class ReminderDeliveryGate(
    val accountKey: AccountKey,
    val getPersistedGeneration: suspend () -> Long,
    val hasPendingWipeMarker: suspend () -> Boolean
) {
    private val stateMutex = Mutex()
    private var state = DeliveryGateState.OPEN
    private var inFlightDeliveries = 0
    private var inFlightMutations = 0
    private var drainSignal: CompletableDeferred<Unit>? = null

    suspend fun getState(): DeliveryGateState = stateMutex.withLock { state }

    /**
     * Executes [block] under mutation admission.
     * Enforces lock ordering: delivery gate -> store mutex -> DB transaction.
     */
    suspend fun <T> withMutationAdmission(block: suspend () -> T): T {
        stateMutex.withLock {
            check(state == DeliveryGateState.OPEN) {
                "Mutation admission rejected: delivery gate is $state"
            }
            inFlightMutations++
        }

        try {
            return block()
        } finally {
            stateMutex.withLock {
                inFlightMutations--
            }
        }
    }

    /**
     * Executes [block] under delivery callback admission.
     * Checks gate state, wipe marker, and persisted generation.
     * Fails closed if gate is closing/closed, wipe marker is present, or generation changed.
     */
    suspend fun <T> withDeliveryAdmission(
        expectedGeneration: Long,
        block: suspend () -> T
    ): T? {
        stateMutex.withLock {
            if (state != DeliveryGateState.OPEN) {
                return null // Fail closed
            }
            inFlightDeliveries++
        }

        try {
            // Check wipe marker and generation outside DB transaction
            if (hasPendingWipeMarker()) {
                return null // Fail closed: account is being wiped
            }
            val currentGen = try {
                getPersistedGeneration()
            } catch (_: Exception) {
                return null // Generation check failure fails closed
            }
            if (currentGen != expectedGeneration) {
                return null // Generation mismatch: stale callback
            }

            return block()
        } finally {
            val signalToComplete: CompletableDeferred<Unit>?
            stateMutex.withLock {
                inFlightDeliveries--
                if (state != DeliveryGateState.OPEN && inFlightDeliveries == 0) {
                    signalToComplete = drainSignal
                } else {
                    signalToComplete = null
                }
            }
            signalToComplete?.complete(Unit)
        }
    }

    /**
     * Closes delivery admission and drains all in-flight delivery callbacks.
     * Called during account leave / teardown before Room is closed.
     * Returns true if drained cleanly within [timeoutMs]; false if timed out.
     */
    suspend fun closeAndDrain(timeoutMs: Long = 5000L): Boolean {
        stateMutex.withLock {
            if (state == DeliveryGateState.CLOSED) return true
            state = DeliveryGateState.CLOSING
            if (inFlightDeliveries == 0 && inFlightMutations == 0) {
                state = DeliveryGateState.CLOSED
                return true
            }
        }

        val drained = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val done = stateMutex.withLock {
                    inFlightDeliveries == 0 && inFlightMutations == 0
                }
                if (done) break
                kotlinx.coroutines.delay(10)
            }
            true
        } ?: false

        stateMutex.withLock {
            if (drained && inFlightDeliveries == 0 && inFlightMutations == 0) {
                state = DeliveryGateState.CLOSED
            }
        }

        return drained
    }
}
