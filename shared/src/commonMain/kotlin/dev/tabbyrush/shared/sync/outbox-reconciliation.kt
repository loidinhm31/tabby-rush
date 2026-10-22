package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.AcknowledgedTuple
import dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME
import dev.tabbyrush.shared.contract.CanonicalRecordRef
import dev.tabbyrush.shared.contract.ConflictTuple
import dev.tabbyrush.shared.contract.RecordFailure
import dev.tabbyrush.shared.data.BINDING_LOCAL_ACCOUNT
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.boundBindingKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject

/**
 * Reconciles local outbox state machine with authoritative server responses.
 *
 * Enforces:
 * - Canonical FIFO head gating: each (ownerUserId, tableName, rowId) tuple admits ONLY its oldest active intent.
 * - Dispatch-time version selection against confirmed base.
 * - Exact-ack tuple matching on (ownerUserId, tableName, rowId, submittedVersion).
 * - Distinct handling of OPERATION_FORBIDDEN vs ACCESS_DENIED.
 * - Reconciles uncertain operations without automatic blind POST replays.
 */
class OutboxReconciliation(val store: RoomAccountStore) {
    suspend fun getEligibleDispatchBatch(maxCount: Int = 100): List<OutboxEntity> =
        store.dao.getAllOutbox()
            .groupBy { Triple(it.ownerUserId ?: store.userId, it.tableName, it.rowId) }
            .values
            .mapNotNull { rowOps ->
                val sorted = rowOps.sortedBy { it.localRevision }
                val head = sorted.firstOrNull() ?: return@mapNotNull null
                head.takeIf { it.deliveryState == OutboxEntity.STATE_PENDING && it.envelopeJson == null }
            }
            .take(maxCount)

    suspend fun prepareAndFreezeBatch(
        batch: List<OutboxEntity>,
        pull: PullRequestDto = PullRequestDto()
    ): List<OutboxEntity> = store.withMutationLock {
        val frozen = mutableListOf<OutboxEntity>()
        val emptyBytes = syncWireJson.encodeToString(DeltaRequestDto(PushRequestDto(emptyList()), pull)).encodeToByteArray().size
        var bodyBytes = emptyBytes

        for (candidate in batch) {
            val op = store.dao.getOutboxEntry(candidate.opId) ?: continue
            if (op.localRevision != candidate.localRevision || op.deliveryState != OutboxEntity.STATE_PENDING || op.envelopeJson != null) continue

            val owner = op.ownerUserId ?: store.userId
            val boundKey = boundBindingKey(owner)
            val rowOps = store.dao.getOutboxByRow(boundKey, op.tableName, op.rowId)
            val activeRowOps = if (rowOps.isEmpty()) store.dao.getOutboxByRowId(op.rowId) else rowOps
            if (activeRowOps.firstOrNull()?.opId != op.opId) continue

            val base = store.dao.getServerBase(boundKey, op.tableName, op.rowId)
                ?: store.dao.getServerBase(BINDING_LOCAL_ACCOUNT, op.tableName, op.rowId)

            if (op.tableName == ASSIGNMENT_TABLE_NAME) {
                val taskBase = store.dao.getServerBase(boundKey, "todos", op.rowId)
                    ?: store.dao.getServerBase(BINDING_LOCAL_ACCOUNT, "todos", op.rowId)
                if (taskBase != null && (!taskBase.standardConfirmed || taskBase.deleted || taskBase.projectionError != null)) {
                    store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_REVIEW, "Target task unavailable or incompatible; assignment intent retained for review")
                    continue
                }
                if (op.kind == "delete" && (base == null || base.deleted)) {
                    store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_REVIEW, "Cannot delete non-existent assignment slot")
                    continue
                }
            } else {
                if (base != null && (!base.standardConfirmed || base.deleted || base.projectionError != null)) {
                    store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_REVIEW, "Remote row unavailable or incompatible; intent retained for review")
                    continue
                }
                if (base == null && op.kind != "create") {
                    store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_REVIEW, "Missing update/delete is not a new record")
                    continue
                }
            }

            if (op.tableName == "todos" && op.parentTaskId != null && op.kind != "delete") {
                val parentBase = store.dao.getServerBase(boundKey, "todos", op.parentTaskId)
                    ?: store.dao.getServerBase(BINDING_LOCAL_ACCOUNT, "todos", op.parentTaskId)
                if (parentBase == null || !parentBase.standardConfirmed || parentBase.deleted || parentBase.projectionError != null || parentBase.parentTaskId != null) continue
                val parentOps = store.dao.getOutboxByRow(boundKey, "todos", op.parentTaskId)
                if (parentOps.any { it.kind == "delete" }) continue
            }

            try {
                val assumed = base?.serverRevision ?: 0L
                check(assumed < MAX_EXACT_VERSION) { "Record version exhausted" }
                val raw = base?.rawJson?.let { TaskJsonMerge.json.parseToJsonElement(it).jsonObject }

                val data = when {
                    op.tableName == "taskAssignees" -> {
                        if (op.kind == "delete") {
                            TaskJsonMerge.encodeRelation(op.rowId, null)
                        } else {
                            TaskJsonMerge.json.parseToJsonElement(op.payloadJson).jsonObject
                        }
                    }
                    op.kind == "delete" -> requireNotNull(raw ?: op.intentJson())
                    raw != null -> TaskJsonMerge.merge(raw, op.intentJson(), op.intentMask)
                    else -> op.intentJson()
                }

                if (op.tableName == "todos" && op.kind != "delete") {
                    TaskJsonMerge.decode(op.rowId, data)
                } else if (op.tableName == "taskAssignees" && op.kind != "delete") {
                    TaskJsonMerge.decodeRelation(op.rowId, data)
                }

                val envelope = PushRecordDto(
                    tableName = op.tableName,
                    rowId = op.rowId,
                    data = data,
                    version = assumed + 1L,
                    deleted = op.kind == "delete",
                    assumedServerVersion = assumed,
                    ownerUserId = owner
                )
                val encoded = syncWireJson.encodeToString(envelope)
                val recordBytes = encoded.encodeToByteArray().size
                if (recordBytes > MAX_SYNC_BODY_BYTES - emptyBytes) {
                    store.dao.updateDeliveryState(
                        op.opId,
                        OutboxEntity.STATE_CONFLICT,
                        "Task exceeds the 256 KiB sync limit; nothing sent. Reduce record data in a compatible client, or copy native fields as a new task"
                    )
                    continue
                }
                val addedBytes = recordBytes + if (frozen.isEmpty()) 0 else 1
                if (addedBytes > MAX_SYNC_BODY_BYTES - bodyBytes) continue

                val prepared = op.copy(
                    envelopeJson = encoded,
                    version = envelope.version,
                    assumedServerVersion = assumed,
                    deliveryState = OutboxEntity.STATE_DISPATCHING,
                    dispatchHistory = "FROZEN_BEFORE_NETWORK",
                    errorMessage = null
                )
                if (store.dao.freezeOutboxAtomic(op.opId, op.localRevision, prepared)) {
                    frozen.add(prepared)
                    bodyBytes += addedBytes
                }
            } catch (_: IllegalArgumentException) {
                store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_CONFLICT, "Task fields cannot be submitted; edit or copy retained draft")
            } catch (_: IllegalStateException) {
                store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_CONFLICT, "Record version unavailable or exhausted; intent retained")
            }
        }
        frozen
    }

    fun toDto(op: OutboxEntity): PushRecordDto = TaskJsonMerge.json.decodeFromString(requireNotNull(op.envelopeJson))

    suspend fun markBatchUncertain(batch: List<OutboxEntity>, reason: String) = store.withMutationLock {
        for (frozen in batch) {
            val current = store.dao.getOutboxEntry(frozen.opId) ?: continue
            if (current.deliveryState == OutboxEntity.STATE_DISPATCHING && current.envelopeJson == frozen.envelopeJson) {
                store.dao.upsertOutbox(
                    current.copy(
                        deliveryState = OutboxEntity.STATE_UNCERTAIN,
                        errorMessage = reason,
                        dispatchHistory = "DELIVERY_UNCERTAIN"
                    )
                )
            }
        }
    }

    suspend fun reconcilePushResults(batch: List<OutboxEntity>, response: PushResponseDto) {
        store.withMutationLock {
            val meta = store.dao.getMeta() ?: dev.tabbyrush.shared.data.AccountSyncMetaEntity()
            store.dao.upsertMeta(meta.copy(hubSchemaVersion = maxOf(meta.hubSchemaVersion ?: 0L, response.schemaVersion)))
        }

        for (op in batch) {
            val owner = op.ownerUserId ?: store.userId
            val ref = CanonicalRecordRef(ownerUserId = owner, tableName = op.tableName, rowId = op.rowId)
            val submittedVer = requireNotNull(op.version)

            val accepted = response.syncedRecords.any {
                (it.ownerUserId == null || it.ownerUserId == owner) &&
                it.tableName == op.tableName &&
                it.rowId == op.rowId &&
                it.version == submittedVer
            }
            val conflict = response.conflicts.firstOrNull {
                (it.ownerUserId == null || it.ownerUserId == owner) &&
                it.tableName == op.tableName &&
                it.rowId == op.rowId
            }
            val failed = response.failures.firstOrNull {
                (it.ownerUserId == null || it.ownerUserId == owner) &&
                it.tableName == op.tableName &&
                it.rowId == op.rowId
            }

            when {
                accepted && conflict == null && failed == null -> {
                    store.acknowledgeAccepted(listOf(AcknowledgedTuple(ref = ref, submittedVersion = submittedVer)))
                }
                conflict != null -> {
                    store.recordConflicts(
                        listOf(ConflictTuple(ref = ref, clientVersion = conflict.clientVersion, serverVersion = conflict.serverVersion))
                    )
                }
                failed != null -> {
                    store.recordFailures(listOf(RecordFailure(ref = ref, error = failed.error)))
                }
                else -> {
                    markBatchUncertain(listOf(op), "No unambiguous explicit acceptance tuple; pull before review")
                }
            }
        }
    }

    /** A matching state is convergence, never proof that this particular operation executed. */
    suspend fun reconcileUncertain() = store.withMutationLock {
        for (op in store.dao.getAllOutbox().filter { it.deliveryState == OutboxEntity.STATE_UNCERTAIN }) {
            val owner = op.ownerUserId ?: store.userId
            val boundKey = boundBindingKey(owner)
            val base = store.dao.getServerBase(boundKey, op.tableName, op.rowId)
                ?: store.dao.getServerBase(BINDING_LOCAL_ACCOUNT, op.tableName, op.rowId)
            val envelope = op.envelopeJson?.let { TaskJsonMerge.json.decodeFromString<PushRecordDto>(it) }
            val same = base?.standardConfirmed == true && base.projectionError == null && envelope != null &&
                base.serverRevision == envelope.version && base.deleted == envelope.deleted &&
                base.rawJson?.let { TaskJsonMerge.json.parseToJsonElement(it) } == envelope.data
            if (same) {
                store.dao.convergeAtomic(op.opId)
            } else {
                store.dao.updateDeliveryState(
                    op.opId,
                    OutboxEntity.STATE_REVIEW,
                    "Delivery uncertain and remote state missing or changed; explicit review required, never replayed"
                )
            }
        }
    }
}
