# Phase 01 — Contract & Safety Freeze

## Context links

[Overview](plan.md) · [Contract and ownership](contracts-and-ownership.md) · [Proposed architecture](../../docs/standard-sync-cutover-design.md) · [Source research](reports/scout-findings.md).

## Parallelization info

Sequential prerequisite. No implementation owners start until interfaces and exclusive file ownership are accepted. Then phases 02/03/04 can run concurrently against the frozen contract; no live deployment during that wave.

## Overview

Date: 2026-10-20. Priority P1. Status: complete as the contract/safety prerequisite; implementation and rollout acceptance remain separate. Language-server reference tooling was unavailable; implementation owners used scoped search inventories and make no LSP-completeness claim.

## Key insights

- Native apps share strict-specific persistence and orchestration; this is not a HubSyncClient URL substitution.
- Strict and standard collection name matches but `_id`/version/timestamp envelope differs.
- Standard generic validation remains. Schema version is metadata, not protocol fencing.
- Strict operation proof, server hierarchy and permanent deletion are deliberately not generic protocol guarantees.

## Requirements and architecture

Freeze the proposed architecture and contracts as written. Prefer delta for ordinary incremental cycles, full pull before initial conversion/recovery, exact per-row acknowledgement, local intent preservation and conservative review of uncertainty. Counter update is atomic with registry content; missing baseline 1 changes to 2. Separate native Room4, Hub schemaVersion, standard record.version and checkpoint.

The default response API is additive for sync readers but intentionally changes schema admin save semantics to require expectedSchemaVersion. Migrate every writer/caller, do not maintain a legacy unconditional overwrite alias.

## Related code files / file ownership

Coordinator owns this plan's overview, contracts and status only. Phase source owners remain those in contracts-and-ownership.md. This phase does not change runtime files. Existing planned architecture documents are design inputs, not claims of implemented behavior.

Before editing exported symbols, implementation owners must discover available language servers and collect references; publish additional callsites into their ownership manifest. H backend/generated TS, H admin src and D native scopes must stay disjoint.

## Implementation steps

1. Review actual standard protocol/record/schema update source cited in scout findings; resolve any discrepancy against code rather than older docs.
2. Freeze exact admin success/error JSON, positive exact-integer bounds and no-op equality. Error 409 must expose a structured current version without secrets.
3. Enumerate all schema writers, API consumers, generated type changes and native strict feature gates. Mark each with one phase owner.
4. Freeze migration stages: inspection → prepared/validated → publication under maintenance → verified standard mode → reopen. Record durable stage transitions and what authoritative state proves each stage after a crash.
5. Confirm logical identity/index/timestamp mapping and preservation of all task fields. Do not use strict digest verification as a standard runtime dependency.
6. Freeze uncertain-operation handling and long-offline retention recovery; missing remote state cannot be evidence for recreating previously confirmed records.
7. Verify tests/smoke plans cover consumer behavior and the side-effect checklist. Select an isolated test database only; no production configuration inheritance.
8. Hand contracts to three disjoint implementation owners together; require terminal reports and no builds/tests during the edit wave.

## Todo list

- [x] Freeze API/metadata/error contract.
- [x] Freeze Room intent conversion and server publication recovery states.
- [x] Complete scoped caller/ownership inventory; LSP unavailable, no LSP-completeness claim.
- [x] Approve explicit differences from strict sync and real proof matrix.
- [x] Launch phase02/03/04 owners under disjoint ownership.

## Success criteria

All interfaces and safety decisions are actionable; no phase has overlapping file ownership; every acceptance row has an owner and real verification method. No live mutation implied by approval of a code plan.

## Conflict prevention

A newly discovered file is assigned before editing. Changes to this contract require communicating the revised interface to all affected owners before dependent edits; do not introduce ad hoc shims.

## Risks and security considerations

The main risk is misrepresenting standard sync as strict or schemaless. Keep guarantees explicit. Never use archived JWTs, Mongo URIs or secrets. An existing healthy server does not establish an isolated testbed or migration permission.

## Next steps

Phases 02,03,04 in parallel. Phase05 only after all terminal reports and integration review.

## Unresolved questions

None in design. Execution configuration and exact live-target authorization remain gated.
