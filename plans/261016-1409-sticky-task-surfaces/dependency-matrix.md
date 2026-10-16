# Dependency matrix and execution strategy

Status: Phase 01 **DELIVERED / 100% (24h / 24h); technical acceptance conditional**. Cycle 3 review **9.4/10** and QA **307/307 Gradle tests** are recorded. Terminal conditional handoff is authorized, so Phases 02/03/04 are unblocked and may proceed; strict dependency provenance remains pending, not waived. Overall plan is **in progress / 18.2% (24h of 132h)**; Phases 02–08 remain pending / 0%. Read [contracts](architecture-contracts.md), [exclusive ownership](file-ownership.md), [acceptance matrix](acceptance-matrix.md) before starting a phase. Interface-only files in phase01 are production contracts; every consumer is real production code, no temporary host/store implementations.

## Hard prerequisites

`H` = hard predecessor; `T` = transitively required; `—` = none. Matrix rows consume columns.

| Consumer | 01 | 02 | 03 | 04 | 05 | 06 | 07 | Extra gate | Parallel group |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01 | — | — | — | — | — | — | — | Foundation and Glance candidate delivered; strict artifact provenance held pending the root checksum baseline (not waived) | A foundation |
| 02 | H | — | — | — | — | — | — | Glance selected and host contracts frozen | B leaves |
| 03 | H | — | — | — | — | — | — | Access/selection contracts frozen | B leaves |
| 04 | H | — | — | — | — | — | — | Compact state/action contracts frozen | B leaves |
| 05 | H | H | H | — | — | — | — | Completed real Android leaves, Android SDK/device for native proof | C integrations |
| 06 | H | — | — | H | — | — | — | Completed compact leaf, Windows graphical session for native proof | C integrations |
| 07 | T | T | T | T | H | H | — | Integrated Android + Windows, test accounts/safe sync endpoint | D acceptance/docs |
| 08 preparation | T | — | — | T | — | H | — | Linux machine/session to implement platform-only adapter | E later Linux |
| 08 completion | T | — | — | T | — | H | H | Actual Linux session/native acceptance, any phase01 Linux metadata prerequisite | E later Linux |

## Handoff payloads and failure ownership

| Producer → consumer | Required completed payload | Failure rule |
| --- | --- | --- |
| 01 → 02/03/04 | Frozen contracts, store implementation + behavioral SQLite tests, selected/locked Glance 1.2.0 and reported build evidence; Cycle 3 terminal conditional authorization | Leaves may proceed; keep the 149-checksum/provenance hold explicit and do not claim strict verification or native runtime proof |
| 02 → 05 | Glance renderer/actions/all-ID updater/clear/quiescence + module tests/resources | Registration/navigation delayed to 05 intentionally; no fake Application host |
| 03 → 05 | Native notification/preferences/receipt/permission/settings/time/worker implementation + tests | ActivityResult and registration must wait for integration; do not claim live permission proof in leaf |
| 04 → 06 | Compact UI + atomic presentation preferences + monitor geometry + tests | UI callbacks are real boundary; no separate runtime or main-editor mutation |
| 05/06 → 07 | Integrated sources, owner tests, bounded readiness/drain + native smoke handoffs | 07 does not repair sources/tests: issue finding to original owner and wait for terminal corrected handoff |
| 06/07 → 08 | Complete generic desktop implementation/provider seam + Windows acceptance baseline | Linux-only adapter/evidence; common regressions stay 04/06, shared metadata stays 01 |

## Execution waves

1. **A**: phase01 sole shared/build owner freezes signatures, implements store and establishes compatible toolchain. All changed shared API implementors/callers/doubles are its responsibility. If desktop Linux lock actually changes, same owner resolves it on Linux; never host-spoof.
2. **B**: Phases 02/03/04 may proceed concurrently under the Cycle 3 terminal conditional handoff; paths are disjoint. Android leaves use completed Phase 01 contracts but do not depend on each other or a live Activity. They compile against real contracts; no manifest/composition-root edits and no runtime mocks. Runtime end-to-end and native proof honestly wait for05/06/07; the dependency-provenance hold is not waived.
3. **C**: phase05 starts when02+03 are terminal; phase06 when04 terminal. These integrations can overlap each other and any remaining unrelated leaf. Android composition roots have one owner; desktop main/controller/tray/lifecycle has one owner.
4. **D**: phase07 waits05+06. Main integrator performs one final aggregate verification after all owner changes land; do not run concurrent build/test storms against half-integrated sources. Native Android + Windows acceptance and affected docs are mandatory.
5. **E**: phase08 Linux-only preparation may start after06 with own paths; final verdict waits07 and actual target Linux session. No machine means pending Linux, not missing Android/Windows implementation and not implicit cross-platform success.

## Edit independence is not runtime independence

Leaves have no mutual file writes; 05/06 have disjoint files and platform runtime dependencies. All share phase01 contracts and authoritative persistence semantics. Never claim zero dependencies because files differ. No file passes to a later owner; a late acceptance defect is fixed by its original owner, then final verification is rerun only after the correction lands. No later test phase edits module tests.

## Verification ownership

Implementation owner authors tests with module and hands off future commands/results. A main integrator schedules compile/test checks after complete changes; final integrated verification once per coherent snapshot. Commands in the acceptance matrix are **planned, not run by the planner**. Strict metadata baseline failures, absent signing custody, device/session prerequisites and existing production holds are recorded explicitly rather than bypassed or relabeled successful.

## Unresolved questions

Glance API/build and frozen contract evidence are reported; Phase 02 must still prove supported update-session/quiescence behavior with its real leaf implementation. Strict Glance artifact provenance remains pending against the repository's 149-checksum verification baseline, and must not be represented as verified. Linux target/session and machine availability block only the later native Linux gate unless Phase 01 genuinely needs Linux lock resolution. No unresolved product decision.
