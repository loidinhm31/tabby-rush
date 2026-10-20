# Backend Verification — 2026-10-20

## Scope and environment
- Scope: sanitized mirror `G:/ws/standard-sync-verification-261020/glean-hub`; Rust core/plugin only. No UI/script/native checks, production config, real accounts or original service/data accessed.
- `CARGO_BUILD_JOBS=4`; integration invocation supplied `MONGO_TEST_URI=mongodb://127.0.0.1:27191/?replicaSet=doggo_standard_test`, `MONGO_TEST_ALLOW_DISPOSABLE=1`, `MONGO_TEST_ALLOWED_HOSTS=127.0.0.1:27191`.
- No tests reached execution; no test DBs/ACL artifacts created by test execution.

## Commands and results
1. `cargo run -p glean-hub-sync-client --bin export_types -- glean-hub-core/glean-hub-sync-client-types/src/index.ts` — **PASS**, exit 0 (32.88 s). Generated `G:/ws/standard-sync-verification-261020/glean-hub/glean-hub-core/glean-hub-sync-client-types/src/index.ts`. Readback shows `schemaVersion: number` at lines 173 and 220.
2. `cargo test -p glean-hub-sync-engine -p glean-hub-sync-client -p glean-hub-sync --all-targets --features glean-hub-sync/mongo-integration-tests -- --test-threads=1` — **BLOCKED**, exit 101 before test runner. Compiler error:
   - `glean-hub-plugin/glean-hub-sync/src/operator/mod.rs:253:29`, `.selection_criteria(ReadPreference::Primary)`: E0277, `Option<SelectionCriteria>: From<mongodb::options::ReadPreference>` is not implemented. Cargo/MongoDB crate version in diagnostic: `mongodb 3.5.0`; builder requires `Option<SelectionCriteria>`.
   - Compiler: `error: could not compile glean-hub-sync (lib) due to 1 previous error`. This is the only recorded compilation failure. Per instruction, did not repeat the compilation.
3. `cargo build -p glean-hub-server` — **NOT RUN**; depends on the same failing plugin compilation. No mirror server binary available for smoke.

## Test, coverage, build evidence
- Executed tests: **0 passed, 0 failed, 0 skipped**; test runner did not start. No test names/counts or live-test skip qualifications observed. Coverage not generated; no coverage metrics available.
- Required real-Mongo schemaVersion CAS/no-op/snapshot assertions, data-preserving cutover, and durable recovery cases: **NOT EXERCISED** due to compile blocker. No actual recovery result is claimed.
- Authored suites identified from source (not executed): `schema_version_integration.rs` has 5 tests (`baseline_cas_noops_exhaustion_and_corruption`, `sharing_map_equality_and_real_concurrent_writers`, `index_failure_does_not_publish_registration_or_schema`, `actual_routes_return_authoritative_dtos_and_standard_snapshot_metadata`, `strict_registration_and_publication_retry_increment_once`); `standard_sync_cutover_integration.rs` has 4 (`binary_dryrun_refusals_prepare_publish_repeat_and_standard_roundtrip`, `invalid_mixed_duplicate_ownership_and_snapshot_tamper_fail_without_mutation`, `recovery_at_every_durable_stage_and_operation_before_journal_acknowledgement`, `explicit_prepublication_rollback_is_restart_safe_and_nonoverwriting`).
- Compiler warning before failure: `glean-hub-core/glean-hub-sync-client/src/reqwest_http.rs:88`, unused variable `client` in lib test (`#[warn(unused_variables)]`). No other source warnings/errors observed.
- Test build invocation elapsed 62.64 s. Exporter elapsed 32.88 s. Server build unavailable; production build status unknown.

## Critical issue and next steps
1. Fix the MongoDB 3.5 selection-criteria type error at `glean-hub-plugin/glean-hub-sync/src/operator/mod.rs:253` in original source, then synchronize the sanitized mirror.
2. Rerun exporter/tests/build only after the fix. Preserve the explicit disposable URI/allowlist and same test DB isolation; capture test names and live recovery evidence.
3. Run actual browser/native smoke only in the parent-owned workflow after a mirror server binary exists.

Unresolved questions: none.