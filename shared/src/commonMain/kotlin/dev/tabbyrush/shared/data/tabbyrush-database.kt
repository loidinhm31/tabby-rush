package dev.tabbyrush.shared.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * TabbyRushDatabase is the Room database for local state persistence.
 * Houses server_base, outbox, editor_drafts, scan_staging, and account_sync_meta.
 * Exports Room4; explicit migration preserves historical strict evidence. Destructive fallback is forbidden.
 */
@Database(
    entities = [
        ServerBaseEntity::class,
        OutboxEntity::class,
        EditorDraftEntity::class,
        ScanStagingEntity::class,
        AccountSyncMetaEntity::class,
        AccountLocalSettingsEntity::class,
        ReminderDeliveryLedgerEntity::class,
        LegacySyncEvidenceEntity::class
    ],
    version = 4,
    exportSchema = true
)
@ConstructedBy(TabbyRushDatabaseConstructor::class)
abstract class TabbyRushDatabase : RoomDatabase() {
    abstract fun tabbyRushDao(): TabbyRushDao
}


@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object TabbyRushDatabaseConstructor : RoomDatabaseConstructor<TabbyRushDatabase>
