# Phase 03 — Reminder settings clock integration

## Overview
- Priority P2; status completed on 2026-10-17; effort 1h.
- Depends on completed [Phase 01](phase-01-picker-components.md) API handoff; runs in parallel with Phase 02.
- Replace the default date-only anchor time input with a clock picker, preserving the settings draft/save boundary.
- Context: [master plan](plan.md), [local settings architecture](../../docs/system-architecture.md#611-shared-deadline-editor-task-list-and-local-settings-phase-10-delivered).

## Exclusive ownership
Modify only:
- `shared/src/commonMain/kotlin/dev/doggo/shared/ui/reminder-settings.kt`
Read-only:
- `shared/src/commonMain/kotlin/dev/doggo/shared/ui/date-time-pickers.kt`
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-ui-state.kt`
No created/deleted files. Do not edit deadline integration, shared components, tests, build files, docs, or plan status.

## Existing behavior to preserve
- `defaultAnchorTime` is local Compose state seeded from `settings.defaultDateOnlyAnchorTime`.
- `Save Settings` emits `TodoAction.SaveSettings(settings.copy(...))`; outer Cancel emits `TodoAction.CloseSettings` without saving.
- Defaults are device- and account-local, apply only to new tasks, and do not rewrite stored task rules/cloud data.
- Anchor input is visible independently of the default-reminder toggle. Preserve that visibility.
- Lead minutes, count, and interval are numeric settings, not time-of-day inputs; their existing fields and bounds remain unchanged.

## Interaction and data flow
```text
settings -> local defaultAnchorTime draft -> clock tentative selection
Confirm -> canonical HH:mm in draft only -> outer Save Settings -> existing SaveSettings action
Picker Cancel/dismiss -> unchanged draft; outer Cancel -> CloseSettings, no SaveSettings
```
- Replace anchor `OutlinedTextField` with `DateTimePickerButton`, label `Default date-only anchor time`, canonical displayed value (or `Choose time`).
- Preserve supporting text: “Used for date-only task reminders (initial default 09:00).”
- Local boolean controls one `DoggoTimePickerDialog`, title matching label, `initialTime = defaultAnchorTime`, `fallbackTime = "09:00"`.
- On picker Confirm, assign selected value to draft and close; no `TodoAction`, I/O, persistence, rescheduling, or permission operation.
- Picker Cancel/outside-dismiss/Back/Escape where supported only closes; reopening seeds from current draft, not discarded tentative selection.
- Outer Save continues to submit all draft preferences together; retain existing blank-anchor fallback behavior. New selections are already canonical.
- Outer Cancel or parent disposal removes nested picker and drafts through existing lifecycle; do not add global/saved modal state.

## Implementation steps
1. Read Phase 01 contract; add local picker visibility state alongside existing settings drafts, without changing other draft initialization behavior.
2. Replace only default anchor text-field block with shared trigger and existing supporting text. Use Doggo spacing, full width, >=48dp click target.
3. Conditionally compose shared clock dialog; wire confirm to draft assignment and dismiss to visibility reset. Do not expose `TimeInput` or keyboard-mode switch.
4. Ensure displayed draft refreshes after confirmation; repeated opening starts from that draft. Do not reinitialize all settings when incoming capability/state refreshes.
5. Preserve platform capability card, OS Settings/Exact Alarms actions, notifications/default-reminder toggles, numeric bounds, and outer Save/Cancel payload construction.
6. Remove only obsolete anchor typing hint/placeholder and unused imports; keep `OutlinedTextField` for numeric controls.
7. Report owned-file change and behavior review; Phase 04 provides rendered tests and verification after both consumers land.

## Success criteria
- Default anchor uses a 24-hour clock selection dialog with no typing path; trigger exposes label/value and button click semantics.
- Existing valid anchor initializes clock accurately; empty/malformed initial anchor has tentative 09:00 seed, never auto-saved by opening/canceling.
- Confirm updates local anchor draft only. Canceling a later picker preserves previously confirmed draft.
- `SaveSettings` fires only from outer Save and includes canonical selected anchor plus untouched other settings.
- Outer Cancel never emits `SaveSettings`; changing anchor does not modify existing tasks or account/cloud data.
- Anchor remains available when default reminders are disabled; numeric inputs and permission controls are unchanged.
- Zero edits to Phase 02 or shared/test/build/doc files; no build/test/lint/formatter execution here.

## Verification scenarios for Phase 04
- Initial `09:00`: open clock, select `23:59`, Confirm, assert displayed draft changes and action list remains empty until outer Save.
- Open again, tentatively change then Cancel; prior `23:59` draft remains and eventual Save preserves it.
- Confirm a draft change then outer Cancel; only `CloseSettings` observed. Reopen with original persisted settings; original value remains.
- Select midnight `00:00`, Save, verify exact canonical string and unmodified capability/other preference values in payload.
- Disable default reminders and still open anchor picker; existing numeric visibility and OS permission buttons behave as before.

## Risks and security
- Nested clock can steal focus or obstruct actions on short screens: use Phase 01 bounded dialog; Phase 04 verifies traversal, dismissal, and focus return.
- Avoid settings re-seeding or controller changes disguised as picker work. Keep account isolation and persistence in existing SaveSettings handler.
- No added logging, storage format, permissions, network, or schedule changes.

## Next steps and unresolved questions
Hand off to Phase 04 after Phase 02 also completes. No unresolved product questions.
