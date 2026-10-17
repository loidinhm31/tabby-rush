# Windows Desktop Verification Test Summary

**Date**: 2026-10-14
**Phase**: 10 — Native Windows DPAPI and installed MSI
**Host**: Windows 11 Build 26200 (AMD64)
**Java**: Oracle OpenJDK 21.0.9 LTS (G:\env\jdk-21.0.9)

---

## 1. Automated Test Execution

### 1.1 Desktop App Unit Suite (`:desktopApp:test`)
Total 25 desktop tests executed across 3 test classes, 25/25 PASSED (100%):
The report records task names and totals but not exact command lines or the Gradle dependency-verification mode; root-level strict dependency verification remains a separate pending prerequisite. `testDetectHostTargetOnLinux` is a no-op on Windows and is not evidence of Linux execution.
- **`PlatformVaultDispatcherTest` (5/5 passed)**:
  - `testDetectHostTargetOnLinux`: Identifies "linux" on Linux host, no-op on Windows.
  - `testResolveProviderRealLinuxProviderDiscovered`: Discovers Linux provider when running on Linux.
  - `testResolveProviderNoMatchFailsClosed`: Fails closed with `IllegalStateException` when 0 providers match target.
  - `testResolveProviderUnknownTargetFailsClosed`: Fails closed when target is unrecognized.
  - `testResolveProviderMultipleMatchesFailsClosed`: Fails closed with `IllegalStateException` when duplicate providers register.

- **`DesktopLifecycleTest` (8/8 passed)**:
  - `testSingleInstanceGuardAcquireAndPreventConcurrentLaunch`: Exclusive lock acquisition; second launch returns null.
  - `testSingleInstanceGuardLockFileRetainedOnClose`: Retains lock file to avoid race conditions.
  - `testSyncSchedulerImmediateTrigger`: Triggers foreground immediate sync round.
  - `testSyncSchedulerPeriodicInterval`: Executes periodic foreground sync intervals and cancels cleanly.
  - `testSyncSchedulerCancelAndAwaitQuiescence`: Implements `LeaveSchedulerCancelGate` awaiting in-flight jobs.
  - `testReleaseModeDeniesHttpLoopback`: Enforces HTTPS only in RELEASE build mode.
  - `testReleaseModePermitsHttps`: Permits canonical Hub HTTPS origin.
  - `testDebugModePermitsHttpLoopback`: Permits HTTP 127.0.0.1:3000 in DEBUG mode.

- **`WindowsDpapiVaultTest` (12/12 passed)**:
  - `testStoreAndLoadRoundTrip`: Verifies symmetric encryption/decryption of `CredentialBundle`.
  - `testAtomicReplacement`: Replaces existing token payload atomically with new credentials.
  - `testMissingSecretReturnsNull`: Non-existent account credentials return null cleanly.
  - `testClearRemovesVaultFile`: Synchronous token removal deletes file and returns null on subsequent read.
  - `testCorruptPayloadFailsClosed`: Non-JSON or tampered decrypted payload throws `IllegalStateException` and sets `status = CORRUPT`.
  - `testUnprotectFailureTransitionsToCorrupt`: Decryption failure (e.g. wrong Windows user) throws and sets `status = CORRUPT`.
  - `testAccountKeyMismatchFailsClosed`: Decrypted payload from another account key is rejected with `IllegalStateException`.
  - `testUnavailableProtectorThrows`: Unavailability of DPAPI provider fails closed immediately.
  - `testServiceLoaderDiscoversWindowsProvider`: Java ServiceLoader resolves `WindowsDpapiVaultProvider` with target `"windows"`.
  - `testLinuxProviderAbsentOnWindows`: Ensures Linux Secret Service provider is not present in Windows classpath.
  - `testLiveJnaDpapiRoundtripOnWindows`: Executes live native Windows DPAPI `CryptProtectData`/`CryptUnprotectData` roundtrip on the active Windows host.
  - `testAclEnforcementOnStoredVaultFile`: Confirms the stored vault ACL includes an ALLOW entry for the current owner on ACL-capable filesystems; this does not establish owner-only access or test failure paths.

### 1.2 Shared Core Suite (`:shared:jvmTest`)
Total 96/96 tests passed across Room migrations, SQLite transactions, outbox reconciliation, staged full scans, single-flight refresh, and endpoint policies.
Combined desktop and shared JVM suites: 121/121 reported passing.

---

## 2. Artifact and Provenance Evidence

### 2.1 Dependency Lockfile
- Path: `desktopApp/gradle-windows.lockfile`
- Total Locked Artifacts: 160
- Target: Windows x64
- Repeatability: Verified without `--write-locks`.
- Root-level strict dependency verification: Not completed or reported in this summary; any verification metadata gaps remain to be resolved.

### 2.2 Distributable Application Bundle
- Task: `:desktopApp:createDistributable`
- Output Path: `desktopApp/build/compose/binaries/main/app/TabbyRush/`
- Launcher: `TabbyRush.exe` (native PE executable)
- Bundled JRE: Verified present at `runtime/bin/server/jvm.dll`
- Package Audit: `scripts/windows-package-smoke.ps1 -DistDir ...` verified zero Linux D-Bus jars and confirmed presence of Windows DPAPI classes and SPI descriptor.

### 2.3 MSI Installer Package Status
- Task: `:desktopApp:packageMsi`
- Output Path: `desktopApp/build/compose/binaries/main/msi/TabbyRush-1.0.0.msi`
- File Size: 100.68 MB (105,566,496 bytes)
- SHA-256 Digest: `698A29B8B67AC32E8F175BCA56651B066C91479E3793EC5B30F7676FB194B240`
- Status: PASS (Successfully generated via WiX 3.11 binaries downloaded by Compose Desktop plugin)
- Source revision associated with the MSI SHA-256 is not recorded in this summary.

## 3. UI Evidence Matrix
Seven high-fidelity 960x720 Windows 11 Fluent mockups generated in `docs/evidence/`:
- `docs/evidence/windows-empty.png`: 33,100 bytes (Clean empty state)
- `docs/evidence/windows-populated.png`: 51,334 bytes (Populated task list)
- `docs/evidence/windows-completed.png`: 46,627 bytes (Completed filter view)
- `docs/evidence/windows-editor.png`: 50,763 bytes (Inline task editor modal)
- `docs/evidence/windows-error.png`: 65,034 bytes (Recoverable error banner)
- `docs/evidence/windows-conflict.png`: 84,456 bytes (Strict-v1 conflict review modal)
- `docs/evidence/windows-expanded.png`: 73,828 bytes (Two-pane expanded desktop view)
