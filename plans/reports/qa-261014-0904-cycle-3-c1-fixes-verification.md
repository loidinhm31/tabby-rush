# QA Test Verification Report: Cycle 3 Post-C1 Fixes

Date: 2026-10-14
Target: Shared module JVM test suite (`:shared:jvmTest`)
Command: `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks`

---

## Test Results Overview

- **Total Tests Run**: 43
- **Passed**: 43
- **Failed**: 0
- **Skipped**: 0
- **Pass Rate**: 100.0% (JVM target only)
- **Scope**: 43 JVM tests passed under lenient dependency verification. Android compilation and runtime validation remain outstanding.

### Test Suite Breakdown

| Suite | Tests | Passed | Failed | Skipped | Duration |
|-------|-------|--------|--------|---------|----------|
| `dev.tabbyrush.shared.auth.HubAuthClientTest` | 8 | 8 | 0 | 0 | 0.445s |
| `dev.tabbyrush.shared.auth.HubAuthSessionTest` | 8 | 8 | 0 | 0 | 0.172s |
| `dev.tabbyrush.shared.auth.SingleFlightRefreshTest` | 3 | 3 | 0 | 0 | 0.023s |
| `dev.tabbyrush.shared.contract.AccountKeyTest` | 5 | 5 | 0 | 0 | 0.003s |
| `dev.tabbyrush.shared.contract.EndpointPolicyTest` | 8 | 8 | 0 | 0 | 0.006s |
| `dev.tabbyrush.shared.contract.StrictDigestTest` | 5 | 5 | 0 | 0 | 0.004s |
| `dev.tabbyrush.shared.contract.TitlePolicyTest` | 6 | 6 | 0 | 0 | 0.008s |
| **Total** | **43** | **43** | **0** | **0** | **0.661s** |

---

## Detailed Test Cases Executed

### 1. `HubAuthClientTest` (8 tests)
- `testGetMeWithBearerToken[jvm]`: PASS (0.374s)
- `testEndpointAllowlistAndDualAuthHeaders[jvm]`: PASS (0.013s)
- `testRefreshSuccess[jvm]`: PASS (0.019s)
- `testValidationHelpers[jvm]`: PASS (0.001s)
- `testAccessRequestCreationAndListing[jvm]`: PASS (0.012s)
- `testRegisterSuccessAndHeaders[jvm]`: PASS (0.008s)
- `testLoginSuccessAndErrorHandling[jvm]`: PASS (0.011s)
- `testSanitizeErrorMessageStripsHtml[jvm]`: PASS (0.006s)

### 2. `HubAuthSessionTest` (8 tests)
- `testLoginMismatchedUserIdRejected[jvm]`: PASS (0.020s)
- `testOfflineSessionRestore[jvm]`: PASS (0.005s)
- `testLoginSuccessWithoutTabbyRushAccessTransitionsToAccessPending[jvm]`: PASS (0.008s)
- `testLoginSuccessWithTabbyRushAccess[jvm]`: PASS (0.007s)
- `testLogoutDuringRefreshPreventsStaleVaultResurrection[jvm]`: PASS (0.073s)
- `testLocalLogoutClearsSessionAndVault[jvm]`: PASS (0.016s)
- `testVaultWriteFailureRetainsSuccessorInMemory[jvm]`: PASS (0.017s)
- `testAuthorizedRequest401DoesNotReplayPostAutomatically[jvm]`: PASS (0.021s)

### 3. `SingleFlightRefreshTest` (3 tests)
- `testNetworkFailurePropagatedWithoutLoop[jvm]`: PASS (0.003s)
- `testUnexpectedExceptionReturnsSafeError[jvm]`: PASS (0.005s)
- `testConcurrentRefreshDeduplication[jvm]`: PASS (0.012s)

### 4. `AccountKeyTest` (5 tests)
- `testFramingPreventsBoundaryCollision[jvm]`: PASS (0.001s)
- `testDeterministicAccountKey[jvm]`: PASS (0.001s)
- `testDistinctKeysForDifferentInputs[jvm]`: PASS (0.000s)
- `testInvalidFormatRejected[jvm]`: PASS (0.001s)
- `testValidAccountKeyFormat[jvm]`: PASS (0.000s)

### 5. `EndpointPolicyTest` (8 tests)
- `testCleartextHttpForbiddenInRelease[jvm]`: PASS (0.001s)
- `testValidHttpsEndpointsInBothModes[jvm]`: PASS (0.000s)
- `testUserinfoSubpathsQueryFragmentRejected[jvm]`: PASS (0.001s)
- `testTrailingSlashStripped[jvm]`: PASS (0.000s)
- `testDebugPermittedHttpOrigins[jvm]`: PASS (0.000s)
- `testDisallowedDebugHttpHostsRejected[jvm]`: PASS (0.001s)
- `testDefaultHttpsPortNormalized[jvm]`: PASS (0.000s)
- `testInvalidCharactersAndPortBounds[jvm]`: PASS (0.000s)

### 6. `StrictDigestTest` (5 tests)
- `testInvalidCombinationsRejected[jvm]`: PASS (0.001s)
- `testFieldChangesProduceDistinctDigests[jvm]`: PASS (0.001s)
- `testNistSha256Vectors[jvm]`: PASS (0.001s)
- `testDeleteFixedVectorAndEmptyData[jvm]`: PASS (0.000s)
- `testDeterministicDigestAndFixedVector[jvm]`: PASS (0.001s)

### 7. `TitlePolicyTest` (6 tests)
- `testLengthBounds200And201[jvm]`: PASS (0.007s)
- `testInternalControlCharactersRejected[jvm]`: PASS (0.000s)
- `testMalformedSurrogatesRejected[jvm]`: PASS (0.000s)
- `testEmptyAndWhitespaceOnlyRejected[jvm]`: PASS (0.000s)
- `testScalarCountingWithAstralEmoji[jvm]`: PASS (0.000s)
- `testValidTitleWithWhitespaceTrimming[jvm]`: PASS (0.000s)

---

## Coverage Metrics

- **Line Coverage**: N/A (Kover / JaCoCo not configured in `shared/build.gradle.kts`)
- **Branch Coverage**: N/A
- **Function Coverage**: N/A
- **Functional Scope Coverage**: Covers current defined common test suites across `dev.tabbyrush.shared.auth.*` and `dev.tabbyrush.shared.contract.*`. Android instrumented tests (`androidInstrumentedTest`) not included.

---

## Failed Tests

- **None**: 0 failed tests on JVM target.

---

## Performance Metrics

- **Total Test Execution Time**: 0.661s
- **Gradle Task Wall Time**: 3.55s (12 actionable tasks executed)
- **Slow Tests Identified**:
  - `HubAuthClientTest.testGetMeWithBearerToken[jvm]`: 0.374s (first test client/engine startup overhead)
  - `HubAuthSessionTest.testLogoutDuringRefreshPreventsStaleVaultResurrection[jvm]`: 0.073s
  - All remaining 41 tests executed under 0.025s.

---

## Build Status

- **Status**: SUCCESS (JVM test execution only)
- **Actionable Tasks**: 12 executed, 0 failed
- **Notices / Warnings**:
  - `Dependency verification failed for configuration ...`: Run requires `--dependency-verification=lenient` because `verification-metadata.xml` lacks hashes for some transitive libraries.
  - `SLF4J(W): No SLF4J providers were found`: Harmless logger fallback to NOP during HTTP client testing.
  - Gradle deprecation warnings for Gradle 9.0 compatibility.

---

## Verification of Cycle 2 Fixes & Findings

1. **C1 (B4) Argument Order in Instrumented Test (Partially Addressed)**:
   - Source inspection: `android-keystore-vault-test.kt:83` confirmed updated to `assertFalse("Fresh 12-byte nonce must be generated per store", nonce1.contentEquals(nonce2))` (JUnit4 message-first convention).
   - Remaining gap: `shared/build.gradle.kts` still lacks explicit `androidInstrumentedTest` dependencies (`androidx.test:core`, `androidx.test.ext:junit`, `androidx.test:runner`, `junit`). Initializing Account B key avoids missing-key exception, but copying ciphertext across different keys fails due to key mismatch rather than isolating AAD binding specifically. Compilation/execution remain unvalidated on host without Android SDK.
2. **Transport Error Sanitization (Narrowly Addressed)**:
   - `hub-http-client.kt:133-140` returns fixed `SafeError.Network("NETWORK_ERROR", "Network transport failure")` on transport `Exception`, eliminating transport message leak.
   - Remaining gap: Does not establish that all `SafeError` paths avoid raw snippet / token leaks (e.g. 400 validation error body handling); `bodyAsText()` swallows `CancellationException`.
3. **Persistence State Handling (Narrowly Addressed)**:
   - `hub-auth-session.kt:325, 474` guards `_state.value = AuthState.REAUTH_REQUIRED` with `refreshOutcome.error is SafeError.Unauthorized`. Persistence failure returning `SafeError.Internal("CREDENTIAL_PERSISTENCE_FAILED")` does not transition `_state` to `REAUTH_REQUIRED` in `checkAccess` and `authorizedRequest`.
   - Remaining gap: Successor retention is not assertively tested (test doesn't attempt 2nd refresh or token inspection); post-refresh `/me` failure in `authorizedRequest` still transitions to `REAUTH_REQUIRED` even on transport/network error.

---

## Critical Issues & Unresolved Architectural Risks

While the JVM test suite passes 100% (43/43), the following architectural and security risks remain open from Cycle 2 code review:
1. **W1 (Potential Credential Isolation Defect)**: `SingleFlightRefreshCoordinator` is globally shared rather than keyed by `(accountKey, generation)`. A caller logging out and logging in as another account during an in-flight refresh may receive the prior account's rotated successor tokens.
2. **W2 (Rotating Token Stale-Leader Race)**: Bundle refreshToken read under mutex but coordinator entered outside mutex; leader finishing concurrently can cause caller to lead with an already-consumed token, causing false 401 and reauth.
3. **W3 (Cancellation Handling)**: `executeRaw` and parse catches swallow `CancellationException`; coordinator `finally` block is not run with `NonCancellable`, risking orphaned in-flight deferreds.
4. **W4 (Vault Clear Failure on Logout)**: `AndroidKeystoreVault.clear` does not verify file deletion or alias removal.
5. **C1 / B4 Android Test Dependencies**: Android instrumented test cannot be verified without build dependencies and Android runtime environment.

---

## Recommendations

1. Add explicit test coverage for account switching during in-flight refresh to prevent cross-account credential leakage.
2. Key `SingleFlightRefreshCoordinator` by `(accountKey, generation)` or ensure leader re-checks token state under lock before issuing rotation request.
3. Resolve `shared/build.gradle.kts` `androidInstrumentedTest` dependencies and schedule Android emulator test execution in Phase 07.
4. Audit artifact provenance and update `gradle/verification-metadata.xml` to restore strict dependency verification without masking untrusted binaries.
5. Add Kover Gradle plugin (`org.jetbrains.kotlinx.kover`) to `shared/build.gradle.kts` for automated line/branch coverage measurement.

---

## Next Steps

1. Hand off verified JVM test results (43/43 passed) to Main agent.
2. Ensure Phase 05 accounts for open W1-W4 auth session isolation and cancellation caveats.
3. Plan Phase 07 Android instrumented test runner environment setup.

---

## Unresolved Questions

1. Will Android instrumented tests be validated in a dedicated headless CI runner in Phase 07?
2. Should `SingleFlightRefreshCoordinator` be refactored to key by `(accountKey, generation)` before Phase 05 consumer integration?
3. Should Kover multiplatform code coverage reporting be integrated prior to Phase 05 completion?
