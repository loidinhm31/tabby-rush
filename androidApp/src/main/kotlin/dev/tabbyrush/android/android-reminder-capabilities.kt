package dev.tabbyrush.android

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android reminder capabilities reflecting runtime permissions and OS restrictions.
 */
data class AndroidReminderCapabilities(
    val canPostNotifications: Boolean,
    val canScheduleExactAlarms: Boolean,
    val isChannelEnabled: Boolean
)

/**
 * Probes and enforces notification display and alarm scheduling capabilities on Android.
 */
object AndroidReminderCapabilitiesChecker {
    fun canPostNotifications(context: Context, channelId: String): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        if (!nm.areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
            val permissionStatus = context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = nm.getNotificationChannel(channelId)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 31) { // Build.VERSION_CODES.S
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false
            return am.canScheduleExactAlarms()
        }
        return true
    }

    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            val channel = nm.getNotificationChannel(channelId)
            return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }

    fun checkCapabilities(context: Context, channelId: String): AndroidReminderCapabilities {
        return AndroidReminderCapabilities(
            canPostNotifications = canPostNotifications(context, channelId),
            canScheduleExactAlarms = canScheduleExactAlarms(context),
            isChannelEnabled = isChannelEnabled(context, channelId)
        )
    }
}
