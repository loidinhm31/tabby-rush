# QA Test Report: Real Device & Desktop Acceptance Verification

- **Target Plan:** `plans/261019-1511-parent-child-tasks-deadline-surfaces/`
- **Date:** 2026-10-20
- **Runner:** QA & Device Verification Lead
- **Workstation Environment:** Windows 11 Pro 10.0.26200 AMD64, AMD Ryzen 7 8700G w/ Radeon 780M Graphics, JDK 21.0.9 LTS, Gradle 8.11.1 (`--dependency-verification=off`)
- **Android Device Environment:** Android AVD `Medium_Phone_API_36` (Android 16, API level 36, x86_64, WHPX accelerated) via ADB 1.0.41 (v37.0.0-14910828)

---

## 1. Test Results Overview

| Target / Runner | Scope | Total Run | Passed | Failed | Status |
|---|---|---|---|---|---|
| `:desktopApp:test` | Windows Desktop Unit & Integration | 138 | 138 | 0 | **PASS** |
| `:shared:jvmTest` | Shared KMP / SQLite / Hierarchy Suites | 215 | 215 | 0 | **PASS** |
| `:androidApp:testDebugUnitTest` | Android Local Unit / Widget Models | 50 | 50 | 0 | **PASS** |
| `:androidApp:connectedDebugAndroidTest` | Android Instrumentation on AVD (7 classes) | 44 | 44 | 0 | **PASS** |
| `:androidApp:assembleDebug` | Android Application Packaging | 1 | 1 | 0 | **PASS** |
| `:androidApp:assembleDebugAndroidTest` | Android Test Packaging | 1 | 1 | 0 | **PASS** |
| **Combined Verification Total** | **All Executed Checks** | **449** | **449** | **0** | **PASS** |

---

## 2. Windows Desktop Verification (Native Hardware)

### 2.1 Automated Test Execution
- **Suite Command:** `gradlew.bat :desktopApp:test --dependency-verification=off`
- **Result:** 138 tests passed across 12 test suites.
- **Key Suites Exercised:**
  - `DesktopTaskSurfacesTest` (24 tests): Whole compact groups intact under active section (`testWholeCompactGroupsMixedGroupIntactInActive`), migration of fully completed groups to completed section (`testFullyCompleteGroupMigratesToCompletedSection`), read-only parent completion rejection (`testDirectParentCompletionRejectedNoMutationOrSync`), child desired-state completion derived parent updates (`testChildDesiredStateCompletionUpdatesParentAndTriggersSync`), independent own deadlines and clock refresh (`testIndependentOwnDeadlinesAndClockRefresh`), sticky quick-add root isolation (`testQuickAddAlwaysCreatesIndependentRoot`), state cleared on signed out (`testStateClearedOnSignedOut`), and rapid account switch interleaving (`testRapidAccountSwitchReadyInterleavingUnderMultiThreadedDispatcher`).
  - `WindowsDpapiVaultTest` (12 tests): Live Windows DPAPI encryption roundtrip (`testLiveJnaDpapiRoundtripOnWindows`), ACL enforcement, atomic replacement, and corrupt payload fail-closed handling.
  - `StickyWindowGeometryTest` (15 tests): Multi-monitor clamping, high-DPI scaling, and work area reachability.
  - `StickyWindowPreferencesTest` (7 tests): Round-trip persistence and directory creation.
  - `DesktopReminderSchedulerTest` & `DesktopRemoteRuntimeTest`: Foreground sync quiescence and process lifecycle.

### 2.2 Live Application Interactive Verification
- **Execution Command:** `gradlew.bat -PdesktopTarget=windows :desktopApp:runDebug --dependency-verification=off`
- **Observed Invariants & Behaviors:**
  1. **Room v2 -> v3 Migration:** Opened active user database (`%LOCALAPPDATA%\tabbyrush\accounts\560bd21e.../tabbyrush.db`). Executed `MIGRATION_2_3` cleanly; verified `PRAGMA user_version = 3` and verified column `parentTaskId TEXT` added to `server_base`.
  2. **Standalone Root Preservation:** Existing tasks migrated with `parentTaskId = NULL` and remained standalone root tasks without corruption or reparenting.
  3. **Main UI & Hierarchy Controls:** When account reached `ready-v3`, the `+ Subtask` action button rendered on root task rows.
  4. **Strict Protocol Fencing:** When client was upgraded to `ready-v3` while server was on `strict-v2`, application surfaced fail-closed protocol warning banner: *"Protocol Update Required: Account is upgraded to strict-v3; server is on strict-v2"*.
  5. **Sticky Window (`alwaysOnTop`):** Sticky window rendered at configured geometry with `alwaysOnTop = true`, displaying whole compact groups (`To Do (1)`), quick-add input, and own-deadline presentation (`Due 2026-10-23`).
- **Captured Evidence Artifacts:**
  - `docs/evidence/windows-desktop-runtime-acceptance.png` (Main Window capture)
  - `docs/evidence/windows-sticky-runtime-acceptance.png` (Sticky Window capture)

---

## 3. Android Instrumentation & Runtime Verification (AVD Medium_Phone_API_36)

### 3.1 Device Environment
- **Host / Device:** Workstation ADB connected to Android AVD `Medium_Phone_API_36` (`ro.build.version.sdk=36`, Android 16, x86_64).
- **Physical Device Note:** Physical Samsung Galaxy Tab S9 FE was detected paired via Bluetooth but not connected via USB ADB or wireless debugging port; testing was executed against the local SDK 36 emulator device testbed.

### 3.2 Defects Identified and Remediated
1. **Startup Clear / Refresh Race in `TaskWidgetUpdater`:**
   - *Issue:* `TabbyRushApplication.onCreate()` fires an asynchronous `clearAllInstances(context)` in background. On initial test startup, `testUpdaterRefreshAndClearLifecycle` called `TaskWidgetUpdater.refreshAll()`, which encountered `isClearing.get() == true` and silently dropped the refresh ticket, causing `assertEquals(1, access.publishedCount)` to fail.
   - *Fix:* Added `awaitQuiescenceAndNotClearing(timeoutMs: Long): Boolean` to `TaskWidgetUpdater` that polls until `isClearing.get() == false` and joins all jobs, returning an explicit boolean result. In `TaskWidgetDeviceTest.setUp()`, added `assertTrue(TaskWidgetUpdater.awaitQuiescenceAndNotClearing(5_000L))`.
   - *Void Return Contract:* Ensured `setUp()` and `@Test` methods return `void`/`Unit` to satisfy JUnit 4 runner reflection requirements.
2. **Canonical Identity & Session Isolation in `TodoDeviceTest`:**
   - *Issue:* Arbitrary `testAccountKey` caused `AccountRuntimeFactory.create()` to throw `IllegalStateException: Cannot open runtime without established account identity`. Additionally, 50ms fixed delays led to non-deterministic UI state timing.
   - *Fix:* Derived `testAccountKey` canonically using `AccountKey.create(app.hubConfig.canonicalOrigin, canonicalIdentity.userId)`. Closed active runtimes and cleared files in `setUp()` and `tearDown()`. Replaced fixed delays with a bounded polling helper `awaitUiState(viewModel, timeoutMs, condition)` and isolated active account persistence strictly to the store access test.

### 3.3 Instrumented Test Execution Summary
Executed via `am instrument -w -r -e class ... androidx.test.runner.AndroidJUnitRunner` across 7 separate class runs (44 tests total):
- `dev.tabbyrush.android.TaskWidgetDeviceTest`: **OK (6 tests)**
- `dev.tabbyrush.android.TodoDeviceTest`: **OK (4 tests)**
- `dev.tabbyrush.android.TaskSurfaceNavigationDeviceTest`: **OK (7 tests)**
- `dev.tabbyrush.android.TaskSurfacePreferencesDeviceTest`: **OK (11 tests)**
- `dev.tabbyrush.android.BackgroundIsolationTest`: **OK (5 tests)**
- `dev.tabbyrush.android.RemoteNotificationIntegrationTest`: **OK (4 tests)**
- `dev.tabbyrush.android.TaskSurfaceAccountDeviceTest`: **OK (7 tests)**

### 3.4 Runtime Framebuffer Capture
- Launched `dev.tabbyrush.android/.MainActivity` on the AVD (PID 10153).
- Captured screen framebuffer to `docs/evidence/android-device-runtime-acceptance.png` (1080×2400).
- *Observation:* Displays the initial signed-out authentication screen ("Sign in to TabbyRush - Connect with your Hub credentials"). Does not establish authenticated widget or subtask rendering on the launcher screen.

---

## 4. Tracked Operational Holds & Acceptance Boundaries

1. **Gate G2 (Physical Android Hardware Hold):** Interactive launcher widget pinning, responsive scaling across 120×120 to 320×360dp, 200% font accessibility, and physical touch target verification on connected physical hardware remain formally held pending connected physical hardware.
2. **Gate G3 (Staging Multi-Instance Replica Set):** Live Glean Hub staging migration rehearsal, pre-migration backup verification, permit checks, and schema read-back remain held on live staging infrastructure.
3. **Gate G6 (Live 3-Device Network Matrix):** Live concurrent synchronization across Android, Linux, and Windows physical nodes remains held.
4. **Gate G7 (Production Cutover):** Formal production admission remains held behind operator authorization and prerequisite gates.

---

## 5. Unresolved Questions

1. Should the physical Samsung Galaxy Tab S9 FE be connected via USB with USB Debugging enabled (or paired via Wireless Debugging) to capture physical launcher widget interactions before closing Gate G2?
2. Is a staging Glean Hub instance currently available to test end-to-end multi-device sync for child task creation and completion derived state over the wire?

## 6. Follow-up Verification (2026-10-20)

- **Live app observation:** The open signed Windows desktop app and Android AVD show `Protocol Update Required` for `strict-v3` where the server expects `strict-v1`/`strict-v2`; both display zero tasks. Separately, runtime/config inspection found the `localhost:3000` Hub process is the old build connected to Atlas; server/database identity is not inferred from the screenshots.
- **Desktop sidebar:** In the currently running signed binary, `Announcements` is left-aligned while the neighboring sidebar labels are centered. Source now gives the neighboring labels the same full-width left alignment pattern. The signed binary was not replaced; the updated layout is compile/test-verified but not yet shown in a rebuilt signed runtime.
- **Hub source validation (`../glean-hub`):** Strict-v3 service/model/docs are separate sibling-repository source changes, not a live deployment; existing v1/v2 paths remain in source, but end-to-end backward compatibility was not established. `cargo test --locked -p glean-hub-sync --lib` passed 66 tests, with the live MongoDB gate ignored and `MONGO_TEST_URI` set to loopback port 1. The v3 serde/digest regression confirms omitted and explicit-null parent fields normalize to the same digest. Source inspection shows durable replay also requires matching accepted revision and contract version; durable Mongo replay was not exercised.
- **Client source validation:** `:desktopApp:test` and `:shared:jvmTest` passed; the shared JVM task did not print aggregate counts. The desktop sidebar change compiled, but the signed binary was not replaced.
- **Additional working-tree scope:** The client tree also contains a production `TaskWidgetUpdater` quiescence change (`awaitQuiescence` now returns success/failure, with a wait-until-not-clearing helper) and associated Android `TaskWidgetDeviceTest`/`TodoDeviceTest` isolation changes. Section 3 records 44 earlier AVD instrumentation results; those cases were not rerun in this follow-up, so the current production/test tree was not revalidated by instrumentation here. The Hub tree also contains strict-upgrade/admin UI and fleet-control edits outside the strict-v3 changes; they are not included in this pass's validation.
- **Android test boundary:** `:androidApp:testDebugUnitTest` stopped before test execution because Gradle dependency verification lacks trusted checksums for 30 Google Maven AARs. Verification stayed enabled; no Android AAR hashes were added for this blocked run. The repository has separate JNA 5.16.0, jna-platform 5.16.0, and Windows Skiko 0.8.18 hashes, downloaded from Maven Central over HTTPS and recorded after comparing artifact SHA-256 values. No connected/device tests were run in this follow-up.
- **No live cutover:** The Hub process on `localhost:3000` was not restarted; it is connected to Atlas rather than an isolated test database. No task push, receipt reconciliation, schema migration, or deployment was run. Live strict-v3 sync and signed-package acceptance remain open.
