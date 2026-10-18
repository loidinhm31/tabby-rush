package dev.tabbyrush.desktop.windows

import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsNotificationAdapterTest {

    @Test
    fun testPostLocalNotificationDispatchesToBridge() {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsNotificationAdapter(fakeBridge)

        val status = adapter.postNotification(
            tag = "task_1",
            title = "Task Reminder",
            message = "Your scheduled task is due now",
            group = "reminders"
        )

        assertTrue(status is WindowsNotificationStatus.Success)
        assertEquals("task_1", status.tag)
        assertEquals("reminders", status.group)

        assertEquals(1, fakeBridge.localDispatched.size)
        val record = fakeBridge.localDispatched.first()
        assertEquals("Task Reminder", record.title)
        assertEquals("Your scheduled task is due now", record.body)
        assertEquals("task_1", record.tag)
        assertEquals("reminders", record.group)
    }

    @Test
    fun testCancelNotificationAndGroup() {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsNotificationAdapter(fakeBridge)

        val cancelStatus = adapter.cancelNotification("task_1", "reminders")
        assertTrue(cancelStatus is WindowsNotificationStatus.Cleared)
        assertEquals(listOf(Pair("task_1", "reminders")), fakeBridge.cleared)

        val groupStatus = adapter.cancelGroup("reminders")
        assertTrue(groupStatus is WindowsNotificationStatus.Cleared)
        assertEquals(listOf("reminders"), fakeBridge.clearedGroups)
    }

    @Test
    fun testLocalNotificationRejectsTrRemoteGroup() {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsNotificationAdapter(fakeBridge)

        val status = adapter.postNotification(
            tag = "local_tag",
            title = "Local Task",
            message = "Body",
            group = "tr-remote"
        )

        assertTrue(status is WindowsNotificationStatus.Error)
        assertTrue(status.message.contains("tr-remote"))
        assertEquals(0, fakeBridge.localDispatched.size)
    }

    @Test
    fun testUnavailableBridgeHandling() {
        val fakeBridge = FakeWindowsToastBridge(available = false)
        val adapter = WindowsNotificationAdapter(fakeBridge)

        assertFalse(adapter.isAvailable())
        val status = adapter.postNotification("tag_1", "Title", "Message")
        assertTrue(status is WindowsNotificationStatus.Unavailable)
    }

    @Test
    fun testErrorBridgeHandling() {
        val fakeBridge = FakeWindowsToastBridge(shouldFail = true)
        val adapter = WindowsNotificationAdapter(fakeBridge)

        assertTrue(adapter.isAvailable())
        val status = adapter.postNotification("tag_1", "Title", "Message")
        assertTrue(status is WindowsNotificationStatus.Error)
    }

    @Test
    fun testJnaBridgeWithMockLibrary() {
        val mockLib = object : TabbyRushToastLibrary {
            var createCalled = false
            var showLocalCalled = false
            var lastTitle = ""
            var lastBody = ""

            override fun tr_toast_abi_version(): Int = 1

            override fun tr_toast_create(
                aumid: String,
                activated: TabbyRushToastLibrary.ToastActivatedCallback?,
                user: Pointer?,
                outHandle: LongByReference
            ): Int {
                createCalled = true
                outHandle.value = 1001L
                return 0
            }

            override fun tr_toast_setting(handle: Long, outSetting: IntByReference): Int {
                outSetting.value = 0 // ENABLED
                return 0
            }

            override fun tr_toast_show(
                handle: Long,
                messageId: String,
                generation: Long,
                tag: String,
                group: String
            ): Int = 0

            override fun tr_toast_show_local(
                handle: Long,
                title: String,
                body: String,
                tag: String,
                group: String
            ): Int {
                showLocalCalled = true
                lastTitle = title
                lastBody = body
                return 0
            }

            override fun tr_toast_remove(handle: Long, tag: String, group: String): Int = 0
            override fun tr_toast_clear_group(handle: Long, group: String): Int = 0
            override fun tr_toast_destroy(handle: Long): Int = 0
        }

        val bridge = JnaWindowsToastBridge(
            aumid = "dev.tabbyrush.desktop",
            libraryOverride = mockLib
        )

        assertTrue(bridge.isAvailable())
        assertTrue(mockLib.createCalled)
        assertEquals(WindowsNotificationSetting.ENABLED, bridge.querySetting())

        val status = bridge.showLocal("Title 1", "Body 1", "t1", "g1")
        assertTrue(status is WindowsNotificationStatus.Success)
        assertTrue(mockLib.showLocalCalled)
        assertEquals("Title 1", mockLib.lastTitle)
        assertEquals("Body 1", mockLib.lastBody)

        assertTrue(bridge.destroy())
        assertFalse(bridge.isAvailable())
    }

    @Test
    fun testAbiValidationRejectsMismatchedVersion() {
        val invalidLib = object : TabbyRushToastLibrary {
            var createCalled = false

            override fun tr_toast_abi_version(): Int = 2 // Mismatched ABI version

            override fun tr_toast_create(
                aumid: String,
                activated: TabbyRushToastLibrary.ToastActivatedCallback?,
                user: Pointer?,
                outHandle: LongByReference
            ): Int {
                createCalled = true
                outHandle.value = 9999L
                return 0
            }

            override fun tr_toast_setting(handle: Long, outSetting: IntByReference): Int = 0
            override fun tr_toast_show(handle: Long, messageId: String, generation: Long, tag: String, group: String): Int = 0
            override fun tr_toast_show_local(handle: Long, title: String, body: String, tag: String, group: String): Int = 0
            override fun tr_toast_remove(handle: Long, tag: String, group: String): Int = 0
            override fun tr_toast_clear_group(handle: Long, group: String): Int = 0
            override fun tr_toast_destroy(handle: Long): Int = 0
        }

        // Loader ABI validation directly fails
        assertFalse(TabbyRushToastLoader.validateAbiVersion(invalidLib))

        // Bridge constructed with invalid ABI library does not call create and remains unavailable
        val bridge = JnaWindowsToastBridge(
            aumid = "dev.tabbyrush.desktop",
            libraryOverride = invalidLib
        )

        assertFalse(bridge.isAvailable(), "Bridge must be unavailable when ABI version != 1")
        assertFalse(invalidLib.createCalled, "tr_toast_create must NEVER be invoked on ABI mismatch")
    }

    @Test
    fun testJnaBridgeHandlesDestroyTimeoutSafely() {
        val initialRetained = JnaWindowsToastBridge.getRetainedFailedStateCount()

        val mockLib = object : TabbyRushToastLibrary {
            override fun tr_toast_abi_version(): Int = 1

            override fun tr_toast_create(
                aumid: String,
                activated: TabbyRushToastLibrary.ToastActivatedCallback?,
                user: Pointer?,
                outHandle: LongByReference
            ): Int {
                outHandle.value = 2002L
                return 0
            }

            override fun tr_toast_setting(handle: Long, outSetting: IntByReference): Int = 0
            override fun tr_toast_show(handle: Long, messageId: String, generation: Long, tag: String, group: String): Int = 0
            override fun tr_toast_show_local(handle: Long, title: String, body: String, tag: String, group: String): Int = 0
            override fun tr_toast_remove(handle: Long, tag: String, group: String): Int = 0
            override fun tr_toast_clear_group(handle: Long, group: String): Int = 0

            override fun tr_toast_destroy(handle: Long): Int {
                // Return E_TIMEOUT (0x800705B4)
                return -2147023436
            }
        }

        val bridge = JnaWindowsToastBridge(
            aumid = "dev.tabbyrush.desktop",
            libraryOverride = mockLib
        )

        assertTrue(bridge.isAvailable())
        // Destroy returns false on timeout, and retains state strongly
        val destroyResult = bridge.destroy()
        assertFalse(destroyResult, "destroy() should return false when native destroy times out")
        assertFalse(bridge.isAvailable(), "bridge should be marked unavailable after destroy attempt")

        // Retained failed states list must have captured the handle, library, and callback trampoline
        val currentRetained = JnaWindowsToastBridge.getRetainedFailedStateCount()
        assertTrue(currentRetained > initialRetained, "State must be strongly retained to prevent GC on timeout")
    }
}
