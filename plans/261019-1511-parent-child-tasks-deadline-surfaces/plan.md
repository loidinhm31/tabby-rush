---
title: "Parent/child tasks and compact deadline display"
description: "Validated two-level task groups, automatic parent completion, and own-deadline compact surfaces."
status: in-progress
priority: P2
effort: 120h
branch: main
tags: [feature, frontend, backend, database, api]
created: 2026-10-19
---

[Open the canonical plan, phases, decisions, and acceptance gates](./evc-cmd-plan.md).

## Implementation phases

| Phase | Status / progress | Provisional effort |
|---|---|---|
| [1 — Contract and Hub](./phase-01-contract-and-hub.md) | DONE (2026-10-19) / 100% (24h credited) | 24h |
| [2 — Room and sync](./phase-02-room-and-sync.md) | DONE (2026-10-19) / 100% (24h credited) | 24h |
| [3 — Hierarchy and main UI](./phase-03-hierarchy-and-main-ui.md) | Pending / 0% | 24h |
| [4 — Android widget](./phase-04-android-widget.md) | Pending / 0% | 12h |
| [5 — Desktop sticky](./phase-05-desktop-sticky.md) | Pending / 0% | 12h |
| [6 — Acceptance and rollout](./phase-06-acceptance-and-rollout.md) | Pending / 0% | 24h |

Planned effort totals 120h; 48h credited as a planned-effort administrative roll-up (40.0%). Phase 01 administratively marked DONE on 2026-10-19 (24h credited): strict-v3 contract freeze, canonical vectors reported passing in Cycle 2 review across Kotlin/Rust/Node, Glean Hub Rust models and admission/upgrade services landed, and operator CLI reported passing unit tests. Contract freeze unblocks Phase 02 (Room and sync) contract-based development without implying Hub runtime acceptance. Phase 02 marked DONE administratively on 2026-10-19 (24h credited): Room 3 migration (MIGRATION_2_3, nullable parentTaskId on server_base, editor_drafts, outbox, scan_staging), confirmed-parent first-dispatch gating (serialized by coordinator mutex rather than inside SQLite freeze transaction), uncertain replay bypass, versioned v3 push/pull/bootstrap, and V2->V3 safe intent translation delivered; QA Cycle 3 reports 371 passing validations (318 JVM tests across 39 suites + 53 fixture/mock checks under --dependency-verification=off; no tests rerun in documentation update); Cycle 3 review scored 7/10 (0 critical issues, C1 closed, H1-residual open). Technical acceptance remains conditional: H1-residual (release-blocking defect where uncertain/dispatched V2 operations are permanently fenced after READY_V2→V3) and live transition-recovery acceptance are tracked remediation gates in Phase 06 alongside inherited Phase 01 gates and live Mongo staging. Phase 03 contract-based development is unblocked; stored last-child preservation review provenance remains an open Phase 03 prerequisite. Remaining pending phases: Phase 3 (Hierarchy and main UI, 24h), Phase 4 (Android widget, 12h), Phase 5 (Desktop sticky, 12h), Phase 6 (Acceptance and rollout, 24h).

## Validation Summary

**Validated:** 2026-10-19. **Final interview:** 3 decision topics; existing-task choice clarified separately. The interview confirmed existing choices; this authorized revision propagates those choices and Advisor refinements through requirements, steps, acceptance, risks, and dependencies.

### Confirmed Decisions
- Exactly two levels. Existing tasks remain top-level, standalone or parent-capable; new children may be created under them. No moving/reparenting.
- Parent completion is automatic while children exist: all complete → complete; adding/reopening an incomplete child → active. No parent/group completion toggle.
- Parent deletion confirms and deletes the group. Locally observed last-child deletion preserves immediately preceding effective completion and restores manual completion with that resultant raw value.
- Child creation is main-app-only; widget/sticky Add continues to create roots.
- Android widget and Linux/Windows sticky mode keep whole groups together with each row's own deadline date and optional time; no inheritance.
- Main Active/Completed filters show matching rows plus parent context, not nonmatching siblings. Aggregation still uses the complete applicable snapshot.
- Cross-device guarantee is local atomicity + conflict review, not globally coordinated group mutations.

### Alignment and remaining work
- Final interview introduced no changed product choice; this revision makes confirmed choices operational throughout the existing plan rather than leaving them only in this summary.
- Advisor refinements retained: independent remote acknowledgements; no synthetic conflict for disjoint child deletions; client-local preservation-intent scheduling barrier; reversible local root-delete suppression without resurrecting accepted child deletes; replay/receipt resolution before live-parent checks.
- Phase 01 contract freeze and Hub upgrade/receipt source deliverables are marked DONE administratively on 2026-10-19, unblocking Phase 02 contract-based development. Phase 02 Room migration and versioned sync integration are marked DONE administratively on 2026-10-19, unblocking Phase 03 hierarchy semantics and main UI. Outstanding review findings (Hub upgrade safety remediation, H1-residual V2 uncertain dispatch visibility, live transition-recovery acceptance, live Mongo staging, and native Android/Linux/Windows acceptance) remain tracked execution prerequisites before affected upgrade operations, staging dry-runs, and Phase 06 rollout.
