package dev.doggo.shared.data

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.AccountStore
import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.StrictSyncContract
import dev.doggo.shared.contract.StrictSyncDigest
import dev.doggo.shared.contract.StrictTodoData
import dev.doggo.shared.contract.Todo
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
    daoOverride: DoggoDao? = null
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

            result.add(
                Todo(
                    id = rowId,
                    title = title,
                    completed = completed,
                    createdAt = createdAt,
                    updatedAt = if (updatedAt >= createdAt) updatedAt else createdAt,
                    serverRevision = serverRevision,
                    isPending = isPending,
                    isDeleted = false
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
     * Coalesces only never-dispatched intents. Unsent create-delete cancels locally.
     */
    override suspend fun saveTodoLocally(todo: Todo): Unit = mutex.withLock {
        check(!isFrozen) { "Cannot mutate store while account leave is in progress" }

        val meta = getOrCreateMeta()
        val nextRev = meta.localRevisionCounter + 1L
        val updatedMeta = meta.copy(localRevisionCounter = nextRev)

        val activeEpoch = meta.activeEpoch ?: ""

        // Draft entity
        val draftEntity = EditorDraftEntity(
            rowId = todo.id,
            draftTitle = todo.title,
            draftCompleted = todo.completed,
            localRevision = nextRev,
            updatedAt = todo.updatedAt,
            isDeleted = false
        )

        val existingBase = dao.getServerBase(todo.id)
        val existingOps = dao.getOutboxByRowId(todo.id)
        val pendingOp = existingOps.lastOrNull { it.deliveryState == OutboxEntity.STATE_PENDING }

        val kind: StrictOperationKind
        val expectedRev: Long
        val opId: String

        if (pendingOp != null) {
            // Coalesce with existing never-dispatched operation
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

        val todoData = StrictTodoData(
            id = todo.id,
            title = todo.title,
            completed = todo.completed,
            createdAt = todo.createdAt
        )

        val digest = StrictSyncDigest.computeDigest(
            domain = StrictSyncContract.PROTOCOL_VERSION,
            appId = appId,
            userId = userId,
            epoch = activeEpoch,
            tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
            rowId = todo.id,
            opId = opId,
            kind = kind,
            expectedRevision = expectedRev,
            data = todoData
        )

        val outboxEntity = OutboxEntity(
            opId = opId,
            epoch = activeEpoch,
            tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
            rowId = todo.id,
            kind = kind.wireName,
            expectedRevision = expectedRev,
            title = todo.title,
            completed = todo.completed,
            createdAt = todo.createdAt,
            digest = digest,
            localRevision = nextRev,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = todo.updatedAt
        )

        // Atomic write (C1)
        dao.saveTodoAtomic(updatedMeta, draftEntity, outboxEntity)
    }

    /**
     * Deletes a todo locally. Unsent create-delete cancels locally without wire dispatch.
     * Otherwise enqueues a frozen delete operation with canonical digest.
     */
    override suspend fun deleteTodoLocally(id: String): Unit = mutex.withLock {
        check(!isFrozen) { "Cannot mutate store while account leave is in progress" }

        val existingBase = dao.getServerBase(id)
        val existingOps = dao.getOutboxByRowId(id)

        // If never committed to server and has only unsent PENDING create, cancel locally:
        val hasOnlyPendingCreate = (existingBase == null || existingBase.deleted) &&
                existingOps.all { it.deliveryState == OutboxEntity.STATE_PENDING }

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

        val digest = StrictSyncDigest.computeDigest(
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
            createdAtEpochMs = System.currentTimeMillis()
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
            // Stale ack arrived after a newer ack was already applied; purge outbox without regressing base
            if (clearDraft) {
                dao.deleteDraft(op.rowId)
            }
            dao.deleteOutbox(opId)
            return
        }
        val updatedBase = if (op.kind == StrictOperationKind.DELETE.wireName) {
            ServerBaseEntity(
                rowId = op.rowId,
                serverRevision = serverRevision,
                deleted = true,
                title = "",
                completed = false,
                createdAt = op.createdAt ?: 0L,
                updatedAt = System.currentTimeMillis(),
                epoch = op.epoch
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
                epoch = op.epoch
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
     * Recovers any operations left in DISPATCHING state (e.g. from app restart or cancellation)
     * by resetting them to UNCERTAIN so they can be re-dispatched or re-evaluated.
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
     * Wipes all local database tables in the partition.
     */
    override suspend fun wipeLocalData(): Unit = mutex.withLock {
        dao.clearDrafts()
        dao.clearOutbox()
        dao.clearStaging()
        dao.clearServerBase()
        dao.clearMeta()
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
