package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.SafeError
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** One POST per invocation. Token refresh never replays a mutation or delta. */
class HubSyncClient(
    val config: HubConfig,
    val session: HubAuthSession,
    val httpClient: HubHttpClient = session.authClient.httpClient,
    val expectedAccountKey: AccountKey? = session.currentAccountKey.value
) {
    private val json = syncWireJson

    suspend fun push(records: List<PushRecordDto>): ApiResult<PushResponseDto> {
        validateRecords(records)?.let { return ApiResult.Failure(it) }
        return post("push", json.encodeToString(PushRequestDto(records))) {
            val element = json.parseToJsonElement(it)
            validateSchemaMetadata(element)
            json.decodeFromJsonElement(PushResponseDto.serializer(), element)
        }
    }

    suspend fun pull(request: PullRequestDto = PullRequestDto()): ApiResult<PullResponseDto> =
        post("pull", json.encodeToString(request)) {
            val element = json.parseToJsonElement(it)
            validateSchemaMetadata(element)
            json.decodeFromJsonElement(PullResponseDto.serializer(), element)
        }

    suspend fun delta(request: DeltaRequestDto): ApiResult<DeltaResponseDto> {
        validateRecords(request.push?.records.orEmpty())?.let { return ApiResult.Failure(it) }
        return post("delta", json.encodeToString(request)) { raw ->
            val objectValue = json.parseToJsonElement(raw).jsonObject
            val push = objectValue["push"]?.takeUnless { it == kotlinx.serialization.json.JsonNull }?.let {
                runCatching { validateSchemaMetadata(it); json.decodeFromJsonElement(PushResponseDto.serializer(), it) }
            }
            val pull = objectValue["pull"]?.takeUnless { it == kotlinx.serialization.json.JsonNull }?.let {
                runCatching { validateSchemaMetadata(it); json.decodeFromJsonElement(PullResponseDto.serializer(), it) }
            }
            DeltaResponseDto(push?.getOrNull(), pull?.getOrNull(),
                if (push?.isFailure == true) "Invalid delta push result; delivery unknown" else null,
                if (pull?.isFailure == true) "Invalid delta pull result; previous checkpoint retained" else null)
        }
    }

    private fun validateSchemaMetadata(element: JsonElement) {
        val value = element.jsonObject["schemaVersion"] as? JsonPrimitive
        require(value != null && !value.isString && value.content.isNotEmpty() && value.content[0] in '1'..'9' &&
            value.content.all { it in '0'..'9' } && value.longOrNull?.let { it in 1..MAX_EXACT_VERSION } == true) { "Invalid schemaVersion" }
    }

    private fun validateRecords(records: List<PushRecordDto>): SafeError? {
        if (records.size > 100 || records.map { it.tableName to it.rowId }.toSet().size != records.size) {
            return SafeError.Validation("INVALID_BATCH", "Push needs at most 100 distinct records")
        }
        if (records.any { it.tableName != "todos" || it.version !in 1..MAX_EXACT_VERSION ||
                    it.assumedServerVersion !in 0 until it.version }) {
            return SafeError.Validation("INVALID_VERSION", "Invalid record table or version")
        }
        return null
    }

    private suspend fun <T> post(route: String, body: String, decode: (String) -> T): ApiResult<T> {
        if (body.encodeToByteArray().size > MAX_SYNC_BODY_BYTES) {
            return ApiResult.Failure(SafeError.Validation("PAYLOAD_TOO_LARGE", "Sync payload exceeds 256 KiB"))
        }
        if (expectedAccountKey == null) return ApiResult.Failure(SafeError.Unauthorized("ACCOUNT_FENCED", "Sync requires a captured account identity"))
        val path = "/api/v1/sync/${config.appId}/$route"
        return session.authorizedRequest(expectedAccountKey = expectedAccountKey) { token ->
            when (val result = httpClient.executeRaw(HttpMethod.Post, path, token, body)) {
                is ApiResult.Failure -> result
                is ApiResult.Success -> {
                    val response = result.value
                    if (response.status != HttpStatusCode.OK) {
                        ApiResult.Failure(httpClient.parseResponseError(response, path))
                    } else {
                        try {
                            ApiResult.Success(decode(response.bodyAsText()))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            ApiResult.Failure(SafeError.ProtocolIncompatible("MALFORMED_RESPONSE", "Invalid standard sync response; local work retained"))
                        }
                    }
                }
            }
        }
    }
}
