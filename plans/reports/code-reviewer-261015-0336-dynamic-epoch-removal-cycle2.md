# Code Review Cycle 2: Dynamic Epoch Collection Removal

Score: **6/10** (cycle-1 recovery/migration/guard/param items fixed + verified; stale-epoch window, adopt-existing-collection, legacy collision, rollback gaps, no runtime tests remain)

## Cycle-1 follow-up matrix
| C1 item | Status |
|---|---|
| Crit1 recovery script broken | FIXED mostly (staging + rename-aside + default index + count check vs staging). Residual W1/W2 below |
| Crit2 no migration | FIXED (runbook in glean-hub/docs/tabbyrush-strict-sync.md + changelog). No rollback note (W5) |
| Crit3 stale-epoch write window | NOT ADDRESSED, not documented (C1) |
| W1 adopt pre-existing collection | NOT ADDRESSED (W3) |
| W2 legacy `_tabbyrush` -> `app_tabbyrush` collision | NOT ADDRESSED (W4) |
| W3 source guard | FIXED (verified: `app_other` -> exit 1; `_strict_v1_5_tabbyrush_abc` -> pass) |
| W4 ismaster | Now fail-closed in both sites (match, `unwrap_or_default`); still unevidenced that `hello` returns `ismaster` [INFERENCE: dead branch] |
| W5 stale docs | MOSTLY fixed; new rename-aside/backup behavior undocumented (W6) |
| W6 tests thin | NOT ADDRESSED (W7) |
| W7 unused `_epoch` param | FIXED (all callsites + test) |

## Critical
1. **Stale-epoch write window (carried from C1 Crit3).** `strong_sync_service.rs` L110-131 / `authorize_strict_access`: epoch is checked against registry only at request start; storage is now epoch-agnostic (`app_<id>`), docs carry no epoch stamp. In-flight old-epoch push straddling CAS can write into post-recovery data. Mitigation = only operator quiescence flag. Needs either explicit documented contract ("quiescence mandatory, no in-flight drain guarantee") or epoch guard. Not mentioned in any updated doc. Severity downgrade to High acceptable if user accepts quiescence-only.

## Warnings
1. **Script rollback missing when no prior live collection.** `hub-strict-recovery.js` L294-312, L342-346: rollback guarded by `if (liveExists)`. If `app_tabbyrush` absent and preflight/CAS fails, staging stays promoted at live name with registry pointing to old epoch. Rename back to `..._failed`.
2. **Rollback not exception-safe.** L306-311, L342-346: a failing rollback rename throws and masks the original preflight/CAS error, leaving state ambiguous with no operator hint. Wrap, log both, print manual restore commands (backup name already printed only on success path).
3. **`register_strict_app` silently adopts pre-existing `app_<id>`** (`app_service.rs` L383-387). Leftover from failed registration / deleted `_schemas` row / other tenant's data adopted without content check; epoch no longer guarantees emptiness. Also TOCTOU between `list_collection_names` and `create_collection`. Fix: require empty (`estimated_document_count()==0`) or fail; handle NamespaceExists.
4. **Legacy namespace collision.** `SyncService::collection_name("_tabbyrush")` == `app_tabbyrush` (verified from code, `sync_service.rs` L128-140). Legacy `register_app` has no id validation. Docs now assert strict/legacy "completely separate" - true only for validated ids. Reject `_`/non-`[a-z0-9-]` ids in legacy registration or reserve.
5. **Runbook lacks rollback + verification detail** (`tabbyrush-strict-sync.md` L46-61): no step to revert (rename back) if admission fails; `strong_sync_admission` invoked without args (`--app-id tabbyrush --epoch <registry epoch>`); no mention that old binaries look at `_strict_v1_*`.
6. **Docs do not describe new recovery semantics**: header comment (script L14-37) and `security-and-recovery.md`/`evidence/hub-recovery-summary.md` don't mention staging `app_<id>_staging_<epoch>`, retained `app_<id>_backup_<epoch>` (contains user todos: PII, no retention/cleanup guidance), or `_failed`/`_cas_failed` leftovers. Changelog "collision safeguards" vague. Orphan staging collection also left on import failure (no cleanup).
7. **No runtime test coverage of changed paths.** Only `test_strict_collection_naming` changed; live Mongo gate `#[ignore]`; script live path (rename-aside, rollback, CAS) never executed - DRY_RUN exits before logic. "TabbyRush 100% pass" does not exercise server/script changes. Plan files still `In Progress`/`Planned`.
8. `ismaster` fallback duplicated in 2 sites (`app_service.rs` L239-242, `strong_sync_service.rs` L49-52): DRY; extract helper or drop if unevidenced.

## Suggestions
- Shared constant for scan-index name / prefix `app_` between Rust and JS.
- Dedupe `renameCollection` choreography into helper in script; log `db.getCollectionNames()` snapshot before mutation.
- `glean-hub-sync/AGENTS.md` L64 still says legacy `app_{appId}` (actual: camelCase `appTabbyRush`); now ambiguous vs strict `app_<id>`. Fix.
- Remove stray blank-line removals (Rust L384-388 comment spacing).
- Update plan status fields.

## Positive
- Recovery script now rename-aside + staging + CAS rollback, default-named index (no IndexOptionsConflict), count verified vs staging, `app_*`/`_schemas`/`system.*` source guard restored.
- Admission topology fail-closed (affirmative writable primary) at both validation and collection acquisition.
- `strict_collection_name` simplified; app-id validated `[a-z0-9-]` so no injection; underscore in staging/backup names cannot collide with another strict app.
- Registry published last, admission before CAS; journaled `w:majority,j:true`.
- Migration runbook uses default index name; docs consistent on `app_<appId>`.

## Reviewed files
glean-hub: `glean-hub-plugin/glean-hub-sync/src/services/{app_service.rs,strong_sync_service.rs,strong_sync_tests.rs,sync_service.rs}`, `src/bin/strong_sync_admission.rs` (grep), `glean-hub-plugin/glean-hub-sync/AGENTS.md`, `docs/tabbyrush-strict-sync.md`.
tabbyrush: `scripts/hub-strict-recovery.js`, `docs/{code-standards,codebase-summary,hub-compatibility-acceptance,hub-schema-integration,project-changelog,project-overview-pdr,project-roadmap,release-acceptance,security-and-recovery,system-architecture}.md`, `docs/evidence/hub-recovery-summary.md`, `plans/261015-0312-remove-dynamic-epoch-collection/*`, cycle-1 report.

## Validation run
- `node --check scripts/hub-strict-recovery.js`: OK.
- DRY_RUN `SOURCE_BACKUP_COLLECTION=app_other`: exit 1 (guard works). `=_strict_v1_5_tabbyrush_abc`: exit 0.
- `cargo test -p glean-hub-sync --lib strong_sync`: 16 passed, 0 failed, 1 ignored (live Mongo gate).
- `cargo fmt -p glean-hub-sync -- --check`: diffs only in pre-existing files (bin/strong_sync_admission.rs, lib.rs, middleware, models, routes); none in changed files' hunks listed.
- Not run: live mongosh recovery, TabbyRush gradle suite (relying on QA report claim), clippy.

## Unresolved questions
1. Is quiescence-only an accepted answer to the stale-epoch window, or is an epoch guard required?
2. Is there any deployed Hub with `_strict_v1_*` data (migration urgency)?
3. Should `register_strict_app` fail on non-empty pre-existing `app_<id>`?
4. Evidence that `hello` returns `ismaster` on any target Mongo? Else drop the fallback.
5. Retention policy for `app_<id>_backup_<epoch>` collections?
