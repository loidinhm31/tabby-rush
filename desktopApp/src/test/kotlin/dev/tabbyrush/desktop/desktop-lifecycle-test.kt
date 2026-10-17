package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.BuildMode
import dev.tabbyrush.shared.contract.EndpointPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopLifecycleTest {

    @Test
    fun testSingleInstanceGuardAcquireAndPreventConcurrentLaunch() {
        val tempFile = File.createTempFile("tabbyrush_test_instance", ".lock")
        tempFile.deleteOnExit()

        val guard1 = SingleInstanceGuard.tryAcquire(tempFile)
        assertNotNull(guard1, "First instance must successfully acquire lock")

        val guard2 = SingleInstanceGuard.tryAcquire(tempFile)
        assertNull(guard2, "Second concurrent instance must fail to acquire lock")

        guard1.close()

        val guard3 = SingleInstanceGuard.tryAcquire(tempFile)
        assertNotNull(guard3, "After first guard releases, a new instance may acquire lock")
        guard3.close()
    }

    @Test
    fun testSingleInstanceGuardLockFileRetainedOnClose() {
        val tempFile = File.createTempFile("tabbyrush_inode_test", ".lock")
        tempFile.deleteOnExit()

        val guard = SingleInstanceGuard.tryAcquire(tempFile)
        assertNotNull(guard)
        guard.close()

        assertTrue(tempFile.exists(), "Lock file must remain on disk to prevent inode recycling races")
    }

    @Test
    fun testSyncSchedulerImmediateTrigger() = runTest {
        val count = AtomicInteger(0)
        val scheduler = DesktopSyncScheduler(this) { _ ->
            count.incrementAndGet()
        }

        val key = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        scheduler.triggerImmediate(key)
        testScheduler.advanceUntilIdle()

        assertEquals(1, count.get())
        scheduler.cancelAll()
    }

    @Test
    fun testSyncSchedulerPeriodicInterval() = runTest {
        val count = AtomicInteger(0)
        val scheduler = DesktopSyncScheduler(this, periodicIntervalMillis = 1000L) { _ ->
            count.incrementAndGet()
        }

        val key = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        scheduler.schedulePeriodic(key)

        advanceTimeBy(1001L)
        assertEquals(1, count.get())

        advanceTimeBy(1001L)
        assertEquals(2, count.get())

        scheduler.cancel(key)
        advanceTimeBy(2000L)
        assertEquals(2, count.get(), "No further syncs should run after cancel")

        scheduler.cancelAll()
    }

    @Test
    fun testSyncSchedulerCancelAndAwaitQuiescence() = runTest {
        val isRunning = AtomicBoolean(false)
        val scheduler = DesktopSyncScheduler(this) { _ ->
            isRunning.set(true)
            try {
                delay(5000L)
            } finally {
                isRunning.set(false)
            }
        }
        val key = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        scheduler.triggerImmediate(key)
        advanceTimeBy(100L)
        assertTrue(isRunning.get(), "Sync job should be actively running")

        // cancelAndAwait must cancel and await completion
        scheduler.cancelAndAwait(key)
        testScheduler.advanceUntilIdle()
        assertFalse(isRunning.get(), "Sync job must be quiesced after cancelAndAwait")
    }

    @Test
    fun testReleaseModeDeniesHttpLoopback() {
        val result = EndpointPolicy.validate("http://127.0.0.1:3000", BuildMode.RELEASE)
        assertTrue(result is ApiResult.Failure, "RELEASE build mode must reject HTTP loopback")
    }

    @Test
    fun testReleaseModePermitsHttps() {
        val result = EndpointPolicy.validate("https://hub.glean.dev", BuildMode.RELEASE)
        assertTrue(result is ApiResult.Success, "RELEASE build mode must permit standard HTTPS origin")
    }

    @Test
    fun testDebugModePermitsHttpLoopback() {
        val result = EndpointPolicy.validate("http://127.0.0.1:3000", BuildMode.DEBUG)
        assertTrue(result is ApiResult.Success, "DEBUG build mode must permit HTTP 127.0.0.1:3000")
    }
}
