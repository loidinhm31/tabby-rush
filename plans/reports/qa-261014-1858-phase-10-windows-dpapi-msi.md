# QA Test Report: Phase 10 — Native Windows DPAPI and Installed MSI

**Date:** 2026-10-14 18:58
**QA Agent:** Phase10Tester  
**Verdict:** **TEST SUITES GREEN (120/120; 0 failed, 0 skipped).** The console reports every executed desktop/shared test as passed; the live DPAPI round-trip was exercised on Windows. Packaging checks remain partial: diagnostics found WiX tools missing from `PATH`, and the distributable (`-DistDir`) audit could not be run because workspace tools denied access to `desktopApp/build/`.

## 1. Scope and Execution

- Windows desktop: `./gradlew.bat -PdesktopTarget=windows :desktopApp:test --dependency-verification=off`
- Shared JVM: `./gradlew.bat :shared:jvmTest --dependency-verification=off`
- Environment diagnostics: `powershell.exe -ExecutionPolicy Bypass -File scripts/windows-package-smoke.ps1 -Diagnose`
- To ensure actual test execution rather than accepting Gradle's initial `UP-TO-DATE` result, desktop tests were rerun with `--rerun-tasks --console=plain`. Shared tests were run with those same execution-forcing options.
- Desktop command's initial invocation returned `BUILD SUCCESSFUL` but `:desktopApp:test UP-TO-DATE`; the forced rerun below is the test evidence used for counts.

## 2. Test Results Overview

| Target | Test classes | Tests | Passed | Failed | Skipped | Status | Elapsed |
|---|---:|---:|---:|---:|---:|---|---:|
| Windows `:desktopApp:test` | 3 | 24 | 24 | 0 | 0 | PASS | 15.04 s wall-clock; Gradle: 14 s |
| `:shared:jvmTest` | 14 | 96 | 96 | 0 | 0 | PASS | 14.29 s wall-clock; Gradle: 14 s |
| **Test total** | **17** | **120** | **120** | **0** | **0** | **PASS** | **29.33 s combined wall-clock** |
| Windows packaging diagnostics (`-Diagnose`) | — | — | — | — | — | COMPLETED | 0.50 s wall-clock |

The 120/120 totals are based on the forced Gradle console test-event inventory (not parsed JUnit XML); all expected methods appeared as `PASSED`. Test skips/failures: none. Gradle tasks marked `SKIPPED`/`NO-SOURCE` during the build are task/configuration states, not skipped test cases. Per-method duration and coverage were not emitted/generated; elapsed times above are command-level timings.

## 3. Windows Desktop Test Inventory (24/24)

| Class | Cases | Result | Methods (each PASSED) |
|---|---:|---|---|
| `dev.doggo.desktop.DesktopLifecycleTest` | 8 | 8/8 PASS | `testReleaseModeDeniesHttpLoopback`; `testSyncSchedulerCancelAndAwaitQuiescence`; `testSingleInstanceGuardLockFileRetainedOnClose`; `testDebugModePermitsHttpLoopback`; `testSyncSchedulerPeriodicInterval`; `testSyncSchedulerImmediateTrigger`; `testSingleInstanceGuardAcquireAndPreventConcurrentLaunch`; `testReleaseModePermitsHttps` |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | 5 | 5/5 PASS | `testResolveProviderUnknownTargetFailsClosed`; `testResolveProviderMultipleMatchesFailsClosed`; `testResolveProviderNoMatchFailsClosed`; `testDetectHostTargetOnLinux`; `testResolveProviderRealLinuxProviderDiscovered` |
| `dev.doggo.desktop.windows.WindowsDpapiVaultTest` | 11 | 11/11 PASS | `testClearRemovesVaultFile`; `testAtomicReplacement`; `testStoreAndLoadRoundTrip`; `testAccountKeyMismatchFailsClosed`; `testUnprotectFailureTransitionsToCorrupt`; `testLiveJnaDpapiRoundtripOnWindows`; `testUnavailableProtectorThrows`; `testLinuxProviderAbsentOnWindows`; `testMissingSecretReturnsNull`; `testCorruptPayloadFailsClosed`; `testServiceLoaderDiscoversWindowsProvider` |

`testLiveJnaDpapiRoundtripOnWindows` passed. Its test returns early only when the protector is unavailable; `JnaWindowsDpapiProtector.isAvailable()` returns `Platform.isWindows()`, and the host was reported as Windows, so the test proceeded through `Crypt32Util.cryptProtectData` and `cryptUnprotectData` and verified round-trip values and clearing. Vault coverage also exercised atomic replacement, missing/corrupt payload handling, account-key mismatch, unavailable protector, provider service loading, and Linux-provider absence. `PlatformVaultDispatcherTest` contains Linux-conditional checks; those test methods passed, but their Linux-only assertions are conditional and are not evidence of Linux-host behavior in this run.

## 4. Shared JVM Test Inventory (96/96)

All methods listed below were printed as `PASSED` by `:shared:jvmTest`.

| Class | Cases | Methods (each PASSED) |
|---|---:|---|
| `dev.doggo.shared.auth.HubAuthClientTest` | 8 | `testGetMeWithBearerToken`; `testEndpointAllowlistAndDualAuthHeaders`; `testRefreshSuccess`; `testValidationHelpers`; `testAccessRequestCreationAndListing`; `testRegisterSuccessAndHeaders`; `testLoginSuccessAndErrorHandling`; `testSanitizeErrorMessageStripsHtml` |
| `dev.doggo.shared.auth.HubAuthSessionTest` | 8 | `testLoginMismatchedUserIdRejected`; `testOfflineSessionRestore`; `testLoginSuccessWithoutDoggoAccessTransitionsToAccessPending`; `testLoginSuccessWithDoggoAccess`; `testLogoutDuringRefreshPreventsStaleVaultResurrection`; `testLocalLogoutClearsSessionAndVault`; `testVaultWriteFailureRetainsSuccessorInMemory`; `testAuthorizedRequest401DoesNotReplayPostAutomatically` |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | 3 | `testNetworkFailurePropagatedWithoutLoop`; `testUnexpectedExceptionReturnsSafeError`; `testConcurrentRefreshDeduplication` |
| `dev.doggo.shared.contract.AccountKeyTest` | 5 | `testFramingPreventsBoundaryCollision`; `testDeterministicAccountKey`; `testDistinctKeysForDifferentInputs`; `testInvalidFormatRejected`; `testValidAccountKeyFormat` |
| `dev.doggo.shared.contract.EndpointPolicyTest` | 8 | `testCleartextHttpForbiddenInRelease`; `testValidHttpsEndpointsInBothModes`; `testUserinfoSubpathsQueryFragmentRejected`; `testTrailingSlashStripped`; `testDebugPermittedHttpOrigins`; `testDisallowedDebugHttpHostsRejected`; `testDefaultHttpsPortNormalized`; `testInvalidCharactersAndPortBounds` |
| `dev.doggo.shared.contract.StrictDigestTest` | 5 | `testInvalidCombinationsRejected`; `testFieldChangesProduceDistinctDigests`; `testNistSha256Vectors`; `testDeleteFixedVectorAndEmptyData`; `testDeterministicDigestAndFixedVector` |
| `dev.doggo.shared.contract.TitlePolicyTest` | 6 | `testLengthBounds200And201`; `testInternalControlCharactersRejected`; `testMalformedSurrogatesRejected`; `testEmptyAndWhitespaceOnlyRejected`; `testScalarCountingWithAstralEmoji`; `testValidTitleWithWhitespaceTrimming` |
| `dev.doggo.shared.data.RoomAccountStoreTest` | 9 | `testReconcileAcceptedAdvancesServerBaseAndClearsOldDraft`; `testSaveAndLoadTodosWithBundledSqlite`; `testCloseAndWipeDatabaseFiles`; `testRecoverInterruptedDispatchesConvertsToUncertain`; `testReconcileAcceptedPreservesNewerDraft`; `testWipeLocalDataClearsAllTables`; `testFrozenStoreRejectsMutations`; `testUnsentCreateDeleteCancelsLocally`; `testProcessRestartRecoveryWithFrozenOutbox` |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | 7 | `testCompletePendingWipeOnStartup`; `testLeaveProceedsWhenZeroUnresolvedWork`; `testMarkerWriteFailureAbortsTeardown`; `testLeaveGuardedWhenReviewWorkPending`; `testDiscardAndLeaveProceedsWithPendingWork`; `testDatabaseDeletionFailureKeepsWipeMarkerFailClosed`; `testSchedulerCancelFailureAbortsTeardownFailClosed` |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | 5 | `testConflictResolutionCopyAsNew`; `testConflictResolutionReapply`; `testEpochMismatchTransitionsToResetRequired`; `testConflictResolutionDiscard`; `testTriggerSyncSuccess` |
| `dev.doggo.shared.sync.HubSyncClientTest` | 6 | `testPushErrorEnvelopesMapped`; `testPullSuccessAndPagination`; `testPushBatchBoundsEnforcedClientSide`; `testBootstrapSuccess`; `testBootstrapProtocolMismatchFails`; `testPushSuccess` |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | 10 | `testUncertainDeliveryConvertsSubsequentConflictToUnknownSuperseded`; `testOptimisticConcurrencyCheckSkipsStaleFreezeOnConcurrentEdit`; `testUncertainOpRetainsExactFrozenIdentityOnRetry`; `testStandardConflictPreservesIntentForReview`; `testEligibleBatchFifoOrder`; `testFifoHeadConflictBlocksSuccessorDispatch`; `testSuccessorRevisionFrozenAtDispatchAgainstCurrentBase`; `testAckPreservesNewerDraft`; `testExactCasAckAdvancesBaseAndRemovesOutbox`; `testRetryableFailureIncrementsFailuresAndRetainsUncertain` |
| `dev.doggo.shared.sync.StagedFullScanTest` | 9 | `testCursorLoopDetectionAborts`; `testMonotonicPromotionEqualRevisionDifferingLogicalDataFails`; `testMonotonicPromotionHigherRevisionReplaces`; `testMonotonicPromotionLowerRevisionRetainsLocal`; `testEmptyPageWithHasMoreFails`; `testFullScanMultiPageSuccess`; `testMissingPreScanConfirmedIdentityTriggersConsistencyError`; `testRowIdOrderViolationFails`; `testActiveRecordWithMissingDataFails` |
| `dev.doggo.shared.ui.TodoSemanticsTest` | 7 | `testPasswordRedactedInAuthSubmitActionToString`; `testTitlePolicyValidationInEditor`; `testLeaveGuardTalliesAndSafety`; `testSyncFeedbackStatusFormattingAndTimestamp`; `testAccessRequestStatusesIncludingApprovedAndUnknown`; `testWcagContrastRatiosMeetAccessibilityStandards`; `testConflictReviewActionsAndTombstoneGuard` |
| **Total** | **96** | **14 classes, all cases passed** |

## 5. Windows Package Diagnostics and Verification

`powershell.exe -ExecutionPolicy Bypass -File scripts/windows-package-smoke.ps1 -Diagnose` completed:

| Check | Observed result |
|---|---|
| Host OS version reported | `10.0.26200.0` (AMD64) |
| Java | Present, `21.0.9` at `G:\env\jdk-21.0.9\bin\java.exe` |
| `jpackage` | Present, `21.0.9` |
| WiX (`candle` / `light`) | **Not found in PATH**; script identifies WiX 3.11+ as required for `:desktopApp:packageMsi` |
| PowerShell | `5.1.26100.9444`; execution policy `Bypass` |

 - **`-Diagnose`: COMPLETED** — Java and `jpackage` were present; WiX `candle`/`light` were not found in `PATH`.
 - **`-DistDir`: NOT VERIFIED** — attempts to list/check `desktopApp/build/` were blocked by the workspace tool boundary (`build/` access denied). No claim is made that a distributable exists or passes the bundle audit. Packaged JAR contents, DPAPI provider registration, and Linux-class isolation therefore remain unverified by the smoke script.
 - **MSI build/install:** not run; this execution covered the requested tests and diagnostics, not `packageMsi` or installer execution. The missing WiX tools prevent package creation with the current host PATH.

## 6. Build Warnings and QA Analysis

- Kotlin compiler emitted the Beta warning for `expect`/`actual` classes in generated `DoggoDatabaseConstructor.kt` and `shared/src/commonMain/.../doggo-database.kt`.
- Desktop test compilation emitted a deprecation warning for the Java `URL(String, String, int, String, URLStreamHandler)` constructor in `platform-vault-dispatcher-test.kt:69`.
- Gradle warned that deprecated features in the build are incompatible with Gradle 9.0.
- Both forced test builds completed successfully; no test failures or skipped cases, and no flaky behavior observed in these executions.
- Coverage percentages were not measured; no coverage task/report was run.

## 7. Critical Issues and Recommendations

1. **MSI verification blocked:** provision WiX 3.11+ on this host or run packaging in a configured Windows CI environment; then build `:desktopApp:packageMsi` and execute the MSI audit/install checks.
2. **Distributable audit outstanding:** make the desktop build artifact path available to the workspace tools, then run `windows-package-smoke.ps1 -DistDir <createDistributable-output>` to check launcher/runtime, DPAPI class and service registration, and Linux artifact isolation.
3. **Warnings:** plan the Gradle 9 compatibility cleanup and replace the deprecated URL constructor; address Kotlin expect/actual Beta warning when supported by the Kotlin configuration.
4. **Coverage:** collect a JVM coverage report if coverage percentages are an acceptance criterion; none were part of this run.

## Unresolved Questions

1. Which Windows host/CI environment will provide WiX 3.11+ for MSI packaging and installation verification?
2. Can access to `desktopApp/build/` be enabled for the follow-up distributable smoke audit?