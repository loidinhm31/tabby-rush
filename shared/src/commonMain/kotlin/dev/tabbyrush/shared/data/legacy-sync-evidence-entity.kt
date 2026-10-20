package dev.tabbyrush.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Original SQLite values, including frozen strict payload/provenance. Never sent to a sync endpoint. */
@Entity(tableName = "legacy_sync_evidence")
data class LegacySyncEvidenceEntity(
    @PrimaryKey val sourceOpId: String,
    val rowId: String,
    val originalJson: String,
    val outcome: String = "PENDING_REVIEW",
    val translatedOpId: String? = null
)
