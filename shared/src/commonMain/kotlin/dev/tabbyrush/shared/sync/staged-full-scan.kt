package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.StrictSyncContract
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.ScanStagingEntity
import dev.tabbyrush.shared.data.ServerBaseEntity

/**
 * StagedFullScan manages cursor-based stable rowId pagination from Hub,
 * stages records in SQLite, validates pre-scan confirmed identity invariants,
 * verifies rowId ordering / payload integrity (C8), and performs monotonic
 * promotion to server_base upon reaching terminal page via an atomic transaction (C1).
 */
class StagedFullScan(
    val syncClient: HubSyncClient,
    val store: RoomAccountStore,
    val activeEpoch: String,
    val protocol: String = StrictSyncContract.PROTOCOL_VERSION,
    val isMigration: Boolean = false,
    val tableName: String = StrictSyncContract.DEFAULT_TABLE_NAME,
    val batchSize: Int = 100,
    val maxPageBudget: Int = 1000,
    val isFenced: (() -> Boolean)? = null
) {

    suspend fun executeScan(): ApiResult<Int> {
        // 1. Capture snapshot of pre-scan confirmed base records
        val preScanConfirmed = store.dao.getAllServerBase().associateBy { it.rowId }

        // 2. Clear staging table before beginning pagination
        store.dao.clearStaging()

        var cursor: String? = null
        var pageCount = 0
        var lastSeenRowId: String? = null
        val seenCursors = mutableSetOf<String>()
        val seenRowIds = mutableSetOf<String>()

        // 3. Pagination loop
        while (true) {
            if (cursor != null) {
                if (!seenCursors.add(cursor)) {
                    store.dao.clearStaging()
                    return ApiResult.Failure(
                        SafeError.ProtocolIncompatible(
                            code = "CURSOR_LOOP",
                            message = "Cursor loop or repetition detected: $cursor"
                        )
                    )
                }
            }

            val pullResult = syncClient.pull(
                epoch = activeEpoch,
                protocol = protocol,
                tableName = tableName,
                cursor = cursor,
                batchSize = batchSize
            )

            when (pullResult) {
                is ApiResult.Failure -> {
                    store.dao.clearStaging()
                    return pullResult
                }
                is ApiResult.Success -> {
                    val response = pullResult.value

                    // Epoch check on every page
                    if (response.epoch != activeEpoch) {
                        store.dao.clearStaging()
                        return ApiResult.Failure(
                            SafeError.ResetRequired(
                                code = "EPOCH_MISMATCH",
                                message = "Server returned epoch ${response.epoch} during scan (expected $activeEpoch)"
                            )
                        )
                    }

                    if (response.records.size > batchSize) {
                        store.dao.clearStaging()
                        return ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "BATCH_OVERFLOW",
                                message = "Server returned ${response.records.size} records, exceeding batchSize $batchSize"
                            )
                        )
                    }

                    // Empty page with hasMore check (C8)
                    if (response.records.isEmpty() && response.hasMore) {
                        store.dao.clearStaging()
                        return ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "NON_PROGRESSING_CURSOR",
                                message = "Server returned empty records page while hasMore was true"
                            )
                        )
                    }

                    val stagingEntities = mutableListOf<ScanStagingEntity>()
                    for (record in response.records) {
                        if (record.tableName != tableName) {
                            store.dao.clearStaging()
                            return ApiResult.Failure(
                                SafeError.ProtocolIncompatible(
                                    code = "TABLE_MISMATCH",
                                    message = "Record tableName ${record.tableName} mismatched requested $tableName"
                                )
                            )
                        }
                        if (record.revision <= 0L) {
                            store.dao.clearStaging()
                            return ApiResult.Failure(
                                SafeError.ProtocolIncompatible(
                                    code = "INVALID_REVISION",
                                    message = "Record revision ${record.revision} must be positive"
                                )
                            )
                        }

                        // Payload validation (C8)
                        if (!record.deleted) {
                            val data = record.data
                            if (data == null || data.id != record.rowId || data.title.isBlank() || data.createdAt < 0) {
                                store.dao.clearStaging()
                                return ApiResult.Failure(
                                    SafeError.ProtocolIncompatible(
                                        code = "INVALID_RECORD",
                                        message = "Active record ${record.rowId} had missing or invalid payload"
                                    )
                                )
                            }
                            if (data.parentTaskId != null) {
                                if (data.parentTaskId.isBlank() || data.parentTaskId == data.id || !dev.tabbyrush.shared.contract.StrictSyncV3Contract.isCanonicalUuid(data.parentTaskId)) {
                                    store.dao.clearStaging()
                                    return ApiResult.Failure(
                                        SafeError.ProtocolIncompatible(
                                            code = "INVALID_RELATIONSHIP",
                                            message = "Active record ${record.rowId} had invalid parentTaskId ${data.parentTaskId}"
                                        )
                                    )
                                }
                            }
                            if (protocol != dev.tabbyrush.shared.contract.StrictSyncV3Contract.PROTOCOL_VERSION && data.parentTaskId != null) {
                                store.dao.clearStaging()
                                return ApiResult.Failure(
                                    SafeError.ProtocolIncompatible(
                                        code = "UNEXPECTED_RELATIONSHIP",
                                        message = "Record ${record.rowId} had parentTaskId in non-v3 protocol ($protocol)"
                                    )
                                )
                            }
                        }

                        // Row ordering and uniqueness check (C8)
                        val lastId = lastSeenRowId
                        if (lastId != null && record.rowId <= lastId) {
                            store.dao.clearStaging()
                            return ApiResult.Failure(
                                SafeError.ProtocolIncompatible(
                                    code = "ROW_ORDER_VIOLATION",
                                    message = "RowId ${record.rowId} was not strictly greater than previous rowId $lastId"
                                )
                            )
                        }
                        if (!seenRowIds.add(record.rowId)) {
                            store.dao.clearStaging()
                            return ApiResult.Failure(
                                SafeError.ProtocolIncompatible(
                                    code = "DUPLICATE_ROW_ID",
                                    message = "Duplicate rowId ${record.rowId} received during scan"
                                )
                            )
                        }
                        lastSeenRowId = record.rowId

                        stagingEntities.add(
                            ScanStagingEntity(
                                rowId = record.rowId,
                                tableName = record.tableName,
                                serverRevision = record.revision,
                                deleted = record.deleted,
                                title = record.data?.title ?: "",
                                completed = record.data?.completed ?: false,
                                createdAt = record.data?.createdAt ?: 0L,
                                epoch = response.epoch,
                                deadlineDate = record.data?.deadlineDate,
                                deadlineTime = record.data?.deadlineTime,
                                deadlineZone = record.data?.deadlineZone,
                                deadlineOffsetSeconds = record.data?.deadlineOffsetSeconds,
                                reminderAnchorTime = record.data?.reminderAnchorTime,
                                deadlineAnchorEpochMs = record.data?.deadlineAnchorEpochMs,
                                deadlineOverdueEpochMs = record.data?.deadlineOverdueEpochMs,
                                reminderEnabled = record.data?.reminderEnabled ?: false,
                                reminderLeadMinutes = record.data?.reminderLeadMinutes ?: 0L,
                                reminderIntervalMinutes = record.data?.reminderIntervalMinutes,
                                reminderCount = record.data?.reminderCount ?: 1L,
                                scheduleToken = record.data?.scheduleToken,
                                parentTaskId = record.data?.parentTaskId
                            )
                        )
                    }

                    if (stagingEntities.isNotEmpty()) {
                        store.dao.upsertStaging(stagingEntities)
                    }

                    if (!response.hasMore) {
                        // Reached terminal page successfully
                        break
                    }

                    val nextCursor = response.cursor
                    if (nextCursor.isNullOrBlank()) {
                        store.dao.clearStaging()
                        return ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "MISSING_CURSOR",
                                message = "hasMore is true but response cursor is null or empty"
                            )
                        )
                    }

                    cursor = nextCursor
                    pageCount++
                    if (pageCount >= maxPageBudget) {
                        store.dao.clearStaging()
                        return ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "PAGE_BUDGET_EXCEEDED",
                                message = "Full scan exceeded maximum page budget of $maxPageBudget"
                            )
                        )
                    }
                }
            }
        }

        // 4. Validate pre-scan confirmed identity invariants and promote atomically (C1)
        val promotionResult = promoteStaging(preScanConfirmed)
        return promotionResult
    }

    private suspend fun promoteStaging(
        preScanConfirmed: Map<String, ServerBaseEntity>
    ): ApiResult<Int> = store.withMutationLock {
        val stagedRecords = store.dao.getAllStagedRecords()
        val stagedByRowId = stagedRecords.associateBy { it.rowId }

        // Check for missing pre-scan confirmed identities in the same epoch (C8)
        for ((rowId, preScanRecord) in preScanConfirmed) {
            if (preScanRecord.epoch == activeEpoch && !stagedByRowId.containsKey(rowId)) {
                store.dao.clearStaging()
                return@withMutationLock ApiResult.Failure(
                    SafeError.ResetRequired(
                        code = "CONSISTENCY_ERROR",
                        message = "Pre-scan confirmed record $rowId in epoch $activeEpoch missing from full scan"
                    )
                )
            }
        }

        // Monotonic promotion collection
        val basesToUpsert = mutableListOf<ServerBaseEntity>()

        for (staged in stagedRecords) {
            val currentBase = store.dao.getServerBase(staged.rowId)

            if (currentBase == null) {
                // New record from server
                basesToUpsert.add(
                    ServerBaseEntity(
                        rowId = staged.rowId,
                        serverRevision = staged.serverRevision,
                        deleted = staged.deleted,
                        title = staged.title,
                        completed = staged.completed,
                        createdAt = staged.createdAt,
                        updatedAt = System.currentTimeMillis(),
                        epoch = staged.epoch,
                        deadlineDate = staged.deadlineDate,
                        deadlineTime = staged.deadlineTime,
                        deadlineZone = staged.deadlineZone,
                        deadlineOffsetSeconds = staged.deadlineOffsetSeconds,
                        reminderAnchorTime = staged.reminderAnchorTime,
                        deadlineAnchorEpochMs = staged.deadlineAnchorEpochMs,
                        deadlineOverdueEpochMs = staged.deadlineOverdueEpochMs,
                        reminderEnabled = staged.reminderEnabled,
                        reminderLeadMinutes = staged.reminderLeadMinutes,
                        reminderIntervalMinutes = staged.reminderIntervalMinutes,
                        reminderCount = staged.reminderCount,
                        scheduleToken = staged.scheduleToken,
                        parentTaskId = staged.parentTaskId
                    )
                )
            } else {
                when {
                    staged.serverRevision > currentBase.serverRevision -> {
                        // Higher confirmed revision: validate immutable parent identity for active records
                        if (!staged.deleted) {
                            if (currentBase.parentTaskId != null && staged.parentTaskId != currentBase.parentTaskId) {
                                store.dao.clearStaging()
                                return@withMutationLock ApiResult.Failure(
                                    SafeError.ProtocolIncompatible(
                                        code = "IMMUTABLE_PARENT_VIOLATION",
                                        message = "Server revision changed immutable parentTaskId from ${currentBase.parentTaskId} to ${staged.parentTaskId} on row ${staged.rowId}"
                                    )
                                )
                            }
                            if (currentBase.parentTaskId == null && staged.parentTaskId != null) {
                                store.dao.clearStaging()
                                return@withMutationLock ApiResult.Failure(
                                    SafeError.ProtocolIncompatible(
                                        code = "ROOT_CONVERTED_TO_CHILD",
                                        message = "Server revision converted root task ${staged.rowId} to child of ${staged.parentTaskId}"
                                    )
                                )
                            }
                        }
                        // Higher confirmed revision: promote!
                        basesToUpsert.add(
                            ServerBaseEntity(
                                rowId = staged.rowId,
                                serverRevision = staged.serverRevision,
                                deleted = staged.deleted,
                                title = staged.title,
                                completed = staged.completed,
                                createdAt = staged.createdAt,
                                updatedAt = System.currentTimeMillis(),
                                epoch = staged.epoch,
                                deadlineDate = staged.deadlineDate,
                                deadlineTime = staged.deadlineTime,
                                deadlineZone = staged.deadlineZone,
                                deadlineOffsetSeconds = staged.deadlineOffsetSeconds,
                                reminderAnchorTime = staged.reminderAnchorTime,
                                deadlineAnchorEpochMs = staged.deadlineAnchorEpochMs,
                                deadlineOverdueEpochMs = staged.deadlineOverdueEpochMs,
                                reminderEnabled = staged.reminderEnabled,
                                reminderLeadMinutes = staged.reminderLeadMinutes,
                                reminderIntervalMinutes = staged.reminderIntervalMinutes,
                                reminderCount = staged.reminderCount,
                                scheduleToken = staged.scheduleToken,
                                parentTaskId = if (staged.deleted) currentBase.parentTaskId else (staged.parentTaskId ?: currentBase.parentTaskId)
                            )
                        )
                    }
                    staged.serverRevision == currentBase.serverRevision -> {
                        // Same revision: verify logical equivalence
                        val titlesEqual = staged.title == currentBase.title
                        val completedEqual = staged.completed == currentBase.completed
                        val createdAtEqual = staged.createdAt == currentBase.createdAt
                        val deletedEqual = staged.deleted == currentBase.deleted

                        val parentEqual = staged.parentTaskId == currentBase.parentTaskId
                        val baseHasTemporal = currentBase.deadlineDate != null ||
                                currentBase.reminderEnabled || currentBase.scheduleToken != null
                        val stagedHasTemporal = staged.deadlineDate != null ||
                                staged.reminderEnabled || staged.scheduleToken != null

                        val temporalEqual = if (isMigration && !baseHasTemporal) {
                            // In explicit migration transition only: normalize legacy v1 null/default extension
                            true
                        } else {
                            staged.deadlineDate == currentBase.deadlineDate &&
                                    staged.deadlineTime == currentBase.deadlineTime &&
                                    staged.deadlineZone == currentBase.deadlineZone &&
                                    staged.deadlineOffsetSeconds == currentBase.deadlineOffsetSeconds &&
                                    staged.reminderAnchorTime == currentBase.reminderAnchorTime &&
                                    staged.deadlineAnchorEpochMs == currentBase.deadlineAnchorEpochMs &&
                                    staged.deadlineOverdueEpochMs == currentBase.deadlineOverdueEpochMs &&
                                    staged.reminderEnabled == currentBase.reminderEnabled &&
                                    staged.reminderLeadMinutes == currentBase.reminderLeadMinutes &&
                                    staged.reminderIntervalMinutes == currentBase.reminderIntervalMinutes &&
                                    staged.reminderCount == currentBase.reminderCount &&
                                    staged.scheduleToken == currentBase.scheduleToken
                        }

                        val logicallyEqual = deletedEqual && (staged.deleted ||
                                (titlesEqual && completedEqual && createdAtEqual && temporalEqual && parentEqual))

                        if (!logicallyEqual) {
                            store.dao.clearStaging()
                            return@withMutationLock ApiResult.Failure(
                                SafeError.ProtocolIncompatible(
                                    code = "DIVERGENT_REVISION",
                                    message = "Same revision ${staged.serverRevision} with differing logical data for row ${staged.rowId}"
                                )
                            )
                        }

                        if (isMigration && stagedHasTemporal && !baseHasTemporal) {
                            // Enrich local base with migrated server temporal metadata
                            basesToUpsert.add(
                                ServerBaseEntity(
                                    rowId = staged.rowId,
                                    serverRevision = staged.serverRevision,
                                    deleted = staged.deleted,
                                    title = staged.title,
                                    completed = staged.completed,
                                    createdAt = staged.createdAt,
                                    updatedAt = System.currentTimeMillis(),
                                    epoch = staged.epoch,
                                    deadlineDate = staged.deadlineDate,
                                    deadlineTime = staged.deadlineTime,
                                    deadlineZone = staged.deadlineZone,
                                    deadlineOffsetSeconds = staged.deadlineOffsetSeconds,
                                    reminderAnchorTime = staged.reminderAnchorTime,
                                    deadlineAnchorEpochMs = staged.deadlineAnchorEpochMs,
                                    deadlineOverdueEpochMs = staged.deadlineOverdueEpochMs,
                                    reminderEnabled = staged.reminderEnabled,
                                    reminderLeadMinutes = staged.reminderLeadMinutes,
                                    reminderIntervalMinutes = staged.reminderIntervalMinutes,
                                    reminderCount = staged.reminderCount,
                                    scheduleToken = staged.scheduleToken,
                                    parentTaskId = currentBase.parentTaskId
                                )
                            )
                        }
                    }
                    else -> {
                        // staged.serverRevision < currentBase.serverRevision: retain higher confirmed revision
                    }
                }
            }
        }

        // Fencing check right before atomic promotion
        if (store.isFrozen || isFenced?.invoke() == true) {
            store.dao.clearStaging()
            return@withMutationLock ApiResult.Failure(
                SafeError.Unauthorized(
                    code = "STORE_FROZEN",
                    message = "Store frozen or fenced before scan promotion"
                )
            )
        }

        // Atomic promotion and staging clear under mutation lock (C1, Phase 07)
        store.dao.promoteStagingAtomic(basesToUpsert)
        ApiResult.Success(stagedRecords.size)
    }
}
