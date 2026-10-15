# Execution map and ownership

[Parent plan](plan.md). Status: Phase 01 complete (100%, Gate G1 passed); Phase 02 logic/contracts DELIVERED (Gate G2 physical delivery BLOCKED); Phase 03 DELIVERED (Contracts/Logic/Tests; technical acceptance blocked by 3 code findings and unexecuted Gate G3 live cluster tests; Code Review: ADMISSION ON HOLD 8.0/10); Phase 04 complete (100%); Phase 05 complete (100%); Phase 06 complete (100%, Gate G4 passed); Phase 07 complete (100%, Gate G5 passed); Phase 08 DELIVERED / DONE (100%, Gate G2 physical pending); Phase 09 DELIVERED / DONE (100%, Gate G2 physical pending); Phase 10 complete (100%); Phase 11 DELIVERED / DONE (100% planned implementation effort, 20h credited, 394 reported passes, 0 failures, 1 ignored live Mongo gate across 395 cases/targets, code review 8.6/10 approved; Gate G6 end-to-end platform acceptance formally held pending physical testbeds). Downstream Phase 12 runbook authoring unblocked; production cutover remains blocked. Read [phase01](phase-01-freeze-contract-and-transition-design.md) for contract details; [acceptance matrix](acceptance-matrix.md) for observable outcomes.

## True implementation dependencies
```mermaid
flowchart TD
  P01["01 Contract and transition - G1"] --> P03["03 Hub migration - G3"]
  P01 --> P05["05 Shared model and Room"]
  P02["02 Native and DST proof - G2"] --> P08["08 Android delivery"]
  P02 --> P09["09 Desktop delivery"]
  P03 --> P04["04 Admin UI and script"]
  P03 --> P06["06 Client transition - G4"]
  P05 --> P06
  P05 --> P07["07 Scheduling and fences - G5"]
  P06 --> P07
  P07 --> P08
  P07 --> P09
  P06 --> P10["10 Shared UI and settings"]
  P08 --> P10
  P09 --> P10
  P04 --> P11["11 Integrated acceptance - G6"]
  P10 --> P11
  P11 --> P12["12 Cutover and release - G7"]
```

Phase 07 shared scheduling, persisted projection, occurrence ledger, delivery gate, and fail-closed leave protocol are complete (Gate G5 passed). Phase 10 Compose UI delivered. Phases 01, 04, 05, 06, 07, and 10 complete; Phase 02 logic/contracts delivered (Gate G2 physical delivery BLOCKED); Phase 03 contracts/logic delivered (Gate G3 physical cluster execution ON HOLD pending live replica set testbed); Phase 08 Android and Phase 09 desktop adapters delivered (Gate G2 physical execution held); Phase 11 adversarial integration delivered with 394 reported passes, 0 failures, 1 ignored live Mongo gate across 395 cases/targets (Gate G6 end-to-end platform acceptance held). Downstream Phase 12 cutover runbook authoring is unblocked.

## Safety gates
- **G1 / phase01:** freeze schema/version/digest/null/temporal/API/legacy outcome and maintenance-fence invariants. Reviewed, not guessed source facts.
- **G2 / phase02:** actual native proof on all three target runtimes; Windows installed AUMID/binding and strict DST resolver selected. Missing platform blocks gate.
- **G3 / phase03:** live two-instance fence/crash/publish-last/data-preservation/legacy receipt proof. Backend contracts, logic, unit/route tests, and mock smoke delivered (2026-10-15); technical acceptance remains formally ON HOLD blocked by 3 open code findings and unexecuted live multi-instance MongoDB cluster tests before production admission.
- **G4 / phase06:** populated offline old-client transition; uncertain receipts and safe changed-field rebasing, original digest preservation.
- **G5 / phase07:** draft-free persisted projection, grace/identity/ledger and delivery-vs-mutation/wipe races. Exactly-once not claimed. **PASSED** (2026-10-15): 270/270 automated tests PASS across 6 suites, code review 9.2/10 approved; delivers shared prerequisites for downstream native adapter phases 08 (Android Exact Alarm / WorkManager) and 09 (Desktop Linux D-Bus / Windows Toast), while native proof and delivery remain gated by G2.
- **G6 / phase11:** AC01–AC12 with separate native/device/browser/CLI runtime evidence, final security/build/dependency checks. **AUTOMATED SUBSET PASSED / END-TO-END ACCEPTANCE HELD** (2026-10-16): 394 reported passes, 0 failures, 1 ignored live Mongo gate across 395 cases/targets, code review 8.6/10 approved; physical Android Doze/reboot, Linux desktop notification bubble, Windows COM/WinRT toast bridge implementation, and multi-node MongoDB replica set live migration remain held pending hardware testbeds and backend code remediations.
- **G7 / phase12:** rehearsed and explicitly authorized operator cutover/readback and signed/platform release acceptance; docs reflect real state.

## Independent work and integration ownership
- Start 01 and 02 independently. Once G1 passed, 03 backend and 05 storage can proceed independently using frozen fixture/digest/API contracts.
- Phase 03 contracts, admission logic, and tests are delivered, unblocking Phase 04 operator tooling (admin UI and script) and Phase 06 client transition planning on contracts (Phase 06 implementation remains dependent on Phase 05); physical production deployment remains gated on Gate G3 live cluster execution and code remediations.
- With Gate G5 passed (shared prerequisites delivered; native proof and runtime delivery remain gated by G2), Phase 08 Android and Phase 09 desktop adapters can proceed independently with adapter implementation under their native testbed prerequisites; shared scheduler/model stay under integration owner. Linux and Windows target adapters can run independently with one owner for desktop main.kt/build/package files.
- Shared `contract/`, `data/`, `sync/` files need one integration owner across05/06/07; sequence commits or disjoint file ownership, never concurrent conflicting edits.
- Phase10 owns shared Compose; Android session-view-model.kt and desktop main.kt actions need coordinated handoff from platform owners, not competing replacements.
Phase 11 QA executed automated regressions and packaging verification; full platform and cluster acceptance remain held on physical testbeds. Phase 12 cutover runbook drafting proceeds while cutover execution and Gate G7 remain blocked until Gates G2, G3, and G6 are cleared. Missing evidence is blocked, not waived implicitly.

## Per-phase execution discipline
Read context+requirements+related files; honor checked invariants; use unchecked Todo list. Freeze exported symbol contract/references first. Implement assigned behavior only; update broken behavior tests, keep deterministic plausible-regression tests. Run actual smoke before marking complete. Update docs/changelog after permanent implementation; remove throwaway proof scaffolds. Never perform production upgrade/account destructive wipe just because a plan checkbox exists.

## Unresolved questions
Execution resources/topology/binding proofs listed in parent. No extra parallel execution is requested by this planning deliverable; graph documents safe future ordering.
