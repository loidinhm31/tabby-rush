package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME
import dev.tabbyrush.shared.contract.AccessLossReason
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.CanonicalRecordRef
import dev.tabbyrush.shared.contract.CanonicalRecordSnapshot
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.TaskAccessState
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.TaskCapabilities
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.data.RoomAccountStore
import kotlinx.datetime.Instant

/**
 * Executes revision-bound staged full-scan and collaboration reconciliation.
 *
 * Lifecycle:
 * 1. Discards un-promoted staging state and captures starting access generation.
 * 2. Starts null full-scan requesting todos and taskAssignees with collaborationVersion = 1.
 * 3. Binds stable access revision E from page 1.
 * 4. Stages all content pages in durable storage, validating checkpoint progression and E consistency.
 * 5. Enumerates union of staged, previously accessible, and draft/outbox task refs
 *    (excluding private unsent owner-created tasks).
 * 6. Validates access in bounded batches (<= 100) at revision E.
 *    Authoritative unavailable / ACCESS_DENIED immediately invokes privacy-first invalidation.
 * 7. Sends explicit empty accessChecks: [] at revision E to confirm boundary.
 * 8. Atomically promotes staged reconciliation in Room.
 * 9. On 409 ACCESS_REVISION_CHANGED, discards staging and signals caller to restart.
 */
class StagedFullScan(
    val syncClient: HubSyncClient,
    val store: RoomAccountStore,
    val batchSize: Int = 100,
    val maxPageBudget: Int = 1000,
    val displayedTaskRefs: Set<TaskRef> = emptySet(),
    val isFenced: (() -> Boolean)? = null
) {
    suspend fun executeScan(): ApiResult<Unit> {
        if (syncClient.expectedAccountKey != store.accountKey || isFenced?.invoke() == true) {
            return fenced()
        }

        var scanGeneration = store.accessGeneration
        store.clearStagedReconciliation()
        val scanTables = listOf("todos", ASSIGNMENT_TABLE_NAME)

        // Page 1: begin null scan and bind access revision E
        val firstResult = syncClient.pull(
            PullRequestDto(
                checkpoint = null,
                batchSize = batchSize,
                tables = scanTables,
                collaborationVersion = COLLABORATION_VERSION_1
            )
        )

        val firstResponse = when (firstResult) {
            is ApiResult.Failure -> {
                handleFailure(firstResult.error)
                return firstResult
            }
            is ApiResult.Success -> firstResult.value
        }

        if (isFenced?.invoke() == true || store.accessGeneration != scanGeneration) return fenced()

        val expectedRevision = firstResponse.accessRevision ?: return ApiResult.Failure(
            SafeError.ProtocolIncompatible("MISSING_ACCESS_REVISION", "Server omitted accessRevision in collaboration mode")
        )

        val stagedTaskRefs = mutableSetOf<TaskRef>()
        var checkpoint: SyncCheckpointDto? = null

        // Stage page 1
        try {
            validatePage(firstResponse, null)
            stagePage(expectedRevision, firstResponse, stagedTaskRefs)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            store.clearStagedReconciliation()
            throw cancelled
        } catch (_: Exception) {
            store.clearStagedReconciliation()
            return ApiResult.Failure(
                SafeError.ProtocolIncompatible("INVALID_PULL_PAGE", "Pull page rejected; saved checkpoint and local work retained")
            )
        }

        checkpoint = firstResponse.checkpoint
        var currentPage = firstResponse
        var pageCount = 1

        // Drain remaining data pages
        while (currentPage.hasMore) {
            if (pageCount >= maxPageBudget) {
                store.clearStagedReconciliation()
                return ApiResult.Failure(SafeError.Network("SCAN_PAGE_LIMIT", "Catch-up paused; next round must restart a fresh scan"))
            }
            if (isFenced?.invoke() == true || store.accessGeneration != scanGeneration) {
                store.clearStagedReconciliation()
                return fenced()
            }

            val nextResult = syncClient.pull(
                PullRequestDto(
                    checkpoint = checkpoint,
                    batchSize = batchSize,
                    tables = scanTables,
                    collaborationVersion = COLLABORATION_VERSION_1,
                    expectedAccessRevision = expectedRevision
                )
            )

            currentPage = when (nextResult) {
                is ApiResult.Failure -> {
                    handleFailure(nextResult.error)
                    store.clearStagedReconciliation()
                    return nextResult
                }
                is ApiResult.Success -> nextResult.value
            }

            if (isFenced?.invoke() == true || store.accessGeneration != scanGeneration) {
                store.clearStagedReconciliation()
                return fenced()
            }

            if (currentPage.accessRevision != expectedRevision) {
                store.clearStagedReconciliation()
                return ApiResult.Failure(SafeError.AccessRevisionChanged())
            }

            try {
                validatePage(currentPage, checkpoint)
                stagePage(expectedRevision, currentPage, stagedTaskRefs)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                store.clearStagedReconciliation()
                throw cancelled
            } catch (_: Exception) {
                store.clearStagedReconciliation()
                return ApiResult.Failure(
                    SafeError.ProtocolIncompatible("INVALID_PULL_PAGE", "Pull page rejected; saved checkpoint and local work retained")
                )
            }

            checkpoint = currentPage.checkpoint
            pageCount++
        }

        // Terminal scan complete. Enumerate exact union of task refs for access validation.
        val unionRefs = computeAccessCheckUnion(stagedTaskRefs)

        // Execute bounded access checks (<= 100 per batch) at expectedRevision
        val checkBatches = unionRefs.chunked(100)
        for (batch in checkBatches) {
            if (isFenced?.invoke() == true || store.accessGeneration != scanGeneration) {
                store.clearStagedReconciliation()
                return fenced()
            }

            val checkDtos = batch.map { CanonicalRecordRefDto(ownerUserId = it.ownerUserId, tableName = "todos", rowId = it.rowId) }
            val checkRequest = PullRequestDto(
                collaborationVersion = COLLABORATION_VERSION_1,
                expectedAccessRevision = expectedRevision,
                accessChecks = checkDtos
            )

            val checkResult = syncClient.pull(checkRequest)
            val checkResponse = when (checkResult) {
                is ApiResult.Failure -> {
                    handleFailure(checkResult.error)
                    store.clearStagedReconciliation()
                    return checkResult
                }
                is ApiResult.Success -> checkResult.value
            }

            if (checkResponse.accessRevision != expectedRevision) {
                store.clearStagedReconciliation()
                return ApiResult.Failure(SafeError.AccessRevisionChanged())
            }

            val results = checkResponse.accessResults ?: emptyList()
            val requestedSet = checkDtos.map { Triple(it.ownerUserId, it.tableName, it.rowId) }.toSet()
            val returnedSet = results.map { Triple(it.ref.ownerUserId, it.ref.tableName, it.ref.rowId) }

            if (returnedSet.size != results.size || returnedSet.toSet() != requestedSet) {
                store.clearStagedReconciliation()
                return ApiResult.Failure(
                    SafeError.ProtocolIncompatible("INVALID_ACCESS_RESULTS", "Access check results do not match requested refs exactly")
                )
            }

            val stagedAccessStates = mutableListOf<TaskAccessState>()
            for (res in results) {
                val ref = TaskRef(res.ref.ownerUserId, res.ref.rowId)
                if (res.status == "accessible") {
                    val caps = res.capabilities?.let {
                        TaskCapabilities(
                            editContent = it.editContent,
                            manageAssignment = it.manageAssignment,
                            deleteTask = it.deleteTask,
                            createChild = it.createChild
                        )
                    } ?: TaskCapabilities.NONE
                    val coverage = when (res.hierarchyCoverage) {
                        "child-only" -> HierarchyCoverage.ChildOnly
                        "full-group" -> HierarchyCoverage.FullGroup
                        else -> HierarchyCoverage.Standalone
                    }
                    stagedAccessStates.add(
                        TaskAccessState(
                            ref = ref,
                            revision = expectedRevision,
                            capabilities = caps,
                            coverage = coverage,
                            status = TaskAccessStatus.Accessible
                        )
                    )
                } else {
                    // Authoritative unavailable/ACCESS_DENIED: immediately purge content & write evidence
                    store.invalidateAccess(ref, AccessLossReason.UNAVAILABLE, System.currentTimeMillis())
                    scanGeneration = store.accessGeneration
                    stagedAccessStates.add(
                        TaskAccessState(
                            ref = ref,
                            revision = expectedRevision,
                            capabilities = TaskCapabilities.NONE,
                            coverage = HierarchyCoverage.Standalone,
                            status = TaskAccessStatus.Unavailable
                        )
                    )
                }
            }

            store.stageAccessResults(expectedRevision, stagedAccessStates)
        }

        // Terminal validation-only request: explicit empty accessChecks = [] at revision E
        if (isFenced?.invoke() == true || store.accessGeneration != scanGeneration) {
            store.clearStagedReconciliation()
            return fenced()
        }

        val terminalResult = syncClient.pull(
            PullRequestDto(
                collaborationVersion = COLLABORATION_VERSION_1,
                expectedAccessRevision = expectedRevision,
                accessChecks = emptyList()
            )
        )

        val terminalResponse = when (terminalResult) {
            is ApiResult.Failure -> {
                handleFailure(terminalResult.error)
                store.clearStagedReconciliation()
                return terminalResult
            }
            is ApiResult.Success -> terminalResult.value
        }

        if (terminalResponse.accessRevision != expectedRevision) {
            store.clearStagedReconciliation()
            return ApiResult.Failure(SafeError.AccessRevisionChanged())
        }

        // Persist terminal data checkpoint into scanCheckpoint metadata under mutation lock
        store.withMutationLock {
            val meta = store.dao.getMeta() ?: dev.tabbyrush.shared.data.AccountSyncMetaEntity()
            store.dao.upsertMeta(
                meta.copy(
                    scanCheckpointUpdatedAt = checkpoint?.updatedAt,
                    scanCheckpointId = checkpoint?.id
                )
            )
        }

        // Atomic promotion in Room
        val promoted = store.promoteReconciliation(expectedRevision, System.currentTimeMillis())
        if (!promoted) {
            store.clearStagedReconciliation()
            return ApiResult.Failure(SafeError.AccessRevisionChanged())
        }

        return ApiResult.Success(Unit)
    }

    private suspend fun stagePage(
        revision: Long,
        response: PullResponseDto,
        stagedTaskRefs: MutableSet<TaskRef>
    ) {
        val snapshots = response.records.map { r ->
            val owner = r.ownerUserId ?: store.userId
            if (r.tableName == "todos") {
                stagedTaskRefs.add(TaskRef(owner, r.rowId))
            }
            CanonicalRecordSnapshot(
                ref = CanonicalRecordRef(ownerUserId = owner, tableName = r.tableName, rowId = r.rowId),
                dataJson = r.data.toString(),
                version = r.version,
                syncedAt = runCatching { Instant.parse(r.syncedAt).toEpochMilliseconds() }.getOrDefault(0L),
                deleted = r.deleted
            )
        }
        store.stageDataPage(revision, snapshots)
    }

    private suspend fun computeAccessCheckUnion(stagedTaskRefs: Set<TaskRef>): List<TaskRef> {
        val priorAccessRefs = store.dao.getAllAccess()
            .filter { it.status == TaskAccessStatus.Accessible.name }
            .map { TaskRef(it.ownerUserId, it.rowId) }
        val outboxTasks = store.dao.getAllOutbox().filter { it.tableName == "todos" }.map {
            TaskRef(it.ownerUserId ?: store.userId, it.rowId)
        }
        val draftTasks = store.dao.getAllDrafts().filter { it.tableName == "todos" }.map {
            TaskRef(it.ownerUserId ?: store.userId, it.rowId)
        }

        val candidateUnion = (stagedTaskRefs + priorAccessRefs + outboxTasks + draftTasks + displayedTaskRefs)

        // Exclude private unsent owner-created tasks
        return candidateUnion.filter { ref ->
            if (ref.ownerUserId == store.userId && ref !in stagedTaskRefs) {
                val confirmed = store.readConfirmedBase(ref.toRecordRef())
                confirmed != null
            } else {
                true
            }
        }.sorted()
    }

    private suspend fun handleFailure(error: SafeError) {
        if (error is SafeError.MembershipDenied) {
            store.invalidateAllSharedAccess(AccessLossReason.MEMBERSHIP_DENIED, System.currentTimeMillis())
        }
    }

    private fun fenced(): ApiResult.Failure =
        ApiResult.Failure(SafeError.Unauthorized("ACCOUNT_FENCED", "Account changed during catch-up"))
}

internal fun validatePage(response: PullResponseDto, previous: SyncCheckpointDto?) {
    require(response.schemaVersion in 1..MAX_EXACT_VERSION)
    val checkpointTime = Instant.parse(response.checkpoint.updatedAt)
    var lastRecordTime: Instant? = null
    for (record in response.records) {
        require((record.tableName == "todos" || record.tableName == ASSIGNMENT_TABLE_NAME) &&
                record.version in 1..MAX_EXACT_VERSION && record.rowId.isNotEmpty())
        val currentRecordTime = Instant.parse(record.syncedAt)
        if (lastRecordTime != null) {
            require(currentRecordTime >= lastRecordTime) { "Regressive record syncedAt within page" }
        }
        require(currentRecordTime <= checkpointTime) { "Record syncedAt exceeds page checkpoint" }
        lastRecordTime = currentRecordTime
    }
    if (previous != null) {
        require(compareCheckpoint(response.checkpoint, previous) >= 0) { "Regressive checkpoint" }
    }
    if (response.hasMore) {
        require(response.records.isNotEmpty()) { "Nonprogressing empty page" }
        if (previous != null) {
            require(compareCheckpoint(response.checkpoint, previous) > 0) { "Nonprogressing page checkpoint" }
        }
    }
}

internal fun compareCheckpoint(left: SyncCheckpointDto, right: SyncCheckpointDto): Int {
    val time = Instant.parse(left.updatedAt).compareTo(Instant.parse(right.updatedAt))
    return if (time == 0) left.id.compareTo(right.id) else time
}
