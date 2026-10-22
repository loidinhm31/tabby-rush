package dev.tabbyrush.shared.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.sqlite.use
import dev.tabbyrush.shared.contract.AccountKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RoomMigrationToV4Test {
    @Test fun evidencePreservesUnexpectedSqliteStorageClassesWithoutCoercion() = runTest {
        val directory = Files.createTempDirectory("room-storage-evidence").toFile()
        val file = File(directory, "account.db")
        try {
            seedExport(file, 3)
            val connection = BundledSQLiteDriver().open(file.absolutePath)
            try {
                connection.execSQL("UPDATE outbox SET expectedRevision = 1.25, digest = X'00FF' WHERE opId = 'operation-0'")
            } finally { connection.close() }
            val database = createJvmDatabaseBuilder(file).build()
            try {
                val evidence = database.tabbyRushDao().getLegacyEvidence().first { it.sourceOpId == "operation-0" }
                val raw = Json.parseToJsonElement(evidence.originalJson).jsonObject
                assertEquals(JsonPrimitive(1.25.toBits()), raw.getValue("expectedRevision").jsonObject["sqliteFloatBits"])
                assertEquals(JsonPrimitive("00ff"), raw.getValue("digest").jsonObject["sqliteBlobHex"])
            } finally { database.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun historicalExportsOneTwoThreeUpgradeLosslesslyAndRestart() = runTest {
        for (version in 1..3) {
            val directory = Files.createTempDirectory("room-$version-to-4").toFile()
            val file = File(directory, "account.db")
            try {
                val originals = seedExport(file, version)
                val database = createJvmDatabaseBuilder(file).build()
                val store = RoomAccountStore(AccountKey.create("http://localhost:3100", "migration"), "migration", database)
                val evidenceBefore: List<LegacySyncEvidenceEntity>
                try {
                    val dao = store.dao
                    assertEquals("Original title", dao.getServerBase("task")?.title)
                    assertFalse(dao.getServerBase("task")!!.standardConfirmed)
                    assertEquals("Draft title", dao.getDraft("draft")?.draftTitle)
                    assertTrue(store.loadTodos().any { it.title == "Draft title" })
                    assertEquals(4, dao.countOutbox())
                    evidenceBefore = dao.getLegacyEvidence()
                    assertEquals(4, evidenceBefore.size)
                    for (evidence in evidenceBefore) {
                        val archived = Json.parseToJsonElement(evidence.originalJson).jsonObject
                        for ((name, value) in originals.getValue(evidence.sourceOpId)) assertEquals(value, archived[name], "$version:$name")
                        assertFalse(dev.tabbyrush.shared.sync.LegacyStandardIntentMigration.provablyNeverDispatched(evidence))
                    }
                    assertTrue(dao.getAllOutbox().all { it.deliveryState == "REVIEW" && it.envelopeJson == null })
                    assertNull(dao.getMeta()?.hubSchemaVersion)
                    assertEquals("REQUIRED", dao.getMeta()?.scanState)
                    if (version >= 2) {
                        assertTrue(dao.getLocalSettings()!!.notificationsEnabled)
                        assertEquals(1, dao.countLedgerEntries())
                        assertEquals("POSTED", dao.getAllLedgerEntries().single().claimOutcome)
                    }
                } finally { store.close() }
                val restarted = createJvmDatabaseBuilder(file).build()
                try {
                    assertEquals(evidenceBefore, restarted.tabbyRushDao().getLegacyEvidence())
                    assertEquals("Original title", restarted.tabbyRushDao().getServerBase("task")?.title)
                    assertEquals(4, restarted.tabbyRushDao().countOutbox())
                } finally { restarted.close() }
            } finally { directory.deleteRecursively() }
        }
    }

    private fun seedExport(file: File, version: Int): Map<String, JsonObject> {
        val candidates = listOf(File("schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/$version.json"),
            File("shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/$version.json"))
        val schema = Json.parseToJsonElement(candidates.first { it.isFile }.readText()).jsonObject.getValue("database").jsonObject
        val originals = mutableMapOf<String, JsonObject>()
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            val entities = schema.getValue("entities").jsonArray
            for (entity in entities) {
                val objectValue = entity.jsonObject
                val table = objectValue.getValue("tableName").jsonPrimitive.content
                connection.execSQL(objectValue.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                for (index in objectValue["indices"]?.jsonArray.orEmpty()) {
                    connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            insert(connection, "server_base", mapOf("rowId" to JsonPrimitive("task"), "title" to JsonPrimitive("Original title"), "serverRevision" to JsonPrimitive(7L)))
            insert(connection, "editor_drafts", mapOf("rowId" to JsonPrimitive("draft"), "draftTitle" to JsonPrimitive("Draft title"), "localRevision" to JsonPrimitive(10L)))
            insert(connection, "account_sync_meta", mapOf("id" to JsonPrimitive("singleton"), "localRevisionCounter" to JsonPrimitive(10L)))
            for ((index, state) in listOf("PENDING", "DISPATCHING", "UNCERTAIN", "CONFLICT").withIndex()) {
                val id = "operation-$index"
                val values = insert(connection, "outbox", mapOf("opId" to JsonPrimitive(id), "rowId" to JsonPrimitive("row-$index"),
                    "tableName" to JsonPrimitive("todos"), "kind" to JsonPrimitive("create"), "title" to JsonPrimitive("Intent $index"),
                    "deliveryState" to JsonPrimitive(state), "digest" to JsonPrimitive("frozen-$index"), "localRevision" to JsonPrimitive(index + 1L)))
                originals[id] = values
            }
            if (version >= 2) {
                insert(connection, "account_local_settings", mapOf("id" to JsonPrimitive("singleton"), "notificationsEnabled" to JsonPrimitive(1L)))
                insert(connection, "reminder_delivery_ledger", mapOf("accountKey" to JsonPrimitive("account"), "taskId" to JsonPrimitive("task"),
                    "scheduleToken" to JsonPrimitive("token"), "claimOutcome" to JsonPrimitive("POSTED"), "cancellationState" to JsonPrimitive("ACTIVE")))
            }
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.prepare("INSERT INTO room_master_table VALUES (42, ?)").use { statement ->
                statement.bindText(1, schema.getValue("identityHash").jsonPrimitive.content); statement.step()
            }
            connection.execSQL("PRAGMA user_version=$version")
        } finally {
            connection.close()
        }
        return originals
    }

    private fun insert(connection: SQLiteConnection, table: String, overrides: Map<String, JsonElement>): JsonObject {
        val values = linkedMapOf<String, JsonElement>()
        connection.prepare("PRAGMA table_info(`$table`)").use { columns ->
            while (columns.step()) {
                val name = columns.getText(1)
                values[name] = overrides[name] ?: if (columns.getLong(3) == 0L) JsonNull else if (columns.getText(2) == "INTEGER") {
                    JsonPrimitive(if (name == "reminderCount" || name == "defaultReminderCount") 1L else 0L)
                } else JsonPrimitive(if (name == "defaultDateOnlyAnchorTime") "09:00" else "")
            }
        }
        val names = values.keys.joinToString { "`$it`" }
        connection.prepare("INSERT INTO `$table` ($names) VALUES (${values.keys.joinToString { "?" }})").use { statement ->
            values.values.forEachIndexed { index, element ->
                when {
                    element == JsonNull -> statement.bindNull(index + 1)
                    element.jsonPrimitive.isString -> statement.bindText(index + 1, element.jsonPrimitive.content)
                    else -> statement.bindLong(index + 1, element.jsonPrimitive.long)
                }
            }
            statement.step()
        }
        return JsonObject(values)
    }
}
