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
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.StrictSyncContract
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HubSyncClientTest {

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
                "/api/v1/sync/doggo/strong/bootstrap" -> {
                    // Verify Dual-Auth headers
                    assertEquals("Bearer valid-access-token", request.headers["Authorization"])
                    assertEquals("doggo-secret-key", request.headers["X-API-Key"])
                    assertEquals("doggo", request.headers["X-App-Id"])
                    Pair(
                        HttpStatusCode.OK,
                        """{"protocol":"strict-v1","appId":"doggo","epoch":"epoch-uuid-1","tables":["todos"]}"""
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
        assertEquals("doggo", bootstrap.appId)
        assertEquals("epoch-uuid-1", bootstrap.epoch)
        assertEquals(listOf("todos"), bootstrap.tables)
    }

    @Test
    fun testBootstrapProtocolMismatchFails() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{"protocol":"strict-v0","appId":"doggo","epoch":"epoch-uuid-1","tables":["todos"]}"""
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
                "/api/v1/sync/doggo/strong/push" -> {
                    assertEquals("doggo-secret-key", request.headers["X-API-Key"])
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
                "/api/v1/sync/doggo/strong/pull" -> {
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
}
