package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.TaskSections

data class StickyQuickAddState(
    val rawTitle: String = "",
    val error: String? = null,
    val submitting: Boolean = false
)

data class StickyTaskState(
    val readiness: SurfaceReadiness,
    val sections: TaskSections,
    val quickAdd: StickyQuickAddState
)

sealed interface StickyTaskAction {
    data class ChangeTitle(val token: SurfaceAccountToken, val rawTitle: String) : StickyTaskAction
    data class Submit(val token: SurfaceAccountToken) : StickyTaskAction
    data class SetCompleted(
        val token: SurfaceAccountToken,
        val taskId: String,
        val completed: Boolean
    ) : StickyTaskAction
    data object OpenApp : StickyTaskAction
    data object Quit : StickyTaskAction
}
