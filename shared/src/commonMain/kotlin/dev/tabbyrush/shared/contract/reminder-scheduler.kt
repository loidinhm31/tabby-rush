package dev.tabbyrush.shared.contract

/**
 * Handle registered with OS alarms/schedulers.
 * Invariant: Contains identity only, NEVER task title or user text.
 */
data class ScheduledAlarmHandle(
    val accountKey: String,
    val generation: Long,
    val taskRef: TaskRef,
    val scheduleToken: String,
    val occurrenceIndex: Int,
    val triggerAtEpochMs: Long
) {
    val taskId: String get() = taskRef.rowId
    val ownerUserId: String get() = taskRef.ownerUserId
}

/**
 * NativeReminderAdapter abstracts platform-specific alarm and notification operations
 * on Android, Linux, and Windows.
 */
interface NativeReminderAdapter {
    /**
     * Queries current platform capabilities (alarm permissions, notification posting).
     */
    suspend fun queryCapabilities(): ReminderCapabilities

    /**
     * Reconciles scheduled OS alarms to match [desiredAlarms] for the given account and generation.
     */
    suspend fun reconcileSchedule(
        accountKey: AccountKey,
        generation: Long,
        desiredAlarms: List<ScheduledAlarmHandle>
    )

    /**
     * Posts a notification to the platform notification shade / tray.
     * Returns platform-assigned notification identifier on success, or null on failure.
     */
    suspend fun postOwnedNotification(
        accountKey: AccountKey,
        generation: Long,
        taskRef: TaskRef,
        scheduleToken: String,
        occurrenceIndex: Int,
        title: String,
        dueEpochMs: Long
    ): String?

    /**
     * Removes a previously posted notification with [platformNotificationId] from display.
     */
    suspend fun removeOwnedNotification(
        accountKey: AccountKey,
        platformNotificationId: String
    )

    /**
     * Cancels all scheduled alarms and removes all posted notifications for [accountKey].
     */
    suspend fun cancelAllForAccount(accountKey: AccountKey)
}
