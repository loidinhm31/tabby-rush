package dev.doggo.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DateTimePickersTest {

    @Test
    fun testDateRoundTripUtc() {
        val testCases = listOf(
            "2026-10-20" to 1792454400000L,
            "1970-01-01" to 0L,
            "1969-12-31" to -86400000L,
            "2028-02-29" to 1835395200000L, // leap day
            "2026-12-31" to 1798675200000L, // year end
            "2027-01-01" to 1798761600000L, // year start
            "1850-06-15" to -3772569600000L, // pre-1900 boundary
            "2150-01-01" to 5680281600000L   // post-2100 boundary
        )
        for ((dateStr, expectedMillis) in testCases) {
            val millis = DateTimePickerFormats.dateToUtcMillisOrNull(dateStr)
            assertNotNull(millis, "Date $dateStr should parse to millis")
            assertEquals(expectedMillis, millis, "Date $dateStr should match expected UTC millis")
            val roundTrip = DateTimePickerFormats.utcMillisToDate(millis)
            assertEquals(dateStr, roundTrip, "Round trip for $dateStr should match original")
        }
    }

    @Test
    fun testInvalidDateStringsRejected() {
        val invalidDates = listOf(
            "",
            "   ",
            "2026-10",
            "2026/10/20",
            "20-10-2026",
            "2026-2-20",
            "2026-02-30", // impossible day
            "2026-02-31", // impossible day
            "2026-04-31", // April has 30 days
            "2025-02-29", // 2025 not a leap year
            "12026-10-20", // 5 digit year
            "26-10-20"     // 2 digit year
        )
        for (invalid in invalidDates) {
            val result = DateTimePickerFormats.dateToUtcMillisOrNull(invalid)
            assertNull(result, "Invalid date '$invalid' must return null")
        }
    }

    @Test
    fun testTimeParsingValidCases() {
        val validTimes = listOf(
            "00:00" to (0 to 0),
            "09:05" to (9 to 5),
            "12:00" to (12 to 0),
            "17:30" to (17 to 30),
            "23:59" to (23 to 59)
        )
        for ((timeStr, expected) in validTimes) {
            val parsed = DateTimePickerFormats.parseTimeOrNull(timeStr)
            assertNotNull(parsed, "Time $timeStr must parse")
            assertEquals(expected.first, parsed.hour)
            assertEquals(expected.second, parsed.minute)
        }
    }

    @Test
    fun testTimeParsingInvalidCases() {
        val invalidTimes = listOf(
            "",
            "   ",
            "9:00",     // missing leading zero
            "09:5",     // missing leading zero in minute
            "24:00",    // hour 24 invalid
            "23:60",    // minute 60 invalid
            "12:00:00", // seconds not allowed
            "12-00",    // bad delimiter
            "noon",
            "12:00 PM"
        )
        for (invalid in invalidTimes) {
            val result = DateTimePickerFormats.parseTimeOrNull(invalid)
            assertNull(result, "Invalid time '$invalid' must return null")
        }
    }

    @Test
    fun testTimeFormattingValidAndBounds() {
        assertEquals("00:00", DateTimePickerFormats.formatTime(0, 0))
        assertEquals("09:05", DateTimePickerFormats.formatTime(9, 5))
        assertEquals("12:00", DateTimePickerFormats.formatTime(12, 0))
        assertEquals("23:59", DateTimePickerFormats.formatTime(23, 59))

        assertFailsWith<IllegalArgumentException> {
            DateTimePickerFormats.formatTime(-1, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            DateTimePickerFormats.formatTime(24, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            DateTimePickerFormats.formatTime(12, -1)
        }
        assertFailsWith<IllegalArgumentException> {
            DateTimePickerFormats.formatTime(12, 60)
        }
    }
}
