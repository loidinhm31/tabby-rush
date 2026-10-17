# Phase06 — desktop controller, singleton windows, tray and lifecycle integration

## Context links
[Overview](plan.md) · [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md) · [analysis](reports/01-codebase-analysis.md) · [desktop research](research/researcher-02-desktop-surfaces.md) · [contracts](architecture-contracts.md) · [ownership](file-ownership.md#phase06--desktop-integration-single-owner) · [acceptance](acceptance-matrix.md) · [dependencies](dependency-matrix.md).

## Parallelization Info
Group C; **hard dependencies01+04**, completed real contracts/helpers. Can overlap05 and unrelated remaining Android leaf after own04 handoff. Sole owner existing desktop main.kt/controller/runtime/tray/lifecycle;07 waits05+06. Linux-only preparation can follow06 but08 final native verdict waits07 and Linux session.

## Overview
- Date: 2026-10-16. Priority: P2. Effort: **16h**.
- Implementation status: **delivered / 100%**. Review status: **Cycle 2 8.8/10 Conditional Pass; approved for implementation handoff, 0 critical issues**.
- Delivered one desktop controller/runtime, singleton main/sticky windows, tray and no-tray recovery, geometry/preferences, desired-state actions, and explicit Quit teardown with integration tests. Windows/Linux native acceptance remains pending.

## Key Insights
Existing main Close disposes controller/exits; filtered uiState cannot feed sticky. SingleInstanceGuard already refuses lock contention rather than forwarding activation. Pinned Compose1.7.3 Window supports visible/alwaysOnTop; native tray/focus and exact API compatibility still require compile/native proof. Process-scoped reminder lifetime changes with close-to-hide and must be documented/tested, not expanded into Windows toast work.

## Requirements
1. One process/profile lock/controller/runtime, one main, one sticky, at most one usable tray. Repeated Show reuses retained Window; alwaysOnTop=true for sticky only.
2. Main/sticky close hides requested UI only, retains sync/reminders/database; explicit Quit alone drains/disposes/exits/releases lock, idempotently.
3. No usable tray (unsupported/init failure/lost support) => keep or restore visible window with explicit Open/Show-other/Quit controls; cannot strand process invisible.
4. Separate unfiltered task sections and independent compact buffer; same title validation/default task construction/local-first sync; compact submit never edits/closes main editor.
5. Current account/generation admission and state clearing, mutation by ID desired state, observer lifetime independent of window visibility; geometry restore via04 actual monitor work areas/DPI.
6. Complete generic desktop implementation plus Linux provider seam; no autostart/activation IPC/new runtime, no Windows native toast bridge scope.

## Architecture
Single existing application creates retained Window visibility states and one controller. Controller collects store.observeTodos at runtime scope, publishes unfiltered StickyTaskState separately from filtered TodoUiState, uses real TaskSurfaceAccess token checks and teardown drain. New desktop-task-surfaces shell owns singleton tray/window intent state; platform seam enumerates AWT usable displays and tray capability with complete default implementation, optional Linux-only ServiceLoader provider.04 supplies UI/geometry/preferences. Main controller stays in main.kt; no speculative extraction paths.

## Related code files
**Exclusive:** exhaustive06 table: main.kt, proposed desktop-task-surfaces.kt/platform seam/tray PNG, composite-desktop-leave-cancel-gate.kt, existing lifecycle/reminder-lifetime/composite tests, proposed real controller surfaces test and evidence report.
**Read-only:**01 compact/shared contracts/store;04 content/geometry/preferences/tests; existing SingleInstanceGuard, DesktopSyncScheduler/ReminderScheduler/platform adapters and build source-set/runDebug definitions. No build metadata or Windows-native reminder changes.

## File Ownership
Exactly06 paths including controller tests and generic seam.04 retains helper/input view tests;01 metadata/shared store;08 only Linux source/provider/tests/evidence/docs. Late common bug returns to06 rather than Linux claiming main.kt.07 never edits runtime/test files.

Explicit owned paths (repository-relative; actions/reasons in central manifest):
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt`
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt`
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-surface-platform.kt`
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate.kt`
- `desktopApp/src/main/resources/task-surface-tray.png`
- `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-lifecycle-test.kt`
- `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-controller-lifecycle-test.kt`
- `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate-test.kt`
- `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-06-desktop-evidence.md`

## Implementation Steps
1. Consume terminal01/04 contracts and leaf evidence. Re-read current controller creation/toggle/disposal/auth paths; preserve runtime/profile lock acquisition before vault/DB.
2. Add real desktop access implementation with current token/readiness, operation admission/drain and serialized in-memory publication clearing. Collect unfiltered store observer for both views without UI lifetime dependence; existing main filter affects main only. Clear allTodos/sections/compact buffer on account transition before publishing replacement.
3. Migrate main and sticky completion to store ID+desired setter. Use token on compact callbacks; stale event no-op and cannot open account runtime. Existing immediate sync follows successful local commit outside mutation lease; no cadence change.
4. Extract a private controller creation helper used by existing main-create submission and compact title-only create. Reuse TitlePolicy, normalized title, UUID/timestamps/incomplete/no-deadline/default semantics; no editor action replay. Preserve full main EditorState/default reminder semantics. Compact invalid submit keeps text/error; success clears compact buffer only, never main draft.
5. Implement concrete AWT platform seam in declared file: SystemTray support plus initialization outcome, actual GraphicsConfiguration transforms/bounds/insets, default complete generic capability behavior and optional one Linux ServiceLoader provider. No Linux provider needed to make Windows/generic runtime functional.
6. Create exactly one retained main Window and one sticky Window; visibility booleans, sticky alwaysOnTop=true, explicit focus/raise on Show using pinned supported API. Initial main visible; sticky visibility/bounds from04 prefs after clamp; repeated Show cannot allocate another shell/runtime. Bundle native tray image.
7. Create one tray with Open app, Show/Hide sticky, Quit. Intercept both Close callbacks to hide only respective UI and flush preferences. Without usable tray refuse last-visible hide and retain recovery/quit UI; capability loss while hidden restores main. Do not treat Tray composable presence as successful native registration.
8. Integrate current display work-area enumeration with04 conversion/clamp at restore/Show and monitor/DPI changes. Observe/debounce stable WindowState bounds; flush on hide/Quit. Preserve negative origins and sticky-hidden preference across manual launch, no startup registration.
9. Unify all Quit controls through one idempotent suspend teardown: block admissions, save geometry, cancel/join task/schedule/clock observers, drain existing sync/reminder work and runtime, close Room, cancel scope, release profile lock, exit. Do not exit/release lock as graceful success on unfinished drain; retain visible error/retry. Ordinary hide must not call dispose.
10. Extend account-leave composite cancellation to stop surface observer and clear compact/current state before runtime close; existing shared generation/marker order untouched. Leave rejection restores valid current state only if gate/runtime still safely open.
11. Author real controller/SQLite tests for compact/main draft isolation, failed/successful submit, stale generation, hidden mutation/sync, filter independence, close-vs-Quit, idempotent teardown, singleton intent state, last-visible no-tray fallback and profile lock reacquire. Extend existing reminder lifecycle tests for process-scoped lifetime; no source-string or fake controller acceptance.
12. Handoff Windows owner smoke and exact future commands (`gradlew.bat :desktopApp:runDebug -PdesktopTarget=windows`), pending native geometry/stacking/tray scenarios and generic seam to07/08. Source host guard remains unchanged.

## Todo list
- [x] Integrate authoritative sections/desired completion and independent compact creation.
- [x] Implement singleton Window/Tray visibility and no-tray recovery.
- [x] Integrate real display geometry/preferences and Linux provider seam.
- [x] Move all teardown to explicit idempotent Quit and account-leave drains.
- [x] Author real controller/lifetime tests and deliver terminal06 handoff.

## Success Criteria
Production paths satisfy8–10 plus desktop2/3/7/12; hidden main retains authoritative state and schedulers. Native Windows evidence proves singleton tray/window, ordinary-app stacking, Close restore, manual geometry and Quit/profile release; until07 exercised, no runtime certification. Compact submit cannot overwrite/cancel main editor, geometry no account text, no autostart/activation expansion.

## Conflict Prevention
Only06 edits main.kt and desktop roots, even if after07/08 defect report. Generic helper amendments return04, store/metadata01; do not patch sibling files or create duplicate model adapters. Linux seam is complete before08 starts, not a placeholder library fallback.

## Risk Assessment
Nested runBlocking/disposal under UI thread can deadlock: one suspend Quit path and bounded drain, no lock held during joins. Native tray creation can fail despite isSupported; actual success state controls last-visible policy. Per-monitor DPI and OS stacking need native proof. Existing toast stub remains unrelated unchanged hold.

## Security Considerations
One profile lock protects runtime; no second store/companion. Current-token callback validation and immediate state blank on leave prevent cross-account display/actions. Geometry file stores presentation only; compact raw text clears on account change. Runtime lifetime expands while hidden intentionally; explicit Quit drains secrets/database before process exit.

## Next steps
Phase 06 and its integration tests are delivered; Windows tray/window/geometry acceptance remains Phase 07. Linux final verdict remains Phase 08 and requires the actual target/session.

## Unresolved questions
No product blocker. Native Tray initialization/focus behavior under pinned Compose and actual monitor/session proof remain implementation gates; lock contention intentionally stays exit-only.
