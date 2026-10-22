package dev.tabbyrush.shared.auth

import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.EndpointPolicy
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.SafeError
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
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
    DUAL_AUTH_SYNC    // exact /sync/{appId}/{push,pull,delta} paths only
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

    private fun extractErrorCode(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        return try {
            val element = json.parseToJsonElement(rawBody)
            if (element is JsonObject) {
                val errorObj = element["error"]
                if (errorObj is JsonObject) {
                    errorObj["code"]?.jsonPrimitive?.contentOrNull
                } else {
                    element["code"]?.jsonPrimitive?.contentOrNull
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractErrorMessage(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        return try {
            val element = json.parseToJsonElement(rawBody)
            if (element is JsonObject) {
                val errorObj = element["error"]
                if (errorObj is JsonObject) {
                    errorObj["message"]?.jsonPrimitive?.contentOrNull
                } else if (errorObj != null) {
                    errorObj.jsonPrimitive.contentOrNull
                } else {
                    element["message"]?.jsonPrimitive?.contentOrNull
                        ?: element["detail"]?.jsonPrimitive?.contentOrNull
                }
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

    companion object {
        private val CANONICAL_UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        fun isValidCanonicalUuid(value: String): Boolean = CANONICAL_UUID_REGEX.matches(value)

        private fun validateInboxQuery(queryString: String): Boolean {
            if (queryString.isEmpty()) return true
            val params = queryString.split("&")
            for (param in params) {
                val parts = param.split("=")
                val key = parts[0]
                val value = if (parts.size > 1) parts[1] else ""
                when (key) {
                    "limit" -> {
                        val intVal = value.toIntOrNull() ?: return false
                        if (intVal !in 1..50) return false
                    }
                    "cursor" -> {
                        if (value.isBlank()) return false
                    }
                    else -> return false
                }
            }
            return true
        }

        private fun validateLookupQuery(queryString: String): Boolean {
            if (queryString.isEmpty()) return false
            val params = queryString.split("&")
            if (params.size != 1) return false
            val parts = params[0].split("=")
            if (parts.size != 2) return false
            if (parts[0] != "username") return false
            return parts[1].isNotBlank()
        }
    }

    fun classifyRoute(path: String): RouteClassification? {
        if (path.startsWith("api/v1/sync/")) return null
        if (path.contains('#') || path.contains('\\') || path.contains("://")) return null
        if (path.contains('?') &&
            !path.startsWith("/api/v1/apps/${config.appId}/announcements/inbox?") &&
            !path.startsWith("/api/v1/auth/lookup?")
        ) return null
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val pathWithoutQuery = normalizedPath.substringBefore('?')
        val queryString = if (normalizedPath.contains('?')) normalizedPath.substringAfter('?') else ""

        if (queryString.isNotEmpty()) {
            if (pathWithoutQuery == "/api/v1/apps/${config.appId}/announcements/inbox") {
                if (!validateInboxQuery(queryString)) return null
            } else if (pathWithoutQuery == "/api/v1/auth/lookup") {
                if (!validateLookupQuery(queryString)) return null
            } else {
                return null
            }
        }

        return when (pathWithoutQuery) {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh" -> RouteClassification.AUTH_PUBLIC

            "/api/v1/auth/me",
            "/api/v1/auth/lookup",
            "/api/v1/apps/access-requests",
            "/api/v1/apps/access-requests/me" -> RouteClassification.BEARER_ONLY

            "/api/v1/sync/${config.appId}/push",
            "/api/v1/sync/${config.appId}/pull",
            "/api/v1/sync/${config.appId}/delta" -> RouteClassification.DUAL_AUTH_SYNC

            "/api/v1/apps/${config.appId}/announcements/inbox",
            "/api/v1/apps/${config.appId}/announcements/unread",
            "/api/v1/apps/${config.appId}/announcements/stream" -> RouteClassification.BEARER_ONLY

            else -> {
                val inboxPrefix = "/api/v1/apps/${config.appId}/announcements/inbox/"
                if (pathWithoutQuery.startsWith(inboxPrefix)) {
                    val sub = pathWithoutQuery.removePrefix(inboxPrefix)
                    val segments = sub.split("/")
                    if (segments.size == 1 && isValidCanonicalUuid(segments[0])) {
                        RouteClassification.BEARER_ONLY
                    } else if (segments.size == 2 && isValidCanonicalUuid(segments[0]) && segments[1] == "read") {
                        RouteClassification.BEARER_ONLY
                    } else null
                } else {
                    val installPrefix = "/api/v1/apps/${config.appId}/notification-installs/"
                    if (pathWithoutQuery.startsWith(installPrefix)) {
                        val sub = pathWithoutQuery.removePrefix(installPrefix)
                        val segments = sub.split("/")
                        if (segments.size == 2 && isValidCanonicalUuid(segments[0]) &&
                            (segments[1] == "binding" || segments[1] == "unbind")
                        ) {
                            RouteClassification.BEARER_ONLY
                        } else null
                    } else null
                }
            }
        }
    }

    suspend fun executeRaw(
        method: HttpMethod,
        path: String,
        accessToken: String? = null,
        bodyJson: String? = null,
        installProof: String? = null
    ): ApiResult<HttpResponse> {
        val classification = classifyRoute(path)
            ?: return ApiResult.Failure(
                SafeError.ProtocolIncompatible(
                    code = "DISALLOWED_ENDPOINT",
                    message = "Path is not in the TabbyRush endpoint allowlist"
                )
            )
        if (classification == RouteClassification.DUAL_AUTH_SYNC && method != HttpMethod.Post) {
            return ApiResult.Failure(SafeError.ProtocolIncompatible("DISALLOWED_METHOD", "Sync requires POST"))
        }
        if (installProof != null) {
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            val pathWithoutQuery = normalizedPath.substringBefore('?')
            val installPrefix = "/api/v1/apps/${config.appId}/notification-installs/"
            if (!pathWithoutQuery.startsWith(installPrefix)) {
                return ApiResult.Failure(
                    SafeError.ProtocolIncompatible(
                        code = "DISALLOWED_HEADER",
                        message = "Install proof header is only permitted on notification-installs routes"
                    )
                )
            }
        }

        val fullUrl = "$validatedOrigin${if (path.startsWith("/")) path else "/$path"}"
        return try {
            val response: HttpResponse = ktorClient.request(fullUrl) {
                this.method = method
                if (bodyJson != null) {
                    contentType(ContentType.Application.Json)
                    setBody(bodyJson)
                }
                if (installProof != null) {
                    header("X-Tabby-Install-Proof", installProof)
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
                    RouteClassification.DUAL_AUTH_SYNC -> {
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
            HttpStatusCode.Forbidden -> {
                val serverCode = extractErrorCode(rawBody)
                val serverMsg = extractErrorMessage(rawBody)
                if (serverCode == "appAccessRevoked" || serverCode == "MEMBERSHIP_DENIED") {
                    SafeError.MembershipDenied(
                        code = serverCode,
                        message = serverMsg ?: "Application access revoked (HTTP 403)"
                    )
                } else {
                    SafeError.Unauthorized(
                        code = serverCode ?: "FORBIDDEN",
                        message = serverMsg ?: "Access forbidden (HTTP 403)"
                    )
                }
            }
            HttpStatusCode.Conflict -> {
                val serverMsg = extractErrorMessage(rawBody)
                val serverCode = extractErrorCode(rawBody)
                if (serverCode == "ACCESS_REVISION_CHANGED") {
                    SafeError.AccessRevisionChanged(
                        code = serverCode,
                        message = serverMsg ?: "Access revision changed during synchronization (HTTP 409)"
                    )
                } else if (path.contains("/auth/")) {
                    SafeError.Validation(
                        code = serverCode ?: "CONFLICT",
                        message = serverMsg ?: "Email or username already in use (HTTP 409)"
                    )
                } else if (path.contains("/announcements/") || path.contains("/notification-installs/")) {
                    SafeError.Validation(
                        code = serverCode ?: "CONFLICT",
                        message = serverMsg ?: "Operation conflict (HTTP 409)"
                    )
                } else {
                    SafeError.Conflict(
                        code = serverCode ?: "SYNC_CONFLICT",
                        message = "Server rejected this sync attempt; refresh before explicit review",
                        rowId = ""
                    )
                }
            }
            HttpStatusCode.UpgradeRequired -> {
                val serverCode = extractErrorCode(rawBody)
                val serverMsg = extractErrorMessage(rawBody)
                if (serverCode == "COLLABORATION_REQUIRED" || path.startsWith("/api/v1/sync/")) {
                    SafeError.CollaborationRequired(
                        code = serverCode ?: "COLLABORATION_REQUIRED",
                        message = serverMsg ?: "Application upgrade required for collaboration (HTTP 426)"
                    )
                } else {
                    SafeError.ProtocolIncompatible(
                        code = "PROTOCOL_INCOMPATIBLE",
                        message = "Server rejected protocol version (HTTP 426)"
                    )
                }
            }
            HttpStatusCode.NotFound -> {
                val serverMsg = extractErrorMessage(rawBody)
                val serverCode = extractErrorCode(rawBody)
                if (path.startsWith("/api/v1/sync/")) {
                    SafeError.ProtocolIncompatible(
                        code = "SYNC_ROUTE_UNAVAILABLE",
                        message = "Standard sync route unavailable; offline work retained (HTTP 404)"
                    )
                } else {
                    SafeError.Validation(
                        code = serverCode ?: "NOT_FOUND",
                        message = serverMsg ?: "Endpoint not found (HTTP 404)"
                    )
                }
            }
            HttpStatusCode.MethodNotAllowed -> {
                SafeError.Validation(
                    code = "METHOD_NOT_ALLOWED",
                    message = "Method not allowed (HTTP 405)"
                )
            }
            HttpStatusCode.Gone -> {
                val serverMsg = extractErrorMessage(rawBody)
                val serverCode = extractErrorCode(rawBody)
                SafeError.Validation(
                    code = serverCode ?: "GONE",
                    message = serverMsg ?: "Resource expired (HTTP 410)"
                )
            }
            HttpStatusCode.PayloadTooLarge -> {
                val serverMsg = extractErrorMessage(rawBody)
                val serverCode = extractErrorCode(rawBody)
                SafeError.Validation(
                    code = serverCode ?: "REQUEST_TOO_LARGE",
                    message = serverMsg ?: "Request payload too large (HTTP 413)"
                )
            }
            HttpStatusCode.UnprocessableEntity -> {
                val serverMsg = extractErrorMessage(rawBody)
                val serverCode = extractErrorCode(rawBody)
                SafeError.Validation(
                    code = serverCode ?: "UNPROCESSABLE_ENTITY",
                    message = serverMsg ?: "Unprocessable entity (HTTP 422)"
                )
            }
            HttpStatusCode.ServiceUnavailable -> SafeError.Network(
                code = "SERVICE_UNAVAILABLE",
                message = "Service unavailable or admission pending (HTTP 503)"
            )
            HttpStatusCode.BadRequest -> {
                val serverMsg = extractErrorMessage(rawBody)
                val serverCode = extractErrorCode(rawBody)
                SafeError.Validation(
                    code = serverCode ?: "BAD_REQUEST",
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

    suspend fun prepareStream(
        path: String,
        accessToken: String? = null,
        lastEventId: String? = null
    ): HttpStatement {
        val classification = classifyRoute(path)
            ?: throw IllegalArgumentException("Path '$path' is not in the TabbyRush strict endpoint allowlist")
        if (classification != RouteClassification.BEARER_ONLY) {
            throw IllegalArgumentException("Stream route must be BEARER_ONLY")
        }
        val fullUrl = "$validatedOrigin${if (path.startsWith("/")) path else "/$path"}"
        return ktorClient.prepareGet(fullUrl) {
            if (accessToken != null) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (lastEventId != null) {
                header("Last-Event-ID", lastEventId)
            }
            header(HttpHeaders.Accept, "text/event-stream")
            timeout {
                connectTimeoutMillis = 10_000L
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 45_000L
            }
        }
    }
}
