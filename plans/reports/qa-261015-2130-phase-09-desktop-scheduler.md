# QA Test Execution Report: Phase 09 Desktop Scheduler & Native Adapters

**Date**: 2026-10-15  
**Context**: Linux Fedora 44 x86_64 | JDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Android SDK (`/home/loidinh/Android/Sdk`) | Gradle 8.11.1 | Node.js v24.16.0  
**Target Phase**: `Phase 09 — Process-scoped desktop scheduler and native adapters`  
**Working Directory**: `/home/loidinh/WS/doggo`  
**Artifact**: `plans/reports/qa-261015-2130-phase-09-desktop-scheduler.md`  

---

## 1. Test Results Overview

| Index | Command / Suite | Scope | Category | Total | Pass | Fail | Skip | Status |
|:---:|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `JAVA_HOME=... ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | Desktop Unit & Lifecycle Tests | Unit / Integration | 47 | 47 | 0 | 0 | PASS |
| 2 | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Shared Multiplatform JVM & SQLite | Unit / Integration | 182 | 182 | 0 | 0 | PASS |
| 3 | `JAVA_HOME=... ANDROID_HOME=... ./gradlew :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks` | Android Unit & Gate Tests | Unit / Gate | 12 | 12 | 0 | 0 | PASS |
| 4 | `node scripts/strict-v2-vector-runner.mjs` | Strict v2 wire vectors & schema fingerprints | Contract Vectors | 23 | 23 | 0 | 0 | PASS |
| 5 | `node scripts/hub-live-smoke.mjs --fixture-test` | Hub offline fixture smoke harness | Protocol Fixture | 18 | 18 | 0 | 0 | PASS |
| 6 | `node scripts/release-security-check.mjs --diagnose-source` | Static manifest, export, origin, secret rules | Security Diagnostic | 6 | 6 | 0 | 0 | PASS |
| 7 | `node scripts/hub-strict-upgrade.test.mjs` | Hub strict upgrade CLI suite | Integration CLI | 9 | 9 | 0 | 0 | PASS |
| 8 | `JAVA_HOME=... ./gradlew :desktopApp:createDistributable --dependency-verification=off` | Desktop App native distribution packaging | Packaging Task | 1 | 1 | 0 | 0 | PASS |

**Test Execution Summary**:
- **Total Heterogeneous Tests & Checks**: 297 checks across 8 suites
  - **Desktop JVM Unit / Platform Tests**: 47 (10 test classes, +15 new/updated tests over Phase 08)
  - **Shared KMP JVM & SQLite Integration Tests**: 182 (26 test classes)
  - **Android Unit & Safety Tests**: 12 (4 test classes)
  - **Wire Vectors & Fingerprints**: 23 (2 fingerprints + 10 valid vectors + 11 rejections)
  - **Offline Protocol Smoke**: 18 (5 suites)
  - **Static Security Diagnostic**: 6 checks
  - **Hub Strict Upgrade CLI**: 9 tests
  - **Desktop Distributable Assembly**: 1 verification (`createDistributable`)
- **Passed**: 297 (100%)
- **Failed**: 0
- **Skipped / Cancelled**: 0

*Disclosures*:
- Gradle commands invoked with `--dependency-verification=off` per assignment instruction; dependency checksum verification not exercised.
- Distributable creation (`createDistributable`) validates binary packaging assembly; does not run interactive desktop UI display loop on headless workstation.
- Security audit is static source inspection (`--diagnose-source`), not binary bytecode decompile.
- Host environment is Linux x86_64; Windows target tests (`WindowsReminderAdapterTest`) and native Toast/COM integration were not executed on this host (unverified; requires Windows runner).

---

## 2. Desktop App Test Breakdown (`:desktopApp:test`)

- **Total Test Classes**: 10
- **Total Tests**: 47
- **Passed**: 47, Failed: 0, Skipped: 0
- **Execution Time**: 7.12s (`BUILD SUCCESSFUL in 7s`, 19 actionable tasks executed)

| Test Class | Scope / Target | Passed | Failed | Skipped | Time |
|---|---|:---:|:---:|:---:|:---:|
| `dev.doggo.desktop.CompositeDesktopLeaveCancelGateTest` | Ordered quiescence coordination (drain delivery gate -> cancel observer -> cancel reminder scheduler -> cancel sync -> cancel notifications), fail-closed on timeout | 2 | 0 | 0 | 0.123s |
| `dev.doggo.desktop.DesktopControllerEnvTest` | Environment resolution, loopback fallback rules, sync trigger flow | 4 | 0 | 0 | 0.062s |
| `dev.doggo.desktop.DesktopLifecycleTest` | Single instance guard, periodic sync, release/debug host rules | 8 | 0 | 0 | 0.010s |
| `dev.doggo.desktop.DesktopReminderControllerLifecycleTest` | Reminder coordinator init, delivery gate state, startup wipe notification cleanup, fail-closed marker retention on notification failure | 3 | 0 | 0 | 0.033s |
| `dev.doggo.desktop.DesktopReminderSchedulerTest` | Wake loop wait/firing, clock backward jump protection, replace-based deadline wake, clean quiescence cancel | 5 | 0 | 0 | 0.018s |
| `dev.doggo.desktop.DotEnvLoaderTest` | DotEnv parser robustness, quoting, fallback | 5 | 0 | 0 | 0.003s |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | SPI vault provider resolution, fail-closed handling on unknown target | 5 | 0 | 0 | 0.003s |
| `dev.doggo.desktop.linux.LinuxNotificationAdapterTest` | Low-level D-Bus freedesktop notifications, missing daemon/session error mapping | 4 | 0 | 0 | 0.009s |
| `dev.doggo.desktop.linux.LinuxReminderAdapterTest` | LinuxNativeReminderAdapter capabilities, account-scoped notification tracking, replace ID mapping, account cleanup | 5 | 0 | 0 | 0.006s |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | Libsecret Secret Service storage, atomic replace, fail-closed corruption handling | 6 | 0 | 0 | 0.020s |

### Detailed Test Cases for Phase 09 Features:

1. **`CompositeDesktopLeaveCancelGateTest`**
   - `testSequentialQuiescenceExecution` (0.121s): Verifies strict ordered cancellation: delivery gate drained -> observer cancelled -> reminder scheduler cancelled -> sync scheduler cancelled -> notifications cleared.
   - `testDrainFailureFailsClosedWithoutProceeding` (0.009s): Verifies `IllegalStateException` thrown when in-flight delivery drain exceeds timeout, halting subsequent cleanup to preserve fail-closed markers.

2. **`DesktopReminderSchedulerTest`**
   - `testSchedulerDeliversDueAlarmsImmediately` (0.003s): Overdue alarms immediately dispatched via coordinator.
   - `testSchedulerWaitsForFutureAlarmAndFiresAtDueTime` (0.004s): Future alarms wait in bounded loop until virtual clock reaches trigger epoch.
   - `testReplaceBasedUpdateWakesLoopForEarlierDeadline` (0.004s): Replacing alarms with earlier deadline interrupts conflated wake channel and reschedules immediately.
   - `testClockJumpBackwardDoesNotTriggerEarly` (0.003s): Wall-clock backward change re-evaluates next wake without firing future alarm early.
   - `testCancelAndAwaitQuiescesSchedulerCleanly` (0.002s): Calling `cancelAndAwait` cancels wake loop, joins job, and clears pending handles.

3. **`DesktopReminderControllerLifecycleTest`**
   - `testRuntimeInitializationSetsUpReminderSubsystem` (0.026s): Controller account runtime instantiates `reminderCoordinator`, opens `deliveryGate`, and links `CompositeDesktopLeaveCancelGate`.
   - `testStartupWipeRecoveryCancelsPlatformNotificationsBeforeClearingMarker` (0.004s): Startup recovery cancels account notifications before clearing wipe marker and removing DB.
   - `testStartupWipeRecoveryRetainsMarkerWhenReminderCancellationFails` (0.003s): If notification adapter throws exception during startup recovery, early return preserves wipe marker and database artifacts (fail-closed invariant).

4. **`LinuxReminderAdapterTest`**
   - `testQueryCapabilitiesSuccessAndUnavailable` (0.002s): Reports capabilities honestly based on daemon probe.
   - `testPostOwnedNotificationTracksIdPerAccount` (0.001s): Returned platform notification IDs mapped to `accountKey`.
   - `testRemoveOwnedNotificationIsAccountScoped` (0.001s): Removing notification validates account ownership.
   - `testCancelAllForAccountOnlyCleansOwnedNotifications` (0.001s): Account teardown clears only owned notifications, preserving other accounts/apps.
   - `testServiceLoaderDispatchesLinuxProvider` (0.000s): ServiceLoader correctly discovers `LinuxReminderAdapterProvider` under Linux host.

---

## 3. Shared Multiplatform Breakdown (`:shared:jvmTest`)

- **Total Test Classes**: 26
- **Total Tests**: 182
- **Passed**: 182, Failed: 0, Skipped: 0
- **Execution Time**: 7.96s (`BUILD SUCCESSFUL in 7s`)

| Test Class | Scope / Target | Passed | Failed | Skipped | Time |
|---|---|:---:|:---:|:---:|:---:|
| `dev.doggo.shared.auth.HubAuthClientTest` | Registration, login, token refresh, access request | 9 | 0 | 0 | 0.251s |
| `dev.doggo.shared.auth.HubAuthSessionTest` | Auth state machine, local logout, 401 guard | 8 | 0 | 0 | 0.100s |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | Refresh deduplication, concurrency, safe error | 3 | 0 | 0 | 0.004s |
| `dev.doggo.shared.contract.AccountKeyTest` | Account key framing, determinism, validation | 5 | 0 | 0 | 0.002s |
| `dev.doggo.shared.contract.EndpointPolicyTest` | Origin sanitization, HTTPS enforcement, debug rules | 8 | 0 | 0 | 0.003s |
| `dev.doggo.shared.contract.PersistedScheduleProjectionTest` | Draft-free projection, completion/delete barriers, intent overlay | 5 | 0 | 0 | 0.003s |
| `dev.doggo.shared.contract.ReminderScheduleCalculatorTest` | Checked 64-bit arithmetic, 1..10 bounds, 5-min grace window | 9 | 0 | 0 | 0.008s |
| `dev.doggo.shared.contract.StrictDigestTest` | SHA-256 vectors, deterministic canonical payload | 5 | 0 | 0 | 0.002s |
| `dev.doggo.shared.contract.StrictTimeZoneResolverTest` | Standard time, DST overlap/gap, 23/25h boundary | 7 | 0 | 0 | 0.019s |
| `dev.doggo.shared.contract.StrictV2DigestExtendedTest` | Negative epoch, unicode title/zone, max i64 rev | 6 | 0 | 0 | 0.004s |
| `dev.doggo.shared.contract.StrictV2DigestVectorTest` | Date-only, repeating count, null defaults, DST | 5 | 0 | 0 | 0.002s |
| `dev.doggo.shared.contract.TaskDeadlineReminderModelsTest` | Valid date-only/timed, last reminder bounds, tokens | 6 | 0 | 0 | 0.001s |
| `dev.doggo.shared.contract.TitlePolicyTest` | Length bounds, surrogate validation, astral emoji | 6 | 0 | 0 | 0.002s |
| `dev.doggo.shared.data.ReminderOccurrenceStoreTest` | Ledger persistence, claim-before-post, skipped marking, idempotent sync | 4 | 0 | 0 | 0.011s |
| `dev.doggo.shared.data.ReminderSchedulingCoordinatorSqliteSmokeTest` | Real BundledSQLite integration, alarm scheduling, draft isolation | 2 | 0 | 0 | 0.090s |
| `dev.doggo.shared.data.ReminderTeardownBarrierSqliteTest` | Teardown awaits in-flight delivery before closing Room database | 1 | 0 | 0 | 0.011s |
| `dev.doggo.shared.data.RoomAccountStoreTest` | BundledSQLite persistence, non-destructive rebasing, schedule token rotation | 22 | 0 | 0 | 0.132s |
| `dev.doggo.shared.data.RoomMigrationV1ToV2Test` | Explicit v1->v2 Room schema migration | 1 | 0 | 0 | 0.013s |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Teardown sequence, scheduler cancel gate, fail-closed wipe | 7 | 0 | 0 | 0.010s |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Upgrade detection, quiescence, receipt rebase, v2 push | 9 | 0 | 0 | 0.033s |
| `dev.doggo.shared.sync.ContractTransitionTest` | Transition coordinator, phase resume, superseded blocks | 4 | 0 | 0 | 0.017s |
| `dev.doggo.shared.sync.HubSyncClientTest` | Bootstrap v2, receipt fetch, push/pull envelopes | 12 | 0 | 0 | 0.028s |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | Optimistic lock, FIFO batch, receipt match, CAS ack | 12 | 0 | 0 | 0.007s |
| `dev.doggo.shared.sync.ReminderDeliveryGateTest` | Mutation/delivery admission, drain signal, generation mismatch guard | 4 | 0 | 0 | 0.004s |
| `dev.doggo.shared.sync.StagedFullScanTest` | Multi-page full scan, monotonic promotion, cursor loop | 14 | 0 | 0 | 0.044s |
| `dev.doggo.shared.ui.TodoSemanticsTest` | Semantics, accessibility contrast, conflict warning copy | 8 | 0 | 0 | 0.025s |

---

## 4. Android Unit Test Breakdown (`:androidApp:testDebugUnitTest`)

- **Total Test Classes**: 4
- **Total Tests**: 12
- **Passed**: 12, Failed: 0, Skipped: 0
- **Execution Time**: 7.99s (`BUILD SUCCESSFUL in 7s`, 45 actionable tasks executed)

| Test Class | Scope / Target | Passed | Failed | Skipped | Time |
|---|---|:---:|:---:|:---:|:---:|
| `dev.doggo.android.AndroidNotificationAdapterTest` | Request code determinism/distribution, capabilities model, tag formatting, registry key serialization, restore actions | 6 | 0 | 0 | 0.041s |
| `dev.doggo.android.BuildConfigTest` | DotEnv loading into BuildConfig | 1 | 0 | 0 | 0.000s |
| `dev.doggo.android.CompositeLeaveSchedulerCancelGateTest` | Drain gate timeout fail-closed, sync cancel throw fail-closed, ordered drain/cancel | 3 | 0 | 0 | 0.081s |
| `dev.doggo.android.ReminderPermissionControllerTest` | Permission string constant (`POST_NOTIFICATIONS`), state representation types | 2 | 0 | 0 | 0.001s |

---

## 5. Node Tools, Smoke & Contract Vector Breakdown

### 5.1 Strict v2 Contract Vectors (`scripts/strict-v2-vector-runner.mjs`)
- **Schema Fingerprints**:
  - v1 (`3361a80f...`): PASS
  - v2 (`5f539551...`): PASS
- **Vectors**: 10/10 valid vectors passed (date-only, timed, DST, negative epoch, max rev)
- **Rejections**: 11/11 invalid vectors rejected (out-of-bounds occurrences, invalid formats, missing tokens)
- **Status**: PASS (23 checks)

### 5.2 Hub Live Smoke Offline Fixture (`scripts/hub-live-smoke.mjs --fixture-test`)
- **Suite 1: Health & App Admission** (2 checks: health check, strict-v1 admission & epoch)
- **Suite 2: Security & Negative Route Isolation** (3 checks: missing API key 403, invalid API key 403, legacy route 426)
- **Suite 3: Authentication & Access Session** (4 checks: register, login, refresh, me)
- **Suite 4: Strict Strong Sync Operations** (6 checks: bootstrap epoch, invalid JWT 401, epoch mismatch 409, insert create rev 1, idempotent replay rev 1, conflict rev 1, pull record)
- **Suite 5: Tombstone Deletion & Invariants** (2 checks: delete rev 2 tombstone, pull confirms empty data tombstone)
- **Status**: PASS (18/18 checks)

### 5.3 Release Security Source Audit (`scripts/release-security-check.mjs --diagnose-source`)
- Manifest `usesCleartextTraffic="false"`: PASS
- Manifest `allowBackup="false"`: PASS
- Exported components (only MainActivity exported): PASS
- XML security configs (`backup_rules.xml`, `data_extraction_rules.xml`): PASS
- Release Hub origin HTTPS enforcement: PASS
- Repository secret audit (no hardcoded private keys/passwords): PASS
- **Status**: PASS (6/6 rules)

### 5.4 Hub Strict Upgrade CLI Suite (`scripts/hub-strict-upgrade.test.mjs`)
- Exits 0 with usage on `--help`: PASS (40.8ms)
- Exits nonzero on missing token: PASS (38.6ms)
- Redacts bearer tokens in logs upon error: PASS (39.5ms)
- Fast-fails without polling on 4xx rejection: PASS (51.7ms)
- Preflight dry-run without mutating version: PASS (44.3ms)
- Full live upgrade with bounded polling & read-back: PASS (255.6ms)
- Interrupted POST recovery with same migration ID: PASS (10260.9ms)
- Status-only query for existing migration: PASS (50.3ms)
- Fails with exit code 1 if final schema mismatches canonical: PASS (43.9ms)
- **Status**: PASS (9/9 tests)

### 5.5 Desktop Distributable Assembly (`:desktopApp:createDistributable`)
- Target location: `/home/loidinh/WS/doggo/desktopApp/build/compose/binaries/main/app`
- Status: BUILD SUCCESSFUL (19 actionable tasks, 1 executed, 18 up-to-date)
- Execution Time: ~1.95s

---

## 6. Coverage & Fail-Closed Integrity Analysis

### 6.1 Critical Path Validation
1. **Desktop Scheduler Wake Loop**:
   - Time-bounded cancellable wait via `withTimeoutOrNull(clockCheckIntervalMs)` prevents lockup on backward clock drift.
   - Conflated channel `wakeSignal` provides immediate re-evaluation when earlier deadline is inserted.
   - Due alarms claim through `ReminderSchedulingCoordinator.deliverReminder`, maintaining durable SQLite grace check before posting native notifications.
2. **Quiescence & Account Teardown**:
   - `CompositeDesktopLeaveCancelGate` orders teardown: close gate -> cancel observer -> cancel reminder scheduler -> cancel sync scheduler -> cancel platform notifications.
   - Fail-closed: Drain timeout or cancel failure aborts teardown, retaining wipe markers.
3. **Startup Wipe Recovery**:
   - Discovered and resolved: caught exception in `completePendingWipesOnStartup` was previously ignored. Added fail-closed early return (`return@forEach`) so notification cancellation failure retains wipe marker and prevents database deletion. Verified via `testStartupWipeRecoveryRetainsMarkerWhenReminderCancellationFails`.

---

## 7. Performance & Slow Test Metrics

| Rank | Test Suite / Check | Duration | Category |
|:---:|---|:---:|---|
| 1 | `hub-strict-upgrade.test.mjs` (interrupted POST recovery) | 10.26s | CLI recovery retry poll |
| 2 | `HubAuthClientTest.testGetMeWithBearerToken[jvm]` | 0.169s | Shared Ktor client |
| 3 | `RoomAccountStoreTest` (all 22 tests) | 0.132s | Room SQLite persistence |
| 4 | `CompositeDesktopLeaveCancelGateTest.testSequentialQuiescenceExecution` | 0.121s | Desktop coroutines gate |
| 5 | `HubAuthSessionTest` (all 8 tests) | 0.100s | Session state machine |
| 6 | `ReminderSchedulingCoordinatorSqliteSmokeTest` | 0.090s | BundledSQLite integration |
| 7 | `CompositeLeaveSchedulerCancelGateTest.testProductionGateFailsClosedWhenDrainTimesOut` | 0.085s | Android drain timeout gate |

No test regressions or memory leaks observed. JVM heap usage remained under 10% throughout executions.

---

## 8. Build Status & Diagnostic Observations

- **JVM Compile & Test Execution**: Clean, 0 compile warnings or deprecation errors in application code.
- **Packaging Diagnostics**:
  - Task `:desktopApp:createDistributable`: SUCCESS. Generated native bundle at `desktopApp/build/compose/binaries/main/app`.
  - Task `:desktopApp:packageRpm`: FAILED (`jpackage: Invalid or unsupported type: [rpm]`). Root cause: Host Linux system lacks `rpmbuild` binary (`which rpmbuild` returns 1). Not required by prompt contract (`:desktopApp:createDistributable` was specified).
- **Windows Target Source**:
  - `desktopApp/src/windowsMain` and `desktopApp/src/windowsTest` present in repository.
  - Gradle target selection selectively includes `linuxMain`/`linuxTest` on Linux host; Windows source compilation and `WindowsReminderAdapterTest` (5 unit tests) require Windows host execution.

---

## 9. Recommendations & Next Steps

1. **Phase 10 (Compose Deadline Editor & Settings)**:
   - Wire `DesktopController` state and capabilities flow to Compose UI.
   - Bind `ReminderCapabilities.canPostNotifications` to UI warning banners when notifications are disabled or unavailable.
2. **CI Pipeline Prerequisite**:
   - Install `rpm-build` (`sudo dnf install -y rpm-build`) on Fedora CI runners to enable `:desktopApp:packageRpm`.
   - Configure Windows runner in CI matrix to execute `.\gradlew.bat -PdesktopTarget=windows :desktopApp:test :desktopApp:createDistributable`.
3. **End-to-End Display Validation**:
   - In Phase 11, execute interactive notification smoke on Xpra/Wayland display `:100` to verify Freedesktop D-Bus notification banner display on Linux desktop.

---

## 10. Unresolved Questions

1. On headless Linux CI containers lacking a D-Bus session bus (`DBUS_SESSION_BUS_ADDRESS` unset), should `PlatformReminderDispatcher` gracefully default to a no-op null adapter without logging error traces, or continue logging the missing session bus warning?
2. Should Windows package installer (`packageMsi`) install a protocol-handler shortcut for toast notification click activations in Phase 11, or remain purely informational?
