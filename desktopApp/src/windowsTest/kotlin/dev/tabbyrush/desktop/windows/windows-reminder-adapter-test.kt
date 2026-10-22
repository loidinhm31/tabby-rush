package dev.tabbyrush.desktop.windows

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.Sha256
import dev.tabbyrush.shared.contract.TaskRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun testQueryCapabilitiesReflectsDisabledNotificationSettings() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true, setting = WindowsNotificationSetting.DISABLED_FOR_APPLICATION)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        val caps = adapter.queryCapabilities()
        assertTrue(caps.canScheduleExactAlarms)
        assertTrue(caps.canPostNotifications)
        assertFalse(caps.areNotificationsEnabled, "Notifications must be reported disabled when setting is DISABLED_FOR_APPLICATION")

        fakeBridge.setting = WindowsNotificationSetting.DISABLED_FOR_USER
        val capsUser = adapter.queryCapabilities()
        assertFalse(capsUser.areNotificationsEnabled, "Notifications must be reported disabled when setting is DISABLED_FOR_USER")

        fakeBridge.setting = WindowsNotificationSetting.DISABLED_BY_GROUP_POLICY
        val capsPolicy = adapter.queryCapabilities()
        assertFalse(capsPolicy.areNotificationsEnabled, "Notifications must be reported disabled when setting is DISABLED_BY_GROUP_POLICY")
    }

    @Test
    fun testPostOwnedNotificationFormatsCompactTagAndGroup() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        val taskRef = TaskRef("user-alice", "01234567-89ab-cdef-0123-456789abcdef")
        val platformId = adapter.postOwnedNotification(
            accountKey = accountKey1,
            generation = 1L,
            taskRef = taskRef,
            scheduleToken = "tok-1",
            occurrenceIndex = 2,
            title = "Task with <special> & characters",
            dueEpochMs = 1_000_000L
        )

        assertNotNull(platformId)
        val canonicalPayload = "${taskRef.ownerUserId.length}:${taskRef.ownerUserId}:${taskRef.rowId.length}:${taskRef.rowId}:tok-1:2"
        val expectedTag = "t_${Sha256.sha256Hex(canonicalPayload.encodeToByteArray()).take(32)}"
        val expectedGroup = "d_${Sha256.sha256Hex(accountKey1.value.encodeToByteArray()).take(16)}"
        assertEquals("$expectedGroup|$expectedTag", platformId)

        assertEquals(1, fakeBridge.dispatched.size)
        val (aumid, tag, xml) = fakeBridge.dispatched.first()
        assertEquals(WindowsNotificationAdapter.AUMID, aumid)
        assertEquals(expectedTag, tag)
        assertTrue(xml.contains("&lt;special&gt; &amp; characters"))
    }

    @Test
    fun testDuplicateRowIdsFromDifferentOwnersProduceDistinctTags() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        val aliceTask = TaskRef("alice", "shared-row-id")
        val bobTask = TaskRef("bob", "shared-row-id")

        val alicePlatformId = adapter.postOwnedNotification(accountKey1, 1L, aliceTask, "tok", 0, "Alice", 100L)
        val bobPlatformId = adapter.postOwnedNotification(accountKey1, 1L, bobTask, "tok", 0, "Bob", 100L)

        assertNotNull(alicePlatformId)
        assertNotNull(bobPlatformId)
        assertFalse(alicePlatformId == bobPlatformId, "Tags for same rowId under different owners must not collide")
    }

    @Test
    fun testRemoveOwnedNotificationClearsFromBridge() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        val taskRef = TaskRef("user-1", "task-x")
        val platformId = adapter.postOwnedNotification(
            accountKey = accountKey1,
            generation = 1L,
            taskRef = taskRef,
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
        val canonicalPayload = "${taskRef.ownerUserId.length}:${taskRef.ownerUserId}:${taskRef.rowId.length}:${taskRef.rowId}:tok-x:0"
        val expectedTag = "t_${Sha256.sha256Hex(canonicalPayload.encodeToByteArray()).take(32)}"
        val expectedGroup = "d_${Sha256.sha256Hex(accountKey1.value.encodeToByteArray()).take(16)}"
        assertEquals(expectedTag, tag)
        assertEquals(expectedGroup, group)
    }

    @Test
    fun testCancelNotificationsForTaskClearsOnlySpecificTaskEvenWithSameRowId() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        // Same rowId under different owners
        val aliceTask = TaskRef("alice", "task-1")
        val bobTask = TaskRef("bob", "task-1")

        adapter.postOwnedNotification(accountKey1, 1L, aliceTask, "s1", 0, "Alice Task", 100L)
        adapter.postOwnedNotification(accountKey1, 1L, bobTask, "s2", 0, "Bob Task", 100L)

        adapter.cancelNotificationsForTask(accountKey1, aliceTask)

        assertEquals(1, fakeBridge.cleared.size)
        val canonicalPayload = "${aliceTask.ownerUserId.length}:${aliceTask.ownerUserId}:${aliceTask.rowId.length}:${aliceTask.rowId}:s1:0"
        val expectedTag = "t_${Sha256.sha256Hex(canonicalPayload.encodeToByteArray()).take(32)}"
        assertEquals(expectedTag, fakeBridge.cleared.first().first)
    }

    @Test
    fun testCancelAllForAccountClearsToastsAndPreservesOtherAccount() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        val t1 = TaskRef("alice", "t1")
        val t2 = TaskRef("alice", "t2")
        val t3 = TaskRef("bob", "t3")
        val p1 = adapter.postOwnedNotification(accountKey1, 1L, t1, "s1", 0, "Task 1", 100L)
        val p2 = adapter.postOwnedNotification(accountKey1, 1L, t2, "s2", 0, "Task 2", 100L)
        val p3 = adapter.postOwnedNotification(accountKey2, 1L, t3, "s3", 0, "Task 3", 100L)

        adapter.cancelAllForAccount(accountKey1)

        val groupHash1 = "d_${Sha256.sha256Hex(accountKey1.value.encodeToByteArray()).take(16)}"
        val groupHash2 = "d_${Sha256.sha256Hex(accountKey2.value.encodeToByteArray()).take(16)}"
        assertEquals(2, fakeBridge.cleared.size)
        assertTrue(fakeBridge.clearedGroups.contains(groupHash1))
        assertFalse(fakeBridge.clearedGroups.contains(groupHash2), "Account 2 notifications must NOT be cleared")
    }

    @Test
    fun testCancelAllForAccountClearsGroupEvenIfInMemorySetEmpty() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        adapter.cancelAllForAccount(accountKey1)

        val groupHash = "d_${Sha256.sha256Hex(accountKey1.value.encodeToByteArray()).take(16)}"
        assertEquals(listOf(groupHash), fakeBridge.clearedGroups)
    }

    @Test
    fun testCancelAllForAccountPropagatesErrorsOnBridgeFailure() = runTest {
        val fakeBridge = FakeWindowsToastBridge(available = true, shouldFail = false)
        val adapter = WindowsNativeReminderAdapter(WindowsNotificationAdapter(fakeBridge))

        adapter.postOwnedNotification(accountKey1, 1L, TaskRef("alice", "t1"), "s1", 0, "Task 1", 100L)

        fakeBridge.shouldFail = true
        assertFailsWith<IllegalStateException> {
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
