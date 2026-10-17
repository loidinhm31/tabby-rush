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
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.StrictSyncContract
import dev.tabbyrush.shared.contract.StrictSyncV2Contract
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HubSyncClientTest {

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

    private suspend fun createAuthenticatedSession(engine: TestHttpEngine): HubAuthSession {
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
        return session
    }

    @Test
    fun testBootstrapSuccess() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/bootstrap" -> {
                    // Verify Dual-Auth headers
                    assertEquals("Bearer valid-access-token", request.headers["Authorization"])
                    assertEquals("tabbyrush-secret-key", request.headers["X-API-Key"])
                    assertEquals("tabby-rush", request.headers["X-App-Id"])
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v1","appId":"tabby-rush","epoch":"epoch-uuid-1","tables":["todos"]}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)
        val result = client.bootstrap()

        assertIs<ApiResult.Success<BootstrapResponseDto>>(result)
        val bootstrap = result.value
        assertEquals(StrictSyncContract.PROTOCOL_VERSION, bootstrap.protocol)
        assertEquals("tabby-rush", bootstrap.appId)
        assertEquals("epoch-uuid-1", bootstrap.epoch)
        assertEquals(listOf("todos"), bootstrap.tables)
    }

    @Test
    fun testBootstrapProtocolMismatchFails() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{"protocol":"strict-v0","appId":"tabby-rush","epoch":"epoch-uuid-1","tables":["todos"]}"""
            )
        }

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)
        val result = client.bootstrap()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
    }

    @Test
    fun testPushSuccess() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/push" -> {
                    assertEquals("tabbyrush-secret-key", request.headers["X-API-Key"])
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v1",
                            "epoch":"epoch-1",
                            "results":[
                                {
                                    "opId":"op-1",
                                    "rowId":"row-1",
                                    "digest":"digest-1",
                                    "status":"accepted",
                                    "acceptedRevision":1
                                }
                            ]
                        }"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        val ops = listOf(
            StrictOperationDto(
                opId = "op-1",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-1",
                kind = "create",
                expectedRevision = 0L,
                data = StrictTodoDataDto("row-1", "Test todo", false, 1000L)
            )
        )

        val result = client.push("epoch-1", ops)
        assertIs<ApiResult.Success<StrictPushResponseDto>>(result)
        val push = result.value
        assertEquals("epoch-1", push.epoch)
        assertEquals(1, push.results.size)
        assertEquals("accepted", push.results[0].status)
        assertEquals(1L, push.results[0].acceptedRevision)
    }

    @Test
    fun testPushBatchBoundsEnforcedClientSide() = runTest {
        val engine = TestHttpEngine { Pair(HttpStatusCode.OK, "") }
        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        // 1. More than 100 operations
        val over100Ops = (1..101).map { i ->
            StrictOperationDto(
                opId = "op-$i",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "row-$i",
                kind = "create",
                expectedRevision = 0L,
                data = StrictTodoDataDto("row-$i", "Todo $i", false, 1000L)
            )
        }
        val sizeResult = client.push("epoch-1", over100Ops)
        assertIs<ApiResult.Failure>(sizeResult)
        assertIs<SafeError.Validation>(sizeResult.error)
        assertEquals("BATCH_TOO_LARGE", sizeResult.error.code)

        // 2. Duplicate rowIds in same batch
        val duplicateOps = listOf(
            StrictOperationDto(
                opId = "op-a",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "duplicate-row",
                kind = "create",
                expectedRevision = 0L,
                data = StrictTodoDataDto("duplicate-row", "A", false, 1000L)
            ),
            StrictOperationDto(
                opId = "op-b",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "duplicate-row",
                kind = "update",
                expectedRevision = 1L,
                data = StrictTodoDataDto("duplicate-row", "B", true, 2000L)
            )
        )
        val dupResult = client.push("epoch-1", duplicateOps)
        assertIs<ApiResult.Failure>(dupResult)
        assertIs<SafeError.Validation>(dupResult.error)
        assertEquals("DUPLICATE_ROW_IDS", dupResult.error.code)
    }

    @Test
    fun testPushErrorEnvelopesMapped() = runTest {
        // 409 epochMismatch -> ResetRequired
        var statusCode = HttpStatusCode.Conflict
        var responseBody = """{"error":"Epoch changed","code":"epochMismatch"}"""

        val engine = TestHttpEngine { Pair(statusCode, responseBody) }
        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        val sampleOp = listOf(
            StrictOperationDto(
                opId = "op-1",
                epoch = "epoch-old",
                tableName = "todos",
                rowId = "row-1",
                kind = "create",
                expectedRevision = 0L
            )
        )

        val epochResult = client.push("epoch-old", sampleOp)
        assertIs<ApiResult.Failure>(epochResult)
        assertIs<SafeError.ResetRequired>(epochResult.error)

        // 426 protocolUnsupported -> ProtocolIncompatible
        statusCode = HttpStatusCode.UpgradeRequired
        responseBody = """{"error":"Unsupported mode","code":"protocolUnsupported"}"""
        val protoResult = client.push("epoch-old", sampleOp)
        assertIs<ApiResult.Failure>(protoResult)
        assertIs<SafeError.ProtocolIncompatible>(protoResult.error)

        // 503 notAdmitted -> Network (Service Unavailable)
        statusCode = HttpStatusCode.ServiceUnavailable
        responseBody = """{"error":"Admission incomplete","code":"notAdmitted"}"""
        val admitResult = client.push("epoch-old", sampleOp)
        assertIs<ApiResult.Failure>(admitResult)
        assertIs<SafeError.Network>(admitResult.error)
    }

    @Test
    fun testPullSuccessAndPagination() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v1",
                            "epoch":"epoch-1",
                            "records":[
                                {
                                    "tableName":"todos",
                                    "rowId":"row-1",
                                    "revision":1,
                                    "deleted":false,
                                    "data":{
                                        "id":"row-1",
                                        "title":"First todo",
                                        "completed":false,
                                        "createdAt":1700000000000
                                    }
                                }
                            ],
                            "cursor":"token-cursor-1",
                            "hasMore":true
                        }"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        val result = client.pull("epoch-1", cursor = null, batchSize = 100)
        assertIs<ApiResult.Success<StrictPullResponseDto>>(result)
        val pull = result.value
        assertEquals(1, pull.records.size)
        assertEquals("First todo", pull.records[0].data?.title)
        assertEquals("token-cursor-1", pull.cursor)
        assertTrue(pull.hasMore)
    }

    @Test
    fun testPullTombstoneRecordWithEmptyData() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v1",
                            "epoch":"epoch-1",
                            "records":[
                                {
                                    "tableName":"todos",
                                    "rowId":"deleted-row-1",
                                    "revision":2,
                                    "deleted":true,
                                    "data":{}
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

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        val result = client.pull("epoch-1", cursor = null, batchSize = 100)
        assertIs<ApiResult.Success<StrictPullResponseDto>>(result)
        val pull = result.value
        assertEquals(1, pull.records.size)
        assertTrue(pull.records[0].deleted)
        assertEquals("deleted-row-1", pull.records[0].rowId)
        assertEquals(2L, pull.records[0].revision)
        assertFalse(pull.hasMore)
    }

    @Test
    fun testBootstrapAcceptsStrictV2() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/bootstrap" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v2","appId":"tabby-rush","epoch":"epoch-v2","tables":["todos"]}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        val result = client.bootstrap()
        assertIs<ApiResult.Success<BootstrapResponseDto>>(result)
        assertEquals("strict-v2", result.value.protocol)
        assertEquals("epoch-v2", result.value.epoch)
    }

    @Test
    fun testPushV2Success() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/push" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v2","epoch":"epoch-v2","results":[{"opId":"op-v2-1","rowId":"row-v2-1","digest":"dig-1","status":"accepted","acceptedRevision":1}]}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        val ops = listOf(
            StrictV2OperationDto(
                opId = "op-v2-1",
                epoch = "epoch-v2",
                tableName = "todos",
                rowId = "row-v2-1",
                kind = "create",
                expectedRevision = 0L,
                data = StrictV2TodoDataDto(
                    id = "row-v2-1",
                    title = "V2 Task with Deadline",
                    completed = false,
                    createdAt = 1700000000000L,
                    deadlineDate = "2026-11-01",
                    deadlineTime = "14:00",
                    deadlineZone = "America/New_York",
                    deadlineOffsetSeconds = -18000L,
                    deadlineAnchorEpochMs = 1793559600000L,
                    deadlineOverdueEpochMs = 1793559600000L,
                    reminderEnabled = true,
                    reminderLeadMinutes = 30L,
                    reminderCount = 1L,
                    scheduleToken = "sched-token-v2"
                )
            )
        )

        val result = client.pushV2("epoch-v2", ops)
        assertIs<ApiResult.Success<StrictPushResponseDto>>(result)
        val push = result.value
        assertEquals("strict-v2", push.protocol)
        assertEquals("epoch-v2", push.epoch)
        assertEquals(1, push.results.size)
        assertEquals("accepted", push.results[0].status)
        assertEquals(1L, push.results[0].acceptedRevision)
    }

    @Test
    fun testPullV2SuccessWithTemporalFields() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/pull" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v2",
                            "epoch":"epoch-v2",
                            "records":[
                                {
                                    "tableName":"todos",
                                    "rowId":"row-v2-1",
                                    "revision":1,
                                    "deleted":false,
                                    "data":{
                                        "id":"row-v2-1",
                                        "title":"V2 Task with Deadline",
                                        "completed":false,
                                        "createdAt":1700000000000,
                                        "deadlineDate":"2026-11-01",
                                        "deadlineTime":"14:00",
                                        "deadlineZone":"America/New_York",
                                        "deadlineOffsetSeconds":-18000,
                                        "deadlineAnchorEpochMs":1793559600000,
                                        "deadlineOverdueEpochMs":1793559600000,
                                        "reminderEnabled":true,
                                        "reminderLeadMinutes":30,
                                        "reminderCount":1,
                                        "scheduleToken":"sched-token-v2"
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

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        val result = client.pull("epoch-v2", protocol = StrictSyncV2Contract.PROTOCOL_VERSION)
        assertIs<ApiResult.Success<StrictPullResponseDto>>(result)
        val pull = result.value
        assertEquals("strict-v2", pull.protocol)
        assertEquals(1, pull.records.size)
        val data = pull.records[0].data
        assertNotNull(data)
        assertEquals("2026-11-01", data.deadlineDate)
        assertEquals("14:00", data.deadlineTime)
        assertEquals("America/New_York", data.deadlineZone)
        assertTrue(data.reminderEnabled)
        assertEquals(30L, data.reminderLeadMinutes)
        assertEquals("sched-token-v2", data.scheduleToken)
    }

    @Test
    fun testLegacyReceiptsSuccess() = runTest {
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/strong/legacy-receipts" -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{
                            "protocol":"strict-v1",
                            "epoch":"epoch-1",
                            "results":[
                                {
                                    "opId":"op-1",
                                    "rowId":"row-1",
                                    "status":"provenAccepted",
                                    "acceptedRevision":2
                                },
                                {
                                    "opId":"op-2",
                                    "rowId":"row-2",
                                    "status":"provenNotAccepted",
                                    "current":{"revision":1,"deleted":false}
                                },
                                {
                                    "opId":"op-3",
                                    "rowId":"row-3",
                                    "status":"unknownSuperseded",
                                    "current":{"revision":5,"deleted":false}
                                },
                                {
                                    "opId":"op-4",
                                    "rowId":"row-4",
                                    "status":"integrityFailure"
                                }
                            ]
                        }"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = createAuthenticatedSession(engine)
        val client = HubSyncClient(config, session)

        val items = listOf(
            LegacyReceiptItemDto("op-1", "todos", "row-1", "update", 1L, "dig-1", StrictTodoDataDto("row-1", "Title", false, 1000L)),
            LegacyReceiptItemDto("op-2", "todos", "row-2", "update", 1L, "dig-2", StrictTodoDataDto("row-2", "Title 2", false, 1000L)),
            LegacyReceiptItemDto("op-3", "todos", "row-3", "update", 1L, "dig-3", StrictTodoDataDto("row-3", "Title 3", false, 1000L)),
            LegacyReceiptItemDto("op-4", "todos", "row-4", "update", 1L, "bad-dig", StrictTodoDataDto("row-4", "Title 4", false, 1000L))
        )

        val result = client.legacyReceipts("epoch-1", items)
        assertIs<ApiResult.Success<LegacyReceiptsResponseDto>>(result)
        val resp = result.value
        assertEquals("strict-v1", resp.protocol)
        assertEquals("epoch-1", resp.epoch)
        assertEquals(4, resp.results.size)
        assertEquals(LegacyReceiptStatus.PROVEN_ACCEPTED, resp.results[0].status)
        assertEquals(2L, resp.results[0].acceptedRevision)
        assertEquals(LegacyReceiptStatus.PROVEN_NOT_ACCEPTED, resp.results[1].status)
        assertEquals(LegacyReceiptStatus.UNKNOWN_SUPERSEDED, resp.results[2].status)
        assertEquals(LegacyReceiptStatus.INTEGRITY_FAILURE, resp.results[3].status)
    }

    @Test
    fun testLegacyReceiptsBatchTooLarge() = runTest {
        val session = createAuthenticatedSession(TestHttpEngine { Pair(HttpStatusCode.OK, "{}") })
        val client = HubSyncClient(config, session)
        val over100 = (1..101).map { i ->
            LegacyReceiptItemDto("op-$i", "todos", "row-$i", "update", 1L, "dig-$i")
        }
        val result = client.legacyReceipts("epoch-1", over100)
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.Validation>(result.error)
        assertEquals("BATCH_TOO_LARGE", result.error.code)
    }
}
