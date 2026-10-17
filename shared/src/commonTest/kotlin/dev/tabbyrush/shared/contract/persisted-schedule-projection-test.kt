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
    ) = ServerBaseEntity(
        rowId = "task-1",
        serverRevision = 1L,
        deleted = deleted,
        title = title,
        completed = completed,
        createdAt = 1000L,
        updatedAt = 1000L,
        epoch = "ep-1",
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
        val outbox = OutboxEntity(
            opId = "op-1",
            epoch = "ep-1",
            rowId = "task-1",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Active task",
            completed = true,
            createdAt = 1000L,
            digest = "d-1",
            localRevision = 2L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 2000L,
            intentMask = OutboxEntity.INTENT_COMPLETED
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
        val outbox = OutboxEntity(
            opId = "op-1",
            epoch = "ep-1",
            rowId = "task-1",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Active task",
            completed = false,
            createdAt = 1000L,
            digest = "d-1",
            localRevision = 2L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 2000L,
            deadlineDate = null,
            scheduleToken = null,
            reminderEnabled = false,
            intentMask = OutboxEntity.INTENT_DEADLINE
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
        val conflictOp = OutboxEntity(
            opId = "op-1",
            epoch = "ep-1",
            rowId = "task-1",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Conflicted title",
            completed = false,
            createdAt = 1000L,
            digest = "d-1",
            localRevision = 2L,
            deliveryState = OutboxEntity.STATE_CONFLICT,
            createdAtEpochMs = 2000L
        )
        val projections = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(createBase()),
            outboxEntries = listOf(conflictOp)
        )
        assertTrue(projections[0].hasUnresolvedConflict)
        assertFalse(projections[0].isEligibleForScheduling)
    }
}
