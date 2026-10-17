package dev.tabbyrush.android

import android.content.Context
import android.os.Build
import android.util.Log
import dev.tabbyrush.shared.contract.AccountKey

/**
 * Persistently tracks scheduled alarm keys and active notification IDs per account.
 * Uses companion-level mutual exclusion to prevent cross-instance race conditions
 * and enforces synchronous commit() writes.
 */
class AndroidAlarmRegistry(private val context: Context) {

    private fun getPrefs(accountKey: AccountKey) =
        context.getSharedPreferences(getPrefName(accountKey), Context.MODE_PRIVATE)

    fun getScheduledAlarmKeys(accountKey: AccountKey): Set<String> = synchronized(registryLock) {
        getPrefs(accountKey).getStringSet(KEY_SCHEDULED_ALARMS, emptySet())?.toSet() ?: emptySet()
    }

    fun saveScheduledAlarmKeys(accountKey: AccountKey, keys: Set<String>): Boolean = synchronized(registryLock) {
        val success = getPrefs(accountKey).edit().putStringSet(KEY_SCHEDULED_ALARMS, keys.toSet()).commit()
        if (!success) Log.w(TAG, "Failed committing scheduled alarms for $accountKey")
        success
    }

    fun getActiveNotificationIds(accountKey: AccountKey): Set<String> = synchronized(registryLock) {
        getPrefs(accountKey).getStringSet(KEY_ACTIVE_NOTIFICATIONS, emptySet())?.toSet() ?: emptySet()
    }

    fun recordActiveNotificationId(accountKey: AccountKey, notificationId: String): Boolean = synchronized(registryLock) {
        val current = getActiveNotificationIds(accountKey).toMutableSet()
        current.add(notificationId)
        val success = getPrefs(accountKey).edit().putStringSet(KEY_ACTIVE_NOTIFICATIONS, current).commit()
        if (!success) Log.w(TAG, "Failed committing notification ID $notificationId for $accountKey")
        success
    }

    fun removeActiveNotificationId(accountKey: AccountKey, notificationId: String): Boolean = synchronized(registryLock) {
        val current = getActiveNotificationIds(accountKey).toMutableSet()
        if (current.remove(notificationId)) {
            val success = getPrefs(accountKey).edit().putStringSet(KEY_ACTIVE_NOTIFICATIONS, current).commit()
            if (!success) Log.w(TAG, "Failed committing notification removal $notificationId for $accountKey")
            success
        } else {
            true
        }
    }

    fun clearAll(accountKey: AccountKey): Boolean = synchronized(registryLock) {
        val prefName = getPrefName(accountKey)
        val success = getPrefs(accountKey).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                context.deleteSharedPreferences(prefName)
            } catch (_: Exception) {}
        }
        success
    }

    companion object {
        private const val TAG = "AndroidAlarmRegistry"
        private val registryLock = Any()

        private const val KEY_SCHEDULED_ALARMS = "scheduled_alarms"
        private const val KEY_ACTIVE_NOTIFICATIONS = "active_notifications"

        fun getPrefName(accountKey: AccountKey): String = "tabbyrush_alarms_${accountKey.value}"

        fun formatAlarmKey(generation: Long, taskId: String, scheduleToken: String, occurrenceIndex: Int): String {
            return "$generation:$taskId:$scheduleToken:$occurrenceIndex"
        }

        fun parseAlarmKey(key: String): ParsedAlarmKey? {
            val parts = key.split(':')
            if (parts.size != 4) return null
            val gen = parts[0].toLongOrNull() ?: return null
            val taskId = parts[1]
            val token = parts[2]
            val index = parts[3].toIntOrNull() ?: return null
            return ParsedAlarmKey(gen, taskId, token, index)
        }
    }
}

data class ParsedAlarmKey(
    val generation: Long,
    val taskId: String,
    val scheduleToken: String,
    val occurrenceIndex: Int
)
