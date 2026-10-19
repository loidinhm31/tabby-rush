# Codebase Summary

Generated on: 2026-10-19
Application Version: `1.0.17` (versionCode `18`)
Based on Repomix compaction snapshot: `repomix-output.xml` (repository snapshot: 610 files packed; 6 security-scanner exclusions: `docs/security-and-recovery.md`, 4 historical reports, and `endpoint-policy-test.kt`; default broad scan, not an allowlist)
---

## 1. Repository Overview

The `tabbyrush` repository contains the implementation, build configuration, domain contracts, and architectural specifications for TabbyRush, an Android-first shared Compose Multiplatform task management application with secure multi-device synchronization against Glean Hub.

Following Phases 01–12, the repository contains the Android and shared Compose Multiplatform clients, Linux desktop source/distribution tooling, Windows desktop DPAPI provider, comprehensive adversarial integration and SQLite crash invariance test harnesses, and authoritative strict-v2 cutover runbook and rehearsal tooling:
1. **Root Build & Version Catalog**: Staged Gradle build system, official SHA-256 pinned wrapper, and dependency catalog.
2. **Shared Kotlin Multiplatform Module (`shared/`)**: Domain models (`TaskDeadline`, `ReminderRule`, `Todo`), UI state/actions (`EditorState`, `ReminderSettingsState`, `TodoUiState`, `TodoAction`, `ClockTick`), identity/authentication contracts, strict sync canonical framing (`strict-v1` and `strict-v2`), `StrictV2Constants`, `StrictV2TodoData`, `StrictV2SyncOp`, `LegacyReceiptStatus`, `LegacyReceiptResult`, checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`), `StrictSyncV2Digest` binary preimage engine, `StrictTimeZoneResolver` and `StrictZoneOffsetResult` (`Single`, `Overlap`, `Gap`) with `ZoneRules` platform actuals, validation policies, Ktor HTTP transport, Hub auth client, auth session state machine, single-flight refresh coordinator, Android Keystore vault, Room KMP database (version 2, 7 entities, `DatabaseMigrations.MIGRATION_1_2`), BundledSQLiteDriver, local outbox mutation engine with intent masks and provenance tracking, staged full scan reconciler, fail-closed account leave protocol, `RoomAccountStore` authoritative temporal snapshot projection hierarchy (preventing cleared deadline resurrection) and schedule-token rotation on semantic temporal changes, shared Compose UI components (`DeadlineEditorSection`, `DeadlineCalculatorHelpers`, `ReminderSettingsDialog`, `TodoEditor`, `TodoList`, `ConflictReview`), accessible light theme tokens, and comprehensive unit/instrumented/semantics/vector tests.
3. **Android Application Module (`androidApp/`)**: Native Android client hosting shared Compose UI, `TabbyRushApplication` process container, `AccountRuntime` lifecycle, reminder scheduling, and account-scoped sticky surfaces. `task-surface-access.kt` and `task-surface-runtime-coordinator.kt` connect account-generation admission and runtime observers to task projections. `task-widget.kt` implements the Glance widget; `task-surface-notification.kt` provides the quiet deadline notification; supporting files handle preferences, intents, actions, receivers, refresh, settings, and widget updates. Android instrumented tests cover account admission, navigation, preferences, and widget behavior.
4. **Desktop Application Module (`desktopApp/`)**: Compose Multiplatform JVM app with Linux and Windows source sets, shared credential-vault SPI, `DesktopController`, and account-scoped `DesktopStickyTaskController`. `sticky-task-content.kt` renders the compact sticky window; `desktop-surface-platform.kt` owns tray capability and native operations; `sticky-window-preferences.kt` and `sticky-window-geometry.kt` persist and constrain presentation-only state. The tray is conditional on host support. Linux sticky-surface acceptance remains pending a real Linux session.
5. **Host Virtualization, Security & Operations Tooling (`scripts/`)**: Headless Xpra display orchestration, Android SDK/JDK environment configuration, emulator management, Hub schema provisioning (`hub-schema-provision.mjs`), live Hub strict smoke harness (`hub-live-smoke.mjs`), release security and integrity auditor (`release-security-check.mjs`), maintenance recovery script (`hub-strict-recovery.js`, quarantined for legacy strict-v1 emergency cold restores), device smoke runner (`android-smoke.sh`), loopback sync fault-injection proxy (`debug-fault-proxy.mjs`), Linux/Windows package smoke runners, strict-v2 vector runner (`strict-v2-vector-runner.mjs`), vector generator (`generate-strict-v2-vectors.mjs`), CLI upgrade runner (`hub-strict-upgrade.mjs`), and operator cutover rehearsal runner (`strict-v2-cutover-rehearsal.mjs`).
6. **Hub Integration Artifacts (`integration/`)**: Canonical JSON schema definitions for `strict-v1` (`tabbyrush-schema-v1.json`) and `strict-v2` (`tabbyrush-schema-v2.json`), active schema (`tabbyrush-schema.json`), and cryptographic reference test vector fixture (`strict-v2-vectors.json`).
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
│   ├── gradle.lockfile                 # Locked Android dependencies including Glance 1.2.0; artifact provenance remains pending
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # MainActivity and narrowly scoped widget receiver exports
│       │   ├── kotlin/dev/tabbyrush/android/
│       │   │   ├── tabbyrush-application.kt # Process container and wipe recovery
│       │   │   ├── main-activity.kt # Shared Compose UI host
│       │   │   ├── session-view-model.kt # Reactive UI state and cold-start routing
│       │   │   ├── android-account-state-manager.kt # Active account identity and wipe markers
│       │   │   ├── account-runtime.kt # Account-scoped Room/store runtime
│       │   │   ├── account-runtime-factory.kt # Runtime construction and leave protocols
│       │   │   ├── android-sync-scheduler.kt # WorkManager scheduling and cancellation gate
│       │   │   ├── tabbyrush-sync-worker.kt # Generation-fenced background sync
│       │   │   ├── android-notification-adapter.kt # Native reminder scheduling and delivery
│       │   │   ├── android-notification-intent-factory.kt # Immutable identity-only reminder intents
│       │   │   ├── android-alarm-registry.kt # Scheduled alarms and active notifications
│       │   │   ├── tabbyrush-alarm-receiver.kt # Private reminder alarm receiver
│       │   │   ├── reminder-restore-receiver.kt # Boot/unlock/timezone reminder restoration
│       │   │   ├── reminder-permission-controller.kt # Reminder permission states
│       │   │   ├── android-reminder-capabilities.kt # POST_NOTIFICATIONS and exact-alarm capability probes
│       │   │   ├── composite-leave-scheduler-cancel-gate.kt # Coordinated account teardown gate
│       │   │   ├── task-surface-host.kt # Surface host ownership and refresh reasons
│       │   │   ├── task-surface-access.kt # Account-token admission and action/navigation access
│       │   │   ├── task-surface-runtime-coordinator.kt # Account observers and surface publication
│       │   │   ├── task-surface-notification.kt # Opt-in quiet deadline notification
│       │   │   ├── task-surface-preferences.kt # Account-local surface preference state
│       │   │   ├── task-surface-intents.kt # Explicit immutable surface action intents
│       │   │   ├── task-surface-action-receiver.kt # Surface task-action receiver
│       │   │   ├── task-surface-dismiss-receiver.kt # Notification dismissal receiver
│       │   │   ├── task-surface-time-receiver.kt # Deadline-boundary receiver
│       │   │   ├── task-surface-refresh-worker.kt # Persisted surface refresh worker
│       │   │   ├── task-surface-settings.kt # Surface opt-in/settings UI
│       │   │   ├── task-surface-create-screen.kt # Widget quick-create destination
│       │   │   ├── task-surface-permission-controller.kt # Notification permission boundary
│       │   │   ├── task-widget.kt # Responsive Glance task widget
│       │   │   ├── task-widget-actions.kt # Widget open, create, and completion actions
│       │   │   ├── task-widget-receiver.kt # App widget provider receiver
│       │   │   └── task-widget-updater.kt # Refresh, blanking, and Glance session cleanup
│       │   ├── res/drawable/task_surface_notification_icon.xml # Notification icon
│       │   ├── res/layout/task_widget_initial.xml # Initial widget layout
│       │   ├── res/values/task-widget-strings.xml # Widget strings
│       │   ├── res/values/task-surface-strings.xml # Surface notification/settings strings
│       │   └── res/xml/
│       │       ├── backup_rules.xml # API <= 30 backup exclusions
│       │       ├── data_extraction_rules.xml # API >= 31 transfer exclusions
│       │       └── task_widget_info.xml # Glance widget metadata
│       ├── debug/AndroidManifest.xml # Debug-only network security configuration
│       ├── test/kotlin/dev/tabbyrush/android/
│       │   ├── task-surface-notification-test.kt # Quiet notification behavior
│       │   ├── task-widget-model-test.kt # Widget model and action behavior
│       │   ├── android-notification-adapter-test.kt # Reminder adapter behavior
│       │   ├── composite-leave-scheduler-cancel-gate-test.kt # Leave cancel gate
│       │   ├── reminder-permission-controller-test.kt # Permission states
│       │   └── build-config-test.kt # Build configuration
│       └── androidTest/kotlin/dev/tabbyrush/android/
│           ├── task-surface-account-device-test.kt # Account admission and stale actions
│           ├── task-surface-navigation-device-test.kt # Surface navigation and task creation
│           ├── task-surface-preferences-device-test.kt # Preference persistence
│           ├── task-widget-device-test.kt # Widget rendering/actions
│           ├── todo-device-test.kt # ViewModel and store behavior
│           └── background-isolation-test.kt # WorkManager/account isolation
├── desktopApp/                         # Desktop application module (Kotlin JVM 2.1.10, Compose 1.7.3)
│   ├── build.gradle.kts                # Desktop build script, target detection (linux/windows), RPM/MSI config
│   ├── gradle-linux.lockfile           # Linux dependency lockfile (161 locked artifacts)
│   ├── gradle-windows.lockfile         # Windows target-specific dependency lockfile
│   └── src/
│       ├── main/kotlin/dev/tabbyrush/desktop/
│       │   ├── main.kt # MainKt and DevelopmentLauncher window/application entry points
│       │   ├── credential-vault-provider.kt # CredentialVaultProvider SPI
│       │   ├── platform-vault-dispatcher.kt # ServiceLoader vault dispatcher and target validation
│       │   ├── desktop-sync-scheduler.kt # Foreground sync scheduling and cancellation gate
│       │   ├── single-instance-guard.kt # File-channel single-instance lock
│       │   ├── sticky-task-model.kt # Compact task state and action contracts
│       │   ├── desktop-task-surfaces.kt # Account-scoped sticky controller, observation, and tray lifecycle
│       │   ├── sticky-task-content.kt # Compact Compose sticky window content
│       │   ├── desktop-surface-platform.kt # Tray capability and native desktop surface operations
│       │   ├── sticky-window-preferences.kt # Presentation-only sticky window preferences
│       │   └── sticky-window-geometry.kt # Sticky window geometry and scale constraints
│       ├── linuxMain/
│       │   ├── kotlin/dev/tabbyrush/desktop/linux/
│       │   │   ├── linux-secret-service-vault.kt # Freedesktop Secret Service vault implementation
│       │   │   └── linux-notification-adapter.kt # Freedesktop D-Bus notification candidate with resilient error handling
│       │   └── resources/META-INF/services/
│       │       └── dev.tabbyrush.desktop.CredentialVaultProvider # ServiceLoader descriptor for Linux provider
│       ├── windowsMain/
│       │   ├── kotlin/dev/tabbyrush/desktop/windows/
│       │   │   ├── windows-dpapi-vault.kt # Current-user DPAPI vault and provider
│       │   │   └── windows-notification-adapter.kt # Windows toast candidate, XML escaping, tag/group clear, and safe stub
│       │   └── resources/META-INF/services/
│       │       └── dev.tabbyrush.desktop.CredentialVaultProvider # Windows ServiceLoader descriptor
│       ├── test/kotlin/dev/tabbyrush/desktop/
│       │   ├── desktop-task-surfaces-test.kt # Controller actions, lifecycle, and tray behavior
│       │   ├── sticky-window-geometry-test.kt # Geometry normalization and constraints
│       │   ├── sticky-window-preferences-test.kt # Sticky preference persistence
│       │   ├── platform-vault-dispatcher-test.kt # SPI resolution and target validation
│       │   ├── desktop-lifecycle-test.kt # Single-instance lock, sync, and build mode
│       │   ├── desktop-controller-env-test.kt # Desktop controller environment behavior
│       │   ├── composite-desktop-leave-cancel-gate-test.kt # Leave cleanup coordination
│       │   └── desktop-reminder-scheduler-test.kt # Reminder scheduling behavior
│       ├── linuxTest/kotlin/dev/tabbyrush/desktop/linux/
│       │   ├── linux-secret-service-vault-test.kt # Secret Service status, store, replace, clear tests
│       │   └── linux-notification-adapter-test.kt # Unit tests for capabilities, missing daemon, and missing bus
│       └── windowsTest/kotlin/dev/tabbyrush/desktop/windows/
│           ├── windows-dpapi-vault-test.kt # DPAPI provider, vault lifecycle, and native round-trip tests
│           └── windows-notification-adapter-test.kt # Unit tests for XML escaping, payload generation, and safe unavailability
├── gradle/
│   ├── libs.versions.toml              # Version catalog (Kotlin 2.1.10, Compose 1.7.3, AGP 8.7.3, Room 2.7.0-alpha12)
│   ├── verification-metadata.xml       # Gradle dependency checksum verification
│   └── wrapper/
│       ├── gradle-wrapper.jar          # Verified wrapper jar (SHA-256: 2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046)
│       └── gradle-wrapper.properties   # Official Gradle 8.11.1 distribution with SHA-256 checksum pinning
├── shared/                             # Shared Kotlin Multiplatform library
│   ├── build.gradle.kts                # KMP library configuration (dev.tabbyrush.shared, minSdk 26, JVM 17)
│   ├── gradle.lockfile                 # Dependency locking definition
│   ├── schemas/                        # Exported Room database schemas
│   │   └── dev.tabbyrush.shared.data.TabbyRushDatabase/
│   │       ├── 1.json                  # Canonical Room schema version 1 export
│   │       └── 2.json                  # Canonical Room schema version 2 export
│   └── src/
│       ├── commonMain/composeResources/
│       │   └── values/
│       │       └── strings.xml             # UI string resource catalog
│       ├── commonMain/kotlin/dev/tabbyrush/shared/
│       │   ├── contract/ # Pure domain contracts and validation policies
│       │   │   ├── strict-sync-v2-contract.kt # Strict-v2 models, checked arithmetic, error enums
│       │   │   ├── strict-sync-v2-digest.kt # Strict-v2 binary preimage framing and SHA-256
│       │   │   ├── strict-timezone-resolver.kt # Timezone contract and result hierarchy
│       │   │   ├── task-surface-contracts.kt # Account-scoped readiness, access, snapshots, and results
│       │   │   ├── task-surface-projector.kt # Pure task-section and next-deadline projections
│       │   │   └── account-store.kt # Reactive task observation and desired completion mutation API
│       │   ├── auth/ # Hub authentication, HTTP client, and session state machine
│       │   ├── data/ # Room entities, DAO, database, and RoomAccountStore
│       │   │   ├── server-base-entity.kt # Confirmed server base and permanent compact tombstones
│       │   │   ├── outbox-entity.kt # Frozen strict mutations and delivery states
│       │   │   ├── editor-draft-entity.kt # Uncommitted local user edits
│       │   │   ├── scan-staging-entity.kt # Cursor pagination staging table
│       │   │   ├── account-sync-meta-entity.kt # Account sync metadata and active epoch
│       │   │   ├── tabbyrush-dao.kt # Atomic @Transaction DAO methods and queries
│       │   │   ├── tabbyrush-database.kt # RoomDatabase definition (version 2, 7 entities)
│       │   │   ├── database-migrations.kt # Migration registry
│       │   │   ├── account-local-settings-entity.kt # Account/device reminder and notification defaults
│       │   │   ├── reminder-delivery-ledger-entity.kt # Durable native reminder delivery ledger and claims
│       │   │   └── room-account-store.kt # Mutex-guarded AccountStore with SQLite persistence
│       │   ├── sync/ # Strict synchronization protocol orchestration
│       │   │   ├── contract-transition-coordinator.kt # Upgrade state machine and receipt matching
│       │   │   ├── hub-sync-dtos.kt # Wire DTOs for bootstrap, push, pull, and legacy receipts
│       │   │   ├── hub-sync-client.kt # HTTP transport adapter
│       │   │   ├── staged-full-scan.kt # Monotonic staging and pagination
│       │   │   ├── outbox-reconciliation.kt # FIFO dispatch, CAS acknowledgements, and UnknownSuperseded
│       │   │   ├── account-sync-coordinator.kt # Sync rounds, conflict actions, and generation fencing
│       │   │   └── account-leave-protocol.kt # Guarded leave and wipe marker protocol
│       │   ├── theme/ # Accessible light theme and typography
│       │   │   ├── tabbyrush-theme.kt # Color tokens, contrast palette, and theme wrapper
│       │   │   └── tabbyrush-typography.kt # Type scale and system SansSerif basis
│       │   └── ui/ # Shared Compose Multiplatform UI components
│       │       ├── tabbyrush-app.kt # Responsive application scaffold
│       │       ├── account-form.kt # Login and registration form
│       │       ├── access-request-panel.kt # App access request and admin guidance
│       │       ├── todo-list.kt # Lazy task list and deadline/overdue badges
│       │       ├── todo-editor.kt # Task editor and strict-v2 upgrade validation
│       │       ├── deadline-editor-section.kt # Deadline/reminder controls and DST offsets
│       │       ├── date-time-pickers.kt # Material 3 date/clock dialogs, button triggers, UTC-safe date formats
│       │       ├── deadline-calculator-helpers.kt # Deadline validation and calendar boundaries
│       │       ├── reminder-settings.kt # Reminder settings and platform capabilities
│       │       ├── sync-feedback.kt # Sync progress and status
│       │       ├── conflict-review.kt # Conflict comparison and actions
│       │       ├── leave-guard.kt # Guarded account sign-out and unsynced tallies
│       │       └── license-notices.kt # Open-source dependency notices
│       ├── androidMain/kotlin/dev/tabbyrush/shared/
│       │   ├── auth/                   # Android OkHttp engine and Keystore vault
│       │   ├── contract/               # Platform actual contracts
│       │   │   └── strict-timezone-resolver-android.kt # Android ZoneRules platform actual
│       │   └── data/
│       │       ├── android-database-builder.kt # BundledSQLiteDriver Room database builder
│       │       └── android-account-files.kt    # Account directory partitioning and wipe markers
│       ├── jvmMain/kotlin/dev/tabbyrush/shared/
│       │   ├── auth/                   # JVM Java HTTP engine
│       │   ├── contract/               # Platform actual contracts
│       │   │   └── strict-timezone-resolver-jvm.kt     # JVM ZoneRules platform actual
│       │   └── data/
│       │       ├── jvm-database-builder.kt     # Desktop BundledSQLiteDriver Room database builder
│       │       └── jvm-account-files.kt        # Desktop account directory partitioning and wipe markers
│       ├── commonTest/kotlin/dev/tabbyrush/shared/
│       │   ├── contract/
│       │   │   ├── task-deadline-reminder-models-test.kt # Temporal models and checked arithmetic
│       │   │   ├── strict-v2-digest-vector-test.kt # Strict-v2 vector fixtures
│       │   │   ├── strict-v2-digest-extended-test.kt # Digest edge cases and overflow
│       │   │   ├── strict-timezone-resolver-test.kt # Timezone and DST transition vectors
│       │   │   └── task-surface-projector-test.kt # Task partitioning and deadline selection
│       │   ├── auth/ # Auth client, session, and refresh tests
│       │   ├── sync/
│       │   │   ├── contract-transition-test.kt # Transition states and durable resume
│       │   │   ├── hub-sync-client-test.kt # Push, pull, bootstrap, and receipts
│       │   │   ├── staged-full-scan-test.kt # Pagination and migration enrichment
│       │   │   ├── outbox-reconciliation-test.kt # CAS acknowledgements and retries
│       │   │   ├── account-sync-coordinator-test.kt # Sync rounds, conflicts, and cancellation
│       │   │   └── account-leave-protocol-test.kt # Teardown order and wipe recovery
│       │   └── ui/
│       │       ├── date-time-pickers-test.kt # UTC date conversion and canonical 24-hour time helper tests
│       │       └── todo-semantics-test.kt # Accessibility, deadline validation, and picker/domain semantics
│       ├── jvmTest/kotlin/dev/tabbyrush/shared/data/
│       │   ├── room-migration-v1-to-v2-test.kt # Explicit SQLite migration verification
│       │   ├── room-account-store-test.kt # Room projections and deadline clearing behavior
│       │   ├── task-surface-store-sqlite-test.kt # Desired completion mutation and observation behavior
│       │   └── task-surface-sync-sqlite-test.kt # Surface behavior through SQLite sync projections
│       └── androidInstrumentedTest/kotlin/dev/tabbyrush/shared/
│           ├── auth/ # Keystore vault instrumented tests
│           └── data/
│               └── room-account-store-test.kt # Android Room instrumented tests
├── integration/                         # Integration schemas and specifications
│   └── glean-hub/
│       ├── tabbyrush-schema.json             # Exact strict-v1 todos schema specification
│       ├── tabbyrush-schema-v1.json          # Canonical strict-v1 schema (fingerprint: 3361a80f...)
│       ├── tabbyrush-schema-v2.json          # Canonical strict-v2 schema (fingerprint: 5f539551...)
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
│   ├── hub-strict-upgrade.test.mjs     # Unit test suite for CLI upgrade runner (mock HTTP server)
│   └── strict-v2-cutover-rehearsal.mjs # Strict-v2 operator tooling rehearsal and self-test harness
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
    ├── strict-v2-cutover-runbook.md    # Phase 12 authoritative operator cutover and recovery runbook
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
        ├── windows-*.png               # 7 visual design mockups for Windows desktop screen states
        ├── sticky-surfaces-android-summary.md # Phase 07 Android sticky surfaces acceptance summary
        ├── sticky-surfaces-windows-summary.md # Phase 07 Windows sticky surfaces acceptance summary
        ├── sticky-surfaces-android-widget.png # Visual design baseline for Glance task widget
        ├── sticky-surfaces-android-notification.png # Visual design baseline for quiet deadline notification
        ├── sticky-surfaces-windows-window.png # Visual design baseline for sticky companion window
        └── sticky-surfaces-windows-tray.png # Visual design baseline for Windows system tray menu
```

---

## 2. Core Components & Artifacts

### 2.1 Gradle Build System & Version Catalog

| File | Purpose | Key Details |
|---|---|---|
| `settings.gradle.kts` | Staged assembly & plugin management | Unconditionally includes `:shared`; conditionally guards `:androidApp` and `:desktopApp` based on file existence. |
| `build.gradle.kts` | Root build script | Registers `clean` task; declares plugins with `apply false`. |
| `gradle.properties` | Build engine settings | Configures 2 GB (-Xmx2048m) Gradle JVM heap, Kotlin daemon parallel compilation, AndroidX support, and appVersion=1.0.17 (versionCode 18). |
| `gradle/libs.versions.toml` | Pinned version catalog | Pins Kotlin 2.1.10, Compose Multiplatform 1.7.3, AGP 8.7.3, Coroutines 1.10.1, Serialization 1.8.0, Ktor 3.1.0, Room 2.7.0-alpha12, and SQLite 2.5.0-alpha12. |
| `gradle/wrapper/gradle-wrapper.properties` | Wrapper distribution | Specifies Gradle 8.11.1 bin distribution with official checksum. |
| `gradle/wrapper/gradle-wrapper.jar` | Wrapper binary launcher | Checksum verified against known Gradle release hash. |
| `gradle/verification-metadata.xml` | Dependency verification | Checksum configuration file establishing dependency verification rules during resolution. |

### 2.2 Shared Module Build & Contracts (`shared/`)

- Applied plugins: `com.android.library`, `kotlinMultiplatform`, `kotlinxSerialization`, `androidxRoom`.
- Namespace: `dev.tabbyrush.shared`, `minSdk = 26`, `compileSdk = 35`, JVM bytecode target 17.
- Room KMP schema export configured to `shared/schemas/`.
- Domain contracts (`todo-models.kt`, `todo-ui-state.kt`, `todo-action.kt`, `safe-errors.kt`, `account-key.kt`, `strict-sync-contract.kt`, `title-policy.kt`, `endpoint-policy.kt`) have zero dependencies on Android classes or UI frameworks.

### 2.3 Authentication Subsystem (Phase 04)

- `hub-auth-dtos.kt`, `hub-http-client.kt`, `hub-auth-client.kt`: Wire DTOs and client enforcing origin allowlists, route classification, dual-auth headers, and bounded timeouts.
- `hub-auth-session.kt`, `single-flight-refresh.kt`: Generation-fenced auth state machine and concurrent refresh leader.
- `android-keystore-vault.kt`: Android Keystore AES-256-GCM vault (`AndroidKeystoreVault`) with fresh nonces, AAD binding, and atomic file replacement (located in `shared/src/androidMain/kotlin/dev/tabbyrush/shared/auth/`).
- Desktop credential vaults: Implemented as target-specific desktop providers loaded via Java `ServiceLoader` through `PlatformVaultDispatcher`: `LinuxSecretServiceVault` resides in `desktopApp/src/linuxMain/` (Freedesktop Secret Service over D-Bus via `secret-tool`), and `WindowsDpapiVault` resides in `desktopApp/src/windowsMain/` (current-user DPAPI via JNA Crypt32). Neither desktop vault is housed in `shared/src/jvmMain/`.
### 2.4 Local Storage & Strict Synchronization Subsystem (Phase 05)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `server-base-entity.kt` | `ServerBaseEntity` | Authoritative confirmed server state table (`server_base`); permanent compact tombstones (`deleted = true`, empty title). |
| `outbox-entity.kt` | `OutboxEntity` | Strict mutation queue (`outbox`); frozen fields (`opId`, `epoch`, `kind`, `expectedRevision`, `digest`, `localRevision`); delivery states: `PENDING`, `DISPATCHING`, `UNCERTAIN`, `CONFLICT`, `UNKNOWN_SUPERSEDED`. |
| `editor-draft-entity.kt` | `EditorDraftEntity` | Uncommitted local user edits table (`editor_drafts`); preserved on ack if `draft.localRevision > outbox.localRevision`. |
| `scan-staging-entity.kt` | `ScanStagingEntity` | Cursor pull pagination staging table (`scan_staging`); promoted atomically upon reaching terminal page. |
| `account-sync-meta-entity.kt` | `AccountSyncMetaEntity` | Account partition metadata (`account_sync_meta`); tracks `activeEpoch`, `localRevisionCounter`, `syncStatus`. |
| `tabbyrush-dao.kt` | `TabbyRushDao` | Room DAO with atomic `@Transaction` methods: `saveTodoAtomic`, `deleteTodoAtomic`, `cancelUnsentCreateAtomic`, `reconcileAcceptedAtomic`, `promoteStagingAtomic`, `replaceOutboxAtomic`, `discardConflictAtomic`, `freezeOutboxAtomic`, `wipeAllTablesAtomic`. |
| `tabbyrush-database.kt` | `TabbyRushDatabase` | Room database definition (version 2, 7 entities) backed by `BundledSQLiteDriver`. |
| `database-migrations.kt` | `DatabaseMigrations.MIGRATION_1_2` | Explicit SQLite migration upgrading Room schema 1 to 2; registered in `DatabaseMigrations.ALL`. |
| `account-local-settings-entity.kt` | `AccountLocalSettingsEntity` | Account/device reminder and notification defaults table (`account_local_settings`); singleton `id = 'singleton'`. |
| `reminder-delivery-ledger-entity.kt` | `ReminderDeliveryLedgerEntity` | Durable occurrence state and claim outcomes for native reminders (`reminder_delivery_ledger`); composite PK 5-tuple; tracks `claimOutcome` and `cancellationState`. |
| `room-account-store.kt` | `RoomAccountStore` | Implementation of `AccountStore` with mutex-serialized edits, outbox coalescing, unsent cancellation, intent mask derivation, and dispatch recovery. |
| `android-database-builder.kt` | `createAndroidTabbyRushDatabase()` | Platform Room database builder for Android with `BundledSQLiteDriver`. |
| `jvm-database-builder.kt` | `createJvmTabbyRushDatabase()` | Platform Room database builder for Desktop JVM with `BundledSQLiteDriver`. |
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
| `tabbyrush-theme.kt` | `TabbyRushTheme`, `TabbyRushColors`, `TabbyRushCustomColors` | Accessible light palette (WCAG 2.1 AA verified), Material 3 `LightColorScheme`, dynamic colors disabled. |
| `tabbyrush-typography.kt` | `TabbyRushTypography` | Pinned typography scale (12sp–34sp) based on Compose `FontFamily.SansSerif` for universal platform support. |
| `tabbyrush-app.kt` | `TabbyRushApp`, `TabbyRushContent` | Root composable with responsive layout (<840dp compact vertical stack, >=840dp expanded sidebar). |
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

- `integration/glean-hub/tabbyrush-schema.json`: Canonical strict-v1 todos schema specification.
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
| `main-activity.kt` | `MainActivity` | Single exported `ComponentActivity` providing Android Compose container hosting `TabbyRushApp(viewModel)`. |
| `tabbyrush-application.kt` | `TabbyRushApplication`, `AccountRuntime` | Process container managing singletons (`vault`, `accountFiles`, `hubConfig`, `authSession`, `syncScheduler`), active `AccountRuntime`, atomic `active_account.dat` persistence, and synchronous startup wipe recovery (`completePendingWipesOnStartup`). |
| `session-view-model.kt` | `SessionViewModel` | Retained `ViewModel` binding `TodoUiState` to auth/sync StateFlows, dispatching `TodoAction`, sanitizing UI errors (`SafeError.Internal/Validation`), and maintaining zero credential retention. |
| `android-sync-scheduler.kt` | `AndroidSyncScheduler` | Implements `SyncScheduler` and `LeaveSchedulerCancelGate` using WorkManager; configures unique one-shot and periodic work with network constraints and account tags; `cancelAndAwait()` quiescence gate polls WorkManager and coordinator mutex with 5s timeout and fail-closed exception throwing. |
| `tabbyrush-sync-worker.kt` | `TabbyRushSyncWorker` | WorkManager `CoroutineWorker` executing background sync with wipe marker inspection, auth session retry/mismatch checks, and persisted generation counter validation. |
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

### 2.10 Desktop Application Module, Scheduler & Native Adapters (Phases 09–10)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `build.gradle.kts` | `:desktopApp` build script | Configures Compose Multiplatform 1.7.3 on Kotlin JVM 2.1.10; detects and isolates Linux/Windows host targets; binds D-Bus only on Linux and JNA only on Windows; configures RPM/MSI distributions and target-specific lockfiles. |
| `gradle-linux.lockfile` | Dependency locking definition | Linux desktop dependency lockfile (161 locked artifacts). |
| `gradle-windows.lockfile` | Dependency locking definition | Windows desktop target lockfile, independently resolved from Linux. |
| `credential-vault-provider.kt` | `CredentialVaultProvider` | OS credential-vault Service Provider Interface for desktop targets. |
| `platform-vault-dispatcher.kt` | `PlatformVaultDispatcher` | Uses Java `ServiceLoader`; requires exactly one vault provider matching the active target and fails closed for zero or duplicate matches. |
| `desktop-reminder-adapter.kt` | `DesktopReminderAdapter` | Interface extending `NativeReminderAdapter` providing `setScheduleListener()` for desktop coroutine scheduler. |
| `desktop-reminder-adapter-provider.kt` | `DesktopReminderAdapterProvider` | Service Provider Interface for desktop native reminder adapters. |
| `platform-reminder-dispatcher.kt` | `PlatformReminderDispatcher` | ServiceLoader dispatcher enforcing exactly one matching native reminder adapter provider per desktop target. |
| `desktop-reminder-scheduler.kt` | `DesktopReminderScheduler` | Process-scoped scheduler with bounded 15s wake loop on `Dispatchers.IO`, conflated update channel, backward clock jump protection, and grace window evaluation. |
| `composite-desktop-leave-cancel-gate.kt` | `CompositeDesktopLeaveCancelGate` | Strict 5-stage ordered quiescence gate for account leave (delivery gate drain -> observer cancel -> reminder scheduler cancel -> sync cancel -> notification cancel). |
| `single-instance-guard.kt` | `SingleInstanceGuard` | Acquires the OS file-channel lock before vault/database initialization to prevent concurrent launch and profile races. |
| `desktop-sync-scheduler.kt` | `DesktopSyncScheduler` | Window-scoped immediate/15-minute sync and `LeaveSchedulerCancelGate` quiescence. |
| `main.kt` | `MainKt`, `DevelopmentLauncher`, `DesktopController`, `DesktopAccountRuntime` | Desktop Compose entrypoints and runtime; wires reminder coordinator, delivery gate, scheduler, and composite leave gate; single-window close disposal without lingering tray/daemon. |
| `linux-secret-service-vault.kt` | `LinuxSecretServiceVault`, provider, client | Linux Secret Service credential provider over D-Bus; no plaintext token fallback. |
| `linux-notification-adapter.kt` | `LinuxNativeReminderAdapter`, `LinuxReminderAdapterProvider`, `DbusLinuxNotificationAdapter` | Linux Freedesktop D-Bus reminder adapter and ServiceLoader provider over `Dispatchers.IO`; probes capabilities with empty list tolerance, tracks IDs in process memory, aggregates errors on cancel. |
| `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-dpapi-vault.kt` | `WindowsDpapiVault`, `WindowsDpapiVaultProvider`, `JnaWindowsDpapiProtector` | Windows user-scoped DPAPI credential bundle with temporary-file/atomic replacement and plaintext byte-buffer clearing. `applyRestrictiveUserAcl()` is best-effort; ACL failures are suppressed. |
| `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt` | `WindowsNativeReminderAdapter`, `WindowsReminderAdapterProvider`, `JnaWindowsToastBridge` | Windows candidate adapter with AUMID `dev.tabbyrush.desktop`, group `d_<accountKey>`, tag `t_<taskId>_<index>`, and XML escaping. Bridge stub returns `Unavailable`. |
| `META-INF/services/` | ServiceLoader descriptors | Registrations for `CredentialVaultProvider` and `DesktopReminderAdapterProvider` under Linux and Windows targets. |
| `linux-package-smoke.sh` / `windows-package-smoke.ps1` | Platform package audits | Inspect target-specific distributable contents; Windows runner also computes MSI SHA-256, reports signature status, and inspects MSI metadata using COM. |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `platform-vault-dispatcher-test.kt` | Host target and ServiceLoader vault provider resolution; zero-match, duplicate-match, and mismatch fail-closed cases. | **PASSED** (5/5) |
| `desktop-lifecycle-test.kt` | Single-instance lock, sync scheduler quiescence, and RELEASE/DEBUG origin policies. | **PASSED** (8/8) |
| `linux-secret-service-vault-test.kt` | Linux Secret Service status, atomic storage/replacement, corruption handling, and clear. | **PASSED** (Linux, 6/6) |
| `windows-dpapi-vault-test.kt` | DPAPI vault status, store/load, replacement/deletion, corrupt payload handling, provider discovery, target isolation, native DPAPI round-trip, and stored-file ACL check on ACL-capable filesystems. | **PASSED** (Windows, 12/12 per Phase 10 evidence; ACL failure paths are not covered) |
| `desktop-reminder-scheduler-test.kt` | Bounded 15s wake loop, backward clock jump protection, replace-based updates, clean cancel-and-await quiescence. | **PASSED** (5/5) |
| `composite-desktop-leave-cancel-gate-test.kt` | 5-stage ordered quiescence execution, delivery drain timeout fail-closed abort. | **PASSED** (2/2) |
| `desktop-reminder-controller-lifecycle-test.kt` | Reminder subsystem init, delivery gate linkage, startup wipe recovery notification cleanup, fail-closed marker retention on cancellation error. | **PASSED** (3/3) |
| `linux-reminder-adapter-test.kt` | Capabilities reporting, empty capability tolerance, account-scoped tracking, replace ID mapping, account cleanup, close error propagation. | **PASSED** (Linux, 6/6) |
| `linux-notification-adapter-test.kt` | Low-level Freedesktop D-Bus session communication, missing daemon/session error mapping. | **PASSED** (Linux, 4/4) |
| `windows-reminder-adapter-test.kt` | Toast XML escaping, tag/group formatting, multi-account isolation, error propagation on bridge failure, provider discovery. | **AUTHORED, UNEXECUTED** (Authored candidate test suite with fake bridge; not compiled or executed on Linux host) |
### 2.11 Strict Timezone & DST Resolution Subsystem (`shared/`)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `strict-timezone-resolver.kt` | `StrictTimeZoneResolver`, `StrictZoneOffsetResult` (`Single`, `Overlap`, `Gap`) | Interface contract defining strict local date/time offset resolution, instant resolution, calendar day start, next day boundary calculation, and zone validation. |
| `strict-timezone-resolver-jvm.kt` | `getStrictTimeZoneResolver()` | JVM platform actual binding delegating to standard `java.time.zone.ZoneRules` (Java 8+). |
| `strict-timezone-resolver-android.kt` | `getStrictTimeZoneResolver()` | Android platform actual binding delegating to standard `java.time.zone.ZoneRules` (API 26+). |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `strict-timezone-resolver-test.kt` | 7 DST and boundary vectors: NY Spring gap (0 valid offsets, instant throws `IllegalArgumentException`), NY Fall overlap (2 offsets separated by 3600s, explicit offset required), standard time, 23h spring boundary, 25h fall boundary, zone/bounds validation, midnight gap transition. | **RECORDED PASSED** (Recorded in Phase 02 QA report, 7/7 JVM tests under `--dependency-verification=off`; API 26 device pending SDK) |

### 2.12 Native Candidate Notification Adapters (`androidApp/` & `desktopApp/`)

| Component File | Primary Types & Functions | Architectural Role |
|---|---|---|
| `android-notification-adapter.kt` | `AndroidNotificationAdapter` | Android notification channel (`tabbyrush_reminders`) and AlarmManager intent factory (`setExactAndAllowWhileIdle`). |
| `android-reminder-capabilities.kt` | `AndroidReminderCapabilitiesChecker` | Capability checker decoupling runtime `POST_NOTIFICATIONS` from `SCHEDULE_EXACT_ALARM` permissions. |
| `tabbyrush-alarm-receiver.kt` | `TabbyRushAlarmReceiver` | Non-exported `BroadcastReceiver` (`android:exported="false"`) handling offline alarm delivery with extras validation. |
| `linux-notification-adapter.kt` | `DbusLinuxNotificationAdapter`, `SessionBusNotificationTransport` | Freedesktop `/org/freedesktop/Notifications` D-Bus adapter mapping missing daemon (`ServiceUnknown`) to `LinuxNotificationStatus.Unavailable`. |
| `windows-notification-adapter.kt` | `WindowsNotificationAdapter`, `WindowsToastBridge`, `JnaWindowsToastBridge` | Windows toast candidate configuring AUMID `dev.tabbyrush.desktop` and XML escaping; native COM/WinRT bridge is **unimplemented** (`isAvailable() = false`, returns `Unavailable`). Native implementation and MSI runtime acceptance deferred to Phase 11. |

| Test Suite File | Scope & Tested Invariants | Status |
|---|---|---|
| `android-notification-adapter-test.kt` | Action and extra constants, intent extras assembly, capabilities data model. | **SOURCE INSPECTED** (Host SDK pending; unexecuted on Android runner) |
| `linux-notification-adapter-test.kt` | Capabilities query, missing D-Bus daemon graceful unavailability, missing session bus handling, environment probe. | **RECORDED PASSED** (Recorded in Phase 02 QA report, 4/4 tests under `--dependency-verification=off`) |
| `windows-notification-adapter-test.kt` | XML attribute/entity escaping, toast XML construction, explicit bridge unavailability stub test. | **RECORDED PASSED** (Recorded in Phase 02 code review, 3/3 tests under `--dependency-verification=off`; candidate stub only) |

### 2.13 Glean Hub Strict Backend Modules & Services (`glean-hub-sync/`)

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

### 2.14 Server Admin Announcements & Remote Subsystem Components (Phase 12)

| Component File (Repo Relative) | Primary Types & Functions | Architectural Role |
|---|---|---|
| `shared/src/commonMain/.../remote-inbox-contracts.kt` | `RemoteInboxItem`, `RemoteInboxPage`, `RemoteInboxState`, `RemoteAction`, `RemoteInboxError`, wire DTOs | Pure Multiplatform contracts, limits validation, and wire serialization models. |
| `shared/src/commonMain/.../remote-inbox-controller.kt` | `RemoteInboxController`, `RemoteInboxState` | Sliding window in-memory cache ($\le 200$ items, dedup 2048), monotonic expiry filtering, and zero Room persistence. |
| `shared/src/commonMain/.../remote-inbox-client.kt` | `RemoteInboxClient`, HTTP fetchers | Bounded Ktor HTTP client for `/inbox`, `/unread`, `/inbox/{id}/read`, and `/notification-installs`. |
| `shared/src/commonMain/.../remote-stream-client.kt` | `RemoteStreamClient`, `RemoteStreamEvent` | SSE stream consumer parsing `event: ready` baseline and `event: announcement` hints. |
| `shared/src/commonMain/.../remote-inbox.kt` | `RemoteInboxScreen`, `RemoteDetailDialog` | Multiplatform Compose UI for in-app inbox, deep linking, and manual read dispatch. |
| `androidApp/src/main/.../remote-install-coordinator.kt` | `RemoteInstallCoordinator`, `RemoteInstallStore` | Android install proof lifecycle, Keystore-backed AES-256-GCM encrypted persistence, and generation fencing. |
| `androidApp/src/main/.../remote-firebase-messaging-service.kt` | `RemoteFirebaseMessagingService` | Private FCM receiver posting generic alerts on channel `tabbyrush.remote.announcements` with tag `remote:<messageId>`. |
| `desktopApp/src/main/.../desktop-remote-runtime.kt` | `DesktopRemoteRuntime` | Desktop SSE lifecycle coordinator, background thread management, and leave cancel gate integration. |
| `scripts/admin-announcements-smoke.mjs` | Node.js smoke runner & self-test | Automated verification of contract-v1.json, 13 golden vectors, idempotency, and in-process mock server (21/21 PASS). |

---

## 3. Repomix Compaction Metrics (Generated: 2026-10-18)
- **Compaction Artifact:** `repomix-output.xml` at repository root; generated with `repomix --style xml --compress` (default broad scan).
- **Included Files:** 610 files; 6 security-scanner exclusions.
- **Packed Size:** 6,718,802 characters and 1,647,198 tokens.
- **Scope:** Default broad scan including source, tests, scripts, configuration, and documentation.
- **Top 5 Files by Token Count:**
  1. `docs/assets/tabbyrush-app-icon.svg` (200,502 tokens, 296,208 chars)
  2. `shared/gradle.lockfile` (56,057 tokens, 314,065 chars)
  3. `gradle/verification-metadata.xml` (45,214 tokens, 138,636 chars)
  4. `docs/project-roadmap.md` (32,770 tokens, 141,655 chars)
  5. `docs/system-architecture.md` (25,519 tokens, 114,482 chars)
- **Security-Scanner Exclusions (6):**
  1. `docs/security-and-recovery.md`
  2. `plans/reports/code-reviewer-261014-1624-phase-08-release-operations-cycle2.md`
  3. `plans/reports/code-reviewer-261014-1638-phase-08-release-operations-cycle3.md`
  4. `plans/reports/docs-manager-261014-1643-phase-08-docs-verification.md`
  5. `plans/reports/docs-manager-261016-0050-phase-12-cutover-runbook.md`
  6. `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/endpoint-policy-test.kt`
- **Security Boundary:** Repomix security scanner output is an automated heuristic filter, not a formal security assessment or substitute for source review.

## 4. Current State & Invariants

1. **Deliverable Scope & Verification**:
   - **Phase 01 (Headless Tooling)**: Automation scripts, Xpra display isolation, and technical documentation completed and verified via shell suite (31/31 tests PASS).
   - **Phase 02 (Frozen Foundation & Contracts)**: Root Gradle configuration, staged assembly guards, version catalog, shared module, domain contract source files, and test suites completed and verified via shell suite (57/57 tests PASS) and `:shared:jvmTest` (24/24 contract tests PASS).
   - **Phase 03 (Hub Reuse & Strict Server)**: Backend strict synchronization server, legacy isolation fences, admission preflight, schema integration, and operator tooling implemented across 26 files (6 in TabbyRush, 20 in sibling `glean-hub-sync`). Unit and route tests passing (36 passed, clippy clean). Live Mongo integration suites deferred to Phase 07.
   - **Phase 04 (Hub Authentication & Protected Session)**: Authentication client, HTTP engine abstraction, session state machine, generation fencing, single-flight refresh, and Android Keystore vault implemented across 14 files. 19 unit tests passing under `:shared:jvmTest`. Android Keystore instrumented test deferred to Phase 07.
   - **Phase 05 (Durable Local State & Strict Per-Record Sync)**: Room KMP database (v1), 5 Room entities, atomic DAO transactions, BundledSQLite driver, platform account files, staged full scan, FIFO outbox reconciliation, CAS ack matching, UnknownSuperseded disambiguation, crash recovery, and fail-closed account leave protocol implemented across 29 files. 89 common and JVM unit tests pass via `./gradlew --dependency-verification=lenient :shared:jvmTest`. Android instrumented Room tests and live Hub e2e sync deferred to Phase 07.
   - **Phase 06 (Shared Compose UI & Accessible Light Theme)**: Shared Compose UI composables, responsive compact (<840dp) and expanded (>=840dp) layouts, light theme tokens with verified WCAG 2.1 AA contrast ratios, transient masked password handling, system font fallback (`FontFamily.SansSerif`), open source license viewer, and 7 unit tests in `TodoSemanticsTest` implemented across 15 files (5 conditional font/license files omitted per provenance audit). 96 :shared:jvmTest tests pass under lenient dependency verification (strict verification fails on `verification-metadata.xml`). Marked complete administratively; technical review 6.5/10 NOT ACCEPTED with documented integration gaps deferred to Phase 07 (rendered UI automation with `compose.uiTest`, `AccountIdentity.isAdmin` contract amendment, 200% font scale device verification, and string localization).
   - **Phase 07 (Android Composition & Runtime Integration)**: Native Android application module delivered in source (14 files in `androidApp/`, 2 in `scripts/`, `docs/android-acceptance.md`, `docs/evidence/android-test-summary.md`, and 7 visual design mockups in `docs/evidence/android-debug-*.png`). JVM shared regression suite 100% green (96/96 tests PASS across 14 test classes). Tooling scripts syntax verified (`bash -n scripts/android-smoke.sh`, `node --check scripts/debug-fault-proxy.mjs`). Android bytecode compilation (`:androidApp:compileDebugKotlin`) and instrumented test execution (`connectedDebugAndroidTest`) are blocked on the host by absent Android SDK (`ANDROID_HOME`), unrun on device/emulator, and deferred to Phase 08 release operations.
   - **Phase 08 (Authorized Hub Operations & Signed Android Release)**: Operational tooling, release security auditor (`scripts/release-security-check.mjs`), live strict smoke runner (`scripts/hub-live-smoke.mjs`), mongosh recovery script (`scripts/hub-strict-recovery.js`), deployment guide, security & recovery architecture, release acceptance specification, and evidence reports implemented across 20 artifacts. Tooling & harnesses operational; live bytecode compilation, binary signing verification, and live device acceptance gated on host SDK provisioning.
   - **Phase 09 (Linux Desktop & Installed RPM Distribution)**: Native Linux desktop application module (`:desktopApp`), Secret Service vault SPI (`CredentialVaultProvider`), ServiceLoader dispatcher (`PlatformVaultDispatcher`), `SingleInstanceGuard` file-channel lock, `DesktopSyncScheduler` foreground sync coordination, packaging smoke runner (`scripts/linux-package-smoke.sh`), runbooks, and test suites implemented across 23 artifacts. 19/19 unit tests PASS across 3 test classes (`PlatformVaultDispatcherTest`, `DesktopLifecycleTest`, `LinuxSecretServiceVaultTest`). Standalone application image assembled and inspected via smoke runner (`scripts/linux-package-smoke.sh --dist`); live GUI execution without host JDK, live RPM packaging, and interactive Secret Service D-Bus sessions remain pending operator prerequisites.
   - **Phase 10 (Windows Desktop & MSI Distribution)**: Windows DPAPI vault and ServiceLoader provider, per-target dependency isolation/lockfile, PowerShell bundle/MSI audit, `TabbyRush.exe` distributable, and unsigned MSI delivered; implementation recorded at **100% (2026-10-14)**. The evidence summary reports 25/25 desktop tests and 96/96 shared JVM tests passing (121/121 combined) and records bundle/MSI metadata checks. Technical acceptance remains blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not guaranteed by the current source, and clean-user runtime/sync plus uninstall/reinstall evidence remain open; the seven PNGs are design mockups, not live screenshots.
2. **Zero Destructive Fallback**: Room database version 1 uses explicit migrations; `fallbackToDestructiveMigration()` is strictly forbidden.
3. **Account Directory Isolation**: Account directories are keyed strictly by canonical `AccountKey` SHA-256 lowercase hex strings; canonical path validation rejects symlinks and directory traversal attempts outside the base directory.
4. **FIFO Outbox & Head Blocking**: Outbox operations dispatch at most one operation per `rowId` in FIFO order (`localRevision`). A head operation in `DISPATCHING`, `CONFLICT`, or `UNKNOWN_SUPERSEDED` state blocks all successors on that row from dispatching.
5. **Exact CAS-Ack Matching**: Push acknowledgements match on `opId`, `rowId`, `digest`, status in `[accepted, replayed]`, and `acceptedRevision == expectedRevision + 1`. Response omission or mismatch transitions the op to `UNCERTAIN` without advancing or regressing confirmed base.
6. **Newer Draft Preservation**: Acknowledging an outbox mutation deletes `editor_drafts` only if `draft.localRevision <= outbox.localRevision`; newer local edits are preserved.
7. **UnknownSuperseded Disambiguation**: A conflict returned for an operation that had previous uncertain delivery history is classified as `UNKNOWN_SUPERSEDED` rather than plain `CONFLICT`, acknowledging that the mutation may have committed before a subsequent edit bumped the revision.
8. **Crash Recovery**: Interrupted dispatches (`DISPATCHING`) are reset to `UNCERTAIN` on startup. Push coroutine cancellation uses `NonCancellable` to ensure in-flight batches are marked `UNCERTAIN`.
9. **Fail-Closed Teardown Sequence**: Teardown checks 5 categories of unresolved work, freezes the store, writes generation and `wipe.marker` outside SQLite before deleting any data, cancels background schedulers, closes database handles, deletes DB files, and clears credentials before removing the marker.
10. **WCAG 2.1 AA Palette Contrast**: Baseline mathematical pairing is verified for 16 opaque token pairs (text >= 4.5:1, interactive outline borders >= 3.0:1); decorative mint is restricted to non-text accents. Rendered contrast, large-font layout, and assistive-technology behavior remain platform acceptance work; the separate sticky-surface Phase 07 does not close these checks.
11. **Transient Masked Password Memory**: Password fields configure `PasswordVisualTransformation`, emit `semantics { password() }`, and dereference transient strings upon submission, mode switch, or disposal; `AuthSubmitAction.toString()` masks passwords with `[REDACTED]`. (JVM string immutability precludes guaranteed memory erasure; device screen-reader behavior unverified).
12. **Non-Color State Conveyance**: Completion, conflict, error, and leave status indicators provide explicit icons, text descriptions, and accessibility state descriptions without relying solely on color.
13. **Responsive Breakpoint Layout**: Single unified UI hierarchy adapts between a vertical compact stack (<840dp) and a fixed-sidebar desktop workspace (>=840dp). The compact top bar is non-scrolling in `Scaffold.topBar`; 200% font-scale and constrained-viewport behavior remain unverified on native devices.
14. **Process Container & Account Runtime Isolation**: `TabbyRushApplication` manages process singletons (`vault`, `accountFiles`, `hubConfig`, `authSession`, `syncScheduler`). `AccountRuntime` encapsulates `TabbyRushDatabase`, `RoomAccountStore`, `AccountSyncCoordinator`, and `AccountLeaveProtocol` bound to an `AccountKey` and generation counter. Foreground UI (`SessionViewModel`) and background work (`TabbyRushSyncWorker`) share the identical runtime instance and coordinator mutex.
15. **Cold-Start Identity Persistence**: Active account key and identity are persisted in app-private `active_account.dat` via temporary file write and `StandardCopyOption.ATOMIC_MOVE` to allow deterministic session restoration on application launch.
16. **WorkManager Account-Tag Cancellation**: All background synchronization tasks are tagged with `tabbyrush_sync_account_<accountKey>`, allowing `cancel(accountKey)` to cancel all one-shot and periodic work across generation increments.
17. **Teardown Quiescence Mechanism**: `AndroidSyncScheduler.cancelAndAwait()` cancels WorkManager tasks by tag, polls WorkInfo completion for up to 5s (throwing `IllegalStateException` on timeout), and polls `coordinatorMutex.tryLock()` for up to 5s (throwing `IllegalStateException` on timeout) before proceeding with teardown.
18. **Fail-Closed Startup Wipe Recovery**: `completePendingWipesOnStartup()` executes synchronously in `Application.onCreate` before any account or database operations begin, cancelling scheduled work, deleting database files, clearing vault credentials, and removing `wipe.marker` fail-closed.
19. **Android Manifest Hardening & Cleartext Policy**: Base manifest enforces `allowBackup="false"` and `usesCleartextTraffic="false"`. Debug manifest permits cleartext traffic strictly for `127.0.0.1`, `localhost`, and `10.0.2.2` via `network_security_config.xml`. Merged manifest inspection across dependencies is pending AGP compilation. Release builds fail closed via task graph enforcement when signing credentials or keystores are missing.
20. **Visual Design Baselines**: The 7 PNG evidence images in `docs/evidence/` provide pixel-precise visual design specification mockups across core states (empty, populated, completed, editor, error, conflict, access-pending); live device framebuffer screen captures are pending interactive device/emulator execution in Phase 08.
21. **Phase 08 Release Operations & Disaster Recovery Tooling**: Delivered 3 operational tooling scripts (`hub-live-smoke.mjs` with `--fixture-test` self-test passing 18/18 checks, `release-security-check.mjs` with fail-closed release checking and `--diagnose-source` static validation, and `hub-strict-recovery.js` with maintenance quiescence assertion, fresh UUID epoch allocation, and journaled registry CAS), comprehensive operational documentation (`deployment-guide.md`, `security-and-recovery.md`, `release-acceptance.md`, `project-changelog.md`, `README.md`), evidence summaries (`android-release-summary.md`, `hub-recovery-summary.md`), and 7 release UI visual specification baselines in `docs/evidence/android-release-*.png`.
22. **Operational Status & Verification Boundaries**: Scripts and runbooks have been validated for syntax, fail-closed parameter validation, and deterministic offline mock fixture execution. Live release operations (production APK compilation, apksigner verification, live device installation, and live Hub staging) remain gated on host Android SDK provisioning and operator deployment. In addition, Phase 07 runtime correctness defects (teardown generation rollback, cancellation swallowing, and worker TOCTOU) and Phase 08 mongosh BSON Int64 serialization prerequisites remain documented open release-blocking items prior to production authorization.
23. **Desktop Target Isolation & Custom Dependency Locking**: Desktop build detects Linux/Windows host targets and rejects mismatches before dependency resolution. Linux D-Bus and Windows JNA dependencies are target-specific; `gradle-linux.lockfile` and `gradle-windows.lockfile` preserve separate resolution graphs. Package inspection reports each target contains its platform provider and excludes the foreign provider/runtime classes.
24. **ServiceLoader SPI Provider Resolution**: Hardware/OS vaults are decoupled behind the `CredentialVaultProvider` SPI; `PlatformVaultDispatcher` requires exactly one provider matching the active desktop target and fails closed with an explicit exception if zero or duplicate providers exist. Plaintext token file fallback is strictly forbidden.
25. **Single-Instance Profile File Lock**: `SingleInstanceGuard` acquires an exclusive OS file-channel lock on `~/.local/share/tabbyrush/accounts/tabbyrush.lock` before initializing database or vault; prevents concurrent launch, refresh races, and database corruption. The lock file is retained on exit to prevent inode recycling race conditions.
26. **Foreground Desktop Sync Quiescence**: `DesktopSyncScheduler` coordinates periodic 15-minute and immediate sync within the window coroutine scope; implements `LeaveSchedulerCancelGate` to cancel and join active sync jobs before account leave or wipe.
27. **Immutable Build Modes & Launchers**: `MainKt` enforces `BuildMode.RELEASE` (HTTPS only, loopback denied); `DevelopmentLauncher` enables `BuildMode.DEBUG` (HTTP 127.0.0.1:3000 permitted) via `:desktopApp:runDebug`.
28. **Standalone Distributable & Disclosed Packaging Gates**: Standalone unpacked application image assembled and statically inspected (`scripts/linux-package-smoke.sh --dist`); live GUI execution without host JDK, native RPM packaging (`:desktopApp:packageRpm`), system installation, and live Secret Service D-Bus sessions remain pending host `rpm-build` installation, interactive sudo, and an active desktop session. Visual evidence images in `docs/evidence/linux-*.png` represent high-fidelity design specifications.
29. **Windows User-Scoped Credential Custody**: `WindowsDpapiVault` uses JNA `CryptProtectData` / `CryptUnprotectData` with UI prompts forbidden and current-user scope; machine-wide protection and plaintext token fallback are not used.
30. **Windows Credential File Replacement**: DPAPI-protected JSON credential pairs are written to a temporary file and atomically moved into place; plaintext byte buffers are cleared after cryptographic operations. The implementation attempts an owner ACL but suppresses ACL-view/owner errors, so ACL enforcement is not guaranteed when unavailable or failed.
31. **Windows MSI Provenance**: The recorded MSI is unsigned; its hash/metadata inspection does not establish Authenticode trust or successful interactive installation.
32. **Windows Acceptance Status**: Phase 10 implementation is recorded at 100% (2026-10-14), but technical acceptance is blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not fail-closed and clean-user runtime/sync plus uninstall/reinstall evidence remain open. The seven Windows PNGs are design mockups; they are not live application captures.
33. **Phase 01 Temporal Model & Strict Transition (Delivered Through Phase 05)**: Contract, binary digest framing, and transition design frozen on 2026-10-15 (Gate G1 approved); Room v1->v2 evolution delivered in Phase 05 defaulting migrated records to null deadlines and disabled rules. Strict-v2 wire format introduces 12 fields (16 total columns) with canonical JSON schema fingerprints `3361a80f...` (v1) and `5f539551...` (v2). Binary SHA-256 preimages and checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) pass across recorded test suites in Kotlin (:shared:jvmTest), Rust (`strong_sync_v2_tests`), and Node.js (`strict-v2-vector-runner.mjs`). Wire validation rejects non-null interval when `reminderCount == 1` and requires interval >= 15m for repeating count 2..10. Subsequent client sync transition in Phase 06 will address unprovable legacy outbox operations (`UNKNOWN_SUPERSEDED`), intent-masked safe rebasing over remote v2 base, and non-destructive legacy reconciliation.
34. **Strict DST Gap and Overlap Resolution**: `StrictTimeZoneResolver` rejects non-existent spring-forward gap instants (`IllegalArgumentException`), requires explicit offset disambiguation for fall-back overlap hours, and calculates calendar day boundaries with calendar arithmetic (23h/25h), never fixed +24h additions. (Reported 7/7 tests passing in `:shared:jvmTest` under `--dependency-verification=off`).
35. **Non-Exported Android Alarm Receiver**: `TabbyRushAlarmReceiver` declares `android:exported="false"` in `AndroidManifest.xml` and validates identity-only intent extras (`accountKey`, `generation`, `taskId`, `scheduleToken`, `occurrenceIndex`; zero user text in OS payload) and asserts direct-boot unlock, absence of wipe markers, generation alignment, and active account matching before dispatching offline reminders via `ReminderSchedulingCoordinator` under `ReminderDeliveryGate`.
36. **Decoupled Mobile Reminder Capabilities**: `AndroidReminderCapabilitiesChecker` decouples runtime `POST_NOTIFICATIONS` permission from `SCHEDULE_EXACT_ALARM` exact alarm permission (`AlarmManager.canScheduleExactAlarms()` on API 31+).
37. **Safe Desktop Notification Absence & Windows Stub**: `DbusLinuxNotificationAdapter` maps `DBusExecutionException` / `ServiceUnknown` to `LinuxNotificationStatus.Unavailable` without crashing. Windows candidate adapter `WindowsNativeReminderAdapter` is delivered, but its underlying `JnaWindowsToastBridge` explicitly returns `isAvailable() = false` and `WindowsNotificationStatus.Unavailable` pending native WinRT/COM implementation and Windows testbed deferred to Phase 11.
38. **Bounded Coordination Permits & Atomic CAS Gate**: Strict sync data operations acquire an identifiable permit in `_coordination` (`permit:<appId>`), enforcing a strict 1,000 active permit ceiling (`MAX_CONCURRENT_PERMITS`) via atomic CAS update checking `fenced: false` and `permitCount: { $lt: 1000 }` with `$inc: { permitCount: 1 }`. Release operations verify the permit ID exists in `activePermits` before decrementing.
39. **In-Place Strict Migration State Machine & Resumption**: Strict upgrade transitions through an 8-state machine (`ready-v1` -> `fencing` -> `transforming` -> `validating` -> `publishing` -> `ready-v2`, `operatorRequired`, or `rolledBack`) under an exclusive coordination fence. Resuming interrupted migrations skips the initial purity preflight, passes prior recorded counts, and updates `_schemas` via CAS with majority journaled write concern; exact cumulative accounting across mid-batch crash interruptions remains an open correctness item awaiting formal revalidation.
40. **Strict-v1 Protocol Validation on Legacy Receipts**: Legacy receipt reconciliation (`POST /api/v1/sync/:app_id/strong/legacy-receipts`) enforces bounded batches (`<= 100`) and strictly validates `request.protocol == "strict-v1"`. Matching receipts execute durable replay confirmation nonces without mutating payload records or applying legacy mutations.
41. **Phase 03 Hub Versioned Admission & Staging Gate Status**: Phase 03 backend contracts, admission guards, and in-place migration engine are delivered in `glean-hub-sync` with passing unit/contract checks. Production admission remains on hold: (1) Gate G3 live multi-instance Mongo replica-set acceptance is unexecuted pending staging testbed, (2) coordination-document compatibility/backfill for documents missing `permitCount` requires live verification, and (3) exact cumulative accounting across mid-batch crash interruption requires formal revalidation.
42. **Phase 04 Hub Admin UI and API-Only Upgrade Tooling**: Operator tooling is delivered across both Glean Hub Web Admin UI (`glean-hub-app`) and TabbyRush CLI runner (`scripts/hub-strict-upgrade.mjs`). The CLI runner enforces precondition verification, preflight dry-run checks, same-migration-ID resumption upon network interruption, fail-fast on 4xx rejections, complete token redaction, bounded polling, and mandatory final read-back assertions against `GET /strict-contract` verifying `strict-v2` mode, canonical SHA-256 fingerprint (`5f539551...`), and 16 table columns. The Web Admin UI guards strict apps against arbitrary schema PUT requests, checks app-scoped authorization, persists active migration IDs in `sessionStorage`, and asserts final schema read-back. Tooling is verified via unit test suites (`hub-strict-upgrade.test.mjs` 7/7 pass, `glean-hub-app` 13 unit tests across 2 suites pass: 8 in `sync-admin-service.test.ts` and 5 in preload recovery, `pnpm build` clean); production admission remains strictly on hold pending Gate G3 live multi-node replica-set staging validation and remediation of Phase 03 backend findings (`permitCount` coordination backfill, mid-batch crash resumption consistency, and duplicate permit release filtering).

43. **Phase 05 Shared Task Model & Room v2 Migration (Gate G4 Storage Migration Delivered)**: Delivered on 2026-10-15. Room database version 2 transitions from schema 1 via explicit step-by-step migration `DatabaseMigrations.MIGRATION_1_2` without destructive fallback, registered on JVM and Android builders via `DatabaseMigrations.ALL`. 7 entities: `server_base`, `outbox`, `editor_drafts`, and `scan_staging` expanded with 12 temporal columns; `outbox` augmented with `contractVersion`, `dispatchHistory`, `intentMask`, and `transitionCheckpoint`; plus new entities `AccountLocalSettingsEntity` (singleton local reminder/notification defaults) and `ReminderDeliveryLedgerEntity` (5-tuple PK for durable native reminder claims and cancellation). Shared models `TaskDeadline`, `ReminderRule`, and `Todo` enforce schedule token presence and checked 64-bit arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) guaranteeing `last <= anchor`. Verified via 128 JVM unit/integration tests (`:shared:jvmTest`), 23 strict-v2 vector checks, 18 offline smoke-fixture self-checks (`hub-live-smoke.mjs --fixture-test`), and 6 static source security diagnostics (`release-security-check.mjs --diagnose-source`), with 0 failures in single test execution. Client transition and replay logic delivered in Phase 06 with reported JVM/offline validation; Android instrumented execution (`:shared:connectedAndroidTest`) and live mixed-version acceptance remain pending Phases 08 and 11, and production admission remains on hold.

44. **Phase 06 Client Version Transition, Safe Replay & Intention Preservation (Gate G4 Logic Delivered)**: Delivered on 2026-10-15. `ContractTransitionCoordinator` implements an orderly, crash-resilient 7-state / 6-transition state machine (`ready-v1` -> `upgrade-detected` -> `dispatch-quiesced` -> `legacy-reconciling` -> `v2-base-scanning` -> `safe-intents-translating` -> `ready-v2`) with durable SQLite checkpoints in `AccountSyncMetaEntity.syncStatus` (`"TRANSITION:<phase>"`) and `safeErrorCode = "CHECKPOINT_<PHASE>"`. Key delivered capabilities: exact legacy receipt reconciliation via `POST /api/v1/sync/{appId}/strong/legacy-receipts` under `RouteClassification.DUAL_AUTH_STRICT`, bounded batches (`<= 100`), receipt omission defaulting to `UNCERTAIN` (never assumed non-delivery or auto-replayed); non-destructive intent-masked rebasing in `RoomAccountStore.reconcileAccepted()` preserving remote temporal metadata from `currentBase` on legacy or partial edits; atomic transactional cleanup on regressive server revision via `TabbyRushDao.discardRegressiveReconcileAtomic()`; migration-mode equal-revision temporal enrichment in `StagedFullScan` (`isMigration = true`); transactional safe intent translation of provably unsent legacy v1 operations with conservative review gating (`INTENT_REQUIRES_REVIEW` failing closed to `STATE_CONFLICT`); cross-version server regression prevention (`countOutboxByVersion`); and WCAG 2.1 AA accessible conflict warnings in `ConflictReviewDialog` (`isUnknownSuperseded` coral banner) and `VersionCard`. Total 243 executed checks passing with 0 failures (reported in `plans/reports/qa-261015-1815-phase-06-client-transition-and-replay.md`, executed with `--dependency-verification=off`) across shared JVM (155 tests across 20 classes), desktop (32 tests across 6 classes), strict-v2 vectors (23/23), live smoke fixture (18/18), security diagnostics (6/6), and upgrade CLI (9/9). Code review completed with 0 critical/high issues (`plans/reports/code-review-261015-1830-phase-06-client-transition-and-replay.md`). Gate G4 offline-client upgrade and replay logic are delivered; connected Android instrumented execution (`:shared:connectedAndroidTest`), connected platform notification delivery, and live mixed-version multi-device cutover remain scheduled for Phase 08–11.

45. **Phase 07 Shared Scheduling, Persisted Eligibility and Account Safety (Gate G5 Passed)**: Delivered on 2026-10-15. Implements `ReminderScheduleCalculator` (checked 64-bit arithmetic, 1..10 repeat bounds, strict 300,000ms grace window), `PersistedScheduleProjector` (draft-free reactive projection, conflict head suppression, completion/delete cancellation barriers), `ReminderOccurrenceStore` (durable ledger lifecycle, atomic claim-before-post, task reopen preservation), `ReminderDeliveryGate` (three-tier lock hierarchy, fail-closed callback admission, teardown drain synchronization), `ReminderSchedulingCoordinator` (per-account coordination, mutation-gated alarm reconciliation, pre-post re-check), and `AccountLeaveProtocol` enhancements (fail-closed wipe marker retention on scheduler cancellation failure, startup wipe recovery). Verified via 270 automated checks with 0 failures, including `:shared:jvmTest` (182 unit/SQLite tests PASS).

46. **Phase 08 Android Alarms, Private Notifications and Restoration (Gate G2 Logic Delivered)**: Delivered on 2026-10-15. Delivers native Android reminder scheduling, private notification delivery, and device restoration receivers:
    - `AndroidNotificationAdapter`: Native adapter managing `tabbyrush_reminders` high-importance channel with `VISIBILITY_PRIVATE`, generic public fallbacks (`VISIBILITY_PUBLIC`, text: "Task reminder"), exact alarms via `AlarmManager.setExactAndAllowWhileIdle` (falling back to `setAndAllowWhileIdle`), and notification clearing.
    - `AndroidAlarmRegistry`: Synchronized SharedPreferences storage (`tabbyrush_alarms_<accountKey>`) tracking active alarm keys (`generation:taskId:scheduleToken:occurrenceIndex`) and active notification IDs via synchronous `commit()` writes.
    - `TabbyRushAlarmReceiver`: Non-exported (`android:exported="false"`) receiver handling explicit `ACTION_DELIVER_REMINDER` intents. Checks `UserManager.isUserUnlocked`, validates identity-only extras (no user text in alarm PendingIntent extras or URIs), checks wipe markers/generations, and executes bounded delivery under `goAsync()` with a 9,000ms cooperative timeout.
    - `ReminderRestoreReceiver`: Non-exported receiver handling system events (`BOOT_COMPLETED`, `USER_UNLOCKED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`). Enforces direct-boot barrier and wipe-first ordering (`completePendingWipesSuspend()`) before reconciling active account reminders.
    - `CompositeLeaveSchedulerCancelGate`: Coordinated 4-stage teardown: (1) delivery gate drain -> (2) schedule observer cancel -> (3) WorkManager sync cancel -> (4) AlarmManager/notification cancel.
    - `ReminderPermissionController`: Sealed capability hierarchy (`Exact`, `BestEffort`, `NotificationDenied`, `ChannelDisabled`) and settings intent factories.
    - Cold-Start Deep Linking: `MainActivity` routes `ACTION_VIEW_TASK` click intents to `SessionViewModel.selectTaskFromNotification`, buffering targets in `pendingTarget` until auth and todos load, then validating account matching and wipe status before opening task editor.
    - Automated Verification: Total 282 executed checks passed with 0 failures across reported test suites (12 Android unit tests in `:androidApp:testDebugUnitTest`, 182 shared JVM tests in `:shared:jvmTest`, 32 desktop tests in `:desktopApp:test`, 23 strict-v2 vectors, 18 offline smoke-fixture self-checks in `hub-live-smoke.mjs --fixture-test`, 9 CLI tests, and 6 static source security checks; `:androidApp:assembleDebug` APK assembly verified separately as a passing build artifact).
    - Gate G2 Status: Logic and native adapters delivered; connected physical device / emulator execution, Doze mode testing, live broadcast receipt across reboot, and platform acceptance remain formally pending Gate G2 / Phase 11.
47. **Phase 09 Process-Scoped Desktop Scheduler, Native Adapters & Strict Quiescence (Gate G2 Desktop Logic Delivered)**: Delivered on 2026-10-15. Implements process-scoped reminder scheduling, ServiceLoader SPI provider discovery, native desktop adapters, and composite leave cancel gate:
    - `PlatformReminderDispatcher`: singleton resolving `DesktopReminderAdapterProvider` SPI implementations by target platform (`linux` or `windows`), enforcing exactly one provider per target and failing closed with `IllegalStateException` on missing or duplicate providers.
    - `DesktopReminderScheduler`: manages reminders while application window is visible or minimized. Bounded 15s wake loop (`withTimeoutOrNull`) on `Dispatchers.IO` provides bounded re-evaluation against backward clock drift without polling; conflated channel (`Channel.CONFLATED`) handles replace-based desired alarm updates; due reminders claim through `ReminderSchedulingCoordinator.deliverReminder()` with SQLite 5-minute grace window claim check before native posting.
    - `CompositeDesktopLeaveCancelGate`: coordinates strict 5-stage ordered quiescence during account leave (`AccountLeaveProtocol`): (1) closes `ReminderDeliveryGate` and drains in-flight delivery admissions with a 5,000ms timeout (fails closed with `IllegalStateException` on timeout), (2) cancels schedule observer job, (3) cancels `DesktopReminderScheduler` wake loop, (4) cancels `DesktopSyncScheduler` sync jobs, and (5) cancels platform notifications.
    - `LinuxNativeReminderAdapter`: bridges `org.freedesktop.Notifications` over `Dispatchers.IO`; probes daemon capabilities with tolerance for empty capability lists on minimal daemons (dunst, mako); tracks notification IDs in process memory; aggregates close errors on `cancelAllForAccount()` to throw `IllegalStateException`.
    - `WindowsNativeReminderAdapter`: candidate adapter with AUMID `dev.tabbyrush.desktop`, group `d_${accountKey.value.take(12)}`, and tag `t_${taskId.take(16)}_$occurrenceIndex` with XML escaping. Clears group and tag notifications rather than global toast wipe. `JnaWindowsToastBridge` remains an unintegrated candidate stub returning `WindowsNotificationStatus.Unavailable`; real COM/WinRT native delivery is deferred to Phase 11.
    - `DesktopController` Integration: in `completePendingWipesOnStartup()`, notification cancellation errors trigger `return@forEach`, retaining `wipe.marker` and database files fail-closed. On window close, `dispose()` executes `disposeSuspend()`, cooperatively cancelling observers, draining runtime gates, closing database files, and releasing `SingleInstanceGuard` file lock with no background daemon or tray process left running.
    - Limitations & Residual Risks: In-memory tracking cannot discover or cancel active notifications across process restarts (retaining wipe markers does not clean up orphaned OS banners from prior sessions); individual removals drop local tracking without verifying native close status; truncated Windows identifiers (12-char account key, 16-char task ID, missing generation/token) pose collision risks across multiple accounts or long IDs; ordinary window close cooperative disposal catches exceptions to ensure clean exit.
    - Verification: QA and code review reports record 298 passing automated checks across 7 test/check groups (48 Linux/JVM desktop tests in `:desktopApp:test`, 182 shared JVM tests, 12 Android unit tests, 23 strict-v2 vectors, 18 fixture smoke checks, 6 static security rules, 9 CLI tests) plus 1 separate packaging assembly build (`:desktopApp:createDistributable`). Cycle 2 code review approved at 8.9/10 with qualifications; Gate G2 physical desktop notification bubble display and installed Windows MSI execution remain pending Phase 11.
48. **Phase 10 Shared Deadline Editor, Task List & Local Settings (Delivered)**: Delivered on 2026-10-15. Implements pure Compose multiplatform UI for task deadlines, reminder rules, platform settings, and conflict inspection across Android and Desktop:
    - `DeadlineCalculatorHelpers`: Pure immutable calculation engine (`validateAndComputeDeadline`, `computeNextCalendarDayStartEpochMs`, `computeCalendarDayStartEpochMs`). Resolves local dates and times via `StrictTimeZoneResolver`. Explicitly separates DST gaps (non-existent local times rejected with validation error) from DST overlaps (ambiguous times resolved via explicit offset selection). Defensive date parsing catches `IllegalArgumentException` on impossible calendar dates (e.g. Feb 30/31, April 31, non-leap Feb 29) to prevent Compose recomposition crashes.
    - `DeadlineEditorSection`: Shared Compose UI component providing date input, time toggle, explicit timezone and offset disambiguation filter chips, reminder rule enablement, lead minutes picker, repeat interval and count bounds (1..10, count > 1 requires interval >= 15m, non-repeating interval null, last occurrence <= anchor), and live calculated reminder opportunity preview.
    - `TodoEditor`: Integrates `DeadlineEditorSection` into task creation/editing dialog. Guards strict-v2 accounts: if account is strict-v1, `isUpgradeBlocked` disables saving deadlines with a clear Hub admin upgrade requirement message while preserving title and completion editing.
    - `TodoList` & `TodoRow`: Displays date-only and timed deadlines. Overdue status is conveyed via non-color semantics: explicit "Overdue" text badge, distinct background styling, and accessibility `contentDescription = "Task is overdue"`. Date-only tasks at anchor 09:00 do not become overdue during that morning; overdue boundary is strictly the start of the next calendar day in the captured timezone. Completed tasks never render as overdue. Re-evaluates on periodic 15s `ClockTick` events without SQLite mutations or network sync.
    - `ReminderSettingsDialog` & `AccountLocalSettingsEntity`: Manages device- and account-local reminder defaults (enabled, lead, interval, count, date-only anchor defaulting to 09:00) and displays platform reminder capabilities card (`Exact`, `BestEffort`, `NotificationDenied`, `ChannelDisabled`). Settings changes update local preferences without rewriting existing stored tasks.
    - `ConflictReview`: Extended comparison card displaying full side-by-side comparison of local vs remote deadline date, time, zone, offset, reminder rule parameters, and schedule-token preview.
    - `RoomAccountStore` Temporal Projection Refactoring: Refactored `resolveTemporalProjection` so local drafts and outbox mutations take precedence as a complete snapshot unit over `server_base`. Explicit null deadline fields in local state are treated as an intentional clearing of the deadline rather than falling back field-by-field to `server_base` (verified by `testClearingDeadlineToServerBaseDoesNotResurrect`).
    - Schedule Token Semantic Rotation: `saveTodoLocally` enforces `hasScheduleSemanticChange()`. Editing task title or description preserves the existing `scheduleToken`; editing deadline date, time, zone, offset, or reminder rule generates a new UUID `scheduleToken`. (Task completion acts as an independent cancellation barrier in `PersistedScheduleProjector`).
    - Platform Controller Integrations: `SessionViewModel` on Android routes notification deep links through `openEdit(task)` ensuring full deadline hydration, preventing accidental schedule wipe on edit; emits 15s `ClockTick` flow. `DesktopController` on Desktop coordinates settings actions, submit validation, and 15s `ClockTick` flow.
    - Verification: QA test execution report records 306 automated test/check assertions across 7 test suites, plus 2 packaging assembly builds (`:desktopApp:createDistributable` & `:androidApp:assembleDebug`), totaling 308 checks (189 shared JVM tests across 26 classes, 49 desktop tests across 10 classes, 12 Android unit tests across 4 classes, 23 strict-v2 vectors, 18 fixture smoke checks, 6 static security checks, 9 CLI tests). Code review added 1 regression test (`testDeadlineValidationImpossibleCalendarDates`), raising shared JVM tests to 190 and total checks to 309 when executed, with a quality score of 9.2/10 approved. Connected physical device/emulator execution, TalkBack/Orca accessibility verification, live notification delivery, and native Windows toast bridge remain pending Phase 11 platform acceptance.
49. **Phase 11 Adversarial Integration and Platform Acceptance Boundaries (Gate G6 Logic Delivered / Deployment Held)**: Delivered on 2026-10-15. Implements adversarial scheduler boundary tests, file-backed SQLite close/reopen persistence, fault-injection proxy hardening, and packaging verification:
    - `AdversarialSchedulerBoundaryTest`: Multiplatform test suite (7 tests in `dev.tabbyrush.shared.contract`) verifying repetition bounds (1..10, count > 1 requires interval >= 15m), checked 64-bit integer arithmetic overflow prevention, date-only 09:00 anchor vs next-day start overdue boundary (tested across DST fall-back transitions via production `DeadlineCalculatorHelpers.validateAndComputeDeadline`), strict 5-minute grace window compliance (due + 300,000ms eligible, 300,001ms skipped, no retroactive burst delivery), uncommitted editor draft isolation (`editor_drafts` excluded from projection), completion/deletion cancellation barriers, and schedule token preservation across non-temporal edits and rotation on reminder edits.
    - `AdversarialSyncSqliteTest`: BundledSQLite disk persistence test suite (5 tests in `dev.tabbyrush.shared.data`) verifying state survival across physical database close and reopen cycles against local files (distinct from abrupt OS process kill or power-loss fault models): restart durability of outbox and confirmed base, frozen retry idempotence across network timeouts, strict-v1 title-only rebase over strict-v2 remote deadline via intent masking, and `UNKNOWN_SUPERSEDED` conflict locking.
    - Loopback Fault-Injection Proxy Hardening: `scripts/debug-fault-proxy.mjs` and `scripts/debug-fault-proxy.test.mjs` (6 tests) providing route allowlisting (`/push`, `/pull`, `/legacy-receipts`), client socket termination on dropped responses (`drop-push-response`, `drop-legacy-receipts-response`), and complete suppression of sensitive URL path strings from 403 rejection logs.
    - Projector Token Fold Bug Remediation: In `persisted-schedule-projector.kt`, assigned `scheduleToken = op.scheduleToken` in the outbox fold under `INTENT_REMINDER`, eliminating scheduler desynchronization on reminder-only modifications.
    - Automated Verification: Total 397 reported verification targets across 14 verification groups (393 automated test and contract checks: 392 passed, 0 failures, 1 ignored live Mongo test: `test_strong_sync_live_mongo_acceptance_gate` in `glean-hub-sync` when `MONGO_TEST_URI` is unset; plus 4 build, lint, and packaging targets passed). Both packaging targets passed (`:desktopApp:createDistributable`, `:androidApp:assembleDebug`). Code review approved at 8.6/10 (`plans/reports/code-review-261015-2345-phase-11-adversarial-acceptance.md`).
    - Platform Accounting & Honest Boundaries: Gate G6 logic delivered; end-to-end multiplatform acceptance (Gate G2 connected Android device Doze timing, live desktop D-Bus visual notification display, Windows COM/WinRT toast bridge) and live cluster execution (Gate G3 staging replica set and 3 open Phase 03 code findings) remain formally held pending respective implementation/remediation and testbed acceptance.
50. **Phase 12 Coordinated Cutover Runbook, Rehearsal Tooling & Release Documentation (Gate G7 Runbook Delivered / Production Cutover Formally Blocked)**: Delivered on 2026-10-16. Implements authoritative cutover and recovery runbooks, operator tooling rehearsal harness, CLI security hardenings, legacy script quarantine, and cross-repository operational documentation reconciliation:
    - `docs/strict-v2-cutover-runbook.md`: Authoritative operational guide establishing preflight conditions, app-scoped admin JWT authorization, credential custody (environment parameters, never bare command-line `argv`), restorable journaled MongoDB oplog backups, and strict chronological deployment order (all-node Hub binary deploy -> Room v2 TabbyRush client distribution -> non-mutating preflight dry-run -> controlled in-place upgrade with durable migration UUID -> mandatory 16-column/v2-fingerprint contract read-back assertion -> client replay & transition). Defines state-specific incident decision matrix across 6 failure states (pre-fence rejection, orphan permit stalls, interrupted batch idempotent resume via same migration ID, lost publish response status query, preactivation rollback under fence, post-v2 forward repair only).
    - `scripts/strict-v2-cutover-rehearsal.mjs`: Dedicated rehearsal and tooling self-test harness (7/7 passing automated checks) executing the actual `scripts/hub-strict-upgrade.mjs` CLI runner against an in-process mock Glean Hub server. Exercises CLI help, token requirement fail-closed exit, token redaction in stdout/stderr, preflight dry-run non-mutation, interrupted batch recovery using identical migration ID, status-only inspection, and canonical 16-column schema divergence rejection.
    - CLI Hardenings in `scripts/hub-strict-upgrade.mjs`: Strips sensitive authorization tokens from `s.error` logs, validates active epoch invariance in `assertFinalContract()`, and executes complete read-back validation upon detecting that the target contract is already published before exiting 0 (9/9 passing tests in `scripts/hub-strict-upgrade.test.mjs`).
    - Legacy Script Quarantine: `scripts/hub-strict-recovery.js` is documented as quarantined strictly for legacy `strict-v1` emergency cold disaster recovery (allocating fresh UUID epoch and dropping/recreating collection). Its use for `strict-v2` upgrades or rollbacks is strictly forbidden.
    - Operational Documentation Reconciliation: Updated `docs/deployment-guide.md`, `docs/security-and-recovery.md`, `docs/release-acceptance.md`, `docs/system-architecture.md`, `docs/hub-schema-integration.md`, `docs/sync-acceptance.md`, `docs/android-acceptance.md`, `docs/linux-acceptance.md`, `docs/windows-acceptance.md`, `docs/project-roadmap.md`, `docs/project-changelog.md`, `README.md`, and Glean Hub `docs/tabbyrush-strict-sync.md`.
    - Automated Verification: Total 404 reported verification targets across 15 verification groups (400 automated test, contract, and rehearsal checks: 399 passed, 0 failures, 1 ignored live Mongo test: `test_strong_sync_live_mongo_acceptance_gate` in `glean-hub-sync` when `MONGO_TEST_URI` is unset; plus 4 build, lint, and packaging targets passed).
    - Gate G7 Blocker Boundaries: Live production cutover remains formally blocked pending Gate G3 multi-instance MongoDB replica set migration and Phase 03 backend code remediations, Gate G2 physical native platform delivery (Android Doze, Linux session D-Bus, Windows COM/WinRT toast bridge), Gate G6 three-device sync proof, and formal operator authorization.
51. **Sticky Task Surfaces — Phases 01–07**: Shared account-scoped contracts/projections back the Android Glance widget, opt-in quiet deadline notification, and Compose Multiplatform desktop sticky window/tray. Phase 07 delivered documentation and automated verification evidence; 6h / 12h credited, bringing the plan total to 118h / 132h (89.4%).
    - **Reported verification**: 364 Gradle tests (231 shared JVM, 99 desktop, 34 Android unit), 12 Node security tests, 6 source diagnostics, 18 Hub fixture checks, 8 strict-v2 rehearsal checks, `:androidApp:assembleDebug`, and `:desktopApp:createDistributable` are recorded as passing. The build/test runs used `--dependency-verification=off`; these are not fresh results from this documentation update.
    - **Acceptance boundary**: Android launcher/shade and Windows sticky-window/tray/multi-monitor hardware acceptance remain conditionally held at Gate G2. Four PNGs are design baselines, not runtime captures. Linux requires an actual session in Phase 08. The 149-checksum root verification baseline and separate Glance artifact-provenance hold remain open; G3/G6/G7 are unchanged.
    - **Open Android safety findings**: A delivery-gate drain timeout is caught/logged by `closeRuntimeInternal()`, which returns before observer cancellation or Room closure; `closeActiveRuntime()` then clears the active runtime reference. Glance blanking/session-close errors are also caught and logged, so cleanup is not guaranteed.
52. **Server Admin Announcements — Cross-System Implementation & Acceptance Tooling (Phase 12 Delivered)**: Delivered on 2026-10-18. Unidirectional administrative broadcast pipeline spanning Glean Hub and TabbyRush across Android, Linux, and Windows:
    - `P01 Pure Contracts & Vectors`: Canonical `contract-v1.json`, 13 golden test vectors in `vectors-v1.json`, Kotlin multiplatform contracts (`remote-inbox-contracts.kt`), and Rust serde DTOs (`announcement_contract.rs`).
    - `P02 Backend Mongo Storage & Routes`: Canonical MongoDB collections (`admin_announcements`, `admin_announcement_inbox`, `admin_announcement_dispatches`, `notification_install_bindings`) separate from task sync. Enforces `w:1, j:true` journaled parent writes, 90-day retention with BSON DateTime TTL indexes in MongoDB (`expiresAt = createdAt + 90 days`); MongoDB background TTL deletion is asynchronous, while application query and rendering filters strictly exclude records when `expiresAt <= serverNow` (only unexpired records are queryable or renderable); lease-based CAS dispatch workers, and monotonic `readAt` timestamps.
    - `P03 FCM HTTP v1 Push Channel`: Google OAuth2 authenticated transport for Android alerts. FCM HTTP v1 generic notifications for Android with channel `tabbyrush.remote.announcements` and system tag `remote:<messageId>`, maintaining announcement-content isolation from push providers (fixed generic copy "You have a new announcement" and opaque IDs). Automatic token retirement upon `UNREGISTERED` responses.
    - `P04 React Admin Console`: `AnnouncementComposer` with live scalar/byte counters, audience mode selection (`all`, `admins`, `selected` up to 500), preview digest confirmation gating, double-submit protection, and `AnnouncementHistory` with detail modals.
    - `P05 Shared Remote Inbox Controller`: `RemoteInboxController` managing pure in-memory cache (sliding window max 200 items, dedup capacity 2048). Enforces monotonic elapsed expiry filters, safe offline presentation without exposing cached details, and zero Room database persistence or offline read outbox.
    - `P06 Shared Compose UI`: Multiplatform `RemoteInboxScreen` supporting normal in-app entry, native deep linking, unread counter badges, and explicit manual read triggers.
    - `P07 Linux Desktop Adapter`: Connects to session D-Bus `org.freedesktop.Notifications`, checks `actions` capability, maps `ActionInvoked` to UI dispatcher, and catches up silently on reopen.
    - `P08 Windows C ABI v1 & MSI Packaging`: C ABI v1 `tabbyrush-toast.dll`, JNA bridge, and `scripts/build-windows-remote-msi.ps1` WiX3 MSI packaging with `dev.tabbyrush.desktop` AUMID injection.
    - `P09 Android Runtime Integration`: `RemoteNotificationManager` and `RemoteInstallCoordinator` in `:androidApp`. Manifest audit enforces private `FirebaseMessagingService` and `FirebaseInitProvider` with zero analytics receivers. 32-byte install proof encrypted in app-private storage via Android Keystore-backed AES-256-GCM key (`tabbyrush_remote_install_v1`), SHA-256 hashed on Hub, with monotonic numeric generation CAS fencing.
    - `P10 Desktop Runtime Integration`: Streaming SSE broker with `streamEpoch`, monotonic `acceptanceOrdinal`, and ready baseline (`event: ready`). Hints arriving after baseline hydration trigger authenticated detail fetching. Desktop displays at most one generic system alert per active connection and message hint; Android suppresses SSE alerts.
    - `P11 Deployment Preflight & Staged Rollout`: Preflight verification script (`scripts/announcement-preflight.mjs`), non-destructive rollback runbook, and environment variable toggles.
    - `P12 Acceptance Tooling & Evidence`: Standalone Node.js smoke runner (`scripts/admin-announcements-smoke.mjs`) passing 21/21 assertions and 13 golden vectors; Playwright browser suite (`scripts/admin-announcements-browser.spec.ts`); comprehensive acceptance matrix (`docs/evidence/admin-announcements-acceptance.md`).
    - `Acceptance Status`: QA regression report (`qa-261018-1548-c1-c6-regression-verification.md`) records 566 verifications passing (548 test executions across 8 targets + 18 vector/static checks); Cycle 2 code review approved at Score 8/10 with 0 critical issues. Restricted pilot gate approved as recorded in acceptance evidence for internal Android & Linux trials; Gate G5 (Windows Desktop Runtime) formally held on Linux build host. Inherited release holds (G2/G3/G6/G7, 149-checksum baseline, Glance provenance) remain open.