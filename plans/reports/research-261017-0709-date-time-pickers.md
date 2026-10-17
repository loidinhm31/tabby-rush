# Research & Preflight: Date & Time Picker UI Integration

**Slug:** `date-time-pickers`  
**Date:** 2026-10-17  
**Session:** 261017-0709  

---

## 1. Scope Boundary & Inventory

### Target Surfaces
1. **`DeadlineEditorSection`** (`shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-editor-section.kt`):
   - `deadlineDate`: Replace text entry with interactive date display + calendar picker dialog (`DatePickerDialog`).
   - `deadlineTime`: Replace text entry with interactive time display + timer/clock picker dialog (`TimePicker`).
   - `reminderAnchorTime`: Replace text entry with interactive time display + timer/clock picker dialog (`TimePicker`).
2. **`ReminderSettingsDialog`** (`shared/src/commonMain/kotlin/dev/doggo/shared/ui/reminder-settings.kt`):
   - `defaultDateOnlyAnchorTime`: Replace text entry with interactive time display + timer/clock picker dialog (`TimePicker`).
3. **Reusable Component Module** (`shared/src/commonMain/kotlin/dev/doggo/shared/ui/date-time-pickers.kt`):
   - Standalone `DoggoDatePickerDialog` and `DoggoTimePickerDialog` wrapping Compose Material 3 pickers.

### Out-of-Scope (Title-Only Entry Points)
- `TaskSurfaceCreateScreen` (Android widget quick-add): Title-only.
- `StickyTaskContent` (Desktop tray quick-add): Title-only.
- `TodoEditor` raw title field: Title-only.
- Numeric interval/lead inputs (`reminderLeadMinutes`, `reminderCount`): Kept as validated numeric inputs or chips.

---

## 2. Technical Decisions & Acceptance Criteria

### Core Requirements
- **No manual typing for date/time**: Date and time values must be selected via calendar and clock dialogs. Text fields for date and time must be non-editable by typing (read-only / clickable surface with trailing icon).
- **Format Integrity**:
  - Date output: Strict `YYYY-MM-DD`.
  - Time output: Strict `HH:mm` (24-hour format).
  - Timezone: Display and selection preserves current IANA timezone handling without timezone drift.
- **UTC DatePicker Round-Trip**:
  - `DatePickerState.selectedDateMillis` is midnight UTC. Formatting must convert via `TimeZone.UTC` to avoid off-by-one local day shifts.
- **TimePicker Invariants**:
  - Uses 24-hour display (`is24Hour = true`).
  - Output pads hours and minutes to 2 digits (`%02d:%02d`).
- **DST & Offset Handling**:
  - Reset `chosenOffsetSeconds = null` upon date or time changes so `DeadlineCalculatorHelpers` re-evaluates DST fall-back ambiguity or spring-forward gaps cleanly.
- **Accessibility & Contrast**:
  - Minimum touch target 48dp (`DoggoThemeTokens.MinTouchTargetSize`).
  - Explicit semantics: `contentDescription` for calendar and clock launch buttons.

---

## 3. Side-Effect Risks & Mitigations

| Risk | Cause | Mitigation |
|:---|:---|:---|
| Off-by-one date shift | `DatePickerState` provides UTC millis; formatting with local timezone shifts day across timezones | Always format `selectedDateMillis` using `TimeZone.UTC` |
| Corrupted or unparseable state | Dialog opened with empty or malformed initial string | Safe fallback to current UTC date / 09:00 default anchor |
| Broken DST overlap flow | Changing time keeps previous ambiguous offset | Explicitly clear `chosenOffsetSeconds` when selecting date or time |
| Desktop / Android Dialog clipping | Material 3 pickers can be large on smaller screens | Use proper scrolling or compact container constraints in dialog |

---

## 4. Testing Strategy

1. **Unit & Semantics Tests** (`shared/src/commonTest/kotlin/dev/doggo/shared/ui/todo-semantics-test.kt` & new picker tests):
   - Verify date formatting from epoch millis across UTC boundaries and leap days.
   - Verify time formatting for midnight (`00:00`), noon (`12:00`), late evening (`23:59`), and leading zeroes.
   - Verify cancel/dismiss does not alter state.
   - Verify selection updates state and clears DST `chosenOffsetSeconds`.
2. **Regression Verification**:
   - Run `:shared:jvmTest`, `:desktopApp:test`, and `:androidApp:testDebugUnitTest`.
