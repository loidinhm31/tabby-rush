package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TaskSurfaceProjectorTest {

    private fun createTodo(
        id: String,
        title: String = "Todo $id",
        completed: Boolean = false,
        isDeleted: Boolean = false,
        createdAt: Long = 1000L,
        updatedAt: Long = createdAt,
        deadline: TaskDeadline? = null,
        scheduleToken: String? = if (deadline != null) "token-$id" else null
    ) = Todo(
        id = id,
        title = title,
        completed = completed,
        createdAt = createdAt,
        updatedAt = maxOf(updatedAt, createdAt),
        serverRevision = 1L,
        isPending = false,
        isDeleted = isDeleted,
        deadline = deadline,
        reminderRule = ReminderRule.DISABLED,
        scheduleToken = scheduleToken
    )

    private fun createSchedule(
        taskId: String,
        title: String = "Schedule $taskId",
        completed: Boolean = false,
        isDeleted: Boolean = false,
        hasUnresolvedConflict: Boolean = false,
        deadline: TaskDeadline? = null,
        reminderRule: ReminderRule = ReminderRule.DISABLED,
        scheduleToken: String? = null
    ) = PersistedTaskSchedule(
        taskId = taskId,
        title = title,
        completed = completed,
        isDeleted = isDeleted,
        deadline = deadline,
        reminderRule = reminderRule,
        scheduleToken = scheduleToken,
        hasUnresolvedConflict = hasUnresolvedConflict
    )

    private fun createTimedDeadline(
        date: String = "2026-10-20",
        time: String = "15:00",
        zone: String = "UTC",
        offsetSeconds: Long = 0L,
        overdueEpochMs: Long = 1_700_000_000L
    ) = TaskDeadline(
        date = date,
        time = time,
        zone = zone,
        offsetSeconds = offsetSeconds,
        reminderAnchorTime = null,
        anchorEpochMs = overdueEpochMs,
        overdueEpochMs = overdueEpochMs
    )

    private fun createDateOnlyDeadline(
        date: String = "2026-10-20",
        zone: String = "UTC",
        offsetSeconds: Long = 0L,
        anchorEpochMs: Long = 1_700_000_000L,
        overdueEpochMs: Long = 1_700_054_000L
    ) = TaskDeadline(
        date = date,
        time = null,
        zone = zone,
        offsetSeconds = offsetSeconds,
        reminderAnchorTime = "09:00",
        anchorEpochMs = anchorEpochMs,
        overdueEpochMs = overdueEpochMs
    )

    @Test
    fun testPartitionSurfaceTodosEmpty() {
        val sections = partitionSurfaceTodos(emptyList())
        assertTrue(sections.incomplete.isEmpty())
        assertTrue(sections.completed.isEmpty())
    }

    @Test
    fun testPartitionSurfaceTodosSeparatesAndExcludesDeleted() {
        val todos = listOf(
            createTodo(id = "1", createdAt = 3000L, completed = false),
            createTodo(id = "2", createdAt = 2000L, completed = true),
            createTodo(id = "3", createdAt = 1000L, completed = false, isDeleted = true),
            createTodo(id = "4", createdAt = 500L, completed = true, isDeleted = true),
            createTodo(id = "5", createdAt = 100L, completed = false)
        )

        val sections = partitionSurfaceTodos(todos)
        assertEquals(listOf("1", "5"), sections.incomplete.map { it.id })
        assertEquals(listOf("2"), sections.completed.map { it.id })
    }

    @Test
    fun testPartitionSurfaceTodosRetainsOrdering() {
        val todos = listOf(
            createTodo(id = "a", createdAt = 3000L, completed = false),
            createTodo(id = "b", createdAt = 2000L, completed = false),
            createTodo(id = "c", createdAt = 1000L, completed = true),
            createTodo(id = "d", createdAt = 500L, completed = true)
        )

        val sections = partitionSurfaceTodos(todos)
        assertEquals(listOf("a", "b"), sections.incomplete.map { it.id })
        assertEquals(listOf("c", "d"), sections.completed.map { it.id })
    }

    @Test
    fun testSelectNextDeadlineEmpty() {
        assertNull(selectNextDeadline(emptyList(), nowEpochMs = 1_000_000L))
    }

    @Test
    fun testSelectNextDeadlineExcludesIneligible() {
        val dl = createTimedDeadline(overdueEpochMs = 2_000L)
        val schedules = listOf(
            createSchedule(taskId = "deleted", isDeleted = true, deadline = dl),
            createSchedule(taskId = "completed", completed = true, deadline = dl),
            createSchedule(taskId = "no-deadline", deadline = null)
        )

        assertNull(selectNextDeadline(schedules, nowEpochMs = 1_000L))
    }

    @Test
    fun testSelectNextDeadlineSelectsSingleUpcoming() {
        val dl = createTimedDeadline(overdueEpochMs = 5_000L)
        val schedule = createSchedule(taskId = "t1", title = "Task 1", deadline = dl)

        val selection = selectNextDeadline(listOf(schedule), nowEpochMs = 1_000L)
        assertNotNull(selection)
        assertEquals("t1", selection.taskId)
        assertEquals("Task 1", selection.title)
        assertEquals(dl, selection.deadline)
        assertFalse(selection.hasUnresolvedConflict)
    }

    @Test
    fun testSelectNextDeadlinePrioritizesOverdueOverUpcoming() {
        val dlUpcoming = createTimedDeadline(overdueEpochMs = 10_000L)
        val dlOverdue = createTimedDeadline(overdueEpochMs = 4_000L)

        val schedules = listOf(
            createSchedule(taskId = "t-upcoming", deadline = dlUpcoming),
            createSchedule(taskId = "t-overdue", deadline = dlOverdue)
        )

        // At now = 5000, t-overdue is overdue (5000 >= 4000) while t-upcoming is upcoming (5000 < 10000)
        val selection = selectNextDeadline(schedules, nowEpochMs = 5_000L)
        assertNotNull(selection)
        assertEquals("t-overdue", selection.taskId)
    }

    @Test
    fun testSelectNextDeadlineExactOverdueBoundaryIsOverdue() {
        val dl = createTimedDeadline(overdueEpochMs = 5_000L)
        val schedule = createSchedule(taskId = "t-boundary", deadline = dl)

        // Exact equality nowEpochMs == overdueEpochMs counts as overdue
        val selection = selectNextDeadline(listOf(schedule), nowEpochMs = 5_000L)
        assertNotNull(selection)
        assertEquals("t-boundary", selection.taskId)
    }

    @Test
    fun testSelectNextDeadlineMultipleOverdueOldestWinsWithTieBreak() {
        val dlOldest = createTimedDeadline(overdueEpochMs = 2_000L)
        val dlLessOld = createTimedDeadline(overdueEpochMs = 3_000L)
        val dlOldestTieB = createTimedDeadline(overdueEpochMs = 2_000L)

        val schedules = listOf(
            createSchedule(taskId = "task-z", deadline = dlOldestTieB),
            createSchedule(taskId = "task-a", deadline = dlOldest),
            createSchedule(taskId = "task-m", deadline = dlLessOld)
        )

        // Both task-z and task-a have oldest overdue (2000L). Tie break by taskId ASC chooses task-a.
        val selection = selectNextDeadline(schedules, nowEpochMs = 10_000L)
        assertNotNull(selection)
        assertEquals("task-a", selection.taskId)
    }

    @Test
    fun testSelectNextDeadlineMultipleUpcomingEarliestWinsWithTieBreak() {
        val dlFar = createTimedDeadline(overdueEpochMs = 20_000L)
        val dlNearA = createTimedDeadline(overdueEpochMs = 12_000L)
        val dlNearB = createTimedDeadline(overdueEpochMs = 12_000L)

        val schedules = listOf(
            createSchedule(taskId = "task-x", deadline = dlNearB),
            createSchedule(taskId = "task-c", deadline = dlNearA),
            createSchedule(taskId = "task-late", deadline = dlFar)
        )

        // Both task-x and task-c are due at 12000L. Tie break by taskId ASC chooses task-c.
        val selection = selectNextDeadline(schedules, nowEpochMs = 5_000L)
        assertNotNull(selection)
        assertEquals("task-c", selection.taskId)
    }

    @Test
    fun testSelectNextDeadlinePreservesUnresolvedConflictFlag() {
        val dl = createTimedDeadline(overdueEpochMs = 8_000L)
        val schedule = createSchedule(
            taskId = "conflict-task",
            title = "Conflicted task",
            hasUnresolvedConflict = true,
            deadline = dl
        )

        val selection = selectNextDeadline(listOf(schedule), nowEpochMs = 1_000L)
        assertNotNull(selection)
        assertEquals("conflict-task", selection.taskId)
        assertTrue(selection.hasUnresolvedConflict)
    }

    @Test
    fun testSelectNextDeadlineEligibleEvenWhenReminderDisabledOrTokenMissing() {
        val dl = createTimedDeadline(overdueEpochMs = 8_000L)
        val schedule = createSchedule(
            taskId = "opted-out-reminder",
            deadline = dl,
            reminderRule = ReminderRule.DISABLED,
            scheduleToken = null
        )

        val selection = selectNextDeadline(listOf(schedule), nowEpochMs = 1_000L)
        assertNotNull(selection)
        assertEquals("opted-out-reminder", selection.taskId)
    }

    @Test
    fun testSelectNextDeadlineDateOnlyUsesNextDayOverdueBoundary() {
        // Date-only deadline has anchor at 09:00 (1000) but overdue at next midnight (2000)
        val dateOnlyDl = createDateOnlyDeadline(anchorEpochMs = 1_000L, overdueEpochMs = 2_000L)
        // Timed deadline due at 1500
        val timedDl = createTimedDeadline(overdueEpochMs = 1_500L)

        val schedules = listOf(
            createSchedule(taskId = "date-only", deadline = dateOnlyDl),
            createSchedule(taskId = "timed", deadline = timedDl)
        )

        // At now = 500, timedDl (1500) is due before dateOnlyDl (2000), even though dateOnlyDl anchor is 1000
        val selection = selectNextDeadline(schedules, nowEpochMs = 500L)
        assertNotNull(selection)
        assertEquals("timed", selection.taskId)
    }
}
