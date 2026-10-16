package dev.doggo.desktop

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.CompletionChange
import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.contract.SurfaceResult
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.contract.TodoFilter
import dev.doggo.shared.data.JvmAccountFiles
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.data.createInMemoryJvmDatabaseBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
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

    private lateinit var db: dev.doggo.shared.data.DoggoDatabase
    private lateinit var store: RoomAccountStore

    @BeforeTest
    fun setUp() {
        db = createInMemoryJvmDatabaseBuilder().build()
        store = RoomAccountStore(accountKey1, "test-user", db, "doggo-test")
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
        val tempDir = Files.createTempDirectory("doggo-quickadd-isolation-test").toFile()
        tempDir.deleteOnExit()
        val tempLock = File(tempDir, "doggo.lock")
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
}
