# Doggo Project Changelog

All notable changes to the Doggo project are documented in this file, organized chronologically by implementation phase.

---

## [Phase 08] - 2026-10-14: Authorized Hub Operations & Signed Android Release

### Added
- `scripts/hub-live-smoke.mjs`: Live Hub strict-v1 verification harness covering health check, admission read-back, auth session, structured per-operation acknowledgements, base conflict rejection, stable cursor pagination, and revision-CAS empty tombstone persistence. Includes offline self-test mode (`--fixture-test`).
- `scripts/release-security-check.mjs`: Android release security auditing tool enforcing fail-closed signature verification (`apksigner`), manifest security analysis (`apkanalyzer`), cleartext traffic exclusion, backup/extraction rules, approved HTTPS origin validation, and repository secret scanning.
- `scripts/hub-strict-recovery.js`: Maintenance-only disaster recovery script for `mongosh` enforcing writer quiescence, allocating a fresh UUID v4 epoch, provisioning isolated namespaces, resetting active records to revision 1 with `lastOp = null`, maintaining permanent empty tombstones, and executing journaled registry Compare-And-Swap.
- `docs/deployment-guide.md`: End-to-end operational guide for Glean Hub strict provisioning, release APK signing, security inspection, live smoke testing, and device installation.
- `docs/security-and-recovery.md`: Comprehensive security architecture documentation and step-by-step disaster recovery runbook.
- `docs/release-acceptance.md`: Release verification matrix distinguishing operational tooling preparation from live release acceptance gates.
- `docs/evidence/android-release-summary.md`: Android release tooling summary report detailing implemented artifacts, test status, and prerequisites.
- `docs/evidence/hub-recovery-summary.md`: Hub operational recovery summary detailing disaster recovery invariants and validation procedures.
- `docs/evidence/android-release-*.png`: 7 pixel-precise (1080x2400) release visual design baseline mockups matching ForumFlash design specifications.
- `README.md`: Root project overview, feature summary, directory structure, commands, and documentation links.

### Changed
- `docs/project-overview-pdr.md`: Updated to Version 1.5.0 reflecting completion of Phase 08 tooling and operational artifacts.
- `docs/codebase-summary.md`: Updated directory inventory and artifact descriptions to incorporate Phase 08 scripts, documentation, and evidence.
- `docs/development-roadmap.md`: Updated roadmap pointers and release operations milestone details.

---

## [Phase 07] - 2026-10-14: Android Composition & Runtime Integration

### Added
- `:androidApp` module configuration with AGP 8.7.3, minSdk 26, compileSdk 35.
- `DoggoApplication` process container managing shared singletons, cold-start identity persistence, and fail-closed wipe recovery.
- `SessionViewModel` with reactive `TodoUiState`, conflict review dialog, and zero credential retention.
- `AndroidSyncScheduler` WorkManager integration with account-tagged cancellation.
- `DoggoSyncWorker` background sync worker with generation, auth, and account fencing.
- Android security configurations: `backup_rules.xml`, `data_extraction_rules.xml`, and debug loopback `network_security_config.xml`.
- Authored instrumented device tests: `todo-device-test.kt` and `background-isolation-test.kt`.
- `scripts/android-smoke.sh` device smoke runner and `scripts/debug-fault-proxy.mjs` fault-injection proxy.
- 7 visual design mockups in `docs/evidence/android-debug-*.png`.

---

## [Phase 06] - 2026-10-14: Shared Compose UI & Accessible Light Theme

### Added
- Shared Compose Multiplatform UI components in `shared/`: `DoggoApp`, `ResponsiveLayout`, `AccountForm`, `AccessRequestPanel`, `TodoList`, `TodoItemRow`, `TodoEditorDialog`, `SyncStatusBar`, `ConflictDialog`, `LeaveDialog`, and `LicenseNoticeDialog`.
- Accessible light theme tokens conforming to WCAG 2.1 AA contrast requirements.
- Typography scale, semantic colors, and light theme icon primitives.
- Common UI unit and interaction test suites.

---

## [Phase 05] - 2026-10-14: Durable Local State & Strict Per-Record Sync

### Added
- Room KMP persistence (`DoggoDatabase`, version 1) with `BundledSQLiteDriver`.
- Local outbox mutation engine and staged full-scan reconciler.
- Fail-closed account teardown protocol writing generation and wipe markers outside SQLite.
- 89 JVM common and Room integration tests.

---

## [Phase 04] - 2026-10-14: Hub Authentication & Protected Session

### Added
- Hub authentication client with Ktor HTTP transport.
- Single-flight token refresh coordinator.
- `AndroidKeystoreVault` hardware-backed credential storage.
- Session state machine: unauthenticated, pending approval, authenticated, and guarded logout.

---

## [Phase 03] - 2026-10-14: Isolated Strict Server & Legacy Isolation

### Added
- Glean Hub strict-v1 protocol extension in Rust (`glean-hub-sync`).
- Dedicated strict routes (`/api/v1/sync/:app/strong/*`) and strict app registration.
- Read-only `strong_sync_admission` operator preflight helper.
- Legacy sync route isolation guards preventing strict apps from accessing legacy endpoints.

---

## [Phase 02] - 2026-10-14: Frozen Foundation & Contracts

### Added
- Root Gradle multiplatform build baseline with Gradle 8.11.1 wrapper.
- Version catalog (`gradle/libs.versions.toml`) with pinned dependencies.
- 16 core Kotlin domain and contract specifications in `shared/src/commonMain/kotlin/dev/doggo/shared/contract/`.
- NIST cryptographic digest test vectors and validation test suites.

---

## [Phase 01] - 2026-10-13: Headless Tooling & Android Environment

### Added
- Headless Xpra virtual display runner (`scripts/start-xpra.sh`).
- Android SDK and JDK environment configuration script (`scripts/android-sdk-env.sh`).
- Android emulator lifecycle runner (`scripts/start-android-emulator.sh`).
- Operational setup runbooks and host evidence records in `docs/`.
