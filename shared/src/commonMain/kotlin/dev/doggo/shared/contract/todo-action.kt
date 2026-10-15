package dev.doggo.shared.contract

sealed interface TodoAction {
    data class SelectFilter(val filter: TodoFilter) : TodoAction
    data class OpenCreate(val initialTitle: String = "") : TodoAction
    data class ChangeDraftTitle(val rawTitle: String) : TodoAction
    data object SubmitEditor : TodoAction
    data object CancelEditor : TodoAction
    data class OpenEdit(val todo: Todo) : TodoAction
    data class ToggleCompleted(val todo: Todo) : TodoAction
    data class RequestDelete(val todo: Todo) : TodoAction
    data object ConfirmDelete : TodoAction
    data object CancelDelete : TodoAction
    data object TriggerSync : TodoAction
    data object ReconcilePending : TodoAction
    data class DiscardConflict(val rowId: String) : TodoAction
    data class ReapplyConflict(val rowId: String) : TodoAction
    data class CopyAsNew(val rowId: String) : TodoAction
    data object DismissError : TodoAction
    data object RequestLeave : TodoAction
    data object ConfirmLeave : TodoAction
    data object CancelLeave : TodoAction
    data class UpdateEditorState(val editor: EditorState) : TodoAction
    data object OpenSettings : TodoAction
    data object CloseSettings : TodoAction
    data class SaveSettings(val settings: ReminderSettingsState) : TodoAction
    data object RequestExactAlarmPermission : TodoAction
    data object OpenNotificationSettings : TodoAction
    data class ClockTick(val nowEpochMs: Long) : TodoAction
}
