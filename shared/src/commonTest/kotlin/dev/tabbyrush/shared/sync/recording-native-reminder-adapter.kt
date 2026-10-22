package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.contract.TaskRef

data class PostedCall(
    val accountKey: AccountKey,
    val generation: Long,
    val taskRef: TaskRef,
    val scheduleToken: String,
    val occurrenceIndex: Int,
    val title: String,
    val dueEpochMs: Long
) {
    val taskId: String get() = taskRef.rowId
    val ownerUserId: String get() = taskRef.ownerUserId
}

class RecordingNativeReminderAdapter(
    var capabilities: ReminderCapabilities = ReminderCapabilities(
        canScheduleExactAlarms = true,
        canPostNotifications = true,
        areNotificationsEnabled = true
    )
) : NativeReminderAdapter {

    val scheduledAlarms = mutableListOf<ScheduledAlarmHandle>()
    val postedCalls = mutableListOf<PostedCall>()
    val removedNotificationIds = mutableListOf<String>()
    var cancelledAccount: AccountKey? = null
    var nextPlatformId: String = "platform-notif-1"

    override suspend fun queryCapabilities(): ReminderCapabilities = capabilities

    override suspend fun reconcileSchedule(
        accountKey: AccountKey,
        generation: Long,
        desiredAlarms: List<ScheduledAlarmHandle>
    ) {
        scheduledAlarms.clear()
        scheduledAlarms.addAll(desiredAlarms)
    }

    override suspend fun postOwnedNotification(
        accountKey: AccountKey,
        generation: Long,
        taskRef: TaskRef,
        scheduleToken: String,
        occurrenceIndex: Int,
        title: String,
        dueEpochMs: Long
    ): String? {
        if (!capabilities.isDeliveryPermitted) return null
        postedCalls.add(
            PostedCall(
                accountKey = accountKey,
                generation = generation,
                taskRef = taskRef,
                scheduleToken = scheduleToken,
                occurrenceIndex = occurrenceIndex,
                title = title,
                dueEpochMs = dueEpochMs
            )
        )
        return nextPlatformId
    }

    override suspend fun removeOwnedNotification(
        accountKey: AccountKey,
        platformNotificationId: String
    ) {
        removedNotificationIds.add(platformNotificationId)
    }

    override suspend fun cancelAllForAccount(accountKey: AccountKey) {
        cancelledAccount = accountKey
        scheduledAlarms.clear()
    }
}
