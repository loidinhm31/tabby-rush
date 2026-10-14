package dev.doggo.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.doggo.shared.contract.AccessRequestInfo
import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.ConflictState
import dev.doggo.shared.contract.EditorState
import dev.doggo.shared.contract.LeaveGuardState
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.TitlePolicy
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.contract.TodoCounts
import dev.doggo.shared.contract.TodoFilter
import dev.doggo.shared.contract.TodoUiState
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.ui.AuthSubmitAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Retained ViewModel managing TodoUiState and action dispatching.
 * Invariants: Zero credentials or passwords retained in fields or SavedStateHandle.
 */
class SessionViewModel : ViewModel() {

    private val app = DoggoApplication.instance
    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    private var allTodos: List<Todo> = emptyList()
    private var accountJob: Job? = null
    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            app.authSession.state.collect { s ->
                _uiState.value = _uiState.value.copy(authState = s, identity = app.authSession.currentIdentity.value)
                if (s == AuthState.AUTHENTICATED) onAuthenticated() else if (s == AuthState.UNAUTHENTICATED) onUnauthenticated()
            }
        }
    }

    private fun onAuthenticated() {
        accountJob?.cancel()
        val accountKey = app.authSession.currentAccountKey.value ?: return
        app.authSession.currentIdentity.value?.let { app.saveActiveAccount(it, accountKey) }

        accountJob = viewModelScope.launch {
            try {
                val runtime = app.getOrCreateAccountRuntime(accountKey)
                syncJob?.cancel()
                syncJob = launch {
                    runtime.coordinator.progress.collect { p ->
                        _uiState.value = _uiState.value.copy(syncProgress = p, safeError = p.safeError ?: _uiState.value.safeError)
                        if (p.conflictCount > 0) inspectAndShowConflict(runtime)
                        reloadTodos()
                    }
                }
                reloadTodos(); app.syncScheduler.schedulePeriodic(accountKey); runtime.coordinator.triggerSync()
            } catch (ce: CancellationException) { throw ce }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("INTERNAL_ERROR", "Storage operation failed")) }
        }
    }

    private fun onUnauthenticated() {
        accountJob?.cancel(); accountJob = null
        syncJob?.cancel(); syncJob = null
        allTodos = emptyList(); _uiState.value = TodoUiState()
    }

    private suspend fun reloadTodos() {
        val runtime = app.getActiveRuntime() ?: return
        try { allTodos = runtime.store.loadTodos(); applyFilterAndCounts() }
        catch (ce: CancellationException) { throw ce } catch (_: Exception) {}
    }

    private fun applyFilterAndCounts() {
        val total = allTodos.size; val completed = allTodos.count { it.completed }
        val counts = TodoCounts(all = total, active = total - completed, completed = completed)
        val filtered = when (_uiState.value.filter) {
            TodoFilter.ALL -> allTodos
            TodoFilter.ACTIVE -> allTodos.filter { !it.completed }
            TodoFilter.COMPLETED -> allTodos.filter { it.completed }
        }
        _uiState.value = _uiState.value.copy(items = filtered, counts = counts)
    }

    fun onAction(action: TodoAction) = viewModelScope.launch {
        try {
            when (action) {
                is TodoAction.SelectFilter -> { _uiState.value = _uiState.value.copy(filter = action.filter); applyFilterAndCounts() }
                is TodoAction.OpenCreate -> _uiState.value = _uiState.value.copy(editor = EditorState(id = null, rawTitle = action.initialTitle))
                is TodoAction.ChangeDraftTitle -> onTitleChanged(action.rawTitle)
                is TodoAction.SubmitEditor -> submitEditor()
                is TodoAction.CancelEditor -> _uiState.value = _uiState.value.copy(editor = null)
                is TodoAction.OpenEdit -> _uiState.value = _uiState.value.copy(editor = EditorState(id = action.todo.id, rawTitle = action.todo.title))
                is TodoAction.ToggleCompleted -> toggleCompleted(action.todo)
                is TodoAction.RequestDelete -> _uiState.value = _uiState.value.copy(deleteCandidate = action.todo)
                is TodoAction.ConfirmDelete -> confirmDelete()
                is TodoAction.CancelDelete -> _uiState.value = _uiState.value.copy(deleteCandidate = null)
                is TodoAction.TriggerSync -> triggerSync()
                is TodoAction.ReconcilePending -> app.getActiveRuntime()?.coordinator?.reconcilePending()
                is TodoAction.DiscardConflict -> resolveConflict { it.discardConflict(action.rowId) }
                is TodoAction.ReapplyConflict -> resolveConflict { it.reapplyConflict(action.rowId) }
                is TodoAction.CopyAsNew -> resolveConflict { it.copyAsNew(action.rowId) }
                is TodoAction.DismissError -> _uiState.value = _uiState.value.copy(safeError = null)
                is TodoAction.RequestLeave -> requestLeave()
                is TodoAction.ConfirmLeave -> if (app.getActiveRuntime()?.leaveProtocol?.discardAndLeave() == true) { app.clearActiveAccount(); app.closeActiveRuntime(); _uiState.value = _uiState.value.copy(leaveGuard = null) }
                is TodoAction.CancelLeave -> _uiState.value = _uiState.value.copy(leaveGuard = null)
            }
        } catch (ce: CancellationException) { throw ce }
        catch (_: Exception) { _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("INTERNAL_ERROR", "Action execution failed")) }
    }

    private fun onTitleChanged(raw: String) {
        val res = TitlePolicy.normalizeAndValidate(raw)
        val err = (res as? ApiResult.Failure)?.let { (it.error as? SafeError.Validation)?.message ?: "Invalid title" }
        _uiState.value = _uiState.value.copy(editor = _uiState.value.editor?.copy(rawTitle = raw, error = err))
    }

    private suspend fun submitEditor() {
        val editor = _uiState.value.editor ?: return
        val validation = TitlePolicy.normalizeAndValidate(editor.rawTitle)
        if (validation is ApiResult.Failure) {
            _uiState.value = _uiState.value.copy(editor = editor.copy(error = (validation.error as? SafeError.Validation)?.message ?: "Invalid title")); return
        }
        val title = (validation as ApiResult.Success).value; val runtime = app.getActiveRuntime() ?: return; val now = System.currentTimeMillis()
        if (editor.id == null) {
            runtime.store.saveTodoLocally(Todo("todo_" + UUID.randomUUID().toString().replace("-", "").take(16), title, false, now, now))
        } else {
            allTodos.firstOrNull { it.id == editor.id }?.let { runtime.store.saveTodoLocally(it.copy(title = title, updatedAt = maxOf(now, it.createdAt))) } ?: return
        }
        _uiState.value = _uiState.value.copy(editor = null); reloadTodos(); triggerSync()
    }

    private suspend fun toggleCompleted(todo: Todo) {
        app.getActiveRuntime()?.store?.saveTodoLocally(todo.copy(completed = !todo.completed, updatedAt = maxOf(System.currentTimeMillis(), todo.createdAt)))
        reloadTodos(); triggerSync()
    }

    private suspend fun confirmDelete() {
        _uiState.value.deleteCandidate?.let { app.getActiveRuntime()?.store?.deleteTodoLocally(it.id) }
        _uiState.value = _uiState.value.copy(deleteCandidate = null); reloadTodos(); triggerSync()
    }

    private fun triggerSync() {
        app.getActiveRuntime()?.let { r -> viewModelScope.launch { r.coordinator.triggerSync(); app.syncScheduler.triggerImmediate(r.accountKey) } }
    }

    private suspend fun inspectAndShowConflict(runtime: AccountRuntime) {
        val entry = runtime.store.dao.getAllOutbox().firstOrNull { it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED } ?: return
        val base = runtime.store.dao.getServerBase(entry.rowId)
        val remoteTodo = base?.let { Todo(it.rowId, it.title, it.completed, it.createdAt, it.updatedAt, it.serverRevision) }
        _uiState.value = _uiState.value.copy(conflict = ConflictState(entry.rowId, allTodos.firstOrNull { it.id == entry.rowId }, remoteTodo))
    }

    private suspend fun resolveConflict(block: suspend (dev.doggo.shared.sync.AccountSyncCoordinator) -> Unit) {
        app.getActiveRuntime()?.let { block(it.coordinator); _uiState.value = _uiState.value.copy(conflict = null); reloadTodos() }
    }

    private suspend fun requestLeave() {
        val runtime = app.getActiveRuntime()
        if (runtime == null) { app.authSession.logout(); app.clearActiveAccount(); _uiState.value = TodoUiState(); return }
        val r = runtime.leaveProtocol.checkReviewWork()
        if (r.hasReviewWork) {
            _uiState.value = _uiState.value.copy(leaveGuard = LeaveGuardState(r.pendingCount + r.draftCount, r.uncertainCount, r.conflictCount + r.unknownSupersededCount))
        } else if (runtime.leaveProtocol.executeLeave()) {
            app.clearActiveAccount(); app.closeActiveRuntime()
        }
    }

    fun onAuthSubmit(action: AuthSubmitAction) = viewModelScope.launch {
        when (action) {
            is AuthSubmitAction.Login -> handleAuth(app.authSession.login(action.email, action.password))
            is AuthSubmitAction.Register -> handleAuth(app.authSession.register(action.username, action.email, action.password))
            is AuthSubmitAction.RequestAccess -> handleAccess(app.authSession.requestAccess(action.message))
            is AuthSubmitAction.RefreshStatus -> handleAccess(app.authSession.checkAccess())
            is AuthSubmitAction.Logout -> {
                if (app.getActiveRuntime() != null) onAction(TodoAction.RequestLeave)
                else { app.authSession.logout(); app.clearActiveAccount(); _uiState.value = TodoUiState() }
            }
        }
    }

    private fun handleAuth(res: ApiResult<*>) { if (res is ApiResult.Failure) _uiState.value = _uiState.value.copy(safeError = res.error) }
    private fun handleAccess(res: ApiResult<*>) {
        if (res is ApiResult.Failure) _uiState.value = _uiState.value.copy(safeError = res.error)
        else if (res is ApiResult.Success) _uiState.value = _uiState.value.copy(accessRequest = res.value as? AccessRequestInfo)
    }
}
