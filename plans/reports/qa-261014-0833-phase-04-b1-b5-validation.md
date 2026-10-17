# QA Test Report: Phase 04 Hub Auth B1-B5 Fix Verification

**Date:** 2026-10-14
**Target:** Phase 04 Hub Authentication, Protected Session, and Contract Test Suites (B1-B5 Resolution Verification)
**Command:** `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks`
**Environment:** Linux x86_64 (`/home/loidinh/WS/tabbyrush`), OpenJDK 21.0.8 (build 21.0.8+7)

---

## 1. Test Results Overview

| Test Suite | Class FQCN | Total | Passed | Failed | Skipped | Status | Execution Time |
|---|---|---:|---:|---:|---:|:---:|---:|
| **Hub Auth Client** | `dev.tabbyrush.shared.auth.HubAuthClientTest` | 8 | 8 | 0 | 0 | **PASS** | 0.298s |
| **Hub Auth Session** | `dev.tabbyrush.shared.auth.HubAuthSessionTest` | 8 | 8 | 0 | 0 | **PASS** | 0.114s |
| **Single-Flight Refresh** | `dev.tabbyrush.shared.auth.SingleFlightRefreshTest` | 3 | 3 | 0 | 0 | **PASS** | 0.008s |
| **Account Key Contract** | `dev.tabbyrush.shared.contract.AccountKeyTest` | 5 | 5 | 0 | 0 | **PASS** | 0.002s |
| **Endpoint Policy Contract** | `dev.tabbyrush.shared.contract.EndpointPolicyTest` | 8 | 8 | 0 | 0 | **PASS** | 0.004s |
| **Strict Digest Contract** | `dev.tabbyrush.shared.contract.StrictDigestTest` | 5 | 5 | 0 | 0 | **PASS** | 0.003s |
| **Title Policy Contract** | `dev.tabbyrush.shared.contract.TitlePolicyTest` | 6 | 6 | 0 | 0 | **PASS** | 0.005s |
| **GRAND TOTAL** | *All 7 Shared JVM Test Suites* | **43** | **43** | **0** | **0** | **PASS (100%)** | **0.434s** |

*Note on suite count evolution:* Total test count increased from 42 to 43 tests. `SingleFlightRefreshTest` was refactored (4 -> 3 tests) to remove session-level vault concerns from coordinator unit tests, while `HubAuthSessionTest` expanded (6 -> 8 tests) by adding specific integration tests for B2 (stale resurrection prevention) and B3 (successor in-memory retention on vault failure).

---

## 2. Verification of Blockers B1-B5

### B1: Credential Disclosure via Parse Error Messages
- **Status:** **PASS (Narrow scope verified)**
- **Verification:** Inspected `hub-auth-client.kt` catches for `MALFORMED_AUTH_RESPONSE`, `MALFORMED_ACCESS_RESPONSE`, and `MALFORMED_ACCESS_LIST_RESPONSE`. All interpolate static constant error strings (`"Unable to parse registration response"`, `"Unable to parse login response"`, etc.). `SingleFlightRefreshCoordinator` catches generic exceptions with static `"Unexpected error during single-flight refresh"`.
- **Remaining Risk:** `hub-http-client.kt` line 135 forwards sanitized `e.message` via `sanitizeErrorMessage` on general transport network failures. While HTML tags are stripped, token scrubbing is not guaranteed if upstream network client embeds query/body snippets in transport exceptions.

### B2: Logout vs In-Flight Refresh Resurrecting Credentials on Disk
- **Status:** **PASS (Regression verified)**
- **Verification:** `HubAuthSession.refresh()` now executes `vault.store(key, successor)` strictly inside `sessionMutex.withLock` and guards the write with generation fence `if (currentGeneration != gen)`. If logout occurred during network await, refresh returns `SafeError.Unauthorized("GENERATION_STALE")` and skips vault persistence.
- **Automated Test:** `HubAuthSessionTest.testLogoutDuringRefreshPreventsStaleVaultResurrection` (0.062s) PASS. Simulates 50ms network refresh delay, triggers `session.logout()` at 20ms, verifies `GENERATION_STALE` error returned, vault remains empty, and session state remains `UNAUTHENTICATED`.

### B3: Persistence Failure After Successful Refresh
- **Status:** **PASS (Contract behavior verified)**
- **Verification:** In `HubAuthSession.refresh()`, upon successful server refresh, `inMemoryBundle` is immediately updated with successor before `vault.store` is attempted. If `vault.store` throws, error `SafeError.Internal("CREDENTIAL_PERSISTENCE_FAILED")` is returned, and `onCredentialPersistenceFailed` callback is invoked with successor bundle outside mutex. The successor remains in memory to avoid replaying consumed predecessor token on next refresh.
- **Automated Test:** `HubAuthSessionTest.testVaultWriteFailureRetainsSuccessorInMemory` (0.007s) PASS. Injects failing vault store, confirms `CREDENTIAL_PERSISTENCE_FAILED` returned, callback receives rotated token payload, and in-memory session maintains successor.
- **Contract Boundary Clarification:** When vault store fails, in-memory state remains `AUTHENTICATED` to allow retry; server network/revocation failures transition state to `REAUTH_REQUIRED`. Offline restart without valid vault storage will require reauth.

### B4: Android Instrumented Test Compilation
- **Status:** **DEFERRED (Source-level fixed, execution deferred to Phase 07)**
- **Verification:** Missing imports (`org.junit.Assert.assertFalse`, `assertEquals`, `assertNull`, etc.) added to `shared/src/androidInstrumentedTest/kotlin/dev/tabbyrush/shared/auth/android-keystore-vault-test.kt`.
- **Host Limitation:** Local workstation has no Android SDK (`ANDROID_HOME`). Neither Android production sources nor instrumented tests compile on this machine.
- **Plan Authority:** `phase-04-auth-session.md` and roadmap explicitly defer Android compilation and instrumented test execution to Phase 07.

### B5: State Written Without Generation Fence After Awaits
- **Status:** **PASS (Code and unit verified)**
- **Verification:** `completeLoginWithMe`, `checkAccess`, and `handleMeAccessEvaluation` in `hub-auth-session.kt` capture generation `gen` before suspending network calls, then re-check `if (currentGeneration == gen)` under `sessionMutex.withLock` before writing `_state`. Stale network responses after logout or generation bump cannot corrupt session state.

---

## 3. Detailed Test Execution Breakdown

### 3.1 `dev.tabbyrush.shared.auth.HubAuthClientTest` (8/8 PASS)
1. `testGetMeWithBearerToken[jvm]`: PASS (0.247s) - Validates `Bearer <token>` in `Authorization` header on `/api/v1/auth/me` and app access evaluation.
2. `testEndpointAllowlistAndDualAuthHeaders[jvm]`: PASS (0.009s) - Validates `X-API-Key` and `X-App-Id` on allowlisted sync routes; rejects legacy non-allowlisted routes client-side with `DISALLOWED_ENDPOINT`.
3. `testRefreshSuccess[jvm]`: PASS (0.011s) - Validates `/api/v1/auth/refresh` serialization and response parsing.
4. `testValidationHelpers[jvm]`: PASS (0.000s) - Tests email normalization/syntax, username length/characters, password byte bounds.
5. `testAccessRequestCreationAndListing[jvm]`: PASS (0.009s) - Tests access request creation and listing endpoints.
6. `testRegisterSuccessAndHeaders[jvm]`: PASS (0.006s) - Tests registration payload and confirms absence of auth headers on public routes.
7. `testLoginSuccessAndErrorHandling[jvm]`: PASS (0.009s) - Tests login response parsing and 401 error mapping.
8. `testSanitizeErrorMessageStripsHtml[jvm]`: PASS (0.004s) - Confirms raw HTML stripping from 502 server error pages.

### 3.2 `dev.tabbyrush.shared.auth.HubAuthSessionTest` (8/8 PASS)
1. `testLoginMismatchedUserIdRejected[jvm]`: PASS (0.014s) - Rejects login when `/me` user ID mismatches `/login` response user ID.
2. `testOfflineSessionRestore[jvm]`: PASS (0.004s) - Restores authenticated state from cached identity and vault while offline.
3. `testLoginSuccessWithoutTabbyRushAccessTransitionsToAccessPending[jvm]`: PASS (0.005s) - Verifies transition to `ACCESS_PENDING` when user lacks tabbyrush approval.
4. `testLoginSuccessWithTabbyRushAccess[jvm]`: PASS (0.005s) - Full login transition to `AUTHENTICATED` with vault persistence.
5. `testLogoutDuringRefreshPreventsStaleVaultResurrection[jvm]`: PASS (0.062s) - [B2 fix verification] Logout during refresh aborts vault persistence and returns `GENERATION_STALE`.
6. `testLocalLogoutClearsSessionAndVault[jvm]`: PASS (0.005s) - Local logout clears in-memory credentials and deletes vault entry.
7. `testVaultWriteFailureRetainsSuccessorInMemory[jvm]`: PASS (0.007s) - [B3 fix verification] Retains successor in memory on vault write error and notifies callback.
8. `testAuthorizedRequest401DoesNotReplayPostAutomatically[jvm]`: PASS (0.010s) - Refresh succeeds on 401, but non-idempotent POST is not automatically replayed.

### 3.3 `dev.tabbyrush.shared.auth.SingleFlightRefreshTest` (3/3 PASS)
1. `testNetworkFailurePropagatedWithoutLoop[jvm]`: PASS (0.002s) - Network 401 propagates without entering retry loop.
2. `testUnexpectedExceptionReturnsSafeError[jvm]`: PASS (0.002s) - Runtime exceptions map to `SafeError.Network("REFRESH_EXCEPTION")`.
3. `testConcurrentRefreshDeduplication[jvm]`: PASS (0.004s) - 5 concurrent callers deduplicated into 1 single network refresh request.

### 3.4 Contract Test Suites (24/24 PASS)
- `AccountKeyTest`: 5/5 PASS (0.002s) - Hex format, determinism, distinction, framing, and rejection of invalid keys.
- `EndpointPolicyTest`: 8/8 PASS (0.004s) - HTTPS validation, trailing slash normalization, debug allowed hosts, cleartext release rejection, port parsing.
- `StrictDigestTest`: 5/5 PASS (0.003s) - NIST SHA-256 vectors, deterministic canonical digests, tombstone empty payload digests, input alteration detection.
- `TitlePolicyTest`: 6/6 PASS (0.005s) - Trimming, astral emoji scalar counts, 200/201 limit, control character rejection.

---

## 4. Coverage Metrics & Static Quality Assessment

- **JVM Automated Test Coverage:**
  - `dev.tabbyrush.shared.auth.*`: 19 tests covering client, session, and coordinator lifecycle paths.
  - `dev.tabbyrush.shared.contract.*`: 24 tests covering frozen contracts (account keys, endpoint policies, digests, title policies).
  - Common JVM unit/contract coverage estimated >85% across happy paths, error transformations, and generation boundaries.
- **Coverage Deficits / Gaps (Identified by QA & Code Review):**
  1. Concurrency keying: `SingleFlightRefreshCoordinator` is shared across accounts/generations rather than keyed by `(accountKey, generation)` (High Review item H1).
  2. Cancellation safety: Coordinator `finally` block acquires mutex inside cancelled coroutine without `NonCancellable` context (High Review item H3).
  3. Network error token leakage: `hub-http-client.kt` sanitizes HTML but does not scrub tokens from raw transport error strings.
  4. Android instrumented coverage: 0% automated execution due to missing SDK.

---

## 5. Build Status & Runtime Metrics

- **Gradle Command:** `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks`
- **Build Status:** SUCCESSFUL
- **Tasks Executed:** 12 actionable tasks, 12 executed
- **Total Gradle Wall Clock Time:** 4.87s
- **Pure Test Execution Runtime:** 0.434s across all test suites
- **Build Warnings:**
  - Benign SLF4J NOP logger warning (`SLF4J(W): No SLF4J providers were found`).
  - Gradle 9.0 deprecation warnings from KMP/AGP plugins.
  - Dependency verification lenient flag required due to unverified classpath artifacts.

---

## 6. Critical Issues & Blocking Items

- **JVM Scope:** NONE. All 43 test cases pass with 100% pass rate.
- **Integration Scope (Pre-Phase 05):**
  1. Coordinator scope: In-flight refresh coordination is process-wide; account switching or rapid logout/login cycles during in-flight network call should be keyed by account and generation.
  2. Cancellation safety in coordinator: Ensure follower and leader coroutine cancellations propagate without coroutine leaks or hanging deferreds.
- **Android Scope:** Android SDK missing on host; Android builds and instrumented tests deferred to Phase 07 per roadmap.

---

## 7. Recommendations & Next Steps

1. **Phase 04 Sign-off:** JVM unit and contract test suites are green (43/43, 100% pass rate). Core B1-B3 and B5 logic verified.
2. **Phase 05 Hand-off:** Session state management and single-flight refresh are safe for sequential and delayed-logout flows.
3. **Phase 07 Priorities:**
   - Configure Android SDK and emulator to compile and run `AndroidKeystoreVaultTest`.
   - Add multi-account concurrent refresh tests to verify account isolation under high concurrency.
   - Lock dependency verification metadata in `gradle/verification-metadata.xml`.

---

## 8. Unresolved Questions

1. In `hub-http-client.kt`, should transport error messages be replaced with fixed static messages rather than passing `e.message` through `sanitizeErrorMessage` to eliminate all potential token leak surface?
2. Does Phase 05 anticipate multi-account rapid switching on the same client instance where coordinator keying by `(accountKey, generation)` becomes critical immediately?
3. In Phase 07, will the Android CI runner support hardware-backed StrongBox Keystore testing or will testing rely exclusively on the software Keystore in the Android emulator?
