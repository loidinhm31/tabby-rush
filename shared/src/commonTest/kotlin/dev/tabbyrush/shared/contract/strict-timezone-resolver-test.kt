package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StrictTimeZoneResolverTest {
    private val resolver = StrictTimeZoneResolver.default

    @Test
    fun testDstGapRejectionNewYorkSpringForward() {
        // In America/New_York on 2026-03-08, at 02:00 clocks advance to 03:00.
        // 02:30 is a non-existent local time (gap).
        val result = resolver.resolveOffset(
            zoneId = "America/New_York",
            date = "2026-03-08",
            time = "02:30"
        )

        assertTrue(result is StrictZoneOffsetResult.Gap, "Expected Gap for 2026-03-08 02:30 in New York")
        assertEquals(-18000L, result.gapBeforeOffsetSeconds, "Offset before gap should be EST (-5h)")
        assertEquals(-14400L, result.gapAfterOffsetSeconds, "Offset after gap should be EDT (-4h)")

        assertFailsWith<IllegalArgumentException> {
            resolver.resolveInstant(
                zoneId = "America/New_York",
                date = "2026-03-08",
                time = "02:30"
            )
        }
    }

    @Test
    fun testDstOverlapEnumerationNewYorkFallBack() {
        // In America/New_York on 2026-11-01, at 02:00 clocks turn back to 01:00.
        // 01:30 repeats twice (first EDT -04:00, then EST -05:00).
        val result = resolver.resolveOffset(
            zoneId = "America/New_York",
            date = "2026-11-01",
            time = "01:30"
        )

        assertTrue(result is StrictZoneOffsetResult.Overlap, "Expected Overlap for 2026-11-01 01:30 in New York")
        assertEquals(-14400L, result.earlierOffsetSeconds, "Earlier offset should be EDT (-4h = -14400s)")
        assertEquals(-18000L, result.laterOffsetSeconds, "Later offset should be EST (-5h = -18000s)")

        // 1 hour separation between the two valid instants
        val diffMs = result.laterEpochMs - result.earlierEpochMs
        assertEquals(3_600_000L, diffMs, "Difference between overlap instants must be exactly 1 hour (3,600,000 ms)")

        // Calling resolveInstant without explicit offset must fail
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveInstant(
                zoneId = "America/New_York",
                date = "2026-11-01",
                time = "01:30",
                chosenOffsetSeconds = null
            )
        }

        // Calling with invalid offset must fail
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveInstant(
                zoneId = "America/New_York",
                date = "2026-11-01",
                time = "01:30",
                chosenOffsetSeconds = -21600L
            )
        }

        // Resolving with earlier offset (EDT)
        val earlierInstant = resolver.resolveInstant(
            zoneId = "America/New_York",
            date = "2026-11-01",
            time = "01:30",
            chosenOffsetSeconds = -14400L
        )
        assertEquals(result.earlierEpochMs, earlierInstant)
        assertEquals(1793511000000L, earlierInstant)

        // Resolving with later offset (EST)
        val laterInstant = resolver.resolveInstant(
            zoneId = "America/New_York",
            date = "2026-11-01",
            time = "01:30",
            chosenOffsetSeconds = -18000L
        )
        assertEquals(result.laterEpochMs, laterInstant)
        assertEquals(1793514600000L, laterInstant)
    }

    @Test
    fun testStandardUnambiguousTimeResolution() {
        val result = resolver.resolveOffset(
            zoneId = "America/New_York",
            date = "2026-06-15",
            time = "10:00"
        )

        assertTrue(result is StrictZoneOffsetResult.Single)
        assertEquals(-14400L, result.offsetSeconds)
        assertEquals(1781532000000L, result.epochMs)

        val instant = resolver.resolveInstant(
            zoneId = "America/New_York",
            date = "2026-06-15",
            time = "10:00"
        )
        assertEquals(1781532000000L, instant)

        // Matching offset passes
        val instantWithOffset = resolver.resolveInstant(
            zoneId = "America/New_York",
            date = "2026-06-15",
            time = "10:00",
            chosenOffsetSeconds = -14400L
        )
        assertEquals(1781532000000L, instantWithOffset)

        // Mismatched offset fails
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveInstant(
                zoneId = "America/New_York",
                date = "2026-06-15",
                time = "10:00",
                chosenOffsetSeconds = -18000L
            )
        }
    }

    @Test
    fun testTwentyThreeHourDayBoundaryAcrossSpringForward() {
        // In America/New_York on 2026-03-08 (spring forward), the day has only 23 hours.
        val dayStart = resolver.computeDayStartEpochMs("America/New_York", "2026-03-08")
        val nextDayStart = resolver.computeNextCalendarDayStartEpochMs("America/New_York", "2026-03-08")

        // 2026-03-08 00:00:00 EST (-05:00) = 1772946000000L
        assertEquals(1772946000000L, dayStart)
        // 2026-03-09 00:00:00 EDT (-04:00) = 1773028800000L
        assertEquals(1773028800000L, nextDayStart)

        val durationMs = nextDayStart - dayStart
        val twentyThreeHoursMs = 23L * 3600L * 1000L
        val twentyFourHoursMs = 24L * 3600L * 1000L

        assertEquals(twentyThreeHoursMs, durationMs, "Day duration must be exactly 23 hours")
        assertNotEquals(twentyFourHoursMs, durationMs, "Calendar next day MUST NOT be calculated as +24 hours")
    }

    @Test
    fun testTwentyFiveHourDayBoundaryAcrossFallBack() {
        // In America/New_York on 2026-11-01 (fall back), the day has 25 hours.
        val dayStart = resolver.computeDayStartEpochMs("America/New_York", "2026-11-01")
        val nextDayStart = resolver.computeNextCalendarDayStartEpochMs("America/New_York", "2026-11-01")
        // 2026-11-01 00:00:00 EDT (-04:00) = 1793505600000L
        assertEquals(1793505600000L, dayStart)
        // 2026-11-02 00:00:00 EST (-05:00) = 1793595600000L
        assertEquals(1793595600000L, nextDayStart)

        val durationMs = nextDayStart - dayStart
        val twentyFiveHoursMs = 25L * 3600L * 1000L
        val twentyFourHoursMs = 24L * 3600L * 1000L

        assertEquals(twentyFiveHoursMs, durationMs)
        assertNotEquals(twentyFourHoursMs, durationMs)
    }

    @Test
    fun testZoneSupportAndValidation() {
        assertTrue(resolver.isZoneSupported("UTC"))
        assertTrue(resolver.isZoneSupported("America/New_York"))
        assertTrue(resolver.isZoneSupported("Europe/London"))
        assertTrue(resolver.isZoneSupported("Asia/Tokyo"))
        assertFalse(resolver.isZoneSupported("Invalid/Fake_Zone_12345"))

        assertFailsWith<IllegalArgumentException> {
            resolver.resolveOffset("Invalid/Zone", "2026-03-08", "12:00")
        }

        assertFailsWith<IllegalArgumentException> {
            resolver.resolveOffset("America/New_York", "invalid-date", "12:00")
        }

        assertFailsWith<IllegalArgumentException> {
            resolver.resolveOffset("America/New_York", "2026-03-08", "invalid-time")
        }

        assertFailsWith<IllegalArgumentException> {
            resolver.resolveOffset("America/New_York", "2026-02-29", "12:00") // 2026 is not a leap year
        }
    }

    @Test
    fun testMidnightGapTransitionHandling() {
        // In America/Sao_Paulo on 2018-11-04, DST spring-forward occurred exactly at midnight (00:00 -> 01:00).
        // 00:00 and 00:30 are non-existent local times in a gap.
        val gapResult00 = resolver.resolveOffset("America/Sao_Paulo", "2018-11-04", "00:00")
        assertTrue(gapResult00 is StrictZoneOffsetResult.Gap)
        assertEquals(-10800L, gapResult00.gapBeforeOffsetSeconds) // -03:00
        assertEquals(-7200L, gapResult00.gapAfterOffsetSeconds)   // -02:00

        val gapResult30 = resolver.resolveOffset("America/Sao_Paulo", "2018-11-04", "00:30")
        assertTrue(gapResult30 is StrictZoneOffsetResult.Gap)

        // Day start must advance past the midnight gap to the earliest valid instant of that calendar date (01:00:00)
        val dayStartMs = resolver.computeDayStartEpochMs("America/Sao_Paulo", "2018-11-04")
        assertEquals(1541300400000L, dayStartMs)

        val firstValidInstantResult = resolver.resolveOffset("America/Sao_Paulo", "2018-11-04", "01:00")
        assertTrue(firstValidInstantResult is StrictZoneOffsetResult.Single)
        assertEquals(dayStartMs, firstValidInstantResult.epochMs)
    }
}
