# Code Review — Phase 03 Shared hierarchy semantics + main UI

Score: **5.5/10** — NOT ready to close phase. 2 critical, 7 warnings.

## Critical
1. **Stale/repeat last-child delete corrupts parent completion** (`room-account-store.kt` `deleteTodoLocally` Case 2). `loadTodosLocked()` excludes locally-deleted rows → already-deleted child gives `target == null`, `liveSiblings` empty → treated as "last child" again → `precedingEffectiveCompleted = target?.completed ?: false` → stages new child DELETE + new preserving parent UPDATE with `completed=false`. Reproduced with scratch test (removed): child completed → delete #1 root=true; delete #2 root=false; parent outbox holds 3 `create` ops. Violates contract line 19 (preserve *immediately preceding* effective completion). Fix: early-return when target missing/already deleted; require `target != null` before Case 2; add regression test.
2. **Group delete of unsent rows strands outbox forever** (Case 1). Every group row gets DELETE appended after its pending CREATE (confirmed: `c=[create:PENDING, delete:PENDING]`). Child CREATE is head and `getEligibleDispatchBatch`/`prepareAndFreezeBatch` skip non-DELETE child ops when parent base missing/deleted → child CREATE never dispatches, DELETE queued behind it never dispatches. Pending counts/leave guard never clear. Contract item 8 + Step 7 require local cancel of provably-unsent groups (`hasOnlyPendingCreate` equivalent per row, no frozen/migrated). `cancelUnsentGroupAtomic` was added to DAO but is never called (dead code, unwired). Same strand risk for unsent child under synced root when user deletes group/last child.

## Warnings
1. **Schedule projector not reusing hierarchy evaluator** (`persisted-schedule-projector.kt`): re-implements grouping/aggregation inline (plan Step 8 / contract "one shared evaluator"). Divergence already: grandchild whose parent is a child isn't suppressed; classifications differ. DRY + drift risk. Extract shared pure function over a minimal `(id,parentId,deleted,completed)` view.
2. **Late-child cleanup / unresolved recovery not implemented**: Step 7 durable cleanup absent in sync layer. `ProjectedTaskSnapshot.unresolvedChildren` computed but never rendered by hosts/UI → children with missing parent vanish with no access path (contract line 33, 57 "never silently lose only access path"). Also hosts feed `loadTodos()` (deleted rows excluded), so `deletedRootIds` never populated in prod: confirmed-tombstoned root + late child is misclassified "unresolved" instead of "deleted"; tombstone dominance path only exercised by synthetic `isDeleted=true` test.
3. **Preserving parent UPDATE bypasses coalescing/save path** (`buildPreservationUpdateEntities`): appends a separate op even when parent has pending op (3 stacked creates above), uses reduced mask `COMPLETED|LAST_CHILD_PRESERVATION` for kind CREATE (saveTodoLocally uses TITLE|COMPLETED|…), hardcodes V3, replaces draft wholesale. Route through `saveTodoLocallyLocked` + mask flag, or coalesce.
4. **Perf**: `saveTodoLocallyLocked` and `setCompletedLocally` now full-scan all base/draft/outbox (`loadTodosLocked`) just to test `hasLiveChildren` — O(n) per toggle/save. Plan says indexed lookup; add DAO query by `parentTaskId` (or pass already-loaded snapshot).
5. **TodoList fallback re-projects filtered `items`** when `hierarchicalItems` empty (`todo-list.kt`): aggregates a filtered subset (exactly what plan forbids) and keeps redundant `items` + `hierarchicalItems` in `TodoUiState`. Remove fallback and `items` param; single source.
6. **"+ Subtask" not gated on strict-v3 readiness**; store throws `IllegalArgumentException`, hosts collapse to generic "Action execution failed" and `TodoUiState` lacks `isAccountV3`. Gate/hide button; show specific editor error.
7. **A11y/contract gaps**: child rows give no "subtask of X" semantics; `DeadlinePresentation.contentDescription` unused in UI; `conflict-review.kt` (plan-listed) untouched—preservation/barrier conflict has no explanatory copy. English strings ("Due", "Overdue") embedded in shared contract object vs "platform copy in existing layer".

## Suggestions
- DRY hosts: `applyFilterAndCounts`, delete child count, parent-title lookup duplicated in Android `session-view-model.kt` and desktop `main.kt`; push to shared helper (e.g. projection + `TodoUiState` reducer). Use `TaskHierarchyProjector` import instead of FQN.
- `isReadyV3/isReadyV2/opContractVersion` block copy-pasted in Case 1 and Case 3 (and elsewhere); extract.
- Barrier detection uses `intentMask.contains(...)` substring; elsewhere mask is `split("|")` set — use consistent helper.
- Drop `overdueLabel` (redundant with `isOverdue`), unused `computeCounts`, `GroupedTask.effectiveCompleted` duplication; collapse `filterAndFlatten` ~150 LOC of triplicated item construction into one builder (matching predicate + context flag).
- `Surface` import / stray `remember` import placed after theme import in `todo-list.kt`; trivial style.
- Editor `parentTitle` snapshotted at open can go stale; derive from snapshot at render.
- Tests missing: repeat/stale last-child delete; unsent-group delete cancel; reapply keeps barrier + restart persistence; reverse partial acceptance; production-shape tombstone (deleted rows excluded); store-level rejection of grandchild/reparent via new group paths. Tests that exist are behavioural (good), but cover happy paths only.

## Positives
- Raw vs effective cleanly separated; parent raw preserved on edit; `AutomaticParentRejected` enforced at store boundary (not only disabled UI).
- Group delete is one DAO transaction with distinct revisions; no nested mutex.
- Save-time validation: immutable linkage, 2 levels, same-store parent, v3 gate.
- Compact partition keeps groups intact; counts count real rows once; deadline formatter pure, zone preserved, no date-only anchor leak.
- No security regressions seen: no new network/secret/log surface, account isolation unchanged (mutations stay under `withMutationLock`).

## Plan status
Phase 03 file todos all `[ ]`, status "pending". Items NOT done relative to plan: Step 7 late-child cleanup, unresolved-section UI, Step 11 real UI smoke, conflict-review rendering, evaluator reuse in schedules. Plan file not edited by reviewer.

## Reviewed files
All 18 listed changed/added files (≈880 added lines in tracked diff + 4 new files).

## Validation
- Existing QA report `plans/reports/qa-261019-2350-phase-03-test-suite-validation.md`: 512/512 pass (shared 339, desktop 119, android 43, v3 vectors 11) — not rerun by me.
- Own check: scratch test `JAVA_HOME=…jdk-21 ./gradlew :shared:jvmTest --tests '*ZzReviewScratchTest' --dependency-verification=off --offline` → BUILD SUCCESSFUL, confirmed Critical 1 and 2 evidence. Scratch file deleted.
- Green suite does not cover the two critical cases.

## Unresolved questions
1. Is Phase 03 intended to include late-child cleanup + unresolved UI, or deferred to Phase 4/5? Plan Steps 7/9 say Phase 03.
2. Should unsent-group delete cancel locally (contract item 8) even when parent is already confirmed on server?
