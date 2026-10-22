package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskDeadlineReminderModelsTest {

    @Test
    fun testValidTimedDeadlineAndRule() {
        val deadline = TaskDeadline(
            date = "2026-10-15",
            time = "15:00",
            zone = "Asia/Saigon",
            offsetSeconds = 25200L,
            reminderAnchorTime = null,
            anchorEpochMs = 1792051200000L,
            overdueEpochMs = 1792051200000L
        )
        val rule = ReminderRule(
            enabled = true,
            leadMinutes = 30L,
            intervalMinutes = 15L,
            count = 3L
        )
        val todo = Todo(
            taskRef = TaskRef("user-1", "todo-timed-1"),
            title = "Timed Deadline Task",
            completed = false,
            createdAt = 1792000000000L,
            deadline = deadline,
            reminderRule = rule,
            scheduleToken = "token-123"
        )
        assertEquals("token-123", todo.scheduleToken)
        assertEquals(deadline, todo.deadline)
        assertEquals(rule, todo.reminderRule)

        val v2Data = todo.toTaskData()
        assertEquals("todo-timed-1", v2Data.id)
        assertEquals("2026-10-15", v2Data.deadlineDate)
        assertEquals("15:00", v2Data.deadlineTime)
        assertEquals(3L, v2Data.reminderCount)
        assertEquals(15L, v2Data.reminderIntervalMinutes)
        assertEquals(30L, v2Data.reminderLeadMinutes)
        assertTrue(v2Data.reminderEnabled)

        val reconstructedDeadline = v2Data.toDeadline()
        val reconstructedRule = v2Data.toReminderRule()
        assertEquals(deadline, reconstructedDeadline)
        assertEquals(rule, reconstructedRule)
    }

    @Test
    fun testValidDateOnlyDeadline() {
        val deadline = TaskDeadline(
            date = "2026-10-15",
            time = null,
            zone = "Asia/Saigon",
            offsetSeconds = 25200L,
            reminderAnchorTime = "09:00",
            anchorEpochMs = 1792029600000L,
            overdueEpochMs = 1792083600000L
        )
        val rule = ReminderRule(
            enabled = false,
            leadMinutes = 0L,
            intervalMinutes = null,
            count = 1L
        )
        val todo = Todo(
            taskRef = TaskRef("user-1", "todo-date-only-1"),
            title = "Date Only Task",
            completed = false,
            createdAt = 1792000000000L,
            deadline = deadline,
            reminderRule = rule,
            scheduleToken = "token-date-1"
        )
        assertEquals("09:00", todo.deadline?.reminderAnchorTime)
        assertNull(todo.deadline?.time)
        assertFalse(todo.reminderRule.enabled)
    }

    @Test
    fun testDeadlineNullRequiresDisabledRuleAndNullToken() {
        // scheduleToken without deadline should fail
        assertFailsWith<IllegalArgumentException> {
            Todo(
                taskRef = TaskRef("user-1", "todo-fail-1"),
                title = "Bad Token",
                completed = false,
                createdAt = 1000L,
                deadline = null,
                scheduleToken = "orphan-token"
            )
        }

        // enabled reminder without deadline should fail
        assertFailsWith<IllegalArgumentException> {
            Todo(
                taskRef = TaskRef("user-1", "todo-fail-2"),
                title = "Bad Reminder",
                completed = false,
                createdAt = 1000L,
                deadline = null,
                reminderRule = ReminderRule(enabled = true, leadMinutes = 0L, count = 1L)
            )
        }
    }

    @Test
    fun testReminderRuleCountAndIntervalBounds() {
        // count 0 rejected
        assertFailsWith<IllegalArgumentException> {
            ReminderRule(enabled = true, count = 0L)
        }

        // count 11 rejected
        assertFailsWith<IllegalArgumentException> {
            ReminderRule(enabled = true, count = 11L)
        }

        // count 1 must have interval null
        assertFailsWith<IllegalArgumentException> {
            ReminderRule(enabled = true, count = 1L, intervalMinutes = 15L)
        }

        // repeating with interval < 15 rejected
        assertFailsWith<IllegalArgumentException> {
            ReminderRule(enabled = true, count = 2L, intervalMinutes = 14L)
        }

        // repeating with interval == 15 accepted
        val validRule = ReminderRule(enabled = true, count = 2L, intervalMinutes = 15L)
        assertEquals(2L, validRule.count)
        assertEquals(15L, validRule.intervalMinutes)
    }

    @Test
    fun testLastReminderAfterAnchorRejected() {
        val anchor = 1000_000L
        val deadline = TaskDeadline(
            date = "2026-10-15",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = anchor,
            overdueEpochMs = anchor
        )
        // lead 10m (600_000ms), 3 occurrences at 15m intervals (900_000ms each)
        // first = 1000_000 - 600_000 = 400_000
        // last = 400_000 + 2 * 900_000 = 2_200_000 > anchor (1000_000) -> should fail
        val badRule = ReminderRule(
            enabled = true,
            leadMinutes = 10L,
            intervalMinutes = 15L,
            count = 3L
        )
        assertFailsWith<IllegalArgumentException> {
            Todo(
                taskRef = TaskRef("user-1", "todo-overflow"),
                title = "Overflowing reminder",
                completed = false,
                createdAt = 100L,
                deadline = deadline,
                reminderRule = badRule,
                scheduleToken = "tok-1"
            )
        }
    }

    @Test
    fun testSemanticChangeDetection() {
        val d1 = TaskDeadline(
            date = "2026-10-15",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1000L,
            overdueEpochMs = 1000L
        )
        val r1 = ReminderRule(enabled = false, count = 1L)

        // Same deadline and rule -> no semantic change
        assertFalse(hasScheduleSemanticChange(d1, r1, d1, r1))

        // Same deadline, rule enabled changed -> semantic change
        val r2 = ReminderRule(enabled = true, count = 1L)
        assertTrue(hasScheduleSemanticChange(d1, r1, d1, r2))

        // Deadline date changed -> semantic change
        val d2 = d1.copy(date = "2026-10-16")
        assertTrue(hasScheduleSemanticChange(d1, r1, d2, r1))

        // Clearing deadline -> semantic change
        assertTrue(hasScheduleSemanticChange(d1, r1, null, ReminderRule.DISABLED))

        // Adding deadline where previously null -> semantic change
        assertTrue(hasScheduleSemanticChange(null, ReminderRule.DISABLED, d1, r1))

        // Both null -> no semantic change
        assertFalse(hasScheduleSemanticChange(null, ReminderRule.DISABLED, null, ReminderRule.DISABLED))
    }
}
