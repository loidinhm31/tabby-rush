package dev.tabbyrush.shared.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * TabbyRushDatabase is the Room database for local state persistence.
 * Houses server_base, outbox, editor_drafts, scan_staging, and account_sync_meta.
 * Exports version 1 schema for validation. Destructive migration fallback is forbidden.
 */
@Database(
    entities = [
        ServerBaseEntity::class,
        OutboxEntity::class,
        EditorDraftEntity::class,
        ScanStagingEntity::class,
        AccountSyncMetaEntity::class,
        AccountLocalSettingsEntity::class,
        ReminderDeliveryLedgerEntity::class
    ],
    version = 3,
    exportSchema = true
)
@ConstructedBy(TabbyRushDatabaseConstructor::class)
abstract class TabbyRushDatabase : RoomDatabase() {
    abstract fun tabbyRushDao(): TabbyRushDao
}


@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object TabbyRushDatabaseConstructor : RoomDatabaseConstructor<TabbyRushDatabase>
