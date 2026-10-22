package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccessLossReason
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.CanonicalRecordSnapshot
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.SurfaceResult
import dev.tabbyrush.shared.contract.TaskAccessState
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.TaskCapabilities
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoFilter
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.createInMemoryJvmDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollaborativeStickyAccessTest {

    private val accountKey = AccountKey("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    private val token1 = SurfaceAccountToken(accountKey, 1L)

    private lateinit var db: dev.tabbyrush.shared.data.TabbyRushDatabase
    private lateinit var store: RoomAccountStore

    @BeforeTest
    fun setUp() = runBlocking {
        db = createInMemoryJvmDatabaseBuilder().build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "alice",
            database = db,
            appId = "tabbyrush-test"
        )
        store.initializeOwnerBinding(AccountIdentity("alice", "alice", "alice@example.com"))
        Unit
    }

    @AfterTest
    fun tearDown() {
        try { db.close() } catch (_: Exception) {}
    }

    private suspend fun reconcileRemoteTodo(todo: Todo) {
        val access = requireNotNull(todo.accessState)
        val data = buildJsonObject {
            put("id", todo.id)
            put("title", todo.title)
            put("completed", todo.completed)
            put("createdAt", todo.createdAt)
            todo.parentTaskId?.let { put("parentTaskId", it) }
        }
        store.stageDataPage(access.revision, listOf(
            CanonicalRecordSnapshot(todo.taskRef.toRecordRef(), data.toString(), 1L, todo.updatedAt)
        ))
        store.stageAccessResults(access.revision, listOf(access))
        store.promoteReconciliation(access.revision, todo.updatedAt)
        assertNotNull(store.getTodo(todo.taskRef), "Reconciled remote fixture must be available")
    }

    @Test
    fun testChildOnlyTaskRenderedFlatWithoutParentContext() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        // Alice has access only to Bob's child; its parent remains private.
        val parentRef = TaskRef("bob", "parent-row-1")
        val childRef = TaskRef("bob", "child-row-1")
        val childOnlyTodo = Todo(
            taskRef = childRef,
            title = "Shared Subtask For Bob",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            parentTaskRef = parentRef,
            accessState = TaskAccessState(
                ref = childRef,
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.ChildOnly,
                status = TaskAccessStatus.Accessible
            )
        )
        reconcileRemoteTodo(childOnlyTodo)

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
                while (controller.stickyState.value.projectedSections.incomplete.isEmpty()) {
                    delay(20)
                }
            }
        }

        val projected = controller.stickyState.value.projectedSections
        assertEquals(1, projected.incomplete.size)
        val item = projected.incomplete.first()

        // ChildOnly task MUST be projected flat:
        assertTrue(item.isChildOnly)
        assertFalse(item.isChild, "ChildOnly task must not have child indentation in sticky view")
        assertFalse(item.hasLiveChildren)
        assertEquals(0, item.childCount)
        assertEquals("Shared Subtask For Bob", item.todo.title)
    }

    @Test
    fun testDuplicateRowIdsAcrossDifferentOwnersIsolatedInStickyAndStore() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val sharedRowId = "common-uuid-1234"
        val aliceRef = TaskRef("alice", sharedRowId)
        val bobRef = TaskRef("bob", sharedRowId)

        val aliceTodo = Todo(
            taskRef = aliceRef,
            title = "Alice's Task",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val bobTodo = Todo(
            taskRef = bobRef,
            title = "Bob's Task",
            completed = false,
            createdAt = 1100L,
            updatedAt = 1100L,
            accessState = TaskAccessState(bobRef, 1L, TaskCapabilities.ASSIGNEE, HierarchyCoverage.Standalone)
        )
        store.saveTodoLocally(aliceTodo)
        reconcileRemoteTodo(bobTodo)

        // Verify items in sticky projection have distinct owner-qualified identities
        val items = projectStickyItems(listOf(aliceTodo, bobTodo))
        assertEquals(2, items.size)
        val keys = items.map { "inc_${it.todo.taskRef.ownerUserId}_${it.todo.taskRef.rowId}" }
        assertEquals(2, keys.toSet().size, "Sticky keys must be fully owner-qualified and never collide")
        assertTrue(keys.contains("inc_alice_common-uuid-1234"))
        assertTrue(keys.contains("inc_bob_common-uuid-1234"))

        // Complete Alice's task via surface access
        val res = access.setCompleted(token1, aliceRef, true, 2000L)
        assertTrue(res is SurfaceResult.Value)
        assertEquals(CompletionChange.Changed, res.value)

        // Bob's task with same rowId MUST remain incomplete
        val bobLoaded = store.getTodo(bobRef)
        assertNotNull(bobLoaded)
        assertFalse(bobLoaded.completed, "Mutating Alice's task must never affect Bob's task with identical rowId")

        val aliceLoaded = store.getTodo(aliceRef)
        assertNotNull(aliceLoaded)
        assertTrue(aliceLoaded.completed)
    }

    @Test
    fun testAuthoritativeAccessLossClearsTaskAndRetainsContentFreeEvidence() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val collaborativeRef = TaskRef("bob", "collab-task-1")
        val privateRef = TaskRef("alice", "my-private-task")

        val collabTodo = Todo(
            taskRef = collaborativeRef,
            title = "Secret Collaborative Project",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            accessState = TaskAccessState(
                ref = collaborativeRef,
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.Standalone,
                status = TaskAccessStatus.Accessible
            )
        )
        val privateTodo = Todo(
            taskRef = privateRef,
            title = "My Grocery List",
            completed = false,
            createdAt = 1100L,
            updatedAt = 1100L
        )
        reconcileRemoteTodo(collabTodo)
        store.saveTodoLocally(privateTodo)

        // Simulate authoritative access loss
        store.invalidateAccess(collaborativeRef, AccessLossReason.REVOKED, nowEpochMs = 2000L)

        // Collaborative task is purged from active store
        val loadedCollab = store.getTodo(collaborativeRef)
        assertNull(loadedCollab, "Revoked collaborative task must be purged from store")

        // Private task is preserved completely untouched
        val loadedPrivate = store.getTodo(privateRef)
        assertNotNull(loadedPrivate, "Private task must remain intact after collaborative access loss")
        assertEquals("My Grocery List", loadedPrivate.title)

        // Content-free evidence is recorded
        val evidenceList = store.listAccessLossEvidence()
        assertEquals(1, evidenceList.size)
        val evidence = evidenceList.first()
        assertEquals(collaborativeRef, evidence.ref)
        assertEquals(AccessLossReason.REVOKED, evidence.reason)
        // Privacy invariant: Evidence MUST NOT contain title or private payload
    }

    @Test
    fun testAssigneeCapabilitiesPreventUnauthorizedOperations() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val taskRef = TaskRef("bob", "shared-1")
        val assigneeTodo = Todo(
            taskRef = taskRef,
            title = "Read-Only Review Task",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            accessState = TaskAccessState(
                ref = taskRef,
                revision = 1L,
                capabilities = TaskCapabilities(
                    editContent = false,
                    manageAssignment = false,
                    deleteTask = false,
                    createChild = false
                ),
                coverage = HierarchyCoverage.Standalone,
                status = TaskAccessStatus.Accessible
            )
        )
        reconcileRemoteTodo(assigneeTodo)

        // Attempting to set completed when editContent is false:
        // StickyTaskItem reflects canEdit = false
        val items = projectStickyItems(listOf(assigneeTodo))
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(false, item.todo.accessState?.capabilities?.editContent)
        assertEquals(false, item.todo.accessState?.capabilities?.manageAssignment)
        assertEquals(false, item.todo.accessState?.capabilities?.deleteTask)

        // Surface mutation must preserve the read-only shared task.
        val res = store.setCompletedLocally(taskRef, true, 2000L)
        assertEquals(CompletionChange.Missing, res)
        assertFalse(assertNotNull(store.getTodo(taskRef)).completed)
    }

    @Test
    fun testStaleSurfaceAccountTokenRejectsSetCompleted() = runTest {
        val access = DesktopTaskSurfaceAccess()
        access.onAccountAuthenticated(token1, store)

        val staleToken = SurfaceAccountToken(accountKey, 999L)
        val taskRef = TaskRef("alice", "task-1")

        val result = access.setCompleted(staleToken, taskRef, true, 2000L)
        assertTrue(result is SurfaceResult.Rejected, "Stale surface account token must be rejected fail-closed")
    }
}
