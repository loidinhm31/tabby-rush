package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CanonicalRecordRef
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.boundBindingKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OutboxReconciliationTest {
    @Test fun frozenEnvelopeRoundTripRetainsOpaqueValuesAndExplicitNull() {
        val raw = TaskJsonMerge.encode(Todo(TaskRef("u1", "a"), "Title", false, 1L)).toMutableMap().apply {
            put("future", TaskJsonMerge.json.parseToJsonElement("""{"nested":[null,1,{"enabled":true}]}"""))
        }
        val envelope = PushRecordDto(
            rowId = "a",
            data = kotlinx.serialization.json.JsonObject(raw),
            version = 8L,
            assumedServerVersion = 7L,
            ownerUserId = "user-1"
        )
        val serialized = TaskJsonMerge.json.encodeToString(envelope)
        val recovered = TaskJsonMerge.json.decodeFromString<PushRecordDto>(serialized)
        assertEquals(envelope, recovered)
        assertFalse(recovered.deleted)
        assertEquals(7L, recovered.assumedServerVersion)
        assertEquals("user-1", recovered.ownerUserId)
    }

    @Test fun testFifoHeadGatingByCanonicalTuple() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val accountKey = AccountKey.create("http://localhost:3000", "user-alice")
        val store = RoomAccountStore(accountKey, "user-alice", daoOverride = fakeDao)
        val reconciliation = OutboxReconciliation(store)

        val boundAlice = boundBindingKey("user-alice")
        val boundBob = boundBindingKey("user-bob")

        // Alice op 1 (CONFLICT head)
        fakeDao.upsertOutbox(
            OutboxEntity(
                bindingKey = boundAlice,
                ownerUserId = "user-alice",
                tableName = "todos",
                rowId = "task-1",
                opId = "op-alice-1",
                kind = "update",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                completed = false,
                createdAt = 100L,
                createdAtEpochMs = 100L,
                title = "Alice 1"
            )
        )
        // Alice op 2 (PENDING successor - blocked by head op-alice-1)
        fakeDao.upsertOutbox(
            OutboxEntity(
                bindingKey = boundAlice,
                ownerUserId = "user-alice",
                tableName = "todos",
                rowId = "task-1",
                opId = "op-alice-2",
                kind = "update",
                localRevision = 2L,
                deliveryState = OutboxEntity.STATE_PENDING,
                completed = false,
                createdAt = 100L,
                createdAtEpochMs = 100L,
                title = "Alice 2"
            )
        )
        // Bob op 1 (same rowId "task-1", but owner is user-bob - PENDING head, eligible!)
        fakeDao.upsertOutbox(
            OutboxEntity(
                bindingKey = boundBob,
                ownerUserId = "user-bob",
                tableName = "todos",
                rowId = "task-1",
                opId = "op-bob-1",
                kind = "update",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_PENDING,
                completed = false,
                createdAt = 100L,
                createdAtEpochMs = 100L,
                title = "Bob 1"
            )
        )

        val batch = reconciliation.getEligibleDispatchBatch()

        // Alice op 2 is NOT eligible because op-alice-1 head is in CONFLICT
        // Bob op 1 IS eligible because its canonical head is PENDING
        assertEquals(1, batch.size)
        assertEquals("op-bob-1", batch[0].opId)
        assertEquals("user-bob", batch[0].ownerUserId)
    }

    @Test fun testExactTupleAcknowledgementRequiresMatchingOwnerAndVersion() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val accountKey = AccountKey.create("http://localhost:3000", "user-alice")
        val store = RoomAccountStore(accountKey, "user-alice", daoOverride = fakeDao)
        val reconciliation = OutboxReconciliation(store)

        val boundAlice = boundBindingKey("user-alice")
        val opEnvelope = PushRecordDto(
            tableName = "todos",
            rowId = "task-1",
            data = TaskJsonMerge.encode(Todo(TaskRef("user-alice", "task-1"), "Task 1", false, 100L)),
            version = 5L,
            deleted = false,
            assumedServerVersion = 4L,
            ownerUserId = "user-alice"
        )
        val op = OutboxEntity(
            bindingKey = boundAlice,
            ownerUserId = "user-alice",
            tableName = "todos",
            rowId = "task-1",
            opId = "op-1",
            kind = "update",
            localRevision = 1L,
            version = 5L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            completed = false,
            createdAt = 100L,
            createdAtEpochMs = 100L,
            title = "Task 1",
            envelopeJson = syncWireJson.encodeToString(opEnvelope)
        )
        fakeDao.upsertOutbox(op)

        // Response with matching rowId and version, but wrong ownerUserId ("user-bob")
        val wrongOwnerResponse = PushResponseDto(
            synced = 1,
            conflicts = emptyList(),
            syncedRecords = listOf(
                SyncedRecordDto(tableName = "todos", rowId = "task-1", version = 5L, ownerUserId = "user-bob")
            ),
            serverTimestamp = "2026-10-21T10:00:00.000Z",
            schemaVersion = 1L
        )

        reconciliation.reconcilePushResults(listOf(op), wrongOwnerResponse)

        // Op remains NOT acknowledged because owner mismatched
        val entryAfterWrongOwner = fakeDao.getOutboxEntry("op-1")
        assertNotNull(entryAfterWrongOwner)
        assertEquals(OutboxEntity.STATE_UNCERTAIN, entryAfterWrongOwner.deliveryState)

        // Reset to DISPATCHING
        fakeDao.upsertOutbox(op.copy(deliveryState = OutboxEntity.STATE_DISPATCHING))

        // Response with exact matching owner, table, rowId, and submittedVersion
        val exactMatchResponse = PushResponseDto(
            synced = 1,
            conflicts = emptyList(),
            syncedRecords = listOf(
                SyncedRecordDto(tableName = "todos", rowId = "task-1", version = 5L, ownerUserId = "user-alice")
            ),
            serverTimestamp = "2026-10-21T10:00:00.000Z",
            schemaVersion = 1L
        )

        reconciliation.reconcilePushResults(listOf(op), exactMatchResponse)

        val entryAfterExact = fakeDao.getOutboxEntry("op-1")
        assertNotNull(entryAfterExact)
        assertEquals("ACKNOWLEDGED", entryAfterExact.deliveryState)
        assertEquals("EXPLICIT_ACCEPTED_TUPLE", entryAfterExact.resolution)
    }

    @Test fun testOperationForbiddenDiscardsIntentWithoutRevokingTaskAccess() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val accountKey = AccountKey.create("http://localhost:3000", "user-alice")
        val store = RoomAccountStore(accountKey, "user-alice", daoOverride = fakeDao)
        val reconciliation = OutboxReconciliation(store)

        val boundAlice = boundBindingKey("user-alice")
        val op = OutboxEntity(
            bindingKey = boundAlice,
            ownerUserId = "user-alice",
            tableName = "todos",
            rowId = "task-1",
            opId = "op-forbidden",
            kind = "update",
            localRevision = 1L,
            version = 2L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            completed = false,
            createdAt = 100L,
            createdAtEpochMs = 100L,
            title = "Task 1"
        )
        fakeDao.upsertOutbox(op)

        val forbiddenResponse = PushResponseDto(
            synced = 0,
            conflicts = emptyList(),
            failures = listOf(
                FailedRecordDto(tableName = "todos", rowId = "task-1", error = "OPERATION_FORBIDDEN", ownerUserId = "user-alice")
            ),
            serverTimestamp = "2026-10-21T10:00:00.000Z",
            schemaVersion = 1L
        )

        reconciliation.reconcilePushResults(listOf(op), forbiddenResponse)

        val entry = fakeDao.getOutboxEntry("op-forbidden")
        assertNotNull(entry)
        assertEquals("DISCARDED", entry.deliveryState)
        assertEquals("OPERATION_FORBIDDEN", entry.resolution)

        // Read access evidence is NOT written (it is not ACCESS_DENIED)
        assertEquals(0, fakeDao.getAllAccessLossEvidence().size)
    }
}
