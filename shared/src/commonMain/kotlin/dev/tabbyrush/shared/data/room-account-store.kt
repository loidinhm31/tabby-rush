package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AccountStore
import dev.tabbyrush.shared.contract.TaskOperationKind
import dev.tabbyrush.shared.contract.TaskDataContract
import dev.tabbyrush.shared.sync.TaskJsonMerge
import dev.tabbyrush.shared.sync.PushRecordDto
import dev.tabbyrush.shared.sync.PullRecordDto
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.ReminderRule
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
    daoOverride: TabbyRushDao? = null
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

    private fun isProvablyUnsentCreate(base: ServerBaseEntity?, ops: List<OutboxEntity>): Boolean {
        return base == null &&
                ops.isNotEmpty() &&
                ops.all {
                    it.deliveryState == OutboxEntity.STATE_PENDING &&
                            it.envelopeJson == null && it.legacyEvidenceId == null && it.dispatchHistory == "NEVER_DISPATCHED" &&
                            it.kind == TaskOperationKind.CREATE.wireName
                }
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
        latestOp: OutboxEntity?,
        includeDeleted: Boolean = false
    ): Todo? {
        val isDeletedLocally = when {
            draft?.isDeleted == true -> true
            latestOp != null && latestOp.kind == TaskOperationKind.DELETE.wireName -> true
            draft == null && latestOp == null && base?.deleted == true -> true
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

        return Todo(
            id = rowId,
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
            parentTaskId = parentTaskId
        )
    }

    private suspend fun loadTodoLocked(id: String, includeDeleted: Boolean = false): Todo? {
        val base = dao.getServerBase(id)
        val draft = dao.getDraft(id)
        val rowOps = dao.getOutboxByRowId(id)
        val latestOp = rowOps.lastOrNull()
        return projectSingleTodo(id, base, draft, rowOps, latestOp, includeDeleted)
    }

    private suspend fun loadTodosLocked(includeDeleted: Boolean = false): List<Todo> {
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
            val todo = projectSingleTodo(rowId, base, draft, rowOps, latestOp, includeDeleted)
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
        val snapshot = loadTodosLocked(includeDeleted = true)
        val deletedIds = snapshot.filter { it.isDeleted }.mapTo(mutableSetOf()) { it.id }
        snapshot.filter { !it.isDeleted && it.parentTaskId !in deletedIds }
    }

    suspend fun loadAllTodos(includeDeleted: Boolean = true): List<Todo> = mutex.withLock {
        loadTodosLocked(includeDeleted = includeDeleted)
    }

    /**
     * Saves a todo locally with atomic draft and outbox persistence before returning success.
     * Enforces deleted identity suppression, hierarchy invariants, and semantic scheduleToken rotation.
     */
    private suspend fun saveTodoLocallyLocked(todo: Todo, copiedSource: Pair<String, Long>? = null) {

        val existingBase = dao.getServerBase(todo.id)
        if (existingBase != null && existingBase.deleted) {
            throw IllegalStateException("Cannot mutate a deleted or missing identity with rowId: ${todo.id}")
        }

        val meta = getOrCreateMeta()

        val nextRev = nextRevision(meta.localRevisionCounter)
        val updatedMeta = meta.copy(localRevisionCounter = nextRev)
        val existingDraft = dao.getDraft(todo.id)
        val existingOps = dao.getOutboxByRowId(todo.id)
        val latestExistingOp = existingOps.lastOrNull()
        val precedingTodo = loadTodoLocked(todo.id)

        val existingParentTaskId = when {
            existingDraft != null -> existingDraft.parentTaskId
            latestExistingOp != null -> latestExistingOp.parentTaskId
            existingBase != null -> existingBase.parentTaskId
            else -> null
        }
        val isExistingTask = existingBase != null || existingDraft != null || existingOps.isNotEmpty()
        if (isExistingTask) {
            if (existingParentTaskId == null && todo.parentTaskId != null) {
                throw IllegalArgumentException("Cannot convert existing root task into a child task")
            }
            if (existingParentTaskId != null && todo.parentTaskId != existingParentTaskId) {
                throw IllegalArgumentException("Cannot reparent or detach child task")
            }
        }

        if (todo.parentTaskId != null) {
            val parentTodo = loadTodoLocked(todo.parentTaskId)
            requireNotNull(parentTodo) { "Parent task ${todo.parentTaskId} not found" }
            require(parentTodo.parentTaskId == null) { "Parent task ${todo.parentTaskId} cannot be a child (max 2 levels)" }
            require(!parentTodo.isDeleted) { "Parent task ${todo.parentTaskId} is deleted" }
        }

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
        val allTodos = loadTodosLocked()
        val hasLiveChildren = allTodos.any { it.parentTaskId == todo.id && !it.isDeleted }
        val preservedCompleted = if (hasLiveChildren) {
            existingDraft?.draftCompleted ?: latestExistingOp?.completed ?: existingBase?.completed ?: todo.completed
        } else {
            todo.completed
        }

        val validatedTodo = todo.copy(
            completed = preservedCompleted,
            scheduleToken = finalScheduleToken
        )
        // Coalescing policy: only never-dispatched newly submitted pending ops (not historical/migrated)
        val pendingOp = existingOps.lastOrNull { it.deliveryState == OutboxEntity.STATE_PENDING }
        val canCoalesce = pendingOp != null &&
                pendingOp.envelopeJson == null && pendingOp.legacyEvidenceId == null && pendingOp.dispatchHistory == "NEVER_DISPATCHED" &&
                pendingOp.deliveryState == OutboxEntity.STATE_PENDING &&
                pendingOp.kind != TaskOperationKind.DELETE.wireName

        val kind = if (existingBase == null && (existingOps.isEmpty() || (canCoalesce && pendingOp?.kind == "create"))) TaskOperationKind.CREATE else TaskOperationKind.UPDATE
        val opId = if (canCoalesce) requireNotNull(pendingOp).opId else generateUuid()

        val masks = linkedSetOf<String>()
        if (canCoalesce) masks.addAll(requireNotNull(pendingOp?.intentMask).split('|'))
        if (kind == TaskOperationKind.CREATE || precedingTodo?.title != validatedTodo.title) masks.add(OutboxEntity.INTENT_TITLE)
        if (kind == TaskOperationKind.CREATE || precedingTodo?.completed != validatedTodo.completed) masks.add(OutboxEntity.INTENT_COMPLETED)
        if (kind == TaskOperationKind.CREATE || precedingTodo?.deadline != validatedTodo.deadline) masks.add(OutboxEntity.INTENT_DEADLINE)
        if (kind == TaskOperationKind.CREATE || precedingTodo?.reminderRule != validatedTodo.reminderRule) masks.add(OutboxEntity.INTENT_REMINDER)
        if (kind == TaskOperationKind.CREATE || precedingTodo?.parentTaskId != validatedTodo.parentTaskId) masks.add(OutboxEntity.INTENT_PARENT)
        if (masks.isEmpty()) return
        val intentMask = masks.joinToString("|")

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
            scheduleToken = finalScheduleToken,
            parentTaskId = validatedTodo.parentTaskId
        )

        val outboxEntity = OutboxEntity(
            opId = opId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = validatedTodo.id,
            kind = kind.wireName,
            title = validatedTodo.title,
            completed = validatedTodo.completed,
            createdAt = validatedTodo.createdAt,
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
            payloadJson = TaskJsonMerge.encode(validatedTodo).toString(),
            intentMask = intentMask,
            parentTaskId = validatedTodo.parentTaskId
        )

        // Atomic write (C1)
        if (copiedSource == null) dao.saveTodoAtomic(updatedMeta, draftEntity, outboxEntity)
        else dao.copyAsNewAtomic(updatedMeta, draftEntity, outboxEntity, copiedSource.first, copiedSource.second)
    }

    override suspend fun saveTodoLocally(todo: Todo): Unit = withMutationLock {
        saveTodoLocallyLocked(todo)
    }

    /** Uses the same latest draft/outbox projection as conflict presentation, under the mutation lock. */
    suspend fun copyLatestConflictAsNew(rowId: String): Unit = withMutationLock {
        val ops = dao.getOutboxByRowId(rowId)
        val head = ops.firstOrNull() ?: return@withMutationLock
        if (head.deliveryState != OutboxEntity.STATE_CONFLICT && head.deliveryState != OutboxEntity.STATE_REVIEW) return@withMutationLock
        val latest = loadTodoLocked(rowId, includeDeleted = true) ?: return@withMutationLock
        val representedRevision = maxOf(ops.maxOf { it.localRevision }, dao.getDraft(rowId)?.localRevision ?: 0L)
        val now = System.currentTimeMillis()
        saveTodoLocallyLocked(latest.copy(id = generateUuid(), parentTaskId = null, createdAt = now,
            updatedAt = now, serverRevision = 0L, isDeleted = false, isPending = false,
            scheduleToken = if (latest.deadline != null) generateUuid() else null), rowId to representedRevision)
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
        val allTodos = loadTodosLocked()
        val hasChildren = allTodos.any { it.parentTaskId == id && !it.isDeleted }
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
        saveTodoLocallyLocked(updatedTodo)
        CompletionChange.Changed
    }

    private fun buildDeleteEntities(
        rowId: String,
        parentTaskId: String?,
        localRev: Long,
        existingBase: ServerBaseEntity?
    ): Pair<EditorDraftEntity, OutboxEntity> {
        val draftEntity = EditorDraftEntity(
            rowId = rowId,
            draftTitle = "",
            draftCompleted = false,
            localRevision = localRev,
            updatedAt = System.currentTimeMillis(),
            isDeleted = true,
            parentTaskId = parentTaskId
        )

        val opId = generateUuid()
        val kind = TaskOperationKind.DELETE

        val outboxEntity = OutboxEntity(
            opId = opId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = rowId,
            kind = kind.wireName,
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
        parentBase: ServerBaseEntity?,
        capturedCompleted: Boolean,
        localRev: Long,
        pendingParentOp: OutboxEntity? = null
    ): Pair<EditorDraftEntity, OutboxEntity> {
        val draftEntity = EditorDraftEntity(
            rowId = parent.id,
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
                pendingParentOp.envelopeJson == null && pendingParentOp.legacyEvidenceId == null && pendingParentOp.dispatchHistory == "NEVER_DISPATCHED" &&
                pendingParentOp.kind != TaskOperationKind.DELETE.wireName

        val opId = if (canCoalesce) requireNotNull(pendingParentOp).opId else generateUuid()
        val kind = if (parentBase == null && pendingParentOp?.kind != "update") TaskOperationKind.CREATE else TaskOperationKind.UPDATE

        val intentMask = if (kind == TaskOperationKind.CREATE) {
            val parts = mutableListOf(OutboxEntity.INTENT_TITLE, OutboxEntity.INTENT_COMPLETED)
            if (parent.deadline != null) {
                parts.add(OutboxEntity.INTENT_DEADLINE)
                if (parent.reminderRule.enabled) parts.add(OutboxEntity.INTENT_REMINDER)
            }
            if (pendingParentOp?.intentMask?.contains(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION) == true) {
                parts.add(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION)
            }
            parts.joinToString("|")
        } else {
            val masks = mutableListOf<String>()
            if (canCoalesce) {
                val existingMasks = pendingParentOp?.intentMask?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
                masks.addAll(existingMasks)
            }
            if (!masks.contains(OutboxEntity.INTENT_COMPLETED)) {
                masks.add(OutboxEntity.INTENT_COMPLETED)
            }
            if (!masks.contains(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION)) {
                masks.add(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION)
            }
            masks.joinToString("|")
        }

        val outboxEntity = OutboxEntity(
            opId = opId,
            tableName = TaskDataContract.DEFAULT_TABLE_NAME,
            rowId = parent.id,
            kind = kind.wireName,
            title = parent.title,
            completed = capturedCompleted,
            createdAt = parent.createdAt,
            localRevision = localRev,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = pendingParentOp?.createdAtEpochMs ?: System.currentTimeMillis(),
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
            payloadJson = TaskJsonMerge.encode(parent.copy(completed = capturedCompleted, parentTaskId = null)).toString(),
            intentMask = intentMask,
            parentTaskId = null
        )
        return Pair(draftEntity, outboxEntity)
    }

    /**
     * Deletes a todo locally.
     * Enforces group deletion when deleting a parent with live children,
     * and locally observed last-child deletion with parent effective completion capture.
     */
    override suspend fun deleteTodoLocally(id: String): Unit = withMutationLock {
        val existingBase = dao.getServerBase(id)
        val existingOps = dao.getOutboxByRowId(id)
        val existingDraft = dao.getDraft(id)
        val latestOp = existingOps.lastOrNull()
        val existingParentTaskId = when {
            existingDraft != null -> existingDraft.parentTaskId
            latestOp != null -> latestOp.parentTaskId
            existingBase != null -> existingBase.parentTaskId
            else -> null
        }

        val allTodos = loadTodosLocked()
        val target = allTodos.firstOrNull { it.id == id }
        if (target == null || target.isDeleted) {
            return@withMutationLock
        }
        val liveChildren = allTodos.filter { it.parentTaskId == id && !it.isDeleted }

        // Case 1: Confirmed parent-group deletion
        if (liveChildren.isNotEmpty()) {
            val groupIds = listOf(id) + liveChildren.map { it.id }
            val allPendingCreate = groupIds.all { rowId ->
                isProvablyUnsentCreate(dao.getServerBase(rowId), dao.getOutboxByRowId(rowId))
            }
            if (allPendingCreate) {
                dao.cancelUnsentGroupAtomic(groupIds)
                return@withMutationLock
            }

            val meta = getOrCreateMeta()
            var currentRev = meta.localRevisionCounter

            val drafts = mutableListOf<EditorDraftEntity>()
            val outboxes = mutableListOf<OutboxEntity>()
            val cancelledChildRowIds = mutableListOf<String>()

            // Parent delete
            currentRev = nextRevision(currentRev)
            val parentBase = dao.getServerBase(id)
            val (pDraft, pOutbox) = buildDeleteEntities(id, null, currentRev, parentBase)
            drafts.add(pDraft)
            outboxes.add(pOutbox)

            // Children delete or cancel unsent leaf children
            for (child in liveChildren) {
                val childBase = dao.getServerBase(child.id)
                val childOps = dao.getOutboxByRowId(child.id)
                val isProvablyUnsentLeaf = isProvablyUnsentCreate(childBase, childOps)
                if (isProvablyUnsentLeaf) {
                    cancelledChildRowIds.add(child.id)
                } else {
                    currentRev = nextRevision(currentRev)
                    val (cDraft, cOutbox) = buildDeleteEntities(child.id, id, currentRev, childBase)
                    drafts.add(cDraft)
                    outboxes.add(cOutbox)
                }
            }

            val updatedMeta = meta.copy(localRevisionCounter = currentRev)
            dao.deleteGroupAtomic(updatedMeta, drafts, outboxes, cancelledChildRowIds)
            return@withMutationLock
        }

        // Case 2: Locally observed last-child deletion
        val parentId = existingParentTaskId ?: target.parentTaskId
        if (parentId != null) {
            val liveSiblings = allTodos.filter { it.parentTaskId == parentId && it.id != id && !it.isDeleted }
            val isLastLiveChild = liveSiblings.isEmpty()

            if (isLastLiveChild) {
                val parent = loadTodoLocked(parentId)
                if (parent != null && !parent.isDeleted) {
                    val precedingEffectiveCompleted = target.completed

                    val meta = getOrCreateMeta()
                    var currentRev = meta.localRevisionCounter
                    val childBase = dao.getServerBase(id)
                    val parentBase = dao.getServerBase(parentId)

                    val childOps = dao.getOutboxByRowId(id)
                    val isChildProvablyUnsent = isProvablyUnsentCreate(childBase, childOps)

                    val (childDraft, childOutbox) = if (isChildProvablyUnsent) {
                        Pair(null, null)
                    } else {
                        currentRev = nextRevision(currentRev)
                        buildDeleteEntities(
                            id, parentId, currentRev, childBase
                        )
                    }

                    val parentOps = dao.getOutboxByRowId(parentId)
                    val pendingParentOp = parentOps.lastOrNull { it.deliveryState == OutboxEntity.STATE_PENDING }
                    val canCoalesceParent = pendingParentOp != null &&
                            pendingParentOp.deliveryState == OutboxEntity.STATE_PENDING &&
                            pendingParentOp.envelopeJson == null && pendingParentOp.legacyEvidenceId == null &&
                            pendingParentOp.kind != TaskOperationKind.DELETE.wireName

                    val parentRev = if (canCoalesceParent) {
                        requireNotNull(pendingParentOp).localRevision
                    } else {
                        currentRev = nextRevision(currentRev)
                        currentRev
                    }

                    val (parentDraft, parentOutbox) = buildPreservationUpdateEntities(
                        parent, parentBase, precedingEffectiveCompleted, parentRev, pendingParentOp
                    )

                    val updatedMeta = meta.copy(localRevisionCounter = maxOf(meta.localRevisionCounter, currentRev))
                    dao.deleteChildAndPreserveParentAtomic(
                        updatedMeta,
                        childDraft,
                        childOutbox,
                        parentDraft,
                        parentOutbox,
                        cancelledChildRowId = if (isChildProvablyUnsent) id else null
                    )
                    return@withMutationLock
                }
            }
        }

        // Case 3: Single standalone task or non-last child
        val hasOnlyPendingCreate = isProvablyUnsentCreate(existingBase, existingOps)
        if (hasOnlyPendingCreate) {
            dao.cancelUnsentCreateAtomic(id)
            return@withMutationLock
        }

        val meta = getOrCreateMeta()
        val nextRev = nextRevision(meta.localRevisionCounter)
        val updatedMeta = meta.copy(localRevisionCounter = nextRev)

        val (draftEntity, outboxEntity) = buildDeleteEntities(
            id, existingParentTaskId, nextRev, existingBase
        )

        dao.deleteTodoAtomic(updatedMeta, draftEntity, outboxEntity)
    }


    /**
     * Reconciles an accepted operation with the server base atomically.
     * Enforces monotonic base advancement and preserves newer drafts.
     */
    override suspend fun reconcileAccepted(opId: String, serverRevision: Long): Unit = withMutationLock {
        val op = dao.getOutboxEntry(opId) ?: return@withMutationLock
        if (op.deliveryState != OutboxEntity.STATE_DISPATCHING || op.version != serverRevision || op.envelopeJson == null) return@withMutationLock
        val envelope = TaskJsonMerge.json.decodeFromString<PushRecordDto>(op.envelopeJson)
        if (envelope.version != serverRevision || envelope.rowId != op.rowId || envelope.tableName != op.tableName) return@withMutationLock
        val current = dao.getServerBase(op.rowId)
        val acceptedBase = TaskJsonMerge.project(PullRecordDto(envelope.tableName, envelope.rowId, envelope.data, envelope.version,
            current?.syncedAt ?: "1970-01-01T00:00:00Z", envelope.deleted)).let { projected ->
                if (envelope.deleted) projected.copy(parentTaskId = current?.parentTaskId ?: op.parentTaskId) else projected
            }
        dao.reconcileAcceptedAtomic(
            serverBase = if (current?.standardConfirmed == true && current.serverRevision > serverRevision) current else acceptedBase,
            opId = opId, draftRowId = op.rowId,
            clearDraft = dao.getDraft(op.rowId)?.localRevision == op.localRevision
        )
    }

    /**
     * Recovers any operations left in DISPATCHING state by resetting them to UNCERTAIN.
     */
    suspend fun recoverInterruptedDispatches(): Int = mutex.withLock {
        dao.recoverInterruptedDispatches()
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
