package dev.doggo.shared.data

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.Todo
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

class RoomAccountStoreTest {

    private lateinit var tempDir: File
    private lateinit var accountFiles: JvmAccountFiles
    private lateinit var store: RoomAccountStore
    private lateinit var database: DoggoDatabase

    private val accountKey = AccountKey.create("http://localhost:3000", "user-jvm-test")

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("doggo-jvm-store-test").toFile()
        accountFiles = JvmAccountFiles(tempDir)

        val dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createJvmDatabaseBuilder(dbFile).build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = database,
            appId = "doggo"
        )
    }

    @AfterTest
    fun tearDown() {
        store.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testSaveAndLoadTodosWithBundledSqlite() = runTest {
        val now = System.currentTimeMillis()
        val todo = Todo(
            id = "todo-uuid-1",
            title = "Test SQLite Persistence",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )

        store.saveTodoLocally(todo)

        // Verify draft was written
        val draft = store.dao.getDraft("todo-uuid-1")
        assertNotNull(draft)
        assertEquals("Test SQLite Persistence", draft.draftTitle)
        assertFalse(draft.draftCompleted)

        // Verify outbox was written
        val outbox = store.dao.getOutboxByRowId("todo-uuid-1")
        assertEquals(1, outbox.size)
        assertEquals("create", outbox[0].kind)
        assertEquals(0L, outbox[0].expectedRevision)

        // Load visible projection
        val loaded = store.loadTodos()
        assertEquals(1, loaded.size)
        assertEquals("todo-uuid-1", loaded[0].id)
        assertEquals("Test SQLite Persistence", loaded[0].title)
        assertTrue(loaded[0].isPending) // Marked pending because outbox op exists
    }

    @Test
    fun testUnsentCreateDeleteCancelsLocally() = runTest {
        val now = System.currentTimeMillis()
        val todo = Todo(
            id = "todo-cancel-1",
            title = "Unsent Todo",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L
        )

        store.saveTodoLocally(todo)
        assertEquals(1, store.dao.countOutbox())
        assertEquals(1, store.dao.countDrafts())

        // Delete before dispatching
        store.deleteTodoLocally("todo-cancel-1")

        // Unsent create-delete should cancel locally
        assertEquals(0, store.dao.countOutbox())
        assertEquals(0, store.dao.countDrafts())
        assertEquals(0, store.loadTodos().size)
    }

    @Test
    fun testProcessRestartRecoveryWithFrozenOutbox() = runTest {
        val now = System.currentTimeMillis()
        val todo = Todo(
            id = "todo-restart-1",
            title = "Survive Process Restart",
            completed = true,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L
        )
        store.saveTodoLocally(todo)

        val outboxBefore = store.dao.getOutboxByRowId("todo-restart-1")[0]
        store.markDispatching(outboxBefore.opId)

        // Simulate process death / restart: close handle and open fresh DB on same file
        store.close()

        val dbFile = accountFiles.getDatabaseFile(accountKey)
        val restartedDb = createJvmDatabaseBuilder(dbFile).build()
        val restartedStore = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = restartedDb,
            appId = "doggo"
        )

        try {
            val recoveredOps = restartedStore.dao.getOutboxByRowId("todo-restart-1")
            assertEquals(1, recoveredOps.size)
            val recovered = recoveredOps[0]
            assertEquals(outboxBefore.opId, recovered.opId)
            assertEquals(outboxBefore.digest, recovered.digest)
            assertEquals("Survive Process Restart", recovered.title)
            assertEquals(OutboxEntity.STATE_DISPATCHING, recovered.deliveryState)

            val todos = restartedStore.loadTodos()
            assertEquals(1, todos.size)
            assertEquals("Survive Process Restart", todos[0].title)
        } finally {
            restartedStore.close()
        }
    }

    @Test
    fun testReconcileAcceptedAdvancesServerBaseAndClearsOldDraft() = runTest {
        val now = System.currentTimeMillis()
        val todo = Todo(
            id = "todo-ack-1",
            title = "Acked Todo",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L
        )
        store.saveTodoLocally(todo)

        val op = store.dao.getOutboxByRowId("todo-ack-1")[0]
        store.reconcileAccepted(op.opId, serverRevision = 1L)

        // Outbox entry purged
        assertNull(store.dao.getOutboxEntry(op.opId))
        // Draft cleared because localRevision was <= captured
        assertNull(store.dao.getDraft("todo-ack-1"))

        // Server base confirmed
        val base = store.dao.getServerBase("todo-ack-1")
        assertNotNull(base)
        assertEquals(1L, base.serverRevision)
        assertEquals("Acked Todo", base.title)

        // Visible projection shows confirmed state
        val loaded = store.loadTodos()
        assertEquals(1, loaded.size)
        assertFalse(loaded[0].isPending)
        assertEquals(1L, loaded[0].serverRevision)
    }

    @Test
    fun testReconcileAcceptedPreservesNewerDraft() = runTest {
        val now = System.currentTimeMillis()
        val todo = Todo(
            id = "todo-race-1",
            title = "First Title",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L
        )
        store.saveTodoLocally(todo)
        val op = store.dao.getOutboxByRowId("todo-race-1")[0]
        store.markDispatching(op.opId) // Mark dispatched so subsequent edit cannot coalesce

        // User edits locally creating newer draft (localRevision = 2)
        val edited = todo.copy(title = "Newer Local Title", completed = true)
        store.saveTodoLocally(edited)

        // Old ack arrives for First Title (captured at localRevision = 1)
        store.reconcileAccepted(op.opId, serverRevision = 1L)

        // Base has First Title at revision 1
        val base = store.dao.getServerBase("todo-race-1")
        assertNotNull(base)
        assertEquals(1L, base.serverRevision)
        assertEquals("First Title", base.title)

        // But visible projection reflects newer draft "Newer Local Title"
        val loaded = store.loadTodos()
        assertEquals(1, loaded.size)
        assertEquals("Newer Local Title", loaded[0].title)
        assertTrue(loaded[0].completed)
        assertTrue(loaded[0].isPending)
    }

    @Test
    fun testWipeLocalDataClearsAllTables() = runTest {
        store.saveTodoLocally(
            Todo(
                id = "todo-wipe-1",
                title = "To Be Wiped",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        assertEquals(1, store.dao.countDrafts())
        assertEquals(1, store.dao.countOutbox())

        store.wipeLocalData()

        assertEquals(0, store.dao.countDrafts())
        assertEquals(0, store.dao.countOutbox())
        assertEquals(0, store.dao.countServerBase())
        assertEquals(0, store.loadTodos().size)
    }

    @Test
    fun testCloseAndWipeDatabaseFiles() = runTest {
        store.saveTodoLocally(
            Todo(
                id = "todo-del-1",
                title = "File delete test",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Close handles before deleting files
        store.close()

        val deleted = accountFiles.deleteDatabaseAndArtifacts(accountKey)
        assertTrue(deleted)

        val dbFile = accountFiles.getDatabaseFile(accountKey)
        assertFalse(dbFile.exists())
    }

    @Test
    fun testRecoverInterruptedDispatchesConvertsToUncertain() = runTest {
        store.saveTodoLocally(
            Todo(
                id = "todo-int-1",
                title = "Interrupted",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        val op = store.dao.getOutboxByRowId("todo-int-1")[0]
        store.markDispatching(op.opId)

        assertEquals(OutboxEntity.STATE_DISPATCHING, store.dao.getOutboxEntry(op.opId)?.deliveryState)

        // Crash / restart recovery
        val recoveredCount = store.recoverInterruptedDispatches()
        assertEquals(1, recoveredCount)

        val recovered = store.dao.getOutboxEntry(op.opId)
        assertNotNull(recovered)
        assertEquals(OutboxEntity.STATE_UNCERTAIN, recovered.deliveryState)
    }

    @Test
    fun testFrozenStoreRejectsMutations() = runTest {
        store.isFrozen = true

        val result = runCatching {
            store.saveTodoLocally(
                Todo(
                    id = "todo-fail",
                    title = "Should Fail",
                    completed = false,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
