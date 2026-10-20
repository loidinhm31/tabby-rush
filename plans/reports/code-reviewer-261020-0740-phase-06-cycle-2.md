# Code Review Cycle 2 — Phase 06 Acceptance & Rollout

Plan: `plans/261019-1511-parent-child-tasks-deadline-surfaces/phase-06-acceptance-and-rollout.md` (todos 71, 73 updated to `[~]`).
Score: **6.5/10**. Not approved. C1 and C2 only partially resolved.

## Verdict on cycle-1 findings
| ID | Status | Evidence |
|---|---|---|
| C1 desktop controller | **PARTIAL** | Ready branch fixed (no cancel, no `activeToken=null`); emission gate no longer depends on readiness (initial snapshot regression gone). Non-Ready branch `desktop-task-surfaces.kt` L316-329 still `activeObserverJob?.cancel(); activeObserverJob=null; activeToken=null` after non-atomic `access.getCurrentToken()==null` check. Plan gate: "remove asynchronous job cancellation from the collector". |
| C2 runbook | **PARTIAL** | Fixed: CLI flags (`--origin --app-id --expected-version --target-version --migration-id`), recovery-script removal, `--oplogReplay`, `curl -K`, `/api/v1/sync/tabby-rush/...` legacy push paths. Residual defects below. |

## Critical (block phase closure)
**C1-R. Collector still owns lifecycle teardown (race kills new observer).**
- Collector (L316-329) reads `getCurrentToken()==null`, then later cancels whatever `activeObserverJob` holds and nulls `activeToken`. `startObserving(B)` (main.kt L372-373, run in `scope.launch` on `Dispatchers.Default`, main.kt:1060) can install job B between read and cancel → B killed, `activeToken=null` → sticky dead until next account event. `@Volatile` does not make this atomic. Impact: availability (blank/inert sticky), not cross-account leak (all emissions still token-gated).
- `startObserving`/`stopObserving` have no Mutex/single-writer; `cancel`→`prev=activeToken`→`activeToken=token` compound non-atomic; `stopObserving` (main.kt L490, L986) can interleave with `startObserving`.
- Fix: delete lines 327-329 (collector only updates `readiness`/blanks state when `access.getCurrentToken()==null`; teardown is `stopObserving`/`closeRuntimeInternalSuspend.surfaceObserverJob.cancelAndJoin()` already). Serialize start/stop with a `Mutex`/`synchronized` and generation-bound ownership.
- Tests do not discriminate:
  - `testInitialSnapshotProcessedBeforeReadinessCollectorRuns`: controller constructed after `onAccountAuthenticated`, so `stickyState.readiness` is already `Ready(token1)` at construction → old gated code also passes. Name overstates.
  - `testRapidAccountSwitchReady…` calls `startObserving(token2)` BEFORE `onAccountAuthenticated(token2)` (inverse of production). Emission gated on `access.getCurrentToken()==token`; passes only if Room's first emission loses race to authentication → ordering-dependent; does not force stale-collector interleaving; no test for non-Ready/null-token branch.
  - Need: single-thread `StandardTestDispatcher` scope; step: Ready(A) observing → `closeAndDrain` (Unavailable, token null) with collector parked → authenticate B + `startObserving(B)` → release collector → assert job B alive, state B, no A leak. Also test initial emission before readiness (construct controller before authenticate).

**C2-R. Runbook §6 recovery unsafe / inconsistent with Hub + v2 runbook.**
- §6.1 "coordinator fence auto-expires": no expiry in `glean-hub-sync/src/services/permit_service.rs` (grep expire/ttl/lease → none; only `release_fence`). v2 runbook matrix says keep fenced, never blind-clear. Unverified claim; advisor: absence of every expiry mechanism not proven, so reword as "do not assume".
- §6.1 "Abort the CLI runner" then restore: aborting CLI ≠ server migration stopped. Missing v2-style matrix: same `--migration-id` resume, `--status-only` after lost publish response, fencing stall, `operatorRequired`. Plan step 3 requires restart/resume + lost-response read-back to be documented. Restore must follow establishing server migration state / fence owner / writer quiescence, and same-ID resume should precede destructive rollback.
- `mongorestore --drop --oplogReplay` of an instance-wide `mongodump --oplog` dump rolls back ALL apps/DBs sharing the Mongo (Hub is multi-app) to backup time, losing post-backup writes elsewhere; contradicts "zero data loss". Scope with `--nsInclude`/per-db or require maintenance fence.
- §3.2 `mongodump` has no target (`--uri` removed ⇒ defaults to localhost); use `--config` file with `uri:` for custody. `--oplog` valid only for whole-instance dump — say so. Expected-files list (`oplog.bson.gz`, flat `appTabbyRush.bson.gz`) unverified ([INFERENCE]: dump layout is `<db>/<coll>.bson.gz`).

## High
- Runbook §4.3: asserts `syncMode == "ready-v3" or "strict-v3"` and `strictEpoch`. Hub `StrictContractInfo` has `sync_mode`/`epoch`; `ready-v3` is a migration state (`MigrationState::ReadyV3`), not `syncMode`. Script uses `contract.epoch`. Assert `syncMode == "strict-v3"`, `epoch`, and migration state `ready-v3` via `--status-only`.
- Runbook §5.1: `/api/v1/sync/tabby-rush/bootstrap` should be `/strong/bootstrap` (routes `strong_sync.rs`: `/sync/:app_id/strong/{bootstrap,push,pull,legacy-receipts}`). Claim "legacy receipts via bootstrap" unverified.
- `hub-strict-upgrade.mjs` parseArgs: unknown flag rejected (verified: `--bogus` → "Unrecognized command line option"), but known value-flags with missing value are silently ignored (`--migration-id` last arg proceeded to network, no error); `--poll-interval abc` → NaN. In a resume workflow, a dropped ID could mint a fresh migration. Fail-fast on missing/option-like values. No test covers unknown-option rejection. (Advisor: output does not prove a fresh UUID was generated; behavior = no validation error.)
- Late-child step 2 (`account-sync-coordinator.kt` L363-380): eligibility (`base==null`, ops all PENDING CREATE) evaluated BEFORE `withMutationLock`; only the cancel is locked. Re-read base/ops/tombstone/epoch inside the lock (or let the DAO conditionally cancel). Dispatch is under coordinator mutex so practical risk is low, but cycle-1 W3 "fixed" only the lock, not check-then-act.

## Warnings
- W-a. Cycle-2 coordinator changes (`it.epoch == activeEpoch`, lock around cancel) have no tests; cycle-1 W5 gaps remain (uncertain/dispatching skip, conflict skip, unsent-draft cancel, other-epoch tombstone). `testLateChildCleanedUpWhenParentTombstoned` fake server accepts any push and returns child tombstone; does not assert the emitted op is a child DELETE (kind, expectedRevision, v3, no parent UPDATE).
- W-b. DRY: coordinator step 2 re-implements `isProvablyUnsentCreate` (private in store). Expose one store/DAO-level helper. `canCoalesce` pattern still ×3 in store (L334, L663, L867).
- W-c. Layering: `contract/todo-ui-state.kt` now imports `data.OutboxEntity`/`ServerBaseEntity` (UI contract → Room entities). Move `ConflictState.create` next to the store (or accept primitives).
- W-d. `inspectAndShowConflict` local-Todo reconstruction (~25 lines) still duplicated android/desktop; only the tail was DRYed.
- W-e. Runbook titled "§4.2 Fenced Staging Migration"/"§4.1 against staging" while document is the production runbook; staging vs production procedure per plan Architecture not separated. Header "Authoritative Operator Runbook Delivered" overstates; script header comment/help still say "strict-v1 -> strict-v2", defaults `expected=strict-v1,target=strict-v2` (safe only because runbook passes flags; consider failing without explicit versions).
- W-f. Docs reconciliation (todo 74) and all runtime holds (staging, Android launcher, Linux/Windows sticky, native tray) unchanged — correctly still open; no `docs/evidence/` captures.
- W-g. Script polls with 60s default `--timeout`; runbook silent on exit code 2 / resume instructions.

## Suggestions
- Remove the `contains` vs `split("|")` inconsistency if any remains (ConflictState now uses split — good).
- Extract conflict-review copy constants (carried over).
- Keep `isAccountV3=false` default aligned (done; `TodoList` default false, `TodoUiState` false).

## Positive
- `isProvablyUnsentCreate` applied to Case 1/2/3 + leaf; `requireNotNull` replaces `!!`; Case 3 `base==null` aligned.
- `ConflictState.create` removes duplicated derivation; `isAccountV3` defaults fail-closed.
- Script rejects unknown options; runbook flags/fingerprints verified against Hub (`SCHEMA_FINGERPRINT_TABBYRUSH_{V1,V2,V3}` = 132f…/8d64…/7f03… match script and runbook); Hub supports v2→v3 (`strict_upgrade_service.rs`, `ReadyV3`).
- Desktop tests now use real-time timeouts via `withContext(Dispatchers.Default)`; leaked scopes cancelled.

## Validation
| Command | Result |
|---|---|
| `node --test scripts/hub-strict-upgrade.test.mjs` | 13/13 pass |
| `./gradlew :desktopApp:test --tests '*DesktopTaskSurfacesTest*' --offline --dependency-verification=off --rerun-tasks` | BUILD SUCCESSFUL (20 tasks executed, all tests PASSED) |
| `./gradlew :shared:jvmTest --tests '*RoomAccountStoreTest*' --tests '*AccountSyncCoordinatorTest*' :androidApp:compileDebugKotlin --offline --dependency-verification=off` | BUILD SUCCESSFUL (mostly UP-TO-DATE — not a fresh full run; relies on prior QA run) |
| `node scripts/hub-strict-upgrade.mjs --bogus` | rejected with error (verified); `--migration-id` with no value: no arg error, reaches network |
All Gradle runs `--dependency-verification=off` (diagnostic; does not close supply-chain hold). No live staging/Android/Windows runs. Tests passing does not prove C1 race absent (tests do not force the interleaving).

## Metrics
Type coverage n/a; lint not run; coverage not configured; JDK 21.

## Unresolved questions
1. Does the Hub auto-expire/abandon an orphan fence after crashed runner? Only `permit_service.rs` grep checked; needs owner confirmation before §6.1 is rewritten.
2. Approved MongoDB backup/restore topology (single-app DB or shared instance; `--nsInclude` scope)?
3. Should `scripts/hub-strict-upgrade.mjs` require explicit `--expected-version/--target-version` (drop v1→v2 defaults)?
4. Is silent cancellation of offline child drafts under tombstoned parent (cycle-1 W4) intended UX?
5. Are docs reconciliation (todo 74) held-status updates to land now or after evidence?
