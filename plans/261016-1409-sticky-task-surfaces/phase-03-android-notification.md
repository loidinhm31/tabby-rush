# Phase03 — Android quiet deadline notification and settings leaf

## Context links
[Overview](plan.md) · [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md) · [Android research](research/researcher-01-android-surfaces.md) · [analysis](reports/01-codebase-analysis.md) · [contracts](architecture-contracts.md) · [ownership](file-ownership.md#phase03--android-notificationsettings-leaf) · [acceptance](acceptance-matrix.md) · [dependencies](dependency-matrix.md).

## Parallelization Info
Group B; **hard dependency01** selection/account/access signatures and dependency foundation. Independent edits from02/04.05 consumes completed02+03 and owns real ActivityResult registration, Application observers, manifest and wipe composition. This leaf is complete real logic/UI against contracts, not runtime-independent end-to-end acceptance.

## Overview
- Date: 2026-10-16. Priority: P2. Effort: **20h**.
- Implementation status: **pending / 0%**. Review status: pending.
- Implement one optional quiet next-deadline notification, durable dismissal/opt-in, private receivers, inexact refresh and standalone settings UI.

## Key Insights
Reminder projection has conflict knowledge; reminder eligibility additionally requires enabled rule/conflict-free/token. Display must not inherit those conditions. Android13+ permission is actual runtime flow; Android14+ ongoing can be swiped away. Persistent suppression and programmatic-cancel receipt distinction are necessary to avoid repost loops and stale dismissals.

## Requirements
1. Default off, account-local preferences outside Room/task sync; separate channel/tag/ID from reminders, private title and generic lock-screen public version.
2. Eligibility includes incomplete/not-deleted saved deadline **including conflicted projections** with conflict indication, independent of reminder enablement. Notification reads base+submitted outbox, not drafts.
3. Oldest overdue then earliest upcoming by `(overdueEpochMs, taskId ASC)`; now==boundary is overdue; date-only next-day boundary not morning anchor.
4. Complete explicitly true; Open direct Activity; immutable token-bound intents and posted receipt-bound delete. Durable generation/task/full-deadline suppression survives restart/title edits/time/sync.
5. Standalone settings exposes opt-in/rationale/denied/app-off/channel-off and user-directed Settings;05 provides actual ActivityResult permission launch. Reminder controls remain independent.
6. Supported clock/date/zone/data/resume triggers, optional one-time unique inexact WorkManager boundary job without network constraint; no FGS/exact permission/polling/new sync cadence.

## Architecture
Coordinator receives token-tagged snapshot through real Android host, computes shared selector, capability and suppression, persists receipt before posting, publishes via final current-token admission. Preferences atomically store local opt-in/suppression/current receipt under account path. Programmatic cancel invalidates receipt before native cancel; dismiss receiver accepts only current receipt, persists suppression before refresh. Private Complete/time/delete receivers and worker resolve host;05 installs process-lifetime coordinator and registrations.

## Related code files
**Exclusive creates:** complete03 table covers task-surface-preferences, notification/intents/action/dismiss/time/worker/permission/settings code, notification icon/strings, unit and real preference-device tests, evidence report.
**Read-only:** shared selector/projection/deadline contract; existing Android reminder notification/privacy/PendingIntent/capability patterns;01 Android host. No existing reminder adapter/controller changes or shared UI settings edits.

## File Ownership
Exactly03 new paths; own notification and preferences tests.01 owns dependencies;05 owns every existing Android integration file/manifest/auditor and ActivityResult flow. Keep strings/icons feature-specific. Later acceptance does not edit these tests/preferences logic.

Explicit owned paths (repository-relative; all proposed creates):
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-preferences.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-notification.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-intents.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-action-receiver.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-dismiss-receiver.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-time-receiver.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-refresh-worker.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-permission-controller.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-settings.kt`
- `androidApp/src/main/res/drawable/task-surface-notification-icon.xml`
- `androidApp/src/main/res/values/task-surface-strings.xml`
- `androidApp/src/test/kotlin/dev/doggo/android/task-surface-notification-test.kt`
- `androidApp/src/androidTest/kotlin/dev/doggo/android/task-surface-preferences-device-test.kt`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-03-notification-evidence.md`

## Implementation Steps
1. Consume01 frozen selector/access/deadline key. Implement preferences atomic write/read/delete, default-off and capability state values per contracts; no task text persisted in suppression metadata.
2. Implement dedicated quiet channel `doggo_task_surface_v1`, tag `doggo.task.surface`, ID4101, small icon, private/public versions and conflict label. Do not alter reminder channel/settings or evade OS block by new channel.
3. Implement deterministic selection refresh using shared selector and current time, never reminder predicate or display strings. No eligible selection cancels and clears suppression; same suppressed key cancels/stays absent.
4. Implement explicit immutable Complete/Open/delete identity with token+task+operation and durable opaque post receipt. Complete uses access setter(true); Open direct Activity. Validate missing/deleted/current token again before operations; stale callback harmless.
5. Implement deletion semantics: persist exact selection suppression before scheduling anything; title/reminder-only edits do not reset; full deadline/selection changes/disappearance or deliberate off→on reset. Programmatic replacement/cancel invalidates active receipt first so delayed deletes cannot suppress new task or be misclassified.
6. Implement capability controller and standalone native settings composable with real callbacks for opt-in/rationale/request/settings. API33+ request only after explicit user opt-in; denial supported/unavailable, no resume nag.05 owns registerForActivityResult + result dispatch and resume update; no placeholder callback or reminder no-op.
7. Implement private supported clock/date/zone receiver and boundary worker. One account-tagged unique one-time inexact job at future boundary with >=15-minute delay and no network constraint; coalesce refresh, no permanent periodic loop. Startup/data/resume reconcile independently of worker precision; force-stop is explicitly unsupported until next launch.
8. Implement account cancellation/deletion API for05: cancel boundary work and await, invalidate receipt, cancel notification, delete account preferences; do not impersonate user dismissal. Fail closed on persistence/cancel failure.
9. Author tests of production selection/capability/suppression state and actual private file/preferences reopen. Cover conflict/reminder-disabled, equality/date-only/ties, time backward-forward, stale post receipt, process restart, repeated Complete and unavailable independent widget state.
10. Handoff native entry components/settings callbacks/cancel contract and leaf evidence to05. Native shade/permission/swipe proof remains05/07 gate; no fake runtime acceptance.

## Todo list
- [ ] Implement preferences, selection/capability and private notification identities.
- [ ] Implement durable suppression/posted-receipt cancellation distinction.
- [ ] Implement settings/permission support and bounded inexact triggers.
- [ ] Author production behavior and actual preferences reopen tests.
- [ ] Deliver real leaf handoff and pending native scenarios to05.

## Success Criteria
Acceptance4/5/6/7/12 notification logic fully represented. Same selected key never immediately reposts after user dismissal across title edit/sync/restart; no credential/task-text preference leakage. Reminder-disabled deadline is selected, conflict knowledge explicit, no drafts. Future API32/33/34+ actual UI proof is required and stays pending until observed.

## Conflict Prevention
No widget files, shared selection changes, Activity/Application/VM/manifest edits or reminder-system redesign. Changes to frozen public boundaries reviewed with01 and05 first.05 alone wires process observers and permission launcher;03 provides production behavior, not a second composition root.

## Risk Assessment
Delete intents may not fire for every OS removal; do not infer callbacks. Persisted receipt races require process-reopen tests. OS delayed WorkManager and force-stop prevent exact rollover guarantees. Channel importance controlled by user; supported unavailable state instead of repost/channel recreation.

## Security Considerations
Explicit private receivers, immutable intents, stale generation rejection and final publication fence. Private lock-screen title/generic public version. Default-off/account-scoped preference deletion on wipe; no FGS/overlay/exact-alarm escalation. Permission result cannot grant stale account opt-in without revalidation.

## Next steps
Terminal03 plus02 enables05 integration. Main integrator runs future unit/device checks and07 real shade/permission/dismissal acceptance; baseline reminder holds stay unchanged.

## Unresolved questions
No product decisions left. Actual API34+ delete-callback behavior and supported clock-wake coverage require native evidence, not guarantee.
