# Phase04 — desktop compact presentation, geometry and preferences leaf

## Context links
[Overview](plan.md) · [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md) · [desktop research](research/researcher-02-desktop-surfaces.md) · [analysis](reports/01-codebase-analysis.md) · [contracts](architecture-contracts.md) · [ownership](file-ownership.md#phase04--desktop-compact-presentation-leaf) · [acceptance](acceptance-matrix.md) · [dependencies](dependency-matrix.md).

## Parallelization Info
Group B; **hard dependency01** completed compact task/action/section signatures. Can overlap02/03 with no shared edits.06 owns actual Window/controller/Tray wiring; runtime presentation acceptance waits06/07. Geometry/preferences/UI are complete production helpers, not a second runtime or preview-only scaffold.

## Overview
- Date: 2026-10-16. Priority: P2. Effort: **12h**.
- Implementation status: **delivered / 100%**. Review status: **Cycle 2 8.8/10 Conditional Pass; approved for implementation handoff, 0 critical issues**.
- Delivered compact task UI, independent quick-add contract, atomic presentation preferences and monitor-safe geometry helpers with tests. Native display acceptance remains Phase 07/08.

## Key Insights
Main `TodoUiState.items` is filtered and its editor is shared mutable controller state; compact UI must consume separate authoritative sections/buffer. AWT display bounds can be negative and per-monitor scaled; usable work areas exclude insets. Valid saved bounds on yesterday's monitor may strand today's window.

## Requirements
1. `StickyTaskContent(state, onAction)` uses frozen compact contract; Incomplete then Completed in one scrollable list, stable order, desired completion both ways, inline title quick-add.
2. Do not dispatch main-editor OpenCreate/ChangeDraftTitle/SubmitEditor. Compact raw text/error/submitting state is controller-owned and independent; UI communicates compact actions only.
3. Explicit Open app/Show-other/Quit recovery controls remain available for no-tray integration; no hidden-only configuration route.
4. Atomic presentation-only file outside Room/sync, no account/task/credential content; restore sticky visible/size/position on **manual** launch, default hidden, no auto-start.
5. Clamp real current monitor work areas with negative origins/DPI/insets/hotplug and minimum bounds per contracts; no arbitrary primary-screen zero clamp.

## Architecture
Stateless compact composable → frozen `StickyTaskAction`;06 controls mutation/validation/lifetime. Desktop-local preferences serializes versioned logical geometry and scale metadata; load→geometry conversion→intersection-display choice→work-area clamp→WindowState integration in06. Default360×520dp, minimum280×240dp unless work area smaller. Entire title bar plus>=48dp content reachable; corrupt/missing preferences use safe defaults. Geometry algorithms accept concrete monitor descriptions; real AWT enumeration is06 platform seam.

## Related code files
**Exclusive creates:** full04 ownership table: sticky-task-content.kt, sticky-window-geometry.kt, sticky-window-preferences.kt, geometry/preferences tests and evidence report.
**Read-only:**01 desktop sticky-task-model.kt and shared section/models/title validation; shared list/theme/design guidance; existing desktop main.kt/build/profile-lock behavior. No production controller or build edit.

## File Ownership
Exactly04 paths including tests.06 alone owns main.kt, platform capability enumeration, Tray and controller tests.08 Linux adapter consumes same geometry helpers without editing04 files. New extra path requires manifest amendment first; final acceptance returns helper defects to04.

Explicit owned paths (repository-relative; all proposed creates):
- `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-task-content.kt`
- `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-window-geometry.kt`
- `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-window-preferences.kt`
- `desktopApp/src/test/kotlin/dev/doggo/desktop/sticky-window-geometry-test.kt`
- `desktopApp/src/test/kotlin/dev/doggo/desktop/sticky-window-preferences-test.kt`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-04-compact-evidence.md`

## Implementation Steps
1. Consume terminal01 value/action signatures and section ordering. Implement compact composable directly against these, with labels/tap targets/keyboard navigation, pending/error state and enabled actions only when safely ready.
2. Add inline quick-add raw-title field and Submit action; never create a main EditorState locally or mutate main TodoAction flow.06 handles shared normalized task construction and commit before clearing buffer.
3. Expose explicit Open app and Quit in compact shell content so06 can retain visible recovery if native tray fails. Main shell's Show sticky control is06 responsibility.
4. Define exact local `sticky-window.properties` format: version, visible flag, logical x/y/width/height, display scale conversion metadata only. Parse defensively, finite/positive bounds only, atomic temp+replace using current repository file patterns; use temporary-directory tests with actual writes/reopen.
5. Implement reachability helper: convert saved/current coordinates consistently, preserve negative origin, choose maximum intersection else primary, apply screen insets/usable bounds, constrain dimensions to available work area, restore title bar and content margin. Do not expand tiny screen beyond its usable bounds.
6. Define capture policy for06: ignore minimized/invalid transient bounds; debounce move/resize, flush stable geometry on hide/Quit, clamp again on Show and monitor/DPI change. Return ready bounds, not unsafe unchecked saved values.
7. Author production geometry tests for multi-monitor left/top negative origins, removed monitor, unequal scales, DPI change, taskbar insets, zero/corrupt/oversized prefs and tiny work area. No copied algorithm in tests; real preference files prove restart/atomic replacement.
8. Handoff UI/geometry/preferences APIs and leaf evidence to06. No preview/mock window counts as sticky/native acceptance; actual Windows stacking/restore belongs integration.

## Todo list
- [x] Implement compact sections/inline input/recovery controls against frozen actions.
- [x] Implement real versioned atomic presentation preferences.
- [x] Implement work-area/DPI/negative-coordinate restoration helpers.
- [x] Author production geometry and real-file persistence tests.
- [x] Deliver complete leaf handoff to06.

## Success Criteria
Acceptance1/2 desktop counterparts and8/10/12 presentation foundations complete. Compact UI never consumes filtered main items or shared main-editor input. Geometry tests cover every specified edge; actual Windows restore/stacking still pending06/07. Preferences contain presentation values only; no schema/sync or autostart registration.

## Conflict Prevention
No main.kt/Tray/lifecycle/build/platform source changes. Shared contract changes go01 before implementation; geometry/helper changes remain04 even when found later by08.06 consumes completed leaf, never edits same tests.

## Risk Assessment
Logical/physical coordinate mismatch can pass pure geometry tests but fail native DPI;07 must observe actual monitors. Tiny displays need adaptive minimum. UI input lifecycle can be lost if composed state replaces controller buffer; enforce supplied state only.

## Security Considerations
No task titles/account data in geometry file, minimal private local presentation storage. Clear stale task/input through06 account transition; unavailable UI never offers mutation against old token. No auto-start/IPC/new runtime.

## Next steps
Phase 04 is delivered and its implementation tests are reported passing. Phase 07 still needs actual Windows monitor/stacking acceptance; Linux-specific acceptance remains held for Phase 08's target/session.

## Unresolved questions
No product decisions left. Runtime coordinate conversion and focus behavior on actual Windows/Linux displays require native proof.
