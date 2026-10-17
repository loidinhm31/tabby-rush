package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.StrictOperationKind
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.Todo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaskSurfaceStoreSqliteTest {

    private lateinit var tempDir: File
    private lateinit var accountFiles: JvmAccountFiles
    private lateinit var database: TabbyRushDatabase
    private lateinit var store: RoomAccountStore

    private val accountKey = AccountKey.create("http://localhost:3000", "user-surface-test")

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("tabbyrush-surface-sqlite-test").toFile()
        accountFiles = JvmAccountFiles(tempDir)
        val dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createJvmDatabaseBuilder(dbFile).build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-surface-test",
            database = database,
            appId = "tabby-rush"
        )
    }

    @AfterTest
    fun tearDown() {
        store.close()
        tempDir.deleteRecursively()
    }

    private fun reopenStore() {
        store.close()
        val dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createJvmDatabaseBuilder(dbFile).build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-surface-test",
            database = database,
            appId = "tabby-rush"
        )
    }

    private fun createTimedDeadline(overdueEpochMs: Long = 2_000_000L) = TaskDeadline(
        date = "2026-10-20",
        time = "10:00",
        zone = "UTC",
        offsetSeconds = 0L,
        reminderAnchorTime = null,
        anchorEpochMs = overdueEpochMs,
        overdueEpochMs = overdueEpochMs
    )

    @Test
    fun testObserveTodosColdReplay() = runTest {
        val now = 10_000L
        val todo = Todo(
            id = "t1",
            title = "Existing task",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )
        store.saveTodoLocally(todo)

        // Cold subscriber immediately receives initial loaded list
        val initial = store.observeTodos().first()
        assertEquals(1, initial.size)
        assertEquals("t1", initial[0].id)
        assertEquals("Existing task", initial[0].title)
        assertFalse(initial[0].completed)
    }

    @Test
    fun testSetCompletedLocallyChangesStateAndUpdatesMonotonicTime() = runTest {
        val now = 10_000L
        val todo = Todo(
            id = "t1",
            title = "Task to complete",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )
        store.saveTodoLocally(todo)

        val completedNow = 15_000L
        val result = store.setCompletedLocally("t1", completed = true, nowEpochMs = completedNow)
        assertEquals(CompletionChange.Changed, result)

        val loaded = store.loadTodos().first { it.id == "t1" }
        assertTrue(loaded.completed)
        assertEquals(completedNow, loaded.updatedAt)
        assertEquals("Task to complete", loaded.title)

        // Verify outbox entry records intent mask with COMPLETED
        val outbox = store.dao.getOutboxByRowId("t1")
        assertTrue(outbox.isNotEmpty())
        val latest = outbox.last()
        assertEquals(true, latest.completed)
        assertTrue(latest.intentMask?.contains(OutboxEntity.INTENT_COMPLETED) == true)
    }

    @Test
    fun testSetCompletedLocallyDuplicateIsUnchangedNoOp() = runTest {
        val now = 10_000L
        val todo = Todo(
            id = "t1",
            title = "Already completed",
            completed = true,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )
        store.saveTodoLocally(todo)

        val outboxCountBefore = store.dao.getOutboxByRowId("t1").size
        val metaBefore = store.dao.getMeta()?.localRevisionCounter ?: 0L

        // Call again with desired = true
        val result = store.setCompletedLocally("t1", completed = true, nowEpochMs = 20_000L)
        assertEquals(CompletionChange.Unchanged, result)

        val outboxCountAfter = store.dao.getOutboxByRowId("t1").size
        val metaAfter = store.dao.getMeta()?.localRevisionCounter ?: 0L

        assertEquals(outboxCountBefore, outboxCountAfter)
        assertEquals(metaBefore, metaAfter)
    }

    @Test
    fun testSetCompletedLocallyMissingRecordReturnsMissing() = runTest {
        val result = store.setCompletedLocally("non-existent", completed = true, nowEpochMs = 10_000L)
        assertEquals(CompletionChange.Missing, result)
        assertEquals(0, store.dao.countOutbox())
    }

    @Test
    fun testSetCompletedLocallyTombstoneReturnsMissingWithoutException() = runTest {
        // Insert a permanent server tombstone
        val tombstone = ServerBaseEntity(
            rowId = "tombstone-1",
            serverRevision = 5L,
            deleted = true,
            title = "Deleted task",
            completed = false,
            createdAt = 1000L,
            updatedAt = 2000L,
            epoch = "ep-1"
        )
        store.dao.upsertServerBase(tombstone)

        val result = store.setCompletedLocally("tombstone-1", completed = true, nowEpochMs = 10_000L)
        assertEquals(CompletionChange.Missing, result)
        assertEquals(0, store.dao.countOutbox())
    }

    @Test
    fun testSetCompletedLocallyTombstoneWithResidualDraftReturnsMissingWithoutException() = runTest {
        // Insert permanent server tombstone
        val tombstone = ServerBaseEntity(
            rowId = "tombstone-residual",
            serverRevision = 5L,
            deleted = true,
            title = "Deleted on server",
            completed = false,
            createdAt = 1000L,
            updatedAt = 2000L,
            epoch = "ep-1"
        )
        store.dao.upsertServerBase(tombstone)

        // Insert stale residual local draft
        val draft = EditorDraftEntity(
            rowId = "tombstone-residual",
            draftTitle = "Stale draft title",
            draftCompleted = false,
            localRevision = 1L,
            updatedAt = 1500L,
            isDeleted = false
        )
        store.dao.upsertDraft(draft)

        // Calling setCompletedLocally must return Missing, not throw IllegalStateException, and preserve existing draft
        val result = store.setCompletedLocally("tombstone-residual", completed = true, nowEpochMs = 10_000L)
        assertEquals(CompletionChange.Missing, result)
        assertEquals(0, store.dao.countOutbox())
        assertNotNull(store.dao.getDraft("tombstone-residual"))
    }

    @Test
    fun testSetCompletedLocallyTombstoneWithResidualOutboxReturnsMissingWithoutException() = runTest {
        // Insert permanent server tombstone
        val tombstone = ServerBaseEntity(
            rowId = "tombstone-outbox",
            serverRevision = 5L,
            deleted = true,
            title = "Deleted on server",
            completed = false,
            createdAt = 1000L,
            updatedAt = 2000L,
            epoch = "ep-1"
        )
        store.dao.upsertServerBase(tombstone)

        // Insert residual outbox operation
        val outboxOp = OutboxEntity(
            opId = "op-residual",
            epoch = "ep-1",
            tableName = "todos",
            rowId = "tombstone-outbox",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 4L,
            title = "Stale outbox title",
            completed = false,
            createdAt = 1000L,
            digest = "digest-hex",
            localRevision = 1L,
            deliveryState = OutboxEntity.STATE_PENDING,
            createdAtEpochMs = 2000L
        )
        store.dao.upsertOutbox(outboxOp)

        // Calling setCompletedLocally must return Missing without throwing and not enqueue new outbox operations
        val initialCount = store.dao.countOutbox()
        val result = store.setCompletedLocally("tombstone-outbox", completed = true, nowEpochMs = 10_000L)
        assertEquals(CompletionChange.Missing, result)
        assertEquals(initialCount, store.dao.countOutbox())
    }

    @Test
    fun testSetCompletedLocallyPreservesConcurrentlyChangedFields() = runTest {
        // Set up task on server_base with title, deadline, reminder, and token
        val dl = createTimedDeadline(overdueEpochMs = 50_000L)
        val meta = AccountSyncMetaEntity(
            id = "singleton",
            activeEpoch = "ep-1",
            syncStatus = "ready-v2"
        )
        store.dao.upsertMeta(meta)

        val base = ServerBaseEntity(
            rowId = "t1",
            serverRevision = 2L,
            deleted = false,
            title = "Original Title",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            epoch = "ep-1",
            deadlineDate = dl.date,
            deadlineTime = dl.time,
            deadlineZone = dl.zone,
            deadlineOffsetSeconds = dl.offsetSeconds,
            deadlineAnchorEpochMs = dl.anchorEpochMs,
            deadlineOverdueEpochMs = dl.overdueEpochMs,
            reminderEnabled = true,
            reminderLeadMinutes = 15L,
            reminderCount = 1L,
            scheduleToken = "token-preserved"
        )
        store.dao.upsertServerBase(base)

        // Perform completion
        val result = store.setCompletedLocally("t1", completed = true, nowEpochMs = 25_000L)
        assertEquals(CompletionChange.Changed, result)

        val loaded = store.loadTodos().first { it.id == "t1" }
        assertEquals("Original Title", loaded.title)
        assertTrue(loaded.completed)
        assertEquals(dl, loaded.deadline)
        assertEquals("token-preserved", loaded.scheduleToken)
        assertEquals(2L, loaded.serverRevision)
    }

    @Test
    fun testSetCompletedLocallyPersistsAcrossReopen() = runTest {
        val now = 10_000L
        val todo = Todo(
            id = "t1",
            title = "Persistent task",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )
        store.saveTodoLocally(todo)

        val result = store.setCompletedLocally("t1", completed = true, nowEpochMs = 12_000L)
        assertEquals(CompletionChange.Changed, result)

        reopenStore()

        val loaded = store.loadTodos().first { it.id == "t1" }
        assertTrue(loaded.completed)
        assertEquals(12_000L, loaded.updatedAt)
    }

    @Test
    fun testObserveTodosEmitsOnCompletionMutation() = kotlinx.coroutines.runBlocking {
        val now = 10_000L
        val todo = Todo(
            id = "t1",
            title = "Observe mutation",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )
        store.saveTodoLocally(todo)

        val targetReached = kotlinx.coroutines.CompletableDeferred<Unit>()
        val initialEmitted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val collectJob = launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                store.observeTodos().collect { todos ->
                    if (todos.isNotEmpty() && !todos[0].completed) {
                        initialEmitted.complete(Unit)
                    }
                    if (todos.any { it.id == "t1" && it.completed }) {
                        targetReached.complete(Unit)
                        throw kotlinx.coroutines.CancellationException()
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            }
        }

        try {
            initialEmitted.await()
            // Trigger mutation
            store.setCompletedLocally("t1", completed = true, nowEpochMs = 15_000L)
            kotlinx.coroutines.withTimeout(3000) {
                targetReached.await()
            }
        } finally {
            collectJob.cancel()
        }

        val loaded = store.loadTodos().first { it.id == "t1" }
        assertTrue(loaded.completed)
    }
}
