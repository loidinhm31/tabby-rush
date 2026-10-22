# TabbyRush Hub Schema Integration

Date: 2026-10-21  
Status: Standard sync with collaborative task assignment policy (version 1) and Room v5 implemented (Phases 01–08). Dedicated runtime harnesses prepared in Phase 09. G0 policy validation confirmed 2026-10-21; G1–G6 runtime verification pending Main integration. Release remains blocked; production migration unauthorized.  
Related Docs: [API Contract](./api-contract.md) · [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md) · [System Architecture](./system-architecture.md) · [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md)

---

## Current Integration

TabbyRush is a native Android/Desktop application that stores local task data in Room and uses Glean Hub's ordinary app registry, authentication, and standard sync. The Hub admin console is management tooling, not a TabbyRush web client. The current binary does not use strict-v1/v2/v3 routes or contracts.

The sync API lives under `/api/v1`:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register account |
| `POST` | `/api/v1/auth/login` | Authenticate |
| `POST` | `/api/v1/auth/refresh` | Refresh credentials |
| `GET` | `/api/v1/auth/me` | Read current identity |
| `POST` | `/api/v1/sync/{app_id}/push` | Push standard records |
| `POST` | `/api/v1/sync/{app_id}/pull` | Pull standard records |
| `POST` | `/api/v1/sync/{app_id}/delta` | Sequential push then pull |
| `GET` | `/api/v1/apps/{app_id}` | Read app/schema metadata |
| `PUT` | `/api/v1/apps/{app_id}/schema` | Conditionally update schema |
| `GET` | `/api/v1/auth/lookup?username=...` | Username identity lookup |

Standard sync uses bearer authentication plus `X-API-Key` and `X-App-Id`. Push/pull responses carry an informational `schemaVersion`; schema versions are not native feature gates and do not generate native UI, Kotlin properties, or Room migrations. Collaborative task apps additionally require `collaborationVersion: 1` and `ownerUserId` in record envelopes. The Hub accepts unknown JSON fields; the client retains raw unknown fields and merges only explicit known local intent.

### Collaborative Sharing Schema (`assignmentPolicy`)

Apps opting into task collaboration configure an `assignmentPolicy` within their Hub `SharingConfig`:

```json
{
  "sharingTable": "taskAssignees",
  "parentIdField": "taskId",
  "userIdField": "userId",
  "parentTable": "todos",
  "childTables": [],
  "assignmentPolicy": {
    "version": 1,
    "hierarchyParentField": "parentTaskId",
    "editableFields": [
      "title", "completed", "deadlineDate", "deadlineTime", "deadlineZone",
      "deadlineOffsetSeconds", "reminderAnchorTime", "deadlineAnchorEpochMs",
      "deadlineOverdueEpochMs", "reminderEnabled", "reminderLeadMinutes",
      "reminderIntervalMinutes", "reminderCount", "scheduleToken"
    ]
  }
}
```

- **Whole-App Cutoff**: Sync requests lacking `collaborationVersion: 1` fail closed with HTTP 426 `COLLABORATION_REQUIRED` before processing mutations or reading content.
- **Single App Guard**: Mutations on opted-in apps run within a MongoDB transaction against an internal guard in `_sync_assignment_fences`, incrementing `accessRevision` on visibility changes.
- **Validation Mode**: Pull requests with `accessChecks` (bounded to <= 100 keys and <= 256 KiB) return `accessResults` and `accessRevision`.
## Schema Version Editing

Admin schema updates use `expectedSchemaVersion` as an optimistic concurrency precondition. Hub versions are positive exact integers, start at 1, and advance only for an effective change. An effective no-op leaves the version unchanged. A stale changed edit conflicts with HTTP 409; the admin UI preserves the draft and presents the latest server schema read-only until the user explicitly discards/reloads. A stale request whose effective content already equals current state may return current authoritative state.

Disposable-local MongoDB/Hub/admin UI smoke is reported passed: ordinary registration; create/pull/delta/update/stale-conflict/delete; unknown JSON retention; schema version 1→2 after pasting nullable `customNotes`; no-op remains 2; stale changed save returns 409 with draft preserved and latest schema readonly. This is not a production migration or authorization.

### Windows Provisioning File Safety

The current schema-provisioning script is for a new ordinary app registration, not conversion of an existing strict app. On Windows the operator must supply an existing private, non-symlink output directory whose protected DACL and owner are limited to the current user, `SYSTEM`, and `Administrators` before the script creates a file. If the precondition fails, the script refuses and does not modify directory permissions. The generated file ACL is set before the registration POST.

## Standard Sync Behavior and Limits

- Room v5 migration migrates tables to canonical identities (`ownerUserId`, `tableName`, `rowId`) with two-stage legacy binding (`ownerBinding = LOCAL_ACCOUNT`) without data loss.
- Each sync round/reconnect performs a full pull from a null checkpoint followed by bounded access checks. A restart discards staged rows from a stale scan generation and begins a fresh scan. Absence from an incomplete scan is not deletion evidence.
- Authoritative access loss purges active base, draft, and outbox payloads locally, writing non-replayable content-free evidence to `access_loss_evidence`. Regrant fetches fresh server state without replaying purged unsent edits.
- Ambiguous legacy intent requires user review. Reapply only supported, explicit changed-field intent—never all known fields as fallback. Users may discard, selectively reapply, or copy the latest server draft as a fresh root.
- Serialized sync bodies are limited to 256 KiB. A known single-record oversize is marked actionable/conflicted and nothing is sent for that record; fitting rows may still be sent, leaving other rows pending.
- The protocol offers desired-state convergence, not strict operation receipts, exact-revision CAS, server-enforced hierarchy, or atomic group writes. Content rows lack permanent tombstones; however, collaborative `taskAssignees` relationship slot tombstones are explicitly exempt from generic cleanup to prevent stale recreate attacks. Only an explicit `(ownerUserId, tableName, rowId, version)` accepted tuple confirms a particular submitted version. Matching pulled state is not proof of acceptance.
- Delta performs push followed by pull; the sequence is not atomic. Do not assume client-side hierarchy rules constrain arbitrary API clients.
## Operator and Deployment Boundary

Source-built standard conversion tooling is distinct from the TabbyRush binary. Parent-reported local operator verification passed 7/7 checks, including concurrency, deadline, and recovery. No production database has been migrated or authorized.

The operator uses an exclusive durable execution claim. After any failed mutating run, recovery requires explicit `maintenance.executionRecovery` evidence for the exact `expectedExecutionId`: `executorStopped: true` plus `executorStopEvidence`, `mongoOperationsDrained: true` plus `mongoDrainEvidence`, and `authorizedActions` including `"recoverExecution"`. Claims do not expire or transfer automatically. Renew/verify the maintenance window before every collection rename and registry CAS. No automatic service restart, external isolation release, or post-publication rollback is performed. Live cutover requires separate explicit approval.

## Historical Strict Integration Record

**Historical record, 2026-10-14 through 2026-10-16; not applicable to current binary or current operational instructions.** Earlier phase documentation described strict-v1 app registration, strict-v1/v2 schema fingerprints, strong bootstrap/push/pull and legacy-receipt routes, exclusive fencing, migration phases, admin panels, and strict CLI/rehearsal scripts. Those descriptions record the historical design and claimed implementation status at that time, not current routes or acceptance. Former strict-specific CLI scripts, rehearsal scripts, and runbooks have been retired.

The strict history does not establish that strict production cutover occurred. Current source uses standard sync, and production status remains unapproved. Historical test tables and gate statements were snapshots from their stated phases; they are not evidence for current native acceptance or release readiness.
