# QA Test Report: Phase 04 Auth Session & Vault Test Validation

**Date:** 2026-10-14
**Target:** Phase 04 Hub Authentication, Protected Session, and Contract Test Suites
**Command:** `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks`
**Environment:** Linux x86_64 (`/home/loidinh/WS/doggo`), OpenJDK 21.0.8

---

## 1. Test Results Overview

| Test Suite | Class FQCN | Total | Passed | Failed | Skipped | Status |
|---|---|---:|---:|---:|---:|:---:|
| **Single-Flight Refresh** | `dev.doggo.shared.auth.SingleFlightRefreshTest` | 4 | 4 | 0 | 0 | **PASS** |
| **Hub Auth Client** | `dev.doggo.shared.auth.HubAuthClientTest` | 8 | 8 | 0 | 0 | **PASS** |
| **Hub Auth Session** | `dev.doggo.shared.auth.HubAuthSessionTest` | 6 | 6 | 0 | 0 | **PASS** |
| **Account Key Contract** | `dev.doggo.shared.contract.AccountKeyTest` | 5 | 5 | 0 | 0 | **PASS** |
| **Endpoint Policy Contract** | `dev.doggo.shared.contract.EndpointPolicyTest` | 8 | 8 | 0 | 0 | **PASS** |
| **Strict Digest Contract** | `dev.doggo.shared.contract.StrictDigestTest` | 5 | 5 | 0 | 0 | **PASS** |
| **Title Policy Contract** | `dev.doggo.shared.contract.TitlePolicyTest` | 6 | 6 | 0 | 0 | **PASS** |
| **TOTAL (JVM Common)** | *All 7 Common JVM Suites* | **42** | **42** | **0** | **0** | **PASS (100%)** |

---

## 2. Test Execution Details

### 2.1 SingleFlightRefreshTest (4/4 PASS)
- `testConcurrentRefreshDeduplication`: PASS. 5 concurrent asynchronous refresh calls deduplicated into exactly 1 network execution; all callers receive identical successor bundle; vault receives successor tokens.
- `testGenerationFencingPreventsStaleResurrection`: PASS. Generation increment during in-flight refresh causes rejection (`GENERATION_STALE` under `SafeError.Unauthorized`); stale tokens never written to vault.
- `testVaultWriteFailureHandledSafely`: PASS. Simulated disk persistence failure leaves successor bundle in memory-only for recovery; triggers `onPersistenceFailed` callback and returns `SafeError.Internal("CREDENTIAL_PERSISTENCE_FAILED")`.
- `testNetworkFailurePropagatedWithoutLoop`: PASS. Upstream 401 refresh failure propagates as `SafeError.Unauthorized("REFRESH_REVOKED")` without entering infinite refresh loop.

### 2.2 HubAuthClientTest (8/8 PASS)
- `testValidationHelpers`: PASS. Normalizes email casing and trimming; validates email syntax; enforces username length (3..32 chars) and character constraints; validates transient password byte bounds (8..128 UTF-8 bytes).
- `testRegisterSuccessAndHeaders`: PASS. Verifies `/api/v1/auth/register` payload serialization; asserts public auth routes omit `Authorization` and `X-API-Key`.
- `testLoginSuccessAndErrorHandling`: PASS. Verifies `/api/v1/auth/login` DTO parsing (`userId`, `accessToken`, `refreshToken`, `apps`, `isAdmin`) and maps HTTP 401 to `SafeError.Unauthorized`.
- `testRefreshSuccess`: PASS. Verifies `/api/v1/auth/refresh` request and successor tokens deserialization.
- `testGetMeWithBearerToken`: PASS. Injects `Bearer <token>` into `Authorization` header on `/api/v1/auth/me`; validates `hasDoggoAccess` evaluation.
- `testAccessRequestCreationAndListing`: PASS. Tests creation (`POST /api/v1/apps/access-requests`) and listing (`GET /api/v1/apps/access-requests`) for cooperative doggo approval.
- `testEndpointAllowlistAndDualAuthHeaders`: PASS. Injects `X-API-Key` and `X-App-Id` on strict sync endpoints (`/api/v1/sync/:app_id/strong/*`); rejects legacy non-allowlisted routes client-side with `SafeError.ProtocolIncompatible("DISALLOWED_ENDPOINT")`.
- `testSanitizeErrorMessageStripsHtml`: PASS. Sanitizes raw HTML server error pages (502 Bad Gateway), stripping markup to prevent UI injection and log noise.

### 2.3 HubAuthSessionTest (6/6 PASS)
- `testLoginSuccessWithDoggoAccess`: PASS. Full login flow where `/me` returns `apps: ["doggo"]`; transitions session state to `AuthState.AUTHENTICATED`, sets `currentIdentity` and `currentAccountKey`, and persists bundle to vault.
- `testLoginSuccessWithoutDoggoAccessTransitionsToAccessPending`: PASS. Login where user lacks doggo approval; session transitions to `AuthState.ACCESS_PENDING`.
- `testLoginMismatchedUserIdRejected`: PASS. Rejects login if `/me` user ID mismatches `/login` user ID; resets state to `AuthState.UNAUTHENTICATED`, leaving vault unwritten.
- `testOfflineSessionRestore`: PASS. Restores session from cached `AccountIdentity` and `AccountKey` using local vault credentials while network is offline (HTTP engine throws exception).
- `testAuthorizedRequest401DoesNotReplayPostAutomatically`: PASS. Proves critical invariant: when 401 occurs during `authorizedRequest`, refresh succeeds but the method returns `TOKEN_REFRESHED` error instead of auto-replaying POST payload, preserving idempotent caller control.
- `testLocalLogoutClearsSessionAndVault`: PASS. Local logout clears in-memory state, resets state to `AuthState.UNAUTHENTICATED`, and removes credentials from vault without remote network revocation.

### 2.4 Contract Test Suites (24/24 PASS)
- `AccountKeyTest`: 5/5 PASS. Validates 64-char hex SHA-256 formatting, deterministic hash reproducibility, distinct keys for distinct inputs, length framing collision prevention (`ab`+`c` vs `a`+`bc`), and rejection of malformed keys.
- `EndpointPolicyTest`: 8/8 PASS. Validates HTTPS origins in Debug and Release; normalizes trailing slashes and port 443; permits localhost/10.0.2.2 in Debug; rejects cleartext HTTP in Release, disallowed debug hosts, userinfo, query params, fragments, and invalid port bounds.
- `StrictDigestTest`: 5/5 PASS. Validates SHA-256 against NIST vectors (empty string, "abc", multi-block); validates canonical length-framed big-endian digest computation against fixed vector; validates tombstone digest computation with empty payload; verifies sensitivity to any field alteration; rejects invalid input combinations.
- `TitlePolicyTest`: 6/6 PASS. Validates Unicode whitespace trimming; astral emoji Unicode scalar counting; 200 scalar limit boundary (200 pass, 201 reject); rejects empty/whitespace-only, unpaired surrogates, and internal control characters.

---

## 3. Keystore Vault Test Definitions & Android Status

**Static Test Definitions File:** `shared/src/androidInstrumentedTest/kotlin/dev/doggo/shared/auth/android-keystore-vault-test.kt`
**Runner:** `AndroidJUnit4`
**7 Defined Test Cases:**
1. `testStoreAndLoadCredentials`: Round-trip storage and retrieval under Android Keystore AES-256-GCM.
2. `testFreshNoncePerWrite`: Verifies 12-byte random IV differs between consecutive writes.
3. `testAadBindingPreventsCrossAccountDecryption`: Verifies ciphertext written under Account A cannot be decrypted under Account B due to AAD mismatch (`"doggo_vault_v1:" || accountKey`).
4. `testCorruptMagicHeaderDetection`: Detects corrupted magic header (`DGV1`) and flags `VaultStatus.CORRUPT`.
5. `testTruncatedVaultFileDetection`: Detects truncated vault file (< 20 bytes) and flags `VaultStatus.CORRUPT`.
6. `testTamperedCiphertextDetection`: Detects flipped bits in ciphertext/tag via GCM auth tag check and flags `VaultStatus.CORRUPT`.
7. `testClearDeletesFileAndAlias`: Atomic deletion of ciphertext file and Keystore master key alias.

### Android Compilation & Execution Blocker:
- Attempting `:shared:compileDebugKotlinAndroid` or `:shared:compileDebugAndroidTestKotlinAndroid` fails immediately with:
  `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file.`
- Current workstation lacks an Android SDK installation.
- Therefore, Android production sources (`AndroidKeystoreVault`, `AndroidHttpEngine`) and instrumented test sources (`AndroidKeystoreVaultTest`) remain **uncompiled and unexecuted** on this host.

### Plan Authority for Deferral:
The deferral of Android compilation and instrumented Keystore tests to Phase 07 is explicitly defined and governed by the project roadmap:
- `plans/261013-1540-doggo-glean-hub-reuse/phase-04-auth-session.md` (Line 11):
  *"Group C after 02; independent coding 03/05/06... Test source ready here; common/instrumented execution coordinated 07; live Hub 03 needed for runtime."*
- `plans/261013-1540-doggo-glean-hub-reuse/dependency-matrix.md` (Line 26 & 38):
  *"04 auth: Consumes frozen vault/route contracts; Android Keystore device run coordinated in 07 common/Keystore execution... 07 runs tuple-mapped :shared:allTests, shared Android instrumented Room/Keystore suites... in a single coordinated batch after lanes."*
- `docs/auth-acceptance.md` (Line 59):
  *"Android Keystore Test | dev.doggo.shared.auth.AndroidKeystoreVaultTest | Android Instrumented | Phase 07"*

*Note on Hardware Assurance:* Execution on an Android emulator in Phase 07 will exercise the Android Keystore API implementation, but does not provide real TEE/hardware-backed security guarantees (which require physical hardware with StrongBox or TEE support).

---

## 4. Performance Metrics

- **Gradle Execution Time:** 3.50s wall time (3s Gradle reported).
- **Task Breakdown:** 12 actionable tasks, 12 executed.
- **Test Execution Speed:** 42 tests executed in ~1.2s JVM runtime. No timeouts or slow-running tests observed.

---

## 5. Build Warnings & Integrity Notes

1. **SLF4J Logger Provider Notice:**
   - Log: `SLF4J(W): No SLF4J providers were found. Defaulting to no-operation (NOP) logger implementation.`
   - Impact: Benign. Expected in unit tests where logback is intentionally excluded from test classpath.
2. **Gradle Deprecation Warnings:**
   - Log: `Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.`
   - Impact: KMP / AGP plugin deprecation notices under Gradle 8.11.1.
3. **Dependency Verification Leniency:**
   - Command requires `--dependency-verification=lenient`. Gradle reports 149 artifacts failing checksum verification on `classpath` and 251 on `jvmTestRuntimeClasspath`. This flag bypasses cryptographic integrity verification; artifact checksums must be reviewed and locked in `gradle/verification-metadata.xml` during Phase 07 integration.

---

## 6. Critical Issues & Blocking Items

- **JVM Scope:** None. All 42 unit and contract tests executed and passed with 100% pass rate.
- **Android Scope:** Android build and instrumented test execution are blocked on this host due to missing Android SDK (`ANDROID_HOME`). This is an expected boundary per project dependency matrix; full Android validation is deferred to Phase 07.

---

## 7. Recommendations

1. In Phase 07, configure `ANDROID_HOME` and provision emulator (`emulator-5554`) to execute `:shared:compileDebugAndroidTestKotlinAndroid` and `connectedDebugAndroidTest`.
2. Consolidate `gradle/verification-metadata.xml` during Phase 07 so dependency verification can run in strict mode (`--dependency-verification=strict`) rather than lenient mode.
3. Keep test dependencies minimal; NOP SLF4J warning does not impact test assertion validity.

---

## 8. Unresolved Questions

1. When Phase 07 begins, will the CI/test environment provide a headless Linux KVM-enabled emulator for `connectedDebugAndroidTest` or will an external physical device test runner be utilized?
2. Will the dependency verification metadata (`gradle/verification-metadata.xml`) be regenerated for all multiplatform configurations in Phase 07 or deferred until release engineering (Phase 08)?
