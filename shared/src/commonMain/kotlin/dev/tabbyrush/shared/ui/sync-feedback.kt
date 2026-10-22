package dev.tabbyrush.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.contract.SyncProgress
import dev.tabbyrush.shared.contract.SyncStatus
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens
import kotlinx.datetime.Instant

@Composable
fun SyncFeedback(
    progress: SyncProgress,
    onTriggerSync: () -> Unit,
    onReconcilePending: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUpgradeRequired = progress.safeError is SafeError.CollaborationRequired
    val (statusText, statusColor, containerColor) = when {
        isUpgradeRequired -> Triple(
            "Upgrade required for collaboration",
            MaterialTheme.colorScheme.error,
            TabbyRushThemeTokens.customColors.coralSurface
        )
        progress.status == SyncStatus.IDLE -> {
            if (progress.pendingCount > 0) {
                Triple(
                    "${progress.pendingCount} pending local changes",
                    TabbyRushThemeTokens.customColors.primaryDeep,
                    TabbyRushThemeTokens.customColors.mintSurface
                )
            } else {
                Triple(
                    "Saved locally",
                    TabbyRushThemeTokens.customColors.muted,
                    MaterialTheme.colorScheme.surface
                )
            }
        }
        progress.status == SyncStatus.SYNCING -> Triple(
            "Syncing with Hub...",
            MaterialTheme.colorScheme.primary,
            TabbyRushThemeTokens.customColors.mintSurface
        )
        progress.status == SyncStatus.PAUSED_CONFLICT -> Triple(
            "${progress.conflictCount} conflict needs review",
            MaterialTheme.colorScheme.error,
            TabbyRushThemeTokens.customColors.coralSurface
        )
        progress.status == SyncStatus.PAUSED_PROTOCOL -> Triple(
            "Sync unavailable; local work retained",
            MaterialTheme.colorScheme.error,
            TabbyRushThemeTokens.customColors.coralSurface
        )
        progress.status == SyncStatus.UNCERTAIN -> Triple(
            "${progress.uncertainCount} uncertain operations",
            MaterialTheme.colorScheme.error,
            TabbyRushThemeTokens.customColors.coralSurface
        )
        else -> Triple(
            "Saved locally",
            TabbyRushThemeTokens.customColors.muted,
            MaterialTheme.colorScheme.surface
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, shape = MaterialTheme.shapes.small)
            .border(1.dp, TabbyRushThemeTokens.customColors.line, shape = MaterialTheme.shapes.small)
            .padding(horizontal = TabbyRushThemeTokens.SpacingMedium, vertical = TabbyRushThemeTokens.SpacingSmall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (progress.status == SyncStatus.SYNCING) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .semantics {
                                contentDescription = "Sync in progress"
                            },
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(TabbyRushThemeTokens.SpacingSmall))
                }

                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                    )

                    if (progress.lastSyncTime != null && progress.lastSyncTime > 0) {
                        val formattedTime = formatTimestamp(progress.lastSyncTime)
                        Text(
                            text = "Last reconciled: $formattedTime UTC",
                            style = MaterialTheme.typography.labelSmall,
                            color = TabbyRushThemeTokens.customColors.muted
                        )
                    }
                }
            }

            if (isUpgradeRequired) {
                Text(
                    text = "App Update Required",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = TabbyRushThemeTokens.SpacingSmall)
                )
            } else if (progress.status == SyncStatus.UNCERTAIN || progress.pendingCount > 0) {
                TextButton(
                    onClick = onReconcilePending,
                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                ) {
                    Text(
                        text = "Reconcile",
                        style = MaterialTheme.typography.labelMedium,
                        color = TabbyRushThemeTokens.customColors.primaryDeep
                    )
                }
            } else if (progress.status != SyncStatus.SYNCING && progress.status != SyncStatus.PAUSED_PROTOCOL) {
                TextButton(
                    onClick = onTriggerSync,
                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                ) {
                    Text(
                        text = "Sync Now",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

fun formatTimestamp(epochMs: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(epochMs)
        val iso = instant.toString()
        // Format YYYY-MM-DD HH:MM
        val datePart = iso.substringBefore('T')
        val timePart = iso.substringAfter('T').substringBefore('.').substringBefore('Z').take(5)
        "$datePart $timePart"
    } catch (_: Exception) {
        "$epochMs"
    }
}
