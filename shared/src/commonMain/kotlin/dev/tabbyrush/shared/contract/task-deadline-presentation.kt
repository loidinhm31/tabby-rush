package dev.tabbyrush.shared.contract

/**
 * Pure presentation metadata for task deadlines.
 */
data class DeadlinePresentation(
    val label: String,
    val isOverdue: Boolean,
    val overdueLabel: String? = null,
    val contentDescription: String
)

object TaskDeadlinePresentation {

    /**
     * Formats absolute deadline metadata according to the contract:
     * - No deadline: returns null
     * - Date-only: "Due YYYY-MM-DD"
     * - Timed: "Due YYYY-MM-DD HH:mm (captured IANA zone)"
     * - Overdue: derived from (nowEpochMs >= deadline.overdueEpochMs && !effectiveCompleted).
     *   Supplements the absolute date rather than replacing it.
     *   Never labels completed tasks as overdue.
     */
    fun formatDeadline(
        deadline: TaskDeadline?,
        effectiveCompleted: Boolean,
        nowEpochMs: Long
    ): DeadlinePresentation? {
        if (deadline == null) return null

        val label = if (!deadline.time.isNullOrBlank()) {
            "Due ${deadline.date} ${deadline.time} (${deadline.zone})"
        } else {
            "Due ${deadline.date}"
        }

        val isOverdue = !effectiveCompleted && (nowEpochMs >= deadline.overdueEpochMs)
        val overdueLabel = if (isOverdue) "Overdue" else null

        val cd = if (isOverdue) {
            "$label, Overdue"
        } else {
            label
        }

        return DeadlinePresentation(
            label = label,
            isOverdue = isOverdue,
            overdueLabel = overdueLabel,
            contentDescription = cd
        )
    }
}
