# Code Review Cycle 3 — Phase 06 Acceptance & Rollout

Plan: `plans/261019-1511-parent-child-tasks-deadline-surfaces/phase-06-acceptance-and-rollout.md` (todos 71, 73 stay `[~]`).
Score: **6/10**. Not approved. C1 partially resolved (teardown race fixed, but fix regressed state clearing; test red). C2 partially resolved (status-only fixed; backup/restore namespace still wrong).

Note: first read at 07:53 showed Main's claimed edits absent on disk; Main applied them 07:56. Review below = post-07:56 files.

## Verdict on C1 / C2
| ID | Status | Evidence |
|---|---|---|
| C1 desktop controller | **PARTIAL / REGRESSION** | Fixed: collector no longer cancels job / nulls `activeToken`; `startObserving`/`stopObserving` under `synchronized(lifecycleLock)` (desktop-task-surfaces.kt L296, 341, 391). NEW defect: non-Ready branch condition `currentToken == null && activeToken == null` (L318) never blanks state while controller still has `activeToken` (always true after `startObserving` until explicit `stopObserving`). |
| C2 runbook | **PARTIAL** | Fixed: §4.3/§6.1 `--status-only` now passes `--target-version strict-v3` (verified need: without it script defaults target=strict-v2 → expects `ready-v2` → exit 2 on `ready-v3`, reproduced with mock Hub); §5.1 `/strong/bootstrap`+`/strong/push` routes match Hub `strong_sync.rs`; `syncMode==strict-v3`, `epoch`. Remaining: §3.2/§6.1 backup+restore scope (Critical below). |

## Critical
1. **Regression: `DesktopTaskSurfacesTest.testStateClearedOnSignedOut` FAILS deterministically** (`expected:<0> but was:<1>` at test L500; reproduced alone and in full run; passed before 07:56 edit). Cause: L318 `&& activeToken == null`. After `onSignedOut()`/`closeAndDrain()` (token null, readiness Unavailable/SignedOut) the previous account's `sections`/`projectedSections`/`quickAdd.rawTitle` stay in `stickyState`. Production sign-out path calls `stopObserving()` first (main.kt L490) so masked there, but runtime-close/account-switch path (`closeRuntimeInternalSuspend` L429-434) does not: A data persists under `Unavailable`; when `Ready(B)` is collected before `startObserving(B)` runs, `prevReadyToken` is null (state readiness was Unavailable) → no blank → A tasks/quick-add text render under `Ready(B)` until `startObserving(B)` clears them. UI gates content only on readiness (sticky-task-content.kt L105), so this is a cross-account exposure window and violates plan "sign-out/switch does not expose another account's data". Fix: blank whenever `access.getCurrentToken() == null` (drop `&& activeToken == null`); do the check inside `_stickyState.update`/under `lifecycleLock`; never touch job/`activeToken`. Re-run full `DesktopTaskSurfacesTest`.
2. **C2-R2: runbook restore targets wrong namespace + incomplete state.** §6.1.4 `--nsInclude="appTabbyRush.*"` treats `appTabbyRush` as a database. In Hub, `appTabbyRush` is a **collection** (`sync_service.rs::collection_name("tabby-rush")`), inside the configured DB (`MONGO_DB_NAME`, default `glean_hub`). Pattern matches nothing (or wrong DB) while `--drop` is armed. Contract/fingerprint state lives in `_schemas`, fence in `_coordination` (`app_service.rs`, `permit_service.rs`); restoring only app records leaves `_schemas`/`_coordination` at mid-migration state. Use `--nsInclude="<db>.appTabbyRush" --nsInclude="<db>._schemas" --nsInclude="<db>._coordination"` (or whole-DB `--nsInclude="<db>.*"`), name the DB explicitly, and require fence/writer quiescence.
3. **§3.2 `mongodump` has no target** (cycle-2 item unaddressed): no `--uri`/`--config` → defaults to localhost, while §6.1 restore uses `--config`. Backup and restore can hit different instances. Add `--config` to dump; state `--oplog` requires whole-instance dump (conflicts with `--nsInclude`-scoped restore topology — settle which). Expected-file list still flat (`appTabbyRush.bson.gz`, `_coordination.bson.gz`); real layout is `<db>/<collection>.bson.gz` (+ `oplog.bson.gz`) — [INFERENCE from mongodump docs, not executed].

## Warnings
- W1. C1 tests still non-discriminating: no test parks the collector (Ready(A) → closeAndDrain → authenticate B + `startObserving(B)` → release collector → assert job B alive, state B, no A leak). `testRapidAccountSwitch…` still calls `startObserving(token2)` BEFORE `onAccountAuthenticated(token2)` (inverse of production). Plan gate requires deterministic interleaving tests. Plus no test for non-Ready branch with active observer except the failing one.
- W2. Residual TOCTOU in collector: `getCurrentToken()` read then unconditional blank `update`; B can authenticate+emit between → blank wipes B's first snapshot (Room won't re-emit). Narrow; mitigate by evaluating inside `update`/lock.
- W3. `scripts/hub-strict-upgrade.mjs`: missing trailing value now errors (verified `--migration-id` → exit 1). But value-option takes next token blindly: `--poll-interval --migration-id x` consumed `--migration-id` as value (then `x` rejected); `--migration-id --status-only` would set ID to `--status-only` and run a live path. `--poll-interval abc`/`--timeout abc` → NaN silently. No test for missing-value / unknown-option. Reject values starting with `--`, validate numerics.
- W4. Script defaults still `strict-v1 → strict-v2` and help text says so; safe only because runbook passes explicit flags. `--status-only` also does not assert epoch unless `--expected-epoch` passed; runbook §4.3 epoch check is manual curl only (state it).
- W5. Coordinator `reconcileLateChildren` step 2 now re-reads base/ops inside `withMutationLock` (L367-380) — check-then-act fixed. But the eligibility predicate is a third copy of `isProvablyUnsentCreate` (store private L112) and `draft` itself was read outside lock; expose one DAO/store helper (DRY). No new coordinator tests for epoch filter, uncertain/conflict skip, unsent-draft cancel (cycle-1 W5/cycle-2 W-a persist; `testLateChildCleanedUpWhenParentTombstoned` does not assert child DELETE shape).
- W6. Runbook text: §1.1/§3.1 still say `strictEpoch` while §4.3 uses `epoch`; §4.1/§4.2 titled "staging" in production runbook; §5.1 "legacy receipts via bootstrap" unverified; header "Authoritative Operator Runbook Delivered" overstates; exit code 2 / resume semantics undocumented.
- W7. Layering: `contract/todo-ui-state.kt` imports `data.OutboxEntity/ServerBaseEntity` for `ConflictState.create` (UI contract → Room entities). `inspectAndShowConflict` local-Todo reconstruction still duplicated android/desktop.
- W8. `deleteTodoLocally`: `opContractVersion` derivation duplicated (Case 1 vs Case 3); Case 2 hardcodes `CONTRACT_VERSION_V3` — inconsistent.
- W9. `isReadyV3` fallback `defaultContractVersion == V3` forces v3 even when meta says v2 (pre-existing; not Phase 06 scope).

## Suggestions
- Extract conflict-review copy constants; extract `canCoalesce` pattern (×3 in store).
- Make controller state transitions a single `reduce(readiness, tokens)` function so ownership is testable without threads.
- Add `docs/evidence/` captures only when runtime holds are exercised; keep todo 74 open.

## Positive
- Collector no longer owns teardown; start/stop serialized (`synchronized`), emission gated on both tokens.
- Coordinator step 2 locked check-then-act; `it.epoch == activeEpoch` applied.
- `isProvablyUnsentCreate` applied to Case 1/2/3 + leaf; `ConflictState.create` removes duplicated derivation; `isAccountV3` default false (fail-closed).
- Script rejects unknown options and missing values; runbook flags/fingerprints/routes verified vs Hub (v3 fingerprint `7f03…`, `/strong/*` routes, `ready-v3` state via `strict-upgrades/:id`).

## Validation
| Command | Result |
|---|---|
| `node --test scripts/hub-strict-upgrade.test.mjs` | 13/13 pass |
| mock Hub + `--status-only` w/o `--target-version` | exit 2 despite `state=ready-v3` (old runbook form); with `--target-version strict-v3` reaches final read-back (fails only due to mock lacking columns) |
| `node scripts/hub-strict-upgrade.mjs --migration-id` / `--poll-interval --migration-id x` | exit 1 "requires a value" / exit 1 "Unrecognized 'x'" (shows option-like value consumed) |
| `./gradlew :desktopApp:test --tests '*DesktopTaskSurfacesTest*' … --rerun-tasks --offline --dependency-verification=off` | **FAILED**: 23 tests, 1 failed: `testStateClearedOnSignedOut` (L500). Re-run alone: same failure |
| `./gradlew :shared:jvmTest --tests '*RoomAccountStoreTest*' --tests '*AccountSyncCoordinatorTest*' :androidApp:compileDebugKotlin :desktopApp:compileKotlin --continue` | BUILD SUCCESSFUL (up-to-date after prior run) |
| Pre-edit run (before 07:56): DesktopTaskSurfacesTest all PASSED | confirms regression from Main's 07:56 edit |
`--dependency-verification=off` diagnostic only. No live Hub/Mongo/Android/Windows runs.

## Unresolved questions
1. Hub DB name in production (`MONGO_DB_NAME`) and shared-instance vs per-app topology for `--oplog` vs `--nsInclude`?
2. Should sticky `stickyState` retain nothing when readiness != Ready (preferred), or is retention-until-`stopObserving` intended?
3. Require explicit `--expected-version/--target-version` in script (drop v1→v2 defaults)?
4. Is silent cancellation of offline child drafts under tombstoned parent intended UX?
5. Do docs reconciliation (todo 74) updates land before or after runtime evidence?
