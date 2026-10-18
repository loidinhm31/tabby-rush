package dev.tabbyrush.shared.remote

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.RemoteBindingResult
import dev.tabbyrush.shared.contract.RemoteBindingStatus
import dev.tabbyrush.shared.contract.RemoteInboxItem
import dev.tabbyrush.shared.contract.RemoteInboxPage
import dev.tabbyrush.shared.contract.RemoteReadResult
import dev.tabbyrush.shared.contract.RemoteUnreadResponse
import dev.tabbyrush.shared.contract.SafeError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteInboxClientTest {

    private val config = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "tabby-rush",
        appApiKey = "test-tabbyrush-key",
        buildMode = BuildMode.DEBUG
    )

    private val accountIdentity = AccountIdentity("usr-123", "testuser", "test@example.com")
    private val accountKey = AccountKey.create(config.canonicalOrigin, accountIdentity.userId)

    private suspend fun createTestClient(
        handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>
    ): Pair<RemoteInboxClient, TestHttpEngine> {
        val engine = TestHttpEngine(handler)
        val vault = FakeCredentialVault()
        vault.store(accountKey, CredentialBundle("initial-access-token", "refresh-token"))

        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(accountIdentity, accountKey)

        val httpClient = HubHttpClient(config, engine)
        val client = RemoteInboxClient(httpClient, session)
        return Pair(client, engine)
    }

    @Test
    fun testFetchInboxPathAndHeaders() = runTest {
        val (client, engine) = createTestClient { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("Bearer initial-access-token", request.headers["Authorization"])
            assertNull(request.headers["X-API-Key"])
            assertNull(request.headers["X-App-Id"])
            Pair(
                HttpStatusCode.OK,
                """{"items":[{"id":"baf6dc3e-3099-4ae0-8767-985552ef0b82","appId":"tabby-rush","title":"Title","body":"Body","createdAt":"2026-10-18T00:00:00.000Z","expiresAt":"2027-01-16T00:00:00.000Z","readAt":null}],"nextCursor":null,"serverNow":"2026-10-18T00:00:01.000Z"}"""
            )
        }

        val result = client.fetchInbox(limit = 25, cursor = null)
        assertIs<ApiResult.Success<RemoteInboxPage>>(result)
        val page = result.value
        assertEquals(1, page.items.size)
        assertEquals("baf6dc3e-3099-4ae0-8767-985552ef0b82", page.items[0].id)
        assertEquals(1, engine.recordedRequests.size)
        assertTrue(engine.recordedRequests[0].url.encodedPathAndQuery.contains("limit=25"))
    }

    @Test
    fun testFetchUnread() = runTest {
        val (client, _) = createTestClient { _ ->
            Pair(HttpStatusCode.OK, """{"unreadCount":3,"serverNow":"2026-10-18T00:00:01.000Z"}""")
        }

        val result = client.fetchUnread()
        assertIs<ApiResult.Success<RemoteUnreadResponse>>(result)
        assertEquals(3, result.value.unreadCount)
        assertEquals("2026-10-18T00:00:01.000Z", result.value.serverNow)
    }

    @Test
    fun testFetchDetail() = runTest {
        val (client, _) = createTestClient { _ ->
            Pair(
                HttpStatusCode.OK,
                """{"id":"baf6dc3e-3099-4ae0-8767-985552ef0b82","appId":"tabby-rush","title":"Notice","body":"Text","createdAt":"2026-10-18T00:00:00.000Z","expiresAt":"2027-01-16T00:00:00.000Z","readAt":null}"""
            )
        }

        val result = client.fetchDetail("baf6dc3e-3099-4ae0-8767-985552ef0b82")
        assertIs<ApiResult.Success<RemoteInboxItem>>(result)
        assertEquals("baf6dc3e-3099-4ae0-8767-985552ef0b82", result.value.id)
        assertEquals("Notice", result.value.title)
    }

    @Test
    fun testMarkReadSendsEmptyJsonObject() = runTest {
        val (client, engine) = createTestClient { request ->
            assertEquals(HttpMethod.Put, request.method)
            val bodyText = (request.body as? io.ktor.client.request.forms.FormDataContent)?.toString()
                ?: (request.body as? io.ktor.http.content.TextContent)?.text
            assertEquals("{}", bodyText)
            Pair(
                HttpStatusCode.OK,
                """{"id":"baf6dc3e-3099-4ae0-8767-985552ef0b82","readAt":"2026-10-18T00:05:00.000Z","unreadCount":2,"serverNow":"2026-10-18T00:05:00.000Z"}"""
            )
        }

        val result = client.markRead("baf6dc3e-3099-4ae0-8767-985552ef0b82")
        assertIs<ApiResult.Success<RemoteReadResult>>(result)
        assertEquals("baf6dc3e-3099-4ae0-8767-985552ef0b82", result.value.id)
        assertEquals(2, result.value.unreadCount)
        assertEquals(1, engine.recordedRequests.size)
    }

    @Test
    fun testBindInstallSendsProofAndBody() = runTest {
        val (client, engine) = createTestClient { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("test-install-proof-43-chars-base64url==", request.headers["X-Tabby-Install-Proof"])
            Pair(
                HttpStatusCode.OK,
                """{"installId":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","operationGeneration":"17","bindingGeneration":"17","state":"bound","replayed":false}"""
            )
        }

        val req = BindRequest(
            operationGeneration = "17",
            platform = "android",
            token = "fcm-token-xyz",
            appVersion = "1.0.0"
        )
        val result = client.bindInstall("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "test-install-proof-43-chars-base64url==", req)
        assertIs<ApiResult.Success<RemoteBindingResult>>(result)
        assertEquals("bound", result.value.state)
        assertEquals("17", result.value.bindingGeneration)
        assertFalse(result.value.replayed)
        assertEquals(1, engine.recordedRequests.size)
    }

    @Test
    fun testUnbindInstallSendsProofAndBody() = runTest {
        val (client, engine) = createTestClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("test-install-proof-43-chars-base64url==", request.headers["X-Tabby-Install-Proof"])
            Pair(
                HttpStatusCode.OK,
                """{"installId":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","operationGeneration":"18","bindingGeneration":"18","state":"unbound","replayed":false}"""
            )
        }

        val req = UnbindRequest(
            operationGeneration = "18",
            expectedBindingGeneration = "17"
        )
        val result = client.unbindInstall("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "test-install-proof-43-chars-base64url==", req)
        assertIs<ApiResult.Success<RemoteBindingResult>>(result)
        assertEquals("unbound", result.value.state)
        assertEquals("18", result.value.bindingGeneration)
        assertEquals(1, engine.recordedRequests.size)
    }

    @Test
    fun testGetInstallBinding() = runTest {
        val (client, engine) = createTestClient { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("proof-value", request.headers["X-Tabby-Install-Proof"])
            Pair(
                HttpStatusCode.OK,
                """{"installId":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","currentGeneration":"18","state":"unbound","ownedByCurrentUser":false,"lastOperationMatchesCurrentUser":true}"""
            )
        }

        val result = client.getInstallBinding("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "proof-value")
        assertIs<ApiResult.Success<RemoteBindingStatus>>(result)
        assertEquals("unbound", result.value.state)
        assertFalse(result.value.ownedByCurrentUser)
        assertTrue(result.value.lastOperationMatchesCurrentUser)
        assertEquals(1, engine.recordedRequests.size)
    }

    @Test
    fun testSafeRetryOnTokenRefreshed() = runTest {
        var callCount = 0
        val (client, _) = createTestClient { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/auth/refresh") -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"accessToken":"refreshed-token","refreshToken":"new-refresh-token","expiresIn":3600}"""
                    )
                }
                path.endsWith("/auth/me") -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"userId":"usr-123","username":"testuser","email":"test@example.com","apps":["tabby-rush"],"isAdmin":false,"notificationsAllowed":true,"createdAt":1700000000000}"""
                    )
                }
                path.endsWith("/announcements/unread") -> {
                    callCount++
                    if (callCount == 1) {
                        // First attempt fails with 401
                        Pair(HttpStatusCode.Unauthorized, """{"error":"Token expired"}""")
                    } else {
                        // Second attempt after refresh succeeds!
                        assertEquals("Bearer refreshed-token", request.headers["Authorization"])
                        Pair(HttpStatusCode.OK, """{"unreadCount":5,"serverNow":"2026-10-18T00:00:02.000Z"}""")
                    }
                }
                else -> Pair(HttpStatusCode.NotFound, "{}")
            }
        }

        val result = client.fetchUnread()
        assertIs<ApiResult.Success<RemoteUnreadResponse>>(result)
        assertEquals(5, result.value.unreadCount)
        assertEquals(2, callCount)
    }

    @Test
    fun testAnnouncement409NotEpochMismatch() = runTest {
        val (client, _) = createTestClient { _ ->
            Pair(
                HttpStatusCode.Conflict,
                """{"error":{"code":"staleInstallOperation","message":"Stale generation","retryable":false}}"""
            )
        }

        val result = client.markRead("baf6dc3e-3099-4ae0-8767-985552ef0b82")
        assertIs<ApiResult.Failure>(result)
        // Must NOT be SafeError.ResetRequired(EPOCH_MISMATCH)!
        assertFalse(result.error is SafeError.ResetRequired)
        assertIs<SafeError.Validation>(result.error)
        assertEquals("staleInstallOperation", result.error.code)
    }

    @Test
    fun testAppAccessRevoked403Handled() = runTest {
        val (client, _) = createTestClient { _ ->
            Pair(
                HttpStatusCode.Forbidden,
                """{"error":{"code":"appAccessRevoked","message":"Application access revoked","retryable":false}}"""
            )
        }

        val result = client.fetchUnread()
        assertIs<ApiResult.Failure>(result)
        assertEquals("appAccessRevoked", result.error.code)
    }
}
