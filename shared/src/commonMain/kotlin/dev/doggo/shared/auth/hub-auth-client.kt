package dev.doggo.shared.auth

import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.SafeError
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Concrete client for Glean Hub authentication and access request endpoints.
 * Operates over HubHttpClient with strict route classification and body serialization.
 */
class HubAuthClient(
    val config: HubConfig,
    engine: HttpClientEngine? = null
) {
    val httpClient = HubHttpClient(config, engine)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    suspend fun register(
        username: String,
        email: String,
        password: String
    ): ApiResult<RegisterResponse> {
        val normalizedEmail = HubAuthValidation.normalizeEmail(email)
        val requestBody = RegisterRequest(
            username = username,
            email = normalizedEmail,
            password = password
        )
        val bodyJson = json.encodeToString(requestBody)

        val rawResult = httpClient.executeRaw(
            method = HttpMethod.Post,
            path = "/api/v1/auth/register",
            bodyJson = bodyJson
        )

        return when (rawResult) {
            is ApiResult.Failure -> rawResult
            is ApiResult.Success -> {
                val response = rawResult.value
                if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                    try {
                        val bodyText = response.bodyAsText()
                        val parsed = json.decodeFromString<RegisterResponse>(bodyText)
                        ApiResult.Success(parsed)
                    } catch (_: Exception) {
                        ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "MALFORMED_AUTH_RESPONSE",
                                message = "Unable to parse registration response"
                            )
                        )
                    }
                } else {
                    ApiResult.Failure(httpClient.parseResponseError(response, "/api/v1/auth/register"))
                }
            }
        }
    }

    suspend fun login(
        email: String,
        password: String
    ): ApiResult<LoginResponse> {
        val normalizedEmail = HubAuthValidation.normalizeEmail(email)
        val requestBody = LoginRequest(
            email = normalizedEmail,
            password = password
        )
        val bodyJson = json.encodeToString(requestBody)

        val rawResult = httpClient.executeRaw(
            method = HttpMethod.Post,
            path = "/api/v1/auth/login",
            bodyJson = bodyJson
        )

        return when (rawResult) {
            is ApiResult.Failure -> rawResult
            is ApiResult.Success -> {
                val response = rawResult.value
                if (response.status == HttpStatusCode.OK) {
                    try {
                        val bodyText = response.bodyAsText()
                        val parsed = json.decodeFromString<LoginResponse>(bodyText)
                        ApiResult.Success(parsed)
                    } catch (_: Exception) {
                        ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "MALFORMED_AUTH_RESPONSE",
                                message = "Unable to parse login response"
                            )
                        )
                    }
                } else {
                    ApiResult.Failure(httpClient.parseResponseError(response, "/api/v1/auth/login"))
                }
            }
        }
    }

    suspend fun refresh(
        refreshToken: String
    ): ApiResult<RefreshResponse> {
        val requestBody = RefreshRequest(refreshToken = refreshToken)
        val bodyJson = json.encodeToString(requestBody)

        val rawResult = httpClient.executeRaw(
            method = HttpMethod.Post,
            path = "/api/v1/auth/refresh",
            bodyJson = bodyJson
        )

        return when (rawResult) {
            is ApiResult.Failure -> rawResult
            is ApiResult.Success -> {
                val response = rawResult.value
                if (response.status == HttpStatusCode.OK) {
                    try {
                        val bodyText = response.bodyAsText()
                        val parsed = json.decodeFromString<RefreshResponse>(bodyText)
                        ApiResult.Success(parsed)
                    } catch (_: Exception) {
                        ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "MALFORMED_AUTH_RESPONSE",
                                message = "Unable to parse refresh response"
                            )
                        )
                    }
                } else {
                    ApiResult.Failure(httpClient.parseResponseError(response, "/api/v1/auth/refresh"))
                }
            }
        }
    }

    suspend fun getMe(
        accessToken: String
    ): ApiResult<MeResponse> {
        val rawResult = httpClient.executeRaw(
            method = HttpMethod.Get,
            path = "/api/v1/auth/me",
            accessToken = accessToken
        )

        return when (rawResult) {
            is ApiResult.Failure -> rawResult
            is ApiResult.Success -> {
                val response = rawResult.value
                if (response.status == HttpStatusCode.OK) {
                    try {
                        val bodyText = response.bodyAsText()
                        val parsed = json.decodeFromString<MeResponse>(bodyText)
                        ApiResult.Success(parsed)
                    } catch (_: Exception) {
                        ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "MALFORMED_AUTH_RESPONSE",
                                message = "Unable to parse /me response"
                            )
                        )
                    }
                } else {
                    ApiResult.Failure(httpClient.parseResponseError(response, "/api/v1/auth/me"))
                }
            }
        }
    }

    suspend fun requestAccess(
        accessToken: String,
        appId: String = "doggo",
        message: String? = null
    ): ApiResult<AccessRequestDto> {
        val requestBody = CreateAccessRequestBody(appId = appId, message = message)
        val bodyJson = json.encodeToString(requestBody)

        val rawResult = httpClient.executeRaw(
            method = HttpMethod.Post,
            path = "/api/v1/apps/access-requests",
            accessToken = accessToken,
            bodyJson = bodyJson
        )

        return when (rawResult) {
            is ApiResult.Failure -> rawResult
            is ApiResult.Success -> {
                val response = rawResult.value
                if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                    try {
                        val bodyText = response.bodyAsText()
                        val parsed = json.decodeFromString<AccessRequestDto>(bodyText)
                        ApiResult.Success(parsed)
                    } catch (_: Exception) {
                        ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "MALFORMED_ACCESS_RESPONSE",
                                message = "Unable to parse access request response"
                            )
                        )
                    }
                } else {
                    ApiResult.Failure(httpClient.parseResponseError(response, "/api/v1/apps/access-requests"))
                }
            }
        }
    }

    suspend fun getAccessRequests(
        accessToken: String
    ): ApiResult<List<AccessRequestDto>> {
        val rawResult = httpClient.executeRaw(
            method = HttpMethod.Get,
            path = "/api/v1/apps/access-requests/me",
            accessToken = accessToken
        )

        return when (rawResult) {
            is ApiResult.Failure -> rawResult
            is ApiResult.Success -> {
                val response = rawResult.value
                if (response.status == HttpStatusCode.OK) {
                    try {
                        val bodyText = response.bodyAsText()
                        val parsed = json.decodeFromString<List<AccessRequestDto>>(bodyText)
                        ApiResult.Success(parsed)
                    } catch (_: Exception) {
                        ApiResult.Failure(
                            SafeError.ProtocolIncompatible(
                                code = "MALFORMED_ACCESS_LIST_RESPONSE",
                                message = "Unable to parse access requests list"
                            )
                        )
                    }
                } else {
                    ApiResult.Failure(httpClient.parseResponseError(response, "/api/v1/apps/access-requests/me"))
                }
            }
        }
    }

    fun close() {
        httpClient.close()
    }
}
