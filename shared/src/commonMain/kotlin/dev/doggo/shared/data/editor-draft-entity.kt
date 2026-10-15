package dev.doggo.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * EditorDraftEntity persists uncommitted or in-progress local modifications
 * distinct from the confirmed server base and frozen outbox operations.
 */
@Entity(tableName = "editor_drafts")
data class EditorDraftEntity(
    @PrimaryKey
    val rowId: String,
    val draftTitle: String,
    val draftCompleted: Boolean,
    val localRevision: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
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
