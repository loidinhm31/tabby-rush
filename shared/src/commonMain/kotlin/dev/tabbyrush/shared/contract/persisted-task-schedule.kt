package dev.tabbyrush.shared.contract

/**
 * PersistedTaskSchedule represents a task's committed scheduling state,
 * projected strictly from server_base overlaid with submitted outbox intentions.
 * Uncommitted editor drafts are strictly excluded.
 */
data class PersistedTaskSchedule(
    val taskId: String,
    val title: String,
    val completed: Boolean,
    val isDeleted: Boolean,
    val deadline: TaskDeadline?,
    val reminderRule: ReminderRule,
    val scheduleToken: String?,
    val hasUnresolvedConflict: Boolean,
    val parentTaskId: String? = null,
    val hasUnresolvedPreservationBarrier: Boolean = false
) {
    /**
     * A task is eligible for scheduling reminders if and only if:
     * - It is not deleted
     * - It is not completed (saved completion is a cancellation barrier)
     * - It has no unresolved conflict (conflict/unknown heads suppress scheduling)
     * - It has no unresolved preservation barrier (preservation intent suppresses parent scheduling)
     * - It has an active deadline
     * - Reminder rule is enabled (reminder-disable is a cancellation barrier)
     * - Schedule token is present
     */
    val isEligibleForScheduling: Boolean
        get() = !isDeleted &&
                !completed &&
                !hasUnresolvedConflict &&
                !hasUnresolvedPreservationBarrier &&
                deadline != null &&
                reminderRule.enabled &&
                scheduleToken != null
}
