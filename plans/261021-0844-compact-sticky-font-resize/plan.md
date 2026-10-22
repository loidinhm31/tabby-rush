---
title: "Desktop Sticky Font Resizing and Compact UI"
description: "Add persistent sticky-only text zoom and compact desktop controls without changing task behavior."
status: completed
priority: P2
effort: 12h
branch: feature/parent-child-tasks-deadline-surfaces
tags: [feature, frontend, desktop, compose, accessibility]
created: 2026-10-21
---

# Desktop Sticky Font Resizing and Compact UI

## Outcome and Scope

Sticky header offers A−, current-percentage reset, A+; Ctrl− / Ctrl+ / Ctrl0 mirror them. Zoom persists across restart. All sticky components use compact desktop sizing while retaining recovery controls, semantics, keyboard navigation, hierarchy, deadlines, and account fences.

No application implementation in this planning assignment. No task schema, Room migration, sync, Android, shared-theme, tray/lifecycle redesign, packaging, or new dependency work. Existing native release holds remain open.

## Phases and Ownership

| Phase | Owner role | Status | Effort | Details |
|---|---|---|---|---|
| 01 | Presentation/model implementer | Complete, 100% | 3h | [Preferences and model](./phase-01-preferences-and-model.md) |
| 02 | Desktop UI/integration implementer | Complete, 100% | 5h | [Compact components and font scaling](./phase-02-compact-components-and-font-scaling.md) |
| 03 | Main integration/verification owner | Complete (Automated 100%, Native UI pending) | 4h | [Tests, native verification, documentation](./phase-03-tests-and-verification.md) |

Dependency order: 01 → 02 → 03. One owner per file; phase 02 owns `main.kt`, phase 01 owns preference/model/controller files, phase 03 owns test/docs files. Test authoring may start after contracts freeze; all executable checks run once after edits land, under the main verification owner. Do not concurrently edit shared files.

## Architecture and Frozen Decisions

- [Architecture design entry](../../docs/system-architecture.md#21-proposed-desktop-sticky-typography-and-compact-controls); [research report](../reports/evc-planner-261021-0844-compact-sticky-font-resize.md).
- `StickyWindowPresentation.fontScale: Float = DEFAULT_STICKY_FONT_SCALE`; default 1.0, min 0.75, max 1.50, step 0.125.
- Add optional version-1 property `sticky.window.font_scale`; invalid/missing values recover independently to 1.0. Existing geometry/visibility keys and legacy visibility compatibility unchanged.
- Canonical live scale: `StickyTaskState.fontScale`, initialized from presentation by `DesktopController` → `DesktopStickyTaskController`. Zoom actions only update that field; task clearing preserves it.
- `DesktopController` delegates zoom then persists changed scale with latest geometry/visibility. No second mutable zoom copy; preserve existing best-effort persistence failure behavior.
- Sticky content alone provides `Density(inherited.density, inherited.fontScale * state.fontScale)` through `LocalDensity`; never change `density`, transform geometry, or multiply individual text sizes again.
- Desktop-only sizing: outer 6dp; header title 14sp, actions ≥28dp high; quick add ≥36dp; task controls 24dp, single-line plain rows ≥32dp; gaps 2–6dp. These are minimums, not clipping maximums at larger text.
- Explicit desktop-sticky exception to universal 48dp standards required; shared/Android behavior unchanged. Header wraps before recovery actions become inaccessible.

## Acceptance Gates

1. Old preference files and two-argument presentation constructors retain default zoom; valid zoom round-trips with geometry/visibility intact.
2. Buttons/shortcuts reach both endpoints, reset exactly, and persist immediately after a changed action; hide/quit/reopen cannot overwrite zoom with a default.
3. Actual running sticky is measurably compact at 100%, text zoom is visible at 75%/150%, and main-app/DPI/geometry remain unaffected.
4. No lost draft, focus, group-collapse state, task ordering, automatic parent progress, deadline text, readiness privacy, or account fencing.
5. Tab/Shift-Tab/Enter/Space and Narrator/Orca expose labeled controls, completion/progress/expand state, and recoverable Open App/Quit at narrow sizes.
6. Main owner records Windows and Linux evidence separately; unavailable UI/platform runs are blocked/not run, never inferred from unit tests. Feature acceptance stays conditional until required native scenarios pass.

## Risks and Controls

- Material internal minimums: use sticky-local Material-2 enforcement override, explicit compact button dimensions/padding, and compact `BasicTextField` decoration; measure rendered results.
- Reset paths discard zoom: preserve it in every `StickyTaskState` clear/readiness transition; regression tests cover account changes and observation teardown.
- Narrow/high-scale clipping: FlowRow header, text-aware minimum heights, weighted scrolling task area; test 280×240dp and inherited 200% text scale.
- Persistence races: serialize presentation mutation/save snapshots in `DesktopController`; zoom is independent of monitor DPI and account content.
- Verification gaps: no current desktop Compose UI test harness. Use existing JVM suites plus required real native observation; no screenshots fabricated from design baselines.

## Documentation and Handoff

Phase 03 updates current code/design standards, platform guides, roadmap, changelog, and this proposed architecture entry only to evidence-supported status. Main owner reviews implementation once; planner runs no builds/tests/UI acceptance. See phase 03 for exact commands, matrix, and recording requirements.

## Unresolved Questions

None blocking design. Actual native Windows/Linux access and accessibility acceptance remain execution prerequisites, not accepted results.
