package dev.doggo.shared.data

import androidx.room.Entity
import androidx.room.Index

/**
 * ReminderDeliveryLedgerEntity tracks durable occurrence state and claim outcomes
 * for native reminders. Account-partitioned, durable across restarts, and wiped with account.
 * Occurrence identity is accountKey + generation + taskId + scheduleToken + occurrenceIndex.
 */
@Entity(
    tableName = "reminder_delivery_ledger",
    primaryKeys = ["accountKey", "generation", "taskId", "scheduleToken", "occurrenceIndex"],
    indices = [
        Index(value = ["accountKey", "generation", "taskId"]),
        Index(value = ["dueEpochMs"])
    ]
)
data class ReminderDeliveryLedgerEntity(
    val accountKey: String,
    val generation: Long,
    val taskId: String,
    val scheduleToken: String,
    val occurrenceIndex: Int,
    val dueEpochMs: Long,
    val claimOutcome: String = OUTCOME_PENDING,
    val claimedAtEpochMs: Long? = null,
    val postedPlatformId: String? = null,
    val cancellationState: String = CANCELLATION_ACTIVE,
    val updatedAtEpochMs: Long
) {
    companion object {
        const val OUTCOME_PENDING = "PENDING"
        const val OUTCOME_CLAIMED = "CLAIMED"
        const val OUTCOME_POSTED = "POSTED"
        const val OUTCOME_MISSED = "MISSED"
        const val OUTCOME_CANCELLED = "CANCELLED"
        const val OUTCOME_SKIPPED = "SKIPPED"

        const val CANCELLATION_ACTIVE = "ACTIVE"
        const val CANCELLATION_CANCELLED = "CANCELLED"
    }
}
