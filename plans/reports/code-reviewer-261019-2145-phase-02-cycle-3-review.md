# Code Review — Phase 02 (Room + versioned sync) — Cycle 3

Plan: `plans/261019-1511-parent-child-tasks-deadline-surfaces/phase-02-room-and-sync.md` | Reviewer: Phase02ReviewerCycle3

## Score: 7/10

C1 fully fixed + tested. H1 only partly fixed: V2 never-reconciled in the V2→V3 path; fence is silent and untested. Tests green, files staged.

## Cycle-2 verification
| Item | Result |
|---|---|
| C1 translation keeps V2 temporal (`contract-transition-coordinator.kt:423-455,472-504`) | FIXED. CREATE/UPDATE use `op` snapshot when source V2. Test `testV2ToV3TransitionTranslatesPendingV2IntentsToV3` (CREATE + UPDATE w/ deadline/reminder/token) passes |
| H1a V2 in `reconcileLegacyReceipts` filter (`:282`) | Filter extended, BUT not reachable on READY_V2→V3 (see Critical-H1) |
| H1b legacy ops fenced from V3 push (`outbox-reconciliation.kt:60`) | Present, fail-closed; NO test (no `ready-v3` case in outbox-reconciliation-test) |
| H1c reapply on V3 record | Tested `testReapplyConflictOnV3ChildPreservesParentAndSelectsV3Contract`; passes. Digest asserted only `isNotEmpty()` |
| 371 checks | Independently: jvmTest BUILD SUCCESSFUL (--rerun-tasks); v3 vectors 6/6 + 5/5; rehearsal 8/8; smoke harness PASSED (34/34 per tester; my tail showed PASSED only) |
| `3.json` + `room-migration-v2-to-v3-test.kt` staged | YES (`A `, no unstaged delta) |

## Critical Issues
None new. (C1 closed.)

## Warnings (High)
**H1-residual — V2 UNCERTAIN/dispatched ops strand silently after READY_V2→V3.**
- `executeTransition` V3 shortcut (`contract-transition-coordinator.kt:96-146`) runs `recoverInterruptedDispatches()` then base scan + `translateSafeIntents`, never `reconcileLegacyReceipts`. V2 ops with `UNCERTAIN`/`dispatchHistory`/`consecutiveFailures>0` are excluded from translation (`:392-395`), stay V2, then fenced forever by `getEligibleDispatchBatch:60`. Head blocks row successors (incl. new V3 edits) → data stalls with no conflict/visible state. Plan risk line 67 ("keep user recovery visible and fail closed") only half met: fail-closed yes, visible no.
- The `:282` V2 inclusion only helps the full path (READY_V1/UPGRADE_DETECTED→V3). There the receipt DTO sends v1-only `StrictTodoDataDto` + V2 digest; Hub endpoint is strict-v1-only (`docs/hub-schema-integration.md:110-111`; Phase 1 L29 "v2→v3 receipts are new work"). Any V2 op with temporal data would be INTEGRITY_FAILURE→CONFLICT. Unverified, untested path; claim "V2 receipts reconciled" is not substantiated.
- Fix (pick): in V3 shortcut, after quiesce, mark remaining non-translatable V2 ops `STATE_CONFLICT`/`UNKNOWN_SUPERSEDED` with explicit message ("V2 receipt unsupported; review") so UI surfaces them; or add Hub v2 receipts (Phase 1 scope). Add test: READY_V2 + UNCERTAIN V2 op → V3 transition → op surfaced and not pushed. Add fence test: `ready-v3` + PENDING V2 op → `getEligibleDispatchBatch` empty.

## Warnings (carry-over, unchanged from Cycle 2)
- W1 parent-liveness check not atomic with freeze tx (practically serialized by coordinator mutex).
- W2 last-child-preservation review provenance only a constant; no persisted state (Phase 3 prerequisite).
- W3 `reapplyConflict` field-wise `?:` fallback; no parent-live check.
- W4 still missing tests: `SERVER_DOWNGRADE_FORBIDDEN`, `UNEXPECTED/INVALID_RELATIONSHIP`, `isAccountV2()` on ready-v3; `coordinatorSetPhase(READY_V2)` writes `TRANSITION:ready-v2`.
- W5 downgrade mid-transition (V3_BASE_SCANNING + server v2) rewrites phase.
- W6 `AutomaticParentRejected` in `setTodoCompletedLocally` = Phase 3 semantics.
- W7 plan checkboxes (lines 51-55) still `[ ]`; status line still "Cycle 2 done / H1 open".

## Suggestions
- Strengthen V3 reapply test: `assertEquals(StrictSyncV3Digest.computeDigest(...), newOp.digest)`.
- C1 test only covers non-null temporal; add V2 UPDATE that clears deadline (whole-snapshot null must not resurrect base deadline) and op created under `TRANSITION:*`.
- Extract duplicated V1/V2/V3 digest `when` (store/outbox/coordinator); the CREATE/UPDATE `if (isSourceV2)` ladders (14 fields ×2) could be one snapshot helper.
- Shadowed `durablePhase` at `account-sync-coordinator.kt:172`; inline FQ names → imports.

## Reviewed files
shared: `contract/{task-surface-contracts,todo-models}.kt`; `data/{database-migrations,editor-draft-entity,outbox-entity,room-account-store,scan-staging-entity,server-base-entity,tabbyrush-dao,tabbyrush-database}.kt`; `sync/{account-sync-coordinator,contract-transition-coordinator,hub-sync-client,hub-sync-dtos,outbox-reconciliation,staged-full-scan}.kt`; tests: account-sync-coordinator, contract-transition, outbox-reconciliation, staged-full-scan, room-account-store, room-migration-v2-to-v3; `schemas/.../3.json`; plan phase-02. Focus on contract-transition-coordinator, outbox-reconciliation, account-sync-coordinator diffs.

## Validation
| Command | Result |
|---|---|
| `JAVA_HOME=…/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | BUILD SUCCESSFUL (318 tests per tester; I could not read XML — build/ blocked by hook) |
| `node scripts/strict-v3-vector-runner.mjs` | 6/6 + 5/5, fingerprint verified |
| `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | 8/8 |
| `node scripts/admin-announcements-smoke.mjs --self-test` | PASSED |
| `git status` | 3.json + migration test staged |

Type coverage / line coverage / lint: not run (no tasks configured).

## Recommended Actions
1. Close H1-residual: surface V2 UNCERTAIN/dispatched ops as visible conflict on V2→V3 (or build v2 receipts) + two tests above.
2. Fix/withdraw claim that V2 receipts are reconciled (Hub strict-v1-only).
3. Update plan checkboxes/status.

## Unresolved Questions
- Does Hub plan to support v2→v3 receipts in Phase 6, or is explicit user-review conflict the accepted terminal behavior for V2 uncertain ops?
- Is deferral of W2 (preservation-intent persistence) to Phase 3 accepted?
- Is `AutomaticParentRejected` in Phase 2 intended?
