package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.data.LegacySyncEvidenceEntity
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import kotlinx.serialization.json.*

/** Reads archived provenance only. Never reconstructs a strict digest, packet, or receipt request. */
class LegacyStandardIntentMigration(private val store: RoomAccountStore) {
    suspend fun translateAfterCompletedScan() = store.withMutationLock {
        check(store.dao.getMeta()?.scanState == "READY") { "Fresh standard scan required" }
        for (evidence in store.dao.getLegacyEvidence().filter { it.outcome == "PENDING_REVIEW" }) {
            val op = store.dao.getOutboxEntry(evidence.sourceOpId)
            if (op == null || op.deliveryState in setOf("ACKNOWLEDGED", "CONVERGED", "DISCARDED")) {
                store.dao.translateLegacyAtomic(evidence, null, "REVIEW_ORIGINAL_EVIDENCE")
                continue
            }
            val boundKey = dev.tabbyrush.shared.data.boundBindingKey(op.ownerUserId ?: store.userId)
            val sameRow = store.dao.getOutboxByRow(boundKey, op.tableName, op.rowId)
            val activeSameRow = if (sameRow.isEmpty()) store.dao.getOutboxByRowId(op.rowId) else sameRow
            if (!provablyNeverDispatched(evidence) || activeSameRow.any { it.legacyEvidenceId != null && it.opId != op.opId }) {
                store.dao.translateLegacyAtomic(evidence, null, "REVIEW_DELIVERY_PROVENANCE")
                continue
            }
            val base = store.dao.getServerBase(boundKey, op.tableName, op.rowId)
                ?: store.dao.getServerBase(dev.tabbyrush.shared.data.BINDING_LOCAL_ACCOUNT, op.tableName, op.rowId)
                ?: store.dao.getServerBase(op.rowId)
            if ((base == null && op.kind != "create") || base?.deleted == true || base?.projectionError != null) {
                store.dao.translateLegacyAtomic(evidence, null, "REVIEW_MISSING_OR_INCOMPATIBLE_REMOTE")
                continue
            }
            try {
                val intent = op.intentJson()
                val payload = if (base == null) intent else TaskJsonMerge.merge(
                    TaskJsonMerge.json.parseToJsonElement(requireNotNull(base.rawJson)).jsonObject, intent, op.intentMask)
                if (op.kind != "delete") TaskJsonMerge.decode(op.rowId, payload)
                val translated = op.copy(deliveryState = OutboxEntity.STATE_PENDING, payloadJson = intent.toString(),
                    kind = if (op.kind == "delete") "delete" else if (base == null) "create" else "update",
                    envelopeJson = null, version = null, assumedServerVersion = null,
                    dispatchHistory = "NEVER_DISPATCHED", errorMessage = null, resolution = "LEGACY_INTENT_TRANSLATED")
                store.dao.translateLegacyAtomic(evidence, translated, "TRANSLATED_INTENT_ONLY")
            } catch (_: IllegalArgumentException) {
                store.dao.translateLegacyAtomic(evidence, null, "REVIEW_INVALID_INTENT")
            }
        }
    }

    companion object {
        /** PENDING/null history, old Room1 provenance, and prior nonacceptance are not non-dispatch proof. */
        fun provablyNeverDispatched(evidence: LegacySyncEvidenceEntity): Boolean {
            try {
                val row = TaskJsonMerge.json.parseToJsonElement(evidence.originalJson).jsonObject
                if (row["opId"]?.jsonPrimitive?.contentOrNull != evidence.sourceOpId ||
                    row["rowId"]?.jsonPrimitive?.contentOrNull != evidence.rowId) return false
                if (row["contractVersion"]?.jsonPrimitive?.contentOrNull !in setOf("strict-v1", "strict-v2", "strict-v3")) return false
                val kind = row["kind"]?.jsonPrimitive?.contentOrNull ?: return false
                val revision = row["expectedRevision"]?.jsonPrimitive?.longOrNull ?: return false
                if (kind !in setOf("create", "update", "delete") || revision < 0L || (kind == "create" && revision != 0L)) return false
                if (row["deliveryState"]?.jsonPrimitive?.contentOrNull != "PENDING") return false
                if (row["consecutiveFailures"]?.jsonPrimitive?.longOrNull != 0L) return false
                if (row["transitionCheckpoint"] != null && row["transitionCheckpoint"] != JsonNull) return false
                val mask = row["intentMask"]?.jsonPrimitive?.contentOrNull ?: return false
                if (mask.split('|').any { it !in TaskJsonMerge.masks }) return false
                val history = row["dispatchHistory"]?.jsonPrimitive?.contentOrNull ?: return false
                val events = TaskJsonMerge.json.parseToJsonElement(history).jsonObject
                return events.keys == setOf("createdUnsent", "dispatchCount") &&
                    events["createdUnsent"]?.jsonPrimitive?.booleanOrNull == true &&
                    events["dispatchCount"]?.jsonPrimitive?.longOrNull == 0L
            } catch (_: IllegalArgumentException) {
                return false
            }
        }
    }
}
