# TabbyRush Linux Desktop Guide & Runbook

**Document Version**: 1.0.0
**Target Operating System**: Linux (Fedora 40+, RHEL 9+, openSUSE, Ubuntu 22.04+)
**Architecture**: x86_64
**Status**: Current client uses standard Hub sync. Linux interactive session acceptance remains separate from source, package, and automated-test evidence.

---

## 1. Overview

The TabbyRush Linux desktop application is built with Compose Multiplatform 1.7.3 and targets the Java 21 LTS runtime. It brings the offline-first task-management experience and standard Glean Hub push/pull/delta synchronization to Linux workstations.

### Key Architectural Invariants
1. **Hardware/OS-Backed Credential Custody**: Tokens are stored exclusively in the Freedesktop Secret Service (via `secret-tool` / D-Bus). Plaintext fallback to files or preferences is strictly prohibited.
2. **Single Instance Guard**: A file-channel lock on `~/.local/share/tabbyrush/accounts/tabbyrush.lock` prevents multiple running processes from racing token refresh or corrupting local SQLite databases.
3. **Fail-Closed Session Lifecycle**: Account logout synchronously wipes the local database, outbox, and vault tokens before removing fail-closed wipe markers.
4. **Foreground Sync Scheduling**: Sync intervals run in the application scope and quiesce prior to any account leave or database teardown.
5. **No Windows Leakage**: Packaging and dependency resolution strictly isolate Linux artifacts. Zero Windows DLLs or DPAPI classes are included in the Linux distribution.

---

## 2. Prerequisites & Host Setup

### System Packages
```bash
# RPM build tool (required for :desktopApp:packageRpm)
sudo dnf install -y rpm-build

# Secret Service client library and daemon (GNOME Keyring or KSecretService)
sudo dnf install -y libsecret gnome-keyring

# Java 21 LTS (for building from source)
sudo dnf install -y java-21-openjdk-devel
```

### Headless / Remote Display (Xpra)
When running on remote or headless servers, launch an Xpra session on display `:100`:
```bash
bash scripts/start-xpra.sh
export DISPLAY=:100
```
Ensure a D-Bus session bus is active:
```bash
eval $(dbus-launch --sh-syntax)
export DBUS_SESSION_BUS_ADDRESS
```

---

## 3. Building TabbyRush Desktop

### Build Commands
Execute using JDK 21 LTS:
```bash
export JAVA_HOME="${JAVA_HOME:-/path/to/jdk-21}"

# 1. Verify locked dependency resolution
./gradlew -PdesktopTarget=linux :desktopApp:dependencies

# 2. Compile and run unit tests
./gradlew -PdesktopTarget=linux :desktopApp:test

# 3. Build standalone unpacked application image
./gradlew -PdesktopTarget=linux :desktopApp:createDistributable

# 4. Assemble native RPM package (requires rpm-build)
./gradlew -PdesktopTarget=linux :desktopApp:packageRpm
```

> **Security Note**: Dependency verification (`gradle/verification-metadata.xml`) must remain active for production artifacts. Do not use `--dependency-verification=off` or `--dependency-verification=lenient` when generating distributable or release packages.
---

## 4. Running the Application

### Development / Debug Mode (Local Hub)
Debug launcher permits connecting to local development Hub instances (`http://127.0.0.1:3000`):
```bash
./gradlew -PdesktopTarget=linux :desktopApp:runDebug
```

### Production Release Mode (Bundled Distributable)
Run the self-contained distributable without requiring a system JDK:
```bash
./desktopApp/build/compose/binaries/main/app/TabbyRush/bin/TabbyRush
```

### Installed RPM Package
After installing the RPM:
```bash
# Install package
sudo dnf install -y desktopApp/build/compose/binaries/main/rpm/tabbyrush-1.0.0.rpm

# Launch application
tabbyrush
```

---

## 5. Verification & Diagnostics

Run the package smoke runner to audit RPM metadata, permissions, and dependencies:
```bash
# Host environment diagnostics
bash scripts/linux-package-smoke.sh --diagnose

# Unpacked distributable audit
bash scripts/linux-package-smoke.sh --dist desktopApp/build/compose/binaries/main/app/TabbyRush

# Built RPM package audit
bash scripts/linux-package-smoke.sh --rpm desktopApp/build/compose/binaries/main/rpm/tabbyrush-1.0.0.rpm
```

---

## 6. Uninstalling
To cleanly uninstall the RPM package and clean up user data:
```bash
# 1. Ensure all TabbyRush processes have cleanly exited to avoid inode lock races
pkill -f 'dev.tabbyrush.desktop.MainKt' || true

# 2. Remove RPM package
sudo dnf remove -y tabbyrush

# 3. Query existing Secret Service credentials securely without printing token secrets
secret-tool search --all service tabbyrush

# 4. Clear credentials from Secret Service daemon
secret-tool clear service tabbyrush account <account_key>

# 5. Remove local account data and database files after application quiescence
rm -rf ~/.local/share/tabbyrush
```

---

## 7. Server Admin Announcements & Linux Desktop Notifications

### 7.1 Running-Process SSE Alerts
Linux desktop clients receive live administrator announcements through a persistent authenticated Server-Sent Events (SSE) connection:
- **Process Scope**: The SSE connection is active while TabbyRush is running (foreground window or minimized to system tray).
- **Desktop Notification Bubble**: When an announcement is accepted, the server emits a hint. The desktop controller fetches announcement details and posts a native notification via D-Bus service `org.freedesktop.Notifications`.
- **Single Alert Policy**: The client posts at most one native notification per announcement per active connection. Historical announcements fetched during initial baseline hydration are presented silently in the inbox.

### 7.2 D-Bus Actions & Window Restoration
- **Actions Capability Detection**: On connection, TabbyRush inspects the notification daemon capabilities (`GetCapabilities`).
- **ActionInvoked Routing**: If `actions` capability is supported (e.g. GNOME Shell, Mako, Dunst), TabbyRush passes `["default", "Open inbox"]`. Clicking the alert invokes `ActionInvoked`, brings the TabbyRush window to focus (requesting Wayland/X11 focus restoration), and navigates directly to the announcement detail.
- **Minimal Daemons Without Actions**: On minimal notification daemons lacking actions support, the alert is displayed as an informative banner, and the user accesses the announcement directly through the in-app inbox screen.

### 7.3 Tray Lifecycle & Full-Quit Limits
- **System Tray Support**: When a system tray is present, closing the window hides it while keeping background sync and announcement SSE alive.
- **No-Tray Recovery**: If the system tray is unavailable or fails, closing the companion restores the main window rather than stranding the user.
- **Full-Quit Safety**: Explicitly choosing Quit (`quitSuspend()`) closes the SSE stream, unregisters D-Bus signal listeners, cancels posted remote notification IDs, and exits the process. TabbyRush installs no background systemd user services or autostart entries.
- **Silent Catch-up on Reopen**: Relaunching the application establishes a fresh baseline and fetches all active, unexpired announcements from the server without triggering duplicate historical notification bubbles.
