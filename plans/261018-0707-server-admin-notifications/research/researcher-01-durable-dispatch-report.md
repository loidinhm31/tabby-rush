# Research Report: Standalone MongoDB Durable Dispatch & FCM HTTP v1 Delivery

**Author:** DurableDispatchResearch | **Date:** 2026-10-18 | **Target:** Glean Hub Admin Announcements

## 1. Authoritative Source Facts
- **MongoDB Single-Document Atomicity**: Write operations are atomic strictly at the single-document level, including `findOneAndUpdate` and `updateOne`. Multi-document operations are not atomic across documents without replica set transactions ([MongoDB Atomicity Docs](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/)).
- **MongoDB TTL Asynchrony & Standalone Limitation**: TTL deletion is handled by a single-threaded background thread executing once every 60 seconds; documents remain queryable after expiring. The TTL monitor halts completely on standalone `mongod` if `system.local.replset` contains data ([MongoDB TTL Docs](https://www.mongodb.com/docs/manual/core/index-ttl/)).
- **FCM HTTP v1 Authentication**: Requires short-lived OAuth 2.0 access tokens (`https://www.googleapis.com/auth/firebase.messaging`) minted via Google Application Default Credentials (ADC) or Service Account JSON key ([FCM Send v1 API](https://firebase.google.com/docs/cloud-messaging/send/v1-api)).
- **FCM HTTP v1 Errors & Backoff**: Payload limit is 4096 bytes. Error `UNREGISTERED` (404) indicates token invalidation/app uninstall. `QUOTA_EXCEEDED` (429) and `UNAVAILABLE` (503) require honoring `Retry-After` header or using exponential backoff with jitter (initial minimum 60s for 429 quota exhaustion) ([FCM Error Codes](https://firebase.google.com/docs/cloud-messaging/error-codes)).
- **FCM Ambiguous Retries**: HTTP v1 `send` has no idempotency token support. Drops/timeouts can cause duplicate delivery if retried without client-side deduplication.

## 2. Standalone-Safe Storage Architecture & Schema Design
### Concrete Collections & Indexes
```javascript
// 1. announcements: stores canonical content & audience specification
db.announcements.createIndex({ "idempotencyKey": 1 }, { unique: true, sparse: true });
db.announcements.createIndex({ "createdAt": -1 });
db.announcements.createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 }); // Async cleanup

// 2. user_inbox: individual user feed items (materialized)
db.user_inbox.createIndex({ "userId": 1, "createdAt": -1 });
db.user_inbox.createIndex({ "userId": 1, "announcementId": 1 }, { unique: true });
db.user_inbox.createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 });

// 3. device_dispatches: granular per-device delivery jobs preventing BSON bloat
db.device_dispatches.createIndex({ "status": 1, "leaseUntil": 1 });
db.device_dispatches.createIndex({ "announcementId": 1, "deviceId": 1 }, { unique: true });
db.device_dispatches.createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 });
```

### BSON 16MiB Defense & Storage Strategy
- *Fact*: BSON documents hard cap at 16,777,216 bytes. Embedding unbounded recipient arrays or device tokens in `announcements` causes document growth, document moving, and BSON overflow.
- *Proposal*: Canonical content stored once in `announcements`. Recipient tracking materialized into small, normalized `user_inbox` (read state) and `device_dispatches` records.

### Immediate Expiry vs TTL Cleanup
- Because TTL sweep is delayed (60s cycle) and disabled on standalone replset relics, queries MUST NOT rely on TTL for data validity.
- All inbox/announcement queries enforce immediate expiry: `{ expiresAt: { $gt: new Date() } }` (or `createdAt: { $gt: now - 90d }`).

## 3. Durable Lease-Based In-Process Dispatcher
No external broker (Redis/RabbitMQ) required. Single-document CAS in MongoDB provides crash-resilient scheduling.

### Worker CAS Lease Pattern
```javascript
const leaseDurationMs = 60_000;
const job = await db.device_dispatches.findOneAndUpdate(
  {
    status: { $in: ["pending", "retrying"] },
    leaseUntil: { $lt: new Date() },
    attempts: { $lt: 5 }
  },
  {
    $set: {
      status: "processing",
      leasedBy: workerInstanceId,
      leaseUntil: new Date(Date.now() + leaseDurationMs)
    },
    $inc: { attempts: 1 }
  },
  { returnDocument: "after" }
);
```

### Crash Recovery Matrix
| Failure Point | State at Crash | Recovery Mechanism |
|---|---|---|
| Crash during announcement intake | Pending CAS / no doc committed | Client retries with same `idempotencyKey`; fresh insert proceeds |
| Crash after announcement insert, before inbox materialization | `announcement.status = "pending_materialization"` | Background lease scanner claims announcement and executes materialization |
| Crash mid-batch dispatch | Dispatches marked `processing` with active lease | Lease expires after 60s; recovered by next poll cycle |
| Crash after FCM accept, before Mongo status update | FCM sent; Mongo shows `processing` | On retry, FCM re-sent. Ambiguous retry mitigated by Android client deduping on `announcementId` |

### Idempotency Key Handling
- Admin submit endpoint requires `Idempotency-Key` header (UUIDv4).
- On duplicate key conflict (`E11000`):
  1. Retrieve existing document by `idempotencyKey`.
  2. If SHA-256 payload hash matches: return `200 OK` or `202 Accepted` with existing announcement data.
  3. If payload hash differs: reject with `409 Conflict`.

## 4. FCM HTTP v1 Server Delivery & Token Lifecycle
### Credential Custody & OAuth2 Token Management
- Store Service Account JSON path in `GOOGLE_APPLICATION_CREDENTIALS` (or injected JSON string in `GCP_SERVICE_ACCOUNT_KEY`).
- Server process caches OAuth2 Bearer token in memory, refreshing proactively 5 minutes before its 3600s expiration. Never serialize access tokens to disk or database.

### FCM v1 Error Classification & Dispatch Actions
| HTTP Status | FCM v1 Error Status / Code | Action | Retry Policy |
|---|---|---|---|
| `404` | `UNREGISTERED` | Deactivate/fence device registration; mark token dead | Do NOT retry |
| `400` | `INVALID_ARGUMENT` (token) | Mark device token invalid | Do NOT retry |
| `429` | `QUOTA_EXCEEDED` | Defer dispatch queue | Honor `Retry-After`; fallback min 60s exponential backoff |
| `503` / `500`| `UNAVAILABLE` / `INTERNAL` | Transient failure | Honor `Retry-After`; exponential backoff (1s, 2s, 4s, 8s...) + jitter |
| `401` | `UNAUTHENTICATED` | Evict cached OAuth token, mint fresh token | Retry immediately once |
| `403` | `SENDER_ID_MISMATCH` | Fatal project config error; log critical alert | Do NOT retry |

### Payload & Content Boundaries
- Generic push notification only: `notification: { title, body }` with generic content, plus `data: { announcementId, type: "announcement" }`. No PII or private payloads over FCM.
- **Title limit**: <= 256 UTF-8 characters.
- **Body limit**: <= 4,096 UTF-8 characters (aligns with FCM byte limit).
- **Target Audience limits**:
  - `selected`: max 500 user IDs per admin request.
  - `all` / `admin`: materialized asynchronously in chunks of 250 users.
- **Retention**: 90 days (`expiresAt = createdAt + 90 days`).

## 5. Unresolved Prerequisites & Dependencies
- Server environment variable `GOOGLE_APPLICATION_CREDENTIALS` or `FCM_SERVICE_ACCOUNT_JSON` must be provisioned in deployment before enabling HTTP v1 dispatch.
- Android client must implement idempotent notification presentation keyed by `announcementId` to absorb ambiguous retries without duplicate alerts.
