package dev.doggo.desktop.windows

import dev.doggo.desktop.DesktopReminderAdapter
import dev.doggo.desktop.DesktopReminderAdapterProvider
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.NativeReminderAdapter
import dev.doggo.shared.contract.ReminderCapabilities
import dev.doggo.shared.contract.ScheduledAlarmHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Result status for Windows toast notification actions.
 */
sealed interface WindowsNotificationStatus {
    data class Success(val tag: String, val group: String) : WindowsNotificationStatus
    data class Cleared(val tag: String, val group: String) : WindowsNotificationStatus
    data class Unavailable(val reason: String) : WindowsNotificationStatus
    data class Error(val message: String) : WindowsNotificationStatus
}

/**
 * Windows notification adapter managing AUMID toast presentation and history removal.
 */
class WindowsNotificationAdapter(
    private val bridge: WindowsToastBridge = JnaWindowsToastBridge()
) {
    companion object {
        const val AUMID = "dev.doggo.desktop"
        const val DEFAULT_GROUP = "doggo_reminders"

        fun escapeXml(text: String): String {
            return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun buildToastXml(title: String, message: String): String {
            val safeTitle = escapeXml(title)
            val safeMessage = escapeXml(message)
            return """<toast><visual><binding template="ToastGeneric"><text>$safeTitle</text><text>$safeMessage</text></binding></visual><audio src="ms-winsoundevent:Notification.Default"/></toast>"""
        }
    }

    fun isAvailable(): Boolean = bridge.isAvailable()
    fun querySetting(): WindowsNotificationSetting = bridge.querySetting(AUMID)

    fun postNotification(
        tag: String,
        title: String,
        message: String,
        group: String = DEFAULT_GROUP
    ): WindowsNotificationStatus {
        val xml = buildToastXml(title, message)
        return bridge.showToast(AUMID, xml, tag, group)
    }

    fun cancelNotification(
        tag: String,
        group: String = DEFAULT_GROUP
    ): WindowsNotificationStatus {
        return bridge.clearToast(AUMID, tag, group)
    }

    fun cancelGroup(group: String): WindowsNotificationStatus {
        return bridge.clearGroup(AUMID, group)
    }

    fun cancelAllNotifications(): WindowsNotificationStatus {
        return bridge.clearAllToasts(AUMID)
    }
}

/**
 * Windows desktop native reminder adapter bridging shared NativeReminderAdapter to
 * the Windows Toast notification system.
 * Dispatches IPC/COM operations on Dispatchers.IO to prevent blocking the UI/Swing thread.
 */
class WindowsNativeReminderAdapter(
    val windowsAdapter: WindowsNotificationAdapter = WindowsNotificationAdapter(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DesktopReminderAdapter {

    private val postedPlatformIds = ConcurrentHashMap<String, MutableSet<String>>()
    private var scheduleListener: ((AccountKey, Long, List<ScheduledAlarmHandle>) -> Unit)? = null

    override fun setScheduleListener(listener: ((AccountKey, Long, List<ScheduledAlarmHandle>) -> Unit)?) {
        scheduleListener = listener
    }

    override suspend fun queryCapabilities(): ReminderCapabilities = withContext(ioDispatcher) {
        val available = windowsAdapter.isAvailable()
        val setting = windowsAdapter.querySetting()
        val isEnabled = available && (setting == WindowsNotificationSetting.ENABLED)
        ReminderCapabilities(
            canScheduleExactAlarms = true,
            canPostNotifications = available,
            areNotificationsEnabled = isEnabled
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
        val tag = "t_${taskId.take(16)}_$occurrenceIndex"
        val group = "d_${accountKey.value.take(12)}"
        val safeTitle = title.ifBlank { "Task Reminder" }
        val status = windowsAdapter.postNotification(
            tag = tag,
            title = safeTitle,
            message = "Task reminder is due",
            group = group
        )
        when (status) {
            is WindowsNotificationStatus.Success -> {
                val platformId = "$group|$tag"
                postedPlatformIds.computeIfAbsent(accountKey.value) { ConcurrentHashMap.newKeySet() }.add(platformId)
                platformId
            }
            else -> null
        }
    }

    override suspend fun removeOwnedNotification(
        accountKey: AccountKey,
        platformNotificationId: String
    ) = withContext(ioDispatcher) {
        val accountIds = postedPlatformIds[accountKey.value]
        if (accountIds?.contains(platformNotificationId) == true) {
            val parts = platformNotificationId.split('|')
            if (parts.size == 2) {
                val group = parts[0]
                val tag = parts[1]
                windowsAdapter.cancelNotification(tag = tag, group = group)
                accountIds.remove(platformNotificationId)
            }
        }
    }

    override suspend fun cancelAllForAccount(accountKey: AccountKey) = withContext(ioDispatcher) {
        val accountIds = postedPlatformIds.remove(accountKey.value) ?: mutableSetOf()
        val group = "d_${accountKey.value.take(12)}"
        val errors = mutableListOf<String>()
        for (platformId in accountIds) {
            val parts = platformId.split('|')
            if (parts.size == 2) {
                try {
                    val status = windowsAdapter.cancelNotification(tag = parts[1], group = parts[0])
                    if (status is WindowsNotificationStatus.Error) {
                        errors.add("Failed to clear toast $platformId: ${status.message}")
                    }
                } catch (e: Exception) {
                    errors.add("Exception clearing toast $platformId: ${e.message}")
                }
            }
        }
        try {
            val groupStatus = windowsAdapter.cancelGroup(group)
            if (groupStatus is WindowsNotificationStatus.Error) {
                errors.add("Failed to clear group $group: ${groupStatus.message}")
            }
        } catch (e: Exception) {
            errors.add("Exception clearing group $group: ${e.message}")
        }
        if (errors.isNotEmpty()) {
            throw IllegalStateException("Failed to cancel Windows notifications for ${accountKey.value}: ${errors.joinToString("; ")}")
        }
    }
}

/**
 * ServiceLoader provider for Windows desktop reminder adapter.
 */
class WindowsReminderAdapterProvider : DesktopReminderAdapterProvider {
    override val target: String = "windows"
    override fun createAdapter(): NativeReminderAdapter = WindowsNativeReminderAdapter()
}
