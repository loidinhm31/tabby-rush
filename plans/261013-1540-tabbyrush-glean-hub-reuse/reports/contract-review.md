# Contract review — TabbyRush × Glean Hub reuse
Date: 2026-10-13. Reviewer `ReuseContractReview`. Static source read only (glean-hub checkout HEAD `094a183`, dirty state not checked). No build/test/run, no secrets, no source edits. **Nothing below is runtime-proven**; "confirmed" = read in source. Deployed Hub version unknown (health probes failed per design-decisions:50-52).
Paths: `H=/home/loidinh/WS/glean-hub`; `S=H/glean-hub-plugin/glean-hub-sync/src`; `A=H/glean-hub-server/src`. Docs: `C`=contracts.md, `AR`=architecture.md, `D`=reports/design-decisions.md.

## Verdict
Reuse design is viable and wire DTOs match. **3 correctness blockers (B1, B3, B4) + high items (incl. B2 scan semantics)** are documentation promises the source cannot support or rules that are missing. None require the user-declined sync protocol. Narrow Phase-03 correction is **not sufficient** for the semantics claimed (see §Phase03). Admin-token, key-rotation and deployment/schema items are required **limit statements**, not code blockers.

## Wire compatibility — confirmed OK (no change)
| Claim | Evidence |
|---|---|
| Push/pull DTO names, camelCase, `syncedRecords` w/ client `version` echo, `assumedServerVersion` optional, `deleted` default false, `clientTimestamp` optional | `H/glean-hub-core/glean-hub-sync-engine/src/protocol.rs:19-97,106-158`; ack version = `record.version` `S/services/sync_service.rs:426-430` |
| Conflict = `existing.version > assumed`; new row w/ assumed 0 conflicts if row/tombstone exists at preflight; tombstones included in preflight & pull | `sync_service.rs:180-199,330-347`; pull filter has no `deleted` clause `:584-645` |
| Pull strict-after `(syncedAt,rowId)`, page sort, `tables` filter, null checkpoint ok | `:487-500,620-644` |
| Full page ⇒ `hasMore=true` (limit+1 row fetched but ignored) | `:499,517-518` |
| Checkpoint echo on empty page; initial = epoch/"" | `:520-526`; `checkpoint.rs:32-37` |
| Auth: register `{userId,accessToken,refreshToken}`, login adds `apps,isAdmin`, refresh pair, `/me` fields; 200 everywhere | `A/services/auth_service.rs:212-324`; `A/routes/auth.rs:107-134` |
| Login does **not** normalize email; register does → client trim+lowercase is required, correct | `auth_service.rs:214` vs `:283-286` |
| Refresh re-reads apps (approval → new claim after refresh) | `auth.rs:116-119` |
| Sync authz = JWT `apps` claim + API key; admin gets no bypass; row owner = JWT `sub`; all writes/reads filtered by `userId` | `S/middleware/dual_auth.rs:57-87`; `sync_service.rs:359-364,615` |
| Validation: type/nullable only, unknown columns allowed, tombstone validation skipped, `integer` = i64/u64 | `S/services/schema_validator.rs:175-220`; `sync_service.rs:267-290` |
| Body limits: sync 5 MiB; auth 64 KiB; user group 128 KiB | `A/main.rs:296,304,320,361,545` |

## BLOCKERS

### B1 — 401 is overloaded; contract §3 and §2 mis-model it (High-risk for token burn + wrong UX)
Source: missing app grant → `SyncError::Unauthorized` **401, not 403** (`dual_auth.rs:77-82`; `S/error.rs:80`). Bad API key 401 (`:57-60`, `error.rs:79`). Bad/expired JWT 401 (`:72-74`, `error.rs:93`). Access-request routes wrap *every* service error in `SyncError::AuthError` → **401**: duplicate-pending (`A/services/auth_service.rs:444-448` `Conflict`), DB errors, lookup failures (`S/routes/apps.rs:160-163,172-176`; adapter `A/plugins/mod.rs:226-236`). No 403 exists on these paths.
Consequences: (a) C:19 "Duplicate pending request: reload own list" has no status to trigger on; (b) C:27 "401 → one refresh → one retry" burns/rotates the refresh token on every duplicate-request, wrong-key or no-grant 401, and with C:27 "no permanent abandonment" backoff does this repeatedly (more vault writes, more consumed-token crash windows); (c) C:27 "403/access denial" never occurs.
Minimal correction (C:19, C:27, AR:36):
- Say: sync 401 is ambiguous (expired/invalid JWT, no `tabbyrush` grant, wrong API key). Rule: 401 → at most one refresh+retry per operation; second 401 → call `/me`: `/me` 401 ⇒ ReauthRequired; `/me` ok and `tabbyrush∉apps` ⇒ AccessPending; `/me` ok and `tabbyrush∈apps` ⇒ **`AuthorizationUnresolved`** (possible wrong/rotated app key, stale claims, or operational fault — diagnosis not certain because `/me` reads DB state while sync trusts JWT claims). Pause sync, preserve data, no refresh loop; explicit user retry/diagnostic; automatic retry only with long bounded backoff and no further refresh unless `/me` 401s.
- `POST /apps/access-requests` non-2xx incl. 401: **do not refresh**; do one bounded `GET /apps/access-requests/me` (a failing list never triggers another list); classify by list contents. Refresh only if `/me` itself returns 401.
- Delete the "403" sentence or mark "not emitted by current source".

### B2 (High, not blocker) — Full-scan semantics: duplicates, stale rows; absence is purge/reset-only (C:43,51-52,60; AR:43; D:33-34)
Re-derived ordering (my first draft overstated "existing rows can be missed"): `syncedAt` is stamped at construction (`S/models/record.rs:39-48`; `sync_service.rs:349-357`) and written later (`:395-406`); pull orders by `(syncedAt,rowId)` (`:498`). An updated row's new stamp `s1` is later than its old `s0` (assuming monotone server wall clock). A row unseen by the cursor has cursor < `s0` < `s1`, so it is still ahead ⇒ **an existing row is not missed by timestamp ordering**; it can be seen **stale** (cursor passed `s0` before the update landed) and, after the update, again at `s1` ⇒ **duplicate rowId within one scan**. **New rows** whose stamp precedes commit can be missed by this scan (visible next scan). Tombstones are returned (`:620-645`); real absence of a known row ⇒ purge (≥60 d, `:670-699`, `config.rs:17-19`), DB reset/restore, or server clock regression/skew across instances (unverified; single-instance assumed).
Consequences / minimal correction (C:51-52,60; AR:43):
- Staging must **upsert by `rowId`, later occurrence in scan order wins**; duplicate rowId in one scan is NOT a protocol error. This is a staging policy, not proof of latest committed state (stamp is assigned before commit); do not compare versions across occurrences (client chosen, C:61).
- A scan can return a stale version and miss new rows; stale base is reconciled by CAS/conflict at push. Say convergence needs a later complete scan after writes quiesce, which the client cannot certify.
- Absence of a confirmed row ⇒ review/removal is acceptable **under the stated assumptions** (purge/reset, monotone single server clock); state them. Optional hardening (not required): defer discarding a pending *delete* intent as "converged absent" until absence persists in the next round's initial scan. Do not present two scans as proof.

### B3 — Phase-03 correction overclaims (C:58,60; D:39; AR:31) — see §Phase03 below
### B4 — Create-retry / conflict reclassification rule missing (C:52,56-59)
Retry of an in-flight-but-slow original, or a request that landed after the scan, yields **conflict or the generic failure "Record changed during sync; retry sync"** (`sync_service.rs:330-347,418-422`) even though the server row *equals* the frozen request (conflict `serverVersion`==our `version`). C:52 says "Known conflict keeps remote base and rejected intent, marks review", C:56 only applies equality after UNCERTAIN. A reply conflict whose server row equals the frozen request would surface a false user conflict.
Minimal correction (C:52 or C:56): "Before any conflict/record-failure is shown for a dispatched revision, run the complete-scan equality check (row/version/deleted/data); equal ⇒ converged (ack that revision only). Applies to every conflict and every record failure, not only transport loss."

## HIGH

### H1 — Refresh consumption windows and vault-write failure (C:26-27; D:44)
Server order: `validate_and_revoke` (old token revoked) → `get_user` → mint JWT → `create_refresh_token` (`A/routes/auth.rs:111-125`; `auth_service.rs:147-174`). Any failure after revoke (500, **404 user-missing from `get_user`**, DB error) **consumes the old token without issuing a successor**. Only app-origin pre-handler rejections (429 from the rate limiter, 413/400 body/JSON limits; `main.rs:315-320,357-361`) are known not to consume; a status seen through a gateway is not reliable evidence either way, so treat 429/413/400 as "not consumed" only with the app's `{error}` JSON shape and everything else as ambiguous.
C:26 only names "transport/process failure". Correct to: refresh result classes — 2xx ⇒ persist before release; 401 ⇒ ReauthRequired; 429/400/413 ⇒ not consumed, backoff; any other 5xx/timeout/transport ⇒ outcome unknown. For "unknown", one replay of the *same* token is safe (consumed ⇒ 401 ⇒ ReauthRequired; unconsumed ⇒ success) — contract's "enter ReauthRequired rather than replay" is stricter than needed and forces a password prompt on every lost response; either permit one replay or state the UX cost. Also add: **vault write failure after successful refresh** (Secret Service locked, Keystore error) ⇒ keep new pair in memory only, retry persist, stop background work; process exit before persist ⇒ ReauthRequired (both tokens already rotated). Persist `ReauthRequired` durably so periodic workers no-op instead of retrying with a dead token (C:26,65).

### H2 — Admin account token in native vault is not covered (C:14,35; D:24-25)
Login/refresh mint the **same** JWT carrying `is_admin` (`auth_service.rs:62-81,312`; `auth.rs:117-119`). The Hub owner (admin) signing in to TabbyRush stores a bearer valid for admin routes (6 h; 30 d refresh) in the OS vault. "No admin token in client artifacts" is true for builds but not for sessions. Add: TabbyRush never reads `isAdmin` to enable behavior; recommend/require a non-admin account for TabbyRush; if `isAdmin==true` on login show a warning (or refuse — user decision). State residual risk.

### H3 — "Key rotation" is unsupported by the Hub (D:24; C:6)
No rotate/regenerate/recover route: only `register_app` (key returned once, hash stored) and `verify_api_key` (`S/services/app_service.rs:54-80,133-157`; routes `S/routes/apps.rs:259-268`). Compromised/lost key ⇒ manual DB intervention or new appId. Correct D:24: "rotation is not available; key loss/compromise requires operator DB action or a new appId + data migration; record the one-time key in the release-config store before first use." Key-only threat claim (D:25) **is confirmed**: sync needs key AND JWT with `tabbyrush` AND `userId` filter (`dual_auth.rs:35-87`).

### H4 — Server/client compatibility and fail-closed scan (C:37; D:32)
No protocol/data version negotiation; `/api/v1` only; admin `PUT /schema` takes effect immediately (`S/services/app_service.rs:95-125`). Consequences the docs must admit: (a) added required column ⇒ every old-client push row fails with generic "Schema validation failed" (`sync_service.rs:283-287`); (b) one invalid active row, or a row whose `rowId` is not a 36-char hex-dash string, permanently aborts the scan: the pull rejects such a **checkpoint id with HTTP 400** at any page boundary (`sync_service.rs:471-477,84-103`) — not recoverable by retry; (c) C:37 "abort scan, preserve base/outbox" means sync is dead until upgrade/admin cleanup. Minimal correction: define an explicit `ProtocolIncompatible/DataInvalid` state (local editing continues, last reconciliation time shows stale, no retry storm, actionable text, no wipe); state that TabbyRush is only writer by policy (Hub user could still push other data with own token); schema changes require an app release gate.
Also: record `failures[].error` is English-only (`:246-250,283-287,418-422,432-438`); C:27 forbids branching on English text, so there is no way to tell retryable "Record changed" from terminal "Schema validation failed". State rule: any record failure ⇒ keep intent, rescan, retry with backoff, and after N consecutive failed rounds surface `blocked` (visible, not discarded); no text parsing.

### H5 — Android/WorkManager/leave gaps (C:65,68-69; AR:69-70)
Already right: generation fence, WorkData with AccountKey only, wipe marker. Add:
1. Mark revision **dispatching durably before the socket write** (C:48 implies it; make explicit) so process death ⇒ UNCERTAIN.
2. Worker cancelled/stopped by OS (constraint loss, quota) or by leave **while a push is in flight ⇒ UNCERTAIN**, never "not sent"; no post-cancel state writes except marking uncertain.
3. `cancelAndAwait` = cancel unique one-shot **and** periodic work, await terminal `WorkInfo` **plus** the coordinator's own in-process completion signal (terminal `WorkInfo` alone does not prove remote work stopped or callbacks finished); WorkManager's own DB persists; on startup wipe-recovery cancel all TabbyRush work before opening any account. Do not promise leave completes within the 20 s request timeout: bound it by cancellation of the client call, then rely on durable fencing + UNCERTAIN handling for requests the server may still complete.
4. Workers read the durable leave/active-account record before touching DB/vault (in-memory generation alone does not survive restart).
5. One-shot after an edit during a running worker: loop until outbox idle or re-enqueue (unique work KEEP would drop it).

## MEDIUM
- **M1 access state** (C:17-19,34; AR:34): revoke only `$pull`s `apps`, request stays `approved` (`auth_service.rs:407-427`); approve = status flip then separate grant (`:565-568`, non-atomic); list returns all apps' requests unordered (`:507-513`); no check app exists (`:430-470`); `message` serialized as `null` (`api.rs:97-106`). Rule: grant state **only** from `/me.apps`; pick newest `tabbyrush` request by `createdAt`; state "approved but not granted" ⇒ show admin-pending, allow re-request only if no pending.
- **M2 `hasMore`** (C:44): "may be true at 100" ⇒ **always true on a full page** (`:517-518`). `serverState` is **never** populated (`with_server_state` unused; `conflict.rs:118`) ⇒ replace "usually absent" with "absent today". `conflicts` has no serde default in the Rust DTO (`protocol.rs:86-88`) but server always emits it; Kotlin decoder: ignore unknown keys, missing arrays = empty.
- **M3 rate limits** (C:51): sync 60 req/s burst 20; auth 5/s burst 10, per client IP (`config.rs:96-111`, `rate_limit.rs:91`). Two full scans per round + shared NAT ⇒ 429 possible; page pacing + 429 backoff must be in the contract, not assumed. ~200 requests per round at 10k rows.
- **M4 checkpoint string** (C:43): server compares `syncedAt` **as strings** with chrono AutoSi (variable 0/3/6/9 fraction digits; `sync_service.rs:76-80,630-640`), so string order can differ from instant order. Contract must say: store and echo returned checkpoint `updatedAt` **verbatim**, never re-format/sort by parsed time; full scan from null avoids the delta consequences (supports the full-scan decision).
- **M5 purge/restore** (C:40): `/sync/:app/purge` has no admin check and purges expired tombstones for **all users of the app** (`S/routes/sync.rs:70-80`; `sync_service.rs:683-687`); `/restore` bumps version (`:733-742`). TabbyRush never calls them — fine — but any granted user can; threat model should note it and that retention timing is not user-confidential.
- **M6** `plan.md` linked from AR:2 absent at review time (file-ownership.md present).

## §Phase03 — is the narrow correction sufficient?
Source confirmation (upgrade D:39 from `[INFERENCE]` about *code* to *source-confirmed*; runtime effect still unproven): new-row path builds the filter with **no version predicate** (`sync_service.rs:359-374`), `update_one(...).upsert(true)` for `!existing` (`:401`), `$set` of the whole document (`:375`). Unique index `(userId,tableName,rowId)` exists (`app_service.rs:172-177`). MongoDB's upsert on a unique-key equality filter may retry-then-match an existing doc rather than E11000 — **version-dependent, unverified**; the regression must establish actual behavior.
Existing-row CAS protects against intervening changes only if they change `version`; versions are client-selected (C:61), so an equal-version rewrite (ABA) is not detected (`:366-374,412-423`). TabbyRush's `max+1` discipline mitigates for its own clients; server does not enforce it. Keep this limit separate from the create gate (not fixed by it).

What the correction covers: late **create** cannot overwrite a row that appeared after preflight. Real exposure in TabbyRush is narrow: needs retry R1′ of an uncertain create whose original R1 later lands and a second device edits the row before R1′ writes. Valuable defense, small window.
What it does **not** cover:
1. **Edit recreates a purged/absent row**: preflight absent + `assumedServerVersion>0` passes conflict check (`:331-347` requires `existing`) and inserts. Insert-only still inserts. C:60 "Missing edit is review, never implicit recreate" and AR:43 hold only via the client's pre-push scan, with a residual race (daily purge job, `config.rs:21-23`). Options: (i) admit limit in C:52,60 (recommended; keeps Phase 03 narrow); (ii) add server rule `absent && assumed>0 ⇒ conflict/failure`, which changes behavior for **all** Hub apps (offline clients resurrecting after reset) and needs existing-app smoke — not "narrow".
2. **Collision mapping**: D:39 says "failure/conflict" — pick one. Recommend record-level `conflict` (serverVersion by re-read or 0/unknown) or the existing failure string; either way contract B4 rule (scan equality first) applies. Duplicate-key from `insert_one` must be caught explicitly (E11000) and map to that record only; other records in batch unaffected; no 500.
3. **Deterministic regression**: a test-only barrier between preflight and write on the public `push_sync` path, or an extracted write primitive (`expected: Absent | Version(n)`) tested with a seeded row — either is acceptable if scheduling is deterministic, the **old implementation fails**, the replacement passes, and MongoDB availability is enforced. Existing Mongo tests **silently pass when Mongo is absent** (`sync_service.rs:774-795` `setup()->None`; `sync_generation_tests.rs:11-13`); D/AR:78 "not silently skipped" needs the gate to fail when `MONGO_TEST_URI` is unreachable. Plus existing-app smoke.
4. **If Phase 03 does not land**, C:58 has no safe fallback: a client timeout does not bound server execution, so waiting a "quiet interval" is NOT equivalent protection. Either keep uncertain-create-absent as UNCERTAIN/review (no automatic same-UUID retry) or explicitly accept the overwrite risk in the contract. Do not promise "no late create overwrites" before the gate ships.
Changes also alter semantics for non-TabbyRush clients that race on new rows (overwrite → collision) ⇒ release note + smoke (already required AR:78).

## Uncertain-mutation rule gaps (consolidated)
- Classify HTTP outcomes for push (C:27,55): app-origin **401/413/429/400 (pre-handler, `{error}` JSON shape) ⇒ not applied** (middleware precedes handler: `dual_auth.rs`, `main.rs:315-320,357-361`); app-origin 500 occurs before any write (`sync_service.rs:186,375`) but gateway 502/503/504, non-JSON bodies and timeouts **cannot be distinguished** ⇒ treat every 5xx/timeout/transport/cancel/non-app-shaped response as UNCERTAIN. C:27 currently groups "5xx preserve outbox" without that.
- Delete convergence on absence (C:60): acceptable under B2's stated assumptions (purge/reset, monotone single server clock); optional hardening noted there.
- Equality convergence for tombstones: compare `deleted==true`+version; ignore `data` (server stores whatever was sent, `record.rs`).
- Tombstone push must include `data` (`{}`), field is required in `PushRecord` (`protocol.rs:33`).

## Positive observations
Explicit syncedRecords handling; frozen in-flight revisions and per-row FIFO; account-keyed partition with full SHA-256; no custom Hub auth; refusal to emulate logout via refresh (matches non-idempotent rotation, `auth_service.rs:147-174`); honest "no live proof" and "local logout only" limits; key-only-not-sufficient analysis is correct.

## Minimal correction checklist (parent)
1. C:19,27, AR:36 — B1 401 model + access-request no-refresh rule; remove 403 claim.
2. C:51-52,60, AR:43 — B2 staging upsert/later-wins, stale/miss sentence, stated absence assumptions.
3. C:52/56 — B4 reclassify conflict/failure via scan equality.
4. C:58,60, D:39, AR:31 — §Phase03 items 1-4.
5. C:26-27, D:44 — H1 refresh classes, vault-write-failure, durable ReauthRequired.
6. C:14/35, D:24-25 — H2 admin-token note; H3 rotation unsupported.
7. C:37 — H4 `DataInvalid/ProtocolIncompatible` state, 400-on-bad-checkpoint-id, failure-text policy.
8. C:65-69 — H5 items 1-5.
9. C:17-19,42-44 — M1-M4 wording.

## Unresolved questions
- Mongo server version/topology for the isolated regression (upsert-vs-E11000 behavior depends on it).
- Is deployed Hub identical to checkout `094a183`? Gateway (502 seen) may add status mapping.
- Will the admin owner sign in with the same admin account (H2)? User decision: warn vs refuse.
- Accept residual edit-recreate race (Phase03 option i) or approve cross-app server rule (option ii)?
