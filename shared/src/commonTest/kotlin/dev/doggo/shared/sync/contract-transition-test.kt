package dev.doggo.shared.sync

import dev.doggo.shared.auth.FakeCredentialVault
import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.auth.TestHttpEngine
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.CredentialBundle
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.StrictSyncV2Contract
import dev.doggo.shared.data.EditorDraftEntity
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.data.ServerBaseEntity
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractTransitionTest {

    private val accountKey = AccountKey.create("http://localhost:3000", "user-transition-test")
    private val config = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "doggo",
        appApiKey = "doggo-secret-key",
        buildMode = BuildMode.DEBUG
    )
    private val identity = AccountIdentity(
        userId = "user-transition-test",
        username = "tester",
        email = "tester@example.com"
    )

    private suspend fun createSession(engine: TestHttpEngine): HubAuthSession {
        val vault = FakeCredentialVault()
        vault.store(accountKey, CredentialBundle("acc", "ref"))
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(identity, accountKey)
        return session
    }
    private fun createStore(): Pair<RoomAccountStore, FakeDoggoDao> {
        val fakeDao = FakeDoggoDao()
        val store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-transition-test",
            database = null,
            appId = "doggo",
            daoOverride = fakeDao
        )
        return Pair(store, fakeDao)
    }

    @Test
    fun testFullTransitionHappyPath() = runTest {
        val (store, dao) = createStore()

        // Setup: One accepted v1 op (lost push response), one never-sent update
        dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-1",
                serverRevision = 1L,
                deleted = false,
                title = "Row 1 V1 Base",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-v2"
            )
        )

        // Op 1 was in-flight (DISPATCHING) when upgrade happened
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-lost-ack",
                epoch = "epoch-v2",
                tableName = "todos",
                rowId = "row-1",
                kind = "update",
                expectedRevision = 1L,
                title = "Row 1 Edited",
                completed = true,
                createdAt = 1000L,
                digest = "digest-lost",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_DISPATCHING,
                createdAtEpochMs = 1500L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V1
            )
        )

        // Op 2 was never sent (PENDING)
        dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-2",
                serverRevision = 1L,
                deleted = false,
                title = "Row 2 Base",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-v2"
            )
        )
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-unsent",
                epoch = "epoch-v2",
                tableName = "todos",
                rowId = "row-2",
                kind = "update",
                expectedRevision = 1L,
                title = "Row 2 Edited Offline",
                completed = false,
                createdAt = 1000L,
                digest = "digest-unsent",
                localRevision = 2L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1600L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V1
            )
        )

        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/doggo/strong/legacy-receipts" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v1",
                            "epoch":"epoch-v2",
                            "results":[
                                {
                                    "opId":"op-lost-ack",
                                    "rowId":"row-1",
                                    "status":"provenAccepted",
                                    "acceptedRevision":2
                                }
                            ]
                        }"""
                    )
                }
                "/api/v1/sync/doggo/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v2",
                            "epoch":"epoch-v2",
                            "records":[
                                {
                                    "tableName":"todos",
                                    "rowId":"row-1",
                                    "revision":2,
                                    "deleted":false,
                                    "data":{
                                        "id":"row-1",
                                        "title":"Row 1 Edited",
                                        "completed":true,
                                        "createdAt":1000,
                                        "deadlineDate":"2026-11-15",
                                        "deadlineZone":"UTC",
                                        "deadlineOffsetSeconds":0,
                                        "deadlineAnchorEpochMs":1794700800000,
                                        "deadlineOverdueEpochMs":1794787200000,
                                        "reminderAnchorTime":"09:00",
                                        "reminderEnabled":true,
                                        "reminderLeadMinutes":60,
                                        "reminderCount":1,
                                        "scheduleToken":"server-tok-1"
                                    }
                                },
                                {
                                    "tableName":"todos",
                                    "rowId":"row-2",
                                    "revision":1,
                                    "deleted":false,
                                    "data":{
                                        "id":"row-2",
                                        "title":"Row 2 Base",
                                        "completed":false,
                                        "createdAt":1000,
                                        "deadlineDate":"2026-11-20",
                                        "deadlineZone":"UTC",
                                        "deadlineOffsetSeconds":0,
                                        "deadlineAnchorEpochMs":1795132800000,
                                        "deadlineOverdueEpochMs":1795219200000,
                                        "reminderAnchorTime":"09:00",
                                        "reminderEnabled":false,
                                        "reminderLeadMinutes":0,
                                        "reminderCount":1,
                                        "scheduleToken":"server-tok-2"
                                    }
                                }
                            ],
                            "cursor":null,
                            "hasMore":false
                        }"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createSession(engine)
        val syncClient = HubSyncClient(config, session)
        var generatedUuidCount = 0
        val coordinator = ContractTransitionCoordinator(store, syncClient) {
            "v2-new-op-${++generatedUuidCount}"
        }

        val phasesRecorded = mutableListOf<TransitionPhase>()
        val result = coordinator.executeTransition("epoch-v2") { phase ->
            phasesRecorded.add(phase)
        }

        assertIs<ApiResult.Success<TransitionPhase>>(result)
        assertEquals(TransitionPhase.READY_V2, result.value)
        assertEquals(TransitionPhase.READY_V2, coordinator.getDurablePhase())

        // 1. op-lost-ack was provenAccepted: reconciled to base at revision 2 and deleted from outbox
        assertNull(dao.getOutboxEntry("op-lost-ack"))
        val base1 = dao.getServerBase("row-1")
        assertNotNull(base1)
        assertEquals(2L, base1.serverRevision)
        assertEquals("Row 1 Edited", base1.title)
        assertTrue(base1.completed)
        assertEquals("2026-11-15", base1.deadlineDate)
        assertEquals("server-tok-1", base1.scheduleToken)

        // 2. op-unsent was translated to v2: replaced with fresh v2 op targeting scanned base rev 1
        assertNull(dao.getOutboxEntry("op-unsent"))
        val translatedOp = dao.getOutboxEntry("v2-new-op-1")
        assertNotNull(translatedOp)
        assertEquals(OutboxEntity.CONTRACT_VERSION_V2, translatedOp.contractVersion)
        assertEquals("row-2", translatedOp.rowId)
        assertEquals("Row 2 Edited Offline", translatedOp.title)
        assertEquals(1L, translatedOp.expectedRevision)
        assertEquals("TRANSLATED_V2", translatedOp.transitionCheckpoint)
        // Preserves server deadline metadata from scanned v2 base!
        assertEquals("2026-11-20", translatedOp.deadlineDate)
        assertEquals("server-tok-2", translatedOp.scheduleToken)
    }

    @Test
    fun testLegacyReceiptUnknownSupersededBlocksWithExplicitConflict() = runTest {
        val (store, dao) = createStore()

        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-superseded",
                epoch = "epoch-v2",
                tableName = "todos",
                rowId = "row-conflicted",
                kind = "update",
                expectedRevision = 1L,
                title = "My Edit",
                completed = false,
                createdAt = 1000L,
                digest = "digest-old",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_DISPATCHING,
                createdAtEpochMs = 1500L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V1
            )
        )

        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/doggo/strong/legacy-receipts" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v1",
                            "epoch":"epoch-v2",
                            "results":[
                                {
                                    "opId":"op-superseded",
                                    "rowId":"row-conflicted",
                                    "status":"unknownSuperseded",
                                    "current":{"revision":4,"deleted":false}
                                }
                            ]
                        }"""
                    )
                }
                "/api/v1/sync/doggo/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v2","epoch":"epoch-v2","records":[],"cursor":null,"hasMore":false}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createSession(engine)
        val syncClient = HubSyncClient(config, session)
        val coordinator = ContractTransitionCoordinator(store, syncClient)

        val result = coordinator.executeTransition("epoch-v2")
        assertIs<ApiResult.Success<TransitionPhase>>(result)

        // op-superseded must be frozen as UNKNOWN_SUPERSEDED (never translated or silently discarded)
        val op = dao.getOutboxEntry("op-superseded")
        assertNotNull(op)
        assertEquals(OutboxEntity.STATE_UNKNOWN_SUPERSEDED, op.deliveryState)
        assertTrue(op.errorMessage!!.contains("Superseded"))
    }

    @Test
    fun testRequiresReviewDoesNotAutoTranslate() = runTest {
        val (store, dao) = createStore()

        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-review-needed",
                epoch = "epoch-v2",
                tableName = "todos",
                rowId = "row-ambiguous",
                kind = "update",
                expectedRevision = 1L,
                title = "Ambiguous Migration Op",
                completed = false,
                createdAt = 1000L,
                digest = "digest-ambig",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1500L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V1,
                intentMask = OutboxEntity.INTENT_REQUIRES_REVIEW
            )
        )

        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/doggo/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v2","epoch":"epoch-v2","records":[],"cursor":null,"hasMore":false}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createSession(engine)
        val syncClient = HubSyncClient(config, session)
        val coordinator = ContractTransitionCoordinator(store, syncClient)

        val result = coordinator.executeTransition("epoch-v2")
        assertIs<ApiResult.Success<TransitionPhase>>(result)

        // REQUIRES_REVIEW op must be marked CONFLICT and NOT translated into v2
        val op = dao.getOutboxEntry("op-review-needed")
        assertNotNull(op)
        assertEquals(OutboxEntity.STATE_CONFLICT, op.deliveryState)
        assertTrue(op.errorMessage!!.contains("Requires review"))
    }

    @Test
    fun testCrashResumptionFromCheckpoints() = runTest {
        val (store, dao) = createStore()

        // Simulate crash having already completed quiescence and legacy reconciling
        coordinatorSetPhase(store, TransitionPhase.V2_BASE_SCANNING)

        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/doggo/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v2","epoch":"epoch-v2","records":[],"cursor":null,"hasMore":false}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createSession(engine)
        val syncClient = HubSyncClient(config, session)
        val coordinator = ContractTransitionCoordinator(store, syncClient)

        // Resumes without re-running legacy-receipts
        val result = coordinator.executeTransition("epoch-v2")
        assertIs<ApiResult.Success<TransitionPhase>>(result)
        assertEquals(TransitionPhase.READY_V2, result.value)
    }

    private suspend fun coordinatorSetPhase(store: RoomAccountStore, phase: TransitionPhase) {
        val meta = store.dao.getMeta() ?: dev.doggo.shared.data.AccountSyncMetaEntity()
        store.dao.upsertMeta(
            meta.copy(
                syncStatus = "${ContractTransitionCoordinator.TRANSITION_PREFIX}${phase.wireName}",
                safeErrorCode = "CHECKPOINT_${phase.name}"
            )
        )
    }
}
