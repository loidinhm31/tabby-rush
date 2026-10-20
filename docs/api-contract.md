# TabbyRush API & Standard Sync Contract

Date: 2026-10-20  
Status: Standard sync is the current TabbyRush client protocol. Strict-sync contracts below are historical and do not describe the current binary.  
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

Exact server response fields are governed by the deployed Hub contract; do not infer them from this route summary.

---

## 2. Standard Sync Routes

All paths below are relative to `/api/v1`. The `{app_id}` path segment is the Hub app identifier. Sync requests use the dual-auth headers above.

### 2.1 Push — `POST /sync/{app_id}/push`

`PushRequestDto` contains `records` and an optional `clientTimestamp`. Each record has `tableName` (defaults to `todos`), `rowId`, raw JSON `data`, `version`, `deleted` (defaults to `false`), and `assumedServerVersion`.

The response contains `synced`, `conflicts`, optional `failures` and `syncedRecords`, `serverTimestamp`, and informational `schemaVersion`. Conflict entries identify the table and row plus client and server versions. `syncedRecords` names exact accepted `(tableName, rowId, version)` tuples.

Only an explicit accepted tuple is confirmation that a particular submitted version was accepted. A later pull that happens to match local content is desired-state convergence, not an operation receipt.

### 2.2 Pull — `POST /sync/{app_id}/pull`

`PullRequestDto` contains an optional `{updatedAt, id}` checkpoint, `batchSize` (defaults to 100), and `tables` (defaults to `["todos"]`). The response contains records, the next checkpoint, `serverTimestamp`, `hasMore`, and informational `schemaVersion`. Each record carries `tableName`, `rowId`, JSON `data`, `version`, `syncedAt`, and `deleted`.

The client starts a fresh full scan from a null checkpoint for every sync round/reconnect because it does not rely on a durable server-retention horizon. An incomplete scan does not establish that an absent row was deleted.

### 2.3 Delta — `POST /sync/{app_id}/delta`

The request has optional `push` and `pull` bodies; the response has optional `push` and `pull` results. Delta performs push followed by pull; it is not a transaction and does not make the two phases atomic.

### 2.4 Version and Schema Semantics

- `schemaVersion` is positive exact-integer Hub metadata (bounded by JavaScript's safe-integer range), used for schema concurrency; it is not a native feature gate or a request to generate UI/database fields.
- Admin schema updates use `PUT /api/v1/apps/{app_id}/schema` with `schema` and `expectedSchemaVersion`. A changed schema commits only against the exact current version; a stale edit conflicts. An effective no-op does not increment the version.
- TabbyRush preserves unknown JSON fields when applying intent-selected local changes. Hub schema additions do not generate Kotlin models, Room columns, or native UI automatically.
- For the admin editor, a stale changed save returns 409 and retains the draft; the latest server schema is read-only until the user explicitly discards/reloads. A stale request whose effective schema is already current may return the authoritative current state.

---

## 3. Standard Sync Guarantees & Limits

Standard sync is a desired-state synchronization protocol, not the retired strict operation-receipt protocol. It does not provide strict exact-revision compare-and-swap receipts, server-enforced hierarchy, permanent tombstones, or transactional group writes. Do not rely on convergence, a matching version, or matching full JSON as proof that an individual operation was accepted.

The native client retains raw unknown JSON fields and merges only fields named by a valid local intent mask. Ambiguous legacy intent requires user review; it must never be reinterpreted as “reapply every known field.” Users can discard, selectively reapply explicit intent, or copy the latest server draft as a fresh root. Copy-latest reads the current projection and creates a new root record.

The Room v4 migration losslessly archives legacy outbox values, including floating-point bit patterns and BLOB bytes, before normal reconciliation. Archival does not silently confirm or reapply those legacy operations. Every scan round begins from null; if the process restarts, stale scan-generation staging is discarded and a fresh scan begins.

The client checks serialized UTF-8 request bytes before freezing a push batch against its 256 KiB limit. If a single known record exceeds the limit, it is marked actionable/conflicted and nothing is sent for that record; the client does not report it as an uncertain network outcome. Fitting records may be sent while other pending records remain unsent.

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
