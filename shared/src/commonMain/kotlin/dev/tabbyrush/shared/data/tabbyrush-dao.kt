package dev.tabbyrush.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.tabbyrush.shared.contract.ASSIGNMENT_TABLE_NAME
import dev.tabbyrush.shared.contract.TaskDataContract

@Dao
interface TabbyRushDao {
    // -------------------------------------------------------------------------
    // Server Base
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM server_base WHERE bindingKey = :bindingKey AND tableName = :tableName AND rowId = :rowId")
    suspend fun getServerBase(bindingKey: String, tableName: String, rowId: String): ServerBaseEntity?

    @Query("SELECT * FROM server_base WHERE rowId = :rowId LIMIT 1")
    suspend fun getServerBase(rowId: String): ServerBaseEntity?

    @Query("SELECT * FROM server_base WHERE deleted = 0 AND tableName = 'todos'")
    suspend fun getActiveServerBase(): List<ServerBaseEntity>

    @Query("SELECT * FROM server_base WHERE tableName = 'todos'")
    suspend fun getAllServerBase(): List<ServerBaseEntity>

    @Query("SELECT * FROM server_base")
    suspend fun getAllServerBaseAnyTable(): List<ServerBaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServerBase(entity: ServerBaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServerBase(entities: List<ServerBaseEntity>)

    @Query("DELETE FROM server_base WHERE bindingKey = :bindingKey AND tableName = :tableName AND rowId = :rowId")
    suspend fun deleteServerBase(bindingKey: String, tableName: String, rowId: String)

    @Query("DELETE FROM server_base WHERE rowId = :rowId")
    suspend fun deleteServerBase(rowId: String)

    @Query("DELETE FROM server_base WHERE bindingKey = :bindingKey AND rowId = :rowId")
    suspend fun deleteServerBaseByOwnerAndRow(bindingKey: String, rowId: String)

    @Query("DELETE FROM server_base WHERE bindingKey != :keepBoundKey AND ownerBinding != 'LOCAL_ACCOUNT'")
    suspend fun purgeSharedServerBase(keepBoundKey: String)

    @Query("DELETE FROM server_base")
    suspend fun clearServerBase()

    @Query("SELECT COUNT(*) FROM server_base")
    suspend fun countServerBase(): Int

    @Query("SELECT * FROM server_base WHERE bindingKey = :bindingKey AND parentTaskId = :parentTaskId AND deleted = 0 AND tableName = 'todos'")
    suspend fun getActiveChildrenServerBase(bindingKey: String, parentTaskId: String): List<ServerBaseEntity>

    @Query("SELECT * FROM server_base WHERE parentTaskId = :parentTaskId AND deleted = 0")
    suspend fun getActiveChildrenServerBase(parentTaskId: String): List<ServerBaseEntity>

    @Query("SELECT * FROM server_base WHERE bindingKey = :bindingKey AND parentTaskId = :parentTaskId AND tableName = 'todos'")
    suspend fun getAllChildrenServerBase(bindingKey: String, parentTaskId: String): List<ServerBaseEntity>

    @Query("SELECT * FROM server_base WHERE parentTaskId = :parentTaskId")
    suspend fun getAllChildrenServerBase(parentTaskId: String): List<ServerBaseEntity>
    // -------------------------------------------------------------------------
    // Outbox
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM outbox WHERE opId = :opId")
    suspend fun getOutboxEntry(opId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE bindingKey = :bindingKey AND tableName = :tableName AND rowId = :rowId AND deliveryState NOT IN ('ACKNOWLEDGED','CONVERGED','DISCARDED') ORDER BY localRevision ASC, opId ASC")
    suspend fun getOutboxByRow(bindingKey: String, tableName: String, rowId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE rowId = :rowId AND deliveryState NOT IN ('ACKNOWLEDGED','CONVERGED','DISCARDED') ORDER BY localRevision ASC, opId ASC")
    suspend fun getOutboxByRowId(rowId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE deliveryState NOT IN ('ACKNOWLEDGED','CONVERGED','DISCARDED') ORDER BY localRevision ASC, opId ASC")
    suspend fun getAllOutbox(): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(entity: OutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(entities: List<OutboxEntity>)

    @Query("DELETE FROM outbox WHERE opId = :opId")
    suspend fun deleteOutbox(opId: String)

    @Query("DELETE FROM outbox WHERE bindingKey = :bindingKey AND tableName = :tableName AND rowId = :rowId")
    suspend fun deleteOutboxByRow(bindingKey: String, tableName: String, rowId: String)

    @Query("DELETE FROM outbox WHERE rowId = :rowId")
    suspend fun deleteOutboxByRowId(rowId: String)

    @Query("DELETE FROM outbox WHERE bindingKey = :bindingKey AND rowId = :rowId")
    suspend fun deleteOutboxByOwnerAndRow(bindingKey: String, rowId: String)

    @Query("DELETE FROM outbox WHERE bindingKey != :keepBoundKey AND ownerBinding != 'LOCAL_ACCOUNT'")
    suspend fun purgeSharedOutbox(keepBoundKey: String)
    @Query("DELETE FROM outbox")
    suspend fun clearOutbox()

    @Query("SELECT COUNT(*) FROM outbox WHERE deliveryState NOT IN ('ACKNOWLEDGED','CONVERGED','DISCARDED')")
    suspend fun countOutbox(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE deliveryState = :state")
    suspend fun countOutboxByState(state: String): Int


    @Query("UPDATE outbox SET deliveryState = :state, errorMessage = :errorMessage WHERE opId = :opId")
    suspend fun updateDeliveryState(opId: String, state: String, errorMessage: String? = null)

    @Query("UPDATE outbox SET deliveryState = 'UNCERTAIN', errorMessage = 'Interrupted dispatch recovered' WHERE deliveryState = 'DISPATCHING'")
    suspend fun recoverInterruptedDispatches(): Int

    @Query("SELECT * FROM legacy_sync_evidence ORDER BY sourceOpId")
    suspend fun getLegacyEvidence(): List<LegacySyncEvidenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLegacyEvidence(evidence: LegacySyncEvidenceEntity)

    @Query("DELETE FROM legacy_sync_evidence")
    suspend fun clearLegacyEvidence()

    // -------------------------------------------------------------------------
    // Editor Drafts
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM editor_drafts WHERE bindingKey = :bindingKey AND tableName = :tableName AND rowId = :rowId")
    suspend fun getDraft(bindingKey: String, tableName: String, rowId: String): EditorDraftEntity?

    @Query("SELECT * FROM editor_drafts WHERE rowId = :rowId LIMIT 1")
    suspend fun getDraft(rowId: String): EditorDraftEntity?

    @Query("SELECT * FROM editor_drafts WHERE tableName = 'todos'")
    suspend fun getAllDrafts(): List<EditorDraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: EditorDraftEntity)

    @Query("DELETE FROM editor_drafts WHERE bindingKey = :bindingKey AND tableName = :tableName AND rowId = :rowId")
    suspend fun deleteDraft(bindingKey: String, tableName: String, rowId: String)

    @Query("DELETE FROM editor_drafts WHERE rowId = :rowId")
    suspend fun deleteDraft(rowId: String)

    @Query("DELETE FROM editor_drafts WHERE bindingKey = :bindingKey AND rowId = :rowId")
    suspend fun deleteDraftByOwnerAndRow(bindingKey: String, rowId: String)

    @Query("DELETE FROM editor_drafts WHERE bindingKey != :keepBoundKey AND ownerBinding != 'LOCAL_ACCOUNT'")
    suspend fun purgeSharedDrafts(keepBoundKey: String)

    @Query("DELETE FROM editor_drafts")
    suspend fun clearDrafts()

    @Query("SELECT COUNT(*) FROM editor_drafts")
    suspend fun countDrafts(): Int

    // -------------------------------------------------------------------------
    // Scan Staging
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM scan_staging WHERE bindingKey = :bindingKey AND tableName = :tableName AND rowId = :rowId")
    suspend fun getStagedRecord(bindingKey: String, tableName: String, rowId: String): ScanStagingEntity?

    @Query("SELECT * FROM scan_staging WHERE rowId = :rowId LIMIT 1")
    suspend fun getStagedRecord(rowId: String): ScanStagingEntity?

    @Query("SELECT * FROM scan_staging")
    suspend fun getAllStagedRecords(): List<ScanStagingEntity>

    @Query("SELECT * FROM scan_staging WHERE scanGeneration = :generation")
    suspend fun getStagedRecordsForGeneration(generation: Long): List<ScanStagingEntity>

    @Query("DELETE FROM scan_staging WHERE scanGeneration = :generation")
    suspend fun clearStagingForGeneration(generation: Long)

    @Query("DELETE FROM scan_staging WHERE bindingKey = :bindingKey AND rowId = :rowId")
    suspend fun deleteStagedByOwnerAndRow(bindingKey: String, rowId: String)

    @Query("DELETE FROM scan_staging WHERE bindingKey != :keepBoundKey AND ownerBinding != 'LOCAL_ACCOUNT'")
    suspend fun purgeSharedStaging(keepBoundKey: String)

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

    @Query("SELECT * FROM reminder_delivery_ledger WHERE accountKey = :accountKey AND generation = :generation AND bindingKey = :bindingKey AND tableName = :tableName AND taskId = :taskId AND scheduleToken = :scheduleToken AND occurrenceIndex = :occurrenceIndex")
    suspend fun getLedgerEntry(
        accountKey: String,
        generation: Long,
        bindingKey: String,
        tableName: String,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): ReminderDeliveryLedgerEntity?

    @Query("SELECT * FROM reminder_delivery_ledger WHERE accountKey = :accountKey AND generation = :generation AND taskId = :taskId AND scheduleToken = :scheduleToken AND occurrenceIndex = :occurrenceIndex LIMIT 1")
    suspend fun getLedgerEntry(
        accountKey: String,
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int
    ): ReminderDeliveryLedgerEntity?

    @Query("SELECT * FROM reminder_delivery_ledger WHERE accountKey = :accountKey AND generation = :generation AND bindingKey = :bindingKey AND tableName = :tableName AND taskId = :taskId ORDER BY occurrenceIndex ASC")
    suspend fun getLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        bindingKey: String,
        tableName: String,
        taskId: String
    ): List<ReminderDeliveryLedgerEntity>

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

    @Query("UPDATE reminder_delivery_ledger SET cancellationState = 'CANCELLED', updatedAtEpochMs = :updatedAtEpochMs WHERE accountKey = :accountKey AND generation = :generation AND bindingKey = :bindingKey AND tableName = :tableName AND taskId = :taskId AND cancellationState = 'ACTIVE'")
    suspend fun cancelLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        bindingKey: String,
        tableName: String,
        taskId: String,
        updatedAtEpochMs: Long
    )

    @Query("UPDATE reminder_delivery_ledger SET cancellationState = 'CANCELLED', updatedAtEpochMs = :updatedAtEpochMs WHERE accountKey = :accountKey AND generation = :generation AND taskId = :taskId AND cancellationState = 'ACTIVE'")
    suspend fun cancelLedgerEntriesForTask(
        accountKey: String,
        generation: Long,
        taskId: String,
        updatedAtEpochMs: Long
    )

    @Query("UPDATE reminder_delivery_ledger SET cancellationState = 'CANCELLED', updatedAtEpochMs = :updatedAtEpochMs WHERE accountKey = :accountKey AND bindingKey = :bindingKey AND tableName = :tableName AND taskId = :taskId AND cancellationState = 'ACTIVE'")
    suspend fun cancelLedgerEntriesForTask(
        accountKey: String,
        bindingKey: String,
        tableName: String,
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

    // -------------------------------------------------------------------------
    // Task Access
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM task_access WHERE ownerUserId = :ownerUserId AND tableName = :tableName AND rowId = :rowId")
    suspend fun getAccess(ownerUserId: String, tableName: String, rowId: String): TaskAccessEntity?

    @Query("SELECT * FROM task_access WHERE ownerUserId = :ownerUserId AND rowId = :rowId")
    suspend fun getAccessForTask(ownerUserId: String, rowId: String): TaskAccessEntity?

    @Query("SELECT * FROM task_access")
    suspend fun getAllAccess(): List<TaskAccessEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccess(entity: TaskAccessEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccess(entities: List<TaskAccessEntity>)

    @Query("DELETE FROM task_access WHERE ownerUserId = :ownerUserId AND tableName = :tableName AND rowId = :rowId")
    suspend fun deleteAccess(ownerUserId: String, tableName: String, rowId: String)

    @Query("DELETE FROM task_access WHERE ownerUserId = :ownerUserId AND rowId = :rowId")
    suspend fun deleteAccessForTask(ownerUserId: String, rowId: String)

    @Query("DELETE FROM task_access WHERE ownerUserId != :ownerUserId")
    suspend fun clearAccessExceptOwner(ownerUserId: String)

    @Query("DELETE FROM task_access")
    suspend fun clearAccess()

    @Query("SELECT * FROM task_access WHERE ownerUserId = :ownerUserId AND tableName = :tableName AND rowId = :rowId")
    fun observeAccess(ownerUserId: String, tableName: String, rowId: String): kotlinx.coroutines.flow.Flow<TaskAccessEntity?>

    // -------------------------------------------------------------------------
    // Access Check Staging
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM access_check_staging WHERE scanGeneration = :scanGeneration AND ownerUserId = :ownerUserId AND tableName = :tableName AND rowId = :rowId")
    suspend fun getStagedAccess(scanGeneration: Long, ownerUserId: String, tableName: String, rowId: String): AccessCheckStagingEntity?

    @Query("SELECT * FROM access_check_staging WHERE scanGeneration = :scanGeneration")
    suspend fun getStagedAccessForGeneration(scanGeneration: Long): List<AccessCheckStagingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStagedAccess(entities: List<AccessCheckStagingEntity>)

    @Query("DELETE FROM access_check_staging WHERE scanGeneration = :scanGeneration")
    suspend fun clearStagedAccessForGeneration(scanGeneration: Long)

    @Query("DELETE FROM access_check_staging")
    suspend fun clearStagedAccess()

    // -------------------------------------------------------------------------
    // Access Loss Evidence
    // -------------------------------------------------------------------------

    @Query("SELECT * FROM access_loss_evidence ORDER BY timestamp DESC")
    suspend fun getAllAccessLossEvidence(): List<AccessLossEvidenceEntity>

    @Query("SELECT * FROM access_loss_evidence WHERE evidenceId = :evidenceId")
    suspend fun getAccessLossEvidence(evidenceId: String): AccessLossEvidenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccessLossEvidence(entity: AccessLossEvidenceEntity)

    @Query("DELETE FROM access_loss_evidence WHERE ownerUserId = :ownerUserId AND rowId = :rowId")
    suspend fun deleteAccessLossEvidenceByRef(ownerUserId: String, rowId: String)

    @Query("DELETE FROM access_loss_evidence WHERE evidenceId = :evidenceId")
    suspend fun deleteAccessLossEvidence(evidenceId: String)

    @Query("DELETE FROM access_loss_evidence")
    suspend fun clearAccessLossEvidence()

    // -------------------------------------------------------------------------
    // Owner Binding Updates
    // -------------------------------------------------------------------------

    @Query("UPDATE server_base SET bindingKey = :boundKey, ownerBinding = 'BOUND', ownerUserId = :userId WHERE ownerBinding = 'LOCAL_ACCOUNT'")
    suspend fun bindServerBaseAtomic(boundKey: String, userId: String)

    @Query("UPDATE outbox SET bindingKey = :boundKey, ownerBinding = 'BOUND', ownerUserId = :userId WHERE ownerBinding = 'LOCAL_ACCOUNT'")
    suspend fun bindOutboxAtomic(boundKey: String, userId: String)

    @Query("UPDATE editor_drafts SET bindingKey = :boundKey, ownerBinding = 'BOUND', ownerUserId = :userId WHERE ownerBinding = 'LOCAL_ACCOUNT'")
    suspend fun bindDraftsAtomic(boundKey: String, userId: String)

    @Query("UPDATE scan_staging SET bindingKey = :boundKey, ownerBinding = 'BOUND', ownerUserId = :userId WHERE ownerBinding = 'LOCAL_ACCOUNT'")
    suspend fun bindStagingAtomic(boundKey: String, userId: String)

    @Query("UPDATE reminder_delivery_ledger SET bindingKey = :boundKey, ownerBinding = 'BOUND', ownerUserId = :userId WHERE ownerBinding = 'LOCAL_ACCOUNT'")
    suspend fun bindLedgerAtomic(boundKey: String, userId: String)

    @Query("UPDATE legacy_sync_evidence SET bindingKey = :boundKey, ownerBinding = 'BOUND', ownerUserId = :userId WHERE ownerBinding = 'LOCAL_ACCOUNT'")
    suspend fun bindEvidenceAtomic(boundKey: String, userId: String)

    @Transaction
    suspend fun bindLegacyRowsAtomic(userId: String) {
        val boundKey = boundBindingKey(userId)
        bindServerBaseAtomic(boundKey, userId)
        bindOutboxAtomic(boundKey, userId)
        bindDraftsAtomic(boundKey, userId)
        bindStagingAtomic(boundKey, userId)
        bindLedgerAtomic(boundKey, userId)
        bindEvidenceAtomic(boundKey, userId)
        val meta = getMeta() ?: AccountSyncMetaEntity()
        upsertMeta(meta.copy(ownerBindingState = "BOUND", boundOwnerUserId = userId))
    }

    @Transaction
    suspend fun wipeAllTablesAtomic() {
        clearDrafts()
        clearOutbox()
        clearStaging()
        clearServerBase()
        clearMeta()
        clearLocalSettings()
        clearLedger()
        clearLegacyEvidence()
        clearAccess()
        clearStagedAccess()
        clearAccessLossEvidence()
    }

    @Transaction
    suspend fun getScheduleSnapshotAtomic(localOwnerUserId: String): PersistedScheduleSnapshot {
        return PersistedScheduleSnapshot(
            serverBases = getAllServerBase().map { it.toPersistedRecord(localOwnerUserId) },
            outboxEntries = getAllOutbox().map { it.toPersistedRecord(localOwnerUserId) },
            accessRecords = getAllAccess().map { it.toPersistedRecord() }
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
    suspend fun deleteGroupAtomic(
        meta: AccountSyncMetaEntity,
        drafts: List<EditorDraftEntity>,
        outboxes: List<OutboxEntity>,
        cancelledChildRowIds: List<String> = emptyList()
    ) {
        upsertMeta(meta)
        for (rowId in cancelledChildRowIds) {
            deleteDraft(rowId)
            deleteOutboxByRowId(rowId)
        }
        for (draft in drafts) {
            upsertDraft(draft)
        }
        upsertOutbox(outboxes)
    }

    @Transaction
    suspend fun cancelUnsentGroupAtomic(rowIds: List<String>) {
        for (rowId in rowIds) {
            deleteDraft(rowId)
            deleteOutboxByRowId(rowId)
        }
    }

    @Transaction
    suspend fun deleteChildAndPreserveParentAtomic(
        meta: AccountSyncMetaEntity,
        childDraft: EditorDraftEntity?,
        childOutbox: OutboxEntity?,
        parentDraft: EditorDraftEntity,
        parentOutbox: OutboxEntity,
        cancelledChildRowId: String? = null
    ) {
        upsertMeta(meta)
        if (cancelledChildRowId != null) {
            deleteDraft(cancelledChildRowId)
            deleteOutboxByRowId(cancelledChildRowId)
        }
        if (childDraft != null) {
            upsertDraft(childDraft)
        }
        if (childOutbox != null) {
            upsertOutbox(childOutbox)
        }
        upsertDraft(parentDraft)
        upsertOutbox(parentOutbox)
    }

    @Transaction
    suspend fun reconcileAcceptedAtomic(
        serverBase: ServerBaseEntity,
        opId: String,
        draftRowId: String,
        clearDraft: Boolean
    ) {
        val op = getOutboxEntry(opId) ?: return
        if (op.deliveryState != OutboxEntity.STATE_DISPATCHING || op.version == null || op.version > serverBase.serverRevision) return
        upsertServerBase(serverBase)
        upsertOutbox(op.copy(deliveryState = "ACKNOWLEDGED", resolution = "EXPLICIT_ACCEPTED_TUPLE"))
        if (clearDraft && getDraft(op.bindingKey, op.tableName, draftRowId)?.localRevision == op.localRevision) {
            deleteDraft(op.bindingKey, op.tableName, draftRowId)
        }
    }


    @Transaction
    suspend fun replaceOutboxAtomic(
        oldOpId: String?,
        newOp: OutboxEntity
    ) {
        if (oldOpId != null) {
            getOutboxEntry(oldOpId)?.let { upsertOutbox(it.copy(deliveryState = "DISCARDED", resolution = "EXPLICIT_REAPPLY_REPLACEMENT")) }
        }
        upsertOutbox(newOp)
    }

    @Transaction
    suspend fun discardConflictAtomic(
        opId: String,
        rowId: String,
        conflictLocalRev: Long
    ) {
        getOutboxEntry(opId)?.let { upsertOutbox(it.copy(deliveryState = "DISCARDED", resolution = "EXPLICIT_DISCARD")) }
        val draft = getDraft(rowId)
        if (draft != null && draft.localRevision <= conflictLocalRev) {
            deleteDraft(rowId)
        }
    }

    /** The copy and retirement of exactly its represented source revisions succeed or roll back together. */
    @Transaction
    suspend fun copyAsNewAtomic(
        meta: AccountSyncMetaEntity,
        draft: EditorDraftEntity,
        outbox: OutboxEntity,
        sourceBindingKey: String,
        sourceRowId: String,
        sourceTodoRevision: Long,
        sourceAssignRevision: Long? = null
    ) {
        saveTodoAtomic(meta, draft, outbox)
        for (op in getOutboxByRow(sourceBindingKey, TaskDataContract.DEFAULT_TABLE_NAME, sourceRowId)) {
            if (op.localRevision <= sourceTodoRevision) {
                upsertOutbox(op.copy(deliveryState = "DISCARDED", resolution = "EXPLICIT_COPY_AS_NEW"))
            }
        }
        if (sourceAssignRevision != null) {
            for (op in getOutboxByRow(sourceBindingKey, ASSIGNMENT_TABLE_NAME, sourceRowId)) {
                if (op.localRevision <= sourceAssignRevision) {
                    upsertOutbox(op.copy(deliveryState = "DISCARDED", resolution = "EXPLICIT_COPY_AS_NEW"))
                }
            }
        }
        if (getDraft(sourceBindingKey, TaskDataContract.DEFAULT_TABLE_NAME, sourceRowId)?.localRevision?.let { it <= sourceTodoRevision } == true) {
            deleteDraft(sourceBindingKey, TaskDataContract.DEFAULT_TABLE_NAME, sourceRowId)
        }
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

    // No trustworthy retention horizon: stale staged rows are not evidence of current remote presence.
    @Transaction
    suspend fun beginScanAtomic(): AccountSyncMetaEntity {
        val meta = getMeta() ?: AccountSyncMetaEntity()
        check(meta.scanGeneration < Long.MAX_VALUE) { "Scan generation exhausted" }
        clearStaging()
        return meta.copy(scanGeneration = meta.scanGeneration + 1L, scanState = "SCANNING",
            scanCheckpointUpdatedAt = null, scanCheckpointId = null).also { upsertMeta(it) }
    }

    @Transaction
    suspend fun commitPageAtomic(
        records: List<ServerBaseEntity>,
        checkpointUpdatedAt: String,
        checkpointId: String,
        schemaVersion: Long,
        generation: Long?,
        hasMore: Boolean,
        completedAt: Long
    ) {
        val meta = getMeta() ?: AccountSyncMetaEntity()
        check(schemaVersion in 1..dev.tabbyrush.shared.sync.MAX_EXACT_VERSION)
        if (generation != null) {
            check(meta.scanGeneration == generation && meta.scanState == "SCANNING")
            for (base in records) {
                val prior = getStagedRecord(base.rowId)
                if (prior != null && prior.serverRevision > base.serverRevision) continue
                upsertStaging(ScanStagingEntity(bindingKey = base.bindingKey, ownerBinding = base.ownerBinding, ownerUserId = base.ownerUserId,
                    tableName = base.tableName, rowId = base.rowId, serverRevision = base.serverRevision,
                    deleted = base.deleted, title = base.title, completed = base.completed, createdAt = base.createdAt,
                    rawJson = base.rawJson, syncedAt = base.syncedAt, scanGeneration = generation, projectionError = base.projectionError))
            }
            upsertMeta(meta.copy(hubSchemaVersion = maxOf(meta.hubSchemaVersion ?: 0L, schemaVersion),
                scanCheckpointUpdatedAt = checkpointUpdatedAt, scanCheckpointId = checkpointId,
                scanState = if (hasMore) "SCANNING" else "COMPLETE"))
        } else {
            for (base in records) {
                val current = getServerBase(base.rowId)
                if (current?.standardConfirmed == true && current.serverRevision > base.serverRevision) continue
                upsertServerBase(if (base.deleted) base.copy(parentTaskId = current?.parentTaskId) else base)
            }
            upsertMeta(meta.copy(hubSchemaVersion = maxOf(meta.hubSchemaVersion ?: 0L, schemaVersion),
                checkpointUpdatedAt = checkpointUpdatedAt, checkpointId = checkpointId,
                lastCompletedCatchUpEpochMs = if (hasMore) meta.lastCompletedCatchUpEpochMs else completedAt))
        }
    }

    @Transaction
    suspend fun promoteCompletedScanAtomic(generation: Long, completedAt: Long) {
        val meta = getMeta() ?: error("Missing scan metadata")
        check(meta.scanState == "COMPLETE" && meta.scanGeneration == generation)
        val staged = getAllStagedRecords().filter { it.scanGeneration == generation }
        val seen = staged.map { it.rowId }.toSet()
        for (row in staged) {
            val base = dev.tabbyrush.shared.sync.TaskJsonMerge.project(dev.tabbyrush.shared.sync.PullRecordDto(
                row.tableName, row.rowId, dev.tabbyrush.shared.sync.TaskJsonMerge.json.parseToJsonElement(requireNotNull(row.rawJson)),
                row.serverRevision, requireNotNull(row.syncedAt), row.deleted))
            val old = getServerBase(base.rowId)
            if (old?.standardConfirmed == true && old.serverRevision > base.serverRevision) continue
            upsertServerBase(if (base.deleted) base.copy(parentTaskId = old?.parentTaskId) else base)
        }
        for (old in getAllServerBase()) {
            if (old.rowId !in seen) upsertServerBase(old.copy(deleted = true, standardConfirmed = true,
                projectionError = "Absent after completed standard scan; retained locally, never recreated"))
        }
        upsertMeta(meta.copy(scanState = "READY", checkpointUpdatedAt = meta.scanCheckpointUpdatedAt,
            checkpointId = meta.scanCheckpointId, lastCompletedCatchUpEpochMs = completedAt))
        clearStaging()
    }
    @Transaction
    suspend fun promoteReconciliationAtomic(
        accessRevision: Long,
        scanGeneration: Long,
        completedAt: Long
    ) {
        val meta = getMeta() ?: error("Missing scan metadata")
        val stagedRecords = getStagedRecordsForGeneration(scanGeneration)
        val stagedAccess = getStagedAccessForGeneration(scanGeneration)

        for (staged in stagedRecords) {
            val base = ServerBaseEntity(
                bindingKey = staged.bindingKey,
                ownerBinding = staged.ownerBinding,
                ownerUserId = staged.ownerUserId,
                tableName = staged.tableName,
                rowId = staged.rowId,
                serverRevision = staged.serverRevision,
                deleted = staged.deleted,
                title = staged.title,
                completed = staged.completed,
                createdAt = staged.createdAt,
                updatedAt = completedAt,
                deadlineDate = staged.deadlineDate,
                deadlineTime = staged.deadlineTime,
                deadlineZone = staged.deadlineZone,
                deadlineOffsetSeconds = staged.deadlineOffsetSeconds,
                reminderAnchorTime = staged.reminderAnchorTime,
                deadlineAnchorEpochMs = staged.deadlineAnchorEpochMs,
                deadlineOverdueEpochMs = staged.deadlineOverdueEpochMs,
                reminderEnabled = staged.reminderEnabled,
                reminderLeadMinutes = staged.reminderLeadMinutes,
                reminderIntervalMinutes = staged.reminderIntervalMinutes,
                reminderCount = staged.reminderCount,
                scheduleToken = staged.scheduleToken,
                parentTaskId = staged.parentTaskId,
                rawJson = staged.rawJson,
                syncedAt = staged.syncedAt,
                standardConfirmed = true,
                projectionError = staged.projectionError
            )
            upsertServerBase(base)
        }

        for (stagedAcc in stagedAccess) {
            val access = TaskAccessEntity(
                ownerUserId = stagedAcc.ownerUserId,
                tableName = stagedAcc.tableName,
                rowId = stagedAcc.rowId,
                revision = stagedAcc.revision,
                status = stagedAcc.status,
                editContent = stagedAcc.editContent,
                manageAssignment = stagedAcc.manageAssignment,
                deleteTask = stagedAcc.deleteTask,
                createChild = stagedAcc.createChild,
                hierarchyCoverage = stagedAcc.hierarchyCoverage,
                updatedAtEpochMs = completedAt
            )
            upsertAccess(access)
        }

        clearStagingForGeneration(scanGeneration)
        clearStagedAccessForGeneration(scanGeneration)

        upsertMeta(
            meta.copy(
                accessRevision = accessRevision,
                scanState = "READY",
                checkpointUpdatedAt = meta.scanCheckpointUpdatedAt,
                checkpointId = meta.scanCheckpointId,
                lastCompletedCatchUpEpochMs = completedAt
            )
        )
    }

    @Transaction
    suspend fun purgeRevokedTaskAtomic(
        ownerUserId: String,
        rowId: String,
        evidence: AccessLossEvidenceEntity,
        accountKey: String,
        nowEpochMs: Long
    ) {
        val boundKey = boundBindingKey(ownerUserId)
        deleteServerBaseByOwnerAndRow(boundKey, rowId)
        deleteDraftByOwnerAndRow(boundKey, rowId)
        deleteStagedByOwnerAndRow(boundKey, rowId)
        deleteOutboxByOwnerAndRow(boundKey, rowId)
        upsertAccess(
            TaskAccessEntity(
                ownerUserId = ownerUserId,
                tableName = dev.tabbyrush.shared.contract.TaskDataContract.DEFAULT_TABLE_NAME,
                rowId = rowId,
                revision = 0L,
                status = dev.tabbyrush.shared.contract.TaskAccessStatus.Unavailable.name,
                editContent = false,
                manageAssignment = false,
                deleteTask = false,
                createChild = false,
                updatedAtEpochMs = nowEpochMs
            )
        )
        upsertAccessLossEvidence(evidence)
        cancelLedgerEntriesForTask(accountKey, boundKey, dev.tabbyrush.shared.contract.TaskDataContract.DEFAULT_TABLE_NAME, rowId, nowEpochMs)
        val meta = getMeta() ?: AccountSyncMetaEntity()
        upsertMeta(meta.copy(accessGeneration = meta.accessGeneration + 1L))
    }

    @Transaction
    suspend fun purgeAllSharedTasksAtomic(
        keepOwnerUserId: String,
        nowEpochMs: Long
    ) {
        val keepBoundKey = boundBindingKey(keepOwnerUserId)
        purgeSharedServerBase(keepBoundKey)
        purgeSharedDrafts(keepBoundKey)
        purgeSharedOutbox(keepBoundKey)
        purgeSharedStaging(keepBoundKey)
        clearAccessExceptOwner(keepOwnerUserId)
        val meta = getMeta() ?: AccountSyncMetaEntity()
        upsertMeta(meta.copy(accessGeneration = meta.accessGeneration + 1L))
    }

    @Transaction
    suspend fun translateLegacyAtomic(source: LegacySyncEvidenceEntity, op: OutboxEntity?, outcome: String) {
        val current = getLegacyEvidence().firstOrNull { it.sourceOpId == source.sourceOpId } ?: return
        if (current.translatedOpId != null || current.outcome != "PENDING_REVIEW") return
        if (op != null) upsertOutbox(op)
        upsertLegacyEvidence(current.copy(outcome = outcome, translatedOpId = op?.opId))
    }

    @Transaction
    suspend fun convergeAtomic(opId: String) {
        val op = getOutboxEntry(opId) ?: return
        if (op.deliveryState != OutboxEntity.STATE_UNCERTAIN) return
        upsertOutbox(op.copy(deliveryState = "CONVERGED", resolution = "DESIRED_STATE_CONVERGENCE_NOT_RECEIPT"))
        if (getDraft(op.rowId)?.localRevision == op.localRevision) deleteDraft(op.rowId)
    }
}
