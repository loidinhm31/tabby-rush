package dev.doggo.shared.auth

import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.CredentialBundle
import dev.doggo.shared.contract.SafeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SingleFlightRefreshTest {

    @Test
    fun testConcurrentRefreshDeduplication() = runTest {
        var networkCallCount = 0
        val coordinator = SingleFlightRefreshCoordinator()

        // Launch 5 concurrent refresh requests
        val jobs = (1..5).map {
            async {
                coordinator.refresh {
                    delay(50) // simulate network latency
                    networkCallCount++
                    ApiResult.Success(CredentialBundle("new-access-jwt", "new-ref-token"))
                }
            }
        }

        val results = jobs.awaitAll()

        // Exactly one network call made across all 5 requests
        assertEquals(1, networkCallCount)

        // All 5 received the same successor bundle
        for (res in results) {
            assertIs<ApiResult.Success<CredentialBundle>>(res)
            assertEquals("new-access-jwt", res.value.accessToken)
            assertEquals("new-ref-token", res.value.refreshToken)
        }
    }

    @Test
    fun testNetworkFailurePropagatedWithoutLoop() = runTest {
        var callCount = 0
        val coordinator = SingleFlightRefreshCoordinator()

        val result = coordinator.refresh {
            callCount++
            ApiResult.Failure(SafeError.Unauthorized("REFRESH_REVOKED", "Refresh token was revoked"))
        }

        assertEquals(1, callCount)
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.Unauthorized>(result.error)
        assertEquals("REFRESH_REVOKED", result.error.code)
    }

    @Test
    fun testUnexpectedExceptionReturnsSafeError() = runTest {
        val coordinator = SingleFlightRefreshCoordinator()

        val result = coordinator.refresh {
            throw RuntimeException("Unexpected I/O glitch")
        }

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.Network>(result.error)
        assertEquals("REFRESH_EXCEPTION", result.error.code)
        assertEquals("Unexpected error during single-flight refresh", result.error.message)
    }
}
