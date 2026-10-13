package dev.doggo.shared.contract

data class EditorState(
    val id: String?,
    val rawTitle: String,
    val error: String? = null
)

data class ConflictState(
    val rowId: String,
    val localTodo: Todo?,
    val remoteTodo: Todo?
)

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
    val counts: TodoCounts = TodoCounts(0, 0, 0),
    val filter: TodoFilter = TodoFilter.ALL,
    val loading: Boolean = false,
    val syncProgress: SyncProgress = SyncProgress(),
    val editor: EditorState? = null,
    val conflict: ConflictState? = null,
    val deleteCandidate: Todo? = null,
    val leaveGuard: LeaveGuardState? = null,
    val safeError: SafeError? = null
)
