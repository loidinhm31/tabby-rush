package dev.tabbyrush.shared.data

/**
 * Summary of reconciliation actions to align native OS alarms with persisted state.
 */
data class ScheduleReconciliationDelta(
    val alarmsToSchedule: List<ReminderDeliveryLedgerEntity>,
    val obsoletePlatformNotificationIdsToRemove: List<String>
)
