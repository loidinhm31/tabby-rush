package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.StrictOperationKind
import dev.tabbyrush.shared.contract.StrictSyncV2Contract
import dev.tabbyrush.shared.contract.StrictSyncV3Contract
import dev.tabbyrush.shared.data.EditorDraftEntity
import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.ServerBaseEntity
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
        appId = "tabby-rush",
        appApiKey = "tabbyrush-secret-key",
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
    private fun createStore(): Pair<RoomAccountStore, FakeTabbyRushDao> {
        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-transition-test",
            database = null,
            appId = "tabby-rush",
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
                "/api/v1/sync/tabby-rush/strong/legacy-receipts" -> {
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
                "/api/v1/sync/tabby-rush/strong/pull" -> {
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
                "/api/v1/sync/tabby-rush/strong/legacy-receipts" -> {
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
                "/api/v1/sync/tabby-rush/strong/pull" -> {
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
                "/api/v1/sync/tabby-rush/strong/pull" -> {
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
                "/api/v1/sync/tabby-rush/strong/pull" -> {
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

    @Test
    fun testV2ToV3TransitionTranslatesPendingV2IntentsToV3() = runTest {
        val (store, dao) = createStore()

        // Setup: Account is at READY_V2
        coordinatorSetPhase(store, TransitionPhase.READY_V2)

        val rootUuid = "d0000000-0000-4000-8000-000000000001"
        dao.upsertServerBase(
            ServerBaseEntity(
                rowId = rootUuid,
                serverRevision = 2L,
                deleted = false,
                title = "Root V2 Task",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-v3",
                deadlineDate = "2026-10-30",
                parentTaskId = null
            )
        )

        // 1. Pending V2 CREATE with deadline and reminder
        val newRowUuid = "d0000000-0000-4000-8000-000000000002"
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-v2-pending-create",
                epoch = "epoch-v3",
                tableName = "todos",
                rowId = newRowUuid,
                kind = "create",
                expectedRevision = 0L,
                title = "New V2 Task With Deadline",
                completed = false,
                createdAt = 1100L,
                digest = "d-create-v2",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1100L,
                deadlineDate = "2026-11-15",
                deadlineTime = "10:00",
                deadlineZone = "Asia/Saigon",
                deadlineOffsetSeconds = 25200L,
                reminderAnchorTime = null,
                deadlineAnchorEpochMs = 1794711600000L,
                deadlineOverdueEpochMs = 1794711600000L,
                reminderEnabled = true,
                reminderLeadMinutes = 15L,
                reminderCount = 1L,
                scheduleToken = "create-v2-token",
                contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                parentTaskId = null
            )
        )

        // 2. Pending V2 UPDATE with deadline
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-v2-pending-update",
                epoch = "epoch-v3",
                tableName = "todos",
                rowId = rootUuid,
                kind = "update",
                expectedRevision = 2L,
                title = "Updated V2 Title",
                completed = true,
                createdAt = 1000L,
                digest = "d-old-v2",
                localRevision = 2L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1200L,
                deadlineDate = "2026-10-31",
                deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L,
                reminderAnchorTime = "09:00",
                deadlineAnchorEpochMs = 1793437200000L,
                deadlineOverdueEpochMs = 1793437200000L,
                reminderEnabled = true,
                reminderLeadMinutes = 30L,
                reminderCount = 1L,
                scheduleToken = "updated-v2-token",
                contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                parentTaskId = null
            )
        )
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v3",
                            "epoch":"epoch-v3",
                            "records":[
                                {
                                    "tableName":"todos",
                                    "rowId":"$rootUuid",
                                    "revision":2,
                                    "deleted":false,
                                    "data":{
                                        "id":"$rootUuid",
                                        "title":"Root V2 Task",
                                        "completed":false,
                                        "createdAt":1000,
                                        "deadlineDate":"2026-10-30",
                                        "parentTaskId":null
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
        val coordinator = ContractTransitionCoordinator(store, syncClient)

        val result = coordinator.executeTransition(
            activeEpoch = "epoch-v3",
            targetProtocol = StrictSyncV3Contract.PROTOCOL_VERSION
        )
        assertIs<ApiResult.Success<TransitionPhase>>(result)
        assertEquals(TransitionPhase.READY_V3, result.value)
        assertEquals(TransitionPhase.READY_V3, coordinator.getDurablePhase())

        // Verify both pending v2 operations were translated into v3 with preserved temporal metadata!
        val allOutbox = dao.getAllOutbox()
        assertEquals(2, allOutbox.size)

        val translatedCreate = allOutbox.first { it.rowId == newRowUuid }
        assertEquals(OutboxEntity.CONTRACT_VERSION_V3, translatedCreate.contractVersion)
        assertEquals("TRANSLATED_V3", translatedCreate.transitionCheckpoint)
        assertEquals("New V2 Task With Deadline", translatedCreate.title)
        assertEquals("2026-11-15", translatedCreate.deadlineDate)
        assertEquals("10:00", translatedCreate.deadlineTime)
        assertEquals("create-v2-token", translatedCreate.scheduleToken)
        assertTrue(translatedCreate.reminderEnabled)
        assertEquals(15L, translatedCreate.reminderLeadMinutes)

        val translatedUpdate = allOutbox.first { it.rowId == rootUuid }
        assertEquals(OutboxEntity.CONTRACT_VERSION_V3, translatedUpdate.contractVersion)
        assertEquals("TRANSLATED_V3", translatedUpdate.transitionCheckpoint)
        assertEquals(rootUuid, translatedUpdate.rowId)
        assertEquals("Updated V2 Title", translatedUpdate.title)
        assertEquals("2026-10-31", translatedUpdate.deadlineDate)
        assertEquals("updated-v2-token", translatedUpdate.scheduleToken)
        assertEquals(30L, translatedUpdate.reminderLeadMinutes)
        assertTrue(translatedUpdate.completed == true)
    }

    @Test
    fun testV2ToV3TransitionUpdateClearingDeadlineDoesNotResurrectBaseDeadline() = runTest {
        val (store, dao) = createStore()
        coordinatorSetPhase(store, TransitionPhase.READY_V2)

        val rowUuid = "f0000000-0000-4000-8000-000000000001"
        dao.upsertServerBase(
            ServerBaseEntity(
                rowId = rowUuid,
                serverRevision = 2L,
                deleted = false,
                title = "Task with deadline",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-v3-clear",
                deadlineDate = "2026-10-30",
                scheduleToken = "base-tok",
                parentTaskId = null
            )
        )

        // User explicitly cleared the deadline in V2 update
        dao.upsertOutbox(
            OutboxEntity(
                opId = "op-v2-clear-deadline",
                epoch = "epoch-v3-clear",
                tableName = "todos",
                rowId = rowUuid,
                kind = "update",
                expectedRevision = 2L,
                title = "Task with deadline cleared",
                completed = false,
                createdAt = 1000L,
                digest = "d-clear",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_PENDING,
                createdAtEpochMs = 1200L,
                deadlineDate = null,
                scheduleToken = null,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                parentTaskId = null
            )
        )

        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v3",
                            "epoch":"epoch-v3-clear",
                            "records":[
                                {
                                    "tableName":"todos",
                                    "rowId":"$rowUuid",
                                    "revision":2,
                                    "deleted":false,
                                    "data":{
                                        "id":"$rowUuid",
                                        "title":"Task with deadline",
                                        "completed":false,
                                        "createdAt":1000,
                                        "deadlineDate":"2026-10-30",
                                        "scheduleToken":"base-tok",
                                        "parentTaskId":null
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
        val coordinator = ContractTransitionCoordinator(store, syncClient)

        val result = coordinator.executeTransition(
            activeEpoch = "epoch-v3-clear",
            targetProtocol = StrictSyncV3Contract.PROTOCOL_VERSION
        )
        assertIs<ApiResult.Success<TransitionPhase>>(result)

        val allOutbox = dao.getAllOutbox()
        assertEquals(1, allOutbox.size)
        val translated = allOutbox[0]
        assertEquals(OutboxEntity.CONTRACT_VERSION_V3, translated.contractVersion)
        assertNull(translated.deadlineDate) // Base deadline NOT resurrected!
        assertNull(translated.scheduleToken)
    }

    private suspend fun coordinatorSetPhase(store: RoomAccountStore, phase: TransitionPhase) {
        val meta = store.dao.getMeta() ?: dev.tabbyrush.shared.data.AccountSyncMetaEntity()
        store.dao.upsertMeta(
            meta.copy(
                syncStatus = "${ContractTransitionCoordinator.TRANSITION_PREFIX}${phase.wireName}",
                safeErrorCode = "CHECKPOINT_${phase.name}"
            )
        )
    }
}
