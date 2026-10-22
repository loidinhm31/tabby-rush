package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.data.BINDING_LOCAL_ACCOUNT
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.boundBindingKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

/**
 * Account/generation-fenced ordinary sync coordinator.
 * Delta is explicitly not treated as batch atomic.
 * Coordinates full scan, outbox dispatch, and TaskRef-based conflict resolution.
 */
class AccountSyncCoordinator(
    val store: RoomAccountStore,
    val session: HubAuthSession,
    val syncClient: HubSyncClient = HubSyncClient(session.config, session, expectedAccountKey = store.accountKey),
    private val leaveProtocolProvider: (() -> AccountLeaveProtocol)? = null
) : SyncCoordinator {
    val coordinatorMutex = Mutex()
    private val _progress = MutableStateFlow(SyncProgress())
    override val progress: StateFlow<SyncProgress> = _progress.asStateFlow()
    @kotlin.jvm.Volatile
    private var currentGeneration = 0L
    fun setGeneration(generation: Long) { currentGeneration = generation }
    private fun fenced(generation: Long): Boolean = generation != currentGeneration || store.isFrozen ||
        session.currentAccountKey.value != store.accountKey || session.state.value != AuthState.AUTHENTICATED

    override suspend fun triggerSync(): Unit = coordinatorMutex.withLock { executeSyncRound() }
    override suspend fun reconcilePending(): Unit = coordinatorMutex.withLock { executeSyncRound() }

    private suspend fun executeSyncRound() {
        val generation = currentGeneration
        if (fenced(generation)) {
            fail(SafeError.Unauthorized("ACCOUNT_NOT_READY", "Sync requires the active authenticated account"))
            return
        }
        _progress.value = _progress.value.copy(status = SyncStatus.SYNCING, safeError = null)
        store.recoverInterruptedDispatches()

        // Discard prior staging and scan from null before every round's writes.
        if (!catchUp(generation, full = true)) return
        LegacyStandardIntentMigration(store).translateAfterCompletedScan()
        val reconciliation = OutboxReconciliation(store)
        reconciliation.reconcileUncertain()
        reconcileLateChildren()
        if (fenced(generation)) return

        val pull = pullRequest()
        val batch = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch(), pull)
        if (batch.isNotEmpty()) {
            val request = DeltaRequestDto(PushRequestDto(batch.map(reconciliation::toDto), collaborationVersion = COLLABORATION_VERSION_1), pull)
            if (fenced(generation)) {
                if (!store.isFrozen) reconciliation.markBatchUncertain(batch, "Account changed before dispatch; frozen intent retained")
                return
            }
            val result = try {
                syncClient.delta(request)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { reconciliation.markBatchUncertain(batch, "Delta cancelled; delivery unknown") }
                throw cancelled
            } catch (_: Exception) {
                withContext(NonCancellable) { reconciliation.markBatchUncertain(batch, "Delta failed; delivery unknown") }
                ApiResult.Failure(SafeError.Network("DELTA_FAILED", "Delivery unknown; local work retained"))
            }
            if (fenced(generation)) {
                withContext(NonCancellable) {
                    if (!store.isFrozen) reconciliation.markBatchUncertain(batch, "Account changed during dispatch")
                }
                return
            }
            when (result) {
                is ApiResult.Failure -> {
                    reconciliation.markBatchUncertain(batch, "Response unavailable; pull before another write")
                    fail(result.error)
                    return
                }
                is ApiResult.Success -> {
                    // Commit acknowledgement before touching pull. A malformed pull must not roll back a known ack.
                    val push = result.value.push
                    if (push == null) reconciliation.markBatchUncertain(batch, "Delta omitted push result")
                    else reconciliation.reconcilePushResults(batch, push)
                    val pullResult = result.value.pull
                    if (pullResult != null && !applyIncremental(pullResult, request.pull?.checkpoint, generation)) return
                    if (result.value.pullError != null || result.value.pushError != null) {
                        fail(SafeError.ProtocolIncompatible("INVALID_DELTA_PART", result.value.pullError ?: requireNotNull(result.value.pushError)))
                        return
                    }
                    if ((pullResult == null || pullResult.hasMore) && !catchUp(generation, full = false)) return
                }
            }
            reconciliation.reconcileUncertain()
            reconcileLateChildren()
        }
        refreshProgressState()
    }

    private suspend fun pullRequest(): PullRequestDto {
        val meta = store.dao.getMeta()
        val checkpoint = meta?.checkpointUpdatedAt?.let { SyncCheckpointDto(it, requireNotNull(meta.checkpointId)) }
        return PullRequestDto(
            checkpoint = checkpoint,
            tables = listOf("todos", ASSIGNMENT_TABLE_NAME),
            collaborationVersion = COLLABORATION_VERSION_1
        )
    }

    private suspend fun catchUp(generation: Long, full: Boolean): Boolean {
        if (fenced(generation)) return false
        if (full) {
            when (val result = StagedFullScan(syncClient, store, isFenced = { fenced(generation) }).executeScan()) {
                is ApiResult.Failure -> { fail(result.error); return false }
                is ApiResult.Success -> Unit
            }
        }
        // Drain updates after final checkpoint before reconciling/replay.
        repeat(1000) {
            if (fenced(generation)) return false
            val request = pullRequest()
            when (val result = syncClient.pull(request)) {
                is ApiResult.Failure -> { fail(result.error); return false }
                is ApiResult.Success -> {
                    if (!applyIncremental(result.value, request.checkpoint, generation)) return false
                    if (!result.value.hasMore) return true
                }
            }
        }
        fail(SafeError.Network("PULL_PAGE_LIMIT", "Catch-up paused; checkpoint retained"))
        return false
    }

    private suspend fun applyIncremental(response: PullResponseDto, previous: SyncCheckpointDto?, generation: Long): Boolean {
        if (fenced(generation)) return false
        return try {
            validatePage(response, previous)
            val projected = response.records.map(TaskJsonMerge::project)
            store.withMutationLock {
                check(!fenced(generation))
                store.dao.commitPageAtomic(
                    projected,
                    response.checkpoint.updatedAt,
                    response.checkpoint.id,
                    response.schemaVersion,
                    null,
                    response.hasMore,
                    System.currentTimeMillis()
                )
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fail(SafeError.ProtocolIncompatible("INVALID_PULL_PAGE", "Pull rejected; draft and previous checkpoint retained"))
            false
        }
    }

    private suspend fun reconcileLateChildren() {
        val bases = store.dao.getAllServerBase().filter { it.tableName == TaskDataContract.DEFAULT_TABLE_NAME }
        val deletedParentRefs = bases
            .filter { it.deleted && it.parentTaskId == null }
            .map { TaskRef(ownerUserId = it.ownerUserId ?: store.userId, rowId = it.rowId) }
            .toSet()
        for (child in store.loadAllTodos(includeDeleted = false).filter { it.parentTaskRef in deletedParentRefs }) {
            val boundKey = boundBindingKey(child.ownerUserId)
            val ops = store.dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, child.id)
            if (ops.any { it.kind == "delete" || it.deliveryState != OutboxEntity.STATE_PENDING }) continue
            store.deleteTodoLocally(child.taskRef)
        }
    }

    private suspend fun refreshProgressState() {
        val pending = store.dao.countOutboxByState(OutboxEntity.STATE_PENDING) + store.dao.countOutboxByState(OutboxEntity.STATE_DISPATCHING)
        val uncertain = store.dao.countOutboxByState(OutboxEntity.STATE_UNCERTAIN)
        val conflicts = store.dao.countOutboxByState(OutboxEntity.STATE_CONFLICT) + store.dao.countOutboxByState(OutboxEntity.STATE_REVIEW)
        val incompatible = store.dao.getAllServerBase().any { !it.deleted && it.projectionError != null }
        val meta = store.dao.getMeta()
        _progress.value = SyncProgress(
            pendingCount = pending,
            uncertainCount = uncertain,
            conflictCount = conflicts,
            status = when {
                conflicts > 0 || incompatible -> SyncStatus.PAUSED_CONFLICT
                uncertain > 0 -> SyncStatus.UNCERTAIN
                else -> SyncStatus.IDLE
            },
            lastSyncTime = meta?.lastCompletedCatchUpEpochMs,
            safeError = if (incompatible) SafeError.Validation("REMOTE_PROJECTION", "Remote task data needs a compatible client; raw records retained") else null
        )
    }

    private fun fail(error: SafeError) {
        _progress.value = _progress.value.copy(
            status = when (error) {
                is SafeError.ProtocolIncompatible, is SafeError.CollaborationRequired -> SyncStatus.PAUSED_PROTOCOL
                is SafeError.Conflict -> SyncStatus.PAUSED_CONFLICT
                is SafeError.UncertainOutcome -> SyncStatus.UNCERTAIN
                else -> SyncStatus.IDLE
            },
            safeError = error
        )
    }

    override suspend fun discardConflict(taskRef: TaskRef): Unit = coordinatorMutex.withLock {
        store.withMutationLock {
            val boundKey = boundBindingKey(taskRef.ownerUserId)
            val conflictOps = (store.dao.getOutboxByRow(boundKey, "todos", taskRef.rowId) +
                    store.dao.getOutboxByRow(boundKey, ASSIGNMENT_TABLE_NAME, taskRef.rowId) +
                    store.dao.getOutboxByRowId(taskRef.rowId).filter { it.ownerUserId == taskRef.ownerUserId &&
                        (it.tableName == "todos" || it.tableName == ASSIGNMENT_TABLE_NAME) })
                .distinctBy { it.opId }
                .filter { it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW }
            if (conflictOps.isEmpty()) {
                val head = reviewHead(taskRef)
                if (head != null) {
                    store.dao.discardConflictAtomic(head.opId, taskRef.rowId, head.localRevision)
                }
            } else {
                for (op in conflictOps) {
                    store.dao.discardConflictAtomic(op.opId, taskRef.rowId, op.localRevision)
                }
            }
        }
        refreshProgressState()
    }

    override suspend fun reapplyConflict(taskRef: TaskRef): Unit = coordinatorMutex.withLock {
        val generation = currentGeneration
        if (!catchUp(generation, full = true)) return@withLock
        var rejection: SafeError? = null
        store.withMutationLock {
            val op = reviewHead(taskRef) ?: return@withMutationLock
            if (op.tableName == ASSIGNMENT_TABLE_NAME) {
                val taskBase = store.dao.getServerBase(op.bindingKey, "todos", taskRef.rowId)
                    ?: store.dao.getServerBase(dev.tabbyrush.shared.data.BINDING_LOCAL_ACCOUNT, "todos", taskRef.rowId)
                if (taskBase == null || taskBase.deleted) {
                    fail(SafeError.Validation("REMOTE_MISSING", "Cannot assign missing/invalid task; discard or copy as a new root"))
                    return@withMutationLock
                }
                val access = store.accessForLocked(taskRef)
                val canManage = taskRef.ownerUserId == store.userId || access?.capabilities?.manageAssignment == true
                if (!canManage) {
                    fail(SafeError.OperationForbidden(message = "Cannot manage assignment for this task", taskRef = taskRef))
                    return@withMutationLock
                }
                val currentSlot = store.dao.getServerBase(op.bindingKey, ASSIGNMENT_TABLE_NAME, taskRef.rowId)
                val currentSlotVersion = currentSlot?.serverRevision ?: 0L
                store.dao.replaceOutboxAtomic(
                    op.opId,
                    op.copy(
                        opId = newId(),
                        deliveryState = OutboxEntity.STATE_PENDING,
                        envelopeJson = null,
                        version = currentSlotVersion + 1L,
                        assumedServerVersion = currentSlotVersion,
                        legacyEvidenceId = null,
                        dispatchHistory = "NEVER_DISPATCHED",
                        errorMessage = null,
                        resolution = "EXPLICIT_REAPPLY"
                    )
                )
                return@withMutationLock
            }
            val base = store.dao.getServerBase(op.bindingKey, op.tableName, taskRef.rowId)
            if (base == null || base.deleted) {
                fail(SafeError.Validation("REMOTE_MISSING", "Cannot recreate missing/invalid task; discard or copy as a new root"))
                return@withMutationLock
            }
            if (op.parentTaskId != null) {
                val parentBase = store.dao.getServerBase(op.bindingKey, op.tableName, op.parentTaskId)
                if (parentBase == null || parentBase.deleted) {
                    fail(SafeError.Validation("PARENT_MISSING", "Parent unavailable; copy as a new root or discard"))
                    return@withMutationLock
                }
            }
            val mask = op.intentMask?.takeIf { parts -> parts.split('|').all { it in TaskJsonMerge.masks } }
            if (mask == null) {
                rejection = SafeError.Validation(
                    "UNKNOWN_LOCAL_INTENT",
                    "Changed fields cannot be established for this retained edit; discard it or copy the local version as a new root"
                )
                return@withMutationLock
            }
            val payload = TaskJsonMerge.merge(TaskJsonMerge.json.parseToJsonElement(base.rawJson ?: "{}").jsonObject, op.intentJson(), mask)
            if (op.kind != "delete") TaskJsonMerge.decode(taskRef.rowId, payload)
            store.dao.replaceOutboxAtomic(
                op.opId,
                op.copy(
                    opId = newId(),
                    deliveryState = OutboxEntity.STATE_PENDING,
                    payloadJson = op.intentJson().toString(),
                    intentMask = mask,
                    envelopeJson = null,
                    version = null,
                    assumedServerVersion = null,
                    legacyEvidenceId = null,
                    dispatchHistory = "NEVER_DISPATCHED",
                    errorMessage = null,
                    resolution = "EXPLICIT_REAPPLY",
                    kind = if (op.kind == "delete") "delete" else "update"
                )
            )
        }
        refreshProgressState()
        rejection?.let(::fail)
    }

    override suspend fun copyAsNew(taskRef: TaskRef): Unit = coordinatorMutex.withLock {
        store.copyLatestConflictAsNew(taskRef)
        refreshProgressState()
    }

    private suspend fun reviewHead(taskRef: TaskRef): OutboxEntity? {
        val boundKey = boundBindingKey(taskRef.ownerUserId)
        val todoOps = store.dao.getOutboxByRow(boundKey, "todos", taskRef.rowId)
        val assignOps = store.dao.getOutboxByRow(boundKey, ASSIGNMENT_TABLE_NAME, taskRef.rowId)
        val ops = if (todoOps.any { it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW }) {
            todoOps
        } else if (assignOps.any { it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW }) {
            assignOps
        } else if (todoOps.isNotEmpty()) {
            todoOps
        } else {
            assignOps
        }
        val activeOps = if (ops.isEmpty()) {
            store.dao.getOutboxByRowId(taskRef.rowId).filter { it.ownerUserId == taskRef.ownerUserId }
        } else {
            ops
        }
        return activeOps.firstOrNull()?.takeIf {
            it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW
        }
    }

    override suspend fun requestLeave(): Boolean = coordinatorMutex.withLock { leaveProtocolProvider?.invoke()?.executeLeave() ?: false }
    override fun close() { store.close() }
    fun dismissError() { _progress.value = _progress.value.copy(safeError = null) }

    private fun newId(): String {
        val bytes = Random.nextBytes(16)
        bytes[6] = ((bytes[6].toInt() and 15) or 64).toByte()
        bytes[8] = ((bytes[8].toInt() and 63) or 128).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
