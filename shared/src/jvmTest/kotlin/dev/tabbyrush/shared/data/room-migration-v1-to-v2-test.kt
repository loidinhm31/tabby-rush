package dev.tabbyrush.shared.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.TaskOperationKind
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

class RoomMigrationV1ToV2Test {

    private lateinit var tempDir: File
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("tabbyrush-migration-test").toFile()
        dbFile = File(tempDir, "tabbyrush-account.db")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createV1PopulatedDatabase(file: File) {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(file.absolutePath)
        try {
            // Create v1 tables according to 1.json
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

            // Populate v1 data
            connection.execSQL(
                "INSERT INTO `server_base` VALUES ('row-live-1', 5, 0, 'Live task 1', 0, 1000, 2000, 'epoch-1')"
            )
            connection.execSQL(
                "INSERT INTO `server_base` VALUES ('row-tomb-1', 8, 1, '', 0, 500, 3000, 'epoch-1')"
            )
            connection.execSQL(
                "INSERT INTO `editor_drafts` VALUES ('row-draft-1', 'Draft title', 1, 10, 4000, 0)"
            )
            connection.execSQL(
                "INSERT INTO `scan_staging` VALUES ('row-stage-1', 'todos', 12, 0, 'Staged title', 0, 1200, 'epoch-1')"
            )
            connection.execSQL(
                "INSERT INTO `account_sync_meta` VALUES ('singleton', 'epoch-1', 5000, 15, 'IDLE', NULL, NULL, 0, 0)"
            )

            // Populate outbox covering all delivery states
            connection.execSQL(
                "INSERT INTO `outbox` VALUES ('op-pending-1', 'epoch-1', 'todos', 'row-new-1', 'create', 0, 'New task', 0, 6000, 'digest-p1', 1, 'PENDING', 6000, NULL, 0)"
            )
            connection.execSQL(
                "INSERT INTO `outbox` VALUES ('op-disp-1', 'epoch-1', 'todos', 'row-live-1', 'update', 5, 'Updated title', 1, 1000, 'digest-d1', 2, 'DISPATCHING', 6100, NULL, 0)"
            )
            connection.execSQL(
                "INSERT INTO `outbox` VALUES ('op-unc-1', 'epoch-1', 'todos', 'row-live-1', 'update', 5, 'Uncertain title', 0, 1000, 'digest-u1', 3, 'UNCERTAIN', 6200, 'network timeout', 1)"
            )
            connection.execSQL(
                "INSERT INTO `outbox` VALUES ('op-conf-1', 'epoch-1', 'todos', 'row-live-1', 'update', 5, 'Conflicted title', 1, 1000, 'digest-c1', 4, 'CONFLICT', 6300, 'conflict 409', 1)"
            )
            connection.execSQL(
                "INSERT INTO `outbox` VALUES ('op-del-1', 'epoch-1', 'todos', 'row-tomb-1', 'delete', 8, NULL, NULL, NULL, 'digest-del1', 5, 'PENDING', 6400, NULL, 0)"
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun testExplicitMigrationFromV1PreservesAllDataAndIntents() = runTest {
        createV1PopulatedDatabase(dbFile)

        // Open through production JVM builder registering MIGRATION_1_2
        val db = createJvmDatabaseBuilder(dbFile).build()
        val dao = db.tabbyRushDao()

        try {
            // Verify server_base migrated with canonical nulls/defaults
            assertEquals(2, dao.countServerBase())
            val liveBase = dao.getServerBase("row-live-1")
            assertNotNull(liveBase)
            assertEquals("Live task 1", liveBase.title)
            assertFalse(liveBase.completed)
            assertFalse(liveBase.deleted)
            assertFalse(liveBase.standardConfirmed)
            assertNull(liveBase.deadlineDate)
            assertNull(liveBase.deadlineTime)
            assertNull(liveBase.deadlineZone)
            assertFalse(liveBase.reminderEnabled)
            assertEquals(0L, liveBase.reminderLeadMinutes)
            assertNull(liveBase.reminderIntervalMinutes)
            assertEquals(1L, liveBase.reminderCount)
            assertNull(liveBase.scheduleToken)

            val tombBase = dao.getServerBase("row-tomb-1")
            assertNotNull(tombBase)
            assertTrue(tombBase.deleted)
            assertEquals("", tombBase.title)
            assertNull(tombBase.deadlineDate)
            assertNull(tombBase.scheduleToken)

            // Verify drafts migrated
            assertEquals(1, dao.countDrafts())
            val draft = dao.getDraft("row-draft-1")
            assertNotNull(draft)
            assertEquals("Draft title", draft.draftTitle)
            assertTrue(draft.draftCompleted)
            assertNull(draft.deadlineDate)

            // Verify staging migrated
            assertEquals(1, dao.countStagedRecords())
            val staged = dao.getStagedRecord("row-stage-1")
            assertNotNull(staged)
            assertEquals("Staged title", staged.title)
            assertNull(staged.deadlineDate)

            // Active rows remain typed/reviewable; original envelope and provenance are archived losslessly.
            assertEquals(5, dao.countOutbox())
            val evidence = dao.getLegacyEvidence().associateBy { it.sourceOpId }
            assertEquals(5, evidence.size)
            val original = kotlinx.serialization.json.Json.parseToJsonElement(evidence.getValue("op-pending-1").originalJson).let { it as kotlinx.serialization.json.JsonObject }
            assertEquals(kotlinx.serialization.json.JsonPrimitive("digest-p1"), original["digest"])
            assertEquals(kotlinx.serialization.json.JsonPrimitive("PENDING"), original["deliveryState"])
            assertEquals(kotlinx.serialization.json.JsonPrimitive("MIGRATED_FROM_ROOM_V1"), original["transitionCheckpoint"])
            assertTrue(dao.getAllOutbox().all { it.deliveryState == OutboxEntity.STATE_REVIEW && it.legacyEvidenceId != null })
            assertEquals("New task", dao.getOutboxEntry("op-pending-1")?.title)
            assertFalse(dev.tabbyrush.shared.sync.LegacyStandardIntentMigration.provablyNeverDispatched(evidence.getValue("op-pending-1")))

            // Verify account_local_settings table created with defaults
            val settings = dao.getLocalSettings()
            assertNotNull(settings)
            assertFalse(settings.notificationsEnabled)
            assertFalse(settings.defaultReminderEnabled)
            assertEquals(0L, settings.defaultReminderLeadMinutes)
            assertNull(settings.defaultReminderIntervalMinutes)
            assertEquals(1L, settings.defaultReminderCount)
            assertEquals("09:00", settings.defaultDateOnlyAnchorTime)

            // Verify reminder_delivery_ledger table created empty
            assertEquals(0, dao.countLedgerEntries())

            // Test ledger insert and index lookup
            val ledgerEntry = ReminderDeliveryLedgerEntity(
                accountKey = "http://localhost:3000:user-1",
                generation = 1L,
                taskId = "row-live-1",
                scheduleToken = "sched-tok-1",
                occurrenceIndex = 0,
                dueEpochMs = 1792000000000L,
                claimOutcome = ReminderDeliveryLedgerEntity.OUTCOME_PENDING,
                cancellationState = ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            dao.upsertLedgerEntry(ledgerEntry)
            assertEquals(1, dao.countLedgerEntries())
            val retrieved = dao.getLedgerEntry(
                "http://localhost:3000:user-1",
                1L,
                "row-live-1",
                "sched-tok-1",
                0
            )
            assertNotNull(retrieved)
            assertEquals(1792000000000L, retrieved.dueEpochMs)
        } finally {
            db.close()
        }

        // Reopen database to verify restart stability
        val reopenedDb = createJvmDatabaseBuilder(dbFile).build()
        try {
            val reopenedDao = reopenedDb.tabbyRushDao()
            assertEquals(2, reopenedDao.countServerBase())
            assertEquals(5, reopenedDao.countOutbox())
            assertEquals(1, reopenedDao.countDrafts())
            assertEquals(1, reopenedDao.countLedgerEntries())
        } finally {
            reopenedDb.close()
        }
    }
}
