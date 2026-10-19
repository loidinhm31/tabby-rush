# Code Review — Phase 02 (Room + versioned sync) — Cycle 2

Plan: `plans/261019-1511-parent-child-tasks-deadline-surfaces/phase-02-room-and-sync.md`
Reviewer: Phase02ReviewerCycle2 | Date: 2026-10-19

## Score: 6/10

All 7 Cycle-1 items verified fixed in code; 317 JVM tests + 53 runner checks green. But the fix for item 3 widened V2→V3 translation and introduced a data-loss regression (C1 below). Phase not ready to close.

## Scope
- 21 modified + 4 untracked files (git status): shared contract/data/sync main, common/jvm tests, `schemas/.../3.json`, `room-migration-v2-to-v3-test.kt`.
- Focus: Cycle-1 fix verification + regressions from fixes.

## Cycle-1 verification
| # | Item | Result |
|---|------|--------|
| 1 | child tombstone promotion w/o IMMUTABLE_PARENT_VIOLATION (`staged-full-scan.kt:303,346,381`) | FIXED, test `testChildTombstoneHigherRevisionPreservesParentIdentity` |
| 2 | child DELETE allowed after parent delete (`outbox-reconciliation.kt:61,99`) | FIXED, test `testChildPendingDeleteAllowedWhenParentIsDeleted` |
| 3 | V2→V3 quiesce + v3 base scan + translation (`contract-transition-coordinator.kt:96-146`) | FIXED structurally; translation itself regresses (C1) |
| 4 | reapplyConflict V3 selection (`account-sync-coordinator.kt:433`) | FIXED in code; no direct V3 test |
| 5 | `isAccountV2()` accepts ready-v3 (`room-account-store.kt:809`) | FIXED in code; no direct test |
| 6 | downgrade check (`account-sync-coordinator.kt:152`) | FIXED in code; no test |
| 7 | non-v3 relationship rejection (`staged-full-scan.kt:147`) | FIXED in code; no test |

## Critical Issues
**C1 — V2→V3 (and V1→V2 during TRANSITION) translation drops pending V2 temporal data.**
`translateSafeIntents` filter now includes `CONTRACT_VERSION_V2` (HEAD: V1 only; V1 ops had no temporal fields so base copy was right). For a V2 source op:
- CREATE (`contract-transition-coordinator.kt:423-441`): new op copies no deadline/reminder/scheduleToken fields at all → deadline+reminder silently lost.
- UPDATE (`:459-489`): deadline/reminder/scheduleToken taken from `base`, not `op` → user's unsent deadline edit overwritten.
Violates design-contract L47 ("never changes legacy completed/deadline/reminder values"). Made worse by `saveTodoLocally` now creating V2 ops while status is `TRANSITION:*` (`room-account-store.kt:246`). Test `testV2ToV3Transition…` uses a V2 op with no temporal fields, so it cannot catch this.
Fix: when `op.contractVersion == V2`, copy op's own temporal snapshot (whole-snapshot, incl. nulls); only V1 ops use base temporal. Add test: pending V2 CREATE/UPDATE with deadline+reminder → V3 keeps it.

## High Priority
**H1 — V2 UNCERTAIN/recovered-DISPATCHING ops not reconciled on V2→V3.** `reconcileLegacyReceipts` is only run for V1 and `LegacyReceiptItemDto` carries v1 data only. In the V3 branch `recoverInterruptedDispatches()` makes V2 ops UNCERTAIN, then they stay V2 (translation only covers never-sent) and next push encodes every frozen op with `toV3Dto` (`account-sync-coordinator.kt:262-264`) regardless of `op.contractVersion` → V3 DTO against stored V2 digest → OP_ID_REUSE/digest-mismatch → stranded conflict/uncertain. Plan step 6 / contract L46 require original-version receipts for v1/v2. Either add v2 receipt path (needs Phase 1 hub contract confirmation) or fail-closed explicitly + visible; never re-encode frozen V2 as V3. Needs test.

## Warnings
- W1 Parent-liveness gate not atomic with freeze: check in `prepareAndFreezeBatch` (`:99`) then separate `freezeOutboxAtomic` tx (`tabbyrush-dao.kt:422`); sync round is serialized by `coordinatorMutex` so practical risk low, but plan step 5 requires admission under mutation/freeze lock. Move into the DAO tx or `withMutationLock`.
- W2 Last-child-preservation intent/review provenance (plan req 4, step 8, success criteria) is only a constant `INTENT_LAST_CHILD_PRESERVATION`; nothing persists/reloads review state. `loadAllTodos(includeDeleted)` gives merged projection with tombstones but no committed-vs-draft-vs-submitted provenance API (only raw DAO). Phase 3 prerequisite partially unmet.
- W3 `reapplyConflict` uses field-by-field `?:` fallback (draft→op→base) for nullable fields incl. new `parentTaskId` (`:416-430`); plan key insight says whole-snapshot provenance. Deadline clearing via draft resurrects op/base deadline (pre-existing, now in-phase). No check that parent still live / matches base parent (plan step 7: no mutation of deleted-parent groups); reapplied child UPDATE under deleted parent just stalls pending forever.
- W4 Tests missing for Cycle-1 items 4–7: V3 child reapply (wire + V3 digest), `SERVER_DOWNGRADE_FORBIDDEN`, `UNEXPECTED_RELATIONSHIP`/`INVALID_RELATIONSHIP`, `isAccountV2()` on ready-v3. Tester report attributes #5 to unrelated suites. Also `coordinatorSetPhase(READY_V2)` in contract-transition-test writes `TRANSITION:ready-v2`, not actual `ready-v2`.
- W5 Server downgrade mid-transition: durable phase `V3_BASE_SCANNING`/`SAFE_INTENTS_TRANSLATING` + server reports v2 goes down the v2 branch (`:184-206`) and rewrites phase to V2_BASE_SCANNING; downgrade check only covers READY_V3/READY_V2.
- W6 `setTodoCompletedLocally` now returns `AutomaticParentRejected` for parents with children — Phase 3 group semantics, scope creep into Phase 2; confirm intended.
- W7 Plan todo checkboxes/status still `pending`; untracked `3.json` + migration test must be added to VCS.

## Suggestions
- `account-sync-coordinator.kt:172` shadows outer `durablePhase`; reuse.
- `staged-full-scan.kt:346` `staged.parentTaskId ?: currentBase.parentTaskId` unreachable-fallback after guards; simplify.
- Fully-qualified names inline in staged scan / store (`dev.tabbyrush...StrictSyncV3Contract`) → import.
- Tombstone first seen via scan for a child (no base) stores parent null (data absent on wire) — document; fine for root evidence.
- Three-way V1/V2/V3 digest `when` duplicated in store/outbox/coordinator — extract helper (DRY).

## Positive
- Strict immutable-parent enforcement on higher/equal revision with tombstone carve-out; non-v3 relationship rejected pre-staging; staging cleared on every failure.
- Child DELETE legal after parent deletion; UNCERTAIN replay bypasses liveness gate while PENDING first-dispatch gated; root-of-root (2-level) enforced.
- `MIGRATION_2_3` additive, registered in `ALL`; exported `3.json` has `parentTaskId` on all four tables; populated 2→3 and 1→2→3 tests.
- Downgrade fence and server-version/outbox incompatibility checks fail closed.

## Validation
| Command | Result |
|---|---|
| `JAVA_HOME=…jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` (run by me) | BUILD SUCCESSFUL; 317 PASSED, 0 FAILED |
| `node scripts/strict-v3-vector-runner.mjs` | 6/6 vectors, 5/5 rejections, fingerprint verified |
| `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | 8/8 |
| `node scripts/admin-announcements-smoke.mjs --self-test` | per tester report 34/34 (my tail output blank; not independently confirmed) |
| Total | 317 + 11 + 8 + 34 = 370 per tester; 336 independently re-observed |

Metrics: type coverage n/a; no lint task run; Kotlin warnings not inspected.

## Recommended Actions
1. Fix C1 + add temporal-preserving translation tests (V2 CREATE/UPDATE with deadline/reminder; op created during `TRANSITION:*`).
2. Resolve H1 (receipts for V2 or fail-closed; stop re-encoding frozen V2 as V3).
3. Add tests for Cycle-1 items 4–7.
4. Address W1–W3 or record explicit deferral to Phase 3 with plan edit.
5. Update plan checkboxes after fixes; `git add` untracked files.

## Unresolved Questions
- Does the Phase 1 hub `legacy-receipts` endpoint cover strict-v2-origin ops, or only v1? Determines H1 fix shape.
- Is `AutomaticParentRejected` in `setTodoCompletedLocally` intended in Phase 2?
- Is deferral of preservation-intent review persistence (W2) to Phase 3 accepted?
