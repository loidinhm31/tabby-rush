# Phase 02 — Migrate Room and Integrate Versioned Sync Test Validation Report (Cycle 3)

**Date:** 2026-10-19  
**Execution Context:** Verification of C1 and H1 review fixes for Phase 02 (Cycle 3)  
**Target:** Shared multiplatform JVM test suite (`:shared:jvmTest`), vector runners, and smoke regression runners  

---

### Test Results Overview

| Test Suite / Runner | Total Run | Passed | Failed | Skipped | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `:shared:jvmTest` (Multiplatform JVM Suite) | 318 | 318 | 0 | 0 | PASS |
| `strict-v3-vector-runner.mjs` | 11 | 11 | 0 | 0 | PASS |
| `strict-v2-cutover-rehearsal.mjs --self-test` | 8 | 8 | 0 | 0 | PASS |
| `admin-announcements-smoke.mjs --self-test` | 34 | 34 | 0 | 0 | PASS |
| **Total Targeted Validations** | **371** | **371** | **0** | **0** | **100% PASS** |

*Note on runner scopes:* Script runners execute in-process mock and fixture assertions (11 canonical v3 vectors + fingerprint; 8 tooling rehearsal checks; 13 announcement golden vectors + 21 mock smoke tests). Live hardware/OS verification requires separate staged gate execution.

---

### Executed Commands & Performance Metrics

1. **Gradle Multiplatform Suite (Fresh Execution):**
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`
   - Result: `BUILD SUCCESSFUL in 22s (16 actionable tasks: 16 executed)`.
   - Metrics: 318 tests passed across 39 test suites; 0 failures; 0 skipped.
2. **Strict V3 Vector Runner:**
   - Command: `node scripts/strict-v3-vector-runner.mjs`
   - Execution time: <1s.
   - Result: 6/6 valid vectors passed, 5/5 rejections verified, canonical schema fingerprint verified (`7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f`).
3. **Strict V2 Cutover Rehearsal:**
   - Command: `node scripts/strict-v2-cutover-rehearsal.mjs --self-test`
   - Execution time: 10.67s.
   - Result: 8/8 rehearsal checks passed (CLI help, missing bearer exit code 1, token redaction, dry-run, interrupted POST recovery, status-only query, column divergence failure, env var token consumption).
4. **Admin Announcements Smoke:**
   - Command: `node scripts/admin-announcements-smoke.mjs --self-test`
   - Execution time: <1s.
   - Result: 13 golden vectors + 21 smoke checks verified against ephemeral mock server (34/34 checks passed).

---

### Coverage Metrics

- Line coverage: Not measured (Jacoco/Kover not configured in `:shared:jvmTest`).
- Suite coverage: 39 shared multiplatform test suites executed.
- Targeted fix coverage:
  - C1 covered by `ContractTransitionTest.testV2ToV3TransitionTranslatesPendingV2IntentsToV3` asserting translation of pending V2 CREATE and UPDATE preserving `deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, and `scheduleToken`.
  - H1 partially covered by `AccountSyncCoordinatorTest.testReapplyConflictOnV3ChildPreservesParentAndSelectsV3Contract` asserting V3 contract selection, parent retention, and non-empty digest for conflicted V3 child.

---

### Failed Tests

- None in the targeted `:shared:jvmTest` or runner suites (0 failed).

---

### Build Status

- Build status: SUCCESS
- Build warnings: Standard Gradle toolchain warnings; no blocking build deprecations or errors.

---

### Review Fixes Verified (Cycle 3)

1. **C1 — V2 to V3 Translation Preserving Pending V2 Temporal Data:**
   - File: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/contract-transition-coordinator.kt:423-455, 472-504`
   - CREATE translation: When `op.contractVersion == OutboxEntity.CONTRACT_VERSION_V2`, all temporal snapshot fields (`deadlineDate`, `deadlineTime`, `scheduleToken`, `reminderEnabled`, `reminderLeadMinutes`, etc.) are retained from the source V2 op.
   - UPDATE translation: When `op.contractVersion == OutboxEntity.CONTRACT_VERSION_V2`, temporal snapshot fields are taken from `op` rather than defaulting to `base`, preventing unsent temporal modifications from being overwritten.
   - Verified via: `ContractTransitionTest.testV2ToV3TransitionTranslatesPendingV2IntentsToV3`.

2. **H1 — Reapply Conflict V3 Contract Selection:**
   - File: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:433-475`
   - Evaluates `isReadyV3 || conflictOp?.contractVersion == OutboxEntity.CONTRACT_VERSION_V3 || parentTaskId != null` to select `CONTRACT_VERSION_V3`.
   - Computes digest using `StrictSyncV3Digest.computeDigest` with `StrictV3TodoData`.
   - Verified via: `AccountSyncCoordinatorTest.testReapplyConflictOnV3ChildPreservesParentAndSelectsV3Contract`.

---

### Multiplatform Test Suite Breakdown (`:shared:jvmTest`: 318 passed)

- `dev.tabbyrush.shared.auth.HubAuthClientTest`: 9 passed
- `dev.tabbyrush.shared.auth.HubAuthSessionTest`: 8 passed
- `dev.tabbyrush.shared.auth.SingleFlightRefreshTest`: 3 passed
- `dev.tabbyrush.shared.contract.AccountKeyTest`: 5 passed
- `dev.tabbyrush.shared.contract.AdversarialSchedulerBoundaryTest`: 7 passed
- `dev.tabbyrush.shared.contract.EndpointPolicyTest`: 8 passed
- `dev.tabbyrush.shared.contract.PersistedScheduleProjectionTest`: 5 passed
- `dev.tabbyrush.shared.contract.ReminderScheduleCalculatorTest`: 9 passed
- `dev.tabbyrush.shared.contract.RemoteContractVectorTest`: 8 passed
- `dev.tabbyrush.shared.contract.StrictDigestTest`: 5 passed
- `dev.tabbyrush.shared.contract.StrictTimeZoneResolverTest`: 7 passed
- `dev.tabbyrush.shared.contract.StrictV2DigestExtendedTest`: 6 passed
- `dev.tabbyrush.shared.contract.StrictV2DigestVectorTest`: 5 passed
- `dev.tabbyrush.shared.contract.StrictV3DigestVectorTest`: 10 passed
- `dev.tabbyrush.shared.contract.TaskDeadlineReminderModelsTest`: 6 passed
- `dev.tabbyrush.shared.contract.TaskSurfaceProjectorTest`: 13 passed
- `dev.tabbyrush.shared.contract.TitlePolicyTest`: 6 passed
- `dev.tabbyrush.shared.data.AdversarialSyncSqliteTest`: 5 passed
- `dev.tabbyrush.shared.data.ReminderOccurrenceStoreTest`: 4 passed
- `dev.tabbyrush.shared.data.ReminderSchedulingCoordinatorSqliteSmokeTest`: 2 passed
- `dev.tabbyrush.shared.data.ReminderTeardownBarrierSqliteTest`: 1 passed
- `dev.tabbyrush.shared.data.RoomAccountStoreTest`: 31 passed
- `dev.tabbyrush.shared.data.RoomMigrationV1ToV2Test`: 1 passed
- `dev.tabbyrush.shared.data.RoomMigrationV2ToV3Test`: 2 passed
- `dev.tabbyrush.shared.data.TaskSurfaceStoreSqliteTest`: 10 passed
- `dev.tabbyrush.shared.data.TaskSurfaceSyncSqliteTest`: 4 passed
- `dev.tabbyrush.shared.remote.RemoteInboxClientTest`: 10 passed
- `dev.tabbyrush.shared.remote.RemoteInboxControllerTest`: 7 passed
- `dev.tabbyrush.shared.remote.RemoteStreamClientTest`: 8 passed
- `dev.tabbyrush.shared.sync.AccountLeaveProtocolTest`: 7 passed
- `dev.tabbyrush.shared.sync.AccountSyncCoordinatorTest`: 13 passed (+1 new test in Cycle 3)
- `dev.tabbyrush.shared.sync.ContractTransitionTest`: 5 passed
- `dev.tabbyrush.shared.sync.HubSyncClientTest`: 12 passed
- `dev.tabbyrush.shared.sync.OutboxReconciliationTest`: 15 passed
- `dev.tabbyrush.shared.sync.ReminderDeliveryGateTest`: 4 passed
- `dev.tabbyrush.shared.sync.StagedFullScanTest`: 19 passed
- `dev.tabbyrush.shared.ui.DateTimePickersTest`: 5 passed
- `dev.tabbyrush.shared.ui.RemoteInboxNavigationTest`: 14 passed
- `dev.tabbyrush.shared.ui.TodoSemanticsTest`: 19 passed

---

### Critical Issues & Remaining Risks

1. **Uncertain V2 Operations Crossing V3 Cutover:**
   - In `ContractTransitionCoordinator.executeTransition()`, the V2→V3 branch executes `store.recoverInterruptedDispatches()`, scans V3 base, translates safe intents, and marks `READY_V3`.
   - However, `getEligibleDispatchBatch()` admits `UNCERTAIN` operations. Freezing preserves their existing V2 identity and digest, but `AccountSyncCoordinator` pushes them via `pushV3` with `toV3Dto`.
   - Risk: Resending an uncertain V2 operation with a V2 digest as a V3 DTO can cause server-side OP_ID_REUSE or digest mismatch, stranding the operation. Receipt reconciliation or quarantine for uncertain V2 operations during V2→V3 transition remains an unaddressed risk.
2. **Digest Assertion Depth in V3 Reapply Test:**
   - `AccountSyncCoordinatorTest.testReapplyConflictOnV3ChildPreservesParentAndSelectsV3Contract` validates `assertTrue(newOp.digest.isNotEmpty())` instead of computing exact expected `StrictSyncV3Digest.computeDigest(...)`.
3. **Untracked Artifacts in Working Directory:**
   - `shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/3.json` and `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v2-to-v3-test.kt` remain untracked in git.

---

### Recommendations

1. Address the V2 `UNCERTAIN` cutover risk: either invoke receipt reconciliation for V2 operations prior to V3 cutover or fail-closed quarantine V2 `UNCERTAIN` ops before entering `READY_V3`.
2. Strengthen `testReapplyConflictOnV3ChildPreservesParentAndSelectsV3Contract` by asserting `assertEquals(expectedV3Digest, newOp.digest)`.
3. Stage and track `3.json` and `room-migration-v2-to-v3-test.kt` in git.

---

### Next Steps

1. Review and address the V2 UNCERTAIN cutover risk in `ContractTransitionCoordinator`.
2. Hand off validation report to Main and proceed with Phase 02 completion review.

---

### Unresolved Questions

- None.
