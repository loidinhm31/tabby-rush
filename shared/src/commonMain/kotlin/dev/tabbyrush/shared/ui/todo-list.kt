package dev.tabbyrush.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoCounts
import dev.tabbyrush.shared.contract.TodoFilter
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens

@Composable
fun TodoList(
    items: List<Todo>,
    counts: TodoCounts,
    filter: TodoFilter,
    deleteCandidate: Todo?,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier,
    nowEpochMs: Long = 0L
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Filter selection row with horizontal scrolling
        FilterRow(
            counts = counts,
            selectedFilter = filter,
            onSelectFilter = { onAction(TodoAction.SelectFilter(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TabbyRushThemeTokens.SpacingLarge, vertical = TabbyRushThemeTokens.SpacingSmall)
        )

        HorizontalDivider(color = TabbyRushThemeTokens.customColors.line)

        if (items.isEmpty()) {
            EmptyListPlaceholder(
                filter = filter,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    horizontal = TabbyRushThemeTokens.SpacingLarge,
                    vertical = TabbyRushThemeTokens.SpacingMedium
                ),
                verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
            ) {
                items(
                    items = items,
                    key = { it.id }
                ) { todo ->
                    TodoRow(
                        todo = todo,
                        onToggle = { onAction(TodoAction.ToggleCompleted(todo)) },
                        onEdit = { onAction(TodoAction.OpenEdit(todo)) },
                        onDelete = { onAction(TodoAction.RequestDelete(todo)) },
                        nowEpochMs = nowEpochMs
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (deleteCandidate != null) {
        DeleteConfirmationDialog(
            todo = deleteCandidate,
            onConfirm = { onAction(TodoAction.ConfirmDelete) },
            onCancel = { onAction(TodoAction.CancelDelete) }
        )
    }
}

@Composable
private fun FilterRow(
    counts: TodoCounts,
    selectedFilter: TodoFilter,
    onSelectFilter: (TodoFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedFilter == TodoFilter.ALL,
            onClick = { onSelectFilter(TodoFilter.ALL) },
            label = { Text("All (${counts.all})") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = TabbyRushThemeTokens.customColors.mintSurface,
                selectedLabelColor = TabbyRushThemeTokens.customColors.primaryDeep
            ),
            modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
        )

        FilterChip(
            selected = selectedFilter == TodoFilter.ACTIVE,
            onClick = { onSelectFilter(TodoFilter.ACTIVE) },
            label = { Text("Active (${counts.active})") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = TabbyRushThemeTokens.customColors.mintSurface,
                selectedLabelColor = TabbyRushThemeTokens.customColors.primaryDeep
            ),
            modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
        )

        FilterChip(
            selected = selectedFilter == TodoFilter.COMPLETED,
            onClick = { onSelectFilter(TodoFilter.COMPLETED) },
            label = { Text("Done (${counts.completed})") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = TabbyRushThemeTokens.customColors.mintSurface,
                selectedLabelColor = TabbyRushThemeTokens.customColors.primaryDeep
            ),
            modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
        )
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    nowEpochMs: Long = 0L
) {
    val currentClockMs = if (nowEpochMs > 0L) nowEpochMs else System.currentTimeMillis()
    val isOverdue = !todo.completed && todo.deadline != null && (currentClockMs >= todo.deadline.overdueEpochMs)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
            .border(1.dp, TabbyRushThemeTokens.customColors.line, MaterialTheme.shapes.small),
        shape = MaterialTheme.shapes.small,
        color = if (todo.completed) TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(TabbyRushThemeTokens.SpacingMedium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = todo.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = TabbyRushThemeTokens.customColors.muted
                ),
                modifier = Modifier
                    .size(TabbyRushThemeTokens.MinTouchTargetSize)
                    .semantics {
                        stateDescription = if (todo.completed) "Completed" else "Active"
                        contentDescription = "Toggle completion for ${todo.title}"
                    }
            )

            Spacer(modifier = Modifier.width(TabbyRushThemeTokens.SpacingSmall))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (todo.completed) TabbyRushThemeTokens.customColors.muted else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None
                )

                if (todo.deadline != null) {
                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
                    ) {
                        val deadlineLabel = if (todo.deadline.time != null) {
                            "Due: ${todo.deadline.date} ${todo.deadline.time} (${todo.deadline.zone})"
                        } else {
                            "Due: ${todo.deadline.date} (date-only)"
                        }
                        Text(
                            text = deadlineLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TabbyRushThemeTokens.customColors.primaryDeep
                        )

                        if (isOverdue) {
                            Text(
                                text = "Overdue",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .background(TabbyRushThemeTokens.customColors.coralSurface, MaterialTheme.shapes.extraSmall)
                                    .padding(horizontal = TabbyRushThemeTokens.SpacingXSmall, vertical = 2.dp)
                                    .semantics {
                                        contentDescription = "Task is overdue"
                                    }
                            )
                        }
                    }
                }

                if (todo.isPending) {
                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                    Text(
                        text = "Pending sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = TabbyRushThemeTokens.customColors.primaryDeep
                    )
                }
            }
            Spacer(modifier = Modifier.width(TabbyRushThemeTokens.SpacingSmall))

            TextButton(
                onClick = onEdit,
                modifier = Modifier
                    .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                    .semantics {
                        contentDescription = "Edit ${todo.title}"
                    }
            ) {
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                    .semantics {
                        contentDescription = "Delete ${todo.title}"
                    }
            ) {
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyListPlaceholder(
    filter: TodoFilter,
    modifier: Modifier = Modifier
) {
    val message = when (filter) {
        TodoFilter.ALL -> "No tasks yet. Create one to get started!"
        TodoFilter.ACTIVE -> "No active tasks. Everything is done!"
        TodoFilter.COMPLETED -> "No completed tasks yet."
    }

    Box(
        modifier = modifier.padding(TabbyRushThemeTokens.SpacingXLarge),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TabbyRushThemeTokens.customColors.muted
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    todo: Todo,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(
                text = "Delete Task?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete this task? Permanent deletion cannot be undone once committed to the server.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
            ) {
                Text("Confirm Delete")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
            ) {
                Text("Cancel", color = TabbyRushThemeTokens.customColors.muted)
            }
        }
    )
}
