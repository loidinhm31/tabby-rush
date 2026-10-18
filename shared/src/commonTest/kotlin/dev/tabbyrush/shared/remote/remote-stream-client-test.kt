package dev.tabbyrush.shared.remote

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteStreamClientTest {

    private val parser = SseStreamParser()

    @Test
    fun testParseReadyEvent() {
        val lines = listOf(
            "event: ready",
            "id: epoch-uuid:58",
            """data: {"protocol":1,"appId":"tabby-rush","streamEpoch":"epoch-uuid","baselineAcceptanceOrdinal":"41","baselineHintSequence":"58","serverNow":"2026-10-18T00:00:00.000Z","replaySupported":false}""",
            ""
        )

        var event: RemoteStreamEvent? = null
        for (line in lines) {
            val ev = parser.parseLine(line)
            if (ev != null) event = ev
        }

        assertIs<RemoteStreamEvent.Ready>(event)
        assertEquals("epoch-uuid:58", event.id)
        assertEquals("tabby-rush", event.data.appId)
        assertEquals("epoch-uuid", event.data.streamEpoch)
        assertEquals("41", event.data.baselineAcceptanceOrdinal)
        assertEquals("58", event.data.baselineHintSequence)
        assertEquals("2026-10-18T00:00:00.000Z", event.data.serverNow)
    }

    @Test
    fun testParseAnnouncementEvent() {
        val lines = listOf(
            "event: announcement",
            "id: epoch-uuid:59",
            """data: {"appId":"tabby-rush","messageId":"baf6dc3e-3099-4ae0-8767-985552ef0b82","acceptanceEpoch":"epoch-uuid","acceptanceOrdinal":"42","acceptedAt":"2026-10-18T00:01:00.000Z","expiresAt":"2027-01-16T00:01:00.000Z","emittedAt":"2026-10-18T00:01:01.000Z","liveEligible":true}""",
            ""
        )

        var event: RemoteStreamEvent? = null
        for (line in lines) {
            val ev = parser.parseLine(line)
            if (ev != null) event = ev
        }

        assertIs<RemoteStreamEvent.Announcement>(event)
        assertEquals("baf6dc3e-3099-4ae0-8767-985552ef0b82", event.data.messageId)
        assertEquals("42", event.data.acceptanceOrdinal)
        assertTrue(event.data.liveEligible)
    }

    @Test
    fun testParseReadStateEvent() {
        val lines = listOf(
            "event: read-state",
            "id: epoch-uuid:60",
            """data: {"appId":"tabby-rush","messageId":"baf6dc3e-3099-4ae0-8767-985552ef0b82"}""",
            ""
        )

        var event: RemoteStreamEvent? = null
        for (line in lines) {
            val ev = parser.parseLine(line)
            if (ev != null) event = ev
        }

        assertIs<RemoteStreamEvent.ReadState>(event)
        assertEquals("baf6dc3e-3099-4ae0-8767-985552ef0b82", event.data.messageId)
    }

    @Test
    fun testParseReconcileEvent() {
        val lines = listOf(
            "event: reconcile",
            """data: {"appId":"tabby-rush","reason":"lagged"}""",
            ""
        )

        var event: RemoteStreamEvent? = null
        for (line in lines) {
            val ev = parser.parseLine(line)
            if (ev != null) event = ev
        }

        assertIs<RemoteStreamEvent.Reconcile>(event)
        assertEquals("lagged", event.data.reason)
    }

    @Test
    fun testParseKeepaliveComment() {
        val ev1 = parser.parseLine(": keepalive")
        assertIs<RemoteStreamEvent.Keepalive>(ev1)

        val ev2 = parser.parseLine(":")
        assertIs<RemoteStreamEvent.Keepalive>(ev2)
    }

    @Test
    fun testOversizedFrameThrows() {
        val bigData = "a".repeat(17 * 1024)
        assertFailsWith<OversizedFrameException> {
            parser.parseLine("data: $bigData")
        }
    }

    @Test
    fun testMalformedJsonThrows() {
        parser.parseLine("event: announcement")
        parser.parseLine("data: {invalid json}")
        assertFailsWith<MalformedFrameException> {
            parser.parseLine("")
        }
    }

    @Test
    fun testChannelParserStream() = runTest {
        val sseStreamText = buildString {
            append(": keepalive\n\n")
            append("event: ready\n")
            append("id: ep:1\n")
            append("data: {\"protocol\":1,\"appId\":\"tabby-rush\",\"streamEpoch\":\"ep\",\"baselineAcceptanceOrdinal\":\"10\",\"baselineHintSequence\":\"1\",\"serverNow\":\"2026-10-18T00:00:00.000Z\"}\n\n")
            append("event: reconcile\n")
            append("data: {\"appId\":\"tabby-rush\",\"reason\":\"periodic\"}\n\n")
        }

        val channel = ByteReadChannel(sseStreamText.encodeToByteArray())
        val events = mutableListOf<RemoteStreamEvent>()

        val client = RemoteStreamClient(
            httpClient = HubHttpClient(
                dev.tabbyrush.shared.contract.HubConfig(
                    canonicalOrigin = "http://localhost:3000",
                    appId = "tabby-rush",
                    appApiKey = "key",
                    buildMode = dev.tabbyrush.shared.contract.BuildMode.DEBUG
                ),
                null
            ),
            authSession = HubAuthSession(
                dev.tabbyrush.shared.contract.HubConfig(
                    canonicalOrigin = "http://localhost:3000",
                    appId = "tabby-rush",
                    appApiKey = "key",
                    buildMode = dev.tabbyrush.shared.contract.BuildMode.DEBUG
                ),
                dev.tabbyrush.shared.auth.FakeCredentialVault(),
                null
            )
        )

        client.parseChannel(channel) { ev ->
            events.add(ev)
        }

        assertEquals(3, events.size)
        assertIs<RemoteStreamEvent.Keepalive>(events[0])
        assertIs<RemoteStreamEvent.Ready>(events[1])
        assertIs<RemoteStreamEvent.Reconcile>(events[2])
    }
}
