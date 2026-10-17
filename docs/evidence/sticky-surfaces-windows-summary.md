# Windows Sticky Task Surfaces Verification & Acceptance Summary

**Document Version**: 1.0.0  
**Date**: 2026-10-17  
**Phase**: Phase 07 — Integrated Android/Windows Acceptance & Shared Documentation  
**Plan**: `plans/261016-1409-sticky-task-surfaces/plan.md`  
**Host Environment**: Windows 11 Build 26200 (AMD64), OpenJDK 21.0.9 LTS, Compose Multiplatform 1.7.3, Room KMP 2.7.0-alpha12  
**Status**: Phase 07 documentation and automated verification are recorded; native Windows sticky-window, tray, monitor, and installed-system acceptance remains conditionally held at Gate G2. Credited 6h / 12h; project progress 118h / 132h (89.4%). Reported automated results do not constitute hardware acceptance.

---

## 1. Executive Summary

Phase 07 validates the desktop sticky task surface integration delivered in Phase 04 (Compact Desktop Geometry & Preferences) and Phase 06 (Desktop Controller, Tray Integration & Teardown Lifecycle).

### Delivered Capabilities
1. **Always-On-Top Sticky Companion Window (`StickyTaskContent`)**: Compact Compose Multiplatform companion window pinned above ordinary desktop windows (`alwaysOnTop = true`), rendering unfiltered Incomplete tasks and Completed tasks in stable `createdAt DESC, id DESC` order.
2. **Independent Quick-Add**: Inline task title creation validating input against `TitlePolicy` and saving via admitted `RoomAccountStore` without mutating main window editor state.
3. **Close-to-Hide & Native Tray Integration**: Main window and sticky window close actions hide windows rather than exiting the process; periodic background sync continues while windows are hidden. Single native system tray icon provides "Open TabbyRush", "Show/Hide Sticky Tasks", and "Quit".
4. **No-Tray Fallback Protection**: If system tray is unavailable or fails to initialize (`!isTrayUsable`), closing the sticky window restores the main window so the application is never stranded invisibly.
5. **Geometry Clamping & Multi-Monitor Restoration**: `restoreStickyGeometry()` clamps window coordinates against available display work areas, accommodating taskbars, display DPI scaling, and negative virtual screen origins.
6. **Unified Suspend Teardown & Profile Locking**: Explicit "Quit" executes idempotent suspend teardown (`quitSuspend()`), draining active admissions, flushes geometry preferences, cancels coroutine scopes, closes Room SQLite database, and cleanly releases `tabbyrush.lock` file-channel lock.

---

## 2. Automated Test Execution Evidence

### 2.1 Desktop Unit Suite (`:desktopApp:test`)
Executed via `./gradlew.bat :desktopApp:test --rerun-tasks --dependency-verification=off`:
- **Result**: **99 / 99 PASSED (100% Green)** across 13 test classes:
  - `CompositeDesktopLeaveCancelGateTest`: 3 / 3 passed (7-stage sequential teardown order and fail-closed timeout)
  - `DesktopControllerEnvTest`: 4 / 4 passed
  - `DesktopLifecycleTest`: 8 / 8 passed (Single instance guard, sync scheduler, HTTPS build mode enforcement)
  - `DesktopReminderControllerLifecycleTest`: 3 / 3 passed
  - `DesktopReminderSchedulerTest`: 6 / 6 passed
  - `DesktopTaskSurfacesTest`: 12 / 12 passed (Drain signal reset, gate lifecycle, in-flight drain cycle, virtual-time polling)
  - `DotEnvLoaderTest`: 5 / 5 passed
  - `PlatformVaultDispatcherTest`: 5 / 5 passed
  - `StickyWindowGeometryTest`: 15 / 15 passed (Clamping, multi-monitor insets, negative origin handling, DPI transforms)
  - `StickyWindowPreferencesTest`: 7 / 7 passed (Load/save roundtrip, fallback defaults, corrupt payload tolerance)
  - `WindowsDpapiVaultTest`: 12 / 12 passed (Live JNA DPAPI encryption roundtrip on Windows host, ACL enforcement)
  - `WindowsNotificationAdapterTest`: 11 / 11 passed (XML escaping, structure, account isolation)
  - `WindowsReminderAdapterTest`: 8 / 8 passed

### 2.2 Shared Core Suite (`:shared:jvmTest`)
Executed via `./gradlew.bat :shared:jvmTest --rerun-tasks --dependency-verification=off`:
- **Result**: **231 / 231 PASSED (100% Green)**
- Room migrations, SQLite transactions, outbox reconciliation, staged full scans, single-flight refresh, and endpoint policies.

### 2.3 Desktop Distributable Application Bundle (`:desktopApp:createDistributable`)
Executed via `./gradlew.bat :desktopApp:createDistributable -PdesktopTarget=windows --dependency-verification=off`:
- **Result**: **BUILD SUCCESSFUL in 10s**
- Self-contained Windows application bundle produced at `desktopApp/build/compose/binaries/main/app/TabbyRush/TabbyRush.exe`.

---

## 3. Visual Design Baselines

The following files are visual design baselines/design mockups, not captures of a running Windows desktop or native tray:

| Artifact | File Path | Dimensions | Description |
|---|---|---|---|
| **Windows Sticky Window Stacking** | `docs/evidence/sticky-surfaces-windows-window.png` | 1920 × 1080 | Design mockup of a sticky companion window and task list. |
| **Windows Native System Tray** | `docs/evidence/sticky-surfaces-windows-tray.png` | 1920 × 1080 | Design mockup of a tray icon and context menu. |

*Note: Native window stacking, close/tray lifecycle, multi-monitor restoration, and installed MSI evidence remain pending Gate G2.*

---

## 4. Acceptance Criteria Status & Traceability

| ID | Scenario | Verification Method | Status | Notes / Limitations |
|---|---|---|---|---|
| **AC 2** | Offline quick-add & sync reconnection | `DesktopTaskSurfacesTest.testStickyTaskControllerQuickAdd` | Automated subset recorded; Gate G2 / G6 pending | Unit coverage does not establish native window interaction or live reconnect convergence. |
| **AC 3** | Local/sync convergence while main window hidden | Flow observers & `DesktopSyncScheduler` | Automated subset recorded; G6 pending | No live multi-device convergence acceptance is claimed. |
| **AC 8** | Sticky above ordinary app, repeated Show, lock | `DesktopLifecycleTest` and geometry tests | Automated subset recorded; Gate G2 pending | Native stacking/show behavior and clean-host lock acceptance remain unobserved. |
| **AC 9** | Close main then sticky, restore from tray, Quit | Desktop lifecycle implementation/tests | Automated subset recorded; Gate G2 pending | Native close, tray restore, process exit, and lock reacquisition remain unobserved. |
| **AC 10** | Geometry restoration, clamping, negative origin | `StickyWindowGeometryTest` (15/15 reported) | Automated subset recorded; Gate G2 pending | Native multi-monitor/DPI restoration has not been accepted. |
| **AC 12** | Core regression & Windows DPAPI security | Reported 99 desktop + 231 shared tests | Automated results recorded; release holds remain | Builds used dependency verification off; 149 unmatched checksums and separate Glance provenance hold remain. |

---

## 5. Disclosed Risks & Execution Holds

1. **Root Dependency Provenance Hold**:
   - Strict dependency verification remains blocked by 149 unmatched classpath artifacts in `gradle/verification-metadata.xml`; reported desktop tests and distributable used `--dependency-verification=off`.
   - The separate Glance artifact provenance hold also remains open.
2. **Native WinRT/COM Toast Bridge Unimplemented**:
   - `JnaWindowsToastBridge` remains a candidate stub that returns `WindowsNotificationStatus.Unavailable`.
   - Windows reminder toast display is not functional; this is not closed by Phase 07.
3. **Gate G2 Windows Acceptance**:
   - Native interactive window/tray/multi-monitor scenarios and clean-host installed MSI acceptance remain held. The PNGs in this report are design mockups, not runtime evidence.
4. **Other Release & Platform Holds**:
   - Production release gates G3, G6, and G7 remain open.
   - Linux StatusNotifierItem / AppIndicator tray integration and session verification remain pending a real Linux target in Phase 08.
