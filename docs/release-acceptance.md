# Release & Operations Acceptance Specification

**Document Version**: 2.2.0
**Phase**: Phase 06 — Cross-device acceptance, rollout and documentation (Parent/Child Tasks & Deadline Surfaces)
**Status**: Strict-v3 Parent/Child Hierarchy, Deadline Display Surfaces, Late-Child Sync Reconciliation, and Operator Runbook Delivered; Live Staging & Production Cutover (Gate G7) and Physical Platform/Cluster Acceptance (Gates G2, G3, G5, G6) Formally Held
**Related Documents**: [Strict-v3 Cutover Runbook](./strict-v3-cutover-runbook.md) · [Strict-v2 Cutover Runbook](./strict-v2-cutover-runbook.md) · [System Architecture](./system-architecture.md) · [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md) · [Android Acceptance](./android-acceptance.md) · [Linux Acceptance](./linux-acceptance.md) · [Windows Acceptance](./windows-acceptance.md) · [Project Roadmap](./project-roadmap.md)

---

## 1. Executive Summary & Operational Gate Status

Phase 06 delivers cross-device acceptance evaluation, operational rollout runbooks, and sync recovery hardening for strict-v3 parent/child hierarchies and compact deadline surfaces.

### 1.1 Summary Verdict
- **Automated Regression & Packaging Targets**: Cycle 2 QA reported 749 total verification targets across 15 verification groups: 13 client groups (685 checks: 527 Gradle unit/integration tests [347 shared JVM, 130 desktop, 50 Android] + 158 Node.js script, vector, and static checks) and 2 backend groups (62 unit/integration passed, 1 ignored live MongoDB test, 1 Clippy passed). Gradle suites were executed with `--dependency-verification=off` (diagnostic only).
- **Known Post-Cycle 2 Runtime Issue**: Code Review Cycle 3 identified a deterministic failure in `DesktopTaskSurfacesTest.testStateClearedOnSignedOut` (prior-account sticky state retained on sign-out/runtime close), currently under active remediation.
- **Platform & Cluster Acceptance**: **Formally Held Pending Implementation/Remediation and Testbed Acceptance**. Physical Android Glance widget layout scaling (120x120 to 320x360dp), TalkBack, 200% font scaling, and Doze timing; live desktop D-Bus notification display and Orca navigation; native Windows COM/WinRT toast bridge implementation and Narrator traversal; and multi-node MongoDB replica set live migration remain held on respective hardware testbeds and engineering remediations.
### 1.2 Formal Operational Gate Status (Gates G1–G7)

| Gate ID | Scope & Target | Status | Assessment & Evidence |
|:---:|---|:---:|---|
| **Gate G1** | Core Domain Contracts, Wire Vectors & Digest Preimages | **PASSED** | Strict-v3 17-column canonical schema (`parentTaskId: String?`), checked arithmetic, SHA-256 canonical fingerprint `7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f`, 12/12 strict-v3 vectors and 23/23 strict-v2 vectors verified across Kotlin, Node.js, and Rust. |
| **Gate G2** | Physical Native Platform Delivery & Notification/Surface Acceptance | **FORMALLY HELD (PENDING IMPLEMENTATION & TESTBEDS)** | **Android**: Debug APK compiles, unit tests pass (50/50), but physical launcher Glance widget responsive scaling (120x120 to 320x360dp), TalkBack screen-reader traversal, 200% font scaling, and Doze timing require physical hardware.<br>**Linux**: Distributable compiles, unit tests pass (130/130), but visual bubble display, Orca screen-reader traversal, and session tray recovery require active graphical desktop session.<br>**Windows**: Windows toast bridge remains an unintegrated candidate stub requiring native WinRT/COM implementation; Narrator screen-reader traversal requires physical Windows testbed. |
| **Gate G3** | Multi-Instance Live Hub Cluster Staging Hold | **STAGING HOLD (PENDING BACKEND REMEDIATION & CLUSTER)** | Hub backend sync tests pass 62/62 in `glean-hub-sync` with `test_strong_sync_live_mongo_acceptance_gate` ignored due to absent `MONGO_TEST_URI`. Live multi-node replica set cluster staging rehearsal held; Hub dry-run recovery side-effect in Rust service remains open. |
| **Gate G4** | Storage Migration & Client Version Transition | **PASSED** | Room v2-to-v3 non-destructive migration (`MIGRATION_2_3`), nullable `parentTaskId` backfill, 6-phase client contract transition, and safe V2->V3 intent translation verified across shared test suites. |
| **Gate G5** | Shared Hierarchy, Scheduling & Persisted Eligibility | **LOGIC PASSED / RUNTIME HELD** | Task hierarchy projection, automatic parent completion derivation, ancestor deletion dominance, last-child preservation scheduling barrier (`deleteChildAndPreserveParentAtomic`), and draft-free schedule projection verified in logic/SQLite; desktop controller concurrency edge case under remediation; Windows runtime toast stub held. |
| **Gate G6** | Adversarial Integration & Separately Observed Platform Acceptance | **LOGIC PASSED / DEPLOYMENT HELD** | Late-child sync cleanup Step 2 with per-row DELETE, unresolved children recovery UI section, conflict-review explanatory copy, mixed-group queue drainage (W1), and preservation marker retention (W2) pass in tests; end-to-end multiplatform live network matrix held. |
| **Gate G7** | Coordinated Cutover, Recovery Runbook & Release Gate | **RUNBOOK DELIVERED / CUTOVER BLOCKED** | Authoritative strict-v3 operator runbook (`docs/strict-v3-cutover-runbook.md`) and upgrade CLI (`scripts/hub-strict-upgrade.mjs --target-version strict-v3`) delivered. Live staging and production cutover remain formally blocked pending resolution of Gates G2, G3, G5, and G6, plus formal operator authorization. |

---

## 2. Phase 06 Automated Verification Suite Matrix

Verification groups across TabbyRush multiplatform client and Glean Hub backend were executed under recorded configuration (JDK 21 LTS, Android SDK 35, Gradle 8.11.1, Node.js v24.16.0, Cargo 1.95.0, pnpm 10.28.2). Note: Cycle 2 QA reported 748/749 passing targets (1 ignored live Mongo test) with Gradle `--dependency-verification=off`. Cycle 3 code review subsequently identified a deterministic regression in `DesktopTaskSurfacesTest.testStateClearedOnSignedOut` under active remediation:
| Index | Repository | Command / Suite | Category | Scope | Total | Pass | Fail | Skip / Ignored | Status |
|:---:|---|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `tabbyrush` | `./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Unit / Integration | Shared KMP JVM & SQLite Suite (41 classes) | 347 | 347 | 0 | 0 | PASS |
| 2 | `tabbyrush` | `./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | Unit / Platform | Desktop App & Surface Suite (16 classes) | 130 | 130 | 0 | 0 | PASS* |
| 3 | `tabbyrush` | `./gradlew :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks` | Unit / Platform | Android Debug Unit Suite (8 classes) | 50 | 50 | 0 | 0 | PASS |
| 4 | `tabbyrush` | `node scripts/strict-v3-vector-runner.mjs` | Contract Vectors | Strict v3 Wire Vectors & Schema Fingerprint | 12 | 12 | 0 | 0 | PASS |
| 5 | `tabbyrush` | `node scripts/strict-v2-vector-runner.mjs` | Contract Vectors | Strict v2 Wire Vectors & Schema Fingerprints | 23 | 23 | 0 | 0 | PASS |
| 6 | `tabbyrush` | `node scripts/hub-live-smoke.mjs --fixture-test` | Protocol Fixture | Hub Offline Protocol Smoke Harness (5 suites) | 18 | 18 | 0 | 0 | PASS |
| 7 | `tabbyrush` | `node scripts/release-security-check.mjs --diagnose-source` | Security Diagnostic | Static Manifest & Secret Audit | 5 | 5 | 0 | 0 | PASS |
| 8 | `tabbyrush` | `node scripts/hub-strict-upgrade.test.mjs` | Integration CLI | Hub Strict Upgrade CLI Harness | 13 | 13 | 0 | 0 | PASS |
| 9 | `tabbyrush` | `node scripts/debug-fault-proxy.test.mjs` | Fault Injection | Debug Fault Proxy Route & Redaction Suite | 8 | 8 | 0 | 0 | PASS |
| 10 | `tabbyrush` | `node scripts/admin-announcements-smoke.mjs --self-test` | Announcements Smoke | Golden Vectors & Mock Smoke Suite | 34 | 34 | 0 | 0 | PASS |
| 11 | `tabbyrush` | `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | Tooling Rehearsal | Strict-v2 Upgrade CLI Rehearsal Suite | 8 | 8 | 0 | 0 | PASS |
| 12 | `tabbyrush` | `node --test scripts/release-security-check.test.mjs` | Security Unit | Security Auditor Manifest Suite | 24 | 24 | 0 | 0 | PASS |
| 13 | `tabbyrush` | `node --test scripts/bump-version.test.mjs` | Tooling Unit | Version Bump Automation Suite | 13 | 13 | 0 | 0 | PASS |
| 14 | `glean-hub` | `cargo test -p glean-hub-sync --locked` | Backend Integration | Hub Sync Crate Tests | 63 | 62 | 0 | 1 | PASS** |
| 15 | `glean-hub` | `cargo clippy -p glean-hub-sync --locked` | Static Analysis | Rust Code Quality / Linter | 1 | 1 | 0 | 0 | PASS |

\* *Desktop suite passed 130/130 in Cycle 2 QA run; Cycle 3 code review identified `testStateClearedOnSignedOut` regression under active remediation.*  
\*\* *1 ignored: `test_strong_sync_live_mongo_acceptance_gate` (requires live MongoDB at `MONGO_TEST_URI`; Gate G3 staging hold).*  
**Grand Total**: Cycle 2 QA reported 749 verification targets across 15 groups: 747 automated test, contract, and rehearsal checks (746 passed, 0 failures, 1 ignored); plus 2 static/packaging targets passed (release security diagnostic, Clippy). Gradle suites executed with `--dependency-verification=off` (diagnostic only).

## 3. Acceptance Criteria Verification Matrix (AC01–AC12)

In accordance with Phase 06 plan specifications and the authoritative acceptance matrix:

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

## 4. Production Remediations & Boundary Hardening Verified in Phase 06

Six concrete remediations and operational deliveries were verified during Phase 06 implementation and QA:

1. **Mixed-Group Queue Drainage (Remediation W1)**:
   - When deleting a group where the root is synced but child `CREATE` operations remain pending in the local outbox, leaf child rows are cancelled locally (`cancelUnsentGroupAtomic`) or drained transactionally without stranding operations in `[create:PENDING, delete:PENDING]`, preventing uncleared mutation guards.
2. **Preservation UPDATE Coalescing & Marker Retention (Remediation W2)**:
   - Ensured preservation `UPDATE` does not bypass mutation coalescing. Subsequent manual root edits coalescing into a pending preservation op retain the `INTENT_LAST_CHILD_PRESERVATION` intent mask and client-local scheduling barrier (`hasUnresolvedPreservationBarrier = true`) until accepted or discarded.
3. **Sync-Layer Late-Child Cleanup & Tombstone Dominance**:
   - `AccountSyncCoordinator.reconcileLateChildren` performs locked check-then-act verification. When an orphaned child arrives concurrently whose parent root is tombstoned, it enqueues an ordinary per-row DELETE using the confirmed server revision, suppressing the orphaned child from normal task lists and reminder schedules.
4. **Unresolved-Child Recovery UI**:
   - Added a dedicated recovery section in `TodoList` (`todo-list.kt`) and `ConflictReviewDialog` (`conflict-review.kt`) rendering tasks whose parent is absent without a tombstone, isolating them from normal hierarchy and schedules without auto-promotion or deletion.
5. **Conflict-Review Explanatory Copy**:
   - Added user-facing explanatory text in `conflict-review.kt` explaining CAS conflicts, preservation barriers, and partial group outcomes.
6. **Strict-v3 Cutover Runbook & Upgrade CLI**:
   - Delivered `docs/strict-v3-cutover-runbook.md` with backup topologies, same-ID resume semantics, schema fingerprints, and read-back assertions.
   - Updated `scripts/hub-strict-upgrade.mjs` to support `--target-version strict-v3` with 17-column read-back validation.
---

## 5. Formal Platform & Infrastructure Boundaries

1. **Strict Dependency Verification Disclosure & Supply-Chain Integrity**:
   - Executing Gradle builds without `--dependency-verification=off` fails during project configuration with 149 unmatched classpath checksums in `gradle/verification-metadata.xml` (affecting AGP 8.7.3 and Kotlin 2.1.10 tooling jars). All Gradle tasks are currently executed with `--dependency-verification=off` for diagnostic purposes.
   - *Security Protocol*: Resolving this hold requires auditing artifact provenance, verifying signatures, and updating verification metadata to run with `--dependency-verification=strict`.
2. **Glance Provenance Hold**:
   - Android Glance 1.2.0 integration retains an open dependency provenance hold pending security verification of third-party transitive dependencies.
3. **Gate G2 (Physical Connected Device & Native Platform Acceptance)**:
   - *Android*: Connected physical device/emulator execution, Glance widget layout scaling across 120x120, 240x120, 240x240, and 320x360dp, TalkBack screen-reader traversal, dynamic 200% font scaling, and Doze mode timing remain formally pending Gate G2.
   - *Linux Desktop*: Headless workstation lacks an active Freedesktop D-Bus notification daemon and graphical display. Visual notification bubble display, Orca screen-reader traversal, and tray recovery remain pending Gate G2.
   - *Windows Desktop*: `JnaWindowsToastBridge` remains an unintegrated candidate stub returning `WindowsNotificationStatus.Unavailable`. Native WinRT/COM toast posting, installed MSI execution, and Narrator screen-reader traversal require physical Windows testbed validation.
4. **Gate G3 (Multi-Instance Live Hub Cluster Staging Hold)**:
   - Backend unit and integration tests pass in `glean-hub-sync`, but `test_strong_sync_live_mongo_acceptance_gate` is ignored due to absent `MONGO_TEST_URI`. Live multi-node MongoDB replica set staging rehearsal remains held.
   - Upstream Glean Hub Rust service contains an open finding regarding dry-run recovery side-effects on already-upgraded targets.
5. **Gate G5 (Desktop Controller Concurrency Remediation)**:
   - Code Review Cycle 3 identified a deterministic regression in `DesktopTaskSurfacesTest.testStateClearedOnSignedOut` where desktop sticky state retains prior-account data upon sign-out / runtime close. Active remediation is in progress.
6. **Gate G6 (Phase 06 Adversarial Integration Acceptance)**:
   - Automated unit, file-backed SQLite persistence, vector contract, and build packaging tests pass in offline suites. End-to-end multi-platform live network matrix acceptance remains held pending physical platform implementation/remediation and cluster testbed availability.
7. **Gate G7 (Production Cutover Gate)**:
   - Authoritative strict-v3 operator runbook delivered. Live staging and production cutover remain blocked pending resolution of Gates G2, G3, G5, G6, and operator authorization.

---

## 6. Phase 06 Production Release Prerequisite Checklist

Prior to authorizing production cutover or public distribution, the following prerequisites must be satisfied:

1. **Desktop Controller Concurrency Remediation (Phase 06 Blocker)**:
   - Resolve deterministic failure in `DesktopTaskSurfacesTest.testStateClearedOnSignedOut` ensuring that desktop sticky state properly clears prior-account sections and quick-add text upon sign-out / runtime close.
2. **Supply Chain Verification Metadata Audit & Enforcement (Inherited Hold)**:
   - Investigate the 149 unmatched tooling checksums, verify upstream package provenance and developer keys, review the metadata diff, and verify that `./gradlew assembleRelease` and `./gradlew test` pass cleanly with strict verification enforced (`--dependency-verification=strict`).
3. **Glance Dependency Provenance Clearance (Inherited Hold)**:
   - Complete provenance and licensing audit for Android Glance 1.2.0 transitive dependencies.
4. **Staging Cluster Upgrade Rehearsal (Gate G3 Prerequisites)**:
   - Provision multi-node MongoDB replica set staging cluster, export `MONGO_TEST_URI`, and execute live migration rehearsal using `scripts/hub-strict-upgrade.mjs --expected-version strict-v2 --target-version strict-v3`.
   - Verify closure of upstream dry-run recovery side-effects in Glean Hub Rust service.
5. **Physical Android Connected Acceptance & Accessibility (Gate G2)**:
   - Execute instrumented tests on physical Android device / emulator.
   - Verify Glance widget responsive layout scaling across 120x120, 240x120, 240x240, and 320x360dp viewports.
   - Validate accessibility: TalkBack screen-reader navigation and dynamic 200% font scaling on compact widget layouts.
   - Verify Doze mode exact alarm delivery (`adb shell dumpsys deviceidle force-idle`) and reboot restoration (`BOOT_COMPLETED`).
6. **Interactive Linux Desktop Acceptance & Accessibility (Gate G2)**:
   - Verify visual notification bubble display, tray recovery, and click handling under an active graphical D-Bus session daemon (GNOME Shell, dunst, or mako).
   - Validate Orca screen-reader accessibility on desktop UI.
7. **Native Windows Toast Bridge Implementation & Accessibility (Gate G2)**:
   - Replace `JnaWindowsToastBridge` stub with native WinRT/COM toast bridge implementation.
   - Validate installed MSI execution, Action Center toast clearance, and Narrator screen-reader navigation on physical Windows 10/11 testbed.
8. **Gate G7 Production Operator Authorization**:
   - Verify operator JWT authority and execute journaled pre-migration MongoDB backup per `docs/strict-v3-cutover-runbook.md` prior to executing production upgrade commands.

