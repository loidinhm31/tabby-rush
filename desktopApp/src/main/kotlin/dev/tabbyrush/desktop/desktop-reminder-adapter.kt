package dev.tabbyrush.desktop

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.contract.TaskRef

/**
 * Interface extending [NativeReminderAdapter] for desktop platforms.
 * Allows the desktop coroutine scheduler to receive reconciled desired alarms.
 */
interface DesktopReminderAdapter : NativeReminderAdapter {
    /**
     * Registers a listener to be notified when desired alarms are reconciled by the coordinator.
     */
    fun setScheduleListener(listener: ((AccountKey, Long, List<ScheduledAlarmHandle>) -> Unit)?)

    /**
     * Selectively cancels and removes native notifications posted for a specific task reference.
     */
    suspend fun cancelNotificationsForTask(accountKey: AccountKey, taskRef: TaskRef)
}
