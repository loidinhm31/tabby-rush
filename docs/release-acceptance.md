# Release & Operations Acceptance Specification

**Document Version**: 2.1.0
**Phase**: Phase 12 — Coordinated Cutover, Recovery Runbook & Release Documentation
**Status**: Operational Cutover Runbook & Tooling Rehearsal Delivered; Live Production Cutover (Gate G7) Formally Blocked Pending Resolution of Gates G2, G3, and G6
**Related Documents**: [Strict-v2 Cutover Runbook](./strict-v2-cutover-runbook.md) · [System Architecture](./system-architecture.md) · [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md) · [Android Acceptance](./android-acceptance.md) · [Linux Acceptance](./linux-acceptance.md) · [Windows Acceptance](./windows-acceptance.md) · [Project Roadmap](./project-roadmap.md)

---

## 1. Executive Summary & Operational Gate Status

Phase 11 delivers comprehensive adversarial boundary regressions, file-backed SQLite close/reopen persistence suites, loopback debug fault-injection tooling, and verified desktop and Android packaging builds.

### 1.1 Summary Verdict
- **Automated Regression & Packaging Targets**: 100% PASS across all executable verification targets (397 total reported targets across 14 verification groups: 393 automated test and contract checks [392 passed, 0 failures, 1 ignored live MongoDB test in `glean-hub-sync` when `MONGO_TEST_URI` is unset], plus 4 build, lint, and packaging targets passed).
- **Platform & Cluster Acceptance**: **Formally Held Pending Implementation/Remediation and Testbed Acceptance**. Physical Android Doze timing (`setExactAndAllowWhileIdle`), TalkBack and 200% font scaling, live desktop D-Bus notification visual display and Orca navigation, native Windows COM/WinRT toast bridge implementation and Narrator traversal, and multi-node MongoDB replica set live migration with Phase 03 backend remediations remain held on respective hardware testbeds and engineering remediations.

### 1.2 Formal Operational Gate Status (Gates G1–G6)

| Gate ID | Scope & Target | Status | Assessment & Evidence |
|:---:|---|:---:|---|
| **Gate G1** | Core Domain Contracts, Wire Vectors & Digest Preimages | **PASSED** | Strict-v2 wire models, checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`), DST gap/overlap resolution, and canonical schema fingerprints verified across Kotlin, Rust, and Node.js vector runners (23/23 vector checks pass). |
| **Gate G2** | Physical Native Platform Delivery & Notification Acceptance | **FORMALLY HELD (PENDING IMPLEMENTATION & TESTBEDS)** | **Android**: Debug APK compiles (`:androidApp:assembleDebug`), unit tests pass (`:androidApp:testDebugUnitTest`), but live connected device Doze timing (`setExactAndAllowWhileIdle`), reboot restoration broadcasts (`BOOT_COMPLETED`), system notification center banners, TalkBack screen-reader traversal, and 200% font scaling require physical hardware testbed.<br>**Linux**: Linux app distributable bundle compiles (`:desktopApp:createDistributable`), unit tests pass (`:desktopApp:test`), but headless workstation lacks an active Freedesktop D-Bus notification daemon (`org.freedesktop.Notifications`) for visual bubble display and Orca screen-reader validation.<br>**Windows**: Unit tests pass (`:desktopApp:test`), but `JnaWindowsToastBridge` remains an unintegrated candidate stub returning `WindowsNotificationStatus.Unavailable`; native WinRT/COM toast posting and Narrator screen-reader validation require implementation and physical Windows testbed. |
| **Gate G3** | Multi-Instance Live Hub Cluster Staging Hold | **STAGING HOLD (PENDING BACKEND REMEDIATION & CLUSTER)** | Hub backend unit and integration tests pass 54/54 in `glean-hub-sync`, but `test_strong_sync_live_mongo_acceptance_gate` is ignored due to absent `MONGO_TEST_URI`. In addition, 3 open Phase 03 code findings (permit release duplicate filter, `permitCount` backfill in legacy documents, and batch/checkpoint atomicity) remain open engineering prerequisites prior to live replica set cluster testing. |
| **Gate G4** | Storage Migration & Client Version Transition | **PASSED** | Room v1-to-v2 non-destructive migration (`RoomMigrationV1ToV2Test`), 6-phase client contract transition state machine (`ContractTransitionCoordinator`), and legacy receipt reconciliation verified across shared test suites. |
| **Gate G5** | Shared Scheduling, Persisted Eligibility & Teardown Barrier | **PASSED** | Checked 64-bit arithmetic, 1..10 repeat bounds, strict 300,000ms grace window, reactive projection without draft leakage, durable occurrence lifecycle, and fail-closed account leave teardown barrier verified on JVM BundledSQLite (`ReminderTeardownBarrierSqliteTest`). |
| **Gate G6** | Adversarial Integration & Separately Observed Platform Acceptance | **LOGIC PASSED / DEPLOYMENT HELD** | Automated boundary logic, file-backed SQLite restart invariance, vectors, and build packaging verified 100% (Cycle 2 review approved at 8.6/10). End-to-end multiplatform acceptance and cluster execution formally held pending platform implementation/remediation and testbed availability. |
| **Gate G7** | Coordinated Cutover, Recovery Runbook & Release Gate | **RUNBOOK DELIVERED / CUTOVER BLOCKED** | Authoritative operator runbook (`docs/strict-v2-cutover-runbook.md`) and tooling rehearsal harness (`scripts/strict-v2-cutover-rehearsal.mjs`, 7/7 checks PASS) delivered. Live production cutover remains formally blocked pending resolution of Gates G2, G3, and G6, plus formal operator authorization. |

---

## 2. Phase 11 Automated Verification Suite Matrix

All verification groups across TabbyRush multiplatform client and Glean Hub backend were executed under recorded configuration (JDK 21 LTS, Android SDK 35, Gradle 8.11.1, Node.js v24.16.0, Cargo 1.95.0, pnpm 10.28.2):

| Index | Repository | Command / Suite | Category | Scope | Total | Pass | Fail | Skip / Ignored | Status |
|:---:|---|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `tabbyrush` | `./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Unit / Integration | Shared KMP JVM & SQLite Suite (28 classes) | 202 | 202 | 0 | 0 | PASS |
| 2 | `tabbyrush` | `./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | Unit / Platform | Desktop App & Lifecycle Suite (10 classes) | 49 | 49 | 0 | 0 | PASS |
| 3 | `tabbyrush` | `./gradlew :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks` | Unit / Platform | Android Debug Unit Suite (4 classes) | 12 | 12 | 0 | 0 | PASS |
| 4 | `tabbyrush` | `node scripts/strict-v2-vector-runner.mjs` | Contract Vectors | Strict v2 Wire Vectors & Schema Fingerprints | 23 | 23 | 0 | 0 | PASS |
| 5 | `tabbyrush` | `node scripts/hub-live-smoke.mjs --fixture-test` | Protocol Fixture | Hub Offline Protocol Smoke Harness (5 suites) | 18 | 18 | 0 | 0 | PASS |
| 6 | `tabbyrush` | `node scripts/release-security-check.mjs --diagnose-source` | Security Diagnostic | Static Manifest & Secret Audit | 6 | 6 | 0 | 0 | PASS |
| 7 | `tabbyrush` | `node scripts/hub-strict-upgrade.test.mjs` | Integration CLI | Hub Strict Upgrade CLI Harness | 12 | 12 | 0 | 0 | PASS |
| 8 | `tabbyrush` | `node scripts/debug-fault-proxy.test.mjs` | Fault Injection | Debug Fault Proxy Route & Redaction Suite | 8 | 8 | 0 | 0 | PASS |
| 9 | `tabbyrush` | `./gradlew :desktopApp:createDistributable --dependency-verification=off` | Packaging Build | Desktop Linux App Distributable Bundle | 1 | 1 | 0 | 0 | PASS |
| 10 | `tabbyrush` | `./gradlew :androidApp:assembleDebug --dependency-verification=off` | Packaging Build | Android Debug APK Compilation (20.6 MB) | 1 | 1 | 0 | 0 | PASS |
| 11 | `glean-hub` | `cargo test -p glean-hub-sync --locked` | Backend Integration | Hub Sync Crate Tests | 55 | 54 | 0 | 1 | PASS* |
| 12 | `glean-hub` | `cargo clippy -p glean-hub-sync --locked` | Static Analysis | Rust Code Quality / Linter | 1 | 1 | 0 | 0 | PASS |
| 13 | `glean-hub` | `pnpm test:unit` (in `glean-hub-app`) | Unit | Admin UI Service & Vite Preload Suite | 13 | 13 | 0 | 0 | PASS |
| 14 | `glean-hub` | `pnpm build` (in `glean-hub-app`) | Frontend Packaging | TypeScript Compilation & Vite Production Build | 1 | 1 | 0 | 0 | PASS** |
| 15 | `tabbyrush` | `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | Tooling Rehearsal | Strict-v2 Upgrade CLI Rehearsal & Redaction Suite | 8 | 8 | 0 | 0 | PASS |

\* *1 ignored: `test_strong_sync_live_mongo_acceptance_gate` (requires live MongoDB at `MONGO_TEST_URI`; Gate G3 staging hold).*  
\*\* *Vite production build succeeded in 11.20s with dynamic import chunking warnings in embedded apps.*  
**Grand Total**: 410 reported verification targets across 15 groups (406 automated test, contract, and rehearsal checks: 405 passed, 0 failures, 1 ignored; plus 4 build, lint, and packaging targets passed).

---

## 3. Acceptance Criteria Verification Matrix (AC01–AC12)

In accordance with Phase 11 plan specifications and the authoritative acceptance matrix:

| AC ID | Scenario & Core Requirement | Automated Test Evidence | Platform Acceptance Status |
|:---:|---|---|---|
| **AC01** | Offline create/edit/clear date-only/timed rule; Room migration retains queue/records across restart. | 23 tests in `RoomAccountStoreTest`, `RoomMigrationV1ToV2Test`, `AdversarialSyncSqliteTest` PASS | **Partially Validated (Logic & DB PASS)**; Physical device reboot restart pending Gate G2. |
| **AC02** | Repetition bounds (1..10), interval >= 15m, checked arithmetic overflow, date-only 09:00 overdue logic. | 9 tests in `ReminderScheduleCalculatorTest`, 7 in `AdversarialSchedulerBoundaryTest` PASS | **Validated (Contract & Logic PASS)**. |
| **AC03** | DST gap rejected, overlap requires explicit offset; NY spring/fall instant calculations. | 7 tests in `StrictTimeZoneResolverTest`, 15 in `TodoSemanticsTest` PASS | **Validated (Contract & Resolver PASS)**; Live device OS clock jump observation pending Gate G2. |
| **AC04** | Upgrade same pair via browser & CLI; token redaction; app-scoped admin auth. | 9 tests in `hub-strict-upgrade.test.mjs`, 13 in `glean-hub-app`, 3 middleware tests in Rust PASS | **Validated (Harness & Service PASS)**; Live cluster multi-instance deployment pending Gate G3. |
| **AC05** | Interrupted migration crash recovery; two-node concurrent fence exclusion. | 9 tests in `hub-strict-upgrade.test.mjs`, coordination doc tests in Rust PASS | **Partially Validated (CLI State Machine PASS)**; Live multi-node MongoDB replica set execution pending Gate G3. |
| **AC06** | Old client offline upgrade, receipt replay, `UNKNOWN_SUPERSEDED` explicit conflict. | 12 tests in `OutboxReconciliationTest`, 4 in `ContractTransitionTest`, 5 in `AdversarialSyncSqliteTest` PASS | **Validated (Logic & SQLite Replay PASS)**; Multi-device live network cutover pending Gate G6. |
| **AC07** | Android <-> Linux <-> Windows 3-device round-trip sync and intent-masked rebase. | 12 tests in `OutboxReconciliationTest`, 5 in `AdversarialSyncSqliteTest` PASS | **Partially Validated (Outbox Engine PASS)**; Live concurrent 3-device network sync pending Gate G6. |
| **AC08** | Offline reminder posts after process kill and Doze; reboot restore without opening activity. | 6 tests in `AndroidNotificationAdapterTest`, 3 in `CompositeLeaveSchedulerCancelGateTest` PASS | **BLOCKED (Gate G2)**; Requires physical connected Android device/emulator with battery optimization bypass. |
| **AC09** | `POST_NOTIFICATIONS` deny/channel-off=blocked; exact alarm access absent=bestEffort. | 2 tests in `ReminderPermissionControllerTest`, 6 in `AndroidNotificationAdapterTest` PASS | **BLOCKED (Gate G2)**; Requires live OS permission revocation and notification settings toggle. |
| **AC10** | Packaged Linux notification & installed Windows toast appear; process exit leaves no surviving daemon. | 6 tests in `LinuxReminderAdapterTest`, 6 in `DesktopReminderSchedulerTest`, distributable build PASS | **BLOCKED (Gate G2)**; Headless workstation lacks active D-Bus notification daemon; Windows toast bridge is unimplemented stub. |
| **AC11** | Stale occurrence invalidated on edit/delete/logout; wipe admitted before post prevents leaks. | 1 test in `ReminderTeardownBarrierSqliteTest`, 7 in `AccountLeaveProtocolTest`, 4 in `ReminderDeliveryGateTest` PASS | **Validated (Logic & Barrier PASS)**; Live OS notification center banner removal pending Gate G2. |
| **AC12** | At due+300,000ms posts once; +300,001ms skips; clock jump catches no burst. | 9 tests in `ReminderScheduleCalculatorTest`, 6 in `DesktopReminderSchedulerTest` PASS | **Validated (Contract & Grace Logic PASS)**; Physical device system sleep/wake timing pending Gate G2. |

---

## 4. Production Remediations & Boundary Hardening Verified in Phase 11

Four concrete code and test remediations were verified during Phase 11 QA validation and code review:

1. **`PersistedScheduleProjector` Schedule Token Assignment**:
   - Fixed outbox fold in `PersistedScheduleProjector`: `scheduleToken = op.scheduleToken` is now correctly assigned when `deadline != null && op.scheduleToken != null` under `INTENT_REMINDER` updates.
   - Verified via `testScheduleTokenAssignmentAndPreservation` in `AdversarialSchedulerBoundaryTest.kt`: non-temporal edits (title only) preserve existing tokens without alarm churn, reminder edits advance tokens to trigger rescheduling, and deadline clearing resets tokens to null.
2. **`debug-fault-proxy.mjs` Route Allowlist & Sensitive Log Redaction**:
   - Expanded route allowlist to include `/api/v1/sync/tabby-rush/strong/legacy-receipts`.
   - Suppressed sensitive path leakage: unlisted path rejections (403) log the HTTP method only, completely omitting client URL path strings. Verified via active `console.warn` interception in `debug-fault-proxy.test.mjs`.
   - Added `drop-legacy-receipts-response` scenario for fault-injection testing. Client sockets are destroyed on drop to model upstream completion with client hangup.
3. **Temporal Fixture Standardization Across Test Suites**:
   - Corrected `America/New_York` offset for `2026-11-01 15:00` from `-14400L` (EDT) to `-18000L` (EST) across `AdversarialSchedulerBoundaryTest.kt` and `AdversarialSyncSqliteTest.kt` (lines 85, 123, 144, 230, 273), eliminating contradictions between epoch timestamps and timezone offsets around the 02:00 DST fallback.
4. **Production Overdue Logic Validation**:
   - `AdversarialSchedulerBoundaryTest.kt` refactored to exercise the production `DeadlineCalculatorHelpers.validateAndComputeDeadline` path, validating `anchorEpochMs = 1793541600000L` (09:00:00 EST), `overdueEpochMs = 1793595600000L` (next day start EST), and production overdue predicate logic.

---

## 5. Formal Platform & Infrastructure Boundaries

1. **Strict Dependency Verification Disclosure & Supply-Chain Integrity**:
   - Executing Gradle builds without `--dependency-verification=off` fails during project configuration with 149 unmatched classpath checksums in `gradle/verification-metadata.xml` (affecting AGP 8.7.3 and Kotlin 2.1.10 tooling jars). All Gradle tasks are currently executed with `--dependency-verification=off`.
   - *Security Protocol*: Simply regenerating metadata (`--write-verification-metadata sha256`) records whatever artifacts happen to be resolved; it does not authenticate them. Phase 12 cutover must investigate the 149 checksum mismatches, verify artifact provenance and cryptographic signatures against trusted upstream repositories, review the metadata diff, and validate that builds succeed with strict verification enforced (`--dependency-verification=strict`).
2. **Gate G2 (Physical Connected Device & Native Platform Acceptance)**:
   - *Android*: Connected physical device/emulator execution, Doze mode idle quotas (`setExactAndAllowWhileIdle`), live lockscreen notification masking, system reboot broadcast restoration, TalkBack screen-reader traversal, and dynamic 200% font scaling remain formally pending Gate G2.
   - *Linux Desktop*: Headless workstation lacks an active Freedesktop D-Bus notification daemon (e.g., dunst, mako, gnome-shell). Visual notification bubble display, click handling, and Orca screen-reader traversal remain pending Gate G2 on a graphical desktop session.
   - *Windows Desktop*: `JnaWindowsToastBridge` remains an unintegrated candidate stub returning `WindowsNotificationStatus.Unavailable`. Native WinRT/COM toast posting, installed MSI execution, and Narrator screen-reader traversal require implementation and physical Windows testbed validation.
3. **Gate G3 (Multi-Instance Live Hub Cluster Staging Hold)**:
   - Backend unit and integration tests pass 54/54 in `glean-hub-sync`, but `test_strong_sync_live_mongo_acceptance_gate` is ignored due to absent `MONGO_TEST_URI`.
   - *Backend Remediations Required*: In addition to provisioning a live multi-node replica set cluster, three open Phase 03 code findings must be remediated in `glean-hub-sync` prior to cluster admission:
     1. Unconditional permit release counter decrement without filtering on `activePermits.<permitId>: { $exists: true }`.
     2. Missing `permitCount` backfill in legacy coordination documents for numeric `$lt` predicates.
     3. Batch mutation and checkpoint update non-atomicity with ignored checkpoint errors.
4. **Gate G6 (Phase 11 Adversarial Integration Acceptance)**:
   - Automated unit, file-backed SQLite persistence, vector contract, and build packaging tests pass 100%. End-to-end multi-platform live matrix acceptance remains held pending physical platform implementation/remediation and cluster testbed availability.

---

## 6. Phase 12 Production Release Prerequisite Checklist

Prior to authorizing production cutover or public distribution, the following prerequisites must be satisfied:

1. **Phase 07 Android Correctness Defect Remediation**:
   - Confirm and verify specific closure evidence for historical Phase 07 runtime correctness defects in `:androidApp`:
     - Teardown generation rollback during interrupted account logout.
     - WorkManager cancellation exception swallowing.
     - Worker TOCTOU race between generation check and database write.
2. **Supply Chain Verification Metadata Audit & Enforcement**:
   - Investigate the 149 unmatched tooling checksums, verify upstream package provenance and developer keys, review the metadata diff, and verify that `./gradlew assembleRelease` and `./gradlew test` pass cleanly with strict verification enforced (`--dependency-verification=strict`).
3. **Phase 03 Backend Remediation (Gate G3 Prerequisites)**:
   - Filter permit releases on `activePermits.<permitId>: { $exists: true }` in `glean-hub-sync`.
   - Backfill `permitCount` on legacy coordination documents.
   - Guarantee atomicity between batch mutations and checkpoint updates.
4. **Staging Cluster Acceptance (Gate G3)**:
   - Provision multi-node MongoDB replica set, export `MONGO_TEST_URI`, and execute `cargo test -p glean-hub-sync -- --ignored test_strong_sync_live_mongo_acceptance_gate`.
5. **Physical Android Connected Acceptance & Accessibility (Gate G2)**:
   - Execute instrumented tests on physical Android device / emulator.
   - Verify Doze mode exact alarm delivery (`adb shell dumpsys deviceidle force-idle`) and reboot restoration (`BOOT_COMPLETED`).
   - Validate accessibility: TalkBack screen-reader navigation and dynamic 200% font scaling on compact layouts.
   - Execute production signing (`:androidApp:assembleRelease`) with private keystore and verify via `apksigner verify --verbose`.
6. **Interactive Linux Desktop Acceptance & Accessibility (Gate G2)**:
   - Verify visual notification bubble display, Action Center clearance, and click handling under an active D-Bus session daemon (GNOME Shell, dunst, or mako).
   - Validate Orca screen-reader accessibility on desktop UI.
7. **Native Windows Toast Bridge Implementation & Accessibility (Gate G2)**:
   - Replace `JnaWindowsToastBridge` stub with native WinRT/COM toast bridge implementation.
   - Validate installed MSI execution, Action Center toast clearance, and Narrator screen-reader navigation on physical Windows 10/11 testbed.
