package dev.doggo.desktop

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.CompletionChange
import dev.doggo.shared.contract.ReminderRule
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.contract.SurfaceResult
import dev.doggo.shared.contract.SurfaceSnapshot
import dev.doggo.shared.contract.TaskSections
import dev.doggo.shared.contract.TaskSurfaceAccess
import dev.doggo.shared.contract.TitlePolicy
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.partitionSurfaceTodos
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.sync.LeaveSchedulerCancelGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    id: String = UUID.randomUUID().toString().lowercase()
): Todo {
    return Todo(
        id = id,
        title = title,
        completed = false,
        createdAt = nowEpochMs,
        updatedAt = nowEpochMs,
        deadline = null,
        reminderRule = ReminderRule.DISABLED,
        scheduleToken = null
    )
}

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
        taskId: String,
        completed: Boolean,
        nowEpochMs: Long
    ): SurfaceResult<CompletionChange> {
        return withSurfaceAdmission(token) { store ->
            store.setCompletedLocally(taskId, completed, nowEpochMs)
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

    override suspend fun validateNavigation(token: SurfaceAccountToken, taskId: String?): Boolean {
        return stateMutex.withLock {
            if (gateState != SurfaceGateState.OPEN) return@withLock false
            val ready = _readiness.value
            if (ready !is SurfaceReadiness.Ready || ready.token != token || currentToken != token) {
                return@withLock false
            }
            if (taskId == null) {
                true
            } else {
                val store = activeStore ?: return@withLock false
                try {
                    val todos = store.loadTodos()
                    todos.any { it.id == taskId && !it.isDeleted }
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
    private val onTriggerSync: () -> Unit
) {

    private val _stickyState = MutableStateFlow(
        StickyTaskState(
            readiness = access.readiness.value,
            sections = TaskSections(emptyList(), emptyList()),
            quickAdd = StickyQuickAddState()
        )
    )
    val stickyState: StateFlow<StickyTaskState> = _stickyState.asStateFlow()

    private var activeObserverJob: Job? = null
    private var activeToken: SurfaceAccountToken? = null

    init {
        scope.launch {
            access.readiness.collect { r ->
                _stickyState.value = _stickyState.value.copy(readiness = r)
                if (r !is SurfaceReadiness.Ready) {
                    // Blank content and input on transition away from Ready
                    _stickyState.value = _stickyState.value.copy(
                        sections = TaskSections(emptyList(), emptyList()),
                        quickAdd = StickyQuickAddState()
                    )
                    activeToken = null
                    activeObserverJob?.cancel()
                    activeObserverJob = null
                }
            }
        }
    }

    /**
     * Starts observing tasks for the active account runtime.
     * Unfiltered tasks are partitioned into [TaskSections] via [partitionSurfaceTodos].
     */
    fun startObserving(token: SurfaceAccountToken, store: RoomAccountStore): Job {
        activeObserverJob?.cancel()
        activeToken = token
        val job = scope.launch {
            try {
                store.observeTodos().collect { todos ->
                    // Token check prevents stale emissions from previous account/generation
                    if (activeToken == token && access.getCurrentToken() == token) {
                        val sections = partitionSurfaceTodos(todos)
                        _stickyState.value = _stickyState.value.copy(sections = sections)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Gracefully handle database closure
            }
        }
        activeObserverJob = job
        return job
    }

    fun stopObserving() {
        activeObserverJob?.cancel()
        activeObserverJob = null
        activeToken = null
    }

    fun onAction(action: StickyTaskAction) {
        when (action) {
            is StickyTaskAction.ChangeTitle -> {
                val current = activeToken
                if (current == null || current != action.token) return
                val res = TitlePolicy.normalizeAndValidate(action.rawTitle)
                val err = (res as? ApiResult.Failure)?.let {
                    (it.error as? SafeError.Validation)?.message ?: "Invalid title"
                }
                _stickyState.value = _stickyState.value.copy(
                    quickAdd = _stickyState.value.quickAdd.copy(
                        rawTitle = action.rawTitle,
                        error = err
                    )
                )
            }
            is StickyTaskAction.Submit -> {
                val current = activeToken
                if (current == null || current != action.token) return
                val currentQuickAdd = _stickyState.value.quickAdd
                if (currentQuickAdd.submitting) return // Reject duplicate submissions

                val raw = currentQuickAdd.rawTitle
                val validation = TitlePolicy.normalizeAndValidate(raw)
                if (validation is ApiResult.Failure) {
                    val err = (validation.error as? SafeError.Validation)?.message ?: "Invalid title"
                    _stickyState.value = _stickyState.value.copy(
                        quickAdd = currentQuickAdd.copy(error = err, submitting = false)
                    )
                    return
                }

                val title = (validation as ApiResult.Success).value
                _stickyState.value = _stickyState.value.copy(
                    quickAdd = currentQuickAdd.copy(submitting = true, error = null)
                )

                scope.launch {
                    val result = access.withSurfaceAdmission(action.token) { store ->
                        val todo = createDefaultTodo(title)
                        store.saveTodoLocally(todo)
                    }

                    when (result) {
                        is SurfaceResult.Value -> {
                            // Clear only if the user hasn't typed new text during the async submit
                            if (_stickyState.value.quickAdd.rawTitle == raw) {
                                _stickyState.value = _stickyState.value.copy(
                                    quickAdd = StickyQuickAddState(rawTitle = "", error = null, submitting = false)
                                )
                            } else {
                                _stickyState.value = _stickyState.value.copy(
                                    quickAdd = _stickyState.value.quickAdd.copy(submitting = false, error = null)
                                )
                            }
                            onTriggerSync()
                        }
                        is SurfaceResult.Rejected,
                        is SurfaceResult.Unavailable -> {
                            _stickyState.value = _stickyState.value.copy(
                                quickAdd = _stickyState.value.quickAdd.copy(
                                    submitting = false,
                                    error = "Failed to save task"
                                )
                            )
                        }
                    }
                }
            }
            is StickyTaskAction.SetCompleted -> {
                val current = activeToken
                if (current == null || current != action.token) return
                scope.launch {
                    val res = access.setCompleted(
                        token = action.token,
                        taskId = action.taskId,
                        completed = action.completed,
                        nowEpochMs = System.currentTimeMillis()
                    )
                    if (res is SurfaceResult.Value && res.value == CompletionChange.Changed) {
                        onTriggerSync()
                    }
                }
            }
            is StickyTaskAction.OpenApp -> {
                onOpenApp()
            }
            is StickyTaskAction.Quit -> {
                onQuit()
            }
        }
    }
}
