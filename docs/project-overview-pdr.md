# TabbyRush Project Overview & Product Development Requirements

Last Updated: 2026-10-21  
Document Version: 2.1.0  
Application Baseline: `1.0.17` (versionCode `18`)  
Status: Collaborative task assignees and Room v5 delivered (Phases 01–08); Phase 09 harnesses and docs delivered. G0 confirmed; G1 implemented and test-verified (625 reported passes). Current project integration status records Cycle 3 approval with noted findings. G2/G3 disposable runtime acceptance and G4/G5 device/interactive acceptance remain pending; G6 security/release audit is not evidenced. Release hold remains; production migration is unauthorized.
Related Docs: [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Codebase Summary](./codebase-summary.md) · [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md) · [Release Acceptance](./release-acceptance.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [API Contract](./api-contract.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Android Acceptance](./android-acceptance.md) · [Linux Acceptance](./linux-acceptance.md) · [Windows Acceptance](./windows-acceptance.md) · [Project Roadmap](./project-roadmap.md)

---

## 1. Product Overview

TabbyRush is a native Android and desktop task-management application built with Kotlin Multiplatform and Compose. It prioritizes durable offline work, account isolation, transparent sync conflict handling, and platform-appropriate task surfaces. Glean Hub provides account authentication, ordinary app registration/schema metadata, standard synchronization, and separate administrator announcement services. The Hub admin UI is not a TabbyRush web app.

The current client uses standard Hub `push`, `pull`, and `delta` routes. It does not use strict-v1/v2/v3 protocol routes, epochs, or strict operation receipts. Schema metadata changes do not generate native fields, Room migrations, or UI automatically.

## 2. Product Requirements

### 2.1 Functional Requirements

| ID | Requirement | Acceptance intent |
|---|---|---|
| FR-01 | Task lifecycle | Create, view, edit, complete, and delete tasks with local persistence before network dispatch. |
| FR-02 | Offline operation | Retain pending edits and drafts across network loss and application restart. |
| FR-03 | Account isolation | Keep account data and credential state partitioned; fence stale work during logout/account changes. |
| FR-04 | Standard synchronization | Use ordinary Hub app registration and standard push/pull/delta under `/api/v1`; expose actionable conflict and uncertainty states. |
| FR-05 | Unknown-field preservation | Retain raw unknown JSON and merge only fields named by an explicit valid intent; do not drop unrelated server data. |
| FR-06 | Conflict review | Ambiguous legacy intent requires user review. Support discard, explicit supported reapply, and copy-latest-as-new-root without all-field fallback. |
| FR-07 | Full scan and deletion safety | Start each round/reconnect at a null checkpoint; discard stale-generation staging after restart; infer no deletion from an incomplete scan. |
| FR-08 | Task hierarchy | Support root and child tasks with client-side two-level hierarchy behavior, group deletion, and last-child completion semantics. Standard Hub does not enforce hierarchy for arbitrary clients. |
| FR-09 | Deadlines and reminders | Support optional task deadlines and local reminder rules using persisted task state; uncommitted editor drafts do not schedule reminders. |
| FR-10 | Native surfaces | Provide platform task surfaces that use account-scoped data and respect platform permissions/lifecycle. Interactive device behavior requires platform acceptance. |
| FR-11 | Admin schema metadata | Use optimistic `expectedSchemaVersion`; effective changes increment, no-ops do not, stale changed writes do not overwrite newer schema. Version is informational to native features. |
| FR-12 | Announcements | Provide a separate authenticated announcement inbox and generic notification transport, without durable offline announcement content or coupling to task sync. |
| FR-13 | Collaborative task assignees | Keep task content in one creator-owned `todos` row; use one stable owner-scoped `taskAssignees` slot per task; restrict assignees to allowlisted content edits; preserve child-only privacy and full-group completion rules; bind Room v5 legacy rows only after verified account identity; purge revoked payloads without replay or content-bearing evidence. |
### 2.2 Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Security | HTTPS for non-loopback release traffic, route allowlisting, dual-auth sync headers, protected credential custody, secret redaction, and no admin/database credentials in clients. |
| NFR-02 | Reliability | Bounded network operations, no automatic POST replay after auth refresh, account/generation fencing, and recoverable local state for interrupted dispatch. |
| NFR-03 | Data integrity | Explicit Room migrations only; no destructive fallback. Preserve all legacy outbox evidence on migration and do not infer delivery status. |
| NFR-04 | Compatibility | Preserve unknown raw JSON. Standard sync does not promise strict exact-revision CAS receipts, permanent tombstones, server-enforced hierarchy, or atomic group writes. |
| NFR-05 | Performance | Keep disk/network operations off UI-critical paths; bound serialized sync body to 256 KiB before dispatch. |
| NFR-06 | Maintainability | Keep domain and persistence in shared Kotlin Multiplatform modules; isolate platform credential, scheduling, notification, and packaging implementations. |
| NFR-07 | Accessibility | Provide meaningful semantics, scalable layouts, keyboard/screen-reader support and platform-specific accessibility verification; do not treat unit tests as rendered acceptance. |

## 3. Architecture and Constraints

- `shared/` contains shared Compose UI, domain contracts, Room storage, auth, sync coordination, conflict handling, and schedule projection.
- `androidApp/` integrates Android lifecycle, account runtime, WorkManager/alarm/notification services, and Glance task surfaces.
- `desktopApp/` provides JVM desktop lifecycle and platform-specific Linux/Windows vault, reminder, notification, and task-surface providers.
- `TabbyRushDatabase` is Room version 5 with explicit migrations 1→2→3→4→5. Room 3→4 archives legacy outbox values in `legacy_sync_evidence`. Room 4→5 establishes canonical identities (`ownerUserId`, `tableName`, `rowId`), access rights, and two-stage legacy binding (`ownerBinding = LOCAL_ACCOUNT`) resolving from verified `AccountIdentity`.
- Authoritative access loss enforces a privacy-first purge of active base, draft, and outbox payloads, retaining only non-replayable content-free evidence in `access_loss_evidence`.
- Standard push response accepts only explicit `(ownerUserId, tableName, rowId, version)` acknowledgement as operation confirmation. Matching pulled state is convergence, not receipt proof; delta is push then pull and not atomic.
- A 256 KiB serialized UTF-8 request body limit is enforced before freeze. A known single oversized record is actionable/conflicted and not sent; fitting records may be dispatched while others remain pending.
- `schemaVersion` is informational Hub concurrency metadata; collaborative task apps require `collaborationVersion: 1`. Requests lacking this capability on opted-in apps fail closed with HTTP 426 `COLLABORATION_REQUIRED`.
- Strict runbooks and strict protocol phase documents are dated archives, not current implementation or deployment guidance.

## 4. Current Evidence and Acceptance Status

| Scope | Reported result | Remaining limit |
|---|---|---|
| Disposable Hub API/browser smoke | Ordinary provisioning, standard create/pull/delta/update/stale-conflict/delete, unknown JSON retention, and schema editor concurrency passed on disposable local services. Nullable `customNotes` advanced schemaVersion 1→2; no-op stayed 2; stale changed save returned 409 and retained the draft. | Not production migration, authorization, or native acceptance. |
| Standalone Windows runtime smoke | Passed through strict Gradle JavaExec against disposable loopback services: Windows DPAPI, real HTTP auth, Room offline restart, two independent stores using actual coordinator push/pull/delta, schemaVersion 2, unknown nested JSON and `customNotes` preserved through native edit, parent/child sync, and group deletion. | Not interactive UI, connected Android, or complete platform acceptance. |
| Collaborative assignee suites | Cycle 3 reports 625 passing tests across seven suites: shared JVM 324, Desktop 145, Android 60, Hub sync 28, sync engine 6, Hub admin 50, and provisioning scripts 12. | Automated tests do not establish runtime/device acceptance; Cycle 3 review notes remain documented. |
| Conversion operator | Parent reports 7/7 disposable-Mongo checks passed, including concurrency, deadline, and recovery. | No live database migration or authorization. |
| Runtime/device gates and release | G0 policy confirmed; G1 implementation and test verification reported complete. G2/G3 disposable runtime acceptance and G4/G5 device/interactive acceptance remain pending; G6 security/release audit is not evidenced. | Release remains blocked; production migration and operational policy enablement are unauthorized. |

## 5. Operator Safety Requirements

Any failed mutating conversion run leaves an exclusive durable execution claim; there is no automatic expiry or takeover. Recovery requires explicit `maintenance.executionRecovery` for the exact `expectedExecutionId`, `executorStopped: true` with `executorStopEvidence`, `mongoOperationsDrained: true` with `mongoDrainEvidence`, and `authorizedActions` containing `recoverExecution`. Renew/verify the maintenance window before each collection rename and registry compare-and-swap. The operator does not automatically restart services, release external isolation, or roll back after publication. Live cutover requires separate explicit approval, verified backup/restore scope, and an isolation plan.

## 6. Historical Requirements Record

Older PDR sections specified strict-v1/v2/v3 epochs, schema fingerprints, operation receipts, and strict migration flows. Those requirements described earlier design/release snapshots from 2026-10-14 through 2026-10-20 and are superseded for the current binary by the standard-sync requirements above. Archived strict runbooks preserve historical claims only; do not use them as current procedures.
