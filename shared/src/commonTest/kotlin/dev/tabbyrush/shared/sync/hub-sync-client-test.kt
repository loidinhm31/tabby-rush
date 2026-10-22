package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.auth.RouteClassification
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class HubSyncClientTest {
    private val config = HubConfig("http://localhost:3100", "tabby-rush", "disposable-test-key", BuildMode.DEBUG)
    private suspend fun session(engine: TestHttpEngine): HubAuthSession {
        val vault = FakeCredentialVault()
        val key = AccountKey.create(config.canonicalOrigin, "test-user")
        vault.store(key, CredentialBundle("test-access", "test-refresh"))
        return HubAuthSession(config, vault, engine).also {
            it.restoreSession(AccountIdentity("test-user", "tester", "test@example.com"), key)
        }
    }

    @Test fun onlyThreeExactPathsAdmitDualAuthentication() = runTest {
        var requests = 0
        val client = HubHttpClient(config, TestHttpEngine { requests++; HttpStatusCode.OK to "{}" })
        for (route in listOf("push", "pull", "delta")) {
            assertEquals(RouteClassification.DUAL_AUTH_SYNC, client.classifyRoute("/api/v1/sync/tabby-rush/$route"))
            assertIs<ApiResult.Failure>(client.executeRaw(HttpMethod.Get, "/api/v1/sync/tabby-rush/$route"))
        }
        for (path in listOf("api/v1/sync/tabby-rush/push", "/api/v1/sync/tabby-rush/strong/push", "/api/v1/sync/other/push",
            "/api/v1/sync/tabby-rush/push/", "/api/v1/sync/tabby-rush/push?", "/api/v1/sync/tabby-rush/push?a=b",
            "https://foreign.example/api/v1/sync/tabby-rush/push", "/api/v1/sync/tabby-rush/push#fragment")) {
            assertNull(client.classifyRoute(path), path)
        }
        assertEquals(0, requests)
    }

    @Test fun deltaObservesIndependentNestedSchemaVersionsAndHeaders() = runTest {
        val engine = TestHttpEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/sync/tabby-rush/delta", request.url.encodedPath)
            assertEquals("Bearer test-access", request.headers["Authorization"])
            assertEquals("disposable-test-key", request.headers["X-API-Key"])
            assertEquals("tabby-rush", request.headers["X-App-Id"])
            HttpStatusCode.OK to """{"push":{"synced":0,"conflicts":[],"syncedRecords":[],"serverTimestamp":"2026-10-20T00:00:00Z","schemaVersion":2},"pull":{"records":[],"checkpoint":{"updatedAt":"2026-10-20T00:00:00.123456789Z","id":"tie"},"serverTimestamp":"2026-10-20T00:00:00Z","hasMore":false,"schemaVersion":3}}"""
        }
        val result = HubSyncClient(config, session(engine)).delta(DeltaRequestDto(PushRequestDto(emptyList()), PullRequestDto()))
        assertIs<ApiResult.Success<DeltaResponseDto>>(result)
        assertEquals(2L, result.value.push?.schemaVersion)
        assertEquals(3L, result.value.pull?.schemaVersion)
        assertEquals("2026-10-20T00:00:00.123456789Z", result.value.pull?.checkpoint?.updatedAt)
    }

    @Test fun missingSchemaVersionFailsWithoutGuessingBaseline() = runTest {
        val engine = TestHttpEngine { HttpStatusCode.OK to """{"records":[],"checkpoint":{"updatedAt":"2026-10-20T00:00:00Z","id":""},"serverTimestamp":"2026-10-20T00:00:00Z"}""" }
        assertIs<ApiResult.Failure>(HubSyncClient(config, session(engine)).pull())
    }

    @Test fun invalidMetadataKindsNeverCoerceToSchemaCounter() = runTest {
        for (value in listOf("\"1\"", "0", "-1", "1.0", "1e0", "9007199254740992", "null")) {
            val engine = TestHttpEngine { HttpStatusCode.OK to """{"records":[],"checkpoint":{"updatedAt":"2026-10-20T00:00:00Z","id":""},"serverTimestamp":"2026-10-20T00:00:00Z","schemaVersion":$value}""" }
            assertIs<ApiResult.Failure>(HubSyncClient(config, session(engine)).pull(), value)
        }
    }

    @Test fun validPushCanBeCommittedWhenDeltaPullCannotDecode() = runTest {
        val engine = TestHttpEngine { HttpStatusCode.OK to """{"push":{"synced":1,"conflicts":[],"syncedRecords":[{"tableName":"todos","rowId":"a","version":1}],"serverTimestamp":"2026-10-20T00:00:00Z","schemaVersion":2},"pull":{"records":"invalid","schemaVersion":3}}""" }
        val result = HubSyncClient(config, session(engine)).delta(DeltaRequestDto(PushRequestDto(emptyList()), PullRequestDto()))
        assertIs<ApiResult.Success<DeltaResponseDto>>(result)
        assertEquals(listOf(SyncedRecordDto("todos", "a", 1L)), result.value.push?.syncedRecords)
        assertNull(result.value.pull)
        assertNotNull(result.value.pullError)
    }

    @Test fun refreshedAuthDoesNotReplayStandardDeltaPost() = runTest {
        var deltaCalls = 0
        var refreshCalls = 0
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sync/tabby-rush/delta" -> { deltaCalls++; HttpStatusCode.Unauthorized to """{"error":"Expired token"}""" }
                "/api/v1/auth/refresh" -> { refreshCalls++; HttpStatusCode.OK to """{"accessToken":"new-access","refreshToken":"new-refresh"}""" }
                "/api/v1/auth/me" -> HttpStatusCode.OK to """{"userId":"test-user","username":"test","email":"test@example.com","apps":["tabby-rush"],"isAdmin":false}"""
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        val result = HubSyncClient(config, session(engine)).delta(DeltaRequestDto(PushRequestDto(emptyList()), PullRequestDto()))
        assertIs<ApiResult.Failure>(result)
        assertEquals("TOKEN_REFRESHED", result.error.code)
        assertEquals(1, deltaCalls)
        assertEquals(1, refreshCalls)
    }

    @Test fun capturedClientCannotSendOldRowsWithAnotherAccountsToken() = runTest {
        var calls = 0
        val engine = TestHttpEngine { calls++; HttpStatusCode.OK to "{}" }
        val auth = session(engine)
        val client = HubSyncClient(config, auth)
        val otherKey = AccountKey.create(config.canonicalOrigin, "other-user")
        auth.vault.store(otherKey, CredentialBundle("other-access", "other-refresh"))
        auth.restoreSession(AccountIdentity("other-user", "other", "other@example.com"), otherKey)
        val result = client.pull()
        assertIs<ApiResult.Failure>(result)
        assertEquals("ACCOUNT_FENCED", result.error.code)
        assertEquals(0, calls)
    }

    @Test fun versionExhaustionRejectsBeforeNetwork() = runTest {
        var calls = 0
        val engine = TestHttpEngine { calls++; HttpStatusCode.OK to "{}" }
        val result = HubSyncClient(config, session(engine)).push(listOf(PushRecordDto(rowId = "a", data = JsonObject(emptyMap()),
            version = Long.MAX_VALUE, assumedServerVersion = MAX_EXACT_VERSION)))
        assertIs<ApiResult.Failure>(result)
        assertEquals(0, calls)
    }

    @Test fun testPushIncludesCollaborationVersion() = runTest {
        var recordedBody: String? = null
        val engine = TestHttpEngine { req ->
            recordedBody = when (val b = req.body) {
                is io.ktor.http.content.TextContent -> b.text
                is io.ktor.http.content.ByteArrayContent -> b.bytes().decodeToString()
                else -> b.toString()
            }
            Pair(HttpStatusCode.OK, """{"synced":1,"conflicts":[],"serverTimestamp":"2026-10-21T00:00:00Z","schemaVersion":1,"collaborationVersion":1}""")
        }
        val client = HubSyncClient(config, session(engine))
        val result = client.push(
            listOf(
                PushRecordDto(
                    tableName = "todos",
                    rowId = "t1",
                    data = JsonObject(emptyMap()),
                    version = 2L,
                    assumedServerVersion = 1L,
                    ownerUserId = "owner-1"
                )
            )
        )
        assertIs<ApiResult.Success<PushResponseDto>>(result)
        assertNotNull(recordedBody)
        assertTrue(recordedBody!!.contains("\"collaborationVersion\":1"))
    }

    @Test fun testUpgradeRequiredMappedToCollaborationRequired() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.UpgradeRequired,
                """{"error":{"code":"COLLABORATION_REQUIRED","message":"Upgrade required for collaboration"}}"""
            )
        }
        val client = HubSyncClient(config, session(engine))
        val result = client.pull()
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.CollaborationRequired>(result.error)
        assertEquals("COLLABORATION_REQUIRED", result.error.code)
    }

    @Test fun testAccessRevisionChangedMappedFrom409() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.Conflict,
                """{"error":{"code":"ACCESS_REVISION_CHANGED","message":"Revision changed"}}"""
            )
        }
        val client = HubSyncClient(config, session(engine))
        val result = client.pull()
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.AccessRevisionChanged>(result.error)
        assertEquals("ACCESS_REVISION_CHANGED", result.error.code)
    }

    @Test fun testDuplicateCanonicalPushRecordsRejectedBeforeNetwork() = runTest {
        var calls = 0
        val engine = TestHttpEngine { calls++; HttpStatusCode.OK to "{}" }
        val client = HubSyncClient(config, session(engine))
        val result = client.push(
            listOf(
                PushRecordDto(tableName = "todos", rowId = "r1", data = JsonObject(emptyMap()), version = 2L, assumedServerVersion = 1L, ownerUserId = "alice"),
                PushRecordDto(tableName = "todos", rowId = "r1", data = JsonObject(emptyMap()), version = 3L, assumedServerVersion = 2L, ownerUserId = "alice")
            )
        )
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.Validation>(result.error)
        assertEquals("INVALID_BATCH", result.error.code)
        assertEquals(0, calls)
    }

    @Test fun testPullResponseValidationDetectsDuplicateAccessResults() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "records": [],
                    "checkpoint": {"updatedAt":"2026-10-21T00:00:00Z","id":"cp1"},
                    "serverTimestamp": "2026-10-21T00:00:00Z",
                    "hasMore": false,
                    "schemaVersion": 1,
                    "collaborationVersion": 1,
                    "accessRevision": 5,
                    "accessResults": [
                        {"ref":{"ownerUserId":"u1","tableName":"todos","rowId":"r1"},"status":"accessible"},
                        {"ref":{"ownerUserId":"u1","tableName":"todos","rowId":"r1"},"status":"accessible"}
                    ]
                }"""
            )
        }
        val client = HubSyncClient(config, session(engine))
        val result = client.pull()
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
    }
}
