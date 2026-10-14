# Doggo System Architecture

Last Updated: 2026-10-14
Phase: Phase 05 Durable Local State & Strict Per-Record Sync (Implementation Complete; Runtime Acceptance Pending)
Related Docs: [System Architecture](./system-architecture.md) · [Headless Android Setup](./headless-android-setup.md) · [Host Tooling Evidence](./host-tooling-evidence.md) · [Project Roadmap](./project-roadmap.md) · [Codebase Summary](./codebase-summary.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Sync Acceptance](./sync-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md) · [Auth Acceptance](./auth-acceptance.md) · [Authentication & Credentials](./authentication-and-credentials.md)

---

## 1. Architectural Scope & Principles

Doggo is designed as an Android-first shared Compose Multiplatform application developed on a headless Linux host. Development and validation rely on an isolated virtualization architecture that enables full graphical IDE and Android emulator capabilities without installing desktop environments (GNOME/KDE) or exposing insecure network services.

### Core Architectural Principles
1. **Headless Isolation**: The host operates in headless server boot mode. Graphical access is strictly encapsulated within user-isolated virtual display servers.
2. **Encrypted Transport Only**: All remote graphical interaction transits SSH tunnels with out-of-band host key verification. Zero public TCP listeners are permitted.
3. **Hardware-Accelerated Emulation**: Android virtual devices leverage host Linux KVM (`/dev/kvm`) acceleration directly without compromising system device permissions.
4. **Idempotent Tooling**: Host management scripts are deterministic, side-effect free on error, and safe for automated or repetitive execution.
5. **Durable Local State & Strict Sync**: Local mutations persist immediately in SQLite before network dispatch; outbox enforces FIFO per-row serialization; remote updates promote monotonically.

---

## 2. Infrastructure & Virtualization Architecture

### Component Topology

```mermaid
flowchart TD
  subgraph LocalWorkstation["Remote Developer Workstation"]
    Client["Xpra Client / SSH Client"]
  end

  subgraph SSHBoundary["Network Boundary (Port 22 SSH)"]
    Tunnel["Encrypted SSH Tunnel"]
  end

  subgraph HostServer["Fedora 44 Server (Headless)"]
    subgraph XpraSubsystem["Virtual Display Subsystem (:100)"]
      XpraServer["Xpra Display Daemon\n(--html=off, --mdns=no)"]
      UnixSocket["User Socket: /run/user/UID/xpra/100"]
      XtermSession["Child xterm Shell\n(Authenticates XAUTHORITY)"]
    end

    subgraph AndroidSubsystem["Android Tooling Environment"]
      Studio["Android Studio GUI\n(~/.local/share/android-studio)"]
      Emulator["Android Emulator\n(Port 5554, AVD: doggo_android)"]
      SDK["Android SDK\n($HOME/Android/Sdk)"]
    end

    subgraph KernelResources["Host Hardware & Kernel"]
      KVM["KVM Hardware Accel\n(/dev/kvm, AMD-V, API 12)"]
      GPU["GPU Controller\n(AMD Radeon 780M / SwiftShader)"]
    end
  end

  Client -->|SSH Tunnel| Tunnel
  Tunnel -->|SSH Forward| XpraServer
  XpraServer --- UnixSocket
  XpraServer --> XtermSession
  XtermSession --> Studio
  XtermSession --> Emulator
  Emulator --> KVM
  Emulator --> GPU
  Studio --> SDK
  Emulator --> SDK
```

---

## 3. Subsystem Breakdown

### 3.1 Remote Display Subsystem (Xpra)
- **Display Identifier**: Dedicated display `:100` managed by `scripts/start-xpra.sh`.
- **Socket Isolation**: UNIX domain socket located in `/run/user/$(id -u)/xpra/` (mode `0700`).
- **Security Flags**: `--bind-tcp` omitted; `--html=off`; `--mdns=no`.
- **Authentication**: Spawns an authenticated child `xterm` shell ensuring child processes inherit valid `DISPLAY` and `XAUTHORITY`.

### 3.2 Android SDK & Tooling Subsystem
Configured through `scripts/android-sdk-env.sh`:
- **`ANDROID_HOME`**: Points to `$HOME/Android/Sdk`.
- **Tool Resolution**: Adds `cmdline-tools/latest/bin`, `platform-tools`, and `emulator` to `PATH`.
- **Java Compatibility**: Respects existing `JAVA_HOME`; detects Studio JBR or JDK 21 LTS; leaves `JAVA_HOME` unset if only host Java 25 is present.

### 3.3 Emulator Subsystem
Managed via `scripts/start-android-emulator.sh`:
- **Port Allocation**: Strictly bounded to decimal even integers in `5554` to `5682`.
- **Hardware Acceleration**: Checks `/dev/kvm` read/write access (AMD-V, KVM API 12).
- **Rendering Fallback**: Defaults to `-gpu auto`, supporting `-gpu swiftshader` if GLX rendering fails.
- **Headless Mode**: Supports `EMULATOR_HEADLESS=1` for automated CI runs.

---

## 4. Multiplatform Application & Sync Architecture

```mermaid
flowchart TD
  subgraph ClientApp["Client Layer"]
    AndroidApp["Android App\n(androidApp / Phase 07)"]
    DesktopApp["Desktop App\n(desktopApp / Phase 09-10)"]
  end

  subgraph SharedKMP["Shared Core (shared/)"]
    UI["Compose Multiplatform UI\n(Material 3 / Light Theme)"]
    State["Todo State & Actions\n(TodoUiState / TodoAction)"]

    subgraph LocalPersistence["Local Persistence & Sync (Phase 05)"]
      Coordinator["AccountSyncCoordinator\n(3-Stage Sync / Mutex)"]
      Store["RoomAccountStore\n(Store Mutex & Atomic Ops)"]
      RoomDB[("DoggoDatabase (Room v1)\nserver_base | outbox\neditor_drafts | scan_staging\naccount_sync_meta")]
      Reconciliation["OutboxReconciliation\n(FIFO Dispatch / CAS Ack)"]
      Scan["StagedFullScan\n(Pre-Scan Check / Promotion)"]
      LeaveProtocol["AccountLeaveProtocol\n(Fail-Closed Teardown)"]
      AccountFiles["Platform Account Files\n(Android / JVM Partitioning)"]
    end

    subgraph AuthSubsystem["Auth & Protected Session Subsystem (Phase 04)"]
      AuthSession["HubAuthSession\n(State Machine & Gen Fencing)"]
      SingleFlight["SingleFlightRefreshCoordinator\n(Leader Concurrency Lock)"]
      AuthClient["HubAuthClient\n(Auth & Access Operations)"]
      HttpClient["HubHttpClient\n(Allowlist & Dual-Auth Headers)"]
      PlatformEngine["Platform Engine\n(Android: OkHttp / JVM: Java)"]
      Vault["CredentialVault\n(Android: AndroidKeystoreVault)"]
    end
  end

  subgraph HubBackend["Glean Hub Backend (Axum / Rust)"]
    AuthRoutes["Hub Auth Routes\n(/api/v1/auth/{register,login,refresh,me})"]
    AccessRoutes["App Access Routes\n(/api/v1/apps/access-requests*)"]
    DualAuth["Dual Auth Layer\n(Bearer JWT + X-API-Key + X-App-Id)"]
    LegacyGuard["Legacy Sync Mode Guard\n(Rejects strict apps with 426)"]
    ScopeGuard["Strong Route Scope Guard\n(Enforces path/header app_id coherence)"]
    StrictRoutes["Strict Routes\n(/api/v1/sync/:app_id/strong/{bootstrap,push,pull})"]
    AdminStrict["Admin Strict Registration\n(POST /api/v1/strict-apps)"]
    AdmissionHelper["Admission Validator\n(Exact index, topology, uncapped, no TTL)"]
    MongoDB["MongoDB Dedicated Namespace\n(_strict_v1_5_doggo_<epoch>)"]
  end

  AndroidApp --> UI
  DesktopApp --> UI
  UI --> State
  State --> Store
  Store --> RoomDB
  Store --> AccountFiles
  Coordinator --> Store
  Coordinator --> Reconciliation
  Coordinator --> Scan
  Coordinator --> LeaveProtocol
  Coordinator --> AuthSession
  Reconciliation --> RoomDB
  Scan --> RoomDB
  LeaveProtocol --> Store
  LeaveProtocol --> AccountFiles
  LeaveProtocol --> Vault
  AuthSession --> SingleFlight
  AuthSession --> AuthClient
  AuthSession --> Vault
  AuthClient --> HttpClient
  HttpClient --> PlatformEngine
  PlatformEngine -->|Public Auth / Refresh| AuthRoutes
  PlatformEngine -->|Bearer Token| AccessRoutes
  PlatformEngine -->|Bearer + AppKey + AppId| DualAuth
  DualAuth --> ScopeGuard
  ScopeGuard --> StrictRoutes
  StrictRoutes --> MongoDB
  DualAuth --> LegacyGuard
  AdminStrict --> AdmissionHelper
  AdmissionHelper --> MongoDB
```

### 4.1 Storage Namespace & Backend Isolation
Strict sync completely segregates Doggo data from legacy Hub storage:
- **Naming Pattern**: `_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>` (e.g. `_strict_v1_5_doggo_550e8400e29b41d4a716446655440000`).
- **Document Key (`_id`)**: Exact composite key `userId:rowId` preventing cross-user collisions.
- **Scan Index**: Exact compound index `{ userId: 1, tableName: 1, rowId: 1 }` with simple collation.
- **Publication-Last Registration**: Schema entry published only after storage and indexes pass admission.

### 4.2 Route Fencing & Middleware Isolation
- **Legacy Guard (`legacy_sync_mode_guard`)**: Rejects strict apps requesting legacy sync routes with HTTP 426 (`protocolUnsupported`).
- **Scope Guard (`strong_route_scope_guard`)**: Enforces exact match between `X-App-Id` header and URL path parameter `:app_id`.
- **Schema Immutability**: `PUT /api/v1/apps/:app_id/schema` updates reject strict apps with HTTP 400.

### 4.3 Mutation Semantics & Replay Confirmation
- **Create**: Conditional insert-only write requiring expectedRevision = 0, setting revision = 1 and atomic `lastOp`.
- **Update**: Conditional write matching current `revision == expectedRevision` and live row filter (`deleted: false`), incrementing revision by 1.
- **Delete**: Conditional write matching current `revision == expectedRevision`, incrementing revision by 1, replacing data with canonical empty document, and recording permanent tombstone.
- **Replay Confirmation**: Identical `(opId, digest)` mutations perform a conditional write updating `confirmationNonce` with journaled write concern, requiring `matched_count == 1`.

### 4.4 Durability Policy & Backend Topology Observation
- **Replica Set**: `{ w: "majority", j: true }` write concern and `majority` read concern.
- **Standalone**: `{ w: 1, j: true }` write concern and `local` read concern.
- **Implementation Observation**: In `services/strong_sync_service.rs`, `get_strict_collection` runs `hello` to inspect topology and falls back to standalone (`w: 1, local`) if `hello` errors. This behavior is flagged for downstream review to ensure fail-closed topology verification rather than transient fail-open on replica sets.

### 4.5 Hub Authentication & Protected Session Subsystem (Phase 04)
- **Session State Machine (`HubAuthSession`)**: `UNAUTHENTICATED`, `AUTHENTICATING`, `AUTHENTICATED`, `ACCESS_PENDING`, `REAUTH_REQUIRED`. Evaluates `/me.apps` for `"doggo"` grant.
- **Single-Flight Refresh (`SingleFlightRefreshCoordinator`)**: Deduplicates simultaneous refresh requests to a single leader network request.
- **Generation Fencing**: Atomic generation counter fences stale async completions on quiesce/logout/switch.
- **Prohibited Automatic POST Replays**: `authorizedRequest()` refreshes tokens on HTTP 401 but never replays mutating POST requests.
- **Android Keystore Vault (`AndroidKeystoreVault`)**: AES-256-GCM encryption per account key (`doggo_vault_key_{accountKey}`), fresh 12-byte random nonces per write, AAD binding (`"doggo_vault_v1:" + accountKey`), atomic file replacement.

### 4.6 Local Storage & Strict Synchronization Subsystem (Phase 05)

1. **Room KMP Persistence (`DoggoDatabase`)**:
   - Backed by `BundledSQLiteDriver`. Destructive migration is strictly forbidden; schema version 1 is validated against exported artifact `shared/schemas/.../1.json`.
   - Five stores: `server_base` (authoritative base & tombstones), `outbox` (queued/frozen strict mutations), `editor_drafts` (uncommitted local edits), `scan_staging` (cursor pagination staging), `account_sync_meta` (account sync metadata and active epoch).
2. **Account Directory Partitioning & Filesystem Model**:
   - Partitioned strictly by canonical `AccountKey` SHA-256 string.
   - `AndroidAccountFiles` and `JvmAccountFiles` verify `canonicalFile.toPath().startsWith(canonicalBase.toPath())` to reject path traversal outside the base root (intra-tree symlinks and TOCTOU races are outside this check).
   - Critical metadata writes (`wipe.marker`, `generation.dat`) flush file bytes via `fsync()` before atomic rename (`StandardCopyOption.ATOMIC_MOVE`).
3. **Local Mutation Lifecycle & FIFO Dispatch**:
   - Atomic local saves (`saveTodoAtomic`, `deleteTodoAtomic`) persist drafts and outbox entries under the store mutex before returning.
   - Only never-dispatched `PENDING` outbox operations coalesce; unsent create-delete cancels locally without network traffic.
   - Per-row FIFO: Outbox operations dispatch at most one operation per `rowId` in FIFO order (`localRevision`). If the head operation on a row is `DISPATCHING`, `CONFLICT`, or `UNKNOWN_SUPERSEDED`, all successor operations on that row are blocked.
   - Dispatch freezing: `prepareAndFreezeBatch` recomputes digest and expectedRevision from current base and executes `freezeOutboxAtomic` CAS transaction. `UNCERTAIN` retries strictly preserve original frozen fields.
4. **Staged Full Scan & Monotonic Promotion**:
   - 3-stage sync loop: Initial scan -> Push eligible outbox -> Final scan.
   - Pagination stages rows in SQLite `scan_staging`; detects loops, batch overflow, non-progressing cursors, and epoch mismatches.
   - Pre-scan identity check: All confirmed records in `server_base` sharing the `activeEpoch` must appear in staging; missing records trigger `SafeError.ResetRequired("CONSISTENCY_ERROR")`.
   - Monotonic promotion: Higher revisions promote; equal revisions check logical equivalence (divergent data triggers `SafeError.ProtocolIncompatible("DIVERGENT_REVISION")`); lower revisions retain higher local base. Promoted atomically via `promoteStagingAtomic`.
5. **Exact CAS-Ack Matching & Draft Preservation**:
   - Acknowledges an outbox op if and only if `opId`, `rowId`, and `digest` match, status is in `[accepted, replayed]`, and `acceptedRevision == expectedRevision + 1`.
   - Monotonically advances `server_base`. Retains `editor_drafts` if `draft.localRevision > outbox.localRevision`.
6. **UnknownSuperseded Disambiguation**:
   - Conflicts returned for operations with prior uncertain delivery history are classified as `UNKNOWN_SUPERSEDED` (the mutation may have committed remotely before being superseded). User workflows: `discardConflict`, `reapplyConflict`, `copyAsNew`.
7. **Crash Recovery & Interrupted Dispatches**:
   - `recoverInterruptedDispatches()` resets leftover `DISPATCHING` operations to `UNCERTAIN` on startup or round start.
   - Coroutine push cancellation uses `withContext(NonCancellable)` to mark dispatched batches `UNCERTAIN`.
8. **Fail-Closed Account Leave & Wipe Protocol**:
   - Teardown checks 5 unresolved work categories (`drafts`, `pending`, `uncertain`, `conflict`, `unknownSuperseded`).
   - Strict 7-step sequence: freeze store -> write generation and `wipe.marker` outside DB -> cancel/await schedulers -> close Room -> delete DB files -> clear vault -> clear marker.
   - Startup recovery (`completePendingWipe`) inspects `wipe.marker` and finishes teardown fail-closed before any account opens.

### 4.7 Shared Compose UI & Accessible Light Theme Subsystem (Phase 06)

1. **Light Theme & Design Tokens (`DoggoTheme`, `DoggoColors`)**:
   - Material 3 `LightColorScheme` mapping with custom semantic extensions (`DoggoCustomColors`).
   - Dynamic colors are disabled to avoid overriding calculated token ratios.
   - Calculated high-contrast pairings: 16 verified opaque token pairs exceeding 4.5:1 (text) and 3.0:1 (outline borders). Overall rendered control contrast across OEM devices is deferred to Phase 07.
2. **Platform-Universal Typography (`DoggoTypography`)**:
   - Pinned 12sp–34sp type scale built on Compose `FontFamily.SansSerif`.
   - Documented omission of upstream Latin-only binary font assets (`font-provenance.md`), delegating font resolution to platform system fonts. Broad Unicode script rendering, TalkBack/Orca, and OS-level 200% scaling require platform testing.
3. **Responsive Breakpoint Layout (`DoggoApp`)**:
   - Compact mode (<840dp): Vertical layout with warm cream header and scrolling list. Note: compact top bar is non-scrolling in `Scaffold.topBar`, tracked for Phase 07 200% font scale verification.
   - Expanded desktop mode (>=840dp): Two-column layout with fixed-width deep teal navigation sidebar (280dp) and constrained-width task workspace (max 760dp).
4. **Credential Security & Privacy in UI (`AccountForm`)**:
   - Masked inputs with `PasswordVisualTransformation` and `semantics { password() }`.
   - Transient password state is held in component memory and dereferenced upon submission, mode switch, or composable disposal (JVM string immutability precludes guaranteed memory erasure; device screen-reader behavior unverified).
   - `AuthSubmitAction.toString()` masks passwords with `[REDACTED]` to prevent leakage in diagnostic logs.
5. **Task Management & Dialogs**:
   - `TodoList`: LazyColumn with stable keys (`rowId`), non-color completion checkboxes (`stateDescription`), and >=48dp touch targets.
   - `TodoEditorDialog`: Modal task creation/editing dialog with `TitlePolicy` validation (1–200 Unicode scalars).
   - `SyncFeedback`: Real-time sync status badges, pause/reset guidance, and relative/UTC reconciliation timestamp display.
   - `ConflictReviewDialog`: Side-by-side local vs remote diff inspection with Discard, Reapply, CopyAsNew, and Decide Later workflows.
   - `LeaveGuardDialog`: Fail-closed sign-out confirmation warning displaying unsynced draft, pending, uncertain, and conflict tallies.
   - `LicenseNoticesDialog`: In-app attribution viewer for bundled open-source dependencies.
---

## 5. Security & Isolation Invariants

1. **Virtual Display Network Policy**: Script policy omits `--bind-tcp` and specifies `--html=off --mdns=no`. A smoke test on display `:199` observed zero listening TCP sockets associated with Xpra.
2. **Device Nodes**: `/dev/kvm` permissions (`0666` on host) are managed by system udev rules; scripts do not run `chmod` on system devices.
3. **Privilege Separation**: Android Studio, emulator, and build tasks execute as unprivileged user (`loidinh`).
4. **Credential Safety**: Authentication tokens and keystores are never written to version control; `.gitignore` strictly excludes `local.properties`, keystores, and credentials.
5. **Memory-Only Unpersisted Secrets**: Passwords and raw credential tokens are excluded from logs, error envelopes, and toString representations.
6. **Strict Storage Isolation**: Strict-mode collections (`_strict_v1_*`) are strictly unreachable by legacy sync routes, background cleanup jobs, and older Hub binaries.
7. **Key Custody & Perms**: Provisioning outputs write API keys with strict `0600` file permissions; scripts support protected environment variables (`GLEAN_ADMIN_TOKEN`, `DOGGO_APP_KEY`) to prevent command-line argument exposure.
8. **Opaque In-Memory Credential Custody**: Tokens exist strictly in memory and within the Keystore-protected vault. `StateFlow` and public state models emit only `AccountIdentity`, `AccountKey`, and `AuthState`; `CredentialBundle.toString()` redacts token strings.
9. **Single-Flight Generation Fencing**: Foreground and background refresh calls are deduplicated into a single network operation; generation fencing discards stale async completions on quiesce or logout.
10. **Prohibited Automatic POST Replays**: The HTTP auth layer never automatically replays mutating POST requests across 401 refresh cycles; coordinator controls retry idempotency.
11. **Cryptographic AAD Binding & Nonce Freshness**: Android Keystore vault generates a fresh 12-byte random nonce per write and cryptographically binds ciphertext to the account key via Authenticated Additional Data (AAD).
12. **Strict Origin & Route Allowlisting**: Outbound requests are bound to `canonicalOrigin` and classified into public, bearer-only, and dual-auth strict routes; non-allowlisted and legacy endpoints fail closed locally.
13. **Zero Destructive Migration**: Room database version 1 uses explicit migrations; `fallbackToDestructiveMigration()` is forbidden.
14. **Per-Row FIFO Queueing & Head Blocking**: Outbox operations dispatch at most one operation per `rowId` in FIFO order. A head operation in `DISPATCHING`, `CONFLICT`, or `UNKNOWN_SUPERSEDED` state blocks all successors on that row.
15. **Exact CAS-Ack Matching & Monotonic Promotion**: Acknowledgements require exact 5-tuple verification. Staged scans verify pre-scan confirmed identity presence; promotion monotonically advances confirmed base without regressing.
16. **Fail-Closed Teardown & External Marker**: Account leave freezes edits, writes generation and `wipe.marker` outside SQLite before deleting files, and requires startup recovery to complete any interrupted wipe before reopening an account.
17. **Transient Password Privacy**: Password fields configure `PasswordVisualTransformation` visual masking, emit `semantics { password() }`, and dereference transient strings upon submission, mode switch, or disposal; `AuthSubmitAction.toString()` masks passwords with `[REDACTED]`. (JVM string immutability precludes guaranteed memory erasure; device screen-reader behavior unverified).
18. **WCAG 2.1 AA Palette Contrast**: Baseline mathematical pairing verified for 16 opaque token pairs (text >= 4.5:1, interactive outline borders >= 3.0:1); decorative mint is restricted to non-text accents. Rendered UI contrast across devices is deferred to Phase 07.
19. **Non-Color Conveyance**: Task completion, conflict, error, and leave status indicators provide explicit icons, text descriptions, and accessibility state descriptions without relying solely on color.
