package dev.tabbyrush.shared.remote

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.SafeError
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SseReadyData(
    val protocol: Int,
    val appId: String,
    val streamEpoch: String,
    val baselineAcceptanceOrdinal: String,
    val baselineHintSequence: String,
    val serverNow: String,
    val replaySupported: Boolean = false
)

@Serializable
data class SseAnnouncementData(
    val appId: String,
    val messageId: String,
    val acceptanceEpoch: String,
    val acceptanceOrdinal: String,
    val acceptedAt: String,
    val expiresAt: String,
    val emittedAt: String,
    val liveEligible: Boolean
)

@Serializable
data class SseReadStateData(
    val appId: String,
    val messageId: String
)

@Serializable
data class SseReconcileData(
    val appId: String,
    val reason: String
)

sealed interface RemoteStreamEvent {
    data class Ready(val id: String?, val data: SseReadyData) : RemoteStreamEvent
    data class Announcement(val id: String?, val data: SseAnnouncementData) : RemoteStreamEvent
    data class ReadState(val id: String?, val data: SseReadStateData) : RemoteStreamEvent
    data class Reconcile(val id: String?, val data: SseReconcileData) : RemoteStreamEvent
    data object Keepalive : RemoteStreamEvent
}

class OversizedFrameException(message: String) : Exception(message)
class MalformedFrameException(message: String) : Exception(message)

class SseStreamParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }
) {
    companion object {
        const val MAX_FRAME_BYTES = 16 * 1024 // 16 KiB
    }

    private var currentEventType = ""
    private var currentId: String? = null
    private val dataLines = mutableListOf<String>()
    private var currentFrameBytes = 0

    fun parseLine(line: String): RemoteStreamEvent? {
        val lineBytes = line.encodeToByteArray().size
        currentFrameBytes += lineBytes + 1
        if (currentFrameBytes > MAX_FRAME_BYTES) {
            reset()
            throw OversizedFrameException("SSE frame exceeded $MAX_FRAME_BYTES bytes")
        }

        if (line.isEmpty()) {
            if (dataLines.isEmpty() && currentEventType.isEmpty() && currentId == null) {
                reset()
                return null
            }
            val eventType = currentEventType
            val eventId = currentId
            val dataStr = dataLines.joinToString("\n")
            reset()

            return parseEventPayload(eventType, eventId, dataStr)
        }

        if (line.startsWith(":")) {
            return RemoteStreamEvent.Keepalive
        }

        val colonIndex = line.indexOf(':')
        val field = if (colonIndex != -1) line.substring(0, colonIndex) else line
        var value = if (colonIndex != -1) line.substring(colonIndex + 1) else ""
        if (value.startsWith(" ")) {
            value = value.substring(1)
        }

        when (field) {
            "event" -> currentEventType = value
            "data" -> dataLines.add(value)
            "id" -> currentId = value
        }
        return null
    }

    private fun parseEventPayload(eventType: String, id: String?, data: String): RemoteStreamEvent {
        return try {
            when (eventType) {
                "ready" -> {
                    val payload = json.decodeFromString(SseReadyData.serializer(), data)
                    RemoteStreamEvent.Ready(id, payload)
                }
                "announcement" -> {
                    val payload = json.decodeFromString(SseAnnouncementData.serializer(), data)
                    RemoteStreamEvent.Announcement(id, payload)
                }
                "read-state" -> {
                    val payload = json.decodeFromString(SseReadStateData.serializer(), data)
                    RemoteStreamEvent.ReadState(id, payload)
                }
                "reconcile" -> {
                    val payload = json.decodeFromString(SseReconcileData.serializer(), data)
                    RemoteStreamEvent.Reconcile(id, payload)
                }
                else -> throw MalformedFrameException("Unknown event type: '$eventType'")
            }
        } catch (e: Exception) {
            if (e is MalformedFrameException) throw e
            throw MalformedFrameException("Failed to decode SSE data for event '$eventType': ${e.message}")
        }
    }

    fun reset() {
        currentEventType = ""
        currentId = null
        dataLines.clear()
        currentFrameBytes = 0
    }
}

class RemoteStreamClient(
    val httpClient: HubHttpClient,
    val authSession: HubAuthSession
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    suspend fun parseChannel(
        channel: ByteReadChannel,
        emit: suspend (RemoteStreamEvent) -> Unit
    ) {
        val parser = SseStreamParser(json)
        try {
            while (!channel.isClosedForRead) {
                val line = try {
                    channel.readUTF8Line(16 * 1024) ?: break
                } catch (e: Exception) {
                    throw OversizedFrameException("SSE line exceeded 16KiB limit: ${e.message}")
                }
                val event = parser.parseLine(line)
                if (event != null) {
                    emit(event)
                }
            }
        } finally {
            parser.reset()
        }
    }

    suspend fun openAndConsumeStream(
        path: String = "/api/v1/apps/${httpClient.config.appId}/announcements/stream",
        lastEventId: String? = null,
        onRevoked: suspend () -> Unit = {},
        onEvent: suspend (RemoteStreamEvent) -> Unit
    ): ApiResult<Unit> {
        val block: suspend (String) -> ApiResult<Unit> = { token ->
            try {
                val statement = httpClient.prepareStream(
                    path = path,
                    accessToken = token,
                    lastEventId = lastEventId
                )
                statement.execute { response ->
                    if (response.status == HttpStatusCode.OK) {
                        val channel: ByteReadChannel = response.body()
                        parseChannel(channel, onEvent)
                        ApiResult.Success(Unit)
                    } else if (response.status == HttpStatusCode.Unauthorized) {
                        ApiResult.Failure(
                            SafeError.Unauthorized(
                                code = "UNAUTHORIZED",
                                message = "Stream connection unauthorized (HTTP 401)"
                            )
                        )
                    } else {
                        val error = httpClient.parseResponseError(response, path)
                        ApiResult.Failure(error)
                    }
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: OversizedFrameException) {
                ApiResult.Failure(SafeError.Network("OVERSIZED_FRAME", e.message ?: "Oversized frame"))
            } catch (e: MalformedFrameException) {
                ApiResult.Failure(SafeError.ProtocolIncompatible("MALFORMED_FRAME", e.message ?: "Malformed frame"))
            } catch (e: Exception) {
                ApiResult.Failure(SafeError.Network("STREAM_ERROR", e.message ?: "Stream failure"))
            }
        }

        var result = authSession.authorizedRequest(block = block)
        if (result is ApiResult.Failure &&
            result.error is SafeError.Unauthorized &&
            result.error.code == "TOKEN_REFRESHED"
        ) {
            // Replay open at most once after TOKEN_REFRESHED
            result = authSession.authorizedRequest(block = block)
        }

        if (result is ApiResult.Failure) {
            if (result.error.code == "appAccessRevoked" ||
                (result.error is SafeError.AccessPending && result.error.code == "appAccessRevoked")
            ) {
                onRevoked()
            }
        }

        return result
    }
}
