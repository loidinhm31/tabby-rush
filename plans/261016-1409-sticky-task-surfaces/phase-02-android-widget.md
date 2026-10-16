# Phase02 — Android Glance widget leaf

## Context links
[Overview](plan.md) · [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md) · [Android research](research/researcher-01-android-surfaces.md) · [analysis](reports/01-codebase-analysis.md) · [contracts](architecture-contracts.md) · [ownership](file-ownership.md#phase02--android-widget-leaf) · [acceptance](acceptance-matrix.md) · [dependencies](dependency-matrix.md).

## Parallelization Info
Group B; **hard dependency01** complete/frozen Glance/access/section contracts and compatible build setup. Can overlap03/04; no shared edit paths. Real Application host and manifest registration arrive in05, so native installed-widget acceptance waits05/07; do not invent a fake host to claim independence.

## Overview
- Date: 2026-10-16. Priority: P2. Effort: **16h**.
- Implementation status: **pending / 0%**. Review status: pending.
- Implement complete Glance renderer/receiver/actions/all-instance updater with bounded cold account access and quiescent clearing.

## Key Insights
Glance uses RemoteViews, not ordinary shared Compose UI. Launcher owns widget IDs and bounds; no Activity/ViewModel dependency or per-widget account configuration. OS-retained content and update sessions require lifecycle clearing, not just setting an in-memory list empty. `provideContent` can live beyond a callback; admission must cover actual bounded RemoteViews update handoff, never permanent composition lifetime.

## Requirements
1. One scrollable Glance list: Incomplete header/rows then Completed; stable ordering; completion both ways, Add and Open.
2. Responsive supported small/large sizes derived from launcher bounds; accessibility labels, adequate tap targets and readable truncation; scroll reaches both sections.
3. Initial/loading/signed-out/locked/unavailable states reveal no old task title. Current active account only across every installed ID.
4. Immutable desired-state action identity includes token/task/operation; stale generation rejects; no direct DAO/store opening or copied Todo save.
5. Complete production implementation against01 host interfaces; real registration and compact create route are05 responsibility.

## Architecture
`DoggoTaskWidgetReceiver` resolves Application's `TaskSurfaceHostOwner` → bounded access readiness → read current token snapshot → Glance-native projection. Updater enumerates `GlanceAppWidgetManager` IDs, coalesces updates, performs final token publication fencing, and exposes awaitable clear/quiescence to05. Callback desired boolean → host.access.setCompleted; request existing refresh/sync outside mutation lease. Add/Open use explicit Activity intents, token rechecked by05 on consumption.

## Related code files
**Exclusive create paths:** phase02 manifest table lists task-widget.kt, receiver/actions/updater, provider-info/initial-layout/widget-string resources, model/device tests and phase02 evidence report. No existing source/build/manifest changes.
**Read-only:** phase01 task-surface contracts, Android host, selected Glance dependencies; existing main Activity/editor/navigation patterns and shared task UI for behavior only. Regular Compose is not reused inside Glance.

## File Ownership
Own exactly02 paths, including widget tests/resources. Do not change01 host contract,03 notification/preferences files or05 Application/manifest/auditor. New resource files have widget-specific names; no competing strings.xml edits. Proposed unlisted file requires manifest amendment before work.

Explicit owned paths (repository-relative; all proposed creates):
- `androidApp/src/main/kotlin/dev/doggo/android/task-widget.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-widget-receiver.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-widget-actions.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-widget-updater.kt`
- `androidApp/src/main/res/xml/task-widget-info.xml`
- `androidApp/src/main/res/layout/task-widget-initial.xml`
- `androidApp/src/main/res/values/task-widget-strings.xml`
- `androidApp/src/test/kotlin/dev/doggo/android/task-widget-model-test.kt`
- `androidApp/src/androidTest/kotlin/dev/doggo/android/task-widget-device-test.kt`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-02-widget-evidence.md`

## Implementation Steps
1. Consume terminal01 contract/version evidence. Check selected Glance list/action/size/update/session APIs; use only supported stable APIs.
2. Implement Glance widget with responsive size modes using launcher-provided bounds, stable task IDs, section headers and desired-state controls. Render full local list from shared partition, never main filter/reminder predicate.
3. Add non-sensitive initial RemoteViews layout/provider metadata and accessible widget strings. No widget account configuration or overlay permission.
4. Implement callbacks and explicit Add/Open intents with distinct operation identity, account generation and task ID. No title/credential payload; stale/locked/timeouts give harmless refresh/current app route, not alternate runtime creation.
5. Implement all-instance updater and clear path. Enumerate installed launcher IDs after host restore as well as normal update. Prepare snapshot, final-check token and track actual update completion/cancellation; prevent old Glance session publishing after leave's blanking update.
6. Keep readiness wait bounded4s and OS update/session drain bounded; cancellation propagates. If no safely ready account, replace previous content with empty/signed-out projection and remove old callbacks. No infinite authenticated Flow wait.
7. Author production model/action boundary tests and instrumented actual Glance host/callback/all-ID tests. Device runtime requires05 real host; tests may be authored/compiled now, but never substitute a runtime fake for integrated account proof.
8. Document supported receiver configuration for05: prefer non-exported system-driven widget receiver if platform host permits; if real launcher requires exported, provide exact class/action/metadata rationale for narrow auditor exception. Do not broaden exported access here.
9. Handoff updater clear/refresh/quiescence behavior and tests to05 with all pending native scenarios, including delayed update vs leave, launcher restore and multiple IDs.

## Todo list
- [ ] Implement real responsive Glance sections/controls and provider resources.
- [ ] Implement token-bound callbacks and Add/Open intents.
- [ ] Implement all-ID refresh/clear with bounded session quiescence.
- [ ] Author model and native widget tests; record real integration prerequisites.
- [ ] Deliver leaf evidence/API handoff to05.

## Success Criteria
Acceptance1,2,3,5,7,12 widget requirements implemented with no Activity dependence, stale DB opening, duplicate sections or direct DAO writes. Future native checks show real scroll/toggle/Add/Open at supported sizes and all IDs clearing. Until05/07 exercise actual launcher/account races, leaf report explicitly marks those pending; authored tests are not native proof.

## Conflict Prevention
Only02 paths;01 owns build dependencies,05 owns registration and all existing navigation/runtime/wipe files. Agree exact exported updater signatures already in contracts; changes go through01/05 before edits, not parallel root patches. Return integration defects to proper owner.

## Risk Assessment
Glance session timing, launcher sizing/restore and RemoteViews scroll quirks require actual host proof. Cold restore may time out; show safe unavailable and reconcile at next supported trigger, not polling. Huge lists still follow supported lazy Glance behavior; no arbitrary product truncation.

## Security Considerations
Home screen intentionally exposes titles; initial/locked/signed-out/wipe state must not. Immutable action tokens are untrusted and validated at runtime. No current-account key is accepted as permission to open a runtime; final publication fence prevents account-switch snapshot leaks. Exported receiver policy must be least-privilege and audited in05.

## Next steps
Terminal02 plus terminal03 unblocks05. Main integrator later runs Android compile/device tests and07 actual launcher acceptance. No runtime source implementation authorized by this plan.

## Unresolved questions
Exact stable Glance update-session cancellation/completion behavior and receiver export requirements need library/device proof; no product question.
