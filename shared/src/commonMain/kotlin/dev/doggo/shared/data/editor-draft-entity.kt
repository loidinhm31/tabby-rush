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
    val isDeleted: Boolean = false
)
