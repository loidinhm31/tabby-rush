# TabbyRush standard-sync cutover runbook

2026-10-20. Implemented operator/native source; **no live cutover authorized or executed**. Android/Desktop remain native. Hub web UI manages schemas only.

Current contracts: [API](api-contract.md), [storage](local-storage-and-sync.md), [design](standard-sync-cutover-design.md). Exercised evidence: [integration report](../plans/261020-1717-standard-sync-schema-version/reports/verification-final.md). Hub operator definitions are in sibling `glean-hub/glean-hub-plugin/glean-hub-sync/src/operator/{mod,journal,conversion,publication}.rs`; full Hub guide: `glean-hub/docs/standard-sync-schema-version.md`.

## Preservation and deployment boundary

- Room exports 1/2/3 upgrade explicitly to 4 without destructive fallback. Confirmed task data, drafts, legacy outbox/evidence, settings and reminder ledger are retained. Back up native account profiles before a real release; never change the account origin spelling to bypass an existing profile.
- Remote conversion preserves ownership, all 17 task fields, tombstones and strict revision as ordinary row version. Existing API keys/grants are not rotated by conversion. The original collection is retained as a UUID-bound archive.
- Only proven never-dispatched legacy intent translates automatically. Unknown or uncertain old operations remain reviewable; never clear them merely because new transport works. Unknown-field JSON is retained through known-field edits. Copy creates a new root from latest local intent rather than resurrecting an old identity.
- Old strict binaries cannot write the converted collection. New clients have no strict fallback. Do not publish a native rollout as compatible with an unconverted strict registry without explaining that synchronization remains offline/rejected until cutover.
- Standard desired-state sync has weaker guarantees: no strict receipts, server hierarchy enforcement, permanent tombstones or atomic groups. Full scans before every round and fresh generations after interruption prevent unsafe absence decisions; they do not recreate strict guarantees.

## Before approving a target

Obtain authorization naming exact Hub origin, MongoDB database, app `tabby-rush`, backup/restore scope, operator and maintenance window. The plan alone is not that authorization.

1. Close outstanding native/platform and release gates; current interactive Android/Desktop/connected acceptance is held. Review remaining browser edge-case acceptance separately from passed smoke.
2. Inventory every Hub node and every background mutation/cleanup job, including disconnected workers. Establish external ingress isolation. Stop all listed processes; do not rely only on plugin flags or a single-node route fence.
3. Verify an independent backup/restore rehearsal covering application collection, `_schemas`, coordination, migration/operation evidence and indexes. The archived strict-v3 restore example is not an approved backup procedure.
4. Capture exact typed BSON `operator::Snapshot` using `operator::journal::capture` while isolated. Preserve BSON types, registry, rows, indexes, collection UUID/spec, coordination and migrations. Keep a separate full backup and verified restore evidence.
5. Produce `SnapshotManifest` and `MaintenanceManifest` matching the exact Rust structs. Bind origin/database/app/migration, Mongo topology, snapshot/registry/index/UUID hashes, owners/count, stopped-process inventory, external isolation, UTC window and authorized actions. These are audited evidence, not checkbox defaults.
6. Protect snapshot/manifests: absolute regular non-symlink files, read-only, operator-only filesystem permissions (Windows also allows SYSTEM/Administrators). Never include URI credentials or user payloads in reports/screenshots.

## Operator sequence

Run from the approved sibling Hub checkout. Supply a protected environment variable containing the approved MongoDB URI; no dotenv/default target is loaded by the binary.

```sh
cargo build -p glean-hub-sync --bin standard-sync-cutover
standard-sync-cutover --dry-run --mongo-uri-env CUTOVER_MONGO_URI --db-name "$APPROVED_DB" --app-id tabby-rush --migration-id "$MIGRATION_ID"
```

Dry-run reads only. Inspect mode, source epoch/counter, owners/counts and validation errors without modifying storage. A successful dry-run is not mutation approval.

For each separately authorized action, use exactly one mode `--prepare`, `--publish`, `--resume` or `--rollback`, the same explicit target flags, and:

```text
--maintenance-manifest <absolute-protected-maintenance-file>
--snapshot-manifest <absolute-protected-snapshot-manifest>
--confirm-target <exact-database>/tabby-rush
```

- Prepare stages ordinary ObjectId records and standard indexes without replacing original rows. Validate complete payload/identity/version equivalence.
- Publish uses nonoverwriting archive/activation renames and registry CAS. This sequence is not atomic. Keep external all-node/job isolation throughout.
- Read back ordinary registry mode/counter, unchanged key hash/grants, row owners/live/deleted counts, data/version equality, standard indexes and archive UUID. Rehearse native convergence before explicitly reopening traffic.
- The binary never starts/restarts services, releases external isolation or deletes the archive. Do not add these side effects to an unattended wrapper.

## Interrupted or refused mutation

Every mutating invocation claims `_standard_sync_operator_executions` for the app, including identical migration IDs. No automatic expiry/stealing. Success releases only the exact token after final readback; any error, crash, cancellation or uncertain acknowledgement retains ownership.

1. Keep external isolation. Inspect the bound target, snapshot hash and execution ID; do not manually delete the claim.
2. Prove the previous executor stopped **and** all its outstanding MongoDB operations drained. Process death alone is insufficient.
3. Obtain renewed protected maintenance authorization. Use only `--resume` or `--rollback`, add `recoverExecution` to `authorizedActions`, and put the exact prior ID and evidence in the maintenance manifest's root `executionRecovery` object: `expectedExecutionId`, `executorStopped`, `executorStopEvidence`, `mongoOperationsDrained`, `mongoDrainEvidence`.
4. Recovery conditionally replaces that exact claim/target/snapshot. Reusing consumed authorization refuses. The operator checks ownership and its immutable deadline immediately before every rename/CAS; expiry retains evidence and requires renewed recovery.

Before ordinary registry publication, authorized rollback restores the UUID-bound original without overwriting another collection. After publication/new ordinary writes, rollback refuses: preserve evidence and use separately approved forward repair/reverse-data migration. Never restore an older snapshot over new user work.

## Fresh ordinary registration and schema editing

`hub-schema-provision.mjs` registers/verifies an ordinary app; it does **not** convert an existing strict registration. It requires explicit origin/schema/out/confirm-app and `GLEAN_ADMIN_TOKEN`. Windows output must be inside an existing private protected non-symlink directory before file creation; the tool refuses unsafe custody and never silently changes existing directory permissions or overwrites a key file.

After conversion, edit ordinary schema via Hub builder/Paste JSON. Server assigns `schemaVersion`; save sends loaded `expectedSchemaVersion`. Effective change increments, identical save does not, stale changed save returns 409 with drafts retained. Nullable fields do not generate native controls; required/type changes need compatible clients and data evolution.

## Current acceptance

Strict-verification native tests/builds and actual core HTTP/Room/DPAPI smoke passed; real local Mongo conversion and API/admin smoke passed. Interactive native surfaces, connected Android and remaining browser edge cases are not claimed accepted. No production conversion, installed-profile mutation or commit was performed. Keep the overall rollout phase open until those gates and target authorization are resolved.
