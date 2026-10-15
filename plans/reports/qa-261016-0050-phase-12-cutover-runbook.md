# QA Test Execution Report: Phase 12 Coordinated Cutover Runbook & Release Validation

**Date**: 2026-10-16  
**Context**: Linux Fedora 44 x86_64 | JDK 21 LTS (`Temurin-21.0.12.1+1`) | Android SDK (`/home/loidinh/Android/Sdk`) | Gradle 8.11.1 | Node.js v24.16.0 | Cargo 1.95.0 | pnpm 10.28.2  
**Target Phase**: `Phase 12 — Coordinated cutover, recovery runbook and release documentation`  
**Working Directories**: `/home/loidinh/WS/doggo` & `/home/loidinh/WS/glean-hub`  
**Artifact Path**: `plans/reports/qa-261016-0050-phase-12-cutover-runbook.md`  

---

## 1. Executive Summary & Test Results Overview

Comprehensive execution and validation of all specified test suites across Doggo and Glean Hub completed.

### Summary Verdict
- **Automated Test Suites**: **100% PASS** across all requested automated suites (401 executed tests/checks passed, 0 failed, 1 ignored live MongoDB test; 4 build/packaging targets verified).
- **Production Cutover Verdict**: **EXECUTED LOCAL AUTOMATED SUITES PASSED; LIVE ACCEPTANCE AND PRODUCTION RELEASE GATES REMAIN OPEN (GATE G7 FORMALLY BLOCKED)**.
- **Hold Summary**: Gate G7 cutover blocked pending Gate G2 physical native platform proof and Windows toast bridge implementation, Gate G3 multi-instance MongoDB replica set migration and 3 backend code remediations, Gate G6 end-to-end multiplatform acceptance, and supply-chain dependency verification metadata validation.

### Test Results Overview Table

| Index | Repository | Command / Suite | Category | Scope | Total | Pass | Fail | Skip / Ignored | Status |
|:---:|---|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `doggo` | `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | Rehearsal / Self-Test | Operator Cutover Tooling Rehearsal Suite | 7 | 7 | 0 | 0 | PASS |
| 2 | `doggo` | `node scripts/strict-v2-vector-runner.mjs` | Contract Vectors | Strict v2 Wire Vectors & Canonical Fingerprints | 23 | 23 | 0 | 0 | PASS |
| 3 | `doggo` | `node scripts/hub-live-smoke.mjs --fixture-test` | Protocol Fixture | Hub Offline Protocol Smoke Harness | 18 | 18 | 0 | 0 | PASS |
| 4 | `doggo` | `node scripts/release-security-check.mjs --diagnose-source` | Security Diagnostic | Static Manifest & Secret Source Audit | 6 | 6 | 0 | 0 | PASS* |
| 5 | `doggo` | `node scripts/hub-strict-upgrade.test.mjs` | Integration CLI | Hub Strict Upgrade CLI Harness | 9 | 9 | 0 | 0 | PASS |
| 6 | `doggo` | `node scripts/debug-fault-proxy.test.mjs` | Fault Injection | Debug Fault Proxy Route & Redaction Suite | 8 | 8 | 0 | 0 | PASS |
| 7 | `doggo` | `./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Unit / Integration | Shared KMP JVM & SQLite Integration Suite | 202 | 202 | 0 | 0 | PASS |
| 8 | `doggo` | `./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | Unit / Platform | Desktop App & Scheduler Lifecycle Suite | 49 | 49 | 0 | 0 | PASS |
| 9 | `doggo` | `./gradlew :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks` | Unit / Platform | Android Debug JVM Unit Suite | 12 | 12 | 0 | 0 | PASS** |
| 10 | `doggo` | `./gradlew :desktopApp:createDistributable :androidApp:assembleDebug --dependency-verification=off` | Packaging Build | Desktop Linux Bundle & Android Debug APK | 2 | 2 | 0 | 0 | PASS |
| 11 | `glean-hub` | `cargo test --locked` (in `glean-hub-sync`) | Backend Integration | Hub Sync Crate Tests | 55 | 54 | 0 | 1 | PASS*** |
| 12 | `glean-hub` | `cargo clippy -p glean-hub-sync --locked` | Static Analysis | Rust Code Quality & Linter | 1 | 1 | 0 | 0 | PASS |
| 13 | `glean-hub` | `pnpm test:unit` (in `glean-hub-app`) | Unit | Admin UI Service & Vite Preload Suite | 13 | 13 | 0 | 0 | PASS |
| 14 | `glean-hub` | `pnpm build` (in `glean-hub-app`) | Frontend Packaging | TypeScript Compilation & Vite Production Build | 1 | 1 | 0 | 0 | PASS |

\* *Diagnostic source check only; not a release APK signature/binary audit.*  
\*\* *Android JVM unit tests on host; not physical device/emulator instrumentation.*  
\*\*\* *1 ignored: `services::strong_sync_tests::test_strong_sync_live_mongo_acceptance_gate` (requires live MongoDB at `MONGO_TEST_URI`; Gate G3 hold).*

---

## 2. Test Suite Breakdowns & Execution Evidence

### 2.1 Strict-v2 Cutover Rehearsal Suite (`node scripts/strict-v2-cutover-rehearsal.mjs --self-test`)
- **Execution Mode**: In-process mock tooling self-test (`--self-test`).
- **Duration**: 10.84s wall.
- **Pass/Fail**: 7 passed, 0 failed, 0 skipped.
- **Verified Checks**:
  1. CLI runner outputs comprehensive help text on `-h`/`--help` (PASS).
  2. CLI fast-fails with exit code 1 when bearer token absent (PASS).
  3. CLI redacts sensitive admin bearer token from output logs/errors (PASS).
  4. Preflight dry-run verifies preconditions without mutating v1 schema (PASS).
  5. Interrupted POST recovers status and completes using identical migration ID (PASS).
  6. Status-only query asserts active status without initiating mutation (PASS).
  7. CLI fails closed with exit code 1 if final read-back columns diverge from canonical 16 columns (PASS).
- **Scope Boundary**: Confirms CLI orchestration logic against HTTP mocks; does not substitute for live MongoDB replica set migration.

### 2.2 Node Integration, Security & Fault Injection Suites
- **`node scripts/strict-v2-vector-runner.mjs`** (Duration: 0.05s, 23 checks PASS):
  - Canonical schema fingerprints: v1 (`3361a80f...`, 4 cols) & v2 (`5f539551...`, 16 cols) verified against JSON schema files.
  - 10 valid wire vectors verified (v1 minimal, v2 full deadline/reminder, explicit offsets, boundary lead minutes).
  - 11 negative rejection vectors verified (invalid date formats, missing fields, interval < 15m, count < 1, count > 10).
- **`node scripts/hub-live-smoke.mjs --fixture-test`** (Duration: 0.52s, 18 checks PASS):
  - Suite 1 (Health & App Admission): 2 checks (health check, strict admission mode & epoch).
  - Suite 2 (Security & Route Isolation): 3 checks (missing API key 403, invalid API key 403, legacy route rejection HTTP 426).
  - Suite 3 (Authentication & Session): 4 checks (register, login, refresh exchange, verify identity).
  - Suite 4 (Strict Strong Sync Operations): 7 checks (bootstrap protocol, invalid JWT 401, epoch mismatch 409, insert revision 1, idempotent frozen retry replay, revisionConflict, pull schema validation).
  - Suite 5 (Tombstone Deletion & Invariants): 2 checks (CAS delete produces permanent empty tombstone at rev 2, pull confirms tombstone empty data `{}`).
- **`node scripts/release-security-check.mjs --diagnose-source`** (Duration: 0.08s, 6 static rules PASS):
  - `usesCleartextTraffic="false"` verified in base manifest.
  - `allowBackup="false"` verified in base manifest.
  - Exported components audit: only `MainActivity` exported; `DoggoAlarmReceiver` strictly unexported (`exported="false"`).
  - XML security configs confirmed present (`backup_rules.xml`, `data_extraction_rules.xml`).
  - Hub origin enforces HTTPS (`https://api-glean-hub.qm-hub-v001.cloud`).
  - Source secret scan clean (no private keys, passwords, or hardcoded tokens).
- **`node scripts/hub-strict-upgrade.test.mjs`** (Duration: 10.97s, 9 tests PASS):
  - Help text exit 0, missing token nonzero exit, sensitive bearer token log redaction, HTTP 4xx immediate fast-fail without polling, preflight dry-run non-mutation, full live upgrade bounded polling and read-back assertion, interrupted POST recovery via same migration ID, `--status-only` inspection, and schema divergence fail-closed exit.
- **`node scripts/debug-fault-proxy.test.mjs`** (Duration: 0.19s, 8 tests PASS):
  - Allowlist verification including legacy receipts route, upstream pass-through, unlisted path 403 rejection with suppressed path reflection, drop-push-response fault, drop-legacy-receipts-response fault, 502 Bad Gateway handling, 504 Gateway Timeout handling, mid-stream connection drop handling.

### 2.3 Doggo Multiplatform Gradle Tests
- **Execution Command**: `export ANDROID_HOME="$HOME/Android/Sdk" JAVA_HOME="/home/loidinh/.local/share/jvm/jdk-21" && ./gradlew :shared:jvmTest :desktopApp:test :androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off`
- **Build Outcome**: `BUILD SUCCESSFUL in 21s` (63 actionable tasks: 63 executed).
- **Total Tests Executed**: 263 tests across 42 test classes (263 PASS, 0 FAIL, 0 SKIPPED).

#### 2.3.1 `:shared:jvmTest` Breakdown (28 Classes, 202 Tests PASS)
| Test Class | Tests | Pass | Fail | Skip |
|---|:---:|:---:|:---:|:---:|
| `dev.doggo.shared.auth.HubAuthClientTest` | 9 | 9 | 0 | 0 |
| `dev.doggo.shared.auth.HubAuthSessionTest` | 8 | 8 | 0 | 0 |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | 3 | 3 | 0 | 0 |
| `dev.doggo.shared.contract.AccountKeyTest` | 5 | 5 | 0 | 0 |
| `dev.doggo.shared.contract.AdversarialSchedulerBoundaryTest` | 7 | 7 | 0 | 0 |
| `dev.doggo.shared.contract.EndpointPolicyTest` | 8 | 8 | 0 | 0 |
| `dev.doggo.shared.contract.PersistedScheduleProjectionTest` | 5 | 5 | 0 | 0 |
| `dev.doggo.shared.contract.ReminderScheduleCalculatorTest` | 9 | 9 | 0 | 0 |
| `dev.doggo.shared.contract.StrictDigestTest` | 5 | 5 | 0 | 0 |
| `dev.doggo.shared.contract.StrictTimeZoneResolverTest` | 7 | 7 | 0 | 0 |
| `dev.doggo.shared.contract.StrictV2DigestExtendedTest` | 6 | 6 | 0 | 0 |
| `dev.doggo.shared.contract.StrictV2DigestVectorTest` | 5 | 5 | 0 | 0 |
| `dev.doggo.shared.contract.TaskDeadlineReminderModelsTest` | 6 | 6 | 0 | 0 |
| `dev.doggo.shared.contract.TitlePolicyTest` | 6 | 6 | 0 | 0 |
| `dev.doggo.shared.data.AdversarialSyncSqliteTest` | 5 | 5 | 0 | 0 |
| `dev.doggo.shared.data.ReminderOccurrenceStoreTest` | 4 | 4 | 0 | 0 |
| `dev.doggo.shared.data.ReminderSchedulingCoordinatorSqliteSmokeTest` | 2 | 2 | 0 | 0 |
| `dev.doggo.shared.data.ReminderTeardownBarrierSqliteTest` | 1 | 1 | 0 | 0 |
| `dev.doggo.shared.data.RoomAccountStoreTest` | 23 | 23 | 0 | 0 |
| `dev.doggo.shared.data.RoomMigrationV1ToV2Test` | 1 | 1 | 0 | 0 |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | 7 | 7 | 0 | 0 |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | 9 | 9 | 0 | 0 |
| `dev.doggo.shared.sync.ContractTransitionTest` | 4 | 4 | 0 | 0 |
| `dev.doggo.shared.sync.HubSyncClientTest` | 12 | 12 | 0 | 0 |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | 12 | 12 | 0 | 0 |
| `dev.doggo.shared.sync.ReminderDeliveryGateTest` | 4 | 4 | 0 | 0 |
| `dev.doggo.shared.sync.StagedFullScanTest` | 14 | 14 | 0 | 0 |
| `dev.doggo.shared.ui.TodoSemanticsTest` | 15 | 15 | 0 | 0 |

#### 2.3.2 `:desktopApp:test` Breakdown (10 Classes, 49 Tests PASS)
*Note: 49 tests verified in actual Gradle output (includes 3 tests in `DesktopReminderControllerLifecycleTest` and 6 tests in `LinuxReminderAdapterTest`; earlier Phase 09 Cycle 1 baseline had 47 tests prior to Cycle 2 regression test additions).*
| Test Class | Tests | Pass | Fail | Skip |
|---|:---:|:---:|:---:|:---:|
| `dev.doggo.desktop.CompositeDesktopLeaveCancelGateTest` | 2 | 2 | 0 | 0 |
| `dev.doggo.desktop.DesktopControllerEnvTest` | 4 | 4 | 0 | 0 |
| `dev.doggo.desktop.DesktopLifecycleTest` | 8 | 8 | 0 | 0 |
| `dev.doggo.desktop.DesktopReminderControllerLifecycleTest` | 3 | 3 | 0 | 0 |
| `dev.doggo.desktop.DesktopReminderSchedulerTest` | 6 | 6 | 0 | 0 |
| `dev.doggo.desktop.DotEnvLoaderTest` | 5 | 5 | 0 | 0 |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | 5 | 5 | 0 | 0 |
| `dev.doggo.desktop.linux.LinuxNotificationAdapterTest` | 4 | 4 | 0 | 0 |
| `dev.doggo.desktop.linux.LinuxReminderAdapterTest` | 6 | 6 | 0 | 0 |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | 6 | 6 | 0 | 0 |

#### 2.3.3 `:androidApp:testDebugUnitTest` Breakdown (4 Classes, 12 Tests PASS)
| Test Class | Tests | Pass | Fail | Skip |
|---|:---:|:---:|:---:|:---:|
| `dev.doggo.android.AndroidNotificationAdapterTest` | 6 | 6 | 0 | 0 |
| `dev.doggo.android.BuildConfigTest` | 1 | 1 | 0 | 0 |
| `dev.doggo.android.CompositeLeaveSchedulerCancelGateTest` | 3 | 3 | 0 | 0 |
| `dev.doggo.android.ReminderPermissionControllerTest` | 2 | 2 | 0 | 0 |

### 2.4 Glean Hub Backend Tests (`cargo test --locked`)
- **Directory**: `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync`
- **Result**: `ok. 54 passed; 0 failed; 1 ignored; finished in 30.01s`.
- **Ignored Test**: `services::strong_sync_tests::test_strong_sync_live_mongo_acceptance_gate` (explicitly guards live MongoDB at `MONGO_TEST_URI`).
- **Static Analysis**: `cargo clippy -p glean-hub-sync --locked` finished in 0.21s with 0 warnings and 0 errors.

### 2.5 Glean Hub Admin App Tests (`pnpm test:unit` & `pnpm build`)
- **Directory**: `/home/loidinh/WS/glean-hub/glean-hub-app`
- **Unit Tests**: 13 passed across 2 suites (148ms):
  - `sync-admin-service strict upgrade contracts`: 8 tests PASS (`getAppSchema`, special char encoding, `getStrictContract`, 403 handling, `startStrictUpgrade`, `dryRun`, `getStrictUpgradeStatus`, rejection of strict updates).
  - `registerVitePreloadRecovery`: 5 tests PASS (event prevention/single reload, consumed marker through load, hashed entry URL build marker, query-string token stripping, session storage fallback).
- **Production Build**: `pnpm build` (`tsc && vite build --mode production`): SUCCESS with warnings. 10,411 modules transformed cleanly, 0 TypeScript errors, built in 13.42s; non-fatal notices for runtime font resolution, dynamic import chunk deduplication, and minified chunks > 500 kB in embed apps.

---

## 3. Coverage & Performance Metrics

### Coverage Metrics
- **KMP & Android**: Automated line and branch coverage instrumentation (e.g. Jacoco/Kover) is not configured in repository build scripts. Tests exercise target contract behaviors, Room migrations, intent masking, outbox reconciliation, scheduler boundaries, and UI semantics; line/branch coverage was not measured.
- **Node Scripts**: Mock harness and unit test suites exercise targeted CLI argument branches, token redaction paths, dry-run paths, interrupted POST recovery paths, and schema read-back column verification.
- **Rust Backend**: All v1/v2 schema validations, permit gate bounds, and app-scoped admin middleware exercised. Live MongoDB cluster transaction branches remain unexecuted.
### Performance Metrics & Durations
- `strict-v2-cutover-rehearsal.mjs --self-test`: 10.84s (mock HTTP server delay simulation).
- `hub-strict-upgrade.test.mjs`: 10.97s (bounded polling and interrupt recovery).
- `Gradle KMP tests (:shared:jvmTest, :desktopApp:test, :androidApp:testDebugUnitTest)`: 21.0s (compilation + execution of 263 tests).
- `cargo test --locked`: 30.01s (54 unit/integration tests).
- `pnpm test:unit`: 148ms.
- **Slow Tests Identified**: Rehearsal and CLI tests intentionally simulate 10s timeout/recovery cycles (`handles interrupted POST by recovering status using the same migration ID`: 10.28s; `[Rehearsal 5]`: 10.0s).

---

## 4. Build Status & Compilation Warnings

- **Doggo Multiplatform Build**: `BUILD SUCCESSFUL in 21s`.
  - Non-fatal compiler warning: KSP expect/actual beta warning (`-Xexpect-actual-classes`) on `DoggoDatabaseConstructor.kt` and `doggo-database.kt`.
  - Non-fatal warning: Java `URL` constructor deprecation in `platform-vault-dispatcher-test.kt:69`.
  - Non-fatal warning: Instance check warning in `reminder-permission-controller-test.kt`.
  - Deprecated Gradle features warning (incompatible with Gradle 9.0).
- **Packaging Targets**:
  - `:desktopApp:createDistributable`: SUCCESS (Linux distributable bundle created).
  - `:androidApp:assembleDebug`: SUCCESS (Debug APK created, `BUILD SUCCESSFUL in 3s`).
- **Glean Hub Admin Build**: `pnpm build` SUCCESS with warnings.
  - Non-fatal font resolution notices (`/fonts/jetbrains-mono/...` unresolved at build time, deferred for runtime resolution).
  - Rollup warnings: dynamic import chunk consolidation and embed app chunks larger than 500 kB.
---

## 5. Critical Issues, Gating Status & Release Boundaries

### Release Gates Summary

| Gate | Phase / Focus | Status | Hard Blockers & Execution Gaps |
|:---:|---|:---:|---|
| **G1** | Contract Freeze & Transition Design | **PASSED** | Canonical v1/v2 schemas and tri-stack vectors frozen. |
| **G2** | Physical Native Platform Delivery | **BLOCKED** | 1. Android physical Doze timing (`setExactAndAllowWhileIdle`) and reboot persistence unobserved on physical device.<br>2. Linux session notification daemon (`org.freedesktop.Notifications`) display unobserved in headless environment.<br>3. Windows native COM/WinRT toast bridge is unimplemented (candidate stub returns safe `Unavailable`). |
| **G3** | Hub Cluster Staging & Admission Fence | **BLOCKED** | 1. Multi-instance MongoDB replica set live migration unexecuted (`MONGO_TEST_URI` gate test ignored).<br>2. Two-node Hub admission fence race conditions unverified under live cluster traffic.<br>3. Three open Phase 03 backend code review findings unaddressed: duplicate permit release counter drift, missing `permitCount` backfill, and batch/checkpoint write atomicity. |
| **G4** | Client Version Transition & Safe Replay | **PASSED** | Room v1→v2 migration, intent-masked rebasing, and outbox reconciliation verified. |
| **G5** | Shared Scheduling & Account Fences | **PASSED** | Save-state scheduling coordinator, claim-before-post, and wipe teardown fences verified. |
| **G6** | Adversarial Acceptance & 3-Device Proof | **HELD** | Automated regressions pass; live 3-device concurrent sync proof between Android, Linux, and Windows held pending physical hardware testbeds. |
| **G7** | Live Production Cutover | **BLOCKED** | Runbook and rehearsal tooling delivered; live execution strictly blocked until Gates G2, G3, G6 clear and dependency verification metadata regenerated. |

### Immediate Blocking Issues for Production Cutover
1. **Gate G2 Platform Hardware Deficit**: Automated unit tests execute on host JVM; physical Android device Doze behavior, desktop notification bubble rendering, and native Windows WinRT/COM toast notifications are unproven (Windows adapter candidate stub returns safe `Unavailable` per Phase 02/09 records).
2. **Gate G3 Live Replica Set & Backend Findings**: Staging MongoDB cluster migration unexecuted (`MONGO_TEST_URI` test ignored); three open Phase 03 code findings (permit release duplicate filter, `permitCount` backfill, and batch/checkpoint write consistency per Phase 03 review records) remain unaddressed.
3. **Supply-Chain Dependency Verification**: Gradle test runs utilized `--dependency-verification=off`; validation with dependency verification active remains outstanding and must be executed prior to production release.
4. **Disaster Recovery Quarantine**: `scripts/hub-strict-recovery.js` confirmed quarantined for strict-v1 cold disaster recovery only; cannot be used for v2 upgrades or rollbacks.

---

## 6. Recommendations & Next Steps

### Actionable Recommendations
1. **Windows Native Toast Implementation**: Implement native WinRT/COM notification dispatch or external PowerShell toast bridge to replace the fail-closed `Unavailable` stub in `WindowsNotificationAdapter`.
2. **Backend Remediations (Phase 03 Findings)**:
   - Patch `permit_service.rs` to filter duplicate releases on `activePermits.<permitId>: { $exists: true }`.
   - Implement `permitCount` schema backfill in migration preflight.
   - Combine batch mutation and checkpoint update into an atomic write transaction.
3. **Staging Multi-Node Testbed**: Provision live multi-node MongoDB replica set and two-node Hub staging instance to execute `test_strong_sync_live_mongo_acceptance_gate` and validate Gate G3.
4. **Supply Chain Provenance & Verification**: Review any newly added or updated dependency artifacts against trusted upstream provenance, regenerate checksum locks (`./gradlew --write-verification-metadata sha256`), and validate builds with dependency verification enabled.

### Prioritized Next Steps
1. Resolve three Phase 03 Rust backend code findings.
2. Deploy staging MongoDB replica set and execute live cluster migration rehearsal (`--staging-origin`).
3. Implement Windows native toast notification bridge.
4. Conduct physical multiplatform acceptance testing (Android Doze, Linux session D-Bus, Windows installed runtime).
5. Regenerate dependency verification metadata.
6. Submit Gate G7 approval request once all prerequisite gates clear.

---

## 7. Unresolved Questions

1. **Windows Native Toast Binding**: Will Windows native notifications be implemented via JNA WinRT/COM COM bindings in `:desktopApp` or via an external packaged PowerShell helper binary?
2. **Staging Cluster Provisioning Schedule**: When will the multi-node MongoDB replica set and multi-instance Hub staging environment be available to execute the live Gate G3 admission fence test?
3. **Dependency Verification Key Trust**: Does the team intend to verify dependency artifacts against trusted upstream repository keys prior to regenerating Gradle verification metadata?
