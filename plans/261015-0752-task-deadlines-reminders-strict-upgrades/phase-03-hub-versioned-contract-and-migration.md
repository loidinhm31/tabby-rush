# Phase 03 — Hub versioned contract and data-preserving migration

## Context links
[Parent](plan.md) · [G1 design](phase-01-freeze-contract-and-transition-design.md) · [Strict source evidence](research/strict-upgrade-research.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 32h. Owner: Hub backend lead. Depends on approved phase 01. Gate G3: live multi-instance migration/replay safety before production admission.

## Key Insights
Current schema/digest are fixed v1; current fresh-epoch script is destructive to feature-transition semantics. Preserving lastOp without version-aware decoding is insufficient. All readers, writers, admission helpers and older deployed binaries need fencing.

## Requirements
- Exact v1/v2 admission, typed version-aware payloads/digest, preserved CAS/scan/tombstone guarantees.
- One authorized migration operation, durable idempotency/exclusion and publish-last activation.
- Physical `app_<appId>` collection and strict epoch preserved. Live data gains defaults; tombstones remain empty; protected receipt/revision fields unchanged.
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
Modify `glean-hub-plugin/glean-hub-sync/src/models/{strong_sync,app,api}.rs`, `services/{app_service,strong_sync_service}.rs`, `routes/strong_sync.rs`, `middleware/{dual_auth,sync_mode_guard}.rs`, `src/lib.rs`, existing admission binary/service and backend tests; `docs/doggo-strict-sync.md`.
Create focused `src/services/strict_upgrade_service.rs`, `src/models/strict_upgrade.rs`, `src/routes/strict_upgrades.rs`, and gate module within same plugin; no generic migration framework. Add behavior tests under current crate test convention. Doggo versioned fixture/digest vectors consumed read-only from phase 01.

## Implementation Steps
1. Add explicit supported contract/schema registry and version-aware request parsing; preserve v1 framing exactly and legacy isolation.
2. Add app-scoped admin checks for management/status routes (global admin plus target-app authorization); use existing role/grant source of truth. Implement nonmutating preflight.
3. Implement durable request permits, fence/drain, runner exclusion and crash-safe state machine. Exercise two-node admission-vs-fence race before transformation code proceeds.
4. Implement default-only in-place transform, invariant fingerprints, publish-last CAS and preactivation rollback/resume. Document topology durability and indeterminate-storage handling.
5. Implement legacy receipt reconciliation and versioned cursors; prove no endpoint can apply v1 data to v2 rows.
6. Execute populated live fixture with create/update/delete/lastOp, concurrent requests, runner/request-process crash and publish-response loss. Preserve backups until phase-12 operator retention procedure.
7. Update backend specification with exercised evidence, not source-level assertions.

## Todo list
- [ ] Versioned payload/schema/digest/cursor implementation.
- [ ] Authorized idempotent upgrade/status API and distributed fence.
- [ ] In-place transform/publish/rollback/resume.
- [ ] Read-only legacy reconciliation and live failure injection evidence.

## Success Criteria
`cargo test --locked` from plugin; final `cargo clippy --locked --all-targets`; matching Kotlin/Rust vectors. Live disposable Mongo with two Hub nodes demonstrates no partial active contract, no bypass, stable repeated ID, preserved full protected-field fingerprints and receipts. Unauthorized/stale/unsupported requests cause no mutation. Crash/orphan permit recovery remains fenced until safe proof. Tests plus actual API migration run required.

## Risk Assessment
A check-then-write flag is unsafe. No multi-document transaction assumed on standalone. Shared durable admission is extra request cost; measure bounded gate and migration batches without weakening correctness.

## Security Considerations
Check app scope before migration/status/data access; ordinary token cannot upgrade. Never return raw DB errors/task contents. Require explicit approved target and source fingerprint; sanitize operation logs.

## Next steps
G3 unlocks phase 04 live entrypoints and phase 06 integration. Production not upgraded yet.

## Unresolved questions
If selected permit algorithm cannot be proved on actual topology, replace design before G3; do not silently narrow to single-process safety.
