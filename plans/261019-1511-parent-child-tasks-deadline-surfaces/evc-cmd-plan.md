---
title: "Parent/child tasks and compact deadline display"
description: "Add two-level task groups with automatic parent completion and deadline dates in Android widgets and desktop sticky mode."
status: in-progress
priority: P2
effort: 120h
branch: main
tags: [feature, frontend, backend, database, api]
created: 2026-10-19
---

# Parent/child tasks and compact deadlines

## Scope and confirmed decisions
- Phase 01 contract freeze and Hub upgrade/receipt source deliverables completed and marked DONE on 2026-10-19 (24h credited). Phase 02 Room migration and versioned sync integration completed and marked DONE administratively on 2026-10-19 (24h credited; technical acceptance conditional, H1-residual open). Client UI implementation (Phases 03–05) and integrated rollout (Phase 06) remain pending.
- Exactly two levels: roots and children. Existing tasks remain top-level, standalone or parent-capable; new children may be created under them. No moving/reparenting existing tasks.
- All live children complete → parent automatically complete; reopening/adding an incomplete child → parent active. Parent with children has no manual/group toggle.
- Deleting a parent confirms and deletes the group. Locally observed last-child deletion preserves immediately preceding effective completion and restores manual completion with that resultant raw value. Cross-device guarantee: local atomicity + conflict review, not globally ordered group mutations.
- Main app, Android widget, and Linux/Windows sticky mode group children under parents. Each task keeps its own deadline; no inheritance.
- Deadline rows show absolute date and optional time, not title-only or merely “Overdue.”
- Child creation is main-app-only; widget/sticky Add creates roots even while viewing a group.
- Main Active/Completed filters show matching rows plus parent context, not nonmatching siblings. Compact surfaces retain whole groups, including completed children in mixed Active groups; aggregate before filtering.

## Design and evidence
- [Behavior and consistency contract](./design-contract.md) — detailed authority for confirmed interview choices and Advisor consistency refinements; strict-v3 contract freeze completed in Phase 01.
- [Architecture baseline](../../docs/system-architecture.md#9-parentchild-tasks--compact-deadlines--strict-v3-architecture-phase-01-implemented).
- [Hierarchy/storage/sync research](./research/hierarchy-sync.md).
- [Main UI/widget/sticky research](./research/surface-ui.md).
- This authorized revision aligns the existing hard-workflow plan in place. Planned effort totals 120h; Phase 01 (24h), Phase 02 (24h), Phase 03 (24h), Phase 04 (12h), and Phase 05 (12h) completed administrative delivery with 96h credited (80.0% planned-effort roll-up); Phase 06 remains pending.

## Phases
| # | Phase | Depends on | Status / progress |
|---|---|---|---|
| 1 | [Freeze v3 contract and implement Hub upgrade/receipts](./phase-01-contract-and-hub.md) | None | DONE (2026-10-19) / 100% (24h credited) |
| 2 | [Migrate Room and integrate versioned sync](./phase-02-room-and-sync.md) | 1 contract freeze | DONE (2026-10-19) / 100% (24h credited) |
| 3 | [Shared hierarchy semantics and main UI](./phase-03-hierarchy-and-main-ui.md) | 2 | DONE (2026-10-20) / 100% (24h credited) |
| 4 | [Android grouped widget and deadline labels](./phase-04-android-widget.md) | 3 evaluator/admission/deadline/schedule contracts | DONE (2026-10-20) / 100% (12h credited) |
| 5 | [Linux/Windows grouped sticky mode and deadlines](./phase-05-desktop-sticky.md) | 3 evaluator/admission/deadline/schedule contracts | DONE (2026-10-20) / 100% (12h credited) |
| 6 | [Cross-device acceptance, rollout, and documentation](./phase-06-acceptance-and-rollout.md) | 1–5 | Pending / 0% |

Phases 4 and 5 can run independently after shared interfaces settle. One integration owner owns shared contracts/store/sync; platform owners do not edit shared files concurrently.

## Consequential constraints
- Strict-v1/v2 schemas, fingerprints and digest bytes are immutable: hierarchy requires strict-v3 and matching sibling Glean Hub work. Room 2→3 migrates existing tasks as roots; no local-only hierarchy fallback.
- Local cascade staging is atomic; remote CAS/acks remain per-row. Confirmed parent tombstones suppress late children; missing parents without tombstones are unresolved. Root-delete discard removes local suppression only, never resurrecting accepted child deletes.
- Effective completion drives UI, counts and committed scheduling; unsubmitted editor drafts never schedule/cancel. Last-child capture is an explicit stored-parent transition, not a recurring aggregate writer.
- Disjoint non-last child deletions may leave authoritative raw completion once childless without any parent UPDATE or synthetic conflict. Unresolved preservation-intent scheduling suppression is client-local/restart-durable, retained across reapply while its new intent is unresolved, and not a rule for every pending parent edit. Either row may acknowledge first; child-delete discard never rolls back an accepted preserving parent UPDATE.
- Exact replay/receipt matching precedes current live-parent validation; first child admission occurs before initial freeze/dispatch, but frozen uncertain replay bypasses renewed parent-liveness gating. Preserve authorization/integrity checks and original bytes.
- v1/v2 frozen operations require original-version receipts before safe v3 translation; unknown delivery remains reviewable. No post-publication downgrade.
- Active schema artifact remains v1 until an explicit coordinated cutover; deployment state is not inferred from source.
- No reparenting, grandchildren, drag/drop, custom sibling order, inherited deadlines, new compact-surface editing, or unrelated announcement/lifecycle repairs.

## Delivery gates
- Populated Room 1→2→3 and 2→3 reopen proof; Kotlin/Rust/Node v3 parity and unchanged legacy vectors.
- Offline/partial-ack/restart/conflict convergence; concurrent late-child versus parent-delete proof; reminders honor group completion/deletion.
- Actual Android launcher widget and Linux **and Windows** sticky interactions/screenshots; narrow widths, 200% text, date-only/timed deadlines, stale-account rejection.
- Live Hub upgrade/read-back and legacy-client fencing on staging. Model tests or Linux compilation do not establish native Windows acceptance.
- Keep inherited G2/G3/G5/G6/G7, checksum, and Glance-provenance holds separate; do not close them by attribution.

## Session activation
Activation script was invoked; `EVCRATE_SESSION_ID` is unset, so active-plan state could not persist. Re-activate this directory in a session with the variable available; plan files remain usable directly.

## Unresolved questions / prerequisites
- No unresolved product questions. Phase 1 must calculate/freeze v3 bytes, fingerprint, receipt behavior, and upgrade checkpoints; these are deliverables, not assumed capabilities.
- Staging Hub/Mongo access, operator authorization, and interactive Android/Linux/Windows testbeds are acceptance prerequisites; availability not verified in this planning run.
