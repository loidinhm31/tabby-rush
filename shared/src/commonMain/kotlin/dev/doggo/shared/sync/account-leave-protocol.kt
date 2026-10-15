package dev.doggo.shared.sync

import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore

/**
 * LeaveReviewCounts represents unresolved local work that must be reviewed
 * before executing a standard account leave.
 */
data class LeaveReviewCounts(
    val draftCount: Int,
    val pendingCount: Int,
    val uncertainCount: Int,
    val conflictCount: Int,
    val unknownSupersededCount: Int
) {
    val totalUnresolved: Int
        get() = draftCount + pendingCount + uncertainCount + conflictCount + unknownSupersededCount

    val hasReviewWork: Boolean
        get() = totalUnresolved > 0
}

/**
 * Hook to await background WorkManager or job scheduler cancellation.
 */
fun interface LeaveSchedulerCancelGate {
    suspend fun cancelAndAwait(accountKey: AccountKey)
}

/**
 * AccountLeaveProtocol enforces the crash-safe, fail-closed account leave sequence (C7):
 * 1. Count unresolved work (drafts, pending, uncertain, conflict, unknownSuperseded).
 * 2. Guard against leave unless work is resolved or explicit discard is requested.
 * 3. Exact teardown order:
 *    a. Freeze edits and scheduling.
 *    b. Increment and persist next generation + wipe marker outside the database.
 *       (Abort before deleting anything if persistence fails).
 *    c. Cancel and await scheduler and owned background jobs. (Fails closed on error).
 *    d. Close Room database connection.
 *    e. Delete database files (DB, WAL, SHM).
 *       (If deletion fails, remain fail-closed with marker intact).
 *    f. Remove credentials from CredentialVault and clear session.
 *    g. Clear wipe marker.
 */
class AccountLeaveProtocol(
    val store: RoomAccountStore,
    val session: HubAuthSession,
    val onReadGeneration: (AccountKey) -> Long = { 0L },
    val onWriteGeneration: (AccountKey, Long) -> Unit = { _, _ -> },
    val onWriteWipeMarker: (AccountKey, Long) -> Unit,
    val onClearWipeMarker: (AccountKey) -> Unit,
    val onDeleteDatabaseArtifacts: (AccountKey) -> Boolean,
    val schedulerCancelGate: LeaveSchedulerCancelGate? = null
) {

    suspend fun checkReviewWork(): LeaveReviewCounts {
        val draftCount = store.dao.countDrafts()
        val pendingCount = store.dao.countOutboxByState(OutboxEntity.STATE_PENDING) +
                store.dao.countOutboxByState(OutboxEntity.STATE_DISPATCHING)
        val uncertainCount = store.dao.countOutboxByState(OutboxEntity.STATE_UNCERTAIN)
        val conflictCount = store.dao.countOutboxByState(OutboxEntity.STATE_CONFLICT)
        val unknownSupersededCount = store.dao.countOutboxByState(OutboxEntity.STATE_UNKNOWN_SUPERSEDED)

        return LeaveReviewCounts(
            draftCount = draftCount,
            pendingCount = pendingCount,
            uncertainCount = uncertainCount,
            conflictCount = conflictCount,
            unknownSupersededCount = unknownSupersededCount
        )
    }

    suspend fun executeLeave(): Boolean {
        // Freeze store before review check so no intervening edit occurs (W-A)
        store.isFrozen = true
        val review = checkReviewWork()
        if (review.hasReviewWork) {
            store.isFrozen = false
            return false // Guarded: Cannot leave without resolving pending work or discarding
        }
        return performGuardedTeardown()
    }

    suspend fun discardAndLeave(): Boolean {
        return performGuardedTeardown()
    }

    private suspend fun performGuardedTeardown(): Boolean {
        // a. Freeze edits and scheduling (C7)
        store.isFrozen = true

        // Close delivery gate and drain in-flight callbacks and mutations before DB closure
        val drained = store.deliveryGate?.closeAndDrain(5000L) ?: true
        if (!drained) {
            return false // Drain timeout: remain frozen fail-closed without deleting artifacts
        }
        val accountKey = store.accountKey

        // b. Increment and persist new generation + wipe marker outside the database (C7)
        val currentGen = try {
            onReadGeneration(accountKey)
        } catch (_: Exception) {
            // Generation read failure: abort fail-closed
            return false
        }
        if (currentGen == Long.MAX_VALUE) {
            // Overflow: abort fail-closed
            return false
        }
        val nextGeneration = currentGen + 1L

        try {
            onWriteGeneration(accountKey, nextGeneration)
            onWriteWipeMarker(accountKey, nextGeneration)
        } catch (_: Exception) {
            // Failed marker persistence aborts leave before deleting anything; remains fail-closed
            return false
        }

        // c. Cancel and await scheduler / background work (fail-closed if cancelled/failed) (C7, Phase 07)
        try {
            schedulerCancelGate?.cancelAndAwait(accountKey)
        } catch (_: Exception) {
            // Phase 07: Failed cancellation retains wipe marker and remains frozen fail-closed!
            return false
        }

        // d. Close Room database handles
        store.close()

        // e. Delete DB, WAL, SHM, and artifacts
        val deleted = try {
            onDeleteDatabaseArtifacts(accountKey)
        } catch (_: Exception) {
            false
        }

        if (!deleted) {
            // Failed deletion stays fail-closed with wipe marker intact!
            return false
        }

        // f. Remove credentials from vault and clear session
        session.logout()

        // g. Clear wipe marker
        try {
            onClearWipeMarker(accountKey)
        } catch (_: Exception) {
            // Benign if marker already gone
        }

        return true
    }

    companion object {
        /**
         * Startup crash recovery: checks if a pending wipe was interrupted,
         * and completes file deletion and credential cleanup before opening (C7).
         */
        suspend fun completePendingWipe(
            accountKey: AccountKey,
            hasPendingWipe: (AccountKey) -> Boolean,
            onDeleteDatabaseArtifacts: (AccountKey) -> Boolean,
            onClearVault: suspend (AccountKey) -> Unit,
            onClearWipeMarker: (AccountKey) -> Unit,
            schedulerCancelGate: LeaveSchedulerCancelGate? = null
        ): Boolean {
            if (!hasPendingWipe(accountKey)) {
                return false
            }
            try {
                schedulerCancelGate?.cancelAndAwait(accountKey)
            } catch (_: Exception) {
                return false // Retain wipe marker if scheduler cancellation fails
            }
            val deleted = onDeleteDatabaseArtifacts(accountKey)
            if (deleted) {
                onClearVault(accountKey)
                onClearWipeMarker(accountKey)
            }
            return deleted
        }
    }
}
