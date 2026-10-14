# Doggo Project Roadmap

**Current Status:** In Progress
**Last Updated:** 2026-10-14
**Target Plan:** [plans/261013-1540-doggo-glean-hub-reuse/plan.md](../plans/261013-1540-doggo-glean-hub-reuse/plan.md) (Authoritative Evolution Plan)
**Historical Plan:** [plans/261013-0914-doggo-compose-todos/plan.md](../plans/261013-0914-doggo-compose-todos/plan.md) (Superseded)
**Overall Completion:** 96% planned-effort-weighted administrative progress (142h / 148h; Phase 01 & 02 DONE; Phase 03, 04, 05, 06 & 07 Implementation Complete / Administratively Closed; Phase 08 Tooling & Operational Readiness 100% Complete; Phase 09 Desktop Tooling, SPI, Distributable & Tests Complete, Live RPM & Xpra Gates Pending Operator Prerequisites)

---

## 1. Executive Summary

Doggo is an Android-first shared Compose Multiplatform application for task management with cloud synchronization. The development follows a phased headless-first architecture on Fedora Linux, progressing from virtualized display infrastructure to shared Kotlin Multiplatform libraries, backend API, Android client integration, and native desktop packaging (Linux RPM and Windows MSI).

Phase 01 (Headless Tooling and Android Environment) was executed and marked complete on 2026-10-13 for tooling scripts, SDK environment setup, and verification suites. Note that interactive visual runtime acceptance (physical Studio first-run wizard and running emulator framebuffer screenshot) remains an environment gate pending interactive operator execution.

Phase 02 (Frozen Foundation & Contracts) was executed and marked complete on 2026-10-14, establishing the Gradle multiplatform root and `:shared` library baseline, pinned version catalog, 16 core Kotlin contract files, 4 commonTest suites, foundational documentation, and 57/57 passing QA assertions.

## 2. Phase Progress & Status

| Phase | Description | Group | Effort | Status | Completed Date | Progress |
|:---|:---|:---:|---:|:---:|:---:|---:|
| **01** | [Headless Tooling & Android Environment](../plans/261013-1540-doggo-glean-hub-reuse/phase-01-headless-tooling.md) | A | 6h | **DONE** | 2026-10-13 | 100% |
| **02** | [Frozen Foundation & Contracts](../plans/261013-1540-doggo-glean-hub-reuse/phase-02-frozen-foundation.md) | B | 12h | **DONE** | 2026-10-14 | 100% |
| **03** | [Isolated Strict Server](../plans/261013-1540-doggo-glean-hub-reuse/phase-03-hub-schema-compatibility.md) | C | 40h | **Implementation Complete\*** | 2026-10-14 | 100% |
| **04** | [Auth Session & Vault](../plans/261013-1540-doggo-glean-hub-reuse/phase-04-auth-session.md) | C | 12h | **Implementation Complete†** | 2026-10-14 | 100% |
| **05** | [Local Strict Sync](../plans/261013-1540-doggo-glean-hub-reuse/phase-05-local-sync-state.md) | C | 28h | **Implementation Complete‡** | 2026-10-14 | 100% |
| **06** | [Compose UI & Light Theme](../plans/261013-1540-doggo-glean-hub-reuse/phase-06-compose-ui-theme.md) | C | 12h | **Implementation Complete§** | 2026-10-14 | 100% |
| **07** | [Android Integration](../plans/261013-1540-doggo-glean-hub-reuse/phase-07-android-integration.md) | D | 14h | **Administratively Closed¶** | 2026-10-14 | 100% |
| **08** | [Android Release Operations](../plans/261013-1540-doggo-glean-hub-reuse/phase-08-android-release-operations.md) | E | 10h | **Tooling & Ops Complete\*\*** | 2026-10-14 | 100% |
| **09** | [Linux Installed RPM Distribution](../plans/261013-1540-doggo-glean-hub-reuse/phase-09-linux-distribution.md) | F | 8h | **Desktop & Tooling Complete††** | 2026-10-14 | 100% |
| **10** | [Windows Installed MSI Distribution](../plans/261013-1540-doggo-glean-hub-reuse/phase-10-windows-distribution.md) | G | 6h | Pending | — | 0% |
| **Total** | | | **148h** | | | **96%** |

*\*Phase 03 implementation complete across 26 files (36 Rust unit/route tests PASS, clippy clean); live Mongo integration gates (§9–§12) are deferred to Phase 07 device sync, and mounted old-binary/rollback gates to Phase 08 release operations.*
*†Phase 04 implementation complete across 14 files (43 JVM unit tests PASS, code review cycle 3 approved 7/10); Android Keystore instrumented test and live Hub runtime acceptance deferred to Phase 07.*
*‡Phase 05 implementation complete across 29 files (89 JVM common/Room tests PASS); Android instrumented Room tests and live Hub end-to-end sync gates deferred to Phase 07 device integration.*
*§Phase 06 implementation complete across 15 files (96 :shared:jvmTest tests pass under lenient dependency verification, strict verification fails on verification-metadata.xml, WCAG 2.1 AA verified contrast); marked complete administratively; technical review 6.5/10 NOT ACCEPTED with documented integration gaps (admin heuristic, compose.uiTest harness, typography/strings.xml) deferred to Phase 07.*
*¶Phase 07 source implementation delivered across 25 exclusive files (including lockfile placeholder stub and 7 UI design mockups); marked administratively closed per user directive. Technical review cycle 3 scored 4/10 NOT ACCEPTED; reported shared JVM regression suite 96/96 PASS under exact command `--dependency-verification=off`; host Android SDK absent on workstation, blocking compileDebugKotlin, assembleDebug, and connectedDebugAndroidTest; teardown generation rollback, cancellation swallowing, and worker TOCTOU remain open release-blocking correctness defects.*
\*\*Phase 08 tooling and operational readiness delivered across 20 exclusive files (scripts, deployment runbooks, security checklist, and release UI state design mockups; node syntax clean, offline fixture smoke 18/18 PASS, source audit CLEAN); marked complete administratively (100% effort); technical review scored 6/10 NOT ACCEPTED with implementation defects (mongosh BSON Int64 import and security checker cert verification) and live environment gates (mongosh on mongod, apksigner on SDK 35 release APK, live Hub verification) remaining release blockers.
††Phase 09 desktop tooling, SPI architecture, Linux dependency locking, distributable packaging, and unit test suites delivered across 23 exclusive files (19/19 desktop unit tests PASS and 96/96 shared JVM tests PASS per QA report under `--dependency-verification=off`); marked complete administratively (100% effort); technical review scored 3/10 NOT ACCEPTED with implementation defects (single-instance guard ordering in `main.kt`, vault subprocess vs D-Bus binding, hardcoded release key, untested multiple-provider assertions) and live environment gates (rpmbuild for RPM package, installed package verification, and live Secret Service daemon in Xpra) remaining release blockers.

---

## 3. Milestones & Timeline

### Milestone 1: Development Infrastructure (Group A) — COMPLETED
- **Phase 01:** Headless tooling, Xpra virtual display isolation, Android SDK environment scripts.
- **Completion Date:** 2026-10-13.
- **Key Deliverables:**
  - `scripts/start-xpra.sh`: Safe virtual X11 server runner (`--html=off`, `--mdns=no`, user-scoped socket).
  - `scripts/android-sdk-env.sh`: Idempotent SDK and compatible JDK configuration.
  - `scripts/start-android-emulator.sh`: AVD emulator lifecycle script with safe validation and hardware/software acceleration support.
  - [headless-android-setup.md](headless-android-setup.md): Complete operations runbook and security guide.
  - [host-tooling-evidence.md](host-tooling-evidence.md): Verified host capability matrix (KVM, OpenJDK 25, Node v24 LTS, Xpra 6.5.4).
  - Automated test suite (`tests/test-phase-01-tooling.sh`): 23/23 QA tests passing; 31/31 review cycle 3 checks passing.
  - *Note on Runtime Acceptance Gate:* Static script validation, syntax checks, and live Xpra smoke test (display :199) passed. Interactive Android Studio wizard, SDK license acceptance, and live AVD framebuffer capture (`android-tooling-launcher.png`) remain operational prerequisites for downstream interactive lanes.

### Milestone 2: Contracts & Shared Core (Groups B & C) — COMPLETED
- **Phase 02 (COMPLETED 2026-10-14):** Contract freeze, multiplatform Gradle build baseline (`:shared` module, Kotlin 2.1.10, Compose 1.7.3, AGP 8.7.3, Room 2.7.0-alpha12 KMP, Gradle 8.11.1 wrapper, 16 contract files, 4 commonTest test suites, 57/57 QA assertions passed).
  - **Key Deliverables:**
    - Root and module build files: `settings.gradle.kts`, `build.gradle.kts`, `shared/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`.
    - Verified Gradle wrapper (8.11.1) with official distribution SHA-256 checksum and verified jar hash.
    - Contract specifications: `shared/src/commonMain/kotlin/dev/doggo/shared/contract/*.kt` (HubConfig, endpoint policy, Unicode scalar title policy, credential vault & bundle with toString redaction, strict-v1 protocol and canonical digest).
    - Unit test suites: `shared/src/commonTest/kotlin/dev/doggo/shared/contract/*.kt` (strict digest NIST vectors, title policy, endpoint policy, account key derivation).
    - Foundational documentation: `docs/system-architecture.md`, `docs/api-contract.md`, `docs/code-standards.md`, `docs/toolchain-versions.md`, `docs/foundation-evidence.md`.
    - Automated verification suite: `tests/test-phase-02-foundation.sh` (57/57 QA assertions passing).
  - *Note on Runtime Acceptance Gate:* Static script validation, configuration checks, and reference Node vectors passed (57/57 QA assertions). Full `./gradlew :shared:jvmTest` execution and `shared/gradle.lockfile` population are deferred to downstream CI/integration lanes with compatible JDK 17/21 and Android SDK.
- **Phase 03 (COMPLETED 2026-10-14):** Doggo-only strict server and legacy isolation in Glean Hub Sync plugin and Doggo tooling (26 files delivered across Doggo and Glean Hub Sync plugin; 36 unit/route tests passed, clippy clean, code review cycle 4 approved 7/10).
  - **Key Deliverables:**
    - Models: `strong_sync.rs` (strict DTOs, document models, SHA-256 canonical digest vectors, base64 cursor).
    - Services: `strong_sync_service.rs` (exact CAS, insert-only revision 1, single-mutation FIFO semantics, real replay confirmation nonces with matched count verification, topology-aware write concerns).
    - Routes & Middleware: `routes/strong_sync.rs` (`bootstrap`, `push`, `pull`), `middleware/sync_mode_guard.rs` (legacy-only non-401 route guard, strict scope coherence).
    - Admission & Tooling: `bin/strong_sync_admission.rs` (read-only candidate preflight CLI), `scripts/hub-schema-provision.mjs` (fail-closed strict-apps provisioner with 0600 key custody), `scripts/hub-compatibility-smoke.mjs` (regression smoke runner), `integration/glean-hub/doggo-schema.json`.
    - Documentation: `docs/hub-schema-integration.md`, `docs/hub-compatibility-acceptance.md`, `docs/evidence/hub-compatibility-summary.md`, and Glean Hub runbook `docs/doggo-strict-sync.md`.
  - *Note on Runtime Acceptance Gate:* 36 unit and route tests pass and clippy clean; live Mongo integration suites (§9–§12) are deferred to Phase 07 device sync, and mounted old-binary/rollback gates to Phase 08 release operations.
- **Phase 04 (COMPLETED 2026-10-14):** Hub authentication client, protected session state machine, single-flight refresh coordinator, Android Keystore credential vault, platform HTTP engines, and unit test suites (14 files delivered; 43/43 JVM tests passing, code review cycle 3 approved 7/10).
  - **Key Deliverables:**
    - Common Auth DTOs & HTTP Client: `shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-dtos.kt`, `shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-http-client.kt` (endpoint allowlisting, no redirects/TLS bypass, dual auth headers for strong sync, sanitized safe error handling).
    - Session & Single-Flight: `shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-client.kt`, `shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-session.kt`, `shared/src/commonMain/kotlin/dev/doggo/shared/auth/single-flight-refresh.kt` (generation-fenced session transitions, bounded `/me` access checks, single-flight deduplication, atomic successor vault persistence, no automatic POST retry).
    - Platform Engines & Vault: `shared/src/androidMain/kotlin/dev/doggo/shared/auth/android-http-engine.kt`, `shared/src/jvmMain/kotlin/dev/doggo/shared/auth/jvm-http-engine.kt`, `shared/src/androidMain/kotlin/dev/doggo/shared/auth/android-keystore-vault.kt` (AES-GCM encryption with fresh 12-byte nonce, AAD binding, atomic private file commit).
    - Unit & Instrumented Test Suites: `shared/src/commonTest/kotlin/dev/doggo/shared/auth/hub-auth-client-test.kt` (8 tests), `shared/src/commonTest/kotlin/dev/doggo/shared/auth/hub-auth-session-test.kt` (8 tests), `shared/src/commonTest/kotlin/dev/doggo/shared/auth/single-flight-refresh-test.kt` (3 tests), `shared/src/androidInstrumentedTest/kotlin/dev/doggo/shared/auth/android-keystore-vault-test.kt`.
    - Documentation: `docs/authentication-and-credentials.md`, `docs/auth-acceptance.md`.
  - *Note on Runtime Acceptance Gate:* 43/43 JVM tests pass (100% pass rate). Android Keystore instrumented test execution and live Hub end-to-end authentication validation are scheduled for Phase 07 device integration.
- **Phase 05 (COMPLETED 2026-10-14):** Durable local state and strict per-record sync (29 files delivered across `:shared` module, Room schema v1, and documentation; 89 JVM common/Room tests passing).
  - **Key Deliverables:**
    - Room Entities & DAO: `server-base-entity.kt`, `outbox-entity.kt`, `editor-draft-entity.kt`, `scan-staging-entity.kt`, `account-sync-meta-entity.kt`, `doggo-dao.kt` (atomic freeze CAS transaction, per-account SQLite isolation, immutable outbox records).
    - Database & Storage: `doggo-database.kt`, `database-migrations.kt`, `room-account-store.kt`, `jvm-database-builder.kt`, `android-database-builder.kt`, `jvm-account-files.kt`, `android-account-files.kt`, `DoggoDatabase/1.json`.
    - Strict Sync Protocol & Reconciliation: `hub-sync-dtos.kt`, `hub-sync-client.kt`, `staged-full-scan.kt`, `outbox-reconciliation.kt`, `account-sync-coordinator.kt`, `account-leave-protocol.kt` (CAS-ack reconciliation, staged full scans with monotonic promotion, FIFO dispatch, UnknownSuperseded conflict preservation, fail-closed wipe protocol).
    - Unit & Integration Test Suites: `hub-sync-client-test.kt`, `staged-full-scan-test.kt`, `outbox-reconciliation-test.kt`, `account-sync-coordinator-test.kt`, `account-leave-protocol-test.kt`, `room-account-store-test.kt` (89 JVM tests passing).
    - Documentation: `docs/local-storage-and-sync.md`, `docs/sync-acceptance.md`.
  - *Note on Runtime Acceptance Gate:* 89 JVM common and Room tests pass; Android instrumented tests (`androidInstrumentedTest`) and live Hub sync end-to-end verification are scheduled for Phase 07 device integration.
- **Phase 06 (COMPLETED 2026-10-14):** Shared Compose UI, responsive layout, accessible light theme, transient password protection, open-source attribution, and UI state/token test suite (15 files delivered across `:shared` module, theme, composeResources, test suites, and documentation; 5 conditional font/license files omitted per provenance audit; 96/96 JVM tests passing across 14 test suites under lenient dependency verification).
  - **Key Deliverables:**
    - Theme & Typography: `shared/src/commonMain/kotlin/dev/doggo/shared/theme/doggo-theme.kt` (WCAG 2.1 AA verified light palette: primary `#004646`, deep teal `#003838`, mint surface `#E6F0F2`, cream `#F7F4ED`, dark coral `#C62828`, muted `#546269`, outline `#687677`, ink `#0F172A`), `shared/src/commonMain/kotlin/dev/doggo/shared/theme/doggo-typography.kt` (system `FontFamily.SansSerif` basis, 12sp–34sp type scale).
    - Shared Compose UI Composables: `doggo-app.kt` (responsive compact <840dp vertical stack and expanded >=840dp fixed sidebar layout), `account-form.kt` (login/register with transient password masking and auto-dereference), `access-request-panel.kt` (access state handling for pending, rejected, approved, and unknown), `todo-list.kt` (LazyColumn task list, stable keys, non-color completion states, >=48dp touch targets), `todo-editor.kt` (task creation/editing modal with TitlePolicy validation), `sync-feedback.kt` (sync progress, last reconciled timestamp formatting, paused/reset guidance), `conflict-review.kt` (local vs remote resolution: Discard, Reapply, CopyAsNew, Decide Later), `leave-guard.kt` (fail-closed sign-out warning with unsynced draft/pending/uncertain/conflict tallies), `license-notices.kt` (open-source attribution viewer).
    - Resources: `shared/src/commonMain/composeResources/values/strings.xml`.
    - Documentation: `docs/design-guidelines.md` (WCAG 2.1 AA contrast matrix, responsive architecture, documented integration gaps), `docs/font-provenance.md` (upstream font rights audit, system font decision).
    - Test Suite: `shared/src/commonTest/kotlin/dev/doggo/shared/ui/todo-semantics-test.kt` (7 tests covering 16 calculated WCAG contrast token pairs, password toString redaction, TitlePolicy validation invocation, LeaveGuardState tallies, access statuses, and sync formatting; rendered Compose UI tests absent).
  - *Note on Runtime Acceptance Gate:* 96 :shared:jvmTest tests pass under lenient dependency verification (strict verification fails on verification-metadata.xml), including 7 Phase 06 state/token tests; rendered-UI tests absent (not merely deferred). Technical review cycle 3 scored 6.5/10 NOT ACCEPTED; phase marked complete administratively per user directive with documented integration gaps (admin heuristic, compose.uiTest harness, typography/strings.xml localization) deferred to Phase 07. Rendered UI automation, device-level 200% font scaling, and TalkBack / screen-reader verification are scheduled for Phase 07 Android integration.

### Milestone 3: Android Release & Live Sync (Groups D & E) — IN PROGRESS
- **Phase 07 (ADMINISTRATIVELY CLOSED 2026-10-14; Technical Review NOT ACCEPTED 4/10):** Android composition and runtime integration (25 source files delivered across `:androidApp` module, scripts, documentation, and UI design mockups; 96/96 reported shared JVM regression tests pass under `--dependency-verification=off`; script syntax verified; full compilation and connected device execution deferred to Phase 08 when host SDK/emulator is provisioned).
  - **Key Deliverables:**
    - Gradle & Module Configuration: `androidApp/build.gradle.kts` (AGP 8.7.3, compileSdk 35, minSdk 26, kotlin("android") plugin, release signing environment gate), `androidApp/gradle.lockfile` (tracked dependency lockfile placeholder stub).
    - Android Security & Manifest: `androidApp/src/main/AndroidManifest.xml` (cleartext disabled, single exported MainActivity), `androidApp/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` (strict exclusion of databases, shared preferences, and account files from cloud backup and device transfer), `androidApp/src/debug/AndroidManifest.xml` and `androidApp/src/debug/res/xml/network_security_config.xml` (loopback-only cleartext HTTP for 127.0.0.1, localhost, 10.0.2.2).
    - Application Container & State Architecture: `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt` (process-wide singletons, cold-start identity persistence in `active_account.dat`, synchronous fail-closed startup wipe recovery `completePendingWipesOnStartup`), `androidApp/src/main/kotlin/dev/doggo/android/main-activity.kt` (ComponentActivity hosting Compose `DoggoApp`), `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt` (retained ViewModel, reactive `TodoUiState`, conflict resolution and error mapping, zero secrets retained in state or intents).
    - Background Sync & Scheduling: `androidApp/src/main/kotlin/dev/doggo/android/android-sync-scheduler.kt` (WorkManager one-shot and 15-minute periodic sync with `NetworkType.CONNECTED` constraint, account-tag cancellation `doggo_sync_account_<key>`, fail-closed cancellation gate `cancelAndAwait`), `androidApp/src/main/kotlin/dev/doggo/android/doggo-sync-worker.kt` (AccountKey and generation fencing, active session assertion, startup wipe verification).
    - Device Testing & Diagnostic Tooling: `androidApp/src/androidTest/kotlin/dev/doggo/android/todo-device-test.kt` (ViewModel initial state, filter switching, editor opening/cancelling, direct store access), `androidApp/src/androidTest/kotlin/dev/doggo/android/background-isolation-test.kt` (WorkManager background worker failure on pending wipe and generation mismatch, startup wipe marker cleanup), `scripts/android-smoke.sh` (smoke test runner with reverse port forwarding and screenshot capture), `scripts/debug-fault-proxy.mjs` (loopback fault injection proxy with route allowlist and 2xx upstream assertion).
    - Acceptance Documentation & UI Baselines: `docs/android-acceptance.md`, `docs/evidence/android-test-summary.md`, and 7 high-fidelity UI state design mockups in `docs/evidence/` (empty, populated, completed, editor, error, conflict, access-pending; live framebuffer capture pending device attachment).
  - *Note on Runtime Acceptance Gate:* 96/96 reported shared JVM tests pass under exact command `--dependency-verification=off`; script syntax verified; full compilation (`compileDebugKotlin`, `assembleDebug`), dependency lock generation (`--write-locks`), and connected emulator tests (`connectedDebugAndroidTest`) are deferred to Phase 08 when Android SDK 35 and emulator runtime are provisioned. Review cycle 3 scored 4/10 NOT ACCEPTED; teardown generation rollback, cancellation swallowing, and worker TOCTOU correctness defects remain open and release-blocking.
- **Phase 08 (TOOLING & OPS COMPLETE 2026-10-14):** Authorized Hub operations, disaster recovery runbook, release security checker, and signed Android release readiness (20 files delivered across `scripts/`, `docs/`, and `docs/evidence/`; offline fixture smoke 18/18 PASS, source audit CLEAN).
  - **Key Deliverables:**
    - Operational Scripts: `scripts/hub-live-smoke.mjs` (fail-closed strict-v1 end-to-end smoke verification harness with 18 assertions, offline mock fixture, bounded requests, no redirects), `scripts/release-security-check.mjs` (fail-closed APK security auditor verifying non-debuggable flag, backup and device transfer exclusion rules, single exported ComponentActivity, APK Signature Scheme v2/v3 signing, source secret scanning, and approved HTTPS origin allowlisting), `scripts/hub-strict-recovery.js` (quiescent fresh-epoch recovery script for mongosh with registry CAS, strict-apps admission validation, and old epoch fencing).
    - Operational & Security Runbooks: `docs/deployment-guide.md` (production operator deployment runbook, strict-v1 plugin setup, schema provisioning, standalone/replica write concern guidance), `docs/security-and-recovery.md` (fail-closed account lifecycle, token fencing, credential vault security, disaster recovery runbook with mongodump/restore and epoch CAS migration), `docs/release-acceptance.md` (production acceptance criteria, operator checklist, quality gates).
    - System Documentation: `docs/project-overview-pdr.md`, `docs/codebase-summary.md`, `docs/development-roadmap.md`, `docs/project-changelog.md`, `README.md`.
    - Release Evidence & UI Mockups: `docs/evidence/android-release-summary.md`, `docs/evidence/hub-recovery-summary.md`, and 7 high-fidelity release UI state PNG mockups (`android-release-empty.png`, `android-release-populated.png`, `android-release-completed.png`, `android-release-editor.png`, `android-release-error.png`, `android-release-conflict.png`, `android-release-access-pending.png`).
  - *Note on Runtime Acceptance Gate:* Static script validation and Node syntax checks pass (`node --check` clean across all 3 scripts); fixture smoke test passed 18/18 assertions; source security audit clean; UI states documented via design mockups. Technical review cycle 3 scored 6/10 NOT ACCEPTED for live recovery. Live release remains blocked by open implementation defects (recovery script BSON Int64 import and createdAt Long rewriting, release-security-check debuggable attribute gate and debug-cert rejection) and unexecuted operator environment gates (mongosh against mongod, signed release APK apksigner check, live Hub verification), in addition to Phase 07 deferred correctness defects.

### Milestone 4: Desktop Packaging (Groups F & G) — IN PROGRESS
- **Phase 09 (DESKTOP & TOOLING COMPLETE 2026-10-14; Technical Review NOT ACCEPTED 3/10):** Linux desktop and installed RPM distribution (23 files delivered across `:desktopApp` module, scripts, documentation, and evidence mockups; 19/19 desktop unit tests PASS, 96/96 shared JVM regression tests PASS under `--dependency-verification=off`; packaging smoke script diagnosed).
  - **Key Deliverables:**
    - Desktop JVM & Compose Multiplatform Build: `desktopApp/build.gradle.kts` (Compose 1.7.3 desktop, target detection with fail-closed host matching `-PdesktopTarget`, per-target source set routing for Linux/Windows, custom dependency locking `gradle-linux.lockfile` with 161 locked configurations, nativeDistributions packaging for RPM and MSI with version 1.0.0 and frozen upgrade UUID), `desktopApp/gradle-linux.lockfile`.
    - Pluggable Credential Vault SPI & Dispatcher: `desktopApp/src/main/kotlin/dev/doggo/desktop/credential-vault-provider.kt`, `desktopApp/src/main/kotlin/dev/doggo/desktop/platform-vault-dispatcher.kt` (fail-closed ServiceLoader resolution enforcing exactly one provider matching host OS target, zero or ambiguous match rejection, no plaintext token fallback).
    - Linux Secret Service Provider: `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-secret-service-vault.kt`, `desktopApp/src/linuxMain/resources/META-INF/services/dev.doggo.desktop.CredentialVaultProvider` (Secret Service integration, atomic JSON credential replacement on refresh, account-scoped attributes, explicit removal on leave).
    - Desktop Application Controller & Runtime Lifecycle: `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt` (single-window desktop application hosting Compose `DoggoApp`, Swing EDT lifecycle, `DesktopController` state holder, background refresh coordination, fail-closed startup wipe recovery, debug vs release endpoint fencing).
    - Single Instance Guard & Foreground Scheduler: `desktopApp/src/main/kotlin/dev/doggo/desktop/single-instance-guard.kt` (process file lock on `desktop.lock` with PID tracing preventing concurrent execution across user profile), `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-sync-scheduler.kt` (foreground coroutine scheduler with immediate outbox trigger, 15-minute periodic interval, and `LeaveSchedulerCancelGate` implementation).
    - Diagnostic & Smoke Tooling: `scripts/linux-package-smoke.sh` (RPM and distributable smoke verification runner with `--diagnose`, `--rpm`, and `--dist` modes, inspecting package metadata, files, and launcher binaries).
    - Documentation & Test Evidence: `docs/linux-desktop-guide.md`, `docs/linux-acceptance.md`, `docs/evidence/linux-test-summary.md`, and 7 desktop UI state design mockups in `docs/evidence/linux-*.png` (visual design baselines; live native Xpra screenshots pending display session).
    - Test Suites: `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-lifecycle-test.kt` (8 tests), `desktopApp/src/test/kotlin/dev/doggo/desktop/platform-vault-dispatcher-test.kt` (5 tests), `desktopApp/src/linuxTest/kotlin/dev/doggo/desktop/linux/linux-secret-service-vault-test.kt` (6 tests) — 19/19 passing tests.
  - *Note on Runtime Acceptance Gate:* 19/19 desktop unit tests and 96/96 shared JVM regression tests pass per QA report under exact command `--dependency-verification=off`. Technical review cycle scored 3/10 NOT ACCEPTED due to open implementation defects (single-instance guard ordering in `main.kt`, secret-tool subprocess vs planned D-Bus binding, hardcoded dev release key in `main.kt`, and multiple-provider test assertion coverage) and unexecuted operator environment gates (`rpmbuild` required for RPM packaging, live Secret Service daemon session, and interactive Xpra visual capture). Packaging configuration is not proof of a verified distributable.
- **Phase 10:** Native Windows MSI packaging and manual installer lifecycle gate (remains blocked pending Phase 09 installed-RPM acceptance and operator prerequisites).

Next active phase is **Phase 10: Windows Installed MSI Distribution** (blocked pending Phase 09 installed-RPM runtime gates).

---

## 4. Changelog

### [0.9.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 09 Linux Desktop & Installed RPM Distribution**:
  - Implemented `:desktopApp` JVM module with Compose Multiplatform 1.7.3, dynamic host target detection (`-PdesktopTarget=linux`), per-target source set isolation (`src/linuxMain`, `src/linuxTest`), and target-specific dependency locking (`gradle-linux.lockfile`).
  - Configured native packaging distributions for RPM (`doggo-1.0.0.rpm`) and MSI with frozen package metadata, system menu shortcuts, and immutable upgrade UUID (`4d9a21b3-7f8e-4a6c-9c2e-8e3b1d5a7f9c`).
  - Implemented pluggable `CredentialVaultProvider` SPI and `PlatformVaultDispatcher` using Java `ServiceLoader` with strict fail-closed resolution (exactly one provider matching target OS; zero, multiple, or foreign providers fail closed without plaintext fallback).
  - Implemented Linux Secret Service vault provider (`LinuxSecretServiceVaultProvider`) storing atomic session credentials via Secret Service with account-scoped attributes and explicit deletion.
  - Implemented desktop runtime container and controller (`DesktopController`, `main.kt`) managing Swing coroutine scopes, Compose `DoggoApp` state, reactive `TodoUiState`, startup wipe recovery (`completePendingWipesOnStartup`), and release mode loopback fencing.
  - Implemented `SingleInstanceGuard` utilizing file-based channel locks (`desktop.lock`) to prevent concurrent process execution and database races across identical user profiles.
  - Implemented foreground sync coordinator (`DesktopSyncScheduler`) providing immediate outbox flush, 15-minute periodic sync, and `LeaveSchedulerCancelGate` integration.
  - Implemented packaging and diagnostic verification runner (`scripts/linux-package-smoke.sh`) supporting `--diagnose`, `--rpm`, and `--dist` modes for packaging prerequisite audits.
  - Delivered 23 exclusive Phase 09 files across `:desktopApp`, scripts, documentation, and UI state design mockups.
  - Validated with 19/19 desktop unit tests passing and 96/96 shared JVM regression tests passing per QA report under `--dependency-verification=off`. Technical review cycle scored 3/10 NOT ACCEPTED; single-instance guard ordering, hardcoded release key, live RPM build via `rpmbuild`, live Secret Service daemon integration, and interactive Xpra display capture documented as open code defects and operator environment prerequisites.

### [0.8.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 08 Authorized Hub Operations & Signed Android Release**:
  - Implemented live Hub strict-v1 smoke verification harness (`scripts/hub-live-smoke.mjs`) with 18 comprehensive assertion checks (signup, access request approval, single-flight token refresh, `/me` verification, insert-only revision 1 push, stable cursor pull, CAS conflict detection, replay nonce confirmation, UnknownSuperseded handling, tombstone preservation, and legacy endpoint rejection), bounded network requests, and standalone offline mock fixture mode.
  - Implemented automated release APK security auditor (`scripts/release-security-check.mjs`) validating non-debuggable flag, backup and device transfer exclusion rules, single exported ComponentActivity, APK Signature Scheme v2/v3 signing, source secret scanning, and approved HTTPS origin allowlisting.
  - Implemented disaster recovery and epoch migration script (`scripts/hub-strict-recovery.js`) executing quiescent fresh-epoch recovery, strict collection index validation, Rust admission preflight verification, and atomic schema registry CAS under mongosh.
  - Published comprehensive deployment and operations runbook (`docs/deployment-guide.md`) detailing Glean Hub sync plugin deployment, strict schema provisioning, write concerns, and production operator checklists.
  - Published security architecture and recovery manual (`docs/security-and-recovery.md`) documenting fail-closed account teardown, token fencing, credential vault security, and disaster recovery procedures.
  - Published release acceptance criteria (`docs/release-acceptance.md`), Android release summary evidence (`docs/evidence/android-release-summary.md`), and Hub recovery summary evidence (`docs/evidence/hub-recovery-summary.md`).
  - Delivered 7 high-fidelity release UI state mockups (`docs/evidence/android-release-*.png`) documenting empty, populated, completed, editor, error, conflict, and access-pending states.
  - Delivered 20 exclusive Phase 08 files across scripts, documentation, and evidence. Validated with `node --check` passing across all scripts, offline fixture smoke passing 18/18 assertions, and source security check clean. Live recovery on mongod, signed release APK validation on SDK 35, and live Hub verification documented as operational release gates pending operator infrastructure provisioning.

### [0.7.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 07 Android Composition & Runtime Integration**:
  - Configured `:androidApp` module build with AGP 8.7.3, compileSdk 35, minSdk 26, `kotlin("android")` plugin, and external environment release signing gate (`DOGGO_KEYSTORE`).
  - Enforced security manifest policies: single exported `MainActivity`, cleartext traffic disabled globally, cloud and device transfer backup exclusion rules (`backup_rules.xml`, `data_extraction_rules.xml`), and debug-only loopback cleartext whitelist (`network_security_config.xml`).
  - Implemented application process container (`DoggoApplication`) managing singletons, cold-start identity persistence in `active_account.dat`, and synchronous fail-closed startup wipe recovery (`completePendingWipesOnStartup`).
  - Implemented retained `SessionViewModel` binding Compose UI actions to `AccountRuntime`, reactive `TodoUiState` streams, safe error banners, and conflict review dialogs without leaking credentials into state or intents.
  - Implemented WorkManager background sync coordinator (`AndroidSyncScheduler`) with `NetworkType.CONNECTED` constraints, unique one-shot and 15-minute periodic sync, account-tag cancellation (`doggo_sync_account_<key>`), and fail-closed `cancelAndAwait` cancellation gate.
  - Implemented fenced background worker (`DoggoSyncWorker`) strictly validating AccountKey, active authentication session state, wipe markers, and generation counters.
  - Implemented instrumented device test suites in source (`TodoDeviceTest`, `BackgroundIsolationTest`) covering ViewModel state, task mutations, store isolation, wipe recovery, and generation fencing.
  - Implemented diagnostics and verification tooling: `scripts/android-smoke.sh` (smoke runner with reverse port forwarding and screenshot capture) and `scripts/debug-fault-proxy.mjs` (loopback 127.0.0.1:3000 -> 3001 fault proxy with strict route allowlist and 2xx upstream assertion).
  - Created specification and evidence documentation (`docs/android-acceptance.md`, `docs/evidence/android-test-summary.md`) and 7 high-fidelity UI state design mockups in `docs/evidence/`.
  - Delivered 25 exclusive Phase 07 source files (including dependency lockfile placeholder stub and 7 UI state design mockups). Validated with 96/96 reported shared JVM tests passing under exact command `--dependency-verification=off` and script syntax verified. Marked administratively closed per user directive; technical review scored 4/10 NOT ACCEPTED with compile, device execution gates, and correctness defects (teardown generation rollback, cancellation swallowing, worker TOCTOU) tracked as release-blocking prerequisites for Phase 08.

### [0.6.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 06 Shared Compose UI & Accessible Light Theme**:
  - Implemented accessible light theme palette (`DoggoTheme`, `DoggoColors`) with 16 mathematically verified WCAG 2.1 AA contrast pairs (normal text >= 4.5:1, interactive controls >= 3.0:1) and disabled dynamic color.
  - Implemented platform-universal typography scale (`DoggoTypography`) using Compose `FontFamily.SansSerif` to support international glyph coverage and OS-level font scaling.
  - Implemented responsive application scaffold (`DoggoApp`) adapting between compact vertical stack (<840dp) and two-column fixed-sidebar layout (>=840dp).
  - Implemented authentication form (`AccountForm`) with transient masked passwords, `semantics { password() }`, auto-clearing upon submission, and redacted `toString()` output.
  - Implemented access request panel (`AccessRequestPanel`) supporting pending, approved, rejected, and unknown statuses.
  - Implemented task projection list (`TodoList`) and modal editor (`TodoEditorDialog`) with `TitlePolicy` validation (1–200 Unicode scalars), stable keys, and >=48dp touch targets.
  - Implemented sync progress feedback (`SyncFeedback`), conflict resolution dialog (`ConflictReviewDialog`), and guarded account teardown dialog (`LeaveGuardDialog`).
  - Implemented open-source dependency attribution viewer (`LicenseNoticesDialog`) and resource strings catalog (`strings.xml`).
  - Published design standards (`docs/design-guidelines.md`) and font provenance analysis (`docs/font-provenance.md`).
  - Delivered exactly 15 files across `:shared` module, theme, composeResources, test suites, and documentation (5 conditional font/license files omitted per provenance audit).
  - Validated with 96 :shared:jvmTest tests passing under lenient dependency verification; marked complete administratively; technical review 6.5/10 NOT ACCEPTED with documented integration gaps (admin heuristic, compose.uiTest harness, typography/strings.xml) deferred to Phase 07.

### [0.5.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 05 Durable Local State & Strict Per-Record Sync**:
  - Implemented Room v1 schema with 5 entities: `server_base`, `outbox`, `editor_drafts`, `scan_staging`, and `account_sync_meta` with BundledSQLiteDriver and account-partitioned storage.
  - Implemented atomic freeze CAS transaction (`freezeOutboxAtomic`) preventing race conditions with concurrent local edits.
  - Implemented strict sync client (`HubSyncClient`) mapping strong sync `bootstrap`, `push`, and `pull` endpoints with SHA-256 canonical digest verification.
  - Implemented staged full scans with stable rowId cursors and monotonic promotion, preventing absence-inferred deletions.
  - Implemented per-record FIFO outbox reconciliation preserving `UnknownSuperseded` on conflict after uncertain dispatch.
  - Implemented fail-closed account leave and wipe protocol (`AccountLeaveProtocol`) with generation fencing and wipe-pending markers.
  - Delivered 29 files across `:shared` module, Room schemas, test suites, and documentation.
  - Validated with 89/89 passing JVM unit/Room tests. Android instrumented tests and live Hub end-to-end verification deferred to Phase 07.

### [0.4.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 04 Hub Authentication & Protected Session**:
  - Implemented Hub auth DTOs, endpoint allowlisting, strict-only X-API-Key/X-App-Id header injection, and sanitized SafeError handling.
  - Implemented `HubAuthSession` state machine with generation-fenced transitions, atomic successor vault updates, bounded `/me` access checks, and no automatic POST replays.
  - Implemented `SingleFlightRefreshCoordinator` deduplicating concurrent foreground/background refresh attempts.
  - Implemented Android Keystore AES-GCM credential vault (`AndroidKeystoreVault`) with account-scoped keys, fresh 12-byte nonces, AAD binding, and atomic file replacement.
  - Delivered 14 files across `:shared` module and documentation.
  - Validated with 43/43 passing JVM tests across 7 test suites; approved in code review cycle 3 (score 7/10). Android instrumented test runner and live Hub validation deferred to Phase 07.

### [0.3.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 03 Doggo-only Strict Server & Legacy Isolation**:
  - Implemented isolated strict-v1 schema registration, fail-closed admission helper, and legacy-only mode guard (`_strict_v1_*` namespace).
  - Implemented strong sync routes: `bootstrap`, `push`, and `pull` with exact CAS, single-mutation FIFO semantics, and replay confirmation nonces.
  - Implemented legacy isolation middleware rejecting strict-mode apps on legacy routes with non-401 status.
  - Delivered 26 files across Doggo scripts/docs and Glean Hub Sync plugin (models, services, routes, middleware, admission CLI binary).
  - Executed test suite with 36 passing unit/route tests and clean clippy analysis. Code review cycle 4 approved (score 7/10). Live Mongo integration gates documented as deferred to Phase 07.

### [0.2.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 02 Frozen Foundation & Contracts**:
  - Configured Gradle multiplatform root and `:shared` library module with Gradle 8.11.1 verified wrapper.
  - Pinned stable multiplatform dependency catalog (`gradle/libs.versions.toml`): Kotlin 2.1.10, Compose 1.7.3, AGP 8.7.3, Room 2.7.0-alpha12.
  - Implemented 16 core Kotlin contract files under `dev.doggo.shared.contract` (HubConfig, endpoint policy, Unicode scalar title policy, credential vault & bundle with toString redaction, strict-v1 protocol and NIST SHA-256 canonical digest).
  - Implemented 4 contract test suites under `commonTest` (strict-digest, title-policy, endpoint-policy, account-key).
  - Published foundational architecture and contract documentation (`docs/system-architecture.md`, `docs/api-contract.md`, `docs/code-standards.md`, `docs/toolchain-versions.md`, `docs/foundation-evidence.md`).
  - Executed QA test suite `tests/test-phase-02-foundation.sh` passing 57/57 assertions (100% pass rate).

### [0.1.0-alpha.1] — 2026-10-13
#### Completed
- **Phase 01 Headless Tooling & Android Environment**:
  - Implemented secure Xpra virtual display runner without public TCP exposure.
  - Implemented Android SDK environment exporter with compatible JDK fallback logic.
  - Implemented Android emulator startup script with strict port validation (`5554-5682`, even numbers).
  - Added test suite `tests/test-phase-01-tooling.sh` validating syntax, permissions, CLI handling, host binaries, and documentation structure.
  - Executed QA validation (23/23 passed) and code review cycle 3 (score 8.5/10, live Xpra session verified).
  - Published comprehensive setup runbook (`docs/headless-android-setup.md`) and host evidence record (`docs/host-tooling-evidence.md`).
