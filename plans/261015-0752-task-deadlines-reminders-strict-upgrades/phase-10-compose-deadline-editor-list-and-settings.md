# Phase 10 — Shared deadline editor, task list and local settings

## Context links
[Parent](plan.md) · [Field/time contract](phase-01-freeze-contract-and-transition-design.md) · [Native proof](phase-02-prove-native-delivery-and-dst.md) · [Scheduling](phase-07-shared-scheduling-and-account-safety.md) · [Design guidelines](../../docs/design-guidelines.md)

## Overview
Date 2026-10-15. P2. Implementation pending 0%; review pending. Estimate 20h. Owner: shared Compose lead; controllers coordinated by platform integration leads. Dependencies: G1/G2 plus 05/06/07 and capability contracts from 08/09. No broad UI before migration/native proof gates.

## Key Insights
Unsaved editor state must remain pure local UI/draft state. Permission enablement is device-local; rule defaults copy into new synced task. Date-only anchor09:00 must not make task overdue that morning.

## Requirements
- Create/edit/clear optional deadline date, optional time, explicit zone/overlap offset; enablement, lead, repeat interval/count, exact preview/validation.
- List shows date-only/timed deadline and overdue using text/semantics, not only color.
- Settings: local enablement/default new-task rule/date-only anchor initially09:00/capability and permission controls.
- Offline save/restart, accessible responsive UI, conflict metadata inspection and v1 upgrade-required behavior.

## Architecture
- Immutable editor state captures existing original date/zone/offset/instant/token. Editing title/completion only preserves schedule snapshot exactly; only deadline/rule/anchor edits invoke resolver and token regeneration at committed save.
- New task initialized from current account-local defaults; settings changes do not modify open saved task rules or existing records. Unsaved modal preview may recalculate, but never schedules/cancels native work.
- Time-disabled means date-only, not midnight timed task. Date-only preview explicitly names reminder anchor timezone and next-day overdue distinction. Timed list displays device zone; edit shows captured zone/offset and local date.
- Count1 hides/disables interval and stores null; repeat count2–10 requires >=15min. Render all opportunity times, with missed/outside-grace opportunities labeled rather than silently truncating. Invalid last>anchor rejects save.
- V1 account status blocks deadline save with clear upgrade requirement while preserving editable unsynced title/completion; no silent local-only deadline mode. Unknown sync states retain editor data and conflict guidance.
- Settings capability describes separate device-disabled/notificationDenied/channelDisabled/nativeUnavailable/bestEffort/exactRequested states; exactRequested does not promise perfectly precise visibility. Desktop explains process lifetime, Android explains force-stop/permissions/quota limits. Actions are user-initiated OS permission/settings intents supplied by platform controller.
- Conflict local/remote comparison includes full deadline/rule/timezone, explicit unknown legacy receipt warning and consequence of copying. Cancel/Decide Later never changes scheduling.
- List overdue rendering updates on wall-clock boundary/timezone display changes without altering stored tasks or issuing sync operations.

## Related code files
Doggo root `/home/loidinh/WS/doggo`.
Modify shared `contract/{todo-action,todo-ui-state,todo-models}.kt`, `ui/{todo-editor,todo-list,doggo-app,conflict-review}.kt`, common Compose string resources `shared/src/commonMain/composeResources/values/strings.xml` as used by current conventions; Android `session-view-model.kt`, desktop `main.kt` controller actions.
Create focused `ui/reminder-settings.kt`, deadline picker/preview components only if existing editor would mix concerns. Extend shared `commonTest/ui/todo-semantics-test.kt` for consumer-visible semantic behavior, not literal wording. Update feature PDR/design/accessibility docs after runtime evidence.

## Implementation Steps
1. Wire immutable editor/actions for deadline/rule/timezone without mutating persistence before submit; keep original saved snapshot when unchanged.
2. Add shared date/time/zone/offset selection, rule controls and preview from one calculator; explicit validation messages focus first invalid control.
3. Add date-only/timed deadline list and text overdue status; handle completed/deleted rows consistently and preserve existing sorting/filter scope unless explicitly required.
4. Add local account settings/defaults and platform capability/action callbacks; no permission requests from background receiver or recomposition.
5. Extend conflict review for new fields and uncertain legacy warnings; update save/cancel/reapply/copy behavior with full typed data.
6. Launch actual Android/Desktop surfaces. Create offline date-only/timed tasks, cancel edits, change title/timezone/defaults, restart and inspect persisted preview/alarms.
7. Observe compact/expanded widths, 200% font scaling, keyboard focus and TalkBack/Orca/Narrator labels on available target; do not treat design screenshots as live evidence.

## Todo list
- [ ] Pure editor/timezone/DST/preview validation and save.
- [ ] Deadline/overdue list with non-color semantics.
- [ ] Device-local defaults/capability/permission settings.
- [ ] Full conflict UI and actual responsive/accessibility smoke.

## Success Criteria
Actual UI creates/saves/clears/restarts with same values; cancel leaves ledger/native schedule unchanged. Count bounds/invalid arithmetic visibly reject; date-only at09:00 not overdue until saved next-day boundary. Title/completion/default/device-zone changes preserve anchors/token. At least48dp targets and non-color labels; rendered layout and screen-reader evidence per platform. Existing semantic tests only where behavior uncertain; no new wording/wiring tests.

## Risk Assessment
KMP calendar widgets may lack zone/offset affordance; build minimal shared selection around proven resolver, not alternate date semantics. Busy settings should distinguish task-rule enabled vs device capability.

## Security Considerations
No secrets in settings/error model; local capability cannot grant Hub admin privileges. Notification previews and conflict text remain account-scoped; teardown closes editor state.

## Next steps
Phase 11 executes full acceptance matrix with actual platforms; phase 12 updates public feature docs/cutover status.

## Unresolved questions
None; user scope already agreed. Native/DST proof gates must be satisfied rather than bypassed by UI.
