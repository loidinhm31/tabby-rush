# Code Review: Phase 07 Shared Scheduling, Persisted Eligibility & Account Safety

**Date**: 2026-10-15  
**Reviewer**: Senior Software Engineer (Phase07Reviewer)  
**Plan Target**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-07-shared-scheduling-and-account-safety.md`  
**Quality Score**: 8.5 / 10

---

## 1. Executive Summary

Phase 07 implements the shared Kotlin Multiplatform scheduling calculator, reactive draft-free schedule projector, durable occurrence ledger, fail-closed delivery gate, and atomic Room persistence. The temporal arithmetic, exact 5-minute grace window, and SQLite claim-before-post semantics are cleanly designed and mathematically verified.

A comprehensive review identified two critical issues requiring remediation:
1. Un-gated outbox error state writes that bypass `ReminderDeliveryGate` mutation admission and schedule change notifications.
2. Inconsistent store unfreezing (`isFrozen = false`) if generation read or marker write fails after the delivery gate has already transitioned to `CLOSED`.

Additional warnings and optimizations address perpetual obsolete notification removals, smoke test validity, and scan promotion serialization.

---

## 2. Reviewed Files

- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/reminder-schedule-calculator.kt` (Checked arithmetic, 5-min grace window)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-task-schedule.kt` (Committed schedule domain model)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projector.kt` (Draft-free projection, cancellation barriers, nullable clearing)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/reminder-capability.kt` (OS reminder capabilities)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/reminder-scheduler.kt` (Scheduled alarm handle identity, adapter contract)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/persisted-schedule-snapshot.kt` (Atomic Room base+outbox snapshot model)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/reminder-occurrence-store.kt` (Ledger lifecycle, atomic claim, orphan cancellation)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/schedule-reconciliation-delta.kt` (Delta model)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-dao.kt` (Room atomic claim, posted, skipped, and snapshot transactions)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt` (withMutationLock gating, schedule invalidation flow)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/reminder-delivery-gate.kt` (Lock ordering, drain sync, generation/wipe guards)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/reminder-scheduling-coordinator.kt` (Reconciliation, multi-step delivery, pre-post recheck)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol.kt` (Guarded teardown, fail-closed cancellation)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/{staged-full-scan,outbox-reconciliation,account-sync-coordinator,contract-transition-coordinator}.kt` (Mutation locking integration)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/reminder-schedule-calculator-test.kt`
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projection-test.kt`
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/reminder-delivery-gate-test.kt`
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/data/reminder-occurrence-store-test.kt`
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/recording-native-reminder-adapter.kt`
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol-test.kt`
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-scheduling-coordinator-sqlite-smoke-test.kt`

---

## 3. Critical Issues (MUST FIX)

### 3.1. Un-gated Outbox Error State Writes
- **Location**: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation.kt:308, 353, 374`
- **Impact**: While conflict branches (`STATE_CONFLICT`, `STATE_UNKNOWN_SUPERSEDED`) and accepted reconciliation (`reconcileAccepted`) are protected by `store.withMutationLock`, error writes for malformed accepted revisions (line 308), retryable failures (line 353), and unrecognized statuses (line 374) invoke `store.dao.updateDeliveryState` or `store.dao.upsertOutbox` directly without `withMutationLock`. This bypasses `ReminderDeliveryGate` admission tracking, teardown freeze barriers, and schedule invalidation notifications. Furthermore, line 353 writes a potentially stale `op.copy(...)` snapshot.
- **Remediation**: Wrap all outbox state transitions inside `store.withMutationLock { ... }` and read the current outbox record from `dao` before modifying and updating.

### 3.2. Inconsistent Store Unfreezing on Teardown Abort
- **Location**: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol.kt:106, 110, 120`
- **Impact**: In `performGuardedTeardown()`, `store.deliveryGate?.closeAndDrain(5000L)` transitions the delivery gate to `CLOSED`. If a subsequent step fails (such as `onReadGeneration()`, `currentGen == Long.MAX_VALUE`, or `onWriteGeneration`/`onWriteWipeMarker`), the method resets `store.isFrozen = false`. This leaves the store in an inconsistent state: `isFrozen` is false, but `deliveryGate` is permanently `CLOSED`. Any subsequent user edit calling `withMutationLock` crashes with `IllegalStateException("Mutation admission rejected: delivery gate is CLOSED")`.
- **Remediation**: Teardown failures occurring after `closeAndDrain()` must remain fail-closed with `store.isFrozen = true` so the account cannot accept further writes until properly recovered or re-torn down.

---

## 4. Warnings (SHOULD FIX)

### 4.1. Perpetual Obsolete Notification Removals
- **Location**: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/reminder-occurrence-store.kt:105-109` and `dev/tabbyrush/shared/sync/reminder-scheduling-coordinator.kt:63-65`
- **Impact**: In `syncPlannedOccurrences()`, cancelled tasks with posted platform IDs are placed in `delta.obsoletePlatformNotificationIdsToRemove`. Because `postedPlatformId` is never cleared or marked removed in the SQLite ledger upon successful adapter removal, every subsequent execution of `coordinator.reconcile()` repeatedly attempts to dismiss the same platform notification ID.
- **Remediation**: Acknowledge successful native removal in SQLite (e.g. transition to a `DISMISSED` state or clear `postedPlatformId` only after `adapter.removeOwnedNotification` completes) while maintaining retryability if adapter dismissal fails.

### 4.2. Flawed Wipe Race Smoke Test
- **Location**: `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-scheduling-coordinator-sqlite-smoke-test.kt:179-183`
- **Impact**: In `testConcurrentCallbacksAndWipeRace()`, the wipe marker is asserted against occurrence index 0 after it was already claimed and posted by `call1`. The second call returns `false` simply because occurrence 0 is consumed, not necessarily because the wipe marker was respected.
- **Remediation**: Test the wipe race against an unconsumed pending occurrence (e.g. index 1), and implement a barrier test verifying that in-flight delivery callbacks complete before teardown closes Room.

### 4.3. Staged Scan Promotion Decision Interleaving
- **Location**: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/staged-full-scan.kt:249-397`
- **Impact**: `currentBase` reads and calculation of `basesToUpsert` occur outside `store.withMutationLock`; only `promoteStagingAtomic` is wrapped. A concurrent outbox reconciliation or local edit could commit an updated base revision between the snapshot read and the promotion execution.
- **Remediation**: Revalidate base revisions inside the mutation lock or ensure mutual exclusion across the full promote phase.

---

## 5. Suggestions (NICE TO HAVE)

1. **Dead Code in `ReminderDeliveryGate`**: `drainSignal: CompletableDeferred<Unit>?` is declared and completed in `finally`, but never initialized or awaited in `closeAndDrain()` (which uses a 10ms polling loop). Clean up the unused field or adopt event-driven awaiting.
2. **Missing `@Volatile` on `deliveryGate`**: In `RoomAccountStore`, `var deliveryGate: ReminderDeliveryGate? = null` is accessed across coroutine threads without `@kotlin.jvm.Volatile`, risking visibility delays on multi-core JVM/Android runtimes.
3. **Room Query Optimization**: In `ReminderOccurrenceStore.syncPlannedOccurrences()`, entries are fetched via `dao.getAllLedgerEntries()` and filtered in Kotlin memory. Adding `@Query("SELECT * FROM reminder_delivery_ledger WHERE accountKey = :accountKey AND generation = :generation ORDER BY dueEpochMs ASC")` avoids reading other accounts or generations into memory.
4. **Defensive Conflict Precedence**: In `PersistedScheduleProjector.computeProjection()`, `val headOp = rowOps.firstOrNull()` is checked for conflicts. Using `rowOps.any { it.deliveryState == STATE_CONFLICT || it.deliveryState == STATE_UNKNOWN_SUPERSEDED }` is more robust against unforeseen out-of-order queue states.

---

## 6. Positive Observations

- **Exact Grace Window Precision**: `ReminderScheduleCalculator` enforces checked arithmetic (`safeAdd`, `safeSubtract`, `safeMultiply`) with explicit boundary tests (300,000ms included, 300,001ms skipped).
- **Draft-Free Persistence**: Strict isolation of `editor_drafts` from schedule projections guarantees uncommitted user typing never triggers alarms or cancels active reminders.
- **Atomic Claim Invariants**: Room `@Transaction` in `claimOccurrenceAtomic` guarantees atomic claim-before-post and marks older opportunities skipped within the same SQLite transaction.
- **Safe Handle Identity**: `ScheduledAlarmHandle` contains only deterministic identity keys (`accountKey`, `generation`, `taskId`, `scheduleToken`, `occurrenceIndex`), strictly omitting task titles or user text from OS scheduler payloads.
- **Lock Ordering**: Strict hierarchy (`DeliveryGate -> Store Mutex -> SQLite Transaction`) is consistently followed across all major mutation paths.

---

## 7. Validation Commands & Results

| Validation Type | Command / Harness | Checks Executed | Result |
|---|---|:---:|:---:|
| Shared JVM Unit & Integration | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | 181 tests | PASS (0 failures, 25 test classes) |
| Desktop App Integration | `JAVA_HOME=... ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | 32 tests | PASS (0 failures, 6 test classes) |
| Canonical Contract Vectors | `node scripts/strict-v2-vector-runner.mjs` | 23 vectors | PASS (2 fingerprints, 10 valid, 11 rejections) |
| Offline Protocol Fixture | `node scripts/hub-live-smoke.mjs --fixture-test` | 18 checks | PASS (5 suites against mock server) |
| Static Manifest & Secret Audit | `node scripts/release-security-check.mjs --diagnose-source` | 6 rules | PASS (Clean source audit) |
| Hub Strict Upgrade CLI | `node scripts/hub-strict-upgrade.test.mjs` | 9 tests | PASS (CLI execution & retry recovery) |

*Validation Limitation Note*: Dependency verification (`--dependency-verification=off`) remains required due to unverified Gradle dependencies; static audit checks source code only and does not replace binary APK signing or runtime device KeyStore validation.

---

## 8. Unresolved Questions

1. Should exact alarm permission revocation on Android 14+ trigger an automatic fallback to inexact WorkManager reminders, or present a persistent banner requiring user settings elevation?
2. When teardown aborts due to a drain timeout or cancellation failure, should an explicit user prompt offer a forced emergency wipe, or require an application restart?
3. What is the retention policy for cancelled ledger entries (e.g. prune records older than 30 days during staged scan, or retain indefinitely)?
