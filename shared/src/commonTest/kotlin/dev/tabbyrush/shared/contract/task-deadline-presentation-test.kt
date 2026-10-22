package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskDeadlinePresentationTest {

    @Test
    fun testNullDeadlineReturnsNull() {
        val result = TaskDeadlinePresentation.formatDeadline(
            deadline = null,
            effectiveCompleted = false,
            nowEpochMs = 1000L
        )
        assertNull(result)
    }

    @Test
    fun testDateOnlyDeadlineFormatting() {
        val deadline = TaskDeadline(
            date = "2026-11-05",
            time = null,
            zone = "America/New_York",
            offsetSeconds = -18000L,
            reminderAnchorTime = "09:00", // anchor must NOT leak as deadline time!
            anchorEpochMs = 1_793_800_800_000L,
            overdueEpochMs = 1_793_854_800_000L
        )

        // Upcoming (not overdue)
        val upcoming = TaskDeadlinePresentation.formatDeadline(
            deadline = deadline,
            effectiveCompleted = false,
            nowEpochMs = 1_793_800_000_000L
        )
        assertNotNull(upcoming)
        assertEquals("Due 2026-11-05", upcoming.label)
        assertFalse(upcoming.isOverdue)
        assertNull(upcoming.overdueLabel)
        assertEquals("Due 2026-11-05", upcoming.contentDescription)

        // Overdue and incomplete
        val overdue = TaskDeadlinePresentation.formatDeadline(
            deadline = deadline,
            effectiveCompleted = false,
            nowEpochMs = 1_793_860_000_000L
        )
        assertNotNull(overdue)
        assertEquals("Due 2026-11-05", overdue.label)
        assertTrue(overdue.isOverdue)
        assertEquals("Overdue", overdue.overdueLabel)
        assertEquals("Due 2026-11-05, Overdue", overdue.contentDescription)

        // Overdue epoch passed, but task is completed -> NOT overdue!
        val completed = TaskDeadlinePresentation.formatDeadline(
            deadline = deadline,
            effectiveCompleted = true,
            nowEpochMs = 1_793_860_000_000L
        )
        assertNotNull(completed)
        assertFalse(completed.isOverdue, "Completed task must never be labeled overdue")
        assertNull(completed.overdueLabel)
        assertEquals("Due 2026-11-05", completed.contentDescription)
    }

    @Test
    fun testTimedDeadlineFormattingPreservesZone() {
        val deadline = TaskDeadline(
            date = "2026-10-25",
            time = "14:30",
            zone = "Asia/Tokyo",
            offsetSeconds = 32400L,
            anchorEpochMs = 1_792_906_200_000L,
            overdueEpochMs = 1_792_906_200_000L
        )

        val result = TaskDeadlinePresentation.formatDeadline(
            deadline = deadline,
            effectiveCompleted = false,
            nowEpochMs = 1_792_900_000_000L
        )
        assertNotNull(result)
        assertEquals("Due 2026-10-25 14:30 (Asia/Tokyo)", result.label)
        assertFalse(result.isOverdue)
        assertEquals("Due 2026-10-25 14:30 (Asia/Tokyo)", result.contentDescription)
    }
}
