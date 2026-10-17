package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StrictV2DigestExtendedTest {
    private val appId = "tabby-rush"
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
        assertVector(StrictOperationKind.UPDATE, 5L, data, 297, "4f786c5a094a59e10e1d0a477dda9d9c3a4032e0de4b5e015abc3526fa1c1567")
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
        assertVector(StrictOperationKind.UPDATE, 6L, data, 432, "d923fb0090c1b7da548ca5a28eae981fba2bf6d7fbf68676ef9ce13c5b3797ab")
    }

    @Test
    fun testDeleteOperationZeroDataBytesVector() {
        assertVector(StrictOperationKind.DELETE, 7L, null, 202, "1ac67eed08f0b7d04b6d974e226c954b886abc984aeabfff984a61347a3ab05a")
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
        assertVector(StrictOperationKind.UPDATE, 8L, data, 406, "fe03f3691142bd315f862a11e0a766a29afe882bf067ec0b779905dd370a5775")
    }
    @Test
    fun testMaxI64ExpectedRevisionVector() {
        val data = StrictV2TodoData(
            id = rowId, title = "Max revision task", completed = false, createdAt = 1791840000000L
        )
        assertVector(StrictOperationKind.UPDATE, Long.MAX_VALUE, data, 298, "685eef66169071b79bb640d943a62069910ce4ac471c18c0db69051398f9b20b")
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
