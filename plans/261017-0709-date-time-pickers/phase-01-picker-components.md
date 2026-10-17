# Phase 01 — Shared picker components and format utilities

## Overview
- Priority P2; status completed administratively on 2026-10-17 per request; effort 4h. Source sets DatePicker year range to `0..9999`; no supplied Gradle run validates this latest source change, and rendered boundary-year selection belongs to Phase 04.
- Use existing commonMain Material3 and kotlinx-datetime dependencies; no platform API imports or version upgrade.
- Read [master contract](plan.md), [Compose standards](../../docs/code-standards.md#3-kotlin-multiplatform--compose-guidelines), and current deadline validation before editing.

## Exclusive ownership
Create only:
- `shared/src/commonMain/kotlin/dev/doggo/shared/ui/date-time-pickers.kt`
- `shared/src/commonTest/kotlin/dev/doggo/shared/ui/date-time-pickers-test.kt`
Read-only: `deadline-editor-section.kt`, `reminder-settings.kt`, `deadline-calculator-helpers.kt`, `todo-ui-state.kt`, build files, theme tokens.
No deletions. Phase 04 owns rendered-test wiring and semantics tests; do not modify those files.

## Key insights and design
- `DatePicker`, `DatePickerDialog`, `rememberDatePickerState`, `TimePicker`, and `rememberTimePickerState` belong to `androidx.compose.material3`; scope `ExperimentalMaterial3Api` opt-in here.
- Do not assume a Material3 `TimePickerDialog` exists at this pinned version. Use `AlertDialog` with `TimePicker` content, explicit Confirm/Cancel, and `onDismissRequest`.
- Use one shared button-style picker trigger rather than clickable editable text fields. Inline labels follow existing UI convention; no new icon dependency needed.
- Component state is tentative; only Confirm invokes value callback. Callers own domain updates and close visibility. Components never dispatch `TodoAction` or persist anything.

## Frozen component contract
```kotlin
@Composable internal fun DateTimePickerButton(
    label: String, value: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, supportingText: String? = null
)
@Composable internal fun DoggoDatePickerDialog(
    initialDate: String, title: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
)
@Composable internal fun DoggoTimePickerDialog(
    initialTime: String, title: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit,
    fallbackTime: String = "09:00"
)
```
- `DateTimePickerButton`: `OutlinedButton`, label and visible value (or “Choose date”/“Choose time” supplied as value); >=48dp minimum height, full click target, button role, no `EditableText`/`SetText`. Supporting text sits outside click semantics as appropriate.
- Trigger descriptions are caller-provided labels: `Deadline date`, `Deadline time`, `Date-only reminder anchor time`, `Default date-only anchor time`; expose current value through visible text/state description without duplicate spoken labels.
- Dialog headings identify field context; actions read `Confirm` and `Cancel`. Callers close on either callback; Confirm emits once, never additionally calls `onDismiss`.
- Date dialog uses `DatePicker` in calendar mode with `showModeToggle = false`; confirmation disabled when selection is null.
- Time dialog uses `rememberTimePickerState(is24Hour = true)` and renders `TimePicker(state = state)`; no `TimeInput`, keyboard-mode toggle, or seconds.
- Recreate picker state on each opening by conditionally composing dialog; cancellation must not leak tentative selection into reopening.

## Pure format utilities
Keep `internal object DateTimePickerFormats` in the component file; use these exact consumer-facing helpers:
```kotlin
fun dateToUtcMillisOrNull(value: String): Long?
fun utcMillisToDate(value: Long): String
fun parseTimeOrNull(value: String): kotlinx.datetime.LocalTime?
fun formatTime(hour: Int, minute: Int): String
```
- Date parsing accepts trimmed canonical four-digit `YYYY-MM-DD`, validates real calendar dates via `LocalDate`, returns null for empty/malformed/impossible values.
- Convert date to UTC day start; reverse via `Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.UTC).date`. Never use device/deadline timezone for selected milliseconds.
- DatePicker year range must match existing four-digit years (`0..9999`), not its default 1900–2100. Check pinned implementation supports boundary years; do not silently truncate or change an existing date.
- Invalid/empty initial date produces no selection; display current month only. Opening must not seed persisted date or enable Confirm without a selected day.
- Time parsing accepts only canonical 24-hour `HH:mm` after trimming; malformed input returns null. `formatTime` checks hour 0..23/minute 0..59 and pads both fields, with no locale-dependent formatting.
- Time dialog seeds from valid initial value; otherwise uses caller fallback (`09:00` anchors; `17:00` deadline). Fallback is tentative, never committed on opening/dismissal.
- No epoch instant/timezone conversion for wall-clock times, automatic DST adjustment, broad exception suppression, or normalization of persisted data.

## Implementation steps
1. Read pinned Material3 APIs and existing theme/layout patterns; choose only APIs available in Compose Multiplatform 1.7.3.
2. Implement/test pure parsing, canonical formatting, and UTC round-trip boundaries without adding a parallel domain validator.
3. Implement accessible trigger, calendar dialog, and clock dialog; keep helpers/components focused, using Doggo theme tokens and bounded scrollable content for small/large-font viewports.
4. Keep each new file under 200 lines; avoid a generic picker framework or extra state model. Do not refactor existing large consumer/test files.
5. Publish the signatures above and dismiss/confirm/seed rules as the Phase 01 handoff; only then release Phases 02 and 03 in parallel.

## Authored unit coverage
- Date round trips: leap day `2028-02-29`, year boundary `2026-12-31`, epoch `1970-01-01`, pre-epoch day, lower/upper supported four-digit years.
- UTC fixtures: selected UTC midnight returns the same date independently of caller/device timezone assumptions; exact epoch milliseconds asserted.
- Invalid dates: blank, malformed separator/padding, `2026-02-30`, non-leap `2025-02-29`, out-of-contract year width.
- Times: `00:00`, `09:05`, `12:00`, `23:59`; reject `24:00`, `09:60`, unpadded and seconds-bearing strings; formatter rejects invalid bounds.
- Keep tests pure here. Confirm/cancel/reopen and actual semantics are exercised through real composables in Phase 04, not simulated state copies.

## Success criteria and handoff
- Both dialogs and trigger implemented in commonMain; no new runtime dependency, typed-input fallback, or platform-specific implementation.
- Utilities produce canonical values; date selection cannot shift a day; valid pre-1900/post-2100 dates are not lost.
- Unit cases authored, shared API frozen, ownership respected. No build/test/lint/formatter execution in this phase; integration owner verifies after all implementation lands.
- Next: Phase 02 and Phase 03 consume contract without editing shared files.

## Risks and security
- Experimental API/date range differences: inspect pinned implementation before freezing API; escalate incompatibility to integration owner rather than add hidden restrictions.
- Nested dialogs/clock size: explicit labels, cancel path, bounded layout and Phase 04 actual focus/viewport checks.
- All state local and transient; no logs of user values, I/O, account access, alarm scheduling, or permission prompts.

## Unresolved questions
None requiring product input; pinned boundary-year behavior is an implementation compatibility gate, not permission to narrow supported data.
