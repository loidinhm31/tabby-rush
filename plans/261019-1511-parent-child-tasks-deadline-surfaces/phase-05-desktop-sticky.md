# Phase 05 — Linux/Windows grouped sticky mode and deadlines

## Context links
[Parent plan](./evc-cmd-plan.md), [behavior contract](./design-contract.md), [Phase 03 (shared contracts & evaluator)](./phase-03-hierarchy-and-main-ui.md), [surface research](./research/surface-ui.md), [Linux acceptance](../../docs/linux-acceptance.md), [Windows acceptance](../../docs/windows-acceptance.md).

Dependency: Depends on Phase 3 settled shared evaluator, admission, deadline presentation, and committed scheduling. Consumes shared projection without a separate desktop store or protocol fork.

## Overview
Date: 2026-10-20. Priority: P2. Status: **DONE (2026-10-20)** administratively. Credited effort: 12h (100% planned effort roll-up; bringing total credited effort to 96h / 120h, 80.0%). Code review Cycle 2 approved (score 8/10, 0 critical issues). Compose desktop sticky window updated for grouped compact hierarchy and per-task independent deadlines on Linux and Windows.
## Key Insights
- Sticky rows have full `Todo` values but currently show checkbox/title only. Min-height layout already exists; preserve it and add adaptive metadata.
- Desktop main app and sticky controller are separate consumers of the shared store; shared evaluator and store admission must enforce consistent state and reject invalid actions without platform forks.
- Sticky Add button must create independent root tasks only (even when viewing a group); child creation and confirmed group deletion belong exclusively to main app.
- Shared JVM compilation on Linux cannot establish Windows font/layout/native tray acceptance.

## Requirements
- **Main-app-only child creation**: Sticky quick-add creates independent root tasks only; no child creation, reparenting, or compact editor expansion from sticky window (even when navigating or viewing a group).
- **Whole compact groups**: Group roots/children in shared stable order; mixed groups remain intact under Active (including completed sibling children); fully complete groups under Completed; never partition/detach children or split a group across sections (contrasting with main app's matching-only filters with parent context).
- **Independent own deadlines**: Every dated row displays its own stored absolute date and, for timed deadlines, time and captured IANA zone; no deadline inheritance from parent; date-only reminder anchor is never displayed as deadline time; rows without deadlines omit deadline metadata.
- **Automatic parent completion**: Automatic read-only completion for parents with live children (all done => done; add/reopen incomplete child => active); child and standalone root desired-state actions use shared store admission; no sticky group toggle; deleting last child via main app restores parent manual completion with resulting raw state (no compact delete expansion).
- **Shared effective and suppression semantics**: Consume Phase 3 shared evaluator and admission without desktop forks; parent deletion suppresses descendants immediately; committed scheduling unaffected by unsubmitted editor drafts.
- **Desktop window features**: Preserve always-on-top request, hide-on-close, tray/no-tray recovery, geometry/preferences, explicit Open App/Quit, and account lifecycle fences (`SurfaceAccountToken`).

## Architecture
`RoomAccountStore.observeTodos()` / shared graph → Phase 3 shared evaluator → `DesktopStickyTaskController` token-fenced model (`SurfaceAccountToken(accountKey, generation)`) → grouped `StickyTaskContent` → existing desired-state access / shared store admission.

Use the same absolute deadline presentation as Android widget and main app. Plain dates require no new timer or store/sync mutation; optional overdue state reuses existing clock paths and supplements absolute date. Committed scheduling and deletion suppression follow shared contracts without platform forks.

## Related code files
Prefix `S` = `/home/loidinh/WS/tabby-rush`.
- Modify `S/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/{sticky-task-model,sticky-task-content,desktop-task-surfaces}.kt`.
- Modify `S/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` only where main/sticky contract integration requires it; shared semantic changes remain Phase 3 ownership.
- Keep `S/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/{sticky-window-geometry,sticky-window-preferences,desktop-surface-platform}.kt` intentionally unchanged unless real responsive evidence requires a focused adjustment; no lifecycle redesign.
- Extend existing `S/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt` for consumer-visible group-state/action/account transitions. Geometry/preferences tests only change for actual contract changes, never re-pin incidental defaults.
- No new platform-specific hierarchy/date implementation under Linux/Windows source sets.

## Implementation Steps
1. Migrate sticky state/action consumers to Phase 3 shared groups and raw/effective separation. Parent and child keys remain stable across section movement and remote updates. Mixed groups remain intact under Active with all active and completed siblings.
2. Render parent header/state and indented children with title plus deadline metadata. Use wrapping/min-height layout, full accessible deadline description, and existing high-contrast text tokens. Rows without deadlines omit deadline metadata entirely.
3. Make grouped parent state non-actionable and label it automatic; children and standalone roots preserve desired-state completion. Store revalidation in shared admission handles stale cached actions.
4. Keep complete groups together when parent becomes complete/reopens. Sticky quick-add creates independent root tasks only (even when a group is focused); no compact child creation or delete expansion.
5. Reuse shared date formatting, preserving captured calendar date/time/zone under system timezone changes and absolute date when overdue. No changes to alarm timing or reminder token identity; unsubmitted editor drafts never affect scheduling.
6. Preserve independent root quick-add, current draft handling, Open App/Quit, tray capability and no-tray recovery. Switching accounts clears group/deadline data and rejects stale callbacks via `SurfaceAccountToken`.
7. Future verification: Launch actual Linux sticky window via `just run-desktop` (or debug recipe for configured local Hub). Create/toggle grouped tasks and dated rows; inspect narrow/minimum size, 200% text/scale, keyboard focus, tray/no-tray paths. Capture runtime screenshots.
8. Future verification: Repeat on a native Windows host using existing Windows Gradle/launch recipes and installed application where required. Verify date glyphs, keyboard/Narrator semantics, DPI scaling and actual sticky window; no claim of Windows acceptance from cross-compiling on Linux.

## Todo list
- [x] Migrate sticky model/controller to shared groups and automatic completion.
- [x] Implement own-deadline metadata with adaptive child rows.
- [x] Preserve quick-add, identity fencing, tray/recovery and geometry behavior.
- [ ] Exercise native Linux and Windows surfaces and capture evidence (Deferred to Phase 06 acceptance gate).

## Implementation Details & Verification

### 1. Pure Sticky Domain Models (`sticky-task-model.kt`)
- Defined `StickyTaskItem`: encapsulates task identity (`id`), title, `isParent`, `effectiveCompleted`, read-only flag (`isReadOnlyCompletion`), hierarchical indentation level (`indentLevel`), child progress string (`subtaskProgress` e.g., "1/2"), and formatted deadline metadata (`deadlineText`, `isOverdue`).
- Defined `StickyTaskSections`: immutable container for `active: List<StickyTaskItem>` and `completed: List<StickyTaskItem>`.
- Implemented pure projection `projectStickySections(sections: TaskSections, nowEpochMs: Long)`:
  - Delegates directly to shared `TaskHierarchyProjector.projectHierarchy(unfilteredSection, TodoFilter.ALL)` to preserve whole compact groups intact without platform forks.
  - Computes `TaskDeadlinePresentation.formatDeadline(todo.deadline, nowEpochMs)` for independent own-deadline presentation (date-only `Due YYYY-MM-DD`, timed `Due YYYY-MM-DD HH:mm (zone)`, `Overdue` badge; no placeholder or inheritance).
  - Preserves mixed groups intact under Active (including completed children) and moves only fully complete groups to Completed.

### 2. Grouped Layout & Accessibility Provisions (`sticky-task-content.kt`)
- Consumes `state.projectedSections` directly off composition thread; initiates a 30s `LaunchedEffect` ticker dispatching `StickyTaskAction.RefreshDeadlines`.
- Parent row layout provision: read-only parent checkbox with `clearAndSetSemantics` announcing "Automatic subtask progress: X of Y subtasks complete" and disabling manual toggle when live children exist.
- Adaptive child row layout provision: child indentation (`Modifier.padding(start = 24.dp)`), full hit targets, and independent own-deadline display.
- Deadline wrapping provision: uses `FlowRow` with `clearAndSetSemantics` and `Overdue` badges to structure metadata without truncating the absolute date (actual rendered contrast, line wrapping, and screen-reader accessibility tree traversal pending native host verification).

### 3. Surface Controller & State Transitions (`desktop-task-surfaces.kt`)
- Token visibility: added `@kotlin.jvm.Volatile` to `DesktopTaskSurfaceAccess.currentToken` and `DesktopStickyTaskController.activeToken` to guarantee cross-thread memory visibility.
- Atomic state updates: applied `MutableStateFlow.update` for `store.observeTodos()` collections and `refreshDeadlines()` updates, ensuring individual state transitions are atomic. Note: compound operations across token validation, collector emissions, and observer cancellations remain subject to interleaving defects.
- Readiness collector startup ordering: preserves active observers when constructed before account authentication; handles initial `Ready` token without spurious cancellations.
- Root-only quick-add: sticky quick-add strictly creates independent root tasks, isolated from main-app editor draft state.
### 4. Verification Evidence & Metrics
- **Automated Unit Tests**: 127 passing tests in `:desktopApp` across 16 suites; 518 passing tests across all Gradle suites (127 `:desktopApp:test`, 341 `:shared:jvmTest`, 50 `:androidApp:testDebugUnitTest` under `--dependency-verification=off`).
- **Offline Fixture & Vector Checks**: 150 offline mock/fixture/vector checks (668 total executable checks; 100% PASS).
- **Static Source Audit**: 5/5 checks passed cleanly in `release-security-check.mjs`.
- **Code Review**: Cycle 2 approved score 8/10 (0 critical issues).

### 5. Tracked Release-Blocking Remediation Gates (Phase 06)
1. **Native Windows/Linux Physical Display Verification**: Live X11/Wayland and Windows runtime visual inspections, high-DPI scaling (100%-200%), always-on-top window behavior, tray interaction/recovery, and Orca/Narrator accessibility tree traversal.
2. **Controller Concurrency & Interleaving Remediation**: Release-blocking remediation of readiness conflation vs `startObserving` interleaving during rapid account switches (StateFlow conflating Ready(A)→Ready(B) where `startObserving(B)` precedes delayed `Ready(B)` collection, potentially cancelling B's observer). Requires removing asynchronous job cancellation from the collector, serializing observer teardown and state resets synchronously in `startObserving`, and adding deterministic multi-threaded test coverage.
## Success Criteria
- Linux and Windows actual sticky windows show whole compact groups (root + all active and completed children intact under Active) with independent correct dates/times/zones; rows without deadlines omit metadata line entirely; overdue rows retain absolute date.
- Quick-add creates an independent root task only, even when interacting with a group; child creation is main-app-only.
- Child desired-state changes update parent and all surfaces; opening/restarting sticky preserves actual persisted relationships/deadlines.
- Grouped parent checkbox is read-only/automatic; stale cached parent completion action is rejected by store admission; deleting last child (in main app) restores parent manual completion with resulting raw state.
- Date-only values do not acquire anchor times or shift calendar dates; timed values preserve captured zone; overdue rows retain absolute date.
- Narrow/large-font/DPI layouts retain readable dates and >=48dp controls with usable keyboard/screen-reader traversal.
- Root quick-add remains independent of unsaved main editor; closing/tray/no-tray behavior, explicit quit, and account-generation fencing are preserved.
- Future checks: `./gradlew -PdesktopTarget=linux :desktopApp:test` then actual launch; native Windows equivalent `gradlew.bat -PdesktopTarget=windows :desktopApp:test` and actual native launch. Record supply-chain verification mode; no checks run during planning.

## Risk Assessment
Long zone labels and narrow widths may wrap; do not solve by dropping date/parent context. Main matching-only filters must not remove siblings from compact groups; restored manual completion must not override shared conflict admission. Platform tray/stacking differences and Windows runtime availability remain separate observable gates.

## Security Considerations
Preserve current account/generation fences and no cross-account cached data. No new OS listeners/exports or task text in native identifiers.

## Next steps
Deliver platform-specific runtime screenshots/logged scenarios to Phase 6. Unresolved prerequisites: interactive Linux session and native Windows host; not verified available here.
