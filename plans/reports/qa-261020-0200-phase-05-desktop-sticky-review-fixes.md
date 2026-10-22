# Phase 05 — Desktop Sticky After Review Fixes Test Validation Report (Cycle 2)

**Date:** 2026-10-20  
**Execution Context:** Post-review verification of Critical 1 fix (readiness collector startup ordering in `DesktopStickyTaskController`), Warning 1 (single projection path via `projectedSections`), Warning 4 (`@Volatile` access tokens), and environment test helper extraction.  
**Target Commands:**
1. `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew -PdesktopTarget=linux :desktopApp:test :shared:jvmTest :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks`
2. `node scripts/strict-v3-vector-runner.mjs`
3. `node scripts/strict-v2-vector-runner.mjs`
4. `node scripts/admin-announcements-smoke.mjs --self-test`
5. `node scripts/strict-v2-cutover-rehearsal.mjs --self-test`
6. `node scripts/release-security-check.mjs --diagnose-source`
7. Canonical script harnesses (`release-security-check.test.mjs`, `bump-version.test.mjs`, `hub-strict-upgrade.test.mjs`, `debug-fault-proxy.test.mjs`, `hub-live-smoke.mjs --fixture-test`, `hub-compatibility-smoke.mjs`)

---

### Test Results Overview

| Target / Suite | Total Run | Passed | Failed | Skipped | Status | Execution Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `:desktopApp:test` (Desktop App Unit Suite) | 127 | 127 | 0 | 0 | **100% PASS** | — |
| `:shared:jvmTest` (Shared KMP JVM Suite) | 341 | 341 | 0 | 0 | **100% PASS** | — |
| `:androidApp:testDebugUnitTest` (Android Unit Suite) | 50 | 50 | 0 | 0 | **100% PASS** | — |
| **Gradle Unit Tests Subtotal** | **518** | **518** | **0** | **0** | **100% PASS** | **12.42s (Combined wall)** |
| `strict-v3-vector-runner.mjs` (Strict-V3 Canonical Vectors) | 11 checks | 11 | 0 | 0 | **100% PASS** | < 1s |
| `strict-v2-vector-runner.mjs` (Strict-V2 Canonical Vectors) | 21 checks | 21 | 0 | 0 | **100% PASS** | < 1s |
| `admin-announcements-smoke.mjs` (Mock Harness & Golden Vectors) | 34 checks | 34 | 0 | 0 | **100% PASS** | < 1s |
| `strict-v2-cutover-rehearsal.mjs` (Operator Tooling Mock Rehearsal) | 8 checks | 8 | 0 | 0 | **100% PASS** | 10.65s |
| Canonical Script Harnesses (Node.js Unit Suites) | 58 tests | 58 | 0 | 0 | **100% PASS** | 11.66s |
| `hub-live-smoke.mjs --fixture-test` (Offline Fixture Smoke) | 18 checks | 18 | 0 | 0 | **100% PASS** | < 1s |
| **Executable Tests / Checks Total** | **668** | **668** | **0** | **0** | **100% PASS** | — |
| `release-security-check.mjs` (Diagnostic Static Source Audit) | 5 audit checks | 5 pass, 1 info | 0 | 0 | **CLEAN** | < 1s |
| `hub-compatibility-smoke.mjs` (Live Server Smoke) | 5 checks | 0 | 5 | 0 | **OFFLINE** | < 1s |

*Verification Summary: 518 / 518 Gradle unit tests passed across 65 suites (100% pass, 0 failures, 0 skipped). All 150 offline mock/fixture/vector checks passed cleanly (668 total executable checks). 5 static audit checks clean. Live compatibility runner unverified due to offline host (no service at localhost:3000).*

---

### Detailed Test Suite Breakdown

#### 1. Desktop Application (`:desktopApp:test` — 127 tests across 16 suites)

- `dev.tabbyrush.desktop.AppIconAssetsTest`: 4 passed
- `dev.tabbyrush.desktop.CompositeDesktopLeaveCancelGateTest`: 3 passed
- `dev.tabbyrush.desktop.DesktopControllerEnvTest`: 4 passed
- `dev.tabbyrush.desktop.DesktopLifecycleTest`: 8 passed
- `dev.tabbyrush.desktop.DesktopReminderControllerLifecycleTest`: 3 passed
- `dev.tabbyrush.desktop.DesktopReminderSchedulerTest`: 6 passed
- `dev.tabbyrush.desktop.DesktopRemoteRuntimeTest`: 7 passed
- `dev.tabbyrush.desktop.DesktopTaskSurfacesTest`: 20 passed (+1 new regression test)
  - `testTokenValidatedSetCompleted`: PASSED
  - `testDrainReopenNewInFlightDrainCycle`: PASSED
  - `testUnfilteredStickySections`: PASSED
  - `testStaleTokenRejected`: PASSED
  - `testIndependentOwnDeadlinesAndClockRefresh`: PASSED
  - `testQuickAddAlwaysCreatesIndependentRoot`: PASSED
  - `testConcurrentCloseAndDrainShareSignal`: PASSED
  - `testStateClearedOnSignedOut`: PASSED
  - `testReadinessStateFlowAndAwaitReadiness`: PASSED
  - `testChildDesiredStateCompletionUpdatesParentAndTriggersSync`: PASSED
  - `testQuickAddTitleValidationAndSubmission`: PASSED
  - `testAdmissionDrainingWithInFlightOperation`: PASSED
  - `testQuickAddDoesNotMutateMainEditor`: PASSED
  - `testFullyCompleteGroupMigratesToCompletedSection`: PASSED
  - `testWholeCompactGroupsMixedGroupIntactInActive`: PASSED
  - `testPublicationAcrossAccountSwitch`: PASSED
  - `testAccountSwitchingWhileSubmitPendingRejectsStalePublish`: PASSED
  - `testDirectParentCompletionRejectedNoMutationOrSync`: PASSED
  - `testEditingQuickAddWhileSavePendingPreservesNewText`: PASSED
  - `testStartupOrderControllerConstructedBeforeAccountAuthentication`: PASSED
- `dev.tabbyrush.desktop.DotEnvLoaderTest`: 9 passed
- `dev.tabbyrush.desktop.LinuxNotificationAdapterTest`: 6 passed
- `dev.tabbyrush.desktop.LinuxReminderAdapterTest`: 6 passed
- `dev.tabbyrush.desktop.LinuxRemoteNotificationAdapterTest`: 18 passed
- `dev.tabbyrush.desktop.LinuxSecretServiceVaultTest`: 6 passed
- `dev.tabbyrush.desktop.PlatformVaultDispatcherTest`: 5 passed
- `dev.tabbyrush.desktop.StickyWindowGeometryTest`: 15 passed
- `dev.tabbyrush.desktop.StickyWindowPreferencesTest`: 7 passed

#### 2. Shared Domain & Persistence (`:shared:jvmTest` — 341 tests across 41 suites)

- `dev.tabbyrush.shared.auth.HubAuthClientTest`: 9 passed
- `dev.tabbyrush.shared.auth.HubAuthSessionTest`: 8 passed
- `dev.tabbyrush.shared.auth.SingleFlightRefreshTest`: 3 passed
- `dev.tabbyrush.shared.contract.AccountKeyTest`: 5 passed
- `dev.tabbyrush.shared.contract.AdversarialSchedulerBoundaryTest`: 7 passed
- `dev.tabbyrush.shared.contract.EndpointPolicyTest`: 8 passed
- `dev.tabbyrush.shared.contract.PersistedScheduleProjectionTest`: 9 passed
- `dev.tabbyrush.shared.contract.ReminderScheduleCalculatorTest`: 9 passed
- `dev.tabbyrush.shared.contract.RemoteContractVectorTest`: 8 passed
- `dev.tabbyrush.shared.contract.StrictDigestTest`: 5 passed
- `dev.tabbyrush.shared.contract.StrictTimeZoneResolverTest`: 7 passed
- `dev.tabbyrush.shared.contract.StrictV2DigestExtendedTest`: 6 passed
- `dev.tabbyrush.shared.contract.StrictV2DigestVectorTest`: 5 passed
- `dev.tabbyrush.shared.contract.StrictV3DigestVectorTest`: 10 passed
- `dev.tabbyrush.shared.contract.TaskDeadlinePresentationTest`: 3 passed
- `dev.tabbyrush.shared.contract.TaskDeadlineReminderModelsTest`: 6 passed
- `dev.tabbyrush.shared.contract.TaskHierarchyProjectorTest`: 8 passed
- `dev.tabbyrush.shared.contract.TaskSurfaceProjectorTest`: 13 passed
- `dev.tabbyrush.shared.contract.TitlePolicyTest`: 6 passed
- `dev.tabbyrush.shared.data.AdversarialSyncSqliteTest`: 5 passed
- `dev.tabbyrush.shared.data.ReminderOccurrenceStoreTest`: 4 passed
- `dev.tabbyrush.shared.data.ReminderSchedulingCoordinatorSqliteSmokeTest`: 2 passed
- `dev.tabbyrush.shared.data.ReminderTeardownBarrierSqliteTest`: 1 passed
- `dev.tabbyrush.shared.data.RoomAccountStoreTest`: 37 passed
- `dev.tabbyrush.shared.data.RoomMigrationV1ToV2Test`: 1 passed
- `dev.tabbyrush.shared.data.RoomMigrationV2ToV3Test`: 2 passed
- `dev.tabbyrush.shared.data.TaskSurfaceStoreSqliteTest`: 10 passed
- `dev.tabbyrush.shared.data.TaskSurfaceSyncSqliteTest`: 4 passed
- `dev.tabbyrush.shared.remote.RemoteInboxClientTest`: 10 passed
- `dev.tabbyrush.shared.remote.RemoteInboxControllerTest`: 7 passed
- `dev.tabbyrush.shared.remote.RemoteStreamClientTest`: 8 passed
- `dev.tabbyrush.shared.sync.AccountLeaveProtocolTest`: 7 passed
- `dev.tabbyrush.shared.sync.AccountSyncCoordinatorTest`: 13 passed
- `dev.tabbyrush.shared.sync.ContractTransitionTest`: 6 passed
- `dev.tabbyrush.shared.sync.HubSyncClientTest`: 12 passed
- `dev.tabbyrush.shared.sync.OutboxReconciliationTest`: 16 passed
- `dev.tabbyrush.shared.sync.ReminderDeliveryGateTest`: 4 passed
- `dev.tabbyrush.shared.sync.StagedFullScanTest`: 19 passed
- `dev.tabbyrush.shared.ui.DateTimePickersTest`: 5 passed
- `dev.tabbyrush.shared.ui.RemoteInboxNavigationTest`: 14 passed
- `dev.tabbyrush.shared.ui.TodoSemanticsTest`: 19 passed

#### 3. Android Application (`:androidApp:testDebugUnitTest` — 50 tests across 8 suites)

- `dev.tabbyrush.android.AndroidNotificationAdapterTest`: 6 passed
- `dev.tabbyrush.android.AppIconResourcesTest`: 2 passed
- `dev.tabbyrush.android.BuildConfigTest`: 1 passed
- `dev.tabbyrush.android.CompositeLeaveSchedulerCancelGateTest`: 4 passed
- `dev.tabbyrush.android.ReminderPermissionControllerTest`: 2 passed
- `dev.tabbyrush.android.RemoteInstallCoordinatorTest`: 7 passed
- `dev.tabbyrush.android.TaskSurfaceNotificationTest`: 10 passed
- `dev.tabbyrush.android.TaskWidgetModelTest`: 18 passed

#### 4. Canonical Wire Vectors & Platform Smoke Assertions

- `node scripts/strict-v3-vector-runner.mjs`:
  - Canonical strict-v3 schema fingerprint `7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f` verified.
  - 6/6 valid preimages match golden SHA-256 digests; 5/5 rejection rules pass.
- `node scripts/strict-v2-vector-runner.mjs`:
  - Canonical strict-v1 (`132f914fc1a9...`) and strict-v2 (`8d641c2de414...`) fingerprints verified.
  - 10/10 valid vectors + 11/11 rejection vectors verified.
- `node scripts/admin-announcements-smoke.mjs --self-test`:
  - 13 golden vectors verified.
  - 21/21 in-memory mock smoke assertions passed.
- `node scripts/strict-v2-cutover-rehearsal.mjs --self-test`:
  - 8/8 operator tooling mock rehearsal checks passed.
- `node scripts/release-security-check.mjs --diagnose-source`:
  - Static source audit: 5 checks passed (manifest cleartext traffic prohibited, backup restricted, exported components restricted to MainActivity/Widget receiver, XML backup rules present, no hardcoded secrets), 1 info (origin HTTPS requirement enforced at release).
- Canonical script suites:
  - `release-security-check.test.mjs`: 24/24 passed.
  - `bump-version.test.mjs`: 13/13 passed.
  - `hub-strict-upgrade.test.mjs`: 13/13 passed.
  - `debug-fault-proxy.test.mjs`: 8/8 passed.
  - `hub-live-smoke.mjs --fixture-test`: 18/18 fixture smoke assertions passed.
- `hub-compatibility-smoke.mjs`:
  - 5/5 connection checks failed (`fetch failed` against `http://127.0.0.1:3000`); live compatibility unverified in offline environment.

---

### Review Defect Verification & Analysis

1. **Critical 1 (Readiness Collector Startup Order & Account Switch Race):**
   - **Initial State**: `DesktopStickyTaskController.init` collector unconditionally blanked state and canceled `activeObserverJob` on first `Ready` emission.
   - **Patch**: Added condition `tokenChanged = prevReadyToken != null && newReadyToken != null && prevReadyToken != newReadyToken`. On initial `Ready` (where `prevReadyToken == null`), `_stickyState.value = _stickyState.value.copy(readiness = r)` preserves active observer.
   - **Verification**: New test `testStartupOrderControllerConstructedBeforeAccountAuthentication` passes, proving initial production startup sequence no longer blanks observer when pre-populated.
   - **Remaining Concurrency Finding**: On an account switch from account A to B, if `startObserving(B)` installs B's observer before the asynchronous readiness collector receives `Ready(B)`, the collector still sees `prevReadyToken (A) != newReadyToken (B)` and executes `activeObserverJob?.cancel()`, potentially killing B's newly installed observer. Full structural safety requires removing observer cancellation/reset from the async collector and serializing observer replacement and account state reset in `startObserving`.
2. **Warning 1 & 3 (Projection Path & Derived Invariant):**
   - UI now consumes `state.projectedSections` directly; removed redundant UI-level projection.
   - `StickyTaskContent` initiates a 30s `LaunchedEffect` loop dispatching `StickyTaskAction.RefreshDeadlines`.
   - Remaining finding: `_stickyState` updates via read-copy-write are not serialized; atomic update helpers or mutex protection are advised for complete invariance.
3. **Warning 4 (`@Volatile` Token Fields):**
   - Added `@kotlin.jvm.Volatile` to `DesktopTaskSurfaceAccess.currentToken` and `DesktopStickyTaskController.activeToken`.
   - Note: Volatile guarantees visibility across threads, but read-modify-write compound operations remain unsynchronized without serialization.
4. **Warning 6 (Env Test Scope):**
   - Extracted `withMockEnv` helper in `desktop-controller-env-test.kt`.

---

### Coverage & Performance Metrics

- **Functional Coverage**: 65 Gradle suites, 518 unit tests; 150 offline vector, rehearsal, mock smoke, and script assertions.
- **Execution Performance**:
  - Gradle test execution (`:desktopApp:test`, `:shared:jvmTest`, `:androidApp:testDebugUnitTest`): 12.42s wall time (64 tasks executed).
  - Script runners: Vector runners & security audit in <1s; cutover rehearsal in 10.65s; script unit test suites in 11.66s.

---

### Build Status & Diagnostic Notices

- **Build Status**: SUCCESS
- **Deprecations (Non-blocking)**:
  - Kotlin expect/actual Beta warning in shared database constructors.
  - Compose `painterResource(String)` deprecation in desktop `main.kt`.
  - Java `URL` constructor deprecation in `platform-vault-dispatcher-test.kt`.
  - Android notification builder `setSound`/`setDefaults` deprecations in `android-notification-adapter.kt`.
- **Dependency Verification**: Executed with `--dependency-verification=off` per assignment instruction.

---

### Remaining Risks & Open Prerequisites

1. **Asynchronous Collector Account-Switch Race**:
   - `desktop-task-surfaces.kt:299-313`: Collector retains `if (tokenChanged) activeObserverJob?.cancel()`. If `startObserving(B)` runs before collector receives `Ready(B)`, B's observer is cancelled. Observer lifecycle management should be unified into `startObserving(token)`.
2. **State Transition Serialization**:
   - Read-copy-write state updates across `refreshDeadlines`, observer emissions, and account transitions lack atomic synchronization.
3. **Phase 06 Acceptance Prerequisites**:
   - Live Linux X11/Wayland display verification, physical Windows native runtime (DPAPI, WiX/MSI), high-DPI scaling (100%-200%), always-on-top window behavior, tray interaction, and Orca/Narrator accessibility tree traversal require interactive target environments.
4. **Live Server Integration**:
   - `hub-compatibility-smoke.mjs` requires a live Hub instance; offline validation relies on `hub-live-smoke.mjs --fixture-test`.

---

### Recommendations & Next Steps

1. In `DesktopStickyTaskController`, remove observer job cancellation and state reset from the async `readiness.collect` handler; perform all observer teardown and state resets synchronously in `startObserving(token)`.
2. Add controlled-interleaving test exercising rapid account switch A -> B where `startObserving(B)` precedes delayed `Ready(B)` collection.
3. Proceed to Phase 06 native desktop acceptance testing on live host sessions.

---

### Unresolved Questions

- None.
