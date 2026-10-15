# QA Test Execution Report: Phase 07 Shared Scheduling, Persisted Eligibility & Account Safety

**Date**: 2026-10-15  
**Context**: Linux Fedora 44 x86_64 | JDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Gradle 8.11.1 | Node.js v24.16.0  
**Target Phase**: `Phase 07 — Shared scheduling, persisted eligibility and account safety`  
**Working Directory**: `/home/loidinh/WS/doggo`  

---

## 1. Test Results Overview

| Index | Command / Suite | Scope | Category | Total | Pass | Fail | Skip | Status |
|:---:|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Shared KMP JVM test suite | Unit/Integration | 181 | 181 | 0 | 0 | PASS |
| 2 | `JAVA_HOME=... ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | Desktop App test suite | Unit/Integration | 32 | 32 | 0 | 0 | PASS |
| 3 | `node scripts/strict-v2-vector-runner.mjs` | Canonical wire vectors & schema fingerprints | Contract Vectors | 23 | 23 | 0 | 0 | PASS |
| 4 | `node scripts/hub-live-smoke.mjs --fixture-test` | In-process offline fixture smoke suite | Protocol Fixture | 18 | 18 | 0 | 0 | PASS |
| 5 | `node scripts/release-security-check.mjs --diagnose-source` | Static manifest, export, origin, secret rules | Security Audit | 6 | 6 | 0 | 0 | PASS |
| 6 | `node scripts/hub-strict-upgrade.test.mjs` | Hub strict upgrade CLI suite | Integration CLI | 9 | 9 | 0 | 0 | PASS |

**Total**: 269 executed checks / tests. 269 passed, 0 failed, 0 skipped within executed test suites.

---

## 2. Shared JVM Test Breakdown (`:shared:jvmTest`)

- Total Test Classes: 25
- Total Tests: 181
- Passed: 181, Failed: 0, Skipped: 0
- Execution: `BUILD SUCCESSFUL in 7s` (15 actionable tasks executed)

| Test Class | Scope / Target | Passed | Failed | Skipped | Test Time |
|---|---|:---:|:---:|:---:|:---:|
| `dev.doggo.shared.auth.HubAuthClientTest` | Registration, login, token refresh, access request | 9 | 0 | 0 | 0.260s |
| `dev.doggo.shared.auth.HubAuthSessionTest` | Auth state machine, local logout, 401 guard | 8 | 0 | 0 | 0.099s |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | Refresh deduplication, concurrency, safe error | 3 | 0 | 0 | 0.004s |
| `dev.doggo.shared.contract.AccountKeyTest` | Account key framing, determinism, validation | 5 | 0 | 0 | 0.001s |
| `dev.doggo.shared.contract.EndpointPolicyTest` | Origin sanitization, HTTPS enforcement, debug rules | 8 | 0 | 0 | 0.002s |
| `dev.doggo.shared.contract.PersistedScheduleProjectionTest` | Draft-free projection, completion/delete barriers, intent overlay | 5 | 0 | 0 | 0.003s |
| `dev.doggo.shared.contract.ReminderScheduleCalculatorTest` | Checked 64-bit arithmetic, 1..10 bounds, 5-min grace window | 9 | 0 | 0 | 0.007s |
| `dev.doggo.shared.contract.StrictDigestTest` | SHA-256 vectors, deterministic canonical payload | 5 | 0 | 0 | 0.001s |
| `dev.doggo.shared.contract.StrictTimeZoneResolverTest` | Standard time, DST overlap/gap, 23/25h boundary | 7 | 0 | 0 | 0.017s |
| `dev.doggo.shared.contract.StrictV2DigestExtendedTest` | Negative epoch, unicode title/zone, max i64 rev | 6 | 0 | 0 | 0.003s |
| `dev.doggo.shared.contract.StrictV2DigestVectorTest` | Date-only, repeating count, null defaults, DST | 5 | 0 | 0 | 0.002s |
| `dev.doggo.shared.contract.TaskDeadlineReminderModelsTest` | Valid date-only/timed, last reminder bounds, tokens | 6 | 0 | 0 | 0.002s |
| `dev.doggo.shared.contract.TitlePolicyTest` | Length bounds, surrogate validation, astral emoji | 6 | 0 | 0 | 0.002s |
| `dev.doggo.shared.data.ReminderOccurrenceStoreTest` | Ledger persistence, claim-before-post, skipped marking, idempotent sync | 4 | 0 | 0 | 0.009s |
| `dev.doggo.shared.data.ReminderSchedulingCoordinatorSqliteSmokeTest` | Real BundledSQLite integration, alarm scheduling, draft isolation | 2 | 0 | 0 | 0.080s |
| `dev.doggo.shared.data.RoomAccountStoreTest` | BundledSQLite persistence, non-destructive rebasing, schedule token rotation | 22 | 0 | 0 | 0.124s |
| `dev.doggo.shared.data.RoomMigrationV1ToV2Test` | Explicit v1->v2 Room schema migration | 1 | 0 | 0 | 0.014s |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Teardown sequence, scheduler cancel gate, fail-closed wipe | 7 | 0 | 0 | 0.012s |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Upgrade detection, quiescence, receipt rebase, v2 push | 9 | 0 | 0 | 0.038s |
| `dev.doggo.shared.sync.ContractTransitionTest` | Transition coordinator, phase resume, superseded blocks | 4 | 0 | 0 | 0.017s |
| `dev.doggo.shared.sync.HubSyncClientTest` | Bootstrap v2, receipt fetch, push/pull envelopes | 12 | 0 | 0 | 0.033s |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | Optimistic lock, FIFO batch, receipt match, CAS ack | 12 | 0 | 0 | 0.007s |
| `dev.doggo.shared.sync.ReminderDeliveryGateTest` | Mutation/delivery admission, drain signal, generation mismatch guard | 4 | 0 | 0 | 0.005s |
| `dev.doggo.shared.sync.StagedFullScanTest` | Multi-page full scan, monotonic promotion, cursor loop | 14 | 0 | 0 | 0.048s |
| `dev.doggo.shared.ui.TodoSemanticsTest` | Semantics, accessibility contrast, conflict warning copy | 8 | 0 | 0 | 0.016s |

---

## 3. Desktop App Test Breakdown (`:desktopApp:test`)

- Total Test Classes: 6
- Total Tests: 32
- Passed: 32, Failed: 0, Skipped: 0
- Execution: `BUILD SUCCESSFUL in 6s` (19 actionable tasks executed)

| Test Class | Scope / Target | Passed | Failed | Skipped | Test Time |
|---|---|:---:|:---:|:---:|:---:|
| `dev.doggo.desktop.DesktopControllerEnvTest` | Environment bootstrap, hub config parsing | 4 | 0 | 0 | 0.128s |
| `dev.doggo.desktop.DesktopLifecycleTest` | App startup, shutdown hooks, controller dispatch | 8 | 0 | 0 | 0.028s |
| `dev.doggo.desktop.DotEnvLoaderTest` | Local env parsing, comments, precedence | 5 | 0 | 0 | 0.002s |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | Vault resolution across Linux/macOS/Windows | 5 | 0 | 0 | 0.003s |
| `dev.doggo.desktop.linux.LinuxNotificationAdapterTest` | DBus freedesktop notification interface | 4 | 0 | 0 | 0.009s |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | Linux libsecret / secret-service integration | 6 | 0 | 0 | 0.020s |

---

## 4. Contract Vectors & Schema Fingerprints (`strict-v2-vector-runner.mjs`)

- Schema Fingerprints: 2/2 PASS
  - v1 SHA-256: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
  - v2 SHA-256: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
- Valid Vectors: 10/10 PASS
  - Minimal date-only deadline, timed DST overlap, repeating count 10, clear deadline, zero-data delete, migrated null defaults, disabled rule with deadline, unicode title/zone, max i64 rev, negative epoch instant.
- Rejection Vectors: 11/11 PASS
  - Revision out of range, payload on delete, negative created timestamp, negative lead minutes, repetition count out of bounds, interval not null when count=1, interval < 15 min, deadline parameters present without deadlineDate, missing required fields when deadlineDate present, invalid reminder anchor on timed deadlines, last reminder after anchor.

---

## 5. Hub Live Smoke Fixture (`hub-live-smoke.mjs --fixture-test`)

- Suite 1: Health & App Admission (2/2 PASS)
- Suite 2: Security & Negative Route Isolation (3/3 PASS)
- Suite 3: Authentication & Access Session (4/4 PASS)
- Suite 4: Strict Strong Sync Operations (7/7 PASS)
- Suite 5: Tombstone Deletion & Invariants (2/2 PASS)
- Total: 18/18 PASS against offline fixture mock server. (Validates strict-v1 protocol compatibility only; live strict-v2 cluster verification reserved for Phase 11).

---

## 6. Security & Diagnostic Audit (`release-security-check.mjs --diagnose-source`)

- Prohibit cleartext traffic (`usesCleartextTraffic="false"`): PASS
- Restrict backup (`allowBackup="false"`): PASS
- Exported components: Only `MainActivity` exported: PASS
- XML security configs present: PASS
- Release Hub origin HTTPS enforcement: PASS
- Repository source secret audit (0 hardcoded credentials): PASS
- Diagnostic Result: CLEAN (Source code static checks only; binary APK signature and runtime Keystore tests require emulator/device).

---

## 7. Hub Strict Upgrade CLI Suite (`hub-strict-upgrade.test.mjs`)

- Suite 1: CLI Operations & Fault Tolerance (9/9 PASS, ~10.8s)
  - Exit 0 on `--help`
  - Exit nonzero on missing token
  - Sensitive bearer token redaction in logs
  - Fast-fail on HTTP 4xx without polling
  - Preflight dry-run non-mutating
  - Bounded polling upgrade and read-back assertion
  - Idempotent recovery using migration ID
  - `--status-only` query mode
  - Schema column mismatch rejection

---

## 8. Phase 07 Specific Requirements Validation

### 8.1. `ReminderScheduleCalculator`
- **Arithmetic Safety**: Enforces checked 64-bit integer arithmetic (`safeAdd`, `safeSubtract`, `safeMultiply`). Overflows throw `ArithmeticException`. Tested with `Long.MAX_VALUE`.
- **Occurrence Invariants**: Validates 1..10 repeat count bounds. Prohibits occurrences beyond `anchorEpochMs`. Rejects interval when `count == 1`.
- **Strict 5-Minute Grace Window**:
  - Exactly 300,000 ms elapsed after due instant: eligible for delivery (`assertNotNull(result.eligibleOpportunity)`).
  - 300,001 ms elapsed: strictly missed, marked SKIPPED (`assertNull(result.eligibleOpportunity)`, `assertEquals(listOf(0), result.skippedIndices)`).
  - Multi-opportunity selection: Picks at most latest eligible past occurrence; older opportunities marked SKIPPED.
  - Consumed opportunity guard: If latest past occurrence was already claimed/posted, never falls back to older opportunities. Future occurrences (`dueEpochMs > nowEpochMs`) retained.

### 8.2. `PersistedScheduleProjector` & `PersistedTaskSchedule`
- **Draft Exclusion**: Projections constructed strictly from `server_base` overlaid with submitted `outbox` records. Editor drafts in `editor_drafts` table strictly ignored.
- **Cancellation Barriers**:
  - Task completion (`completed == true` in base or outbox) immediately causes `isEligibleForScheduling = false`.
  - Task deletion (`isDeleted == true`) causes `isEligibleForScheduling = false`.
  - Disabling reminder rule causes `isEligibleForScheduling = false`.
  - Unresolved conflict head (`STATE_CONFLICT` or `STATE_UNKNOWN_SUPERSEDED`) causes `isEligibleForScheduling = false`.
- **Token Tracking**: Reflects `scheduleToken` changes across edits, ensuring stale alarms are superseded.

### 8.3. `ReminderDeliveryGate`
- **Lock Ordering**: Enforces canonical order: `ReminderDeliveryGate admission -> Store Mutex -> SQLite Transaction`. Never waits for network under gate admission.
- **Admission States**: Transitions `OPEN -> CLOSING -> CLOSED`. Mutating while `CLOSING`/`CLOSED` throws `IllegalStateException`.
- **Delivery Admission Fail-Closed Checks**:
  - Wipe marker check executed outside SQLite transaction. Rejects delivery when marker present.
  - Persisted generation check: Stale alarms from prior account sessions or pre-teardown generations rejected (`currentGeneration != expectedGeneration`).
- **Drain Synchronization**: `closeAndDrain(timeoutMs = 5000L)` waits for in-flight deliveries before returning and completing transition to `CLOSED`.

### 8.4. `ReminderOccurrenceStore`
- **Identity & Idempotency**: Primary key composite `(accountKey, generation, taskId, scheduleToken, occurrenceIndex)`. Re-running `syncPlannedOccurrences` is idempotent.
- **Atomic Claim-Before-Post**: `dao.claimOccurrenceAtomic` transitions `claim_outcome` from `PENDING` to `CLAIMED` in single atomic SQL update `WHERE claim_outcome = 'PENDING'`.
- **Skipped Occurrence Tracking**: `dao.markOccurrencesSkippedAtomic` marks older missed occurrences as `SKIPPED`.
- **Crash Recovery**: Once claimed, crashes before or during posting never replay opportunity (`assertNull(reClaimed)`).
- **Notification Cleanup**: Detects cancelled or completed tasks with posted platform notification IDs and returns them for native OS dismissal.

### 8.5. `ReminderSchedulingCoordinator`
- **Schedule Reconciliation**: Computes `ScheduleReconciliationDelta` and drives `adapter.reconcileSchedule` and `adapter.removeOwnedNotification`.
- **Multi-Step Delivery Flow**:
  1. Delivery gate admission
  2. Capabilities verified (`adapter.queryCapabilities().isDeliveryPermitted`)
  3. Re-reads current persisted schedule projection for fresh title and eligibility
  4. Atomic claim in SQLite
  5. Wipe marker and gate state re-check before native post
  6. Native post via `adapter.postOwnedNotification`
  7. Ledger update via `recordPosted` with platform notification ID
- **Integration Test**: `ReminderSchedulingCoordinatorSqliteSmokeTest` verifies real BundledSQLite persistence, alarm scheduling, draft modification isolation, and obsolete notification removal.

### 8.6. Fail-Closed Account Leave Protocol
- **Review Guard**: Counts drafts, pending, uncertain, conflict, and unknownSuperseded records. Blocks standard leave when unresolved work exists (`review.hasReviewWork`).
- **Teardown Order**:
  1. Freezes edits: `store.isFrozen = true`
  2. Closes and drains delivery gate: `deliveryGate?.closeAndDrain(5000L)`
  3. Increments generation and writes wipe marker outside DB. Aborts before file deletion if marker persistence fails.
  4. Cancels scheduler work via `schedulerCancelGate?.cancelAndAwait(accountKey)`. If cancellation fails, retains wipe marker, remains frozen, and aborts fail-closed.
  5. Closes Room database connection.
  6. Deletes database files (`.db`, `.db-wal`, `.db-shm`). If deletion fails, wipe marker remains intact.
  7. Clears vault credentials and auth session.
  8. Clears wipe marker only after all cleanup succeeds.
- **Crash Recovery on Startup**: `AccountLeaveProtocol.completePendingWipe` safely cleans up uncompleted wipe if process crashed mid-teardown.

---

## 9. Critical Findings & Safety Gaps (QA Architectural Audit)

While the authored unit/integration tests pass with 100% success, static inspection reveals five material safety and concurrency gaps requiring hardening before release sign-off:

1. **Teardown ignores drain timeout and active mutations**:
   - `AccountLeaveProtocol.performGuardedTeardown()` invokes `store.deliveryGate?.closeAndDrain(5000L)` but completely ignores the returned `Boolean`. If draining times out, it proceeds directly to close and delete Room files while a native delivery callback may still be executing against SQLite.
   - `ReminderDeliveryGate` tracks `inFlightMutations` counter but `closeAndDrain()` only waits for `inFlightDeliveries == 0`, never draining or waiting for in-flight store mutations.
   - *Fix needed*: Check drain result; if false or mutations active, abort teardown fail-closed without deleting DB.

2. **Delivery admission does not enforce cancellation barriers against concurrent saves**:
   - In `ReminderSchedulingCoordinator.deliverReminder()`, task eligibility and title are read, then claimed in SQLite, then wipe/gate state is re-checked, then native post is invoked.
   - A concurrent save (completion, deletion, deadline edit, or reminder disable) can commit between the initial projection read and the native post. The delivery path does not hold mutual exclusion against concurrent store saves across this window.
   - *Fix needed*: Enforce serialized barrier between cancellation mutations and native delivery dispatch.

3. **Reconciliation can race teardown and retain orphan schedules**:
   - `ReminderSchedulingCoordinator.reconcile()` checks `deliveryGate.getState() != OPEN` once at entry, but does not acquire or hold admission across the duration of ledger sync and native adapter reconciliation. Teardown could cancel alarms, followed by an already-running `reconcile()` reinstalling them.
   - In `ReminderOccurrenceStore.syncPlannedOccurrences()`, cancellation occurs for tasks present in `schedules` that are ineligible, or for older schedule tokens. However, if a task disappears entirely from `schedules` (e.g. wiped or purged base), its existing active ledger entries are not cancelled and can linger as active scheduled alarms.
   - *Fix needed*: Execute `reconcile()` under mutation admission; cancel ledger entries for any taskId not present in `schedules`.

4. **Inconsistent state on leave failure**:
   - In `performGuardedTeardown()`, `closeAndDrain()` closes the gate. If subsequent generation read/write fails, the method sets `store.isFrozen = false`, but the delivery gate remains permanently in `CLOSED` state. The store is left neither fully operational nor fail-closed frozen.
   - *Fix needed*: Explicitly preserve frozen state on teardown abort, or define clean gate reset semantics.

5. **Grace evaluation arithmetic overflow risk**:
   - In `ReminderScheduleCalculator.evaluateGrace()`, elapsed time is calculated via `nowEpochMs - latest.dueEpochMs`. If `latest.dueEpochMs` is an extreme negative or boundary value, this subtraction can overflow to negative, erroneously falling within `<= GRACE_PERIOD_MS`.
   - *Fix needed*: Use `safeSubtract()` in `evaluateGrace()` with boundary regression tests.

---

## 10. Coverage Metrics & Limitations

- **Automated Tool Limitation**: No bytecode coverage agent (JaCoCo) is wired into the Gradle KMP build configuration. Line/branch percentages cannot be programmatically generated.
- **Observed Test Suite Coverage**:
  - Test suites thoroughly exercise normal execution paths, nominal edge boundaries (e.g. exactly 300,000ms vs 300,001ms), and basic error paths.
  - Multi-threaded interleaving, drain timeouts, and race conditions between concurrent mutations and delivery callbacks lack deterministic concurrent barrier tests.

---

## 11. Performance Metrics

- Total Suite Execution Wall Time: ~25.5s
  - `:shared:jvmTest`: 7.47s wall time (test execution: 0.806s)
  - `:desktopApp:test`: 6.70s wall time (test execution: 0.190s)
  - `strict-v2-vector-runner.mjs`: 0.15s
  - `hub-live-smoke.mjs --fixture-test`: 0.50s
  - `release-security-check.mjs --diagnose-source`: 0.10s
  - `hub-strict-upgrade.test.mjs`: 10.82s
- Slow Tests Identified: None (All JVM tests execute under 300ms; CLI upgrade test duration dominated by intentional retry delay simulation).

---

## 12. Build Status & Validation Limitations

- Shared JVM Test Suite: BUILD SUCCESSFUL (0 compilation errors, 2 expect/actual Beta warnings)
- Desktop App Test Suite: BUILD SUCCESSFUL (0 errors)
- Strict Upgrade Test Suite: 9/9 PASS
- Security Audit: CLEAN
- Flakiness: None observed across fresh `--rerun-tasks` executions.
- **Validation Limitation 1 (Dependency Verification)**: `--dependency-verification=off` was required because Gradle verification metadata contains unverified artifacts. This is a validation gap that must be resolved prior to release build packaging.
- **Validation Limitation 2 (Protocol Smoke Scope)**: `hub-live-smoke.mjs --fixture-test` exercises strict-v1 protocol compatibility against an in-process mock server; it does not validate live backend cluster migrations.
- **Validation Limitation 3 (Security Scope)**: `release-security-check.mjs --diagnose-source` audits static repository files and manifests; binary APK signing and runtime Android KeyStore hardware backing require emulator/device verification.

---

## 13. Critical Issues

1. **Gate Quiescence during Teardown**: `closeAndDrain()` timeout is ignored and active mutations are not drained prior to Room database deletion.
2. **Mutation vs Delivery Race Window**: Potential race where a task completion commits concurrently while a delivery callback is between projection read and native post.
3. **Orphaned Ledger Entries**: `syncPlannedOccurrences` does not cancel ledger entries for tasks completely removed from the projection set.

---

## 14. Recommendations

1. **Harden Teardown Quiescence**:
   - Assert `val drained = deliveryGate?.closeAndDrain(...) == true`; abort and retain wipe marker if false.
   - Wait for `inFlightMutations == 0` before closing database handles.
2. **Deterministic Concurrency Tests**:
   - Author concurrent test cases using `CompletableDeferred` barriers:
     - Pause delivery right before native post, execute and commit task completion, assert native post is suppressed.
     - Hold an active store mutation, trigger leave, assert leave waits or fails closed without deleting active DB.
     - Simulate `closeAndDrain()` timeout, assert DB files are not deleted.
3. **Grace Window Arithmetic**:
   - Replace `nowEpochMs - latest.dueEpochMs` with `safeSubtract(nowEpochMs, latest.dueEpochMs)` in `ReminderScheduleCalculator.evaluateGrace()`.
4. **Dependency Verification**:
   - Regenerate `gradle/verification-metadata.xml` so test suites can execute without `--dependency-verification=off`.

---

## 15. Next Steps

1. Address the 3 critical concurrency issues identified in Section 13 before Phase 07 final sign-off.
2. Implement deterministic barrier tests verifying concurrency ordering between mutations and delivery.
3. Update `gradle/verification-metadata.xml` to re-enable Gradle dependency verification.

---

## 16. Unresolved Questions

1. For Android 14+ (`minSdk 26`, `targetSdk 35`), should exact alarm permission denial fall back to inexact alarms via WorkManager, or present an explicit permission elevation prompt in the UI?
2. When an account leave aborts due to a drain timeout or cancel failure, should the account remain permanently frozen requiring app restart, or should an explicit "Retry Teardown" flow be presented to the user?
3. Should cancelled ledger entries older than 30 days be purged during staged full scans, or retained indefinitely as permanent local audit history?
