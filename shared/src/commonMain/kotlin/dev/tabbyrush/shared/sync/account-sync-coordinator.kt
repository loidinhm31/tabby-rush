package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.StrictOperationKind
import dev.tabbyrush.shared.contract.StrictSyncContract
import dev.tabbyrush.shared.contract.StrictSyncDigest
import dev.tabbyrush.shared.contract.StrictTodoData
import dev.tabbyrush.shared.contract.StrictSyncV2Contract
import dev.tabbyrush.shared.contract.StrictSyncV2Digest
import dev.tabbyrush.shared.contract.StrictV2TodoData
import dev.tabbyrush.shared.contract.StrictSyncV3Contract
import dev.tabbyrush.shared.contract.StrictSyncV3Digest
import dev.tabbyrush.shared.contract.StrictV3TodoData
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.SyncCoordinator
import dev.tabbyrush.shared.contract.SyncProgress
import dev.tabbyrush.shared.contract.SyncStatus
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.ServerBaseEntity
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
                    message = "TabbyRush app access pending administrative approval"
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
        val isServerV3 = bootstrap.protocol == StrictSyncV3Contract.PROTOCOL_VERSION
        val isServerV2 = bootstrap.protocol == StrictSyncV2Contract.PROTOCOL_VERSION
        val transitionCoordinator = ContractTransitionCoordinator(store, syncClient)
        val durablePhase = transitionCoordinator.getDurablePhase()
        if (durablePhase == TransitionPhase.READY_V3 && !isServerV3) {
            handleSyncFailure(
                SafeError.ProtocolIncompatible(
                    code = "SERVER_DOWNGRADE_FORBIDDEN",
                    message = "Account is upgraded to strict-v3; server is on ${bootstrap.protocol}"
                )
            )
            return
        }
        if (durablePhase == TransitionPhase.READY_V2 && bootstrap.protocol == StrictSyncContract.PROTOCOL_VERSION) {
            handleSyncFailure(
                SafeError.ProtocolIncompatible(
                    code = "SERVER_DOWNGRADE_FORBIDDEN",
                    message = "Account is upgraded to strict-v2; server is on strict-v1"
                )
            )
            return
        }

        if (isServerV3) {
            val durablePhase = transitionCoordinator.getDurablePhase()
            if (durablePhase != TransitionPhase.READY_V3) {
                val transitionResult = transitionCoordinator.executeTransition(
                    activeEpoch = activeEpoch,
                    targetProtocol = StrictSyncV3Contract.PROTOCOL_VERSION,
                    isFenced = { capturedGeneration != currentGeneration || store.isFrozen }
                )
                if (transitionResult is ApiResult.Failure) {
                    handleSyncFailure(transitionResult.error)
                    return
                }
            }
        } else if (isServerV2) {
            val hasV3Outbox = store.dao.countOutboxByVersion(OutboxEntity.CONTRACT_VERSION_V3) > 0
            if (hasV3Outbox) {
                handleSyncFailure(
                    SafeError.ProtocolIncompatible(
                        code = "SERVER_V2_INCOMPATIBLE",
                        message = "Server is on strict-v2; cannot sync pending strict-v3 operations until server upgrades"
                    )
                )
                return
            }
            val durablePhase = transitionCoordinator.getDurablePhase()
            if (durablePhase != TransitionPhase.READY_V2 && durablePhase != TransitionPhase.READY_V3) {
                val transitionResult = transitionCoordinator.executeTransition(
                    activeEpoch = activeEpoch,
                    targetProtocol = StrictSyncV2Contract.PROTOCOL_VERSION,
                    isFenced = { capturedGeneration != currentGeneration || store.isFrozen }
                )
                if (transitionResult is ApiResult.Failure) {
                    handleSyncFailure(transitionResult.error)
                    return
                }
            }
        } else {
            val hasAdvancedOutbox = store.dao.countOutboxByVersion(OutboxEntity.CONTRACT_VERSION_V2) > 0 ||
                    store.dao.countOutboxByVersion(OutboxEntity.CONTRACT_VERSION_V3) > 0
            if (hasAdvancedOutbox) {
                handleSyncFailure(
                    SafeError.ProtocolIncompatible(
                        code = "SERVER_V1_INCOMPATIBLE",
                        message = "Server is on strict-v1; cannot sync pending strict-v2/strict-v3 operations until server upgrades"
                    )
                )
                return
            }
        }

        val syncProtocol = when {
            isServerV3 -> StrictSyncV3Contract.PROTOCOL_VERSION
            isServerV2 -> StrictSyncV2Contract.PROTOCOL_VERSION
            else -> StrictSyncContract.PROTOCOL_VERSION
        }
        // 3. Stage 1: Initial Staged Full Scan
        var initialScan = StagedFullScan(
            syncClient = syncClient,
            store = store,
            activeEpoch = activeEpoch,
            protocol = syncProtocol,
            isFenced = { capturedGeneration != currentGeneration || store.isFrozen }
        ).executeScan()
        if (initialScan is ApiResult.Failure && initialScan.error is SafeError.Unauthorized && initialScan.error.code == "TOKEN_REFRESHED") {
            initialScan = StagedFullScan(
                syncClient = syncClient,
                store = store,
                activeEpoch = activeEpoch,
                protocol = syncProtocol,
                isFenced = { capturedGeneration != currentGeneration || store.isFrozen }
            ).executeScan()
        }
        if (capturedGeneration != currentGeneration || store.isFrozen) return

        when (initialScan) {
            is ApiResult.Failure -> {
                handleSyncFailure(initialScan.error)
                return
            }
            is ApiResult.Success -> { /* Completed initial scan */ }
        }

        // 3.5. Late-child sync cleanup: reconcile surviving children whose parent has confirmed tombstone
        reconcileLateChildren(activeEpoch)

        // 4. Stage 2: Push Eligible Outbox Mutations
        val reconciliation = OutboxReconciliation(store, activeEpoch)
        val eligibleBatch = reconciliation.getEligibleDispatchBatch(maxCount = 100)

        if (eligibleBatch.isNotEmpty()) {
            // Freeze expectedRevision, epoch, and digest at DISPATCH time against current base (C2, C5)
            val frozenBatch = reconciliation.prepareAndFreezeBatch(eligibleBatch)

            val pushResult = try {
                if (isServerV3) {
                    val dtos = frozenBatch.map { reconciliation.toV3Dto(it) }
                    syncClient.pushV3(activeEpoch, dtos)
                } else if (isServerV2) {
                    val dtos = frozenBatch.map { reconciliation.toV2Dto(it) }
                    syncClient.pushV2(activeEpoch, dtos)
                } else {
                    val dtos = frozenBatch.map { reconciliation.toDto(it) }
                    syncClient.push(activeEpoch, dtos)
                }
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
            val finalScan = StagedFullScan(
                syncClient = syncClient,
                store = store,
                activeEpoch = activeEpoch,
                protocol = syncProtocol,
                isFenced = { capturedGeneration != currentGeneration || store.isFrozen }
            ).executeScan()
            if (capturedGeneration != currentGeneration || store.isFrozen) return

            if (finalScan is ApiResult.Failure) {
                handleSyncFailure(finalScan.error)
                return
            }
            // Reconcile any late children discovered after final scan
            reconcileLateChildren(activeEpoch)
        }

        // 6. Refresh and publish progress state
        refreshProgressState()
    }
    private suspend fun reconcileLateChildren(activeEpoch: String) {
        val allBases = store.dao.getAllServerBase()
        val tombstoneRootIds = allBases
            .filter { it.parentTaskId == null && it.deleted && it.epoch == activeEpoch }
            .map { it.rowId }
            .toSet()

        if (tombstoneRootIds.isEmpty()) return

        // 1. Surviving children confirmed in server_base whose parent has a confirmed tombstone
        val survivingLateChildren = allBases.filter {
            it.parentTaskId != null && !it.deleted && it.epoch == activeEpoch && tombstoneRootIds.contains(it.parentTaskId)
        }
        for (childBase in survivingLateChildren) {
            val childOps = store.dao.getOutboxByRowId(childBase.rowId)
            val hasUncertainOrInFlight = childOps.any {
                it.deliveryState == OutboxEntity.STATE_UNCERTAIN || it.deliveryState == OutboxEntity.STATE_DISPATCHING
            }
            if (hasUncertainOrInFlight) continue // Resolve receipts first

            val hasConflict = childOps.any {
                it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED
            }
            if (hasConflict) continue // Leave for explicit user review

            val hasPendingDelete = childOps.any {
                it.deliveryState == OutboxEntity.STATE_PENDING && it.kind == StrictOperationKind.DELETE.wireName
            }
            if (hasPendingDelete) continue

            val draft = store.dao.getDraft(childBase.rowId)
            if (draft != null && draft.isDeleted) continue

            // Enqueue clean per-row DELETE for this late child
            store.deleteTodoLocally(childBase.rowId)
        }

        // 2. Offline drafts created under a parent that has since been tombstoned on the server
        val allDrafts = store.dao.getAllDrafts()
        for (draft in allDrafts) {
            val parentId = draft.parentTaskId ?: continue
            if (!tombstoneRootIds.contains(parentId)) continue
            store.withMutationLock {
                val base = store.dao.getServerBase(draft.rowId)
                if (base == null) {
                    val ops = store.dao.getOutboxByRowId(draft.rowId)
                    val isUnsentCreate = ops.isNotEmpty() && ops.all {
                        it.deliveryState == OutboxEntity.STATE_PENDING &&
                                it.transitionCheckpoint == null &&
                                it.kind == StrictOperationKind.CREATE.wireName
                    }
                    if (isUnsentCreate) {
                        store.dao.cancelUnsentCreateAtomic(draft.rowId)
                    }
                }
            }
        }
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
        store.withMutationLock {
            if (conflictOp != null) {
                store.dao.discardConflictAtomic(conflictOp.opId, rowId, conflictOp.localRevision)
            } else {
                store.dao.deleteDraft(rowId)
            }
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

        val deadlineDate = draft?.deadlineDate ?: conflictOp?.deadlineDate ?: currentBase?.deadlineDate
        val deadlineTime = draft?.deadlineTime ?: conflictOp?.deadlineTime ?: currentBase?.deadlineTime
        val deadlineZone = draft?.deadlineZone ?: conflictOp?.deadlineZone ?: currentBase?.deadlineZone
        val deadlineOffsetSeconds = draft?.deadlineOffsetSeconds ?: conflictOp?.deadlineOffsetSeconds ?: currentBase?.deadlineOffsetSeconds
        val reminderAnchorTime = draft?.reminderAnchorTime ?: conflictOp?.reminderAnchorTime ?: currentBase?.reminderAnchorTime
        val deadlineAnchorEpochMs = draft?.deadlineAnchorEpochMs ?: conflictOp?.deadlineAnchorEpochMs ?: currentBase?.deadlineAnchorEpochMs
        val deadlineOverdueEpochMs = draft?.deadlineOverdueEpochMs ?: conflictOp?.deadlineOverdueEpochMs ?: currentBase?.deadlineOverdueEpochMs
        val reminderEnabled = draft?.reminderEnabled ?: conflictOp?.reminderEnabled ?: currentBase?.reminderEnabled ?: false
        val reminderLeadMinutes = draft?.reminderLeadMinutes ?: conflictOp?.reminderLeadMinutes ?: currentBase?.reminderLeadMinutes ?: 0L
        val reminderIntervalMinutes = draft?.reminderIntervalMinutes ?: conflictOp?.reminderIntervalMinutes ?: currentBase?.reminderIntervalMinutes
        val reminderCount = draft?.reminderCount ?: conflictOp?.reminderCount ?: currentBase?.reminderCount ?: 1L
        val scheduleToken = draft?.scheduleToken ?: conflictOp?.scheduleToken ?: currentBase?.scheduleToken

        val hasDeadline = deadlineDate != null
        val parentTaskId = draft?.parentTaskId ?: conflictOp?.parentTaskId ?: currentBase?.parentTaskId
        if (parentTaskId != null && kind != StrictOperationKind.DELETE) {
            val parentBase = store.dao.getServerBase(parentTaskId)
            if (parentBase == null || parentBase.deleted) {
                updateProgress(
                    status = SyncStatus.IDLE,
                    safeError = SafeError.Validation(
                        code = "PARENT_DELETED",
                        field = "parentTaskId",
                        message = "Cannot reapply subtask edits because parent task is deleted on server"
                    )
                )
                return
            }
        }
        val isReadyV3 = store.isAccountV3()
        val isReadyV2 = store.isAccountV2()
        val contractVersion = when {
            isReadyV3 || conflictOp?.contractVersion == OutboxEntity.CONTRACT_VERSION_V3 || parentTaskId != null -> OutboxEntity.CONTRACT_VERSION_V3
            isReadyV2 || conflictOp?.contractVersion == OutboxEntity.CONTRACT_VERSION_V2 || hasDeadline -> OutboxEntity.CONTRACT_VERSION_V2
            else -> OutboxEntity.CONTRACT_VERSION_V1
        }
        val nextRev = store.allocateNextLocalRevision()
        val newOpId = generateUuid()

        val digest = when (contractVersion) {
            OutboxEntity.CONTRACT_VERSION_V3 -> {
                val v3Data = if (kind == StrictOperationKind.DELETE) null else StrictV3TodoData(
                    id = rowId,
                    title = title,
                    completed = completed,
                    createdAt = createdAt,
                    deadlineDate = deadlineDate,
                    deadlineTime = deadlineTime,
                    deadlineZone = deadlineZone,
                    deadlineOffsetSeconds = deadlineOffsetSeconds,
                    reminderAnchorTime = reminderAnchorTime,
                    deadlineAnchorEpochMs = deadlineAnchorEpochMs,
                    deadlineOverdueEpochMs = deadlineOverdueEpochMs,
                    reminderEnabled = reminderEnabled,
                    reminderLeadMinutes = reminderLeadMinutes,
                    reminderIntervalMinutes = reminderIntervalMinutes,
                    reminderCount = reminderCount,
                    scheduleToken = scheduleToken,
                    parentTaskId = parentTaskId
                )
                StrictSyncV3Digest.computeDigest(
                    domain = StrictSyncV3Contract.PROTOCOL_VERSION,
                    appId = store.appId,
                    userId = store.userId,
                    epoch = activeEpoch,
                    tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
                    rowId = rowId,
                    opId = newOpId,
                    kind = kind,
                    expectedRevision = expectedRev,
                    data = v3Data
                )
            }
            OutboxEntity.CONTRACT_VERSION_V2 -> {
                val v2Data = if (kind == StrictOperationKind.DELETE) null else StrictV2TodoData(
                    id = rowId,
                    title = title,
                    completed = completed,
                    createdAt = createdAt,
                    deadlineDate = deadlineDate,
                    deadlineTime = deadlineTime,
                    deadlineZone = deadlineZone,
                    deadlineOffsetSeconds = deadlineOffsetSeconds,
                    reminderAnchorTime = reminderAnchorTime,
                    deadlineAnchorEpochMs = deadlineAnchorEpochMs,
                    deadlineOverdueEpochMs = deadlineOverdueEpochMs,
                    reminderEnabled = reminderEnabled,
                    reminderLeadMinutes = reminderLeadMinutes,
                    reminderIntervalMinutes = reminderIntervalMinutes,
                    reminderCount = reminderCount,
                    scheduleToken = scheduleToken
                )
                StrictSyncV2Digest.computeDigest(
                    domain = StrictSyncV2Contract.PROTOCOL_VERSION,
                    appId = store.appId,
                    userId = store.userId,
                    epoch = activeEpoch,
                    tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
                    rowId = rowId,
                    opId = newOpId,
                    kind = kind,
                    expectedRevision = expectedRev,
                    data = v2Data
                )
            }
            else -> {
                val todoData = if (kind == StrictOperationKind.DELETE) null else StrictTodoData(
                    id = rowId,
                    title = title,
                    completed = completed,
                    createdAt = createdAt
                )
                StrictSyncDigest.computeDigest(
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
            }
        }

        store.withMutationLock {
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
                createdAtEpochMs = System.currentTimeMillis(),
                deadlineDate = if (kind == StrictOperationKind.DELETE) null else deadlineDate,
                deadlineTime = if (kind == StrictOperationKind.DELETE) null else deadlineTime,
                deadlineZone = if (kind == StrictOperationKind.DELETE) null else deadlineZone,
                deadlineOffsetSeconds = if (kind == StrictOperationKind.DELETE) null else deadlineOffsetSeconds,
                reminderAnchorTime = if (kind == StrictOperationKind.DELETE) null else reminderAnchorTime,
                deadlineAnchorEpochMs = if (kind == StrictOperationKind.DELETE) null else deadlineAnchorEpochMs,
                deadlineOverdueEpochMs = if (kind == StrictOperationKind.DELETE) null else deadlineOverdueEpochMs,
                reminderEnabled = if (kind == StrictOperationKind.DELETE) false else reminderEnabled,
                reminderLeadMinutes = if (kind == StrictOperationKind.DELETE) 0L else reminderLeadMinutes,
                reminderIntervalMinutes = if (kind == StrictOperationKind.DELETE) null else reminderIntervalMinutes,
                reminderCount = if (kind == StrictOperationKind.DELETE) 1L else reminderCount,
                scheduleToken = if (kind == StrictOperationKind.DELETE) null else scheduleToken,
                contractVersion = contractVersion,
                intentMask = if (kind == StrictOperationKind.DELETE) OutboxEntity.INTENT_DELETE else conflictOp?.intentMask,
                parentTaskId = parentTaskId
            )
        )
        }
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

        val deadlineDate = draft?.deadlineDate ?: conflictOp?.deadlineDate ?: currentBase?.deadlineDate
        val deadlineTime = draft?.deadlineTime ?: conflictOp?.deadlineTime ?: currentBase?.deadlineTime
        val deadlineZone = draft?.deadlineZone ?: conflictOp?.deadlineZone ?: currentBase?.deadlineZone
        val deadlineOffsetSeconds = draft?.deadlineOffsetSeconds ?: conflictOp?.deadlineOffsetSeconds ?: currentBase?.deadlineOffsetSeconds
        val reminderAnchorTime = draft?.reminderAnchorTime ?: conflictOp?.reminderAnchorTime ?: currentBase?.reminderAnchorTime
        val deadlineAnchorEpochMs = draft?.deadlineAnchorEpochMs ?: conflictOp?.deadlineAnchorEpochMs ?: currentBase?.deadlineAnchorEpochMs
        val deadlineOverdueEpochMs = draft?.deadlineOverdueEpochMs ?: conflictOp?.deadlineOverdueEpochMs ?: currentBase?.deadlineOverdueEpochMs
        val reminderEnabled = draft?.reminderEnabled ?: conflictOp?.reminderEnabled ?: currentBase?.reminderEnabled ?: false
        val reminderLeadMinutes = draft?.reminderLeadMinutes ?: conflictOp?.reminderLeadMinutes ?: currentBase?.reminderLeadMinutes ?: 0L
        val reminderIntervalMinutes = draft?.reminderIntervalMinutes ?: conflictOp?.reminderIntervalMinutes ?: currentBase?.reminderIntervalMinutes
        val reminderCount = draft?.reminderCount ?: conflictOp?.reminderCount ?: currentBase?.reminderCount ?: 1L

        val deadline = if (deadlineDate != null && deadlineZone != null && deadlineOffsetSeconds != null && deadlineAnchorEpochMs != null && deadlineOverdueEpochMs != null) {
            TaskDeadline(
                date = deadlineDate,
                time = deadlineTime,
                zone = deadlineZone,
                offsetSeconds = deadlineOffsetSeconds,
                reminderAnchorTime = reminderAnchorTime,
                anchorEpochMs = deadlineAnchorEpochMs,
                overdueEpochMs = deadlineOverdueEpochMs
            )
        } else null

        val reminderRule = if (deadline != null) {
            ReminderRule(
                enabled = reminderEnabled,
                leadMinutes = reminderLeadMinutes,
                intervalMinutes = reminderIntervalMinutes,
                count = reminderCount
            )
        } else ReminderRule.DISABLED

        // Clean up conflicted identity
        store.withMutationLock {
            if (conflictOp != null) {
                store.dao.deleteOutbox(conflictOp.opId)
            }
            store.dao.deleteDraft(rowId)
        }

        // Create new identity with fresh schedule token if deadline present
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
                isDeleted = false,
                deadline = deadline,
                reminderRule = reminderRule,
                scheduleToken = if (deadline != null) generateUuid() else null
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
