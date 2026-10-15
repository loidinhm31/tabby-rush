# Doggo Hub Schema Integration

Date: 2026-10-14  
Phase: Phase 03 Strict Server & Legacy Isolation (Implementation Complete; Runtime Acceptance Pending)  
Related Docs: [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md) · [API Contract](./api-contract.md) · [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Evidence Summary](./evidence/hub-compatibility-summary.md)

---
## Overview
Doggo integrates with Glean Hub using the opt-in `strict-v1` synchronization protocol. This integration establishes complete namespace and logic isolation from legacy sync applications while reusing Hub's dual-authentication and app registry infrastructure.

## Schema Specification
Strict apps register with an immutable, strict schema containing exclusively the `todos` table:

```json
{
  "appId": "doggo",
  "syncMode": "strict-v1",
  "schema": {
    "todos": {
      "columns": [
        { "name": "id", "type": "string", "nullable": false },
        { "name": "title", "type": "string", "nullable": false },
        { "name": "completed", "type": "boolean", "nullable": false },
        { "name": "createdAt", "type": "integer", "nullable": false }
      ],
      "primaryKey": "id"
    }
  }
}
```

## Registration & Admission Lifecycle
1. **Admin Registration**:
   - Registered via admin-only endpoint: `POST /api/v1/strict-apps`.
   - Validates `appId` charset: `[a-z0-9-]{1,40}`.
   - Validates exact `todos` table structure (no extra tables, no sharing).
2. **Server-Generated Epoch**:
   - Server creates a lowercase UUIDv4 epoch string (e.g. `550e8400-e29b-41d4-a716-446655440000`).
3. **Storage Provisioning**:
   - Targets designated MongoDB collection: `app_<appId>` (e.g. `app_doggo`).
   - Sets up compound scan index `{ userId: 1, tableName: 1, rowId: 1 }`.
   - Verifies collection is uncapped, has no custom validators, no TTL indexes, and simple/absent collation.
4. **Registry Publication Last**:
   - Server registers `AppSchema` in `_schemas` collection only after storage and indexes pass admission.
5. **Mandatory Read-Back**:
   - Operator and provisioning scripts must verify `GET /api/v1/apps/doggo` echoing `syncMode: "strict-v1"` and matching `strictEpoch`.

---

## Versioned Schema Upgrades & Fingerprints (Gate G1 Frozen Specification)

*Note: The contracts, endpoints, and invariants in this section represent the frozen architecture and wire specification designed in Phase 01 (Gate G1 Approved). Implementation of server-side upgrade endpoints and migration fencing is scheduled for Phase 03; client legacy-receipt reconciliation is scheduled for Phase 06. Glean Hub currently operates strictly on `strict-v1`.*

Glean Hub's planned migration design supports controlled in-place schema upgrades from `strict-v1` to `strict-v2` under an app-scoped administrative fence:

### Canonical Schemas & Fingerprints
1. **`strict-v1` (Current Active)**:
   - File: `integration/glean-hub/doggo-schema-v1.json`
   - Fingerprint: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
   - 4 columns: `id`, `title`, `completed`, `createdAt`.
2. **`strict-v2` (Target Version)**:
   - File: `integration/glean-hub/doggo-schema-v2.json`
   - Fingerprint: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
   - 16 columns: adds `deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`.

### Upgrade Invariants
- **Planned Management Endpoints (Scheduled for Phase 03 Implementation)**:
  - `GET /api/v1/apps/:app_id/strict-contract`: Echoes `syncMode`, active `strictEpoch`, and canonical `schemaFingerprint`.
  - `POST /api/v1/apps/:app_id/strict-upgrades`: Initiates atomic in-place migration. Payload: `{ migrationId, expectedVersion, expectedSchemaFingerprint, expectedEpoch, targetVersion, targetSchemaFingerprint }`. Requires global admin role plus app-scoped authorization.
  - `GET /api/v1/apps/:app_id/strict-upgrades/:migrationId`: Queries transition state (`ready-v1` -> `fencing` -> `transforming` -> `validating` -> `publishing` -> `ready-v2`, or `operatorRequired`).
- **Planned Legacy-Receipt Reconciliation Endpoint (Scheduled for Phase 03/06 Implementation)**:
  - `POST /api/v1/sync/:app_id/strong/legacy-receipts`: Ordinary app-scoped dual-auth endpoint with bounded batch (<= 100). Accepts original v1 preimage + digest. Returns per-operation verification status (`provenAccepted`, `provenNotAccepted`, `unknownSuperseded`, `integrityFailure`). Confirms receipt durability without applying legacy mutations.
- **Fingerprint Verification**: `POST /api/v1/apps/:app_id/strict-upgrades` validates that the app's current schema fingerprint matches `expectedSchemaFingerprint` and the target schema matches `targetSchemaFingerprint`.
- **Fencing**: Acquires exclusive migration lease in `_coordination` collection, drains in-flight reader/writer requests, transforms MongoDB documents in `app_<appId>` in-place without changing collection name or epoch, verifies collection documents against target schema, and atomically updates `_schemas`.
- **Standard Error Mapping**:
  - `400 invalidRequest` (malformed schema, invalid parameters)
  - `401 unauthorized` (missing or invalid authentication credentials)
  - `403 forbidden` (admin role present but missing explicit app-scoped authorization)
  - `404 appNotFound` (unknown application ID)
  - `409 epochMismatch` / `preconditionFailed` (stale schema fingerprint or active epoch conflict)
  - `426 protocolUnsupported` (client protocol version mismatch)
  - `503 migrationInProgress` / `indeterminateStorage` (active fence drain or unresolvable storage state)
