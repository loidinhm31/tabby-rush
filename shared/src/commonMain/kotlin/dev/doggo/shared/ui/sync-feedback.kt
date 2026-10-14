package dev.doggo.shared.ui

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
import dev.doggo.shared.contract.SyncProgress
import dev.doggo.shared.contract.SyncStatus
import dev.doggo.shared.theme.DoggoThemeTokens
import kotlinx.datetime.Instant

@Composable
fun SyncFeedback(
    progress: SyncProgress,
    onTriggerSync: () -> Unit,
    onReconcilePending: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor, containerColor) = when (progress.status) {
        SyncStatus.IDLE -> {
            if (progress.pendingCount > 0) {
                Triple(
                    "${progress.pendingCount} pending local changes",
                    DoggoThemeTokens.customColors.primaryDeep,
                    DoggoThemeTokens.customColors.mintSurface
                )
            } else {
                Triple(
                    "Saved locally",
                    DoggoThemeTokens.customColors.muted,
                    MaterialTheme.colorScheme.surface
                )
            }
        }
        SyncStatus.SYNCING -> Triple(
            "Syncing with Hub...",
            MaterialTheme.colorScheme.primary,
            DoggoThemeTokens.customColors.mintSurface
        )
        SyncStatus.PAUSED_CONFLICT -> Triple(
            "${progress.conflictCount} conflict needs review",
            MaterialTheme.colorScheme.error,
            DoggoThemeTokens.customColors.coralSurface
        )
        SyncStatus.PAUSED_PROTOCOL -> Triple(
            "Protocol update required",
            MaterialTheme.colorScheme.error,
            DoggoThemeTokens.customColors.coralSurface
        )
        SyncStatus.RESET_REQUIRED -> Triple(
            "Epoch reset required",
            MaterialTheme.colorScheme.error,
            DoggoThemeTokens.customColors.coralSurface
        )
        SyncStatus.UNCERTAIN -> Triple(
            "${progress.uncertainCount} uncertain operations",
            MaterialTheme.colorScheme.error,
            DoggoThemeTokens.customColors.coralSurface
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, shape = MaterialTheme.shapes.small)
            .border(1.dp, DoggoThemeTokens.customColors.line, shape = MaterialTheme.shapes.small)
            .padding(horizontal = DoggoThemeTokens.SpacingMedium, vertical = DoggoThemeTokens.SpacingSmall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
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
                    Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))
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
                            color = DoggoThemeTokens.customColors.muted
                        )
                    }
                }
            }

            if (progress.status == SyncStatus.UNCERTAIN || progress.pendingCount > 0) {
                TextButton(
                    onClick = onReconcilePending,
                    modifier = Modifier.defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                ) {
                    Text(
                        text = "Reconcile",
                        style = MaterialTheme.typography.labelMedium,
                        color = DoggoThemeTokens.customColors.primaryDeep
                    )
                }
            } else if (progress.status != SyncStatus.SYNCING) {
                TextButton(
                    onClick = onTriggerSync,
                    modifier = Modifier.defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
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
