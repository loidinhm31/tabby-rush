# Phase 05 — Linux/Windows grouped sticky mode and deadlines

## Context links
[Parent plan](./evc-cmd-plan.md), [behavior contract](./design-contract.md), [Phase 03 (shared contracts & evaluator)](./phase-03-hierarchy-and-main-ui.md), [surface research](./research/surface-ui.md), [Linux acceptance](../../docs/linux-acceptance.md), [Windows acceptance](../../docs/windows-acceptance.md).

Dependency: Depends on Phase 3 settled shared evaluator, admission, deadline presentation, and committed scheduling. Consumes shared projection without a separate desktop store or protocol fork.

## Overview
Date: 2026-10-19. Priority: P2. Implementation: pending. Review: pending. Provisional effort: 12h (subtotal 48h for surface & rollout phases 4–6). Update the real Compose desktop sticky window for grouped rows and per-task dates on Linux and Windows.

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
- [ ] Migrate sticky model/controller to shared groups and automatic completion.
- [ ] Implement own-deadline metadata with adaptive child rows.
- [ ] Preserve quick-add, identity fencing, tray/recovery and geometry behavior.
- [ ] Exercise native Linux and Windows surfaces and capture evidence.

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
