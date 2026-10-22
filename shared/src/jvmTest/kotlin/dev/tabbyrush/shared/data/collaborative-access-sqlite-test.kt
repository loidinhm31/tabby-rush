package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccessLossReason
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AssignmentIntent
import dev.tabbyrush.shared.contract.AssignmentSlotResult
import dev.tabbyrush.shared.contract.CanonicalRecordSnapshot
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.OwnerBindingState
import dev.tabbyrush.shared.contract.RecordFailure
import dev.tabbyrush.shared.contract.TaskAccessState
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.TaskCapabilities
import dev.tabbyrush.shared.contract.TaskMutationResult
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.Todo
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

class CollaborativeAccessSqliteTest {
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var store: RoomAccountStore
    private val origin = "http://localhost:3100"
    private val alice = "user-alice"
    private val bob = "user-bob"
    private val aliceKey = AccountKey.create(origin, alice)

    @BeforeTest fun setup() {
        directory = Files.createTempDirectory("collaborative-sqlite-test").toFile()
        file = File(directory, "account.db")
        store = RoomAccountStore(aliceKey, alice, createJvmDatabaseBuilder(file).build(), canonicalOrigin = origin)
    }
    private fun identity(u: String) = AccountIdentity(u, "user-$u", "$u@example.com")

    @AfterTest fun cleanup() {
        store.close()
        directory.deleteRecursively()
    }

    @Test fun identicalRowIdsAcrossDifferentOwnersRemainDistinctInSqlite() = runTest {
        store.initializeOwnerBinding(identity(alice))

        val sharedRowId = "shared-row-id-1"
        val aliceRef = TaskRef(alice, sharedRowId)
        val bobRef = TaskRef(bob, sharedRowId)

        // Alice saves her task
        val aliceTodo = Todo(
            taskRef = aliceRef,
            title = "Alice's Task",
            completed = false,
            createdAt = 1000L
        )
        val aliceResult = store.saveTodoLocally(aliceTodo)
        assertTrue(aliceResult is TaskMutationResult.Changed)

        // Stage Bob's task (with same rowId!) via collaborative scan staging
        val bobJson = """{"id":"$sharedRowId","title":"Bob's Task","completed":true,"createdAt":2000}"""
        store.stageDataPage(1L, listOf(
            CanonicalRecordSnapshot(
                ref = bobRef.toRecordRef(),
                dataJson = bobJson,
                version = 5L,
                syncedAt = 2000L
            )
        ))
        store.stageAccessResults(1L, listOf(
            TaskAccessState(
                ref = bobRef,
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.Standalone,
                status = TaskAccessStatus.Accessible
            )
        ))
        store.promoteReconciliation(1L, 2000L)

        // Inspect both records in SQLite
        val aliceLoaded = store.getTodo(aliceRef)
        val bobLoaded = store.getTodo(bobRef)

        assertNotNull(aliceLoaded)
        assertNotNull(bobLoaded)
        assertEquals("Alice's Task", aliceLoaded.title)
        assertEquals("Bob's Task", bobLoaded.title)
        assertFalse(aliceLoaded.completed)
        assertTrue(bobLoaded.completed)
        assertEquals(alice, aliceLoaded.taskRef.ownerUserId)
        assertEquals(bob, bobLoaded.taskRef.ownerUserId)
    }

    @Test fun childOnlyEditProceedsWithoutLocalParentAndRejectsStructuralMutation() = runTest {
        store.initializeOwnerBinding(identity(alice))

        val childRowId = "child-row-1"
        val bobChildRef = TaskRef(bob, childRowId)

        // Stage Bob's child-only task where parent is private/absent on client
        val childJson = """{"id":"$childRowId","title":"Child Content","completed":false,"createdAt":1000,"parentTaskId":"private-parent-99"}"""
        store.stageDataPage(1L, listOf(
            CanonicalRecordSnapshot(
                ref = bobChildRef.toRecordRef(),
                dataJson = childJson,
                version = 1L,
                syncedAt = 1000L
            )
        ))
        store.stageAccessResults(1L, listOf(
            TaskAccessState(
                ref = bobChildRef,
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE, // editContent = true, manageAssignment = false, deleteTask = false, createChild = false
                coverage = HierarchyCoverage.ChildOnly,
                status = TaskAccessStatus.Accessible
            )
        ))
        store.promoteReconciliation(1L, 1000L)

        val loaded = store.getTodo(bobChildRef)
        assertNotNull(loaded)
        assertEquals("Child Content", loaded.title)
        assertNotNull(loaded.parentTaskRef)

        // 1. Content edit succeeds despite parent absent locally
        val edited = loaded.copy(title = "Updated Content")
        val editResult = store.saveTodoLocally(edited)
        assertTrue(editResult is TaskMutationResult.Changed)
        assertEquals("Updated Content", store.getTodo(bobChildRef)?.title)

        // 2. Completion toggle succeeds
        val completionResult = store.setCompletedLocally(bobChildRef, true, 2000L)
        assertEquals(CompletionChange.Changed, completionResult)
        assertTrue(store.getTodo(bobChildRef)!!.completed)

        // 3. Delete is denied for assignee
        val deleteResult = store.deleteTodoLocally(bobChildRef)
        assertEquals(TaskMutationResult.AccessDenied, deleteResult)

        // 4. Assignment is denied for assignee
        val assignResult = store.assignTaskLocally(
            AssignmentIntent(taskRef = bobChildRef, desiredRecipientUserId = alice, expectedSlotVersion = 0L)
        )
        assertEquals(AssignmentSlotResult.AccessDenied, assignResult)
    }

    @Test fun assignmentDesiredStatePersistsWithVersionCheckAndStableSlotKey() = runTest {
        store.initializeOwnerBinding(identity(alice))

        val taskRef = TaskRef(alice, "task-assign-test")
        store.saveTodoLocally(Todo(taskRef = taskRef, title = "To Assign", completed = false, createdAt = 1000L))

        // 1. Version conflict when expectedSlotVersion != actual
        val conflictResult = store.assignTaskLocally(
            AssignmentIntent(taskRef = taskRef, desiredRecipientUserId = bob, expectedSlotVersion = 5L)
        )
        assertTrue(conflictResult is AssignmentSlotResult.VersionConflict)
        assertEquals(5L, conflictResult.expectedVersion)
        assertEquals(0L, conflictResult.actualVersion)

        // 2. Successful assignment to Bob
        val successResult = store.assignTaskLocally(
            AssignmentIntent(taskRef = taskRef, desiredRecipientUserId = bob, expectedSlotVersion = 0L)
        )
        assertTrue(successResult is AssignmentSlotResult.Success)
        assertEquals(bob, successResult.record.recipientUserId)
        assertEquals(1L, successResult.record.version)

        // 3. Re-assigning to same recipient is Unchanged
        val unchangedResult = store.assignTaskLocally(
            AssignmentIntent(taskRef = taskRef, desiredRecipientUserId = bob, expectedSlotVersion = 1L)
        )
        assertEquals(AssignmentSlotResult.Unchanged, unchangedResult)

        // 4. Outbox contains taskAssignees relation record with rowId strictly matching taskId
        val relationOps = store.dao.getOutboxByRow(boundBindingKey(alice), dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME, taskRef.rowId)
        assertEquals(1, relationOps.size)
        val op = relationOps.single()
        assertEquals(taskRef.rowId, op.rowId)
        assertEquals(dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME, op.tableName)
        assertTrue(op.payloadJson.contains(bob))
    }

    @Test fun privacyFirstPurgeErasesRevokedSharedContentAndRetainsContentFreeEvidence() = runTest {
        store.initializeOwnerBinding(identity(alice))

        // Alice has private task
        val alicePrivateRef = TaskRef(alice, "alice-private-task")
        store.saveTodoLocally(Todo(taskRef = alicePrivateRef, title = "Alice Private", completed = false, createdAt = 1000L))

        // Bob's shared task staged and promoted
        val bobSharedRef = TaskRef(bob, "bob-shared-task")
        val bobJson = """{"id":"${bobSharedRef.rowId}","title":"Secret Shared Project","completed":false,"createdAt":1000}"""
        store.stageDataPage(1L, listOf(
            CanonicalRecordSnapshot(bobSharedRef.toRecordRef(), bobJson, 1L, 1000L)
        ))
        store.stageAccessResults(1L, listOf(
            TaskAccessState(
                ref = bobSharedRef,
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.Standalone,
                status = TaskAccessStatus.Accessible
            )
        ))
        store.promoteReconciliation(1L, 1000L)

        // Alice queued an offline edit for Bob's task
        val loaded = store.getTodo(bobSharedRef)!!
        store.saveTodoLocally(loaded.copy(title = "Alice's Secret Draft"))

        // Authoritative access revocation occurs
        val initialGen = store.accessGeneration
        val newGen = store.invalidateAccess(bobSharedRef, AccessLossReason.REVOKED, 3000L)
        assertTrue(newGen > initialGen)

        // 1. Bob's task is completely absent from loaded todos
        val visibleTodos = store.loadTodos()
        assertNull(visibleTodos.firstOrNull { it.taskRef == bobSharedRef })

        // 2. Alice's private task remains completely intact
        val aliceTask = store.getTodo(alicePrivateRef)
        assertNotNull(aliceTask)
        assertEquals("Alice Private", aliceTask.title)

        // 3. Inspect SQLite directly: NO secret content remains in server_base, editor_drafts, outbox, or scan_staging
        val boundBobKey = boundBindingKey(bob)
        assertNull(store.dao.getServerBase(boundBobKey, "todos", bobSharedRef.rowId))
        assertNull(store.dao.getDraft(boundBobKey, "todos", bobSharedRef.rowId))
        assertTrue(store.dao.getOutboxByRow(boundBobKey, "todos", bobSharedRef.rowId).isEmpty())

        // 4. Content-free evidence exists with NO payload leakage
        val evidenceList = store.listAccessLossEvidence()
        assertEquals(1, evidenceList.size)
        val evidence = evidenceList.single()
        assertEquals(bobSharedRef, evidence.ref)
        assertEquals(AccessLossReason.REVOKED, evidence.reason)
        assertEquals(3000L, evidence.timestamp)

        // 5. Dismissal clears the evidence
        store.dismissAccessLossEvidence(bobSharedRef)
        assertTrue(store.listAccessLossEvidence().isEmpty())

        // 6. Repeated invalidation on already-purged task is an idempotent no-op (no evidence or generation growth)
        val genBeforeRepeated = store.accessGeneration
        val genAfterRepeated = store.invalidateAccess(bobSharedRef, AccessLossReason.UNAVAILABLE, 4000L)
        assertEquals(genBeforeRepeated, genAfterRepeated)
        assertTrue(store.listAccessLossEvidence().isEmpty(), "No duplicate evidence should be created for already-purged task")

        // 7. Regrant: task shared again, promoted to Accessible, then revoked -> fresh evidence created
        store.stageDataPage(2L, listOf(
            CanonicalRecordSnapshot(bobSharedRef.toRecordRef(), bobJson, 2L, 5000L)
        ))
        store.stageAccessResults(2L, listOf(
            TaskAccessState(
                ref = bobSharedRef,
                revision = 2L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.Standalone,
                status = TaskAccessStatus.Accessible
            )
        ))
        store.promoteReconciliation(2L, 5000L)
        assertNotNull(store.getTodo(bobSharedRef))

        // Revoke again after regrant -> fresh notice created
        val regrantRevokeGen = store.invalidateAccess(bobSharedRef, AccessLossReason.REVOKED, 6000L)
        assertTrue(regrantRevokeGen > genAfterRepeated)
        val freshEvidence = store.listAccessLossEvidence()
        assertEquals(1, freshEvidence.size)
        assertEquals(6000L, freshEvidence.single().timestamp)
    }

    @Test fun operationForbiddenDiscardsOnlyTheForbiddenIntentAndPreservesValidAccess() = runTest {
        store.initializeOwnerBinding(identity(alice))

        val bobRef = TaskRef(bob, "bob-task-op-forbidden")
        store.stageDataPage(1L, listOf(
            CanonicalRecordSnapshot(bobRef.toRecordRef(), """{"id":"${bobRef.rowId}","title":"Shared Task"}""", 1L, 1000L)
        ))
        store.stageAccessResults(1L, listOf(
            TaskAccessState(
                ref = bobRef,
                revision = 1L,
                capabilities = TaskCapabilities.ASSIGNEE,
                coverage = HierarchyCoverage.Standalone,
                status = TaskAccessStatus.Accessible
            )
        ))
        store.promoteReconciliation(1L, 1000L)

        // Alice attempts edit
        val todo = store.getTodo(bobRef)!!
        store.saveTodoLocally(todo.copy(title = "Attempted edit"))
        val frozen = store.prepareFrozenOutbox()
        assertEquals(1, frozen.size)

        // Server returns OPERATION_FORBIDDEN for this intent
        store.recordFailures(listOf(
            RecordFailure(ref = bobRef.toRecordRef(), error = "OPERATION_FORBIDDEN: field not editable")
        ))

        // Outbox entry is discarded
        val op = store.dao.getOutboxEntry(frozen.single().opId)!!
        assertEquals("DISCARDED", op.deliveryState)
        assertEquals("OPERATION_FORBIDDEN", op.resolution)

        // Valid task access is PRESERVED (not purged)
        val access = store.accessFor(bobRef)
        assertNotNull(access)
        assertEquals(TaskAccessStatus.Accessible, access.status)
        assertNotNull(store.getTodo(bobRef))
    }

    @Test fun taskAndAssignmentSharingSameRowIdDoNotCollideInLoadTodos() = runTest {
        store.initializeOwnerBinding(identity(alice))
        val rowId = "shared-row-1"
        val ref = TaskRef(alice, rowId)

        // Stage a todo and an assignment slot with the exact same rowId
        val todoSnapshot = CanonicalRecordSnapshot(
            ref = ref.toRecordRef(),
            dataJson = """{"id":"$rowId","title":"Clean kitchen","completed":false,"createdAt":1700000000000}""",
            version = 1L,
            syncedAt = 1700000000000L,
            deleted = false
        )
        val assignSnapshot = CanonicalRecordSnapshot(
            ref = dev.tabbyrush.shared.contract.CanonicalRecordRef(alice, dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME, rowId),
            dataJson = """{"id":"$rowId","taskId":"$rowId","userId":"$bob"}""",
            version = 1L,
            syncedAt = 1700000000000L,
            deleted = false
        )
        store.stageDataPage(1L, listOf(todoSnapshot, assignSnapshot))
        val promoted = store.promoteReconciliation(1L, 1700000000000L)
        assertTrue(promoted)

        val todos = store.loadTodos()
        assertEquals(1, todos.size)
        val todo = todos[0]
        assertEquals(ref, todo.taskRef)
        assertEquals("Clean kitchen", todo.title)
        assertFalse(todo.completed)

        // Verify assignment slot is queryable without corrupting the task
        val slot = store.getAssignmentSlot(ref)
        assertEquals(bob, slot.recipientUserId)
        assertEquals(1L, slot.slotVersion)
    }
}
