package dev.tabbyrush.shared.auth

import dev.tabbyrush.shared.contract.AccessRequestInfo
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.AuthSession
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.CredentialVault
import dev.tabbyrush.shared.contract.HubConfig
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.VaultStatus
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * HubAuthSession manages the lifecycle, in-memory tokens, and state transitions
 * for a Glean Hub authenticated account session.
 *
 * Implements strict security boundaries:
 * - Opaque credentials exist ONLY in memory and the injected CredentialVault.
 * - State flow exposes only AuthState, AccountIdentity, and AccountKey (NEVER tokens or secrets).
 * - Single-flight token refresh across foreground/background with generation fencing.
 * - Credential persistence coordinated under session mutex to eliminate logout races.
 * - Prohibits automatic POST replays in 401 recovery loops (coordinator controls sync retries).
 * - Local logout deletes vault bundle only; acknowledges remote tokens remain valid until expiry.
 */
class HubAuthSession(
    val config: HubConfig,
    val vault: CredentialVault,
    engine: HttpClientEngine? = null,
    private val onCredentialPersistenceFailed: suspend (AccountKey, CredentialBundle, Exception) -> Unit = { _, _, _ -> }
) : AuthSession {

    val authClient = HubAuthClient(config, engine)

    private val sessionMutex = Mutex()

    private val _state = MutableStateFlow(AuthState.UNAUTHENTICATED)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _currentIdentity = MutableStateFlow<AccountIdentity?>(null)
    override val currentIdentity: StateFlow<AccountIdentity?> = _currentIdentity.asStateFlow()

    private val _currentAccountKey = MutableStateFlow<AccountKey?>(null)
    override val currentAccountKey: StateFlow<AccountKey?> = _currentAccountKey.asStateFlow()

    // Opaque in-memory token bundle; never logged or exposed in state flows
    private var inMemoryBundle: CredentialBundle? = null

    // Generation counter increments on quiesce/logout/switch to fence stale async completions
    private var currentGeneration: Long = 0L

    private val refreshCoordinator = SingleFlightRefreshCoordinator()

    /**
     * Attempts to restore an established offline session from the vault without requiring network connectivity.
     */
    suspend fun restoreSession(identity: AccountIdentity, key: AccountKey): Boolean = sessionMutex.withLock {
        if (vault.status != VaultStatus.READY) {
            return false
        }
        val expectedKey = AccountKey.create(config.canonicalOrigin, identity.userId)
        if (key != expectedKey) {
            return false
        }
        val bundle = vault.load(key) ?: return false
        inMemoryBundle = bundle
        _currentIdentity.value = identity
        _currentAccountKey.value = key
        _state.value = AuthState.AUTHENTICATED
        return true
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String
    ): ApiResult<AccountIdentity> {
        val gen = sessionMutex.withLock {
            _state.value = AuthState.AUTHENTICATING
            ++currentGeneration
        }

        val regResult = authClient.register(username, email, password)
        when (regResult) {
            is ApiResult.Failure -> {
                sessionMutex.withLock {
                    if (currentGeneration == gen) {
                        _state.value = AuthState.UNAUTHENTICATED
                        inMemoryBundle = null
                        _currentIdentity.value = null
                        _currentAccountKey.value = null
                    }
                }
                return regResult
            }
            is ApiResult.Success -> {
                val regResponse = regResult.value
                val bundle = regResponse.toCredentialBundle()
                return completeLoginWithMe(regResponse.userId, bundle, gen)
            }
        }
    }

    override suspend fun login(
        email: String,
        password: String
    ): ApiResult<AccountIdentity> {
        val gen = sessionMutex.withLock {
            _state.value = AuthState.AUTHENTICATING
            ++currentGeneration
        }

        val loginResult = authClient.login(email, password)
        when (loginResult) {
            is ApiResult.Failure -> {
                sessionMutex.withLock {
                    if (currentGeneration == gen) {
                        _state.value = AuthState.UNAUTHENTICATED
                        inMemoryBundle = null
                        _currentIdentity.value = null
                        _currentAccountKey.value = null
                    }
                }
                return loginResult
            }
            is ApiResult.Success -> {
                val loginResponse = loginResult.value
                val bundle = loginResponse.toCredentialBundle()
                return completeLoginWithMe(loginResponse.userId, bundle, gen)
            }
        }
    }

    private suspend fun completeLoginWithMe(
        expectedUserId: String,
        bundle: CredentialBundle,
        gen: Long
    ): ApiResult<AccountIdentity> {
        val meResult = authClient.getMe(bundle.accessToken)
        when (meResult) {
            is ApiResult.Failure -> {
                sessionMutex.withLock {
                    if (currentGeneration == gen) {
                        _state.value = AuthState.UNAUTHENTICATED
                        inMemoryBundle = null
                        _currentIdentity.value = null
                        _currentAccountKey.value = null
                    }
                }
                return meResult
            }
            is ApiResult.Success -> {
                val me = meResult.value
                if (me.userId != expectedUserId) {
                    sessionMutex.withLock {
                        if (currentGeneration == gen) {
                            _state.value = AuthState.UNAUTHENTICATED
                            inMemoryBundle = null
                            _currentIdentity.value = null
                            _currentAccountKey.value = null
                        }
                    }
                    return ApiResult.Failure(
                        SafeError.Unauthorized(
                            code = "USER_ID_MISMATCH",
                            message = "Authenticated user ID '${me.userId}' did not match login response '$expectedUserId'"
                        )
                    )
                }

                val identity = me.toAccountIdentity()
                val accountKey = AccountKey.create(config.canonicalOrigin, me.userId)

                var persistenceError: Exception? = null
                sessionMutex.withLock {
                    if (currentGeneration != gen) {
                        return ApiResult.Failure(
                            SafeError.Unauthorized(
                                code = "SESSION_INTERRUPTED",
                                message = "Session generation changed during authentication sequence"
                            )
                        )
                    }

                    try {
                        vault.store(accountKey, bundle)
                    } catch (vaultEx: Exception) {
                        persistenceError = vaultEx
                    }

                    if (persistenceError != null) {
                        _state.value = AuthState.UNAUTHENTICATED
                        inMemoryBundle = null
                        _currentIdentity.value = null
                        _currentAccountKey.value = null
                    } else {
                        inMemoryBundle = bundle
                        _currentIdentity.value = identity
                        _currentAccountKey.value = accountKey

                        if (me.hasTabbyRushAccess(config.appId)) {
                            _state.value = AuthState.AUTHENTICATED
                        } else {
                            _state.value = AuthState.ACCESS_PENDING
                        }
                    }
                }

                persistenceError?.let { vaultEx ->
                    onCredentialPersistenceFailed(accountKey, bundle, vaultEx)
                    return ApiResult.Failure(
                        SafeError.Internal(
                            code = "VAULT_WRITE_FAILED",
                            message = "Failed to securely store credentials in vault"
                        )
                    )
                }

                return ApiResult.Success(identity)
            }
        }
    }

    override suspend fun refresh(): ApiResult<Unit> {
        val (key, bundle, gen) = sessionMutex.withLock {
            val k = _currentAccountKey.value
            val b = inMemoryBundle
            val g = currentGeneration
            if (k == null || b == null) {
                return ApiResult.Failure(
                    SafeError.Unauthorized(
                        code = "NO_SESSION_TO_REFRESH",
                        message = "No active session credentials to refresh"
                    )
                )
            }
            Triple(k, b, g)
        }

        val refreshResult = refreshCoordinator.refresh {
            when (val res = authClient.refresh(bundle.refreshToken)) {
                is ApiResult.Success -> ApiResult.Success(res.value.toCredentialBundle())
                is ApiResult.Failure -> res
            }
        }

        var persistenceError: Exception? = null
        val finalResult: ApiResult<Unit> = sessionMutex.withLock {
            // Generation fence: ensure generation has not changed (e.g. logout)
            if (currentGeneration != gen) {
                return@withLock ApiResult.Failure(
                    SafeError.Unauthorized(
                        code = "GENERATION_STALE",
                        message = "Session generation changed during token refresh; discarded successor"
                    )
                )
            }

            when (refreshResult) {
                is ApiResult.Failure -> {
                    // Conservative reauth per contracts §3: any refresh failure forces reauth
                    _state.value = AuthState.REAUTH_REQUIRED
                    ApiResult.Failure(refreshResult.error)
                }
                is ApiResult.Success -> {
                    val successor = refreshResult.value
                    // Always update in-memory bundle with the successor to avoid using consumed predecessor
                    inMemoryBundle = successor

                    try {
                        vault.store(key, successor)
                    } catch (vaultEx: Exception) {
                        persistenceError = vaultEx
                    }

                    if (persistenceError != null) {
                        ApiResult.Failure(
                            SafeError.Internal(
                                code = "CREDENTIAL_PERSISTENCE_FAILED",
                                message = "Token refreshed on server but local vault persistence failed"
                            )
                        )
                    } else {
                        ApiResult.Success(Unit)
                    }
                }
            }
        }

        persistenceError?.let { vaultEx ->
            val successor = (refreshResult as? ApiResult.Success)?.value
            if (successor != null) {
                onCredentialPersistenceFailed(key, successor, vaultEx)
            }
        }

        return finalResult
    }

    override suspend fun checkAccess(): ApiResult<AccessRequestInfo?> {
        val (token, gen) = sessionMutex.withLock {
            val t = inMemoryBundle?.accessToken
            val g = currentGeneration
            if (t == null) null else Pair(t, g)
        } ?: return ApiResult.Failure(
            SafeError.Unauthorized(
                code = "UNAUTHENTICATED",
                message = "Cannot check access without active credentials"
            )
        )
        val meResult = authClient.getMe(token)
        return when (meResult) {
            is ApiResult.Failure -> {
                if (meResult.error is SafeError.Unauthorized) {
                    when (val refreshOutcome = refresh()) {
                        is ApiResult.Failure -> {
                            sessionMutex.withLock {
                                if (currentGeneration == gen && refreshOutcome.error is SafeError.Unauthorized) {
                                    _state.value = AuthState.REAUTH_REQUIRED
                                }
                            }
                            return refreshOutcome
                        }
                        is ApiResult.Success -> {
                            val refreshedToken = sessionMutex.withLock { inMemoryBundle?.accessToken }
                                ?: return ApiResult.Failure(
                                    SafeError.Unauthorized("UNAUTHENTICATED", "No credentials after refresh")
                                )
                            val secondMe = authClient.getMe(refreshedToken)
                            when (secondMe) {
                                is ApiResult.Failure -> secondMe
                                is ApiResult.Success -> handleMeAccessEvaluation(secondMe.value, refreshedToken, gen)
                            }
                        }
                    }
                } else {
                    meResult
                }
            }
            is ApiResult.Success -> {
                handleMeAccessEvaluation(meResult.value, token, gen)
            }
        }
    }

    private suspend fun handleMeAccessEvaluation(
        me: MeResponse,
        token: String,
        gen: Long
    ): ApiResult<AccessRequestInfo?> {
        val hasAccess = me.hasTabbyRushAccess(config.appId)
        sessionMutex.withLock {
            if (currentGeneration == gen) {
                if (hasAccess) {
                    _state.value = AuthState.AUTHENTICATED
                } else {
                    _state.value = AuthState.ACCESS_PENDING
                }
            }
        }

        // Query access requests to explain status to user/UI
        val listResult = authClient.getAccessRequests(token)
        return when (listResult) {
            is ApiResult.Success -> {
                val requests = listResult.value
                val latest = requests
                    .filter { it.appId == config.appId }
                    .maxByOrNull { it.createdAt }
                    ?.toAccessRequestInfo()
                ApiResult.Success(latest)
            }
            is ApiResult.Failure -> {
                ApiResult.Success(null)
            }
        }
    }

    override suspend fun requestAccess(message: String?): ApiResult<AccessRequestInfo> {
        val (token, gen) = sessionMutex.withLock {
            val t = inMemoryBundle?.accessToken
            val g = currentGeneration
            if (t == null) null else Pair(t, g)
        } ?: return ApiResult.Failure(
            SafeError.Unauthorized(
                code = "UNAUTHENTICATED",
                message = "Cannot submit access request without active credentials"
            )
        )
        val submitResult = authClient.requestAccess(
            accessToken = token,
            appId = config.appId,
            message = message
        )

        return when (submitResult) {
            is ApiResult.Success -> {
                val info = submitResult.value.toAccessRequestInfo()
                ApiResult.Success(info)
            }
            is ApiResult.Failure -> {
                // On failure (e.g. duplicate pending), perform bounded own-list reconciliation
                val listResult = authClient.getAccessRequests(token)
                when (listResult) {
                    is ApiResult.Success -> {
                        val latest = listResult.value
                            .filter { it.appId == config.appId }
                            .maxByOrNull { it.createdAt }
                            ?.toAccessRequestInfo()

                        if (latest != null) {
                            ApiResult.Success(latest)
                        } else {
                            submitResult
                        }
                    }
                    is ApiResult.Failure -> {
                        submitResult
                    }
                }
            }
        }
    }

    /**
     * Executes an authorized operation.
     * Enforces the critical invariant: HTTP auth layer NEVER automatically replays a POST
     * after token refresh. Caller coordinator retains frozen operation intent.
     */
    suspend fun <T> authorizedRequest(
        block: suspend (accessToken: String) -> ApiResult<T>
    ): ApiResult<T> {
        val (token, gen) = sessionMutex.withLock {
            if (_state.value != AuthState.AUTHENTICATED) {
                return ApiResult.Failure(
                    SafeError.Unauthorized(
                        code = "UNAUTHENTICATED",
                        message = "Operation requires an authenticated session"
                    )
                )
            }
            Pair(inMemoryBundle?.accessToken, currentGeneration)
        }

        if (token == null) {
            return ApiResult.Failure(
                SafeError.Unauthorized(
                    code = "UNAUTHENTICATED",
                    message = "Operation requires an authenticated session"
                )
            )
        }

        val firstAttempt = block(token)
        when (firstAttempt) {
            is ApiResult.Success -> return firstAttempt
            is ApiResult.Failure -> {
                if (firstAttempt.error !is SafeError.Unauthorized) {
                    return firstAttempt
                }

                // Invalid API key is an application configuration error, not a refreshable JWT session expiration!
                if (firstAttempt.error.code == "INVALID_API_KEY" ||
                    firstAttempt.error.message.contains("API key", ignoreCase = true)
                ) {
                    return firstAttempt
                }

                // 401 is ambiguous: JWT expiry, missing grant, or bad key.
                // Perform at most one single-flight refresh recovery:
                when (val refreshOutcome = refresh()) {
                    is ApiResult.Failure -> {
                        sessionMutex.withLock {
                            if (currentGeneration == gen && refreshOutcome.error is SafeError.Unauthorized) {
                                _state.value = AuthState.REAUTH_REQUIRED
                            }
                        }
                        return refreshOutcome
                    }
                    is ApiResult.Success -> {
                        val newToken = sessionMutex.withLock { inMemoryBundle?.accessToken }
                            ?: return ApiResult.Failure(
                                SafeError.Unauthorized("UNAUTHENTICATED", "No credentials after refresh")
                            )

                        val meCheck = authClient.getMe(newToken)
                        when (meCheck) {
                            is ApiResult.Failure -> {
                                sessionMutex.withLock {
                                    if (currentGeneration == gen) {
                                        _state.value = AuthState.REAUTH_REQUIRED
                                    }
                                }
                                return ApiResult.Failure(
                                    SafeError.Unauthorized(
                                        code = "REAUTH_REQUIRED",
                                        message = "Token refreshed but user verification failed"
                                    )
                                )
                            }
                            is ApiResult.Success -> {
                                val me = meCheck.value
                                sessionMutex.withLock {
                                    if (currentGeneration == gen) {
                                        if (!me.hasTabbyRushAccess(config.appId)) {
                                            _state.value = AuthState.ACCESS_PENDING
                                        } else {
                                            _state.value = AuthState.AUTHENTICATED
                                        }
                                    }
                                }

                                if (!me.hasTabbyRushAccess(config.appId)) {
                                    return ApiResult.Failure(
                                        SafeError.AccessPending(
                                            code = "ACCESS_PENDING",
                                            message = "Account does not possess the tabbyrush application grant"
                                        )
                                    )
                                } else {
                                    // DO NOT automatically replay the POST request!
                                    return ApiResult.Failure(
                                        SafeError.Unauthorized(
                                            code = "TOKEN_REFRESHED",
                                            message = "Token refreshed; coordinator must control retry of operation"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Increments the generation fence to prevent any in-flight asynchronous operations
     * from restoring credentials or altering session state.
     */
    suspend fun quiesce() = sessionMutex.withLock {
        currentGeneration++
    }

    /**
     * Local-only logout: clears in-memory credentials and deletes vault bundle.
     * Does NOT call remote revocation (Hub issued tokens expire via TTL).
     */
    override suspend fun logout() = sessionMutex.withLock {
        currentGeneration++
        val key = _currentAccountKey.value
        if (key != null) {
            try {
                vault.clear(key)
            } catch (_: Exception) {
                // Best-effort clear
            }
        }
        inMemoryBundle = null
        _currentIdentity.value = null
        _currentAccountKey.value = null
        _state.value = AuthState.UNAUTHENTICATED
    }

    /**
     * Clears local credentials called by 05 leave sequence with generation fencing.
     */
    suspend fun clearLocalCredentials() {
        logout()
    }

    override fun close() {
        authClient.close()
    }
}
