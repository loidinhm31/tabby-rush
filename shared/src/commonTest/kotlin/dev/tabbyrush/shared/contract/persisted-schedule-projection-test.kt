package dev.tabbyrush.shared.contract

import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.ServerBaseEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistedScheduleProjectionTest {

    private fun createBase(
        title: String = "Test task",
        completed: Boolean = false,
        deleted: Boolean = false
    ) = ServerBaseEntity(rowId = "task-1",
    serverRevision = 1L,
    deleted = deleted,
    title = title,
    completed = completed,
    createdAt = 1000L,
    updatedAt = 1000L, deadlineDate = "2026-10-20",
    deadlineTime = "10:00",
    deadlineZone = "UTC",
    deadlineOffsetSeconds = 0L,
    deadlineAnchorEpochMs = 1_000_000L,
    deadlineOverdueEpochMs = 1_000_000L,
    reminderEnabled = true,
    reminderLeadMinutes = 15L,
    reminderCount = 1L,
    scheduleToken = "token-1")

    @Test
    fun testBaseProjectionActiveTaskWithReminder() {
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase()),
            outboxEntries = emptyList()
        )
        assertEquals(1, projections.size)
        val p = projections[0]
        assertEquals("task-1", p.taskId)
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
        val outbox = OutboxEntity(opId = "op-1", rowId = "task-1",
        kind = TaskOperationKind.UPDATE.wireName, title = "Active task",
        completed = true,
        createdAt = 1000L, localRevision = 2L,
        deliveryState = OutboxEntity.STATE_PENDING,
        createdAtEpochMs = 2000L,
        intentMask = OutboxEntity.INTENT_COMPLETED)
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase()),
            outboxEntries = listOf(outbox)
        )
        assertTrue(projections[0].completed)
        assertFalse(projections[0].isEligibleForScheduling)
    }

    @Test
    fun testOutboxDeadlineRemovalClearsDeadlineAndToken() {
        val outbox = OutboxEntity(opId = "op-1", rowId = "task-1",
        kind = TaskOperationKind.UPDATE.wireName, title = "Active task",
        completed = false,
        createdAt = 1000L, localRevision = 2L,
        deliveryState = OutboxEntity.STATE_PENDING,
        createdAtEpochMs = 2000L,
        deadlineDate = null,
        scheduleToken = null,
        reminderEnabled = false,
        intentMask = OutboxEntity.INTENT_DEADLINE)
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
        val conflictOp = OutboxEntity(opId = "op-1", rowId = "task-1",
        kind = TaskOperationKind.UPDATE.wireName, title = "Conflicted title",
        completed = false,
        createdAt = 1000L, localRevision = 2L,
        deliveryState = OutboxEntity.STATE_CONFLICT,
        createdAtEpochMs = 2000L)
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase()),
            outboxEntries = listOf(conflictOp)
        )
        assertTrue(projections[0].hasUnresolvedConflict)
        assertFalse(projections[0].isEligibleForScheduling)
    }
    @Test
    fun testParentWithAllCompletedChildrenCancelsParentReminder() {
        val parent = createBase(title = "Parent with deadline").copy(rowId = "p-1", completed = false)
        val c1 = ServerBaseEntity(rowId = "c-1",
        serverRevision = 1L,
        deleted = false,
        title = "Child 1",
        completed = true,
        createdAt = 1000L,
        updatedAt = 1000L, parentTaskId = "p-1")
        val c2 = ServerBaseEntity(rowId = "c-2",
        serverRevision = 1L,
        deleted = false,
        title = "Child 2",
        completed = true,
        createdAt = 1000L,
        updatedAt = 1000L, parentTaskId = "p-1")

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
        val parent = createBase(title = "Parent with deadline").copy(rowId = "p-1", completed = true)
        val c1 = ServerBaseEntity(rowId = "c-1",
        serverRevision = 1L,
        deleted = false,
        title = "Child 1",
        completed = true,
        createdAt = 1000L,
        updatedAt = 1000L, parentTaskId = "p-1")
        val c2 = ServerBaseEntity(rowId = "c-2",
        serverRevision = 1L,
        deleted = false,
        title = "Child 2",
        completed = false,
        createdAt = 1000L,
        updatedAt = 1000L, parentTaskId = "p-1")

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
        val parent = createBase(title = "Deleted Parent", deleted = true).copy(rowId = "p-1")
        val child = ServerBaseEntity(rowId = "c-1",
        serverRevision = 1L,
        deleted = false,
        title = "Child with deadline",
        completed = false,
        createdAt = 1000L,
        updatedAt = 1000L, deadlineDate = "2026-10-25",
        deadlineZone = "UTC",
        deadlineOffsetSeconds = 0L,
        reminderAnchorTime = "09:00",
        deadlineAnchorEpochMs = 1_000_000L,
        deadlineOverdueEpochMs = 1_000_000L,
        reminderEnabled = true,
        reminderLeadMinutes = 15L,
        reminderCount = 1L,
        scheduleToken = "c-token-1",
        parentTaskId = "p-1")

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
        val parent = createBase(title = "Parent").copy(rowId = "p-1", completed = false)
        val preservationOp = OutboxEntity(opId = "op-preservation", rowId = "p-1",
        kind = TaskOperationKind.UPDATE.wireName, title = "Parent",
        completed = false,
        createdAt = 1000L, localRevision = 2L,
        deliveryState = OutboxEntity.STATE_PENDING,
        createdAtEpochMs = 2000L,
        intentMask = "${OutboxEntity.INTENT_COMPLETED}|${OutboxEntity.INTENT_LAST_CHILD_PRESERVATION}",
        deadlineDate = parent.deadlineDate,
        deadlineZone = parent.deadlineZone,
        deadlineOffsetSeconds = parent.deadlineOffsetSeconds,
        deadlineAnchorEpochMs = parent.deadlineAnchorEpochMs,
        deadlineOverdueEpochMs = parent.deadlineOverdueEpochMs,
        reminderEnabled = true,
        reminderLeadMinutes = 15L,
        reminderCount = 1L,
        scheduleToken = "token-1")

        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(parent),
            outboxEntries = listOf(preservationOp)
        )

        val projectedParent = projections.first { it.taskId == "p-1" }
        assertTrue(projectedParent.hasUnresolvedPreservationBarrier, "Unresolved preservation intent must set barrier")
        assertFalse(projectedParent.isEligibleForScheduling, "Preservation barrier must suppress scheduling")
    }
}
