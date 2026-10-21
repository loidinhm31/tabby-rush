package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoFilter
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.JvmAccountFiles
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.createInMemoryJvmDatabaseBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTaskSurfacesTest {

    private val accountKey1 = AccountKey("1111111111111111111111111111111111111111111111111111111111111111")
    private val token1 = SurfaceAccountToken(accountKey1, 1L)
    private val staleToken = SurfaceAccountToken(accountKey1, 999L)

    private lateinit var db: dev.tabbyrush.shared.data.TabbyRushDatabase
    private lateinit var store: RoomAccountStore

    @BeforeTest
    fun setUp() {
        db = createInMemoryJvmDatabaseBuilder().build()
        store = RoomAccountStore(
            accountKey = accountKey1,
            userId = "test-user",
            database = db,
            appId = "tabbyrush-test"
        )
    }

    @AfterTest
    fun tearDown() {
        try { db.close() } catch (_: Exception) {}
    }

    @Test
    fun testReadinessStateFlowAndAwaitReadiness() = runTest {
        val access = DesktopTaskSurfaceAccess()

        // 1. Initial readiness is Restoring
        assertEquals(SurfaceReadiness.Restoring, access.readiness.value)

        // 2. awaitReadiness times out to Unavailable when unresolved
        val timedOut = access.awaitReadiness(timeoutMs = 50L)
        assertEquals(SurfaceReadiness.Unavailable, timedOut)

        // 3. Authenticate transitions to Ready
        access.onAccountAuthenticated(token1, store)
        assertEquals(SurfaceReadiness.Ready(token1), access.readiness.value)

        // 4. awaitReadiness resolves immediately
        val resolved = access.awaitReadiness(timeoutMs = 1000L)
        assertEquals(SurfaceReadiness.Ready(token1), resolved)

        // 5. Sign out transitions to SignedOut
        access.onSignedOut()
        assertEquals(SurfaceReadiness.SignedOut, access.readiness.value)
    }

    @Test
    fun testStaleTokenRejected() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        // Stale token read is rejected
        val readRes = access.read(staleToken)
        assertTrue(readRes is SurfaceResult.Rejected)

        // Stale token setCompleted is rejected
        val completeRes = access.setCompleted(staleToken, "task-1", true, 1000L)
        assertTrue(completeRes is SurfaceResult.Rejected)

        // Stale token publishIfCurrent returns false
        var published = false
        val publishRes = access.publishIfCurrent(staleToken) { published = true }
        assertFalse(publishRes)
        assertFalse(published)

        // Stale token navigation is rejected
        val navRes = access.validateNavigation(staleToken, "task-1")
        assertFalse(navRes)
    }

    @Test
    fun testTokenValidatedSetCompleted() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        // Save an incomplete task
        val initialTodo = createDefaultTodo(title = "Buy groceries")
        store.saveTodoLocally(initialTodo)

        // 1. Set completed to true -> Changed
        val res1 = access.setCompleted(token1, initialTodo.id, true, 2000L)
        assertTrue(res1 is SurfaceResult.Value)
        assertEquals(CompletionChange.Changed, res1.value)

        val updated = store.loadTodos().first { it.id == initialTodo.id }
        assertTrue(updated.completed)

        // 2. Set completed to true again -> Unchanged
        val res2 = access.setCompleted(token1, initialTodo.id, true, 3000L)
        assertTrue(res2 is SurfaceResult.Value)
        assertEquals(CompletionChange.Unchanged, res2.value)

        // 3. Set completed on missing task -> Missing
        val res3 = access.setCompleted(token1, "missing-task-id", true, 4000L)
        assertTrue(res3 is SurfaceResult.Value)
        assertEquals(CompletionChange.Missing, res3.value)
    }

    @Test
    fun testAdmissionDrainingWithInFlightOperation() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val inFlightEntered = CompletableDeferred<Unit>()
        val inFlightProceed = CompletableDeferred<Unit>()
        var inFlightCompleted = false

        // Launch an admitted operation held open
        backgroundScope.launch {
            access.withSurfaceAdmission(token1) {
                inFlightEntered.complete(Unit)
                inFlightProceed.await()
                inFlightCompleted = true
            }
        }
        inFlightEntered.await()

        // Launch drain concurrently
        var drainCompleted = false
        val drainJob = backgroundScope.launch {
            val drained = access.closeAndDrain(2000L)
            assertTrue(drained)
            drainCompleted = true
        }
        yield()

        // While in-flight is held, new admissions are rejected
        val rejectedRes = access.read(token1)
        assertTrue(rejectedRes is SurfaceResult.Rejected)
        assertFalse(drainCompleted)

        // Allow in-flight to finish
        inFlightProceed.complete(Unit)
        drainJob.join()

        assertTrue(inFlightCompleted)
        assertTrue(drainCompleted)
    }

    @Test
    fun testDrainReopenNewInFlightDrainCycle() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        // 1. First cycle with an active in-flight operation and real CompletableDeferred drain signal
        val c1Entered = CompletableDeferred<Unit>()
        val c1Proceed = CompletableDeferred<Unit>()
        var c1Completed = false
        backgroundScope.launch {
            access.withSurfaceAdmission(token1) {
                c1Entered.complete(Unit)
                c1Proceed.await()
                c1Completed = true
            }
        }
        c1Entered.await()

        var c1DrainDone = false
        val c1DrainJob = backgroundScope.launch {
            val drained = access.closeAndDrain(2000L)
            assertTrue(drained)
            c1DrainDone = true
        }
        yield()
        assertFalse(c1DrainDone, "Cycle 1 drain must not complete while in-flight is held")
        c1Proceed.complete(Unit)
        c1DrainJob.join()
        assertTrue(c1Completed)
        assertTrue(c1DrainDone)

        // 2. Re-authenticate on token2 (new lifecycle)
        val token2 = SurfaceAccountToken(accountKey1, 2L)
        access.onAccountAuthenticated(token2, store)

        // 3. Launch an in-flight operation on token2
        val c2Entered = CompletableDeferred<Unit>()
        val c2Proceed = CompletableDeferred<Unit>()
        var c2Completed = false
        backgroundScope.launch {
            access.withSurfaceAdmission(token2) {
                c2Entered.complete(Unit)
                c2Proceed.await()
                c2Completed = true
            }
        }
        c2Entered.await()

        // 4. Cycle 2 drain must NOT complete immediately on stale signal from Cycle 1
        var c2DrainDone = false
        val c2DrainJob = backgroundScope.launch {
            val drained = access.closeAndDrain(2000L)
            assertTrue(drained)
            c2DrainDone = true
        }
        yield()
        assertFalse(c2DrainDone, "Cycle 2 drain must not complete prematurely on reused signal")

        // 5. Complete Cycle 2 in-flight and join drain
        c2Proceed.complete(Unit)
        c2DrainJob.join()
        assertTrue(c2Completed)
        assertTrue(c2DrainDone)
    }

    @Test
    fun testConcurrentCloseAndDrainShareSignal() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val inFlightEntered = CompletableDeferred<Unit>()
        val inFlightProceed = CompletableDeferred<Unit>()
        backgroundScope.launch {
            access.withSurfaceAdmission(token1) {
                inFlightEntered.complete(Unit)
                inFlightProceed.await()
            }
        }
        inFlightEntered.await()

        var drain1Done = false
        var drain2Done = false
        val drain1 = backgroundScope.launch {
            drain1Done = access.closeAndDrain(2000L)
        }
        val drain2 = backgroundScope.launch {
            drain2Done = access.closeAndDrain(2000L)
        }
        yield()
        assertFalse(drain1Done)
        assertFalse(drain2Done)

        inFlightProceed.complete(Unit)
        drain1.join()
        drain2.join()

        assertTrue(drain1Done, "First concurrent caller must succeed")
        assertTrue(drain2Done, "Second concurrent caller must succeed on shared signal")
    }
    @Test
    fun testPublicationAcrossAccountSwitch() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        // Valid publication succeeds
        var published = false
        val p1 = access.publishIfCurrent(token1) { published = true }
        assertTrue(p1)
        assertTrue(published)

        // Sign out / invalidate
        access.onSignedOut()

        // Stale token publication rejected
        var stalePublished = false
        val p2 = access.publishIfCurrent(token1) { stalePublished = true }
        assertFalse(p2)
        assertFalse(stalePublished)
    }

    @Test
    fun testQuickAddTitleValidationAndSubmission() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        var syncTriggered = false
        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = { syncTriggered = true }
        )
        controller.startObserving(token1, store)

        // 1. ChangeTitle with invalid title sets error
        controller.onAction(StickyTaskAction.ChangeTitle(token1, "   "))
        assertEquals("   ", controller.stickyState.value.quickAdd.rawTitle)
        assertNotNull(controller.stickyState.value.quickAdd.error)

        // 2. Submit with invalid title keeps error and refuses submit
        controller.onAction(StickyTaskAction.Submit(token1))
        assertFalse(controller.stickyState.value.quickAdd.submitting)

        // 3. ChangeTitle with valid title clears error
        controller.onAction(StickyTaskAction.ChangeTitle(token1, "Submit tax report"))
        assertEquals("Submit tax report", controller.stickyState.value.quickAdd.rawTitle)
        assertNull(controller.stickyState.value.quickAdd.error)

        // 4. Submit with valid title creates task and clears quickAdd buffer
        controller.onAction(StickyTaskAction.Submit(token1))
        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.quickAdd.submitting) {
                    delay(20)
                }
            }
        }
        assertTrue(syncTriggered, "syncTriggered was false; state is: ${controller.stickyState.value.quickAdd}")
        assertNull(controller.stickyState.value.quickAdd.error)
        assertFalse(controller.stickyState.value.quickAdd.submitting)

        val savedTodos = store.loadTodos()
        assertEquals(1, savedTodos.size)
        assertEquals("Submit tax report", savedTodos[0].title)
        assertFalse(savedTodos[0].completed)
    }

    @Test
    fun testEditingQuickAddWhileSavePendingPreservesNewText() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {}
        )
        controller.startObserving(token1, store)

        controller.onAction(StickyTaskAction.ChangeTitle(token1, "Task A"))
        controller.onAction(StickyTaskAction.Submit(token1))

        // While submit is processing, user types more
        controller.onAction(StickyTaskAction.ChangeTitle(token1, "Task A and extra notes"))

        testScheduler.advanceUntilIdle()

        // Because text changed during async submit, the new text must NOT be cleared!
        assertEquals("Task A and extra notes", controller.stickyState.value.quickAdd.rawTitle)
    }

    @Test
    fun testQuickAddDoesNotMutateMainEditor() = runTest {
        val tempDir = Files.createTempDirectory("tabbyrush-quickadd-isolation-test").toFile()
        tempDir.deleteOnExit()
        val tempLock = File(tempDir, "tabbyrush.lock")
        val guard = SingleInstanceGuard.tryAcquire(tempLock)!!
        val files = JvmAccountFiles(baseDir = tempDir)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

        val controller = DesktopController(
            mode = BuildMode.DEBUG,
            scope = scope,
            instanceGuard = guard,
            accountFiles = files
        )
        try {
            val runtime = controller.getOrCreateAccountRuntime(accountKey1)
            val token = runtime.token

            // Open an editor in main window
            controller.onAction(TodoAction.OpenCreate("Main draft in progress"))
            withContext(Dispatchers.Default) {
                withTimeout(5000L) {
                    while (controller.uiState.value.editor == null) {
                        delay(20)
                    }
                }
            }
            assertEquals("Main draft in progress", controller.uiState.value.editor?.rawTitle)
            controller.onStickyAction(StickyTaskAction.ChangeTitle(token, "Sticky task title"))
            assertEquals("Sticky task title", controller.stickyState.value.quickAdd.rawTitle)
            assertEquals("Main draft in progress", controller.uiState.value.editor?.rawTitle)

            // Submit sticky quick-add
            controller.onStickyAction(StickyTaskAction.Submit(token))
            withContext(Dispatchers.Default) {
                withTimeout(5000L) {
                    while (controller.stickyState.value.quickAdd.submitting) {
                        delay(20)
                    }
                }
            }
            assertEquals("", controller.stickyState.value.quickAdd.rawTitle)
            assertEquals("Main draft in progress", controller.uiState.value.editor?.rawTitle)

            controller.disposeSuspend()
        } finally {
            scope.cancel()
            guard.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUnfilteredStickySections() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val todo1 = createDefaultTodo(title = "Task 1", nowEpochMs = 1000L).copy(completed = false)
        val todo2 = createDefaultTodo(title = "Task 2", nowEpochMs = 2000L).copy(completed = true)
        store.saveTodoLocally(todo1)
        store.saveTodoLocally(todo2)

        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {}
        )
        controller.startObserving(token1, store)
        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.sections.incomplete.isEmpty()) {
                    delay(20)
                }
            }
        }
        val state = controller.stickyState.value
        assertEquals(1, state.sections.incomplete.size, "Incomplete size was ${state.sections.incomplete.size}, full state: $state")
        assertEquals(1, state.sections.completed.size)
        assertEquals("Task 2", state.sections.completed[0].title)
    }

    @Test
    fun testStateClearedOnSignedOut() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        store.saveTodoLocally(createDefaultTodo(title = "Task A"))

        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {}
        )
        controller.startObserving(token1, store)
        controller.onAction(StickyTaskAction.ChangeTitle(token1, "Draft task"))
        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.sections.incomplete.isEmpty()) {
                    delay(20)
                }
            }
        }

        assertEquals(1, controller.stickyState.value.sections.incomplete.size)
        assertEquals("Draft task", controller.stickyState.value.quickAdd.rawTitle)

        // Transition away from Ready (e.g. signed out)
        access.onSignedOut()
        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.readiness != SurfaceReadiness.SignedOut) {
                    delay(20)
                }
            }
        }

        assertEquals(SurfaceReadiness.SignedOut, controller.stickyState.value.readiness)
        assertEquals(0, controller.stickyState.value.sections.incomplete.size)
        assertEquals(0, controller.stickyState.value.sections.completed.size)
        assertEquals("", controller.stickyState.value.quickAdd.rawTitle)
        assertNull(controller.stickyState.value.quickAdd.error)
    }

    @Test
    fun testWholeCompactGroupsMixedGroupIntactInActive() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val root = createDefaultTodo(title = "Parent Project", nowEpochMs = 1000L).copy(completed = false)
        val child1 = createDefaultTodo(title = "Child 1 Active", nowEpochMs = 1100L).copy(
            parentTaskId = root.id,
            completed = false
        )
        val child2 = createDefaultTodo(title = "Child 2 Done", nowEpochMs = 1200L).copy(
            parentTaskId = root.id,
            completed = true
        )
        store.saveTodoLocally(root)
        store.saveTodoLocally(child1)
        store.saveTodoLocally(child2)

        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {}
        )
        controller.startObserving(token1, store)

        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.projectedSections.incomplete.size < 3) {
                    delay(20)
                }
            }
        }

        val projected = controller.stickyState.value.projectedSections
        assertEquals(3, projected.incomplete.size)
        assertEquals(0, projected.completed.size)

        // Root checks
        val rootItem = projected.incomplete[0]
        assertEquals(root.id, rootItem.todo.id)
        assertFalse(rootItem.isChild)
        assertTrue(rootItem.hasLiveChildren)
        assertFalse(rootItem.effectiveCompleted)
        assertEquals(2, rootItem.childCount)
        assertEquals(1, rootItem.completedChildCount)

        // Child 1 checks
        val child1Item = projected.incomplete.first { it.todo.id == child1.id }
        assertEquals(child1.id, child1Item.todo.id)
        assertTrue(child1Item.isChild)
        assertFalse(child1Item.hasLiveChildren)
        assertFalse(child1Item.effectiveCompleted)

        // Child 2 checks (completed sibling remains in mixed group under Active)
        val child2Item = projected.incomplete.first { it.todo.id == child2.id }
        assertEquals(child2.id, child2Item.todo.id)
        assertTrue(child2Item.isChild)
        assertFalse(child2Item.hasLiveChildren)
        assertTrue(child2Item.effectiveCompleted)
    }

    @Test
    fun testFullyCompleteGroupMigratesToCompletedSection() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val root = createDefaultTodo(title = "Parent Project", nowEpochMs = 1000L).copy(completed = false)
        val child1 = createDefaultTodo(title = "Child 1", nowEpochMs = 1100L).copy(
            parentTaskId = root.id,
            completed = true
        )
        val child2 = createDefaultTodo(title = "Child 2", nowEpochMs = 1200L).copy(
            parentTaskId = root.id,
            completed = true
        )
        store.saveTodoLocally(root)
        store.saveTodoLocally(child1)
        store.saveTodoLocally(child2)

        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {}
        )
        controller.startObserving(token1, store)

        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.projectedSections.completed.size < 3) {
                    delay(20)
                }
            }
        }

        val projected = controller.stickyState.value.projectedSections
        assertEquals(0, projected.incomplete.size)
        assertEquals(3, projected.completed.size)

        val rootItem = projected.completed[0]
        assertEquals(root.id, rootItem.todo.id)
        assertTrue(rootItem.effectiveCompleted)
        assertTrue(rootItem.hasLiveChildren)
        assertEquals(2, rootItem.childCount)
        assertEquals(2, rootItem.completedChildCount)
    }

    @Test
    fun testDirectParentCompletionRejectedNoMutationOrSync() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val root = createDefaultTodo(title = "Parent Project", nowEpochMs = 1000L).copy(completed = false)
        val child = createDefaultTodo(title = "Child Incomplete", nowEpochMs = 1100L).copy(
            parentTaskId = root.id,
            completed = false
        )
        store.saveTodoLocally(root)
        store.saveTodoLocally(child)

        var syncTriggered = false
        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = { syncTriggered = true }
        )
        controller.startObserving(token1, store)

        // Attempt direct parent completion
        controller.onAction(StickyTaskAction.SetCompleted(token1, root.id, true))

        withContext(Dispatchers.Default) {
            delay(100)
        }

        // Must reject: root still incomplete, sync not triggered
        val storedRoot = store.loadTodos().first { it.id == root.id }
        assertFalse(storedRoot.completed)
        assertFalse(syncTriggered)
    }

    @Test
    fun testChildDesiredStateCompletionUpdatesParentAndTriggersSync() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val root = createDefaultTodo(title = "Parent Project", nowEpochMs = 1000L).copy(completed = false)
        val child = createDefaultTodo(title = "Only Child", nowEpochMs = 1100L).copy(
            parentTaskId = root.id,
            completed = false
        )
        store.saveTodoLocally(root)
        store.saveTodoLocally(child)

        var syncTriggered = false
        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = { syncTriggered = true }
        )
        controller.startObserving(token1, store)

        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.projectedSections.incomplete.size < 2) {
                    delay(20)
                }
            }
        }

        // Complete child
        controller.onAction(StickyTaskAction.SetCompleted(token1, child.id, true))

        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (!syncTriggered || controller.stickyState.value.projectedSections.completed.size < 2) {
                    delay(20)
                }
            }
        }

        assertTrue(syncTriggered)
        val projected = controller.stickyState.value.projectedSections
        assertEquals(2, projected.completed.size)
        assertTrue(projected.completed[0].effectiveCompleted)
    }

    @Test
    fun testIndependentOwnDeadlinesAndClockRefresh() = runTest {
        val fixedNow = 1_700_000_000_000L
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val taskNoDeadline = createDefaultTodo("No deadline", nowEpochMs = 1000L)
        val taskDateOnly = createDefaultTodo("Date only", nowEpochMs = 1000L).copy(
            scheduleToken = "sched-tok-1",
            deadline = TaskDeadline(
                date = "2026-10-25",
                time = null,
                zone = "UTC",
                offsetSeconds = 0L,
                reminderAnchorTime = "09:00",
                anchorEpochMs = fixedNow + 100_000L,
                overdueEpochMs = fixedNow + 100_000L
            )
        )
        val taskTimedFuture = createDefaultTodo("Timed future", nowEpochMs = 1000L).copy(
            scheduleToken = "sched-tok-2",
            deadline = TaskDeadline(
                date = "2026-10-25",
                time = "14:30",
                zone = "Asia/Saigon",
                offsetSeconds = 25200L,
                reminderAnchorTime = null,
                anchorEpochMs = fixedNow + 200_000L,
                overdueEpochMs = fixedNow + 200_000L
            )
        )
        val taskOverdue = createDefaultTodo("Timed overdue", nowEpochMs = 1000L).copy(
            scheduleToken = "sched-tok-3",
            deadline = TaskDeadline(
                date = "2026-10-20",
                time = "08:00",
                zone = "Asia/Saigon",
                offsetSeconds = 25200L,
                reminderAnchorTime = null,
                anchorEpochMs = fixedNow - 50_000L,
                overdueEpochMs = fixedNow - 50_000L
            )
        )
        val taskCompletedOverdue = createDefaultTodo("Completed overdue", nowEpochMs = 1000L).copy(
            completed = true,
            scheduleToken = "sched-tok-4",
            deadline = TaskDeadline(
                date = "2026-10-20",
                time = "08:00",
                zone = "Asia/Saigon",
                offsetSeconds = 25200L,
                reminderAnchorTime = null,
                anchorEpochMs = fixedNow - 50_000L,
                overdueEpochMs = fixedNow - 50_000L
            )
        )
        store.saveTodoLocally(taskNoDeadline)
        store.saveTodoLocally(taskDateOnly)
        store.saveTodoLocally(taskTimedFuture)
        store.saveTodoLocally(taskOverdue)
        store.saveTodoLocally(taskCompletedOverdue)

        var simulatedClock = fixedNow
        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {},
            clock = { simulatedClock }
        )
        controller.startObserving(token1, store)

        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.projectedSections.incomplete.size < 4) {
                    delay(20)
                }
            }
        }

        val incomplete = controller.stickyState.value.projectedSections.incomplete
        val itemNoDl = incomplete.first { it.todo.id == taskNoDeadline.id }
        assertNull(itemNoDl.deadlinePresentation)

        val itemDateOnly = incomplete.first { it.todo.id == taskDateOnly.id }
        assertNotNull(itemDateOnly.deadlinePresentation)
        assertEquals("Due 2026-10-25", itemDateOnly.deadlinePresentation?.label)
        assertFalse(itemDateOnly.deadlinePresentation!!.isOverdue)

        val itemTimed = incomplete.first { it.todo.id == taskTimedFuture.id }
        assertNotNull(itemTimed.deadlinePresentation)
        assertEquals("Due 2026-10-25 14:30 (Asia/Saigon)", itemTimed.deadlinePresentation?.label)
        assertFalse(itemTimed.deadlinePresentation!!.isOverdue)

        val itemOverdue = incomplete.first { it.todo.id == taskOverdue.id }
        assertNotNull(itemOverdue.deadlinePresentation)
        assertEquals("Due 2026-10-20 08:00 (Asia/Saigon)", itemOverdue.deadlinePresentation?.label)
        assertTrue(itemOverdue.deadlinePresentation!!.isOverdue)
        assertEquals("Overdue", itemOverdue.deadlinePresentation?.overdueLabel)

        // Completed task with past deadline must NOT be overdue
        val completed = controller.stickyState.value.projectedSections.completed
        val itemDone = completed.first { it.todo.id == taskCompletedOverdue.id }
        assertNotNull(itemDone.deadlinePresentation)
        assertFalse(itemDone.deadlinePresentation!!.isOverdue)

        // Advance clock so taskDateOnly becomes overdue, then refresh
        simulatedClock = fixedNow + 150_000L
        controller.refreshDeadlines(simulatedClock)
        val refreshedDateOnly = controller.stickyState.value.projectedSections.incomplete.first { it.todo.id == taskDateOnly.id }
        assertTrue(refreshedDateOnly.deadlinePresentation!!.isOverdue)
        assertEquals("Due 2026-10-25", refreshedDateOnly.deadlinePresentation?.label)
    }

    @Test
    fun testAccountSwitchingWhileSubmitPendingRejectsStalePublish() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        var syncCount = 0
        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = { syncCount++ }
        )
        controller.startObserving(token1, store)

        controller.onAction(StickyTaskAction.ChangeTitle(token1, "Account 1 task"))
        controller.onAction(StickyTaskAction.Submit(token1))

        // Immediately switch account before submit completes
        val token2 = SurfaceAccountToken(AccountKey("2222222222222222222222222222222222222222222222222222222222222222"), 1L)
        val db2 = createInMemoryJvmDatabaseBuilder().build()
        val store2 = RoomAccountStore(token2.accountKey, "user2", db2, "tabbyrush-test2")
        try {
            access.onAccountAuthenticated(token2, store2)

            withContext(Dispatchers.Default) {
                delay(100)
            }

            // Sync should NOT be triggered for token1 after switch
            assertEquals(0, syncCount)
            // State for new account must not have Account 1 task draft
            assertEquals("", controller.stickyState.value.quickAdd.rawTitle)
        } finally {
            try { db2.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun testQuickAddAlwaysCreatesIndependentRoot() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {}
        )
        controller.startObserving(token1, store)

        controller.onAction(StickyTaskAction.ChangeTitle(token1, "Standalone Root Task"))
        controller.onAction(StickyTaskAction.Submit(token1))

        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.sections.incomplete.isEmpty()) {
                    delay(20)
                }
            }
        }

        val saved = store.loadTodos().first { it.title == "Standalone Root Task" }
        assertNull(saved.parentTaskId, "Sticky quick-add must create independent root tasks only")
    }

    @Test
    fun testStartupOrderControllerConstructedBeforeAccountAuthentication() = runTest {
        val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            for (i in 0 until 10) {
                val access = DesktopTaskSurfaceAccess()
                val controller = DesktopStickyTaskController(
                    access = access,
                    scope = testScope,
                    onOpenApp = {},
                    onQuit = {},
                    onTriggerSync = {}
                )

                val task = createDefaultTodo(title = "Startup Task $i", nowEpochMs = 1000L + i)
                store.saveTodoLocally(task)

                // Interleave authentication and observation on Default dispatcher
                access.onAccountAuthenticated(token1, store)
                controller.startObserving(token1, store)

                withContext(Dispatchers.Default) {
                    withTimeout(5000L) {
                        while (controller.stickyState.value.projectedSections.incomplete.isEmpty()) {
                            delay(10)
                        }
                    }
                }

                val state = controller.stickyState.value
                assertEquals(SurfaceReadiness.Ready(token1), state.readiness)
                assertEquals(1, state.projectedSections.incomplete.size)
                assertEquals("Startup Task $i", state.projectedSections.incomplete[0].todo.title)
                controller.stopObserving()
                store.deleteTodoLocally(task.id)
            }
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testRapidAccountSwitchReadyInterleavingUnderMultiThreadedDispatcher() = runTest {
        val accountKey2 = AccountKey("2222222222222222222222222222222222222222222222222222222222222222")
        val token2 = SurfaceAccountToken(accountKey2, 1L)
        val db2 = createInMemoryJvmDatabaseBuilder().build()
        val store2 = RoomAccountStore(
            accountKey = accountKey2,
            userId = "test-user-2",
            database = db2,
            appId = "tabbyrush-test"
        )

        val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            store.saveTodoLocally(createDefaultTodo(title = "Account 1 Task", nowEpochMs = 1000L))
            store2.saveTodoLocally(createDefaultTodo(title = "Account 2 Task", nowEpochMs = 2000L))

            val access = DesktopTaskSurfaceAccess()
            val controller = DesktopStickyTaskController(
                access = access,
                scope = testScope,
                onOpenApp = {},
                onQuit = {},
                onTriggerSync = {}
            )

            // 1. Account 1 is active
            access.onAccountAuthenticated(token1, store)
            controller.startObserving(token1, store)

            withContext(Dispatchers.Default) {
                withTimeout(5000L) {
                    while (controller.stickyState.value.projectedSections.incomplete.isEmpty()) {
                        delay(10)
                    }
                }
            }
            assertEquals("Account 1 Task", controller.stickyState.value.projectedSections.incomplete[0].todo.title)

            // 2. Rapid switch to Account 2: startObserving(token2) runs BEFORE onAccountAuthenticated(token2)
            controller.startObserving(token2, store2)
            access.onAccountAuthenticated(token2, store2)

            withContext(Dispatchers.Default) {
                withTimeout(5000L) {
                    while (controller.stickyState.value.readiness != SurfaceReadiness.Ready(token2) ||
                        controller.stickyState.value.projectedSections.incomplete.isEmpty()) {
                        delay(10)
                    }
                }
            }

            val finalState = controller.stickyState.value
            assertEquals(SurfaceReadiness.Ready(token2), finalState.readiness)
            assertEquals(1, finalState.projectedSections.incomplete.size)
            assertEquals("Account 2 Task", finalState.projectedSections.incomplete[0].todo.title)
            // Verify Account 1 task was NOT leaked
            assertTrue(finalState.projectedSections.incomplete.none { it.todo.title == "Account 1 Task" })
        } finally {
            testScope.cancel()
            try { db2.close() } catch (_: Exception) {}
        }
    }
    @Test
    fun testInitialSnapshotProcessedBeforeReadinessCollectorRuns() = runTest {
        val access = DesktopTaskSurfaceAccess()
        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {}
        )
        assertEquals(SurfaceReadiness.Restoring, controller.stickyState.value.readiness)

        val task = createDefaultTodo(title = "Immediate Task", nowEpochMs = 1000L)
        store.saveTodoLocally(task)

        access.onAccountAuthenticated(token1, store)
        controller.startObserving(token1, store)

        withContext(Dispatchers.Default) {
            withTimeout(5000L) {
                while (controller.stickyState.value.projectedSections.incomplete.isEmpty()) {
                    delay(10)
                }
            }
        }

        // The initial snapshot must be present and not dropped by readiness gating
        assertEquals(1, controller.stickyState.value.projectedSections.incomplete.size)
        assertEquals("Immediate Task", controller.stickyState.value.projectedSections.incomplete[0].todo.title)
    }

    @Test
    fun testQuickAddWithMismatchedTokenIsFenced() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {}
        )
        controller.startObserving(token1, store)

        // Submit with stale/mismatched token
        controller.onAction(StickyTaskAction.ChangeTitle(staleToken, "Stale Token Task"))
        // The rawTitle must not update because token doesn't match
        assertEquals("", controller.stickyState.value.quickAdd.rawTitle)

        controller.onAction(StickyTaskAction.Submit(staleToken))
        // No task must be saved in store
        val todos = store.loadTodos()
        assertTrue(todos.isEmpty())
    }

    @Test
    fun testStickyZoomActionsAndClamping() = runTest {
        val access = DesktopTaskSurfaceAccess()
        var reportedScale: Float? = null
        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {},
            initialFontScale = DEFAULT_STICKY_FONT_SCALE,
            onFontScaleChanged = { reportedScale = it }
        )

        assertEquals(DEFAULT_STICKY_FONT_SCALE, controller.stickyState.value.fontScale)

        // 1. ZoomIn increases by STICKY_FONT_SCALE_STEP (0.125f)
        controller.onAction(StickyTaskAction.ZoomIn)
        assertEquals(1.125f, controller.stickyState.value.fontScale)
        assertEquals(1.125f, reportedScale)

        // 2. ZoomIn up to MAX_STICKY_FONT_SCALE (1.50f)
        controller.onAction(StickyTaskAction.ZoomIn)
        assertEquals(1.25f, controller.stickyState.value.fontScale)
        controller.onAction(StickyTaskAction.ZoomIn)
        assertEquals(1.375f, controller.stickyState.value.fontScale)
        controller.onAction(StickyTaskAction.ZoomIn)
        assertEquals(1.50f, controller.stickyState.value.fontScale)

        // 3. ZoomIn past MAX_STICKY_FONT_SCALE is clamped and does not report a change
        reportedScale = null
        controller.onAction(StickyTaskAction.ZoomIn)
        assertEquals(MAX_STICKY_FONT_SCALE, controller.stickyState.value.fontScale)
        assertNull(reportedScale)

        // 4. ZoomOut decreases by STICKY_FONT_SCALE_STEP
        controller.onAction(StickyTaskAction.ZoomOut)
        assertEquals(1.375f, controller.stickyState.value.fontScale)
        assertEquals(1.375f, reportedScale)

        // 5. ResetZoom restores DEFAULT_STICKY_FONT_SCALE (1.0f)
        controller.onAction(StickyTaskAction.ResetZoom)
        assertEquals(DEFAULT_STICKY_FONT_SCALE, controller.stickyState.value.fontScale)
        assertEquals(DEFAULT_STICKY_FONT_SCALE, reportedScale)

        // 6. ResetZoom when already at default is a no-op
        reportedScale = null
        controller.onAction(StickyTaskAction.ResetZoom)
        assertEquals(DEFAULT_STICKY_FONT_SCALE, controller.stickyState.value.fontScale)
        assertNull(reportedScale)

        // 7. ZoomOut down to MIN_STICKY_FONT_SCALE (0.50f)
        controller.onAction(StickyTaskAction.ZoomOut) // 0.875f
        controller.onAction(StickyTaskAction.ZoomOut) // 0.750f
        controller.onAction(StickyTaskAction.ZoomOut) // 0.625f
        controller.onAction(StickyTaskAction.ZoomOut) // 0.500f
        assertEquals(MIN_STICKY_FONT_SCALE, controller.stickyState.value.fontScale)
        assertEquals(MIN_STICKY_FONT_SCALE, reportedScale)

        // 8. ZoomOut past MIN_STICKY_FONT_SCALE (0.50f) is clamped
        reportedScale = null
        controller.onAction(StickyTaskAction.ZoomOut)
        assertEquals(MIN_STICKY_FONT_SCALE, controller.stickyState.value.fontScale)
        assertNull(reportedScale)
    }

    @Test
    fun testComputeEffectiveStickyFontScale() {
        // Default width and 1.0 manual zoom yields 1.0
        assertEquals(1.0f, computeEffectiveStickyFontScale(360.0, 1.0f))

        // Narrower window yields proportionally smaller font scale
        val narrowScale = computeEffectiveStickyFontScale(280.0, 1.0f)
        assertEquals((280.0f / 360.0f), narrowScale, 0.001f)

        // Manual zoom scales relative to window width
        val manualUnder75 = computeEffectiveStickyFontScale(280.0, 0.75f)
        assertEquals((280.0f / 360.0f) * 0.75f, manualUnder75, 0.001f)

        // Clamping to MIN_STICKY_FONT_SCALE (0.50f) on tiny windows
        val tinyWindowScale = computeEffectiveStickyFontScale(100.0, 1.0f)
        assertEquals(MIN_STICKY_FONT_SCALE, tinyWindowScale)

        // Clamping to MAX_STICKY_FONT_SCALE (1.50f) on huge windows
        val hugeWindowScale = computeEffectiveStickyFontScale(800.0, 1.0f)
        assertEquals(MAX_STICKY_FONT_SCALE, hugeWindowScale)

        // Invalid dimensions safely fall back to manual zoom
        assertEquals(1.0f, computeEffectiveStickyFontScale(Double.NaN, 1.0f))
        assertEquals(1.0f, computeEffectiveStickyFontScale(-50.0, 1.0f))
    }
    @Test
    fun testStickyFontScalePreservedAcrossReadinessTransitions() = runTest {
        val access = DesktopTaskSurfaceAccess()
        val controller = DesktopStickyTaskController(
            access = access,
            scope = backgroundScope,
            onOpenApp = {},
            onQuit = {},
            onTriggerSync = {},
            initialFontScale = 1.25f
        )
        assertEquals(1.25f, controller.stickyState.value.fontScale)

        // Authenticate
        access.onAccountAuthenticated(token1, store)
        testScheduler.advanceUntilIdle()
        assertEquals(1.25f, controller.stickyState.value.fontScale)

        // Start observing
        controller.startObserving(token1, store)
        testScheduler.advanceUntilIdle()
        assertEquals(1.25f, controller.stickyState.value.fontScale)

        // Stop observing
        controller.stopObserving()
        assertEquals(1.25f, controller.stickyState.value.fontScale)

        // Sign out
        access.onSignedOut()
        testScheduler.advanceUntilIdle()
        assertEquals(1.25f, controller.stickyState.value.fontScale)

        // Close and drain (unavailable)
        access.closeAndDrain()
        testScheduler.advanceUntilIdle()
        assertEquals(1.25f, controller.stickyState.value.fontScale)

        // Re-authenticate with different token
        val token2 = SurfaceAccountToken(AccountKey("b".repeat(64)), 1L)
        access.onAccountAuthenticated(token2, store)
        testScheduler.advanceUntilIdle()
        assertEquals(1.25f, controller.stickyState.value.fontScale)
    }

    @Test
    fun testDesktopControllerStickyFontScalePersistence() = runTest {
        val tempDir = Files.createTempDirectory("tabbyrush-fontscale-persistence-test").toFile()
        tempDir.deleteOnExit()
        val tempLock = File(tempDir, "tabbyrush.lock")
        val guard = SingleInstanceGuard.tryAcquire(tempLock)!!
        val files = JvmAccountFiles(baseDir = tempDir)
        val prefsFile = File(tempDir, "sticky-window.properties")
        val preferences = StickyWindowPreferences(prefsFile)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

        preferences.save(
            StickyWindowPresentation(
                visible = true,
                geometry = StickyWindowGeometry(10.0, 20.0, 360.0, 520.0, 1.0, 1.0),
                fontScale = 1.25f
            )
        )

        val controller = DesktopController(
            mode = BuildMode.DEBUG,
            scope = scope,
            instanceGuard = guard,
            accountFiles = files,
            preferences = preferences,
            initialStickyPresentation = preferences.load()
        )

        try {
            assertEquals(1.25f, controller.stickyState.value.fontScale)

            // Zoom in via controller action
            controller.onStickyAction(StickyTaskAction.ZoomIn)
            assertEquals(1.375f, controller.stickyState.value.fontScale)

            // Verify persisted in preferences
            val reloaded = preferences.load()
            assertEquals(1.375f, reloaded.fontScale)
            assertTrue(reloaded.visible)
            assertEquals(360.0, reloaded.geometry.widthDp)

            // Update geometry must preserve fontScale
            controller.updateStickyGeometry(
                StickyWindowGeometry(50.0, 60.0, 400.0, 600.0, 1.0, 1.0)
            )
            controller.flushStickyGeometry()
            val reloadedAfterGeom = preferences.load()
            assertEquals(1.375f, reloadedAfterGeom.fontScale)
            assertEquals(400.0, reloadedAfterGeom.geometry.widthDp)

            // Reset zoom
            controller.onStickyAction(StickyTaskAction.ResetZoom)
            assertEquals(DEFAULT_STICKY_FONT_SCALE, controller.stickyState.value.fontScale)
            val reloadedAfterReset = preferences.load()
            assertEquals(DEFAULT_STICKY_FONT_SCALE, reloadedAfterReset.fontScale)
        } finally {
            controller.disposeSuspend()
        }
    }

    @Test
    fun testConcurrentZoomUpdatesPersistConsistentState() = runTest {
        val tempDir = Files.createTempDirectory("tabbyrush-concurrent-zoom-test").toFile()
        tempDir.deleteOnExit()
        val tempLock = File(tempDir, "tabbyrush.lock")
        val guard = SingleInstanceGuard.tryAcquire(tempLock)!!
        val files = JvmAccountFiles(baseDir = tempDir)
        val prefsFile = File(tempDir, "sticky-window.properties")
        val preferences = StickyWindowPreferences(prefsFile)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

        val controller = DesktopController(
            mode = BuildMode.DEBUG,
            scope = scope,
            instanceGuard = guard,
            accountFiles = files,
            preferences = preferences,
            initialStickyPresentation = preferences.load()
        )

        try {
            val threads = (1..8).map { i ->
                kotlin.concurrent.thread {
                    for (j in 1..20) {
                        if ((i + j) % 2 == 0) {
                            controller.onStickyAction(StickyTaskAction.ZoomIn)
                        } else {
                            controller.onStickyAction(StickyTaskAction.ZoomOut)
                        }
                    }
                }
            }
            threads.forEach { it.join() }

            val liveScale = controller.stickyState.value.fontScale
            val persistedScale = preferences.load().fontScale
            assertEquals(liveScale, persistedScale)
        } finally {
            controller.disposeSuspend()
        }
    }
}
