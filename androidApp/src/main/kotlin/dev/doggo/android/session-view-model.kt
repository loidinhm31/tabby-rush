package dev.doggo.android
import android.util.Log
import android.content.Intent
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
import dev.doggo.shared.data.AccountLocalSettingsEntity
import dev.doggo.shared.contract.ReminderSettingsState
import dev.doggo.shared.contract.ReminderCapabilities
import dev.doggo.shared.ui.DeadlineCalculatorHelpers
import dev.doggo.shared.ui.DeadlineValidationResult
import dev.doggo.shared.contract.TaskDeadline
import dev.doggo.shared.contract.ReminderRule
import java.time.ZoneId
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

    companion object {
        private const val TAG = "SessionViewModel"
    }

    private val app = DoggoApplication.instance
    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    private val _surfaceCreateVisible = MutableStateFlow(false)
    val surfaceCreateVisible: StateFlow<Boolean> = _surfaceCreateVisible.asStateFlow()

    private val _surfaceSettingsVisible = MutableStateFlow(false)
    val surfaceSettingsVisible: StateFlow<Boolean> = _surfaceSettingsVisible.asStateFlow()

    private val _surfacePreferenceState = MutableStateFlow(TaskSurfacePreferenceState())
    val surfacePreferenceState: StateFlow<TaskSurfacePreferenceState> = _surfacePreferenceState.asStateFlow()

    private val _surfaceAvailability = MutableStateFlow(TaskSurfaceAvailability.OptedOut)
    val surfaceAvailability: StateFlow<TaskSurfaceAvailability> = _surfaceAvailability.asStateFlow()

    private var pendingIntentAction: (() -> Unit)? = null
    private var allTodos: List<Todo> = emptyList()
    private var isTodosLoaded = false; private var accountJob: Job? = null; private var syncJob: Job? = null; private var clockJob: Job? = null
    private var pendingTarget: Pair<String, String>? = null
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
                        _uiState.value = _uiState.value.copy(syncProgress = p, safeError = p.safeError)
                        if (p.conflictCount > 0) inspectAndShowConflict(runtime)
                        reloadTodos()
                    }
                }
                clockJob?.cancel()
                clockJob = launch {
                    while (true) {
                        _uiState.value = _uiState.value.copy(nowEpochMs = System.currentTimeMillis())
                        kotlinx.coroutines.delay(15_000L)
                    }
                }
                reloadTodos(); app.syncScheduler.schedulePeriodic(accountKey); runtime.coordinator.triggerSync()
            } catch (ce: CancellationException) { throw ce }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("INTERNAL_ERROR", "Storage operation failed")) }
        }
    }

    private fun onUnauthenticated() {
        accountJob?.cancel(); accountJob = null; syncJob?.cancel(); syncJob = null; clockJob?.cancel(); clockJob = null
        allTodos = emptyList(); isTodosLoaded = false; pendingIntentAction = null
        _surfaceCreateVisible.value = false; _surfaceSettingsVisible.value = false
        _uiState.value = TodoUiState()
    }

    private suspend fun reloadTodos() {
        val runtime = app.getActiveRuntime() ?: return
        try {
            val isV2 = runtime.store.isAccountV2()
            allTodos = runtime.store.loadTodos(); applyFilterAndCounts()
            _uiState.value = _uiState.value.copy(isAccountV2 = isV2)
            isTodosLoaded = true
            pendingTarget?.let { (k, t) -> dispatchTaskSelection(k, t) }
            pendingIntentAction?.let { action ->
                pendingIntentAction = null
                action()
            }
        } catch (ce: CancellationException) { throw ce } catch (_: Exception) {}
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
                is TodoAction.OpenCreate -> openCreate(action.initialTitle)
                is TodoAction.ChangeDraftTitle -> onTitleChanged(action.rawTitle)
                is TodoAction.UpdateEditorState -> _uiState.value = _uiState.value.copy(editor = action.editor)
                is TodoAction.SubmitEditor -> submitEditor()
                is TodoAction.CancelEditor -> _uiState.value = _uiState.value.copy(editor = null)
                is TodoAction.OpenEdit -> openEdit(action.todo)
                is TodoAction.OpenSettings -> openSettings()
                is TodoAction.CloseSettings -> _uiState.value = _uiState.value.copy(settings = null)
                is TodoAction.SaveSettings -> saveSettings(action.settings)
                is TodoAction.RequestExactAlarmPermission -> requestExactAlarmPermission()
                is TodoAction.OpenNotificationSettings -> openNotificationSettings()
                is TodoAction.ClockTick -> _uiState.value = _uiState.value.copy(nowEpochMs = action.nowEpochMs)
                is TodoAction.ToggleCompleted -> toggleCompleted(action.todo)
                is TodoAction.RequestDelete -> _uiState.value = _uiState.value.copy(deleteCandidate = action.todo)
                is TodoAction.ConfirmDelete -> confirmDelete()
                is TodoAction.CancelDelete -> _uiState.value = _uiState.value.copy(deleteCandidate = null)
                is TodoAction.TriggerSync -> triggerSync()
                is TodoAction.ReconcilePending -> app.getActiveRuntime()?.coordinator?.reconcilePending()
                is TodoAction.DiscardConflict -> resolveConflict { it.discardConflict(action.rowId) }
                is TodoAction.ReapplyConflict -> resolveConflict { it.reapplyConflict(action.rowId) }
                is TodoAction.CopyAsNew -> resolveConflict { it.copyAsNew(action.rowId) }
                is TodoAction.DismissError -> { app.getActiveRuntime()?.coordinator?.dismissError(); _uiState.value = _uiState.value.copy(safeError = null) }
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

    private suspend fun openCreate(initialTitle: String) {
        val runtime = app.getActiveRuntime()
        val settings = runtime?.store?.getLocalSettings() ?: AccountLocalSettingsEntity()
        val isV2 = runtime?.store?.isAccountV2() ?: true
        val localZone = try { ZoneId.systemDefault().id } catch (_: Exception) { "UTC" }
        _uiState.value = _uiState.value.copy(
            editor = EditorState(
                id = null,
                rawTitle = initialTitle,
                hasDeadline = false,
                deadlineDate = "",
                hasTime = false,
                deadlineTime = "",
                deadlineZone = localZone,
                reminderAnchorTime = settings.defaultDateOnlyAnchorTime,
                reminderEnabled = settings.defaultReminderEnabled,
                reminderLeadMinutes = settings.defaultReminderLeadMinutes,
                reminderIntervalMinutes = settings.defaultReminderIntervalMinutes,
                reminderCount = settings.defaultReminderCount,
                isAccountV2 = isV2
            )
        )
    }

    private suspend fun openEdit(todo: Todo) {
        val runtime = app.getActiveRuntime()
        val isV2 = runtime?.store?.isAccountV2() ?: true
        val localZone = try { ZoneId.systemDefault().id } catch (_: Exception) { "UTC" }
        val dl = todo.deadline
        val editor = if (dl != null) {
            EditorState(
                id = todo.id,
                rawTitle = todo.title,
                hasDeadline = true,
                deadlineDate = dl.date,
                hasTime = dl.time != null,
                deadlineTime = dl.time ?: "",
                deadlineZone = dl.zone,
                chosenOffsetSeconds = dl.offsetSeconds,
                reminderAnchorTime = dl.reminderAnchorTime ?: "09:00",
                reminderEnabled = todo.reminderRule.enabled,
                reminderLeadMinutes = todo.reminderRule.leadMinutes,
                reminderIntervalMinutes = todo.reminderRule.intervalMinutes,
                reminderCount = todo.reminderRule.count,
                isAccountV2 = isV2,
                originalDeadline = dl,
                originalReminderRule = todo.reminderRule,
                originalScheduleToken = todo.scheduleToken
            )
        } else {
            val settings = runtime?.store?.getLocalSettings() ?: AccountLocalSettingsEntity()
            EditorState(
                id = todo.id,
                rawTitle = todo.title,
                hasDeadline = false,
                deadlineDate = "",
                hasTime = false,
                deadlineTime = "",
                deadlineZone = localZone,
                reminderAnchorTime = settings.defaultDateOnlyAnchorTime,
                reminderEnabled = settings.defaultReminderEnabled,
                reminderLeadMinutes = settings.defaultReminderLeadMinutes,
                reminderIntervalMinutes = settings.defaultReminderIntervalMinutes,
                reminderCount = settings.defaultReminderCount,
                isAccountV2 = isV2,
                originalDeadline = null,
                originalReminderRule = ReminderRule.DISABLED,
                originalScheduleToken = null
            )
        }
        _uiState.value = _uiState.value.copy(editor = editor)
    }

    private suspend fun openSettings() {
        val runtime = app.getActiveRuntime()
        val settings = runtime?.store?.getLocalSettings() ?: AccountLocalSettingsEntity()
        val caps = AndroidReminderCapabilitiesChecker.checkCapabilities(
            app.applicationContext,
            AndroidNotificationIntentFactory.CHANNEL_ID
        )
        _uiState.value = _uiState.value.copy(
            settings = ReminderSettingsState(
                notificationsEnabled = settings.notificationsEnabled,
                defaultReminderEnabled = settings.defaultReminderEnabled,
                defaultReminderLeadMinutes = settings.defaultReminderLeadMinutes,
                defaultReminderIntervalMinutes = settings.defaultReminderIntervalMinutes,
                defaultReminderCount = settings.defaultReminderCount,
                defaultDateOnlyAnchorTime = settings.defaultDateOnlyAnchorTime,
                capabilities = ReminderCapabilities(
                    canScheduleExactAlarms = caps.canScheduleExactAlarms,
                    canPostNotifications = caps.canPostNotifications,
                    areNotificationsEnabled = caps.isChannelEnabled
                ),
                platformName = "Android"
            )
        )
    }

    private suspend fun saveSettings(s: ReminderSettingsState) {
        val runtime = app.getActiveRuntime() ?: return
        val entity = AccountLocalSettingsEntity(
            notificationsEnabled = s.notificationsEnabled,
            defaultReminderEnabled = s.defaultReminderEnabled,
            defaultReminderLeadMinutes = s.defaultReminderLeadMinutes,
            defaultReminderIntervalMinutes = s.defaultReminderIntervalMinutes,
            defaultReminderCount = s.defaultReminderCount,
            defaultDateOnlyAnchorTime = s.defaultDateOnlyAnchorTime
        )
        runtime.store.updateLocalSettings(entity)
        val readiness = app.taskSurfaces.access.readiness.value
        if (readiness is dev.doggo.shared.contract.SurfaceReadiness.Ready) {
            val surfacePrefs = TaskSurfacePreferences(app)
            surfacePrefs.setEnabled(readiness.token, s.notificationsEnabled)
            app.taskSurfaces.refresh(SurfaceRefreshReason.Capability)
        }
        _uiState.value = _uiState.value.copy(settings = null)
    }

    private fun openNotificationSettings() {
        try {
            val intent = ReminderPermissionController.createChannelNotificationSettingsIntent(app)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = ReminderPermissionController.createAppNotificationSettingsIntent(app)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
            } catch (_: Exception) {
                val intent = ReminderPermissionController.createAppDetailsSettingsIntent(app)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
            }
        }
    }

    private fun requestExactAlarmPermission() {
        try {
            val intent = ReminderPermissionController.createExactAlarmSettingsIntent(app)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (_: Exception) {
            val intent = ReminderPermissionController.createAppDetailsSettingsIntent(app)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }
    }

    private suspend fun submitEditor() {
        val editor = _uiState.value.editor ?: return
        val validation = TitlePolicy.normalizeAndValidate(editor.rawTitle)
        if (validation is ApiResult.Failure) {
            _uiState.value = _uiState.value.copy(editor = editor.copy(error = (validation.error as? SafeError.Validation)?.message ?: "Invalid title")); return
        }
        val title = (validation as ApiResult.Success).value
        val runtime = app.getActiveRuntime() ?: return

        val deadline: TaskDeadline?
        val rule: ReminderRule
        if (editor.hasDeadline) {
            if (!editor.isAccountV2) {
                _uiState.value = _uiState.value.copy(editor = editor.copy(error = "Strict-v2 account upgrade required to save deadlines or reminders."))
                return
            }
            when (val dRes = DeadlineCalculatorHelpers.validateAndComputeDeadline(editor)) {
                is DeadlineValidationResult.Valid -> {
                    deadline = dRes.deadline
                    rule = dRes.rule
                }
                is DeadlineValidationResult.Invalid -> {
                    _uiState.value = _uiState.value.copy(editor = editor.copy(error = dRes.message))
                    return
                }
                is DeadlineValidationResult.None -> {
                    deadline = null
                    rule = ReminderRule.DISABLED
                }
            }
        } else {
            deadline = null
            rule = ReminderRule.DISABLED
        }

        val now = System.currentTimeMillis()
        val token = if (deadline != null) (editor.originalScheduleToken ?: UUID.randomUUID().toString()) else null
        if (editor.id == null) {
            runtime.store.saveTodoLocally(
                Todo(
                    id = UUID.randomUUID().toString().lowercase(),
                    title = title,
                    completed = false,
                    createdAt = now,
                    updatedAt = now,
                    deadline = deadline,
                    reminderRule = rule,
                    scheduleToken = token
                )
            )
        } else {
            val existing = allTodos.firstOrNull { it.id == editor.id } ?: return
            runtime.store.saveTodoLocally(
                existing.copy(
                    title = title,
                    updatedAt = maxOf(now, existing.createdAt),
                    deadline = deadline,
                    reminderRule = rule,
                    scheduleToken = token
                )
            )
        }
        _uiState.value = _uiState.value.copy(editor = null)
        reloadTodos()
        triggerSync()
    }

    private suspend fun toggleCompleted(todo: Todo) {
        val runtime = app.getActiveRuntime() ?: return
        val desired = !todo.completed
        val now = System.currentTimeMillis()
        val change = runtime.store.setCompletedLocally(todo.id, desired, now)
        if (change == dev.doggo.shared.contract.CompletionChange.Changed) {
            reloadTodos()
            triggerSync()
        }
    }

    private suspend fun confirmDelete() {
        _uiState.value.deleteCandidate?.let { app.getActiveRuntime()?.store?.deleteTodoLocally(it.id) }
        _uiState.value = _uiState.value.copy(deleteCandidate = null); reloadTodos(); triggerSync()
    }

    private fun triggerSync() {
        app.getActiveRuntime()?.let { r -> viewModelScope.launch { r.coordinator.triggerSync(); app.syncScheduler.triggerImmediate(r.accountKey) } }
    }

    private suspend fun inspectAndShowConflict(runtime: AccountRuntime) {
        val entry = runtime.store.dao.getAllOutbox().firstOrNull {
            it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED
        } ?: return
        val base = runtime.store.dao.getServerBase(entry.rowId)
        val bDate = base?.deadlineDate
        val bZone = base?.deadlineZone
        val bOffset = base?.deadlineOffsetSeconds
        val bAnchor = base?.deadlineAnchorEpochMs
        val bOverdue = base?.deadlineOverdueEpochMs
        val remoteDeadline = if (bDate != null && bZone != null && bOffset != null && bAnchor != null && bOverdue != null) {
            TaskDeadline(
                date = bDate,
                time = base.deadlineTime,
                zone = bZone,
                offsetSeconds = bOffset,
                reminderAnchorTime = base.reminderAnchorTime,
                anchorEpochMs = bAnchor,
                overdueEpochMs = bOverdue
            )
        } else null
        val remoteRule = if (remoteDeadline != null && base != null) {
            ReminderRule(
                enabled = base.reminderEnabled,
                leadMinutes = base.reminderLeadMinutes,
                intervalMinutes = base.reminderIntervalMinutes,
                count = base.reminderCount
            )
        } else ReminderRule.DISABLED

        val remoteTodo = base?.let {
            Todo(
                id = it.rowId,
                title = it.title,
                completed = it.completed,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                serverRevision = it.serverRevision,
                isDeleted = it.deleted,
                deadline = remoteDeadline,
                reminderRule = remoteRule,
                scheduleToken = it.scheduleToken
            )
        }

        val eDate = entry.deadlineDate
        val eZone = entry.deadlineZone
        val eOffset = entry.deadlineOffsetSeconds
        val eAnchor = entry.deadlineAnchorEpochMs
        val eOverdue = entry.deadlineOverdueEpochMs
        val localDeadline = if (eDate != null && eZone != null && eOffset != null && eAnchor != null && eOverdue != null) {
            TaskDeadline(
                date = eDate,
                time = entry.deadlineTime,
                zone = eZone,
                offsetSeconds = eOffset,
                reminderAnchorTime = entry.reminderAnchorTime,
                anchorEpochMs = eAnchor,
                overdueEpochMs = eOverdue
            )
        } else null
        val localRule = if (localDeadline != null) {
            ReminderRule(
                enabled = entry.reminderEnabled,
                leadMinutes = entry.reminderLeadMinutes,
                intervalMinutes = entry.reminderIntervalMinutes,
                count = entry.reminderCount
            )
        } else ReminderRule.DISABLED

        val localTodo = allTodos.firstOrNull { it.id == entry.rowId } ?: Todo(
            id = entry.rowId,
            title = entry.title ?: "",
            completed = entry.completed ?: false,
            createdAt = entry.createdAt ?: 0L,
            updatedAt = entry.createdAt ?: 0L,
            deadline = localDeadline,
            reminderRule = localRule,
            scheduleToken = entry.scheduleToken
        )
        _uiState.value = _uiState.value.copy(
            conflict = ConflictState(
                rowId = entry.rowId,
                localTodo = localTodo,
                remoteTodo = remoteTodo,
                isUnknownSuperseded = entry.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED
            )
        )
    }

    private suspend fun resolveConflict(block: suspend (dev.doggo.shared.sync.AccountSyncCoordinator) -> Unit) {
        app.getActiveRuntime()?.let { block(it.coordinator); _uiState.value = _uiState.value.copy(conflict = null); reloadTodos() }
    }

    private suspend fun requestLeave() {
        val runtime = app.getActiveRuntime()
        if (runtime == null) { app.authSession.logout(); app.clearActiveAccount(); _uiState.value = TodoUiState(); return }
        val r = runtime.leaveProtocol.checkReviewWork()
        if (r.hasReviewWork) _uiState.value = _uiState.value.copy(leaveGuard = LeaveGuardState(r.pendingCount + r.draftCount, r.uncertainCount, r.conflictCount + r.unknownSupersededCount))
        else if (runtime.leaveProtocol.executeLeave()) { app.clearActiveAccount(); app.closeActiveRuntime() }
    }

    fun onAuthSubmit(action: AuthSubmitAction) = viewModelScope.launch {
        when (action) {
            is AuthSubmitAction.Login -> handleAuth(app.authSession.login(action.email, action.password))
            is AuthSubmitAction.Register -> handleAuth(app.authSession.register(action.username, action.email, action.password))
            is AuthSubmitAction.RequestAccess -> handleAccess(app.authSession.requestAccess(action.message))
            is AuthSubmitAction.RefreshStatus -> handleAccess(app.authSession.checkAccess())
            is AuthSubmitAction.Logout -> { pendingTarget = null; if (app.getActiveRuntime() != null) onAction(TodoAction.RequestLeave) else { app.authSession.logout(); app.clearActiveAccount(); _uiState.value = TodoUiState() } }
        }
    }

    private fun handleAuth(res: ApiResult<*>) { if (res is ApiResult.Failure) _uiState.value = _uiState.value.copy(safeError = res.error) }
    private fun handleAccess(res: ApiResult<*>) { if (res is ApiResult.Failure) _uiState.value = _uiState.value.copy(safeError = res.error) else if (res is ApiResult.Success) _uiState.value = _uiState.value.copy(accessRequest = res.value as? AccessRequestInfo) }
    fun selectTaskFromNotification(accountKey: String, taskId: String) = viewModelScope.launch {
        selectTaskFromIntent(accountKey, -1L, taskId)
    }

    fun selectTaskFromIntent(
        accountKey: String?,
        generation: Long,
        taskId: String,
        isSurfaceAction: Boolean = true
    ) = viewModelScope.launch {
        if (app.authSession.currentAccountKey.value?.value == null || !isTodosLoaded) {
            pendingIntentAction = {
                viewModelScope.launch {
                    dispatchTaskSelectionWithToken(accountKey, generation, taskId, isSurfaceAction)
                }
            }
            return@launch
        }
        dispatchTaskSelectionWithToken(accountKey, generation, taskId, isSurfaceAction)
    }

    private suspend fun dispatchTaskSelectionWithToken(
        accountKey: String?,
        generation: Long,
        taskId: String,
        isSurfaceAction: Boolean
    ) {
        val readiness = app.taskSurfaces.access.readiness.value
        if (readiness !is dev.doggo.shared.contract.SurfaceReadiness.Ready) return
        val currentToken = readiness.token
        if (accountKey.isNullOrBlank() || currentToken.accountKey.value != accountKey) {
            Log.w(TAG, "Rejecting task selection: mismatched or missing accountKey")
            return
        }
        if (isSurfaceAction) {
            if (generation < 0L || currentToken.generation != generation) {
                Log.w(TAG, "Rejecting surface task selection: mismatched or missing generation token ($generation vs ${currentToken.generation})")
                return
            }
        }
        val isValid = app.taskSurfaces.access.validateNavigation(currentToken, taskId)
        if (!isValid) return

        val task = allTodos.find { it.id == taskId && !it.isDeleted } ?: return
        openEdit(task)
    }

    fun openCreateFromIntent(accountKey: String?, generation: Long) = viewModelScope.launch {
        if (app.authSession.currentAccountKey.value?.value == null || !isTodosLoaded) {
            pendingIntentAction = {
                viewModelScope.launch { dispatchCreateWithToken(accountKey, generation) }
            }
            return@launch
        }
        dispatchCreateWithToken(accountKey, generation)
    }

    private suspend fun dispatchCreateWithToken(accountKey: String?, generation: Long) {
        val readiness = app.taskSurfaces.access.readiness.value
        if (readiness !is dev.doggo.shared.contract.SurfaceReadiness.Ready) return
        val currentToken = readiness.token
        if (accountKey.isNullOrBlank() || generation < 0L) {
            Log.w(TAG, "Rejecting create intent: missing accountKey or generation token")
            return
        }
        if (currentToken.accountKey.value != accountKey || currentToken.generation != generation) {
            Log.w(TAG, "Rejecting create intent: mismatched or stale surface token")
            return
        }
        val isValid = app.taskSurfaces.access.validateNavigation(currentToken, null)
        if (!isValid) return

        _surfaceCreateVisible.value = true
    }

    fun openSurfaceSettings() {
        loadSurfaceSettingsState()
        _surfaceSettingsVisible.value = true
    }

    fun closeSurfaceSettings() {
        _surfaceSettingsVisible.value = false
    }

    fun closeSurfaceCreate() {
        _surfaceCreateVisible.value = false
    }

    fun loadSurfaceSettingsState() = viewModelScope.launch {
        val key = app.authSession.currentAccountKey.value ?: return@launch
        val prefs = TaskSurfacePreferences(app)
        val state = prefs.read(key)
        _surfacePreferenceState.value = state
        _surfaceAvailability.value = TaskSurfacePermissionController.evaluateAvailability(
            context = app,
            enabled = state.enabled,
            channelId = TaskSurfaceIntents.CHANNEL_ID
        )
    }

    fun setSurfaceNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        val readiness = app.taskSurfaces.access.readiness.value
        if (readiness !is dev.doggo.shared.contract.SurfaceReadiness.Ready) return@launch
        val token = readiness.token
        val prefs = TaskSurfacePreferences(app)
        prefs.setEnabled(token, enabled)
        loadSurfaceSettingsState()
        app.taskSurfaces.refresh(SurfaceRefreshReason.Capability)
    }

    fun onNotificationPermissionResult(isGranted: Boolean) = viewModelScope.launch {
        loadSurfaceSettingsState()
        app.taskSurfaces.refresh(SurfaceRefreshReason.Capability)
    }

    fun onActivityResume() = viewModelScope.launch {
        loadSurfaceSettingsState()
        app.taskSurfaces.refresh(SurfaceRefreshReason.Capability)
    }

    fun createTaskFromSurface(rawTitle: String) = viewModelScope.launch {
        val validation = TitlePolicy.normalizeAndValidate(rawTitle)
        if (validation !is ApiResult.Success) return@launch
        val validTitle = validation.value

        val runtime = app.getActiveRuntime() ?: return@launch
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val newTodo = Todo(
            id = id,
            title = validTitle,
            completed = false,
            createdAt = now,
            updatedAt = now
        )
        runtime.store.saveTodoLocally(newTodo)
        _surfaceCreateVisible.value = false
        reloadTodos()
        triggerSync()
    }

    private suspend fun dispatchTaskSelection(accountKey: String, taskId: String) {
        val currentKey = app.authSession.currentAccountKey.value?.value; pendingTarget = null
        if (currentKey != accountKey || app.accountFiles.hasPendingWipe(dev.doggo.shared.contract.AccountKey(accountKey))) return
        val task = allTodos.find { it.id == taskId } ?: return
        openEdit(task)
    }
}
