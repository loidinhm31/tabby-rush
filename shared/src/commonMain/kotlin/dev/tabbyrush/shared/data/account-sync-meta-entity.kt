package dev.tabbyrush.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AccountSyncMetaEntity tracks persistent synchronization metadata for an account partition,
 * including independent Hub schema observation, composite checkpoints, scan generation, and credential recovery.
 */
@Entity(tableName = "account_sync_meta")
data class AccountSyncMetaEntity(
    @PrimaryKey
    val id: String = SINGLETON_ID,
    val lastSyncTime: Long? = null,
    val localRevisionCounter: Long = 0L,
    val syncStatus: String = "IDLE",
    val safeErrorCode: String? = null,
    val safeErrorMessage: String? = null,
    val consecutiveFailedRounds: Int = 0,
    val credentialPersistencePending: Boolean = false,
    val hubSchemaVersion: Long? = null,
    val checkpointUpdatedAt: String? = null,
    val checkpointId: String? = null,
    val scanGeneration: Long = 0L,
    val scanState: String = "REQUIRED",
    val scanCheckpointUpdatedAt: String? = null,
    val scanCheckpointId: String? = null,
    val lastCompletedCatchUpEpochMs: Long? = null,
    val accessGeneration: Long = 0L,
    val ownerBindingState: String = "UNRESOLVED_LOCAL",
    val boundOwnerUserId: String? = null,
    val accessRevision: Long? = null
) {
    companion object {
        const val SINGLETON_ID = "singleton"
    }
}
