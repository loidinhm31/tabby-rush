# Doggo Strict-v2 Coordinated Cutover & Recovery Runbook

**Document Version**: 1.0.0  
**Phase**: 12 — Coordinated Cutover, Recovery Runbook & Release Documentation  
**Status**: Authoritative Operator Runbook Delivered; Production Cutover Blocked Pending Gates G2, G3, G6, and G7  
**Related Documents**: [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md) · [Release Acceptance](./release-acceptance.md) · [System Architecture](./system-architecture.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Hub Schema Integration](./hub-schema-integration.md)

---

## 1. Executive Summary & Operational Scope

This runbook establishes the strict, versioned procedure for upgrading the Doggo personal task management application on Glean Hub from `strict-v1` to `strict-v2` (incorporating task deadlines, timezones, and offline reminders).

### 1.1 In-Place Upgrade Invariants
1. **Zero Data Loss**: In-place transformation of `appDoggo` records; no collection recreation or record ID modifications.
2. **Epoch Invariance**: Active `strictEpoch` UUID is strictly preserved across the upgrade. Epoch rotation is forbidden during feature upgrades.
3. **Monotonic Revisions**: Monotonic record revisions, tombstones (`deleted: true`, `data: {}`), and confirmation receipts are preserved bit-for-bit.
4. **Coordinated Fencing**: Request permits in `_coordination` drain in-flight reads/writes, acquire an exclusive fence, transform batches idempotently, and publish the canonical `strict-v2` schema atomically.
5. **Single Server Operation**: Upgrade execution is exposed through two identical, version-guarded interfaces:
   - CLI Runner: `node scripts/hub-strict-upgrade.mjs`
   - Web Admin UI: `glean-hub-app` at `/admin/apps/:appId/edit`

### 1.2 Production Gate G7 Blocker Notice
**DO NOT EXECUTE AGAINST PRODUCTION.** Live production cutover (Gate G7) is formally **BLOCKED** until the following verified operational and code prerequisites are satisfied:
- **Gate G3 (Cluster Staging & Code Remediations)**: Live multi-instance MongoDB replica set migration execution and resolution of three open Phase 03 code review findings:
  1. Permit release duplicate filter to prevent counter drift.
  2. Coordination document `permitCount` backfill for numeric predicates.
  3. Batch mutation and checkpoint update atomic transaction consistency.
- **Gate G2 (Platform Hardware & Adapters)**: Connected Android physical device Doze/idle quotas, graphical Linux desktop D-Bus notification bubble display, and native Windows COM/WinRT toast bridge implementation (current stub returns safe `Unavailable`).
- **Gate G6 (Three-Device Live Sync)**: Live concurrent round-trip synchronization between Android, Linux, and Windows.
- **Dependency Integrity**: Regeneration of `gradle/verification-metadata.xml` to allow Gradle builds without `--dependency-verification=off`.

---

## 2. Operator Prerequisites & Authorization

### 2.1 Access Requirements
- **App-Scoped Admin Authority**: The operator must possess a Glean Hub administrative JWT whose claims satisfy:
  ```json
  {
    "is_admin": true,
    "apps": ["doggo"]
  }
  ```
  *(Global admin tokens with wildcard access are also admitted; standard user tokens or admin tokens scoped to other applications are rejected with HTTP 403 Forbidden).*
- **Credential Custody**: Passwords and tokens must NEVER be passed as bare command-line arguments on `argv` (visible in `ps` and shell history). Set the token via environment variable:
  ```bash
  export GLEAN_ADMIN_TOKEN="<operator_jwt_bearer_token>"
  export APPROVED_HUB_ORIGIN="https://api-glean-hub.qm-hub-v001.cloud"
  ```
  *(Note: In shared environments where shell expansions on `argv` could be observed in process listings, avoid `curl -H "$TOKEN"` on interactive command lines; prefer `scripts/hub-strict-upgrade.mjs` which reads `$GLEAN_ADMIN_TOKEN` directly via protected environment custody, or pass headers via curl configuration files (`curl -K <(echo "header = \"Authorization: Bearer $GLEAN_ADMIN_TOKEN\"")`). Similarly, protect database connection strings using environment configuration or auth files rather than bare command lines).*
- **MongoDB Access**: Direct administrative access to the underlying MongoDB replica set for restorable snapshot backups prior to upgrade.

### 2.2 Canonical Schemas & Fingerprints
Before executing any upgrade commands, confirm the canonical SHA-256 schema fingerprints:

| Protocol Version | Canonical Columns | SHA-256 Fingerprint |
| :--- | :---: | :--- |
| `strict-v1` | 4 columns (`id`, `title`, `completed`, `createdAt`) | `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec` |
| `strict-v2` | 16 columns (4 original + 12 temporal/reminder fields) | `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4` |

---

## 3. Pre-Cutover Backup & Verification

### 3.1 Verify Source State
Verify the currently active application contract via the read-only contract endpoint:
```bash
curl -fsS \
  -H "Authorization: Bearer $GLEAN_ADMIN_TOKEN" \
  "$APPROVED_HUB_ORIGIN/api/v1/apps/doggo/strict-contract" | jq .
```
**Assert**:
- `syncMode` is `"strict-v1"`
- `schemaFingerprint` is `"3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec"`
- Record the active `strictEpoch` UUID for subsequent validation.

### 3.2 Execute Journaled MongoDB Backup
Take a consistent, point-in-time snapshot backup of the MongoDB database prior to any migration activity.
> **CONSISTENCY NOTICE**: Sequential, uncoordinated collection dumps during live traffic do not guarantee a consistent recovery point across documents, schemas, and coordination. The backup must be executed either during an active maintenance fence or using a replica-set oplog dump (`--oplog`) covering all application and coordination collections:
```bash
# In protected operator shell on database host
BACKUP_DATE=$(date +%Y%m%d_%H%M%S)
mongodump \
  --uri="$MONGO_URI" \
  --db=glean_hub \
  --oplog \
  --out="/backup/doggo/pre_v2_cutover_${BACKUP_DATE}"
```
Verify the archive restoration on an isolated staging instance before proceeding with the production cutover window.

### 3.3 Quarantine Notice: Legacy Disaster Recovery Script
> **CRITICAL WARNING**: Do NOT use `scripts/hub-strict-recovery.js` during a strict-v2 upgrade or recovery. That script is strictly an emergency fresh-epoch disaster recovery tool for `strict-v1` that hardcodes a 4-column whitelist and performs a `strict-v1` registry CAS. Running it against `strict-v2` will strip deadline metadata, allocate a new epoch, and cause irreversible client reset conflicts.

---

## 4. Coordinated Deployment Sequence

The deployment must follow this strict chronological order:

```mermaid
sequenceDiagram
  autonumber
  actor Op as Operator
  participant Hub as Glean Hub Nodes
  participant Client as Doggo Clients
  participant DB as MongoDB Cluster

  Op->>Hub: 1. Deploy fence-aware Glean Hub binary to all nodes
  Note over Hub: Backend runs strict-v1 with v2-fencing support
  Op->>Client: 2. Publish Room v2 Doggo client binaries
  Note over Client: Clients run Room v2; deadline UI blocked on v1 server
  Op->>Hub: 3. Execute non-mutating preflight dry-run
  Hub->>DB: Scan & validate appDoggo records
  Op->>Hub: 4. Initiate live upgrade with durable migration UUID
  Hub->>DB: Acquire exclusive fence in _coordination
  Note over Hub: New traffic rejected with HTTP 503 (migrationInProgress)
  Hub->>DB: In-place transform batches (500 records/batch)
  Hub->>DB: CAS publish strict-v2 in _schemas
  Hub->>DB: Release fence in _coordination
  Op->>Hub: 5. Execute mandatory read-back assertion
  Note over Client: Clients transition ready-v1 -> ready-v2
  Client->>Hub: 6. Reconcile legacy receipts & resume v2 sync
```

### Step 1: Deploy Fence-Aware Hub Binary to All Nodes
Deploy the coordination-aware Glean Hub binary across all cluster instances. Confirm that all instances are running the updated binary before proceeding. Confirm old binary instances are stopped and cannot rejoin.

### Step 2: Publish Updated Doggo Client Binaries
Publish the updated Doggo clients (Android APK, Linux package, Windows MSI) containing Room migration `1->2` and the `ContractTransitionCoordinator`.  
- **Client Behavior**: Clients will upgrade their local database to Room v2 with `contractVersion = "strict-v1"`.
- **UI Guard**: Normal task creation, editing, and completion sync as usual. Deadline and reminder submission controls display a disabled state with notice: *"Backend upgrade required to enable task deadlines and reminders."*

### Step 3: Execute Non-Mutating Preflight Dry-Run
Execute the preflight validation via CLI:
```bash
node scripts/hub-strict-upgrade.mjs \
  --origin "$APPROVED_HUB_ORIGIN" \
  --app-id doggo \
  --dry-run
```
**Expected Output**:
```
[Preflight] Validating preflight conditions...
[Preflight] Source syncMode: strict-v1
[Preflight] Source fingerprint matches canonical strict-v1
[Preflight] Target fingerprint matches canonical strict-v2
[Preflight] Dry-run completed successfully. Ready for upgrade.
```

### Step 4: Initiate Controlled Live Upgrade
Generate a UUIDv4 `migrationId` and execute the upgrade:
```bash
MIGRATION_ID=$(node -e "console.log(crypto.randomUUID())")
echo "Starting migration with ID: $MIGRATION_ID"

node scripts/hub-strict-upgrade.mjs \
  --origin "$APPROVED_HUB_ORIGIN" \
  --app-id doggo \
  --migration-id "$MIGRATION_ID"
```
*(Alternatively, navigate to `/admin/apps/doggo/edit` in `glean-hub-app` and click "Start Upgrade" in the Strict Contract panel).*

The runner will:
1. Validate preconditions and active epoch.
2. Acquire exclusive fence in `_coordination` (`fenced: true`, `fenceOwner: migration_id`).
3. Drain admitted reader/writer permits.
4. Transform `appDoggo` live documents in batches of 500, setting null/default values for the 12 new fields while leaving tombstones empty (`data: {}`).
5. Incrementally update `checkpointRowId` in `_schemas.activeMigration`.
6. Validate all documents adhere to the 16-column schema.
7. Atomically CAS publish `syncMode: "strict-v2"` with the canonical v2 fingerprint in `_schemas`.
8. Release the exclusive fence in `_coordination`.
9. Execute a mandatory read-back verification against `/api/v1/apps/doggo/strict-contract`.

---

## 5. Post-Cutover Verification Checklist

Immediately following completion:

### 5.1 Protocol & Contract Read-Back Assertion
```bash
curl -fsS \
  -H "Authorization: Bearer $GLEAN_ADMIN_TOKEN" \
  "$APPROVED_HUB_ORIGIN/api/v1/apps/doggo/strict-contract" | jq .
```
Verify:
- `syncMode`: `"strict-v2"`
- `schemaFingerprint`: `"5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4"`
- `epoch`: Identical to the pre-cutover UUID recorded in Step 3.1 (confirming strict epoch invariance).
- `columns`: Exactly 16 columns matching canonical definitions.

### 5.2 Legacy Receipts Route Verification
Verify that the legacy receipts reconciliation endpoint is responsive and rejects unauthenticated/unscoped calls:
```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  "$APPROVED_HUB_ORIGIN/api/v1/sync/doggo/strong/legacy-receipts" \
  -d '{"operations":[]}'
```
Must return HTTP 401 or 403 (unauthorized).

### 5.3 Client Transition & Replay Smoke
1. Launch an updated Doggo client previously synchronized under `strict-v1`.
2. Observe `ContractTransitionCoordinator` logs transitioning through:
   `ready-v1` -> `upgrade-detected` -> `dispatch-quiesced` -> `legacy-reconciling` -> `v2-base-scanning` -> `safe-intents-translating` -> `ready-v2`.
3. Create a task with a deadline and reminder rule. Verify push and pull operations succeed without error.
4. Verify overdue badge rendering and reminder notification scheduling.

---

## 6. Recovery & Incident Decision Matrix

If an interruption, failure, or timeout occurs during the upgrade, follow this state-specific incident decision matrix:

| Migration State / Incident | Root Cause | Operator Procedure | Safety Invariant |
| :--- | :--- | :--- | :--- |
| **Pre-Fence Rejection** (HTTP 4xx on POST) | Stale source version, fingerprint mismatch, or token scope error | Application remains unchanged `strict-v1`. Review error details, correct credentials or configuration, and re-run. | Zero database mutation; no rollback required. |
| **Fencing Stall / Orphan Permits** | In-flight permits failed to drain; previous crashed runner held fence | **STOP. Keep fenced.** Inspect `_coordination` document `permit:doggo`. Verify whether active Hub nodes are executing queries. If nodes are idle and runner crashed, escalate to database operator. **NEVER blindly clear permits by TTL or shell script.** | Maintaining fence prevents mixed-version read/write corruption. |
| **Interrupted Batch Transform** (Network timeout, process kill) | Hub or CLI process terminated during batch transformation | **Idempotent Resume.** Re-run the CLI upgrade runner using the **EXACT SAME** `--migration-id`: `node scripts/hub-strict-upgrade.mjs --app-id doggo --migration-id <SAME_UUID>`. The engine resumes from `checkpointRowId`. *(Note: `--status-only` only queries status; do not use it to resume execution).* | Idempotent set operations avoid duplicate counting or record corruption. |
| **Lost Publish Response** (CLI timed out during validation/publish) | Network dropped between operator and Hub during final phase | Query status with `--status-only`: `node scripts/hub-strict-upgrade.mjs --app-id doggo --migration-id <UUID> --status-only`. If status is `ready-v2`, verify contract read-back. If state is `operatorRequired` or `publishing`, re-run with matching `--migration-id` without `--status-only`. | Atomic CAS in MongoDB guarantees either clean publication or preserved preflight state. |
| **Preactivation Rollback** (Failure detected before v2 publish) | Validation detected invalid records during transform | If the backend aborts before publishing v2, the migration engine initiates preactivation rollback under fence: removes the 12 migration fields, verifies 4-column v1 schema, and restores `ready-v1`. If stalled, keep fenced and inspect database logs. | Never release fence until original snapshot is verified. |
| **Post-v2 Publish Failure** (Errors observed after v2 is active) | Client bug or unexpected data format under v2 | **FORWARD REPAIR ONLY.** **DO NOT RESTORE V1 BACKUP OR ROLL BACK TO V1.** Restoring v1 will erase all new deadlines and permanently corrupt offline client intentions. Keep backend on v2, fence ingress if necessary, and deploy a forward hotfix. | Non-destructive evolution principle: never regress schema versions once published. |

---

## 7. Archive & Incident Sign-Off

Upon successful cutover verification:
1. Retain the pre-cutover MongoDB backup archive for **30 days** in secure, encrypted storage.
2. Archive the cutover execution log containing:
   - Migration UUID
   - Operator identity and timestamp
   - Pre-cutover and post-cutover schema fingerprints
   - Total transformed record count
   - Verification curl outputs
3. Notify stakeholders and release leads that `appDoggo` is operating under `strict-v2`.
