package dev.doggo.shared.sync

import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.StrictOperationStatus
import dev.doggo.shared.contract.StrictSyncContract
import dev.doggo.shared.contract.StrictSyncDigest
import dev.doggo.shared.contract.StrictTodoData
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore

/**
 * OutboxReconciliation coordinates:
 * 1. Gathering eligible FIFO outbox operations:
 *    - Strictly checks head operation per rowId. If head is CONFLICT or UNKNOWN_SUPERSEDED,
 *      successors are blocked from dispatching (C3).
 * 2. Freezing expectedRevision, epoch, and digest at DISPATCH time against current base (C2, C5).
 * 3. Exact CAS acknowledgement matching:
 *    - Matching account, epoch, opId, rowId, digest, and acceptedRevision == expectedRevision + 1.
 *    - Advancing server_base monotonically while preserving newer editor drafts.
 *    - Converting apparent conflicts after UNCERTAIN dispatch into UNKNOWN_SUPERSEDED.
 *    - Bounded retry tracking for retryableFailure.
 */
class OutboxReconciliation(
    val store: RoomAccountStore,
    val activeEpoch: String
) {

    /**
     * Gathers eligible operations to dispatch.
     * Enforces strict FIFO order per row: checks the earliest operation by localRevision.
     * If the earliest operation is CONFLICT, UNKNOWN_SUPERSEDED, or DISPATCHING,
     * no successor on that row can dispatch (C3).
     * Capped at maxCount operations (default 100).
     */
    suspend fun getEligibleDispatchBatch(maxCount: Int = 100): List<OutboxEntity> {
        val allOutbox = store.dao.getAllOutbox()
        val byRow = allOutbox.groupBy { it.rowId }
        val eligible = mutableListOf<OutboxEntity>()

        for ((_, rowOps) in byRow) {
            val head = rowOps.minByOrNull { it.localRevision } ?: continue

            // If head is conflicted or unknown superseded, row is blocked; successors cannot pass (C3)
            if (head.deliveryState == OutboxEntity.STATE_CONFLICT ||
                head.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED ||
                head.deliveryState == OutboxEntity.STATE_DISPATCHING
            ) {
                continue
            }

            // Only dispatch if head is PENDING or UNCERTAIN
            if (head.deliveryState in setOf(OutboxEntity.STATE_PENDING, OutboxEntity.STATE_UNCERTAIN)) {
                eligible.add(head)
                if (eligible.size >= maxCount) {
                    break
                }
            }
        }

        return eligible
    }

    /**
     * Prepares and freezes batch operations at dispatch time (C2, C5).
     * Recomputes expectedRevision from current server_base, binds activeEpoch,
     * computes canonical digest, and marks state as DISPATCHING.
     */
    suspend fun prepareAndFreezeBatch(batch: List<OutboxEntity>): List<OutboxEntity> {
        val frozenBatch = mutableListOf<OutboxEntity>()

        for (op in batch) {
            if (op.deliveryState == OutboxEntity.STATE_UNCERTAIN) {
                // N1: Retain exact frozen identity, revision, epoch, and digest for UNCERTAIN retry!
                val dispatchingOp = op.copy(deliveryState = OutboxEntity.STATE_DISPATCHING)
                store.dao.updateDeliveryState(op.opId, OutboxEntity.STATE_DISPATCHING, null)
                frozenBatch.add(dispatchingOp)
                continue
            }

            val base = store.dao.getServerBase(op.rowId)

            val kind: StrictOperationKind
            val expectedRev: Long

            if (op.kind == StrictOperationKind.DELETE.wireName) {
                kind = StrictOperationKind.DELETE
                expectedRev = base?.serverRevision ?: 0L
            } else {
                if (base == null || base.serverRevision == 0L || base.deleted) {
                    kind = StrictOperationKind.CREATE
                    expectedRev = 0L
                } else {
                    kind = StrictOperationKind.UPDATE
                    expectedRev = base.serverRevision
                }
            }

            val todoData = if (kind == StrictOperationKind.DELETE) null else StrictTodoData(
                id = op.rowId,
                title = op.title ?: "",
                completed = op.completed ?: false,
                createdAt = op.createdAt ?: 0L
            )

            val digest = StrictSyncDigest.computeDigest(
                domain = StrictSyncContract.PROTOCOL_VERSION,
                appId = store.appId,
                userId = store.userId,
                epoch = activeEpoch,
                tableName = StrictSyncContract.DEFAULT_TABLE_NAME,
                rowId = op.rowId,
                opId = op.opId,
                kind = kind,
                expectedRevision = expectedRev,
                data = todoData
            )

            val frozenOp = op.copy(
                epoch = activeEpoch,
                kind = kind.wireName,
                expectedRevision = expectedRev,
                digest = digest,
                deliveryState = OutboxEntity.STATE_DISPATCHING
            )

            // N2: Optimistic concurrency check to ensure no intervening edit occurred
            val frozen = store.dao.freezeOutboxAtomic(op.opId, op.localRevision, frozenOp)
            if (frozen) {
                frozenBatch.add(frozenOp)
            }
        }
        return frozenBatch
    }

    /**
     * Converts a frozen OutboxEntity to a wire StrictOperationDto.
     * Delete operation data payload is serialized as null.
     */
    fun toDto(entity: OutboxEntity): StrictOperationDto {
        val dataDto = if (entity.kind == StrictOperationKind.DELETE.wireName) {
            null
        } else {
            StrictTodoDataDto(
                id = entity.rowId,
                title = entity.title ?: "",
                completed = entity.completed ?: false,
                createdAt = entity.createdAt ?: 0L
            )
        }

        return StrictOperationDto(
            opId = entity.opId,
            epoch = entity.epoch,
            tableName = entity.tableName,
            rowId = entity.rowId,
            kind = entity.kind,
            expectedRevision = entity.expectedRevision,
            data = dataDto
        )
    }

    /**
     * Marks in-flight operations as UNCERTAIN (e.g. after socket timeout or crash).
     */
    suspend fun markBatchUncertain(operations: List<OutboxEntity>, message: String) {
        for (op in operations) {
            val current = store.dao.getOutboxEntry(op.opId)
            if (current != null && current.deliveryState == OutboxEntity.STATE_DISPATCHING) {
                store.dao.upsertOutbox(
                    current.copy(
                        deliveryState = OutboxEntity.STATE_UNCERTAIN,
                        errorMessage = message,
                        consecutiveFailures = current.consecutiveFailures + 1
                    )
                )
            }
        }
    }

    /**
     * Reconciles server push results with dispatched outbox operations.
     */
    suspend fun reconcilePushResults(
        dispatched: List<OutboxEntity>,
        response: StrictPushResponseDto
    ) {
        val resultsByOpId = response.results.associateBy { it.opId }

        for (op in dispatched) {
            val result = resultsByOpId[op.opId]
            if (result == null) {
                store.dao.updateDeliveryState(
                    opId = op.opId,
                    state = OutboxEntity.STATE_UNCERTAIN,
                    errorMessage = "Server omitted operation from push response"
                )
                continue
            }

            // Validate rowId matches
            if (result.rowId != op.rowId) {
                store.dao.updateDeliveryState(
                    opId = op.opId,
                    state = OutboxEntity.STATE_UNCERTAIN,
                    errorMessage = "Server response rowId ${result.rowId} mismatched request ${op.rowId}"
                )
                continue
            }

            if (result.status == StrictOperationStatus.ACCEPTED.wireName || result.status == StrictOperationStatus.REPLAYED.wireName) {
                if (result.digest != op.digest) {
                    store.dao.updateDeliveryState(
                        opId = op.opId,
                        state = OutboxEntity.STATE_UNCERTAIN,
                        errorMessage = "Server response digest mismatched request digest"
                    )
                    continue
                }
            }

            when (result.status) {
                StrictOperationStatus.ACCEPTED.wireName,
                StrictOperationStatus.REPLAYED.wireName -> {
                    val acceptedRev = result.acceptedRevision
                    val expectedNextRev = op.expectedRevision + 1L

                    if (acceptedRev == null || acceptedRev != expectedNextRev) {
                        store.dao.updateDeliveryState(
                            opId = op.opId,
                            state = OutboxEntity.STATE_UNCERTAIN,
                            errorMessage = "acceptedRevision $acceptedRev did not equal expected $expectedNextRev"
                        )
                    } else {
                        store.reconcileAccepted(
                            opId = op.opId,
                            serverRevision = acceptedRev
                        )
                    }
                }

                StrictOperationStatus.REVISION_CONFLICT.wireName,
                StrictOperationStatus.MISSING_RECORD.wireName,
                StrictOperationStatus.DELETED_RECORD.wireName -> {
                    // Critical Invariant:
                    // If operation was previously UNCERTAIN before this dispatch round,
                    // the previous attempt might have committed and then been superseded by another client.
                    val wasUncertain = op.deliveryState == OutboxEntity.STATE_UNCERTAIN || op.consecutiveFailures > 0
                    if (wasUncertain) {
                        store.dao.updateDeliveryState(
                            opId = op.opId,
                            state = OutboxEntity.STATE_UNKNOWN_SUPERSEDED,
                            errorMessage = "Superseded after uncertain delivery: ${result.status}"
                        )
                    } else {
                        store.dao.updateDeliveryState(
                            opId = op.opId,
                            state = OutboxEntity.STATE_CONFLICT,
                            errorMessage = "Server conflict: ${result.status}"
                        )
                    }
                }

                StrictOperationStatus.RETRYABLE_FAILURE.wireName -> {
                    val current = store.dao.getOutboxEntry(op.opId)
                    val newFailures = (current?.consecutiveFailures ?: op.consecutiveFailures) + 1
                    store.dao.upsertOutbox(
                        op.copy(
                            deliveryState = OutboxEntity.STATE_UNCERTAIN,
                            errorMessage = "Server retryableFailure",
                            consecutiveFailures = newFailures
                        )
                    )
                }

                StrictOperationStatus.OP_ID_REUSE.wireName,
                StrictOperationStatus.INVALID_RECORD.wireName -> {
                    store.dao.updateDeliveryState(
                        opId = op.opId,
                        state = OutboxEntity.STATE_CONFLICT,
                        errorMessage = "Server rejected: ${result.status}"
                    )
                }

                else -> {
                    store.dao.updateDeliveryState(
                        opId = op.opId,
                        state = OutboxEntity.STATE_UNCERTAIN,
                        errorMessage = "Unrecognized status: ${result.status}"
                    )
                }
            }
        }
    }
}
