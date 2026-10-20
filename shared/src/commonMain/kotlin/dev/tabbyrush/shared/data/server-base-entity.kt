package dev.tabbyrush.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ServerBaseEntity stores the confirmed authoritative server state for a record.
 * Deleted/missing identities stay locally suppressed even after server tombstone retention expires.
 */
@Entity(tableName = "server_base")
data class ServerBaseEntity(
    @PrimaryKey
    val rowId: String,
    val serverRevision: Long,
    val deleted: Boolean,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
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
)
