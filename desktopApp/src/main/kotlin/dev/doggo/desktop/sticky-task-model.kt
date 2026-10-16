package dev.doggo.desktop

import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.contract.TaskSections

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
