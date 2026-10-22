# TabbyRush Codebase Summary

Generated: 2026-10-22
Source: Fresh repository compaction generated with `repomix --quiet -o ./repomix-output.xml`, plus inspection of current implementation sources.
Compaction snapshot: 118,250 lines and 8,437,178 bytes; packed-file and token totals were not recorded. Ignored/default-filtered and binary files are absent. Repomix warns the artifact may contain sensitive information; no exhaustive secret audit was performed, so do not distribute it without a targeted scan.

## 1. Project at a Glance

TabbyRush is a Kotlin Multiplatform native task application for Android and JVM desktop, using shared Compose UI and Room/SQLite offline persistence. Glean Hub supplies account auth, ordinary app/schema registry, standard task sync, and a separate administrator-announcement service. The Hub admin UI is not a TabbyRush web client.

The current binary uses ordinary app registration and standard push/pull/delta under `/api/v1/sync/{app_id}/`. It does not use the historical strict-v1/v2/v3 sync protocol. `schemaVersion` is informational Hub schema concurrency metadata; it does not generate native fields, UI, features, or Room migrations.

## 2. Repository Structure

| Path | Responsibility |
|---|---|
| `shared/` | Shared Kotlin domain contracts, Compose UI/theme, authentication, Room persistence, standard sync, conflict review, deadline/reminder logic, and announcement inbox. |
| `androidApp/` | Android application/account runtime, WorkManager sync, alarms/notifications, Glance task widget, remote announcement notifications, and Android tests. |
| `desktopApp/` | Compose desktop application, target-specific Linux/Windows credential vaults and notification providers, scheduler, sticky task surfaces, and package configuration. |
| `scripts/` | Android/headless host tools, ordinary Hub provisioning, smoke/diagnostic scripts, security checks, versioning, and platform packaging. |
| `integration/` | Hub schema inputs, announcement contract/vectors, and historical strict protocol vector/schema artifacts. |
| `docs/` | Architecture, API, standards, product requirements, platform acceptance, deployment, security, and archived strict runbooks. |
| `plans/` | Dated implementation plans, phase records, reviews, QA and verification reports. |
| `.github/workflows/` | Pull-request test and tagged release automation. |
| `gradle/` | Wrapper, version catalog, dependency locks/verification metadata. |

## 3. Shared Kotlin Core

The compaction shows `shared/src/commonMain/kotlin/dev/tabbyrush/shared/` organized by responsibility:

- `contract/`: account identity/key, task models and policies, hierarchy/deadline projection, sync/error/auth interfaces, scheduler/surface contracts, and announcement contracts.
- `auth/`: Hub auth DTOs/client/session, HTTP route policy/client, and single-flight token refresh.
- `data/`: Room entities/DAO/database, explicit migrations, account store, account-local settings, reminder ledger, and legacy evidence archive.
- `sync/`: standard Hub DTO/client, account coordinator/leave protocol, outbox reconciliation, JSON merge, scan staging, legacy-intent conversion, reminder gates/scheduling.
- `remote/`: announcement inbox cache/client/controller and authenticated streaming client.
- `ui/` and `theme/`: shared account/task/conflict/deadline/announcement Compose UI and design tokens.

The shared test tree includes contract, auth, data, remote, sync, UI, and JVM SQLite suites; Android instrumented tests include credential-vault and account-store coverage.

### Collaborative Task Assignment Components

- `contract/`: `TaskRef(ownerUserId, rowId)`, `CanonicalRecordRef(ownerUserId, tableName, rowId)`, assignment intents, access capabilities, and hierarchy-coverage contracts. App ID remains sync/request context rather than a field on these Kotlin identities.
- `data/`: `RoomAccountStore` and Room v5 entities persist owner-qualified task state, assignment-slot intent, staged access checks, access rights, and content-free revocation evidence. Legacy rows remain local-only until verified account binding.
- `sync/`: `AccountSyncCoordinator`, `HubSyncClient`, and `StagedFullScan` perform collaboration-capability negotiation, full-scan/access-check reconciliation, exact owner-qualified acknowledgements, and access-generation fencing.
- `ui/`, `androidApp/`, and `desktopApp/`: shared assignee workflows and platform task surfaces use canonical task references and permission state; Android Glance/actions and desktop sticky/actions re-resolve access before mutation.

## 4. Local Data and Migration

`TabbyRushDatabase` is Room version 5 with explicit 1→2, 2→3, 3→4, and 4→5 migrations. Destructive fallback is prohibited. Current tables are `server_base`, `outbox`, `editor_drafts`, `scan_staging`, `access_check_staging`, `task_access`, `access_loss_evidence`, `account_sync_meta`, `account_local_settings`, `reminder_delivery_ledger`, and `legacy_sync_evidence`.

Room 4→5 migrates local storage to canonical identities (`ownerUserId`, `tableName`, `rowId`), access tracking, and relationship state. Two-stage binding preserves legacy rows as `ownerBinding = LOCAL_ACCOUNT` and nullable `ownerUserId` until store initialization verifies `AccountIdentity` matches `AccountKey.create(origin, userId)`. Unresolved legacy rows survive upgrade and display offline, but remain strictly local-only and cannot be dispatched to sync endpoints.

Room 3→4 losslessly archives legacy outbox SQLite values before rebuilding the active outbox. FLOAT values are represented by their bit pattern and BLOBs by exact bytes; unsupported storage types stop migration rather than dropping evidence. The archive is for review, not sync transport, and migration does not imply that a legacy operation was accepted or unsent.

Authoritative access loss immediately purges active `server_base`, `editor_drafts`, and pending `outbox` records from active storage, writing non-replayable content-free evidence to `access_loss_evidence` (`ownerUserId`, `tableName`, `rowId`, `opId`, `baseVersion`, `submittedVersion`, `occurredEpochMs`, `reason = REVOCATION`). Zero task title, deadline, description, or payload content is retained.

Account data is partitioned by an origin/user-derived account key and platform-specific account files. Credential implementations use Android Keystore, Linux Secret Service, and user-scoped Windows DPAPI.

## 5. Current Standard Sync

- Auth routes and sync routes use `/api/v1`; sync uses Bearer JWT plus `X-API-Key` and `X-App-Id`. Collaborative task apps require `collaborationVersion: 1`.
- Push records include table/row identity, optional `ownerUserId` (mandatory in collaborative mode), raw JSON, record version, deletion state, and `assumedServerVersion`; pull pagination uses composite `{updatedAt,id}` checkpoint.
- Collaborative task content stays in the creator-owned `todos` row. The `taskAssignees` relationship uses one stable slot per task (`rowId == data.id == taskId`); unassignment retains a tombstone to prevent stale recreation.
- `schemaVersion` in sync responses is informational. For collaborative apps, requests lacking `collaborationVersion: 1` fail closed with HTTP 426 `COLLABORATION_REQUIRED`.
- Raw unknown JSON is preserved; local updates merge only keys selected by explicit valid intent masks. Assignees are restricted to the configured `editableFields` allowlist.
- Every sync round/reconnect begins a full scan from null followed by bounded access checks (`accessChecks`, <= 100 items, <= 256 KiB) agreeing on a consistent `accessRevision`. Restart discards stale scan-generation staging. An incomplete scan never proves deletion by absence.
- Only exact accepted `(ownerUserId, tableName, rowId, version)` tuples confirm a submitted version. Matching pulled state can be convergence, not operation receipt proof.
- Delta sequences push then pull and is not atomic. In collaborative mode, an internal app guard in `_sync_assignment_fences` serializes writes across the replica set.
- The serialized UTF-8 body limit is 256 KiB before freeze. A known single-record oversize is marked actionable/conflicted and unsent, not an uncertain network result; fitting records may still be dispatched.
- Ambiguous legacy intent requires review. Never fall back to all-known-field replay; explicit supported reapply, discard, or copy-latest-as-new-root are review paths.

## 6. Platform Modules

### Android

The compaction contains account runtime and session wiring, Room account files/builders, WorkManager sync scheduling, alarms and notification adapters, task surfaces, Glance widget receivers/updaters, remote notification installation, and Android tests. Interactive launcher/notification/accessibility/Doze/device behavior remains distinct from automated tests and package assembly.

### Desktop

The JVM desktop module uses service-provider dispatch for credential and reminder adapters. Linux-specific sources include Secret Service and D-Bus integrations; Windows-specific sources include DPAPI and notification/provider code. Separate Linux/Windows lockfiles and package smoke scripts protect target isolation. A standalone Windows runtime smoke was separately reported passed against disposable loopback services; this does not establish interactive UI or connected-Android acceptance.

## 7. Hub, Integration, and Tooling

Current repository scripts include `hub-schema-provision.mjs`, `hub-live-smoke.mjs`, `admin-announcements-smoke.mjs`, `release-security-check.mjs`, `debug-fault-proxy.mjs`, versioning utilities, Android/headless scripts, and Linux/Windows packaging tools. Strict-v2/v3 upgrade, rehearsal, and recovery scripts referenced by older documents have been removed; those runbooks are archives only.

The ordinary `hub-schema-provision.mjs` registers a new app and does not convert an existing strict registration. On Windows its output must be inside an existing private, non-symlink directory with protected DACL/owner limited to the current user, `SYSTEM`, and `Administrators`; the script refuses otherwise without changing directory permissions and applies/verifies the file ACL before registration POST.

`integration/announcements/` contains contract and vector fixtures. `integration/glean-hub/` contains the current `tabbyrush-schema.json` input plus historical strict schemas/vectors; strict artifacts do not imply current binary protocol use.

## 8. Current Verification Boundary

Reported evidence includes disposable-local Hub API/browser smoke for standard create/pull/delta/update/stale-conflict/delete, unknown JSON retention, schema-version concurrency, and 7/7 disposable-Mongo operator checks. These are distinct from production migration or authorization.

A separate standalone Windows standard-sync runtime smoke passed via strict Gradle JavaExec against disposable loopback services. It exercised Windows DPAPI, real HTTP auth, Room offline restart, two independent stores using actual coordinator push/pull/delta, schemaVersion 2, unknown nested JSON and `customNotes` through native edit, and parent/child sync/group deletion. It did not establish interactive UI or connected-Android acceptance.

Broad G1 QA reports 625 passing tests across seven suites: shared JVM 324, Desktop 145, Android 60, Hub sync 28, sync engine 6, Hub admin 50, and provisioning scripts 12 ([QA report](../plans/reports/qa-261021-2225-critical-fix-regression-tests.md)). A distinct 2026-10-22 selected-suite QA run reports 390/390 (shared JVM 329, Hub admin 51, task-assignment integration 6, schema-version integration 4); these scopes are not additive ([QA report](../plans/reports/qa-261022-1521-selected-test-suite-results.md)). The final targeted Cycle 3 review scored 9.5/10 with 0 critical findings and 3 warnings: permissive desktop sticky access fallback, N+1 membership queries during access checks, and missing replica-set integration coverage for concurrent insert-collision recovery ([review report](../plans/reports/code-review-261022-1649-collaborative-assignees-cycle3.md)). The review is not runtime/release approval. QA flags a disposable Mongo test credential in command text; rotation is not evidenced, so do not reuse it. G2/G3 disposable runtime acceptance and G4/G5 device/interactive acceptance remain pending; G6 security/release audit is not evidenced. Release remains blocked; production migration is unauthorized.

## 9. Key Documentation

- [Project Overview & PDR](./project-overview-pdr.md)
- [System Architecture](./system-architecture.md)
- [Code Standards](./code-standards.md)
- [Local Storage & Standard Sync](./local-storage-and-sync.md)
- [API Contract](./api-contract.md)
- [Hub Schema Integration](./hub-schema-integration.md)
- [Deployment Guide](./deployment-guide.md)
- [Security & Recovery](./security-and-recovery.md)
- [Release Acceptance](./release-acceptance.md)
- [Standard Sync Cutover Runbook](./standard-sync-cutover-runbook.md)
