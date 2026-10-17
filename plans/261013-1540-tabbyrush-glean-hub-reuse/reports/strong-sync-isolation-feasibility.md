# Strong-sync isolation feasibility (TabbyRush × Glean Hub)
Date 2026-10-13. Agent `StrongSyncIsolation`. Static read only; no build/test/run, no source edits, no live probe. Hub checkout `094a183`; `glean-hub-plugin/glean-hub-sync` has no working-tree diff (`git status` clean for plugin/server/core dirs). "Confirmed" = read in source; **nothing is runtime-proven**. Searches were grep-scoped (`**/*.rs`, admin UI, core clients), so claims below are "found in searched scope", not exhaustive proof.
Paths: `S`=`H/glean-hub-plugin/glean-hub-sync/src`, `N`=`H/glean-hub-plugin/glean-hub-app-notification-events/src`, `SRV`=`H/glean-hub-server/src`, `UI`=`H/glean-hub-app/src`, `H`=`/home/loidinh/WS/glean-hub`.
Status: **feasibility recommendation + proposed scope expansion**. Existing Phase 03 permits only the narrow legacy insert-only repair; nothing here is frozen or applied.

## Verdict
Feasible, **smallest safe isolation = Option A: schema-selected immutable mode + new route group + separate strong collection** (name outside the legacy `app*` namespace). Option B (patch shared `app*` collection) is **not** safe for "existing apps unchanged": legacy purge/restore/cleanup/notification writers and any rolled-back binary would still reach strong rows. A needs no change to the legacy push/pull/delta/purge/restore semantics, Rust/TS clients, admin UI, or `glean-hub-server`.

## Dispatch/bypass inventory (every path that reaches an `app*` row)
| # | Path | Evidence | Reaches strong rows under A | Under B (shared coll.) |
|---|---|---|---|---|
|1|push: preflight, `$set` upsert for new row (`update_one(..).upsert(!existing)`), CAS only when row existed|`S/services/sync_service.rs:159-452` (`:359-374`, `:401`)|No (collection derives from `collection_name(app_id)`)|Yes; must gate|
|2|delta = push then pull (calls `push_sync`)|`sync_service.rs:648-668`; `S/routes/sync.rs:44-60`|No|Yes|
|3|pull (sort `(syncedAt,rowId)`, sharing `$or`)|`sync_service.rs:456-534,584-645`|No|Yes (reads)|
|4|`POST /sync/:app/purge`: **no admin check**, any granted user, purges app-wide expired tombstones|`S/routes/sync.rs:68-80`; `sync_service.rs:671-699` (filter `appId,deleted:true,deletedAt<cutoff`)|No (targets legacy name)|Yes: **would delete deletion fences**|
|5|`POST /sync/:app/restore`: undelete + `$inc version` + new `syncedAt`, no op-id/fence logic|`S/routes/sync.rs:96-112`; `sync_service.rs:705-760`|No (NotFound)|Yes: bypasses CAS/last-op|
|6|Daily cleanup job: `find({})` over **all** `_schemas`, purges each app by retention (default 60 d)|`S/jobs/deleted_records_cleanup.rs:57-98`; started `S/lib.rs:194-206`; `SRV/plugins/mod.rs:454-458`; `SRV/plugins/runtime_controller.rs:195`|No (inert; see "unverified")|Yes|
|7|Notification-events plugin writes **directly** to `app*` collections for apps whose schema has `tables.notificationEvents`: claims, `$inc version`, `syncedAt` as BSON DateTime (sync stores ISO strings), indexes, multi-doc txns|`N/repository/event_repository.rs:403-405,434,782,1005,1427,1906`; `:1040,1110` (`syncedAt` BsonDateTime); txn `:1846-1862`|No unless strong schema declares that table (forbid it)|Yes if table present|
|8|Registry: `register_app` creates legacy `app*` indexes `app_service.rs:76`; `update_schema` recreates when `sharing` supplied `:122-126`; `ensure_indexes` `:27`|`S/services/app_service.rs`|Must branch by mode (skip legacy indexes, create strong indexes pre-admission)|n/a|
|9|`SyncPlugin::start_jobs/route_groups/create_services`: sole wiring; server mounts `sync_data` with rate limit 60/s, `plugin_gate "sync"`, 5 MiB body|`S/lib.rs:194-253`; `SRV/main.rs:307-320`|New group merges into `sync_data`, inherits all three|—|
|10|Tests seed/read collections directly|`S/services/sync_generation_tests.rs`; `sync_service.rs` tests `:986+`|n/a|n/a|
Not found in searched scope: any `glean-hub-server` code touching `app*` collections (`SRV` collection grep: users, refreshTokens, accessRequests, emailVerifications, plugin state, notificationDeliveryIdempotency only); hard-coded `"app…"` collection literals. **Not covered**: operator Mongo shell/backup/restore/drop, other repos, future writers, other deployments.
Callers of legacy routes: Rust client `glean-hub-core/glean-hub-sync-client/src/client.rs:255,275,305` and TS `glean-hub-core/glean-hub-sync-client-types/src/client.ts:239,255,280` call only push/pull/delta by configured `appId`; **no purge/restore callers found** in app/core/k8s/project dirs searched.

## Why A isolates (and where it does not)
- Collection selection: every searched writer builds the name via `SyncService::collection_name` (`sync_service.rs:128-152`, always `"app"+…`). A strong collection whose name never starts with `app` cannot be hit by legacy push/delta/pull/purge/restore/cleanup/notification code, **including a rolled-back binary**, with no guard needed. This is the decisive property B cannot give.
- Legacy purge/cleanup on a strong appId target the (unused) legacy collection: expected 0 deletions, but "`delete_many` on a missing collection is inert" is **unverified**; add test. Cleanup job needs no change (optional mode-skip only for log clarity).
- Isolation is of **rows**, not of metadata, deploys, or operators: DB-wide drop, collection enumeration, backup/restore scripts, future writers stay audit boundaries.
- Existing collision hazard to avoid copying: `collection_name("a-b")` and `("aB")` both yield `appAB` (`sync_service.rs:128-152`); `register_app` only enforces exact `appId` uniqueness and does not validate charset (`app_service.rs:56-82`). Strong naming must be injective over a validated appId charset (e.g. `[a-z0-9-]{1,40}`) enforced **at strong registration only**; unique index `(userId,tableName,rowId)` created **before** the app can accept writes.

## Mode encoding and registry serialization (smallest)
- `AppSchema` (`S/models/app.rs:31-44`) is stored directly via serde into `_schemas`; `#[serde(rename_all="camelCase")]`, `sharing` is `default+skip_serializing_if None`. Add an optional string/enum `sync_mode` the same way (`#[serde(default, skip_serializing_if = "...")]`) so a legacy document is **unchanged in document shape** (not claiming byte equality). Parse tolerantly: an unparseable enum would make `get_app_schema` fail with 500 for that app and `list_apps` (`app_service.rs:39-53`, `try_next()?`) fail for **all** apps.
- Reserved table-name key is a non-starter: `SchemaDefinition = HashMap<String, TableSchema>` (`app.rs:29`) is iterated by validator/notification filter `tables.notificationEvents`.
- Set only in `register_app` (`app_service.rs:56-82`). `update_schema` is a partial `$set` of `tables`, `updatedAt`, optional `sharing` (`:104-116`), so old and new binaries preserve an existing mode; new code must explicitly **reject** a `syncMode` in the update body and reject `sharing` for strong apps. (Old binaries silently ignore unknown fields: `RegisterAppRequest`/`UpdateSchemaRequest` have no `deny_unknown_fields`; only `admin_plugins.rs:62` does.)
- **Registration read-back is mandatory**: an old/unknown-version Hub silently registers the app as legacy and returns 200 with a key (shown once, `app_service.rs:54-80` per prior review H3). Runbook must `GET /apps/:id` and require the mode echoed (`GetSchemaResponse` `api.rs:46-54` gains the field; old binary omits it ⇒ treat as failure, rotate nothing, use a new appId if mis-registered).
- Admin UI: `UI/services/sync-admin-service.ts:41-45,78-88,95-102,118-131` sends only `{appId,schema,sharing}` / `{schema,sharing}` and cannot create strong apps; strong registration stays a curl/runbook admin step (already planned in Phase 03). UI edits of a strong app's tables remain live immediately (`update_schema` has no versioning, prior H4); no UI change required for isolation.
- Rust/TS clients and `AppListItem` stay unchanged (do not add fields to list/legacy DTOs).

## Route namespace and auth gate
- Recommended: `/sync/:app_id/strong/{push,pull,...}` merged into the existing `sync_data` router. `extract_app_id_from_path` (`S/middleware/dual_auth.rs:94-103`) only recognizes `/sync/<app>/…` (else falls back to `X-App-Id`), so a differently prefixed path would require editing a **shared** middleware. Axum literal segment `strong` does not collide with `/sync/:app_id/push`.
- Gate (new middleware layer or helper on strong group only): same key+JWT checks, then require `schema.sync_mode == strong`; else reject. **Never 401**: `Unauthorized/AuthError/InvalidApiKey` map to 401 (`S/error.rs:78-93`) and the contract's 401-triggers-refresh rule would burn refresh tokens (contract-review B1). Use existing `BadRequest/InvalidInput/NotFound` (400/404) or add one variant (touches `error.rs`); client treats any non-2xx on strong routes as `ProtocolIncompatible`, **no legacy fallback**.
- Auth/app-id coherence risk (pre-existing): middleware authenticates the **raw** path segment, handler uses decoded `Path<String>`; an appId containing `%xx` could diverge. Strong registration charset rule removes it for strong apps; add a mounted-router test (below).
- Legacy-route guard for strong appIds (new binary rejects push/pull/delta/purge/restore): defense in depth, **optional**; without it a legacy call writes the unused legacy collection (split-brain, not corruption). If added, it is a shared-path edit (one mode check after the existing schema load) → requires existing-app smoke. `purge`/`restore` do not load the schema today (`sync_service.rs:671-760`), so the guard there is additional DB read or middleware-level.

## Files owner03 must own if A is adopted (no code written here)
Must: `S/models/app.rs` (mode field), `S/models/api.rs` (`RegisterAppRequest`, `GetSchemaResponse`, strong DTOs), `S/services/app_service.rs` (register branch, indexes, update-rejects-mode, mode accessor), **new** `S/services/strong_sync_service.rs` + `S/services/mod.rs`, **new** `S/routes/strong_sync.rs` + `S/routes/mod.rs`, `S/lib.rs` (`route_groups` merge into `sync_data`, `:209-228`), `S/middleware/dual_auth.rs` + `middleware/mod.rs` (strong-mode gate), new/extended tests (`sync_generation_tests.rs` pattern, `MONGO_TEST_URI` hard-fail), plugin CLAUDE/docs/CHANGELOG, runbook.
Conditional: `S/error.rs` (new status variant only if 400/404 insufficient); `S/jobs/deleted_records_cleanup.rs` (mode-skip, optional); `S/routes/sync.rs`/`sync_service.rs` (only for legacy-route guard).
No change: `SRV/main.rs` (mount at `:307-320` inherits limits/gate), `SRV/plugins/*`, `N/*`, `glean-hub-core/*` clients, `UI/*`.
Collision rule with Phase 03: current Phase 03 owns `sync_service.rs` for the legacy insert-only fix; A leaves it untouched, so the two do not overlap unless the optional legacy guard is added.

## Rollback rule
1. Strong rows live only in the strong collection; **never migrate/copy** between strong and `app*` collections in either direction; strong collection and indexes are never dropped on rollback.
2. `sync_mode` is set once; no endpoint mutates it. Old binaries ignore the field and preserve it across `update_schema` `$set`; manual DB edits to `_schemas` for the strong appId are forbidden.
3. Server rollback below the strong-capable build: strong routes 404 ⇒ TabbyRush goes `ProtocolIncompatible`, local data kept, **no fallback to legacy routes**. The old binary exposes legacy routes for the same appId on a legacy collection that is now a **divergent legacy namespace**: clients with the key/grant could create parallel data there. Strong rows stay unaffected; roll-forward must **not** import that data and must not reinterpret missing/unknown mode as permission to read `app*` data (missing mode ⇒ strong routes reject).
4. Roll-forward checks before admitting writes: strong unique index present, `_schemas` mode present, registry read-back matches.

## Live topology limits (unprobed; Hub 502/localhost refused per parent)
- Mongo version/topology unknown. Source-level evidence only: the notification plugin uses multi-doc transactions with majority concern (`N/repository/event_repository.rs:1846-1862`), so that plugin's code assumes transaction support. **[INFERENCE]** only; deployed topology is unverified and no plan guarantee may rely on it.
- Limitation: a secret-bearing configuration file was inadvertently inspected during this investigation, contrary to the task instruction; no value was copied into this report and nothing from it informs any conclusion here.
- Per-row safety needs only single-doc atomic ops: `insert_one` + unique index, `update_one/find_one_and_update` with exact-value filter, no transactions, no pipeline updates (4.2+), no change streams → feasible on standalone too.
- Durability across primary failover/rollback of acknowledged writes depends on write concern/journal; legacy sync collections use driver defaults (no write concern found in `S/`). Owner03 must set majority write concern on the strong collection handle **and** document that standalone/unknown topology cannot give that claim; timeouts remain UNCERTAIN regardless.
- `delete_many`/upsert/duplicate-key behavior must be characterized on the real Mongo version in the isolated test (prior review: upsert-vs-E11000 version dependent).

## Guarantee bounds (isolation-relevant)
- Per-row CAS + insert-only is sufficient for per-row concurrency safety; **not** durable ack history. `lastOpId`+hash recognizes a replay only while it is the row's last op; after another write the outcome is unknown. Deletion fence must live in the same uniquely keyed doc, no TTL/purge path; update CAS must also require live row (matching a fence's rev must not undelete). Stable-`rowId` scans stay non-snapshot (convergence needs a later complete round).
- Explicitly exclude from the smallest version: sharing, notification-generated writes, restore, migration of existing apps/data, `notificationEvents` table in a strong schema.

## Tests required (not run)
1. Bypass matrix on a populated strong collection with deletion fences: legacy purge route, legacy restore, cleanup job tick (`cleanup_deleted_records`), notification-plugin repository functions, legacy push/delta on same appId ⇒ strong rows/fences unchanged; legacy purge on missing collection returns 0.
2. Legacy equivalence: legacy app register/update/push/pull/delta/purge/restore outputs unchanged; schema doc shape unchanged when mode absent; existing-app smoke.
3. Mode: immutable on update, rejected sharing, tolerant parse of unknown mode (`list_apps` still works), read-back echoes mode, missing mode ⇒ strong route rejected.
4. Mounted-router auth over real `/api/v1` mount: strong route on legacy app ⇒ non-401 rejection; `%xx`/encoded appId, conflicting `X-App-Id`, wrong key, missing grant; cross-user/cross-app isolation.
5. **Actual old-binary test** (previous build, isolated DB): `update_schema` preserves mode; legacy writes go only to legacy collection; strong collection untouched; roll-forward refuses import.
6. Strong-core concurrency: concurrent create/update/delete, lost response + replay after intervening write, fence-resurrection attempt, unique index absent at startup ⇒ fail closed.
7. `MONGO_TEST_URI` unreachable must **fail** the gate (existing tests silently pass: `sync_service.rs:774-795`, `sync_generation_tests.rs:11-26`).

## Risks / limits
- Unknown Hub version: silent-ignore of `syncMode` ⇒ read-back runbook step is load-bearing.
- Registry/other consumers reading `_schemas` (`list_apps`, cleanup, notification schema scan `event_repository.rs:412-433`) must tolerate the new field; a strict new field type would break them.
- Optional mode gate on legacy routes touches shared hot paths; A's isolation does not depend on it.
- Operator/DB-level actions and non-searched repos are not covered.

## Unresolved questions
- Is the legacy-route guard for strong appIds wanted (fail-closed) or acceptable to omit (inert/split-brain only)?
- Add a dedicated status variant vs reuse 400/404 for "wrong mode"?
- Deployed Mongo version/topology and acceptable write-concern claim (needs operator input).
- Fixed collection name vs `strong…<appId>` derivation (injective, validated charset) — design owner decides.
- Deployed Hub = `094a183`? (strong-registration read-back depends on it).
