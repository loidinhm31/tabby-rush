package dev.tabbyrush.shared.remote

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.RemoteBindingResult
import dev.tabbyrush.shared.contract.RemoteBindingStatus
import dev.tabbyrush.shared.contract.RemoteInboxItem
import dev.tabbyrush.shared.contract.RemoteInboxPage
import dev.tabbyrush.shared.contract.RemoteReadResult
import dev.tabbyrush.shared.contract.RemoteUnreadResponse
import dev.tabbyrush.shared.contract.SafeError
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BindRequest(
    val operationGeneration: String,
    val platform: String,
    val token: String,
    val appVersion: String
)

typealias RemoteBindRequest = BindRequest

@Serializable
data class UnbindRequest(
    val operationGeneration: String,
    val expectedBindingGeneration: String
)

typealias RemoteUnbindRequest = UnbindRequest

class RemoteInboxClient(
    val httpClient: HubHttpClient,
    val authSession: HubAuthSession
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    suspend fun fetchInbox(
        limit: Int = 25,
        cursor: String? = null
    ): ApiResult<RemoteInboxPage> {
        val queryParams = buildList {
            add("limit=$limit")
            if (cursor != null) add("cursor=$cursor")
        }.joinToString("&")
        val path = "/api/v1/apps/${httpClient.config.appId}/announcements/inbox?$queryParams"

        return executeWithRetry { token ->
            val result = httpClient.executeRaw(
                method = HttpMethod.Get,
                path = path,
                accessToken = token
            )
            parseResult(result, path, RemoteInboxPage.serializer())
        }
    }

    suspend fun fetchUnread(): ApiResult<RemoteUnreadResponse> {
        val path = "/api/v1/apps/${httpClient.config.appId}/announcements/unread"
        return executeWithRetry { token ->
            val result = httpClient.executeRaw(
                method = HttpMethod.Get,
                path = path,
                accessToken = token
            )
            parseResult(result, path, RemoteUnreadResponse.serializer())
        }
    }

    suspend fun fetchDetail(id: String): ApiResult<RemoteInboxItem> {
        val path = "/api/v1/apps/${httpClient.config.appId}/announcements/inbox/$id"
        return executeWithRetry { token ->
            val result = httpClient.executeRaw(
                method = HttpMethod.Get,
                path = path,
                accessToken = token
            )
            parseResult(result, path, RemoteInboxItem.serializer())
        }
    }

    suspend fun markRead(id: String): ApiResult<RemoteReadResult> {
        val path = "/api/v1/apps/${httpClient.config.appId}/announcements/inbox/$id/read"
        return executeWithRetry { token ->
            val result = httpClient.executeRaw(
                method = HttpMethod.Put,
                path = path,
                accessToken = token,
                bodyJson = "{}"
            )
            parseResult(result, path, RemoteReadResult.serializer())
        }
    }

    suspend fun bindInstall(
        installId: String,
        proof: String,
        request: BindRequest
    ): ApiResult<RemoteBindingResult> {
        val path = "/api/v1/apps/${httpClient.config.appId}/notification-installs/$installId/binding"
        val bodyJson = json.encodeToString(BindRequest.serializer(), request)
        // Device operation: does not silently replay on TOKEN_REFRESHED; caller retains intent
        return authSession.authorizedRequest { token ->
            val result = httpClient.executeRaw(
                method = HttpMethod.Put,
                path = path,
                accessToken = token,
                bodyJson = bodyJson,
                installProof = proof
            )
            parseResult(result, path, RemoteBindingResult.serializer())
        }
    }

    suspend fun unbindInstall(
        installId: String,
        proof: String,
        request: UnbindRequest,
        accessTokenOverride: String? = null
    ): ApiResult<RemoteBindingResult> {
        val path = "/api/v1/apps/${httpClient.config.appId}/notification-installs/$installId/unbind"
        val bodyJson = json.encodeToString(UnbindRequest.serializer(), request)

        if (accessTokenOverride != null) {
            val result = httpClient.executeRaw(
                method = HttpMethod.Post,
                path = path,
                accessToken = accessTokenOverride,
                bodyJson = bodyJson,
                installProof = proof
            )
            return parseResult(result, path, RemoteBindingResult.serializer())
        }

        return authSession.authorizedRequest { token ->
            val result = httpClient.executeRaw(
                method = HttpMethod.Post,
                path = path,
                accessToken = token,
                bodyJson = bodyJson,
                installProof = proof
            )
            parseResult(result, path, RemoteBindingResult.serializer())
        }
    }

    suspend fun getInstallBinding(
        installId: String,
        proof: String
    ): ApiResult<RemoteBindingStatus> {
        val path = "/api/v1/apps/${httpClient.config.appId}/notification-installs/$installId/binding"
        return executeWithRetry { token ->
            val result = httpClient.executeRaw(
                method = HttpMethod.Get,
                path = path,
                accessToken = token,
                installProof = proof
            )
            parseResult(result, path, RemoteBindingStatus.serializer())
        }
    }

    private suspend fun <T> executeWithRetry(
        block: suspend (accessToken: String) -> ApiResult<T>
    ): ApiResult<T> {
        val firstAttempt = authSession.authorizedRequest(block)
        if (firstAttempt is ApiResult.Failure &&
            firstAttempt.error is SafeError.Unauthorized &&
            firstAttempt.error.code == "TOKEN_REFRESHED"
        ) {
            return authSession.authorizedRequest(block)
        }
        return firstAttempt
    }

    private suspend fun <T> parseResult(
        responseResult: ApiResult<io.ktor.client.statement.HttpResponse>,
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>
    ): ApiResult<T> {
        return when (responseResult) {
            is ApiResult.Failure -> responseResult
            is ApiResult.Success -> {
                val response = responseResult.value
                if (response.status == HttpStatusCode.OK ||
                    response.status == HttpStatusCode.Created ||
                    response.status == HttpStatusCode.Accepted
                ) {
                    try {
                        val text = response.bodyAsText()
                        val parsed = json.decodeFromString(serializer, text)
                        ApiResult.Success(parsed)
                    } catch (e: Exception) {
                        ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "JSON_PARSE_ERROR",
                                message = "Failed to parse response from $path: ${e.message}"
                            )
                        )
                    }
                } else {
                    val error = httpClient.parseResponseError(response, path)
                    ApiResult.Failure(error)
                }
            }
        }
    }
}
