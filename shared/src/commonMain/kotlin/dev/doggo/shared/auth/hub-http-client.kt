package dev.doggo.shared.auth

import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.EndpointPolicy
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.SafeError
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

expect fun createPlatformHttpEngine(): HttpClientEngine

enum class RouteClassification {
    AUTH_PUBLIC,      // /auth/register, /auth/login, /auth/refresh -> no auth headers
    BEARER_ONLY,      // /auth/me, /apps/access-requests* -> Bearer token only
    DUAL_AUTH_STRICT  // /sync/{appId}/strong/* -> Bearer token + X-API-Key + X-App-Id
}

class HubHttpClient(
    val config: HubConfig,
    engine: HttpClientEngine? = null
) {
    val validatedOrigin: String

    init {
        when (val originResult = EndpointPolicy.validate(config.canonicalOrigin, config.buildMode)) {
            is ApiResult.Success -> {
                validatedOrigin = originResult.value
            }
            is ApiResult.Failure -> {
                throw IllegalArgumentException("Invalid canonicalOrigin in HubConfig: ${originResult.error.message}")
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    private fun extractErrorMessage(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        return try {
            val element = json.parseToJsonElement(rawBody)
            if (element is JsonObject) {
                element["error"]?.jsonPrimitive?.contentOrNull
                    ?: element["message"]?.jsonPrimitive?.contentOrNull
                    ?: element["detail"]?.jsonPrimitive?.contentOrNull
            } else null
        } catch (_: Exception) {
            null
        }
    }

    val ktorClient: HttpClient = HttpClient(engine ?: createPlatformHttpEngine()) {
        followRedirects = false

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000L
            requestTimeoutMillis = 20_000L
            socketTimeoutMillis = 20_000L
        }
    }

    fun classifyRoute(path: String): RouteClassification? {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return when (normalizedPath) {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh" -> RouteClassification.AUTH_PUBLIC

            "/api/v1/auth/me",
            "/api/v1/apps/access-requests",
            "/api/v1/apps/access-requests/me" -> RouteClassification.BEARER_ONLY

            "/api/v1/sync/${config.appId}/strong/bootstrap",
            "/api/v1/sync/${config.appId}/strong/push",
            "/api/v1/sync/${config.appId}/strong/pull",
            "/api/v1/sync/${config.appId}/strong/legacy-receipts" -> RouteClassification.DUAL_AUTH_STRICT

            else -> null
        }
    }

    suspend fun executeRaw(
        method: HttpMethod,
        path: String,
        accessToken: String? = null,
        bodyJson: String? = null
    ): ApiResult<HttpResponse> {
        val classification = classifyRoute(path)
            ?: return ApiResult.Failure(
                SafeError.ProtocolIncompatible(
                    code = "DISALLOWED_ENDPOINT",
                    message = "Path '$path' is not in the Doggo strict endpoint allowlist"
                )
            )

        val fullUrl = "$validatedOrigin${if (path.startsWith("/")) path else "/$path"}"

        return try {
            val response: HttpResponse = ktorClient.request(fullUrl) {
                this.method = method
                if (bodyJson != null) {
                    contentType(ContentType.Application.Json)
                    setBody(bodyJson)
                }

                when (classification) {
                    RouteClassification.AUTH_PUBLIC -> {
                        // Public auth routes: NO Authorization, NO X-API-Key, NO X-App-Id
                    }
                    RouteClassification.BEARER_ONLY -> {
                        if (accessToken != null) {
                            header(HttpHeaders.Authorization, "Bearer $accessToken")
                        }
                    }
                    RouteClassification.DUAL_AUTH_STRICT -> {
                        if (accessToken != null) {
                            header(HttpHeaders.Authorization, "Bearer $accessToken")
                        }
                        header("X-API-Key", config.appApiKey)
                        header("X-App-Id", config.appId)
                    }
                }
            }
            ApiResult.Success(response)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Exception) {
            ApiResult.Failure(
                SafeError.Network(
                    code = "NETWORK_ERROR",
                    message = "Network transport failure"
                )
            )
        }
    }

    suspend fun parseResponseError(response: HttpResponse, path: String): SafeError {
        val status = response.status
        val rawBody = try {
            response.bodyAsText()
        } catch (_: Exception) {
            ""
        }
        val sanitized = sanitizeErrorMessage(rawBody)

        return when (status) {
            HttpStatusCode.Unauthorized -> {
                val serverMsg = extractErrorMessage(rawBody)
                if (serverMsg?.contains("API key", ignoreCase = true) == true) {
                    SafeError.Unauthorized(
                        code = "INVALID_API_KEY",
                        message = serverMsg
                    )
                } else {
                    SafeError.Unauthorized(
                        code = "UNAUTHORIZED",
                        message = serverMsg ?: "Authentication credentials rejected (HTTP 401)"
                    )
                }
            }
            HttpStatusCode.Forbidden -> SafeError.Unauthorized(
                code = "FORBIDDEN",
                message = "Access forbidden (HTTP 403)"
            )
            HttpStatusCode.Conflict -> {
                val serverMsg = extractErrorMessage(rawBody)
                if (path.contains("/auth/")) {
                    SafeError.Validation(
                        code = "CONFLICT",
                        message = serverMsg ?: "Email or username already in use (HTTP 409)"
                    )
                } else {
                    SafeError.ResetRequired(
                        code = "EPOCH_MISMATCH",
                        message = "Server reported epoch conflict or reset required (HTTP 409)"
                    )
                }
            }
            HttpStatusCode.UpgradeRequired -> SafeError.ProtocolIncompatible(
                code = "PROTOCOL_INCOMPATIBLE",
                message = "Server rejected protocol version (HTTP 426)"
            )
            HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed -> {
                if (path.contains("/strong/")) {
                    SafeError.ProtocolIncompatible(
                        code = "STRICT_ROUTE_UNAVAILABLE",
                        message = "Strict strong sync route unavailable on server (HTTP ${status.value})"
                    )
                } else {
                    SafeError.Validation(
                        code = "NOT_FOUND",
                        message = "Endpoint not found (HTTP ${status.value})"
                    )
                }
            }
            HttpStatusCode.ServiceUnavailable -> SafeError.Network(
                code = "SERVICE_UNAVAILABLE",
                message = "Service unavailable or admission pending (HTTP 503)"
            )
            HttpStatusCode.BadRequest -> {
                val serverMsg = extractErrorMessage(rawBody)
                SafeError.Validation(
                    code = "BAD_REQUEST",
                    message = serverMsg ?: "Invalid request format or parameters (HTTP 400)"
                )
            }
            HttpStatusCode.TooManyRequests -> SafeError.Network(
                code = "RATE_LIMITED",
                message = "Rate limit exceeded (HTTP 429)"
            )
            else -> {
                if (status.value >= 500) {
                    SafeError.Network(
                        code = "SERVER_ERROR",
                        message = "Server error (HTTP ${status.value})"
                    )
                } else {
                    SafeError.Validation(
                        code = "CLIENT_ERROR",
                        message = "Client error (HTTP ${status.value})"
                    )
                }
            }
        }
    }

    fun sanitizeErrorMessage(raw: String): String {
        if (raw.isBlank()) return ""
        // Strip HTML tags if present (e.g. standard nginx/apache 502/503 HTML pages)
        val stripped = raw.replace(Regex("<[^>]*>"), " ").trim()
        val singleLine = stripped.replace(Regex("\\s+"), " ")
        return if (singleLine.length > 256) {
            singleLine.substring(0, 256) + "..."
        } else {
            singleLine
        }
    }

    fun close() {
        ktorClient.close()
    }
}
