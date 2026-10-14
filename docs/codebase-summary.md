# Codebase Summary

Generated on: 2026-10-14
Based on Repomix compaction snapshot: `repomix-output.xml` (268 files, 498,743 tokens, 2,148,581 chars, dated 2026-10-14)
---

## 1. Repository Overview

The `doggo` repository contains the implementation, build configuration, domain contracts, and architectural specifications for Doggo, an Android-first shared Compose Multiplatform task management application with secure multi-device synchronization against Glean Hub.

Following Phase 01 (Headless Tooling), Phase 02 (Frozen Foundation & Contracts), Phase 03 (Isolated Strict Server & Legacy Isolation), Phase 04 (Hub Authentication & Protected Session), Phase 05 (Durable Local State & Strict Per-Record Sync), Phase 06 (Shared Compose UI & Accessible Light Theme), Phase 07 (Android Composition & Runtime Integration), Phase 08 (Authorized Hub Operations & Signed Android Release), and Phase 09 (Linux Desktop & Installed RPM Distribution), the repository contains:
1. **Root Build & Version Catalog**: Staged Gradle build system, official SHA-256 pinned wrapper, and dependency catalog.
2. **Shared Kotlin Multiplatform Module (`shared/`)**: Domain models, UI state/actions, identity/authentication contracts, strict sync canonical framing, validation policies, Ktor HTTP transport, Hub auth client, auth session state machine, single-flight refresh coordinator, Android Keystore vault, Room KMP database (version 1), BundledSQLiteDriver, local outbox mutation engine, staged full scan reconciler, fail-closed account leave protocol, shared Compose UI components (app shell, responsive layout, account form, access request panel, todo list/editor, sync feedback, conflict review, leave guard, license notices), accessible light theme tokens (WCAG 2.1 AA verified), and comprehensive unit/instrumented/semantics tests.
3. **Android Application Module (`androidApp/`)**: Native Android client hosting shared Compose UI, `DoggoApplication` process container, `SessionViewModel` reactive UI state orchestration, `AndroidSyncScheduler` WorkManager scheduling, `DoggoSyncWorker` background sync worker with generation/auth fencing, debug network security configuration with strict loopback cleartext whitelist, backup/extraction security exclusions, release signing task-graph enforcement, and authored instrumented test suites.
4. **Desktop Application Module (`desktopApp/`)**: Compose Multiplatform desktop target on JVM 21, hosting shared Compose UI, `MainKt` (RELEASE) and `DevelopmentLauncher` (DEBUG) entrypoints, `CredentialVaultProvider` SPI, `PlatformVaultDispatcher` ServiceLoader resolution, `SingleInstanceGuard` file-channel lock, `DesktopSyncScheduler` foreground sync coordination with `LeaveSchedulerCancelGate`, and Linux Secret Service vault implementation (`LinuxSecretServiceVault`).
5. **Host Virtualization, Security & Operations Tooling (`scripts/`)**: Headless Xpra display orchestration, Android SDK/JDK environment configuration, emulator management, Hub schema provisioning (`hub-schema-provision.mjs`), live Hub strict smoke harness (`hub-live-smoke.mjs`), release security and integrity auditor (`release-security-check.mjs`), maintenance recovery script (`hub-strict-recovery.js`), device smoke runner (`android-smoke.sh`), loopback sync fault-injection proxy (`debug-fault-proxy.mjs`), and Linux packaging smoke runner (`linux-package-smoke.sh`).
6. **Hub Integration Artifacts (`integration/`)**: Canonical strict-v1 JSON schema specification matching backend admission rules.
7. **Automated Verification Harness (`tests/`)**: Automated shell suites validating scripts, Gradle contracts, Kotlin sources, and cryptographic digest vectors.
8. **Technical Documentation & Evidence (`docs/`)**: Architecture specifications, API contracts, deployment and disaster recovery guides, release acceptance specifications, toolchain evidence, operational runbooks, compatibility criteria, auth/credential specifications, local storage and sync specifications, design guidelines, font provenance, coding standards, Android runtime acceptance criteria, Linux desktop guide and acceptance specifications, and UI design mockups.
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
│       │   │   └── doggo-sync-worker.kt    # Background sync worker with wipe, auth, account, and generation fencing
│       │   └── res/xml/
│       │       ├── backup_rules.xml        # API <= 30 backup exclusions (databases, sharedprefs, account files)
│       │       └── data_extraction_rules.xml # API >= 31 cloud and device transfer exclusions
│       ├── debug/
│       │   ├── AndroidManifest.xml     # Debug manifest overriding network security config
│       │   └── res/xml/
│       │       └── network_security_config.xml # Debug cleartext whitelist (127.0.0.1, localhost, 10.0.2.2)
│       └── androidTest/kotlin/dev/doggo/android/
│           ├── todo-device-test.kt     # Authored instrumented device tests for ViewModel and store (unexecuted)
│           └── background-isolation-test.kt # Authored instrumented tests for WorkManager and isolation (unexecuted)
├── desktopApp/                         # Desktop application module (Kotlin JVM 2.1.10, Compose 1.7.3)
│   ├── build.gradle.kts                # Desktop build script, target detection (linux/windows), RPM/MSI config
│   ├── gradle-linux.lockfile           # Dependency lockfile for Linux target (161 locked artifacts)
│   └── src/
│       ├── main/kotlin/dev/doggo/desktop/
│       │   ├── main.kt                 # MainKt (RELEASE) and DevelopmentLauncher (DEBUG), window scaffold
│       │   ├── credential-vault-provider.kt # CredentialVaultProvider SPI
│       │   ├── platform-vault-dispatcher.kt # ServiceLoader vault dispatcher and target validation
│       │   ├── desktop-sync-scheduler.kt # Foreground sync scheduler and LeaveSchedulerCancelGate
│       │   └── single-instance-guard.kt # File-channel lock on doggo.lock
│       ├── linuxMain/
│       │   ├── kotlin/dev/doggo/desktop/linux/
│       │   │   └── linux-secret-service-vault.kt # Freedesktop Secret Service vault implementation
│       │   └── resources/META-INF/services/
│       │       └── dev.doggo.desktop.CredentialVaultProvider # ServiceLoader descriptor for Linux provider
│       ├── test/kotlin/dev/doggo/desktop/
│       │   ├── platform-vault-dispatcher-test.kt # SPI resolution, zero/duplicate/target validation tests
│       │   └── desktop-lifecycle-test.kt # Single instance lock, sync scheduling, and build mode tests
│       └── linuxTest/kotlin/dev/doggo/desktop/linux/
│           └── linux-secret-service-vault-test.kt # Secret Service status, store, replace, clear tests
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
│   │       └── 1.json                  # Canonical Room schema version 1 export
│   └── src/
│       ├── commonMain/composeResources/
│       │   └── values/
│       │       └── strings.xml             # UI string resource catalog
│       ├── commonMain/kotlin/dev/doggo/shared/
│       │   ├── contract/               # Pure domain contracts & validation policies
│       │   ├── auth/                   # Hub authentication, HTTP client, and session state machine
│       │   ├── data/                   # Room entities, DAO, Database, and RoomAccountStore
│       │   │   ├── server-base-entity.kt       # Confirmed server base and permanent compact tombstones
│       │   │   ├── outbox-entity.kt            # Frozen strict mutations and delivery states
│       │   │   ├── editor-draft-entity.kt      # Uncommitted local user edits
│       │   │   ├── scan-staging-entity.kt      # Cursor pagination staging table
│       │   │   ├── account-sync-meta-entity.kt # Account sync metadata and active epoch
│       │   │   ├── doggo-dao.kt                # Atomic @Transaction DAO methods and queries
│       │   │   ├── doggo-database.kt           # RoomDatabase definition (version 1)
│       │   │   ├── database-migrations.kt      # Migration registry (empty for v1)
│       │   │   └── room-account-store.kt       # Mutex-guarded AccountStore with SQLite persistence
│       │   └── sync/                   # Strict synchronization protocol orchestration
│       │       ├── hub-sync-dtos.kt            # Wire DTOs for bootstrap, push, and pull
│       │       ├── hub-sync-client.kt          # HTTP transport adapter for sync routes
│       │       ├── staged-full-scan.kt         # Monotonic staging, pagination loop & consistency checks
│       │       ├── outbox-reconciliation.kt    # FIFO dispatch, CAS ack matching, and UnknownSuperseded
│       │       ├── account-sync-coordinator.kt # 3-stage sync round, conflict actions, generation fencing
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
│       │   └── data/
│       │       ├── android-database-builder.kt # BundledSQLiteDriver Room database builder
│       │       └── android-account-files.kt    # Account directory partitioning and wipe markers
│       ├── jvmMain/kotlin/dev/doggo/shared/
│       │   ├── auth/                   # JVM Java HTTP engine
│       │   └── data/
│       │       ├── jvm-database-builder.kt     # Desktop BundledSQLiteDriver Room database builder
│       │       └── jvm-account-files.kt        # Desktop account directory partitioning and wipe markers
│       ├── commonTest/kotlin/dev/doggo/shared/
│       │   ├── contract/               # Contract unit tests (digest, policy, keys)
│       │   ├── auth/                   # Auth client, session, and refresh tests
│       │   └── sync/                   # Phase 05 sync test suites
│       │       ├── hub-sync-client-test.kt     # Push, pull, bootstrap, and batch bounds tests
│       │       ├── staged-full-scan-test.kt    # Pagination, loops, overflow, consistency tests
│       │       ├── outbox-reconciliation-test.kt # CAS acks, FIFO, UnknownSuperseded, retries
│       │       ├── account-sync-coordinator-test.kt # Sync loop, conflict actions, cancellation
│       │       └── account-leave-protocol-test.kt # Review guards, teardown order, wipe recovery
│       │   └── ui/                     # Phase 06 UI semantics & theme test suites
│       │       └── todo-semantics-test.kt      # Contrast calculations, password redaction, UI invariants
│       ├── jvmTest/kotlin/dev/doggo/shared/data/
│       │   └── room-account-store-test.kt      # BundledSQLite driver temporary database tests
│       └── androidInstrumentedTest/kotlin/dev/doggo/shared/
│           ├── auth/                   # Keystore vault instrumented tests
│           └── data/
│               └── room-account-store-test.kt  # Android Room instrumented tests
├── integration/                         # Integration schemas and specifications
│   └── glean-hub/
│       └── doggo-schema.json             # Exact strict-v1 todos schema specification
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
│   └── linux-package-smoke.sh          # Linux desktop package and distributable smoke verification runner
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
    ├── deployment-guide.md             # Phase 08 operational deployment guide
    ├── linux-acceptance.md             # Phase 09 Linux desktop and RPM acceptance specification
    ├── linux-desktop-guide.md          # Phase 09 Linux desktop operations and build guide
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
        └── linux-*.png                 # 7 visual design mockups for Linux desktop screen states
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
| `doggo-dao.kt` | `DoggoDao` | Room DAO with atomic `@Transaction` methods: `saveTodoAtomic`, `deleteTodoAtomic`, `cancelUnsentCreateAtomic`, `reconcileAcceptedAtomic`, `promoteStagingAtomic`, `replaceOutboxAtomic`, `discardConflictAtomic`, `freezeOutboxAtomic`. |
| `doggo-database.kt` | `DoggoDatabase` | Room database definition (version 1) backed by `BundledSQLiteDriver`. |
| `room-account-store.kt` | `RoomAccountStore` | Implementation of `AccountStore` with mutex-serialized edits, outbox coalescing, unsent cancellation, and dispatch recovery. |
| `android-database-builder.kt` | `createAndroidDoggoDatabase()` | Platform Room database builder for Android with `BundledSQLiteDriver`. |
| `jvm-database-builder.kt` | `createJvmDoggoDatabase()` | Platform Room database builder for Desktop JVM with `BundledSQLiteDriver`. |
| `android-account-files.kt` | `AndroidAccountFiles` | Android app-private storage partitioner by `AccountKey` SHA-256, path traversal guard, and `wipe.marker` manager. |
| `jvm-account-files.kt` | `JvmAccountFiles` | Desktop storage partitioner (XDG / AppData / Library) by `AccountKey` SHA-256, path traversal guard, and `wipe.marker` manager. |
| `hub-sync-dtos.kt` | `StrictBootstrapResponseDto`, `StrictPushRequestDto`, `StrictPushResponseDto`, `StrictPullResponseDto` | Wire DTOs for strict synchronization endpoints. |
| `hub-sync-client.kt` | `HubSyncClient` | Sync transport adapter enforcing batch size `<= 100`, payload size `<= 256 KiB`, and `SafeError` translation. |
| `staged-full-scan.kt` | `StagedFullScan` | 3-stage scan reconciler: cursor pagination, loop/overflow/non-progress detection, pre-scan identity check (`CONSISTENCY_ERROR`), and monotonic promotion. |
| `outbox-reconciliation.kt` | `OutboxReconciliation` | FIFO row-head selection, head blocking on conflicts, dispatch freezing via `freezeOutboxAtomic`, exact CAS ack verification, and `UNKNOWN_SUPERSEDED` classification. |
| `account-sync-coordinator.kt` | `AccountSyncCoordinator` | 3-stage sync coordinator (`SyncCoordinator`), mutex serialization, generation fencing, conflict actions (`discardConflict`, `reapplyConflict`, `copyAsNew`), and cancellation safety. |
| `account-leave-protocol.kt` | `AccountLeaveProtocol` | 5-category review work guard, store freezing, 7-step fail-closed teardown sequence, marker rollback, and startup `completePendingWipe`. |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `hub-sync-client-test.kt` | Push, pull, bootstrap, batch size <= 100, payload <= 256 KiB, HTTP error envelopes. | **PASSED** (JVM) |
| `staged-full-scan-test.kt` | Cursor loops, batch overflow, non-progressing cursors, missing pre-scan identity check, monotonic promotion, divergent revisions. | **PASSED** (JVM) |
| `outbox-reconciliation-test.kt` | FIFO row-head dispatch, head blocking on conflicts, dispatch freezing, exact CAS ack matching, newer draft preservation, UnknownSuperseded, retries. | **PASSED** (JVM) |
| `account-sync-coordinator-test.kt` | 3-stage sync loop, mutex serialization, generation fencing, conflict workflows, push cancellation safety. | **PASSED** (JVM) |
| `account-leave-protocol-test.kt` | 5-category review guard, freeze-before-review, marker write failure abort, scheduler cancel rollback, database deletion fail-closed, completePendingWipe. | **PASSED** (JVM) |
| `room-account-store-test.kt` (JVM) | Real SQLite temporary database: schema v1, atomic saves, unsent create-delete cancel, FIFO outbox, monotonic base advancement, crash recovery, wipe. | **PASSED** (JVM) |
| `room-account-store-test.kt` (Android) | Android instrumented Room tests with app-private storage and Keystore integration. | **DEFERRED** (Phase 07) |

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

### 2.6 Glean Hub Integration Artifacts & Host Scripts

- `integration/glean-hub/doggo-schema.json`: Canonical strict-v1 todos schema specification.
- `scripts/`: Headless Xpra display orchestration (`start-xpra.sh`), Android SDK/JDK environment configuration (`android-sdk-env.sh`), emulator management (`start-android-emulator.sh`), Hub provisioning/smoke runners (`hub-schema-provision.mjs`, `hub-compatibility-smoke.mjs`), device smoke runner (`android-smoke.sh`), and loopback sync fault-injection proxy (`debug-fault-proxy.mjs`).

### 2.7 Native Android Application Module (Phase 07)

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


### 2.8 Phase 08 Release Operations, Security Auditing & Recovery Tooling (`scripts/`)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `hub-live-smoke.mjs` | `runLiveSmokeSuite()`, local fixture server | Automated strict-v1 integration smoke runner verifying health, admission read-back, auth session, structured push/pull with specific rowId assertions, base conflict, frozen replay, epochMismatch, and permanent empty tombstones. Features standalone `--fixture-test` offline mode (18/18 checks pass). |
| `release-security-check.mjs` | `runReleaseSecurityAudit()`, `diagnoseSource()` | Fail-closed security auditor inspecting release APK signatures (v2/v3), explicit `allowBackup="false"`, `debuggable="false"`, `usesCleartextTraffic="false"`, non-main exported components rejection, networkSecurityConfig stripping, and static source secret scanning. |
| `hub-strict-recovery.js` | `executeRecovery()`, `validateTodoRecord()` | Maintenance disaster recovery script for `mongosh` requiring `CONFIRM_MAINTENANCE_QUIESCENCE=true`, generating fresh UUID v4 epoch, provisioning canonical `_strict_v1_...` collection, resetting records to revision 1 with `lastOp = null`, maintaining permanent empty tombstones, and executing journaled Compare-And-Swap on `_schemas`. |

### 2.9 Linux Desktop Application Module & Packaging Tooling (Phase 09)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `build.gradle.kts` | `:desktopApp` build script | Configures Compose Multiplatform 1.7.3 on Kotlin JVM 2.1.10; detects host OS (`linux` vs `windows`); enforces target isolation; binds target-specific dependencies (`libs.dbus.java` on Linux); configures native RPM/MSI distributions; pins lockfile to `gradle-$desktopTarget.lockfile`; registers `runDebug` task. |
| `gradle-linux.lockfile` | Dependency locking definition | Pinned dependency locks for Linux x86_64 desktop configuration (161 locked artifacts). |
| `credential-vault-provider.kt` | `CredentialVaultProvider` | Hardware/OS credential vault Service Provider Interface (SPI) contract for desktop platforms. |
| `platform-vault-dispatcher.kt` | `PlatformVaultDispatcher` | Dispatches and resolves `CredentialVaultProvider` via Java `ServiceLoader`; requires exactly one provider matching the active desktop target; fails closed if zero or duplicate providers exist; zero plaintext fallback. |
| `single-instance-guard.kt` | `SingleInstanceGuard` | Acquires exclusive OS file-channel lock on `~/.local/share/doggo/accounts/doggo.lock` before vault or database initialization; prevents concurrent launch, refresh races, and database corruption; retains lock file across exits. |
| `desktop-sync-scheduler.kt` | `DesktopSyncScheduler` | Implements `SyncScheduler` and `LeaveSchedulerCancelGate` for window-scoped lifecycle; schedules periodic 15-minute and immediate sync rounds; `cancelAndAwait()` guarantees active sync jobs are quiesced before account leave or wipe. |
| `main.kt` | `MainKt`, `DevelopmentLauncher`, `DesktopController`, `DesktopAccountRuntime` | Application entrypoint and Compose window container; `MainKt` enforces `BuildMode.RELEASE` (HTTPS only); `DevelopmentLauncher` enables `BuildMode.DEBUG` (HTTP loopback permitted) via `:desktopApp:runDebug`; coordinates account lifecycle, local database, and auth session. |
| `linux-secret-service-vault.kt` | `LinuxSecretServiceVault`, `LinuxSecretServiceVaultProvider`, `SecretServiceClient` | Freedesktop Secret Service client and SPI provider; stores access and refresh tokens as an atomic JSON bundle via `secret-tool` / D-Bus; reports `READY`, `LOCKED`, `UNAVAILABLE` status; fails closed without plaintext fallback. |
| `dev.doggo.desktop.CredentialVaultProvider` | ServiceLoader configuration | Service provider configuration file registering `dev.doggo.desktop.linux.LinuxSecretServiceVaultProvider`. |
| `scripts/linux-package-smoke.sh` | Shell smoke script | Packaging verification runner providing host diagnostics (`--diagnose`), unpacked distributable verification (`--dist`), and RPM query/dependency audit (`--rpm`). |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `platform-vault-dispatcher-test.kt` | Host detection, ServiceLoader provider resolution, zero-match fail-closed, multiple-match fail-closed. | **PASSED** (JVM, 5/5 tests) |
| `desktop-lifecycle-test.kt` | SingleInstanceGuard exclusive lock acquisition, lock retention, periodic and immediate sync scheduling, cancelAndAwait quiescence, RELEASE vs DEBUG build mode origin policies. | **PASSED** (JVM, 8/8 tests) |
| `linux-secret-service-vault-test.kt` | Secret Service status transitions (READY/LOCKED/UNAVAILABLE), atomic store and load, atomic replacement, corrupt payload handling, clear credentials. | **PASSED** (JVM, 6/6 tests) |
---

## 3. Repomix Compaction Metrics (Dated Generation Snapshot: 2026-10-14)

- **Total Files Packed:** 268 files
- **Total Character Count:** 2,148,581 characters
- **Total Token Count:** 498,743 tokens
- **Security Check:** Repomix security scanner flagged 5 suspicious files that were excluded from the compaction output by default filter heuristics: `docs/security-and-recovery.md`, `plans/reports/code-reviewer-261014-1624-phase-08-release-operations-cycle2.md`, `plans/reports/code-reviewer-261014-1638-phase-08-release-operations-cycle3.md`, `plans/reports/docs-manager-261014-1643-phase-08-docs-verification.md`, and `shared/src/commonTest/kotlin/dev/doggo/shared/contract/endpoint-policy-test.kt`. These exclusions represent heuristic scanner flags and are not an affirmative clean security adjudication of those files.
- **Top Files by Token Footprint:** `docs/codebase-summary.md` (12,077 tokens), `docs/project-roadmap.md` (10,002 tokens), `docs/project-overview-pdr.md` (9,454 tokens), `scripts/hub-live-smoke.mjs` (7,610 tokens), `plans/261013-1540-doggo-glean-hub-reuse/file-ownership.md` (7,367 tokens).
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
23. **Desktop Target Isolation & Custom Dependency Locking**: Desktop build detects host OS (`linux` vs `windows`), rejects target mismatches prior to dependency resolution, binds target-specific dependencies (`libs.dbus.java` on Linux), and pins locked dependencies to custom lockfiles (`desktopApp/gradle-linux.lockfile`, 161 locked artifacts). Packaged binaries contain only Linux provider classes with zero Windows references.
24. **ServiceLoader SPI Provider Resolution**: Hardware/OS vaults are decoupled behind the `CredentialVaultProvider` SPI; `PlatformVaultDispatcher` requires exactly one provider matching the active desktop target and fails closed with an explicit exception if zero or duplicate providers exist. Plaintext token file fallback is strictly forbidden.
25. **Single-Instance Profile File Lock**: `SingleInstanceGuard` acquires an exclusive OS file-channel lock on `~/.local/share/doggo/accounts/doggo.lock` before initializing database or vault; prevents concurrent launch, refresh races, and database corruption. The lock file is retained on exit to prevent inode recycling race conditions.
26. **Foreground Desktop Sync Quiescence**: `DesktopSyncScheduler` coordinates periodic 15-minute and immediate sync within the window coroutine scope; implements `LeaveSchedulerCancelGate` to cancel and join active sync jobs before account leave or wipe.
27. **Immutable Build Modes & Launchers**: `MainKt` enforces `BuildMode.RELEASE` (HTTPS only, loopback denied); `DevelopmentLauncher` enables `BuildMode.DEBUG` (HTTP 127.0.0.1:3000 permitted) via `:desktopApp:runDebug`.
28. **Standalone Distributable & Disclosed Packaging Gates**: Standalone unpacked application image assembled and statically inspected (`scripts/linux-package-smoke.sh --dist`); live GUI execution without host JDK, native RPM packaging (`:desktopApp:packageRpm`), system installation, and live Secret Service D-Bus sessions remain pending host `rpm-build` installation, interactive sudo, and an active desktop session. Visual evidence images in `docs/evidence/linux-*.png` represent high-fidelity design specifications.
