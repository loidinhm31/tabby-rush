package dev.doggo.desktop.linux

import dev.doggo.desktop.PlatformReminderDispatcher
import dev.doggo.shared.contract.AccountKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxReminderAdapterTest {

    private val accountKey1 = AccountKey("1111111111111111111111111111111111111111111111111111111111111111")
    private val accountKey2 = AccountKey("2222222222222222222222222222222222222222222222222222222222222222")

    private class FakeLinuxNotificationAdapter(
        var available: Boolean = true,
        var nextId: Long = 100L,
        var failOnClose: Boolean = false
    ) : LinuxNotificationAdapter {
        val postedNotifications = mutableListOf<Triple<Long, String, String>>()
        val closedIds = mutableListOf<Long>()

        override fun checkCapabilities(): Pair<LinuxNotificationStatus, Set<String>> {
            return if (available) {
                LinuxNotificationStatus.Success(0L) to setOf("body", "actions")
            } else {
                LinuxNotificationStatus.Unavailable("Daemon unavailable") to emptySet()
            }
        }

        override fun postNotification(
            replacesId: Long,
            title: String,
            body: String,
            actions: List<String>,
            expireTimeoutMs: Int
        ): LinuxNotificationStatus {
            return if (available) {
                val id = nextId++
                postedNotifications.add(Triple(id, title, body))
                LinuxNotificationStatus.Success(id)
            } else {
                LinuxNotificationStatus.Unavailable("Daemon unavailable")
            }
        }

        override fun closeNotification(id: Long): LinuxNotificationStatus {
            if (failOnClose) return LinuxNotificationStatus.Error("Daemon failed to close $id")
            closedIds.add(id)
            return LinuxNotificationStatus.Closed(id)
        }
    }

    @Test
    fun testQueryCapabilitiesSuccessAndUnavailable() = runTest {
        val fake = FakeLinuxNotificationAdapter(available = true)
        val adapter = LinuxNativeReminderAdapter(fake)

        val capsAvailable = adapter.queryCapabilities()
        assertTrue(capsAvailable.canScheduleExactAlarms)
        assertTrue(capsAvailable.canPostNotifications)
        assertTrue(capsAvailable.areNotificationsEnabled)

        fake.available = false
        val capsUnavailable = adapter.queryCapabilities()
        assertTrue(capsUnavailable.canScheduleExactAlarms)
        assertFalse(capsUnavailable.canPostNotifications)
        assertFalse(capsUnavailable.areNotificationsEnabled)
    }

    @Test
    fun testPostOwnedNotificationTracksIdPerAccount() = runTest {
        val fake = FakeLinuxNotificationAdapter(available = true, nextId = 50L)
        val adapter = LinuxNativeReminderAdapter(fake)

        val notifId = adapter.postOwnedNotification(
            accountKey = accountKey1,
            generation = 1L,
            taskId = "task-a",
            scheduleToken = "tok-a",
            occurrenceIndex = 0,
            title = "Pay bills",
            dueEpochMs = 1_000_000L
        )

        assertEquals("50", notifId)
        assertEquals(1, fake.postedNotifications.size)
        assertEquals("Pay bills", fake.postedNotifications.first().second)

        // Blank title gets safe default
        val notifIdBlank = adapter.postOwnedNotification(
            accountKey = accountKey1,
            generation = 1L,
            taskId = "task-b",
            scheduleToken = "tok-b",
            occurrenceIndex = 0,
            title = "",
            dueEpochMs = 1_000_000L
        )
        assertEquals("51", notifIdBlank)
        assertEquals("Task Reminder", fake.postedNotifications[1].second)
    }

    @Test
    fun testRemoveOwnedNotificationIsAccountScoped() = runTest {
        val fake = FakeLinuxNotificationAdapter(available = true, nextId = 10L)
        val adapter = LinuxNativeReminderAdapter(fake)

        // Post for account 1 -> gets ID 10
        val id1 = adapter.postOwnedNotification(
            accountKey = accountKey1,
            generation = 1L,
            taskId = "task-1",
            scheduleToken = "tok-1",
            occurrenceIndex = 0,
            title = "Account 1 task",
            dueEpochMs = 1_000_000L
        )
        assertNotNull(id1)

        // Attempt to remove id1 using account 2 must NOT close the notification
        adapter.removeOwnedNotification(accountKey2, id1)
        assertEquals(0, fake.closedIds.size)

        // Removing with owning account 1 closes the notification
        adapter.removeOwnedNotification(accountKey1, id1)
        assertEquals(listOf(10L), fake.closedIds)
    }

    @Test
    fun testCancelAllForAccountOnlyCleansOwnedNotifications() = runTest {
        val fake = FakeLinuxNotificationAdapter(available = true, nextId = 1L)
        val adapter = LinuxNativeReminderAdapter(fake)

        // Post for account 1 (ID 1, 2)
        adapter.postOwnedNotification(accountKey1, 1L, "t1", "s1", 0, "Task 1", 100L)
        adapter.postOwnedNotification(accountKey1, 1L, "t2", "s2", 0, "Task 2", 100L)

        // Post for account 2 (ID 3)
        adapter.postOwnedNotification(accountKey2, 1L, "t3", "s3", 0, "Task 3", 100L)

        // Cancel all for account 1
        adapter.cancelAllForAccount(accountKey1)

        assertEquals(2, fake.closedIds.size)
        assertTrue(fake.closedIds.contains(1L))
        assertTrue(fake.closedIds.contains(2L))
        assertFalse(fake.closedIds.contains(3L))
    }

    @Test
    fun testCancelAllForAccountPropagatesErrorsOnCloseFailure() = runTest {
        val fake = FakeLinuxNotificationAdapter(available = true, nextId = 1L, failOnClose = false)
        val adapter = LinuxNativeReminderAdapter(fake)

        adapter.postOwnedNotification(accountKey1, 1L, "t1", "s1", 0, "Task 1", 100L)

        fake.failOnClose = true
        kotlin.test.assertFailsWith<IllegalStateException> {
            adapter.cancelAllForAccount(accountKey1)
        }
    }

    @Test
    fun testServiceLoaderDispatchesLinuxProvider() {
        val provider = PlatformReminderDispatcher.resolveProvider("linux")
        assertEquals("linux", provider.target)
        val adapter = provider.createAdapter()
        assertTrue(adapter is LinuxNativeReminderAdapter)
    }
}
