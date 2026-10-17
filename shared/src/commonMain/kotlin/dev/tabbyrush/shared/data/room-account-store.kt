package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AccountStore
import dev.tabbyrush.shared.contract.StrictOperationKind
import dev.tabbyrush.shared.contract.StrictSyncContract
import dev.tabbyrush.shared.contract.StrictSyncDigest
import dev.tabbyrush.shared.contract.StrictTodoData
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.toStrictV2TodoData
import dev.tabbyrush.shared.contract.PersistedScheduleProjector
import dev.tabbyrush.shared.contract.PersistedTaskSchedule
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import dev.tabbyrush.shared.contract.CompletionChange
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

private val PENDING_OUTBOX_STATES = setOf(
    OutboxEntity.STATE_PENDING,
    OutboxEntity.STATE_DISPATCHING,
    OutboxEntity.STATE_UNCERTAIN
)
/**
 * RoomAccountStore implements the AccountStore interface using TabbyRushDatabase.
 * Coordinates visible todo projections from server_base, editor_drafts, and outbox,
 * ensuring frozen FIFO mutation requests, localRevision tracking, and transactional updates.
 */
class RoomAccountStore(
    override val accountKey: AccountKey,
    val userId: String,
    val database: TabbyRushDatabase? = null,
    val appId: String = "tabby-rush",
    daoOverride: TabbyRushDao? = null,
    val defaultContractVersion: String = OutboxEntity.CONTRACT_VERSION_V1
) : AccountStore {

    val dao: TabbyRushDao = daoOverride ?: requireNotNull(database) {
        "Either database or daoOverride must be provided"
    }.tabbyRushDao()

    val mutex = Mutex()
    @kotlin.jvm.Volatile
    var isFrozen: Boolean = false

    @kotlin.jvm.Volatile
    var deliveryGate: ReminderDeliveryGate? = null

    private val taskInvalidationFlow = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply {
        tryEmit(Unit)
    }

    private fun notifyInvalidated() {
        taskInvalidationFlow.tryEmit(Unit)
    }

    suspend fun <T> withMutationLock(block: suspend () -> T): T {
        check(!isFrozen) { "Cannot mutate store while account leave is in progress" }
        val gate = deliveryGate
        return if (gate != null) {
            gate.withMutationAdmission {
                mutex.withLock {
                    check(!isFrozen) { "Cannot mutate store while account leave is in progress" }
                    val result = block()
                    if (result != CompletionChange.Unchanged && result != CompletionChange.Missing) {
                        notifyInvalidated()
                    }
                    result
                }
            }
        } else {
            mutex.withLock {
                check(!isFrozen) { "Cannot mutate store while account leave is in progress" }
                val result = block()
                if (result != CompletionChange.Unchanged && result != CompletionChange.Missing) {
                    notifyInvalidated()
                }
                result
            }
        }
    }

    override suspend fun getPersistedScheduleProjection(): List<PersistedTaskSchedule> = mutex.withLock {
        val snapshot = dao.getScheduleSnapshotAtomic()
        PersistedScheduleProjector.computeProjection(snapshot.serverBases, snapshot.outboxEntries)
    }

    override fun observePersistedSchedule(): Flow<List<PersistedTaskSchedule>> {
        return taskInvalidationFlow.map {
            getPersistedScheduleProjection()
        }.distinctUntilChanged()
    }

    override fun observeTodos(): Flow<List<Todo>> {
        return taskInvalidationFlow.map {
            loadTodos()
        }.distinctUntilChanged()
    }
    /**
     * Loads the current visible list of todos, projecting server_base overlaid
     * with uncommitted editor_drafts and outbox intents.
     * Orders records by createdAt DESC, then id DESC.
     */
    private fun projectSingleTodo(
        rowId: String,
        base: ServerBaseEntity?,
        draft: EditorDraftEntity?,
        rowOps: List<OutboxEntity>,
        latestOp: OutboxEntity?
    ): Todo? {
        val isDeletedLocally = when {
            draft?.isDeleted == true -> true
            latestOp != null && latestOp.kind == StrictOperationKind.DELETE.wireName -> true
            draft == null && latestOp == null && base?.deleted == true -> true
            else -> false
        }

        if (isDeletedLocally) {
            return null
        }

        val title = draft?.draftTitle
            ?: latestOp?.title
            ?: base?.title
            ?: return null

        val completed = draft?.draftCompleted
            ?: latestOp?.completed
            ?: base?.completed
            ?: false

        val createdAt = base?.createdAt
            ?: latestOp?.createdAt
            ?: draft?.updatedAt
            ?: 0L

        val updatedAt = draft?.updatedAt
            ?: base?.updatedAt
            ?: createdAt

        val serverRevision = base?.serverRevision ?: 0L

        val hasPendingOp = rowOps.any {
            it.deliveryState in PENDING_OUTBOX_STATES
        }
        val isPending = hasPendingOp || (draft != null && draft.localRevision > 0L)

        val temporalProj = resolveTemporalProjection(draft, latestOp, base)
        val deadline = toTaskDeadline(temporalProj)
        val reminderRule = toReminderRule(temporalProj, deadline != null)
        val scheduleToken = if (deadline != null) temporalProj.scheduleToken else null

        return Todo(
            id = rowId,
            title = title,
            completed = completed,
            createdAt = createdAt,
            updatedAt = if (updatedAt >= createdAt) updatedAt else createdAt,
            serverRevision = serverRevision,
            isPending = isPending,
            isDeleted = false,
            deadline = deadline,
            reminderRule = reminderRule,
            scheduleToken = scheduleToken
        )
    }

    private suspend fun loadTodoLocked(id: String): Todo? {
        val base = dao.getServerBase(id)
        val draft = dao.getDraft(id)
        val rowOps = dao.getOutboxByRowId(id)
        val latestOp = rowOps.lastOrNull()
        return projectSingleTodo(id, base, draft, rowOps, latestOp)
    }

    private suspend fun loadTodosLocked(): List<Todo> {
        val serverBaseMap = dao.getAllServerBase().associateBy { it.rowId }
        val draftsMap = dao.getAllDrafts().associateBy { it.rowId }
        val allOutbox = dao.getAllOutbox()
        val outboxByRow = allOutbox.groupBy { it.rowId }

        val allRowIds = (serverBaseMap.keys + draftsMap.keys + outboxByRow.keys).toSet()
        val result = mutableListOf<Todo>()

        for (rowId in allRowIds) {
            val base = serverBaseMap[rowId]
            val draft = draftsMap[rowId]
            val rowOps = outboxByRow[rowId] ?: emptyList()
            val latestOp = rowOps.lastOrNull()
            val todo = projectSingleTodo(rowId, base, draft, rowOps, latestOp)
            if (todo != null) {
                result.add(todo)
            }
        }

        return result.sortedWith(
            compareByDescending<Todo> { it.createdAt }
                .thenByDescending { it.id }
        )
    }

    override suspend fun loadTodos(): List<Todo> = mutex.withLock {
        loadTodosLocked()
    }

    /**
     * Saves a todo locally with atomic draft and outbox persistence before returning success.
     * Enforces tombstone non-mutation, account contract version guard, and semantic scheduleToken rotation.
     */
    private suspend fun saveTodoLocallyLocked(todo: Todo) {

        val existingBase = dao.getServerBase(todo.id)
        if (existingBase != null && existingBase.deleted) {
            throw IllegalStateException("Cannot mutate permanently deleted tombstone with rowId: ${todo.id}")
        }

        val meta = getOrCreateMeta()
        val isReadyV2 = meta.syncStatus == "ready-v2" || meta.syncStatus == "READY_V2"
        val opContractVersion = if (isReadyV2) OutboxEntity.CONTRACT_VERSION_V2 else defaultContractVersion
        if (opContractVersion == OutboxEntity.CONTRACT_VERSION_V1 && todo.deadline != null) {
            throw IllegalArgumentException("Task deadlines and reminders require strict-v2 account contract")
        }

        val nextRev = meta.localRevisionCounter + 1L
        val updatedMeta = meta.copy(localRevisionCounter = nextRev)
        val activeEpoch = meta.activeEpoch ?: ""
        val existingDraft = dao.getDraft(todo.id)
        val existingOps = dao.getOutboxByRowId(todo.id)
        val latestExistingOp = existingOps.lastOrNull()

        // Calculate schedule token rotation from authoritative existing snapshot
        val existingTemporalProj = resolveTemporalProjection(existingDraft, latestExistingOp, existingBase)
        val existingDeadline = toTaskDeadline(existingTemporalProj)
        val existingRule = toReminderRule(existingTemporalProj, existingDeadline != null)
        val existingToken = existingTemporalProj.scheduleToken

        val finalScheduleToken: String? = if (todo.deadline == null) {
            null
        } else if (existingDeadline == null || existingToken == null) {
            todo.scheduleToken ?: generateUuid()
        } else {
            val changed = dev.tabbyrush.shared.contract.hasScheduleSemanticChange(
                oldDeadline = existingDeadline,
                oldRule = existingRule,
                newDeadline = todo.deadline,
                newRule = todo.reminderRule
            )
            if (changed) generateUuid() else existingToken
        }

        val validatedTodo = todo.copy(scheduleToken = finalScheduleToken)

        // Coalescing policy: only never-dispatched newly submitted pending ops (not historical/migrated)
        val pendingOp = existingOps.lastOrNull { it.deliveryState == OutboxEntity.STATE_PENDING }
        val canCoalesce = pendingOp != null &&
                pendingOp.transitionCheckpoint == null &&
                pendingOp.deliveryState == OutboxEntity.STATE_PENDING &&
                pendingOp.kind != StrictOperationKind.DELETE.wireName

        val kind: StrictOperationKind
        val expectedRev: Long
        val opId: String

        if (canCoalesce) {
            val op = requireNotNull(pendingOp)
            opId = op.opId
            kind = StrictOperationKind.fromWireName(op.kind) ?: StrictOperationKind.UPDATE
            expectedRev = op.expectedRevision
        } else {
            opId = generateUuid()
            if (existingBase == null || existingBase.serverRevision == 0L || existingBase.deleted) {
                kind = StrictOperationKind.CREATE
                expectedRev = 0L
            } else {
                kind = StrictOperationKind.UPDATE
                expectedRev = existingBase.serverRevision
            }
        }

        // Derive intent mask
        val intentMask: String = if (kind == StrictOperationKind.CREATE) {
            val parts = mutableListOf(OutboxEntity.INTENT_TITLE, OutboxEntity.INTENT_COMPLETED)
            if (validatedTodo.deadline != null) {
                parts.add(OutboxEntity.INTENT_DEADLINE)
                if (validatedTodo.reminderRule.enabled) {
                    parts.add(OutboxEntity.INTENT_REMINDER)
                }
            }
            parts.joinToString("|")
        } else {
            val masks = mutableListOf<String>()
            if (existingBase == null || existingBase.title != validatedTodo.title) {
                masks.add(OutboxEntity.INTENT_TITLE)
            }
            if (existingBase == null || existingBase.completed != validatedTodo.completed) {
                masks.add(OutboxEntity.INTENT_COMPLETED)
            }
            val deadlineChanged = existingBase == null ||
                    existingBase.deadlineDate != validatedTodo.deadline?.date ||
                    existingBase.deadlineTime != validatedTodo.deadline?.time ||
                    existingBase.deadlineZone != validatedTodo.deadline?.zone ||
                    existingBase.deadlineOffsetSeconds != validatedTodo.deadline?.offsetSeconds
            if (deadlineChanged && (validatedTodo.deadline != null || existingBase?.deadlineDate != null)) {
                masks.add(OutboxEntity.INTENT_DEADLINE)
            }
            val reminderChanged = existingBase == null ||
                    existingBase.reminderEnabled != validatedTodo.reminderRule.enabled ||
                    existingBase.reminderLeadMinutes != validatedTodo.reminderRule.leadMinutes ||
                    existingBase.reminderIntervalMinutes != validatedTodo.reminderRule.intervalMinutes ||
                    existingBase.reminderCount != validatedTodo.reminderRule.count
            if (reminderChanged && (validatedTodo.reminderRule.enabled || existingBase?.reminderEnabled == true)) {
                masks.add(OutboxEntity.INTENT_REMINDER)
            }
            if (masks.isEmpty()) OutboxEntity.INTENT_TITLE else masks.joinToString("|")
        }

        // Compute digest using version-appropriate algorithm
        val digest = if (opContractVersion == OutboxEntity.CONTRACT_VERSION_V2) {
            dev.tabbyrush.shared.contract.StrictSyncV2Digest.computeDigest(
                domain = dev.tabbyrush.shared.contract.StrictSyncV2Contract.PROTOCOL_VERSION,
                appId = appId,
                userId = userId,
                epoch = activeEpoch,
                tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
                rowId = validatedTodo.id,
                opId = opId,
                kind = kind,
                expectedRevision = expectedRev,
                data = validatedTodo.toStrictV2TodoData()
            )
        } else {
            StrictSyncDigest.computeDigest(
                domain = StrictSyncContract.PROTOCOL_VERSION,
                appId = appId,
                userId = userId,
                epoch = activeEpoch,
                tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
                rowId = validatedTodo.id,
                opId = opId,
                kind = kind,
                expectedRevision = expectedRev,
                data = StrictTodoData(
                    id = validatedTodo.id,
                    title = validatedTodo.title,
                    completed = validatedTodo.completed,
                    createdAt = validatedTodo.createdAt
                )
            )
        }

        val draftEntity = EditorDraftEntity(
            rowId = validatedTodo.id,
            draftTitle = validatedTodo.title,
            draftCompleted = validatedTodo.completed,
            localRevision = nextRev,
            updatedAt = validatedTodo.updatedAt,
            isDeleted = false,
            deadlineDate = validatedTodo.deadline?.date,
            deadlineTime = validatedTodo.deadline?.time,
            deadlineZone = validatedTodo.deadline?.zone,
            deadlineOffsetSeconds = validatedTodo.deadline?.offsetSeconds,
            reminderAnchorTime = validatedTodo.deadline?.reminderAnchorTime,
            deadlineAnchorEpochMs = validatedTodo.deadline?.anchorEpochMs,
            deadlineOverdueEpochMs = validatedTodo.deadline?.overdueEpochMs,
            reminderEnabled = validatedTodo.reminderRule.enabled,
            reminderLeadMinutes = validatedTodo.reminderRule.leadMinutes,
            reminderIntervalMinutes = validatedTodo.reminderRule.intervalMinutes,
            reminderCount = validatedTodo.reminderRule.count,
            scheduleToken = finalScheduleToken
        )

        val outboxEntity = OutboxEntity(
            opId = opId,
            epoch = activeEpoch,
            tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
            rowId = validatedTodo.id,
            kind = kind.wireName,
            expectedRevision = expectedRev,
            title = validatedTodo.title,
            completed = validatedTodo.completed,
            createdAt = validatedTodo.createdAt,
            digest = digest,
            localRevision = nextRev,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = validatedTodo.updatedAt,
            deadlineDate = validatedTodo.deadline?.date,
            deadlineTime = validatedTodo.deadline?.time,
            deadlineZone = validatedTodo.deadline?.zone,
            deadlineOffsetSeconds = validatedTodo.deadline?.offsetSeconds,
            reminderAnchorTime = validatedTodo.deadline?.reminderAnchorTime,
            deadlineAnchorEpochMs = validatedTodo.deadline?.anchorEpochMs,
            deadlineOverdueEpochMs = validatedTodo.deadline?.overdueEpochMs,
            reminderEnabled = validatedTodo.reminderRule.enabled,
            reminderLeadMinutes = validatedTodo.reminderRule.leadMinutes,
            reminderIntervalMinutes = validatedTodo.reminderRule.intervalMinutes,
            reminderCount = validatedTodo.reminderRule.count,
            scheduleToken = finalScheduleToken,
            contractVersion = opContractVersion,
            intentMask = intentMask
        )

        // Atomic write (C1)
        dao.saveTodoAtomic(updatedMeta, draftEntity, outboxEntity)
    }

    override suspend fun saveTodoLocally(todo: Todo): Unit = withMutationLock {
        saveTodoLocallyLocked(todo)
    }

    override suspend fun setCompletedLocally(
        id: String,
        completed: Boolean,
        nowEpochMs: Long
    ): CompletionChange = withMutationLock {
        val existingBase = dao.getServerBase(id)
        if (existingBase != null && existingBase.deleted) {
            return@withMutationLock CompletionChange.Missing
        }
        val current = loadTodoLocked(id) ?: return@withMutationLock CompletionChange.Missing
        if (current.completed == completed) {
            return@withMutationLock CompletionChange.Unchanged
        }
        val updatedTodo = current.copy(
            completed = completed,
            updatedAt = maxOf(nowEpochMs, current.updatedAt, current.createdAt)
        )
        saveTodoLocallyLocked(updatedTodo)
        CompletionChange.Changed
    }

    /**
     * Deletes a todo locally. Unsent un-migrated create-delete cancels locally without wire dispatch.
     * Otherwise enqueues a frozen delete operation with canonical digest.
     */
    override suspend fun deleteTodoLocally(id: String): Unit = withMutationLock {

        val existingBase = dao.getServerBase(id)
        val existingOps = dao.getOutboxByRowId(id)

        // If never committed to server and has only unsent PENDING create (not migrated), cancel locally:
        val hasOnlyPendingCreate = (existingBase == null || existingBase.deleted) &&
                existingOps.isNotEmpty() &&
                existingOps.all {
                    it.deliveryState == OutboxEntity.STATE_PENDING &&
                            it.transitionCheckpoint == null &&
                            it.kind == StrictOperationKind.CREATE.wireName
                }

        if (hasOnlyPendingCreate) {
            dao.cancelUnsentCreateAtomic(id)
            return@withMutationLock
        }

        val meta = getOrCreateMeta()
        val nextRev = meta.localRevisionCounter + 1L
        val updatedMeta = meta.copy(localRevisionCounter = nextRev)
        val activeEpoch = meta.activeEpoch ?: ""

        val draftEntity = EditorDraftEntity(
            rowId = id,
            draftTitle = "",
            draftCompleted = false,
            localRevision = nextRev,
            updatedAt = System.currentTimeMillis(),
            isDeleted = true
        )

        val opId = generateUuid()
        val expectedRev = existingBase?.serverRevision ?: 0L
        val kind = StrictOperationKind.DELETE
        val isReadyV2 = meta.syncStatus == "ready-v2" || meta.syncStatus == "READY_V2"
        val opContractVersion = if (isReadyV2) OutboxEntity.CONTRACT_VERSION_V2 else defaultContractVersion
        val digest = if (opContractVersion == OutboxEntity.CONTRACT_VERSION_V2) {
            dev.tabbyrush.shared.contract.StrictSyncV2Digest.computeDigest(
                domain = dev.tabbyrush.shared.contract.StrictSyncV2Contract.PROTOCOL_VERSION,
                appId = appId,
                userId = userId,
                epoch = activeEpoch,
                tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
                rowId = id,
                opId = opId,
                kind = kind,
                expectedRevision = expectedRev,
                data = null
            )
        } else {
            StrictSyncDigest.computeDigest(
                domain = StrictSyncContract.PROTOCOL_VERSION,
                appId = appId,
                userId = userId,
                epoch = activeEpoch,
                tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
                rowId = id,
                opId = opId,
                kind = kind,
                expectedRevision = expectedRev,
                data = null
            )
        }

        val outboxEntity = OutboxEntity(
            opId = opId,
            epoch = activeEpoch,
            tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
            rowId = id,
            kind = kind.wireName,
            expectedRevision = expectedRev,
            title = null,
            completed = null,
            createdAt = null,
            digest = digest,
            localRevision = nextRev,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = System.currentTimeMillis(),
            contractVersion = opContractVersion,
            intentMask = OutboxEntity.INTENT_DELETE
        )

        // Atomic write (C1)
        dao.deleteTodoAtomic(updatedMeta, draftEntity, outboxEntity)
    }

    /**
     * Marks an operation as DISPATCHING prior to socket I/O.
     */
    override suspend fun markDispatching(opId: String): Unit = mutex.withLock {
        dao.updateDeliveryState(opId, OutboxEntity.STATE_DISPATCHING)
    }

    /**
     * Reconciles an accepted operation with the server base atomically.
     * Enforces monotonic base advancement and preserves newer drafts.
     */
    override suspend fun reconcileAccepted(opId: String, serverRevision: Long): Unit = withMutationLock {
        val op = dao.getOutboxEntry(opId) ?: return@withMutationLock
        val currentBase = dao.getServerBase(op.rowId)

        val currentDraft = dao.getDraft(op.rowId)
        val clearDraft = currentDraft != null && currentDraft.localRevision <= op.localRevision

        // Monotonic check: Never regress confirmed server base (W1, W-E)
        if (currentBase != null && serverRevision < currentBase.serverRevision) {
            dao.discardRegressiveReconcileAtomic(opId, op.rowId, clearDraft)
            return@withMutationLock
        }
        val isV1Op = op.contractVersion == OutboxEntity.CONTRACT_VERSION_V1
        val intentMasks = op.intentMask?.split("|")?.toSet() ?: emptySet()
        val preservesRemoteDeadline = isV1Op || (intentMasks.isNotEmpty() &&
                !intentMasks.contains(OutboxEntity.INTENT_DEADLINE) &&
                !intentMasks.contains(OutboxEntity.INTENT_REMINDER))

        val updatedBase = if (op.kind == StrictOperationKind.DELETE.wireName) {
            ServerBaseEntity(
                rowId = op.rowId,
                serverRevision = serverRevision,
                deleted = true,
                title = "",
                completed = false,
                createdAt = op.createdAt ?: 0L,
                updatedAt = System.currentTimeMillis(),
                epoch = op.epoch,
                deadlineDate = null,
                deadlineTime = null,
                deadlineZone = null,
                deadlineOffsetSeconds = null,
                reminderAnchorTime = null,
                deadlineAnchorEpochMs = null,
                deadlineOverdueEpochMs = null,
                reminderEnabled = false,
                reminderLeadMinutes = 0L,
                reminderIntervalMinutes = null,
                reminderCount = 1L,
                scheduleToken = null
            )
        } else {
            val resolvedTitle = if (intentMasks.isNotEmpty() && !intentMasks.contains(OutboxEntity.INTENT_TITLE) && currentBase != null) {
                currentBase.title
            } else {
                op.title ?: currentBase?.title ?: ""
            }

            val resolvedCompleted = if (intentMasks.isNotEmpty() && !intentMasks.contains(OutboxEntity.INTENT_COMPLETED) && currentBase != null) {
                currentBase.completed
            } else {
                op.completed ?: currentBase?.completed ?: false
            }

            val useBaseDeadline = preservesRemoteDeadline && currentBase != null
            ServerBaseEntity(
                rowId = op.rowId,
                serverRevision = serverRevision,
                deleted = false,
                title = resolvedTitle,
                completed = resolvedCompleted,
                createdAt = op.createdAt ?: currentBase?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                epoch = op.epoch,
                deadlineDate = if (useBaseDeadline) currentBase.deadlineDate else op.deadlineDate,
                deadlineTime = if (useBaseDeadline) currentBase.deadlineTime else op.deadlineTime,
                deadlineZone = if (useBaseDeadline) currentBase.deadlineZone else op.deadlineZone,
                deadlineOffsetSeconds = if (useBaseDeadline) currentBase.deadlineOffsetSeconds else op.deadlineOffsetSeconds,
                reminderAnchorTime = if (useBaseDeadline) currentBase.reminderAnchorTime else op.reminderAnchorTime,
                deadlineAnchorEpochMs = if (useBaseDeadline) currentBase.deadlineAnchorEpochMs else op.deadlineAnchorEpochMs,
                deadlineOverdueEpochMs = if (useBaseDeadline) currentBase.deadlineOverdueEpochMs else op.deadlineOverdueEpochMs,
                reminderEnabled = if (useBaseDeadline) currentBase.reminderEnabled else op.reminderEnabled,
                reminderLeadMinutes = if (useBaseDeadline) currentBase.reminderLeadMinutes else op.reminderLeadMinutes,
                reminderIntervalMinutes = if (useBaseDeadline) currentBase.reminderIntervalMinutes else op.reminderIntervalMinutes,
                reminderCount = if (useBaseDeadline) currentBase.reminderCount else op.reminderCount,
                scheduleToken = if (useBaseDeadline) currentBase.scheduleToken else op.scheduleToken
            )
        }

        // Atomic reconcile (C1)
        dao.reconcileAcceptedAtomic(
            serverBase = updatedBase,
            opId = opId,
            draftRowId = op.rowId,
            clearDraft = clearDraft
        )
    }

    /**
     * Recovers any operations left in DISPATCHING state by resetting them to UNCERTAIN.
     */
    suspend fun recoverInterruptedDispatches(): Int = mutex.withLock {
        dao.recoverInterruptedDispatches()
    }

    suspend fun allocateNextLocalRevision(): Long = mutex.withLock {
        val meta = getOrCreateMeta()
        val nextRev = meta.localRevisionCounter + 1L
        dao.upsertMeta(meta.copy(localRevisionCounter = nextRev))
        nextRev
    }

    /**
     * Wipes all local database tables in the partition atomically.
     */
    override suspend fun wipeLocalData(): Unit = withMutationLock {
        dao.wipeAllTablesAtomic()
    }

    suspend fun getLocalSettings(): AccountLocalSettingsEntity = mutex.withLock {
        dao.getLocalSettings() ?: AccountLocalSettingsEntity().also {
            dao.upsertLocalSettings(it)
        }
    }

    suspend fun updateLocalSettings(settings: AccountLocalSettingsEntity): Unit = mutex.withLock {
        dao.upsertLocalSettings(settings)
    }

    suspend fun getLedgerEntriesForTask(generation: Long, taskId: String): List<ReminderDeliveryLedgerEntity> = mutex.withLock {
        dao.getLedgerEntriesForTask(accountKey.toString(), generation, taskId)
    }

    suspend fun upsertLedgerEntry(entry: ReminderDeliveryLedgerEntity): Unit = mutex.withLock {
        dao.upsertLedgerEntry(entry)
    }

    suspend fun cancelLedgerEntriesForTask(generation: Long, taskId: String): Unit = mutex.withLock {
        dao.cancelLedgerEntriesForTask(accountKey.toString(), generation, taskId, System.currentTimeMillis())
    }

    override fun close() {
        database?.close()
    }

    suspend fun getActiveEpoch(): String? = mutex.withLock {
        dao.getMeta()?.activeEpoch
    }

    suspend fun setActiveEpoch(epoch: String): Unit = mutex.withLock {
        val meta = getOrCreateMeta()
        dao.upsertMeta(meta.copy(activeEpoch = epoch))
    }

    private suspend fun getOrCreateMeta(): AccountSyncMetaEntity {
        return dao.getMeta() ?: AccountSyncMetaEntity(id = AccountSyncMetaEntity.SINGLETON_ID).also {
            dao.upsertMeta(it)
        }
    }

    suspend fun isAccountV2(): Boolean = mutex.withLock {
        val s = getOrCreateMeta().syncStatus
        s == "ready-v2" || s == "READY_V2"
    }

    private fun generateUuid(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte() // UUID v4
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte() // RFC 4122 variant
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private data class TemporalProjection(
        val deadlineDate: String?,
        val deadlineTime: String?,
        val deadlineZone: String?,
        val deadlineOffsetSeconds: Long?,
        val reminderAnchorTime: String?,
        val deadlineAnchorEpochMs: Long?,
        val deadlineOverdueEpochMs: Long?,
        val reminderEnabled: Boolean,
        val reminderLeadMinutes: Long,
        val reminderIntervalMinutes: Long?,
        val reminderCount: Long,
        val scheduleToken: String?
    )

    private fun resolveTemporalProjection(
        draft: EditorDraftEntity?,
        op: OutboxEntity?,
        base: ServerBaseEntity?
    ): TemporalProjection {
        return when {
            draft != null -> TemporalProjection(
                deadlineDate = draft.deadlineDate,
                deadlineTime = draft.deadlineTime,
                deadlineZone = draft.deadlineZone,
                deadlineOffsetSeconds = draft.deadlineOffsetSeconds,
                reminderAnchorTime = draft.reminderAnchorTime,
                deadlineAnchorEpochMs = draft.deadlineAnchorEpochMs,
                deadlineOverdueEpochMs = draft.deadlineOverdueEpochMs,
                reminderEnabled = draft.reminderEnabled,
                reminderLeadMinutes = draft.reminderLeadMinutes,
                reminderIntervalMinutes = draft.reminderIntervalMinutes,
                reminderCount = draft.reminderCount,
                scheduleToken = draft.scheduleToken
            )
            op != null && op.contractVersion != OutboxEntity.CONTRACT_VERSION_V1 -> TemporalProjection(
                deadlineDate = op.deadlineDate,
                deadlineTime = op.deadlineTime,
                deadlineZone = op.deadlineZone,
                deadlineOffsetSeconds = op.deadlineOffsetSeconds,
                reminderAnchorTime = op.reminderAnchorTime,
                deadlineAnchorEpochMs = op.deadlineAnchorEpochMs,
                deadlineOverdueEpochMs = op.deadlineOverdueEpochMs,
                reminderEnabled = op.reminderEnabled,
                reminderLeadMinutes = op.reminderLeadMinutes,
                reminderIntervalMinutes = op.reminderIntervalMinutes,
                reminderCount = op.reminderCount,
                scheduleToken = op.scheduleToken
            )
            base != null -> TemporalProjection(
                deadlineDate = base.deadlineDate,
                deadlineTime = base.deadlineTime,
                deadlineZone = base.deadlineZone,
                deadlineOffsetSeconds = base.deadlineOffsetSeconds,
                reminderAnchorTime = base.reminderAnchorTime,
                deadlineAnchorEpochMs = base.deadlineAnchorEpochMs,
                deadlineOverdueEpochMs = base.deadlineOverdueEpochMs,
                reminderEnabled = base.reminderEnabled,
                reminderLeadMinutes = base.reminderLeadMinutes,
                reminderIntervalMinutes = base.reminderIntervalMinutes,
                reminderCount = base.reminderCount,
                scheduleToken = base.scheduleToken
            )
            else -> TemporalProjection(
                deadlineDate = null,
                deadlineTime = null,
                deadlineZone = null,
                deadlineOffsetSeconds = null,
                reminderAnchorTime = null,
                deadlineAnchorEpochMs = null,
                deadlineOverdueEpochMs = null,
                reminderEnabled = false,
                reminderLeadMinutes = 0L,
                reminderIntervalMinutes = null,
                reminderCount = 1L,
                scheduleToken = null
            )
        }
    }

    private fun toTaskDeadline(p: TemporalProjection): TaskDeadline? {
        return if (p.deadlineDate != null && p.deadlineZone != null && p.deadlineOffsetSeconds != null && p.deadlineAnchorEpochMs != null && p.deadlineOverdueEpochMs != null) {
            TaskDeadline(
                date = p.deadlineDate,
                time = p.deadlineTime,
                zone = p.deadlineZone,
                offsetSeconds = p.deadlineOffsetSeconds,
                reminderAnchorTime = p.reminderAnchorTime,
                anchorEpochMs = p.deadlineAnchorEpochMs,
                overdueEpochMs = p.deadlineOverdueEpochMs
            )
        } else null
    }

    private fun toReminderRule(p: TemporalProjection, hasDeadline: Boolean): ReminderRule {
        return if (hasDeadline) {
            ReminderRule(
                enabled = p.reminderEnabled,
                leadMinutes = p.reminderLeadMinutes,
                intervalMinutes = p.reminderIntervalMinutes,
                count = p.reminderCount
            )
        } else {
            ReminderRule.DISABLED
        }
    }
}
