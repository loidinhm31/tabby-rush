package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.DeadlinePresentation
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.TaskDeadlinePresentation
import dev.tabbyrush.shared.contract.TaskHierarchyProjector
import dev.tabbyrush.shared.contract.TaskSections
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoFilter

data class StickyQuickAddState(
    val rawTitle: String = "",
    val error: String? = null,
    val submitting: Boolean = false
)

data class StickyTaskItem(
    val todo: Todo,
    val effectiveCompleted: Boolean,
    val hasLiveChildren: Boolean,
    val isChild: Boolean,
    val childCount: Int = 0,
    val completedChildCount: Int = 0,
    val isChildOnly: Boolean = false,
    val deadlinePresentation: DeadlinePresentation? = null
)

data class StickyTaskSections(
    val incomplete: List<StickyTaskItem> = emptyList(),
    val completed: List<StickyTaskItem> = emptyList()
)

data class StickyTaskState(
    val readiness: SurfaceReadiness,
    val sections: TaskSections,
    val quickAdd: StickyQuickAddState,
    val projectedSections: StickyTaskSections = projectStickySections(sections),
    val fontScale: Float = DEFAULT_STICKY_FONT_SCALE
)

/**
 * Pure projection of a section's todos into grouped sticky task items.
 * Uses TodoFilter.ALL to ensure whole compact groups stay intact with all active
 * and completed children preserved.
 */
fun projectStickyItems(
    section: List<Todo>,
    nowEpochMs: Long = System.currentTimeMillis()
): List<StickyTaskItem> {
    if (section.isEmpty()) return emptyList()
    val snapshot = TaskHierarchyProjector.projectHierarchy(section, TodoFilter.ALL)
    return snapshot.items.map { item ->
        val deadlinePres = TaskDeadlinePresentation.formatDeadline(
            deadline = item.todo.deadline,
            effectiveCompleted = item.effectiveCompleted,
            nowEpochMs = nowEpochMs
        )
        StickyTaskItem(
            todo = item.todo,
            effectiveCompleted = item.effectiveCompleted,
            hasLiveChildren = item.hasLiveChildren,
            isChild = !item.isChildOnly && item.depth > 0,
            childCount = item.childCount,
            completedChildCount = item.completedChildCount,
            isChildOnly = item.isChildOnly,
            deadlinePresentation = deadlinePres
        )
    }
}

/**
 * Projects partitioned TaskSections into StickyTaskSections atomically.
 */
fun projectStickySections(
    sections: TaskSections,
    nowEpochMs: Long = System.currentTimeMillis()
): StickyTaskSections {
    return StickyTaskSections(
        incomplete = projectStickyItems(sections.incomplete, nowEpochMs),
        completed = projectStickyItems(sections.completed, nowEpochMs)
    )
}

sealed interface StickyTaskAction {
    data class ChangeTitle(val token: SurfaceAccountToken, val rawTitle: String) : StickyTaskAction
    data class Submit(val token: SurfaceAccountToken) : StickyTaskAction
    data class SetCompleted(
        val token: SurfaceAccountToken,
        val taskRef: TaskRef,
        val completed: Boolean
    ) : StickyTaskAction
    data object RefreshDeadlines : StickyTaskAction
    data object OpenApp : StickyTaskAction
    data object Quit : StickyTaskAction
    data object ZoomIn : StickyTaskAction
    data object ZoomOut : StickyTaskAction
    data object ResetZoom : StickyTaskAction
}
