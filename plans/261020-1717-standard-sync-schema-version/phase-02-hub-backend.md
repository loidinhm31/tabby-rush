# Phase 02 — Hub Registry, Standard Wire & Offline Cutover Tool

## Context / overview
- Status: **source implementation complete; verification in progress.**
- Roots: `H = G:/ws/sharing/glean-hub`; `D = G:/ws/sharing/doggo`. H-relative paths below are exact existing files unless marked **NEW**.
- Read [contract/ownership](contracts-and-ownership.md), [source evidence](reports/scout-findings.md#hubsyncscout), [architecture](../../docs/standard-sync-cutover-design.md), H `README.md`, plugin `AGENTS.md`.
- Dependency: phase01 contract freeze is satisfied. Source, latest affected Rust/operator suites, workspace `cargo check --workspace --all-targets`, and strict integrated Gradle verification are complete; remaining named end-to-end and live-cutover gates stay phase05-owned. Parent-reported outcomes are consolidated in [final integration verification](reports/verification-final.md).
- Exclusive owner: all affected H Rust/core/plugin/server tests, operator binary, generated core TS index. No admin UI, native, D scripts/docs, H docs or overview edits. Phase05 owns runbooks/changelog and integrated evidence.

## Current status — 2026-10-20

- Backend registry CAS/schemaVersion logic, standard response counters and the offline cutover operator are source-delivered; full implementation details remain in the implementation report.
- Actual disposable API smoke (local Mongo 27191, Hub 3100): ordinary schema baseline 1, changed save 1→2, identical no-op/retry stayed at 2, stale different save returned 409. Standard create/pull/delta/update/delete retained unknown JSON. No production data was migrated.
- Parent-reported latest affected Rust suite: 83 passed, 1 pre-existing strict-live-gate ignored (66 plugin, 5 schema Mongo, 7 operator Mongo, 4 client, 1 engine). The 7/7 operator suite covers concurrency, maintenance-deadline and recovery. `cargo check --workspace --all-targets` passed with existing unused warnings.
- Source report: [phase02 implementation](reports/phase-02-implementation.md). Latest scoped source review: [Hub/operator final review](reports/review-hub-ui-final.md), approved with no actionable findings. [Final integration verification](reports/verification-final.md) supersedes the earlier [backend verification report](reports/verification-backend.md) for current test results; that report retains its historical pre-fix compile blocker.

## Insights / requirements / architecture
- Existing ordinary writer `services/app_service.rs:634-674` has no counter and publishes before sharing indexes; `strict_upgrade_service.rs:529-574` separately publishes tables. Both must migrate. Ordinary routes reject apps whose `syncMode` exists; keep that boundary.
- `schemaVersion` is server-owned `1..=9007199254740991`, exact JSON integer; independent of per-row version, strict label/fingerprint/epoch and Room version. Missing registry field reads as 1 without GET writes. Explicit null, negative, fractional, oversized or otherwise corrupt stored values fail closed, not baseline coercion.
- Normal/strict registration persists 1 and returns it; prepare required indexes before registry publication. Keep standard declared-type/required-field validation and allowance for unknown data keys; no new TabbyRush validator, exact schema-version gate, retention engine or strict fallback.
- Effective equality: table-map/object key order irrelevant; column-array order significant; compare effective sharing using current omitted/null behavior (both currently mean retain). No unrelated sharing-clearing fix. Equal schema/sharing returns authoritative state without counter/updatedAt/index mutation, including stale-identical retry and the maximum counter value.
- Changed ordinary save: required valid `expectedSchemaVersion`; read identity/mode/content/version; reject strict app as today. If different content and stale expectation, return 409. Prepare required indexes, then one conditional update of tables/effective sharing/updatedAt/version. Predicate includes registry `_id`/appId, absent syncMode and observed version. Present version increments once; absent-field + expectation 1 uses `$exists:false` and explicitly sets 2, never `$inc` from missing zero.
- On CAS loss, re-read: equal effective content returns that state; different content returns 409/current counter. Never silently replace the caller expectation or retry an overwrite. Bound next version before any mutation/index preparation; changed save at maximum returns HTTP 400 `{"error":"Schema version limit reached"}` without changes. Unauthorized/invalid writes never mutate state.
- Strict publication uses the same version/equality/bound rules within its existing mode+epoch CAS/fence. Cover missing baseline and publication retry; `already_published` must verify target tables/epoch/mode and journal evidence, not just target mode. Table change increments atomically with publication; protocol-only metadata changes preserve counter/schema timestamp. Keep strict engine for other apps.
- PushResponse/PullResponse gain required numeric `schemaVersion` from each operation's existing schema snapshot, including empty push/pull and partial failures. Delta keeps optional nested push/pull; snapshots may differ across its sequential operations. Do not manufacture a top-level counter or transaction guarantee.

## Exact proposed public JSON examples
Examples use an isolated ordinary `demo-notes` app, not the 17-field TabbyRush schema. Keys shown are public wire keys; omitted optional sharing/mode/epoch stay omitted.
```json
{"appId":"demo-notes","apiKey":"fixture-issued-key","schemaVersion":1}
```
Strict registration also returns the counter, independently from its existing strict metadata:
```json
{"appId":"fixture-strict","apiKey":"fixture-issued-key","syncMode":"strict-v3","strictEpoch":"00000000-0000-4000-8000-000000000001","schemaVersion":1}
```
GET `/api/v1/apps/demo-notes` before the change:
```json
{"appId":"demo-notes","schema":{"todos":{"columns":[{"name":"id","type":"string","nullable":false}],"primaryKey":"id"}},"schemaVersion":1}
```
PUT `/api/v1/apps/demo-notes/schema` (body), then HTTP 200 (complete authoritative response, not a message):
```json
{"schema":{"todos":{"columns":[{"name":"id","type":"string","nullable":false},{"name":"title","type":"string","nullable":true}],"primaryKey":"id"}},"expectedSchemaVersion":1}
```
```json
{"appId":"demo-notes","schema":{"todos":{"columns":[{"name":"id","type":"string","nullable":false},{"name":"title","type":"string","nullable":true}],"primaryKey":"id"}},"schemaVersion":2}
```
Identical retry with expectation 1 returns the same HTTP 200 response/version 2. Different stale content returns HTTP 409 exactly:
```json
{"error":"Schema version conflict","currentSchemaVersion":2}
```
Empty standard push, pull and combined delta responses (timestamps/checkpoint values illustrative; counter 2 authoritative):
```json
{"synced":0,"conflicts":[],"failures":[],"syncedRecords":[],"serverTimestamp":"2026-10-20T10:00:00Z","schemaVersion":2}
```
```json
{"records":[],"checkpoint":{"updatedAt":"1970-01-01T00:00:00Z","id":""},"serverTimestamp":"2026-10-20T10:00:00Z","hasMore":false,"schemaVersion":2}
```
```json
{"push":{"synced":0,"conflicts":[],"failures":[],"syncedRecords":[],"serverTimestamp":"2026-10-20T10:00:00Z","schemaVersion":2},"pull":{"records":[],"checkpoint":{"updatedAt":"1970-01-01T00:00:00Z","id":""},"serverTimestamp":"2026-10-20T10:00:00Z","hasMore":false,"schemaVersion":2}}
```

## Related files / exclusive ownership
All plugin paths prefixed `glean-hub-plugin/glean-hub-sync/` unless stated otherwise.
- MODIFY `src/models/app.rs`, `src/models/api.rs`, `src/error.rs`: bounded baseline serialization, registration/read/save DTOs, structured conflict/exhaustion response. Reuse GetSchemaResponse projection for success; never include stored apiKey hash.
- MODIFY `src/services/app_service.rs`, `src/services/strict_upgrade_service.rs`, `src/services/mod.rs`: shared small CAS/equality helpers, all ordinary/strict writers and test-module wiring; no parallel second writer convention.
- MODIFY `src/routes/apps.rs`, `src/services/sync_service.rs`, `src/lib.rs` as needed: required expectation, authoritative response, snapshot counters, operator module export only; retain existing middleware/auth.
- MODIFY `src/services/strict_upgrade_tests.rs`, `src/services/sync_generation_tests.rs`, `src/services/strong_sync_tests.rs`, `src/routes/strong_sync_route_tests.rs` only where references/literals or writer assertions require migration; preserve unrelated strict behavior tests.
- READ `src/models/record.rs`, `src/models/strong_sync.rs`, `src/services/permit_service.rs`, `src/jobs/deleted_records_cleanup.rs`, `src/bin/strong_sync_admission.rs`: record/evidence shapes, `_coordination`, `_strict_migrations`, cleanup scope and CLI precedent. Do not copy admission tool's implicit live-DB/env defaults.
- MODIFY `glean-hub-core/glean-hub-sync-engine/src/protocol.rs`; related existing `src/{handler,lib}.rs` only if LSP finds consumers; MODIFY `glean-hub-core/glean-hub-sync-client/src/{client,lib}.rs` only if references require it.
- MODIFY exporter `glean-hub-core/glean-hub-sync-client/src/bin/export_types.rs` if needed; REGENERATE `glean-hub-core/glean-hub-sync-client-types/src/index.ts` from Rust, never hand-edit generated counters.
- **NEW** plugin `src/bin/standard-sync-cutover.rs`, `src/operator/{mod,conversion,journal,publication}.rs`: explicit standalone offline operator; no route/startup migration. **NEW** plugin `tests/{schema_version_integration,standard_sync_cutover_integration}.rs`: real Mongo behavior/conversion suites. MODIFY plugin `Cargo.toml` only for actual test/CLI dependencies.
- No existing whole-file deletions identified. Remove obsolete message-only success handling, unversioned writer signatures/literals and unsafe mode-only already-published shortcut; migrate every reference, do not retain compatibility aliases. Newly discovered files require ownership manifest update before edits; H UI callers belong phase04, D callers phase05/03 by layer.

## Numbered implementation steps
1. Before exported-symbol/signature changes, obtain language-server declaration/find-references inventory for AppSchema/constructors, UpdateSchemaRequest, GetSchemaResponse, register/update services, PushResponse/PullResponse/DeltaResponse and re-exports; include Rust struct literals, deserialize fixtures and generated TS consumers. Save inventory in phase02 report; grep only supplements LSP. If LSP unavailable, report prerequisite, do not claim inventory completed.
2. Implement bounded model/default semantics and effective equality; migrate constructors, ordinary service/route/error DTOs together. Preserve auth and sharing semantics; return the conditional write's authoritative state. Inventory all normal-app save callers and retire old unguarded contract; coordinate UI signature with phase04 and command/script examples with phase05.
3. Migrate strict registration/publication and interrupted publication retry paths. Include schema version in CAS and durable journal evidence. Verify equality before increments; no-op retries must not republish timestamps. Keep fingerprints/protocol validation independent.
4. Add counters to every standard response construction (including early return) and core deserialize/consumer fixtures. Generate numeric TS via `cargo run -p glean-hub-sync-client --bin export_types -- glean-hub-core/glean-hub-sync-client-types/src/index.ts` once after Rust changes land; this is code generation, not permission for mid-wave checks. Pass generated artifact to phase04.
5. Implement CLI with explicit `--mongo-uri-env CUTOVER_MONGO_URI --db-name <exact-db> --app-id tabby-rush --migration-id <id> --dry-run|--prepare|--publish|--resume`; mutation modes require `--maintenance-manifest <path> --snapshot-manifest <path> --confirm-target <db>/tabby-rush`. No URI/database fallback, dotenv loading or URI logging. Unknown/missing/conflicting arguments fail closed; no credentials in argv/logs.
6. Dry-run is strictly non-mutating: no collection/index/journal creation, fence acquisition or metadata touches; read topology/registry/records/indexes and emit redacted counts, source mode/epoch/version, validation errors and planned operations. Before any mutation require explicit source expectation in target-bound manifests and revalidate it; dry-run alone does not authorize preparation. Reject mixed strict/standard records, duplicate logical identities, unexpected ownership, unsupported source shapes or incompatible 17-field task schema rather than silently upgrading.
7. Mutations run only while **all Hub nodes and job processes are externally stopped and routes isolated**; broader outage is intentional. Manifest binds origin/database/app/migration, complete node/job inventory, stop/isolation confirmations, operator authorization/window and backup/restore evidence. Strict permits/process-local mutex alone are insufficient. Tool cannot prove arbitrary remote processes stopped: fail without explicit complete attestation; phase05 separately demonstrates all-node shutdown and restore rehearsal. Never start services or release isolation automatically.
8. Snapshot verified original registry, source collection/index specs, relevant `_coordination` app entry, `_strict_migrations` and per-record lastOp/receipt evidence; manifest stores checksums/counts/collection UUIDs and backup location with restricted permissions. Establish durable journal (**NEW** `_standard_sync_cutovers`) scoped by database/app/migration before preparation. Same ID resumes; other active ID or mismatched snapshot/target aborts. Use topology-appropriate journaled acknowledged writes; lost acknowledgements resolved by readback.
9. Prepare a separately named staging collection, never rewrite active strict rows. Convert to ObjectId `_id`, preserve `(userId,tableName,rowId)`, appId, raw data including unknown keys, deletion flag and strict revision→standard version; require positive representable revisions. Preserve all 17 live task fields, empty tombstones and original evidence in snapshot. Stable journaled seed instant supplies `syncedAt` and tombstone `deletedAt` using standard `to_iso_string` RFC3339/UTC string representation, not BSON dates. Do not claim original deletion time where absent; converted tombstones begin ordinary retention at seed time.
10. Stage retries upsert by unique logical identity without regenerating existing ObjectIds; journal chunk progress and verify each resumed row against snapshot. Build standard unique `(userId,tableName,rowId)`, user/syncedAt and `(appId,tableName,syncedAt,rowId)` checkpoint indexes on staging. Validate every logical payload/revision/owner/tombstone against snapshot, counts by user/table/live/deleted, ObjectId type/uniqueness, actual index definitions, timestamp-filter compatibility and complete paged standard scan. No lossy repair or sample-only equivalence proof.
11. Journal intent and completion around each stage: `snapshotComplete → preparing → validated → sourceArchived → stageActivated → registryPublished → verified`. Archive original active collection by non-overwriting rename; activate staged collection by another non-overwriting rename. Record collection UUIDs; on restart inspect actual names/UUIDs plus source/target registry CAS, covering crash after DB operation but before journal acknowledgement. Ambiguous/mismatched ownership stops under isolation; never guess by counts/name or use dropTarget.
12. Publish ordinary registry only after active collection validation: CAS original identity/mode/epoch/version/content, `$unset` syncMode/strictEpoch and obsolete active strict metadata (absence, not null), preserving API-key hash/grants/other registry fields. Mode-only conversion materializes/preserves 1 or existing counter; actual table/sharing change uses common increment rules once. Archive strict coordination/evidence, prevent old migration resumption from rewriting converted storage, and verify standard acceptance/strict rejection before isolation release. Collection swap+registry publication are not atomic.
13. Test interruption at every journal/rename/CAS boundary and repeated prepare/publish/resume. Pre-publication rollback restores original collection/registry under isolation only; after publication keep isolation for validation. After any standard writes, permit forward repair or separately authorized reverse conversion only; never blind snapshot restore. Tool emits stage/required operator action, never auto-rollback uncertain published state or delete backups.

## Pending acceptance / tests / real smoke
- [ ] `schema_version_integration`: baseline GET no write; normal+strict registration 1; absent→2; effective changed sharing/schema increments; map reorder/no-op/stale-identical stable timestamps; column reorder changes; invalid preconditions/corrupt stored values; maximum no-op/overflow; index failure prevents publication; concurrent same/different saves; CAS race re-read; strict publication resume increments once.
- [ ] Same suite verifies real Mongo registry and actual plugin routes, all standard response branches/counters, sequential delta snapshots, additive consumer decoding, schema required/type rejection with unknown-key preservation, auth/account boundaries. No mock DB/fetch or wording/source-string tests as acceptance proof.
- [ ] `standard_sync_cutover_integration`: disposable Mongo clone contains same rowId across users, live/deleted/unknown-key rows, strict evidence and indexes. Run real binary for dry-run immutability, explicit-target refusal, invalid/mixed/duplicate input, snapshot integrity, real stage/swap, journal recovery at every boundary, repeat execution, credential/grant preservation, old strict-route rejection and standard pull/push round trip.
- [ ] New Mongo suites require explicit `MONGO_TEST_URI`, random `test_standard_sync_*` database and disposable-target allowlist; fail if unavailable, never silently skip/default localhost or access live config. Existing Mongo tests that can skip are not sufficient evidence. No migration invoked by startup or ordinary tests against configured service.
- [ ] Main/phase05 runs once after all owners land, from H: `cargo test -p glean-hub-sync-engine -p glean-hub-sync-client -p glean-hub-sync --all-targets -- --test-threads=1` (includes both named integration binaries); `cargo build -p glean-hub-server`. Record actual executed test counts, not only exit status.
- [ ] On authorized **disposable clone only**, PowerShell commands: `$env:CUTOVER_MONGO_URI=$env:MONGO_TEST_URI`; `cargo run -p glean-hub-sync --bin standard-sync-cutover -- --mongo-uri-env CUTOVER_MONGO_URI --db-name test_standard_sync_rehearsal --app-id tabby-rush --migration-id rehearsal-01 --dry-run`; then same base args with `--prepare`, `--publish`, or `--resume` plus `--maintenance-manifest <approved-file> --snapshot-manifest <verified-file> --confirm-target test_standard_sync_rehearsal/tabby-rush`. These options/files are proposed deliverables, not existing CLI claims.
- [ ] Phase05 uses real isolated Hub authentication and admin browser plus native Android/Desktop against converted clone; observes schema 1→2→same retry, 409, standard convergence and retained strict behavior for another app. Actual server/DB + UI proof required; mocked transport tests do not satisfy this gate.

## Conflict prevention / risks / security / next steps
- Single Rust owner changes every registry writer and generated index; notify phase04 when required expectation/success DTO is ready. Route any newly found embed/core response literal to its owning layer before parallel edits; no generated-index co-ownership.
- CAS cannot undo prepared surplus indexes after a competing save; leave harmless additive indexes, publish no losing metadata. Index failures/overflow must not report success. No new compound pagination/hasMore redesign or generic retention changes.
- Biggest risk: collection swap with another node/job alive. Require externally verified shutdown/isolation, immutable backup, exact target authorization and durable journal. No secret-bearing output; protected snapshot contains credentials and user data, never commit it or attach it unredacted.
- Next: hand source/API inventory, generated types, named suites and operator help/examples to phases04/05. Phase05 updates architecture/API docs/changelog/runbook and records real commands/evidence; implementation owners fix failures before release.

## Unresolved questions
- No design disagreement. Execution prerequisites: explicit disposable Mongo target, restore-rehearsal evidence, all-node maintenance authorization and actual source-shape preflight; none inferred from a running Hub or existing environment.
