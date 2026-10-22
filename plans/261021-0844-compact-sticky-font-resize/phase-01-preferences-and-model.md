# Phase 01 — Preferences and Model

## Context Links

- [Overview and frozen decisions](./plan.md)
- [Planner research/evidence](../reports/evc-planner-261021-0844-compact-sticky-font-resize.md)
- [Architecture proposal](../../docs/system-architecture.md#21-proposed-desktop-sticky-typography-and-compact-controls)
- [Code standards](../../docs/code-standards.md#32-immutability--state-management)
- [Preferences source](../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-window-preferences.kt)
- [Controller source](../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt)

## Overview

Priority P2 · Status complete · Effort 3h · Owner presentation/model implementer.

Add bounded, backward-compatible presentation preference and reactive zoom state. Freeze interfaces before phase 02. Do not run builds/tests mid-flight; main owner verifies after integration.

## Key Insights

- `StickyWindowPresentation` currently contains only visible/geometry in `sticky-window-geometry.kt:34–37`; append a defaulted field, preserving existing positional constructors.
- Preferences use version 1, Java `Properties`, an instance lock, same-directory temp file, fsync, atomic replace with existing non-atomic fallback, swallowed I/O failures, and temp cleanup. Extend this path; do not create another settings store or change failure semantics.
- Corrupt geometry currently recovers independently of parsed visibility. Font validation must be equally independent, including the invalid-geometry return branch.
- `StickyTaskState` is reconstructed during initialization, Ready-token change, non-Ready clearing, and `stopObserving()`. Naively defaulting a new field resets user zoom on sign-out.
- `MutableStateFlow.update` already protects task state. A zoom update must copy only zoom; never recompute `projectStickySections`, touch Room, or trigger sync.

## Requirements

### Functional

- Constants: `DEFAULT_STICKY_FONT_SCALE = 1.0f`, `MIN_STICKY_FONT_SCALE = 0.75f`, `MAX_STICKY_FONT_SCALE = 1.50f`, `STICKY_FONT_SCALE_STEP = 0.125f`.
- `StickyWindowPresentation(visible, geometry, fontScale = DEFAULT_STICKY_FONT_SCALE)`.
- `KEY_FONT_SCALE = "sticky.window.font_scale"` in existing preferences companion.
- Accept any finite stored float within the inclusive range, including non-step values; preserve valid stored values without silently snapping. Missing, unparsable, NaN, infinity, zero/negative, or out-of-range values recover to default, not the nearest endpoint.
- Interactive +/- adds/subtracts one step then clamps at endpoints; reset yields exactly 1.0. On-grid values use binary-exact eighth steps; no accumulated decimal drift.
- Append defaulted `fontScale` to `StickyTaskState` and token-free `IncreaseFontScale`, `DecreaseFontScale`, `ResetFontScale` singleton actions to `StickyTaskAction`.
- Optional trailing constructor input `initialFontScale: Float = DEFAULT_STICKY_FONT_SCALE` on `DesktopStickyTaskController`; normalize it before initializing state.
- Zoom works in every readiness state. It is device-profile presentation, not account/task state.

### Non-Functional

No Room/wire/shared-model changes, new settings abstraction, persistent task data, account identifier, or network activity. Retain current thread-safety and preference-write behavior; no migration/version bump for an additive optional property.

## Architecture and Component Interactions

`StickyWindowPreferences.load → StickyWindowPresentation.fontScale → DesktopController construction → DesktopStickyTaskController(initialFontScale) → stickyState.fontScale`.

`StickyTaskAction zoom → DesktopStickyTaskController.onAction → state.update(current.copy(fontScale = next))`.

Phase 02 persists changed state through `DesktopController.onStickyAction`; this controller remains independent of files. Header and keyboard emit the same actions. Task mutations keep existing account tokens and desired-state setters.

Suggested pure helpers in `sticky-window-geometry.kt` alongside the presentation constants:

- `isValidStickyFontScale(value: Float): Boolean` — finite and in range.
- `normalizeStickyFontScale(value: Float): Float` — valid value or default, used only at presentation input/persistence boundaries.
- +/- math is tiny; keep directly in action branches or one small step helper if tests benefit. No action classes for arbitrary unvalidated scale are needed.

`normalize` is recovery for non-critical presentation metadata, not permission to coerce task/deadline/domain input.

## File Ownership and Changes

All paths repo-root-relative under `G:/ws/sharing/doggo/`.

| File | Action | Phase owner / change |
|---|---|---|
| `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-window-geometry.kt` | Modify | 01: append defaulted presentation field; constants and pure validation/recovery helpers. Leave monitor geometry algorithm unchanged. |
| `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-window-preferences.kt` | Modify | 01: key, independent parse, all load branches, serialized value, accurate presentation-metadata comments. |
| `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-model.kt` | Modify | 01: defaulted live state field and three presentation actions. Leave task projection untouched. |
| `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt` | Modify | 01: initial font input, zoom action cases, preserve scale while clearing sensitive state. Leave `DesktopTaskSurfaceAccess` untouched. |
| New/deleted source files | None | Reuse current presentation seams; do not split unrelated existing oversized files. |

## Specific Implementation Steps

1. Add constants/helpers and append the presentation field. Document difference between font zoom and `savedScaleX/Y` display transforms.
2. Parse `KEY_FONT_SCALE` with `toFloatOrNull()`, then finite/range validation. No whole-file reset solely because zoom is bad.
3. Build effective geometry as today, then construct presentation carrying visible + effective geometry + parsed zoom. Ensure the invalid-geometry branch does not discard valid zoom.
4. Serialize normalized zoom in `save()` with every complete presentation snapshot; preserve current keys/version/atomic-replace path. Existing code that saves an invalid in-memory zoom must never persist NaN/infinity.
5. Add state field and actions, then handle all exhaustive action matches. Read every `StickyTaskState(` constructor and every `when` on `StickyTaskAction`, not just named target lines.
6. Initialize controller scale from validated constructor input. In `onAction`, use pure `MutableStateFlow.update` lambdas; keep callbacks/I/O out of retryable update lambdas.
7. On Ready-token change, sign-out/non-Ready clear, and `stopObserving`, replace task/readiness/input fields using `current.copy(...)`, or explicitly carry `current.fontScale`. Sensitive lists/input must still clear exactly as before.
8. Ensure `startObserving`, async submission responses, completion, and deadline refresh copies naturally retain scale. No zoom-triggered store reads, sync requests, deadline projection, or main-editor mutation.
9. Hand off constants, action names, constructor field, and validation policy to phase 02. Tests are owned by phase 03; do not duplicate implementation across owners.

## Success Criteria and Verification Contract

Main owner adds/runs tests in phase 03:

- Old two-argument presentation and state constructors default to 1.0; old preference file preserves geometry/visibility.
- Valid endpoints, default, 1.125 and arbitrary 1.1 round-trip; bad font values only replace font field.
- Invalid geometry + valid zoom keeps zoom and visibility while geometry recovers.
- Save/load stores the exact specified property key and includes font with concurrent/successive geometry snapshots.
- Repeated +/- reaches 0.75 and 1.50 without exceeding; reset exact; bound actions leave state unchanged.
- Task observation, submission, sign-out, account switch, deadline refresh, and stop preserve zoom while prior-account content is cleared.
- Zoom emits zero task writes/sync triggers/navigation callbacks and retains task projection references when nothing else changes.

## Risk Assessment and Mitigation

| Risk | Severity | Mitigation |
|---|---|---|
| Reconstructed state resets zoom | High | Audit all state constructors; use copy; regressions on all lifecycle paths. |
| NaN bypasses `coerceIn` | Medium | `isFinite` check before range validation/arithmetic; invalid loads/defaults explicit. |
| New field breaks constructor calls | Medium | Append defaulted field; search all constructors; avoid reordered parameters. |
| Invalid font erases valid window placement | Medium | Independently validated presentation fields and cross-corruption tests. |
| Preference write fails | Low, existing | Keep in-session zoom functional; retain previous file/best-effort semantics; do not claim persistence succeeded on failed I/O. |

## Security Considerations

Scale is non-sensitive, profile-local, not account-authorized data. Never persist quick-add text, tasks, account key, tokens, or credentials with it. Presentation actions require no account token; this must not weaken token validation on task actions. Existing leave privacy clears remain mandatory.

## Next Steps

Phase 02 integrates this frozen contract with desktop window lifecycle, sticky-only density, header actions, compact components, and keyboard input. Phase 03 authors regression coverage and executes verification after all code lands.

## Unresolved Questions

None.
