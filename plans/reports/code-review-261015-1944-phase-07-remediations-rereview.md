# Code Review: Phase 07 Remediations Re-Review

**Date**: 2026-10-15  
**Reviewer**: Senior Software Engineer (Phase07Reviewer2)  
**Plan Target**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-07-shared-scheduling-and-account-safety.md`  
**Quality Score**: 9.2 / 10  
**Recommendation**: Conditional Approval (G5 gate unlocked for Phase 08/09 adapter integration)

---

## 1. Executive Summary

This re-review verifies the post-review remediations applied to Phase 07 (Shared scheduling, persisted eligibility, and account safety). Both critical issues identified in the initial review have been resolved, and the three warnings have been addressed.

All test suites pass cleanly under JDK 21 Temurin (181 JVM unit/integration tests, 32 desktop app tests, 23 canonical wire vectors, 18/18 mock Hub smoke checks, clean source security audit, and 9/9 Hub upgrade CLI tests).

---

## 2. Status of Previous Findings

### Critical Issues (from Initial Review)

#### 3.1. Un-gated Outbox Error State Writes
- **Initial Status**: CRITICAL.
- **Remediation Verified**:
  - In `shared/src/commonMain/kotlin/dev/doggo/shared/sync/outbox-reconciliation.kt`:
    - All outbox error state writes (omitted op, rowId mismatch, digest mismatch, invalid acceptedRevision, revision conflict, retryable failure, op ID reuse/invalid record, unrecognized status) are now wrapped inside `store.withMutationLock { ... }`.
    - In the `RETRYABLE_FAILURE` branch, the implementation re-reads `current` from `store.dao.getOutboxEntry(op.opId)` and checks `current.deliveryState == OutboxEntity.STATE_DISPATCHING` under the mutation lock before updating consecutive failures, preventing resurrection of deleted or superseded records.
- **Status**: **RESOLVED**.

#### 3.2. Inconsistent Store Unfreezing on Teardown Abort
- **Initial Status**: CRITICAL.
- **Remediation Verified**:
  - In `shared/src/commonMain/kotlin/dev/doggo/shared/sync/account-leave-protocol.kt`:
    - Once `closeAndDrain()` is invoked in `performGuardedTeardown()`, none of the failure paths (`!drained`, generation read error, generation overflow `Long.MAX_VALUE`, marker persistence failure, scheduler cancel failure, database deletion failure) unfreeze the store.
    - `store.isFrozen` remains `true` (fail-closed), preventing inconsistent mutation admission while the delivery gate is `CLOSED`.
    - Only pre-teardown review abort (`review.hasReviewWork`) unfreezes the store before `closeAndDrain()` is called.
- **Status**: **RESOLVED**.

---

### Warnings (from Initial Review)

#### 4.1. Perpetual Obsolete Notification Removals
- **Initial Status**: WARNING.
- **Remediation Verified**:
  - `doggo-dao.kt` added `@Query("UPDATE reminder_delivery_ledger SET postedPlatformId = NULL, updatedAtEpochMs = :nowEpochMs WHERE accountKey = :accountKey AND generation = :generation AND postedPlatformId = :platformNotificationId")`.
  - Exposed via `ReminderOccurrenceStore.clearPostedNotificationId()`.
  - `ReminderSchedulingCoordinator.reconcile()` clears `postedPlatformId` in the SQLite ledger after successful native removal via `adapter.removeOwnedNotification()`. If the adapter throws, the record is retained as obsolete for subsequent retry.
- **Status**: **RESOLVED**.

#### 4.2. Flawed Wipe Race Smoke Test
- **Initial Status**: WARNING.
- **Remediation Verified**:
  - `ReminderSchedulingCoordinatorSqliteSmokeTest` now asserts wipe protection against a fresh, unconsumed task (`task-wipe`, occurrence index 0) with `wipeMarkerPresent = true`. Verified that `postUnderWipe` returns `false` and the occurrence remains `OUTCOME_PENDING`.
  - Added new dedicated test `ReminderTeardownBarrierSqliteTest` demonstrating that `AccountLeaveProtocol.executeLeave()` enters `DeliveryGateState.CLOSING` and waits for in-flight callbacks admitted under `withDeliveryAdmission` to drain before writing the wipe marker, closing Room, and deleting database files.
  - *Architectural Note*: While this test proves drain-before-closure synchronization, an active race where the wipe marker appears mid-flight between claim and post is protected by coordinator pre-post checks (`lines 113-118`). End-to-end native race proof will be exercised in platform adapters (Phase 08/09).
- **Status**: **RESOLVED**.

#### 4.3. Staged Scan Promotion Decision Interleaving
- **Initial Status**: WARNING.
- **Remediation Verified**:
  - In `shared/src/commonMain/kotlin/dev/doggo/shared/sync/staged-full-scan.kt`, the entire `promoteStaging` method is now defined as `= store.withMutationLock { ... }`.
  - The evaluation of `preScanConfirmed`, base comparison against current SQLite state, divergent revision checks, and atomic promotion are serialized under `withMutationLock`.
- **Status**: **RESOLVED**.

---

### Suggestions & Optimizations Verified
1. **Volatile Visibility**: Added `@kotlin.jvm.Volatile` to `var deliveryGate: ReminderDeliveryGate? = null` in `RoomAccountStore` (line 46).
2. **Persistence Token Verification**: Schedule token read-back verified in SQLite smoke tests (`store.loadTodos().first { ... }.scheduleToken!!`).

---

## 3. Concurrency & Architectural Analysis

### Delivery Gate vs. Mutation Mutex Invariant
The lock hierarchy (`DeliveryGate -> Store Mutex -> SQLite Room Transaction`) is strictly maintained:
1. `RoomAccountStore.withMutationLock` acquires gate mutation admission (`inFlightMutations++`), checks freeze, acquires `mutex.withLock`, executes the block, triggers schedule invalidation flow, and releases.
2. `ReminderSchedulingCoordinator.deliverReminder` acquires delivery admission (`inFlightDeliveries++`), re-reads persisted projection (short `store.mutex`), claims occurrence in SQLite (`@Transaction`), performs pre-post re-check, posts natively, and records outcome.
3. *Trade-off*: In accordance with the Phase 07 design specification, visible delivery and SQLite ledger commits cannot be 100% atomic across OS process boundaries; claim-before-post intentionally trades possible missed alerts on OS crash for avoiding double delivery.

---

## 4. Verification Evidence

| Harness / Suite | Command | Executed | Status |
|---|---|:---:|:---:|
| Shared KMP JVM Suite | `JAVA_HOME=~/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | 15 tasks / 181 tests | **PASS** |
| Desktop App Suite | `JAVA_HOME=~/.local/share/jvm/jdk-21 ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | 19 tasks / 32 tests | **PASS** |
| Coordinator SQLite Smoke & Barrier | `JAVA_HOME=... ./gradlew :shared:jvmTest --tests "*ReminderSchedulingCoordinatorSqliteSmokeTest*" --tests "*ReminderTeardownBarrierSqliteTest*"` | 2 test classes | **PASS** |
| Canonical Contract Vectors | `node scripts/strict-v2-vector-runner.mjs` | 23 vectors | **PASS** |
| Hub Offline Protocol Fixture | `node scripts/hub-live-smoke.mjs --fixture-test` | 18/18 checks | **PASS** |
| Android Static Security Audit | `node scripts/release-security-check.mjs --diagnose-source` | 6 rules | **PASS** (Clean) |
| Hub Strict Upgrade CLI | `node scripts/hub-strict-upgrade.test.mjs` | 9 tests | **PASS** |

*Note on Validation Constraints*:
- Gradle tests require `--dependency-verification=off` due to pre-existing unverified transitive AGP/KSP plugins in `verification-metadata.xml`.
- Source security audit is diagnostic; binary APK signing and runtime Android KeyStore/Linux Secret Service validation are exercised in target phases.

---

## 5. Plan Tracking & Gating Status

- Plan file updated: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-07-shared-scheduling-and-account-safety.md`.
- Gate **G5** is unlocked: Persisted scheduling calculator, projection, durable occurrence ledger, and fail-closed teardown barriers are ready for native adapter integration in Phase 08 (Android Exact Alarm / WorkManager) and Phase 09 (Linux Desktop D-Bus).

---

## 6. Unresolved Questions

1. Should Android exact alarm permission revocation dynamically degrade to inexact WorkManager triggers or block notification scheduling with a persistent status banner? (Owned by Phase 08).
2. What is the scheduled purge policy for historical ledger entries marked `CANCELLED` or `SKIPPED`? (Can be addressed in maintenance/pruning pass).
