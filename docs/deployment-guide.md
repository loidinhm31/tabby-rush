# TabbyRush Deployment & Operations Guide

**Document Version**: 1.18.0
**Last Updated**: 2026-10-20
**Release Baseline**: 1.0.17 (versionCode 18)
**Status**: Standard sync is current. Offline Gradle/package suites, disposable-local Hub smoke, and a standalone Windows runtime smoke are reported passed; interactive native UI/device acceptance is not passed, and live production migration/release remain unauthorized.
**Related Documents**: [Standard Sync Design](./standard-sync-cutover-design.md) · [Security & Recovery](./security-and-recovery.md) · [Release Acceptance](./release-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [System Architecture](./system-architecture.md) · [Linux Desktop Guide](./linux-desktop-guide.md) · [Linux Acceptance](./linux-acceptance.md) · [Windows Desktop Guide](./windows-desktop-guide.md) · [Windows Acceptance](./windows-acceptance.md)

---

## 1. Overview & Operational Responsibilities

This guide covers deployment of the native TabbyRush Android/Desktop clients using Glean Hub ordinary registration and standard synchronization.

In keeping with architectural boundaries:
- **Client Distribution**: Approved Hub origins and the app API key are client configuration; administrative and database credentials are never distributed to end-user devices.
- **Backend Infrastructure**: Glean Hub and MongoDB remain under operator custody.
- **Sync Protocol**: The current binary uses standard push/pull/delta routes under `/api/v1/sync/{app_id}/`; it does not use strict-v1/v2/v3 routes.
- **Desktop Distribution**: Linux uses the supported package/image and Secret Service; Windows uses the supported package/image and current-user DPAPI. Each target uses its own dependency lock and OS credential provider.

Offline Gradle/package suites and disposable-local API/browser smoke are reported passed. A standalone Windows runtime smoke also passed through a strict Gradle JavaExec entry point against disposable loopback services, exercising DPAPI, real HTTP auth, Room offline restart, two independent stores using the actual coordinator, schemaVersion 2, unknown JSON/customNotes preservation, parent/child sync, and group deletion. It is not interactive UI acceptance or connected-Android proof. UI provider approval timed out twice; connected-device acceptance was not run. Do not equate package creation or harness/backend smoke with native release acceptance.

---

## 2. Glean Hub Ordinary Registration & Schema

### 2.1 Hub and Database Prerequisites

Deploy the approved Glean Hub release behind the organization's TLS-terminating reverse proxy and use its supported MongoDB topology/backup policy. The current TabbyRush client expects HTTPS outside configured loopback development environments and does not follow redirects for sync requests. Keep database credentials, admin tokens, and backups within operator custody.

### 2.2 Register a New Ordinary App

`scripts/hub-schema-provision.mjs` provisions a new ordinary registration only; it does not update, convert, or migrate an existing app. The schema file must contain `appId` and `schema`, and `--confirm-app` must exactly match. The script requires explicit `--origin`, `--schema`, `--out`, and `GLEAN_ADMIN_TOKEN`, validates ordinary schema metadata, writes credentials to a new file without printing them, and verifies registry read-back.

```bash
export GLEAN_ADMIN_TOKEN="<operator_admin_jwt>"
node scripts/hub-schema-provision.mjs \
  --origin "https://hub.example.com" \
  --schema ./integration/glean-hub/tabbyrush-schema.json \
  --out /secure/credentials/tabbyrush-credentials.json \
  --confirm-app tabby-rush
```

Never pass administrative tokens or MongoDB credentials as command-line literals. On Windows, `--out` MUST be inside an already-existing private, non-symlink directory whose protected DACL and owner restrict access to the current user, `SYSTEM`, and `Administrators`. Create and secure this directory through the approved host process before running the script. The script refuses an unsafe/missing directory and does not change its permissions. It reserves the new output file, sets/verifies its file DACL, and only then sends the registration POST.

### 2.3 Schema Changes

Admin schema updates use `expectedSchemaVersion`. Treat the returned `schemaVersion` as Hub metadata concurrency only: an effective change increments it; a no-op does not; a stale changed update conflicts rather than overwriting current data. Unknown JSON keys are permitted by standard sync. Adding a Hub schema field does not generate native UI, Kotlin fields, or Room migrations; any new required client behavior needs an explicitly compatible native release.

The disposable-local smoke used nullable `customNotes`: version 1→2, no-op remained 2, and a stale changed edit returned 409 while preserving the draft and rendering latest schema read-only. This is not production authorization or a live cutover test.
---

## 3. Android Release Build & Signing

### 3.1 Keystore Setup (Operator Custody)
The release signing key must be managed securely outside the repository. To generate a release keypair:

```bash
export TABBYRUSH_KEYSTORE="/secure/keystores/tabbyrush-release.jks"
export TABBYRUSH_KEY_ALIAS="tabbyrush-release"

keytool -genkeypair \
  -keystore "$TABBYRUSH_KEYSTORE" \
  -alias "$TABBYRUSH_KEY_ALIAS" \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000 \
  -storetype JKS
```

### 3.2 Assembling the Release APK
Provide signing credentials via environment variables and run the release build:

```bash
export TABBYRUSH_KEYSTORE="/secure/keystores/tabbyrush-release.jks"
export TABBYRUSH_KEY_ALIAS="tabbyrush-release"
export TABBYRUSH_STORE_PASSWORD="<store_password>"
export TABBYRUSH_KEY_PASSWORD="<key_password>"

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
export TABBYRUSH_API_KEY="<provisioned_api_key>"

# Live Hub execution
node scripts/hub-live-smoke.mjs \
  --origin "$APPROVED_HUB_ORIGIN" \
  --app-id tabbyrush

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
`desktopApp/build/compose/binaries/main/app/TabbyRush/bin/TabbyRush`

The resulting native RPM package is placed at:
`desktopApp/build/compose/binaries/main/rpm/tabbyrush-1.0.0.rpm`

### 7.3 Packaging Smoke & Diagnostic Verification
Before deploying or distributing, run the Linux package smoke runner:

```bash
# 1. Run environment diagnostics (verifies java, jpackage, rpm, rpmbuild, secret-tool, DBUS, DISPLAY)
bash scripts/linux-package-smoke.sh --diagnose

# 2. Verify unpacked standalone distributable image
bash scripts/linux-package-smoke.sh --dist desktopApp/build/compose/binaries/main/app/TabbyRush

# 3. Audit built RPM metadata, file listing, permissions, and dependencies
bash scripts/linux-package-smoke.sh --rpm desktopApp/build/compose/binaries/main/rpm/tabbyrush-1.0.0.rpm
```

### 7.4 Installation & Verification
Install the built RPM package onto the Fedora/RHEL workstation:

```bash
sudo dnf install -y desktopApp/build/compose/binaries/main/rpm/tabbyrush-1.0.0.rpm
```

**Desktop Post-Installation Checklist:**
- [ ] Launch application; verify `SingleInstanceGuard` acquires exclusive lock on `~/.local/share/tabbyrush/accounts/tabbyrush.lock`.
- [ ] Attempt concurrent second launch; verify clean exit without database or vault access.
- [ ] Create account or login; verify credentials saved in Secret Service via `secret-tool search --all service tabbyrush account <account_key>` (lists item paths without printing token payload).
- [ ] Verify tasks persist in `~/.local/share/tabbyrush/accounts/<account_key>/tabbyrush.db`.
- [ ] Verify foreground periodic sync operates every 15 minutes via `DesktopSyncScheduler`.
- [ ] Execute account logout; verify fail-closed wipe of database files, wipe markers, and Secret Service credentials.

### 7.5 Clean Uninstallation
To cleanly remove the package and local user data:
```bash
# 1. Ensure all TabbyRush desktop processes have cleanly exited to prevent file lock and inode recycling race conditions
pkill -f 'dev.tabbyrush.desktop.MainKt' || true

# 2. Remove RPM package
sudo dnf remove -y tabbyrush

# 3. Query existing Secret Service credentials securely without displaying secret tokens
secret-tool search --all service tabbyrush

# 4. Clear credentials from Secret Service daemon
secret-tool clear service tabbyrush account <account_key>

# 5. Remove application data and local SQLite databases after process termination
rm -rf ~/.local/share/tabbyrush
```

## 8. Windows Desktop & MSI Deployment

### 8.1 Build Host Prerequisites
Build and package on a Windows x64 host with JDK 21 LTS (`java`, `javac`, and `jpackage`), CMake/MSVC for C++20 WinRT, and PowerShell. Note that bare `:desktopApp:packageMsi` is guarded in `desktopApp/build.gradle.kts` (requiring `-PallowBarePackageMsi` if run directly). CI and official release packaging use the dedicated remote MSI packaging script `scripts/build-windows-remote-msi.ps1` (or `:desktopApp:packageRemoteMsi`), which compiles the native C++/WinRT toast DLL (`tabbyrush-toast.dll`), stages resources, normalizes configs to BOM-free UTF-8, and packages a per-user MSI with WiX.

Run diagnostics before resolving dependencies:

```powershell
.\scripts\windows-package-smoke.ps1 -Diagnose
```

The Windows build selects `desktopTarget=windows`; explicit targets that do not match the host are rejected. Note: CI currently runs with `--dependency-verification=off` due to the open 149-hash buildscript verification baseline hold.

### 8.2 Build and Packaging via Remote MSI Script
Run the packaging script to compile the native toast bridge, assemble distributables, and build the MSI:

```powershell
# Execute complete Windows desktop build and per-user MSI packaging
powershell -ExecutionPolicy Bypass -File .\scripts\build-windows-remote-msi.ps1 -Verification off
```

The Windows lockfile is `desktopApp\gradle-windows.lockfile`; ordinary builds consume the target-specific lock without `--write-locks`. The self-contained application image is written to `desktopApp\build\compose\binaries\main\app\TabbyRush\`. The MSI output is written to `desktopApp\build\compose\binaries\main\msi\TabbyRush-<version>.msi` (or `desktopApp\build\windows-installer\TabbyRush-<version>.msi`).

### 8.3 Audit the Bundle and MSI
Inspect the distributable and MSI using the Windows-specific PowerShell harness:

```powershell
.\scripts\windows-package-smoke.ps1 -DistDir desktopApp\build\compose\binaries\main\app\TabbyRush
.\scripts\windows-package-smoke.ps1 -MsiPath desktopApp\build\compose\binaries\main\msi\TabbyRush-1.0.17.msi
```

The bundle audit checks the launcher/runtime, Windows DPAPI class and SPI registration, exactly one registered vault provider, and absence of Linux D-Bus/Secret Service classes. The MSI audit calculates SHA-256, reports Authenticode status, and reads Windows Installer metadata through COM. See [Windows Test Summary](./evidence/windows-test-summary.md) for the recorded 100.68 MB unsigned development MSI and its exact hash; rebuilds may have different hashes.

### 8.4 Installation and Runtime Acceptance
The evidence records successful MSI generation and package metadata inspection, **not** a completed interactive installation. Only install a trusted, approved artifact; the recorded development MSI is unsigned and is not suitable for public production distribution. Production delivery requires an approved Authenticode signature and verification.

For an authorized operator acceptance run:

```powershell
msiexec.exe /i "desktopApp\build\compose\binaries\main\msi\TabbyRush-1.0.0.msi"
```

After installation, verify the per-user Start Menu entry launches TabbyRush without a developer JDK on `PATH`, then exercise login, offline restart, task edits, and synchronization. Verify credentials remain usable only in the intended Windows user context. Treat sync quiescence and clearing local account data and vault credentials on account leave as acceptance requirements to test, not as established Windows behavior. Use the [Windows Acceptance](./windows-acceptance.md) matrix for the complete runtime and accessibility checklist. The seven Windows PNGs are design mockups, not installed-app captures; Windows Narrator, scaling, and live UI states still need operator verification.

To uninstall, close TabbyRush and use Windows **Settings → Apps → Installed apps** or the ProductCode inspected from the MSI. Uninstalling the package is not a promise that per-user account data is erased; use TabbyRush's guarded account-leave flow before removal when account data should be cleared. Verify same-version reinstall separately; it does not establish future upgrade behavior.

Phase 10 implementation is recorded at **100% (2026-10-14)**; technical acceptance is blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not guaranteed by the current source, and clean-user runtime/sync plus uninstall/reinstall evidence remain open. Upstream Phase 09 installed-RPM acceptance is also open. Do not report package generation or metadata inspection as installed-runtime acceptance.

---

## 9. Standard Sync Conversion & Cutover Safety

The current TabbyRush binary uses ordinary Hub registration and standard sync. Strict-v1/v2/v3 migration commands and runbooks are historical and do not apply to this client. The client does not perform a live server conversion.

The standard-sync conversion operator is source-built separately from TabbyRush. Parent-reported disposable MongoDB execution passed 7/7 operator checks, including concurrency, deadline, and recovery. That evidence does not authorize any live target. No production migration has been authorized.

### 9.1 Mutating Operator Preconditions

Any real conversion requires separate explicit live-cutover approval naming the Hub origin, exact database and app ID, maintenance window, verified backup/restore scope, and isolation plan. Before mutation, quiesce or isolate every Hub instance and background writer/job as required by the approved plan. A maintenance window must be renewed and verified before **each collection rename and each registry compare-and-swap**. Publication is not a multi-step transaction; no automatic service restart, isolation release, or post-publication rollback occurs.

### 9.2 Exclusive Claim Recovery After Failure

The operator's exclusive execution claim is durable and has no automatic expiry or takeover. After **any failed mutating run**, do not start another execution or clear the claim by hand. Recovery is allowed only with explicit `maintenance.executionRecovery` for the exact `expectedExecutionId`, containing:

- `executorStopped: true` and `executorStopEvidence`;
- `mongoOperationsDrained: true` and `mongoDrainEvidence`;
- `authorizedActions` containing `"recoverExecution"`.

The evidence must establish both that the prior executor is stopped and that all MongoDB operations it initiated are drained. If any condition is not established, stop and keep the target isolated. An operator recovery authorization is not approval for a production migration.

### 9.3 Rollback Boundary

Before publication, recovery or restoration still requires the separately approved procedure, verified backup, and confirmed isolation. Once standard data/publication is active, do not restore an older snapshot over new writes; any reverse migration needs a separate approved, data-aware plan. The conversion tool does not restart services or release external isolation on completion.

For current client behavior, see [Standard Sync & Schema Version](./standard-sync-cutover-design.md) and [Standard Sync Cutover Runbook](./standard-sync-cutover-runbook.md). Former strict runbooks have been retired.

---

## 10. Sticky Task Surface Capabilities & Operating Limits (Phase 07)

Phase 07 records implementation, automated tests, and build evidence. Native Android launcher/shade and Windows sticky-window/tray/multi-monitor acceptance remain conditionally held at Gate G2; the four sticky-surface PNGs are design baselines, not runtime captures. Linux acceptance requires a real session in Phase 08.

### 10.1 Android Surface Capabilities
- **Glance AppWidget**:
  - Baseline: Supported on Android 8.0+ (API 26) through Android 15 (API 35).
  - Launcher Host: Requires an Android home screen launcher supporting Jetpack Glance / AppWidgetManager.
  - No Auto-Start / FGS: Does not register background auto-start broadcast receivers, exact alarm permissions, or foreground services. All updates are triggered reactively by database observers or WorkManager boundary wake.
- **Quiet Deadline Notification**:
  - Channel `tabbyrush_task_surfaces`: Low-importance, no-sound, no-vibration alert for the single next task deadline.
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

---

## 11. Server Admin Announcements Deployment & Operational Runbook

### 11.1 Server Configuration & Environment Variables
Deploying Server Admin Announcements requires configuring backend and transport variables in `glean-hub-server`:

| Variable | Type | Default | Description |
|---|---|---|---|
| `ANNOUNCEMENTS_ENABLED` | boolean | `false` | Master switch gating intake routes. When `false`, intake returns 503 while read/replay routes remain functional. |
| `ANNOUNCEMENT_FCM_ENABLED` | boolean | `false` | Enables FCM HTTP v1 background worker for Android push dispatch. |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | string | - | Absolute path to Google Service Account JSON file with `firebase.messaging` scope. |
| `FCM_PROJECT_ID` | string | - | Google Cloud / Firebase project ID matching Android public client configuration. |
| `ANNOUNCEMENT_RETENTION_DAYS` | integer | `90` | Announcement retention lifetime in days (enforced monotonically). |

### 11.2 Preflight Verification
Before enabling intake or worker dispatch in production or staging, operators must run the preflight verification script:
```bash
node scripts/announcement-preflight.mjs --config /etc/tabbyrush/operator-config.json
```
Preflight checks:
1. **MongoDB Write Concern**: Asserts replica set or standalone node acknowledges `w:1, j:true` writes.
2. **Required Indexes**: Verifies unique indexes on `(appId, senderId, idempotencyKey)`, TTL index on `expiresAt`, multikey index on `recipientIds`, and unique partial token index on `notification_install_bindings`.
3. **Outbound TLS & FCM Scope**: Validates HTTPS connectivity to `https://fcm.googleapis.com` and validates Google OAuth2 bearer token acquisition via ADC or service-account JSON.

### 11.3 Staged Rollout & Pilot Gate Policy
- **Pilot Gate (Android & Linux)**:
  - Approved for internal pilot once G1 (Vectors), G4 (Linux), G7 (Admin Web), and G8 (Security) pass.
  - Operational restriction: Broadcasts must use `selected` audience mode ($\le 500$ users). Broadcast to `all` or `admins` is restricted to disposable staging fixtures during pilot.
  - Disclose to pilot cohort that Windows desktop alerts are held at Gate G5.
- **Full Delivery Gate (Windows Inclusive)**:
  - Windows desktop native notifications and per-user MSI installation (Gate G5) must be verified on a clean Windows x64 host before enabling wide organization rollouts.

### 11.4 Nondestructive Rollback Procedure
If an issue is detected during pilot or full rollout:
1. **Immediate Intake Halt**: Set `ANNOUNCEMENTS_ENABLED=false` on the server and trigger graceful configuration reload. New creation requests receive 503 `announcementsDisabled`.
2. **Worker Quiescence**: Set `ANNOUNCEMENT_FCM_ENABLED=false` to pause new device claims. In-flight leases expire after 60s without data loss.
3. **Preserve Read Availability**: Existing accepted messages remain queryable via `/inbox` and `/unread` through their 90-day lifecycle.
4. **Zero Collection Deletions**: Never drop `admin_announcements`, `admin_announcement_inbox`, or `notification_install_bindings` collections.

---

## 12. GitHub Flow CI/CD Pipeline & Multiplatform Releases

TabbyRush implements an automated GitHub Flow workflow structure:
- **Pull Request CI (`.github/workflows/pr-test.yml`)**: Strictly executes tests only (no packaging overhead), providing rapid feedback on PRs.
- **End-User Release Automation (`.github/workflows/release.yml`)**: Builds installable end-user packages and publishes a GitHub Release when a version tag (`v*`) is pushed.

### 12.1 PR Verification (`pr-test.yml`)
- **Trigger**: `pull_request` against `main`.
- **Concurrency**: `cancel-in-progress: true` automatically cancels stale runs when new commits arrive.
- **Scope (Tests Only)**:
  - `test-shared`: Shared KMP JVM & SQLite tests (`:shared:jvmTest`).
  - `test-android`: Android unit tests (`:androidApp:testDebugUnitTest`).
  - `test-desktop-linux`: Linux Compose Desktop unit tests (`:desktopApp:test`).
  - `test-desktop-windows`: Windows Compose Desktop unit tests (`:desktopApp:test`).

### 12.2 End-User Releases & Version Bumping (`release.yml`)
- **Triggers**:
  - `push` with tag matching `v*` (e.g. `v1.0.0`, `v1.0.1`, `v1.1.0`).
- **Single Source of Truth**: Application version (`appVersion`) and Android version code (`appVersionCode`) are configured centrally in `gradle.properties` (initial version: `1.0.0` / code `1`).
- **Tag & Commit Validation**: The workflow validates semantic versioning format and asserts that the git tag strictly matches the committed `appVersion` in `gradle.properties`.
- **End-User Artifact Matrix**:

| Platform | Built File | Generated Release Asset | Description |
|---|---|---|---|
| **Android** | `:androidApp:assembleRelease` (or `:androidApp:assembleDebug`) | `tabbyrush-<version>.apk` | Signed production Android package (or fallback debug APK staged as `tabbyrush-<version>.apk` when signing secrets are unprovisioned) |
| **Linux Desktop** | `:desktopApp:packageRpm` | `tabbyrush-<version>.rpm` | Native RPM installer (Fedora / RHEL / openSUSE) |
| **Linux Desktop** | `:desktopApp:createDistributable` | `tabbyrush-<version>-linux-x64.tar.gz` | Portable standalone executable tarball |
| **Windows Desktop** | `:desktopApp:packageMsi` | `TabbyRush-<version>.msi` | Native per-user MSI installer |
| **Windows Desktop** | `:desktopApp:createDistributable` | `TabbyRush-<version>-windows-x64.zip` | Portable standalone executable ZIP bundle |

- **Release Publication**: The `publish-release` job aggregates all 5 binaries, computes SHA-256 checksums, and publishes a GitHub Release with attached assets using `softprops/action-gh-release@v2` (`permissions: contents: write`).

### 12.3 Version Bump Utility & Release Procedure
TabbyRush provides an automated JavaScript bump-version runner at `scripts/bump-version.mjs` with task runner bindings in `justfile` and `Makefile`.

**Command Shortcuts:**
```bash
# Preview version bump without modifying files
just bump patch --dry-run
node scripts/bump-version.mjs patch --dry-run

# Bump patch version (e.g. 1.0.17 -> 1.0.18, versionCode 18 -> 19)
just bump patch
# or: node scripts/bump-version.mjs patch

# Bump minor version (e.g. 1.0.17 -> 1.1.0)
just bump minor

# Bump and create git release tag automatically
just bump-tag patch
# or: node scripts/bump-version.mjs patch --git-tag
```

**End-User Release Steps:**
1. Bump version and create tag:
   ```bash
   just bump patch
   git add gradle.properties
   git commit -m "chore(release): bump version to 1.0.18"
   git tag v1.0.18
   ```
2. Push to GitHub:
   ```bash
   git push origin main v1.0.18
   ```
3. The `release.yml` GitHub Actions workflow triggers automatically on the tag push, validates version synchronization, builds the 5 multiplatform packages, and attaches them to a new GitHub Release.

### 12.4 Packaging & Verification Caveats
- **Dependency Verification Notice**: CI builds currently execute with `--dependency-verification=off` to accommodate the 149-hash buildscript verification baseline hold documented in Section 7.2.
- **Windows Packaging**: The MSI installer produced by the workflow is an unsigned development installer; public distribution requires Authenticode signing on an authorized build workstation.
- **Platform Acceptance Status**: Packaging verification in CI confirms installer and binary assembly, not installed-runtime or physical device acceptance (Gates G2/G6 remain held).
- **Android Release Signing Mode & Fallback**: The workflow determines signing custody dynamically (`id: signing_check`). If all release keystore credentials (`TABBYRUSH_KEYSTORE_BASE64`, `TABBYRUSH_KEY_ALIAS`, `TABBYRUSH_STORE_PASSWORD`, `TABBYRUSH_KEY_PASSWORD`, `APPROVED_HUB_ORIGIN`, `TABBYRUSH_API_KEY`) are present, it builds and audits a signed release APK (`androidApp-release.apk`) via `scripts/release-security-check.mjs`. If signing secrets are unprovisioned, it emits a workflow warning and falls back to building an Android debug APK (`androidApp-debug.apk`), staging it as `tabbyrush-<version>.apk` solely for testing release workflow asset delivery. A debug fallback APK does NOT constitute production release acceptance or verified signature custody.
