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

class AdversarialSyncSqliteTest {
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var store: RoomAccountStore
    private val config = HubConfig("http://localhost:3100", "tabby-rush", "test-only-key", BuildMode.DEBUG)
    private val key = AccountKey.create(config.canonicalOrigin, "sqlite-user")
    private val stamp = "2026-10-20T00:00:00.123456789Z"
    @BeforeTest fun setup() {
        directory = Files.createTempDirectory("standard-adversarial").toFile(); file = File(directory, "account.db")
        store = open()
    }
    private fun open() = RoomAccountStore(key, "sqlite-user", createJvmDatabaseBuilder(file).build(), config.appId)
    @AfterTest fun cleanup() { store.close(); directory.deleteRecursively() }
    private fun record(id: String, title: String = "Remote", version: Long = 1L) = PullRecordDto("todos", id, TaskJsonMerge.encode(Todo(id, title, false, 1000L)), version, stamp)
    private fun sql(statement: String) {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try { connection.execSQL(statement) } finally { connection.close() }
    }
    private suspend fun session(engine: TestHttpEngine): HubAuthSession {
        val vault = FakeCredentialVault(); vault.store(key, CredentialBundle("access", "refresh"))
        return HubAuthSession(config, vault, engine).also { it.restoreSession(AccountIdentity("sqlite-user", "test", "test@example.com"), key) }
    }

    @Test fun pageAndCheckpointRollbackTogetherThenRestartFreshAfterDowntime() = runTest {
        val generation = store.dao.beginScanAtomic().scanGeneration
        sql("CREATE TRIGGER abort_scan BEFORE INSERT ON scan_staging WHEN NEW.rowId = 'b' BEGIN SELECT RAISE(ABORT, 'injected page failure'); END")
        assertFailsWith<Exception> {
            store.dao.commitPageAtomic(listOf(TaskJsonMerge.project(record("a")), TaskJsonMerge.project(record("b"))), stamp, "b", 2L, generation, true, 1L)
        }
        assertEquals(0, store.dao.countStagedRecords()); assertNull(store.dao.getMeta()?.scanCheckpointUpdatedAt)
        sql("DROP TRIGGER abort_scan")
        store.dao.commitPageAtomic(listOf(TaskJsonMerge.project(record("a"))), stamp, "a", 2L, generation, true, 1L)
        store.saveTodoLocally(Todo("local", "Concurrent scan edit", false, 1000L))
        store.close(); store = open()
        assertEquals(stamp, store.dao.getMeta()?.scanCheckpointUpdatedAt)
        assertEquals(1, store.dao.countStagedRecords()); assertEquals("Concurrent scan edit", store.loadTodos().single().title)
        val restarted = store.dao.beginScanAtomic().scanGeneration
        assertTrue(restarted > generation)
        assertNull(store.dao.getMeta()?.scanCheckpointUpdatedAt)
        assertEquals(0, store.dao.countStagedRecords())
        assertFailsWith<IllegalStateException> {
            store.dao.commitPageAtomic(emptyList(), stamp, "a", 3L, generation, false, 2L)
        }
        store.dao.commitPageAtomic(listOf(TaskJsonMerge.project(record("a")), TaskJsonMerge.project(record("b"))),
            stamp, "b", 3L, restarted, false, 2L)
        store.dao.promoteCompletedScanAtomic(restarted, 2L)
        assertEquals(setOf("a", "b", "local"), store.loadTodos().map { it.id }.toSet())
        assertEquals(stamp, store.dao.getMeta()?.checkpointUpdatedAt); assertEquals(3L, store.dao.getMeta()?.hubSchemaVersion)
        assertEquals(1, store.dao.countOutbox())
    }

    @Test fun incompleteScanCannotDeleteAndPromotionRollsBackAtomically() = runTest {
        store.dao.upsertServerBase(TaskJsonMerge.project(record("old")))
        val generation = store.dao.beginScanAtomic().scanGeneration
        store.dao.commitPageAtomic(listOf(TaskJsonMerge.project(record("new"))), stamp, "new", 1L, generation, true, 1L)
        assertFalse(store.dao.getServerBase("old")!!.deleted)
        assertFailsWith<IllegalStateException> { store.dao.promoteCompletedScanAtomic(generation, 1L) }
        store.dao.commitPageAtomic(emptyList(), stamp, "new", 1L, generation, false, 2L)
        sql("CREATE TRIGGER abort_promotion BEFORE INSERT ON account_sync_meta WHEN NEW.scanState = 'READY' BEGIN SELECT RAISE(ABORT, 'injected promotion failure'); END")
        assertFailsWith<Exception> { store.dao.promoteCompletedScanAtomic(generation, 2L) }
        assertFalse(store.dao.getServerBase("old")!!.deleted); assertNull(store.dao.getServerBase("new"))
        assertEquals("COMPLETE", store.dao.getMeta()?.scanState)
        sql("DROP TRIGGER abort_promotion")
        store.dao.promoteCompletedScanAtomic(generation, 2L)
        assertTrue(store.dao.getServerBase("old")!!.deleted); assertNotNull(store.dao.getServerBase("new"))
    }

    @Test fun opaqueJsonSurvivesFifoRebaseAndOlderAckCannotClearNewerEdit() = runTest {
        val raw = JsonObject(record("a").data.jsonObject.toMutableMap().apply { put("future", TaskJsonMerge.json.parseToJsonElement("""{"nested":[1,null,{"x":true}]}""")) })
        store.dao.upsertServerBase(TaskJsonMerge.project(record("a").copy(data = raw)))
        store.saveTodoLocally(Todo("a", "First local", false, 1000L))
        val reconciliation = OutboxReconciliation(store)
        val first = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).single()
        assertEquals(raw["future"], reconciliation.toDto(first).data["future"])
        store.saveTodoLocally(store.loadTodos().single().copy(title = "Second local"))
        assertTrue(reconciliation.getEligibleDispatchBatch().isEmpty())
        reconciliation.reconcilePushResults(listOf(first), PushResponseDto(1, emptyList(), syncedRecords = listOf(SyncedRecordDto("todos", "a", 2L)), serverTimestamp = stamp, schemaVersion = 2L))
        assertEquals("Second local", store.loadTodos().single().title)
        val second = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).single()
        assertEquals(3L, second.version); assertEquals(raw["future"], reconciliation.toDto(second).data["future"])
        store.reconcileAccepted(first.opId, 2L)
        assertEquals(OutboxEntity.STATE_DISPATCHING, store.dao.getOutboxEntry(second.opId)?.deliveryState)
    }

    @Test fun matchingLostDeliveryConvergesWithoutReceiptOrReplay() = runTest {
        store.saveTodoLocally(Todo("a", "Local", false, 1000L))
        val reconciliation = OutboxReconciliation(store)
        val frozen = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).single()
        reconciliation.markBatchUncertain(listOf(frozen), "lost response")
        val envelope = reconciliation.toDto(frozen)
        store.dao.upsertServerBase(TaskJsonMerge.project(PullRecordDto("todos", "a", envelope.data, envelope.version, stamp)))
        store.close(); store = open()
        OutboxReconciliation(store).reconcileUncertain()
        assertTrue(store.dao.getAllOutbox().isEmpty())
        assertEquals("DESIRED_STATE_CONVERGENCE_NOT_RECEIPT", store.dao.getOutboxEntry(frozen.opId)?.resolution)
        assertNull(store.dao.getDraft("a"))
    }

    @Test fun supersededUncertainAndMissingConfirmedNeverBecomeCreates() = runTest {
        store.dao.upsertServerBase(TaskJsonMerge.project(record("a")))
        store.saveTodoLocally(Todo("a", "Local", false, 1000L))
        val reconciliation = OutboxReconciliation(store)
        val frozen = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).single()
        reconciliation.markBatchUncertain(listOf(frozen), "lost response")
        val generation = store.dao.beginScanAtomic().scanGeneration
        store.dao.commitPageAtomic(emptyList(), stamp, "", 1L, generation, false, 1L)
        store.dao.promoteCompletedScanAtomic(generation, 1L)
        reconciliation.reconcileUncertain()
        assertEquals(OutboxEntity.STATE_REVIEW, store.dao.getAllOutbox().single().deliveryState)
        assertTrue(reconciliation.getEligibleDispatchBatch().isEmpty()); assertTrue(store.dao.getServerBase("a")!!.deleted)
        assertNotNull(store.dao.getDraft("a"))
    }

    @Test fun legacyTranslationRollsBackWithEvidenceAndIsRestartIdempotent() = runTest {
        val legacy = OutboxEntity("old", rowId = "a", kind = "update", title = "Intent", completed = false, createdAt = 1000L,
            localRevision = 1L, deliveryState = "REVIEW", createdAtEpochMs = 1000L, intentMask = "TITLE", legacyEvidenceId = "old")
        val provenance = buildJsonObject {
            put("opId", "old"); put("rowId", "a"); put("kind", "update")
            put("expectedRevision", 1L); put("contractVersion", "strict-v3")
            put("deliveryState", "PENDING"); put("consecutiveFailures", 0L); put("intentMask", "TITLE")
            put("transitionCheckpoint", JsonNull); put("dispatchHistory", """{"createdUnsent":true,"dispatchCount":0}""")
        }
        store.dao.upsertOutbox(legacy); store.dao.upsertLegacyEvidence(LegacySyncEvidenceEntity("old", "a", provenance.toString()))
        store.dao.upsertServerBase(TaskJsonMerge.project(record("a")))
        store.dao.upsertMeta(AccountSyncMetaEntity(scanState = "READY"))
        sql("CREATE TRIGGER abort_translation BEFORE INSERT ON legacy_sync_evidence WHEN NEW.outcome = 'TRANSLATED_INTENT_ONLY' BEGIN SELECT RAISE(ABORT, 'injected translation failure'); END")
        assertFailsWith<Exception> { LegacyStandardIntentMigration(store).translateAfterCompletedScan() }
        assertEquals("REVIEW", store.dao.getAllOutbox().single().deliveryState)
        assertEquals("PENDING_REVIEW", store.dao.getLegacyEvidence().single().outcome)
        sql("DROP TRIGGER abort_translation")
        LegacyStandardIntentMigration(store).translateAfterCompletedScan()
        assertEquals("PENDING", store.dao.getAllOutbox().single().deliveryState)
        store.close(); store = open()
        LegacyStandardIntentMigration(store).translateAfterCompletedScan()
        assertEquals(1, store.dao.getAllOutbox().size)
        assertEquals("old", store.dao.getLegacyEvidence().single().translatedOpId)
    }

    @Test fun coordinatorScansBeforeEachReconnectAndLostDeltaIsNotReplayed() = runTest {
        val remote = mutableListOf<PullRecordDto>()
        var deltaCalls = 0
        var fullPulls = 0
        val engine = TestHttpEngine { request ->
            assertFalse(request.url.encodedPath.contains("strong"))
            val body = (request.body as TextContent).text
            when {
                request.url.encodedPath.endsWith("/pull") -> {
                    val pull = TaskJsonMerge.json.decodeFromString<PullRequestDto>(body)
                    if (pull.checkpoint == null) fullPulls++
                    val rows = if (pull.checkpoint == null) remote.toList() else emptyList()
                    HttpStatusCode.OK to TaskJsonMerge.json.encodeToString(PullResponseDto(rows, SyncCheckpointDto(stamp, remote.lastOrNull()?.rowId ?: ""), stamp, false, 3L))
                }
                request.url.encodedPath.endsWith("/delta") -> {
                    deltaCalls++
                    val delta = TaskJsonMerge.json.decodeFromString<DeltaRequestDto>(body)
                    for (outgoing in delta.push!!.records) remote.add(PullRecordDto("todos", outgoing.rowId, outgoing.data, outgoing.version, stamp, outgoing.deleted))
                    HttpStatusCode.OK to "response deliberately lost after push"
                }
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        val session = session(engine)
        val coordinator = AccountSyncCoordinator(store, session)
        store.saveTodoLocally(Todo("a", "Local", false, 1000L))
        coordinator.triggerSync()
        assertEquals(1, deltaCalls); assertEquals("UNCERTAIN", store.dao.getAllOutbox().single().deliveryState)
        coordinator.triggerSync()
        assertEquals(1, deltaCalls); assertEquals(2, fullPulls)
        assertEquals(0, store.dao.countOutbox()); assertEquals(3L, store.dao.getMeta()?.hubSchemaVersion)
        assertEquals("Local", store.loadTodos().single().title)
    }

    @Test fun explicitReapplyMintsEnvelopeOverRefreshedOpaqueBaseOnlyChangedFields() = runTest {
        val anchor = StrictTimeZoneResolver.default.resolveInstant("UTC", "2026-10-20", "10:00")
        val deadline = TaskDeadline("2026-10-20", "10:00", "UTC", 0L, anchorEpochMs = anchor, overdueEpochMs = anchor)
        val raw = JsonObject(TaskJsonMerge.encode(Todo("a", "Remote", true, 1000L, deadline = deadline, scheduleToken = "remote-token"))
            .toMutableMap().apply { put("future", TaskJsonMerge.json.parseToJsonElement("""{"nested":[null,true]}""")) })
        val remote = PullRecordDto("todos", "a", raw, 5L, stamp)
        store.dao.upsertServerBase(TaskJsonMerge.project(record("a")))
        store.dao.upsertOutbox(OutboxEntity("conflict", rowId = "a", kind = "update", title = "Local title", completed = false,
            createdAt = 1000L, localRevision = 1L, deliveryState = "CONFLICT", createdAtEpochMs = 1000L, intentMask = "TITLE"))
        val engine = TestHttpEngine { request ->
            val pull = TaskJsonMerge.json.decodeFromString<PullRequestDto>((request.body as TextContent).text)
            HttpStatusCode.OK to TaskJsonMerge.json.encodeToString(PullResponseDto(
                if (pull.checkpoint == null) listOf(remote) else emptyList(), SyncCheckpointDto(stamp, "a"), stamp, false, 4L))
        }
        val coordinator = AccountSyncCoordinator(store, session(engine))
        coordinator.reapplyConflict("a")
        assertEquals("DISCARDED", store.dao.getOutboxEntry("conflict")?.deliveryState)
        val reconciliation = OutboxReconciliation(store)
        val fresh = reconciliation.prepareAndFreezeBatch(reconciliation.getEligibleDispatchBatch()).single()
        val envelope = reconciliation.toDto(fresh)
        assertEquals(6L, envelope.version); assertEquals(5L, envelope.assumedServerVersion)
        assertEquals(JsonPrimitive("Local title"), envelope.data["title"])
        assertEquals(JsonPrimitive(true), envelope.data["completed"])
        assertEquals(raw["future"], envelope.data["future"])
        assertEquals(raw["deadlineDate"], envelope.data["deadlineDate"])
        assertNotEquals("conflict", fresh.opId)
    }

    @Test fun explicitCopyOfUnresolvedChildCreatesNewRootWithoutReusingIdentity() = runTest {
        val parent = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val child = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        store.dao.upsertOutbox(OutboxEntity("review-child", rowId = child, kind = "update", title = "Retained child", completed = true,
            createdAt = 1000L, localRevision = 1L, deliveryState = "REVIEW", createdAtEpochMs = 1000L,
            intentMask = "TITLE", parentTaskId = parent))
        val engine = TestHttpEngine { error("Copy-as-new is a local decision; no network required") }
        AccountSyncCoordinator(store, session(engine)).copyAsNew(child)
        val copy = store.loadTodos().single()
        assertNotEquals(child, copy.id)
        assertNull(copy.parentTaskId)
        assertEquals("Retained child", copy.title)
        assertEquals("create", store.dao.getAllOutbox().single().kind)
        assertEquals("DISCARDED", store.dao.getOutboxEntry("review-child")?.deliveryState)
    }
}
