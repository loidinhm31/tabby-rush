package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME
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

    suspend fun push(
        records: List<PushRecordDto>,
        collaborationVersion: Long? = COLLABORATION_VERSION_1
    ): ApiResult<PushResponseDto> {
        validateRecords(records)?.let { return ApiResult.Failure(it) }
        val request = PushRequestDto(records = records, collaborationVersion = collaborationVersion)
        return post("push", json.encodeToString(request)) { raw ->
            val element = json.parseToJsonElement(raw)
            validateSchemaMetadata(element)
            val parsed = json.decodeFromJsonElement(PushResponseDto.serializer(), element)
            validatePushResponse(parsed)
            parsed
        }
    }

    suspend fun pull(
        request: PullRequestDto = PullRequestDto(
            tables = listOf("todos", ASSIGNMENT_TABLE_NAME),
            collaborationVersion = COLLABORATION_VERSION_1
        )
    ): ApiResult<PullResponseDto> =
        post("pull", json.encodeToString(request)) { raw ->
            val element = json.parseToJsonElement(raw)
            validateSchemaMetadata(element)
            val parsed = json.decodeFromJsonElement(PullResponseDto.serializer(), element)
            validatePullResponse(parsed)
            parsed
        }

    suspend fun delta(request: DeltaRequestDto): ApiResult<DeltaResponseDto> {
        validateRecords(request.push?.records.orEmpty())?.let { return ApiResult.Failure(it) }
        return post("delta", json.encodeToString(request)) { raw ->
            val objectValue = json.parseToJsonElement(raw).jsonObject
            val push = objectValue["push"]?.takeUnless { it == JsonNull }?.let {
                runCatching {
                    validateSchemaMetadata(it)
                    val parsed = json.decodeFromJsonElement(PushResponseDto.serializer(), it)
                    validatePushResponse(parsed)
                    parsed
                }
            }
            val pull = objectValue["pull"]?.takeUnless { it == JsonNull }?.let {
                runCatching {
                    validateSchemaMetadata(it)
                    val parsed = json.decodeFromJsonElement(PullResponseDto.serializer(), it)
                    validatePullResponse(parsed)
                    parsed
                }
            }
            DeltaResponseDto(
                push?.getOrNull(),
                pull?.getOrNull(),
                if (push?.isFailure == true) "Invalid delta push result; delivery unknown" else null,
                if (pull?.isFailure == true) "Invalid delta pull result; previous checkpoint retained" else null
            )
        }
    }

    private fun validateSchemaMetadata(element: JsonElement) {
        val value = element.jsonObject["schemaVersion"] as? JsonPrimitive
        require(value != null && !value.isString && value.content.isNotEmpty() && value.content[0] in '1'..'9' &&
            value.content.all { it in '0'..'9' } && value.longOrNull?.let { it in 1..MAX_EXACT_VERSION } == true) { "Invalid schemaVersion" }
    }

    private fun validateRecords(records: List<PushRecordDto>): SafeError? {
        if (records.size > 100) {
            return SafeError.Validation("INVALID_BATCH", "Push needs at most 100 records")
        }
        val canonicalKeys = records.map { Triple(it.ownerUserId ?: "", it.tableName, it.rowId) }
        if (canonicalKeys.toSet().size != records.size) {
            return SafeError.Validation("INVALID_BATCH", "Push needs distinct canonical records")
        }
        for (record in records) {
            if (record.tableName != "todos" && record.tableName != "taskAssignees") {
                return SafeError.Validation("INVALID_VERSION", "Invalid record table: ${record.tableName}")
            }
            if (record.version !in 1..MAX_EXACT_VERSION || record.assumedServerVersion !in 0 until record.version) {
                return SafeError.Validation("INVALID_VERSION", "Invalid record version")
            }
            if (record.ownerUserId != null && record.ownerUserId.isBlank()) {
                return SafeError.Validation("INVALID_OWNER", "ownerUserId must not be blank when present")
            }
        }
        return null
    }

    private fun validatePushResponse(response: PushResponseDto) {
        if (response.collaborationVersion != null) {
            require(response.collaborationVersion == COLLABORATION_VERSION_1) { "Unsupported collaborationVersion in response" }
        }
        for (synced in response.syncedRecords) {
            require(synced.version in 1..MAX_EXACT_VERSION) { "Invalid synced version" }
            if (response.collaborationVersion == COLLABORATION_VERSION_1 && synced.ownerUserId != null) {
                require(synced.ownerUserId.isNotBlank()) { "Invalid ownerUserId in syncedRecords" }
            }
        }
        for (conflict in response.conflicts) {
            require(conflict.clientVersion in 1..MAX_EXACT_VERSION && conflict.serverVersion in 1..MAX_EXACT_VERSION) {
                "Invalid conflict version"
            }
        }
    }

    private fun validatePullResponse(response: PullResponseDto) {
        if (response.collaborationVersion != null) {
            require(response.collaborationVersion == COLLABORATION_VERSION_1) { "Unsupported collaborationVersion in response" }
        }
        if (response.accessRevision != null) {
            require(response.accessRevision in 1..MAX_EXACT_VERSION) { "Invalid accessRevision in response" }
        }
        for (record in response.records) {
            require(record.version in 1..MAX_EXACT_VERSION) { "Invalid record version in pull" }
            if (response.collaborationVersion == COLLABORATION_VERSION_1) {
                require(record.ownerUserId != null && record.ownerUserId.isNotBlank()) {
                    "Missing trusted owner on collaborative pull record"
                }
            }
        }
        val results = response.accessResults
        if (results != null) {
            val refs = results.map {
                require(it.ref.ownerUserId.isNotBlank() && it.ref.tableName.isNotBlank() && it.ref.rowId.isNotBlank()) {
                    "Invalid ref in accessResults"
                }
                Triple(it.ref.ownerUserId, it.ref.tableName, it.ref.rowId)
            }
            require(refs.toSet().size == refs.size) { "Duplicate canonical reference in accessResults" }
        }
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
