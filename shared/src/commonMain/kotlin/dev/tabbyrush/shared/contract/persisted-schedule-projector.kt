package dev.tabbyrush.shared.contract

/**
 * PersistedScheduleProjector computes draft-free schedule projections from
 * atomic contract snapshots of server_base, outbox entries, and access records.
 *
 * Invariants:
 * - Operates purely on immutable [PersistedProjectionInputs] contracts (zero Room/SQLite imports).
 * - Identifies and keys tasks by canonical [TaskRef] (ownerUserId, rowId).
 * - Filters out non-todo tables (e.g. taskAssignees relation records) and projection-error records.
 * - Suppresses revoked or unavailable tasks based on [PersistedAccessRecord].
 * - Completely excludes uncommitted UI/editor drafts.
 */
object PersistedScheduleProjector {

    fun computeProjection(inputs: PersistedProjectionInputs): List<PersistedTaskSchedule> {
        return computeProjection(inputs.serverBases, inputs.outboxEntries, inputs.accessRecords)
    }

    fun computeProjection(
        serverBases: List<PersistedBaseRecord>,
        outboxEntries: List<PersistedOutboxRecord>,
        accessRecords: List<PersistedAccessRecord> = emptyList()
    ): List<PersistedTaskSchedule> {
        val filteredBases = serverBases.filter {
            it.tableName == TaskDataContract.DEFAULT_TABLE_NAME && it.projectionError == null
        }
        val filteredOutbox = outboxEntries.filter {
            it.tableName == TaskDataContract.DEFAULT_TABLE_NAME
        }
        val accessMap = accessRecords
            .filter { it.tableName == TaskDataContract.DEFAULT_TABLE_NAME }
            .associateBy { it.taskRef }

        val baseMap = filteredBases.associateBy { it.taskRef }
        val outboxByRef = filteredOutbox.groupBy { it.taskRef }
        val allRefs = (baseMap.keys + outboxByRef.keys).toSet()

        data class IntermediateSchedule(
            val taskRef: TaskRef,
            var title: String,
            var rawCompleted: Boolean,
            var isDeleted: Boolean,
            var deadline: TaskDeadline?,
            var reminderRule: ReminderRule,
            var scheduleToken: String?,
            val hasUnresolvedConflict: Boolean,
            var parentTaskRef: TaskRef?,
            val hasUnresolvedPreservationBarrier: Boolean,
            val isAccessible: Boolean
        )

        val intermediateList = ArrayList<IntermediateSchedule>(allRefs.size)

        for (taskRef in allRefs) {
            val base = baseMap[taskRef]
            val access = accessMap[taskRef]

            // If access is recorded as unavailable, the task is excluded from active schedule projection
            val isAccessible = access?.status != TaskAccessStatus.Unavailable

            val rowOps = (outboxByRef[taskRef] ?: emptyList()).sortedBy { it.localRevision }

            // Check for unresolved conflict head
            val headOp = rowOps.firstOrNull()
            val hasUnresolvedConflict = headOp != null && (
                headOp.deliveryState == OutboxDeliveryStates.CONFLICT ||
                headOp.deliveryState == OutboxDeliveryStates.REVIEW
            )

            val hasPreservationBarrier = rowOps.any {
                it.intentMask?.contains(OutboxIntentMasks.LAST_CHILD_PRESERVATION) == true
            }

            // Initialize from server_base
            var title = base?.title ?: ""
            var completed = base?.completed ?: false
            var isDeleted = base?.deleted ?: false
            var parentTaskRef = base?.parentTaskId?.let { TaskRef(taskRef.ownerUserId, it) }

            var deadline: TaskDeadline? = if (
                base?.deadlineDate != null &&
                base.deadlineZone != null &&
                base.deadlineOffsetSeconds != null &&
                base.deadlineAnchorEpochMs != null &&
                base.deadlineOverdueEpochMs != null
            ) {
                TaskDeadline(
                    date = base.deadlineDate!!,
                    time = base.deadlineTime,
                    zone = base.deadlineZone!!,
                    offsetSeconds = base.deadlineOffsetSeconds!!,
                    reminderAnchorTime = base.reminderAnchorTime,
                    anchorEpochMs = base.deadlineAnchorEpochMs!!,
                    overdueEpochMs = base.deadlineOverdueEpochMs!!
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
                    parentTaskRef = TaskRef(taskRef.ownerUserId, op.parentTaskId!!)
                }
                when (op.kind) {
                    TaskOperationKind.CREATE.wireName -> {
                        title = op.title ?: title
                        completed = op.completed ?: completed
                        isDeleted = false

                        if (op.deadlineDate != null && op.deadlineZone != null && op.deadlineOffsetSeconds != null && op.deadlineAnchorEpochMs != null && op.deadlineOverdueEpochMs != null) {
                            deadline = TaskDeadline(
                                date = op.deadlineDate!!,
                                time = op.deadlineTime,
                                zone = op.deadlineZone!!,
                                offsetSeconds = op.deadlineOffsetSeconds!!,
                                reminderAnchorTime = op.reminderAnchorTime,
                                anchorEpochMs = op.deadlineAnchorEpochMs!!,
                                overdueEpochMs = op.deadlineOverdueEpochMs!!
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
                    TaskOperationKind.DELETE.wireName -> {
                        isDeleted = true
                    }
                    else -> { // UPDATE
                        val mask = op.intentMask
                        if (mask != null) {
                            if (mask.contains(OutboxIntentMasks.DELETE)) isDeleted = true
                            if (mask.contains(OutboxIntentMasks.TITLE)) title = op.title ?: title
                            if (mask.contains(OutboxIntentMasks.COMPLETED)) completed = op.completed ?: completed
                            if (mask.contains(OutboxIntentMasks.PARENT) && op.parentTaskId != null) {
                                parentTaskRef = TaskRef(taskRef.ownerUserId, op.parentTaskId!!)
                            }
                            if (mask.contains(OutboxIntentMasks.DEADLINE)) {
                                if (op.deadlineDate != null && op.deadlineZone != null && op.deadlineOffsetSeconds != null && op.deadlineAnchorEpochMs != null && op.deadlineOverdueEpochMs != null) {
                                    deadline = TaskDeadline(
                                        date = op.deadlineDate!!,
                                        time = op.deadlineTime,
                                        zone = op.deadlineZone!!,
                                        offsetSeconds = op.deadlineOffsetSeconds!!,
                                        reminderAnchorTime = op.reminderAnchorTime,
                                        anchorEpochMs = op.deadlineAnchorEpochMs!!,
                                        overdueEpochMs = op.deadlineOverdueEpochMs!!
                                    )
                                    scheduleToken = op.scheduleToken
                                } else {
                                    deadline = null
                                    scheduleToken = null
                                    reminderRule = ReminderRule.DISABLED
                                }
                            }
                            if (mask.contains(OutboxIntentMasks.REMINDER)) {
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
                            if (op.title != null) title = op.title!!
                            if (op.completed != null) completed = op.completed!!
                            if (op.parentTaskId != null) parentTaskRef = TaskRef(taskRef.ownerUserId, op.parentTaskId!!)
                            if (op.deadlineDate != null && op.deadlineZone != null && op.deadlineOffsetSeconds != null && op.deadlineAnchorEpochMs != null && op.deadlineOverdueEpochMs != null) {
                                deadline = TaskDeadline(
                                    date = op.deadlineDate!!,
                                    time = op.deadlineTime,
                                    zone = op.deadlineZone!!,
                                    offsetSeconds = op.deadlineOffsetSeconds!!,
                                    reminderAnchorTime = op.reminderAnchorTime,
                                    anchorEpochMs = op.deadlineAnchorEpochMs!!,
                                    overdueEpochMs = op.deadlineOverdueEpochMs!!
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

            // Confirmed tombstones dominate every retained/review overlay
            if (base?.deleted == true) isDeleted = true

            intermediateList.add(
                IntermediateSchedule(
                    taskRef = taskRef,
                    title = title,
                    rawCompleted = completed,
                    isDeleted = isDeleted,
                    deadline = deadline,
                    reminderRule = reminderRule,
                    scheduleToken = scheduleToken,
                    hasUnresolvedConflict = hasUnresolvedConflict,
                    parentTaskRef = parentTaskRef,
                    hasUnresolvedPreservationBarrier = hasPreservationBarrier,
                    isAccessible = isAccessible
                )
            )
        }

        val intermediateTodos = intermediateList.map { item ->
            Todo(
                taskRef = item.taskRef,
                title = item.title,
                completed = item.rawCompleted,
                createdAt = 1000L,
                isDeleted = item.isDeleted,
                deadline = item.deadline,
                reminderRule = item.reminderRule,
                scheduleToken = item.scheduleToken,
                parentTaskRef = item.parentTaskRef,
                accessState = accessMap[item.taskRef]?.let {
                    TaskAccessState(
                        ref = item.taskRef,
                        revision = 1L,
                        capabilities = it.capabilities,
                        coverage = it.coverage,
                        status = it.status
                    )
                }
            )
        }

        val hierarchySnapshot = TaskHierarchyProjector.projectHierarchy(intermediateTodos, TodoFilter.ALL)
        val validItemsByRef = hierarchySnapshot.items.associateBy { it.todo.taskRef }

        val result = ArrayList<PersistedTaskSchedule>(intermediateList.size)
        for (item in intermediateList) {
            val hierarchicalItem = validItemsByRef[item.taskRef]
            val isDeleted = item.isDeleted || hierarchicalItem == null
            val completed = hierarchicalItem?.effectiveCompleted ?: item.rawCompleted

            result.add(
                PersistedTaskSchedule(
                    taskRef = item.taskRef,
                    title = item.title,
                    completed = completed,
                    isDeleted = isDeleted,
                    deadline = item.deadline,
                    reminderRule = item.reminderRule,
                    scheduleToken = item.scheduleToken,
                    hasUnresolvedConflict = item.hasUnresolvedConflict,
                    parentTaskRef = item.parentTaskRef,
                    hasUnresolvedPreservationBarrier = item.hasUnresolvedPreservationBarrier,
                    isAccessible = item.isAccessible
                )
            )
        }

        return result
    }
}
