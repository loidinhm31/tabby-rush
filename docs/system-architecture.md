# TabbyRush System Architecture

Last Updated: 2026-10-20
Status: Native Android/Desktop clients use ordinary Glean Hub registration and standard sync. Offline Gradle/package suites, disposable-local API/browser smoke, and a standalone Windows runtime smoke are reported passed. Interactive UI approval timed out and connected-Android acceptance was not run; native acceptance and release are not passed. Live production migration is unauthorized.
Related Docs: [Standard Sync Design](./standard-sync-cutover-design.md) · [API Contract](./api-contract.md) · [Codebase Summary](./codebase-summary.md) · [Deployment Guide](./deployment-guide.md) · [Release Acceptance](./release-acceptance.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md) · [Security & Recovery](./security-and-recovery.md) · [Android Acceptance](./android-acceptance.md) · [Linux Acceptance](./linux-acceptance.md) · [Windows Acceptance](./windows-acceptance.md)

---

## 1. Product and Runtime Boundaries

TabbyRush is a native Android and desktop task application built with Kotlin Multiplatform and Compose. `shared/` owns domain contracts, shared UI, account persistence, authentication, and synchronization. `androidApp/` and `desktopApp/` provide native lifecycle, platform storage, scheduler, and presentation integrations. Glean Hub is the remote account/authentication/schema service and task-sync backend; its admin UI is operational tooling, not a TabbyRush web app.

The current client synchronizes with an ordinary Hub app registration through standard `push`, `pull`, and `delta` routes under `/api/v1/sync/{app_id}/`; app-schema provisioning remains an administrator action, not automatic native startup behavior. The current binary does not use the former strict-v1/v2/v3 route trees, epochs, or operation-receipt contracts. Schema changes do not generate native UI, Kotlin fields, or Room columns automatically.

## 2. Component and Data Flow

```mermaid
flowchart TD
  User --> ComposeUI[Shared Compose UI]
  ComposeUI --> Store[RoomAccountStore]
  Store --> DB[(Room v4 / SQLite)]
  Coordinator[AccountSyncCoordinator] --> Store
  Coordinator --> Session[HubAuthSession]
  Session --> Vault[Platform Credential Vault]
  Coordinator --> HTTP[HubHttpClient / HubSyncClient]
  HTTP --> Hub[Glean Hub /api/v1]
  Hub --> Registry[Ordinary app schema registry]
  Hub --> Mongo[(Standard sync records)]
  Android[androidApp] --> ComposeUI
  Desktop[desktopApp] --> ComposeUI
  Store --> Scheduler[Persisted reminder/surface projection]
  Scheduler --> Platform[Android and desktop adapters]
```

Local edits are persisted before network activity. Account-scoped storage and auth generations fence stale callbacks after account changes. A sync round authenticates, reconciles standard push results, and obtains server state through pull/delta; delta is a sequential push followed by pull, not an atomic transaction. Room applies related state changes transactionally where implemented, but a client transaction does not create server-side group-write atomicity.

## 3. Native Persistence

`TabbyRushDatabase` is Room version 4 and registers explicit migrations 1→2, 2→3, and 3→4. Destructive fallback is prohibited. Its entities are:

| Table | Responsibility |
|---|---|
| `server_base` | Confirmed standard server projection, deletion state, record version, raw JSON and projection metadata. |
| `outbox` | Local submitted intent, delivery/review state, intent mask, current standard payload/envelope and version metadata. |
| `editor_drafts` | Uncommitted user edits, separate from server base and submitted intent. |
| `scan_staging` | Paginated full-scan rows isolated by scan generation before promotion. |
| `account_sync_meta` | Hub schema observation, pull checkpoint, scan generation/state, sync progress metadata. |
| `account_local_settings` | Device/account-local preferences, not synced as task data. |
| `reminder_delivery_ledger` | Durable reminder occurrence and delivery claims. |
| `legacy_sync_evidence` | Lossless archive of pre-v4 outbox SQLite values for review; never sent to sync endpoints. |

Room 3→4 archives every column/value of each existing outbox row as JSON, encoding SQLite FLOAT by its bit representation and BLOB as bytes. Unsupported SQLite storage types abort the migration rather than discard evidence. The legacy archive starts pending review; migration alone does not establish that an operation was unsent or accepted. The new outbox retains a link to its archived evidence and enters review state.

Account data uses an origin/user-derived account key and platform account directories. Platform credential vaults are Android Keystore, Linux Secret Service, and current-user Windows DPAPI. The desktop credential-vault file ACL is a separate concern from provisioning-file DACL; documented best-effort ACL behavior is not an owner-only guarantee.

## 4. Standard Synchronization

### 4.1 Wire Contract

All routes use `/api/v1`. Account routes include `/auth/register`, `/auth/login`, `/auth/refresh`, and `/auth/me`. Sync requests use Bearer JWT, `X-API-Key`, and `X-App-Id` headers. Push records carry `tableName`, `rowId`, raw JSON `data`, `version`, `deleted`, and `assumedServerVersion`; pull pagination uses the composite `{updatedAt,id}` checkpoint. Hub `schemaVersion` is returned as informational metadata.

The client enforces a 256 KiB serialized UTF-8 request-body limit before freezing a batch. A known record that individually exceeds this limit is marked conflicted/actionable and remains unsent; it is not represented as an uncertain network result. Fitting records can be submitted while other records remain pending.

### 4.2 Raw JSON, Schema and Acknowledgement

`server_base` and scan staging retain raw JSON so fields unknown to an older native build survive. Merging applies only keys selected by a valid explicit intent mask. The client does not infer unknown legacy intent as “all known fields.” Ambiguous legacy rows require user review; choices include discard, reapply explicit supported intent, or copy the latest server draft as a fresh root.

Hub `schemaVersion` is a positive exact integer used for admin schema concurrency, not a feature gate or Room migration trigger. Effective no-op schema changes do not increment it. Changed updates use `expectedSchemaVersion`; stale changed edits conflict rather than overwriting newer schema. UI schema additions do not generate native UI or database fields.

Only an explicit accepted `(tableName,rowId,version)` tuple confirms the submitted version. Pulled matching content/version/deletion can establish desired-state convergence, not operation-receipt proof. Standard sync does not provide strict exact-revision CAS receipts, server-enforced hierarchy, permanent tombstones, or transactional group writes. Hierarchy rules remain client-side; arbitrary API clients are not bound by the retired strict hierarchy validator.

### 4.3 Full Scan and Restart Safety

The client starts a full scan from a null checkpoint every sync round/reconnect because no trustworthy server retention horizon is assumed. Pages are staged with a generation and promoted only after a complete scan. If a process restarts, staging from an older generation is discarded and a fresh scan begins. An incomplete scan never proves that an absent record was deleted. The composite checkpoint is not replaced with `serverTimestamp`.

### 4.4 Legacy Conflict Review and Copy-Latest

Room v4 migration archives legacy outbox values without assuming delivery outcome. If intent is ambiguous, the row remains for review. Reapply is constrained to explicit supported changed fields and never falls back to all known fields. Discard removes the reviewed conflicting intent. Copy-latest reads the latest authoritative projection and creates a new root record; it does not resurrect/reparent the conflicted row implicitly.

## 5. Hub Schema and Operator Boundary

Ordinary app schema management is separate from task transport. `GET /api/v1/apps/{app_id}` reads metadata and `PUT /api/v1/apps/{app_id}/schema` uses `expectedSchemaVersion`. The current provisioning script creates a new ordinary registration only; it does not convert an existing strict registration. On Windows, its output directory must already exist as a private, non-symlink directory with protected DACL/owner limited to the current user, `SYSTEM`, and `Administrators`. It refuses unsafe directories without changing permissions and establishes the credential file DACL before registration POST.

The standard-sync conversion operator is source-built separately from the native client. Parent reports 7/7 disposable-Mongo operator checks passed, including concurrency, deadline, and recovery. Any failed mutating run leaves a durable exclusive claim with no automatic expiry/takeover. Recovery requires `maintenance.executionRecovery` for the exact `expectedExecutionId`, `executorStopped: true` with `executorStopEvidence`, `mongoOperationsDrained: true` with `mongoDrainEvidence`, and `authorizedActions` containing `recoverExecution`. Renew/verify the maintenance window before every collection rename and registry CAS. The operator does not restart services, release external isolation, or automatically roll back. Live cutover requires separate explicit approval and verified backup/isolation scope.

## 6. Authentication, Scheduling and Announcements

`HubAuthSession` owns account authentication state and generation fencing. Token refresh is coordinated to avoid concurrent refreshes; configured requests use the Hub HTTP allowlist and dual-auth sync headers. Sign-out/account leave freezes mutations, cancels account-scoped work, handles unresolved local work according to the selected leave action, and clears account data/credentials under the guarded teardown protocol.

Reminder scheduling is driven from persisted task/server/outbox state rather than uncommitted editor drafts. Android uses native alarm/notification integrations; desktop scheduling is process-scoped with platform adapters. Native service behavior, widget/window/tray appearance, accessibility, Doze behavior, and connected-device convergence require platform acceptance and are not established by backend smoke or package generation.

Announcements are a separate Hub service and contract from task synchronization. The TabbyRush client uses authenticated inbox APIs and in-memory announcement state rather than Room task tables or an offline announcement outbox. See [announcement contract](./announcement-contract.md) for the versioned wire contract.

## 7. Current Verification and Release Boundary

Reported disposable-local API/browser smoke used MongoDB 27191, Hub 3100, and admin UI 3102. It exercised ordinary provisioning, standard create/pull/delta/update/stale-conflict/delete, unknown JSON retention, nullable `customNotes` schema update 1→2, no-op version stability, and stale admin draft preservation.

A separate standalone Windows runtime smoke passed against disposable loopback services: Windows DPAPI, real HTTP auth, Room offline restart, two independent stores using actual coordinator push/pull/delta, schemaVersion 2, unknown nested JSON and `customNotes` retained through native edit, parent/child sync, and group deletion. It was invoked through a strict Gradle JavaExec entry point. This is not interactive UI acceptance or connected-Android proof. UI provider approval timed out twice; connected-device verification was not run. Do not claim native acceptance passed, production migrated, or release unblocked.

## 8. Historical Strict Architecture

Strict-v1/v2/v3 route, epoch, schema-fingerprint, digest, migration, and acceptance descriptions in earlier phase documents are historical snapshots from 2026-10-14 through 2026-10-20. They are not current runtime guidance. Former strict runbooks and strict-specific CLI/rehearsal scripts have been retired. See [release acceptance](./release-acceptance.md).
