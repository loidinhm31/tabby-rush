package dev.tabbyrush.shared.contract

import dev.tabbyrush.shared.data.OutboxEntity
import dev.tabbyrush.shared.data.ServerBaseEntity

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

        data class IntermediateSchedule(
            val rowId: String,
            var title: String,
            var rawCompleted: Boolean,
            var isDeleted: Boolean,
            var deadline: TaskDeadline?,
            var reminderRule: ReminderRule,
            var scheduleToken: String?,
            val hasUnresolvedConflict: Boolean,
            var parentTaskId: String?,
            val hasUnresolvedPreservationBarrier: Boolean
        )

        val intermediateList = ArrayList<IntermediateSchedule>(allRowIds.size)

        for (rowId in allRowIds) {
            val base = baseMap[rowId]
            val rowOps = (outboxByRow[rowId] ?: emptyList()).sortedBy { it.localRevision }

            // Check for unresolved conflict head
            val headOp = rowOps.firstOrNull()
            val hasUnresolvedConflict = headOp != null && (
                headOp.deliveryState == OutboxEntity.STATE_CONFLICT ||
                headOp.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED
            )

            val hasPreservationBarrier = rowOps.any {
                it.intentMask?.contains(OutboxEntity.INTENT_LAST_CHILD_PRESERVATION) == true
            }

            // Initialize from server_base
            var title = base?.title ?: ""
            var completed = base?.completed ?: false
            var isDeleted = base?.deleted ?: false
            var parentTaskId = base?.parentTaskId

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
                if (op.parentTaskId != null) {
                    parentTaskId = op.parentTaskId
                }
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
                            if (mask.contains(OutboxEntity.INTENT_PARENT) && op.parentTaskId != null) {
                                parentTaskId = op.parentTaskId
                            }
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
                                if (deadline != null && op.scheduleToken != null) {
                                    scheduleToken = op.scheduleToken
                                }
                            }
                        } else {
                            if (op.title != null) title = op.title
                            if (op.completed != null) completed = op.completed
                            if (op.parentTaskId != null) parentTaskId = op.parentTaskId
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

            intermediateList.add(
                IntermediateSchedule(
                    rowId = rowId,
                    title = title,
                    rawCompleted = completed,
                    isDeleted = isDeleted,
                    deadline = deadline,
                    reminderRule = reminderRule,
                    scheduleToken = scheduleToken,
                    hasUnresolvedConflict = hasUnresolvedConflict,
                    parentTaskId = parentTaskId,
                    hasUnresolvedPreservationBarrier = hasPreservationBarrier
                )
            )
        }

        val intermediateTodos = intermediateList.map { item ->
            Todo(
                id = item.rowId,
                title = item.title,
                completed = item.rawCompleted,
                createdAt = 1000L,
                isDeleted = item.isDeleted,
                deadline = item.deadline,
                reminderRule = item.reminderRule,
                scheduleToken = item.scheduleToken,
                parentTaskId = item.parentTaskId
            )
        }

        val hierarchySnapshot = TaskHierarchyProjector.projectHierarchy(intermediateTodos, TodoFilter.ALL)
        val validItemsById = hierarchySnapshot.items.associateBy { it.todo.id }

        val result = ArrayList<PersistedTaskSchedule>(intermediateList.size)
        for (item in intermediateList) {
            val hierarchicalItem = validItemsById[item.rowId]
            val isDeleted = item.isDeleted || hierarchicalItem == null
            val completed = hierarchicalItem?.effectiveCompleted ?: item.rawCompleted

            result.add(
                PersistedTaskSchedule(
                    taskId = item.rowId,
                    title = item.title,
                    completed = completed,
                    isDeleted = isDeleted,
                    deadline = item.deadline,
                    reminderRule = item.reminderRule,
                    scheduleToken = item.scheduleToken,
                    hasUnresolvedConflict = item.hasUnresolvedConflict,
                    parentTaskId = item.parentTaskId,
                    hasUnresolvedPreservationBarrier = item.hasUnresolvedPreservationBarrier
                )
            )
        }

        return result
    }
}
