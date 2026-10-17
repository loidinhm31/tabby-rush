package dev.tabbyrush.shared.data

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
    val consecutiveFailures: Int = 0,
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
    val contractVersion: String = CONTRACT_VERSION_V1,
    val dispatchHistory: String? = null,
    val intentMask: String? = null,
    val transitionCheckpoint: String? = null
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_DISPATCHING = "DISPATCHING"
        const val STATE_UNCERTAIN = "UNCERTAIN"
        const val STATE_CONFLICT = "CONFLICT"
        const val STATE_UNKNOWN_SUPERSEDED = "UNKNOWN_SUPERSEDED"

        const val CONTRACT_VERSION_V1 = "strict-v1"
        const val CONTRACT_VERSION_V2 = "strict-v2"

        const val INTENT_TITLE = "TITLE"
        const val INTENT_COMPLETED = "COMPLETED"
        const val INTENT_DEADLINE = "DEADLINE"
        const val INTENT_REMINDER = "REMINDER"
        const val INTENT_DELETE = "DELETE"
        const val INTENT_REQUIRES_REVIEW = "REQUIRES_REVIEW"
    }
}
