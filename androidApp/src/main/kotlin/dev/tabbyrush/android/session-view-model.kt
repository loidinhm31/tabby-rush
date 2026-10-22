package dev.tabbyrush.android
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tabbyrush.shared.contract.AccessRequestInfo
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.ConflictState
import dev.tabbyrush.shared.contract.EditorState
import dev.tabbyrush.shared.contract.LeaveGuardState
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.TitlePolicy
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoCounts
import dev.tabbyrush.shared.contract.TodoFilter
import dev.tabbyrush.shared.contract.TodoUiState
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.ui.AuthSubmitAction
import dev.tabbyrush.shared.data.AccountLocalSettingsEntity
import dev.tabbyrush.shared.contract.ReminderSettingsState
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.ui.DeadlineCalculatorHelpers
import dev.tabbyrush.shared.ui.DeadlineValidationResult
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxState
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.AssignmentIntent
import dev.tabbyrush.shared.contract.AssignmentSlotResult
import dev.tabbyrush.shared.contract.TaskMutationResult
import dev.tabbyrush.shared.contract.AccessLossEvidence
import dev.tabbyrush.shared.contract.UserLookupResult
import dev.tabbyrush.shared.contract.UserLookupCache
import java.time.ZoneId
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

    private val app = TabbyRushApplication.instance
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

    private val _remoteInboxState = MutableStateFlow(RemoteInboxState())
    val remoteInboxState: StateFlow<RemoteInboxState> = _remoteInboxState.asStateFlow()

    private var pendingIntentAction: (() -> Unit)? = null
    private var allTodos: List<Todo> = emptyList()
    private var isTodosLoaded = false; private var accountJob: Job? = null; private var syncJob: Job? = null; private var clockJob: Job? = null; private var remoteJob: Job? = null
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
        val identity = app.authSession.currentIdentity.value
        if (identity != null) {
            app.saveActiveAccount(identity, accountKey)
        }

        accountJob = viewModelScope.launch {
            try {
                val runtime = app.getOrCreateAccountRuntime(accountKey)
                if (identity != null) {
                    runtime.store.initializeOwnerBinding(identity)
                }
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
                remoteJob?.cancel()
                remoteJob = launch {
                    val controller = runtime.remoteController
                    if (controller != null) {
                        controller.state.collect { rState ->
                            _remoteInboxState.value = rState
                        }
                    } else {
                        _remoteInboxState.value = RemoteInboxState(availability = RemoteInboxAvailability.Offline)
                    }
                }
                reloadTodos(); app.syncScheduler.schedulePeriodic(accountKey); runtime.coordinator.triggerSync()
            } catch (ce: CancellationException) { throw ce }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("INTERNAL_ERROR", "Storage operation failed")) }
        }
    }

    private fun onUnauthenticated() {
        accountJob?.cancel(); accountJob = null; syncJob?.cancel(); syncJob = null; clockJob?.cancel(); clockJob = null; remoteJob?.cancel(); remoteJob = null
        allTodos = emptyList(); isTodosLoaded = false; pendingIntentAction = null
        _surfaceCreateVisible.value = false; _surfaceSettingsVisible.value = false
        _uiState.value = TodoUiState()
        _remoteInboxState.value = RemoteInboxState(availability = RemoteInboxAvailability.SignedOut)
    }

    private suspend fun reloadTodos() {
        val runtime = app.getActiveRuntime() ?: return
        try {
            allTodos = runtime.store.loadTodos()
            val evidence = runtime.store.listAccessLossEvidence()
            applyFilterAndCounts(evidence)
            isTodosLoaded = true

            // Clear in-memory revoked editor content on authoritative invalidation
            val currentEditor = _uiState.value.editor
            if (currentEditor?.taskRef != null) {
                val stillAccessible = allTodos.any { it.taskRef == currentEditor.taskRef && !it.isDeleted }
                if (!stillAccessible) {
                    _uiState.value = _uiState.value.copy(editor = null)
                }
            }

            pendingTarget?.let { (k, t) -> dispatchTaskSelection(k, t) }
            pendingIntentAction?.let { action ->
                pendingIntentAction = null
                action()
            }
        } catch (ce: CancellationException) { throw ce } catch (_: Exception) {}
    }

    private fun applyFilterAndCounts(evidence: List<AccessLossEvidence> = _uiState.value.accessLossEvidence) {
        val projection = dev.tabbyrush.shared.contract.TaskHierarchyProjector.projectHierarchy(
            todos = allTodos,
            filter = _uiState.value.filter
        )
        _uiState.value = _uiState.value.copy(
            items = projection.items.map { it.todo },
            hierarchicalItems = projection.items,
            counts = projection.counts,
            unresolvedChildren = projection.unresolvedChildren,
            accessLossEvidence = evidence
        )
    }

    fun onAction(action: TodoAction) = viewModelScope.launch {
        try {
            when (action) {
                is TodoAction.SelectFilter -> { _uiState.value = _uiState.value.copy(filter = action.filter); applyFilterAndCounts() }
                is TodoAction.OpenCreate -> openCreate(action.initialTitle, action.parentTaskRef, action.parentTitle)
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
                is TodoAction.RequestDelete -> {
                    val currentUserId = app.authSession.currentIdentity.value?.userId ?: ""
                    val isOwner = currentUserId.isNotEmpty() && action.todo.ownerUserId == currentUserId
                    val canDelete = if (isOwner) true else (action.todo.accessState?.canDeleteTask == true)
                    if (!canDelete) {
                        _uiState.value = _uiState.value.copy(safeError = SafeError.OperationForbidden(message = "Delete forbidden for assignee", taskRef = action.todo.taskRef))
                    } else {
                        val count = allTodos.count { it.parentTaskRef == action.todo.taskRef && !it.isDeleted }
                        _uiState.value = _uiState.value.copy(deleteCandidate = action.todo, deleteCandidateChildCount = count)
                    }
                }
                is TodoAction.ConfirmDelete -> confirmDelete()
                is TodoAction.CancelDelete -> _uiState.value = _uiState.value.copy(deleteCandidate = null)
                is TodoAction.TriggerSync -> triggerSync()
                is TodoAction.ReconcilePending -> app.getActiveRuntime()?.coordinator?.reconcilePending()
                is TodoAction.DiscardConflict -> resolveConflict { it.discardConflict(action.taskRef) }
                is TodoAction.ReapplyConflict -> resolveConflict { it.reapplyConflict(action.taskRef) }
                is TodoAction.CopyAsNew -> resolveConflict { it.copyAsNew(action.taskRef) }
                is TodoAction.DismissError -> { app.getActiveRuntime()?.coordinator?.dismissError(); _uiState.value = _uiState.value.copy(safeError = null) }
                is TodoAction.RequestLeave -> requestLeave()
                is TodoAction.ConfirmLeave -> if (app.getActiveRuntime()?.leaveProtocol?.discardAndLeave() == true) { app.clearActiveAccount(); app.closeActiveRuntime(); _uiState.value = _uiState.value.copy(leaveGuard = null) }
                is TodoAction.CancelLeave -> _uiState.value = _uiState.value.copy(leaveGuard = null)
                is TodoAction.AssignTask -> assignTask(action.intent)
                is TodoAction.DismissAccessLossNotice -> dismissAccessLossNotice(action.ref)
            }
        } catch (ce: CancellationException) { throw ce }
        catch (_: Exception) { _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("INTERNAL_ERROR", "Action execution failed")) }
    }

    private suspend fun assignTask(intent: AssignmentIntent) {
        val runtime = app.getActiveRuntime() ?: return
        when (val result = runtime.store.assignTaskLocally(intent)) {
            is AssignmentSlotResult.Success -> {
                reloadTodos()
                triggerSync()
            }
            is AssignmentSlotResult.VersionConflict -> {
                _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("VERSION_CONFLICT", "Assignment version conflict on task"))
                reloadTodos()
            }
            AssignmentSlotResult.Missing -> {
                _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("TASK_NOT_FOUND", "Task not found for assignment"))
                reloadTodos()
            }
            AssignmentSlotResult.AccessDenied -> {
                _uiState.value = _uiState.value.copy(safeError = SafeError.AccessDenied(taskRef = intent.taskRef))
                reloadTodos()
            }
            is AssignmentSlotResult.InvalidStructure -> {
                _uiState.value = _uiState.value.copy(safeError = SafeError.Validation("INVALID_STRUCTURE", result.message))
                reloadTodos()
            }
            AssignmentSlotResult.Unchanged -> { /* No-op */ }
        }
    }

    private suspend fun dismissAccessLossNotice(ref: TaskRef) {
        val runtime = app.getActiveRuntime() ?: return
        runtime.store.dismissAccessLossEvidence(ref)
        reloadTodos()
    }

    private fun onTitleChanged(raw: String) {
        val res = TitlePolicy.normalizeAndValidate(raw)
        val err = (res as? ApiResult.Failure)?.let { (it.error as? SafeError.Validation)?.message ?: "Invalid title" }
        _uiState.value = _uiState.value.copy(editor = _uiState.value.editor?.copy(rawTitle = raw, error = err))
    }

    private suspend fun openCreate(
        initialTitle: String,
        parentTaskRef: TaskRef? = null,
        parentTitle: String? = null
    ) {
        val runtime = app.getActiveRuntime()
        val settings = runtime?.store?.getLocalSettings() ?: AccountLocalSettingsEntity()
        val localZone = try { ZoneId.systemDefault().id } catch (_: Exception) { "UTC" }
        _uiState.value = _uiState.value.copy(
            editor = EditorState(
                taskRef = null,
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
                parentTaskRef = parentTaskRef,
                parentTitle = parentTitle,
                canManageAssignment = true
            )
        )
    }

    private suspend fun openEdit(todo: Todo) {
        val runtime = app.getActiveRuntime()
        val localZone = try { ZoneId.systemDefault().id } catch (_: Exception) { "UTC" }
        val dl = todo.deadline
        val parentTitle = if (todo.parentTaskRef != null) allTodos.firstOrNull { it.taskRef == todo.parentTaskRef }?.title else null
        val currentUserId = app.authSession.currentIdentity.value?.userId ?: ""
        val isOwner = currentUserId.isNotEmpty() && todo.ownerUserId == currentUserId
        val canManage = if (isOwner) true else (todo.accessState?.canManageAssignment == true)
        val canEdit = if (isOwner) true else (todo.accessState?.canEditContent == true)
        if (!canEdit) {
            _uiState.value = _uiState.value.copy(
                safeError = SafeError.OperationForbidden("PERMISSION_DENIED", "Cannot edit this task", todo.taskRef)
            )
            return
        }
        val assignment = runtime?.store?.getAssignmentSlot(todo.taskRef)
        val editor = if (dl != null) {
            EditorState(
                taskRef = todo.taskRef,
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
                originalDeadline = dl,
                originalReminderRule = todo.reminderRule,
                originalScheduleToken = todo.scheduleToken,
                parentTaskRef = todo.parentTaskRef,
                parentTitle = parentTitle,
                assigneeUserId = assignment?.recipientUserId,
                assigneeUsername = UserLookupCache.getUsername(assignment?.recipientUserId),
                expectedSlotVersion = assignment?.slotVersion ?: 0L,
                inheritedAssigneeUserId = assignment?.inheritedRecipientUserId,
                inheritedAssigneeUsername = UserLookupCache.getUsername(assignment?.inheritedRecipientUserId),
                canManageAssignment = canManage
            )
        } else {
            val settings = runtime?.store?.getLocalSettings() ?: AccountLocalSettingsEntity()
            EditorState(
                taskRef = todo.taskRef,
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
                originalDeadline = null,
                originalReminderRule = ReminderRule.DISABLED,
                originalScheduleToken = null,
                parentTaskRef = todo.parentTaskRef,
                parentTitle = parentTitle,
                assigneeUserId = assignment?.recipientUserId,
                assigneeUsername = UserLookupCache.getUsername(assignment?.recipientUserId),
                expectedSlotVersion = assignment?.slotVersion ?: 0L,
                inheritedAssigneeUserId = assignment?.inheritedRecipientUserId,
                inheritedAssigneeUsername = UserLookupCache.getUsername(assignment?.inheritedRecipientUserId),
                canManageAssignment = canManage
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
        if (readiness is dev.tabbyrush.shared.contract.SurfaceReadiness.Ready) {
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

        val currentUserId = app.authSession.currentIdentity.value?.userId ?: return
        val now = System.currentTimeMillis()
        val token = if (deadline != null) (editor.originalScheduleToken ?: UUID.randomUUID().toString()) else null
        if (editor.taskRef == null) {
            val newRef = TaskRef(currentUserId, UUID.randomUUID().toString().lowercase())
            val res = runtime.store.saveTodoLocally(
                Todo(
                    taskRef = newRef,
                    title = title,
                    completed = false,
                    createdAt = now,
                    updatedAt = now,
                    deadline = deadline,
                    reminderRule = rule,
                    scheduleToken = token,
                    parentTaskRef = editor.parentTaskRef
                )
            )
            if (res is TaskMutationResult.AccessDenied) {
                _uiState.value = _uiState.value.copy(safeError = SafeError.AccessDenied(taskRef = newRef))
                return
            }
            if (editor.assigneeUserId != null) {
                runtime.store.assignTaskLocally(
                    dev.tabbyrush.shared.contract.AssignmentIntent(
                        taskRef = newRef,
                        desiredRecipientUserId = editor.assigneeUserId,
                        expectedSlotVersion = 0L
                    )
                )
            }
        } else {
            val existing = allTodos.firstOrNull { it.taskRef == editor.taskRef } ?: return
            val isOwner = currentUserId.isNotEmpty() && existing.ownerUserId == currentUserId
            val canEdit = if (isOwner) true else (existing.accessState?.canEditContent == true)
            if (!canEdit) {
                _uiState.value = _uiState.value.copy(
                    editor = null,
                    safeError = SafeError.OperationForbidden(message = "Cannot edit revoked or unauthorized task", taskRef = existing.taskRef)
                )
                return
            }
            val res = runtime.store.saveTodoLocally(
                existing.copy(
                    title = title,
                    updatedAt = maxOf(now, existing.createdAt),
                    deadline = deadline,
                    reminderRule = rule,
                    scheduleToken = token,
                    parentTaskRef = editor.parentTaskRef
                )
            )
            if (res is TaskMutationResult.AccessDenied) {
                _uiState.value = _uiState.value.copy(safeError = SafeError.AccessDenied(taskRef = editor.taskRef))
                return
            }
        }
        _uiState.value = _uiState.value.copy(editor = null)
        reloadTodos()
        triggerSync()
    }

    private suspend fun toggleCompleted(todo: Todo) {
        val runtime = app.getActiveRuntime() ?: return
        val currentUserId = app.authSession.currentIdentity.value?.userId ?: ""
        val isOwner = currentUserId.isNotEmpty() && todo.ownerUserId == currentUserId
        val canEdit = if (isOwner) true else (todo.accessState?.canEditContent == true)
        if (!canEdit) {
            _uiState.value = _uiState.value.copy(
                safeError = SafeError.OperationForbidden(message = "Cannot toggle completion on revoked task", taskRef = todo.taskRef)
            )
            return
        }
        val desired = !todo.completed
        val now = System.currentTimeMillis()
        val change = runtime.store.setCompletedLocally(todo.taskRef, desired, now)
        if (change == dev.tabbyrush.shared.contract.CompletionChange.Changed) {
            reloadTodos()
            triggerSync()
        }
    }

    private suspend fun confirmDelete() {
        val candidate = _uiState.value.deleteCandidate ?: return
        val runtime = app.getActiveRuntime() ?: return
        val currentUserId = app.authSession.currentIdentity.value?.userId ?: ""
        val isOwner = currentUserId.isNotEmpty() && candidate.ownerUserId == currentUserId
        val canDelete = if (isOwner) true else (candidate.accessState?.canDeleteTask == true)
        if (!canDelete) {
            _uiState.value = _uiState.value.copy(deleteCandidate = null)
            return
        }
        runtime.store.deleteTodoLocally(candidate.taskRef)
        _uiState.value = _uiState.value.copy(deleteCandidate = null)
        reloadTodos()
        triggerSync()
    }

    private fun triggerSync() {
        app.getActiveRuntime()?.let { r ->
            viewModelScope.launch {
                r.coordinator.triggerSync()
                app.syncScheduler.triggerImmediate(r.accountKey)
            }
        }
    }

    private suspend fun inspectAndShowConflict(runtime: AccountRuntime) {
        val entry = runtime.store.dao.getAllOutbox().firstOrNull {
            it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW
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

        val ownerUserId = entry.ownerUserId ?: (app.authSession.currentIdentity.value?.userId ?: "")
        val taskRef = TaskRef(ownerUserId = ownerUserId, rowId = entry.rowId)
        val parentTaskRef = entry.parentTaskId?.let { TaskRef(ownerUserId = ownerUserId, rowId = it) }
        val remoteTodo = base?.let {
            Todo(
                taskRef = taskRef,
                title = it.title,
                completed = it.completed,
                createdAt = it.createdAt,
                updatedAt = if (it.updatedAt >= it.createdAt) it.updatedAt else it.createdAt,
                serverRevision = it.serverRevision,
                isDeleted = it.deleted,
                deadline = remoteDeadline,
                reminderRule = remoteRule,
                scheduleToken = if (remoteDeadline != null) it.scheduleToken else null,
                parentTaskRef = parentTaskRef
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

        val localTodo = allTodos.firstOrNull { it.taskRef == taskRef } ?: Todo(
            taskRef = taskRef,
            title = entry.title ?: "",
            completed = entry.completed ?: false,
            createdAt = entry.createdAt ?: 0L,
            updatedAt = entry.createdAt ?: 0L,
            deadline = localDeadline,
            reminderRule = localRule,
            scheduleToken = if (localDeadline != null) entry.scheduleToken else null,
            parentTaskRef = parentTaskRef
        )
        val isPreservationConflict = entry.intentMask?.split("|")?.contains(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION) == true
        val isSubtaskConflict = entry.parentTaskId != null || localTodo.parentTaskRef != null
        val targetParentRef = parentTaskRef ?: localTodo.parentTaskRef
        val parentBase = targetParentRef?.let { runtime.store.dao.getServerBase(it.rowId) }
        val isParentDeleted = isSubtaskConflict && (parentBase == null || parentBase.deleted)
        _uiState.value = _uiState.value.copy(
            conflict = ConflictState(
                taskRef = taskRef,
                localTodo = localTodo,
                remoteTodo = remoteTodo,
                requiresDeliveryReview = entry.deliveryState == OutboxEntity.STATE_REVIEW,
                isPreservationConflict = isPreservationConflict,
                isSubtaskConflict = isSubtaskConflict,
                isParentDeleted = isParentDeleted
            )
        )
    }

    suspend fun lookupUser(username: String): ApiResult<UserLookupResult> {
        return app.authSession.lookupUser(username)
    }

    private suspend fun resolveConflict(block: suspend (dev.tabbyrush.shared.sync.AccountSyncCoordinator) -> Unit) {
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
    fun selectTaskFromNotification(accountKey: String, taskRef: TaskRef) = viewModelScope.launch {
        selectTaskFromIntent(accountKey, -1L, taskRef)
    }

    fun selectTaskFromIntent(
        accountKey: String?,
        generation: Long,
        taskRef: TaskRef,
        isSurfaceAction: Boolean = true
    ) = viewModelScope.launch {
        if (app.authSession.currentAccountKey.value?.value == null || !isTodosLoaded) {
            pendingIntentAction = {
                viewModelScope.launch {
                    dispatchTaskSelectionWithToken(accountKey, generation, taskRef, isSurfaceAction)
                }
            }
            return@launch
        }
        dispatchTaskSelectionWithToken(accountKey, generation, taskRef, isSurfaceAction)
    }

    private suspend fun dispatchTaskSelectionWithToken(
        accountKey: String?,
        generation: Long,
        taskRef: TaskRef,
        isSurfaceAction: Boolean
    ) {
        val readiness = app.taskSurfaces.access.readiness.value
        if (readiness !is dev.tabbyrush.shared.contract.SurfaceReadiness.Ready) return
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
        val isValid = app.taskSurfaces.access.validateNavigation(currentToken, taskRef)
        if (!isValid) return

        val task = allTodos.find { it.taskRef == taskRef && !it.isDeleted } ?: return
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
        if (readiness !is dev.tabbyrush.shared.contract.SurfaceReadiness.Ready) return
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
        if (readiness !is dev.tabbyrush.shared.contract.SurfaceReadiness.Ready) return@launch
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
        app.getActiveRuntime()?.remoteController?.onForeground()
    }

    fun onRemoteAction(action: RemoteInboxAction) {
        val runtime = app.getActiveRuntime()
        runtime?.remoteController?.dispatch(action)
    }

    fun selectAnnouncementFromIntent(messageId: String, generation: Long) = viewModelScope.launch {
        if (app.authSession.state.value == AuthState.UNAUTHENTICATED) {
            // Signed out: do not retain intent target to auto-open across unrelated login
            return@launch
        }

        if (app.authSession.currentAccountKey.value == null || app.getActiveRuntime() == null) {
            pendingIntentAction = {
                viewModelScope.launch {
                    dispatchAnnouncementSelection(messageId, generation)
                }
            }
            return@launch
        }
        dispatchAnnouncementSelection(messageId, generation)
    }

    private fun dispatchAnnouncementSelection(messageId: String, generation: Long) {
        val currentRuntime = app.getActiveRuntime() ?: return
        if (generation >= 0L && currentRuntime.generation != generation) {
            Log.w(TAG, "Rejecting announcement intent: mismatched generation ($generation vs ${currentRuntime.generation})")
            return
        }
        currentRuntime.remoteController?.onNativeOpen(messageId, currentRuntime.generation)
    }

    fun createTaskFromSurface(rawTitle: String) = viewModelScope.launch {
        val validation = TitlePolicy.normalizeAndValidate(rawTitle)
        if (validation !is ApiResult.Success) return@launch
        val validTitle = validation.value

        val runtime = app.getActiveRuntime() ?: return@launch
        val currentUserId = app.authSession.currentIdentity.value?.userId ?: return@launch
        val newRef = TaskRef(ownerUserId = currentUserId, rowId = UUID.randomUUID().toString().lowercase())
        val now = System.currentTimeMillis()
        val newTodo = Todo(
            taskRef = newRef,
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
        if (currentKey != accountKey || app.accountFiles.hasPendingWipe(dev.tabbyrush.shared.contract.AccountKey(accountKey))) return
        val task = allTodos.find { it.id == taskId } ?: return
        openEdit(task)
    }
}
