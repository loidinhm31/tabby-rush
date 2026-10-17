# QA Test Execution Report: Phase 10 Shared Deadline Editor, Task List & Local Settings

**Date**: 2026-10-15  
**Context**: Linux Fedora 44 x86_64 | JDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Android SDK (`/home/loidinh/Android/Sdk`) | Gradle 8.11.1 | Node.js v24.16.0  
**Target Phase**: `Phase 10 — Shared deadline editor, task list and local settings`  
**Working Directory**: `/home/loidinh/WS/tabbyrush`  
**Artifact Path**: `plans/reports/qa-261015-2245-phase-10-validation.md`  

---

## 1. Test Results Overview

| Index | Command / Suite | Scope | Category | Total | Pass | Fail | Skip | Status |
|:---:|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Shared Multiplatform JVM & SQLite Suite | Unit / Integration | 189 | 189 | 0 | 0 | PASS |
| 2 | `JAVA_HOME=... ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | Desktop App Unit & Controller Lifecycle Suite | Unit / Integration | 49 | 49 | 0 | 0 | PASS |
| 3 | `node scripts/strict-v2-vector-runner.mjs` | Strict v2 wire vectors & schema fingerprints | Contract Vectors | 23 | 23 | 0 | 0 | PASS |
| 4 | `node scripts/hub-live-smoke.mjs --fixture-test` | Hub offline fixture smoke harness | Protocol Fixture | 18 | 18 | 0 | 0 | PASS |
| 5 | `node scripts/release-security-check.mjs --diagnose-source` | Static manifest, export, origin, secret rules | Security Diagnostic | 6 | 6 | 0 | 0 | PASS |
| 6 | `node scripts/hub-strict-upgrade.test.mjs` | Hub strict upgrade CLI test suite | Integration CLI | 9 | 9 | 0 | 0 | PASS |
| 7 | `JAVA_HOME=... ANDROID_HOME=... ./gradlew :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks` | Android Unit & Gate Tests | Unit / Platform | 12 | 12 | 0 | 0 | PASS |
| 8 | `JAVA_HOME=... ./gradlew :desktopApp:createDistributable --dependency-verification=off` | Desktop App native distribution packaging | Packaging Task | 1 | 1 | 0 | 0 | PASS |
| 9 | `JAVA_HOME=... ANDROID_HOME=... ./gradlew :androidApp:assembleDebug --dependency-verification=off` | Android Debug APK compilation & assembly | Packaging Task | 1 | 1 | 0 | 0 | PASS |

**Test Execution Summary**:
- **Total Heterogeneous Checks Executed**: 308 checks across 9 test suites / build targets
  - **Shared KMP JVM & SQLite Tests**: 189 (+7 tests over Phase 09, across 26 test classes)
  - **Desktop App Unit & Platform Tests**: 49 (+2 tests over Phase 09, across 10 test classes)
  - **Contract Wire Vectors & Fingerprints**: 23 (2 canonical fingerprints + 10 valid vectors + 11 rejections)
  - **Hub Offline Protocol Smoke Checks**: 18 (5 suites)
  - **Static Release Security Diagnostic**: 6 checks
  - **Hub Strict Upgrade CLI Tests**: 9 tests
  - **Android Unit & Gate Tests**: 12 tests across 4 test classes
  - **Build & Distribution Assembly**: 2 checks (`:desktopApp:createDistributable`, `:androidApp:assembleDebug`)
- **Passed**: 308 (100%)
- **Failed**: 0
- **Skipped / Cancelled**: 0

---

## 2. Shared KMP JVM Suite Breakdown (`:shared:jvmTest`)

- **Total Test Classes**: 26
- **Total Tests**: 189
- **Passed**: 189, Failed: 0, Skipped: 0
- **Execution Time**: ~1.08s (overall build ~8.7s)

| Test Class | Passed | Failed | Skipped | Time |
|---|:---:|:---:|:---:|:---:|
| `dev.tabbyrush.shared.auth.HubAuthClientTest` | 9 | 0 | 0 | 0.336s |
| `dev.tabbyrush.shared.auth.HubAuthSessionTest` | 8 | 0 | 0 | 0.126s |
| `dev.tabbyrush.shared.auth.SingleFlightRefreshTest` | 3 | 0 | 0 | 0.007s |
| `dev.tabbyrush.shared.contract.AccountKeyTest` | 5 | 0 | 0 | 0.003s |
| `dev.tabbyrush.shared.contract.EndpointPolicyTest` | 8 | 0 | 0 | 0.003s |
| `dev.tabbyrush.shared.contract.PersistedScheduleProjectionTest` | 5 | 0 | 0 | 0.003s |
| `dev.tabbyrush.shared.contract.ReminderScheduleCalculatorTest` | 9 | 0 | 0 | 0.009s |
| `dev.tabbyrush.shared.contract.StrictDigestTest` | 5 | 0 | 0 | 0.002s |
| `dev.tabbyrush.shared.contract.StrictTimeZoneResolverTest` | 7 | 0 | 0 | 0.023s |
| `dev.tabbyrush.shared.contract.StrictV2DigestExtendedTest` | 6 | 0 | 0 | 0.004s |
| `dev.tabbyrush.shared.contract.StrictV2DigestVectorTest` | 5 | 0 | 0 | 0.003s |
| `dev.tabbyrush.shared.contract.TaskDeadlineReminderModelsTest` | 6 | 0 | 0 | 0.001s |
| `dev.tabbyrush.shared.contract.TitlePolicyTest` | 6 | 0 | 0 | 0.002s |
| `dev.tabbyrush.shared.data.ReminderOccurrenceStoreTest` | 4 | 0 | 0 | 0.013s |
| `dev.tabbyrush.shared.data.ReminderSchedulingCoordinatorSqliteSmokeTest` | 2 | 0 | 0 | 0.114s |
| `dev.tabbyrush.shared.data.ReminderTeardownBarrierSqliteTest` | 1 | 0 | 0 | 0.012s |
| `dev.tabbyrush.shared.data.RoomAccountStoreTest` | 23 | 0 | 0 | 0.168s |
| `dev.tabbyrush.shared.data.RoomMigrationV1ToV2Test` | 1 | 0 | 0 | 0.017s |
| `dev.tabbyrush.shared.sync.AccountLeaveProtocolTest` | 7 | 0 | 0 | 0.014s |
| `dev.tabbyrush.shared.sync.AccountSyncCoordinatorTest` | 9 | 0 | 0 | 0.042s |
| `dev.tabbyrush.shared.sync.ContractTransitionTest` | 4 | 0 | 0 | 0.022s |
| `dev.tabbyrush.shared.sync.HubSyncClientTest` | 12 | 0 | 0 | 0.037s |
| `dev.tabbyrush.shared.sync.OutboxReconciliationTest` | 12 | 0 | 0 | 0.012s |
| `dev.tabbyrush.shared.sync.ReminderDeliveryGateTest` | 4 | 0 | 0 | 0.006s |
| `dev.tabbyrush.shared.sync.StagedFullScanTest` | 14 | 0 | 0 | 0.063s |
| `dev.tabbyrush.shared.ui.TodoSemanticsTest` | 14 | 0 | 0 | 0.036s |

### Phase 10 Specific Shared Test Additions:
1. **`RoomAccountStoreTest.testClearingDeadlineToServerBaseDoesNotResurrect`**:
   - Verifies clearing deadline and reminder rules produces empty local fields matching server base tombstone without resurrecting deleted schedule.
2. **`TodoSemanticsTest` Suite Additions (+6 tests)**:
   - `testDeadlineValidationValidTimedAndDateOnly`: Validates date-only reminder anchor 09:00 vs timed deadline parsing. Confirms next-day overdue boundary calculation (`overdueEpochMs > anchorEpochMs`).
   - `testDeadlineValidationDstGapAndOverlap`: Checks America/New_York spring-forward invalid gap (02:30 EDT) rejection and fall-back ambiguous overlap (01:30 EST/EDT) requiring explicit disambiguation offset selection (-14400s vs -18000s).
   - `testDeadlineReminderBoundsAndLastOccurrenceLimit`: Enforces negative lead rejection, multi-reminder interval >=15min constraint, and rejection when last reminder occurrence exceeds deadline anchor.
   - `testOverdueLogicDateOnlyVsTimedAndCompleted`: Confirms date-only task at anchor 09:00 is NOT overdue; overdue triggered only at next calendar day start. Confirms completed tasks never render overdue.
   - `testReminderSettingsStateDefaultsAndCapabilities`: Validates UI settings state container defaults and delivery permission calculation.
   - `testConflictReviewDetailedParameters`: Validates local vs remote full deadline, reminder rule, timezone comparison container in `ConflictState`.

---

## 3. Desktop App Suite Breakdown (`:desktopApp:test`)

- **Total Test Classes**: 10
- **Total Tests**: 49
- **Passed**: 49, Failed: 0, Skipped: 0
- **Execution Time**: ~0.30s

| Test Class | Passed | Failed | Skipped | Time |
|---|:---:|:---:|:---:|:---:|
| `dev.tabbyrush.desktop.CompositeDesktopLeaveCancelGateTest` | 2 | 0 | 0 | 0.138s |
| `dev.tabbyrush.desktop.DesktopControllerEnvTest` | 4 | 0 | 0 | 0.067s |
| `dev.tabbyrush.desktop.DesktopLifecycleTest` | 8 | 0 | 0 | 0.010s |
| `dev.tabbyrush.desktop.DesktopReminderControllerLifecycleTest` | 3 | 0 | 0 | 0.028s |
| `dev.tabbyrush.desktop.DesktopReminderSchedulerTest` | 6 | 0 | 0 | 0.020s |
| `dev.tabbyrush.desktop.DotEnvLoaderTest` | 5 | 0 | 0 | 0.002s |
| `dev.tabbyrush.desktop.linux.LinuxNotificationAdapterTest` | 4 | 0 | 0 | 0.009s |
| `dev.tabbyrush.desktop.linux.LinuxReminderAdapterTest` | 6 | 0 | 0 | 0.010s |
| `dev.tabbyrush.desktop.linux.LinuxSecretServiceVaultTest` | 6 | 0 | 0 | 0.016s |
| `dev.tabbyrush.desktop.PlatformVaultDispatcherTest` | 5 | 0 | 0 | 0.002s |

---

## 4. Contract Wire Vectors, Security & Hub Tooling Suites

### 4.1 Strict v2 Wire Vector Runner (`node scripts/strict-v2-vector-runner.mjs`)
- Schema fingerprints verified:
  - Strict v1: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
  - Strict v2: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
- Valid vector runs: 10/10 passed (v1 minimal, v2 full deadline/reminder, explicit offsets, boundary leads).
- Negative rejection vectors: 11/11 verified (invalid date formats, missing fields, interval < 15min, count < 1, count > 10, invalid lead minutes).
- **Result**: 23/23 checks passed.

### 4.2 Hub Offline Fixture Smoke Test (`node scripts/hub-live-smoke.mjs --fixture-test`)
- Suite 1 (Health & App Admission): 2/2 passed.
- Suite 2 (Security & Negative Route Isolation): 3/3 passed.
- Suite 3 (Authentication & Access Session): 4/4 passed.
- Suite 4 (Strict Strong Sync Operations): 7/7 passed.
- Suite 5 (Tombstone Deletion & Invariants): 2/2 passed.
- **Result**: 18/18 checks passed.

### 4.3 Static Release Security Audit (`node scripts/release-security-check.mjs --diagnose-source`)
- Manifest cleartext traffic prohibited (`usesCleartextTraffic="false"`): PASS.
- Backup restrictions enforced (`allowBackup="false"`): PASS.
- Exported components minimal (`MainActivity` only): PASS.
- Security XML config files present: PASS.
- Enforced HTTPS release Hub origin: PASS.
- Secret leak scanner (no hardcoded keys/passwords): PASS.
- **Result**: 6/6 checks passed.

### 4.4 Hub Strict Upgrade CLI Test Suite (`node scripts/hub-strict-upgrade.test.mjs`)
- 9 CLI tests: help, missing token, token redaction in logs, fast-fail on 4xx, preflight dry-run, live upgrade polling, interrupted POST recovery, status-only query, mismatch schema validation rejection.
- **Result**: 9/9 passed (duration: 10.86s).

---

## 5. Android Test Suite & Build Verification

### 5.1 Android Debug Unit Tests (`:androidApp:testDebugUnitTest`)
- `dev.tabbyrush.android.AndroidNotificationAdapterTest`: 6 passed (0.041s)
- `dev.tabbyrush.android.BuildConfigTest`: 1 passed (0.001s)
- `dev.tabbyrush.android.CompositeLeaveSchedulerCancelGateTest`: 3 passed (0.080s)
- `dev.tabbyrush.android.ReminderPermissionControllerTest`: 2 passed (0.001s)
- **Result**: 12/12 passed (duration: 0.123s).

### 5.2 Build Packaging Verifications
- `:desktopApp:createDistributable`: SUCCESSFUL (assembly written to `desktopApp/build/compose/binaries/main/app`).
- `:androidApp:assembleDebug`: SUCCESSFUL (64 actionable tasks, assembleDebug APK compiled).

---

## 6. Remediations & Fixes Applied During Validation

During initial test execution, compilation in `:androidApp:compileDebugKotlin` failed:
1. **Compilation Error**:
   - `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:232`: Unresolved reference `AndroidNotificationIntentFactory.DEFAULT_CHANNEL_ID`.
   - **Remediation**: Corrected reference to `AndroidNotificationIntentFactory.CHANNEL_ID`.
2. **Capability State Fidelity Fix**:
   - In `session-view-model.kt:245`: `areNotificationsEnabled` was mapped to `caps.canPostNotifications` instead of `caps.isChannelEnabled`. This collapsed channel-disabled vs app-level permission states.
   - **Remediation**: Corrected mapping to `areNotificationsEnabled = caps.isChannelEnabled`, matching `AndroidNotificationAdapter` logic and preserving dedicated channel status distinction.

---

## 7. Disclosures & Boundaries

1. **Gradle Flags**: All Gradle tasks executed with `--dependency-verification=off` per assignment instructions.
2. **Headless Execution**: Host is Linux x86_64 without active physical display. Packaging tasks (`createDistributable`, `assembleDebug`) verify build integrity; interactive GUI rendering loops, TalkBack/Orca screen reader announcements, and dynamic font scaling were validated via headless semantics/helper unit tests rather than physical screen inspection.
3. **Phase 10 Runtime Acceptance**: Interactive multiplatform smoke tests (live Android/Desktop save/cancel/clear, offline restart, window resize layout, TalkBack/Orca focus traversal, and live alarm firing on hardware) remain marked **acceptance pending** for execution in Phase 11 platform acceptance matrix.
4. **Platform Scope**: Windows-specific toast notification integration (`WindowsReminderAdapterTest`) was unverified on this Linux runner.

---

## 8. Summary & Pass Counts

- **Total Heterogeneous Checks Executed**: 308
- **Passed**: 308 (100%)
- **Failed**: 0
- **Regressions**: 0
- **Status**: PASSED (Ready for Phase 11 adversarial platform testing)

---

## 9. Unresolved Questions

1. Should the date-only reminder anchor (default 09:00) allow user customization per individual task in addition to the global account settings default?
2. In Phase 11 platform acceptance, will Linux desktop notification clicks be routed via a registered DBus action activation handler or remain informational desktop banners?
3. Should Android `session-view-model.kt` (currently 472 LOC) be decomposed into separate controller delegate classes (`ReminderSettingsController`, `TaskEditorController`) before Phase 11 execution?
