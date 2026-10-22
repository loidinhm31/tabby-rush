## Phase Implementation Report

### Executed Phase
- Phase: phase-02-hub-backend.
- Plan: `plans/261020-1717-standard-sync-schema-version`.
- Status: source implementation complete; coordinator generation/build/Mongo execution pending, deliberately not run mid-wave.
- Ownership: H Rust/core/plugin sources/tests/Cargo only; this assigned report. No H UI/docs, D runtime/scripts, services, installed app data, secrets, database, git stage/commit operations.

### Files Modified
H-relative; counts are final source lines, not diff statistics.
- `glean-hub-core/glean-hub-sync-engine/src/protocol.rs` — 203.
- Plugin prefix: `glean-hub-plugin/glean-hub-sync/`:
  - `Cargo.toml` — 36; `src/lib.rs` — 451; `src/error.rs` — 120.
  - `src/models/app.rs` — 218; `src/models/api.rs` — 156.
  - `src/services/app_service.rs` — 768; `src/services/strict_upgrade_service.rs` — 917; `src/services/sync_service.rs` — 1346.
  - `src/routes/apps.rs` — 292.
  - NEW `src/bin/standard-sync-cutover.rs` — 48.
  - NEW `src/operator/mod.rs` — 286; `conversion.rs` — 154; `journal.rs` — 109; `publication.rs` — 166.
  - NEW `tests/schema_version_integration.rs` — 222; `tests/standard_sync_cutover_integration.rs` — 214.
- Generated TS index intentionally unchanged: exporter compilation forbidden mid-wave; exact generation command below.

### Tasks Completed
- Missing registry counter defaults to 1 on deserialization only; present null/type/fraction/out-of-range corruption fails closed.
- Normal/strict constructors persist counter 1; registration DTOs return it. Normal registration prepares indexes before publishing registry.
- Shared bounded counter/predicate helpers; allocation-free semantic equality: table-map order ignored, ordered column arrays/sharing arrays retained.
- Ordinary PUT requires positive safe-integer expectation; no-op/stale-identical returns full authoritative DTO without timestamp/index/counter writes.
- Changed PUT bounds before indexes, prepares indexes, conditionally publishes schema/sharing/timestamp/counter once; absent counter explicitly becomes 2.
- CAS loss reads current state: equivalent effective intent returns authoritative state; otherwise exact 409/currentSchemaVersion. Omitted/null sharing retains latest current sharing.
- Strict publication includes observed identity/mode/epoch/tables/counter CAS and journaled source/target version/table evidence. Changed tables increment once; mode-only metadata preserves schema timestamp/counter.
- Removed strict mode-only already-published shortcuts: retries require matching epoch, target tables, migration request fingerprints and publication counter evidence; interrupted completion can finish without republishing schema.
- Empty/ordinary/partial-failure push and pull responses carry each operation's schema-snapshot counter. Delta remains nested/sequential, no fabricated top-level counter.
- Existing standard validation/unknown-key acceptance, strict engines for other apps, generic retention and auth middleware retained.
- Full explicit offline operator: read-only preflight; protected snapshot/manifests; staging conversion/indexes/full row equivalence and paged scans; UUID-proven nonoverwriting renames; registry CAS; retired strict coordination; standard read/strict rejection verification.
- Explicit separately authorized `--rollback` supports prepublication restoration under isolation; refuses published registry or changed standard rows. Interrupted rollback resumes only with rollback authorization.
- Journals record intent/completion, stable seed instant, source/stage UUIDs, snapshot checksum, row progress. Lost acknowledgements recover by readback or same-ID resume; ambiguous ownership stops. No dropTarget, backup deletion, service restart or isolation release.

### Reference / Callsite Inventory
- LSP unavailable per coordinator-observed environment; no LSP completeness claim. Used scoped Rust/core/plugin/server declaration/literal/callsite and serialized-field search fallback.
- `AppSchema`: constructors in model; normal/strict registration in app service; existing tests use constructors rather than struct literals. Updated constructors once; no other Rust AppSchema literals found.
- `UpdateSchemaRequest`, registration DTO literals, `GetSchemaResponse`: model and `routes/apps.rs`; obsolete message-only PUT removed. `AppService::update_schema` only runtime caller is that route; new tests use required expectation.
- Registry table writers found: normal registration/update, strict registration, strict upgrade publication. Each migrated; operator mode-only publication materializes existing/default counter without changing tables/sharing.
- Standard response literals found: core declarations; sync service empty push/final push/pull. Reexports and HTTP decoder consumers in core client/lib/exporter need no signature changes beyond additive fields.
- Core exporter already exports PushResponse/PullResponse/DeltaResponse with `BigIntExportBehavior::Number`; no exporter modification needed.
- Existing strict response DTOs and strict service response literals are distinct contracts, intentionally unchanged.
- AdminSchemaImpl notified API signatures fixed: required expectedSchemaVersion, authoritative GET/PUT, registrations 1, exact structured409; sharing null/omitted retains.

### Operator Contract / Protected Artifacts
- CLI: `--mongo-uri-env <NAME> --db-name <exact> --app-id tabby-rush --migration-id <id>` plus exactly one `--dry-run|--prepare|--publish|--resume|--rollback`.
- Every mutation additionally requires `--maintenance-manifest <absolute-path> --snapshot-manifest <absolute-path> --confirm-target <database>/tabby-rush`.
- No URI argv/default/database fallback/dotenv; no connection details or raw rows printed. Mutation uses primary reads and journaled majority writes on replicas, journaled w1 on affirmative standalone primary; sharded topology refused.
- `Snapshot` BSON contains original registry, every complete strict row (including raw unknown keys, lastOp/receipt evidence), index specifications, full collection specification/UUID, app coordination and strict migrations. `operator::journal::capture(db, app)` is read-only; external backup process must protect/export and restore-rehearse it before mutation.
- Snapshot JSON manifest fields: target, topologySha256, snapshotFile, snapshotSha256, registrySha256, indexesSha256, sourceUuidSha256, sourceCollectionUuid, recordCount, owners, backupLocation, backupVerified, restoreRehearsalEvidence, restrictedPermissionsVerified.
- Maintenance JSON manifest fields: target, topologySha256, snapshotSha256, sourceRegistrySha256, nodes, jobs, inventoryComplete, allHubNodesStopped, allJobProcessesStopped, routesIsolated, isolationEvidence, operator, authorization, authorizedActions, windowStart, windowEnd, backupRestoreEvidence.
- Target shape: `{origin,database,appId,migrationId}`; node/job entries: `{identity,host,stopped,evidence}`. Both inventories must be nonempty, unique and complete with explicit stopped attestations; empty jobs requires explicit inventory evidence representing no external job processes, not an omitted inventory.
- Actions use strings `prepare`, `publish`, `resume`, `rollback`; rollback resumption additionally requires rollback authorization. Maintenance source hashes bind the exact authorized backup; window rechecked before publication.
- Every supplied artifact must be absolute regular non-symlink/read-only; Unix excludes group/other access. Windows real DACL checked: only current operator, SYSTEM, Administrators may receive allow ACEs; no ACL permission grant performed by operator.
- Hash formulas: SHA256(snapshot BSON bytes); SHA256(BSON registry); SHA256(BSON `{indexes: snapshot.indexes}`); SHA256(BSON `{uuid: snapshot.collectionUuid}`). Topology checksum supplied by dry-run binds actual Mongo topology; source UUID also stored verbatim in manifest.
- Preflight only accepts supported strict-v3/compatible17-field source; no implicit v1/v2 backfill, mixed rows, duplicate logical identities, unsupported collection/index policies or unexpected ownership. Unknown task JSON keys preserved. Standard runtime receives no new per-app strict validator.
- Converted `_id` is ObjectId, logical owner/table/row identity preserved, strict revision becomes standard version. Stable canonical UTC string seed supplies syncedAt/deletedAt; tombstone retention starts there, never claims original deletion time.
- Snapshot/archive are retained permanently by this tool; operator must independently verify and separately authorize restart/isolation release. External all-node/job shutdown cannot be proved by a database permit and is explicitly attested, not claimed detected.

### Tests Status
- Type/build/lint/formatter checks: NOT RUN by instruction.
- Unit/integration execution: NOT RUN; no verified pass counts or coverage claimed.
- Authored 5 real-Mongo registry/API tests: missing baseline/no writes, no-op/stale-identical/map equality, ordered-column/sharing changes, corrupt counters, maximum overflow, concurrent same/different saves, index-before-publication failure, actual plugin DTO/routes/auth/account isolation, required/type validation/unknown JSON, standard empty/partial/conflict/delta counters, strict absent-counter publication/retry.
- Authored 4 cutover tests: real binary immutable dry-run/argument and target refusal, mixed/duplicate/owner/revision rejection, actual BSON restore rehearsal/protected checksums, staging/swap/repeats, every-row/index/scan equivalence, credentials/grants preservation, standard push/pull round trip, no post-write resume/restoration, recovery across 9 durable operation boundaries, prepublication rollback and occupied archive preservation.
- New suites are Cargo feature-gated so default unrelated full-suite runs require no Mongo secrets/config. Explicit integration invocation fails clearly without prerequisites; never silently skips or defaults localhost. Existing strict tests preserved; no tests deleted or source/wiring tests introduced.

### Exact Pending Coordinator Commands
Run from H or coordinator's sanitized source mirror after all owners finish; no live .env reuse.
```powershell
cargo run -p glean-hub-sync-client --bin export_types -- glean-hub-core/glean-hub-sync-client-types/src/index.ts
$env:MONGO_TEST_URI='mongodb://127.0.0.1:27191/?replicaSet=doggo_standard_test'
$env:MONGO_TEST_ALLOW_DISPOSABLE='1'
$env:MONGO_TEST_ALLOWED_HOSTS='127.0.0.1:27191'
cargo test -p glean-hub-sync-engine -p glean-hub-sync-client -p glean-hub-sync --all-targets --features glean-hub-sync/mongo-integration-tests -- --test-threads=1
cargo build -p glean-hub-server
```
- Generated output path above is authoritative; do not hand-edit generated counters. Copy generated index back from sanitized mirror if that is where generation runs.
- Tests create only random `test_standard_sync_*` databases, require explicit seed-host allowlist and disposable authorization; delete only those test DBs on successful completion. Failed test fixtures may remain for protected investigation.

### Issues / Next Steps
- No ownership conflict or inaccessible implementation prerequisite encountered. No live migration or service operation attempted.
- Coordinator still owes actual compilation/generation/test counts, isolated API/browser/native verification, full external shutdown/restore evidence and rollout documentation. Route any source corrections here; do not interpret authored tests as executed proof.
- Unresolved questions: none. Live deployment remains separately authorized exact-target operation, not permission inferred from implementation.
