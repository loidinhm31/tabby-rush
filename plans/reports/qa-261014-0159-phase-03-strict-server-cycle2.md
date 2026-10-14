# QA Test Report: Phase 03 Strict Server & Legacy Isolation (Cycle 2)

**Date:** 2026-10-14
**Target:** Phase 03 — Doggo-only strict server and legacy isolation review cycle 2
**Context:** Linux x86_64 (`/home/loidinh/WS/doggo`, `/home/loidinh/WS/glean-hub`)

---

## 1. Test Results Overview

| Suite / Command | Status | Total | Passed | Failed | Ignored | Run Time | Wall Time |
|---|---|---:|---:|---:|---:|---:|---:|
| `cargo test --locked --manifest-path .../glean-hub-sync/Cargo.toml` | **PASS** | 36 | 35 | 0 | 1 | 30.00s | 30.55s |
| `node --check scripts/hub-schema-provision.mjs` | **PASS** | 1 | 1 | 0 | 0 | <0.05s | <0.05s |
| `node --check scripts/hub-compatibility-smoke.mjs` | **PASS** | 1 | 1 | 0 | 0 | <0.05s | <0.05s |
| *Auxiliary:* `test_strong_sync_live_mongo_acceptance_gate --ignored` | **FAILED** | 1 | 0 | 1 | 0 | 0.00s | 0.14s |
| *Auxiliary:* `cargo clippy --locked --manifest-path .../Cargo.toml` | **COMPLETED** | - | - | - | - | 1.67s | 1.67s |

---

## 2. Test Execution Details

### Command 1: Cargo Test Suite (`glean-hub-sync`)
- **Command:** `cargo test --locked --manifest-path /home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml`
- **Result:** 35 passed; 0 failed; 1 ignored; 0 measured.
- **Breakdown:**
  - `glean_hub_sync` (lib unit tests): 36 tests (35 passed, 1 ignored)
  - `strong_sync_admission` (bin unit tests): 0 tests (0 passed)
  - `glean_hub_sync` (doc-tests): 0 tests (0 passed)
- **Ignored Test:** `services::strong_sync_tests::test_strong_sync_live_mongo_acceptance_gate` (ignored by default; gate requires live MongoDB instance).
- **Passed Test Highlights:**
  - `models::strong_sync::tests::test_strict_digest_vectors` (PASS)
  - `routes::strong_sync_route_tests::test_register_strict_app_request_deny_unknown_fields` (PASS)
  - `routes::strong_sync_route_tests::test_strong_route_scope_guard_matching_allowed` (PASS)
  - `routes::strong_sync_route_tests::test_strong_route_scope_guard_mismatch_rejected` (PASS)
  - `routes::strong_sync_route_tests::test_strong_route_scope_guard_error_body_structure` (PASS)
  - `services::strong_sync_tests::test_canonical_uuid_hex_rigor` (PASS)
  - `services::strong_sync_tests::test_strict_app_id_validation` (PASS)
  - `services::strong_sync_tests::test_strict_collection_naming` (PASS)
  - `services::strong_sync_tests::test_strict_digest_vectors_matching_contract` (PASS)
  - `services::strong_sync_tests::test_strict_dto_deny_unknown_fields` (PASS)
  - `services::strong_sync_tests::test_strict_schema_validation_accepts_valid_todos` (PASS)
  - `services::strong_sync_tests::test_strict_schema_validation_rejects_extra_tables` (PASS)
  - `services::strong_sync_tests::test_strict_schema_validation_rejects_invalid_columns` (PASS)
  - `services::strong_sync_tests::test_strict_error_response_codes` (PASS)
  - `services::sync_service::tests::*` (all legacy isolation / sharing / permission tests passed)

### Command 2: Hub Schema Provisioning Syntax Check
- **Command:** `node --check /home/loidinh/WS/doggo/scripts/hub-schema-provision.mjs`
- **Result:** PASS (exit code 0; syntax valid; no runtime execution).

### Command 3: Hub Compatibility Smoke Syntax Check
- **Command:** `node --check /home/loidinh/WS/doggo/scripts/hub-compatibility-smoke.mjs`
- **Result:** PASS (exit code 0; syntax valid; no runtime execution).

---

## 3. Auxiliary Test Verifications

### Auxiliary Gate: Live MongoDB Acceptance Gate (`--ignored`)
- **Command:** `cargo test --locked --manifest-path .../Cargo.toml -- test_strong_sync_live_mongo_acceptance_gate --ignored`
- **Result:** FAILED (exit code 101, thread panicked).
- **Error Output:**
  ```
  thread 'services::strong_sync_tests::test_strong_sync_live_mongo_acceptance_gate' panicked at glean-hub-plugin/glean-hub-sync/src/services/strong_sync_tests.rs:251:13:
  MONGO_TEST_URI environment variable is required for live MongoDB acceptance gates; cannot skip
  ```
- **Finding:** Hard-fails when `MONGO_TEST_URI` is missing. Proves failure on missing prerequisite, NOT live MongoDB acceptance.

### Auxiliary Linter: Cargo Clippy
- **Command:** `cargo clippy --locked --manifest-path .../Cargo.toml`
- **Result:** Completed with 7 warnings (3 in lib, 4 in bin):
  - `src/services/strong_sync_service.rs:824`: manual `!RangeInclusive::contains` implementation
  - `src/services/strong_sync_service.rs:874`: manual `.is_multiple_of()`
  - `src/models/strong_sync.rs:176`: `compute_digest` has 10 arguments (exceeds clippy threshold 7)
  - `src/bin/strong_sync_admission.rs:23, 29, 35, 41`: collapsible matches/ifs

---

## 4. Static Code Observations & Remaining Risks

1. **Replay Confirmation Error Handling:**
   - In `strong_sync_service.rs:637`, `let _ = coll.update_one(replay_filter, replay_update).await;` discards the result of the replay confirmation nonce update. Returns `Replayed` even if update fails or matches 0 documents.
2. **Concern Policy Asymmetry:**
   - Push configures `w: 1, journal: true` with local read concern; pull queries use inherited collection defaults without explicit read concern options. Topology-aware durability (standalone vs replica set majority) not dynamically negotiated.
3. **Key Custody in Provisioning Script:**
   - `hub-schema-provision.mjs` logs API key to stdout and writes unconstrained permissions file (`writeFileSync`). If read-back fails after registration, newly generated API key is lost without recovery mechanism.
4. **Smoke Script Assertion Strictness:**
   - `hub-compatibility-smoke.mjs` accepts HTTP 401 as passing for legacy rejection (Gate 2) and scope coherence (Gate 3). Does not prove downstream route guards executed when origin dual auth middleware rejects requests upfront.
5. **Admission Index Specification Matching:**
   - `app_service.rs:289-293` uses `filter_map` on `as_i32()`, which could silently discard non-integer index keys. Does not explicitly reject hidden scan indexes or extra unique index options.

---

## 5. Summary & Verdict

- **Requested Commands Status:** ALL 3 REQUESTED COMMANDS PASSED.
  - Crate test suite: 35 passed, 0 failed, 1 ignored.
  - Both Node.js utility scripts passed syntax checks.
- **Runtime Environment Status:** Live MongoDB acceptance and live HTTP isolation remain unverified at runtime due to lack of `MONGO_TEST_URI` and active Glean Hub test origin.

---

## 6. Recommendations

1. Provide an isolated MongoDB test instance via `MONGO_TEST_URI` in CI/CD pipeline to execute `test_strong_sync_live_mongo_acceptance_gate`.
2. Inspect `update_one` result on `classify_reread` replay confirmation path; fail or retry if `matched_count == 0` or write concern errors occur.
3. Explicitly configure read concern on `pull` collection handle (`ReadConcern::local()` / `ReadConcern::majority()`).
4. Apply secure file mode (0600) for written credentials file in `hub-schema-provision.mjs`.
5. Clean up clippy warnings in `strong_sync_admission.rs` and `strong_sync_service.rs`.

---

## 7. Unresolved Questions

1. When will an isolated MongoDB instance (with replica set / journaling) be provisioned to run live integration tests?
2. Should `classify_reread` return `RetryableFailure` rather than `Replayed` if the confirmation update write fails?
3. Should `pull` explicitly enforce `ReadConcern::local()` matching `push`?
