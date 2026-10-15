# QA Test Execution Report: Phase 05 Shared Model & Explicit Room Migration

**Date**: 2026-10-15  
**Context**: Linux Fedora 44 x86_64 | JDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Gradle 8.11.1 | Node.js v24.16.0  
**Target Phase**: `phase-05-shared-model-and-room-migration`  
**Working Directory**: `/home/loidinh/WS/doggo`  

---

## 1. Test Results Overview

| Index | Command | Scope | Category | Total | Pass | Fail | Skip | Status |
|:---:|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Shared KMP JVM test suite | Unit/Integration | 128 | 128 | 0 | 0 | PASS |
| 2 | `node scripts/strict-v2-vector-runner.mjs` | Wire vector validation & schema fingerprints | Contract Vectors | 23 | 23 | 0 | 0 | PASS |
| 3 | `node scripts/hub-live-smoke.mjs --fixture-test` | Offline fixture protocol smoke test | Protocol Fixture | 18 | 18 | 0 | 0 | PASS |
| 4 | `node scripts/release-security-check.mjs --diagnose-source` | Static manifest, export, origin, secret rules | Security Audit | 6 | 6 | 0 | 0 | PASS |

**Total**: 175 executed checks / tests. 175 passed, 0 failed, 0 skipped.

---

## 2. Shared JVM Test Breakdown (`:shared:jvmTest`)

- Total Test Classes: 19
- Total Tests: 128
- Passed: 128, Failed: 0, Skipped: 0

| Test Class | Scope / Target | Passed | Failed | Skipped |
|---|---|:---:|:---:|:---:|
| `AccountKeyTest` | Account key framing, determinism, validation | 5 | 0 | 0 |
| `AccountLeaveProtocolTest` | Teardown guard, fail-closed wipe, review check | 7 | 0 | 0 |
| `AccountSyncCoordinatorTest` | Epoch conflict, copy/reapply/discard resolution | 5 | 0 | 0 |
| `EndpointPolicyTest` | Origin sanitization, HTTPS enforcement, debug rules | 8 | 0 | 0 |
| `HubAuthClientTest` | Registration, login, token refresh, access request | 9 | 0 | 0 |
| `HubAuthSessionTest` | Auth state machine, local logout, 401 guard | 8 | 0 | 0 |
| `HubSyncClientTest` | Bootstrap, push/pull envelopes, tombstone payloads | 7 | 0 | 0 |
| `OutboxReconciliationTest` | Optimistic lock, FIFO batch, CAS ack, uncertain retry | 10 | 0 | 0 |
| `RoomAccountStoreTest` | BundledSQLite persistence, draft recovery, v2 token | 14 | 0 | 0 |
| `RoomMigrationV1ToV2Test` | Explicit v1->v2 Room schema migration | 1 | 0 | 0 |
| `SingleFlightRefreshTest` | Refresh deduplication, concurrency, safe error | 3 | 0 | 0 |
| `StagedFullScanTest` | Multi-page full scan, monotonic promotion, cursor loop | 9 | 0 | 0 |
| `StrictDigestTest` | SHA-256 vectors, deterministic canonical payload | 5 | 0 | 0 |
| `StrictTimeZoneResolverTest` | Standard time, DST overlap/gap, 23/25h boundary | 7 | 0 | 0 |
| `StrictV2DigestExtendedTest` | Negative epoch, unicode title/zone, max i64 rev | 6 | 0 | 0 |
| `StrictV2DigestVectorTest` | Date-only, repeating count, null defaults, DST | 5 | 0 | 0 |
| `TaskDeadlineReminderModelsTest` | Valid date-only/timed, last reminder bounds, tokens | 6 | 0 | 0 |
| `TitlePolicyTest` | Length bounds, surrogate validation, astral emoji | 6 | 0 | 0 |
| `TodoSemanticsTest` | Semantics, accessibility contrast, redacted toString | 7 | 0 | 0 |

---

## 3. Vector Runner Breakdown (`strict-v2-vector-runner.mjs`)

- Schema Fingerprints (2/2 PASS):
  - v1 SHA-256: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
  - v2 SHA-256: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
- Valid Vectors (10/10 PASS): date-only minimal, timed DST overlap, repeating reminder count 10, clear deadline, zero data delete, migrated null defaults, disabled rule with deadline, unicode title/zone, max i64 expected revision, negative epoch instant.
- Rejection Vectors (11/11 PASS): revision out of range, payload on delete, negative created timestamp, negative lead minutes, repetition count out of bounds, interval not null when count=1, interval < 15 min, deadline parameters present without deadlineDate, missing required fields when deadlineDate present, invalid reminder anchor on timed deadlines, last reminder after anchor.

---

## 4. Hub Live Smoke Fixture Breakdown (`hub-live-smoke.mjs --fixture-test`)

- Suite 1 (Health & App Admission): 2/2 PASS
- Suite 2 (Security & Negative Route Isolation): 3/3 PASS
- Suite 3 (Authentication & Access Session): 4/4 PASS
- Suite 4 (Strict Strong Sync Operations): 7/7 PASS
- Suite 5 (Tombstone Deletion & Invariants): 2/2 PASS
- Total: 18/18 checks PASS.

---

## 5. Security & Diagnostic Audit (`release-security-check.mjs --diagnose-source`)

- Base manifest prohibits cleartext traffic (`usesCleartextTraffic="false"`): PASS
- Base manifest restricts backup (`allowBackup="false"`): PASS
- Exported components audit: Only `MainActivity` exported: PASS
- XML security configs present (`backup_rules.xml`, `data_extraction_rules.xml`): PASS
- Release Hub origin enforces HTTPS (`https://api-glean-hub.qm-hub-v001.cloud`): PASS
- Repository source secret audit: No matches under the script's rules (0 hardcoded secrets): PASS
- Result: CLEAN (Source meets static security rules).

---

## 6. Coverage Boundaries & Limitations

1. **Offline Fixture Smoke vs Live Cluster**: `hub-live-smoke.mjs --fixture-test` exercises strict-v1 protocol contracts against an in-process mock server; it does not validate a live backend cluster or live strict-v2 migration.
2. **Static Source Security vs Binary APK Signatures**: `release-security-check.mjs --diagnose-source` verifies static manifest and source properties; binary APK v2/v3 signatures and runtime Keystore execution require device/emulator release runs.
3. **Desktop JVM BundledSQLite vs Android Platform**: `:shared:jvmTest` executes on desktop JVM with BundledSQLite; Android instrumented Room migrations require device or emulator execution (`connectedAndroidTest`).
4. **Migration Invariants**: `RoomMigrationV1ToV2Test` explicitly asserts that existing v1 accounts, drafts, outbox intents, and frozen payloads survive migration to v2. Both JVM and Android builders register `MIGRATION_1_2`.

---

## 7. Build Status & Performance Metrics

- Shared JVM Test Suite: BUILD SUCCESSFUL (15 actionable tasks executed, 6.46s wall time)
- Vector Runner: PASS (Duration not captured)
- Fixture Smoke: PASS (Duration not captured)
- Diagnostic Security Audit: CLEAN (Duration not captured)
- Failures: 0
- Flakiness: No flakiness observed in executed runs

---

## 8. Critical Issues

None. All 175 checks pass cleanly.

---

## 9. Recommendations

1. Keep `--dependency-verification=off` scoped until `gradle/verification-metadata.xml` checksums regenerated.
2. Run Android instrumented test suite (`:shared:connectedAndroidTest` or `androidInstrumentedTest`) on an emulator when Android release verification begins.

---

## 10. Unresolved Questions

1. When will connected Android device/emulator environments be available to validate instrumented Keystore and Android-specific SQLite Room migration paths?
