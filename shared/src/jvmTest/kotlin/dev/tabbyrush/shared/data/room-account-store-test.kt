package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.sync.*
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RoomAccountStoreTest {
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var store: RoomAccountStore
    private val key = AccountKey.create("http://localhost:3100", "sqlite-user")
    private val parent = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val child = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    @BeforeTest fun setup() {
        directory = Files.createTempDirectory("standard-room-store").toFile()
        file = File(directory, "account.db")
        store = open()
    }
    private fun open() = RoomAccountStore(key, "sqlite-user", createJvmDatabaseBuilder(file).build(), "tabby-rush")
    @AfterTest fun cleanup() { store.close(); directory.deleteRecursively() }
    private fun todo(id: String = parent, completed: Boolean = false, parentId: String? = null) = Todo(id, "Task $id", completed, 1000L, parentTaskId = parentId)
    private suspend fun confirm(id: String = parent) {
        val reconciliation = OutboxReconciliation(store)
        val batch = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch().filter { it.rowId == id })
        assertEquals(1, batch.size)
        reconciliation.reconcilePushResults(batch, PushResponseDto(1, emptyList(), syncedRecords = batch.map { SyncedRecordDto("todos", it.rowId, requireNotNull(it.version)) }, serverTimestamp = "2026-10-20T00:00:00Z", schemaVersion = 1L))
    }

    @Test fun localDraftAndIntentSurviveRestartWithoutNetwork() = runTest {
        store.saveTodoLocally(todo())
        val before = store.dao.getAllOutbox().single()
        assertNull(before.envelopeJson)
        store.close(); store = open()
        assertEquals(before, store.dao.getAllOutbox().single())
        assertEquals(todo().title, store.loadTodos().single().title)
        assertTrue(store.loadTodos().single().isPending)
    }

    @Test fun neverDispatchedCreateDeleteCancelsOnlyLocalIntent() = runTest {
        store.saveTodoLocally(todo()); store.deleteTodoLocally(parent)
        assertEquals(0, store.dao.countOutbox()); assertEquals(0, store.dao.countDrafts())
        assertTrue(store.loadTodos().isEmpty())
    }

    @Test fun freezeIsDurableAndInterruptedDispatchBecomesUncertain() = runTest {
        store.saveTodoLocally(todo())
        val batch = OutboxReconciliation(store).prepareAndFreezeBatch(store.dao.getAllOutbox())
        val envelope = batch.single().envelopeJson
        store.close(); store = open()
        assertEquals(1, store.recoverInterruptedDispatches())
        assertEquals(envelope, store.dao.getAllOutbox().single().envelopeJson)
        assertTrue(OutboxReconciliation(store).getEligibleDispatchBatch().isEmpty())
    }

    @Test fun explicitAckAdvancesBaseAndOnlyCapturedRevision() = runTest {
        store.saveTodoLocally(todo())
        val reconciliation = OutboxReconciliation(store)
        val frozen = reconciliation.prepareAndFreezeBatch(store.dao.getAllOutbox())
        store.saveTodoLocally(todo().copy(title = "Newer edit", updatedAt = 2000L))
        val newer = store.dao.getDraft(parent)!!
        reconciliation.reconcilePushResults(frozen, PushResponseDto(1, emptyList(), syncedRecords = listOf(SyncedRecordDto("todos", parent, 1L)), serverTimestamp = "2026-10-20T00:00:00Z", schemaVersion = 2L))
        assertEquals(1L, store.dao.getServerBase(parent)?.serverRevision)
        assertEquals(newer, store.dao.getDraft(parent))
        assertEquals("Newer edit", store.loadTodos().single().title)
        assertEquals(1, store.dao.getAllOutbox().size)
        assertEquals("ACKNOWLEDGED", store.dao.getOutboxEntry(frozen.single().opId)?.deliveryState)
        val next = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).single()
        assertEquals(1L, next.assumedServerVersion); assertEquals(2L, next.version)
    }

    @Test fun unexpectedAckAndAggregateSyncedNeverClearIntent() = runTest {
        store.saveTodoLocally(todo())
        val reconciliation = OutboxReconciliation(store)
        val frozen = reconciliation.prepareAndFreezeBatch(store.dao.getAllOutbox())
        reconciliation.reconcilePushResults(frozen, PushResponseDto(99, emptyList(), syncedRecords = listOf(SyncedRecordDto("other", parent, 1L), SyncedRecordDto("todos", parent, 2L)), serverTimestamp = "2026-10-20T00:00:00Z", schemaVersion = 1L))
        assertEquals(OutboxEntity.STATE_UNCERTAIN, store.dao.getAllOutbox().single().deliveryState)
        assertNotNull(store.dao.getDraft(parent)); assertNull(store.dao.getServerBase(parent))
    }

    @Test fun confirmedDeleteNeverAllowsSameIdentityUpsert() = runTest {
        store.saveTodoLocally(todo()); confirm()
        store.deleteTodoLocally(parent); confirm()
        assertTrue(store.dao.getServerBase(parent)!!.deleted)
        assertFailsWith<IllegalStateException> { store.saveTodoLocally(todo()) }
    }

    @Test fun childWaitsForConfirmedLiveRootAndParentCompletionIsDerived() = runTest {
        store.saveTodoLocally(todo())
        store.saveTodoLocally(todo(child, true, parent))
        val reconciliation = OutboxReconciliation(store)
        val first = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch())
        assertEquals(listOf(parent), first.map { it.rowId })
        store.reconcileAccepted(first.single().opId, 1L)
        assertEquals(listOf(child), reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).map { it.rowId })
        assertEquals(CompletionChange.AutomaticParentRejected, store.setCompletedLocally(parent, true, 2000L))
        val hierarchy = TaskHierarchyProjector.projectHierarchy(store.loadTodos(), TodoFilter.ALL)
        assertTrue(hierarchy.items.first { it.todo.id == parent }.effectiveCompleted)
    }

    @Test fun reparentGrandchildAndSelfReferenceRejected() = runTest {
        store.saveTodoLocally(todo()); store.saveTodoLocally(todo(child, parentId = parent))
        assertFailsWith<IllegalArgumentException> { store.saveTodoLocally(todo(child)) }
        assertFailsWith<IllegalArgumentException> { store.saveTodoLocally(todo("cccccccc-cccc-4ccc-8ccc-cccccccccccc", parentId = child)) }
        assertFailsWith<IllegalArgumentException> { todo(child, parentId = child) }
    }

    @Test fun unsentGroupDeletionIsAtomicLocalCancellation() = runTest {
        store.saveTodoLocally(todo()); store.saveTodoLocally(todo(child, parentId = parent))
        store.deleteTodoLocally(parent)
        assertTrue(store.loadTodos().isEmpty()); assertEquals(0, store.dao.countOutbox())
        assertEquals(0, store.dao.countDrafts())
    }

    @Test fun lastChildDeletionCapturesPrecedingEffectiveCompletion() = runTest {
        store.saveTodoLocally(todo()); confirm()
        store.saveTodoLocally(todo(child, true, parent)); confirm(child)
        store.deleteTodoLocally(child)
        assertTrue(store.loadTodos().single().completed)
        val parentOp = store.dao.getOutboxByRowId(parent).single()
        assertTrue(parentOp.intentMask!!.contains(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION))
        assertTrue(parentOp.completed == true)
        assertEquals("delete", store.dao.getOutboxByRowId(child).single().kind)
        assertTrue(store.getPersistedScheduleProjection().first { it.taskId == parent }.hasUnresolvedPreservationBarrier)
    }

    @Test fun groupDeletionCreatesIndependentFrozenRowsAndHidesGroup() = runTest {
        store.saveTodoLocally(todo()); confirm(); store.saveTodoLocally(todo(child, parentId = parent)); confirm(child)
        store.deleteTodoLocally(parent)
        assertTrue(store.loadTodos().isEmpty())
        assertEquals(setOf(parent, child), store.dao.getAllOutbox().map { it.rowId }.toSet())
        assertTrue(store.dao.getAllOutbox().all { it.kind == "delete" })
        assertTrue(store.getPersistedScheduleProjection().all { it.isDeleted })
    }

    @Test fun deletedParentSuppressesSurvivingChildWhileUnknownParentEntersRecovery() = runTest {
        store.saveTodoLocally(todo()); confirm(); store.saveTodoLocally(todo(child, parentId = parent)); confirm(child)
        store.dao.upsertServerBase(store.dao.getServerBase(parent)!!.copy(deleted = true))
        assertTrue(store.loadTodos().isEmpty())
        assertEquals(listOf(child), store.loadAllTodos(includeDeleted = false).map { it.id })
        assertTrue(store.getPersistedScheduleProjection().first { it.taskId == child }.isDeleted)
        store.dao.deleteServerBase(parent)
        val recovered = TaskHierarchyProjector.projectHierarchy(store.loadTodos(), TodoFilter.ALL)
        assertEquals(listOf(child), recovered.unresolvedChildren.map { it.id })
        assertTrue(recovered.items.isEmpty())
        assertTrue(store.getPersistedScheduleProjection().first { it.taskId == child }.isDeleted)
    }

    @Test fun desiredStateCompletionRejectsMissingAndIsIdempotent() = runTest {
        assertEquals(CompletionChange.Missing, store.setCompletedLocally(parent, true, 2000L))
        store.saveTodoLocally(todo())
        val counter = store.dao.getMeta()!!.localRevisionCounter
        assertEquals(CompletionChange.Unchanged, store.setCompletedLocally(parent, false, 2000L))
        assertEquals(counter, store.dao.getMeta()!!.localRevisionCounter)
        assertEquals(CompletionChange.Changed, store.setCompletedLocally(parent, true, 2000L))
        assertTrue(store.loadTodos().single().completed)
    }

    @Test fun deadlineOwnFieldsAndScheduleTokenRotationPersist() = runTest {
        val deadline = TaskDeadline("2026-10-20", "10:00", "UTC", 0L, anchorEpochMs = 100_000L, overdueEpochMs = 100_000L)
        store.saveTodoLocally(todo().copy(deadline = deadline, scheduleToken = "initial"))
        val initial = store.loadTodos().single()
        store.saveTodoLocally(initial.copy(title = "Title only", updatedAt = 2000L))
        assertEquals(initial.scheduleToken, store.loadTodos().single().scheduleToken)
        store.saveTodoLocally(store.loadTodos().single().copy(deadline = deadline.copy(time = "11:00", anchorEpochMs = 200_000L, overdueEpochMs = 200_000L)))
        assertNotEquals(initial.scheduleToken, store.loadTodos().single().scheduleToken)
        store.saveTodoLocally(todo(child, parentId = parent))
        assertNull(store.loadTodos().first { it.id == child }.deadline)
        store.close(); store = open()
        assertNull(store.loadTodos().first { it.id == child }.deadline)
    }

    @Test fun frozenAccountAndRevisionExhaustionFailWithoutErasingData() = runTest {
        store.saveTodoLocally(todo())
        store.isFrozen = true
        assertFailsWith<IllegalStateException> { store.saveTodoLocally(todo().copy(title = "Rejected")) }
        store.isFrozen = false
        store.dao.upsertMeta(store.dao.getMeta()!!.copy(localRevisionCounter = MAX_EXACT_VERSION))
        assertFailsWith<IllegalStateException> { store.saveTodoLocally(todo().copy(title = "Rejected")) }
        assertEquals(todo().title, store.loadTodos().single().title)
    }

    @Test fun settingsAndReminderLedgerSurviveRestartThenExplicitWipe() = runTest {
        val settings = AccountLocalSettingsEntity(notificationsEnabled = true)
        store.updateLocalSettings(settings)
        val ledger = ReminderDeliveryLedgerEntity(key.toString(), 4L, parent, "schedule", 0, 1000L,
            "POSTED", postedPlatformId = "notification", cancellationState = "ACTIVE", updatedAtEpochMs = 1000L)
        store.upsertLedgerEntry(ledger)
        store.close(); store = open()
        assertEquals(settings, store.getLocalSettings()); assertEquals(listOf(ledger), store.getLedgerEntriesForTask(4L, parent))
        store.wipeLocalData()
        assertEquals(0, store.dao.countLedgerEntries()); assertNull(store.dao.getLocalSettings()); assertNull(store.dao.getMeta())
        assertTrue(store.dao.getLegacyEvidence().isEmpty())
    }
}
