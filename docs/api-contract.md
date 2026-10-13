# Doggo API & Strict Sync Protocol Contract

Date: 2026-10-14  
Phase: Phase 03 Strict Server & Legacy Isolation (Implementation Complete; Runtime Acceptance Pending)  
Plan: `plans/261013-1540-doggo-glean-hub-reuse`  
Related Docs: [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Toolchain Versions](./toolchain-versions.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md)

---

## 1. Scope, Origin & Authentication Overview

Doggo reuses the existing Glean Hub authentication service, app schema registry, and dual-layer authorization (JWT Bearer token + App API Key). Doggo exclusively uses the opt-in strict synchronization protocol (`syncMode: "strict-v1"`).

### 1.1 Endpoint & Origin Policy
- **Base Origin**: Canonical origin only (scheme, host, optional port). Zero path, query, fragment, or user credentials.
- **API Prefix**: `/api/v1` for sync operations; root path for Hub auth (`/auth/*`, `/apps/*`).
- **Release Mode**: HTTPS required. No HTTP, cleartext, or self-signed bypass permitted.
- **Debug Mode**: Permits HTTPS plus loopback HTTP targets:
  - `http://localhost:3000`
  - `http://127.0.0.1:3000`
  - `http://10.0.2.2:3000` (Android emulator loopback)
- **Timeouts**: Connect 10s, Request 20s. Zero automatic mutation retries at transport level.

### 1.2 Account Identity & Vault Protection
- **AccountKey**: Canonical hash:
  `SHA-256(u32_be(origin.length) + origin_utf8 + u32_be(userId.length) + userId_utf8)`
- **Vault Integration**:
  - Android: Android Keystore with AES-256-GCM encrypted bundle.
  - Linux Desktop: Secret Service D-Bus API.
  - Windows: User-scoped DPAPI with restricted file ACLs.
- Zero plaintext credential fallback; memory-only lifetime for unpersisted successor tokens.

---

## 2. Hub Authentication Routes

All request/response payloads use JSON camelCase.

| Method | Path | Request Body | Response (Success 200) | Notes |
|---|---|---|---|---|
| `POST` | `/auth/register` | `{username, email, password}` | `{userId, accessToken, refreshToken}` | Password never logged; email trimmed/lowercased |
| `POST` | `/auth/login` | `{email, password}` | `{userId, accessToken, refreshToken, apps, isAdmin}` | App access verified via `apps` array |
| `POST` | `/auth/refresh` | `{refreshToken}` | `{accessToken, refreshToken}` | Single-flight execution; atomic bundle replacement |
| `GET` | `/auth/me` | Bearer auth header | `{userId, username, email, apps, isAdmin, ...}` | Server identity check |
| `POST` | `/apps/access-requests` | `{appId: "doggo", message?}` | `AccessRequestInfo` | Explicit access request if `doggo` not granted |
| `GET` | `/apps/access-requests/me` | Bearer auth header | `AccessRequestInfo[]` | Query status: pending, approved, rejected |

### 2.1 Admin Strict App Registration & Schema Management

Strict apps require administrative registration and immutable schema binding:

| Method | Path | Request Body | Response (Success 200/201) | Notes |
|---|---|---|---|---|
| `POST` | `/api/v1/strict-apps` | `{appId, syncMode: "strict-v1", schema}` | `{appId, apiKey, syncMode, strictEpoch}` | Admin-only; generates canonical UUID epoch, provisions storage, publishes last |
| `GET` | `/api/v1/apps/:app_id` | *(none)* | `{appId, schema, syncMode, strictEpoch, sharing?}` | Mandatory read-back verification echoing mode and epoch |
| `PUT` | `/api/v1/apps/:app_id/schema` | `{schema}` | *400 Bad Request* | Schema mutation rejected (`syncMode $exists: false` predicate rejects strict apps) |

---

## 3. Strict Sync Endpoints (`strict-v1`)

Sync routes require dual authentication headers:
- `Authorization: Bearer <accessToken>`
- `X-API-Key: <appApiKey>`
- `X-App-Id: doggo`

### 3.1 Bootstrap: `POST /api/v1/sync/:app_id/strong/bootstrap`
- **Request**: `{}`
- **Response**:
  ```json
  {
    "protocol": "strict-v1",
    "appId": "doggo",
    "epoch": "550e8400-e29b-41d4-a716-446655440000",
    "tables": ["todos"]
  }
  ```
- **Epoch Invariant**: If client has established local data with a different epoch, sync pauses in `ResetRequired` state. Never auto-rebase.

### 3.2 Push: `POST /api/v1/sync/:app_id/strong/push`
- **Request**:
  ```json
  {
    "protocol": "strict-v1",
    "epoch": "550e8400-e29b-41d4-a716-446655440000",
    "operations": [
      {
        "opId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
        "epoch": "550e8400-e29b-41d4-a716-446655440000",
        "tableName": "todos",
        "rowId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        "kind": "create",
        "expectedRevision": 0,
        "data": {
          "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
          "title": "Clean workspace",
          "completed": false,
          "createdAt": 1791840000000
        }
      }
    ]
  }
  ```
- **Response**:
  ```json
  {
    "protocol": "strict-v1",
    "epoch": "550e8400-e29b-41d4-a716-446655440000",
    "results": [
      {
        "opId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
        "rowId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        "digest": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "status": "accepted",
        "acceptedRevision": 1
      }
    ]
  }
  ```
- **Operation Status Codes**:
  - `accepted`: Mutation conditionally applied, server revision bumped by 1.
  - `replayed`: Idempotent match of identical opId + digest with durable confirmation.
  - `revisionConflict`: Concurrent modification detected (`expectedRevision` mismatch).
  - `missingRecord`: Attempted update/delete on non-existent record.
  - `deletedRecord`: Attempted update on existing tombstone.
  - `opIdReuse`: Reused opId with differing payload digest (protocol violation).
  - `invalidRecord`: Validation failure (schema, Unicode, scalar bounds).
  - `retryableFailure`: Storage or confirmation timeout; client treats as uncertain outcome.

### 3.3 Pull: `POST /api/v1/sync/:app_id/strong/pull`
- **Request**:
  ```json
  {
    "protocol": "strict-v1",
    "epoch": "550e8400-e29b-41d4-a716-446655440000",
    "tableName": "todos",
    "cursor": null,
    "batchSize": 100
  }
  ```
- **Response**:
  ```json
  {
    "protocol": "strict-v1",
    "epoch": "550e8400-e29b-41d4-a716-446655440000",
    "records": [
      {
        "tableName": "todos",
        "rowId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        "revision": 1,
        "deleted": false,
        "data": { ... }
      }
    ],
    "cursor": "opaque-cursor-token",
    "hasMore": false
  }
  ```

### 3.4 Strict Sync Error Envelopes

Errors emitted by strict sync route handlers and middleware follow a consistent structured JSON envelope:

```json
{
  "error": "Descriptive human-readable error message",
  "code": "machineErrorCode"
}
```

| HTTP Status | Machine Code | Cause |
|---|---|---|
| `400 Bad Request` | `invalidRequest` | Schema validation failure, malformed payload, or `X-App-Id` header mismatched with route path. |
| `401 Unauthorized` | `unauthorized` | Missing or invalid user JWT token or app API key. |
| `404 Not Found` | `appNotFound` | Specified `app_id` is not registered on the Hub. |
| `409 Conflict` | `epochMismatch` | Request epoch does not match the active server epoch in `_schemas`. |
| `426 Upgrade Required` | `protocolUnsupported` | Strict app accessed via legacy sync routes, or unsupported protocol requested. |
| `500 Internal Server Error` | `internalError` | Database error or unexpected server-side processing exception. |
| `503 Service Unavailable` | `notAdmitted` | App storage, topology, or index structure failed admission validation. |

*Note on Framework Errors:* Axum framework JSON extraction errors (e.g. HTTP 415 `Unsupported Media Type` or HTTP 422 `Unprocessable Entity`) and raw transport dropouts are generated by framework layers prior to strict route dispatch and do not use the `StrictSyncError` envelope.

---

## 4. Canonical SHA-256 Digest Encoding

To prevent cross-platform ambiguity between Kotlin Multiplatform and backend Rust, digest encoding uses length-framed big-endian fields. **Crucially, all length prefixes represent the byte count of the UTF-8 encoded byte array (`u32_be(utf8_bytes.len)`), never Unicode character or scalar counts:**

1. `u32_be(utf8("strict-v1").len)` + UTF-8 `"strict-v1"`
2. `u32_be(utf8(appId).len)` + UTF-8 `appId`
3. `u32_be(utf8(userId).len)` + UTF-8 `userId`
4. `u32_be(utf8(epoch).len)` + UTF-8 `epoch`
5. `u32_be(utf8(tableName).len)` + UTF-8 `tableName`
6. `u32_be(utf8(rowId).len)` + UTF-8 `rowId`
7. `u32_be(utf8(opId).len)` + UTF-8 `opId`
8. `u32_be(utf8(kind).len)` + UTF-8 `kind` (`"create"`, `"update"`, `"delete"`)
9. `u64_be(expectedRevision)`
10. Payload:
    - If `create` or `update`:
      - `u32_be(utf8(id).len)` + UTF-8 `id`
      - `u32_be(utf8(title).len)` + UTF-8 `title`
      - `u8(completed ? 1 : 0)`
      - `u64_be(createdAt)`
    - If `delete`: zero additional payload bytes.
