package dev.doggo.desktop.linux

import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxNotificationAdapterTest {

    private class FakeNotificationTransport(
        private val proxy: FreedesktopNotifications? = null,
        private val throwConnectionException: Boolean = false
    ) : NotificationTransport {
        override fun <T> withProxy(block: (FreedesktopNotifications) -> T): T {
            if (throwConnectionException) {
                throw DBusException("Simulated connection failure to user bus")
            }
            return block(requireNotNull(proxy))
        }
    }

    private fun createFakeAdapter(
        capabilities: List<String> = listOf("body", "actions", "icon-static"),
        nextId: UInt32 = UInt32(42L),
        throwExecutionException: Boolean = false,
        throwConnectionException: Boolean = false
    ): DbusLinuxNotificationAdapter {
        if (throwConnectionException) {
            return DbusLinuxNotificationAdapter(FakeNotificationTransport(throwConnectionException = true))
        }

        val fakeProxy = Proxy.newProxyInstance(
            FreedesktopNotifications::class.java.classLoader,
            arrayOf(FreedesktopNotifications::class.java)
        ) { _, method, _ ->
            if (throwExecutionException) {
                throw DBusExecutionException("org.freedesktop.DBus.Error.ServiceUnknown: The name is not activatable")
            }
            when (method.name) {
                "GetCapabilities" -> capabilities
                "Notify" -> nextId
                "CloseNotification" -> null
                "isRemote" -> true
                "getObjectPath" -> "/org/freedesktop/Notifications"
                "getInterface" -> FreedesktopNotifications::class.java
                else -> null
            }
        } as FreedesktopNotifications

        return DbusLinuxNotificationAdapter(FakeNotificationTransport(fakeProxy))
    }

    @Test
    fun testSuccessfulCapabilitiesAndNotification() {
        val adapter = createFakeAdapter(capabilities = listOf("body", "actions"), nextId = UInt32(99L))

        val (capStatus, caps) = adapter.checkCapabilities()
        assertTrue(capStatus is LinuxNotificationStatus.Success)
        assertEquals(setOf("body", "actions"), caps)

        val notifyStatus = adapter.postNotification(
            title = "Task Due",
            body = "Complete quarterly report"
        )
        assertTrue(notifyStatus is LinuxNotificationStatus.Success)
        assertEquals(99L, notifyStatus.notificationId)

        val closeStatus = adapter.closeNotification(99L)
        assertTrue(closeStatus is LinuxNotificationStatus.Closed)
        assertEquals(99L, closeStatus.notificationId)
    }

    @Test
    fun testMissingDaemonServiceUnknownHandling() {
        val adapter = createFakeAdapter(throwExecutionException = true)

        val (capStatus, caps) = adapter.checkCapabilities()
        assertTrue(capStatus is LinuxNotificationStatus.Unavailable)
        assertTrue(caps.isEmpty())
        assertTrue(capStatus.reason.contains("ServiceUnknown") || capStatus.reason.contains("unavailable"))

        val notifyStatus = adapter.postNotification(
            title = "Task Due",
            body = "Complete quarterly report"
        )
        assertTrue(notifyStatus is LinuxNotificationStatus.Unavailable)

        val closeStatus = adapter.closeNotification(123L)
        assertTrue(closeStatus is LinuxNotificationStatus.Unavailable)
    }

    @Test
    fun testMissingDbusSessionConnectionHandling() {
        val adapter = createFakeAdapter(throwConnectionException = true)

        val (capStatus, caps) = adapter.checkCapabilities()
        assertTrue(capStatus is LinuxNotificationStatus.Unavailable)
        assertTrue(caps.isEmpty())
        assertTrue(capStatus.reason.contains("unavailable") || capStatus.reason.contains("failure"))

        val notifyStatus = adapter.postNotification(
            title = "Task Due",
            body = "Complete quarterly report"
        )
        assertTrue(notifyStatus is LinuxNotificationStatus.Unavailable)
    }

    @Test
    fun testHostEnvironmentProbeSafeExecution() {
        val liveAdapter = DbusLinuxNotificationAdapter()
        val (capStatus, _) = liveAdapter.checkCapabilities()
        // Must either succeed (if a daemon is active) or report unavailable safely
        assertTrue(
            capStatus is LinuxNotificationStatus.Success || capStatus is LinuxNotificationStatus.Unavailable,
            "Live host probe must produce Success or Unavailable status, got: $capStatus"
        )
    }
}
