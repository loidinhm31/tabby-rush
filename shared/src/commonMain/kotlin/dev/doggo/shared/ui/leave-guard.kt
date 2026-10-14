package dev.doggo.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.doggo.shared.contract.LeaveGuardState
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.theme.DoggoThemeTokens

@Composable
fun LeaveGuard(
    leaveGuard: LeaveGuardState,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(DoggoThemeTokens.SpacingLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(DoggoThemeTokens.SpacingLarge)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Leave Account?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingSmall))

            Text(
                text = "Leaving this account will remove local cached data and credentials from this device. Remote data on the Hub will remain intact. Any in-flight requests may still commit remotely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val hasUnsynced = leaveGuard.pendingCount > 0 ||
                    leaveGuard.uncertainCount > 0 ||
                    leaveGuard.conflictCount > 0

            if (hasUnsynced) {
                Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingMedium))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = DoggoThemeTokens.customColors.coralSurface,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(DoggoThemeTokens.SpacingMedium)
                ) {
                    Text(
                        text = "Unsynchronized local changes will be lost from this device:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingXSmall))

                    if (leaveGuard.pendingCount > 0) {
                        Text(
                            text = "• ${leaveGuard.pendingCount} pending local operations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (leaveGuard.uncertainCount > 0) {
                        Text(
                            text = "• ${leaveGuard.uncertainCount} uncertain operations awaiting server confirmation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (leaveGuard.conflictCount > 0) {
                        Text(
                            text = "• ${leaveGuard.conflictCount} unresolved conflicts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingLarge))
            HorizontalDivider(color = DoggoThemeTokens.customColors.line)
            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingLarge))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingSmall)
            ) {
                OutlinedButton(
                    onClick = { onAction(TodoAction.CancelLeave) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Cancel & Stay",
                        style = MaterialTheme.typography.labelLarge,
                        color = DoggoThemeTokens.customColors.primaryDeep
                    )
                }

                Button(
                    onClick = { onAction(TodoAction.ConfirmLeave) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Leave & Wipe Local Data",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
