# Linux Desktop Verification Test Summary

**Date**: 2026-10-14
**Phase**: 09 — Linux desktop and installed RPM
**Host**: Linux 7.1.10-200.fc44.x86_64
**Java**: OpenJDK 21.0.12.1 LTS (Temurin)

---

## 1. Automated Test Execution

### 1.1 Desktop App Unit Suite (`:desktopApp:test`)
Total 19 tests executed across 3 test classes, 19/19 PASSED (100%):

- **`PlatformVaultDispatcherTest` (5/5 passed)**:
  - `testDetectHostTargetOnLinux`: Identifies "linux" on Linux host.
  - `testResolveProviderRealLinuxProviderDiscovered`: Resolves `LinuxSecretServiceVaultProvider` via ServiceLoader.
  - `testResolveProviderNoMatchFailsClosed`: Fails closed with `IllegalStateException` when 0 providers match target "windows".
  - `testResolveProviderUnknownTargetFailsClosed`: Fails closed when target is unrecognized.
  - `testResolveProviderMultipleMatchesFailsClosed`: Fails closed with `IllegalStateException` when duplicate providers register.

- **`DesktopLifecycleTest` (8/8 passed)**:
  - `testSingleInstanceGuardAcquireAndPreventConcurrentLaunch`: Exclusive lock acquisition; second launch returns null.
  - `testSingleInstanceGuardLockFileRetainedOnClose`: Retains lock file to avoid inode race conditions.
  - `testSyncSchedulerImmediateTrigger`: Triggers foreground immediate sync round.
  - `testSyncSchedulerPeriodicInterval`: Executes periodic foreground sync intervals and cancels cleanly.
  - `testSyncSchedulerCancelAndAwaitQuiescence`: Implements `LeaveSchedulerCancelGate` awaiting in-flight jobs.
  - `testReleaseModeDeniesHttpLoopback`: Enforces HTTPS only in RELEASE build mode.
  - `testReleaseModePermitsHttps`: Permits canonical Hub HTTPS origin.
  - `testDebugModePermitsHttpLoopback`: Permits HTTP 127.0.0.1:3000 in DEBUG mode.

- **`LinuxSecretServiceVaultTest` (6/6 passed)**:
  - `testVaultStatusTransitions`: Validates READY, LOCKED, UNAVAILABLE transitions.
  - `testAtomicStoreAndLoad`: Stores access and refresh tokens as an atomic JSON bundle and loads them.
  - `testAtomicReplacement`: Replaces existing token bundle without partial state.
  - `testCorruptPayloadFailsClosed`: Throws `IllegalStateException` on corrupt JSON payload.
  - `testMissingSecretReturnsNull`: Returns null when no secret exists for account.
  - `testClearRemovesSecret`: Deletes credentials synchronously.

### 1.2 Shared Core Suite (`:shared:jvmTest`)
Total 96/96 tests passed across Room migrations, SQLite transactions, outbox reconciliation, staged full scans, single-flight refresh, and endpoint policies.

---

## 2. Artifact and Provenance Evidence

### 2.1 Dependency Lockfile
- Path: `desktopApp/gradle-linux.lockfile`
- Total Locked Artifacts: 161
- Target: Linux x86_64
- Repeatability: Verified without `--write-locks`.

### 2.2 Distributable Application Bundle
- Target Directory: `desktopApp/build/compose/binaries/main/app/TabbyRush`
- Launcher: `bin/TabbyRush` (executable application launcher)
- Package Audit: `scripts/linux-package-smoke.sh --dist ...` verified presence of `LinuxSecretServiceVaultProvider` and complete absence of Windows DLLs or DPAPI classes.

---

## 3. Disclosed Limitations & Environment Gates

1. **RPM Package Assembly (`:desktopApp:packageRpm`)**:
   - Host has `rpm` (v6.0.2) installed, but lacks `rpmbuild` (`rpm-build` package). Sudo requires interactive authentication.
   - Live RPM package generation and `sudo dnf install` remain pending operator prerequisites.
2. **Display & D-Bus Session**:
   - In headless shell without `DISPLAY` and `DBUS_SESSION_BUS_ADDRESS`, Secret Service daemon and Xpra framebuffer capture are not active.
   - UI evidence images in `docs/evidence/linux-*.png` are high-fidelity design baseline renders conforming to ForumFlash light theme and WCAG AA contrast guidelines.
