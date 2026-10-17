# Phase05 — Android runtime, navigation, wipe, registration and auditor integration

## Context links
[Overview](plan.md) · [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md) · [analysis](reports/01-codebase-analysis.md) · [Android research](research/researcher-01-android-surfaces.md) · [contracts](architecture-contracts.md) · [ownership](file-ownership.md#phase05--android-integration-single-owner) · [acceptance](acceptance-matrix.md) · [dependencies](dependency-matrix.md).

## Parallelization Info
Group C; **hard dependencies01+02+03**, all terminal and real. Can overlap06 once its own04 dependency is complete. Sole owner of existing Android Application/runtime/VM/Activity/worker/restore/cancel/wipe roots, manifest and security auditor.07 waits05+06. No runtime mock/scaffold to bridge incomplete leaves.

## Overview
- Date: 2026-10-16. Priority: P2. Effort: **24h**.
- Implementation status: **delivered / 100%**. Review status: **Cycle 2 8.8/10 Conditional Pass; approved for implementation handoff, 0 critical issues**.
- Delivered real Android readiness/access/publication, process observers, navigation/settings, wipe cleanup, registration and security audit integration. Cycle 2 downstream acceptance and safety findings remain open.

## Key Insights
Session restoration is asynchronous; stale `getOrCreateAccountRuntime(key)` can displace current runtime. Current navigation only account-checks and main toggle copies stale Todo. Startup wipe recovery cancels reminders but not launcher contents/new jobs. Existing release audit rejects all exported components except MainActivity; widget exception, if needed, must be narrowly justified.

## Requirements
1. Exact Restoring/Ready/SignedOut/Locked/Unavailable states;4s bounded cold wait; token-safe read/mutate/navigation/publication and drain through actual runtime ownership.
2. Application/runtime observers continue without Activity/VM; observe all tasks and persisted schedule state, coalesce successful mutation/sync/startup/account/time refresh; no new sync cadence/polling.
3. Add opens small app creation UI reusing existing editor/validation/default semantics. Open selects fresh task only after current account+generation revalidation. Migrate main completion to setter-by-ID.
4. Actual ActivityResult POST_NOTIFICATIONS after opt-in/rationale and capability checks on resume; native settings independent of reminders and accessible from app.
5. Both normal leave/account replacement and startup pending-wipe recovery cancel/drain/clear all OS content/preferences/work before Room close/marker removal; same account new generation rejects old action.
6. Register complete leaves with immutable intents/private receivers and verified widget export behavior; update production source+merged-manifest security audit narrowly, no broad allowlist.

## Architecture
`TabbyRushApplication : TaskSurfaceHostOwner` owns real Android task-surface access/coordinator; `AccountRuntime` owns observer jobs/leases. Runtime establishment alone opens current account after restore/wipe/unlock checks. Existing delivery gate counts surface read/mutation/publication operations; publication/lifecycle mutex final-checks and serializes actual OS handoff against transition without holding it during drain. Existing AccountLeaveProtocol sequence remains authoritative; composite scheduler hook clears external state and fails closed. Main Activity handles direct Add/Open intents/real permission launcher, not a second task database/runtime.

## Related code files
**Exclusive modifications/creates:** all phase05 ownership rows are exhaustive: Application/runtime/factory/account-state/composite cancel/VM/Activity/sync worker+scheduler/restore receiver, proposed real surface-access/runtime-coordinator/create-screen, manifest, auditor+test, existing cancel test and new real account/navigation device tests, evidence.
**Read-only:** completed02/03 module files/resources/tests and01 contracts/store/build metadata; existing reminder adapter/intents/capability/permission/alarm files; shared leave/delivery gate/sync/projectors/editor. No shared schema or backup-rule changes.

## File Ownership
Exactly05 paths, including all affected Android roots, security auditor and integration tests.01 owns dependencies/locks;02 owns widget;03 notification/preferences/settings logic. A leaf defect goes back to its owner;05 never edits leaf test/source to force integration.07 has no production/test writes.

Explicit owned paths (repository-relative; actions/reasons in central manifest):
- `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/android-account-state-manager.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-sync-worker.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/android-sync-scheduler.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/reminder-restore-receiver.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-runtime-coordinator.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-create-screen.kt`
- `androidApp/src/main/AndroidManifest.xml`
- `scripts/release-security-check.mjs`
- `scripts/release-security-check.test.mjs`
- `androidApp/src/test/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate-test.kt`
- `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-surface-account-device-test.kt`
- `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-surface-navigation-device-test.kt`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-05-android-evidence.md`

## Implementation Steps
1. Consume terminal01/02/03 contracts and actual widget session quiescence/export evidence. Reject incomplete handoff rather than install no-op host.
2. Implement readiness state in Application. Wipe recovery runs before safe restoration; locked/error/absent identity produce explicit non-sensitive states. Ready only after current key/identity+persisted generation+absent marker+actual runtime installation. External paths never open action-specified account.
3. Implement TaskSurfaceAccess using real current runtime, bounded readiness, immutable snapshots and existing delivery admission. Read validates token/runtime/marker/unlocked state inside lease; setter uses store ID method; request existing sync only after local lease commit. Cancellation propagates.
4. Implement serialized publication: final account/generation/readiness/gate/marker check immediately before actual notification/Glance OS handoff; await bounded completion. Mark surfaces non-Ready under same mutex on account transition, release mutex before draining. Track/cancel/join pending Glance sessions so blank is final after leave. Never hold admission for indefinite provideContent.
5. Add process-owned observer jobs for general Todos and persisted schedule (notification draft-free) with cold replay; wire existing completed background sync/restore/resume/clock events into coalesced refresh. Observe on runtime scope even if Activity destroyed; existing sync worker retains network/cadence policies.
6. Extend composite cancellation for surface admissions/observers/account-tagged boundary work, actual widget sessions and preferences/notification cleanup. Preserve shared sequence freeze→drain→generation+marker→cancel+OS clear→Room close/delete→credentials→marker removal. Clear all installed widget IDs and old intent payloads; programmatic notification cancel invalidates dismissal receipt first. Cleanup failure retains marker and unavailable state.
7. Extend startup pending-wipe recovery without doomed DB open: cancel work/reminders/surface sessions, clear launcher+task notification/preferences, then existing database deletion/credential cleanup/marker removal. Signed-out no-marker startup also blanks widgets. Account replacement clears old content before new Ready; failed leave review restores Ready only while same valid open runtime remains.
8. Migrate main completion path to fresh ID+desired state. Add direct token-bound ACTION_CREATE/ACTION_OPEN routing for onCreate/onNewIntent and queued restoration; revalidate again on consumption/fresh task lookup. Invalid target opens safe current app state, no cross-account selection. Small creation surface reuses existing TodoEditor and commit/default validation, not new task semantics.
9. Mount standalone settings from03 via actual accessible app entry; register ActivityResult RequestPermission in Activity, launch only after deliberate opt-in+rationale API33+, revalidate account on result, update capability onResume. Preserve reminder controls and no-op desktop counterparts unchanged.
10. Register widget metadata/component and private Complete/dismiss/time/worker entry points. Prefer private widget receiver if verified; otherwise narrowly allow exact widget class+APPWIDGET_UPDATE+provider metadata in both source and merged auditor. Reject unrelated exported receivers/providers/services. No new overlay/FGS/exact permissions, no blanket allowed-package exception.
11. Author real runtime/Room device tests for4s restoration timeout, locked/no-session/startup recovery, same-account generation replay, duplicate Complete, delayed OS update vs account leave, wipe interruption/failure and Add/Open/ActivityResult navigation. Extend actual cancel tests and auditor input-policy tests; no source-string/wiring mocks as behavioral proof.
12. Handoff integration report with owned tests and future Android commands. Required native API32/33/34+ launcher/shade/permission process-death smoke is07 gate; no compile-only claim.

## Todo list
- [x] Implement real readiness/access/publication/drain (close-after-drain-timeout behavior remains open).
- [x] Integrate normal/startup wipe clearing and account replacement (Glance cleanup failure propagation remains open with Phase 02 owner).
- [x] Wire fresh completion/navigation/compact create and actual opt-in permission UX.
- [x] Register complete components and narrowly update security auditor.
- [x] Author real integration/security behavior tests; deliver terminal05 evidence.

## Success Criteria
All Android acceptance1–7 and12 production paths are wired, not just leaf code.4s readiness and bounded publication/drain proven, no stale account runtime opening or publication after blank. Logout/recovery failure retains marker; hidden/dead Activity does not prevent refresh. Actual opt-in permission request exists. Auditor remains fail-closed; real OS launcher/shade evidence required during07.

## Conflict Prevention
One owner for every existing Android integration file; no leaf/acceptance parallel root patches. Do not alter01 shared leave/gate API or metadata without original owner. Publish API changes as contract amendment and get corrected leaf handoff, never compatibility shim.

## Risk Assessment
Publication/drain deadlock: never wait drain under publication/store mutex; test both interleavings. Boot cleanup may fail: bounded unavailable + retained marker. Permission result can race account switch: token fence. Glance OS session quiescence is explicit acceptance prerequisite. Background restrictions forbid exact refresh promise.

## Security Considerations
Current identity/key/generation/unlock/marker/admission checked for every external action/publication. Explicit immutable PendingIntent identities are untrusted; no arbitrary runtime fallback. Home-screen exposure intentional only for current account; lock-screen notification remains generic. No old titles in persisted geometry or suppression. Least-exported manifest audited at source and merged APK.

## Next steps
Phase 05 implementation, unit/security checks and handoff are delivered. Phase 07 owns native Android launcher/shade/account acceptance and must route the Cycle 2 drain-timeout finding to the original Phase 05 owner; do not treat logging followed by Room closure as resolved fail-closed behavior.

## Unresolved questions
Cycle 2 warning: if `deliveryGate.closeAndDrain()` times out, runtime closure currently logs and proceeds toward Room closure. Fail-closed correction/decision and native acceptance remain open with the original Phase 05 owner. Glance cleanup failure-swallowing is also open with the original Phase 02 widget owner. These warnings do not negate delivered implementation or test results, but must not be waived by the 8.8/10 review.
