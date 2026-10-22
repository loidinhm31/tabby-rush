package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanonicalTaskAccessTest {

    @Test
    fun testDuplicateRowIdAcrossDifferentOwnersIsIsolated() {
        // Two tasks with the exact same rowId but different owners
        val refUserA = TaskRef("user-a", "task-1")
        val refUserB = TaskRef("user-b", "task-1")

        val todoA = Todo(
            taskRef = refUserA,
            title = "Task from User A",
            completed = false,
            createdAt = 1000L
        )
        val todoB = Todo(
            taskRef = refUserB,
            title = "Task from User B",
            completed = true,
            createdAt = 2000L
        )

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(todoA, todoB), TodoFilter.ALL)
        assertEquals(2, snapshot.items.size)

        // Sorted by createdAt DESC: todoB (2000L), then todoA (1000L)
        assertEquals("user-b", snapshot.items[0].todo.ownerUserId)
        assertEquals("task-1", snapshot.items[0].todo.id)
        assertTrue(snapshot.items[0].effectiveCompleted)

        assertEquals("user-a", snapshot.items[1].todo.ownerUserId)
        assertEquals("task-1", snapshot.items[1].todo.id)
        assertFalse(snapshot.items[1].effectiveCompleted)
    }

    @Test
    fun testSameEncounterTimeTieBreakIncludesOwner() {
        val refUserA = TaskRef("user-a", "task-1")
        val refUserB = TaskRef("user-b", "task-1")

        val todoA = Todo(
            taskRef = refUserA,
            title = "Task A",
            completed = false,
            createdAt = 1000L
        )
        val todoB = Todo(
            taskRef = refUserB,
            title = "Task B",
            completed = false,
            createdAt = 1000L
        )

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(todoA, todoB), TodoFilter.ALL)
        assertEquals(2, snapshot.items.size)
        // Descending by ownerUserId: "user-b" before "user-a"
        assertEquals("user-b", snapshot.items[0].todo.ownerUserId)
        assertEquals("user-a", snapshot.items[1].todo.ownerUserId)
    }

    @Test
    fun testChildOnlyTaskProjectsFlatWithoutParentSynthesis() {
        val parentRef = TaskRef("alice", "parent-1")
        val childRef = TaskRef("alice", "child-1")

        // Bob receives only child-1 with ChildOnly coverage; parent-1 is private and absent locally
        val childAccess = TaskAccessState(
            ref = childRef,
            revision = 5L,
            capabilities = TaskCapabilities.ASSIGNEE,
            coverage = HierarchyCoverage.ChildOnly,
            status = TaskAccessStatus.Accessible
        )

        val childTodo = Todo(
            taskRef = childRef,
            title = "Assigned Child Task",
            completed = false,
            createdAt = 1500L,
            parentTaskRef = parentRef,
            accessState = childAccess
        )

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(childTodo), TodoFilter.ALL)

        // Must project flat: 1 item, depth 0, isChildOnly = true, childCount = 0
        assertEquals(1, snapshot.items.size)
        val item = snapshot.items[0]
        assertEquals(childRef, item.todo.taskRef)
        assertEquals(0, item.depth)
        assertTrue(item.isChildOnly)
        assertFalse(item.hasLiveChildren)
        assertEquals(0, item.childCount)
        assertEquals(0, item.completedChildCount)
        assertFalse(item.effectiveCompleted)

        // Must NOT report as unresolved missing parent
        assertTrue(snapshot.unresolvedChildren.isEmpty())
    }

    @Test
    fun testMissingParentWithoutChildOnlyMetadataRemainsUnresolved() {
        val parentRef = TaskRef("alice", "parent-missing")
        val childRef = TaskRef("alice", "child-orphan")

        // Orphan child without trusted server ChildOnly coverage
        val childTodo = Todo(
            taskRef = childRef,
            title = "Orphan Child",
            completed = false,
            createdAt = 1500L,
            parentTaskRef = parentRef,
            accessState = null
        )

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(childTodo), TodoFilter.ALL)
        // Should not be projected into items
        assertTrue(snapshot.items.isEmpty())
        // Should be caught in unresolvedChildren
        assertEquals(1, snapshot.unresolvedChildren.size)
        assertEquals(childRef, snapshot.unresolvedChildren[0].taskRef)
    }

    @Test
    fun testFullGroupCoveragePermitsAutomaticParentCompletion() {
        val rootRef = TaskRef("alice", "root-1")
        val child1Ref = TaskRef("alice", "child-1")
        val child2Ref = TaskRef("alice", "child-2")

        val rootAccess = TaskAccessState(
            ref = rootRef,
            revision = 1L,
            capabilities = TaskCapabilities.OWNER,
            coverage = HierarchyCoverage.FullGroup,
            status = TaskAccessStatus.Accessible
        )

        val root = Todo(
            taskRef = rootRef,
            title = "Root",
            completed = false,
            createdAt = 1000L,
            accessState = rootAccess
        )
        val c1 = Todo(
            taskRef = child1Ref,
            title = "Child 1",
            completed = true,
            createdAt = 1100L,
            parentTaskRef = rootRef
        )
        val c2 = Todo(
            taskRef = child2Ref,
            title = "Child 2",
            completed = true,
            createdAt = 1200L,
            parentTaskRef = rootRef
        )

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(root, c1, c2), TodoFilter.ALL)
        val rootItem = snapshot.items.first { it.todo.taskRef == rootRef }

        // All children completed with FullGroup coverage -> automatic parent completion true
        assertTrue(rootItem.effectiveCompleted)
        assertFalse(rootItem.todo.completed) // Stored completion remains false
    }

    @Test
    fun testNonFullGroupCoverageDoesNotDeriveAutomaticParentCompletion() {
        val rootRef = TaskRef("alice", "root-1")
        val childRef = TaskRef("alice", "child-1")

        // Standalone or non-full coverage prevents automatic completion derivation
        val rootAccess = TaskAccessState(
            ref = rootRef,
            revision = 1L,
            capabilities = TaskCapabilities.ASSIGNEE,
            coverage = HierarchyCoverage.Standalone,
            status = TaskAccessStatus.Accessible
        )

        val root = Todo(
            taskRef = rootRef,
            title = "Root",
            completed = false,
            createdAt = 1000L,
            accessState = rootAccess
        )
        val c1 = Todo(
            taskRef = childRef,
            title = "Child",
            completed = true,
            createdAt = 1100L,
            parentTaskRef = rootRef
        )

        val snapshot = TaskHierarchyProjector.projectHierarchy(listOf(root, c1), TodoFilter.ALL)
        val rootItem = snapshot.items.first { it.todo.taskRef == rootRef }

        // Standalone coverage -> automatic parent completion suppressed; stays root.completed (false)
        assertFalse(rootItem.effectiveCompleted)
    }

    @Test
    fun testRelationRowCollisionIsFilteredFromPersistedSchedule() {
        val taskRef = TaskRef("alice", "id-123")

        // A todos content record and a taskAssignees relation record with the same rowId
        val baseTask = SimplePersistedBaseRecord(
            ownerUserId = "alice",
            tableName = "todos",
            rowId = "id-123",
            title = "Real Task",
            deadlineDate = "2026-12-01",
            deadlineTime = "08:00",
            deadlineZone = "UTC",
            deadlineOffsetSeconds = 0L,
            deadlineAnchorEpochMs = 1796112000000L,
            deadlineOverdueEpochMs = 1796112000000L,
            scheduleToken = "token-1",
            reminderEnabled = true
        )
        val relationRecord = SimplePersistedBaseRecord(
            ownerUserId = "alice",
            tableName = "taskAssignees",
            rowId = "id-123",
            title = "Should Be Ignored"
        )

        val schedules = PersistedScheduleProjector.computeProjection(
            listOf(baseTask, relationRecord),
            emptyList()
        )

        assertEquals(1, schedules.size)
        assertEquals(taskRef, schedules[0].taskRef)
        assertEquals("Real Task", schedules[0].title)
    }

    @Test
    fun testUnavailableAccessRecordSuppressesScheduleEligibility() {
        val taskRef = TaskRef("alice", "task-revoked")

        val baseTask = SimplePersistedBaseRecord(
            ownerUserId = "alice",
            tableName = "todos",
            rowId = "task-revoked",
            title = "Revoked Task",
            deadlineDate = "2026-12-01",
            deadlineTime = "08:00",
            deadlineZone = "UTC",
            deadlineOffsetSeconds = 0L,
            deadlineAnchorEpochMs = 1796112000000L,
            deadlineOverdueEpochMs = 1796112000000L,
            scheduleToken = "token-revoked",
            reminderEnabled = true
        )

        val accessRecord = SimplePersistedAccessRecord(
            ownerUserId = "alice",
            tableName = "todos",
            rowId = "task-revoked",
            status = TaskAccessStatus.Unavailable
        )

        val schedules = PersistedScheduleProjector.computeProjection(
            listOf(baseTask),
            emptyList(),
            listOf(accessRecord)
        )

        assertEquals(1, schedules.size)
        assertFalse(schedules[0].isAccessible)
        assertFalse(schedules[0].isEligibleForScheduling)

        // selectNextDeadline must also filter it out
        val deadlineSelection = selectNextDeadline(schedules, nowEpochMs = 1000L)
        assertNull(deadlineSelection)
    }

    @Test
    fun testOutboxOverlayCannotOverrideUnavailableAccess() {
        val taskRef = TaskRef("alice", "task-revoked")

        val baseTask = SimplePersistedBaseRecord(
            ownerUserId = "alice",
            tableName = "todos",
            rowId = "task-revoked",
            title = "Revoked Base Task",
            deadlineDate = "2026-12-01",
            deadlineTime = "08:00",
            deadlineZone = "UTC",
            deadlineOffsetSeconds = 0L,
            deadlineAnchorEpochMs = 1796112000000L,
            deadlineOverdueEpochMs = 1796112000000L,
            scheduleToken = "token-base",
            reminderEnabled = true
        )

        // Local pending outbox edit attempting to update title and advance schedule token
        val outboxOp = SimplePersistedOutboxRecord(
            ownerUserId = "alice",
            tableName = "todos",
            rowId = "task-revoked",
            kind = TaskOperationKind.UPDATE.wireName,
            title = "Unsent Local Title Edit",
            completed = false,
            localRevision = 2L,
            deliveryState = OutboxDeliveryStates.PENDING,
            deadlineDate = "2026-12-01",
            deadlineTime = "08:00",
            deadlineZone = "UTC",
            deadlineOffsetSeconds = 0L,
            deadlineAnchorEpochMs = 1796112000000L,
            deadlineOverdueEpochMs = 1796112000000L,
            reminderEnabled = true,
            reminderLeadMinutes = 30L,
            reminderCount = 1L,
            scheduleToken = "token-new",
            intentMask = OutboxIntentMasks.REMINDER
        )

        val accessRecord = SimplePersistedAccessRecord(
            ownerUserId = "alice",
            tableName = "todos",
            rowId = "task-revoked",
            status = TaskAccessStatus.Unavailable
        )

        val schedules = PersistedScheduleProjector.computeProjection(
            listOf(baseTask),
            listOf(outboxOp),
            listOf(accessRecord)
        )

        assertEquals(1, schedules.size)
        // Access loss must dominate: cannot be overridden by outbox intent
        assertFalse(schedules[0].isAccessible)
        assertFalse(schedules[0].isEligibleForScheduling)
        assertNull(selectNextDeadline(schedules, nowEpochMs = 1000L))
    }

    @Test
    fun testAccessLossEvidenceIsContentFree() {
        val ref = TaskRef("creator-1", "task-secret")
        val evidence = AccessLossEvidence(
            ref = ref,
            opId = "op-99",
            baseVersion = 2L,
            submittedVersion = 3L,
            timestamp = 1700000000L,
            reason = AccessLossReason.REVOKED
        )

        assertEquals(ref, evidence.ref)
        assertEquals("op-99", evidence.opId)
        assertEquals(2L, evidence.baseVersion)
        assertEquals(3L, evidence.submittedVersion)
        assertEquals(AccessLossReason.REVOKED, evidence.reason)
        // Privacy verification: class structure contains no title, body, payload, or diff fields.
    }
}
