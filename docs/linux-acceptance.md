# Linux Desktop & Installed RPM Acceptance Specification

**Document Version**: 1.3.0
**Phase**: 12 — Coordinated cutover, recovery runbook and release documentation
**Status**: Desktop Multiplatform Implementation, Process-Scoped Scheduler, and D-Bus Adapter Delivered; Gate G2 Live Desktop Visual Display & Gate G7 Production Cutover Pending Operator/Testbed Prerequisites
**Related Documents**: [Plan](../plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-09-desktop-scheduler-and-native-notifications.md) · [System Architecture](./system-architecture.md) · [Linux Desktop Guide](./linux-desktop-guide.md) · [Local Storage & Sync](./local-storage-and-sync.md)

---

## 1. Scope & Acceptance Criteria

Phase 09 delivers the desktop application module (`:desktopApp`) configured for Linux x86_64 with Compose Multiplatform 1.7.3, hardware-backed credential custody via Secret Service, foreground sync coordination, single instance locking, and fail-closed account leave lifecycle.

### Acceptance Invariants
1. **Target Isolation**:
   - `desktopTarget` defaults to detected host OS (`linux` or `windows`).
   - Mismatched or unsupported target requests (e.g. `-PdesktopTarget=macos` or `-PdesktopTarget=windows` on Linux) fail immediately before dependency resolution.
   - Compiled classes and packaged jars contain `LinuxSecretServiceVaultProvider` and zero references to Windows DPAPI classes.
2. **Deterministic Locking**:
   - `desktopApp/gradle-linux.lockfile` locks all 161 desktop configurations.
   - Builds reproduce cleanly without `--write-locks`.
3. **Hardware Vault Custody**:
   - `CredentialVaultProvider` SPI discovered via Java `ServiceLoader`.
   - Dispatcher enforces exactly one matching provider; fails closed with explicit error if zero or multiple providers are present.
   - Never falls back to plaintext token files.
4. **Single Instance Safety**:
   - `SingleInstanceGuard` acquires an OS file-channel lock on `tabbyrush.lock` BEFORE initializing database or vault.
   - Second launch detects lock and exits cleanly without racing sync or touching database.
   - Lock file is retained on exit to prevent inode recycling races.
5. **Foreground Sync & Quiescence**:
   - `DesktopSyncScheduler` coordinates periodic (15-min) and immediate sync rounds within window scope.
   - Implements `LeaveSchedulerCancelGate` to cancel and await active sync jobs before account leave or wipe.
6. **Immutable Mode & Launchers**:
   - `MainKt.main` enforces `BuildMode.RELEASE` (HTTPS only, strict endpoint validation).
   - `DevelopmentLauncher.main` enables `BuildMode.DEBUG` (HTTP 127.0.0.1:3000 permitted) via custom `runDebug` task.

---

## 2. Verification Evidence Matrix

| Gate | Requirement | Verification Method | Status |
|---|---|---|---|
| **Compilation** | Kotlin JVM 2.1.10, Compose 1.7.3, Room KMP | `./gradlew :desktopApp:compileKotlin` | PASS (Zero errors) |
| **Unit Test Suite** | Dispatcher, Lifecycle, Single Instance Guard, Vault | `./gradlew :desktopApp:test` | PASS (19/19 passing) |
| **Shared Multiplatform** | Core contracts, digests, Room SQLite | `./gradlew :shared:jvmTest` | PASS (96/96 passing) |
| **Dependency Locking** | `gradle-linux.lockfile` generated and verified | `./gradlew :desktopApp:dependencies` | PASS (161 locked artifacts) |
| **Target Isolation** | Unsupported target mismatch rejection | `-PdesktopTarget=windows` / `macos` | PASS (Fails immediately) |
| **App Distributable** | Self-contained bundled runtime image | `./gradlew :desktopApp:createDistributable` | PASS (Executable launcher verified) |
| **Package Smoke** | Diagnostics, JAR inspection, absence of Windows | `bash scripts/linux-package-smoke.sh --dist ...` | PASS |
| **RPM Packaging** | Native `.rpm` package generation | `./gradlew :desktopApp:packageRpm` | Pending `rpm-build` tool on host |
| **Installed RPM Test** | System installation via `dnf install` | `sudo dnf install` | Pending operator sudo credentials |


### 2.1 Phase 02 — Native Delivery & Notification Proof Status

| Gate | Requirement | Verification Method | Status |
|---|---|---|---|
| **D-Bus Notification Adapter** | Session bus communication with `org.freedesktop.Notifications`, capability probing, and missing-daemon error handling | `./gradlew :desktopApp:test --tests "dev.tabbyrush.desktop.linux.LinuxNotificationAdapterTest"` | PASS (4/4 tests passing) |
| **Workstation Bus Probe** | Introspect user D-Bus at `/run/user/1000/bus` for `org.freedesktop.Notifications` | `busctl --user introspect org.freedesktop.Notifications /org/freedesktop/Notifications` | Safely caught: `org.freedesktop.DBus.Error.ServiceUnknown` (Returns `LinuxNotificationStatus.Unavailable` without crash) |
| **Dependency Locking** | Reconciled `desktopApp/gradle-linux.lockfile` without Windows JNA dependencies | `./gradlew :desktopApp:test` (without `--write-locks`) | PASS (23/23 tests passing) |
| **Live Visual Notification Bubble** | Native notification bubble display and removal in active desktop session | Live desktop session notification | BLOCKED (Headless Fedora host has no active notification daemon; G2 gate prerequisite remaining) |
---
### 2.2 Phase 09 — Process-Scoped Desktop Scheduler & Native D-Bus Reminders

| Gate | Requirement | Verification Method | Status |
|---|---|---|---|
| **Desktop Reminder Scheduler** | Bounded 15s wake loop, bounded re-evaluation against backward clock drift, replace-based deadline wake, cancel-and-await quiescence | `./gradlew :desktopApp:test --tests "dev.tabbyrush.desktop.DesktopReminderSchedulerTest"` | PASS (5/5 tests passing) |
| **Composite Leave Cancel Gate** | Strict 5-step ordered quiescence: drain delivery gate -> cancel observer -> cancel reminder scheduler -> cancel sync scheduler -> cancel notifications | `./gradlew :desktopApp:test --tests "dev.tabbyrush.desktop.CompositeDesktopLeaveCancelGateTest"` | PASS (2/2 tests passing) |
| **Linux Reminder Adapter** | D-Bus notification posting, account-scoped tracking, capabilities probe tolerance for minimal daemons, close error propagation (`IllegalStateException`) | `./gradlew :desktopApp:test --tests "dev.tabbyrush.desktop.linux.LinuxReminderAdapterTest"` | PASS (6/6 tests passing) |
| **Low-Level D-Bus Notifications** | Low-level Freedesktop D-Bus session communication and missing daemon/session error mapping | `./gradlew :desktopApp:test --tests "dev.tabbyrush.desktop.linux.LinuxNotificationAdapterTest"` | PASS (4/4 tests passing) |
| **Controller Reminder Lifecycle** | Subsystem initialization, delivery gate linkage, startup wipe recovery notification cleanup, and fail-closed marker retention on cancellation error | `./gradlew :desktopApp:test --tests "dev.tabbyrush.desktop.DesktopReminderControllerLifecycleTest"` | PASS (3/3 tests passing) |
| **ServiceLoader SPI Provider** | Target-specific discovery of `LinuxReminderAdapterProvider` under Linux host | `PlatformReminderDispatcher.createAdapter("linux")` | PASS (Discovered and instantiated) |
| **Distributable Application Assembly** | Self-contained bundled application packaging with Linux reminder classes | `./gradlew -PdesktopTarget=linux :desktopApp:createDistributable` | PASS (Application bundle generated at `desktopApp/build/compose/binaries/main/app`) |
| **Live Visual Notification Bubble** | Native notification bubble display and removal in active desktop session | Live desktop session notification | BLOCKED (Headless workstation lacks active notification daemon; Gate G2 physical execution pending hardware/desktop testbed) |

#### Disclosed Limitations for Phase 09 Linux Reminders
1. **Process-Memory Notification Tracking**: `LinuxNativeReminderAdapter` tracks posted notification IDs in an in-memory set (`ConcurrentHashMap<String, MutableSet<Long>>`). If the desktop process is killed or crashes with active notifications displayed in the notification center, subsequent startup wipe recovery cannot discover or remove those orphaned notifications through this adapter.
2. **Individual Removal Status**: `removeOwnedNotification()` sends a close call to the D-Bus daemon but drops the in-memory tracking ID without checking whether the daemon succeeded, meaning removal failures are not surfaced to the caller.
3. **Host Notification Daemon Prerequisite**: On headless Linux installations lacking an active D-Bus session notification daemon (e.g., dunst, mako, or gnome-shell), `queryCapabilities()` safely reports `canPostNotifications = false` and `areNotificationsEnabled = false` without crashing. Live notification bubbles require an active desktop session (Gate G2, scheduled for Phase 11).

---

## 3. Disclosed Limitations & Operator Prerequisites

1. **Host `rpm-build` Package**:
   - The host workstation has `rpm` installed but lacks `rpmbuild` (`/usr/bin/rpmbuild`).
   - Sudo requires interactive authentication. Installing `rpm-build` via `sudo dnf install -y rpm-build` is required to run `:desktopApp:packageRpm`.
   - The self-contained unpacked distributable (`:desktopApp:createDistributable`) validates the complete binary bundle, JRE runtime, and launcher without requiring `rpmbuild`.
2. **Secret Service D-Bus Session**:
   - In headless non-GUI SSH environments without an active D-Bus session (`DBUS_SESSION_BUS_ADDRESS`), `LinuxSecretServiceVault` reports `VaultStatus.UNAVAILABLE` and rejects writes fail-closed.
   - For live GUI sessions, launch within an Xpra desktop session with GNOME Keyring enabled (`bash scripts/start-xpra.sh`).
3. **UI Evidence Visuals**:
   - Visual mockups in `docs/evidence/linux-*.png` represent the high-fidelity design baseline conforming to WCAG AA and ForumFlash guidelines.
