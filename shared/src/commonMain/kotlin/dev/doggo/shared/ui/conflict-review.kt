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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.doggo.shared.contract.ConflictState
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.theme.DoggoThemeTokens

@Composable
fun ConflictReview(
    conflict: ConflictState,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(DoggoThemeTokens.SpacingLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(DoggoThemeTokens.SpacingLarge)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Conflict Review",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "ID: ${conflict.rowId.take(8)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = DoggoThemeTokens.customColors.muted
                )
            }

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingSmall))

            Text(
                text = "A change was made on another client while you had pending offline edits. Review both versions and choose how to proceed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (conflict.isUnknownSuperseded) {
                Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingSmall))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = DoggoThemeTokens.customColors.coralSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                ) {
                    Text(
                        text = "Warning: This operation was in-flight and may have already been accepted on the server before being superseded by another client. Reapplying or copying as new may duplicate prior edits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(DoggoThemeTokens.SpacingMedium)
                    )
                }
            }
            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingLarge))

            // Local Draft Card
            VersionCard(
                title = "Your Local Version (Retained Intent)",
                todo = conflict.localTodo,
                isLocal = true,
                containerColor = DoggoThemeTokens.customColors.mintSurface,
                headerColor = DoggoThemeTokens.customColors.primaryDeep
            )

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingMedium))

            // Remote Base Card
            VersionCard(
                title = "Current Server Version",
                todo = conflict.remoteTodo,
                isLocal = false,
                containerColor = DoggoThemeTokens.customColors.coralSurface,
                headerColor = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingLarge))
            HorizontalDivider(color = DoggoThemeTokens.customColors.line)
            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingLarge))

            // Resolution Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingSmall)
            ) {
                // Copy As New: retains local edits as a fresh task UUID
                Button(
                    onClick = { onAction(TodoAction.CopyAsNew(conflict.rowId)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Keep Both (Copy Local as New Task)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Reapply: if remote item is not deleted
                val remoteIsDeleted = conflict.remoteTodo == null || conflict.remoteTodo.isDeleted
                if (!remoteIsDeleted) {
                    OutlinedButton(
                        onClick = { onAction(TodoAction.ReapplyConflict(conflict.rowId)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Overwrite Server with My Version",
                            style = MaterialTheme.typography.labelLarge,
                            color = DoggoThemeTokens.customColors.primaryDeep
                        )
                    }
                }

                // Discard: accepts remote server version, drops local edits
                OutlinedButton(
                    onClick = { onAction(TodoAction.DiscardConflict(conflict.rowId)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Discard My Edits (Accept Server)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Close / Decide Later
                OutlinedButton(
                    onClick = { onAction(TodoAction.DismissError) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Decide Later",
                        style = MaterialTheme.typography.labelLarge,
                        color = DoggoThemeTokens.customColors.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionCard(
    title: String,
    todo: Todo?,
    isLocal: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    headerColor: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, shape = MaterialTheme.shapes.small)
            .border(1.dp, DoggoThemeTokens.customColors.line, shape = MaterialTheme.shapes.small)
            .padding(DoggoThemeTokens.SpacingMedium)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = headerColor
        )

        Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingXSmall))

        if (todo == null || todo.isDeleted) {
            val emptyMsg = if (isLocal) {
                "Local draft was deleted or not yet created"
            } else {
                "Item was deleted on the server"
            }
            Text(
                text = emptyMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingXSmall))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (todo.completed) "Status: Completed" else "Status: Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = DoggoThemeTokens.customColors.muted
                )
                Text(
                    text = "Rev: ${todo.serverRevision}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DoggoThemeTokens.customColors.muted
                )
            }

            if (todo.deadline != null) {
                Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingXSmall))
                val deadlineText = if (todo.deadline.time != null) {
                    "Deadline: ${todo.deadline.date} ${todo.deadline.time} (${todo.deadline.zone})"
                } else {
                    "Deadline: ${todo.deadline.date} (date-only, ${todo.deadline.zone})"
                }
                Text(
                    text = deadlineText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                val reminderText = if (todo.reminderRule.enabled) {
                    "Reminder: ${todo.reminderRule.leadMinutes}m before (${todo.reminderRule.count}x)"
                } else {
                    "Reminder: Disabled"
                }
                Text(
                    text = reminderText,
                    style = MaterialTheme.typography.bodySmall,
                    color = DoggoThemeTokens.customColors.muted
                )
            } else {
                Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingXSmall))
                Text(
                    text = "Deadline: None",
                    style = MaterialTheme.typography.bodySmall,
                    color = DoggoThemeTokens.customColors.muted
                )
            }
        }
    }
}
