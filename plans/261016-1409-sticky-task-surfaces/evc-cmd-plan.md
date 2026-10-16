# Sticky task surfaces — plan access point

Status: **pending / 0%**. Planning artifacts only; implementation not authorized. [Full overview](plan.md). Total estimated effort **132h**; eight phases. Existing architecture §7 is intended/pending, not delivered.

| Phase | Scope | Status / progress | Group | Effort | Plan |
| --- | --- | --- | --- | --- | --- |
| 01 | Shared contracts/store/dependency foundation | pending / 0% | A | 24h | [detail](phase-01-shared-foundation.md) |
| 02 | Glance widget leaf | pending / 0% | B | 16h | [detail](phase-02-android-widget.md) |
| 03 | Deadline notification/settings leaf | pending / 0% | B | 20h | [detail](phase-03-android-notification.md) |
| 04 | Compact desktop/geometry/preferences leaf | pending / 0% | B | 12h | [detail](phase-04-desktop-compact.md) |
| 05 | Android sole-owner integration | pending / 0% | C | 24h | [detail](phase-05-android-integration.md) |
| 06 | Desktop sole-owner integration | pending / 0% | C | 16h | [detail](phase-06-desktop-integration.md) |
| 07 | Integrated Android/Windows acceptance/docs | pending / 0% | D | 12h | [detail](phase-07-acceptance-docs.md) |
| 08 | Later Linux-only adaptation/session acceptance | pending / 0% | E | 8h | [detail](phase-08-linux-acceptance.md) |

## Run order
1. Complete01 and freeze contracts/build compatibility.
2. Execute02/03/04 concurrently against01's real interfaces; no shared root edits.
3. Execute05 after02+03;06 after04. Integrations may overlap; each consumes completed real leaves.
4. Execute07 after05+06; final aggregate verification and actual Android/Windows surfaces.
5. Linux-only preparation after06;08 completion after07 on real Linux machine/session. Missing machine keeps08 pending, never certified by Windows.

## Required supporting artifacts
- [Frozen architecture contracts](architecture-contracts.md): atomic completion, readiness, account/publication fencing, teardown, notification decisions, desktop isolation/lifetime.
- [Exact exclusive file ownership](file-ownership.md): tests/build metadata/roots/wipe/auditor/docs included; no duplicate owners.
- [Dependency matrix](dependency-matrix.md): real hard prerequisites and handoff gates.
- [Acceptance matrix](acceptance-matrix.md): brainstorm1–12, edge cases, native evidence and future platform commands.
- Inputs: [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md), [analysis](reports/01-codebase-analysis.md), [brief](reports/02-planner-brief.md), [Android](research/researcher-01-android-surfaces.md), [desktop](research/researcher-02-desktop-surfaces.md).

## Guardrails
No runtime scaffolds/mocks, schema/backend changes, broad checksum repair, auto-start or reminder-toast expansion. Module owners retain tests/fixes; acceptance owner does not edit source. Existing production release holds stay held. All commands are future; planner ran no build/test/lint/formatter or UI smoke. Explicit directory required because session activation could not persist.

## Unresolved questions
No product blockers. Exact Glance/toolchain/session quiescence implementation proof; later Linux target/session and machine. Conservative notification, dismissal and geometry decisions are frozen in contracts, not deferred.
