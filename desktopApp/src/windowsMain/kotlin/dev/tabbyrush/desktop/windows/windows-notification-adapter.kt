package dev.tabbyrush.desktop.windows

import dev.tabbyrush.desktop.DesktopReminderAdapter
import dev.tabbyrush.desktop.DesktopReminderAdapterProvider
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.contract.Sha256
import dev.tabbyrush.shared.contract.TaskRef
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
        const val AUMID = "dev.tabbyrush.desktop"
        const val DEFAULT_GROUP = "tabbyrush_reminders"
    }

    fun isAvailable(): Boolean = bridge.isAvailable()
    fun querySetting(): WindowsNotificationSetting = bridge.querySetting(AUMID)

    fun postNotification(
        tag: String,
        title: String,
        message: String,
        group: String = DEFAULT_GROUP
    ): WindowsNotificationStatus {
        return bridge.showLocal(title = title, body = message, tag = tag, group = group)
    }

    fun cancelNotification(
        tag: String,
        group: String = DEFAULT_GROUP
    ): WindowsNotificationStatus {
        return bridge.clearToast(tag = tag, group = group)
    }

    fun cancelGroup(group: String): WindowsNotificationStatus {
        return bridge.clearGroup(group = group)
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
    private val taskNotificationMap = ConcurrentHashMap<String, ConcurrentHashMap<TaskRef, MutableSet<String>>>()
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
        taskRef: TaskRef,
        scheduleToken: String,
        occurrenceIndex: Int,
        title: String,
        dueEpochMs: Long
    ): String? = withContext(ioDispatcher) {
        val canonicalPayload = "${taskRef.ownerUserId.length}:${taskRef.ownerUserId}:${taskRef.rowId.length}:${taskRef.rowId}:$scheduleToken:$occurrenceIndex"
        val hash = Sha256.sha256Hex(canonicalPayload.encodeToByteArray())
        val tag = "t_${hash.take(32)}"
        val groupHash = Sha256.sha256Hex(accountKey.value.encodeToByteArray())
        val group = "d_${groupHash.take(16)}"
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
                taskNotificationMap.computeIfAbsent(accountKey.value) { ConcurrentHashMap() }
                    .computeIfAbsent(taskRef) { ConcurrentHashMap.newKeySet() }.add(platformId)
                platformId
            }
            else -> null
        }
    }

    override suspend fun cancelNotificationsForTask(
        accountKey: AccountKey,
        taskRef: TaskRef
    ) = withContext(ioDispatcher) {
        val platformIds = taskNotificationMap[accountKey.value]?.remove(taskRef) ?: return@withContext
        for (platformId in platformIds) {
            postedPlatformIds[accountKey.value]?.remove(platformId)
            val parts = platformId.split('|')
            if (parts.size == 2) {
                windowsAdapter.cancelNotification(tag = parts[1], group = parts[0])
            }
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
        taskNotificationMap.remove(accountKey.value)
        val accountIds = postedPlatformIds.remove(accountKey.value) ?: mutableSetOf()
        val groupHash = Sha256.sha256Hex(accountKey.value.encodeToByteArray())
        val group = "d_${groupHash.take(16)}"
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
