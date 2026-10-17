package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.PersistedTaskSchedule
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.sync.FakeTabbyRushDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderOccurrenceStoreTest {

    private val accountKey = AccountKey.create("http://localhost:3000", "user-123")
    private val dao = FakeTabbyRushDao()
    private val store = ReminderOccurrenceStore(accountKey, dao)

    private val deadline = TaskDeadline(
        date = "2026-10-20",
        time = "10:00",
        zone = "UTC",
        offsetSeconds = 0L,
        anchorEpochMs = 2_000_000L,
        overdueEpochMs = 2_000_000L
    )
    @Test
    fun testSyncPlannedOccurrencesCreatesActiveEntries() = runTest {
        val schedule = PersistedTaskSchedule(
            taskId = "task-1",
            title = "Milk",
            completed = false,
            isDeleted = false,
            deadline = deadline,
            reminderRule = ReminderRule(enabled = true, leadMinutes = 30L, intervalMinutes = 15L, count = 2L),
            scheduleToken = "token-1",
            hasUnresolvedConflict = false
        )

        val delta = store.syncPlannedOccurrences(generation = 1L, schedules = listOf(schedule), nowEpochMs = 0L)
        assertEquals(2, delta.alarmsToSchedule.size)

        val entries = dao.getLedgerEntriesForTask(accountKey.toString(), 1L, "task-1")
        assertEquals(2, entries.size)
        assertEquals(ReminderDeliveryLedgerEntity.OUTCOME_PENDING, entries[0].claimOutcome)
        assertEquals(ReminderDeliveryLedgerEntity.OUTCOME_PENDING, entries[1].claimOutcome)
    }

    @Test
    fun testClaimOpportunityMarksOlderSkippedAndLatestClaimed() = runTest {
        val schedule = PersistedTaskSchedule(
            taskId = "task-1",
            title = "Milk",
            completed = false,
            isDeleted = false,
            deadline = deadline,
            reminderRule = ReminderRule(enabled = true, leadMinutes = 30L, intervalMinutes = 15L, count = 2L),
            scheduleToken = "token-1",
            hasUnresolvedConflict = false
        )
        store.syncPlannedOccurrences(generation = 1L, schedules = listOf(schedule), nowEpochMs = 0L)

        // Occurrences at 2_000_000 - 1_800_000 = 200_000 (0) and 200_000 + 900_000 = 1_100_000 (1).
        // At now = 1_200_000L: both are past.
        // Occurrence 1 is within grace (1_200_000 - 1_100_000 = 100_000 <= 300_000).
        val claimed = store.evaluateAndClaimDueOpportunity(1L, "task-1", "token-1", nowEpochMs = 1_200_000L)
        assertNotNull(claimed)
        assertEquals(1, claimed.index)

        val entries = dao.getLedgerEntriesForTask(accountKey.toString(), 1L, "task-1")
        assertEquals(ReminderDeliveryLedgerEntity.OUTCOME_SKIPPED, entries[0].claimOutcome)
        assertEquals(ReminderDeliveryLedgerEntity.OUTCOME_CLAIMED, entries[1].claimOutcome)
    }

    @Test
    fun testCrashAfterClaimNeverReplaysClaimedOpportunity() = runTest {
        val schedule = PersistedTaskSchedule(
            taskId = "task-1",
            title = "Milk",
            completed = false,
            isDeleted = false,
            deadline = deadline,
            reminderRule = ReminderRule(enabled = true, leadMinutes = 0L, intervalMinutes = null, count = 1L),
            scheduleToken = "token-1",
            hasUnresolvedConflict = false
        )
        store.syncPlannedOccurrences(generation = 1L, schedules = listOf(schedule), nowEpochMs = 0L)

        // Claim at now = 2_000_100 (due is 2_000_000)
        val claimed = store.evaluateAndClaimDueOpportunity(1L, "task-1", "token-1", nowEpochMs = 2_000_100L)
        assertNotNull(claimed)

        // Process crashes before posting. Now app restarts and re-evaluates:
        val reClaimed = store.evaluateAndClaimDueOpportunity(1L, "task-1", "token-1", nowEpochMs = 2_000_200L)
        assertNull(reClaimed) // NEVER replays consumed opportunity!
    }

    @Test
    fun testCompletionAndReopenRetainsPriorConsumedCount() = runTest {
        val schedule = PersistedTaskSchedule(
            taskId = "task-1",
            title = "Milk",
            completed = false,
            isDeleted = false,
            deadline = deadline,
            reminderRule = ReminderRule(enabled = true, leadMinutes = 30L, intervalMinutes = 15L, count = 2L),
            scheduleToken = "token-1",
            hasUnresolvedConflict = false
        )
        store.syncPlannedOccurrences(generation = 1L, schedules = listOf(schedule), nowEpochMs = 0L)

        // Claim occurrence 0
        dao.claimOccurrenceAtomic(accountKey.toString(), 1L, "task-1", "token-1", 0, claimTimeEpochMs = 1000L)

        // Complete task
        val completedSchedule = schedule.copy(completed = true)
        store.syncPlannedOccurrences(generation = 1L, schedules = listOf(completedSchedule), nowEpochMs = 2000L)

        var entries = dao.getLedgerEntriesForTask(accountKey.toString(), 1L, "task-1")
        assertTrue(entries.all { it.cancellationState == ReminderDeliveryLedgerEntity.CANCELLATION_CANCELLED })

        // Reopen task (without changing scheduleToken)
        store.syncPlannedOccurrences(generation = 1L, schedules = listOf(schedule), nowEpochMs = 3000L)
        entries = dao.getLedgerEntriesForTask(accountKey.toString(), 1L, "task-1")

        // Occurrence 0 retained CLAIMED; occurrence 1 reactivated as PENDING
        assertEquals(ReminderDeliveryLedgerEntity.OUTCOME_CLAIMED, entries[0].claimOutcome)
        assertEquals(ReminderDeliveryLedgerEntity.OUTCOME_PENDING, entries[1].claimOutcome)
        assertEquals(ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE, entries[0].cancellationState)
        assertEquals(ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE, entries[1].cancellationState)
    }
}
