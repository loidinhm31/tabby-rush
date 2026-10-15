package dev.doggo.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Non-exported BroadcastReceiver handling AlarmManager reminder delivery.
 * Receives explicit intents, validates payload integrity, and posts offline notifications.
 */
class DoggoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action != AndroidNotificationAdapter.ACTION_DELIVER_REMINDER) return

        val taskId = intent.getStringExtra(AndroidNotificationAdapter.EXTRA_TASK_ID)
        val scheduleToken = intent.getStringExtra(AndroidNotificationAdapter.EXTRA_SCHEDULE_TOKEN)
        val occurrenceIndex = intent.getLongExtra(AndroidNotificationAdapter.EXTRA_OCCURRENCE_INDEX, -1L)
        val notificationId = intent.getIntExtra(AndroidNotificationAdapter.EXTRA_NOTIFICATION_ID, -1)
        val title = intent.getStringExtra(AndroidNotificationAdapter.EXTRA_TITLE)

        if (taskId.isNullOrBlank() || scheduleToken.isNullOrBlank() || notificationId < 0 || title.isNullOrBlank()) {
            return
        }

        // Deliver notification offline
        AndroidNotificationAdapter.postNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            content = "Task reminder: $title"
        )
    }
}
