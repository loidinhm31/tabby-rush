package dev.tabbyrush.android

import dev.tabbyrush.shared.contract.DeadlinePresentation
import dev.tabbyrush.shared.contract.TaskDeadlinePresentation
import dev.tabbyrush.shared.contract.TaskHierarchyProjector
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoFilter

/**
 * Native widget item model projected from TaskHierarchyProjector.
 * Encapsulates effective completion, subtask progress, hierarchy depth, and absolute deadline formatting.
 */
data class WidgetTaskItem(
    val todo: Todo,
    val effectiveCompleted: Boolean,
    val hasLiveChildren: Boolean,
    val isChild: Boolean,
    val childCount: Int = 0,
    val completedChildCount: Int = 0,
    val deadlinePresentation: DeadlinePresentation? = null,
    val isChildOnly: Boolean = false
)

/**
 * Pure projection of a section's todos into adaptive Glance widget items.
 * Uses TodoFilter.ALL to ensure whole compact groups stay intact with all active
 * and completed children preserved.
 */
fun projectWidgetItems(
    section: List<Todo>,
    nowEpochMs: Long = System.currentTimeMillis()
): List<WidgetTaskItem> {
    if (section.isEmpty()) return emptyList()
    val snapshot = TaskHierarchyProjector.projectHierarchy(section, TodoFilter.ALL)
    return snapshot.items.map { item ->
        val deadlinePres = TaskDeadlinePresentation.formatDeadline(
            deadline = item.todo.deadline,
            effectiveCompleted = item.effectiveCompleted,
            nowEpochMs = nowEpochMs
        )
        WidgetTaskItem(
            todo = item.todo,
            effectiveCompleted = item.effectiveCompleted,
            hasLiveChildren = item.hasLiveChildren,
            isChild = item.depth > 0 || item.isChildOnly,
            childCount = item.childCount,
            completedChildCount = item.completedChildCount,
            deadlinePresentation = deadlinePres,
            isChildOnly = item.isChildOnly
        )
    }
}
