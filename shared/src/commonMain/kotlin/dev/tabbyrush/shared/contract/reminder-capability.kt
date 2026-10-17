package dev.tabbyrush.shared.contract

/**
 * Platform reminder and notification capabilities for the active device and OS.
 */
data class ReminderCapabilities(
    val canScheduleExactAlarms: Boolean,
    val canPostNotifications: Boolean,
    val areNotificationsEnabled: Boolean
) {
    /**
     * True if notifications are allowed by OS permission and enabled in account/system settings.
     */
    val isDeliveryPermitted: Boolean
        get() = canPostNotifications && areNotificationsEnabled
}

/**
 * Interface to query device and OS reminder capabilities.
 */
fun interface ReminderCapabilityChecker {
    suspend fun queryCapabilities(): ReminderCapabilities
}
