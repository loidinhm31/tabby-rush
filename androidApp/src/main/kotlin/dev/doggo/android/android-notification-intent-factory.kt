package dev.doggo.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.doggo.shared.contract.AccountKey

/**
 * Factory utilities for Android notification channels, explicit alarm intents,
 * and immutable PendingIntents with identity-only data URIs.
 */
object AndroidNotificationIntentFactory {

    const val CHANNEL_ID = "doggo_reminders"
    const val CHANNEL_NAME = "Doggo Task Reminders"
    const val CHANNEL_DESCRIPTION = "Offline task deadline and reminder notifications"

    const val ACTION_DELIVER_REMINDER = "dev.doggo.android.ACTION_DELIVER_REMINDER"
    const val ACTION_VIEW_TASK = "dev.doggo.android.ACTION_VIEW_TASK"

    const val EXTRA_ACCOUNT_KEY = "extra_account_key"
    const val EXTRA_GENERATION = "extra_generation"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_SCHEDULE_TOKEN = "extra_schedule_token"
    const val EXTRA_OCCURRENCE_INDEX = "extra_occurrence_index"

    fun getAccountNotificationTag(accountKey: AccountKey): String = "doggo_${accountKey.value}"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun createAlarmIntent(
        context: Context,
        accountKey: AccountKey,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): Intent {
        return Intent(context, DoggoAlarmReceiver::class.java).apply {
            action = ACTION_DELIVER_REMINDER
            // Data URI participates in filter equality without carrying user text
            data = Uri.parse("doggo://alarm/${accountKey.value}/$generation/$taskId/$scheduleToken/$occurrenceIndex")
            putExtra(EXTRA_ACCOUNT_KEY, accountKey.value)
            putExtra(EXTRA_GENERATION, generation)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_SCHEDULE_TOKEN, scheduleToken)
            putExtra(EXTRA_OCCURRENCE_INDEX, occurrenceIndex.toLong())
        }
    }

    fun getAlarmRequestCode(
        accountKey: AccountKey,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): Int {
        return (accountKey.value + taskId + scheduleToken + occurrenceIndex).hashCode() and 0x7FFFFFFF
    }

    fun createAlarmPendingIntent(
        context: Context,
        accountKey: AccountKey,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): PendingIntent {
        val intent = createAlarmIntent(context, accountKey, generation, taskId, scheduleToken, occurrenceIndex)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = getAlarmRequestCode(accountKey, taskId, scheduleToken, occurrenceIndex)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    fun createViewTaskPendingIntent(
        context: Context,
        accountKey: AccountKey,
        taskId: String
    ): PendingIntent {
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_TASK
            data = Uri.parse("doggo://task/${accountKey.value}/$taskId")
            putExtra(EXTRA_ACCOUNT_KEY, accountKey.value)
            putExtra(EXTRA_TASK_ID, taskId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = (accountKey.value + taskId).hashCode() and 0x7FFFFFFF
        return PendingIntent.getActivity(context, requestCode, clickIntent, flags)
    }
}
