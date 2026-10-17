# Phase 03 — Hub versioned contract and data-preserving migration

## Context links
[Parent](plan.md) · [G1 design](phase-01-freeze-contract-and-transition-design.md) · [Strict source evidence](research/strict-upgrade-research.md)

## Overview
Date 2026-10-15. Priority P1. Status: **DELIVERED (Contracts/Logic/Tests)** (2026-10-15); Gate G3 physical multi-instance Mongo cluster execution formally ON HOLD pending live replica set testbed. Cycle 3 final review completed: ADMISSION ON HOLD (NOT APPROVED FOR PRODUCTION — Score: 8.0/10) due to 3 open code findings and unexecuted live multi-instance cluster tests. Estimate 32h. Owner: Hub backend lead. Depends on approved phase 01. Gate G3: live multi-instance migration/replay safety before production admission.
## Key Insights
Current schema/digest are fixed v1; current fresh-epoch script is destructive to feature-transition semantics. Preserving lastOp without version-aware decoding is insufficient. All readers, writers, admission helpers and older deployed binaries need fencing.

## Requirements
- Exact v1/v2 admission, typed version-aware payloads/digest, preserved CAS/scan/tombstone guarantees.
- One authorized migration operation, durable idempotency/exclusion and publish-last activation.
- Physical `appTabbyRush` collection and strict epoch preserved. Live data gains defaults; tombstones remain empty; protected receipt/revision fields unchanged.
- Reconciliation-only legacy receipts work on v2; no general legacy writes after activation.

## Architecture
Use phase-01 management API/state/permit contract. Server owns target schema registry. Keep ordinary strict schema PUT blocked.
- Migration record stores ID, source/target fingerprints/epoch, owner/fencing generation, phase/checkpoint, validated invariant fingerprints and outcome. Same ID+same request resumes/returns existing result; same ID+different request conflicts.
- Atomic admission to durable app permit document checks ready state/version. Fence CAS disallows new read/write permits, then waits for admitted requests to finish. A permit spans bootstrap/pull data access or whole push batch; finally release only after definitive completion. Bounded admission prevents coordination document growth. Never release merely because request canceled while DB write outcome is uncertain.
- Deploy fence-aware binaries to ALL writers/readers first; old instances stopped/drained. Leases may coordinate migration runner ownership but cannot release unresolved request permits. On crash, retain fence and operation record; no periodic auto-unfence.
- In-place deterministic transform in bounded batches under maintenance: `$set` only missing new live fields to agreed defaults. Do not touch `_id`, user/table/row, revision, deleted, timestamps, lastOp/digest, confirmationNonce or collection indexes. Restart may rescan/reapply same transform; advance checkpoint only after journaled writes. Unexpected pre-existing new fields on v1 fail validation, not silently overwrite.
- Validate every record, protected-field fingerprint, schema and storage admission. Atomically set target version/schema and ready state in registry CAS last; ambiguous publication rereads durable state. Readers validate current contract only after permit admission, so cannot see mixed data/version.
- Before activation: inverse transform removes only known added fields under fence after snapshot equality checks. After activation/v2 writes: no automatic downgrade/backup restoration; forward repair under fence. Never discard post-cutover writes.
- Legacy lastOp without explicit version is v1 under recorded migration provenance. New v2 receipts explicitly store contractVersion. Legacy endpoint recomputes original v1 digest and verifies app/user/epoch; exact receipt confirm uses journaled CAS. Statuses provenNotAccepted need monotonic/permanent-tombstone proof; unknown is not retry permission.
- v2 scan cursors carry/validate contract version as well as epoch. Reject v1 cursors after cutover; upgraded client discards only transient scan staging/cursor and rescans, not outbox/account data.

## Related code files
Hub root `/home/loidinh/WS/glean-hub`.
Modify `glean-hub-plugin/glean-hub-sync/src/models/{strong_sync,app,api}.rs`, `services/{app_service,strong_sync_service}.rs`, `routes/strong_sync.rs`, `middleware/{dual_auth,sync_mode_guard}.rs`, `src/lib.rs`, existing admission binary/service and backend tests; `docs/tabbyrush-strict-sync.md`.
Create focused `src/services/strict_upgrade_service.rs`, `src/models/strict_upgrade.rs`, `src/routes/strict_upgrades.rs`, and gate module within same plugin; no generic migration framework. Add behavior tests under current crate test convention. TabbyRush versioned fixture/digest vectors consumed read-only from phase 01.

## Implementation Steps
1. Add explicit supported contract/schema registry and version-aware request parsing; preserve v1 framing exactly and legacy isolation.
2. Add app-scoped admin checks for management/status routes (global admin plus target-app authorization); use existing role/grant source of truth. Implement nonmutating preflight.
3. Implement durable request permits, fence/drain, runner exclusion and crash-safe state machine. Exercise two-node admission-vs-fence race before transformation code proceeds.
4. Implement default-only in-place transform, invariant fingerprints, publish-last CAS and preactivation rollback/resume. Document topology durability and indeterminate-storage handling.
5. Implement legacy receipt reconciliation and versioned cursors; prove no endpoint can apply v1 data to v2 rows.
6. Execute populated live fixture with create/update/delete/lastOp, concurrent requests, runner/request-process crash and publish-response loss. Preserve backups until phase-12 operator retention procedure.
7. Update backend specification with exercised evidence, not source-level assertions.

## Todo list
- [x] Versioned payload/schema/digest/cursor implementation (strict-v2 admission, 16-column schema validation, SHA-256 canonical digest vectors).
- [x] Authorized idempotent upgrade/status API, coordination permit gate, and distributed fence (app-scoped admin auth, atomic permitCount CAS bound).
- [x] In-place transform/publish/rollback/resume (chunked BATCH_SIZE=500 with incremental checkpointRowId, majority+journal _schemas CAS publication, cumulative resumption accounting).
- [x] Read-only legacy reconciliation and protocol validation (`StrongSyncService::legacy_receipts_inner` protocol == "strict-v1" check, deterministic v1 digest recomputation).
- [ ] Gate G3 live physical multi-instance Mongo cluster execution (fencing/drain races, crash/resume, publish-response loss/replay, physical collection & protected-field preservation; ON HOLD pending live replica set testbed).
## Success Criteria
`cargo test --locked` from plugin; final `cargo clippy --locked --all-targets`; matching Kotlin/Rust vectors. Live disposable Mongo with two Hub nodes demonstrates no partial active contract, no bypass, stable repeated ID, preserved full protected-field fingerprints and receipts. Unauthorized/stale/unsupported requests cause no mutation. Crash/orphan permit recovery remains fenced until safe proof. Tests plus actual API migration run required.

## Risk Assessment
A check-then-write flag is unsafe. No multi-document transaction assumed on standalone. Shared durable admission is extra request cost; measure bounded gate and migration batches without weakening correctness.

## Security Considerations
Check app scope before migration/status/data access; ordinary token cannot upgrade. Never return raw DB errors/task contents. Require explicit approved target and source fingerprint; sanitize operation logs.

## Next steps
1. [COMPLETED] Fix crash-resumption in `StrictUpgradeService::execute_upgrade`: bypassed preflight_check on active resumption owned by same migration_id.
2. [COMPLETED] Enforce `MAX_CONCURRENT_PERMITS` bound during permit admission in `PermitService` via atomic CAS (`permitCount: { $lt: 1000 }` and `$inc: { permitCount: 1 }`).
3. [COMPLETED] Preserve cumulative `records_transformed` and `tombstones_preserved` from `existing_mig` on resumption in `StrictUpgradeService`.
4. [COMPLETED] Validate `request.protocol == "strict-v1"` in `StrongSyncService::legacy_receipts_inner`.
5. [COMPLETED] Configure journal/majority write concern on `_schemas` CAS publication update via `get_strict_collection`.
6. [COMPLETED] Implement batch-wise transform (BATCH_SIZE=500) with incremental `checkpointRowId` persistence.
7. Guard `release_permit` with `activePermits.<permitId>: { $exists: true }` filter to prevent permitCount counter corruption on duplicate/unknown release calls.
8. Provision live MongoDB replica set and execute Gate G3 multi-instance admission, crash recovery, and race validation suite before promoting to Phase 04.
If selected permit algorithm cannot be proved on actual topology, replace design before G3; do not silently narrow to single-process safety.

## Delivery & Verification Evidence
- **Delivery Status:** DELIVERED (Contracts/Logic/Tests) on 2026-10-15.
- **Scope Delivered:**
  - **Strict-v2 Admission & Guarding:** Exact v1/v2 schema validation enforcing all 16 ordered columns (4 original columns `id`, `title`, `completed`, `createdAt` plus 12 added temporal/reminder fields `deadlineDate` through `scheduleToken`), canonical schema fingerprints (`3361a80f...` and `5f539551...`), typed version-aware request/digest processing in Rust (`strong_sync.rs`, `strong_sync_v2.rs`, `sync_mode_guard.rs`).
  - **Coordination Permit Gate:** Distributed durable permit gate in `permit_service.rs` with atomic `permitCount: { $lt: 1000 }` CAS admission bound, request fencing, and quiescence drain.
  - **In-Place Migration Engine:** Authorized upgrade execution in `strict_upgrade_service.rs` performing chunked in-place transform (`BATCH_SIZE = 500`), incremental `checkpointRowId` persistence, cumulative resumption accounting (`prior_transformed_count` & `preflight_tombstone_count`), majority+journal `_schemas` CAS publication, and invariant verification.
  - **Legacy Receipts Route:** Read-only legacy reconciliation endpoint (`routes/strong_sync.rs`, `StrongSyncService::legacy_receipts_inner`) enforcing `protocol == "strict-v1"`, journaled CAS confirmation, and deterministic v1 digest verification without modifying v2 rows.
  - **App-Scoped Admin Authentication:** Middleware in `dual_auth.rs` verifying target app-scoped admin authorization or global admin grant.
- **Validation Reports:**
  - [QA Test Report](../reports/qa-261015-1215-phase-03-hub-v2-admission.md): 211 passing tests/checks across both repositories (0 failures, 1 ignored live Mongo test). Suite breakdown: 54/54 executed Rust tests PASS (32.34s); 23/23 Node vector and schema fingerprint checks PASS; 18/18 offline Hub fixture smoke checks PASS; 116/116 KMP JVM tests PASS (under `--rerun-tasks --dependency-verification=off`); 6/6 static Android security checks PASS. Rust compiler linter clean (0 warnings, 0 errors via `cargo clippy --locked --all-targets`).
  - [Code Review Report](../reports/code-review-261015-1225-phase-03-hub-v2-admission.md): Cycle 3 final review scored 8.0/10 with status **ADMISSION ON HOLD (NOT APPROVED FOR PRODUCTION)** due to 3 open code findings and unexecuted Gate G3 live cluster tests:
    1. Permit release unconditionally decrements `permitCount` without filtering on `activePermits.<permitId>: { $exists: true }`, risking counter drift on duplicate release.
    2. Existing coordination documents lack a `permitCount` backfill for numeric `$lt` predicates.
    3. Batch mutation and checkpoint update are separate writes with ignored checkpoint errors; interruption can undercount transformed rows.
- **Gate G3 Status:** Formally **ON HOLD** pending live replica set testbed. Gate G3 demands physical execution across a live two-node Hub and multi-instance MongoDB replica set covering:
  1. Distributed two-node admission fence race conditions.
  2. Concurrent read/write request permit drain under active migration.
  3. Crash injection during transform batches with resumption from checkpoint.
  4. Publish-response packet loss with idempotent replay.
  5. Physical collection `appTabbyRush` and protected-field fingerprint preservation under real DB storage.
- **Residual Scope & Downstream Phases:** Downstream Phase 04 (Hub admin UI & upgrade script) and Phase 05 (Shared Room migration) can proceed on logic and contract foundations under Gate G1/Phase 03 logic delivery (Phase 06 implementation remains dependent on Phase 05), while Gate G3 live cluster execution and 3 open code remediations remain prerequisites for production admission.
