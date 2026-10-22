package dev.tabbyrush.shared.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.sqlite.use
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.OwnerBindingState
import dev.tabbyrush.shared.contract.TaskRef
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class RoomMigrationToV5Test {
    private lateinit var directory: File

    @BeforeTest fun setup() {
        directory = Files.createTempDirectory("room-migration-v5-test").toFile()
    }

    @AfterTest fun cleanup() {
        directory.deleteRecursively()
    }

    @Test fun historicalExportsOneThroughFourUpgradeLosslesslyToV5() = runTest {
        for (version in 1..4) {
            val file = File(directory, "account-$version.db")
            seedExport(file, version)
            val database = createJvmDatabaseBuilder(file).build()
            val accountKey = AccountKey.create("http://localhost:3100", "test-user")
            val store = RoomAccountStore(accountKey, "test-user", database)

            try {
                val dao = store.dao

                // 1. Unresolved binding state initially
                assertEquals(OwnerBindingState.UNRESOLVED_LOCAL, store.getOwnerBindingState())
                val meta = dao.getMeta()
                assertNotNull(meta)
                assertEquals(0L, meta.accessGeneration)
                assertEquals("UNRESOLVED_LOCAL", meta.ownerBindingState)
                assertNull(meta.boundOwnerUserId)

                // 2. server_base rows preserved with LOCAL_ACCOUNT
                val base = dao.getServerBase("task")
                assertNotNull(base)
                assertEquals("Original title", base.title)
                assertEquals(BINDING_LOCAL_ACCOUNT, base.ownerBinding)
                assertEquals(BINDING_LOCAL_ACCOUNT, base.bindingKey)
                assertNull(base.ownerUserId)
                assertEquals("todos", base.tableName)

                // 3. editor_drafts rows preserved with LOCAL_ACCOUNT
                val draft = dao.getDraft("draft")
                assertNotNull(draft)
                assertEquals("Draft title", draft.draftTitle)
                assertEquals(BINDING_LOCAL_ACCOUNT, draft.ownerBinding)
                assertEquals(BINDING_LOCAL_ACCOUNT, draft.bindingKey)
                assertNull(draft.ownerUserId)

                // 4. outbox rows preserved with LOCAL_ACCOUNT
                val outboxes = dao.getAllOutbox()
                assertEquals(4, outboxes.size)
                assertTrue(outboxes.all { it.ownerBinding == BINDING_LOCAL_ACCOUNT && it.ownerUserId == null })
                assertTrue(outboxes.all { it.bindingKey == BINDING_LOCAL_ACCOUNT })

                // 5. Offline display works before binding
                val todos = store.loadTodos()
                assertTrue(todos.any { it.title == "Draft title" })

                // 6. If version >= 2, ledger entries preserved with LOCAL_ACCOUNT
                if (version >= 2) {
                    val ledgerEntries = dao.getAllLedgerEntries()
                    assertEquals(1, ledgerEntries.size)
                    val entry = ledgerEntries.single()
                    assertEquals("POSTED", entry.claimOutcome)
                    assertEquals(BINDING_LOCAL_ACCOUNT, entry.ownerBinding)
                    assertEquals(BINDING_LOCAL_ACCOUNT, entry.bindingKey)
                    assertNull(entry.ownerUserId)
                }

                // 7. If version >= 4, legacy evidence preserved
                if (version >= 4) {
                    val evidence = dao.getLegacyEvidence()
                    assertEquals(listOf("legacy-1"), evidence.map { it.sourceOpId })
                    assertEquals("{}", evidence.single().originalJson)
                    assertTrue(evidence.all { it.ownerBinding == BINDING_LOCAL_ACCOUNT })
                }
            } finally {
                store.close()
            }
        }
    }

    @Test fun twoStageOwnerBindingValidatesAccountKeyAndBindsAtomically() = runTest {
        val file = File(directory, "binding-test.db")
        seedExport(file, 4)
        val database = createJvmDatabaseBuilder(file).build()
        val origin = "http://localhost:3100"
        val expectedUser = "test-user"
        val accountKey = AccountKey.create(origin, expectedUser)
        val store = RoomAccountStore(accountKey, expectedUser, database, canonicalOrigin = origin)

        try {
            val dao = store.dao
            assertEquals(OwnerBindingState.UNRESOLVED_LOCAL, store.getOwnerBindingState())

            // 1. Attempt binding with wrong user identity -> rejected, no mutations
            val wrongIdentity = AccountIdentity(userId = "wrong-user", username = "wrong", email = "wrong@example.com")
            val rejectResult = store.initializeOwnerBinding(wrongIdentity)
            assertEquals(OwnerBindingState.UNRESOLVED_LOCAL, rejectResult)
            assertEquals(OwnerBindingState.UNRESOLVED_LOCAL, store.getOwnerBindingState())
            assertEquals(BINDING_LOCAL_ACCOUNT, dao.getServerBase("task")!!.ownerBinding)

            // 2. Attempt binding with matching identity -> accepted, atomic update
            val correctIdentity = AccountIdentity(userId = expectedUser, username = "test", email = "test@example.com")
            val successResult = store.initializeOwnerBinding(correctIdentity)
            assertEquals(OwnerBindingState.BOUND, successResult)
            assertEquals(OwnerBindingState.BOUND, store.getOwnerBindingState())

            val meta = dao.getMeta()!!
            assertEquals("BOUND", meta.ownerBindingState)
            assertEquals(expectedUser, meta.boundOwnerUserId)

            val boundKey = boundBindingKey(expectedUser)

            val boundBase = dao.getServerBase("task")!!
            assertEquals("BOUND", boundBase.ownerBinding)
            assertEquals(expectedUser, boundBase.ownerUserId)
            assertEquals(boundKey, boundBase.bindingKey)

            val boundDraft = dao.getDraft("draft")!!
            assertEquals("BOUND", boundDraft.ownerBinding)
            assertEquals(expectedUser, boundDraft.ownerUserId)
            assertEquals(boundKey, boundDraft.bindingKey)

            val boundOutboxes = dao.getAllOutbox()
            assertTrue(boundOutboxes.all { it.ownerBinding == "BOUND" && it.ownerUserId == expectedUser })
            assertTrue(boundOutboxes.all { it.bindingKey == boundKey })

            val boundLedger = dao.getAllLedgerEntries()
            assertTrue(boundLedger.all { it.ownerBinding == "BOUND" && it.ownerUserId == expectedUser })

            val boundEvidence = dao.getLegacyEvidence()
            assertTrue(boundEvidence.all { it.ownerBinding == "BOUND" && it.ownerUserId == expectedUser })

            // 3. Idempotent re-binding with same identity
            assertEquals(OwnerBindingState.BOUND, store.initializeOwnerBinding(correctIdentity))

            // 4. Bound rows survive database close and restart
            store.close()
            val restartedDb = createJvmDatabaseBuilder(file).build()
            val restartedStore = RoomAccountStore(accountKey, expectedUser, restartedDb)
            try {
                assertEquals(OwnerBindingState.BOUND, restartedStore.getOwnerBindingState())
                assertEquals(expectedUser, restartedStore.dao.getServerBase("task")!!.ownerUserId)
                assertEquals(boundKey, restartedStore.dao.getServerBase("task")!!.bindingKey)
            } finally {
                restartedStore.close()
            }
        } finally {
            database.close()
        }
    }

    private fun seedExport(file: File, version: Int): Map<String, JsonObject> {
        val candidates = listOf(
            File("schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/$version.json"),
            File("shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/$version.json")
        )
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
                val values = insert(connection, "outbox", mapOf(
                    "opId" to JsonPrimitive(id),
                    "rowId" to JsonPrimitive("row-$index"),
                    "tableName" to JsonPrimitive("todos"),
                    "kind" to JsonPrimitive("create"),
                    "title" to JsonPrimitive("Intent $index"),
                    "deliveryState" to JsonPrimitive(state),
                    "localRevision" to JsonPrimitive(index + 1L)
                ) + if (version >= 4) mapOf("dispatchHistory" to JsonPrimitive("NEVER_DISPATCHED"), "payloadJson" to JsonPrimitive("{}")) else mapOf("digest" to JsonPrimitive("frozen-$index")))
                originals[id] = values
            }
            if (version >= 2) {
                insert(connection, "account_local_settings", mapOf("id" to JsonPrimitive("singleton"), "notificationsEnabled" to JsonPrimitive(1L)))
                insert(connection, "reminder_delivery_ledger", mapOf(
                    "accountKey" to JsonPrimitive("key"),
                    "generation" to JsonPrimitive(1L),
                    "taskId" to JsonPrimitive("task"),
                    "scheduleToken" to JsonPrimitive("token"),
                    "occurrenceIndex" to JsonPrimitive(0L),
                    "dueEpochMs" to JsonPrimitive(1000L),
                    "claimOutcome" to JsonPrimitive("POSTED"),
                    "cancellationState" to JsonPrimitive("ACTIVE"),
                    "updatedAtEpochMs" to JsonPrimitive(1000L)
                ))
            }
            if (version >= 4) {
                insert(connection, "legacy_sync_evidence", mapOf(
                    "sourceOpId" to JsonPrimitive("legacy-1"),
                    "rowId" to JsonPrimitive("row-1"),
                    "originalJson" to JsonPrimitive("{}"),
                    "outcome" to JsonPrimitive("PENDING_REVIEW")
                ))
            }
            connection.execSQL("PRAGMA user_version=$version")
        } finally {
            connection.close()
        }
        return originals
    }

    private fun insert(connection: androidx.sqlite.SQLiteConnection, table: String, overrides: Map<String, JsonPrimitive>): JsonObject {
        val values = linkedMapOf<String, JsonElement>()
        connection.prepare("PRAGMA table_info(`$table`)").use { columns ->
            while (columns.step()) {
                val name = columns.getText(1)
                values[name] = overrides[name] ?: if (columns.getLong(3) == 0L) JsonNull else if (columns.getText(2) == "INTEGER") {
                    JsonPrimitive(if (name == "reminderCount" || name == "defaultReminderCount") 1L else 0L)
                } else JsonPrimitive(if (name == "defaultDateOnlyAnchorTime") "09:00" else "")
            }
        }
        val keys = values.keys.toList()
        val sql = "INSERT INTO `$table` (${keys.joinToString { "`$it`" }}) VALUES (${keys.joinToString { "?" }})"
        connection.prepare(sql).use { statement ->
            keys.forEachIndexed { index, key ->
                val v = values.getValue(key)
                when {
                    v == JsonNull -> statement.bindNull(index + 1)
                    v.jsonPrimitive.isString -> statement.bindText(index + 1, v.jsonPrimitive.content)
                    v.jsonPrimitive.booleanOrNull != null -> statement.bindLong(index + 1, if (v.jsonPrimitive.boolean) 1L else 0L)
                    v.jsonPrimitive.longOrNull != null -> statement.bindLong(index + 1, v.jsonPrimitive.long)
                    else -> statement.bindDouble(index + 1, v.jsonPrimitive.double)
                }
            }
            statement.step()
        }
        return JsonObject(values)
    }
}
