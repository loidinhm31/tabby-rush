# QA Test Report: Phase 05 Sync Validation (Cycle 3 Post N1-N3 Fixes)

**Date:** 2026-10-14  
**Target:** Phase 05 Durable Local State & Strict Per-Record Sync Test Suites  
**Command:** `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks`  
**Environment:** Linux x86_64 Fedora (`/home/loidinh/WS/doggo`), OpenJDK 21.0.8 (`$HOME/.local/share/jvm/jdk-21`), Gradle 8.11.1  
**Scope:** Working tree containing Phase 05 shared implementation and common/JVM test suites following N1-N3 remediations.

---

## Sequential Thinking Problem Decomposition

1. **Scope & Pre-requisite Verification:**
   - Review Cycle 2 identified critical defects N1 (UNCERTAIN retry re-frozen against newer base breaking idempotent retry & UnknownSuperseded transition), N2 (dispatch freeze overwriting concurrent coalesced edits without optimistic concurrency check), and N3 (scheduler cancel gate failure leaving wipe marker on disk while unfreezing store).
   - Expected test count: 89 tests across 13 suites (expanded from 87 via 2 new reconciliation regression tests).
2. **Execution & Metric Collection:**
   - Ran `:shared:jvmTest --rerun-tasks` with JDK 21.0.8.
   - 12 actionable tasks executed; build succeeded in 15.32s.
   - Parsed stdout/artifact test execution logs; confirmed 89 passed, 0 failed, 0 skipped.
3. **N1-N3 Deep Analysis & Defect Identification:**
   - **N1 Evaluation:** `prepareAndFreezeBatch` now bypasses re-freezing for `STATE_UNCERTAIN` entries, retaining original `kind`, `expectedRevision`, and `digest`. Direct test `testUncertainOpRetainsExactFrozenIdentityOnRetry` validates retention and `UNKNOWN_SUPERSEDED` transition. Edge risk identified: `wasUncertain` flag logic depends on `deliveryState == STATE_UNCERTAIN || consecutiveFailures > 0`; recovered interrupted dispatches starting at `consecutiveFailures == 0` require verification.
   - **N2 Evaluation:** `store.dao.freezeOutboxAtomic` optimistic CAS query verifies `localRevision` match before transition to `DISPATCHING`. Stale snapshots discarded from push batch. Edge risk: Tested on `FakeDoggoDao`; real bundled SQLite Room concurrency race needs end-to-end multi-threaded test.
   - **N3 Evaluation:** `AccountLeaveProtocol.executeLeave()` wraps scheduler cancel gate and attempts `onClearWipeMarker(accountKey)` if cancel fails. **Defect identified:** if `onClearWipeMarker` throws an exception, the failure is swallowed (`catch (_: Exception) {}`) while `store.isFrozen = false` unfreezes store. User can edit live data while un-cleared wipe marker remains on disk, causing fatal data wipe on startup. Teardown must remain fail-closed (`isFrozen = true`) if marker clear fails.
4. **Actionable Recommendations & Next Steps:**
   - Address fail-closed defect in teardown abort path.
   - Guard generation read/bump failures.
   - Reconcile verification-metadata.xml and run Android instrumented tests in Phase 07.

---

## 1. Test Results Overview

| Test Suite | Class FQCN | Test Doubles / Engine | Total | Passed | Failed | Skipped | Status |
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
| **Outbox Reconciliation** | `dev.doggo.shared.sync.OutboxReconciliationTest` | Fake Store/DAO | 10 | 10 | 0 | 0 | **PASS** |
| **Staged Full Scan** | `dev.doggo.shared.sync.StagedFullScanTest` | Fake DAO + Mock Ktor | 9 | 9 | 0 | 0 | **PASS** |
| **TOTAL (JVM Execution)** | *All 13 Suites* | — | **89** | **89** | **0** | **0** | **PASS (100%)** |

---

## 2. Verified Test Execution Details

### 2.1 OutboxReconciliationTest (10/10 PASS) — Includes N1 & N2 Regressions
- `testUncertainOpRetainsExactFrozenIdentityOnRetry` *(NEW - N1)*: PASS. Outbox op previously frozen as `create exp=0` with `STATE_UNCERTAIN` is retried after server advances base to `rev 1`. Validates that op retains `create`, `expectedRevision = 0L`, and identical frozen digest; marks state `DISPATCHING`; on subsequent revision conflict converts state to `STATE_UNKNOWN_SUPERSEDED`.
- `testOptimisticConcurrencyCheckSkipsStaleFreezeOnConcurrentEdit` *(NEW - N2)*: PASS. Outbox op selected at `localRevision = 1L` is concurrently modified in DB to `localRevision = 2L` (coalesced edit). `prepareAndFreezeBatch` executes `freezeOutboxAtomic(opId, 1L, frozenOp)` which detects revision mismatch, rejects stale freeze, and returns empty dispatch batch without clobbering edit.
- `testUncertainDeliveryConvertsSubsequentConflictToUnknownSuperseded`: PASS. `wasUncertain` flag converts conflict to `UNKNOWN_SUPERSEDED`.
- `testStandardConflictPreservesIntentForReview`: PASS. Fresh pending op with conflict transitions to `CONFLICT`.
- `testEligibleBatchFifoOrder`: PASS. Selects earliest pending op per distinct `rowId`, respecting sequential order.
- `testFifoHeadConflictBlocksSuccessorDispatch`: PASS. Non-eligible head op blocks subsequent ops for row.
- `testSuccessorRevisionFrozenAtDispatchAgainstCurrentBase`: PASS. PENDING op frozen against current `server_base` at dispatch.
- `testAckPreservesNewerDraft`: PASS. Clears draft only if `draft.localRevision <= outbox.localRevision`.
- `testExactCasAckAdvancesBaseAndRemovesOutbox`: PASS. CAS ack advances `server_base` and deletes outbox row.
- `testRetryableFailureIncrementsFailuresAndRetainsUncertain`: PASS. Retryable push error keeps op in `STATE_UNCERTAIN`.

### 2.2 AccountLeaveProtocolTest (7/7 PASS) — Includes N3 Regression
- `testSchedulerCancelFailureAbortsTeardownFailClosed` *(UPDATED - N3)*: PASS. When scheduler cancel throws, teardown aborts: `dbDeleted == false`, `store.isFrozen == false`, wipe marker written then rolled back via `onClearWipeMarker`.
- `testCompletePendingWipeOnStartup`: PASS. Detects pending wipe marker on startup; deletes residual database artifacts, clears vault, and clears marker.
- `testLeaveProceedsWhenZeroUnresolvedWork`: PASS. Clean teardown sequence: write wipe marker -> cancel scheduler -> delete DB -> clear wipe marker; session transitions to `UNAUTHENTICATED`.
- `testLeaveGuardedWhenReviewWorkPending`: PASS. Blocks leave when uncommitted drafts or conflicts exist.
- `testDiscardAndLeaveProceedsWithPendingWork`: PASS. Explicit `discardAndLeave` overrides review guard and executes clean teardown.
- `testMarkerWriteFailureAbortsTeardown`: PASS. Wipe marker I/O failure aborts teardown immediately before DB deletion.
- `testDatabaseDeletionFailureKeepsWipeMarkerFailClosed`: PASS. Failure during DB file deletion keeps wipe marker intact for startup cleanup.

### 2.3 RoomAccountStoreTest (9/9 PASS) — Real Bundled SQLite on JVM
- `testSaveAndLoadTodosWithBundledSqlite`: PASS. Persists draft + outbox entity to real bundled SQLite on JVM; projects visible Todo with `isPending: true`.
- `testUnsentCreateDeleteCancelsLocally`: PASS. Unsent create cancelled locally before push; zero orphaned rows remain.
- `testProcessRestartRecoveryWithFrozenOutbox`: PASS. Recovers frozen `DISPATCHING` operation across database close/reopen.
- `testReconcileAcceptedAdvancesServerBaseAndClearsOldDraft`: PASS. Acknowledges op; moves state to `server_base`; purges outbox and draft.
- `testReconcileAcceptedPreservesNewerDraft`: PASS. Acknowledging earlier op does not overwrite newer uncommitted draft.
- `testWipeLocalDataClearsAllTables`: PASS. Wipes tables (`editor_drafts`, `outbox`, `server_base`); zero todos loaded.
- `testCloseAndWipeDatabaseFiles`: PASS. Closes connection pool and deletes database files.
- `testRecoverInterruptedDispatchesConvertsToUncertain`: PASS. Dispatched head interrupted before completion transitions from `STATE_DISPATCHING` to `STATE_UNCERTAIN`.
- `testFrozenStoreRejectsMutations`: PASS. Fenced store (`isFrozen = true`) rejects mutations fail-closed with `IllegalStateException`.

### 2.4 StagedFullScanTest (9/9 PASS)
- `testFullScanMultiPageSuccess`: PASS. Ingests multi-page scan records into staging store and promotes to `server_base`.
- `testCursorLoopDetectionAborts`: PASS. Aborts scan when identical cursor token repeats with `hasMore: true`.
- `testMissingPreScanConfirmedIdentityTriggersConsistencyError`: PASS. Catches omission of pre-scan confirmed row identity; aborts promotion with `SafeError.ResetRequired`.
- `testMonotonicPromotionHigherRevisionReplaces`: PASS. Promotes server record with higher revision.
- `testMonotonicPromotionEqualRevisionDifferingLogicalDataFails`: PASS. Divergent logical data at equal revision triggers `SafeError.ProtocolIncompatible`.
- `testMonotonicPromotionLowerRevisionRetainsLocal`: PASS. Server record with lower revision ignored.
- `testActiveRecordWithMissingDataFails`: PASS. Active record (`deleted: false`) with null payload rejected fail-closed as `SafeError.ProtocolIncompatible`.
- `testRowIdOrderViolationFails`: PASS. Decreasing `rowId` ordering rejected as `SafeError.ProtocolIncompatible`.
- `testEmptyPageWithHasMoreFails`: PASS. Non-progress empty record page with `hasMore: true` rejected as `SafeError.ProtocolIncompatible`.

### 2.5 HubSyncClientTest (6/6 PASS)
- `testBootstrapSuccess`: PASS. Validates `POST /api/v1/sync/doggo/strong/bootstrap` with Dual-Auth.
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
- `HubAuthClientTest`: 8/8 PASS.
- `HubAuthSessionTest`: 8/8 PASS.
- `SingleFlightRefreshTest`: 3/3 PASS.
- `AccountKeyTest`: 5/5 PASS.
- `EndpointPolicyTest`: 8/8 PASS.
- `StrictDigestTest`: 5/5 PASS.
- `TitlePolicyTest`: 6/6 PASS.

---

## 3. N1-N3 Review Blocker Remediation Status & Code Analysis

| Ref | Blocker | Fix Implemented | Validation Evidence | Remaining Risk / Note |
|---|---|---|---|---|
| **N1** | UNCERTAIN retry re-frozen against newer base breaking idempotent retry & UnknownSuperseded transition | `prepareAndFreezeBatch` skips re-computation for `deliveryState == STATE_UNCERTAIN`, keeping original `kind`, `expectedRevision`, `epoch`, and `digest`. Marks state `DISPATCHING`. Reconcile checks `wasUncertain = op.deliveryState == STATE_UNCERTAIN \|\| op.consecutiveFailures > 0`. | `OutboxReconciliationTest.testUncertainOpRetainsExactFrozenIdentityOnRetry` | Edge case: recovered crash dispatch with `consecutiveFailures == 0` when retried becomes `DISPATCHING`; need explicit assertion that `wasUncertain` remains true if `consecutiveFailures == 0`. |
| **N2** | Dispatch freeze overwrites concurrent coalesced edit (lost update) | `freezeOutboxAtomic` enforces optimistic concurrency CAS check `localRevision == expectedLocalRevision`. Stale snapshot discarded from batch; edited row remains untouched. | `OutboxReconciliationTest.testOptimisticConcurrencyCheckSkipsStaleFreezeOnConcurrentEdit` | Tested via `FakeDoggoDao`. Real multi-threaded SQLite Room transaction race test recommended. |
| **N3** | Scheduler cancel failure leaves wipe marker on disk while unfreezing store | Added `onClearWipeMarker` rollback call in scheduler cancel catch block before `store.isFrozen = false`. | `AccountLeaveProtocolTest.testSchedulerCancelFailureAbortsTeardownFailClosed` | **Defect:** `onClearWipeMarker` exception swallowed in `catch (_: Exception) {}`. If marker clearance fails, store is unfrozen while marker remains on disk! Store must remain frozen (`isFrozen = true`) unless marker clear succeeds. |

---

## 4. Coverage Metrics & Test Execution Performance

- **Coverage Metrics:**
  - Test suites: 13/13 passing (100%).
  - Total test methods: 89/89 passing (100%).
  - Zero test failures, zero test skips, zero flaky test detections.
  - Line/branch coverage runner (Kover/JaCoCo) not enabled in Gradle build; assertion coverage exercises happy paths, protocol boundaries, and fail-closed error transitions.
- **Performance Metrics:**
  - Wall-clock execution time: 15.32 seconds (`--rerun-tasks`).
  - Actionable Gradle tasks: 12 executed.
  - Zero slow-running tests (>2s); test coroutines utilize virtual time dispatchers (`runTest`).

---

## 5. Build Status & Warnings

- **Build Status:** BUILD SUCCESSFUL in 15s.
- **Dependency Verification Warning:**
  - Build requires `--dependency-verification=lenient`.
  - 149-174 unverified transitive artifacts noted across `classpath` and `kspKotlinJvmProcessorClasspath` (AGP, KSP, Compose plugins).
  - Checksum metadata reconciliation in `gradle/verification-metadata.xml` tracked for Phase 07.

---

## 6. Critical Issues & Blocking Items

1. **Teardown Rollback Fail-Closed Defect (N3 Secondary Risk):**
   In `account-leave-protocol.kt:120-124`:
   ```kotlin
   try {
       onClearWipeMarker(accountKey)
   } catch (_: Exception) {}
   store.isFrozen = false
   ```
   If `onClearWipeMarker` fails (e.g. disk I/O error or permission failure), the exception is swallowed and `store.isFrozen` is still reset to `false`. The user continues editing while the persistent wipe marker remains on disk. Upon next process launch, `completePendingWipe` detects the marker and destroys the database files and vault credentials. The store must remain frozen (`isFrozen = true`) fail-closed if marker clearance fails.
2. **Generation Monotonicity on Partial Teardown Failure:**
   Generation is bumped via `onWriteGeneration` before scheduler cancellation. If teardown aborts, the generation counter remains incremented on disk, but in-memory state or fallback generation reader defaults to `0` on read error. Monotonic fencing requires verified recovery.
3. **Android Platform Validation Deferral:**
   Android instrumented test suites (`RoomAccountStoreTest` on Android SQLite and `AndroidKeystoreVaultTest`) require an active emulator and are deferred to Phase 07.

---

## 7. Recommendations

1. **Fix Teardown Rollback in `account-leave-protocol.kt`:**
   Wrap marker clear in strict check: only unfreeze `store.isFrozen = false` if `onClearWipeMarker` succeeds without exception. If marker clearance fails, leave `store.isFrozen = true` fail-closed and return `false`.
2. **Add Crash-Recovery Edge Test for N1:**
   Add test for an interrupted dispatch with `consecutiveFailures == 0` that is recovered as `STATE_UNCERTAIN` and subsequently retried, verifying `wasUncertain` remains true during conflict reconciliation.
3. **Add Room SQLite Concurrency Test for N2:**
   Exercise `freezeOutboxAtomic` concurrently against real Room database on JVM SQLite under concurrent coroutine dispatchers.
4. **Reconcile Dependency Verification:**
   Generate trusted checksums in `gradle/verification-metadata.xml` during Phase 07 to enable strict dependency verification.

---

## 8. Next Steps

1. Present Cycle 3 test execution report and N3 secondary defect analysis to engineering team.
2. Apply fail-closed guard to `AccountLeaveProtocol.executeLeave()` marker rollback.
3. Proceed with Phase 05 sign-off and Phase 06 UI state & view model integration upon blocker clearance.

---

## 9. Unresolved Questions

1. Should `AccountLeaveProtocol` treat scheduler cancellation failure as an unrecoverable committed leave (retaining `store.isFrozen = true` and wipe marker for startup wipe) rather than attempting rollback?
2. When will generation persistence backend be wired to platform key-value / preference store (scheduled Phase 05 vs Phase 07)?
