# Phase 06 — Client version transition, safe replay and intention preservation

## Context links
[Parent](plan.md) · [Transition table](phase-01-freeze-contract-and-transition-design.md) · [Hub dependency](phase-03-hub-versioned-contract-and-migration.md) · [Room dependency](phase-05-shared-model-and-room-migration.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 24h. Owner: shared sync lead. Depends on phases 03/G3 and 05; contract frozen phase 01. Gate G4: populated offline-client upgrade round-trip before feature rollout.

## Key Insights
Old clients cannot be globally drained. Frozen accepted/uncertain requests cannot be rehashed under v2. Revisions alone do not reveal whether a superseded legacy operation was accepted. Blind v1 whole-row replacement loses new remote fields.

## Requirements
- Explicit supported version negotiation and incompatibility preserving local data.
- Exact legacy receipt reconciliation; safe never-sent intention translation only; durable restart/checkpoints/FIFO.
- Full v2 round-trip across nullable/removal, conflicts, scans, tombstones and account leave.
- Historical v1 support limited to required transition/reconciliation, not default alias/shim or permissive payload fallback.

## Architecture
State: ready-v1→upgrade-detected→dispatch-quiesced→legacy-reconciling→v2-base-scanning→safe-intents-translating→ready-v2. Per-row unknown heads can remain blocked with explicit conflict UI while other rows progress safely. Any crash resumes from durable phase/provenance, never guesses based on network timeout.
- Bootstrap precedes scan/push; operation protocol version explicit. An app still on v1 accepts only representable v1 edits; editor clearly disables deadline save with upgrade-required explanation until activation.
- At cutover quiesce coordinator dispatch, recover interrupted sends as UNCERTAIN, keep immutable original op identity/preimage/digest. Legacy endpoint confirms exact accepted tuple or classifies safely; epoch mismatch remains existing reset-required flow, no automatic wipe.
- Receipt success reconciles original captured intention at accepted revision; subsequent v2 scan promotes transformed row. Equal revision comparisons normalize v1 null/default extension only inside explicit transition, not globally weakening divergent-revision checks.
- Read legacy receipt evidence before attempting new v2 writes on that row. For unknownSuperseded preserve queue head/frozen intent; explicit discard/reapply/copy workflows warn prior acceptance may exist, including duplicate-copy risk. No automatic copy/re-ID.
- Never-sent safe translation atomically reuses row identity, applies only known changed legacy fields over scanned v2 state, creates distinct v2 op and marks old intention replaced in same transaction. Titles/completion cannot clear a remotely-added deadline. Missing/tombstoned existing rows enter normal explicit conflict handling, not create resurrection.
- v2 pending intentions created while offline against known v2 base retain full typed fields and changed-field semantics; contract state prevents v2 push to v1 server. Client rollback to old binary not supported after Room upgrade; expose operator guidance.
- Full scans discard stale contract cursor/staging, retain base identities/outbox; completed scan and transition checkpoint must be durable together where used to permit dispatch.

## Related code files
Doggo root `/home/loidinh/WS/doggo`.
Modify shared `contract/{strict-sync-contract,endpoint-policy,safe-errors,account-store}.kt`, `sync/{hub-sync-dtos,hub-sync-client,staged-full-scan,outbox-reconciliation,account-sync-coordinator}.kt`, relevant `data/{doggo-dao,room-account-store,account-sync-meta-entity,outbox-entity}.kt`, UI `conflict-review.kt` and both session/controller error projections.
Create narrow sync contract-transition coordinator if existing coordinator would exceed focused scope; retain v1 digest function only as explicit historical implementation. Extend existing `commonTest` sync/digest suites and JVM storage tests; versioned canonical schema/vector fixtures from phase 01. Update `docs/local-storage-and-sync.md`, `docs/sync-acceptance.md`, Hub integration specs.

## Implementation Steps
1. Add typed v2 wire/digest, bootstrap contract status and legacy-receipt route allowlisting; retain strict redirect/auth/no-automatic-POST-replay protections.
2. Implement durable transition state, dispatch quiescence and bounded receipt reconciliation; validate per-op ID/row/digest/acceptedRevision against original frozen op.
3. Implement versioned scan cursors and explicit equal-revision null/default normalization during migration only.
4. Translate provably unsent changes over v2 base transactionally; preserve later drafts/successors and per-row blocked heads. No translation based on missing receipt alone.
5. Update conflict comparison/reapply/copy with deadline/rule and unknown-history warnings; only submitted resolution can schedule.
6. Populate live v1 server+old client: unsent create/update/delete; lost push response; accepted then superseded; concurrent new deadline from v2 device; upgrade server+client, interrupt each checkpoint, restart and complete safe work.
7. Confirm v1 old binary returns incompatibility without deleting local work; reconnect updated binary retains original intents and no duplicate row creation.

## Todo list
- [ ] Version negotiation/wire/digest/receipt client.
- [ ] Durable transition and exact legacy outcome matching.
- [ ] Safe mask-based rebasing and per-row uncertain conflicts.
- [ ] Cross-version lost-response/restart/live round-trip evidence.

## Success Criteria
Target existing reconciliation/coordinator/staged-scan JVM tests plus behavior regressions for uncertain/superseded outcomes and stale title edit preserving remote deadline. Live smoke proves no duplicate create, no lost local intention, unchanged original digest and explicit conflicts; Rust/Kotlin/Node v2 bytes agree. New fields including clear/null/completion/deletion round-trip both directions. Deadline save never silently strips metadata on a v1 app.

## Risk Assessment
Legacy operation history may be incomplete: explicit review is safer than guessed translation. No tests asserting copies/wiring; preserve tests covering CAS/replay boundaries and remove incidental wording assertions.

## Security Considerations
Legacy route dual-auth scoped to app+user; do not weaken origin allowlist or introduce unauthenticated receipt lookup. Local operation history remains account-partitioned and wiped.

## Next steps
G4 unlocks end-to-end reminder/sync acceptance. Phase 07 can develop against completed phase-05 storage but final conflict/remote-change integration depends on this phase.

## Unresolved questions
None outside approved G1/G3 design; unprovable superseded acceptance is an intentional conflict state, not missing implementation.
