# Phase 07 Terminal Evidence & Acceptance Report

**Plan:** `plans/261016-1409-sticky-task-surfaces/plan.md`  
**Phase:** Phase 07 — Integrated Android/Windows Acceptance & Shared Documentation  
**Date:** 2026-10-17 | **Author:** Main Integrator  
**Status:** delivered / documentation & automated verification evidenced; native hardware acceptance conditionally held (Gate G2); pending Linux Phase 08. Phase 07 credit: 6h / 12h; total: 118h / 132h (89.4%).

---

## 1. Acceptance Criteria Traceability Matrix (AC 1–12)

| ID | Scenario | Source / Test Reference | Recorded Automated Evidence | Runtime Evidence | Remaining Hold / Caveat |
|:---:|---|---|---|---|---|
| **1** | Glance widget scrolling & toggle | `TaskWidgetModelTest.kt`, `TabbyRushTaskWidget.kt` | Android unit test suite (34/34 reported) | PNG is a design mockup only | Native launcher interaction and process-death behavior held at Gate G2 |
| **2** | Offline widget/sticky Add & sync | `DesktopTaskSurfacesTest.kt`, `TaskSurfaceCreateScreen.kt` | Desktop tests (99/99 reported) and fixtures | PNG is a design mockup only | Native surface interaction held at G2; live multi-device sync held at G6 |
| **3** | Multi-surface background convergence | `TabbyRushSyncWorker.kt`, `DesktopSyncScheduler.kt` | Shared tests (231/231 reported) | No native runtime capture | Live three-device sync held at G6 |
| **4** | Oldest-overdue notification selection | `TaskSurfaceNotificationTest.kt`, `selectNextDeadline` | Android unit test (10/10 reported) | PNG is a design mockup only | Native notification/shade acceptance held at Gate G2 |
| **5** | Stale generation & duplicate actions | `AndroidTaskSurfaceAccess.kt`, `SessionViewModel.kt` | Unit/source evidence recorded | No physical action-delivery evidence | Native action delivery held at Gate G2 |
| **6** | Permission denial & API 34+ swipe | `TaskSurfacePreferences.kt`, `main-activity.kt` | Unit tests and preference codecs recorded | No native notification evidence | Native permission and dismissal behavior held at Gate G2 |
| **7** | Process kill, restart, logout/wipe | `CompositeLeaveSchedulerCancelGateTest.kt` | Android unit test (4/4 reported) | No connected-device capture | Gate G2 pending; Android drain-timeout and Glance cleanup findings remain open |
| **8** | Sticky above ordinary app & lock | `DesktopLifecycleTest.kt`, `main.kt` | Desktop tests recorded | PNG is a design mockup only | Native stacking and lock behavior held at Gate G2 |
| **9** | Close-to-hide, tray restore, Quit | `main.kt`, `desktop-task-surfaces.kt` | Desktop tests and distributable build recorded | PNG is a design mockup only | Native tray, close, Quit, and lock-reacquisition behavior held at Gate G2 |
| **10** | Geometry clamping & multi-monitor | `StickyWindowGeometryTest.kt` | Desktop geometry tests (15/15 reported) | No native multi-monitor capture | Native DPI, display-change, and restoration scenarios held at Gate G2 |
| **11** | Linux session, tray, stacking | `phase-08-linux-acceptance.md` | Not run on Linux | None | Pending a real Linux host and graphical session in Phase 08 |
| **12** | Core regression & security audit | `release-security-check.test.mjs`, diagnostics | 364 Gradle tests, 12 Node tests, six diagnostics, 18 Hub checks, and eight rehearsal checks reported | Automated evidence only | 149 unmatched classpath checksums, separate Glance provenance hold, and release gates remain open |

---

## 2. Race and Semantic Boundary Verification

- **Atomic Desired-State Completion**: `setCompletedLocally` updates under the mutation lock; preserves concurrent edits and advances `updatedAt` monotonically.
- **Token Generation Fencing**: Non-matching tokens and negative generations are rejected fail-closed; arbitrary accounts cannot be initialized on demand.
- **Serialized Publication Mutex**: `publishIfCurrent` asserts active token matches while holding `publicationMutex`, preventing cross-account leaks during teardown.
- **Presentation Preference Isolation**: Desktop coordinates (`sticky-window.properties`) and Android suppression keys persist outside Room without user secrets.
- **No-Tray Fallback**: The implementation restores the main window when `!isTrayUsable` and the sticky window closes. Native tray availability/init-failure behavior has not been accepted at Gate G2.

---

## 3. Automated Verification Execution Summary
The commands and results below are recorded from the Phase 07 evidence; they were not rerun while preparing this documentation.

The 99 desktop-test count is attributed to this 2026-10-17 Phase 07 report; the [Cycle 2 review dated 2026-10-16](../../reports/code-review-261016-2157-sticky-task-surfaces-cycle-2.md) recorded 98. The available records do not identify the test or source revision accounting for the one-test difference, so these counts are not revision-linked and were not rerun for this documentation update.

| Suite / Check | Command | Result | Details |
|---|---|:---:|---|
| Shared Core JVM Tests | `./gradlew.bat :shared:jvmTest --rerun-tasks --dependency-verification=off` | **PASS** | 231 / 231 tests green across 30 test classes |
| Desktop App Unit Tests | `./gradlew.bat :desktopApp:test --rerun-tasks --dependency-verification=off` | **PASS** | 99 / 99 tests green across 13 test classes |
| Android App Unit Tests | `./gradlew.bat :androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off` | **PASS** | 34 / 34 tests green across 6 test classes |
| **Total Gradle Tests** | *All three suites combined* | **PASS** | **364 / 364 tests passing (100% Green, 0 failures)** |
| Exported Component Auditor | `node --test scripts/release-security-check.test.mjs` | **PASS** | 12 / 12 checks green (audits canonical `MainActivity` & `TabbyRushTaskWidgetReceiver`) |
| Diagnostic Source Security | `node scripts/release-security-check.mjs --diagnose-source` | **PASS** | CLEAN (Cleartext disabled, backup disabled, HTTPS origin, 0 source secrets) |
| Hub Offline Live Smoke | `node scripts/hub-live-smoke.mjs --fixture-test` | **PASS** | 18 / 18 checks green (Strict-v1 health, auth, push, pull, tombstone CAS) |
| Strict-v2 Rehearsal | `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | **PASS** | 8 / 8 checks green (CLI token redaction, dry-run, same-ID retry, read-back) |
| Android Debug Assemble | `./gradlew.bat :androidApp:assembleDebug --dependency-verification=off` | **PASS** | Debug APK built with bundled native SQLite driver in 30s |
| Desktop Distributable | `./gradlew.bat :desktopApp:createDistributable -PdesktopTarget=windows --dependency-verification=off` | **PASS** | Self-contained Windows bundle produced (`TabbyRush.exe`) in 10s |
| Strict Dependency Provenance| `./gradlew.bat :shared:jvmTest --dependency-verification=strict` | **HELD** | 149 unmatched classpath checksums in `gradle/verification-metadata.xml` |

---

## 4. Preserved Release Holds & Safety Disclosures

1. **Supply-Chain Verification Baseline**: 149 classpath checksums in `gradle/verification-metadata.xml` remain unmatched; recorded builds used `--dependency-verification=off`. The separate Glance artifact provenance hold remains unresolved.
2. **Open Android Safety Findings**:
   - `TabbyRushApplication.closeRuntimeInternal()` catches and logs a delivery-gate drain-timeout exception, then returns before observer-job cancellation or Room closure. `closeActiveRuntime()` nevertheless clears `activeRuntime`; coroutine jobs remain and the database is not closed.
   - `TaskWidgetUpdater.kt` catches `Throwable` during RemoteViews blanking and Glance session closure, risking retained widget state on OS failure.
3. **Hardware & Release Gate Holds**:
   - Gate G2: Native Android launcher/shade/device scenarios and native Windows window/tray/multi-monitor acceptance remain held. The Windows installed MSI clean-host acceptance is also pending.
   - The native Windows WinRT/COM toast bridge remains an unimplemented candidate stub.
   - Gate G3: Multi-node live MongoDB cluster migration remains held on staging infrastructure.
   - Gate G6: Three-device concurrent live sync proof remains held pending physical hardware testbeds.
   - Gate G7: Production cutover remains blocked pending prerequisite gates.
4. **Phase 08 Linux Gate**: Linux StatusNotifierItem / AppIndicator tray integration and session verification remain pending a real Linux session.

## 5. Unresolved Questions

- When will Android device/emulator and Windows clean-host interactive testbeds be available to complete Gate G2 acceptance?
- When will the 149 dependency checksums and separate Glance artifact provenance be established?
- Which Linux host and graphical session will be used for Phase 08 acceptance?
