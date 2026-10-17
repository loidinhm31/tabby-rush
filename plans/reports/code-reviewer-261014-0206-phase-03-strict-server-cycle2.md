# Phase 03 Review — Cycle 2 (TabbyRush-only strict server + legacy isolation)

**Score: 6/10.** Cycle-1 defects (concern config, UUID hex, epoch validation, admission tightening, DTO strictness, cursor panic, per-op errors, provision key output, CLAUDE.md fence) verified fixed in source. Phase still NOT acceptable: plan §9–§12 acceptance gates unmet, replay-durability path still has a hole, docs overclaim topology support.

## Scope
- Plan: `plans/261013-1540-tabbyrush-glean-hub-reuse/phase-03-hub-schema-compatibility.md` (status lines updated).
- Reviewed: glean-hub-sync `strong_sync_service.rs`(905), `app_service.rs`(strict parts), `strong_sync_error.rs`, `models/strong_sync.rs`, `middleware/sync_mode_guard.rs`, `routes/strong_sync.rs`, `routes/apps.rs` diff, `lib.rs` diff, `bin/strong_sync_admission.rs`, tests (`strong_sync_tests.rs`, `strong_sync_route_tests.rs`); tabbyrush `scripts/hub-schema-provision.mjs`, `scripts/hub-compatibility-smoke.mjs`, `docs/evidence/hub-compatibility-summary.md`, doc claims grep; QA report `qa-261014-0159-...`.
- Not reviewed in depth: `integration/glean-hub/tabbyrush-schema.json`, glean-hub docs prose, models/app.rs/api.rs (diff only skimmed).

## Validation (run by me)
| Command | Result |
|---|---|
| `cargo test --locked` (glean-hub-sync) | 35 pass / 0 fail / 1 ignored (`test_strong_sync_live_mongo_acceptance_gate`) |
| `cargo clippy --locked --all-targets` (after touching lib.rs + bin) | 0 warnings (QA's 7 warnings no longer reproduce) |
| Live Mongo / mounted router / old-binary / node smoke+provision | NOT run: no `MONGO_TEST_URI`, no mongod/mongo image locally (docker/podman present, no pull attempted), no Hub origin |
| QA `--ignored` gate | hard-fails w/ clear panic when URI unset (confirms hard-fail; proves nothing about Mongo behavior) |

## Critical
1. **Replay confirmation result discarded** — `strong_sync_service.rs:637` `let _ = coll.update_one(replay_filter, …)` in `classify_reread`, returns `Replayed` regardless of error/`matched_count==0`. Reachable on create duplicate-key / WC-error-but-applied and update/delete match0 reread. Plan §7: `matched_count==1` REQUIRED, else unresolved/`retryableFailure`. Without it the "durable real replay confirmation" invariant is not delivered on the very path (post-WC-error retry) it exists for. Also main replay path `:364` `Ok(_)` (match0) returns `RevisionConflict` — plan says retryableFailure/MAYBE applied; reporting definitive conflict for a possibly-applied op breaks client conservative handling. Fix: share one `confirm_replay()` helper (filter id+revision+lastOp.opId+digest, matched==1 → Replayed; match0/Err → RetryableFailure) used by both paths.
2. **Acceptance suite missing (plan §9–§12, "source inspection cannot pass 03")** — only live test = register + admission (strong_sync_tests.rs:247-308), `#[ignore]`d; zero tests for push/pull CAS, replay vs successor, opIdReuse, tombstone/resurrection, overflow, cursor scope, forged epoch creates-no-collection, index/collation/TTL admission failure, topology. `strong_sync_route_tests.rs` = scope-guard w/ stub handler + DTO test; no mounted `/api/v1` router, no legacy-426, no auth, no bypass/cleanup-job, no tolerant list, no strict update rejection, no old-binary/legacy-client fixtures. Runtime gates unexecuted. Implementation status in plan must stay "not accepted".
3. **Concern policy vs claims / no topology admission** — push uses fixed `w:1,j:true`+`local` (`:148-159`); pull uses plain `db.collection` (`:729`); admission never inspects topology/journal. `docs/evidence/hub-compatibility-summary.md` says supported "replica set with `{w:"majority", j:true}`"; code never selects majority. Plan §4: replica majority/primary-majority reads, unsupported topology → 503 notAdmitted. On a replica set w:1 loses acked writes on failover → durability claim false. Decide (cycle-1 open question still unanswered): either implement hello/topology detection + majority, or restrict docs/evidence/admission to standalone and fail closed on replica sets.

## Warnings
- `legacy_sync_mode_guard` still fails open (`sync_mode_guard.rs:21 if let Ok(...)`): DB error forwards strict app to legacy handlers (legacy namespace only, but violates fail-closed §5). Should return 503/500. Path-parse duplication with dual_auth; encoded-id safety relies on dual_auth ordering, untested.
- Pull: collection has no explicit read/write concern (inconsistent with push); corrupt rows silently coerced (`unwrap_or(0)/false/{}` in pull `:749-753`, apply_operation `:326-327`, createdAt immutability check skipped when `data` missing `:462`). Should fail closed (NotAdmitted/Internal).
- `user_id` from JWT never validated as exact 24-hex ObjectId (plan Architecture/§1); `_id` = `user:row` and cursor `split(':')` — a sub containing `:` breaks cursor decode for that user. Add check.
- Admission not "exact": extra indexes (unique/partial/hidden on other keys) not rejected, only TTL; `filter_map(as_i32)` silently drops non-int key values so `{userId:1,tableName:1,rowId:1,x:"text"}` passes; hidden flag unchecked; collection `type`/timeseries/clustered unchecked. Cache never evicts (plan allows, but index drop after admit undetected until restart). `list_collection_names()` full scan redundant before `list_collections` filter.
- Provision script key custody: (a) 409 rerun writes placeholder `"(pre-existing key retained…)"` to `--out`, **overwriting** earlier file holding the real key; (b) key only printed after read-back — read-back failure `process.exit(1)` loses the only copy; (c) `writeFileSync` no mode 0600; (d) `--token` still argv; (e) read-back checks columns but 409 path never verifies it is a strict app w/ this epoch beyond syncMode.
- Smoke script is vacuous without creds: every gate passes on HTTP 401 (`:77,:106,:133,:162`), so `ALL CHECKS PASSED` with no key/JWT proves nothing; no strict happy path (push create/pull), no legacy-app-unchanged check. Fail if creds missing, or report SKIPPED.
- `strong_sync_admission` bin: `--mongo-uri` on argv (plan: never URI argv), silent fallback to `MONGO_TEST_URI`/default db `glean_hub`, prints `e.to_string()` (may leak host/driver detail); no test that helper == server validator.
- Evidence doc says "34 tests" (actual 35 pass+1 ignored), says nothing is unexecuted beyond live gate; doesn't state mounted/old-binary gates unexecuted (plan: separate static vs actual). `register_strict_app` not retry-safe: failure after `create_collection` orphans collection (new epoch each retry) — acceptable but document.

## Suggestions
- DRY: `apply_operation` ~400 lines with ~14 copy-pasted `StrictOperationResult{…}`; add `fn result(&op, digest, status)`/builder; merge Update/Delete write paths (differ only in `$set`); share replay logic (ties to Critical 1).
- `serde_json::to_vec(&request)` re-serialization for payload cap duplicates body-limit layer and measures wrong bytes.
- `compute_digest` 10 args → struct (currently `#[allow]`).
- Remove unused `StrictSyncError::Unauthorized` (YAGNI). Make `Create`/`Update` data-required validation a single helper.
- Add `[[bin]]` doc/provenance; test strict helper vs server validator.
- Axum JSON rejections (422/415) not strict-coded; bad body on strong routes returns generic Axum error.

## Positives
- Cycle-1 fixes real and verifiable: canonical UUID rigorous, per-op errors → retryable, empty-page cursor retained, `is_ascii` cursor guard, `deny_unknown_fields`, per-request schema re-check, epoch validated in admission, journaled concern on push handle, fail-closed missing spec.
- Clean isolation: separate error/model/service/route/guard modules; legacy `register_app`/`AppListItem` untouched; `update_schema` filter `syncMode $exists:false` + disambiguating reread; registry published last; epoch checked before collection handle; user derived from auth only; layering (dual_auth outer, mode guard inner) correct.
- clippy clean, hard-fail live gate, digest vectors match Kotlin.

## Metrics
- Tests 35 pass/0 fail/1 ignored; live Mongo coverage ≈ register+admission only. Clippy 0 warnings. Type coverage n/a.

## Recommended actions
1. Fix replay confirmation (Critical 1) — shared helper, matched==1 required, RetryableFailure otherwise.
2. Resolve durability policy (Critical 3): implement topology/majority or scope docs+admission to standalone.
3. Provision isolated Mongo and implement §9/§10/§12 suites (CAS/replay/opIdReuse/tombstone/overflow/cursor/epoch forgery/admission failures/mounted router/bypass/old-binary); execute and record evidence.
4. Fix warnings: guard fail-closed, user-id 24-hex check, pull concerns/corrupt rows, stricter admission, provision/smoke scripts, admission bin argv URI.
5. Correct evidence doc counts + unexecuted gate statements.

## Unresolved questions
- Is an isolated Mongo (version/topology/journal) + old-binary artifact available for §9–§12? (docker/podman present locally, no mongo image.)
- Standalone-only (w1/j:true) or replica majority required? Still undecided; blocks Critical 3.
- API key custody: script file (0600) vs Phase 08 operator flow?
- Do Kotlin and Rust `validate_title` agree beyond digest vectors (no cross-language title vectors seen)?
