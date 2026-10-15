package dev.doggo.shared.contract

/**
 * Calculated occurrence for a task reminder rule.
 */
data class CalculatedReminderOccurrence(
    val index: Int,
    val dueEpochMs: Long
)

/**
 * Full schedule preview including all planned reminder occurrences and deadline boundaries.
 */
data class ReminderSchedulePreview(
    val occurrences: List<CalculatedReminderOccurrence>,
    val anchorEpochMs: Long,
    val overdueEpochMs: Long
)

/**
 * Result of evaluating reminder occurrences against the 5-minute grace window at a given instant.
 */
data class GraceEvaluationResult(
    /** The single latest eligible occurrence to trigger now, or null if none eligible or already consumed. */
    val eligibleOpportunity: CalculatedReminderOccurrence?,
    /** Indices of older past unconsumed occurrences that must be marked SKIPPED. */
    val skippedIndices: List<Int>,
    /** Future occurrences retained for subsequent alarms (dueEpochMs > nowEpochMs). */
    val futureOccurrences: List<CalculatedReminderOccurrence>,
    /** True if the latest past occurrence was already consumed (blocks fallback to older occurrences). */
    val isLatestConsumed: Boolean
)

/**
 * ReminderScheduleCalculator computes deterministic reminder occurrences
 * using checked 64-bit integer arithmetic and evaluates the strict 5-minute grace window.
 */
object ReminderScheduleCalculator {

    const val GRACE_PERIOD_MS = 300_000L // Exactly 5 minutes
    const val MILLIS_PER_MINUTE = 60_000L

    /**
     * Calculates all planned reminder occurrences for a deadline and rule.
     * Enforces checked arithmetic, 1..10 repeat bounds, and anchor boundary invariants.
     */
    fun calculateOccurrences(
        deadline: TaskDeadline,
        rule: ReminderRule
    ): List<CalculatedReminderOccurrence> {
        if (!rule.enabled) {
            return emptyList()
        }

        val anchor = deadline.anchorEpochMs
        val leadMs = safeMultiply(rule.leadMinutes, MILLIS_PER_MINUTE)
        val firstDue = safeSubtract(anchor, leadMs)

        val count = rule.count.toInt()
        val occurrences = ArrayList<CalculatedReminderOccurrence>(count)

        if (count == 1) {
            occurrences.add(CalculatedReminderOccurrence(index = 0, dueEpochMs = firstDue))
            return occurrences
        }

        val intervalMs = safeMultiply(
            requireNotNull(rule.intervalMinutes) { "intervalMinutes required when count > 1" },
            MILLIS_PER_MINUTE
        )

        for (i in 0 until count) {
            val offset = safeMultiply(i.toLong(), intervalMs)
            val due = safeAdd(firstDue, offset)
            if (due > anchor) {
                throw IllegalArgumentException("Occurrence $i at $due exceeds anchor $anchor")
            }
            occurrences.add(CalculatedReminderOccurrence(index = i, dueEpochMs = due))
        }

        return occurrences
    }

    /**
     * Creates a complete schedule preview for a task deadline and rule.
     */
    fun createPreview(deadline: TaskDeadline, rule: ReminderRule): ReminderSchedulePreview {
        val occurrences = calculateOccurrences(deadline, rule)
        return ReminderSchedulePreview(
            occurrences = occurrences,
            anchorEpochMs = deadline.anchorEpochMs,
            overdueEpochMs = deadline.overdueEpochMs
        )
    }
    /**
     * Evaluates occurrences against the current time [nowEpochMs] applying the strict 5-minute grace rule:
     * - Grace: exactly 300,000ms included; 300,001ms skipped.
     * - Post at most latest eligible opportunity per task; older opportunities skipped.
     * - If latest eligible already consumed, do not fall back to an older one.
     * - All future occurrences (due > now) are retained.
     */
    fun evaluateGrace(
        occurrences: List<CalculatedReminderOccurrence>,
        consumedIndices: Set<Int>,
        nowEpochMs: Long
    ): GraceEvaluationResult {
        if (occurrences.isEmpty()) {
            return GraceEvaluationResult(
                eligibleOpportunity = null,
                skippedIndices = emptyList(),
                futureOccurrences = emptyList(),
                isLatestConsumed = false
            )
        }

        val pastOrCurrent = occurrences.filter { it.dueEpochMs <= nowEpochMs }
        val future = occurrences.filter { it.dueEpochMs > nowEpochMs }

        if (pastOrCurrent.isEmpty()) {
            return GraceEvaluationResult(
                eligibleOpportunity = null,
                skippedIndices = emptyList(),
                futureOccurrences = future,
                isLatestConsumed = false
            )
        }

        // Find the latest (largest index) past occurrence
        val latest = pastOrCurrent.maxByOrNull { it.index }!!
        val elapsedSinceLatest = try {
            safeSubtract(nowEpochMs, latest.dueEpochMs)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        val isLatestInGrace = elapsedSinceLatest <= GRACE_PERIOD_MS
        val isLatestConsumed = consumedIndices.contains(latest.index)

        val skipped = mutableListOf<Int>()

        // All occurrences older than latest must be skipped if not already consumed
        for (occ in pastOrCurrent) {
            if (occ.index < latest.index && !consumedIndices.contains(occ.index)) {
                skipped.add(occ.index)
            }
        }

        val eligible: CalculatedReminderOccurrence?
        if (isLatestInGrace) {
            if (isLatestConsumed) {
                // Latest already consumed: no opportunity, do not fall back
                eligible = null
            } else {
                // Latest is eligible to trigger
                eligible = latest
            }
        } else {
            // Latest is older than 5 minutes: missed, must be skipped
            if (!isLatestConsumed) {
                skipped.add(latest.index)
            }
            eligible = null
        }

        return GraceEvaluationResult(
            eligibleOpportunity = eligible,
            skippedIndices = skipped.sorted(),
            futureOccurrences = future,
            isLatestConsumed = isLatestConsumed
        )
    }

    private fun safeMultiply(a: Long, b: Long): Long {
        val r = a * b
        if (a != 0L && (r / a != b || (a == -1L && b == Long.MIN_VALUE))) {
            throw ArithmeticException("Multiplication overflow: $a * $b")
        }
        return r
    }

    private fun safeSubtract(a: Long, b: Long): Long {
        val r = a - b
        if (((a xor b) and (a xor r)) < 0) {
            throw ArithmeticException("Subtraction overflow: $a - $b")
        }
        return r
    }

    private fun safeAdd(a: Long, b: Long): Long {
        val r = a + b
        if (((a xor r) and (b xor r)) < 0) {
            throw ArithmeticException("Addition overflow: $a + $b")
        }
        return r
    }
}
