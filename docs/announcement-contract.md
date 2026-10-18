# Server Admin Announcements Contract Reference (v1)

Last Updated: 2026-10-18  
Contract Version: `v1`  
Specification Authority: [Frozen Contract Spec](../plans/261018-0707-server-admin-notifications/reports/03-contract-specification.md)  
Machine-Readable Contract: [`integration/announcements/contract-v1.json`](../integration/announcements/contract-v1.json)  
Golden Test Vectors: [`integration/announcements/vectors-v1.json`](../integration/announcements/vectors-v1.json)

---

## 1. Identity & Authority

| Parameter | Value | Description |
|---|---|---|
| Application ID | `tabby-rush` | Canonical identifier across backend and clients. |
| Android Package | `dev.tabbyrush.android` | Android application ID for FCM targeting and intent routing. |
| Windows AUMID | `dev.tabbyrush.desktop` | Application User Model ID for Windows toast notifications. |
| Cursor HMAC Label | `tabbyrush-announcement-cursor-v1` | Key derivation label for pagination cursor HMAC-SHA256. |
| Proof Header | `X-Tabby-Install-Proof` | Base64URL-encoded 32 random bytes (43 characters). |
| Idempotency Header | `Idempotency-Key` | Client-generated UUIDv4 passed on announcement creation. |

### Deterministic UUIDv5 Namespaces
- **Inbox Record**: `b29b36f3-1c6d-5a55-94c0-045e243c2e4d`  
  Name format: `["tabby-rush","<messageId>","<userId>"]` (canonical JSON array)
- **Dispatch Record**: `0e9bf528-13dc-58b4-824e-fdd139f77d5c`  
  Name format: `["<messageId>","<userId>","<kind>","<installId>","<bindingGeneration>"]` (nulls represented as JSON `null`)
- **Target Batch**: `f37cd6fb-f90b-5e34-b065-c2be584e0c61`  
  Name format: `["<messageId>","<userId>",<batchIndex>]`

---

## 2. HTTP Routes

All receiver routes require `Authorization: Bearer <jwt>`. Static segments are registered before parameter segments.

### Admin Routes (`/api/v1/admin/apps/tabby-rush/announcements`)
| Method | Path | Auth | Request Body | Max Body | Success | Response |
|---|---|---|---|---|---|---|
| `POST` | `/audience-preview` | Current DB admin | `PreviewRequest` | 32 KiB | 200 | `AudiencePreview` |
| `POST` | `/` | Current DB admin | `CreateAnnouncementRequest` | 64 KiB | 202 | `Acceptance` |
| `GET` | `/` | Current DB admin | Query: `limit`, `cursor` | None | 200 | `SentPage` |
| `GET` | `/by-idempotency/{key}` | Current DB admin (own scope) | None | None | 200 | `Acceptance` |
| `GET` | `/{id}` | Current DB admin | None | None | 200 | `SentDetail` |

### Recipient Routes (`/api/v1/apps/tabby-rush/announcements`)
| Method | Path | Auth | Request Body | Max Body | Success | Response |
|---|---|---|---|---|---|---|
| `GET` | `/inbox` | Current user with grant | Query: `limit`, `cursor` | None | 200 | `InboxPage` |
| `GET` | `/unread` | Current user with grant | None | None | 200 | `UnreadResponse` |
| `GET` | `/inbox/{id}` | Current user with grant & member | None | None | 200 | `InboxItem` |
| `PUT` | `/inbox/{id}/read` | Current user with grant & member | `{}` | 1 KiB | 200 | `ReadResult` |
| `GET` | `/stream` | Current user with grant | Header: `Last-Event-ID` (advisory) | None | 200 | `text/event-stream` |

### Device Install Routes (`/api/v1/apps/tabby-rush/notification-installs`)
| Method | Path | Auth | Request Body | Max Body | Success | Response |
|---|---|---|---|---|---|---|
| `PUT` | `/{installId}/binding` | Current user + proof | `BindRequest` | 8 KiB | 200 | `BindingResult` |
| `POST` | `/{installId}/unbind` | Current owner + proof | `UnbindRequest` | 1 KiB | 200 | `BindingResult` |
| `GET` | `/{installId}/binding` | Authenticated + proof | None | None | 200 | `BindingStatus` |

---

## 3. Limits, Bounds & Content Normalization

- **Intake Bound**: Maximum aggregate request payload size is 65,536 bytes (64 KiB).
- **Title**:
  - Trimmed once of leading/trailing whitespace.
  - Length: 1 to 200 Unicode scalars.
  - Maximum UTF-8 byte length: 800 bytes.
  - Reject NUL (`\0`) and invalid UTF-8.
- **Body**:
  - Normalize CRLF (`\r\n`) and CR (`\r`) to LF (`\n`).
  - Outer whitespace trimmed once.
  - Length: 1 to 4,000 Unicode scalars.
  - Maximum UTF-8 byte length: 16,384 bytes.
  - Reject NUL (`\0`) and invalid UTF-8.
- **Audience**:
  - `selected`: 1 to 500 unique valid Hub user IDs.
  - Server resolves up to 501 recipients to detect overflow across all audience modes (`selected`, `all`, `admins`). Over 500 fails closed with 413 `audienceTooLarge`.
  - Zero eligible recipients fails with 422 `emptyAudience`.
- **Retention**:
  - Accepted parent announcements and derived records expire after 90 days (`expiresAt = createdAt + 90 * 24h`).
  - All read, query, render, and dispatch paths strictly reject records where `expiresAt <= serverNow`.

---

## 4. Digest Encodings

All digests use SHA-256 over canonical, whitespace-free UTF-8 JSON with fixed key ordering.

1. **Audience Digest**:
   `{"appId":"tabby-rush","audience":{"mode":"..."},"recipientIds":["..."]}`  
   (For `all` and `admins`, `userIds` is omitted; for `selected`, `userIds` is sorted and deduplicated).  
   Prefixed with `sha256:` lowercase hex.
2. **Request Digest**:
   `{"previewId":"...","audienceDigest":"sha256:...","title":"...","body":"..."}`  
   Prefixed with `sha256:` lowercase hex.
3. **Binding Operation Digest**:
   `{"appId":...,"installId":...,"operationKind":...,"operationGeneration":...,"ownerId":...,"platform":...,"tokenHash":...,"appVersion":...,"expectedBindingGeneration":...}`  
   Absent fields are serialized as `null`. Hexadecimal SHA-256 string (raw 64-char lowercase hex).

---

## 5. Idempotent Intake & Fencing Semantics

1. **Intake Flow**:
   - Admin composer generates UUIDv4 `Idempotency-Key` before submission.
   - Server checks `(appId, senderId, idempotencyKey)` before preview validation.
   - Matching key with identical request digest returns `202 Accepted` with `replayed: true`.
   - Matching key with conflicting request digest returns `409 Conflict` (`idempotencyConflict`).
2. **Generation Fencing (Android Installs)**:
   - Install generations are represented as decimal strings on the wire (e.g. `"17"`), but compared strictly numerically (`10 > 9`).
   - CAS on stored generation: strictly newer operation generation required. Lower generation rejected with 409 `staleInstallOperation`.
   - Same generation with matching operation digest returns idempotent 200 replay.
   - Same generation with differing operation digest returns 409 `installOperationConflict`.
   - Unbind CAS enforces `expectedBindingGeneration == stored generation`.

---

## 6. Real-Time Streaming & Push Payload

### SSE Stream Protocol (`/stream`)
- **Ready Event**:
  ```
  event: ready
  id: <streamEpoch>:<hintSequence>
  data: {"protocol":1,"appId":"tabby-rush","streamEpoch":"UUID","baselineAcceptanceOrdinal":"41","baselineHintSequence":"58","serverNow":"RFC3339","replaySupported":false}
  ```
- **Announcement Event**:
  ```
  event: announcement
  id: <streamEpoch>:<hintSequence>
  data: {"appId":"tabby-rush","messageId":"UUID","acceptanceEpoch":"UUID","acceptanceOrdinal":"42","acceptedAt":"RFC3339","expiresAt":"RFC3339","emittedAt":"RFC3339","liveEligible":true}
  ```
- Hints are minimal and never carry announcement title/body or user identity.
- Client desktop OS alert requires: `acceptanceEpoch == streamEpoch`, `acceptanceOrdinal > baselineAcceptanceOrdinal`, `liveEligible == true` (emitted within 120s of server acceptance), and freshly authorized detail fetch.

### FCM HTTP v1 Wire Payload
- Sent by server to Google FCM endpoint:
  ```json
  {
    "message": {
      "token": "<fcm_registration_token>",
      "notification": {
        "title": "TabbyRush",
        "body": "You have a new announcement."
      },
      "data": {
        "type": "admin_announcement",
        "appId": "tabby-rush",
        "messageId": "<UUIDv4>"
      },
      "android": {
        "priority": "high",
        "ttl": "<remainingSeconds>s",
        "notification": {
          "channel_id": "tabbyrush.remote.announcements",
          "tag": "remote:<messageId>",
          "icon": "remote_notification_icon"
        }
      }
    }
  }
  ```

---

## 7. Error Format & Error Codes

All API errors return a standard JSON envelope:
```json
{
  "error": {
    "code": "audienceChanged",
    "message": "Recipient eligibility changed; preview again.",
    "retryable": false,
    "requestId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
    "details": {}
  }
}
```

| HTTP Status | Error Codes | Retryable |
|---|---|---|
| `400 Bad Request` | `invalidRequest`, `invalidCursor` | `false` |
| `401 Unauthorized` | `unauthenticated`, `tokenExpired` | `false` |
| `403 Forbidden` | `adminRequired`, `appAccessRevoked`, `installProofRejected` | `false` |
| `404 Not Found` | `notFound`, `previewNotFound` | `false` |
| `409 Conflict` | `audienceChanged`, `idempotencyConflict`, `staleInstallOperation`, `installOperationConflict`, `bindingChanged`, `tokenAlreadyBound` | `false` |
| `410 Gone` | `previewExpired`, `idempotencyExpired` | `false` |
| `413 Payload Too Large` | `requestTooLarge`, `audienceTooLarge` | `false` |
| `422 Unprocessable` | `emptyAudience`, `invalidContent` | `false` |
| `429 Too Many Requests`| `rateLimited` | `true` |
| `503 Service Unavailable` | `announcementsDisabled`, `durableStorageUnavailable`, `providerNotConfigured` | `true` |

---

## 8. Cross-Repository Implementations

- **Kotlin Contracts**: `dev.tabbyrush.shared.contract.RemoteInboxContractsKt`
  - Interfaces: `RemoteInboxController`, `RemoteNotificationAdapter`
  - State & Actions: `RemoteInboxState`, `RemoteInboxAction`, `RemoteInboxAvailability`
  - DTOs: `RemoteInboxItem`, `RemoteInboxPage`, `RemoteUnreadResponse`, `RemoteReadResult`, `RemoteBindingResult`, `RemoteBindingStatus`, `RemoteErrorEnvelope`
  - Tests: `dev.tabbyrush.shared.contract.RemoteContractVectorTest`
- **Rust Contracts**: `glean_hub_notification::announcement_contract`
  - Trait: `AnnouncementPushTransport`
  - DTOs: `AudienceSpec`, `PreviewRequest`, `AudiencePreview`, `CreateAnnouncementRequest`, `Acceptance`, `SentPage`, `SentDetail`, `InboxItem`, `InboxPage`, `UnreadResponse`, `ReadResult`, `BindRequest`, `UnbindRequest`, `BindingResult`, `BindingStatus`, `SseReadyEvent`, `SseAnnouncementHint`, `PushTarget`, `AnnouncementPushHint`, `PushOutcome`
