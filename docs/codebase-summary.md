# TabbyRush Codebase Summary

Generated: 2026-10-20  
Source: repository compaction in `repomix-output.xml` plus inspection of current implementation sources.  
Compaction scope: 694 files, approximately 1.74 million tokens / 7.63 million characters; six security exclusions and ignored/binary files are not represented. The compaction is an analysis snapshot, not a substitute for source files.

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

## 4. Local Data and Migration

`TabbyRushDatabase` is Room version 4 with explicit 1→2, 2→3, and 3→4 migrations. Destructive fallback is prohibited. Current tables are `server_base`, `outbox`, `editor_drafts`, `scan_staging`, `account_sync_meta`, `account_local_settings`, `reminder_delivery_ledger`, and `legacy_sync_evidence`.

Room 3→4 archives old outbox SQLite values before rebuilding the active outbox. FLOAT values are represented by their bit pattern and BLOBs by exact bytes; unsupported storage types stop migration rather than dropping evidence. The archive is for review, not sync transport, and migration does not imply that a legacy operation was accepted or unsent.

Account data is partitioned by an origin/user-derived account key and platform-specific account files. Credential implementations use Android Keystore, Linux Secret Service, and user-scoped Windows DPAPI.

## 5. Current Standard Sync

- Auth routes and sync routes use `/api/v1`; sync uses Bearer JWT plus `X-API-Key` and `X-App-Id`.
- Push records include table/row identity, raw JSON, record version, deletion state, and `assumedServerVersion`; pull pagination uses composite `{updatedAt,id}` checkpoint.
- `schemaVersion` in sync responses is informational. Admin schema updates use `expectedSchemaVersion`; no-op does not increment, and stale changed writes conflict.
- Raw unknown JSON is preserved; local updates merge only keys selected by explicit valid intent masks.
- Every sync round/reconnect begins a full scan from null. Restart discards stale scan-generation staging. An incomplete scan never proves deletion by absence.
- Only exact accepted `(tableName,rowId,version)` tuples confirm a submitted version. Matching pulled state can be convergence, not operation receipt proof.
- Delta sequences push then pull and is not atomic. Standard sync does not provide strict exact-revision CAS receipts, server-enforced hierarchy, permanent tombstones, or transactionally atomic group writes.
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

`integration/announcements/` contains contract and vector fixtures. `integration/glean-hub/` contains standard schema input plus historical strict schemas/vectors; strict artifacts do not imply current binary protocol use.

## 8. Current Verification Boundary

Reported evidence includes offline Gradle/package suites; disposable-local Hub API/browser smoke for ordinary provisioning, standard create/pull/delta/update/stale conflict/delete and unknown JSON retention; admin nullable `customNotes` schema change 1→2, no-op stability and stale-draft preservation; and 7/7 disposable-Mongo operator checks including concurrency, deadline, and recovery.

A separate standalone Windows runtime smoke passed via strict Gradle JavaExec against disposable loopback services. It exercised Windows DPAPI, real HTTP auth, Room offline restart, two independent stores using actual coordinator push/pull/delta, schemaVersion 2, unknown nested JSON and `customNotes` retained through native edit, and parent/child sync/group deletion. UI provider approval timed out twice and connected-device acceptance was not run. Native acceptance is not passed, production migration is unauthorized, and release remains blocked.

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
