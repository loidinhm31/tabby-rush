# Doggo Project Overview & Product Development Requirements (PDR)

Last Updated: 2026-10-14  
Document Version: 1.3.0  
Status: Active  
Related Docs: [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Project Roadmap](./project-roadmap.md) · [Codebase Summary](./codebase-summary.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Sync Acceptance](./sync-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md) · [Auth Acceptance](./auth-acceptance.md) · [Authentication & Credentials](./authentication-and-credentials.md)

---

## 1. Executive Summary & Product Vision

Doggo is an Android-first shared Compose Multiplatform application designed for fast, focused personal task management with multi-device cloud synchronization. 

The project prioritizes a clean multiplatform architecture, where the core domain logic, state management, and user interface are planned in Kotlin Multiplatform and Compose Multiplatform:
1. **Android Application (`androidApp/`)**: Planned primary mobile target (Phase 07).
2. **Desktop Application (`desktopApp/`)**: Planned desktop targets for native Linux RPM (Phase 09) and Windows MSI (Phase 10).
3. **Headless Development Infrastructure**: Immediate development, IDE execution, and emulator testing on headless Fedora Linux servers via secure Xpra virtual display sessions (delivered in Phase 01).

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
| **FR-06** | Secure Authentication | Reuses Glean Hub auth service with single-flight refresh, generation fencing, no automatic POST replays, and Android Keystore–protected vault. |

### 2.2 Non-Functional Requirements (NFR)

| ID | Category | Requirement |
|---|---|---|
| **NFR-01** | Performance | Smooth, responsive UI interactions without main-thread blocking during network or disk I/O. |
| **NFR-02** | Security | Zero unauthenticated network listeners; HTTPS-only external transport; encrypted credential storage; no secrets in logs or code. |
| **NFR-03** | Portability | 100% shared Compose Multiplatform UI across Android and JVM desktop targets. |
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
| `:shared` Module Configuration | `shared/build.gradle.kts`, `shared/gradle.lockfile` (`dev.doggo.shared`, minSdk 26, JVM 17). | Implemented; dependency locking and Room schema dir configured. |
| Common Domain Contract Sources (16 files) | `shared/src/commonMain/kotlin/dev/doggo/shared/contract/*.kt`. | Implemented; models, UI state, actions, errors, auth, sync, and policies complete. |
| Contract Test Suites (4 files) | `shared/src/commonTest/kotlin/dev/doggo/shared/contract/*.kt`. | Implemented; NIST vectors, title policy, account key, and endpoint policy covered. |
| Automated Foundation Test Suite | `tests/test-phase-02-foundation.sh`. | Implemented; 57/57 shell assertions PASS (live Gradle test runner pending downstream phases). |

---

## 5. Phase 03 Requirements & Acceptance: Hub Reuse & Strict Server Compatibility

Phase 03 establishes backend strict synchronization support on Glean Hub, enforcing absolute namespace and route isolation for Doggo while preserving existing Hub applications without behavioral regression.

### 5.1 Strict Server Requirements
- **FR-STRICT-01 (Strict Mode & Canonical Epoch)**: Opt-in `syncMode: "strict-v1"` registered via admin endpoint (`POST /api/v1/strict-apps`); server generates an immutable lowercase UUIDv4 epoch string before publishing the schema.
- **FR-STRICT-02 (Storage Namespace Isolation)**: Dedicated collections using the exact pattern `_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>` with exact composite document key `_id: "userId:rowId"` and compound scan index `{ userId: 1, tableName: 1, rowId: 1 }`.
- **FR-STRICT-03 (Fail-Closed Admission Preflight)**: Admission validation requires uncapped collection, no TTL index, no custom validator, simple or absent collation, and verified scan index before schema publication. A standalone CLI (`strong_sync_admission`) executes identical validation rules.
- **FR-STRICT-04 (Atomic CAS Mutations)**: Single-document conditional writes enforce revision predicates (Create expected 0 -> rev 1 insert-only; Update expected current -> rev current+1 live filter; Delete -> rev current+1 compact tombstone with canonical empty data).
- **FR-STRICT-05 (Durable Replay Confirmation)**: Replay of matching `(opId, digest)` mutations executes conditional write updating `confirmationNonce` under journaled write concern; confirmation requires `matched_count == 1`.
- **FR-STRICT-06 (Legacy Fencing & Immutability)**: Legacy sync routes reject strict apps with HTTP 426 (`protocolUnsupported`); schema mutation attempts reject strict apps with HTTP 400 (`invalidRequest`); strong routes reject non-strict apps and epoch mismatches with HTTP 409 (`epochMismatch`).

### 5.2 Phase 03 Deliverables & Status Distinction

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| Doggo Strict Schema | `integration/glean-hub/doggo-schema.json` | Implemented; verified exact 4-column todos schema. |
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
- **FR-AUTH-02 (Keystore-Protected Credential Custody)**: Platform `CredentialVault` implemented on Android via `AndroidKeystoreVault` using AES-256-GCM with fresh 12-byte random nonces per write and AAD binding (`"doggo_vault_v1:" + accountKey`). Tokens exist only in memory and within the vault; never exposed in `StateFlow`, loggers, or serialized state.
- **FR-AUTH-03 (Single-Flight Refresh & Generation Fencing)**: Concurrent foreground and background refresh calls are deduplicated into a single leader network request via `SingleFlightRefreshCoordinator`. Generation counter increments on quiesce/logout/switch to fence stale async completions and prevent resurrected credentials.
- **FR-AUTH-04 (Prohibited Automatic POST Replays)**: In `authorizedRequest()`, the HTTP auth layer never automatically replays mutating POST requests across 401 token refresh cycles; returns `SafeError.Unauthorized("TOKEN_REFRESHED")`, leaving retry ownership strictly to `SyncCoordinator`.
- **FR-AUTH-05 (Session Lifecycle & App Grant Enforcement)**: Explicit state machine (`UNAUTHENTICATED`, `AUTHENTICATING`, `AUTHENTICATED`, `ACCESS_PENDING`, `REAUTH_REQUIRED`). Enforces `/me.userId` matching login response. Evaluates `/me.apps` for `"doggo"` grant; creates and reconciles access requests if grant is pending.
- **FR-AUTH-06 (Multiplatform Engine Abstraction)**: Common expect/actual factory `createPlatformHttpEngine()` configured with bounded timeouts (10s connect, 20s read/write) and disabled redirects on Android (`OkHttp`) and Desktop JVM (`Java`).

### 6.2 Phase 04 Deliverables & Status Distinction

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| Auth Wire DTOs | `shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-dtos.kt` | Implemented; redacted `toString()`, email normalization, byte length validation. |
| Ktor HTTP Client | `shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-http-client.kt` | Implemented; route classification, dual-auth headers, HTML error sanitization, 10s/20s timeouts. |
| Hub Auth API Client | `shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-client.kt` | Implemented; register, login, refresh, `/me`, and access requests. |
| Auth Session State Machine | `shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-session.kt` | Implemented; state machine, generation fencing, prohibited POST replay, offline restore, local logout. |
| Single-Flight Coordinator | `shared/src/commonMain/kotlin/dev/doggo/shared/auth/single-flight-refresh.kt` | Implemented; leader-based concurrency lock using Mutex and CompletableDeferred. |
| Android HTTP Engine | `shared/src/androidMain/kotlin/dev/doggo/shared/auth/android-http-engine.kt` | Implemented; OkHttp engine with redirects disabled and bounded timeouts. |
| JVM HTTP Engine | `shared/src/jvmMain/kotlin/dev/doggo/shared/auth/jvm-http-engine.kt` | Implemented; Java HTTP engine with connect timeout. |
| Android Keystore Vault | `shared/src/androidMain/kotlin/dev/doggo/shared/auth/android-keystore-vault.kt` | Implemented; Android Keystore–protected AES-256-GCM, fresh 12-byte nonces, AAD binding, atomic rename, status discrimination. |
| Auth Client Unit Tests | `shared/src/commonTest/kotlin/dev/doggo/shared/auth/hub-auth-client-test.kt` | Implemented; 8 unit tests PASS (routes, headers, sanitization). |
| Auth Session Unit Tests | `shared/src/commonTest/kotlin/dev/doggo/shared/auth/hub-auth-session-test.kt` | Implemented; 8 unit tests PASS (lifecycle, fencing, no POST replay, vault write failure). |
| Single-Flight Unit Tests | `shared/src/commonTest/kotlin/dev/doggo/shared/auth/single-flight-refresh-test.kt` | Implemented; 3 unit tests PASS (deduplication, error propagation). |
| Keystore Instrumented Tests | `shared/src/androidInstrumentedTest/kotlin/dev/doggo/shared/auth/android-keystore-vault-test.kt` | Implemented; covers AES-GCM, fresh nonces, AAD tamper rejection, atomic rename. (Deferred to Phase 07). |
| Auth Acceptance Spec | `docs/auth-acceptance.md` | Published; 21 verification criteria across 4 test suites. |
| Auth & Credentials Architecture | `docs/authentication-and-credentials.md` | Published; security invariants, route policies, AAD derivation, vault lifecycle. |

---

## 7. Phase 05 Requirements & Acceptance: Durable Local State & Strict Per-Record Sync

Phase 05 implements local persistence via Room KMP version 1 and SQLite `BundledSQLiteDriver`, strict per-record outbox reconciliation, staged full scans, and crash-safe fail-closed account teardown.

### 7.1 Local Persistence & Sync Requirements
- **FR-STORE-01 (Room Schema & Partitioning)**: Room database version 1 (`DoggoDatabase`) defines 5 stores (`server_base`, `outbox`, `editor_drafts`, `scan_staging`, `account_sync_meta`). Exported schema validated at `shared/schemas/.../1.json`. Destructive fallback is strictly forbidden. Storage is partitioned by canonical `AccountKey` SHA-256 string with path traversal validation.
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
| Room KMP Database & Entities | `shared/src/commonMain/kotlin/dev/doggo/shared/data/` (5 entities, DAO, Database, migrations, RoomAccountStore). | Implemented; schema v1 exported, BundledSQLite driver, atomic `@Transaction` methods. |
| Platform Database Builders & Files | `shared/src/{androidMain,jvmMain}/kotlin/dev/doggo/shared/data/` (Android and JVM database builders and account files). | Implemented; directory partitioning, path traversal guards, atomic wipe markers. |
| Strict Sync Clients & Reconcilers | `shared/src/commonMain/kotlin/dev/doggo/shared/sync/` (HubSyncDtos, HubSyncClient, StagedFullScan, OutboxReconciliation, AccountSyncCoordinator, AccountLeaveProtocol). | Implemented; 3-stage sync, FIFO dispatch, CAS acks, UnknownSuperseded, fail-closed leave. |
| Room Schema Export | `shared/schemas/dev.doggo.shared.data.DoggoDatabase/1.json`. | Implemented; verified canonical schema version 1 export. |
| Local Storage & Sync Specs | `docs/local-storage-and-sync.md`, `docs/sync-acceptance.md`. | Published; complete architecture, lifecycle diagrams, acceptance criteria. |
| Common & JVM Sync Test Suites (6 suites) | `shared/src/commonTest/.../sync/*-test.kt`, `shared/src/jvmTest/.../room-account-store-test.kt`. | Implemented; 89 unit and SQLite tests pass via `./gradlew --dependency-verification=lenient :shared:jvmTest`. |
| Android Room Instrumented Tests | `shared/src/androidInstrumentedTest/.../room-account-store-test.kt`. | Implemented; covers app-private storage and Room transactions. (Deferred to Phase 07). |

---

## 8. Technical Constraints & Dependencies

1. **Host Environment**: Fedora Linux 44 Server (`x86_64`, kernel `7.1.10`), AMD-V hardware virtualization enabled.
2. **JDK Requirements**: AGP 8.x requires a compatible JDK (JDK 17 or JDK 21 LTS). System default Java 25 is not verified for AGP Gradle tasks; Studio bundled JBR or `java-21-openjdk-devel` is required.
3. **Interactive Licensing**: Android SDK licenses must be accepted interactively via `sdkmanager --licenses`; automated blanket acceptance is prohibited.
4. **Interactive GUI Acceptance**: Full visual verification of Android Studio and the emulator framebuffer requires interactive remote workstation attachment via `xpra attach ssh://.../100`.
5. **Live Database Testing**: Full backend acceptance requires a live MongoDB daemon (standalone or replica set) accessible via `MONGO_TEST_URI`.
6. **Instrumented & Hardware Testing**: Keystore encryption and Android Room verification require an active Android device or emulator with Google Play / AOSP platform image.

---

## 9. Traceability & Phased Roadmap

Progress across the Doggo project is tracked in the living [Project Roadmap](./project-roadmap.md):
- **Phase 01 Tooling & Environment**: Scripts and documentation implemented (DONE); runtime GUI gate pending interactive attachment.
- **Phase 02 Frozen Foundation & Contracts**: Staged Gradle build system, SHA-256 pinned wrapper, shared module configuration, common domain contract sources, and test suites implemented (DONE).
- **Phase 03 Hub Reuse & Strict Server**: Backend strict synchronization server, legacy isolation fences, admission preflight, schema integration, and operator tooling implemented across 26 files (Implementation Complete; 36 Rust tests pass; live MongoDB gates deferred to Phase 07).
- **Phase 04 Hub Authentication & Protected Session**: Authentication client, HTTP engine abstraction, session state machine, generation fencing, single-flight refresh, and Android Keystore vault implemented across 14 files (DONE; 19 JVM unit tests PASS; Android Keystore device run and live Hub integration deferred to Phase 07).
- **Phase 05 Durable Local State & Strict Per-Record Sync**: Room KMP database (v1), 5 entities, atomic DAO transactions, BundledSQLite driver, platform account files, staged full scan, FIFO outbox reconciliation, CAS ack matching, UnknownSuperseded, crash recovery, and fail-closed leave protocol implemented across 29 files (Implementation Complete; 89 common/JVM tests PASS; Android device test and live Mongo e2e sync deferred to Phase 07).
- **Phase 06 (Upcoming)**: Compose UI implementation with light theme components and view models.
- **Phases 07–08 (Upcoming)**: Android integration, connected emulator testing, live Hub sync coordination, and release operations.
- **Phases 09–10 (Upcoming)**: Linux RPM and Windows MSI packaging.
