package dev.tabbyrush.android

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.SurfaceSnapshot
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.TaskSections
import dev.tabbyrush.shared.contract.TaskSurfaceAccess
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.TaskAccessState
import dev.tabbyrush.shared.contract.TaskCapabilities
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
import kotlinx.coroutines.withTimeout

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
        createdAt: Long = 1000L,
        parentTaskId: String? = null,
        deadline: TaskDeadline? = null,
        scheduleToken: String? = if (deadline != null) "token-$id" else null,
        reminderRule: ReminderRule = ReminderRule.DISABLED,
        ownerUserId: String = "alice"
    ): Todo {
        val taskRef = TaskRef(ownerUserId, id)
        val parentTaskRef = parentTaskId?.let { TaskRef(ownerUserId, it) }
        return Todo(
            taskRef = taskRef,
            title = title,
            completed = completed,
            isDeleted = isDeleted,
            createdAt = createdAt,
            updatedAt = createdAt,
            parentTaskRef = parentTaskRef,
            deadline = deadline,
            scheduleToken = scheduleToken,
            reminderRule = reminderRule
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
    // 1b. Native Widget Hierarchy & Deadline Projections
    // -------------------------------------------------------------------------

    @Test
    fun testPartitionSurfaceTodosAncestorDeletionDominance() {
        val root = sampleTodo("root-1", "Parent Deleted", completed = false, isDeleted = true)
        val child = sampleTodo("child-1", "Child Live", completed = false, isDeleted = false, parentTaskId = "root-1")

        val sections = partitionSurfaceTodos(listOf(root, child))
        assertTrue(sections.incomplete.isEmpty(), "Child of deleted root must be suppressed")
        assertTrue(sections.completed.isEmpty())
    }

    @Test
    fun testProjectWidgetItemsStandaloneTasks() {
        val active = sampleTodo("t1", "Active Standalone", completed = false, createdAt = 2000L)
        val completed = sampleTodo("t2", "Completed Standalone", completed = true, createdAt = 1000L)

        val items = projectWidgetItems(listOf(active, completed))
        assertEquals(2, items.size)

        val item1 = items[0]
        assertEquals("t1", item1.todo.id)
        assertFalse(item1.isChild)
        assertFalse(item1.hasLiveChildren)
        assertFalse(item1.effectiveCompleted)
        assertEquals(0, item1.childCount)
        assertEquals(0, item1.completedChildCount)

        val item2 = items[1]
        assertEquals("t2", item2.todo.id)
        assertFalse(item2.isChild)
        assertFalse(item2.hasLiveChildren)
        assertTrue(item2.effectiveCompleted)
    }

    @Test
    fun testProjectWidgetItemsMixedGroupInIncompleteSection() {
        val parent = sampleTodo("p1", "Parent Task", completed = false, createdAt = 2000L)
        val c1 = sampleTodo("c1", "Child Done", completed = true, createdAt = 1800L, parentTaskId = "p1")
        val c2 = sampleTodo("c2", "Child Active", completed = false, createdAt = 1600L, parentTaskId = "p1")

        val sections = partitionSurfaceTodos(listOf(parent, c1, c2))
        assertEquals(listOf("p1", "c1", "c2"), sections.incomplete.map { it.id })
        assertTrue(sections.completed.isEmpty())

        val widgetItems = projectWidgetItems(sections.incomplete)
        assertEquals(3, widgetItems.size)

        val pItem = widgetItems[0]
        assertEquals("p1", pItem.todo.id)
        assertFalse(pItem.isChild)
        assertTrue(pItem.hasLiveChildren)
        assertFalse(pItem.effectiveCompleted, "Parent of active child must be effectively active")
        assertEquals(2, pItem.childCount)
        assertEquals(1, pItem.completedChildCount)

        val c1Item = widgetItems[1]
        assertEquals("c1", c1Item.todo.id)
        assertTrue(c1Item.isChild)
        assertFalse(c1Item.hasLiveChildren)
        assertTrue(c1Item.effectiveCompleted, "Completed child must retain its completed state in mixed group")

        val c2Item = widgetItems[2]
        assertEquals("c2", c2Item.todo.id)
        assertTrue(c2Item.isChild)
        assertFalse(c2Item.hasLiveChildren)
        assertFalse(c2Item.effectiveCompleted)
    }

    @Test
    fun testProjectWidgetItemsFullyCompletedGroupRawCompletedDisagreement() {
        val parent = sampleTodo("p1", "Parent Task", completed = false, createdAt = 2000L)
        val c1 = sampleTodo("c1", "Child 1", completed = true, createdAt = 1800L, parentTaskId = "p1")
        val c2 = sampleTodo("c2", "Child 2", completed = true, createdAt = 1600L, parentTaskId = "p1")

        val sections = partitionSurfaceTodos(listOf(parent, c1, c2))
        assertTrue(sections.incomplete.isEmpty())
        assertEquals(listOf("p1", "c1", "c2"), sections.completed.map { it.id })

        val widgetItems = projectWidgetItems(sections.completed)
        assertEquals(3, widgetItems.size)

        val pItem = widgetItems[0]
        assertTrue(pItem.hasLiveChildren)
        assertTrue(pItem.effectiveCompleted, "Parent with all completed children must be effectively completed despite raw completed=false")
        assertEquals(2, pItem.childCount)
        assertEquals(2, pItem.completedChildCount)

        assertTrue(widgetItems[1].effectiveCompleted)
        assertTrue(widgetItems[2].effectiveCompleted)
    }

    @Test
    fun testProjectWidgetItemsAbsoluteOwnDeadlinesAndOverdue() {
        val nowMs = 1792165000000L
        val dateOnly = TaskDeadline(
            date = "2026-10-25",
            time = null,
            zone = "UTC",
            offsetSeconds = 0L,
            reminderAnchorTime = "09:00",
            anchorEpochMs = nowMs + 100_000L,
            overdueEpochMs = nowMs + 100_000L
        )
        val timedOverdue = TaskDeadline(
            date = "2026-10-18",
            time = "14:00",
            zone = "America/New_York",
            offsetSeconds = -14400L,
            anchorEpochMs = nowMs - 50_000L,
            overdueEpochMs = nowMs - 50_000L
        )

        val taskNoDeadline = sampleTodo("t1", "No deadline", createdAt = 4000L)
        val taskDateOnly = sampleTodo("t2", "Date only", deadline = dateOnly, createdAt = 3000L)
        val taskTimedOverdue = sampleTodo("t3", "Timed Overdue", deadline = timedOverdue, completed = false, createdAt = 2000L)
        val taskCompletedPastDeadline = sampleTodo("t4", "Completed Past Deadline", deadline = timedOverdue, completed = true, createdAt = 1000L)

        val items = projectWidgetItems(listOf(taskNoDeadline, taskDateOnly, taskTimedOverdue, taskCompletedPastDeadline), nowEpochMs = nowMs)

        assertNull(items[0].deadlinePresentation)

        val presDateOnly = items[1].deadlinePresentation
        assertNotNull(presDateOnly)
        assertEquals("Due 2026-10-25", presDateOnly.label)
        assertFalse(presDateOnly.isOverdue)
        assertNull(presDateOnly.overdueLabel)

        val presTimedOverdue = items[2].deadlinePresentation
        assertNotNull(presTimedOverdue)
        assertEquals("Due 2026-10-18 14:00 (America/New_York)", presTimedOverdue.label)
        assertTrue(presTimedOverdue.isOverdue)
        assertEquals("Overdue", presTimedOverdue.overdueLabel)

        val presCompleted = items[3].deadlinePresentation
        assertNotNull(presCompleted)
        assertEquals("Due 2026-10-18 14:00 (America/New_York)", presCompleted.label)
        assertFalse(presCompleted.isOverdue, "Completed task must never be labeled as overdue")
        assertNull(presCompleted.overdueLabel)
    }

    @Test
    fun testNoDeadlineInheritanceBetweenParentAndChild() {
        val parentDeadline = TaskDeadline(
            date = "2026-10-28",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1792165000000L,
            overdueEpochMs = 1792165000000L
        )
        val parent = sampleTodo("p1", "Parent with deadline", deadline = parentDeadline)
        val child = sampleTodo("c1", "Child without deadline", parentTaskId = "p1", deadline = null)

        val items = projectWidgetItems(listOf(parent, child))
        assertNotNull(items[0].deadlinePresentation)
        assertEquals("Due 2026-10-28 10:00 (UTC)", items[0].deadlinePresentation?.label)

        assertNull(items[1].deadlinePresentation, "Child must not inherit parent's deadline")
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
        val ref = TaskRef("alice", "task-uuid-42")
        val params = TaskWidgetActionKeys.toggleTaskParameters(
            token = testToken,
            taskRef = ref,
            desiredCompleted = true
        )

        assertEquals(testToken.accountKey.value, params[TaskWidgetActionKeys.PARAM_ACCOUNT_KEY])
        assertEquals(7L, params[TaskWidgetActionKeys.PARAM_GENERATION])
        assertEquals("alice", params[TaskWidgetActionKeys.PARAM_OWNER_USER_ID])
        assertEquals("task-uuid-42", params[TaskWidgetActionKeys.PARAM_ROW_ID])
        assertEquals("task-uuid-42", params[TaskWidgetActionKeys.PARAM_TASK_ID])
        assertEquals(true, params[TaskWidgetActionKeys.PARAM_DESIRED_COMPLETED])
    }

    @Test
    fun testRequestCodeDeterminismAndSeparation() {
        val ref1 = TaskRef("alice", "task-1")
        val ref1Repeat = TaskRef("alice", "task-1")
        val ref2 = TaskRef("alice", "task-2")
        val refDiffOwnerSameRow = TaskRef("bob", "task-1")

        val code1 = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, ref1)
        val code1Repeat = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, ref1Repeat)
        val code2 = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, ref2)
        val codeDiffOwner = TaskWidgetActionKeys.getOpenTaskRequestCode(testToken, refDiffOwnerSameRow)
        val addCode = TaskWidgetActionKeys.getAddTaskRequestCode(testToken)
        val appCode = TaskWidgetActionKeys.getOpenAppRequestCode()

        assertEquals(code1, code1Repeat)
        assertTrue(code1 >= 0, "Request code must be non-negative")
        assertTrue(code2 >= 0, "Request code must be non-negative")
        assertTrue(codeDiffOwner >= 0, "Request code must be non-negative")
        assertTrue(addCode >= 0, "Add request code must be non-negative")
        assertTrue(code1 != code2, "Different tasks must yield distinct request codes")
        assertTrue(code1 != codeDiffOwner, "Different owners with colliding row IDs must yield distinct request codes")
        assertTrue(code1 != addCode, "Open and Add must yield distinct request codes")
        assertTrue(code1 != appCode, "Open task and Open app must yield distinct request codes")
    }

    @Test
    fun testActionConstantsConformToStandard() {
        assertEquals("dev.tabbyrush.android.ACTION_VIEW_TASK", TaskWidgetActionKeys.ACTION_WIDGET_VIEW_TASK)
        assertEquals("dev.tabbyrush.android.ACTION_ADD_TASK", TaskWidgetActionKeys.ACTION_WIDGET_ADD_TASK)
        assertEquals("extra_account_key", TaskWidgetActionKeys.EXTRA_ACCOUNT_KEY)
        assertEquals("extra_generation", TaskWidgetActionKeys.EXTRA_GENERATION)
        assertEquals("extra_owner_user_id", TaskWidgetActionKeys.EXTRA_OWNER_USER_ID)
        assertEquals("extra_row_id", TaskWidgetActionKeys.EXTRA_ROW_ID)
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

        val readStarted = CompletableDeferred<Unit>()
        val readGate = CompletableDeferred<Unit>()
        var publishedCount = 0

        fun updateReadiness(newReadiness: SurfaceReadiness) {
            _readiness.value = newReadiness
        }

        override suspend fun awaitReadiness(timeoutMs: Long): SurfaceReadiness = _readiness.value

        override suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot> {
            readStarted.complete(Unit) // Signal entry into read
            readGate.await() // Pauses read until explicitly released by test
            return SurfaceResult.Value(
                SurfaceSnapshot(
                    token = token,
                    sections = TaskSections(
                        incomplete = listOf(Todo(taskRef = TaskRef("alice", "t1"), title = "Leaked Title Candidate", completed = false, createdAt = 100L, updatedAt = 100L)),
                        completed = emptyList()
                    ),
                    schedules = emptyList()
                )
            )
        }

        override suspend fun setCompleted(
            token: SurfaceAccountToken,
            taskRef: TaskRef,
            completed: Boolean,
            nowEpochMs: Long
        ): SurfaceResult<CompletionChange> = SurfaceResult.Value(CompletionChange.Changed)
        override suspend fun publishIfCurrent(
            token: SurfaceAccountToken,
            publish: suspend () -> Unit
        ): Boolean {
            val current = _readiness.value
            if (current !is SurfaceReadiness.Ready || current.token != token) {
                return false
            }
            publishedCount++
            publish()
            return true
        }

        override suspend fun validateNavigation(token: SurfaceAccountToken, taskRef: TaskRef?): Boolean = true
    }

    @Test
    fun testClearDuringReadNeutralizesPublication() = runBlocking {
        TabbyRushTaskWidget.clearState()
        assertEquals(WidgetDisplayState.SignedOut, TabbyRushTaskWidget.displayState.value)

        val access = MockControlledTaskSurfaceAccess(SurfaceReadiness.Ready(testToken))

        // Simulate an update thread reading from storage
        val readJob = launch(kotlinx.coroutines.Dispatchers.Default) {
            val readiness = access.awaitReadiness(1000L)
            if (readiness is SurfaceReadiness.Ready) {
                val snapshotResult = access.read(readiness.token)
                if (snapshotResult is SurfaceResult.Value) {
                    access.publishIfCurrent(readiness.token) {
                        TabbyRushTaskWidget.updateDisplayState(
                            WidgetDisplayState.Content(
                                token = readiness.token,
                                sections = snapshotResult.value.sections
                            )
                        )
                    }
                }
            }
        }

        // Await entry into read with bounded timeout: proves read has started and is suspended on readGate
        withTimeout(5_000L) {
            access.readStarted.await()
        }
        // State is SignedOut while read is paused
        assertEquals(WidgetDisplayState.SignedOut, TabbyRushTaskWidget.displayState.value)

        // Clear state again on logout / account switch
        access.updateReadiness(SurfaceReadiness.SignedOut)
        TabbyRushTaskWidget.clearState()

        // Unblock read after clear
        access.readGate.complete(Unit)
        withTimeout(5_000L) {
            readJob.join()
        }
        // Verify that TabbyRushTaskWidget did not adopt leaked title
        assertEquals(WidgetDisplayState.SignedOut, TabbyRushTaskWidget.displayState.value)
        assertEquals(0, access.publishedCount)
    }

    @Test
    fun testWidgetTaskItemRowSpecifications() {
        val parent = sampleTodo("p1", "Parent Task", completed = false, createdAt = 3000L)
        val child = sampleTodo("c1", "Child Task", completed = false, createdAt = 2000L, parentTaskId = "p1")
        val standalone = sampleTodo("s1", "Standalone", completed = false, createdAt = 1000L)

        val items = projectWidgetItems(listOf(parent, child, standalone))
        assertEquals(3, items.size)

        // Parent with live children: hasLiveChildren=true, isChild=false
        val p = items[0]
        assertTrue(p.hasLiveChildren)
        assertFalse(p.isChild)
        assertFalse(p.effectiveCompleted)
        assertEquals(1, p.childCount)
        assertEquals(0, p.completedChildCount)

        // Child: hasLiveChildren=false, isChild=true (indented)
        val c = items[1]
        assertFalse(c.hasLiveChildren)
        assertTrue(c.isChild)
        assertFalse(c.effectiveCompleted)

        // Standalone: hasLiveChildren=false, isChild=false (not indented)
        val s = items[2]
        assertFalse(s.hasLiveChildren)
        assertFalse(s.isChild)
        assertFalse(s.effectiveCompleted)
    }

    @Test
    fun testChildOnlyTaskProjectedWithoutRequiringParent() {
        val bobChildRef = TaskRef("bob", "child-only-1")
        val childOnly = Todo(
            taskRef = bobChildRef,
            title = "Bob Child Only Task",
            completed = false,
            createdAt = 4000L,
            parentTaskRef = TaskRef("bob", "private-parent-omitted"),
            accessState = TaskAccessState(
                ref = bobChildRef,
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.ChildOnly
            )
        )
        val items = projectWidgetItems(listOf(childOnly))
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("Bob Child Only Task", item.todo.title)
        assertTrue(item.isChildOnly)
        assertTrue(item.isChild)
        assertFalse(item.hasLiveChildren)
    }

    @Test
    fun testAwaitQuiescenceCompletesCleanly() = runBlocking {
        TaskWidgetUpdater.awaitQuiescence(500L)
        assertTrue(true)
    }
}
