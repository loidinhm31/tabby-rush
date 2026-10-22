package dev.tabbyrush.shared.contract

import kotlinx.serialization.Serializable

/**
 * Explicit capabilities granted to an actor for a task.
 *
 * Creator/owner has all capabilities (OWNER).
 * Direct/inherited assignees have content editing only (ASSIGNEE).
 * Non-assignees / viewers / revoked users have NONE.
 */
@Serializable
data class TaskCapabilities(
    val editContent: Boolean,
    val manageAssignment: Boolean,
    val deleteTask: Boolean,
    val createChild: Boolean
) {
    companion object {
        val NONE = TaskCapabilities(
            editContent = false,
            manageAssignment = false,
            deleteTask = false,
            createChild = false
        )
        val OWNER = TaskCapabilities(
            editContent = true,
            manageAssignment = true,
            deleteTask = true,
            createChild = true
        )
        val ASSIGNEE = TaskCapabilities(
            editContent = true,
            manageAssignment = false,
            deleteTask = false,
            createChild = false
        )
    }
}

/**
 * Coverage of hierarchical data available to the client for this task:
 * - [FullGroup]: The full root + children tree is visible and reconciled. Automatic parent completion applies.
 * - [ChildOnly]: Only this individual child is accessible; parent is private/absent on client. Flat projection with no synthetic parent or sibling counts. Automatic parent completion does NOT apply.
 * - [Standalone]: Root task without live children.
 */
enum class HierarchyCoverage {
    FullGroup,
    ChildOnly,
    Standalone
}

enum class TaskAccessStatus {
    Accessible,
    Unavailable
}

/**
 * Authoritative access state reconciled from the server.
 */
@Serializable
data class TaskAccessState(
    val ref: TaskRef,
    val revision: Long,
    val capabilities: TaskCapabilities,
    val coverage: HierarchyCoverage,
    val status: TaskAccessStatus = TaskAccessStatus.Accessible
) {
    val isAccessible: Boolean
        get() = status == TaskAccessStatus.Accessible

    val canEditContent: Boolean
        get() = isAccessible && capabilities.editContent

    val canManageAssignment: Boolean
        get() = isAccessible && capabilities.manageAssignment

    val canDeleteTask: Boolean
        get() = isAccessible && capabilities.deleteTask

    val canCreateChild: Boolean
        get() = isAccessible && capabilities.createChild
}

enum class AccessLossReason {
    REVOKED,
    MEMBERSHIP_DENIED,
    UNAVAILABLE,
    PARENT_UNAVAILABLE
}

/**
 * Content-free, non-replayable evidence of access loss.
 *
 * Privacy Invariant: Retains only identity, local opId, versions, timestamps, and reason.
 * NEVER stores revoked JSON, task title, deadline, recipient, diff, or draft payloads.
 * Cannot be replayed or un-quarantined after regrant.
 */
@Serializable
data class AccessLossEvidence(
    val ref: TaskRef,
    val opId: String? = null,
    val baseVersion: Long? = null,
    val submittedVersion: Long? = null,
    val timestamp: Long,
    val reason: AccessLossReason
)
