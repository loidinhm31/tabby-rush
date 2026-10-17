# Strict-v1 (Option A) contract review — TabbyRush × Glean Hub
Date 2026-10-13. Agent `StrictContractReview`. P=`plans/261013-1540-tabbyrush-glean-hub-reuse`. Read-only static review; no build/test/run/secrets; nothing runtime-proven.
Reviewed: the parent's rewritten `P/contracts.md` (80 lines; §4–§9 strict) against P/reports strong-sync-{design,isolation}-feasibility, strong-sync-feasibility, mongodb-feasibility-evidence, design-decisions. `architecture.md` and `phase-03` not yet revised when read (phase-03:20-23,58 still the narrow legacy-upsert repair; planner owns). Hub (`S`=`H/glean-hub-plugin/glean-hub-sync/src`) re-read: `services/app_service.rs`, `models/app.rs`, `middleware/dual_auth.rs`, `error.rs`, `lib.rs:180-260`, register/update callsites.

## Verdict
Draft is correct on: per-row CAS/insert-only, server-owned revision, `expected+1` ack, real confirmation write, no `lastOp` in pull, scan-start identity set, permanent tombstones with `{}` data, epoch in all DTOs, legacy-route rejection non-401, no transactions. **Seven items remain open (R1–R7); two are blockers of correctness (R1,R2).** No new transaction/receipt/feed design is needed or proposed.

## Source facts behind corrections (verified)
- `app_service.rs:64-77`: legacy `register_app` inserts `_schemas` doc FIRST, indexes AFTER. Callsites: `register_app` only `routes/apps.rs:43`; `update_schema` only `routes/apps.rs:78`.
- `error.rs:78-93`: 401=`InvalidApiKey|Unauthorized|AuthError`; 409 only `AppAlreadyExists`; no 426 variant; body `{error}` only, no machine code.
- `dual_auth.rs:41-54,94-103`: appId from RAW path `parts[2]`; userId=`claims.sub`.
- `app_service.rs:45`: `list_apps` deserializes every `_schemas` doc (`try_next()?`).
- `lib.rs:215-219`: legacy router `.layer(dual_auth)`; `.layer` binds only routes already added ⇒ a guard layer can wrap legacy routes alone (strong router merged after) with NO edit to `routes/sync.rs`/`sync_service.rs`. `lib.rs:185-187`: `initialize` returns `Result` ⇒ per-app admission failure must not propagate (aborts Hub init).
- Notification plugin writes direct to `SyncService::collection_name(app_id)` (`event_repository.rs:434,782,1006…`); `_strict_v1_*` never starts `app`.

## Status of earlier concerns vs current draft
| # | Concern | Draft | Remaining |
|---|---|---|---|
|digest|canonical, length-framed|§5 L42 good|R5 minor|
|replay confirm|real nonce write|§7 L53 good|R3|
|scan promotion|known-before-start set|§8 L60 good|R4|
|tombstone|`{}`, no compaction, deleted:false|§7/§8 good|R6|
|namespace|`_strict_v1_<len>_<app>_<epoch32>` injective (length prefix; appId has no `_`)|§4 L36 good|R1|
|legacy isolation|non-401 reject, namespace outside `app*`|§4 L36 good|R7|
|index admission|unique `(userId,tableName,rowId)`|§4 L35 partial|R2|
|ambiguity|UnknownSuperseded|§6 L47 partial|R3|

## Open items — corrections needed in contracts.md (minimal)
**R1 (BLOCKER) Epoch authority + server-side epoch validation.** §4 L36 says namespace is "captured from request epoch" but no rule makes the server compare it with the ACTIVE epoch first. As written a user-chosen UUID derives a collection name and an insert can auto-create arbitrary `_strict_v1_*` collections. Add: every strict request validates canonical lowercase UUID, then `epoch == registry active epoch`; else HTTP 409 `epochMismatch` BEFORE any namespace/DB-collection use. §9 L64 requires "fresh epoch … import … reopen" but never says where the server learns the new epoch; isolation report rule (manual `_schemas` edit forbidden) contradicts it. Pin: registry field `strictEpoch` changes only through the documented offline maintenance step (writers stopped, new namespace created+admitted first, then the single permitted `_schemas` edit). "Import approved restored content" has no tool and no rev assignment: either state "new epoch starts empty; client may explicitly re-create from retained local copy as new creates (duplicate/resurrection warning)" or accept an operator import procedure as undesigned/out-of-scope. Decision for parent.

**R2 (BLOCKER) Index/namespace admission under-specified.** Required unique `(userId,tableName,rowId)` is a droppable secondary index and is evaluated under the collection's default collation. Add: admission (per app, cached per app+epoch, lazy/async, never from `SyncPlugin::initialize`) verifies via `listCollections/listIndexes` — collection exists, simple/absent default collation, no validator/capped, exact key+`unique:true`, no partial/sparse/collation on that index, NO TTL (`expireAfterSeconds`) index anywhere in the collection, journal/WC policy matches mode. Strict writes refuse until admitted (otherwise `insert_one` silently auto-creates an unindexed collection). Cheaper alternative worth considering: make `_id`=`userId(24 hex)+":"+rowId` so create-uniqueness cannot depend on an operator-droppable index (scan = `_id` range). Registry doc inserted LAST or marked unadmitted until verification passes (legacy order is schema-first, `app_service.rs:64-77`); registration retry after partial setup must be idempotent for the same epoch/namespace.

**R3 (HIGH) Outcome/ambiguity table.** §6 gives machine codes; add classification:
- Server returns facts only; it cannot know prior dispatch. `current` in `revisionConflict/deletedRecord/missingRecord` must carry `{revision,deleted}` (no `lastOp`).
- Status precedence (deterministic): invalid ⇒ `invalidRecord`; lastOp(opId,digest) matches ⇒ confirm then `replayed`; same opId different digest ⇒ `opIdReuse`; row absent & expected>0 ⇒ `missingRecord`; revision≠expected ⇒ `revisionConflict`; tombstone ⇒ `deletedRecord`; create on existing ⇒ `revisionConflict`.
- `retryableFailure` (incl. write-concern error AFTER apply, confirm timeout) = MAYBE-applied; operation stays UNCERTAIN; never a rejection.
- Client classification using its durable DISPATCHING marker: never dispatched + mismatch ⇒ known stale base. Dispatched + `current==expected` ⇒ not applied, retry safe. Dispatched + `current==expected+1`, lastOp not ours ⇒ **provably not applied** (exactly one op can take slot expected+1) ⇒ plain conflict. Dispatched + `current>expected+1` ⇒ `UnknownSuperseded` (blocks later ops of that row; successors have no frozen base until dispatch). This tightening is optional but shrinks unknowns; if rejected, say so.
- Confirm result uses `matched_count` (not modified_count/visibility read); applies on EVERY replay path incl. duplicate-key create reread and same-request WC error; assert invariant `revision==lastOp.acceptedRevision` (violation ⇒ corruption pause); replica confirm+scan reads use primary `readConcern majority`, admission checks `writeConcernMajorityJournalDefault`.
- Delete intent vs observed tombstone (not ours) ⇒ intent satisfied by remote state (not an ack, not a conflict); edit vs tombstone ⇒ conflict with CopyAsNew.

**R4 (MEDIUM) Scan details.** §8 add: epoch change/mismatch mid-scan discards staging; staged rev < base keeps base; row in scan-start set `K` absent ⇒ ProtocolIncompatible/reset-suspect pause (no delete); cursor opaque `{protocol,app,epoch,table,afterRowId}` need not be signed (owner always from JWT; cursor cannot widen scope) — keep L58 wording, avoid adding HMAC. Page reads on replica use majority.

**R5 (MEDIUM) Digest/identity edge cases.** §5 add: opId canonical lowercase UUIDv4, reject non-canonical (else case-variants become distinct digests); domain tag is length-prefixed `strict-v1`; `completed` byte 0/1; userId = exact JWT `sub` string; client resends the PERSISTED frozen normalized fields after restart (never re-derived from editor state); server validation policy mismatch ⇒ permanent `invalidRecord` (known-not-applied, never stored in `lastOp`); state whether `createdAt` is server-immutable on update (recommend reject change); unknown fields rejected (already stated).

**R6 (MEDIUM) Tombstone/revision.** Keep delete = one CAS writing `{}`+`deleted:true`; state explicitly NO separate compaction job (design report still mentions compaction; contract correctly omits). Delete of existing tombstone ⇒ `deletedRecord`, no revision bump. Max revision 2^53-1 rejected pre-mutation (stated). Unbounded per-user row growth: state as limit (no quota).

**R7 (HIGH) Registration entrypoint + request-level errors.**
- §4 L34-35 uses same `POST /apps` with additive `syncMode`. Old/unknown binary ignores it, registers a LEGACY `tabbyrush`, returns the one-time key; recovery needs a new appId and rebuilt config. A separate admin route (not `/apps/strong`, which collides with appId `strong`; e.g. `/strict-apps`) + `AppService::register_strict_app`, keeping `register_app/update_schema` untouched, makes old binaries 404/405 ⇒ fails closed without burning a key. Read-back (L35) stays mandatory.
- `syncMode`/`strictEpoch` stored as `Option<String>` (`#[serde(default, skip_serializing_if)]`), parsed at gates ⇒ unknown value affects only that app; `list_apps` survives. Legacy guard rule: mode ABSENT ⇒ legacy; anything else ⇒ legacy routes rejected non-401; strict gate requires exactly `strict-v1`.
- §6 L48 uses HTTP 409/426 as codes; `SyncError` has no 426 and 409 means `AppAlreadyExists`, body has no code. Strict routes need a strict-local error type returning `{error,code}` (`epochMismatch`, `protocolUnsupported`, `notStrictApp`, `notAdmitted`, `invalidRequest`) so `error.rs` stays unowned; never 401/403 except genuine auth failures.
- Strict `update_schema`: reject ALL changes (tables, sharing, mode) not just "schema-incompatible"; strict code validates with fixed in-code todos policy and never reads stored `tables` (admin UI edits are live; old binary `update_schema` is a partial `$set`, mode survives).
- Legacy guard placement: new layer around legacy routes only (`lib.rs` + new middleware file), covers push/pull/delta/purge/restore (purge/restore load no schema); cost = one extra `_schemas` read per legacy sync request; requires existing-app smoke. Cleanup job calls the service directly (not routes) but targets legacy namespace only (inert); keep a test.

## Explicit limits (acceptable; keep stated, do not "fix")
- No causal receipt history; `UnknownSuperseded` remains for `current>expected+1` after uncertain delivery. No snapshot/feed; scans converge only after quiescence.
- Stale-backup/storage loss and operator DB actions undetectable by server; recovery = quiescence + fresh epoch; reopening a rolled-back epoch voids guarantees.
- In-flight old-epoch request commits OLD namespace; no reset-vs-accept ordering.
- lastOp cannot detect historical opId reuse with a new base; guarantee covers immutable frozen requests only.
- Rolled-back binary: strict routes absent ⇒ pause; legacy namespace for same appId can diverge, never imported.
- Standalone `{w:1,j:true}` no redundancy/HA; JWT grant lag; extractable app key; key shown once; no quota; row isolation excludes DB-wide drop/other repos/future writers.

## Ownership/plan consequences (for parent/planner)
- Phase03 external files: `models/app.rs`, `models/api.rs`, `services/app_service.rs`, new `services/strict_sync_service.rs`+`services/mod.rs`, new `routes/strict_sync.rs`+`routes/mod.rs`, `routes/apps.rs` (strict registration route), `lib.rs`, new legacy-guard middleware+`middleware/mod.rs`, tests/docs. Under R7 `sync_service.rs`, `routes/sync.rs`, `error.rs`, `dual_auth.rs`, `jobs/*` can stay unowned/unchanged. Exact Hub identifiers beyond those read must be verified by the implementer, not guessed.
- Phase02 freezes outcome enums/precedence (R3), digest vector (R5), epoch/error codes (R1,R7). Phase05: DISPATCHING marker, row-block on UnknownSuperseded, scan-start set, delete-satisfied rule. Phase07/08: epoch-rotation/maintenance runbook, old-binary registration 404 gate, no-legacy-fallback.
- Add gates (real Mongo): confirm-vs-successor race (matched 0), WC-error then replay, duplicate-key create replay, `expected+1` vs `>expected+1`, TTL/collation/dropped-index admission failure, forged epoch creates no collection, unknown-mode legacy rejection, `list_apps` tolerant, old-binary strict registration 404/405.

## Unresolved questions
1. How does rotation set the new epoch in the registry (single permitted manual edit vs other)? (R1)
2. New-epoch content: empty + explicit client re-create, or operator import with server rev1? (R1)
3. Adopt `_id` identity or keep secondary unique index + exact spec verification? (R2)
4. Adopt the `expected+1` provably-not-applied tightening? (R3)
5. Separate strict registration route accepted over additive `syncMode` on `POST /apps`? (R7)
6. Is `tabbyrush` already registered legacy anywhere; deployed Mongo/driver/journal config and Hub revision unverified.

## Parent decisions R1–R7 (received) — residual checks only
Accepted as stated (minimal R7, `_id=userId:rowId`, conservative UnknownSuperseded, no expected+1 inference, no delete-satisfied auto-clear, recovery script in phase08). Remaining consistency points, none reopening a decision:
1. **Imported rows `lastOp=null`, revision=1.** The invariant `revision==lastOp.acceptedRevision` must read "holds when `lastOp` present; imported/never-mutated rows have `lastOp=null`, revision=1". Confirmation write and replay lookup must treat null as "no replay possible" (opId never matches), not corruption. Imported tombstones: `deleted:true`, data `{}`, revision=1, no title.
2. **Script/server admission drift.** The JS script creates the new namespace and indexes; the Rust admission verifies them. Specs live in two languages: gate = run script output against the real server admission (and against a deliberately wrong index/collation/TTL variant). Imported `_id` must use the exact `userId+":"+rowId` format (24 lowercase hex + `:` + canonical lowercase UUID); script rejects any row failing it.
3. **Registry CAS vs cached epoch.** Server epoch/admission cache must have a bounded lifetime or be invalidated on mismatch; otherwise a surviving new-binary instance keeps serving the old epoch after the CAS. Maintenance runbook requires all strict writers drained BEFORE the CAS (already decided) and verifies post-CAS that old-epoch requests return 409 and the new namespace is admitted lazily (`503 notAdmitted` until verified). New namespace must be created+verified by the script BEFORE the CAS; abort leaves old pointer.
4. **Client mapping for new codes.** 409 `epochMismatch` ⇒ ResetRequired; 426 `protocolUnsupported` and 404/405 (old binary) ⇒ ProtocolIncompatible pause; 503 `notAdmitted` ⇒ transient pause/backoff, NOT ProtocolIncompatible, outbox preserved; 400 `invalidRequest` (request-level) ⇒ ProtocolIncompatible/DataInvalid pause, distinct from per-record `invalidRecord`. Bodies are `{error,code}` only on strict routes; ignore English text.
5. **update_schema strict rejection.** Put the strict check in the `update_one` filter (`syncMode` absent) and on `matched_count==0` do one read to return NotFound vs strict-rejection; avoids a read-then-write gap and keeps legacy behavior byte-identical when mode is absent.
6. **Delete-vs-remote-tombstone** now ends in a visible conflict with explicit user discard (per decision); UI/outbox must keep blocking that row's successors until the user acts, and Leave guard counts it.
7. **Gates to keep** (real Mongo, no skipped proof): forged epoch creates no collection; unknown mode rejected by legacy-only middleware yet `list_apps` works; old-binary strict registration 404/405; legacy purge/restore/cleanup/notification bypass leaves strict namespace untouched; script abort leaves old pointer; script import then client bootstrap shows ResetRequired for old-epoch outbox with no auto-rebase.
