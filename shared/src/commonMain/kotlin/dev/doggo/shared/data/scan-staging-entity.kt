package dev.doggo.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ScanStagingEntity holds records accumulated across pagination pages during
 * a full scan until the scan terminates successfully and promotes to server_base.
 */
@Entity(tableName = "scan_staging")
data class ScanStagingEntity(
    @PrimaryKey
    val rowId: String,
    val tableName: String,
    val serverRevision: Long,
    val deleted: Boolean,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
    val epoch: String,
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
    val scheduleToken: String? = null
)
