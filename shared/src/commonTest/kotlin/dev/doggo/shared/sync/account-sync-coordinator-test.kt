package dev.doggo.shared.sync

import dev.doggo.shared.auth.FakeCredentialVault
import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.auth.TestHttpEngine
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.AuthState
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.CredentialBundle
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.SyncStatus
import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.StrictSyncContract
import dev.doggo.shared.contract.StrictSyncDigest
import dev.doggo.shared.contract.StrictTodoData
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.data.ServerBaseEntity
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountSyncCoordinatorTest {

    private val config = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "doggo",
        appApiKey = "doggo-secret-key",
        buildMode = BuildMode.DEBUG
    )

    private val accountKey = AccountKey.create(config.canonicalOrigin, "user-123")
    private val identity = AccountIdentity(
        userId = "user-123",
        username = "tester",
        email = "tester@example.com"
    )

    private suspend fun createTestSetup(engine: TestHttpEngine): Triple<AccountSyncCoordinator, RoomAccountStore, FakeDoggoDao> {
        val vault = FakeCredentialVault()
        vault.store(
            accountKey,
            CredentialBundle(
                accessToken = "valid-access-token",
                refreshToken = "valid-refresh-token"
            )
        )
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(identity, accountKey)

        val fakeDao = FakeDoggoDao()
        val store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-123",
            database = null,
            appId = "doggo",
            daoOverride = fakeDao
        )

        val client = HubSyncClient(config, session)
        val coordinator = AccountSyncCoordinator(
            store = store,
            session = session,
            syncClient = client
        )

        return Triple(coordinator, store, fakeDao)
    }

    @Test
    fun testTriggerSyncSuccess() = runTest {
        val d1 = StrictSyncDigest.computeDigest(
            domain = StrictSyncContract.PROTOCOL_VERSION,
            appId = "doggo",
            userId = "user-123",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-1",
            opId = "op-1",
            kind = StrictOperationKind.CREATE,
            expectedRevision = 0L,
            data = StrictTodoData("row-1", "Todo 1", false, 1000L)
        )

        var pullCount = 0
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/doggo/strong/bootstrap" -> Pair(
                    HttpStatusCode.OK,
                    """{"protocol":"strict-v1","appId":"doggo","epoch":"epoch-1","tables":["todos"]}"""
                )
                "/api/v1/sync/doggo/strong/pull" -> {
                    pullCount++
                    if (pullCount == 1) {
                        Pair(
                            HttpStatusCode.OK,
                            """{"protocol":"strict-v1","epoch":"epoch-1","records":[],"cursor":null,"hasMore":false}"""
                        )
                    } else {
                        Pair(
                            HttpStatusCode.OK,
                            """{
                                "protocol":"strict-v1",
                                "epoch":"epoch-1",
                                "records":[
                                    {"tableName":"todos","rowId":"row-1","revision":1,"deleted":false,"data":{"id":"row-1","title":"Todo 1","completed":false,"createdAt":1000}}
                                ],
                                "cursor":null,
                                "hasMore":false
                            }"""
                        )
                    }
                }
                "/api/v1/sync/doggo/strong/push" -> Pair(
                    HttpStatusCode.OK,
                    """{
                        "protocol":"strict-v1",
                        "epoch":"epoch-1",
                        "results":[
                            {"opId":"op-1","rowId":"row-1","digest":"$d1","status":"accepted","acceptedRevision":1}
                        ]
                    }"""
                )
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val (coordinator, store, dao) = createTestSetup(engine)
        // Queue one pending outbox operation
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-1",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-1",
                kind = "create",
                expectedRevision = 0L,
                title = "Todo 1",
                completed = false,
                createdAt = 1000L,
                digest = "d1",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1000L
            )
        )

        coordinator.triggerSync()

        val progress = coordinator.progress.value
        println("DEBUG PROGRESS: status=${progress.status}, error=${progress.safeError}")
        assertEquals(SyncStatus.IDLE, progress.status)
        assertEquals(0, progress.conflictCount)
        assertNotNull(progress.lastSyncTime)
        assertNull(progress.safeError)

        // Outbox entry should be reconciled and base updated
        assertNull(dao.getOutboxEntry("op-1"))
        val base = dao.getServerBase("row-1")
        assertNotNull(base)
        assertEquals(1L, base.serverRevision)
        assertEquals("Todo 1", base.title)
    }

    @Test
    fun testEpochMismatchTransitionsToResetRequired() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/doggo/strong/bootstrap" -> Pair(
                    HttpStatusCode.OK,
                    """{"protocol":"strict-v1","appId":"doggo","epoch":"epoch-NEW","tables":["todos"]}"""
                )
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val (coordinator, store, dao) = createTestSetup(engine)
        // Set local active epoch to epoch-OLD
        store.setActiveEpoch("epoch-OLD")

        coordinator.triggerSync()

        val progress = coordinator.progress.value
        assertEquals(SyncStatus.RESET_REQUIRED, progress.status)
        assertIs<SafeError.ResetRequired>(progress.safeError)
        assertEquals("EPOCH_MISMATCH", progress.safeError?.code)
    }

    @Test
    fun testConflictResolutionDiscard() = runTest {
        val engine = TestHttpEngine { Pair(HttpStatusCode.OK, "{}") }
        val (coordinator, store, dao) = createTestSetup(engine)

        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-c",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-c",
                kind = "update",
                expectedRevision = 1L,
                title = "Conflicted",
                completed = true,
                createdAt = 1000L,
                digest = "d-c",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                createdAtEpochMs = 1000L
            )
        )

        coordinator.discardConflict("row-c")

        assertNull(dao.getOutboxEntry("op-c"))
        assertEquals(0, coordinator.progress.value.conflictCount)
    }

    @Test
    fun testConflictResolutionReapply() = runTest {
        val engine = TestHttpEngine { Pair(HttpStatusCode.OK, "{}") }
        val (coordinator, store, dao) = createTestSetup(engine)
        store.setActiveEpoch("epoch-1")

        dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-c",
                serverRevision = 5L, // Current server revision is 5
                deleted = false,
                title = "Server Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1"
            )
        )

        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-old-conflict",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-c",
                kind = "update",
                expectedRevision = 4L, // Conflict was at expected 4
                title = "My Winning Edit",
                completed = true,
                createdAt = 1000L,
                digest = "d-old",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                createdAtEpochMs = 1000L
            )
        )

        coordinator.reapplyConflict("row-c")

        // Old conflict op should be deleted
        assertNull(dao.getOutboxEntry("op-old-conflict"))

        // New op created targeting current serverRevision 5
        val ops = dao.getOutboxByRowId("row-c")
        assertEquals(1, ops.size)
        val newOp = ops[0]
        assertEquals(5L, newOp.expectedRevision)
        assertEquals("My Winning Edit", newOp.title)
        assertEquals(OutboxEntity.STATE_PENDING, newOp.deliveryState)
    }

    @Test
    fun testConflictResolutionCopyAsNew() = runTest {
        val engine = TestHttpEngine { Pair(HttpStatusCode.OK, "{}") }
        val (coordinator, store, dao) = createTestSetup(engine)
        store.setActiveEpoch("epoch-1")

        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-old-conflict",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-c",
                kind = "update",
                expectedRevision = 4L,
                title = "Content To Preserve",
                completed = false,
                createdAt = 1000L,
                digest = "d-old",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                createdAtEpochMs = 1000L
            )
        )

        coordinator.copyAsNew("row-c")

        // Old conflict op should be deleted
        assertNull(dao.getOutboxEntry("op-old-conflict"))

        // A new Todo with brand new rowId should be created in outbox
        val allOutbox = dao.getAllOutbox()
        assertEquals(1, allOutbox.size)
        val brandNewOp = allOutbox[0]
        assertTrue(brandNewOp.rowId != "row-c")
        assertEquals(0L, brandNewOp.expectedRevision)
        assertEquals("create", brandNewOp.kind)
        assertEquals("Content To Preserve", brandNewOp.title)
    }
}
