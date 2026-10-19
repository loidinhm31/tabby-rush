# Phase 02 — Migrate Room and integrate versioned sync

## Context links
- [Parent plan](./evc-cmd-plan.md), [design contract](./design-contract.md), [Phase 01](./phase-01-contract-and-hub.md), [sync research](./research/hierarchy-sync.md).
- [Storage/sync architecture](../../docs/local-storage-and-sync.md).

## Overview
Date: 2026-10-19. Priority: P2. Implementation: pending. Review: pending. Add durable relationships and safe v3 transitions without changing frozen legacy operations.
- Effort: 24h (provisional planning estimate only; not implementation or verification credit).
- Dependency: Phase 1 must freeze v3 payload, receipts and transition rules before this integration relies on them. Contract freeze does not satisfy the matching sibling Hub runtime/Phase 6 rollout gate.

## Key Insights
- Four task-bearing Room tables copy task payloads; updating only `Todo` loses relationships during ack/scan/conflict/restart.
- Nullable clearing must use whole-snapshot provenance, not field-by-field `?:` fallback. Parent IDs are immutable on normal updates.
- Current flat implementation: eligibility is FIFO per row with no parent dependency. Proposed v3 change: confirm root identity in the current account/epoch before a child's initial freeze/dispatch; frozen/`UNCERTAIN` replay bypasses renewed parent-liveness gating and preserves exact bytes/identity, not account authorization or integrity checks.

## Requirements
- Explicit Room 2→3 migration; populated 1→2→3 remains valid. Existing payload rows in all four task-bearing tables gain null relationship/root semantics without rewriting frozen v1/v2 wire bytes; no destructive fallback.
- Carry immutable relationship through load/save/draft/outbox/freeze/DTO/scan/promotion/ack/conflict and all account factories. Normal edit/reapply cannot move a root, detach/reparent a child, or create deeper nesting. Copy-as-new recovery may deliberately create a new root only by explicit user action, never automatically promote a child.
- `ready-v3` gates hierarchy creation, including new children under existing roots. New binaries can edit ordinary flat v1/v2 accounts until upgrade; no hidden local-only hierarchy or stripped relationship payloads.
- Supply Phase 3 with complete graph/tombstone evidence and committed-base/submitted-outbox provenance distinct from unsubmitted editor drafts. Preserve durable last-child-preservation intent/review provenance needed for its client-local scheduling barrier across restart.

## Architecture
- Add nullable relationship to all four task-bearing payload tables: `server_base`, `editor_drafts`, `outbox`, `scan_staging`; add version/provenance support to existing sync metadata as required by the frozen contract.
- Retain raw completed and parent identity in persisted records; expose committed versus draft provenance without substituting a projected aggregate into saves. Phase 3 supplies shared effective-state projections and group mutation semantics.
- Preserve permanent root tombstones through full scans, promotion and reopen. Missing root without tombstone yields unresolved classification, never deletion inference from a partial page.
- Transition sequence v1→v2→v3 with original-version receipts, complete v3 base scan, safe intent translation only after proof and durable checkpoints.

## Related code files
Prefix `S` = `/home/loidinh/WS/tabby-rush`.
- Modify `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/{todo-models,account-store}.kt`.
- Modify `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/{server-base-entity,editor-draft-entity,outbox-entity,scan-staging-entity,account-sync-meta-entity,tabbyrush-database,tabbyrush-dao,database-migrations,room-account-store,persisted-schedule-snapshot}.kt`.
- Modify `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/{hub-sync-dtos,hub-sync-client,outbox-reconciliation,staged-full-scan,account-sync-coordinator,contract-transition-coordinator}.kt`.
- Create generated `S/shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/3.json`; preserve `1.json`, `2.json`.
- Create `S/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v2-to-v3-test.kt`; extend existing store/sync/adversarial SQLite tests and common sync tests for changed contracts.
- Both existing Android/JVM database builders already register `DatabaseMigrations.ALL`; verify registration remains sufficient, change only if necessary. Locate factory and test-double consumers through symbol references before interface edits.
- No obsolete compatibility aliases or duplicated v3-only store planned.

## Implementation Steps
1. Obtain exported-symbol references; migrate every implementation/test fake alongside contract changes. Add relationship fields and v3 version selection without altering v1/v2 serialization/digest output.
2. Implement additive `MIGRATION_2_3` and register in `ALL`; initialize relationship null in all four payload tables, leaving existing tasks root/parent-capable. Generate Room export from build, not handwritten identity hashes. Preserve all seven stores, indices, settings, ledger state and frozen bytes/provenance.
3. Update all payload conversions and whole-snapshot projection paths. Explicit null parent on roots must not resurrect a stale relationship via fallback; relationship reassignment is rejected rather than normalized. Deleted payloads remain canonical empty data on wire.
4. Add v3 freeze/DTO handling, scan validation and equal-revision migration enrichment restricted to the new null relationship. Ordinary equal-revision divergence still fails closed.
5. Add first-dispatch gating to existing per-row FIFO before initial child freezing: re-read confirmed root evidence in the current account/epoch atomically with eligibility checks under existing mutation/freeze admission. Root's failed/conflicted create keeps child unfrozen, pending and visible. Only an already-admitted frozen/`UNCERTAIN` operation bypasses renewed parent-liveness checks for exact replay; retain account authorization and operation identity/integrity checks, never re-ID it or recompute its digest.
6. Extend transition durability and receipt dispatch per Phase 1: preserve old frozen bytes/provenance, reconcile accepted/not-accepted/unknown/integrity outcomes, scan complete base, translate only proven safe intent with new v3 IDs.
7. Update conflict snapshots/reapply without losing immutable parent identity/deadlines or mutating deleted-parent groups. Copy-as-new requires an explicit new-root action/identity, not relationship reassignment or silent promotion. Reject unsupported server-version regressions and wrong v3 fingerprints.
8. Make ancestor deletion observable to later projection: retain/reload tombstones after full scan/restart; provide complete graph and committed/submitted-versus-draft provenance to Phase 3 rather than a lossy live-row list. Persist/reload the submitted preservation-intent review state it needs; ordinary pending parent UPDATEs must remain distinguishable.
9. Exercise migration/reopen and actual store→freeze→ack→scan code with temporary production-path smoke; run targeted behavioral suites once integration edits settle.

## Todo list
- [ ] Migrate every relationship payload and symbol consumer.
- [ ] Implement/export Room 3 and populated migration chains.
- [ ] Integrate v3 dispatch/scan/ack and confirmed-parent gating.
- [ ] Implement legacy receipt transition/restart/version fences.
- [ ] Verify immutable retries and permanent tombstone availability.

## Success Criteria
- Real populated 2→3 and 1→2→3 SQLite reopen preserves tasks, deadlines, queued/frozen identities/bytes, conflicts, settings and reminder ledger; relationship fields default null in `server_base`, `editor_drafts`, `outbox` and `scan_staging`, retaining existing-root eligibility.
- Parent/child IDs survive save→close/reopen→push/ack→pull/scan and conflict workflows; null roots and canonical empty tombstones stay correct. Edit/reapply rejects moving roots and detaching/reparenting children; explicit copy-as-new creates a new root without changing the source relation.
- A lost response and restart replay exactly the same legacy/v3 op ID/revision/digest/data; unmatched acks stay uncertain, not accepted.
- A newly created child under an existing or newly created root cannot initially freeze/dispatch before root confirmation; admission and eligibility are checked atomically. Once admitted/frozen, lost-response replay after root deletion/restart resolves the exact original operation/receipt before live-parent validation, retaining account authorization and identity/integrity checks.
- Full scans do not discard tombstone evidence or mistake pagination absence for deletion; incomplete scans never publish a destructive half-graph. Reopen preserves committed/submitted-versus-draft and preservation-intent review provenance needed by Phase 3.
- Run focused `:shared:jvmTest` migration/store/sync classes, unchanged legacy vectors and v3 runner. Exact runtime command/environment recorded in Phase 6 evidence, not claimed here.

## Risk Assessment
- Payload copies and `copyAsNew` paths are easy to miss; use references and end-to-end persistence paths, not source-text assertions.
- A protocol gate without legacy receipts can strand uncertain work; keep user recovery visible and fail closed.
- Migration-mode enrichment must not become a general equal-revision overwrite bypass.
- Confusing the current flat FIFO observation with proposed dependency gating can strand frozen retries. Enforce the first-dispatch-only boundary and retain exact historical receipt resolution.

## Security Considerations
Account partitioning, generation/wipe fencing, original lock hierarchy and credential custody remain unchanged. No new global relationship store; no task content in pending intents/logs.

## Next steps
Phase 3 depends on this phase's complete graph/tombstone and committed provenance primitives before defining shared surface interfaces. Phase 1 frozen contract is the integration prerequisite; matching sibling Hub runtime acceptance still gates rollout. Unresolved product questions: none; contract/receipt/checkpoint freeze remains a Phase 1 prerequisite.
