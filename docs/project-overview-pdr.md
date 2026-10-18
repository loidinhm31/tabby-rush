# TabbyRush Project Overview & Product Development Requirements (PDR)

Last Updated: 2026-10-18
Document Version: 1.11.0
Status: Active
Related Docs: [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Project Roadmap](./project-roadmap.md) · [Codebase Summary](./codebase-summary.md) · [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md) · [Release Acceptance](./release-acceptance.md) · [Android Acceptance](./android-acceptance.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Sync Acceptance](./sync-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md) · [Auth Acceptance](./auth-acceptance.md) · [Authentication & Credentials](./authentication-and-credentials.md) · [Linux Desktop Guide](./linux-desktop-guide.md) · [Linux Acceptance](./linux-acceptance.md) · [Windows Desktop Guide](./windows-desktop-guide.md) · [Windows Acceptance](./windows-acceptance.md) · [Announcement Contract](./announcement-contract.md) · [Remote Notification Setup](./remote-notification-setup.md) · [Admin Announcements Acceptance](./evidence/admin-announcements-acceptance.md)

---

## 1. Executive Summary & Product Vision

TabbyRush is an Android-first shared Compose Multiplatform application designed for fast, focused personal task management with multi-device cloud synchronization.

The project prioritizes a clean multiplatform architecture, where the core domain logic, state management, and user interface are planned in Kotlin Multiplatform and Compose Multiplatform:
1. **Android Application (`androidApp/`)**: Native mobile client and sticky task surfaces are implemented. The Phase 07 report records a successful debug APK build; instrumented device and interactive launcher/shade acceptance remain pending the Gate G2 hardware run.
2. **Desktop Application (`desktopApp/`)**: Linux desktop source and a standalone distributable were delivered in Phase 09; RPM installation and a live Secret Service session remain operator acceptance gates. Phase 10 Windows implementation was delivered and recorded at 100% (2026-10-14), including the x64 DPAPI provider, isolated dependency lock, package audit harness, self-contained `TabbyRush.exe` bundle, and unsigned MSI. Technical acceptance remains blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification.
3. **Headless Development Infrastructure**: Immediate development, IDE execution, and emulator testing on headless Fedora Linux servers via secure Xpra virtual display sessions (delivered in Phase 01).

The primary task-management UI is shared Compose Multiplatform. Task surfaces extend it with an Android Glance app widget, an opt-in low-importance deadline notification, and a compact desktop Compose window with a system-tray entry when the desktop session exposes a usable tray. Phase 07 records automated Android/Windows integration evidence and design baselines, not native hardware acceptance; Linux session acceptance remains pending Phase 08.

---

## 2. Product Development Requirements (PDR)

### 2.1 Functional Requirements (FR)

| ID | Requirement | Description |
|---|---|---|
| **FR-01** | Task Lifecycle | Users can create, view, edit title, toggle completion, and delete task items. |
| **FR-02** | Task Filtering | Users can filter tasks by status (All, Active, Completed) with immediate client-side query response. |
| **FR-03** | Aggregate Metrics | The UI displays total, active, and completed task counts dynamically reflecting current filters. |
| **FR-04** | Cloud Synchronization | Client syncs tasks with the backend service according to the contracts frozen in Phase 02. |
| **FR-05** | Local State Handling | Task mutations update local state; pending drafts survive transient network faults. |
| **FR-06** | Secure Authentication | Reuses Glean Hub auth service with single-flight refresh, generation fencing, no automatic POST replays, and platform OS credential custody (Android Keystore on mobile, Freedesktop Secret Service on Linux, and current-user DPAPI on Windows). |
| **FR-07** | Task Deadlines & Reminders | Users can assign optional date-only or timed deadlines to tasks, with captured IANA timezone, explicit DST offset, overdue boundaries, and configurable local reminder rules (lead minutes, count 1..10, interval >= 15m). |
| **FR-08** | Strict-v2 Protocol & Atomic Schema Evolution | End-to-end data synchronization supports versioned `strict-v2` wire contracts with cryptographic preimages, checked 64-bit integer arithmetic, in-place app-scoped migration fencing, legacy receipt durability verification, and safe intent rebasing without data loss. |
| **FR-09** | Sticky Task Surfaces | Share account- and generation-fenced task observation and completion actions across a native Android Glance widget, an opt-in quiet deadline notification, and a compact Compose Multiplatform desktop window with conditional tray controls. Presentation preferences remain local and are not synced as task data. |
| **FR-10** | Server Admin Announcements | Unidirectional administrator broadcasts from Glean Hub with 90-day retention backed by MongoDB BSON DateTime TTL indexes, generic FCM HTTP v1 notifications for Android (channel `tabbyrush.remote.announcements`, tag `remote:<messageId>`), streaming SSE broker with `streamEpoch`, monotonic `acceptanceOrdinal`, and ready baseline, in-memory 200-item sliding cache, no persistent offline content or read outbox, and restricted pilot staged rollout (Android/Linux/Web Admin) with Windows MSI/WinRT held at Gate G5. |

### 2.2 Non-Functional Requirements (NFR)

| ID | Category | Requirement |
|---|---|---|
| **NFR-01** | Performance | Smooth, responsive UI interactions without main-thread blocking during network or disk I/O. |
| **NFR-02** | Security | Zero unauthenticated network listeners; HTTPS-only external transport; encrypted credential storage; no secrets in logs or code. |
| **NFR-03** | Portability | The primary task-management UI is shared Compose Multiplatform across Android and JVM desktop; platform-specific task surfaces use native host APIs while consuming shared account-scoped contracts. |
| **NFR-04** | Maintainability | Strict package modularity, unidirectional data flow, immutable state models, and automated test coverage. |
| **NFR-05** | Reliability | Bounded network timeouts, deterministic error envelopes, and graceful handling of network transitions. |

---

## 3. Phase 01 Requirements & Acceptance: Headless Tooling & Environment

Phase 01 delivers the foundational headless virtualization and Android development tooling required to develop and validate the multiplatform application on a headless Fedora Linux host.

### 3.1 Headless Infrastructure Requirements
- **FR-ENV-01 (Virtual Display)**: Provide an isolated virtual X11 display (`:100`) via Xpra, accessible remotely only over encrypted SSH tunnels.
- **FR-ENV-02 (Security Invariants)**: Virtual display must not bind public TCP ports (`--bind-tcp` omitted), must disable HTML5 web clients (`--html=off`), and must disable mDNS broadcast (`--mdns=no`).
- **FR-ENV-03 (Display Authentication)**: Xpra must spawn an authenticated child `xterm` ensuring child processes inherit valid `XAUTHORITY` credentials.
- **FR-ENV-04 (SDK Environment)**: Provide an idempotent shell configuration script that exports `ANDROID_HOME`, adds SDK tools to `PATH`, detects compatible JDKs, and remains silent in non-interactive sessions.
- **FR-ENV-05 (Emulator Lifecycle)**: Provide an emulator startup script that validates port bounds (even integers 5554–5682), checks KVM virtualization, and supports both hardware acceleration and SwiftShader fallback.

### 3.2 Phase 01 Deliverables & Status Distinction

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| `scripts/start-xpra.sh` | Starts/stops Xpra display `:100` cleanly without TCP exposure. | Script implemented; smoke test on `:199` verified zero TCP listeners via `ss -ltn`. |
| `scripts/android-sdk-env.sh` | Exports SDK paths, handles Java 25 / JDK 21 detection safely. | Script implemented; user overrides and non-interactive silence verified. |
| `scripts/start-android-emulator.sh` | Enforces port bounds, verifies `/dev/kvm`, handles missing binaries. | Script implemented; odd port, octal ports, and missing binary errors tested. |
| `docs/headless-android-setup.md` | Operations runbook with bounded boot checks and checksums. | Runbook published; wall-clock bounded `wait_for_emulator_boot` helper documented. |
| `docs/host-tooling-evidence.md` | Host capabilities and pending inventory recorded. | Published; AMD-V, KVM API 12, Xpra 6.5.4, Node v24 LTS verified; pending items explicit. |
| `tests/test-phase-01-tooling.sh` | Automated test suite exercising all script and doc contracts. | Suite implemented; 31/31 assertions passing cleanly. |
| **Runtime Acceptance Gate** | Interactive Studio GUI, SDK install, AVD creation, launcher screenshot. | **PENDING**: Requires interactive workstation operator attachment via `xpra attach`. |

---

## 4. Phase 02 Requirements & Acceptance: Frozen Foundation & Contracts

Phase 02 delivers the immutable build system foundation, staged multiplatform project assembly, version catalog, domain contracts, and test suites.

### 4.1 Foundation Requirements
- **FR-FND-01 (Staged Assembly)**: Root `settings.gradle.kts` unconditionally includes `:shared` and guards `:androidApp` and `:desktopApp` upon presence of build scripts.
- **FR-FND-02 (Toolchain Pinning)**: Pinned version catalog (`gradle/libs.versions.toml`) aligning Kotlin 2.1.10, Compose 1.7.3, AGP 8.7.3, Room 2.7.0-alpha12.
- **FR-FND-03 (Wrapper Integrity)**: Gradle 8.11.1 wrapper distribution and jar pinned to verified official SHA-256 checksums.
- **FR-FND-04 (Contract Isolation)**: Pure Kotlin Multiplatform domain contracts in `shared/src/commonMain/kotlin/` with zero Android (`android.*`) or concrete storage/network dependencies.
- **FR-FND-05 (Canonical Digest Vectors)**: Big-endian length-framed SHA-256 digest computation (`StrictSyncDigest`) aligns with backend Rust protocol; reference vectors pass via Node.js reference runner.
- **FR-FND-06 (Credential Redaction)**: Sensitive tokens and API keys redacted in `toString()` across credential entities (`CredentialBundle`, `HubConfig`) to prevent inadvertent string logging.

### 4.2 Phase 02 Deliverables & Status

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| Root Gradle Configuration | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`. | Implemented; staged assembly and JVM flags verified. |
| Pinned Wrapper & Toolchains | `gradle/wrapper/*`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`. | Implemented; official SHA-256 hashes verified. |
| `:shared` Module Configuration | `shared/build.gradle.kts`, `shared/gradle.lockfile` (`dev.tabbyrush.shared`, minSdk 26, JVM 17). | Implemented; dependency locking and Room schema dir configured. |
| Common Domain Contract Sources (16 files) | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/*.kt`. | Implemented; models, UI state, actions, errors, auth, sync, and policies complete. |
| Contract Test Suites (4 files) | `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/*.kt`. | Implemented; NIST vectors, title policy, account key, and endpoint policy covered. |
| Automated Foundation Test Suite | `tests/test-phase-02-foundation.sh`. | Implemented; 57/57 shell assertions PASS (live Gradle test runner pending downstream phases). |

---

## 5. Phase 03 Requirements & Acceptance: Hub Reuse & Strict Server Compatibility

Phase 03 establishes backend strict synchronization support on Glean Hub, enforcing absolute namespace and route isolation for TabbyRush while preserving existing Hub applications without behavioral regression.

### 5.1 Strict Server Requirements
- **FR-STRICT-01 (Strict Mode & Canonical Epoch)**: Opt-in `syncMode: "strict-v1"` registered via admin endpoint (`POST /api/v1/strict-apps`); server generates an immutable lowercase UUIDv4 epoch string before publishing the schema.
- **FR-STRICT-02 (Storage Target Collection)**: Dedicated application collection using the direct pattern `app<AppId>` (e.g. `appTabbyRush` via `SyncService::collection_name(app_id)`) with exact composite document key `_id: "userId:rowId"` and compound scan index `{ userId: 1, tableName: 1, rowId: 1 }`.
- **FR-STRICT-03 (Fail-Closed Admission Preflight)**: Admission validation requires uncapped collection, no TTL index, no custom validator, simple or absent collation, and verified scan index before schema publication. A standalone CLI (`strong_sync_admission`) executes identical validation rules.
- **FR-STRICT-04 (Atomic CAS Mutations)**: Single-document conditional writes enforce revision predicates (Create expected 0 -> rev 1 insert-only; Update expected current -> rev current+1 live filter; Delete -> rev current+1 compact tombstone with canonical empty data).
- **FR-STRICT-05 (Durable Replay Confirmation)**: Replay of matching `(opId, digest)` mutations executes conditional write updating `confirmationNonce` under journaled write concern; confirmation requires `matched_count == 1`.
- **FR-STRICT-06 (Legacy Fencing & Immutability)**: Legacy sync routes reject strict apps with HTTP 426 (`protocolUnsupported`); schema mutation attempts reject strict apps with HTTP 400 (`invalidRequest`); strong routes reject non-strict apps and epoch mismatches with HTTP 409 (`epochMismatch`).

### 5.2 Phase 03 Deliverables & Status Distinction

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| TabbyRush Strict Schema | `integration/glean-hub/tabbyrush-schema.json` | Implemented; verified exact 4-column todos schema. |
| Provisioning Script | `scripts/hub-schema-provision.mjs` | Implemented; syntax verified (`node --check`), 0600 key custody. |
| Compatibility Smoke Script | `scripts/hub-compatibility-smoke.mjs` | Implemented; syntax verified (`node --check`), non-zero on connection failure. |
| Integration Documentation | `docs/hub-schema-integration.md`, `docs/hub-compatibility-acceptance.md`, `docs/evidence/hub-compatibility-summary.md` | Published; documented invariants, error envelopes, and lifecycle. |
| Hub Plugin Rust Sources (20 files in sibling repo) | `glean-hub-sync/src/{models,services,routes,middleware,bin}/*.rs` | Implemented; 36 unit and route tests PASS, clippy clean (0 warnings). |
| **Live Mongo Acceptance Gates** | Live Mongo push/pull/replay/CAS, mounted router, rollback binary, legacy client fixtures. | **DEFERRED**: Requires live Mongo instance (`MONGO_TEST_URI`), deferred to Phase 07 device sync and Phase 08 release ops. |

---

## 6. Phase 04 Requirements & Acceptance: Hub Authentication & Protected Session

Phase 04 implements the multiplatform authentication client, session state machine, single-flight token refresh, and Android Keystore–protected credential storage, enabling secure opt-in sync against Glean Hub.

### 6.1 Authentication & Session Requirements
- **FR-AUTH-01 (Route Policy & Dual-Auth Header Allowlist)**: All outbound requests are bounded to `canonicalOrigin`. Routes are strictly classified into `AUTH_PUBLIC`, `BEARER_ONLY`, and `DUAL_AUTH_STRICT`. Disallowed paths and legacy sync endpoints fail closed locally with `SafeError.ProtocolIncompatible`. Redirects are strictly disabled (`followRedirects = false`).
- **FR-AUTH-02 (Keystore-Protected Credential Custody)**: Platform `CredentialVault` implemented on Android via `AndroidKeystoreVault` using AES-256-GCM with fresh 12-byte random nonces per write and AAD binding (`"tabbyrush_vault_v1:" + accountKey`). Tokens exist only in memory and within the vault; never exposed in `StateFlow`, loggers, or serialized state.
- **FR-AUTH-03 (Single-Flight Refresh & Generation Fencing)**: Concurrent foreground and background refresh calls are deduplicated into a single leader network request via `SingleFlightRefreshCoordinator`. Generation counter increments on quiesce/logout/switch to fence stale async completions and prevent resurrected credentials.
- **FR-AUTH-04 (Prohibited Automatic POST Replays)**: In `authorizedRequest()`, the HTTP auth layer never automatically replays mutating POST requests across 401 token refresh cycles; returns `SafeError.Unauthorized("TOKEN_REFRESHED")`, leaving retry ownership strictly to `SyncCoordinator`.
- **FR-AUTH-05 (Session Lifecycle & App Grant Enforcement)**: Explicit state machine (`UNAUTHENTICATED`, `AUTHENTICATING`, `AUTHENTICATED`, `ACCESS_PENDING`, `REAUTH_REQUIRED`). Enforces `/me.userId` matching login response. Evaluates `/me.apps` for `"tabbyrush"` grant; creates and reconciles access requests if grant is pending.
- **FR-AUTH-06 (Multiplatform Engine Abstraction)**: Common expect/actual factory `createPlatformHttpEngine()` configured with bounded timeouts (10s connect, 20s read/write) and disabled redirects on Android (`OkHttp`) and Desktop JVM (`Java`).

### 6.2 Phase 04 Deliverables & Status Distinction

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| Auth Wire DTOs | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-dtos.kt` | Implemented; redacted `toString()`, email normalization, byte length validation. |
| Ktor HTTP Client | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-http-client.kt` | Implemented; route classification, dual-auth headers, HTML error sanitization, 10s/20s timeouts. |
| Hub Auth API Client | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-client.kt` | Implemented; register, login, refresh, `/me`, and access requests. |
| Auth Session State Machine | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-session.kt` | Implemented; state machine, generation fencing, prohibited POST replay, offline restore, local logout. |
| Single-Flight Coordinator | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/single-flight-refresh.kt` | Implemented; leader-based concurrency lock using Mutex and CompletableDeferred. |
| Android HTTP Engine | `shared/src/androidMain/kotlin/dev/tabbyrush/shared/auth/android-http-engine.kt` | Implemented; OkHttp engine with redirects disabled and bounded timeouts. |
| JVM HTTP Engine | `shared/src/jvmMain/kotlin/dev/tabbyrush/shared/auth/jvm-http-engine.kt` | Implemented; Java HTTP engine with connect timeout. |
| Android Keystore Vault | `shared/src/androidMain/kotlin/dev/tabbyrush/shared/auth/android-keystore-vault.kt` | Implemented; Android Keystore–protected AES-256-GCM, fresh 12-byte nonces, AAD binding, atomic rename, status discrimination. |
| Auth Client Unit Tests | `shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-client-test.kt` | Implemented; 8 unit tests PASS (routes, headers, sanitization). |
| Auth Session Unit Tests | `shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-session-test.kt` | Implemented; 8 unit tests PASS (lifecycle, fencing, no POST replay, vault write failure). |
| Single-Flight Unit Tests | `shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/single-flight-refresh-test.kt` | Implemented; 3 unit tests PASS (deduplication, error propagation). |
| Keystore Instrumented Tests | `shared/src/androidInstrumentedTest/kotlin/dev/tabbyrush/shared/auth/android-keystore-vault-test.kt` | Implemented; covers AES-GCM, fresh nonces, AAD tamper rejection, atomic rename. (Deferred to Phase 07). |
| Auth Acceptance Spec | `docs/auth-acceptance.md` | Published; 21 verification criteria across 4 test suites. |
| Auth & Credentials Architecture | `docs/authentication-and-credentials.md` | Published; security invariants, route policies, AAD derivation, vault lifecycle. |

---

## 7. Phase 05 Requirements & Acceptance: Durable Local State & Strict Per-Record Sync

Phase 05 implements local persistence via Room KMP version 1 and SQLite `BundledSQLiteDriver`, strict per-record outbox reconciliation, staged full scans, and crash-safe fail-closed account teardown.

### 7.1 Local Persistence & Sync Requirements
- **FR-STORE-01 (Room Schema & Partitioning)**: Room database version 1 (`TabbyRushDatabase`) defines 5 stores (`server_base`, `outbox`, `editor_drafts`, `scan_staging`, `account_sync_meta`). Exported schema validated at `shared/schemas/.../1.json`. Destructive fallback is strictly forbidden. Storage is partitioned by canonical `AccountKey` SHA-256 string with path traversal validation.
- **FR-STORE-02 (Atomic Local Save & Unsent Cancellation)**: Local edits write `account_sync_meta`, `editor_drafts`, and `outbox` atomically via Room `@Transaction` methods (`saveTodoAtomic`, `deleteTodoAtomic`). Deleting a record with only an unsent `CREATE` operation cancels locally via `cancelUnsentCreateAtomic` without wire traffic.
- **FR-STORE-03 (Per-Row FIFO Dispatch & Head Blocking)**: At most one unresolved operation per `rowId` is dispatched in any round. If the head operation on a row is `DISPATCHING`, `CONFLICT`, or `UNKNOWN_SUPERSEDED`, all successor operations on that row are blocked. Dispatch batches enforce `<= 100` operations, zero duplicate `rowId`s, and `<= 256 KiB` total payload.
- **FR-STORE-04 (Staged Full Scan & Monotonic Promotion)**: 3-stage sync loop (scan -> push -> scan). Pagination begins with `cursor = null`, verifies epoch consistency, detects cursor loops and non-progressing cursors, and stages rows in SQLite `scan_staging`. Pre-scan confirmed identities must all be present in scan staging or sync aborts with `SafeError.ResetRequired("CONSISTENCY_ERROR")`. Monotonic promotion atomically applies higher revisions and verifies logical equivalence on equal revisions.
- **FR-STORE-05 (Exact CAS-Ack Reconciliation)**: Acknowledging an outbox mutation requires matching `opId`, `rowId`, `digest`, status in `[accepted, replayed]`, and `acceptedRevision == expectedRevision + 1`. Monotonically advances `server_base` and retains `editor_drafts` if `draft.localRevision > outbox.localRevision`.
- **FR-STORE-06 (UnknownSuperseded Disambiguation)**: Conflicts returned for operations with previous uncertain delivery history are classified as `UNKNOWN_SUPERSEDED`, acknowledging that the mutation may have committed remotely before being superseded. Provides explicit user workflows: `discardConflict`, `reapplyConflict`, `copyAsNew`.
- **FR-STORE-07 (Crash Recovery & Cancellation Safety)**: Startup resets `DISPATCHING` operations to `UNCERTAIN` via `recoverInterruptedDispatches()`. Coroutine push cancellation uses `NonCancellable` to mark dispatched batches `UNCERTAIN` before rethrowing `CancellationException`.
- **FR-STORE-08 (Fail-Closed Account Leave & Wipe Protocol)**: Teardown checks 5 unresolved work categories; executes strict 7-step sequence (freeze -> generation + marker write outside DB -> cancel/await schedulers -> close Room -> delete DB files -> clear vault -> clear marker). Startup recovery (`completePendingWipe`) inspects `wipe.marker` and completes teardown fail-closed before opening any account.

### 7.2 Phase 05 Deliverables & Status Distinction

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| Room KMP Database & Entities | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/` (5 entities, DAO, Database, migrations, RoomAccountStore). | Implemented; schema v1 exported, BundledSQLite driver, atomic `@Transaction` methods. |
| Platform Database Builders & Files | `shared/src/{androidMain,jvmMain}/kotlin/dev/tabbyrush/shared/data/` (Android and JVM database builders and account files). | Implemented; directory partitioning, path traversal guards, atomic wipe markers. |
| Strict Sync Clients & Reconcilers | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/` (HubSyncDtos, HubSyncClient, StagedFullScan, OutboxReconciliation, AccountSyncCoordinator, AccountLeaveProtocol). | Implemented; 3-stage sync, FIFO dispatch, CAS acks, UnknownSuperseded, fail-closed leave. |
| Room Schema Export | `shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/1.json`. | Implemented; verified canonical schema version 1 export. |
| Local Storage & Sync Specs | `docs/local-storage-and-sync.md`, `docs/sync-acceptance.md`. | Published; complete architecture, lifecycle diagrams, acceptance criteria. |
| Common & JVM Sync Test Suites (6 suites) | `shared/src/commonTest/.../sync/*-test.kt`, `shared/src/jvmTest/.../room-account-store-test.kt`. | Implemented; 89 unit and SQLite tests pass via `./gradlew --dependency-verification=lenient :shared:jvmTest`. |
| Android Room Instrumented Tests | `shared/src/androidInstrumentedTest/.../room-account-store-test.kt`. | Implemented; covers app-private storage and Room transactions. (Deferred to Phase 07). |

---

## 8. Phase 06 Requirements & Acceptance: Shared Compose UI & Accessible Light Theme

Phase 06 delivers the shared Compose Multiplatform user interface, accessible light theme palette, typography scale, responsive layouts, credential security protections, and UI semantics tests.

### 8.1 UI & Theme Requirements
- **FR-UI-01 (Accessible Light Palette)**: Deliver an accessible light theme palette (`TabbyRushTheme`, `TabbyRushColors`) with 16 calculated opaque token pairs meeting WCAG 2.1 AA contrast thresholds (normal text >= 4.5:1, interactive controls >= 3.0:1). Dynamic color is disabled; rendered UI contrast across OEM devices is deferred to Phase 07.
- **FR-UI-02 (Platform-Universal Typography)**: Deliver a typography scale (`TabbyRushTypography`) based on Compose `FontFamily.SansSerif`, omitting binary Latin-only font assets to delegate font resolution to platform system fonts; international glyph rendering, TalkBack/Orca compatibility, and OS-level 200% font scaling require platform device testing.
- **FR-UI-03 (Responsive Breakpoint Architecture)**: Deliver a unified Compose layout adapting between a compact vertical stack (<840dp) and an expanded desktop two-column fixed sidebar layout (>=840dp). Note: compact top bar is non-scrolling in `Scaffold.topBar`, tracked for Phase 07 200% font scale verification.
- **FR-UI-04 (Transient Password Security)**: Protect passwords with `PasswordVisualTransformation` visual masking, `semantics { password() }` semantic tag, memory dereferencing upon submission or disposal, and `AuthSubmitAction.toString()` redaction with `[REDACTED]`.
- **FR-UI-05 (Accessible Task Management)**: Display tasks in a lazy column (`TodoList`) with stable keys, non-color completion states, >=48dp touch targets, and modal editing (`TodoEditorDialog`) enforcing `TitlePolicy` scalar limits (1–200 scalars).
- **FR-UI-06 (Sync Feedback & Review Dialogs)**: Render sync status chips and last reconciled timestamps (`SyncFeedback`), side-by-side conflict resolution (`ConflictReviewDialog`), unsynced work tallies upon sign-out (`LeaveGuardDialog`), and open-source licenses (`LicenseNoticesDialog`).

### 8.2 Phase 06 Deliverables & Status Distinction

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| Theme & Typography (2 files) | `shared/.../theme/{tabbyrush-theme,tabbyrush-typography}.kt` | Implemented; 16 contrast pairs verified >= 4.5:1 (text) and >= 3.0:1 (controls). |
| Shared UI Composables (9 files) | `shared/.../ui/{tabbyrush-app,account-form,access-request-panel,todo-list,todo-editor,sync-feedback,conflict-review,leave-guard,license-notices}.kt` | Implemented; responsive layouts, transient password masking, dialogs, 48dp targets. |
| Resources & Notices (1 file) | `shared/.../composeResources/values/strings.xml` | Implemented; string resource catalog. |
| UI Semantics Test Suite (1 file) | `shared/.../ui/todo-semantics-test.kt` | Implemented; 7 unit tests PASS via `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest`. |
| Design & Provenance Specs (2 files) | `docs/{design-guidelines,font-provenance}.md` | Published; WCAG 2.1 AA contrast calculations, font audit, and documented deferrals. |
| **Rendered UI & Platform Gates** | Full rendered composables, TalkBack, IME Done, 200% device font scaling. | **DEFERRED**: Requires `compose.uiTest` harness and physical/emulator device, scheduled for Phase 07. |

---

## 9. Phase 07 Requirements & Acceptance: Android Composition & Runtime Integration

Phase 07 delivers the native Android application module (`:androidApp`), integrating the frozen domain contracts (Phase 02), backend strict protocol client (Phase 03), Keystore credential vault and auth session (Phase 04), Room database and outbox sync coordinator (Phase 05), and accessible Compose UI (Phase 06).

### 9.1 Android Integration Requirements
- **FR-AND-01 (Process Container & Account Runtime Isolation)**: `TabbyRushApplication` manages application-level singletons (`vault`, `accountFiles`, `hubConfig`, `authSession`, `syncScheduler`). `AccountRuntime` encapsulates `TabbyRushDatabase`, `RoomAccountStore`, `AccountSyncCoordinator`, and `AccountLeaveProtocol` bound to an `AccountKey` and generation counter. Foreground UI (`SessionViewModel`) and background tasks (`TabbyRushSyncWorker`) share the identical runtime instance and coordinator mutex.
- **FR-AND-02 (Cold-Start Active Identity Persistence)**: Active account key and identity persist in app-private `active_account.dat` via temporary file write and `StandardCopyOption.ATOMIC_MOVE` upon authenticated login/register. On cold start, `TabbyRushApplication` restores the session from the vault only after verifying no `wipe.marker` exists.
- **FR-AND-03 (Retained ViewModel & Reactive State Flow)**: `SessionViewModel` binds `TodoUiState` to auth and sync progress flows, dispatches `TodoAction` variants, maps storage and validation errors to sanitized `SafeError.Internal` and `SafeError.Validation` envelopes, and ensures passwords and auth tokens are not retained in ViewModel fields or `SavedStateHandle` after submission.
- **FR-AND-04 (Background Sync Scheduling & Generation Fencing)**: `AndroidSyncScheduler` schedules WorkManager unique one-shot (`KEEP` policy, network connected constraint) and periodic 15-minute (`UPDATE` policy) work tagged with `tabbyrush_sync_account_<key>`. `TabbyRushSyncWorker` enforces wipe marker absence, session auth state, account key match, and persisted generation counter equality before accessing store resources.
- **FR-AND-05 (Fail-Closed Quiescence & Wipe Protocol)**: `cancelAndAwait()` cancels WorkManager tasks by tag, polls WorkInfo completion for up to 5s (throwing `IllegalStateException` on timeout), and polls `coordinatorMutex.tryLock()` for up to 5s (throwing `IllegalStateException` on timeout). `completePendingWipesOnStartup()` synchronously cancels tasks, deletes database files, clears vault credentials, and removes markers before opening accounts.
- **FR-AND-06 (Manifest Hardening & Diagnostic Infrastructure)**: Base manifest enforces single exported `MainActivity`, `allowBackup="false"`, and `usesCleartextTraffic="false"`. Debug manifest links `network_security_config.xml` strictly allowlisting `127.0.0.1`, `localhost`, and `10.0.2.2`. Backup and data extraction XML rules exclude private databases and accounts. Release task graph listener fails builds closed when keystore or signing credentials are absent. Includes `android-smoke.sh` and `debug-fault-proxy.mjs` for loopback fault injection.

### 9.2 Phase 07 Deliverables & Status Distinction

The 25 Phase 07 deliverables are fully enumerated in the artifact inventory at [Android Test Summary](./evidence/android-test-summary.md) and [Android Acceptance Spec](./android-acceptance.md):

| Category | Deliverable Summary | Verification Status |
|---|---|---|
| Android Build & Config (2 files) | `androidApp/{build.gradle.kts,gradle.lockfile}` | Implemented; AGP 8.7.3, compileSdk 35, release signing gate. |
| Manifests & Security (5 files) | `androidApp/src/{main,debug}/AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, `network_security_config.xml` | Implemented; source audited; cleartext disabled in release, loopback-only debug NSC. |
| Android Kotlin Source (5 files) | `main-activity.kt`, `tabbyrush-application.kt`, `session-view-model.kt`, `android-sync-scheduler.kt`, `tabbyrush-sync-worker.kt` | Implemented; source audited; uncompiled by AGP (host SDK absent); shared JVM regression suite (`:shared` only) 100% PASS (96/96 tests under lenient dependency verification). |
| Instrumented Test Suites (2 files) | `todo-device-test.kt`, `background-isolation-test.kt` | Authored; covers ViewModel, Room store, WorkManager, and wipe isolation (unexecuted on device). |
| Host Scripts (2 files) | `scripts/{android-smoke.sh,debug-fault-proxy.mjs}` | Implemented; syntax verified (`bash -n`, `node --check`). |
| Acceptance & Evidence Docs (2 files) | `docs/{android-acceptance.md,evidence/android-test-summary.md}` | Published; complete invariants, test matrices, and inventory. |
| Visual Design Baselines (7 PNGs) | `docs/evidence/android-debug-*.png` | Visual design mockups for core screen states (live device captures pending Phase 08). |
| **Android Compilation & Device Gates** | Bytecode compilation (`:androidApp:compileDebugKotlin`), `assembleDebug`, connected device tests (`connectedDebugAndroidTest`). | **PENDING**: Blocked by absent Android SDK on host (`ANDROID_HOME`); deferred to Phase 08 release operations. |
| **Previously Deferred Platform Gates** | Live Mongo routes (Phase 03), Android Keystore vault test (Phase 04), Android Room store test (Phase 05), rendered UI & 200% font scale checks (Phase 06). | **DEFERRED**: Scheduled for Phase 07, now carried forward to Phase 08 release operations alongside Android host SDK provisioning. |

---

## 10. Phase 09 Requirements & Acceptance: Linux Desktop & Installed RPM

Phase 09 delivers the desktop application module (`:desktopApp`) targeting Linux x86_64 with Compose Multiplatform 1.7.3, hardware/OS credential custody via Freedesktop Secret Service, foreground sync coordination, single-instance profile locking, and fail-closed account leave lifecycle.

### 10.1 Linux Desktop Requirements
- **FR-DSK-01 (Target Isolation & Locked Tooling)**: `desktopTarget` defaults to detected host OS (`linux`); explicit target mismatches fail before dependency resolution. Locked dependencies defined in `desktopApp/gradle-linux.lockfile` (161 locked artifacts). Packaged binaries contain Linux provider only with zero Windows references.
- **FR-DSK-02 (Hardware/OS Vault SPI & ServiceLoader Dispatcher)**: Extensible `CredentialVaultProvider` SPI discovered via Java `ServiceLoader`. `PlatformVaultDispatcher` requires exactly one provider matching the active desktop target; zero, multiple, or mismatched providers fail closed with explicit exception. Plaintext token file fallback is strictly prohibited.
- **FR-DSK-03 (Freedesktop Secret Service Vault Custody)**: `LinuxSecretServiceVault` stores access and refresh tokens as an atomic JSON bundle via Freedesktop Secret Service (`secret-tool` / D-Bus). Unlocked keyring status represented faithfully; missing daemon fails closed (`UNAVAILABLE`).
- **FR-DSK-04 (Single-Instance Profile Guard)**: `SingleInstanceGuard` acquires exclusive OS file-channel lock on `~/.local/share/tabbyrush/accounts/tabbyrush.lock` before initializing vault or database. Second launch exits immediately without touching database or racing refresh. Lock file is retained across exits to prevent inode recycling races.
- **FR-DSK-05 (Foreground Sync Scheduling & Quiescence)**: `DesktopSyncScheduler` coordinates periodic (15-min) and immediate sync within the window coroutine scope; implements `LeaveSchedulerCancelGate` to cancel and await active sync jobs before account leave or wipe.
- **FR-DSK-06 (Immutable Modes & Packaging Smoke Runner)**: Default entrypoint `MainKt` enforces `BuildMode.RELEASE` (HTTPS only). `DevelopmentLauncher` enables `BuildMode.DEBUG` (HTTP loopback permitted) via `:desktopApp:runDebug`. `scripts/linux-package-smoke.sh` audits distributable bundle, RPM metadata, and absence of foreign platform classes.

### 10.2 Phase 09 Deliverables & Status Distinction

| Category | Deliverable Summary | Verification Status |
|---|---|---|
| Desktop Build & Tooling (3 files) | `desktopApp/build.gradle.kts`, `desktopApp/gradle-linux.lockfile`, `scripts/linux-package-smoke.sh` | Implemented; Kotlin JVM 2.1.10, Compose 1.7.3, 161 locked artifacts, smoke runner syntax verified. |
| Common Desktop Source (5 files) | `main.kt`, `credential-vault-provider.kt`, `platform-vault-dispatcher.kt`, `desktop-sync-scheduler.kt`, `single-instance-guard.kt` | Implemented; ServiceLoader SPI, file lock guard, foreground scheduler, and RELEASE/DEBUG entrypoints. |
| Linux Target Source & SPI (2 files) | `linux-secret-service-vault.kt`, `META-INF/services/dev.tabbyrush.desktop.CredentialVaultProvider` | Implemented; Freedesktop Secret Service client, atomic JSON payload, ServiceLoader descriptor. |
| Desktop Unit Test Suites (3 files) | `platform-vault-dispatcher-test.kt`, `desktop-lifecycle-test.kt`, `linux-secret-service-vault-test.kt` | Implemented; 19/19 unit tests PASS via `./gradlew :desktopApp:test`. |
| Desktop Acceptance & Runbooks (3 files) | `docs/linux-desktop-guide.md`, `docs/linux-acceptance.md`, `docs/evidence/linux-test-summary.md` | Published; architecture, runbooks, acceptance criteria, and test execution evidence. |
| Visual Design Baselines (7 PNGs) | `docs/evidence/linux-*.png` | Visual design mockups for desktop screen states conforming to ForumFlash light theme. |
| Standalone Distributable Bundle | `desktopApp/build/compose/binaries/main/app/TabbyRush/` | Assembled and statically inspected via smoke runner (`bin/TabbyRush` executable verified; live launch without JDK unrun). |
| **Live RPM Packaging & System Install** | `:desktopApp:packageRpm`, `sudo dnf install` | **PENDING**: Blocked by absent `rpmbuild` (`rpm-build` package) on host and interactive sudo; deferred to operator setup. |
| **Live D-Bus / Secret Service Session** | Running desktop under active D-Bus session and Xpra framebuffer capture. | **PENDING**: Requires interactive Xpra desktop attachment (`xpra attach`) with unlocked GNOME Keyring. |

---

## 11. Phase 10 Requirements & Acceptance: Native Windows DPAPI and MSI

Phase 10 implementation was delivered and is recorded at **100% (2026-10-14)**. This is implementation status, not technical acceptance: interactive MSI installation, Narrator verification, and strict dependency verification remain blocked pending operator prerequisites. The latest Windows evidence summary reports 25/25 desktop tests and 96/96 shared JVM tests passing (121/121 combined), a successful `TabbyRush.exe` bundle audit, and MSI metadata/hash inspection. Separately, the source ACL helper does not guarantee restrictive ACL enforcement; clean-user runtime/sync and uninstall/reinstall evidence also remain open.

### 11.1 Windows Desktop Requirements
- **FR-WIN-01 (Target Isolation & Locked Tooling)**: `desktopTarget` selects `windows` only on a Windows host and rejects mismatches before resolution. `desktopApp/gradle-windows.lockfile` freezes the Windows target graph. Windows packaging includes JNA and the Windows provider, not Linux D-Bus/Secret Service runtime dependencies.
- **FR-WIN-02 (User-Scoped DPAPI Custody)**: `WindowsDpapiVault` protects one JSON credential bundle containing account key and access/refresh tokens using JNA-backed `CryptProtectData` / `CryptUnprotectData` with UI prompts forbidden; machine-wide scope and plaintext token fallback are prohibited. The vault zeroes plaintext byte buffers after cryptographic use.
- **FR-WIN-03 (Atomic Credential Replacement)**: `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-dpapi-vault.kt` writes protected bytes to a temporary file and atomically moves them into place. `applyRestrictiveUserAcl()` attempts an owner ACL but returns when the ACL view/owner is absent and suppresses exceptions, so effective ACL enforcement is not guaranteed.
- **FR-WIN-04 (Service Provider Isolation)**: Java `ServiceLoader` discovers `WindowsDpapiVaultProvider`; the shared dispatcher requires exactly one provider matching the active target and fails closed when resolution is empty or ambiguous. The Windows package smoke script audits the provider descriptor and absence of Linux D-Bus/Secret Service classes.
- **FR-WIN-05 (Windows Distribution)**: `:desktopApp:createDistributable` creates the self-contained `TabbyRush.exe` application image; `:desktopApp:packageMsi` creates the per-user MSI with Start Menu integration. The developer MSI is unsigned; organizational Authenticode signing is a production distribution prerequisite.
- **FR-WIN-06 (Installed Runtime Acceptance)**: Installation without a developer JDK, launch, persistence/sync, DPAPI custody, accessibility, and uninstall/reinstall require a real interactive Windows session. Account-leave quiescence and credential/data clearing must be verified as acceptance criteria, not assumed from source or package inspection. Interactive MSI install, Narrator verification, and strict dependency verification remain pending operator prerequisites.

### 11.2 Phase 10 Deliverables & Status Distinction

| Category | Deliverable Summary | Verification Status |
|---|---|---|
| Windows Vault & SPI | `windows-dpapi-vault.kt`, Windows `META-INF/services` descriptor | Implemented; live JNA DPAPI round-trip and provider discovery reported in the Windows evidence summary. |
| Target Lock & Isolation | `gradle-windows.lockfile`, JNA-only Windows target dependencies | 160 configurations locked; package inspection reports Windows provider present and Linux D-Bus artifacts absent. |
| Automated Tests | Windows `desktopApp` tests and `:shared:jvmTest` | 25/25 desktop and 96/96 shared tests reported passing (121/121 combined) in [Windows Test Summary](./evidence/windows-test-summary.md). |
| Distribution Artifacts | `createDistributable`, `packageMsi` | `TabbyRush.exe` bundle and `TabbyRush-1.0.0.msi` generated and audited; MSI is 100.68 MB and unsigned. |
| Package Verification | `scripts/windows-package-smoke.ps1` | Audits distributable JAR contents/provider, target isolation, MSI SHA-256, Authenticode status, and COM metadata. |
| UI Evidence | Seven `docs/evidence/windows-*.png` renders | Design mockups only; not live application captures or Narrator evidence. |
| **Technical Acceptance Gates** | Interactive MSI install/runtime, Narrator verification, strict dependency verification | **BLOCKED** pending operator prerequisites; package metadata inspection is not proof of installed runtime behavior. |

Windows review cycle 2 was approved with a score of 8.5/10. This review status does not replace pending interactive acceptance or the Phase 09 installed-RPM gate.

## 12. Task Deadlines, Reminders & Strict Protocol Upgrades: Phase 01 Requirements & Acceptance

Phase 01 delivers the frozen data contract, temporal model, and safe transition design for task deadlines, reminders, and strict-protocol upgrades (Gate G1 Approved on 2026-10-15).

### 12.1 Phase 01 Requirements
- **FR-TMP-01 (16-Column Strict-v2 Schema)**: 12 new fields added (`deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`). Payloads require full replacement with explicit presence/nullability.
- **FR-TMP-02 (Canonical Digests & Preimages)**: Domain `strict-v2` with length-prefixed 8-string identity framing, 8-byte big-endian signed 64-bit revision, and zero data bytes for `DELETE`.
- **FR-TMP-03 (Checked 64-bit Integer Arithmetic)**: Reject arithmetic overflow wrap at all conversions (`safeMultiply`, `safeSubtract`, `safeAdd`), including lead duration, repeating offsets, and intermediate anchor points.
- **FR-TMP-04 (Opportunity Bounds & Interval Constraints)**: `reminderCount` in `1..10`. Non-repeating count = 1 requires null interval. Repeating count 2..10 requires interval >= 15 min. Nonnegative `createdAt` and `reminderLeadMinutes`.
- **FR-TMP-05 (App-Scoped Admin Migration Fencing)**: In-place migration (`POST /api/v1/apps/:app_id/strict-upgrades`) requires global admin role and app-scoped authorization. Acquires coordination lease, drains in-flight requests, transforms documents in-place, verifies schema, and atomically updates `_schemas`.
- **FR-TMP-06 (Outbox Replay & Reconciliation Safety)**: Legacy outbox operations lacking verified dispatch provenance enter `UNKNOWN_SUPERSEDED` for explicit user review via `POST /api/v1/sync/:app_id/strong/legacy-receipts`; safe intent rebasing preserves remote v2 fields.

### 12.2 Phase 01 Deliverables & Verification Status

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| Canonical Schemas | `integration/glean-hub/tabbyrush-schema-v1.json`, `tabbyrush-schema-v2.json` | Schemas defined; SHA-256 fingerprints `3361a80f...` and `5f539551...` verified. |
| Cryptographic Vectors | `integration/glean-hub/strict-v2-vectors.json` | 20 reference test vectors (9 valid + 11 rejections) verified across Kotlin, Rust, and Node.js test runners. |
| Kotlin Domain Contracts | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v2-contract.kt` | Implemented; `StrictV2TodoData`, checked 64-bit arithmetic, validation invariants. |
| Binary Preimage Engine | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v2-digest.kt` | Implemented; `StrictSyncV2Digest` SHA-256 framing and encoding verified. |
| Unit & Vector Tests | `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-v2-digest-*.kt` | 10 tests across 2 test files (`StrictV2DigestVectorTest`, `StrictV2DigestExtendedTest`) passing in `:shared:jvmTest`. |
| Node.js Vector Runner | `scripts/strict-v2-vector-runner.mjs` | Node.js test runner verifying vectors and schema fingerprint hashing (20/20 PASS). |
| Vector Generator | `scripts/generate-strict-v2-vectors.mjs` | Reference fixture generator script implemented. |
| Documentation & Review | `docs/system-architecture.md`, `local-storage-and-sync.md`, `hub-schema-integration.md` | Documented; QA report ([QA Report](../plans/reports/qa-261015-0938-phase-01-freeze-temporal-model.md)) and Code Review ([Review Report](../plans/reports/code-review-261015-0951-phase-01-freeze-temporal-model.md)) approved (Gate G1 passed; Phases 02–12 implementation delivered under Plan 261015-0752; live production cutover blocked at Gate G7). |

---

## 13. Technical Constraints & Dependencies
10. **Windows Desktop Toolchain**: Windows x64 build and packaging require JDK 21 with `jpackage`; `packageMsi` requires WiX tooling (the Compose plugin provisioned WiX for the recorded build). The distributable bundle does not require WiX.
11. **Windows Installer Trust**: The recorded MSI is unsigned. Production distribution requires an approved Authenticode signing certificate and verification; the current package build is not a signed public release.

1. **Host Environment**: Fedora Linux 44 Server (`x86_64`, kernel `7.1.10`), AMD-V hardware virtualization enabled.
2. **JDK Requirements**: AGP 8.x and Compose Desktop require a compatible JDK (JDK 17 or JDK 21 LTS). System default Java 25 is not verified for Gradle tasks; Studio bundled JBR or `java-21-openjdk-devel` is required.
3. **Android SDK Requirements**: AGP bytecode compilation and Android instrumented tests require Android SDK 35 (`cmdline-tools;latest`, `platforms;android-35`, `platform-tools`, emulator) and interactive license acceptance (`sdkmanager --licenses`).
4. **Interactive Licensing**: Android SDK licenses must be accepted interactively via `sdkmanager --licenses`; automated blanket acceptance is prohibited.
5. **Interactive GUI Acceptance**: Full visual verification of Android Studio, emulator framebuffer, and desktop Xpra windowing requires interactive remote workstation attachment via `xpra attach ssh://.../100`.
6. **Live Database Testing**: Full backend acceptance requires a live MongoDB daemon (standalone or replica set) accessible via `MONGO_TEST_URI`.
7. **Instrumented & Hardware Testing**: Keystore encryption and Android Room verification require an active Android device or emulator with Google Play / AOSP platform image.
8. **Linux Packaging Dependencies**: Compiling RPM native distributions (`:desktopApp:packageRpm`) requires `rpm-build` (`rpmbuild` executable); installing requires administrative (`sudo`) access.
9. **Secret Service Daemon**: Hardware/OS credential custody on Linux requires an active D-Bus session bus (`DBUS_SESSION_BUS_ADDRESS`) and an unlocked Secret Service provider (`gnome-keyring-daemon` or `ksecretservice`).

---

## 14. Traceability & Phased Roadmap

Progress across the TabbyRush project is tracked in the living [Project Roadmap](./project-roadmap.md):
- **Phase 01 Tooling & Environment**: Scripts and documentation implemented (DONE); runtime GUI gate pending interactive attachment.
- **Phase 02 Frozen Foundation & Contracts**: Staged Gradle build system, SHA-256 pinned wrapper, shared module configuration, common domain contract sources, and test suites implemented (DONE).
- **Phase 03 Hub Reuse & Strict Server**: Backend strict synchronization server, legacy isolation fences, admission preflight, schema integration, and operator tooling implemented across 26 files (Implementation Complete; 36 Rust tests pass; live MongoDB gates deferred to Phase 07).
- **Phase 04 Hub Authentication & Protected Session**: Authentication client, HTTP engine abstraction, session state machine, generation fencing, single-flight refresh, and Android Keystore vault implemented across 14 files (DONE; 19 JVM unit tests PASS; Android Keystore device run and live Hub integration deferred to Phase 07).
- **Phase 05 Durable Local State & Strict Per-Record Sync**: Room KMP database (v1), 5 entities, atomic DAO transactions, BundledSQLite driver, platform account files, staged full scan, FIFO outbox reconciliation, CAS ack matching, UnknownSuperseded, crash recovery, and fail-closed leave protocol implemented across 29 files (Implementation Complete; 89 common/JVM tests PASS; Android device test and live Mongo e2e sync deferred to Phase 07).
- **Phase 06 Shared Compose UI & Accessible Light Theme**: Shared Compose UI components, responsive compact/expanded layout, light theme palette with verified WCAG 2.1 AA contrast, transient password protection, and UI semantic test suite implemented across 15 files (5 conditional font/license files omitted per provenance audit; 96 :shared:jvmTest tests pass under lenient dependency verification; marked complete administratively; technical review 6.5/10 NOT ACCEPTED with documented integration gaps deferred to Phase 07).
- **Phase 07 Android Composition & Runtime Integration**: Native Android client module, process container, WorkManager background sync, debug network security config, manifest hardening, fault-injection proxy, smoke runner, and authored instrumented test suites delivered across 25 artifacts (Android source delivered; JVM regression suite 96/96 PASS; AGP bytecode compilation, connected device tests, and live framebuffer capture pending host SDK provisioning in Phase 08).
- **Phase 08 Authorized Hub Operations & Signed Android Release**: Operational tooling, release security auditor (`scripts/release-security-check.mjs`), live strict smoke runner (`scripts/hub-live-smoke.mjs`), mongosh recovery script (`scripts/hub-strict-recovery.js`), deployment guide, security & recovery architecture, release acceptance specification, and evidence reports implemented across 20 artifacts (Tooling & harnesses operational; live bytecode compilation, binary signing verification, and live device acceptance gated on host SDK provisioning and Phase 07 defect remediation).
- **Phase 09 Linux Desktop & Installed RPM Distribution**: Native Linux desktop module (`:desktopApp`), Secret Service hardware/OS vault SPI (`CredentialVaultProvider`), ServiceLoader dispatcher (`PlatformVaultDispatcher`), `SingleInstanceGuard` file-channel lock, `DesktopSyncScheduler` foreground sync coordination, packaging smoke runner (`scripts/linux-package-smoke.sh`), and unit test suites implemented across 23 artifacts (19/19 desktop tests PASS; standalone distributable assembled and inspected; live RPM packaging, installed smoke test, and interactive D-Bus session pending operator prerequisites).
- **Phase 10 Windows Desktop & MSI Distribution**: DPAPI vault, Windows SPI, target lock/isolation, smoke harness, `TabbyRush.exe` distributable and MSI artifacts delivered; test/package evidence recorded. Interactive MSI installation, runtime/accessibility checks, uninstall/reinstall, and upstream Phase 09 acceptance remain pending.
- **Task Deadlines, Reminders & Strict Protocol Upgrades (Plan 261015-0752, Phases 01–12)**:
  - **Implementation Effort**: 100% planned effort delivered (236h / 236h credited across Phases 01–12).
  - **Gate G1 (Contract & Transition Design)**: **PASSED** (canonical v1/v2 schemas, tri-stack binary digests, and 23 wire vectors frozen).
  - **Gate G2 (Platform Hardware Delivery)**: **LOGIC DELIVERED / PHYSICAL HELD** (Android AlarmManager/Doze adapters, Linux D-Bus reminder adapter, and Windows candidate adapter delivered; physical Android Doze verification, desktop visual notification display, and native Windows WinRT/COM toast bridge implementation remain pending).
  - **Gate G3 (Hub Cluster Staging & Remediations)**: **BACKEND CONTRACTS DELIVERED / LIVE HELD** (Hub v2 admission, coordination permit fences, and in-place migration engine delivered; multi-node MongoDB replica set migration and 3 Phase 03 code findings remain prerequisites).
  - **Gate G4 (Client Version Transition & Safe Replay)**: **PASSED** (Room explicit migration `1->2`, `ContractTransitionCoordinator`, intent-masked rebasing, and legacy receipt reconciliation).
  - **Gate G5 (Shared Scheduling & Account Fences)**: **PASSED** (`ReminderScheduleCalculator`, `PersistedScheduleProjector`, `ReminderOccurrenceStore` claim-before-post, and `CompositeLeaveSchedulerCancelGate`).
  - **Gate G6 (Adversarial Acceptance & 3-Device Proof)**: **LOGIC PASSED / LIVE HELD** (adversarial boundaries and file-backed SQLite persistence pass; live 3-device concurrent sync held pending physical hardware testbeds).
  - **Gate G7 (Coordinated Cutover & Release Runbook)**: **RUNBOOK DELIVERED / LIVE CUTOVER BLOCKED** (authoritative operator runbook `docs/strict-v2-cutover-runbook.md` and rehearsal tooling `scripts/strict-v2-cutover-rehearsal.mjs` delivered; live production cutover strictly blocked pending prerequisite gates G2, G3, G6, and formal operator authorization).

## 15. Sticky Task Surfaces — Delivered Architecture & Status

The separate Sticky Task Surfaces initiative (`plans/261016-1409-sticky-task-surfaces/`) delivers shared task-surface contracts, the Android Glance widget and quiet deadline notification, and the desktop Compose sticky window/tray integration. Phase 07 is delivered with documentation and automated verification evidenced; **6h / 12h credited (118h / 132h overall; 89.4%)**. Native Android/Windows acceptance remains conditionally held at Gate G2, and Linux session acceptance remains pending Phase 08.
### 15.1 Surface Requirements and Implemented Behavior
- `SurfaceAccountToken` binds an `AccountKey` to its runtime generation. `TaskSurfaceAccess` admits reads, desired-state completion, publication and navigation only for the current token; non-ready states do not publish task content.
- `AccountStore.observeTodos()` feeds current task snapshots to the surfaces. `setCompletedLocally(id, completed, nowEpochMs)` performs an atomic desired-state update from a fresh projection, preserving other current fields.
- Shared projections partition non-deleted tasks without reordering and select overdue deadlines before upcoming ones, with deterministic deadline/task-ID ordering and conflict metadata. Reminder enablement does not gate deadline selection.
- Android `TabbyRushTaskWidget` is a responsive Glance app widget with Incomplete and Completed sections and Open, Add and desired-completion actions. `TaskSurfaceNotificationCoordinator` publishes one opt-in, low-importance notification without sound, vibration or badge; task detail is private, with generic public lock-screen text.
- Desktop `StickyTaskContent` is composed in a separate compact Compose `Window` requesting `alwaysOnTop`; the tray offers Open TabbyRush, Show/Hide Sticky Tasks and Quit when supported. Quick-add is isolated from the main editor. Closing a window hides it; explicit Quit performs runtime teardown. A missing/unusable tray must not strand the process with every window hidden.
- Android task-surface preferences and desktop sticky-window geometry/visibility are local presentation state, not synchronized task fields. Linux-specific session acceptance is not inferred from shared desktop behavior.

### 15.2 Review, Verification and Remaining Gates
- Phase 07 records 364 passing Gradle tests (231 shared JVM, 99 desktop, 34 Android unit), 12/12 Node security tests, 6/6 source diagnostics, 18/18 Hub fixture checks, 8/8 strict-v2 rehearsal checks, `:androidApp:assembleDebug`, and `:desktopApp:createDistributable`. These are report-attributed results, not fresh runs in this documentation update; Gradle commands used `--dependency-verification=off`.
- The Android and Windows PNGs are visual design baselines, not runtime OS captures. Interactive Android launcher/shade and Windows window/tray/multi-monitor acceptance remain held at G2.
- The review's Android safety findings remain open: on delivery-gate drain timeout, `closeRuntimeInternal()` catches and logs the exception, then returns before canceling observer jobs or closing Room; `closeActiveRuntime()` still clears `activeRuntime`. Glance blanking/session-close failures are also caught and logged, so stale OS-retained content may remain.
- The 149-checksum dependency-verification hold and separate Glance artifact-provenance hold remain open. Linux acceptance needs a real Linux session; historical release holds G3/G6/G7 are unchanged.

---

## 16. Server Admin Announcements — Delivered Architecture & Staged Pilot Status

The Server Admin Announcements initiative (`plans/261018-0707-server-admin-notifications/`, Phases 01–12) delivers secure, unidirectional administrator announcements from Glean Hub to TabbyRush clients across Android, Linux, and Windows:

### 16.1 Core Requirements and Implemented Architecture
- **Intake & Confirmation**: Glean Hub admin console (`AnnouncementComposer`, `AnnouncementHistory`) supports `selected` (1..500 user IDs), `all`, and `admins` audience modes intersecting current `tabby-rush` grants. Confirmation binds an immutable SHA-256 digest (`sha256:{hex}`); changed eligibility returns 409 `audienceChanged`.
- **Durable Storage & Retention**: Canonical MongoDB collections (`admin_announcements`, `admin_announcement_inbox`, `admin_announcement_dispatches`, `notification_install_bindings`) are isolated from strict task-sync data. Journaled parent inserts (`w:1, j:true`) precede HTTP 202 acknowledgement. 90-day retention with BSON DateTime TTL indexes in MongoDB (`expiresAt = createdAt + 90 days`); MongoDB background TTL deletion is asynchronous, while application-side query and rendering filters strictly exclude records when `expiresAt <= serverNow` (only unexpired records are queryable or renderable); reading never extends expiry.
- **Client In-Memory Isolation**: TabbyRush clients store announcements exclusively in memory (sliding window max 200 items, dedup 2048) via `RemoteInboxController`. Offline UI indicates unavailable content without exposing cached details. Zero Room database migrations, persistent offline content, or read outbox; process termination purges in-memory items.
- **Transport & Wire Delivery**:
  - Android: FCM HTTP v1 generic notifications for Android with channel `tabbyrush.remote.announcements` and tag `remote:<messageId>`, ensuring announcement-content isolation from push providers with fixed generic copy ("You have a new announcement") and opaque metadata (`type: "admin_announcement"`, `appId: "tabby-rush"`, `messageId: "<UUID>"`). Cold/warm taps reauthorize against current active account session. Exported component policy strictly permits `FirebaseInstanceIdReceiver` protected by `com.google.android.c2dm.permission.SEND`.
  - Desktop: Streaming SSE broker with `streamEpoch`, monotonic `acceptanceOrdinal`, and ready baseline (`event: ready`). Hints arriving after baseline hydration trigger authenticated detail fetching. Desktop displays at most one generic alert per active connection and message hint; Android suppresses SSE alerts.
- **Install Proof & Fencing**: Android client encrypts 32-byte cryptographic install proof in app-private no-backup storage using an Android Keystore-backed AES-256-GCM key (`tabbyrush_remote_install_v1`), transmitted as `X-Tabby-Install-Proof` header and validated via SHA-256 hash on Glean Hub with numeric generation CAS fencing against delayed unbinds or stolen install UUIDs.

### 16.2 Acceptance Matrix & Staged Pilot Gate
- **Automated Tooling**: Standalone smoke runner `scripts/admin-announcements-smoke.mjs --self-test` passes 21 mock assertions and 13 golden vectors; QA regression report (`qa-261018-1548-c1-c6-regression-verification.md`) records 566 verifications passing (548 test executions across 8 targets + 18 vector/static checks). Cycle 2 code review approved at Score 8/10 with 0 critical issues.
- **Restricted Pilot Gate (Android/Linux/Web Admin)**: Common safety, server durability semantics, Android runtime, Linux desktop adapter, and Admin Web console verified for internal restricted pilot ($\le 500$ users per send).
- **Full Delivery Gate (Windows Inclusive)**: Windows C ABI v1 `tabbyrush-toast.dll` and WiX3 MSI packaging are implemented in source but held at Gate G5 on Linux build hosts. Full rollout requires clean-host Windows proof.
- **Release Holds**: Inherited release holds (G2/G3/G6/G7, 149-checksum baseline, Glance provenance) remain open until separate resolution.
