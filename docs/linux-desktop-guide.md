# TabbyRush Linux Desktop Guide & Runbook

**Document Version**: 1.0.0
**Target Operating System**: Linux (Fedora 40+, RHEL 9+, openSUSE, Ubuntu 22.04+)
**Architecture**: x86_64
**Distribution Format**: RPM (`.rpm`) and self-contained runtime directory

---

## 1. Overview

The TabbyRush Linux desktop application is built with Compose Multiplatform 1.7.3 and targets the Java 21 LTS runtime. It brings the full offline-first personal task management experience and strict-v1 Glean Hub synchronization to Linux workstations.

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
