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
import dev.tabbyrush.shared.contract.ConflictState
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens

@Composable
fun ConflictReview(
    conflict: ConflictState,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(TabbyRushThemeTokens.SpacingLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(TabbyRushThemeTokens.SpacingLarge)
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
                    text = "ID: ${conflict.taskRef.ownerUserId}:${conflict.taskRef.rowId.take(8)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = TabbyRushThemeTokens.customColors.muted
                )
            }

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

            Text(
                text = "Task rows synchronize independently, so a group cleanup can be partial or conflicted. Local work is retained. Refresh and review before choosing how to proceed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (conflict.requiresDeliveryReview) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = TabbyRushThemeTokens.customColors.coralSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                ) {
                    Text(
                        text = "Delivery history cannot establish whether these edits reached the server. Reapplying or copying as new may duplicate earlier edits; nothing is replayed automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(TabbyRushThemeTokens.SpacingMedium)
                    )
                }
            }

            if (conflict.isPreservationConflict) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = TabbyRushThemeTokens.customColors.mintSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, TabbyRushThemeTokens.customColors.primaryDeep, MaterialTheme.shapes.small)
                ) {
                    Text(
                        text = "Subtask Completion Conflict: This parent task has an automatic completion state derived from subtasks that conflicted with an update on the server. Discarding will clear the local completion barrier and adopt the server version without undoing accepted subtask deletions. Overwriting will push your local completion state to the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TabbyRushThemeTokens.customColors.primaryDeep,
                        modifier = Modifier.padding(TabbyRushThemeTokens.SpacingMedium)
                    )
                }
            }

            val isSlotConflict = conflict.localTodo?.accessState != null &&
                (conflict.localTodo.accessState != conflict.remoteTodo?.accessState)
            if (isSlotConflict) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = TabbyRushThemeTokens.customColors.coralSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                ) {
                    Text(
                        text = "Assignment Conflict: Task capabilities or assignment permissions differ between your device and the server. Discarding will adopt the server's authoritative access state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(TabbyRushThemeTokens.SpacingMedium)
                    )
                }
            }

            if (conflict.isSubtaskConflict) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = TabbyRushThemeTokens.customColors.coralSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                ) {
                    Text(
                        text = if (conflict.isParentDeleted) {
                            "Orphaned Subtask Notice: The parent task for this subtask was deleted on the server. Because subtasks cannot exist without a live parent, you cannot overwrite the server version. You can keep your work as a new independent root task, or discard these edits."
                        } else {
                            "Subtask Outcome Notice: Subtask operations synchronize independently from their parent. Overwriting will apply this subtask version to the server, while discarding will adopt the server version."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(TabbyRushThemeTokens.SpacingMedium)
                    )
                }
            }
            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

            // Local Draft Card
            VersionCard(
                title = "Your Local Version (Retained Intent)",
                todo = conflict.localTodo,
                isLocal = true,
                containerColor = TabbyRushThemeTokens.customColors.mintSurface,
                headerColor = TabbyRushThemeTokens.customColors.primaryDeep
            )

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))

            // Remote Base Card
            VersionCard(
                title = "Current Server Version",
                todo = conflict.remoteTodo,
                isLocal = false,
                containerColor = TabbyRushThemeTokens.customColors.coralSurface,
                headerColor = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))
            HorizontalDivider(color = TabbyRushThemeTokens.customColors.line)
            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

            // Resolution Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
            ) {
                val remoteIsUnavailable = conflict.remoteTodo?.accessState?.status == TaskAccessStatus.Unavailable
                val remoteIsDeleted = conflict.remoteTodo == null || conflict.remoteTodo.isDeleted
                val canReapply = !remoteIsDeleted && !conflict.isParentDeleted && !remoteIsUnavailable

                // Copy As New: retains local edits as a fresh root task UUID, only when access is not revoked
                if (!remoteIsUnavailable) {
                    Button(
                        onClick = { onAction(TodoAction.CopyAsNew(conflict.taskRef)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Keep Both (Copy Local as New Root Task)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Reapply: if remote item is not deleted AND parent is not deleted AND access is not unavailable
                if (canReapply) {
                    OutlinedButton(
                        onClick = { onAction(TodoAction.ReapplyConflict(conflict.taskRef)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Reapply My Changed Fields",
                            style = MaterialTheme.typography.labelLarge,
                            color = TabbyRushThemeTokens.customColors.primaryDeep
                        )
                    }
                }

                // Discard: accepts remote server version, drops local edits / clears preservation barrier
                OutlinedButton(
                    onClick = { onAction(TodoAction.DiscardConflict(conflict.taskRef)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (conflict.isPreservationConflict) "Discard Local Barrier (Accept Server)" else "Discard My Edits (Accept Server)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // Close / Decide Later
                OutlinedButton(
                    onClick = { onAction(TodoAction.DismissError) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Decide Later",
                        style = MaterialTheme.typography.labelLarge,
                        color = TabbyRushThemeTokens.customColors.muted
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
            .border(1.dp, TabbyRushThemeTokens.customColors.line, shape = MaterialTheme.shapes.small)
            .padding(TabbyRushThemeTokens.SpacingMedium)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = headerColor
        )

        Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))

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

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (todo.completed) "Status: Completed" else "Status: Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = TabbyRushThemeTokens.customColors.muted
                )
                Text(
                    text = "Rev: ${todo.serverRevision}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TabbyRushThemeTokens.customColors.muted
                )
            }
            val access = todo.accessState
            if (access != null) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                Text(
                    text = "Access: ${access.coverage} (${if (access.capabilities.manageAssignment) "Creator" else "Assignee"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TabbyRushThemeTokens.customColors.primaryDeep
                )
            }

            val dl = todo.deadline
            if (dl != null) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                val deadlineText = if (dl.time != null) {
                    "Deadline: ${dl.date} ${dl.time} (${dl.zone})"
                } else {
                    "Deadline: ${dl.date} (date-only, anchor ${dl.reminderAnchorTime ?: "09:00"}, ${dl.zone})"
                }
                Text(
                    text = deadlineText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                val reminderText = if (todo.reminderRule.enabled) {
                    val repeatDesc = if (todo.reminderRule.count > 1L) {
                        ", repeat ${todo.reminderRule.count}x every ${todo.reminderRule.intervalMinutes}m"
                    } else " (1 alert)"
                    "Reminder: ${todo.reminderRule.leadMinutes}m before anchor$repeatDesc"
                } else {
                    "Reminder: Disabled"
                }
                Text(
                    text = reminderText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TabbyRushThemeTokens.customColors.muted
                )

                if (todo.scheduleToken != null) {
                    Text(
                        text = "Schedule Token: ${todo.scheduleToken.take(8)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = TabbyRushThemeTokens.customColors.muted
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                Text(
                    text = "Deadline: None",
                    style = MaterialTheme.typography.bodySmall,
                    color = TabbyRushThemeTokens.customColors.muted
                )
            }
        }
    }
}
