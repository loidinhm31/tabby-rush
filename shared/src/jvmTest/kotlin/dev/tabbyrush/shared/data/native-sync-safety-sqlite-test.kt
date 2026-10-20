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
        PullRecordDto("todos", id, TaskJsonMerge.encode(Todo(id, title, false, 1000L)), 1L, stamp)
    private fun sql(statement: String) {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try { connection.execSQL(statement) } finally { connection.close() }
    }
    private suspend fun session(engine: TestHttpEngine): HubAuthSession {
        val vault = FakeCredentialVault()
        vault.store(key, CredentialBundle("access", "refresh"))
        return HubAuthSession(config, vault, engine).also {
            it.restoreSession(AccountIdentity("safety-user", "test", "test@example.com"), key)
        }
    }

    @Test fun interruptedAndUnpromotedScansRestartFromNullBeforeAbsenceDecisions() = runTest {
        for (hasMore in listOf(true, false)) {
            val id = if (hasMore) "interrupted" else "unpromoted"
            store.dao.upsertServerBase(TaskJsonMerge.project(record(id)))
            store.saveTodoLocally(Todo(id, "Retained edit", false, 1000L))
            val old = store.dao.beginScanAtomic().scanGeneration
            store.dao.commitPageAtomic(listOf(TaskJsonMerge.project(record(id))), stamp, id, 1L, old, hasMore, 1L)
            store.close(); store = open()
            var pulls = 0
            val engine = TestHttpEngine { request ->
                val pull = TaskJsonMerge.json.decodeFromString<PullRequestDto>((request.body as TextContent).text)
                assertNull(pull.checkpoint)
                pulls++
                HttpStatusCode.OK to TaskJsonMerge.json.encodeToString(
                    PullResponseDto(emptyList(), SyncCheckpointDto(stamp, ""), stamp, false, 1L))
            }
            val auth = session(engine)
            assertIs<ApiResult.Success<Unit>>(StagedFullScan(HubSyncClient(config, auth), store).executeScan())
            assertEquals(1, pulls)
            assertTrue(store.dao.getMeta()!!.scanGeneration > old)
            assertTrue(store.dao.getServerBase(id)!!.deleted)
            val reconciliation = OutboxReconciliation(store)
            assertTrue(reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).isEmpty())
            assertEquals(OutboxEntity.STATE_REVIEW, store.dao.getOutboxByRowId(id).single().deliveryState)
            assertEquals("Retained edit", store.dao.getDraft(id)?.draftTitle)
        }
    }

    @Test fun unknownLegacyReapplyRetainsEvidenceAndNeverInventsFieldIntent() = runTest {
        val remote = record("a").copy(data = JsonObject(record("a").data.jsonObject.toMutableMap().apply {
            put("completed", JsonPrimitive(true)); put("opaque", JsonPrimitive("keep"))
        }))
        val engine = TestHttpEngine { request ->
            val pull = TaskJsonMerge.json.decodeFromString<PullRequestDto>((request.body as TextContent).text)
            HttpStatusCode.OK to TaskJsonMerge.json.encodeToString(PullResponseDto(
                if (pull.checkpoint == null) listOf(remote) else emptyList(), SyncCheckpointDto(stamp, "a"), stamp, false, 1L))
        }
        val coordinator = AccountSyncCoordinator(store, session(engine))
        for (mask in listOf(null, "REQUIRES_REVIEW", "TITLE|UNKNOWN")) {
            store.dao.clearOutbox()
            val original = OutboxEntity("legacy", rowId = "a", kind = "update", title = "Old snapshot",
                completed = false, createdAt = 1000L, localRevision = 1L, deliveryState = OutboxEntity.STATE_REVIEW,
                createdAtEpochMs = 1000L, intentMask = mask, legacyEvidenceId = "legacy")
            store.dao.upsertOutbox(original)
            coordinator.reapplyConflict("a")
            assertEquals(original, store.dao.getOutboxEntry("legacy"))
            assertEquals(1, store.dao.countOutbox())
            assertEquals(remote.data.toString(), store.dao.getServerBase("a")?.rawJson)
            assertIs<SafeError.Validation>(coordinator.progress.value.safeError)
            assertTrue(OutboxReconciliation(store).getEligibleDispatchBatch().isEmpty())
        }
    }

    @Test fun copyUsesLatestDisplayedDraftAndAtomicallyRetiresRepresentedSourceRevisions() = runTest {
        store.dao.upsertServerBase(TaskJsonMerge.project(record("a")))
        store.saveTodoLocally(Todo("a", "Old frozen version", false, 1000L))
        val reconciliation = OutboxReconciliation(store)
        val head = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).single()
        store.saveTodoLocally(store.loadTodos().single().copy(title = "Latest displayed version", completed = true))
        store.dao.updateDeliveryState(head.opId, OutboxEntity.STATE_REVIEW, "Needs review")
        val sourceOps = store.dao.getOutboxByRowId("a")
        sql("CREATE TRIGGER abort_copy BEFORE INSERT ON outbox WHEN NEW.resolution = 'EXPLICIT_COPY_AS_NEW' BEGIN SELECT RAISE(ABORT, 'copy interrupted'); END")
        assertFailsWith<Exception> { store.copyLatestConflictAsNew("a") }
        assertEquals(sourceOps, store.dao.getOutboxByRowId("a"))
        assertEquals(setOf("a"), store.loadAllTodos().map { it.id }.toSet())
        sql("DROP TRIGGER abort_copy")
        store.copyLatestConflictAsNew("a")
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
            store.saveTodoLocally(Todo(id, "Local $id", false, 1000L))
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
        store.saveTodoLocally(Todo("a", "Retained title", false, 1000L))
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
}
