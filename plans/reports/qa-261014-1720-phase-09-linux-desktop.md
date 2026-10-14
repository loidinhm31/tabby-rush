# QA Test Report: Phase 09 Linux Desktop Test Suite & Packaging Diagnostics

**Date:** 2026-10-14  
**Target:** Phase 09 — Linux Desktop Distribution Test Suite & Host Packaging Diagnostics  
**QA Agent:** Phase09TestRunner  
**Verdict:** **TEST SUITE GREEN (115/115 PASS)** — Unit and integration tests passed; host environment diagnostics completed; packaged distribution not validated.

---

## Sequential Thinking Problem Decomposition

1. **Target Identification & Scope**:
   - `:desktopApp:test` with `-PdesktopTarget=linux`: Desktop lifecycle, vault dispatcher, and Linux Secret Service unit/mock tests.
   - `:shared:jvmTest`: Multiplatform shared domain, protocol, database, and auth suites on JVM.
   - `scripts/linux-package-smoke.sh --diagnose`: Host runtime, packaging prerequisites, D-Bus session, and display diagnostics.
2. **Execution & Evidence Gathering**:
   - Initial `:desktopApp:test` run completed with 19 tasks up-to-date.
   - Forced re-execution via `--rerun-tasks` with `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21` and `--dependency-verification=off` to produce fresh test logs.
   - Re-executed `:shared:jvmTest` via `--rerun-tasks` with `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21` and `--dependency-verification=off`.
   - Executed `scripts/linux-package-smoke.sh --diagnose`.
3. **Deprecation & Warning Analysis**:
   - Identified Kotlin Beta expect/actual class warnings and Java deprecated `URL` constructor usage in test code.
4. **Environment Readiness Assessment**:
   - Evaluated rpm tool version, missing `rpmbuild`, present `secret-tool`, and unbound D-Bus/display in headless session.

---

## 1. Test Results Overview

| Test Target / Component | Execution Command | Total Tests | Passed | Failed | Skipped | Status | Execution Time |
|---|---|---:|---:|---:|---:|:---:|---:|
| **Desktop Linux Tests** | `JAVA_HOME=... ./gradlew -PdesktopTarget=linux :desktopApp:test --dependency-verification=off --rerun-tasks` | 19 | 19 | 0 | 0 | **PASS** | 11.72s |
| **Shared JVM Tests** | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | 96 | 96 | 0 | 0 | **PASS** | 8.77s |
| **Linux Smoke Diagnostics** | `bash scripts/linux-package-smoke.sh --diagnose` | — | — | — | — | **DIAGNOSED** | < 0.1s |
| **TOTAL TESTS** | — | **115** | **115** | **0** | **0** | **PASS** | **20.49s** |

---

## 2. Desktop Test Suite Breakdown (`:desktopApp:test` — 19/19 PASS)

All 19 tests executed and passed under `desktopApp`:

| Test Class FQCN | Test Name | Result | Duration / Notes |
|---|---|:---:|---|
| `dev.doggo.desktop.DesktopLifecycleTest` | `testReleaseModeDeniesHttpLoopback` | PASSED | Cleartext HTTP blocked in release mode |
| `dev.doggo.desktop.DesktopLifecycleTest` | `testSyncSchedulerCancelAndAwaitQuiescence` | PASSED | Coroutine scope cancellation & quiescence validated |
| `dev.doggo.desktop.DesktopLifecycleTest` | `testSingleInstanceGuardLockFileRetainedOnClose` | PASSED | FileLock released while lock file remains on disk |
| `dev.doggo.desktop.DesktopLifecycleTest` | `testDebugModePermitsHttpLoopback` | PASSED | Loopback HTTP permitted in debug mode |
| `dev.doggo.desktop.DesktopLifecycleTest` | `testSyncSchedulerPeriodicInterval` | PASSED | Periodic polling loop trigger timing verified |
| `dev.doggo.desktop.DesktopLifecycleTest` | `testSyncSchedulerImmediateTrigger` | PASSED | Immediate outbox drain dispatch verified |
| `dev.doggo.desktop.DesktopLifecycleTest` | `testSingleInstanceGuardAcquireAndPreventConcurrentLaunch` | PASSED | Second instance fails to acquire lock |
| `dev.doggo.desktop.DesktopLifecycleTest` | `testReleaseModePermitsHttps` | PASSED | HTTPS allowed in release mode |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | `testResolveProviderUnknownTargetFailsClosed` | PASSED | Unknown target fails closed |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | `testResolveProviderMultipleMatchesFailsClosed` | PASSED | Ambiguous providers fail closed |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | `testResolveProviderNoMatchFailsClosed` | PASSED | Missing provider fails closed |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | `testDetectHostTargetOnLinux` | PASSED | Host detection identifies "linux" |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | `testResolveProviderRealLinuxProviderDiscovered` | PASSED | META-INF/services discovery finds Linux provider |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | `testVaultStatusTransitions` | PASSED | Initialized / Closed lifecycle states |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | `testAtomicStoreAndLoad` | PASSED | Secret payload storage & round-trip retrieval |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | `testAtomicReplacement` | PASSED | In-place secret update overwrites previous |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | `testClearRemovesSecret` | PASSED | Secret removal returns null on subsequent read |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | `testMissingSecretReturnsNull` | PASSED | Non-existent secret lookup returns null |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | `testCorruptPayloadFailsClosed` | PASSED | Invalid JSON in vault fails closed |

*Note on vault tests:* `LinuxSecretServiceVaultTest` exercises the vault provider logic with in-memory / mock state; it does not test interaction with a live D-Bus Secret Service daemon.

---

## 3. Shared JVM Test Suite Breakdown (`:shared:jvmTest` — 96/96 PASS)

| Test Class FQCN | Domain / Subsystem | Total Tests | Passed | Failed | Skipped | Status |
|---|---|---:|---:|---:|---:|:---:|
| `dev.doggo.shared.auth.HubAuthClientTest` | Hub Auth HTTP Client & Envelopes | 8 | 8 | 0 | 0 | PASSED |
| `dev.doggo.shared.auth.HubAuthSessionTest` | Auth Session State & Transitions | 8 | 8 | 0 | 0 | PASSED |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | Mutex Token Refresh Deduplication | 3 | 3 | 0 | 0 | PASSED |
| `dev.doggo.shared.contract.AccountKeyTest` | Deterministic Account Key Hashing | 5 | 5 | 0 | 0 | PASSED |
| `dev.doggo.shared.contract.EndpointPolicyTest` | Endpoint Validation & Scheme Fencing | 8 | 8 | 0 | 0 | PASSED |
| `dev.doggo.shared.contract.StrictDigestTest` | Record SHA-256 Digest & NIST Vectors | 5 | 5 | 0 | 0 | PASSED |
| `dev.doggo.shared.contract.TitlePolicyTest` | Unicode / Astral Emoji Title Rules | 6 | 6 | 0 | 0 | PASSED |
| `dev.doggo.shared.data.RoomAccountStoreTest` | BundledSQLite Room Store & Wiping | 9 | 9 | 0 | 0 | PASSED |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Fail-Closed Account Teardown Protocol | 7 | 7 | 0 | 0 | PASSED |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Orchestration & Conflict Resolution | 5 | 5 | 0 | 0 | PASSED |
| `dev.doggo.shared.sync.HubSyncClientTest` | Strict-v1 Protocol Client & Pagination | 6 | 6 | 0 | 0 | PASSED |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | FIFO CAS Outbox Reconciliation | 10 | 10 | 0 | 0 | PASSED |
| `dev.doggo.shared.sync.StagedFullScanTest` | Full Cursor Monotonic Promotion | 9 | 9 | 0 | 0 | PASSED |
| `dev.doggo.shared.ui.TodoSemanticsTest` | Accessible Light UI & WCAG Contrast | 7 | 7 | 0 | 0 | PASSED |
| **TOTAL** | — | **96** | **96** | **0** | **0** | **PASSED** |

---

## 4. Packaging Diagnostics (`scripts/linux-package-smoke.sh --diagnose`)

```
=== Doggo Linux Desktop Environment Diagnostics ===
1. Host OS: Linux 7.1.10-200.fc44.x86_64 (x86_64)
2. Java Runtime: openjdk version "25.0.4.1" 2026-08-18
3. jpackage tool: 25.0.4.1
4. RPM query tool (rpm): RPM version 6.0.2
5. RPM build tool (rpmbuild): NOT INSTALLED (Required for :desktopApp:packageRpm; install via 'sudo dnf install -y rpm-build')
6. Secret Service CLI (secret-tool): PRESENT (/usr/bin/secret-tool)
7. D-Bus session bus: NOT CONNECTED (Headless / non-GUI session; run inside Xpra or desktop terminal)
8. X11 / Wayland Display: NOT SET (Use Xpra display :100 or native desktop display)
=== Diagnostics Complete ===
```

### Diagnostic Findings & Packaging Readiness:
- **Host**: Linux 7.1.10 x86_64 (Fedora 44).
- **Java / jpackage**: Diagnostic script queries default PATH, discovering OpenJDK 25.0.4.1 and jpackage 25.0.4.1. Gradle tasks explicitly run with target JDK 21 LTS (`JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21`).
- **RPM Query vs. Build Tool**: `rpm` binary is installed (RPM version 6.0.2). However, `rpmbuild` is missing (`rpm-build` package not installed on host). `:desktopApp:packageRpm` cannot succeed on this host until `rpm-build` is installed.
- **Secret Service / D-Bus**: `secret-tool` CLI binary is present at `/usr/bin/secret-tool`. D-Bus session bus address (`DBUS_SESSION_BUS_ADDRESS`) is unset in this session.
- **Display**: Neither `DISPLAY` nor `WAYLAND_DISPLAY` is configured in current environment.
- **Scope Note**: `linux-package-smoke.sh --diagnose` evaluates host tool availability; it does not build or inspect RPM or distributable artifacts.

---

## 5. Failed Tests & Error Analysis

- **Failed Tests**: **0**
- **Skipped Tests**: **0**
- **Intermittent / Flaky Tests**: None observed.

---

## 6. Build Warnings & Deprecations

1. **Kotlin Beta Expect/Actual Warning**:
   - `DoggoDatabaseConstructor.kt:5:8` and `doggo-database.kt:30:1`:
     `'expect'/'actual' classes ... are in Beta. You can use -Xexpect-actual-classes flag to suppress this warning.`
2. **Java Deprecated Constructor**:
   - `platform-vault-dispatcher-test.kt:68:31`:
     `'constructor(p0: String!, p1: String!, p2: Int, p3: String!, p4: URLStreamHandler!): URL' is deprecated.`
3. **Gradle Deprecations**:
   - Features incompatible with Gradle 9.0 used in build plugins.

---

## 7. Critical Issues & Limitations

- **Unit/Integration Test Execution**: Fully functional and passing (115/115).
- **Distribution Build Blocker**: `:desktopApp:packageRpm` cannot generate native RPM packages on this host due to missing `rpmbuild`.
- **Runtime Environment**: Executing live desktop application with native Secret Service integration requires active D-Bus session and X11/Wayland display.

---

## 8. Recommendations

1. **Host Setup for Packaging**:
   - Install `rpm-build` (`sudo dnf install -y rpm-build`) to enable `:desktopApp:packageRpm`.
2. **Code Cleanup**:
   - Update `platform-vault-dispatcher-test.kt:68` to use `URI.create(...).toURL()`.
   - Add `-Xexpect-actual-classes` compiler argument.
3. **Distribution Smoke Verification**:
   - Once package is built, execute `scripts/linux-package-smoke.sh --rpm <path-to-rpm>` and `--dist <path-to-dist>` to validate package contents and ensure no Windows artifacts leak into Linux bundle.

---

## Unresolved Questions

1. Is RPM packaging intended to be built directly on developer workstations or in containerized CI runners where `rpm-build` is provisioned?
2. Is live D-Bus Secret Service smoke testing required in Phase 09, or is mock-level vault provider test coverage sufficient before release?
