# Doggo Hub Schema Integration

Date: 2026-10-15  
Phase: Phase 04 Hub Admin UI and Upgrade Script Delivered (Phase 03 Backend Contracts & Logic Delivered; Gate G3 Live Multi-Instance Staging Pending)  
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

---

## Operator Surfaces: Admin UI and Upgrade CLI (Phase 04 Delivered)

Phase 04 delivers administrative operator tooling to discover strict contracts, evaluate migration preconditions, run dry-run preflight checks, execute in-place upgrades from `strict-v1` to `strict-v2`, and monitor/resume operations safely across both browser and CLI surfaces.

### 1. Glean Hub Admin Web Interface (`glean-hub-app`)
- **Location**: `/admin/apps/:appId/edit` (`src/pages/admin/edit-app.tsx`).
- **Component Structure**:
  - `StrictContractCard` (`src/components/organisms/strict-contract-card.tsx`): Renders active sync mode, strict epoch UUID, canonical SHA-256 schema fingerprint, and read-only column specifications (4 columns for `strict-v1`, 16 columns for `strict-v2`).
  - `StrictAppUpgradePanel` (`src/components/organisms/strict-app-upgrade-panel.tsx`): Orchestrates preflight dry-run discovery, operator confirmation modals, bounded status polling, same-migration-ID resumption, and mandatory post-upgrade read-back assertion.
  - `StrictUpgradeControls` (`src/components/organisms/strict-upgrade-controls.tsx`): Provides action controls for Dry Run Preflight, Execute Upgrade, and Manual Resume.
- **Strict Application Guarding**: Strict applications (`syncMode: strict-v1` or `strict-v2`) automatically suppress the editable legacy schema builder and sharing configuration editor. The save handler is guarded against arbitrary `PUT /api/v1/apps/:appId/schema` calls as defense in depth.
- **Scope Authorization Guard**: If the admin JWT lacks the target `appId` in `claims.apps`, the UI displays a scope warning and disables mutation buttons while preserving read-only schema display.
- **Operator Migration Panel Capabilities**:
  - **Impact Notice**: Outlines safety invariants and migration consequences (zero task payload loss, revision sequence continuity, legacy receipt reconciliation preservation, temporary synchronization pause under exclusive fence, incompatibility of unmigrated client versions).
  - **Preflight Dry Run**: Issues non-mutating `POST /api/v1/apps/:appId/strict-upgrades` with `dryRun: true` to discover eligible live records and tombstones without modifying data, advancing schema version, or consuming a migration ID.
  - **Controlled Upgrade Execution**: Requires explicit operator confirmation typing the application name before initiating the live transition with a freshly minted UUIDv4 migration ID.
  - **Safe Resumption**: Persists the active migration ID in `sessionStorage` (scoped to `strict_upgrade_mig_${appId}`). Operators can reload the page or navigate away without losing the active migration ID or minting orphan migrations. A manual resumption input allows querying and tracking any existing migration UUID.
  - **Bounded Status Polling**: Queries `GET /api/v1/apps/:appId/strict-upgrades/:migrationId` every 1500ms until reaching a terminal state (`ready-v2`, `operatorRequired`, or `rolledBack`). Note: Interactive browser UI behavior, modal confirmation input, and browser session-storage recovery across page reloads remain manually verified; they are not covered by automated headless browser tests.
  - **Mandatory Read-Back**: Upon reaching `ready-v2`, automatically asserts `syncMode: strict-v2` and canonical v2 fingerprint via fresh GET contract query before rendering completion.

### 2. Dedicated CLI Upgrade Runner (`scripts/hub-strict-upgrade.mjs`)
- **Usage Synopsis**:
  ```bash
  # 1. Preflight dry-run check (non-mutating)
  node scripts/hub-strict-upgrade.mjs --app-id doggo --dry-run

  # 2. Execute full migration (automated bounded polling and final read-back assertion)
  node scripts/hub-strict-upgrade.mjs --app-id doggo

  # 3. Resume interrupted execution with explicit migration ID (resumes server transform)
  node scripts/hub-strict-upgrade.mjs --app-id doggo --migration-id <UUID>

  # 4. Passive status inspection (queries current state without initiating mutations)
  node scripts/hub-strict-upgrade.mjs --app-id doggo --migration-id <UUID> --status-only
  ```
- **CLI Flags & Options**:
  | Flag | Default | Description |
  |---|---|---|
  | `--origin <URL>` | `$APPROVED_HUB_ORIGIN` / `$GLEAN_ORIGIN` / `http://127.0.0.1:3000` | Glean Hub server origin URL |
  | `--app-id <ID>` | `doggo` (or `$DOGGO_APP_ID`) | Target strict application identifier |
  | `--token <JWT>` | `$GLEAN_ADMIN_TOKEN` | Admin JWT bearer token scoped to target app |
  | `--expected-version <VER>` | `strict-v1` | Source protocol version expected on server |
  | `--expected-fingerprint <HEX>` | `3361a80f...` | Canonical schema fingerprint of source version |
  | `--target-version <VER>` | `strict-v2` | Target protocol version |
  | `--target-fingerprint <HEX>` | `5f539551...` | Canonical schema fingerprint of target version |
  | `--expected-epoch <UUID>` | `null` | Optional epoch assertion |
  | `--migration-id <UUID>` | Auto-generated UUIDv4 | Explicit migration ID to execute or resume |
  | `--dry-run` | `false` | Non-mutating preflight validation only |
  | `--status-only` | `false` | Query status of existing migration ID without initiating transform |
  | `--poll-interval <MS>` | `1500` | Status polling interval in milliseconds |
  | `--timeout <MS>` | `60000` | Execution timeout in milliseconds |
  | `-h, --help` | `false` | Print CLI usage instructions and exit |
- **Exit Code Contract**:
  | Code | Status | Meaning |
  |:---:|---|---|
  | `0` | Success | Migration confirmed with final read-back assertion, dry-run passed, app already upgraded, or `--help` displayed |
  | `1` | Error | Precondition mismatch, HTTP 4xx rejection, authentication failure, terminal error state (`operatorRequired`, `rolledBack`), or read-back assertion failed |
  | `2` | Timeout | Polling loop exceeded timeout before terminal state reached. Fence may remain active on server; resume via `--migration-id <UUID> --status-only` |
- **Operational Invariants**:
  - **Precondition Verification**: Queries `GET /api/v1/apps/:appId/strict-contract`, verifying active sync mode, canonical v1 schema fingerprint, and matching epoch prior to dispatching mutations.
  - **Idempotent Already-Upgraded Handling**: If the app is already on `strict-v2` with matching target fingerprint, exits 0 safely.
  - **Same-Migration-ID Invariant**: If a network drop or interruption occurs during the initial `POST`, the script re-uses the active migration ID for status polling; never blindly mints duplicate IDs.
  - **Definitive 4xx Fail-Fast**: HTTP 400 (`invalidRequest`), 403 (`forbidden`), and 409 (`epochMismatch` / `preconditionFailed`) rejections exit immediately with code 1 instead of polling 404s.
  - **Complete Secret Redaction**: Scrubs `Bearer <token>` patterns and bare token substrings from all stdout and stderr log outputs.
  - **Mandatory Final Read-Back Assertion**: Upon reaching `ready-v2`, queries `GET /strict-contract` and asserts `syncMode === 'strict-v2'`, canonical SHA-256 fingerprint (`5f539551...`), and all 16 columns active prior to exiting 0.

### 3. Version-Aware Tooling Updates
- `scripts/hub-schema-provision.mjs`: Supports dual-schema validation (4 columns for `strict-v1`, 16 columns for `strict-v2`), and handles idempotent provisioning of already-upgraded v2 apps without failure.
- `scripts/hub-compatibility-smoke.mjs`: Accepts both `strict-v1` and `strict-v2` bootstrap protocols, and adds Gate 5 strict contract metadata validation (`GET /api/v1/apps/:appId/strict-contract`).
- `scripts/hub-live-smoke.mjs`: Dynamically adapts to active admitted protocol (`strict-v1` or `strict-v2`) in live mode, emitting compliant strict-v2 task payloads (`reminderCount: 1`), while preserving strict-v1 offline fixture coverage (`--fixture-test`).

### 4. Phase 04 Automated Verification Matrix & Gate Status

| Scope / Test Suite | Runner / Command | Units / Checks | Result | Execution Note |
|---|---|:---:|:---:|---|
| Hub Admin Service & Preload Unit Tests | `pnpm --prefix ../glean-hub/glean-hub-app test:unit` | 13 tests (2 suites) | **PASS** | 120 ms, Node native runner (8 service contract tests + 5 preload tests; browser UI interactions unexercised) |
| Hub Admin Web Production Build | `pnpm --prefix ../glean-hub/glean-hub-app build` | 10,411 modules | **PASS** | `tsc` clean, 9.87s Vite bundle |
| Doggo Strict Upgrade CLI Test Suite | `node scripts/hub-strict-upgrade.test.mjs` | 7 tests (1 suite) | **PASS** | 10.8s, local mock HTTP server; exercises dry-run, interrupted POST recovery, polling, and read-back |
| Hub Live Smoke Offline Fixture Suite | `node scripts/hub-live-smoke.mjs --fixture-test` | 18 checks (5 suites) | **PASS** | Strict-v1 protocol fixtures against in-process mock server |
| Strict-v2 Vector Runner | `node scripts/strict-v2-vector-runner.mjs` | 23 checks (2 fp + 21 vectors) | **PASS** | Preimage framing & SHA-256 digests |
| Release Security Static Audit | `node scripts/release-security-check.mjs --diagnose-source` | 6 static checks | **PASS** | 0 findings / 0 violations under script's static rules |
| Shared JVM Multiplatform Tests | `./gradlew :shared:jvmTest --dependency-verification=off` | 15 tasks | **PASS** | 15 Gradle tasks executed (exit 0) under `--dependency-verification=off` |
| Glean Hub Rust Backend Unit Tests | `cargo test --manifest-path ../glean-hub/.../Cargo.toml --locked` | 54 tests (+1 ignored) | **PASS** | 30.7s, admission & upgrade unit routes (1 ignored: live Mongo gate) |

**Production Admission Blockers & Gate G3 Status**:
Phase 04 operator tooling and script suites are verified in unit tests and against mock HTTP fixtures. Production admission remains strictly on hold pending resolution of:
1. **Gate G3 Multi-Node Replica-Set Testbed**: Physical multi-instance cluster migration, exclusive fence draining under concurrent client writes, and live cross-entrypoint resumption between browser and CLI are unexecuted pending the live staging environment.
2. **Phase 03 Coordination Document Compatibility**: Backfill and handling for pre-existing `_coordination` documents lacking `permitCount` require live cluster verification.
3. **Phase 03 Crash Resumption Accounting**: Formal validation of exact cumulative accounting and idempotency across arbitrary mid-batch crash interruptions during document transformation.
4. **Phase 03 Duplicate Permit Release Filter**: Remediation of permit release filtering to guard against multiple decrements on repeated release calls.
