package dev.doggo.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StrictV2DigestExtendedTest {
    private val appId = "doggo"
    private val userId = "65a123456789abcdef012345"
    private val epoch = "550e8400-e29b-41d4-a716-446655440000"
    private val rowId = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    private val opId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"

    private fun assertVector(kind: StrictOperationKind, rev: Long, data: StrictV2TodoData?, len: Int, sha: String) {
        val preimage = StrictSyncV2Digest.buildPreimage(
            appId = appId, userId = userId, epoch = epoch, tableName = "todos",
            rowId = rowId, opId = opId, kind = kind, expectedRevision = rev, data = data
        )
        assertEquals(len, preimage.size)
        assertEquals(sha, StrictSyncDigest.sha256Hex(preimage))
        assertEquals(sha, StrictSyncV2Digest.computeDigest(
            appId = appId, userId = userId, epoch = epoch, tableName = "todos",
            rowId = rowId, opId = opId, kind = kind, expectedRevision = rev, data = data
        ))
    }

    @Test
    fun testClearedDeadlineVector() {
        val data = StrictV2TodoData(
            id = rowId, title = "No deadline task", completed = true, createdAt = 1791840000000L
        )
        assertVector(StrictOperationKind.UPDATE, 5L, data, 292, "e6b9a0c6bd2604512faaee0c16f883a40a6351e14c871b6276d810c6615ca180")
    }

    @Test
    fun testUnicodeTitleAndZoneVector() {
        val data = StrictV2TodoData(
            id = rowId, title = "Lập kế hoạch dự án 🎯 日本語", completed = false, createdAt = 1791840000000L,
            deadlineDate = "2026-12-31", deadlineTime = "23:59", deadlineZone = "Asia/Tokyo",
            deadlineOffsetSeconds = 32400L, reminderAnchorTime = null,
            deadlineAnchorEpochMs = 1798729140000L, deadlineOverdueEpochMs = 1798729140000L,
            reminderEnabled = true, reminderLeadMinutes = 60L, reminderIntervalMinutes = 30L,
            reminderCount = 3L, scheduleToken = "00000000-0000-0000-0000-000000000005"
        )
        assertVector(StrictOperationKind.UPDATE, 6L, data, 427, "ca330ee1a20a9ed2d1f99af7a472c6ca4c94e1d0efd3fcd031880b263f081da9")
    }

    @Test
    fun testDeleteOperationZeroDataBytesVector() {
        assertVector(StrictOperationKind.DELETE, 7L, null, 197, "edfa0f80dac03a1ccc1be8d64d52bf0f7629cfd9bb804c7327056d30cc0e5dfa")
    }

    @Test
    fun testNegativeEpochInstantVector() {
        val data = StrictV2TodoData(
            id = rowId, title = "Apollo 11 milestone anniversary", completed = true, createdAt = 1000L,
            deadlineDate = "1969-07-20", deadlineTime = "20:17", deadlineZone = "UTC",
            deadlineOffsetSeconds = 0L, reminderAnchorTime = null,
            deadlineAnchorEpochMs = -14182980000L, deadlineOverdueEpochMs = -14182980000L,
            reminderEnabled = false, reminderLeadMinutes = 0L, reminderIntervalMinutes = null,
            reminderCount = 1L, scheduleToken = "00000000-0000-0000-0000-000000000006"
        )
        assertVector(StrictOperationKind.UPDATE, 8L, data, 401, "4046cb4f57e701fa802f28f384c532a6db99a8f69802556787f76b5a0ed2d3ac")
    }
    @Test
    fun testMaxI64ExpectedRevisionVector() {
        val data = StrictV2TodoData(
            id = rowId, title = "Max revision task", completed = false, createdAt = 1791840000000L
        )
        assertVector(StrictOperationKind.UPDATE, Long.MAX_VALUE, data, 293, "60afc0df6b5842b7803ac67e90c69e8f1aa3da430749ce933f3c279372d8cc8c")
    }


    @Test
    fun testValidationRejections() {
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L, reminderCount = 0L).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L, reminderCount = 11L).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L, reminderCount = 2L, reminderIntervalMinutes = 14L).validate()
        }
        // Negative expectedRevision rejected
        assertFailsWith<IllegalArgumentException> {
            StrictSyncV2Digest.buildPreimage(
                appId = appId, userId = userId, epoch = epoch, tableName = "todos",
                rowId = rowId, opId = opId, kind = StrictOperationKind.UPDATE, expectedRevision = -1L,
                data = StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L, reminderCount = 1L, reminderIntervalMinutes = 15L).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L, reminderLeadMinutes = -1L).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(
                id = "x", title = "y", completed = false, createdAt = 0L,
                deadlineDate = "2026-10-20", deadlineTime = "12:00", deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L, deadlineAnchorEpochMs = 1000L, deadlineOverdueEpochMs = 1000L,
                scheduleToken = "t", reminderEnabled = true, reminderLeadMinutes = 10L,
                reminderIntervalMinutes = 15L, reminderCount = 2L
            ).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = -1L).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L, reminderEnabled = true).validate()
        }
        // Timed deadline with mismatched overdue and anchor
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(
                id = "x", title = "y", completed = false, createdAt = 0L,
                deadlineDate = "2026-10-20", deadlineTime = "12:00", deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L, deadlineAnchorEpochMs = 1000L, deadlineOverdueEpochMs = 2000L,
                scheduleToken = "t", reminderEnabled = false
            ).validate()
        }
        // Lead minutes multiplication overflow
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(
                id = "x", title = "y", completed = false, createdAt = 0L,
                deadlineDate = "2026-10-20", deadlineTime = "12:00", deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L, deadlineAnchorEpochMs = 1000L, deadlineOverdueEpochMs = 1000L,
                scheduleToken = "t", reminderEnabled = false, reminderLeadMinutes = Long.MAX_VALUE
            ).validate()
        }
        // Anchor subtraction overflow
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(
                id = "x", title = "y", completed = false, createdAt = 0L,
                deadlineDate = "2026-10-20", deadlineTime = "12:00", deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L, deadlineAnchorEpochMs = Long.MIN_VALUE, deadlineOverdueEpochMs = Long.MIN_VALUE,
                scheduleToken = "t", reminderEnabled = false, reminderLeadMinutes = 1L
            ).validate()
        }
        // Interval multiplication overflow
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(
                id = "x", title = "y", completed = false, createdAt = 0L,
                deadlineDate = "2026-10-20", deadlineTime = "12:00", deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L, deadlineAnchorEpochMs = 1000L, deadlineOverdueEpochMs = 1000L,
                scheduleToken = "t", reminderEnabled = false, reminderCount = 2L,
                reminderIntervalMinutes = Long.MAX_VALUE
            ).validate()
        // Ninefold repeat offset multiplication overflow
        assertFailsWith<IllegalArgumentException> {
            val safeIntervalMinutes = (Long.MAX_VALUE / 60_000L) / 2L
            StrictV2TodoData(
                id = "x", title = "y", completed = false, createdAt = 0L,
                deadlineDate = "2026-10-20", deadlineTime = "12:00", deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L, deadlineAnchorEpochMs = 1000L, deadlineOverdueEpochMs = 1000L,
                scheduleToken = "t", reminderEnabled = false, reminderCount = 10L,
                reminderIntervalMinutes = safeIntervalMinutes
            ).validate()
        }
        // Final addition overflow (first + lastOffset exceeds Long.MAX_VALUE)
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(
                id = "x", title = "y", completed = false, createdAt = 0L,
                deadlineDate = "2026-10-20", deadlineTime = "12:00", deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L, deadlineAnchorEpochMs = Long.MAX_VALUE, deadlineOverdueEpochMs = Long.MAX_VALUE,
                scheduleToken = "t", reminderEnabled = false, reminderLeadMinutes = 0L,
                reminderIntervalMinutes = 15L, reminderCount = 2L
            ).validate()
        }
        // Disabled rule with invalid timing (last > anchor)
            StrictV2TodoData(
                id = "x", title = "y", completed = false, createdAt = 0L,
                deadlineDate = "2026-10-20", deadlineTime = "12:00", deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L, deadlineAnchorEpochMs = 1000L, deadlineOverdueEpochMs = 1000L,
                scheduleToken = "t", reminderEnabled = false, reminderLeadMinutes = 10L,
                reminderIntervalMinutes = 15L, reminderCount = 2L
            ).validate()
        }
        // Non-default reminder preferences without deadline
        assertFailsWith<IllegalArgumentException> {
            StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L, reminderLeadMinutes = 5L).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StrictSyncV2Digest.buildPreimage(
                appId = appId, userId = userId, epoch = epoch, tableName = "todos",
                rowId = rowId, opId = opId, kind = StrictOperationKind.DELETE, expectedRevision = 1L,
                data = StrictV2TodoData(id = "x", title = "y", completed = false, createdAt = 0L)
            )
        }
    }
}
