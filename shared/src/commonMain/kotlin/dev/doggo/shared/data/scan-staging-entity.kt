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
    val epoch: String
)
