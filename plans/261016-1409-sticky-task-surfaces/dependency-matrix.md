# Dependency matrix and execution strategy

Status: Waves A–C (Phases 01–06) **delivered / 100% of implementation scope (112h / 132h; 84.8%)**. Cycle 2 review **8.8/10 Conditional Pass**, with zero critical issues and downstream acceptance authorized. Wave D / Phase 07 is **unblocked and ready to record integrated Android/Windows status**, not complete. Wave E / Phase 08 remains pending an actual Linux target/session. The 149-checksum root Gradle verification baseline and Glance 1.2.0 artifact-provenance hold are distinct and neither is waived. Cycle 2 also leaves Android drain-timeout and Glance cleanup safety findings open. Read [contracts](architecture-contracts.md), [exclusive ownership](file-ownership.md), [acceptance matrix](acceptance-matrix.md) before starting a phase. All consumers are real production code; no temporary host/store implementations.

## Hard prerequisites

`H` = hard predecessor; `T` = transitively required; `—` = none. Matrix rows consume columns.

| Consumer | 01 | 02 | 03 | 04 | 05 | 06 | 07 | Extra gate | Parallel group |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01 | — | — | — | — | — | — | — | Foundation delivered; 149 unmatched root classpath checksums and Glance artifact provenance remain separate unwaived holds | A foundation complete |
| 02 | H | — | — | — | — | — | — | Delivered/verified; real launcher/session acceptance and Glance provenance remain pending | B leaves complete |
| 03 | H | — | — | — | — | — | — | Delivered/verified; real shade, permission, dismissal acceptance remains pending | B leaves complete |
| 04 | H | — | — | — | — | — | — | Delivered/verified; native Windows geometry/window acceptance remains pending | B leaves complete |
| 05 | H | H | H | — | — | — | — | Delivered/verified; Phase 07 native acceptance open; drain-timeout fail-closed finding remains open | C integrations complete |
| 06 | H | — | — | H | — | — | — | Delivered/verified; Windows native session acceptance remains pending Phase 07 | C integrations complete |
| 07 | T | T | T | T | H | H | — | Hard predecessors delivered; D unblocked/ready to record integrated Android + Windows acceptance/docs; native evidence and open safety findings remain | D ready |
| 08 preparation | T | — | — | T | — | H | — | Preparation remains held pending actual Linux target/session | E Linux gate |
| 08 completion | T | — | — | T | — | H | H | Actual Linux target/session and native acceptance required; no Windows substitute | E Linux gate |

## Handoff payloads and failure ownership

| Producer → consumer | Required completed payload | Failure rule |
| --- | --- | --- |
| 01 → 02/03/04 | Frozen contracts, store implementation + behavioral SQLite tests, selected/locked Glance 1.2.0, and build evidence; leaf implementations delivered | Keep the 149-checksum baseline and Glance artifact-provenance holds explicit; no strict verification or native proof inferred |
| 02 → 05 | Glance renderer/actions/all-ID updater/clear/quiescence + module tests/resources delivered | Native launcher/session proof and cleanup fail-closed finding remain open; no fake Application host |
| 03 → 05 | Notification/preferences/receipt/permission/settings/time/worker implementation + tests delivered | Native shade, permission and dismissal proof remain Phase 07 |
| 04 → 06 | Compact UI + atomic presentation preferences + monitor geometry + tests delivered | Native geometry/window proof remains Phase 07/08 |
| 05/06 → 07 | Integrated sources and owner tests/security checks delivered; Cycle 2 reviewed, 8.8/10 Conditional Pass | D is unblocked; acceptance findings route to original source owner, not Phase 07 production edits |
| 06/07 → 08 | Complete generic desktop implementation/provider seam delivered; Windows acceptance baseline is Phase 07 | Linux-only adapter/evidence requires actual Linux target/session; common regressions stay with original owners |

## Execution waves

1. **A — delivered**: Phase 01 froze signatures, implemented the store, and established the dependency/build foundation. Root checksum and Glance artifact-provenance holds remain separate and unwaived.
2. **B — delivered**: Phases 02/03/04 shipped real widget, notification/settings, and compact desktop leaves against frozen contracts; tests are included in Cycle 2's reported passing suite. Native launcher/shade/window proof remains Phase 07.
3. **C — delivered**: Phase 05 consumed 02+03 and Phase 06 consumed 04; both integrations are delivered and verified. Cycle 2 identifies open Android drain-timeout and Glance cleanup safety findings; fixes remain with the original Phase 05/02 owners.
4. **D — unblocked / ready**: Phase 07 can record integrated Android/Windows acceptance and shared docs after 05+06. Do not mark complete without native evidence and disposition of the open safety findings.
5. **E — held**: Phase 08 Linux-only work requires the actual target/session; final verdict also depends on Phase 07. No machine/session means pending Linux, not implicit cross-platform success.

## Edit independence is not runtime independence

Leaves have no mutual file writes; 05/06 have disjoint files and platform runtime dependencies. All share phase01 contracts and authoritative persistence semantics. Never claim zero dependencies because files differ. No file passes to a later owner; a late acceptance defect is fixed by its original owner, then final verification is rerun only after the correction lands. No later test phase edits module tests.

## Verification ownership

Implementation owner authors tests with module and hands off future commands/results. A main integrator schedules compile/test checks after complete changes; final integrated verification once per coherent snapshot. Commands in the acceptance matrix are **planned, not run by the planner**. Strict metadata baseline failures, absent signing custody, device/session prerequisites and existing production holds are recorded explicitly rather than bypassed or relabeled successful.

## Unresolved questions

Glance implementation and test evidence are delivered, but supported Glance session behavior and artifact provenance remain unwaived. The root Gradle verification baseline has 149 unmatched classpath checksums; it is distinct from the unverified Glance artifact provenance. Phase 07 still owns Android/Windows native acceptance and must route the two Cycle 2 safety findings to their original owners. Linux target/session and machine availability block the later Linux gate. No unresolved product decision.
