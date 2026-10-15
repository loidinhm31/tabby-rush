package dev.doggo.desktop.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeWindowsToastBridge(
    var available: Boolean = true,
    var shouldFail: Boolean = false
) : WindowsToastBridge {
    val dispatched = mutableListOf<Triple<String, String, String>>()
    val cleared = mutableListOf<Pair<String, String>>()
    var clearedAllCount = 0
    val clearedGroups = mutableListOf<String>()

    override fun isAvailable(): Boolean = available

    override fun showToast(
        aumid: String,
        xmlPayload: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated bridge failure")
        dispatched.add(Triple(aumid, tag, xmlPayload))
        return WindowsNotificationStatus.Success(tag, group)
    }

    override fun clearToast(
        aumid: String,
        tag: String,
        group: String
    ): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated clear failure")
        cleared.add(Pair(tag, group))
        return WindowsNotificationStatus.Cleared(tag, group)
    }

    override fun clearGroup(aumid: String, group: String): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        if (shouldFail) return WindowsNotificationStatus.Error("Simulated clear group failure")
        clearedGroups.add(group)
        return WindowsNotificationStatus.Cleared("", group)
    }

    override fun clearAllToasts(aumid: String): WindowsNotificationStatus {
        if (!available) return WindowsNotificationStatus.Unavailable("Bridge not available")
        clearedAllCount++
        return WindowsNotificationStatus.Cleared("*", "*")
    }
}

class WindowsNotificationAdapterTest {

    @Test
    fun testXmlEscaping() {
        val input = "Buy milk & eggs <bread> \"butter\" 'cheese'"
        val escaped = WindowsNotificationAdapter.escapeXml(input)
        assertEquals("Buy milk &amp; eggs &lt;bread&gt; &quot;butter&quot; &apos;cheese&apos;", escaped)
    }

    @Test
    fun testToastXmlStructure() {
        val xml = WindowsNotificationAdapter.buildToastXml("Title & Co", "Body <alert>")
        assertTrue(xml.startsWith("<toast><visual>"))
        assertTrue(xml.contains("<text>Title &amp; Co</text>"))
        assertTrue(xml.contains("<text>Body &lt;alert&gt;</text>"))
        assertTrue(xml.endsWith("</visual></toast>"))
    }

    @Test
    fun testPostAndCancelNotification() {
        val fakeBridge = FakeWindowsToastBridge()
        val adapter = WindowsNotificationAdapter(fakeBridge)

        val postStatus = adapter.postNotification(
            tag = "task-123",
            title = "Deadline",
            message = "Prepare slides"
        )
        assertTrue(postStatus is WindowsNotificationStatus.Success)
        assertEquals("task-123", postStatus.tag)
        assertEquals(WindowsNotificationAdapter.DEFAULT_GROUP, postStatus.group)
        assertEquals(1, fakeBridge.dispatched.size)
        assertEquals(WindowsNotificationAdapter.AUMID, fakeBridge.dispatched[0].first)
        assertEquals("task-123", fakeBridge.dispatched[0].second)

        val cancelStatus = adapter.cancelNotification("task-123")
        assertTrue(cancelStatus is WindowsNotificationStatus.Cleared)
        assertEquals(1, fakeBridge.cleared.size)
        assertEquals("task-123", fakeBridge.cleared[0].first)
    }

    @Test
    fun testUnavailableBridgeHandling() {
        val fakeBridge = FakeWindowsToastBridge(available = false)
        val adapter = WindowsNotificationAdapter(fakeBridge)

        assertFalse(adapter.isAvailable())
        val status = adapter.postNotification("task-1", "T", "M")
        assertTrue(status is WindowsNotificationStatus.Unavailable)
    }

    @Test
    fun testErrorBridgeHandling() {
        val fakeBridge = FakeWindowsToastBridge(available = true, shouldFail = true)
        val adapter = WindowsNotificationAdapter(fakeBridge)

        val status = adapter.postNotification("task-1", "T", "M")
        assertTrue(status is WindowsNotificationStatus.Error)
    }

    @Test
    fun testJnaBridgeSafeUnavailability() {
        val bridge = JnaWindowsToastBridge()
        assertFalse(bridge.isAvailable(), "JnaWindowsToastBridge must not report available until implemented in Phase 09")
        val postStatus = bridge.showToast("dev.doggo.desktop", "<xml/>", "tag", "group")
        assertTrue(postStatus is WindowsNotificationStatus.Unavailable)
        val clearStatus = bridge.clearToast("dev.doggo.desktop", "tag", "group")
        assertTrue(clearStatus is WindowsNotificationStatus.Unavailable)
        val clearAllStatus = bridge.clearAllToasts("dev.doggo.desktop")
        assertTrue(clearAllStatus is WindowsNotificationStatus.Unavailable)
    }
}
