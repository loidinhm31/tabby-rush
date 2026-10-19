package dev.tabbyrush.shared.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.StrictOperationKind
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomMigrationV2ToV3Test {

    private lateinit var tempDir: File
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("tabbyrush-v3-migration-test").toFile()
        dbFile = File(tempDir, "tabbyrush-account.db")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createV2PopulatedDatabase(file: File) {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(file.absolutePath)
        try {
            // Create v2 tables according to 2.json
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `server_base` (
                    `rowId` TEXT NOT NULL,
                    `serverRevision` INTEGER NOT NULL,
                    `deleted` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `epoch` TEXT NOT NULL,
                    `deadlineDate` TEXT,
                    `deadlineTime` TEXT,
                    `deadlineZone` TEXT,
                    `deadlineOffsetSeconds` INTEGER,
                    `reminderAnchorTime` TEXT,
                    `deadlineAnchorEpochMs` INTEGER,
                    `deadlineOverdueEpochMs` INTEGER,
                    `reminderEnabled` INTEGER NOT NULL,
                    `reminderLeadMinutes` INTEGER NOT NULL,
                    `reminderIntervalMinutes` INTEGER,
                    `reminderCount` INTEGER NOT NULL,
                    `scheduleToken` TEXT,
                    PRIMARY KEY(`rowId`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `outbox` (
                    `opId` TEXT NOT NULL,
                    `epoch` TEXT NOT NULL,
                    `tableName` TEXT NOT NULL,
                    `rowId` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `expectedRevision` INTEGER NOT NULL,
                    `title` TEXT,
                    `completed` INTEGER,
                    `createdAt` INTEGER,
                    `digest` TEXT NOT NULL,
                    `localRevision` INTEGER NOT NULL,
                    `deliveryState` TEXT NOT NULL,
                    `createdAtEpochMs` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    `consecutiveFailures` INTEGER NOT NULL,
                    `deadlineDate` TEXT,
                    `deadlineTime` TEXT,
                    `deadlineZone` TEXT,
                    `deadlineOffsetSeconds` INTEGER,
                    `reminderAnchorTime` TEXT,
                    `deadlineAnchorEpochMs` INTEGER,
                    `deadlineOverdueEpochMs` INTEGER,
                    `reminderEnabled` INTEGER NOT NULL,
                    `reminderLeadMinutes` INTEGER NOT NULL,
                    `reminderIntervalMinutes` INTEGER,
                    `reminderCount` INTEGER NOT NULL,
                    `scheduleToken` TEXT,
                    `contractVersion` TEXT NOT NULL,
                    `dispatchHistory` TEXT,
                    `intentMask` TEXT,
                    `transitionCheckpoint` TEXT,
                    PRIMARY KEY(`opId`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `editor_drafts` (
                    `rowId` TEXT NOT NULL,
                    `draftTitle` TEXT NOT NULL,
                    `draftCompleted` INTEGER NOT NULL,
                    `localRevision` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    `deadlineDate` TEXT,
                    `deadlineTime` TEXT,
                    `deadlineZone` TEXT,
                    `deadlineOffsetSeconds` INTEGER,
                    `reminderAnchorTime` TEXT,
                    `deadlineAnchorEpochMs` INTEGER,
                    `deadlineOverdueEpochMs` INTEGER,
                    `reminderEnabled` INTEGER NOT NULL,
                    `reminderLeadMinutes` INTEGER NOT NULL,
                    `reminderIntervalMinutes` INTEGER,
                    `reminderCount` INTEGER NOT NULL,
                    `scheduleToken` TEXT,
                    PRIMARY KEY(`rowId`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scan_staging` (
                    `rowId` TEXT NOT NULL,
                    `tableName` TEXT NOT NULL,
                    `serverRevision` INTEGER NOT NULL,
                    `deleted` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `epoch` TEXT NOT NULL,
                    `deadlineDate` TEXT,
                    `deadlineTime` TEXT,
                    `deadlineZone` TEXT,
                    `deadlineOffsetSeconds` INTEGER,
                    `reminderAnchorTime` TEXT,
                    `deadlineAnchorEpochMs` INTEGER,
                    `deadlineOverdueEpochMs` INTEGER,
                    `reminderEnabled` INTEGER NOT NULL,
                    `reminderLeadMinutes` INTEGER NOT NULL,
                    `reminderIntervalMinutes` INTEGER,
                    `reminderCount` INTEGER NOT NULL,
                    `scheduleToken` TEXT,
                    PRIMARY KEY(`rowId`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `account_sync_meta` (
                    `id` TEXT NOT NULL,
                    `activeEpoch` TEXT,
                    `lastSyncTime` INTEGER,
                    `localRevisionCounter` INTEGER NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    `safeErrorCode` TEXT,
                    `safeErrorMessage` TEXT,
                    `consecutiveFailedRounds` INTEGER NOT NULL,
                    `credentialPersistencePending` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
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
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '06a93ffbac4a0570d56446f47e0e3e4c')"
            )
            connection.execSQL("PRAGMA user_version = 2")

            // Populate v2 data
            connection.execSQL(
                """
                INSERT INTO `server_base` VALUES (
                    'row-v2-live-1', 5, 0, 'Live v2 task', 0, 1000, 2000, 'epoch-1',
                    '2026-10-25', '14:30', 'Asia/Saigon', 25200, NULL, 1792913400000, 1792913400000,
                    1, 15, NULL, 1, 'token-v2-1'
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO `server_base` VALUES (
                    'row-v2-tomb-1', 8, 1, '', 0, 500, 3000, 'epoch-1',
                    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                    0, 0, NULL, 1, NULL
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO `editor_drafts` VALUES (
                    'row-v2-draft-1', 'V2 draft title', 1, 10, 4000, 0,
                    '2026-10-26', NULL, 'Asia/Saigon', 25200, '09:00', 1792980000000, 1792980000000,
                    0, 0, NULL, 1, 'token-draft-1'
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO `scan_staging` VALUES (
                    'row-v2-stage-1', 'todos', 12, 0, 'Staged v2 title', 0, 1200, 'epoch-1',
                    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                    0, 0, NULL, 1, NULL
                )
                """.trimIndent()
            )
            connection.execSQL(
                "INSERT INTO `account_sync_meta` VALUES ('singleton', 'epoch-1', 5000, 15, 'ready-v2', NULL, NULL, 0, 0)"
            )
            connection.execSQL(
                "INSERT INTO `account_local_settings` VALUES ('singleton', 1, 1, 15, NULL, 1, '09:00')"
            )
            connection.execSQL(
                """
                INSERT INTO `reminder_delivery_ledger` VALUES (
                    'acc-1', 1, 'row-v2-live-1', 'token-v2-1', 0, 1792912500000,
                    'PENDING', NULL, NULL, 'ACTIVE', 5000
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO `outbox` VALUES (
                    'op-v2-pending-1', 'epoch-1', 'todos', 'row-new-1', 'create', 0,
                    'New v2 task', 0, 6000, 'digest-v2-p1', 1, 'PENDING', 6000, NULL, 0,
                    '2026-10-28', NULL, 'Asia/Saigon', 25200, '09:00', 1793152800000, 1793152800000,
                    0, 0, NULL, 1, 'token-new-1', 'strict-v2', NULL, 'TITLE|COMPLETED|DEADLINE', NULL
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO `outbox` VALUES (
                    'op-v2-unc-1', 'epoch-1', 'todos', 'row-v2-live-1', 'update', 5,
                    'Updated title', 0, 1000, 'digest-v2-u1', 2, 'UNCERTAIN', 6100, 'timeout', 1,
                    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                    0, 0, NULL, 1, NULL, 'strict-v2', '{"dispatched":true}', 'TITLE', 'QUIESCED'
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO `outbox` VALUES (
                    'op-v2-conf-1', 'epoch-1', 'todos', 'row-v2-live-1', 'update', 5,
                    'Conflicted title', 1, 1000, 'digest-v2-c1', 3, 'CONFLICT', 6200, 'conflict 409', 1,
                    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                    0, 0, NULL, 1, NULL, 'strict-v2', NULL, 'COMPLETED', NULL
                )
                """.trimIndent()
            )
        } finally {
            connection.close()
        }
    }

    private fun createV1PopulatedDatabase(file: File) {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(file.absolutePath)
        try {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `server_base` (`rowId` TEXT NOT NULL, `serverRevision` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `title` TEXT NOT NULL, `completed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `epoch` TEXT NOT NULL, PRIMARY KEY(`rowId`))"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `outbox` (`opId` TEXT NOT NULL, `epoch` TEXT NOT NULL, `tableName` TEXT NOT NULL, `rowId` TEXT NOT NULL, `kind` TEXT NOT NULL, `expectedRevision` INTEGER NOT NULL, `title` TEXT, `completed` INTEGER, `createdAt` INTEGER, `digest` TEXT NOT NULL, `localRevision` INTEGER NOT NULL, `deliveryState` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `errorMessage` TEXT, `consecutiveFailures` INTEGER NOT NULL, PRIMARY KEY(`opId`))"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `editor_drafts` (`rowId` TEXT NOT NULL, `draftTitle` TEXT NOT NULL, `draftCompleted` INTEGER NOT NULL, `localRevision` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`rowId`))"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `scan_staging` (`rowId` TEXT NOT NULL, `tableName` TEXT NOT NULL, `serverRevision` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `title` TEXT NOT NULL, `completed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `epoch` TEXT NOT NULL, PRIMARY KEY(`rowId`))"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `account_sync_meta` (`id` TEXT NOT NULL, `activeEpoch` TEXT, `lastSyncTime` INTEGER, `localRevisionCounter` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `safeErrorCode` TEXT, `safeErrorMessage` TEXT, `consecutiveFailedRounds` INTEGER NOT NULL, `credentialPersistencePending` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6ebd06d2860e1c3a811a85f5ccdf6a4f')"
            )
            connection.execSQL("PRAGMA user_version = 1")

            connection.execSQL(
                "INSERT INTO `server_base` VALUES ('row-v1-1', 3, 0, 'V1 task', 0, 1000, 2000, 'epoch-1')"
            )
            connection.execSQL(
                "INSERT INTO `outbox` VALUES ('op-v1-1', 'epoch-1', 'todos', 'row-v1-1', 'update', 3, 'V1 update', 1, 1000, 'digest-v1-1', 1, 'PENDING', 3000, NULL, 0)"
            )
            connection.execSQL(
                "INSERT INTO `editor_drafts` VALUES ('row-v1-1', 'V1 draft', 1, 2, 4000, 0)"
            )
            connection.execSQL(
                "INSERT INTO `account_sync_meta` VALUES ('singleton', 'epoch-1', 5000, 5, 'ready-v1', NULL, NULL, 0, 0)"
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun testExplicitMigrationFromV2PreservesAllDataAndNullParent() = runTest {
        createV2PopulatedDatabase(dbFile)

        val db = createJvmDatabaseBuilder(dbFile).build()
        val dao = db.tabbyRushDao()

        try {
            // Verify server_base migrated with parentTaskId = null
            assertEquals(2, dao.countServerBase())
            val liveBase = dao.getServerBase("row-v2-live-1")
            assertNotNull(liveBase)
            assertEquals("Live v2 task", liveBase.title)
            assertFalse(liveBase.completed)
            assertFalse(liveBase.deleted)
            assertEquals("2026-10-25", liveBase.deadlineDate)
            assertEquals("token-v2-1", liveBase.scheduleToken)
            assertNull(liveBase.parentTaskId) // Null parent: standalone / root-capable

            val tombBase = dao.getServerBase("row-v2-tomb-1")
            assertNotNull(tombBase)
            assertTrue(tombBase.deleted)
            assertNull(tombBase.parentTaskId)

            // Verify drafts migrated with parentTaskId = null
            assertEquals(1, dao.countDrafts())
            val draft = dao.getDraft("row-v2-draft-1")
            assertNotNull(draft)
            assertEquals("V2 draft title", draft.draftTitle)
            assertTrue(draft.draftCompleted)
            assertNull(draft.parentTaskId)

            // Verify scan_staging migrated with parentTaskId = null
            assertEquals(1, dao.countStagedRecords())
            val staged = dao.getStagedRecord("row-v2-stage-1")
            assertNotNull(staged)
            assertEquals("Staged v2 title", staged.title)
            assertNull(staged.parentTaskId)

            // Verify outbox migrated with parentTaskId = null, preserved contractVersion and intents
            assertEquals(3, dao.countOutbox())
            val opPending = dao.getOutboxEntry("op-v2-pending-1")
            assertNotNull(opPending)
            assertEquals("strict-v2", opPending.contractVersion)
            assertEquals("digest-v2-p1", opPending.digest)
            assertEquals("TITLE|COMPLETED|DEADLINE", opPending.intentMask)
            assertNull(opPending.parentTaskId)

            val opUnc = dao.getOutboxEntry("op-v2-unc-1")
            assertNotNull(opUnc)
            assertEquals(OutboxEntity.STATE_UNCERTAIN, opUnc.deliveryState)
            assertEquals("QUIESCED", opUnc.transitionCheckpoint)
            assertNull(opUnc.parentTaskId)

            val opConf = dao.getOutboxEntry("op-v2-conf-1")
            assertNotNull(opConf)
            assertEquals(OutboxEntity.STATE_CONFLICT, opConf.deliveryState)
            assertNull(opConf.parentTaskId)

            // Verify ledger & settings preserved
            assertEquals(1, dao.countLedgerEntries())
            val ledger = dao.getLedgerEntry("acc-1", 1, "row-v2-live-1", "token-v2-1", 0)
            assertNotNull(ledger)
            assertEquals(1792912500000, ledger.dueEpochMs)

            val settings = dao.getLocalSettings()
            assertNotNull(settings)
            assertTrue(settings.notificationsEnabled)
        } finally {
            db.close()
        }

        // Reopen to verify restart stability
        val reopenedDb = createJvmDatabaseBuilder(dbFile).build()
        try {
            val reopenedDao = reopenedDb.tabbyRushDao()
            assertEquals(2, reopenedDao.countServerBase())
            assertEquals(3, reopenedDao.countOutbox())
            assertEquals(1, reopenedDao.countDrafts())
            assertEquals(1, reopenedDao.countLedgerEntries())
            assertNull(reopenedDao.getServerBase("row-v2-live-1")?.parentTaskId)
        } finally {
            reopenedDb.close()
        }
    }

    @Test
    fun testPopulatedMigrationFromV1ThroughV2ToV3Chain() = runTest {
        createV1PopulatedDatabase(dbFile)

        val db = createJvmDatabaseBuilder(dbFile).build()
        val dao = db.tabbyRushDao()

        try {
            // Verify full chain 1 -> 2 -> 3 migrated properly
            assertEquals(1, dao.countServerBase())
            val base = dao.getServerBase("row-v1-1")
            assertNotNull(base)
            assertEquals("V1 task", base.title)
            assertNull(base.deadlineDate)
            assertNull(base.parentTaskId) // Both v2 and v3 columns added as null

            assertEquals(1, dao.countDrafts())
            val draft = dao.getDraft("row-v1-1")
            assertNotNull(draft)
            assertEquals("V1 draft", draft.draftTitle)
            assertNull(draft.parentTaskId)

            assertEquals(1, dao.countOutbox())
            val op = dao.getOutboxEntry("op-v1-1")
            assertNotNull(op)
            assertEquals("digest-v1-1", op.digest)
            assertNull(op.parentTaskId)
        } finally {
            db.close()
        }

        // Reopen to verify restart stability
        val reopenedDb = createJvmDatabaseBuilder(dbFile).build()
        try {
            val reopenedDao = reopenedDb.tabbyRushDao()
            assertEquals(1, reopenedDao.countServerBase())
            assertNull(reopenedDao.getServerBase("row-v1-1")?.parentTaskId)
        } finally {
            reopenedDb.close()
        }
    }
}
