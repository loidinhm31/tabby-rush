# TabbyRush Hub Compatibility & Acceptance

Date: 2026-10-21  
Status: Collaborative task assignees (version 1) and Room v5 implemented (Phases 01–08); dedicated harnesses prepared in Phase 09. G0 policy validation confirmed 2026-10-21; G1–G6 runtime verification pending Main integration. Release remains blocked; production migration unauthorized.  
Related Docs: [Hub Schema Integration](./hub-schema-integration.md) · [API Contract](./api-contract.md) · [System Architecture](./system-architecture.md) · [Deployment Guide](./deployment-guide.md) · [Standard Sync Design](./standard-sync-cutover-design.md) · [Integrated Acceptance Evidence](../plans/261021-1552-tabbyrush-task-assignees/reports/04-integrated-acceptance-evidence.md)

## Current Compatibility Contract

TabbyRush's current binary uses ordinary Hub app registration and standard sync routes under `/api/v1/sync/{app_id}/{push,pull,delta}`. Authentication, app, and schema routes also use `/api/v1`. Standard sync uses `Authorization: Bearer`, `X-API-Key`, and `X-App-Id`.

Hub schema validation supports registered table/column constraints while permitting unknown JSON keys. TabbyRush preserves raw unknown fields and merges only explicitly selected local intent. Hub `schemaVersion` is informational metadata/concurrency state; it does not turn on native features or generate Room/Kotlin/UI structures.

## Accepted Contract Behaviors

| Area | Required behavior |
|---|---|
| Standard sync | Create, pull, delta, update, stale conflict, and delete use ordinary routes; delta is sequential push then pull, not atomic. |
| Collaborative sync | Multi-user assignment via creator-owned `todos` and stable `taskAssignees` slot (`rowId == taskId`). Requires `collaborationVersion: 1`. |
| Capability cutoff | Missing `collaborationVersion: 1` on opted-in app returns HTTP 426 `COLLABORATION_REQUIRED` before processing mutations or reads. |
| Authoritative guard | App-wide transaction guard `_sync_assignment_fences` serializes opted-in mutations and advances `accessRevision`. |
| Acknowledgement | Clear only an explicit accepted `(ownerUserId, tableName, rowId, version)` tuple. Pulled matching state is desired-state convergence, not operation receipt proof. |
| Unknown data | Preserve unknown raw JSON fields through known-field intent merges. |
| Access revalidation | Bounded `accessChecks` (<= 100 items, <= 256 KiB) and terminal `[]` check must agree on access revision E before Room promotion. |
| Privacy purge | Authoritative access loss purges active base, draft, and outbox payloads; records content-free evidence in `access_loss_evidence`; no auto-replay on regrant. |
| Schema concurrency | Effective schema change increments `schemaVersion`; no-op does not. Stale changed update returns conflict without overwriting newer content. |
| Admin draft | On stale changed save, retain draft and show latest server schema read-only; replacement requires explicit discard/reload. |
| Legacy local data | Room v5 migrates tables to canonical identities; two-stage binding preserves legacy rows (`ownerBinding = LOCAL_ACCOUNT`) until verified `AccountIdentity` binds them. |
| Full scan | Start each round/reconnect at a null checkpoint; discard stale staged generation on restart; do not infer deletion from incomplete scan. |
| Oversized record | Check UTF-8 body bytes before freeze. A known single record over 256 KiB is actionable/conflicted and unsent, not an uncertain delivery. |
| Schema additions | Admin schema changes do not autogenerate native UI, Kotlin fields, or Room migrations. |
These guarantees are deliberately weaker than retired strict sync: no strict operation receipts or exact-revision protocol CAS, server-enforced hierarchy, permanent tombstones, or atomic group writes.

## Evidence and Limits

Disposable local services (MongoDB 27191, Hub 3100, admin UI 3102) were used for API/browser smoke. Reported results:

- Ordinary app provisioning with private file permissions.
- Standard create, pull, delta, update, stale conflict, delete, and unknown JSON retention.
- Admin UI paste of nullable `customNotes`: `schemaVersion` advanced 1→2; no-op remained 2.
- A stale schema edit returned 409, preserved the draft, and kept latest server schema read-only.

A separate standalone Windows runtime smoke was previously reported by the parent as passed through a strict Gradle JavaExec entry point against disposable loopback services.

For collaborative task assignees, dedicated verification harnesses have been delivered in Phase 09:
- `node scripts/task-assignees-live-smoke.mjs`: validates 426 capability cutoff, canonical single-row storage, assignment CAS, child-only privacy, hostile rejections, and bounded accessChecks against disposable Hub.
- `./gradlew :desktopApp:runTaskAssigneesRuntimeSmoke`: standalone JavaExec runner exercising Room v5 migration, two-stage binding, independent store coordinators, exact acknowledgements, opaque JSON retention, and revocation purge with non-replayable content-free evidence.

Both harnesses are prepared and fail closed if disposable loopback prerequisites are missing. In accordance with project delegation rules, G1–G6 execution is **NOT RUN / pending** (reserved for Main integration verification). Policy validation (G0) was confirmed 2026-10-21. Production has not been migrated or authorized. Release remains blocked.
## Cutover Safety Preconditions

The conversion operator uses a durable exclusive claim. After **any** failed mutating run, an operator must explicitly recover using `maintenance.executionRecovery` with the exact `expectedExecutionId`, `executorStopped: true` and `executorStopEvidence`, `mongoOperationsDrained: true` and `mongoDrainEvidence`, and `authorizedActions` containing `"recoverExecution"`. There is no claim expiry or automatic takeover.

Renew and verify the maintenance window before each collection rename and registry CAS. Keep all writers/jobs isolated and a verified backup available. The operator does not automatically restart services, release external isolation, or roll back after publication. Live cutover requires separate explicit approval. These requirements do not authorize production action.

## Historical Strict Acceptance Notes

Strict-v1/v2/v3 route behavior, digest-vector results, test counts, and release-gate statements in earlier phase reports are historical snapshots, not current TabbyRush protocol acceptance. The current binary does not use strict routes, and former strict-specific scripts and runbooks have been retired.
