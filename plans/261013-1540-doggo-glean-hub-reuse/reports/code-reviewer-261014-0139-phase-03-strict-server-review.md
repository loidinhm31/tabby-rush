# Phase 03 Code Review — Doggo-only strict server + legacy isolation

Score: **4/10** — not acceptable. Code compiles, unit tests pass, but durability contract, live-Mongo gates, key custody absent/false claims.

## Scope
- 26 registry paths (glean-hub-sync plugin, glean-hub docs, doggo scripts/docs/schema). ~3.1k LOC Rust + 250 LOC mjs + docs.
- Plan: `phase-03-hub-schema-compatibility.md` (status line updated).

## Validation run
| Cmd | Result |
|---|---|
| `cargo test --locked --manifest-path .../glean-hub-sync/Cargo.toml strong_sync -- --nocapture` | 12 pass. Live-Mongo test printed `[SKIP-OR-REPORT] MONGO_TEST_URI not set` and returned ok (vacuous) |
| `cargo test --locked` (crate) | 32 pass / 0 fail |
| `cargo clippy --locked --all-targets` | 4 lib warnings (collapsible if, manual Option::map, is_multiple_of, RangeInclusive::contains) + too_many_arguments(10) on compute_digest |
| NOT run | live Mongo, mounted router, old-binary, legacy-client, node smoke/provision (no MONGO_TEST_URI / Hub origin) |

## Critical
1. **No write/read concern anywhere** (grep: zero hits for WriteConcern/ReadConcern/selection_criteria in crate). Plan §4/§7: strong handles w1,j:true (standalone) / majority+journal (replica), primary/majority reads, topology+journal admission. Code uses default `db.collection()`. `confirmationNonce` replay write is not durably journaled; admission never checks topology. CHANGELOG, CLAUDE.md, doggo-strict-sync.md, hub-compatibility-acceptance.md all claim "journaled write concern" → **false documentation**.
2. **Mandatory real-Mongo suite missing + vacuous pass.** `strong_sync_tests.rs:test_strong_sync_live_mongo_acceptance_gate` returns Ok when `MONGO_TEST_URI` unset (plan §9: HARD FAIL, never skip). Only content: register + admission. Zero tests for push/pull/CAS/replay/opIdReuse/tombstone/overflow/cursor/epoch-forgery/index-collation-TTL failure. `strong_sync_route_tests.rs` tests only scope guard w/ stub handler: no mounted `/api/v1`, no legacy guard 426, no auth, no bypass/cleanup-job, no tolerant-list, no strict update rejection. Plan §9-§12 acceptance unmet; "all green" is not evidence.
3. **Provision script discards API key.** `hub-schema-provision.mjs` prints only `apiKeyConfigured: true`; the key (hashed server-side, unrecoverable) never stored → registered Doggo app unusable. No protected custody. Also admin JWT via `--token` argv (leaks to ps/history); re-run after partial success hard-fails (AppAlreadyExists, not idempotent). Read-back only checks `schema.todos` exists, not exact columns/immutability; logs "admitted" though read-back ≠ admission.
4. **Admission validator too weak / epoch unvalidated** (`app_service.rs validate_strict_admission`). Scan index accepted if the 3 keys are each `1` — key *order* not compared (Document order ignored), index collation/partialFilter/sparse/hidden not checked, extra indexes (e.g. unique) allowed, `list_collections` returning None silently passes. Plan §3: "exact index specs". `strict_collection_name` interpolates registry epoch with only `-` stripped; epoch is never validated as canonical UUID in `authorize_strict_access` or admission → malformed/tampered registry value builds arbitrary collection name (defense-in-depth gap vs plan "check canonical epoch BEFORE any collection handle").
5. **`is_canonical_uuid` accepts non-hex** (`strong_sync_service.rs`): any `[a-z0-9]` passes (`g..z`). Violates "reject noncanonical op/row identity"; stored `_id` identity polluted.

## Warnings
- `legacy_sync_mode_guard` fails **open**: `if let Ok(schema)` — DB error/lookup failure forwards to legacy handler. Should fail closed (503/500). Also path parse duplicates dual_auth's (guard filters empty segments, dual_auth raw `split('/')`); percent-encoded ids only safe because dual_auth rejects them first — undocumented coupling, no test ("encoded app" required §10).
- Per-request fixed-schema revalidation (plan §5) absent: `authorize_strict_access` never calls `validate_strict_schema(app_schema.tables)`; cache hit skips all checks permanently (never evicted, never re-verified after index drop; unbounded set, though keyed per app+epoch).
- Admission cache lives in `AppService`, cloned via `AppService::new` in 3 places (management groups, route_groups, start_jobs) → separate caches; `mark_admitted` in `register_strict_app` is therefore useless for the sync routes. Belongs in `StrongSyncService`.
- `get_app_schema` any error → `AppNotFound` 404 (masks DB outage as missing app).
- Push batch: `?` on DB errors aborts whole batch after earlier ops already applied → client gets 500 without per-op results; should degrade to per-op `retryableFailure`.
- Insert error handling: any `insert_one` Err → `classify_reread`; transient failure + row absent returns `MissingRecord` for a *create* (misleading); WC-error-but-applied returns `Replayed` with **no** confirmation write (plan §7 post-WC-error retry). Replay-confirm `matched_count==0` returns `RevisionConflict` (plan: unresolved/retryableFailure).
- Corrupt-row tolerance: `get_i64("revision").unwrap_or(0)`, `get_bool("deleted").unwrap_or(false)`, `createdAt unwrap_or(0)` silently fail-open; should NotAdmitted/internal.
- Request DTOs lack `#[serde(deny_unknown_fields)]` (StrictOperation, StrictTodoData, Push/Pull) — plan §1 "unknown mutation fields rejected". Axum JSON rejection bodies (422/415) not strict-coded.
- Pull returns `cursor: None` on empty page even when request had cursor → client may restart full scan. Echo request cursor.
- `strong_sync_admission` bin: `--mongo-uri` on argv (plan: never URI argv), falls back to `MONGO_TEST_URI`, default db `glean_hub` silent; docs show `--mongo-uri $MONGO_URI`. Rejected-path prints `e.to_string()` (driver errors can include host info). No test that bin == server validator, nor packaged path/provenance recorded.
- `CLAUDE.md`: closing code fence of module tree deleted (diff `-```` ) → rest of doc renders as code. Changelog says `AppSchema::update_schema` (it is `AppService`).
- Smoke script weak: gate1 passes on 401/426/503; gate3 passes on 401; default fake `test-api-key`; `--api-key`/`--token` argv; no strict happy path, no epoch-mismatch 409, no legacy-app unchanged check. Evidence summary lists deliverables only; does not state runtime gates unexecuted (plan: distinguish static vs actual).
- Admission perf: `list_collection_names()` (full DB scan) + `list_collections` filter — drop the first.

## Suggestions
- DRY: `apply_operation` ~470 lines with ~12 copy-pasted `StrictOperationResult{...InvalidRecord}`; add `fn result(op, digest, status)` helpers; split validate / replay / write paths; Update & Delete branches near-identical.
- `compute_digest` 10 args → small struct (clippy). Re-serialization `serde_json::to_vec(&request)` for size check is redundant to body-limit layer and measures wrong bytes.
- `StrictSyncError::Unauthorized` unused; remove (YAGNI).
- Replace hand-rolled hex cursor with documented base64/HMAC-free simple format + validate `after_row_id` canonical; `hex` parse via `from_str_radix` on slices may panic on non-ASCII (`&hex_part[i..i+2]` char boundary) — **panics on multibyte cursor input** (500/handler panic). Use `is_ascii()` guard or `hex` crate.
- Add `[[bin]]` doc/provenance; run admission through same function with a trait for testing.

## Positives
- Clean separation: new error/model/service/route/guard modules; legacy constructor, `register_app`, `AppListItem` untouched; `update_schema` filter `syncMode $exists:false` + disambiguating reread is tidy.
- Registry-last publication; epoch check precedes collection handle; cursor scoped to protocol/app/user/epoch/table; `_id` = userId:rowId derives user from auth only; digest vectors test matches Kotlin vectors.
- Layer order correct (dual_auth outer, mode guard inner); strong routes inherit rate/body limits; admin `/strict-apps` behind admin_role_middleware.

## Metrics
- Tests: 32 crate pass; 0 meaningful live-Mongo/mounted-router/old-binary evidence.
- Clippy: 5 warnings. Type coverage n/a.

## Recommended actions (priority)
1. Add per-handle write/read concern + topology/journal admission; or amend docs to drop claims and get explicit scope approval.
2. Real-Mongo suite: hard-fail without URI; cover CAS/replay/opIdReuse/cursor/admission failures; mounted router tests (legacy 426, unknown mode, auth, encoded app, bypass).
3. Provision script: protected key output (stdout-once to file 0600 / env), idempotent resume, token via env only, exact schema read-back.
4. Fix `is_canonical_uuid`, validate epoch canonical before name build, stricter index spec compare incl. order/collation/options, fail-closed guard, deny_unknown_fields, cursor hex panic.
5. Fix CLAUDE.md fence, docs claims, evidence doc statuses.

## Unresolved questions
- Is an isolated Mongo (version/topology/journal) available to run §9-§12 gates, and who supplies old-binary artifact?
- Accept w1/j:true only for standalone, or require replica majority? Needed before implementing concern config.
- Should API key custody be handled by script (file/secret store) or by Phase 08 operator flow?
- Does Kotlin `validateTitle`/whitespace set match Rust `validate_title` beyond digest vectors (no cross-language title vectors seen)?
