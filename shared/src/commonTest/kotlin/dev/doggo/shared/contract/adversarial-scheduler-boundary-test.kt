package dev.doggo.shared.contract

import dev.doggo.shared.data.EditorDraftEntity
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.ServerBaseEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdversarialSchedulerBoundaryTest {

    private val baseDeadline = TaskDeadline(
        date = "2026-11-01",
        time = "15:00",
        zone = "America/New_York",
        offsetSeconds = -18000L,
        anchorEpochMs = 1793563200000L,
        overdueEpochMs = 1793563200000L
    )

    @Test
    fun testRepetitionBoundaryEnforcement() {
        // count = 1, interval = null: valid
        val validSingle = ReminderRule(enabled = true, leadMinutes = 15L, intervalMinutes = null, count = 1L)
        assertEquals(1L, validSingle.count)

        // count = 10, interval = 15, lead = 150: valid boundary
        val validMaxRepeat = ReminderRule(enabled = true, leadMinutes = 150L, intervalMinutes = 15L, count = 10L)
        val occ10 = ReminderScheduleCalculator.calculateOccurrences(baseDeadline, validMaxRepeat)
        assertEquals(10, occ10.size)

        // count = 0: rejected
        assertFailsWith<IllegalArgumentException> {
            ReminderRule(enabled = true, leadMinutes = 15L, intervalMinutes = 15L, count = 0L)
        }

        // count = 11: rejected
        assertFailsWith<IllegalArgumentException> {
            ReminderRule(enabled = true, leadMinutes = 165L, intervalMinutes = 15L, count = 11L)
        }

        // repeat with interval = 14: rejected (min interval is 15 minutes)
        assertFailsWith<IllegalArgumentException> {
            ReminderRule(enabled = true, leadMinutes = 30L, intervalMinutes = 14L, count = 2L)
        }

        // repeat with interval = 15: valid
        val validMinInterval = ReminderRule(enabled = true, leadMinutes = 30L, intervalMinutes = 15L, count = 2L)
        assertEquals(15L, validMinInterval.intervalMinutes)

        // negative lead: rejected
        assertFailsWith<IllegalArgumentException> {
            ReminderRule(enabled = true, leadMinutes = -1L, intervalMinutes = null, count = 1L)
        }
    }

    @Test
    fun testArithmeticOverflowRejection() {
        // Lead minutes causes safeMultiply overflow
        val overflowLeadRule = ReminderRule(
            enabled = true,
            leadMinutes = Long.MAX_VALUE / 10L,
            intervalMinutes = null,
            count = 1L
        )
        assertFailsWith<ArithmeticException> {
            ReminderScheduleCalculator.calculateOccurrences(baseDeadline, overflowLeadRule)
        }

        // Count * interval causes safeMultiply overflow
        val overflowIntervalRule = ReminderRule(
            enabled = true,
            leadMinutes = Long.MAX_VALUE / 1000L,
            intervalMinutes = Long.MAX_VALUE / 20L,
            count = 10L
        )
        assertFailsWith<ArithmeticException> {
            ReminderScheduleCalculator.calculateOccurrences(baseDeadline, overflowIntervalRule)
        }

        // Last occurrence > anchor throws IllegalArgumentException
        val exceedAnchorRule = ReminderRule(
            enabled = true,
            leadMinutes = 20L,
            intervalMinutes = 15L,
            count = 3L // first = -20m, second = -5m, third = +10m > anchor!
        )
        assertFailsWith<IllegalArgumentException> {
            ReminderScheduleCalculator.calculateOccurrences(baseDeadline, exceedAnchorRule)
        }
    }

    @Test
    fun testDateOnlyMorningIsNotOverdue() {
        // Compute date-only deadline via production helper
        val editor = EditorState(
            id = "task-adv-1",
            rawTitle = "Date-only task",
            hasDeadline = true,
            hasTime = false,
            deadlineDate = "2026-11-01",
            deadlineZone = "America/New_York",
            reminderAnchorTime = "09:00"
        )
        val validation = dev.doggo.shared.ui.DeadlineCalculatorHelpers.validateAndComputeDeadline(editor)
        assertTrue(validation is dev.doggo.shared.ui.DeadlineValidationResult.Valid, "Date-only deadline must be valid")
        val deadline = validation.deadline

        // Anchor is at 09:00:00 EST on 2026-11-01 = 1793541600000L
        assertEquals(1793541600000L, deadline.anchorEpochMs)
        assertEquals("09:00", deadline.reminderAnchorTime)
        assertEquals(-18000L, deadline.offsetSeconds)

        // Overdue is at midnight next calendar day start (2026-11-02T00:00:00-05:00) = 1793595600000L
        assertEquals(1793595600000L, deadline.overdueEpochMs)

        // Production overdue predicate: !completed && deadline != null && (now >= deadline.overdueEpochMs)
        fun isOverdue(completed: Boolean, nowMs: Long) = !completed && (nowMs >= deadline.overdueEpochMs)

        // Morning at 09:00:00 EST on 2026-11-01 = 1793541600000L -> NOT overdue
        assertFalse(isOverdue(completed = false, nowMs = 1793541600000L))

        // Evening at 23:59:59 EST on 2026-11-01 = 1793595599000L -> NOT overdue
        assertFalse(isOverdue(completed = false, nowMs = 1793595599000L))

        // Next calendar day midnight start = 1793595600000L -> Overdue!
        assertTrue(isOverdue(completed = false, nowMs = 1793595600000L))

        // Completed task is never overdue
        assertFalse(isOverdue(completed = true, nowMs = 1793595600000L))
    }

    @Test
    fun testGraceBoundaryEvaluationAdversarial() {
        // 3 occurrences:
        // idx 0: due at 200,000L
        // idx 1: due at 1,100,000L
        // idx 2: due at 2,000,000L
        val occurrences = listOf(
            CalculatedReminderOccurrence(index = 0, dueEpochMs = 200_000L),
            CalculatedReminderOccurrence(index = 1, dueEpochMs = 1_100_000L),
            CalculatedReminderOccurrence(index = 2, dueEpochMs = 2_000_000L)
        )

        // At anchor + 300,000ms: idx 2 is within 5-min grace window
        val evalGraceExact = ReminderScheduleCalculator.evaluateGrace(
            occurrences = occurrences,
            consumedIndices = emptySet(),
            nowEpochMs = 2_000_000L + 300_000L
        )
        assertNotNull(evalGraceExact.eligibleOpportunity)
        assertEquals(2, evalGraceExact.eligibleOpportunity?.index)
        assertEquals(listOf(0, 1), evalGraceExact.skippedIndices)

        // At anchor + 300,001ms: idx 2 is 1ms past grace window -> skipped!
        val evalPastGrace = ReminderScheduleCalculator.evaluateGrace(
            occurrences = occurrences,
            consumedIndices = emptySet(),
            nowEpochMs = 2_000_000L + 300_001L
        )
        assertNull(evalPastGrace.eligibleOpportunity)
        assertEquals(listOf(0, 1, 2), evalPastGrace.skippedIndices)

        // If idx 2 was already consumed, older occurrences (0, 1) are NEVER resurrected
        val evalAlreadyConsumed = ReminderScheduleCalculator.evaluateGrace(
            occurrences = occurrences,
            consumedIndices = setOf(2),
            nowEpochMs = 2_000_000L + 100_000L
        )
        assertNull(evalAlreadyConsumed.eligibleOpportunity)
        assertEquals(listOf(0, 1), evalAlreadyConsumed.skippedIndices)
    }

    @Test
    fun testDraftIsolationInPersistedProjection() {
        val base = ServerBaseEntity(
            rowId = "task-proj-1",
            serverRevision = 1L,
            deleted = false,
            title = "Committed Base Task",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            epoch = "epoch-1",
            deadlineDate = "2026-11-01",
            deadlineTime = "15:00",
            deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -18000L,
            deadlineAnchorEpochMs = 1793563200000L,
            deadlineOverdueEpochMs = 1793563200000L,
            reminderEnabled = true,
            reminderLeadMinutes = 15L,
            reminderCount = 1L,
            scheduleToken = "tok-base-123"
        )

        // Outbox operation submitted by user modifying lead to 30m
        val outboxOp = OutboxEntity(
            opId = "op-mod-lead",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "task-proj-1",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Committed Base Task",
            completed = false,
            createdAt = 1000L,
            digest = "dig-123",
            localRevision = 2L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 1100L,
            deadlineDate = "2026-11-01",
            deadlineTime = "15:00",
            deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -18000L,
            deadlineAnchorEpochMs = 1793563200000L,
            deadlineOverdueEpochMs = 1793563200000L,
            reminderEnabled = true,
            reminderLeadMinutes = 30L,
            reminderCount = 1L,
            scheduleToken = "tok-outbox-456",
            intentMask = OutboxEntity.INTENT_REMINDER
        )

        // Uncommitted draft in editor_drafts with completely different values (never projected)
        val uncommittedDraft = EditorDraftEntity(
            rowId = "task-proj-1",
            draftTitle = "Unsaved Draft Title",
            draftCompleted = true,
            localRevision = 3L,
            updatedAt = 1200L,
            deadlineDate = "2027-01-01",
            deadlineTime = "08:00",
            deadlineZone = "UTC",
            deadlineOffsetSeconds = 0L,
            deadlineAnchorEpochMs = 9999999999999L,
            deadlineOverdueEpochMs = 9999999999999L,
            reminderEnabled = false,
            scheduleToken = "tok-draft-IGNORE-ME"
        )

        // Projector observes only base and outbox, ignoring uncommittedDraft
        val scheduledList = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(base),
            outboxEntries = listOf(outboxOp)
        )

        assertEquals(1, scheduledList.size)
        val projected = scheduledList[0]

        assertEquals("task-proj-1", projected.taskId)
        assertEquals("tok-outbox-456", projected.scheduleToken)
        assertEquals(30L, projected.reminderRule.leadMinutes)
        assertEquals("Committed Base Task", projected.title)
        assertFalse(projected.completed)
        assertTrue(projected.isEligibleForScheduling)
    }

    @Test
    fun testCancellationBarrierOnSavedState() {
        val completedBase = ServerBaseEntity(
            rowId = "task-comp",
            serverRevision = 1L,
            deleted = false,
            title = "Completed Task",
            completed = true,
            createdAt = 1000L,
            updatedAt = 1000L,
            epoch = "epoch-1",
            deadlineDate = "2026-11-01",
            deadlineTime = "15:00",
            deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -18000L,
            deadlineAnchorEpochMs = 1793563200000L,
            deadlineOverdueEpochMs = 1793563200000L,
            reminderEnabled = true,
            reminderLeadMinutes = 15L,
            reminderCount = 1L,
            scheduleToken = "tok-comp-1"
        )

        val completedProjection = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(completedBase),
            outboxEntries = emptyList()
        )
        assertEquals(1, completedProjection.size)
        assertFalse(completedProjection[0].isEligibleForScheduling, "Completed tasks must not be eligible for scheduling")

        val deletedBase = completedBase.copy(
            rowId = "task-del",
            deleted = true,
            completed = false
        )
        val deletedProjection = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(deletedBase),
            outboxEntries = emptyList()
        )
        assertEquals(1, deletedProjection.size)
        assertFalse(deletedProjection[0].isEligibleForScheduling, "Deleted tasks must not be eligible for scheduling")

        val disabledBase = completedBase.copy(
            rowId = "task-dis",
            deleted = false,
            completed = false,
            reminderEnabled = false
        )
        val disabledProjection = PersistedScheduleProjector.computeProjection(
            serverBases = listOf(disabledBase),
            outboxEntries = emptyList()
        )
        assertEquals(1, disabledProjection.size)
        assertFalse(disabledProjection[0].isEligibleForScheduling, "Disabled reminder tasks must not be eligible for scheduling")
    }

    @Test
    fun testScheduleTokenAssignmentAndPreservation() {
        val base = ServerBaseEntity(
            rowId = "task-tok-1",
            serverRevision = 1L,
            deleted = false,
            title = "Original Title",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            epoch = "epoch-1",
            deadlineDate = "2026-11-01",
            deadlineTime = "15:00",
            deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -18000L,
            deadlineAnchorEpochMs = 1793563200000L,
            deadlineOverdueEpochMs = 1793563200000L,
            reminderEnabled = true,
            reminderLeadMinutes = 15L,
            reminderCount = 1L,
            scheduleToken = "tok-initial"
        )

        // 1. Non-temporal update (e.g. title only) must preserve existing scheduleToken without churn
        val titleOp = OutboxEntity(
            opId = "op-title",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "task-tok-1",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Updated Title Only",
            completed = false,
            createdAt = 1000L,
            digest = "dig-title",
            localRevision = 2L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 1100L,
            intentMask = OutboxEntity.INTENT_TITLE
        )
        val proj1 = PersistedScheduleProjector.computeProjection(listOf(base), listOf(titleOp))
        assertEquals(1, proj1.size)
        assertEquals("tok-initial", proj1[0].scheduleToken, "Non-temporal edit must preserve existing scheduleToken")

        // 2. Reminder rule edit with new schedule token must advance scheduleToken
        val reminderOp = OutboxEntity(
            opId = "op-rem",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "task-tok-1",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Updated Title Only",
            completed = false,
            createdAt = 1000L,
            digest = "dig-rem",
            localRevision = 3L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 1200L,
            reminderEnabled = true,
            reminderLeadMinutes = 30L,
            reminderCount = 1L,
            scheduleToken = "tok-reminder-update",
            intentMask = OutboxEntity.INTENT_REMINDER
        )
        val proj2 = PersistedScheduleProjector.computeProjection(listOf(base), listOf(titleOp, reminderOp))
        assertEquals(1, proj2.size)
        assertEquals("tok-reminder-update", proj2[0].scheduleToken, "Reminder edit must advance scheduleToken")

        // 3. Deadline clearing must reset scheduleToken to null
        val clearDeadlineOp = OutboxEntity(
            opId = "op-clear-dl",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "task-tok-1",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Updated Title Only",
            completed = false,
            createdAt = 1000L,
            digest = "dig-clear",
            localRevision = 4L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 1300L,
            deadlineDate = null,
            deadlineTime = null,
            deadlineZone = null,
            deadlineOffsetSeconds = null,
            deadlineAnchorEpochMs = null,
            deadlineOverdueEpochMs = null,
            scheduleToken = null,
            intentMask = OutboxEntity.INTENT_DEADLINE
        )
        val proj3 = PersistedScheduleProjector.computeProjection(listOf(base), listOf(titleOp, reminderOp, clearDeadlineOp))
        assertEquals(1, proj3.size)
        assertNull(proj3[0].scheduleToken, "Clearing deadline must clear scheduleToken")
        assertFalse(proj3[0].isEligibleForScheduling, "Cleared deadline must not be eligible")
    }
}
