# Phase 02 — Compact Components and Font Scaling

## Context Links

- [Overview](./plan.md) · [Phase 01 contract](./phase-01-preferences-and-model.md) · [Verification matrix](./phase-03-tests-and-verification.md)
- [Architecture proposal](../../docs/system-architecture.md#21-proposed-desktop-sticky-typography-and-compact-controls)
- [Existing sticky components](../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt)
- [Window/controller integration](../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt)
- [Design standards](../../docs/design-guidelines.md#73-desktop-sticky-companion-window)
- Official references: [Density](https://developer.android.com/reference/kotlin/androidx/compose/ui/unit/Density), [Material minimum interactive size](https://developer.android.com/reference/kotlin/androidx/compose/material/minimumInteractiveComponentSize.modifier), [BasicTextField](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/BasicTextField.composable).

## Overview

Priority P2 · Status complete · Effort 5h · Owner desktop UI/integration implementer.

Depends on phase 01 field/action names. Own only `main.kt` and `sticky-task-content.kt`. No verification mid-flight; main integration owner runs phase 03 once edits land.

## Key Insights

- Current sticky uses Material **2** (`androidx.compose.material`), not Material 3. Compose Multiplatform is pinned to 1.7.3. Do not copy current Material-3-only APIs or upgrade dependencies to compact this surface.
- Root padding is 12dp; persistent header buttons, quick-add controls, row minimums, expanders and checkboxes explicitly use shared 48dp token. Changing only outer padding will not solve density.
- Material components also impose internal minimums and content padding; `defaultMinSize(32.dp)` cannot reliably shrink a Material text field or remove 48dp interaction layout.
- Current `OutlinedTextField` internal padding needs replacement with a compact field using existing foundation APIs. Retain real editable-text semantics, IME, selection and keyboard behavior.
- Main and sticky are separate `Window` trees. A provider around sticky content can scale all sticky text without changing other windows, AWT monitor scale, or window bounds.
- Header width is already constrained; adding three zoom controls to the current single Row without wrapping would crowd out Open App/Quit.

## Requirements

### Typography and Interaction

- Always-visible A− / percentage reset / A+ controls with semantics: decrease/increase sticky font size, reset sticky font size to 100%, current percentage as state description.
- Min/max disable corresponding button; reset remains keyboard reachable and returns 1.0. Show integer percentages without decimals and fractional eighth-step values with one decimal, e.g. 87.5%, 100%, 112.5%.
- Sticky-only Ctrl+plus, Ctrl+equals (including Shift+=), Ctrl+minus, Ctrl+0; add numpad variants supported by pinned Compose key enums. Only KeyDown, Ctrl pressed, no Alt/Meta. Do not swallow ordinary +/−/0 typing or unrelated key events.
- Root preview handling must work while quick-add is focused; it must not steal focus, select/reset typed text, interfere with Enter/IME submit, Escape, Tab, or child controls.
- Font zoom is multiplied by inherited font scale exactly once; density and dp dimensions stay unchanged. Reset means 100% of inherited font setting, not a forced system scale of 1.0.

### Compact Layout Budget

All sizes logical dp at inherited font scale 1.0 and sticky zoom 1.0. Minimum heights must grow for text; do not force fixed-height clipping at 150% zoom or larger OS text.

| Component | Target |
|---|---|
| Sticky outer inset | 6dp, replacing 12dp |
| Header title | 14sp semibold/bold, single line with ellipsis; title alone may truncate |
| Header action/zoom controls | ≥28dp height, ≥28dp zoom width; horizontal content padding 4–6dp; label text 12sp |
| Header layout | FlowRow with 4dp gaps; title/zoom/action groups wrap, no hidden recovery actions |
| Header/body and quick-add/list gaps | 4–6dp |
| Quick-add field and Add | 36dp minimum height, 14sp field text, vertical padding 4dp, horizontal 6dp |
| Task row | 32dp minimum; horizontal inset 4dp, vertical 2dp, title 14sp, control/text gap 4dp |
| Checkbox and expand button | 24×24dp layout targets, normal checkmark/indicator rendering |
| Child indentation | 12dp; retain unambiguous hierarchy |
| List row gap / section inset | 2dp row gaps; section text vertical inset 2dp |
| Progress/deadline metadata | 12sp, 2dp vertical gap, 4dp horizontal FlowRow gaps; no dropped date/zone/overdue content |
| Error/status content | 4–8dp padding/gaps, compact action button; readable text and loading indicators, no duplicated oversized spacers |

This is an explicit desktop-sticky-only exception to current global 48dp and body-text standards. Do not change `TabbyRushThemeTokens.MinTouchTargetSize`, shared typography, Android widget targets, or shared main-app components. Phase 03 updates standards to name the exception.

## Architecture and Data Flow

1. `DesktopController` passes `initialStickyPresentation.fontScale` into `DesktopStickyTaskController`.
2. Existing sticky StateFlow exposes scale with task state; no separate UI `remember` zoom or second mutable persisted copy.
3. Header/keyboard → `DesktopController.onStickyAction` → sticky controller state update → reactive recomposition.
4. On changed zoom only, `DesktopController` writes a complete presentation snapshot (visible, latest valid geometry, current state fontScale) through existing preferences.
5. Sticky content captures inherited `LocalDensity.current` before providing its override:

```kotlin
val inherited = LocalDensity.current
val stickyDensity = remember(inherited.density, inherited.fontScale, state.fontScale) {
    Density(inherited.density, inherited.fontScale * state.fontScale)
}
CompositionLocalProvider(LocalDensity provides stickyDensity) {
    // Existing sticky theme and entire sticky content only.
}
```

Use a sticky-local Material-2 `LocalMinimumInteractiveComponentEnforcement provides false` where supported by the pinned dependency (with narrow experimental opt-in). Explicitly size individual controls and button content padding. Do not globally override `LocalViewConfiguration` or Android target enforcement. Check measured pointer/semantic bounds because touch-target expansion is separate from Material layout minimums.

## File Ownership and Changes

All paths repo-root-relative under `G:/ws/sharing/doggo/`.

| File | Action | Owner / change |
|---|---|---|
| `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` | Modify | 02: seed controller scale; action-to-save integration; complete presentation flush; presentation snapshot serialization. Keep native geometry/window lifecycle unchanged. |
| `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt` | Modify | 02: scoped density/enforcement, preview keys, wrapping compact header, compact quick-add, rows, metadata, status/error spacing, updated comments. |
| Phase 01 files | Intentionally unchanged in 02 | Request contract adjustment from owner rather than creating another action/state convention. |
| New/deleted source files and dependencies | None | Reuse existing composable helpers and foundation/material APIs. No general desktop design system or UI-test harness added. |

## Specific Implementation Steps

1. Seed sticky controller from loaded presentation. Keep launch-time `.copy(geometry = initialClampedGeometry)` so the added zoom field survives geometry restoration automatically.
2. Rename private/public-in-module `flushStickyGeometry()` to `flushStickyPresentation()` because it now saves all presentation fields. Migrate **every** caller, including hide, quit/dispose, tests if applicable; retain no obsolete alias.
3. Give `DesktopController` a small presentation lock for geometry mutation, visibility changes, zoom dispatch/save snapshot, and final flush. Capture current geometry/visibility/scale together and serialize saves so a delayed geometry-only/default snapshot cannot overwrite a newer zoom. Never acquire account/store locks while holding it.
4. In `onStickyAction`, delegate normally; for the three presentation actions compare before/after zoom and save only if changed. Persistence occurs outside StateFlow update lambdas. Normal task actions must not cause file writes. Hide/final flush always read latest scale.
5. Put `LocalDensity` override in `StickyTaskContent` around the existing theme/content only. Keep a single provider; do not also wrap main window or multiply each `sp` style. Existing duplicated theme wrapper is not a new zoom source.
6. Add root preview-key handling dispatching the exact phase 01 actions. For pinned enum/key naming inspect existing dependency sources if needed. Consume recognized Ctrl combinations including bound no-op actions; return false otherwise. No global OS hooks or per-row duplicate shortcuts.
7. Replace header's rigid Row with a FlowRow of a shrinkable title and compact zoom/recovery groups. All controls stay in the persistent header before readiness gating. Narrow widths wrap complete controls rather than truncating labels or using an overflow-only recovery menu.
8. Add A−/percentage/A+ using existing Material TextButton styling, compact explicit min dimensions/content padding and bound-enabled state. Apply clear labels, current percentage state, button roles, and visible focus affordance.
9. Replace OutlinedTextField with `BasicTextField` using `decorationBox`, a bordered/background container, compact padding, and placeholder. Preserve value, onValueChange, enabled/submitting state, `singleLine`, text style/cursor contrast, `KeyboardOptions`, IME Done, Enter guard, Escape focus clear, and label semantics. A placeholder is decorative, not another editor/focus node. Keep typed text/selection stable across recomposition.
10. Compact Add to same minimum height; keep submitting progress state and duplicate-submit protection. Error text appears only when present; no permanently reserved error space.
11. Apply row target dimensions to root, interactive/disabled checkboxes, expand button, insets/gaps, indentation and metadata. Preserve disabled automatic parent semantics, desired completion setter, stable item keys, collapse state keyed by account token, two-line title ellipsis, own absolute deadlines and overdue labeling.
12. Use a weighted remaining-height task list/status area after persistent header and quick-add. Keep list scrollable at 280×240dp; avoid hard-coded header/list heights. Status/empty/error content may wrap/scroll within remaining space when inherited font scale is high.
13. Compact `ReadinessStatusView`, SectionHeader, empty-state padding, error banners, progress/deadline gaps and the completed-section separator, not just Ready task rows. Retain non-Ready privacy and permanent header recovery.
14. Replace obsolete sticky-only “minimum 48dp” comments with exact desktop sizing/accessibility rationale. Keep palette, contrast, SansSerif, and main-app/shared tokens intact. Pass source to phase 03 owner; do not run checks yourself.

## Success Criteria and Verification Contract

Main owner checks phase 03 matrix:

- At 100%, compact dimension targets measured in real runtime; no hidden internal 48/56dp inflation remains in task controls/quick-add.
- At 75/150%, text changes immediately, main window typography unchanged, persisted geometry and display-density conversions unchanged.
- Header controls wrap at narrow width; Open App/Quit remain visible, focusable, operable in all readiness states, with and without tray.
- Font actions save immediately on change and survive hide/show/quit/restart. Keyboard typing, task submission, main draft, scroll/collapse/focus survive zoom.
- Multi-line tasks, parent progress, child indentation, own deadlines/timezones and overdue FlowRow are legible; tall content grows/scrolls instead of clipping.
- Semantics retain labels, roles, checked/disabled/expanded state; keyboard action activates once, not twice through row and child handlers.

## Risk Assessment and Mitigation

| Risk | Severity | Mitigation |
|---|---|---|
| Material-3 sizing advice breaks Material-2 build | High | Use pinned Material-2 imports/APIs; narrowly scoped opt-ins; main owner compile gate. |
| Text field visually shrinks but clips cursor/baseline | High | Foundation editable field with explicit decoration and text-aware min-height; real typed input at extreme scales. |
| Narrow header hides recovery | High | Wrapping groups before truncation; persistent header outside scroll/task gating; verify minimum window. |
| Density scales dp/geometry accidentally | High | Retain inherited density; fontScale-only override; verify DPI and main-window independence. |
| Account clear or stale file snapshot loses zoom | Medium | Phase 01 copies plus serialized complete presentation saves; hide/quit/restart regressions. |
| Smaller targets overlap or lose keyboard semantics | Medium | 24dp row targets, clear spacing, standard interactive controls; native hit/focus/semantics inspection. No accessibility-compliance claim from size alone. |
| Repeated fsync from held shortcuts | Low | No writes on endpoint/reset no-op; reuse current synchronous best-effort mechanism. No debounce/background persistence framework unless separately justified. |

## Security Considerations

No new external side effects or remote data paths. Keep token-fenced task access and non-Ready clearing untouched. UI screenshots for acceptance use a controlled disposable profile; no private task titles/credentials in evidence. Interactive computer control requires human grant; point-of-risk Quit/restart or task writes must target that disposable app/profile only.

## Next Steps

Phase 03 owns regression tests, integrated compile/test execution, real Windows/Linux observation, accessibility checks, evidence recording, and accurate documentation updates. Any discovered pinned-API mismatch is resolved in these same files, not by upgrading Compose or weakening acceptance.

## Unresolved Questions

None blocking design. Native rendered behavior is intentionally unverified until phase 03.
