package dev.doggo.shared.auth

import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.SafeError
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpProtocolVersion
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.utils.io.InternalAPI
@OptIn(InternalAPI::class)
class TestHttpEngine(
    private val handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>
) : HttpClientEngineBase("TestHttpEngine") {
    override val config: HttpClientEngineConfig = HttpClientEngineConfig()
    override val supportedCapabilities = setOf(io.ktor.client.plugins.HttpTimeoutCapability)
    val recordedRequests = mutableListOf<HttpRequestData>()
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        recordedRequests.add(data)
        val (statusCode, responseBody) = handler(data)
        val bytes = responseBody.encodeToByteArray()
        val responseHeaders = Headers.build {
            append(HttpHeaders.ContentType, "application/json")
            append(HttpHeaders.ContentLength, bytes.size.toString())
        }

        return HttpResponseData(
            statusCode = statusCode,
            requestTime = GMTDate(),
            headers = responseHeaders,
            version = HttpProtocolVersion.HTTP_1_1,
            body = ByteReadChannel(bytes),
            callContext = callContext()
        )
    }
}

class HubAuthClientTest {

    private val debugConfig = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "doggo",
        appApiKey = "test-doggo-key",
        buildMode = BuildMode.DEBUG
    )

    @Test
    fun testValidationHelpers() {
        // Email normalization
        assertEquals("user@example.com", HubAuthValidation.normalizeEmail("  USER@Example.COM  "))
        assertTrue(HubAuthValidation.validateEmail("test@example.com"))
        assertFalse(HubAuthValidation.validateEmail("invalid-email"))
        assertFalse(HubAuthValidation.validateEmail("test@"))

        // Username validation (3..32 chars, alphanumeric, _, -)
        assertTrue(HubAuthValidation.validateUsername("valid_user-1"))
        assertFalse(HubAuthValidation.validateUsername("ab")) // too short (< 3)
        assertFalse(HubAuthValidation.validateUsername("a".repeat(33))) // too long (> 32)
        assertFalse(HubAuthValidation.validateUsername("invalid user!")) // special char

        // Password validation (8..128 UTF-8 bytes, transient, never trimmed)
        assertTrue(HubAuthValidation.validatePassword("12345678"))
        assertFalse(HubAuthValidation.validatePassword("short")) // < 8
        assertFalse(HubAuthValidation.validatePassword("a".repeat(129))) // > 128
    }

    @Test
    fun testRegisterSuccessAndHeaders() = runTest {
        var recordedAuthHeader: String? = null
        var recordedApiKey: String? = null

        val engine = TestHttpEngine { request ->
            recordedAuthHeader = request.headers[HttpHeaders.Authorization]
            recordedApiKey = request.headers["X-API-Key"]

            Pair(
                HttpStatusCode.OK,
                """{"userId":"usr-123","accessToken":"jwt-access-token","refreshToken":"refresh-token-val"}"""
            )
        }

        val client = HubAuthClient(debugConfig, engine)
        val result = client.register("newuser", "user@example.com", "securePassword123")

        assertIs<ApiResult.Success<RegisterResponse>>(result)
        assertEquals("usr-123", result.value.userId)
        assertEquals("jwt-access-token", result.value.accessToken)
        assertEquals("refresh-token-val", result.value.refreshToken)

        // Public auth route MUST NOT include Authorization or X-API-Key headers
        assertEquals(null, recordedAuthHeader)
        assertEquals(null, recordedApiKey)
    }

    @Test
    fun testLoginSuccessAndErrorHandling() = runTest {
        val successEngine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{"userId":"usr-456","accessToken":"access-token-456","refreshToken":"ref-456","apps":["doggo"],"isAdmin":false}"""
            )
        }

        val successClient = HubAuthClient(debugConfig, successEngine)
        val result = successClient.login("user@example.com", "password123")

        assertIs<ApiResult.Success<LoginResponse>>(result)
        assertEquals("usr-456", result.value.userId)
        assertTrue(result.value.apps.contains("doggo"))
        assertFalse(result.value.isAdmin)

        // Unauthorized failure
        val failureEngine = TestHttpEngine {
            Pair(HttpStatusCode.Unauthorized, """{"error":"Invalid credentials"}""")
        }
        val failureClient = HubAuthClient(debugConfig, failureEngine)
        val failResult = failureClient.login("user@example.com", "wrong-password")

        assertIs<ApiResult.Failure>(failResult)
        assertIs<SafeError.Unauthorized>(failResult.error)
        assertEquals("UNAUTHORIZED", failResult.error.code)
    }

    @Test
    fun testRefreshSuccess() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{"accessToken":"new-access-jwt","refreshToken":"new-refresh-token"}"""
            )
        }

        val client = HubAuthClient(debugConfig, engine)
        val result = client.refresh("old-refresh-token")

        assertIs<ApiResult.Success<RefreshResponse>>(result)
        assertEquals("new-access-jwt", result.value.accessToken)
        assertEquals("new-refresh-token", result.value.refreshToken)
    }

    @Test
    fun testGetMeWithBearerToken() = runTest {
        var recordedBearer: String? = null

        val engine = TestHttpEngine { request ->
            recordedBearer = request.headers[HttpHeaders.Authorization]
            Pair(
                HttpStatusCode.OK,
                """{"userId":"usr-789","username":"testuser","email":"test@example.com","apps":["doggo"],"isAdmin":false,"notificationsAllowed":true,"createdAt":1700000000000}"""
            )
        }

        val client = HubAuthClient(debugConfig, engine)
        val result = client.getMe("my-access-token")

        assertIs<ApiResult.Success<MeResponse>>(result)
        assertEquals("usr-789", result.value.userId)
        assertEquals("testuser", result.value.username)
        assertEquals("test@example.com", result.value.email)
        assertTrue(result.value.hasDoggoAccess("doggo"))
        assertEquals("Bearer my-access-token", recordedBearer)
    }

    @Test
    fun testAccessRequestCreationAndListing() = runTest {
        val engine = TestHttpEngine { request ->
            if (request.url.encodedPath == "/api/v1/apps/access-requests") {
                Pair(
                    HttpStatusCode.Created,
                    """{"id":"req-1","userId":"usr-789","username":"testuser","appId":"doggo","message":"please","status":"pending","createdAt":1700000001000,"updatedAt":1700000001000}"""
                )
            } else {
                Pair(
                    HttpStatusCode.OK,
                    """[{"id":"req-1","userId":"usr-789","username":"testuser","appId":"doggo","message":"please","status":"pending","createdAt":1700000001000,"updatedAt":1700000001000}]"""
                )
            }
        }

        val client = HubAuthClient(debugConfig, engine)
        val createResult = client.requestAccess("token-123", "doggo", "please")
        assertIs<ApiResult.Success<AccessRequestDto>>(createResult)
        assertEquals("pending", createResult.value.status)

        val listResult = client.getAccessRequests("token-123")
        assertIs<ApiResult.Success<List<AccessRequestDto>>>(listResult)
        assertEquals(1, listResult.value.size)
        assertEquals("doggo", listResult.value[0].appId)
    }

    @Test
    fun testEndpointAllowlistAndDualAuthHeaders() = runTest {
        var recordedApiKey: String? = null
        var recordedAppId: String? = null

        val engine = TestHttpEngine { request ->
            recordedApiKey = request.headers["X-API-Key"]
            recordedAppId = request.headers["X-App-Id"]
            Pair(HttpStatusCode.OK, """{"protocol":"strict-v1","appId":"doggo","epoch":"epoch-1","tables":["todos"]}""")
        }

        val client = HubAuthClient(debugConfig, engine)

        // Strict endpoint receives dual auth headers
        val strictResult = client.httpClient.executeRaw(
            method = HttpMethod.Post,
            path = "/api/v1/sync/doggo/strong/bootstrap",
            accessToken = "token-abc"
        )
        assertIs<ApiResult.Success<*>>(strictResult)
        assertEquals("test-doggo-key", recordedApiKey)
        assertEquals("doggo", recordedAppId)

        // Legacy / non-allowlisted endpoint is rejected locally
        val legacyResult = client.httpClient.executeRaw(
            method = HttpMethod.Get,
            path = "/api/v1/sync/doggo"
        )
        assertIs<ApiResult.Failure>(legacyResult)
        assertIs<SafeError.ProtocolIncompatible>(legacyResult.error)
        assertEquals("DISALLOWED_ENDPOINT", legacyResult.error.code)
    }

    @Test
    fun testSanitizeErrorMessageStripsHtml() {
        val client = HubAuthClient(debugConfig)
        val rawHtml = "<html><head><title>502 Bad Gateway</title></head><body><h1>Bad Gateway</h1><p>The server encountered a temporary error.</p></body></html>"
        val sanitized = client.httpClient.sanitizeErrorMessage(rawHtml)
        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertTrue(sanitized.contains("Bad Gateway"))
    }
}
