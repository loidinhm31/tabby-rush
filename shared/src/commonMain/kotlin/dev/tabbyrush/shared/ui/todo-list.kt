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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.tabbyrush.shared.contract.HierarchicalTodoItem
import dev.tabbyrush.shared.contract.HierarchyCoverage
import dev.tabbyrush.shared.contract.TaskAccessStatus
import dev.tabbyrush.shared.contract.TaskCapabilities
import dev.tabbyrush.shared.contract.TaskDeadlinePresentation
import dev.tabbyrush.shared.contract.TaskHierarchyProjector
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.Todo
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoCounts
import dev.tabbyrush.shared.contract.TodoFilter
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens
import androidx.compose.runtime.remember
@Composable
fun TodoList(
    items: List<Todo>,
    counts: TodoCounts,
    filter: TodoFilter,
    deleteCandidate: Todo?,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier,
    nowEpochMs: Long = 0L,
    hierarchicalItems: List<HierarchicalTodoItem> = emptyList(),
    deleteCandidateChildCount: Int = 0,
    unresolvedChildren: List<Todo> = emptyList(),
    currentUserId: String? = null
) {
    val displayItems = remember(items, filter, hierarchicalItems) {
        if (hierarchicalItems.isNotEmpty()) {
            hierarchicalItems
        } else {
            TaskHierarchyProjector.projectHierarchy(items, filter).items
        }
    }

    var expandedParentRefs by remember(currentUserId) { mutableStateOf(emptySet<TaskRef>()) }

    val visibleItems = remember(displayItems, expandedParentRefs) {
        displayItems.filterNot { item ->
            item.depth > 0 && item.todo.parentTaskRef != null && !expandedParentRefs.contains(item.todo.parentTaskRef)
        }
    }

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

        if (displayItems.isEmpty() && unresolvedChildren.isEmpty()) {
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
                if (unresolvedChildren.isNotEmpty()) {
                    item(key = "unresolved_section") {
                        UnresolvedSubtaskSection(
                            unresolvedChildren = unresolvedChildren,
                            onAction = onAction,
                            nowEpochMs = nowEpochMs,
                            accountKey = currentUserId
                        )
                    }
                }
                if (displayItems.isEmpty()) {
                    item(key = "empty_placeholder") {
                        EmptyListPlaceholder(
                            filter = filter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = TabbyRushThemeTokens.SpacingLarge)
                        )
                    }
                } else {
                    items(
                        items = visibleItems,
                        key = { "${it.todo.taskRef.ownerUserId}:${it.todo.taskRef.rowId}" }
                    ) { item ->
                        val isChildOnly = item.isChildOnly
                        val isCollapsed = !expandedParentRefs.contains(item.todo.taskRef)
                        val isUnavailable = item.todo.accessState?.status == TaskAccessStatus.Unavailable
                        val isOwner = !isUnavailable && currentUserId != null && item.todo.taskRef.ownerUserId == currentUserId
                        val canDelete = if (isUnavailable) false else if (isOwner) true else (item.todo.accessState?.canDeleteTask == true)
                        val canCreateChild = (if (isUnavailable) false else if (isOwner) true else (item.todo.accessState?.canCreateChild == true)) && !isChildOnly && item.depth == 0
                        TodoRow(
                            item = item,
                            currentUserId = currentUserId,
                            isCollapsed = isCollapsed,
                            onToggleExpand = if (item.hasLiveChildren && !isChildOnly) {
                                {
                                    expandedParentRefs = if (isCollapsed) {
                                        expandedParentRefs + item.todo.taskRef
                                    } else {
                                        expandedParentRefs - item.todo.taskRef
                                    }
                                }
                            } else null,
                            onToggle = { onAction(TodoAction.ToggleCompleted(item.todo)) },
                            onEdit = { onAction(TodoAction.OpenEdit(item.todo)) },
                            onDelete = if (canDelete) { { onAction(TodoAction.RequestDelete(item.todo)) } } else null,
                            onAddSubtask = if (canCreateChild) {
                                { onAction(TodoAction.OpenCreate(parentTaskRef = item.todo.taskRef, parentTitle = item.todo.title)) }
                            } else null,
                            nowEpochMs = nowEpochMs
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (deleteCandidate != null) {
        DeleteConfirmationDialog(
            todo = deleteCandidate,
            childCount = deleteCandidateChildCount,
            onConfirm = { onAction(TodoAction.ConfirmDelete) },
            onCancel = { onAction(TodoAction.CancelDelete) }
        )
    }
}
@Composable
private fun UnresolvedSubtaskSection(
    unresolvedChildren: List<Todo>,
    onAction: (TodoAction) -> Unit,
    nowEpochMs: Long,
    accountKey: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TabbyRushThemeTokens.SpacingSmall),
        colors = CardDefaults.cardColors(
            containerColor = TabbyRushThemeTokens.customColors.coralSurface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(TabbyRushThemeTokens.SpacingMedium)) {
            Text(
                text = "Unresolved Subtasks (${unresolvedChildren.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
            Text(
                text = "These subtasks reference a parent task that is missing from local storage. They are isolated from normal lists and schedules until resolved or deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))
            Column(verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)) {
                unresolvedChildren.forEach { child ->
                    val isUnavailable = child.accessState?.status == TaskAccessStatus.Unavailable
                    val isOwner = !isUnavailable && accountKey != null && child.taskRef.ownerUserId == accountKey
                    val canDelete = if (isUnavailable) false else if (isOwner) true else (child.accessState?.canDeleteTask == true)
                    TodoRow(
                        item = HierarchicalTodoItem(
                            todo = child,
                            effectiveCompleted = child.completed,
                            hasLiveChildren = false,
                            depth = 0
                        ),
                        currentUserId = accountKey,
                        onToggle = { onAction(TodoAction.ToggleCompleted(child)) },
                        onEdit = { onAction(TodoAction.OpenEdit(child)) },
                        onDelete = if (canDelete) { { onAction(TodoAction.RequestDelete(child)) } } else null,
                        onAddSubtask = null,
                        nowEpochMs = nowEpochMs
                    )
                }
            }
        }
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
    item: HierarchicalTodoItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onAddSubtask: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    nowEpochMs: Long = 0L,
    isCollapsed: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    currentUserId: String? = null
) {
    val todo = item.todo
    val isChildOnly = item.isChildOnly
    val currentClockMs = if (nowEpochMs > 0L) nowEpochMs else System.currentTimeMillis()
    val startPadding = if (!isChildOnly && item.depth > 0) TabbyRushThemeTokens.SpacingXXLarge else 0.dp
    val deadlinePresentation = TaskDeadlinePresentation.formatDeadline(
        deadline = todo.deadline,
        effectiveCompleted = item.effectiveCompleted,
        nowEpochMs = currentClockMs
    )
    val isUnavailable = todo.accessState?.status == TaskAccessStatus.Unavailable
    val isOwner = !isUnavailable && currentUserId != null && todo.taskRef.ownerUserId == currentUserId
    val canEdit = if (isUnavailable) false else if (isOwner) true else (todo.accessState?.canEditContent == true)
    val canDelete = (if (isUnavailable) false else if (isOwner) true else (todo.accessState?.canDeleteTask == true)) && onDelete != null
    val canCreateChild = (if (isUnavailable) false else if (isOwner) true else (todo.accessState?.canCreateChild == true)) && onAddSubtask != null && !isChildOnly
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
            .border(
                1.dp,
                if (item.isContextOnly) TabbyRushThemeTokens.customColors.line.copy(alpha = 0.5f)
                else TabbyRushThemeTokens.customColors.line,
                MaterialTheme.shapes.small
            ),
        shape = MaterialTheme.shapes.small,
        color = if (item.effectiveCompleted) TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.5f)
        else if (item.isContextOnly) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(TabbyRushThemeTokens.SpacingMedium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.hasLiveChildren && !isChildOnly && onToggleExpand != null) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .size(width = 32.dp, height = TabbyRushThemeTokens.MinTouchTargetSize)
                        .semantics {
                            stateDescription = if (isCollapsed) {
                                "Collapsed (${item.childCount} hidden)"
                            } else {
                                "Expanded (${item.childCount} visible)"
                            }
                            contentDescription = if (isCollapsed) {
                                "Expand ${todo.title}"
                            } else {
                                "Collapse ${todo.title}"
                            }
                        }
                ) {
                    Text(
                        text = if (isCollapsed) "▶" else "▼",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }

            if (item.hasLiveChildren && !isChildOnly) {
                Checkbox(
                    checked = item.effectiveCompleted,
                    onCheckedChange = null,
                    enabled = false,
                    colors = CheckboxDefaults.colors(
                        disabledCheckedColor = MaterialTheme.colorScheme.primary,
                        disabledUncheckedColor = TabbyRushThemeTokens.customColors.muted
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .semantics {
                            stateDescription = if (item.effectiveCompleted) {
                                "Completed (automatic: ${item.completedChildCount}/${item.childCount})"
                            } else {
                                "Active (automatic: ${item.completedChildCount}/${item.childCount})"
                            }
                            contentDescription = "Automatic progress for ${todo.title}"
                        }
                )
            } else {
                Checkbox(
                    checked = item.effectiveCompleted,
                    onCheckedChange = if (canEdit) { { onToggle() } } else null,
                    enabled = canEdit,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = TabbyRushThemeTokens.customColors.muted
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .semantics {
                            stateDescription = if (isChildOnly) {
                                if (item.effectiveCompleted) "Completed shared child" else "Active shared child"
                            } else if (item.depth > 0) {
                                if (item.effectiveCompleted) "Completed subtask" else "Active subtask"
                            } else {
                                if (item.effectiveCompleted) "Completed" else "Active"
                            }
                            contentDescription = if (isChildOnly) "Toggle shared child ${todo.title}" else if (item.depth > 0) "Toggle subtask ${todo.title}" else "Toggle completion for ${todo.title}"
                        }
                )
            }

            Spacer(modifier = Modifier.width(TabbyRushThemeTokens.SpacingSmall))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (item.isContextOnly) {
                    Text(
                        text = "Parent context",
                        style = MaterialTheme.typography.labelSmall,
                        color = TabbyRushThemeTokens.customColors.muted
                    )
                } else if (isChildOnly) {
                    Text(
                        text = "Shared child",
                        style = MaterialTheme.typography.labelSmall,
                        color = TabbyRushThemeTokens.customColors.primaryDeep
                    )
                }

                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.effectiveCompleted) TabbyRushThemeTokens.customColors.muted
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.effectiveCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                if (item.hasLiveChildren && !isChildOnly) {
                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                    Text(
                        text = "${item.completedChildCount}/${item.childCount} completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = TabbyRushThemeTokens.customColors.primaryDeep
                    )
                }

                if (deadlinePresentation != null) {
                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall),
                        modifier = Modifier.semantics {
                            contentDescription = deadlinePresentation.contentDescription
                        }
                    ) {
                        Text(
                            text = deadlinePresentation.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TabbyRushThemeTokens.customColors.primaryDeep
                        )

                        if (deadlinePresentation.isOverdue) {
                            Text(
                                text = deadlinePresentation.overdueLabel ?: "Overdue",
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

            if (canCreateChild) {
                TextButton(
                    onClick = onAddSubtask,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                        .semantics {
                            contentDescription = "Add subtask to ${todo.title}"
                        }
                ) {
                    Text(
                        text = "+ Subtask",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (canEdit) {
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                        .semantics {
                            contentDescription = "Edit ${todo.title}"
                        }
                ) {
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (canDelete) {
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                        .semantics {
                            contentDescription = if (item.hasLiveChildren) "Delete group ${todo.title}" else "Delete ${todo.title}"
                        }
                ) {
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
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
    childCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val isGroup = childCount > 0
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(
                text = if (isGroup) "Delete Group?" else "Delete Task?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = if (isGroup) {
                    "Are you sure you want to delete '${todo.title}' and its $childCount subtasks? Permanent deletion cannot be undone once committed to the server."
                } else {
                    "Are you sure you want to delete this task? Permanent deletion cannot be undone once committed to the server."
                },
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
                Text(if (isGroup) "Confirm Delete Group" else "Confirm Delete")
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
