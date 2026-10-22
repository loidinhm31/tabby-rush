# TabbyRush

TabbyRush is a native Android and desktop task-management application built with Kotlin Multiplatform and Compose. Local data is stored offline in Room/SQLite and synchronized with Glean Hub using ordinary app registration and standard push/pull/delta routes. Glean Hub's admin console is management tooling, not a TabbyRush web application.

The current client does not use strict-v1/v2/v3 synchronization. Hub `schemaVersion` is informational concurrency metadata; it does not automatically generate native UI, Kotlin fields, or Room migrations. Former strict protocol runbooks have been retired.

## Features

- Offline-first task creation, editing, completion, deletion, and account-specific storage.
- Two-level parent/child tasks, client-side hierarchy projection, group deletion, and parent completion behavior.
- Optional date/time deadlines and local reminders, with shared scheduling rules and platform-specific delivery integrations.
- Collaborative task assignment: multi-user task delegation with single canonical creator-owned rows, child-only privacy boundaries, full-group completion calculation, and username lookup.
- Standard Hub sync with raw unknown JSON retention, explicit intent-based merges, version acknowledgements, staged full scans, and conflict review.
- Room v5 local storage with two-stage legacy owner binding, authoritative access state tracking, and privacy-first revocation purge with non-replayable content-free evidence.
- Whole-app collaboration cutoff (HTTP 426) for legacy clients and single app-wide replica-set transaction guard (`_sync_assignment_fences`).
- Android app and Glance task widget/notifications; desktop Compose app and sticky task surfaces where the platform supports them.
- Protected platform credentials: Android Keystore, Linux Secret Service, and current-user Windows DPAPI.
- Glean Hub administrator announcements with a separate versioned contract and in-memory client inbox.
## Current Status

Implementation of Collaborative Task Assignees (Phases 01–08) is complete, establishing Room v5 persistence, two-stage binding, canonical identity projections, and shared Compose/Android/desktop surfaces. Policy validation (G0) was confirmed 2026-10-21 (privacy-first purge, whole-app capability cutoff, and simpler app-wide replica-set guard).

Dedicated verification harnesses have been prepared in Phase 09: `./gradlew :desktopApp:runTaskAssigneesRuntimeSmoke` and `node scripts/task-assignees-live-smoke.mjs`. However, G1–G6 runtime verification is **NOT RUN / pending** per project delegation rules (reserved for Main integration verification).

Prior reported verification includes offline Gradle/package suites, disposable-local Hub API/browser smoke, 7/7 disposable-Mongo conversion-operator checks, and a standalone Windows runtime smoke through a strict Gradle JavaExec entry point. Interactive UI provider approval timed out twice; connected-device acceptance was not run. Native acceptance is not passed, no production database has been migrated or authorized, and release remains blocked. See [Release Acceptance](docs/release-acceptance.md) for the current boundary.
## Repository Layout

```text
androidApp/      Native Android lifecycle, platform integration, widgets and tests
desktopApp/       Compose Multiplatform desktop app with Linux/Windows providers
shared/          Shared domain, Compose UI, Room storage, auth and standard sync
scripts/         Provisioning, security checks, host tooling, smoke and packaging helpers
integration/     Hub schema inputs, announcement contracts and historical vectors
plans/           Implementation plans, phase evidence and reviews
docs/            Architecture, requirements, operations, acceptance and archives
```

## Development

See [Toolchain Versions](docs/toolchain-versions.md), [Code Standards](docs/code-standards.md), [System Architecture](docs/system-architecture.md), and [Deployment Guide](docs/deployment-guide.md) for supported environment and workflows. Common entry points include:

```bash
./gradlew :shared:jvmTest
./gradlew :desktopApp:runTaskAssigneesRuntimeSmoke -PdesktopTarget=windows
node scripts/task-assignees-live-smoke.mjs --help
node scripts/hub-schema-provision.mjs --help
node scripts/release-security-check.mjs --diagnose-source
node scripts/admin-announcements-smoke.mjs --self-test
```

Run provisioning only for a new ordinary Hub app registration. On Windows, its `--out` file must be placed in an existing private, non-symlink directory with protected DACL/owner limited to the current user, `SYSTEM`, and `Administrators`; the script refuses unsafe directories and does not change their permissions. Never pass administrative tokens or database credentials as command-line literals.

Package generation and automated test results are not a substitute for interactive platform acceptance or production authorization.

## Documentation Index

- [Project Overview & Product Requirements](docs/project-overview-pdr.md)
- [System Architecture](docs/system-architecture.md)
- [Code Standards](docs/code-standards.md)
- [Codebase Summary](docs/codebase-summary.md)
- [Local Storage & Standard Sync](docs/local-storage-and-sync.md)
- [API Contract](docs/api-contract.md)
- [Standard Sync & Schema Design](docs/standard-sync-cutover-design.md)
- [Standard Sync Cutover Runbook](docs/standard-sync-cutover-runbook.md)
- [Hub Schema Integration](docs/hub-schema-integration.md)
- [Hub Compatibility Acceptance](docs/hub-compatibility-acceptance.md)
- [Authentication & Credentials](docs/authentication-and-credentials.md) — not revalidated during this cutover; use the API Contract above for current routes.
- [Security & Recovery](docs/security-and-recovery.md)
- [Deployment Guide](docs/deployment-guide.md)
- [Release Acceptance](docs/release-acceptance.md)
- [Android Acceptance](docs/android-acceptance.md)
- [Linux Desktop Guide](docs/linux-desktop-guide.md) · [Linux Acceptance](docs/linux-acceptance.md)
- [Windows Desktop Guide](docs/windows-desktop-guide.md) · [Windows Acceptance](docs/windows-acceptance.md)
- [Announcements Contract](docs/announcement-contract.md) · [Announcements Evidence](docs/evidence/admin-announcements-acceptance.md)
- [Project Roadmap](docs/project-roadmap.md) · [Development Roadmap](docs/development-roadmap.md) · [Project Changelog](docs/project-changelog.md)
