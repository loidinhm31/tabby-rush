package dev.doggo.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * OutboxEntity stores queued, in-flight, or conflicted strict mutations.
 * Immutable fields (opId, epoch, tableName, rowId, kind, expectedRevision,
 * payload, digest, captured localRevision) are preserved without re-computation.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey
    val opId: String,
    val epoch: String,
    val tableName: String = "todos",
    val rowId: String,
    val kind: String, // "create", "update", "delete"
    val expectedRevision: Long,
    val title: String?,
    val completed: Boolean?,
    val createdAt: Long?,
    val digest: String,
    val localRevision: Long,
    val deliveryState: String, // PENDING, DISPATCHING, UNCERTAIN, CONFLICT, UNKNOWN_SUPERSEDED
    val createdAtEpochMs: Long,
    val errorMessage: String? = null,
    val consecutiveFailures: Int = 0
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_DISPATCHING = "DISPATCHING"
        const val STATE_UNCERTAIN = "UNCERTAIN"
        const val STATE_CONFLICT = "CONFLICT"
        const val STATE_UNKNOWN_SUPERSEDED = "UNKNOWN_SUPERSEDED"
    }
}
