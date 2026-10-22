package dev.tabbyrush.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ServerBaseEntity stores the confirmed authoritative server state for a record.
 * Deleted/missing identities stay locally suppressed even after server tombstone retention expires.
 */
const val BINDING_LOCAL_ACCOUNT = "LOCAL_ACCOUNT"
const val BINDING_BOUND = "BOUND"

fun boundBindingKey(userId: String): String = "BOUND:$userId"

@Entity(
    tableName = "server_base",
    primaryKeys = ["bindingKey", "tableName", "rowId"]
)
data class ServerBaseEntity(
    val bindingKey: String = BINDING_LOCAL_ACCOUNT,
    val ownerBinding: String = BINDING_LOCAL_ACCOUNT,
    val ownerUserId: String? = null,
    val tableName: String = dev.tabbyrush.shared.contract.TaskDataContract.DEFAULT_TABLE_NAME,
    val rowId: String,
    val serverRevision: Long = 0L,
    val deleted: Boolean = false,
    val title: String = "",
    val completed: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deadlineDate: String? = null,
    val deadlineTime: String? = null,
    val deadlineZone: String? = null,
    val deadlineOffsetSeconds: Long? = null,
    val reminderAnchorTime: String? = null,
    val deadlineAnchorEpochMs: Long? = null,
    val deadlineOverdueEpochMs: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Long = 0L,
    val reminderIntervalMinutes: Long? = null,
    val reminderCount: Long = 1L,
    val scheduleToken: String? = null,
    val parentTaskId: String? = null,
    val rawJson: String? = null,
    val syncedAt: String? = null,
    val standardConfirmed: Boolean = false,
    val projectionError: String? = null
) {
    fun toPersistedRecord(fallbackOwnerUserId: String = ""): dev.tabbyrush.shared.contract.PersistedBaseRecord {
        val resolvedOwner = ownerUserId ?: fallbackOwnerUserId
        return dev.tabbyrush.shared.contract.SimplePersistedBaseRecord(
            ownerUserId = resolvedOwner,
            tableName = tableName,
            rowId = rowId,
            title = title,
            completed = completed,
            deleted = deleted,
            parentTaskId = parentTaskId,
            deadlineDate = deadlineDate,
            deadlineTime = deadlineTime,
            deadlineZone = deadlineZone,
            deadlineOffsetSeconds = deadlineOffsetSeconds,
            reminderAnchorTime = reminderAnchorTime,
            deadlineAnchorEpochMs = deadlineAnchorEpochMs,
            deadlineOverdueEpochMs = deadlineOverdueEpochMs,
            reminderEnabled = reminderEnabled,
            reminderLeadMinutes = reminderLeadMinutes,
            reminderIntervalMinutes = reminderIntervalMinutes,
            reminderCount = reminderCount,
            scheduleToken = scheduleToken,
            projectionError = projectionError
        )
    }
}
