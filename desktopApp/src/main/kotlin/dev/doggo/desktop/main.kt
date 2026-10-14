package dev.doggo.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.contract.AccessRequestInfo
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.ConflictState
import dev.doggo.shared.contract.EditorState
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.LeaveGuardState
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.TitlePolicy
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.contract.TodoCounts
import dev.doggo.shared.contract.TodoFilter
import dev.doggo.shared.contract.TodoUiState
import dev.doggo.shared.data.DoggoDatabase
import dev.doggo.shared.data.JvmAccountFiles
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.data.createJvmDatabaseBuilder
import dev.doggo.shared.sync.AccountLeaveProtocol
import dev.doggo.shared.sync.AccountSyncCoordinator
import dev.doggo.shared.theme.DoggoTheme
import dev.doggo.shared.ui.AuthSubmitAction
import dev.doggo.shared.ui.DoggoApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.system.exitProcess

class DesktopAccountRuntime(
    val accountKey: AccountKey,
    val generation: Long,
    val database: DoggoDatabase,
    val store: RoomAccountStore,
    val coordinator: AccountSyncCoordinator,
    val leaveProtocol: AccountLeaveProtocol
)

class DesktopController(
    val mode: BuildMode,
    val scope: CoroutineScope,
    val instanceGuard: SingleInstanceGuard
) {
    val accountFiles = JvmAccountFiles()
    val origin = System.getenv("APPROVED_HUB_ORIGIN")
        ?: System.getProperty("doggo.hub.origin")
        ?: if (mode == BuildMode.DEBUG) "http://127.0.0.1:3000" else "https://hub.example.com"
    val hubConfig = HubConfig(
        canonicalOrigin = origin,
        appId = "doggo",
        appApiKey = System.getenv("DOGGO_API_KEY") ?: "",
        buildMode = mode
    )
    val vault = PlatformVaultDispatcher.createVault(accountFiles)
    val authSession = HubAuthSession(config = hubConfig, vault = vault)
    val syncScheduler = DesktopSyncScheduler(scope) { key ->
        activeRuntime?.takeIf { it.accountKey == key }?.coordinator?.triggerSync()
    }
    private val runtimeMutex = Mutex()
    private var activeRuntime: DesktopAccountRuntime? = null
    private val activeAccountFile = File(accountFiles.baseDir, "active_account.dat")
    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()
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

    private fun completePendingWipesOnStartup() {
        try {
            accountFiles.baseDir.listFiles()?.filter { it.isDirectory && it.name.length == 64 }?.forEach { dir ->
                val key = AccountKey(dir.name)
                if (accountFiles.hasPendingWipe(key)) {
                    syncScheduler.cancel(key)
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
    }

    suspend fun getOrCreateAccountRuntime(accountKey: AccountKey): DesktopAccountRuntime = runtimeMutex.withLock {
        if (accountFiles.hasPendingWipe(accountKey)) throw IllegalStateException("Pending wipe marker exists for $accountKey")
        val gen = accountFiles.readGeneration(accountKey)
        val existing = activeRuntime
        if (existing != null && existing.accountKey == accountKey && existing.generation == gen) {
            return@withLock existing
        }
        if (existing != null) closeRuntimeInternal(existing)

        val db = createJvmDatabaseBuilder(accountFiles.getDatabaseFile(accountKey)).build()
        val store = RoomAccountStore(accountKey, authSession.currentIdentity.value?.userId ?: "default", db, hubConfig.appId)
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
            schedulerCancelGate = syncScheduler
        )
        DesktopAccountRuntime(accountKey, gen, db, store, coord, leaveProto).also { activeRuntime = it }
    }

    private fun closeRuntimeInternal(r: DesktopAccountRuntime) {
        try { r.store.isFrozen = true; r.database.close() } catch (_: Exception) {}
    }

    private fun onAuthenticated() {
        val key = authSession.currentAccountKey.value ?: return
        val id = authSession.currentIdentity.value ?: return
        saveActiveAccount(id, key)
        scope.launch {
            try {
                getOrCreateAccountRuntime(key)
                reloadTodos()
                syncScheduler.schedulePeriodic(key)
                syncScheduler.triggerImmediate(key)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("INTERNAL_ERROR", "Storage operation failed"))
            }
        }
    }

    private fun onUnauthenticated() {
        activeRuntime?.let { closeRuntimeInternal(it) }
        activeRuntime = null
        allTodos = emptyList()
        _uiState.value = TodoUiState()
    }

    private suspend fun reloadTodos() {
        val r = activeRuntime ?: return
        try {
            allTodos = r.store.loadTodos()
            applyFilterAndCounts()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {}
    }

    private fun applyFilterAndCounts() {
        val total = allTodos.size
        val completed = allTodos.count { it.completed }
        val counts = TodoCounts(all = total, active = total - completed, completed = completed)
        val filtered = when (_uiState.value.filter) {
            TodoFilter.ALL -> allTodos
            TodoFilter.ACTIVE -> allTodos.filter { !it.completed }
            TodoFilter.COMPLETED -> allTodos.filter { it.completed }
        }
        _uiState.value = _uiState.value.copy(items = filtered, counts = counts)
    }

    fun onAction(action: TodoAction) = scope.launch {
        try {
            when (action) {
                is TodoAction.SelectFilter -> {
                    _uiState.value = _uiState.value.copy(filter = action.filter)
                    applyFilterAndCounts()
                }
                is TodoAction.OpenCreate -> _uiState.value = _uiState.value.copy(editor = EditorState(id = null, rawTitle = action.initialTitle))
                is TodoAction.ChangeDraftTitle -> onTitleChanged(action.rawTitle)
                is TodoAction.SubmitEditor -> submitEditor()
                is TodoAction.CancelEditor -> _uiState.value = _uiState.value.copy(editor = null)
                is TodoAction.OpenEdit -> _uiState.value = _uiState.value.copy(editor = EditorState(action.todo.id, action.todo.title))
                is TodoAction.ToggleCompleted -> toggleCompleted(action.todo)
                is TodoAction.RequestDelete -> _uiState.value = _uiState.value.copy(deleteCandidate = action.todo)
                is TodoAction.ConfirmDelete -> confirmDelete()
                is TodoAction.CancelDelete -> _uiState.value = _uiState.value.copy(deleteCandidate = null)
                is TodoAction.TriggerSync -> triggerSync()
                is TodoAction.ReconcilePending -> activeRuntime?.coordinator?.reconcilePending()
                is TodoAction.DiscardConflict -> resolveConflict { it.discardConflict(action.rowId) }
                is TodoAction.ReapplyConflict -> resolveConflict { it.reapplyConflict(action.rowId) }
                is TodoAction.CopyAsNew -> resolveConflict { it.copyAsNew(action.rowId) }
                is TodoAction.DismissError -> {
                    activeRuntime?.coordinator?.dismissError()
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

    private fun onTitleChanged(raw: String) {
        val res = TitlePolicy.normalizeAndValidate(raw)
        val err = (res as? ApiResult.Failure)?.let { (it.error as? SafeError.Validation)?.message ?: "Invalid title" }
        _uiState.value = _uiState.value.copy(editor = _uiState.value.editor?.copy(rawTitle = raw, error = err))
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
        val now = System.currentTimeMillis()
        if (editor.id == null) {
            runtime.store.saveTodoLocally(Todo(UUID.randomUUID().toString().lowercase(), title, false, now, now))
        } else {
            allTodos.firstOrNull { it.id == editor.id }?.let {
                runtime.store.saveTodoLocally(it.copy(title = title, updatedAt = maxOf(now, it.createdAt)))
            } ?: return
        }
        _uiState.value = _uiState.value.copy(editor = null)
        reloadTodos()
        triggerSync()
    }

    private suspend fun toggleCompleted(todo: Todo) {
        activeRuntime?.store?.saveTodoLocally(todo.copy(completed = !todo.completed, updatedAt = maxOf(System.currentTimeMillis(), todo.createdAt)))
        reloadTodos()
        triggerSync()
    }

    private suspend fun confirmDelete() {
        _uiState.value.deleteCandidate?.let { activeRuntime?.store?.deleteTodoLocally(it.id) }
        _uiState.value = _uiState.value.copy(deleteCandidate = null)
        reloadTodos()
        triggerSync()
    }

    private fun triggerSync() {
        activeRuntime?.let { r ->
            syncScheduler.triggerImmediate(r.accountKey)
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
        activeRuntime?.let { closeRuntimeInternal(it) }
        activeRuntime = null
    }

    fun onAuthSubmit(action: AuthSubmitAction) = scope.launch {
        try {
            when (action) {
                is AuthSubmitAction.Login -> authSession.login(action.email, action.password)
                is AuthSubmitAction.Register -> authSession.register(action.username, action.email, action.password)
                is AuthSubmitAction.RequestAccess -> authSession.requestAccess(action.message)
                is AuthSubmitAction.RefreshStatus -> authSession.checkAccess()
                is AuthSubmitAction.Logout -> requestLeave()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(safeError = SafeError.Internal("AUTH_FAILED", "Authentication failed"))
        }
    }

    fun dispose() {
        runBlocking {
            syncScheduler.disposeAndAwait()
        }
        activeRuntime?.let { closeRuntimeInternal(it) }
        activeRuntime = null
        scope.cancel()
        instanceGuard.close()
    }
}

fun launchDesktop(mode: BuildMode) {
    val accountFiles = JvmAccountFiles()
    val lockFile = File(accountFiles.baseDir, "doggo.lock")
    val instanceGuard = SingleInstanceGuard.tryAcquire(lockFile)

    if (instanceGuard == null) {
        System.err.println("Another instance of Doggo is already running for this user profile. Exiting.")
        exitProcess(0)
    }

    val swingScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    val controller = DesktopController(mode, swingScope, instanceGuard)

    application {
        Window(
            onCloseRequest = {
                controller.dispose()
                exitApplication()
            },
            title = "Doggo",
            state = WindowState(width = 960.dp, height = 720.dp)
        ) {
            val state by controller.uiState.collectAsState()
            DoggoTheme {
                DoggoApp(
                    state = state,
                    onAction = { controller.onAction(it) },
                    onAuthSubmit = { controller.onAuthSubmit(it) }
                )
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
