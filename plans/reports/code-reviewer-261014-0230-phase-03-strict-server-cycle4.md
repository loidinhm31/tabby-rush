# Phase 03 Review — Cycle 4 (Doggo-only strict server + legacy isolation)

**Score: 7/10.** Code fixes from cycle 3 verified in source (topology-aware concern, admission fail-closed/exact, key custody, lastOp invariant, docs honest). Phase NOT acceptable: plan §9–§12 live-Mongo/mounted/old-binary gates still unexecuted ("source inspection cannot pass 03"). No new tests added since cycle 3 (still 36 + 1 ignored).

## Scope
- Plan: `plans/261013-1540-doggo-glean-hub-reuse/phase-03-hub-schema-compatibility.md` (status lines updated).
- glean-hub-sync: `services/strong_sync_service.rs`, `services/app_service.rs`, `middleware/sync_mode_guard.rs`, `routes/strong_sync.rs`, `routes/apps.rs`/`lib.rs` diffs, `bin/strong_sync_admission.rs`, `docs/doggo-strict-sync.md`.
- Doggo: `scripts/hub-schema-provision.mjs`, `scripts/hub-compatibility-smoke.mjs`, `integration/glean-hub/doggo-schema.json`, `docs/evidence/hub-compatibility-summary.md`, `docs/hub-*.md` (grep only).
- glean-hub worktree has large unrelated dirty diff (embed-app, UI, docs); not reviewed.

## Validation (run by me)
| Command | Result |
|---|---|
| `cargo test --locked` (glean-hub-sync) | 36 pass / 0 fail / 1 ignored (`test_strong_sync_live_mongo_acceptance_gate`) |
| `cargo clippy --locked --all-targets` | 0 warnings/errors |
| `node --check` both scripts; schema JSON parse | OK |
| `node scripts/hub-compatibility-smoke.mjs --origin http://127.0.0.1:1` | exit 1, 4x ERROR fetch failed (not vacuous on connection failure) |
| Live Mongo / mounted router / old-binary / real smoke+provision | NOT run: `MONGO_TEST_URI` unset, no mongod/mongosh, no mongo image locally |

## Cycle-3 findings status
| Finding | Status |
|---|---|
| C1 replica-set durability / hello fail-open in admission | FIXED. `get_strict_collection` (`strong_sync_service.rs:32-63`) picks majority+j / majority read on `setName`, else w1+j / local. `validate_strict_admission` fails closed on hello error (`app_service.rs:234-237`). Evidence doc now consistent with code. UNTESTED live. |
| C2 provision key custody | FIXED mostly: key printed+written (0600) right after 201 before read-back; 409 path never writes placeholder/overwrites. Residuals W5. |
| C3 acceptance suite §9–§12 | OPEN (see Critical 1). |
| Admission exactness | FIXED: only `_id` + exact `{userId,tableName,rowId}` allowed; non-int/extra/4-key variants fall to "unexpected index" reject; partial/sparse/collation/TTL rejected. Residual: hidden flag, view/timeseries/clustered not checked. |
| lastOp.acceptedRevision == revision | FIXED on apply path (`:359-368`); NOT on `classify_reread`. |
| revision/deleted required | FIXED on apply + pull; NOT in `classify_reread` (`:610-611` `unwrap_or(0/false)`). |
| Legacy guard fail-open | FIXED (500 on DB error). |
| 24-hex user id | FIXED. |
| Evidence doc overclaim ("Node.js suites") | FIXED; doc honest on unexecuted gates. |
| Runbook URI argv / packaged binary | FIXED in runbook (env, `./target/release/...`); binary itself still accepts `--mongo-uri` + `MONGO_TEST_URI` fallback. |
| Smoke vacuous without creds | PARTIAL (see W4). |

## Critical
1. **Acceptance suite §9–§12 absent/unexecuted.** Only live test is `#[ignore]` register+admission. Zero Mongo-backed tests for create/update/delete CAS, replay confirm (`confirm_replay` matched==1 else RetryableFailure), opIdReuse, tombstone resurrection, overflow, cursor scope/forgery, forged epoch creates-no-collection, admission negatives (TTL/collation/wrong index/extra index/hidden), replica vs standalone concern selection. No mounted `/api/v1` router tests, no legacy-426/bypass, no old-binary, no legacy-client fixtures. All Phase-03 durability/at-most-one claims rest on source inspection. Plan success criteria unmet; blocks acceptance and Phase 07 device sync. Needs isolated Mongo (version/topology/journal) + previous Hub binary.

No new security/data-loss defects found in code.

## Warnings
1. `get_strict_collection` runs `hello` on **every** push/pull (extra RTT/request) and `Err(_) => false` falls back to standalone w1/local — **fail-open on durability policy** if hello transiently fails on a replica set (inconsistent with admission's fail-closed). Fix: derive topology once at admission (cache with `(app,epoch)` entry) or return `NotAdmitted`/Internal on hello error.
2. Remaining fail-open coercions on corrupt rows: `classify_reread` (`:610-611` revision/deleted default, no lastOp-invariant check); pull `data` `unwrap_or(json!({}))` (`:740-741`); update createdAt immutability silently skipped when `data` missing/non-doc or `createdAt` missing (`:455-469`, `unwrap_or(0)`); `confirm_replay` `acceptedRevision.unwrap_or(current_rev)` (`:918-920`). Negative `revision` i64 cast `as u64` unchecked. Fail closed (Internal/RetryableFailure) for consistency with brief item 4.
3. Admission still not fully exact: index `hidden`, collection `type` (view/timeseries/clustered via `options`) unchecked; `list_collection_names()` full-DB list redundant with `list_collections().filter` (perf on large Hubs, runs on cache miss). Admission cache `HashSet` never evicts/revalidates (plan-accepted, but a TTL index added post-admission is undetected until restart) — document.
4. Smoke script: with no creds each gate passes on 401 → prints "ALL CHECKS PASSED" proving nothing; with only one of key/JWT likewise. `--debug-local` parsed, unused though referenced by plan command. No strict happy path (create/pull/replay) or legacy-unchanged check. Should exit non-zero/SKIPPED when creds absent.
5. Provision script: `--token` (admin JWT) still on argv (env supported — drop flag); plaintext key echoed to stdout even when `--out` given (CI log leak); `mode: 0o600` only applies on file creation (pre-existing looser file keeps perms — add `chmodSync` or `flag:'wx'`); `GLEAN_ADMIN_TOKEN` fallback fine.
6. Admission bin: `--mongo-uri` on argv still accepted, silent `MONGO_TEST_URI` fallback and default db `glean_hub`; unknown args silently ignored; missing-value flags ignored. No test that helper == server validator (it calls same fn — OK by construction).
7. `register_strict_app` not retry-safe: orphan collection after any failure post-`create_collection` (admission/insert fail, concurrent same-app race → dup key 500 after check-then-insert). Orphans are inert but accumulate; document or best-effort `drop` on failure.
8. Runbook claims "Never accessible by legacy … cleanup jobs/notification writers" — structural (separate namespace) but unmeasured (plan §10). Qualify wording.
9. Perf: `legacy_sync_mode_guard` adds `_schemas` find on every legacy sync request for all Hub apps (+ dual_auth's own lookups); transient `_schemas` error now 500 for unrelated legacy apps. Acceptable; consider sharing schema via request extension.

## Suggestions
- DRY: `apply_operation` ~380 lines with ~14 copy-pasted `StrictOperationResult{…}`; add `result(op, digest, status)` helper; Create/Update data validation block duplicated; Update/Delete write paths differ only in `$set` (extract). Would shrink file ~35%.
- `serde_json::to_vec(&request)` payload cap re-serializes parsed body (measures wrong bytes, duplicates body-limit layer) — remove or rely on layer.
- Remove unused `StrictSyncError::Unauthorized`; `compute_digest` 10 args → struct.
- Axum `Json` rejections (415/422) not strict-coded on strong routes.
- Guard/dual_auth path extractors differ (`filter(!empty)` vs raw index `parts[2]`); both operate on raw encoded path so encoded-id bypass is closed, but pin with a mounted test.
- Move cursor hex-encode/decode into `models/strong_sync.rs` (DTO/cursor concern) to slim service.
- Evidence doc line 34 (`doggo-strict-sync.md` bullet) sits under "Runtime Environment" list; move to Documentation list.

## Positives
- Clean isolation: separate model/service/route/error/guard modules; legacy `register_app` signature and `AppListItem` untouched; `update_schema` filter `syncMode $exists:false` + disambiguating reread; registry published last; epoch checked before collection handle; user id only from auth; cursor scope-bound; dual_auth is outermost layer over legacy+strong.
- Shared `confirm_replay` with matched==1 requirement; error → RetryableFailure (conservative).
- Admission fail-closed incl. hello error and standalone non-primary; strict exact index allow-list.
- Evidence/docs now accurate about unexecuted gates; clippy clean; live gate hard-fails w/o URI.

## Metrics
- Tests 36 pass / 0 fail / 1 ignored; live-Mongo coverage 0 executed. Clippy 0. Type coverage n/a.

## Recommended actions
1. Provision isolated Mongo (standalone + replica set) and implement/run §9/§10/§12 suites; record real evidence (blocking).
2. Make topology a one-time admission-derived fact; remove per-request `hello` and fail-open fallback.
3. Fail closed on remaining coercions (`classify_reread`, pull data, createdAt, acceptedRevision).
4. Smoke: fail/SKIPPED without creds; strict happy path; drop/implement `--debug-local`.
5. Provision: drop `--token` argv, suppress stdout key when `--out`, chmod 0600.
6. Admission bin: drop `--mongo-uri` flag + `MONGO_TEST_URI` fallback.

## Unresolved questions
- Replica-set support required for Phase 03 or standalone only? (code supports both; neither verified live.)
- Isolated Mongo (version/topology/journal), previous Hub binary, legacy-client fixtures available for §9–§12?
- Key custody final: script `--out` file (0600) vs Phase 08 operator flow?
- Is glean-hub worktree's unrelated dirty diff outside Phase 03 ownership?
