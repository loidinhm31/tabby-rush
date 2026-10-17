package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.SyncStatus
import dev.tabbyrush.shared.contract.StrictOperationKind
import dev.tabbyrush.shared.contract.StrictSyncContract
import dev.tabbyrush.shared.contract.StrictSyncDigest
import dev.tabbyrush.shared.contract.StrictTodoData
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.StrictSyncV2Contract
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.data.AccountSyncMetaEntity
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.ServerBaseEntity
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
        appId = "tabby-rush",
        appApiKey = "tabbyrush-secret-key",
        buildMode = BuildMode.DEBUG
    )

    private val accountKey = AccountKey.create(config.canonicalOrigin, "user-123")
    private val identity = AccountIdentity(
        userId = "user-123",
        username = "tester",
        email = "tester@example.com"
    )

    private suspend fun createTestSetup(engine: TestHttpEngine): Triple<AccountSyncCoordinator, RoomAccountStore, FakeTabbyRushDao> {
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

        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-123",
            database = null,
            appId = "tabby-rush",
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
            appId = "tabby-rush",
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
                "/api/v1/sync/tabby-rush/strong/bootstrap" -> Pair(
                    HttpStatusCode.OK,
                    """{"protocol":"strict-v1","appId":"tabby-rush","epoch":"epoch-1","tables":["todos"]}"""
                )
                "/api/v1/sync/tabby-rush/strong/pull" -> {
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
                "/api/v1/sync/tabby-rush/strong/push" -> Pair(
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
                "/api/v1/sync/tabby-rush/strong/bootstrap" -> Pair(
                    HttpStatusCode.OK,
                    """{"protocol":"strict-v1","appId":"tabby-rush","epoch":"epoch-NEW","tables":["todos"]}"""
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

    @Test
    fun testV2ServerTriggersTransitionAndCompletesV2Sync() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/bootstrap" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v2","appId":"tabby-rush","epoch":"epoch-v2","tables":["todos"]}"""
                    )
                }
                "/api/v1/sync/tabby-rush/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v2","epoch":"epoch-v2","records":[],"cursor":null,"hasMore":false}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val (coordinator, store, dao) = createTestSetup(engine)
        coordinator.triggerSync()

        val progress = coordinator.progress.value
        assertEquals(SyncStatus.IDLE, progress.status)
        assertNull(progress.safeError)
        assertEquals("epoch-v2", store.getActiveEpoch())
        val meta = dao.getMeta()
        assertEquals(TransitionPhase.READY_V2.wireName, meta?.syncStatus)
    }

    @Test
    fun testServerV1RejectsPendingV2Outbox() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/bootstrap" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v1","appId":"tabby-rush","epoch":"epoch-v1","tables":["todos"]}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val (coordinator, store, dao) = createTestSetup(engine)
        // Enqueue a strict-v2 operation
        dao.upsertOutbox(
            OutboxEntity(
                opId = "v2-op-on-v1-server",
                epoch = "epoch-v1",
                tableName = "todos",
                rowId = "row-v2",
                kind = "create",
                expectedRevision = 0L,
                title = "V2 Task",
                completed = false,
                createdAt = 1000L,
                digest = "dig",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1000L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                deadlineDate = "2026-11-01"
            )
        )

        coordinator.triggerSync()

        val progress = coordinator.progress.value
        assertEquals(SyncStatus.PAUSED_PROTOCOL, progress.status)
        assertNotNull(progress.safeError)
        assertEquals("SERVER_V1_INCOMPATIBLE", progress.safeError?.code)
    }

    @Test
    fun testReapplyConflictPreservesDeadlineAndComputesV2Digest() = runTest {
        val engine = TestHttpEngine { Pair(HttpStatusCode.OK, "{}") }
        val (coordinator, store, dao) = createTestSetup(engine)
        store.setActiveEpoch("epoch-v2")

        dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-c-v2",
                serverRevision = 5L,
                deleted = false,
                title = "Server Base Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-v2"
            )
        )

        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-old-v2-conflict",
                epoch = "epoch-v2",
                tableName = "todos",
                rowId = "row-c-v2",
                kind = "update",
                expectedRevision = 4L,
                title = "My Winning V2 Edit",
                completed = false,
                createdAt = 1000L,
                digest = "d-old",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                createdAtEpochMs = 1000L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                deadlineDate = "2026-11-15",
                deadlineTime = "10:00",
                deadlineZone = "America/New_York",
                deadlineOffsetSeconds = -18000L,
                deadlineAnchorEpochMs = 1794754800000L,
                deadlineOverdueEpochMs = 1794754800000L,
                reminderEnabled = true,
                reminderLeadMinutes = 15L,
                reminderCount = 1L,
                scheduleToken = "tok-reapply"
            )
        )

        coordinator.reapplyConflict("row-c-v2")

        assertNull(dao.getOutboxEntry("op-old-v2-conflict"))
        val ops = dao.getOutboxByRowId("row-c-v2")
        assertEquals(1, ops.size)
        val newOp = ops[0]
        assertEquals(5L, newOp.expectedRevision)
        assertEquals("My Winning V2 Edit", newOp.title)
        assertEquals(OutboxEntity.CONTRACT_VERSION_V2, newOp.contractVersion)
        assertEquals("2026-11-15", newOp.deadlineDate)
        assertEquals("10:00", newOp.deadlineTime)
        assertEquals("tok-reapply", newOp.scheduleToken)
        assertTrue(newOp.reminderEnabled)
        assertEquals(OutboxEntity.STATE_PENDING, newOp.deliveryState)
    }

    @Test
    fun testCopyAsNewPreservesDeadlineAndReminderRule() = runTest {
        val engine = TestHttpEngine { Pair(HttpStatusCode.OK, "{}") }
        val (coordinator, store, dao) = createTestSetup(engine)
        store.setActiveEpoch("epoch-v2")
        dao.upsertMeta(AccountSyncMetaEntity(activeEpoch = "epoch-v2", syncStatus = "ready-v2"))

        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-copy-deadline",
                epoch = "epoch-v2",
                tableName = "todos",
                rowId = "row-to-copy",
                kind = "update",
                expectedRevision = 3L,
                title = "Task with Deadline to Copy",
                completed = false,
                createdAt = 1000L,
                digest = "d-old",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_CONFLICT,
                createdAtEpochMs = 1000L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                deadlineDate = "2026-12-01",
                deadlineTime = "09:00",
                deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L,
                deadlineAnchorEpochMs = 1796115600000L,
                deadlineOverdueEpochMs = 1796115600000L,
                reminderEnabled = true,
                reminderLeadMinutes = 30L,
                reminderCount = 1L,
                scheduleToken = "old-tok"
            )
        )

        coordinator.copyAsNew("row-to-copy")

        assertNull(dao.getOutboxEntry("op-copy-deadline"))
        val allOutbox = dao.getAllOutbox()
        assertEquals(1, allOutbox.size)
        val newOp = allOutbox[0]
        assertTrue(newOp.rowId != "row-to-copy")
        assertEquals(0L, newOp.expectedRevision)
        assertEquals("create", newOp.kind)
        assertEquals("Task with Deadline to Copy", newOp.title)
        assertEquals("2026-12-01", newOp.deadlineDate)
        assertEquals("09:00", newOp.deadlineTime)
        assertTrue(newOp.reminderEnabled)
        assertEquals(30L, newOp.reminderLeadMinutes)
        // New schedule token generated
        assertNotNull(newOp.scheduleToken)
        assertTrue(newOp.scheduleToken != "old-tok")
    }
}
