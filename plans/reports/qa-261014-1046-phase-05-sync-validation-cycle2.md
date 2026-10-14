# QA Test Report: Phase 05 Sync Validation (Cycle 2 Post C1-C8 Fixes)

**Date:** 2026-10-14
**Target:** Phase 05 Durable Local State & Strict Per-Record Sync Test Suites
**Command:** `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks`
**Environment:** Linux x86_64 Fedora (`/home/loidinh/WS/doggo`), OpenJDK 21.0.8 (`$HOME/.local/share/jvm/jdk-21`), Gradle 8.11.1
**Scope:** Working tree containing Phase 05 shared implementation and common/JVM test suites following C1-C8 remediations.

---

## 1. Test Results Overview

| Test Suite | Class FQCN | Test Doubles / Backend | Total | Passed | Failed | Skipped | Status |
|---|---|---|---:|---:|---:|---:|:---:|
| **Hub Auth Client** | `dev.doggo.shared.auth.HubAuthClientTest` | Mock Ktor Engine | 8 | 8 | 0 | 0 | **PASS** |
| **Hub Auth Session** | `dev.doggo.shared.auth.HubAuthSessionTest` | Fake Vault + Mock Ktor | 8 | 8 | 0 | 0 | **PASS** |
| **Single-Flight Refresh** | `dev.doggo.shared.auth.SingleFlightRefreshTest` | Coroutine Coordinator | 3 | 3 | 0 | 0 | **PASS** |
| **Account Key Contract** | `dev.doggo.shared.contract.AccountKeyTest` | Pure Common Kotlin | 5 | 5 | 0 | 0 | **PASS** |
| **Endpoint Policy Contract** | `dev.doggo.shared.contract.EndpointPolicyTest` | Pure Common Kotlin | 8 | 8 | 0 | 0 | **PASS** |
| **Strict Digest Contract** | `dev.doggo.shared.contract.StrictDigestTest` | Pure Common Kotlin | 5 | 5 | 0 | 0 | **PASS** |
| **Title Policy Contract** | `dev.doggo.shared.contract.TitlePolicyTest` | Pure Common Kotlin | 6 | 6 | 0 | 0 | **PASS** |
| **Room Account Store (JVM)** | `dev.doggo.shared.data.RoomAccountStoreTest` | Real Bundled SQLite (JVM) | 9 | 9 | 0 | 0 | **PASS** |
| **Account Leave Protocol** | `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Fake Store/Session + Callbacks | 7 | 7 | 0 | 0 | **PASS** |
| **Account Sync Coordinator** | `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Fake DAO + Mock Ktor | 5 | 5 | 0 | 0 | **PASS** |
| **Hub Sync Client** | `dev.doggo.shared.sync.HubSyncClientTest` | Mock Ktor Engine | 6 | 6 | 0 | 0 | **PASS** |
| **Outbox Reconciliation** | `dev.doggo.shared.sync.OutboxReconciliationTest` | Fake Store/DAO | 8 | 8 | 0 | 0 | **PASS** |
| **Staged Full Scan** | `dev.doggo.shared.sync.StagedFullScanTest` | Fake DAO + Mock Ktor | 9 | 9 | 0 | 0 | **PASS** |
| **TOTAL (JVM Execution)** | *All 13 Suites* | — | **87** | **87** | **0** | **0** | **PASS (100%)** |

---

## 2. Verified Test Execution Details

### 2.1 RoomAccountStoreTest (9/9 PASS) — Real Bundled SQLite
- `testSaveAndLoadTodosWithBundledSqlite`: PASS. Persists draft + outbox entity to real bundled SQLite on JVM; projects visible Todo with `isPending: true`.
- `testUnsentCreateDeleteCancelsLocally`: PASS. Unsent create cancelled locally before push; zero orphaned rows remain.
- `testProcessRestartRecoveryWithFrozenOutbox`: PASS. Recovers frozen `DISPATCHING` operation across database close/reopen.
- `testReconcileAcceptedAdvancesServerBaseAndClearsOldDraft`: PASS. Acknowledges op; moves state to `server_base`; purges outbox and draft.
- `testReconcileAcceptedPreservesNewerDraft`: PASS. Acknowledging earlier op does not overwrite newer uncommitted draft.
- `testWipeLocalDataClearsAllTables`: PASS. Wipes tables (`editor_drafts`, `outbox`, `server_base`); zero todos loaded.
- `testCloseAndWipeDatabaseFiles`: PASS. Closes connection pool and deletes database files.
- `testRecoverInterruptedDispatchesConvertsToUncertain` *(NEW - C4)*: PASS. Dispatched head interrupted before completion transitions from `STATE_DISPATCHING` to `STATE_UNCERTAIN`.
- `testFrozenStoreRejectsMutations` *(NEW - C7)*: PASS. Fenced store (`isFrozen = true`) rejects mutations fail-closed with `IllegalStateException`.

### 2.2 AccountLeaveProtocolTest (7/7 PASS)
- `testLeaveGuardedWhenReviewWorkPending`: PASS. Blocks leave when uncommitted drafts or conflicts exist.
- `testLeaveProceedsWhenZeroUnresolvedWork`: PASS. Teardown sequence: write wipe marker -> cancel scheduler -> delete DB -> clear wipe marker; session transitions to `UNAUTHENTICATED`.
- `testDiscardAndLeaveProceedsWithPendingWork`: PASS. Explicit `discardAndLeave` overrides review guard and executes clean teardown.
- `testMarkerWriteFailureAbortsTeardown`: PASS. Wipe marker I/O failure aborts teardown immediately before DB deletion.
- `testDatabaseDeletionFailureKeepsWipeMarkerFailClosed`: PASS. Failure during DB file deletion keeps wipe marker intact for startup cleanup.
- `testSchedulerCancelFailureAbortsTeardownFailClosed` *(NEW - C7)*: PASS. Exception in scheduler cancellation aborts teardown without deleting database files.
- `testCompletePendingWipeOnStartup` *(NEW - C7)*: PASS. Detects pending wipe marker on startup; deletes residual database artifacts, clears vault, and clears marker.

### 2.3 OutboxReconciliationTest (8/8 PASS)
- `testEligibleBatchFifoOrder`: PASS. Selects earliest pending op per distinct `rowId`, respecting sequential order.
- `testExactCasAckAdvancesBaseAndRemovesOutbox`: PASS. CAS ack updates `server_base` and removes outbox entry.
- `testAckPreservesNewerDraft`: PASS. Clears draft only if `draft.localRevision <= outbox.localRevision`.
- `testUncertainDeliveryConvertsSubsequentConflictToUnknownSuperseded`: PASS. Outbox marked `UNCERTAIN` receiving conflict converts to `UNKNOWN_SUPERSEDED`.
- `testStandardConflictPreservesIntentForReview`: PASS. Fresh pending op with conflict transitions to `CONFLICT`.
- `testRetryableFailureIncrementsFailuresAndRetainsUncertain`: PASS. On retryable failure, op retained as `UNCERTAIN` and failure counter incremented.
- `testSuccessorRevisionFrozenAtDispatchAgainstCurrentBase` *(NEW - C2/C5)*: PASS. `prepareAndFreezeBatch` freezes `expectedRevision` against current `server_base` (1L) at dispatch, assigns `activeEpoch`, and computes strict digest.
- `testFifoHeadConflictBlocksSuccessorDispatch` *(NEW - C3)*: PASS. When head op for a row is `CONFLICT`, subsequent `PENDING` op is withheld from eligible batch.

### 2.4 StagedFullScanTest (9/9 PASS)
- `testFullScanMultiPageSuccess`: PASS. Ingests multi-page scan records into staging store and promotes to `server_base`.
- `testCursorLoopDetectionAborts`: PASS. Aborts scan when identical cursor token repeats with `hasMore: true`.
- `testMissingPreScanConfirmedIdentityTriggersConsistencyError`: PASS. Catches omission of pre-scan confirmed row identity; aborts promotion with `SafeError.ResetRequired`.
- `testMonotonicPromotionHigherRevisionReplaces`: PASS. Promotes server record with higher revision (`rev 2 > rev 1`).
- `testMonotonicPromotionEqualRevisionDifferingLogicalDataFails`: PASS. Divergent logical data at equal revision triggers `SafeError.ProtocolIncompatible`.
- `testMonotonicPromotionLowerRevisionRetainsLocal`: PASS. Server record with lower revision ignored.
- `testActiveRecordWithMissingDataFails` *(NEW - C8)*: PASS. Active record (`deleted: false`) with null payload rejected fail-closed as `SafeError.ProtocolIncompatible`.
- `testRowIdOrderViolationFails` *(NEW - C8)*: PASS. Out-of-order `rowId` stream (decreasing sort) rejected as `SafeError.ProtocolIncompatible`.
- `testEmptyPageWithHasMoreFails` *(NEW - C8)*: PASS. Non-progress empty record page with `hasMore: true` rejected as `SafeError.ProtocolIncompatible`.

### 2.5 HubSyncClientTest (6/6 PASS)
- `testBootstrapSuccess`: PASS. Validates `POST /api/v1/sync/doggo/strong/bootstrap` with Dual-Auth (`Authorization: Bearer <jwt>`, `X-API-Key`, `X-App-Id`).
- `testBootstrapProtocolMismatchFails`: PASS. Server non-"strict-v1" protocol rejected.
- `testPushSuccess`: PASS. Serializes push batch; parses 200 OK response and per-record operation results.
- `testPushBatchBoundsEnforcedClientSide`: PASS. Batch limit `<= 100` and unique `rowId` rules enforced.
- `testPushErrorEnvelopesMapped`: PASS. HTTP 409 -> `ResetRequired`, HTTP 426 -> `ProtocolIncompatible`, HTTP 503 -> `Network`.
- `testPullSuccessAndPagination`: PASS. Deserializes paginated pull stream.

### 2.6 AccountSyncCoordinatorTest (5/5 PASS)
- `testTriggerSyncSuccess`: PASS. Full sync round: bootstrap -> pull -> push -> pull.
- `testEpochMismatchTransitionsToResetRequired`: PASS. Epoch mismatch transitions to `RESET_REQUIRED`.
- `testConflictResolutionDiscard`: PASS. Drops conflicted head outbox row.
- `testConflictResolutionReapply`: PASS. Enqueues new operation targeting current server revision.
- `testConflictResolutionCopyAsNew`: PASS. Generates new Todo with fresh UUID at revision 0.

### 2.7 Contract & Auth Test Suites (43/43 PASS)
- `HubAuthClientTest`: 8/8 PASS. Normalization, registration, login, token refresh, `/me` access check, access requests, dual-auth headers, HTML error envelope stripping.
- `HubAuthSessionTest`: 8/8 PASS. Authenticated transition, access pending transition, user ID verification, offline restore, no automatic POST replay on 401, logout cleanup, token race fencing, memory retention on vault write failure.
- `SingleFlightRefreshTest`: 3/3 PASS. Concurrent refresh deduplication, failure propagation, exception wrapping.
- `AccountKeyTest`: 5/5 PASS. SHA-256 formatting, determinism, framing collision prevention, invalid hex rejection.
- `EndpointPolicyTest`: 8/8 PASS. HTTPS origin enforcement, localhost allowances in debug, cleartext rejection in release, URL normalization.
- `StrictDigestTest`: 5/5 PASS. NIST vectors, deterministic framing, tombstone vectors, field sensitivity.
- `TitlePolicyTest`: 6/6 PASS. Astral emoji counting, 200 scalar boundary, surrogate rejection, whitespace trimming.

---

## 3. C1-C8 Blocker Remediation Status & Code Analysis

| Ref | Code Review Defect | Fix Implemented | Validation Evidence | Remaining Risk / Note |
|---|---|---|---|---|
| **C1** | No transactions across store mutations | Added `@Transaction` DAO methods (`saveTodoAtomic`, `deleteTodoAtomic`, `cancelUnsentCreateAtomic`, `reconcileAcceptedAtomic`, `promoteStagingAtomic`) + store mutex | Verified in `RoomAccountStoreTest` (bundled SQLite) | `reapplyConflict` and `discardConflict` in coordinator remain multi-step non-atomic writes. |
| **C2** | Successor expectedRevision frozen at enqueue | `prepareAndFreezeBatch` freezes `expectedRevision` against current `server_base` at dispatch | `OutboxReconciliationTest.testSuccessorRevisionFrozenAtDispatchAgainstCurrentBase` | Need end-to-end multi-op sequence test (create-in-flight -> edit -> predecessor ack -> successor dispatch). |
| **C3** | FIFO head bypass (conflict head allowed successor dispatch) | `filterEligibleOutboxBatch` checks row head state; non-eligible head blocks subsequent ops on same rowId | `OutboxReconciliationTest.testFifoHeadConflictBlocksSuccessorDispatch` | Head filtering validated against Fake DAO. |
| **C4** | No DISPATCHING -> UNCERTAIN recovery on restart/crash | `RoomAccountStore.recoverInterruptedDispatches()` resets orphaned DISPATCHING rows to UNCERTAIN | `RoomAccountStoreTest.testRecoverInterruptedDispatchesConvertsToUncertain` | Direct method call verified; coordinator startup wiring needs end-to-end lifecycle test. |
| **C5** | Frozen epoch mismatch for pre-bootstrap ops | Dispatch freeze binds `activeEpoch` and recomputes strict digest before dispatch | Tested in `testSuccessorRevisionFrozenAtDispatchAgainstCurrentBase` | UNCERTAIN retries must retain original epoch and digest without re-binding. |
| **C6** | FIFO order keyed on caller clock | Queries order by monotonic `localRevision` counter | Verified in `OutboxReconciliationTest` | `reapplyConflict` still injects `System.currentTimeMillis()` for `localRevision`, mixing clock with counter. |
| **C7** | Leave not fenced; teardown not crash-safe | `store.isFrozen` rejects mutations; fail-closed on cancel failure; startup wipe completion | `AccountLeaveProtocolTest` (3 tests) + `RoomAccountStoreTest.testFrozenStoreRejectsMutations` | Durable generation counter persistence deferred to Phase 07. |
| **C8** | Scan accepted invalid rows silently | Strict validation on `deleted=false && data==null`, strictly ascending `rowId` ordering, empty page `hasMore: true` loop check | `StagedFullScanTest` (3 tests) | Comprehensive validation in place. |

---

## 4. Coverage Metrics & Test Execution Performance

- **Coverage Metrics:**
  - Test suites: 13/13 passing (100%).
  - Total test methods: 87/87 passing (100%).
  - Zero test failures, zero flaky test detections.
  - Line/branch coverage analysis: Explicit coverage runner (Jacoco/Kover) not configured in Gradle build script; test assertions exercise primary success and fail-closed error paths.
- **Performance Metrics:**
  - Wall-clock time: 7.0 seconds (`--rerun-tasks`).
  - Actionable tasks: 12 executed.
  - Zero slow-running tests (>2s) identified; coroutine tests execute via virtual time test dispatchers.

---

## 5. Build Status & Integrity Notices

- **Build Status:** BUILD SUCCESSFUL in 7s.
- **Dependency Verification Notice:**
  - Build requires `--dependency-verification=lenient`.
  - Gradle logs report 149-251 unverified transitive artifacts across `classpath`, `kspKotlinJvmProcessorClasspath`, `jvmTestCompileClasspath`, and `jvmTestRuntimeClasspath` (Android Gradle Plugin, KSP, Compose, Kotlin tooling).
  - Checksum metadata reconciliation in `gradle/verification-metadata.xml` scheduled for Phase 07.

---

## 6. Critical Issues & Blocking Items

1. **Conflict Resolution Atomicity (C1/W2):**
   `reapplyConflict` executes `deleteOutbox(conflictOp.opId)` and `upsertOutbox(...)` as separate non-atomic calls without a DAO `@Transaction` wrapper. A crash between these calls destroys the queued intent.
2. **Local Revision Counter Mixing (C6/W2):**
   `reapplyConflict` sets `localRevision = System.currentTimeMillis()`, mixing wall-clock milliseconds with the monotonic integer counter (`localRevisionCounter`). This can violate draft-reconciliation ordering invariants.
3. **Unconditional Draft Deletion on Discard (W2):**
   `discardConflict` unconditionally deletes draft at `store.dao.deleteDraft(rowId)` even if a pending successor owns a newer draft.
4. **Android Platform Parity:**
   Android instrumented test suites (`RoomAccountStoreTest` on Android SQLite and `AndroidKeystoreVaultTest`) require emulator environment (Phase 07).

---

## 7. Recommendations

1. Move `reapplyConflict` and `discardConflict` mutations into a dedicated Room `@Transaction` DAO method in `DoggoDao` or `RoomAccountStore` using the incremented `localRevisionCounter`.
2. Safeguard `discardConflict` to delete draft only when `draft.localRevision <= conflictOp.localRevision`.
3. Add a multi-operation test covering: create in-flight -> concurrent local edit -> predecessor acknowledgment -> successor dispatch.
4. Update `gradle/verification-metadata.xml` with trusted checksums during Phase 07 to eliminate `--dependency-verification=lenient`.

---

## 8. Unresolved Questions

- None.
