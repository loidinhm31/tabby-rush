# QA Test Validation Report: Phase 01 — Freeze Temporal Model and Strict Transition

- Date: 2026-10-15
- Phase: Phase 01 — Freeze temporal model and strict transition
- Target: TabbyRush (`/home/loidinh/WS/tabbyrush`) & Glean Hub (`/home/loidinh/WS/glean-hub`)

---

## Test Results Overview

| Test Suite / Target | Command | Tests Run | Passed | Failed | Skipped / Ignored | Status |
|---|---|---|---|---|---|---|
| Kotlin Multiplatform JVM | `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | 108 | 108 | 0 | 0 | PASS |
| Node.js strict-v2 Vector Runner | `node scripts/strict-v2-vector-runner.mjs` | 20 (9 valid + 11 rejects) | 20 | 0 | 0 | PASS |
| Node.js Hub Live Smoke (Offline Fixture) | `node scripts/hub-live-smoke.mjs --fixture-test` | 18 checks (5 suites) | 18 | 0 | 0 | PASS |
| Rust `strong_sync_v2_tests` (Glean Hub) | `cargo test --package glean-hub-sync --lib models::strong_sync_v2_tests` | 1 test (20 fixture cases) | 1 | 0 | 0 | PASS |
| Rust `glean-hub-sync` full package | `cargo test --package glean-hub-sync` | 38 | 37 | 0 | 1 (ignored*) | PASS |

*\*Note: 1 Rust test (`test_strong_sync_live_mongo_acceptance_gate`) is explicitly marked `#[ignore]` because it requires a live MongoDB instance at `MONGO_TEST_URI`.*

---

## Executed Commands & Verifications

### 1. Kotlin JVM Test Suite
- Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`
- Scope: 16 test classes, 108 unit/integration tests across shared KMP layer.
- Outcome: 108 passed, 0 failed, 0 skipped. Execution time: 0.588s in test runner (wall time: 6.46s).
- Targeted Phase 01 suites:
  - `dev.tabbyrush.shared.contract.StrictV2DigestVectorTest`: 5/5 passed. Validates migrated null/default vector, date-only deadline, timed DST overlap, repeating count 10, and disabled rule with deadline.
  - `dev.tabbyrush.shared.contract.StrictV2DigestExtendedTest`: 5/5 passed. Validates cleared deadline, Unicode title & zone, zero-data-bytes delete, negative epoch instants, and boundary/overflow rejections:
    - `reminderCount` in 1..10 bounds.
    - Repeating interval >= 15 min when count > 1; null interval when count = 1.
    - Non-negative `createdAt` and `reminderLeadMinutes`.
    - Checked 64-bit integer arithmetic rejecting overflow wrap at intermediate operations: lead multiplication (`reminderLeadMinutes * 60_000L`), anchor subtraction (`anchor - leadMs`), interval multiplication (`reminderIntervalMinutes * 60_000L`), repeat offset multiplication (`(count - 1L) * intervalMs`), and final addition (`first + lastOffset`).
    - Timed deadline `deadlineOverdueEpochMs == deadlineAnchorEpochMs` invariant.
    - Disabled rule timing validity (`last <= anchor`) with deadline.
    - Deadline removal / no deadline null policies and default preference enforcement.

### 2. Node.js Strict-v2 Vector Verification
- Command: `node scripts/strict-v2-vector-runner.mjs`
- Scope: Independent JS preimage encoder, checked 64-bit bounds validation, and SHA-256 digest calculation against `integration/glean-hub/strict-v2-vectors.json`.
- Outcome: 9/9 valid vectors passed; 11/11 rejection vectors verified. Cross-repository fixture SHA-256 comparison between TabbyRush and Glean Hub passed identically. Execution time: ~0.08s.

### 3. Node.js Hub Live Smoke Offline Fixture Test
- Command: `node scripts/hub-live-smoke.mjs --fixture-test`
- Scope: Standalone HTTP fixture server testing strict-v1 protocol compatibility, negative route isolation, auth flows, push/pull CAS semantics, and tombstone invariants.
- Classification: Offline strict-v1 protocol fixture self-test. Does not serve as live Hub or strict-v2 backend integration evidence.
- Outcome: 18/18 checks passed across 5 test suites. Execution time: ~0.25s.

### 4. Rust strong_sync_v2 Vector Test (Glean Hub)
- Command: `cargo test --package glean-hub-sync --lib models::strong_sync_v2_tests`
- Working Directory: `/home/loidinh/WS/glean-hub`
- Scope: `models::strong_sync_v2_tests::tests::test_strict_v2_vectors_against_fixture` testing Rust preimage construction and SHA-256 digest against `strict-v2-vectors.json` (using portable fixture resolution via `STRICT_V2_VECTORS_PATH`, local fixture in `tests/fixtures/strict-v2-vectors.json`, or relative repo paths).
- Outcome: 1 passed, 0 failed (exercising all 20 fixture cases: 9 valid + 11 rejections). Execution time: <0.01s.

### 5. Rust glean-hub-sync Package Test Suite
- Command: `cargo test --package glean-hub-sync`
- Working Directory: `/home/loidinh/WS/glean-hub`
- Outcome: 37 passed, 0 failed, 1 ignored.

---

## Coverage & Validation Boundaries

### Tested Scope
- Strict-v2 wire format framing and big-endian binary preimage serialization in Kotlin, Node.js, and Rust.
- Checked 64-bit integer arithmetic in Kotlin, Rust, and Node.js for `leadMs`, `firstAnchor`, `intervalMs`, `lastOffset`, and `lastAnchor`, rejecting integer overflow wrap at all steps.
- Invariant enforcement:
  - `reminderCount` constrained to 1..10.
  - Repeating interval requires >= 15 minutes when count > 1.
  - Non-repeating count = 1 requires null interval.
  - Nonnegative `createdAt` and `reminderLeadMinutes`.
  - Timed deadline requires `deadlineOverdueEpochMs == deadlineAnchorEpochMs`.
  - Date-only deadline requires non-null `reminderAnchorTime`.
  - Deadline removal / no deadline requires all deadline fields null and default reminder preferences (`reminderLeadMinutes == 0`, `reminderCount == 1`, `reminderIntervalMinutes == null`).
  - Disabled reminder rules with deadline retain validated timing and resolved anchors (`last <= anchor`).

### Excluded / Deferred Validation Gaps (Future Phase Gates)
- Syntactic calendar validation of date/time strings (e.g. leap year, valid month/day ranges, minute boundaries) is deferred to Phase 02 and Phase 05.
- Full IANA timezone identifier validation and strict DST gap/overlap resolvers are scheduled for Phase 02 (native alarm/DST proof) and Phase 05 (Room migration).
- Canonical UUID format validation for `scheduleToken` is deferred to Phase 05.
- Automated line/branch coverage measurement (kover/jacoco) is not configured in `shared/build.gradle.kts`.
- Cross-instance distributed permit drain and MongoDB lock fencing are scheduled for Phase 03/04.
- Outbox replay and receipt reconciliation under network partition/server restart are scheduled for Phase 06.

---

## Failed Tests
- None. 0 failures across all executed test suites.

---

## Performance Metrics
- Kotlin JVM tests: 108 tests in 0.588s execution time.
- Node.js vector verification: ~0.08s.
- Node.js fixture test: ~0.25s.
- Rust strong_sync_v2 test: <0.01s.
- No slow tests identified (all individual test cases executed under 180ms).

---

## Build Status
- Shared Kotlin module (`:shared`): Build successful with JDK 21 LTS (`15 actionable tasks executed`).
- Glean Hub Sync crate (`glean-hub-sync`): Build and test compilation successful with Cargo.
- Android Diagnostic Security Audit (`node scripts/release-security-check.mjs --diagnose-source`): Clean pass (manifest cleartext traffic prohibited, backup disabled, only MainActivity exported).

---

## Recommendations
1. Maintain synchronized copies of `strict-v2-vectors.json` in both repositories and enforce the automated SHA-256 equality check in CI.
2. In Phase 02, verify strict offset resolution against native platform timezone databases (handling duplicate local times during fall-back transitions).
3. In Phase 03, execute integration tests against a live MongoDB replica set for atomic migration permit acquisition and barrier draining.

---

## Next Steps
1. Hand off validation evidence to Main agent for Phase 01 completion.
2. Proceed to Phase 02 (native alarm delivery and DST proof) and Phase 03 (Hub versioned contract and migration implementation).

---

## Unresolved Questions
- None. All specified test suites executed and passed 100%. Cross-stack agreement on strict-v2 preimages, digests, and checked arithmetic boundary rejections verified.
