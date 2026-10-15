# Codebase Summary

Generated on: 2026-10-15
Based on Repomix compaction snapshot: `repomix-output.xml` (point-in-time partial snapshot: 356 files packed, 5 excluded for security check, 763,908 tokens, 3,282,457 chars, generated during Phase 06 documentation updates on 2026-10-15)
---

## 1. Repository Overview

The `doggo` repository contains the implementation, build configuration, domain contracts, and architectural specifications for Doggo, an Android-first shared Compose Multiplatform task management application with secure multi-device synchronization against Glean Hub.

Following Phases 01–10, the repository contains the Android and shared Compose Multiplatform clients, Linux desktop source/distribution tooling, and the delivered Windows desktop DPAPI provider, isolated build, distributable, MSI artifact, and package-verification harness:
1. **Root Build & Version Catalog**: Staged Gradle build system, official SHA-256 pinned wrapper, and dependency catalog.
2. **Shared Kotlin Multiplatform Module (`shared/`)**: Domain models (`TaskDeadline`, `ReminderRule`, `Todo`), UI state/actions, identity/authentication contracts, strict sync canonical framing (`strict-v1` and `strict-v2`), `StrictV2Constants`, `StrictV2TodoData`, `StrictV2SyncOp`, `LegacyReceiptStatus`, `LegacyReceiptResult`, checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`), `StrictSyncV2Digest` binary preimage engine, `StrictTimeZoneResolver` and `StrictZoneOffsetResult` (`Single`, `Overlap`, `Gap`) with `ZoneRules` platform actuals, validation policies, Ktor HTTP transport, Hub auth client, auth session state machine, single-flight refresh coordinator, Android Keystore vault, Room KMP database (version 2, 7 entities, `DatabaseMigrations.MIGRATION_1_2`), BundledSQLiteDriver, local outbox mutation engine with intent masks and provenance tracking, staged full scan reconciler, fail-closed account leave protocol, shared Compose UI components, accessible light theme tokens (WCAG 2.1 AA verified), and comprehensive unit/instrumented/semantics/vector tests.
3. **Android Application Module (`androidApp/`)**: Native Android client hosting shared Compose UI, `DoggoApplication` process container, `SessionViewModel` reactive UI state orchestration, `AndroidSyncScheduler` WorkManager scheduling, `DoggoSyncWorker` background sync worker with generation/auth fencing, `AndroidNotificationAdapter` channel/scheduling candidate, `AndroidReminderCapabilitiesChecker` decoupling notification and exact alarm permissions, non-exported `DoggoAlarmReceiver`, debug network security configuration with strict loopback cleartext whitelist, backup/extraction security exclusions, release signing task-graph enforcement, and authored instrumented test suites.
4. **Desktop Application Module (`desktopApp/`)**: Compose Multiplatform JVM app with target-isolated Linux and Windows source sets; shared `CredentialVaultProvider` SPI, fail-closed `PlatformVaultDispatcher`, `SingleInstanceGuard`, `DesktopSyncScheduler`, Linux Secret Service provider, Windows current-user DPAPI provider, `DbusLinuxNotificationAdapter` with safe unavailability fallback, and candidate `WindowsNotificationAdapter` with XML escaping and explicit unavailable stub bridge.
5. **Host Virtualization, Security & Operations Tooling (`scripts/`)**: Headless Xpra display orchestration, Android SDK/JDK environment configuration, emulator management, Hub schema provisioning (`hub-schema-provision.mjs`), live Hub strict smoke harness (`hub-live-smoke.mjs`), release security and integrity auditor (`release-security-check.mjs`), maintenance recovery script (`hub-strict-recovery.js`), device smoke runner (`android-smoke.sh`), loopback sync fault-injection proxy (`debug-fault-proxy.mjs`), Linux/Windows package smoke runners, strict-v2 vector runner (`strict-v2-vector-runner.mjs`), and vector generator (`generate-strict-v2-vectors.mjs`).
6. **Hub Integration Artifacts (`integration/`)**: Canonical JSON schema definitions for `strict-v1` (`doggo-schema-v1.json`) and `strict-v2` (`doggo-schema-v2.json`), active schema (`doggo-schema.json`), and cryptographic reference test vector fixture (`strict-v2-vectors.json`).
7. **Automated Verification Harness (`tests/`)**: Automated shell suites validating scripts, Gradle contracts, Kotlin sources, and cryptographic digest vectors.
8. **Technical Documentation & Evidence (`docs/`)**: Architecture specifications, API contracts, deployment and disaster recovery guides, release acceptance specifications, toolchain evidence, operational runbooks, compatibility criteria, auth/credential specifications, local storage and sync specifications, design guidelines, coding standards, Android/Linux/Windows desktop guides and acceptance specifications, test summaries, and UI design mockups.
9. **Planning & Roadmap (`plans/`)**: Phased execution plans, architectural decisions, and verification reports.
### High-Level Directory Structure

```
.
├── .gitignore                          # Git ignore rules for Gradle, local environment, and build outputs
├── build.gradle.kts                    # Root build script with modern plugin declarations
├── gradle.properties                   # JVM memory, Kotlin daemon, AndroidX, and Compose flags
├── gradlew / gradlew.bat               # Official Gradle wrapper executables
├── settings.gradle.kts                 # Staged module assembly (guards :androidApp and :desktopApp)
├── androidApp/                          # Native Android application module
│   ├── build.gradle.kts                # Android application build configuration (AGP 8.7.3, minSdk 26, compileSdk 35)
│   ├── gradle.lockfile                 # Dependency lockfile (stub empty=)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # Base manifest (exported MainActivity, cleartext disabled, backup rules)
│       │   ├── kotlin/dev/doggo/android/
│       │   │   ├── doggo-application.kt    # Application container, singletons, cold-start persistence, wipe recovery
│       │   │   ├── main-activity.kt        # Single exported Activity hosting DoggoApp and SessionViewModel
│       │   │   ├── session-view-model.kt   # Retained ViewModel, reactive UI state flows, zero credential retention
│       │   │   ├── android-sync-scheduler.kt # WorkManager one-shot/periodic scheduler and fail-closed cancelAndAwait gate
│       │   │   ├── doggo-sync-worker.kt    # Background sync worker with wipe, auth, account, and generation fencing
│       │   │   ├── android-notification-adapter.kt # Candidate notification adapter & AlarmManager intent preparation
│       │   │   ├── android-reminder-capabilities.kt # Probes decoupling POST_NOTIFICATIONS from SCHEDULE_EXACT_ALARM
│       │   │   └── doggo-alarm-receiver.kt # Non-exported receiver handling offline reminder alarm dispatch
│       │   └── res/xml/
│       │       ├── backup_rules.xml        # API <= 30 backup exclusions (databases, sharedprefs, account files)
│       │       └── data_extraction_rules.xml # API >= 31 cloud and device transfer exclusions
│       ├── debug/
│       │   ├── AndroidManifest.xml     # Debug manifest overriding network security config
│       │   └── res/xml/
│       │       └── network_security_config.xml # Debug cleartext whitelist (127.0.0.1, localhost, 10.0.2.2)
│       ├── test/kotlin/dev/doggo/android/
│       │   └── android-notification-adapter-test.kt # Unit tests for adapter constants, intent generation, and capabilities
│       └── androidTest/kotlin/dev/doggo/android/
│           ├── todo-device-test.kt     # Authored instrumented device tests for ViewModel and store (unexecuted)
│           └── background-isolation-test.kt # Authored instrumented tests for WorkManager and isolation (unexecuted)
├── desktopApp/                         # Desktop application module (Kotlin JVM 2.1.10, Compose 1.7.3)
│   ├── build.gradle.kts                # Desktop build script, target detection (linux/windows), RPM/MSI config
│   ├── gradle-linux.lockfile           # Linux dependency lockfile (161 locked artifacts)
│   ├── gradle-windows.lockfile         # Windows target-specific dependency lockfile
│   └── src/
│       ├── main/kotlin/dev/doggo/desktop/
│       │   ├── main.kt                 # MainKt (RELEASE) and DevelopmentLauncher (DEBUG), window scaffold
│       │   ├── credential-vault-provider.kt # CredentialVaultProvider SPI
│       │   ├── platform-vault-dispatcher.kt # ServiceLoader vault dispatcher and target validation
│       │   ├── desktop-sync-scheduler.kt # Foreground sync scheduler and LeaveSchedulerCancelGate
│       │   └── single-instance-guard.kt # File-channel lock on doggo.lock
│       ├── linuxMain/
│       │   ├── kotlin/dev/doggo/desktop/linux/
│       │   │   ├── linux-secret-service-vault.kt # Freedesktop Secret Service vault implementation
│       │   │   └── linux-notification-adapter.kt # Freedesktop D-Bus notification candidate with resilient error handling
│       │   └── resources/META-INF/services/
│       │       └── dev.doggo.desktop.CredentialVaultProvider # ServiceLoader descriptor for Linux provider
│       ├── windowsMain/
│       │   ├── kotlin/dev/doggo/desktop/windows/
│       │   │   ├── windows-dpapi-vault.kt # Current-user DPAPI vault and provider
│       │   │   └── windows-notification-adapter.kt # Windows toast candidate, XML escaping, tag/group clear, and safe stub
│       │   └── resources/META-INF/services/
│       │       └── dev.doggo.desktop.CredentialVaultProvider # Windows ServiceLoader descriptor
│       ├── test/kotlin/dev/doggo/desktop/
│       │   ├── platform-vault-dispatcher-test.kt # SPI resolution, zero/duplicate/target validation tests
│       │   └── desktop-lifecycle-test.kt # Single instance lock, sync scheduling, and build mode tests
│       ├── linuxTest/kotlin/dev/doggo/desktop/linux/
│       │   ├── linux-secret-service-vault-test.kt # Secret Service status, store, replace, clear tests
│       │   └── linux-notification-adapter-test.kt # Unit tests for capabilities, missing daemon, and missing bus
│       └── windowsTest/kotlin/dev/doggo/desktop/windows/
│           ├── windows-dpapi-vault-test.kt # DPAPI provider, vault lifecycle, and native round-trip tests
│           └── windows-notification-adapter-test.kt # Unit tests for XML escaping, payload generation, and safe unavailability
├── gradle/
│   ├── libs.versions.toml              # Version catalog (Kotlin 2.1.10, Compose 1.7.3, AGP 8.7.3, Room 2.7.0-alpha12)
│   ├── verification-metadata.xml       # Gradle dependency checksum verification
│   └── wrapper/
│       ├── gradle-wrapper.jar          # Verified wrapper jar (SHA-256: 2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046)
│       └── gradle-wrapper.properties   # Official Gradle 8.11.1 distribution with SHA-256 checksum pinning
├── shared/                             # Shared Kotlin Multiplatform library
│   ├── build.gradle.kts                # KMP library configuration (dev.doggo.shared, minSdk 26, JVM 17)
│   ├── gradle.lockfile                 # Dependency locking definition
│   ├── schemas/                        # Exported Room database schemas
│   │   └── dev.doggo.shared.data.DoggoDatabase/
│   │       ├── 1.json                  # Canonical Room schema version 1 export
│   │       └── 2.json                  # Canonical Room schema version 2 export
│   └── src/
│       ├── commonMain/composeResources/
│       │   └── values/
│       │       └── strings.xml             # UI string resource catalog
│       ├── commonMain/kotlin/dev/doggo/shared/
│       │   ├── contract/               # Pure domain contracts & validation policies
│       │   │   ├── strict-sync-v2-contract.kt # Strict-v2 models, checked arithmetic, error enums
│       │   │   ├── strict-sync-v2-digest.kt   # Strict-v2 binary preimage framing and SHA-256
│       │   │   └── strict-timezone-resolver.kt # StrictTimeZoneResolver contract and StrictZoneOffsetResult hierarchy
│       │   ├── auth/                   # Hub authentication, HTTP client, and session state machine
│       │   ├── data/                   # Room entities, DAO, Database, and RoomAccountStore
│       │   │   ├── server-base-entity.kt       # Confirmed server base and permanent compact tombstones
│       │   │   ├── outbox-entity.kt            # Frozen strict mutations and delivery states
│       │   │   ├── editor-draft-entity.kt      # Uncommitted local user edits
│       │   │   ├── scan-staging-entity.kt      # Cursor pagination staging table
│       │   │   ├── account-sync-meta-entity.kt # Account sync metadata and active epoch
│   │   │   ├── doggo-dao.kt                # Atomic @Transaction DAO methods and queries
│   │   │   ├── doggo-database.kt           # RoomDatabase definition (version 2, 7 entities)
│   │   │   ├── database-migrations.kt      # Migration registry (DatabaseMigrations.MIGRATION_1_2)
│   │   │   ├── account-local-settings-entity.kt # Account/device reminder and notification defaults
│   │   │   ├── reminder-delivery-ledger-entity.kt # Durable native reminder delivery ledger and claims
│   │   │   └── room-account-store.kt       # Mutex-guarded AccountStore with SQLite persistence
│       │   └── sync/                   # Strict synchronization protocol orchestration
│       │       ├── contract-transition-coordinator.kt # 6-phase upgrade state machine, receipt match & safe translation
│       │       ├── hub-sync-dtos.kt            # Wire DTOs for bootstrap, push, pull, and legacy receipts
│       │       ├── hub-sync-client.kt          # HTTP transport adapter for sync routes and legacy receipts
│       │       ├── staged-full-scan.kt         # Monotonic staging, pagination loop & consistency checks
│       │       ├── outbox-reconciliation.kt    # FIFO dispatch, CAS ack matching, and UnknownSuperseded
│       │       ├── account-sync-coordinator.kt # 3-stage sync round, transition trigger, conflict actions, generation fencing
│       │       └── account-leave-protocol.kt   # Fail-closed guarded leave & wipe marker protocol
│       │   ├── theme/                  # Accessible light theme and typography
│       │   │   ├── doggo-theme.kt              # Color tokens, WCAG 2.1 AA contrast palette, Theme wrapper
│       │   │   └── doggo-typography.kt         # Type scale, line heights, system SansSerif basis
│       │   └── ui/                     # Shared Compose Multiplatform UI components
│       │       ├── doggo-app.kt                # Root responsive scaffold (compact stack / expanded sidebar)
│       │       ├── account-form.kt             # Login & registration form with transient password masking
│       │       ├── access-request-panel.kt     # App access request, status reload, and admin guidance
│       │       ├── todo-list.kt                # LazyColumn task list, stable keys, filter chips, counts
│       │       ├── todo-editor.kt              # Task creation/editing dialog with TitlePolicy validation
│       │       ├── sync-feedback.kt            # Sync progress, last reconciled timestamp, status chips
│       │       ├── conflict-review.kt          # Conflict dialog (Discard, Reapply, CopyAsNew)
│       │       ├── leave-guard.kt              # Guarded account sign-out dialog with unsynced tallies
│       │       └── license-notices.kt          # Bundled open-source dependency license viewer
│       ├── androidMain/kotlin/dev/doggo/shared/
│       │   ├── auth/                   # Android OkHttp engine and Keystore vault
│       │   ├── contract/               # Platform actual contracts
│       │   │   └── strict-timezone-resolver-android.kt # Android ZoneRules platform actual
│       │   └── data/
│       │       ├── android-database-builder.kt # BundledSQLiteDriver Room database builder
│       │       └── android-account-files.kt    # Account directory partitioning and wipe markers
│       ├── jvmMain/kotlin/dev/doggo/shared/
│       │   ├── auth/                   # JVM Java HTTP engine
│       │   ├── contract/               # Platform actual contracts
│       │   │   └── strict-timezone-resolver-jvm.kt     # JVM ZoneRules platform actual
│       │   └── data/
│       │       ├── jvm-database-builder.kt     # Desktop BundledSQLiteDriver Room database builder
│       │       └── jvm-account-files.kt        # Desktop account directory partitioning and wipe markers
│       ├── commonTest/kotlin/dev/doggo/shared/
│   │   ├── contract/               # Contract unit tests (digest, policy, keys)
│   │   │   ├── task-deadline-reminder-models-test.kt # Temporal domain models and overflow arithmetic tests
│   │   │   ├── strict-v2-digest-vector-test.kt   # Strict-v2 vector fixtures (5 tests)
│   │   │   ├── strict-v2-digest-extended-test.kt # Strict-v2 edge cases & checked overflow (5 tests)
│   │   │   └── strict-timezone-resolver-test.kt  # 7 timezone & DST transition vectors (gap, overlap, boundaries)
│       │   ├── auth/                   # Auth client, session, and refresh tests
│       │   └── sync/                   # Phase 05-06 sync test suites
│       │       ├── contract-transition-test.kt # Transition states, durable resume, review gating (4 tests)
│       │       ├── hub-sync-client-test.kt     # Push, pull, bootstrap, legacy receipts, and batch bounds tests (12 tests)
│       │       ├── staged-full-scan-test.kt    # Pagination, loops, overflow, migration enrichment tests (13 tests)
│       │       ├── outbox-reconciliation-test.kt # CAS acks, FIFO, UnknownSuperseded, retries (12 tests)
│       │       ├── account-sync-coordinator-test.kt # Sync loop, transition trigger, conflict actions, cancellation (9 tests)
│       │       └── account-leave-protocol-test.kt # Review guards, teardown order, wipe recovery (7 tests)
│       │   └── ui/                     # Phase 06 UI semantics & theme test suites
│       │       └── todo-semantics-test.kt      # Contrast calculations, password redaction, UI invariants
│       ├── jvmTest/kotlin/dev/doggo/shared/data/
│   │   ├── room-migration-v1-to-v2-test.kt     # Explicit v1->v2 SQLite migration verification
│   │   └── room-account-store-test.kt      # BundledSQLite driver temporary database tests
│       └── androidInstrumentedTest/kotlin/dev/doggo/shared/
│           ├── auth/                   # Keystore vault instrumented tests
│           └── data/
│               └── room-account-store-test.kt  # Android Room instrumented tests
├── integration/                         # Integration schemas and specifications
│   └── glean-hub/
│       ├── doggo-schema.json             # Exact strict-v1 todos schema specification
│       ├── doggo-schema-v1.json          # Canonical strict-v1 schema (fingerprint: 3361a80f...)
│       ├── doggo-schema-v2.json          # Canonical strict-v2 schema (fingerprint: 5f539551...)
│       └── strict-v2-vectors.json        # 20 cross-stack cryptographic test vectors (9 valid, 11 rejections)
├── scripts/                            # Host automation, operations, security and integration scripts
│   ├── start-xpra.sh                   # Headless Xpra virtual display runner
│   ├── android-sdk-env.sh              # Android SDK and JDK environment configuration
│   ├── start-android-emulator.sh       # Hardware-accelerated emulator launcher
│   ├── hub-schema-provision.mjs        # Glean Hub schema registration and provisioning
│   ├── hub-compatibility-smoke.mjs     # Hub compatibility smoke runner
│   ├── hub-live-smoke.mjs              # Live Hub strict-v1 smoke runner and offline fixture self-test
│   ├── release-security-check.mjs      # Release APK and source security auditor
│   ├── hub-strict-recovery.js          # Maintenance-only fresh-epoch disaster recovery runner
│   ├── android-smoke.sh                # Device smoke runner with reverse port forwarding and screenshot capture
│   ├── debug-fault-proxy.mjs           # Loopback sync fault-injection proxy (drop-push-response, drop-refresh-response)
│   ├── linux-package-smoke.sh          # Linux desktop package/distributable/RPM audit runner
│   ├── windows-package-smoke.ps1       # Windows distributable/MSI audit and COM metadata inspection
│   ├── strict-v2-vector-runner.mjs     # Strict-v2 vector & schema fingerprint verification runner
│   ├── generate-strict-v2-vectors.mjs  # Strict-v2 vector reference generator
│   ├── hub-strict-upgrade.mjs          # Strict-v1 to strict-v2 CLI upgrade runner with bounded polling & read-back
│   └── hub-strict-upgrade.test.mjs     # Unit test suite for CLI upgrade runner (mock HTTP server)
├── tests/                              # Automated test suites
└── docs/                               # Developer documentation and operational runbooks
    ├── android-acceptance.md           # Android composition, security invariants, and acceptance spec
    ├── api-contract.md                 # REST and strict sync wire protocol contract
    ├── auth-acceptance.md              # Acceptance criteria matrix for auth, session, and vault
    ├── authentication-and-credentials.md # Auth architecture, security invariants, and credential vault
    ├── code-standards.md               # Coding, testing, and security standards
    ├── codebase-summary.md             # This document (codebase inventory & metrics)
    ├── design-guidelines.md            # Visual language, tokens, WCAG 2.1 AA contrast, and UI standards
    ├── development-roadmap.md          # Architectural evolution roadmap
    ├── font-provenance.md              # Upstream font audit and system font fallback decision
    ├── foundation-evidence.md          # Phase 02 cryptographic and toolchain evidence
    ├── headless-android-setup.md       # Operational runbook for headless Xpra & Android
    ├── host-tooling-evidence.md        # Verified host hardware, virtualization, and tool capabilities
    ├── hub-compatibility-acceptance.md # Architectural acceptance criteria and invariants
    ├── hub-schema-integration.md       # Integration guide and registration lifecycle
    ├── deployment-guide.md             # Hub, Android, Linux, and Windows deployment procedures
    ├── linux-acceptance.md             # Phase 09 Linux desktop and RPM acceptance specification
    ├── linux-desktop-guide.md          # Phase 09 Linux desktop operations and build guide
    ├── windows-acceptance.md           # Phase 10 Windows desktop and MSI acceptance specification
    ├── windows-desktop-guide.md        # Windows DPAPI, build, packaging, and operations guide
    ├── local-storage-and-sync.md       # Phase 05 local storage, Room entities, and sync spec
    ├── project-changelog.md            # Chronological project changelog across phases
    ├── project-overview-pdr.md         # Product development requirements and traceability
    ├── project-roadmap.md              # Project milestones, phase progress, and changelog
    ├── release-acceptance.md           # Phase 08 release acceptance and verification matrix
    ├── security-and-recovery.md        # Phase 08 security model and disaster recovery runbook
    ├── sync-acceptance.md              # Phase 05 sync acceptance gates and test matrix
    ├── system-architecture.md          # System architecture and multiplatform components
    ├── toolchain-versions.md           # Pinned toolchain compatibility matrix
    └── evidence/                       # Evidence reports and visual design baselines
        ├── android-release-summary.md  # Phase 08 Android release operations summary
        ├── android-test-summary.md     # Phase 07 test summary and artifact inventory
        ├── hub-recovery-summary.md     # Phase 08 Hub operational recovery summary
        ├── android-debug-*.png         # 7 visual design mockups for debug screen states
        ├── android-release-*.png       # 7 visual design mockups for release screen states
        ├── linux-test-summary.md       # Phase 09 Linux desktop verification test summary
        ├── windows-test-summary.md     # Phase 10 Windows tests, bundle, and MSI evidence
        ├── linux-*.png                 # 7 visual design mockups for Linux desktop screen states
        └── windows-*.png               # 7 visual design mockups for Windows desktop screen states
```

---

## 2. Core Components & Artifacts

### 2.1 Gradle Build System & Version Catalog

| File | Purpose | Key Details |
|---|---|---|
| `settings.gradle.kts` | Staged assembly & plugin management | Unconditionally includes `:shared`; conditionally guards `:androidApp` and `:desktopApp` based on file existence. |
| `build.gradle.kts` | Root build script | Registers `clean` task; declares plugins with `apply false`. |
| `gradle.properties` | Build engine settings | Configures 4 GB Gradle JVM heap, Kotlin daemon parallel compilation, AndroidX support, and Compose compiler metrics. |
| `gradle/libs.versions.toml` | Pinned version catalog | Pins Kotlin 2.1.10, Compose Multiplatform 1.7.3, AGP 8.7.3, Coroutines 1.10.1, Serialization 1.8.0, Ktor 3.1.0, Room 2.7.0-alpha12, and SQLite 2.5.0-SNAPSHOT. |
| `gradle/wrapper/gradle-wrapper.properties` | Wrapper distribution | Specifies Gradle 8.11.1 bin distribution with official checksum. |
| `gradle/wrapper/gradle-wrapper.jar` | Wrapper binary launcher | Checksum verified against known Gradle release hash. |
| `gradle/verification-metadata.xml` | Dependency verification | Checksum configuration file establishing dependency verification rules during resolution. |

### 2.2 Shared Module Build & Contracts (`shared/`)

- Applied plugins: `com.android.library`, `kotlinMultiplatform`, `kotlinxSerialization`, `androidxRoom`.
- Namespace: `dev.doggo.shared`, `minSdk = 26`, `compileSdk = 35`, JVM bytecode target 17.
- Room KMP schema export configured to `shared/schemas/`.
- Domain contracts (`todo-models.kt`, `todo-ui-state.kt`, `todo-action.kt`, `safe-errors.kt`, `account-key.kt`, `strict-sync-contract.kt`, `title-policy.kt`, `endpoint-policy.kt`) have zero dependencies on Android classes or UI frameworks.

### 2.3 Authentication Subsystem (Phase 04)

- `hub-auth-dtos.kt`, `hub-http-client.kt`, `hub-auth-client.kt`: Wire DTOs and client enforcing origin allowlists, route classification, dual-auth headers, and bounded timeouts.
- `hub-auth-session.kt`, `single-flight-refresh.kt`: Generation-fenced auth state machine and concurrent refresh leader.
- `android-keystore-vault.kt`: Android Keystore AES-256-GCM vault with fresh nonces, AAD binding, and atomic file replacement.

### 2.4 Local Storage & Strict Synchronization Subsystem (Phase 05)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `server-base-entity.kt` | `ServerBaseEntity` | Authoritative confirmed server state table (`server_base`); permanent compact tombstones (`deleted = true`, empty title). |
| `outbox-entity.kt` | `OutboxEntity` | Strict mutation queue (`outbox`); frozen fields (`opId`, `epoch`, `kind`, `expectedRevision`, `digest`, `localRevision`); delivery states: `PENDING`, `DISPATCHING`, `UNCERTAIN`, `CONFLICT`, `UNKNOWN_SUPERSEDED`. |
| `editor-draft-entity.kt` | `EditorDraftEntity` | Uncommitted local user edits table (`editor_drafts`); preserved on ack if `draft.localRevision > outbox.localRevision`. |
| `scan-staging-entity.kt` | `ScanStagingEntity` | Cursor pull pagination staging table (`scan_staging`); promoted atomically upon reaching terminal page. |
| `account-sync-meta-entity.kt` | `AccountSyncMetaEntity` | Account partition metadata (`account_sync_meta`); tracks `activeEpoch`, `localRevisionCounter`, `syncStatus`. |
| `doggo-dao.kt` | `DoggoDao` | Room DAO with atomic `@Transaction` methods: `saveTodoAtomic`, `deleteTodoAtomic`, `cancelUnsentCreateAtomic`, `reconcileAcceptedAtomic`, `promoteStagingAtomic`, `replaceOutboxAtomic`, `discardConflictAtomic`, `freezeOutboxAtomic`, `wipeAllTablesAtomic`. |
| `doggo-database.kt` | `DoggoDatabase` | Room database definition (version 2, 7 entities) backed by `BundledSQLiteDriver`. |
| `database-migrations.kt` | `DatabaseMigrations.MIGRATION_1_2` | Explicit SQLite migration upgrading Room schema 1 to 2; registered in `DatabaseMigrations.ALL`. |
| `account-local-settings-entity.kt` | `AccountLocalSettingsEntity` | Account/device reminder and notification defaults table (`account_local_settings`); singleton `id = 'singleton'`. |
| `reminder-delivery-ledger-entity.kt` | `ReminderDeliveryLedgerEntity` | Durable occurrence state and claim outcomes for native reminders (`reminder_delivery_ledger`); composite PK 5-tuple; tracks `claimOutcome` and `cancellationState`. |
| `room-account-store.kt` | `RoomAccountStore` | Implementation of `AccountStore` with mutex-serialized edits, outbox coalescing, unsent cancellation, intent mask derivation, and dispatch recovery. |
| `android-database-builder.kt` | `createAndroidDoggoDatabase()` | Platform Room database builder for Android with `BundledSQLiteDriver`. |
| `jvm-database-builder.kt` | `createJvmDoggoDatabase()` | Platform Room database builder for Desktop JVM with `BundledSQLiteDriver`. |
| `android-account-files.kt` | `AndroidAccountFiles` | Android app-private storage partitioner by `AccountKey` SHA-256, path traversal guard, and `wipe.marker` manager. |
| `jvm-account-files.kt` | `JvmAccountFiles` | Desktop storage partitioner (XDG / AppData / Library) by `AccountKey` SHA-256, path traversal guard, and `wipe.marker` manager. |
| `hub-sync-dtos.kt` | `StrictBootstrapResponseDto`, `StrictPushRequestDto`, `StrictPushResponseDto`, `StrictPullResponseDto` | Wire DTOs for strict synchronization endpoints. |
| `hub-sync-client.kt` | `HubSyncClient` | Sync transport adapter enforcing batch size `<= 100`, payload size `<= 256 KiB`, and `SafeError` translation. |
| `staged-full-scan.kt` | `StagedFullScan` | 3-stage scan reconciler: cursor pagination, loop/overflow/non-progress detection, pre-scan identity check (`CONSISTENCY_ERROR`), and monotonic promotion. |
| `outbox-reconciliation.kt` | `OutboxReconciliation` | FIFO row-head selection, head blocking on conflicts, dispatch freezing via `freezeOutboxAtomic`, exact CAS ack verification, and `UNKNOWN_SUPERSEDED` classification. |
| `account-sync-coordinator.kt` | `AccountSyncCoordinator` | 3-stage sync coordinator (`SyncCoordinator`), mutex serialization, generation fencing, conflict actions (`discardConflict`, `reapplyConflict`, `copyAsNew`), and cancellation safety. |
| `account-leave-protocol.kt` | `AccountLeaveProtocol` | 5-category review work guard, store freezing, delivery gate drain synchronization, fail-closed teardown keeping wipe marker on cancellation failure, and startup `completePendingWipe`. |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `hub-sync-client-test.kt` | Push, pull, bootstrap, batch size <= 100, payload <= 256 KiB, HTTP error envelopes. | **PASSED** (JVM) |
| `staged-full-scan-test.kt` | Cursor loops, batch overflow, non-progressing cursors, missing pre-scan identity check, monotonic promotion, divergent revisions. | **PASSED** (JVM) |
| `outbox-reconciliation-test.kt` | FIFO row-head dispatch, head blocking on conflicts, dispatch freezing, exact CAS ack matching, newer draft preservation, UnknownSuperseded, retries. | **PASSED** (JVM) |
| `account-sync-coordinator-test.kt` | 3-stage sync loop, mutex serialization, generation fencing, conflict workflows, push cancellation safety. | **PASSED** (JVM) |
| `account-leave-protocol-test.kt` | 5-category review guard, freeze-before-review, marker write failure abort, fail-closed marker retention on scheduler cancel failure, database deletion fail-closed, completePendingWipe. | **PASSED** (JVM) |
| `task-deadline-reminder-models-test.kt` | `TaskDeadline`, `ReminderRule`, `Todo` models, checked 64-bit integer arithmetic, occurrence bounds. | **PASSED** (JVM) |
| `room-migration-v1-to-v2-test.kt` (JVM) | Explicit Room v1->v2 migration on BundledSQLite: schema alteration, legacy outbox intent derivation, table creation. | **PASSED** (JVM) |
| `room-account-store-test.kt` (JVM) | Real SQLite temporary database: schema v2, atomic saves, v2 schedule tokens, unsent create-delete cancel, FIFO outbox, monotonic base advancement, crash recovery, wipe. | **PASSED** (JVM) |
| `room-account-store-test.kt` (Android) | Android instrumented Room tests with app-private storage and Keystore integration. | **DEFERRED** (Phase 08) |

### 2.5 Shared Compose UI & Theme Subsystem (Phase 06)

| Component File | Primary Types & Composables | Architectural Role |
|---|---|---|
| `doggo-theme.kt` | `DoggoTheme`, `DoggoColors`, `DoggoCustomColors` | Accessible light palette (WCAG 2.1 AA verified), Material 3 `LightColorScheme`, dynamic colors disabled. |
| `doggo-typography.kt` | `DoggoTypography` | Pinned typography scale (12sp–34sp) based on Compose `FontFamily.SansSerif` for universal platform support. |
| `doggo-app.kt` | `DoggoApp`, `DoggoContent` | Root composable with responsive layout (<840dp compact vertical stack, >=840dp expanded sidebar). |
| `account-form.kt` | `AccountForm` | Login/signup toggle, transient password masking, non-saveable state, auto-clearing upon submission. |
| `access-request-panel.kt` | `AccessRequestPanel` | App access request submission, status polling (`pending`, `rejected`, `approved`, `unknown`), sign-out. |
| `todo-list.kt` | `TodoList`, `TodoItemRow` | LazyColumn task projection, stable `key = { it.rowId }`, non-color completion state, >=48dp touch targets. |
| `todo-editor.kt` | `TodoEditorDialog` | Modal task editor, `TitlePolicy` validation (1–200 Unicode scalars), draft error display. |
| `sync-feedback.kt` | `SyncFeedback` | Sync status badge, last reconciled relative/UTC timestamp formatting, pause/reset error guidance. |
| `conflict-review.kt` | `ConflictReviewDialog` | Side-by-side local vs remote comparison, user actions (`Discard`, `Reapply`, `CopyAsNew`, `Decide Later`). |
| `leave-guard.kt` | `LeaveGuardDialog` | Fail-closed sign-out warning displaying uncommitted draft, pending, uncertain, and conflict counts. |
| `license-notices.kt` | `LicenseNoticesDialog` | In-app open-source attribution viewer for bundled third-party libraries and fonts. |
| `strings.xml` | Resource definitions | Compose Multiplatform string catalog (`composeResources/values/strings.xml`). |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `todo-semantics-test.kt` | 16 WCAG 2.1 AA relative luminance & contrast pairs (Ink, Page, White, Cream, MintSurface, Coral, Muted, Outline), `AuthSubmitAction.toString()` password redaction, `TitlePolicy` editor validation, `LeaveGuardState` tallies, `AccessRequestInfo` statuses, `SyncProgress` formatting and timestamp calculation. | **PASSED** (JVM, 7/7 tests) |

### 2.6 Shared Scheduling, Persisted Eligibility and Account Safety (Phase 07)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `reminder-schedule-calculator.kt` | `ReminderScheduleCalculator` | Checked 64-bit integer arithmetic, 1..10 repeat bounds, strict 5-min grace window (300,000ms included, 300,001ms skipped), latest opportunity selection, and consumed barrier blocking. |
| `persisted-schedule-projector.kt` | `PersistedScheduleProjector` | Draft-free projection from server_base + submitted outbox, nullable clearing, conflict head suppression, completion/delete/disable cancellation barriers. |
| `reminder-occurrence-store.kt` | `ReminderOccurrenceStore` | Durable occurrence ledger lifecycle, atomic claim-before-post (`evaluateAndClaimDueOpportunity`), orphan task cancellation, posted notification removal acknowledgment (`clearPostedNotificationId`). |
| `reminder-delivery-gate.kt` | `ReminderDeliveryGate`, `DeliveryGateState` | Three-tier lock hierarchy (`gate -> mutex -> DB tx`), fail-closed generation/wipe checks, teardown drain synchronization (`closeAndDrain`). |
| `reminder-scheduling-coordinator.kt` | `ReminderSchedulingCoordinator` | Per-account coordinator, mutation-gated reconciliation (`withMutationAdmission`), pre-post projection recheck, opportunity delivery semantics, teardown cancellation gate. |
| `reminder-scheduler.kt` | `NativeReminderAdapter`, `ScheduledAlarmHandle` | Platform adapter contracts for capability querying, schedule reconciliation, notification posting/removal, and account-wide cancellation. |
| `reminder-capability.kt` | `ReminderCapabilities` | Platform capability abstractions for alarms and notifications. |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `reminder-schedule-calculator-test.kt` | Checked arithmetic, 1..10 repeat bounds, exact 300,000ms grace boundary, latest opportunity selection, consumed barrier blocking. | **PASSED** (JVM, 9/9 tests) |
| `persisted-schedule-projection-test.kt` | Draft-free projection, base + outbox overlay, nullable clearing, conflict head suppression, completion/delete barriers. | **PASSED** (JVM, 5/5 tests) |
| `reminder-occurrence-store-test.kt` | Ledger persistence, atomic claim, grace skip marking, and idempotent sync. | **PASSED** (JVM, 4/4 tests) |
| `reminder-delivery-gate-test.kt` | Mutation/delivery admission, drain signal, wipe marker rejection, and generation mismatch guard. | **PASSED** (JVM, 4/4 tests) |
| `reminder-scheduling-coordinator-sqlite-smoke-test.kt` | Real BundledSQLite smoke test exercising local save/delete/complete/reopen, title update identity retention, grace bounds, duplicate callback rejection, and wipe race protection. | **PASSED** (JVM, 2/2 tests) |
| `reminder-teardown-barrier-sqlite-test.kt` | Real BundledSQLite test validating teardown gate drain synchronization, fail-closed wipe marker retention on cancel failure, and startup wipe recovery. | **PASSED** (JVM, 1/1 test) |

### 2.7 Glean Hub Integration Artifacts & Host Scripts

- `integration/glean-hub/doggo-schema.json`: Canonical strict-v1 todos schema specification.
- `scripts/`: Headless Xpra display orchestration (`start-xpra.sh`), Android SDK/JDK environment configuration (`android-sdk-env.sh`), emulator management (`start-android-emulator.sh`), Hub provisioning/smoke runners (`hub-schema-provision.mjs`, `hub-compatibility-smoke.mjs`), device smoke runner (`android-smoke.sh`), and loopback sync fault-injection proxy (`debug-fault-proxy.mjs`).

### 2.8 Native Android Application Module (Phase 07)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `build.gradle.kts` | `:androidApp` build configuration | Configures AGP 8.7.3, compileSdk 35, minSdk 26, Compose compiler metrics, Room/Ktor deps, and release signing task graph enforcement. |
| `gradle.lockfile` | Dependency locking | Lenient dependency lockfile definition for `:androidApp`. |
| `AndroidManifest.xml` (main) | Manifest declaration | Single exported `MainActivity`, disables application backup (`allowBackup="false"`), and enforces `usesCleartextTraffic="false"`. |
| `backup_rules.xml` | Backup security config | Pre-API 31 XML exclusion rules for SQLite databases, shared preferences, and `accounts/` storage directory. |
| `data_extraction_rules.xml` | Extraction security config | API 31+ XML rules prohibiting cloud backup and device-to-device transfer of private app directories. |
| `AndroidManifest.xml` (debug) | Debug manifest override | Links debug-only `network_security_config.xml` to allow loopback HTTP cleartext traffic for local dev. |
| `network_security_config.xml` | Cleartext network whitelist | Permits cleartext HTTP traffic strictly to `127.0.0.1`, `localhost`, and `10.0.2.2`; custom CAs and public domains disallowed. |
| `main-activity.kt` | `MainActivity` | Single exported `ComponentActivity` providing Android Compose container hosting `DoggoApp(viewModel)`. |
| `doggo-application.kt` | `DoggoApplication`, `AccountRuntime` | Process container managing singletons (`vault`, `accountFiles`, `hubConfig`, `authSession`, `syncScheduler`), active `AccountRuntime`, atomic `active_account.dat` persistence, and synchronous startup wipe recovery (`completePendingWipesOnStartup`). |
| `session-view-model.kt` | `SessionViewModel` | Retained `ViewModel` binding `TodoUiState` to auth/sync StateFlows, dispatching `TodoAction`, sanitizing UI errors (`SafeError.Internal/Validation`), and maintaining zero credential retention. |
| `android-sync-scheduler.kt` | `AndroidSyncScheduler` | Implements `SyncScheduler` and `LeaveSchedulerCancelGate` using WorkManager; configures unique one-shot and periodic work with network constraints and account tags; `cancelAndAwait()` quiescence gate polls WorkManager and coordinator mutex with 5s timeout and fail-closed exception throwing. |
| `doggo-sync-worker.kt` | `DoggoSyncWorker` | WorkManager `CoroutineWorker` executing background sync with wipe marker inspection, auth session retry/mismatch checks, and persisted generation counter validation. |
| `todo-device-test.kt` | `TodoDeviceTest` | Authored instrumented device test suite covering ViewModel initial state, filter switching, editor lifecycle, and direct store operations (unexecuted on device; host SDK pending). |
| `background-isolation-test.kt` | `BackgroundIsolationTest` | Authored instrumented test suite covering WorkManager background worker failure on pending wipe/stale generation, startup wipe cleanup, and scheduler cancel-and-await quiescence (unexecuted on device; host SDK pending). |
| `android-smoke.sh` | Shell smoke script | Automated device smoke verification script supporting adb device selection, reverse port forwarding, and screenshot capture. |
| `debug-fault-proxy.mjs` | Node fault proxy | Loopback HTTP proxy (127.0.0.1:3000 -> 3001) for deterministic sync fault injection (`drop-push-response`, `drop-refresh-response`) with strict route allowlist and zero payload logging. |


### 2.9 Phase 08 Release Operations, Security Auditing & Recovery Tooling (`scripts/`)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `hub-live-smoke.mjs` | `runLiveSmokeSuite()`, local fixture server | Automated strict-v1 integration smoke runner verifying health, admission read-back, auth session, structured push/pull with specific rowId assertions, base conflict, frozen replay, epochMismatch, and permanent empty tombstones. Features standalone `--fixture-test` offline mode (18/18 checks pass). |
| `release-security-check.mjs` | `runReleaseSecurityAudit()`, `diagnoseSource()` | Fail-closed security auditor inspecting release APK signatures (v2/v3), explicit `allowBackup="false"`, `debuggable="false"`, `usesCleartextTraffic="false"`, non-main exported components rejection, networkSecurityConfig stripping, and static source secret scanning. |
| `hub-strict-recovery.js` | `executeRecovery()`, `validateTodoRecord()` | Maintenance disaster recovery script for `mongosh` requiring `CONFIRM_MAINTENANCE_QUIESCENCE=true`, generating fresh UUID v4 epoch, targeting target collection `app_<appId>`, resetting records to revision 1 with `lastOp = null`, maintaining permanent empty tombstones, and executing journaled Compare-And-Swap on `_schemas`. |

### 2.9 Desktop Application Module & Packaging Tooling (Phases 09–10)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `build.gradle.kts` | `:desktopApp` build script | Configures Compose Multiplatform 1.7.3 on Kotlin JVM 2.1.10; detects and isolates Linux/Windows host targets; binds D-Bus only on Linux and JNA only on Windows; configures RPM/MSI distributions and target-specific lockfiles. |
| `gradle-linux.lockfile` | Dependency locking definition | Linux desktop dependency lockfile (161 locked artifacts). |
| `gradle-windows.lockfile` | Dependency locking definition | Windows desktop target lockfile, independently resolved from Linux. |
| `credential-vault-provider.kt` | `CredentialVaultProvider` | OS credential-vault Service Provider Interface for desktop targets. |
| `platform-vault-dispatcher.kt` | `PlatformVaultDispatcher` | Uses Java `ServiceLoader`; requires exactly one provider matching the active target and fails closed for zero or duplicate matches. |
| `single-instance-guard.kt` | `SingleInstanceGuard` | Acquires the OS file-channel lock before vault/database initialization to prevent concurrent launch and profile races. |
| `desktop-sync-scheduler.kt` | `DesktopSyncScheduler` | Window-scoped immediate/15-minute sync and `LeaveSchedulerCancelGate` quiescence. |
| `main.kt` | `MainKt`, `DevelopmentLauncher`, `DesktopController`, `DesktopAccountRuntime` | Desktop Compose entrypoints and runtime; production launcher uses RELEASE mode, while `runDebug` enables loopback development mode. |
| `linux-secret-service-vault.kt` | `LinuxSecretServiceVault`, provider, client | Linux Secret Service credential provider over D-Bus; no plaintext token fallback. |
| `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-dpapi-vault.kt` | `WindowsDpapiVault`, `WindowsDpapiVaultProvider`, `JnaWindowsDpapiProtector` | Windows user-scoped DPAPI credential bundle with temporary-file/atomic replacement and plaintext byte-buffer clearing. `applyRestrictiveUserAcl()` is best-effort; ACL failures are suppressed. |
| `META-INF/services/dev.doggo.desktop.CredentialVaultProvider` | ServiceLoader descriptors | Target-specific registrations select the Linux or Windows provider; Windows packaging contains one Windows provider and excludes Linux D-Bus classes. |
| `linux-package-smoke.sh` / `windows-package-smoke.ps1` | Platform package audits | Inspect target-specific distributable contents; Windows runner also computes MSI SHA-256, reports signature status, and inspects MSI metadata using COM. |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `platform-vault-dispatcher-test.kt` | Host target and ServiceLoader provider resolution; zero-match, duplicate-match, and mismatch fail-closed cases. | **PASSED** (5/5 in the Windows suite) |
| `desktop-lifecycle-test.kt` | Single-instance lock, scheduler/quiescence behavior, and RELEASE/DEBUG origin policies. | **PASSED** (8/8 in the Windows suite) |
| `linux-secret-service-vault-test.kt` | Linux Secret Service status, atomic storage/replacement, corruption handling, and clear. | **PASSED** (Linux, 6/6 per Phase 09 evidence) |
| `windows-dpapi-vault-test.kt` | DPAPI vault status, store/load, replacement/deletion, corrupt payload handling, provider discovery, target isolation, native DPAPI round-trip, and stored-file ACL check on ACL-capable filesystems. | **PASSED** (Windows, 12/12 per Phase 10 evidence; ACL failure paths are not covered) |

Phase 10 implementation is recorded at **100% (2026-10-14)**. The evidence summary reports 25/25 Windows desktop tests and 96/96 shared JVM tests passing (121/121 combined), a verified `Doggo.exe` bundle, and a generated unsigned 100.68 MB MSI. Technical acceptance is blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification; package metadata inspection is not installed-runtime acceptance.
### 2.10 Strict Timezone & DST Resolution Subsystem (`shared/`)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `strict-timezone-resolver.kt` | `StrictTimeZoneResolver`, `StrictZoneOffsetResult` (`Single`, `Overlap`, `Gap`) | Interface contract defining strict local date/time offset resolution, instant resolution, calendar day start, next day boundary calculation, and zone validation. |
| `strict-timezone-resolver-jvm.kt` | `getStrictTimeZoneResolver()` | JVM platform actual binding delegating to standard `java.time.zone.ZoneRules` (Java 8+). |
| `strict-timezone-resolver-android.kt` | `getStrictTimeZoneResolver()` | Android platform actual binding delegating to standard `java.time.zone.ZoneRules` (API 26+). |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `strict-timezone-resolver-test.kt` | 7 DST and boundary vectors: NY Spring gap (0 valid offsets, instant throws `IllegalArgumentException`), NY Fall overlap (2 offsets separated by 3600s, explicit offset required), standard time, 23h spring boundary, 25h fall boundary, zone/bounds validation, midnight gap transition. | **RECORDED PASSED** (Recorded in Phase 02 QA report, 7/7 JVM tests under `--dependency-verification=off`; API 26 device pending SDK) |

### 2.11 Native Candidate Notification Adapters (`androidApp/` & `desktopApp/`)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `android-notification-adapter.kt` | `AndroidNotificationAdapter` | Android notification channel (`doggo_reminders`) and AlarmManager intent factory (`setExactAndAllowWhileIdle`). |
| `android-reminder-capabilities.kt` | `AndroidReminderCapabilitiesChecker` | Capability checker decoupling runtime `POST_NOTIFICATIONS` from `SCHEDULE_EXACT_ALARM` permissions. |
| `doggo-alarm-receiver.kt` | `DoggoAlarmReceiver` | Non-exported `BroadcastReceiver` (`android:exported="false"`) handling offline alarm delivery with extras validation. |
| `linux-notification-adapter.kt` | `DbusLinuxNotificationAdapter`, `SessionBusNotificationTransport` | Freedesktop `/org/freedesktop/Notifications` D-Bus adapter mapping missing daemon (`ServiceUnknown`) to `LinuxNotificationStatus.Unavailable`. |
| `windows-notification-adapter.kt` | `WindowsNotificationAdapter`, `WindowsToastBridge`, `JnaWindowsToastBridge` | Windows toast candidate configuring AUMID `dev.doggo.desktop` and XML escaping; native COM/WinRT bridge is **unimplemented** (`isAvailable() = false`, returns `Unavailable`). Native implementation and MSI runtime acceptance pending Phase 09. |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `android-notification-adapter-test.kt` | Action and extra constants, intent extras assembly, capabilities data model. | **SOURCE INSPECTED** (Host SDK pending; unexecuted on Android runner) |
| `linux-notification-adapter-test.kt` | Capabilities query, missing D-Bus daemon graceful unavailability, missing session bus handling, environment probe. | **RECORDED PASSED** (Recorded in Phase 02 QA report, 4/4 tests under `--dependency-verification=off`) |
| `windows-notification-adapter-test.kt` | XML attribute/entity escaping, toast XML construction, explicit bridge unavailability stub test. | **RECORDED PASSED** (Recorded in Phase 02 code review, 3/3 tests under `--dependency-verification=off`; candidate stub only) |

### 2.12 Glean Hub Strict Backend Modules & Services (`glean-hub-sync/`)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `models/strict_upgrade.rs` | `canonical_v1_schema_json`, `canonical_v2_schema_json`, `compute_schema_fingerprint`, `MigrationState`, `StrictContractInfo`, `StrictUpgradeRequest`, `StrictUpgradeResponse`, `AppCoordinationDoc`, `PermitEntry`, `LegacyReceiptItem`, `LegacyReceiptResult` | Strict schema constants (v1 `3361a80f...`, v2 `5f539551...`), 8-stage migration state machine, and wire DTOs. |
| `services/permit_service.rs` | `PermitService`, `acquire_permit`, `release_permit`, `acquire_fence`, `release_fence`, `drain_permits` | Durable permit admission in `_coordination` (`permit:<appId>`), enforcing `MAX_CONCURRENT_PERMITS = 1000` via atomic CAS, existence-checked release, and exclusive migration fence draining. |
| `services/strict_upgrade_service.rs` | `StrictUpgradeService`, `get_contract_info`, `execute_upgrade`, `get_migration_status` | Orchestrates upgrade lifecycle: dry-run preflight, exclusive fence, permit draining, chunked in-place transformation (`BATCH_SIZE = 500`), validation, CAS schema publication with majority journaled write concern, and resumption mechanism passing prior counts (exact cumulative counts across mid-batch crash interruptions remain unverified). |
| `services/strong_sync_service.rs` | `StrongSyncService`, `legacy_receipts`, `reconcile_single_legacy_receipt` | Dual-auth legacy receipt reconciliation bridge (`<= 100` batch, strict-v1 protocol validation, v1 SHA-256 digest recomputation, durable `confirmationNonce` write without payload mutation) and permit gate integration across data routes. |
| `routes/strict_upgrades.rs` | `strict_upgrades_routes`, `get_strict_contract`, `post_strict_upgrade`, `get_strict_upgrade_status` | Axum administration router mounted under `/apps/:app_id/*` (served at `/api/v1/apps/:app_id/*`), protected by `app_scoped_admin_middleware`. |
| `middleware/dual_auth.rs` | `app_scoped_admin_middleware` | App-scoped authorization middleware requiring `is_admin: true` and `claims.apps.contains(app_id)` (HTTP 401 for non-admin, HTTP 403 for missing app scope). |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `services/strict_upgrade_tests.rs` | 11 unit/contract tests: app-scoped admin middleware authorization, non-admin 401, ungranted app 403, path parameter extraction, schema validation, error HTTP mappings, request DTO parsing, and coordination document serialization. | **RECORDED PASSED** (Recorded in Phase 03 QA report; 11/11 tests pass in `cargo test --locked`) |

---

## 3. Repomix Compaction Metrics (Dated Generation Snapshot: 2026-10-15)

- **Compaction Artifact:** Generated on 2026-10-15 at `repomix-output.xml` in repository root.
- **Total Files Packed:** 340 files
- **Total Character Count:** 2,813,952 characters
- **Total Token Count:** 656,907 tokens
- **Top 5 Files by Token Count:**
  1. `docs/codebase-summary.md` (14,962 tokens, 64,339 chars, 2.3%)
  2. `docs/project-roadmap.md` (14,266 tokens, 59,798 chars, 2.2%)
  3. `docs/project-overview-pdr.md` (11,783 tokens, 51,327 chars, 1.8%)
  4. `integration/glean-hub/strict-v2-vectors.json` (10,932 tokens, 32,971 chars, 1.7%)
  5. `docs/system-architecture.md` (9,485 tokens, 40,946 chars, 1.4%)
- **Security Check:** Repomix security scanner flagged 5 suspicious files that were excluded from the compaction output by default filter heuristics: `docs/security-and-recovery.md`, `plans/reports/code-reviewer-261014-1624-phase-08-release-operations-cycle2.md`, `plans/reports/code-reviewer-261014-1638-phase-08-release-operations-cycle3.md`, `plans/reports/docs-manager-261014-1643-phase-08-docs-verification.md`, and `shared/src/commonTest/kotlin/dev/doggo/shared/contract/endpoint-policy-test.kt`. These exclusions represent heuristic scanner flags and are not an affirmative clean security adjudication of those files.
- **Snapshot Scope:** These counts describe a single 2026-10-15 Repomix generation.
---

## 4. Current State & Invariants

1. **Deliverable Scope & Verification**:
   - **Phase 01 (Headless Tooling)**: Automation scripts, Xpra display isolation, and technical documentation completed and verified via shell suite (31/31 tests PASS).
   - **Phase 02 (Frozen Foundation & Contracts)**: Root Gradle configuration, staged assembly guards, version catalog, shared module, domain contract source files, and test suites completed and verified via shell suite (57/57 tests PASS) and `:shared:jvmTest` (24/24 contract tests PASS).
   - **Phase 03 (Hub Reuse & Strict Server)**: Backend strict synchronization server, legacy isolation fences, admission preflight, schema integration, and operator tooling implemented across 26 files (6 in Doggo, 20 in sibling `glean-hub-sync`). Unit and route tests passing (36 passed, clippy clean). Live Mongo integration suites deferred to Phase 07.
   - **Phase 04 (Hub Authentication & Protected Session)**: Authentication client, HTTP engine abstraction, session state machine, generation fencing, single-flight refresh, and Android Keystore vault implemented across 14 files. 19 unit tests passing under `:shared:jvmTest`. Android Keystore instrumented test deferred to Phase 07.
   - **Phase 05 (Durable Local State & Strict Per-Record Sync)**: Room KMP database (v1), 5 Room entities, atomic DAO transactions, BundledSQLite driver, platform account files, staged full scan, FIFO outbox reconciliation, CAS ack matching, UnknownSuperseded disambiguation, crash recovery, and fail-closed account leave protocol implemented across 29 files. 89 common and JVM unit tests pass via `./gradlew --dependency-verification=lenient :shared:jvmTest`. Android instrumented Room tests and live Hub e2e sync deferred to Phase 07.
   - **Phase 06 (Shared Compose UI & Accessible Light Theme)**: Shared Compose UI composables, responsive compact (<840dp) and expanded (>=840dp) layouts, light theme tokens with verified WCAG 2.1 AA contrast ratios, transient masked password handling, system font fallback (`FontFamily.SansSerif`), open source license viewer, and 7 unit tests in `TodoSemanticsTest` implemented across 15 files (5 conditional font/license files omitted per provenance audit). 96 :shared:jvmTest tests pass under lenient dependency verification (strict verification fails on `verification-metadata.xml`). Marked complete administratively; technical review 6.5/10 NOT ACCEPTED with documented integration gaps deferred to Phase 07 (rendered UI automation with `compose.uiTest`, `AccountIdentity.isAdmin` contract amendment, 200% font scale device verification, and string localization).
   - **Phase 07 (Android Composition & Runtime Integration)**: Native Android application module delivered in source (14 files in `androidApp/`, 2 in `scripts/`, `docs/android-acceptance.md`, `docs/evidence/android-test-summary.md`, and 7 visual design mockups in `docs/evidence/android-debug-*.png`). JVM shared regression suite 100% green (96/96 tests PASS across 14 test classes). Tooling scripts syntax verified (`bash -n scripts/android-smoke.sh`, `node --check scripts/debug-fault-proxy.mjs`). Android bytecode compilation (`:androidApp:compileDebugKotlin`) and instrumented test execution (`connectedDebugAndroidTest`) are blocked on the host by absent Android SDK (`ANDROID_HOME`), unrun on device/emulator, and deferred to Phase 08 release operations.
   - **Phase 08 (Authorized Hub Operations & Signed Android Release)**: Operational tooling, release security auditor (`scripts/release-security-check.mjs`), live strict smoke runner (`scripts/hub-live-smoke.mjs`), mongosh recovery script (`scripts/hub-strict-recovery.js`), deployment guide, security & recovery architecture, release acceptance specification, and evidence reports implemented across 20 artifacts. Tooling & harnesses operational; live bytecode compilation, binary signing verification, and live device acceptance gated on host SDK provisioning.
   - **Phase 09 (Linux Desktop & Installed RPM Distribution)**: Native Linux desktop application module (`:desktopApp`), Secret Service vault SPI (`CredentialVaultProvider`), ServiceLoader dispatcher (`PlatformVaultDispatcher`), `SingleInstanceGuard` file-channel lock, `DesktopSyncScheduler` foreground sync coordination, packaging smoke runner (`scripts/linux-package-smoke.sh`), runbooks, and test suites implemented across 23 artifacts. 19/19 unit tests PASS across 3 test classes (`PlatformVaultDispatcherTest`, `DesktopLifecycleTest`, `LinuxSecretServiceVaultTest`). Standalone application image assembled and inspected via smoke runner (`scripts/linux-package-smoke.sh --dist`); live GUI execution without host JDK, live RPM packaging, and interactive Secret Service D-Bus sessions remain pending operator prerequisites.
   - **Phase 10 (Windows Desktop & MSI Distribution)**: Windows DPAPI vault and ServiceLoader provider, per-target dependency isolation/lockfile, PowerShell bundle/MSI audit, `Doggo.exe` distributable, and unsigned MSI delivered; implementation recorded at **100% (2026-10-14)**. The evidence summary reports 25/25 desktop tests and 96/96 shared JVM tests passing (121/121 combined) and records bundle/MSI metadata checks. Technical acceptance remains blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not guaranteed by the current source, and clean-user runtime/sync plus uninstall/reinstall evidence remain open; the seven PNGs are design mockups, not live screenshots.
2. **Zero Destructive Fallback**: Room database version 1 uses explicit migrations; `fallbackToDestructiveMigration()` is strictly forbidden.
3. **Account Directory Isolation**: Account directories are keyed strictly by canonical `AccountKey` SHA-256 lowercase hex strings; canonical path validation rejects symlinks and directory traversal attempts outside the base directory.
4. **FIFO Outbox & Head Blocking**: Outbox operations dispatch at most one operation per `rowId` in FIFO order (`localRevision`). A head operation in `DISPATCHING`, `CONFLICT`, or `UNKNOWN_SUPERSEDED` state blocks all successors on that row from dispatching.
5. **Exact CAS-Ack Matching**: Push acknowledgements match on `opId`, `rowId`, `digest`, status in `[accepted, replayed]`, and `acceptedRevision == expectedRevision + 1`. Response omission or mismatch transitions the op to `UNCERTAIN` without advancing or regressing confirmed base.
6. **Newer Draft Preservation**: Acknowledging an outbox mutation deletes `editor_drafts` only if `draft.localRevision <= outbox.localRevision`; newer local edits are preserved.
7. **UnknownSuperseded Disambiguation**: A conflict returned for an operation that had previous uncertain delivery history is classified as `UNKNOWN_SUPERSEDED` rather than plain `CONFLICT`, acknowledging that the mutation may have committed before a subsequent edit bumped the revision.
8. **Crash Recovery**: Interrupted dispatches (`DISPATCHING`) are reset to `UNCERTAIN` on startup. Push coroutine cancellation uses `NonCancellable` to ensure in-flight batches are marked `UNCERTAIN`.
9. **Fail-Closed Teardown Sequence**: Teardown checks 5 categories of unresolved work, freezes the store, writes generation and `wipe.marker` outside SQLite before deleting any data, cancels background schedulers, closes database handles, deletes DB files, and clears credentials before removing the marker.
10. **WCAG 2.1 AA Palette Contrast**: Baseline mathematical pairing verified for 16 opaque token pairs (text >= 4.5:1, interactive outline borders >= 3.0:1); decorative mint is restricted to non-text accents. Rendered UI contrast across devices is deferred to Phase 07.
11. **Transient Masked Password Memory**: Password fields configure `PasswordVisualTransformation`, emit `semantics { password() }`, and dereference transient strings upon submission, mode switch, or disposal; `AuthSubmitAction.toString()` masks passwords with `[REDACTED]`. (JVM string immutability precludes guaranteed memory erasure; device screen-reader behavior unverified).
12. **Non-Color State Conveyance**: Completion, conflict, error, and leave status indicators provide explicit icons, text descriptions, and accessibility state descriptions without relying solely on color.
13. **Responsive Breakpoint Layout**: Single unified UI hierarchy adapts between a vertical compact stack (<840dp) and a fixed-sidebar desktop workspace (>=840dp). Note: compact top bar is non-scrolling in `Scaffold.topBar`, tracked for 200% font scale device testing in Phase 07.
14. **Process Container & Account Runtime Isolation**: `DoggoApplication` manages process singletons (`vault`, `accountFiles`, `hubConfig`, `authSession`, `syncScheduler`). `AccountRuntime` encapsulates `DoggoDatabase`, `RoomAccountStore`, `AccountSyncCoordinator`, and `AccountLeaveProtocol` bound to an `AccountKey` and generation counter. Foreground UI (`SessionViewModel`) and background work (`DoggoSyncWorker`) share the identical runtime instance and coordinator mutex.
15. **Cold-Start Identity Persistence**: Active account key and identity are persisted in app-private `active_account.dat` via temporary file write and `StandardCopyOption.ATOMIC_MOVE` to allow deterministic session restoration on application launch.
16. **WorkManager Account-Tag Cancellation**: All background synchronization tasks are tagged with `doggo_sync_account_<accountKey>`, allowing `cancel(accountKey)` to cancel all one-shot and periodic work across generation increments.
17. **Teardown Quiescence Mechanism**: `AndroidSyncScheduler.cancelAndAwait()` cancels WorkManager tasks by tag, polls WorkInfo completion for up to 5s (throwing `IllegalStateException` on timeout), and polls `coordinatorMutex.tryLock()` for up to 5s (throwing `IllegalStateException` on timeout) before proceeding with teardown.
18. **Fail-Closed Startup Wipe Recovery**: `completePendingWipesOnStartup()` executes synchronously in `Application.onCreate` before any account or database operations begin, cancelling scheduled work, deleting database files, clearing vault credentials, and removing `wipe.marker` fail-closed.
19. **Android Manifest Hardening & Cleartext Policy**: Base manifest enforces `allowBackup="false"` and `usesCleartextTraffic="false"`. Debug manifest permits cleartext traffic strictly for `127.0.0.1`, `localhost`, and `10.0.2.2` via `network_security_config.xml`. Merged manifest inspection across dependencies is pending AGP compilation. Release builds fail closed via task graph enforcement when signing credentials or keystores are missing.
20. **Visual Design Baselines**: The 7 PNG evidence images in `docs/evidence/` provide pixel-precise visual design specification mockups across core states (empty, populated, completed, editor, error, conflict, access-pending); live device framebuffer screen captures are pending interactive device/emulator execution in Phase 08.
21. **Phase 08 Release Operations & Disaster Recovery Tooling**: Delivered 3 operational tooling scripts (`hub-live-smoke.mjs` with `--fixture-test` self-test passing 18/18 checks, `release-security-check.mjs` with fail-closed release checking and `--diagnose-source` static validation, and `hub-strict-recovery.js` with maintenance quiescence assertion, fresh UUID epoch allocation, and journaled registry CAS), comprehensive operational documentation (`deployment-guide.md`, `security-and-recovery.md`, `release-acceptance.md`, `project-changelog.md`, `README.md`), evidence summaries (`android-release-summary.md`, `hub-recovery-summary.md`), and 7 release UI visual specification baselines in `docs/evidence/android-release-*.png`.
22. **Operational Status & Verification Boundaries**: Scripts and runbooks have been validated for syntax, fail-closed parameter validation, and deterministic offline mock fixture execution. Live release operations (production APK compilation, apksigner verification, live device installation, and live Hub staging) remain gated on host Android SDK provisioning and operator deployment. In addition, Phase 07 runtime correctness defects (teardown generation rollback, cancellation swallowing, and worker TOCTOU) and Phase 08 mongosh BSON Int64 serialization prerequisites remain documented open release-blocking items prior to production authorization.
23. **Desktop Target Isolation & Custom Dependency Locking**: Desktop build detects Linux/Windows host targets and rejects mismatches before dependency resolution. Linux D-Bus and Windows JNA dependencies are target-specific; `gradle-linux.lockfile` and `gradle-windows.lockfile` preserve separate resolution graphs. Package inspection reports each target contains its platform provider and excludes the foreign provider/runtime classes.
24. **ServiceLoader SPI Provider Resolution**: Hardware/OS vaults are decoupled behind the `CredentialVaultProvider` SPI; `PlatformVaultDispatcher` requires exactly one provider matching the active desktop target and fails closed with an explicit exception if zero or duplicate providers exist. Plaintext token file fallback is strictly forbidden.
25. **Single-Instance Profile File Lock**: `SingleInstanceGuard` acquires an exclusive OS file-channel lock on `~/.local/share/doggo/accounts/doggo.lock` before initializing database or vault; prevents concurrent launch, refresh races, and database corruption. The lock file is retained on exit to prevent inode recycling race conditions.
26. **Foreground Desktop Sync Quiescence**: `DesktopSyncScheduler` coordinates periodic 15-minute and immediate sync within the window coroutine scope; implements `LeaveSchedulerCancelGate` to cancel and join active sync jobs before account leave or wipe.
27. **Immutable Build Modes & Launchers**: `MainKt` enforces `BuildMode.RELEASE` (HTTPS only, loopback denied); `DevelopmentLauncher` enables `BuildMode.DEBUG` (HTTP 127.0.0.1:3000 permitted) via `:desktopApp:runDebug`.
28. **Standalone Distributable & Disclosed Packaging Gates**: Standalone unpacked application image assembled and statically inspected (`scripts/linux-package-smoke.sh --dist`); live GUI execution without host JDK, native RPM packaging (`:desktopApp:packageRpm`), system installation, and live Secret Service D-Bus sessions remain pending host `rpm-build` installation, interactive sudo, and an active desktop session. Visual evidence images in `docs/evidence/linux-*.png` represent high-fidelity design specifications.
29. **Windows User-Scoped Credential Custody**: `WindowsDpapiVault` uses JNA `CryptProtectData` / `CryptUnprotectData` with UI prompts forbidden and current-user scope; machine-wide protection and plaintext token fallback are not used.
30. **Windows Credential File Replacement**: DPAPI-protected JSON credential pairs are written to a temporary file and atomically moved into place; plaintext byte buffers are cleared after cryptographic operations. The implementation attempts an owner ACL but suppresses ACL-view/owner errors, so ACL enforcement is not guaranteed when unavailable or failed.
31. **Windows MSI Provenance**: The recorded MSI is unsigned; its hash/metadata inspection does not establish Authenticode trust or successful interactive installation.
32. **Windows Acceptance Status**: Phase 10 implementation is recorded at 100% (2026-10-14), but technical acceptance is blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not fail-closed and clean-user runtime/sync plus uninstall/reinstall evidence remain open. The seven Windows PNGs are design mockups; they are not live application captures.
33. **Phase 01 Temporal Model & Strict Transition (Delivered Through Phase 05)**: Contract, binary digest framing, and transition design frozen on 2026-10-15 (Gate G1 approved); Room v1->v2 evolution delivered in Phase 05 defaulting migrated records to null deadlines and disabled rules. Strict-v2 wire format introduces 12 fields (16 total columns) with canonical JSON schema fingerprints `3361a80f...` (v1) and `5f539551...` (v2). Binary SHA-256 preimages and checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) pass across recorded test suites in Kotlin (:shared:jvmTest), Rust (`strong_sync_v2_tests`), and Node.js (`strict-v2-vector-runner.mjs`). Wire validation rejects non-null interval when `reminderCount == 1` and requires interval >= 15m for repeating count 2..10. Subsequent client sync transition in Phase 06 will address unprovable legacy outbox operations (`UNKNOWN_SUPERSEDED`), intent-masked safe rebasing over remote v2 base, and non-destructive legacy reconciliation.
34. **Strict DST Gap and Overlap Resolution**: `StrictTimeZoneResolver` rejects non-existent spring-forward gap instants (`IllegalArgumentException`), requires explicit offset disambiguation for fall-back overlap hours, and calculates calendar day boundaries with calendar arithmetic (23h/25h), never fixed +24h additions. (Reported 7/7 tests passing in `:shared:jvmTest` under `--dependency-verification=off`).
35. **Non-Exported Android Alarm Receiver**: `DoggoAlarmReceiver` declares `android:exported="false"` in `AndroidManifest.xml` and validates `taskId`, `scheduleToken`, `notificationId`, and `title` before dispatching offline notifications via `AndroidNotificationAdapter`.
36. **Decoupled Mobile Reminder Capabilities**: `AndroidReminderCapabilitiesChecker` decouples runtime `POST_NOTIFICATIONS` permission from `SCHEDULE_EXACT_ALARM` exact alarm permission (`AlarmManager.canScheduleExactAlarms()` on API 31+).
37. **Safe Desktop Notification Absence & Windows Stub**: `DbusLinuxNotificationAdapter` maps `DBusExecutionException` / `ServiceUnknown` to `LinuxNotificationStatus.Unavailable` without crashing; `WindowsToastBridge` explicitly returns `isAvailable() = false` and `WindowsNotificationStatus.Unavailable` pending native WinRT/COM implementation and Windows testbed in Phase 09.
38. **Bounded Coordination Permits & Atomic CAS Gate**: Strict sync data operations acquire an identifiable permit in `_coordination` (`permit:<appId>`), enforcing a strict 1,000 active permit ceiling (`MAX_CONCURRENT_PERMITS`) via atomic CAS update checking `fenced: false` and `permitCount: { $lt: 1000 }` with `$inc: { permitCount: 1 }`. Release operations verify the permit ID exists in `activePermits` before decrementing.
39. **In-Place Strict Migration State Machine & Resumption**: Strict upgrade transitions through an 8-state machine (`ready-v1` -> `fencing` -> `transforming` -> `validating` -> `publishing` -> `ready-v2`, `operatorRequired`, or `rolledBack`) under an exclusive coordination fence. Resuming interrupted migrations skips the initial purity preflight, passes prior recorded counts, and updates `_schemas` via CAS with majority journaled write concern; exact cumulative accounting across mid-batch crash interruptions remains an open correctness item awaiting formal revalidation.
40. **Strict-v1 Protocol Validation on Legacy Receipts**: Legacy receipt reconciliation (`POST /api/v1/sync/:app_id/strong/legacy-receipts`) enforces bounded batches (`<= 100`) and strictly validates `request.protocol == "strict-v1"`. Matching receipts execute durable replay confirmation nonces without mutating payload records or applying legacy mutations.
41. **Phase 03 Hub Versioned Admission & Staging Gate Status**: Phase 03 backend contracts, admission guards, and in-place migration engine are delivered in `glean-hub-sync` with passing unit/contract checks. Production admission remains on hold: (1) Gate G3 live multi-instance Mongo replica-set acceptance is unexecuted pending staging testbed, (2) coordination-document compatibility/backfill for documents missing `permitCount` requires live verification, and (3) exact cumulative accounting across mid-batch crash interruption requires formal revalidation.
42. **Phase 04 Hub Admin UI and API-Only Upgrade Tooling**: Operator tooling is delivered across both Glean Hub Web Admin UI (`glean-hub-app`) and Doggo CLI runner (`scripts/hub-strict-upgrade.mjs`). The CLI runner enforces precondition verification, preflight dry-run checks, same-migration-ID resumption upon network interruption, fail-fast on 4xx rejections, complete token redaction, bounded polling, and mandatory final read-back assertions against `GET /strict-contract` verifying `strict-v2` mode, canonical SHA-256 fingerprint (`5f539551...`), and 16 table columns. The Web Admin UI guards strict apps against arbitrary schema PUT requests, checks app-scoped authorization, persists active migration IDs in `sessionStorage`, and asserts final schema read-back. Tooling is verified via unit test suites (`hub-strict-upgrade.test.mjs` 7/7 pass, `glean-hub-app` 13 unit tests across 2 suites pass: 8 in `sync-admin-service.test.ts` and 5 in preload recovery, `pnpm build` clean); production admission remains strictly on hold pending Gate G3 live multi-node replica-set staging validation and remediation of Phase 03 backend findings (`permitCount` coordination backfill, mid-batch crash resumption consistency, and duplicate permit release filtering).

43. **Phase 05 Shared Task Model & Room v2 Migration (Gate G4 Storage Migration Delivered)**: Delivered on 2026-10-15. Room database version 2 transitions from schema 1 via explicit step-by-step migration `DatabaseMigrations.MIGRATION_1_2` without destructive fallback, registered on JVM and Android builders via `DatabaseMigrations.ALL`. 7 entities: `server_base`, `outbox`, `editor_drafts`, and `scan_staging` expanded with 12 temporal columns; `outbox` augmented with `contractVersion`, `dispatchHistory`, `intentMask`, and `transitionCheckpoint`; plus new entities `AccountLocalSettingsEntity` (singleton local reminder/notification defaults) and `ReminderDeliveryLedgerEntity` (5-tuple PK for durable native reminder claims and cancellation). Shared models `TaskDeadline`, `ReminderRule`, and `Todo` enforce schedule token presence and checked 64-bit arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) guaranteeing `last <= anchor`. Verified via 128 JVM unit/integration tests (`:shared:jvmTest`), 23 strict-v2 vector checks, 18 offline smoke-fixture self-checks (`hub-live-smoke.mjs --fixture-test`), and 6 static source security diagnostics (`release-security-check.mjs --diagnose-source`), with 0 failures in single test execution. Client transition and replay logic delivered in Phase 06 with reported JVM/offline validation; Android instrumented execution (`:shared:connectedAndroidTest`) and live mixed-version acceptance remain pending Phases 08 and 11, and production admission remains on hold.

44. **Phase 06 Client Version Transition, Safe Replay & Intention Preservation (Gate G4 Logic Delivered)**: Delivered on 2026-10-15. `ContractTransitionCoordinator` implements an orderly, crash-resilient 7-state / 6-transition state machine (`ready-v1` -> `upgrade-detected` -> `dispatch-quiesced` -> `legacy-reconciling` -> `v2-base-scanning` -> `safe-intents-translating` -> `ready-v2`) with durable SQLite checkpoints in `AccountSyncMetaEntity.syncStatus` (`"TRANSITION:<phase>"`) and `safeErrorCode = "CHECKPOINT_<PHASE>"`. Key delivered capabilities: exact legacy receipt reconciliation via `POST /api/v1/sync/{appId}/strong/legacy-receipts` under `RouteClassification.DUAL_AUTH_STRICT`, bounded batches (`<= 100`), receipt omission defaulting to `UNCERTAIN` (never assumed non-delivery or auto-replayed); non-destructive intent-masked rebasing in `RoomAccountStore.reconcileAccepted()` preserving remote temporal metadata from `currentBase` on legacy or partial edits; atomic transactional cleanup on regressive server revision via `DoggoDao.discardRegressiveReconcileAtomic()`; migration-mode equal-revision temporal enrichment in `StagedFullScan` (`isMigration = true`); transactional safe intent translation of provably unsent legacy v1 operations with conservative review gating (`INTENT_REQUIRES_REVIEW` failing closed to `STATE_CONFLICT`); cross-version server regression prevention (`countOutboxByVersion`); and WCAG 2.1 AA accessible conflict warnings in `ConflictReviewDialog` (`isUnknownSuperseded` coral banner) and `VersionCard`. Total 243 executed checks passing with 0 failures (reported in `plans/reports/qa-261015-1815-phase-06-client-transition-and-replay.md`, executed with `--dependency-verification=off`) across shared JVM (155 tests across 20 classes), desktop (32 tests across 6 classes), strict-v2 vectors (23/23), live smoke fixture (18/18), security diagnostics (6/6), and upgrade CLI (9/9). Code review completed with 0 critical/high issues (`plans/reports/code-review-261015-1830-phase-06-client-transition-and-replay.md`). Gate G4 offline-client upgrade and replay logic are delivered; connected Android instrumented execution (`:shared:connectedAndroidTest`), connected platform notification delivery, and live mixed-version multi-device cutover remain scheduled for Phase 08–11.