package dev.tabbyrush.desktop

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.contract.CredentialVault
import dev.tabbyrush.shared.contract.AccessRequestInfo
import dev.tabbyrush.shared.contract.AccessLossEvidence
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AssignmentSlotResult
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.OwnerBindingState
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.UserLookupResult
import dev.tabbyrush.shared.contract.UserLookupCache
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.ConflictState
import dev.tabbyrush.shared.contract.EditorState
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.LeaveGuardState
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxState
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.ReminderSettingsState
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.TaskDataContract
import dev.tabbyrush.shared.contract.TitlePolicy
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoCounts
import dev.tabbyrush.shared.contract.TodoFilter
import dev.tabbyrush.shared.contract.TodoUiState
import dev.tabbyrush.shared.data.AccountLocalSettingsEntity
import dev.tabbyrush.shared.data.TabbyRushDatabase
import dev.tabbyrush.shared.data.JvmAccountFiles
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.ReminderOccurrenceStore
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.boundBindingKey
import dev.tabbyrush.shared.data.createJvmDatabaseBuilder
import dev.tabbyrush.shared.sync.AccountLeaveProtocol
import dev.tabbyrush.shared.sync.AccountSyncCoordinator
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import dev.tabbyrush.shared.sync.ReminderSchedulingCoordinator
import dev.tabbyrush.shared.theme.TabbyRushTheme
import dev.tabbyrush.shared.ui.AuthSubmitAction
import dev.tabbyrush.shared.ui.DeadlineCalculatorHelpers
import dev.tabbyrush.shared.ui.DeadlineValidationResult
import dev.tabbyrush.shared.ui.TabbyRushApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class DesktopAccountRuntime(
    val accountKey: AccountKey,
    val generation: Long,
    val token: SurfaceAccountToken,
    val database: TabbyRushDatabase,
    val store: RoomAccountStore,
    val coordinator: AccountSyncCoordinator,
    val reminderCoordinator: ReminderSchedulingCoordinator,
    val deliveryGate: ReminderDeliveryGate,
    val reminderScheduler: DesktopReminderScheduler,
    val scheduleObserverJob: Job,
    val surfaceObserverJob: Job,
    val leaveProtocol: AccountLeaveProtocol,
    val remoteRuntime: DesktopRemoteRuntime
)

class DesktopController(
    val mode: BuildMode,
    val scope: CoroutineScope,
    val instanceGuard: SingleInstanceGuard,
    val accountFiles: JvmAccountFiles = JvmAccountFiles(),
    val preferences: StickyWindowPreferences = StickyWindowPreferences(File(accountFiles.baseDir, "sticky-window.properties")),
    val platform: DesktopSurfacePlatform = DesktopSurfacePlatformDispatcher.current(),
    initialStickyPresentation: StickyWindowPresentation = preferences.load(),
    remoteAdapterOverride: RemoteNotificationAdapter? = null,
    vaultOverride: CredentialVault? = null
) {
    val dotEnv = DotEnvLoader.load(baseDir = accountFiles.baseDir)
    val origin = System.getenv("APPROVED_HUB_ORIGIN")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("tabbyrush.hub.origin")?.takeIf { it.isNotBlank() }
        ?: dotEnv["APPROVED_HUB_ORIGIN"]?.takeIf { it.isNotBlank() }
        ?: if (mode == BuildMode.DEBUG) "http://127.0.0.1:3000" else throw IllegalStateException(
            "Release mode requires an approved HTTPS origin. Set APPROVED_HUB_ORIGIN in environment or .env, or system property tabbyrush.hub.origin."
        )
    val apiKey = System.getenv("TABBYRUSH_API_KEY")?.takeIf { it.isNotBlank() }
        ?: dotEnv["TABBYRUSH_API_KEY"]?.takeIf { it.isNotBlank() }
        ?: if (mode == BuildMode.DEBUG) "" else throw IllegalStateException(
            "Release mode requires an application API key. Set TABBYRUSH_API_KEY in environment or .env."
        )
    val hubConfig = HubConfig(
        canonicalOrigin = origin,
        appId = "tabby-rush",
        appApiKey = apiKey,
        buildMode = mode
    )
    val vault: CredentialVault = vaultOverride ?: PlatformVaultDispatcher.createVault(accountFiles)
    val authSession = HubAuthSession(config = hubConfig, vault = vault)
    val reminderAdapter = PlatformReminderDispatcher.createAdapter()
    val remoteAdapter: RemoteNotificationAdapter = remoteAdapterOverride ?: DesktopRemotePlatformDispatcher.createAdapter(
        uiDispatcher = Dispatchers.Main,
        onActivationRequest = {
            showMainWindow()
        }
    )
    val syncScheduler = DesktopSyncScheduler(scope) { key ->
        activeRuntime?.takeIf { it.accountKey == key }?.coordinator?.triggerSync()
    }

    // Task surface integration
    val taskSurfaceAccess = DesktopTaskSurfaceAccess()
    private var currentStickyFontScale: Float = initialStickyPresentation.fontScale
    val stickyController = DesktopStickyTaskController(
        access = taskSurfaceAccess,
        scope = scope,
        onOpenApp = { showMainWindow() },
        onQuit = { quit() },
        onTriggerSync = { triggerSync() },
        initialFontScale = initialStickyPresentation.fontScale,
        onFontScaleChanged = { updateStickyFontScale(it) }
    )
    val stickyState = stickyController.stickyState

    // Window and tray presentation state
    private val isQuitting = AtomicBoolean(false)
    val isMainWindowVisible = MutableStateFlow(true)
    val isStickyWindowVisible = MutableStateFlow(initialStickyPresentation.visible)
    private val _isTrayUsable = MutableStateFlow(platform.traySupported())
    val isTrayUsable: StateFlow<Boolean> = _isTrayUsable.asStateFlow()
    private var currentStickyGeometry: StickyWindowGeometry = initialStickyPresentation.geometry
    private val presentationLock = Any()

    private val runtimeMutex = Mutex()
    private var activeRuntime: DesktopAccountRuntime? = null
    private var syncJob: Job? = null
    private var clockJob: Job? = null
    private val activeAccountFile = File(accountFiles.baseDir, "active_account.dat")
    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()
    private val _remoteInboxState = MutableStateFlow(RemoteInboxState(availability = RemoteInboxAvailability.SignedOut))
    val remoteInboxState: StateFlow<RemoteInboxState> = _remoteInboxState.asStateFlow()
    private var remoteJob: Job? = null
    private var allTodos: List<Todo> = emptyList()

    init {
        completePendingWipesOnStartup()
        scope.launch {
            authSession.state.collect { s ->
                _uiState.value = _uiState.value.copy(authState = s, identity = authSession.currentIdentity.value)
                if (s == AuthState.AUTHENTICATED) onAuthenticated() else if (s == AuthState.UNAUTHENTICATED) onUnauthenticated()
            }
        }
        scope.launch { checkAndRestoreEstablishedSession() }
    }

    fun updateStickyGeometry(geometry: StickyWindowGeometry) {
        if (isValidStickyGeometry(geometry)) {
            synchronized(presentationLock) {
                currentStickyGeometry = geometry
            }
        }
    }

    fun updateStickyFontScale(scale: Float) {
        if (isValidStickyFontScale(scale)) {
            synchronized(presentationLock) {
                currentStickyFontScale = scale
                flushStickyPresentationLocked()
            }
        }
    }

    fun flushStickyGeometry(): Unit = synchronized(presentationLock) {
        flushStickyPresentationLocked()
    }

    private fun flushStickyPresentationLocked() {
        preferences.save(
            StickyWindowPresentation(
                visible = isStickyWindowVisible.value,
                geometry = currentStickyGeometry,
                fontScale = currentStickyFontScale
            )
        )
    }

    fun showMainWindow() {
        isMainWindowVisible.value = true
        activeRuntime?.remoteRuntime?.onForeground()
        try {
            DefaultSwingDesktopUiRestorer().restoreWindow()
        } catch (_: Throwable) {}
    }

    fun hideMainWindow(): Boolean {
        // Refuse hide if tray is unavailable and sticky window is not visible
        if (!_isTrayUsable.value && !isStickyWindowVisible.value) {
            return false
        }
        isMainWindowVisible.value = false
        return true
    }

    fun toggleStickyWindow() {
        if (isStickyWindowVisible.value) {
            hideStickyWindow()
        } else {
            showStickyWindow()
        }
    }

    fun showStickyWindow() {
        isStickyWindowVisible.value = true
    }

    fun hideStickyWindow(): Boolean {
        // Refuse hide if tray is unavailable and main window is not visible
        if (!_isTrayUsable.value && !isMainWindowVisible.value) {
            return false
        }
        isStickyWindowVisible.value = false
        flushStickyGeometry()
        return true
    }

    fun onCloseMainWindow() {
        val hidden = hideMainWindow()
        if (!hidden) {
            _uiState.value = _uiState.value.copy(
                safeError = SafeError.Validation(
                    code = "TRAY_UNAVAILABLE",
                    message = "System tray is unavailable. Keep TabbyRush open or use explicit Quit."
                )
            )
        }
    }

    fun onCloseStickyWindow() {
        val hidden = hideStickyWindow()
        if (!hidden) {
            showMainWindow()
        }
    }

    fun onStickyAction(action: dev.tabbyrush.desktop.StickyTaskAction) {
        stickyController.onAction(action)
    }

    private fun completePendingWipesOnStartup() {
        try {
            accountFiles.baseDir.listFiles()?.filter { it.isDirectory && it.name.length == 64 }?.forEach { dir ->
                val key = AccountKey(dir.name)
                if (accountFiles.hasPendingWipe(key)) {
                    syncScheduler.cancel(key)
                    try {
                        runBlocking(Dispatchers.IO) { reminderAdapter.cancelAllForAccount(key) }
                    } catch (_: Exception) {
                        return@forEach // Fail-closed: retain wipe marker if reminder cancellation fails
                    }
                    if (accountFiles.deleteDatabaseAndArtifacts(key)) {
                        runBlocking(Dispatchers.IO) { vault.clear(key) }
                        accountFiles.clearWipeMarker(key)
                        if (activeAccountFile.exists() && activeAccountFile.readLines().firstOrNull() == key.value) {
                            activeAccountFile.delete()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun checkAndRestoreEstablishedSession() {
        if (!activeAccountFile.exists()) return
        try {
            val lines = activeAccountFile.readLines()
            if (lines.size >= 4) {
                val key = AccountKey(lines[0])
                val identity = AccountIdentity(lines[1], lines[2], lines[3])
                if (!accountFiles.hasPendingWipe(key) && authSession.restoreSession(identity, key)) {
                    getOrCreateAccountRuntime(key)
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveActiveAccount(identity: AccountIdentity, key: AccountKey) {
        try {
            val tmp = File(accountFiles.baseDir, "active_account.tmp")
            tmp.writeText("${key.value}\n${identity.userId}\n${identity.username}\n${identity.email}\n")
            Files.move(tmp.toPath(), activeAccountFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {}
    }

    fun clearActiveAccount() {
        try { activeAccountFile.delete() } catch (_: Exception) {}
        remoteJob?.cancel()
        remoteJob = null
        _remoteInboxState.value = RemoteInboxState(availability = RemoteInboxAvailability.SignedOut)
    }

    suspend fun getOrCreateAccountRuntime(accountKey: AccountKey): DesktopAccountRuntime = runtimeMutex.withLock {
        if (accountFiles.hasPendingWipe(accountKey)) throw IllegalStateException("Pending wipe marker exists for $accountKey")
        val gen = accountFiles.readGeneration(accountKey)
        val existing = activeRuntime
        if (existing != null && existing.accountKey == accountKey && existing.generation == gen) {
            return@withLock existing
        }
        if (existing != null) closeRuntimeInternal(existing)

        val token = SurfaceAccountToken(accountKey, gen)

        val identity = authSession.currentIdentity.value
            ?: throw IllegalStateException("Cannot create account runtime without authenticated AccountIdentity")
        val db = createJvmDatabaseBuilder(accountFiles.getDatabaseFile(accountKey)).build()
        val store = RoomAccountStore(accountKey, identity.userId, db, hubConfig.appId)
        val bindingState = store.initializeOwnerBinding(identity)
        if (bindingState != OwnerBindingState.BOUND) {
            try { db.close() } catch (_: Exception) {}
            throw IllegalStateException("Owner binding failed for $accountKey with identity ${identity.userId}")
        }
        val deliveryGate = ReminderDeliveryGate(
            accountKey = accountKey,
            getPersistedGeneration = { accountFiles.readGeneration(accountKey) },
            hasPendingWipeMarker = { accountFiles.hasPendingWipe(accountKey) }
        )
        val occurrenceStore = ReminderOccurrenceStore(accountKey = accountKey, dao = db.tabbyRushDao())
        val reminderCoord = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = reminderAdapter,
            deliveryGate = deliveryGate,
            getGeneration = { accountFiles.readGeneration(accountKey) },
            hasWipeMarker = { accountFiles.hasPendingWipe(accountKey) }
        )
        val reminderSched = DesktopReminderScheduler(
            accountKey = accountKey,
            reminderCoordinator = reminderCoord,
            scope = scope,
            ioDispatcher = Dispatchers.IO
        )
        if (reminderAdapter is DesktopReminderAdapter) {
            reminderAdapter.setScheduleListener { k, _, desiredAlarms ->
                if (k == accountKey) {
                    reminderSched.updateDesiredAlarms(desiredAlarms)
                }
            }
        }
        reminderSched.start()

        val scheduleObserverJob = scope.launch(Dispatchers.IO) {
            try {
                store.observePersistedSchedule().collect {
                    try {
                        reminderCoord.reconcile()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {}
                }
            } catch (ce: CancellationException) {
                // Expected cancellation on shutdown
            } catch (_: Exception) {
                // Gracefully handle database closure
            }
        }

        // Install token on desktop task surface access and start observing unfiltered tasks
        taskSurfaceAccess.onAccountAuthenticated(token, store)
        val surfaceObserverJob = stickyController.startObserving(token, store)

        val remoteRuntime = DesktopRemoteRuntime(
            config = hubConfig,
            authSession = authSession,
            adapter = remoteAdapter,
            scope = scope,
            ioDispatcher = Dispatchers.IO,
            uiDispatcher = Dispatchers.Main,
            onRestoreWindow = { showMainWindow() }
        )
        remoteRuntime.start(identity, gen)

        val compositeCancelGate = CompositeDesktopLeaveCancelGate(
            deliveryGate = deliveryGate,
            scheduleObserverJob = scheduleObserverJob,
            reminderScheduler = reminderSched,
            syncCancelGate = syncScheduler,
            reminderCancelGate = reminderCoord,
            surfaceObserverJob = surfaceObserverJob,
            surfaceCancelGate = taskSurfaceAccess,
            remoteCancelGate = remoteRuntime
        )

        lateinit var leaveProto: AccountLeaveProtocol
        val coord = AccountSyncCoordinator(store = store, session = authSession, leaveProtocolProvider = { leaveProto }).apply { setGeneration(gen) }
        leaveProto = AccountLeaveProtocol(
            store = store, session = authSession,
            onReadGeneration = { accountFiles.readGeneration(it) },
            onWriteGeneration = { k, g -> accountFiles.writeGeneration(k, g) },
            onWriteWipeMarker = { k, g -> accountFiles.writeWipeMarker(k, g) },
            onClearWipeMarker = { accountFiles.clearWipeMarker(it) },
            onDeleteDatabaseArtifacts = { k ->
                try { db.close() } catch (_: Exception) {}
                accountFiles.deleteDatabaseAndArtifacts(k)
            },
            schedulerCancelGate = compositeCancelGate
        )
        DesktopAccountRuntime(
            accountKey = accountKey,
            generation = gen,
            token = token,
            database = db,
            store = store,
            coordinator = coord,
            reminderCoordinator = reminderCoord,
            deliveryGate = deliveryGate,
            reminderScheduler = reminderSched,
            scheduleObserverJob = scheduleObserverJob,
            surfaceObserverJob = surfaceObserverJob,
            leaveProtocol = leaveProto,
            remoteRuntime = remoteRuntime
        ).also { activeRuntime = it }
    }

    private suspend fun closeRuntimeInternalSuspend(r: DesktopAccountRuntime) {
        try { r.deliveryGate.closeAndDrain() } catch (_: Exception) {}
        try { taskSurfaceAccess.closeAndDrain() } catch (_: Exception) {}
        try { r.surfaceObserverJob.cancelAndJoin() } catch (_: Exception) {}
        try { r.scheduleObserverJob.cancelAndJoin() } catch (_: Exception) {}
        try { r.reminderScheduler.cancelAndAwait(r.accountKey) } catch (_: Exception) {}
        try { r.reminderCoordinator.cancelAndAwait(r.accountKey) } catch (_: Exception) {}
        try { r.remoteRuntime.quiesce() } catch (_: Exception) {}
        try { r.store.isFrozen = true; r.database.close() } catch (_: Exception) {}
    }

    private fun closeRuntimeInternal(r: DesktopAccountRuntime) {
        runBlocking(Dispatchers.IO) {
            closeRuntimeInternalSuspend(r)
        }
    }

    private fun onAuthenticated() {
        val key = authSession.currentAccountKey.value ?: return
        val id = authSession.currentIdentity.value ?: return
        saveActiveAccount(id, key)
        scope.launch {
            try {
                val runtime = getOrCreateAccountRuntime(key)
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
                        delay(15_000L)
                    }
                }
                reloadTodos()
                syncScheduler.schedulePeriodic(key)
                triggerSync()
                remoteJob?.cancel()
                remoteJob = launch {
                    runtime.remoteRuntime.state.collect { remoteState ->
                        _remoteInboxState.value = remoteState
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("INTERNAL_ERROR", "Storage operation failed"))
            }
        }
    }

    private fun onUnauthenticated() {
        syncJob?.cancel()
        syncJob = null
        clockJob?.cancel()
        clockJob = null
        stickyController.stopObserving()
        remoteJob?.cancel()
        remoteJob = null
        _remoteInboxState.value = RemoteInboxState(availability = RemoteInboxAvailability.SignedOut)
        scope.launch { taskSurfaceAccess.onSignedOut() }
        activeRuntime?.let { closeRuntimeInternal(it) }
        activeRuntime = null
        allTodos = emptyList()
        _uiState.value = TodoUiState()
    }

    private suspend fun reloadTodos() {
        val r = activeRuntime ?: return
        try {
            allTodos = r.store.loadTodos()
            val evidence = r.store.listAccessLossEvidence()

            // 1. Authoritative access loss: purge active editor if affected
            val currentEditor = _uiState.value.editor
            if (currentEditor?.taskRef != null) {
                val currentTodo = allTodos.firstOrNull { it.taskRef == currentEditor.taskRef }
                if (currentTodo == null || currentTodo.isDeleted || currentTodo.accessState?.status == TaskAccessStatus.Unavailable || evidence.any { it.ref == currentEditor.taskRef }) {
                    _uiState.value = _uiState.value.copy(editor = null)
                }
            }

            // 2. Authoritative access loss: purge delete candidate if affected
            val currentDelete = _uiState.value.deleteCandidate
            if (currentDelete != null) {
                val currentTodo = allTodos.firstOrNull { it.taskRef == currentDelete.taskRef }
                if (currentTodo == null || currentTodo.isDeleted || currentTodo.accessState?.status == TaskAccessStatus.Unavailable || evidence.any { it.ref == currentDelete.taskRef }) {
                    _uiState.value = _uiState.value.copy(deleteCandidate = null)
                }
            }

            // 3. Authoritative access loss: purge conflict if affected
            val currentConflict = _uiState.value.conflict
            if (currentConflict != null && evidence.any { it.ref == currentConflict.taskRef }) {
                _uiState.value = _uiState.value.copy(conflict = null)
            }

            // 4. Cancel notifications and alarms for revoked tasks
            for (ev in evidence) {
                r.reminderScheduler.cancelAlarmsForTask(ev.ref)
                if (reminderAdapter is DesktopReminderAdapter) {
                    reminderAdapter.cancelNotificationsForTask(r.accountKey, ev.ref)
                }
            }

            applyFilterAndCounts(evidence)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {}
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

    fun onAction(action: TodoAction) = scope.launch {
        try {
            when (action) {
                is TodoAction.SelectFilter -> {
                    _uiState.value = _uiState.value.copy(filter = action.filter)
                    applyFilterAndCounts()
                }
                is TodoAction.OpenCreate -> openCreate(action.initialTitle, action.parentTaskRef, action.parentTitle)
                is TodoAction.ChangeDraftTitle -> onTitleChanged(action.rawTitle)
                is TodoAction.UpdateEditorState -> _uiState.value = _uiState.value.copy(editor = action.editor)
                is TodoAction.SubmitEditor -> submitEditor()
                is TodoAction.CancelEditor -> _uiState.value = _uiState.value.copy(editor = null)
                is TodoAction.OpenEdit -> openEdit(action.todo)
                is TodoAction.OpenSettings -> openSettings()
                is TodoAction.CloseSettings -> _uiState.value = _uiState.value.copy(settings = null)
                is TodoAction.SaveSettings -> saveSettings(action.settings)
                is TodoAction.RequestExactAlarmPermission -> { /* No-op on desktop */ }
                is TodoAction.OpenNotificationSettings -> openOsNotificationSettings()
                is TodoAction.ClockTick -> _uiState.value = _uiState.value.copy(nowEpochMs = action.nowEpochMs)
                is TodoAction.ToggleCompleted -> toggleCompleted(action.todo)
                is TodoAction.RequestDelete -> {
                    val store = activeRuntime?.store
                    val isOwner = store != null && action.todo.taskRef.ownerUserId == store.userId
                    val canDelete = if (isOwner) true else (action.todo.accessState?.canDeleteTask == true)
                    if (!canDelete) {
                        _uiState.value = _uiState.value.copy(
                            safeError = SafeError.OperationForbidden("PERMISSION_DENIED", "Cannot delete task", action.todo.taskRef)
                        )
                        return@launch
                    }
                    val childCount = store?.loadTodos()?.count { it.parentTaskRef == action.todo.taskRef && !it.isDeleted }
                        ?: allTodos.count { it.parentTaskRef == action.todo.taskRef && !it.isDeleted }
                    _uiState.value = _uiState.value.copy(deleteCandidate = action.todo, deleteCandidateChildCount = childCount)
                }
                is TodoAction.ConfirmDelete -> confirmDelete()
                is TodoAction.CancelDelete -> _uiState.value = _uiState.value.copy(deleteCandidate = null)
                is TodoAction.TriggerSync -> triggerSync()
                is TodoAction.ReconcilePending -> activeRuntime?.coordinator?.reconcilePending()
                is TodoAction.DiscardConflict -> resolveConflict { it.discardConflict(action.taskRef) }
                is TodoAction.ReapplyConflict -> resolveConflict { it.reapplyConflict(action.taskRef) }
                is TodoAction.CopyAsNew -> resolveConflict { it.copyAsNew(action.taskRef) }
                is TodoAction.AssignTask -> {
                    val r = activeRuntime ?: return@launch
                    val result = r.store.assignTaskLocally(action.intent)
                    when (result) {
                        is AssignmentSlotResult.Success -> {
                            reloadTodos()
                            triggerSync()
                        }
                        is AssignmentSlotResult.AccessDenied -> {
                            _uiState.value = _uiState.value.copy(
                                safeError = SafeError.OperationForbidden("PERMISSION_DENIED", "Cannot manage assignment for this task", action.intent.taskRef)
                            )
                        }
                        is AssignmentSlotResult.VersionConflict -> {
                            _uiState.value = _uiState.value.copy(
                                safeError = SafeError.Conflict("SLOT_CONFLICT", "Assignment slot conflict", action.intent.taskRef)
                            )
                            triggerSync()
                        }
                        is AssignmentSlotResult.InvalidStructure -> {
                            _uiState.value = _uiState.value.copy(
                                safeError = SafeError.Validation("INVALID_STRUCTURE", result.message)
                            )
                        }
                        AssignmentSlotResult.Missing -> {
                            _uiState.value = _uiState.value.copy(
                                safeError = SafeError.Validation("TASK_MISSING", "Task not found")
                            )
                        }
                        AssignmentSlotResult.Unchanged -> { /* No-op */ }
                    }
                }
                is TodoAction.DismissAccessLossNotice -> {
                    val r = activeRuntime ?: return@launch
                    r.store.dismissAccessLossEvidence(action.ref)
                    reloadTodos()
                }
                is TodoAction.DismissError -> {
                    _uiState.value = _uiState.value.copy(safeError = null)
                }
                is TodoAction.RequestLeave -> requestLeave()
                is TodoAction.ConfirmLeave -> {
                    if (activeRuntime?.leaveProtocol?.discardAndLeave() == true) {
                        clearActiveAccount()
                        closeActiveRuntime()
                        _uiState.value = _uiState.value.copy(leaveGuard = null)
                    }
                }
                is TodoAction.CancelLeave -> _uiState.value = _uiState.value.copy(leaveGuard = null)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("INTERNAL_ERROR", "Action execution failed"))
        }
    }

    fun onRemoteAction(action: RemoteInboxAction) {
        activeRuntime?.remoteRuntime?.dispatch(action)
    }

    suspend fun lookupUser(username: String): ApiResult<UserLookupResult> {
        return authSession.lookupUser(username)
    }

    private fun openOsNotificationSettings() {
        try {
            val osName = System.getProperty("os.name")?.lowercase() ?: ""
            if (osName.contains("win")) {
                ProcessBuilder("cmd", "/c", "start", "ms-settings:notifications").start()
            } else if (osName.contains("linux")) {
                ProcessBuilder("xdg-open", "gnome-control-center:notifications").start()
            }
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    private fun onTitleChanged(raw: String) {
        val res = TitlePolicy.normalizeAndValidate(raw)
        val err = (res as? ApiResult.Failure)?.let { (it.error as? SafeError.Validation)?.message ?: "Invalid title" }
        _uiState.value = _uiState.value.copy(editor = _uiState.value.editor?.copy(rawTitle = raw, error = err))
    }

    private suspend fun openCreate(initialTitle: String, parentTaskRef: TaskRef? = null, parentTitle: String? = null) {
        val runtime = activeRuntime
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
        val runtime = activeRuntime ?: return
        val currentTodo = runtime.store.getTodo(todo.taskRef) ?: todo
        if (currentTodo.isDeleted) return
        if (currentTodo.accessState?.status == TaskAccessStatus.Unavailable) return

        val isChildOnly = currentTodo.accessState?.coverage == HierarchyCoverage.ChildOnly
        val isOwner = currentTodo.taskRef.ownerUserId == runtime.store.userId
        val canManageAssignment = if (isOwner) true else (currentTodo.accessState?.canManageAssignment == true)
        val canEdit = if (isOwner) true else (currentTodo.accessState?.canEditContent == true)
        if (!canEdit) {
            _uiState.value = _uiState.value.copy(
                safeError = SafeError.OperationForbidden("PERMISSION_DENIED", "Cannot edit this task", currentTodo.taskRef)
            )
            return
        }

        val dl = currentTodo.deadline
        val parentTitle = if (!isChildOnly && currentTodo.parentTaskRef != null) {
            allTodos.firstOrNull { it.taskRef == currentTodo.parentTaskRef }?.title
        } else null
        val localZone = try { ZoneId.systemDefault().id } catch (_: Exception) { "UTC" }
        val assignment = runtime.store.getAssignmentSlot(currentTodo.taskRef)
        val editor = EditorState(
            taskRef = currentTodo.taskRef,
            rawTitle = currentTodo.title,
            hasDeadline = dl != null,
            deadlineDate = dl?.date ?: "",
            hasTime = dl?.time != null,
            deadlineTime = dl?.time ?: "",
            deadlineZone = dl?.zone ?: localZone,
            chosenOffsetSeconds = dl?.offsetSeconds,
            reminderAnchorTime = dl?.reminderAnchorTime ?: "09:00",
            reminderEnabled = currentTodo.reminderRule.enabled,
            reminderLeadMinutes = currentTodo.reminderRule.leadMinutes,
            reminderIntervalMinutes = currentTodo.reminderRule.intervalMinutes,
            reminderCount = currentTodo.reminderRule.count,
            originalDeadline = dl,
            originalReminderRule = currentTodo.reminderRule,
            originalScheduleToken = currentTodo.scheduleToken,
            parentTaskRef = if (!isChildOnly) currentTodo.parentTaskRef else null,
            parentTitle = parentTitle,
            assigneeUserId = assignment.recipientUserId,
            assigneeUsername = UserLookupCache.getUsername(assignment.recipientUserId),
            expectedSlotVersion = assignment.slotVersion,
            inheritedAssigneeUserId = assignment.inheritedRecipientUserId,
            inheritedAssigneeUsername = UserLookupCache.getUsername(assignment.inheritedRecipientUserId),
            canManageAssignment = canManageAssignment
        )
        _uiState.value = _uiState.value.copy(editor = editor)
    }

    private suspend fun openSettings() {
        val runtime = activeRuntime
        val settings = runtime?.store?.getLocalSettings() ?: AccountLocalSettingsEntity()
        val capabilities = runtime?.reminderCoordinator?.adapter?.queryCapabilities()
            ?: ReminderCapabilities(
                canScheduleExactAlarms = true,
                canPostNotifications = false,
                areNotificationsEnabled = false
            )
        _uiState.value = _uiState.value.copy(
            settings = ReminderSettingsState(
                notificationsEnabled = settings.notificationsEnabled,
                defaultReminderEnabled = settings.defaultReminderEnabled,
                defaultReminderLeadMinutes = settings.defaultReminderLeadMinutes,
                defaultReminderIntervalMinutes = settings.defaultReminderIntervalMinutes,
                defaultReminderCount = settings.defaultReminderCount,
                defaultDateOnlyAnchorTime = settings.defaultDateOnlyAnchorTime,
                capabilities = capabilities,
                platformName = "Desktop"
            )
        )
    }

    private suspend fun saveSettings(s: ReminderSettingsState) {
        val runtime = activeRuntime ?: return
        val entity = AccountLocalSettingsEntity(
            notificationsEnabled = s.notificationsEnabled,
            defaultReminderEnabled = s.defaultReminderEnabled,
            defaultReminderLeadMinutes = s.defaultReminderLeadMinutes,
            defaultReminderIntervalMinutes = s.defaultReminderIntervalMinutes,
            defaultReminderCount = s.defaultReminderCount,
            defaultDateOnlyAnchorTime = s.defaultDateOnlyAnchorTime
        )
        runtime.store.updateLocalSettings(entity)
        _uiState.value = _uiState.value.copy(settings = null)
    }

    private suspend fun submitEditor() {
        val editor = _uiState.value.editor ?: return
        val validation = TitlePolicy.normalizeAndValidate(editor.rawTitle)
        if (validation is ApiResult.Failure) {
            _uiState.value = _uiState.value.copy(editor = editor.copy(error = (validation.error as? SafeError.Validation)?.message ?: "Invalid title"))
            return
        }
        val title = (validation as ApiResult.Success).value
        val runtime = activeRuntime ?: return

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

        val now = System.currentTimeMillis()
        val token = if (deadline != null) (editor.originalScheduleToken ?: UUID.randomUUID().toString()) else null
        if (editor.taskRef == null) {
            val newRef = TaskRef(ownerUserId = runtime.store.userId, rowId = UUID.randomUUID().toString().lowercase())
            val newTodo = Todo(
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
            runtime.store.saveTodoLocally(newTodo)
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
            val targetRef = editor.taskRef ?: return
            val existing = runtime.store.getTodo(targetRef) ?: return
            val isOwner = existing.taskRef.ownerUserId == runtime.store.userId
            val canEdit = if (isOwner) true else (existing.accessState?.canEditContent == true)
            if (!canEdit) {
                _uiState.value = _uiState.value.copy(
                    editor = null,
                    safeError = SafeError.OperationForbidden("PERMISSION_DENIED", "Cannot edit revoked or unauthorized task", targetRef)
                )
                return
            }
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
        val r = activeRuntime ?: return
        val current = r.store.getTodo(todo.taskRef) ?: return
        val isOwner = current.taskRef.ownerUserId == r.store.userId
        val canEdit = if (isOwner) true else (current.accessState?.canEditContent == true)
        if (!canEdit) {
            _uiState.value = _uiState.value.copy(
                safeError = SafeError.OperationForbidden("PERMISSION_DENIED", "Cannot toggle completion on revoked task", current.taskRef)
            )
            return
        }
        val desired = !current.completed
        val change = r.store.setCompletedLocally(todo.taskRef, desired, System.currentTimeMillis())
        if (change == CompletionChange.Changed) {
            reloadTodos()
            triggerSync()
        }
    }

    private suspend fun confirmDelete() {
        val candidate = _uiState.value.deleteCandidate ?: return
        val r = activeRuntime ?: return
        val isOwner = candidate.taskRef.ownerUserId == r.store.userId
        val canDelete = if (isOwner) true else (candidate.accessState?.canDeleteTask == true)
        if (!canDelete) {
            _uiState.value = _uiState.value.copy(deleteCandidate = null)
            return
        }
        try {
            r.store.deleteTodoLocally(candidate.taskRef)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            System.err.println("Error deleting task ${candidate.taskRef}: ${e.message}")
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                deleteCandidate = null,
                safeError = SafeError.Internal("DELETE_FAILED", e.message ?: "Failed to delete task")
            )
            return
        }

        try {
            r.reminderScheduler.cancelAlarmsForTask(candidate.taskRef)
            if (reminderAdapter is DesktopReminderAdapter) {
                reminderAdapter.cancelNotificationsForTask(r.accountKey, candidate.taskRef)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            System.err.println("Non-fatal error canceling notifications for ${candidate.taskRef}: ${e.message}")
        }

        _uiState.value = _uiState.value.copy(deleteCandidate = null)
        reloadTodos()
        triggerSync()
    }

    private fun triggerSync() {
        activeRuntime?.let { r ->
            syncScheduler.triggerImmediate(r.accountKey)
        }
    }

    private suspend fun inspectAndShowConflict(runtime: DesktopAccountRuntime) {
        try {
            val entry = runtime.store.dao.getAllOutbox().firstOrNull {
                it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW
            } ?: return
            val owner = entry.ownerUserId ?: runtime.store.userId
            val boundKey = boundBindingKey(owner)
            val taskRef = TaskRef(ownerUserId = owner, rowId = entry.rowId)
            val base = runtime.store.dao.getServerBase(boundKey, TaskDataContract.DEFAULT_TABLE_NAME, entry.rowId)
                ?: runtime.store.dao.getServerBase(entry.rowId)
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
                parentTaskRef = it.parentTaskId?.let { pId -> TaskRef(ownerUserId = owner, rowId = pId) }
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
            parentTaskRef = entry.parentTaskId?.let { pId -> TaskRef(ownerUserId = owner, rowId = pId) }
        )
        val isPreservationConflict = entry.intentMask?.split("|")?.contains(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION) == true
        val isSubtaskConflict = entry.parentTaskId != null || localTodo.parentTaskRef != null
        val targetParentRef = entry.parentTaskId?.let { TaskRef(ownerUserId = owner, rowId = it) } ?: localTodo.parentTaskRef
        val parentBase = targetParentRef?.let {
            val pKey = boundBindingKey(it.ownerUserId)
            runtime.store.dao.getServerBase(pKey, TaskDataContract.DEFAULT_TABLE_NAME, it.rowId)
                ?: runtime.store.dao.getServerBase(it.rowId)
        }
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
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            System.err.println("Error inspecting conflict: ${e.message}")
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                safeError = SafeError.Internal("CONFLICT_INSPECTION_FAILED", "Failed to inspect conflict: ${e.message}")
            )
        }
    }

    private suspend fun resolveConflict(block: suspend (AccountSyncCoordinator) -> Unit) {
        activeRuntime?.let {
            block(it.coordinator)
            _uiState.value = _uiState.value.copy(conflict = null)
            reloadTodos()
        }
    }

    private suspend fun requestLeave() {
        val runtime = activeRuntime ?: run {
            authSession.logout()
            clearActiveAccount()
            _uiState.value = TodoUiState()
            return
        }
        val r = runtime.leaveProtocol.checkReviewWork()
        if (r.hasReviewWork) {
            _uiState.value = _uiState.value.copy(
                leaveGuard = LeaveGuardState(
                    pendingCount = r.pendingCount + r.draftCount,
                    uncertainCount = r.uncertainCount,
                    conflictCount = r.conflictCount + r.unknownSupersededCount
                )
            )
        } else if (runtime.leaveProtocol.executeLeave()) {
            clearActiveAccount()
            closeActiveRuntime()
            _uiState.value = _uiState.value.copy(leaveGuard = null)
        }
    }

    private suspend fun closeActiveRuntime() = runtimeMutex.withLock {
        activeRuntime?.let { closeRuntimeInternalSuspend(it) }
        activeRuntime = null
    }

    fun onAuthSubmit(action: AuthSubmitAction) = scope.launch {
        try {
            when (action) {
                is AuthSubmitAction.Login -> handleAuth(authSession.login(action.email, action.password))
                is AuthSubmitAction.Register -> handleAuth(authSession.register(action.username, action.email, action.password))
                is AuthSubmitAction.RequestAccess -> handleAccess(authSession.requestAccess(action.message))
                is AuthSubmitAction.RefreshStatus -> handleAccess(authSession.checkAccess())
                is AuthSubmitAction.Logout -> requestLeave()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("AUTH_FAILED", "Authentication failed"))
        }
    }

    private fun handleAuth(res: ApiResult<*>) {
        if (res is ApiResult.Failure) _uiState.value = _uiState.value.copy(safeError = res.error)
    }

    private fun handleAccess(res: ApiResult<*>) {
        if (res is ApiResult.Failure) _uiState.value = _uiState.value.copy(safeError = res.error)
        else if (res is ApiResult.Success) _uiState.value = _uiState.value.copy(accessRequest = res.value as? AccessRequestInfo)
    }

    fun quit(onExited: (() -> Unit)? = null) {
        if (!isQuitting.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val success = quitSuspend()
            if (success) {
                try { onExited?.invoke() } catch (_: Exception) {}
                exitProcess(0)
            }
        }
    }

    suspend fun quitSuspend(): Boolean {
        try {
            flushStickyGeometry()
        } catch (_: Exception) {}

        syncJob?.cancel()
        syncJob = null
        clockJob?.cancel()
        clockJob = null
        stickyController.stopObserving()

        try {
            withTimeoutOrNull(3000L) {
                taskSurfaceAccess.closeAndDrain(2500L)
            }
        } catch (e: Exception) {
            System.err.println("Error draining task surface access on quit: ${e.message}")
        }

        try {
            withTimeoutOrNull(3000L) {
                syncScheduler.disposeAndAwait()
            }
        } catch (e: Exception) {
            System.err.println("Error draining sync scheduler: ${e.message}")
        }

        try {
            withTimeoutOrNull(3000L) {
                activeRuntime?.let { closeRuntimeInternalSuspend(it) }
                remoteAdapter.close()
            }
        } catch (e: Exception) {
            System.err.println("Error draining active runtime and remote adapter: ${e.message}")
        }
        activeRuntime = null

        try {
            scope.cancel()
        } catch (_: Exception) {}

        try {
            instanceGuard.close()
        } catch (_: Exception) {}

        return true
    }

    suspend fun disposeSuspend() {
        quitSuspend()
    }

    fun dispose() {
        runBlocking(Dispatchers.IO) {
            try {
                withTimeoutOrNull(5000L) {
                    quitSuspend()
                }
            } catch (e: Exception) {
                System.err.println("Error disposing DesktopController: ${e.message}")
            } finally {
                try { instanceGuard.close() } catch (_: Exception) {}
            }
        }
    }
}

fun launchDesktop(
    mode: BuildMode,
    platform: DesktopSurfacePlatform = DesktopSurfacePlatformDispatcher.current(),
    onAppStarted: ((DesktopController) -> Unit)? = null
) {
    // Windows explicit AUMID setup before notifier/window initialization
    setCurrentProcessExplicitAppUserModelID("dev.tabbyrush.desktop")
    val accountFiles = JvmAccountFiles()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        System.err.println("FATAL UNCAUGHT EXCEPTION on thread ${thread.name}:")
        throwable.printStackTrace()
        try {
            File(accountFiles.baseDir, "desktop-crash.log").appendText(
                "=== FATAL CRASH ${java.time.Instant.now()} on thread ${thread.name} ===\n" +
                throwable.stackTraceToString() + "\n\n"
            )
        } catch (_: Exception) {}
    }
    val lockFile = File(accountFiles.baseDir, "tabbyrush.lock")
    val instanceGuard = SingleInstanceGuard.tryAcquire(lockFile)

    if (instanceGuard == null) {
        System.err.println("Another instance of TabbyRush is already running for this user profile. Exiting.")
        exitProcess(0)
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preferences = StickyWindowPreferences(File(accountFiles.baseDir, "sticky-window.properties"))
    val initialPresentation = preferences.load()
    val monitors = platform.monitorWorkAreas()
    val initialClampedGeometry = restoreStickyGeometry(initialPresentation.geometry, monitors)

    val controller = DesktopController(
        mode = mode,
        scope = appScope,
        instanceGuard = instanceGuard,
        accountFiles = accountFiles,
        preferences = preferences,
        platform = platform,
        initialStickyPresentation = initialPresentation.copy(geometry = initialClampedGeometry)
    )
    onAppStarted?.invoke(controller)
    println("[TabbyRush Desktop] Launched in $mode mode. Active canonicalOrigin: ${controller.hubConfig.canonicalOrigin}, appId: ${controller.hubConfig.appId}")

    application {
        val isTrayUsable by controller.isTrayUsable.collectAsState()
        val isStickyVisible by controller.isStickyWindowVisible.collectAsState()
        val isMainVisible by controller.isMainWindowVisible.collectAsState()

        if (isTrayUsable) {
            Tray(
                icon = painterResource("task-surface-tray.png"),
                tooltip = "TabbyRush",
                onAction = { controller.showMainWindow() },
                menu = {
                    Item(
                        text = "Open TabbyRush",
                        onClick = { controller.showMainWindow() }
                    )
                    Item(
                        text = if (isStickyVisible) "Hide Sticky Tasks" else "Show Sticky Tasks",
                        onClick = { controller.toggleStickyWindow() }
                    )
                    Separator()
                    Item(
                        text = "Quit",
                        onClick = {
                            controller.quit(onExited = { exitApplication() })
                        }
                    )
                }
            )
        }

        val appIcon = painterResource("tabbyrush-icon.png")
        val mainWindowState = rememberWindowState(width = 960.dp, height = 720.dp)
        Window(
            onCloseRequest = {
                controller.onCloseMainWindow()
            },
            visible = isMainVisible,
            title = "TabbyRush",
            icon = appIcon,
            state = mainWindowState
        ) {
            val state by controller.uiState.collectAsState()
            val remoteInboxState by controller.remoteInboxState.collectAsState()
            TabbyRushTheme {
                TabbyRushApp(
                    state = state,
                    remoteInboxState = remoteInboxState,
                    onAction = { controller.onAction(it) },
                    onRemoteAction = { controller.onRemoteAction(it) },
                    onAuthSubmit = { controller.onAuthSubmit(it) },
                    onLookupUser = { controller.lookupUser(it) }
                )
            }
        }

        val stickyWindowState = rememberWindowState(
            position = WindowPosition(initialClampedGeometry.xDp.dp, initialClampedGeometry.yDp.dp),
            size = DpSize(initialClampedGeometry.widthDp.dp, initialClampedGeometry.heightDp.dp)
        )

        LaunchedEffect(stickyWindowState.position, stickyWindowState.size) {
            if (!stickyWindowState.isMinimized) {
                controller.updateStickyGeometry(
                    StickyWindowGeometry(
                        xDp = stickyWindowState.position.x.value.toDouble(),
                        yDp = stickyWindowState.position.y.value.toDouble(),
                        widthDp = stickyWindowState.size.width.value.toDouble(),
                        heightDp = stickyWindowState.size.height.value.toDouble(),
                        savedScaleX = initialClampedGeometry.savedScaleX,
                        savedScaleY = initialClampedGeometry.savedScaleY
                    )
                )
            }
        }

        Window(
            onCloseRequest = {
                controller.onCloseStickyWindow()
            },
            visible = isStickyVisible,
            alwaysOnTop = true,
            title = "TabbyRush - Sticky Tasks",
            icon = appIcon,
            state = stickyWindowState
        ) {
            val stickyState by controller.stickyState.collectAsState()
            val windowWidthDp = stickyWindowState.size.width.value.toDouble()
            val effectiveScale = remember(windowWidthDp, stickyState.fontScale) {
                computeEffectiveStickyFontScale(windowWidthDp, stickyState.fontScale)
            }
            val currentDensity = LocalDensity.current
            val customDensity = remember(currentDensity.density, currentDensity.fontScale, effectiveScale) {
                Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale * effectiveScale
                )
            }
            @OptIn(ExperimentalMaterialApi::class)
            CompositionLocalProvider(
                LocalDensity provides customDensity,
                LocalMinimumInteractiveComponentEnforcement provides false
            ) {
                TabbyRushTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed) && !event.isAltPressed) {
                                    when {
                                        event.key == Key.Equals || event.key == Key.Plus || event.key == Key.NumPadAdd -> {
                                            controller.onStickyAction(StickyTaskAction.ZoomIn)
                                            true
                                        }
                                        event.key == Key.Minus || event.key == Key.NumPadSubtract -> {
                                            controller.onStickyAction(StickyTaskAction.ZoomOut)
                                            true
                                        }
                                        event.key == Key.Zero || event.key == Key.NumPad0 -> {
                                            controller.onStickyAction(StickyTaskAction.ResetZoom)
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                    ) {
                        StickyTaskContent(
                            state = stickyState,
                            onAction = { controller.onStickyAction(it) },
                            effectiveFontScale = effectiveScale
                        )
                    }
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    launchDesktop(BuildMode.RELEASE)
}

object DevelopmentLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        launchDesktop(BuildMode.DEBUG)
    }
}
