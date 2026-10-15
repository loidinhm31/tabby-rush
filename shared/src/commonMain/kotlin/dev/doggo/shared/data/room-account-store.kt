package dev.doggo.shared.data

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.AccountStore
import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.StrictSyncContract
import dev.doggo.shared.contract.StrictSyncDigest
import dev.doggo.shared.contract.StrictTodoData
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.TaskDeadline
import dev.doggo.shared.contract.ReminderRule
import dev.doggo.shared.contract.toStrictV2TodoData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * RoomAccountStore implements the AccountStore interface using DoggoDatabase.
 * Coordinates visible todo projections from server_base, editor_drafts, and outbox,
 * ensuring frozen FIFO mutation requests, localRevision tracking, and transactional updates.
 */
class RoomAccountStore(
    override val accountKey: AccountKey,
    val userId: String,
    val database: DoggoDatabase? = null,
    val appId: String = "doggo",
    daoOverride: DoggoDao? = null,
    val defaultContractVersion: String = OutboxEntity.CONTRACT_VERSION_V1
) : AccountStore {

    val dao: DoggoDao = daoOverride ?: requireNotNull(database) {
        "Either database or daoOverride must be provided"
    }.doggoDao()

    private val mutex = Mutex()
    @kotlin.jvm.Volatile
    var isFrozen: Boolean = false

    /**
     * Loads the current visible list of todos, projecting server_base overlaid
     * with uncommitted editor_drafts and outbox intents.
     * Orders records by createdAt DESC, then id DESC.
     */
    override suspend fun loadTodos(): List<Todo> = mutex.withLock {
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

            // Determine if the item is deleted locally
            val isDeletedLocally = when {
                draft?.isDeleted == true -> true
                latestOp != null && latestOp.kind == StrictOperationKind.DELETE.wireName -> true
                draft == null && latestOp == null && base?.deleted == true -> true
                else -> false
            }

            if (isDeletedLocally) {
                continue
            }

            // Determine title, completed, createdAt, updatedAt
            val title = draft?.draftTitle
                ?: latestOp?.title
                ?: base?.title
                ?: continue

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
                it.deliveryState in setOf(
                    OutboxEntity.STATE_PENDING,
                    OutboxEntity.STATE_DISPATCHING,
                    OutboxEntity.STATE_UNCERTAIN
                )
            }
            val isPending = hasPendingOp || (draft != null && draft.localRevision > 0L)

            // Temporal deadline & reminder projections
            val deadlineDate = draft?.deadlineDate ?: latestOp?.deadlineDate ?: base?.deadlineDate
            val deadlineTime = draft?.deadlineTime ?: latestOp?.deadlineTime ?: base?.deadlineTime
            val deadlineZone = draft?.deadlineZone ?: latestOp?.deadlineZone ?: base?.deadlineZone
            val deadlineOffsetSeconds = draft?.deadlineOffsetSeconds ?: latestOp?.deadlineOffsetSeconds ?: base?.deadlineOffsetSeconds
            val reminderAnchorTime = draft?.reminderAnchorTime ?: latestOp?.reminderAnchorTime ?: base?.reminderAnchorTime
            val deadlineAnchorEpochMs = draft?.deadlineAnchorEpochMs ?: latestOp?.deadlineAnchorEpochMs ?: base?.deadlineAnchorEpochMs
            val deadlineOverdueEpochMs = draft?.deadlineOverdueEpochMs ?: latestOp?.deadlineOverdueEpochMs ?: base?.deadlineOverdueEpochMs

            val reminderEnabled = draft?.reminderEnabled ?: latestOp?.reminderEnabled ?: base?.reminderEnabled ?: false
            val reminderLeadMinutes = draft?.reminderLeadMinutes ?: latestOp?.reminderLeadMinutes ?: base?.reminderLeadMinutes ?: 0L
            val reminderIntervalMinutes = draft?.reminderIntervalMinutes ?: latestOp?.reminderIntervalMinutes ?: base?.reminderIntervalMinutes
            val reminderCount = draft?.reminderCount ?: latestOp?.reminderCount ?: base?.reminderCount ?: 1L

            val rawToken = draft?.scheduleToken ?: latestOp?.scheduleToken ?: base?.scheduleToken

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
            } else {
                ReminderRule.DISABLED
            }

            val scheduleToken = if (deadline != null) rawToken else null

            result.add(
                Todo(
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
            )
        }

        // Sort UI order: createdAt desc then id desc
        return result.sortedWith(
            compareByDescending<Todo> { it.createdAt }
                .thenByDescending { it.id }
        )
    }

    /**
     * Saves a todo locally with atomic draft and outbox persistence before returning success.
     * Enforces tombstone non-mutation, account contract version guard, and semantic scheduleToken rotation.
     */
    override suspend fun saveTodoLocally(todo: Todo): Unit = mutex.withLock {
        check(!isFrozen) { "Cannot mutate store while account leave is in progress" }

        val existingBase = dao.getServerBase(todo.id)
        if (existingBase != null && existingBase.deleted) {
            throw IllegalStateException("Cannot mutate permanently deleted tombstone with rowId: ${todo.id}")
        }

        val opContractVersion = defaultContractVersion
        if (opContractVersion == OutboxEntity.CONTRACT_VERSION_V1 && todo.deadline != null) {
            throw IllegalArgumentException("Task deadlines and reminders require strict-v2 account contract")
        }

        val meta = getOrCreateMeta()
        val nextRev = meta.localRevisionCounter + 1L
        val updatedMeta = meta.copy(localRevisionCounter = nextRev)
        val activeEpoch = meta.activeEpoch ?: ""

        val existingDraft = dao.getDraft(todo.id)
        val existingOps = dao.getOutboxByRowId(todo.id)
        val latestExistingOp = existingOps.lastOrNull()

        // Calculate schedule token rotation
        val existingDeadlineDate = existingDraft?.deadlineDate ?: latestExistingOp?.deadlineDate ?: existingBase?.deadlineDate
        val existingDeadlineZone = existingDraft?.deadlineZone ?: latestExistingOp?.deadlineZone ?: existingBase?.deadlineZone
        val existingOffset = existingDraft?.deadlineOffsetSeconds ?: latestExistingOp?.deadlineOffsetSeconds ?: existingBase?.deadlineOffsetSeconds
        val existingAnchor = existingDraft?.deadlineAnchorEpochMs ?: latestExistingOp?.deadlineAnchorEpochMs ?: existingBase?.deadlineAnchorEpochMs
        val existingOverdue = existingDraft?.deadlineOverdueEpochMs ?: latestExistingOp?.deadlineOverdueEpochMs ?: existingBase?.deadlineOverdueEpochMs

        val existingDeadline = if (existingDeadlineDate != null && existingDeadlineZone != null && existingOffset != null && existingAnchor != null && existingOverdue != null) {
            TaskDeadline(
                date = existingDeadlineDate,
                time = existingDraft?.deadlineTime ?: latestExistingOp?.deadlineTime ?: existingBase?.deadlineTime,
                zone = existingDeadlineZone,
                offsetSeconds = existingOffset,
                reminderAnchorTime = existingDraft?.reminderAnchorTime ?: latestExistingOp?.reminderAnchorTime ?: existingBase?.reminderAnchorTime,
                anchorEpochMs = existingAnchor,
                overdueEpochMs = existingOverdue
            )
        } else null

        val existingRule = if (existingDeadline != null) {
            ReminderRule(
                enabled = existingDraft?.reminderEnabled ?: latestExistingOp?.reminderEnabled ?: existingBase?.reminderEnabled ?: false,
                leadMinutes = existingDraft?.reminderLeadMinutes ?: latestExistingOp?.reminderLeadMinutes ?: existingBase?.reminderLeadMinutes ?: 0L,
                intervalMinutes = existingDraft?.reminderIntervalMinutes ?: latestExistingOp?.reminderIntervalMinutes ?: existingBase?.reminderIntervalMinutes,
                count = existingDraft?.reminderCount ?: latestExistingOp?.reminderCount ?: existingBase?.reminderCount ?: 1L
            )
        } else {
            ReminderRule.DISABLED
        }

        val existingToken = existingDraft?.scheduleToken ?: latestExistingOp?.scheduleToken ?: existingBase?.scheduleToken

        val finalScheduleToken: String? = if (todo.deadline == null) {
            null
        } else if (existingDeadline == null || existingToken == null) {
            todo.scheduleToken ?: generateUuid()
        } else {
            val changed = dev.doggo.shared.contract.hasScheduleSemanticChange(
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

        if (canCoalesce && pendingOp != null) {
            opId = pendingOp.opId
            kind = StrictOperationKind.fromWireName(pendingOp.kind) ?: StrictOperationKind.UPDATE
            expectedRev = pendingOp.expectedRevision
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
            dev.doggo.shared.contract.StrictSyncV2Digest.computeDigest(
                domain = dev.doggo.shared.contract.StrictSyncV2Contract.PROTOCOL_VERSION,
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

    /**
     * Deletes a todo locally. Unsent un-migrated create-delete cancels locally without wire dispatch.
     * Otherwise enqueues a frozen delete operation with canonical digest.
     */
    override suspend fun deleteTodoLocally(id: String): Unit = mutex.withLock {
        check(!isFrozen) { "Cannot mutate store while account leave is in progress" }

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
            return
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
        val opContractVersion = defaultContractVersion

        val digest = if (opContractVersion == OutboxEntity.CONTRACT_VERSION_V2) {
            dev.doggo.shared.contract.StrictSyncV2Digest.computeDigest(
                domain = dev.doggo.shared.contract.StrictSyncV2Contract.PROTOCOL_VERSION,
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
    override suspend fun reconcileAccepted(opId: String, serverRevision: Long): Unit = mutex.withLock {
        val op = dao.getOutboxEntry(opId) ?: return
        val currentBase = dao.getServerBase(op.rowId)

        val currentDraft = dao.getDraft(op.rowId)
        val clearDraft = currentDraft != null && currentDraft.localRevision <= op.localRevision

        // Monotonic check: Never regress confirmed server base (W1, W-E)
        if (currentBase != null && serverRevision < currentBase.serverRevision) {
            dao.purgeStaleOutboxAtomic(opId, op.rowId, clearDraft)
            return
        }
        val isV1Op = op.contractVersion == OutboxEntity.CONTRACT_VERSION_V1
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
            ServerBaseEntity(
                rowId = op.rowId,
                serverRevision = serverRevision,
                deleted = false,
                title = op.title ?: "",
                completed = op.completed ?: false,
                createdAt = op.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                epoch = op.epoch,
                deadlineDate = if (isV1Op && currentBase != null) currentBase.deadlineDate else op.deadlineDate,
                deadlineTime = if (isV1Op && currentBase != null) currentBase.deadlineTime else op.deadlineTime,
                deadlineZone = if (isV1Op && currentBase != null) currentBase.deadlineZone else op.deadlineZone,
                deadlineOffsetSeconds = if (isV1Op && currentBase != null) currentBase.deadlineOffsetSeconds else op.deadlineOffsetSeconds,
                reminderAnchorTime = if (isV1Op && currentBase != null) currentBase.reminderAnchorTime else op.reminderAnchorTime,
                deadlineAnchorEpochMs = if (isV1Op && currentBase != null) currentBase.deadlineAnchorEpochMs else op.deadlineAnchorEpochMs,
                deadlineOverdueEpochMs = if (isV1Op && currentBase != null) currentBase.deadlineOverdueEpochMs else op.deadlineOverdueEpochMs,
                reminderEnabled = if (isV1Op && currentBase != null) currentBase.reminderEnabled else op.reminderEnabled,
                reminderLeadMinutes = if (isV1Op && currentBase != null) currentBase.reminderLeadMinutes else op.reminderLeadMinutes,
                reminderIntervalMinutes = if (isV1Op && currentBase != null) currentBase.reminderIntervalMinutes else op.reminderIntervalMinutes,
                reminderCount = if (isV1Op && currentBase != null) currentBase.reminderCount else op.reminderCount,
                scheduleToken = if (isV1Op && currentBase != null) currentBase.scheduleToken else op.scheduleToken
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
    override suspend fun wipeLocalData(): Unit = mutex.withLock {
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

    private fun generateUuid(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte() // UUID v4
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte() // RFC 4122 variant
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }
}
