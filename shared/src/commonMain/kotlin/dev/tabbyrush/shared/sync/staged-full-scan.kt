package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.data.RoomAccountStore
import kotlinx.datetime.Instant

/** Pages commit durably, but without a retention bound only this uninterrupted invocation may promote them. */
class StagedFullScan(
    val syncClient: HubSyncClient,
    val store: RoomAccountStore,
    val batchSize: Int = 100,
    val maxPageBudget: Int = 1000,
    val isFenced: (() -> Boolean)? = null
) {
    suspend fun executeScan(): ApiResult<Unit> {
        if (syncClient.expectedAccountKey != store.accountKey || isFenced?.invoke() == true) return fenced()
        val start = store.withMutationLock { store.dao.beginScanAtomic() }
        var checkpoint = start.scanCheckpointUpdatedAt?.let { SyncCheckpointDto(it, requireNotNull(start.scanCheckpointId)) }
        repeat(maxPageBudget) {
            if (isFenced?.invoke() == true) return fenced()
            val response = when (val result = syncClient.pull(PullRequestDto(checkpoint, batchSize))) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> result.value
            }
            if (isFenced?.invoke() == true) return fenced()
            try {
                validatePage(response, checkpoint)
                val projected = response.records.map(TaskJsonMerge::project)
                store.withMutationLock {
                    if (isFenced?.invoke() == true) error("Account generation changed")
                    store.dao.commitPageAtomic(projected, response.checkpoint.updatedAt, response.checkpoint.id,
                        response.schemaVersion, start.scanGeneration, response.hasMore, System.currentTimeMillis())
                    if (!response.hasMore) store.dao.promoteCompletedScanAtomic(start.scanGeneration, System.currentTimeMillis())
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return ApiResult.Failure(SafeError.ProtocolIncompatible("INVALID_PULL_PAGE", "Pull page rejected; saved checkpoint and local work retained"))
            }
            checkpoint = response.checkpoint
            if (!response.hasMore) return ApiResult.Success(Unit)
        }
        return ApiResult.Failure(SafeError.Network("SCAN_PAGE_LIMIT", "Catch-up paused; next round must restart a fresh scan"))
    }

    private fun fenced(): ApiResult.Failure = ApiResult.Failure(SafeError.Unauthorized("ACCOUNT_FENCED", "Account changed during catch-up"))
}

internal fun validatePage(response: PullResponseDto, previous: SyncCheckpointDto?) {
    require(response.schemaVersion in 1..MAX_EXACT_VERSION)
    Instant.parse(response.checkpoint.updatedAt)
    var last = previous
    for (record in response.records) {
        require(record.tableName == "todos" && record.version in 1..MAX_EXACT_VERSION && record.rowId.isNotEmpty())
        val current = SyncCheckpointDto(record.syncedAt, record.rowId)
        if (last != null) require(compareCheckpoint(current, last) > 0) { "Nonprogressing record page" }
        require(compareCheckpoint(current, response.checkpoint) <= 0) { "Checkpoint precedes page" }
        last = current
    }
    if (previous != null) require(compareCheckpoint(response.checkpoint, previous) >= 0) { "Regressive checkpoint" }
    if (response.hasMore) {
        require(response.records.isNotEmpty()) { "Nonprogressing empty page" }
        if (previous != null) require(compareCheckpoint(response.checkpoint, previous) > 0)
    }
}

internal fun compareCheckpoint(left: SyncCheckpointDto, right: SyncCheckpointDto): Int {
    val time = Instant.parse(left.updatedAt).compareTo(Instant.parse(right.updatedAt))
    return if (time == 0) left.id.compareTo(right.id) else time
}
