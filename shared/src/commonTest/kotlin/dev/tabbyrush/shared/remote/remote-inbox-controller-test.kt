package dev.tabbyrush.shared.remote

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxItem
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteNotificationCapabilities
import dev.tabbyrush.shared.contract.RemotePostResult
import dev.tabbyrush.shared.contract.RemoteQuiesceResult
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeNotificationAdapter : RemoteNotificationAdapter {
    val shownAlerts = mutableListOf<Pair<String, Long>>()
    val clearedGenerations = mutableListOf<Long>()
    private var callback: ((String, Long) -> Unit)? = null
    var closed = false

    override suspend fun capabilities(): RemoteNotificationCapabilities =
        RemoteNotificationCapabilities(alertsSupported = true, actionsSupported = true, notificationsEnabled = true)

    override suspend fun show(messageId: String, generation: Long): RemotePostResult {
        shownAlerts.add(Pair(messageId, generation))
        return RemotePostResult.Posted
    }

    override suspend fun clearRemote(generation: Long) {
        clearedGenerations.add(generation)
    }

    override suspend fun close() {
        closed = true
    }

    override fun setActivationCallback(callback: (messageId: String, generation: Long) -> Unit) {
        this.callback = callback
    }

    fun triggerActivation(messageId: String, generation: Long) {
        callback?.invoke(messageId, generation)
    }
}

class TestMonotonicClock(var currentMs: Long = 0L) : MonotonicClock {
    override fun markNow(): Long = currentMs
    fun advance(ms: Long) {
        currentMs += ms
    }
}

@OptIn(InternalAPI::class)
class ControllerTestHttpEngine(
    private val handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>
) : HttpClientEngineBase("ControllerTestHttpEngine") {
    override val config = HttpClientEngineConfig()
    override val supportedCapabilities = setOf(io.ktor.client.plugins.HttpTimeoutCapability)
    override val dispatcher = Dispatchers.Unconfined
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
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteInboxControllerTest {

    private val config = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "tabby-rush",
        appApiKey = "test-tabbyrush-key",
        buildMode = BuildMode.DEBUG
    )

    private val accountIdentity = AccountIdentity("usr-123", "testuser", "test@example.com")
    private val accountKey = AccountKey.create(config.canonicalOrigin, accountIdentity.userId)

    private suspend fun createController(
        scope: TestScope,
        clock: TestMonotonicClock = TestMonotonicClock(1000L),
        adapter: FakeNotificationAdapter = FakeNotificationAdapter(),
        isDesktop: Boolean = true,
        handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>
    ): DefaultRemoteInboxController {
        val engine = ControllerTestHttpEngine(handler)
        val vault = FakeCredentialVault()
        vault.store(accountKey, CredentialBundle("access-token-1", "refresh-token-1"))

        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(accountIdentity, accountKey)
        val httpClient = HubHttpClient(config, engine)
        val client = RemoteInboxClient(httpClient, session)
        val streamClient = RemoteStreamClient(httpClient, session)

        return DefaultRemoteInboxController(
            client = client,
            streamClient = streamClient,
            coroutineScope = scope,
            adapter = adapter,
            clock = clock,
            isDesktop = isDesktop
        )
    }

    @Test
    fun testSilentInitialHydrationAndDesktopLiveAlert() = runTest {
        val adapter = FakeNotificationAdapter()
        val clock = TestMonotonicClock(1000L)

        val controller = createController(
            scope = this,
            clock = clock,
            adapter = adapter,
            isDesktop = true
        ) { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/announcements/inbox") -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"items":[{"id":"baf6dc3e-3099-4ae0-8767-985552ef0b82","appId":"tabby-rush","title":"Hydrated Item","body":"Body","createdAt":"2026-10-18T00:00:00.000Z","expiresAt":"2027-01-16T00:00:00.000Z","readAt":null}],"nextCursor":null,"serverNow":"2026-10-18T00:00:01.000Z"}"""
                    )
                }
                path.endsWith("/announcements/unread") -> {
                    Pair(HttpStatusCode.OK, """{"unreadCount":1,"serverNow":"2026-10-18T00:00:01.000Z"}""")
                }
                path.endsWith("/announcements/inbox/c1c2c3c4-e5f6-4a7b-8c9d-0e1f2a3b4c5d") -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"id":"c1c2c3c4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","appId":"tabby-rush","title":"Live Alert","body":"Body","createdAt":"2026-10-18T00:01:00.000Z","expiresAt":"2027-01-16T00:01:00.000Z","readAt":null}"""
                    )
                }
                path.endsWith("/announcements/stream") -> {
                    // Feed ready + live announcement
                    val sseBody = buildString {
                        append("event: ready\n")
                        append("id: ep-1:1\n")
                        append("data: {\"protocol\":1,\"appId\":\"tabby-rush\",\"streamEpoch\":\"ep-1\",\"baselineAcceptanceOrdinal\":\"10\",\"baselineHintSequence\":\"1\",\"serverNow\":\"2026-10-18T00:00:01.000Z\"}\n\n")
                        append("event: announcement\n")
                        append("id: ep-1:2\n")
                        append("data: {\"appId\":\"tabby-rush\",\"messageId\":\"c1c2c3c4-e5f6-4a7b-8c9d-0e1f2a3b4c5d\",\"acceptanceEpoch\":\"ep-1\",\"acceptanceOrdinal\":\"11\",\"acceptedAt\":\"2026-10-18T00:01:00.000Z\",\"expiresAt\":\"2027-01-16T00:01:00.000Z\",\"emittedAt\":\"2026-10-18T00:01:01.000Z\",\"liveEligible\":true}\n\n")
                    }
                    Pair(HttpStatusCode.OK, sseBody)
                }
                else -> Pair(HttpStatusCode.NotFound, "{}")
            }
        }

        controller.start(accountIdentity, generation = 1L)
        repeat(5) { runCurrent() }
        // Verify baseline hydration: state is Online, item is present, unreadCount is 1
        assertEquals(RemoteInboxAvailability.Online, controller.state.value.availability)
        assertEquals(1, controller.state.value.items.size)
        assertEquals("baf6dc3e-3099-4ae0-8767-985552ef0b82", controller.state.value.items[0].id)

        // Hydrated historical item was NOT toasted
        assertFalse(adapter.shownAlerts.any { it.first == "baf6dc3e-3099-4ae0-8767-985552ef0b82" })

        // Live announcement with ordinal 11 > baseline 10 WAS toasted on desktop!
        assertTrue(adapter.shownAlerts.any { it.first == "c1c2c3c4-e5f6-4a7b-8c9d-0e1f2a3b4c5d" })
        controller.quiesce()
    }

    @Test
    fun testAndroidSuppressesStreamAlerts() = runTest {
        val adapter = FakeNotificationAdapter()
        val controller = createController(
            scope = this,
            adapter = adapter,
            isDesktop = false // Android!
        ) { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/announcements/inbox") -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"items":[],"nextCursor":null,"serverNow":"2026-10-18T00:00:01.000Z"}"""
                    )
                }
                path.endsWith("/announcements/unread") -> {
                    Pair(HttpStatusCode.OK, """{"unreadCount":0,"serverNow":"2026-10-18T00:00:01.000Z"}""")
                }
                path.endsWith("/announcements/stream") -> {
                    val sseBody = buildString {
                        append("event: ready\n")
                        append("id: ep-1:1\n")
                        append("data: {\"protocol\":1,\"appId\":\"tabby-rush\",\"streamEpoch\":\"ep-1\",\"baselineAcceptanceOrdinal\":\"10\",\"baselineHintSequence\":\"1\",\"serverNow\":\"2026-10-18T00:00:01.000Z\"}\n\n")
                        append("event: announcement\n")
                        append("id: ep-1:2\n")
                        append("data: {\"appId\":\"tabby-rush\",\"messageId\":\"c1c2c3c4-e5f6-4a7b-8c9d-0e1f2a3b4c5d\",\"acceptanceEpoch\":\"ep-1\",\"acceptanceOrdinal\":\"11\",\"acceptedAt\":\"2026-10-18T00:01:00.000Z\",\"expiresAt\":\"2027-01-16T00:01:00.000Z\",\"emittedAt\":\"2026-10-18T00:01:01.000Z\",\"liveEligible\":true}\n\n")
                    }
                    Pair(HttpStatusCode.OK, sseBody)
                }
                else -> Pair(HttpStatusCode.NotFound, "{}")
            }
        }

        controller.start(accountIdentity, generation = 1L)
        runCurrent()

        // Android MUST NOT emit OS toast from stream!
        assertEquals(0, adapter.shownAlerts.size)
        controller.quiesce()
    }

    @Test
    fun testAccountGenerationFencing() = runTest {
        val adapter = FakeNotificationAdapter()
        val controller = createController(
            scope = this,
            adapter = adapter,
            isDesktop = true
        ) { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/announcements/inbox") -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"items":[],"nextCursor":null,"serverNow":"2026-10-18T00:00:01.000Z"}"""
                    )
                }
                path.endsWith("/announcements/unread") -> {
                    Pair(HttpStatusCode.OK, """{"unreadCount":0,"serverNow":"2026-10-18T00:00:01.000Z"}""")
                }
                path.endsWith("/announcements/inbox/d1d2d3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d") -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"id":"d1d2d3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","appId":"tabby-rush","title":"Stale Title","body":"Stale Body","createdAt":"2026-10-18T00:00:00.000Z","expiresAt":"2027-01-16T00:00:00.000Z","readAt":null}"""
                    )
                }
                path.endsWith("/announcements/stream") -> {
                    Pair(
                        HttpStatusCode.OK,
                        "event: ready\ndata: {\"protocol\":1,\"appId\":\"tabby-rush\",\"streamEpoch\":\"ep-1\",\"baselineAcceptanceOrdinal\":\"10\",\"baselineHintSequence\":\"1\",\"serverNow\":\"2026-10-18T00:00:01.000Z\"}\n\n"
                    )
                }
                else -> Pair(HttpStatusCode.NotFound, "{}")
            }
        }

        controller.start(accountIdentity, generation = 5L)
        runCurrent()

        // Trigger native open with an OLD generation (4L != 5L)
        controller.onNativeOpen("d1d2d3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", capturedGeneration = 4L)
        repeat(5) { runCurrent() }
        // Detail must NOT be opened or stored because generation mismatched!
        assertNull(controller.state.value.detail)

        // Now trigger with current generation 5L
        controller.onNativeOpen("d1d2d3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", capturedGeneration = 5L)
        repeat(5) { runCurrent() }
        assertNotNull(controller.state.value.detail)
        assertEquals("d1d2d3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", controller.state.value.detail?.id)
        controller.quiesce()
    }

    @Test
    fun testQuiesceBarrierClearsStateAndRemoteAlerts() = runTest {
        val adapter = FakeNotificationAdapter()
        val controller = createController(
            scope = this,
            adapter = adapter
        ) { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/announcements/stream") -> {
                    Pair(
                        HttpStatusCode.OK,
                        "event: ready\ndata: {\"protocol\":1,\"appId\":\"tabby-rush\",\"streamEpoch\":\"ep-1\",\"baselineAcceptanceOrdinal\":\"10\",\"baselineHintSequence\":\"1\",\"serverNow\":\"2026-10-18T00:00:01.000Z\"}\n\n"
                    )
                }
                path.endsWith("/announcements/unread") -> {
                    Pair(HttpStatusCode.OK, """{"unreadCount":1,"serverNow":"2026-10-18T00:00:01.000Z"}""")
                }
                else -> {
                    Pair(
                        HttpStatusCode.OK,
                        """{"items":[{"id":"msg-1","appId":"tabby-rush","title":"T1","body":"B1","createdAt":"2026-10-18T00:00:00.000Z","expiresAt":"2027-01-16T00:00:00.000Z","readAt":null}],"nextCursor":null,"serverNow":"2026-10-18T00:00:01.000Z"}"""
                    )
                }
            }
        }

        controller.start(accountIdentity, generation = 10L)
        runCurrent()

        assertEquals(RemoteInboxAvailability.Online, controller.state.value.availability)
        assertEquals(1, controller.state.value.items.size)

        val result = controller.quiesce()
        assertIs<RemoteQuiesceResult.Quiesced>(result)

        // State is signed out, items and detail cleared
        assertEquals(RemoteInboxAvailability.SignedOut, controller.state.value.availability)
        assertEquals(0, controller.state.value.items.size)
        assertNull(controller.state.value.detail)

        // Adapter remote alerts cleared with advanced generation 11L
        assertEquals(listOf(11L), adapter.clearedGenerations)
    }

    @Test
    fun testSlidingPageCacheLimit() {
        val cache = RemoteInboxCache()
        val itemsPage1 = (1..150).map { i ->
            RemoteInboxItem(
                id = "item-$i",
                appId = "tabby-rush",
                title = "Title $i",
                body = "Body $i",
                createdAt = "2026-10-18T00:00:${i.toString().padStart(2, '0')}.000Z",
                expiresAt = "2027-01-16T00:00:00.000Z"
            )
        }
        val itemsPage2 = (151..250).map { i ->
            RemoteInboxItem(
                id = "item-$i",
                appId = "tabby-rush",
                title = "Title $i",
                body = "Body $i",
                createdAt = "2026-10-18T00:00:${(i - 100).toString().padStart(2, '0')}.000Z",
                expiresAt = "2027-01-16T00:00:00.000Z"
            )
        }

        cache.replaceFirstPage(
            dev.tabbyrush.shared.contract.RemoteInboxPage(
                items = itemsPage1,
                nextCursor = "cursor-p2",
                serverNow = "2026-10-18T00:05:00.000Z"
            )
        )
        assertEquals(150, cache.getItems().size)
        assertEquals("cursor-p2", cache.nextCursor)

        cache.appendPage(
            dev.tabbyrush.shared.contract.RemoteInboxPage(
                items = itemsPage2,
                nextCursor = "cursor-p3",
                serverNow = "2026-10-18T00:05:00.000Z"
            )
        )

        // Must cap at MAX_CACHE_ITEMS (200)
        assertEquals(200, cache.getItems().size)
        assertEquals("cursor-p3", cache.nextCursor)
    }

    @Test
    fun testServerNowAnchorAndExpiryFiltering() {
        val clock = TestMonotonicClock(10_000L) // monotonic 10s
        val cache = RemoteInboxCache(clock)

        // Anchor server at 2026-10-18T00:00:00.000Z (1792281600000L epoch ms)
        val serverNowIso = "2026-10-18T00:00:00.000Z"
        val serverNowMs = 1792281600000L

        // Item expiring in 30 seconds
        val itemExpiresIn30s = RemoteInboxItem(
            id = "expiring-soon",
            appId = "tabby-rush",
            title = "Expiring",
            body = "Body",
            createdAt = serverNowIso,
            expiresAt = "2026-10-18T00:00:30.000Z"
        )

        cache.replaceFirstPage(
            dev.tabbyrush.shared.contract.RemoteInboxPage(
                items = listOf(itemExpiresIn30s),
                nextCursor = null,
                serverNow = serverNowIso
            )
        )

        // Immediately after load (0s elapsed): item is NOT expired
        assertEquals(1, cache.getItems().size)

        // Advance monotonic clock by 10 seconds -> estimated server time is +10s -> NOT expired
        clock.advance(10_000L)
        assertEquals(1, cache.getItems().size)

        // Advance monotonic clock by another 25 seconds -> total +35s -> EXPIRED!
        clock.advance(25_000L)
        assertEquals(0, cache.getItems().size)
    }

    @Test
    fun testCanonicalMonotonicReadAtMerge() {
        val cache = RemoteInboxCache()
        val item = RemoteInboxItem(
            id = "read-test-id",
            appId = "tabby-rush",
            title = "Title",
            body = "Body",
            createdAt = "2026-10-18T00:00:00.000Z",
            expiresAt = "2027-01-16T00:00:00.000Z",
            readAt = null
        )

        cache.replaceFirstPage(
            dev.tabbyrush.shared.contract.RemoteInboxPage(
                items = listOf(item),
                nextCursor = null,
                serverNow = "2026-10-18T00:00:00.000Z"
            )
        )

        // Mark read at T1
        val firstReadAt = "2026-10-18T00:01:00.000Z"
        cache.markItemRead("read-test-id", firstReadAt)
        assertEquals(firstReadAt, cache.getItem("read-test-id")?.readAt)

        // Attempt to mark read with a later timestamp T2
        val laterReadAt = "2026-10-18T00:02:00.000Z"
        cache.markItemRead("read-test-id", laterReadAt)
        // Monotonic: firstReadAt MUST BE PRESERVED!
        assertEquals(firstReadAt, cache.getItem("read-test-id")?.readAt)
    }
}
