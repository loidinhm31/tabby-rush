# TabbyRush Local Storage and Standard Sync

Date: 2026-10-21  
Status: Room v5 and collaborative standard sync implemented (Phases 01–08). Dedicated runtime harnesses prepared in Phase 09. G0 policy validation confirmed 2026-10-21; G1–G6 runtime verification pending Main integration. Release remains blocked; production migration unauthorized.  
Related Docs: [System Architecture](./system-architecture.md) · [API Contract](./api-contract.md) · [Standard Sync Design](./standard-sync-cutover-design.md) · [Security & Recovery](./security-and-recovery.md) · [Release Acceptance](./release-acceptance.md)

---

## 1. Storage Architecture

TabbyRush stores account-scoped local state in Room/SQLite through the shared Kotlin Multiplatform data layer. Destructive migration fallback is prohibited. The registered migration chain is Room 1→2, 2→3, 3→4, and 4→5. Android and JVM/desktop builders register the same migrations.
Account partitions use a canonical account key derived from Hub origin and user identity. Platform account data is stored under an account-specific directory; credentials are held separately in Android Keystore, Linux Secret Service, or user-scoped Windows DPAPI. Account leave/wipe coordinates scheduler cancellation, database/artifact cleanup, and credential clearing. OS-held widget/notification content is not guaranteed erased when the OS rejects cleanup.

### Room v5 Tables

| Table | Purpose |
|---|---|
| `server_base` | Latest confirmed standard server projection, version/deletion metadata, preserved raw JSON, projection status, and canonical owner binding. |
| `outbox` | User-submitted intent and delivery/review state. Includes intent mask, current standard payload/envelope, record version, canonical TaskRef, and assumed server version. |
| `editor_drafts` | Uncommitted edits, separate from confirmed server data and submitted intent. |
| `scan_staging` | Pull pages held under a scan generation until a full scan can be applied. |
| `access_check_staging` | Staged access-validation results across multiple pages, validated against a consistent access revision before atomic promotion. |
| `task_access` | Authoritative reconciled task access rights, capability flags (`editContent`, `manageAssignment`, `deleteTask`, `createChild`), and hierarchy coverage (`full-group`, `child-only`). |
| `access_loss_evidence` | Non-replayable, content-free records of authoritative access loss (identity, versions, timestamp, reason) retained for review without leaking purged payloads. |
| `account_sync_meta` | Informational Hub `schemaVersion`, checkpoint, scan generation/state, sync progress metadata, and cached access revision. |
| `account_local_settings` | Account/device-local reminder and notification defaults; not synchronized as task data. |
| `reminder_delivery_ledger` | Durable reminder occurrence and delivery-claim state. |
| `legacy_sync_evidence` | Lossless archive of pre-v4 outbox SQLite values, retained for explicit review and never sent to sync routes. |

### Room 4→5 Migration and Two-Stage Binding

`MIGRATION_4_5` updates local database schemas to support canonical identities (`ownerUserId`, `tableName`, `rowId`), relationship tracking, and access state.

To prevent guessing owner identities from one-way `AccountKey` hashes, legacy rows are preserved with `ownerBinding = LOCAL_ACCOUNT` and nullable `ownerUserId`. During store initialization, when an authenticated or restored `AccountIdentity` is supplied, `RoomAccountStore` verifies that `AccountKey.create(origin, userId)` matches the current partition and binds legacy rows atomically. Unresolved legacy rows survive upgrade and display offline, but remain strictly local-only and cannot be dispatched to sync endpoints until bound.

### Room 3→4 Legacy Archive

`MIGRATION_3_4` copies every old outbox column into `legacy_sync_evidence.originalJson` before rebuilding the active outbox. SQLite values retain type-sensitive representation: `NULL` remains JSON null, integers remain integer values, text remains text, FLOAT stores its bit representation, and BLOB stores its exact bytes as hex. An unsupported SQLite storage type aborts the migration rather than dropping evidence. Archived rows begin `PENDING_REVIEW`; the new outbox row uses review state and retains a link to its original evidence.
## 2. Local Mutation and Account Isolation

A task edit is persisted locally before network dispatch. The local task projection combines confirmed server base, submitted outbox intent, and editor draft state; scheduling projections intentionally exclude uncommitted drafts. Outbox processing is serialized per account/coordinator, and authentication generation/account identity checks prevent stale asynchronous completions from modifying a switched or leaving account.

Interrupted in-flight work is not treated as a server rejection. Local delivery state retains enough evidence for reconciliation/review. A newer user draft remains distinct from an older in-flight version so an older response cannot erase newer local work.

## 3. Standard Hub Protocol

All endpoints use `/api/v1`. Authentication uses `/auth/register`, `/auth/login`, `/auth/refresh`, and `/auth/me`. Standard task synchronization uses:

| Request | Route | Purpose |
|---|---|---|
| `POST` | `/sync/{app_id}/push` | Submit versioned records |
| `POST` | `/sync/{app_id}/pull` | Read records from a `{updatedAt,id}` checkpoint |
| `POST` | `/sync/{app_id}/delta` | Run push followed by pull |

Sync requests carry Bearer JWT, `X-API-Key`, and `X-App-Id`. Push records contain table name, row ID, raw JSON data, record version, deletion state, and `assumedServerVersion`. Pull records carry version, synchronization time, deletion state, and raw JSON. `schemaVersion` in push/pull response is informational Hub metadata, not an admission gate or native feature switch.

Delta is not atomic: it performs push then pull sequentially. Standard sync deliberately does not promise strict operation receipts/exact-revision CAS, server-side hierarchy enforcement, permanent tombstones, or transactional group mutations.

### Acknowledgements and Convergence

Only an explicit accepted `(tableName,rowId,version)` tuple clears the exact submitted local version. A pulled record that happens to match local version/content/deletion is desired-state convergence, not proof the operation was accepted. Do not clear local work on a matching projection alone. Client task-group rules remain client-side; the standard Hub API is not the retired strict hierarchy validator.

## 4. Unknown JSON and Intent-Masked Merging

`server_base` and staged pull records keep raw JSON alongside the typed task projection. When a local mutation is sent, known fields selected by a valid intent mask are merged into the latest base JSON; unrelated and unknown keys remain intact. Unknown JSON is not mapped to generated Kotlin fields or Room columns automatically.

If legacy evidence lacks enough information to identify the user's intended changed fields, keep it for review. Never interpret unknown/ambiguous legacy intent as “reapply every known field.” The user can discard the conflicted intent, reapply only an explicit supported changed-field intent, or copy the latest server draft as a fresh root. Copy-latest reads the current server projection and creates a new root record; it does not silently update, resurrect, or reparent the original record.

## 5. Full-Scan and Restart Semantics

The client does not assume a trustworthy Hub retention horizon. Each sync round/reconnect performs a full scan from a null checkpoint. Pull pages are staged under a generation and applied only after scan completion. On restart, stale generation staging is discarded and a fresh full scan begins. No deletion is inferred from an incomplete scan or from absence before the scan's terminal page.

The persisted pull checkpoint is the composite `{updatedAt,id}` value; `serverTimestamp` is not used as a substitute. A server-retention policy change requires coordinated client recovery assumptions; the current client avoids that dependency by full scanning before each round's reconciliation.

## 6. Body Budget and Partial Dispatch

The native client checks serialized UTF-8 request bytes before freezing a push batch. The body limit is 256 KiB. A single known record that exceeds the limit is marked conflicted/actionable and remains unsent; this is a known unsent record, not an uncertain network delivery. Other fitting records may still be submitted, while remaining records stay pending. Never report an unsent oversize as accepted or as a transport timeout.

## 7. Schema Version and Native Model Independence

Hub `schemaVersion` is a positive exact JSON integer used to coordinate admin schema changes. It starts at 1, increments once for an effective schema change, and remains unchanged for an effective no-op. Admin schema PUT uses `expectedSchemaVersion`; stale changed data conflicts rather than overwriting newer schema. A stale request that is already effectively equal may return current authoritative state.

The client stores the Hub version as informational metadata. It does not drive feature gates, Room migration, code generation, or UI generation. Hub schema may permit nullable/custom keys such as `customNotes`; the native client preserves such keys in raw JSON but does not display or edit arbitrary schema columns unless product code separately implements them.

## 8. Conflict Resolution, Revocation Purge, and Privacy Guarantees

Conflict resolution is explicit. Discard removes the reviewed conflicting intent; selective reapply creates a new supported intent based on the current confirmed state; copy-latest creates a fresh root from the latest server projection. Unknown legacy intent never takes an automatic all-fields replay path.

In collaborative mode, authoritative loss of access (via revocation or unassignment) enforces a privacy-first purge:
1. The active task projection is immediately removed from the user's task lists.
2. Scheduled reminders and active notifications for the task are cancelled.
3. Active `server_base`, `editor_drafts`, and pending `outbox` payloads for the task are purged from active storage.
4. Non-replayable, content-free audit records are written to `access_loss_evidence` recording only canonical identity, opId, base/submitted version, timestamps, and reason (`REVOCATION`). No task title, deadline, description, or payload content is retained.
5. A subsequent regrant fetches current authorized state from the server without automatically replaying purged unsent edits.

Dismiss acknowledges the selected task's content-free access-loss notice; it does not retry or discard unrelated pending work. Later scans of the same unavailable task still purge newly staged payloads and restored deleted records, but do not recreate that notice when no new draft or submitted intent was lost. A confirmed regrant followed by a new revocation remains a new loss and creates fresh evidence.

Parent/child task behavior, last-child completion preservation, group deletion, and reminder eligibility are native client/domain behavior. The standalone Windows runtime smoke exercised parent/child sync and group deletion against a disposable loopback Hub, but standard sync does not enforce those rules for arbitrary clients and the smoke is not interactive UI or connected-device acceptance.

## 9. Current Evidence and Boundaries

Disposable-local API/browser smoke used MongoDB 27191, Hub 3100, and admin UI 3102. Reported checks covered ordinary private provisioning; standard create/pull/delta/update/stale-conflict/delete; unknown JSON retention; nullable `customNotes` schema update 1→2; no-op version stability; and stale admin draft preservation.

A separate standalone Windows runtime smoke passed through a strict Gradle JavaExec entry point against disposable loopback services. It exercised native Windows DPAPI, real HTTP authentication, Room persistence across offline restart, two independent stores using the actual coordinator's push/pull/delta, `schemaVersion` 2, unknown nested JSON and nullable `customNotes` retained through a native edit, and parent/child sync/group deletion. This does not establish interactive native UI acceptance or connected-Android proof. UI provider approval timed out twice; connected-device acceptance was not run. No production database has been migrated or authorized; native acceptance/release remain incomplete.

## 10. Historical Strict Sync

Earlier strict-v1/v2/v3 outbox, epoch, digest, receipt, transition, and runbook descriptions are historical snapshots from 2026-10-14 through 2026-10-20. They do not describe current Room v5 or the current binary. Former strict-specific scripts and runbooks have been retired.
