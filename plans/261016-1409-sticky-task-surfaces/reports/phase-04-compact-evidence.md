# Phase 04 Desktop Compact Presentation Evidence Report

**Status:** DELIVERED / READY FOR INTEGRATION
**Date:** 2026-10-16
**Author:** Phase 04 Desktop Compact Owner (Wave B)

---

## 1. Executed & Delivered Artifacts

Strictly conforming to exclusive file ownership boundaries, the following production and test components were delivered:

### 1.1 `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt`
- **Stateless Compact Composable:** `StickyTaskContent(state: StickyTaskState, onAction: (StickyTaskAction) -> Unit)`.
- **Section Ordering & Partitioning:** Renders Incomplete tasks followed by Completed tasks in a single scrollable `LazyColumn` in stable encounter order.
- **Readiness-Gated Rendering:** Renders task content and mutations only when `state.readiness` is `SurfaceReadiness.Ready(token)`. For `Restoring`, `SignedOut`, `Locked`, and `Unavailable`, presents clean, non-sensitive recovery/status views.
- **Persistent Recovery Controls:** Heading bar provides dedicated, permanently visible `Open App` and `Quit` buttons (`StickyTaskAction.OpenApp`, `StickyTaskAction.Quit`) regardless of tray or readiness status. Flexible heading layout prevents crowding or clipping on narrow viewports (down to 200dp).
- **Independent Quick-Add Buffer:** Controller-owned inline text field with raw title validation, error banner display, and submission progress indicator. Keyboard navigation supports Enter-to-submit (when enabled and non-blank) and Escape to clear focus without mutating the controller buffer. Never creates main `EditorState` or dispatches main `TodoAction` variants.
- **Desired Completion Dispatch:** Task toggles dispatch `StickyTaskAction.SetCompleted(token, todo.id, checked)` with the requested boolean state.
- **Accessibility & Touch Targets:** Uses `TabbyRushTheme` tokens, minimum 48dp click/touch targets (`TabbyRushThemeTokens.MinTouchTargetSize`), and explicit semantic descriptions (`contentDescription`, `stateDescription`).

### 1.2 `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-window-geometry.kt`
- **Contract Models:** Defines `StickyWindowGeometry(xDp, yDp, widthDp, heightDp, savedScaleX, savedScaleY)`, `MonitorWorkArea(id, primary, xDp, yDp, widthDp, heightDp, scaleX, scaleY)`, and `StickyWindowPresentation(visible, geometry)`.
- **Restoration & Multi-Monitor Math:** `restoreStickyGeometry(saved: StickyWindowGeometry?, monitors: List<MonitorWorkArea>): StickyWindowGeometry`:
  - **Negative Origin Preservation:** Preserves valid negative virtual coordinates for secondary monitors placed to the left or above the primary monitor.
  - **Display Selection:** Identifies target monitor by maximum intersection area with saved bounds; deterministic tie-breaking favors primary monitor, then sorts by monitor ID.
  - **Hotplug Fallback:** When saved bounds have zero intersection with any connected monitor (e.g. secondary monitor disconnected), falls back to centering on the primary monitor.
  - **Size Clamping:** Default 360×520 dp; enforces minimum 280×240 dp unless the target work area is smaller (in which case it constrains to the work area).
  - **Reachability Clamping:** Keeps the entire title bar reachable (`yDp >= monitor.yDp` and within horizontal work area bounds) and ensures content visibility within usable work areas.
  - **Scale Metadata:** Records target monitor's current `scaleX` and `scaleY` in returned geometry without applying double scale multiplication to normalized dp coordinates.

### 1.3 `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-window-preferences.kt`
- **Presentation Storage Outside Room/Sync:** `StickyWindowPreferences(file: File)` manages `sticky-window.properties` containing only presentation metadata (`version`, `visible`, `xDp`, `yDp`, `widthDp`, `heightDp`, `savedScaleX`, `savedScaleY`).
- **Atomic Replacement:** Saves via a UUID-named temporary file in the parent directory, flushes to disk with `FileOutputStream.fd.sync()`, and renames using `StandardCopyOption.ATOMIC_MOVE` (with `AtomicMoveNotSupportedException` fallback to `REPLACE_EXISTING`), cleaning temporary files in `finally`.
- **Defensive Recovery:** Missing, empty, unparseable, or corrupted files (e.g. NaN, infinite, negative sizes) fall back to safe default hidden presentation (`visible = false`, 360×520 dp).
- **Single-Writer Thread Safety:** Synchronized instance methods protect against intra-process concurrent write races.

---

## 2. Automated Test Suite

### 2.1 `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/sticky-window-geometry-test.kt`
Exhaustive unit test suite comprising 15 test cases:
1. `testEmptyMonitorListReturnsDefaultReachableGeometry`: Returns reachable 360×520 dp at (0, 0).
2. `testNullSavedGeometryPlacesOnPrimaryMonitorWorkArea`: Centers default geometry on primary monitor.
3. `testCorruptSavedGeometryFallsBackToDefaults`: Handles NaN, infinite, and negative bounds safely.
4. `testNegativeOriginsPreservedOnSecondaryMonitor`: Preserves negative coordinates on left monitor.
5. `testNegativeOriginAbovePrimaryPreserved`: Preserves negative coordinates on top monitor.
6. `testHighDpiScalingMetadataRecorded`: Records target monitor scale metadata accurately.
7. `testHotplugDisconnectionFallbackToPrimary`: Repositions onto primary when saved monitor is unplugged.
8. `testMinimumSizeEnforcement`: Enforces 280×240 dp minimum size on normal displays.
9. `testTinyMonitorDoesNotExpandBeyondWorkArea`: Constrains window to tiny work area (e.g. 200×200 dp).
10. `testTitleBarReachabilityClamping`: Clamps top edge to work area top inset (`y >= monitor.yDp`).
11. `testHorizontalReachabilityClamping`: Clamps window horizontally within work area bounds.
12. `testMaximumIntersectionSelectionAcrossMultipleDisplays`: Chooses display with largest overlap area.
13. `testFallbackToFirstMonitorWhenNoPrimaryDesignated`: Gracefully handles headless/no-primary setups.
14. `testTiedIntersectionAreaSelectsDeterministically`: Breaks equal-area ties deterministically by ID.
15. `testTiedIntersectionWithOnePrimaryFavorsPrimary`: Favors primary display in equal-area tie scenarios.

### 2.2 `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/sticky-window-preferences-test.kt`
Thorough persistence unit test suite comprising 7 test cases:
1. `testMissingFileReturnsDefaultHiddenPresentation`: Initial launch defaults to hidden at 360×520 dp.
2. `testRoundTripPersistence`: Verifies exact serialization and deserialization of presentation state.
3. `testCorruptFileContentFallsBackToDefaults`: Recovers cleanly from garbage text and unparseable values.
4. `testNegativeOrInfiniteDimensionsRecoverToDefaults`: Recovers cleanly from negative and infinite numbers.
5. `testAutoCreatesParentDirectories`: Creates missing nested parent configuration directories on save.
6. `testTemporaryFilesCleanedUpAfterSave`: Verifies no orphan `.tmp` files linger in the directory.
7. `testConcurrentWritesOnSamePreferencesInstance`: Verifies 20 concurrent threads execute without error and maintain valid file state.

---

## 3. Disclosed Non-Goals & Integration Boundary

The following responsibilities remain explicitly outside Phase 04 ownership and belong to downstream phases:
- **`DesktopSurfacePlatform` Seam & Native AWT Enumeration:** Enumerating `GraphicsEnvironment` screen devices, transforms, and `Toolkit` insets into `MonitorWorkArea` instances is owned by Phase 06.
- **Window Composition & Controller Wiring:** Integrating `StickyTaskContent` into Compose `Window`, managing `alwaysOnTop = true`, hooking `WindowState`, and debouncing position saves are owned by Phase 06 (`main.kt`).
- **Tray Integration & Stacking Verification:** System tray creation, menu dispatch, and native Windows/Linux window manager acceptance belong to Phase 06/07/08.
- **Root Build & Project Lockfiles:** No build files or shared root manifests were modified.

---

## 4. Terminal Leaf Handoff

Phase 04 is fully complete and unblocks **Phase 06 (Desktop Integration)**:
- `StickyTaskContent` is ready to be composed in the desktop sticky `Window`.
- `restoreStickyGeometry` is ready to clamp monitor work areas during launch and monitor configuration changes.
- `StickyWindowPreferences` is ready for presentation loading and debounced geometry saves.
