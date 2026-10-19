package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.StrictOperationKind
import dev.tabbyrush.shared.contract.StrictSyncV2Contract
import dev.tabbyrush.shared.contract.StrictSyncV2Digest
import dev.tabbyrush.shared.contract.CompletionChange
import dev.tabbyrush.shared.contract.StrictSyncV3Contract
import kotlin.test.assertFailsWith
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
    private lateinit var database: TabbyRushDatabase

    private val accountKey = AccountKey.create("http://localhost:3000", "user-jvm-test")

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("tabbyrush-jvm-store-test").toFile()
        accountFiles = JvmAccountFiles(tempDir)

        val dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createJvmDatabaseBuilder(dbFile).build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = database,
            appId = "tabby-rush"
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
            appId = "tabby-rush"
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

    @Test
    fun testV1AccountRejectsDeadlines() = runTest {
        val v1Store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = database,
            defaultContractVersion = OutboxEntity.CONTRACT_VERSION_V1
        )
        val deadline = TaskDeadline(
            date = "2026-10-15",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1000L,
            overdueEpochMs = 1000L
        )
        val todoWithDeadline = Todo(
            id = "todo-v1-deadline-reject",
            title = "Must Reject",
            completed = false,
            createdAt = 1000L,
            deadline = deadline,
            scheduleToken = "tok-1"
        )
        val result = runCatching {
            v1Store.saveTodoLocally(todoWithDeadline)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testV2SaveGeneratesTokenAndPreservesOnTitleEdit() = runTest {
        val v2Store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = database,
            defaultContractVersion = OutboxEntity.CONTRACT_VERSION_V2
        )
        val deadline = TaskDeadline(
            date = "2026-10-15",
            time = "15:00",
            zone = "Asia/Saigon",
            offsetSeconds = 25200L,
            anchorEpochMs = 1792051200000L,
            overdueEpochMs = 1792051200000L
        )
        val rule = ReminderRule(enabled = true, leadMinutes = 15L, count = 1L)
        val initialTodo = Todo(
            id = "todo-v2-token-1",
            title = "Initial Title",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            deadline = deadline,
            reminderRule = rule,
            scheduleToken = "sched-tok-initial"
        )

        v2Store.saveTodoLocally(initialTodo)

        val loaded = v2Store.loadTodos().first { it.id == "todo-v2-token-1" }
        val generatedToken = loaded.scheduleToken
        assertNotNull(generatedToken)
        assertEquals(deadline, loaded.deadline)
        assertEquals(rule, loaded.reminderRule)

        // Reconcile accepted create so server_base has revision 1
        val createOp = v2Store.dao.getOutboxByRowId("todo-v2-token-1").first()
        v2Store.reconcileAccepted(createOp.opId, 1L)

        // Edit title only
        val editedTitleTodo = loaded.copy(title = "Changed Title", updatedAt = 2000L)
        v2Store.saveTodoLocally(editedTitleTodo)

        val reloaded = v2Store.loadTodos().first { it.id == "todo-v2-token-1" }
        assertEquals("Changed Title", reloaded.title)
        // Schedule token MUST be preserved
        assertEquals(generatedToken, reloaded.scheduleToken)

        // Check outbox intentMask on update
        val latestOp = v2Store.dao.getOutboxByRowId("todo-v2-token-1").last()
        assertEquals(OutboxEntity.INTENT_TITLE, latestOp.intentMask)
        assertEquals(OutboxEntity.CONTRACT_VERSION_V2, latestOp.contractVersion)
    }

    @Test
    fun testV2SemanticChangeRotatesScheduleToken() = runTest {
        val v2Store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = database,
            defaultContractVersion = OutboxEntity.CONTRACT_VERSION_V2
        )
        val d1 = TaskDeadline(
            date = "2026-10-15",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 100000L,
            overdueEpochMs = 100000L
        )
        val initial = Todo(
            id = "todo-v2-rot-1",
            title = "Semantic Change Test",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            deadline = d1,
            reminderRule = ReminderRule(enabled = true, leadMinutes = 0L, count = 1L),
            scheduleToken = "init-tok"
        )
        v2Store.saveTodoLocally(initial)
        val initialLoaded = v2Store.loadTodos().first { it.id == "todo-v2-rot-1" }
        val tok1 = initialLoaded.scheduleToken
        assertNotNull(tok1)

        // Semantic change: change deadline date and anchor
        val d2 = d1.copy(date = "2026-10-16", anchorEpochMs = 186400000L, overdueEpochMs = 186400000L)
        val changed = initialLoaded.copy(deadline = d2, updatedAt = 2000L)
        v2Store.saveTodoLocally(changed)

        val reloaded = v2Store.loadTodos().first { it.id == "todo-v2-rot-1" }
        val tok2 = reloaded.scheduleToken
        assertNotNull(tok2)
        assertTrue(tok1 != tok2, "Schedule token should rotate on semantic deadline change")

        // Semantic change: clear deadline
        val cleared = reloaded.copy(deadline = null, reminderRule = ReminderRule.DISABLED, scheduleToken = null, updatedAt = 3000L)
        v2Store.saveTodoLocally(cleared)

        val clearedLoaded = v2Store.loadTodos().first { it.id == "todo-v2-rot-1" }
        assertNull(clearedLoaded.deadline)
        assertNull(clearedLoaded.scheduleToken)
        assertFalse(clearedLoaded.reminderRule.enabled)
    }

    @Test
    fun testClearingDeadlineToServerBaseDoesNotResurrect() = runTest {
        val v2Store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = database,
            defaultContractVersion = OutboxEntity.CONTRACT_VERSION_V2
        )
        val d = TaskDeadline(
            date = "2026-10-15",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 100000L,
            overdueEpochMs = 100000L
        )
        val initial = Todo(
            id = "todo-v2-clear-base",
            title = "Deadline in Base Test",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            deadline = d,
            reminderRule = ReminderRule(enabled = true, leadMinutes = 15L, count = 1L),
            scheduleToken = "tok-clear-1"
        )
        v2Store.saveTodoLocally(initial)
        val op = v2Store.dao.getOutboxByRowId("todo-v2-clear-base").first()
        v2Store.reconcileAccepted(op.opId, 1L)

        // Verify base has deadline
        val base = v2Store.dao.getServerBase("todo-v2-clear-base")
        assertNotNull(base?.deadlineDate)

        // Now edit task locally to remove deadline completely
        val loaded = v2Store.loadTodos().first { it.id == "todo-v2-clear-base" }
        val withoutDeadline = loaded.copy(deadline = null, reminderRule = ReminderRule.DISABLED, scheduleToken = null, updatedAt = 2000L)
        v2Store.saveTodoLocally(withoutDeadline)

        // loadTodos MUST return null deadline, not resurrect from base
        val reloaded = v2Store.loadTodos().first { it.id == "todo-v2-clear-base" }
        assertNull(reloaded.deadline, "Deadline must not be resurrected from base")
        assertNull(reloaded.scheduleToken, "Schedule token must be null when deadline cleared")
        assertFalse(reloaded.reminderRule.enabled, "Reminder rule must be disabled")

        // Switch from timed to date-only
        val dateOnly = TaskDeadline(
            date = "2026-10-15",
            time = null,
            zone = "UTC",
            offsetSeconds = 0L,
            reminderAnchorTime = "09:00",
            anchorEpochMs = 90000L,
            overdueEpochMs = 186400000L
        )
        val dateOnlyTodo = reloaded.copy(deadline = dateOnly, scheduleToken = "tok-do", updatedAt = 3000L)
        v2Store.saveTodoLocally(dateOnlyTodo)

        val reloadedDateOnly = v2Store.loadTodos().first { it.id == "todo-v2-clear-base" }
        assertNotNull(reloadedDateOnly.deadline)
        assertNull(reloadedDateOnly.deadline?.time, "Time must be null for date-only task")
        assertEquals("09:00", reloadedDateOnly.deadline?.reminderAnchorTime)
    }

    @Test
    fun testTombstoneMutationForbidden() = runTest {
        val now = System.currentTimeMillis()
        // Seed server base with confirmed deleted tombstone
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "tombstone-row-1",
                serverRevision = 10L,
                deleted = true,
                title = "",
                completed = false,
                createdAt = now - 1000,
                updatedAt = now,
                epoch = "epoch-1"
            )
        )
        val result = runCatching {
            store.saveTodoLocally(
                Todo(
                    id = "tombstone-row-1",
                    title = "Attempted Resurrection",
                    completed = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun testLocalSettingsAndLedgerOperationsAndWipe() = runTest {
        // Settings roundtrip
        val defaultSettings = store.getLocalSettings()
        assertFalse(defaultSettings.notificationsEnabled)
        assertEquals(0L, defaultSettings.defaultReminderLeadMinutes)

        val updatedSettings = defaultSettings.copy(
            notificationsEnabled = true,
            defaultReminderEnabled = true,
            defaultReminderLeadMinutes = 15L
        )
        store.updateLocalSettings(updatedSettings)
        val retrievedSettings = store.getLocalSettings()
        assertTrue(retrievedSettings.notificationsEnabled)
        assertTrue(retrievedSettings.defaultReminderEnabled)
        assertEquals(15L, retrievedSettings.defaultReminderLeadMinutes)

        // Ledger roundtrip
        val entry = ReminderDeliveryLedgerEntity(
            accountKey = accountKey.toString(),
            generation = 1L,
            taskId = "task-ledger-1",
            scheduleToken = "tok-ledger-1",
            occurrenceIndex = 0,
            dueEpochMs = 1792000000000L,
            claimOutcome = ReminderDeliveryLedgerEntity.OUTCOME_PENDING,
            cancellationState = ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        store.upsertLedgerEntry(entry)
        val entries = store.getLedgerEntriesForTask(1L, "task-ledger-1")
        assertEquals(1, entries.size)
        assertEquals("tok-ledger-1", entries[0].scheduleToken)

        // Cancel ledger entries
        store.cancelLedgerEntriesForTask(1L, "task-ledger-1")
        val cancelledEntries = store.getLedgerEntriesForTask(1L, "task-ledger-1")
        assertEquals(1, cancelledEntries.size)
        assertEquals(ReminderDeliveryLedgerEntity.CANCELLATION_CANCELLED, cancelledEntries[0].cancellationState)

        // Wipe local data
        store.wipeLocalData()
        assertEquals(0, store.dao.countLedgerEntries())
        assertEquals(0, store.dao.countDrafts())
        assertEquals(0, store.dao.countOutbox())
        assertEquals(0, store.dao.countServerBase())
        assertNull(store.dao.getLocalSettings())
    }

    @Test
    fun testV1AckPreservesV2DeadlineFieldsOnServerBase() = runTest {
        val now = System.currentTimeMillis()
        // Seed server base with confirmed v2 task with deadline
        val initialV2Base = ServerBaseEntity(
            rowId = "todo-cross-version-1",
            serverRevision = 2L,
            deleted = false,
            title = "Existing V2 Title",
            completed = false,
            createdAt = now - 5000L,
            updatedAt = now - 1000L,
            epoch = "epoch-1",
            deadlineDate = "2026-10-15",
            deadlineTime = "15:00",
            deadlineZone = "Asia/Saigon",
            deadlineOffsetSeconds = 25200L,
            reminderAnchorTime = null,
            deadlineAnchorEpochMs = 1792051200000L,
            deadlineOverdueEpochMs = 1792051200000L,
            reminderEnabled = true,
            reminderLeadMinutes = 30L,
            reminderIntervalMinutes = 15L,
            reminderCount = 2L,
            scheduleToken = "existing-v2-token"
        )
        store.dao.upsertServerBase(initialV2Base)

        // Enqueue legacy v1 update (where deadline fields are null and contractVersion is v1)
        val v1Op = OutboxEntity(
            opId = "op-v1-ack-1",
            epoch = "epoch-1",
            rowId = "todo-cross-version-1",
            kind = "update",
            expectedRevision = 2L,
            title = "Updated By V1 Client",
            completed = true,
            createdAt = now - 5000L,
            digest = "v1-digest",
            localRevision = 10L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            createdAtEpochMs = now,
            contractVersion = OutboxEntity.CONTRACT_VERSION_V1,
            deadlineDate = null,
            scheduleToken = null
        )
        store.dao.upsertOutbox(v1Op)

        // Reconcile accepted at higher revision 3
        store.reconcileAccepted("op-v1-ack-1", 3L)

        // Verify server base updated title and completed, BUT preserved v2 deadline & token!
        val updatedBase = store.dao.getServerBase("todo-cross-version-1")
        assertNotNull(updatedBase)
        assertEquals(3L, updatedBase.serverRevision)
        assertEquals("Updated By V1 Client", updatedBase.title)
        assertTrue(updatedBase.completed)
        assertEquals("2026-10-15", updatedBase.deadlineDate)
        assertEquals("15:00", updatedBase.deadlineTime)
        assertEquals("Asia/Saigon", updatedBase.deadlineZone)
        assertEquals("existing-v2-token", updatedBase.scheduleToken)
        assertTrue(updatedBase.reminderEnabled)
        assertEquals(30L, updatedBase.reminderLeadMinutes)

        // Enqueue delete operation
        val delOp = OutboxEntity(
            opId = "op-v1-del-1",
            epoch = "epoch-1",
            rowId = "todo-cross-version-1",
            kind = "delete",
            expectedRevision = 3L,
            title = null,
            completed = null,
            createdAt = null,
            digest = "v1-del-digest",
            localRevision = 11L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            createdAtEpochMs = now + 100L,
            contractVersion = OutboxEntity.CONTRACT_VERSION_V1
        )
        store.dao.upsertOutbox(delOp)

        // Reconcile delete accepted at revision 4 -> must clear deadline fields
        store.reconcileAccepted("op-v1-del-1", 4L)
        val tombBase = store.dao.getServerBase("todo-cross-version-1")
        assertNotNull(tombBase)
        assertTrue(tombBase.deleted)
        assertNull(tombBase.deadlineDate)
        assertNull(tombBase.scheduleToken)
        assertFalse(tombBase.reminderEnabled)
    }

    @Test
    fun testStaleAckPurgesOutboxAndRetainsNewerDraft() = runTest {
        val now = System.currentTimeMillis()
        // Server base is already at revision 5
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "todo-stale-1",
                serverRevision = 5L,
                deleted = false,
                title = "Confirmed Base",
                completed = false,
                createdAt = now,
                updatedAt = now,
                epoch = "epoch-1"
            )
        )
        // Local draft exists at localRevision 10
        store.dao.upsertDraft(
            EditorDraftEntity(
                rowId = "todo-stale-1",
                draftTitle = "Newer Local Draft",
                draftCompleted = true,
                localRevision = 10L,
                updatedAt = now + 2000L
            )
        )
        // Stale outbox op exists at localRevision 8
        store.dao.upsertOutbox(
            OutboxEntity(
                opId = "op-stale-ack-1",
                epoch = "epoch-1",
                rowId = "todo-stale-1",
                kind = "update",
                expectedRevision = 4L,
                title = "Stale Update",
                completed = false,
                createdAt = now,
                digest = "stale-digest",
                localRevision = 8L,
                deliveryState = OutboxEntity.STATE_DISPATCHING,
                createdAtEpochMs = now + 1000L
            )
        )
        // Stale ack arrives with serverRevision 4 < currentBase.serverRevision 5
        store.reconcileAccepted("op-stale-ack-1", 4L)

        // Stale outbox op must be purged atomically
        assertNull(store.dao.getOutboxEntry("op-stale-ack-1"))

        // Newer draft at localRevision 10 must NOT be deleted
        val draft = store.dao.getDraft("todo-stale-1")
        assertNotNull(draft)
        assertEquals("Newer Local Draft", draft.draftTitle)

        // Base must remain at revision 5
        assertEquals(5L, store.dao.getServerBase("todo-stale-1")?.serverRevision)
    }

    @Test
    fun testScheduleTokenPersistedAndRotatedOnSemanticChange() = runTest {
        val v2Store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = database,
            defaultContractVersion = OutboxEntity.CONTRACT_VERSION_V2
        )
        val deadline = TaskDeadline(
            date = "2026-10-15",
            time = "10:00",
            zone = "UTC",
            offsetSeconds = 0L,
            anchorEpochMs = 1000L,
            overdueEpochMs = 1000L
        )
        val initialTodo = Todo(
            id = "todo-auto-token-gen",
            title = "Initial Schedule Token",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            deadline = deadline,
            scheduleToken = "initial-token-123"
        )
        v2Store.saveTodoLocally(initialTodo)
        val loaded = v2Store.loadTodos().first { it.id == "todo-auto-token-gen" }
        assertEquals("initial-token-123", loaded.scheduleToken)

        // Mutating deadline semantic properties (e.g. time shifted) must rotate scheduleToken
        val shiftedDeadline = deadline.copy(time = "11:00", anchorEpochMs = 4600000L, overdueEpochMs = 4600000L)
        val editedTodo = initialTodo.copy(deadline = shiftedDeadline, updatedAt = 2000L)
        v2Store.saveTodoLocally(editedTodo)
        val rotated = v2Store.loadTodos().first { it.id == "todo-auto-token-gen" }
        assertNotNull(rotated.scheduleToken)
        assertTrue(rotated.scheduleToken!!.isNotEmpty())
        assertTrue(rotated.scheduleToken != "initial-token-123", "scheduleToken must rotate on schedule change")
    }

    @Test
    fun testPreAckTitleEditCoalescesWithUnsentCreate() = runTest {
        val v2Store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-jvm-test",
            database = database,
            defaultContractVersion = OutboxEntity.CONTRACT_VERSION_V2
        )
        val initial = Todo(
            id = "todo-pre-ack-coalesce",
            title = "Unsent Create Initial",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        v2Store.saveTodoLocally(initial)
        assertEquals(1, v2Store.dao.countOutbox())
        val op1 = v2Store.dao.getOutboxByRowId("todo-pre-ack-coalesce").first()
        assertEquals("create", op1.kind)
        assertEquals("Unsent Create Initial", op1.title)

        // Edit title before any acknowledgement
        val edited = initial.copy(title = "Unsent Create Edited", updatedAt = 2000L)
        v2Store.saveTodoLocally(edited)

        // Coalesces into the same single pending create operation
        assertEquals(1, v2Store.dao.countOutbox())
        val op2 = v2Store.dao.getOutboxByRowId("todo-pre-ack-coalesce").first()
        assertEquals(op1.opId, op2.opId)
        assertEquals("create", op2.kind)
        assertEquals("Unsent Create Edited", op2.title)
    }

    @Test
    fun testDiscardRegressiveReconcileAtomic() = runTest {
        // Setup base at serverRevision 5
        database.tabbyRushDao().upsertServerBase(
            ServerBaseEntity(
                rowId = "regressive-row",
                serverRevision = 5L,
                deleted = false,
                title = "Confirmed Server Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1"
            )
        )

        // Setup draft and outbox at localRevision 2
        database.tabbyRushDao().upsertDraft(
            EditorDraftEntity(
                rowId = "regressive-row",
                draftTitle = "Stale Local Draft",
                draftCompleted = true,
                localRevision = 2L,
                updatedAt = 1500L
            )
        )
        database.tabbyRushDao().upsertOutbox(
            OutboxEntity(
                opId = "stale-op-1",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "regressive-row",
                kind = "update",
                expectedRevision = 3L,
                title = "Stale Local Draft",
                completed = true,
                createdAt = 1000L,
                digest = "fake-digest",
                localRevision = 2L,
                deliveryState = OutboxEntity.STATE_DISPATCHING,
                createdAtEpochMs = 1500L
            )
        )

        // Reconcile with regressive revision (4 < 5)
        store.reconcileAccepted("stale-op-1", serverRevision = 4L)

        // Confirmed base must NOT regress
        val base = database.tabbyRushDao().getServerBase("regressive-row")
        assertNotNull(base)
        assertEquals(5L, base.serverRevision)
        assertEquals("Confirmed Server Title", base.title)

        // Outbox entry and draft must be cleaned up atomically
        assertNull(database.tabbyRushDao().getOutboxEntry("stale-op-1"))
        assertNull(database.tabbyRushDao().getDraft("regressive-row"))
    }

    @Test
    fun testLegacyV1ReconcilePreservesRemoteV2DeadlineMetadata() = runTest {
        // Setup base at revision 2 with v2 temporal fields
        database.tabbyRushDao().upsertServerBase(
            ServerBaseEntity(
                rowId = "v1-preserve-row",
                serverRevision = 2L,
                deleted = false,
                title = "Base Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1",
                deadlineDate = "2026-11-01",
                deadlineTime = "14:00",
                deadlineZone = "America/New_York",
                deadlineOffsetSeconds = -18000L,
                reminderAnchorTime = null,
                deadlineAnchorEpochMs = 1793559600000L,
                deadlineOverdueEpochMs = 1793559600000L,
                reminderEnabled = true,
                reminderLeadMinutes = 30L,
                reminderCount = 1L,
                scheduleToken = "remote-token-123"
            )
        )

        // Enqueue legacy v1 update (where all temporal fields are null)
        database.tabbyRushDao().upsertOutbox(
            OutboxEntity(
                opId = "legacy-v1-op",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "v1-preserve-row",
                kind = "update",
                expectedRevision = 2L,
                title = "Updated Title From V1 Client",
                completed = true,
                createdAt = 1000L,
                digest = "fake-v1-digest",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_DISPATCHING,
                createdAtEpochMs = 1500L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V1,
                deadlineDate = null,
                deadlineTime = null,
                deadlineZone = null,
                deadlineOffsetSeconds = null,
                reminderAnchorTime = null,
                deadlineAnchorEpochMs = null,
                deadlineOverdueEpochMs = null,
                reminderEnabled = false,
                reminderLeadMinutes = 0L,
                reminderIntervalMinutes = null,
                reminderCount = 1L,
                scheduleToken = null
            )
        )

        // Reconcile accepted legacy v1 op
        store.reconcileAccepted("legacy-v1-op", serverRevision = 3L)

        val updated = database.tabbyRushDao().getServerBase("v1-preserve-row")
        assertNotNull(updated)
        assertEquals(3L, updated.serverRevision)
        assertEquals("Updated Title From V1 Client", updated.title)
        assertTrue(updated.completed)

        // Critical Invariant: Remote v2 deadline metadata must be completely preserved!
        assertEquals("2026-11-01", updated.deadlineDate)
        assertEquals("14:00", updated.deadlineTime)
        assertEquals("America/New_York", updated.deadlineZone)
        assertEquals(-18000L, updated.deadlineOffsetSeconds)
        assertEquals(1793559600000L, updated.deadlineAnchorEpochMs)
        assertEquals(1793559600000L, updated.deadlineOverdueEpochMs)
        assertTrue(updated.reminderEnabled)
        assertEquals(30L, updated.reminderLeadMinutes)
        assertEquals("remote-token-123", updated.scheduleToken)
    }

    @Test
    fun testV2TitleOnlyIntentPreservesRemoteDeadline() = runTest {
        database.tabbyRushDao().upsertServerBase(
            ServerBaseEntity(
                rowId = "v2-title-only-row",
                serverRevision = 2L,
                deleted = false,
                title = "Base Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1",
                deadlineDate = "2026-12-25",
                deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L,
                reminderAnchorTime = "09:00",
                deadlineAnchorEpochMs = 1798189200000L,
                deadlineOverdueEpochMs = 1798243200000L,
                reminderEnabled = true,
                reminderLeadMinutes = 60L,
                scheduleToken = "xmas-token"
            )
        )

        database.tabbyRushDao().upsertOutbox(
            OutboxEntity(
                opId = "v2-title-op",
                epoch = "epoch-1",
                tableName = "todos",
                rowId = "v2-title-only-row",
                kind = "update",
                expectedRevision = 2L,
                title = "Edited Title Only",
                completed = false,
                createdAt = 1000L,
                digest = "fake-v2-digest",
                localRevision = 1L,
                deliveryState = OutboxEntity.STATE_DISPATCHING,
                createdAtEpochMs = 1500L,
                contractVersion = OutboxEntity.CONTRACT_VERSION_V2,
                intentMask = OutboxEntity.INTENT_TITLE
            )
        )

        store.reconcileAccepted("v2-title-op", serverRevision = 3L)

        val updated = database.tabbyRushDao().getServerBase("v2-title-only-row")
        assertNotNull(updated)
        assertEquals("Edited Title Only", updated.title)
        assertEquals("2026-12-25", updated.deadlineDate)
        assertEquals("xmas-token", updated.scheduleToken)
        assertTrue(updated.reminderEnabled)
    }

    @Test
    fun testDeleteTodoLocallySelectsV2ContractWhenReadyV2() = runTest {
        // Transition store to ready-v2
        database.tabbyRushDao().upsertMeta(
            AccountSyncMetaEntity(
                activeEpoch = "epoch-test",
                syncStatus = "ready-v2"
            )
        )

        database.tabbyRushDao().upsertServerBase(
            ServerBaseEntity(
                rowId = "delete-v2-row",
                serverRevision = 4L,
                deleted = false,
                title = "To Delete",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-test"
            )
        )

        store.deleteTodoLocally("delete-v2-row")

        val ops = database.tabbyRushDao().getOutboxByRowId("delete-v2-row")
        assertEquals(1, ops.size)
        val deleteOp = ops[0]
        assertEquals(OutboxEntity.CONTRACT_VERSION_V2, deleteOp.contractVersion)
        assertEquals("delete", deleteOp.kind)
        assertEquals(4L, deleteOp.expectedRevision)

        val expectedV2Digest = StrictSyncV2Digest.computeDigest(
            domain = StrictSyncV2Contract.PROTOCOL_VERSION,
            appId = "tabby-rush",
            userId = "user-jvm-test",
            epoch = "epoch-test",
            tableName = "todos",
            rowId = "delete-v2-row",
            opId = deleteOp.opId,
            kind = StrictOperationKind.DELETE,
            expectedRevision = 4L,
            data = null
        )
        assertEquals(expectedV2Digest, deleteOp.digest)
    }

    @Test
    fun testSetCompletedLocallyPreservesScheduleTokenAndV2Contract() = runTest {
        database.tabbyRushDao().upsertMeta(
            AccountSyncMetaEntity(
                id = "singleton",
                activeEpoch = "epoch-test",
                syncStatus = "ready-v2"
            )
        )

        val dl = TaskDeadline(
            date = "2026-10-25",
            time = "14:00",
            zone = "UTC",
            offsetSeconds = 0L,
            reminderAnchorTime = null,
            anchorEpochMs = 2_000_000L,
            overdueEpochMs = 2_000_000L
        )
        database.tabbyRushDao().upsertServerBase(
            ServerBaseEntity(
                rowId = "v2-complete-row",
                serverRevision = 3L,
                deleted = false,
                title = "V2 Task",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-test",
                deadlineDate = dl.date,
                deadlineTime = dl.time,
                deadlineZone = dl.zone,
                deadlineOffsetSeconds = dl.offsetSeconds,
                deadlineAnchorEpochMs = dl.anchorEpochMs,
                deadlineOverdueEpochMs = dl.overdueEpochMs,
                reminderEnabled = false,
                reminderLeadMinutes = 0L,
                reminderCount = 1L,
                scheduleToken = "token-preserved-123"
            )
        )

        val result = store.setCompletedLocally("v2-complete-row", completed = true, nowEpochMs = 5000L)
        assertEquals(CompletionChange.Changed, result)

        val ops = database.tabbyRushDao().getOutboxByRowId("v2-complete-row")
        assertEquals(1, ops.size)
        val op = ops[0]
        assertEquals(OutboxEntity.CONTRACT_VERSION_V2, op.contractVersion)
        assertEquals(true, op.completed)
        assertEquals("token-preserved-123", op.scheduleToken)
        assertTrue(op.intentMask?.contains(OutboxEntity.INTENT_COMPLETED) == true)

        val loaded = store.loadTodos().first { it.id == "v2-complete-row" }
        assertTrue(loaded.completed)
        assertEquals("token-preserved-123", loaded.scheduleToken)
    }

    @Test
    fun testSetCompletedLocallyUncompletingTask() = runTest {
        val now = 10_000L
        val todo = Todo(
            id = "t-uncomplete",
            title = "Completed task",
            completed = true,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            isPending = false
        )
        store.saveTodoLocally(todo)

        val result = store.setCompletedLocally("t-uncomplete", completed = false, nowEpochMs = now + 2000L)
        assertEquals(CompletionChange.Changed, result)

        val loaded = store.loadTodos().first { it.id == "t-uncomplete" }
        assertFalse(loaded.completed)
        assertEquals(now + 2000L, loaded.updatedAt)
    }

    @Test
    fun testCreateChildTaskRequiresReadyV3() = runTest {
        database.tabbyRushDao().upsertMeta(
            AccountSyncMetaEntity(
                id = AccountSyncMetaEntity.SINGLETON_ID,
                activeEpoch = "epoch-1",
                syncStatus = "ready-v2"
            )
        )

        val now = 10_000L
        val rootId = "10000000-0000-4000-8000-000000000001"
        val childId = "10000000-0000-4000-8000-000000000002"
        val root = Todo(
            id = rootId,
            title = "Root task",
            completed = false,
            createdAt = now
        )
        store.saveTodoLocally(root)

        val child = Todo(
            id = childId,
            title = "Child task",
            completed = false,
            createdAt = now + 100L,
            parentTaskId = rootId
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            store.saveTodoLocally(child)
        }
        assertTrue(ex.message?.contains("strict-v3") == true)
    }

    @Test
    fun testCreateChildTaskUnderExistingRootWhenReadyV3() = runTest {
        database.tabbyRushDao().upsertMeta(
            AccountSyncMetaEntity(
                id = AccountSyncMetaEntity.SINGLETON_ID,
                activeEpoch = "epoch-1",
                syncStatus = "ready-v3"
            )
        )

        val now = 10_000L
        val rootId = "20000000-0000-4000-8000-000000000001"
        val childId = "20000000-0000-4000-8000-000000000002"
        val root = Todo(
            id = rootId,
            title = "Root task title",
            completed = false,
            createdAt = now
        )
        store.saveTodoLocally(root)

        val child = Todo(
            id = childId,
            title = "Child task title",
            completed = false,
            createdAt = now + 100L,
            parentTaskId = rootId
        )
        store.saveTodoLocally(child)

        // Verify child draft & outbox
        val draft = database.tabbyRushDao().getDraft(childId)
        assertNotNull(draft)
        assertEquals(rootId, draft.parentTaskId)

        val ops = database.tabbyRushDao().getOutboxByRowId(childId)
        assertEquals(1, ops.size)
        val op = ops[0]
        assertEquals("strict-v3", op.contractVersion)
        assertEquals(rootId, op.parentTaskId)
        assertTrue(op.intentMask?.contains(OutboxEntity.INTENT_PARENT) == true)

        val loaded = store.loadTodos()
        assertEquals(2, loaded.size)
        val loadedChild = loaded.first { it.id == childId }
        assertEquals(rootId, loadedChild.parentTaskId)
    }

    @Test
    fun testRejectConvertingRootToChildOrReparenting() = runTest {
        database.tabbyRushDao().upsertMeta(
            AccountSyncMetaEntity(
                id = AccountSyncMetaEntity.SINGLETON_ID,
                activeEpoch = "epoch-1",
                syncStatus = "ready-v3"
            )
        )

        val now = 10_000L
        val rootId = "30000000-0000-4000-8000-000000000001"
        val childId = "30000000-0000-4000-8000-000000000002"
        val otherRootId = "30000000-0000-4000-8000-000000000009"
        val root = Todo(
            id = rootId,
            title = "Root 1",
            completed = false,
            createdAt = now
        )
        store.saveTodoLocally(root)

        val child = Todo(
            id = childId,
            title = "Child 1",
            completed = false,
            createdAt = now + 50L,
            parentTaskId = rootId
        )
        store.saveTodoLocally(child)

        // Try converting root-task-1 into a child -> must fail
        val convertedRoot = root.copy(parentTaskId = otherRootId)
        val rootEx = assertFailsWith<IllegalArgumentException> {
            store.saveTodoLocally(convertedRoot)
        }
        assertTrue(rootEx.message?.contains("Cannot convert existing root task into a child task") == true)

        // Try reparenting child to another parent -> must fail
        val reparentedChild = child.copy(parentTaskId = otherRootId)
        val reparentEx = assertFailsWith<IllegalArgumentException> {
            store.saveTodoLocally(reparentedChild)
        }
        assertTrue(reparentEx.message?.contains("Cannot reparent or detach child task") == true)

        // Try detaching child into a root -> must fail
        val detachedChild = child.copy(parentTaskId = null)
        val detachEx = assertFailsWith<IllegalArgumentException> {
            store.saveTodoLocally(detachedChild)
        }
        assertTrue(detachEx.message?.contains("Cannot reparent or detach child task") == true)
    }

    @Test
    fun testRejectNestingBeyondTwoLevels() = runTest {
        database.tabbyRushDao().upsertMeta(
            AccountSyncMetaEntity(
                id = AccountSyncMetaEntity.SINGLETON_ID,
                activeEpoch = "epoch-1",
                syncStatus = "ready-v3"
            )
        )

        val now = 10_000L
        val level0Id = "40000000-0000-4000-8000-000000000000"
        val level1Id = "40000000-0000-4000-8000-000000000001"
        val level2Id = "40000000-0000-4000-8000-000000000002"
        val root = Todo(id = level0Id, title = "Level 0 Root", completed = false, createdAt = now)
        store.saveTodoLocally(root)

        val child = Todo(id = level1Id, title = "Level 1 Child", completed = false, createdAt = now + 10L, parentTaskId = level0Id)
        store.saveTodoLocally(child)

        // Attempt to create a child under level-1 -> must fail
        val grandChild = Todo(id = level2Id, title = "Level 2 Grandchild", completed = false, createdAt = now + 20L, parentTaskId = level1Id)
        val ex = assertFailsWith<IllegalArgumentException> {
            store.saveTodoLocally(grandChild)
        }
        assertTrue(ex.message?.contains("cannot be a child (max 2 levels)") == true)
    }

    @Test
    fun testAutomaticParentCompletionRejection() = runTest {
        database.tabbyRushDao().upsertMeta(
            AccountSyncMetaEntity(
                id = AccountSyncMetaEntity.SINGLETON_ID,
                activeEpoch = "epoch-1",
                syncStatus = "ready-v3"
            )
        )

        val now = 10_000L
        val parentId = "50000000-0000-4000-8000-000000000001"
        val childId = "50000000-0000-4000-8000-000000000002"
        val root = Todo(id = parentId, title = "Auto Parent", completed = false, createdAt = now)
        store.saveTodoLocally(root)

        val child = Todo(id = childId, title = "Auto Child", completed = false, createdAt = now + 10L, parentTaskId = parentId)
        store.saveTodoLocally(child)

        // Calling setCompletedLocally on parent when child exists -> AutomaticParentRejected
        val result = store.setCompletedLocally(parentId, completed = true, nowEpochMs = now + 20L)
        assertEquals(CompletionChange.AutomaticParentRejected, result)

        // Child can be completed normally
        val childResult = store.setCompletedLocally(childId, completed = true, nowEpochMs = now + 30L)
        assertEquals(CompletionChange.Changed, childResult)
    }

    @Test
    fun testDeleteParentWithChildrenDoesNotUseSingleRowCancelShortcut() = runTest {
        database.tabbyRushDao().upsertMeta(
            AccountSyncMetaEntity(
                id = AccountSyncMetaEntity.SINGLETON_ID,
                activeEpoch = "epoch-1",
                syncStatus = "ready-v3"
            )
        )

        val now = 10_000L
        val rootId = "60000000-0000-4000-8000-000000000001"
        val childId = "60000000-0000-4000-8000-000000000002"
        val root = Todo(id = rootId, title = "Root to delete", completed = false, createdAt = now)
        store.saveTodoLocally(root)

        val child = Todo(id = childId, title = "Child of del", completed = false, createdAt = now + 10L, parentTaskId = rootId)
        store.saveTodoLocally(child)

        // Delete root
        store.deleteTodoLocally(rootId)

        // Should enqueue a DELETE operation in outbox, not cancelUnsentCreate
        val ops = database.tabbyRushDao().getOutboxByRowId(rootId)
        val deleteOp = ops.firstOrNull { it.kind == StrictOperationKind.DELETE.wireName }
        assertNotNull(deleteOp)
        assertEquals(OutboxEntity.CONTRACT_VERSION_V3, deleteOp.contractVersion)
    }
}
