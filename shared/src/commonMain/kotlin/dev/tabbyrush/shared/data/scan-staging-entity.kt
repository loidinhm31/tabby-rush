package dev.tabbyrush.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ScanStagingEntity holds records accumulated across pagination pages during
 * a full scan until the scan terminates successfully and promotes to server_base.
 */
@Entity(
    tableName = "scan_staging",
    primaryKeys = ["bindingKey", "tableName", "rowId"]
)
data class ScanStagingEntity(
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
    val scanGeneration: Long = 0L,
    val projectionError: String? = null
)
