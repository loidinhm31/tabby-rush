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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.AssignmentIntent
import dev.tabbyrush.shared.contract.TaskRef
import dev.tabbyrush.shared.contract.UserLookupCache
import dev.tabbyrush.shared.contract.UserLookupResult
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens
import kotlinx.coroutines.launch

/**
 * Task assignee management section for task creators.
 *
 * Displays direct vs inherited assignee status using clear, explicit text (never color alone).
 * Provides username lookup with stable request identity to ignore obsolete responses,
 * allows setting or clearing direct assignees, and presents separate feedback for initial
 * task-creation assignments awaiting sync versus existing task slot updates.
 */
@Composable
fun TaskAssigneePicker(
    assigneeUserId: String?,
    assigneeUsername: String? = null,
    inheritedAssigneeUserId: String? = null,
    inheritedAssigneeUsername: String? = null,
    canManageAssignment: Boolean = true,
    isInitialAssignmentForNewTask: Boolean = false,
    expectedSlotVersion: Long = 0L,
    slotConflictMessage: String? = null,
    taskRef: TaskRef? = null,
    onAssigneeChanged: (String?) -> Unit,
    onAssignTask: ((AssignmentIntent) -> Unit)? = null,
    onLookupUser: (suspend (String) -> ApiResult<UserLookupResult>)? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var searchUsername by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var lookupResult by remember { mutableStateOf<UserLookupResult?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var activeRequestId by remember { mutableStateOf(0L) }
    var localAssigneeUsername by remember(assigneeUserId) {
        mutableStateOf(assigneeUsername ?: UserLookupCache.getUsername(assigneeUserId))
    }
    val effectiveAssigneeUsername = assigneeUsername ?: localAssigneeUsername ?: UserLookupCache.getUsername(assigneeUserId)
    val effectiveInheritedUsername = inheritedAssigneeUsername ?: UserLookupCache.getUsername(inheritedAssigneeUserId)
    val scope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TabbyRushThemeTokens.customColors.line, MaterialTheme.shapes.small),
        colors = CardDefaults.cardColors(
            containerColor = TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TabbyRushThemeTokens.SpacingMedium)
        ) {
            // Header with clear accessible semantic heading
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assignment",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                if (canManageAssignment && !isExpanded) {
                    OutlinedButton(
                        onClick = { isExpanded = true },
                        modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (assigneeUserId != null) "Change" else "Assign",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

            // Direct vs Inherited Assignee Indicators (Explicit clear text, never colors alone)
            when {
                assigneeUserId != null -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (!effectiveAssigneeUsername.isNullOrBlank()) {
                            Text(
                                text = "Direct assignee: $effectiveAssigneeUsername",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TabbyRushThemeTokens.customColors.primaryDeep
                            )
                            Text(
                                text = "User ID: $assigneeUserId",
                                style = MaterialTheme.typography.bodySmall,
                                color = TabbyRushThemeTokens.customColors.muted
                            )
                        } else {
                            Text(
                                text = "Direct assignee: $assigneeUserId",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TabbyRushThemeTokens.customColors.primaryDeep
                            )
                        }

                        if (inheritedAssigneeUserId != null && inheritedAssigneeUserId != assigneeUserId) {
                            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                            if (!effectiveInheritedUsername.isNullOrBlank()) {
                                Text(
                                    text = "Overrides inherited assignee from parent: $effectiveInheritedUsername",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TabbyRushThemeTokens.customColors.muted
                                )
                                Text(
                                    text = "Parent assignee ID: $inheritedAssigneeUserId",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TabbyRushThemeTokens.customColors.muted
                                )
                            } else {
                                Text(
                                    text = "Overrides inherited assignee from parent: $inheritedAssigneeUserId",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TabbyRushThemeTokens.customColors.muted
                                )
                            }
                        }

                        if (canManageAssignment) {
                            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                            OutlinedButton(
                                onClick = {
                                    localAssigneeUsername = null
                                    onAssigneeChanged(null)
                                    if (taskRef != null && onAssignTask != null) {
                                        onAssignTask(
                                            AssignmentIntent(
                                                taskRef = taskRef,
                                                desiredRecipientUserId = null,
                                                expectedSlotVersion = expectedSlotVersion
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "Clear Direct Assignee",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                inheritedAssigneeUserId != null -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (!effectiveInheritedUsername.isNullOrBlank()) {
                            Text(
                                text = "Inherited assignee: $effectiveInheritedUsername (from parent task)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TabbyRushThemeTokens.customColors.primaryDeep
                            )
                            Text(
                                text = "User ID: $inheritedAssigneeUserId",
                                style = MaterialTheme.typography.bodySmall,
                                color = TabbyRushThemeTokens.customColors.muted
                            )
                        } else {
                            Text(
                                text = "Inherited assignee: $inheritedAssigneeUserId (from parent task)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TabbyRushThemeTokens.customColors.primaryDeep
                            )
                        }
                        Text(
                            text = "No direct assignee is set on this subtask.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TabbyRushThemeTokens.customColors.muted
                        )
                    }
                }

                else -> {
                    Text(
                        text = "No assignee (unassigned)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TabbyRushThemeTokens.customColors.muted
                    )
                }
            }

            // Notice for new task creation: initial assignment awaits task creation and sync
            if (isInitialAssignmentForNewTask && assigneeUserId != null) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = TabbyRushThemeTokens.customColors.mintSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Initial assignment will be applied after the task is created and synced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TabbyRushThemeTokens.customColors.primaryDeep,
                        modifier = Modifier.padding(TabbyRushThemeTokens.SpacingSmall)
                    )
                }
            }

            // Slot version conflict feedback
            if (slotConflictMessage != null) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = TabbyRushThemeTokens.customColors.coralSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = slotConflictMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(TabbyRushThemeTokens.SpacingSmall)
                    )
                }
            }

            // Assignee restriction notice: non-creators cannot manage assignment
            if (!canManageAssignment) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                Text(
                    text = "Only the task creator can change or remove assignment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TabbyRushThemeTokens.customColors.muted
                )
            }

            // Expanded username lookup and selection form for creators
            if (canManageAssignment && isExpanded) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))

                Text(
                    text = "Look up user by username to assign:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
                ) {
                    OutlinedTextField(
                        value = searchUsername,
                        onValueChange = {
                            searchUsername = it
                            lookupError = null
                        },
                        label = { Text("Username") },
                        placeholder = { Text("e.g. alice") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searchUsername.isNotBlank() && !isSearching) {
                                executeLookup(
                                    query = searchUsername.trim(),
                                    taskRef = taskRef,
                                    onLookupUser = onLookupUser,
                                    onStart = {
                                        activeRequestId++
                                        isSearching = true
                                        lookupError = null
                                        lookupResult = null
                                    },
                                    onComplete = { reqId, result, error ->
                                        if (activeRequestId == reqId) {
                                            isSearching = false
                                            lookupResult = result
                                            lookupError = error
                                        }
                                    },
                                    scope = scope,
                                    requestId = activeRequestId + 1
                                )
                            }
                        }),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                    )

                    Button(
                        onClick = {
                            if (searchUsername.isNotBlank() && !isSearching) {
                                executeLookup(
                                    query = searchUsername.trim(),
                                    taskRef = taskRef,
                                    onLookupUser = onLookupUser,
                                    onStart = {
                                        activeRequestId++
                                        isSearching = true
                                        lookupError = null
                                        lookupResult = null
                                    },
                                    onComplete = { reqId, result, error ->
                                        if (activeRequestId == reqId) {
                                            isSearching = false
                                            lookupResult = result
                                            lookupError = error
                                        }
                                    },
                                    scope = scope,
                                    requestId = activeRequestId + 1
                                )
                            }
                        },
                        enabled = searchUsername.isNotBlank() && !isSearching,
                        modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        shape = MaterialTheme.shapes.small
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(16.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Look Up")
                        }
                    }
                }

                // Error feedback
                if (lookupError != null) {
                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
                    Text(
                        text = lookupError ?: "User lookup failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                // Resolved user card with confirmation button
                val resolved = lookupResult
                if (resolved != null) {
                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = TabbyRushThemeTokens.customColors.mintSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(TabbyRushThemeTokens.SpacingMedium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Found: ${resolved.username}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TabbyRushThemeTokens.customColors.primaryDeep
                                )
                                Text(
                                    text = "User ID: ${resolved.userId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TabbyRushThemeTokens.customColors.muted
                                )
                            }

                            Button(
                                onClick = {
                                    val selected = resolved
                                    localAssigneeUsername = selected.username
                                    UserLookupCache.remember(selected.userId, selected.username)
                                    onAssigneeChanged(selected.userId)
                                    if (taskRef != null && onAssignTask != null) {
                                        onAssignTask(
                                            AssignmentIntent(
                                                taskRef = taskRef,
                                                desiredRecipientUserId = selected.userId,
                                                expectedSlotVersion = expectedSlotVersion
                                            )
                                        )
                                    }
                                    isExpanded = false
                                    lookupResult = null
                                    searchUsername = ""
                                },
                                modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Assign")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                OutlinedButton(
                    onClick = {
                        isExpanded = false
                        lookupResult = null
                        lookupError = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Close Picker")
                }
            }
        }
    }
}

private fun executeLookup(
    query: String,
    taskRef: TaskRef?,
    onLookupUser: (suspend (String) -> ApiResult<UserLookupResult>)?,
    onStart: () -> Unit,
    onComplete: (Long, UserLookupResult?, String?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    requestId: Long
) {
    onStart()
    scope.launch {
        try {
            if (onLookupUser != null) {
                when (val result = onLookupUser(query)) {
                    is ApiResult.Success -> {
                        UserLookupCache.remember(result.value.userId, result.value.username)
                        onComplete(requestId, result.value, null)
                    }
                    is ApiResult.Failure -> {
                        val message = result.error.message.ifBlank { "User not found or ineligible" }
                        onComplete(requestId, null, message)
                    }
                }
            } else {
                // Standalone / test fallback: treat non-blank input as resolved candidate
                val fallbackUser = UserLookupResult(userId = query, username = query)
                UserLookupCache.remember(fallbackUser.userId, fallbackUser.username)
                onComplete(requestId, fallbackUser, null)
            }
        } catch (e: Exception) {
            onComplete(requestId, null, e.message ?: "Lookup request failed")
        }
    }
}
