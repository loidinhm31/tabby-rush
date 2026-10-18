package dev.tabbyrush.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxItem
import dev.tabbyrush.shared.contract.RemoteInboxState
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens

@Composable
fun RemoteInboxView(
    state: RemoteInboxState,
    unavailableMessage: String?,
    onRemoteAction: (RemoteInboxAction) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(TabbyRushThemeTokens.SpacingLarge)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = TabbyRushThemeTokens.SpacingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize), shape = MaterialTheme.shapes.small) {
                    Text("← Tasks", style = MaterialTheme.typography.labelMedium, color = TabbyRushThemeTokens.customColors.primaryDeep)
                }
                Text("Announcements", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                if (state.unreadCount > 0) {
                    Surface(shape = MaterialTheme.shapes.small, color = TabbyRushThemeTokens.customColors.coral) {
                        Text(text = "${state.unreadCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Button(
                onClick = { onRemoteAction(RemoteInboxAction.Refresh) },
                enabled = !state.loading,
                modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Refresh", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (unavailableMessage != null) BannerBox(text = unavailableMessage, isError = true)
        if (state.error != null) BannerBox(text = state.error, isError = true, onDismiss = { onRemoteAction(RemoteInboxAction.DismissError) })
        when (state.availability) {
            RemoteInboxAvailability.Offline -> BannerBox(text = "Offline: Announcements may be stale. Connect to network to view details.", isError = false)
            RemoteInboxAvailability.Revoked -> BannerBox(text = "Access revoked: Application access has been revoked.", isError = true)
            RemoteInboxAvailability.SignedOut -> BannerBox(text = "Signed out: Sign in to view announcements.", isError = false)
            else -> {}
        }
        if (!state.permission) BannerBox(text = "System notifications are disabled or unsupported on this device. In-app announcements remain available here.", isError = false)

        Text("Retention: Announcements retained on server for 90 days from publication.", style = MaterialTheme.typography.labelSmall, color = TabbyRushThemeTokens.customColors.muted, modifier = Modifier.padding(vertical = TabbyRushThemeTokens.SpacingXSmall))
        HorizontalDivider(color = TabbyRushThemeTokens.customColors.line)

        if (state.availability == RemoteInboxAvailability.Revoked || state.availability == RemoteInboxAvailability.SignedOut) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.availability == RemoteInboxAvailability.Revoked) "Access revoked." else "Please sign in.", style = MaterialTheme.typography.bodyLarge, color = TabbyRushThemeTokens.customColors.muted)
            }
        } else if (state.loading && state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No announcements yet.", style = MaterialTheme.typography.bodyLarge, color = TabbyRushThemeTokens.customColors.muted) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = TabbyRushThemeTokens.SpacingSmall),
                verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingSmall)
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    RemoteInboxItemRow(item = item, isOffline = state.availability == RemoteInboxAvailability.Offline, onOpen = { onOpen(item.id) })
                }
                if (state.nextCursor != null) {
                    item(key = "load_more") {
                        Box(modifier = Modifier.fillMaxWidth().padding(TabbyRushThemeTokens.SpacingMedium), contentAlignment = Alignment.Center) {
                            if (state.loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            else OutlinedButton(onClick = { onRemoteAction(RemoteInboxAction.LoadNext) }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize), shape = MaterialTheme.shapes.small) {
                                Text("Load older announcements", style = MaterialTheme.typography.labelLarge, color = TabbyRushThemeTokens.customColors.primaryDeep)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteInboxItemRow(item: RemoteInboxItem, isOffline: Boolean, onOpen: () -> Unit) {
    val isRead = !item.readAt.isNullOrBlank()
    Surface(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize).clickable(onClick = onOpen).border(1.dp, if (isRead) TabbyRushThemeTokens.customColors.line else MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        shape = MaterialTheme.shapes.small,
        color = if (isRead) MaterialTheme.colorScheme.surface else TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(TabbyRushThemeTokens.SpacingMedium)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(text = if (isRead) "Read" else "● New", style = MaterialTheme.typography.labelSmall, color = if (isRead) TabbyRushThemeTokens.customColors.muted else MaterialTheme.colorScheme.primary)
            }
            if (!isOffline) Text(text = item.body, style = MaterialTheme.typography.bodyMedium, color = TabbyRushThemeTokens.customColors.muted, maxLines = 2, modifier = Modifier.padding(top = TabbyRushThemeTokens.SpacingXSmall))
            Text(text = formatIsoTimestamp(item.createdAt), style = MaterialTheme.typography.labelSmall, color = TabbyRushThemeTokens.customColors.muted, modifier = Modifier.padding(top = TabbyRushThemeTokens.SpacingXSmall))
        }
    }
}

@Composable
private fun BannerBox(text: String, isError: Boolean, onDismiss: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = TabbyRushThemeTokens.SpacingXSmall).border(1.dp, if (isError) MaterialTheme.colorScheme.error else TabbyRushThemeTokens.customColors.line, MaterialTheme.shapes.small),
        color = if (isError) TabbyRushThemeTokens.customColors.coralSurface else TabbyRushThemeTokens.customColors.cream,
        shape = MaterialTheme.shapes.small
    ) {
        Row(modifier = Modifier.padding(TabbyRushThemeTokens.SpacingSmall), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = if (isError) MaterialTheme.colorScheme.error else TabbyRushThemeTokens.customColors.primaryDeep, modifier = Modifier.weight(1f))
            if (onDismiss != null) {
                TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
