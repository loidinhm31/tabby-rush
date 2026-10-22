package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.data.ReminderOccurrenceStore
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.createInMemoryJvmDatabaseBuilder
import dev.tabbyrush.shared.sync.LeaveSchedulerCancelGate
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import dev.tabbyrush.shared.sync.ReminderSchedulingCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
@OptIn(ExperimentalCoroutinesApi::class)
class CompositeDesktopLeaveCancelGateTest {

    private val accountKey = AccountKey("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")

    private class RecordingNativeAdapter : NativeReminderAdapter {
        override suspend fun queryCapabilities(): ReminderCapabilities = ReminderCapabilities(true, true, true)
        override suspend fun reconcileSchedule(accountKey: AccountKey, generation: Long, desiredAlarms: List<ScheduledAlarmHandle>) {}
        override suspend fun postOwnedNotification(accountKey: AccountKey, generation: Long, taskRef: dev.tabbyrush.shared.contract.TaskRef, scheduleToken: String, occurrenceIndex: Int, title: String, dueEpochMs: Long): String? = "id"
        override suspend fun removeOwnedNotification(accountKey: AccountKey, platformNotificationId: String) {}
        override suspend fun cancelAllForAccount(accountKey: AccountKey) {}
    }

    @Test
    fun testSequentialQuiescenceExecution() = runTest {
        val executionOrder = mutableListOf<String>()

        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val surfaceCancelGate = object : LeaveSchedulerCancelGate {
            override suspend fun cancelAndAwait(accountKey: AccountKey) {
                executionOrder.add("surface_drained")
            }
        }

        val surfaceObserverJob = launch {
            delay(Long.MAX_VALUE)
        }
        surfaceObserverJob.invokeOnCompletion {
            executionOrder.add("surface_observer_cancelled")
        }

        val scheduleObserverJob = launch {
            delay(Long.MAX_VALUE)
        }
        scheduleObserverJob.invokeOnCompletion {
            executionOrder.add("schedule_observer_cancelled")
        }

        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "user", db, "tabbyrush")
        val occStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val coord = ReminderSchedulingCoordinator(
            accountKey, store, occStore, RecordingNativeAdapter(), deliveryGate, { 1L }, { false }
        )

        val reminderScheduler = DesktopReminderScheduler(accountKey, coord, this)
        reminderScheduler.start()

        val syncCancelGate = object : LeaveSchedulerCancelGate {
            override suspend fun cancelAndAwait(accountKey: AccountKey) {
                executionOrder.add("sync_cancelled")
            }
        }

        val reminderCancelGate = object : LeaveSchedulerCancelGate {
            override suspend fun cancelAndAwait(accountKey: AccountKey) {
                executionOrder.add("reminders_cancelled")
            }
        }

        val compositeGate = CompositeDesktopLeaveCancelGate(
            deliveryGate = deliveryGate,
            scheduleObserverJob = scheduleObserverJob,
            reminderScheduler = reminderScheduler,
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate,
            surfaceObserverJob = surfaceObserverJob,
            surfaceCancelGate = surfaceCancelGate
        )

        compositeGate.cancelAndAwait(accountKey)

        assertEquals("surface_drained", executionOrder[0])
        assertEquals("surface_observer_cancelled", executionOrder[1])
        assertEquals("schedule_observer_cancelled", executionOrder[2])
        assertEquals("sync_cancelled", executionOrder[3])
        assertEquals("reminders_cancelled", executionOrder[4])

        db.close()
    }

    @Test
    fun testDrainFailureFailsClosedWithoutProceeding() = runTest {
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val admissionAcquired = kotlinx.coroutines.CompletableDeferred<Unit>()
        val admissionHoldJob = launch {
            deliveryGate.withDeliveryAdmission(1L) {
                admissionAcquired.complete(Unit)
                delay(Long.MAX_VALUE)
            }
        }
        admissionAcquired.await()

        val observerJob = Job()
        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "user", db, "tabbyrush")
        val occStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val coord = ReminderSchedulingCoordinator(
            accountKey, store, occStore, RecordingNativeAdapter(), deliveryGate, { 1L }, { false }
        )
        val reminderScheduler = DesktopReminderScheduler(accountKey, coord, this)

        var syncCancelled = false
        val syncCancelGate = object : LeaveSchedulerCancelGate {
            override suspend fun cancelAndAwait(accountKey: AccountKey) {
                syncCancelled = true
            }
        }

        val reminderCancelGate = object : LeaveSchedulerCancelGate {
            override suspend fun cancelAndAwait(accountKey: AccountKey) {}
        }

        val compositeGate = CompositeDesktopLeaveCancelGate(
            deliveryGate = deliveryGate,
            scheduleObserverJob = observerJob,
            reminderScheduler = reminderScheduler,
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate,
            drainTimeoutMs = 50L
        )

        assertFailsWith<IllegalStateException> {
            compositeGate.cancelAndAwait(accountKey)
        }

        assertTrue(!syncCancelled)

        admissionHoldJob.cancel()
        db.close()
    }

    @Test
    fun testSurfaceDrainFailureFailsClosed() = runTest {
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val failingSurfaceCancelGate = object : LeaveSchedulerCancelGate {
            override suspend fun cancelAndAwait(accountKey: AccountKey) {
                throw IllegalStateException("Timed out draining desktop task surface operations for $accountKey")
            }
        }

        val observerJob = Job()
        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "user", db, "tabbyrush")
        val occStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val coord = ReminderSchedulingCoordinator(
            accountKey, store, occStore, RecordingNativeAdapter(), deliveryGate, { 1L }, { false }
        )
        val reminderScheduler = DesktopReminderScheduler(accountKey, coord, this)

        var syncCancelled = false
        val syncCancelGate = object : LeaveSchedulerCancelGate {
            override suspend fun cancelAndAwait(accountKey: AccountKey) {
                syncCancelled = true
            }
        }

        val reminderCancelGate = object : LeaveSchedulerCancelGate {
            override suspend fun cancelAndAwait(accountKey: AccountKey) {}
        }

        val compositeGate = CompositeDesktopLeaveCancelGate(
            deliveryGate = deliveryGate,
            scheduleObserverJob = observerJob,
            reminderScheduler = reminderScheduler,
            syncCancelGate = syncCancelGate,
            reminderCancelGate = reminderCancelGate,
            surfaceCancelGate = failingSurfaceCancelGate
        )

        assertFailsWith<IllegalStateException> {
            compositeGate.cancelAndAwait(accountKey)
        }

        assertFalse(syncCancelled, "Sync cancel must not be invoked if surface draining fails")
        db.close()
    }
}
