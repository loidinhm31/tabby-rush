package dev.doggo.shared.contract

/**
 * Result of resolving a local date and minute-precision local time in a specified IANA timezone.
 */
sealed interface StrictZoneOffsetResult {
    /**
     * Exact unambiguous local time in the timezone with exactly one valid UTC offset.
     */
    data class Single(
        val offsetSeconds: Long,
        val epochMs: Long
    ) : StrictZoneOffsetResult

    /**
     * Ambiguous local time during a DST fall-back overlap with two valid UTC offsets.
     * [earlierEpochMs] has [earlierOffsetSeconds] (the offset with higher value / less negative offset, e.g. EDT -04:00).
     * [laterEpochMs] has [laterOffsetSeconds] (the offset with lower value / more negative offset, e.g. EST -05:00).
     * Both instants produce the same local time string in this timezone.
     */
    data class Overlap(
        val earlierOffsetSeconds: Long,
        val earlierEpochMs: Long,
        val laterOffsetSeconds: Long,
        val laterEpochMs: Long
    ) : StrictZoneOffsetResult

    /**
     * Non-existent local time skipped during a DST spring-forward gap.
     * Zero valid offsets exist for this local time.
     */
    data class Gap(
        val gapBeforeOffsetSeconds: Long,
        val gapAfterOffsetSeconds: Long
    ) : StrictZoneOffsetResult
}

/**
 * Contract for strict timezone resolution and calendar day boundary calculation.
 * Ensures that nonexistent times are rejected, ambiguous times require explicit offset selection,
 * and calendar day boundaries account for 23-hour/25-hour transitions rather than fixed +24h additions.
 */
interface StrictTimeZoneResolver {
    /**
     * Resolves the zone offsets and epoch milliseconds for a local date (YYYY-MM-DD) and time (HH:mm).
     * Returns [StrictZoneOffsetResult.Single], [StrictZoneOffsetResult.Overlap], or [StrictZoneOffsetResult.Gap].
     */
    fun resolveOffset(zoneId: String, date: String, time: String): StrictZoneOffsetResult

    /**
     * Resolves to a single epoch millisecond instant.
     * If the time is in an overlap, [chosenOffsetSeconds] is required and must match either
     * the earlier or later offset.
     * If the time is in a gap, throws [IllegalArgumentException].
     */
    fun resolveInstant(
        zoneId: String,
        date: String,
        time: String,
        chosenOffsetSeconds: Long? = null
    ): Long

    /**
     * Computes the epoch millisecond of the start of the specified calendar day (YYYY-MM-DD) in [zoneId].
     * Defaults to 00:00:00, or the earliest valid instant of that date if midnight is in a DST gap.
     */
    fun computeDayStartEpochMs(zoneId: String, date: String): Long

    /**
     * Computes the epoch millisecond of the start of the next local calendar day following [date] in [zoneId].
     * Uses strict calendar date arithmetic (date + 1 day at start of day), NOT instant + 24 hours.
     */
    fun computeNextCalendarDayStartEpochMs(zoneId: String, date: String): Long

    /**
     * Validates whether the given zone identifier is recognized and supported by the platform tzdb.
     */
    fun isZoneSupported(zoneId: String): Boolean

    companion object {
        val default: StrictTimeZoneResolver get() = getStrictTimeZoneResolver()
    }
}

/**
 * Platform factory function providing the strict timezone resolver implementation.
 */
expect fun getStrictTimeZoneResolver(): StrictTimeZoneResolver
