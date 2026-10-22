package dev.tabbyrush.shared.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * TabbyRushDatabase is the Room database for local state persistence.
 * Houses server_base, outbox, editor_drafts, scan_staging, account_sync_meta,
 * account_local_settings, reminder_delivery_ledger, legacy_sync_evidence,
 * task_access, access_check_staging, and access_loss_evidence.
 * Exports Room 5; explicit migration preserves historical strict evidence. Destructive fallback is forbidden.
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
        LegacySyncEvidenceEntity::class,
        TaskAccessEntity::class,
        AccessCheckStagingEntity::class,
        AccessLossEvidenceEntity::class
    ],
    version = 5,
    exportSchema = true
)
@ConstructedBy(TabbyRushDatabaseConstructor::class)
abstract class TabbyRushDatabase : RoomDatabase() {
    abstract fun tabbyRushDao(): TabbyRushDao
}


@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object TabbyRushDatabaseConstructor : RoomDatabaseConstructor<TabbyRushDatabase>
