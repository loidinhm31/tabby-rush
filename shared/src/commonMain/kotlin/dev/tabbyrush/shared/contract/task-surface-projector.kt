package dev.tabbyrush.shared.contract

data class DeadlineSelection(
    val taskId: String,
    val title: String,
    val deadline: TaskDeadline,
    val hasUnresolvedConflict: Boolean
)

data class DeadlineSelectionKey(
    val token: SurfaceAccountToken,
    val taskId: String,
    val deadline: TaskDeadline
)

/**
 * Partitions todos into incomplete and completed sections, omitting deleted records
 * in a single pass while preserving encounter order (createdAt DESC, id DESC).
 */
fun partitionSurfaceTodos(todos: List<Todo>): TaskSections {
    return TaskHierarchyProjector.partitionCompactSections(todos)
}

private val DEADLINE_COMPARATOR = compareBy<PersistedTaskSchedule>(
    { it.deadline!!.overdueEpochMs },
    { it.taskId }
)

/**
 * Selects the single most urgent deadline from draft-free persisted schedules
 * in a single pass without intermediate list allocations.
 * Eligible: !isDeleted && !completed && deadline != null (includes unresolved conflicts).
 * Tie-break:
 * - If any overdue (nowEpochMs >= overdueEpochMs): oldest overdue by (overdueEpochMs ASC, taskId ASC).
 * - Otherwise: earliest upcoming by (overdueEpochMs ASC, taskId ASC).
 */
fun selectNextDeadline(schedules: List<PersistedTaskSchedule>, nowEpochMs: Long): DeadlineSelection? {
    var bestOverdue: PersistedTaskSchedule? = null
    var bestUpcoming: PersistedTaskSchedule? = null

    for (schedule in schedules) {
        if (schedule.isDeleted || schedule.completed || schedule.deadline == null) continue
        val dl = schedule.deadline
        if (nowEpochMs >= dl.overdueEpochMs) {
            if (bestOverdue == null || DEADLINE_COMPARATOR.compare(schedule, bestOverdue) < 0) {
                bestOverdue = schedule
            }
        } else {
            if (bestUpcoming == null || DEADLINE_COMPARATOR.compare(schedule, bestUpcoming) < 0) {
                bestUpcoming = schedule
            }
        }
    }

    val selected = bestOverdue ?: bestUpcoming ?: return null

    return DeadlineSelection(
        taskId = selected.taskId,
        title = selected.title,
        deadline = selected.deadline!!,
        hasUnresolvedConflict = selected.hasUnresolvedConflict
    )
}
