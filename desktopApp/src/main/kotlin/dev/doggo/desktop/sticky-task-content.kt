package dev.doggo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import dev.doggo.shared.theme.DoggoPrimary
import dev.doggo.shared.theme.DoggoWhite
import dev.doggo.shared.theme.DoggoPage
import dev.doggo.shared.theme.DoggoInk
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.doggo.shared.contract.SurfaceAccountToken
import dev.doggo.shared.contract.SurfaceReadiness
import dev.doggo.shared.contract.Todo
import dev.doggo.shared.theme.DoggoTheme
import dev.doggo.shared.theme.DoggoThemeTokens

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
    modifier: Modifier = Modifier
) {
    DoggoTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = DoggoPage
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DoggoThemeTokens.SpacingMedium)
            ) {
                // Persistent recovery header: Open App and Quit controls are always accessible
                StickyHeaderBar(
                    onOpenApp = { onAction(StickyTaskAction.OpenApp) },
                    onQuit = { onAction(StickyTaskAction.Quit) }
                )

                Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingSmall))

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
                            subtitle = "Open the main Doggo app to sign in to your account.",
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

@Composable
private fun StickyHeaderBar(
    onOpenApp: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Heading is flexible to prevent crowding out recovery controls on narrow viewports
        Text(
            text = "Doggo Sticky",
            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
            color = DoggoPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))

        // Recovery buttons retain dedicated >=48dp click targets and never get squeezed
        Row(
            horizontalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingXSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onOpenApp,
                modifier = Modifier
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    .semantics { contentDescription = "Open main application window" }
            ) {
                Text(
                    text = "Open App",
                    style = MaterialTheme.typography.button,
                    color = DoggoPrimary
                )
            }

            TextButton(
                onClick = onQuit,
                modifier = Modifier
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    .semantics { contentDescription = "Quit Doggo application" }
            ) {
                Text(
                    text = "Quit",
                    style = MaterialTheme.typography.button,
                    color = DoggoThemeTokens.customColors.coral
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
        // Independent quick-add inline input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.quickAdd.rawTitle,
                onValueChange = { onAction(StickyTaskAction.ChangeTitle(token, it)) },
                enabled = !state.quickAdd.submitting,
                placeholder = { Text("Quick add task…", style = MaterialTheme.typography.body2) },
                singleLine = true,
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
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
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
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = DoggoPrimary,
                    unfocusedBorderColor = DoggoThemeTokens.customColors.outline,
                    cursorColor = DoggoPrimary
                )
            )

            Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))

            Button(
                onClick = { onAction(StickyTaskAction.Submit(token)) },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = DoggoPrimary,
                    contentColor = DoggoWhite
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    .semantics { contentDescription = "Submit quick add task" }
            ) {
                if (state.quickAdd.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = DoggoWhite
                    )
                } else {
                    Text("Add", style = MaterialTheme.typography.button)
                }
            }
        }

        // Inline error banner if validation or submission failed
        if (state.quickAdd.error != null) {
            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingXSmall))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DoggoThemeTokens.customColors.coralSurface, MaterialTheme.shapes.small)
                    .padding(DoggoThemeTokens.SpacingSmall)
                    .semantics { contentDescription = "Quick add error: ${state.quickAdd.error}" }
            ) {
                Text(
                    text = state.quickAdd.error,
                    style = MaterialTheme.typography.caption,
                    color = DoggoThemeTokens.customColors.coral
                )
            }
        }

        Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingMedium))

        // Single scrollable column: Incomplete then Completed
        val incomplete = state.sections.incomplete
        val completed = state.sections.completed

        if (incomplete.isEmpty() && completed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DoggoThemeTokens.SpacingLarge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tasks yet. Add one above!",
                    style = MaterialTheme.typography.body2,
                    color = DoggoThemeTokens.customColors.muted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingXSmall)
            ) {
                if (incomplete.isNotEmpty()) {
                    item(key = "header_incomplete") {
                        SectionHeader(title = "To Do", count = incomplete.size)
                    }

                    items(
                        items = incomplete,
                        key = { "inc_${it.id}" }
                    ) { todo ->
                        CompactTaskRow(
                            todo = todo,
                            onToggle = { desired ->
                                onAction(StickyTaskAction.SetCompleted(token, todo.id, desired))
                            }
                        )
                    }
                }

                if (completed.isNotEmpty()) {
                    item(key = "header_completed") {
                        if (incomplete.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingSmall))
                        }
                        SectionHeader(title = "Completed", count = completed.size)
                    }

                    items(
                        items = completed,
                        key = { "comp_${it.id}" }
                    ) { todo ->
                        CompactTaskRow(
                            todo = todo,
                            onToggle = { desired ->
                                onAction(StickyTaskAction.SetCompleted(token, todo.id, desired))
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
        color = DoggoThemeTokens.customColors.muted,
        modifier = Modifier.padding(vertical = DoggoThemeTokens.SpacingXSmall)
    )
}

@Composable
private fun CompactTaskRow(
    todo: Todo,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
            .border(1.dp, DoggoThemeTokens.customColors.line, MaterialTheme.shapes.small),
        shape = MaterialTheme.shapes.small,
        color = if (todo.completed) {
            DoggoThemeTokens.customColors.mintSurface.copy(alpha = 0.4f)
        } else {
            DoggoWhite
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = DoggoThemeTokens.SpacingSmall,
                    vertical = DoggoThemeTokens.SpacingXSmall
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = todo.completed,
                onCheckedChange = { checked -> onToggle(checked) },
                colors = CheckboxDefaults.colors(
                    checkedColor = DoggoPrimary,
                    uncheckedColor = DoggoThemeTokens.customColors.outline
                ),
                modifier = Modifier
                    .size(DoggoThemeTokens.MinTouchTargetSize)
                    .semantics {
                        stateDescription = if (todo.completed) "Completed" else "Active"
                        contentDescription = "Toggle completion for ${todo.title}"
                    }
            )

            Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))

            Text(
                text = todo.title,
                style = MaterialTheme.typography.body1,
                color = if (todo.completed) {
                    DoggoThemeTokens.customColors.muted
                } else {
                    DoggoThemeTokens.customColors.primaryDeep
                },
                textDecoration = if (todo.completed) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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
            verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingMedium),
            modifier = Modifier.padding(DoggoThemeTokens.SpacingLarge)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = DoggoPrimary
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                color = DoggoPrimary
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.body2,
                color = DoggoThemeTokens.customColors.muted
            )


            Button(
                onClick = onOpenApp,
                modifier = Modifier.defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
            ) {
                Text("Open Main App")
            }
        }
    }
}
