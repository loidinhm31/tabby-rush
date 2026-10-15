# Doggo Security Model & Operational Disaster Recovery

**Document Version**: 1.3.0
**Phase**: 12 — Coordinated Cutover, Recovery Runbook & Release Documentation
**Status**: Platform credential custody model; Windows ACL limits and strict-v2 recovery boundaries documented
**Related Documents**: [Strict-v2 Cutover Runbook](./strict-v2-cutover-runbook.md) · [Deployment Guide](./deployment-guide.md) · [Release Acceptance](./release-acceptance.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [System Architecture](./system-architecture.md) · [Windows Desktop Guide](./windows-desktop-guide.md) · [Windows Acceptance](./windows-acceptance.md)

---

## 1. Security Architecture & Threat Model

### 1.1 Credential & Key Lifecycle
- **Zero Plaintext Credentials in Storage**: Passwords exist in memory only during user submission and are never stored in databases, shared preferences, view models, or process bundles.
- **OS-Protected Platform Credential Custody**:
  - Android: `AndroidKeystoreVault` uses Android KeyStore with AES-256-GCM to encrypt access and refresh tokens.
  - Linux: Freedesktop Secret Service API via D-Bus (Phase 09).
  - Windows: `WindowsDpapiVault` uses JNA `CryptProtectData` / `CryptUnprotectData` in current-user scope; it stores the encrypted account-key/token pair in `credentials.dpapi` using temporary-file and atomic replacement. Machine-wide scope and plaintext fallback are prohibited.
  - **Windows ACL limitation**: `applyRestrictiveUserAcl()` attempts an owner ACL before the atomic move, but returns when the ACL view or owner is absent and suppresses exceptions. Effective owner-only ACL enforcement is therefore best-effort, not guaranteed. The vault also treats a missing/zero-length file as no saved credentials and does not reject an empty serialized account key; these edge cases remain security-hardening concerns.
- **API Key Classification**: The `DOGGO_API_KEY` is an extractable client application identifier, NOT an administrative credential. It identifies the client as authorized for the `doggo` app schema. Human user authentication relies exclusively on short-lived JWT access tokens and rotatable refresh tokens.
- **Single-Flight Token Refresh**: Mutex-coordinated refresh ensures only one in-flight network refresh executes at a time. All concurrent requests wait on the resulting token or fail closed.

### 1.2 Network & Communication Security
- **Strict HTTPS Enforcement**: Release builds reject cleartext HTTP traffic unconditionally (`android:usesCleartextTraffic="false"`).
- **No-Redirect Policy**: The Ktor HTTP engine and tooling scripts reject HTTP 3xx automatic redirects to prevent TLS downgrade and credential forward attacks.
- **Fail-Closed Wipe Protocol**: Account logout or server reset triggers synchronous cancellation of all scheduled sync workers, deletion of Room SQLite files, clearing of vault credentials, and persistence of fail-closed wipe markers.

---

## 2. Hub Strict Persistence Invariants

1. **Namespace Isolation**:
   - Each strict-v1 application is partitioned into a dedicated collection: `app_<appId>` (e.g. `app_doggo`).
   - Legacy Glean Hub camelCase collections (`app<AppId>`) are never queried, updated, or written to by strict endpoints.
2. **CAS & Tombstone Policy**:
   - Inserts: require `expectedRevision === 0` and produce revision 1.
   - Updates: require exact `expectedRevision === currentRevision`.
   - Deletions: require revision CAS and persist a permanent empty tombstone (`deleted: true`, `data: {}`). Physical deletion of records is strictly prohibited to guarantee monotonic sync history.
3. **Monotonic Epoch Fencing**:
   - The authoritative `strictEpoch` is registered in the Hub `_schemas` collection.
   - Client requests presenting mismatched epochs receive HTTP 409 Conflict with `code: "epochMismatch"`, placing the client sync coordinator in `ResetRequired` state. Clients never auto-rebase across epoch mismatches.

---

## 3. Disaster Recovery Runbook: Strict-v1 Fresh-Epoch Recovery (Emergency Cold Restore Only)

> **QUARANTINE & SAFETY NOTICE**: This section and `scripts/hub-strict-recovery.js` apply **exclusively** to emergency disaster recovery under the legacy `strict-v1` protocol. It creates a fresh UUID epoch and whitelists only the 4 original columns (`id`, `title`, `completed`, `createdAt`). It is **NOT** compatible with `strict-v2`, cannot be used as a feature rollback mechanism, and will destroy temporal metadata if run against a v2 database. For strict-v2 upgrades and incidents, see Section 4 and the [Strict-v2 Cutover Runbook](./strict-v2-cutover-runbook.md).

When catastrophic database corruption or node loss requires restoring records from cold storage under `strict-v1`, the protocol dictates allocating a **fresh external UUID epoch** and provisioning an isolated target collection rather than in-place overwriting:
```
┌────────────────────────────────────────────────────────┐
│ 1. Halt Ingress Traffic & Quiesce All Hub Writers      │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 2. Restore Source Backup into Staging Collection       │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 3. Allocate Fresh UUID Epoch & Provision New Namespace │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 4. Sanitize Records: Revision 1, Empty Tombstones      │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 5. Execute Rust Candidate Preflight Helper             │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 6. Atomic Journaled Registry CAS: Old Epoch -> New     │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 7. Verify Registry Majority & Unblock Ingress Traffic  │
└────────────────────────────────────────────────────────┘
```

### 3.1 Step-by-Step Recovery Procedure

#### Step 1: Halt Ingress Traffic & Verify Quiescence
Drain all ingress connections and pause background worker jobs to ensure complete writer quiescence:
```bash
export CONFIRM_MAINTENANCE_QUIESCENCE="true"
```

#### Step 2: Restore Source Backup into Staging Collection
Restore the sanitized MongoDB dump into a temporary staging collection:
```bash
# Protected restore using environment credentials
mongorestore --drop --nsInclude="glean_hub.staging_doggo_restore" backup/doggo/
```

#### Step 3: Configure Environment & Execute Recovery Script
Provide connection details and parameters via protected environment variables (never on CLI argv):
```bash
export MONGO_URI="mongodb+srv://<db_user>:<db_password>@<db_cluster>/glean_hub"
export MONGO_DB_NAME="glean_hub"
export APP_ID="doggo"
export EXPECTED_OLD_EPOCH="550e8400-e29b-41d4-a716-446655440000"
export SOURCE_BACKUP_COLLECTION="staging_doggo_restore"
export CONFIRM_MAINTENANCE_QUIESCENCE="true"

# Preflight test without mutations
DRY_RUN=true node scripts/hub-strict-recovery.js

# Live execution under mongosh
mongosh --nodb --file scripts/hub-strict-recovery.js
```

#### Step 4: Validate Preflight & Atomic CAS
1. The script imports and sanitizes records, resetting active items to revision 1 and tombstones to `{}`.
2. It executes the Rust `strong_sync_admission` helper in a subprocess passing credentials via environment variables. If preflight fails, registry publication is blocked fail-closed.
3. It performs atomic Compare-And-Swap on `_schemas`:
   ```javascript
   db._schemas.updateOne(
     { appId: "doggo", strictEpoch: EXPECTED_OLD_EPOCH, syncMode: "strict-v1" },
     { $set: { strictEpoch: newEpoch, recoveryTimestamp: new Date(), lastRecoveryStatus: "fresh_epoch_promoted" } },
     { writeConcern: { w: "majority", j: true } }
   );
   ```
4. If CAS succeeds, the previous collection is preserved intact (never dropped).
5. If CAS times out or is ambiguous, ingress traffic remains BLOCKED until the operator manually inspects `_schemas` on primary majority.
6. Restart Hub API services. Connected Doggo clients will receive an `epochMismatch` HTTP 409 notice, pause in `ResetRequired` state, and await explicit operator review.

---

## 4. Strict-v2 Feature Upgrade & Incident Recovery Boundaries

### 4.1 In-Place Upgrade vs. Disaster Recovery
Upgrading Doggo to `strict-v2` is a **data-preserving, in-place feature migration**, fundamentally distinct from emergency fresh-epoch disaster recovery:
- **Preserves Active Epoch**: `strictEpoch` UUID remains unchanged in `_schemas`.
- **Preserves Collection Identity**: All data resides in `app_doggo` throughout the transform.
- **Preserves Monotonic Revisions**: Existing revisions, tombstones, and confirmation receipts are preserved bit-for-bit.
- **Non-Destructive Defaults**: Missing v2 temporal fields default to null (reminders disabled) without modifying user data.

### 4.2 State-Specific Recovery Matrix
During an upgrade, operators must adhere to strict state-specific recovery rules:
1. **Pre-Fence Rejection**: Application remains clean `strict-v1`. No database rollback needed. Correct parameters or credentials and re-attempt.
2. **Fencing Stall / Orphan Permits**: If permits fail to drain or a runner crashes holding the fence, **KEEP FENCED**. Escalate to database operators to verify queries before releasing permits. Never clear permits by TTL or arbitrary shell commands.
3. **Interrupted Batch Transform**: Idempotently resume using the **exact same** `--migration-id <UUID>` via `scripts/hub-strict-upgrade.mjs`. The engine resumes from `checkpointRowId`. *(Note: `--status-only` inspects status and does not resume transform execution).*
4. **Lost Publish Response**: Query status with `--status-only`. If the server published `ready-v2`, verify contract read-back. If stalled, resume with same migration ID.
5. **Preactivation Rollback**: If an unrecoverable validation error occurs before publishing v2, execute preactivation rollback under fence: strip only migration-added fields, verify 4-column schema, and restore `ready-v1`.
6. **Post-v2 Forward Repair Only**: Once `strict-v2` is published to `_schemas`, **DOWNWARD ROLLBACK IS FORBIDDEN**. Restoring a v1 backup or downgrading client/server binaries would wipe newly saved deadlines and permanently corrupt offline client intentions. All post-cutover issues must be resolved via forward engineering hotfixes.

Consult the [Strict-v2 Cutover Runbook](./strict-v2-cutover-runbook.md) for full operational commands and verification scripts.
