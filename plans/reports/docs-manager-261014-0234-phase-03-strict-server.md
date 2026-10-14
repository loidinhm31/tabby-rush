# Documentation Summary Report: Phase 03 Strict Server & Legacy Isolation

**Date:** 2026-10-14
**Agent:** Phase03Docs
**Scope:** Phase 03 Doggo-Only Strict Server & Legacy Isolation Documentation
**Target Plan:** `plans/261013-1540-doggo-glean-hub-reuse/plan.md`
**Phase File:** `plans/261013-1540-doggo-glean-hub-reuse/phase-03-hub-schema-compatibility.md`

---

## 1. Current State Assessment

Phase 03 delivers the backend foundation for isolated strict synchronization in Glean Hub Sync and the associated integration tooling, schemas, and verification scripts in Doggo.

Documentation across `docs/` has been systematically synchronized with the actual codebase implementation across both repositories:
- **`docs/codebase-summary.md`**: Updated to catalog all 26 Phase 03 files (6 Doggo integration artifacts and 20 Glean Hub Sync plugin source/doc artifacts), restored host tooling section, updated Repomix compaction metrics (133 packed files, 211,737 tokens, 893,878 chars), sanitized mock credentials in prose to resolve secretlint exclusions, and added Phase 03 status invariants.
- **`docs/project-overview-pdr.md`**: Added Section 5 detailing Phase 03 Functional Requirements (FR-STRICT-01 through FR-STRICT-06), deliverable matrix, and updated Section 7 Traceability marking Phase 03 implementation complete with live Mongo gates deferred to Phase 07.
- **`docs/system-architecture.md`**: Expanded Section 4 with Glean Hub backend strict-v1 architecture, MongoDB dedicated namespace (`_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>`), admission preflight CLI, route mode fences (`legacy_sync_mode_guard` returning HTTP 426), CAS mutation lifecycle, and durability policy with `hello` fallback observations.
- **`docs/code-standards.md`**: Added Section 4 for Node.js scripts (ESM `.mjs`, 0600 key custody, CLI arguments, exit codes) and Section 5 for Rust backend integration (`StrictSyncError`, fail-closed admission, clean clippy, CAS invariants); renumbered subsequent sections.
- **`docs/api-contract.md`**: Documented admin registration `POST /api/v1/strict-apps`, schema read-back `GET /api/v1/apps/:app_id`, schema update rejection `PUT /api/v1/apps/:app_id/schema`, `StrictSyncError` structured error envelope with machine codes (400, 401, 404, 409, 426, 500, 503), and clarified big-endian length prefix counts UTF-8 bytes (`u32_be(utf8_bytes.len)`).
- **`docs/project-roadmap.md`**: Updated Phase 03 status to "Implementation Complete*" with qualification footnote; updated planned effort progress to 39% (58h / 148h planned); aligned Phase 07 (device sync / live Mongo) and Phase 08 (release operations / old-binary rollback) deferrals; added `[0.3.0-alpha.1]` changelog entry.
- **`docs/hub-schema-integration.md`**: Added metadata header, navigation links, and cross-references.
- **`docs/hub-compatibility-acceptance.md`**: Added acceptance gate status matrix distinguishing verified static/unit tests from deferred live Mongo gates, and documented open cycle-4 review findings and production blockers.
- **`docs/evidence/hub-compatibility-summary.md`**: Relocated `doggo-strict-sync.md` to Documentation section, separated Doggo vs sibling Glean Hub artifacts, and clarified deferred gates.

---

## 2. Changes Made

1. **`docs/codebase-summary.md`**:
   - Added `integration/glean-hub/doggo-schema.json`, `scripts/hub-schema-provision.mjs`, `scripts/hub-compatibility-smoke.mjs`, and Phase 03 documentation files to the directory tree.
   - Restored Subsection 2.3 (`scripts/start-xpra.sh`, `scripts/android-sdk-env.sh`, `scripts/start-android-emulator.sh`).
   - Added Subsection 2.4 detailing Doggo integration artifacts and sibling `glean-hub-sync` backend modules (Models, Services, Routes, Middleware, Binaries, Documentation).
   - Documented script limitations (provision script stdout key logging, smoke script unauthenticated 401 false-acceptance).
   - Updated Repomix compaction metrics based on fresh scan (133 packed files, 211,737 tokens, 893,878 chars; 1 test file excluded due to mock URL vector; sanitized prose to resolve `docs/codebase-summary.md` exclusion).
   - Removed temporary `repomix-output.xml`.

2. **`docs/project-overview-pdr.md`**:
   - Added Section 5 detailing Phase 03 functional requirements: FR-STRICT-01 (Strict Mode & Canonical Epoch), FR-STRICT-02 (Storage Namespace Isolation), FR-STRICT-03 (Fail-Closed Admission Preflight), FR-STRICT-04 (Atomic CAS Mutations), FR-STRICT-05 (Durable Replay Confirmation), FR-STRICT-06 (Legacy Fencing & Immutability).
   - Added Phase 03 deliverables and status distinction table.
   - Renumbered Technical Constraints & Dependencies to Section 6.
   - Updated Section 7 Traceability marking Phase 03 implementation complete with live Mongo gates deferred to Phase 07.

3. **`docs/system-architecture.md`**:
   - Expanded Section 4 architecture diagram with Hub backend layers: `DualAuth`, `LegacyGuard`, `ScopeGuard`, `StrictRoutes`, `AdminStrict`, `AdmissionHelper`, `MongoDB Dedicated Namespace`.
   - Added detailed subsections 4.1 through 4.6 covering client core, storage namespace isolation, route fencing middleware, mutation semantics, durability policy observations, and operator admission CLI.
   - Documented observation that `get_strict_collection` falls back to standalone (`w: 1, local`) if `hello` errors.
   - Added strict storage isolation and key custody rules to Section 5.

4. **`docs/code-standards.md`**:
   - Added Section 4: Node.js Integration Script Standards (ESM `.mjs`, `node:` imports, CLI flag parsing with environment fallbacks, 0600 file permissions, non-zero exits, syntax verification).
   - Added Section 5: Rust Backend Integration Standards (legacy isolation, non-destructive schema evolution, `StrictSyncError` unified JSON envelope, atomic CAS queries, exact index admission, clippy cleanliness).
   - Renumbered automated testing and security sections to Sections 6 and 7.

5. **`docs/api-contract.md`**:
   - Documented admin registration `POST /api/v1/strict-apps` and schema read-back `GET /api/v1/apps/:app_id`.
   - Documented schema update rejection (`PUT /api/v1/apps/:app_id/schema` returning HTTP 400).
   - Added Section 3.4 documenting `StrictSyncError` envelopes and machine codes (`invalidRequest`, `unauthorized`, `appNotFound`, `epochMismatch`, `protocolUnsupported`, `internalError`, `notAdmitted`).
   - Clarified digest calculation: length prefix counts UTF-8 byte length (`u32_be(utf8_bytes.len)`), not UTF-16 code units or scalar counts.

6. **`docs/project-roadmap.md`**:
   - Updated Phase 03 status to `Implementation Complete*` with qualification footnote.
   - Labeled completion percentage as planned effort progress: 39% (58h / 148h planned).
   - Reconciled downstream deferrals: live Mongo integration suites (§9–§12) deferred to Phase 07; mounted old-binary and legacy-client rollback gates deferred to Phase 08.
   - Added `[0.3.0-alpha.1]` changelog entry.

7. **`docs/hub-schema-integration.md` & `docs/hub-compatibility-acceptance.md`**:
   - Added metadata headers, navigation links, and cross-references.
   - Added Acceptance Gate Status & Verification Matrix distinguishing verified static/unit tests from deferred live Mongo gates.
   - Documented Open Review Findings & Production Blockers (Cycle 4): topology fallback on `hello` error, corrupt-row default unwraps, script key custody and 401 acceptance behavior.

8. **`docs/evidence/hub-compatibility-summary.md`**:
   - Relocated `doggo-strict-sync.md` from Runtime Environment list to Documentation list.
   - Explicitly separated Doggo artifacts (6 files) from sibling Glean Hub artifacts (20 files).
   - Qualified live Mongo gate deferral to Phase 07.

---

## 3. Evidence & Verification

- **Documentation Link Validation (`validate-docs.cjs`)**:
  - Scanned `docs/` files; **74 internal links verified working** with **0 broken links**.
  - Code references: triaged Kotlin symbols (`Todo`, `TodoUiState`, `AccountKey`, `CredentialBundle`, `toString()`) and cross-repository Rust symbols (`StrictSyncError`) not matched by local JS regexes.
  - Config keys: triaged HTTP method backticks (`POST`, `GET`, `PUT`) and shell environment variables (`ANDROID_HOME`, `JAVA_HOME`, `XAUTHORITY`).
- **Documentation Size Limits**: All 14 documentation files strictly comply with `docs.maxLoc` (800 LOC limit). Current sizes range from 3 to 232 LOC (total footprint: 1,625 LOC).
- **Node Script Syntax Check**: Both integration scripts validate cleanly under `node --check`:
  - `scripts/hub-schema-provision.mjs`: Syntax valid (0 errors).
  - `scripts/hub-compatibility-smoke.mjs`: Syntax valid (0 errors).
- **Rust Backend Static & Unit Verification (Recorded from Cycle 4)**:
  - `cargo test --locked` in `glean-hub-sync`: 36 passed, 0 failed, 1 ignored (`test_strong_sync_live_mongo_acceptance_gate`).
  - `cargo clippy --locked --all-targets`: 0 warnings, 0 errors.
- **Compaction & Hygiene**: Fresh Repomix compaction verified (133 packed files, 211,737 tokens, 893,878 chars; 0 secrets in packed output; temporary `repomix-output.xml` deleted).

---

## 4. Gaps Identified & Production Blockers

1. **Live MongoDB Integration Gates (§9–§12 Deferred to Phase 07)**:
   - Full live database acceptance requires a running MongoDB daemon reachable via `MONGO_TEST_URI`.
   - CAS mutations, replay confirmation matching `matched_count == 1`, cursor pagination, and negative admission checks remain unverified against a real database instance.
2. **Durability Topology Fallback (Cycle 4 Warning)**:
   - `get_strict_collection()` in `services/strong_sync_service.rs` queries `hello` on every request and falls back to standalone (`w: 1, local`) if `hello` errors. Must be hardened to fail closed before production deployment.
3. **Corrupt Row Coercions (Cycle 4 Warning)**:
   - `classify_reread` and pull data default unwraps (`unwrap_or`) coerce missing/invalid fields to default values rather than failing closed. Must be hardened to fail closed (`InternalError` or `RetryableFailure`).
4. **Script Key Custody & Acceptance Limitations**:
   - `scripts/hub-schema-provision.mjs` prints the provisioned API key to stdout even when `--out` is specified, and does not alter permissions on pre-existing credential files.
   - `scripts/hub-compatibility-smoke.mjs` accepts HTTP 401 as passing when credentials are omitted; genuine verification requires valid credentials.
5. **Old-Binary Rollback Gates (Deferred to Phase 08)**:
   - Validation against an actual prior Hub binary to verify that old binaries ignore strict collections and preserve strict schema metadata remains deferred to Phase 08 release operations.

---

## 5. Recommendations

1. **For Phase 04–06 Execution**:
   - Proceed with client-side parallel lanes (Phase 04 Auth Session, Phase 05 Local Strict Sync with Room v1, Phase 06 Compose UI) against the frozen contract and big-endian UTF-8 digest specification.
2. **For Phase 07 Integration Gate**:
   - Provision an isolated MongoDB test environment (standalone and replica set) to execute the deferred live acceptance suites (§9–§12).
   - Harden `get_strict_collection()` topology inspection to fail closed or derive topology once at admission.
   - Harden `classify_reread` coercions to fail closed.
3. **For Script Hardening**:
   - Suppress console API key output when `--out` is specified in `hub-schema-provision.mjs`.
   - Require credentials or explicitly output `SKIPPED` in `hub-compatibility-smoke.mjs` when credentials are absent.

---

## 6. Metrics Summary

| Metric | Measurement |
|---|---|
| Total Documentation Files | 14 files |
| Total Documentation Footprint | 1,625 LOC (all files < 235 LOC, limit 800) |
| Internal Links Working | 74 working, 0 broken |
| Node Scripts Validated | 2 scripts (`node --check` clean) |
| Rust Unit/Route Tests | 36 passed, 0 failed, 1 ignored live test |
| Rust Clippy Warnings | 0 warnings, 0 errors |
| Repomix Packed Files | 133 files (211,737 tokens, 893,878 chars) |
| Repomix Excluded Files | 1 test file (`endpoint-policy-test.kt` due to mock URL vector) |
| Plan Progress (Planned Effort) | 39% (58h / 148h planned) |

---

## 7. Unresolved Questions

1. Will the isolated MongoDB instance for Phase 07 live acceptance testing be provisioned as a containerized replica set or standalone daemon?
2. What sanitized legacy client fixtures and previous Hub binary versions will be used for Phase 08 old-binary regression validation?
