# Hub Disaster Recovery Summary Report

**Date**: 2026-10-14  
**Phase**: 08 — Authorized Hub Operations & Signed Android Release  
**Status**: Recovery Invariants & Tooling Verified; Live Cluster Execution Pending Maintenance Window  
**Related Documents**: [Security & Recovery](../security-and-recovery.md) · [Deployment Guide](../deployment-guide.md) · [Hub Schema Integration](../hub-schema-integration.md)

---

## 1. Overview & Architectural Boundaries

In Glean Hub strict-v1 synchronization, disaster recovery must strictly prevent data corruption, epoch confusion, and causal receipt desynchronization.

The recovery procedure implemented in `scripts/hub-strict-recovery.js` establishes the following architectural guarantees:
1. **Writer Quiescence Before Mutation**: Recovery cannot proceed while live ingress traffic is permitted. `CONFIRM_MAINTENANCE_QUIESCENCE=true` is strictly required.
2. **Fresh External UUID Epoch**: The script generates a new UUID v4 epoch (lowercase, canonical). It never reuses, reopens, or rolls back to a stale epoch.
3. **Dedicated Target Namespace**: Records are imported into the designated target MongoDB collection matching backend conventions: `app_${appId}`.
4. **Mandatory Source Collection Validation**: Requires non-empty `SOURCE_BACKUP_COLLECTION`. Publishing an unseeded or empty epoch is strictly prohibited. Source collection must not be the active namespace.
5. **Active Record Normalization**: Active (non-deleted) records are reset to revision 1 with `lastOp = null`. Each record must contain a valid `userId` and match the schema.
6. **Empty Tombstone Normalization**: Deleted records are imported with `deleted: true` and an empty data object (`data: {}`), ensuring tombstones remain permanent and compact.
7. **Pre-CAS Rust Preflight**: Validates the candidate collection structure using the shipped `strong_sync_admission` helper prior to updating the registry.
8. **Journaled Registry Compare-And-Swap**: Atomically updates `_schemas.strictEpoch` matching on `expectedOldEpoch` with `{ writeConcern: { w: 'majority', j: true } }`.

---

## 2. Verification Evidence & Dry-Run Logs

### 2.1 Syntax & Environment Verification
- **Syntax Check**: `node --check scripts/hub-strict-recovery.js` executed cleanly with 0 syntax errors.
- **Fail-Closed Quiescence Assertion**: Invocation without `CONFIRM_MAINTENANCE_QUIESCENCE=true` outputs:
  ```
  [FATAL] Prerequisite validation failed:
    - CONFIRM_MAINTENANCE_QUIESCENCE=true is required. All writer traffic must be halted before recovery.
    - MONGO_URI environment variable is required.
    - EXPECTED_OLD_EPOCH environment variable is required to perform journaled CAS.
    - SOURCE_BACKUP_COLLECTION environment variable is required. Publishing an unseeded epoch is prohibited.
  ```
- **Node Fail-Closed Execution**: Running live recovery in Node outside mongosh outputs:
  ```
  [FATAL] Live recovery execution requires mongosh shell environment.
  To execute live recovery against MongoDB cluster, run:
    CONFIRM_MAINTENANCE_QUIESCENCE=true MONGO_URI="..." EXPECTED_OLD_EPOCH="..." SOURCE_BACKUP_COLLECTION="..." mongosh --nodb --file scripts/hub-strict-recovery.js
  ```
- **Dry-Run Validation**:
  ```bash
  CONFIRM_MAINTENANCE_QUIESCENCE=true DRY_RUN=true node scripts/hub-strict-recovery.js
  ```
  Result:
  ```
  ================================================================
  Doggo Hub Strict-v1 Maintenance Recovery Script
  Database:       glean_hub
  App ID:         doggo
  Source Backup:  (None - required for live recovery)
  Expected Epoch: (Not provided)
  Mode:           DRY-RUN (No mutations)
  ================================================================

  [PASS] Prerequisites and configuration syntax verified cleanly.
  ```

### 2.2 Workstation Execution Scope & Verification Boundaries
- **Host Tooling Availability**: The live MongoDB recovery flow was not executed against an active database on this development workstation because `mongosh` and MongoDB services are not installed locally.
- **Verified Offline Invariants**: Verification was conducted via static Node syntax checks (`node --check`), environment parameter parsing, offline fail-closed rejection (halting on missing quiescence and missing mongosh shell environment), and clean dry-run configuration execution.
- **Dry-Run Scope Qualification**: The dry-run demonstrates offline configuration and environment parsing; it does not constitute a full recovery rehearsal or live verification of BSON type compatibility, source-record import, admission preflight, or registry CAS.
- **Live Cluster Prerequisite**: Full end-to-end recovery rehearsal against a throwaway MongoDB instance remains scheduled for the authorized cluster maintenance window under operator custody, with mongosh Int64 BSON serialization tracked as a prerequisite.
---

## 3. Operational Invariants for Cluster Administrators

1. **Reconciliation on Ambiguous CAS**: If a network timeout occurs during `_schemas.updateOne`, ingress traffic must remain BLOCKED. The operator must inspect `_schemas` on primary majority before taking any further action. Never blindly re-run the script or regenerate another epoch without reading back current state.
2. **Client Sync Behavior Post-Recovery**: Connected Doggo clients presenting the previous epoch receive `HTTP 409 Conflict` (`code: "epochMismatch"`). In accordance with frozen contract, the client transitions to `ResetRequired` state and pauses synchronization. Clients never auto-rebase across epoch changes.
3. **No Legacy Data Cross-Contamination**: Recovery scripts touch only strict collections and never read from or write to legacy Glean `app_*` collections.
