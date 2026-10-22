package dev.tabbyrush.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.contract.EditorState
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens

@Composable
fun DeadlineEditorSection(
    editor: EditorState,
    validationResult: DeadlineValidationResult,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var activePicker by remember(editor.id, editor.hasDeadline, editor.hasTime) { mutableStateOf<ActivePicker?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
    ) {
        // Toggle Set Deadline
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = editor.hasDeadline,
                onCheckedChange = { checked ->
                    onAction(TodoAction.UpdateEditorState(editor.copy(hasDeadline = checked)))
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = TabbyRushThemeTokens.customColors.muted
                ),
                modifier = Modifier
                    .size(TabbyRushThemeTokens.MinTouchTargetSize)
                    .semantics {
                        stateDescription = if (editor.hasDeadline) "Deadline enabled" else "Deadline disabled"
                        contentDescription = "Toggle task deadline"
                    }
            )
            Spacer(modifier = Modifier.width(TabbyRushThemeTokens.SpacingSmall))
            Text(
                text = "Set task deadline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (editor.hasDeadline) {

            // Date & Timezone Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
            ) {
                DateTimePickerButton(
                    label = "Deadline date",
                    value = editor.deadlineDate,
                    onClick = { activePicker = ActivePicker.Date },
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = editor.deadlineZone,
                    onValueChange = {
                        onAction(TodoAction.UpdateEditorState(editor.copy(deadlineZone = it, chosenOffsetSeconds = null)))
                    },
                    label = { Text("Timezone (IANA)") },
                    placeholder = { Text("UTC") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                )
            }

            // Specific Time Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = editor.hasTime,
                    onCheckedChange = { checked ->
                        onAction(TodoAction.UpdateEditorState(editor.copy(hasTime = checked, chosenOffsetSeconds = null)))
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = TabbyRushThemeTokens.customColors.muted
                    ),
                    modifier = Modifier
                        .size(TabbyRushThemeTokens.MinTouchTargetSize)
                        .semantics {
                            stateDescription = if (editor.hasTime) "Timed deadline" else "Date-only deadline"
                            contentDescription = "Toggle specific deadline time"
                        }
                )
                Spacer(modifier = Modifier.width(TabbyRushThemeTokens.SpacingSmall))
                Text(
                    text = "Specific deadline time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (editor.hasTime) {
                DateTimePickerButton(
                    label = "Deadline time",
                    value = editor.deadlineTime,
                    onClick = { activePicker = ActivePicker.DeadlineTime },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                DateTimePickerButton(
                    label = "Date-only reminder anchor time",
                    value = editor.reminderAnchorTime,
                    onClick = { activePicker = ActivePicker.AnchorTime },
                    supportingText = "Date-only reminders trigger at this time; task becomes overdue at next day start.",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Overlap disambiguation if needed
            if (validationResult is DeadlineValidationResult.Invalid && validationResult.overlap != null) {
                val overlap = validationResult.overlap
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TabbyRushThemeTokens.customColors.mintSurface, MaterialTheme.shapes.small)
                        .padding(TabbyRushThemeTokens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingXSmall)
                ) {
                    Text(
                        text = "Ambiguous time during DST fall-back. Choose offset:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)) {
                        FilterChip(
                            selected = editor.chosenOffsetSeconds == overlap.earlierOffsetSeconds,
                            onClick = {
                                onAction(TodoAction.UpdateEditorState(editor.copy(chosenOffsetSeconds = overlap.earlierOffsetSeconds)))
                            },
                            label = { Text(DeadlineCalculatorHelpers.formatOffset(overlap.earlierOffsetSeconds)) }
                        )
                        FilterChip(
                            selected = editor.chosenOffsetSeconds == overlap.laterOffsetSeconds,
                            onClick = {
                                onAction(TodoAction.UpdateEditorState(editor.copy(chosenOffsetSeconds = overlap.laterOffsetSeconds)))
                            },
                            label = { Text(DeadlineCalculatorHelpers.formatOffset(overlap.laterOffsetSeconds)) }
                        )
                    }
                }
            }

            // Reminder Rule Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = editor.reminderEnabled,
                    onCheckedChange = { checked ->
                        onAction(TodoAction.UpdateEditorState(editor.copy(reminderEnabled = checked)))
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = TabbyRushThemeTokens.customColors.muted
                    ),
                    modifier = Modifier
                        .size(TabbyRushThemeTokens.MinTouchTargetSize)
                        .semantics {
                            stateDescription = if (editor.reminderEnabled) "Reminders active" else "Reminders disabled"
                            contentDescription = "Toggle reminder notification"
                        }
                )
                Spacer(modifier = Modifier.width(TabbyRushThemeTokens.SpacingSmall))
                Text(
                    text = "Notify before deadline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (editor.reminderEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
                ) {
                    OutlinedTextField(
                        value = editor.reminderLeadMinutes.toString(),
                        onValueChange = {
                            val v = it.toLongOrNull() ?: 0L
                            onAction(TodoAction.UpdateEditorState(editor.copy(reminderLeadMinutes = maxOf(0L, v))))
                        },
                        label = { Text("Lead time (min)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                    )

                    OutlinedTextField(
                        value = editor.reminderCount.toString(),
                        onValueChange = {
                            val v = it.toLongOrNull() ?: 1L
                            onAction(TodoAction.UpdateEditorState(editor.copy(reminderCount = v.coerceIn(1L, 10L))))
                        },
                        label = { Text("Count (1–10)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                    )
                }

                if (editor.reminderCount > 1L) {
                    OutlinedTextField(
                        value = (editor.reminderIntervalMinutes ?: 15L).toString(),
                        onValueChange = {
                            val v = it.toLongOrNull() ?: 15L
                            onAction(TodoAction.UpdateEditorState(editor.copy(reminderIntervalMinutes = maxOf(15L, v))))
                        },
                        label = { Text("Interval (min, ≥15)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                    )
                }
            }

            // Validation message or schedule preview
            if (validationResult is DeadlineValidationResult.Invalid) {
                Text(
                    text = validationResult.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = TabbyRushThemeTokens.SpacingXSmall)
                )
            } else if (validationResult is DeadlineValidationResult.Valid) {
                val preview = validationResult.preview
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        .border(1.dp, TabbyRushThemeTokens.customColors.line, MaterialTheme.shapes.small)
                        .padding(TabbyRushThemeTokens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingXSmall)
                ) {
                    Text(
                        text = "Schedule Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = TabbyRushThemeTokens.customColors.primaryDeep
                    )
                    if (preview.occurrences.isEmpty()) {
                        Text(
                            text = "No notification alarms will be scheduled (reminders disabled).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TabbyRushThemeTokens.customColors.muted
                        )
                    } else {
                        preview.occurrences.forEachIndexed { i, occ ->
                            Text(
                                text = "Reminder #${i + 1}: index ${occ.index} at instant ${occ.dueEpochMs}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (!editor.hasTime) {
                        Text(
                            text = "Overdue boundary starts next calendar day at instant ${preview.overdueEpochMs}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TabbyRushThemeTokens.customColors.muted
                        )
                    }
                }
            }

            when (activePicker) {
                ActivePicker.Date -> {
                    TabbyRushDatePickerDialog(
                        initialDate = editor.deadlineDate,
                        title = "Deadline date",
                        onConfirm = { newDate ->
                            if (activePicker == ActivePicker.Date && editor.hasDeadline) {
                                if (newDate != editor.deadlineDate) {
                                    onAction(TodoAction.UpdateEditorState(editor.copy(deadlineDate = newDate, chosenOffsetSeconds = null)))
                                }
                                activePicker = null
                            }
                        },
                        onDismiss = {
                            if (activePicker == ActivePicker.Date) {
                                activePicker = null
                            }
                        }
                    )
                }
                ActivePicker.DeadlineTime -> {
                    TabbyRushTimePickerDialog(
                        initialTime = editor.deadlineTime,
                        title = "Deadline time",
                        onConfirm = { newTime ->
                            if (activePicker == ActivePicker.DeadlineTime && editor.hasDeadline && editor.hasTime) {
                                if (newTime != editor.deadlineTime) {
                                    onAction(TodoAction.UpdateEditorState(editor.copy(deadlineTime = newTime, chosenOffsetSeconds = null)))
                                }
                                activePicker = null
                            }
                        },
                        onDismiss = {
                            if (activePicker == ActivePicker.DeadlineTime) {
                                activePicker = null
                            }
                        },
                        fallbackTime = "17:00"
                    )
                }
                ActivePicker.AnchorTime -> {
                    TabbyRushTimePickerDialog(
                        initialTime = editor.reminderAnchorTime,
                        title = "Date-only reminder anchor time",
                        onConfirm = { newAnchor ->
                            if (activePicker == ActivePicker.AnchorTime && editor.hasDeadline && !editor.hasTime) {
                                if (newAnchor != editor.reminderAnchorTime) {
                                    onAction(TodoAction.UpdateEditorState(editor.copy(reminderAnchorTime = newAnchor, chosenOffsetSeconds = null)))
                                }
                                activePicker = null
                            }
                        },
                        onDismiss = {
                            if (activePicker == ActivePicker.AnchorTime) {
                                activePicker = null
                            }
                        },
                        fallbackTime = "09:00"
                    )
                }
                null -> Unit
            }
        }
    }
}

private enum class ActivePicker {
    Date,
    DeadlineTime,
    AnchorTime
}
