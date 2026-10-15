package dev.doggo.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AccountLocalSettingsEntity stores device- and account-local reminder and notification defaults.
 * These settings are strictly account-local, wiped with account logout, and never sent to the Hub.
 */
@Entity(tableName = "account_local_settings")
data class AccountLocalSettingsEntity(
    @PrimaryKey
    val id: String = SINGLETON_ID,
    val notificationsEnabled: Boolean = false,
    val defaultReminderEnabled: Boolean = false,
    val defaultReminderLeadMinutes: Long = 0L,
    val defaultReminderIntervalMinutes: Long? = null,
    val defaultReminderCount: Long = 1L,
    val defaultDateOnlyAnchorTime: String = DEFAULT_DATE_ONLY_ANCHOR_TIME
) {
    companion object {
        const val SINGLETON_ID = "singleton"
        const val DEFAULT_DATE_ONLY_ANCHOR_TIME = "09:00"
    }
}
