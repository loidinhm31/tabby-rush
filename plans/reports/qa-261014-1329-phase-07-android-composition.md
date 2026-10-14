# QA Test Report: Phase 07 Android Composition & Runtime Integration

**Date:** 2026-10-14
**Target:** Phase 07 — Android composition and runtime integration
**QA Agent:** TesterPhase07
**Build & Test Commands:**
- Syntax Check: `bash -n scripts/android-smoke.sh && node --check scripts/debug-fault-proxy.mjs`
- Shared JVM Tests: `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=off :shared:jvmTest --rerun-tasks`
- Android Build: `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=off :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest`
**Environment:** Linux x86_64 Fedora, OpenJDK 21.0.8 (`$HOME/.local/share/jvm/jdk-21`), Node.js v22.x, Gradle 8.11.1
**Status:** **QA REVIEW COMPLETE — ANDROID RUNTIME ACCEPTANCE BLOCKED**
*(Android build blocked by missing SDK on host; 6 implementation findings of mixed severity identified)*

---

## Sequential Thinking Problem Decomposition

1. **Scope & Inventory Audit:**
   - Verified presence of all 25 Phase 07 paths from `plans/261013-1540-doggo-glean-hub-reuse/file-ownership.md`. All 25 files exist and contain non-trivial implementations.
2. **Script Syntax & Execution Audit:**
   - Ran `bash -n` on `scripts/android-smoke.sh`: establishes Bash syntax validity.
   - Ran `node --check` on `scripts/debug-fault-proxy.mjs`: establishes ES module syntax validity.
   - Exercised CLI flags: `android-smoke.sh -h` returns exit code 1; `debug-fault-proxy.mjs` drops connections on matching routes regardless of upstream HTTP status.
3. **Shared JVM Regression Suite Execution:**
   - Executed `:shared:jvmTest` with JDK 21 and `--rerun-tasks`.
   - Parsed XML reports and stdout logs: 96 total tests executed across 14 test classes; 96 passed, 0 failed, 0 skipped.
   - Disclosed `--dependency-verification=off` (Gradle dependency checksum verification was bypassed per test instruction).
4. **Android Build & Instrumented Test Evaluation:**
   - Attempted `:androidApp:assembleDebug` and `:androidApp:assembleDebugAndroidTest`.
   - Build failed: Android SDK not found (`ANDROID_HOME` absent, `$HOME/Android/Sdk` nonexistent).
   - Instrumented test suites (`TodoDeviceTest`, `BackgroundIsolationTest`) were statically inspected only; no APK built, no device/emulator runner executed.
5. **UI Evidence Artifact Inspection:**
   - Inspected 7 PNG files in `docs/evidence/`. All verify as valid 1080x2400 8-bit RGB non-interlaced image files.
   - Provenance status: device-capture provenance unverified; cannot confirm live device adb screencap vs pre-rendered layout rendering.
6. **Security & Manifest Source Review:**
   - Reviewed `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/debug/AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, and `network_security_config.xml`.
   - Noted source manifest declarations; merged manifest and dependency-injected components remain uninspected pending SDK resolution.
7. **Lifecycle & Concurrency Defect Analysis:**
   - Identified 6 findings of mixed severity across startup wipe sequencing, account isolation & generation fencing, ViewModel lifecycle leaks, and script error handling.

---

## 1. Test Results Overview

| Test Target / Component | Execution Command | Total Tests | Passed | Failed | Skipped | Status | Execution / Notes |
|---|---|---:|---:|---:|---:|:---:|---|
| **Smoke Script Syntax** | `bash -n scripts/android-smoke.sh` | 1 | 1 | 0 | 0 | **PASS** | Valid Bash syntax |
| **Fault Proxy Syntax** | `node --check scripts/debug-fault-proxy.mjs` | 1 | 1 | 0 | 0 | **PASS** | Valid Node.js ES module syntax |
| **Shared JVM Regression** | `:shared:jvmTest --rerun-tasks` | 96 | 96 | 0 | 0 | **PASS** | 14 test suites, 0 failures, 6.11s wall time |
| **Android Debug Build** | `:androidApp:assembleDebug` | — | — | 1 | — | **FAIL** | Blocked: `ANDROID_HOME` not found |
| **Android Instrumented Suite** | `:androidApp:connectedDebugAndroidTest` | 0 | 0 | 0 | 0 | **NOT EXECUTED** | 8 tests statically inspected; unexecuted due to SDK absence |
| **Evidence PNG Artifacts** | `file docs/evidence/*.png` | 7 | 7 | 0 | 0 | **VERIFIED** | 7 valid 1080x2400 PNG files; device provenance unverified |
| **Security & Manifests** | Source XML inspection | 5 | 5 | 0 | 0 | **INSPECTED** | Source declarations reviewed; merged manifest uninspected |

---

## 2. Shared JVM Test Suite Breakdown (96/96 PASS)

| Test Class FQCN | Domain / Layer | Test Doubles / Engine | Tests | Passed | Failed |
|---|---|---|---:|---:|---:|
| `dev.doggo.shared.auth.HubAuthClientTest` | Authentication HTTP | Mock Ktor Engine | 8 | 8 | 0 |
| `dev.doggo.shared.auth.HubAuthSessionTest` | Auth Session State | Fake Vault + Mock Ktor | 8 | 8 | 0 |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | Token Concurrency | Coroutine Mutex Double | 3 | 3 | 0 |
| `dev.doggo.shared.contract.AccountKeyTest` | Core Contract | Pure Common Kotlin | 5 | 5 | 0 |
| `dev.doggo.shared.contract.EndpointPolicyTest` | Contract Routing | Pure Common Kotlin | 8 | 8 | 0 |
| `dev.doggo.shared.contract.StrictDigestTest` | Hashing Invariants | Pure Common Kotlin | 5 | 5 | 0 |
| `dev.doggo.shared.contract.TitlePolicyTest` | Sanitization Contract | Pure Common Kotlin | 6 | 6 | 0 |
| `dev.doggo.shared.data.RoomAccountStoreTest` | Local Persistence | Bundled SQLite (JVM) | 9 | 9 | 0 |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Teardown & Wipe | Fake Store + Session | 7 | 7 | 0 |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Strict Sync Pipeline | Fake DAO + Mock Ktor | 5 | 5 | 0 |
| `dev.doggo.shared.sync.HubSyncClientTest` | Backend Sync Client | Mock Ktor Engine | 6 | 6 | 0 |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | Outbox & Replay CAS | Fake Store / DAO | 10 | 10 | 0 |
| `dev.doggo.shared.sync.StagedFullScanTest` | Full Sync Scan | Fake DAO + Mock Ktor | 9 | 9 | 0 |
| `dev.doggo.shared.ui.TodoSemanticsTest` | Compose Accessibility | Semantic Node Test | 7 | 7 | 0 |
| **TOTAL** | — | — | **96** | **96** | **0** |

*Execution Note: Ran with `--dependency-verification=off` per instruction.*

---

## 3. Android Instrumented Test Suites (Statically Inspected — Not Executed)

The `:androidApp` module defines 8 instrumented tests across 2 files. Because SDK location is not configured, these tests could not be compiled or executed on device/emulator. Static inspection details:

### 3.1 `todo-device-test.kt` (4 Tests Inspected)
- `testInitialUiState`: Checks `SessionViewModel` initial unauthenticated state, empty items, filter ALL.
- `testFilterSelectionAction`: Checks `SessionViewModel` filter mutation between `ACTIVE` and `COMPLETED`.
- `testEditorOpenAndCancel`: Checks `TodoAction.OpenCreate` and `CancelEditor` dispatching.
- `testAccountRuntimeCreationAndStoreAccess`: Exercises `DoggoApplication.getOrCreateAccountRuntime`, verifying database creation, todo item insertion, and round-trip retrieval via `RoomAccountStore`.

### 3.2 `background-isolation-test.kt` (4 Tests Inspected)
- `testWorkerFailsClosedOnPendingWipe`: Exercises `DoggoSyncWorker.doWork()` failure return when `wipe.marker` exists.
- `testWorkerFailsOnGenerationMismatch`: Exercises worker failure return when input generation is stale (4 vs 5).
- `testStartupWipeRecoveryCleansPendingWipe`: Exercises `completePendingWipesOnStartup()` clearing wipe markers.
- `testSchedulerCancelAndAwaitQuiescesWork`: Exercises `AndroidSyncScheduler.cancelAndAwait` invocation.

---

## 4. UI Evidence Artifacts Inspection

7 PNG files inspected in `docs/evidence/`:
1. `android-debug-empty.png`: 57,254 bytes, 1080 x 2400, 8-bit RGB non-interlaced.
2. `android-debug-populated.png`: 84,918 bytes, 1080 x 2400, 8-bit RGB non-interlaced.
3. `android-debug-completed.png`: 55,628 bytes, 1080 x 2400, 8-bit RGB non-interlaced.
4. `android-debug-editor.png`: 58,940 bytes, 1080 x 2400, 8-bit RGB non-interlaced.
5. `android-debug-error.png`: 55,072 bytes, 1080 x 2400, 8-bit RGB non-interlaced.
6. `android-debug-conflict.png`: 91,397 bytes, 1080 x 2400, 8-bit RGB non-interlaced.
7. `android-debug-access-pending.png`: 67,824 bytes, 1080 x 2400, 8-bit RGB non-interlaced.

*Provenance Caveat:* Image files exist and match specified dimensions, but live device-capture provenance is unverified in this environment due to missing Android SDK and emulator.

---

## 5. Security & Manifest Source Review

- **Cleartext Restrictions:**
  - Base `androidApp/src/main/AndroidManifest.xml` declares `android:usesCleartextTraffic="false"`.
  - Debug `androidApp/src/debug/AndroidManifest.xml` specifies `@xml/network_security_config`.
  - `network_security_config.xml` sets `base-config cleartextTrafficPermitted="false"` and permits cleartext only for `localhost`, `127.0.0.1`, and `10.0.2.2` with `includeSubdomains="false"`.
- **Backup & Extraction Rules:**
  - `backup_rules.xml` (API <= 30) excludes `database`, `sharedpref`, `accounts`, and `root`.
  - `data_extraction_rules.xml` (API >= 31) excludes `database`, `sharedpref`, `accounts`, and `root` for cloud backup and device transfer.
- **Exported Activity Boundary:**
  - Source manifest exports only `MainActivity` (`android:exported="true"`) with launcher intent filter.
  - Final merged manifest components cannot be inspected until Android SDK build completes.
- **Release Signing Guard:**
  - `androidApp/build.gradle.kts` task graph listener throws a build failure if release tasks are executed without `DOGGO_KEYSTORE`, `DOGGO_KEY_ALIAS`, `DOGGO_STORE_PASSWORD`, and `DOGGO_KEY_PASSWORD`.

---

## 6. Implementation Findings (Six Findings of Mixed Severity)

### Finding 1: Unsafe Async Ordering in Startup Wipe Recovery (`doggo-application.kt:74–77`)
- **Severity:** High
- **Code Reference:**
  ```kotlin
  syncScheduler.cancel(key)
  accountFiles.deleteDatabaseAndArtifacts(key)
  applicationScope.launch { vault.clear(key) }
  accountFiles.clearWipeMarker(key)
  ```
- **Analysis:** `vault.clear(key)` is dispatched asynchronously onto `applicationScope`, while `clearWipeMarker(key)` runs immediately on the calling thread. If a crash or exception occurs before the vault coroutine executes, the wipe marker is destroyed while credentials remain in Keystore. Furthermore, `checkAndRestoreEstablishedSession()` is launched concurrently from `onCreate()` on line 61 without gating on wipe completion, risking session resurrection while wiping.
- **Remediation:** Implement an awaited coroutine startup barrier: await scheduler cancellation/quiescence, file deletion, and vault credential clearing before removing `wipe.marker`. Gate session restoration and account runtime access until the barrier finishes.

### Finding 2: Incomplete Account Isolation & Generation Fencing (`doggo-application.kt`, `DoggoSyncWorker`, `AndroidSyncScheduler`)
- **Severity:** High
- **Code Reference:**
  - `doggo-application.kt:98`: `if (existing != null && existing.accountKey == accountKey) return existing` returns `existing` before checking `hasPendingWipe(accountKey)` and without revalidating `generation`.
  - `doggo-sync-worker.kt:41`: checks only `authSession.state.value == AuthState.AUTHENTICATED`, failing to assert `authSession.currentAccountKey.value == accountKey`.
  - `android-sync-scheduler.kt:42`: `runtime?.generation` is taken without asserting `runtime?.accountKey == accountKey`.
  - `android-sync-scheduler.kt:69`: periodic work request omits `generation`, causing `DoggoSyncWorker` to evaluate `expectedGeneration == -1L` and bypass generation fencing.
- **Analysis:** A race condition exists where active runtime can be returned after wipe marker creation or across generation bumps. Background workers for one account can run while another account is authenticated. Periodic work lacks immutable generation fencing and `ExistingPeriodicWorkPolicy.KEEP` can preserve an obsolete worker across generation bumps.
- **Remediation:** Enforce authentication, generation, and wipe checks under the lock in `getOrCreateAccountRuntime`; assert `authSession.currentAccountKey.value == accountKey` in worker; attach generation to periodic work and replace obsolete periodic tasks when generation changes.

### Finding 3: ViewModel Account-Scoped Work Outlives Logout (`session-view-model.kt:35–62`)
- **Severity:** Medium
- **Code Reference:**
  - `init` launches `authSession.state.collect`; upon `AuthState.UNAUTHENTICATED`, resets state but does not cancel active account jobs.
  - `onAuthenticated()` launches an unreferenced `viewModelScope.launch` before assigning `syncJob`.
- **Analysis:** An asynchronous setup or sync progress update initiated prior to logout can complete after logout or switch of accounts, repopulating UI state.
- **Remediation:** Track the full account-scoped coroutine session Job and cancel it cleanly upon `AuthState.UNAUTHENTICATED`. Fence UI updates against the active account key and generation.

### Finding 4: Hardcoded Cleartext Hub Endpoint in Main Source (`doggo-application.kt:48`)
- **Severity:** Medium
- **Code Reference:** `val hubConfig by lazy { HubConfig(origin = "http://10.0.2.2:3000", appId = "doggo") }`
- **Analysis:** Hardcoded `http://10.0.2.2:3000` cleartext in main application source will fail release builds at runtime due to `android:usesCleartextTraffic="false"`. Physical test devices connected via USB `adb reverse` also require loopback `http://127.0.0.1:3000`.
- **Remediation:** Parameterize origin via `BuildConfig.HUB_ORIGIN` or build variants, defaulting to HTTPS in release and configurable local loopback in debug.

### Finding 5: Premature Fault Consumption in Fault Proxy (`scripts/debug-fault-proxy.mjs:83–97`)
- **Severity:** Low
- **Analysis:** The fault proxy consumes its one-shot drop trigger and destroys the client connection whenever the target path is hit, regardless of `upstreamRes.statusCode`. If upstream returns an error (500 or 409), the drop occurs without proving that upstream committed the mutation.
- **Remediation:** Assert `upstreamRes.statusCode >= 200 && upstreamRes.statusCode < 300` before triggering connection drop.

### Finding 6: Smoke Script Help Flag Exits with Code 1 (`scripts/android-smoke.sh:26`)
- **Severity:** Low
- **Analysis:** `usage()` executes `exit 1` unconditionally, causing `scripts/android-smoke.sh -h` to exit with an error code rather than standard exit code 0.
- **Remediation:** Separate error exit (`exit 1`) from explicit help request (`exit 0`).

---

## 7. Performance Metrics

- **JVM Test Execution:** 6.11s wall time for 15 Gradle tasks executed with `--rerun-tasks`.
- **Test Worker JVM Memory:** `-Xmx512m` allocated to test runner process.

---

## 8. Build Status

- `:shared:jvmTest`: **SUCCESS** (Compiled and 96 tests passed).
- `:androidApp:assembleDebug`: **FAILED** (Blocked by absent Android SDK on host).

---

## 9. Critical Issues

1. **Android SDK Missing:** Workstation environment lacks Android SDK / build-tools, blocking local APK compilation and emulator instrumentation execution.
2. **Startup Wipe Ordering:** Vault clearance runs asynchronously without awaiting completion before wipe marker removal.
3. **Account Isolation Race:** Runtime return and worker execution omit strict account key equality and generation validation.

---

## 10. Recommendations

1. Implement an awaited startup wipe barrier in `DoggoApplication` that ensures cancellation, file deletion, and vault clearing complete before marker removal.
2. Under the `runtimeMutex` in `DoggoApplication`, re-validate account key, generation, and wipe marker before returning `existing` or creating new `AccountRuntime`.
3. Add strict `authSession.currentAccountKey.value == accountKey` check in `DoggoSyncWorker`.
4. Cancel all account-scoped coroutine jobs in `SessionViewModel` upon `AuthState.UNAUTHENTICATED`.
5. Provision Android SDK 35 (`cmdline-tools`, `platform-tools`, emulator) to enable automated Android test runs.

---

## 11. Unresolved Questions

1. When will Android SDK 35 and cmdline-tools be installed on this host environment?
2. What production HTTPS endpoint will be injected into release APK builds?
3. Should periodic sync work retain an immutable generation counter or be re-enqueued on generation increments?
