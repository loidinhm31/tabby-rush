# QA Test Execution Report: Phase 06 Client Version Transition & Safe Replay

**Date**: 2026-10-15  
**Context**: Linux Fedora 44 x86_64 | JDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Gradle 8.11.1 | Node.js v24.16.0  
**Target Phase**: `phase-06-client-version-transition-and-replay`  
**Working Directory**: `/home/loidinh/WS/doggo`  

---

## 1. Test Results Overview

| Index | Command | Scope | Category | Total | Pass | Fail | Skip | Status |
|:---:|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Shared KMP JVM test suite | Unit/Integration | 155 | 155 | 0 | 0 | PASS |
| 2 | `JAVA_HOME=... ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | Desktop App test suite | Unit/Integration | 32 | 32 | 0 | 0 | PASS |
| 3 | `node scripts/strict-v2-vector-runner.mjs` | Wire vector validation & schema fingerprints | Contract Vectors | 23 | 23 | 0 | 0 | PASS |
| 4 | `node scripts/hub-live-smoke.mjs --fixture-test` | Offline fixture protocol smoke test | Protocol Fixture | 18 | 18 | 0 | 0 | PASS |
| 5 | `node scripts/release-security-check.mjs --diagnose-source` | Static manifest, export, origin, secret rules | Security Audit | 6 | 6 | 0 | 0 | PASS |
| 6 | `node scripts/hub-strict-upgrade.test.mjs` | Hub strict upgrade CLI suite | Integration CLI | 9 | 9 | 0 | 0 | PASS |

**Total**: 243 executed checks / tests. 243 passed, 0 failed, 0 skipped within executed test suites.

---

## 2. Shared JVM Test Breakdown (`:shared:jvmTest`)

- Total Test Classes: 20
- Total Tests: 155
- Passed: 155, Failed: 0, Skipped: 0
- Direct Exit Code: 0 (`BUILD SUCCESSFUL in 6s`, 15 actionable tasks executed)

| Test Class | Scope / Target | Passed | Failed | Skipped |
|---|---|:---:|:---:|:---:|
| `dev.doggo.shared.auth.HubAuthClientTest` | Registration, login, token refresh, access request | 9 | 0 | 0 |
| `dev.doggo.shared.auth.HubAuthSessionTest` | Auth state machine, local logout, 401 guard | 8 | 0 | 0 |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | Refresh deduplication, concurrency, safe error | 3 | 0 | 0 |
| `dev.doggo.shared.contract.AccountKeyTest` | Account key framing, determinism, validation | 5 | 0 | 0 |
| `dev.doggo.shared.contract.EndpointPolicyTest` | Origin sanitization, HTTPS enforcement, debug rules | 8 | 0 | 0 |
| `dev.doggo.shared.contract.StrictDigestTest` | SHA-256 vectors, deterministic canonical payload | 5 | 0 | 0 |
| `dev.doggo.shared.contract.StrictTimeZoneResolverTest` | Standard time, DST overlap/gap, 23/25h boundary | 7 | 0 | 0 |
| `dev.doggo.shared.contract.StrictV2DigestExtendedTest` | Negative epoch, unicode title/zone, max i64 rev | 6 | 0 | 0 |
| `dev.doggo.shared.contract.StrictV2DigestVectorTest` | Date-only, repeating count, null defaults, DST | 5 | 0 | 0 |
| `dev.doggo.shared.contract.TaskDeadlineReminderModelsTest` | Valid date-only/timed, last reminder bounds, tokens | 6 | 0 | 0 |
| `dev.doggo.shared.contract.TitlePolicyTest` | Length bounds, surrogate validation, astral emoji | 6 | 0 | 0 |
| `dev.doggo.shared.data.RoomAccountStoreTest` | BundledSQLite persistence, non-destructive rebasing, atomic cleanup, schedule token rotation | 21 | 0 | 0 |
| `dev.doggo.shared.data.RoomMigrationV1ToV2Test` | Explicit v1->v2 Room schema migration | 1 | 0 | 0 |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Teardown guard, fail-closed wipe, review check | 7 | 0 | 0 |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Upgrade detection, quiescence, receipt rebase, v2 push | 9 | 0 | 0 |
| `dev.doggo.shared.sync.ContractTransitionTest` | Transition coordinator, phase resume, superseded blocks, review gating | 4 | 0 | 0 |
| `dev.doggo.shared.sync.HubSyncClientTest` | Bootstrap v2, receipt fetch, push/pull envelopes | 12 | 0 | 0 |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | Optimistic lock, FIFO batch, receipt match, CAS ack | 12 | 0 | 0 |
| `dev.doggo.shared.sync.StagedFullScanTest` | Multi-page full scan, monotonic promotion, cursor loop | 13 | 0 | 0 |
| `dev.doggo.shared.ui.TodoSemanticsTest` | Semantics, accessibility contrast, conflict warning copy | 8 | 0 | 0 |

---

## 3. Desktop App Test Breakdown (`:desktopApp:test`)

- Total Test Classes: 6
- Total Tests: 32
- Passed: 32, Failed: 0, Skipped: 0
- Direct Exit Code: 0 (`BUILD SUCCESSFUL in 6s`, 19 actionable tasks executed)

| Test Class | Scope / Target | Passed | Failed | Skipped |
|---|---|:---:|:---:|:---:|
| `dev.doggo.desktop.DesktopControllerEnvTest` | Environment bootstrap, hub config parsing | 4 | 0 | 0 |
| `dev.doggo.desktop.DesktopLifecycleTest` | App startup, shutdown hooks, controller dispatch | 8 | 0 | 0 |
| `dev.doggo.desktop.DotEnvLoaderTest` | Local env parsing, comments, precedence | 5 | 0 | 0 |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | Vault resolution across Linux/macOS/Windows | 5 | 0 | 0 |
| `dev.doggo.desktop.linux.LinuxNotificationAdapterTest` | DBus freedesktop notification interface | 4 | 0 | 0 |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | Linux libsecret / secret-service integration | 6 | 0 | 0 |

---

## 4. Vector Runner Breakdown (`strict-v2-vector-runner.mjs`)

- Schema Fingerprints (2/2 PASS):
  - v1 SHA-256: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
  - v2 SHA-256: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
- Valid Vectors (10/10 PASS): date-only minimal, timed DST overlap, repeating reminder count 10, clear deadline, zero data delete, migrated null defaults, disabled rule with deadline, unicode title/zone, max i64 expected revision, negative epoch instant.
- Rejection Vectors (11/11 PASS): revision out of range, payload on delete, negative created timestamp, negative lead minutes, repetition count out of bounds, interval not null when count=1, interval < 15 min, deadline parameters present without deadlineDate, missing required fields when deadlineDate present, invalid reminder anchor on timed deadlines, last reminder after anchor.

---

## 5. Hub Live Smoke Fixture Breakdown (`hub-live-smoke.mjs --fixture-test`)

- Note: Executes against offline in-process fixture mock server under strict-v1 protocol. Does not exercise live strict-v2 cutover.
- Suite 1 (Health & App Admission): 2/2 PASS
- Suite 2 (Security & Negative Route Isolation): 3/3 PASS
- Suite 3 (Authentication & Access Session): 4/4 PASS
- Suite 4 (Strict Strong Sync Operations): 7/7 PASS
- Suite 5 (Tombstone Deletion & Invariants): 2/2 PASS
- Total: 18/18 checks PASS.

---

## 6. Security & Diagnostic Audit (`release-security-check.mjs --diagnose-source`)

- Note: Static source analysis only. Does not validate binary APK v2/v3 signatures or hardware Keystore bindings.
- Base manifest prohibits cleartext traffic (`usesCleartextTraffic="false"`): PASS
- Base manifest restricts backup (`allowBackup="false"`): PASS
- Exported components audit: Only `MainActivity` exported: PASS
- XML security configs present (`backup_rules.xml`, `data_extraction_rules.xml`): PASS
- Release Hub origin enforces HTTPS (`https://api-glean-hub.qm-hub-v001.cloud`): PASS
- Repository source secret audit: 0 hardcoded secrets detected in source: PASS
- Result: CLEAN (Source meets static security rules).

---

## 7. Hub Strict Upgrade CLI Breakdown (`hub-strict-upgrade.test.mjs`)

- Total Test Suites: 1
- Total Tests: 9
- Passed: 9, Failed: 0, Skipped: 0
- Execution Time: ~10.8s
- Verified Capabilities:
  - `--help` exit code 0 and usage instructions
  - Nonzero exit on missing authorization token
  - Bearer token redaction in stderr logs
  - Fast-fail on HTTP 4xx rejections without polling
  - Preflight dry-run execution without schema version mutation
  - Live upgrade with bounded polling and final schema read-back assertion
  - Idempotent recovery after interrupted POST using migration ID
  - `--status-only` query mode
  - Exit code 1 rejection on schema column mismatch

---

## 8. Failure-Path & Contract Invariants Verified

1. **Non-Destructive Intent-Masked Rebasing**: `RoomAccountStore.reconcileAccepted` preserves remote v2 deadline fields when reconciling v1 outbox updates or title-only v2 edits against equal/higher revision base.
2. **Atomic Regressive Reconciliation Cleanup**: `DoggoDao.discardRegressiveReconcileAtomic` wraps `deleteDraft` and `deleteOutbox` into single SQLite transaction preventing orphaned stale edits when server revision regresses.
3. **Dispatch Quiescence & Checkpoint Resumption**: `ContractTransitionTest.testCrashResumptionFromCheckpoints` proves coordinator resumes from intermediate `V2_BASE_SCANNING` phase without re-requesting legacy receipts.
4. **Receipt Ambiguity Conflict Preservation**: `ContractTransitionTest.testLegacyReceiptUnknownSupersededBlocksWithExplicitConflict` proves receipts with `unknownSuperseded` transition to `STATE_UNKNOWN_SUPERSEDED` for explicit user resolution rather than silent dropping or automated re-ID.
5. **Review-Gated Intention Safety**: `ContractTransitionTest.testRequiresReviewDoesNotAutoTranslate` verifies ambiguous intentions marked with `INTENT_REQUIRES_REVIEW` transition to `STATE_CONFLICT` rather than automated v2 translation.
6. **Schedule Token Rotation**: `RoomAccountStoreTest.testScheduleTokenPersistedAndRotatedOnSemanticChange` explicitly asserts scheduleToken rotates to new UUID upon semantic change and persists across reloads.
7. **Delivery Files Tracking**: Implementation introduces `shared/src/commonMain/kotlin/dev/doggo/shared/sync/contract-transition-coordinator.kt` and `shared/src/commonTest/kotlin/dev/doggo/shared/sync/contract-transition-test.kt` which must be included in deliverable commit.

---

## 9. Build Status & Validation Limitations

- Shared JVM Test Suite: BUILD SUCCESSFUL (15 actionable tasks, 7.05s wall time)
- Desktop App Test Suite: BUILD SUCCESSFUL (19 actionable tasks, 6.47s wall time)
- Flakiness: None observed across fresh `--rerun-tasks` executions
- **Validation Limitation 1**: Offline fixture smoke exercises strict-v1 in-process server; live strict-v2 backend cutover and migration verification reserved for Phase 11 multi-device tests.
- **Validation Limitation 2**: Security diagnostics test static properties; binary APK signatures and runtime Keystore execution require device/emulator release runs.
- **Validation Limitation 3**: `--dependency-verification=off` was used due to unregenerated Gradle dependency checksum metadata; verification must be restored prior to release packaging.

---

## 10. Critical Issues

No failures observed in executed test suites. Implementation review, code formatting, Android device testing, and live-cutover cluster validation remain outside this evidence.

---

## 11. Recommendations

1. Restore Gradle dependency verification metadata before final release packaging.
2. Run Android instrumented test suite (`:shared:connectedAndroidTest` or `androidInstrumentedTest`) when connected emulator environment is available.
3. Commit new transition files (`contract-transition-coordinator.kt` and `contract-transition-test.kt`) along with shared sync updates.

---

## 12. Unresolved Questions

1. Will end-to-end device testing with live server cutover (Phase 11) require multi-device real-time sync replay fixtures, or are scripted node harness scenarios sufficient for Gate G4 sign-off?
