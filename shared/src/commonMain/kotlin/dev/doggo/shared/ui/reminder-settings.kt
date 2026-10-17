package dev.doggo.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.doggo.shared.contract.ReminderSettingsState
import dev.doggo.shared.contract.TodoAction
import dev.doggo.shared.theme.DoggoThemeTokens

@Composable
fun ReminderSettingsDialog(
    settings: ReminderSettingsState,
    onAction: (TodoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var defaultReminderEnabled by remember { mutableStateOf(settings.defaultReminderEnabled) }
    var defaultLeadMinutes by remember { mutableStateOf(settings.defaultReminderLeadMinutes) }
    var defaultIntervalMinutes by remember { mutableStateOf(settings.defaultReminderIntervalMinutes ?: 15L) }
    var defaultCount by remember { mutableStateOf(settings.defaultReminderCount) }
    var defaultAnchorTime by remember { mutableStateOf(settings.defaultDateOnlyAnchorTime) }
    var showAnchorTimePicker by remember { mutableStateOf(false) }

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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingMedium)
        ) {
            Text(
                text = "Reminder Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )

            Text(
                text = "Preferences are strictly device- and account-local. Defaults apply to new tasks only and never modify existing task rules or cloud data.",
                style = MaterialTheme.typography.bodySmall,
                color = DoggoThemeTokens.customColors.muted
            )

            // Platform capability card
            val capabilities = settings.capabilities
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = DoggoThemeTokens.customColors.mintSurface.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier.padding(DoggoThemeTokens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingXSmall)
                ) {
                    Text(
                        text = "Device & Platform Status (${settings.platformName.ifEmpty { "System" }})",
                        style = MaterialTheme.typography.labelMedium,
                        color = DoggoThemeTokens.customColors.primaryDeep
                    )

                    if (capabilities != null) {
                        Text(
                            text = "Notifications permission: ${if (capabilities.canPostNotifications) "Granted" else "Denied / Restricted"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Exact alarms: ${if (capabilities.canScheduleExactAlarms) "Allowed" else "Best-effort / Restricted"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val platformNote = if (settings.platformName.contains("Android", ignoreCase = true)) {
                        "Android alarms operate while the device is sleeping, but background restrictions or force-stop can delay alerts."
                    } else {
                        "Desktop reminders operate while the Doggo process is running (visible or minimized). Closing Doggo stops alerts."
                    }
                    Text(
                        text = platformNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = DoggoThemeTokens.customColors.muted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingSmall)
                    ) {
                        OutlinedButton(
                            onClick = { onAction(TodoAction.OpenNotificationSettings) },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                        ) {
                            Text("OS Settings", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onAction(TodoAction.RequestExactAlarmPermission) },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                        ) {
                            Text("Exact Alarms", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Local notifications toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = DoggoThemeTokens.customColors.muted
                    ),
                    modifier = Modifier
                        .size(DoggoThemeTokens.MinTouchTargetSize)
                        .semantics {
                            stateDescription = if (notificationsEnabled) "Notifications enabled" else "Notifications disabled"
                            contentDescription = "Allow notifications on this device"
                        }
                )
                Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))
                Column {
                    Text(
                        text = "Allow notifications on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Disabling stops local delivery without altering task rules.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DoggoThemeTokens.customColors.muted
                    )
                }
            }

            // Default new-task reminder toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = defaultReminderEnabled,
                    onCheckedChange = { defaultReminderEnabled = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = DoggoThemeTokens.customColors.muted
                    ),
                    modifier = Modifier
                        .size(DoggoThemeTokens.MinTouchTargetSize)
                        .semantics {
                            stateDescription = if (defaultReminderEnabled) "Default reminder enabled" else "Default reminder disabled"
                            contentDescription = "Default reminder rule for new tasks"
                        }
                )
                Spacer(modifier = Modifier.width(DoggoThemeTokens.SpacingSmall))
                Text(
                    text = "Default reminder for new tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (defaultReminderEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingSmall)
                ) {
                    OutlinedTextField(
                        value = defaultLeadMinutes.toString(),
                        onValueChange = { defaultLeadMinutes = maxOf(0L, it.toLongOrNull() ?: 0L) },
                        label = { Text("Lead (min)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    )

                    OutlinedTextField(
                        value = defaultCount.toString(),
                        onValueChange = { defaultCount = (it.toLongOrNull() ?: 1L).coerceIn(1L, 10L) },
                        label = { Text("Count (1–10)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    )
                }

                if (defaultCount > 1L) {
                    OutlinedTextField(
                        value = defaultIntervalMinutes.toString(),
                        onValueChange = { defaultIntervalMinutes = maxOf(15L, it.toLongOrNull() ?: 15L) },
                        label = { Text("Interval (min, ≥15)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)
                    )
                }
            }

            // Date-only default anchor time
            DateTimePickerButton(
                label = "Default date-only anchor time",
                value = defaultAnchorTime,
                onClick = { showAnchorTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
                supportingText = "Used for date-only task reminders (initial default 09:00)."
            )

            // Dialog action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DoggoThemeTokens.SpacingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onAction(TodoAction.CloseSettings) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Cancel", color = DoggoThemeTokens.customColors.muted)
                }

                Button(
                    onClick = {
                        val updated = settings.copy(
                            notificationsEnabled = notificationsEnabled,
                            defaultReminderEnabled = defaultReminderEnabled,
                            defaultReminderLeadMinutes = defaultLeadMinutes,
                            defaultReminderIntervalMinutes = if (defaultCount > 1L) defaultIntervalMinutes else null,
                            defaultReminderCount = defaultCount,
                            defaultDateOnlyAnchorTime = defaultAnchorTime.trim().ifEmpty { "09:00" }
                        )
                        onAction(TodoAction.SaveSettings(updated))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Save Settings")
                }
            }
        }
    }

    if (showAnchorTimePicker) {
        DoggoTimePickerDialog(
            initialTime = defaultAnchorTime,
            title = "Default date-only anchor time",
            fallbackTime = "09:00",
            onConfirm = { selected ->
                defaultAnchorTime = selected
                showAnchorTimePicker = false
            },
            onDismiss = { showAnchorTimePicker = false }
        )
    }
}
