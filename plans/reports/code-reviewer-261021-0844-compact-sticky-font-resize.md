# Code Review Report: Desktop Sticky Font Resizing and Compact UI

**Date:** 2026-10-21  
**Reviewer:** Senior Software Engineer (Code Reviewer)  
**Plan:** `plans/261021-0844-compact-sticky-font-resize/plan.md`  
**Report Target:** `plans/reports/code-reviewer-261021-0844-compact-sticky-font-resize.md`  

---

## Code Review Summary

### Scope
- **Files Reviewed:**
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-window-geometry.kt`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-window-preferences.kt`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-model.kt`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt`
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/sticky-window-preferences-test.kt`
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt`
  - `docs/code-standards.md`
  - `docs/design-guidelines.md`
  - `docs/development-roadmap.md`
  - `docs/project-changelog.md`
  - `docs/system-architecture.md`
  - `plans/261021-0844-compact-sticky-font-resize/plan.md` (and phase-01..03)
- **Lines Analyzed:** ~2,600 LOC across 8 Kotlin source/test files and 5 documentation files.
- **Review Focus:** Correctness, thread safety & concurrency, input sanitization, accessibility invariants, backward compatibility, and test coverage.
- **Updated Plans:** `plans/261021-0844-compact-sticky-font-resize/plan.md`, `phase-01-preferences-and-model.md`, `phase-02-compact-components-and-font-scaling.md`, `phase-03-tests-and-verification.md`.

### Overall Assessment
The implementation delivers persistent desktop sticky font scaling (0.75x–1.50x, 12.5% step) and compact UI layout components without compromising task domain logic, account fencing, or main application windows. Scoped typography scaling via Compose `LocalDensity` is cleanly isolated to sticky content. Backward compatibility for legacy preference files and two-argument presentation constructors is fully maintained. 

Several areas require hardening:
1. Missing presentation synchronization lock in `DesktopController` allowing concurrent geometry/visibility/scale updates to interleave and overwrite newer zoom levels on disk.
2. Rigid `Row` in `StickyHeaderBar` instead of `FlowRow`, presenting horizontal clipping risks for recovery controls at narrow viewports (280dp) under high zoom (150%).
3. Missing scroll containment in `ReadinessStatusView` at minimum window height (240dp).
4. Subtle CAS retry capture bug in `DesktopStickyTaskController.onAction` zoom cases.
5. Shortcut modifier check lacking `!event.isAltPressed`, risking AltGr conflicts on international keyboard layouts.

---

## Critical Issues
*None identified.* No remote execution, authentication leaks, memory corruption, SQL injection, or unrecoverable data loss risks.

---

## High Priority Findings

### 1. Presentation Persistence Race in `DesktopController`
- **Location:** `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:201–222`
- **Problem:** `DesktopController` stores `currentStickyGeometry` and `currentStickyFontScale` as unsynchronized mutable fields. When window movement/resizing (AWT EDT calling `updateStickyGeometry`), window close (`onCloseStickyWindow` setting `visible = false`), and zoom actions (`updateStickyFontScale`) occur concurrently or in close succession:
  - `flushStickyGeometry()` captures `isStickyWindowVisible.value`, `currentStickyGeometry`, and `currentStickyFontScale` without a shared lock.
  - While `preferences.save()` serializes file writes via internal instance lock, the *snapshot capture* in `DesktopController` is unsynchronized. A delayed visibility/geometry flush can snapshot a stale `currentStickyFontScale` and persist it after a zoom save completes, silently overwriting user zoom back to the default.
- **Recommendation:** Implement a dedicated presentation lock (`presentationLock = Any()`) in `DesktopController` serializing snapshot capture and persistence:
  ```kotlin
  private val presentationLock = Any()

  fun updateStickyFontScale(scale: Float) {
      if (isValidStickyFontScale(scale)) {
          synchronized(presentationLock) {
              currentStickyFontScale = scale
              flushStickyPresentationLocked()
          }
      }
  }

  fun updateStickyGeometry(geometry: StickyWindowGeometry) {
      if (isValidStickyGeometry(geometry)) {
          synchronized(presentationLock) {
              currentStickyGeometry = geometry
          }
      }
  }

  fun flushStickyPresentation() = synchronized(presentationLock) {
      flushStickyPresentationLocked()
  }

  private fun flushStickyPresentationLocked() {
      preferences.save(
          StickyWindowPresentation(
              visible = isStickyWindowVisible.value,
              geometry = currentStickyGeometry,
              fontScale = currentStickyFontScale
          )
      )
  }
  ```

### 2. Header Layout Rigid `Row` vs `FlowRow` at Min Width & High Zoom
- **Location:** `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt:170–276`
- **Problem:** Phase 02 explicitly specified `FlowRow` with 4dp gaps for `StickyHeaderBar`. Currently implemented as a single horizontal `Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)`.
  - At minimum window width (280dp) minus 12dp horizontal padding (268dp usable), at 150% font scale or high OS text scale, the 3 zoom buttons cluster (~100dp) and 2 recovery buttons cluster (~102dp) leave <66dp for title text.
  - If inherited OS text scale is 1.5x–2.0x, the action clusters alone exceed 268dp. Because `Row` cannot wrap, buttons on the far right ("Quit", "Open") will be pushed out of bounds and clipped, violating the invariant that recovery controls must always be accessible on narrow viewports without tray.
- **Recommendation:** Wrap header elements in `FlowRow` (already imported in this file) so the zoom and recovery button clusters gracefully wrap to a second line when horizontal space is constrained:
  ```kotlin
  FlowRow(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
      Text(
          text = "TabbyRush Sticky",
          style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
          color = TabbyRushPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.align(Alignment.CenterVertically)
      )
      Row(
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.CenterVertically
      ) { /* Zoom cluster */ }
      Row(
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.CenterVertically
      ) { /* Recovery cluster */ }
  }
  ```

### 3. Missing Vertical Scroll in `ReadinessStatusView` at Min Height
- **Location:** `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt:675–720`
- **Problem:** `ReadinessStatusView` centers an unscrollable `Column` with 12dp padding and 8dp gaps containing a progress spinner (32dp), title, multi-line subtitle, and "Open Main App" button (32dp).
  - At minimum window height (240dp), when combined with the persistent header (~35–55dp), outer padding (12dp), and 150% font scale, this column exceeds 240dp.
  - Without `Modifier.verticalScroll(rememberScrollState())`, content clips and the "Open Main App" action becomes unreachable in SignedOut/Unavailable/Locked states.
- **Recommendation:** Add vertical scroll modifier to the Column inside `ReadinessStatusView`:
  ```kotlin
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
          .padding(12.dp)
          .verticalScroll(rememberScrollState())
  )
  ```

---

## Medium Priority Improvements

### 1. StateFlow CAS Retry Leaking Stale `updatedScale`
- **Location:** `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt:536–573`
- **Problem:** In `ZoomIn`, `ZoomOut`, and `ResetZoom`:
  ```kotlin
  var updatedScale: Float? = null
  _stickyState.update { current ->
      val newScale = (current.fontScale + STICKY_FONT_SCALE_STEP).coerceIn(MIN_STICKY_FONT_SCALE, MAX_STICKY_FONT_SCALE)
      if (newScale != current.fontScale) {
          updatedScale = newScale
          current.copy(fontScale = newScale)
      } else {
          current
      }
  }
  updatedScale?.let { onFontScaleChanged?.invoke(it) }
  ```
  `MutableStateFlow.update` is an optimistic CAS loop (`while (!compareAndSet)`). If iteration 1 computes `newScale != current.fontScale` and sets `updatedScale = 1.125f`, but the CAS fails due to a concurrent state update, iteration 2 runs. If iteration 2 finds `newScale == current.fontScale` (e.g. concurrent zoom reached max), `updatedScale` is *not reset to null*. `onFontScaleChanged` will be called with the stale value from iteration 1 even though iteration 2 made no change.
- **Recommendation:** Clear `updatedScale = null` at the start of each CAS iteration, or compare `val prev = _stickyState.value.fontScale` before and after:
  ```kotlin
  var updatedScale: Float? = null
  _stickyState.update { current ->
      val newScale = (current.fontScale + STICKY_FONT_SCALE_STEP).coerceIn(MIN_STICKY_FONT_SCALE, MAX_STICKY_FONT_SCALE)
      if (newScale != current.fontScale) {
          updatedScale = newScale
          current.copy(fontScale = newScale)
      } else {
          updatedScale = null
          current
      }
  }
  ```

### 2. Input Sanitization at Controller and Save Boundaries
- **Location:**
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt:280, 290`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-window-preferences.kt:140`
- **Problem:**
  - `DesktopStickyTaskController(initialFontScale: Float)` initializes `_stickyState` directly with `fontScale = initialFontScale` without calling `isValidStickyFontScale`. If a caller passes `Float.NaN` or `5.0f`, non-finite/out-of-bounds scale enters the live state.
  - `StickyWindowPreferences.save()` writes `properties.setProperty(KEY_FONT_SCALE, presentation.fontScale.toString())` without normalizing. While `load()` sanitizes corrupt files, saving an invalid scale writes `"NaN"` to disk.
- **Recommendation:** Apply `isValidStickyFontScale` normalization at both boundaries:
  - In controller init:
    `fontScale = if (isValidStickyFontScale(initialFontScale)) initialFontScale else DEFAULT_STICKY_FONT_SCALE`
  - In `save()`:
    `val safeScale = if (isValidStickyFontScale(presentation.fontScale)) presentation.fontScale else DEFAULT_STICKY_FONT_SCALE`
    `properties.setProperty(KEY_FONT_SCALE, safeScale.toString())`

### 3. Redundant Automatic Parent Progress in Accessibility Tree
- **Location:** `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt:570–577, 626–635`
- **Problem:** In `CompactTaskRow`, when a task has live subtasks:
  1. Disabled checkbox sets `stateDescription = "Active (automatic: X/Y)"` and `contentDescription = "Automatic progress for ${todo.title}"`.
  2. Text below title sets `contentDescription = "Progress: X of Y completed"`.
  When a screen reader (Narrator/Orca) traverses the row, progress is announced twice in immediate succession.
- **Recommendation:** Consolidate announcement: retain the complete accessible description on the checkbox, and clear accessibility semantics on the visual progress text (`Modifier.clearAndSetSemantics { }`), or use visual-only text.

### 4. Global Shortcut Modifier Handling & AltGr Collisions
- **Location:** `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:1201`
- **Problem:**
  `if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed))`
  On Windows systems with international keyboard layouts (German QWERTZ, Polish Programmer's), the `AltGr` key registers as `Ctrl + Alt`. Typing symbols via `AltGr + Minus` or `AltGr + 0` satisfies `event.isCtrlPressed` and triggers unintended zoom actions while typing.
- **Recommendation:** Guard against Alt modifier:
  `if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed) && !event.isAltPressed)`

---

## Low Priority Suggestions

### 1. Compact Sizing Variance from Plan 02 Budget
- **Observation:** `phase-02` layout budget specified 24dp checkboxes, 36dp quick-add minHeight, and 32dp task row minHeight. The implementation uses 22dp checkboxes, 32dp quick-add, and 28dp task rows.
- **Note:** `docs/code-standards.md` §10.5 was updated to document 22–24dp and 28dp min heights, so this is an intentional refinement for mouse/keyboard viewports, but represents a minor deviation from the initial plan budget.

### 2. Method Naming in `DesktopController`
- **Observation:** Phase 02 suggested renaming `flushStickyGeometry()` to `flushStickyPresentation()`. The author retained `flushStickyGeometry()`. While keeping the existing name prevents breaking callers, renaming or aliasing it better reflects that it now flushes all presentation fields including `fontScale`.

---

## Positive Observations
1. **Scoped Compose Typography Architecture:** Providing `Density(currentDensity.density, currentDensity.fontScale * stickyState.fontScale)` through `LocalDensity` strictly inside the sticky `Window` ensures clean text scaling without inflating dp layout coordinates, altering system DPI, or affecting main application windows.
2. **Material-2 Component Enforcement Scoping:** Narrow use of `@OptIn(ExperimentalMaterialApi::class)` with `LocalMinimumInteractiveComponentEnforcement provides false` cleanly bypasses the default 48dp layout expansion without affecting Android or shared app tokens.
3. **Robust Backward Compatibility:**
   - Defaulted `fontScale = DEFAULT_STICKY_FONT_SCALE` preserves binary/source compatibility for two-parameter `StickyWindowPresentation` callers.
   - Dual property key support (`fontScale` and `sticky.window.font_scale` alias).
   - Independent recovery: corrupt or missing font scale safely recovers to 1.0f without invalidating valid geometry or visibility.
4. **Strong Security & Account Privacy Fencing:** Presentation preferences strictly persist window metadata. Zoom preferences survive account transitions, while sensitive task titles, active drafts, and account tokens are completely wiped on sign-out/token mismatch.
5. **Atomic Preference Persistence:** Retained atomic replace semantics via temporary file + `fsync` + `ATOMIC_MOVE` with `REPLACE_EXISTING` fallback.

---

## Recommended Actions

1. **[High] Add presentation synchronization lock in `DesktopController`:** Wrap geometry mutation, font scale updates, and presentation file saves in a shared lock to prevent race conditions from overwriting zoom on disk.
2. **[High] Replace `Row` with `FlowRow` in `StickyHeaderBar`:** Ensure zoom and recovery button clusters wrap on narrow viewports (280dp) at 150% text zoom.
3. **[High] Add vertical scroll to `ReadinessStatusView`:** Prevent UI clipping at 240dp window height under high zoom.
4. **[Medium] Reset `updatedScale = null` in CAS retry loop:** Prevent spurious `onFontScaleChanged` callbacks during CAS conflict retries.
5. **[Medium] Sanitize input in controller constructor and `save()`:** Guard against `Float.NaN` and non-finite floats.
6. **[Medium] Exclude `isAltPressed` in shortcut preview:** Prevent AltGr typing collisions on international keyboards.
7. **[Medium] Consolidate parent progress screen reader announcements:** Avoid duplicate reads between checkbox and text semantics.

---

## Metrics
- **Type Coverage:** 100% (Strict Kotlin static typing; all enums, actions, and states sealed).
- **Test Results:** 137 tests executed in `:desktopApp` suite; **137 PASSED, 0 FAILED** (including 4 new tests: `testFontScaleRoundTripAndBounds`, `testStickyZoomActionsAndClamping`, `testStickyFontScalePreservedAcrossReadinessTransitions`, `testDesktopControllerStickyFontScalePersistence`).
- **Dependency Verification:** STRICT mode verified; 0 verification errors.
- **Linting / Compile Issues:** 0 errors, 0 deprecation warnings in new code.
- **Native UI Acceptance:** Conditional / Pending (Real Windows Narrator, Linux Orca, and multi-monitor graphical acceptance deferred to Phase 06 as planned).

---

## Unresolved Questions
1. Should `DesktopController.flushStickyGeometry()` be formally renamed to `flushStickyPresentation()` in the next refactoring cycle to eliminate semantic ambiguity?
2. Does the team wish to retain 22dp/28dp compact targets as standard desktop companion metrics in `code-standards.md`, or align back to 24dp/32dp from the initial Phase 02 budget?
