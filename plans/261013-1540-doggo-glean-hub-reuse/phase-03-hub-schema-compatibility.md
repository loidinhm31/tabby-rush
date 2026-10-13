# Phase 03 — Doggo-only strict server and legacy isolation

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Strict contracts](contracts.md) · [Decisions](reports/design-decisions.md).
- [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md).
- [Selected A](reports/strong-sync-feasibility.md) · [Strict design](reports/strong-sync-design-feasibility.md) · [Source isolation/callsites](reports/strong-sync-isolation-feasibility.md) · [Mongo evidence](reports/mongodb-feasibility-evidence.md) · [Strict review](reports/strict-contract-review.md).

## Parallelization Info
Group C after02; independent coding04/05/06 against frozen strict contract/digest vectors. Real isolated Mongo/Hub/operator/old-binary/legacy-client prerequisites required before07 device sync. No production change in03.

## Overview
- Date: 2026-10-13
- Description: Immutable opt-in strict-v1 mode, separate authenticated routes/storage and fail-closed admission; other Hub apps unchanged.
- Priority: P2
- Implementation status: pending (0%)
- Review status: final planning review complete; implementation/runtime acceptance pending.
- Effort: 40h estimated; total plan148h,uncertainty ±25–35%.

## Key Insights
Verified source: `services/app_service.rs:56-82` legacy register inserts registry before indexes; `routes/apps.rs:43,61-65,78` registration/read-back/update callsites; `lib.rs:185-187,215-219` initialization and legacy dual-auth router. Preserve legacy register signature/behavior and add real `register_strict_app` entrypoint. Legacy writers/jobs/notification/old binaries derive only `app*`; `_strict_v1_*` isolation protects strict rows. New legacy-only mode guard covers all routes, not direct service calls; those remain safely confined to old namespace. No obsolete legacy upsert repair.

## Requirements
Fixed personal todos/no sharing/no notificationEvents; immutable syncMode strict-v1 with epoch. New admin POST `/api/v1/strict-apps`, mandatory schema mode+epoch read-back and index/topology admission. New POST `/api/v1/sync/:app_id/strong/{bootstrap,push,pull}` under existing auth/rate/body/plugin gates. Existing embedded apps/adminUI/auth server/shared Rust/TS DTOs/legacy routes semantics untouched for absent mode. Unknown mode fails closed only affected app, registry list survives. No transaction/feed/receipt ledger/GC/restore/reset API.

## Architecture
Store optional mode/strictEpoch strings tolerantly in AppSchema; absent mode legacy, any present mode rejected by legacy-only router guard, strong gate exactly strict-v1. `_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>` uses validated appId and active canonical UUID epoch. Check registry epoch BEFORE any collection handle/use; capture validated namespace permanently. Fixed todos identity `_id=exact24hexUserId+":"+canonicalRowId`, intrinsic unique _id; scan index owner/table/row with simple collation. Admission per app+epoch fails only that app, never whole Hub initialize. Separate strict DTO/model/error/service/routes; no core protocol re-export migration.

## Related code files
Future implementation artifacts only, not implemented now.
- `/home/loidinh/WS/doggo/integration/glean-hub/doggo-schema.json` — create, exact strict registration schema.
- `/home/loidinh/WS/doggo/scripts/hub-schema-provision.mjs` — create, strict-apps provision/read-back/admission.
- `/home/loidinh/WS/doggo/scripts/hub-compatibility-smoke.mjs` — create, mounted strict/legacy/old-binary client gates.
- `/home/loidinh/WS/doggo/docs/hub-schema-integration.md` — create.
- `/home/loidinh/WS/doggo/docs/hub-compatibility-acceptance.md` — create.
- `/home/loidinh/WS/doggo/docs/evidence/hub-compatibility-summary.md` — create.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/app.rs` — modify, tolerant mode/epoch metadata; legacy constructor preserved.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/api.rs` — modify, isolated strict registration/read-back additions; legacy lists unchanged.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/mod.rs` — modify, strict exports.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/strong_sync.rs` — create, strict DTO/document/digest/cursor.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/app_service.rs` — modify, separate strict registration/index admission/update rejection.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/mod.rs` — modify, strict service/test wiring.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strong_sync_service.rs` — create, single-document mutations/scans/admission.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strong_sync_tests.rs` — create, deterministic real-Mongo tests plus local digest vectors.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/apps.rs` — modify, strict-apps handler/service dispatch,read-back,strict update rejection.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/mod.rs` — modify, strict route/test wiring.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/strong_sync.rs` — create, strong bootstrap/push/pull.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/strong_sync_route_tests.rs` — create, mounted router/mode/auth/bypass regression.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/middleware/mod.rs` — modify, mode-guard export.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/middleware/sync_mode_guard.rs` — create, legacy-only non401 guard and strict scope coherence.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/strong_sync_error.rs` — create, strict-local machine codes/status, legacy SyncError unchanged.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/lib.rs` — modify, isolated services/routes/guard/admission wiring; existing public service factories remain valid.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/bin/strong_sync_admission.rs` — create, read-only operator candidate preflight invoking SAME Rust validator.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/CLAUDE.md` — modify, accurate strict extension/docs/tests.
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/CHANGELOG.md` — create, Doggo opt-in change/legacy unchanged.
- `/home/loidinh/WS/glean-hub/docs/doggo-strict-sync.md` — create, protocol/admission/old-binary/regression runbook.

## File Ownership
Exclusive03 owns all26 absolute paths above,exactly registry03. No deletes. New Rust snake_case. Cargo manifest/lock/core/embedded/adminUI/auth/notification and legacy sync_service/routes/sync/error/dual_auth/config/jobs/models/record unchanged. Isolated guards/errors/wiring/bypass tests cover required surfaces without shared churn. Read-only admission binary03 is necessary inactive-candidate preflight;08 owns recovery script,no reset API. Extra tracked path reviewed;original owner repairs.

## Implementation Steps
1. Consume parent strict contract, including current admission/_id/error/recovery decisions, and02 canonical cross-language vectors. Pin fixed schema/data validation; reject noncanonical app/row/op/epoch/user identity,unknown mutation fields,overflow,changed createdAt on update. No body user authority. Digest uses persisted normalized fields,length-framed domain+UTF8/u64/bool0or1,not map order.
2. Add optional mode/strictEpoch strings default+skip-none so absent-mode legacy shape remains. Preserve register_app/update_schema signatures; separate real register_strict_app invoked only new strict-apps handler. GET schema echoes mode+epoch; AppListItem unchanged. update_schema write filter requires syncMode ABSENT; matched0 read distinguishes notfound/strict or unknown-mode rejection,ordinary legacy unchanged. Update all affected03 callsites (routes/apps.rs register/read-back/update),including local constructors; no external caller migration/aliases.
3. Strict registration validates exact todos/no-sharing schema,generates epoch,creates ordinary isolated collection and _id/scan indexes BEFORE publishing registry LAST. Admission inspects existence/simple or absent default collation/no capped/custom validator/NO TTL/exact index specs and journaled topology policy; no auto-created unindexed collection. Partial provisioning safely retries after authorization,conflicting app refused. Cache ONLY admitted specs per app+epoch,never active epoch authority. Lazy/async per-app failure never aborts Hub initialization. Infrastructure/operator drop outside guarantee,not continuous-lock protection.
4. Configure strong handles only: standalone w1,jtrue/primary,noHA; replica majority,jtrue/primary majority reads and journal policy verification. No global Mongo defaults/config/core changes. Unsupported/unknown topology/index state yields503 notAdmitted for strict traffic only. Mounted strong routes inherit dual auth,rate/body/plugin limits. Strict-local errors machine code409 epochMismatch,426 protocolUnsupported,400 invalidRequest,503 notAdmitted; mode rejection non401. Genuine auth errors retain existing behavior.
5. Wrap legacy router alone before strong merge: absent mode unchanged; present mode rejects legacy push/pull/delta/purge/restore. Every strong request fetches authoritative mode/strictEpoch/schema with configured primary reads (replica majority),validates current fixed schema even on admitted-spec cache hit. Bootstrap discovers active admitted epoch;push/pull require matching canonical epoch BEFORE collection handle,stale/forged409 creates nothing. No active-epoch cache/rerouting. Guard strict/raw-path/header coherence. Capture validated namespace; pre-cutover in-flight may commit OLD only,no registry+write atomicity guarantee. Operator recovery publishes epoch with matching journaled concern;strict schema changed by old binary pauses admission.
6. Create expected0 insert-only revision1/_id/lastOp; duplicate key reread same replay/conflict logic,no overwrite. Update/delete exact expectedRevision+live filter,no upsert,increment1 and atomically lastOp. Delete canonical empty data/permanent metadata; no compaction/TTL/purge/restore/undelete. Deterministic result precedence,missing non-create rejection,latest ID/digest mismatch opIdReuse,overflow pre-write. Nonnull lastOp.acceptedRevision must equal current revision; maintenance-imported revision1,lastOp=null valid and never historical acknowledgement. Independent outcomes,not transaction.
7. Every exact replay uses REAL fresh confirmationNonce conditional write matching identity/revision/lastOp/digest with configured journaled concern; matched_count1 required,not modified_count/no-op/read visibility. Logical revision/data/lastOp unchanged. Duplicate-create reread and post-write-concern-error retry included. Race match0 or transport/concern failure remains unresolved; retryableFailure MAYBE applied. Superseded lastOp lacks history; client classifies prior uncertainty conservatively UnknownSuperseded,no equality ack.
8. Stable rowId simple-collation full pull includes canonical tombstones,<=100 limit+1 lookahead,hasMore from extra row,cursor last RETURNED row. Scope cursor protocol/app/user/epoch/table; reject forged/nonprogress cursor,never cursor authority. No lastOp/nonce in pull DTO. Full scans not snapshots: behind-cursor inserts/after-read edits wait next round. Bootstrap returns exact protocol/app/epoch/tables; no reset endpoint.
9. Permanent `strong_sync_tests` suite uses actual isolated uniquely named Mongo DB,awaited deterministic barriers not sleeps; missing/unreachable MONGO_TEST_URI HARD FAILS required suite,never optional skip. Cover concurrent create/update/delete,same-base one effect,delayed original+retry,WC timeout then replay,latest same-ID changed digest,replay-confirm vs successor match0,successor supersession/equal payload not proof,missing edit/tombstone resurrection/overflow. Verify actual nonce changes without logical revision on replay and durability under supported crash/restart/failover policy; standalone never claims HA.100/101 cursor/behind-cursor/identity isolation,index/collation/TTL/topology admission failure/forged epoch no collection gates mandatory.
10. Mounted /api/v1 real router+Mongo tests: strong legacy/unknown,legacy strict/unknown,wrong key/JWT/grant/header/app/encoded app,cross-user/cursor/epoch isolation,non401 protocol,tolerant list,strict schema immutability. Bypass populated strict tombstones versus actual legacy push/delta/purge/restore/direct service,existing public cleanup-job spawn/start with controlled clock/cancellation and notification legacy-namespace writes: strict docs/fences unchanged. No private sibling access/public testing hook/job-source edit; actual cleanup invocation measured. Script-created recovery namespace admitted by actual Rust gate; wrong index/collation/TTL fails. Post-cutover requests409 and active epoch authority not cached.
   Implement shipped read-only strong_sync_admission helper invoking SAME Rust admission validator on exact candidate app/epoch/namespace from protected operator environment,never URI argv/log or collection writes/registry activation. Public routes remain active-epoch only.08 runs helper before registry CAS;candidate creation/index/schema/count logic stays08. Tests compare helper/server validator and valid recovery output versus wrong _id/scan/collation/TTL,imported lastOp=null. Helper artifact availability/provenance and no-Cargo-on-production execution are release prerequisites;record actual packaged executable path,not guessed installation.
11. Future commands: `cargo test --locked --manifest-path /home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml strong_sync -- --nocapture`,then crate permanent suite with --locked,plus `node scripts/hub-compatibility-smoke.mjs --origin http://127.0.0.1:3000 --debug-local`. Suite filters/names are planned new identifiers; document exact implemented equivalents,never count unexecuted/ignored tests. Real Mongo supplied privately,never printed. No builds/tests run now.
12. Actual previous Hub binary against isolated DB: strict-apps404/405/no accidental registration; strong routes absent ->Doggo ProtocolIncompatible; old schema partial update preserves mode/epoch,old legacy writers/purge/restore/jobs touch only app* and cannot alter strict collection. Roll-forward imports nothing from divergent app*. Sanitized existing legacy app register/update/push/pull/delta/purge/restore fixtures and actual unchanged embedded/legacy clients against new server mandatory. Record exact previous/current revision/provenance/results,not simulated new-code old-mode test. Provision script uses strict-apps/read-back/index checks and protected key custody; docs distinguish static evidence from actual Mongo/old-client results. Hand to07/08; no production deploy/provision now.

## Todo list
- [ ] Implement every owned artifact/requirement with real behavior,not scaffolds.
- [ ] Pass designated future commands and actual real-Mongo/router/old-binary/legacy-client gates; sanitized evidence.
- [ ] Original owner resolves failures; parent review before implementation acceptance.

## Success Criteria
Strict fixed schema/register/read-back/admission fail closed per app; server-owned revision/insert-only/exact CAS/permanent tombstones/durable real replay confirmation prove at-most-one frozen logical effect within intact epoch. Deterministic real Mongo and mounted routes/bypass/old-binary/unchanged legacy clients pass with no skip. No shared engine/client/embedded/auth/adminUI behavior migration; no equality receipt/snapshot/transaction claim. Source inspection cannot pass03.

## Conflict Prevention
Exact registry ownership only; no shared source/build/lock sweep. New tracked path reviewed before creation. Root factory/API churn avoided; original owner repairs.03 tests/docs own isolation evidence;08 recovery script separately owned,not duplicate maintenance implementation.

## Risk Assessment
Runtime Mongo/version/journal/index privileges/operator authorization/old-binary fixtures may block. LastOp historical ambiguity/permanent identity growth/non-snapshot convergence accepted limits. Unknown or partial admission pauses strict app while legacy remains usable. DB-wide/operator drop or stale-epoch reopen outside guarantee. Rollback may create divergent legacy namespace but never imports it.

## Security Considerations
Authorized isolated DB/app/user fixtures only; cleanup limited to owned test DBs/IDs. Strict collection auth user boundary unchanged,raw/decoded/header coherence verified. No secrets/.env inspection,credential logs or private payload evidence. Admin/key/Mongo connection protected prompts/environment; native app key not human auth. No global DB concern changes.

## Next steps
07 composes real strict/auth/store/UI and runs Android gates after03 evidence.08 deploys/provisions reviewed strict extension and rehearses quiescent fresh-epoch recovery using its owned operator script/version guard. No legacy create fix remains.

## Unresolved questions
Actual approved isolated/deployed Hub+Mongo version/topology/journal/index authority,old-binary artifact and unchanged embedded-client sanitized fixtures/operator access still needed. Runtime gates unexecuted; no selected A/auth/logout decision reopened.
