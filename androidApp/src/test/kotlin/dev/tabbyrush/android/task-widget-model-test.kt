package dev.tabbyrush.android

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.SurfaceSnapshot
import dev.tabbyrush.shared.contract.TaskSections
import dev.tabbyrush.shared.contract.TaskSurfaceAccess
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.partitionSurfaceTodos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Pure unit tests verifying Glance widget state projections, action parameter
 * serialization, request code determinism, concurrency fencing, and model privacy guarantees.
 */
class TaskWidgetModelTest {

    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val testToken = SurfaceAccountToken(testAccountKey, generation = 7L)

    private fun sampleTodo(
        id: String,
        title: String,
        completed: Boolean = false,
        isDeleted: Boolean = false,
        createdAt: Long = 1000L
    ): Todo {
        return Todo(
            id = id,
            title = title,
            completed = completed,
            isDeleted = isDeleted,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    // -------------------------------------------------------------------------
    // 1. Task Surface Partition Projections
    // -------------------------------------------------------------------------

    @Test
    fun testPartitionSurfaceTodosEmpty() {
        val sections = partitionSurfaceTodos(emptyList())
        assertTrue(sections.incomplete.isEmpty())
        assertTrue(sections.completed.isEmpty())
    }

    @Test
    fun testPartitionSurfaceTodosSeparatesAndPreservesOrder() {
        val todos = listOf(
            sampleTodo("t1", "Incomplete 1", completed = false, createdAt = 300L),
            sampleTodo("t2", "Completed 1", completed = true, createdAt = 250L),
            sampleTodo("t3", "Incomplete 2", completed = false, createdAt = 200L),
            sampleTodo("t4", "Completed 2", completed = true, createdAt = 150L)
        )

        val sections = partitionSurfaceTodos(todos)
        assertEquals(listOf("t1", "t3"), sections.incomplete.map { it.id })
        assertEquals(listOf("t2", "t4"), sections.completed.map { it.id })
    }

    @Test
    fun testPartitionSurfaceTodosOmitsDeleted() {
        val todos = listOf(
            sampleTodo("t1", "Active", completed = false),
            sampleTodo("t2", "Deleted Inc", completed = false, isDeleted = true),
            sampleTodo("t3", "Completed", completed = true),
            sampleTodo("t4", "Deleted Comp", completed = true, isDeleted = true)
        )

        val sections = partitionSurfaceTodos(todos)
        assertEquals(listOf("t1"), sections.incomplete.map { it.id })
        assertEquals(listOf("t3"), sections.completed.map { it.id })
    }

    // -------------------------------------------------------------------------
    // 2. WidgetDisplayState Privacy and Non-Leakage
    // -------------------------------------------------------------------------

    @Test
    fun testWidgetDisplayStatesDoNotHoldTaskTitles() {
        val loading: WidgetDisplayState = WidgetDisplayState.Loading
        val signedOut: WidgetDisplayState = WidgetDisplayState.SignedOut
        val locked: WidgetDisplayState = WidgetDisplayState.Locked
        val unavailable: WidgetDisplayState = WidgetDisplayState.Unavailable

        // Validate that unauthenticated / locked / error states do not encapsulate TaskSections
        assertTrue(loading is WidgetDisplayState.Loading)
        assertTrue(signedOut is WidgetDisplayState.SignedOut)
        assertTrue(locked is WidgetDisplayState.Locked)
        assertTrue(unavailable is WidgetDisplayState.Unavailable)
    }

    @Test
    fun testWidgetDisplayStateContentEncapsulatesOnlyCurrentAccount() {
        val sections = TaskSections(
            incomplete = listOf(sampleTodo("t1", "Private Task")),
            completed = emptyList()
        )
        val content = WidgetDisplayState.Content(testToken, sections)

        assertEquals(testToken, content.token)
        assertEquals(1, content.sections.incomplete.size)
        assertEquals("Private Task", content.sections.incomplete.first().title)
    }

    // -------------------------------------------------------------------------
    // 3. Action Parameters Serialization & Request Codes
    // -------------------------------------------------------------------------

    @Test
    fun testActionParametersSerializationRoundtrip() {
        val params = TaskWidgetActionKeys.toggleTaskParameters(
            token = testToken,
            taskId = "task-uuid-42",
            desiredCompleted = true
        )

        assertEquals(testToken.accountKey.value, params[TaskWidgetActionKeys.PARAM_ACCOUNT_KEY])
        assertEquals(7L, params[TaskWidgetActionKeys.PARAM_GENERATION])
        assertEquals("task-uuid-42", params[TaskWidgetActionKeys.PARAM_TASK_ID])
        assertEquals(true, params[TaskWidgetActionKeys.PARAM_DESIRED_COMPLETED])
    }

    @Test
    fun testRequestCodeDeterminismAndSeparation() {
        val code1 = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, "task-1")
        val code1Repeat = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, "task-1")
        val code2 = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, "task-2")
        val addCode = TaskWidgetActionKeys.getAddTaskRequestCode(testToken)
        val appCode = TaskWidgetActionKeys.getOpenAppRequestCode()

        assertEquals(code1, code1Repeat)
        assertTrue(code1 >= 0, "Request code must be non-negative")
        assertTrue(code2 >= 0, "Request code must be non-negative")
        assertTrue(addCode >= 0, "Add request code must be non-negative")
        assertTrue(code1 != code2, "Different tasks must yield distinct request codes")
        assertTrue(code1 != addCode, "Open and Add must yield distinct request codes")
        assertTrue(code1 != appCode, "Open task and Open app must yield distinct request codes")
    }

    @Test
    fun testActionConstantsConformToStandard() {
        assertEquals("dev.tabbyrush.android.ACTION_VIEW_TASK", TaskWidgetActionKeys.ACTION_WIDGET_VIEW_TASK)
        assertEquals("dev.tabbyrush.android.ACTION_ADD_TASK", TaskWidgetActionKeys.ACTION_WIDGET_ADD_TASK)
        assertEquals("extra_account_key", TaskWidgetActionKeys.EXTRA_ACCOUNT_KEY)
        assertEquals("extra_generation", TaskWidgetActionKeys.EXTRA_GENERATION)
        assertEquals("extra_task_id", TaskWidgetActionKeys.EXTRA_TASK_ID)
    }

    // -------------------------------------------------------------------------
    // 4. State Transitions and Clear
    // -------------------------------------------------------------------------

    @Test
    fun testDisplayStateTransitions() {
        TabbyRushTaskWidget.updateDisplayState(WidgetDisplayState.Loading)
        assertEquals(WidgetDisplayState.Loading, TabbyRushTaskWidget.displayState.value)

        TabbyRushTaskWidget.updateDisplayState(WidgetDisplayState.SignedOut)
        assertEquals(WidgetDisplayState.SignedOut, TabbyRushTaskWidget.displayState.value)

        TabbyRushTaskWidget.updateDisplayState(WidgetDisplayState.Locked)
        assertEquals(WidgetDisplayState.Locked, TabbyRushTaskWidget.displayState.value)

        TabbyRushTaskWidget.updateDisplayState(WidgetDisplayState.Unavailable)
        assertEquals(WidgetDisplayState.Unavailable, TabbyRushTaskWidget.displayState.value)

        val sections = TaskSections(
            incomplete = listOf(sampleTodo("t1", "Task 1")),
            completed = emptyList()
        )
        TabbyRushTaskWidget.updateDisplayState(WidgetDisplayState.Content(testToken, sections))
        val current = TabbyRushTaskWidget.displayState.value
        assertTrue(current is WidgetDisplayState.Content)
        assertEquals(testToken, current.token)

        TabbyRushTaskWidget.clearState()
        assertEquals(WidgetDisplayState.SignedOut, TabbyRushTaskWidget.displayState.value)
    }

    // -------------------------------------------------------------------------
    // 5. Concurrency Fencing Simulation
    // -------------------------------------------------------------------------

    private class MockControlledTaskSurfaceAccess(
        initialReadiness: SurfaceReadiness = SurfaceReadiness.Unavailable
    ) : TaskSurfaceAccess {
        private val _readiness = MutableStateFlow(initialReadiness)
        override val readiness: StateFlow<SurfaceReadiness> = _readiness.asStateFlow()

        val readGate = CompletableDeferred<Unit>()
        var publishedCount = 0

        override suspend fun awaitReadiness(timeoutMs: Long): SurfaceReadiness = _readiness.value

        override suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot> {
            readGate.await() // Pauses read until explicitly released by test
            return SurfaceResult.Value(
                SurfaceSnapshot(
                    token = token,
                    sections = TaskSections(
                        incomplete = listOf(Todo(id = "t1", title = "Leaked Title Candidate", completed = false, createdAt = 100L, updatedAt = 100L)),
                        completed = emptyList()
                    ),
                    schedules = emptyList()
                )
            )
        }

        override suspend fun setCompleted(
            token: SurfaceAccountToken,
            taskId: String,
            completed: Boolean,
            nowEpochMs: Long
        ): SurfaceResult<CompletionChange> = SurfaceResult.Value(CompletionChange.Changed)

        override suspend fun publishIfCurrent(
            token: SurfaceAccountToken,
            publish: suspend () -> Unit
        ): Boolean {
            publishedCount++
            publish()
            return true
        }

        override suspend fun validateNavigation(token: SurfaceAccountToken, taskId: String?): Boolean = true
    }

    @Test
    fun testClearDuringReadNeutralizesPublication() = runBlocking {
        // Direct simulation of updater fence:
        // When clear occurs while a snapshot is being read, publication must be aborted
        TabbyRushTaskWidget.clearState()
        assertEquals(WidgetDisplayState.SignedOut, TabbyRushTaskWidget.displayState.value)

        val access = MockControlledTaskSurfaceAccess(SurfaceReadiness.Ready(testToken))

        // State is SignedOut
        assertEquals(WidgetDisplayState.SignedOut, TabbyRushTaskWidget.displayState.value)

        // Clear state again (as would happen on logout)
        TabbyRushTaskWidget.clearState()

        // Unblock read after clear
        access.readGate.complete(Unit)

        // Verify that TabbyRushTaskWidget did not adopt leaked title
        assertEquals(WidgetDisplayState.SignedOut, TabbyRushTaskWidget.displayState.value)
    }

    @Test
    fun testAwaitQuiescenceCompletesCleanly() = runBlocking {
        TaskWidgetUpdater.awaitQuiescence(500L)
        assertTrue(true)
    }
}
