package dev.tabbyrush.shared.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.use
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_NULL
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Explicit migrations catalog for TabbyRushDatabase.
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
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `server_base` ADD COLUMN `parentTaskId` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `editor_drafts` ADD COLUMN `parentTaskId` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `scan_staging` ADD COLUMN `parentTaskId` TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE `outbox` ADD COLUMN `parentTaskId` TEXT DEFAULT NULL")
        }
    }

    /** Room wraps this migration in its upgrade transaction. No SQLite JSON extension required. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE TABLE `legacy_sync_evidence` (`sourceOpId` TEXT NOT NULL, `rowId` TEXT NOT NULL, `originalJson` TEXT NOT NULL, `outcome` TEXT NOT NULL, `translatedOpId` TEXT, PRIMARY KEY(`sourceOpId`))")
            val columns = columns(connection, "outbox")
            connection.prepare("SELECT * FROM `outbox`").use { rows ->
                connection.prepare("INSERT INTO `legacy_sync_evidence` VALUES (?, ?, ?, 'PENDING_REVIEW', NULL)").use { insert ->
                    while (rows.step()) {
                        val values = columns.mapIndexed { index, column ->
                            column.name to when (rows.getColumnType(index)) {
                                SQLITE_DATA_NULL -> JsonNull
                                SQLITE_DATA_INTEGER -> JsonPrimitive(rows.getLong(index))
                                SQLITE_DATA_TEXT -> JsonPrimitive(rows.getText(index))
                                SQLITE_DATA_FLOAT -> JsonObject(mapOf("sqliteFloatBits" to JsonPrimitive(rows.getDouble(index).toBits())))
                                SQLITE_DATA_BLOB -> JsonObject(mapOf("sqliteBlobHex" to JsonPrimitive(rows.getBlob(index).joinToString("") {
                                    (it.toInt() and 255).toString(16).padStart(2, '0')
                                })))
                                else -> error("Unsupported SQLite storage type; migration cancelled without discarding evidence")
                            }
                        }.toMap()
                        insert.bindText(1, rows.getText(columns.indexOfFirst { it.name == "opId" }))
                        insert.bindText(2, rows.getText(columns.indexOfFirst { it.name == "rowId" }))
                        insert.bindText(3, JsonObject(values).toString())
                        insert.step()
                        insert.reset()
                    }
                }
            }
            rebuild(connection, "server_base", setOf("epoch"), listOf(
                AddedColumn("rawJson", "TEXT", "NULL"), AddedColumn("syncedAt", "TEXT", "NULL"),
                AddedColumn("standardConfirmed", "INTEGER NOT NULL", "0"), AddedColumn("projectionError", "TEXT", "NULL")
            ))
            rebuild(connection, "scan_staging", setOf("epoch"), listOf(
                AddedColumn("rawJson", "TEXT", "NULL"), AddedColumn("syncedAt", "TEXT", "NULL"),
                AddedColumn("scanGeneration", "INTEGER NOT NULL", "0"), AddedColumn("projectionError", "TEXT", "NULL")
            ))
            rebuild(connection, "account_sync_meta", setOf("activeEpoch"), listOf(
                AddedColumn("hubSchemaVersion", "INTEGER", "NULL"),
                AddedColumn("checkpointUpdatedAt", "TEXT", "NULL"), AddedColumn("checkpointId", "TEXT", "NULL"),
                AddedColumn("scanGeneration", "INTEGER NOT NULL", "0"), AddedColumn("scanState", "TEXT NOT NULL", "'REQUIRED'"),
                AddedColumn("scanCheckpointUpdatedAt", "TEXT", "NULL"), AddedColumn("scanCheckpointId", "TEXT", "NULL"),
                AddedColumn("lastCompletedCatchUpEpochMs", "INTEGER", "NULL")
            ))
            rebuild(connection, "outbox", setOf("epoch", "digest", "expectedRevision", "contractVersion", "transitionCheckpoint", "dispatchHistory"), listOf(
                AddedColumn("dispatchHistory", "TEXT NOT NULL", "'LEGACY_EVIDENCE'"),
                AddedColumn("payloadJson", "TEXT NOT NULL", "'{}'"), AddedColumn("envelopeJson", "TEXT", "NULL"),
                AddedColumn("version", "INTEGER", "NULL"), AddedColumn("assumedServerVersion", "INTEGER", "NULL"),
                AddedColumn("legacyEvidenceId", "TEXT", "`opId`"), AddedColumn("resolution", "TEXT", "NULL")
            ), mapOf("deliveryState" to "'REVIEW'"))
            connection.execSQL("CREATE UNIQUE INDEX `index_outbox_legacyEvidenceId` ON `outbox` (`legacyEvidenceId`)")
        }
    }

    private data class Column(val name: String, val type: String, val notNull: Boolean, val primaryKey: Int)
    private data class AddedColumn(val name: String, val declaration: String, val initial: String)

    private fun columns(connection: SQLiteConnection, table: String): List<Column> {
        val result = mutableListOf<Column>()
        connection.prepare("PRAGMA table_info(`$table`)").use { statement ->
            while (statement.step()) result.add(Column(statement.getText(1), statement.getText(2), statement.getLong(3) != 0L, statement.getLong(5).toInt()))
        }
        return result
    }

    private fun rebuild(
        connection: SQLiteConnection,
        table: String,
        removed: Set<String>,
        added: List<AddedColumn>,
        overrides: Map<String, String> = emptyMap()
    ) {
        val retained = columns(connection, table).filter { it.name !in removed }
        val definitions = retained.map { "`${it.name}` ${it.type}${if (it.notNull) " NOT NULL" else ""}" } +
            added.map { "`${it.name}` ${it.declaration}" } +
            "PRIMARY KEY(${retained.filter { it.primaryKey > 0 }.sortedBy { it.primaryKey }.joinToString { "`${it.name}`" }})"
        connection.execSQL("CREATE TABLE `${table}_standard` (${definitions.joinToString()})")
        val names = retained.map { it.name } + added.map { it.name }
        val selected = retained.map { overrides[it.name] ?: "`${it.name}`" } + added.map { it.initial }
        connection.execSQL("INSERT INTO `${table}_standard` (${names.joinToString { "`$it`" }}) SELECT ${selected.joinToString()} FROM `$table`")
        connection.execSQL("DROP TABLE `$table`")
        connection.execSQL("ALTER TABLE `${table}_standard` RENAME TO `$table`")
    }

    /**
     * All registered migrations for TabbyRushDatabase.
     */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
