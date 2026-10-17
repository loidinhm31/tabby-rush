package dev.tabbyrush.shared.contract

import kotlinx.coroutines.flow.StateFlow

enum class SyncStatus {
    IDLE,
    SYNCING,
    PAUSED_CONFLICT,
    PAUSED_PROTOCOL,
    RESET_REQUIRED,
    UNCERTAIN
}

data class SyncProgress(
    val pendingCount: Int = 0,
    val uncertainCount: Int = 0,
    val conflictCount: Int = 0,
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSyncTime: Long? = null,
    val safeError: SafeError? = null
)

interface SyncCoordinator {
    val progress: StateFlow<SyncProgress>

    suspend fun triggerSync()
    suspend fun reconcilePending()
    suspend fun discardConflict(rowId: String)
    suspend fun reapplyConflict(rowId: String)
    suspend fun copyAsNew(rowId: String)
    suspend fun requestLeave(): Boolean
    fun close()
}
