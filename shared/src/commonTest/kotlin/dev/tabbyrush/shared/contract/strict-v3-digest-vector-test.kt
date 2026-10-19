package dev.tabbyrush.shared.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StrictV3DigestVectorTest {
    private val appId = "tabby-rush"
    private val userId = "65a123456789abcdef012345"
    private val epoch = "550e8400-e29b-41d4-a716-446655440000"
    private val rowId = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    private val opId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
    private val parentRowId = "e1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c"

    private fun assertVector(
        kind: StrictOperationKind,
        rev: Long,
        data: StrictV3TodoData?,
        len: Int,
        sha: String
    ) {
        val preimage = StrictSyncV3Digest.buildPreimage(
            appId = appId,
            userId = userId,
            epoch = epoch,
            tableName = "todos",
            rowId = rowId,
            opId = opId,
            kind = kind,
            expectedRevision = rev,
            data = data
        )
        assertEquals(len, preimage.size, "Preimage size mismatch")
        assertEquals(sha, StrictSyncDigest.sha256Hex(preimage), "Preimage SHA-256 mismatch")
        assertEquals(
            sha,
            StrictSyncV3Digest.computeDigest(
                appId = appId,
                userId = userId,
                epoch = epoch,
                tableName = "todos",
                rowId = rowId,
                opId = opId,
                kind = kind,
                expectedRevision = rev,
                data = data
            ),
            "Computed digest mismatch"
        )
    }

    @Test
    fun testRootNullParentVector() {
        val data = StrictV3TodoData(
            id = rowId,
            title = "Parent project plan",
            completed = false,
            createdAt = 1791840000000L,
            parentTaskId = null
        )
        assertVector(
            StrictOperationKind.CREATE,
            0L,
            data,
            301,
            "4c793ecfeece95f486f8181abeac360b097ccb791500704d60f0de8b54bad70d"
        )
    }

    @Test
    fun testChildWithParentVector() {
        val data = StrictV3TodoData(
            id = rowId,
            title = "Subtask 1 - Setup CI",
            completed = false,
            createdAt = 1791840000000L,
            parentTaskId = parentRowId
        )
        assertVector(
            StrictOperationKind.CREATE,
            0L,
            data,
            342,
            "058f24106d29c595e8c802452feba78784f21fae8adfc9291239c7435649cc24"
        )
    }

    @Test
    fun testChildWithOwnDeadlineVector() {
        val data = StrictV3TodoData(
            id = rowId,
            title = "Subtask 2 - Draft specs",
            completed = false,
            createdAt = 1791840000000L,
            deadlineDate = "2026-10-31",
            deadlineTime = null,
            deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -14400L,
            reminderAnchorTime = "09:00",
            deadlineAnchorEpochMs = 1793451600000L,
            deadlineOverdueEpochMs = 1793505600000L,
            reminderEnabled = true,
            reminderLeadMinutes = 0L,
            reminderIntervalMinutes = null,
            reminderCount = 1L,
            scheduleToken = "00000000-0000-0000-0000-000000000001",
            parentTaskId = parentRowId
        )
        assertVector(
            StrictOperationKind.UPDATE,
            1L,
            data,
            452,
            "7e2e5d1f6efce37ccbe56a20f7bc51ce89f398b370fd09b4a1b37ebe981ac1c3"
        )
    }

    @Test
    fun testChildTimedDeadlineDstVector() {
        val data = StrictV3TodoData(
            id = rowId,
            title = "Subtask 3 - Review code before cutover",
            completed = false,
            createdAt = 1791840000000L,
            deadlineDate = "2026-11-01",
            deadlineTime = "01:30",
            deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -18000L,
            reminderAnchorTime = null,
            deadlineAnchorEpochMs = 1793514600000L,
            deadlineOverdueEpochMs = 1793514600000L,
            reminderEnabled = false,
            reminderLeadMinutes = 15L,
            reminderIntervalMinutes = null,
            reminderCount = 1L,
            scheduleToken = "00000000-0000-0000-0000-000000000002",
            parentTaskId = parentRowId
        )
        assertVector(
            StrictOperationKind.UPDATE,
            2L,
            data,
            467,
            "92840e47ce241d2b36531e34f84f3e8a5627ce724937f877d8436ea54f254229"
        )
    }

    @Test
    fun testChildCompletedUpdateVector() {
        val data = StrictV3TodoData(
            id = rowId,
            title = "Subtask 1 - Setup CI",
            completed = true,
            createdAt = 1791840000000L,
            parentTaskId = parentRowId
        )
        assertVector(
            StrictOperationKind.UPDATE,
            3L,
            data,
            342,
            "1d947048db22ebc8902ad5fa3b43036a2ce33fc05dedc14b6c60dbc4dc2d320b"
        )
    }

    @Test
    fun testDeleteOperationEmptyDataVector() {
        assertVector(
            StrictOperationKind.DELETE,
            4L,
            null,
            202,
            "8f9803b40a59069f729dbe6fa18b61e7cc81f8ae3b33b9978124ae8da5761ec3"
        )
    }

    @Test
    fun testSelfReferentialParentThrows() {
        val data = StrictV3TodoData(
            id = rowId,
            title = "Self parent",
            completed = false,
            createdAt = 1791840000000L,
            parentTaskId = rowId
        )
        assertFailsWith<IllegalArgumentException> {
            data.validate()
        }
    }

    @Test
    fun testBlankParentThrows() {
        val data = StrictV3TodoData(
            id = rowId,
            title = "Blank parent",
            completed = false,
            createdAt = 1791840000000L,
            parentTaskId = "   "
        )
        assertFailsWith<IllegalArgumentException> {
            data.validate()
        }
    }

    @Test
    fun testDeleteWithDataThrows() {
        val data = StrictV3TodoData(
            id = rowId,
            title = "Invalid delete payload",
            completed = false,
            createdAt = 1791840000000L,
            parentTaskId = null
        )
        assertFailsWith<IllegalArgumentException> {
            StrictSyncV3Digest.buildPreimage(
                appId = appId,
                userId = userId,
                epoch = epoch,
                tableName = "todos",
                rowId = rowId,
                opId = opId,
                kind = StrictOperationKind.DELETE,
                expectedRevision = 1L,
                data = data
            )
        }
    }

    @Test
    fun testNegativeRevisionThrows() {
        assertFailsWith<IllegalArgumentException> {
            StrictSyncV3Digest.buildPreimage(
                appId = appId,
                userId = userId,
                epoch = epoch,
                tableName = "todos",
                rowId = rowId,
                opId = opId,
                kind = StrictOperationKind.DELETE,
                expectedRevision = -1L,
                data = null
            )
        }
    }
}
