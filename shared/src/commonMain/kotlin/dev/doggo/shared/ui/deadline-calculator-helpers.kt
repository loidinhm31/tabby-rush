package dev.doggo.shared.ui

import dev.doggo.shared.contract.EditorState
import dev.doggo.shared.contract.ReminderRule
import dev.doggo.shared.contract.ReminderScheduleCalculator
import dev.doggo.shared.contract.ReminderSchedulePreview
import dev.doggo.shared.contract.StrictTimeZoneResolver
import dev.doggo.shared.contract.StrictZoneOffsetResult
import dev.doggo.shared.contract.TaskDeadline

sealed interface DeadlineValidationResult {
    data class Valid(
        val deadline: TaskDeadline,
        val rule: ReminderRule,
        val preview: ReminderSchedulePreview
    ) : DeadlineValidationResult

    data class Invalid(
        val message: String,
        val isGap: Boolean = false,
        val overlap: StrictZoneOffsetResult.Overlap? = null
    ) : DeadlineValidationResult

    data object None : DeadlineValidationResult
}

object DeadlineCalculatorHelpers {

    private val DATE_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

    fun validateAndComputeDeadline(
        editor: EditorState,
        resolver: StrictTimeZoneResolver = StrictTimeZoneResolver.default
    ): DeadlineValidationResult {
        if (!editor.hasDeadline) {
            return DeadlineValidationResult.None
        }

        if (editor.deadlineDate.isBlank()) {
            return DeadlineValidationResult.Invalid("Deadline date is required (YYYY-MM-DD)")
        }

        val dateMatch = DATE_REGEX.matchEntire(editor.deadlineDate.trim())
            ?: return DeadlineValidationResult.Invalid("Deadline date must be formatted as YYYY-MM-DD")

        val year = dateMatch.groupValues[1].toInt()
        val month = dateMatch.groupValues[2].toInt()
        val day = dateMatch.groupValues[3].toInt()
        if (month !in 1..12 || day !in 1..31) {
            return DeadlineValidationResult.Invalid("Invalid calendar date (month 1..12, day 1..31)")
        }

        val zone = editor.deadlineZone.trim()
        if (zone.isEmpty()) {
            return DeadlineValidationResult.Invalid("Timezone must not be empty")
        }
        if (!resolver.isZoneSupported(zone)) {
            return DeadlineValidationResult.Invalid("Unsupported or invalid timezone identifier: $zone")
        }

        val anchorEpochMs: Long
        val overdueEpochMs: Long
        val offsetSeconds: Long
        val reminderAnchorTime: String?

        try {
            if (editor.hasTime) {
                val timeStr = editor.deadlineTime.trim()
                if (timeStr.isEmpty()) {
                    return DeadlineValidationResult.Invalid("Deadline time is required when specific time is enabled (HH:mm)")
                }
                if (!TIME_REGEX.matches(timeStr)) {
                    return DeadlineValidationResult.Invalid("Deadline time must be formatted as HH:mm (24-hour clock)")
                }

                when (val offsetResult = resolver.resolveOffset(zone, editor.deadlineDate, timeStr)) {
                    is StrictZoneOffsetResult.Gap -> {
                        return DeadlineValidationResult.Invalid(
                            message = "Time $timeStr does not exist in $zone due to a DST spring-forward gap.",
                            isGap = true
                        )
                    }
                    is StrictZoneOffsetResult.Overlap -> {
                        val chosen = editor.chosenOffsetSeconds
                        if (chosen == null || (chosen != offsetResult.earlierOffsetSeconds && chosen != offsetResult.laterOffsetSeconds)) {
                            return DeadlineValidationResult.Invalid(
                                message = "Ambiguous local time during DST fall-back. Please select an offset.",
                                overlap = offsetResult
                            )
                        }
                        val instant = try {
                            resolver.resolveInstant(zone, editor.deadlineDate, timeStr, chosen)
                        } catch (e: Exception) {
                            return DeadlineValidationResult.Invalid(e.message ?: "Failed to resolve ambiguous instant")
                        }
                        anchorEpochMs = instant
                        overdueEpochMs = instant
                        offsetSeconds = chosen
                    }
                    is StrictZoneOffsetResult.Single -> {
                        anchorEpochMs = offsetResult.epochMs
                        overdueEpochMs = offsetResult.epochMs
                        offsetSeconds = offsetResult.offsetSeconds
                    }
                }
                reminderAnchorTime = null
            } else {
                // Date-only task
                val anchorStr = editor.reminderAnchorTime.trim().ifEmpty { "09:00" }
                if (!TIME_REGEX.matches(anchorStr)) {
                    return DeadlineValidationResult.Invalid("Date-only reminder anchor time must be formatted as HH:mm")
                }

                when (val offsetResult = resolver.resolveOffset(zone, editor.deadlineDate, anchorStr)) {
                    is StrictZoneOffsetResult.Gap -> {
                        return DeadlineValidationResult.Invalid(
                            message = "Anchor time $anchorStr does not exist in $zone due to a DST spring-forward gap.",
                            isGap = true
                        )
                    }
                    is StrictZoneOffsetResult.Overlap -> {
                        val chosen = editor.chosenOffsetSeconds
                        if (chosen == null || (chosen != offsetResult.earlierOffsetSeconds && chosen != offsetResult.laterOffsetSeconds)) {
                            return DeadlineValidationResult.Invalid(
                                message = "Ambiguous anchor time during DST fall-back. Please select an offset.",
                                overlap = offsetResult
                            )
                        }
                        val instant = try {
                            resolver.resolveInstant(zone, editor.deadlineDate, anchorStr, chosen)
                        } catch (e: Exception) {
                            return DeadlineValidationResult.Invalid(e.message ?: "Failed to resolve anchor instant")
                        }
                        anchorEpochMs = instant
                        offsetSeconds = chosen
                    }
                    is StrictZoneOffsetResult.Single -> {
                        anchorEpochMs = offsetResult.epochMs
                        offsetSeconds = offsetResult.offsetSeconds
                    }
                }
                // For date-only tasks: overdue is the start of the NEXT local calendar day
                overdueEpochMs = resolver.computeNextCalendarDayStartEpochMs(zone, editor.deadlineDate)
                reminderAnchorTime = anchorStr
            }
        } catch (e: Exception) {
            return DeadlineValidationResult.Invalid(e.message ?: "Invalid calendar date or timezone configuration")
        }

        val taskDeadline = try {
            TaskDeadline(
                date = editor.deadlineDate.trim(),
                time = if (editor.hasTime) editor.deadlineTime.trim() else null,
                zone = zone,
                offsetSeconds = offsetSeconds,
                reminderAnchorTime = reminderAnchorTime,
                anchorEpochMs = anchorEpochMs,
                overdueEpochMs = overdueEpochMs
            )
        } catch (e: Exception) {
            return DeadlineValidationResult.Invalid(e.message ?: "Invalid deadline parameters")
        }

        val reminderRule = if (editor.reminderEnabled) {
            if (editor.reminderLeadMinutes < 0L) {
                return DeadlineValidationResult.Invalid("Reminder lead minutes must be non-negative")
            }
            if (editor.reminderCount !in 1L..10L) {
                return DeadlineValidationResult.Invalid("Reminder count must be between 1 and 10")
            }
            if (editor.reminderCount > 1L && (editor.reminderIntervalMinutes == null || editor.reminderIntervalMinutes < 15L)) {
                return DeadlineValidationResult.Invalid("Repeat interval must be at least 15 minutes when repeating (count > 1)")
            }
            try {
                ReminderRule(
                    enabled = true,
                    leadMinutes = editor.reminderLeadMinutes,
                    intervalMinutes = if (editor.reminderCount > 1L) editor.reminderIntervalMinutes else null,
                    count = editor.reminderCount
                )
            } catch (e: Exception) {
                return DeadlineValidationResult.Invalid(e.message ?: "Invalid reminder rule")
            }
        } else {
            ReminderRule.DISABLED
        }

        val occurrences = try {
            ReminderScheduleCalculator.calculateOccurrences(taskDeadline, reminderRule)
        } catch (e: Exception) {
            return DeadlineValidationResult.Invalid(e.message ?: "Last reminder occurs after the deadline anchor")
        }

        val preview = ReminderSchedulePreview(
            occurrences = occurrences,
            anchorEpochMs = taskDeadline.anchorEpochMs,
            overdueEpochMs = taskDeadline.overdueEpochMs
        )

        return DeadlineValidationResult.Valid(
            deadline = taskDeadline,
            rule = reminderRule,
            preview = preview
        )
    }

    fun formatOffset(offsetSeconds: Long): String {
        val sign = if (offsetSeconds >= 0) "+" else "-"
        val abs = kotlin.math.abs(offsetSeconds)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        return "UTC$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }
}
