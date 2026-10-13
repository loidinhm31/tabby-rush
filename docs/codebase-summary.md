# Codebase Summary

Generated on: 2026-10-14  
Based on Repomix compaction: `repomix-output.xml` (133 files, 211,737 tokens, 893,878 chars)
---

## 1. Repository Overview

The `doggo` repository contains the implementation, build configuration, domain contracts, and architectural specifications for Doggo, an Android-first shared Compose Multiplatform task management application with secure multi-device synchronization against Glean Hub.

Following Phase 01 (Headless Tooling), Phase 02 (Frozen Foundation & Contracts), and Phase 03 (Isolated Strict Server & Legacy Isolation), the repository contains:
1. **Root Build & Version Catalog**: Staged Gradle build system, official SHA-256 pinned wrapper, and dependency catalog.
2. **Shared Kotlin Multiplatform Module (`shared/`)**: Domain models, UI state and action models, identity/authentication contracts, strict sync canonical framing, validation policies, and unit tests.
3. **Host Virtualization & Tooling (`scripts/`)**: Headless Xpra display orchestration, Android SDK/JDK environment configuration, emulator management, and Hub provisioning/smoke runners.
4. **Hub Integration Artifacts (`integration/`)**: Canonical strict-v1 JSON schema specification matching backend admission rules.
5. **Automated Verification Harness (`tests/`)**: Automated shell suites validating scripts, Gradle contracts, Kotlin sources, and cryptographic digest vectors.
6. **Technical Documentation (`docs/`)**: Architecture specifications, API contracts, toolchain evidence, operational runbooks, compatibility acceptance criteria, and coding standards.
7. **Planning & Roadmap (`plans/`)**: Phased execution plans, architectural decisions, and verification reports.
### High-Level Directory Structure

```
.
├── .gitignore                          # Git ignore rules for Gradle, local environment, and build outputs
├── build.gradle.kts                    # Root build script with modern plugin declarations
├── gradle.properties                   # JVM memory, Kotlin daemon, AndroidX, and Compose flags
├── gradlew / gradlew.bat               # Official Gradle wrapper executables
├── settings.gradle.kts                 # Staged module assembly (guards :androidApp and :desktopApp)
├── gradle/
│   ├── libs.versions.toml              # Version catalog (Kotlin 2.1.10, Compose 1.7.3, AGP 8.7.3, Room 2.7.0-alpha12)
│   ├── verification-metadata.xml       # Gradle dependency checksum verification
│   └── wrapper/
│       ├── gradle-wrapper.jar          # Verified wrapper jar (SHA-256: 2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046)
│       └── gradle-wrapper.properties   # Official Gradle 8.11.1 distribution with SHA-256 checksum pinning
├── shared/                             # Shared Kotlin Multiplatform library
│   ├── build.gradle.kts                # KMP library configuration (dev.doggo.shared, minSdk 26, JVM 17)
│   ├── gradle.lockfile                 # Dependency locking definition
│   └── src/
│       ├── commonMain/kotlin/dev/doggo/shared/contract/
│       │   ├── account-identity.kt     # AccountIdentity domain model
│       │   ├── account-key.kt          # AccountKey deterministic SHA-256 key derivation
│       │   ├── account-store.kt        # AccountStore, AccountStoreFactory interfaces
│       │   ├── app-factories.kt        # AppFactories dependency wiring interface
│       │   ├── auth-session.kt         # AuthSession, AuthState, AccessRequestInfo contracts
│       │   ├── credential-vault.kt     # CredentialVault, CredentialBundle (redacted toString)
│       │   ├── endpoint-policy.kt      # EndpointPolicy origin, scheme, and port validation
│       │   ├── hub-config.kt           # HubConfig endpoint configuration (redacted toString)
│       │   ├── safe-errors.kt          # SafeError sealed taxonomy, ApiResult envelope
│       │   ├── strict-sync-contract.kt # StrictSyncContract, StrictSyncDigest canonical framing
│       │   ├── sync-coordinator.kt     # SyncCoordinator, SyncStatus, SyncProgress orchestration
│       │   ├── sync-scheduler.kt       # SyncScheduler scheduling interface
│       │   ├── title-policy.kt         # TitlePolicy length and character validation
│       │   ├── todo-action.kt          # TodoAction sealed UI event taxonomy
│       │   ├── todo-models.kt          # Todo, TodoCounts, TodoFilter core domain models
│       │   └── todo-ui-state.kt        # TodoUiState, EditorState, ConflictState view models
│       └── commonTest/kotlin/dev/doggo/shared/contract/
│           ├── account-key-test.kt     # Account key derivation and validation tests
│           ├── endpoint-policy-test.kt # Endpoint policy HTTPS/loopback/port tests
│           ├── strict-digest-test.kt   # NIST test vectors and field isolation tests
│           └── title-policy-test.kt    # Title length, trimming, and character tests
├── integration/                         # Integration schemas and specifications
│   └── glean-hub/
│       └── doggo-schema.json             # Exact strict-v1 todos schema specification
├── scripts/                            # Host automation and integration scripts
│   ├── android-sdk-env.sh              # SDK/JDK PATH configuration and environment detection
│   ├── hub-compatibility-smoke.mjs     # Smoke tests verifying route mounting and isolation guards
│   ├── hub-schema-provision.mjs        # Node provisioning script with 0600 key custody
│   ├── start-android-emulator.sh       # AVD emulator lifecycle with port and KVM validation
│   └── start-xpra.sh                   # Headless virtual display (:100) runner without TCP listeners
├── tests/                              # Automated test suites
│   ├── test-phase-01-tooling.sh        # Phase 01 tooling and host prerequisites test suite
│   └── test-phase-02-foundation.sh     # Phase 02 foundation, contracts, and digest test suite
├── docs/                               # Developer documentation and operational runbooks
│   ├── api-contract.md                 # REST and strict sync wire protocol contract
│   ├── code-standards.md               # Coding, testing, and security standards
│   ├── codebase-summary.md             # This document (codebase inventory & metrics)
│   ├── development-roadmap.md          # Architectural evolution roadmap
│   ├── foundation-evidence.md          # Phase 02 cryptographic and toolchain evidence
│   ├── headless-android-setup.md       # Operational runbook for headless Xpra & Android
│   ├── host-tooling-evidence.md        # Verified host hardware, virtualization, and tool capabilities
│   ├── hub-compatibility-acceptance.md # Architectural acceptance criteria and invariants
│   ├── hub-schema-integration.md       # Integration guide and registration lifecycle
│   ├── project-overview-pdr.md         # Product development requirements and traceability
│   ├── project-roadmap.md              # Project milestones, phase progress, and changelog
│   ├── system-architecture.md          # System architecture and multiplatform components
│   ├── toolchain-versions.md           # Pinned toolchain compatibility matrix
│   └── evidence/
│       └── hub-compatibility-summary.md # Phase 03 backend and script implementation summary
└── plans/                              # Implementation plans, architectural specs & reports
    ├── 261013-0914-doggo-compose-todos/ # Baseline plan series
    ├── 261013-1540-doggo-glean-hub-reuse/ # Glean Hub authoritative evolution plan series
    └── reports/                         # Phase review and verification reports
```

---

## 2. Core Components & Artifacts

### 2.1 Gradle Build System & Version Catalog

| File | Purpose | Key Details |
|---|---|---|
| `settings.gradle.kts` | Staged assembly & plugin management | Unconditionally includes `:shared`; conditionally guards `:androidApp` and `:desktopApp` based on file existence to prevent broken build references. |
| `build.gradle.kts` | Root build script | Registers `clean` task; declares plugins with `apply false` across the project hierarchy. |
| `gradle.properties` | Build engine settings | Configures 4 GB Gradle JVM heap, Kotlin daemon parallel compilation, AndroidX support, and Compose compiler metrics. |
| `gradle/libs.versions.toml` | Pinned version catalog | Pins Kotlin 2.1.10, Compose Multiplatform 1.7.3, AGP 8.7.3, Coroutines 1.10.1, Serialization 1.8.0, Ktor 3.1.0, Room 2.7.0-alpha12, and SQLite 2.5.0-SNAPSHOT. |
| `gradle/wrapper/gradle-wrapper.properties` | Wrapper distribution | Specifies Gradle 8.11.1 bin distribution with official checksum `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`. |
| `gradle/wrapper/gradle-wrapper.jar` | Wrapper binary launcher | Checksum verified against known Gradle release hash (`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`). |
| `gradle/verification-metadata.xml` | Dependency verification | Checksum configuration file establishing dependency verification rules during resolution. |
| `.gitignore` | Repository hygiene | Excludes `.gradle/`, `build/`, `local.properties`, `.env`, IDE files, and Android keystores. |

### 2.2 Shared Module (`shared/`)

The `:shared` module provides the core domain abstractions and business logic, shared across Android and Desktop frontends.

#### Build Configuration
- `shared/build.gradle.kts`: Applied `com.android.library` plugin (`alias(libs.plugins.android.library)`) alongside `kotlinMultiplatform`, `kotlinxSerialization`, and `androidxRoom`.
  - Namespace: `dev.doggo.shared`
  - Targets: `minSdk = 26`, `compileSdk = 35`, JVM toolchain bytecode target 17.
  - Dependencies: Coroutines (`api`), Serialization, Ktor Client, Room Runtime.
  - Enforces dependency locking (`lockAllConfigurations()`) and Room schema directory configuration.
- `shared/gradle.lockfile`: Dependency locking file present on disk (`empty=`); lockfile resolution and entry population remain pending live Gradle build resolution.

#### Contract Interfaces & Domain Policies (`shared/src/commonMain/kotlin/...`)
| Contract File | Primary Types & Interfaces | Architectural Role |
|---|---|---|
| `todo-models.kt` | `Todo`, `TodoCounts`, `TodoFilter` | Pure domain entities representing tasks, metrics, and filter states. |
| `todo-ui-state.kt` | `TodoUiState`, `EditorState`, `ConflictState`, `LeaveGuardState` | Immutable UI view state and modal interaction models. |
| `todo-action.kt` | `TodoAction` | Sealed hierarchy representing all UI intents and lifecycle events. |
| `safe-errors.kt` | `SafeError`, `ApiResult` | Sanitized error taxonomy and result envelope intended to model user-facing errors without exposing internal exceptions or credentials. |
| `account-identity.kt` | `AccountIdentity` | Entity encapsulating authenticated user identity (id, username, hubUrl). |
| `account-key.kt` | `AccountKey` | Deterministic SHA-256 derived account key supporting offline data isolation. |
| `account-store.kt` | `AccountStore`, `AccountStoreFactory` | Persistence contracts for multi-account session management. |
| `auth-session.kt` | `AuthSession`, `AuthState`, `AccessRequestInfo` | Authentication session lifecycle and access grant state tracking. |
| `credential-vault.kt` | `CredentialVault`, `CredentialBundle`, `VaultStatus` | Secure token storage contract; `CredentialBundle` overrides `toString()` to redact tokens. |
| `hub-config.kt` | `HubConfig` | Backend connection configuration; overrides `toString()` to redact `appApiKey`. |
| `strict-sync-contract.kt` | `StrictSyncContract`, `StrictSyncDigest`, `StrictOperationKind`, `StrictOperationStatus`, `StrictTodoData`, `StrictOperation`, `StrictOperationResult` | Canonical protocol specification matching Glean Hub; implements length-framed big-endian SHA-256 digest computation. |
| `sync-coordinator.kt` | `SyncCoordinator`, `SyncStatus`, `SyncProgress` | High-level sync coordination contract between local storage and backend. |
| `sync-scheduler.kt` | `SyncScheduler` | Sync trigger scheduling and execution contract. |
| `title-policy.kt` | `TitlePolicy` | Pure validation logic: enforces 1–200 character bounds, trims whitespace, rejects ASCII control characters. |
| `endpoint-policy.kt` | `EndpointPolicy`, `BuildMode` | Validates API endpoint origins; enforces HTTPS in release mode; permits loopback HTTP in debug mode; rejects paths/userinfo/queries. |
| `app-factories.kt` | `AppFactories` | Common factory interface decoupling component composition from concrete implementations. |

| Test File | Target | Coverage |
|---|---|---|
| `strict-digest-test.kt` | `StrictSyncDigest` | Declares test coverage for NIST vectors (empty string, "abc", 56-byte boundary), big-endian field framing, and canonical delete empty data (reference vectors pass via Node.js runner; Kotlin test runner execution pending). |
| `account-key-test.kt` | `AccountKey` | Validates deterministic lowercase hex hash derivation, input trimming, and empty/blank input rejection. |
| `title-policy-test.kt` | `TitlePolicy` | Validates valid titles, leading/trailing whitespace trimming, empty/blank rejection, length boundaries, and control character rejection. |
| `endpoint-policy-test.kt` | `EndpointPolicy` | Validates standard HTTPS URLs, trailing slash stripping, default port normalization, debug HTTP loopback origins, release cleartext rejection, and userinfo/path/query forbidden errors. |

### 2.3 Host Tooling & Virtualization (`scripts/`)

| Script | Purpose | Key Implementation Details |
|---|---|---|
| `scripts/start-xpra.sh` | Headless virtual display lifecycle | Manages Xpra on `:100`; enforces `--html=off`, `--mdns=no`, user-owned UNIX domain socket; launches child `xterm` for display authentication. |
| `scripts/android-sdk-env.sh` | Android SDK and JDK environment | Preserves user overrides for `ANDROID_HOME` and `JAVA_HOME`; prepends SDK tools (`cmdline-tools`, `platform-tools`, `emulator`) to `PATH`; detects Studio JBR or JDK 21; silences output in non-interactive shells. |
| `scripts/start-android-emulator.sh` | Android Emulator launch orchestration | Validates decimal even port (`5554-5682`); checks `/dev/kvm` read/write access; checks `emulator` binary on `PATH`; manages headless/GUI flags; passes through safe arguments. |

### 2.4 Glean Hub Integration & Provisioning Artifacts

Phase 03 establishes backend strict synchronization support and integration tooling:

#### Doggo Integration Artifacts (`integration/`, `scripts/`)
| Artifact | Purpose | Key Details |
|---|---|---|
| `integration/glean-hub/doggo-schema.json` | Exact strict schema payload | Strict-v1 schema with immutable 4-column `todos` table definition. |
| `scripts/hub-schema-provision.mjs` | Schema provisioner & key custody | Registers strict app via `POST /api/v1/strict-apps`, validates schema columns, outputs credentials (`mode: 0o600` on file creation; note: echoes key to stdout, pre-existing files require manual chmod). |
| `scripts/hub-compatibility-smoke.mjs` | Integration smoke runner | Exercises 4 mounted isolation gates (bootstrap, legacy push HTTP 426, scope coherence HTTP 400, forged epoch HTTP 409; note: requires valid credentials to avoid unauthenticated 401 false-acceptance). |
#### Sibling Backend Artifacts (`glean-hub-plugin/glean-hub-sync/` in sibling repo)
| Module Group | Source Paths | Role & Behavior |
|---|---|---|
| Models | `src/models/strong_sync.rs`, `app.rs`, `api.rs`, `mod.rs` | Strict wire DTOs, document models, canonical SHA-256 digest computation, tolerant `sync_mode` / `strict_epoch` fields. |
| Services | `src/services/strong_sync_service.rs`, `app_service.rs`, `mod.rs`, `strong_sync_tests.rs` | Atomic CAS single-mutation engine, real `confirmationNonce` replay writes, topology-aware write concerns, fail-closed admission validator. |
| Routes | `src/routes/strong_sync.rs`, `apps.rs`, `mod.rs`, `strong_sync_route_tests.rs` | Strong sync endpoints (`/api/v1/sync/:app_id/strong/{bootstrap,push,pull}`), admin registration `POST /api/v1/strict-apps`, schema read-back. |
| Middleware & Errors | `src/middleware/sync_mode_guard.rs`, `mod.rs`, `src/strong_sync_error.rs` | Legacy route guard rejecting strict apps with HTTP 426 (`protocolUnsupported`), strong scope coherence guard, structured error envelopes. |
| Binaries & Root | `src/bin/strong_sync_admission.rs`, `src/lib.rs` | Standalone read-only operator admission preflight CLI tool; service and route composition root. |
| Documentation | `CLAUDE.md`, `CHANGELOG.md`, `docs/doggo-strict-sync.md` | Operator runbook and backend sync plugin reference documentation. |

### 2.5 Test Automation (`tests/`)

| Suite | Scope | Status |
|---|---|---|
| `tests/test-phase-01-tooling.sh` | Shell syntax (`bash -n`), executable permissions, CLI argument handling, port validation, host prerequisites (`/dev/kvm`, `xpra`, `xterm`, `java`, `node`), and documentation contracts. | 31/31 assertions PASS. |
| `tests/test-phase-02-foundation.sh` | Wrapper checksums, settings guards, version catalog, shared module config, contract source existence, test suites, contract isolation/redaction, and Node.js reference vector runner for policy and digest logic. (Note: Gradle test runner and Kotlin compiler tasks not yet executed; pending downstream build gates). | 57/57 assertions PASS. |

### 2.6 Documentation (`docs/`)

| Document | Purpose |
|---|---|
| [`docs/api-contract.md`](./api-contract.md) | Authoritative wire protocol specification for Glean Hub authentication, admin registration, and strict sync. |
| [`docs/code-standards.md`](./code-standards.md) | Engineering standards covering Bash scripting, Kotlin Multiplatform, Node.js scripts, Rust backend isolation, and credential security. |
| [`docs/codebase-summary.md`](./codebase-summary.md) | Comprehensive codebase inventory based on Repomix compaction. |
| [`docs/development-roadmap.md`](./development-roadmap.md) | Architectural roadmap documenting phased evolution toward Glean Hub reuse. |
| [`docs/foundation-evidence.md`](./foundation-evidence.md) | Verification evidence for Phase 02 toolchains, wrapper checksums, and cryptographic test vectors. |
| [`docs/headless-android-setup.md`](./headless-android-setup.md) | Operations runbook for running Android Studio and emulator over SSH-tunneled Xpra. |
| [`docs/host-tooling-evidence.md`](./host-tooling-evidence.md) | Verified specification of host hardware (AMD-V, KVM, GPU) and installed tooling versions. |
| [`docs/hub-compatibility-acceptance.md`](./hub-compatibility-acceptance.md) | Architectural acceptance criteria, mutation semantics, and verification matrix for Glean Hub strict sync. |
| [`docs/hub-schema-integration.md`](./hub-schema-integration.md) | Integration guide and lifecycle runbook for strict-v1 app registration on Glean Hub. |
| [`docs/evidence/hub-compatibility-summary.md`](./evidence/hub-compatibility-summary.md) | Summary of Phase 03 deliverables, Rust crate unit tests, and live MongoDB deferrals. |
| [`docs/project-overview-pdr.md`](./project-overview-pdr.md) | Product development requirements (PDR), acceptance criteria, and system constraints. |
| [`docs/project-roadmap.md`](./project-roadmap.md) | Living project roadmap tracking phase completion, milestones, and release changelog. |
| [`docs/system-architecture.md`](./system-architecture.md) | System architecture covering headless virtualization, Kotlin Multiplatform, and Glean Hub strict sync subsystem. |
| [`docs/toolchain-versions.md`](./toolchain-versions.md) | Authoritative toolchain version compatibility matrix (Kotlin, Compose, AGP, Gradle, Java). |

---

## 3. Repomix Compaction Metrics

- **Total Files Packed:** 133 files (134 processed in `doggo` repository; 1 test file excluded by security filter)
- **Total Character Count:** 893,878 characters
- **Total Token Count:** 211,737 tokens
- **Security Check:** Secretlint completed across packed files with 0 findings in packed output. Note: Repomix packs the local `doggo` repository (excluding sibling repository files). 1 test file (`endpoint-policy-test.kt`) was excluded due to a mock userinfo URL vector.
- **Top Files by Token Footprint:** `file-ownership.md` (7,367 tokens), `reports/contract-review.md` (5,471 tokens), `docs/codebase-summary.md` (5,207 tokens), `contracts.md` (5,106 tokens), `reports/strong-sync-isolation-feasibility.md` (4,054 tokens).
---

## 4. Current State & Invariants

1. **Deliverable Scope & Status**:
   - **Phase 01 (Headless Tooling)**: Automation scripts, Xpra display isolation, and technical documentation completed and verified via shell suite (31/31 tests PASS). Interactive Studio GUI wizard, SDK license acceptance, and live AVD screenshot remain an interactive operator prerequisite.
   - **Phase 02 (Frozen Foundation & Contracts)**: Root Gradle configuration, staged assembly guards, version catalog, shared module, domain contract source files, and test suites completed and verified via shell suite (57/57 tests PASS). Live Gradle build execution and Kotlin test runner execution remain pending downstream build phases.
   - **Phase 03 (Hub Reuse & Strict Server)**: Backend strict synchronization server, legacy isolation fences, admission preflight, schema integration, and operator tooling implemented across 26 files (6 in Doggo, 20 in sibling `glean-hub-sync`). Unit and route tests passing (36 passed, clippy clean). Live Mongo integration suites and mounted router tests explicitly deferred to Phase 07 device sync and Phase 08 release operations.
2. **Zero Fake Stubs & Staged Assembly**: The repository contains zero dummy application entrypoints or fake Room implementations. Root `settings.gradle.kts` conditionally guards `:androidApp` and `:desktopApp` until their modules are created in downstream phases.
3. **Cryptographic & Wrapper Integrity**: Gradle 8.11.1 wrapper distribution SHA-256 and wrapper jar SHA-256 are pinned and verified. Strict sync digest specification uses explicit big-endian length-framed byte buffers matching backend Rust specifications (reference vectors verified via Node.js runner; native Kotlin execution and live backend parity remain unverified under Gradle).
4. **Credential Redaction**: `CredentialBundle` and `HubConfig` override `toString()` to redact sensitive tokens and API keys, mitigating accidental logging via default string conversion (property access by callers remains unredacted).
5. **Contract Modularity**: Shared domain contracts in `shared/src/commonMain/kotlin/` have zero dependencies on Android platform classes (`android.*`) or concrete persistence/HTTP engines.
6. **Headless Infrastructure Isolation**: The Xpra display server binds zero public TCP listeners; all external GUI access is strictly mediated over encrypted SSH tunnels.
7. **Dependency Locking Configuration**: Dependency locking is declared via `lockAllConfigurations()` with `shared/gradle.lockfile` present on disk, awaiting live dependency resolution to record locked versions.
8. **Strict Storage & Route Isolation**: Strict collections (`_strict_v1_*`) are completely segregated from legacy collections; legacy routes reject strict apps with HTTP 426 (`protocolUnsupported`); strict routes enforce epoch coherence and fail-closed admission.
