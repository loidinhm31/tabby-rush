package dev.tabbyrush.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import android.util.Log
import dev.tabbyrush.android.AndroidNotificationIntentFactory.CHANNEL_ID
import dev.tabbyrush.android.AndroidNotificationIntentFactory.GROUP_KEY
import dev.tabbyrush.android.AndroidNotificationIntentFactory.createAlarmIntent
import dev.tabbyrush.android.AndroidNotificationIntentFactory.createAlarmPendingIntent
import dev.tabbyrush.android.AndroidNotificationIntentFactory.createViewTaskPendingIntent
import dev.tabbyrush.android.AndroidNotificationIntentFactory.ensureNotificationChannel
import dev.tabbyrush.android.AndroidNotificationIntentFactory.getAccountNotificationTag
import dev.tabbyrush.android.AndroidNotificationIntentFactory.getAlarmRequestCode
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ReminderCapabilities
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.contract.TaskRef
/**
 * Android native reminder adapter managing AlarmManager scheduling and NotificationManager alerts.
 * Implements NativeReminderAdapter with fail-closed permission snapshots and private visibility.
 */
class AndroidNotificationAdapter(
    private val context: Context
) : NativeReminderAdapter {

    private val alarmRegistry = AndroidAlarmRegistry(context)

    override suspend fun queryCapabilities(): ReminderCapabilities {
        ensureNotificationChannel(context)
        val checker = AndroidReminderCapabilitiesChecker.checkCapabilities(context, CHANNEL_ID)
        return ReminderCapabilities(
            canScheduleExactAlarms = checker.canScheduleExactAlarms,
            canPostNotifications = checker.canPostNotifications,
            areNotificationsEnabled = checker.isChannelEnabled
        )
    }

    override suspend fun reconcileSchedule(
        accountKey: AccountKey,
        generation: Long,
        desiredAlarms: List<ScheduledAlarmHandle>
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val currentKeys = alarmRegistry.getScheduledAlarmKeys(accountKey)
        val desiredMap = desiredAlarms.associateBy {
            AndroidAlarmRegistry.formatAlarmKey(it.generation, it.taskRef, it.scheduleToken, it.occurrenceIndex)
        }

        // Cancel obsolete alarms
        val obsoleteKeys = currentKeys - desiredMap.keys
        for (obsoleteKey in obsoleteKeys) {
            val parsed = AndroidAlarmRegistry.parseAlarmKey(obsoleteKey) ?: continue
            if (parsed.isLegacy) {
                cancelLegacyAlarmHandle(am, accountKey, parsed.generation, parsed.legacyTaskId!!, parsed.scheduleToken, parsed.occurrenceIndex)
            } else {
                cancelAlarmHandle(am, accountKey, parsed.generation, parsed.taskRef!!, parsed.scheduleToken, parsed.occurrenceIndex)
            }
        }

        // Schedule desired alarms
        for ((_, alarm) in desiredMap) {
            scheduleAlarmHandle(am, accountKey, alarm)
        }

        alarmRegistry.saveScheduledAlarmKeys(accountKey, desiredMap.keys)
    }

    override suspend fun postOwnedNotification(
        accountKey: AccountKey,
        generation: Long,
        taskRef: TaskRef,
        scheduleToken: String,
        occurrenceIndex: Int,
        title: String,
        dueEpochMs: Long
    ): String? {
        if (!AndroidReminderCapabilitiesChecker.canPostNotifications(context, CHANNEL_ID)) return null
        ensureNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null

        val publicVersion = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("TabbyRush")
            .setContentText("Task reminder")
            .setGroup(GROUP_KEY)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        val clickPendingIntent = createViewTaskPendingIntent(context, accountKey, taskRef)
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Task reminder: $title")
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(clickPendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setSound(soundUri, audioAttributes)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val notifId = (accountKey.value + ":" + taskRef.ownerUserId + ":" + taskRef.rowId).hashCode() and 0x7FFFFFFF
        val tag = getAccountNotificationTag(accountKey)
        return try {
            nm.notify(tag, notifId, notification)
            alarmRegistry.recordActiveNotificationId(accountKey, notifId.toString())
            notifId.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Notification post failed: ${e.message}")
            null
        }
    }

    override suspend fun removeOwnedNotification(
        accountKey: AccountKey,
        platformNotificationId: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = platformNotificationId.toIntOrNull() ?: return
        val tag = getAccountNotificationTag(accountKey)
        nm.cancel(tag, notifId)
        alarmRegistry.removeActiveNotificationId(accountKey, platformNotificationId)
    }

    override suspend fun cancelAllForAccount(accountKey: AccountKey) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (am != null) {
            val scheduledKeys = alarmRegistry.getScheduledAlarmKeys(accountKey)
            for (key in scheduledKeys) {
                val parsed = AndroidAlarmRegistry.parseAlarmKey(key) ?: continue
                if (parsed.isLegacy) {
                    cancelLegacyAlarmHandle(am, accountKey, parsed.generation, parsed.legacyTaskId!!, parsed.scheduleToken, parsed.occurrenceIndex)
                } else {
                    cancelAlarmHandle(am, accountKey, parsed.generation, parsed.taskRef!!, parsed.scheduleToken, parsed.occurrenceIndex)
                }
            }
        }

        if (nm != null) {
            val tag = getAccountNotificationTag(accountKey)
            for (idStr in alarmRegistry.getActiveNotificationIds(accountKey)) {
                val id = idStr.toIntOrNull() ?: continue
                nm.cancel(tag, id)
            }
        }

        alarmRegistry.clearAll(accountKey)
    }

    private fun scheduleAlarmHandle(am: AlarmManager, accountKey: AccountKey, alarm: ScheduledAlarmHandle) {
        val pendingIntent = createAlarmPendingIntent(
            context, accountKey, alarm.generation, alarm.taskRef, alarm.scheduleToken, alarm.occurrenceIndex
        )
        val canExact = AndroidReminderCapabilitiesChecker.canScheduleExactAlarms(context)
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtEpochMs, pendingIntent)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtEpochMs, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtEpochMs, pendingIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed inexact fallback alarm: ${e.message}")
            }
        }
    }

    private fun cancelAlarmHandle(
        am: AlarmManager,
        accountKey: AccountKey,
        generation: Long,
        taskRef: TaskRef,
        scheduleToken: String,
        occurrenceIndex: Int
    ) {
        val dummyIntent = createAlarmIntent(context, accountKey, generation, taskRef, scheduleToken, occurrenceIndex)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getAlarmRequestCode(accountKey, taskRef, scheduleToken, occurrenceIndex),
            dummyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            am.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun cancelLegacyAlarmHandle(
        am: AlarmManager,
        accountKey: AccountKey,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ) {
        val pendingIntent = AndroidNotificationIntentFactory.createLegacyAlarmPendingIntent(
            context, accountKey, generation, taskId, scheduleToken, occurrenceIndex
        )
        am.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    companion object {
        private const val TAG = "AndroidNotificationAdapter"
    }
}
