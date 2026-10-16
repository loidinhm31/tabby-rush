# Sticky task surfaces — plan access point

Status: **Phases 01–06 delivered / 100% of planned implementation scope; 112h / 132h credited (84.8%)**. Cycle 2 review: **8.8/10 Conditional Pass**, approved for downstream acceptance, 0 critical issues. Wave D / Phase 07 is unblocked and ready to record integrated Android/Windows status; Phase 08 remains pending an actual Linux target/session. [Full overview](plan.md). Total estimated effort **132h** across eight phases.

| Phase | Scope | Status / progress | Group | Effort | Plan |
| --- | --- | --- | --- | --- | --- |
| 01 | Shared contracts/store/dependency foundation | delivered / 100%; root checksum + Glance provenance holds | A | 24h | [detail](phase-01-shared-foundation.md) |
| 02 | Glance widget leaf | delivered / 100%; implementation/tests verified; native acceptance pending | B | 16h | [detail](phase-02-android-widget.md) |
| 03 | Deadline notification/settings leaf | delivered / 100%; implementation/tests verified; native acceptance pending | B | 20h | [detail](phase-03-android-notification.md) |
| 04 | Compact desktop/geometry/preferences leaf | delivered / 100%; implementation/tests verified; native acceptance pending | B | 12h | [detail](phase-04-desktop-compact.md) |
| 05 | Android sole-owner integration | delivered / 100%; Cycle 2 safety findings remain open | C | 24h | [detail](phase-05-android-integration.md) |
| 06 | Desktop sole-owner integration | delivered / 100%; Windows acceptance pending | C | 16h | [detail](phase-06-desktop-integration.md) |
| 07 | Integrated Android/Windows acceptance/docs | unblocked / ready to record integrated status; pending / 0% credited | D | 12h | [detail](phase-07-acceptance-docs.md) |
| 08 | Later Linux-only adaptation/session acceptance | pending / 0%; actual Linux target/session required | E | 8h | [detail](phase-08-linux-acceptance.md) |

## Run order
1. Phases 01–06 delivered: A, then B leaves, then C integrations.
2. Wave D / Phase 07 is unblocked after 05+06; perform integrated Android/Windows native acceptance and docs, then record only evidenced outcomes.
3. Wave E / Phase 08 remains pending actual Linux target/session; final acceptance follows Phase 07. Windows does not substitute for Linux.

## Required supporting artifacts
- [Frozen architecture contracts](architecture-contracts.md): atomic completion, readiness, account/publication fencing, teardown, notification decisions, desktop isolation/lifetime.
- [Exact exclusive file ownership](file-ownership.md): tests/build metadata/roots/wipe/auditor/docs included; no duplicate owners.
- [Dependency matrix](dependency-matrix.md): real hard prerequisites and handoff gates.
- [Acceptance matrix](acceptance-matrix.md): brainstorm1–12, edge cases, native evidence and future platform commands.
- Inputs: [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md), [analysis](reports/01-codebase-analysis.md), [brief](reports/02-planner-brief.md), [Android](research/researcher-01-android-surfaces.md), [desktop](research/researcher-02-desktop-surfaces.md).

## Guardrails
No runtime scaffolds/mocks, schema/backend changes, broad checksum repair, auto-start or reminder-toast expansion. Module owners retain tests/fixes; acceptance owner does not edit source. Existing production release holds stay held. Cycle 2 reports 363/363 Gradle tests passing, 12/12 Node security tests, clean diagnostic audit, 18/18 Hub fixture smoke, and 8/8 strict-v2 rehearsal; these are attributed results, not new verification here. Strict dependency and native acceptance remain open.

## Unresolved questions
No product blockers. Exact Glance/toolchain/session quiescence implementation proof; later Linux target/session and machine. Conservative notification, dismissal and geometry decisions are frozen in contracts, not deferred.
