package dev.tabbyrush.desktop.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull


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
        assertTrue(xml.contains("<audio src=\"ms-winsoundevent:Notification.Default\"/>"))
        assertTrue(xml.endsWith("</toast>"))
    }

    @Test
    fun testToastXmlParsesAsValidXmlDocument() {
        val xml = WindowsNotificationAdapter.buildToastXml("Title & Co", "Body <alert>")
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        assertNotNull(doc)
        assertEquals("toast", doc.documentElement.tagName)
        val audioNodes = doc.getElementsByTagName("audio")
        assertEquals(1, audioNodes.length)
        val audioElement = audioNodes.item(0) as org.w3c.dom.Element
        assertEquals("ms-winsoundevent:Notification.Default", audioElement.getAttribute("src"))
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
    fun testPowerShellBridgeWithMockExecutor() {
        val mockExecutor: (String, Long) -> Pair<Int, String> = { script, _ ->
            when {
                script.contains("History.Remove") -> 0 to ""
                script.contains("CreateToastNotifier") && script.contains("Setting") -> 0 to "Enabled"
                script.contains("Show") -> 0 to ""
                else -> 0 to ""
            }
        }
        val bridge = PowerShellWindowsToastBridge(processExecutor = mockExecutor)
        assertTrue(bridge.isAvailable())
        assertEquals(WindowsNotificationSetting.ENABLED, bridge.querySetting("dev.tabbyrush.desktop"))
        val xml = WindowsNotificationAdapter.buildToastXml("Test", "Body")
        val postStatus = bridge.showToast("dev.tabbyrush.desktop", xml, "t1", "g1")
        assertTrue(postStatus is WindowsNotificationStatus.Success)
        val clearStatus = bridge.clearToast("dev.tabbyrush.desktop", "t1", "g1")
        assertTrue(clearStatus is WindowsNotificationStatus.Cleared)
    }

    @Test
    fun testPowerShellBridgeSimulatedError() {
        val errorExecutor: (String, Long) -> Pair<Int, String> = { script, _ ->
            if (script.contains("XmlDocument") && !script.contains("Show")) {
                0 to ""
            } else {
                1 to "Simulated PowerShell failure"
            }
        }
        val bridge = PowerShellWindowsToastBridge(processExecutor = errorExecutor)
        val xml = WindowsNotificationAdapter.buildToastXml("Test", "Body")
        val status = bridge.showToast("dev.tabbyrush.desktop", xml, "t1", "g1")
        assertTrue(status is WindowsNotificationStatus.Error)
        assertTrue((status as WindowsNotificationStatus.Error).message.contains("Simulated PowerShell failure"))
    }

    @Test
    fun testPowerShellBridgeSettingParsingWithProgressOrMultiLine() {
        val dirtyExecutor: (String, Long) -> Pair<Int, String> = { script, _ ->
            when {
                script.contains("CreateToastNotifier('app.enabled')") -> 0 to "#< CLIXML\nEnabled\n<Objs..."
                script.contains("CreateToastNotifier('app.disabled')") -> 0 to "DisabledForApplication"
                else -> 0 to ""
            }
        }
        val bridge = PowerShellWindowsToastBridge(processExecutor = dirtyExecutor)
        assertEquals(WindowsNotificationSetting.ENABLED, bridge.querySetting("app.enabled"))
        assertEquals(WindowsNotificationSetting.DISABLED_FOR_APPLICATION, bridge.querySetting("app.disabled"))
    }

    @Test
    fun testPowerShellBridgeCacheIsolationByAumid() {
        var queryCount = 0
        val countingExecutor: (String, Long) -> Pair<Int, String> = { script, _ ->
            if (script.contains("CreateToastNotifier")) {
                queryCount++
                if (script.contains("app.a")) 0 to "Enabled" else 0 to "DisabledForApplication"
            } else {
                0 to ""
            }
        }
        val bridge = PowerShellWindowsToastBridge(processExecutor = countingExecutor)
        val settingA = bridge.querySetting("app.a")
        val settingB = bridge.querySetting("app.b")
        assertEquals(WindowsNotificationSetting.ENABLED, settingA)
        assertEquals(WindowsNotificationSetting.DISABLED_FOR_APPLICATION, settingB)
        // Cache hit for app.a within TTL
        val cachedA = bridge.querySetting("app.a")
        assertEquals(WindowsNotificationSetting.ENABLED, cachedA)
        assertEquals(2, queryCount) // 1 for app.a, 1 for app.b, cachedA did not re-query
    }

    @Test
    fun testLiveWindowsToastWhenEnabled() {
        if (System.getProperty("tabbyrush.test.liveToasts") != "true") return
        val bridge = PowerShellWindowsToastBridge()
        if (!bridge.isAvailable()) return
        val xml = WindowsNotificationAdapter.buildToastXml("Test Live", "Body")
        val status = bridge.showToast("dev.tabbyrush.desktop", xml, "test-live-tag", "test-live-group")
        try {
            assertTrue(status is WindowsNotificationStatus.Success)
        } finally {
            bridge.clearToast("dev.tabbyrush.desktop", "test-live-tag", "test-live-group")
        }
    }
}
