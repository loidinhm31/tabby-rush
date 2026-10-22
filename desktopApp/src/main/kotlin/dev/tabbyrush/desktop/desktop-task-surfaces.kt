package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.SurfaceSnapshot
import dev.tabbyrush.shared.contract.TaskSections
import dev.tabbyrush.shared.contract.TaskSurfaceAccess
import dev.tabbyrush.shared.contract.TitlePolicy
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.partitionSurfaceTodos
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.sync.LeaveSchedulerCancelGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Creates a default [Todo] task with standard UUID, current timestamps, incomplete status,
 * no deadline, and disabled reminder rule. Used by both main create and compact sticky quick-add.
 */
fun createDefaultTodo(
    title: String,
    nowEpochMs: Long = System.currentTimeMillis(),
    taskRef: TaskRef
): Todo {
    return Todo(
        taskRef = taskRef,
        title = title,
        completed = false,
        createdAt = nowEpochMs,
        updatedAt = nowEpochMs,
        deadline = null,
        reminderRule = ReminderRule.DISABLED,
        scheduleToken = null
    )
}

fun createDefaultTodo(
    title: String,
    nowEpochMs: Long = System.currentTimeMillis(),
    ownerUserId: String,
    rowId: String = UUID.randomUUID().toString().lowercase()
): Todo = createDefaultTodo(
    title = title,
    nowEpochMs = nowEpochMs,
    taskRef = TaskRef(ownerUserId = ownerUserId, rowId = rowId)
)

enum class SurfaceGateState {
    OPEN,
    CLOSING,
    CLOSED
}

/**
 * Production Desktop implementation of [TaskSurfaceAccess] and [LeaveSchedulerCancelGate].
 *
 * Guarantees:
 * 1. Current token validation on all operations; stale token returns [SurfaceResult.Rejected].
 * 2. Atomic admission gating with non-cancellable in-flight count tracking.
 * 3. 4-second bounded [awaitReadiness] returning [SurfaceReadiness.Unavailable] on timeout.
 * 4. Safe drain on account leave before database closure; timeout throws to fail-closed.
 * 5. Serialized [publishIfCurrent] boundary under a dedicated publication mutex.
 */
class DesktopTaskSurfaceAccess : TaskSurfaceAccess, LeaveSchedulerCancelGate {

    private val stateMutex = Mutex()
    private val publicationMutex = Mutex()

    private val _readiness = MutableStateFlow<SurfaceReadiness>(SurfaceReadiness.Restoring)
    override val readiness: StateFlow<SurfaceReadiness> = _readiness.asStateFlow()

    private var gateState = SurfaceGateState.OPEN
    private var inFlightCount = 0
    private var drainSignal: CompletableDeferred<Unit>? = null
    @kotlin.jvm.Volatile
    private var currentToken: SurfaceAccountToken? = null
    private var activeStore: RoomAccountStore? = null
    /**
     * Waits up to [timeoutMs] for readiness to resolve from Restoring.
     */
    override suspend fun awaitReadiness(timeoutMs: Long): SurfaceReadiness {
        val current = _readiness.value
        if (current !is SurfaceReadiness.Restoring) {
            return current
        }
        return withTimeoutOrNull(timeoutMs) {
            _readiness.first { it !is SurfaceReadiness.Restoring }
        } ?: SurfaceReadiness.Unavailable
    }

    /**
     * Executes [block] under admission checking token match, open gate state, and
     * incrementing in-flight count with non-cancellable decrement in finally.
     */
    suspend fun <T> withSurfaceAdmission(
        token: SurfaceAccountToken,
        block: suspend (RoomAccountStore) -> T
    ): SurfaceResult<T> {
        val store: RoomAccountStore
        stateMutex.withLock {
            if (gateState != SurfaceGateState.OPEN) {
                return SurfaceResult.Rejected
            }
            val ready = _readiness.value
            if (ready !is SurfaceReadiness.Ready || ready.token != token || currentToken != token) {
                return SurfaceResult.Rejected
            }
            store = activeStore ?: return SurfaceResult.Unavailable
            inFlightCount++
        }

        try {
            val result = block(store)
            return SurfaceResult.Value(result)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            return SurfaceResult.Unavailable
        } finally {
            withContext(NonCancellable) {
                val signal: CompletableDeferred<Unit>?
                stateMutex.withLock {
                    inFlightCount--
                    if (gateState != SurfaceGateState.OPEN && inFlightCount == 0) {
                        signal = drainSignal
                    } else {
                        signal = null
                    }
                }
                signal?.complete(Unit)
            }
        }
    }

    override suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot> {
        return withSurfaceAdmission(token) { store ->
            val todos = store.loadTodos()
            val sections = partitionSurfaceTodos(todos)
            val schedules = store.getPersistedScheduleProjection()
            SurfaceSnapshot(
                token = token,
                sections = sections,
                schedules = schedules
            )
        }
    }

    override suspend fun setCompleted(
        token: SurfaceAccountToken,
        taskRef: TaskRef,
        completed: Boolean,
        nowEpochMs: Long
    ): SurfaceResult<CompletionChange> {
        return withSurfaceAdmission(token) { store ->
            store.setCompletedLocally(taskRef, completed, nowEpochMs)
        }
    }

    override suspend fun publishIfCurrent(
        token: SurfaceAccountToken,
        publish: suspend () -> Unit
    ): Boolean = publicationMutex.withLock {
        val isCurrent = stateMutex.withLock {
            if (gateState != SurfaceGateState.OPEN) return@withLock false
            val ready = _readiness.value
            if (ready !is SurfaceReadiness.Ready || ready.token != token || currentToken != token) {
                return@withLock false
            }
            true
        }
        if (!isCurrent) {
            return@withLock false
        }
        publish()
        true
    }

    override suspend fun validateNavigation(token: SurfaceAccountToken, taskRef: TaskRef?): Boolean {
        return stateMutex.withLock {
            if (gateState != SurfaceGateState.OPEN) return@withLock false
            val ready = _readiness.value
            if (ready !is SurfaceReadiness.Ready || ready.token != token || currentToken != token) {
                return@withLock false
            }
            if (taskRef == null) {
                true
            } else {
                val store = activeStore ?: return@withLock false
                try {
                    val todos = store.loadTodos()
                    todos.any { it.taskRef == taskRef && !it.isDeleted }
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    suspend fun onAccountAuthenticated(token: SurfaceAccountToken, store: RoomAccountStore) {
        stateMutex.withLock {
            check(gateState != SurfaceGateState.CLOSING) { "Cannot authenticate while surface is draining" }
            gateState = SurfaceGateState.OPEN
            drainSignal = null
            currentToken = token
            activeStore = store
            _readiness.value = SurfaceReadiness.Ready(token)
        }
    }

    suspend fun onSignedOut() {
        stateMutex.withLock {
            currentToken = null
            activeStore = null
            _readiness.value = SurfaceReadiness.SignedOut
        }
    }

    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        val drained = closeAndDrain(5000L)
        if (!drained) {
            throw IllegalStateException("Timed out draining desktop task surface operations for $accountKey")
        }
    }

    suspend fun closeAndDrain(timeoutMs: Long = 5000L): Boolean {
        val signal: CompletableDeferred<Unit>
        stateMutex.withLock {
            if (gateState == SurfaceGateState.CLOSED) return true
            gateState = SurfaceGateState.CLOSING
            _readiness.value = SurfaceReadiness.Unavailable
            currentToken = null
            activeStore = null
            if (inFlightCount == 0) {
                gateState = SurfaceGateState.CLOSED
                drainSignal = null
                return true
            }
            signal = drainSignal ?: CompletableDeferred<Unit>().also { drainSignal = it }
        }

        val drained = withTimeoutOrNull(timeoutMs) {
            signal.await()
            true
        } ?: false

        return stateMutex.withLock {
            if (drained && inFlightCount == 0) {
                gateState = SurfaceGateState.CLOSED
                drainSignal = null
                true
            } else {
                false
            }
        }
    }

    fun getCurrentToken(): SurfaceAccountToken? = currentToken
}

/**
 * Controller managing sticky task state, independent quick-add input,
 * unfiltered reactive task projection, and action delegation.
 */
class DesktopStickyTaskController(
    val access: DesktopTaskSurfaceAccess,
    private val scope: CoroutineScope,
    private val onOpenApp: () -> Unit,
    private val onQuit: () -> Unit,
    private val onTriggerSync: () -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
    initialFontScale: Float = DEFAULT_STICKY_FONT_SCALE,
    private val onFontScaleChanged: ((Float) -> Unit)? = null
) {

    private val _stickyState = MutableStateFlow(
        StickyTaskState(
            readiness = access.readiness.value,
            sections = TaskSections(emptyList(), emptyList()),
            quickAdd = StickyQuickAddState(),
            projectedSections = StickyTaskSections(emptyList(), emptyList()),
            fontScale = if (isValidStickyFontScale(initialFontScale)) initialFontScale else DEFAULT_STICKY_FONT_SCALE
        )
    )
    val stickyState: StateFlow<StickyTaskState> = _stickyState.asStateFlow()

    @kotlin.jvm.Volatile
    private var activeObserverJob: Job? = null
    @kotlin.jvm.Volatile
    private var activeToken: SurfaceAccountToken? = null
    private val lifecycleLock = Any()
    private val zoomLock = Any()
    init {
        scope.launch {
            access.readiness.collect { r ->
                when (r) {
                    is SurfaceReadiness.Ready -> {
                        _stickyState.update { current ->
                            val prevReadyToken = (current.readiness as? SurfaceReadiness.Ready)?.token
                            if (prevReadyToken != null && prevReadyToken != r.token && activeToken != r.token) {
                                StickyTaskState(
                                    readiness = r,
                                    sections = TaskSections(emptyList(), emptyList()),
                                    quickAdd = StickyQuickAddState(),
                                    projectedSections = StickyTaskSections(emptyList(), emptyList()),
                                    fontScale = current.fontScale
                                )
                            } else {
                                current.copy(readiness = r)
                            }
                        }
                    }
                    else -> {
                        val currentToken = access.getCurrentToken()
                        if (currentToken == null) {
                            _stickyState.update { current ->
                                StickyTaskState(
                                    readiness = r,
                                    sections = TaskSections(emptyList(), emptyList()),
                                    quickAdd = StickyQuickAddState(),
                                    projectedSections = StickyTaskSections(emptyList(), emptyList()),
                                    fontScale = current.fontScale
                                )
                            }
                        } else {
                            _stickyState.update { it.copy(readiness = r) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts observing tasks for the active account runtime.
     * Unfiltered tasks are partitioned into [TaskSections] via [partitionSurfaceTodos]
     * and projected into whole compact groups via [projectStickySections].
     */
    fun startObserving(token: SurfaceAccountToken, store: RoomAccountStore): Job = synchronized(lifecycleLock) {
        activeObserverJob?.cancel()
        val prev = activeToken
        activeToken = token
        if (prev != null && prev != token) {
            _stickyState.update {
                it.copy(
                    sections = TaskSections(emptyList(), emptyList()),
                    projectedSections = StickyTaskSections(emptyList(), emptyList()),
                    quickAdd = StickyQuickAddState()
                )
            }
        }
        val job = scope.launch {
            try {
                store.observeTodos().collect { todos ->
                    if (activeToken == token && access.getCurrentToken() == token) {
                        val sections = partitionSurfaceTodos(todos)
                        val now = clock()
                        val projected = projectStickySections(sections, now)
                        _stickyState.update { current ->
                            if (activeToken == token && access.getCurrentToken() == token) {
                                current.copy(sections = sections, projectedSections = projected)
                            } else {
                                current
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Gracefully handle database closure
            }
        }
        activeObserverJob = job
        job
    }

    fun refreshDeadlines(nowEpochMs: Long = clock()) {
        _stickyState.update { current ->
            if (current.readiness is SurfaceReadiness.Ready) {
                val projected = projectStickySections(current.sections, nowEpochMs)
                current.copy(projectedSections = projected)
            } else {
                current
            }
        }
    }

    fun stopObserving(): Unit = synchronized(lifecycleLock) {
        activeObserverJob?.cancel()
        activeObserverJob = null
        activeToken = null
        _stickyState.update { current ->
            StickyTaskState(
                readiness = access.readiness.value,
                sections = TaskSections(emptyList(), emptyList()),
                quickAdd = StickyQuickAddState(),
                projectedSections = StickyTaskSections(emptyList(), emptyList()),
                fontScale = current.fontScale
            )
        }
    }

    fun onAction(action: StickyTaskAction) {
        when (action) {
            is StickyTaskAction.ChangeTitle -> {
                val current = activeToken
                if (current == null || current != action.token || access.getCurrentToken() != action.token) return
                val res = TitlePolicy.normalizeAndValidate(action.rawTitle)
                val err = (res as? ApiResult.Failure)?.let {
                    (it.error as? SafeError.Validation)?.message ?: "Invalid title"
                }
                _stickyState.update { current ->
                    if (activeToken == action.token && access.getCurrentToken() == action.token) {
                        current.copy(
                            quickAdd = current.quickAdd.copy(
                                rawTitle = action.rawTitle,
                                error = err
                            )
                        )
                    } else {
                        current
                    }
                }
            }
            is StickyTaskAction.Submit -> {
                val current = activeToken
                if (current == null || current != action.token || access.getCurrentToken() != action.token) return
                val currentQuickAdd = _stickyState.value.quickAdd
                if (currentQuickAdd.submitting) return // Reject duplicate submissions

                val raw = currentQuickAdd.rawTitle
                val validation = TitlePolicy.normalizeAndValidate(raw)
                if (validation is ApiResult.Failure) {
                    val err = (validation.error as? SafeError.Validation)?.message ?: "Invalid title"
                    _stickyState.update {
                        it.copy(quickAdd = it.quickAdd.copy(error = err, submitting = false))
                    }
                    return
                }

                val title = (validation as ApiResult.Success).value
                _stickyState.update {
                    it.copy(quickAdd = it.quickAdd.copy(submitting = true, error = null))
                }

                scope.launch {
                    val result = access.withSurfaceAdmission(action.token) { store ->
                        val todo = createDefaultTodo(
                            title = title,
                            nowEpochMs = clock(),
                            taskRef = TaskRef(ownerUserId = store.userId, rowId = UUID.randomUUID().toString().lowercase())
                        )
                        store.saveTodoLocally(todo)
                    }

                    // Strengthened account fencing: ensure controller activeToken and access token still match action.token
                    if (activeToken != action.token || access.getCurrentToken() != action.token) {
                        _stickyState.update { current ->
                            if (activeToken == action.token) {
                                current.copy(quickAdd = current.quickAdd.copy(submitting = false))
                            } else {
                                current
                            }
                        }
                        return@launch
                    }

                    when (result) {
                        is SurfaceResult.Value -> {
                            // Clear only if the user hasn't typed new text during the async submit
                            _stickyState.update { current ->
                                if (activeToken == action.token && access.getCurrentToken() == action.token) {
                                    if (current.quickAdd.rawTitle == raw) {
                                        current.copy(
                                            quickAdd = StickyQuickAddState(rawTitle = "", error = null, submitting = false)
                                        )
                                    } else {
                                        current.copy(
                                            quickAdd = current.quickAdd.copy(submitting = false, error = null)
                                        )
                                    }
                                } else {
                                    current
                                }
                            }
                            onTriggerSync()
                        }
                        is SurfaceResult.Rejected,
                        is SurfaceResult.Unavailable -> {
                            _stickyState.update { current ->
                                if (activeToken == action.token) {
                                    current.copy(
                                        quickAdd = current.quickAdd.copy(
                                            submitting = false,
                                            error = "Failed to save task"
                                        )
                                    )
                                } else {
                                    current
                                }
                            }
                        }
                    }
                }
            }
            is StickyTaskAction.SetCompleted -> {
                val current = activeToken
                if (current == null || current != action.token || access.getCurrentToken() != action.token) return
                scope.launch {
                    val res = access.setCompleted(
                        token = action.token,
                        taskRef = action.taskRef,
                        completed = action.completed,
                        nowEpochMs = clock()
                    )
                    if (activeToken == action.token && access.getCurrentToken() == action.token) {
                        if (res is SurfaceResult.Value && res.value == CompletionChange.Changed) {
                            onTriggerSync()
                        }
                    }
                }
            }
            is StickyTaskAction.RefreshDeadlines -> {
                refreshDeadlines()
            }
            is StickyTaskAction.OpenApp -> {
                onOpenApp()
            }
            is StickyTaskAction.Quit -> {
                onQuit()
            }
            is StickyTaskAction.ZoomIn -> synchronized(zoomLock) {
                var updatedScale: Float? = null
                _stickyState.update { current ->
                    val newScale = (current.fontScale + STICKY_FONT_SCALE_STEP).coerceIn(MIN_STICKY_FONT_SCALE, MAX_STICKY_FONT_SCALE)
                    if (newScale != current.fontScale) {
                        updatedScale = newScale
                        current.copy(fontScale = newScale)
                    } else {
                        updatedScale = null
                        current
                    }
                }
                updatedScale?.let { onFontScaleChanged?.invoke(it) }
            }
            is StickyTaskAction.ZoomOut -> synchronized(zoomLock) {
                var updatedScale: Float? = null
                _stickyState.update { current ->
                    val newScale = (current.fontScale - STICKY_FONT_SCALE_STEP).coerceIn(MIN_STICKY_FONT_SCALE, MAX_STICKY_FONT_SCALE)
                    if (newScale != current.fontScale) {
                        updatedScale = newScale
                        current.copy(fontScale = newScale)
                    } else {
                        updatedScale = null
                        current
                    }
                }
                updatedScale?.let { onFontScaleChanged?.invoke(it) }
            }
            is StickyTaskAction.ResetZoom -> synchronized(zoomLock) {
                var updatedScale: Float? = null
                _stickyState.update { current ->
                    if (current.fontScale != DEFAULT_STICKY_FONT_SCALE) {
                        updatedScale = DEFAULT_STICKY_FONT_SCALE
                        current.copy(fontScale = DEFAULT_STICKY_FONT_SCALE)
                    } else {
                        updatedScale = null
                        current
                    }
                }
                updatedScale?.let { onFontScaleChanged?.invoke(it) }
            }
        }
    }
}
