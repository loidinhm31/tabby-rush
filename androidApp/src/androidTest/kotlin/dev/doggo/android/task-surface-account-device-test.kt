package dev.doggo.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.CompletionChange
import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.contract.SurfaceResult
import dev.doggo.shared.contract.TaskSurfaceAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Android device tests verifying:
 * 1. 4s bounded cold wait timeout behavior for TaskSurfaceAccess.awaitReadiness.
 * 2. Token fencing for read, setCompleted, publishIfCurrent, and validateNavigation.
 * 3. Locked storage state handling.
 * 4. Stale generation rejection and non-current token fencing.
 */
@RunWith(AndroidJUnit4::class)
class TaskSurfaceAccountDeviceTest {

    private lateinit var context: Context
    private val testAccountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val otherAccountKey = AccountKey("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210")
    private val testToken = SurfaceAccountToken(testAccountKey, generation = 5L)
    private val staleToken = SurfaceAccountToken(testAccountKey, generation = 4L)
    private val otherToken = SurfaceAccountToken(otherAccountKey, generation = 1L)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testAwaitReadinessImmediateWhenAlreadyReady() = runBlocking {
        val app = DoggoApplication.instance
        val mutex = Mutex()
        val access = AndroidTaskSurfaceAccess(app, mutex)

        access.updateReadiness(SurfaceReadiness.Ready(testToken))

        val result = access.awaitReadiness(timeoutMs = 1_000L)
        assertTrue(result is SurfaceReadiness.Ready)
        assertEquals(testToken, (result as SurfaceReadiness.Ready).token)
    }

    @Test
    fun testAwaitReadinessImmediateWhenSignedOutOrLocked() = runBlocking {
        val app = DoggoApplication.instance
        val mutex = Mutex()
        val access = AndroidTaskSurfaceAccess(app, mutex)

        access.updateReadiness(SurfaceReadiness.SignedOut)
        assertEquals(SurfaceReadiness.SignedOut, access.awaitReadiness(1_000L))

        access.updateReadiness(SurfaceReadiness.Locked)
        assertEquals(SurfaceReadiness.Locked, access.awaitReadiness(1_000L))
    }

    @Test
    fun testAwaitReadinessTimesOutToUnavailableWhenRestoringStallsBoundedTo4Seconds() = runBlocking {
        val app = DoggoApplication.instance
        val mutex = Mutex()
        val access = AndroidTaskSurfaceAccess(app, mutex)

        // Leaves readiness in Restoring
        access.updateReadiness(SurfaceReadiness.Restoring)

        val startTime = System.currentTimeMillis()
        val result = access.awaitReadiness(timeoutMs = 4_000L)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(SurfaceReadiness.Unavailable, result)
        // Verify bounded execution: must elapse at least 3,900ms and no more than 4,500ms
        assertTrue("Timeout must elapse near 4000ms (elapsed=$elapsed)", elapsed >= 3_900L)
        assertTrue("Timeout must not hang indefinitely (elapsed=$elapsed)", elapsed <= 4_500L)
    }

    @Test
    fun testAwaitReadinessResolvesWhenReadyTransitionOccursConcurrently() = runBlocking {
        val app = DoggoApplication.instance
        val mutex = Mutex()
        val access = AndroidTaskSurfaceAccess(app, mutex)

        access.updateReadiness(SurfaceReadiness.Restoring)

        launch {
            kotlinx.coroutines.delay(200L)
            access.updateReadiness(SurfaceReadiness.Ready(testToken))
        }

        val result = access.awaitReadiness(timeoutMs = 2_000L)
        assertTrue(result is SurfaceReadiness.Ready)
        assertEquals(testToken, (result as SurfaceReadiness.Ready).token)
    }

    @Test
    fun testTokenFencingRejectsMismatchedTokensForReadAndMutate() = runBlocking {
        val app = DoggoApplication.instance
        val mutex = Mutex()
        val access = AndroidTaskSurfaceAccess(app, mutex)

        // Surface readiness is for testToken (gen 5)
        access.updateReadiness(SurfaceReadiness.Ready(testToken))

        // 1. Read with stale token (gen 4) must be Rejected
        val staleRead = access.read(staleToken)
        assertEquals(SurfaceResult.Rejected, staleRead)

        // 2. Read with completely different account token must be Rejected
        val otherRead = access.read(otherToken)
        assertEquals(SurfaceResult.Rejected, otherRead)

        // 3. setCompleted with stale token must be Rejected
        val staleMutate = access.setCompleted(staleToken, "task-1", true, System.currentTimeMillis())
        assertEquals(SurfaceResult.Rejected, staleMutate)

        // 4. setCompleted with different account token must be Rejected
        val otherMutate = access.setCompleted(otherToken, "task-1", true, System.currentTimeMillis())
        assertEquals(SurfaceResult.Rejected, otherMutate)

        // 5. publishIfCurrent with stale token must return false
        var published = false
        val publishedStale = access.publishIfCurrent(staleToken) {
            published = true
        }
        assertFalse(publishedStale)
        assertFalse(published)

        // 6. validateNavigation with stale token must return false
        val validNav = access.validateNavigation(staleToken, null)
        assertFalse(validNav)
    }

    @Test
    fun testOperationsWhenLockedReturnUnavailableOrRejected() = runBlocking {
        val app = DoggoApplication.instance
        val mutex = Mutex()
        val access = AndroidTaskSurfaceAccess(app, mutex)

        access.updateReadiness(SurfaceReadiness.Locked)

        assertEquals(SurfaceResult.Rejected, access.read(testToken))
        assertEquals(SurfaceResult.Rejected, access.setCompleted(testToken, "t1", true, 1000L))

        var published = false
        val publishResult = access.publishIfCurrent(testToken) { published = true }
        assertFalse(publishResult)
        assertFalse(published)

        assertFalse(access.validateNavigation(testToken, "t1"))
    }

    @Test
    fun testOperationsWhenSignedOutReturnRejected() = runBlocking {
        val app = DoggoApplication.instance
        val mutex = Mutex()
        val access = AndroidTaskSurfaceAccess(app, mutex)

        access.updateReadiness(SurfaceReadiness.SignedOut)

        assertEquals(SurfaceResult.Rejected, access.read(testToken))
        assertEquals(SurfaceResult.Rejected, access.setCompleted(testToken, "t1", true, 1000L))

        var published = false
        val publishResult = access.publishIfCurrent(testToken) { published = true }
        assertFalse(publishResult)
        assertFalse(published)

        assertFalse(access.validateNavigation(testToken, "t1"))
    }
}
