package dev.tabbyrush.shared.contract

object TaskDataContract {
    const val DEFAULT_TABLE_NAME = "todos"
    const val ASSIGNMENT_TABLE_NAME = "taskAssignees"
    const val MIN_REPEAT_INTERVAL_MINUTES = 15L
    const val MIN_REMINDER_COUNT = 1L
    const val MAX_REMINDER_COUNT = 10L
}

enum class TaskOperationKind(val wireName: String) {
    CREATE("create"), UPDATE("update"), DELETE("delete");
}

// Keep validation implementation private without making the generated serialization companion private.
private val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

@kotlinx.serialization.Serializable
data class TaskData(
    val id: String,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
    val deadlineDate: String? = null,
    val deadlineTime: String? = null,
    val deadlineZone: String? = null,
    val deadlineOffsetSeconds: Long? = null,
    val reminderAnchorTime: String? = null,
    val deadlineAnchorEpochMs: Long? = null,
    val deadlineOverdueEpochMs: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Long = 0L,
    val reminderIntervalMinutes: Long? = null,
    val reminderCount: Long = 1L,
    val scheduleToken: String? = null,
    val parentTaskId: String? = null
) {
    fun validate() {
        require(id.isNotEmpty()) { "id must not be empty" }
        require(parentTaskId == null || (parentTaskId != id && UUID_PATTERN.matches(parentTaskId))) { "Invalid parentTaskId" }
        require(createdAt >= 0) { "createdAt must be non-negative" }
        require(reminderLeadMinutes >= 0) { "reminderLeadMinutes must be non-negative" }
        require(reminderCount in TaskDataContract.MIN_REMINDER_COUNT..TaskDataContract.MAX_REMINDER_COUNT) {
            "reminderCount must be between 1 and 10"
        }
        if (reminderCount == 1L) {
            require(reminderIntervalMinutes == null) {
                "reminderIntervalMinutes must be null when reminderCount is 1"
            }
        } else {
            require(reminderIntervalMinutes != null && reminderIntervalMinutes >= TaskDataContract.MIN_REPEAT_INTERVAL_MINUTES) {
                "repeating reminderIntervalMinutes must be >= 15"
            }
        }
        if (deadlineDate == null) {
            require(deadlineTime == null) { "deadlineTime must be null when deadlineDate is null" }
            require(deadlineZone == null) { "deadlineZone must be null when deadlineDate is null" }
            require(deadlineOffsetSeconds == null) { "deadlineOffsetSeconds must be null when deadlineDate is null" }
            require(reminderAnchorTime == null) { "reminderAnchorTime must be null when deadlineDate is null" }
            require(deadlineAnchorEpochMs == null) { "deadlineAnchorEpochMs must be null when deadlineDate is null" }
            require(deadlineOverdueEpochMs == null) { "deadlineOverdueEpochMs must be null when deadlineDate is null" }
            require(scheduleToken == null) { "scheduleToken must be null when deadlineDate is null" }
            require(!reminderEnabled) { "reminderEnabled must be false when deadlineDate is null" }
            require(reminderLeadMinutes == 0L) { "reminderLeadMinutes must be 0 when deadlineDate is null" }
            require(reminderCount == 1L) { "reminderCount must be 1 when deadlineDate is null" }
            require(reminderIntervalMinutes == null) { "reminderIntervalMinutes must be null when deadlineDate is null" }
        } else {
            requireNotNull(deadlineZone) { "deadlineZone is required when deadlineDate is present" }
            requireNotNull(deadlineOffsetSeconds) { "deadlineOffsetSeconds is required when deadlineDate is present" }
            requireNotNull(deadlineAnchorEpochMs) { "deadlineAnchorEpochMs is required when deadlineDate is present" }
            requireNotNull(deadlineOverdueEpochMs) { "deadlineOverdueEpochMs is required when deadlineDate is present" }
            requireNotNull(scheduleToken) { "scheduleToken is required when deadlineDate is present" }
            if (deadlineTime == null) {
                requireNotNull(reminderAnchorTime) { "reminderAnchorTime required for date-only deadline" }
            } else {
                require(reminderAnchorTime == null) { "reminderAnchorTime must be null for timed deadline" }
                require(deadlineOverdueEpochMs == deadlineAnchorEpochMs) {
                    "deadlineOverdueEpochMs must equal deadlineAnchorEpochMs for timed deadline"
                }
            }

            val anchor = requireNotNull(deadlineAnchorEpochMs)
            val leadMs = safeMultiply(reminderLeadMinutes, 60_000L)
            val first = safeSubtract(anchor, leadMs)
            val interval = reminderIntervalMinutes ?: 0L
            val intervalMs = safeMultiply(interval, 60_000L)
            val lastOffset = safeMultiply(reminderCount - 1L, intervalMs)
            val last = safeAdd(first, lastOffset)
            require(last <= anchor) { "last reminder occurrence ($last) must not be after anchor ($anchor)" }
        }
    }

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
