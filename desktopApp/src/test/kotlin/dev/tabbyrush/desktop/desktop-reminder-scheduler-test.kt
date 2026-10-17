package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.data.ReminderOccurrenceStore
import dev.tabbyrush.shared.data.RoomAccountStore
import dev.tabbyrush.shared.data.createInMemoryJvmDatabaseBuilder
import dev.tabbyrush.shared.sync.DeliveryGateState
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import dev.tabbyrush.shared.sync.ReminderSchedulingCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopReminderSchedulerTest {

    private val accountKey = AccountKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

    private class TestNativeReminderAdapter : NativeReminderAdapter {
        val postedReminders = mutableListOf<String>()
        val removedReminders = mutableListOf<String>()

        override suspend fun queryCapabilities(): ReminderCapabilities = ReminderCapabilities(
            canScheduleExactAlarms = true,
            canPostNotifications = true,
            areNotificationsEnabled = true
        )

        override suspend fun reconcileSchedule(
            accountKey: AccountKey,
            generation: Long,
            desiredAlarms: List<ScheduledAlarmHandle>
        ) {}

        override suspend fun postOwnedNotification(
            accountKey: AccountKey,
            generation: Long,
            taskId: String,
            scheduleToken: String,
            occurrenceIndex: Int,
            title: String,
            dueEpochMs: Long
        ): String {
            val id = "notif-$taskId-$occurrenceIndex"
            postedReminders.add(id)
            return id
        }

        override suspend fun removeOwnedNotification(
            accountKey: AccountKey,
            platformNotificationId: String
        ) {
            removedReminders.add(platformNotificationId)
        }

        override suspend fun cancelAllForAccount(accountKey: AccountKey) {
            postedReminders.clear()
        }
    }

    @Test
    fun testSchedulerDeliversDueAlarmsImmediately() = runTest {
        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "test-user", db, "tabbyrush")
        val occurrenceStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val adapter = TestNativeReminderAdapter()
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val currentTime = AtomicLong(1_000_000L)
        val coordinator = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = adapter,
            deliveryGate = deliveryGate,
            getGeneration = { 1L },
            hasWipeMarker = { false },
            clock = { currentTime.get() }
        )

        val scheduler = DesktopReminderScheduler(
            accountKey = accountKey,
            reminderCoordinator = coordinator,
            scope = this,
            clockCheckIntervalMs = 5_000L,
            clock = { currentTime.get() }
        )

        scheduler.start()

        val alarm = ScheduledAlarmHandle(
            accountKey = accountKey.value,
            generation = 1L,
            taskId = "task-1",
            scheduleToken = "tok-1",
            occurrenceIndex = 0,
            triggerAtEpochMs = 1_000_000L
        )

        scheduler.updateDesiredAlarms(listOf(alarm))
        runCurrent()

        // Once processed, the due alarm is delivered and removed from pending
        assertEquals(0, scheduler.getPendingAlarms().size)

        scheduler.cancelAndAwait(accountKey)
        db.close()
    }

    @Test
    fun testSchedulerWaitsForFutureAlarmAndFiresAtDueTime() = runTest {
        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "test-user", db, "tabbyrush")
        val occurrenceStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val adapter = TestNativeReminderAdapter()
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val currentTime = AtomicLong(1_000_000L)
        val coordinator = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = adapter,
            deliveryGate = deliveryGate,
            getGeneration = { 1L },
            hasWipeMarker = { false },
            clock = { currentTime.get() }
        )

        val scheduler = DesktopReminderScheduler(
            accountKey = accountKey,
            reminderCoordinator = coordinator,
            scope = this,
            clockCheckIntervalMs = 15_000L,
            clock = { currentTime.get() }
        )

        scheduler.start()

        // Alarm due in 10 seconds
        val alarm = ScheduledAlarmHandle(
            accountKey = accountKey.value,
            generation = 1L,
            taskId = "task-2",
            scheduleToken = "tok-2",
            occurrenceIndex = 0,
            triggerAtEpochMs = 1_010_000L
        )

        scheduler.updateDesiredAlarms(listOf(alarm))
        runCurrent()

        // Still pending at t=0
        assertEquals(1, scheduler.getPendingAlarms().size)

        // Advance 5 seconds (t = 1_005_000L): still pending
        currentTime.set(1_005_000L)
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, scheduler.getPendingAlarms().size)

        // Advance another 5 seconds to due time (t = 1_010_000L)
        currentTime.set(1_010_000L)
        advanceTimeBy(5_000L)
        runCurrent()

        // Now delivered and pending list empty
        assertEquals(0, scheduler.getPendingAlarms().size)

        scheduler.cancelAndAwait(accountKey)
        db.close()
    }

    @Test
    fun testReplaceBasedUpdateWakesLoopForEarlierDeadline() = runTest {
        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "test-user", db, "tabbyrush")
        val occurrenceStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val adapter = TestNativeReminderAdapter()
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val currentTime = AtomicLong(1_000_000L)
        val coordinator = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = adapter,
            deliveryGate = deliveryGate,
            getGeneration = { 1L },
            hasWipeMarker = { false },
            clock = { currentTime.get() }
        )

        val scheduler = DesktopReminderScheduler(
            accountKey = accountKey,
            reminderCoordinator = coordinator,
            scope = this,
            clockCheckIntervalMs = 30_000L,
            clock = { currentTime.get() }
        )

        scheduler.start()

        // Initially scheduled for 60 seconds in the future
        val alarmFar = ScheduledAlarmHandle(
            accountKey = accountKey.value,
            generation = 1L,
            taskId = "task-far",
            scheduleToken = "tok-far",
            occurrenceIndex = 0,
            triggerAtEpochMs = 1_060_000L
        )
        scheduler.updateDesiredAlarms(listOf(alarmFar))
        runCurrent()

        assertEquals(1, scheduler.getPendingAlarms().size)

        // User edits task, replacing schedule with earlier deadline due now
        val alarmSoon = ScheduledAlarmHandle(
            accountKey = accountKey.value,
            generation = 1L,
            taskId = "task-soon",
            scheduleToken = "tok-soon",
            occurrenceIndex = 0,
            triggerAtEpochMs = 1_000_000L
        )
        scheduler.updateDesiredAlarms(listOf(alarmSoon))
        runCurrent()

        // Immediate wake delivered alarmSoon
        assertEquals(0, scheduler.getPendingAlarms().size)

        scheduler.cancelAndAwait(accountKey)
        db.close()
    }

    @Test
    fun testClockJumpBackwardDoesNotTriggerEarly() = runTest {
        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "test-user", db, "tabbyrush")
        val occurrenceStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val adapter = TestNativeReminderAdapter()
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val currentTime = AtomicLong(1_000_000L)
        val coordinator = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = adapter,
            deliveryGate = deliveryGate,
            getGeneration = { 1L },
            hasWipeMarker = { false },
            clock = { currentTime.get() }
        )

        val scheduler = DesktopReminderScheduler(
            accountKey = accountKey,
            reminderCoordinator = coordinator,
            scope = this,
            clockCheckIntervalMs = 10_000L,
            clock = { currentTime.get() }
        )

        scheduler.start()

        // Alarm scheduled at 1_015_000L (15s future)
        val alarm = ScheduledAlarmHandle(
            accountKey = accountKey.value,
            generation = 1L,
            taskId = "task-clock",
            scheduleToken = "tok-clock",
            occurrenceIndex = 0,
            triggerAtEpochMs = 1_015_000L
        )
        scheduler.updateDesiredAlarms(listOf(alarm))
        runCurrent()

        // Clock adjusted BACKWARD by 1 hour (to 940_000L)
        currentTime.set(940_000L)
        advanceTimeBy(10_000L) // Bounded clock check ticks
        runCurrent()

        // Alarm must NOT be delivered because triggerAtEpochMs (1_015_000L) > now (940_000L)
        assertEquals(1, scheduler.getPendingAlarms().size)

        scheduler.cancelAndAwait(accountKey)
        db.close()
    }

    @Test
    fun testCancelAndAwaitQuiescesSchedulerCleanly() = runTest {
        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "test-user", db, "tabbyrush")
        val occurrenceStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val adapter = TestNativeReminderAdapter()
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val coordinator = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = adapter,
            deliveryGate = deliveryGate,
            getGeneration = { 1L },
            hasWipeMarker = { false }
        )

        val scheduler = DesktopReminderScheduler(
            accountKey = accountKey,
            reminderCoordinator = coordinator,
            scope = this
        )

        scheduler.start()
        scheduler.updateDesiredAlarms(
            listOf(
                ScheduledAlarmHandle(
                    accountKey = accountKey.value,
                    generation = 1L,
                    taskId = "t1",
                    scheduleToken = "s1",
                    occurrenceIndex = 0,
                    triggerAtEpochMs = System.currentTimeMillis() + 60_000L
                )
            )
        )
        runCurrent()

        assertEquals(1, scheduler.getPendingAlarms().size)

        // Cancel and await quiescence
        scheduler.cancelAndAwait(accountKey)
        runCurrent()

        assertEquals(0, scheduler.getPendingAlarms().size)
        db.close()
    }

    @Test
    fun testOverlappingUpdatesConflateDeterministically() = runTest {
        val db = createInMemoryJvmDatabaseBuilder().build()
        val store = RoomAccountStore(accountKey, "test-user", db, "tabbyrush")
        val occurrenceStore = ReminderOccurrenceStore(accountKey, db.tabbyRushDao())
        val adapter = TestNativeReminderAdapter()
        val deliveryGate = ReminderDeliveryGate(accountKey, { 1L }, { false })

        val coordinator = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = adapter,
            deliveryGate = deliveryGate,
            getGeneration = { 1L },
            hasWipeMarker = { false }
        )

        val scheduler = DesktopReminderScheduler(
            accountKey = accountKey,
            reminderCoordinator = coordinator,
            scope = this
        )

        val alarmA = ScheduledAlarmHandle(accountKey.value, 1L, "a", "s", 0, System.currentTimeMillis() + 50_000L)
        val alarmB = ScheduledAlarmHandle(accountKey.value, 1L, "b", "s", 0, System.currentTimeMillis() + 60_000L)
        val alarmC = ScheduledAlarmHandle(accountKey.value, 1L, "c", "s", 0, System.currentTimeMillis() + 70_000L)

        // Rapid successive updates without yielding thread
        scheduler.updateDesiredAlarms(listOf(alarmA))
        scheduler.updateDesiredAlarms(listOf(alarmB))
        scheduler.updateDesiredAlarms(listOf(alarmC))

        scheduler.start()
        runCurrent()

        // The conflated channel must retain only the latest update (alarmC)
        val pending = scheduler.getPendingAlarms()
        assertEquals(1, pending.size)
        assertEquals("c", pending.first().taskId)

        scheduler.cancelAndAwait(accountKey)
        db.close()
    }
}
