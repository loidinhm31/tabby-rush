# Doggo Deployment & Operations Guide

**Document Version**: 1.5.0
**Phase**: Sticky Task Surfaces Phase 07 delivered / documentation & automated verification evidenced; native Android/Windows acceptance conditionally held at Gate G2; Linux acceptance pending Phase 08; production cutover remains blocked by G3/G6/G7
**Related Documents**: [Strict-v2 Cutover Runbook](./strict-v2-cutover-runbook.md) · [Security & Recovery](./security-and-recovery.md) · [Release Acceptance](./release-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [System Architecture](./system-architecture.md) · [Linux Desktop Guide](./linux-desktop-guide.md) · [Linux Acceptance](./linux-acceptance.md) · [Windows Desktop Guide](./windows-desktop-guide.md) · [Windows Acceptance](./windows-acceptance.md)

---

## 1. Overview & Operational Responsibilities

This guide details the end-to-end operational procedures required to deploy and maintain Doggo with isolated strict-v1 synchronization against Glean Hub.

In strict adherence to architectural boundaries:
- **Client Distribution**: The Doggo Android client ships with fixed, compile-time approved Hub endpoints; no administrative or database credentials are ever distributed to end-user devices.
- **Backend Infrastructure**: Glean Hub (implemented in Rust with the `glean-hub-sync` extension) and its underlying MongoDB persistence remain strictly under operator custody.
- **Sync Protocol Isolation**: The strict-v1 protocol runs in dedicated namespaces and route trees, completely isolated from legacy Glean clients.
- **Desktop Distribution**: Linux uses the self-contained image or RPM and Freedesktop Secret Service; Windows uses a self-contained application image or per-user MSI and current-user DPAPI. Each target uses its own dependency lock and OS credential provider.

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

## 8. Windows Desktop & MSI Deployment

### 8.1 Build Host Prerequisites
Build and package on a Windows x64 host with JDK 21 LTS (`java`, `javac`, and `jpackage`) and PowerShell. The `:desktopApp:packageMsi` task also requires WiX tooling; the recorded build used WiX 3.11 provisioned by the Compose Desktop plugin. Run diagnostics before resolving dependencies:

```powershell
.\scripts\windows-package-smoke.ps1 -Diagnose
```

The Windows build selects `desktopTarget=windows`; explicit targets that do not match the host are rejected. Keep Gradle dependency verification enabled for package builds. Do not use `--dependency-verification=off` to produce a distributable or MSI.

### 8.2 Build and Test
Run the target-specific build, Windows desktop tests, and shared JVM regression suite:

```powershell
.\gradlew.bat -PdesktopTarget=windows :desktopApp:dependencies
.\gradlew.bat -PdesktopTarget=windows :desktopApp:compileKotlin :desktopApp:test
.\gradlew.bat :shared:jvmTest
.\gradlew.bat -PdesktopTarget=windows :desktopApp:createDistributable
.\gradlew.bat -PdesktopTarget=windows :desktopApp:packageMsi
```

The Windows lockfile is `desktopApp\gradle-windows.lockfile`; ordinary builds consume the target-specific lock without `--write-locks`. The self-contained application image is written to `desktopApp\build\compose\binaries\main\app\Doggo\`. The MSI output is `desktopApp\build\compose\binaries\main\msi\Doggo-1.0.0.msi`. `createDistributable` includes the runtime and `Doggo.exe`; it does not require WiX.

### 8.3 Audit the Bundle and MSI
Inspect the distributable and MSI using the Windows-specific PowerShell harness:

```powershell
.\scripts\windows-package-smoke.ps1 -DistDir desktopApp\build\compose\binaries\main\app\Doggo
.\scripts\windows-package-smoke.ps1 -MsiPath desktopApp\build\compose\binaries\main\msi\Doggo-1.0.0.msi
```

The bundle audit checks the launcher/runtime, Windows DPAPI class and SPI registration, exactly one registered vault provider, and absence of Linux D-Bus/Secret Service classes. The MSI audit calculates SHA-256, reports Authenticode status, and reads Windows Installer metadata through COM. See [Windows Test Summary](./evidence/windows-test-summary.md) for the recorded 100.68 MB unsigned development MSI and its exact hash; rebuilds may have different hashes.

### 8.4 Installation and Runtime Acceptance
The evidence records successful MSI generation and package metadata inspection, **not** a completed interactive installation. Only install a trusted, approved artifact; the recorded development MSI is unsigned and is not suitable for public production distribution. Production delivery requires an approved Authenticode signature and verification.

For an authorized operator acceptance run:

```powershell
msiexec.exe /i "desktopApp\build\compose\binaries\main\msi\Doggo-1.0.0.msi"
```

After installation, verify the per-user Start Menu entry launches Doggo without a developer JDK on `PATH`, then exercise login, offline restart, task edits, and synchronization. Verify credentials remain usable only in the intended Windows user context. Treat sync quiescence and clearing local account data and vault credentials on account leave as acceptance requirements to test, not as established Windows behavior. Use the [Windows Acceptance](./windows-acceptance.md) matrix for the complete runtime and accessibility checklist. The seven Windows PNGs are design mockups, not installed-app captures; Windows Narrator, scaling, and live UI states still need operator verification.

To uninstall, close Doggo and use Windows **Settings → Apps → Installed apps** or the ProductCode inspected from the MSI. Uninstalling the package is not a promise that per-user account data is erased; use Doggo's guarded account-leave flow before removal when account data should be cleared. Verify same-version reinstall separately; it does not establish future upgrade behavior.

Phase 10 implementation is recorded at **100% (2026-10-14)**; technical acceptance is blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not guaranteed by the current source, and clean-user runtime/sync plus uninstall/reinstall evidence remain open. Upstream Phase 09 installed-RPM acceptance is also open. Do not report package generation or metadata inspection as installed-runtime acceptance.

---

## 9. Strict-v2 Protocol Migration & Cutover Operations

### 9.1 Architecture & Deployment Ordering
Upgrading Doggo and Glean Hub to `strict-v2` (task deadlines, strict timezones, and native reminders) requires strict versioned coordination. Deploying schema changes or database modifications in isolation is strictly prohibited.

The authorized deployment ordering is:
1. **All-Node Backend Deployment**: Deploy the fence-aware Glean Hub backend binary across all instances. Ingress continues serving `strict-v1`.
2. **Client Publication**: Publish updated Doggo clients (Android APK, Linux package, Windows MSI) containing Room 1→2 migration and `ContractTransitionCoordinator`. UI guards keep deadline submission disabled with an upgrade prompt while the backend remains on v1.
3. **Non-Mutating Preflight**: Execute dry-run preflight via `node scripts/hub-strict-upgrade.mjs --app-id doggo --dry-run`.
4. **Controlled In-Place Upgrade**: Execute the upgrade via CLI or Web Admin UI with a durable migration UUID. The backend fences new traffic, drains admitted request permits, transforms documents in batches of 500, validates all documents, publishes canonical `strict-v2` in `_schemas` via CAS, and releases the fence.
5. **Mandatory Post-Cutover Read-Back**: Verify `syncMode === 'strict-v2'`, canonical fingerprint `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`, all 16 columns active, and unchanged active epoch UUID.
6. **Client Replay & Transition**: Connected clients transition to `ready-v2`, reconcile legacy receipts, and unlock full deadline and reminder functionality.

For the complete, step-by-step incident response and recovery procedures, consult the authoritative [Strict-v2 Cutover Runbook](./strict-v2-cutover-runbook.md).

### 9.2 Tooling Rehearsal & Testing
Operators can rehearse CLI upgrade flows and verify parameter validation, token redaction, same-ID retry, and read-back assertions using the dedicated rehearsal runner:
```bash
# In-process mock tooling self-test
node scripts/strict-v2-cutover-rehearsal.mjs --self-test

# Disposable staging rehearsal (requires explicit safety flag and token via env)
export GLEAN_ADMIN_TOKEN="<operator_admin_token>"
node scripts/strict-v2-cutover-rehearsal.mjs \
  --staging-origin "https://staging-hub.example.com" \
  --app-id doggo \
  --confirm-disposable-staging
```
*Note: Tooling self-tests verify client-side CLI orchestration; physical Gate G3 multi-instance cluster migration must be verified on a live MongoDB replica set before production cutover.*

---

## 10. Sticky Task Surface Capabilities & Operating Limits (Phase 07)

Phase 07 records implementation, automated tests, and build evidence. Native Android launcher/shade and Windows sticky-window/tray/multi-monitor acceptance remain conditionally held at Gate G2; the four sticky-surface PNGs are design baselines, not runtime captures. Linux acceptance requires a real session in Phase 08.

### 10.1 Android Surface Capabilities
- **Glance AppWidget**:
  - Baseline: Supported on Android 8.0+ (API 26) through Android 15 (API 35).
  - Launcher Host: Requires an Android home screen launcher supporting Jetpack Glance / AppWidgetManager.
  - No Auto-Start / FGS: Does not register background auto-start broadcast receivers, exact alarm permissions, or foreground services. All updates are triggered reactively by database observers or WorkManager boundary wake.
- **Quiet Deadline Notification**:
  - Channel `doggo_task_surfaces`: Low-importance, no-sound, no-vibration alert for the single next task deadline.
  - Opt-In Policy: Disabled by default; requires explicit user opt-in in task surface settings.
  - Runtime Permissions: On Android 13+ (API 33+), requests `POST_NOTIFICATIONS` only upon explicit user enablement in settings. Rejection shows informative guidance without repeated nagging.
  - Swipe Dismissal: On Android 14+ (API 34+), user swipe dismissal records a durable suppression key preventing re-posting of the same task and deadline until selection or deadline changes.

### 10.2 Desktop Sticky Companion & Tray Capabilities
- **Window Chrome & Pinning**:
  - The sticky companion requests `alwaysOnTop = true` above ordinary windows. Real stacking behavior has not been accepted in an interactive Windows session and remains held at Gate G2.
  - Geometry restoration is covered by unit tests for work areas, taskbar insets, DPI transforms, and negative virtual origins; actual multi-monitor/DPI restoration remains pending native acceptance.
- **Tray Lifecycle & Fallback**:
  - Conditional SystemTray integration: exposed only when the host environment reports usable tray support.
  - Close-to-Hide: Window close hides window while background synchronization continues.
- **Safe Fallback**: If the tray is unavailable or fails to initialize, the fallback logic restores a visible main window rather than allowing the process to become invisible. Automated coverage is recorded; native tray capability/init-failure behavior remains unobserved at Gate G2.
- **Zero Auto-Start Registration**:
  - The desktop application does not register Windows Run registry keys, startup folder shortcuts, or Linux systemd autostart services. Launch is strictly user-invoked.
