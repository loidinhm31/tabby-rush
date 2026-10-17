package dev.tabbyrush.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TabbyRushDao {
    // -------------------------------------------------------------------------
    // Server Base
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM server_base WHERE rowId = :rowId")
    suspend fun getServerBase(rowId: String): ServerBaseEntity?

    @Query("SELECT * FROM server_base WHERE deleted = 0")
    suspend fun getActiveServerBase(): List<ServerBaseEntity>

    @Query("SELECT * FROM server_base")
    suspend fun getAllServerBase(): List<ServerBaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServerBase(entity: ServerBaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServerBase(entities: List<ServerBaseEntity>)

    @Query("DELETE FROM server_base WHERE rowId = :rowId")
    suspend fun deleteServerBase(rowId: String)

    @Query("DELETE FROM server_base")
    suspend fun clearServerBase()

    @Query("SELECT COUNT(*) FROM server_base")
    suspend fun countServerBase(): Int

    // -------------------------------------------------------------------------
    // Outbox
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM outbox WHERE opId = :opId")
    suspend fun getOutboxEntry(opId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE rowId = :rowId ORDER BY localRevision ASC")
    suspend fun getOutboxByRowId(rowId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY localRevision ASC")
    suspend fun getAllOutbox(): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(entity: OutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(entities: List<OutboxEntity>)

    @Query("DELETE FROM outbox WHERE opId = :opId")
    suspend fun deleteOutbox(opId: String)

    @Query("DELETE FROM outbox WHERE rowId = :rowId")
    suspend fun deleteOutboxByRowId(rowId: String)

    @Query("DELETE FROM outbox")
    suspend fun clearOutbox()

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun countOutbox(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE deliveryState = :state")
    suspend fun countOutboxByState(state: String): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE contractVersion = :version")
    suspend fun countOutboxByVersion(version: String): Int

    @Query("UPDATE outbox SET deliveryState = :state, errorMessage = :errorMessage WHERE opId = :opId")
    suspend fun updateDeliveryState(opId: String, state: String, errorMessage: String? = null)

    @Query("UPDATE outbox SET deliveryState = 'UNCERTAIN', errorMessage = 'Interrupted dispatch recovered' WHERE deliveryState = 'DISPATCHING'")
    suspend fun recoverInterruptedDispatches(): Int

    // -------------------------------------------------------------------------
    // Editor Drafts
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM editor_drafts WHERE rowId = :rowId")
    suspend fun getDraft(rowId: String): EditorDraftEntity?

    @Query("SELECT * FROM editor_drafts")
    suspend fun getAllDrafts(): List<EditorDraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: EditorDraftEntity)

    @Query("DELETE FROM editor_drafts WHERE rowId = :rowId")
    suspend fun deleteDraft(rowId: String)

    @Query("DELETE FROM editor_drafts")
    suspend fun clearDrafts()

    @Query("SELECT COUNT(*) FROM editor_drafts")
    suspend fun countDrafts(): Int

    // -------------------------------------------------------------------------
    // Scan Staging
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM scan_staging WHERE rowId = :rowId")
    suspend fun getStagedRecord(rowId: String): ScanStagingEntity?

    @Query("SELECT * FROM scan_staging")
    suspend fun getAllStagedRecords(): List<ScanStagingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaging(entity: ScanStagingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaging(entities: List<ScanStagingEntity>)

    @Query("DELETE FROM scan_staging")
    suspend fun clearStaging()

    @Query("SELECT COUNT(*) FROM scan_staging")
    suspend fun countStagedRecords(): Int

    // -------------------------------------------------------------------------
    // Account Sync Meta
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM account_sync_meta WHERE id = :id")
    suspend fun getMeta(id: String = AccountSyncMetaEntity.SINGLETON_ID): AccountSyncMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: AccountSyncMetaEntity)

    @Query("DELETE FROM account_sync_meta")
    suspend fun clearMeta()

    // -------------------------------------------------------------------------
    // Account Local Settings
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM account_local_settings WHERE id = :id")
    suspend fun getLocalSettings(id: String = AccountLocalSettingsEntity.SINGLETON_ID): AccountLocalSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalSettings(settings: AccountLocalSettingsEntity)

    @Query("DELETE FROM account_local_settings")
    suspend fun clearLocalSettings()

    // -------------------------------------------------------------------------
    // Reminder Delivery Ledger
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM reminder_delivery_ledger WHERE accountKey = :accountKey AND generation = :generation AND taskId = :taskId AND scheduleToken = :scheduleToken AND occurrenceIndex = :occurrenceIndex")
    suspend fun getLedgerEntry(
        accountKey: String,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): ReminderDeliveryLedgerEntity?

    @Query("SELECT * FROM reminder_delivery_ledger WHERE accountKey = :accountKey AND generation = :generation AND taskId = :taskId ORDER BY occurrenceIndex ASC")
    suspend fun getLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        taskId: String
    ): List<ReminderDeliveryLedgerEntity>

    @Query("SELECT * FROM reminder_delivery_ledger WHERE dueEpochMs <= :dueBeforeEpochMs AND claimOutcome = 'PENDING' AND cancellationState = 'ACTIVE' ORDER BY dueEpochMs ASC")
    suspend fun getDueLedgerEntries(dueBeforeEpochMs: Long): List<ReminderDeliveryLedgerEntity>

    @Query("SELECT * FROM reminder_delivery_ledger ORDER BY dueEpochMs ASC")
    suspend fun getAllLedgerEntries(): List<ReminderDeliveryLedgerEntity>

    @Query("SELECT * FROM reminder_delivery_ledger WHERE accountKey = :accountKey AND generation = :generation AND claimOutcome = 'POSTED' AND postedPlatformId IS NOT NULL AND cancellationState = 'ACTIVE'")
    suspend fun getActivePostedNotifications(accountKey: String, generation: Long): List<ReminderDeliveryLedgerEntity>

    @Query("SELECT * FROM reminder_delivery_ledger WHERE accountKey = :accountKey AND claimOutcome = 'POSTED' AND postedPlatformId IS NOT NULL")
    suspend fun getAllPostedNotificationsForAccount(accountKey: String): List<ReminderDeliveryLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedgerEntry(entry: ReminderDeliveryLedgerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedgerEntries(entries: List<ReminderDeliveryLedgerEntity>)

    @Query("UPDATE reminder_delivery_ledger SET cancellationState = 'CANCELLED', updatedAtEpochMs = :updatedAtEpochMs WHERE accountKey = :accountKey AND generation = :generation AND taskId = :taskId AND cancellationState = 'ACTIVE'")
    suspend fun cancelLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        taskId: String,
        updatedAtEpochMs: Long
    )
    @Query("UPDATE reminder_delivery_ledger SET postedPlatformId = NULL, updatedAtEpochMs = :nowEpochMs WHERE accountKey = :accountKey AND generation = :generation AND postedPlatformId = :platformNotificationId")
    suspend fun clearPostedNotificationId(
        accountKey: String,
        generation: Long,
        platformNotificationId: String,
        nowEpochMs: Long
    )


    @Query("DELETE FROM reminder_delivery_ledger WHERE accountKey = :accountKey")
    suspend fun deleteLedgerForAccount(accountKey: String)

    @Query("DELETE FROM reminder_delivery_ledger")
    suspend fun clearLedger()

    @Query("SELECT COUNT(*) FROM reminder_delivery_ledger")
    suspend fun countLedgerEntries(): Int

    @Transaction
    suspend fun wipeAllTablesAtomic() {
        clearDrafts()
        clearOutbox()
        clearStaging()
        clearServerBase()
        clearMeta()
        clearLocalSettings()
        clearLedger()
    }
    @Transaction
    suspend fun getScheduleSnapshotAtomic(): PersistedScheduleSnapshot {
        return PersistedScheduleSnapshot(
            serverBases = getAllServerBase(),
            outboxEntries = getAllOutbox()
        )
    }

    @Transaction
    suspend fun claimOccurrenceAtomic(
        accountKey: String,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int,
        claimTimeEpochMs: Long
    ): Boolean {
        val entry = getLedgerEntry(accountKey, generation, taskId, scheduleToken, occurrenceIndex) ?: return false
        if (entry.claimOutcome != ReminderDeliveryLedgerEntity.OUTCOME_PENDING ||
            entry.cancellationState != ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE) {
            return false
        }
        val taskEntries = getLedgerEntriesForTask(accountKey, generation, taskId)
        for (other in taskEntries) {
            if (other.scheduleToken == scheduleToken &&
                other.occurrenceIndex < occurrenceIndex &&
                other.claimOutcome == ReminderDeliveryLedgerEntity.OUTCOME_PENDING &&
                other.cancellationState == ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE) {
                upsertLedgerEntry(
                    other.copy(
                        claimOutcome = ReminderDeliveryLedgerEntity.OUTCOME_SKIPPED,
                        updatedAtEpochMs = claimTimeEpochMs
                    )
                )
            }
        }
        upsertLedgerEntry(
            entry.copy(
                claimOutcome = ReminderDeliveryLedgerEntity.OUTCOME_CLAIMED,
                claimedAtEpochMs = claimTimeEpochMs,
                updatedAtEpochMs = claimTimeEpochMs
            )
        )
        return true
    }

    @Transaction
    suspend fun recordPostedAtomic(
        accountKey: String,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int,
        platformNotificationId: String,
        postTimeEpochMs: Long
    ): Boolean {
        val entry = getLedgerEntry(accountKey, generation, taskId, scheduleToken, occurrenceIndex) ?: return false
        upsertLedgerEntry(
            entry.copy(
                claimOutcome = ReminderDeliveryLedgerEntity.OUTCOME_POSTED,
                postedPlatformId = platformNotificationId,
                updatedAtEpochMs = postTimeEpochMs
            )
        )
        return true
    }

    @Transaction
    suspend fun markOccurrencesSkippedAtomic(
        accountKey: String,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        indices: List<Int>,
        updatedAtEpochMs: Long
    ) {
        for (index in indices) {
            val entry = getLedgerEntry(accountKey, generation, taskId, scheduleToken, index) ?: continue
            if (entry.claimOutcome == ReminderDeliveryLedgerEntity.OUTCOME_PENDING &&
                entry.cancellationState == ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE) {
                upsertLedgerEntry(
                    entry.copy(
                        claimOutcome = ReminderDeliveryLedgerEntity.OUTCOME_SKIPPED,
                        updatedAtEpochMs = updatedAtEpochMs
                    )
                )
            }
        }
    }


    // -------------------------------------------------------------------------
    // Atomic Transaction Methods (C1)
    // -------------------------------------------------------------------------

    @Transaction
    suspend fun saveTodoAtomic(
        meta: AccountSyncMetaEntity,
        draft: EditorDraftEntity,
        outbox: OutboxEntity
    ) {
        upsertMeta(meta)
        upsertDraft(draft)
        upsertOutbox(outbox)
    }

    @Transaction
    suspend fun deleteTodoAtomic(
        meta: AccountSyncMetaEntity,
        draft: EditorDraftEntity,
        outbox: OutboxEntity
    ) {
        upsertMeta(meta)
        upsertDraft(draft)
        upsertOutbox(outbox)
    }

    @Transaction
    suspend fun cancelUnsentCreateAtomic(rowId: String) {
        deleteDraft(rowId)
        deleteOutboxByRowId(rowId)
    }

    @Transaction
    suspend fun reconcileAcceptedAtomic(
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

    @Transaction
    suspend fun promoteStagingAtomic(
        basesToUpsert: List<ServerBaseEntity>
    ) {
        upsertServerBase(basesToUpsert)
        clearStaging()
    }

    @Transaction
    suspend fun replaceOutboxAtomic(
        oldOpId: String?,
        newOp: OutboxEntity
    ) {
        if (oldOpId != null) {
            deleteOutbox(oldOpId)
        }
        upsertOutbox(newOp)
    }

    @Transaction
    suspend fun discardConflictAtomic(
        opId: String,
        rowId: String,
        conflictLocalRev: Long
    ) {
        deleteOutbox(opId)
        val draft = getDraft(rowId)
        if (draft != null && draft.localRevision <= conflictLocalRev) {
            deleteDraft(rowId)
        }
    }

    @Transaction
    suspend fun purgeStaleOutboxAtomic(
        opId: String,
        rowId: String,
        clearDraft: Boolean
    ) {
        if (clearDraft) {
            deleteDraft(rowId)
        }
        deleteOutbox(opId)
    }

    @Transaction
    suspend fun discardRegressiveReconcileAtomic(
        opId: String,
        rowId: String,
        clearDraft: Boolean
    ) {
        purgeStaleOutboxAtomic(opId, rowId, clearDraft)
    }

    @Transaction
    suspend fun freezeOutboxAtomic(
        opId: String,
        expectedLocalRevision: Long,
        newOp: OutboxEntity
    ): Boolean {
        val current = getOutboxEntry(opId) ?: return false
        if (current.localRevision != expectedLocalRevision || current.deliveryState != OutboxEntity.STATE_PENDING) {
            return false
        }
        upsertOutbox(newOp)
        return true
    }
}
