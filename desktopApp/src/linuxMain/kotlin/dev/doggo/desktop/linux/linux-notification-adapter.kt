package dev.doggo.desktop.linux

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

@DBusInterfaceName("org.freedesktop.Notifications")
interface FreedesktopNotifications : DBusInterface {
    fun GetCapabilities(): List<String>
    fun Notify(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, Variant<*>>,
        expireTimeout: Int
    ): UInt32
    fun CloseNotification(id: UInt32)
}

sealed interface LinuxNotificationStatus {
    data class Success(val notificationId: Long) : LinuxNotificationStatus
    data class Closed(val notificationId: Long) : LinuxNotificationStatus
    data class Unavailable(val reason: String) : LinuxNotificationStatus
    data class Error(val message: String) : LinuxNotificationStatus
}

interface NotificationTransport {
    fun <T> withProxy(block: (FreedesktopNotifications) -> T): T
}

class SessionBusNotificationTransport : NotificationTransport {
    override fun <T> withProxy(block: (FreedesktopNotifications) -> T): T {
        val conn = DBusConnectionBuilder.forSessionBus().build()
        return try {
            val proxy = conn.getRemoteObject(
                "org.freedesktop.Notifications",
                "/org/freedesktop/Notifications",
                FreedesktopNotifications::class.java
            )
            block(proxy)
        } finally {
            try {
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }
}

interface LinuxNotificationAdapter {
    fun checkCapabilities(): Pair<LinuxNotificationStatus, Set<String>>
    fun postNotification(
        replacesId: Long = 0L,
        title: String,
        body: String,
        actions: List<String> = emptyList(),
        expireTimeoutMs: Int = -1
    ): LinuxNotificationStatus
    fun closeNotification(id: Long): LinuxNotificationStatus
}

class DbusLinuxNotificationAdapter(
    private val transport: NotificationTransport = SessionBusNotificationTransport()
) : LinuxNotificationAdapter {

    override fun checkCapabilities(): Pair<LinuxNotificationStatus, Set<String>> {
        return try {
            transport.withProxy { proxy ->
                val caps = proxy.GetCapabilities()
                LinuxNotificationStatus.Success(0L) to caps.toSet()
            }
        } catch (e: DBusExecutionException) {
            LinuxNotificationStatus.Unavailable("Notification daemon unavailable: ${e.message}") to emptySet()
        } catch (e: DBusException) {
            LinuxNotificationStatus.Unavailable("D-Bus session bus unavailable: ${e.message}") to emptySet()
        } catch (e: Exception) {
            LinuxNotificationStatus.Error("Failed to probe notification capabilities: ${e.message}") to emptySet()
        }
    }

    override fun postNotification(
        replacesId: Long,
        title: String,
        body: String,
        actions: List<String>,
        expireTimeoutMs: Int
    ): LinuxNotificationStatus {
        return try {
            transport.withProxy { proxy ->
                val nativeId = proxy.Notify(
                    "Doggo",
                    UInt32(replacesId),
                    "doggo",
                    title,
                    body,
                    actions,
                    emptyMap(),
                    expireTimeoutMs
                )
                LinuxNotificationStatus.Success(nativeId.toLong())
            }
        } catch (e: DBusExecutionException) {
            LinuxNotificationStatus.Unavailable("Notification daemon execution failed: ${e.message}")
        } catch (e: DBusException) {
            LinuxNotificationStatus.Unavailable("D-Bus connection failed: ${e.message}")
        } catch (e: Exception) {
            LinuxNotificationStatus.Error("Notification delivery error: ${e.message}")
        }
    }

    override fun closeNotification(id: Long): LinuxNotificationStatus {
        return try {
            transport.withProxy { proxy ->
                proxy.CloseNotification(UInt32(id))
                LinuxNotificationStatus.Closed(id)
            }
        } catch (e: DBusExecutionException) {
            LinuxNotificationStatus.Unavailable("Notification daemon unavailable: ${e.message}")
        } catch (e: DBusException) {
            LinuxNotificationStatus.Unavailable("D-Bus connection failed: ${e.message}")
        } catch (e: Exception) {
            LinuxNotificationStatus.Error("Close notification error: ${e.message}")
        }
    }
}
