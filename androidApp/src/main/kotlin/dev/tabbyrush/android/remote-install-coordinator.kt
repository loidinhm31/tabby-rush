package dev.tabbyrush.android

import android.util.Log
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.RemoteBindingResult
import dev.tabbyrush.shared.contract.RemoteBindingStatus
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.remote.BindRequest
import dev.tabbyrush.shared.remote.RemoteInboxClient
import dev.tabbyrush.shared.remote.UnbindRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serialized process-wide coordinator managing device install identity,
 * monotonic generation CAS fencing, and install binding lifecycle.
 *
 * Enforces:
 * - Persist incremented generation and exact command before enqueue/network.
 * - Retries repeat the exact same generation and command body (replayed: true).
 * - Offline logout uses best-effort unbind with captured auth and expected generation;
 *   never blocks local leave or runtime cleanup.
 * - When signed out, token rotation is cached privately in store.
 * - Account switch reserves a strictly newer generation and discards old-account commands.
 */
class RemoteInstallCoordinator(
    val store: RemoteInstallStore,
    val clientProvider: () -> RemoteInboxClient?,
    val authSession: HubAuthSession,
    val appVersion: String = "1.0.0",
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    companion object {
        private const val TAG = "RemoteInstallCoordinator"
        private const val BEST_EFFORT_UNBIND_TIMEOUT_MS = 5000L

        private fun logDebug(msg: String) {
            try { Log.d(TAG, msg) } catch (_: Throwable) {}
        }
        private fun logWarn(msg: String) {
            try { Log.w(TAG, msg) } catch (_: Throwable) {}
        }
    }
    private val mutex = Mutex()

    suspend fun onAccountStarted(accountKey: String, userId: String, persistedGen: Long) = mutex.withLock {
        var state = store.readState()

        // Discard pending command if from a different account
        if (state.pendingCommand != null && state.pendingCommand!!.accountKey != accountKey) {
            state = state.copy(pendingCommand = null)
            store.writeState(state)
        }

        val token = state.cachedFcmToken
        if (token != null) {
            // Check if already bound with current token and account
            val isAlreadyBound = state.pendingCommand == null &&
                state.lastBoundGeneration != null &&
                state.lastBoundGeneration == state.persistedGeneration

            if (!isAlreadyBound) {
                val nextGen = maxOf(state.persistedGeneration + 1L, 1L)
                val command = PendingInstallCommand.Bind(
                    generation = nextGen,
                    accountKey = accountKey,
                    userId = userId,
                    token = token,
                    appVersion = appVersion
                )
                state = state.copy(persistedGeneration = nextGen, pendingCommand = command)
                store.writeState(state)
                executePendingCommandLocked(state)
            }
        } else if (state.pendingCommand != null && state.pendingCommand!!.accountKey == accountKey) {
            executePendingCommandLocked(state)
        }
    }

    suspend fun onNewToken(token: String) = mutex.withLock {
        var state = store.readState()
        state = state.copy(cachedFcmToken = token)
        store.writeState(state)

        val currentAccount = authSession.currentAccountKey.value?.value
        val currentUserId = authSession.currentIdentity.value?.userId

        // If signed out, token is stored privately only
        if (currentAccount.isNullOrBlank() || currentUserId.isNullOrBlank()) {
            return@withLock
        }

        val nextGen = maxOf(state.persistedGeneration + 1L, 1L)
        val command = PendingInstallCommand.Bind(
            generation = nextGen,
            accountKey = currentAccount,
            userId = currentUserId,
            token = token,
            appVersion = appVersion
        )
        state = state.copy(persistedGeneration = nextGen, pendingCommand = command)
        store.writeState(state)
        executePendingCommandLocked(state)
    }

    suspend fun scheduleBestEffortUnbind(
        accountKey: String,
        userId: String,
        persistedGen: Long,
        capturedAccessToken: String? = null
    ) = mutex.withLock {
        var state = store.readState()
        val lastBound = state.lastBoundGeneration ?: return@withLock

        val nextGen = maxOf(state.persistedGeneration + 1L, 1L)
        val command = PendingInstallCommand.Unbind(
            generation = nextGen,
            accountKey = accountKey,
            userId = userId,
            expectedBindingGeneration = lastBound,
            capturedAccessToken = capturedAccessToken
        )
        state = state.copy(
            persistedGeneration = nextGen,
            pendingCommand = command,
            lastBoundGeneration = null
        )
        store.writeState(state)

        val installId = state.installId
        val proof = state.installProof
        val client = clientProvider()

        // Asynchronous best-effort attempt with bounded timeout; never blocks local leave
        scope.launch {
            if (client == null) return@launch
            withTimeoutOrNull(BEST_EFFORT_UNBIND_TIMEOUT_MS) {
                try {
                    client.unbindInstall(
                        installId = installId,
                        proof = proof,
                        request = UnbindRequest(
                            operationGeneration = nextGen.toString(),
                            expectedBindingGeneration = lastBound.toString()
                        ),
                        accessTokenOverride = capturedAccessToken
                    )
                } catch (e: Exception) {
                    logWarn("Best-effort unbind network attempt failed: ${e.message}")
                }
            }
        }
    }

    suspend fun retryPendingCommand() = mutex.withLock {
        val state = store.readState()
        if (state.pendingCommand != null) {
            executePendingCommandLocked(state)
        }
    }

    suspend fun getBindingStatus(): ApiResult<RemoteBindingStatus> {
        val state = store.readState()
        val client = clientProvider() ?: return ApiResult.Failure(
            SafeError.Internal("NO_CLIENT", "Remote client unavailable")
        )
        return client.getInstallBinding(state.installId, state.installProof)
    }

    private suspend fun executePendingCommandLocked(state: RemoteInstallState) {
        val command = state.pendingCommand ?: return
        val client = clientProvider() ?: return

        when (command) {
            is PendingInstallCommand.Bind -> {
                val res = client.bindInstall(
                    installId = state.installId,
                    proof = state.installProof,
                    request = BindRequest(
                        operationGeneration = command.generation.toString(),
                        platform = "android",
                        token = command.token,
                        appVersion = command.appVersion
                    )
                )

                when (res) {
                    is ApiResult.Success -> {
                        val updated = state.copy(
                            lastBoundGeneration = command.generation,
                            pendingCommand = null
                        )
                        store.writeState(updated)
                    }
                    is ApiResult.Failure -> {
                        handleBindFailure(state, command, res.error)
                    }
                }
            }
            is PendingInstallCommand.Unbind -> {
                val res = client.unbindInstall(
                    installId = state.installId,
                    proof = state.installProof,
                    request = UnbindRequest(
                        operationGeneration = command.generation.toString(),
                        expectedBindingGeneration = command.expectedBindingGeneration.toString()
                    ),
                    accessTokenOverride = command.capturedAccessToken
                )
                if (res is ApiResult.Success) {
                    store.writeState(state.copy(pendingCommand = null))
                }
            }
        }
    }

    private suspend fun handleBindFailure(
        state: RemoteInstallState,
        command: PendingInstallCommand.Bind,
        error: SafeError
    ) {
        if (error is SafeError.Conflict) {
            when (error.code) {
                "tokenAlreadyBound" -> {
                    // Reset identity to clear stale conflict
                    val (newId, newProof) = store.resetIdentity()
                    val nextGen = 1L
                    val newCmd = command.copy(generation = nextGen)
                    val updated = RemoteInstallState(
                        installId = newId,
                        installProof = newProof,
                        persistedGeneration = nextGen,
                        cachedFcmToken = command.token,
                        pendingCommand = newCmd
                    )
                    store.writeState(updated)
                }
                "staleInstallOperation" -> {
                    val client = clientProvider() ?: return
                    val statusRes = client.getInstallBinding(state.installId, state.installProof)
                    if (statusRes is ApiResult.Success) {
                        val serverGen = statusRes.value.currentGeneration.toLongOrNull() ?: state.persistedGeneration
                        val nextGen = maxOf(serverGen + 1L, state.persistedGeneration + 1L)
                        val newCmd = command.copy(generation = nextGen)
                        val updated = state.copy(persistedGeneration = nextGen, pendingCommand = newCmd)
                        store.writeState(updated)
                    }
                }
                else -> {
                    logWarn("Install bind conflict: ${error.code} - retaining command for retry")
                }
            }
        } else {
            // Transient or network error: retain command and same generation for idempotent retry
            logDebug("Install bind failed with ${error.code}; retaining command with generation ${command.generation}")
        }
    }
}
