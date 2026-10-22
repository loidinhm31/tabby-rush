package dev.tabbyrush.shared.data

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.CalculatedReminderOccurrence
import dev.tabbyrush.shared.contract.PersistedTaskSchedule
import dev.tabbyrush.shared.contract.ReminderScheduleCalculator

/**
 * ReminderOccurrenceStore manages the lifecycle of reminder_delivery_ledger entries.
 * Enforces stable occurrence identity, idempotent insertion, 5-minute grace evaluation,
 * atomic claim-before-post, and completion/cancellation barriers.
 */
class ReminderOccurrenceStore(
    val accountKey: AccountKey,
    val dao: TabbyRushDao
) {

    suspend fun syncPlannedOccurrences(
        generation: Long,
        schedules: List<PersistedTaskSchedule>,
        nowEpochMs: Long
    ): ScheduleReconciliationDelta {
        val existingEntries = dao.getAllLedgerEntries()
            .filter { it.accountKey == accountKey.toString() && it.generation == generation }
        val existingByKey = existingEntries.associateBy {
            "${it.bindingKey}:${it.tableName}:${it.taskId}:${it.scheduleToken}:${it.occurrenceIndex}"
        }

        val entriesToUpsert = mutableListOf<ReminderDeliveryLedgerEntity>()
        val activeEligibleTaskTokens = mutableMapOf<String, String>() // "${bindingKey}:${tableName}:${taskId}" -> scheduleToken

        for (schedule in schedules) {
            val boundKey = boundBindingKey(schedule.ownerUserId)
            val tableName = dev.tabbyrush.shared.contract.TaskDataContract.DEFAULT_TABLE_NAME
            val taskMapKey = "$boundKey:$tableName:${schedule.taskId}"

            if (!schedule.isEligibleForScheduling) {
                // Ineligible: cancel active ledger entries for this task
                dao.cancelLedgerEntriesForTask(accountKey.toString(), generation, boundKey, tableName, schedule.taskId, nowEpochMs)
                continue
            }

            val deadline = schedule.deadline ?: continue
            val token = schedule.scheduleToken ?: continue
            activeEligibleTaskTokens[taskMapKey] = token

            val calculated = ReminderScheduleCalculator.calculateOccurrences(deadline, schedule.reminderRule)
            for (occ in calculated) {
                val key = "$boundKey:$tableName:${schedule.taskId}:$token:${occ.index}"
                val existing = existingByKey[key]

                if (existing == null) {
                    // New occurrence: start PENDING and ACTIVE
                    entriesToUpsert.add(
                        ReminderDeliveryLedgerEntity(
                            accountKey = accountKey.toString(),
                            generation = generation,
                            bindingKey = boundKey,
                            ownerBinding = BINDING_BOUND,
                            ownerUserId = schedule.ownerUserId,
                            tableName = tableName,
                            taskId = schedule.taskId,
                            scheduleToken = token,
                            occurrenceIndex = occ.index,
                            dueEpochMs = occ.dueEpochMs,
                            claimOutcome = ReminderDeliveryLedgerEntity.OUTCOME_PENDING,
                            cancellationState = ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE,
                            updatedAtEpochMs = nowEpochMs
                        )
                    )
                } else if (existing.cancellationState == ReminderDeliveryLedgerEntity.CANCELLATION_CANCELLED) {
                    // Reopened task: reactivate while preserving prior consumed outcome!
                    entriesToUpsert.add(
                        existing.copy(
                            cancellationState = ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE,
                            updatedAtEpochMs = nowEpochMs
                        )
                    )
                }
            }
        }

        if (entriesToUpsert.isNotEmpty()) {
            dao.upsertLedgerEntries(entriesToUpsert)
        }

        // Cancel any occurrences belonging to older schedule tokens or orphan tasks
        val allScheduleKeys = schedules.map {
            "${boundBindingKey(it.ownerUserId)}:${dev.tabbyrush.shared.contract.TaskDataContract.DEFAULT_TABLE_NAME}:${it.taskId}"
        }.toSet()
        for (entry in existingEntries) {
            val entryKey = "${entry.bindingKey}:${entry.tableName}:${entry.taskId}"
            val activeToken = activeEligibleTaskTokens[entryKey]
            val isOrphanTask = !allScheduleKeys.contains(entryKey)
            val isStaleToken = activeToken != null && entry.scheduleToken != activeToken
            if ((isOrphanTask || isStaleToken) && entry.cancellationState == ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE) {
                dao.upsertLedgerEntry(
                    entry.copy(
                        cancellationState = ReminderDeliveryLedgerEntity.CANCELLATION_CANCELLED,
                        updatedAtEpochMs = nowEpochMs
                    )
                )
            }
        }

        // Compute delta: future active pending occurrences
        val latestLedger = dao.getAllLedgerEntries()
            .filter { it.accountKey == accountKey.toString() && it.generation == generation }
        val alarmsToSchedule = latestLedger.filter {
            it.cancellationState == ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE &&
            it.claimOutcome == ReminderDeliveryLedgerEntity.OUTCOME_PENDING &&
            it.dueEpochMs > nowEpochMs
        }

        // Obsolete notifications to remove (posted notifications whose task is cancelled)
        val obsoleteNotificationIds = latestLedger.filter {
            it.cancellationState == ReminderDeliveryLedgerEntity.CANCELLATION_CANCELLED &&
            it.claimOutcome == ReminderDeliveryLedgerEntity.OUTCOME_POSTED &&
            it.postedPlatformId != null
        }.mapNotNull { it.postedPlatformId }

        return ScheduleReconciliationDelta(
            alarmsToSchedule = alarmsToSchedule,
            obsoletePlatformNotificationIdsToRemove = obsoleteNotificationIds
        )
    }

    /**
     * Evaluates a task for delivery under the 5-minute grace window.
     * If eligible, atomically claims the latest opportunity and marks older unconsumed occurrences as SKIPPED.
     * Returns the claimed occurrence index and due instant if claimed, or null if ineligible/already consumed.
     */
    suspend fun evaluateAndClaimDueOpportunity(
        generation: Long,
        taskId: String,
        scheduleToken: String,
        nowEpochMs: Long
    ): CalculatedReminderOccurrence? {
        val entries = dao.getLedgerEntriesForTask(accountKey.toString(), generation, taskId)
            .filter { it.scheduleToken == scheduleToken && it.cancellationState == ReminderDeliveryLedgerEntity.CANCELLATION_ACTIVE }

        if (entries.isEmpty()) return null

        val calculated = entries.map {
            CalculatedReminderOccurrence(index = it.occurrenceIndex, dueEpochMs = it.dueEpochMs)
        }
        val consumedIndices = entries.filter {
            it.claimOutcome == ReminderDeliveryLedgerEntity.OUTCOME_CLAIMED ||
            it.claimOutcome == ReminderDeliveryLedgerEntity.OUTCOME_POSTED
        }.map { it.occurrenceIndex }.toSet()

        val graceResult = ReminderScheduleCalculator.evaluateGrace(calculated, consumedIndices, nowEpochMs)

        // Mark skipped indices in DB
        if (graceResult.skippedIndices.isNotEmpty()) {
            dao.markOccurrencesSkippedAtomic(
                accountKey = accountKey.toString(),
                generation = generation,
                taskId = taskId,
                scheduleToken = scheduleToken,
                indices = graceResult.skippedIndices,
                updatedAtEpochMs = nowEpochMs
            )
        }

        val eligible = graceResult.eligibleOpportunity ?: return null

        // Atomically claim the eligible occurrence
        val claimed = dao.claimOccurrenceAtomic(
            accountKey = accountKey.toString(),
            generation = generation,
            taskId = taskId,
            scheduleToken = scheduleToken,
            occurrenceIndex = eligible.index,
            claimTimeEpochMs = nowEpochMs
        )

        return if (claimed) eligible else null
    }

    suspend fun recordPosted(
        generation: Long,
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int,
        platformNotificationId: String,
        nowEpochMs: Long
    ): Boolean {
        return dao.recordPostedAtomic(
            accountKey = accountKey.toString(),
            generation = generation,
            taskId = taskId,
            scheduleToken = scheduleToken,
            occurrenceIndex = occurrenceIndex,
            platformNotificationId = platformNotificationId,
            postTimeEpochMs = nowEpochMs
        )
    }

    suspend fun getActivePostedNotifications(generation: Long): List<ReminderDeliveryLedgerEntity> {
        return dao.getActivePostedNotifications(accountKey.toString(), generation)
    }
    suspend fun clearPostedNotificationId(
        generation: Long,
        platformNotificationId: String,
        nowEpochMs: Long
    ) {
        dao.clearPostedNotificationId(accountKey.toString(), generation, platformNotificationId, nowEpochMs)
    }
}
