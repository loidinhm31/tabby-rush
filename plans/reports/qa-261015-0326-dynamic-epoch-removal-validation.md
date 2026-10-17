# QA Test Report: Dynamic Epoch Collection Removal Validation

**Report Path:** `plans/reports/qa-261015-0326-dynamic-epoch-removal-validation.md`  
**Date:** 2026-10-15  
**Subject:** Validation of dynamic epoch collection removal across `glean-hub` and `tabbyrush` repositories  
**Status:** Requested validation commands completed successfully; one Rust acceptance test ignored

---

## Executive Summary

Executed all 7 target validation commands across `glean-hub` and `tabbyrush` repositories following transition from dynamic epoch collection naming (`_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>`) to direct target collection naming (`app_<appId>`).

100% of executed unit and integration test assertions passed. Zero test failures, zero compilation errors, zero Clippy linter warnings, and zero security check failures. One Rust live MongoDB integration test was intentionally ignored per test gate design requiring live `MONGO_TEST_URI`.

---

## 1. Test Results Overview

| Repository | Scope / Command | Target / Suite Type | Discovered / Total | Passed | Failed | Skipped / Ignored | Pass Rate (Executed) |
|---|---|---|---|---|---|---|---|
| **glean-hub** | `cargo test -p glean-hub-sync` | `src/lib.rs` unit tests | 37 | 36 | 0 | 1 (`test_strong_sync_live_mongo_acceptance_gate`) | 100% |
| **glean-hub** | `cargo test -p glean-hub-sync` | `src/bin/strong_sync_admission.rs` | 0 | 0 | 0 | 0 | N/A |
| **glean-hub** | `cargo test -p glean-hub-sync` | Doc-tests `glean_hub_sync` | 0 | 0 | 0 | 0 | N/A |
| **glean-hub** | `cargo clippy -p glean-hub-sync -p glean-hub-server --all-targets -- -D warnings` | Linter / static analysis | N/A | Clean (0 err/warn) | 0 | 0 | 100% |
| **glean-hub** | `cargo check --workspace` | Workspace compiler check | N/A | Clean (0 errors) | 0 | 0 | 100% |
| **tabbyrush** | `node scripts/hub-live-smoke.mjs --fixture-test` | Strict-v1 live smoke fixture checks | 18 checks | 18 | 0 | 0 | 100% |
| **tabbyrush** | `CONFIRM_MAINTENANCE_QUIESCENCE=true DRY_RUN=true node scripts/hub-strict-recovery.js` | Recovery script syntax & config | 1 check | 1 | 0 | 0 | 100% |
| **tabbyrush** | `node scripts/release-security-check.mjs --diagnose-source` | Static security audit checks | 6 checks | 6 | 0 | 0 | 100% |
| **tabbyrush** | `JAVA_HOME=... ./gradlew :shared:jvmTest --rerun-tasks` | Shared KMP JVM unit/integration tests | 98 | 98 | 0 | 0 | 100% |

### Total Aggregated Metrics
- **Compiled Unit/Integration Tests (Rust + JVM):** 135 discovered, 134 passed, 0 failed, 1 ignored.
- **Protocol / Fixture / Security Checks:** 25 checks discovered, 25 passed, 0 failed, 0 skipped.
- **Total Executed Verifications:** 159, 159 passed (100% pass rate among executed).

---

## 2. Command Execution Details & Outputs

### 2.1 Glean Hub: `cargo test -p glean-hub-sync`
- **Working Directory:** `/home/loidinh/WS/glean-hub`
- **Execution Time:** 30.00s wall time
- **Outcome:** Passed. 36 passed, 0 failed, 1 ignored.
- **Ignored Test:** `services::strong_sync_tests::test_strong_sync_live_mongo_acceptance_gate` (requires live MongoDB at `MONGO_TEST_URI`).
- **Key Test Verified:** `services::strong_sync_tests::test_strict_collection_naming ... ok` validates direct collection mapping `app_<appId>`.
- **Output:**
```
running 37 tests
test models::strong_sync::tests::test_strict_digest_vectors ... ok
test routes::strong_sync_route_tests::test_register_strict_app_request_deny_unknown_fields ... ok
test services::schema_validator::tests::rejects_learning_row_without_generation ... ok
test routes::strong_sync_route_tests::test_strict_sync_error_all_codes_mapping ... ok
test services::strong_sync_tests::test_canonical_uuid_hex_rigor ... ok
test services::schema_validator::tests::rejects_null_or_malformed_generation ... ok
test services::schema_validator::tests::validates_dashboard_preferences_semantics_and_singleton_id ... ok
test services::schema_validator::tests::accepts_integer_generation_and_preserves_content_contract ... ok
test services::schema_validator::tests::accepts_valid_credit_card_notification_event ... ok
test services::strong_sync_tests::test_strong_sync_live_mongo_acceptance_gate ... ignored, Requires live MongoDB at MONGO_TEST_URI; hard-fails if unset when executed
test routes::strong_sync_route_tests::test_strong_route_scope_guard_header_absent_allowed ... ok
test services::strong_sync_tests::test_strict_schema_validation_rejects_extra_tables ... ok
test routes::strong_sync_route_tests::test_strong_route_scope_guard_matching_allowed ... ok
test services::strong_sync_tests::test_strict_app_id_validation ... ok
test routes::strong_sync_route_tests::test_strong_route_scope_guard_mismatch_rejected ... ok
test services::strong_sync_tests::test_strict_error_response_codes ... ok
test routes::strong_sync_route_tests::test_strong_route_scope_guard_error_body_structure ... ok
test services::schema_validator::tests::rejects_semantically_invalid_recurring_notification_events ... ok
test services::strong_sync_tests::test_strict_collection_naming ... ok
test services::strong_sync_tests::test_strict_dto_deny_unknown_fields ... ok
test services::strong_sync_tests::test_strict_schema_validation_accepts_valid_todos ... ok
test services::sync_service::tests::legacy_notification_rows_get_required_server_defaults ... ok
test services::strong_sync_tests::test_strict_schema_validation_rejects_invalid_columns ... ok
test services::sync_service::tests::deleted_notification_tombstones_preserve_server_owned_fields ... ok
test services::sync_service::tests::new_notification_sync_rows_start_with_safe_server_state ... ok
test services::strong_sync_tests::test_strict_digest_vectors_matching_contract ... ok
test services::sync_service::tests::notification_sync_preserves_server_owned_fields ... ok
test tests::test_config_defaults ... ok
test tests::test_plugin_info ... ok
test tests::app_management_routes_exist_without_sync_data_routes ... ok
test services::sync_generation_tests::delta_reuses_generation_validation_for_new_ids ... ok
test services::sync_service::tests::test_viewer_push_to_shared_collection_denied ... ok
test services::sync_service::tests::test_generic_sharing_config_no_hardcoding ... ok
test services::sync_service::tests::test_editor_permission_in_db_treated_as_viewer ... ok
test services::sync_generation_tests::push_rejects_old_learning_rows_without_creating_new_ids ... ok
test services::sync_service::tests::test_owner_push_own_collection_allowed ... ok
test services::sync_generation_tests::valid_generation_rows_sync_at_high_client_versions_and_content_is_unchanged ... ok

test result: ok. 36 passed; 0 failed; 1 ignored; 0 measured; 0 filtered out; finished in 30.00s
```

### 2.2 Glean Hub: `cargo clippy -p glean-hub-sync -p glean-hub-server --all-targets -- -D warnings`
- **Working Directory:** `/home/loidinh/WS/glean-hub`
- **Execution Time:** 0.16s
- **Outcome:** Clean. Zero warnings, zero errors across all targets.
- **Output:**
```
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 0.16s
```

### 2.3 Glean Hub: `cargo check --workspace`
- **Working Directory:** `/home/loidinh/WS/glean-hub`
- **Execution Time:** 0.16s
- **Outcome:** Clean. Workspace compiled with zero errors across all crates.
- **Output:**
```
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 0.16s
```

### 2.4 TabbyRush: `node scripts/hub-live-smoke.mjs --fixture-test`
- **Working Directory:** `/home/loidinh/WS/tabbyrush`
- **Execution Time:** ~1s
- **Outcome:** 18/18 checks passed across 5 test suites.
- **Output:**
```
================================================================
TabbyRush Hub Strict-v1 Live Smoke Test Runner
Target Origin: http://127.0.0.1:45711
Application:   tabbyrush
================================================================

--- Suite 1: Health & App Admission ---
  [Admission] GET /api/v1/health returns healthy without redirect... PASS (HTTP 200, status=healthy)
  [Admission] GET /api/v1/apps/tabbyrush echoes strict-v1 mode and schema... PASS (syncMode=strict-v1, epoch=550e8400-e29b-41d4-a716-446655440000)

--- Suite 2: Security & Negative Route Isolation ---
  [Security] Strict sync bootstrap rejects missing API key (HTTP 401/403)... PASS (status 403)
  [Security] Strict sync bootstrap rejects invalid API key (HTTP 401/403)... PASS (status 403)
  [Security] Legacy sync route rejects strict-v1 app with HTTP 426 (protocolUnsupported)... PASS (status 426 (426 protocolUnsupported))

--- Suite 3: Authentication & Access Session ---
  [Auth] Register new user account (POST /auth/register)... PASS (registered (status 200))
  [Auth] Login with user credentials (POST /auth/login)... PASS (accessToken obtained, apps=["tabby-rush"])
  [Auth] Token refresh exchange (POST /auth/refresh)... PASS (token refreshed successfully)
  [Auth] Verify identity and grants (GET /auth/me)... PASS (verified user=test@example.com)

--- Suite 4: Strict Strong Sync Operations ---
  [Sync] Bootstrap establishes authoritative protocol and epoch... PASS (protocol=strict-v1, epoch=550e8400-e29b-41d4-a716-446655440000)
  [Security] Strict sync rejects invalid JWT token (HTTP 401)... PASS (status 401)
  [Sync] Epoch mismatch negative test (POST /push with invalid epoch returns 409)... PASS (status 409 (epochMismatch))
  [Sync] Push create operation: Insert-only create (expectedRevision: 0) accepted... PASS (accepted at revision 1)
  [Sync] Idempotent frozen retry: identical opId + digest returns replayed... PASS (replayed confirmed, acceptedRevision=1)
  [Sync] Push conflict: Base revision mismatch returns revisionConflict... PASS (revisionConflict detected, currentRevision=1)
  [Sync] Pull operation retrieves the specific created record and validates schema... PASS (retrieved record '74df93ce-c270-4cd8-b7c5-8ff620b8e16f' at revision 1)

--- Suite 5: Tombstone Deletion & Invariants ---
  [Cleanup] Revision-CAS delete produces permanent empty tombstone... PASS (deleted at revision 2)
  [Cleanup] Pull confirms specific tombstone preservation (rowId match, deleted: true, data: {})... PASS (tombstone confirmed empty at revision 2)

================================================================
Live Smoke Summary: 18/18 checks passed
================================================================
```

### 2.5 TabbyRush: `CONFIRM_MAINTENANCE_QUIESCENCE=true DRY_RUN=true node scripts/hub-strict-recovery.js`
- **Working Directory:** `/home/loidinh/WS/tabbyrush`
- **Execution Time:** ~0.2s
- **Outcome:** Syntax and pre-execution configuration verified cleanly in dry-run mode.
- **Output:**
```
================================================================
TabbyRush Hub Strict-v1 Maintenance Recovery Script
Database:       glean_hub
App ID:         tabbyrush
Source Backup:  (None - required for live recovery)
Expected Epoch: (Not provided)
Mode:           DRY-RUN (No mutations)
================================================================

[PASS] Prerequisites and configuration syntax verified cleanly.
```

### 2.6 TabbyRush: `node scripts/release-security-check.mjs --diagnose-source`
- **Working Directory:** `/home/loidinh/WS/tabbyrush`
- **Execution Time:** ~0.3s
- **Outcome:** 6/6 checks passed. Diagnostic audit result: CLEAN.
- **Output:**
```
================================================================
TabbyRush Android Release Security & Integrity Checker
Target APK: androidApp/build/outputs/apk/release/androidApp-release.apk
Approved Origin: https://api-glean-hub.qm-hub-v001.cloud
Mode: Diagnostic Source Check
================================================================

=== Running Diagnostic Static Source Audit ===
[PASS] Base manifest prohibits cleartext traffic (usesCleartextTraffic="false")
[PASS] Base manifest restricts backup (allowBackup="false")
[PASS] Exported components audit: Only MainActivity is exported.
[PASS] XML security configs present (backup_rules.xml, data_extraction_rules.xml)
[PASS] Release Hub origin enforces HTTPS: https://api-glean-hub.qm-hub-v001.cloud
[PASS] Repository source secret audit: No hardcoded private keys or passwords detected in source.

Diagnostic Audit Result: CLEAN (Source meets static security rules)
```

### 2.7 TabbyRush: `JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jvm/jdk-21}" ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks`
- **Working Directory:** `/home/loidinh/WS/tabbyrush`
- **Execution Time:** 7.35s wall time
- **Outcome:** BUILD SUCCESSFUL in 7s. 15 actionable tasks executed.
- **Test Counts:** 98 tests executed, 98 passed, 0 failed, 0 skipped.
- **Breakdown by Test Class:**
  - `AccountKeyTest[jvm]`: 5 passed
  - `AccountLeaveProtocolTest[jvm]`: 7 passed
  - `AccountSyncCoordinatorTest[jvm]`: 5 passed
  - `EndpointPolicyTest[jvm]`: 8 passed
  - `HubAuthClientTest[jvm]`: 9 passed
  - `HubAuthSessionTest[jvm]`: 8 passed
  - `HubSyncClientTest[jvm]`: 7 passed
  - `OutboxReconciliationTest[jvm]`: 10 passed
  - `RoomAccountStoreTest[jvm]`: 9 passed
  - `SingleFlightRefreshTest[jvm]`: 3 passed
  - `StagedFullScanTest[jvm]`: 9 passed
  - `StrictDigestTest[jvm]`: 5 passed
  - `TitlePolicyTest[jvm]`: 6 passed
  - `TodoSemanticsTest[jvm]`: 7 passed
- **Warnings:**
  - Deprecated Gradle features detected (incompatible with future Gradle 9.0).
  - Task `:shared:checkKotlinGradlePluginConfigurationErrors` reported skipped by Gradle task runner (0 test impact).

---

## 3. Coverage & Performance Metrics

- **Code Coverage:** Formal line/branch coverage reporting was not enabled for this rerun invocation (requires jacoco/cargo-tarpaulin flags).
- **Execution Times:**
  - Rust sync tests: 30.00s (dominated by crypto digest / property rounds)
  - Clippy check: 0.16s
  - Cargo check workspace: 0.16s
  - TabbyRush fixture smoke: ~1.0s
  - TabbyRush recovery dry run: ~0.2s
  - TabbyRush security check: ~0.3s
  - TabbyRush JVM unit/integration test suite: 7.35s
- **Slow Test Analysis:** Rust test suite takes 30.00s due to iterative schema generation tests and strict digest vectors; all finished well within acceptable bounds.

---

## 4. Build Status & Scope Boundaries

- **Build Status:** SUCCESS across both repositories. Zero compiler errors, zero linter warnings.
- **Scope Limitations:**
  - Fixture mode (`--fixture-test`) runs against a local mock server and validates strict-v1 protocol compatibility, not a live production Hub deployment.
  - Recovery script executed in `DRY_RUN=true` mode validates prerequisite and syntax checking, but does not perform live database record restoration.
  - Release security check ran in `--diagnose-source` mode auditing source manifests and configuration files without inspecting final compiled release APK bytecode.
  - Rust test `test_strong_sync_live_mongo_acceptance_gate` was ignored due to absent `MONGO_TEST_URI`.

---

## 5. Critical Issues & Recommendations

### Critical Issues
- None. All requested suites and commands execute cleanly with zero defects.

### Recommendations
1. **Automated CI Live MongoDB Stage:** Introduce ephemeral MongoDB container in CI to exercise `test_strong_sync_live_mongo_acceptance_gate`.
2. **Gradle 9 Compatibility Migration:** Audit deprecated Gradle APIs using `--warning-mode all` prior to next major Gradle upgrade.
3. **Release APK Security Check in CI:** Pair `--diagnose-source` with binary APK check once release signing pipeline artifacts are produced.

---

## 6. Unresolved Questions

1. Will CI pipelines provision an ephemeral MongoDB instance to un-ignore and execute `test_strong_sync_live_mongo_acceptance_gate` regularly?
2. When will production Glean Hub deployments be updated and verified against live physical Android devices running TabbyRush release builds?
