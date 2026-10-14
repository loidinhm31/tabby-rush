package dev.doggo.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AccountSyncMetaEntity tracks persistent synchronization metadata for an account partition,
 * including active epoch, local monotonic revision counter, and auth credential recovery state.
 */
@Entity(tableName = "account_sync_meta")
data class AccountSyncMetaEntity(
    @PrimaryKey
    val id: String = SINGLETON_ID,
    val activeEpoch: String? = null,
    val lastSyncTime: Long? = null,
    val localRevisionCounter: Long = 0L,
    val syncStatus: String = "IDLE",
    val safeErrorCode: String? = null,
    val safeErrorMessage: String? = null,
    val consecutiveFailedRounds: Int = 0,
    val credentialPersistencePending: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = "singleton"
    }
}
