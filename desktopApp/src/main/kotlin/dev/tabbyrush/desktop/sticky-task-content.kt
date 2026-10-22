package dev.tabbyrush.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import kotlin.math.roundToInt
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import dev.tabbyrush.shared.theme.TabbyRushPrimary
import dev.tabbyrush.shared.theme.TabbyRushWhite
import dev.tabbyrush.shared.theme.TabbyRushPage
import dev.tabbyrush.shared.theme.TabbyRushInk
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.contract.SurfaceAccountToken
import dev.tabbyrush.shared.contract.SurfaceReadiness
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.theme.TabbyRushTheme
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens

/**
 * Compact sticky task composable presented in the pinned desktop companion window.
 *
 * Requirements:
 * 1. Shows Incomplete tasks then Completed tasks in a single scrollable column in stable order.
 * 2. Independent quick-add inline field with raw title validation, error display, and Enter-to-submit.
 * 3. Never mutates main editor state or dispatches main TodoActions.
 * 4. Open app and Quit controls remain permanently available for recovery regardless of tray status.
 * 5. High-contrast accessible styling, minimum 48dp touch targets, semantic descriptions.
 * 6. Non-Ready states render dedicated recovery/status views; stale tasks/inputs are never shown.
 */
@Composable
fun StickyTaskContent(
    state: StickyTaskState,
    onAction: (StickyTaskAction) -> Unit,
    modifier: Modifier = Modifier,
    effectiveFontScale: Float = state.fontScale
) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            onAction(StickyTaskAction.RefreshDeadlines)
        }
    }
    TabbyRushTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = TabbyRushPage
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                // Persistent recovery header: Open App, Quit, and font zoom controls are always accessible
                StickyHeaderBar(
                    effectiveFontScale = effectiveFontScale,
                    manualZoom = state.fontScale,
                    onAction = onAction,
                    onOpenApp = { onAction(StickyTaskAction.OpenApp) },
                    onQuit = { onAction(StickyTaskAction.Quit) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Content area gated on SurfaceReadiness
                when (val readiness = state.readiness) {
                    is SurfaceReadiness.Ready -> {
                        ReadyStickyContent(
                            token = readiness.token,
                            state = state,
                            onAction = onAction
                        )
                    }
                    is SurfaceReadiness.Restoring -> {
                        ReadinessStatusView(
                            title = "Restoring Tasks…",
                            subtitle = "Please wait while the active session is verified.",
                            isLoading = true,
                            onOpenApp = { onAction(StickyTaskAction.OpenApp) }
                        )
                    }
                    is SurfaceReadiness.SignedOut -> {
                        ReadinessStatusView(
                            title = "Signed Out",
                            subtitle = "Open the main TabbyRush app to sign in to your account.",
                            isLoading = false,
                            onOpenApp = { onAction(StickyTaskAction.OpenApp) }
                        )
                    }
                    is SurfaceReadiness.Locked -> {
                        ReadinessStatusView(
                            title = "Storage Locked",
                            subtitle = "Unlock your device credentials to access your tasks.",
                            isLoading = false,
                            onOpenApp = { onAction(StickyTaskAction.OpenApp) }
                        )
                    }
                    is SurfaceReadiness.Unavailable -> {
                        ReadinessStatusView(
                            title = "Service Unavailable",
                            subtitle = "Task storage is temporarily unavailable. Open the main app for details.",
                            isLoading = false,
                            onOpenApp = { onAction(StickyTaskAction.OpenApp) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickyHeaderBar(
    effectiveFontScale: Float,
    manualZoom: Float,
    onAction: (StickyTaskAction) -> Unit,
    onOpenApp: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Heading is flexible to prevent crowding out zoom and recovery controls on narrow viewports
        Text(
            text = "TabbyRush Sticky",
            style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
            color = TabbyRushPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterVertically)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Zoom controls cluster
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { onAction(StickyTaskAction.ZoomOut) },
                enabled = effectiveFontScale > MIN_STICKY_FONT_SCALE && manualZoom > MIN_STICKY_FONT_SCALE,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                    .semantics { contentDescription = "Decrease font size" }
            ) {
                Text(
                    text = "A−",
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (effectiveFontScale > MIN_STICKY_FONT_SCALE && manualZoom > MIN_STICKY_FONT_SCALE) TabbyRushPrimary else TabbyRushThemeTokens.customColors.muted
                )
            }

            TextButton(
                onClick = { onAction(StickyTaskAction.ResetZoom) },
                enabled = manualZoom != DEFAULT_STICKY_FONT_SCALE,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .defaultMinSize(minWidth = 32.dp, minHeight = 24.dp)
                    .semantics {
                        contentDescription = if (manualZoom != DEFAULT_STICKY_FONT_SCALE) {
                            "Reset to automatic size"
                        } else {
                            "Automatic size"
                        }
                        stateDescription = "${(effectiveFontScale * 100).roundToInt()}%"
                    }
            ) {
                Text(
                    text = "${(effectiveFontScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (manualZoom != DEFAULT_STICKY_FONT_SCALE) TabbyRushPrimary else TabbyRushThemeTokens.customColors.muted
                )
            }

            TextButton(
                onClick = { onAction(StickyTaskAction.ZoomIn) },
                enabled = effectiveFontScale < MAX_STICKY_FONT_SCALE && manualZoom < MAX_STICKY_FONT_SCALE,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                    .semantics { contentDescription = "Increase font size" }
            ) {
                Text(
                    text = "A+",
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (effectiveFontScale < MAX_STICKY_FONT_SCALE && manualZoom < MAX_STICKY_FONT_SCALE) TabbyRushPrimary else TabbyRushThemeTokens.customColors.muted
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Recovery buttons cluster
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onOpenApp,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 26.dp)
                    .semantics { contentDescription = "Open main application window" }
            ) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = TabbyRushPrimary
                )
            }

            TextButton(
                onClick = onQuit,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 26.dp)
                    .semantics { contentDescription = "Quit TabbyRush application" }
            ) {
                Text(
                    text = "Quit",
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = TabbyRushThemeTokens.customColors.coral
                )
            }
        }
    }
}

@Composable
private fun ReadyStickyContent(
    token: SurfaceAccountToken,
    state: StickyTaskState,
    onAction: (StickyTaskAction) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val canSubmit = state.quickAdd.rawTitle.isNotBlank() && !state.quickAdd.submitting

    Column(modifier = Modifier.fillMaxSize()) {
        // Independent compact quick-add inline input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = state.quickAdd.rawTitle,
                onValueChange = { onAction(StickyTaskAction.ChangeTitle(token, it)) },
                enabled = !state.quickAdd.submitting,
                singleLine = true,
                textStyle = MaterialTheme.typography.body2.copy(color = TabbyRushInk),
                cursorBrush = SolidColor(TabbyRushPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canSubmit) {
                            onAction(StickyTaskAction.Submit(token))
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 32.dp)
                    .border(1.dp, TabbyRushThemeTokens.customColors.outline, MaterialTheme.shapes.small)
                    .background(TabbyRushWhite, MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Enter -> {
                                    if (canSubmit) {
                                        onAction(StickyTaskAction.Submit(token))
                                    }
                                    true
                                }
                                Key.Escape -> {
                                    focusManager.clearFocus()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .semantics { contentDescription = "Quick add task title input" },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.quickAdd.rawTitle.isEmpty()) {
                            Text(
                                text = "Quick add task…",
                                 style = MaterialTheme.typography.body2,
                                color = TabbyRushThemeTokens.customColors.muted
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = { onAction(StickyTaskAction.Submit(token)) },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = TabbyRushPrimary,
                    contentColor = TabbyRushWhite
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 32.dp)
                    .semantics { contentDescription = "Submit quick add task" }
            ) {
                if (state.quickAdd.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = TabbyRushWhite
                    )
                } else {
                    Text("Add", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Inline error banner if validation or submission failed
        if (state.quickAdd.error != null) {
            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TabbyRushThemeTokens.customColors.coralSurface, MaterialTheme.shapes.small)
                    .padding(TabbyRushThemeTokens.SpacingSmall)
                    .semantics { contentDescription = "Quick add error: ${state.quickAdd.error}" }
            ) {
                Text(
                    text = state.quickAdd.error,
                    style = MaterialTheme.typography.caption,
                    color = TabbyRushThemeTokens.customColors.coral
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Single scrollable column: Incomplete then Completed whole compact groups
        val incomplete = state.projectedSections.incomplete
        val completed = state.projectedSections.completed
        var expandedParentRefs by remember(token) { mutableStateOf(emptySet<TaskRef>()) }

        val visibleIncomplete = remember(incomplete, expandedParentRefs) {
            incomplete.filterNot { it.isChild && it.todo.parentTaskRef != null && !expandedParentRefs.contains(it.todo.parentTaskRef) }
        }
        val visibleCompleted = remember(completed, expandedParentRefs) {
            completed.filterNot { it.isChild && it.todo.parentTaskRef != null && !expandedParentRefs.contains(it.todo.parentTaskRef) }
        }
        if (incomplete.isEmpty() && completed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(TabbyRushThemeTokens.SpacingLarge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tasks yet. Add one above!",
                    style = MaterialTheme.typography.body2,
                    color = TabbyRushThemeTokens.customColors.muted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (incomplete.isNotEmpty()) {
                    item(key = "header_incomplete") {
                        SectionHeader(title = "To Do", count = incomplete.size)
                    }

                    items(
                        items = visibleIncomplete,
                        key = { "inc_${it.todo.taskRef.ownerUserId}_${it.todo.taskRef.rowId}" }
                    ) { item ->
                        val isCollapsed = !expandedParentRefs.contains(item.todo.taskRef)
                        CompactTaskRow(
                            item = item,
                            isCollapsed = isCollapsed,
                            onToggleExpand = if (item.hasLiveChildren) {
                                {
                                    expandedParentRefs = if (isCollapsed) {
                                        expandedParentRefs + item.todo.taskRef
                                    } else {
                                        expandedParentRefs - item.todo.taskRef
                                    }
                                }
                            } else null,
                            onToggle = { desired ->
                                onAction(StickyTaskAction.SetCompleted(token, item.todo.taskRef, desired))
                            }
                        )
                    }
                }

                if (completed.isNotEmpty()) {
                    item(key = "header_completed") {
                        if (incomplete.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                        }
                        SectionHeader(title = "Completed", count = completed.size)
                    }

                    items(
                        items = visibleCompleted,
                        key = { "comp_${it.todo.taskRef.ownerUserId}_${it.todo.taskRef.rowId}" }
                    ) { item ->
                        val isCollapsed = !expandedParentRefs.contains(item.todo.taskRef)
                        CompactTaskRow(
                            item = item,
                            isCollapsed = isCollapsed,
                            onToggleExpand = if (item.hasLiveChildren) {
                                {
                                    expandedParentRefs = if (isCollapsed) {
                                        expandedParentRefs + item.todo.taskRef
                                    } else {
                                        expandedParentRefs - item.todo.taskRef
                                    }
                                }
                            } else null,
                            onToggle = { desired ->
                                onAction(StickyTaskAction.SetCompleted(token, item.todo.taskRef, desired))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = TabbyRushThemeTokens.customColors.muted,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactTaskRow(
    item: StickyTaskItem,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isCollapsed: Boolean = false,
    onToggleExpand: (() -> Unit)? = null
) {
    val todo = item.todo
    val isCompleted = item.effectiveCompleted
    val canEdit = item.todo.accessState?.canEditContent ?: true
    val startPadding = if (item.isChild) 12.dp else 0.dp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .defaultMinSize(minHeight = 28.dp)
            .border(1.dp, TabbyRushThemeTokens.customColors.line, MaterialTheme.shapes.small),
        shape = MaterialTheme.shapes.small,
        color = if (isCompleted) {
            TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.4f)
        } else {
            TabbyRushWhite
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 6.dp,
                    vertical = 3.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.hasLiveChildren && onToggleExpand != null) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .size(22.dp)
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
                        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                        color = TabbyRushPrimary
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }

            if (item.hasLiveChildren) {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = null,
                    enabled = false,
                    colors = CheckboxDefaults.colors(
                        disabledColor = if (isCompleted) TabbyRushPrimary else TabbyRushThemeTokens.customColors.outline
                    ),
                    modifier = Modifier
                        .size(22.dp)
                        .semantics {
                            stateDescription = if (isCompleted) {
                                "Completed (automatic: ${item.completedChildCount}/${item.childCount})"
                            } else {
                                "Active (automatic: ${item.completedChildCount}/${item.childCount})"
                            }
                            contentDescription = "Automatic progress for ${todo.title}"
                        }
                )
            } else {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = if (canEdit) { { checked -> onToggle(checked) } } else null,
                    enabled = canEdit,
                    colors = CheckboxDefaults.colors(
                        checkedColor = TabbyRushPrimary,
                        uncheckedColor = TabbyRushThemeTokens.customColors.outline,
                        disabledColor = if (isCompleted) TabbyRushPrimary.copy(alpha = 0.5f) else TabbyRushThemeTokens.customColors.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .size(22.dp)
                        .semantics {
                            stateDescription = if (item.isChild) {
                                if (isCompleted) "Completed subtask" else "Active subtask"
                            } else {
                                if (isCompleted) "Completed" else "Active"
                            }
                            contentDescription = if (item.isChild) {
                                "Toggle subtask ${todo.title}"
                            } else {
                                "Toggle completion for ${todo.title}"
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.body2,
                    color = if (isCompleted) {
                        TabbyRushThemeTokens.customColors.muted
                    } else {
                        TabbyRushThemeTokens.customColors.primaryDeep
                    },
                    textDecoration = if (isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.hasLiveChildren) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.completedChildCount}/${item.childCount} completed",
                        style = MaterialTheme.typography.caption,
                        color = TabbyRushThemeTokens.customColors.primaryDeep,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }

                val deadlinePres = item.deadlinePresentation
                if (deadlinePres != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = deadlinePres.contentDescription
                        }
                    ) {
                        Text(
                            text = deadlinePres.label,
                            style = MaterialTheme.typography.caption,
                            color = TabbyRushThemeTokens.customColors.primaryDeep
                        )

                        if (deadlinePres.isOverdue) {
                            Text(
                                text = deadlinePres.overdueLabel ?: "Overdue",
                                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colors.error,
                                modifier = Modifier
                                    .background(
                                        TabbyRushThemeTokens.customColors.coralSurface,
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessStatusView(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = TabbyRushPrimary
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                color = TabbyRushPrimary
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.body2,
                color = TabbyRushThemeTokens.customColors.muted
            )


            Button(
                onClick = onOpenApp,
                modifier = Modifier.defaultMinSize(minHeight = 32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Open Main App")
            }
        }
    }
}
