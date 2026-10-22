package dev.tabbyrush.shared.contract

import kotlinx.serialization.Serializable

/**
 * Task assignment relation table name.
 */
const val ASSIGNMENT_TABLE_NAME = "taskAssignees"

/**
 * Desired assignment intention submitted by the task creator.
 *
 * A null [desiredRecipientUserId] indicates unassignment (tombstone).
 * [expectedSlotVersion] ensures optimistic concurrency control against server slot version.
 */
@Serializable
data class AssignmentIntent(
    val taskRef: TaskRef,
    val desiredRecipientUserId: String?,
    val expectedSlotVersion: Long
) {
    init {
        require(expectedSlotVersion >= 0) { "expectedSlotVersion must be non-negative" }
        if (desiredRecipientUserId != null) {
            require(desiredRecipientUserId.isNotBlank()) { "desiredRecipientUserId must not be blank when present" }
        }
    }

    /**
     * The canonical record reference for this assignment slot in [ASSIGNMENT_TABLE_NAME].
     * The slot rowId strictly matches the target task rowId.
     */
    val slotRef: CanonicalRecordRef
        get() = CanonicalRecordRef(
            ownerUserId = taskRef.ownerUserId,
            tableName = ASSIGNMENT_TABLE_NAME,
            rowId = taskRef.rowId
        )
}

/**
 * Stored or synchronized record for an assignment slot.
 */
@Serializable
data class AssignmentSlotRecord(
    val taskRef: TaskRef,
    val recipientUserId: String?,
    val version: Long,
    val deleted: Boolean = false
) {
    init {
        require(version >= 0) { "version must be non-negative" }
    }

    val slotRef: CanonicalRecordRef
        get() = CanonicalRecordRef(
            ownerUserId = taskRef.ownerUserId,
            tableName = ASSIGNMENT_TABLE_NAME,
            rowId = taskRef.rowId
        )
}

/**
 * Result of an assignment slot operation.
 */
sealed interface AssignmentSlotResult {
    data class Success(val record: AssignmentSlotRecord) : AssignmentSlotResult
    data object Missing : AssignmentSlotResult
    data object AccessDenied : AssignmentSlotResult
    data class VersionConflict(val expectedVersion: Long, val actualVersion: Long) : AssignmentSlotResult
    data class InvalidStructure(val message: String) : AssignmentSlotResult
    data object Unchanged : AssignmentSlotResult
}

/**
 * Named result of a canonical task mutation, distinguishing concurrency and authorization outcomes.
 */
sealed interface TaskMutationResult {
    data class Changed(val todo: Todo) : TaskMutationResult
    data object Unchanged : TaskMutationResult
    data object Missing : TaskMutationResult
    data object AccessDenied : TaskMutationResult
    data class VersionConflict(val expectedVersion: Long, val actualVersion: Long) : TaskMutationResult
    data class InvalidStructure(val message: String) : TaskMutationResult
}
