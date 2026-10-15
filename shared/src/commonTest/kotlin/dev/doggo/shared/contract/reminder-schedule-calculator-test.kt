package dev.doggo.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderScheduleCalculatorTest {

    private val deadline = TaskDeadline(
        date = "2026-10-20",
        time = "10:00",
        zone = "UTC",
        offsetSeconds = 0L,
        anchorEpochMs = 1_000_000L,
        overdueEpochMs = 1_000_000L
    )

    @Test
    fun testDisabledRuleCalculatesZeroOccurrences() {
        val rule = ReminderRule.DISABLED
        val occurrences = ReminderScheduleCalculator.calculateOccurrences(deadline, rule)
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun testSingleOccurrenceCalculation() {
        val rule = ReminderRule(enabled = true, leadMinutes = 15L, intervalMinutes = null, count = 1L)
        val occurrences = ReminderScheduleCalculator.calculateOccurrences(deadline, rule)
        assertEquals(1, occurrences.size)
        assertEquals(0, occurrences[0].index)
        assertEquals(1_000_000L - (15L * 60_000L), occurrences[0].dueEpochMs)
    }

    @Test
    fun testRepeatingOccurrencesCalculation() {
        val rule = ReminderRule(enabled = true, leadMinutes = 60L, intervalMinutes = 15L, count = 3L)
        val occurrences = ReminderScheduleCalculator.calculateOccurrences(deadline, rule)
        assertEquals(3, occurrences.size)

        val first = 1_000_000L - (60L * 60_000L)
        val step = 15L * 60_000L
        assertEquals(first, occurrences[0].dueEpochMs)
        assertEquals(first + step, occurrences[1].dueEpochMs)
        assertEquals(first + (2 * step), occurrences[2].dueEpochMs)
    }

    @Test
    fun testOccurrenceExceedingAnchorThrows() {
        val rule = ReminderRule(enabled = true, leadMinutes = 10L, intervalMinutes = 15L, count = 2L)
        assertFailsWith<IllegalArgumentException> {
            ReminderScheduleCalculator.calculateOccurrences(deadline, rule)
        }
    }

    @Test
    fun testArithmeticOverflowThrows() {
        val rule = ReminderRule(enabled = true, leadMinutes = Long.MAX_VALUE, intervalMinutes = null, count = 1L)
        assertFailsWith<ArithmeticException> {
            ReminderScheduleCalculator.calculateOccurrences(deadline, rule)
        }
    }

    @Test
    fun testExactFiveMinuteGraceBoundary() {
        val occurrences = listOf(
            CalculatedReminderOccurrence(index = 0, dueEpochMs = 1_000_000L)
        )

        // Exactly 300,000ms after due: within grace
        val resultExact = ReminderScheduleCalculator.evaluateGrace(
            occurrences = occurrences,
            consumedIndices = emptySet(),
            nowEpochMs = 1_000_000L + 300_000L
        )
        assertNotNull(resultExact.eligibleOpportunity)
        assertEquals(0, resultExact.eligibleOpportunity?.index)
        assertTrue(resultExact.skippedIndices.isEmpty())

        // Exactly 300,001ms after due: 1ms past grace window -> skipped!
        val resultSkipped = ReminderScheduleCalculator.evaluateGrace(
            occurrences = occurrences,
            consumedIndices = emptySet(),
            nowEpochMs = 1_000_000L + 300_001L
        )
        assertNull(resultSkipped.eligibleOpportunity)
        assertEquals(listOf(0), resultSkipped.skippedIndices)
    }

    @Test
    fun testLatestOpportunitySelectedAndOlderSkipped() {
        val occurrences = listOf(
            CalculatedReminderOccurrence(index = 0, dueEpochMs = 1_000_000L),
            CalculatedReminderOccurrence(index = 1, dueEpochMs = 1_500_000L),
            CalculatedReminderOccurrence(index = 2, dueEpochMs = 2_000_000L)
        )

        // At now = 1_600_000L:
        // Occurrences 0 and 1 are in the past. Latest past is index 1.
        // 1_600_000 - 1_500_000 = 100_000 <= 300_000 (in grace).
        // Occurrence 0 is older: must be marked skipped!
        // Occurrence 2 is future: retained!
        val result = ReminderScheduleCalculator.evaluateGrace(
            occurrences = occurrences,
            consumedIndices = emptySet(),
            nowEpochMs = 1_600_000L
        )

        assertEquals(1, result.eligibleOpportunity?.index)
        assertEquals(listOf(0), result.skippedIndices)
        assertEquals(1, result.futureOccurrences.size)
        assertEquals(2, result.futureOccurrences[0].index)
    }

    @Test
    fun testConsumedLatestOpportunityDoesNotFallBackToOlder() {
        val occurrences = listOf(
            CalculatedReminderOccurrence(index = 0, dueEpochMs = 1_000_000L),
            CalculatedReminderOccurrence(index = 1, dueEpochMs = 1_500_000L)
        )

        // Latest past occurrence (index 1) was ALREADY CONSUMED!
        // Even though index 0 was never consumed, it MUST NOT fall back to index 0!
        val result = ReminderScheduleCalculator.evaluateGrace(
            occurrences = occurrences,
            consumedIndices = setOf(1),
            nowEpochMs = 1_600_000L
        )

        assertNull(result.eligibleOpportunity)
        assertTrue(result.isLatestConsumed)
        assertEquals(listOf(0), result.skippedIndices)
    }

    @Test
    fun testClockJumpHoursLaterDoesNotBurst() {
        val occurrences = listOf(
            CalculatedReminderOccurrence(index = 0, dueEpochMs = 1_000_000L),
            CalculatedReminderOccurrence(index = 1, dueEpochMs = 1_500_000L),
            CalculatedReminderOccurrence(index = 2, dueEpochMs = 2_000_000L)
        )

        // Device slept or clock jumped 10 hours later (now = 40_000_000L)
        // All occurrences are past. Latest (index 2) is older than 5 minutes.
        // Zero opportunities fire; all are skipped without burst!
        val result = ReminderScheduleCalculator.evaluateGrace(
            occurrences = occurrences,
            consumedIndices = emptySet(),
            nowEpochMs = 40_000_000L
        )

        assertNull(result.eligibleOpportunity)
        assertEquals(listOf(0, 1, 2), result.skippedIndices)
        assertTrue(result.futureOccurrences.isEmpty())
    }
}
