# Phase 03 QA Test Execution & Validation Report: Hub Versioned Contract & Migration

**Date**: 2026-10-15  
**Execution Context**: Linux Fedora 44 x86_64 | JDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Rust 1.95.0 | Node.js v24.16.0  
**Phase Target**: Phase 03 — Hub versioned contract, admission guard, and data-preserving migration  
**Working Directories**:
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync`
- `/home/loidinh/WS/tabbyrush`

---

## 1. Test Summary Table

| Command | Working Directory | Target / Suite Description | Total Tests / Checks | Passed | Failed | Ignored | Pass Rate | Execution Duration | Exit Status |
|---|---|---|---|---|---|---|---|---|---|
| `cargo test --locked` | `.../glean-hub-sync` | Hub Sync Rust unit, integration & doc tests | 55 | 54 | 0 | 1 | 100% (executed) | 32.34s wall (30.00s test runner) | 0 |
| `cargo clippy --locked --all-targets` | `.../glean-hub-sync` | Rust compiler linter & static analysis | N/A | N/A (0 warnings) | 0 | 0 | N/A (clean) | 0.19s | 0 |
| `node scripts/hub-live-smoke.mjs --fixture-test` | `/home/loidinh/WS/tabbyrush` | Offline strict-v1 protocol & route isolation smoke | 18 | 18 | 0 | 0 | 100% | Recorded exit 0 | 0 |
| `node scripts/strict-v2-vector-runner.mjs` | `/home/loidinh/WS/tabbyrush` | Preimage, SHA-256 digest & schema fingerprint checks | 23 | 23 | 0 | 0 | 100% | Recorded exit 0 | 0 |
| `JAVA_HOME=... ./gradlew :shared:jvmTest --rerun-tasks --dependency-verification=off` | `/home/loidinh/WS/tabbyrush` | Shared Kotlin Multiplatform JVM unit test suites | 116 | 116 | 0 | 0 | 100% | 7.76s wall (initial run: 905ms up-to-date) | 0 |
| `node scripts/release-security-check.mjs --diagnose-source` | `/home/loidinh/WS/tabbyrush` | Android static manifest, backup & secret audit checks | 6 | 6 | 0 | 0 | 100% | Recorded exit 0 | 0 |
| **Combined Automated Tests / Checks** | **Both Repos** | **All executed unit, vector, smoke, and audit checks** | **212** | **211** | **0** | **1** | **100%** | **All suites completed** | **All 0** |

*Note: 1 ignored test in `glean-hub-sync` (`services::strong_sync_tests::test_strong_sync_live_mongo_acceptance_gate`) explicitly requires live MongoDB instance via `MONGO_TEST_URI`.*

---

## 2. Detailed Test Breakdown per Suite

### 2.1 Glean Hub Sync Backend (`cargo test --locked`) (54/54 PASS, 1 IGNORED)
- `models::strict_upgrade::tests`: 6/6 PASS
  - `test_migration_state_roundtrip`: PASS
  - `test_validate_strict_v1_schema_success`: PASS
  - `test_validate_strict_v2_schema_success`: PASS
  - `test_validate_schema_wrong_mode_rejected`: PASS
  - `test_validate_schema_duplicate_column_rejected`: PASS
  - `test_canonical_schema_fingerprints_match_frozen_constants`: PASS
- `models::strong_sync::tests`: 1/1 PASS
  - `test_strict_digest_vectors`: PASS
- `models::strong_sync_v2_tests::tests`: 1/1 PASS
  - `test_strict_v2_vectors_against_fixture`: PASS
- `routes::strong_sync_route_tests`: 6/6 PASS
  - `test_register_strict_app_request_deny_unknown_fields`: PASS
  - `test_strict_sync_error_all_codes_mapping`: PASS
  - `test_strong_route_scope_guard_matching_allowed`: PASS
  - `test_strong_route_scope_guard_header_absent_allowed`: PASS
  - `test_strong_route_scope_guard_error_body_structure`: PASS
  - `test_strong_route_scope_guard_mismatch_rejected`: PASS
- `services::schema_validator::tests`: 6/6 PASS
  - `rejects_learning_row_without_generation`: PASS
  - `accepts_integer_generation_and_preserves_content_contract`: PASS
  - `rejects_null_or_malformed_generation`: PASS
  - `accepts_valid_credit_card_notification_event`: PASS
  - `validates_dashboard_preferences_semantics_and_singleton_id`: PASS
  - `rejects_semantically_invalid_recurring_notification_events`: PASS
- `services::strict_upgrade_tests`: 11/11 PASS
  - `test_extract_app_id_from_various_routes`: PASS
  - `test_legacy_receipts_request_parsing`: PASS
  - `test_coordination_doc_structure`: PASS
  - `test_app_scoped_admin_middleware_grants_authorized_admin`: PASS
  - `test_app_scoped_admin_middleware_rejects_non_admin_with_401`: PASS
  - `test_app_scoped_admin_middleware_rejects_admin_without_app_grant_with_403`: PASS
  - `test_upgrade_request_parsing_and_defaults`: PASS
  - `test_v2_schema_requires_all_16_columns_in_order`: PASS
  - `test_v2_schema_rejects_duplicate_column_definition`: PASS
  - `test_strict_sync_error_status_codes`: PASS
  - `test_fingerprints_and_canonical_json_parity`: PASS
- `services::strong_sync_tests`: 9/9 PASS, 1 IGNORED
  - `test_canonical_uuid_hex_rigor`: PASS
  - `test_strict_app_id_validation`: PASS
  - `test_strict_collection_naming`: PASS
  - `test_strict_digest_vectors_matching_contract`: PASS
  - `test_strict_dto_deny_unknown_fields`: PASS
  - `test_strict_schema_validation_accepts_valid_todos`: PASS
  - `test_strong_sync_live_mongo_acceptance_gate`: IGNORED (requires live MongoDB at `MONGO_TEST_URI`)
  - `test_strict_schema_validation_rejects_extra_tables`: PASS
  - `test_strict_error_response_codes`: PASS
  - `test_strict_schema_validation_rejects_invalid_columns`: PASS
- `services::sync_service::tests`: 8/8 PASS
  - `deleted_notification_tombstones_preserve_server_owned_fields`: PASS
  - `legacy_notification_rows_get_required_server_defaults`: PASS
  - `new_notification_sync_rows_start_with_safe_server_state`: PASS
  - `notification_sync_preserves_server_owned_fields`: PASS
  - `test_editor_permission_in_db_treated_as_viewer`: PASS
  - `test_generic_sharing_config_no_hardcoding`: PASS
  - `test_owner_push_own_collection_allowed`: PASS
  - `test_viewer_push_to_shared_collection_denied`: PASS
- `services::sync_generation_tests`: 3/3 PASS
  - `delta_reuses_generation_validation_for_new_ids`: PASS
  - `push_rejects_old_learning_rows_without_creating_new_ids`: PASS
  - `valid_generation_rows_sync_at_high_client_versions_and_content_is_unchanged`: PASS
- `tests::`: 3/3 PASS
  - `test_config_defaults`: PASS
  - `test_plugin_info`: PASS
  - `app_management_routes_exist_without_sync_data_routes`: PASS
- Bin tests (`src/bin/strong_sync_admission.rs`): 0 tests defined
- Doc tests (`glean_hub_sync`): 0 tests defined

### 2.2 TabbyRush Strict-v2 Vector Runner (`strict-v2-vector-runner.mjs`) (23/23 PASS)
- Canonical Schema Fingerprint Checks: 2/2 PASS
  - v1 SHA-256 fingerprint verified: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
  - v2 SHA-256 fingerprint verified: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
- Cross-repository Fixture Parity: PASS (Both TabbyRush `integration/glean-hub/strict-v2-vectors.json` and Hub `.../glean-hub-sync/tests/fixtures/strict-v2-vectors.json` present on disk with matching SHA-256 digests)
- Valid Vectors: 10/10 PASS
  - Exact byte length, preimage hex, and SHA-256 digest verified for 10 operations:
    1. `v2-create-date-only-minimal`
    2. `v2-create-timed-dst-overlap`
    3. `v2-create-date-only-reminder-repeat10`
    4. `v2-update-clear-deadline`
    5. `v2-delete-zero-data-bytes`
    6. `v2-migrated-null-defaults`
    7. `v2-disabled-rule-with-deadline`
    8. `v2-unicode-title-and-zone`
    9. `v2-max-i64-expected-revision`
    10. `v2-negative-epoch-instant`
- Rejection Vectors: 11/11 PASS
  - Validates rejection for out-of-range expected revision, delete containing data payload, negative createdAt, negative lead minutes, count out of range, interval not null when count=1, interval < 15 for repeating reminders, deadline parameters present without deadlineDate, missing required fields with deadlineDate, invalid reminder anchor on timed deadlines, last reminder after anchor

### 2.3 TabbyRush Hub Live Smoke Fixture Test (`hub-live-smoke.mjs --fixture-test`) (18/18 PASS)
- Suite 1 (Health & App Admission): 2/2 PASS
  - GET `/api/v1/health` healthy (HTTP 200)
  - GET `/api/v1/apps/tabbyrush` echoes `syncMode=strict-v1` and authoritative epoch
- Suite 2 (Security & Negative Route Isolation): 3/3 PASS
  - Strict sync bootstrap rejects missing API key (HTTP 403)
  - Strict sync bootstrap rejects invalid API key (HTTP 403)
  - Legacy sync route rejects strict-v1 app with HTTP 426 (`protocolUnsupported`)
- Suite 3 (Authentication & Access Session): 4/4 PASS
  - Register new user account (POST `/auth/register` HTTP 200)
  - Login user credentials (POST `/auth/login` token obtained, apps=["tabby-rush"])
  - Token refresh exchange (POST `/auth/refresh` successful)
  - Verify identity & grants (GET `/auth/me` verified user)
- Suite 4 (Strict Strong Sync Operations): 7/7 PASS
  - Bootstrap establishes authoritative protocol and epoch
  - Strict sync rejects invalid JWT token (HTTP 401)
  - Epoch mismatch returns HTTP 409 (`epochMismatch`)
  - Push insert-only create (expectedRevision: 0) accepted at revision 1
  - Idempotent frozen retry with identical opId + digest returns `replayed` (confirmed acceptedRevision=1)
  - Push revision conflict returns `revisionConflict` (currentRevision=1)
  - Pull retrieves specific created record at revision 1
- Suite 5 (Tombstone Deletion & Invariants): 2/2 PASS
  - Revision-CAS delete produces permanent empty tombstone at revision 2
  - Pull confirms tombstone preservation (`deleted: true`, `data: {}`)

### 2.4 TabbyRush Shared JVM Multiplatform Suite (`:shared:jvmTest`) (116/116 PASS)
*Run with `--rerun-tasks` to ensure active execution. Total 116 tests executed across 17 suites.*
- `HubAuthClientTest`: 9/9 PASS
- `HubAuthSessionTest`: 8/8 PASS
- `SingleFlightRefreshTest`: 3/3 PASS
- `AccountKeyTest`: 5/5 PASS
- `EndpointPolicyTest`: 8/8 PASS
- `StrictDigestTest`: 5/5 PASS
- `StrictTimeZoneResolverTest`: 7/7 PASS (includes DST overlap, midnight gap, 23h spring forward, 25h fall back, zone validation)
- `StrictV2DigestExtendedTest`: 6/6 PASS
- `StrictV2DigestVectorTest`: 5/5 PASS
- `TitlePolicyTest`: 6/6 PASS
- `RoomAccountStoreTest`: 9/9 PASS
- `AccountLeaveProtocolTest`: 7/7 PASS
- `AccountSyncCoordinatorTest`: 5/5 PASS
- `HubSyncClientTest`: 7/7 PASS
- `OutboxReconciliationTest`: 10/10 PASS
- `StagedFullScanTest`: 9/9 PASS
- `TodoSemanticsTest`: 7/7 PASS

---

## 3. Security, Invariant, and Clippy Audit Outcomes

### 3.1 Rust Clippy Audit (`cargo clippy --locked --all-targets`)
- Execution directory: `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync`
- Target coverage: lib, bins, tests, benches across crate
- Result: **CLEAN (0 warnings, 0 errors, exit status 0)**
- Verified without modifying command flags (did not inject `-D warnings` or suppress lints).

### 3.2 Android Release Security Source Diagnostic (`release-security-check.mjs --diagnose-source`)
- Execution directory: `/home/loidinh/WS/tabbyrush`
- Target: Source repository manifest, security configs, and origin rules
- Result: **CLEAN (6/6 checks passed)**
  1. Base manifest prohibits cleartext traffic (`usesCleartextTraffic="false"`): PASS
  2. Base manifest restricts backup (`allowBackup="false"`): PASS
  3. Exported components audit: Only `MainActivity` exported: PASS
  4. XML security configs present (`backup_rules.xml`, `data_extraction_rules.xml`): PASS
  5. Release Hub origin enforces HTTPS (`https://api-glean-hub.qm-hub-v001.cloud`): PASS
  6. Source secret scan: No hardcoded private keys or passwords in source: PASS
- *Audit Scope Notice*: Proves source repository meets static configuration rules; does not certify compiled release APK binary signatures or runtime keystore state.

### 3.3 Strict Invariant & Schema Fingerprint Audit
- Strict v1 Canonical Fingerprint: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec` (verified in Rust unit tests and Node vector runner)
- Strict v2 Canonical Fingerprint: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4` (verified in Rust unit tests and Node vector runner)
- Strict v2 16-column schema ordering & types: Verified in `test_v2_schema_requires_all_16_columns_in_order`.
- Cross-repository vector consistency: TabbyRush fixture matches Hub test fixture bit-for-bit (verified on disk via SHA-256 fixture digest check).

---

## 4. Critical Scope Distinction & Gate Limitations

### 4.1 Automated Logic Suite vs. Gate G3 Multi-Instance Admission
- The executed test suites passed all exercised unit and fixture test cases.
- **Gate G3 Production Admission Status: UNVERIFIED / PENDING LIVE TOPOLOGY TESTING**.
  - Phase 03 plan specification explicitly demands live multi-instance MongoDB verification:
    1. Distributed fencing across two concurrent Hub instances.
    2. Concurrent read/write request drain under active migration.
    3. Crash injection during in-place migration transform batches.
    4. Publish-response packet loss with restart replay.
    5. Preservation of physical `app_<appId>` collection and full protected-field fingerprints under real DB storage.
  - The single ignored Rust test (`test_strong_sync_live_mongo_acceptance_gate`) documents this boundary: it requires live MongoDB at `MONGO_TEST_URI`.
  - Offline fixture tests and Node vector suites validate local protocol semantics, not physical cluster high-availability guarantees.

### 4.2 Toolchain & Execution Flags
- Initial `./gradlew :shared:jvmTest` invocation reported all 15 tasks up-to-date; execution with `--rerun-tasks` ensured all 116 tests actively executed and validated.
- Gradle execution used `--dependency-verification=off` per assignment command specification. Production builds require re-enabling verification once gradle dependency checksum metadata is updated.

---

## 5. Failed / Flaky Tests & Execution Anomalies
- Total failed tests / checks: **0**
- Total test errors: **0**
- Flaky tests: No failures observed in these runs; flakiness was not assessed (single execution per suite).
- Total ignored tests: **1** (Rust live Mongo acceptance gate)

---

## 6. Recommendations
1. Deploy disposable standalone MongoDB instance and execute `test_strong_sync_live_mongo_acceptance_gate` with `MONGO_TEST_URI` set.
2. Formulate multi-node live deployment smoke script to validate dual-Hub distributed fence coordination before promoting Phase 03 to G3 admission gate sign-off.
3. Update Gradle dependency-verification metadata so that `--dependency-verification=off` can be safely removed.

---

## 7. Next Steps
1. Report completed test pass metrics to Main agent.
2. Retain Gate G3 unverified status pending live multi-instance cluster migration test run.
3. Prepare live disposable MongoDB and multi-instance test harness required to satisfy Gate G3 prerequisites.

---

## 8. Terminal Result Statement
All six requested commands completed successfully. The runs reported 54 passing Rust tests with one ignored live-Mongo test, 116 passing JVM tests, 18 passing offline smoke checks, 21 passing vector cases plus two schema-fingerprint checks, and six passing static security checks. Clippy emitted no warnings across all targets. Gradle dependency verification was disabled as specified. Gate G3 remains unverified pending live two-Hub/MongoDB migration, race, crash-recovery, and protected-field-preservation evidence.

---

## 9. Unresolved Questions
1. Does the staging environment provision a secondary Glean Hub instance to automate two-node admission fence race tests against a shared MongoDB replica set in CI?
2. Will the Phase 04 upgrade runner script execute directly from operator bastions with direct MongoDB access or route exclusively through the Hub admin REST endpoint?
3. When will Gradle dependency checksum metadata be updated to eliminate the `--dependency-verification=off` flag requirement during CI builds?
