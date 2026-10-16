# QA Report: Phase 01 Full-Suite Validation

**Date:** 2026-10-16  
**Scope:** Shared JVM, desktop, Android debug unit tests; offline fixture smoke; source security diagnostics; strict-v2 tooling self-test.

## Test Results Overview

The exact requested Gradle command completed successfully, but all three test tasks were initially `UP-TO-DATE`. To ensure tests actually ran during this validation, the same tasks were rerun with `--rerun-tasks`.

| Module / task | Test classes (JUnit XML reports) | Passed | Failed | Errors | Skipped | Status |
|---|---:|---:|---:|---:|---:|---|
| Shared `:shared:jvmTest` | 31 | 231 | 0 | 0 | 0 | PASS |
| Desktop `:desktopApp:test` | 10 | 64 | 0 | 0 | 0 | PASS |
| Android `:androidApp:testDebugUnitTest` | 4 | 12 | 0 | 0 | 0 | PASS |
| **Overall Gradle tests** | **45** | **307** | **0** | **0** | **0** | **PASS** |

Counts are aggregated from the XML reports produced by the forced rerun. No failed or skipped cases were reported.

### Shared JVM breakdown (231 tests)

| Test class | Cases |
|---|---:|
| HubAuthClientTest | 9 |
| HubAuthSessionTest | 8 |
| SingleFlightRefreshTest | 3 |
| AccountKeyTest | 5 |
| AdversarialSchedulerBoundaryTest | 7 |
| EndpointPolicyTest | 8 |
| PersistedScheduleProjectionTest | 5 |
| ReminderScheduleCalculatorTest | 9 |
| StrictDigestTest | 5 |
| StrictTimeZoneResolverTest | 7 |
| StrictV2DigestExtendedTest | 6 |
| StrictV2DigestVectorTest | 5 |
| TaskDeadlineReminderModelsTest | 6 |
| TaskSurfaceProjectorTest | 13 |
| TitlePolicyTest | 6 |
| AdversarialSyncSqliteTest | 5 |
| ReminderOccurrenceStoreTest | 4 |
| ReminderSchedulingCoordinatorSqliteSmokeTest | 2 |
| ReminderTeardownBarrierSqliteTest | 1 |
| RoomAccountStoreTest | 25 |
| RoomMigrationV1ToV2Test | 1 |
| TaskSurfaceStoreSqliteTest | 10 |
| TaskSurfaceSyncSqliteTest | 4 |
| AccountLeaveProtocolTest | 7 |
| AccountSyncCoordinatorTest | 9 |
| ContractTransitionTest | 4 |
| HubSyncClientTest | 12 |
| OutboxReconciliationTest | 12 |
| ReminderDeliveryGateTest | 4 |
| StagedFullScanTest | 14 |
| TodoSemanticsTest | 15 |

**TaskSurfaceStoreSqliteTest detail (10/10 passed):** This includes `testSetCompletedLocallyTombstoneWithResidualDraftReturnsMissingWithoutException` and `testSetCompletedLocallyTombstoneWithResidualOutboxReturnsMissingWithoutException`. The residual-draft case asserts completion returns `Missing`, no outbox entry is created, and the draft remains. The residual-outbox case asserts completion returns `Missing` and the pre-existing outbox count stays unchanged. The ordinary tombstone mutation case also passed.

### Desktop breakdown (64 tests)

| Test class | Cases |
|---|---:|
| CompositeDesktopLeaveCancelGateTest | 2 |
| DesktopControllerEnvTest | 4 |
| DesktopLifecycleTest | 8 |
| DesktopReminderControllerLifecycleTest | 3 |
| DesktopReminderSchedulerTest | 6 |
| DotEnvLoaderTest | 5 |
| PlatformVaultDispatcherTest | 5 |
| WindowsDpapiVaultTest | 12 |
| WindowsNotificationAdapterTest | 11 |
| WindowsReminderAdapterTest | 8 |

### Android breakdown (12 tests)

| Test class | Cases |
|---|---:|
| AndroidNotificationAdapterTest | 6 |
| BuildConfigTest | 1 |
| CompositeLeaveSchedulerCancelGateTest | 3 |
| ReminderPermissionControllerTest | 2 |

## Node Script Verification

These script checks are reported separately and are **not** included in the 307 Gradle test count.

| Command | Result | Scope |
|---|---|---|
| `node scripts/hub-live-smoke.mjs --fixture-test` | **PASS: 18/18 checks** | Starts a local fixture server; health/admission, security rejection paths, auth, strict sync, conflict/idempotency, and tombstone checks passed. This is offline fixture validation, not a live Hub test. |
| `node scripts/release-security-check.mjs --diagnose-source` | **PASS: CLEAN; 6/6 listed checks** | Static source checks passed: cleartext disabled, backup disabled, exported component audit, security XML presence, HTTPS origin, and no hard-coded private keys/passwords. No release APK was audited in this diagnostic mode. |
| `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | **PASS: 8/8 checks** | In-process tooling mock self-test passed help, token handling/redaction, dry-run, interrupted-POST recovery, status-only query, read-back divergence, and environment-token scenarios. Not a live service migration. |

## Coverage and Performance

- **Coverage:** No coverage task/report was requested or generated; line, branch, and function percentages are unavailable.
- **Gradle duration:** Forced rerun completed successfully in **16 seconds** (`63 actionable tasks: 63 executed`).
- **JUnit-reported test duration:** **3.130 seconds** summed across the three modules' XML reports. This is test-reported duration, not total build wall time.
- **Strict-v2 self-test duration:** **11.07 seconds** as reported by the command runner.
- No dedicated performance benchmark or flakiness/repeatability campaign was run.

## Build Status and Warnings

**Build status: PASS.** The forced Gradle run completed with `BUILD SUCCESSFUL`.

Warnings observed:

- Gradle reports deprecated features that will be incompatible with Gradle 9.0.
- Kotlin `expect`/`actual` classes are marked Beta.
- Four Android test-source type checks are reported as always `true`.
- Desktop test compilation uses a deprecated Java `URL` constructor.

The Windows live-toast test is conditionally gated by `doggo.test.liveToasts=true` and bridge availability; this run is not treated as evidence of live Windows toast delivery. The requested Gradle tasks run JVM/unit suites only, not Android instrumentation on a device/emulator.

## Dependency Verification Gate

Both Gradle invocations explicitly used `--dependency-verification=off`, as required by the known pre-existing **149-checksum baseline hold**. Therefore dependency checksum verification was **bypassed, not passed**; this run does not establish dependency-verification integrity. The 149 unmatched-checksum baseline is recorded in the prior Phase 01 validation report (`qa-261016-1716-phase-01-sticky-task-surfaces-validation.md`); it was not independently recomputed during this run.

## Critical Issues and Recommendations

- No Gradle test failures, errors, or skips; requested Node commands passed in their specified offline/static/self-test modes.
- Mock-free/live validation is not established: existing unit suites include test doubles, the fixture smoke uses a local fixture server, and the strict-v2 self-test explicitly uses an in-process tooling mock. No fabricated test results or replacement implementations were introduced.
- **Next:** resolve the checksum baseline hold and rerun with dependency verification enabled before treating the build as dependency-verified.
- **Next:** generate coverage if line/branch/function thresholds are required.
- For release acceptance, run any required live Hub/device/APK checks separately; they were outside the executed commands and their validation modes.
- Review the Gradle/Kotlin/deprecated-constructor warnings before the corresponding toolchain upgrades.

## Unresolved Questions

None. The dependency-verification baseline hold remains an explicitly identified validation limitation.
