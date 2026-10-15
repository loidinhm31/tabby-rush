package dev.doggo.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.doggo.android.AndroidNotificationIntentFactory.CHANNEL_ID

/**
 * Resolved permission and capability state for Android reminder delivery.
 */
sealed class ReminderPermissionState {
    /** Exact alarm timing and notifications are enabled. */
    object Exact : ReminderPermissionState()

    /** Best-effort inexact timing is active (exact alarms not permitted or revoked). */
    object BestEffort : ReminderPermissionState()

    /** Notifications are denied or disabled at the app or OS level. */
    object NotificationDenied : ReminderPermissionState()

    /** The dedicated reminder notification channel is muted or disabled. */
    object ChannelDisabled : ReminderPermissionState()
}

/**
 * Controller managing Android notification permissions, exact-alarm access,
 * and system settings intent generation.
 */
object ReminderPermissionController {

    const val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    fun evaluateState(
        context: Context,
        channelId: String = CHANNEL_ID
    ): ReminderPermissionState {
        AndroidNotificationIntentFactory.ensureNotificationChannel(context)
        // 1. Channel gate
        if (!AndroidReminderCapabilitiesChecker.isChannelEnabled(context, channelId)) {
            return ReminderPermissionState.ChannelDisabled
        }

        // 2. Notification posting gate
        if (!AndroidReminderCapabilitiesChecker.canPostNotifications(context, channelId)) {
            return ReminderPermissionState.NotificationDenied
        }

        // 3. Timing precision gate
        return if (AndroidReminderCapabilitiesChecker.canScheduleExactAlarms(context)) {
            ReminderPermissionState.Exact
        } else {
            ReminderPermissionState.BestEffort
        }
    }

    fun createAppNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
    }

    fun createChannelNotificationSettingsIntent(
        context: Context,
        channelId: String = CHANNEL_ID
    ): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
    }

    fun createExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
    }

    fun createAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun shouldRequestPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(PERMISSION_POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }
}
