package dev.doggo.shared.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Explicit migrations catalog for DoggoDatabase.
 * Destructive fallback is strictly forbidden. Schema changes must be accompanied
 * by verified step-by-step migrations.
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            // 1. Alter server_base with 12 temporal fields
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `deadlineDate` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `deadlineTime` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `deadlineZone` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `deadlineOffsetSeconds` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `reminderAnchorTime` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `deadlineAnchorEpochMs` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `deadlineOverdueEpochMs` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `reminderEnabled` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `reminderLeadMinutes` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `reminderIntervalMinutes` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `reminderCount` INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `scheduleToken` TEXT DEFAULT NULL")

            // 2. Alter editor_drafts with 12 temporal fields
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `deadlineDate` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `deadlineTime` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `deadlineZone` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `deadlineOffsetSeconds` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `reminderAnchorTime` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `deadlineAnchorEpochMs` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `deadlineOverdueEpochMs` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `reminderEnabled` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `reminderLeadMinutes` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `reminderIntervalMinutes` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `reminderCount` INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `scheduleToken` TEXT DEFAULT NULL")

            // 3. Alter scan_staging with 12 temporal fields
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `deadlineDate` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `deadlineTime` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `deadlineZone` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `deadlineOffsetSeconds` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `reminderAnchorTime` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `deadlineAnchorEpochMs` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `deadlineOverdueEpochMs` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `reminderEnabled` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `reminderLeadMinutes` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `reminderIntervalMinutes` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `reminderCount` INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `scheduleToken` TEXT DEFAULT NULL")

            // 4. Alter outbox with 12 temporal fields + provenance/version
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `deadlineDate` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `deadlineTime` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `deadlineZone` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `deadlineOffsetSeconds` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `reminderAnchorTime` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `deadlineAnchorEpochMs` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `deadlineOverdueEpochMs` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `reminderEnabled` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `reminderLeadMinutes` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `reminderIntervalMinutes` INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `reminderCount` INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `scheduleToken` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `contractVersion` TEXT NOT NULL DEFAULT 'strict-v1'")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `dispatchHistory` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `intentMask` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `transitionCheckpoint` TEXT DEFAULT NULL")

            // Conservative provenance & intent derivation for legacy outbox rows
            connection.execSQL(
                """
                UPDATE `outbox` SET 
                    `transitionCheckpoint` = 'MIGRATED_FROM_ROOM_V1',
                    `intentMask` = CASE
                        WHEN `kind` = 'delete' THEN 'DELETE'
                        WHEN `kind` = 'create' THEN 'TITLE|COMPLETED'
                        WHEN `kind` = 'update' AND `title` IS NOT NULL AND `completed` IS NOT NULL THEN 'TITLE|COMPLETED'
                        ELSE 'REQUIRES_REVIEW'
                    END,
                    `dispatchHistory` = '{"event":"ROOM_V1_MIGRATION","priorState":"' || `deliveryState` || '"}'
                WHERE `transitionCheckpoint` IS NULL
                """.trimIndent()
            )

            // 5. Create account_local_settings table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `account_local_settings` (
                    `id` TEXT NOT NULL,
                    `notificationsEnabled` INTEGER NOT NULL,
                    `defaultReminderEnabled` INTEGER NOT NULL,
                    `defaultReminderLeadMinutes` INTEGER NOT NULL,
                    `defaultReminderIntervalMinutes` INTEGER,
                    `defaultReminderCount` INTEGER NOT NULL,
                    `defaultDateOnlyAnchorTime` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            connection.execSQL(
                """
                INSERT OR IGNORE INTO `account_local_settings` (
                    `id`, `notificationsEnabled`, `defaultReminderEnabled`,
                    `defaultReminderLeadMinutes`, `defaultReminderIntervalMinutes`,
                    `defaultReminderCount`, `defaultDateOnlyAnchorTime`
                ) VALUES ('singleton', 0, 0, 0, NULL, 1, '09:00')
                """.trimIndent()
            )

            // 6. Create reminder_delivery_ledger table and indices
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reminder_delivery_ledger` (
                    `accountKey` TEXT NOT NULL,
                    `generation` INTEGER NOT NULL,
                    `taskId` TEXT NOT NULL,
                    `scheduleToken` TEXT NOT NULL,
                    `occurrenceIndex` INTEGER NOT NULL,
                    `dueEpochMs` INTEGER NOT NULL,
                    `claimOutcome` TEXT NOT NULL,
                    `claimedAtEpochMs` INTEGER,
                    `postedPlatformId` TEXT,
                    `cancellationState` TEXT NOT NULL,
                    `updatedAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`accountKey`, `generation`, `taskId`, `scheduleToken`, `occurrenceIndex`)
                )
                """.trimIndent()
            )

            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminder_delivery_ledger_accountKey_generation_taskId` ON `reminder_delivery_ledger` (`accountKey`, `generation`, `taskId`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminder_delivery_ledger_dueEpochMs` ON `reminder_delivery_ledger` (`dueEpochMs`)"
            )
        }
    }

    /**
     * All registered migrations for DoggoDatabase.
     */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
