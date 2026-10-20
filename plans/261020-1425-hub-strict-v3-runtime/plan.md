---
title: "Repair Glean Hub strict-v3 runtime support"
description: "Implement complete strict-v3 runtime dispatch, hierarchy validation, durable replay and legacy receipts without touching live accounts."
status: pending
priority: P1
effort: 12h
branch: feature/parent-child-tasks-deadline-surfaces
tags: [bugfix, backend, api, database, critical]
created: 2026-10-20
---

# Glean Hub strict-v3 runtime repair

## Scope and evidence

Server-only implementation plan; no implementation or validation executed by this planning assignment. Desktop UI work belongs to the parent task. Source paths below use `H = G:/ws/sharing/glean-hub`, `S = G:/ws/sharing/doggo`, `P = H/glean-hub-plugin/glean-hub-sync`.

Current source, not historical completion labels, is authoritative:
- `P/src/services/strong_sync_service.rs:103-110` admits v1/v2 only; bootstrap, push, pull and receipts share this gate.
- `apply_operation:288-475` has v1/v2 payload/digest branches only. `models/strong_sync_v2.rs` rejects unknown fields and cannot carry `parentTaskId`.
- CREATE/UPDATE/DELETE persistence stamps only v2 `lastOp.contractVersion`; receipt parsing/digest computation is v1-only.
- Schema validation and upgrade code already know v3. Schema publication alone is not runtime support.
- Existing phase-01 administrative completion and reported admission fix conflict with current source; do not reuse those statements as acceptance evidence.

## Sequenced phases

| Phase | Deliverable | Status | Effort |
|---|---|---|---|
| [01 — Model and canonical digest](./phase-01-model-and-digest.md) | Distinct strict v3 model, nullable parent encoding, authoritative vectors | Pending | 3h |
| [02 — Runtime and receipt paths](./phase-02-runtime-and-receipts.md) | Admission/bootstrap/push/pull, relationship semantics, CAS/replay/version evidence, v1/v2 receipts | Pending; depends on 01 | 5h |
| [03 — Isolated acceptance and docs](./phase-03-isolated-validation-and-docs.md) | Deterministic and isolated service evidence, smoke criteria, accurate documentation | Pending; depends on 01–02 | 4h |

## Design decisions

- Add an explicit v3 wire model and digest branch; never extend/permissively deserialize v2 or alias v3 to v2.
- Reuse existing strict authorization, permits, admission, scoped collection, CAS and durability machinery. No new storage abstraction, transaction layer, synthetic cascade, protocol fallback or migration.
- Parse/validate canonical operation → calculate version-specific digest → inspect row and confirm exact replay → enforce fresh relationship constraints → conditional row write. Replay must survive later parent deletion.
- Preserve epoch, row identity, revisions, empty tombstones, historical digests and all v1/v2 canonical bytes.
- Schema/cutover correctness and runtime correctness are separate gates. Existing published v3 state is repaired forward; no downgrade/republication workaround.

## Hard safety boundary

No real-account writes, credential inspection, database requests, migration (including dry-run), production/local-server restart or deployment are authorized by this plan. The running local Hub is connected to a MongoDB cluster. Do not use `MONGO_TEST_URI` or the existing ignored live-Mongo gate. Planned service acceptance requires a newly provisioned, disposable loopback-only MongoDB instance and synthetic identities, separately authorized before execution. Any real-account smoke requires point-of-risk operator confirmation of exact origin/account/app, actions and values; it is not part of this deliverable.

## Completion criteria

V3 root and child CREATE/UPDATE/DELETE round-trip with correct digest acknowledgements; immutable two-level links and same-user scoping enforced; replay and original-version receipts remain provable; cursor and tombstone semantics unchanged; v1/v2 remain usable on v1/v2 apps and fenced on v3 apps. All acceptance evidence must identify source/fixture, isolated service, or operator-authorized environment separately. Allowlisting alone never satisfies completion.

## References and unresolved questions

- [Frozen hierarchy contract](../261019-1511-parent-child-tasks-deadline-surfaces/design-contract.md)
- [Existing phase-01 claims and unresolved gates](../261019-1511-parent-child-tasks-deadline-surfaces/phase-01-contract-and-hub.md)
- [Strict-v3 cutover runbook](../../docs/strict-v3-cutover-runbook.md)
- [Architecture invariants](../../docs/system-architecture.md#43-mutation-semantics--replay-confirmation)

No product questions. Execution prerequisites: isolated testbed approval/provisioning; separate operator authorization for deployment or real-account acceptance. Client currently quarantines unresolved v2 operations during v3 transition; server v2 receipt capability must not be reported as automatic client reconciliation.
