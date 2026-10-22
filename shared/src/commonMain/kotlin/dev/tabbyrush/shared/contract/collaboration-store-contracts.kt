package dev.tabbyrush.shared.contract

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * State of local account owner binding.
 *
 * Legacy rows created offline remain [UNRESOLVED_LOCAL] until explicit binding with
 * authenticated [AccountIdentity] validates that AccountKey.create(origin, userId)
 * matches the opened partition.
 *
 * Unresolved rows remain strictly local-only and are never sent or given synthetic TaskRef authority.
 */
enum class OwnerBindingState {
    UNRESOLVED_LOCAL,
    BOUND
}

/**
 * Snapshot of a synchronized canonical record in raw JSON form.
 */
@Serializable
data class CanonicalRecordSnapshot(
    val ref: CanonicalRecordRef,
    val dataJson: String,
    val version: Long,
    val syncedAt: Long,
    val deleted: Boolean = false
)

/**
 * Store managing collaborative access metadata, owner binding, staged reconciliation,
 * and access-loss evidence lifecycle.
 */
interface CollaborationStore {
    /**
     * Account-local persisted access generation.
     * Advanced on authoritative access invalidation; distinct from account generation and server revision.
     * Stale content responses and publications presenting an older generation are rejected fail-closed.
     */
    val accessGeneration: Long

    /**
     * Validates and binds legacy private rows to the authenticated user ID.
     * Returns [OwnerBindingState.BOUND] on success, or remains [OwnerBindingState.UNRESOLVED_LOCAL]
     * if the identity does not match the partition key.
     */
    suspend fun initializeOwnerBinding(identity: AccountIdentity): OwnerBindingState

    /**
     * Returns the current owner binding state.
     */
    suspend fun getOwnerBindingState(): OwnerBindingState

    /**
     * Look up current reconciled access state for a task.
     * Returns null if unrecorded or unavailable.
     */
    suspend fun accessFor(taskRef: TaskRef): TaskAccessState?

    /**
     * Observe reactive access state updates for a task.
     */
    fun observeAccessFor(taskRef: TaskRef): Flow<TaskAccessState?>

    /**
     * Stage a page of content records during full scan at the given server [revision].
     * Never visible until atomic promotion.
     */
    suspend fun stageDataPage(revision: Long, records: List<CanonicalRecordSnapshot>)

    /**
     * Stage access validation results at the given server [revision].
     */
    suspend fun stageAccessResults(revision: Long, results: List<TaskAccessState>)

    /**
     * Atomically promote all staged content pages and access decisions for [accessRevision].
     * Returns true if successfully promoted; false if revision mismatch or staging aborted.
     */
    suspend fun promoteReconciliation(accessRevision: Long, nowEpochMs: Long): Boolean

    /**
     * Discard any un-promoted staging state (e.g. on 409 ACCESS_REVISION_CHANGED or network abort).
     */
    suspend fun clearStagedReconciliation()

    /**
     * Invalidate access for a specific task upon receiving an authoritative ACCESS_DENIED or unavailable status.
     * Advances local [accessGeneration], purges active/review payloads, and writes content-free evidence.
     * Returns the new accessGeneration.
     */
    suspend fun invalidateAccess(taskRef: TaskRef, reason: AccessLossReason, nowEpochMs: Long): Long

    /**
     * Invalidate all collaborative access (e.g. upon whole-app membership denial).
     * Purges all shared collaborative records and advances [accessGeneration].
     */
    suspend fun invalidateAllSharedAccess(reason: AccessLossReason, nowEpochMs: Long): Long

    /**
     * Lists current non-replayable, content-free access loss evidence notices.
     */
    suspend fun listAccessLossEvidence(): List<AccessLossEvidence>

    /**
     * Dismiss a specific access loss evidence item after user review.
     */
    suspend fun dismissAccessLossEvidence(ref: TaskRef)
}

/**
 * Factory for opening [CollaborationStore] instances scoped to an account partition.
 */
interface CollaborationStoreFactory {
    fun open(accountKey: AccountKey): CollaborationStore
}
