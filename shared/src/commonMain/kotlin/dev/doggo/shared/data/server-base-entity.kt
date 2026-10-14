package dev.doggo.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ServerBaseEntity stores the confirmed authoritative server state for a record.
 * A deleted record is permanently marked with deleted = true and empty/tombstone data.
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
    val epoch: String
)
