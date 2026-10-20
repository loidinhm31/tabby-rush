# Standard Sync & Schema Version — Current Implementation

Date: 2026-10-20. Status: **Implemented in source; disposable-local smoke exercised; no live production cutover authorized. Native acceptance and release remain blocked.**
Plan: [Option B](../plans/261020-1717-standard-sync-schema-version/plan.md).
Current architecture: [system architecture](system-architecture.md) · [API contract](api-contract.md)
Operator sequence: [standard-sync cutover runbook](standard-sync-cutover-runbook.md).

## Current Runtime

TabbyRush remains a native Android/Desktop application with Room offline storage. Its current client uses Glean Hub ordinary app registration and standard `POST /api/v1/sync/{app_id}/{push,pull,delta}` routes, with dual-auth headers. Hub admin is management tooling, not a TabbyRush web app. The current binary does not speak strict-v1/v2/v3.

Disposable-local MongoDB/Hub/admin UI smoke passed for ordinary provisioning and private file ACL handling, standard create/pull/delta/update/stale-conflict/delete, and preservation of unknown JSON fields. The admin UI accepted a pasted nullable `customNotes` schema update from version 1 to 2; an effective no-op stayed at 2, and a stale changed save returned 409 while preserving the draft and showing latest schema read-only. These results do not authorize or establish a production migration.

A separate standalone Windows runtime smoke passed via a strict Gradle JavaExec entry point against disposable loopback services. It exercised native Windows DPAPI, real HTTP auth, Room offline restart, two independent stores using the actual coordinator's push/pull/delta, `schemaVersion` 2, unknown nested JSON and nullable `customNotes` preserved through a native edit, and parent/child sync plus group deletion. This is runtime-harness evidence only, not interactive native UI or connected-Android acceptance.

## Schema Registry Contract

- Hub `schemaVersion` is a positive, exact JSON integer concurrency counter, initially 1. It is metadata, not a native feature gate or migration trigger.
- Schema PUT requires `expectedSchemaVersion`. An effective change conditionally commits and increments once; an effective no-op does not increment. A stale changed request conflicts; it must not overwrite newer content or retry blindly. A stale request whose effective schema is already current may return the authoritative state.
- Unknown schema fields are permitted and retained by standard sync. Schema changes do not generate Kotlin properties, Room columns/migrations, or native UI. Required-field or semantic changes still need compatible application code and explicit migration/release work.
- On Windows, schema provisioning requires an existing private, non-symlink output directory with protected DACL/owner limited to current user, `SYSTEM`, and `Administrators`, before creating a file. The script refuses unsafe directories and does not change their permissions. The generated file's DACL is set before the registration POST.

## Native Persistence & Reconciliation

- Room v4's migration losslessly archives every legacy outbox SQLite value before reconciliation, preserving floating-point bit patterns and BLOB bytes. It does not automatically prove an old operation was unsent or accepted.
- The client stores `hubSchemaVersion` as informational metadata, independently from Room version and record versions. Unknown JSON fields survive through raw JSON storage and intent-only merges.
- Each standard sync round/reconnect begins a full pull scan from a null checkpoint because no trustworthy server retention horizon is assumed. A restart invalidates staged rows from an older scan generation; the new scan begins fresh. Incomplete scans never establish deletion by absence.
- Ambiguous legacy intent requires user review. Reapply only an explicit, supported changed-field mask; unknown intent cannot be reapplied as “all known fields.” Discard or copy the latest local draft/projection as a fresh root instead. No field-selection editor is introduced.
- The client enforces a 256 KiB serialized UTF-8 request-body limit before freezing. If a known record alone exceeds the budget, it is marked actionable/conflicted and nothing is sent for that record; this is not an uncertain delivery. Fitting records may proceed, with other records pending.

## Protocol Guarantees and Boundaries

Standard sync is desired-state synchronization, not strict operation receipts. It has no strict exact-revision CAS receipt, server-enforced TabbyRush hierarchy, permanent tombstones, or transactional group writes. Only an explicit accepted `(tableName,rowId,version)` tuple confirms an operation. Matching version/full JSON/deletion after a pull is convergence, not proof the submitted operation was accepted. Delta pushes then pulls sequentially and is not atomic.

Local hierarchy, reminder, and task behavior remains client-side. Do not claim that arbitrary API clients receive strict server-side hierarchy enforcement. Hub schema metadata does not itself activate native features.

## Operator and Cutover Safety

The standard-sync conversion operator is source-built tooling, not a command exposed by the TabbyRush binary. Parent-reported disposable MongoDB execution passed 7/7 operator checks, including concurrency, deadline, and recovery coverage. This is test evidence, not permission to run against any live database.

Any failed mutating run leaves an exclusive durable claim requiring explicit recovery. There is no automatic expiry or takeover. The protected maintenance manifest's root `executionRecovery` object must provide exact `expectedExecutionId`, `executorStopped: true` with `executorStopEvidence`, and `mongoOperationsDrained: true` with `mongoDrainEvidence`; `authorizedActions` must contain `"recoverExecution"`. The operator rechecks its immutable authorized deadline immediately before each collection rename or registry compare-and-swap. An expired run needs renewed authorization and exact-token recovery, not an in-process window extension. It does not restart services, release external isolation, or roll back after publication.

Live cutover requires separate explicit approval, verified backup/restore scope, all Hub writers/jobs quiesced or isolated, and a renewed maintenance window. A changed registry publication is not a multi-step transaction. Pre-publication restoration is only under separate authorization and verified isolation; after publication, do not restore an older snapshot over new standard writes.

## Acceptance Status

API/browser smoke on disposable local services, the standalone Windows runtime smoke, parent-reported offline Gradle/package suites, and the operator's 7/7 disposable-Mongo checks are reported passed. Native UI provider approval timed out twice and connected-device acceptance was not run; native acceptance is therefore not passed. Production has not been migrated or authorized. Release remains blocked; this document is not a release approval.
