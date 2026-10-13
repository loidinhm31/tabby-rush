# Doggo API & Strict Sync Protocol Contract

Date: 2026-10-14  
Phase: Phase 02 Frozen Foundation & Contracts  
Plan: `plans/261013-1540-doggo-glean-hub-reuse`  
Related Docs: [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Toolchain Versions](./toolchain-versions.md)

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

---

## 4. Canonical SHA-256 Digest Encoding

To prevent ambiguity, digest encoding uses length-framed big-endian fields:
1. `u32_be("strict-v1".length)` + UTF-8 `"strict-v1"`
2. `u32_be(appId.length)` + UTF-8 `appId`
3. `u32_be(userId.length)` + UTF-8 `userId`
4. `u32_be(epoch.length)` + UTF-8 `epoch`
5. `u32_be(tableName.length)` + UTF-8 `tableName`
6. `u32_be(rowId.length)` + UTF-8 `rowId`
7. `u32_be(opId.length)` + UTF-8 `opId`
8. `u32_be(kind.length)` + UTF-8 `kind` (`"create"`, `"update"`, `"delete"`)
9. `u64_be(expectedRevision)`
10. Payload:
    - If `create` or `update`:
      - `u32_be(id.length)` + UTF-8 `id`
      - `u32_be(title.length)` + UTF-8 `title`
      - `u8(completed ? 1 : 0)`
      - `u64_be(createdAt)`
    - If `delete`: zero additional payload bytes.
