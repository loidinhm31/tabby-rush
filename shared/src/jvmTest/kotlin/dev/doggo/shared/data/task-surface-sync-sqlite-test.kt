package dev.doggo.shared.data

import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.CompletionChange
import dev.doggo.shared.contract.PersistedTaskSchedule
import dev.doggo.shared.contract.StrictOperationKind
import dev.doggo.shared.contract.TaskDeadline
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.selectNextDeadline
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaskSurfaceSyncSqliteTest {

    private lateinit var tempDir: File
    private lateinit var accountFiles: JvmAccountFiles
    private lateinit var database: DoggoDatabase
    private lateinit var store: RoomAccountStore

    private val accountKey = AccountKey.create("http://localhost:3000", "user-sync-test")

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("doggo-sync-sqlite-test").toFile()
        accountFiles = JvmAccountFiles(tempDir)
        val dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createJvmDatabaseBuilder(dbFile).build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-sync-test",
            database = database,
            appId = "doggo"
        )
    }

    @AfterTest
    fun tearDown() {
        store.close()
        tempDir.deleteRecursively()
    }

    private fun createTimedDeadline(overdueEpochMs: Long = 100_000L) = TaskDeadline(
        date = "2026-10-20",
        time = "10:00",
        zone = "UTC",
        offsetSeconds = 0L,
        reminderAnchorTime = null,
        anchorEpochMs = overdueEpochMs,
        overdueEpochMs = overdueEpochMs
    )

    @Test
    fun testReconcileAcceptedEmitsUpdatedStateInObservers() = runBlocking {
        val now = 10_000L
        val todo = Todo(
            id = "t1",
            title = "Task to Reconcile",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )
        store.saveTodoLocally(todo)

        val outbox = store.dao.getOutboxByRowId("t1")
        assertEquals(1, outbox.size)
        val opId = outbox[0].opId

        val initialEmitted = CompletableDeferred<Unit>()
        val targetReached = CompletableDeferred<Unit>()
        val collectJob = launch(Dispatchers.IO) {
            try {
                store.observeTodos().collect { todos ->
                    if (todos.any { it.id == "t1" && it.isPending }) {
                        initialEmitted.complete(Unit)
                    }
                    if (todos.any { it.id == "t1" && !it.isPending && it.serverRevision == 1L }) {
                        targetReached.complete(Unit)
                        throw kotlinx.coroutines.CancellationException()
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            }
        }

        try {
            initialEmitted.await()
            // Acknowledge the operation via reconcileAccepted
            store.reconcileAccepted(opId = opId, serverRevision = 1L)
            withTimeout(3000) {
                targetReached.await()
            }
        } finally {
            collectJob.cancel()
        }

        val loaded = store.loadTodos().first { it.id == "t1" }
        assertFalse(loaded.isPending)
        assertEquals(1L, loaded.serverRevision)
    }

    @Test
    fun testPromotionUnderMutationLockEmitsToObservers() = runBlocking {
        val initialEmitted = CompletableDeferred<Unit>()
        val targetReached = CompletableDeferred<Unit>()
        val collectJob = launch(Dispatchers.IO) {
            try {
                store.observeTodos().collect { todos ->
                    if (todos.isEmpty()) {
                        initialEmitted.complete(Unit)
                    }
                    if (todos.any { it.id == "promoted-1" && it.title == "Promoted Task" }) {
                        targetReached.complete(Unit)
                        throw kotlinx.coroutines.CancellationException()
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            }
        }

        try {
            initialEmitted.await()
            // Simulate scan promotion under store.withMutationLock
            store.withMutationLock {
                val base = ServerBaseEntity(
                    rowId = "promoted-1",
                    serverRevision = 3L,
                    deleted = false,
                    title = "Promoted Task",
                    completed = false,
                    createdAt = 5000L,
                    updatedAt = 5000L,
                    epoch = "ep-1"
                )
                store.dao.upsertServerBase(base)
            }
            withTimeout(3000) {
                targetReached.await()
            }
        } finally {
            collectJob.cancel()
        }

        val loaded = store.loadTodos().first { it.id == "promoted-1" }
        assertEquals("Promoted Task", loaded.title)
    }

    @Test
    fun testOutboxConflictHeadEmitsHasUnresolvedConflict() = runBlocking {
        val dl = createTimedDeadline(overdueEpochMs = 50_000L)
        val base = ServerBaseEntity(
            rowId = "conflict-1",
            serverRevision = 1L,
            deleted = false,
            title = "Conflict Task",
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
            reminderEnabled = false,
            reminderLeadMinutes = 0L,
            reminderCount = 1L,
            scheduleToken = "tok-1"
        )
        store.dao.upsertServerBase(base)

        val initialEmitted = CompletableDeferred<Unit>()
        val targetReached = CompletableDeferred<List<PersistedTaskSchedule>>()
        val collectJob = launch(Dispatchers.IO) {
            try {
                store.observePersistedSchedule().collect { schedules ->
                    if (schedules.any { it.taskId == "conflict-1" && !it.hasUnresolvedConflict }) {
                        initialEmitted.complete(Unit)
                    }
                    if (schedules.any { it.taskId == "conflict-1" && it.hasUnresolvedConflict }) {
                        targetReached.complete(schedules)
                        throw kotlinx.coroutines.CancellationException()
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            }
        }

        val finalSchedules = try {
            initialEmitted.await()
            // Inject outbox conflict head
            store.withMutationLock {
                val conflictOp = OutboxEntity(
                    opId = "op-conflict",
                    epoch = "ep-1",
                    tableName = "todos",
                    rowId = "conflict-1",
                    kind = StrictOperationKind.UPDATE.wireName,
                    expectedRevision = 1L,
                    title = "Conflict Title",
                    completed = false,
                    createdAt = 1000L,
                    digest = "digest-hex",
                    localRevision = 2L,
                    deliveryState = OutboxEntity.STATE_CONFLICT,
                    createdAtEpochMs = 2000L
                )
                store.dao.upsertOutbox(conflictOp)
            }
            withTimeout(3000) {
                targetReached.await()
            }
        } finally {
            collectJob.cancel()
        }

        assertTrue(finalSchedules.any { it.taskId == "conflict-1" && it.hasUnresolvedConflict })

        // Verify selectNextDeadline includes this schedule with hasUnresolvedConflict = true
        val selection = selectNextDeadline(finalSchedules, nowEpochMs = 10_000L)
        assertNotNull(selection)
        assertEquals("conflict-1", selection.taskId)
        assertTrue(selection.hasUnresolvedConflict)
    }

    @Test
    fun testConcurrentTitleEditPreservedBySetCompletedLocally() = runBlocking {
        val now = 10_000L
        val original = Todo(
            id = "t1",
            title = "Original Title",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )
        store.saveTodoLocally(original)

        // Simulate an edit changing title to "Edited Title"
        val edited = original.copy(title = "Edited Title", updatedAt = now + 1000L)
        store.saveTodoLocally(edited)

        // Complete the task via setCompletedLocally
        val result = store.setCompletedLocally("t1", completed = true, nowEpochMs = now + 5000L)
        assertEquals(CompletionChange.Changed, result)

        val loaded = store.loadTodos().first { it.id == "t1" }
        assertEquals("Edited Title", loaded.title)
        assertTrue(loaded.completed)
        assertEquals(now + 5000L, loaded.updatedAt)
    }
}
