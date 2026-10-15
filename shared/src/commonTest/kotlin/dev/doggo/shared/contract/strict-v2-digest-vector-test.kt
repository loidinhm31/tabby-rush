package dev.doggo.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals

class StrictV2DigestVectorTest {
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
    fun testMigratedNullDefaultVector() {
        val data = StrictV2TodoData(id = rowId, title = "Buy groceries", completed = false, createdAt = 1791840000000L)
        assertVector(StrictOperationKind.CREATE, 0L, data, 289, "e1f470d22f00df17e612633fdae5d41de5fccc3fc310808651764267c1a3a4d6")
    }

    @Test
    fun testDateOnlyDeadlineVector() {
        val data = StrictV2TodoData(
            id = rowId, title = "File quarterly taxes", completed = false, createdAt = 1791840000000L,
            deadlineDate = "2026-10-31", deadlineTime = null, deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -14400L, reminderAnchorTime = "09:00",
            deadlineAnchorEpochMs = 1793451600000L, deadlineOverdueEpochMs = 1793505600000L,
            reminderEnabled = true, reminderLeadMinutes = 0L, reminderIntervalMinutes = null,
            reminderCount = 1L, scheduleToken = "00000000-0000-0000-0000-000000000001"
        )
        assertVector(StrictOperationKind.UPDATE, 1L, data, 403, "c996757cf633c2cecc82c7d5d71ad65aaa253d9f092aeff42ab761fa6b0d9488")
    }

    @Test
    fun testTimedDstOverlapVector() {
        val data = StrictV2TodoData(
            id = rowId, title = "Fall back shift review", completed = false, createdAt = 1791840000000L,
            deadlineDate = "2026-11-01", deadlineTime = "01:30", deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -18000L, reminderAnchorTime = null,
            deadlineAnchorEpochMs = 1793514600000L, deadlineOverdueEpochMs = 1793514600000L,
            reminderEnabled = false, reminderLeadMinutes = 15L, reminderIntervalMinutes = null,
            reminderCount = 1L, scheduleToken = "00000000-0000-0000-0000-000000000002"
        )
        assertVector(StrictOperationKind.UPDATE, 2L, data, 405, "72e44db01ebb8802362a321a4c393d78ae2a4e47389ce85a16362b3b39535544")
    }

    @Test
    fun testRepeatingCount10Vector() {
        val data = StrictV2TodoData(
            id = rowId, title = "Submit urgent report", completed = false, createdAt = 1791840000000L,
            deadlineDate = "2026-10-20", deadlineTime = "17:00", deadlineZone = "Asia/Saigon",
            deadlineOffsetSeconds = 25200L, reminderAnchorTime = null,
            deadlineAnchorEpochMs = 1792490400000L, deadlineOverdueEpochMs = 1792490400000L,
            reminderEnabled = true, reminderLeadMinutes = 135L, reminderIntervalMinutes = 15L,
            reminderCount = 10L, scheduleToken = "00000000-0000-0000-0000-000000000003"
        )
        assertVector(StrictOperationKind.UPDATE, 3L, data, 406, "1403d20a53aa2e5c18e2f50319842b8ce62e021d663bcd573d37af1edb0c1988")
    }

    @Test
    fun testDisabledRuleWithDeadlineVector() {
        val data = StrictV2TodoData(
            id = rowId, title = "Optional review", completed = false, createdAt = 1791840000000L,
            deadlineDate = "2026-10-25", deadlineTime = "10:00", deadlineZone = "UTC",
            deadlineOffsetSeconds = 0L, reminderAnchorTime = null,
            deadlineAnchorEpochMs = 1792836000000L, deadlineOverdueEpochMs = 1792836000000L,
            reminderEnabled = false, reminderLeadMinutes = 30L, reminderIntervalMinutes = 15L,
            reminderCount = 2L, scheduleToken = "00000000-0000-0000-0000-000000000004"
        )
        assertVector(StrictOperationKind.UPDATE, 4L, data, 393, "08e9a135a70d9d7b787aaa23c8ac355c341c6797a6826b0037dd49739f979de4")
    }
}
