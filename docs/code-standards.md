# Doggo Code Standards & Implementation Guidelines

Last Updated: 2026-10-14
Phase: Phases 01–10 implementation conventions; desktop installed-runtime acceptance remains pending
Related Docs: [System Architecture](./system-architecture.md) · [Project Roadmap](./project-roadmap.md) · [Codebase Summary](./codebase-summary.md) · [Windows Desktop Guide](./windows-desktop-guide.md) · [Windows Acceptance](./windows-acceptance.md) · [Headless Android Setup](./headless-android-setup.md) · [Host Tooling Evidence](./host-tooling-evidence.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Sync Acceptance](./sync-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [Authentication & Credentials](./authentication-and-credentials.md)

---

## 1. Overview & Purpose

This document establishes engineering conventions, coding standards, error handling patterns, and testing requirements across the Doggo repository. Standards defined here are established in Phase 01 shell tooling and provide the foundation for subsequent Kotlin Multiplatform phases.

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
- Root shared package: `dev.doggo.shared.*`
  - Contracts & models: `dev.doggo.shared.contract.*`
  - Authentication & session: `dev.doggo.shared.auth.*`
  - Local database & persistence: `dev.doggo.shared.data.*`
  - Strict sync protocol: `dev.doggo.shared.sync.*`
  - Theme & tokens: `dev.doggo.shared.theme.*`
  - Compose UI components: `dev.doggo.shared.ui.*`
- Android application: `dev.doggo.android.*`
- Desktop application: `dev.doggo.desktop.*`
  - Linux adapter: `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/*`
  - Windows adapter: `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/*`
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

### 3.4 Strict Sync & Digest Invariants
- Common contract interfaces must not leak concrete database (Room) or HTTP (Ktor) dependencies into domain signatures.
- Canonical digest calculation must use explicit length-framed big-endian fields matching the backend Rust implementation.
- Credentials (`CredentialBundle`, session tokens, passwords) must never be serialized to unencrypted storage, diagnostic logs, or `toString()` output; structured serialization is restricted exclusively to authorized transport payloads and encrypted vault containers.

### 3.5 Compose UI, Accessibility & Theme Guidelines
- **WCAG 2.1 AA Palette Contrast**: All functional text pairings must mathematically satisfy the WCAG 2.1 AA baseline contrast threshold of >= 4.5:1 (>= 3.0:1 for non-text interactive borders). Dynamic device theming is disabled; rendered UI contrast requires platform device validation.
- **Universal Typography**: Rely on Compose `FontFamily.SansSerif` rather than bundling partial Latin-only TTFs, avoiding Latin glyph restrictions and delegating to platform system fonts; international script rendering and OS font scaling up to 200% require platform device testing.
- **Minimum Touch Targets**: All interactive elements (buttons, checkboxes, chips, row actions) must measure strictly `>= 48.dp`.
- **Non-Color Conveyance**: Completion, error, conflict, and leave states must not be communicated by color alone. Checkboxes must provide visual checkmarks and `stateDescription` semantics; error banners must provide explicit textual guidance.
- **Transient Credential Privacy**: Passwords in UI forms must use `PasswordVisualTransformation` visual masking, emit `semantics { password() }`, and be dereferenced from memory upon form submission, mode toggling, or composable disposal. Password strings in `AuthSubmitAction.toString()` must be redacted with `[REDACTED]`. (JVM string immutability precludes guaranteed memory erasure; device screen-reader behavior unverified).
- **Responsive Hierarchy**: UI must adapt cleanly across breakpoints (<840dp compact vertical stack, >=840dp two-column desktop sidebar).

### 3.6 Strict-v2 Temporal Model & Checked Arithmetic Standards
- **Checked 64-bit Integer Arithmetic**: All time and duration conversions (e.g. lead minutes to milliseconds, repeat count interval offsets, first/last occurrence anchors) must use checked arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd` in Kotlin; `checked_mul`, `checked_sub`, `checked_add` in Rust; boundary range checks in Node.js). Integer overflow wrapping at intermediate or terminal calculations must fail closed and reject the record.
- **Strict Validation Over Normalization**: Wire payloads must reject malformed, out-of-bounds, or contradictory states (e.g. non-repeating `reminderCount == 1` with non-null `reminderIntervalMinutes`, repeating `reminderCount > 1` with null or interval < 15 minutes, negative `createdAt` or `reminderLeadMinutes`, last scheduled reminder exceeding deadline anchor). Implementations must never silently normalize or coerce invalid wire values.
- **Canonical Binary Digest Preimages**:
  - Framed identity prefix: 8 length-prefixed strings (domain `strict-v2`, appId, userId, epoch, tableName, rowId, opId, kind) + 8-byte big-endian signed 64-bit revision.
  - Data payload: Exactly 0 bytes for `DELETE` operations. For `CREATE` and `UPDATE`, 4 base fields followed by 12 strict-v2 fields in canonical table order with explicit 1-byte presence tags (`0x00`/`0x01`).
- **Automated Schema Fingerprint Parity**: Automated test runners across all platforms must assert that SHA-256 digests derived from canonical JSON schema definitions match frozen constants (`3361a80f...` for v1, `5f539551...` for v2) preventing unreviewed schema drift.
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
  - App ID: `--app-id` flag, fallback `process.env.DOGGO_APP_ID || 'doggo'`.
  - API Key: `--api-key` flag, fallback `process.env.DOGGO_API_KEY`.
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
- Strict collection naming targets the designated application collection: `app_<appId>` (e.g. `app_doggo`).
- Code must compile cleanly with `cargo clippy --locked --all-targets` with zero warnings.

---

## 6. Kotlin Local Storage & Room Synchronization Standards (Phase 05)

### 6.1 Room Entity Conventions
- Entities must be immutable Kotlin `data class` types annotated with `@Entity(tableName = "...")`.
- All fields must declare explicit column names, SQLite affinities, and nullability matching the exported schema JSON (`shared/schemas/.../1.json`).
- Primary keys must be annotated with `@PrimaryKey` (`autoGenerate = false` for deterministic client/server IDs).
- CommonMain entity classes must remain free of platform-specific imports or annotations.

### 6.2 DAO Transaction Patterns
- Multi-table operations must use Room `@Transaction` methods in `DoggoDao` (e.g. `saveTodoAtomic`, `deleteTodoAtomic`, `cancelUnsentCreateAtomic`, `reconcileAcceptedAtomic`, `promoteStagingAtomic`, `freezeOutboxAtomic`).
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
