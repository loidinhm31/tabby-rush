package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AccountStore
import dev.tabbyrush.shared.contract.AcknowledgedTuple
import dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME
import dev.tabbyrush.shared.contract.AccessLossEvidence
import dev.tabbyrush.shared.contract.AccessLossReason
import dev.tabbyrush.shared.contract.AssignmentIntent
import dev.tabbyrush.shared.contract.AssignmentSlotRecord
import dev.tabbyrush.shared.contract.AssignmentSlotResult
import dev.tabbyrush.shared.contract.CanonicalRecordRef
import dev.tabbyrush.shared.contract.CanonicalRecordSnapshot
import dev.tabbyrush.shared.contract.CollaborationStore
import dev.tabbyrush.shared.contract.CollaborationStoreFactory
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.ConflictTuple
import dev.tabbyrush.shared.contract.FrozenOutboxEntry
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.OwnerBindingState
import dev.tabbyrush.shared.contract.PersistedScheduleProjector
import dev.tabbyrush.shared.contract.PersistedTaskSchedule
import dev.tabbyrush.shared.contract.RecordFailure
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.SyncStorage
import dev.tabbyrush.shared.contract.TaskCapabilities
import dev.tabbyrush.shared.contract.TaskAccessState
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.TaskDataContract
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.TaskMutationResult
import dev.tabbyrush.shared.contract.TaskOperationKind
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.hasScheduleSemanticChange
import dev.tabbyrush.shared.sync.PullRecordDto
import dev.tabbyrush.shared.sync.PushRecordDto
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import dev.tabbyrush.shared.sync.TaskJsonMerge
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.random.Random

private val PENDING_OUTBOX_STATES = setOf(
    OutboxEntity.STATE_PENDING,
    OutboxEntity.STATE_DISPATCHING,
    OutboxEntity.STATE_UNCERTAIN
)

/**
 * RoomAccountStore implements AccountStore, CollaborationStore, and SyncStorage
 * using TabbyRushDatabase. Coordinates canonical task storage, two-stage owner
 * binding, access reconciliation, privacy-first revocation purge, and frozen FIFO mutations.
 */
class RoomAccountStore(
    override val accountKey: AccountKey,
    val userId: String,
    val database: TabbyRushDatabase? = null,
    val appId: String = "tabby-rush",
    daoOverride: TabbyRushDao? = null,
    val canonicalOrigin: String? = null
) : AccountStore, CollaborationStore, SyncStorage {

    val dao: TabbyRushDao = daoOverride ?: requireNotNull(database) {
        "Either database or daoOverride must be provided"
    }.tabbyRushDao()

    val mutex = Mutex()

    @kotlin.jvm.Volatile
    var isFrozen: Boolean = false

    @kotlin.jvm.Volatile
    var deliveryGate: ReminderDeliveryGate? = null

    @kotlin.jvm.Volatile
    private var _cachedAccessGeneration: Long = 0L

    override val accessGeneration: Long
        get() = _cachedAccessGeneration

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
                    if (result != CompletionChange.Unchanged && result != CompletionChange.Missing && result != CompletionChange.AutomaticParentRejected) {
                        notifyInvalidated()
                    }
                    result
                }
            }
        } else {
            mutex.withLock {
                check(!isFrozen) { "Cannot mutate store while account leave is in progress" }
                val result = block()
                if (result != CompletionChange.Unchanged && result != CompletionChange.Missing && result != CompletionChange.AutomaticParentRejected) {
                    notifyInvalidated()
                }
                result
            }
        }
    }

    // -------------------------------------------------------------------------
    // Two-Stage Owner Binding (CollaborationStore)
    // -------------------------------------------------------------------------

    override suspend fun initializeOwnerBinding(identity: AccountIdentity): OwnerBindingState = mutex.withLock {
        if (identity.userId != userId) {
            return@withLock OwnerBindingState.UNRESOLVED_LOCAL
        }
        if (canonicalOrigin != null) {
            val expectedKey = AccountKey.create(canonicalOrigin, identity.userId)
            if (expectedKey != accountKey) {
                return@withLock OwnerBindingState.UNRESOLVED_LOCAL
            }
        }
        val meta = dao.getMeta()
        if (meta?.boundOwnerUserId != null && meta.boundOwnerUserId != identity.userId) {
            return@withLock OwnerBindingState.UNRESOLVED_LOCAL
        }
        dao.bindLegacyRowsAtomic(identity.userId)
        _cachedAccessGeneration = dao.getMeta()?.accessGeneration ?: 0L
        notifyInvalidated()
        OwnerBindingState.BOUND
    }

    override suspend fun getOwnerBindingState(): OwnerBindingState = mutex.withLock {
        val meta = dao.getMeta()
        if (meta?.ownerBindingState == "BOUND" && meta.boundOwnerUserId != null) {
            OwnerBindingState.BOUND
        } else {
            OwnerBindingState.UNRESOLVED_LOCAL
        }
    }

    private fun resolveBindingKey(targetOwnerUserId: String, isUnresolved: Boolean): String {
        return if (isUnresolved && targetOwnerUserId == userId) {
            BINDING_LOCAL_ACCOUNT
        } else {
            boundBindingKey(targetOwnerUserId)
        }
    }

    // -------------------------------------------------------------------------
    // Access Metadata (CollaborationStore)
    // -------------------------------------------------------------------------

    override suspend fun accessFor(taskRef: TaskRef): TaskAccessState? = mutex.withLock {
        accessForLocked(taskRef)
    }

    internal suspend fun accessForLocked(taskRef: TaskRef): TaskAccessState? {
        val accessEntity = dao.getAccess(taskRef.ownerUserId, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        if (accessEntity != null) {
            return accessEntity.toTaskAccessState()
        }
        if (taskRef.ownerUserId == userId) {
            val base = dao.getServerBase(boundBindingKey(userId), TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
                ?: dao.getServerBase(BINDING_LOCAL_ACCOUNT, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
            return creatorAccessState(taskRef, revision = base?.serverRevision ?: 0L)
        }
        return null
    }

    override fun observeAccessFor(taskRef: TaskRef): Flow<TaskAccessState?> {
        return dao.observeAccess(taskRef.ownerUserId, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
            .map { entity ->
                entity?.toTaskAccessState() ?: if (taskRef.ownerUserId == userId) {
                    creatorAccessState(taskRef, revision = 0L)
                } else null
            }.distinctUntilChanged()
    }

    // -------------------------------------------------------------------------
    // Staged Full-Scan & Promotion (CollaborationStore)
    // -------------------------------------------------------------------------

    override suspend fun stageDataPage(revision: Long, records: List<CanonicalRecordSnapshot>): Unit = withMutationLock {
        val meta = getOrCreateMeta()
        val generation = if (meta.scanState == "SCANNING") meta.scanGeneration else dao.beginScanAtomic().scanGeneration
        val entities = records.map { snap ->
            val fields = parseJsonToTaskFields(snap.dataJson)
            ScanStagingEntity(
                bindingKey = boundBindingKey(snap.ref.ownerUserId),
                ownerBinding = BINDING_BOUND,
                ownerUserId = snap.ref.ownerUserId,
                tableName = snap.ref.tableName,
                rowId = snap.ref.rowId,
                serverRevision = snap.version,
                deleted = snap.deleted,
                title = fields.title ?: "",
                completed = fields.completed ?: false,
                createdAt = fields.createdAt ?: 0L,
                deadlineDate = fields.deadline?.date,
                deadlineTime = fields.deadline?.time,
                deadlineZone = fields.deadline?.zone,
                deadlineOffsetSeconds = fields.deadline?.offsetSeconds,
                reminderAnchorTime = fields.deadline?.reminderAnchorTime,
                deadlineAnchorEpochMs = fields.deadline?.anchorEpochMs,
                deadlineOverdueEpochMs = fields.deadline?.overdueEpochMs,
                reminderEnabled = fields.reminderRule.enabled,
                reminderLeadMinutes = fields.reminderRule.leadMinutes,
                reminderIntervalMinutes = fields.reminderRule.intervalMinutes,
                reminderCount = fields.reminderRule.count,
                scheduleToken = fields.scheduleToken,
                parentTaskId = fields.parentTaskId,
                rawJson = snap.dataJson,
                syncedAt = snap.syncedAt.toString(),
                scanGeneration = generation
            )
        }
        dao.upsertStaging(entities)
    }

    override suspend fun stageAccessResults(revision: Long, results: List<TaskAccessState>): Unit = withMutationLock {
        val meta = getOrCreateMeta()
        val generation = meta.scanGeneration
        val entities = results.map { AccessCheckStagingEntity.fromTaskAccessState(generation, it) }
        dao.upsertStagedAccess(entities)
    }

    override suspend fun promoteReconciliation(accessRevision: Long, nowEpochMs: Long): Boolean = withMutationLock {
        val meta = getOrCreateMeta()
        dao.promoteReconciliationAtomic(accessRevision, meta.scanGeneration, nowEpochMs)
        _cachedAccessGeneration = dao.getMeta()?.accessGeneration ?: 0L
        true
    }

    override suspend fun clearStagedReconciliation(): Unit = withMutationLock {
        val meta = getOrCreateMeta()
        dao.clearStagingForGeneration(meta.scanGeneration)
        dao.clearStagedAccessForGeneration(meta.scanGeneration)
        dao.upsertMeta(meta.copy(scanState = "REQUIRED"))
    }

    // -------------------------------------------------------------------------
    // Privacy-First Revocation Purge (CollaborationStore)
    // -------------------------------------------------------------------------

    override suspend fun invalidateAccess(taskRef: TaskRef, reason: AccessLossReason, nowEpochMs: Long): Long = withMutationLock {
        val boundKey = boundBindingKey(taskRef.ownerUserId)
        val ops = dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        val op = ops.lastOrNull()
        val base = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        val currentAccess = dao.getAccess(taskRef.ownerUserId, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        val draft = dao.getDraft(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        if (currentAccess?.status == TaskAccessStatus.Unavailable.name && base == null && draft == null && ops.isEmpty()) {
            return@withMutationLock dao.getMeta()?.accessGeneration ?: 1L
        }
        val evidence = AccessLossEvidenceEntity(
            evidenceId = generateUuid(),
            ownerUserId = taskRef.ownerUserId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = taskRef.rowId,
            opId = op?.opId,
            baseVersion = base?.serverRevision,
            submittedVersion = op?.version,
            timestamp = nowEpochMs,
            reason = reason.name
        )
        dao.purgeRevokedTaskAtomic(
            ownerUserId = taskRef.ownerUserId,
            rowId = taskRef.rowId,
            evidence = evidence,
            accountKey = accountKey.toString(),
            nowEpochMs = nowEpochMs
        )
        val newGen = dao.getMeta()?.accessGeneration ?: 1L
        _cachedAccessGeneration = newGen
        newGen
    }

    override suspend fun invalidateAllSharedAccess(reason: AccessLossReason, nowEpochMs: Long): Long = withMutationLock {
        dao.purgeAllSharedTasksAtomic(
            keepOwnerUserId = userId,
            nowEpochMs = nowEpochMs
        )
        val newGen = dao.getMeta()?.accessGeneration ?: 1L
        _cachedAccessGeneration = newGen
        newGen
    }

    override suspend fun listAccessLossEvidence(): List<AccessLossEvidence> = mutex.withLock {
        dao.getAllAccessLossEvidence().map { it.toAccessLossEvidence() }
    }

    override suspend fun dismissAccessLossEvidence(ref: TaskRef): Unit = withMutationLock {
        dao.deleteAccessLossEvidenceByRef(ref.ownerUserId, ref.rowId)
    }

    // -------------------------------------------------------------------------
    // SyncStorage Implementation
    // -------------------------------------------------------------------------

    override suspend fun prepareFrozenOutbox(): List<FrozenOutboxEntry> = mutex.withLock {
        getOrCreateMeta()
        // Legacy unresolved rows remain strictly local-only and cannot be dispatched over the wire
        val pendingOps = dao.getAllOutbox().filter {
            it.deliveryState == OutboxEntity.STATE_PENDING && it.ownerBinding != BINDING_LOCAL_ACCOUNT
        }
        val frozen = mutableListOf<FrozenOutboxEntry>()
        for (op in pendingOps) {
            val owner = op.ownerUserId ?: userId
            val ref = CanonicalRecordRef(ownerUserId = owner, tableName = op.tableName, rowId = op.rowId)
            val kind = when (op.kind.lowercase()) {
                "create" -> TaskOperationKind.CREATE
                "delete" -> TaskOperationKind.DELETE
                else -> TaskOperationKind.UPDATE
            }
            frozen.add(
                FrozenOutboxEntry(
                    opId = op.opId,
                    ref = ref,
                    kind = kind,
                    dataJson = op.payloadJson,
                    version = op.version ?: (op.assumedServerVersion?.plus(1L) ?: 1L),
                    assumedServerVersion = op.assumedServerVersion,
                    deleted = op.kind == TaskOperationKind.DELETE.wireName,
                    intentMask = op.intentMask
                )
            )
            dao.upsertOutbox(op.copy(deliveryState = OutboxEntity.STATE_DISPATCHING))
        }
        frozen
    }

    override suspend fun acknowledgeAccepted(tuples: List<AcknowledgedTuple>): Unit = withMutationLock {
        val isUnresolved = getOrCreateMeta().ownerBindingState != "BOUND"
        for (tuple in tuples) {
            val boundOps = dao.getOutboxByRow(boundBindingKey(tuple.ref.ownerUserId), tuple.ref.tableName, tuple.ref.rowId)
            val ops = if (boundOps.isEmpty() && isUnresolved && tuple.ref.ownerUserId == userId) {
                dao.getOutboxByRow(BINDING_LOCAL_ACCOUNT, tuple.ref.tableName, tuple.ref.rowId)
            } else boundOps
            val matching = ops.firstOrNull {
                it.deliveryState == OutboxEntity.STATE_DISPATCHING && it.version == tuple.submittedVersion
            } ?: continue
            reconcileAcceptedLocked(matching.opId, tuple.submittedVersion)
        }
    }

    override suspend fun recordConflicts(conflicts: List<ConflictTuple>): Unit = withMutationLock {
        for (conflict in conflicts) {
            val boundKey = boundBindingKey(conflict.ref.ownerUserId)
            val ops = dao.getOutboxByRow(boundKey, conflict.ref.tableName, conflict.ref.rowId)
            val matching = ops.firstOrNull { it.deliveryState == OutboxEntity.STATE_DISPATCHING }
            if (matching != null) {
                dao.upsertOutbox(matching.copy(
                    deliveryState = OutboxEntity.STATE_CONFLICT,
                    errorMessage = "Version conflict: client ${conflict.clientVersion}, server ${conflict.serverVersion}"
                ))
            }
        }
    }

    override suspend fun recordFailures(failures: List<RecordFailure>): Unit = withMutationLock {
        for (failure in failures) {
            val boundKey = boundBindingKey(failure.ref.ownerUserId)
            val ops = dao.getOutboxByRow(boundKey, failure.ref.tableName, failure.ref.rowId)
            val matching = ops.firstOrNull { it.deliveryState == OutboxEntity.STATE_DISPATCHING }
            if (matching != null) {
                if (failure.error.contains("OPERATION_FORBIDDEN")) {
                    // OPERATION_FORBIDDEN marks only the forbidden intent non-replayable and preserves valid task access
                    dao.upsertOutbox(matching.copy(
                        deliveryState = "DISCARDED",
                        errorMessage = failure.error,
                        resolution = "OPERATION_FORBIDDEN"
                    ))
                } else if (failure.error.contains("ACCESS_DENIED")) {
                    // ACCESS_DENIED invalidates shared access
                    invalidateAccess(failure.ref.toTaskRef(), AccessLossReason.REVOKED, System.currentTimeMillis())
                } else {
                    dao.upsertOutbox(matching.copy(
                        deliveryState = OutboxEntity.STATE_UNCERTAIN,
                        errorMessage = failure.error
                    ))
                }
            }
        }
    }

    override suspend fun readConfirmedBase(ref: CanonicalRecordRef): CanonicalRecordSnapshot? = mutex.withLock {
        val boundKey = boundBindingKey(ref.ownerUserId)
        val base = dao.getServerBase(boundKey, ref.tableName, ref.rowId)
            ?: dao.getServerBase(BINDING_LOCAL_ACCOUNT, ref.tableName, ref.rowId)
            ?: return@withLock null
        CanonicalRecordSnapshot(
            ref = ref,
            dataJson = base.rawJson ?: "{}",
            version = base.serverRevision,
            syncedAt = base.updatedAt,
            deleted = base.deleted
        )
    }

    // -------------------------------------------------------------------------
    // Schedule Projections (AccountStore)
    // -------------------------------------------------------------------------

    override suspend fun getPersistedScheduleProjection(): List<PersistedTaskSchedule> = mutex.withLock {
        val snapshot = dao.getScheduleSnapshotAtomic(userId)
        PersistedScheduleProjector.computeProjection(snapshot.serverBases, snapshot.outboxEntries, snapshot.accessRecords)
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

    // -------------------------------------------------------------------------
    // Task Projections & Queries (AccountStore)
    // -------------------------------------------------------------------------

    private fun isProvablyUnsentCreate(base: ServerBaseEntity?, ops: List<OutboxEntity>): Boolean {
        return base == null &&
                ops.isNotEmpty() &&
                ops.all {
                    it.deliveryState == OutboxEntity.STATE_PENDING &&
                            it.envelopeJson == null && it.legacyEvidenceId == null && it.dispatchHistory == "NEVER_DISPATCHED" &&
                            it.kind == TaskOperationKind.CREATE.wireName
                }
    }

    private fun projectSingleTodo(
        taskRef: TaskRef,
        base: ServerBaseEntity?,
        draft: EditorDraftEntity?,
        rowOps: List<OutboxEntity>,
        latestOp: OutboxEntity?,
        access: TaskAccessEntity?,
        includeDeleted: Boolean = false
    ): Todo? {
        if (access?.status == TaskAccessStatus.Unavailable.name) {
            return null
        }

        val isDeletedLocally = when {
            draft?.isDeleted == true -> true
            latestOp != null && latestOp.kind == TaskOperationKind.DELETE.wireName -> true
            base?.deleted == true -> true
            else -> false
        }
        if (base?.projectionError != null && !base.deleted && draft == null && latestOp == null) return null
        if (draft == null && latestOp == null && base == null) {
            return null
        }

        if (isDeletedLocally && !includeDeleted) {
            return null
        }

        val title = draft?.draftTitle
            ?: latestOp?.title
            ?: base?.title
            ?: ""

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
        val parentTaskId = when {
            draft != null -> draft.parentTaskId
            latestOp != null -> latestOp.parentTaskId
            base != null -> base.parentTaskId
            else -> null
        }
        val parentTaskRef = parentTaskId?.let { TaskRef(ownerUserId = taskRef.ownerUserId, rowId = it) }

        val accessState = if (taskRef.ownerUserId == userId) {
            creatorAccessState(taskRef, revision = serverRevision)
        } else {
            access?.toTaskAccessState()
        }

        return Todo(
            taskRef = taskRef,
            title = title,
            completed = completed,
            createdAt = createdAt,
            updatedAt = if (updatedAt >= createdAt) updatedAt else createdAt,
            serverRevision = serverRevision,
            isPending = isPending,
            isDeleted = isDeletedLocally,
            deadline = deadline,
            reminderRule = reminderRule,
            scheduleToken = scheduleToken,
            parentTaskRef = parentTaskRef,
            accessState = accessState
        )
    }
    private fun creatorAccessState(taskRef: TaskRef, revision: Long): TaskAccessState {
        return TaskAccessState(
            ref = taskRef,
            revision = revision,
            capabilities = TaskCapabilities.OWNER,
            coverage = HierarchyCoverage.FullGroup,
            status = TaskAccessStatus.Accessible
        )
    }

    private suspend fun loadTodoLocked(taskRef: TaskRef, includeDeleted: Boolean = false): Todo? {
        val meta = dao.getMeta()
        val isUnresolved = meta?.ownerBindingState != "BOUND"
        val boundKey = resolveBindingKey(taskRef.ownerUserId, isUnresolved)

        val base = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
            ?: if (isUnresolved) dao.getServerBase(BINDING_LOCAL_ACCOUNT, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId) else null
        val draft = dao.getDraft(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
            ?: if (isUnresolved) dao.getDraft(BINDING_LOCAL_ACCOUNT, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId) else null
        val rowOps = dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        val latestOp = rowOps.lastOrNull()
        val access = dao.getAccess(taskRef.ownerUserId, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)

        return projectSingleTodo(taskRef, base, draft, rowOps, latestOp, access, includeDeleted)
    }

    private suspend fun loadTodosLocked(includeDeleted: Boolean = false): List<Todo> {
        val meta = dao.getMeta()
        val isUnresolved = meta?.ownerBindingState != "BOUND"

        val serverBaseList = dao.getAllServerBase().filter { it.tableName == TaskDataContract.DEFAULT_TABLE_NAME }
        val draftsList = dao.getAllDrafts().filter { it.tableName == TaskDataContract.DEFAULT_TABLE_NAME }
        val allOutbox = dao.getAllOutbox().filter { it.tableName == TaskDataContract.DEFAULT_TABLE_NAME }
        val accessList = dao.getAllAccess().associateBy { TaskRef(ownerUserId = it.ownerUserId, rowId = it.rowId) }

        val serverBaseMap = serverBaseList.associateBy {
            TaskRef(ownerUserId = it.ownerUserId ?: userId, rowId = it.rowId)
        }
        val draftsMap = draftsList.associateBy {
            TaskRef(ownerUserId = it.ownerUserId ?: userId, rowId = it.rowId)
        }
        val outboxByRef = allOutbox.groupBy {
            TaskRef(ownerUserId = it.ownerUserId ?: userId, rowId = it.rowId)
        }

        val allRefs = (serverBaseMap.keys + draftsMap.keys + outboxByRef.keys).toSet()
        val result = mutableListOf<Todo>()

        for (taskRef in allRefs) {
            val base = serverBaseMap[taskRef]
            val draft = draftsMap[taskRef]
            val rowOps = outboxByRef[taskRef] ?: emptyList()
            val latestOp = rowOps.lastOrNull()
            val access = accessList[taskRef]
            val todo = projectSingleTodo(taskRef, base, draft, rowOps, latestOp, access, includeDeleted)
            if (todo != null) {
                result.add(todo)
            }
        }

        return result.sortedWith(
            compareByDescending<Todo> { it.createdAt }
                .thenBy { it.taskRef }
        )
    }

    override suspend fun loadTodos(): List<Todo> = mutex.withLock {
        val snapshot = loadTodosLocked(includeDeleted = true)
        val deletedRefs = snapshot.filter { it.isDeleted }.mapTo(mutableSetOf()) { it.taskRef }
        snapshot.filter { !it.isDeleted && (it.parentTaskRef == null || it.parentTaskRef !in deletedRefs) }
    }

    suspend fun loadAllTodos(includeDeleted: Boolean = true): List<Todo> = mutex.withLock {
        loadTodosLocked(includeDeleted = includeDeleted)
    }

    override suspend fun getTodo(taskRef: TaskRef): Todo? = mutex.withLock {
        loadTodoLocked(taskRef, includeDeleted = false)
    }

    // -------------------------------------------------------------------------
    // Task Mutations (AccountStore)
    // -------------------------------------------------------------------------

    override suspend fun saveTodoLocally(todo: Todo): TaskMutationResult = withMutationLock {
        val meta = getOrCreateMeta()
        val isUnresolved = meta.ownerBindingState != "BOUND"
        val boundKey = resolveBindingKey(todo.taskRef.ownerUserId, isUnresolved)

        val existingBase = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, todo.taskRef.rowId)
            ?: if (isUnresolved) dao.getServerBase(BINDING_LOCAL_ACCOUNT, TaskDataContract.DEFAULT_TABLE_NAME, todo.taskRef.rowId) else null
        if (existingBase != null && existingBase.deleted) {
            return@withMutationLock TaskMutationResult.Missing
        }

        val current = loadTodoLocked(todo.taskRef)
        val access = accessForLocked(todo.taskRef)

        // Capability check
        if (current != null) {
            if (current.parentTaskRef != todo.parentTaskRef || current.createdAt != todo.createdAt) {
                return@withMutationLock TaskMutationResult.InvalidStructure("Task creation time and parent linkage are immutable")
            }
            val canEdit = todo.taskRef.ownerUserId == userId || access?.canEditContent == true
            if (!canEdit) {
                return@withMutationLock TaskMutationResult.AccessDenied
            }
        } else {
            // New task create: only creator creates roots/children
            if (todo.taskRef.ownerUserId != userId) {
                return@withMutationLock TaskMutationResult.AccessDenied
            }
            if (todo.parentTaskRef != null) {
                val parent = loadTodoLocked(todo.parentTaskRef)
                if (parent == null || parent.isDeleted) {
                    return@withMutationLock TaskMutationResult.Missing
                }
                if (parent.parentTaskRef != null) {
                    return@withMutationLock TaskMutationResult.InvalidStructure("Tasks support only two hierarchy levels")
                }
            }
        }

        if (current != null &&
            current.title == todo.title &&
            current.completed == todo.completed &&
            current.deadline == todo.deadline &&
            current.reminderRule == todo.reminderRule &&
            current.parentTaskRef == todo.parentTaskRef
        ) {
            return@withMutationLock TaskMutationResult.Unchanged
        }

        val saved = saveTodoLocallyLocked(todo, boundKey = boundKey, isUnresolved = isUnresolved)
        TaskMutationResult.Changed(saved)
    }

    /** Copy the displayed local version and retire only the canonical revisions it represents. */
    suspend fun copyLatestConflictAsNew(taskRef: TaskRef): Unit = withMutationLock {
        if (accessForLocked(taskRef)?.status == TaskAccessStatus.Unavailable) return@withMutationLock
        val meta = getOrCreateMeta()
        val isUnresolved = meta.ownerBindingState != "BOUND"
        val sourceBindingKey = resolveBindingKey(taskRef.ownerUserId, isUnresolved)
        val todoOps = dao.getOutboxByRow(sourceBindingKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        val assignOps = dao.getOutboxByRow(sourceBindingKey, ASSIGNMENT_TABLE_NAME, taskRef.rowId)
        val allRowOps = dao.getOutboxByRowId(taskRef.rowId).filter { it.ownerUserId == taskRef.ownerUserId &&
            (it.tableName == TaskDataContract.DEFAULT_TABLE_NAME || it.tableName == ASSIGNMENT_TABLE_NAME) }
        val hasConflictOrReview = (todoOps + assignOps + allRowOps).any {
            it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW
        }
        if (!hasConflictOrReview) return@withMutationLock
        val source = loadTodoLocked(taskRef, includeDeleted = true) ?: return@withMutationLock
        val draft = dao.getDraft(sourceBindingKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        val sourceTodoRevision = maxOf(draft?.localRevision ?: 0L, todoOps.lastOrNull()?.localRevision ?: 0L)
        val sourceAssignRevision = assignOps.filter {
            it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW
        }.maxOfOrNull { it.localRevision }
        val now = System.currentTimeMillis()
        val copy = source.copy(
            taskRef = TaskRef(userId, generateUuid()),
            parentTaskRef = null,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false,
            isDeleted = false,
            accessState = null,
            scheduleToken = if (source.deadline != null) generateUuid() else null
        )
        saveTodoLocallyLocked(
            copy,
            boundKey = resolveBindingKey(userId, isUnresolved),
            isUnresolved = isUnresolved,
            copiedSourceBindingKey = sourceBindingKey,
            copiedSourceRowId = taskRef.rowId,
            sourceTodoRevision = sourceTodoRevision,
            sourceAssignRevision = sourceAssignRevision
        )
    }

    private suspend fun saveTodoLocallyLocked(
        requestedTodo: Todo,
        boundKey: String,
        isUnresolved: Boolean,
        copiedSourceBindingKey: String? = null,
        copiedSourceRowId: String? = null,
        sourceTodoRevision: Long = 0L,
        sourceAssignRevision: Long? = null
    ): Todo {
        val meta = getOrCreateMeta()
        val nextRev = nextRevision(meta.localRevisionCounter)
        val updatedMeta = meta.copy(localRevisionCounter = nextRev)
        val current = loadTodoLocked(requestedTodo.taskRef, includeDeleted = true)
        val todo = if (current != null && hasScheduleSemanticChange(
                current.deadline, current.reminderRule, requestedTodo.deadline, requestedTodo.reminderRule
            )) {
            requestedTodo.copy(scheduleToken = if (requestedTodo.deadline != null) generateUuid() else null)
        } else requestedTodo

        val existingBase = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, todo.taskRef.rowId)
            ?: if (isUnresolved) dao.getServerBase(BINDING_LOCAL_ACCOUNT, TaskDataContract.DEFAULT_TABLE_NAME, todo.taskRef.rowId) else null
        val existingOps = dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, todo.taskRef.rowId)

        val intentMask = buildList {
            if (current == null || current.title != todo.title) add(OutboxEntity.INTENT_TITLE)
            if (current == null || current.completed != todo.completed) add(OutboxEntity.INTENT_COMPLETED)
            if (current == null || current.deadline != todo.deadline) add(OutboxEntity.INTENT_DEADLINE)
            if (current == null || current.reminderRule != todo.reminderRule) add(OutboxEntity.INTENT_REMINDER)
            if (existingOps.any { it.intentMask?.contains(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION) == true }) {
                add(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION)
            }
        }.joinToString("|")
        val draftEntity = EditorDraftEntity(
            bindingKey = boundKey,
            ownerBinding = if (isUnresolved) BINDING_LOCAL_ACCOUNT else BINDING_BOUND,
            ownerUserId = if (isUnresolved) null else todo.taskRef.ownerUserId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = todo.taskRef.rowId,
            draftTitle = todo.title,
            draftCompleted = todo.completed,
            localRevision = nextRev,
            updatedAt = todo.updatedAt,
            isDeleted = false,
            deadlineDate = todo.deadline?.date,
            deadlineTime = todo.deadline?.time,
            deadlineZone = todo.deadline?.zone,
            deadlineOffsetSeconds = todo.deadline?.offsetSeconds,
            reminderAnchorTime = todo.deadline?.reminderAnchorTime,
            deadlineAnchorEpochMs = todo.deadline?.anchorEpochMs,
            deadlineOverdueEpochMs = todo.deadline?.overdueEpochMs,
            reminderEnabled = todo.reminderRule.enabled,
            reminderLeadMinutes = todo.reminderRule.leadMinutes,
            reminderIntervalMinutes = todo.reminderRule.intervalMinutes,
            reminderCount = todo.reminderRule.count,
            scheduleToken = todo.scheduleToken,
            parentTaskId = todo.parentTaskRef?.rowId
        )

        val isCreate = existingBase == null && existingOps.none { it.kind == TaskOperationKind.CREATE.wireName }
        if (!isCreate && intentMask.isEmpty()) {
            return current ?: todo
        }
        val opId = generateUuid()
        val kind = if (isCreate) TaskOperationKind.CREATE else TaskOperationKind.UPDATE

        val outboxEntity = OutboxEntity(
            opId = opId,
            bindingKey = boundKey,
            ownerBinding = if (isUnresolved) BINDING_LOCAL_ACCOUNT else BINDING_BOUND,
            ownerUserId = if (isUnresolved) null else todo.taskRef.ownerUserId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = todo.taskRef.rowId,
            kind = kind.wireName,
            title = todo.title,
            completed = todo.completed,
            createdAt = todo.createdAt,
            localRevision = nextRev,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = System.currentTimeMillis(),
            deadlineDate = todo.deadline?.date,
            deadlineTime = todo.deadline?.time,
            deadlineZone = todo.deadline?.zone,
            deadlineOffsetSeconds = todo.deadline?.offsetSeconds,
            reminderAnchorTime = todo.deadline?.reminderAnchorTime,
            deadlineAnchorEpochMs = todo.deadline?.anchorEpochMs,
            deadlineOverdueEpochMs = todo.deadline?.overdueEpochMs,
            reminderEnabled = todo.reminderRule.enabled,
            reminderLeadMinutes = todo.reminderRule.leadMinutes,
            reminderIntervalMinutes = todo.reminderRule.intervalMinutes,
            reminderCount = todo.reminderRule.count,
            scheduleToken = todo.scheduleToken,
            payloadJson = TaskJsonMerge.encode(todo).toString(),
            intentMask = intentMask,
            parentTaskId = todo.parentTaskRef?.rowId,
            version = null,
            assumedServerVersion = null
        )

        if (copiedSourceBindingKey == null || copiedSourceRowId == null) {
            dao.saveTodoAtomic(updatedMeta, draftEntity, outboxEntity)
        } else {
            dao.copyAsNewAtomic(
                updatedMeta, draftEntity, outboxEntity,
                sourceBindingKey = copiedSourceBindingKey,
                sourceRowId = copiedSourceRowId,
                sourceTodoRevision = sourceTodoRevision,
                sourceAssignRevision = sourceAssignRevision
            )
        }
        return todo
    }

    override suspend fun deleteTodoLocally(taskRef: TaskRef): TaskMutationResult = withMutationLock {
        val meta = getOrCreateMeta()
        val isUnresolved = meta.ownerBindingState != "BOUND"
        val boundKey = resolveBindingKey(taskRef.ownerUserId, isUnresolved)

        val access = accessForLocked(taskRef)
        val canDelete = taskRef.ownerUserId == userId || access?.canDeleteTask == true
        if (!canDelete) {
            return@withMutationLock TaskMutationResult.AccessDenied
        }

        val allTodos = loadTodosLocked()
        val target = allTodos.firstOrNull { it.taskRef == taskRef }
        if (target == null || target.isDeleted) {
            return@withMutationLock TaskMutationResult.Missing
        }

        val liveChildren = allTodos.filter { it.parentTaskRef == taskRef && !it.isDeleted }

        // Case 1: Parent-group deletion
        if (liveChildren.isNotEmpty()) {
            val groupRefs = listOf(taskRef) + liveChildren.map { it.taskRef }
            val allPendingCreate = groupRefs.all { ref ->
                val b = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, ref.rowId)
                val o = dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, ref.rowId)
                isProvablyUnsentCreate(b, o)
            }
            if (allPendingCreate) {
                dao.cancelUnsentGroupAtomic(groupRefs.map { it.rowId })
                return@withMutationLock TaskMutationResult.Changed(target.copy(isDeleted = true))
            }

            var currentRev = meta.localRevisionCounter
            val drafts = mutableListOf<EditorDraftEntity>()
            val outboxes = mutableListOf<OutboxEntity>()
            val cancelledChildRowIds = mutableListOf<String>()

            currentRev = nextRevision(currentRev)
            val parentBase = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
            val (pDraft, pOutbox) = buildDeleteEntities(taskRef, null, currentRev, boundKey, isUnresolved)
            drafts.add(pDraft)
            outboxes.add(pOutbox)

            for (child in liveChildren) {
                val childBase = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, child.taskRef.rowId)
                val childOps = dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, child.taskRef.rowId)
                if (isProvablyUnsentCreate(childBase, childOps)) {
                    cancelledChildRowIds.add(child.taskRef.rowId)
                } else {
                    currentRev = nextRevision(currentRev)
                    val (cDraft, cOutbox) = buildDeleteEntities(child.taskRef, taskRef.rowId, currentRev, boundKey, isUnresolved)
                    drafts.add(cDraft)
                    outboxes.add(cOutbox)
                }
            }

            val updatedMeta = meta.copy(localRevisionCounter = currentRev)
            dao.deleteGroupAtomic(updatedMeta, drafts, outboxes, cancelledChildRowIds)
            return@withMutationLock TaskMutationResult.Changed(target.copy(isDeleted = true))
        }

        // Case 2: Last-child deletion with parent completion preservation
        val parentRef = target.parentTaskRef
        if (parentRef != null) {
            val liveSiblings = allTodos.filter { it.parentTaskRef == parentRef && it.taskRef != taskRef && !it.isDeleted }
            if (liveSiblings.isEmpty()) {
                val parent = loadTodoLocked(parentRef)
                if (parent != null && !parent.isDeleted) {
                    val precedingEffectiveCompleted = target.completed
                    var currentRev = meta.localRevisionCounter
                    val childBase = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
                    val childOps = dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
                    val isChildProvablyUnsent = isProvablyUnsentCreate(childBase, childOps)

                    val (childDraft, childOutbox) = if (isChildProvablyUnsent) {
                        Pair(null, null)
                    } else {
                        currentRev = nextRevision(currentRev)
                        buildDeleteEntities(taskRef, parentRef.rowId, currentRev, boundKey, isUnresolved)
                    }

                    val parentOps = dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, parentRef.rowId)
                    val pendingParentOp = parentOps.lastOrNull { it.deliveryState == OutboxEntity.STATE_PENDING }
                    val canCoalesceParent = pendingParentOp != null &&
                            pendingParentOp.deliveryState == OutboxEntity.STATE_PENDING &&
                            pendingParentOp.envelopeJson == null && pendingParentOp.legacyEvidenceId == null &&
                            pendingParentOp.dispatchHistory == "NEVER_DISPATCHED" &&
                            pendingParentOp.kind != TaskOperationKind.DELETE.wireName

                    val parentRev = if (canCoalesceParent) {
                        requireNotNull(pendingParentOp).localRevision
                    } else {
                        currentRev = nextRevision(currentRev)
                        currentRev
                    }

                    val (parentDraft, parentOutbox) = buildPreservationUpdateEntities(
                        parent, precedingEffectiveCompleted, parentRev, boundKey, isUnresolved, pendingParentOp
                    )

                    val updatedMeta = meta.copy(localRevisionCounter = maxOf(meta.localRevisionCounter, currentRev))
                    dao.deleteChildAndPreserveParentAtomic(
                        updatedMeta,
                        childDraft,
                        childOutbox,
                        parentDraft,
                        parentOutbox,
                        cancelledChildRowId = if (isChildProvablyUnsent) taskRef.rowId else null
                    )
                    return@withMutationLock TaskMutationResult.Changed(target.copy(isDeleted = true))
                }
            }
        }

        // Case 3: Standalone task delete
        val existingBase = dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        val existingOps = dao.getOutboxByRow(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, taskRef.rowId)
        if (isProvablyUnsentCreate(existingBase, existingOps)) {
            dao.cancelUnsentCreateAtomic(taskRef.rowId)
            return@withMutationLock TaskMutationResult.Changed(target.copy(isDeleted = true))
        }

        val nextRev = nextRevision(meta.localRevisionCounter)
        val updatedMeta = meta.copy(localRevisionCounter = nextRev)
        val (draftEntity, outboxEntity) = buildDeleteEntities(taskRef, target.parentTaskRef?.rowId, nextRev, boundKey, isUnresolved)
        dao.deleteTodoAtomic(updatedMeta, draftEntity, outboxEntity)
        TaskMutationResult.Changed(target.copy(isDeleted = true))
    }

    override suspend fun setCompletedLocally(
        taskRef: TaskRef,
        completed: Boolean,
        nowEpochMs: Long
    ): CompletionChange = withMutationLock {
        val current = loadTodoLocked(taskRef) ?: return@withMutationLock CompletionChange.Missing
        if (current.isDeleted) return@withMutationLock CompletionChange.Missing

        val access = accessForLocked(taskRef)
        val canEdit = taskRef.ownerUserId == userId || access?.canEditContent == true
        if (!canEdit) {
            return@withMutationLock CompletionChange.Missing
        }

        val allTodos = loadTodosLocked()
        val hasChildren = allTodos.any { it.parentTaskRef == taskRef && !it.isDeleted }
        if (hasChildren) {
            return@withMutationLock CompletionChange.AutomaticParentRejected
        }
        if (current.completed == completed) {
            return@withMutationLock CompletionChange.Unchanged
        }

        val updatedTodo = current.copy(
            completed = completed,
            updatedAt = maxOf(nowEpochMs, current.updatedAt, current.createdAt)
        )
        val meta = getOrCreateMeta()
        val isUnresolved = meta.ownerBindingState != "BOUND"
        val boundKey = resolveBindingKey(taskRef.ownerUserId, isUnresolved)
        saveTodoLocallyLocked(updatedTodo, boundKey = boundKey, isUnresolved = isUnresolved)
        CompletionChange.Changed
    }

    // -------------------------------------------------------------------------
    // Assignment Mutations (AccountStore)
    // -------------------------------------------------------------------------

    override suspend fun assignTaskLocally(intent: AssignmentIntent): AssignmentSlotResult = withMutationLock {
        val targetTodo = loadTodoLocked(intent.taskRef) ?: return@withMutationLock AssignmentSlotResult.Missing
        if (targetTodo.isDeleted) return@withMutationLock AssignmentSlotResult.Missing

        val access = accessForLocked(intent.taskRef)
        val canManage = intent.taskRef.ownerUserId == userId || access?.canManageAssignment == true
        if (!canManage) {
            return@withMutationLock AssignmentSlotResult.AccessDenied
        }

        val meta = getOrCreateMeta()
        val isUnresolved = meta.ownerBindingState != "BOUND"
        val boundKey = resolveBindingKey(intent.taskRef.ownerUserId, isUnresolved)

        val slotBase = dao.getServerBase(boundKey, ASSIGNMENT_TABLE_NAME, intent.taskRef.rowId)
        val slotOps = dao.getOutboxByRow(boundKey, ASSIGNMENT_TABLE_NAME, intent.taskRef.rowId)
        val latestSlotOp = slotOps.lastOrNull()

        val currentSlotVersion = latestSlotOp?.version ?: slotBase?.serverRevision ?: 0L
        if (intent.expectedSlotVersion != currentSlotVersion) {
            return@withMutationLock AssignmentSlotResult.VersionConflict(
                expectedVersion = intent.expectedSlotVersion,
                actualVersion = currentSlotVersion
            )
        }

        val currentRecipient = if (latestSlotOp != null) {
            parseRecipientFromPayload(latestSlotOp.payloadJson)
        } else {
            slotBase?.rawJson?.let { parseRecipientFromPayload(it) }
        }

        if (currentRecipient == intent.desiredRecipientUserId) {
            return@withMutationLock AssignmentSlotResult.Unchanged
        }

        val nextRev = nextRevision(meta.localRevisionCounter)
        val opId = generateUuid()
        val isDelete = intent.desiredRecipientUserId == null
        val slotPayloadJson = buildAssignmentSlotPayloadJson(intent.taskRef.rowId, intent.desiredRecipientUserId)

        val outbox = OutboxEntity(
            opId = opId,
            bindingKey = boundKey,
            ownerBinding = if (isUnresolved) BINDING_LOCAL_ACCOUNT else BINDING_BOUND,
            ownerUserId = if (isUnresolved) null else intent.taskRef.ownerUserId,
            tableName = ASSIGNMENT_TABLE_NAME,
            rowId = intent.taskRef.rowId,
            kind = if (isDelete) TaskOperationKind.DELETE.wireName else TaskOperationKind.UPDATE.wireName,
            title = null,
            completed = null,
            createdAt = System.currentTimeMillis(),
            localRevision = nextRev,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = System.currentTimeMillis(),
            payloadJson = slotPayloadJson,
            version = intent.expectedSlotVersion + 1L,
            assumedServerVersion = intent.expectedSlotVersion
        )

        dao.upsertMeta(meta.copy(localRevisionCounter = nextRev))
        dao.upsertOutbox(outbox)

        AssignmentSlotResult.Success(
            AssignmentSlotRecord(
                taskRef = intent.taskRef,
                recipientUserId = intent.desiredRecipientUserId,
                version = intent.expectedSlotVersion + 1L,
                deleted = isDelete
            )
        )
    }
    data class AssignmentSlotInfo(
        val recipientUserId: String?,
        val slotVersion: Long,
        val inheritedRecipientUserId: String? = null
    )

    suspend fun getAssignmentSlot(taskRef: TaskRef): AssignmentSlotInfo = withMutationLock {
        val meta = getOrCreateMeta()
        val isUnresolved = meta.ownerBindingState != "BOUND"
        val boundKey = resolveBindingKey(taskRef.ownerUserId, isUnresolved)

        val slotBase = dao.getServerBase(boundKey, ASSIGNMENT_TABLE_NAME, taskRef.rowId)
        val slotOps = dao.getOutboxByRow(boundKey, ASSIGNMENT_TABLE_NAME, taskRef.rowId)
        val latestSlotOp = slotOps.lastOrNull()
        val currentSlotVersion = latestSlotOp?.version ?: slotBase?.serverRevision ?: 0L
        val currentRecipient = if (latestSlotOp != null) {
            parseRecipientFromPayload(latestSlotOp.payloadJson)
        } else {
            slotBase?.rawJson?.let { parseRecipientFromPayload(it) }
        }

        val todo = loadTodoLocked(taskRef, includeDeleted = true)
        val inheritedRecipient = if (todo?.parentTaskRef != null) {
            val parentRef = todo.parentTaskRef
            val parentBoundKey = resolveBindingKey(parentRef.ownerUserId, isUnresolved)
            val parentSlotBase = dao.getServerBase(parentBoundKey, ASSIGNMENT_TABLE_NAME, parentRef.rowId)
            val parentSlotOps = dao.getOutboxByRow(parentBoundKey, ASSIGNMENT_TABLE_NAME, parentRef.rowId)
            val parentLatestOp = parentSlotOps.lastOrNull()
            if (parentLatestOp != null) {
                parseRecipientFromPayload(parentLatestOp.payloadJson)
            } else {
                parentSlotBase?.rawJson?.let { parseRecipientFromPayload(it) }
            }
        } else null

        AssignmentSlotInfo(
            recipientUserId = currentRecipient,
            slotVersion = currentSlotVersion,
            inheritedRecipientUserId = inheritedRecipient
        )
    }

    override suspend fun reconcileAccepted(opId: String, serverRevision: Long): Unit = withMutationLock {
        reconcileAcceptedLocked(opId, serverRevision)
    }

    private suspend fun reconcileAcceptedLocked(opId: String, serverRevision: Long) {
        val op = dao.getOutboxEntry(opId) ?: return
        if (op.deliveryState != OutboxEntity.STATE_DISPATCHING || op.version != serverRevision || op.envelopeJson == null) return
        val envelope = TaskJsonMerge.json.decodeFromString<PushRecordDto>(op.envelopeJson)
        if (envelope.version != serverRevision || envelope.rowId != op.rowId || envelope.tableName != op.tableName) return
        val current = dao.getServerBase(op.bindingKey, op.tableName, op.rowId)
        val acceptedBase = TaskJsonMerge.project(
            PullRecordDto(envelope.tableName, envelope.rowId, envelope.data, envelope.version,
                current?.syncedAt ?: "1970-01-01T00:00:00Z", envelope.deleted)
        ).let { projected ->
            ServerBaseEntity(
                bindingKey = op.bindingKey,
                ownerBinding = op.ownerBinding,
                ownerUserId = op.ownerUserId,
                tableName = op.tableName,
                rowId = op.rowId,
                serverRevision = envelope.version,
                deleted = envelope.deleted,
                title = projected.title,
                completed = projected.completed,
                createdAt = projected.createdAt,
                updatedAt = System.currentTimeMillis(),
                deadlineDate = projected.deadlineDate,
                deadlineTime = projected.deadlineTime,
                deadlineZone = projected.deadlineZone,
                deadlineOffsetSeconds = projected.deadlineOffsetSeconds,
                reminderAnchorTime = projected.reminderAnchorTime,
                deadlineAnchorEpochMs = projected.deadlineAnchorEpochMs,
                deadlineOverdueEpochMs = projected.deadlineOverdueEpochMs,
                reminderEnabled = projected.reminderEnabled,
                reminderLeadMinutes = projected.reminderLeadMinutes,
                reminderIntervalMinutes = projected.reminderIntervalMinutes,
                reminderCount = projected.reminderCount,
                scheduleToken = projected.scheduleToken,
                parentTaskId = if (envelope.deleted) current?.parentTaskId ?: op.parentTaskId else projected.parentTaskId,
                rawJson = envelope.data.toString(),
                syncedAt = current?.syncedAt ?: "1970-01-01T00:00:00Z",
                standardConfirmed = true
            )
        }
        dao.reconcileAcceptedAtomic(
            serverBase = if (current?.standardConfirmed == true && current.serverRevision > serverRevision) current else acceptedBase,
            opId = opId, draftRowId = op.rowId,
            clearDraft = dao.getDraft(op.bindingKey, op.tableName, op.rowId)?.localRevision == op.localRevision
        )
    }

    suspend fun recoverInterruptedDispatches(): Int = mutex.withLock {
        dao.recoverInterruptedDispatches()
    }

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

    // -------------------------------------------------------------------------
    // Entity Construction Helpers
    // -------------------------------------------------------------------------

    private fun buildDeleteEntities(
        taskRef: TaskRef,
        parentTaskId: String?,
        localRev: Long,
        boundKey: String,
        isUnresolved: Boolean
    ): Pair<EditorDraftEntity, OutboxEntity> {
        val draftEntity = EditorDraftEntity(
            bindingKey = boundKey,
            ownerBinding = if (isUnresolved) BINDING_LOCAL_ACCOUNT else BINDING_BOUND,
            ownerUserId = if (isUnresolved) null else taskRef.ownerUserId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = taskRef.rowId,
            draftTitle = "",
            draftCompleted = false,
            localRevision = localRev,
            updatedAt = System.currentTimeMillis(),
            isDeleted = true,
            parentTaskId = parentTaskId
        )

        val opId = generateUuid()
        val outboxEntity = OutboxEntity(
            opId = opId,
            bindingKey = boundKey,
            ownerBinding = if (isUnresolved) BINDING_LOCAL_ACCOUNT else BINDING_BOUND,
            ownerUserId = if (isUnresolved) null else taskRef.ownerUserId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = taskRef.rowId,
            kind = TaskOperationKind.DELETE.wireName,
            title = null,
            completed = null,
            createdAt = null,
            localRevision = localRev,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = System.currentTimeMillis(),
            intentMask = OutboxEntity.INTENT_DELETE,
            parentTaskId = parentTaskId
        )

        return Pair(draftEntity, outboxEntity)
    }

    private fun buildPreservationUpdateEntities(
        parent: Todo,
        capturedCompleted: Boolean,
        localRev: Long,
        boundKey: String,
        isUnresolved: Boolean,
        pendingParentOp: OutboxEntity? = null
    ): Pair<EditorDraftEntity, OutboxEntity> {
        val draftEntity = EditorDraftEntity(
            bindingKey = boundKey,
            ownerBinding = if (isUnresolved) BINDING_LOCAL_ACCOUNT else BINDING_BOUND,
            ownerUserId = if (isUnresolved) null else parent.taskRef.ownerUserId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = parent.taskRef.rowId,
            draftTitle = parent.title,
            draftCompleted = capturedCompleted,
            localRevision = localRev,
            updatedAt = System.currentTimeMillis(),
            isDeleted = false,
            parentTaskId = null,
            deadlineDate = parent.deadline?.date,
            deadlineTime = parent.deadline?.time,
            deadlineZone = parent.deadline?.zone,
            deadlineOffsetSeconds = parent.deadline?.offsetSeconds,
            reminderAnchorTime = parent.deadline?.reminderAnchorTime,
            deadlineAnchorEpochMs = parent.deadline?.anchorEpochMs,
            deadlineOverdueEpochMs = parent.deadline?.overdueEpochMs,
            reminderEnabled = parent.reminderRule.enabled,
            reminderLeadMinutes = parent.reminderRule.leadMinutes,
            reminderIntervalMinutes = parent.reminderRule.intervalMinutes,
            reminderCount = parent.reminderRule.count,
            scheduleToken = parent.scheduleToken
        )

        val canCoalesce = pendingParentOp != null &&
                pendingParentOp.deliveryState == OutboxEntity.STATE_PENDING &&
                pendingParentOp.envelopeJson == null && pendingParentOp.legacyEvidenceId == null &&
                pendingParentOp.dispatchHistory == "NEVER_DISPATCHED" &&
                pendingParentOp.kind != TaskOperationKind.DELETE.wireName

        val opId = if (canCoalesce) pendingParentOp.opId else generateUuid()
        val kind = if (canCoalesce) pendingParentOp.kind else TaskOperationKind.UPDATE.wireName
        val intentMask = if (canCoalesce) {
            val existing = pendingParentOp.intentMask ?: ""
            "$existing|${OutboxEntity.INTENT_COMPLETED}|${OutboxEntity.INTENT_LAST_CHILD_PRESERVATION}"
        } else {
            "${OutboxEntity.INTENT_COMPLETED}|${OutboxEntity.INTENT_LAST_CHILD_PRESERVATION}"
        }

        val outboxEntity = OutboxEntity(
            opId = opId,
            bindingKey = boundKey,
            ownerBinding = if (isUnresolved) BINDING_LOCAL_ACCOUNT else BINDING_BOUND,
            ownerUserId = if (isUnresolved) null else parent.taskRef.ownerUserId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = parent.taskRef.rowId,
            kind = kind,
            title = parent.title,
            completed = capturedCompleted,
            createdAt = parent.createdAt,
            localRevision = localRev,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = System.currentTimeMillis(),
            deadlineDate = parent.deadline?.date,
            deadlineTime = parent.deadline?.time,
            deadlineZone = parent.deadline?.zone,
            deadlineOffsetSeconds = parent.deadline?.offsetSeconds,
            reminderAnchorTime = parent.deadline?.reminderAnchorTime,
            deadlineAnchorEpochMs = parent.deadline?.anchorEpochMs,
            deadlineOverdueEpochMs = parent.deadline?.overdueEpochMs,
            reminderEnabled = parent.reminderRule.enabled,
            reminderLeadMinutes = parent.reminderRule.leadMinutes,
            reminderIntervalMinutes = parent.reminderRule.intervalMinutes,
            reminderCount = parent.reminderRule.count,
            scheduleToken = parent.scheduleToken,
            payloadJson = TaskJsonMerge.encode(parent.copy(completed = capturedCompleted, parentTaskRef = null)).toString(),
            intentMask = intentMask,
            parentTaskId = null
        )

        return Pair(draftEntity, outboxEntity)
    }

    private suspend fun getOrCreateMeta(): AccountSyncMetaEntity {
        return dao.getMeta() ?: AccountSyncMetaEntity(id = AccountSyncMetaEntity.SINGLETON_ID).also {
            dao.upsertMeta(it)
        }
    }

    private fun nextRevision(current: Long): Long {
        check(current < dev.tabbyrush.shared.sync.MAX_EXACT_VERSION) { "Local revision exhausted; work retained" }
        return current + 1L
    }

    private fun generateUuid(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private data class ParsedTaskFields(
        val title: String?,
        val completed: Boolean?,
        val createdAt: Long?,
        val parentTaskId: String?,
        val deadline: TaskDeadline?,
        val reminderRule: ReminderRule,
        val scheduleToken: String?
    )

    private fun parseJsonToTaskFields(jsonStr: String): ParsedTaskFields {
        return try {
            val element = TaskJsonMerge.json.parseToJsonElement(jsonStr)
            val obj = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content
            val completed = obj["completed"]?.jsonPrimitive?.booleanOrNull
            val createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull
            val parentTaskId = obj["parentTaskId"]?.let {
                if (it is JsonNull) null else it.jsonPrimitive.content
            }
            val dDate = obj["deadlineDate"]?.jsonPrimitive?.content
            val dTime = obj["deadlineTime"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
            val dZone = obj["deadlineZone"]?.jsonPrimitive?.content
            val dOffset = obj["deadlineOffsetSeconds"]?.jsonPrimitive?.longOrNull
            val dAnchorTime = obj["reminderAnchorTime"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
            val dAnchorEpoch = obj["deadlineAnchorEpochMs"]?.jsonPrimitive?.longOrNull
            val dOverdueEpoch = obj["deadlineOverdueEpochMs"]?.jsonPrimitive?.longOrNull

            val deadline = if (dDate != null && dZone != null && dOffset != null && dAnchorEpoch != null && dOverdueEpoch != null) {
                TaskDeadline(
                    date = dDate,
                    time = dTime,
                    zone = dZone,
                    offsetSeconds = dOffset,
                    reminderAnchorTime = dAnchorTime,
                    anchorEpochMs = dAnchorEpoch,
                    overdueEpochMs = dOverdueEpoch
                )
            } else null

            val rEnabled = obj["reminderEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
            val rLead = obj["reminderLeadMinutes"]?.jsonPrimitive?.longOrNull ?: 0L
            val rInterval = obj["reminderIntervalMinutes"]?.jsonPrimitive?.longOrNull
            val rCount = obj["reminderCount"]?.jsonPrimitive?.longOrNull ?: 1L
            val reminderRule = if (deadline != null && rEnabled) {
                ReminderRule(
                    enabled = true,
                    leadMinutes = rLead,
                    intervalMinutes = rInterval,
                    count = rCount
                )
            } else {
                ReminderRule.DISABLED
            }
            val scheduleToken = obj["scheduleToken"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

            ParsedTaskFields(title, completed, createdAt, parentTaskId, deadline, reminderRule, scheduleToken)
        } catch (_: Exception) {
            ParsedTaskFields(null, null, null, null, null, ReminderRule.DISABLED, null)
        }
    }

    private fun buildAssignmentSlotPayloadJson(taskId: String, recipientUserId: String?): String {
        return if (recipientUserId != null) {
            """{"id":"$taskId","taskId":"$taskId","userId":"$recipientUserId"}"""
        } else {
            """{"id":"$taskId","taskId":"$taskId","userId":null}"""
        }
    }

    private fun parseRecipientFromPayload(jsonStr: String): String? {
        return try {
            val element = TaskJsonMerge.json.parseToJsonElement(jsonStr)
            val obj = element.jsonObject
            val userElem = obj["userId"]
            if (userElem != null && userElem !is JsonNull) {
                userElem.jsonPrimitive.content
            } else null
        } catch (_: Exception) {
            null
        }
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
            op != null -> TemporalProjection(
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

/**
 * Factory for creating [RoomAccountStore] instances bound to an account partition.
 */
class RoomCollaborationStoreFactory(
    private val database: TabbyRushDatabase? = null,
    private val userId: String,
    private val daoOverride: TabbyRushDao? = null
) : CollaborationStoreFactory {
    override fun open(accountKey: AccountKey): CollaborationStore {
        return RoomAccountStore(accountKey = accountKey, userId = userId, database = database, daoOverride = daoOverride)
    }
}
