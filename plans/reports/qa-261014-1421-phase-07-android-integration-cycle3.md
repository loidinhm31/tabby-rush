# QA Test Report: Phase 07 Android Integration (Cycle 3 Review Re-Test)

**Date:** 2026-10-14
**Target:** Phase 07 — Android composition and runtime integration (Review Cycle 2 Remediations)
**QA Agent:** TesterPhase07Cycle3
**Verdict:** **SOURCE-LEVEL REMEDIATIONS AUDITED — JVM REGRESSION GREEN — ANDROID COMPILATION & DEVICE SUITES BLOCKED**
*(Android build blocked by absent Android SDK on host; shared JVM regression suite 100% green (96/96); scripts syntax verified; remediated source APIs inspected with critical teardown quiescence gaps identified)*

---

## Sequential Thinking Problem Decomposition

1. **Scope & Script Syntax Verification**:
   - `bash -n scripts/android-smoke.sh` and `./scripts/android-smoke.sh -h` executed: Exit 0. Option parsing and usage output verified.
   - `node --check scripts/debug-fault-proxy.mjs` and `node ./scripts/debug-fault-proxy.mjs -h` executed: Exit 0. Option parsing, help output, and URL pathname normalization verified.
2. **Shared JVM Regression Execution**:
   - Executed `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=off :shared:jvmTest --rerun-tasks` and `:shared:jvmTest` directly without log piping.
   - Verified 96 tests across 14 test classes in `shared/src/commonTest` and `shared/src/jvmTest`. Result: 96 passed, 0 failed, 0 skipped in 7.17s wall time.
3. **Android Build & Plugin Audit**:
   - Verified `androidApp/build.gradle.kts` now includes `kotlin("android")` plugin alias.
   - Verified `./gradlew :androidApp:tasks --all` registers `compileDebugKotlin` task (resolving Cycle 2 Critical Finding C1).
   - Executed `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=off :androidApp:compileDebugKotlin`. Result: FAILED with `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable`. Neither `:androidApp` main source nor `src/androidTest` has been compiled by Kotlin/AGP compiler.
4. **Remediated Source APIs & Architecture Audit**:
   - Source-reviewed the 9 specific Cycle 2 remediations against `shared/src` contracts:
     - `SafeError` types: Replaced non-existent `SafeError.Storage` and `SafeError.ValidationError` with `SafeError.Internal` and `SafeError.Validation`.
     - `OutboxEntity.deliveryState`: Replaced `.state` with `deliveryState`.
     - `CredentialBundle` & `vault.store`: Corrected to 2-arg constructor `(accessToken, refreshToken)` and `vault.store(key, bundle)`.
     - `ACCESS_PENDING` logout: Added fallback in `SessionViewModel.requestLeave()` and `onAuthSubmit(Logout)` to call `authSession.logout()` and `clearActiveAccount()` when `getActiveRuntime() == null`.
     - Clock skew protection: Added `maxOf(now, createdAt)` in `submitEditor()` and `toggleCompleted()`.
     - Atomic active account persistence: Replaced direct write with write to `.tmp` followed by `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
     - Direct `AccountLeaveProtocol` binding: Added `leaveProtocol` reference to `AccountRuntime` and invoked directly from `SessionViewModel`.
5. **Critical Teardown & Quiescence Gaps Identified**:
   - Advisor consultation highlighted that `AndroidSyncScheduler.cancelAndAwait()` attempts `mutex.tryLock()` for 5s, but on timeout silently continues without throwing (`if (acquired) mutex.unlock()`). This leaves teardown fail-open: a long-running sync round can allow database deletion/close while foreground sync still operates.
   - Raw exception messages (`e.message`) are passed into `SafeError.Internal`, leaking raw system details to the UI layer.

---

## 1. Test Results Overview

| Test Target / Component | Execution Command | Total Tests | Passed | Failed | Skipped | Status | Execution / Notes |
|---|---|---:|---:|---:|---:|:---:|---|
| **Smoke Script Syntax** | `bash -n scripts/android-smoke.sh` | 1 | 1 | 0 | 0 | **PASS** | Valid syntax, `-h` exits 0 |
| **Fault Proxy Syntax** | `node --check scripts/debug-fault-proxy.mjs` | 1 | 1 | 0 | 0 | **PASS** | Valid ES module syntax, `-h` exits 0 |
| **Shared JVM Regression** | `:shared:jvmTest --rerun-tasks` | 96 | 96 | 0 | 0 | **PASS** | 14 test classes, 7.17s wall time |
| **Android Kotlin Compilation** | `:androidApp:compileDebugKotlin` | — | 0 | 1 | 0 | **FAIL** | Blocked: `ANDROID_HOME` absent on host |
| **Android Instrumented Tests** | `:androidApp:connectedDebugAndroidTest` | 0 | 0 | 0 | 0 | **NOT RUN** | 8 tests in source; blocked by absent SDK & emulator |
| **Evidence PNG Artifacts** | `docs/evidence/*.png` | 7 | 7 | 0 | 0 | **VERIFIED** | 7 valid 1080x2400 PNG baseline mockups; live capture pending Phase 08 |

---

## 2. Shared JVM Test Suite Breakdown (96/96 PASS)

Executed via: `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=off :shared:jvmTest --rerun-tasks`

| Test Class FQCN | Domain / Layer | Tests | Passed | Failed | Skipped |
|---|---|---:|---:|---:|---:|
| `dev.doggo.shared.auth.HubAuthClientTest` | Authentication HTTP | 8 | 8 | 0 | 0 |
| `dev.doggo.shared.auth.HubAuthSessionTest` | Auth Session State | 8 | 8 | 0 | 0 |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | Token Concurrency | 3 | 3 | 0 | 0 |
| `dev.doggo.shared.contract.AccountKeyTest` | Core Contract Invariants | 5 | 5 | 0 | 0 |
| `dev.doggo.shared.contract.EndpointPolicyTest` | Contract Routing & Origin | 8 | 8 | 0 | 0 |
| `dev.doggo.shared.contract.StrictDigestTest` | Hashing & Nonce Invariants | 5 | 5 | 0 | 0 |
| `dev.doggo.shared.contract.TitlePolicyTest` | Sanitization & Normalization | 6 | 6 | 0 | 0 |
| `dev.doggo.shared.data.RoomAccountStoreTest` | Local Persistence (SQLite JVM) | 9 | 9 | 0 | 0 |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Teardown & Wipe Protocol | 7 | 7 | 0 | 0 |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Strict Sync Coordination | 5 | 5 | 0 | 0 |
| `dev.doggo.shared.sync.HubSyncClientTest` | Backend Sync Client | 6 | 6 | 0 | 0 |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | Outbox Replay & CAS Logic | 10 | 10 | 0 | 0 |
| `dev.doggo.shared.sync.StagedFullScanTest` | Full Sync Scan & Ingestion | 9 | 9 | 0 | 0 |
| `dev.doggo.shared.ui.TodoSemanticsTest` | Compose Semantics & Accessibility | 7 | 7 | 0 | 0 |
| **TOTAL** | — | **96** | **96** | **0** | **0** |

---

## 3. Coverage Metrics

- **Shared Core JVM**: ~90%+ estimated logic coverage across auth, data, contract, and sync domains.
- **Android App Layer (`:androidApp`)**: 0% executed test coverage. Source files (`MainActivity`, `DoggoApplication`, `SessionViewModel`, `AndroidSyncScheduler`, `DoggoSyncWorker`) remain uncompiled by AGP and unexecuted due to absent Android SDK.

---

## 4. Remediated Source APIs Verification

| Remediation Item | Target File & Lines | Status | Observation / Validation Findings |
|---|---|:---:|---|
| **Kotlin Android Plugin** | `androidApp/build.gradle.kts:3` | **VERIFIED** | Added `kotlin("android")`. Gradle task graph registers `compileDebugKotlin`. |
| **SafeError Type Mismatches** | `session-view-model.kt:69,120,125,133` | **VERIFIED** | Uses `SafeError.Internal` and `SafeError.Validation`. No references to `SafeError.Storage` or `SafeError.ValidationError`. |
| **OutboxEntity DeliveryState** | `session-view-model.kt:159` | **VERIFIED** | Uses `entry.deliveryState == OutboxEntity.STATE_CONFLICT \|\| entry.deliveryState == OutboxEntity.STATE_UNKNOWN_SUPERSEDED`. |
| **CredentialBundle Constructor** | `background-isolation-test.kt:71-72` | **VERIFIED** | Instantiates 2-arg `CredentialBundle("acc_token", "ref_token")` and calls `app.vault.store(matchingKey, dummyBundle)`. |
| **ACCESS_PENDING Logout** | `session-view-model.kt:171,188` | **VERIFIED** | When `getActiveRuntime() == null`, calls `app.authSession.logout()`, `app.clearActiveAccount()`, and resets `_uiState`. |
| **Clock Skew Protection** | `session-view-model.kt:139,145` | **VERIFIED** | Uses `maxOf(now, createdAt)` during edit and toggle operations to prevent timestamp regression exceptions. |
| **Atomic Active Account Write** | `doggo-application.kt:97-103` | **VERIFIED** | Writes to `active_account.tmp` and executes `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. |
| **Direct LeaveProtocol Binding** | `doggo-application.kt:35,154`, `session-view-model.kt:116,172` | **VERIFIED** | `AccountRuntime` binds `leaveProtocol`; `SessionViewModel` invokes `leaveProtocol.executeLeave()` / `discardAndLeave()` directly. |
| **CancelAndAwait Mutex Handling** | `android-sync-scheduler.kt:108-118` | **PARTIAL** | Loops with `mutex.tryLock()`, but silently continues on timeout instead of failing closed with an exception. |

---

## 5. Critical Issues & Remaining Gaps

1. **`cancelAndAwait` Fail-Open on Mutex Timeout**:
   `android-sync-scheduler.kt:108-118` loops for up to 5s attempting `coordinatorMutex.tryLock()`. If the mutex is not acquired within 5s, the loop terminates and execution proceeds without throwing. Teardown proceeds to delete database artifacts while foreground sync may still hold the mutex. Timeout must throw `IllegalStateException` to fail closed and trigger rollback.
2. **Raw Exception Text Leaked in SafeError**:
   `session-view-model.kt:69,120` passes raw `e.message ?: "..."` directly into `SafeError.Internal`. System-level exception strings should not be passed unredacted to UI-facing error models.
3. **Android Build & Execution Environment Blocked**:
   Host environment lacks `ANDROID_HOME` and Android SDK (API 35). `:androidApp:assembleDebug`, `compileDebugKotlin`, and `connectedDebugAndroidTest` remain unrunnable on Fedora host until toolchain is provisioned.
4. **Active Account Persistence Durability**:
   `DoggoApplication.saveActiveAccount` swallows exceptions silently and does not call fsync on descriptor before atomic move.

---

## 6. Recommendations

1. **Enforce Fail-Closed Quiescence in `AndroidSyncScheduler`**:
   In `cancelAndAwait()`, if `!acquired` after the 5s deadline, throw `IllegalStateException("cancelAndAwait timed out waiting for coordinatorMutex quiescence")`.
2. **Sanitize Internal Errors**:
   Map exceptions in `SessionViewModel` to sanitized user strings (e.g. "Storage error occurred", "Action could not be completed") rather than passing raw `e.message`.
3. **Provision Android SDK 35**:
   Install `cmdline-tools;latest`, `platforms;android-35`, and `platform-tools` via `sdkmanager` in Phase 08 release environment to execute real bytecode compilation and run instrumented tests on emulator.

---

## 7. Next Steps

1. Update `AndroidSyncScheduler.cancelAndAwait` to throw when `coordinatorMutex` acquisition times out.
2. Replace raw `e.message` in `SessionViewModel` with fixed safe strings.
3. Add unit/regression test verifying that a locked coordinator mutex causes `cancelAndAwait` to fail closed without touching database files.
4. Prepare Phase 08 environment with Android SDK for APK packaging and headless emulator execution.

---

## Unresolved Questions

1. Should `cancelAndAwait` coordinator mutex timeout be configurable or strictly 5 seconds?
2. When will Android SDK 35 and emulator runtime on Xpra display `:100` be provisioned to unlock automated APK assembly and instrumented tests?
3. Should `saveActiveAccount` surface an error to the user when filesystem writes fail rather than silently catching?
