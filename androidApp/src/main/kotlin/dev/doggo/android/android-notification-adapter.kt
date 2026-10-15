package dev.doggo.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Candidate adapter managing Android NotificationManager channels and AlarmManager scheduling.
 */
object AndroidNotificationAdapter {
    const val CHANNEL_ID = "doggo_reminders"
    const val CHANNEL_NAME = "Doggo Task Reminders"
    const val CHANNEL_DESCRIPTION = "Offline task deadline and reminder notifications"

    const val ACTION_DELIVER_REMINDER = "dev.doggo.android.ACTION_DELIVER_REMINDER"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_SCHEDULE_TOKEN = "extra_schedule_token"
    const val EXTRA_OCCURRENCE_INDEX = "extra_occurrence_index"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_TITLE = "extra_title"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableLights(true)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun checkCapabilities(context: Context): AndroidReminderCapabilities {
        ensureNotificationChannel(context)
        return AndroidReminderCapabilitiesChecker.checkCapabilities(context, CHANNEL_ID)
    }

    fun createAlarmIntent(
        context: Context,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Long,
        notificationId: Int,
        title: String
    ): Intent {
        return Intent(context, DoggoAlarmReceiver::class.java).apply {
            action = ACTION_DELIVER_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_SCHEDULE_TOKEN, scheduleToken)
            putExtra(EXTRA_OCCURRENCE_INDEX, occurrenceIndex)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TITLE, title)
        }
    }

    fun createPendingIntent(
        context: Context,
        notificationId: Int,
        intent: Intent
    ): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, notificationId, intent, flags)
    }

    fun scheduleAlarm(
        context: Context,
        triggerEpochMs: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Long,
        notificationId: Int,
        title: String
    ): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        val intent = createAlarmIntent(
            context = context,
            taskId = taskId,
            scheduleToken = scheduleToken,
            occurrenceIndex = occurrenceIndex,
            notificationId = notificationId,
            title = title
        )
        val pendingIntent = createPendingIntent(context, notificationId, intent)

        val canExact = AndroidReminderCapabilitiesChecker.canScheduleExactAlarms(context)
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
        }
        return canExact
    }

    fun cancelAlarm(context: Context, notificationId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val dummyIntent = Intent(context, DoggoAlarmReceiver::class.java).apply {
            action = ACTION_DELIVER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            dummyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            am.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun postNotification(
        context: Context,
        notificationId: Int,
        title: String,
        content: String
    ): Boolean {
        if (!AndroidReminderCapabilitiesChecker.canPostNotifications(context, CHANNEL_ID)) {
            return false
        }
        ensureNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        nm.notify(notificationId, notification)
        return true
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        nm.cancel(notificationId)
    }
}
