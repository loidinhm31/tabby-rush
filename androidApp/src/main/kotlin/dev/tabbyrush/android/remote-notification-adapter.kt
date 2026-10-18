package dev.tabbyrush.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteNotificationCapabilities
import dev.tabbyrush.shared.contract.RemotePostResult

/**
 * Android native adapter for server admin announcement notifications.
 *
 * Invariants:
 * - Fixed generic copy only ("TabbyRush", "You have a new announcement.").
 *   Zero private announcement content or titles in notifications.
 * - Dedicated channel: "tabbyrush.remote.announcements" (Importance: Default).
 * - System tag: "remote:<messageId>", notification ID: 0 with tag addressing.
 * - Separate group: "tabbyrush.remote.announcements".
 * - Scoped remote cleanup: cancels ONLY remote announcements, never touches task reminders.
 * - Explicit immutable PendingIntent routing to MainActivity.
 */
class AndroidRemoteNotificationAdapter(
    private val context: Context
) : RemoteNotificationAdapter {

    companion object {
        const val CHANNEL_ID = "tabbyrush.remote.announcements"
        const val NOTIFICATION_GROUP = "tabbyrush.remote.announcements"
        const val TAG_PREFIX = "remote:"
        const val NOTIFICATION_ID = 0
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var activationCallback: ((messageId: String, generation: Long) -> Unit)? = null

    init {
        createNotificationChannel()
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.remote_notification_channel_name)
            val descriptionText = context.getString(R.string.remote_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun capabilities(): RemoteNotificationCapabilities {
        val areEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }

        return RemoteNotificationCapabilities(
            alertsSupported = true,
            actionsSupported = true,
            notificationsEnabled = areEnabled && channelEnabled
        )
    }

    override suspend fun show(messageId: String, generation: Long): RemotePostResult {
        val caps = capabilities()
        if (!caps.notificationsEnabled) {
            return RemotePostResult.PermissionDenied
        }

        createNotificationChannel()

        val intent = RemoteNotificationIntents.createAnnouncementIntent(context, messageId, generation)
        val pendingIntent = PendingIntent.getActivity(
            context,
            messageId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.getString(R.string.remote_notification_generic_title)
        val body = context.getString(R.string.remote_notification_generic_body)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.remote_notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setGroup(NOTIFICATION_GROUP)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val tag = "$TAG_PREFIX$messageId"
        notificationManager.notify(tag, NOTIFICATION_ID, notification)
        return RemotePostResult.Posted
    }

    override suspend fun clearRemote(generation: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val active = notificationManager.activeNotifications
                for (sbn in active) {
                    val tag = sbn.tag
                    if (tag != null && tag.startsWith(TAG_PREFIX)) {
                        notificationManager.cancel(tag, sbn.id)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override suspend fun close() {
        activationCallback = null
    }

    override fun setActivationCallback(callback: (messageId: String, generation: Long) -> Unit) {
        activationCallback = callback
    }

    fun dispatchActivation(messageId: String, generation: Long) {
        activationCallback?.invoke(messageId, generation)
    }
}
