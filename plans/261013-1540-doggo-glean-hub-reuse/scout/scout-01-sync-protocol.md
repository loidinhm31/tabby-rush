# Sync protocol scout
Date: 2026-10-13. Read-only scout `SyncReuseScout` returned terminal complete. Parent verified critical source sections. Root `/home/loidinh/WS/glean-hub/`.

## Reusable surfaces
- `glean-hub-plugin/glean-hub-sync/src/models/api.rs:20-59`: admin register `{appId,schema,sharing?}` -> `{appId,apiKey}`; schema update separate.
- `models/app.rs:8-36`: table map, columns `{name,type,nullable}`, primaryKey.
- `middleware/dual_auth.rs:28-90`: app key + JWT, require apps claim contains appId; no admin bypass for sync.
- `routes/sync.rs:16-60`: push/pull/delta endpoint group. Server root mounts under `/api/v1`.
- `services/app_service.rs:54-115`: schema registration, per-app collection and hashed app key.
- `glean-hub-core/glean-hub-sync-engine/src/protocol.rs:20-188`: authoritative camelCase push/pull/delta DTOs. Kotlin reimplements wire mapping, not Rust binary bindings.

## Actual sync guarantees and limits
- `services/sync_service.rs:154-451`: per-record optimistic version checks, explicit syncedRecords/conflicts/failures, not batch transaction. Stored version is client supplied. Known older assumedServerVersion conflicts; no assumption skips that check. Use known assumption in Doggo, never omit to force overwrite.
- Existing rows get compare-and-set predicate using observed version. New rows use upsert with no observed-version predicate; parent identified delayed-create overwrite risk for scoped reproduction/fix, not yet a reproduced failure.
- No mutation ID/receipt collection; duplicate delivery cannot be classified through receipt lookup.
- `:454-533,599-668`: pull `(syncedAt,rowId)` strict-after pagination; no immutable watermark/snapshot. Delta is push then pull, not transaction.
- Equal timestamp/rowId across multiple tables can complicate cursor ordering; Doggo uses only todos. Full scans still do not claim snapshot isolation.
- `:670-755`, `config.rs:8-18`, `jobs/deleted_records_cleanup.rs:54-86`: tombstones expire by configured retention, default60 days; no RESET_REQUIRED/expired-cursor response.
- Row storage uses user/app/table/row keys; sharing can expand visible records but Doggo will not configure sharing.
- Revocation changes users.apps, not issued token claims; stale grants last until expiry/refresh.

## Native API-key caution
An app-wide key is extractable from distributed native clients. Scout suggested proxy/provisioning decision. Parent chose dedicated Doggo key treated as public app identification plus mandatory user JWT/grant, not as a user/admin secret; no extra proxy. See authoritative decisions for full rationale.

## Minimum fit
User approved reusing existing version-conflict policy, not strict LWW or exactly-once sync. Durable local queue, response reconciliation, full refresh and explicit conflict UI needed. Partial ack must match exact sent row/version; new local edits remain pending.

## Validation
Source inspection only; no execution/secrets/writes by scout. Parent health probes failed as recorded in decisions. No backend readiness certification.

## Unresolved prerequisites
Admin Doggo schema/key/grants; approved live test endpoint and isolated MongoDB; existing-app regression gate for any minimal server correction.
