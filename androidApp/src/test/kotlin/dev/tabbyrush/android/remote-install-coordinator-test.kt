package dev.tabbyrush.android

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.CredentialVault
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.VaultStatus
import dev.tabbyrush.shared.remote.RemoteInboxClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAPI::class)
class RemoteInstallCoordinatorTest {

    private val hubConfig = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "tabby-rush",
        appApiKey = "test-key",
        buildMode = BuildMode.DEBUG
    )

    private lateinit var coordinatorScope: CoroutineScope

    @BeforeTest
    fun setUp() {
        coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        coordinatorScope.cancel()
    }

    private class TestVault : CredentialVault {
        val map = mutableMapOf<AccountKey, CredentialBundle>()
        override val status = VaultStatus.READY
        override suspend fun load(accountKey: AccountKey) = map[accountKey]
        override suspend fun store(accountKey: AccountKey, bundle: CredentialBundle) { map[accountKey] = bundle }
        override suspend fun clear(accountKey: AccountKey) { map.remove(accountKey) }
    }

    private class MockEngine(
        val handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>
    ) : HttpClientEngineBase("MockEngine") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf(io.ktor.client.plugins.HttpTimeoutCapability)
        val requests = mutableListOf<HttpRequestData>()

        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            requests.add(data)
            val (status, body) = handler(data)
            val bytes = body.encodeToByteArray()
            val headers = Headers.build {
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.ContentLength, bytes.size.toString())
            }
            return HttpResponseData(
                statusCode = status,
                requestTime = GMTDate(),
                headers = headers,
                version = HttpProtocolVersion.HTTP_1_1,
                body = ByteReadChannel(bytes),
                callContext = callContext()
            )
        }
    }

    private suspend fun setupEnvironment(
        store: InMemoryRemoteInstallStore,
        httpHandler: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>
    ): Pair<RemoteInstallCoordinator, MockEngine> {
        val engine = MockEngine(httpHandler)
        val vault = TestVault()

        val alice = AccountIdentity("usr_alice", "alice", "alice@example.com")
        val aliceKey = AccountKey.create(hubConfig.canonicalOrigin, alice.userId)
        vault.store(aliceKey, CredentialBundle("alice-token", "alice-refresh"))

        val authSession = HubAuthSession(hubConfig, vault, engine)
        authSession.restoreSession(alice, aliceKey)

        val httpClient = HubHttpClient(hubConfig, engine)
        val remoteClient = RemoteInboxClient(httpClient, authSession)

        val coordinator = RemoteInstallCoordinator(
            store = store,
            clientProvider = { remoteClient },
            authSession = authSession,
            appVersion = "1.0.0",
            scope = coordinatorScope
        )

        return Pair(coordinator, engine)
    }

    @Test
    fun testFreshStoreInitialization() = runTest {
        val store = InMemoryRemoteInstallStore()
        val state = store.readState()
        assertNotNull(state.installId)
        assertTrue(RemoteNotificationIntents.isCanonicalUuid(state.installId))
        assertEquals(43, state.installProof.length)
        assertEquals(0L, state.persistedGeneration)
        assertNull(state.lastBoundGeneration)
        assertNull(state.pendingCommand)
    }

    @Test
    fun testPersistentGenerationIncrementsBeforeNetwork() = runTest {
        var observedGenInStoreDuringCall = -1L
        val store = InMemoryRemoteInstallStore()
        val (coordinator, _) = setupEnvironment(store) { req ->
            val currentState = store.readState()
            observedGenInStoreDuringCall = currentState.persistedGeneration
            val body = """{"installId":"${currentState.installId}","operationGeneration":"1","bindingGeneration":"1","state":"bound","replayed":false}"""
            Pair(HttpStatusCode.OK, body)
        }

        store.writeState(store.readState().copy(cachedFcmToken = "fcm_token_123"))
        coordinator.onAccountStarted("alice_key", "usr_alice", 1L)

        // Monotonic generation incremented before/during network execution
        assertEquals(1L, observedGenInStoreDuringCall)
        val finalState = store.readState()
        assertEquals(1L, finalState.lastBoundGeneration)
        assertNull(finalState.pendingCommand)
    }

    @Test
    fun testLostResponseSameGenerationReplay() = runTest {
        var callCount = 0
        val store = InMemoryRemoteInstallStore()
        val (coordinator, _) = setupEnvironment(store) { req ->
            callCount++
            if (callCount == 1) {
                // First attempt: simulate 500 network failure / lost response
                Pair(HttpStatusCode.InternalServerError, """{"error":{"code":"internal","message":"Lost response","retryable":true}}""")
            } else {
                // Second attempt: successful replay with exact same generation
                val currentState = store.readState()
                Pair(HttpStatusCode.OK, """{"installId":"${currentState.installId}","operationGeneration":"1","bindingGeneration":"1","state":"bound","replayed":true}""")
            }
        }

        store.writeState(store.readState().copy(cachedFcmToken = "fcm_token_123"))
        coordinator.onAccountStarted("alice_key", "usr_alice", 1L)

        val stateAfterFail = store.readState()
        assertEquals(1L, stateAfterFail.persistedGeneration)
        assertNotNull(stateAfterFail.pendingCommand)
        assertEquals(1L, stateAfterFail.pendingCommand?.generation)
        assertNull(stateAfterFail.lastBoundGeneration)

        // Retry with same generation
        coordinator.retryPendingCommand()

        val stateAfterRetry = store.readState()
        assertEquals(1L, stateAfterRetry.persistedGeneration)
        assertEquals(1L, stateAfterRetry.lastBoundGeneration)
        assertNull(stateAfterRetry.pendingCommand)
    }

    @Test
    fun testTokenRotationIssuesNewGeneration() = runTest {
        val store = InMemoryRemoteInstallStore()
        val (coordinator, _) = setupEnvironment(store) { req ->
            val currentState = store.readState()
            val gen = currentState.persistedGeneration
            Pair(HttpStatusCode.OK, """{"installId":"${currentState.installId}","operationGeneration":"$gen","bindingGeneration":"$gen","state":"bound","replayed":false}""")
        }

        // Initial bind at gen 1
        store.writeState(store.readState().copy(cachedFcmToken = "initial_token"))
        coordinator.onAccountStarted("alice_key", "usr_alice", 1L)
        assertEquals(1L, store.readState().lastBoundGeneration)

        // Rotate token
        coordinator.onNewToken("rotated_token_456")

        val stateAfterRotation = store.readState()
        assertEquals(2L, stateAfterRotation.persistedGeneration)
        assertEquals(2L, stateAfterRotation.lastBoundGeneration)
        assertEquals("rotated_token_456", stateAfterRotation.cachedFcmToken)
        assertNull(stateAfterRotation.pendingCommand)
    }

    @Test
    fun testTokenCallbackSignedOutStoresPrivately() = runTest {
        val store = InMemoryRemoteInstallStore()
        val engine = MockEngine { Pair(HttpStatusCode.OK, "{}") }
        val vault = TestVault()
        val authSession = HubAuthSession(hubConfig, vault, engine) // unauthenticated
        val httpClient = HubHttpClient(hubConfig, engine)
        val remoteClient = RemoteInboxClient(httpClient, authSession)

        val coordinator = RemoteInstallCoordinator(
            store = store,
            clientProvider = { remoteClient },
            authSession = authSession,
            appVersion = "1.0.0",
            scope = coordinatorScope
        )

        coordinator.onNewToken("signed_out_token")

        val state = store.readState()
        assertEquals("signed_out_token", state.cachedFcmToken)
        assertEquals(0L, state.persistedGeneration)
        assertNull(state.pendingCommand)
        assertEquals(0, engine.requests.size)
    }

    @Test
    fun testAccountSwitchDiscardsOldPendingAndAdvancesGeneration() = runTest {
        val store = InMemoryRemoteInstallStore()
        val (coordinator, _) = setupEnvironment(store) { req ->
            val currentState = store.readState()
            val gen = currentState.persistedGeneration
            Pair(HttpStatusCode.OK, """{"installId":"${currentState.installId}","operationGeneration":"$gen","bindingGeneration":"$gen","state":"bound","replayed":false}""")
        }

        // Alice bound at gen 1, with leftover pending command
        val oldCommand = PendingInstallCommand.Bind(
            generation = 2L,
            accountKey = "alice_key",
            userId = "usr_alice",
            token = "fcm_token_alice",
            appVersion = "1.0.0"
        )
        store.writeState(
            store.readState().copy(
                persistedGeneration = 2L,
                cachedFcmToken = "fcm_token_bob",
                pendingCommand = oldCommand
            )
        )

        // Bob starts account
        coordinator.onAccountStarted("bob_key", "usr_bob", 1L)

        val finalState = store.readState()
        // Alice's gen 2 discarded; Bob receives strictly newer gen 3
        assertEquals(3L, finalState.persistedGeneration)
        assertEquals(3L, finalState.lastBoundGeneration)
        assertNull(finalState.pendingCommand)
    }

    @Test
    fun testOfflineLogoutBestEffortDoesNotBlock() = runTest {
        val store = InMemoryRemoteInstallStore()
        val requestStarted = CompletableDeferred<Unit>()

        val (coordinator, _) = setupEnvironment(store) { req ->
            requestStarted.complete(Unit)
            // Stalled network request - simulates prolonged delay
            delay(10_000L)
            Pair(HttpStatusCode.GatewayTimeout, """{"error":{"code":"timeout","message":"Gateway timeout","retryable":true}}""")
        }

        store.writeState(store.readState().copy(persistedGeneration = 5L, lastBoundGeneration = 5L))

        // Schedule unbind: local state immediately updates and returns without awaiting network
        coordinator.scheduleBestEffortUnbind("alice_key", "usr_alice", 1L, "captured-token")

        val stateAfterUnbind = store.readState()
        assertEquals(6L, stateAfterUnbind.persistedGeneration)
        assertNull(stateAfterUnbind.lastBoundGeneration)
        assertNotNull(stateAfterUnbind.pendingCommand)
        assertEquals(6L, stateAfterUnbind.pendingCommand?.generation)
    }
}
