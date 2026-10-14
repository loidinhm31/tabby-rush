package dev.doggo.shared.sync

import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.auth.HubHttpClient
import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.StrictSyncContract
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HubSyncClient coordinates HTTP communications with the Hub strict-v1 endpoints:
 * - POST /api/v1/sync/:app_id/strong/bootstrap
 * - POST /api/v1/sync/:app_id/strong/push
 * - POST /api/v1/sync/:app_id/strong/pull
 *
 * Enforces request bounds (<= 100 unique rowIds, <= 256 KiB serialized payload)
 * and maps server error codes to typed SafeErrors.
 */
class HubSyncClient(
    val config: HubConfig,
    val session: HubAuthSession,
    val httpClient: HubHttpClient = session.authClient.httpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    suspend fun bootstrap(): ApiResult<BootstrapResponseDto> {
        val path = "/api/v1/sync/${config.appId}/strong/bootstrap"
        val bodyJson = "{}"

        return session.authorizedRequest { token ->
            val responseResult = httpClient.executeRaw(
                method = HttpMethod.Post,
                path = path,
                accessToken = token,
                bodyJson = bodyJson
            )

            when (responseResult) {
                is ApiResult.Failure -> responseResult
                is ApiResult.Success -> {
                    val response = responseResult.value
                    if (response.status == HttpStatusCode.OK) {
                        try {
                            val parsed = json.decodeFromString<BootstrapResponseDto>(response.bodyAsText())
                            if (parsed.protocol != StrictSyncContract.PROTOCOL_VERSION) {
                                ApiResult.Failure(
                                    SafeError.ProtocolIncompatible(
                                        code = "PROTOCOL_MISMATCH",
                                        message = "Server returned unsupported protocol: ${parsed.protocol}"
                                    )
                                )
                            } else {
                                ApiResult.Success(parsed)
                            }
                        } catch (e: Exception) {
                            ApiResult.Failure(
                                SafeError.ProtocolIncompatible(
                                    code = "MALFORMED_RESPONSE",
                                    message = "Failed to parse bootstrap response: ${e.message}"
                                )
                            )
                        }
                    } else {
                        ApiResult.Failure(parseStrictError(response.status, response.bodyAsText()))
                    }
                }
            }
        }
    }

    suspend fun push(
        epoch: String,
        operations: List<StrictOperationDto>
    ): ApiResult<StrictPushResponseDto> {
        if (operations.size > MAX_BATCH_SIZE) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "BATCH_TOO_LARGE",
                    message = "Operation batch exceeds maximum of $MAX_BATCH_SIZE (got ${operations.size})"
                )
            )
        }

        val rowIds = operations.map { it.rowId }
        if (rowIds.toSet().size != rowIds.size) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "DUPLICATE_ROW_IDS",
                    message = "Push operations must not contain duplicate rowIds"
                )
            )
        }

        val requestDto = StrictPushRequestDto(
            protocol = StrictSyncContract.PROTOCOL_VERSION,
            epoch = epoch,
            operations = operations
        )

        val bodyJson = json.encodeToString(requestDto)
        val bodyBytes = bodyJson.encodeToByteArray()
        if (bodyBytes.size > MAX_PAYLOAD_BYTES) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "PAYLOAD_TOO_LARGE",
                    message = "Serialized push body exceeds $MAX_PAYLOAD_BYTES bytes (got ${bodyBytes.size})"
                )
            )
        }

        val path = "/api/v1/sync/${config.appId}/strong/push"

        return session.authorizedRequest { token ->
            val responseResult = httpClient.executeRaw(
                method = HttpMethod.Post,
                path = path,
                accessToken = token,
                bodyJson = bodyJson
            )

            when (responseResult) {
                is ApiResult.Failure -> responseResult
                is ApiResult.Success -> {
                    val response = responseResult.value
                    if (response.status == HttpStatusCode.OK) {
                        try {
                            val parsed = json.decodeFromString<StrictPushResponseDto>(response.bodyAsText())
                            if (parsed.protocol != StrictSyncContract.PROTOCOL_VERSION) {
                                ApiResult.Failure(
                                    SafeError.ProtocolIncompatible(
                                        code = "PROTOCOL_MISMATCH",
                                        message = "Server returned unsupported protocol: ${parsed.protocol}"
                                    )
                                )
                            } else if (parsed.epoch != epoch) {
                                ApiResult.Failure(
                                    SafeError.ResetRequired(
                                        code = "EPOCH_MISMATCH",
                                        message = "Response epoch ${parsed.epoch} mismatched request epoch $epoch"
                                    )
                                )
                            } else {
                                ApiResult.Success(parsed)
                            }
                        } catch (e: Exception) {
                            ApiResult.Failure(
                                SafeError.ProtocolIncompatible(
                                    code = "MALFORMED_RESPONSE",
                                    message = "Failed to parse push response: ${e.message}"
                                )
                            )
                        }
                    } else {
                        ApiResult.Failure(parseStrictError(response.status, response.bodyAsText()))
                    }
                }
            }
        }
    }

    suspend fun pull(
        epoch: String,
        tableName: String = StrictSyncContract.DEFAULT_TABLE_NAME,
        cursor: String? = null,
        batchSize: Int = 100
    ): ApiResult<StrictPullResponseDto> {
        val requestDto = StrictPullRequestDto(
            protocol = StrictSyncContract.PROTOCOL_VERSION,
            epoch = epoch,
            tableName = tableName,
            cursor = cursor,
            batchSize = batchSize
        )

        val bodyJson = json.encodeToString(requestDto)
        val path = "/api/v1/sync/${config.appId}/strong/pull"

        return session.authorizedRequest { token ->
            val responseResult = httpClient.executeRaw(
                method = HttpMethod.Post,
                path = path,
                accessToken = token,
                bodyJson = bodyJson
            )

            when (responseResult) {
                is ApiResult.Failure -> responseResult
                is ApiResult.Success -> {
                    val response = responseResult.value
                    if (response.status == HttpStatusCode.OK) {
                        try {
                            val parsed = json.decodeFromString<StrictPullResponseDto>(response.bodyAsText())
                            if (parsed.protocol != StrictSyncContract.PROTOCOL_VERSION) {
                                ApiResult.Failure(
                                    SafeError.ProtocolIncompatible(
                                        code = "PROTOCOL_MISMATCH",
                                        message = "Server returned unsupported protocol: ${parsed.protocol}"
                                    )
                                )
                            } else if (parsed.epoch != epoch) {
                                ApiResult.Failure(
                                    SafeError.ResetRequired(
                                        code = "EPOCH_MISMATCH",
                                        message = "Response epoch ${parsed.epoch} mismatched request epoch $epoch"
                                    )
                                )
                            } else {
                                ApiResult.Success(parsed)
                            }
                        } catch (e: Exception) {
                            ApiResult.Failure(
                                SafeError.ProtocolIncompatible(
                                    code = "MALFORMED_RESPONSE",
                                    message = "Failed to parse pull response: ${e.message}"
                                )
                            )
                        }
                    } else {
                        ApiResult.Failure(parseStrictError(response.status, response.bodyAsText()))
                    }
                }
            }
        }
    }

    private fun parseStrictError(status: HttpStatusCode, rawBody: String): SafeError {
        val parsedEnvelope = try {
            json.decodeFromString<StrictSyncErrorDto>(rawBody)
        } catch (_: Exception) {
            null
        }

        val code = parsedEnvelope?.code
        val message = parsedEnvelope?.error ?: "Server returned HTTP ${status.value}"

        return when {
            status == HttpStatusCode.Conflict || code == "epochMismatch" -> {
                SafeError.ResetRequired(
                    code = "EPOCH_MISMATCH",
                    message = message
                )
            }
            status == HttpStatusCode.UpgradeRequired || code == "protocolUnsupported" || code == "notStrictApp" -> {
                SafeError.ProtocolIncompatible(
                    code = "PROTOCOL_INCOMPATIBLE",
                    message = message
                )
            }
            status == HttpStatusCode.ServiceUnavailable || code == "notAdmitted" -> {
                SafeError.Network(
                    code = "SERVICE_UNAVAILABLE",
                    message = message
                )
            }
            status == HttpStatusCode.BadRequest || code == "invalidRequest" -> {
                SafeError.Validation(
                    code = "BAD_REQUEST",
                    message = message
                )
            }
            status == HttpStatusCode.Unauthorized || code == "unauthorized" -> {
                SafeError.Unauthorized(
                    code = "UNAUTHORIZED",
                    message = message
                )
            }
            status == HttpStatusCode.NotFound || code == "appNotFound" -> {
                SafeError.Validation(
                    code = "APP_NOT_FOUND",
                    message = message
                )
            }
            status.value >= 500 -> {
                SafeError.Network(
                    code = "SERVER_ERROR",
                    message = message
                )
            }
            else -> {
                SafeError.Validation(
                    code = "REQUEST_FAILED",
                    message = message
                )
            }
        }
    }

    companion object {
        const val MAX_BATCH_SIZE = 100
        const val MAX_PAYLOAD_BYTES = 256 * 1024 // 256 KiB
    }
}
