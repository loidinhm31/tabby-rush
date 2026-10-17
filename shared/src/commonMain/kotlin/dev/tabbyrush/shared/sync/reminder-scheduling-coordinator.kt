package dev.tabbyrush.shared.sync

import dev.tabbyrush.shared.contract.AccountKey
import dev.tabbyrush.shared.contract.NativeReminderAdapter
import dev.tabbyrush.shared.contract.ScheduledAlarmHandle
import dev.tabbyrush.shared.data.ReminderOccurrenceStore
import dev.tabbyrush.shared.data.RoomAccountStore

/**
 * ReminderSchedulingCoordinator manages the per-account reminder lifecycle.
 * Coordinates reactive persisted schedule observation, OS alarm reconciliation,
 * and fail-closed claim-before-post delivery execution under the ReminderDeliveryGate.
 */
class ReminderSchedulingCoordinator(
    val accountKey: AccountKey,
    val store: RoomAccountStore,
    val occurrenceStore: ReminderOccurrenceStore,
    val adapter: NativeReminderAdapter,
    val deliveryGate: ReminderDeliveryGate,
    val getGeneration: suspend () -> Long,
    val hasWipeMarker: suspend () -> Boolean,
    val clock: () -> Long = { System.currentTimeMillis() }
) : LeaveSchedulerCancelGate {

    init {
        store.deliveryGate = deliveryGate
    }

    /**
     * Reconciles current persisted schedule with OS alarm scheduler and removes
     * obsolete posted notifications.
     */
    suspend fun reconcile(nowEpochMs: Long = clock()) {
        if (deliveryGate.getState() != DeliveryGateState.OPEN || hasWipeMarker()) {
            return // Gate closed or account wiping: fail closed
        }

        deliveryGate.withMutationAdmission {
            if (hasWipeMarker()) return@withMutationAdmission

            val generation = try {
                getGeneration()
            } catch (_: Exception) {
                return@withMutationAdmission
            }

            val schedules = store.getPersistedScheduleProjection()
            val delta = occurrenceStore.syncPlannedOccurrences(generation, schedules, nowEpochMs)

            val handles = delta.alarmsToSchedule.map {
                ScheduledAlarmHandle(
                    accountKey = it.accountKey,
                    generation = it.generation,
                    taskId = it.taskId,
                    scheduleToken = it.scheduleToken,
                    occurrenceIndex = it.occurrenceIndex,
                    triggerAtEpochMs = it.dueEpochMs
                )
            }

            adapter.reconcileSchedule(accountKey, generation, handles)

            for (obsoleteId in delta.obsoletePlatformNotificationIdsToRemove) {
                adapter.removeOwnedNotification(accountKey, obsoleteId)
                occurrenceStore.clearPostedNotificationId(generation, obsoleteId, nowEpochMs)
            }
        }
    }

    /**
     * Executes claim-before-post notification delivery when an alarm callback fires.
     * Enforces:
     * 1. Delivery gate admission (fail closed if closing, closed, or wiped).
     * 2. Capability verification.
     * 3. Current persisted task eligibility re-check & fresh title fetch.
     * 4. Atomic claim in SQLite before native post.
     * 5. Pre-post wipe marker re-check.
     * 6. Native post followed by outcome recording.
     */
    suspend fun deliverReminder(
        taskId: String,
        scheduleToken: String,
        occurrenceIndex: Int,
        triggerGeneration: Long,
        nowEpochMs: Long = clock()
    ): Boolean {
        return deliveryGate.withDeliveryAdmission(expectedGeneration = triggerGeneration) {
            if (hasWipeMarker()) return@withDeliveryAdmission false

            val caps = adapter.queryCapabilities()
            if (!caps.isDeliveryPermitted) return@withDeliveryAdmission false

            // Re-read current persisted task projection (fresh content and eligibility)
            val schedules = store.getPersistedScheduleProjection()
            val taskSchedule = schedules.find { it.taskId == taskId }
            if (taskSchedule == null || !taskSchedule.isEligibleForScheduling || taskSchedule.scheduleToken != scheduleToken) {
                // Task was deleted, completed, disabled, or token changed: cancel and do not post
                occurrenceStore.syncPlannedOccurrences(triggerGeneration, schedules, nowEpochMs)
                return@withDeliveryAdmission false
            }

            val title = taskSchedule.title

            // Atomic claim-before-post in SQLite
            val claimed = occurrenceStore.evaluateAndClaimDueOpportunity(
                generation = triggerGeneration,
                taskId = taskId,
                scheduleToken = scheduleToken,
                nowEpochMs = nowEpochMs
            ) ?: return@withDeliveryAdmission false

            // Pre-post re-check outside DB transaction
            if (hasWipeMarker() || deliveryGate.getState() != DeliveryGateState.OPEN) {
                return@withDeliveryAdmission false
            }
            val prePostSchedule = store.getPersistedScheduleProjection().find { it.taskId == taskId }
            if (prePostSchedule == null || !prePostSchedule.isEligibleForScheduling || prePostSchedule.scheduleToken != scheduleToken) {
                return@withDeliveryAdmission false
            }

            // Native post
            val platformId = adapter.postOwnedNotification(
                accountKey = accountKey,
                generation = triggerGeneration,
                taskId = taskId,
                scheduleToken = scheduleToken,
                occurrenceIndex = claimed.index,
                title = title,
                dueEpochMs = claimed.dueEpochMs
            )

            if (platformId != null) {
                occurrenceStore.recordPosted(
                    generation = triggerGeneration,
                    taskId = taskId,
                    scheduleToken = scheduleToken,
                    occurrenceIndex = claimed.index,
                    platformNotificationId = platformId,
                    nowEpochMs = nowEpochMs
                )
                true
            } else {
                // Posting failed/blocked: retained as claimed/attempted, no retry
                false
            }
        } ?: false
    }

    /**
     * Implements LeaveSchedulerCancelGate to cancel all alarms and posted notifications
     * on account leave or teardown.
     */
    override suspend fun cancelAndAwait(accountKey: AccountKey) {
        adapter.cancelAllForAccount(accountKey)
    }
}
