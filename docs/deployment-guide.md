# Doggo Deployment & Operations Guide

**Document Version**: 1.2.0  
**Phase**: 09 — Linux Desktop & Installed RPM Distribution  
**Status**: Desktop Tooling & Standalone Distributable Operational; Live RPM Build Pending Host rpm-build Tool  
**Related Documents**: [Security & Recovery](./security-and-recovery.md) · [Release Acceptance](./release-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [System Architecture](./system-architecture.md) · [Linux Desktop Guide](./linux-desktop-guide.md) · [Linux Acceptance](./linux-acceptance.md)

---

## 1. Overview & Operational Responsibilities

This guide details the end-to-end operational procedures required to deploy and maintain Doggo with isolated strict-v1 synchronization against Glean Hub.

In strict adherence to architectural boundaries:
- **Client Distribution**: The Doggo Android client ships with fixed, compile-time approved Hub endpoints; no administrative or database credentials are ever distributed to end-user devices.
- **Backend Infrastructure**: Glean Hub (implemented in Rust with the `glean-hub-sync` extension) and its underlying MongoDB persistence remain strictly under operator custody.
- **Sync Protocol Isolation**: The strict-v1 protocol runs in dedicated namespaces and route trees, completely isolated from legacy Glean clients.
- **Desktop Distribution**: The Doggo Linux desktop application ships as a standalone unpacked distributable image or an installable `.rpm` package for Fedora/RHEL. Hardware/OS credential custody relies on the Freedesktop Secret Service via `secret-tool` / D-Bus.

---

## 2. Glean Hub Strict Backend Deployment

### 2.1 Host & Database Prerequisites
1. **Glean Hub Binary**: Built with `glean-hub-sync` strict extension and Axum web framework.
2. **MongoDB Cluster**:
   - MongoDB 6.0+ or 7.0+ (Replica Set recommended for majority write concerns, or standalone writable primary with `w: 1, j: true`).
   - Storage engine: WiredTiger with journaling enabled.
   - Network: Firewalled, TLS-encrypted connections only; never exposed to public internet directly without a reverse proxy.
3. **Approved Reverse Proxy**:
   - Nginx, Caddy, or Cloudflare terminating public TLS.
   - Enforce HTTPS only (HTTP -> HTTPS redirect at proxy edge; application client does not follow redirects).

### 2.2 Provisioning the Strict-v1 Application
Execute the schema provisioning runner against the approved Hub instance:

```bash
# Set authorized operator admin token
export GLEAN_ADMIN_TOKEN="<operator_admin_jwt>"
export APPROVED_HUB_ORIGIN="https://hub.example.com"

# Provision strict app schema and verify admission read-back
node scripts/hub-schema-provision.mjs \
  --origin "$APPROVED_HUB_ORIGIN" \
  --token "$GLEAN_ADMIN_TOKEN" \
  --schema integration/glean-hub/doggo-schema.json \
  --out /secure/credentials/doggo-credentials.json
```

**Verification Invariants:**
- Inspects `GET /api/v1/apps/doggo` read-back.
- Asserts `syncMode === 'strict-v1'`.
- Validates the 4 canonical columns in `todos`: `id` (string), `title` (string), `completed` (boolean), `createdAt` (integer).
- Saves the dedicated `apiKey` into custody with restrictive `0600` file permissions.

---

## 3. Android Release Build & Signing

### 3.1 Keystore Setup (Operator Custody)
The release signing key must be managed securely outside the repository. To generate a release keypair:

```bash
export DOGGO_KEYSTORE="/secure/keystores/doggo-release.jks"
export DOGGO_KEY_ALIAS="doggo-release"

keytool -genkeypair \
  -keystore "$DOGGO_KEYSTORE" \
  -alias "$DOGGO_KEY_ALIAS" \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000 \
  -storetype JKS
```

### 3.2 Assembling the Release APK
Provide signing credentials via environment variables and run the release build:

```bash
export DOGGO_KEYSTORE="/secure/keystores/doggo-release.jks"
export DOGGO_KEY_ALIAS="doggo-release"
export DOGGO_STORE_PASSWORD="<store_password>"
export DOGGO_KEY_PASSWORD="<key_password>"

# Set path to JDK 21 LTS
export JAVA_HOME="/path/to/jdk-21"

./gradlew --no-daemon --no-configuration-cache :androidApp:assembleRelease
```

The resulting signed APK is placed at:
`androidApp/build/outputs/apk/release/androidApp-release.apk`

---

## 4. Release Security & Integrity Verification

Before distribution, execute the fail-closed security auditor:

```bash
export APPROVED_HUB_ORIGIN="https://hub.example.com"

node scripts/release-security-check.mjs \
  --apk androidApp/build/outputs/apk/release/androidApp-release.apk \
  --origin "$APPROVED_HUB_ORIGIN"
```

The audit script verifies:
1. **APK Signature**: Validates v2/v3 signature scheme via `apksigner verify`.
2. **Manifest Invariants**:
   - `android:debuggable="false"`
   - `android:usesCleartextTraffic="false"`
   - `android:allowBackup="false"`
   - Debug network security configurations are stripped.
   - Only `MainActivity` is exported.
3. **Secret Scan**: Confirms no private keys, JWT signing keys, or database passwords leaked into the APK assets or code.

---

## 5. Live Hub Verification Smoke Run

Run the live smoke verification suite to validate end-to-end functionality before opening user registration:

```bash
export APPROVED_HUB_ORIGIN="https://hub.example.com"
export DOGGO_API_KEY="<provisioned_api_key>"

# Live Hub execution
node scripts/hub-live-smoke.mjs \
  --origin "$APPROVED_HUB_ORIGIN" \
  --app-id doggo

# Or execute local fixture test mode for automated regression
node scripts/hub-live-smoke.mjs --fixture-test
```

---

## 6. Device Deployment & Verification

Install the signed release APK onto the target Android device or emulator:

```bash
adb -s "$ANDROID_SERIAL" install -r androidApp/build/outputs/apk/release/androidApp-release.apk
```

**Post-Installation Checklist:**
- [ ] Launch application; verify clean empty state.
- [ ] Create account; verify pending approval or approved state.
- [ ] Create, update, toggle, and delete tasks; verify local Room persistence.
- [ ] Connect network; verify push sync to Hub strict epoch collection.
- [ ] Verify background WorkManager sync operates every 15 minutes.
- [ ] Execute account logout; verify fail-closed wipe of local database, preferences, and Keystore credentials.

---

## 7. Linux Desktop & RPM Deployment

### 7.1 Workstation & Runtime Prerequisites
1. **System Packages**:
   ```bash
   # RPM package build tool (required for :desktopApp:packageRpm)
   sudo dnf install -y rpm-build

   # Secret Service daemon and CLI tool
   sudo dnf install -y libsecret gnome-keyring

   # Java 21 LTS development kit
   sudo dnf install -y java-21-openjdk-devel
   ```
2. **Remote Xpra & D-Bus Environment**:
   When executing in headless environments, launch an Xpra virtual display and initialize the user D-Bus session bus:
   ```bash
   bash scripts/start-xpra.sh
   export DISPLAY=:100
   eval $(dbus-launch --sh-syntax)
   export DBUS_SESSION_BUS_ADDRESS
   ```

### 7.2 Building Linux Desktop Binaries
Execute build commands with JDK 21 LTS:

```bash
export JAVA_HOME="${JAVA_HOME:-/path/to/jdk-21}"

# 1. Verify locked dependencies
./gradlew -PdesktopTarget=linux :desktopApp:dependencies

# 2. Run desktop unit test suite (19/19 passing)
./gradlew -PdesktopTarget=linux :desktopApp:test

# 3. Assemble self-contained unpacked application bundle (no rpm-build required)
./gradlew -PdesktopTarget=linux :desktopApp:createDistributable

# 4. Assemble native RPM package (requires rpm-build)
./gradlew -PdesktopTarget=linux :desktopApp:packageRpm
```

> **Security Notice**: Production builds and distributions must use strict Gradle dependency verification (`gradle/verification-metadata.xml`). Bypassing verification (`--dependency-verification=off` or `--dependency-verification=lenient`) is restricted to local diagnostic troubleshooting on offline dev workstations and must never be used to produce distributable artifacts.

The resulting standalone application bundle is placed at:
`desktopApp/build/compose/binaries/main/app/Doggo/bin/Doggo`

The resulting native RPM package is placed at:
`desktopApp/build/compose/binaries/main/rpm/doggo-1.0.0.rpm`

### 7.3 Packaging Smoke & Diagnostic Verification
Before deploying or distributing, run the Linux package smoke runner:

```bash
# 1. Run environment diagnostics (verifies java, jpackage, rpm, rpmbuild, secret-tool, DBUS, DISPLAY)
bash scripts/linux-package-smoke.sh --diagnose

# 2. Verify unpacked standalone distributable image
bash scripts/linux-package-smoke.sh --dist desktopApp/build/compose/binaries/main/app/Doggo

# 3. Audit built RPM metadata, file listing, permissions, and dependencies
bash scripts/linux-package-smoke.sh --rpm desktopApp/build/compose/binaries/main/rpm/doggo-1.0.0.rpm
```

### 7.4 Installation & Verification
Install the built RPM package onto the Fedora/RHEL workstation:

```bash
sudo dnf install -y desktopApp/build/compose/binaries/main/rpm/doggo-1.0.0.rpm
```

**Desktop Post-Installation Checklist:**
- [ ] Launch application; verify `SingleInstanceGuard` acquires exclusive lock on `~/.local/share/doggo/accounts/doggo.lock`.
- [ ] Attempt concurrent second launch; verify clean exit without database or vault access.
- [ ] Create account or login; verify credentials saved in Secret Service via `secret-tool search --all service doggo account <account_key>` (lists item paths without printing token payload).
- [ ] Verify tasks persist in `~/.local/share/doggo/accounts/<account_key>/doggo.db`.
- [ ] Verify foreground periodic sync operates every 15 minutes via `DesktopSyncScheduler`.
- [ ] Execute account logout; verify fail-closed wipe of database files, wipe markers, and Secret Service credentials.

### 7.5 Clean Uninstallation
To cleanly remove the package and local user data:
```bash
# 1. Ensure all Doggo desktop processes have cleanly exited to prevent file lock and inode recycling race conditions
pkill -f 'dev.doggo.desktop.MainKt' || true

# 2. Remove RPM package
sudo dnf remove -y doggo

# 3. Query existing Secret Service credentials securely without displaying secret tokens
secret-tool search --all service doggo

# 4. Clear credentials from Secret Service daemon
secret-tool clear service doggo account <account_key>

# 5. Remove application data and local SQLite databases after process termination
rm -rf ~/.local/share/doggo
```
