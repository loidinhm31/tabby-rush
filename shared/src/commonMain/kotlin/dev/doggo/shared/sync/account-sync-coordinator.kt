package dev.doggo.shared.sync

import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.StrictSyncContract
import dev.doggo.shared.contract.StrictSyncDigest
import dev.doggo.shared.contract.StrictTodoData
import dev.doggo.shared.contract.SyncCoordinator
import dev.doggo.shared.contract.SyncProgress
import dev.doggo.shared.contract.SyncStatus
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.data.ServerBaseEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * AccountSyncCoordinator manages the sync lifecycle:
 * - Mutex-serialized sync rounds across foreground and background workers.
 * - Generation and account fencing to drop stale asynchronous completions.
 * - Recovers interrupted dispatches (DISPATCHING -> UNCERTAIN) on startup/round start (C4).
 * - Freezes expectedRevision, digest, and epoch at dispatch time (C2, C5).
 * - Handles push cancellation safely using NonCancellable (C4).
 * - Three-stage sync round: Initial scan -> Push eligible outbox -> Final scan.
 * - Conflict resolution workflows: discardConflict, reapplyConflict, copyAsNew.
 */
class AccountSyncCoordinator(
    val store: RoomAccountStore,
    val session: HubAuthSession,
    val syncClient: HubSyncClient = HubSyncClient(session.config, session),
    private val leaveProtocolProvider: (() -> AccountLeaveProtocol)? = null
) : SyncCoordinator {

    val coordinatorMutex = Mutex()
    private val _progress = MutableStateFlow(SyncProgress())
    override val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private var currentGeneration: Long = 0L

    fun setGeneration(generation: Long) {
        currentGeneration = generation
    }

    override suspend fun triggerSync(): Unit = coordinatorMutex.withLock {
        executeSyncRound()
    }

    override suspend fun reconcilePending(): Unit = coordinatorMutex.withLock {
        executeSyncRound()
    }

    private suspend fun executeSyncRound() {
        val capturedGeneration = currentGeneration

        // 0. Frozen check
        if (store.isFrozen) {
            updateProgress(
                status = SyncStatus.IDLE,
                safeError = SafeError.Unauthorized(
                    code = "STORE_FROZEN",
                    message = "Store is frozen for account leave"
                )
            )
            return
        }

        // 1. Session and auth state gate
        if (session.state.value == AuthState.ACCESS_PENDING) {
            updateProgress(
                status = SyncStatus.PAUSED_PROTOCOL,
                safeError = SafeError.AccessPending(
                    code = "ACCESS_PENDING",
                    message = "Doggo app access pending administrative approval"
                )
            )
            return
        }

        if (session.state.value != AuthState.AUTHENTICATED) {
            updateProgress(
                status = SyncStatus.IDLE,
                safeError = SafeError.Unauthorized(
                    code = "UNAUTHENTICATED",
                    message = "Cannot sync while unauthenticated"
                )
            )
            return
        }

        // Recover any interrupted dispatches from prior crashed/cancelled rounds (C4)
        store.recoverInterruptedDispatches()

        updateProgress(status = SyncStatus.SYNCING, safeError = null)

        // 2. Bootstrap & Epoch verification
        var bootstrapResult = syncClient.bootstrap()
        if (bootstrapResult is ApiResult.Failure && bootstrapResult.error is SafeError.Unauthorized && bootstrapResult.error.code == "TOKEN_REFRESHED") {
            // Coordinator controls retry of operation once across token refresh (AUTH_ACCEPT_15)
            bootstrapResult = syncClient.bootstrap()
        }
        if (capturedGeneration != currentGeneration || store.isFrozen) return // Fenced

        val bootstrap = when (bootstrapResult) {
            is ApiResult.Failure -> {
                handleSyncFailure(bootstrapResult.error)
                return
            }
            is ApiResult.Success -> bootstrapResult.value
        }

        val serverEpoch = bootstrap.epoch
        val localEpoch = store.getActiveEpoch()

        if (localEpoch == null) {
            store.setActiveEpoch(serverEpoch)
        } else if (localEpoch != serverEpoch) {
            // Epoch mismatch: Server epoch rotated or restored
            updateProgress(
                status = SyncStatus.RESET_REQUIRED,
                safeError = SafeError.ResetRequired(
                    code = "EPOCH_MISMATCH",
                    message = "Server epoch changed from $localEpoch to $serverEpoch; reset required"
                )
            )
            return
        }

        val activeEpoch = serverEpoch

        // 3. Stage 1: Initial Staged Full Scan
        var initialScan = StagedFullScan(syncClient, store, activeEpoch).executeScan()
        if (initialScan is ApiResult.Failure && initialScan.error is SafeError.Unauthorized && initialScan.error.code == "TOKEN_REFRESHED") {
            initialScan = StagedFullScan(syncClient, store, activeEpoch).executeScan()
        }
        if (capturedGeneration != currentGeneration || store.isFrozen) return

        when (initialScan) {
            is ApiResult.Failure -> {
                handleSyncFailure(initialScan.error)
                return
            }
            is ApiResult.Success -> { /* Completed initial scan */ }
        }

        // 4. Stage 2: Push Eligible Outbox Mutations
        val reconciliation = OutboxReconciliation(store, activeEpoch)
        val eligibleBatch = reconciliation.getEligibleDispatchBatch(maxCount = 100)

        if (eligibleBatch.isNotEmpty()) {
            // Freeze expectedRevision, epoch, and digest at DISPATCH time against current base (C2, C5)
            val frozenBatch = reconciliation.prepareAndFreezeBatch(eligibleBatch)
            val dtos = frozenBatch.map { reconciliation.toDto(it) }

            val pushResult = try {
                syncClient.push(activeEpoch, dtos)
            } catch (e: CancellationException) {
                // Cancellation safety: mark batch UNCERTAIN in NonCancellable block (C4)
                withContext(NonCancellable) {
                    reconciliation.markBatchUncertain(frozenBatch, "Push cancelled: ${e.message}")
                }
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    reconciliation.markBatchUncertain(frozenBatch, "Push exception: ${e.message}")
                }
                ApiResult.Failure(SafeError.Network("PUSH_EXCEPTION", e.message ?: "Push failed"))
            }

            if (capturedGeneration != currentGeneration || store.isFrozen) return

            when (pushResult) {
                is ApiResult.Failure -> {
                    reconciliation.markBatchUncertain(
                        frozenBatch,
                        pushResult.error.message
                    )
                    handleSyncFailure(pushResult.error)
                    return
                }
                is ApiResult.Success -> {
                    reconciliation.reconcilePushResults(frozenBatch, pushResult.value)
                }
            }

            // 5. Stage 3: Final Staged Full Scan after mutations
            val finalScan = StagedFullScan(syncClient, store, activeEpoch).executeScan()
            if (capturedGeneration != currentGeneration || store.isFrozen) return

            if (finalScan is ApiResult.Failure) {
                handleSyncFailure(finalScan.error)
                return
            }
        }

        // 6. Refresh and publish progress state
        refreshProgressState()
    }

    private suspend fun refreshProgressState() {
        val pendingCount = store.dao.countOutboxByState(OutboxEntity.STATE_PENDING) +
                store.dao.countOutboxByState(OutboxEntity.STATE_DISPATCHING)
        val uncertainCount = store.dao.countOutboxByState(OutboxEntity.STATE_UNCERTAIN) +
                store.dao.countOutboxByState(OutboxEntity.STATE_UNKNOWN_SUPERSEDED)
        val conflictCount = store.dao.countOutboxByState(OutboxEntity.STATE_CONFLICT)

        val status = when {
            conflictCount > 0 -> SyncStatus.PAUSED_CONFLICT
            uncertainCount > 0 -> SyncStatus.UNCERTAIN
            else -> SyncStatus.IDLE
        }

        updateProgress(
            pendingCount = pendingCount,
            uncertainCount = uncertainCount,
            conflictCount = conflictCount,
            status = status,
            lastSyncTime = System.currentTimeMillis(),
            safeError = null
        )
    }

    private suspend fun handleSyncFailure(error: SafeError) {
        val status = when (error) {
            is SafeError.ResetRequired -> SyncStatus.RESET_REQUIRED
            is SafeError.ProtocolIncompatible -> SyncStatus.PAUSED_PROTOCOL
            is SafeError.Conflict -> SyncStatus.PAUSED_CONFLICT
            is SafeError.UncertainOutcome -> SyncStatus.UNCERTAIN
            else -> SyncStatus.IDLE
        }
        updateProgress(status = status, safeError = error)
    }

    private fun updateProgress(
        pendingCount: Int = _progress.value.pendingCount,
        uncertainCount: Int = _progress.value.uncertainCount,
        conflictCount: Int = _progress.value.conflictCount,
        status: SyncStatus,
        lastSyncTime: Long? = _progress.value.lastSyncTime,
        safeError: SafeError?
    ) {
        _progress.value = SyncProgress(
            pendingCount = pendingCount,
            uncertainCount = uncertainCount,
            conflictCount = conflictCount,
            status = status,
            lastSyncTime = lastSyncTime,
            safeError = safeError
        )
    }

    /**
     * Discards a conflicted record intent, acknowledging local loss and accepting server base.
     */
    override suspend fun discardConflict(rowId: String): Unit = coordinatorMutex.withLock {
        // Only delete the conflicted head operation, preserving pending successors (W2)
        val ops = store.dao.getOutboxByRowId(rowId)
        val conflictOp = ops.firstOrNull {
            it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED
        }
        if (conflictOp != null) {
            store.dao.discardConflictAtomic(conflictOp.opId, rowId, conflictOp.localRevision)
        } else {
            store.dao.deleteDraft(rowId)
        }
        refreshProgressState()
    }

    /**
     * Authoritatively reapplies a conflicted record by creating a fresh mutation
     * targeting the latest confirmed server revision.
     */
    override suspend fun reapplyConflict(rowId: String): Unit = coordinatorMutex.withLock {
        val activeEpoch = store.getActiveEpoch() ?: return
        val currentBase = store.dao.getServerBase(rowId)
        val draft = store.dao.getDraft(rowId)
        val ops = store.dao.getOutboxByRowId(rowId)
        val conflictOp = ops.firstOrNull {
            it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED
        } ?: ops.lastOrNull()

        val expectedRev = currentBase?.serverRevision ?: 0L
        val kind = if (draft?.isDeleted == true || conflictOp?.kind == StrictOperationKind.DELETE.wireName) {
            StrictOperationKind.DELETE
        } else {
            StrictOperationKind.UPDATE
        }

        val title = draft?.draftTitle ?: conflictOp?.title ?: currentBase?.title ?: ""
        val completed = draft?.draftCompleted ?: conflictOp?.completed ?: currentBase?.completed ?: false
        val createdAt = currentBase?.createdAt ?: conflictOp?.createdAt ?: System.currentTimeMillis()

        val nextRev = store.allocateNextLocalRevision()
        val newOpId = generateUuid()
        val todoData = if (kind == StrictOperationKind.DELETE) null else StrictTodoData(
            id = rowId,
            title = title,
            completed = completed,
            createdAt = createdAt
        )

        val digest = StrictSyncDigest.computeDigest(
            domain = StrictSyncContract.PROTOCOL_VERSION,
            appId = store.appId,
            userId = store.userId,
            epoch = activeEpoch,
            tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
            rowId = rowId,
            opId = newOpId,
            kind = kind,
            expectedRevision = expectedRev,
            data = todoData
        )

        store.dao.replaceOutboxAtomic(
            conflictOp?.opId,
            OutboxEntity(
                opId = newOpId,
                epoch = activeEpoch,
                tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
                rowId = rowId,
                kind = kind.wireName,
                expectedRevision = expectedRev,
                title = if (kind == StrictOperationKind.DELETE) null else title,
                completed = if (kind == StrictOperationKind.DELETE) null else completed,
                createdAt = if (kind == StrictOperationKind.DELETE) null else createdAt,
                digest = digest,
                localRevision = nextRev,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )

        refreshProgressState()
    }

    /**
     * Copies a conflicted item's contents into a new Todo with a fresh UUID,
     * discarding the old conflicted identity.
     */
    override suspend fun copyAsNew(rowId: String): Unit = coordinatorMutex.withLock {
        val currentBase = store.dao.getServerBase(rowId)
        val draft = store.dao.getDraft(rowId)
        val ops = store.dao.getOutboxByRowId(rowId)
        val conflictOp = ops.firstOrNull {
            it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED
        } ?: ops.lastOrNull()

        val title = draft?.draftTitle ?: conflictOp?.title ?: currentBase?.title ?: ""
        val completed = draft?.draftCompleted ?: conflictOp?.completed ?: currentBase?.completed ?: false
        val now = System.currentTimeMillis()

        // Clean up conflicted identity
        if (conflictOp != null) {
            store.dao.deleteOutbox(conflictOp.opId)
        }
        store.dao.deleteDraft(rowId)

        // Create new identity
        val newId = generateUuid()
        store.saveTodoLocally(
            Todo(
                id = newId,
                title = title,
                completed = completed,
                createdAt = now,
                updatedAt = now,
                serverRevision = 0L,
                isPending = true,
                isDeleted = false
            )
        )

        refreshProgressState()
    }

    override suspend fun requestLeave(): Boolean = coordinatorMutex.withLock {
        val protocol = leaveProtocolProvider?.invoke() ?: return false
        return protocol.executeLeave()
    }

    override fun close() {
        store.close()
    }

    fun dismissError() {
        _progress.value = _progress.value.copy(safeError = null)
    }

    private fun generateUuid(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte() // UUID v4
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }
}
