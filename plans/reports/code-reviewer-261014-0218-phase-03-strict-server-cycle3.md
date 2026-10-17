# Phase 03 Review — Cycle 3 (TabbyRush-only strict server + legacy isolation)

**Score: 6/10.** Cycle-2 Critical 1 (replay confirm) fixed in code. Critical 2 (acceptance suite) and Critical 3 (durability policy/doc claims) still open. Two cycle-3 claims in the brief are not true in the files (provision key custody; "36 tests across Rust and Node"). Phase NOT acceptable per plan ("source inspection cannot pass 03").

## Scope
- Plan: `plans/261013-1540-tabbyrush-glean-hub-reuse/phase-03-hub-schema-compatibility.md` (status lines updated).
- glean-hub-sync (`/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync`): `services/strong_sync_service.rs`(922), `services/app_service.rs` strict parts, `strong_sync_error.rs`, `models/strong_sync.rs`(skim), `middleware/sync_mode_guard.rs`, `routes/strong_sync.rs`, `routes/apps.rs`/`lib.rs` diffs, `bin/strong_sync_admission.rs`, tests, `CHANGELOG.md`, `docs/tabbyrush-strict-sync.md`.
- TabbyRush: `scripts/hub-schema-provision.mjs`, `scripts/hub-compatibility-smoke.mjs`, `integration/glean-hub/tabbyrush-schema.json`, `docs/hub-schema-integration.md`, `docs/hub-compatibility-acceptance.md`, `docs/evidence/hub-compatibility-summary.md`.
- Note: glean-hub worktree has large unrelated dirty diff (embed-app, app UI, docs rewrites); not reviewed.

## Validation (run by me)
| Command | Result |
|---|---|
| `cargo test --locked` (glean-hub-sync) | 36 pass / 0 fail / 1 ignored (`test_strong_sync_live_mongo_acceptance_gate`) |
| `cargo clippy --locked --all-targets` | 0 warnings |
| `node --check` both scripts | OK |
| `node scripts/hub-compatibility-smoke.mjs --origin http://127.0.0.1:1` | exits non-zero, 4x ERROR fetch failed (connection failure not vacuous) |
| Live Mongo / mounted router / old-binary / real smoke+provision | NOT run: `MONGO_TEST_URI` unset, no mongod/mongosh, no Hub origin (docker/podman present, no image) |

## Cycle-2 findings status
| # | Finding | Status |
|---|---|---|
| C1 | replay confirm discarded / match0→RevisionConflict | FIXED: `confirm_replay` (`strong_sync_service.rs:873`) filter id+rev+lastOp.opId+digest, `matched_count==1` else RetryableFailure; used by main path (:341) and `classify_reread` (:586). Verified in source. UNTESTED (no Mongo). |
| C2 | acceptance suite §9–§12 | OPEN: only 1 `#[ignore]` live test (register+admission). Zero tests of CAS/replay/opIdReuse/tombstone/overflow/cursor/forged-epoch/index-TTL-collation failures; route tests = scope-guard on stub handler + DTO/error-code mapping; no mounted `/api/v1` router, no legacy-426, bypass, old-binary, legacy-client. |
| C3 | concern policy vs docs/topology | PARTIAL: both handles now w1+j:true+local; docs unchanged and still contradict (see Critical 1 below). |
| W guard fail-open | FIXED (`sync_mode_guard.rs:29` Internal on DB err). |
| W user id 24-hex | FIXED push/pull (`is_valid_user_id`). |
| W pull fail-closed | MOSTLY: rowId/revision/deleted fail closed; `data` still `unwrap_or(json!({}))` (:719-720). apply path still `unwrap_or(0/false)` (:332-333, :424, :578-579). |
| W admission exactness | OPEN (extra indexes, hidden, non-int key values dropped by `filter_map(as_i32)`, collection type). |
| W provision key custody | OPEN — contradicts brief (see Critical 2). |
| W smoke vacuous | PARTIAL: strict when both key+JWT present; with neither, 401 passes → "ALL CHECKS PASSED" proves nothing. |
| W admission bin argv URI | OPEN. |
| W evidence counts | PARTIAL: 36 correct, but "across Rust and Node.js suites" false; replica-majority claim stays. |

## Critical
1. **Durability claim contradicts code (replica set).** `docs/evidence/hub-compatibility-summary.md` says supported topology includes "replica set with `{w:"majority", j:true}`"; code hardcodes `w:1,j:true` (`strong_sync_service.rs:154-158,679-683`). `validate_strict_admission` (`app_service.rs:234-244`) only checks standalone writable-primary; **replica sets (`setName` present) pass with no check**, and `if let Ok(hello)` ignores hello failure (fail-open). On a replica set w:1 acks can roll back on failover → "durable replay confirmation / at-most-one effect" false. Plan §4 requires majority or 503 notAdmitted. Fix: either (a) detect `setName` → use `w:"majority"`+`ReadConcern::majority` and verify, or (b) fail closed (NotAdmitted) on replica sets and strike the replica claim from docs; make hello error → NotAdmitted. Decision still unanswered since cycle 1.
2. **Provision script key custody broken; brief claim false.** `hub-schema-provision.mjs`: key printed only after read-back (`:158-162`) → read-back failure (`process.exit(1)` :135/:141/:147) loses only copy; 409 path sets `apiKey` placeholder `'(pre-existing key retained in database)'` and with `--out` **overwrites** a file holding the real key (:164); `writeFileSync` no 0600; `--token` on argv. Script untouched since 01:56 (before cycle 2). Fix: print/write key immediately after 201 (mode 0o600, `wx` flag / refuse overwrite), never write placeholder; on 409 skip `--out`.
3. **Acceptance suite missing (plan §9–§12).** Replay fix, CAS, cursor, epoch forgery, admission negatives, legacy-bypass all unexercised; evidence doc correctly lists mounted/old-binary gates as unexecuted but plan success criteria unmet. Phase stays "not accepted" until run against real isolated Mongo with no skip.

## Warnings
- Evidence doc: "All 36 unit and route tests pass … across Rust (`glean-hub-sync`) and Node.js suites" — no Node tests exist in repo (only `tests/*.sh` for phases 01/02). Misleading; also docs say strict data is "Never accessible by legacy … cleanup jobs/notification writers" — asserted, unmeasured (plan §10).
- `docs/tabbyrush-strict-sync.md` runbook: `cargo run --bin strong_sync_admission -- --mongo-uri $MONGO_URI` — URI on argv (plan: never) and cargo on production (plan: release prerequisite = packaged executable, no Cargo on prod). Bin also silently falls back to `MONGO_TEST_URI` and default db `glean_hub`; no test that helper == server validator.
- Admission still not "exact": extra unique/partial/hidden indexes unchecked, `filter_map(as_i32)` lets `{userId:1,tableName:1,rowId:1,x:"text"}` pass (`app_service.rs:302-308`), collection `type`/timeseries/clustered unchecked, hidden flag unchecked; `list_collection_names()` full scan redundant with `list_collections().filter`; cache never evicts.
- Corrupt-row handling inconsistent: apply path coerces missing `revision/deleted` to 0/false; update `createdAt` immutability silently skipped if `data` missing/invalid (:423-437); pull coerces bad `data` to `{}`. Also nonnull `lastOp.acceptedRevision == revision` invariant (plan §6) not enforced; replay returns stored value with `unwrap_or(current_rev)`. Should fail closed (Internal/RetryableFailure).
- Smoke: with neither key nor JWT every gate passes on 401 → misleading green; `--debug-local` parsed, unused (plan command references it). No strict happy path (create/pull/replay) nor legacy-app-unchanged check. Fail/SKIPPED when creds absent.
- Perf: `legacy_sync_mode_guard` adds one `_schemas` find on every legacy sync request of all Hub apps (plus dual_auth's own find) and turns a transient `_schemas` read error into 500 for unchanged apps. Acceptable but note; consider reuse of schema fetched by dual_auth. Strict push: per-op find + journaled write sequential (≤100) — fine for scale.
- `register_strict_app` not retry-safe: orphan collection on failure after `create_collection`, concurrent same-app registration leaves orphan (new epoch each attempt). Document.
- Encoded-id bypass protection relies on guard + dual_auth sharing the same raw-path extraction; no test pins this ordering.

## Suggestions
- DRY: `apply_operation` still ~370 lines with ~14 copy-pasted `StrictOperationResult{…}`; add `result(&op, digest, status)` helper; Create/Update share data-validation block; Update/Delete share write path (differ only `$set`).
- `serde_json::to_vec(&request)` payload cap re-serialization measures wrong bytes, duplicates body-limit layer (`:130`).
- Remove unused `StrictSyncError::Unauthorized`; `compute_digest` 10 args → struct (`#[allow]`).
- Axum JSON rejections (415/422) not strict-coded on strong routes.
- Add Mongo-backed tests once DB available: concurrent create same row, update/delete CAS, replay vs successor, opIdReuse, tombstone resurrection, `confirm_replay` match0 → RetryableFailure, admission negatives (TTL/collation/wrong index), forged epoch creates no collection.

## Positives
- Replay confirmation now single shared helper; error/match0 → RetryableFailure (correct conservative semantics).
- Fail-closed guards (legacy guard 500 on DB error, pull missing fields, 24-hex user id) real; strong error taxonomy tested over all codes.
- Isolation clean: separate model/service/route/error/guard modules; legacy `register_app` signature + `AppListItem` untouched; `update_schema` filter `syncMode $exists:false` with disambiguating reread; registry published last; epoch checked before collection handle; user only from auth; cursor scope-bound.
- clippy clean; digest vectors pass; live gate hard-fails without URI.

## Metrics
- Tests 36 pass / 0 fail / 1 ignored; live Mongo coverage ≈ register+admission (ignored, unexecuted). Clippy 0 warnings. Type coverage n/a.

## Recommended actions
1. Resolve durability: majority on replica sets or fail-closed on `setName`; hello error → NotAdmitted; fix evidence/CHANGELOG/runbook claims.
2. Fix provision script key custody (print/write immediately, 0600, no overwrite/placeholder, token via env/stdin only).
3. Stand up isolated Mongo; implement + run §9/§10/§12 suites; record real evidence.
4. Tighten admission (exact index set, key types, hidden, collection type); fail closed on corrupt rows; enforce lastOp invariant.
5. Smoke: SKIPPED/fail without creds, add strict happy path; remove/implement `--debug-local`.
6. Admission bin/runbook: URI via env only, no fallback to test URI, packaged binary path.
7. Correct evidence doc ("Node.js suites", unmeasured isolation claim).

## Unresolved questions
- Replica-set support required (majority) or standalone-only for Phase 03? Blocks Critical 1.
- Isolated Mongo (version/topology/journal) + previous Hub binary + legacy-client fixtures available for §9–§12? (docker/podman present, no mongo image.)
- API key custody: script file (0600) vs Phase 08 operator flow?
- Is glean-hub worktree's unrelated dirty diff (embed-app renames, docs rewrites) intentionally outside Phase 03 ownership?
