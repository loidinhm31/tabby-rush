package dev.tabbyrush.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.tabbyrush.shared.contract.AuthState
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxState
import dev.tabbyrush.shared.contract.TodoAction
import dev.tabbyrush.shared.contract.TodoUiState
import dev.tabbyrush.shared.theme.TabbyRushTheme
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens
import dev.tabbyrush.shared.resources.Res
import dev.tabbyrush.shared.resources.tabbyrush_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Root Composable for the TabbyRush application.
 * Consumes immutable [TodoUiState] and [RemoteInboxState] and emits user actions.
 *
 * Automatically adapts between a compact mobile stack and an expanded (>=840dp) sidebar layout.
 */
@Composable
fun TabbyRushApp(
    state: TodoUiState,
    remoteInboxState: RemoteInboxState,
    onAction: (TodoAction) -> Unit,
    onRemoteAction: (RemoteInboxAction) -> Unit,
    onAuthSubmit: (AuthSubmitAction) -> Unit,
    modifier: Modifier = Modifier
) {
    TabbyRushTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (state.authState) {
                AuthState.UNAUTHENTICATED,
                AuthState.AUTHENTICATING -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AccountForm(
                            authState = state.authState,
                            safeError = state.safeError,
                            onAuthSubmit = onAuthSubmit,
                            modifier = Modifier.widthIn(max = 480.dp)
                        )
                    }
                }

                AuthState.ACCESS_PENDING -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AccessRequestPanel(
                            accessRequest = state.accessRequest,
                            safeError = state.safeError,
                            onRequestAccess = { onAuthSubmit(AuthSubmitAction.RequestAccess(it)) },
                            onRefreshStatus = { onAuthSubmit(AuthSubmitAction.RefreshStatus) },
                            onLogout = { onAuthSubmit(AuthSubmitAction.Logout) },
                            modifier = Modifier.widthIn(max = 480.dp)
                        )
                    }
                }

                AuthState.REAUTH_REQUIRED -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AccountForm(
                            authState = state.authState,
                            safeError = state.safeError ?: SafeError.Unauthorized(
                                code = "REAUTH_REQUIRED",
                                message = "Your credentials must be refreshed. Local data is preserved."
                            ),
                            onAuthSubmit = onAuthSubmit,
                            modifier = Modifier.widthIn(max = 480.dp)
                        )
                    }
                }

                AuthState.AUTHENTICATED -> {
                    AuthenticatedWorkspace(
                        state = state,
                        remoteInboxState = remoteInboxState,
                        onAction = onAction,
                        onRemoteAction = onRemoteAction
                    )
                }
            }

            // Modal Overlays with explicit dismissal safety (prevent accidental outside-click draft loss)
            if (state.editor != null) {
                Dialog(
                    onDismissRequest = { /* Require explicit Cancel or Save button */ },
                    properties = DialogProperties(dismissOnClickOutside = false)
                ) {
                    TodoEditor(
                        editor = state.editor,
                        onAction = onAction
                    )
                }
            }

            if (state.conflict != null) {
                Dialog(
                    onDismissRequest = { onAction(TodoAction.DismissError) },
                    properties = DialogProperties(dismissOnClickOutside = false)
                ) {
                    ConflictReview(
                        conflict = state.conflict,
                        onAction = onAction
                    )
                }
            }

            if (state.leaveGuard != null) {
                Dialog(
                    onDismissRequest = { onAction(TodoAction.CancelLeave) },
                    properties = DialogProperties(dismissOnClickOutside = false)
                ) {
                    LeaveGuard(
                        leaveGuard = state.leaveGuard,
                        onAction = onAction
                    )
                }
            }
            if (state.settings != null) {
                Dialog(
                    onDismissRequest = { onAction(TodoAction.CloseSettings) },
                    properties = DialogProperties(dismissOnClickOutside = false)
                ) {
                    ReminderSettingsDialog(
                        settings = state.settings,
                        onAction = onAction
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(
    state: TodoUiState,
    remoteInboxState: RemoteInboxState,
    onAction: (TodoAction) -> Unit,
    onRemoteAction: (RemoteInboxAction) -> Unit
) {
    var showNotices by remember { mutableStateOf(false) }
    var navState by remember { mutableStateOf(RemoteInboxNavState()) }

    LaunchedEffect(state.identity?.userId) {
        navState = navState.onGenerationChanged(0L)
    }

    LaunchedEffect(remoteInboxState.detail?.id) {
        val detailId = remoteInboxState.detail?.id
        if (detailId != null && navState.currentRoute !is RemoteInboxRoute.Detail) {
            navState = navState.navigateToDetail(detailId)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

    val isAdmin = state.identity?.username?.contains("admin", ignoreCase = true) == true ||
            state.identity?.email?.contains("admin", ignoreCase = true) == true
    var adminWarningDismissed by remember { mutableStateOf(false) }
        if (isExpanded) {
            // >= 840dp: Two-column desktop layout with persistent scrolling sidebar
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(TabbyRushThemeTokens.customColors.primaryDeep)
                        .padding(TabbyRushThemeTokens.SpacingLarge)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TabbyRush",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.semantics { heading() }
                        )

                        Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                        val userLabel = state.identity?.username ?: state.identity?.email ?: "Offline Account"
                        Text(
                            text = userLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = TabbyRushThemeTokens.customColors.mintSurface
                        )

                        Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXLarge))

                        SyncFeedback(
                            progress = state.syncProgress,
                            onTriggerSync = { onAction(TodoAction.TriggerSync) },
                            onReconcilePending = { onAction(TodoAction.ReconcilePending) }
                        )
                    }

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
                    ) {
                        TextButton(
                            onClick = { navState = navState.navigateToTasks() },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                        ) {
                            Text(
                                text = "My Tasks",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (navState.currentRoute is RemoteInboxRoute.Tasks) MaterialTheme.colorScheme.onPrimary else TabbyRushThemeTokens.customColors.mintSurface
                            )
                        }

                        TextButton(
                            onClick = { navState = navState.navigateToList() },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Announcements",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (navState.currentRoute !is RemoteInboxRoute.Tasks) MaterialTheme.colorScheme.onPrimary else TabbyRushThemeTokens.customColors.mintSurface
                                )
                                if (remoteInboxState.unreadCount > 0) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = TabbyRushThemeTokens.customColors.coral
                                    ) {
                                        Text(
                                            text = "${remoteInboxState.unreadCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = { onAction(TodoAction.OpenSettings) },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                        ) {
                            Text(
                                text = "Reminder Settings",
                                style = MaterialTheme.typography.labelLarge,
                                color = TabbyRushThemeTokens.customColors.mintSurface
                            )
                        }

                        TextButton(
                            onClick = { showNotices = true },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                        ) {
                            Text(
                                text = "Open Source Notices",
                                style = MaterialTheme.typography.labelLarge,
                                color = TabbyRushThemeTokens.customColors.mintSurface
                            )
                        }

                        OutlinedButton(
                            onClick = { onAction(TodoAction.RequestLeave) },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TabbyRushThemeTokens.customColors.coralSurface
                            )
                        ) {
                            Text(
                                text = "Leave Account",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Main workspace
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val route = navState.currentRoute) {
                        is RemoteInboxRoute.Detail -> {
                            val isAvailable = remoteInboxState.availability == RemoteInboxAvailability.Online
                            val detailToShow = if (isAvailable && remoteInboxState.detail?.id == route.messageId) remoteInboxState.detail else null
                            RemoteInboxDetailView(
                                detail = detailToShow,
                                availability = remoteInboxState.availability,
                                loading = remoteInboxState.loading && detailToShow == null,
                                error = remoteInboxState.error,
                                onRemoteAction = onRemoteAction,
                                onBack = { navState = navState.popBack() }
                            )
                        }
                        RemoteInboxRoute.List -> {
                            RemoteInboxView(
                                state = remoteInboxState,
                                unavailableMessage = navState.unavailableMessage,
                                onRemoteAction = onRemoteAction,
                                onOpen = { messageId ->
                                    navState = navState.navigateToDetail(messageId)
                                    onRemoteAction(RemoteInboxAction.Open(messageId))
                                },
                                onBack = { navState = navState.popBack() }
                            )
                        }
                        RemoteInboxRoute.Tasks -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = TabbyRushThemeTokens.SpacingXLarge, vertical = TabbyRushThemeTokens.SpacingLarge),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "My Tasks",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.semantics { heading() }
                                )

                                Button(
                                    onClick = { onAction(TodoAction.OpenCreate()) },
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                                ) {
                                    Text("Add Task", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            if (state.safeError != null) {
                                SafeErrorBanner(
                                    error = state.safeError,
                                    onDismiss = { onAction(TodoAction.DismissError) },
                                    modifier = Modifier.padding(horizontal = TabbyRushThemeTokens.SpacingXLarge)
                                )
                            }
                            if (isAdmin && !adminWarningDismissed) {
                                AdminWarningBanner(
                                    onDismiss = { adminWarningDismissed = true },
                                    modifier = Modifier.padding(horizontal = TabbyRushThemeTokens.SpacingXLarge, vertical = TabbyRushThemeTokens.SpacingSmall)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .widthIn(max = 760.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                TodoList(
                                    items = state.items,
                                    counts = state.counts,
                                    filter = state.filter,
                                    deleteCandidate = state.deleteCandidate,
                                    onAction = onAction,
                                    nowEpochMs = state.nowEpochMs,
                                    hierarchicalItems = state.hierarchicalItems,
                                    deleteCandidateChildCount = state.deleteCandidateChildCount,
                                    isAccountV3 = state.isAccountV3,
                                    unresolvedChildren = state.unresolvedChildren
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // < 840dp: Compact mobile layout with accessible Notices button in top bar
            Scaffold(
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TabbyRushThemeTokens.customColors.cream)
                            .padding(TabbyRushThemeTokens.SpacingMedium)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(end = TabbyRushThemeTokens.SpacingSmall)
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.tabbyrush_logo),
                                    contentDescription = "TabbyRush",
                                    modifier = Modifier
                                        .size(TabbyRushThemeTokens.CompactLogoSize)
                                        .clip(RoundedCornerShape(TabbyRushThemeTokens.CompactLogoCornerRadius))
                                        .semantics { heading() }
                                )
                                val userLabel = state.identity?.username ?: state.identity?.email ?: "Offline"
                                Text(
                                    text = userLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TabbyRushThemeTokens.customColors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = TabbyRushThemeTokens.CompactAccountLabelMaxWidth)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingXSmall),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onAction(TodoAction.OpenCreate()) },
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                                ) {
                                    Text("Add", style = MaterialTheme.typography.labelLarge)
                                }

                                TextButton(
                                    onClick = {
                                        navState = if (navState.currentRoute is RemoteInboxRoute.Tasks) navState.navigateToList() else navState.navigateToTasks()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (navState.currentRoute is RemoteInboxRoute.Tasks) "Inbox" else "Tasks",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TabbyRushThemeTokens.customColors.primaryDeep
                                        )
                                        if (remoteInboxState.unreadCount > 0 && navState.currentRoute is RemoteInboxRoute.Tasks) {
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = TabbyRushThemeTokens.customColors.coral
                                            ) {
                                                Text(
                                                    text = "${remoteInboxState.unreadCount}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onError,
                                                    modifier = Modifier
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        .semantics {
                                                            contentDescription = "${remoteInboxState.unreadCount} unread announcements"
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = { onAction(TodoAction.OpenSettings) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                                ) {
                                    Text(
                                        text = "Settings",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TabbyRushThemeTokens.customColors.primaryDeep
                                    )
                                }

                                TextButton(
                                    onClick = { showNotices = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                                ) {
                                    Text(
                                        text = "Notices",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TabbyRushThemeTokens.customColors.primaryDeep
                                    )
                                }

                                TextButton(
                                    onClick = { onAction(TodoAction.RequestLeave) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                                ) {
                                    Text(
                                        text = "Leave",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                        SyncFeedback(
                            progress = state.syncProgress,
                            onTriggerSync = { onAction(TodoAction.TriggerSync) },
                            onReconcilePending = { onAction(TodoAction.ReconcilePending) }
                        )

                        if (isAdmin && !adminWarningDismissed) {
                            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))
                            AdminWarningBanner(
                                onDismiss = { adminWarningDismissed = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (val route = navState.currentRoute) {
                        is RemoteInboxRoute.Detail -> {
                            val isAvailable = remoteInboxState.availability == RemoteInboxAvailability.Online
                            val detailToShow = if (isAvailable && remoteInboxState.detail?.id == route.messageId) remoteInboxState.detail else null
                            RemoteInboxDetailView(
                                detail = detailToShow,
                                availability = remoteInboxState.availability,
                                loading = remoteInboxState.loading && detailToShow == null,
                                error = remoteInboxState.error,
                                onRemoteAction = onRemoteAction,
                                onBack = { navState = navState.popBack() }
                            )
                        }
                        RemoteInboxRoute.List -> {
                            RemoteInboxView(
                                state = remoteInboxState,
                                unavailableMessage = navState.unavailableMessage,
                                onRemoteAction = onRemoteAction,
                                onOpen = { messageId ->
                                    navState = navState.navigateToDetail(messageId)
                                    onRemoteAction(RemoteInboxAction.Open(messageId))
                                },
                                onBack = { navState = navState.popBack() }
                            )
                        }
                        RemoteInboxRoute.Tasks -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (state.safeError != null) {
                                    SafeErrorBanner(
                                        error = state.safeError,
                                        onDismiss = { onAction(TodoAction.DismissError) },
                                        modifier = Modifier.padding(TabbyRushThemeTokens.SpacingMedium)
                                    )
                                }

                                TodoList(
                                    items = state.items,
                                    counts = state.counts,
                                    filter = state.filter,
                                    deleteCandidate = state.deleteCandidate,
                                    onAction = onAction,
                                    nowEpochMs = state.nowEpochMs,
                                    hierarchicalItems = state.hierarchicalItems,
                                    deleteCandidateChildCount = state.deleteCandidateChildCount,
                                    isAccountV3 = state.isAccountV3,
                                    unresolvedChildren = state.unresolvedChildren
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotices) {
        Dialog(onDismissRequest = { showNotices = false }) {
            LicenseNotices(onDismiss = { showNotices = false })
        }
    }
}

@Composable
private fun SafeErrorBanner(
    error: SafeError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isReset = error is SafeError.ResetRequired
    val isProtocol = error is SafeError.ProtocolIncompatible
    val title = when {
        isReset -> "Epoch Reset Required"
        isProtocol -> "Protocol Update Required"
        else -> "Attention"
    }
    val guidance = when {
        isReset -> " The server sync epoch rotated. Local drafts and operations are preserved but require manual review before re-syncing."
        isProtocol -> " The client schema or wire version is incompatible with the server. Please update the application to resume sync."
        else -> ""
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.small),
        color = TabbyRushThemeTokens.customColors.coralSurface,
        shape = MaterialTheme.shapes.small
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "${error.message}$guidance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
            ) {
                Text(
                    text = "Dismiss",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
@Composable
private fun AdminWarningBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TabbyRushThemeTokens.customColors.primaryDeep, MaterialTheme.shapes.small),
        color = TabbyRushThemeTokens.customColors.cream,
        shape = MaterialTheme.shapes.small
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
                    text = "Admin Account Warning",
                    style = MaterialTheme.typography.titleMedium,
                    color = TabbyRushThemeTokens.customColors.primaryDeep
                )
                Text(
                    text = "You are logged in with an administrator account. For daily task management, a dedicated non-admin account is recommended.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
            ) {
                Text(
                    text = "Dismiss",
                    color = TabbyRushThemeTokens.customColors.primaryDeep,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
