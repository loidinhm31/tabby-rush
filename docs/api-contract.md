# TabbyRush API & Standard Sync Contract

Date: 2026-10-22  
Status: Standard sync is the current TabbyRush client protocol, including collaborative task assignment on existing push/pull/delta routes. Strict-sync contracts below are historical and do not describe the current binary.  
Related Docs: [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Toolchain Versions](./toolchain-versions.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md)

---

## 1. Origin, Prefix & Authentication

TabbyRush uses Glean Hub's ordinary app registration, account authentication, and standard sync routes. It does not use strict-v1/strict-v2/strict-v3 bootstrap or operation endpoints.

- The configured Hub origin is a canonical origin (scheme, host, optional port), without path, query, fragment, or user credentials.
- API routes use the `/api/v1` prefix, including authentication, app, and sync routes. Do not omit `/api/v1` from auth paths.
- Sync requests use `Authorization: Bearer <accessToken>`, `X-API-Key: <appApiKey>`, and `X-App-Id: <appId>`.
- Release mode requires HTTPS. Debug mode additionally permits the configured loopback development origins; redirects are not followed for sync requests.
- The client caps a serialized sync request body at 256 KiB. This is a client-side transport limit, not an atomicity guarantee.
- Credentials are held in platform-backed storage: Android Keystore, Linux Secret Service, and Windows DPAPI. No plaintext credential fallback is intended.

### 1.1 Authentication Routes

Payloads use JSON camelCase. These paths are relative to `/api/v1`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Register an account |
| `POST` | `/auth/login` | Authenticate and obtain account/app access data |
| `POST` | `/auth/refresh` | Exchange refresh credentials |
| `GET` | `/auth/me` | Read authenticated account identity |
| `POST` | `/apps/access-requests` | Request app access |
| `GET` | `/apps/access-requests/me` | Read the account's access requests |
| `GET` | `/auth/lookup?username={username}` | Username identity lookup used by the assignee picker; lookup alone does not grant app membership or task access |

Exact server response fields are governed by the deployed Hub contract; do not infer them from this route summary.

---

## 2. Standard Sync Routes

All paths below are relative to `/api/v1`. The `{app_id}` path segment is the Hub app identifier. Sync requests use the dual-auth headers above.

### Collaborative Assignment Data

The native client adds no dedicated assignment route. Assignment policy is configured in the ordinary app `SharingConfig`; the current [Hub schema input](../integration/glean-hub/tabbyrush-schema.json) defines `todos.parentTaskId` as a nullable string and `taskAssignees` with required string columns `id`, `taskId`, and `userId` (`id` is the primary key). The sharing mapping uses `sharingTable: "taskAssignees"`, `parentIdField: "taskId"`, `userIdField: "userId"`, `parentTable: "todos"`, and `childTables: []`. See [Hub Schema Integration](./hub-schema-integration.md) for the policy configuration.

`assignmentPolicy` version 1 sets `hierarchyParentField: "parentTaskId"` and allows assignee content edits only to `title`, `completed`, `deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, and `scheduleToken`. Structural `parentTaskId` changes and assignment management are not in that allowlist.

A live assignment is represented by the ordinary push record envelope and one owner-scoped relationship slot:

```json
{
  "tableName": "taskAssignees",
  "rowId": "<taskId>",
  "ownerUserId": "<creatorUserId>",
  "data": {
    "id": "<taskId>",
    "taskId": "<taskId>",
    "userId": "<recipientUserId>"
  }
}
```

For this slot, `ownerUserId` qualifies the canonical task owner and `rowId == data.id == data.taskId`. Reassignment updates the same slot. Unassignment is a deleted relationship record (`deleted: true`) at that same identity; its tombstone is retained to prevent stale recreation. The client may include `userId: null` in tombstone data, but a live assignment has a string `userId`. Task content remains in the creator-owned `todos` row; the relationship record does not copy task content to the recipient.

The native client sends assignment changes as standard records through `POST /sync/{app_id}/push` or the `push` portion of `POST /sync/{app_id}/delta`; it reconciles shared task data and access through the standard pull route. No dedicated assignment sync endpoint is added.

### 2.1 Push — `POST /sync/{app_id}/push`

`PushRequestDto` contains `records`, optional `clientTimestamp`, and optional `collaborationVersion` (must be 1 for collaborative apps). Each record has `tableName` (defaults to `todos`), `rowId`, optional `ownerUserId` (mandatory for collaborative apps), raw JSON `data`, `version`, `deleted` (defaults to `false`), and `assumedServerVersion`.

The response contains `synced`, `conflicts`, optional `failures` and `syncedRecords`, `serverTimestamp`, informational `schemaVersion`, and `collaborationVersion: 1` when enabled. Conflict entries identify the table, row, and client/server versions. `syncedRecords` names exact accepted `(ownerUserId, tableName, rowId, version)` tuples.

Only an explicit accepted tuple is confirmation that a particular submitted version was accepted. A later pull that happens to match local content is desired-state convergence, not an operation receipt.

### 2.2 Pull — `POST /sync/{app_id}/pull`

`PullRequestDto` contains an optional `{updatedAt, id}` checkpoint, `batchSize` (defaults to 100), `tables` (Hub default `["todos"]`; the native collaborative client explicitly requests `["todos", "taskAssignees"]`), optional `collaborationVersion: 1`, optional `expectedAccessRevision`, and optional `accessChecks: [{ownerUserId, tableName, rowId}]`.

When `accessChecks` is present, pull runs in validation-only mode:
- Bounded to <= 100 keys and 256 KiB body limit.
- The response contains `accessRevision` and `accessResults` matching each requested record: `{ref, status: "accessible", capabilities: {editContent, manageAssignment, deleteTask, createChild}, hierarchyCoverage: "full-group" | "child-only" | "standalone"}` or `{ref, status: "unavailable"}`.
- Checkpoint is unchanged, hasMore is false, and no content rows are returned.
- An empty array `accessChecks: []` serves as the final confirmation boundary at access revision E.

When `accessChecks` is omitted, pull performs a normal data scan returning records, the next checkpoint, `serverTimestamp`, `hasMore`, `schemaVersion`, and `accessRevision`. In collaborative mode, tie-breaker sorting uses unique stored record ID, not row ID.

### 2.3 Delta — `POST /sync/{app_id}/delta`

The request has optional `push` and `pull` bodies; the response has optional `push` and `pull` results. Delta performs push followed by pull; it is not a transaction and does not make the two phases atomic. For collaborative apps, both parts require `collaborationVersion: 1`.

### 2.4 Collaboration Capability Cutoff & Guard

For apps with an active `assignmentPolicy`:
- **Capability Cutoff**: Any request to `/push`, `/pull`, or `/delta` lacking `collaborationVersion: 1` fails immediately with HTTP 426 `COLLABORATION_REQUIRED` before processing any records or reading content. No unsafe private-only fallback is attempted.
- **Single App Guard**: All collaborative mutations are serialized using an internal MongoDB document in `_sync_assignment_fences` per app. Any grant, hierarchy, or visibility change increments `accessRevision`.
- **Revision Mismatch**: An in-flight change returning HTTP 409 `ACCESS_REVISION_CHANGED` invalidates current scan/validation staging and forces a restart from a null checkpoint.
### 2.5 Version and Schema Semantics
- `schemaVersion` is positive exact-integer Hub metadata (bounded by JavaScript's safe-integer range), used for schema concurrency; it is not a native feature gate or a request to generate UI/database fields.
- Admin schema updates use `PUT /api/v1/apps/{app_id}/schema` with `schema` and `expectedSchemaVersion`. A changed schema commits only against the exact current version; a stale edit conflicts. An effective no-op does not increment the version.
- TabbyRush preserves unknown JSON fields when applying intent-selected local changes. Hub schema additions do not generate Kotlin models, Room columns, or native UI automatically.
- For the admin editor, a stale changed save returns 409 and retains the draft; the latest server schema is read-only until the user explicitly discards/reloads. A stale request whose effective schema is already current may return the authoritative current state.

---

## 3. Standard Sync Guarantees & Limits

Standard sync is a desired-state synchronization protocol, not the retired strict operation-receipt protocol. It does not provide strict exact-revision compare-and-swap receipts, server-enforced hierarchy, permanent tombstones, or transactional group writes. Do not rely on convergence, a matching version, or matching full JSON as proof that an individual operation was accepted.

In collaborative mode, content remains in creator-owned `todos` rows; `taskAssignees` relationship rows use `rowId == data.id == taskId`. Assignment tombstones are exempt from generic cleanup to prevent stale recreations. Direct child grants require an internally live same-owner root parent on the server; child-only recipients lack the parent locally due to privacy, not server absence.

Authoritative access loss purges active base, draft, and outbox payloads locally, retaining non-replayable content-free evidence in `access_loss_evidence`. Regrant hydrates the live task fresh without replaying purged unsent edits.

---

## 4. Admin App Registration & Schema Management

TabbyRush uses ordinary app registration, not `/strict-apps` provisioning. The current provisioning script creates a new ordinary registration and must not be used to convert an existing strict registration.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/apps` | Create an ordinary app registration (admin) |
| `GET` | `/api/v1/apps/{app_id}` | Read registered app/schema metadata |
| `PUT` | `/api/v1/apps/{app_id}/schema` | Conditionally update schema using `expectedSchemaVersion` |

On Windows, provisioning requires an existing private, non-symlink output directory whose protected DACL and owner restrict access to the current user, `SYSTEM`, and `Administrators`. The script refuses an unsuitable directory and does not change its permissions. It applies/verifies the generated file's DACL before sending the registration request.

---

## 5. Historical Strict Protocol

The strict bootstrap, strong push/pull, operation digest, epoch, and error-envelope descriptions formerly in this section described the historical strict implementation. They are not routes or guarantees used by the current TabbyRush binary. Former strict runbooks and scripts have been retired.

---

## 5. Server Admin Announcements API Contract

Server Admin Announcements operate on distinct, unidirectional routes completely separated from strict task-sync endpoints:
- **Protocol Separation**: Announcement endpoints do not require dual-auth or `X-App-Id` headers. They operate under `RouteClassification.BEARER_ONLY`.
- **Canonical Reference**: The complete versioned wire contract and schemas are frozen in [`docs/announcement-contract.md`](announcement-contract.md) and [`integration/announcements/contract-v1.json`](../integration/announcements/contract-v1.json).

### 5.1 Route Summary

| Method & Route Path | Auth | Purpose & Success Code |
|---|---|---|
| `POST /api/v1/admin/apps/tabby-rush/announcements/audience-preview` | Admin | Preview audience and generate immutable digest (200 OK). |
| `POST /api/v1/admin/apps/tabby-rush/announcements` | Admin | Create announcement with `Idempotency-Key` header (202 Accepted). |
| `GET /api/v1/admin/apps/tabby-rush/announcements` | Admin | Query paginated sent announcement history (200 OK). |
| `GET /api/v1/admin/apps/tabby-rush/announcements/by-idempotency/{key}` | Admin | Retrieve sender's announcement by idempotency key (200 OK or 404). |
| `GET /api/v1/admin/apps/tabby-rush/announcements/{id}` | Admin | Query full sent announcement detail (200 OK). |
| `GET /api/v1/apps/tabby-rush/announcements/inbox` | User | Retrieve current user's unexpired announcements (200 OK). |
| `GET /api/v1/apps/tabby-rush/announcements/unread` | User | Query count of unread announcements for current user (200 OK). |
| `GET /api/v1/apps/tabby-rush/announcements/inbox/{id}` | User | Retrieve single announcement detail for member (200 OK). |
| `PUT /api/v1/apps/tabby-rush/announcements/inbox/{id}/read` | User | Mark announcement read; idempotent monotonic timestamp (200 OK). |
| `GET /api/v1/apps/tabby-rush/announcements/stream` | User | Persistent SSE stream for live hints and `ready` handshake (200 OK). |
| `PUT /api/v1/apps/tabby-rush/notification-installs/{id}/binding` | User + Proof | Register device FCM token with numeric generation CAS (200 OK). |
| `POST /api/v1/apps/tabby-rush/notification-installs/{id}/unbind` | User + Proof | Unbind device token with expected generation check (200 OK). |
| `GET /api/v1/apps/tabby-rush/notification-installs/{id}/binding` | User + Proof | Query binding status without disclosing tokens (200 OK). |

### 5.2 Structured Announcement Error Format
Errors on announcement endpoints use the standard nested error envelope:
```json
{
  "error": {
    "code": "audienceChanged",
    "message": "Recipient eligibility changed; preview again.",
    "retryable": false,
    "requestId": "4a7b2c1d-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
    "details": {}
  }
}
```
Key error codes: `invalidRequest` (400), `unauthenticated` (401), `adminRequired` (403), `appAccessRevoked` (403), `installProofRejected` (403), `notFound` (404), `previewNotFound` (404), `audienceChanged` (409), `idempotencyConflict` (409), `staleInstallOperation` (409), `previewExpired` (410), `audienceTooLarge` (413), `emptyAudience` (422), `announcementsDisabled` (503).
