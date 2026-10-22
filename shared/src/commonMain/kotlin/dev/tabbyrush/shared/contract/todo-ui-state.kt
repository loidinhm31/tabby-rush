package dev.tabbyrush.shared.contract

data class EditorState(
    val taskRef: TaskRef? = null,
    val rawTitle: String,
    val error: String? = null,
    val hasDeadline: Boolean = false,
    val deadlineDate: String = "",
    val hasTime: Boolean = false,
    val deadlineTime: String = "",
    val deadlineZone: String = "UTC",
    val chosenOffsetSeconds: Long? = null,
    val reminderAnchorTime: String = "09:00",
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Long = 0L,
    val reminderIntervalMinutes: Long? = null,
    val reminderCount: Long = 1L,
    val originalDeadline: TaskDeadline? = null,
    val originalReminderRule: ReminderRule = ReminderRule.DISABLED,
    val originalScheduleToken: String? = null,
    val parentTaskRef: TaskRef? = null,
    val parentTitle: String? = null,
    val assigneeUserId: String? = null,
    val expectedSlotVersion: Long = 0L,
    val inheritedAssigneeUserId: String? = null,
    val canManageAssignment: Boolean = true
) {
    val id: String? get() = taskRef?.rowId
    val parentTaskId: String? get() = parentTaskRef?.rowId
}

data class ReminderSettingsState(
    val notificationsEnabled: Boolean = false,
    val defaultReminderEnabled: Boolean = false,
    val defaultReminderLeadMinutes: Long = 0L,
    val defaultReminderIntervalMinutes: Long? = null,
    val defaultReminderCount: Long = 1L,
    val defaultDateOnlyAnchorTime: String = "09:00",
    val capabilities: ReminderCapabilities? = null,
    val platformName: String = ""
)

data class ConflictState(
    val taskRef: TaskRef,
    val localTodo: Todo?,
    val remoteTodo: Todo?,
    val requiresDeliveryReview: Boolean = false,
    val isPreservationConflict: Boolean = false,
    val isSubtaskConflict: Boolean = false,
    val isParentDeleted: Boolean = false
) {
    val rowId: String get() = taskRef.rowId
}

data class LeaveGuardState(
    val pendingCount: Int,
    val uncertainCount: Int,
    val conflictCount: Int
)

data class TodoUiState(
    val authState: AuthState = AuthState.UNAUTHENTICATED,
    val identity: AccountIdentity? = null,
    val accessRequest: AccessRequestInfo? = null,
    val items: List<Todo> = emptyList(),
    val hierarchicalItems: List<HierarchicalTodoItem> = emptyList(),
    val unresolvedChildren: List<Todo> = emptyList(),
    val counts: TodoCounts = TodoCounts(0, 0, 0),
    val filter: TodoFilter = TodoFilter.ALL,
    val loading: Boolean = false,
    val syncProgress: SyncProgress = SyncProgress(),
    val editor: EditorState? = null,
    val settings: ReminderSettingsState? = null,
    val conflict: ConflictState? = null,
    val deleteCandidate: Todo? = null,
    val deleteCandidateChildCount: Int = 0,
    val leaveGuard: LeaveGuardState? = null,
    val safeError: SafeError? = null,
    val nowEpochMs: Long = 0L,
    val accessLossEvidence: List<AccessLossEvidence> = emptyList()
)
