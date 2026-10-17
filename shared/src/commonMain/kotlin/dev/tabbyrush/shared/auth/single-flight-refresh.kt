package dev.tabbyrush.shared.auth

import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.CredentialBundle
import dev.tabbyrush.shared.contract.SafeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates concurrent refresh calls across foreground and background workers.
 * Ensures exactly one network refresh request is in flight at any time.
 */
class SingleFlightRefreshCoordinator {
    private val mutex = Mutex()
    private var inFlightDeferred: CompletableDeferred<ApiResult<CredentialBundle>>? = null

    /**
     * Executes or joins an in-flight token refresh network call.
     */
    suspend fun refresh(
        refreshNetworkCall: suspend () -> ApiResult<CredentialBundle>
    ): ApiResult<CredentialBundle> {
        val (deferred, isLeader) = mutex.withLock {
            val existing = inFlightDeferred
            if (existing != null) {
                Pair(existing, false)
            } else {
                val newDeferred = CompletableDeferred<ApiResult<CredentialBundle>>()
                inFlightDeferred = newDeferred
                Pair(newDeferred, true)
            }
        }

        if (!isLeader) {
            return deferred.await()
        }

        val result: ApiResult<CredentialBundle> = try {
            refreshNetworkCall()
        } catch (c: CancellationException) {
            deferred.cancel(c)
            throw c
        } catch (_: Exception) {
            ApiResult.Failure(
                SafeError.Network(
                    code = "REFRESH_EXCEPTION",
                    message = "Unexpected error during single-flight refresh"
                )
            )
        } finally {
            mutex.withLock {
                inFlightDeferred = null
            }
        }

        deferred.complete(result)
        return result
    }
}
