# QA Report: Post-Fix Regression Suite Verification (C1–C6 Scope)

**Identifier**: qa-261018-1548-c1-c6-regression-verification
**Date**: 2026-10-18
**Author**: RegressionTester (QA Engineer)
**Repositories**: TabbyRush (`/home/loidinh/WS/tabby-rush`), GleanHub (`/home/loidinh/WS/glean-hub`)
**Scope**: Verification suite executed across TabbyRush and GleanHub post-implementation of critical issues C1 through C6.

---

## Sequential Thinking Steps

1. **Step 1 - TabbyRush Shared JVM Tests**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --rerun-tasks --dependency-verification=strict`
   - Scope: 37 test suites covering sync protocol, scheduling, encryption keys, auth session, temporal models, remote contracts, Room database, and UI semantics.
   - Result: 286/286 passed, 0 failed, 0 skipped. Build time: 10s.
2. **Step 2 - TabbyRush Desktop Tests**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :desktopApp:test --tests "*DesktopRemoteRuntimeTest*" --tests "*Linux*NotificationAdapterTest" --dependency-verification=strict --rerun-tasks`
   - Scope: Filtered test run covering 3 test suites (`DesktopRemoteRuntimeTest`, `LinuxNotificationAdapterTest`, `LinuxRemoteNotificationAdapterTest`).
   - Result: 31/31 passed, 0 failed, 0 skipped. Build time: 9s.
3. **Step 3 - TabbyRush Release Security Audit**:
   - Unit tests: `node --test scripts/release-security-check.test.mjs` -> 21/21 passed, 0 failed.
   - Static audit: `node scripts/release-security-check.mjs --diagnose-source` -> 5 static checks passed, Result: CLEAN (diagnosed source tree; release APK not verified).
4. **Step 4 - TabbyRush Announcements Smoke Self-Test**:
   - Command: `node scripts/admin-announcements-smoke.mjs --self-test`
   - Scope: Ephemeral in-memory mock server verifying 13 golden vectors (`vectors-v1.json`) and 21 mock-server tests.
   - Result: 13 vectors + 21 mock tests passed, 0 failed.
5. **Step 5 - GleanHub Rust Notification Tests**:
   - Command: `cargo test -p glean-hub-notification`
   - Scope: Unittests (`src/lib.rs`), FCM transport integration (`tests/fcm_transport.rs`), Doc-tests.
   - Result: 35 passed, 0 failed, 1 ignored (doc-test).
6. **Step 6 - GleanHub Rust Server Tests**:
   - Integration tests: `cargo test -p glean-hub-server --test announcement_authorization --test announcement_stream --test announcement_storage` -> 44 passed across 3 test binaries (authorization: 19, storage: 12, stream: 13), 0 failed.
   - Binary tests: `cargo test -p glean-hub-server --bin glean-hub-server` -> 41 passed across 1 suite, 0 failed.
   - Result: 85 passed test executions, 0 failed.
7. **Step 7 - GleanHub Web Admin Unit Tests & Typecheck**:
   - Unit tests: `pnpm --dir /home/loidinh/WS/glean-hub/glean-hub-app test:unit` -> 48 passed across 14 suites, 0 failed.
   - Typecheck: `pnpm --dir /home/loidinh/WS/glean-hub/glean-hub-app exec tsc --noEmit` -> Clean exit, 0 errors.
8. **Step 8 - GleanHub Preflight Self-Test**:
   - Command: `node /home/loidinh/WS/glean-hub/scripts/announcement-preflight.mjs --self-test`
   - Result: 21 unit checks passed, 0 failed.
9. **Synthesis**:
   - Across the 8 executed targets, 548 test executions and 18 vector/static checks passed cleanly without failures. Validation confirms no regressions across the executed test surface. Platform/device gates (G2, G5) and live MongoDB/Hub connectivity remain unexercised in this suite.

---

## Test Results Overview

| Component / Test Suite | Exact Command | Discovered / Run | Passed | Failed | Skipped / Ignored | Result |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TabbyRush Shared JVM** | `JAVA_HOME=... ./gradlew :shared:jvmTest --rerun-tasks --dependency-verification=strict` | 286 | 286 | 0 | 0 | **PASS** |
| **TabbyRush Desktop (Filtered)** | `JAVA_HOME=... ./gradlew :desktopApp:test --tests "*DesktopRemoteRuntimeTest*" --tests "*Linux*NotificationAdapterTest" --dependency-verification=strict --rerun-tasks` | 31 | 31 | 0 | 0 | **PASS** |
| **TabbyRush Security Unit** | `node --test scripts/release-security-check.test.mjs` | 21 | 21 | 0 | 0 | **PASS** |
| **TabbyRush Security Source Audit** | `node scripts/release-security-check.mjs --diagnose-source` | 5 checks | 5 | 0 | 0 | **PASS (CLEAN)** |
| **TabbyRush Smoke Self-Test** | `node scripts/admin-announcements-smoke.mjs --self-test` | 13 vectors + 21 mock tests | 34 | 0 | 0 | **PASS** |
| **GleanHub Rust Notification** | `cargo test -p glean-hub-notification` | 36 | 35 | 0 | 1 doc-test | **PASS** |
| **GleanHub Rust Server Integration** | `cargo test -p glean-hub-server --test announcement_authorization --test announcement_stream --test announcement_storage` | 44 | 44 | 0 | 0 | **PASS** |
| **GleanHub Rust Server Binary** | `cargo test -p glean-hub-server --bin glean-hub-server` | 41 | 41 | 0 | 0 | **PASS** |
| **GleanHub Web Admin Unit** | `pnpm --dir glean-hub-app test:unit` | 48 | 48 | 0 | 0 | **PASS** |
| **GleanHub Web Admin Typecheck** | `pnpm --dir glean-hub-app exec tsc --noEmit` | Workspace tsc | Clean (0 err) | 0 | 0 | **PASS** |
| **GleanHub Preflight Self-Test** | `node scripts/announcement-preflight.mjs --self-test` | 21 | 21 | 0 | 0 | **PASS** |

### Verification Totals
- **Passing Test Executions**: **548** passed (286 JVM + 31 Desktop + 21 Security + 21 Smoke Mock + 35 Rust Notification + 44 Rust Server Integration + 41 Rust Server Binary + 48 Web Admin + 21 Preflight), 0 failed, 1 ignored doc-test.
  *(Note: Model unit tests such as BSON date representations recur across separate Rust test binaries).*
- **Golden Vector & Static Source Checks**: **18** passed (13 golden vectors in `vectors-v1.json`, 5 static source security rules).
- **Total Passing Verifications**: **566** passed (548 test executions + 18 vector/static checks), 0 failures.

---

## Detailed Suite Breakdown

### 1. TabbyRush Shared JVM Tests (`:shared:jvmTest`)
- **Status**: BUILD SUCCESSFUL in 10s (15 actionable tasks executed).
- **Counts**: 37 test suites, 286 tests passed, 0 failures, 0 skipped.
- **Key Suites**:
  - `RemoteContractVectorTest`: 8 tests passed.
  - `RemoteInboxClientTest`: 10 tests passed.
  - `RemoteInboxControllerTest`: 7 tests passed.
  - `RemoteStreamClientTest`: 8 tests passed.
  - `RemoteInboxNavigationTest`: 14 tests passed.
  - `AdversarialSyncSqliteTest`: 5 tests passed.
  - `RoomAccountStoreTest`: 26 tests passed.

### 2. TabbyRush Desktop Tests (`:desktopApp:test`)
- **Status**: BUILD SUCCESSFUL in 9s (19 actionable tasks executed).
- **Scope**: Filtered tests matching specified patterns `--tests "*DesktopRemoteRuntimeTest*" --tests "*Linux*NotificationAdapterTest"`.
- **Counts**: 3 test suites, 31 tests passed, 0 failures, 0 skipped.
  - `DesktopRemoteRuntimeTest`: 7 tests.
  - `LinuxNotificationAdapterTest`: 6 tests.
  - `LinuxRemoteNotificationAdapterTest`: 18 tests.

### 3. TabbyRush Security Checks
- **Unit Test Runner**: `scripts/release-security-check.test.mjs` - 21 tests passed across 1 suite.
- **Static Source Audit**: `scripts/release-security-check.mjs --diagnose-source` - 5 static checks passed (CLEAN). Validates source manifest cleartext restriction, backup restriction, exported components, XML configuration rules, and absence of hardcoded source secrets.

### 4. TabbyRush Smoke Self-Test
- **Status**: PASS.
- **Scope**: Evaluates golden contract vectors and in-memory mock server.
- **Counts**: 13 vectors verified, 21 mock-server tests passed, 0 failed.

### 5. GleanHub Rust Notification Tests (`glean-hub-notification`)
- **Status**: Finished in 0.08s.
- **Counts**:
  - `src/lib.rs` (unittests): 14 passed.
  - `tests/fcm_transport.rs` (integration): 21 passed.
  - Doc-tests: 1 ignored.

### 6. GleanHub Rust Server Tests (`glean-hub-server`)
- **Integration Tests**: 44 passed across 3 suites.
  - `tests/announcement_authorization.rs`: 19 passed.
  - `tests/announcement_storage.rs`: 12 passed.
  - `tests/announcement_stream.rs`: 13 passed.
- **Binary Unit Tests**: `glean-hub-server --bin glean-hub-server`: 41 passed across 1 suite.
- **Total Server Test Executions**: 85 passed, 0 failed.
- **Live Database Note**: `mongo_repository_*_when_available` tests fall back to no-op stubs when live MongoDB is not present in the local test environment. Live MongoDB integration was not exercised.

### 7. GleanHub Web Admin Unit Tests & Typecheck
- **Unit Tests**: `test:unit` - 48 passed across 14 suites, 0 failed.
- **TypeScript Typecheck**: `exec tsc --noEmit` - Clean exit, 0 errors.

### 8. GleanHub Preflight Self-Test
- **Status**: PASS.
- **Counts**: 21 unit checks passed (credential scrubbing, index schema validation, CLI option parsing).

---

## Validation Boundaries & Scope Limitations
- **Filtered Scope**: TabbyRush desktop suite was filtered to remote runtime and Linux notification adapter tests; full desktop app test suite was not executed.
- **Static & Mock Validation**: Release security audit evaluated source manifest and rules, not a compiled release APK. Smoke test and preflight ran in self-test/mock harness mode.
- **Unexercised Targets**: Android instrumentation tests, release APK build (`assembleRelease`), Windows native packaging/runtime tests, live browser E2E, and live network/MongoDB cluster interactions were not part of this command set.
- **Release Holds**: Hardware acceptance gates G2 (Android physical device) and G5 (Windows desktop runtime) remain held pending physical environment verification.

## Coverage Metrics
- Formal coverage reports (lcov/JaCoCo/tarpaulin) not requested or generated in this run.

## Failed Tests
- **None**: 0 failed test executions across all 8 executed targets.

## Performance Metrics
- `TabbyRush :shared:jvmTest`: 10.4s
- `TabbyRush :desktopApp:test`: 9.3s
- `TabbyRush Security Checks`: ~0.1s
- `GleanHub cargo test -p glean-hub-notification`: 0.08s
- `GleanHub cargo test -p glean-hub-server (integration)`: 0.14s
- `GleanHub cargo test -p glean-hub-server (bin)`: 0.25s
- `GleanHub Web Admin unit`: 0.22s
- `GleanHub Web Admin tsc`: 11.17s
- `GleanHub Preflight self-test`: ~0.1s
- **Total Suite Execution Time**: ~32s across both repositories.

## Build Status & Compiler Warnings
- **Build Status**: All executed test tasks and builds completed successfully.
- **Warnings**:
  - Gradle deprecation warnings regarding Gradle 9.0 compatibility.
  - Kotlin expect/actual warning for Room database constructor.
  - Rust compiler warnings in `glean-hub-server`: Unused imports and unused dead-code methods in `src/models/admin_announcement.rs`, `src/services/announcement_repository.rs`, `src/services/announcement_devices.rs`, `src/services/announcement_stream.rs`, and test files.

## Critical Issues
- **None observed within executed scope**: All 8 suites passed cleanly with 0 failures.

## Recommendations
1. Address Rust compiler unused warnings in `glean-hub-server` (`cargo fix` or removing dead helper methods/imports).
2. Exercise live MongoDB test instances in staging environment to validate MongoDB repository integration paths.

## Next Steps
1. Hand off regression verification summary to Main Agent.
2. Coordinate with platform owners for hardware gate verifications (G2, G5) when physical devices are staged.

---

## Unresolved Questions
- None.
