# Phase 02 — Deadline editor calendar and clock integration

## Overview
- Priority P2; status completed on 2026-10-17; effort 2h.
- Depends on completed [Phase 01](phase-01-picker-components.md) API handoff; runs in parallel with Phase 03.
- Replace all three date/time text fields in `DeadlineEditorSection`; retain all other behavior.
- Context: [master ownership](plan.md), [strict timezone rules](../../docs/code-standards.md#92-strict-timezone--dst-resolution).

## Exclusive ownership
Modify only:
- `shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-editor-section.kt`
Read-only:
- `shared/src/commonMain/kotlin/dev/doggo/shared/ui/date-time-pickers.kt`
- `shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-calculator-helpers.kt`
- `shared/src/commonMain/kotlin/dev/doggo/shared/ui/todo-editor.kt`
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-ui-state.kt`
No created/deleted files. Do not edit shared components, settings, tests, build files, docs, or plan status.

## Key insights and invariants
- Current date, specific deadline time, and date-only anchor are `OutlinedTextField` inputs; timezone is a separate IANA field and remains editable.
- Existing state stores strings and dispatches `TodoAction.UpdateEditorState(editor.copy(...))`; preserve this boundary without controller/model changes.
- Existing date/time/anchor edits clear `chosenOffsetSeconds`. Preserve reset on actual value changes; opening/cancel/unchanged confirmation must not invalidate a user's DST overlap choice.
- Existing `DeadlineCalculatorHelpers` remains authoritative for invalid timezone, DST gaps/overlaps, reminder bounds, and schedule preview. Calendar validity does not imply a resolvable zoned instant.

## UI and action contract
| Existing value | Trigger label | Dialog/title | Confirm result |
|---|---|---|---|
| `editor.deadlineDate` | `Deadline date` | `DoggoDatePickerDialog`, same title | Copy date, clear chosen offset |
| `editor.deadlineTime` when `hasTime` | `Deadline time` | `DoggoTimePickerDialog`, same title; fallback `17:00` | Copy time, clear chosen offset |
| `editor.reminderAnchorTime` otherwise | `Date-only reminder anchor time` | `DoggoTimePickerDialog`, same title; fallback `09:00` | Copy anchor, clear chosen offset |
- Use Phase 01 `DateTimePickerButton`; show saved canonical value or explicit chooser prompt. Preserve date-only supporting text about reminder anchor and next-day overdue boundary.
- Trigger exposes click/button semantics and value, never an editable-text action or a typing hint.
- Confirm closes picker; emit exactly one update only when selected canonical value differs from current field. Use latest editor snapshot at callback time, preserving unrelated state.
- Cancel, outside-dismiss, Back/Escape where supported, and unchanged confirmation close picker without actions or offset resets.
- No text entry, `TimeInput`, calendar keyboard switch, automatic date/time assignment, or instant conversion in this file.

## Local state and composition
- Keep one nullable private picker target (`Date`, `DeadlineTime`, `AnchorTime`); one modal at most, keyed to editor identity/lifecycle.
- Dialog state exists only while target is visible. Closing and reopening reseeds from current editor, never the previous canceled selection.
- Close/dispose an open picker if its parent editor closes, task identity changes, deadline is disabled, or its time/anchor branch is no longer applicable. Do not allow a stale callback to update hidden fields.
- Do not make modal visibility part of `EditorState`, persist it, or reset task drafts as a side effect.

## Implementation steps
1. Import Phase 01 shared components and Compose local state; introduce the single active-target state inside this owned file.
2. Replace date text field in its existing date/timezone row with `DateTimePickerButton`, preserving weight and >=48dp target size. Keep IANA timezone input and its existing offset-reset callback.
3. Replace timed deadline field with clock trigger; replace date-only anchor field with clock trigger plus existing supporting copy.
4. Conditionally compose the corresponding shared dialog and wire confirm/dismiss actions per table, including no-op unchanged confirmation.
5. Preserve Set Deadline and Specific Time toggles, strict-v2 upgrade warning, overlap chips, reminder enable/lead/count/interval controls, validation text, and schedule preview.
6. Remove only newly obsolete date/time text-field labels/placeholders/imports; keep `OutlinedTextField` for timezone and numeric controls. Avoid unrelated layout/refactoring changes.
7. Report changed file and behavior review to integration owner; Phase 04 authors/runs tests after both parallel phases finish.

## Success criteria
- Deadline date is chosen from calendar; timed deadline and date-only anchor are chosen from clock; all three have accessible non-editable triggers.
- Confirmed date/time strings remain canonical; only the intended field and `chosenOffsetSeconds` change.
- Cancel/dismiss/reopen and unchanged confirmation preserve editor state and previous DST choice; invalid initial data is recoverable by selection without mutating it on open.
- Hiding deadline removes its pickers; `hasTime` controls which time trigger exists; no stale picker state crosses tasks.
- Explicit IANA timezone, DST gap rejection, overlap offset choice, strict-v2 save guard, and date-only overdue logic remain intact.
- No files outside exclusive ownership changed. No build/test/lint/formatter execution here.

## Verification scenarios for Phase 04
- Blank/new task: enable deadline, choose leap-day date, choose specific time, inspect emitted canonical update.
- Existing timed task: select different date/time and verify chosen offset clears; confirm same value and cancel modified tentative values without clearing it.
- Date-only task: change anchor through clock; validation still yields null deadline time and next-day overdue boundary.
- America/New_York: select `2026-03-08 02:30` and retain gap error; `2026-11-01 01:30` still requires explicit offset choice after changes.
- Toggle time/deadline, close editor, reopen another task; inspect visibility, current values, and no unintended action.

## Risks and security
- Parent dialog nesting and narrow date/timezone row may affect layout/focus; integration checks Android and desktop, large fonts, and keyboard traversal.
- Date/time edits must not schedule anything before existing save path; never introduce direct persistence, network, alarm, permission, or account operations.

## Next steps and unresolved questions
Hand off to Phase 04 only after Phase 03 also completes. No unresolved product questions.
