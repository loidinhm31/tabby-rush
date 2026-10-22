# Phase 03 — Shared Hierarchy Semantics & Main UI Test Suite Validation Report

**Date:** 2026-10-19  
**Execution Context:** Validation of Phase 03 test suites across shared, desktopApp, and androidApp modules  
**Target Modules:** `:shared:jvmTest`, `:desktopApp:test`, `:androidApp:testDebugUnitTest`  
**Environment:** Linux x86_64, JDK 21 (`/home/loidinh/.local/share/jvm/jdk-21`), Android SDK (`/home/loidinh/Android/Sdk`), Gradle 8.11.1  

---

### Test Results Overview

| Test Suite / Target | Total Run | Passed | Failed | Skipped | Status | Execution Time |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `:shared:jvmTest` (Shared KMP JVM Suite) | 339 | 339 | 0 | 0 | **100% PASS** | 9s (wall 10.01s) |
| `:desktopApp:test` (Desktop JVM/Linux Suite) | 119 | 119 | 0 | 0 | **100% PASS** | 9s (wall 9.81s) |
| `:androidApp:testDebugUnitTest` (Android Unit Suite) | 43 | 43 | 0 | 0 | **100% PASS** | 10s (wall 10.70s) |
| `strict-v3-vector-runner.mjs` (Canonical V3 Vectors) | 11 | 11 | 0 | 0 | **100% PASS** | < 1s |
| **Total Aggregated Tests** | **512** | **512** | **0** | **0** | **100% PASS** | **29.8s (Combined wall)** |

*Zero failed assertions, zero skipped or disabled tests across all executed tasks.*

---

### Detailed Test Suite Breakdown

#### 1. Shared Module (`:shared:jvmTest` — 339 tests, 41 suites)
Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`
Artifact: `artifact://40` (16 actionable tasks: 16 executed)

- `dev.tabbyrush.shared.auth.HubAuthClientTest`: 9 passed
- `dev.tabbyrush.shared.auth.HubAuthSessionTest`: 8 passed
- `dev.tabbyrush.shared.auth.SingleFlightRefreshTest`: 3 passed
- `dev.tabbyrush.shared.contract.AccountKeyTest`: 5 passed
- `dev.tabbyrush.shared.contract.AdversarialSchedulerBoundaryTest`: 7 passed
- `dev.tabbyrush.shared.contract.EndpointPolicyTest`: 8 passed
- `dev.tabbyrush.shared.contract.PersistedScheduleProjectionTest`: 9 passed *(+4 new Phase 03 hierarchy/barrier tests)*
- `dev.tabbyrush.shared.contract.ReminderScheduleCalculatorTest`: 9 passed
- `dev.tabbyrush.shared.contract.RemoteContractVectorTest`: 8 passed
- `dev.tabbyrush.shared.contract.StrictDigestTest`: 5 passed
- `dev.tabbyrush.shared.contract.StrictTimeZoneResolverTest`: 7 passed
- `dev.tabbyrush.shared.contract.StrictV2DigestExtendedTest`: 6 passed
- `dev.tabbyrush.shared.contract.StrictV2DigestVectorTest`: 5 passed
- `dev.tabbyrush.shared.contract.StrictV3DigestVectorTest`: 10 passed
- `dev.tabbyrush.shared.contract.TaskDeadlinePresentationTest`: 3 passed *(New Phase 03 suite)*
- `dev.tabbyrush.shared.contract.TaskDeadlineReminderModelsTest`: 6 passed
- `dev.tabbyrush.shared.contract.TaskHierarchyProjectorTest`: 8 passed *(New Phase 03 suite)*
- `dev.tabbyrush.shared.contract.TaskSurfaceProjectorTest`: 13 passed
- `dev.tabbyrush.shared.contract.TitlePolicyTest`: 6 passed
- `dev.tabbyrush.shared.data.AdversarialSyncSqliteTest`: 5 passed
- `dev.tabbyrush.shared.data.ReminderOccurrenceStoreTest`: 4 passed
- `dev.tabbyrush.shared.data.ReminderSchedulingCoordinatorSqliteSmokeTest`: 2 passed
- `dev.tabbyrush.shared.data.ReminderTeardownBarrierSqliteTest`: 1 passed
- `dev.tabbyrush.shared.data.RoomAccountStoreTest`: 35 passed *(+4 new Phase 03 parent-child / group deletion tests)*
- `dev.tabbyrush.shared.data.RoomMigrationV1ToV2Test`: 1 passed
- `dev.tabbyrush.shared.data.RoomMigrationV2ToV3Test`: 2 passed
- `dev.tabbyrush.shared.data.TaskSurfaceStoreSqliteTest`: 10 passed
- `dev.tabbyrush.shared.data.TaskSurfaceSyncSqliteTest`: 4 passed
- `dev.tabbyrush.shared.remote.RemoteInboxClientTest`: 10 passed
- `dev.tabbyrush.shared.remote.RemoteInboxControllerTest`: 7 passed
- `dev.tabbyrush.shared.remote.RemoteStreamClientTest`: 8 passed
- `dev.tabbyrush.shared.sync.AccountLeaveProtocolTest`: 7 passed
- `dev.tabbyrush.shared.sync.AccountSyncCoordinatorTest`: 13 passed
- `dev.tabbyrush.shared.sync.ContractTransitionTest`: 6 passed *(+1 new Phase 03 transition test)*
- `dev.tabbyrush.shared.sync.HubSyncClientTest`: 12 passed
- `dev.tabbyrush.shared.sync.OutboxReconciliationTest`: 16 passed *(+1 new Phase 03 fence test)*
- `dev.tabbyrush.shared.sync.ReminderDeliveryGateTest`: 4 passed
- `dev.tabbyrush.shared.sync.StagedFullScanTest`: 19 passed
- `dev.tabbyrush.shared.ui.DateTimePickersTest`: 5 passed
- `dev.tabbyrush.shared.ui.RemoteInboxNavigationTest`: 14 passed
- `dev.tabbyrush.shared.ui.TodoSemanticsTest`: 19 passed

#### 2. Desktop Module (`:desktopApp:test` — 119 tests, 16 suites)
Command: `APPROVED_HUB_ORIGIN=https://api-glean-hub.qm-hub-v001.cloud TABBYRUSH_API_KEY=XoaJSoximq8EhK06IjXAQCOqKqRtM6qy JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks`
Artifact: `artifact://46` (20 actionable tasks: 20 executed)

- `dev.tabbyrush.desktop.AppIconAssetsTest`: 4 passed
- `dev.tabbyrush.desktop.CompositeDesktopLeaveCancelGateTest`: 3 passed
- `dev.tabbyrush.desktop.DesktopControllerEnvTest`: 4 passed
- `dev.tabbyrush.desktop.DesktopLifecycleTest`: 8 passed
- `dev.tabbyrush.desktop.DesktopReminderControllerLifecycleTest`: 3 passed
- `dev.tabbyrush.desktop.DesktopReminderSchedulerTest`: 6 passed
- `dev.tabbyrush.desktop.DesktopRemoteRuntimeTest`: 7 passed
- `dev.tabbyrush.desktop.DesktopTaskSurfacesTest`: 12 passed
- `dev.tabbyrush.desktop.DotEnvLoaderTest`: 9 passed
- `dev.tabbyrush.desktop.PlatformVaultDispatcherTest`: 5 passed
- `dev.tabbyrush.desktop.StickyWindowGeometryTest`: 15 passed
- `dev.tabbyrush.desktop.StickyWindowPreferencesTest`: 7 passed
- `dev.tabbyrush.desktop.linux.LinuxNotificationAdapterTest`: 6 passed
- `dev.tabbyrush.desktop.linux.LinuxReminderAdapterTest`: 6 passed
- `dev.tabbyrush.desktop.linux.LinuxRemoteNotificationAdapterTest`: 18 passed
- `dev.tabbyrush.desktop.linux.LinuxSecretServiceVaultTest`: 6 passed

#### 3. Android Module (`:androidApp:testDebugUnitTest` — 43 tests, 8 suites)
Command: `ANDROID_HOME=/home/loidinh/Android/Sdk JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks`
Artifact: `artifact://49` (46 actionable tasks: 46 executed)

- `dev.tabbyrush.android.AndroidNotificationAdapterTest`: 6 passed
- `dev.tabbyrush.android.AppIconResourcesTest`: 2 passed
- `dev.tabbyrush.android.BuildConfigTest`: 1 passed
- `dev.tabbyrush.android.CompositeLeaveSchedulerCancelGateTest`: 4 passed
- `dev.tabbyrush.android.ReminderPermissionControllerTest`: 2 passed
- `dev.tabbyrush.android.RemoteInstallCoordinatorTest`: 7 passed
- `dev.tabbyrush.android.TaskSurfaceNotificationTest`: 10 passed
- `dev.tabbyrush.android.TaskWidgetModelTest`: 11 passed

---

### Coverage Metrics

- **Line / Branch Coverage:** Not measured directly (JaCoCo/Kover instrumentation not configured in multiplatform Gradle tasks).
- **Suite Coverage:** 65 automated test suites executed across 3 modules.
- **Phase 03 Hierarchy Coverage:**
  - `TaskHierarchyProjectorTest`: Validates standalone roots, effective completion derivation (all children done vs active child), ancestor deletion dominance, missing parent handling, stable encounter order, and context-only parent row filtering.
  - `TaskDeadlinePresentationTest`: Validates date-only deadline presentation, timed deadline presentation with preserved time zones, and null deadlines.
  - `PersistedScheduleProjectionTest`: Validates suppression of parent scheduling during unresolved preservation intent, base projection active tasks with reminders, parent reminder cancellation when all children completed, parent reminder eligibility when any child active, and outbox deadline clearing.
  - `RoomAccountStoreTest`: Validates child creation under existing root when `READY_V3`, rejection beyond two nesting levels, rejection of root-to-child or reparenting, group deletion transaction deleting parent and all children, last-child deletion capturing preceding effective completed / incomplete state, automatic parent completion rejection at mutation boundary, and parent title edits while grouped preserving raw state.

---

### Build Status & Diagnostic Warnings

- **Build Status:** SUCCESSFUL across all three modules (`BUILD SUCCESSFUL in 9s/9s/10s`).
- **Compilation Warnings (Non-blocking):**
  - Kotlin expect/actual classes in Beta warning (`TabbyRushDatabaseConstructor.kt`, `tabbyrush-database.kt`).
  - Deprecated Java `setSound` and `setDefaults` in `androidApp/android-notification-adapter.kt`.
  - Deprecated Compose `painterResource` in `desktopApp/main.kt:1081, 1104`.
  - Deprecated Java `URL` constructor in `desktopApp/platform-vault-dispatcher-test.kt:69`.
  - Android test warning in `ReminderPermissionControllerTest`: check for instance is always true (`Check for instance is always 'true'`).
- **Flag Disclosure:** All tasks ran with `--dependency-verification=off` per instruction. Artifact checksum verification in `gradle/verification-metadata.xml` remains bypassed for local execution.

---

### Critical Issues & Remaining Risks

1. **Compact Surface Effective Completion Consumption:**
   - In `task-surface-projector.kt`, `partitionCompactSections()` partitions groups based on `group.effectiveCompleted`, but yields the original root `Todo` record.
   - If downstream compact surface consumers (Android widget or desktop sticky/tray) render checkboxes or calculate overdue status from `root.completed` instead of effective completion, parent state could diverge from the main UI.
2. **Legacy V2 UNCERTAIN Operation Quarantine:**
   - `testReadyV3AccountFencesUntranslatedLegacyOpsFromDispatch` verifies dispatch fencing in `READY_V3`.
   - Durable quarantine or receipt reconciliation for uncertain legacy ops prior to entering `READY_V3` remains an operational consideration for live migration.
3. **Android Permission Controller Test Depth:**
   - `ReminderPermissionControllerTest` contains 4 warnings where `is` type checks always evaluate to true. Behavioral assertions should replace tautological instance checks.
4. **Environment & Host Boundaries:**
   - Unit tests executed in JVM headless Linux environment. Live Android emulator/device UI rendering and Windows desktop runtime/packaging (Gate G5) were not exercised.

---

### Untracked Implementation Files (Reproducibility)

The following four new files in working tree provide Phase 03 hierarchy projection and deadline presentation logic:
1. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-deadline-presentation.kt`
2. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-hierarchy-projector.kt`
3. `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-deadline-presentation-test.kt`
4. `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-hierarchy-projector-test.kt`

---

### Recommendations & Next Steps

1. In Phase 04 (Android Widget) and Phase 05 (Desktop Sticky), verify that surface adapters pass effective completion rather than raw `root.completed` to rendering components.
2. Refactor `ReminderPermissionControllerTest` to eliminate tautological instance checks and test actual controller state transitions.
3. Reconcile Gradle dependency checksum metadata in `gradle/verification-metadata.xml` to re-enable strict verification.

---

### Unresolved Questions

- None.
