package dev.tabbyrush.shared.data

import androidx.room.Entity
import androidx.room.Index

/**
 * ReminderDeliveryLedgerEntity tracks durable occurrence state and claim outcomes
 * for native reminders. Account-partitioned, durable across restarts, and wiped with account.
 * Occurrence identity is accountKey + generation + taskId + scheduleToken + occurrenceIndex.
 */
@Entity(
    tableName = "reminder_delivery_ledger",
    primaryKeys = ["accountKey", "generation", "bindingKey", "tableName", "taskId", "scheduleToken", "occurrenceIndex"],
    indices = [
        Index(value = ["accountKey", "generation", "bindingKey", "tableName", "taskId"]),
        Index(value = ["dueEpochMs"])
    ]
)
data class ReminderDeliveryLedgerEntity(
    val accountKey: String,
    val generation: Long,
    val bindingKey: String = BINDING_LOCAL_ACCOUNT,
    val ownerBinding: String = BINDING_LOCAL_ACCOUNT,
    val ownerUserId: String? = null,
    val tableName: String = dev.tabbyrush.shared.contract.TaskDataContract.DEFAULT_TABLE_NAME,
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
