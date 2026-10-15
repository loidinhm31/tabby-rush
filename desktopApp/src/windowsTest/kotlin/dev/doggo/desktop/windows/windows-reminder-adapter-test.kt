package dev.doggo.desktop.windows

import dev.doggo.shared.contract.AccountKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsReminderAdapterTest {

    private val accountKey1 = AccountKey("1111111111111111111111111111111111111111111111111111111111111111")
    private val accountKey2 = AccountKey("2222222222222222222222222222222222222222222222222222222222222222")

    @Test
    fun testQueryCapabilitiesTiedToBridgeAvailability() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        val capsAvailable = adapter.queryCapabilities()
        assertTrue(capsAvailable.canScheduleExactAlarms)
        assertTrue(capsAvailable.canPostNotifications)
        assertTrue(capsAvailable.areNotificationsEnabled)

        fakeBridge.available = false
        val capsUnavailable = adapter.queryCapabilities()
        assertTrue(capsUnavailable.canScheduleExactAlarms)
        assertFalse(capsUnavailable.canPostNotifications)
        assertFalse(capsUnavailable.areNotificationsEnabled)
    }

    @Test
    fun testPostOwnedNotificationFormatsCompactTagAndGroup() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        val platformId = adapter.postOwnedNotification(
            accountKey = accountKey1,
            generation = 1L,
            taskId = "01234567-89ab-cdef-0123-456789abcdef",
            scheduleToken = "tok-1",
            occurrenceIndex = 2,
            title = "Task with <special> & characters",
            dueEpochMs = 1_000_000L
        )

        assertNotNull(platformId)
        val expectedGroup = "d_111111111111"
        val expectedTag = "t_01234567-89ab-c_2"
        assertEquals("$expectedGroup|$expectedTag", platformId)

        assertEquals(1, fakeBridge.dispatched.size)
        val (tag, group, xml) = fakeBridge.dispatched.first()
        assertEquals(expectedTag, tag)
        assertEquals(expectedGroup, group)
        assertTrue(xml.contains("&lt;special&gt; &amp; characters"))
    }

    @Test
    fun testRemoveOwnedNotificationClearsFromBridge() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        val platformId = adapter.postOwnedNotification(
            accountKey = accountKey1,
            generation = 1L,
            taskId = "task-x",
            scheduleToken = "tok-x",
            occurrenceIndex = 0,
            title = "Task X",
            dueEpochMs = 1_000_000L
        )
        assertNotNull(platformId)

        // Attempt removal with wrong account has no effect
        adapter.removeOwnedNotification(accountKey2, platformId)
        assertEquals(0, fakeBridge.cleared.size)

        // Removal with owning account clears toast
        adapter.removeOwnedNotification(accountKey1, platformId)
        assertEquals(1, fakeBridge.cleared.size)
        val (tag, group) = fakeBridge.cleared.first()
        assertEquals("t_task-x_0", tag)
        assertEquals("d_111111111111", group)
    }

    @Test
    fun testCancelAllForAccountClearsToasts() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        adapter.postOwnedNotification(accountKey1, 1L, "t1", "s1", 0, "Task 1", 100L)
        adapter.postOwnedNotification(accountKey1, 1L, "t2", "s2", 0, "Task 2", 100L)
        adapter.postOwnedNotification(accountKey2, 1L, "t3", "s3", 0, "Task 3", 100L)

        adapter.cancelAllForAccount(accountKey1)

        assertEquals(2, fakeBridge.cleared.size)
        assertTrue(fakeBridge.cleared.any { it.first == "t_t1_0" })
        assertTrue(fakeBridge.cleared.any { it.first == "t_t2_0" })
        assertFalse(fakeBridge.cleared.any { it.first == "t_t3_0" })
        assertTrue(fakeBridge.clearedGroups.contains("d_111111111111"))
    }

    @Test
    fun testCancelAllForAccountClearsGroupEvenIfInMemorySetEmpty() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        // No in-memory notifications posted (simulating process restart)
        adapter.cancelAllForAccount(accountKey1)

        // Must still clear the account's group in Windows Action Center!
        assertEquals(listOf("d_111111111111"), fakeBridge.clearedGroups)
    }

    @Test
    fun testCancelAllForAccountPropagatesErrorsOnBridgeFailure() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true, shouldFail = false)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        adapter.postOwnedNotification(accountKey1, 1L, "t1", "s1", 0, "Task 1", 100L)

        fakeBridge.shouldFail = true
        kotlin.test.assertFailsWith<IllegalStateException> {
            adapter.cancelAllForAccount(accountKey1)
        }
    }

    @Test
    fun testWindowsProviderTarget() {
        val provider = WindowsReminderAdapterProvider()
        assertEquals("windows", provider.target)
        val adapter = provider.createAdapter()
        assertTrue(adapter is WindowsNativeReminderAdapter)
    }
}
