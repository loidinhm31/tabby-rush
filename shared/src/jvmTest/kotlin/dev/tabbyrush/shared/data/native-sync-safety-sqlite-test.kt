package dev.tabbyrush.shared.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.tabbyrush.shared.auth.*
import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.sync.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Real Room transactions/restarts; HTTP boundaries use the existing deterministic transport engine. */
class NativeSyncSafetySqliteTest {
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var store: RoomAccountStore
    private val config = HubConfig("http://localhost:3100", "tabby-rush", "test-only-key", BuildMode.DEBUG)
    private val key = AccountKey.create(config.canonicalOrigin, "safety-user")
    private val stamp = "2026-10-20T00:00:00Z"

    @BeforeTest fun setup() {
        directory = Files.createTempDirectory("native-sync-safety").toFile()
        file = File(directory, "account.db")
        store = open()
    }
    private fun open() = RoomAccountStore(key, "safety-user", createJvmDatabaseBuilder(file).build(), config.appId)
    @AfterTest fun cleanup() { store.close(); directory.deleteRecursively() }
    private fun record(id: String, title: String = "Remote") =
        PullRecordDto("todos", id, TaskJsonMerge.encode(Todo(TaskRef("safety-user", id), title, false, 1000L)), 1L, stamp)
    private fun sql(statement: String) {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try { connection.execSQL(statement) } finally { connection.close() }
    }
    private suspend fun session(engine: TestHttpEngine): HubAuthSession {
        val vault = FakeCredentialVault()
        vault.store(key, CredentialBundle("access", "refresh"))
        store.initializeOwnerBinding(AccountIdentity("safety-user", "test", "test@example.com"))
        return HubAuthSession(config, vault, engine).also {
            it.restoreSession(AccountIdentity("safety-user", "test", "test@example.com"), key)
        }
    }
    private fun pullResponse(request: PullRequestDto, records: List<PullRecordDto>): String {
        val rows = if (request.accessChecks == null && request.checkpoint == null) records else emptyList()
        return syncWireJson.encodeToString(PullResponseDto(
            records = rows.map { it.copy(ownerUserId = "safety-user") },
            checkpoint = request.checkpoint ?: SyncCheckpointDto(stamp, rows.lastOrNull()?.rowId ?: ""),
            serverTimestamp = stamp,
            schemaVersion = 1L,
            collaborationVersion = 1L,
            accessRevision = 1L,
            accessResults = request.accessChecks?.map {
                TaskAccessResultDto(it, "accessible", TaskCapabilitiesDto(true, true, true, true), "full-group")
            }
        ))
    }

    @Test fun interruptedAndUnpromotedScansRestartFromNullBeforeAbsenceDecisions() = runTest {
        for (hasMore in listOf(true, false)) {
            val id = if (hasMore) "interrupted" else "unpromoted"
            store.dao.upsertServerBase(TaskJsonMerge.project(record(id)))
            store.saveTodoLocally(Todo(TaskRef("safety-user", id), "Retained edit", false, 1000L))
            val old = store.dao.beginScanAtomic().scanGeneration
            store.dao.commitPageAtomic(listOf(TaskJsonMerge.project(record(id))), stamp, id, 1L, old, hasMore, 1L)
            store.close(); store = open()
            var pulls = 0
            val engine = TestHttpEngine { request ->
                val pull = TaskJsonMerge.json.decodeFromString<PullRequestDto>((request.body as TextContent).text)
                assertNull(pull.checkpoint)
                if (pull.accessChecks == null) pulls++
                HttpStatusCode.OK to pullResponse(pull, emptyList())
            }
            val auth = session(engine)
            assertIs<ApiResult.Success<Unit>>(StagedFullScan(HubSyncClient(config, auth), store).executeScan())
            assertEquals(1, pulls)
            assertTrue(store.dao.getMeta()!!.scanGeneration > old)
            assertFalse(store.dao.getServerBase(id)!!.deleted, "Scan absence alone is not a tombstone")
            val reconciliation = OutboxReconciliation(store)
            val pending = reconciliation.getEligibleDispatchBatch().filter { it.rowId == id }
            assertEquals(1, reconciliation.prepareAndFreezeBatch(pending).size)
            assertEquals("Retained edit", store.dao.getDraft(id)?.draftTitle)
        }
    }

    @Test fun unknownLegacyReapplyRetainsEvidenceAndNeverInventsFieldIntent() = runTest {
        val remote = record("a").copy(data = JsonObject(record("a").data.jsonObject.toMutableMap().apply {
            put("completed", JsonPrimitive(true)); put("opaque", JsonPrimitive("keep"))
        }))
        val engine = TestHttpEngine { request ->
            val pull = TaskJsonMerge.json.decodeFromString<PullRequestDto>((request.body as TextContent).text)
            HttpStatusCode.OK to pullResponse(pull, listOf(remote))
        }
        val coordinator = AccountSyncCoordinator(store, session(engine))
        for (mask in listOf(null, "REQUIRES_REVIEW", "TITLE|UNKNOWN")) {
            store.dao.clearOutbox()
            val original = OutboxEntity("legacy", rowId = "a", kind = "update", title = "Old snapshot",
                bindingKey = boundBindingKey("safety-user"), ownerBinding = BINDING_BOUND, ownerUserId = "safety-user",
                completed = false, createdAt = 1000L, localRevision = 1L, deliveryState = OutboxEntity.STATE_REVIEW,
                createdAtEpochMs = 1000L, intentMask = mask, legacyEvidenceId = "legacy")
            store.dao.upsertOutbox(original)
            coordinator.reapplyConflict(TaskRef("safety-user", "a"))
            assertEquals(original, store.dao.getOutboxEntry("legacy"))
            assertEquals(1, store.dao.countOutbox())
            assertEquals(remote.data.toString(), store.dao.getServerBase("a")?.rawJson)
            assertIs<SafeError.Validation>(coordinator.progress.value.safeError)
            assertTrue(OutboxReconciliation(store).getEligibleDispatchBatch().isEmpty())
        }
    }

    @Test fun copyUsesLatestDisplayedDraftAndAtomicallyRetiresRepresentedSourceRevisions() = runTest {
        store.dao.upsertServerBase(TaskJsonMerge.project(record("a")))
        store.saveTodoLocally(Todo(TaskRef("safety-user", "a"), "Old frozen version", false, 1000L))
        val reconciliation = OutboxReconciliation(store)
        val head = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).single()
        store.saveTodoLocally(store.loadTodos().single().copy(title = "Latest displayed version", completed = true))
        store.dao.updateDeliveryState(head.opId, OutboxEntity.STATE_REVIEW, "Needs review")
        val coordinator = AccountSyncCoordinator(store, session(TestHttpEngine { error("Copy-as-new must stay local") }))
        val sourceOps = store.dao.getOutboxByRowId("a")
        val taskRef = TaskRef("safety-user", "a")
        sql("CREATE TRIGGER abort_copy BEFORE INSERT ON outbox WHEN NEW.resolution = 'EXPLICIT_COPY_AS_NEW' BEGIN SELECT RAISE(ABORT, 'copy interrupted'); END")
        assertFailsWith<Exception> { coordinator.copyAsNew(taskRef) }
        assertEquals(sourceOps, store.dao.getOutboxByRowId("a"))
        assertEquals(setOf("a"), store.loadAllTodos().map { it.id }.toSet())
        sql("DROP TRIGGER abort_copy")
        coordinator.copyAsNew(taskRef)
        store.close(); store = open()
        val copy = store.loadTodos().single { it.id != "a" }
        assertEquals("Latest displayed version", copy.title)
        assertTrue(copy.completed)
        assertNull(copy.parentTaskId)
        assertTrue(store.dao.getOutboxByRowId("a").isEmpty())
        assertNull(store.dao.getDraft("a"))
        assertEquals("Remote", store.loadTodos().single { it.id == "a" }.title)
        for (op in sourceOps) assertEquals("EXPLICIT_COPY_AS_NEW", store.dao.getOutboxEntry(op.opId)?.resolution)
        store.saveTodoLocally(store.loadTodos().single { it.id == "a" }.copy(title = "Later independent edit"))
        assertEquals("Later independent edit", store.dao.getDraft("a")?.draftTitle)
    }

    @Test fun utf8BudgetSplitsLargeOpaqueRecordsBeforeFreezing() = runTest {
        for (id in listOf("a", "b")) {
            val raw = JsonObject(record(id).data.jsonObject.toMutableMap().apply { put("opaque", JsonPrimitive("界".repeat(48_000))) })
            store.dao.upsertServerBase(TaskJsonMerge.project(record(id).copy(data = raw)))
            store.saveTodoLocally(Todo(TaskRef("safety-user", id), "Local $id", false, 1000L))
        }
        val reconciliation = OutboxReconciliation(store)
        val pull = PullRequestDto(SyncCheckpointDto(stamp, "escaped-\"-界"))
        val first = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch(), pull).single()
        val pending = store.dao.getAllOutbox().single { it.opId != first.opId }
        assertEquals(OutboxEntity.STATE_PENDING, pending.deliveryState)
        assertNull(pending.envelopeJson)
        val body = syncWireJson.encodeToString(DeltaRequestDto(PushRequestDto(listOf(reconciliation.toDto(first))), pull))
        assertTrue(body.encodeToByteArray().size <= MAX_SYNC_BODY_BYTES)
        store.reconcileAccepted(first.opId, requireNotNull(first.version))
        assertEquals(pending.opId, reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch(), pull).single().opId)
    }

    @Test fun singleOversizedRecordIsKnownUnsentAndDraftSurvivesRestart() = runTest {
        val raw = JsonObject(record("a").data.jsonObject.toMutableMap().apply { put("opaque", JsonPrimitive("界".repeat(90_000))) })
        store.dao.upsertServerBase(TaskJsonMerge.project(record("a").copy(data = raw)))
        store.saveTodoLocally(Todo(TaskRef("safety-user", "a"), "Retained title", false, 1000L))
        val reconciliation = OutboxReconciliation(store)
        assertTrue(reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).isEmpty())
        store.close(); store = open()
        val op = store.dao.getOutboxByRowId("a").single()
        assertEquals(OutboxEntity.STATE_CONFLICT, op.deliveryState)
        assertEquals("NEVER_DISPATCHED", op.dispatchHistory)
        assertNull(op.envelopeJson)
        assertNull(op.version)
        assertEquals("Retained title", store.dao.getDraft("a")?.draftTitle)
        assertEquals(raw.toString(), store.dao.getServerBase("a")?.rawJson)
    }

    @Test fun copyRetiresOnlyTheSelectedCanonicalOwnerWithACollidingRowId() = runTest {
        val coordinator = AccountSyncCoordinator(store, session(TestHttpEngine { error("Copy must stay local") }))
        for (owner in listOf("alice", "bob")) {
            store.dao.upsertOutbox(OutboxEntity(
                opId = "review-$owner", bindingKey = boundBindingKey(owner), ownerBinding = BINDING_BOUND,
                ownerUserId = owner, rowId = "same-id", kind = "update", title = "Draft $owner",
                completed = false, createdAt = 1000L, localRevision = 1L,
                deliveryState = OutboxEntity.STATE_REVIEW, createdAtEpochMs = 1000L, intentMask = "TITLE"
            ))
        }
        val alice = store.dao.getOutboxEntry("review-alice")
        coordinator.copyAsNew(TaskRef("bob", "same-id"))
        assertEquals(alice, store.dao.getOutboxEntry("review-alice"))
        assertEquals("EXPLICIT_COPY_AS_NEW", store.dao.getOutboxEntry("review-bob")?.resolution)
        val copy = store.loadTodos().single { it.ownerUserId == "safety-user" }
        assertEquals("Draft bob", copy.title)
        assertNotEquals("same-id", copy.id)
        assertNull(copy.parentTaskRef)
    }

    @Test fun copyCannotCreateANewTaskFromUnavailableContent() = runTest {
        val coordinator = AccountSyncCoordinator(store, session(TestHttpEngine { error("Copy must stay local") }))
        store.dao.upsertOutbox(OutboxEntity(
            opId = "revoked-review", bindingKey = boundBindingKey("bob"), ownerBinding = BINDING_BOUND,
            ownerUserId = "bob", rowId = "revoked", kind = "update", title = "Revoked content",
            completed = false, createdAt = 1000L, localRevision = 1L,
            deliveryState = OutboxEntity.STATE_REVIEW, createdAtEpochMs = 1000L, intentMask = "TITLE"
        ))
        store.dao.upsertAccess(TaskAccessEntity(
            ownerUserId = "bob", rowId = "revoked", revision = 1L, status = TaskAccessStatus.Unavailable.name,
            editContent = false
        ))
        coordinator.copyAsNew(TaskRef("bob", "revoked"))
        assertTrue(store.loadTodos().isEmpty())
        assertEquals(listOf("revoked-review"), store.dao.getAllOutbox().map { it.opId })
    }

    @Test fun copyRetiresRepresentedAssignmentRevisionsAndPreservesLaterPending() = runTest {
        val coordinator = AccountSyncCoordinator(store, session(TestHttpEngine { error("Copy must stay local") }))
        store.dao.upsertServerBase(TaskJsonMerge.project(record("task-1")))
        val taskRef = TaskRef("safety-user", "task-1")
        val bindingKey = boundBindingKey("safety-user")

        store.dao.upsertOutbox(OutboxEntity(
            opId = "todo-rev-1", bindingKey = bindingKey, ownerBinding = BINDING_BOUND,
            ownerUserId = "safety-user", tableName = "todos", rowId = "task-1", kind = "update",
            title = "Task with assignment", completed = false, createdAt = 1000L, localRevision = 1L,
            deliveryState = OutboxEntity.STATE_REVIEW, createdAtEpochMs = 1000L, intentMask = "TITLE"
        ))
        store.dao.upsertOutbox(OutboxEntity(
            opId = "assign-rev-2", bindingKey = bindingKey, ownerBinding = BINDING_BOUND,
            ownerUserId = "safety-user", tableName = ASSIGNMENT_TABLE_NAME, rowId = "task-1", kind = "update",
            title = null, completed = null, createdAt = 1000L, localRevision = 2L,
            deliveryState = OutboxEntity.STATE_REVIEW, createdAtEpochMs = 1000L,
            payloadJson = """{"id":"task-1","taskId":"task-1","recipientUserId":"alice"}"""
        ))
        store.dao.upsertOutbox(OutboxEntity(
            opId = "assign-rev-3-later", bindingKey = bindingKey, ownerBinding = BINDING_BOUND,
            ownerUserId = "safety-user", tableName = ASSIGNMENT_TABLE_NAME, rowId = "task-1", kind = "update",
            title = null, completed = null, createdAt = 1000L, localRevision = 3L,
            deliveryState = OutboxEntity.STATE_PENDING, createdAtEpochMs = 1000L,
            payloadJson = """{"id":"task-1","taskId":"task-1","recipientUserId":"bob"}"""
        ))

        coordinator.copyAsNew(taskRef)

        assertEquals("EXPLICIT_COPY_AS_NEW", store.dao.getOutboxEntry("todo-rev-1")?.resolution)
        assertEquals("DISCARDED", store.dao.getOutboxEntry("todo-rev-1")?.deliveryState)
        assertEquals("EXPLICIT_COPY_AS_NEW", store.dao.getOutboxEntry("assign-rev-2")?.resolution)
        assertEquals("DISCARDED", store.dao.getOutboxEntry("assign-rev-2")?.deliveryState)

        val preserved = store.dao.getOutboxEntry("assign-rev-3-later")
        assertNotNull(preserved)
        assertEquals(OutboxEntity.STATE_PENDING, preserved.deliveryState)
        assertNull(preserved.resolution)
    }

    @Test fun discardConflictDiscardsBothTodosAndAssignmentReviewOps() = runTest {
        val coordinator = AccountSyncCoordinator(store, session(TestHttpEngine { error("Local only") }))
        val taskRef = TaskRef("safety-user", "task-conflict")
        val bindingKey = boundBindingKey("safety-user")

        store.dao.upsertOutbox(OutboxEntity(
            opId = "todo-discarded", bindingKey = bindingKey, ownerBinding = BINDING_BOUND,
            ownerUserId = "safety-user", tableName = "todos", rowId = "task-conflict", kind = "update",
            title = "Task", completed = false, createdAt = 1000L, localRevision = 1L,
            deliveryState = "DISCARDED", createdAtEpochMs = 1000L, resolution = "EXPLICIT_DISCARD"
        ))
        store.dao.upsertOutbox(OutboxEntity(
            opId = "assign-stuck-review", bindingKey = bindingKey, ownerBinding = BINDING_BOUND,
            ownerUserId = "safety-user", tableName = ASSIGNMENT_TABLE_NAME, rowId = "task-conflict", kind = "update",
            title = null, completed = null, createdAt = 1000L, localRevision = 2L,
            deliveryState = OutboxEntity.STATE_REVIEW, createdAtEpochMs = 1000L,
            errorMessage = "Missing update/delete is not a new record"
        ))

        coordinator.discardConflict(taskRef)

        assertEquals("DISCARDED", store.dao.getOutboxEntry("assign-stuck-review")?.deliveryState)
        assertEquals("EXPLICIT_DISCARD", store.dao.getOutboxEntry("assign-stuck-review")?.resolution)
    }

    @Test fun reapplyConflictReappliesAssignmentSlotAgainstLiveTask() = runTest {
        val engine = TestHttpEngine { request ->
            val pull = TaskJsonMerge.json.decodeFromString<PullRequestDto>((request.body as TextContent).text)
            HttpStatusCode.OK to pullResponse(pull, emptyList())
        }
        val coordinator = AccountSyncCoordinator(store, session(engine))
        val taskRef = TaskRef("safety-user", "task-live")
        val bindingKey = boundBindingKey("safety-user")

        store.dao.upsertServerBase(TaskJsonMerge.project(record("task-live").copy(ownerUserId = "safety-user")))
        store.dao.upsertOutbox(OutboxEntity(
            opId = "assign-review", bindingKey = bindingKey, ownerBinding = BINDING_BOUND,
            ownerUserId = "safety-user", tableName = ASSIGNMENT_TABLE_NAME, rowId = "task-live", kind = "update",
            title = null, completed = null, createdAt = 1000L, localRevision = 1L,
            deliveryState = OutboxEntity.STATE_REVIEW, createdAtEpochMs = 1000L,
            payloadJson = """{"id":"task-live","taskId":"task-live","recipientUserId":"alice"}"""
        ))

        coordinator.reapplyConflict(taskRef)

        val active = store.dao.getOutboxByRow(bindingKey, ASSIGNMENT_TABLE_NAME, "task-live")
        val reapplied = active.single { it.opId != "assign-review" }
        assertEquals(OutboxEntity.STATE_PENDING, reapplied.deliveryState)
        assertEquals("EXPLICIT_REAPPLY", reapplied.resolution)
        assertEquals(0L, reapplied.assumedServerVersion)
        assertEquals(1L, reapplied.version)
    }
}
