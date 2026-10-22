package dev.tabbyrush.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.TaskRef
/**
 * Factory utilities for Android notification channels, explicit alarm intents,
 * and immutable PendingIntents with identity-only data URIs.
 */
object AndroidNotificationIntentFactory {

    const val CHANNEL_ID = "tabbyrush_reminders"
    const val CHANNEL_NAME = "TabbyRush Task Reminders"
    const val CHANNEL_DESCRIPTION = "Offline task deadline and reminder notifications"
    const val GROUP_KEY = "dev.tabbyrush.android.REMINDERS"

    const val ACTION_DELIVER_REMINDER = "dev.tabbyrush.android.ACTION_DELIVER_REMINDER"
    const val ACTION_VIEW_TASK = "dev.tabbyrush.android.ACTION_VIEW_TASK"

    const val EXTRA_ACCOUNT_KEY = "extra_account_key"
    const val EXTRA_GENERATION = "extra_generation"
    const val EXTRA_OWNER_USER_ID = "extra_owner_user_id"
    const val EXTRA_ROW_ID = "extra_row_id"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_SCHEDULE_TOKEN = "extra_schedule_token"
    const val EXTRA_OCCURRENCE_INDEX = "extra_occurrence_index"
    fun getAccountNotificationTag(accountKey: AccountKey): String = "tabbyrush_${accountKey.value}"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing != null && (existing.importance < NotificationManager.IMPORTANCE_HIGH || existing.sound == null)) {
                nm.deleteNotificationChannel(CHANNEL_ID)
            }
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: Settings.System.DEFAULT_NOTIFICATION_URI

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableLights(true)
                    enableVibration(true)
                    setSound(soundUri, audioAttributes)
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
        taskRef: TaskRef,
        scheduleToken: String,
        occurrenceIndex: Int
    ): Intent {
        return Intent(context, TabbyRushAlarmReceiver::class.java).apply {
            action = ACTION_DELIVER_REMINDER
            data = Uri.Builder()
                .scheme("tabbyrush")
                .authority("alarm")
                .appendPath(accountKey.value)
                .appendPath(generation.toString())
                .appendPath(taskRef.ownerUserId)
                .appendPath(taskRef.rowId)
                .appendPath(scheduleToken)
                .appendPath(occurrenceIndex.toString())
                .build()
            putExtra(EXTRA_ACCOUNT_KEY, accountKey.value)
            putExtra(EXTRA_GENERATION, generation)
            putExtra(EXTRA_OWNER_USER_ID, taskRef.ownerUserId)
            putExtra(EXTRA_ROW_ID, taskRef.rowId)
            putExtra(EXTRA_TASK_ID, taskRef.rowId)
            putExtra(EXTRA_SCHEDULE_TOKEN, scheduleToken)
            putExtra(EXTRA_OCCURRENCE_INDEX, occurrenceIndex.toLong())
        }
    }

    fun getAlarmRequestCode(
        accountKey: AccountKey,
        taskRef: TaskRef,
        scheduleToken: String,
        occurrenceIndex: Int
    ): Int {
        var h = accountKey.value.hashCode()
        h = 31 * h + taskRef.ownerUserId.hashCode()
        h = 31 * h + taskRef.rowId.hashCode()
        h = 31 * h + scheduleToken.hashCode()
        h = 31 * h + occurrenceIndex.hashCode()
        return h and 0x7FFFFFFF
    }

    fun createAlarmPendingIntent(
        context: Context,
        accountKey: AccountKey,
        generation: Long,
        taskRef: TaskRef,
        scheduleToken: String,
        occurrenceIndex: Int
    ): PendingIntent {
        val intent = createAlarmIntent(context, accountKey, generation, taskRef, scheduleToken, occurrenceIndex)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = getAlarmRequestCode(accountKey, taskRef, scheduleToken, occurrenceIndex)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    fun createViewTaskPendingIntent(
        context: Context,
        accountKey: AccountKey,
        taskRef: TaskRef
    ): PendingIntent {
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_TASK
            data = Uri.Builder()
                .scheme("tabbyrush")
                .authority("task")
                .appendPath(accountKey.value)
                .appendPath(taskRef.ownerUserId)
                .appendPath(taskRef.rowId)
                .build()
            putExtra(EXTRA_ACCOUNT_KEY, accountKey.value)
            putExtra(EXTRA_OWNER_USER_ID, taskRef.ownerUserId)
            putExtra(EXTRA_ROW_ID, taskRef.rowId)
            putExtra(EXTRA_TASK_ID, taskRef.rowId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = (accountKey.value + ":" + taskRef.ownerUserId + ":" + taskRef.rowId).hashCode() and 0x7FFFFFFF
        return PendingIntent.getActivity(context, requestCode, clickIntent, flags)
    }

    /**
     * Legacy intent builder to allow cancellation of pre-upgrade alarms without mapping them to current user.
     */
    fun createLegacyAlarmPendingIntent(
        context: Context,
        accountKey: AccountKey,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): PendingIntent {
        val intent = Intent(context, TabbyRushAlarmReceiver::class.java).apply {
            action = ACTION_DELIVER_REMINDER
            data = Uri.parse("tabbyrush://alarm/${accountKey.value}/$generation/$taskId/$scheduleToken/$occurrenceIndex")
            putExtra(EXTRA_ACCOUNT_KEY, accountKey.value)
            putExtra(EXTRA_GENERATION, generation)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_SCHEDULE_TOKEN, scheduleToken)
            putExtra(EXTRA_OCCURRENCE_INDEX, occurrenceIndex.toLong())
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = (accountKey.value + taskId + scheduleToken + occurrenceIndex).hashCode() and 0x7FFFFFFF
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
