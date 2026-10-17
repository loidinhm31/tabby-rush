package dev.tabbyrush.shared.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.contract.AccessRequestInfo
import dev.tabbyrush.shared.contract.SafeError
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens

@Composable
fun AccessRequestPanel(
    accessRequest: AccessRequestInfo?,
    safeError: SafeError?,
    onRequestAccess: (message: String?) -> Unit,
    onRefreshStatus: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var message by remember { mutableStateOf("") }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val status = accessRequest?.status?.lowercase() ?: "none"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(TabbyRushThemeTokens.SpacingLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(TabbyRushThemeTokens.SpacingLarge)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (status) {
                "approved" -> {
                    Text(
                        text = "Access Approved!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                    Text(
                        text = "Your access request has been approved by an administrator. Refresh your session to start synchronizing tasks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

                    Button(
                        onClick = onRefreshStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Refresh Session", style = MaterialTheme.typography.labelLarge)
                    }
                }

                "pending" -> {
                    Text(
                        text = "Access Request Pending",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                    Text(
                        text = "Your access request for TabbyRush is awaiting administrator approval. You will be able to synchronize tasks once approved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TabbyRushThemeTokens.customColors.muted
                    )

                    if (accessRequest?.message != null) {
                        Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = TabbyRushThemeTokens.customColors.cream,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(TabbyRushThemeTokens.SpacingMedium)
                        ) {
                            Text(
                                text = "Submitted Note:",
                                style = MaterialTheme.typography.labelMedium,
                                color = TabbyRushThemeTokens.customColors.primaryDeep
                            )
                            Text(
                                text = accessRequest.message ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXLarge))

                    Button(
                        onClick = onRefreshStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Refresh Status", style = MaterialTheme.typography.labelLarge)
                    }
                }

                "rejected" -> {
                    Text(
                        text = "Access Request Rejected",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                    Text(
                        text = "An administrator rejected your request to access the TabbyRush application. You may submit a new message explaining your access needs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Reason for re-request (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        shape = MaterialTheme.shapes.small
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

                    Button(
                        onClick = {
                            val msg = message.trim().ifEmpty { null }
                            onRequestAccess(msg)
                            message = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Submit New Request", style = MaterialTheme.typography.labelLarge)
                    }
                }

                "none" -> {
                    // No request exists yet
                    Text(
                        text = "Request TabbyRush Access",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                    Text(
                        text = "To access cloud synchronization, your account requires permission from a Hub administrator.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TabbyRushThemeTokens.customColors.muted
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Access note / department (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        shape = MaterialTheme.shapes.small
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

                    Button(
                        onClick = {
                            val msg = message.trim().ifEmpty { null }
                            onRequestAccess(msg)
                            message = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Send Access Request", style = MaterialTheme.typography.labelLarge)
                    }
                }

                else -> {
                    // Status is explicitly unknown
                    Text(
                        text = "Access Status Unknown",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                    Text(
                        text = "The access status for your account could not be verified ($status). Please refresh your status or contact an administrator.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

                    Button(
                        onClick = onRefreshStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Refresh Status", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (safeError != null) {
                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))
                Text(
                    text = safeError.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingMedium))

            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.labelLarge,
                    color = TabbyRushThemeTokens.customColors.muted
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Sign Out?") },
            text = { Text("Are you sure you want to sign out from this account on this device?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirm = false },
                    modifier = Modifier.defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)
                ) {
                    Text("Cancel", color = TabbyRushThemeTokens.customColors.muted)
                }
            }
        )
    }
}
