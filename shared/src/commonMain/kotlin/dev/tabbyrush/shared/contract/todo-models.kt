package dev.tabbyrush.shared.contract

enum class TodoFilter {
    ALL,
    ACTIVE,
    COMPLETED
}

data class TaskDeadline(
    val date: String,
    val time: String? = null,
    val zone: String,
    val offsetSeconds: Long,
    val reminderAnchorTime: String? = null,
    val anchorEpochMs: Long,
    val overdueEpochMs: Long
) {
    init {
        require(date.isNotEmpty()) { "deadline date must not be empty" }
        require(zone.isNotEmpty()) { "deadline zone must not be empty" }
        if (time == null) {
            requireNotNull(reminderAnchorTime) { "reminderAnchorTime required for date-only deadline" }
        } else {
            require(reminderAnchorTime == null) { "reminderAnchorTime must be null for timed deadline" }
            require(overdueEpochMs == anchorEpochMs) {
                "deadline overdueEpochMs must equal anchorEpochMs for timed deadline"
            }
        }
    }
}

data class ReminderRule(
    val enabled: Boolean = false,
    val leadMinutes: Long = 0L,
    val intervalMinutes: Long? = null,
    val count: Long = 1L
) {
    init {
        require(leadMinutes >= 0) { "leadMinutes must be non-negative" }
        require(count in StrictSyncV2Contract.MIN_REMINDER_COUNT..StrictSyncV2Contract.MAX_REMINDER_COUNT) {
            "reminder count must be between 1 and 10"
        }
        if (count == 1L) {
            require(intervalMinutes == null) { "intervalMinutes must be null when count is 1" }
        } else {
            require(intervalMinutes != null && intervalMinutes >= StrictSyncV2Contract.MIN_REPEAT_INTERVAL_MINUTES) {
                "repeating intervalMinutes must be >= 15"
            }
        }
    }

    companion object {
        val DISABLED = ReminderRule(enabled = false, leadMinutes = 0L, intervalMinutes = null, count = 1L)
    }
}

data class Todo(
    val id: String,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val serverRevision: Long = 0,
    val isPending: Boolean = false,
    val isDeleted: Boolean = false,
    val deadline: TaskDeadline? = null,
    val reminderRule: ReminderRule = ReminderRule.DISABLED,
    val scheduleToken: String? = null,
    val parentTaskId: String? = null
) {
    init {
        require(createdAt >= 0) { "createdAt must be non-negative" }
        require(updatedAt >= createdAt) { "updatedAt must be greater than or equal to createdAt" }
        require(serverRevision >= 0) { "serverRevision must be non-negative" }
        validateScheduleConsistency()
        if (parentTaskId != null) {
            require(parentTaskId.isNotBlank()) { "parentTaskId must not be blank" }
            require(parentTaskId != id) { "parentTaskId must not equal id (self-reference forbidden)" }
        }
    }

    private fun validateScheduleConsistency() {
        if (deadline == null) {
            require(scheduleToken == null) { "scheduleToken must be null when deadline is null" }
            require(!reminderRule.enabled) { "reminderRule must not be enabled when deadline is null" }
            require(reminderRule.leadMinutes == 0L) { "reminderLeadMinutes must be 0 when deadline is null" }
            require(reminderRule.count == 1L) { "reminderCount must be 1 when deadline is null" }
            require(reminderRule.intervalMinutes == null) { "reminderIntervalMinutes must be null when deadline is null" }
        } else {
            requireNotNull(scheduleToken) { "scheduleToken is required when deadline is present" }
            require(scheduleToken.isNotEmpty()) { "scheduleToken must not be empty when deadline is present" }
            val anchor = deadline.anchorEpochMs
            val leadMs = safeMultiply(reminderRule.leadMinutes, 60_000L)
            val first = safeSubtract(anchor, leadMs)
            val intervalMs = safeMultiply(reminderRule.intervalMinutes ?: 0L, 60_000L)
            val lastOffset = safeMultiply(reminderRule.count - 1L, intervalMs)
            val last = safeAdd(first, lastOffset)
            require(last <= anchor) { "last reminder occurrence ($last) must not be after anchor ($anchor)" }
        }
    }

    private companion object {
        private fun safeMultiply(a: Long, b: Long): Long {
            val r = a * b
            if (a != 0L && (r / a != b || (a == -1L && b == Long.MIN_VALUE))) {
                throw IllegalArgumentException("Multiplication overflow: $a * $b")
            }
            return r
        }

        private fun safeSubtract(a: Long, b: Long): Long {
            val r = a - b
            if (((a xor b) and (a xor r)) < 0) {
                throw IllegalArgumentException("Subtraction overflow: $a - $b")
            }
            return r
        }

        private fun safeAdd(a: Long, b: Long): Long {
            val r = a + b
            if (((a xor r) and (b xor r)) < 0) {
                throw IllegalArgumentException("Addition overflow: $a + $b")
            }
            return r
        }
    }
}

fun Todo.toStrictV2TodoData(): StrictV2TodoData {
    return StrictV2TodoData(
        id = id,
        title = title,
        completed = completed,
        createdAt = createdAt,
        deadlineDate = deadline?.date,
        deadlineTime = deadline?.time,
        deadlineZone = deadline?.zone,
        deadlineOffsetSeconds = deadline?.offsetSeconds,
        reminderAnchorTime = deadline?.reminderAnchorTime,
        deadlineAnchorEpochMs = deadline?.anchorEpochMs,
        deadlineOverdueEpochMs = deadline?.overdueEpochMs,
        reminderEnabled = reminderRule.enabled,
        reminderLeadMinutes = reminderRule.leadMinutes,
        reminderIntervalMinutes = reminderRule.intervalMinutes,
        reminderCount = reminderRule.count,
        scheduleToken = scheduleToken
    )
}

fun Todo.toStrictV3TodoData(): StrictV3TodoData {
    return StrictV3TodoData(
        id = id,
        title = title,
        completed = completed,
        createdAt = createdAt,
        deadlineDate = deadline?.date,
        deadlineTime = deadline?.time,
        deadlineZone = deadline?.zone,
        deadlineOffsetSeconds = deadline?.offsetSeconds,
        reminderAnchorTime = deadline?.reminderAnchorTime,
        deadlineAnchorEpochMs = deadline?.anchorEpochMs,
        deadlineOverdueEpochMs = deadline?.overdueEpochMs,
        reminderEnabled = reminderRule.enabled,
        reminderLeadMinutes = reminderRule.leadMinutes,
        reminderIntervalMinutes = reminderRule.intervalMinutes,
        reminderCount = reminderRule.count,
        scheduleToken = scheduleToken,
        parentTaskId = parentTaskId
    )
}

fun StrictV3TodoData.toDeadline(): TaskDeadline? {
    val date = deadlineDate ?: return null
    return TaskDeadline(
        date = date,
        time = deadlineTime,
        zone = requireNotNull(deadlineZone) { "deadlineZone required when deadlineDate is present" },
        offsetSeconds = requireNotNull(deadlineOffsetSeconds) { "deadlineOffsetSeconds required when deadlineDate is present" },
        reminderAnchorTime = reminderAnchorTime,
        anchorEpochMs = requireNotNull(deadlineAnchorEpochMs) { "deadlineAnchorEpochMs required when deadlineDate is present" },
        overdueEpochMs = requireNotNull(deadlineOverdueEpochMs) { "deadlineOverdueEpochMs required when deadlineDate is present" }
    )
}

fun StrictV3TodoData.toReminderRule(): ReminderRule {
    return ReminderRule(
        enabled = reminderEnabled,
        leadMinutes = reminderLeadMinutes,
        intervalMinutes = reminderIntervalMinutes,
        count = reminderCount
    )
}

fun StrictV2TodoData.toDeadline(): TaskDeadline? {
    val date = deadlineDate ?: return null
    return TaskDeadline(
        date = date,
        time = deadlineTime,
        zone = requireNotNull(deadlineZone) { "deadlineZone required when deadlineDate is present" },
        offsetSeconds = requireNotNull(deadlineOffsetSeconds) { "deadlineOffsetSeconds required when deadlineDate is present" },
        reminderAnchorTime = reminderAnchorTime,
        anchorEpochMs = requireNotNull(deadlineAnchorEpochMs) { "deadlineAnchorEpochMs required when deadlineDate is present" },
        overdueEpochMs = requireNotNull(deadlineOverdueEpochMs) { "deadlineOverdueEpochMs required when deadlineDate is present" }
    )
}

fun StrictV2TodoData.toReminderRule(): ReminderRule {
    return ReminderRule(
        enabled = reminderEnabled,
        leadMinutes = reminderLeadMinutes,
        intervalMinutes = reminderIntervalMinutes,
        count = reminderCount
    )
}

fun hasScheduleSemanticChange(
    oldDeadline: TaskDeadline?,
    oldRule: ReminderRule,
    newDeadline: TaskDeadline?,
    newRule: ReminderRule
): Boolean {
    if ((oldDeadline == null) != (newDeadline == null)) return true
    if (oldDeadline == null) return false
    return oldDeadline != newDeadline || oldRule != newRule
}

data class TodoCounts(
    val all: Int,
    val active: Int,
    val completed: Int
) {
    init {
        require(all >= 0 && active >= 0 && completed >= 0) { "Counts must be non-negative" }
        require(all == active + completed) { "Total count must equal active + completed" }
    }
}
