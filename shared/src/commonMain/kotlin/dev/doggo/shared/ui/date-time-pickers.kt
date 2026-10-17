package dev.doggo.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.doggo.shared.theme.DoggoThemeTokens
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun DateTimePickerButton(
    label: String, value: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, supportingText: String? = null
) {
    val displayText = value.ifBlank { "Choose $label" }
    Column(modifier = modifier) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize).semantics {
                role = Role.Button
                stateDescription = if (value.isNotBlank()) value else "Not set"
            },
            shape = RoundedCornerShape(DoggoThemeTokens.ControlCornerRadius),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = DoggoThemeTokens.SpacingXSmall), horizontalAlignment = Alignment.Start) {
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = DoggoThemeTokens.customColors.muted)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayText, style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isNotBlank()) MaterialTheme.colorScheme.onSurface else DoggoThemeTokens.customColors.muted
                )
            }
        }
        if (!supportingText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(DoggoThemeTokens.SpacingXSmall))
            Text(
                text = supportingText, style = MaterialTheme.typography.labelSmall,
                color = DoggoThemeTokens.customColors.muted, modifier = Modifier.padding(horizontal = DoggoThemeTokens.SpacingXSmall)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DoggoDatePickerDialog(
    initialDate: String, title: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    val parsedDate = remember(initialDate) { DateTimePickerFormats.parseDateOrNull(initialDate) }
    val initialMillis = remember(parsedDate) { parsedDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds() }
    val yearRange = 0..9999
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis, yearRange = yearRange)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { datePickerState.selectedDateMillis?.let { onConfirm(DateTimePickerFormats.utcMillisToDate(it)) } },
                enabled = datePickerState.selectedDateMillis != null
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(
            state = datePickerState, showModeToggle = false,
            title = {
                Text(
                    text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = DoggoThemeTokens.SpacingXLarge, top = DoggoThemeTokens.SpacingLarge)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DoggoTimePickerDialog(
    initialTime: String, title: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit,
    fallbackTime: String = "09:00"
) {
    val parsed = remember(initialTime) { DateTimePickerFormats.parseTimeOrNull(initialTime) }
    val fallback = remember(fallbackTime) { DateTimePickerFormats.parseTimeOrNull(fallbackTime) ?: LocalTime(9, 0) }
    val timePickerState = rememberTimePickerState(
        initialHour = parsed?.hour ?: fallback.hour,
        initialMinute = parsed?.minute ?: fallback.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(DateTimePickerFormats.formatTime(timePickerState.hour, timePickerState.minute)) }
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                TimePicker(state = timePickerState)
            }
        }
    )
}

internal object DateTimePickerFormats {
    private val DATE_FORMAT_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val TIME_FORMAT_REGEX = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

    fun parseDateOrNull(value: String): LocalDate? {
        val trimmed = value.trim()
        if (!DATE_FORMAT_REGEX.matches(trimmed)) return null
        return try {
            LocalDate.parse(trimmed)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun dateToUtcMillisOrNull(value: String): Long? =
        parseDateOrNull(value)?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()

    fun utcMillisToDate(value: Long): String =
        Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.UTC).date.toString()

    fun parseTimeOrNull(value: String): LocalTime? {
        val trimmed = value.trim()
        if (!TIME_FORMAT_REGEX.matches(trimmed)) return null
        return try {
            LocalTime.parse(trimmed)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun formatTime(hour: Int, minute: Int): String {
        require(hour in 0..23) { "Hour must be 0..23, got $hour" }
        require(minute in 0..59) { "Minute must be 0..59, got $minute" }
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }
}
