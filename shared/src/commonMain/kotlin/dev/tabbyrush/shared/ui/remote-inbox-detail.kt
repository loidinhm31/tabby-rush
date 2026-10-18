package dev.tabbyrush.shared.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxItem
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens
import kotlinx.datetime.Instant

/** Formats an RFC3339 timestamp to a readable UTC date-time representation. */
fun formatIsoTimestamp(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        val str = instant.toString()
        "${str.substringBefore('T')} ${str.substringAfter('T').take(5)} UTC"
    } catch (_: Exception) {
        iso
    }
}

/** Determines whether detail body content is authorized and safe to display. */
fun isDetailContentVisible(availability: RemoteInboxAvailability, detail: RemoteInboxItem?): Boolean {
    return availability == RemoteInboxAvailability.Online && detail != null
}

/**
 * Accessible detail view for a single announcement with plain-text rendering and canonical read acknowledgement.
 */
@Composable
fun RemoteInboxDetailView(
    detail: RemoteInboxItem?,
    availability: RemoteInboxAvailability,
    loading: Boolean,
    error: String?,
    onRemoteAction: (RemoteInboxAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(TabbyRushThemeTokens.SpacingLarge)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = TabbyRushThemeTokens.SpacingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onRemoteAction(RemoteInboxAction.Back); onBack() },
                modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "← Back to Inbox",
                    style = MaterialTheme.typography.labelLarge,
                    color = TabbyRushThemeTokens.customColors.primaryDeep
                )
            }

            if (detail != null && availability == RemoteInboxAvailability.Online) {
                if (!detail.readAt.isNullOrBlank()) {
                    Text(
                        text = "✓ Read ${formatIsoTimestamp(detail.readAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TabbyRushThemeTokens.customColors.muted
                    )
                } else {
                    Button(
                        onClick = { onRemoteAction(RemoteInboxAction.MarkRead(detail.id)) },
                        modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(text = "Mark as read", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (error != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = TabbyRushThemeTokens.SpacingSmall).border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.small),
                color = TabbyRushThemeTokens.customColors.coralSurface,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(TabbyRushThemeTokens.SpacingMedium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRemoteAction(RemoteInboxAction.DismissError) }, modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (loading && detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (availability == RemoteInboxAvailability.Offline) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Offline: Announcement details are unavailable while offline. Connect to network to view details.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TabbyRushThemeTokens.customColors.muted
                )
            }
        } else if (availability == RemoteInboxAvailability.Revoked) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Access revoked: Application access has been revoked.", style = MaterialTheme.typography.bodyLarge, color = TabbyRushThemeTokens.customColors.muted)
            }
        } else if (availability == RemoteInboxAvailability.SignedOut) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Signed out: Sign in to view announcement details.", style = MaterialTheme.typography.bodyLarge, color = TabbyRushThemeTokens.customColors.muted)
            }
        } else if (detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Announcement details unavailable.", style = MaterialTheme.typography.bodyLarge, color = TabbyRushThemeTokens.customColors.muted)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingMedium)
            ) {
                Text(text = detail.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                Text(text = "Published: ${formatIsoTimestamp(detail.createdAt)}", style = MaterialTheme.typography.labelMedium, color = TabbyRushThemeTokens.customColors.muted)
                HorizontalDivider(color = TabbyRushThemeTokens.customColors.line)
                Text(text = detail.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.weight(1f, fill = false))

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = TabbyRushThemeTokens.SpacingXLarge).border(1.dp, TabbyRushThemeTokens.customColors.line, MaterialTheme.shapes.small),
                    color = TabbyRushThemeTokens.customColors.mintSurface.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(TabbyRushThemeTokens.SpacingMedium), verticalArrangement = Arrangement.spacedBy(TabbyRushThemeTokens.SpacingXSmall)) {
                        Text(text = "Retention Notice", style = MaterialTheme.typography.labelMedium, color = TabbyRushThemeTokens.customColors.primaryDeep)
                        Text(
                            text = "Announcements are retained on server for 90 days from publication (expires ${formatIsoTimestamp(detail.expiresAt)}). Read status is canonical.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TabbyRushThemeTokens.customColors.muted
                        )
                    }
                }
            }
        }
    }
}
