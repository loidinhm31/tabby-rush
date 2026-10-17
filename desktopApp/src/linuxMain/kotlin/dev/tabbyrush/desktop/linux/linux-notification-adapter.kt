package dev.tabbyrush.desktop.linux

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

import dev.tabbyrush.desktop.DesktopReminderAdapter
import dev.tabbyrush.desktop.DesktopReminderAdapterProvider
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
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
                    "TabbyRush",
                    UInt32(replacesId),
                    "tabbyrush",
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

/**
 * Linux desktop native reminder adapter bridging shared NativeReminderAdapter to
 * the session bus Freedesktop Notifications service.
 * Dispatches IPC operations on Dispatchers.IO to prevent blocking the UI/Swing thread.
 */
class LinuxNativeReminderAdapter(
    val dbusAdapter: LinuxNotificationAdapter = DbusLinuxNotificationAdapter(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DesktopReminderAdapter {

    private val postedNotificationIds = ConcurrentHashMap<String, MutableSet<Long>>()
    private var scheduleListener: ((AccountKey, Long, List<ScheduledAlarmHandle>) -> Unit)? = null

    override fun setScheduleListener(listener: ((AccountKey, Long, List<ScheduledAlarmHandle>) -> Unit)?) {
        scheduleListener = listener
    }

    override suspend fun queryCapabilities(): ReminderCapabilities = withContext(ioDispatcher) {
        val (status, _) = dbusAdapter.checkCapabilities()
        val canPost = status is LinuxNotificationStatus.Success
        ReminderCapabilities(
            canScheduleExactAlarms = true,
            canPostNotifications = canPost,
            areNotificationsEnabled = canPost
        )
    }

    override suspend fun reconcileSchedule(
        accountKey: AccountKey,
        generation: Long,
        desiredAlarms: List<ScheduledAlarmHandle>
    ) {
        scheduleListener?.invoke(accountKey, generation, desiredAlarms)
    }

    override suspend fun postOwnedNotification(
        accountKey: AccountKey,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int,
        title: String,
        dueEpochMs: Long
    ): String? = withContext(ioDispatcher) {
        val safeTitle = title.ifBlank { "Task Reminder" }
        val status = dbusAdapter.postNotification(
            replacesId = 0L,
            title = safeTitle,
            body = "Task reminder is due"
        )
        when (status) {
            is LinuxNotificationStatus.Success -> {
                val id = status.notificationId
                postedNotificationIds.computeIfAbsent(accountKey.value) { ConcurrentHashMap.newKeySet() }.add(id)
                id.toString()
            }
            else -> null
        }
    }

    override suspend fun removeOwnedNotification(
        accountKey: AccountKey,
        platformNotificationId: String
    ) = withContext(ioDispatcher) {
        val id = platformNotificationId.toLongOrNull() ?: return@withContext
        val accountIds = postedNotificationIds[accountKey.value]
        if (accountIds?.contains(id) == true) {
            dbusAdapter.closeNotification(id)
            accountIds.remove(id)
        }
    }

    override suspend fun cancelAllForAccount(accountKey: AccountKey) = withContext(ioDispatcher) {
        val accountIds = postedNotificationIds.remove(accountKey.value) ?: return@withContext
        val errors = mutableListOf<String>()
        for (id in accountIds) {
            try {
                val status = dbusAdapter.closeNotification(id)
                if (status is LinuxNotificationStatus.Error) {
                    errors.add("Failed to close notification $id: ${status.message}")
                }
            } catch (e: Exception) {
                errors.add("Exception closing notification $id: ${e.message}")
            }
        }
        if (errors.isNotEmpty()) {
            throw IllegalStateException("Failed to cancel notifications for ${accountKey.value}: ${errors.joinToString("; ")}")
        }
    }
}

/**
 * ServiceLoader provider for Linux desktop reminder adapter.
 */
class LinuxReminderAdapterProvider : DesktopReminderAdapterProvider {
    override val target: String = "linux"
    override fun createAdapter(): NativeReminderAdapter = LinuxNativeReminderAdapter()
}
