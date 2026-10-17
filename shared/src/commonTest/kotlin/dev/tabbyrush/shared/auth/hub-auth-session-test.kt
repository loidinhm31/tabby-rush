package dev.tabbyrush.shared.auth

import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.SafeError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeCredentialVault : dev.tabbyrush.shared.contract.CredentialVault {
    var storedBundle: CredentialBundle? = null
    var shouldFailStore: Boolean = false
    override var status: dev.tabbyrush.shared.contract.VaultStatus = dev.tabbyrush.shared.contract.VaultStatus.READY

    override suspend fun load(accountKey: AccountKey): CredentialBundle? {
        return storedBundle
    }

    override suspend fun store(accountKey: AccountKey, bundle: CredentialBundle) {
        if (shouldFailStore) {
            throw IllegalStateException("Simulated vault disk write failure")
        }
        storedBundle = bundle
    }

    override suspend fun clear(accountKey: AccountKey) {
        storedBundle = null
    }
}

class HubAuthSessionTest {

    private val debugConfig = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "tabby-rush",
        appApiKey = "test-key",
        buildMode = BuildMode.DEBUG
    )

    @Test
    fun testLoginSuccessWithTabbyRushAccess() = runTest {
        val vault = FakeCredentialVault()
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-1","accessToken":"access-1","refreshToken":"ref-1","apps":["tabby-rush"]}"""
                )
                "/api/v1/auth/me" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-1","username":"alice","email":"alice@example.com","apps":["tabby-rush"],"isAdmin":false}"""
                )
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = HubAuthSession(debugConfig, vault, engine)
        val result = session.login("alice@example.com", "password123")

        assertIs<ApiResult.Success<AccountIdentity>>(result)
        assertEquals("usr-1", result.value.userId)
        assertEquals("alice", result.value.username)
        assertEquals("alice@example.com", result.value.email)

        // State is AUTHENTICATED
        assertEquals(AuthState.AUTHENTICATED, session.state.value)
        assertEquals("usr-1", session.currentIdentity.value?.userId)
        assertNotNull(session.currentAccountKey.value)

        // Vault is populated
        assertEquals("access-1", vault.storedBundle?.accessToken)
        assertEquals("ref-1", vault.storedBundle?.refreshToken)
    }

    @Test
    fun testLoginSuccessWithoutTabbyRushAccessTransitionsToAccessPending() = runTest {
        val vault = FakeCredentialVault()
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-2","accessToken":"access-2","refreshToken":"ref-2","apps":[]}"""
                )
                "/api/v1/auth/me" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-2","username":"bob","email":"bob@example.com","apps":[],"isAdmin":false}"""
                )
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = HubAuthSession(debugConfig, vault, engine)
        val result = session.login("bob@example.com", "password123")

        assertIs<ApiResult.Success<AccountIdentity>>(result)
        // Without tabbyrush grant, state is ACCESS_PENDING
        assertEquals(AuthState.ACCESS_PENDING, session.state.value)
    }

    @Test
    fun testLoginMismatchedUserIdRejected() = runTest {
        val vault = FakeCredentialVault()
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-expected","accessToken":"access-x","refreshToken":"ref-x"}"""
                )
                "/api/v1/auth/me" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-DIFFERENT","username":"eve","email":"eve@example.com","apps":["tabby-rush"],"isAdmin":false}"""
                )
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = HubAuthSession(debugConfig, vault, engine)
        val result = session.login("eve@example.com", "password123")

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.Unauthorized>(result.error)
        assertEquals("USER_ID_MISMATCH", result.error.code)

        // Session reset to UNAUTHENTICATED; vault untouched
        assertEquals(AuthState.UNAUTHENTICATED, session.state.value)
        assertNull(vault.storedBundle)
    }

    @Test
    fun testOfflineSessionRestore() = runTest {
        val vault = FakeCredentialVault()
        val identity = AccountIdentity("usr-saved", "saveduser", "saved@example.com")
        val key = AccountKey.create(debugConfig.canonicalOrigin, "usr-saved")

        // Pre-populate vault (simulating established account from previous launch)
        vault.store(key, CredentialBundle("stored-access", "stored-refresh"))

        // Engine throws on network calls to prove offline capability
        val engine = TestHttpEngine {
            throw IllegalStateException("Network unreachable offline")
        }

        val session = HubAuthSession(debugConfig, vault, engine)
        val restored = session.restoreSession(identity, key)

        assertTrue(restored)
        assertEquals(AuthState.AUTHENTICATED, session.state.value)
        assertEquals(identity, session.currentIdentity.value)
        assertEquals(key, session.currentAccountKey.value)
    }

    @Test
    fun testAuthorizedRequest401DoesNotReplayPostAutomatically() = runTest {
        val vault = FakeCredentialVault()
        var callCount = 0

        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-3","accessToken":"access-old","refreshToken":"ref-old","apps":["tabby-rush"]}"""
                )
                "/api/v1/auth/me" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-3","username":"charlie","email":"charlie@example.com","apps":["tabby-rush"],"isAdmin":false}"""
                )
                "/api/v1/auth/refresh" -> Pair(
                    HttpStatusCode.OK,
                    """{"accessToken":"access-new","refreshToken":"ref-new"}"""
                )
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = HubAuthSession(debugConfig, vault, engine)
        session.login("charlie@example.com", "password123")

        // Call authorizedRequest with an operation that initially returns 401
        val opResult = session.authorizedRequest<String> { token ->
            callCount++
            if (token == "access-old") {
                ApiResult.Failure(SafeError.Unauthorized("EXPIRED_JWT", "Expired"))
            } else {
                ApiResult.Success("operation-result")
            }
        }

        // Token refreshed successfully, BUT authorizedRequest returns TOKEN_REFRESHED error
        // to prevent silent POST replay at the auth transport layer!
        assertIs<ApiResult.Failure>(opResult)
        assertIs<SafeError.Unauthorized>(opResult.error)
        assertEquals("TOKEN_REFRESHED", opResult.error.code)

        // Block was called exactly ONCE; not automatically replayed
        assertEquals(1, callCount)

        // Successor tokens are in memory and vault
        assertEquals("access-new", vault.storedBundle?.accessToken)
        assertEquals("ref-new", vault.storedBundle?.refreshToken)
    }

    @Test
    fun testLocalLogoutClearsSessionAndVault() = runTest {
        val vault = FakeCredentialVault()
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-4","accessToken":"access-4","refreshToken":"ref-4","apps":["tabby-rush"]}"""
                )
                "/api/v1/auth/me" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-4","username":"dave","email":"dave@example.com","apps":["tabby-rush"],"isAdmin":false}"""
                )
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = HubAuthSession(debugConfig, vault, engine)
        session.login("dave@example.com", "password123")

        assertEquals(AuthState.AUTHENTICATED, session.state.value)
        assertNotNull(vault.storedBundle)

        // Local logout
        session.logout()

        assertEquals(AuthState.UNAUTHENTICATED, session.state.value)
        assertNull(session.currentIdentity.value)
        assertNull(session.currentAccountKey.value)
        assertNull(vault.storedBundle)
    }

    @Test
    fun testLogoutDuringRefreshPreventsStaleVaultResurrection() = runTest {
        val vault = FakeCredentialVault()
        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-5","accessToken":"acc-5","refreshToken":"ref-5","apps":["tabby-rush"]}"""
                )
                "/api/v1/auth/me" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-5","username":"ed","email":"ed@example.com","apps":["tabby-rush"],"isAdmin":false}"""
                )
                "/api/v1/auth/refresh" -> {
                    delay(50) // simulate delayed network response
                    Pair(
                        HttpStatusCode.OK,
                        """{"accessToken":"acc-5-new","refreshToken":"ref-5-new"}"""
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = HubAuthSession(debugConfig, vault, engine)
        session.login("ed@example.com", "password123")

        // Start async refresh
        val refreshJob = async {
            session.refresh()
        }

        // Before refresh finishes, user calls logout
        delay(20)
        session.logout()

        val refreshResult = refreshJob.await()

        // Refresh must fail with GENERATION_STALE
        assertIs<ApiResult.Failure>(refreshResult)
        assertIs<SafeError.Unauthorized>(refreshResult.error)
        assertEquals("GENERATION_STALE", refreshResult.error.code)

        // Vault remains clean; departed credentials NEVER resurrected
        assertNull(vault.storedBundle)
        assertEquals(AuthState.UNAUTHENTICATED, session.state.value)
    }

    @Test
    fun testVaultWriteFailureRetainsSuccessorInMemory() = runTest {
        val vault = FakeCredentialVault()
        var callbackNotified = false

        val engine = TestHttpEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-6","accessToken":"acc-6","refreshToken":"ref-6","apps":["tabby-rush"]}"""
                )
                "/api/v1/auth/me" -> Pair(
                    HttpStatusCode.OK,
                    """{"userId":"usr-6","username":"frank","email":"frank@example.com","apps":["tabby-rush"],"isAdmin":false}"""
                )
                "/api/v1/auth/refresh" -> Pair(
                    HttpStatusCode.OK,
                    """{"accessToken":"acc-6-rotated","refreshToken":"ref-6-rotated"}"""
                )
                else -> Pair(HttpStatusCode.NotFound, "")
            }
        }

        val session = HubAuthSession(
            config = debugConfig,
            vault = vault,
            engine = engine,
            onCredentialPersistenceFailed = { _, bundle, _ ->
                callbackNotified = true
                assertEquals("acc-6-rotated", bundle.accessToken)
            }
        )

        session.login("frank@example.com", "password123")

        // Make vault store fail for subsequent writes
        vault.shouldFailStore = true

        val refreshResult = session.refresh()

        // Contract: failure returned to signal persistence failure
        assertIs<ApiResult.Failure>(refreshResult)
        assertIs<SafeError.Internal>(refreshResult.error)
        assertEquals("CREDENTIAL_PERSISTENCE_FAILED", refreshResult.error.code)
        assertTrue(callbackNotified)
    }
}
