package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.ReminderRule
import dev.tabbyrush.shared.contract.StrictOperationKind
import dev.tabbyrush.shared.contract.StrictOperationStatus
import dev.tabbyrush.shared.contract.StrictSyncV2Contract
import dev.tabbyrush.shared.contract.StrictSyncV2Digest
import dev.tabbyrush.shared.contract.TaskDeadline
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.sync.OutboxReconciliation
import dev.tabbyrush.shared.sync.StrictOperationResultDto
import dev.tabbyrush.shared.sync.StrictPushResponseDto
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

class AdversarialSyncSqliteTest {

    private lateinit var tempDir: File
    private lateinit var accountFiles: JvmAccountFiles
    private lateinit var dbFile: File
    private lateinit var store: RoomAccountStore
    private lateinit var database: TabbyRushDatabase

    private val accountKey = AccountKey.create("http://localhost:3000", "user-adv-sqlite-test")

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("tabbyrush-adv-sqlite-test").toFile()
        accountFiles = JvmAccountFiles(tempDir)
        dbFile = accountFiles.getDatabaseFile(accountKey)
        database = createJvmDatabaseBuilder(dbFile).build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-adv-sqlite-test",
            database = database,
            appId = "tabby-rush"
        )
        kotlinx.coroutines.runBlocking {
            store.dao.upsertMeta(
                AccountSyncMetaEntity(
                    syncStatus = "ready-v2",
                    activeEpoch = "epoch-v2",
                    localRevisionCounter = 0L
                )
            )
        }
    }

    @AfterTest
    fun tearDown() {
        if (!store.isFrozen) {
            store.close()
        }
        tempDir.deleteRecursively()
    }

    private fun reopenDatabase() {
        store.close()
        database = createJvmDatabaseBuilder(dbFile).build()
        store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-adv-sqlite-test",
            database = database,
            appId = "tabby-rush"
        )
    }

    @Test
    fun testReopenAfterCrashPreservesExactTemporalPayloadAndOutbox() = runTest {
        val now = 1793500000000L
        val deadline = TaskDeadline(
            date = "2026-11-01",
            time = "14:00",
            zone = "America/New_York",
            offsetSeconds = -18000L,
            anchorEpochMs = 1793559600000L,
            overdueEpochMs = 1793559600000L
        )
        val rule = ReminderRule(enabled = true, leadMinutes = 15L, intervalMinutes = null, count = 1L)
        val todo = Todo(
            id = "task-crash-1",
            title = "Crash Resistance Task",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            deadline = deadline,
            reminderRule = rule,
            scheduleToken = "tok-initial-crash"
        )

        store.saveTodoLocally(todo)

        // Verify state before simulated crash
        val outboxBefore = store.dao.getOutboxByRowId("task-crash-1")
        assertEquals(1, outboxBefore.size)
        val op = outboxBefore[0]
        assertEquals("2026-11-01", op.deadlineDate)
        assertEquals("tok-initial-crash", op.scheduleToken)

        // Crash and reopen database from disk
        reopenDatabase()

        // Verify state restored cleanly from SQLite
        val loaded = store.loadTodos()
        assertEquals(1, loaded.size)
        val restored = loaded[0]
        assertEquals("task-crash-1", restored.id)
        assertEquals("Crash Resistance Task", restored.title)
        assertNotNull(restored.deadline)
        assertEquals("2026-11-01", restored.deadline?.date)
        assertEquals("America/New_York", restored.deadline?.zone)
        assertEquals(-18000L, restored.deadline?.offsetSeconds)
        assertEquals(1793559600000L, restored.deadline?.anchorEpochMs)
        assertTrue(restored.reminderRule.enabled)
        assertEquals(15L, restored.reminderRule.leadMinutes)
        assertEquals("tok-initial-crash", restored.scheduleToken)
        assertTrue(restored.isPending)

        val outboxAfter = store.dao.getOutboxByRowId("task-crash-1")
        assertEquals(1, outboxAfter.size)
        assertEquals(op.opId, outboxAfter[0].opId)
        assertEquals(op.localRevision, outboxAfter[0].localRevision)
        assertEquals(op.deadlineAnchorEpochMs, outboxAfter[0].deadlineAnchorEpochMs)
    }

    @Test
    fun testFrozenRetryRetainsExactIdentityDigestAndTemporalPayload() = runTest {
        val now = 1793500000000L
        val deadline = TaskDeadline(
            date = "2026-11-01",
            time = "14:00",
            zone = "America/New_York",
            offsetSeconds = -18000L,
            anchorEpochMs = 1793559600000L,
            overdueEpochMs = 1793559600000L
        )
        val rule = ReminderRule(enabled = true, leadMinutes = 15L, intervalMinutes = null, count = 1L)
        val todo = Todo(
            id = "task-retry-1",
            title = "Frozen Retry Invariance",
            completed = false,
            createdAt = now,
            updatedAt = now,
            serverRevision = 0L,
            deadline = deadline,
            reminderRule = rule,
            scheduleToken = "tok-freeze-retry"
        )
        store.saveTodoLocally(todo)

        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-v2-test")
        val eligible = reconciliation.getEligibleDispatchBatch()
        assertEquals(1, eligible.size)

        // Freeze batch
        val frozenBatch = reconciliation.prepareAndFreezeBatch(eligible)
        assertEquals(1, frozenBatch.size)
        val frozenOp = frozenBatch[0]

        // Snapshot all invariant properties
        val capturedOpId = frozenOp.opId
        val capturedDigest = frozenOp.digest
        val capturedContractVersion = frozenOp.contractVersion
        val capturedDate = frozenOp.deadlineDate
        val capturedTime = frozenOp.deadlineTime
        val capturedZone = frozenOp.deadlineZone
        val capturedOffset = frozenOp.deadlineOffsetSeconds
        val capturedAnchor = frozenOp.deadlineAnchorEpochMs
        val capturedOverdue = frozenOp.deadlineOverdueEpochMs
        val capturedRemEnabled = frozenOp.reminderEnabled
        val capturedRemLead = frozenOp.reminderLeadMinutes
        val capturedRemInterval = frozenOp.reminderIntervalMinutes
        val capturedRemCount = frozenOp.reminderCount
        val capturedToken = frozenOp.scheduleToken

        assertTrue(capturedDigest.isNotEmpty(), "Frozen operation must have computed digest")

        // Simulate network disconnect / timeout without server response
        // Reopen database to simulate restart while dispatching
        reopenDatabase()

        // Verify outbox entry in database remains in STATE_DISPATCHING or can be safely retried
        val inDb = store.dao.getOutboxEntry(capturedOpId)
        assertNotNull(inDb)
        assertEquals(OutboxEntity.STATE_DISPATCHING, inDb.deliveryState)

        // Invariant: Exact identity, digest, contract version, and all 12 temporal fields remain bit-for-bit unchanged
        assertEquals(capturedOpId, inDb.opId)
        assertEquals(capturedDigest, inDb.digest)
        assertEquals(capturedContractVersion, inDb.contractVersion)
        assertEquals(capturedDate, inDb.deadlineDate)
        assertEquals(capturedTime, inDb.deadlineTime)
        assertEquals(capturedZone, inDb.deadlineZone)
        assertEquals(capturedOffset, inDb.deadlineOffsetSeconds)
        assertEquals(capturedAnchor, inDb.deadlineAnchorEpochMs)
        assertEquals(capturedOverdue, inDb.deadlineOverdueEpochMs)
        assertEquals(capturedRemEnabled, inDb.reminderEnabled)
        assertEquals(capturedRemLead, inDb.reminderLeadMinutes)
        assertEquals(capturedRemInterval, inDb.reminderIntervalMinutes)
        assertEquals(capturedRemCount, inDb.reminderCount)
        assertEquals(capturedToken, inDb.scheduleToken)
    }

    @Test
    fun testIntentMaskedRebaseOfV1TitleOverRemoteV2Deadline() = runTest {
        // Setup existing server base with remote v2 deadline and reminder
        val baseEntity = ServerBaseEntity(
            rowId = "task-rebase-v2",
            serverRevision = 1L,
            deleted = false,
            title = "Remote V2 Task",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            epoch = "epoch-v2",
            deadlineDate = "2026-11-01",
            deadlineTime = "15:00",
            deadlineZone = "America/New_York",
            deadlineOffsetSeconds = -18000L,
            deadlineAnchorEpochMs = 1793563200000L,
            deadlineOverdueEpochMs = 1793563200000L,
            reminderEnabled = true,
            reminderLeadMinutes = 30L,
            reminderCount = 1L,
            scheduleToken = "tok-remote-v2"
        )
        store.dao.upsertServerBase(baseEntity)

        // Offline legacy client or partial edit only updates title
        val v1TitleOp = OutboxEntity(
            opId = "op-v1-title-edit",
            epoch = "epoch-v2",
            tableName = "todos",
            rowId = "task-rebase-v2",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Updated Title From Client",
            completed = null, // untouched
            createdAt = 1000L,
            digest = "v1-digest",
            localRevision = 2L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            createdAtEpochMs = 1100L,
            contractVersion = OutboxEntity.CONTRACT_VERSION_V1,
            intentMask = OutboxEntity.INTENT_TITLE
        )
        store.dao.upsertOutbox(v1TitleOp)

        // Push succeeds, reconciling accepted at revision 2
        store.reconcileAccepted(v1TitleOp.opId, serverRevision = 2L)

        // Verify updated base in SQLite
        val updatedBase = store.dao.getServerBase("task-rebase-v2")
        assertNotNull(updatedBase)
        assertEquals(2L, updatedBase.serverRevision)
        assertEquals("Updated Title From Client", updatedBase.title)

        // Critical Invariant: Remote v2 temporal fields MUST NOT be erased by v1 edit
        assertEquals("2026-11-01", updatedBase.deadlineDate)
        assertEquals("15:00", updatedBase.deadlineTime)
        assertEquals("America/New_York", updatedBase.deadlineZone)
        assertEquals(-18000L, updatedBase.deadlineOffsetSeconds)
        assertEquals(1793563200000L, updatedBase.deadlineAnchorEpochMs)
        assertTrue(updatedBase.reminderEnabled)
        assertEquals(30L, updatedBase.reminderLeadMinutes)
        assertEquals("tok-remote-v2", updatedBase.scheduleToken)

        // Verify loaded projection preserves full deadline and reminder
        val todos = store.loadTodos()
        assertEquals(1, todos.size)
        assertEquals("Updated Title From Client", todos[0].title)
        assertNotNull(todos[0].deadline)
        assertEquals("2026-11-01", todos[0].deadline?.date)
        assertTrue(todos[0].reminderRule.enabled)
        assertEquals("tok-remote-v2", todos[0].scheduleToken)
    }

    @Test
    fun testLocalDeadlineRemovalDoesNotResurrectBaseDeadline() = runTest {
        // Base task has a deadline
        val baseEntity = ServerBaseEntity(
            rowId = "task-clear-dl",
            serverRevision = 1L,
            deleted = false,
            title = "Task To Clear",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            epoch = "epoch-v2",
            deadlineDate = "2026-11-01",
            deadlineTime = "10:00",
            deadlineZone = "UTC",
            deadlineOffsetSeconds = 0L,
            deadlineAnchorEpochMs = 1793534400000L,
            deadlineOverdueEpochMs = 1793534400000L,
            reminderEnabled = true,
            reminderLeadMinutes = 15L,
            reminderCount = 1L,
            scheduleToken = "tok-before-clear"
        )
        store.dao.upsertServerBase(baseEntity)

        // User clears deadline locally
        val clearedTodo = Todo(
            id = "task-clear-dl",
            title = "Task To Clear",
            completed = false,
            createdAt = 1000L,
            updatedAt = 1200L,
            serverRevision = 1L,
            deadline = null,
            reminderRule = ReminderRule.DISABLED,
            scheduleToken = null
        )
        store.saveTodoLocally(clearedTodo)

        // Loaded projection MUST reflect cleared deadline immediately (no resurrection)
        val loaded = store.loadTodos()
        assertEquals(1, loaded.size)
        assertNull(loaded[0].deadline, "Cleared deadline must not be resurrected from base")
        assertFalse(loaded[0].reminderRule.enabled)
        assertNull(loaded[0].scheduleToken)
        assertTrue(loaded[0].isPending)

        // Verify outbox op records clear deadline intent
        val outbox = store.dao.getOutboxByRowId("task-clear-dl")
        assertEquals(1, outbox.size)
        assertNull(outbox[0].deadlineDate)
        assertNull(outbox[0].scheduleToken)
        assertTrue(outbox[0].intentMask!!.contains(OutboxEntity.INTENT_DEADLINE))
    }

    @Test
    fun testUnknownSupersededExplicitConflictLockPersistsAcrossRestart() = runTest {
        val op = OutboxEntity(
            opId = "op-superseded-sql",
            epoch = "epoch-1",
            tableName = "todos",
            rowId = "row-superseded-1",
            kind = StrictOperationKind.UPDATE.wireName,
            expectedRevision = 1L,
            title = "Conflicted Edit",
            completed = false,
            createdAt = 1000L,
            digest = "frozen-digest-1",
            localRevision = 2L,
            deliveryState = OutboxEntity.STATE_DISPATCHING,
            createdAtEpochMs = 1100L,
            contractVersion = OutboxEntity.CONTRACT_VERSION_V1,
            dispatchHistory = """{"dispatched":true,"uncertain":true}"""
        )
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-superseded-1",
                serverRevision = 1L,
                deleted = false,
                title = "Base Task Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1"
            )
        )
        store.dao.upsertOutbox(op)
        val reconciliation = OutboxReconciliation(store, activeEpoch = "epoch-1")
        val response = StrictPushResponseDto(
            protocol = "strict-v1",
            epoch = "epoch-1",
            results = listOf(
                StrictOperationResultDto(
                    opId = "op-superseded-sql",
                    rowId = "row-superseded-1",
                    digest = "frozen-digest-1",
                    status = StrictOperationStatus.REVISION_CONFLICT.wireName,
                    acceptedRevision = null
                )
            )
        )
        reconciliation.reconcilePushResults(listOf(op), response)

        // Verify locked in UNKNOWN_SUPERSEDED
        val marked = store.dao.getOutboxEntry("op-superseded-sql")
        assertNotNull(marked)
        assertEquals(OutboxEntity.STATE_UNKNOWN_SUPERSEDED, marked.deliveryState)

        // Close and reopen database
        reopenDatabase()

        // Reassert state after restart
        val restored = store.dao.getOutboxEntry("op-superseded-sql")
        assertNotNull(restored)
        assertEquals(OutboxEntity.STATE_UNKNOWN_SUPERSEDED, restored.deliveryState)
        assertEquals("frozen-digest-1", restored.digest) // NEVER rehashed
        assertEquals("op-superseded-sql", restored.opId) // NEVER re-ID'd

        // The operation is quarantined in UNKNOWN_SUPERSEDED and excluded from normal pending dispatch
        assertEquals(1, store.dao.countOutboxByState(OutboxEntity.STATE_UNKNOWN_SUPERSEDED))
        assertEquals(0, store.dao.countOutboxByState(OutboxEntity.STATE_PENDING))
        assertEquals(0, store.dao.countOutboxByState(OutboxEntity.STATE_DISPATCHING))

        // Other tasks can continue loading and syncing
        val todos = store.loadTodos()
        assertEquals(1, todos.size)
        assertFalse(todos[0].isPending, "Quarantined conflict is not marked as pending dispatch")
}
}
