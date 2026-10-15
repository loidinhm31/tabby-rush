# Doggo

Doggo is an Android-first shared Compose Multiplatform application designed for fast, focused personal task management with multi-device cloud synchronization.

The application reuses the **Glean Hub** backend infrastructure with an opt-in, strict per-record synchronization protocol (`strict-v1`), ensuring complete data isolation from legacy Hub clients while delivering native offline capability, durable Room KMP persistence, and an accessible light theme.

---

## Key Features

- **Offline-First Task Management**: Full task lifecycle (create, toggle completion, edit title, filter, delete) operating against local Room SQLite database with BundledSQLiteDriver.
- **Strict-v1 Sync Protocol**: Opt-in strong per-record sync protocol featuring insert-only create, explicit revision Compare-And-Swap (CAS), structured per-operation acknowledgements, stable cursor scans, and permanent empty tombstones.
- **Hardware-Backed Credential Vault**: Token storage backed by platform security primitives (Android KeyStore with AES-256 GCM on mobile; Secret Service on Linux; DPAPI on Windows).
- **Single-Flight Refresh & Token Fencing**: Concurrent network operations coalesce behind a single mutex-protected token refresh coordinator.
- **Fail-Closed Account Lifecycle**: Account logout synchronously wipes local databases, shared preferences, and vault credentials before clearing fail-closed markers.
- **Accessible Light UI**: Clean, high-contrast light theme conforming to WCAG 2.1 AA contrast requirements and ForumFlash design guidelines.

---

## Architecture & Directory Structure

```
.
├── androidApp/          # Native Android application module (AGP 8.7.3, minSdk 26, compileSdk 35)
│   ├── src/main/        # Application container, MainActivity, WorkManager background workers
│   └── src/androidTest/ # Authored instrumented device and isolation test suites
├── shared/              # Shared Kotlin Multiplatform core library
│   ├── src/commonMain/  # Domain models, strict-v1 protocol contracts, auth session, Room DB, Compose UI
│   └── src/commonTest/  # Unit tests, digest vectors, and in-memory SQLite integration tests
├── scripts/             # Host tooling, emulator orchestration, Hub smoke runners, and recovery scripts
│   ├── hub-strict-upgrade.mjs          # Strict-v2 Hub migration CLI runner
│   ├── strict-v2-cutover-rehearsal.mjs # Strict-v2 operator tooling rehearsal and self-test harness
│   ├── hub-live-smoke.mjs              # Live Hub strict-v1 verification harness
│   ├── release-security-check.mjs      # Release APK security and manifest auditor
│   ├── hub-strict-recovery.js          # Maintenance-only fresh-epoch recovery script (strict-v1 only)
│   ├── hub-schema-provision.mjs        # Schema registration and admission runner
│   └── android-smoke.sh                # Device smoke runner with reverse port-forwarding
├── integration/         # Canonical strict-v1 schema specifications
│   └── glean-hub/doggo-schema.json
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
export DOGGO_KEYSTORE="/path/to/keystore.jks"
export DOGGO_KEY_ALIAS="doggo-release"
export DOGGO_STORE_PASSWORD="<password>"
export DOGGO_KEY_PASSWORD="<password>"

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
- [Deployment Guide](docs/deployment-guide.md)
- [Strict-v2 Cutover Runbook](docs/strict-v2-cutover-runbook.md)
- [Windows Desktop Guide](docs/windows-desktop-guide.md)
- [Windows Acceptance](docs/windows-acceptance.md)
- [Security & Recovery](docs/security-and-recovery.md)
- [Release Acceptance](docs/release-acceptance.md)
- [Android Integration Acceptance](docs/android-acceptance.md)
- [Local Storage & Sync](docs/local-storage-and-sync.md)
- [Project Roadmap](docs/project-roadmap.md)
- [Project Changelog](docs/project-changelog.md)
