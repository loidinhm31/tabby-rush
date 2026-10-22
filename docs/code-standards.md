# TabbyRush Code Standards & Implementation Guidelines

Last Updated: 2026-10-21
Status: Collaborative task assignees and Room v5 delivered (Phases 01–08); dedicated smoke harnesses prepared in Phase 09. G0 policy validation confirmed 2026-10-21. Native acceptance remains incomplete; production migration is unauthorized.
Related Docs: [Standard Sync Design](./standard-sync-cutover-design.md) · [System Architecture](./system-architecture.md) · [Project Roadmap](./project-roadmap.md) · [Codebase Summary](./codebase-summary.md) · [Windows Desktop Guide](./windows-desktop-guide.md) · [Windows Acceptance](./windows-acceptance.md) · [Headless Android Setup](./headless-android-setup.md) · [Host Tooling Evidence](./host-tooling-evidence.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Sync Acceptance](./sync-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Authentication & Credentials](./authentication-and-credentials.md) · [Announcement Contract](./announcement-contract.md) · [Remote Notification Setup](./remote-notification-setup.md) · [Admin Announcements Acceptance](./evidence/admin-announcements-acceptance.md) · [Integrated Acceptance Evidence](../plans/261021-1552-tabbyrush-task-assignees/reports/04-integrated-acceptance-evidence.md)

---

## 1. Overview & Purpose

This document establishes engineering conventions, coding standards, error handling patterns, and testing requirements across TabbyRush. It reflects the delivered Kotlin Multiplatform, Android, and desktop implementation, including account-scoped sticky task surfaces, while distinguishing standards from unresolved review findings and platform acceptance.

---

## 2. Shell Scripting Standards (Bash)

Stand-alone executable scripts (`scripts/start-xpra.sh`, `scripts/start-android-emulator.sh`, `tests/test-phase-01-tooling.sh`) and sourced environment scripts (`scripts/android-sdk-env.sh`) must adhere to these standards:

### 2.1 Header & Execution Safety
- Executable scripts must use the portable shebang: `#!/usr/bin/env bash`.
- Enforce strict execution safety in standalone executable scripts:
  ```bash
  set -euo pipefail
  ```
- `-e`: Exit immediately if a simple command exits with a non-zero status (subject to Bash conditional execution rules in `if`, `while`, and `||` expressions).
  - `-u`: Treat unset variables as errors and exit immediately.
  - `-o pipefail`: Return the exit status of the last command in the pipeline that failed.
- **Sourced Script Exception**: Sourced environment helpers (such as `scripts/android-sdk-env.sh`) **MUST NOT** invoke `set -euo pipefail`, as doing so would alter the calling developer's shell session options or parent script behavior.

### 2.2 Script Location Resolution
Determine the absolute directory of the executing script reliably:
```bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
```

### 2.3 Variable Quoting & Array Expansion
- Always quote variable expansions: `"$variable"` or `"${variable}"` (never bare `$variable`).
- For optional arrays, use safe empty-array expansion:
  ```bash
  "${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}"
  ```

### 2.4 Input & Port Validation
- Reject ambiguous or non-canonical numbers. Prevent Bash octal interpretation of leading zeros:
  ```bash
  if ! [[ "${PORT}" =~ ^[1-9][0-9]{3,4}$ ]]; then
    echo "Error: Invalid port '${PORT}'." >&2
    exit 1
  fi
  PORT=$((10#${PORT}))
  ```
- Validate display numbers using strict patterns (`^:[0-9]+$`).

### 2.5 Sourced Scripts vs Executable Scripts
Scripts intended to be sourced (such as `scripts/android-sdk-env.sh`) must:
1. Detect direct execution and emit guidance (`if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then ... fi`).
2. Avoid using `exit`; use `return 1` instead.
3. Suppress verbose console output in non-interactive sessions to avoid breaking remote tools (`scp`, `sftp`, `rsync`, Git over SSH).

### 2.6 Subprocess Execution
Replace the script process with the target executable when launching long-running daemons:
```bash
exec emulator -avd "${AVD_NAME}" -port "${PORT}" ...
```

---

## 3. Kotlin Multiplatform & Compose Guidelines

### 3.1 Package Hierarchy
- Root shared package: `dev.tabbyrush.shared.*`
  - Contracts & models: `dev.tabbyrush.shared.contract.*`
  - Authentication & session: `dev.tabbyrush.shared.auth.*`
  - Local database & persistence: `dev.tabbyrush.shared.data.*`
  - Standard sync client: `dev.tabbyrush.shared.sync.*`
  - Theme & tokens: `dev.tabbyrush.shared.theme.*`
  - Compose UI components: `dev.tabbyrush.shared.ui.*`
- Android application: `dev.tabbyrush.android.*`
- Desktop application: `dev.tabbyrush.desktop.*`
  - Linux adapter: `desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/*`
  - Windows adapter: `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/*`
  - Each selected target must register exactly one matching `CredentialVaultProvider` in `META-INF/services`; platform dependencies and lockfiles remain isolated by target.

### 3.2 Immutability & State Management
- UI models must be immutable Kotlin `data class` types.
- UI state updates follow unidirectional data flow: UI components receive an immutable state snapshot and emit actions via callback functions:
  ```kotlin
  @Composable
  fun TodoScreen(
      state: TodoUiState,
      onAction: (TodoAction) -> Unit
  )
  ```
- Shared `commonMain` must never import Android APIs or platform-specific desktop frameworks. KMP adaptations belong in `androidMain` and `jvmMain`; desktop OS adapters belong only in the selected `desktopApp/src/linuxMain` or `desktopApp/src/windowsMain` source set. The desktop build must reject explicit targets that do not match the host before resolving target dependencies.

### 3.3 Naming Conventions
- Types, classes, interfaces, and composable functions: PascalCase.
- Member functions, local functions, and variables: camelCase.
- Constants and enum entries: UPPER_SNAKE_CASE or PascalCase for type-safe sealed objects.
- Source file names: `kebab-case.kt` for Kotlin files, `kebab-case.sh` for shell scripts, `kebab-case.md` for documentation.

### 3.4 Shared Core, Authentication & Standard Sync Invariants
- Common domain contracts must not leak concrete database (Room) or HTTP (Ktor) dependencies into domain signatures.
- Sync/auth routes use the `/api/v1` prefix; sync requests use the approved Bearer, `X-API-Key`, and `X-App-Id` header policy. Mutating POSTs are not automatically replayed after token refresh.
- Preserve unknown raw JSON and apply only fields named by a valid explicit intent mask. Ambiguous legacy intent requires review; never fall back to all-known-field replay.
- Begin every sync round/reconnect with a null-checkpoint full scan; discard stale scan-generation staging after restart. Do not infer deletion from an incomplete scan.
- Clear a submitted local version only for an explicit accepted `(tableName,rowId,version)` tuple. A matching pulled projection is convergence, not operation receipt proof.
- Keep credentials out of unencrypted storage, logs, and `toString()` output; structured serialization is restricted to authorized transport payloads and encrypted vault containers.

### 3.5 Compose UI, Accessibility & Theme Guidelines
- **WCAG 2.1 AA Palette Contrast**: All functional text pairings must mathematically satisfy the WCAG 2.1 AA baseline contrast threshold of >= 4.5:1 (>= 3.0:1 for non-text interactive borders). Dynamic device theming is disabled; rendered UI contrast requires platform device validation.
- **Universal Typography**: Rely on Compose `FontFamily.SansSerif` rather than bundling partial Latin-only TTFs, avoiding Latin glyph restrictions and delegating to platform system fonts; international script rendering and OS font scaling up to 200% require platform device testing.
- **Minimum Touch Targets**: All interactive elements (buttons, checkboxes, chips, row actions) must measure strictly `>= 48.dp`.
- **Non-Color Conveyance**: Completion, error, conflict, and leave states must not be communicated by color alone. Checkboxes must provide visual checkmarks and `stateDescription` semantics; error banners must provide explicit textual guidance.
- **Transient Credential Privacy**: Passwords in UI forms must use `PasswordVisualTransformation` visual masking, emit `semantics { password() }`, and be dereferenced from memory upon form submission, mode toggling, or composable disposal. Password strings in `AuthSubmitAction.toString()` must be redacted with `[REDACTED]`. (JVM string immutability precludes guaranteed memory erasure; device screen-reader behavior unverified).
- **Responsive Hierarchy**: UI must adapt cleanly across breakpoints (<840dp compact vertical stack, >=840dp two-column desktop sidebar).

### 3.6 Temporal Models & Checked Arithmetic Standards
- **Checked 64-bit Integer Arithmetic**: Timestamp and duration calculations must detect overflow rather than wrap, truncate, or silently clamp.
- **Strict Validation Over Normalization**: Reject malformed, out-of-bounds, or contradictory task/deadline/reminder states instead of inventing values or silently coercing them.
- **Timezone & DST Semantics**: Reject impossible calendar dates and nonexistent local times; require explicit offset selection for ambiguous overlaps. Use calendar-aware boundaries rather than fixed 24-hour assumptions.
- **Protocol Scope**: The current Hub client uses standard sync, not strict digest preimages or epoch/fingerprint admission. Do not introduce strict-protocol digest requirements into current sync paths.
### 3.7 Task Surface Contracts & Projection Standards
- Keep task-surface value contracts and pure projections in `shared/src/commonMain`; do not introduce platform APIs, Room, or HTTP dependencies into them.
- **Admission:** Bind every surface read, mutation, navigation request, and publication to `SurfaceAccountToken(accountKey, generation)`. Obtain readiness through `TaskSurfaceAccess`; require the matching active token before acting or publishing. Reject stale-generation work and do not route a surface action to an arbitrary or newly selected account.
- **Desired-state setters:** Surface actions request `completed = true` or `false`; never toggle the value cached by a widget/window. `RoomAccountStore.setCompletedLocally` must read the current task inside the mutation lock and return `Missing`, `Unchanged`, or `Changed`. Missing includes deleted/tombstoned tasks; `Unchanged` performs no write or invalidation; `Changed` preserves current task fields, advances `updatedAt` monotonically, and is the only outcome that should enqueue sync.
- Keep `partitionSurfaceTodos` single-pass, omit deleted records, and preserve input order. Keep `selectNextDeadline` deterministic: overdue before upcoming, then deadline boundary and task ID ascending; retain unresolved-conflict metadata and do not gate selection on reminder enablement.
- Test account-generation rejection, deleted/tombstoned records, duplicate desired state, concurrent field preservation, overdue equality, ties, conflict retention, and reminder-disabled eligibility at contract/store boundaries.

### 3.8 Native Surface Lifecycle & Platform Boundaries
- Keep Glance, notification manager, Android receivers, Compose `Window`, tray APIs, and host-session checks in platform-specific source sets. Shared contracts and account stores must not own OS objects or process lifetimes.
- Own surface observers in the account runtime/controller, not an `Activity` or individual composable. On account leave/wipe, reject new actions/publications, stop observers/work, cancel scheduled surface work and notifications, blank registered widgets, close native sessions, and clear presentation preferences before deleting account data. Await delivery gates when possible; log and surface incomplete cleanup rather than claiming OS-retained content was erased.
- Keep Android component exposure narrow: only the explicitly intended app entry activity and the widget receiver for the exact `APPWIDGET_UPDATE` action may be exported. Surface action, dismiss, time, and reminder receivers remain unexported; do not add exported services/providers, extra widget actions, categories, or data filters. Preserve `scripts/release-security-check.mjs` allowlist/audit invariants and its tests.
- For desktop, closing the compact window hides it; explicit Quit owns runtime teardown. Expose hide/show only when a usable tray exists and retain a visible way to recover the main application when it does not. Treat always-on-top and tray availability as host-session-dependent, not portable guarantees.
- Record lifecycle limitations as explicit findings. The current Cycle 2 review warns that Android `closeRuntimeInternal()` catches/logs a delivery-gate drain timeout and returns before observer-job cancellation or Room closure; `closeActiveRuntime()` nevertheless clears `activeRuntime`. Glance blank/session-close failures are also caught and logged. These are not proof of fail-closed native cleanup. See [System Architecture](./system-architecture.md) and [Security & Recovery](./security-and-recovery.md).

---

## 4. Node.js Integration Script Standards

Integration, provisioning, and smoke testing scripts (`scripts/*.mjs`) must follow these standards:

### 4.1 Module Format & Built-ins
- Use ECMAScript Modules (ESM) with the `.mjs` file extension.
- Use explicit `node:` protocol imports for Node.js built-in modules (`node:fs`, `node:path`, `node:process`).
- Shebang line: `#!/usr/bin/env node`.

### 4.2 CLI Options & Environment Fallbacks
- Provide consistent CLI flag parsing with documented defaults and environment variable fallbacks:
  - Origin: `--origin` flag, fallback `process.env.GLEAN_ORIGIN || 'http://127.0.0.1:3000'`.
  - Admin JWT: `--token` flag, fallback `process.env.GLEAN_ADMIN_TOKEN`.
  - App ID: `--app-id` flag, fallback `process.env.TABBYRUSH_APP_ID || 'tabby-rush'`.
  - API Key: `--api-key` flag, fallback `process.env.TABBYRUSH_API_KEY`.
- **Environment Variable Custody**: While scripts accept `--token` for diagnostic and mock test runners, production automation and CI workflows **MUST** provide credentials via protected environment variables (`GLEAN_ADMIN_TOKEN`, `TABBYRUSH_API_KEY`) rather than bare `argv` parameters, strictly adhering to Section 8 Invariant 12 to prevent exposure in process tables (`/proc/<pid>/cmdline`, `ps`).
- Implement a `--help` / `-h` flag printing usage instructions and exit with code 0.

### 4.3 Key Custody & Security
- Credential output files containing provisioned API keys must be written with restrictive `0600` permissions (`mode: 0o600`).
- When an output file path (`--out`) is specified, avoid echoing sensitive plaintext keys to console output to prevent credential leakage in automated CI logs.

### 4.4 Exit Codes & Error Verification
- Exit with non-zero status codes on failure (`process.exit(1)` or `process.exit(2)`).
- Smoke and health check scripts must not report vacuous success: when credentials are required, missing or rejected credentials must be flagged as failed or skipped, never reported as passing.
- Validate all scripts using `node --check <script>`.

---

## 5. Rust Backend Integration Standards (Glean Hub Sync Plugin)

### 5.1 Legacy Isolation & Compatibility
- **Non-Destructive Schema Evolution**: App schema fields (`sync_mode`, `strict_epoch`) must be optional (`Option<String>`) with `skip_serializing_if = "Option::is_none"`, ensuring legacy apps serialize identically to prior releases.
- **Isolated Handlers & Routes**: Strict endpoints (`/api/v1/sync/:app_id/strong/*`) must be housed in dedicated route and service modules (`routes/strong_sync.rs`, `services/strong_sync_service.rs`), never interleaved into legacy sync service code.
- **Fail-Closed Route Guards**: Legacy routes must be guarded by middleware rejecting strict apps with HTTP 426 (`protocolUnsupported`).

### 5.2 Error Envelopes & Response Consistency
- Handlers must return `StrictSyncError` emitting a unified JSON envelope: `{ "error": string, "code": string }`.
- HTTP status codes must map unambiguously: 400 `invalidRequest`, 401 `unauthorized`, 404 `appNotFound`, 409 `epochMismatch`, 426 `protocolUnsupported`, 500 `internalError`, 503 `notAdmitted`.
### 5.3 Storage & CAS Invariants
- Strict mutation writes must execute atomic compare-and-swap (CAS) queries matching `expectedRevision` and live record filters.
- Replay confirmation writes must conditionally match `(opId, digest)` and verify `matched_count == 1`.
- Strict collection naming targets the canonical application collection: `app<AppId>` (e.g. `appTabbyRush` via `SyncService::collection_name(app_id)`).
- Code must compile cleanly with `cargo clippy --locked --all-targets` with zero warnings.

---

## 6. Kotlin Local Storage & Room Synchronization Standards (Phase 05)

### 6.1 Room Entity Conventions
- Entities must be immutable Kotlin `data class` types annotated with `@Entity(tableName = "...")`.
- All fields must declare explicit column names, SQLite affinities, and nullability matching the exported schema JSON (`shared/schemas/.../1.json`).
- Primary keys must be annotated with `@PrimaryKey` (`autoGenerate = false` for deterministic client/server IDs).
- CommonMain entity classes must remain free of platform-specific imports or annotations.

### 6.2 DAO Transaction Patterns
- Multi-table operations must use Room `@Transaction` methods in `TabbyRushDao` (e.g. `saveTodoAtomic`, `deleteTodoAtomic`, `cancelUnsentCreateAtomic`, `reconcileAcceptedAtomic`, `promoteStagingAtomic`, `freezeOutboxAtomic`).
- Implement optimistic concurrency checks inside transactions where race conditions could occur (e.g. `freezeOutboxAtomic` verifies `localRevision` and `deliveryState == PENDING` before dispatch).

### 6.3 Concurrency & Mutex Serialization
- Store mutations must be serialized by `RoomAccountStore`'s coroutine `mutex.withLock`.
- Synchronization rounds must be serialized by `AccountSyncCoordinator`'s `coordinatorMutex.withLock`.
- In-flight operations must capture coroutine cancellation: asynchronous network dispatches catch `CancellationException` and mark the batch `UNCERTAIN` inside `withContext(NonCancellable)` before rethrowing.

### 6.4 File System Safety & Atomic Metadata
- Account storage directories must be validated via canonical paths (`canonicalDir.toPath().startsWith(canonicalBase.toPath())`) to reject symlinks and directory traversal attacks.
- Critical metadata files (`wipe.marker`, `generation.dat`) must be written to temporary files, synced to disk via `FileOutputStream.fd.sync()`, and moved via `StandardCopyOption.ATOMIC_MOVE`.
- Always close Room database handles before attempting to delete database files. If deletion fails, remain fail-closed with markers intact.

---

## 7. Testing & Quality Assurance Standards

### 7.1 Automated Shell Testing
- Test suites must be executable via standard Bash (`bash tests/test-*.sh`).
- Automated tests must provide explicit assertion tracking (`assert_test "<description>" "<command>"`).
- Test error handling rigorously: assert that invalid arguments, missing binaries, and unauthorized inputs fail cleanly with expected error codes and standard error messages.
- Tests must be idempotent and non-destructive: do not modify system device files, kill unrelated developer processes, or leave orphan background services running.
### 7.2 Unit & Integration Testing
- Kotlin test suites must execute under `./gradlew :shared:jvmTest`.
- Mocks must be strictly limited to network transport adapters (e.g. `MockEngine`); storage and database logic must execute against real SQLite instances (`BundledSQLiteDriver`).
- Android instrumented tests must be housed in `shared/src/androidInstrumentedTest/` and tested via real platform APIs.


### 7.3 Desktop Target & Packaging Verification
- Run desktop tests on the matching native host/target (`-PdesktopTarget=linux` on Linux; `-PdesktopTarget=windows` on Windows); the platform-specific vault suite must exercise the real OS API where available.
- Resolve dependencies from the target-specific lockfile. Use `--write-locks` only when intentionally regenerating a reviewed lockfile, not for ordinary builds.
- Run the corresponding package smoke harness against the distributable and MSI/RPM artifact. Static class/metadata inspection is not a substitute for interactive install, runtime, vault, or accessibility acceptance.
- **Windows Packaging BOM-Free Standard**: All WiX source files (`*.wxs`), launcher configuration files (`TabbyRush.cfg`), and packaged configuration files (`.env`) must be encoded in strictly BOM-free UTF-8. The presence of a UTF-8 BOM (`0xEF 0xBB 0xBF`) causes the native `jpackage` launcher to fail (`Fail to launch JVM`). The packaging pipeline (`scripts/build-windows-remote-msi.ps1`) normalizes these files, and `scripts/windows-package-smoke.ps1` verifies the absence of UTF-8 BOMs.

---

## 8. Security & Isolation Invariants

1. **Zero Secret Leakage**: No API keys, session tokens, passwords, keystores, or private certificates may be committed to Git.
2. **Ignored Configuration Files**: Local user configurations (`local.properties`, `*.keystore`) must never be committed to source control and must be explicitly listed in `.gitignore`.
3. **No Unauthenticated Ports**: Never bind development services, Xpra, or ADB to `0.0.0.0` or public network interfaces.
4. **License Integrity**: Never bypass interactive SDK license reviews with automated `yes` piping.
5. **System Device Safety**: Never modify system device permissions (such as `/dev/kvm`) with `chmod` or `chown`.
6. **No Destructive Database Migration**: Room database must use explicit migrations; destructive fallback is prohibited.
7. **Fail-Closed Teardown Order**: Account logout must write generation and wipe markers outside SQLite before deleting any data, ensuring crash safety.
8. **Windows Credential Scope**: Desktop Windows tokens must use current-user DPAPI; machine-wide protection and plaintext file/registry/preferences fallback are prohibited.
9. **Windows ACL Must Not Be Overclaimed**: An ACL helper that suppresses unsupported-view or owner/permission failures cannot be documented as guaranteeing restrictive ACL enforcement; verify native ACLs separately.
10. **Artifact Signing Status**: Report MSI Authenticode status and digest accurately. An unsigned development installer is not a trusted production release.
11. **Retired Strict Tooling**: Strict-v1/v2/v3 upgrade, rehearsal, and recovery scripts cited by historical docs are removed. The current binary uses ordinary registration and standard sync; strict runbooks are archives only.
12. **Windows Provisioning Directory**: Before credential-file creation, provisioning requires an existing private, non-symlink output directory with protected DACL/owner limited to the current user, `SYSTEM`, and `Administrators`. The script refuses an unsafe directory without changing its permissions; it sets/verifies file DACL before registration POST.
13. **CLI Credential Custody on `argv`**: Administrative tokens, database URIs, and passwords must never be passed as bare command-line arguments. Use protected environment variables or secured configuration files.

---

## 9. Temporal Models, Scheduling & Cutover Standards (Phases 01–12)

### 9.1 Checked 64-bit Integer Arithmetic
- All timestamp, epoch millisecond, and lead-duration calculations must use overflow-checked arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`).
- Silent truncation, bitwise integer overflow wrap, or silent clamping is strictly prohibited across shared Kotlin, Rust backend, and Node.js tooling.

### 9.2 Strict Timezone & DST Resolution
- Calendar dates and times must resolve through `StrictTimeZoneResolver` using platform `ZoneRules`.
- Impossible calendar dates (e.g. Feb 30) and spring-forward gap instants must be rejected with validation errors.
- Overlap ambiguous times during fall-back transitions must require explicit offset selection.
- Overdue calculations must use calendar arithmetic (start of the next calendar day in the captured timezone), never fixed +24h additions.

### 9.3 Claim-Before-Post Reminder Lifecycle
- Native notification delivery must follow the atomic claim-before-post pattern via `ReminderOccurrenceStore` (`reminder_delivery_ledger`).
- Notifications must be claimed in SQLite with a 5-minute grace window before posting to native OS notification services.
- Obsolete, completed, or deleted task notifications must be cancelled at the native OS layer.

### 9.4 Coordinated Quiescence & Teardown
- Account logout or leave must execute composite ordered quiescence (`CompositeLeaveSchedulerCancelGate` on Android, `CompositeDesktopLeaveCancelGate` on Desktop) draining delivery gates before cancelling observers, schedulers, and native notifications.

### 9.5 Standard Conversion & In-Place Mutation Safety
- The conversion operator is source-built separately from the TabbyRush client; tests are not permission to act on a live database.
- A failed mutating run of any kind leaves an exclusive durable claim. There is no automatic expiry or takeover. Recover only through `maintenance.executionRecovery` for the exact `expectedExecutionId`, with `executorStopped: true` plus `executorStopEvidence`, `mongoOperationsDrained: true` plus `mongoDrainEvidence`, and `authorizedActions` containing `"recoverExecution"`.
- Renew and verify the maintenance window before each collection rename and registry compare-and-swap. Do not automatically restart services, release external isolation, or roll back after publication.
- Live cutover requires separate explicit approval, verified backup/restore scope, and an isolation plan. Strict-v2/v3 runbooks are historical archives.

---

## 10. Sticky Task Surface Engineering Standards (Phase 07)

### 10.1 Generation & Admission Token Fencing
- Every interaction from external OS surfaces (Glance widgets, quiet deadline notifications, desktop companion windows) must carry and validate an immutable `SurfaceAccountToken(accountKey, generation)`.
- Handlers must reject requests presenting non-current tokens, mismatched account keys, or negative generations fail-closed without touching SQLite databases.
- Arbitrary account sessions must never be initialized on-demand from untrusted external intents.

### 10.2 Desired-State Atomic Completion
- Surface task toggles must call `store.setCompletedLocally(id, desiredCompleted, nowEpochMs)` specifying the exact desired completion boolean rather than inverting an in-memory snapshot.
- The mutation reloads current state under the database write lock, retaining concurrent title and temporal edits, and advancing `updatedAt` monotonically.
- The call returns `CompletionChange.Changed`, `Unchanged`, or `Missing`. Background network sync is dispatched only on `Changed`.

### 10.3 Native Surface Boundaries & Zero Second Stores
- Sticky task surfaces must project directly from the single authoritative `RoomAccountStore` via `observeTodos()` and `observePersistedSchedule()`.
- Creating secondary synchronization pipelines or duplicate cached task stores is strictly prohibited.
- Surface preferences (window geometry, display coordinates, notification suppression keys) must be stored in presentation-only storage outside Room and must never contain user task titles, descriptions, or account secrets.

### 10.4 Fail-Closed Teardown & Lifecycle Quiescence
- Account sign-out and wipe must enforce structured sequential quiescence:
  1. Transition surface host state to non-Ready (`SurfaceReadiness.Unavailable`).
  2. Drain active surface delivery admissions under timeout.
  3. Cancel and join reactive observation jobs.
  4. Cancel background boundary wake and active notifications.
  5. Blank all installed widget instances and close Glance sessions before deleting databases or removing wipe markers.
- If delivery gate drainage times out, teardown must log and fail closed rather than proceeding with uncoordinated database closure.

Current implementation divergence: Android `closeRuntimeInternal()` catches and logs a drain-timeout exception, then returns before observer-job cancellation or Room closure; `closeActiveRuntime()` nevertheless clears `activeRuntime`. This remains an open safety finding, not acceptable teardown behavior.

### 10.5 Desktop Sticky Companion Compact Sizing & Typography Scaling Exception
- **Desktop Companion Control Sizing**: As an explicit desktop-companion-only exception to the universal 48dp minimum touch target rule (§3.5), desktop sticky window controls use dense mouse/keyboard-appropriate dimensions:
  - Checkboxes and subtask expander toggles render at 22–24dp (`size(22.dp)`).
  - Quick-add input uses compact `BasicTextField` with 32dp minimum height.
  - Header actions ("Open", "Quit", zoom buttons) use compact buttons (24–28dp minimum height).
  - Task row container uses 28dp minimum height (`defaultMinSize(minHeight = 28.dp)`).
- **Accessibility Invariant**: Compact desktop sizing is scoped strictly to the desktop sticky companion window. Semantic labels (`contentDescription`), state descriptions (`stateDescription`), keyboard navigation (Tab/Shift-Tab, Space, Enter), and focus rings remain strictly required.
- **Dynamic & Responsive Text Scaling**: Sticky window provides persistent text zoom (0.50x to 1.50x, step 0.125x) via `CompositionLocalProvider(LocalDensity provides customDensity)`. Stored in presentation properties (`fontScale`). Keyboard shortcuts (`Ctrl+=`, `Ctrl+-`, `Ctrl+0`) and header zoom controls (`A−`, `reset to automatic`, `A+`) provide zero-dependency scaling. When sticky window dimensions change, text automatically scales responsively relative to standard width (360dp) combined with manual zoom (`computeEffectiveStickyFontScale`).

---

## 11. Server Admin Announcements & Remote Security Standards (Phase 12)

### 11.1 Route Structure & Bearer-Only Policy
- **Exact Route Hierarchy**: Static routes (`audience-preview`, `by-idempotency`, `inbox`, `unread`, `stream`) must be registered before dynamic UUID parameters (`/{id}`, `/{installId}`).
- **Bearer-Only Authorization**: Remote announcement endpoints operate under `BEARER_ONLY` policy (`RouteClassification.BEARER_ONLY`). Query-parameter access tokens, session cookies, and API key bypass are strictly prohibited.
- **No Redirects**: All HTTP clients must reject 3xx redirects on authenticated announcement endpoints fail-closed.

### 11.2 Bounded Intake & Digest Integrity
- **Recipients Limit**: Maximum 500 recipients in all audience modes (`selected`, `all`, `admins`). Resolving >500 users rejects with HTTP 413 `audienceTooLarge`.
- **Content Limits**: Title trimmed to 1..200 Unicode scalars and $\le 800$ UTF-8 bytes; body normalized (CRLF to LF) to 1..4,000 scalars and $\le 16,384$ bytes. Rejects NUL bytes and invalid UTF-8.
- **Digest Binding**: Confirmation binds `sha256:{hex}` calculated over canonical JSON `{appId, audience, recipientIds}`. Changed audience membership between preview and create returns HTTP 409 `audienceChanged`.

### 11.3 Protected Firebase Merged Manifest Policy
- **Merged Manifest Audit**: Android security checks must audit the final merged `AndroidManifest.xml` (or parsed APK), not just source manifests, to detect transitive library exports:
  - `FirebaseMessagingService` must be private (`android:exported="false"`) with action `com.google.firebase.MESSAGING_EVENT`.
  - `FirebaseInitProvider` must be private (`android:exported="false"`) with authority `dev.tabbyrush.android.firebaseinitprovider`.
  - `FirebaseInstanceIdReceiver` is permitted only with `permission="com.google.android.c2dm.permission.SEND"`, exact action `com.google.android.c2dm.intent.RECEIVE`, and package category `dev.tabbyrush.android`.
  - Exported analytics receivers (`AppMeasurementReceiver`) are completely prohibited.

### 11.4 In-Memory Cache & Zero Database Persistence
- **In-Memory Sliding Window**: Client-side announcements are stored exclusively in process memory (sliding window max 200 items, dedup capacity 2048) managed by `RemoteInboxController`.
- **Zero Offline Storage**: Storing announcement titles, bodies, or read queues in local Room SQLite databases or SharedPreferences is strictly prohibited.
- **Zero Persisted Read Outbox**: Client read states are sent synchronously or dropped on network failure; there is no offline persistent read outbox.
- **Process Teardown**: Exiting or killing the application purges all in-memory announcement data. Reopening silently hydrates active, unexpired items from the Hub.

### 11.5 MongoDB Storage & TTL Indexing Standards
- **90-Day Retention**: All parent announcements and derived inbox records enforce 90-day retention calculated monotonically from creation (`expiresAt = createdAt + 90 days`).
- **BSON DateTime TTL Indexes**: Collections configure MongoDB TTL indexes on `expiresAt` with `expireAfterSeconds: 0`.
- **Dual-Layer Boundary Enforcement**: Because MongoDB TTL background cleanup is asynchronous (typically executing on 60-second cycles), all client and server query, fetch, rendering, and dispatch paths must enforce strict application-side filtering to exclude records when `expiresAt <= serverNow` (only unexpired records are queryable or renderable). Reading an announcement never extends its expiry.

### 11.6 FCM HTTP v1 Generic Notifications (Android)
- **Announcement-Content Isolation**: Mobile alerts use FCM HTTP v1 generic notifications for Android with channel `tabbyrush.remote.announcements` (importance: Default) and system tag `remote:<messageId>`.
- **Generic Copy Only**: Push payloads contain only generic copy ("You have a new announcement") and opaque metadata (`type: "admin_announcement"`, `appId: "tabby-rush"`, `messageId: "<UUID>"`). Announcement title and body are NEVER transmitted over FCM.
- **Session Reauthorization**: Tapping a notification routes through deep links (`ACTION_VIEW_ANNOUNCEMENT`) and must reauthorize the message ID against the currently authenticated account session before displaying content.
- **Install Proof & Fencing**: Android client encrypts 32-byte cryptographic install proof in app-private no-backup storage (`remote_install_v1.enc`) using an Android Keystore-backed AES-256-GCM key (`tabbyrush_remote_install_v1`), transmitted as `X-Tabby-Install-Proof` header, verified by SHA-256 hash on Glean Hub, and guarded by numeric generation CAS fencing.

### 11.7 Streaming SSE Broker Invariants (Desktop)
- **Broker Framing**: Desktop clients consume live announcements via a streaming SSE broker with `streamEpoch`, monotonic `acceptanceOrdinal`, and ready baseline (`event: ready`).
- **Handshake Baseline**: The stream opens with `event: ready` delivering `streamEpoch`, `baselineAcceptanceOrdinal`, and `serverNow`.
- **Announcement Hints**: Subsequent `event: announcement` events transmit lightweight hints without titles, bodies, or recipient identities.
- **Alert Gating**: Desktop displays at most one generic system alert per active connection and message hint, gated on `acceptanceEpoch == streamEpoch`, `acceptanceOrdinal > baselineAcceptanceOrdinal`, and `liveEligible == true` (emitted within 120s of server acceptance). Android suppresses SSE alerts.

### 11.8 Staged Pilot Boundaries & Platform Scopes
- **Restricted Pilot Scope**: Restricted pilot encompasses Android mobile, Linux desktop, and Web Admin console for cohort sizes $\le 500$ recipients per send.
- **Full Rollout Hold**: Full multiplatform rollout requires native Windows desktop acceptance; Windows MSI packaging and WinRT toast integration are held at Gate G5 on Linux build hosts pending verified clean-host Windows proof.

### 11.9 Release Security Auditor & Widget Provider Invariants
- **Fail-Closed Verification**: `scripts/release-security-check.mjs` enforces Fail-Closed release gating; absence of release artifacts or required Android SDK tools halts release pipelines with exit code 1.
- **Widget Provider Metadata Formats**: `TabbyRushTaskWidgetReceiver` metadata must declare `android.appwidget.provider` pointing to either raw `@xml/task-widget-info` / `@xml/task_widget_info` or compiled `@ref/0x[0-9a-fA-F]+` resource identifiers produced by `apkanalyzer manifest print`. Malformed resource formats (e.g. bare hex, missing `@xml/`, or foreign attributes) fail closed.
- **Permitted AndroidX System Components**: Transitive AndroidX system receivers protected by `android.permission.DUMP` (`DiagnosticsReceiver`, `ProfileInstallReceiver`) and services protected by system permissions (`GlanceRemoteViewsService` with `BIND_REMOTEVIEWS`, `SystemJobService` with `BIND_JOB_SERVICE`) are permitted; all other exported services, receivers, and content providers are strictly prohibited.

---

## 12. Collaborative Task Assignment & Identity Standards (Phases 01–09)

### 12.1 Canonical Identity & TaskRef Everywhere
- **Explicit Value Types**: Use `TaskRef(ownerUserId, rowId)` for task references and `CanonicalRecordRef(appId, ownerUserId, tableName, rowId)` for generic sync records. Bare String row IDs or bare integer keys are strictly prohibited across store methods, view models, intent extras, reminder delivery suppression, Glance widgets, and desktop surfaces.
- **Owner Namespace Isolation**: An assignee's store never treats incoming collaborative tasks as caller-owned; `ownerUserId` identifies the canonical creator. Duplicate row IDs under different owners represent entirely distinct tasks and never collide.

### 12.2 Two-Stage Room v5 Legacy Binding
- **Preserve Legacy State**: Migration 4→5 marks pre-existing private rows with `ownerBinding = LOCAL_ACCOUNT` and nullable `ownerUserId`.
- **Cryptographic Match Gate**: Store initialization requires an authenticated or restored `AccountIdentity` and verifies `AccountKey.create(origin, userId)` matches the partition before binding legacy rows atomically.
- **Local-Only Boundary**: Unresolved legacy rows display offline for continuity, but fail closed against outgoing sync dispatch or shared assignment actions.

### 12.3 Privacy-First Revocation Purge & Non-Replayable Evidence
- **Authoritative Access Loss**: When access is revoked or denied, the client immediately removes the active task projection, cancels scheduled reminders and notifications, and purges `server_base`, `editor_drafts`, and pending `outbox` records for that task from active storage.
- **Zero Retained Task Content**: `access_loss_evidence` stores only audit metadata: `ownerUserId`, `tableName`, `rowId`, `opId`, `baseVersion`, `submittedVersion`, `occurredEpochMs`, and `reason` (`REVOCATION`). Retaining task titles, deadlines, notes, or raw JSON payloads in evidence tables is strictly prohibited.
- **No Auto-Replay on Regrant**: A fresh grant restores the live canonical task as currently stored on the server. Purged unsent drafts or outbox operations are never resurrected or automatically replayed.

### 12.4 Assignee Capability & Privilege Invariants
- **Editable Allowlist**: Assignees may edit only fields explicitly registered in `assignmentPolicy.editableFields` (`title`, `completed`, `deadlineDate`, `deadlineTime`, etc.). Modifying non-editable, structural, or unknown JSON fields is rejected.
- **Creator-Only Operations**: Creating subtasks, reparenting, task deletion, tombstone restoration, and assignee management (`taskAssignees`) are strictly creator-only.
- **Error Code Typing**: Privileged mutation attempts while retaining read access return `OPERATION_FORBIDDEN` (preserving valid local access). Loss of read access returns `ACCESS_DENIED` and triggers local purge.

### 12.5 Whole-App Capability Cutoff (HTTP 426)
- **Pre-Mutation Enforcement**: When `assignmentPolicy` version 1 is active on Glean Hub, any `/push`, `/pull`, or `/delta` request lacking `collaborationVersion: 1` fails closed with HTTP 426 `COLLABORATION_REQUIRED` before processing any records. Downgrading to unsafe private sync is prohibited.
