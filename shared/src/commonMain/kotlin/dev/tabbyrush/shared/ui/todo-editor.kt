package dev.tabbyrush.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.EditorState
import dev.tabbyrush.shared.contract.TitlePolicy
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.UserLookupResult
import dev.tabbyrush.shared.contract.UserLookupCache
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens
@Composable
fun TodoEditor(
    editor: EditorState,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier,
    onLookupUser: (suspend (String) -> ApiResult<UserLookupResult>)? = null
) {
    val isCreating = editor.id == null
    val validation = remember(editor.rawTitle) {
        TitlePolicy.normalizeAndValidate(editor.rawTitle)
    }
    val isValid = validation is ApiResult.Success

    val deadlineValidation = remember(
        editor.hasDeadline,
        editor.deadlineDate,
        editor.hasTime,
        editor.deadlineTime,
        editor.deadlineZone,
        editor.chosenOffsetSeconds,
        editor.reminderAnchorTime,
        editor.reminderEnabled,
        editor.reminderLeadMinutes,
        editor.reminderIntervalMinutes,
        editor.reminderCount
    ) {
        DeadlineCalculatorHelpers.validateAndComputeDeadline(editor)
    }

    val isDeadlineValid = !editor.hasDeadline || (deadlineValidation is DeadlineValidationResult.Valid)
    val canSubmit = isValid && isDeadlineValid

    val validationError = when (validation) {
        is ApiResult.Failure -> {
            if (editor.rawTitle.isEmpty()) null
            else validation.error.message
        }
        is ApiResult.Success -> null
    }

    val displayError = editor.error ?: validationError
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Card(
        modifier = modifier
            .widthIn(max = 560.dp)
            .heightIn(max = 640.dp)
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
        ) {
            Text(
                text = if (isCreating) {
                    if (editor.parentTaskId != null) "New Subtask" else "New Task"
                } else "Edit Task",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {

            if (editor.parentTitle != null) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Subtask of: ${editor.parentTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TabbyRushThemeTokens.customColors.primaryDeep,
                        modifier = Modifier.padding(
                            horizontal = TabbyRushThemeTokens.SpacingMedium,
                            vertical = TabbyRushThemeTokens.SpacingSmall
                        )
                    )
                }
            } else if (editor.parentTaskRef != null) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Shared child task",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TabbyRushThemeTokens.customColors.primaryDeep,
                        modifier = Modifier.padding(
                            horizontal = TabbyRushThemeTokens.SpacingMedium,
                            vertical = TabbyRushThemeTokens.SpacingSmall
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))

            OutlinedTextField(
                value = editor.rawTitle,
                onValueChange = { onAction(TodoAction.ChangeDraftTitle(it)) },
                label = { Text("Task description") },
                placeholder = { Text("What needs to be done?") },
                singleLine = false,
                maxLines = 6,
                isError = displayError != null,
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (displayError != null) {
                            Text(
                                text = displayError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = "1–200 characters",
                                color = TabbyRushThemeTokens.customColors.muted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Text(
                            text = "${editor.rawTitle.codePointCount(0, editor.rawTitle.length)}/200",
                            color = TabbyRushThemeTokens.customColors.muted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canSubmit) {
                            onAction(TodoAction.SubmitEditor)
                        }
                    }
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                    .focusRequester(focusRequester)
            )

            DeadlineEditorSection(
                editor = editor,
                validationResult = deadlineValidation,
                onAction = onAction
            )

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))

            TaskAssigneePicker(
                assigneeUserId = editor.assigneeUserId,
                assigneeUsername = editor.assigneeUsername,
                inheritedAssigneeUserId = editor.inheritedAssigneeUserId,
                inheritedAssigneeUsername = editor.inheritedAssigneeUsername,
                canManageAssignment = editor.canManageAssignment,
                isInitialAssignmentForNewTask = isCreating,
                expectedSlotVersion = editor.expectedSlotVersion,
                taskRef = editor.taskRef,
                onAssigneeChanged = { newAssignee ->
                    val username = UserLookupCache.getUsername(newAssignee)
                    onAction(TodoAction.UpdateEditorState(editor.copy(
                        assigneeUserId = newAssignee,
                        assigneeUsername = username
                    )))
                },
                onAssignTask = { intent ->
                    onAction(TodoAction.AssignTask(intent))
                },
                onLookupUser = onLookupUser,
                modifier = Modifier.fillMaxWidth()
            )
            }

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onAction(TodoAction.CancelEditor) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = TabbyRushThemeTokens.customColors.muted
                    )
                }

                Button(
                    onClick = { onAction(TodoAction.SubmitEditor) },
                    enabled = canSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (isCreating) "Add Task" else "Save Changes",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private fun String.codePointCount(beginIndex: Int, endIndex: Int): Int {
    var count = 0
    var i = beginIndex
    while (i < endIndex) {
        val cp = this[i].code
        if (cp in 0xD800..0xDBFF && i + 1 < endIndex && this[i + 1].code in 0xDC00..0xDFFF) {
            i += 2
        } else {
            i += 1
        }
        count++
    }
    return count
}
