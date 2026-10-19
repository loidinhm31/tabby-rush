# Phase 04 — Android Grouped Widget & Deadline Labels Test Suite Validation Report

**Date:** 2026-10-20  
**Execution Context:** Validation of Phase 04 test suites and strict-v3 vectors  
**Target Commands:**
1. `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`
2. `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks`
3. `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :androidApp:compileDebugAndroidTestKotlin --dependency-verification=off`
4. `node scripts/strict-v3-vector-runner.mjs`

---

### Test Results Overview

| Target / Suite | Total Run | Passed | Failed | Skipped | Status | Execution Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `:shared:jvmTest` (Shared KMP JVM Suite) | 341 | 341 | 0 | 0 | **100% PASS** | 28s (wall 28.91s) |
| `:androidApp:testDebugUnitTest` (Android Unit Suite) | 50 | 50 | 0 | 0 | **100% PASS** | 15s (wall 15.15s) |
| `:androidApp:compileDebugAndroidTestKotlin` (Instrumentation Compilation) | 47 tasks | 47 executed | 0 | 0 | **SUCCESS** | 11s (wall 11.40s) |
| `strict-v3-vector-runner.mjs` (Strict-V3 Canonical Vectors) | 11 | 11 | 0 | 0 | **100% PASS** | < 1s |
| **Total Automated Assertions** | **402** | **402** | **0** | **0** | **100% PASS** | **55.5s (Combined wall)** |

*Overall Automated Verification Status: PASSED (Automated test suites passed; launcher acceptance pending).*

---

### Detailed Test Suite Breakdown

#### 1. Shared Module (`:shared:jvmTest` — 341 tests, 41 suites)
Artifact: `artifact://23` (16 actionable tasks: 16 executed)

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

#### 2. Android Module (`:androidApp:testDebugUnitTest` — 50 tests, 8 suites)
Artifact: `artifact://28` (46 actionable tasks: 46 executed)

- `dev.tabbyrush.android.AndroidNotificationAdapterTest`: 6 passed
- `dev.tabbyrush.android.AppIconResourcesTest`: 2 passed
- `dev.tabbyrush.android.BuildConfigTest`: 1 passed
- `dev.tabbyrush.android.CompositeLeaveSchedulerCancelGateTest`: 4 passed
- `dev.tabbyrush.android.ReminderPermissionControllerTest`: 2 passed
- `dev.tabbyrush.android.RemoteInstallCoordinatorTest`: 7 passed
- `dev.tabbyrush.android.TaskSurfaceNotificationTest`: 10 passed
- `dev.tabbyrush.android.TaskWidgetModelTest`: 18 passed
  - `testActionConstantsConformToStandard`: PASSED
  - `testActionParametersSerializationRoundtrip`: PASSED
  - `testAwaitQuiescenceCompletesCleanly`: PASSED
  - `testClearDuringReadNeutralizesPublication`: PASSED
  - `testDisplayStateTransitions`: PASSED
  - `testNoDeadlineInheritanceBetweenParentAndChild`: PASSED
  - `testParentAutomaticProgressCopyAndAbsenceOfToggle`: PASSED
  - `testPartitionSurfaceTodosAncestorDeletionDominance`: PASSED
  - `testPartitionSurfaceTodosEmpty`: PASSED
  - `testPartitionSurfaceTodosOmitsDeleted`: PASSED
  - `testPartitionSurfaceTodosSeparatesAndPreservesOrder`: PASSED
  - `testProjectWidgetItemsAbsoluteOwnDeadlinesAndOverdue`: PASSED
  - `testProjectWidgetItemsFullyCompletedGroupRawCompletedDisagreement`: PASSED
  - `testProjectWidgetItemsMixedGroupInIncompleteSection`: PASSED
  - `testProjectWidgetItemsStandaloneTasks`: PASSED
  - `testRequestCodeDeterminismAndSeparation`: PASSED
  - `testWidgetDisplayStateContentEncapsulatesOnlyCurrentAccount`: PASSED
  - `testWidgetDisplayStatesDoNotHoldTaskTitles`: PASSED

#### 3. Android Instrumentation Compilation (`:androidApp:compileDebugAndroidTestKotlin`)
Artifact: `artifact://34` (47 actionable tasks: 47 executed)
- Status: BUILD SUCCESSFUL (0 errors).
- Verifies instrumented test sources compile against current contracts.

#### 4. Canonical Strict-V3 Vectors (`strict-v3-vector-runner.mjs`)
- Schema fingerprint: `7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f` verified against canonical definition.
- Vectors verified: 6/6 valid vectors passed; 5/5 rejections verified.

---

### Coverage & Verification Analysis

- **Phase 04 Group Projection Coverage:**
  - `testProjectWidgetItemsStandaloneTasks`: Standalone active/completed items project flat with 0 depth and zero child counts.
  - `testProjectWidgetItemsMixedGroupInIncompleteSection`: Whole compact groups stay intact under Incomplete; parent + active and completed sibling children preserved; parent has `hasLiveChildren = true` and `effectiveCompleted = false`.
  - `testProjectWidgetItemsFullyCompletedGroupRawCompletedDisagreement`: Parent with all completed children projects under Completed section with `effectiveCompleted = true` despite raw `completed = false`.
  - `testProjectWidgetItemsAbsoluteOwnDeadlinesAndOverdue`: Date-only formats `Due YYYY-MM-DD`; timed formats `Due YYYY-MM-DD HH:mm (zone)`; overdue active tasks decorated with `Overdue`; completed past deadline never marked overdue.
  - `testNoDeadlineInheritanceBetweenParentAndChild`: Child without deadline does not inherit parent's deadline.
  - `testParentAutomaticProgressCopyAndAbsenceOfToggle`: Parent with live children has progress counts and automatic semantics.
- **Untracked File Disclosure:**
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-projection.kt` contains pure projection helper `projectWidgetItems` bridging Glance widget to shared `TaskHierarchyProjector` and `TaskDeadlinePresentation`. Must be tracked in git commit.

---

### Diagnostic Warnings & Policy Disclosures

1. **Gradle Dependency Verification:**
   - Command ran with `--dependency-verification=off` per instruction.
2. **Compilation Warnings (Non-blocking):**
   - Kotlin expect/actual classes in Beta warning in shared database constructors.
   - Deprecated Java `setSound` and `setDefaults` in `android-notification-adapter.kt`.

---

### Critical Issues & Remaining Risks

1. **Launcher Acceptance Pending (Non-Unit Verification):**
   - Unit tests pass, but live launcher acceptance requires running app on connected Android device/emulator.
   - Pinned widget rendering, responsive 120×120, 240×120, 240×240, 320×360dp scaling, and 200% font accessibility require runtime proof (Phase 06).
2. **Deadline Label Text Truncation Risk:**
   - `task-widget.kt` sets `maxLines = 1` for deadline label. Long timed strings with IANA zone names (`Due YYYY-MM-DD HH:mm (America/New_York) • Overdue`) may truncate on narrow compact sizes or indented children. Multi-line wrapping should be tested on device.
3. **Touch Target Dimensions:**
   - Header Add button and placeholder buttons explicitly declare 48dp height. Task title clickable column relies on Glance row sizing; must verify ≥48dp physical touch target on real launcher.
4. **Test Fixture Observations:**
   - `testClearDuringReadNeutralizesPublication`: Launches coroutine in `runBlocking`; should incorporate explicit entry latch to guarantee in-flight execution prior to clear.

---

### Recommendations & Next Steps

1. Track `androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-projection.kt` into version control.
2. Address deadline label wrapping if on-device launcher test reveals truncation on narrow displays.
3. Execute Phase 06 real launcher device verification on connected emulator.

---

### Unresolved Questions

- None.
