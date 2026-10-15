package dev.doggo.shared.sync

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.StrictOperationStatus
import dev.doggo.shared.contract.StrictSyncV2Digest
import dev.doggo.shared.contract.StrictSyncV2Contract
import dev.doggo.shared.contract.StrictV2TodoData
import dev.doggo.shared.data.EditorDraftEntity
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
class OutboxReconciliationTest {

    private val accountKey = AccountKey.create("http://localhost:3000", "user-123")

    private fun createStore(): Pair<RoomAccountStore, FakeDoggoDao> {
        val fakeDao = FakeDoggoDao()
        val store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-123",
            database = null,
            appId = "doggo",
            daoOverride = fakeDao
        )
        return Pair(store, fakeDao)
    }

    @Test
    fun testEligibleBatchFifoOrder() = runTest {
        val (store, dao) = createStore()

        // Two operations for row-1 (op-1 at t=1000, op-2 at t=2000)
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-1",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-1",
                kind = "create",
                expectedRevision = 0L,
                title = "Row 1 V1",
                completed = false,
                createdAt = 1000L,
                digest = "digest-1",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1000L
            )
        )
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-2",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-1",
                kind = "update",
                expectedRevision = 1L,
                title = "Row 1 V2",
                completed = true,
                createdAt = 1000L,
                digest = "digest-2",
                localRevision = 2L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 2000L
            )
        )

        // One operation for row-2
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-3",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-2",
                kind = "create",
                expectedRevision = 0L,
                title = "Row 2 V1",
                completed = false,
                createdAt = 1500L,
                digest = "digest-3",
                localRevision = 3L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1500L
            )
        )

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val batch = reconciliation.getEligibleDispatchBatch()

        // Must contain earliest for row-1 (op-1) and row-2 (op-3). op-2 must be excluded!
        assertEquals(2, batch.size)
        val opIds = batch.map { it.opId }.toSet()
        assertEquals(setOf("op-1", "op-3"), opIds)
    }

    @Test
    fun testExactCasAckAdvancesBaseAndRemovesOutbox() = runTest {
        val (store, dao) = createStore()

        val op = OutboxEntity(
            opId = "op-1",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            kind = "create",
            expectedRevision = 0L,
            title = "Created Todo",
            completed = false,
            createdAt = 1000L,
            digest = "digest-1",
            localRevision = 1L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            createdAtEpochMs = 1000L
        )
        dao.upsertOutbox(op)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val response = StrictPushResponseDto(
            protocol = "strict-v1",
            epoch = "epoch-1",
            results = listOf(
                StrictOperationResultDto(
                    opId = "op-1",
                    rowId = "row-1",
                    digest = "digest-1",
                    status = StrictOperationStatus.ACCEPTED.wireName,
                    acceptedRevision = 1L
                )
            )
        )

        reconciliation.reconcilePushResults(listOf(op), response)

        // Outbox entry removed
        assertNull(dao.getOutboxEntry("op-1"))

        // Server base updated
        val base = dao.getServerBase("row-1")
        assertNotNull(base)
        assertEquals(1L, base.serverRevision)
        assertEquals("Created Todo", base.title)
    }

    @Test
    fun testAckPreservesNewerDraft() = runTest {
        val (store, dao) = createStore()

        val op = OutboxEntity(
            opId = "op-1",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            kind = "create",
            expectedRevision = 0L,
            title = "First Title",
            completed = false,
            createdAt = 1000L,
            digest = "digest-1",
            localRevision = 10L, // Captured localRevision = 10
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            createdAtEpochMs = 1000L
        )
        dao.upsertOutbox(op)

        // User edited locally after dispatch, creating draft at localRevision = 11
        dao.upsertDraft(
            EditorDraftEntity(
                rowId = "row-1",
                draftTitle = "Newer Local Draft",
                draftCompleted = true,
                localRevision = 11L,
                updatedAt = 2000L,
                isDeleted = false
            )
        )

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val response = StrictPushResponseDto(
            protocol = "strict-v1",
            epoch = "epoch-1",
            results = listOf(
                StrictOperationResultDto(
                    opId = "op-1",
                    rowId = "row-1",
                    digest = "digest-1",
                    status = StrictOperationStatus.ACCEPTED.wireName,
                    acceptedRevision = 1L
                )
            )
        )

        reconciliation.reconcilePushResults(listOf(op), response)

        // Outbox operation acknowledged
        assertNull(dao.getOutboxEntry("op-1"))

        // Base updated to First Title
        val base = dao.getServerBase("row-1")
        assertNotNull(base)
        assertEquals(1L, base.serverRevision)

        // But newer draft at localRevision = 11 is RETAINED!
        val draft = dao.getDraft("row-1")
        assertNotNull(draft)
        assertEquals("Newer Local Draft", draft.draftTitle)
        assertEquals(11L, draft.localRevision)
    }

    @Test
    fun testUncertainDeliveryConvertsSubsequentConflictToUnknownSuperseded() = runTest {
        val (store, dao) = createStore()

        // An operation that was marked UNCERTAIN after a prior round
        val op = OutboxEntity(
            opId = "op-uncertain",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            kind = "update",
            expectedRevision = 1L,
            title = "Maybe Committed",
            completed = true,
            createdAt = 1000L,
            digest = "digest-u",
            localRevision = 5L,
            deliveryState = OutboxEntity.STATE_UNCERTAIN, // Previously uncertain
            createdAtEpochMs = 1000L
        )
        dao.upsertOutbox(op)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val response = StrictPushResponseDto(
            protocol = "strict-v1",
            epoch = "epoch-1",
            results = listOf(
                StrictOperationResultDto(
                    opId = "op-uncertain",
                    rowId = "row-1",
                    digest = "digest-u",
                    status = StrictOperationStatus.REVISION_CONFLICT.wireName,
                    acceptedRevision = null
                )
            )
        )

        reconciliation.reconcilePushResults(listOf(op), response)

        // Critical Invariant: Apparent conflict after UNCERTAIN becomes UNKNOWN_SUPERSEDED
        val updated = dao.getOutboxEntry("op-uncertain")
        assertNotNull(updated)
        assertEquals(OutboxEntity.STATE_UNKNOWN_SUPERSEDED, updated.deliveryState)
    }

    @Test
    fun testStandardConflictPreservesIntentForReview() = runTest {
        val (store, dao) = createStore()

        val op = OutboxEntity(
            opId = "op-pending",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            kind = "update",
            expectedRevision = 1L,
            title = "Conflicted Edit",
            completed = true,
            createdAt = 1000L,
            digest = "digest-p",
            localRevision = 5L,
            deliveryState = OutboxEntity.STATE_PENDING, // Fresh pending op
            createdAtEpochMs = 1000L
        )
        dao.upsertOutbox(op)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val response = StrictPushResponseDto(
            protocol = "strict-v1",
            epoch = "epoch-1",
            results = listOf(
                StrictOperationResultDto(
                    opId = "op-pending",
                    rowId = "row-1",
                    digest = "digest-p",
                    status = StrictOperationStatus.REVISION_CONFLICT.wireName,
                    acceptedRevision = null
                )
            )
        )

        reconciliation.reconcilePushResults(listOf(op), response)

        val updated = dao.getOutboxEntry("op-pending")
        assertNotNull(updated)
        assertEquals(OutboxEntity.STATE_CONFLICT, updated.deliveryState)
    }

    @Test
    fun testRetryableFailureIncrementsFailuresAndRetainsUncertain() = runTest {
        val (store, dao) = createStore()

        val op = OutboxEntity(
            opId = "op-retry",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            kind = "create",
            expectedRevision = 0L,
            title = "Retryable",
            completed = false,
            createdAt = 1000L,
            digest = "digest-r",
            localRevision = 1L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            createdAtEpochMs = 1000L,
            consecutiveFailures = 1
        )
        dao.upsertOutbox(op)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val response = StrictPushResponseDto(
            protocol = "strict-v1",
            epoch = "epoch-1",
            results = listOf(
                StrictOperationResultDto(
                    opId = "op-retry",
                    rowId = "row-1",
                    digest = "digest-r",
                    status = StrictOperationStatus.RETRYABLE_FAILURE.wireName,
                    acceptedRevision = null
                )
            )
        )

        reconciliation.reconcilePushResults(listOf(op), response)

        val updated = dao.getOutboxEntry("op-retry")
        assertNotNull(updated)
        assertEquals(OutboxEntity.STATE_UNCERTAIN, updated.deliveryState)
        assertEquals(2, updated.consecutiveFailures)
    }

    @Test
    fun testSuccessorRevisionFrozenAtDispatchAgainstCurrentBase() = runTest {
        val (store, dao) = createStore()

        // Base is at revision 1 (e.g. from prior ack)
        dao.upsertServerBase(
            dev.doggo.shared.data.ServerBaseEntity(
                rowId = "row-1",
                serverRevision = 1L,
                deleted = false,
                title = "Initial",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1"
            )
        )

        // Successor was enqueued as pending edit
        val op = OutboxEntity(
            opId = "op-succ",
            epoch = "",
            tableName = "todos",
            rowId = "row-1",
            kind = "update",
            expectedRevision = 0L, // initially had old/default 0
            title = "Successor Title",
            completed = true,
            createdAt = 1000L,
            digest = "",
            localRevision = 2L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 2000L
        )
        dao.upsertOutbox(op)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val frozenBatch = reconciliation.prepareAndFreezeBatch(listOf(op))

        // Invariant C2: expectedRevision is frozen at dispatch against current base (1L)!
        assertEquals(1, frozenBatch.size)
        val frozen = frozenBatch[0]
        assertEquals(1L, frozen.expectedRevision)
        assertEquals("update", frozen.kind)
        assertEquals("epoch-1", frozen.epoch)
        assertEquals(OutboxEntity.STATE_DISPATCHING, frozen.deliveryState)
        assertTrue(frozen.digest.isNotBlank())
    }

    @Test
    fun testFifoHeadConflictBlocksSuccessorDispatch() = runTest {
        val (store, dao) = createStore()

        // Head op is CONFLICT
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-head-conflict",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-1",
                kind = "update",
                expectedRevision = 1L,
                title = "Conflict Head",
                completed = false,
                createdAt = 1000L,
                digest = "d-head",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                createdAtEpochMs = 1000L
            )
        )

        // Successor op is PENDING
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-successor",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-1",
                kind = "update",
                expectedRevision = 2L,
                title = "Successor",
                completed = true,
                createdAt = 1000L,
                digest = "d-succ",
                localRevision = 2L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 2000L
            )
        )

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val eligible = reconciliation.getEligibleDispatchBatch()

        // Invariant C3: row is blocked by conflict head; successor MUST NOT dispatch!
        assertEquals(0, eligible.size)
    }

    @Test
    fun testUncertainOpRetainsExactFrozenIdentityOnRetry() = runTest {
        val (store, dao) = createStore()

        // Base has moved ahead to revision 1 (e.g. from scan)
        dao.upsertServerBase(
            dev.doggo.shared.data.ServerBaseEntity(
                rowId = "row-1",
                serverRevision = 1L,
                deleted = false,
                title = "Server Advanced",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1"
            )
        )

        // Op was previously frozen as CREATE exp=0, but timed out into UNCERTAIN
        val op = OutboxEntity(
            opId = "op-unc",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            kind = "create",
            expectedRevision = 0L,
            title = "My Create",
            completed = false,
            createdAt = 1000L,
            digest = "frozen-digest-123",
            localRevision = 1L,
            deliveryState = OutboxEntity.STATE_UNCERTAIN,
            consecutiveFailures = 1,
            createdAtEpochMs = 1000L
        )
        dao.upsertOutbox(op)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val frozenBatch = reconciliation.prepareAndFreezeBatch(listOf(op))

        // Invariant N1: Retrying UNCERTAIN op MUST NOT re-freeze against new base!
        assertEquals(1, frozenBatch.size)
        val retried = frozenBatch[0]
        assertEquals("create", retried.kind)
        assertEquals(0L, retried.expectedRevision)
        assertEquals("frozen-digest-123", retried.digest)
        assertEquals(OutboxEntity.STATE_DISPATCHING, retried.deliveryState)

        // And if server returns conflict, it becomes UNKNOWN_SUPERSEDED (N1 part 2)
        val response = StrictPushResponseDto(
            protocol = "strict-v1",
            epoch = "epoch-1",
            results = listOf(
                StrictOperationResultDto(
                    opId = "op-unc",
                    rowId = "row-1",
                    digest = "frozen-digest-123",
                    status = StrictOperationStatus.REVISION_CONFLICT.wireName,
                    acceptedRevision = null
                )
            )
        )
        reconciliation.reconcilePushResults(frozenBatch, response)
        val updated = dao.getOutboxEntry("op-unc")
        assertNotNull(updated)
        assertEquals(OutboxEntity.STATE_UNKNOWN_SUPERSEDED, updated.deliveryState)
    }

    @Test
    fun testOptimisticConcurrencyCheckSkipsStaleFreezeOnConcurrentEdit() = runTest {
        val (store, dao) = createStore()

        val op = OutboxEntity(
            opId = "op-race",
            epoch = "",
            tableName = "todos",
            rowId = "row-1",
            kind = "create",
            expectedRevision = 0L,
            title = "Original",
            completed = false,
            createdAt = 1000L,
            digest = "",
            localRevision = 1L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 1000L
        )
        dao.upsertOutbox(op)

        // Eligible batch was selected at localRevision = 1
        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val eligible = listOf(op)

        // Concurrently, user edited the row, bumping localRevision to 2
        dao.upsertOutbox(op.copy(title = "Edited", localRevision = 2L))

        // Now prepareAndFreezeBatch runs for the old snapshot
        val frozenBatch = reconciliation.prepareAndFreezeBatch(eligible)

        // Invariant N2: Stale op is rejected by freezeOutboxAtomic and not pushed!
        assertEquals(0, frozenBatch.size)
        val inDb = dao.getOutboxEntry("op-race")
        assertNotNull(inDb)
        assertEquals("Edited", inDb.title)
        assertEquals(2L, inDb.localRevision)
        assertEquals(OutboxEntity.STATE_PENDING, inDb.deliveryState)
    }

    @Test
    fun testV2OperationFreezeComputesV2DigestAndPreservesTemporalFields() = runTest {
        val (store, dao) = createStore()

        val v2Op = OutboxEntity(
            opId = "v2-op-freeze",
            epoch = "",
            tableName = "todos",
            rowId = "v2-row-1",
            kind = "create",
            expectedRevision = 0L,
            title = "V2 Task",
            completed = false,
            createdAt = 1000L,
            digest = "",
            localRevision = 1L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 1000L,
            deadlineDate = "2026-11-01",
            deadlineTime = "14:00",
            deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -18000L,
            deadlineAnchorEpochMs = 1793559600000L,
            deadlineOverdueEpochMs = 1793559600000L,
            reminderEnabled = true,
            reminderLeadMinutes = 15L,
            reminderCount = 1L,
            scheduleToken = "tok-freeze-v2",
            contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
            intentMask = "${OutboxEntity.INTENT_TITLE}|${OutboxEntity.INTENT_DEADLINE}"
        )
        dao.upsertOutbox(v2Op)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-v2")
        val frozen = reconciliation.prepareAndFreezeBatch(listOf(v2Op))

        assertEquals(1, frozen.size)
        val op = frozen[0]
        assertEquals("epoch-v2", op.epoch)
        assertEquals(OutboxEntity.STATE_DISPATCHING, op.deliveryState)
        assertNotNull(op.dispatchHistory)

        val expectedDigest = StrictSyncV2Digest.computeDigest(
            domain = StrictSyncV2Contract.PROTOCOL_VERSION,
            appId = "doggo",
            userId = "user-123",
            epoch = "epoch-v2",
            tableName = "todos",
            rowId = "v2-row-1",
            opId = "v2-op-freeze",
            kind = StrictOperationKind.CREATE,
            expectedRevision = 0L,
            data = StrictV2TodoData(
                id = "v2-row-1",
                title = "V2 Task",
                completed = false,
                createdAt = 1000L,
                deadlineDate = "2026-11-01",
                deadlineTime = "14:00",
                deadlineZone = "America/New_York",
                deadlineOffsetSeconds = -18000L,
                deadlineAnchorEpochMs = 1793559600000L,
                deadlineOverdueEpochMs = 1793559600000L,
                reminderEnabled = true,
                reminderLeadMinutes = 15L,
                reminderCount = 1L,
                scheduleToken = "tok-freeze-v2"
            )
        )
        assertEquals(expectedDigest, op.digest)

        // Verify wire DTO mapping
        val dto = reconciliation.toV2Dto(op)
        assertEquals("v2-op-freeze", dto.opId)
        assertEquals("2026-11-01", dto.data?.deadlineDate)
        assertEquals("14:00", dto.data?.deadlineTime)
        assertEquals("tok-freeze-v2", dto.data?.scheduleToken)
    }

    @Test
    fun testDispatchedUncertainOpRetainsUncertaintyMarkerAcrossPreparation() = runTest {
        val (store, dao) = createStore()

        val op = OutboxEntity(
            opId = "op-uncertain-marker",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-marker",
            kind = "update",
            expectedRevision = 2L,
            title = "Uncertain Op",
            completed = false,
            createdAt = 1000L,
            digest = "frozen-digest",
            localRevision = 1L,
            deliveryState = OutboxEntity.STATE_UNCERTAIN,
            createdAtEpochMs = 1000L,
            dispatchHistory = """{"dispatched":true,"uncertain":true}"""
        )
        dao.upsertOutbox(op)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val frozen = reconciliation.prepareAndFreezeBatch(listOf(op))
        assertEquals(1, frozen.size)
        assertEquals(OutboxEntity.STATE_DISPATCHING, frozen[0].deliveryState)
        assertNotNull(frozen[0].dispatchHistory)

        // When push returns revisionConflict, it must become UNKNOWN_SUPERSEDED because of prior uncertainty
        val response = StrictPushResponseDto(
            protocol = "strict-v1",
            epoch = "epoch-1",
            results = listOf(
                StrictOperationResultDto(
                    opId = "op-uncertain-marker",
                    rowId = "row-marker",
                    digest = "frozen-digest",
                    status = StrictOperationStatus.REVISION_CONFLICT.wireName,
                    acceptedRevision = null
                )
            )
        )
        reconciliation.reconcilePushResults(frozen, response)
        val updated = dao.getOutboxEntry("op-uncertain-marker")
        assertNotNull(updated)
        assertEquals(OutboxEntity.STATE_UNKNOWN_SUPERSEDED, updated.deliveryState)
    }
}
