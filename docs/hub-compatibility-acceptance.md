# TabbyRush Hub Compatibility & Acceptance

Date: 2026-10-20  
Status: Disposable-local API/browser smoke passed; production migration unauthorized; native acceptance not passed and release remains blocked.  
Related Docs: [Hub Schema Integration](./hub-schema-integration.md) · [API Contract](./api-contract.md) · [System Architecture](./system-architecture.md) · [Deployment Guide](./deployment-guide.md) · [Standard Sync Design](./standard-sync-cutover-design.md)

## Current Compatibility Contract

TabbyRush's current binary uses ordinary Hub app registration and standard sync routes under `/api/v1/sync/{app_id}/{push,pull,delta}`. Authentication, app, and schema routes also use `/api/v1`. Standard sync uses `Authorization: Bearer`, `X-API-Key`, and `X-App-Id`.

Hub schema validation supports registered table/column constraints while permitting unknown JSON keys. TabbyRush preserves raw unknown fields and merges only explicitly selected local intent. Hub `schemaVersion` is informational metadata/concurrency state; it does not turn on native features or generate Room/Kotlin/UI structures.

## Accepted Contract Behaviors

| Area | Required behavior |
|---|---|
| Standard sync | Create, pull, delta, update, stale conflict, and delete use ordinary routes; delta is sequential push then pull, not atomic. |
| Acknowledgement | Clear only an explicit accepted `(tableName,rowId,version)` tuple. Pulled matching state is desired-state convergence, not operation receipt proof. |
| Unknown data | Preserve unknown raw JSON fields through known-field intent merges. |
| Schema concurrency | Effective schema change increments `schemaVersion`; no-op does not. Stale changed update returns conflict without overwriting newer content. |
| Admin draft | On stale changed save, retain draft and show latest server schema read-only; replacement requires explicit discard/reload. |
| Legacy local data | Room v4 archives every legacy outbox value losslessly. Unclear legacy intent requires review; never reapply all known fields as fallback. |
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

A separate standalone Windows runtime smoke is reported by the parent as passed through a strict Gradle JavaExec entry point against disposable loopback services. It exercised Windows DPAPI, real HTTP auth, Room offline restart, two independent stores using the actual coordinator's push/pull/delta, `schemaVersion` 2, unknown nested JSON and nullable `customNotes` preservation through a native edit, and parent/child sync/group deletion. See the [parent's current verification report](../plans/261020-1717-standard-sync-schema-version/reports/verification-final.md).

Parent reports offline Gradle/package suites and 7/7 disposable-Mongo operator checks passed, including concurrency, deadline, and recovery. The Windows runtime harness is not interactive UI acceptance or connected-Android proof. UI provider approval timed out twice; connected-device verification was not run. Production has not been migrated or authorized. Do not label native acceptance passed or release unblocked.

## Cutover Safety Preconditions

The conversion operator uses a durable exclusive claim. After **any** failed mutating run, an operator must explicitly recover using `maintenance.executionRecovery` with the exact `expectedExecutionId`, `executorStopped: true` and `executorStopEvidence`, `mongoOperationsDrained: true` and `mongoDrainEvidence`, and `authorizedActions` containing `"recoverExecution"`. There is no claim expiry or automatic takeover.

Renew and verify the maintenance window before each collection rename and registry CAS. Keep all writers/jobs isolated and a verified backup available. The operator does not automatically restart services, release external isolation, or roll back after publication. Live cutover requires separate explicit approval. These requirements do not authorize production action.

## Historical Strict Acceptance Notes

Strict-v1/v2/v3 route behavior, digest-vector results, test counts, and release-gate statements in earlier phase reports are historical snapshots, not current TabbyRush protocol acceptance. The current binary does not use strict routes, and former strict-specific scripts and runbooks have been retired.
