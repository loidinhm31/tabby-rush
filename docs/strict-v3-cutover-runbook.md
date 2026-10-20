# TabbyRush Strict-v3 Coordinated Cutover & Recovery Runbook

**Document Version**: 1.0.0  
**Phase**: Phase 06 — Cross-device acceptance, rollout and documentation  
**Status**: Operator Cutover & Recovery Runbook Delivered; Staging & Production Cutover Formally Held Pending Release Gates  
**Related Documents**: [Strict-v2 Cutover Runbook](./strict-v2-cutover-runbook.md) · [Release Acceptance](./release-acceptance.md) · [System Architecture](./system-architecture.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md)

---

## 1. Executive Summary & Operational Scope

This runbook establishes the strict, versioned procedure for upgrading the TabbyRush personal task management application on Glean Hub from `strict-v2` to `strict-v3` (incorporating two-level parent/child hierarchies, compact deadline surfaces, and atomic group deletion/last-child preservation semantics).

### 1.1 In-Place Upgrade Invariants
1. **Zero Data Loss**: In-place transformation of `appTabbyRush` records; no collection recreation or record ID modifications. Existing tasks migrate as roots (`parentTaskId = null`).
2. **Epoch Invariance**: Active `epoch` UUID (`strictEpoch` property) is strictly preserved across the upgrade. Epoch rotation is forbidden during feature upgrades.
3. **Monotonic Revisions**: Monotonic record revisions, tombstones (`deleted: true`, `data: {}`), and confirmation receipts are preserved bit-for-bit.
4. **Coordinated Fencing**: Request permits in `_coordination` drain in-flight reads/writes, acquire an exclusive fence, transform batches idempotently, and publish the canonical `strict-v3` schema atomically.
5. **No Downgrade After Publication**: Once `ready-v3` is published and client hierarchy writes occur, downgrade to `strict-v2` is forbidden. Post-publication repair is forward-only.
6. **Server Dry-Run Caveat**: Glean Hub `strict_upgrade_service.rs` contains an open finding where preflight recovery logic on an already-upgraded target mutates migration records before the `dry_run` check; until upstream resolves this, dry-run must only be used on non-migrated targets.

### 1.2 Production Release Gates & Operational Holds
**DO NOT EXECUTE AGAINST PRODUCTION WITHOUT OPERATOR AUTHORIZATION.** Live production cutover is formally **HELD** until the following verified operational and platform prerequisites are satisfied:
- **Staging Testbed Verification**: Live Glean Hub staging migration rehearsal, pre-migration backup verification, permit check, and schema read-back.
- **Physical Android Launcher Acceptance**: Glance widget layout scaling (120x120 to 320x360dp), 200% font scaling, and >=48dp touch target verification on connected physical hardware.
- **Native Desktop Sticky Acceptance**: Native Linux (X11/Wayland) and native Windows DPAPI/MSI runtime display verification, high-DPI scaling, always-on-top behavior, and system tray recovery.
- **Supply-Chain Dependency Integrity**: Strict dependency checksum verification baseline update (`gradle/verification-metadata.xml`) to permit release builds without `--dependency-verification=off`.
- **Inherited Release Holds**: Prior gates G2, G3, G5, and G6 remain tracked release boundaries.

---

## 2. Operator Prerequisites & Authorization

### 2.1 Access Requirements
- **App-Scoped Admin Authority**: The operator must possess a Glean Hub administrative JWT whose claims satisfy:
  ```json
  {
    "is_admin": true,
    "apps": ["tabby-rush"]
  }
  ```
- **Credential Custody**: Passwords and tokens must NEVER be passed as bare command-line arguments on `argv` (visible in `ps` and shell history). Set the token via environment variable:
  ```bash
  export GLEAN_ADMIN_TOKEN="<operator_jwt_bearer_token>"
  export APPROVED_HUB_ORIGIN="https://api-glean-hub.qm-hub-v001.cloud"
  ```
- **MongoDB Access**: Direct administrative access to the underlying MongoDB replica set for point-in-time snapshot backups prior to upgrade.

### 2.2 Canonical Schemas & Fingerprints
Before executing upgrade operations, verify the canonical SHA-256 schema fingerprints:

#### App ID: `tabby-rush` (Current Production Standard)
| Protocol Version | Canonical Columns | SHA-256 Fingerprint |
| :--- | :---: | :--- |
| `strict-v1` | 4 columns (`id`, `title`, `completed`, `createdAt`) | `132f914fc1a9856d5c9f2be8a145b2c41ddb1db1393efa76546f82e253924cd8` |
| `strict-v2` | 16 columns (4 original + 12 temporal/reminder fields) | `8d641c2de4142e6f62f6691d6fe91559076f6c96f7437b9467577075fd72c653` |
| `strict-v3` | 17 columns (16 strict-v2 columns + nullable `parentTaskId`) | `7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f` |

#### App ID: `doggo` (Historical Legacy Backwards-Compatibility)
| Protocol Version | Canonical Columns | SHA-256 Fingerprint |
| :--- | :---: | :--- |
| `strict-v1` | 4 columns | `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec` |
| `strict-v2` | 16 columns | `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4` |
| `strict-v3` | 17 columns | `eec72663e7367b4330d1c94196f8e44888df02fa34e240fa4d3bd29222136094` |

---

## 3. Pre-Cutover Backup & Verification

### 3.1 Verify Source State
Verify the currently active application contract via the read-only contract endpoint:
```bash
curl -fsS \
  -K <(echo "header = \"Authorization: Bearer $GLEAN_ADMIN_TOKEN\"") \
  "$APPROVED_HUB_ORIGIN/api/v1/apps/tabby-rush/strict-contract" | jq .
```
**Assert**:
- `syncMode` is `"strict-v2"` (v1 accounts must explicitly complete the supported v1→v2 prerequisite)
- `schemaFingerprint` matches canonical v2 fingerprint (`8d641c2de4142e6f62f6691d6fe91559076f6c96f7437b9467577075fd72c653` for `tabby-rush`)
- Record active `epoch` UUID for subsequent validation:
  ```bash
  ACTIVE_EPOCH=$(curl -fsS -K <(echo "header = \"Authorization: Bearer $GLEAN_ADMIN_TOKEN\"") "$APPROVED_HUB_ORIGIN/api/v1/apps/tabby-rush/strict-contract" | jq -r .epoch)
  ```

### 3.2 Execute Journaled MongoDB Backup
Take a consistent, point-in-time snapshot backup covering all application and coordination collections. Protect connection strings and credentials using environment configuration or a protected configuration file (`--config="/etc/mongodump.conf"`), never bare credentials on `argv`:

> **BACKUP TOPOLOGY NOTICE**: `mongodump --oplog` is only valid when capturing the entire MongoDB instance without collection/database scoping. When dumping a replica set instance, use whole-instance `--oplog`. When capturing a database-scoped backup, all Hub writes must be quiesced under an active coordination fence throughout dump and restore.

#### Option A: Whole-Instance Replica Set Oplog Dump (Recommended for Replica Sets)
```bash
BACKUP_DATE=$(date +%Y%m%d_%H%M%S)
mongodump \
  --config="/etc/mongodump.conf" \
  --oplog \
  --gzip \
  --out="/backup/tabbyrush/pre_v3_cutover_${BACKUP_DATE}"
```
Verify archive contents:
- `<backup_dir>/${MONGO_DB_NAME:-glean_hub}/appTabbyRush.bson.gz` (primary application collection)
- `<backup_dir>/${MONGO_DB_NAME:-glean_hub}/_schemas.bson.gz` (schema registry)
- `<backup_dir>/${MONGO_DB_NAME:-glean_hub}/_coordination.bson.gz` (fences, migration state, permits)
- `<backup_dir>/oplog.bson.gz` (replica set point-in-time transaction log)

#### Option B: Database-Scoped Backup (Requires Complete Ingress Quiescence Under Fence)
```bash
BACKUP_DATE=$(date +%Y%m%d_%H%M%S)
mongodump \
  --config="/etc/mongodump.conf" \
  --db="${MONGO_DB_NAME:-glean_hub}" \
  --gzip \
  --out="/backup/tabbyrush/pre_v3_cutover_${BACKUP_DATE}"
```
Verify archive contents:
- `<backup_dir>/${MONGO_DB_NAME:-glean_hub}/appTabbyRush.bson.gz` present and non-empty
- `<backup_dir>/${MONGO_DB_NAME:-glean_hub}/_schemas.bson.gz` present
- `<backup_dir>/${MONGO_DB_NAME:-glean_hub}/_coordination.bson.gz` present

## 4. Upgrade Execution Procedure

The following procedure applies to staging rehearsal first, then authorized production cutover. Tooling requires explicit `--expected-version "strict-v2" --target-version "strict-v3"` to prevent defaulting to legacy v1→v2 migration.
### 4.1 Step 1: Preflight Dry-Run
Execute the migration preflight check against the staging environment:
```bash
MIGRATION_ID=$(uuidgen || node -e 'console.log(crypto.randomUUID())')
node scripts/hub-strict-upgrade.mjs \
  --dry-run \
  --origin "$APPROVED_HUB_ORIGIN" \
  --app-id "tabby-rush" \
  --expected-version "strict-v2" \
  --target-version "strict-v3" \
  --migration-id "$MIGRATION_ID"
```
**Expected Outcome**:
- Preflight asserts existing `strict-v2` status.
- Returns status code 0 without modifying live documents.

### 4.2 Step 2: Fenced Staging Migration
Execute the authoritative migration with operator authentication:
```bash
node scripts/hub-strict-upgrade.mjs \
  --origin "$APPROVED_HUB_ORIGIN" \
  --app-id "tabby-rush" \
  --expected-version "strict-v2" \
  --target-version "strict-v3" \
  --migration-id "$MIGRATION_ID"
```
**Execution Sequence**:
1. Acquires exclusive lock in `_coordination`.
2. Drains in-flight requests.
3. Transforms records idempotently to `strict-v3` schema.
4. Updates canonical schema definition and publishes `strict-v3` fingerprint.
5. Releases coordination fence and activates `ready-v3` sync mode.

### 4.3 Step 3: Schema and Epoch Read-Back
Assert that the published contract reflects canonical `strict-v3` state:
```bash
curl -fsS \
  -K <(echo "header = \"Authorization: Bearer $GLEAN_ADMIN_TOKEN\"") \
  "$APPROVED_HUB_ORIGIN/api/v1/apps/tabby-rush/strict-contract" | jq .
```
**Assertions**:
- `syncMode` == `"strict-v3"`
- `schemaFingerprint` == `"7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f"`
- `epoch` == `<unmodified UUID recorded in Step 3.1>`
- Columns list contains exactly 17 fields, with `parentTaskId` nullable.
- Verify migration completion state via CLI:
  ```bash
  node scripts/hub-strict-upgrade.mjs \
    --status-only \
    --origin "$APPROVED_HUB_ORIGIN" \
    --app-id "tabby-rush" \
    --target-version "strict-v3" \
    --expected-epoch "$ACTIVE_EPOCH" \
    --migration-id "$MIGRATION_ID"
  ```
  Assert output confirms `state: ready-v3` and command exits with code 0.

## 5. Post-Cutover Verification & Adversarial Convergence

### 5.1 Legacy Client Rejection (Fail-Closed Fencing)
Verify that old writes from unsupported `strict-v1` or un-upgraded `strict-v2` clients attempting to mutate tasks fail closed:
- Legacy push requests to `/api/v1/sync/tabby-rush/strong/push` (or historical `/api/v1/sync/tabby-rush/push`) with old protocol headers are rejected with HTTP 426 (`protocolUnsupported`).
- Existing legacy receipts remain queryable via dedicated legacy receipts endpoint `/api/v1/sync/tabby-rush/strong/legacy-receipts` so clients can recover their pending outbox state.

### 5.2 Frozen Operation Replay
- Already-admitted frozen/UNCERTAIN replay preserves exact wire bytes and matches durable receipts before live-parent checks.
- Lost response replay does not gate on renewed root liveness or duplicate records.
### 5.3 Staged Full Scan & Late-Child Cleanup Convergence
- A late child arriving concurrently while its parent root is tombstoned on the server is suppressed from normal lists, counts, and schedules.
- `AccountSyncCoordinator.reconcileLateChildren` enqueues an ordinary per-row DELETE for the late child using its confirmed server revision.
- Discarding a conflicted root DELETE removes local suppression without resurrecting acknowledged child deletions.

---

## 6. Rollback & Forward Repair Strategy

### 6.1 Pre-Publication Recovery & Same-ID Resume
If network drops, client times out, or errors occur **prior to schema publication**:
1. **Status Query & Resume Observation**:
   Query migration status using the identical `$MIGRATION_ID`:
   ```bash
   node scripts/hub-strict-upgrade.mjs \
     --status-only \
     --origin "$APPROVED_HUB_ORIGIN" \
     --app-id "tabby-rush" \
     --target-version "strict-v3" \
     --expected-epoch "$ACTIVE_EPOCH" \
     --migration-id "$MIGRATION_ID"
   ```
   - Exit code 0: Migration completed successfully (`ready-v3`) with verified schema and epoch.
   - Exit code 1: Migration failed or rolled back (`operatorRequired` or `rolledBack`). Inspect logs.
   - Exit code 2: Migration is still in-progress or polling timed out.
2. **Resume In-Flight Upgrade**:
   If network dropped or polling timed out (exit code 2), re-run the upgrade command with the **SAME** `$MIGRATION_ID` and without `--status-only`:
   ```bash
   node scripts/hub-strict-upgrade.mjs \
     --origin "$APPROVED_HUB_ORIGIN" \
     --app-id "tabby-rush" \
     --expected-version "strict-v2" \
     --target-version "strict-v3" \
     --migration-id "$MIGRATION_ID"
   ```
3. **Operator Intervention**: If the server reports `operatorRequired` or unrecoverable error, inspect server logs. Keep the coordination fence held to prevent data corruption.
4. **Point-in-Time Restore**: If document transformation failed before publication and rollback is approved by the release lead:
   Establish an external maintenance fence prior to restore (do not rely on in-flight `_coordination` document).
   For whole-instance replica set oplog backup:
   ```bash
   mongorestore \
     --config="/etc/mongodump.conf" \
     --drop \
     --oplogReplay \
     --gzip \
     --dir="/backup/tabbyrush/pre_v3_cutover_${BACKUP_DATE}"
   ```
   For database-scoped backup (all relevant writers quiesced):
   ```bash
   mongorestore \
     --config="/etc/mongodump.conf" \
     --drop \
     --nsInclude="${MONGO_DB_NAME:-glean_hub}.appTabbyRush" \
     --nsInclude="${MONGO_DB_NAME:-glean_hub}._schemas" \
     --nsInclude="${MONGO_DB_NAME:-glean_hub}._coordination" \
     --gzip \
     --dir="/backup/tabbyrush/pre_v3_cutover_${BACKUP_DATE}"
   ```


### 6.2 Post-Publication: Forward-Repair Only
Once the `strict-v3` schema is published and client hierarchy writes commence:
- **No Downward Schema Migration**: Do not attempt to revert MongoDB to `strict-v2`. Doing so would drop or corrupt `parentTaskId` relationships and violate client database constraints.
- **Forward Repair**: Resolve schema discrepancies, stuck operations, or network anomalies through idempotent sync reconciliation or app updates.
