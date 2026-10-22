# QA Test Report: Phase 06 Test Suite Validation & Remediations

- **Target:** Phase 06 — Cross-device acceptance, rollout and documentation
- **Date:** 2026-10-20
- **Runner:** Phase06Tester (Senior QA Engineer)
- **Environment:** Linux x86_64, JDK 21 (`/home/loidinh/.local/share/jvm/jdk-21`), Node v20+, Gradle offline mode (`--dependency-verification=off --offline`)

---

## 1. Test Results Overview

| Suite / Module | Type | Total Run | Passed | Failed | Skipped | Pass Rate | Status |
|---|---|---|---|---|---|---|---|
| `:shared:jvmTest` | Gradle KMP Unit / SQLite | 347 | 347 | 0 | 0 | 100% | PASS |
| `:desktopApp:test` | Gradle JVM Unit / Surface | 129 | 129 | 0 | 0 | 100% | PASS |
| `:androidApp:testDebugUnitTest` | Gradle Android Unit / Widget | 50 | 50 | 0 | 0 | 100% | PASS |
| **Gradle Subtotal** | **All Modules (--rerun-tasks)** | **526** | **526** | **0** | **0** | **100%** | **PASS** |
| `hub-live-smoke.mjs --fixture-test` | Node Offline Protocol Fixture | 18 | 18 | 0 | 0 | 100% | PASS |
| `strict-v2-cutover-rehearsal.mjs --self-test` | Node Rehearsal / Mock Self-Test | 8 | 8 | 0 | 0 | 100% | PASS |
| `admin-announcements-smoke.mjs --self-test` | Node Golden Vectors + Mock Smoke | 34 | 34 | 0 | 0 | 100% | PASS |
| `release-security-check.mjs --diagnose-source` | Static Manifest & Source Audit | 5 | 5 | 0 | 0 | 100% | PASS |
| `strict-v2-vector-runner.mjs` | Node Wire Vectors & Schema | 23 | 23 | 0 | 0 | 100% | PASS |
| `strict-v3-vector-runner.mjs` | Node Wire Vectors & Schema | 12 | 12 | 0 | 0 | 100% | PASS |
| `hub-strict-upgrade.test.mjs` | Node CLI & Migration Tests | 13 | 13 | 0 | 0 | 100% | PASS |
| `release-security-check.test.mjs` etc. | Node Security & Helper Unit Tests | 45 | 45 | 0 | 0 | 100% | PASS |
| **Grand Total** | **All Test Runners & Scripts** | **684** | **684** | **0** | **0** | **100%** | **PASS** |

### Detailed Suite Breakdown

#### `:shared:jvmTest` (347 tests, 41 suites, 0 failures, 0 skipped)
- `HubAuthClientTest`: 9/9 PASS
- `HubAuthSessionTest`: 8/8 PASS
- `SingleFlightRefreshTest`: 3/3 PASS
- `AccountKeyTest`: 5/5 PASS
- `AdversarialSchedulerBoundaryTest`: 7/7 PASS
- `EndpointPolicyTest`: 8/8 PASS
- `PersistedScheduleProjectionTest`: 9/9 PASS
- `ReminderScheduleCalculatorTest`: 9/9 PASS
- `RemoteContractVectorTest`: 8/8 PASS
- `StrictDigestTest`: 5/5 PASS
- `StrictTimeZoneResolverTest`: 7/7 PASS
- `StrictV2DigestExtendedTest`: 6/6 PASS
- `StrictV2DigestVectorTest`: 5/5 PASS
- `StrictV3DigestVectorTest`: 10/10 PASS
- `TaskDeadlinePresentationTest`: 3/3 PASS
- `TaskDeadlineReminderModelsTest`: 6/6 PASS
- `TaskHierarchyProjectorTest`: 8/8 PASS
- `TaskSurfaceProjectorTest`: 13/13 PASS
- `TitlePolicyTest`: 6/6 PASS
- `AdversarialSyncSqliteTest`: 5/5 PASS
- `ReminderOccurrenceStoreTest`: 4/4 PASS
- `ReminderSchedulingCoordinatorSqliteSmokeTest`: 2/2 PASS
- `ReminderTeardownBarrierSqliteTest`: 1/1 PASS
- `RoomAccountStoreTest`: 41/41 PASS (includes W1, W2, preservation, and hierarchy tests)
- `RoomMigrationV1ToV2Test`: 1/1 PASS
- `RoomMigrationV2ToV3Test`: 2/2 PASS
- `TaskSurfaceStoreSqliteTest`: 10/10 PASS
- `TaskSurfaceSyncSqliteTest`: 4/4 PASS
- `RemoteInboxClientTest`: 10/10 PASS
- `RemoteInboxControllerTest`: 7/7 PASS
- `RemoteStreamClientTest`: 8/8 PASS
- `AccountLeaveProtocolTest`: 7/7 PASS
- `AccountSyncCoordinatorTest`: 15/15 PASS (includes Step 7 late-child cleanup)
- `ContractTransitionTest`: 6/6 PASS
- `HubSyncClientTest`: 12/12 PASS
- `OutboxReconciliationTest`: 16/16 PASS
- `ReminderDeliveryGateTest`: 4/4 PASS
- `StagedFullScanTest`: 19/19 PASS
- `DateTimePickersTest`: 5/5 PASS
- `RemoteInboxNavigationTest`: 14/14 PASS
- `TodoSemanticsTest`: 19/19 PASS

#### `:desktopApp:test` (129 tests, 16 suites, 0 failures, 0 skipped)
- `AppIconAssetsTest`: 4/4 PASS
- `CompositeDesktopLeaveCancelGateTest`: 3/3 PASS
- `DesktopControllerEnvTest`: 4/4 PASS
- `DesktopLifecycleTest`: 8/8 PASS
- `DesktopReminderControllerLifecycleTest`: 3/3 PASS
- `DesktopReminderSchedulerTest`: 6/6 PASS
- `DesktopRemoteRuntimeTest`: 7/7 PASS
- `DesktopTaskSurfacesTest`: 22/22 PASS (includes multi-threaded interleaving tests)
- `DotEnvLoaderTest`: 9/9 PASS
- `PlatformVaultDispatcherTest`: 5/5 PASS
- `StickyWindowGeometryTest`: 15/15 PASS
- `StickyWindowPreferencesTest`: 7/7 PASS
- `LinuxNotificationAdapterTest`: 6/6 PASS
- `LinuxReminderAdapterTest`: 6/6 PASS
- `LinuxRemoteNotificationAdapterTest`: 18/18 PASS
- `LinuxSecretServiceVaultTest`: 6/6 PASS

#### `:androidApp:testDebugUnitTest` (50 tests, 8 suites, 0 failures, 0 skipped)
- `AndroidNotificationAdapterTest`: 6/6 PASS
- `AppIconResourcesTest`: 2/2 PASS
- `BuildConfigTest`: 1/1 PASS
- `CompositeLeaveSchedulerCancelGateTest`: 4/4 PASS
- `ReminderPermissionControllerTest`: 2/2 PASS
- `RemoteInstallCoordinatorTest`: 7/7 PASS
- `TaskSurfaceNotificationTest`: 10/10 PASS
- `TaskWidgetModelTest`: 18/18 PASS

---

## 2. Validation of Phase 06 Remediations

### W1: Mixed-Group Deletion Queue Drainage
- **Mechanics:** In `RoomAccountStore.deleteTodoLocallyLocked`, unsent leaf child rows (`isProvablyUnsentLeaf`: child has no server base, only pending create outbox) are segregated into `cancelledChildRowIds`. `dao.deleteGroupAtomic` purges draft and outbox entries locally without generating superfluous DELETE wire operations. Synced roots and synced children stage legitimate DELETE outbox entries. In last-child deletion, `cancelledChildRowId = id` purges unsent leaf without staging an outbox delete.
- **Verification:**
  - `testMixedGroupDeleteCancelsUnsentLeafChildAndStagesSyncedRows` (PASS): Confirmed synced root + unsent child deletion purges child locally and stages root delete without stranding operations.
  - `testUnsentGroupCancelledLocallyWithoutStrandingOps` (PASS): Fully unsent parent+child group purges atomically locally.

### W2: Preservation UPDATE Coalescing & Marker Retention
- **Mechanics:**
  - `buildPreservationUpdateEntities` checks `canCoalesce`: reuses `opId`, `expectedRevision`, and `localRevision` if parent has a pending, non-delete outbox entry.
  - Generates intent mask containing `INTENT_LAST_CHILD_PRESERVATION` and `INTENT_COMPLETED`.
  - In `saveTodoLocallyInternal`, subsequent manual edits on the parent check `pendingOp.intentMask.contains(INTENT_LAST_CHILD_PRESERVATION)` and preserve the barrier and mask across manual re-edits.
- **Verification:**
  - `testPreservationCoalescingUnsentParentAndChild` (PASS): Pre-ack root + child delete coalesces cleanly.
  - `testPreservationCoalescingSyncedParentPendingUpdate` (PASS): Existing pending UPDATE receives preservation intent without duplicate op.
  - `testManualEditAfterPreservationRetainsPreservationMarker` (PASS): Manual title re-edit coalesces while retaining `LAST_CHILD_PRESERVATION` marker.

### Step 7: Sync-Layer Late-Child Cleanup
- **Mechanics:** `AccountSyncCoordinator.reconcileLateChildren(activeEpoch)` scans server base for surviving active children whose parent row is confirmed deleted/tombstoned in the active epoch. It checks outbox for uncertain/in-flight/conflict ops (skipping if unresolved), and invokes `store.deleteTodoLocally(childId)` to stage clean per-row DELETE. Also cancels unsent offline drafts created under tombstoned parents.
- **Verification:**
  - `testLateChildCleanedUpWhenParentTombstoned` (PASS): Verified child under confirmed parent tombstone receives per-row delete and reconciles.

### Desktop Controller Multi-Threaded Interleaving
- **Mechanics:** `DesktopStickyTaskController.startObserving(token, store)` synchronously cancels previous observer job and resets UI state if account key differs. Readiness collector does not cancel observer job when `activeToken == r.token`. Observer collection checks `currentReadyToken == token && activeToken == token && access.getCurrentToken() == token`.
- **Verification:**
  - `testStartupOrderControllerConstructedBeforeAccountAuthentication` (PASS, 10 iterations under Dispatchers.Default).
  - `testRapidAccountSwitchReadyInterleavingUnderMultiThreadedDispatcher` (PASS, Dispatchers.Default multi-threaded switch without blanking or leaking stale account state).
  - `testQuickAddWithMismatchedTokenIsFenced` (PASS, stale token action rejected).

---

## 3. Coverage Metrics

- **Configured Coverage Tool:** None (JaCoCo and Kover are not integrated in `build.gradle.kts` for multiplatform/desktop/android modules).
- **Line/Branch/Function Coverage:** Not measured by automated Gradle tasks.
- **Manual Assessment:** Critical path coverage (data persistence, migrations, outbox coalescing, sync reconciliation, deadline calculations, and UI projections) is exercised by 526 unit/integration tests and 158 script/vector checks.

---

## 4. Failed Tests

- **Failures:** 0
- **Errors:** 0
- **Flaky Tests Observed:** None across 64 Gradle execution tasks and script runners.

---

## 5. Performance Metrics

- **Gradle Test Execution Time:** 18.42s total (`--rerun-tasks`, all 526 tests compiled and executed from clean state).
- **Node Test & Smoke Runners Time:**
  - `hub-live-smoke.mjs --fixture-test`: 1.3s
  - `strict-v2-cutover-rehearsal.mjs --self-test`: 10.68s
  - `admin-announcements-smoke.mjs --self-test`: 1.4s
  - `release-security-check.mjs --diagnose-source`: 0.2s
  - `strict-v2-vector-runner.mjs`: 0.15s
  - `strict-v3-vector-runner.mjs`: 0.15s
  - `hub-strict-upgrade.test.mjs`: 11.29s
  - `scripts/*.test.mjs`: 0.25s
- **Slowest Gradle Tests Identified:**
  1. `HubAuthClientTest.testGetMeWithBearerToken`: 0.264s
  2. `CompositeDesktopLeaveCancelGateTest.testSurfaceDrainFailureFailsClosed`: 0.152s
  3. `CompositeLeaveSchedulerCancelGateTest.testProductionGateFailsClosedWhenDrainTimesOut`: 0.141s
  4. `DesktopControllerEnvTest.testDesktopControllerWithoutEnvFallsBackToLoopbackInDebug`: 0.125s
  5. `RemoteInstallCoordinatorTest.testTokenRotationIssuesNewGeneration`: 0.115s
  6. `DesktopTaskSurfacesTest.testDirectParentCompletionRejectedNoMutationOrSync`: 0.112s
  7. `DesktopTaskSurfacesTest.testAccountSwitchingWhileSubmitPendingRejectsStalePublish`: 0.104s
  *(All test methods execute in < 300ms)*

---

## 6. Build Status & Diagnostic Notice

- **Build Result:** SUCCESS
- **Diagnostic Modes & Verification Notice:**
  - Gradle executed with `--dependency-verification=off --offline`. Per repo policy, this mode is diagnostic test evidence and does NOT establish supply-chain dependency verification acceptance.
  - `release-security-check.mjs --diagnose-source` represents a **static source/manifest audit**, NOT release APK signing/integrity verification.
  - Smoke tests operated in **offline fixture/mock mode**; live Hub multi-instance MongoDB cluster interoperability remains separate.

---

## 7. Critical Issues & Technical Reservations

*Identified during deep inspection and Advisor review:*

1. **Desktop Observation Initial Snapshot Race (Potential Empty Surface):**
   In `DesktopStickyTaskController.startObserving`, task emissions arriving before `_stickyState.readiness` is set to `Ready(token)` are dropped by the token check `currentReadyToken == token`. When readiness collector later emits `Ready(token)`, it does not re-read or replay the task list. If no subsequent database mutation occurs, sticky task list may remain blank indefinitely.
2. **Desktop Lifecycle Transition Synchronization:**
   `activeObserverJob?.cancel()` requests coroutine cancellation without awaiting completion (`job.join()`). Concurrent transitions between readiness collector and `startObserving` rely on `@Volatile activeToken`, which does not guarantee atomic multi-field state updates across job, token, and UI state.
3. **Late-Child Cleanup Check-Then-Act Race:**
   In `AccountSyncCoordinator.reconcileLateChildren`, checking outbox states (`hasUncertainOrInFlight`, `isUnsentCreate`) occurs before calling `cancelUnsentCreateAtomic` or `deleteTodoLocally`. Without holding the store mutation mutex during the check, a concurrent dispatch or edit could invalidate the precondition.

---

## 8. Recommendations

1. **Fix Desktop Sticky Initial Snapshot Delivery:**
   In `DesktopStickyTaskController`, combine readiness and task observation flows using `combine(access.readiness, store.observeTodos())`, or buffer the latest snapshot and replay it immediately when readiness transitions to `Ready(token)`. Add a unit test forcing task emission before readiness publication.
2. **Synchronize Observer Teardown & Account Fencing:**
   Enforce sequential transition handling in `DesktopStickyTaskController` using a single-threaded actor/mutex or ensuring `activeObserverJob?.cancelAndJoin()` before launching a new observer.
3. **Wrap Late-Child Reconciliation in Mutation Lock:**
   Ensure `reconcileLateChildren` executes within `store.withMutationLock` so candidate checks and outbox deletions/cancellations occur atomically against concurrent dispatchers.
4. **Integrate KMP Code Coverage:**
   Configure `kover` plugin in Gradle root and submodules to produce quantifiable line/branch coverage reports for CI.

---

## 9. Next Steps

1. Remediate the 3 critical concurrency reservations in `DesktopStickyTaskController` and `AccountSyncCoordinator`.
2. Re-run `:desktopApp:test` and `:shared:jvmTest` to confirm fix stability.
3. Retain external release gates:
   - **Gate G2:** Physical Android launcher widget rendering and TalkBack accessibility on hardware/emulator.
   - **Gate G5:** Native Windows and Linux physical desktop display, DPI scaling, and tray integration.
   - **Gate G3:** Live Hub MongoDB replica-set staging cluster upgrade rehearsal and strict-v3 cutover.

---

## 10. Unresolved Questions

1. Should `DesktopStickyTaskController` retain a cached `TaskSections` snapshot in memory to immediately display upon receiving `Ready(token)`, or should it trigger an explicit `store.loadTodos()` fallback?
2. Does `reconcileLateChildren` require a dedicated atomic DAO transaction method (e.g. `reconcileLateChildrenAtomic`), or is wrapping the method in `store.withMutationLock` sufficient for thread safety?
3. What is the scheduled timeline for live staging cluster deployment to execute live strict-v3 cutover rehearsal?
