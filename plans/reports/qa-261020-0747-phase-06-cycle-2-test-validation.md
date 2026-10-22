# QA Test Report: Phase 06 Cycle 2 Test Validation

- **Target:** Phase 06 — Cross-device acceptance, rollout and documentation after Cycle 2
- **Date:** 2026-10-20
- **Runner:** Phase06TesterPostFix (Senior QA Engineer)
- **Environment:** Linux x86_64, JDK 21 (`/home/loidinh/.local/share/jvm/jdk-21`), Node v20+, Gradle offline mode (`--dependency-verification=off --offline --rerun-tasks`)

---

## 1. Test Results Overview

| Suite / Runner | Type | Total Run | Passed | Failed | Skipped | Pass Rate | Status |
|---|---|---|---|---|---|---|---|
| `:shared:jvmTest` | Gradle KMP Unit / SQLite | 347 | 347 | 0 | 0 | 100% | PASS |
| `:desktopApp:test` | Gradle JVM Unit / Surface | 130 | 130 | 0 | 0 | 100% | PASS |
| `:androidApp:testDebugUnitTest` | Gradle Android Unit / Widget | 50 | 50 | 0 | 0 | 100% | PASS |
| **Gradle Subtotal** | **All Modules (--rerun-tasks)** | **527** | **527** | **0** | **0** | **100%** | **PASS** |
| `hub-live-smoke.mjs --fixture-test` | Offline Protocol Fixture | 18 | 18 | 0 | 0 | 100% | PASS |
| `strict-v2-cutover-rehearsal.mjs --self-test` | Rehearsal / Mock Self-Test | 8 | 8 | 0 | 0 | 100% | PASS |
| `admin-announcements-smoke.mjs --self-test` | Golden Vectors + Mock Smoke | 34 | 34 | 0 | 0 | 100% | PASS |
| `release-security-check.mjs --diagnose-source` | Static Manifest & Source Audit | 5 | 5 | 0 | 0 | 100% | PASS |
| `strict-v2-vector-runner.mjs` | Wire Vectors & Schema (2+21) | 23 | 23 | 0 | 0 | 100% | PASS |
| `strict-v3-vector-runner.mjs` | Wire Vectors & Schema (1+11) | 12 | 12 | 0 | 0 | 100% | PASS |
| `hub-strict-upgrade.test.mjs` | CLI & Migration Unit Tests | 13 | 13 | 0 | 0 | 100% | PASS |
| `release-security-check.test.mjs` etc. | Security & Helper Unit Tests | 45 | 45 | 0 | 0 | 100% | PASS |
| **Grand Total** | **All Test Suites & Runners** | **685** | **685** | **0** | **0** | **100%** | **PASS** |

---

## 2. Detailed Suite Breakdown

### `:shared:jvmTest` (347 tests, 41 suites, 0 failures, 0 skipped)
- `HubAuthClientTest`: 9/9 PASS
- `HubAuthSessionTest`: 8/8 PASS
- `SingleFlightRefreshTest`: 3/3 PASS
- `AccountKeyTest`: 5/5 PASS
- `AdversarialSchedulerBoundaryTest`: 7/7 PASS
- `EndpointPolicyTest`: 8/8 PASS
- `PersistedScheduleProjectionTest`: 9/9 PASS
- `ReminderScheduleCalculatorTest`: 9/9 PASS
- `RemoteContractVectorTest`: 8/8 PASS
- `StrictDigestTest`: 5/5 PASS
- `StrictTimeZoneResolverTest`: 7/7 PASS
- `StrictV2DigestExtendedTest`: 6/6 PASS
- `StrictV2DigestVectorTest`: 5/5 PASS
- `StrictV3DigestVectorTest`: 10/10 PASS
- `TaskDeadlinePresentationTest`: 3/3 PASS
- `TaskDeadlineReminderModelsTest`: 6/6 PASS
- `TaskHierarchyProjectorTest`: 8/8 PASS
- `TaskSurfaceProjectorTest`: 13/13 PASS
- `TitlePolicyTest`: 6/6 PASS
- `AdversarialSyncSqliteTest`: 5/5 PASS
- `ReminderOccurrenceStoreTest`: 4/4 PASS
- `ReminderSchedulingCoordinatorSqliteSmokeTest`: 2/2 PASS
- `ReminderTeardownBarrierSqliteTest`: 1/1 PASS
- `RoomAccountStoreTest`: 41/41 PASS (W1/W2/preservation/unresolved child coverage)
- `RoomMigrationV1ToV2Test`: 1/1 PASS
- `RoomMigrationV2ToV3Test`: 2/2 PASS
- `TaskSurfaceStoreSqliteTest`: 10/10 PASS
- `TaskSurfaceSyncSqliteTest`: 4/4 PASS
- `RemoteInboxClientTest`: 10/10 PASS
- `RemoteInboxControllerTest`: 7/7 PASS
- `RemoteStreamClientTest`: 8/8 PASS
- `AccountLeaveProtocolTest`: 7/7 PASS
- `AccountSyncCoordinatorTest`: 15/15 PASS (includes late-child cleanup + conflict tests)
- `ContractTransitionTest`: 6/6 PASS
- `HubSyncClientTest`: 12/12 PASS
- `OutboxReconciliationTest`: 16/16 PASS
- `ReminderDeliveryGateTest`: 4/4 PASS
- `StagedFullScanTest`: 19/19 PASS
- `DateTimePickersTest`: 5/5 PASS
- `RemoteInboxNavigationTest`: 14/14 PASS
- `TodoSemanticsTest`: 19/19 PASS

### `:desktopApp:test` (130 tests, 16 suites, 0 failures, 0 skipped)
- `DesktopTaskSurfacesTest`: 23/23 PASS (+1 new test over Cycle 1: `testStartupOrderControllerConstructedBeforeAccountAuthentication`)
- `AppIconAssetsTest`: 4/4 PASS
- `CompositeDesktopLeaveCancelGateTest`: 3/3 PASS
- `DesktopControllerEnvTest`: 4/4 PASS
- `DesktopLifecycleTest`: 8/8 PASS
- `DesktopReminderControllerLifecycleTest`: 3/3 PASS
- `DesktopReminderSchedulerTest`: 6/6 PASS
- `DesktopRemoteRuntimeTest`: 7/7 PASS
- `DotEnvLoaderTest`: 9/9 PASS
- `PlatformVaultDispatcherTest`: 5/5 PASS
- `StickyWindowGeometryTest`: 15/15 PASS
- `StickyWindowPreferencesTest`: 7/7 PASS
- `LinuxNotificationAdapterTest`: 6/6 PASS
- `LinuxReminderAdapterTest`: 6/6 PASS
- `LinuxRemoteNotificationAdapterTest`: 18/18 PASS
- `LinuxSecretServiceVaultTest`: 6/6 PASS

### `:androidApp:testDebugUnitTest` (50 tests, 8 suites, 0 failures, 0 skipped)
- `AndroidNotificationAdapterTest`: 6/6 PASS
- `AppIconResourcesTest`: 2/2 PASS
- `BuildConfigTest`: 1/1 PASS
- `CompositeLeaveSchedulerCancelGateTest`: 4/4 PASS
- `ReminderPermissionControllerTest`: 2/2 PASS
- `RemoteInstallCoordinatorTest`: 7/7 PASS
- `TaskSurfaceNotificationTest`: 10/10 PASS
- `TaskWidgetModelTest`: 18/18 PASS

### Offline Script & Node Runners (158 checks/tests, 0 failures)
- `hub-live-smoke.mjs --fixture-test`: 18/18 PASS (offline fixture server HTTP 200/401/403/409/426 checks)
- `strict-v2-cutover-rehearsal.mjs --self-test`: 8/8 PASS (CLI mock runner self-test)
- `admin-announcements-smoke.mjs --self-test`: 34/34 PASS (13 golden vectors + 21 mock assertions)
- `release-security-check.mjs --diagnose-source`: 5/5 PASS (manifest + source security audit)
- `strict-v2-vector-runner.mjs`: 23/23 PASS (2 schema fingerprints, 10 valid vectors, 11 rejections)
- `strict-v3-vector-runner.mjs`: 12/12 PASS (1 schema fingerprint, 6 valid vectors, 5 rejections)
- `hub-strict-upgrade.test.mjs`: 13/13 PASS (CLI upgrade & recovery mock tests)
- `release-security-check.test.mjs`: 24/24 PASS (exported component security assertions)
- `bump-version.test.mjs`: 13/13 PASS (semver parser & bumper unit tests)
- `debug-fault-proxy.test.mjs`: 8/8 PASS (fault proxy injection unit tests)

---

## 3. Validation of Phase 06 Remediations & Status

### Critical Engineering Findings Status (Unresolved Blockers)
- **C1-R (Desktop Controller Concurrency):**
  - **Status:** OPEN / UNRESOLVED.
  - **Code Analysis:** `desktop-task-surfaces.kt` L316-333 still cancels `activeObserverJob` and nulls `activeToken` inside readiness collector when `currentToken == null`. Because collector runs asynchronously on `scope.launch`, this is a TOCTOU race against concurrent `startObserving(B)`.
  - **Test Suite Analysis:** While `DesktopTaskSurfacesTest` passes 23/23, current tests do not prove race freedom under interleaving:
    - `testRapidAccountSwitchReadyInterleavingUnderMultiThreadedDispatcher` calls `startObserving(token2)` before `onAccountAuthenticated(token2)` (inverting production call order).
    - `testStartupOrderControllerConstructedBeforeAccountAuthentication` and `testInitialSnapshotProcessedBeforeReadinessCollectorRuns` do not force the collector to interleave with null-token state.
  - **Resolution Requirement:** Remove asynchronous job cancellation and `activeToken` nulling from collector; serialize observer lifecycle synchronously in `startObserving`/`stopObserving` under mutex or generation token; add deterministic test with parked/stale collector.

- **C2-R (Runbook §6 Recovery Safety):**
  - **Status:** OPEN / UNRESOLVED.
  - **Analysis:** `docs/strict-v3-cutover-runbook.md` contains unverified claims ("coordinator fence auto-expires" despite no TTL in Hub permit service) and instructs aborting the CLI runner followed by destructive `mongorestore --drop --oplogReplay` on shared DB instance without scoping (`--nsInclude`). Missing same-migration-id resume matrix.
  - **Resolution Requirement:** Align recovery steps with v2 runbook matrix and Hub `permit_service.rs` realities; scope MongoDB restore commands.

### High & Warning Findings Status
- `hub-strict-upgrade.mjs` CLI parsing: accepts unknown options rejection, but missing argument values for known options (`--migration-id`) still proceed to network; requires fail-fast argument validation.
- `account-sync-coordinator.kt` late-child check: eligibility checked before `withMutationLock`.
- Operational holds G1–G7 remain in effect: physical Android launcher, native Windows/Linux sticky window rendering, and live staging Hub cluster rollout cannot be verified in offline headless environment.
- Supply-chain verification hold remains: all Gradle executions performed with `--dependency-verification=off`.

---

## 4. Performance & Execution Metrics
- `:shared:jvmTest`: 10.14s wall time (16 tasks rerun)
- `:desktopApp:test`: 10.25s wall time (20 tasks rerun)
- `:androidApp:testDebugUnitTest`: 9.70s wall time (46 tasks rerun)
- Combined Gradle rerun (`:shared:jvmTest :desktopApp:test :androidApp:testDebugUnitTest`): 12.17s (64 tasks rerun)
- Offline Node test runners: 23.5s total wall time across all scripts
- Total execution time: ~36s

---

## 5. Unresolved Questions
1. When will development address C1-R (removing async observer teardown from collector and adding deterministic `StandardTestDispatcher` race test)?
2. What is the approved operator recovery path for orphaned Hub coordinator fences during v3 cutover (given absence of fence TTL in Hub permit service)?
3. What is the target staging environment deployment schedule for live strict-v3 cutover validation?
