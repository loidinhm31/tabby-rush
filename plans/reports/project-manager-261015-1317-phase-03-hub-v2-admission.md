# Project Manager Status Report: Phase 03 Hub v2 Admission, Fencing & Migration

**Date**: 2026-10-15  
**Phase**: Phase 03 — Hub versioned contract and data-preserving migration  
**Status**: DELIVERED (Contracts/Logic/Tests); Technical Acceptance blocked by 3 open code findings and unexecuted Gate G3 live cluster tests; Code Review ADMISSION ON HOLD (Score: 8.0/10)  
**Author**: evcProjectManagerPhase03 (Senior Project Manager & System Orchestrator)  
**Target Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/plan.md`  

---

## 1. Executive Summary & Progress Status

Phase 03 implementation across Glean Hub Sync backend (`glean-hub`) and TabbyRush integration suites delivered. Contracts, backend logic, and automated unit/vector/smoke test suites verified.
- **Planned Effort Credited**: 32h / 32h allocated (evolution plan cumulative credited: 64h / 236h = 27.1% delivery roll-up).
- **Delivery Classification**: DELIVERED (Contracts/Logic/Tests).
- **Production Status**: ADMISSION ON HOLD (NOT APPROVED FOR PRODUCTION per Cycle 3 Code Review score 8.0/10).
- **Technical Acceptance & Gate G3 Physical Execution**: Blocked by 3 open code findings and unexecuted live multi-instance MongoDB replica set tests.

---

## 2. Key Achievements & Delivered Scope

1. **Strict-v2 Admission & Contract Guarding**:
   - Exact schema validation enforcing all 16 ordered columns (original 4 columns `id`, `title`, `completed`, `createdAt` plus 12 newly added temporal/reminder fields `deadlineDate` through `scheduleToken`).
   - Bit-identical canonical schema fingerprints: v1 (`3361a80f...`) and v2 (`5f539551...`).
   - Version-aware request decoding and typed DTO handling in `strong_sync.rs`, `strong_sync_v2.rs`, `sync_mode_guard.rs`.
2. **Coordination Permit Gate**:
   - Durable app-scoped permit document coordination in `permit_service.rs`.
   - Atomic `permitCount: { $lt: 1000 }` CAS admission bound with `$inc: { permitCount: 1 }`.
   - Reader/writer request fencing and quiescence drain before schema transformation.
3. **In-Place Migration Engine**:
   - Chunked in-place transformation in `strict_upgrade_service.rs` (`BATCH_SIZE = 500`) with incremental `checkpointRowId` persistence.
   - Preserves collection `app_<appId>`, epoch, row IDs, revisions, tombstones, receipts; no destructive epoch reset.
   - Cumulative resumption accounting: `prior_transformed_count` and `preflight_tombstone_count` loaded from existing migration document on retry.
   - Durable publication: `_schemas` updated via CAS using majority write concern with journal acknowledgment (`w: Majority`, `journal: true`).
4. **Legacy Receipts Route**:
   - Read-only legacy receipt reconciliation route in `routes/strong_sync.rs` and `StrongSyncService::legacy_receipts_inner`.
   - Explicit `protocol == "strict-v1"` validation rejecting non-v1 requests with `ProtocolUnsupported`.
   - Deterministic v1 digest recomputation and journaled CAS confirmation without modifying v2 rows.
5. **App-Scoped Admin Authentication**:
   - Middleware in `dual_auth.rs` enforcing app-scoped administrative authorization (global admin or target app grant required).

---

## 3. Testing Requirements & Verification Evidence

QA test execution report (`plans/reports/qa-261015-1215-phase-03-hub-v2-admission.md`) attributes 211 executed passing tests across 4 suites (0 failures, 1 ignored test), plus 6 static Android security checks:
- **Rust Backend (`cargo test --locked`)**: 54/54 executed tests PASS, 1 test ignored (`test_strong_sync_live_mongo_acceptance_gate` requiring live MongoDB).
- **Rust Clippy (`cargo clippy --locked --all-targets`)**: 0 warnings, 0 errors (clean).
- **TabbyRush Strict-v2 Vector Runner (`strict-v2-vector-runner.mjs`)**: 23/23 checks PASS (schema fingerprints, cross-repo fixture parity, 10 valid vectors, 11 rejection vectors).
- **TabbyRush Hub Live Smoke (`hub-live-smoke.mjs --fixture-test`)**: 18/18 checks PASS across 5 suites (health, route isolation, auth session, strict sync, tombstone preservation).
- **TabbyRush Shared JVM Suite (`./gradlew :shared:jvmTest --rerun-tasks --dependency-verification=off`)**: 116/116 tests PASS across 17 suites (including 7 strict DST vectors and 11 strict-v2 digest vectors). Note: executed with `--dependency-verification=off` per assignment command.
- **Android Release Security Audit (`release-security-check.mjs --diagnose-source`)**: 6/6 static source checks PASS (additional audit outside the 211 executed unit/vector test count).

---

## 4. Risk Assessment & Material Code Review Findings

Cycle 3 Code Review (`plans/reports/code-review-261015-1225-phase-03-hub-v2-admission.md`) scored 8.0/10 with status **ADMISSION ON HOLD (NOT APPROVED FOR PRODUCTION)** based on 3 open code findings and unexecuted Gate G3 live cluster tests:
1. **Permit Release Counter Idempotency**: `PermitService::release_permit` executes `$inc: { permitCount: -1 }` unconditionally without filtering on `activePermits.<permitId>: { $exists: true }`. Duplicate or unknown release calls cause counter drift downwards, allowing future admissions to exceed the 1,000 ceiling.
2. **Missing PermitCount Backfill**: Pre-existing coordination documents lack `permitCount`, which does not evaluate against numeric `$lt` predicates until backfilled.
3. **Batch Mutation vs Checkpoint Write Atomicity**: In `transform_collection_in_place`, record update and migration document checkpoint update are separate writes with ignored checkpoint errors; runner crash during this window undercounts transformed rows.
4. **Gate G3 Multi-Instance Cluster Validation (MANDATORY)**: Physical execution across a live two-node Hub and MongoDB replica set covering distributed fencing races, concurrent request drain, crash injection during transform batches, and publication loss/replay has not yet been executed on live infrastructure.

---

## 5. Next Steps & Action Items

Main agent MUST prioritize completing implementation plans and finishing all planned tasks! Finishing the plan is critically important to maintain project velocity, architectural integrity, and production safety.

1. **Remediate Code Review Findings in Glean Hub Sync**:
   - Add `activePermits.<permitId>: { $exists: true }` filter in `PermitService::release_permit` to prevent permitCount downward corruption on duplicate/unknown releases.
   - Implement a safe migration for pre-existing coordination documents that either derives `permitCount` from verified active permits or initializes zero under verified quiescence (avoiding naive unconditional zero initialization while permits might be active).
   - Wrap batch transform and checkpoint persistence into durable verified write logic so interruptions cannot cause undercounting.
2. **Execute Gate G3 on Multi-Instance Replica Set**:
   - Provision live MongoDB replica set testbed.
   - Run dual-Hub concurrent admission race, crash recovery, and publication loss tests before production cutover.
3. **Advance Dependent Phases Under Gate G1 / Phase 03 Contract Clearance**:
   - **Phase 04 (Hub Admin UI and Upgrade Script)**: Unblocked to develop operator management UI and script using the delivered API-only Hub management endpoints (no direct database mutation).
   - **Phase 05 (Shared Model and Explicit Room 1→2 Migration)**: Proceed with local Room SQLite schema migration using frozen v2 contract.
   - **Phase 06 (Client Version Transition and Replay)**: Implementation remains dependent on Phase 05 completion, with contract-based interface planning unblocked.
---

## 6. Unresolved Questions

1. When will the staging environment provision a secondary Glean Hub instance and MongoDB replica set to automate two-node admission fence race tests in CI?
2. When will Gradle dependency checksum metadata in `gradle/verification-metadata.xml` be updated to safely remove the `--dependency-verification=off` flag?
