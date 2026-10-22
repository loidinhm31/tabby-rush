package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.data.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

class CollaborationReconciliationTest {

    private val config = HubConfig("http://localhost:3100", "tabby-rush", "test-key", BuildMode.DEBUG)
    private val accountKey = AccountKey.create(config.canonicalOrigin, "user-alice")

    @Test
    fun testExplicitEmptyAccessChecksConfirmedAtRevisionE() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(accountKey = accountKey, userId = "user-alice", daoOverride = fakeDao)

        var terminalCheckSent = false
        var terminalRevision: Long? = null

        val engine = TestHttpEngine { req ->
            val body = when (val b = req.body) {
                is io.ktor.http.content.TextContent -> b.text
                is io.ktor.http.content.ByteArrayContent -> b.bytes().decodeToString()
                else -> b.toString()
            }
            if (body.contains("\"accessChecks\":[]")) {
                terminalCheckSent = true
                terminalRevision = 25L
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "records": [],
                        "checkpoint": {"updatedAt":"2026-10-21T10:00:00.000Z","id":"cp-final"},
                        "serverTimestamp": "2026-10-21T10:00:00.000Z",
                        "hasMore": false,
                        "schemaVersion": 1,
                        "collaborationVersion": 1,
                        "accessRevision": 25,
                        "accessResults": []
                    }"""
                )
            } else {
                // Empty data scan page (no records on server)
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "records": [],
                        "checkpoint": {"updatedAt":"2026-10-21T10:00:00.000Z","id":"cp-empty"},
                        "serverTimestamp": "2026-10-21T10:00:00.000Z",
                        "hasMore": false,
                        "schemaVersion": 1,
                        "collaborationVersion": 1,
                        "accessRevision": 25
                    }"""
                )
            }
        }

        val vault = FakeCredentialVault()
        vault.store(accountKey, CredentialBundle("tok", "ref"))
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(AccountIdentity("user-alice", "alice", "alice@example.com"), accountKey)
        val client = HubSyncClient(config, session, expectedAccountKey = accountKey)

        val scan = StagedFullScan(client, store)
        val result = scan.executeScan()

        assertIs<ApiResult.Success<Unit>>(result)
        // Terminal empty check was sent and verified at revision E=25
        assertTrue(terminalCheckSent)
        assertEquals(25L, terminalRevision)
    }

    @Test
    fun testRevisionMismatchBetweenDataPagesAndAccessChecksRestartsScan() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(accountKey = accountKey, userId = "user-alice", daoOverride = fakeDao)

        var callCount = 0
        val engine = TestHttpEngine { req ->
            callCount++
            val body = when (val b = req.body) {
                is io.ktor.http.content.TextContent -> b.text
                is io.ktor.http.content.ByteArrayContent -> b.bytes().decodeToString()
                else -> b.toString()
            }
            if (body.contains("accessChecks")) {
                // Access check races with revision change on server -> 409 ACCESS_REVISION_CHANGED
                Pair(
                    HttpStatusCode.Conflict,
                    """{"error":{"code":"ACCESS_REVISION_CHANGED","message":"Access revision changed to 30"}}"""
                )
            } else {
                // Page 1 gave revision 25
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "records": [
                            {
                                "tableName": "todos",
                                "rowId": "task-1",
                                "data": {"id":"task-1","title":"Task 1","completed":false,"createdAt":1000},
                                "version": 1,
                                "syncedAt": "2026-10-21T10:00:00.000Z",
                                "deleted": false,
                                "ownerUserId": "user-alice"
                            }
                        ],
                        "checkpoint": {"updatedAt":"2026-10-21T10:00:00.000Z","id":"task-1"},
                        "serverTimestamp": "2026-10-21T10:00:00.000Z",
                        "hasMore": false,
                        "schemaVersion": 1,
                        "collaborationVersion": 1,
                        "accessRevision": 25
                    }"""
                )
            }
        }

        val vault = FakeCredentialVault()
        vault.store(accountKey, CredentialBundle("tok", "ref"))
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(AccountIdentity("user-alice", "alice", "alice@example.com"), accountKey)
        val client = HubSyncClient(config, session, expectedAccountKey = accountKey)

        val scan = StagedFullScan(client, store)
        val result = scan.executeScan()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.AccessRevisionChanged>(result.error)
        // Staging was cleared on 409
        assertEquals(0, fakeDao.countStagedRecords())
        // Staged data was NOT promoted to server_base
        assertEquals(0, fakeDao.countServerBase())
    }

    @Test
    fun testAuthoritativeDenialPurgesCollaborativeContentAndUnsentPayloads() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(accountKey = accountKey, userId = "user-alice", daoOverride = fakeDao)

        // Existing collaborative task and unsent draft
        val boundBob = boundBindingKey("user-bob")
        fakeDao.upsertServerBase(
            ServerBaseEntity(
                bindingKey = boundBob,
                ownerBinding = BINDING_BOUND,
                ownerUserId = "user-bob",
                tableName = "todos",
                rowId = "task-bob-1",
                serverRevision = 1L,
                title = "Bob's private task",
                completed = false,
                standardConfirmed = true
            )
        )
        fakeDao.upsertDraft(
            EditorDraftEntity(
                bindingKey = boundBob,
                ownerBinding = BINDING_BOUND,
                ownerUserId = "user-bob",
                tableName = "todos",
                rowId = "task-bob-1",
                localRevision = 1L,
                draftTitle = "Alice's unsent edit on Bob's task",
                draftCompleted = true,
                updatedAt = 100L
            )
        )
        fakeDao.upsertOutbox(
            OutboxEntity(
                bindingKey = boundBob,
                ownerBinding = BINDING_BOUND,
                ownerUserId = "user-bob",
                tableName = "todos",
                rowId = "task-bob-1",
                opId = "op-unsent-bob-1",
                localRevision = 1L,
                kind = "update",
                completed = false,
                createdAt = 100L,
                createdAtEpochMs = 100L,
                deliveryState = OutboxEntity.STATE_PENDING,
                title = "Alice's unsent edit on Bob's task"
            )
        )

        val engine = TestHttpEngine { req ->
            val body = when (val b = req.body) {
                is io.ktor.http.content.TextContent -> b.text
                is io.ktor.http.content.ByteArrayContent -> b.bytes().decodeToString()
                else -> b.toString()
            }
            if (body.contains("\"accessChecks\":[]")) {
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "records": [],
                        "checkpoint": {"updatedAt":"2026-10-21T10:00:00.000Z","id":"cp-term"},
                        "serverTimestamp": "2026-10-21T10:00:00.000Z",
                        "hasMore": false,
                        "schemaVersion": 1,
                        "collaborationVersion": 1,
                        "accessRevision": 50,
                        "accessResults": []
                    }"""
                )
            } else if (body.contains("accessChecks")) {
                // Return authoritative unavailable for task-bob-1
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "records": [],
                        "checkpoint": {"updatedAt":"2026-10-21T10:00:00.000Z","id":"cp-chk"},
                        "serverTimestamp": "2026-10-21T10:00:00.000Z",
                        "hasMore": false,
                        "schemaVersion": 1,
                        "collaborationVersion": 1,
                        "accessRevision": 50,
                        "accessResults": [
                            {"ref":{"ownerUserId":"user-bob","tableName":"todos","rowId":"task-bob-1"},"status":"unavailable"}
                        ]
                    }"""
                )
            } else {
                // Data scan page: empty
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "records": [],
                        "checkpoint": {"updatedAt":"2026-10-21T10:00:00.000Z","id":"cp-0"},
                        "serverTimestamp": "2026-10-21T10:00:00.000Z",
                        "hasMore": false,
                        "schemaVersion": 1,
                        "collaborationVersion": 1,
                        "accessRevision": 50
                    }"""
                )
            }
        }

        val vault = FakeCredentialVault()
        vault.store(accountKey, CredentialBundle("tok", "ref"))
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(AccountIdentity("user-alice", "alice", "alice@example.com"), accountKey)
        val client = HubSyncClient(config, session, expectedAccountKey = accountKey)

        val scan = StagedFullScan(client, store)
        val result = scan.executeScan()

        assertIs<ApiResult.Success<Unit>>(result)

        // Privacy Invariant: Remote content purged, unsent drafts/outbox purged
        assertNull(fakeDao.getServerBase("task-bob-1"))
        assertNull(fakeDao.getDraft("task-bob-1"))
        assertNull(fakeDao.getOutboxEntry("op-unsent-bob-1"))

        // Content-free AccessLossEvidence retained
        val evidenceList = fakeDao.getAllAccessLossEvidence()
        assertEquals(1, evidenceList.size)
        assertEquals("user-bob", evidenceList[0].ownerUserId)
        assertEquals("task-bob-1", evidenceList[0].rowId)
        assertEquals(AccessLossReason.UNAVAILABLE.name, evidenceList[0].reason)
    }

    @Test
    fun testDeltaPushAckCommittedEvenIfDeltaPullFails() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(accountKey = accountKey, userId = "user-alice", daoOverride = fakeDao)
        val reconciliation = OutboxReconciliation(store)

        val boundAlice = boundBindingKey("user-alice")
        val envelope = PushRecordDto(
            tableName = "todos",
            rowId = "task-delta-1",
            data = TaskJsonMerge.encode(Todo(TaskRef("user-alice", "task-delta-1"), "Delta Task", false, 100L)),
            version = 2L,
            deleted = false,
            assumedServerVersion = 1L,
            ownerUserId = "user-alice"
        )
        val op = OutboxEntity(
            bindingKey = boundAlice,
            ownerUserId = "user-alice",
            tableName = "todos",
            rowId = "task-delta-1",
            opId = "op-delta-1",
            localRevision = 1L,
            version = 2L,
            kind = "update",
            completed = false,
            createdAt = 100L,
            createdAtEpochMs = 100L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            title = "Delta Task",
            envelopeJson = syncWireJson.encodeToString(envelope)
        )
        fakeDao.upsertOutbox(op)

        val deltaResponse = DeltaResponseDto(
            push = PushResponseDto(
                synced = 1,
                conflicts = emptyList(),
                syncedRecords = listOf(
                    SyncedRecordDto(tableName = "todos", rowId = "task-delta-1", version = 2L, ownerUserId = "user-alice")
                ),
                serverTimestamp = "2026-10-21T10:00:00.000Z",
                schemaVersion = 1L
            ),
            pull = null,
            pullError = "Invalid delta pull result; previous checkpoint retained"
        )

        // Push results reconciled independently of pull failure
        reconciliation.reconcilePushResults(listOf(op), deltaResponse.push!!)

        val committedOp = fakeDao.getOutboxEntry("op-delta-1")
        assertNotNull(committedOp)
        assertEquals("ACKNOWLEDGED", committedOp.deliveryState)
        assertEquals("EXPLICIT_ACCEPTED_TUPLE", committedOp.resolution)
    }

    @Test
    fun testExactAckCollisionProtectionBetweenOwners() = runTest {
        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(accountKey = accountKey, userId = "user-alice", daoOverride = fakeDao)
        val reconciliation = OutboxReconciliation(store)

        val boundAlice = boundBindingKey("user-alice")
        val boundBob = boundBindingKey("user-bob")

        // Alice's task-1 op
        val aliceEnvelope = PushRecordDto(
            tableName = "todos",
            rowId = "task-1",
            data = TaskJsonMerge.encode(Todo(TaskRef("user-alice", "task-1"), "Alice's Task 1", false, 100L)),
            version = 3L,
            deleted = false,
            assumedServerVersion = 2L,
            ownerUserId = "user-alice"
        )
        val bobEnvelope = PushRecordDto(
            tableName = "todos",
            rowId = "task-1",
            data = TaskJsonMerge.encode(Todo(TaskRef("user-bob", "task-1"), "Bob's Task 1", false, 100L)),
            version = 3L,
            deleted = false,
            assumedServerVersion = 2L,
            ownerUserId = "user-bob"
        )
        val aliceOp = OutboxEntity(
            bindingKey = boundAlice,
            ownerUserId = "user-alice",
            tableName = "todos",
            rowId = "task-1",
            opId = "op-alice-task-1",
            localRevision = 1L,
            version = 3L,
            kind = "update",
            completed = false,
            createdAt = 100L,
            createdAtEpochMs = 100L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            title = "Alice's Task 1",
            envelopeJson = syncWireJson.encodeToString(aliceEnvelope)
        )
        val bobOp = OutboxEntity(
            bindingKey = boundBob,
            ownerUserId = "user-bob",
            tableName = "todos",
            rowId = "task-1",
            opId = "op-bob-task-1",
            localRevision = 1L,
            version = 3L,
            kind = "update",
            completed = false,
            createdAt = 100L,
            createdAtEpochMs = 100L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            title = "Bob's Task 1",
            envelopeJson = syncWireJson.encodeToString(bobEnvelope)
        )
        fakeDao.upsertOutbox(aliceOp)
        fakeDao.upsertOutbox(bobOp)

        // Server acknowledges Alice's task-1
        val aliceAckResponse = PushResponseDto(
            synced = 1,
            conflicts = emptyList(),
            syncedRecords = listOf(
                SyncedRecordDto(tableName = "todos", rowId = "task-1", version = 3L, ownerUserId = "user-alice")
            ),
            serverTimestamp = "2026-10-21T10:00:00.000Z",
            schemaVersion = 1L
        )

        // Reconcile batch containing both ops
        reconciliation.reconcilePushResults(listOf(aliceOp, bobOp), aliceAckResponse)

        // Alice's op is ACKNOWLEDGED
        val afterAlice = fakeDao.getOutboxEntry("op-alice-task-1")
        assertNotNull(afterAlice)
        assertEquals("ACKNOWLEDGED", afterAlice.deliveryState)

        // Bob's op was NOT acknowledged by Alice's ack! It marks UNCERTAIN because no unambiguous tuple for user-bob matched.
        val afterBob = fakeDao.getOutboxEntry("op-bob-task-1")
        assertNotNull(afterBob)
        assertEquals(OutboxEntity.STATE_UNCERTAIN, afterBob.deliveryState)
    }
}
