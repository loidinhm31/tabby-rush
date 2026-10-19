# QA Report: Phase 01 — Freeze v3 Contract & Hub Upgrade Verification

**Identifier**: qa-261019-1754-phase-01-freeze-v3-contract-hub-upgrade
**Date**: 2026-10-19
**Author**: Phase1Tester (Senior QA Engineer)
**Repositories**: TabbyRush (`/home/loidinh/WS/tabby-rush`), GleanHub (`/home/loidinh/WS/glean-hub`)
**Scope**: Plan Phase 01 verification suite covering strict-v3 contract freeze, schema fingerprints, golden digest vectors, v2 regression vectors, CLI upgrade state machine, and Glean Hub Rust v3 models/receipts.

---

## Sequential Thinking Steps

1. **Step 1 - TabbyRush Shared JVM Tests**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` (also verified clean with `--dependency-verification=strict`)
   - Scope: 38 test suites across shared library (auth, strict contract, digest vectors, Room database, scheduling, sync coordinator, UI semantics).
   - Result: 297/297 passed, 0 failed, 0 skipped. Execution time: ~11.1s - 15.9s.
   - Highlights: `StrictV3DigestVectorTest` (10/10 passed), `StrictV2DigestVectorTest` (5/5 passed), `StrictV2DigestExtendedTest` (6/6 passed), `StrictDigestTest` (5/5 passed).

2. **Step 2 - Node.js Strict-v3 Vector Runner**:
   - Command: `node scripts/strict-v3-vector-runner.mjs`
   - Scope: Strict-v3 schema canonical fingerprint validation (`7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f`), 6 valid golden preimage/hash assertions, 5 rejection vectors (malformed JSON, self-parent, cross-user, depth violation, immutable link alteration).
   - Result: 11/11 vector checks passed (6 valid + 5 rejections), 0 failed. Execution time: 0.08s.

3. **Step 3 - Node.js Strict-v2 Vector Runner Regression**:
   - Command: `node scripts/strict-v2-vector-runner.mjs`
   - Scope: Regression verification of strict-v1 and strict-v2 canonical fingerprints (`132f914fc1a9856d5c9f2be8a145b2c41ddb1db1393efa76546f82e253924cd8` / `8d641c2de4142e6f62f6691d6fe91559076f6c96f7437b9467577075fd72c653`), 10 valid vectors, 11 rejection vectors.
   - Result: 21/21 vector checks passed (10 valid + 11 rejections), 0 failed. Execution time: 0.09s.

4. **Step 4 - Node.js Hub Strict Upgrade CLI Test Suite**:
   - Command: `node --test scripts/hub-strict-upgrade.test.mjs`
   - Scope: 13 CLI upgrade harness tests covering option parsing, token redaction in logs/errors, 4xx fast-fail, dry-run mode, bounded polling, resume/interrupted migration ID, status-only query, epoch/column validation, and simulated v2->v3 17-column upgrade with final read-back assertion.
   - Result: 13/13 passed, 0 failed, 0 skipped. Execution time: 11.29s.

5. **Step 5 - Glean Hub Rust Cargo Test Suite**:
   - Command: `cargo test --manifest-path /home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml --locked`
   - Scope: 63 discovered tests in `glean-hub-sync` covering schema validation, migration state roundtripping, v3 golden vectors, v2 legacy receipts parsing, v3 upgrade request parsing, 17-column ordering, and strong sync DTO validation.
   - Result: 62 passed, 0 failed, 1 ignored (`test_strong_sync_live_mongo_acceptance_gate` - requires live MongoDB at MONGO_TEST_URI). Execution time: 30.01s.

6. **Synthesis**:
   - Across the 5 verification targets, 372 tests and 32 vector/rejection checks passed (404 passing verifications, 0 failures, 1 ignored live MongoDB gate).
   - Strict canonical schema fingerprint and digest parity verified across Kotlin, Node.js, and Rust runtimes.
   - JVM validation confirmed under both `--dependency-verification=off` and `--dependency-verification=strict`.

---

## Test Results Overview

| Test Suite / Target | Exact Command | Discovered / Run | Passed | Failed | Skipped / Ignored | Result |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TabbyRush Kotlin JVM Tests** | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off` | 297 tests | 297 | 0 | 0 | **PASS** |
| **Node.js Strict-v3 Vector Runner** | `node scripts/strict-v3-vector-runner.mjs` | 11 checks (6 valid + 5 reject) | 11 | 0 | 0 | **PASS** |
| **Node.js Strict-v2 Vector Runner (Regression)** | `node scripts/strict-v2-vector-runner.mjs` | 21 checks (10 valid + 11 reject) | 21 | 0 | 0 | **PASS** |
| **Node.js CLI Upgrade Suite** | `node --test scripts/hub-strict-upgrade.test.mjs` | 13 tests | 13 | 0 | 0 | **PASS** |
| **Glean Hub Rust Cargo Test Suite** | `cargo test --manifest-path .../Cargo.toml --locked` | 63 tests | 62 | 0 | 1 (Live Mongo) | **PASS** |

### Verification Totals
- **Test Executions**: **372 passed** (297 JVM + 13 CLI + 62 Rust), 0 failed.
- **Golden Vector & Rejection Checks**: **32 passed** (11 strict-v3 + 21 strict-v2), 0 failed.
- **Total Passing Verifications**: **404 passed**, 0 failures, 1 ignored test (`test_strong_sync_live_mongo_acceptance_gate`).

---

## Detailed Suite Breakdown

### 1. TabbyRush Shared JVM Tests (`:shared:jvmTest`)
- **Status**: BUILD SUCCESSFUL (16 actionable tasks executed).
- **Counts**: 38 test suites, 297 tests passed, 0 failures, 0 skipped.
- **Dependency Verification**: Passed with `--dependency-verification=off` (as requested) and confirmed passing with `--dependency-verification=strict`.
- **Key Suites**:
  - `StrictV3DigestVectorTest`: 10 passed (v3 canonical vector assertions and rejection rules).
  - `StrictV2DigestVectorTest`: 5 passed (v2 regression).
  - `StrictV2DigestExtendedTest`: 6 passed.
  - `StrictDigestTest`: 5 passed (v1 regression).
  - `RemoteContractVectorTest`: 8 passed.
  - `RoomAccountStoreTest`: 25 passed.
  - `AdversarialSyncSqliteTest`: 5 passed.
  - `TaskSurfaceStoreSqliteTest`: 10 passed.
  - `TodoSemanticsTest`: 19 passed.
  - `HubSyncClientTest`: 12 passed.
  - `OutboxReconciliationTest`: 12 passed.
  - `StagedFullScanTest`: 14 passed.

### 2. Node.js Strict-v3 Vector Runner (`strict-v3-vector-runner.mjs`)
- **Status**: PASS.
- **Counts**: 11 checks (6 valid golden vectors + 5 rejection vectors).
- **Validation**:
  - Verified v3 canonical schema fingerprint: `7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f`.
  - Valid vectors: create root, create child, update root, update child, delete root, delete child.
  - Rejection vectors: malformed JSON, self-parent cyclic reference, cross-user parent reference, depth > 2 hierarchy attempt, immutable link reparenting.

### 3. Node.js Strict-v2 Vector Runner Regression (`strict-v2-vector-runner.mjs`)
- **Status**: PASS.
- **Counts**: 21 checks (10 valid vectors + 11 rejection vectors).
- **Validation**:
  - Verified v1 fingerprint: `132f914fc1a9856d5c9f2be8a145b2c41ddb1db1393efa76546f82e253924cd8`.
  - Verified v2 fingerprint: `8d641c2de4142e6f62f6691d6fe91559076f6c96f7437b9467577075fd72c653`.
  - All v2 temporal, reminder, and deadline vectors pass without regressions.

### 4. Node.js CLI Upgrade Test Suite (`hub-strict-upgrade.test.mjs`)
- **Status**: PASS (11.29s).
- **Counts**: 13 passed, 0 failed, 0 skipped across 1 suite.
- **CLI Scenarios Exercised**:
  - Usage instructions on `--help`.
  - Sensitive bearer token redaction in stderr and error logs.
  - Immediate fail-fast without polling on 4xx rejections.
  - Preflight `--dry-run` safety without mutating schema version.
  - Live upgrade harness with bounded polling and final read-back assertion.
  - Interrupted POST recovery with stable migration ID.
  - `--status-only` query validation.
  - Epoch and canonical column validation on read-back.
  - Simulated upgrade from strict-v2 to strict-v3 with 17 columns and final read-back assertion.

### 5. Glean Hub Rust Cargo Test Suite (`glean-hub-sync`)
- **Status**: PASS (30.01s).
- **Counts**: 62 passed, 0 failed, 1 ignored.
- **Key Suites & Functions Tested**:
  - `models::strict_upgrade::tests::test_validate_strict_v3_schema_success`: OK.
  - `models::strong_sync_v3::tests::test_strict_v3_vectors_against_fixture`: OK.
  - `services::strict_upgrade_tests::test_v3_schema_requires_all_17_columns_in_order`: OK.
  - `services::strict_upgrade_tests::test_v3_upgrade_request_parsing`: OK.
  - `services::strong_sync_tests::test_strict_v3_dto_and_validation`: OK.
  - `services::strict_upgrade_tests::test_v2_legacy_receipts_request_parsing`: OK.
  - `models::strict_upgrade::tests::test_canonical_schema_fingerprints_match_frozen_constants`: OK.
  - `services::strong_sync_tests::test_strong_sync_live_mongo_acceptance_gate`: Ignored (live MongoDB connection required via `MONGO_TEST_URI`).

---

## Validation Boundaries & Scope Limitations
- **Unit & Fixture Scope**: Validates contract models, digest preimage calculations, CLI upgrade behavior, and Rust v3 unit/integration tests within the local offline environment.
- **Live Integration Not Established**: Live Hub deployment, physical multi-device sync, and live MongoDB integration were not exercised. `test_strong_sync_live_mongo_acceptance_gate` was ignored because it requires an external MongoDB instance at `MONGO_TEST_URI`.
- **Dependency Integrity**: Kotlin JVM tests were executed with `--dependency-verification=off` per assignment command, and additionally verified to succeed under `--dependency-verification=strict`.

## Coverage Metrics
- Formal line/branch coverage instrumentation (JaCoCo / tarpaulin) was not requested or configured for this phase.
- **Vector Parity**: Canonical schema fingerprints and golden digest preimages verified across Kotlin JVM, Node.js, and Rust runtimes.

## Failed Tests
- **None**: 0 failed test executions across all 5 verification targets.

## Performance Metrics
- `TabbyRush :shared:jvmTest`: 11.13s (rerun-tasks)
- `Node.js strict-v3-vector-runner.mjs`: 0.08s
- `Node.js strict-v2-vector-runner.mjs`: 0.09s
- `Node.js hub-strict-upgrade.test.mjs`: 11.29s (includes simulated backoff delay)
- `Glean Hub cargo test`: 30.01s (compilation + execution)
- **Total Verification Runtime**: ~53s
- **Slow Tests Identified**:
  - `hub-strict-upgrade.test.mjs` test "handles interrupted POST by recovering status using the same migration ID" took 10.25s due to simulated backoff delay.

## Build Status & Compiler Warnings
- **Build Status**: All builds and test runners succeeded.
- **Warnings**:
  - Gradle deprecation warnings for future Gradle 9.0 compatibility.
  - Kotlin compiler `expect`/`actual` Beta warning for `TabbyRushDatabaseConstructor`.
  - Rust compiler warning in Glean Hub: Unused imports `SCHEMA_FINGERPRINT_V1`, `SCHEMA_FINGERPRINT_V2`, and `SCHEMA_FINGERPRINT_V3` in `glean-hub-sync/src/services/strict_upgrade_service.rs:8:44`.

## Critical Issues
- **None**: Zero failures across all executed test suites.

## Recommendations
1. Clean up unused imports in `glean-hub-sync/src/services/strict_upgrade_service.rs` to keep Rust build warning-free.
2. Coordinate staging environment with live `MONGO_TEST_URI` for Phase 06 acceptance gate verification.

## Next Steps
1. Hand off Phase 01 test verification evidence to Main Agent.
2. Proceed to Phase 02: Room database schema migration and client synchronization integration.

---

## Unresolved Questions
- None.
