# TabbyRush Standard Sync & Local State Acceptance

Date: 2026-10-20  
Status: Disposable-local API/browser and standalone Windows runtime smoke reported passed. Native UI/connected-device acceptance is not passed; production migration is unauthorized.  
Related Docs: [Local Storage & Sync](./local-storage-and-sync.md) · [API Contract](./api-contract.md) · [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Release Acceptance](./release-acceptance.md)

---

## 1. Current Acceptance Criteria

| ID | Area | Required behavior |
|---|---|---|
| SYNC-01 | Room migration | Explicit Room 1→2→3→4 chain; no destructive fallback. Room 3→4 preserves all legacy outbox SQLite values in `legacy_sync_evidence`, including FLOAT bits and BLOB bytes. Migration does not confirm or replay old work. |
| SYNC-02 | Account partition | Data, credentials, callbacks and scheduled work remain bound to the active account key/generation. Stale callbacks cannot mutate a switched/closed account. |
| SYNC-03 | Local mutation | Persist user edits before network dispatch. Preserve newer drafts when an older submitted version is acknowledged. |
| SYNC-04 | Standard route/auth | Use `/api/v1/sync/{app_id}/{push,pull,delta}` with Bearer JWT, `X-API-Key`, and `X-App-Id`. Do not use strict bootstrap, epoch, digest, or legacy-receipt routes. |
| SYNC-05 | Explicit acknowledgement | Clear only an explicit accepted `(tableName,rowId,version)` tuple. Matching pulled state is convergence, not operation-receipt proof. |
| SYNC-06 | Unknown JSON | Preserve raw unknown JSON through known-field edits; apply only keys selected by a valid explicit intent mask. |
| SYNC-07 | Legacy intent | Unknown/ambiguous legacy intent remains for review. Never fall back to all-known-field replay. Support discard, explicit supported reapply, and copy-latest as a fresh root. |
| SYNC-08 | Full scan | Start a full scan from null each sync round/reconnect. Discard staged rows from stale generation after restart. An incomplete scan does not prove deletion by absence. |
| SYNC-09 | Body budget | Check serialized UTF-8 bytes before freeze against 256 KiB. A known single oversized record is actionable/conflicted and unsent, not uncertain delivery; fitting records may proceed. |
| SYNC-10 | Delta semantics | Delta sequences push then pull. Do not represent it as atomic or as server-side group write. |
| SYNC-11 | Hub schema metadata | `schemaVersion` is informational and does not gate native features or generate UI/Room code. Effective no-op does not increment; stale changed edits do not overwrite newer schema. |
| SYNC-12 | Standard protocol limits | Do not promise strict exact-revision CAS receipts, server-enforced hierarchy, permanent tombstones, or transactional group writes. |

## 2. Current Evidence

| Scope | Reported result | What it does not establish |
|---|---|---|
| Disposable Hub API/browser smoke | Ordinary private provisioning; standard create/pull/delta/update/stale-conflict/delete; unknown JSON retention; admin nullable `customNotes` schema update 1→2; no-op remained 2; stale changed save returned 409 with draft preserved and latest schema read-only. | Production migration, production authorization, or native acceptance. |
| Standalone Windows runtime | Passed via strict Gradle JavaExec against disposable loopback services: Windows DPAPI, real HTTP auth, Room offline restart, two independent stores using actual coordinator push/pull/delta, schemaVersion 2, unknown nested JSON and `customNotes` retained through native edit, parent/child sync and group deletion. | Interactive UI, connected Android, full desktop surface/accessibility/device acceptance. |
| Offline verification | Parent reports current offline Gradle/package suites passed. | Does not replace interactive platform acceptance. |
| Native UI and connected device | UI provider approval timed out twice; connected-device acceptance was not run. | Native acceptance is not passed. |
| Production services | No live production migration is authorized or reported as performed. | No permission for a mutating production operation. |

## 3. Verification Locations

Current source tests are organized under `shared/src/commonTest/` and `shared/src/jvmTest/`, including sync DTO/client, account coordinator, outbox reconciliation, task JSON merge, staged scan, account leave, and Room migration tests. Android instrumented tests live under `shared/src/androidInstrumentedTest/` and `androidApp/src/androidTest/`. Desktop tests are under `desktopApp/src/test/`, `linuxTest/`, and `windowsTest/`. Test source presence or a package build is not acceptance evidence for a connected device or interactive native surface.

## 4. Historical Strict Sync Record

**Historical snapshot, 2026-10-14 through 2026-10-20; not current acceptance.** Earlier Phase 05–12 material described Room v1/v2/v3, strict operation digests, exact revision acknowledgements, strict-v2/v3 migration, epochs, and strict CLI rehearsal. Those claims describe earlier phase reports. Strict-specific scripts cited there have been removed; the strict-v2 and strict-v3 runbooks are archived and must not be used as current procedures.
