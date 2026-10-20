package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject

/** No strict receipts or automatic uncertain retries. Each row admits only its oldest active intent. */
class OutboxReconciliation(val store: RoomAccountStore) {
    suspend fun getEligibleDispatchBatch(maxCount: Int = 100): List<OutboxEntity> =
        store.dao.getAllOutbox().groupBy { it.tableName to it.rowId }.values.mapNotNull { row ->
            row.firstOrNull()?.takeIf { it.deliveryState == OutboxEntity.STATE_PENDING && it.envelopeJson == null }
        }.take(maxCount)

    suspend fun prepareAndFreezeBatch(
        batch: List<OutboxEntity>,
        pull: PullRequestDto = PullRequestDto()
    ): List<OutboxEntity> = store.withMutationLock {
        val frozen = mutableListOf<OutboxEntity>()
        // Exact wire codec and captured pull: UTF-8 bytes, JSON punctuation and checkpoint escaping included.
        val emptyBytes = syncWireJson.encodeToString(DeltaRequestDto(PushRequestDto(emptyList()), pull)).encodeToByteArray().size
        var bodyBytes = emptyBytes
        for (candidate in batch) {
            val op = store.dao.getOutboxEntry(candidate.opId) ?: continue
            if (op.localRevision != candidate.localRevision || op.deliveryState != OutboxEntity.STATE_PENDING || op.envelopeJson != null) continue
            if (store.dao.getOutboxByRowId(op.rowId).firstOrNull()?.opId != op.opId) continue
            val base = store.dao.getServerBase(op.rowId)
            if (base != null && (!base.standardConfirmed || base.deleted || base.projectionError != null)) {
                store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_REVIEW, "Remote row unavailable or incompatible; intent retained for review")
                continue
            }
            if (base == null && op.kind != "create") {
                store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_REVIEW, "Missing update/delete is not a new record")
                continue
            }
            if (op.parentTaskId != null && op.kind != "delete") {
                val parent = store.dao.getServerBase(op.parentTaskId)
                if (parent == null || !parent.standardConfirmed || parent.deleted || parent.projectionError != null || parent.parentTaskId != null) continue
                val parentOps = store.dao.getOutboxByRowId(op.parentTaskId)
                if (parentOps.any { it.kind == "delete" }) continue
            }
            try {
                val assumed = base?.serverRevision ?: 0L
                check(assumed < MAX_EXACT_VERSION) { "Record version exhausted" }
                val raw = base?.rawJson?.let { TaskJsonMerge.json.parseToJsonElement(it).jsonObject }
                val data = when {
                    op.kind == "delete" -> requireNotNull(raw)
                    raw != null -> TaskJsonMerge.merge(raw, op.intentJson(), op.intentMask)
                    else -> op.intentJson()
                }
                if (op.kind != "delete") TaskJsonMerge.decode(op.rowId, data)
                val envelope = PushRecordDto(op.tableName, op.rowId, data, assumed + 1L, op.kind == "delete", assumed)
                val encoded = syncWireJson.encodeToString(envelope)
                val recordBytes = encoded.encodeToByteArray().size
                if (recordBytes > MAX_SYNC_BODY_BYTES - emptyBytes) {
                    store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_CONFLICT,
                        "Task exceeds the 256 KiB sync limit; nothing sent. Reduce record data in a compatible client, or copy native fields as a new task")
                    continue
                }
                val addedBytes = recordBytes + if (frozen.isEmpty()) 0 else 1
                if (addedBytes > MAX_SYNC_BODY_BYTES - bodyBytes) continue
                val prepared = op.copy(envelopeJson = encoded, version = envelope.version,
                    assumedServerVersion = assumed, deliveryState = OutboxEntity.STATE_DISPATCHING,
                    dispatchHistory = "FROZEN_BEFORE_NETWORK", errorMessage = null)
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
                store.dao.upsertOutbox(current.copy(deliveryState = OutboxEntity.STATE_UNCERTAIN, errorMessage = reason,
                    dispatchHistory = "DELIVERY_UNCERTAIN"))
            }
        }
    }

    suspend fun reconcilePushResults(batch: List<OutboxEntity>, response: PushResponseDto) {
        store.withMutationLock {
            val meta = store.dao.getMeta() ?: dev.tabbyrush.shared.data.AccountSyncMetaEntity()
            store.dao.upsertMeta(meta.copy(hubSchemaVersion = maxOf(meta.hubSchemaVersion ?: 0L, response.schemaVersion)))
        }
        for (op in batch) {
            val accepted = response.syncedRecords.any { it.tableName == op.tableName && it.rowId == op.rowId && it.version == op.version }
            val conflict = response.conflicts.any { it.tableName == op.tableName && it.rowId == op.rowId }
            val failed = response.failures.any { it.tableName == op.tableName && it.rowId == op.rowId }
            when {
                accepted && !conflict && !failed -> store.reconcileAccepted(op.opId, requireNotNull(op.version))
                conflict || failed -> store.withMutationLock {
                    store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_CONFLICT,
                        if (conflict) "Remote task changed; refresh then explicitly reapply or discard" else "Schema validation or persistence failed; draft retained for correction")
                }
                else -> markBatchUncertain(listOf(op), "No unambiguous explicit acceptance tuple; pull before review")
            }
        }
    }

    /** A matching state is convergence, never proof that this particular operation executed. */
    suspend fun reconcileUncertain() = store.withMutationLock {
        for (op in store.dao.getAllOutbox().filter { it.deliveryState == OutboxEntity.STATE_UNCERTAIN }) {
            val base = store.dao.getServerBase(op.rowId)
            val envelope = op.envelopeJson?.let { TaskJsonMerge.json.decodeFromString<PushRecordDto>(it) }
            val same = base?.standardConfirmed == true && base.projectionError == null && envelope != null &&
                base.serverRevision == envelope.version && base.deleted == envelope.deleted &&
                base.rawJson?.let { TaskJsonMerge.json.parseToJsonElement(it) } == envelope.data
            if (same) store.dao.convergeAtomic(op.opId)
            else store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_REVIEW,
                "Delivery uncertain and remote state missing or changed; explicit review required, never replayed")
        }
    }
}
