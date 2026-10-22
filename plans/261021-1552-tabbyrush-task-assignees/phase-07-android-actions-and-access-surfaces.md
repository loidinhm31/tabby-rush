# Phase 07: Android actions and access surfaces

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: [01](phase-01-canonical-contracts-and-pure-projections.md), [03](phase-03-room-v5-and-access-persistence.md) (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; it is not implementation approval or runtime evidence.

## Parallelization Info
Wave C with05/06/08; construction depends on01/03. Sole Android app/runtime/widget/intent/test owner; Room Android builder remains03. Consume frozen UI/controller interfaces so work does not contend with06. Runtime gates require02/04/05/06 and connected Android/provider approval; JVM model tests cannot establish device acceptance.

## Overview
- Date: 2026-10-21. Description: Android actions and access surfaces. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22**; implementation scope delivered and approved with noted issues per user-provided disposition.
- Reported verification: Cycle 3 review reports `:androidApp:testDebugUnitTest` 40/40; earlier regression QA separately reports 60/60 for its Android unit run ([review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md), [QA](../../reports/qa-261021-2225-critical-fix-regression-tests.md)).
- Boundary: connected-device tests, Glance/widget workflow and native Android acceptance remain unrun; unit test counts are from separate snapshots.
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; no tests were run for this plan reconciliation.

## Key Insights
- Current surfaces already carry account/generation tokens but only String task IDs. Qualify every intent, widget action, navigation, alarm/suppression and notification identity by owner.
- Active runtime is the single store/projection source. Never open an arbitrary account named by untrusted intent or create a second widget cache.
- Existing teardown timeout/Glance failure findings are not proof of erasure. Preserve barriers and report OS cancellation failures rather than claiming success.

## Requirements
- Bind shared assignment/lookup/content actions through SessionViewModel and account runtime; use restored verified identity for03 owner binding before shared/sync use. Remove default/guessed actor paths.
- Actual widget/notification/reminder/main app actions re-resolve TaskRef and current account generation/access at admission and immediately before mutation/publication. Assignee cannot delete/create-child/manage assignment via stale/direct intent.
- Reconciled access loss removes active task/editor/widget/notification state, cancels reminders and refreshes/blank surfaces; offline cached data remains until reconnection, explicitly documented.
- Enforce confirmed privacy behavior in Android runtime/saved state: after authoritative loss clear revoked content and unsent editor snapshots, retain content-free non-replayable notice metadata only and never quarantine or restore discarded changes after restart/regrant. Native actions must resolve fresh TaskRef/current rights; preserve unrelated private tasks. No claim of remote erasure while disconnected or of OS cancellation before observed success.

## Architecture
- Android controller owns action routing:06 TodoAction ->03 canonical store/assignment API and05 lookup/sync. New task uses current verified creator; relationship queue remains independent and waits for task acknowledgement.
- Intents carry explicit ownerUserId and rowId with accountKey/generation. Validate complete structured identity and current live runtime; no delimiter-concatenation ambiguity or legacy String fallback. PendingIntent identity must include full canonical key, not rely solely on int hash requestCode.
- SurfaceAccess resolves local authoritative rights; widget projection uses01 ChildOnly/full-group rules. Account runtime observes03 active projections and05 reconciliation readiness, invalidates selected/editor content and cancels ledger-driven alarms/notifications before republishing.
- Capture local accessGeneration in pending UI/reminder/widget publications as specified by01; account generation alone does not fence same-account revocation/regrant. Recheck it immediately before publication so pre-revocation work cannot restore purged pixels/state.
- Alarm registry and reminder click/delivery paths use owner-qualified TaskRef+scheduleToken+occurrence+generation; stale pre-upgrade intents lacking owner are denied/canceled, not mapped to current user. No new exported components or assignment alerts.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/android-account-state-manager.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-sync-worker.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-projection.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-actions.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-updater.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-receiver.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-host.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-runtime-coordinator.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-action-receiver.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-intents.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-notification.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-preferences.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-settings.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-create-screen.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-refresh-worker.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-time-receiver.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-dismiss-receiver.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-alarm-receiver.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/reminder-restore-receiver.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/android-alarm-registry.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-adapter.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-intent-factory.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate.kt` — modify; Canonical Android controller/runtime/intents, permissions and access-loss cancellation; no Room builders.
- `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/task-widget-model-test.kt` — modify; Android identity/admission/publication and cancellation behavioral tests.
- `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/task-surface-notification-test.kt` — modify; Android identity/admission/publication and cancellation behavioral tests.
- `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/android-notification-adapter-test.kt` — modify; Android identity/admission/publication and cancellation behavioral tests.
- `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate-test.kt` — modify; Android identity/admission/publication and cancellation behavioral tests.
- `G:/ws/sharing/doggo/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-widget-device-test.kt` — modify; Real widget/native child-only and account-switch coverage.
- `G:/ws/sharing/doggo/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/todo-device-test.kt` — modify; Real widget/native child-only and account-switch coverage.
- `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/collaborative-surface-access-test.kt` — create; Hostile/stale intent and revoked-content publication tests (proposed new path).

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/android-account-state-manager.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-sync-worker.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-projection.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-actions.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-updater.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-receiver.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-host.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-runtime-coordinator.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-action-receiver.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-intents.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-notification.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-preferences.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-settings.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-create-screen.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-refresh-worker.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-time-receiver.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-dismiss-receiver.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-alarm-receiver.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/reminder-restore-receiver.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/android-alarm-registry.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-adapter.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-intent-factory.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/main/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/task-widget-model-test.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/task-surface-notification-test.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/android-notification-adapter-test.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate-test.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-widget-device-test.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/todo-device-test.kt` | modify | 07 exclusively |
| `G:/ws/sharing/doggo/androidApp/src/test/kotlin/dev/tabbyrush/android/collaborative-surface-access-test.kt` | create (proposed) | 07 exclusively |

## Implementation Steps
1. Use LSP references for TodoAction, TaskRef, store constructor, surface/reminder contracts and intent factory exports. Migrate every owned Android caller and test; do not edit shared DTOs/store/builders to preserve compatibility aliases.
2. At runtime creation/auth restoration supply AccountIdentity with origin/user to03 initializer, verify partition match and await atomic binding before dispatch/shared actions. Preserve offline unresolved-private read display; never userId default fallback. Fence late lookup/sync/mutation results by current account generation.
3. Wire assignment lookup/select/clear/save actions, direct/inherited status and separate content/assignment feedback in SessionViewModel to05/03. Existing task read/editor/conflict/surface navigation uses TaskRef. Clear in-memory revoked editor content on authoritative invalidation, generic evidence only.
4. Migrate widget actions, surface receivers, main Activity routing and notification click extras to structured canonical identity. Reject missing/forged/mixed owner/account/generation; only use current runtime, never lazily initialize arbitrary intent account. Use unique encoded data/identity for PendingIntent equality and canonical suppression keys, not a rowId hash alone.
5. Admit each completion/edit action through SurfaceAccountToken and03 current rights; re-read under mutation lock. Only Changed enqueues sync; Unchanged/Missing/AccessDenied do not. Assignee stale delete/add-child/assignment actions fail even if a prior widget displayed owner controls.
6. Project real ChildOnly item in widgets and app without private parent. Preserve desired completion and deadline selection behavior; automatic parent completion requires01 full-group reconciled coverage. Creator-only child creation checks live parent and current creator state.
7. Observe authoritative access loss in runtime coordinator: stop publications for affected keys, let03's confirmed content/unsent-payload purge and ledger cancellation commit, clear revoked in-memory/saved editor/selection/review snapshots, then cancel matching alarms/notifications/work, refresh or blank widgets and close stale widget sessions as needed. Keep only generic content-free evidence; no Android recovery cache/quarantine or replay after restart/regrant. Admission and immediate pre-publication accessGeneration/current-rights recheck prevent queued stale content publishing; retain no second persistent task store.
8. Qualify alarm registry/delivery/restoration keys and notification identifiers with TaskRef, schedule token, occurrence and account generation. At alarm delivery and click, resolve current accessible task again; pre-post check suppresses canceled/revoked task. Pending private owner data outside revoked scope stays unaffected.
9. Preserve ordered leave/delivery quiescence; cancellation timeout must keep surface readiness unavailable and be reported rather than silently authorize publication/claim remote erasure. Do not broaden this feature into unrelated notification/runtime redesign.
10. Add hostile stale/owner-collision action tests and real device widget/task workflow tests. Exercise Bob child-only open/edit, Alice inherited edits, creator assignment/reassign conflict, denied widget actions, offline restart then revoke/reconnect, alarm cancellation and account switch during publication. Inspect restored editor/saved state and widget/notification refresh after denial/regrant for no revoked content or discarded-intent replay; OPERATION_FORBIDDEN alone preserves readable state. Retain Android manifest exposure policy with no new exported components.
11. Capture NOT RUN device steps for09: connected debug client against disposable loopback/emulator Hub route, inspect actual visible widget and notification state before/after revoke, stale action attempts and reminders canceled. Provider interactive approval is separate; do not fabricate screenshots or claim unit tests equal runtime proof.

## Todo list
- [x] Implement numbered steps in assigned files, migrating LSP-referenced owned callers without shims.
- [x] Add/update the phase-owned behavioral/security/migration coverage specified above; no copied-source/default tests.
- [x] Implement confirmed purge-driven clearing of Android editor/saved/widget/notification state and content-free notice flow, with current accessGeneration fencing and no recovery/replay after restart/regrant; preserve private rows and report OS cancellation failures.
- [x] Hand off real interfaces/behavior and pending checks to main/integration owner; do not run mid-flight checks without explicit verification assignment.

## Success Criteria
- **Acceptance criterion:** Android behavioral tests reject missing owner/stale generation/forged role actions and distinguish duplicate-owner IDs; widget/notification identifiers cannot cross-target. **Reported evidence:** Cycle 3 review reports 40/40 Android unit tests (earlier regression QA reports 60/60 in a separate run); summaries do not itemize these named outcomes.
- **Device criterion not run:** connected Android app+Glance+native alarm/reminder exercise proves child-only editing without private parent, offline restart/reconciliation, revoked content removal, no replay and no stale publication. Connected-device/provider evidence remains pending G4.
- **Native inspection not reported:** Android runtime/device inspection after authoritative denial shows no revoked editor/saved-state payload or recovery cache, generic notice only, canceled/blanked native surfaces as actually observed, and no discarded edit replay after restart/regrant. Delayed old-accessGeneration publication is rejected; unrelated private state and still-readable forbidden-action targets remain intact.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- Android OS may retain rendered notification/widget pixels until actual cancellation/refresh; prove device behavior, not store removal alone. Old PendingIntents need fail-closed handling after upgrade. Do not weaken current account barriers to recover a stale surface.

## Security Considerations
- No exported service/receiver changes or untrusted intent-triggered account opening. Exact TaskRef+current rights required twice for asynchronous publication. Generic revoked evidence/notice must not retain titles/deadlines in Android saved state/preferences.

## Next steps
Deliver Android runtime/tests to09 evidence owner. Route shared UI defects06, sync05, storage03 and contracts01;09 never edits these files. Connected-device approval remains release hold until exercised.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
