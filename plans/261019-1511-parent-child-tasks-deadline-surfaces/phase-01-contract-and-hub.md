---
title: "Phase 01 — Freeze v3 contract and implement Hub upgrade/receipts"
description: "Freeze strict-v3 contract, golden vectors, Glean Hub Rust models/services, and fenced upgrade tooling."
status: completed
priority: P2
effort: 24h
branch: main
tags: [feature, contract, backend, rust, kotlin]
created: 2026-10-19
completed: 2026-10-19
---

# Phase 01 — Freeze v3 contract and implement Hub upgrade/receipts

## Context links
- [Parent plan](./evc-cmd-plan.md), [design contract](./design-contract.md), [sync research](./research/hierarchy-sync.md).
- [Current architecture](../../docs/system-architecture.md), [v2 cutover precedent](../../docs/strict-v2-cutover-runbook.md).

## Overview
- Status: **DONE — 2026-10-19** (administrative completion per user directive).
- Source Deliverables: Recorded and complete. Strict-v3 contract frozen, canonical vectors across Kotlin/Rust/Node delivered, Glean Hub Rust models/services and upgrade/receipts implemented, operator CLI tooling updated and tested.
- Technical Acceptance: Remains conditional. Cycle 2 code review scored 7/10 with 0 critical issues (`plans/reports/code-reviewer-261019-1847-phase-01-cycle-2-review.md`). Both cycle-1 criticals (C1 `authorize_strict_access` v3 admission, C2 CLI fingerprint default) are reported fixed, but outstanding review findings (recovery writes under dry-run, fence error propagation, explicit-null enforcement, and absent service-level Hub coverage) remain open remediation gates before live upgrade staging; v2 cross-repository parity check remains conditional; live Mongo/staging acceptance is deferred to Phase 06.
- Effort: 24h credited as a planned-effort administrative roll-up (100% of phase provisional effort).
- Contract Scope: Phase 01 frozen contract unblocks Phase 02 (Room and sync) contract-based development without implying Hub runtime acceptance.

## Key Insights
- v2 schema/digests are immutable; the active checked-in schema is still v1.
- Existing Hub CAS is per-row; neither a batch nor a parent-existence query supplies cross-row atomicity.
- Lost-ack replay must remain provable after parent deletion. Existing receipts accept v1 specifically; v2→v3 receipts are new work.

## Requirements
- Implement [contract](./design-contract.md): exactly two levels, explicit nullable immutable parent identity, effective completion not persisted as a derived aggregate, and confirmed deleted-parent dominance.
- Existing tasks remain top-level standalone/parent-capable roots and may gain newly created children. No moving an existing task under another root, changing a child's parent, or creating grandchildren.
- Preserve v1/v2 schemas, fingerprints, canonical bytes and frozen retry identities, temporal validation, same-user authorization, per-row empty tombstones, bounded push/receipt batches and app-scoped admin checks.
- Upgrade supports v2→v3, with v1 accounts taking explicit v1→v2→v3 sequencing; no assumed direct jump.

## Architecture
- Proposed v3 payload = frozen v2 field order plus nullable `parentTaskId`; new domain `strict-v3`, canonical schema/fingerprint and Kotlin/Rust/Node golden vectors.
- Original-version receipt resolution is separate from permission to apply old-version writes. Unsupported v1/v2 writes remain fenced after v3 publication.
- Fresh child CREATE validates a live same-user/account/table root; a root may already exist from before upgrade. UPDATE cannot turn a root into a child, detach a child, or change its parent. Exact replay/receipt matching precedes current live-parent validation; child DELETE remains legal after parent deletion.
- Each row retains independent CAS/ack outcomes, even when distinct parent/child rows share a legal batch. Client-local atomic cascade is not a Hub multi-document transaction. Root tombstone dominates late children logically; cleanup remains ordinary per-row deletes, not server-invented operations without client acknowledgements.

## Related code files
Existing paths below are verified; proposed new files are marked Create. Prefix `S` = `/home/loidinh/WS/tabby-rush`; `H` = `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync`.
- Create `S/integration/glean-hub/tabbyrush-schema-v3.json`, `strict-v3-vectors.json`; preserve v1/v2 files and active `tabbyrush-schema.json` until deliberate cutover.
- Create `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v3-contract.kt`, `strict-sync-v3-digest.kt`.
- Create `S/scripts/strict-v3-vector-runner.mjs`; add vector generation only where needed, preserving existing v2 vectors/runner behavior.
- Modify `S/scripts/hub-strict-upgrade.mjs`, `hub-schema-provision.mjs` and their existing behavioral tests for target-version-aware operation/read-back.
- Create `H/src/models/strong_sync_v3.rs`; modify `H/src/models/{mod,app,strong_sync,strict_upgrade}.rs`.
- Modify `H/src/services/{strong_sync_service,strict_upgrade_service,strict_upgrade_tests}.rs` for protocol selection, validation, replay/receipts, fenced migration and restart behavior.
- Preserve `H/src/models/strong_sync_v2.rs` digest/schema semantics and existing golden fixtures.
- No deletions planned. Before exported-symbol edits, obtain references via available language server and migrate all consumers (including backend management callers).

## Implementation Steps
1. Freeze exact nullable encoding, field order, fingerprint calculation, protocol response errors, two-level immutable-link semantics, existing-root eligibility and tombstone visibility in the design contract and v3 fixtures. Compute hashes; do not type guessed constants.
2. Define receipt dispatch by original contract version. Preserve frozen operation identity/data; prove replay of an accepted child after root deletion before adding fresh-parent admission checks.
3. Implement Hub v3 create/update/delete validation and canonical digest parity. Verify root lookup includes user/account/table scope, immutable linkage comparison uses authoritative current row, and first-dispatch relationship checks cannot intercept exact replay/receipt proof.
4. Extend upgrade state machine for fenced v2→v3 migration: drain permits, add explicit null parent to live legacy rows only, keep empty tombstones/revisions/epoch/lastOp receipt evidence intact, validate, publish schema last, release fence. Retain original replay proof; current row enrichment must not invalidate historical digest evidence.
5. Define durable checkpoint/resume, dry-run, status/read-back, prepublication rollback and postpublication forward-only recovery. Resolve the inherited permit/backfill/checkpoint findings before relying on live migration safety.
6. Extend legacy receipts to reconcile frozen v2 alongside v1 without applying legacy mutations. Unknown superseded/integrity failure remains explicit manual review, not a rewritten v3 retry.
7. Update CLI admission/version selection/status/read-back; reject wrong fingerprint/version, unsupported skip paths and downgrade. No live migration in this phase without the rollout gate.
8. Exercise real production digest/validation/upgrade functions with temporary smoke harness plus focused existing contract tests. Record fixture verification separately from live Mongo proof.

## Todo list
### Core Source Deliverables (Administratively Closed)
- [x] Freeze schema/preimage/golden vectors (6 valid + 5 preimage rejections; Kotlin/Rust/Node parity reported passing in Cycle 2 review). Convergence contract text unchanged. (DONE 2026-10-19)
- [x] Implement replay-first relationship admission and immutable updates in Hub (`strong_sync_service.rs`, `strong_sync_v3.rs`) — C1 reported fixed (`authorize_strict_access` admits `PROTOCOL_V3`). (DONE 2026-10-19)
- [x] Implement v2 receipts and restartable v2→v3 fenced upgrade (`strict_upgrade_service.rs`, `strict_upgrade.rs`, `app_service.rs`). (DONE 2026-10-19)
- [x] Update operator tooling and exhaustive consumers (`scripts/hub-strict-upgrade.mjs`, `scripts/hub-schema-provision.mjs`) — C2 reported fixed; CLI tests 13/13 reported passing. (DONE 2026-10-19)
- [x] Run baseline vector and unit suites — reported in Cycle 2 review: vectors 6/6+5/5 (v3), 10/10+11/11 (v2), CLI 13/13, cargo 64 pass/1 ignored/0 warnings. Note: v2 cross-repo hash check remains conditional (swallows missing sibling fixture). (DONE 2026-10-19)

### Technical Remediation Gates & Follow-Up Items (Required Before Affected Upgrade Operations, Staging Dry-Runs, or Phase 06 Acceptance)
- [ ] Add service-level / in-process integration tests for v3 Hub paths (admission, replay-after-parent-delete, grandchild/reparent rejections) — required before client reliance.
- [ ] Remediate upgrade recovery branch: gate dry-run execution against side-effect writes (`strict_upgrade_service.rs`), improve fence error propagation, and clear lingering fences via CLI.
- [ ] Enforce explicit-null validation in `validate_transformed_collection_v3` for missing `parentTaskId` keys.
- [ ] Tighten Hub fingerprint validation and CLI guard consistency (`--expected-epoch` before target check).
- [ ] Resolve conditional v2 cross-repository runner check to strictly verify sibling fixture existence.
- [ ] Execute live Mongo replica set upgrade and multi-device acceptance (deferred to Phase 06 delivery gate).
## Success Criteria
- Kotlin/Rust/Node derive identical v3 preimages/hashes and reject malformed/self/cross-user/depth/link-change payloads; legacy vectors unchanged.
- An upgraded existing root accepts a newly created child without changing root identity or legacy task values. Attempts to move that root under another task, detach/reparent a child, or create a grandchild fail closed.
- Child accepted → response lost → parent deleted → exact child retry returns original acceptance/replay proof. A distinct fresh child create after confirmed parent deletion is rejected; raced child is suppressed once tombstone is known.
- Migration preserves populated titles/completion/deadlines/reminders/row IDs/epoch/revisions/tombstones and legacy receipt evidence, including restart and lost publish response.
- Wrong fingerprints/versions/downgrades and unauthorized upgrade calls fail closed; old writes are fenced but authorized receipt reconciliation works.
- Verification commands reported passing in Cycle 2 review: `node scripts/strict-v2-vector-runner.mjs` (PASS 10/10, 11/11); `node scripts/strict-v3-vector-runner.mjs` (PASS 6/6 valid, 5/5 rejections); `cargo test --manifest-path ../glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml --locked` (PASS 64 / 0 fail / 1 ignored); `node --test scripts/hub-strict-upgrade.test.mjs` (PASS 13/13). Note that v2 cross-repository parity check remains conditional; live Mongo tests and physical hardware testbeds remain deferred to Phase 06.

## Risk Assessment
- High: receipts/upgrades currently specialized for v1→v2; do not assume v3 support exists.
- Cross-row races are handled by logical deletion plus durable per-row cleanup, not claims of distributed transactions. A parent lookup or shared batch cannot establish atomic group admission/deletion.
- Existing live-cluster safety holds must not be inherited as presumed passing. Keep source/fixture and staging gates separate.

## Security Considerations
- Same-user root validation; JWT/app grant enforcement; bounded batch/payload; no cross-account references or unencrypted credentials.
- Keep secrets in protected environment variables; no tokens in CLI argv or evidence.

## Next steps
Phase 01 is DONE administratively on 2026-10-19. Contract freeze unblocks Phase 02 (Migrate Room and integrate versioned sync) contract-based development without implying Hub runtime acceptance. Outstanding upgrade safety remediation findings (dry-run writes, fence error handling, explicit-null enforcement) and absent service-level Hub coverage remain strict prerequisite gates before affected upgrade operations and staging dry-runs; full cluster acceptance remains tracked for Phase 06.
