package dev.doggo.shared.contract

import dev.doggo.shared.data.OutboxEntity
import dev.doggo.shared.data.ServerBaseEntity

/**
 * PersistedScheduleProjector computes draft-free schedule projections from
 * atomic snapshots of server_base and outbox entries.
 */
object PersistedScheduleProjector {

    fun computeProjection(
        serverBases: List<ServerBaseEntity>,
        outboxEntries: List<OutboxEntity>
    ): List<PersistedTaskSchedule> {
        val baseMap = serverBases.associateBy { it.rowId }
        val outboxByRow = outboxEntries.groupBy { it.rowId }
        val allRowIds = (baseMap.keys + outboxByRow.keys).toSet()

        val result = ArrayList<PersistedTaskSchedule>(allRowIds.size)

        for (rowId in allRowIds) {
            val base = baseMap[rowId]
            val rowOps = (outboxByRow[rowId] ?: emptyList()).sortedBy { it.localRevision }

            // Check for unresolved conflict head
            val headOp = rowOps.firstOrNull()
            val hasUnresolvedConflict = headOp != null && (
                headOp.deliveryState == OutboxEntity.STATE_CONFLICT ||
                headOp.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED
            )

            // Initialize from server_base
            var title = base?.title ?: ""
            var completed = base?.completed ?: false
            var isDeleted = base?.deleted ?: false

            var deadline: TaskDeadline? = if (
                base?.deadlineDate != null &&
                base.deadlineZone != null &&
                base.deadlineOffsetSeconds != null &&
                base.deadlineAnchorEpochMs != null &&
                base.deadlineOverdueEpochMs != null
            ) {
                TaskDeadline(
                    date = base.deadlineDate,
                    time = base.deadlineTime,
                    zone = base.deadlineZone,
                    offsetSeconds = base.deadlineOffsetSeconds,
                    reminderAnchorTime = base.reminderAnchorTime,
                    anchorEpochMs = base.deadlineAnchorEpochMs,
                    overdueEpochMs = base.deadlineOverdueEpochMs
                )
            } else null

            var reminderRule: ReminderRule = if (deadline != null && base?.reminderEnabled == true) {
                ReminderRule(
                    enabled = true,
                    leadMinutes = base.reminderLeadMinutes,
                    intervalMinutes = base.reminderIntervalMinutes,
                    count = base.reminderCount
                )
            } else {
                ReminderRule.DISABLED
            }

            var scheduleToken = base?.scheduleToken

            // Overlay submitted intentions
            for (op in rowOps) {
                when (op.kind) {
                    StrictOperationKind.CREATE.wireName -> {
                        title = op.title ?: title
                        completed = op.completed ?: completed
                        isDeleted = false

                        if (op.deadlineDate != null && op.deadlineZone != null && op.deadlineOffsetSeconds != null && op.deadlineAnchorEpochMs != null && op.deadlineOverdueEpochMs != null) {
                            deadline = TaskDeadline(
                                date = op.deadlineDate,
                                time = op.deadlineTime,
                                zone = op.deadlineZone,
                                offsetSeconds = op.deadlineOffsetSeconds,
                                reminderAnchorTime = op.reminderAnchorTime,
                                anchorEpochMs = op.deadlineAnchorEpochMs,
                                overdueEpochMs = op.deadlineOverdueEpochMs
                            )
                            scheduleToken = op.scheduleToken
                            reminderRule = if (op.reminderEnabled) {
                                ReminderRule(
                                    enabled = true,
                                    leadMinutes = op.reminderLeadMinutes,
                                    intervalMinutes = op.reminderIntervalMinutes,
                                    count = op.reminderCount
                                )
                            } else ReminderRule.DISABLED
                        } else {
                            deadline = null
                            scheduleToken = null
                            reminderRule = ReminderRule.DISABLED
                        }
                    }
                    StrictOperationKind.DELETE.wireName -> {
                        isDeleted = true
                    }
                    else -> { // UPDATE
                        val mask = op.intentMask
                        if (mask != null) {
                            if (mask.contains(OutboxEntity.INTENT_DELETE)) isDeleted = true
                            if (mask.contains(OutboxEntity.INTENT_TITLE)) title = op.title ?: title
                            if (mask.contains(OutboxEntity.INTENT_COMPLETED)) completed = op.completed ?: completed
                            if (mask.contains(OutboxEntity.INTENT_DEADLINE)) {
                                if (op.deadlineDate != null && op.deadlineZone != null && op.deadlineOffsetSeconds != null && op.deadlineAnchorEpochMs != null && op.deadlineOverdueEpochMs != null) {
                                    deadline = TaskDeadline(
                                        date = op.deadlineDate,
                                        time = op.deadlineTime,
                                        zone = op.deadlineZone,
                                        offsetSeconds = op.deadlineOffsetSeconds,
                                        reminderAnchorTime = op.reminderAnchorTime,
                                        anchorEpochMs = op.deadlineAnchorEpochMs,
                                        overdueEpochMs = op.deadlineOverdueEpochMs
                                    )
                                    scheduleToken = op.scheduleToken
                                } else {
                                    deadline = null
                                    scheduleToken = null
                                    reminderRule = ReminderRule.DISABLED
                                }
                            }
                            if (mask.contains(OutboxEntity.INTENT_REMINDER)) {
                                reminderRule = if (deadline != null && op.reminderEnabled) {
                                    ReminderRule(
                                        enabled = true,
                                        leadMinutes = op.reminderLeadMinutes,
                                        intervalMinutes = op.reminderIntervalMinutes,
                                        count = op.reminderCount
                                    )
                                } else ReminderRule.DISABLED
                            }
                        } else {
                            if (op.title != null) title = op.title
                            if (op.completed != null) completed = op.completed
                            if (op.deadlineDate != null && op.deadlineZone != null && op.deadlineOffsetSeconds != null && op.deadlineAnchorEpochMs != null && op.deadlineOverdueEpochMs != null) {
                                deadline = TaskDeadline(
                                    date = op.deadlineDate,
                                    time = op.deadlineTime,
                                    zone = op.deadlineZone,
                                    offsetSeconds = op.deadlineOffsetSeconds,
                                    reminderAnchorTime = op.reminderAnchorTime,
                                    anchorEpochMs = op.deadlineAnchorEpochMs,
                                    overdueEpochMs = op.deadlineOverdueEpochMs
                                )
                                scheduleToken = op.scheduleToken ?: scheduleToken
                                if (op.reminderEnabled) {
                                    reminderRule = ReminderRule(
                                        enabled = true,
                                        leadMinutes = op.reminderLeadMinutes,
                                        intervalMinutes = op.reminderIntervalMinutes,
                                        count = op.reminderCount
                                    )
                                }
                            }
                        }
                    }
                }
            }

            result.add(
                PersistedTaskSchedule(
                    taskId = rowId,
                    title = title,
                    completed = completed,
                    isDeleted = isDeleted,
                    deadline = deadline,
                    reminderRule = reminderRule,
                    scheduleToken = scheduleToken,
                    hasUnresolvedConflict = hasUnresolvedConflict
                )
            )
        }

        return result
    }
}
