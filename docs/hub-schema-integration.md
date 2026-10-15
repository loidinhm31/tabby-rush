# Doggo Hub Schema Integration

Date: 2026-10-15  
Phase: Phase 03 Hub v2 Admission, Fencing, and Migration (Backend Contracts & Logic Delivered; Gate G3 Live Multi-Instance Staging Pending)  
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

## Versioned Schema Upgrades, Admission Fencing & Migration (Phase 03 Delivered)

*Status: Phase 03 backend contracts, admission guards, and migration logic are delivered in `glean-hub-sync` with passing unit/contract checks. Production admission remains on hold: Gate G3 live multi-instance Mongo replica-set acceptance is unexecuted pending staging testbed, coordination-document compatibility for pre-existing documents lacking `permitCount` requires live backfill verification, and exact cumulative accounting across mid-batch crash interruption requires formal revalidation.*

Glean Hub provides controlled in-place schema upgrades from `strict-v1` to `strict-v2` under an app-scoped administrative fence and multi-node coordination gate:

### Canonical Schemas & Fingerprints
1. **`strict-v1` (Active Baseline)**:
   - File: `integration/glean-hub/doggo-schema-v1.json`
   - Fingerprint: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
   - 4 columns: `id`, `title`, `completed`, `createdAt`.
2. **`strict-v2` (Target Version)**:
   - File: `integration/glean-hub/doggo-schema-v2.json`
   - Fingerprint: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
   - 16 columns: adds `deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`.

### Delivered Management & Upgrade Endpoints
All upgrade management endpoints require app-scoped admin authorization via `app_scoped_admin_middleware`:

1. **`GET /api/v1/apps/:app_id/strict-contract`**:
   - Queries active contract metadata for an application.
   - Returns `StrictContractInfo`: `{ appId, syncMode, epoch, schemaFingerprint, tables, schema }`.
   - Echoes `syncMode: "strict-v1"` or `"strict-v2"`, active `epoch` UUID, canonical SHA-256 schema fingerprint, and full table column definitions.

2. **`POST /api/v1/apps/:app_id/strict-upgrades`**:
   - Initiates atomic in-place migration or preflight dry run.
   - Request payload (`StrictUpgradeRequest`):
     ```json
     {
       "migrationId": "550e8400-e29b-41d4-a716-446655440000",
       "expectedVersion": "strict-v1",
       "expectedSchemaFingerprint": "3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec",
       "expectedEpoch": "550e8400-e29b-41d4-a716-446655440001",
       "targetVersion": "strict-v2",
       "targetSchemaFingerprint": "5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4",
       "dryRun": false
     }
     ```
   - Preflight validation: Enforces fingerprint equality, active epoch match, and verifies storage collection invariants. Resumption from active `Transforming`, `Validating`, or `Publishing` states passes prior recorded counts, though exact cumulative accounting across arbitrary mid-batch crash interruptions remains an open correctness item awaiting formal revalidation.
   - Fencing: Claims exclusive migration fence in `_coordination` (`fenced: true`, `fenceOwner: migrationId`, incremented `fenceGeneration`). Drains in-flight reader/writer permits before data transform begins.
   - In-place transform: Transforms documents in `app_<appId>` in chunks of `BATCH_SIZE = 500` sorted by `rowId`. Live documents (`deleted: false`) lacking `deadlineDate` receive default null/zero values for the 12 v2 fields; deleted tombstones remain compact with `data: {}`.
   - CAS Publication: Validates transformed records, updates `_schemas` via compare-and-swap with journaled majority write concern, and releases the fence.
   - Response: `{ migrationId, appId, state, sourceVersion, targetVersion, recordsTransformed, tombstonesPreserved, error? }`. Supports preactivation rollback.

3. **`GET /api/v1/apps/:app_id/strict-upgrades/:migration_id`**:
   - Queries transition state machine: `ready-v1` -> `fencing` -> `transforming` -> `validating` -> `publishing` -> `ready-v2`, `operatorRequired`, or `rolledBack`.

### Multi-Node Coordination & Permit Gate
- **Collection**: `_coordination`.
- **Document Key**: `permit:<appId>`.
- **Bounded Permits**: Enforces a strict ceiling of `MAX_CONCURRENT_PERMITS = 1000`.
- **Atomic Admission**: All data access endpoints (`bootstrap`, `push`, `pull`, `legacy-receipts`) acquire a durable permit prior to reading active contracts. The atomic CAS update checks `fenced: false` and `permitCount: { $lt: 1000 }` with `$inc: { permitCount: 1 }` and registers client metadata in `activePermits.<permit_id>`.
- **Permit Release**: When operations complete, `release_permit` filters by `activePermits.<permit_id>: { $exists: true }` before decrementing `permitCount: -1`, guarding against counter corruption.
- **Exclusive Fence Drain**: Migration claims `fenced: true`, immediately causing client requests to fail fast with HTTP 503 `migrationInProgress`. Existing permits are drained before transformation proceeds.

### Delivered Legacy-Receipt Reconciliation Route
Provides client outbox reconciliation during protocol migration without mutating data:
- **Endpoint**: `POST /api/v1/sync/:app_id/strong/legacy-receipts`.
- **Authorization**: Dual-auth (`Authorization: Bearer <jwt>`, `X-API-Key: <key>`, `X-App-Id: <appId>`) scoped to target user and app.
- **Batch Limit**: Bounded to `<= 100` receipts per request (`MAX_BATCH_SIZE`).
- **Protocol Check**: Strictly enforces `request.protocol == "strict-v1"`; mismatched versions return HTTP 426 `protocolUnsupported`.
- **Verification Logic**: Recomputes original v1 SHA-256 digest against submitted data payload. Classifies operations:
  - `provenAccepted`: Matches current row's `lastOp` (`opId` and `digest`). Performs durable conditional update writing fresh `confirmationNonce` (`matched_count == 1`). Returns `acceptedRevision`.
  - `provenNotAccepted`: Current revision matches expected revision (operation was never applied). Returns current state.
  - `unknownSuperseded`: Current revision differs from expected revision (operation was superseded by later mutation). Returns current state for manual review.
  - `integrityFailure`: Invalid UUIDs, unknown table, non-matching digest, or malformed data payload.

### Standard Error Mapping
- `400 invalidRequest`: Malformed schema, batch size > 100, invalid parameter values.
- `401 unauthorized`: Missing or invalid bearer JWT; non-admin user on admin endpoints.
- `403 forbidden`: Missing API key; admin token lacking explicit target `appId` in `claims.apps`.
- `404 appNotFound`: Unknown application ID in app registry.
- `409 epochMismatch` / `preconditionFailed`: Stale schema fingerprint, epoch conflict, or preflight purity failure.
- `426 protocolUnsupported`: Unsupported protocol version; non-v1 legacy receipts request.
- `503 migrationInProgress` / `indeterminateStorage`: Active exclusive fence, permit capacity ceiling reached, or unresolvable storage state.

### Verification and Staging Gate G3 Status
- **Automated Tests**: Unit and contract tests pass across both repositories:
  - Rust `glean-hub-sync`: 54 passed, 1 ignored (`test_strong_sync_live_mongo_acceptance_gate` due to missing `MONGO_TEST_URI`), 0 clippy warnings.
  - Vector runner: 23/23 checks passed (`scripts/strict-v2-vector-runner.mjs`).
  - Live smoke offline fixture: 18/18 checks passed (`node scripts/hub-live-smoke.mjs --fixture-test`).
  - Shared JVM tests: 116 tests passed (`./gradlew :shared:jvmTest --dependency-verification=off`).
- **Production Admission Hold**: Phase 03 backend contracts and logic are delivered with passing unit/contract checks. Production admission remains on hold: (1) Gate G3 live multi-instance replica-set acceptance is unexecuted pending staging testbed, (2) coordination-document compatibility/backfill for documents missing `permitCount` requires live verification, and (3) exact cumulative accounting across mid-batch crash interruption requires formal revalidation.
