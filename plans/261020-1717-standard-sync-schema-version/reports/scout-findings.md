# Parallel Scout Findings

Date: 2026-10-20. Both scouts completed. Source inspection only; no code edits, tests, DB reads/writes or secrets accessed by scouts.

## NativeSyncScout

- Shared strict runtime: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/{hub-sync-client,hub-sync-dtos,account-sync-coordinator,outbox-reconciliation,staged-full-scan,contract-transition-coordinator}.kt`.
- `hub-sync-client.kt:18-38,125,215,305,369-450` uses only `/strong/*`. `account-sync-coordinator.kt:114-319` depends on bootstrap protocol/epoch and strict transition state.
- `outbox-reconciliation.kt:34-225,381-499` freezes operation identity/revision/digest, enforces FIFO and exact acknowledgements. Uncertain strict payloads cannot be replayed as ordinary standard writes.
- Room v3: `data/{tabbyrush-database,database-migrations,account-sync-meta-entity,outbox-entity,server-base-entity,scan-staging-entity}.kt`. No standard checkpoint or Hub schemaVersion.
- `room-account-store.kt:245-405,795-900` owns atomic intent and hierarchy operations. `contract/task-hierarchy-projector.kt:31-130` handles parent completion, deletion dominance and unresolved children.
- `auth/hub-http-client.kt:150-211` needs exact dual-auth standard routes; do not weaken origin/account/refresh guards.
- Android `account-runtime-factory.kt:32-150`, Desktop `main.kt:299-413` construct shared runtime. Shared UI `todo-list.kt:67,139` has strict-v3 capability gates.
- Future-field data is at risk: typed decode plus full-record replacement can drop unknown JSON keys. Retain opaque base JSON and merge local intents.
- Keep domain behavior tests; remove strict-only runtime/vector assertions once migration readers no longer consume them. Do not keep old runtime aliases.

## HubSyncScout

Paths below relative to `G:/ws/sharing/glean-hub`.

- Actual routes: `glean-hub-plugin/glean-hub-sync/src/routes/sync.rs:16-122`, all POST `/api/v1/sync/:app_id/{push,pull,delta}`.
- Standard protocol: `glean-hub-core/glean-hub-sync-engine/src/protocol.rs:19-199`: full `data`, client `version`, `assumedServerVersion`; explicit `syncedRecords`, conflicts and failures; pull checkpoint `{updatedAt,id}`; delta is sequential push then pull, not a transaction.
- `services/sync_service.rs:330-450` rejects a stale assumption only if stored version is greater; write predicate uses observed version to avoid racing overwrite. No strict receipts. Conflicts contain versions, not full remote state.
- `services/sync_service.rs:455-533` pulls by `syncedAt,rowId`, returns composite checkpoint. Parent reviewed actual hasMore implementation: a full final page may cause one additional empty pull; do not expand scope to fix incidental inefficiency.
- `services/schema_validator.rs:175-230` checks required declared fields/types but permits extra keys. No standard TabbyRush hierarchy enforcement.
- Strict and standard collection name is the same (`appTabbyRush`), but `models/record.rs:12-25` requires optional ObjectId, `appId`, `version`, `syncedAt`. Strict rows use string `_id`, `revision`, `lastOp` and omit generic fields.
- `services/app_service.rs:634-674` updates ordinary schema without version; requires `syncMode` absent. `middleware/sync_mode_guard.rs:11-42` rejects standard access to strict apps.
- Schema writers: `models/app.rs` normal/strict constructors; `services/app_service.rs` normal registration, strict registration, update; `services/strict_upgrade_service.rs:529-549` strict table publication. Version must cover all, not only UI saves.
- Schema DTOs/routes: `models/api.rs`, `routes/apps.rs`. Frontend `glean-hub-app/src/services/sync-admin-service.ts` and `pages/admin/edit-app.tsx` currently ignore versions.
- Generic response types generated through `glean-hub-core/glean-hub-sync-client/src/bin/export_types.rs` into `glean-hub-core/glean-hub-sync-client-types/src/index.ts`; migrate changed struct literals/callers using language-server references during implementation.
- Standard indexes: `app_service.rs:707-755`: unique `(userId,tableName,rowId)` plus sync/checkpoint indexes. Strict scan index is non-unique; conversion must recreate correct indexes.
- Standard tombstone sweep: `sync_service.rs:670-698`, configured default 60 days. Not TTL; strict tombstones lack deletedAt. Standard guarantees differ.
- `embed-app/money-insight/src/adapters/web/sync/IndexedDBSyncAdapter.ts:164-210` clears only explicit acknowledgements; do not copy code-notes' blanket success clearing.
- No source proof of current live app registry state; no DB was queried. Keep `glean-hub` app normal, do not assume or mutate its live registration.

## Parent runtime checks

- Existing `GET http://127.0.0.1:3000/api/v1/health` returned HTTP 200, `{"status":"ok","service":"glean-hub-server"}`.
- Java 21.0.9, Node v24.21.0, Cargo 1.96.0 observed.
- `adb` absent from PATH; resolved `C:/Users/loidi/AppData/Local/Android/Sdk/platform-tools/adb.exe`. Its `devices` command reports `emulator-5554` attached.
- These checks establish prerequisites only, not standard-sync behavior or migration success. No builds, native UI smoke, schema saves or data conversion executed.

## Planning decisions

- Standard schema validation remains; no strict schema fingerprints or exact schemaVersion handshake.
- Server metadata counter starts at 1; effective changes increment; no-op/retry does not; expectedSchemaVersion protects concurrent editing.
- Expose counter via ordinary sync push/pull responses; no new admin access for native users.
- Keep generic 60-day configured retention, with conservative full rescan/review after retention-length offline gaps. Do not introduce another per-app strict validation/retention engine.

## Unresolved questions

None required for the plan. Live target authorization, backup restore rehearsal and multi-device runtime evidence remain execution gates.
