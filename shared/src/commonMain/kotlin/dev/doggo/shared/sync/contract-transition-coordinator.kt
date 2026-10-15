package dev.doggo.shared.sync

import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.StrictSyncContract
import dev.doggo.shared.contract.StrictSyncV2Contract
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.data.ServerBaseEntity
import kotlin.random.Random

enum class TransitionPhase(val wireName: String) {
    READY_V1("ready-v1"),
    UPGRADE_DETECTED("upgrade-detected"),
    DISPATCH_QUIESCED("dispatch-quiesced"),
    LEGACY_RECONCILING("legacy-reconciling"),
    V2_BASE_SCANNING("v2-base-scanning"),
    SAFE_INTENTS_TRANSLATING("safe-intents-translating"),
    READY_V2("ready-v2");

    companion object {
        fun fromWireName(wireName: String?): TransitionPhase? {
            return entries.firstOrNull { it.wireName == wireName }
        }
    }
}

/**
 * ContractTransitionCoordinator manages the orderly, data-preserving transition
 * of an existing client partition from strict-v1 to strict-v2:
 *
 * ready-v1 -> upgrade-detected -> dispatch-quiesced -> legacy-reconciling ->
 * v2-base-scanning -> safe-intents-translating -> ready-v2
 *
 * Invariants:
 * 1. Quiesces dispatch and recovers in-flight requests as UNCERTAIN.
 * 2. Exact legacy receipt reconciliation using original frozen v1 preimages/digests.
 * 3. Never-sent safe intent translation: applies known changed legacy fields over scanned v2 base.
 * 4. Preserves remote deadline/reminder fields when translating title/completion edits.
 * 5. Ambiguous provenance or REQUIRES_REVIEW operations block as explicit conflicts.
 */
class ContractTransitionCoordinator(
    val store: RoomAccountStore,
    val syncClient: HubSyncClient,
    private val uuidGenerator: () -> String = { generateUuid() }
) {

    suspend fun getDurablePhase(): TransitionPhase {
        val meta = store.dao.getMeta()
        val status = meta?.syncStatus ?: return TransitionPhase.READY_V1
        if (status.startsWith(TRANSITION_PREFIX)) {
            val phaseStr = status.removePrefix(TRANSITION_PREFIX)
            return TransitionPhase.fromWireName(phaseStr) ?: TransitionPhase.READY_V1
        }
        if (status == TransitionPhase.READY_V2.wireName) {
            return TransitionPhase.READY_V2
        }
        return TransitionPhase.READY_V1
    }

    suspend fun setDurablePhase(phase: TransitionPhase) {
        val meta = store.dao.getMeta() ?: dev.doggo.shared.data.AccountSyncMetaEntity()
        val newStatus = if (phase == TransitionPhase.READY_V2) {
            TransitionPhase.READY_V2.wireName
        } else {
            "$TRANSITION_PREFIX${phase.wireName}"
        }
        store.dao.upsertMeta(
            meta.copy(
                syncStatus = newStatus,
                safeErrorCode = "CHECKPOINT_${phase.name}"
            )
        )
    }

    suspend fun executeTransition(
        activeEpoch: String,
        onProgress: ((TransitionPhase) -> Unit)? = null
    ): ApiResult<TransitionPhase> {
        if (store.isFrozen) {
            return ApiResult.Failure(
                SafeError.Unauthorized("STORE_FROZEN", "Store is frozen for account leave")
            )
        }

        var currentPhase = getDurablePhase()

        // Phase 1: Upgrade Detected
        if (currentPhase == TransitionPhase.READY_V1 || currentPhase == TransitionPhase.UPGRADE_DETECTED) {
            currentPhase = TransitionPhase.UPGRADE_DETECTED
            setDurablePhase(currentPhase)
            onProgress?.invoke(currentPhase)
        }

        // Phase 2: Dispatch Quiesced
        if (currentPhase == TransitionPhase.UPGRADE_DETECTED || currentPhase == TransitionPhase.DISPATCH_QUIESCED) {
            // Recover any in-flight DISPATCHING mutations to UNCERTAIN
            store.recoverInterruptedDispatches()

            // Mark any uncertain or dispatching v1 operations with durable quiescence checkpoint
            val allOutbox = store.dao.getAllOutbox()
            for (op in allOutbox) {
                if (op.contractVersion == OutboxEntity.CONTRACT_VERSION_V1 &&
                    op.deliveryState == OutboxEntity.STATE_UNCERTAIN &&
                    op.transitionCheckpoint == null
                ) {
                    store.dao.upsertOutbox(
                        op.copy(
                            transitionCheckpoint = "QUIESCED",
                            dispatchHistory = op.dispatchHistory ?: """{"quiesced":true}"""
                        )
                    )
                }
            }

            currentPhase = TransitionPhase.DISPATCH_QUIESCED
            setDurablePhase(currentPhase)
            onProgress?.invoke(currentPhase)
        }

        // Phase 3: Legacy Receipts Reconciliation
        if (currentPhase == TransitionPhase.DISPATCH_QUIESCED || currentPhase == TransitionPhase.LEGACY_RECONCILING) {
            currentPhase = TransitionPhase.LEGACY_RECONCILING
            setDurablePhase(currentPhase)
            onProgress?.invoke(currentPhase)

            val reconcileResult = reconcileLegacyReceipts(activeEpoch)
            if (reconcileResult is ApiResult.Failure) {
                return reconcileResult
            }
        }

        // Phase 4: V2 Base Scanning
        if (currentPhase == TransitionPhase.LEGACY_RECONCILING || currentPhase == TransitionPhase.V2_BASE_SCANNING) {
            currentPhase = TransitionPhase.V2_BASE_SCANNING
            setDurablePhase(currentPhase)
            onProgress?.invoke(currentPhase)

            val scan = StagedFullScan(
                syncClient = syncClient,
                store = store,
                activeEpoch = activeEpoch,
                protocol = StrictSyncV2Contract.PROTOCOL_VERSION,
                isMigration = true
            ).executeScan()

            when (scan) {
                is ApiResult.Failure -> return scan
                is ApiResult.Success -> { /* Base scan successfully promoted */ }
            }
        }

        // Phase 5: Safe Intents Translating
        if (currentPhase == TransitionPhase.V2_BASE_SCANNING || currentPhase == TransitionPhase.SAFE_INTENTS_TRANSLATING) {
            currentPhase = TransitionPhase.SAFE_INTENTS_TRANSLATING
            setDurablePhase(currentPhase)
            onProgress?.invoke(currentPhase)

            val translateResult = translateSafeIntents()
            if (translateResult is ApiResult.Failure) {
                return translateResult
            }
        }

        // Phase 6: Ready V2
        currentPhase = TransitionPhase.READY_V2
        setDurablePhase(currentPhase)
        onProgress?.invoke(currentPhase)

        return ApiResult.Success(currentPhase)
    }

    private suspend fun reconcileLegacyReceipts(activeEpoch: String): ApiResult<Unit> {
        val allOutbox = store.dao.getAllOutbox()
        val v1PendingOps = allOutbox.filter { op ->
            op.contractVersion == OutboxEntity.CONTRACT_VERSION_V1 &&
                    op.transitionCheckpoint != "PROVEN_NOT_ACCEPTED" &&
                    op.transitionCheckpoint != "TRANSLATED_V2" &&
                    (op.deliveryState == OutboxEntity.STATE_UNCERTAIN ||
                            op.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED ||
                            op.consecutiveFailures > 0 ||
                            op.dispatchHistory != null ||
                            op.transitionCheckpoint == "QUIESCED")
        }

        if (v1PendingOps.isEmpty()) {
            return ApiResult.Success(Unit)
        }

        // Chunk into batches of up to 100 operations
        val batches = v1PendingOps.chunked(100)
        for (batch in batches) {
            val receiptItems = batch.map { op ->
                val dataDto = if (op.kind == StrictOperationKind.DELETE.wireName) {
                    null
                } else {
                    StrictTodoDataDto(
                        id = op.rowId,
                        title = op.title ?: "",
                        completed = op.completed ?: false,
                        createdAt = op.createdAt ?: 0L
                    )
                }

                LegacyReceiptItemDto(
                    opId = op.opId,
                    tableName = op.tableName,
                    rowId = op.rowId,
                    kind = op.kind,
                    expectedRevision = op.expectedRevision,
                    digest = op.digest,
                    data = dataDto
                )
            }

            val result = syncClient.legacyReceipts(activeEpoch, receiptItems)
            when (result) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> {
                    val response = result.value
                    val resultsByOpId = response.results.associateBy { it.opId }

                    for (op in batch) {
                        val receiptResult = resultsByOpId[op.opId]
                        if (receiptResult == null) {
                            store.dao.updateDeliveryState(
                                op.opId,
                                OutboxEntity.STATE_UNCERTAIN,
                                "Omitted from legacy receipts response"
                            )
                            continue
                        }

                        when (receiptResult.status) {
                            LegacyReceiptStatus.PROVEN_ACCEPTED -> {
                                val acceptedRev = receiptResult.acceptedRevision ?: (op.expectedRevision + 1L)
                                store.reconcileAccepted(op.opId, acceptedRev)
                            }

                            LegacyReceiptStatus.PROVEN_NOT_ACCEPTED -> {
                                store.dao.upsertOutbox(
                                    op.copy(
                                        deliveryState = OutboxEntity.STATE_PENDING,
                                        transitionCheckpoint = "PROVEN_NOT_ACCEPTED",
                                        errorMessage = null
                                    )
                                )
                            }

                            LegacyReceiptStatus.UNKNOWN_SUPERSEDED -> {
                                store.dao.updateDeliveryState(
                                    op.opId,
                                    OutboxEntity.STATE_UNKNOWN_SUPERSEDED,
                                    "Superseded during legacy transition"
                                )
                            }

                            LegacyReceiptStatus.INTEGRITY_FAILURE -> {
                                store.dao.updateDeliveryState(
                                    op.opId,
                                    OutboxEntity.STATE_CONFLICT,
                                    "Legacy receipt integrity failure"
                                )
                            }

                            else -> {
                                store.dao.updateDeliveryState(
                                    op.opId,
                                    OutboxEntity.STATE_UNCERTAIN,
                                    "Unrecognized legacy receipt status: ${receiptResult.status}"
                                )
                            }
                        }
                    }
                }
            }
        }
        return ApiResult.Success(Unit)
    }

    private suspend fun translateSafeIntents(): ApiResult<Unit> {
        val allOutbox = store.dao.getAllOutbox()
        val translatableOps = allOutbox.filter { op ->
            op.contractVersion == OutboxEntity.CONTRACT_VERSION_V1 &&
                    (op.transitionCheckpoint == "PROVEN_NOT_ACCEPTED" ||
                            (op.deliveryState == OutboxEntity.STATE_PENDING &&
                                    op.dispatchHistory == null &&
                                    op.consecutiveFailures == 0))
        }

        for (op in translatableOps) {
            if (op.intentMask == OutboxEntity.INTENT_REQUIRES_REVIEW) {
                store.withMutationLock {
                    store.dao.updateDeliveryState(
                        op.opId,
                        OutboxEntity.STATE_CONFLICT,
                        "Requires review from legacy Room v1 migration"
                    )
                }
                continue
            }

            val base = store.dao.getServerBase(op.rowId)
            val newOpId = uuidGenerator()

            when (op.kind) {
                StrictOperationKind.CREATE.wireName -> {
                    if (base != null && !base.deleted) {
                        store.withMutationLock {
                            store.dao.updateDeliveryState(
                                op.opId,
                                OutboxEntity.STATE_CONFLICT,
                                "Cannot translate create: row already exists on server (rev ${base.serverRevision})"
                            )
                        }
                    } else {
                        val newV2Op = OutboxEntity(
                            opId = newOpId,
                            epoch = op.epoch,
                            tableName = op.tableName,
                            rowId = op.rowId,
                            kind = StrictOperationKind.CREATE.wireName,
                            expectedRevision = 0L,
                            title = op.title ?: "",
                            completed = op.completed ?: false,
                            createdAt = op.createdAt ?: System.currentTimeMillis(),
                            digest = "",
                            localRevision = op.localRevision,
                            deliveryState = OutboxEntity.STATE_PENDING,
                            createdAtEpochMs = op.createdAtEpochMs,
                            contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                            intentMask = op.intentMask ?: "${OutboxEntity.INTENT_TITLE}|${OutboxEntity.INTENT_COMPLETED}",
                            transitionCheckpoint = "TRANSLATED_V2"
                        )
                        store.withMutationLock {
                            store.dao.replaceOutboxAtomic(oldOpId = op.opId, newOp = newV2Op)
                        }
                    }
                }

                StrictOperationKind.UPDATE.wireName -> {
                    if (base == null || base.deleted) {
                        store.withMutationLock {
                            store.dao.updateDeliveryState(
                                op.opId,
                                OutboxEntity.STATE_CONFLICT,
                                "Cannot translate update: base row missing or deleted on server"
                            )
                        }
                    } else {
                        val newV2Op = OutboxEntity(
                            opId = newOpId,
                            epoch = op.epoch,
                            tableName = op.tableName,
                            rowId = op.rowId,
                            kind = StrictOperationKind.UPDATE.wireName,
                            expectedRevision = base.serverRevision,
                            title = op.title ?: base.title,
                            completed = op.completed ?: base.completed,
                            createdAt = base.createdAt,
                            digest = "",
                            localRevision = op.localRevision,
                            deliveryState = OutboxEntity.STATE_PENDING,
                            createdAtEpochMs = op.createdAtEpochMs,
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
                            scheduleToken = base.scheduleToken,
                            contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                            intentMask = op.intentMask ?: "${OutboxEntity.INTENT_TITLE}|${OutboxEntity.INTENT_COMPLETED}",
                            transitionCheckpoint = "TRANSLATED_V2"
                        )
                        store.withMutationLock {
                            store.dao.replaceOutboxAtomic(oldOpId = op.opId, newOp = newV2Op)
                        }
                    }
                }

                StrictOperationKind.DELETE.wireName -> {
                    if (base == null || base.deleted) {
                        store.withMutationLock {
                            store.dao.deleteOutbox(op.opId)
                        }
                    } else {
                        val newV2Op = OutboxEntity(
                            opId = newOpId,
                            epoch = op.epoch,
                            tableName = op.tableName,
                            rowId = op.rowId,
                            kind = StrictOperationKind.DELETE.wireName,
                            expectedRevision = base.serverRevision,
                            title = "",
                            completed = false,
                            createdAt = base.createdAt,
                            digest = "",
                            localRevision = op.localRevision,
                            deliveryState = OutboxEntity.STATE_PENDING,
                            createdAtEpochMs = op.createdAtEpochMs,
                            contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                            intentMask = OutboxEntity.INTENT_DELETE,
                            transitionCheckpoint = "TRANSLATED_V2"
                        )
                        store.withMutationLock {
                            store.dao.replaceOutboxAtomic(oldOpId = op.opId, newOp = newV2Op)
                        }
                    }
                }
            }
        }
        return ApiResult.Success(Unit)
    }

    companion object {
        const val TRANSITION_PREFIX = "TRANSITION:"

        private fun generateUuid(): String {
            val random = Random.Default
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte() // UUID v4
            bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte() // RFC 4122 variant
            return buildString(36) {
                for (i in 0 until 16) {
                    if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
                    val b = bytes[i].toInt() and 0xff
                    append(b.toString(16).padStart(2, '0'))
                }
            }
        }
    }
}
