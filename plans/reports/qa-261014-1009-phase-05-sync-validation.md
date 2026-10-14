# QA Test Report: Phase 05 Durable Local State & Strict Per-Record Sync Validation

**Date:** 2026-10-14
**Target:** Phase 05 Durable Local State & Strict Per-Record Sync Test Suites
**Command:** `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks`
**Environment:** Linux x86_64 Fedora (`/home/loidinh/WS/doggo`), OpenJDK 21.0.8 (`$HOME/.local/share/jvm/jdk-21`), Gradle 8.11.1
**Scope:** Working tree containing Phase 05 shared implementation and common/JVM test suites.

---

## 1. Test Results Overview

| Test Suite | Class FQCN | Test Doubles / Backend | Total | Passed | Failed | Skipped | Status |
|---|---|---|---:|---:|---:|---:|:---:|
| **Hub Sync Client** | `dev.doggo.shared.sync.HubSyncClientTest` | Mock Ktor Engine | 6 | 6 | 0 | 0 | **PASS** |
| **Staged Full Scan** | `dev.doggo.shared.sync.StagedFullScanTest` | Fake DAO + Mock Ktor | 6 | 6 | 0 | 0 | **PASS** |
| **Outbox Reconciliation** | `dev.doggo.shared.sync.OutboxReconciliationTest` | Fake DAO | 6 | 6 | 0 | 0 | **PASS** |
| **Account Sync Coordinator** | `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Fake DAO + Mock Ktor | 5 | 5 | 0 | 0 | **PASS** |
| **Account Leave Protocol** | `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Fake DAO + Callback Spy | 5 | 5 | 0 | 0 | **PASS** |
| **Room Account Store (JVM)** | `dev.doggo.shared.data.RoomAccountStoreTest` | Real Bundled SQLite (JVM) | 7 | 7 | 0 | 0 | **PASS** |
| **Hub Auth Client** | `dev.doggo.shared.auth.HubAuthClientTest` | Mock Ktor Engine | 8 | 8 | 0 | 0 | **PASS** |
| **Hub Auth Session** | `dev.doggo.shared.auth.HubAuthSessionTest` | Fake Vault + Mock Ktor | 8 | 8 | 0 | 0 | **PASS** |
| **Single-Flight Refresh** | `dev.doggo.shared.auth.SingleFlightRefreshTest` | Coroutine Coordinator | 3 | 3 | 0 | 0 | **PASS** |
| **Account Key Contract** | `dev.doggo.shared.contract.AccountKeyTest` | Pure Common Kotlin | 5 | 5 | 0 | 0 | **PASS** |
| **Endpoint Policy Contract** | `dev.doggo.shared.contract.EndpointPolicyTest` | Pure Common Kotlin | 8 | 8 | 0 | 0 | **PASS** |
| **Strict Digest Contract** | `dev.doggo.shared.contract.StrictDigestTest` | Pure Common Kotlin | 5 | 5 | 0 | 0 | **PASS** |
| **Title Policy Contract** | `dev.doggo.shared.contract.TitlePolicyTest` | Pure Common Kotlin | 6 | 6 | 0 | 0 | **PASS** |
| **TOTAL (JVM Execution)** | *All 13 Suites* | — | **78** | **78** | **0** | **0** | **PASS (100%)** |

---

## 2. Phase 05 Sync Test Suites Execution Details

### 2.1 HubSyncClientTest (6/6 PASS)
- `testBootstrapSuccess`: PASS. Validates `POST /api/v1/sync/doggo/strong/bootstrap` with Dual-Auth (`Authorization: Bearer <jwt>`, `X-API-Key`, `X-App-Id`). Verifies parsing of `StrictSyncContract.PROTOCOL_VERSION ("strict-v1")`, `epoch`, and table registration.
- `testBootstrapProtocolMismatchFails`: PASS. Server returning non-"strict-v1" protocol version maps to `SafeError.ProtocolIncompatible`.
- `testPushSuccess`: PASS. Serializes push batch with Dual-Auth; parses 200 OK push response and unpacks per-record operation results (`accepted`, `acceptedRevision: 1`).
- `testPushBatchBoundsEnforcedClientSide`: PASS. Enforces push batch limit `<= 100` operations (`BATCH_TOO_LARGE`); enforces unique `rowId` per batch (`DUPLICATE_ROW_IDS`).
- `testPushErrorEnvelopesMapped`: PASS. HTTP 409 `epochMismatch` maps to `SafeError.ResetRequired`; HTTP 426 `protocolUnsupported` maps to `SafeError.ProtocolIncompatible`; HTTP 503 `notAdmitted` maps to `SafeError.Network`.
- `testPullSuccessAndPagination`: PASS. Pulls paginated record stream (`/api/v1/sync/doggo/strong/pull`); deserializes payload records, cursor token, and `hasMore` pagination boolean.

### 2.2 StagedFullScanTest (6/6 PASS)
*Note: Staged scan tests execute against in-memory FakeDoggoDao, testing staging state management and promotion logic.*
- `testFullScanMultiPageSuccess`: PASS. Pulls multi-page scan records into staging store; promotes staged records to `server_base`; verifies staging rows cleared after promotion.
- `testCursorLoopDetectionAborts`: PASS. Aborts scan when identical pagination cursor repeats with `hasMore: true`, returning `SafeError.ProtocolIncompatible("CURSOR_LOOP")` and cleaning staging rows.
- `testMissingPreScanConfirmedIdentityTriggersConsistencyError`: PASS. Catches omission of pre-scan confirmed row identity from full scan results; aborts promotion with fail-closed `SafeError.ResetRequired("CONSISTENCY_ERROR")`.
- `testMonotonicPromotionHigherRevisionReplaces`: PASS. Promotes server record with higher revision (`rev 2 > rev 1`), updating `server_base` state and title.
- `testMonotonicPromotionEqualRevisionDifferingLogicalDataFails`: PASS. Same revision with divergent payload rejected as `SafeError.ProtocolIncompatible("DIVERGENT_REVISION")`.
- `testMonotonicPromotionLowerRevisionRetainsLocal`: PASS. Server record with lower revision (`rev 2 < rev 3`) ignored, keeping newer local base.

### 2.3 OutboxReconciliationTest (6/6 PASS)
*Note: Reconciliation tests execute against in-memory FakeDoggoDao, verifying state transitions on outbox and base entities.*
- `testEligibleBatchFifoOrder`: PASS. Filters outbox to select the earliest pending operation per distinct `rowId` (`op-1` for `row-1` and `op-3` for `row-2`), excluding subsequent uncommitted operations on the same `rowId` (`op-2`).
- `testExactCasAckAdvancesBaseAndRemovesOutbox`: PASS. On server `accepted` result, updates `server_base` with `acceptedRevision` and removes corresponding outbox row.
- `testAckPreservesNewerDraft`: PASS. Acknowledged operation clears draft only if `draft.localRevision <= outbox.localRevision`; preserves newer uncommitted local draft (`localRevision: 11 > 10`).
- `testUncertainDeliveryConvertsSubsequentConflictToUnknownSuperseded`: PASS. When outbox operation previously marked `UNCERTAIN` receives subsequent server conflict, transitions to `UNKNOWN_SUPERSEDED` preventing accidental rollback.
- `testStandardConflictPreservesIntentForReview`: PASS. Fresh pending operation encountering revision conflict transitions to `CONFLICT` state for user review.
- `testRetryableFailureIncrementsFailuresAndRetainsUncertain`: PASS. On `RETRYABLE_FAILURE`, outbox marked `UNCERTAIN` and `consecutiveFailures` counter incremented.

### 2.4 AccountSyncCoordinatorTest (5/5 PASS)
- `testTriggerSyncSuccess`: PASS. Executes mocked sync cycle (bootstrap -> pull -> push -> pull); verifies final progress status is `IDLE` with `conflictCount: 0` and `safeError: null`.
- `testEpochMismatchTransitionsToResetRequired`: PASS. Bootstrap returning different epoch (`epoch-NEW != epoch-OLD`) transitions coordinator to `SyncStatus.RESET_REQUIRED` with `SafeError.ResetRequired("EPOCH_MISMATCH")`.
- `testConflictResolutionDiscard`: PASS. User discard resolution drops conflicted outbox row and decrements `conflictCount` to 0.
- `testConflictResolutionReapply`: PASS. User reapply resolution removes old conflicted op and enqueues new operation targeting current server revision (`expectedRevision: 5`).
- `testConflictResolutionCopyAsNew`: PASS. User copy-as-new resolution deletes conflicted op and generates brand new Todo with new UUID in outbox at `expectedRevision: 0`.

### 2.5 AccountLeaveProtocolTest (5/5 PASS)
- `testLeaveGuardedWhenReviewWorkPending`: PASS. Blocks account leave when uncommitted drafts or conflicts exist, returning false without triggering callback sequence.
- `testLeaveProceedsWhenZeroUnresolvedWork`: PASS. With zero unresolved work, verifies exact callback order: 1) write wipe marker, 2) cancel sync scheduler, 3) delete database artifacts, 4) clear wipe marker; asserts session transitions to `UNAUTHENTICATED`.
- `testDiscardAndLeaveProceedsWithPendingWork`: PASS. Explicit `discardAndLeave` overrides pending review guard and performs clean teardown callback sequence.
- `testMarkerWriteFailureAbortsTeardown`: PASS. Wipe marker I/O failure aborts teardown immediately, preventing database deletion callback.
- `testDatabaseDeletionFailureKeepsWipeMarkerFailClosed`: PASS. Database file deletion callback failure leaves wipe marker uncleared so next startup can execute cleanup fail-closed.

### 2.6 RoomAccountStoreTest - JVM Bundled SQLite (7/7 PASS)
*Note: Real bundled SQLite tests running on JVM filesystem via JvmAccountFiles and createJvmDatabaseBuilder.*
- `testSaveAndLoadTodosWithBundledSqlite`: PASS. Persists draft and outbox entity to real bundled SQLite on JVM; projects visible Todo with `isPending: true`.
- `testUnsentCreateDeleteCancelsLocally`: PASS. Unsent create immediately followed by local delete cancels both locally with zero leftover database rows.
- `testProcessRestartRecoveryWithFrozenOutbox`: PASS. Closes database handle while operation is `STATE_DISPATCHING`; reopens new database instance on same file; verifies persistence of frozen `DISPATCHING` operation and loaded Todo projection.
- `testReconcileAcceptedAdvancesServerBaseAndClearsOldDraft`: PASS. Acknowledges operation; moves state to `server_base`; purges outbox and matching draft.
- `testReconcileAcceptedPreservesNewerDraft`: PASS. Acknowledging earlier op does not overwrite newer uncommitted draft.
- `testWipeLocalDataClearsAllTables`: PASS. Wipes account tables (`todos_draft`, `todos_outbox`, `todos_server_base`) and confirms zero loaded todos.
- `testCloseAndWipeDatabaseFiles`: PASS. Closes database connection pool and deletes database artifacts; confirms absence of primary `.db` file.

---

## 3. Contract & Auth Test Suites Execution Details (43/43 PASS)

### 3.1 HubAuthClientTest (8/8 PASS)
- `testValidationHelpers`: PASS. Normalizes email casing and trimming; validates email format; enforces username length (3..32 chars) and character set; validates password byte bounds (8..128 bytes).
- `testRegisterSuccessAndHeaders`: PASS. Validates payload serialization and public auth header rules (no auth headers on register).
- `testLoginSuccessAndErrorHandling`: PASS. Parses login response DTO; maps 401 Unauthorized to `SafeError.Unauthorized`.
- `testRefreshSuccess`: PASS. Deserializes refreshed successor token bundle.
- `testGetMeWithBearerToken`: PASS. Attaches `Bearer <token>` to `/api/v1/auth/me`; parses app access list.
- `testAccessRequestCreationAndListing`: PASS. Tests app access request submission and query.
- `testEndpointAllowlistAndDualAuthHeaders`: PASS. Enforces strict sync endpoint allowlist; injects `X-API-Key` and `X-App-Id`.
- `testSanitizeErrorMessageStripsHtml`: PASS. Strips raw HTML error envelopes (e.g. 502 Bad Gateway) to prevent UI injection and log pollution.

### 3.2 HubAuthSessionTest (8/8 PASS)
- `testLoginSuccessWithDoggoAccess`: PASS. Full login with verified doggo app permission transitions session to `AuthState.AUTHENTICATED`.
- `testLoginSuccessWithoutDoggoAccessTransitionsToAccessPending`: PASS. Missing doggo permission transitions session to `AuthState.ACCESS_PENDING`.
- `testLoginMismatchedUserIdRejected`: PASS. Detects user ID mismatch between `/login` and `/me`, aborting session authentication.
- `testOfflineSessionRestore`: PASS. Restores authenticated session from cached vault credentials when offline.
- `testAuthorizedRequest401DoesNotReplayPostAutomatically`: PASS. Refreshing on 401 returns `TOKEN_REFRESHED` without auto-replaying POST, preserving caller idempotency.
- `testLocalLogoutClearsSessionAndVault`: PASS. Local logout transitions session to `AuthState.UNAUTHENTICATED` and removes credentials from vault.
- `testLogoutDuringRefreshPreventsStaleVaultResurrection`: PASS. Explicit logout while refresh in-flight fencing token generation prevents stale tokens from writing to vault.
- `testVaultWriteFailureRetainsSuccessorInMemory`: PASS. Vault disk write failure keeps refreshed tokens in memory for recovery without dropping session.

### 3.3 SingleFlightRefreshTest (3/3 PASS)
- `testConcurrentRefreshDeduplication`: PASS. 5 concurrent refresh calls collapse into exactly 1 network execution; all callers receive identical successor bundle.
- `testNetworkFailurePropagatedWithoutLoop`: PASS. Upstream refresh failure propagates as `SafeError.Unauthorized("REFRESH_REVOKED")` without entering retry loop.
- `testUnexpectedExceptionReturnsSafeError`: PASS. Unexpected I/O exception wrapped in `SafeError.Network("REFRESH_EXCEPTION")`.

### 3.4 Contract Suites (24/24 PASS)
- `AccountKeyTest` (5/5 PASS): Format validation (64-char hex SHA-256), determinism, collision prevention via length-prefixed framing, and invalid string rejection.
- `EndpointPolicyTest` (8/8 PASS): HTTPS origin enforcement in Debug and Release; trailing slash and port 443 normalization; localhost/10.0.2.2 allowance in Debug; rejection of cleartext in Release, userinfo, query params, fragments, and invalid ports.
- `StrictDigestTest` (5/5 PASS): NIST SHA-256 vectors; canonical length-framed big-endian digest computation; tombstone digest vector; payload sensitivity; invalid combinations rejection.
- `TitlePolicyTest` (6/6 PASS): Unicode whitespace trimming; astral emoji scalar counting; 200 scalar limit boundary; empty/whitespace-only rejection; malformed surrogate rejection; control character rejection.

---

## 4. Test Configuration & Build Notes

1. **Gradle Test Logging Configuration:**
   - Configured `tasks.withType<Test>` in `shared/build.gradle.kts` with `events("passed", "skipped", "failed")` to output individual test method names to the terminal log.
2. **Dependency Verification Leniency:**
   - Command requires `--dependency-verification=lenient` flag due to unverified transitive dependencies from Android/Room/Compose tooling on `classpath` and `jvmTestRuntimeClasspath`. Lockfiles (`shared/gradle.lockfile`) and checksum metadata (`gradle/verification-metadata.xml`) are separate mechanisms; verification metadata updates with trusted checksums remain scheduled for Phase 07.
3. **Android Instrumented Test Scope:**
   - `RoomAccountStoreTest` (4 Android instrumented tests) and `AndroidKeystoreVaultTest` (7 Android instrumented tests) in `shared/src/androidInstrumentedTest/` require an active Android runtime/emulator; deferred to Phase 07 coordinated execution.

---

## 5. Performance Metrics

- **Gradle Build Time:** 4s wall time (`--rerun-tasks`).
- **Task Breakdown:** 12 actionable tasks executed.
- **Failures / Regressions:** 0.

---

## 6. Critical Issues & Blocking Items

- None on JVM target. All 78 tests across 13 JVM common test suites passed (100% pass rate).

---

## 7. Recommendations

1. In Phase 07, execute Android instrumented test suites (`:shared:connectedDebugAndroidTest`) on emulator to validate Android Keystore AES-256-GCM and Android-specific Room database operations.
2. In Phase 07, validate trusted artifact checksums and update `gradle/verification-metadata.xml` to allow strict dependency verification mode.
3. Keep untracked Phase 05 implementation and test source files intact and tracked when committing.

---

## 8. Unresolved Questions

- None.
