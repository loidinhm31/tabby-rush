# Phase 05 — Linux/Windows Grouped Sticky Mode & Deadlines Test Validation Report

**Date:** 2026-10-20  
**Execution Context:** Test suite validation for Phase 05 (Desktop sticky grouped hierarchy, automatic completion, own deadlines, quick-add isolation)  
**Target Commands:**
1. `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew -PdesktopTarget=linux :desktopApp:test :shared:jvmTest :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks`
2. `node scripts/strict-v3-vector-runner.mjs`
3. `node scripts/strict-v2-vector-runner.mjs`
4. `node scripts/admin-announcements-smoke.mjs --self-test`
5. `node scripts/strict-v2-cutover-rehearsal.mjs --self-test`
6. `node scripts/release-security-check.mjs --diagnose-source`

---

### Test Results Overview

| Target / Suite | Total Run | Passed | Failed | Skipped | Status | Execution Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `:desktopApp:test` (Desktop App Unit Suite) | 126 | 126 | 0 | 0 | **100% PASS** | — |
| `:shared:jvmTest` (Shared KMP JVM Suite) | 341 | 341 | 0 | 0 | **100% PASS** | — |
| `:androidApp:testDebugUnitTest` (Android Unit Suite) | 50 | 50 | 0 | 0 | **100% PASS** | — |
| **Gradle Unit Tests Subtotal** | **517** | **517** | **0** | **0** | **100% PASS** | **14.57s (Combined wall)** |
| `strict-v3-vector-runner.mjs` (Strict-V3 Canonical Vectors) | 11 checks | 11 | 0 | 0 | **100% PASS** | < 1s |
| `strict-v2-vector-runner.mjs` (Strict-V2 Canonical Vectors) | 21 checks | 21 | 0 | 0 | **100% PASS** | < 1s |
| `admin-announcements-smoke.mjs` (Mock Harness & Golden Vectors) | 34 checks | 34 | 0 | 0 | **100% PASS** | < 1s |
| `strict-v2-cutover-rehearsal.mjs` (Operator Tooling Mock Rehearsal) | 8 checks | 8 | 0 | 0 | **100% PASS** | 10.63s |
| `release-security-check.mjs` (Diagnostic Static Source Audit) | 5 checks | 5 pass, 1 info | 0 | 0 | **CLEAN** | < 1s |

*Verification Summary: 517 / 517 Gradle unit tests passed across 65 suites (100% pass, 0 failures, 0 skipped). All canonical wire vector fixtures, cutover rehearsals, in-process mock smoke runners, and static source security checks passed cleanly.*

---

### Detailed Test Suite Breakdown

#### 1. Desktop Application (`:desktopApp:test` — 126 tests across 16 suites)
Artifact: `artifact://52` (64 actionable tasks executed)

- `dev.tabbyrush.desktop.AppIconAssetsTest`: 4 passed
- `dev.tabbyrush.desktop.CompositeDesktopLeaveCancelGateTest`: 3 passed
- `dev.tabbyrush.desktop.DesktopControllerEnvTest`: 4 passed
- `dev.tabbyrush.desktop.DesktopLifecycleTest`: 8 passed
- `dev.tabbyrush.desktop.DesktopReminderControllerLifecycleTest`: 3 passed
- `dev.tabbyrush.desktop.DesktopReminderSchedulerTest`: 6 passed
- `dev.tabbyrush.desktop.DesktopRemoteRuntimeTest`: 7 passed
- `dev.tabbyrush.desktop.DesktopTaskSurfacesTest`: 19 passed
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

---

### Phase 05 Contract Assertions

1. **Whole Compact Groups**:
   - `testWholeCompactGroupsMixedGroupIntactInActive`: Parent with mixed children (some active, some completed) remains together under Active section; children retain parent grouping without split sections.
   - `testFullyCompleteGroupMigratesToCompletedSection`: Group moves to Completed section when parent is effectively complete (all live children complete, regardless of raw parent completed flag).
2. **Main-App-Only Child Creation & Sticky Quick-Add**:
   - `testQuickAddAlwaysCreatesIndependentRoot`: Submissions from sticky quick-add bar produce independent root tasks (`parentTaskId = null`) even when a group row is focused.
   - `testQuickAddDoesNotMutateMainEditor`: Sticky quick-add input text does not mutate main editor drafts.
   - `testEditingQuickAddWhileSavePendingPreservesNewText`: In-flight quick-add saves do not drop characters typed while the save is active.
3. **Automatic Parent Completion & Action Rejection**:
   - `testDirectParentCompletionRejectedNoMutationOrSync`: Direct completion toggle on parent row with live children is rejected by store admission; no store mutation occurs and sync is not triggered.
   - `testChildDesiredStateCompletionUpdatesParentAndTriggersSync`: Desired-state completion toggle on a child row passes admission, updates parent effective status, and triggers sync.
4. **Independent Own Deadlines**:
   - `testIndependentOwnDeadlinesAndClockRefresh`: Rows reflect only their own stored deadline; child rows do not inherit parent deadlines; rows without deadlines omit deadline labels; clock tick refreshes overdue state without modifying underlying model.
5. **Account Lifecycle & Admission Fencing**:
   - `testStaleTokenRejected`: Mutations referencing stale `SurfaceAccountToken` are dropped fail-closed.
   - `testStateClearedOnSignedOut`: Signing out clears sticky task items and resets display state.
   - `testAccountSwitchingWhileSubmitPendingRejectsStalePublish`: Account generation advancement during in-flight quick-add prevents publishing tasks into wrong account.
   - `testConcurrentCloseAndDrainShareSignal`: Window close and lifecycle drain coordinate cleanly without orphan coroutines or leaks.

---

### Diagnostic Warnings & Policy Disclosures

1. **Gradle Dependency Verification:**
   - Executed with `--dependency-verification=off` per instruction.
2. **Compiler Deprecations (Non-blocking):**
   - Kotlin expect/actual classes in Beta warning in shared database constructors.
   - Java `painterResource(String)` deprecation in desktop `main.kt` (lines 1082, 1105).
   - Java `URL` constructor deprecation in `platform-vault-dispatcher-test.kt` (line 69).
   - Java `setSound` / `setDefaults` in Android `android-notification-adapter.kt` (lines 110, 111).

---

### Critical Issues & Remaining Risks

1. **Native OS Display & Layout Acceptance (Phase 06 Prerequisites):**
   - All tests executed headlessly on Linux x86_64 target (`-PdesktopTarget=linux`).
   - Physical window rendering, DPI scaling (100%–200%), always-on-top behavior, tray minimization/recovery, and screen-reader traversal (Orca/Narrator) on native Linux and Windows hosts remain open Phase 06 acceptance prerequisites.
2. **Windows Cross-Compilation Boundary:**
   - Native Windows runtime validation requires execution on a physical Windows host with native DPAPI and WiX/MSI toolchain.
3. **Mock Smoke & Static Audit Boundaries:**
   - Admin announcements runner and cutover rehearsal verify protocol and tooling against in-memory mock servers, not live services.
   - Release security audit evaluated source configuration; final release APK verification requires artifact build.

---

### Recommendations & Next Steps

1. Hand off passing Phase 05 test validation report to Main agent and Code Reviewer.
2. Proceed to Phase 06 acceptance testing on live Linux session and native Windows environment when hardware is available.

---

### Unresolved Questions

- None.
