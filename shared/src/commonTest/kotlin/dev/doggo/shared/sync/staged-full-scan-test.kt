package dev.doggo.shared.sync

import dev.doggo.shared.auth.FakeCredentialVault
import dev.doggo.shared.auth.HubAuthSession
import dev.doggo.shared.auth.TestHttpEngine
import dev.doggo.shared.contract.AccountIdentity
import dev.doggo.shared.contract.AccountKey
import dev.doggo.shared.contract.ApiResult
import dev.doggo.shared.contract.BuildMode
import dev.doggo.shared.contract.CredentialBundle
import dev.doggo.shared.contract.HubConfig
import dev.doggo.shared.contract.SafeError
import dev.doggo.shared.contract.StrictSyncV2Contract
import dev.doggo.shared.data.AccountSyncMetaEntity
import dev.doggo.shared.data.AccountLocalSettingsEntity
import dev.doggo.shared.data.DoggoDao
import dev.doggo.shared.data.EditorDraftEntity
import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.ReminderDeliveryLedgerEntity
import dev.doggo.shared.data.RoomAccountStore
import dev.doggo.shared.data.ScanStagingEntity
import dev.doggo.shared.data.ServerBaseEntity
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeDoggoDao : DoggoDao {
    val serverBase = mutableMapOf<String, ServerBaseEntity>()
    val outbox = mutableMapOf<String, OutboxEntity>()
    val drafts = mutableMapOf<String, EditorDraftEntity>()
    val staging = mutableMapOf<String, ScanStagingEntity>()
    var meta: AccountSyncMetaEntity? = null
    var localSettings: AccountLocalSettingsEntity? = null
    val ledger = mutableMapOf<String, ReminderDeliveryLedgerEntity>()

    override suspend fun getServerBase(rowId: String): ServerBaseEntity? = serverBase[rowId]
    override suspend fun getActiveServerBase(): List<ServerBaseEntity> = serverBase.values.filter { !it.deleted }
    override suspend fun getAllServerBase(): List<ServerBaseEntity> = serverBase.values.toList()
    override suspend fun upsertServerBase(entity: ServerBaseEntity) { serverBase[entity.rowId] = entity }
    override suspend fun upsertServerBase(entities: List<ServerBaseEntity>) { entities.forEach { serverBase[it.rowId] = it } }
    override suspend fun deleteServerBase(rowId: String) { serverBase.remove(rowId) }
    override suspend fun clearServerBase() { serverBase.clear() }
    override suspend fun countServerBase(): Int = serverBase.size

    override suspend fun getOutboxEntry(opId: String): OutboxEntity? = outbox[opId]
    override suspend fun getOutboxByRowId(rowId: String): List<OutboxEntity> =
        outbox.values.filter { it.rowId == rowId }.sortedBy { it.createdAtEpochMs }
    override suspend fun getAllOutbox(): List<OutboxEntity> =
        outbox.values.sortedBy { it.localRevision }
    override suspend fun upsertOutbox(entity: OutboxEntity) { outbox[entity.opId] = entity }
    override suspend fun upsertOutbox(entities: List<OutboxEntity>) { entities.forEach { outbox[it.opId] = it } }
    override suspend fun deleteOutbox(opId: String) { outbox.remove(opId) }
    override suspend fun deleteOutboxByRowId(rowId: String) {
        val toRemove = outbox.values.filter { it.rowId == rowId }.map { it.opId }
        toRemove.forEach { outbox.remove(it) }
    }
    override suspend fun clearOutbox() { outbox.clear() }
    override suspend fun countOutbox(): Int = outbox.size
    override suspend fun countOutboxByState(state: String): Int = outbox.values.count { it.deliveryState == state }
    override suspend fun countOutboxByVersion(version: String): Int = outbox.values.count { it.contractVersion == version }
    override suspend fun updateDeliveryState(opId: String, state: String, errorMessage: String?) {
        val op = outbox[opId]
        if (op != null) {
            outbox[opId] = op.copy(deliveryState = state, errorMessage = errorMessage)
        }
    }

    override suspend fun getDraft(rowId: String): EditorDraftEntity? = drafts[rowId]
    override suspend fun getAllDrafts(): List<EditorDraftEntity> = drafts.values.toList()
    override suspend fun upsertDraft(draft: EditorDraftEntity) { drafts[draft.rowId] = draft }
    override suspend fun deleteDraft(rowId: String) { drafts.remove(rowId) }
    override suspend fun clearDrafts() { drafts.clear() }
    override suspend fun countDrafts(): Int = drafts.size

    override suspend fun getStagedRecord(rowId: String): ScanStagingEntity? = staging[rowId]
    override suspend fun getAllStagedRecords(): List<ScanStagingEntity> = staging.values.toList()
    override suspend fun upsertStaging(entity: ScanStagingEntity) { staging[entity.rowId] = entity }
    override suspend fun upsertStaging(entities: List<ScanStagingEntity>) { entities.forEach { staging[it.rowId] = it } }
    override suspend fun clearStaging() { staging.clear() }
    override suspend fun countStagedRecords(): Int = staging.size

    override suspend fun getMeta(id: String): AccountSyncMetaEntity? = meta
    override suspend fun upsertMeta(meta: AccountSyncMetaEntity) { this.meta = meta }
    override suspend fun clearMeta() { this.meta = null }

    override suspend fun saveTodoAtomic(
        meta: AccountSyncMetaEntity,
        draft: EditorDraftEntity,
        outbox: OutboxEntity
    ) {
        upsertMeta(meta)
        upsertDraft(draft)
        upsertOutbox(outbox)
    }

    override suspend fun deleteTodoAtomic(
        meta: AccountSyncMetaEntity,
        draft: EditorDraftEntity,
        outbox: OutboxEntity
    ) {
        upsertMeta(meta)
        upsertDraft(draft)
        upsertOutbox(outbox)
    }

    override suspend fun cancelUnsentCreateAtomic(rowId: String) {
        deleteDraft(rowId)
        deleteOutboxByRowId(rowId)
    }

    override suspend fun reconcileAcceptedAtomic(
        serverBase: ServerBaseEntity,
        opId: String,
        draftRowId: String,
        clearDraft: Boolean
    ) {
        upsertServerBase(serverBase)
        deleteOutbox(opId)
        if (clearDraft) {
            deleteDraft(draftRowId)
        }
    }

    override suspend fun promoteStagingAtomic(basesToUpsert: List<ServerBaseEntity>) {
        upsertServerBase(basesToUpsert)
        clearStaging()
    }

    override suspend fun replaceOutboxAtomic(oldOpId: String?, newOp: OutboxEntity) {
        if (oldOpId != null) {
            deleteOutbox(oldOpId)
        }
        upsertOutbox(newOp)
    }

    override suspend fun discardConflictAtomic(opId: String, rowId: String, conflictLocalRev: Long) {
        deleteOutbox(opId)
        val draft = getDraft(rowId)
        if (draft != null && draft.localRevision <= conflictLocalRev) {
            deleteDraft(rowId)
        }
    }

    override suspend fun purgeStaleOutboxAtomic(opId: String, rowId: String, clearDraft: Boolean) {
        if (clearDraft) {
            deleteDraft(rowId)
        }
        deleteOutbox(opId)
    }

    override suspend fun freezeOutboxAtomic(
        opId: String,
        expectedLocalRevision: Long,
        newOp: OutboxEntity
    ): Boolean {
        val current = outbox[opId] ?: return false
        if (current.localRevision != expectedLocalRevision || current.deliveryState != OutboxEntity.STATE_PENDING) {
            return false
        }
        outbox[opId] = newOp
        return true
    }

    override suspend fun recoverInterruptedDispatches(): Int {
        var count = 0
        for ((opId, op) in outbox.toList()) {
            if (op.deliveryState == OutboxEntity.STATE_DISPATCHING) {
                outbox[opId] = op.copy(deliveryState = OutboxEntity.STATE_UNCERTAIN, errorMessage = "Interrupted dispatch recovered")
                count++
            }
        }
        return count
    }

    override suspend fun getLocalSettings(id: String): AccountLocalSettingsEntity? = localSettings
    override suspend fun upsertLocalSettings(settings: AccountLocalSettingsEntity) { this.localSettings = settings }
    override suspend fun clearLocalSettings() { this.localSettings = null }

    override suspend fun getLedgerEntry(
        accountKey: String,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): ReminderDeliveryLedgerEntity? {
        val key = "$accountKey:$generation:$taskId:$scheduleToken:$occurrenceIndex"
        return ledger[key]
    }

    override suspend fun getLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        taskId: String
    ): List<ReminderDeliveryLedgerEntity> =
        ledger.values.filter { it.accountKey == accountKey && it.generation == generation && it.taskId == taskId }
            .sortedBy { it.occurrenceIndex }

    override suspend fun getDueLedgerEntries(dueBeforeEpochMs: Long): List<ReminderDeliveryLedgerEntity> =
        ledger.values.filter { it.dueEpochMs <= dueBeforeEpochMs && it.claimOutcome == "PENDING" && it.cancellationState == "ACTIVE" }
            .sortedBy { it.dueEpochMs }

    override suspend fun getAllLedgerEntries(): List<ReminderDeliveryLedgerEntity> =
        ledger.values.sortedBy { it.dueEpochMs }

    override suspend fun upsertLedgerEntry(entry: ReminderDeliveryLedgerEntity) {
        val key = "${entry.accountKey}:${entry.generation}:${entry.taskId}:${entry.scheduleToken}:${entry.occurrenceIndex}"
        ledger[key] = entry
    }

    override suspend fun upsertLedgerEntries(entries: List<ReminderDeliveryLedgerEntity>) {
        entries.forEach { upsertLedgerEntry(it) }
    }

    override suspend fun cancelLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        taskId: String,
        updatedAtEpochMs: Long
    ) {
        for ((k, v) in ledger.toList()) {
            if (v.accountKey == accountKey && v.generation == generation && v.taskId == taskId && v.cancellationState == "ACTIVE") {
                ledger[k] = v.copy(cancellationState = "CANCELLED", updatedAtEpochMs = updatedAtEpochMs)
            }
        }
    }

    override suspend fun deleteLedgerForAccount(accountKey: String) {
        val toRemove = ledger.values.filter { it.accountKey == accountKey }.map {
            "${it.accountKey}:${it.generation}:${it.taskId}:${it.scheduleToken}:${it.occurrenceIndex}"
        }
        toRemove.forEach { ledger.remove(it) }
    }

    override suspend fun clearLedger() { ledger.clear() }
    override suspend fun countLedgerEntries(): Int = ledger.size

    override suspend fun wipeAllTablesAtomic() {
        clearDrafts()
        clearOutbox()
        clearStaging()
        clearServerBase()
        clearMeta()
        clearLocalSettings()
        clearLedger()
    }
}

class StagedFullScanTest {

    private val config = HubConfig(
        canonicalOrigin = "http://localhost:3000",
        appId = "doggo",
        appApiKey = "doggo-secret-key",
        buildMode = BuildMode.DEBUG
    )

    private val accountKey = AccountKey.create(config.canonicalOrigin, "user-123")
    private val identity = AccountIdentity(
        userId = "user-123",
        username = "tester",
        email = "tester@example.com"
    )

    private suspend fun createTestSetup(engine: TestHttpEngine): Pair<HubSyncClient, RoomAccountStore> {
        val vault = FakeCredentialVault()
        vault.store(
            accountKey,
            CredentialBundle(
                accessToken = "valid-access-token",
                refreshToken = "valid-refresh-token"
            )
        )
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(identity, accountKey)
        val client = HubSyncClient(config, session)
        val fakeDao = FakeDoggoDao()
        val store = RoomAccountStore(
            accountKey = accountKey,
            userId = "user-123",
            database = null,
            appId = "doggo",
            daoOverride = fakeDao
        )
        return Pair(client, store)
    }

    @Test
    fun testFullScanMultiPageSuccess() = runTest {
        var callIndex = 0
        val engine = TestHttpEngine { request ->
            callIndex++
            if (callIndex == 1) {
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "protocol":"strict-v1",
                        "epoch":"epoch-1",
                        "records":[
                            {"tableName":"todos","rowId":"row-1","revision":1,"deleted":false,"data":{"id":"row-1","title":"Todo 1","completed":false,"createdAt":1000}},
                            {"tableName":"todos","rowId":"row-2","revision":1,"deleted":false,"data":{"id":"row-2","title":"Todo 2","completed":true,"createdAt":2000}}
                        ],
                        "cursor":"cur-1",
                        "hasMore":true
                    }"""
                )
            } else {
                // Page 2: 1 record, hasMore = false
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "protocol":"strict-v1",
                        "epoch":"epoch-1",
                        "records":[
                            {"tableName":"todos","rowId":"row-3","revision":2,"deleted":true,"data":null}
                        ],
                        "cursor":"cur-2",
                        "hasMore":false
                    }"""
                )
            }
        }

        val (client, store) = createTestSetup(engine)
        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Success<Int>>(result)
        assertEquals(3, result.value)

        // Verify promoted to server_base
        val allBase = store.dao.getAllServerBase()
        assertEquals(3, allBase.size)
        val row1 = store.dao.getServerBase("row-1")
        assertNotNull(row1)
        assertEquals("Todo 1", row1.title)
        assertEquals(1L, row1.serverRevision)

        val row3 = store.dao.getServerBase("row-3")
        assertNotNull(row3)
        assertTrue(row3.deleted)
        assertEquals(2L, row3.serverRevision)

        // Verify staging table cleared
        assertEquals(0, store.dao.countStagedRecords())
    }

    @Test
    fun testCursorLoopDetectionAborts() = runTest {
        var callIndex = 0
        val engine = TestHttpEngine {
            callIndex++
            // Returns same cursor with hasMore = true infinitely, with monotonic rowIds
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-1",
                    "records":[
                        {"tableName":"todos","rowId":"row-$callIndex","revision":1,"deleted":false,"data":{"id":"row-$callIndex","title":"Loop","completed":false,"createdAt":1000}}
                    ],
                    "cursor":"same-cursor",
                    "hasMore":true
                }"""
            )
        }
        val (client, store) = createTestSetup(engine)
        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
        assertEquals("CURSOR_LOOP", result.error.code)
        assertEquals(0, store.dao.countStagedRecords())
    }

    @Test
    fun testMissingPreScanConfirmedIdentityTriggersConsistencyError() = runTest {
        val engine = TestHttpEngine {
            // Scan returns only row-2, omitting pre-scan confirmed row-prescan
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-1",
                    "records":[
                        {"tableName":"todos","rowId":"row-2","revision":1,"deleted":false,"data":{"id":"row-2","title":"Todo 2","completed":false,"createdAt":2000}}
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        // Insert confirmed pre-scan record with epoch-1
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-prescan",
                serverRevision = 1L,
                deleted = false,
                title = "Must not disappear",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1"
            )
        )

        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ResetRequired>(result.error)
        assertEquals("CONSISTENCY_ERROR", result.error.code)
    }

    @Test
    fun testMonotonicPromotionHigherRevisionReplaces() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-1",
                    "records":[
                        {"tableName":"todos","rowId":"row-1","revision":2,"deleted":false,"data":{"id":"row-1","title":"Updated Title","completed":true,"createdAt":1000}}
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-1",
                serverRevision = 1L,
                deleted = false,
                title = "Initial Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1"
            )
        )

        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Success<Int>>(result)
        val base = store.dao.getServerBase("row-1")
        assertNotNull(base)
        assertEquals(2L, base.serverRevision)
        assertEquals("Updated Title", base.title)
        assertTrue(base.completed)
    }

    @Test
    fun testMonotonicPromotionEqualRevisionDifferingLogicalDataFails() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-1",
                    "records":[
                        {"tableName":"todos","rowId":"row-1","revision":1,"deleted":false,"data":{"id":"row-1","title":"Divergent Title","completed":false,"createdAt":1000}}
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-1",
                serverRevision = 1L,
                deleted = false,
                title = "Original Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-1"
            )
        )

        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
        assertEquals("DIVERGENT_REVISION", result.error.code)
    }

    @Test
    fun testMonotonicPromotionLowerRevisionRetainsLocal() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-1",
                    "records":[
                        {"tableName":"todos","rowId":"row-1","revision":2,"deleted":false,"data":{"id":"row-1","title":"Stale Title","completed":false,"createdAt":1000}}
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-1",
                serverRevision = 3L,
                deleted = false,
                title = "Newer Local Title",
                completed = true,
                createdAt = 1000L,
                updatedAt = 2000L,
                epoch = "epoch-1"
            )
        )

        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Success<Int>>(result)
        val base = store.dao.getServerBase("row-1")
        assertNotNull(base)
        assertEquals(3L, base.serverRevision)
        assertEquals("Newer Local Title", base.title)
    }

    @Test
    fun testActiveRecordWithMissingDataFails() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-1",
                    "records":[
                        {"tableName":"todos","rowId":"row-1","revision":1,"deleted":false,"data":null}
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
        assertEquals("INVALID_RECORD", result.error.code)
    }

    @Test
    fun testRowIdOrderViolationFails() = runTest {
        val engine = TestHttpEngine {
            // Returns row-2 then row-1 (decreasing order)
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-1",
                    "records":[
                        {"tableName":"todos","rowId":"row-2","revision":1,"deleted":false,"data":{"id":"row-2","title":"Two","completed":false,"createdAt":1000}},
                        {"tableName":"todos","rowId":"row-1","revision":1,"deleted":false,"data":{"id":"row-1","title":"One","completed":false,"createdAt":1000}}
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
        assertEquals("ROW_ORDER_VIOLATION", result.error.code)
    }

    @Test
    fun testEmptyPageWithHasMoreFails() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-1",
                    "records":[],
                    "cursor":"some-cursor",
                    "hasMore":true
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        val scan = StagedFullScan(client, store, activeEpoch = "epoch-1")
        val result = scan.executeScan()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
        assertEquals("NON_PROGRESSING_CURSOR", result.error.code)
    }

    @Test
    fun testV2ScanPromotesAll12TemporalFields() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v2",
                    "epoch":"epoch-v2",
                    "records":[
                        {
                            "tableName":"todos",
                            "rowId":"row-v2-1",
                            "revision":1,
                            "deleted":false,
                            "data":{
                                "id":"row-v2-1",
                                "title":"Full Temporal Task",
                                "completed":false,
                                "createdAt":1000,
                                "deadlineDate":"2026-11-01",
                                "deadlineTime":"14:00",
                                "deadlineZone":"America/New_York",
                                "deadlineOffsetSeconds":-18000,
                                "deadlineAnchorEpochMs":1793559600000,
                                "deadlineOverdueEpochMs":1793559600000,
                                "reminderEnabled":true,
                                "reminderLeadMinutes":30,
                                "reminderIntervalMinutes":15,
                                "reminderCount":2,
                                "scheduleToken":"sched-tok-full"
                            }
                        }
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        val scan = StagedFullScan(client, store, activeEpoch = "epoch-v2", protocol = StrictSyncV2Contract.PROTOCOL_VERSION)
        val result = scan.executeScan()

        assertIs<ApiResult.Success<Int>>(result)
        assertEquals(1, result.value)

        val base = store.dao.getServerBase("row-v2-1")
        assertNotNull(base)
        assertEquals("2026-11-01", base.deadlineDate)
        assertEquals("14:00", base.deadlineTime)
        assertEquals("America/New_York", base.deadlineZone)
        assertEquals(-18000L, base.deadlineOffsetSeconds)
        assertEquals(1793559600000L, base.deadlineAnchorEpochMs)
        assertEquals(1793559600000L, base.deadlineOverdueEpochMs)
        assertTrue(base.reminderEnabled)
        assertEquals(30L, base.reminderLeadMinutes)
        assertEquals(15L, base.reminderIntervalMinutes)
        assertEquals(2L, base.reminderCount)
        assertEquals("sched-tok-full", base.scheduleToken)
    }

    @Test
    fun testMigrationEqualRevisionNormalizesV1BaseWithV2Defaults() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v2",
                    "epoch":"epoch-mig",
                    "records":[
                        {
                            "tableName":"todos",
                            "rowId":"row-eq-rev",
                            "revision":2,
                            "deleted":false,
                            "data":{
                                "id":"row-eq-rev",
                                "title":"Identical Title",
                                "completed":false,
                                "createdAt":1000,
                                "deadlineDate":"2026-11-05",
                                "deadlineZone":"UTC",
                                "deadlineOffsetSeconds":0,
                                "deadlineAnchorEpochMs":1793836800000,
                                "deadlineOverdueEpochMs":1793923200000,
                                "reminderAnchorTime":"09:00",
                                "reminderEnabled":false,
                                "reminderLeadMinutes":0,
                                "reminderCount":1,
                                "scheduleToken":"migrated-tok"
                            }
                        }
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        // Local base has revision 2 from v1 (null temporal fields)
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-eq-rev",
                serverRevision = 2L,
                deleted = false,
                title = "Identical Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-mig",
                deadlineDate = null,
                scheduleToken = null
            )
        )

        // Scan with isMigration = true
        val scan = StagedFullScan(
            syncClient = client,
            store = store,
            activeEpoch = "epoch-mig",
            protocol = StrictSyncV2Contract.PROTOCOL_VERSION,
            isMigration = true
        )
        val result = scan.executeScan()
        assertIs<ApiResult.Success<Int>>(result)

        // Base is enriched with v2 metadata without throwing DIVERGENT_REVISION
        val base = store.dao.getServerBase("row-eq-rev")
        assertNotNull(base)
        assertEquals("2026-11-05", base.deadlineDate)
        assertEquals("migrated-tok", base.scheduleToken)
    }

    @Test
    fun testNonMigrationEqualRevisionDifferingTemporalTriggersDivergentRevision() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v2",
                    "epoch":"epoch-diverge",
                    "records":[
                        {
                            "tableName":"todos",
                            "rowId":"row-div",
                            "revision":2,
                            "deleted":false,
                            "data":{
                                "id":"row-div",
                                "title":"Same Title",
                                "completed":false,
                                "createdAt":1000,
                                "deadlineDate":"2026-12-01",
                                "deadlineZone":"UTC",
                                "deadlineOffsetSeconds":0,
                                "deadlineAnchorEpochMs":1796083200000,
                                "deadlineOverdueEpochMs":1796169600000,
                                "reminderAnchorTime":"09:00",
                                "reminderEnabled":false,
                                "reminderLeadMinutes":0,
                                "reminderCount":1,
                                "scheduleToken":"tok-2"
                            }
                        }
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        // Local base has different deadlineDate ("2026-11-01" vs "2026-12-01") at the same revision
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-div",
                serverRevision = 2L,
                deleted = false,
                title = "Same Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-diverge",
                deadlineDate = "2026-11-01",
                deadlineZone = "UTC",
                deadlineOffsetSeconds = 0L,
                reminderAnchorTime = "09:00",
                deadlineAnchorEpochMs = 1793491200000L,
                deadlineOverdueEpochMs = 1793577600000L,
                reminderEnabled = false,
                reminderLeadMinutes = 0L,
                reminderCount = 1L,
                scheduleToken = "tok-1"
            )
        )

        // Normal scan (isMigration = false)
        val scan = StagedFullScan(
            syncClient = client,
            store = store,
            activeEpoch = "epoch-diverge",
            protocol = StrictSyncV2Contract.PROTOCOL_VERSION,
            isMigration = false
        )
        val result = scan.executeScan()
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
        assertEquals("DIVERGENT_REVISION", result.error.code)
    }

    @Test
    fun testPrePromotionFencingAbortsWithoutPromoting() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v1",
                    "epoch":"epoch-fence",
                    "records":[
                        {"tableName":"todos","rowId":"row-fence","revision":1,"deleted":false,"data":{"id":"row-fence","title":"Fenced","completed":false,"createdAt":1000}}
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        var isFencedFlag = true
        val scan = StagedFullScan(
            syncClient = client,
            store = store,
            activeEpoch = "epoch-fence",
            isFenced = { isFencedFlag }
        )
        val result = scan.executeScan()
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.Unauthorized>(result.error)
        assertEquals("STORE_FROZEN", result.error.code)
        assertNull(store.dao.getServerBase("row-fence"))
    }

    @Test
    fun testMigrationEqualRevisionRejectsMismatchedTitleEvenWithMissingTemporalMetadata() = runTest {
        val engine = TestHttpEngine {
            Pair(
                HttpStatusCode.OK,
                """{
                    "protocol":"strict-v2",
                    "epoch":"epoch-mig-reject",
                    "records":[
                        {
                            "tableName":"todos",
                            "rowId":"row-mig-mismatch",
                            "revision":2,
                            "deleted":false,
                            "data":{
                                "id":"row-mig-mismatch",
                                "title":"Server Title Differs",
                                "completed":false,
                                "createdAt":1000,
                                "deadlineDate":"2026-11-05",
                                "deadlineZone":"UTC",
                                "deadlineOffsetSeconds":0,
                                "deadlineAnchorEpochMs":1793836800000,
                                "deadlineOverdueEpochMs":1793923200000,
                                "reminderAnchorTime":"09:00",
                                "reminderEnabled":false,
                                "reminderLeadMinutes":0,
                                "reminderCount":1,
                                "scheduleToken":"migrated-tok"
                            }
                        }
                    ],
                    "cursor":null,
                    "hasMore":false
                }"""
            )
        }

        val (client, store) = createTestSetup(engine)
        store.dao.upsertServerBase(
            ServerBaseEntity(
                rowId = "row-mig-mismatch",
                serverRevision = 2L,
                deleted = false,
                title = "Local Title",
                completed = false,
                createdAt = 1000L,
                updatedAt = 1000L,
                epoch = "epoch-mig-reject",
                deadlineDate = null,
                scheduleToken = null
            )
        )

        val scan = StagedFullScan(
            syncClient = client,
            store = store,
            activeEpoch = "epoch-mig-reject",
            protocol = StrictSyncV2Contract.PROTOCOL_VERSION,
            isMigration = true
        )
        val result = scan.executeScan()
        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.ProtocolIncompatible>(result.error)
        assertEquals("DIVERGENT_REVISION", result.error.code)
    }
}
