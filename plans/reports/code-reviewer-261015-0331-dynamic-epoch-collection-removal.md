# Code Review: Dynamic Epoch Collection Removal (FR-STRICT-02)

Score: **4/10** (naming swap itself correct + tests green; recovery/migration/isolation semantics broken or undocumented)

## Scope
- glean-hub: `app_service.rs` (strict_collection_name, validate_strict_admission, register_strict_app), `strong_sync_tests.rs`, `docs/tabbyrush-strict-sync.md`, `glean-hub-sync/AGENTS.md`
- tabbyrush: `scripts/hub-strict-recovery.js`, 11 docs (see list below), plan dir + QA report
- ~60 changed lines code, ~40 docs. Read also: `strong_sync_service.rs` (authorize_strict_access), `sync_service.rs::collection_name`, legacy `register_app`.

## Critical
1. **Recovery script defeats its own fresh-epoch model; likely fails live.** `scripts/hub-strict-recovery.js` L204-229, L286.
   - New epoch is generated but target = live `app_<appId>` (same collection as old epoch). Old/corrupt rows are not moved aside nor truncated; imported rows land among them. Purpose of recovery (retire bad data) lost.
   - `insertMany` with `_id=userId:rowId` hits duplicate-key on any overlapping row -> aborts mid-way after partial writes into the LIVE collection (no rollback; old epoch still active).
   - `actualTargetCount !== totalSourceCount` (L286) always throws unless live collection empty -> script unusable on a populated app.
   - Script names index `scan_user_table_row`; Rust `register_strict_app` creates default-named `userId_1_tableName_1_rowId_1`. Same keys, different name => Mongo IndexOptionsConflict (code 85) at Step 3, before import [INFERENCE: not run live, standard Mongo behavior].
   - Step 5 admission preflight (`--epoch newEpoch`) now validates the same live collection regardless of epoch: vacuous gate.
   - Fix: rename-aside (`renameCollection app_<id> -> app_<id>_retired_<oldEpoch>`, preserves data+indexes) then create fresh `app_<id>` with default-named index, import, CAS; on pre-CAS failure rename back. Or drop epoch rotation from recovery entirely and redesign (bigger decision).
2. **No data migration for existing deployments.** Server now resolves `app_tabbyrush`; existing registered strict apps live in `_strict_v1_5_tabbyrush_<epoch>`. `authorize_strict_access` -> `validate_strict_admission` -> "Collection 'app_tabbyrush' does not exist" -> every strict bootstrap/push/pull fails (503) until operator renames. Nothing in repo/docs/changelog says this (grep `rename|migrat` in docs: none). Also rollback to old binary would look for `_strict_v1_*` and miss new data. Need runbook step (`db.getCollection('_strict_v1_5_tabbyrush_<epochNoHyphens>').renameCollection('app_tabbyrush')` before deploy, or one-time startup migration) + rollback note.
3. **Stale-epoch write window reopened (isolation weakened).** Old design: in-flight request validated for old epoch kept writing to old namespace after rotation (contract: "never redirect in-flight request to later epoch"). Now `apply_operation` with old epoch can write into the same collection that now holds new-epoch data; docs/records carry no epoch field to reject it. Mitigated only by quiescence flag. Document explicitly or add epoch stamp/guard.

## Warnings
1. **`register_strict_app` adopts pre-existing collection silently** (`app_service.rs` L384-388). Previously guaranteed fresh/empty (random epoch). Now a leftover from a failed registration (registry insert after collection created), manual `_schemas` delete, or other tenant's `app_<id>` gets adopted with arbitrary documents; admission checks only options/indexes, not content. Fail if exists unless explicitly empty (`estimated_document_count==0`), or validate doc shape. Also TOCTOU between `list_collection_names` and `create_collection`; just call `create_collection` and map NamespaceExists to the check.
2. **Namespace isolation from legacy no longer structural.** Legacy `register_app` has no app-id validation; legacy `collection_name("_tabbyrush")` -> `app_tabbyrush` == strict `tabbyrush`. Admin-only, low probability, but old guarantee ("never legacy `app*`") gone. Reject/validate legacy ids `^[a-z0-9-]+$` or reserve `_`-prefix. Also notification plugin writes directly via `SyncService::collection_name`.
3. **Source-collection guard relaxed (recovery script L108-116).** Previously rejected all `app_*`; now only own target. `SOURCE_BACKUP_COLLECTION=app_other` passes (verified via DRY_RUN run) -> can import another strict app's todos into this app (shape validation passes). Restore `startsWith('app_')` rejection (plus own target), keep `_strict_v1_` allowed if migration from old namespaces is wanted.
4. **Unrelated, untested change: `ismaster` fallback** (`app_service.rs` L240-243). Out of scope for requirement; `hello` returns `isWritablePrimary` (`ismaster` is the legacy `isMaster` cmd field) [INFERENCE] so branch likely dead; `unwrap_or(true)` still fail-open. Changelog advertises it as a reconciliation. Revert or justify with evidence.
5. **Docs/script comments contradict new behavior.** Still claim fresh isolated namespace / previous collection retained: `scripts/hub-strict-recovery.js` L34, L36, L334 ("Previous namespace retained intact (never dropped)" printed on success — now false); `docs/security-and-recovery.md` L46, L58, L121; `docs/project-changelog.md` L67; `docs/hub-compatibility-acceptance.md` L31 ("candidate namespaces ... before epoch cutover"); `docs/hub-schema-integration.md` still says "isolated"; `docs/evidence/hub-recovery-summary.md` L14-18. `plans/261013-1540-tabbyrush-glean-hub-reuse/contracts.md` (L35-37) still normative for old formula (historical; mark superseded).
6. **Test coverage thin.** Only `test_strict_collection_naming` changed. No test for idempotent registration/pre-existing collection, no recovery logic test (DRY_RUN exits before any logic), live Mongo gate ignored => none of the changed runtime paths executed. QA report "100% pass" is about unrelated suites; does not validate the migration. Plan phases still marked Pending/In Progress.
7. **Unused `_epoch` param** in `strict_collection_name(app_id, _epoch)` (KISS/YAGNI): 3 callers + test still thread epoch for nothing. Clean cutover = drop param. Epoch-keyed admission cache fine.

## Suggestions
- Add doc comment on changelog: breaking storage change + operator action.
- Remove stray blank-line deletions (blank line lost before "Create scan index" comment in Rust, before index comment in JS) — cosmetic.
- `cargo fmt --check` on `glean-hub-sync` flags only pre-existing files (`bin/strong_sync_admission.rs`, `lib.rs`); `app_service.rs` clean.
- Use named constant for the index name shared across Rust/JS to avoid drift.

## Positive
- Naming function simple, app-id validated (`[a-z0-9-]{1,40}`) before use so no injection into collection name; admission check still enforces options/index/TTL/collation; registry published last; legacy `appX` camelCase distinct from `app_x` in practice.
- Docs fixed to correctly describe legacy camelCase naming.
- Admission/auth flow unchanged (epoch checked against registry before collection use).

## Reviewed files
glean-hub: `glean-hub-plugin/glean-hub-sync/src/services/{app_service.rs,strong_sync_tests.rs,strong_sync_service.rs,sync_service.rs}`, `src/routes/apps.rs`, `docs/tabbyrush-strict-sync.md`, `glean-hub-plugin/glean-hub-sync/AGENTS.md`
tabbyrush: `scripts/hub-strict-recovery.js`, `docs/{system-architecture,project-overview-pdr,hub-schema-integration,code-standards,security-and-recovery,project-changelog,project-roadmap,release-acceptance,codebase-summary,hub-compatibility-acceptance}.md`, `docs/evidence/hub-recovery-summary.md`, `plans/261015-0312-remove-dynamic-epoch-collection/*`, `plans/reports/qa-261015-0326-dynamic-epoch-removal-validation.md`

## Validation run by reviewer
- `cargo fmt -p glean-hub-sync -- --check`: diffs only in pre-existing `bin/strong_sync_admission.rs`, `lib.rs`; none in changed files.
- `CONFIRM_MAINTENANCE_QUIESCENCE=true DRY_RUN=true SOURCE_BACKUP_COLLECTION=app_other EXPECTED_OLD_EPOCH=<uuid> node scripts/hub-strict-recovery.js`: PASS (demonstrates relaxed source guard, Warning 3).
- `grep _strict_v1_`: only historical plans/reports + changelog remain; no active code refs.
- Not re-run: cargo test/clippy, gradle, smoke (QA report claims pass; not independently re-executed). Live Mongo behavior (index-name conflict, dup-key) not executed — INFERENCE.

## Unresolved questions
1. Is there a production/staging Hub with registered `tabbyrush` in `_strict_v1_*`? (determines Critical 2 urgency; migration vs fresh-start)
2. Should epoch rotation still exist in recovery if storage is shared? If yes, rename-aside strategy vs in-place wipe?
3. Is `ismaster` fallback required by a real target Mongo build/test env? Evidence?
4. Should legacy `register_app` ids be validated to rule out `_`-prefixed collisions?
