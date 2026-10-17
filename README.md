# TabbyRush

TabbyRush is an Android-first shared Compose Multiplatform application designed for fast, focused personal task management with multi-device cloud synchronization.

The application reuses the **Glean Hub** backend infrastructure with an opt-in, strict per-record synchronization protocol (`strict-v1`), ensuring complete data isolation from legacy Hub clients while delivering native offline capability, durable Room KMP persistence, and an accessible light theme.

---

## Key Features

- **Offline-First Task Management**: Full task lifecycle (create, toggle completion, edit title, filter, delete) operating against local Room SQLite database with BundledSQLiteDriver.
- **Strict-v1 Sync Protocol**: Opt-in strong per-record sync protocol featuring insert-only create, explicit revision Compare-And-Swap (CAS), structured per-operation acknowledgements, stable cursor scans, and permanent empty tombstones.
- **Hardware-Backed Credential Vault**: Token storage backed by platform security primitives (Android KeyStore with AES-256 GCM on mobile; Secret Service on Linux; DPAPI on Windows).
- **Single-Flight Refresh & Token Fencing**: Concurrent network operations coalesce behind a single mutex-protected token refresh coordinator.
- **Account Lifecycle**: Account leave coordinates local database/credential cleanup with sync, reminder, and task-surface cancellation. Android OS-retained widget and notification content is cleared on a best-effort basis; see [Security & Recovery](docs/security-and-recovery.md) for the lifecycle caveats.
- **Accessible Light UI**: Clean, high-contrast light theme conforming to WCAG 2.1 AA contrast requirements and ForumFlash design guidelines.
- **Sticky Task Surfaces**: Android provides a native Glance task widget and an opt-in, quiet deadline notification; Desktop provides a compact Compose Multiplatform sticky window with tray controls when the host session supports a usable tray.

Sticky Task Surfaces Phase 07 is delivered: documentation and automated verification are evidenced, with 6h of 12h credited (118h / 132h overall, 89.4%). The recorded suites report 364 Gradle tests and 12 Node security tests passing; build/test runs used `--dependency-verification=off`. Native Android and Windows hardware acceptance remains conditionally held at Gate G2, and Linux session acceptance remains pending Phase 08. The 149-checksum verification baseline, separate Glance provenance hold, and release holds G3/G6/G7 remain open.

---

## Architecture & Directory Structure

```
.
├── androidApp/          # Native Android application module (AGP 8.7.3, minSdk 26, compileSdk 35)
│   ├── src/main/        # Application container, Glance widget, task-surface notification and receivers
│   └── src/androidTest/ # Instrumented account, navigation, preference and widget tests
├── desktopApp/          # Compose Multiplatform JVM desktop application
│   └── src/             # Shared sticky window/tray integration and target-specific Linux/Windows code
├── shared/              # Shared Kotlin Multiplatform core library
│   ├── src/commonMain/  # Domain, sync, Room, Compose UI and account-token-fenced task-surface contracts
│   └── src/commonTest/  # Unit tests, digest vectors and in-memory SQLite integration tests
├── scripts/             # Host tooling, emulator orchestration, Hub smoke runners, and recovery scripts
│   ├── hub-strict-upgrade.mjs          # Strict-v2 Hub migration CLI runner
│   ├── strict-v2-cutover-rehearsal.mjs # Strict-v2 operator tooling rehearsal and self-test harness
│   ├── hub-live-smoke.mjs              # Live Hub strict-v1 verification harness
│   ├── release-security-check.mjs      # Release APK security and manifest auditor
│   ├── hub-strict-recovery.js          # Maintenance-only fresh-epoch recovery script (strict-v1 only)
│   ├── hub-schema-provision.mjs        # Schema registration and admission runner
│   └── android-smoke.sh                # Device smoke runner with reverse port-forwarding
├── integration/         # Canonical strict-v1/strict-v2 schema specifications and vectors
│   └── glean-hub/        # Active schema, versioned schemas, and strict-v2 vectors
└── docs/                # Architecture specifications, operational runbooks, and test evidence
```

---

## Development Prerequisites

### 1. Run Shared Multiplatform Tests
Execute the unit and SQLite integration suites across the shared core:
```bash
# Use JDK 21 LTS (e.g. export JAVA_HOME=/path/to/jdk-21)
JAVA_HOME="${JAVA_HOME:-/home/loidinh/.local/share/jvm/jdk-21}" ./gradlew :shared:jvmTest --dependency-verification=off
```
- **Headless Display**: Xpra 6.x on display `:100` for remote GUI workflows.

---

## Common Commands

### 1. Run Shared Multiplatform Tests
Execute the unit and SQLite integration suites across the shared core:
```bash
JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off
```

### 2. Verify Hub Compatibility Smoke (Fixture Mode)
Run the self-contained offline test suite for strict-v1 protocol verification:
```bash
node scripts/hub-live-smoke.mjs --fixture-test
```

### 3. Run Release Security Audit (Diagnostic Mode)
Audit Android manifest, backup rules, and secret absence statically:
```bash
node scripts/release-security-check.mjs --diagnose-source
```

### 4. Build Android Release APK (Requires SDK & Keystore)
```bash
export TABBYRUSH_KEYSTORE="/path/to/keystore.jks"
export TABBYRUSH_KEY_ALIAS="tabbyrush-release"
export TABBYRUSH_STORE_PASSWORD="<password>"
export TABBYRUSH_KEY_PASSWORD="<password>"

./gradlew :androidApp:assembleRelease
```

### 5. Run Strict-v2 Cutover Rehearsal & Tooling Self-Test
Run the automated in-process mock self-test verifying CLI upgrade workflows, same-ID retry, and read-back assertions:
```bash
node scripts/strict-v2-cutover-rehearsal.mjs --self-test
```

---

## Documentation Index

- [Project Overview & PDR](docs/project-overview-pdr.md)
- [System Architecture](docs/system-architecture.md)
- [Code Standards](docs/code-standards.md)
- [Codebase Summary](docs/codebase-summary.md)
- [Design Guidelines](docs/design-guidelines.md)
- [Local Storage & Sync](docs/local-storage-and-sync.md)
- [Authentication & Credentials](docs/authentication-and-credentials.md)
- [Security & Recovery](docs/security-and-recovery.md)
- [Android Acceptance](docs/android-acceptance.md)
- [Windows Acceptance](docs/windows-acceptance.md)
- [Windows Desktop Guide](docs/windows-desktop-guide.md)
- [Deployment Guide](docs/deployment-guide.md)
- [Toolchain Versions](docs/toolchain-versions.md)
- [Strict-v2 Cutover Runbook](docs/strict-v2-cutover-runbook.md)
- [Release Acceptance](docs/release-acceptance.md)
- [Project Roadmap](docs/project-roadmap.md)
- [Development Roadmap](docs/development-roadmap.md)
- [Project Changelog](docs/project-changelog.md)
- [Android Sticky-Surface Evidence](docs/evidence/sticky-surfaces-android-summary.md)
- [Windows Sticky-Surface Evidence](docs/evidence/sticky-surfaces-windows-summary.md)
