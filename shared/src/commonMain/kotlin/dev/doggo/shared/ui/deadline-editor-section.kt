package dev.doggo.shared.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.doggo.shared.contract.EditorState
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.theme.DoggoThemeTokens

@Composable
fun DeadlineEditorSection(
    editor: EditorState,
    validationResult: DeadlineValidationResult,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingSmall)
    ) {
        // Toggle Set Deadline
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = editor.hasDeadline,
                onCheckedChange = { checked ->
                    onAction(TodoAction.UpdateEditorState(editor.copy(hasDeadline = checked)))
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = DoggoThemeTokens.customColors.muted
                ),
                modifier = Modifier
                    .size(DoggoThemeTokens.MinTouchTargetSize)
                    .semantics {
                        stateDescription = if (editor.hasDeadline) "Deadline enabled" else "Deadline disabled"
                        contentDescription = "Toggle task deadline"
                    }
            )
            Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))
            Text(
                text = "Set task deadline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (editor.hasDeadline) {
            // Strict-v2 Upgrade Warning if account is v1
            if (!editor.isAccountV2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DoggoThemeTokens.customColors.coralSurface),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Strict-v2 account upgrade required to save deadlines or reminders. Upgrading can be performed via the Hub admin console or CLI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(DoggoThemeTokens.SpacingMedium)
                    )
                }
            }

            // Date & Timezone Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingSmall)
            ) {
                OutlinedTextField(
                    value = editor.deadlineDate,
                    onValueChange = {
                        onAction(TodoAction.UpdateEditorState(editor.copy(deadlineDate = it, chosenOffsetSeconds = null)))
                    },
                    label = { Text("Date (YYYY-MM-DD)") },
                    placeholder = { Text("2026-10-20") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                )

                OutlinedTextField(
                    value = editor.deadlineZone,
                    onValueChange = {
                        onAction(TodoAction.UpdateEditorState(editor.copy(deadlineZone = it, chosenOffsetSeconds = null)))
                    },
                    label = { Text("Timezone (IANA)") },
                    placeholder = { Text("UTC") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                )
            }

            // Specific Time Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = editor.hasTime,
                    onCheckedChange = { checked ->
                        onAction(TodoAction.UpdateEditorState(editor.copy(hasTime = checked, chosenOffsetSeconds = null)))
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = DoggoThemeTokens.customColors.muted
                    ),
                    modifier = Modifier
                        .size(DoggoThemeTokens.MinTouchTargetSize)
                        .semantics {
                            stateDescription = if (editor.hasTime) "Timed deadline" else "Date-only deadline"
                            contentDescription = "Toggle specific deadline time"
                        }
                )
                Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))
                Text(
                    text = "Specific deadline time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (editor.hasTime) {
                OutlinedTextField(
                    value = editor.deadlineTime,
                    onValueChange = {
                        onAction(TodoAction.UpdateEditorState(editor.copy(deadlineTime = it, chosenOffsetSeconds = null)))
                    },
                    label = { Text("Time (HH:mm)") },
                    placeholder = { Text("17:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                )
            } else {
                OutlinedTextField(
                    value = editor.reminderAnchorTime,
                    onValueChange = {
                        onAction(TodoAction.UpdateEditorState(editor.copy(reminderAnchorTime = it, chosenOffsetSeconds = null)))
                    },
                    label = { Text("Anchor time (HH:mm, default 09:00)") },
                    placeholder = { Text("09:00") },
                    supportingText = {
                        Text(
                            text = "Date-only reminders trigger at this time; task becomes overdue at next day start.",
                            style = MaterialTheme.typography.labelSmall,
                            color = DoggoThemeTokens.customColors.muted
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                )
            }

            // Overlap disambiguation if needed
            if (validationResult is DeadlineValidationResult.Invalid && validationResult.overlap != null) {
                val overlap = validationResult.overlap
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DoggoThemeTokens.customColors.mintSurface, MaterialTheme.shapes.small)
                        .padding(DoggoThemeTokens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingXSmall)
                ) {
                    Text(
                        text = "Ambiguous time during DST fall-back. Choose offset:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingSmall)) {
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
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = editor.reminderEnabled,
                    onCheckedChange = { checked ->
                        onAction(TodoAction.UpdateEditorState(editor.copy(reminderEnabled = checked)))
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = DoggoThemeTokens.customColors.muted
                    ),
                    modifier = Modifier
                        .size(DoggoThemeTokens.MinTouchTargetSize)
                        .semantics {
                            stateDescription = if (editor.reminderEnabled) "Reminders active" else "Reminders disabled"
                            contentDescription = "Toggle reminder notification"
                        }
                )
                Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))
                Text(
                    text = "Notify before deadline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (editor.reminderEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingSmall)
                ) {
                    OutlinedTextField(
                        value = editor.reminderLeadMinutes.toString(),
                        onValueChange = {
                            val v = it.toLongOrNull() ?: 0L
                            onAction(TodoAction.UpdateEditorState(editor.copy(reminderLeadMinutes = maxOf(0L, v))))
                        },
                        label = { Text("Lead time (min)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    )

                    OutlinedTextField(
                        value = editor.reminderCount.toString(),
                        onValueChange = {
                            val v = it.toLongOrNull() ?: 1L
                            onAction(TodoAction.UpdateEditorState(editor.copy(reminderCount = v.coerceIn(1L, 10L))))
                        },
                        label = { Text("Count (1–10)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
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
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    )
                }
            }

            // Validation message or schedule preview
            if (validationResult is DeadlineValidationResult.Invalid) {
                Text(
                    text = validationResult.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = DoggoThemeTokens.SpacingXSmall)
                )
            } else if (validationResult is DeadlineValidationResult.Valid) {
                val preview = validationResult.preview
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DoggoThemeTokens.customColors.mintSurface.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        .border(1.dp, DoggoThemeTokens.customColors.line, MaterialTheme.shapes.small)
                        .padding(DoggoThemeTokens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingXSmall)
                ) {
                    Text(
                        text = "Schedule Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = DoggoThemeTokens.customColors.primaryDeep
                    )
                    if (preview.occurrences.isEmpty()) {
                        Text(
                            text = "No notification alarms will be scheduled (reminders disabled).",
                            style = MaterialTheme.typography.bodySmall,
                            color = DoggoThemeTokens.customColors.muted
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
                            color = DoggoThemeTokens.customColors.muted
                        )
                    }
                }
            }
        }
    }
}
