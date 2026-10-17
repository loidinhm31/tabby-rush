package dev.tabbyrush.android

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Controller evaluating notification capabilities, permissions, and system settings intents
 * specifically for the quiet task surface deadline notification.
 */
object TaskSurfacePermissionController {

    const val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val DEFAULT_CHANNEL_ID = "tabbyrush_task_surface_v2"

    /**
     * Evaluates the availability of the task surface notification.
     * Evaluates in strict order:
     * 1. Opt-in state: if not enabled, OptedOut.
     * 2. App-level notifications: if disabled:
     *    - On API 33+, if POST_NOTIFICATIONS is not granted, PermissionDenied.
     *    - Otherwise, AppDisabled.
     * 3. Channel importance: if channel exists and importance is IMPORTANCE_NONE, ChannelDisabled.
     * 4. Available.
     */
    fun evaluateAvailability(
        context: Context,
        enabled: Boolean,
        channelId: String = DEFAULT_CHANNEL_ID
    ): TaskSurfaceAvailability {
        if (!enabled) {
            return TaskSurfaceAvailability.OptedOut
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return TaskSurfaceAvailability.AppDisabled

        if (!nm.areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= 33) {
                val permissionStatus = context.checkSelfPermission(PERMISSION_POST_NOTIFICATIONS)
                if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                    return TaskSurfaceAvailability.PermissionDenied
                }
            }
            return TaskSurfaceAvailability.AppDisabled
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = nm.getNotificationChannel(channelId)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return TaskSurfaceAvailability.ChannelDisabled
            }
        }

        return TaskSurfaceAvailability.Available
    }

    /**
     * Returns true if POST_NOTIFICATIONS runtime permission is required (API 33+)
     * and not currently granted.
     */
    fun shouldRequestPostNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(PERMISSION_POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    /**
     * Intent leading to the app-level notification settings in system settings.
     */
    fun createAppNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
    }

    /**
     * Intent leading directly to the task surface channel settings in system settings.
     */
    fun createChannelNotificationSettingsIntent(
        context: Context,
        channelId: String = DEFAULT_CHANNEL_ID
    ): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            createAppDetailsSettingsIntent(context)
        }
    }

    /**
     * Fallback intent leading to general application details settings.
     */
    fun createAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
