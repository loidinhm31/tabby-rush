package dev.doggo.shared.contract

object EndpointPolicy {
    private val ALLOWED_DEBUG_HTTP_HOSTS = setOf(
        "localhost:3000",
        "127.0.0.1:3000",
        "10.0.2.2:3000"
    )

    fun validate(raw: String, mode: BuildMode): ApiResult<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "EMPTY_ENDPOINT",
                    message = "Endpoint origin must not be empty",
                    field = "endpoint"
                )
            )
        }

        if (trimmed.contains(" ") || trimmed.contains("\\")) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "INVALID_ENDPOINT_CHARACTERS",
                    message = "Whitespace and backslashes are forbidden in endpoint",
                    field = "endpoint"
                )
            )
        }

        if (trimmed.contains("@")) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "USERINFO_FORBIDDEN",
                    message = "User credentials in endpoint origin are strictly forbidden",
                    field = "endpoint"
                )
            )
        }

        if (trimmed.contains("?") || trimmed.contains("#")) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "QUERY_FRAGMENT_FORBIDDEN",
                    message = "Query parameters and fragments in endpoint origin are strictly forbidden",
                    field = "endpoint"
                )
            )
        }

        val schemeSplit = trimmed.split("://", limit = 2)
        if (schemeSplit.size != 2) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "INVALID_URL_SCHEME",
                    message = "Endpoint must specify scheme (e.g. https://...)",
                    field = "endpoint"
                )
            )
        }

        val scheme = schemeSplit[0].lowercase()
        val rest = schemeSplit[1]

        val hostAndPort: String
        val path: String
        val slashIndex = rest.indexOf('/')
        if (slashIndex >= 0) {
            hostAndPort = rest.substring(0, slashIndex)
            path = rest.substring(slashIndex)
        } else {
            hostAndPort = rest
            path = ""
        }

        if (path.isNotEmpty() && path != "/") {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "PATH_FORBIDDEN",
                    message = "Endpoint origin must not contain subpaths (found '$path')",
                    field = "endpoint"
                )
            )
        }

        if (hostAndPort.isEmpty()) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "HOST_MISSING",
                    message = "Endpoint must include a valid host",
                    field = "endpoint"
                )
            )
        }

        val host: String
        val port: Int?
        if (hostAndPort.contains(':')) {
            val parts = hostAndPort.split(':', limit = 2)
            host = parts[0].lowercase()
            val portStr = parts[1]
            val parsedPort = portStr.toIntOrNull()
            if (parsedPort == null || parsedPort !in 1..65535) {
                return ApiResult.Failure(
                    SafeError.Validation(
                        code = "INVALID_PORT",
                        message = "Endpoint port must be an integer between 1 and 65535 (found '$portStr')",
                        field = "endpoint"
                    )
                )
            }
            port = parsedPort
        } else {
            host = hostAndPort.lowercase()
            port = null
        }

        if (host.isEmpty() || !host.matches(Regex("^[a-z0-9.-]+$")) || host.startsWith(".") || host.endsWith(".")) {
            return ApiResult.Failure(
                SafeError.Validation(
                    code = "INVALID_HOST",
                    message = "Endpoint host is invalid: '$host'",
                    field = "endpoint"
                )
            )
        }

        val canonicalHostAndPort = when {
            scheme == "https" && port == 443 -> host
            scheme == "http" && port == 80 -> host
            port != null -> "$host:$port"
            else -> host
        }

        when (scheme) {
            "https" -> {
                return ApiResult.Success("https://$canonicalHostAndPort")
            }
            "http" -> {
                if (mode == BuildMode.RELEASE) {
                    return ApiResult.Failure(
                        SafeError.Validation(
                            code = "CLEAR_TEXT_HTTP_FORBIDDEN",
                            message = "Cleartext HTTP is forbidden in RELEASE mode",
                            field = "endpoint"
                        )
                    )
                }

                if (canonicalHostAndPort !in ALLOWED_DEBUG_HTTP_HOSTS) {
                    return ApiResult.Failure(
                        SafeError.Validation(
                            code = "DEBUG_HTTP_HOST_DISALLOWED",
                            message = "HTTP in DEBUG mode is only allowed for: $ALLOWED_DEBUG_HTTP_HOSTS (found '$canonicalHostAndPort')",
                            field = "endpoint"
                        )
                    )
                }

                return ApiResult.Success("http://$canonicalHostAndPort")
            }
            else -> {
                return ApiResult.Failure(
                    SafeError.Validation(
                        code = "UNSUPPORTED_SCHEME",
                        message = "Only http and https schemes are supported (found '$scheme')",
                        field = "endpoint"
                    )
                )
            }
        }
    }
}
