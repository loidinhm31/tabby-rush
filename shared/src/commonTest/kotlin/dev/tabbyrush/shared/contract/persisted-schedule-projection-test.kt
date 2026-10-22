package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistedScheduleProjectionTest {

    private val owner = "user-1"
    private fun ref(id: String) = TaskRef(owner, id)

    private fun createBase(
        id: String = "task-1",
        title: String = "Test task",
        completed: Boolean = false,
        deleted: Boolean = false,
        parentTaskId: String? = null
    ) = SimplePersistedBaseRecord(
        ownerUserId = owner,
        tableName = "todos",
        rowId = id,
        title = title,
        completed = completed,
        deleted = deleted,
        parentTaskId = parentTaskId,
        deadlineDate = "2026-10-20",
        deadlineTime = "10:00",
        deadlineZone = "UTC",
        deadlineOffsetSeconds = 0L,
        deadlineAnchorEpochMs = 1_000_000L,
        deadlineOverdueEpochMs = 1_000_000L,
        reminderEnabled = true,
        reminderLeadMinutes = 15L,
        reminderCount = 1L,
        scheduleToken = "token-1"
    )

    @Test
    fun testBaseProjectionActiveTaskWithReminder() {
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase()),
            outboxEntries = emptyList()
        )
        assertEquals(1, projections.size)
        val p = projections[0]
        assertEquals("task-1", p.taskId)
        assertEquals(ref("task-1"), p.taskRef)
        assertFalse(p.completed)
        assertFalse(p.isDeleted)
        assertFalse(p.hasUnresolvedConflict)
        assertNotNull(p.deadline)
        assertTrue(p.reminderRule.enabled)
        assertEquals("token-1", p.scheduleToken)
        assertTrue(p.isEligibleForScheduling)
    }

    @Test
    fun testCompletedBaseTaskIsNotEligibleForScheduling() {
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase(completed = true)),
            outboxEntries = emptyList()
        )
        assertEquals(1, projections.size)
        assertTrue(projections[0].completed)
        assertFalse(projections[0].isEligibleForScheduling)
    }

    @Test
    fun testOutboxCompletedActsAsCancellationBarrier() {
        val outbox = SimplePersistedOutboxRecord(
            ownerUserId = owner,
            tableName = "todos",
            rowId = "task-1",
            kind = TaskOperationKind.UPDATE.wireName,
            title = "Active task",
            completed = true,
            localRevision = 2L,
            deliveryState = OutboxDeliveryStates.PENDING,
            intentMask = OutboxIntentMasks.COMPLETED
        )
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase()),
            outboxEntries = listOf(outbox)
        )
        assertTrue(projections[0].completed)
        assertFalse(projections[0].isEligibleForScheduling)
    }

    @Test
    fun testOutboxDeadlineRemovalClearsDeadlineAndToken() {
        val outbox = SimplePersistedOutboxRecord(
            ownerUserId = owner,
            tableName = "todos",
            rowId = "task-1",
            kind = TaskOperationKind.UPDATE.wireName,
            title = "Active task",
            completed = false,
            localRevision = 2L,
            deliveryState = OutboxDeliveryStates.PENDING,
            deadlineDate = null,
            scheduleToken = null,
            reminderEnabled = false,
            intentMask = OutboxIntentMasks.DEADLINE
        )
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase()),
            outboxEntries = listOf(outbox)
        )
        assertNull(projections[0].deadline)
        assertNull(projections[0].scheduleToken)
        assertFalse(projections[0].reminderRule.enabled)
        assertFalse(projections[0].isEligibleForScheduling)
    }

    @Test
    fun testConflictHeadSuppressesSchedule() {
        val conflictOp = SimplePersistedOutboxRecord(
            ownerUserId = owner,
            tableName = "todos",
            rowId = "task-1",
            kind = TaskOperationKind.UPDATE.wireName,
            title = "Conflicted title",
            completed = false,
            localRevision = 2L,
            deliveryState = OutboxDeliveryStates.CONFLICT
        )
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase()),
            outboxEntries = listOf(conflictOp)
        )
        assertTrue(projections[0].hasUnresolvedConflict)
        assertFalse(projections[0].isEligibleForScheduling)
    }

    @Test
    fun testParentWithAllCompletedChildrenCancelsParentReminder() {
        val parent = createBase(id = "p-1", title = "Parent with deadline", completed = false)
        val c1 = createBase(id = "c-1", title = "Child 1", completed = true, parentTaskId = "p-1")
        val c2 = createBase(id = "c-2", title = "Child 2", completed = true, parentTaskId = "p-1")

        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(parent, c1, c2),
            outboxEntries = emptyList()
        )

        val projectedParent = projections.first { it.taskId == "p-1" }
        assertTrue(projectedParent.completed, "Parent must be effectively completed when all children complete")
        assertFalse(projectedParent.isEligibleForScheduling, "Completed parent must not be eligible for reminders")
    }

    @Test
    fun testParentWithAnyActiveChildKeepsParentReminderEligible() {
        val parent = createBase(id = "p-1", title = "Parent with deadline", completed = true)
        val c1 = createBase(id = "c-1", title = "Child 1", completed = true, parentTaskId = "p-1")
        val c2 = createBase(id = "c-2", title = "Child 2", completed = false, parentTaskId = "p-1")

        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(parent, c1, c2),
            outboxEntries = emptyList()
        )

        val projectedParent = projections.first { it.taskId == "p-1" }
        assertFalse(projectedParent.completed, "Parent must be effectively active when any child is incomplete")
        assertTrue(projectedParent.isEligibleForScheduling, "Active parent with deadline and reminders is eligible")
    }

    @Test
    fun testDeletedParentSuppressesChildSchedule() {
        val parent = createBase(id = "p-1", title = "Deleted Parent", deleted = true)
        val child = createBase(id = "c-1", title = "Child with deadline", completed = false, parentTaskId = "p-1")

        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(parent, child),
            outboxEntries = emptyList()
        )

        val projectedChild = projections.first { it.taskId == "c-1" }
        assertTrue(projectedChild.isDeleted, "Child schedule must be suppressed when parent is deleted")
        assertFalse(projectedChild.isEligibleForScheduling)
    }

    @Test
    fun testUnresolvedPreservationIntentSuppressesParentScheduling() {
        val parent = createBase(id = "p-1", title = "Parent", completed = false)
        val preservationOp = SimplePersistedOutboxRecord(
            ownerUserId = owner,
            tableName = "todos",
            rowId = "p-1",
            kind = TaskOperationKind.UPDATE.wireName,
            title = "Parent",
            completed = false,
            localRevision = 2L,
            deliveryState = OutboxDeliveryStates.PENDING,
            intentMask = "${OutboxIntentMasks.COMPLETED}|${OutboxIntentMasks.LAST_CHILD_PRESERVATION}",
            deadlineDate = parent.deadlineDate,
            deadlineZone = parent.deadlineZone,
            deadlineOffsetSeconds = parent.deadlineOffsetSeconds,
            deadlineAnchorEpochMs = parent.deadlineAnchorEpochMs,
            deadlineOverdueEpochMs = parent.deadlineOverdueEpochMs,
            reminderEnabled = true,
            reminderLeadMinutes = 15L,
            reminderCount = 1L,
            scheduleToken = "token-1"
        )

        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(parent),
            outboxEntries = listOf(preservationOp)
        )

        val projectedParent = projections.first { it.taskId == "p-1" }
        assertTrue(projectedParent.hasUnresolvedPreservationBarrier, "Unresolved preservation intent must set barrier")
        assertFalse(projectedParent.isEligibleForScheduling, "Preservation barrier must suppress scheduling")
    }
}
