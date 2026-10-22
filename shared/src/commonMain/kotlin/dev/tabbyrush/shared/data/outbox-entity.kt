package dev.tabbyrush.shared.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Submitted intent. A non-null envelopeJson is immutable and never automatically replayed. */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["legacyEvidenceId"], unique = true),
        Index(value = ["bindingKey", "tableName", "rowId"])
    ]
)
data class OutboxEntity(
    @PrimaryKey val opId: String,
    val bindingKey: String = BINDING_LOCAL_ACCOUNT,
    val ownerBinding: String = BINDING_LOCAL_ACCOUNT,
    val ownerUserId: String? = null,
    val tableName: String = dev.tabbyrush.shared.contract.TaskDataContract.DEFAULT_TABLE_NAME,
    val rowId: String,
    val kind: String,
    val title: String?,
    val completed: Boolean?,
    val createdAt: Long?,
    val localRevision: Long,
    val deliveryState: String,
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
    val dispatchHistory: String = "NEVER_DISPATCHED",
    val intentMask: String? = null,
    val parentTaskId: String? = null,
    val payloadJson: String = "{}",
    val envelopeJson: String? = null,
    val version: Long? = null,
    val assumedServerVersion: Long? = null,
    val legacyEvidenceId: String? = null,
    val resolution: String? = null
) {
    fun toPersistedRecord(fallbackOwnerUserId: String = ""): dev.tabbyrush.shared.contract.PersistedOutboxRecord {
        val resolvedOwner = ownerUserId ?: fallbackOwnerUserId
        return dev.tabbyrush.shared.contract.SimplePersistedOutboxRecord(
            ownerUserId = resolvedOwner,
            tableName = tableName,
            rowId = rowId,
            localRevision = localRevision,
            deliveryState = deliveryState,
            kind = kind,
            intentMask = intentMask,
            parentTaskId = parentTaskId,
            title = title,
            completed = completed,
            deadlineDate = deadlineDate,
            deadlineTime = deadlineTime,
            deadlineZone = deadlineZone,
            deadlineOffsetSeconds = deadlineOffsetSeconds,
            reminderAnchorTime = reminderAnchorTime,
            deadlineAnchorEpochMs = deadlineAnchorEpochMs,
            deadlineOverdueEpochMs = deadlineOverdueEpochMs,
            reminderEnabled = reminderEnabled,
            reminderLeadMinutes = reminderLeadMinutes,
            reminderIntervalMinutes = reminderIntervalMinutes,
            reminderCount = reminderCount,
            scheduleToken = scheduleToken
        )
    }
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_DISPATCHING = "DISPATCHING"
        const val STATE_UNCERTAIN = "UNCERTAIN"
        const val STATE_CONFLICT = "CONFLICT"
        const val STATE_REVIEW = "REVIEW"
        const val INTENT_TITLE = "TITLE"
        const val INTENT_COMPLETED = "COMPLETED"
        const val INTENT_DEADLINE = "DEADLINE"
        const val INTENT_REMINDER = "REMINDER"
        const val INTENT_DELETE = "DELETE"
        const val INTENT_PARENT = "PARENT"
        const val INTENT_LAST_CHILD_PRESERVATION = "LAST_CHILD_PRESERVATION"
        const val INTENT_REQUIRES_REVIEW = "REQUIRES_REVIEW"
    }
}
