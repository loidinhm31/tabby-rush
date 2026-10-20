# Code Review — Phase 06 Acceptance & Rollout

Plan: `plans/261019-1511-parent-child-tasks-deadline-surfaces/phase-06-acceptance-and-rollout.md` (todo statuses updated).
Score: **6/10**. Code W1/W2/late-child/unresolved-UI/conflict-copy solid; desktop controller remediation incomplete; runbook has unsafe commands; holds remain.

## Scope
14 modified files (+1079/-167) + new `docs/strict-v3-cutover-runbook.md`. Diff-focused; compared against plan, `scripts/hub-strict-upgrade.mjs`, `docs/strict-v2-cutover-runbook.md`.

## Critical (blocks phase acceptance)
**C1. Desktop sticky controller remediation not met; lost-snapshot race remains** (`desktop-task-surfaces.kt` init collector ~L300-337, `startObserving` ~L343-377)
- Plan: "remove asynchronous job cancellation from the collector". Collector still does `activeObserverJob?.cancel(); activeToken = ...` in Ready-token-change branch AND in non-Ready branch (`activeToken = null` write races with `startObserving` on another thread → surface can stay dead: activeToken null, job cancelled, no re-start).
- New emission gate `currentReadyToken == token` (read from `_stickyState.readiness`) rejects the initial `observeTodos()` snapshot when it arrives before the readiness collector processed `Ready(token)`. `observeTodos()` = invalidation flow + `distinctUntilChanged` → no replay. Collector then (a) on switch A→B blanks state because `prev(A) != B` even though `startObserving(B)` already ran, (b) on startup just sets readiness. Result: blank sticky until next DB write. Production order (`main.kt` L372-373): `onAccountAuthenticated(token)` then immediately `startObserving(token)` — exactly the window. Old code gated on `activeToken`+`access.getCurrentToken()` only, so this is a new regression path. (Not reproduced; derived from code. Advisor concurred.)
- Fix: single lifecycle owner (`Mutex`/single-writer) for activeToken/job/state resets in `startObserving`/`stopObserving`; collector only updates `readiness` when `r.token == activeToken` (never cancels, never blanks a token already owned by `startObserving`); gate emissions on `activeToken == token && access.getCurrentToken() == token` (drop the readiness-in-state check) or re-publish latest snapshot once readiness becomes Ready(token).
- Tests (`desktop-task-surfaces-test.kt` `testStartupOrder…`, `testRapidAccountSwitchReady…`) are timing-dependent, not deterministic as plan requires: `withTimeout`+`delay(10)` now sit directly in `runTest` (virtual time; old version wrapped in `withContext(Dispatchers.Default)`), so 5s "timeout" can elapse in ~ms real time while real Room/Default threads work. Rapid-switch test calls `startObserving(token2)` before `onAccountAuthenticated(token2)` and only passes if the DB emission loses the race to readiness. Need tests forcing: initial emission before readiness; delayed non-Ready processing; switch with no later DB write.

**C2. `docs/strict-v3-cutover-runbook.md` operationally unsafe**
- §4.1/4.2 use `--app="tabby-rush" --target="strict-v3"`. `hub-strict-upgrade.mjs` parses only `--app-id`, `--target-version`, `--expected-version`, etc.; unknown flags silently ignored; defaults `expectedVersion=strict-v1`, `targetVersion=strict-v2`. Documented command ≠ v2→v3. Must be `--origin "$APPROVED_HUB_ORIGIN" --app-id tabby-rush --expected-version strict-v2 --target-version strict-v3 [--expected-epoch <uuid>] --migration-id "$MIGRATION_ID"` (v2 runbook pattern). Also make CLI reject unknown args.
- §6.1 tells operators to clear fence with `scripts/hub-strict-recovery.js`; v2 runbook §3.3 says NEVER (fresh epoch, strips metadata, v1-only). Direct contradiction; remove.
- §3.2 `mongodump --uri="$MONGODB_URI" --oplog --gzip` (secret in argv, contradicts §2.1 custody; `--oplog` needs whole-instance dump, v2 used `--db`); §6.1 `mongorestore --drop --gzip --dir` lacks `--oplogReplay`; restore after fence auto-expiry under live traffic needs admission fence kept. §3.1/5.3 curl `-H "Authorization: Bearer $GLEAN_ADMIN_TOKEN"` on argv (v2 runbook warns against it; use `curl -K`).
- §5.1 legacy route path `/api/v1/apps/tabby-rush/push` wrong; docs (`api-contract.md`, `hub-compatibility-acceptance.md`) use `/api/v1/sync/:app_id/{push,...}`. Legacy receipts path is `/api/v1/sync/tabby-rush/strong/legacy-receipts` per v2 runbook.
- §4.1 claims ("zero unindexed or malformed records", step list 1-5 incl. defaulting `parentTaskId: null`) not verified against script/Hub; keep only what script prints.

## Warnings
- W1 Docs not reconciled (plan step 8): only the runbook is new; `project-changelog`, roadmaps, `*-acceptance`, `system-architecture` status untouched. Header says "Authoritative Operator Runbook Delivered" while commands are wrong.
- W2 No runtime evidence (staging, Android launcher, Linux/Windows sticky, production-path smoke). Correctly stated as holds in runbook §1.2; todo stays open. No `docs/evidence/` captures.
- W3 `AccountSyncCoordinator.reconcileLateChildren` step 2 calls `store.dao.cancelUnsentCreateAtomic` without `store.withMutationLock`, unlike `discardConflict`/`reapplyConflict` in same file (and step 1 via `deleteTodoLocally` which locks). TOCTOU vs concurrent UI edit of the same unsent draft. Low-probability; wrap in lock.
- W4 Step 2 silently deletes user-authored offline child drafts under a tombstoned parent; no user-visible signal. Plan says "safe durable cleanup", but silent loss of typed content merits a note/ack (or at least doc'd behavior).
- W5 Test gaps for coordinator: only "accepted push" late-child path and reapply rejection tested; no test for uncertain/dispatching skip, conflict skip, unsent-draft cancel, or tombstone from another epoch.
- W6 `reconcileLateChildren` does `getAllServerBase()` + `getAllDrafts()` (full scans) twice per sync cycle and every cycle once any root tombstone exists (tombstones are permanent). Fine for small DBs; add targeted DAO query if lists grow. Surviving-child filter lacks epoch check (roots filtered by epoch, children not).
- W7 `reapplyConflict` parent-deleted rejection uses `updateProgress(status = IDLE, safeError=...)` — may clobber an in-flight status; and error code `PARENT_DELETED` has no UI mapping beyond ConflictReview's flag (UI hides the button, so error is defensive only; OK).
- W8 Inconsistent unsent-create predicate: Case 1/leaf use `base == null`, Case 3 `hasOnlyPendingCreate` still `(base == null || base.deleted)`.

## Suggestions
- DRY: "provably unsent CREATE" predicate repeated 4× (store ×3, coordinator ×1) and `canCoalesce` ×3 → one private helper in store (`isProvablyUnsentCreate(base, ops)`); `pendingParentOp!!` → `requireNotNull`.
- DRY: identical ~12-line preservation/subtask/parentDeleted ConflictState derivation in `android/session-view-model.kt` and `desktop/main.kt` → shared helper next to `ConflictState`.
- Inconsistent defaults: `TodoUiState.isAccountV3=false` (good, fail-closed) vs `TodoList(isAccountV3 = true)` param default; make both false. `isPreservationConflict` uses substring `contains` while rest of store uses `split("|")`; harmless but inconsistent.
- Make `hub-strict-upgrade.mjs` fail on unknown flags.
- Hard-coded long copy strings in `conflict-review.kt`; acceptable given repo convention, but extract constants for testability (no UI test asserts the copy).

## Plan-criteria checklist
| Criterion | Result |
|---|---|
| W1 leaf cancellation atomic, no stranded ops | PASS. `deleteGroupAtomic(..., cancelledChildRowIds)` deletes draft+outbox in same `@Transaction` as root/child DELETE staging; test `testMixedGroupDeleteCancelsUnsentLeafChildAndStagesSyncedRows`. Last-child path equivalent (`cancelledChildRowId`). |
| W2 coalescing + marker retention | PASS. `buildPreservationUpdateEntities` coalesces into pending non-DELETE op (same opId/rev/createdAtEpochMs), merges masks; `saveTodoLocally` retains marker on coalesce; CREATE-coalesce intentionally omits marker (no CAS) and is asserted. 4 new tests. |
| Step 7 late-child cleanup + deleted-parent reapply rejection | PASS w/ W3-W6 caveats. Skips uncertain/dispatching/conflict/pending-delete; per-row DELETE via locked `deleteTodoLocally`. Reapply rejects when parent tombstoned/absent; test present. |
| Unresolved-child UI + ConflictReview copy/enforcement | PASS. `UnresolvedSubtaskSection` (heading semantics, empty-state handling), flags wired on Android+Desktop, Reapply hidden when parent deleted + coordinator enforces, Copy-as-New root wording (copyAsNew builds root Todo). |
| Desktop sticky concurrency/fencing/tests | FAIL — see C1. `StateFlow.update` conversion of quick-add paths is good; observer lifecycle is not serialized. |
| Runbook format/holds | Format/holds PARTIAL — structure mirrors v2 and holds listed; commands wrong — see C2. |

## Positive
- Atomic DAO transactions; consistent opId/localRevision reuse in coalescing; `b == null` tightening in group-delete conservative.
- Fail-closed `isAccountV3` default; account-token fencing kept in quick-add paths.
- Removed dead nested `semantics` in overdue chip (inside `clearAndSetSemantics`, was inert).

## Validation
| Command | Result |
|---|---|
| `./gradlew :desktopApp:test --tests '*DesktopTaskSurfacesTest*' --offline --dependency-verification=off --rerun-tasks` ×4 | BUILD SUCCESSFUL each (flakiness not exposed; timing-dependent by design) |
| `./gradlew :shared:jvmTest --tests '*RoomAccountStoreTest*' --tests '*AccountSyncCoordinatorTest*' --offline --dependency-verification=off` | BUILD SUCCESSFUL (mostly up-to-date; relies on QA run) |
| QA report `plans/reports/qa-261020-0705-phase-06-test-suite-validation.md` | 526 Gradle + Node suites pass — reported by tester, not independently re-run in full by reviewer |
All Gradle runs used `--dependency-verification=off` (diagnostic; does not close supply-chain hold). No staging/Android/Windows runs.

## Metrics
Type coverage n/a (Kotlin); lint not run; coverage tool not configured. JDK 21.

## Unresolved questions
1. Is silent cancellation of offline child drafts under a tombstoned parent (W4) the intended UX, or should user get a notice?
2. Should collector ever update readiness for a token not yet owned by `startObserving`, or should activation fully move to `startObserving`/`stopObserving` (cleaner)?
3. Which `mongodump`/`--db` form is the approved backup for the production Mongo topology (v2 runbook's `--oplog --db` combo is itself invalid for mongodump)?
4. Docs reconciliation (step 8): defer until evidence exists, or land held-status updates now?
