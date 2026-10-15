# Doggo Project Changelog

All notable changes to the Doggo project are documented in this file, organized chronologically by implementation phase.

---
## [Phase 03] - 2026-10-15: Hub v2 Admission, Fencing and Migration (Gate G3 Backend Logic & Tests Delivered; Staging Gate Pending)

### Added
- Sibling backend models and migration state machine in `glean-hub-sync` (`models/strict_upgrade.rs`):
  - Canonical schema definitions and JSON builders (`canonical_v1_schema_json`, `canonical_v2_schema_json`) matching fingerprints `3361a80f...` (v1) and `5f539551...` (v2).
  - 8-stage migration transition state machine (`MigrationState`: `ready-v1`, `fencing`, `transforming`, `validating`, `publishing`, `ready-v2`, `operatorRequired`, `rolledBack`).
  - DTOs for contract queries (`StrictContractInfo`), upgrade requests (`StrictUpgradeRequest`), migration records (`MigrationRecord`), coordination documents (`AppCoordinationDoc`, `PermitEntry`), and legacy receipt reconciliation (`LegacyReceiptItem`, `LegacyReceiptsRequest`, `LegacyReceiptStatus`, `LegacyReceiptResult`, `LegacyReceiptsResponse`).
- Durable permit admission and exclusive fencing service in `glean-hub-sync` (`services/permit_service.rs`):
  - `PermitService` managing coordination records in `_coordination` (`permit:<appId>`).
  - Enforces bounded concurrency ceiling `MAX_CONCURRENT_PERMITS = 1000` via atomic CAS filter `permitCount: { $lt: 1000 }` with `$inc: { permitCount: 1 }`.
  - Permit release verifies `activePermits.<permit_id>: { $exists: true }` before decrementing `permitCount: -1`.
  - Exclusive migration fence acquisition (`fenced: true`, `fenceOwner`, incremented `fenceGeneration`) and active permit draining.
- In-place strict upgrade engine in `glean-hub-sync` (`services/strict_upgrade_service.rs`):
  - `StrictUpgradeService` orchestrating contract inspection, dry-run preflights, exclusive fencing, and permit draining.
  - Chunked in-place MongoDB document transformation (`BATCH_SIZE = 500`) setting 12 missing v2 fields to default/null on live rows while preserving `{}` tombstones.
  - Idempotent `$exists: false` matching and resumption passing prior recorded `recordsTransformed` and `tombstonesPreserved` (with exact mid-batch interruption accounting remaining an open item awaiting revalidation).
  - Schema publication via CAS in `_schemas` using majority journaled write concern, followed by exclusive fence release.
- Legacy receipt reconciliation bridge in `glean-hub-sync` (`services/strong_sync_service.rs`):
  - Dedicated reconciliation route for v1 client receipts (`POST /api/v1/sync/:app_id/strong/legacy-receipts`).
  - Bounded batch limit `<= 100` receipts per request (`MAX_BATCH_SIZE`).
  - Protocol validation enforcing `request.protocol == "strict-v1"` (rejecting mismatched protocols with HTTP 426 `protocolUnsupported`).
  - SHA-256 v1 digest recomputation and 4-way receipt status classification (`provenAccepted`, `provenNotAccepted`, `unknownSuperseded`, `integrityFailure`).
  - Durable replay confirmation writes (`confirmationNonce`) for matching `lastOp` without mutating task payload data.
- App-scoped administration routes and authorization middleware in `glean-hub-sync` (`routes/strict_upgrades.rs`, `middleware/dual_auth.rs`):
  - `GET /api/v1/apps/:app_id/strict-contract`, `POST /api/v1/apps/:app_id/strict-upgrades`, `GET /api/v1/apps/:app_id/strict-upgrades/:migration_id`.
  - `app_scoped_admin_middleware` requiring both `is_admin: true` and target app present in `claims.apps` (returning HTTP 401 for non-admin, HTTP 403 for ungranted app).
- Integration test suite in `glean-hub-sync` (`services/strict_upgrade_tests.rs`):
  - 11 unit/contract tests covering app-scoped admin authorization, 401/403 status mappings, route parameter extraction, schema validation, error response codes, request parsing, and coordination document serialization.
- Sibling operator documentation in `glean-hub` (`docs/doggo-strict-sync.md`):
  - Strict-v2 upgrade invariants, management endpoints, multi-node coordination runbook, and migration procedures.

### Changed
- `glean-hub-sync/src/services/strong_sync_service.rs`: Integrated durable permit acquisition and release gates across all strict data access endpoints (`bootstrap`, `push`, `pull`, `legacy-receipts`), rejecting client ingress with HTTP 503 `migrationInProgress` when app is fenced.
- `glean-hub-sync/src/services/app_service.rs`: Extended schema validation to support `strict-v2` 16-column specifications and mode-specific column rules.
- `docs/system-architecture.md`: Updated Section 6 to record Phase 03 delivered logic, documented management endpoints, bounded coordination permits, in-place migration engine, legacy receipt reconciliation, and Invariants 33–36.
- `docs/hub-schema-integration.md`: Updated status to Phase 03 Delivered, detailed upgrade and legacy receipt wire contracts, bounded coordination permit gate, error mappings, and release gating criteria.
- `docs/codebase-summary.md`: Updated Repomix metrics based on fresh compaction (340 files, 656,907 tokens), documented delivered Phase 03 backend architecture and modules, and added Invariants 38–41.

### Verification and Gate G3 Status
- Backend test runner passes 54 tests in Rust `glean-hub-sync` (1 ignored: `test_strong_sync_live_mongo_acceptance_gate` due to missing `MONGO_TEST_URI`), clippy clean (0 compiler warnings).
- Vector runner passes 23/23 checks (`scripts/strict-v2-vector-runner.mjs`).
- Live smoke offline fixture self-test passes 18/18 checks (`node scripts/hub-live-smoke.mjs --fixture-test`).
- Shared Kotlin Multiplatform JVM test suites pass 116 tests (`./gradlew :shared:jvmTest --dependency-verification=off`).
- Production admission remains on hold: Phase 03 backend contracts and logic are delivered with passing unit/contract checks; (1) Gate G3 live multi-instance replica-set acceptance is unexecuted pending staging testbed, (2) coordination-document compatibility/backfill for documents missing `permitCount` requires live verification, and (3) exact cumulative accounting across mid-batch crash interruptions requires formal revalidation.

---
## [Phase 02] - 2026-10-15: Native Delivery & Strict DST Resolution (Gate G2 Blocked on Host Prerequisites)

### Added
- Strict timezone and DST resolution contract in `:shared`:
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/strict-timezone-resolver.kt`: `StrictTimeZoneResolver` interface and `StrictZoneOffsetResult` sealed hierarchy (`Single`, `Overlap`, `Gap`). Rejects non-existent spring-forward gap instants (`IllegalArgumentException`), requires explicit offset disambiguation for fall-back overlap hours, and calculates calendar day boundaries with calendar arithmetic (23h/25h).
  - Platform actual bindings delegating to standard `java.time.zone.ZoneRules`: `shared/src/jvmMain/kotlin/dev/doggo/shared/contract/strict-timezone-resolver-jvm.kt` and `shared/src/androidMain/kotlin/dev/doggo/shared/contract/strict-timezone-resolver-android.kt`.
  - Comprehensive vector test suite: `shared/src/commonTest/kotlin/dev/doggo/shared/contract/strict-timezone-resolver-test.kt` exercising 7 test vectors (NY spring gap, NY fall overlap, standard unambiguous time, 23h and 25h calendar day boundaries, input bounds validation, and midnight gap transitions).
- Candidate mobile notification adapter & receiver in `:androidApp`:
  - `androidApp/src/main/kotlin/dev/doggo/android/android-notification-adapter.kt`: Notification channel setup (`doggo_reminders`), intent preparation, and AlarmManager exact alarm scheduling (`setExactAndAllowWhileIdle`).
  - `androidApp/src/main/kotlin/dev/doggo/android/android-reminder-capabilities.kt`: Independent capability probes decoupling `POST_NOTIFICATIONS` (API 33+) from `SCHEDULE_EXACT_ALARM` (`AlarmManager.canScheduleExactAlarms()` on API 31+).
  - `androidApp/src/main/kotlin/dev/doggo/android/doggo-alarm-receiver.kt`: Non-exported `BroadcastReceiver` (`android:exported="false"`) handling offline alarm dispatch with strict intent extras validation.
  - `androidApp/src/test/kotlin/dev/doggo/android/android-notification-adapter-test.kt`: Unit tests verifying action and extra constants, intent generation, and capabilities data classes.
- Candidate desktop notification adapters in `:desktopApp`:
  - `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-notification-adapter.kt`: Linux D-Bus candidate communicating with `/org/freedesktop/Notifications`, mapping missing daemons (`ServiceUnknown`) and absent session bus to `LinuxNotificationStatus.Unavailable` without crashes.
  - `desktopApp/src/linuxTest/kotlin/dev/doggo/desktop/linux/linux-notification-adapter-test.kt`: 4 unit tests verifying missing daemon handling, missing session handling, safe host probing, and capabilities under mock proxy.
  - `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-notification-adapter.kt`: Windows toast candidate configuring AUMID `dev.doggo.desktop`, XML attribute/entity escaping, and tag/group toast clearance. Includes `JnaWindowsToastBridge` explicitly returning `isAvailable() = false` and `WindowsNotificationStatus.Unavailable` pending native WinRT/COM implementation and Windows testbed in Phase 09.
  - `desktopApp/src/windowsTest/kotlin/dev/doggo/desktop/windows/windows-notification-adapter-test.kt`: Unit tests verifying XML escaping, XML payload construction, and explicit bridge unavailability.

### Changed
- `androidApp/src/main/AndroidManifest.xml`: Added `SCHEDULE_EXACT_ALARM` and `POST_NOTIFICATIONS` permissions, and declared non-exported `DoggoAlarmReceiver` (`android:exported="false"`).
- `desktopApp/gradle-linux.lockfile`: Reconciled locked artifacts to remove stale JNA dependencies while maintaining Linux target dependency isolation.
- `docs/android-acceptance.md`: Updated Section 4.2 with Phase 02 mobile acceptance matrix, recording candidate adapter and receiver verification status as unexecuted pending host Android SDK/device in Phase 08.
- `docs/linux-acceptance.md`: Updated Section 4.2 with Phase 02 Linux desktop acceptance matrix, recording candidate adapter status and missing daemon probe on workstation session.
- `docs/windows-acceptance.md`: Updated Section 4.2 with Phase 02 Windows desktop acceptance matrix, recording candidate adapter status as source-inspected/unexecuted pending Windows testbed in Phase 09.
- `docs/system-architecture.md`: Updated status to reflect Phase 02 deliverables and Gate G2 blocked status; added Section 4.10 (Native Notification Candidates & Strict Timezone Architecture), Invariants 29–32, and Section 6.5.

### Verification and Gate G2 Status
- Reported automated test runs pass 147 tests across `:shared:jvmTest` (115 tests, including 7 strict timezone vectors) and `:desktopApp:test` (32 tests, including 4 Linux D-Bus tests) under `--dependency-verification=off` (recorded in Phase 02 QA and Code Review reports).
- Strict root dependency verification (`./gradlew without --dependency-verification=off`) remains failing until platform lockfile hashes are unified.
- Gate G2 physical native delivery is formally recorded as **BLOCKED** per specification due to missing implementation and workstation prerequisites: Android SDK/device absent (Phase 08), active desktop notification daemon absent on user D-Bus session (Phase 09), and Windows native WinRT/COM implementation and host runtime absent (Phase 09).
---
## [Phase 01] - 2026-10-15: Freeze Temporal Model and Strict Transition (Gate G1 Frozen)

### Added
- Canonical schema specifications:
  - `integration/glean-hub/doggo-schema-v1.json`: Canonical `strict-v1` schema with 4 columns (`id`, `title`, `completed`, `createdAt`), SHA-256 fingerprint `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`.
  - `integration/glean-hub/doggo-schema-v2.json`: Canonical `strict-v2` target schema with 16 columns (adding `deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`), SHA-256 fingerprint `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`.
- Binary test vectors:
  - `integration/glean-hub/strict-v2-vectors.json`: 20 cross-stack cryptographic reference test cases (9 valid vector preimages + SHA-256 digests; 11 boundary and overflow rejection vectors).
- Kotlin Multiplatform domain contracts and digest engine:
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/strict-sync-v2-contract.kt`: `StrictV2Constants`, `StrictV2TodoData`, `StrictV2OpKind`, `StrictV2SyncOp`, `LegacyReceiptStatus`, `LegacyReceiptResult`, with checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) rejecting overflow wrapping at intermediate and terminal operations.
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/strict-sync-v2-digest.kt`: `StrictSyncV2Digest` calculating SHA-256 binary preimages with framed identity prefix (8 length-prefixed strings + big-endian 8-byte signed expected revision), 0 bytes for DELETE, and canonical presence-tagged field framing for CREATE/UPDATE.
- Test suites & verification runners:
  - `shared/src/commonTest/kotlin/dev/doggo/shared/contract/strict-v2-digest-vector-test.kt`: 5 vector tests verifying migrated null, date-only, timed DST overlap, repeating count 10, and disabled rule preimages against fixture.
  - `shared/src/commonTest/kotlin/dev/doggo/shared/contract/strict-v2-digest-extended-test.kt`: 5 extended tests exercising cleared deadlines, Unicode strings, zero-byte DELETE, negative epochs, and all boundary/overflow rejection cases.
  - `scripts/strict-v2-vector-runner.mjs`: Independent Node.js test runner verifying preimage framing, SHA-256 digests, boundary rejections, and automated schema fingerprint derivation.
  - `scripts/generate-strict-v2-vectors.mjs`: Vector generation utility producing canonical fixture vectors.

### Changed
- `docs/system-architecture.md`: Added Section 6 documenting `strict-v2` wire schema, bit-level binary digest framing, and planned management endpoints (`/strict-contract`, `/strict-upgrades`), app-scoped admin fencing, and legacy receipt reconciliation.
- `docs/local-storage-and-sync.md`: Added Section 7 detailing planned Room v1 -> v2 schema evolution, migrated default policies, outbox provenance fields (`protocolVersion`, `dispatchedOnce`, `changedFieldsMask`), legacy operation receipt reconciliation states (`PROVEN_ACCEPTED`, `PROVEN_NOT_ACCEPTED`, `UNKNOWN_SUPERSEDED`, `INTEGRITY_FAILURE`), intent rebase invariants, and Section 7.3 mathematical invariants.
- `docs/hub-schema-integration.md`: Added versioned schema upgrade specification, canonical schema fingerprints, planned management endpoints, and admin fencing lifecycle.
- `docs/code-standards.md`: Added coding standards for strict-v2 preimages, checked 64-bit integer arithmetic, temporal model representations, and error code conventions.
- `docs/codebase-summary.md`: Updated codebase inventory, Repomix metrics, directory structure, and architectural invariants for strict-v2 contracts and scripts.
- `docs/project-overview-pdr.md`: Updated PDR to Version 1.8.0 reflecting Phase 01 / Gate G1 freeze specifications and deliverables.

---
## [2026-10-15] - Direct Target Collection Migration (FR-STRICT-02)

### Changed
- Migrated Glean Hub `strict-v1` backend storage resolution from dynamic epoch-based formula (`_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>`) to direct application target collection (`app_<appId>`, e.g. `app_doggo`).
- Updated `AppService::strict_collection_name` and `validate_strict_admission` in `glean-hub-sync` to validate and admit target collection `app_<appId>` with compound scan index `{ userId: 1, tableName: 1, rowId: 1 }`, uncapped storage, and no TTL indexes.
- Reconciled MongoDB standalone topology check in admission preflight to handle both `isWritablePrimary` and `ismaster`.
- Updated Doggo maintenance recovery script `scripts/hub-strict-recovery.js` to target `app_${CONFIG.appId}` with collision safeguards.
- Rechecked Doggo shared KMP and Android client modules, confirming zero breaking changes and full strict-v1 protocol compatibility.
- Added operator migration runbook: existing `_strict_v1_5_doggo_<cleanEpoch>` collections can be renamed in `mongosh` via `db.getCollection(oldCollectionName).renameCollection("app_doggo")` before running `strong_sync_admission`.

---

## [Phase 10] - 2026-10-14: Native Windows DPAPI & Installed MSI Implementation

### Added
- Windows `WindowsDpapiVault` using user-scoped `CryptProtectData` / `CryptUnprotectData`, atomic replacement, and fail-closed user ACL enforcement; `testAclEnforcementOnStoredVaultFile` checks the stored-file owner ALLOW entry.
- `WindowsDpapiVaultProvider` ServiceLoader registration; Windows runtime excludes Linux D-Bus dependencies.
- `desktopApp/gradle-windows.lockfile` and `scripts/windows-package-smoke.ps1` with COM metadata inspection.
- Windows evidence summary reports the `Doggo.exe` distributable and unsigned `Doggo-1.0.0.msi` (100.68 MB) built and verified; seven high-fidelity Windows UI design mockups.

### Verification and remaining acceptance
- Evidence summary reports 121/121 combined desktop and shared JVM tests passing, target isolation verified, and MSI generated/package metadata inspected.
- Phase 10 implementation delivered / 100% on 2026-10-14. Technical acceptance is blocked by root-level strict dependency verification, interactive MSI installation, and Narrator verification; clean-user runtime/sync and uninstall/reinstall remain pending. The MSI is unsigned and is not a production release.

---

## [Phase 09] - 2026-10-14: Linux Desktop & Installed RPM Distribution

### Added
- `:desktopApp` module configuration with Compose Multiplatform 1.7.3 on Kotlin JVM 2.1.10.
- `desktopApp/build.gradle.kts`: Desktop build script supporting host OS detection, target isolation, target-specific sourceSets (`src/${desktopTarget}Main`), Compose desktop packaging (RPM and MSI targets), custom dependency locking, and `runDebug` task.
- `desktopApp/gradle-linux.lockfile`: Custom dependency locking definition for Linux target (161 locked artifacts).
- `desktopApp/src/main/kotlin/dev/doggo/desktop/credential-vault-provider.kt`: Hardware/OS credential vault Service Provider Interface (SPI).
- `desktopApp/src/main/kotlin/dev/doggo/desktop/platform-vault-dispatcher.kt`: ServiceLoader-based vault dispatcher requiring exactly one target matching provider with fail-closed resolution.
- `desktopApp/src/main/kotlin/dev/doggo/desktop/single-instance-guard.kt`: Exclusive OS file-channel lock on `~/.local/share/doggo/accounts/doggo.lock` preventing concurrent app launches and token refresh races.
- `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-sync-scheduler.kt`: Foreground-only sync scheduler managing periodic (15-min) and immediate sync with `LeaveSchedulerCancelGate` quiescence.
- `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt`: Window scaffold hosting `DoggoApp`, `DesktopController`, `MainKt` (RELEASE mode, HTTPS only), and `DevelopmentLauncher` (DEBUG mode, HTTP loopback permitted).
- `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-secret-service-vault.kt`: Linux Secret Service credential vault storing access and refresh tokens as an atomic JSON bundle via `secret-tool` / D-Bus.
- `desktopApp/src/linuxMain/resources/META-INF/services/dev.doggo.desktop.CredentialVaultProvider`: ServiceLoader registration descriptor for `LinuxSecretServiceVaultProvider`.
- `desktopApp/src/test/kotlin/dev/doggo/desktop/platform-vault-dispatcher-test.kt`: Unit tests for SPI provider discovery, zero/multiple/mismatch fail-closed behavior (5/5 PASS).
- `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-lifecycle-test.kt`: Unit tests for single-instance guard, sync scheduler, and build mode origin policies (8/8 PASS).
- `desktopApp/src/linuxTest/kotlin/dev/doggo/desktop/linux/linux-secret-service-vault-test.kt`: Unit tests for Secret Service status transitions, atomic bundle storage, replacement, and deletion (6/6 PASS).
- `scripts/linux-package-smoke.sh`: Packaging verification and diagnostics script supporting environment diagnostics (`--diagnose`), unpacked distributable validation (`--dist`), and RPM query/dependency audits (`--rpm`).
- `docs/linux-desktop-guide.md`: Linux desktop operations and build guide covering prerequisites, commands, launch modes, and uninstallation.
- `docs/linux-acceptance.md`: Acceptance specification matrix for Linux desktop and installed RPM package.
- `docs/evidence/linux-test-summary.md`: Linux desktop test summary report detailing automated test execution and artifact evidence.
- `docs/evidence/linux-*.png`: 7 high-fidelity visual design mockups for Linux desktop screen states.

### Changed
- `docs/project-overview-pdr.md`: Updated to Version 1.6.0 reflecting Phase 09 requirements, deliverables, constraints, and roadmap status.
- `docs/codebase-summary.md`: Updated codebase inventory, Repomix metrics, directory structure, and architectural invariants for `:desktopApp`.
- `docs/deployment-guide.md`: Updated to Version 1.2.0 adding Section 7 detailing Linux desktop distributable and RPM deployment procedures.
- `docs/project-changelog.md`: Added Phase 09 changelog entry.

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
