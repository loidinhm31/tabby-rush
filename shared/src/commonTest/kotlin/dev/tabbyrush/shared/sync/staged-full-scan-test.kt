package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.data.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    override suspend fun getServerBase(rowId: String): ServerBaseEntity? = serverBase[rowId]
    override suspend fun getActiveServerBase(): List<ServerBaseEntity> = serverBase.values.filter { !it.deleted }
    override suspend fun getAllServerBase(): List<ServerBaseEntity> = serverBase.values.toList()
    override suspend fun upsertServerBase(entity: ServerBaseEntity) { serverBase[entity.rowId] = entity }
    override suspend fun upsertServerBase(entities: List<ServerBaseEntity>) { entities.forEach { serverBase[it.rowId] = it } }
    override suspend fun deleteServerBase(rowId: String) { serverBase.remove(rowId) }
    override suspend fun clearServerBase() { serverBase.clear() }
    override suspend fun countServerBase(): Int = serverBase.size
    override suspend fun getActiveChildrenServerBase(parentTaskId: String): List<ServerBaseEntity> =
        serverBase.values.filter { it.parentTaskId == parentTaskId && !it.deleted }
    override suspend fun getAllChildrenServerBase(parentTaskId: String): List<ServerBaseEntity> =
        serverBase.values.filter { it.parentTaskId == parentTaskId }

    override suspend fun getOutboxEntry(opId: String): OutboxEntity? = outbox[opId]
    override suspend fun getOutboxByRowId(rowId: String): List<OutboxEntity> =
        activeOutbox().filter { it.rowId == rowId }.sortedBy { it.localRevision }
    override suspend fun getAllOutbox(): List<OutboxEntity> =
        activeOutbox().sortedBy { it.localRevision }
    override suspend fun upsertOutbox(entity: OutboxEntity) { outbox[entity.opId] = entity }
    override suspend fun upsertOutbox(entities: List<OutboxEntity>) { entities.forEach { outbox[it.opId] = it } }
    override suspend fun deleteOutbox(opId: String) { outbox.remove(opId) }
    override suspend fun deleteOutboxByRowId(rowId: String) {
        val toRemove = outbox.values.filter { it.rowId == rowId }.map { it.opId }
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
}
