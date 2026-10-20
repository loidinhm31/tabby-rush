package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.sync.RecordingNativeReminderAdapter
import dev.tabbyrush.shared.sync.ReminderDeliveryGate
import dev.tabbyrush.shared.sync.ReminderSchedulingCoordinator
import kotlinx.coroutines.async
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

class ReminderSchedulingCoordinatorSqliteSmokeTest {

    private lateinit var tempDir: File
    private lateinit var accountFiles: JvmAccountFiles
    private lateinit var store: RoomAccountStore
    private lateinit var database: TabbyRushDatabase
    private lateinit var coordinator: ReminderSchedulingCoordinator
    private lateinit var adapter: RecordingNativeReminderAdapter
    private lateinit var gate: ReminderDeliveryGate

    private val accountKey = AccountKey.create("http://localhost:3000", "user-smoke-test")
    private var currentGeneration = 1L
    private var wipeMarkerPresent = false

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("tabbyrush-sqlite-smoke-test").toFile()
        accountFiles = JvmAccountFiles(tempDir)
        val dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createJvmDatabaseBuilder(dbFile).build()

        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-smoke-test",
            database = database,
            appId = "tabby-rush"
        )

        gate = ReminderDeliveryGate(
            accountKey = accountKey,
            getPersistedGeneration = { currentGeneration },
            hasPendingWipeMarker = { wipeMarkerPresent }
        )

        adapter = RecordingNativeReminderAdapter()
        val occurrenceStore = ReminderOccurrenceStore(accountKey, store.dao)

        coordinator = ReminderSchedulingCoordinator(
            accountKey = accountKey,
            store = store,
            occurrenceStore = occurrenceStore,
            adapter = adapter,
            deliveryGate = gate,
            getGeneration = { currentGeneration },
            hasWipeMarker = { wipeMarkerPresent },
            clock = { 0L }
        )
    }

    @AfterTest
    fun tearDown() {
        store.close()
        tempDir.deleteRecursively()
    }

    private fun createDeadline(anchor: Long = 1_000_000L) = TaskDeadline(
        date = "2026-10-20",
        time = "10:00",
        zone = "UTC",
        offsetSeconds = 0L,
        anchorEpochMs = anchor,
        overdueEpochMs = anchor
    )

    @Test
    fun testLocalSaveSchedulesAlarmAndDraftDoesNot() = runTest {
        val deadline = createDeadline(1_000_000L)
        val rule = ReminderRule(enabled = true, leadMinutes = 15L, intervalMinutes = null, count = 1L)
        val todo = Todo(
            id = "task-1",
            title = "Test task",
            completed = false,
            createdAt = 1000L,
            deadline = deadline,
            reminderRule = rule,
            scheduleToken = "token-1"
        )

        // 1. Saved locally
        store.saveTodoLocally(todo)
        coordinator.reconcile(nowEpochMs = 0L)

        assertEquals(1, adapter.scheduledAlarms.size)
        assertEquals("task-1", adapter.scheduledAlarms[0].taskId)
        val originalToken = adapter.scheduledAlarms[0].scheduleToken

        // 2. Unsaved editor draft modification does NOT schedule
        store.dao.upsertDraft(
            EditorDraftEntity(
                rowId = "task-2",
                draftTitle = "Unsaved draft",
                draftCompleted = false,
                localRevision = 99L,
                updatedAt = 2000L,
                isDeleted = false,
                deadlineDate = "2026-10-20",
                deadlineTime = "12:00",
                deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L,
                deadlineAnchorEpochMs = 2_000_000L,
                deadlineOverdueEpochMs = 2_000_000L,
                reminderEnabled = true,
                reminderCount = 1L,
                scheduleToken = "draft-token"
            )
        )
        coordinator.reconcile(nowEpochMs = 0L)
        // Still only 1 alarm scheduled (draft excluded)
        assertEquals(1, adapter.scheduledAlarms.size)
        assertEquals("task-1", adapter.scheduledAlarms[0].taskId)

        // 3. Title update preserves scheduleToken
        val updatedTodo = todo.copy(title = "Updated title", scheduleToken = originalToken)
        store.saveTodoLocally(updatedTodo)
        coordinator.reconcile(nowEpochMs = 0L)
        assertEquals(1, adapter.scheduledAlarms.size)
        assertEquals(originalToken, adapter.scheduledAlarms[0].scheduleToken)

        // 4. Delivery fetches fresh title at delivery time
        val delivered = coordinator.deliverReminder(
            taskId = "task-1",
            scheduleToken = originalToken,
            occurrenceIndex = 0,
            triggerGeneration = 1L,
            nowEpochMs = 1_000_000L - (15L * 60_000L) + 10_000L
        )
        assertTrue(delivered)
        assertEquals(1, adapter.postedCalls.size)
        assertEquals("Updated title", adapter.postedCalls[0].title)

        // 5. Completion cancels alarm and removes posted notification
        val completedTodo = updatedTodo.copy(completed = true)
        store.saveTodoLocally(completedTodo)
        coordinator.reconcile(nowEpochMs = 500_000L)
        assertTrue(adapter.scheduledAlarms.isEmpty())
        assertEquals(listOf("platform-notif-1"), adapter.removedNotificationIds)
    }

    @Test
    fun testConcurrentCallbacksAndWipeRace() = runTest {
        val deadline = createDeadline(1_000_000L)
        val rule = ReminderRule(enabled = true, leadMinutes = 0L, intervalMinutes = null, count = 1L)
        val todo = Todo(id = "task-race", title = "Race", completed = false, createdAt = 1000L, deadline = deadline, reminderRule = rule, scheduleToken = "token-race")
        store.saveTodoLocally(todo)
        val token = store.loadTodos().first { it.id == "task-race" }.scheduleToken!!
        coordinator.reconcile(nowEpochMs = 0L)

        // Concurrent duplicate callbacks: exactly one posts
        val call1 = async { coordinator.deliverReminder("task-race", token, 0, 1L, nowEpochMs = 1_000_100L) }
        val call2 = async { coordinator.deliverReminder("task-race", token, 0, 1L, nowEpochMs = 1_000_100L) }
        val r1 = call1.await()
        val r2 = call2.await()

        assertTrue(r1 xor r2) // Exactly one succeeded!
        assertEquals(1, adapter.postedCalls.size)

        // Wipe race vs post: test against a fresh unconsumed pending task
        val todoWipe = Todo(id = "task-wipe", title = "Wipe test", completed = false, createdAt = 1000L, deadline = deadline, reminderRule = rule, scheduleToken = "token-wipe")
        store.saveTodoLocally(todoWipe)
        coordinator.reconcile(nowEpochMs = 0L)
        val wipeToken = store.loadTodos().first { it.id == "task-wipe" }.scheduleToken!!

        wipeMarkerPresent = true
        val postUnderWipe = coordinator.deliverReminder("task-wipe", wipeToken, 0, 1L, nowEpochMs = 1_000_100L)
        assertFalse(postUnderWipe) // Fails closed immediately under wipe marker!

        // Occurrence was NOT claimed or posted due to wipe guard
        val wipeEntries = store.dao.getLedgerEntriesForTask(accountKey.toString(), 1L, "task-wipe")
        assertEquals(ReminderDeliveryLedgerEntity.OUTCOME_PENDING, wipeEntries[0].claimOutcome)
    }
}
