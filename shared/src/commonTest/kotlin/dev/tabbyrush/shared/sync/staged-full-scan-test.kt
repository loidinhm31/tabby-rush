package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.auth.FakeCredentialVault
import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.TestHttpEngine
import dev.tabbyrush.shared.contract.*
import dev.tabbyrush.shared.data.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeTabbyRushDao : TabbyRushDao {
    private val evidence = mutableMapOf<String, LegacySyncEvidenceEntity>()
    private fun activeOutbox() = outbox.values.filter { it.deliveryState !in setOf("ACKNOWLEDGED", "CONVERGED", "DISCARDED") }
    override suspend fun getLegacyEvidence() = evidence.values.toList()
    override suspend fun upsertLegacyEvidence(evidence: LegacySyncEvidenceEntity) { this.evidence[evidence.sourceOpId] = evidence }
    override suspend fun clearLegacyEvidence() { evidence.clear() }

    val serverBase = mutableMapOf<String, ServerBaseEntity>()
    val outbox = mutableMapOf<String, OutboxEntity>()
    val drafts = mutableMapOf<String, EditorDraftEntity>()
    val staging = mutableMapOf<String, ScanStagingEntity>()
    var meta: AccountSyncMetaEntity? = null
    var localSettings: AccountLocalSettingsEntity? = null
    val ledger = mutableMapOf<String, ReminderDeliveryLedgerEntity>()
    val accessMap = mutableMapOf<String, TaskAccessEntity>()
    val stagedAccessMap = mutableMapOf<String, AccessCheckStagingEntity>()
    val evidenceLossMap = mutableMapOf<String, AccessLossEvidenceEntity>()

    override suspend fun getServerBase(bindingKey: String, tableName: String, rowId: String): ServerBaseEntity? =
        serverBase.values.firstOrNull { it.bindingKey == bindingKey && it.tableName == tableName && it.rowId == rowId }
    override suspend fun getServerBase(rowId: String): ServerBaseEntity? = serverBase[rowId]
    override suspend fun getActiveServerBase(): List<ServerBaseEntity> = serverBase.values.filter { !it.deleted && it.tableName == "todos" }
    override suspend fun getAllServerBase(): List<ServerBaseEntity> = serverBase.values.filter { it.tableName == "todos" }
    override suspend fun getAllServerBaseAnyTable(): List<ServerBaseEntity> = serverBase.values.toList()
    override suspend fun upsertServerBase(entity: ServerBaseEntity) {
        val key = "${entity.bindingKey}:${entity.tableName}:${entity.rowId}"
        serverBase[key] = entity
        serverBase[entity.rowId] = entity
    }
    override suspend fun upsertServerBase(entities: List<ServerBaseEntity>) { entities.forEach { upsertServerBase(it) } }
    override suspend fun deleteServerBase(bindingKey: String, tableName: String, rowId: String) {
        serverBase.remove("${bindingKey}:${tableName}:${rowId}")
        serverBase.remove(rowId)
    }
    override suspend fun deleteServerBase(rowId: String) { serverBase.remove(rowId) }
    override suspend fun deleteServerBaseByOwnerAndRow(bindingKey: String, rowId: String) {
        val toRemove = serverBase.values.filter { it.bindingKey == bindingKey && it.rowId == rowId }.map { "${it.bindingKey}:${it.tableName}:${it.rowId}" }
        toRemove.forEach { serverBase.remove(it) }
        serverBase.remove(rowId)
    }
    override suspend fun purgeSharedServerBase(keepBoundKey: String) {
        val toRemove = serverBase.values.filter { it.bindingKey != keepBoundKey && it.ownerBinding != BINDING_LOCAL_ACCOUNT }.map { "${it.bindingKey}:${it.tableName}:${it.rowId}" }
        toRemove.forEach { serverBase.remove(it) }
    }
    override suspend fun clearServerBase() { serverBase.clear() }
    override suspend fun countServerBase(): Int = serverBase.size
    override suspend fun getActiveChildrenServerBase(bindingKey: String, parentTaskId: String): List<ServerBaseEntity> =
        serverBase.values.filter { it.bindingKey == bindingKey && it.parentTaskId == parentTaskId && !it.deleted && it.tableName == "todos" }
    override suspend fun getActiveChildrenServerBase(parentTaskId: String): List<ServerBaseEntity> =
        serverBase.values.filter { it.parentTaskId == parentTaskId && !it.deleted }
    override suspend fun getAllChildrenServerBase(bindingKey: String, parentTaskId: String): List<ServerBaseEntity> =
        serverBase.values.filter { it.bindingKey == bindingKey && it.parentTaskId == parentTaskId && it.tableName == "todos" }
    override suspend fun getAllChildrenServerBase(parentTaskId: String): List<ServerBaseEntity> =
        serverBase.values.filter { it.parentTaskId == parentTaskId }

    override suspend fun getOutboxEntry(opId: String): OutboxEntity? = outbox[opId]
    override suspend fun getOutboxByRow(bindingKey: String, tableName: String, rowId: String): List<OutboxEntity> =
        activeOutbox().filter { it.bindingKey == bindingKey && it.tableName == tableName && it.rowId == rowId }.sortedBy { it.localRevision }
    override suspend fun getOutboxByRowId(rowId: String): List<OutboxEntity> =
        activeOutbox().filter { it.rowId == rowId }.sortedBy { it.localRevision }
    override suspend fun getAllOutbox(): List<OutboxEntity> =
        activeOutbox().sortedBy { it.localRevision }
    override suspend fun upsertOutbox(entity: OutboxEntity) { outbox[entity.opId] = entity }
    override suspend fun upsertOutbox(entities: List<OutboxEntity>) { entities.forEach { outbox[it.opId] = it } }
    override suspend fun deleteOutbox(opId: String) { outbox.remove(opId) }
    override suspend fun deleteOutboxByRow(bindingKey: String, tableName: String, rowId: String) {
        val toRemove = outbox.values.filter { it.bindingKey == bindingKey && it.tableName == tableName && it.rowId == rowId }.map { it.opId }
        toRemove.forEach { outbox.remove(it) }
    }
    override suspend fun deleteOutboxByRowId(rowId: String) {
        val toRemove = outbox.values.filter { it.rowId == rowId }.map { it.opId }
        toRemove.forEach { outbox.remove(it) }
    }
    override suspend fun deleteOutboxByOwnerAndRow(bindingKey: String, rowId: String) {
        val toRemove = outbox.values.filter { it.bindingKey == bindingKey && it.rowId == rowId }.map { it.opId }
        toRemove.forEach { outbox.remove(it) }
    }
    override suspend fun purgeSharedOutbox(keepBoundKey: String) {
        val toRemove = outbox.values.filter { it.bindingKey != keepBoundKey && it.ownerBinding != BINDING_LOCAL_ACCOUNT }.map { it.opId }
        toRemove.forEach { outbox.remove(it) }
    }
    override suspend fun clearOutbox() { outbox.clear() }
    override suspend fun countOutbox(): Int = activeOutbox().size
    override suspend fun countOutboxByState(state: String): Int = outbox.values.count { it.deliveryState == state }
    override suspend fun updateDeliveryState(opId: String, state: String, errorMessage: String?) {
        val op = outbox[opId]
        if (op != null) {
            outbox[opId] = op.copy(deliveryState = state, errorMessage = errorMessage)
        }
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

    override suspend fun getDraft(bindingKey: String, tableName: String, rowId: String): EditorDraftEntity? =
        drafts.values.firstOrNull { it.bindingKey == bindingKey && it.tableName == tableName && it.rowId == rowId }
    override suspend fun getDraft(rowId: String): EditorDraftEntity? = drafts[rowId]
    override suspend fun getAllDrafts(): List<EditorDraftEntity> = drafts.values.filter { it.tableName == "todos" }
    override suspend fun upsertDraft(draft: EditorDraftEntity) {
        drafts["${draft.bindingKey}:${draft.tableName}:${draft.rowId}"] = draft
        drafts[draft.rowId] = draft
    }
    override suspend fun deleteDraft(bindingKey: String, tableName: String, rowId: String) {
        drafts.remove("${bindingKey}:${tableName}:${rowId}")
        drafts.remove(rowId)
    }
    override suspend fun deleteDraft(rowId: String) { drafts.remove(rowId) }
    override suspend fun deleteDraftByOwnerAndRow(bindingKey: String, rowId: String) {
        val toRemove = drafts.values.filter { it.bindingKey == bindingKey && it.rowId == rowId }.map { "${it.bindingKey}:${it.tableName}:${it.rowId}" }
        toRemove.forEach { drafts.remove(it) }
        drafts.remove(rowId)
    }
    override suspend fun purgeSharedDrafts(keepBoundKey: String) {
        val toRemove = drafts.values.filter { it.bindingKey != keepBoundKey && it.ownerBinding != BINDING_LOCAL_ACCOUNT }.map { "${it.bindingKey}:${it.tableName}:${it.rowId}" }
        toRemove.forEach { drafts.remove(it) }
    }
    override suspend fun clearDrafts() { drafts.clear() }
    override suspend fun countDrafts(): Int = drafts.size

    override suspend fun getStagedRecord(bindingKey: String, tableName: String, rowId: String): ScanStagingEntity? =
        staging.values.firstOrNull { it.bindingKey == bindingKey && it.tableName == tableName && it.rowId == rowId }
    override suspend fun getStagedRecord(rowId: String): ScanStagingEntity? = staging.values.firstOrNull { it.rowId == rowId }
    override suspend fun getStagedRecordsForGeneration(scanGeneration: Long): List<ScanStagingEntity> =
        staging.values.filter { it.scanGeneration == scanGeneration }
    override suspend fun getAllStagedRecords(): List<ScanStagingEntity> = staging.values.toList()
    override suspend fun upsertStaging(entity: ScanStagingEntity) {
        staging["${entity.bindingKey}:${entity.tableName}:${entity.rowId}"] = entity
    }
    override suspend fun upsertStaging(entities: List<ScanStagingEntity>) { entities.forEach { upsertStaging(it) } }
    override suspend fun clearStagingForGeneration(scanGeneration: Long) {
        staging.entries.removeAll { it.value.scanGeneration == scanGeneration }
    }
    override suspend fun deleteStagedByOwnerAndRow(bindingKey: String, rowId: String) {
        staging.entries.removeAll { it.value.bindingKey == bindingKey && it.value.rowId == rowId }
    }
    override suspend fun purgeSharedStaging(keepBoundKey: String) {
        staging.entries.removeAll { it.value.bindingKey != keepBoundKey && it.value.ownerBinding != BINDING_LOCAL_ACCOUNT }
    }
    override suspend fun clearStaging() { staging.clear() }
    override suspend fun countStagedRecords(): Int = staging.size

    override suspend fun getMeta(id: String): AccountSyncMetaEntity? = meta
    override suspend fun upsertMeta(meta: AccountSyncMetaEntity) { this.meta = meta }
    override suspend fun clearMeta() { this.meta = null }

    override suspend fun getLocalSettings(id: String): AccountLocalSettingsEntity? = localSettings
    override suspend fun upsertLocalSettings(settings: AccountLocalSettingsEntity) { this.localSettings = settings }
    override suspend fun clearLocalSettings() { this.localSettings = null }

    override suspend fun getLedgerEntry(
        accountKey: String,
        generation: Long,
        bindingKey: String,
        tableName: String,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): ReminderDeliveryLedgerEntity? = ledger["$accountKey:$generation:$bindingKey:$tableName:$taskId:$scheduleToken:$occurrenceIndex"]

    override suspend fun getLedgerEntry(
        accountKey: String,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): ReminderDeliveryLedgerEntity? = ledger["$accountKey:$generation:$taskId:$scheduleToken:$occurrenceIndex"]

    override suspend fun getLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        bindingKey: String,
        tableName: String,
        taskId: String
    ): List<ReminderDeliveryLedgerEntity> =
        ledger.values.filter { it.accountKey == accountKey && it.generation == generation && it.bindingKey == bindingKey && it.tableName == tableName && it.taskId == taskId }
            .sortedBy { it.occurrenceIndex }

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

    override suspend fun getActivePostedNotifications(accountKey: String, generation: Long): List<ReminderDeliveryLedgerEntity> =
        ledger.values.filter { it.accountKey == accountKey && it.generation == generation && it.claimOutcome == "POSTED" && it.postedPlatformId != null && it.cancellationState == "ACTIVE" }

    override suspend fun getAllPostedNotificationsForAccount(accountKey: String): List<ReminderDeliveryLedgerEntity> =
        ledger.values.filter { it.accountKey == accountKey && it.claimOutcome == "POSTED" && it.postedPlatformId != null }

    override suspend fun clearPostedNotificationId(accountKey: String, generation: Long, platformNotificationId: String, nowEpochMs: Long) {
        for ((k, v) in ledger.toList()) {
            if (v.accountKey == accountKey && v.generation == generation && v.postedPlatformId == platformNotificationId) {
                ledger[k] = v.copy(postedPlatformId = null, updatedAtEpochMs = nowEpochMs)
            }
        }
    }

    override suspend fun upsertLedgerEntry(entry: ReminderDeliveryLedgerEntity) {
        val key = "${entry.accountKey}:${entry.generation}:${entry.taskId}:${entry.scheduleToken}:${entry.occurrenceIndex}"
        ledger[key] = entry
    }

    override suspend fun upsertLedgerEntries(entries: List<ReminderDeliveryLedgerEntity>) {
        entries.forEach { upsertLedgerEntry(it) }
    }

    override suspend fun cancelLedgerEntriesForTask(
        accountKey: String,
        bindingKey: String,
        tableName: String,
        taskId: String,
        updatedAtEpochMs: Long
    ) {
        for ((k, v) in ledger.toList()) {
            if (v.accountKey == accountKey && v.bindingKey == bindingKey && v.tableName == tableName && v.taskId == taskId && v.cancellationState == "ACTIVE") {
                ledger[k] = v.copy(cancellationState = "CANCELLED", updatedAtEpochMs = updatedAtEpochMs)
            }
        }
    }

    override suspend fun cancelLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        bindingKey: String,
        tableName: String,
        taskId: String,
        updatedAtEpochMs: Long
    ) {
        for ((k, v) in ledger.toList()) {
            if (v.accountKey == accountKey && v.generation == generation && v.bindingKey == bindingKey && v.tableName == tableName && v.taskId == taskId && v.cancellationState == "ACTIVE") {
                ledger[k] = v.copy(cancellationState = "CANCELLED", updatedAtEpochMs = updatedAtEpochMs)
            }
        }
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

    override suspend fun getAccess(ownerUserId: String, tableName: String, rowId: String): TaskAccessEntity? =
        accessMap["$ownerUserId:$tableName:$rowId"]

    override suspend fun getAccessForTask(ownerUserId: String, rowId: String): TaskAccessEntity? =
        accessMap["$ownerUserId:todos:$rowId"]

    override suspend fun getAllAccess(): List<TaskAccessEntity> = accessMap.values.toList()

    override suspend fun upsertAccess(entity: TaskAccessEntity) {
        accessMap["${entity.ownerUserId}:${entity.tableName}:${entity.rowId}"] = entity
    }

    override suspend fun upsertAccess(entities: List<TaskAccessEntity>) {
        entities.forEach { upsertAccess(it) }
    }

    override suspend fun deleteAccess(ownerUserId: String, tableName: String, rowId: String) {
        accessMap.remove("$ownerUserId:$tableName:$rowId")
    }

    override suspend fun deleteAccessForTask(ownerUserId: String, rowId: String) {
        accessMap.remove("$ownerUserId:todos:$rowId")
    }

    override suspend fun clearAccessExceptOwner(ownerUserId: String) {
        val toRemove = accessMap.values.filter { it.ownerUserId != ownerUserId }.map { "${it.ownerUserId}:${it.tableName}:${it.rowId}" }
        toRemove.forEach { accessMap.remove(it) }
    }

    override suspend fun clearAccess() { accessMap.clear() }

    override fun observeAccess(ownerUserId: String, tableName: String, rowId: String): Flow<TaskAccessEntity?> =
        flowOf(accessMap["$ownerUserId:$tableName:$rowId"])

    override suspend fun getStagedAccess(scanGeneration: Long, ownerUserId: String, tableName: String, rowId: String): AccessCheckStagingEntity? =
        stagedAccessMap["$scanGeneration:$ownerUserId:$tableName:$rowId"]

    override suspend fun getStagedAccessForGeneration(scanGeneration: Long): List<AccessCheckStagingEntity> =
        stagedAccessMap.values.filter { it.scanGeneration == scanGeneration }

    override suspend fun upsertStagedAccess(entities: List<AccessCheckStagingEntity>) {
        entities.forEach {
            stagedAccessMap["${it.scanGeneration}:${it.ownerUserId}:${it.tableName}:${it.rowId}"] = it
        }
    }

    override suspend fun clearStagedAccessForGeneration(scanGeneration: Long) {
        stagedAccessMap.entries.removeAll { it.value.scanGeneration == scanGeneration }
    }

    override suspend fun clearStagedAccess() { stagedAccessMap.clear() }

    override suspend fun getAllAccessLossEvidence(): List<AccessLossEvidenceEntity> = evidenceLossMap.values.toList()

    override suspend fun getAccessLossEvidence(evidenceId: String): AccessLossEvidenceEntity? =
        evidenceLossMap.values.firstOrNull { it.evidenceId == evidenceId }

    override suspend fun upsertAccessLossEvidence(entity: AccessLossEvidenceEntity) {
        evidenceLossMap[entity.evidenceId] = entity
    }

    override suspend fun deleteAccessLossEvidenceByRef(ownerUserId: String, rowId: String) {
        val toRemove = evidenceLossMap.values.filter { it.ownerUserId == ownerUserId && it.rowId == rowId }.map { it.evidenceId }
        toRemove.forEach { evidenceLossMap.remove(it) }
    }

    override suspend fun deleteAccessLossEvidence(evidenceId: String) {
        evidenceLossMap.remove(evidenceId)
    }

    override suspend fun clearAccessLossEvidence() { evidenceLossMap.clear() }

    override suspend fun bindServerBaseAtomic(boundKey: String, userId: String) {
        for ((k, v) in serverBase.toList()) {
            if (v.ownerBinding == BINDING_LOCAL_ACCOUNT) {
                serverBase[k] = v.copy(bindingKey = boundKey, ownerBinding = BINDING_BOUND, ownerUserId = userId)
            }
        }
    }

    override suspend fun bindOutboxAtomic(boundKey: String, userId: String) {
        for ((k, v) in outbox.toList()) {
            if (v.ownerBinding == BINDING_LOCAL_ACCOUNT) {
                outbox[k] = v.copy(bindingKey = boundKey, ownerBinding = BINDING_BOUND, ownerUserId = userId)
            }
        }
    }

    override suspend fun bindDraftsAtomic(boundKey: String, userId: String) {
        for ((k, v) in drafts.toList()) {
            if (v.ownerBinding == BINDING_LOCAL_ACCOUNT) {
                drafts[k] = v.copy(bindingKey = boundKey, ownerBinding = BINDING_BOUND, ownerUserId = userId)
            }
        }
    }

    override suspend fun bindStagingAtomic(boundKey: String, userId: String) {
        for ((k, v) in staging.toList()) {
            if (v.ownerBinding == BINDING_LOCAL_ACCOUNT) {
                staging[k] = v.copy(bindingKey = boundKey, ownerBinding = BINDING_BOUND, ownerUserId = userId)
            }
        }
    }

    override suspend fun bindLedgerAtomic(boundKey: String, userId: String) {
        for ((k, v) in ledger.toList()) {
            if (v.ownerBinding == BINDING_LOCAL_ACCOUNT) {
                ledger[k] = v.copy(bindingKey = boundKey, ownerBinding = BINDING_BOUND, ownerUserId = userId)
            }
        }
    }

    override suspend fun bindEvidenceAtomic(boundKey: String, userId: String) {
        for ((k, v) in evidence.toList()) {
            if (v.ownerBinding == BINDING_LOCAL_ACCOUNT) {
                evidence[k] = v.copy(bindingKey = boundKey, ownerBinding = BINDING_BOUND, ownerUserId = userId)
            }
        }
    }
}

class StagedFullScanTest {
    @Test fun compositeCheckpointPreservesNanosecondsAndTies() {
        val a = SyncCheckpointDto("2026-10-20T00:00:00.123456789Z", "a")
        val b = SyncCheckpointDto("2026-10-20T00:00:00.123456789Z", "b")
        assertEquals(-1, compareCheckpoint(a, b))
        assertEquals(1, compareCheckpoint(b, a))
    }

    @Test fun nonprogressingHasMorePageRejected() {
        val cp = SyncCheckpointDto("2026-10-20T00:00:00Z", "a")
        assertFailsWith<IllegalArgumentException> {
            validatePage(PullResponseDto(emptyList(), cp, cp.updatedAt, true, 1L), cp)
        }
    }

    @Test fun emptyFinalPageMayKeepExactCheckpoint() {
        val cp = SyncCheckpointDto("2026-10-20T00:00:00.123456789Z", "a")
        validatePage(PullResponseDto(emptyList(), cp, cp.updatedAt, false, 2L), cp)
    }
    @Test fun multiRecordPageWithSharedTaskAndAssignmentSucceeds() {
        val cp = SyncCheckpointDto("2026-10-21T10:00:00.000Z", "6ad8f92607d605ce58aed523")
        val records = listOf(
            PullRecordDto(
                tableName = "todos",
                rowId = "task-1",
                data = kotlinx.serialization.json.buildJsonObject { put("title", "Clean Room") },
                version = 1L,
                syncedAt = "2026-10-21T10:00:00.000Z",
                ownerUserId = "user-alice"
            ),
            PullRecordDto(
                tableName = dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME,
                rowId = "task-1",
                data = kotlinx.serialization.json.buildJsonObject { put("userId", "user-bob") },
                version = 1L,
                syncedAt = "2026-10-21T10:00:00.000Z",
                ownerUserId = "user-alice"
            )
        )
        validatePage(PullResponseDto(records, cp, cp.updatedAt, false, 1L), null)
    }


    @Test fun fullScanStagesPagesBindsRevisionAndPromotesAtomically() = runTest {
        val config = HubConfig("http://localhost:3100", "tabby-rush", "test-key", BuildMode.DEBUG)
        val accountKey = AccountKey.create(config.canonicalOrigin, "user-alice")
        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(accountKey = accountKey, userId = "user-alice", daoOverride = fakeDao)

        val cp1 = SyncCheckpointDto("2026-10-21T10:00:00.000Z", "task-1")
        val engine = TestHttpEngine { req ->
            val body = when (val b = req.body) {
                is io.ktor.http.content.TextContent -> b.text
                is io.ktor.http.content.ByteArrayContent -> b.bytes().decodeToString()
                else -> b.toString()
            }
            val hasAccessChecks = body.contains("accessChecks")
            if (hasAccessChecks) {
                // Access checks response or terminal check response
                val isTerminalEmpty = body.contains("\"accessChecks\":[]")
                val resultsJson = if (isTerminalEmpty) "[]" else """[
                    {
                        "ref": {"ownerUserId":"user-alice","tableName":"todos","rowId":"task-1"},
                        "status": "accessible",
                        "capabilities": {"editContent":true,"manageAssignment":true,"deleteTask":true,"createChild":true},
                        "hierarchyCoverage": "full-group"
                    }
                ]"""
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "records": [],
                        "checkpoint": {"updatedAt":"2026-10-21T10:00:00.000Z","id":"task-1"},
                        "serverTimestamp": "2026-10-21T10:00:00.000Z",
                        "hasMore": false,
                        "schemaVersion": 1,
                        "collaborationVersion": 1,
                        "accessRevision": 42,
                        "accessResults": $resultsJson
                    }"""
                )
            } else {
                // Data scan page
                Pair(
                    HttpStatusCode.OK,
                    """{
                        "records": [
                            {
                                "tableName": "todos",
                                "rowId": "task-1",
                                "data": {"id":"task-1","title":"Clean Room","completed":false,"createdAt":1000},
                                "version": 1,
                                "syncedAt": "2026-10-21T10:00:00.000Z",
                                "deleted": false,
                                "ownerUserId": "user-alice"
                            }
                        ],
                        "checkpoint": {"updatedAt":"2026-10-21T10:00:00.000Z","id":"task-1"},
                        "serverTimestamp": "2026-10-21T10:00:00.000Z",
                        "hasMore": false,
                        "schemaVersion": 1,
                        "collaborationVersion": 1,
                        "accessRevision": 42
                    }"""
                )
            }
        }

        val vault = FakeCredentialVault()
        vault.store(accountKey, CredentialBundle("tok", "ref"))
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(AccountIdentity("user-alice", "alice", "alice@example.com"), accountKey)
        val client = HubSyncClient(config, session, expectedAccountKey = accountKey)

        val scan = StagedFullScan(client, store)
        val result = scan.executeScan()

        assertIs<ApiResult.Success<Unit>>(result)
        // Promoted to active server base
        val promoted = store.loadTodos()
        assertEquals(1, promoted.size)
        assertEquals("task-1", promoted[0].id)
        assertEquals("Clean Room", promoted[0].title)
    }

    @Test fun scanDiscardsAndRestartsOn409AccessRevisionChanged() = runTest {
        val config = HubConfig("http://localhost:3100", "tabby-rush", "test-key", BuildMode.DEBUG)
        val accountKey = AccountKey.create(config.canonicalOrigin, "user-alice")
        val fakeDao = FakeTabbyRushDao()
        val store = RoomAccountStore(accountKey = accountKey, userId = "user-alice", daoOverride = fakeDao)

        val engine = TestHttpEngine { req ->
            Pair(
                HttpStatusCode.Conflict,
                """{"error":{"code":"ACCESS_REVISION_CHANGED","message":"Access revision changed"}}"""
            )
        }

        val vault = FakeCredentialVault()
        vault.store(accountKey, CredentialBundle("tok", "ref"))
        val session = HubAuthSession(config, vault, engine)
        session.restoreSession(AccountIdentity("user-alice", "alice", "alice@example.com"), accountKey)
        val client = HubSyncClient(config, session, expectedAccountKey = accountKey)

        val scan = StagedFullScan(client, store)
        val result = scan.executeScan()

        assertIs<ApiResult.Failure>(result)
        assertIs<SafeError.AccessRevisionChanged>(result.error)
        // Staging was cleared
        assertEquals(0, fakeDao.countStagedRecords())
    }
}
