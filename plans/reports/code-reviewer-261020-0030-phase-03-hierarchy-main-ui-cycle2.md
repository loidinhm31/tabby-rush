# Code Review Cycle 2 — Phase 03 Shared hierarchy semantics + main UI

Score: **7/10**. 0 new critical. Both cycle-1 criticals fixed. Not ready to close phase: Step 7 late-child cleanup, unresolved-section UI, conflict-review copy, real-UI smoke still open; 2 new high warnings.

## Cycle-1 fix verification
| Item | Status | Evidence |
|---|---|---|
| C1 repeated last-child delete | FIXED | `room-account-store.kt:726` early-return `target == null \|\| target.isDeleted`; `loadTodosLocked()` hides locally-deleted rows so repeat → null → no-op. Test `testRepeatedLastChildDeleteDoesNotCorruptParent` asserts draft stays true + op count unchanged. |
| C2 unsent group strand | FIXED for fully-unsent group | `:734-748` all rows (base null/deleted, all ops PENDING+CREATE, no checkpoint) → `dao.cancelUnsentGroupAtomic` (one @Transaction, wired). Test `testUnsentGroupCancelledLocallyWithoutStrandingOps`. Matches contract item 8. Residual → W1 below. |
| W1 schedule projector DRY | FIXED | `persisted-schedule-projector.kt:225` delegates to `TaskHierarchyProjector.projectHierarchy`; items missing from projection → deleted/suppressed (covers unresolved + tombstone-dominated). |
| W3 preservation mask | PARTIAL | mask now includes TITLE/COMPLETED/(DEADLINE/REMINDER) for CREATE, `COMPLETED\|LAST_CHILD_PRESERVATION` for UPDATE. Coalescing bypass + hard-coded V3 remain (W2). |
| W6/W7 +Subtask gate / a11y | FIXED | `todo-list.kt:116` gated by `isAccountV3 && depth==0`; hosts set `isAccountV3` from store; child checkbox + deadline semantics present. |
| W2 late-child cleanup / unresolved UI | NOT FIXED | `unresolvedChildren` still computed, never rendered; no late-child cleanup in sync layer. |
| W4 O(n) scans | NOT FIXED | `saveTodoLocallyLocked:309`, `setCompletedLocally:512`, `deleteTodoLocally:724` each `loadTodosLocked()` (3 full-table reads) per mutation. |
| W5 TodoList fallback | NOT FIXED | `todo-list.kt:66-72` re-projects filtered `items` if `hierarchicalItems` empty; `items` + `hierarchicalItems` both in `TodoUiState`. Dead in practice (hosts feed both from one projection → both empty together) but is the exact filtered-subset aggregation plan forbids. |

## Critical issues
None.

## Warnings
1. **[High] Mixed group (synced root + unsent child) delete can strand ops.** `deleteTodoLocally` Case 1 only cancels when *every* row is unsent. Root synced + child pending CREATE → child gets `[create, delete]`, root gets DELETE. Scratch repro (removed): after root DELETE accepted, `getEligibleDispatchBatch` skips child CREATE (`outbox-reconciliation.kt:65-69`, parent base deleted) → child `[create:PENDING, delete:PENDING]` stuck forever; pending count/leave guard never clear. Same class as cycle-1 C2. Even without the race, server may reject child CREATE after root delete → spurious conflict for a task user deleted. Fix: cancel provably-unsent *leaf* rows individually (no remote descendants possible; consistent with contract 8 since rule only forbids shortcut when remote descendants possible) and enqueue DELETE only for rows with base/non-create ops. Add regression test (synced root + unsent child, delete root, accept root delete, assert child ops empty).
2. **[High] Preservation UPDATE bypasses coalescing/save path** (`buildPreservationUpdateEntities`). Scratch repro: unsent parent + unsent child, delete child → parent ops `[create, create+LAST_CHILD_PRESERVATION]`, child `[create, delete]`; whole child round-trip + extra parent write + permanent scheduling barrier for a child never sent. Synced parent with pending title UPDATE → second stacked UPDATE. Expected revision is rebased at freeze (`outbox-reconciliation.kt:111-127`) so no self-CAS conflict, but wire noise + barrier. Conversely, a later manual root edit/toggle *coalesces into* the pending preservation op (`canCoalesce`) and the fresh mask drops `LAST_CHILD_PRESERVATION` → barrier silently disappears while op still unresolved (contract: barrier until accepted/discarded). Fix: route through `saveTodoLocallyLocked` with a preservation flag; preserve flag in mask on coalesce; skip preservation + cancel child locally when child is provably unsent (W1 fix covers).
3. **[Medium] Unresolved/late-child recovery absent (plan Step 7/9, contract 7).** `unresolvedChildren` has no consumer; children whose parent is missing vanish with no access path. Hosts feed `loadTodos()` (deleted rows excluded) so `deletedRootIds` is never populated in production → tombstoned-root late child classified "unresolved" not "deleted"; no ordinary per-row DELETE cleanup. Phase 03 acceptance "late children do not reappear or schedule" only satisfied via invisibility. Needs either implementation or explicit plan deferral (see Q1).
4. **[Medium] `conflict-review.kt` untouched** though plan-listed; preservation/barrier conflicts and partial group outcomes have no explanatory copy (contract 9: "Reapply/discard must explain partial child outcomes").
5. **[Medium] O(n) per-mutation full scans** (see W4 above). Add DAO query by `parentTaskId` or pass snapshot.
6. **[Low-Med] `TodoUiState.isAccountV3` defaults `true`** → "+ Subtask" visible on v2 account until first `reloadTodos`. Default should be `false`. Also `isAccountV3()` ORs `defaultContractVersion` but `isAccountV2()` does not (inconsistent).

## Suggestions
- Remove `TodoList` fallback + `items` param; single source `hierarchicalItems`; drop `items` from `TodoUiState` or derive.
- DRY hosts: `applyFilterAndCounts`, delete-child-count, parent-title lookup duplicated in Android `session-view-model.kt` / desktop `main.kt` → shared reducer/helper; import `TaskHierarchyProjector` instead of FQN.
- Extract `isReadyV3/isReadyV2/opContractVersion` block (copy-pasted in Case 1, Case 3, save path). Case 2 hard-codes V3.
- `target?.completed ?: false` at `:794` is dead-nullable after early return; use `target.completed`.
- Barrier detection uses `intentMask?.contains(...)` substring; elsewhere `split("|")` set — use one helper.
- Drop redundant `overdueLabel` (== isOverdue) and unused `computeCounts`; English literals ("Due", "Overdue", "Parent context", "+ Subtask") live in shared code — plan said "corresponding Compose strings".
- Editor `parentTitle` snapshotted at open; derive from snapshot at render.
- Tests worth adding: reapply retains barrier + restart persistence; coalesced manual edit after preservation; reverse partial acceptance; mixed-group delete (W1); production-shape tombstone (deleted rows excluded from input).

## Positives
- Raw vs effective cleanly separated; edit while grouped preserves raw completed (test).
- Group delete + last-child preservation each one DAO `@Transaction`; distinct revisions; no nested mutex.
- Save-time validation of immutable linkage / 2 levels / v3 readiness; `AutomaticParentRejected` enforced at store boundary.
- Schedule projector now single-sourced through hierarchy evaluator; barrier derived from persisted outbox so it survives restart; reapply carries `conflictOp.intentMask`.
- Security: no new network/log/secret surface; all mutations under `withMutationLock`; parent validated against same per-account store; account isolation unchanged.
- Counts count unique real tasks; compact partition keeps groups whole; deadline formatter pure, zone preserved, no date-only anchor leak.

## Plan/contract consistency
- Done: Steps 1-6, 8 (evaluator reuse, barrier), 10; most of 9.
- Open: Step 7 (late-child cleanup, reversible/tombstone distinction in production data path), Step 9 unresolved section + conflict rendering, Step 11 real UI smoke (not executed; no evidence).
- Plan file updated: Overview status, todo list annotated (`[x]` evaluator; `[~]` two items; others open).

## Reviewed files
`shared/.../data/{room-account-store,tabbyrush-dao}.kt`, `contract/{task-hierarchy-projector,task-deadline-presentation,persisted-schedule-projector,persisted-task-schedule,task-surface-projector,todo-action,todo-ui-state}.kt`, `ui/{todo-list,todo-editor,tabbyrush-app}.kt`, `sync/outbox-reconciliation.kt` (dispatch gating), `sync/account-sync-coordinator.kt` (reapply/discard), `androidApp/.../session-view-model.kt`, `desktopApp/.../main.kt`, `room-account-store-test.kt` (group tests), phase-03 plan + design-contract.

## Validation
- `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --tests '*RoomAccountStoreTest' --tests '*TaskHierarchyProjectorTest' --tests '*PersistedScheduleProjection*' --dependency-verification=off --offline --rerun-tasks` → BUILD SUCCESSFUL, 16 tasks executed, no FAILED.
- `ANDROID_HOME=/home/loidinh/Android/Sdk JAVA_HOME=... ./gradlew :shared:jvmTest :androidApp:testDebugUnitTest --dependency-verification=off --offline` → BUILD SUCCESSFUL (up-to-date/cache; full counts 515 pass per main-agent claim, not independently re-counted; build dir reads blocked by ignore rules). Desktop tests not rerun (needs Hub env/API key).
- Scratch test (3 scenarios: mixed group, unsent parent+child last-child delete, synced parent pending UPDATE + last-child delete) written, run, **deleted**; findings above (W1, W2). Green suite does not cover them.
- No lint/typecheck task run beyond Gradle compile.

## Unresolved questions
1. Is Step 7 late-child cleanup + unresolved-section UI in Phase 03 scope or deferred to Phase 4/5? Plan says Phase 03; code has neither.
2. Should a manual root edit that coalesces into a pending preservation op keep the scheduling barrier? (Contract: barrier until accepted/discarded; current code drops it.)
3. For mixed groups, is per-row local cancel of unsent leaf children acceptable under contract item 8 (reading: yes, no remote descendants)?
