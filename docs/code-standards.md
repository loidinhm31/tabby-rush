# Doggo Code Standards & Implementation Guidelines

Last Updated: 2026-10-13  
Phase: Phase 01 Headless Tooling & Android Environment  
Related Docs: [System Architecture](./system-architecture.md) · [Headless Android Setup](./headless-android-setup.md) · [Host Tooling Evidence](./host-tooling-evidence.md) · [Codebase Summary](./codebase-summary.md)

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
- For optional arrays, use safe empty-array expansion to avoid unbound variable errors under `set -u` on older Bash versions:
  ```bash
  "${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}"
  ```

### 2.4 Input & Port Validation
- Reject ambiguous or non-canonical numbers. Prevent Bash octal interpretation of leading zeros (e.g. `08` or `05554`):
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
1. Detect direct execution and emit guidance:
   ```bash
   if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
     echo "Notice: This script is intended to be sourced. Run: source $0" >&2
   fi
   ```
2. Avoid using `exit` (which terminates the calling developer's shell session); use `return 1` instead.
3. Suppress verbose console output in non-interactive sessions:
   ```bash
   if [[ "${ANDROID_ENV_VERBOSE:-0}" == "1" || "$-" == *i* ]]; then
     # Print status summary
   fi
   ```
   This prevents breaking automated remote tools (`scp`, `sftp`, `rsync`, Git over SSH).

### 2.6 Subprocess Execution
- Replace the script process with the target executable when launching long-running daemons:
  ```bash
  exec emulator -avd "${AVD_NAME}" -port "${PORT}" ...
  ```

---

## 3. Kotlin Multiplatform & Compose Guidelines

As application modules are introduced in subsequent phases, code must adhere to the following conventions:

### 3.1 Package Hierarchy
- Root shared package: `dev.doggo.shared.*`
  - Contracts & models: `dev.doggo.shared.contract.*`
  - Local database & persistence: `dev.doggo.shared.data.*`
  - UI state & controllers: `dev.doggo.shared.state.*`
  - Compose UI & theme: `dev.doggo.shared.ui.*`
- Android application: `dev.doggo.android.*`
- Desktop application: `dev.doggo.desktop.*`

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
- Shared common code (`commonMain`) must never import Android platform APIs (`android.*`) or platform-specific display frameworks. Platform adaptations must be isolated in `androidMain` and `jvmMain`.

### 3.3 Naming Conventions
- Types, classes, interfaces, and composable functions: PascalCase.
- Member functions, local functions, and variables: camelCase.
- Constants and enum entries: UPPER_SNAKE_CASE or PascalCase for type-safe sealed objects.
- Source file names: `kebab-case.kt` for Kotlin files, `kebab-case.sh` for shell scripts, `kebab-case.md` for documentation.

### 3.4 Strict Sync & Digest Invariants
- Common contract interfaces must not leak concrete database (Room) or HTTP (Ktor) dependencies into domain signatures.
- Canonical digest calculation must use explicit length-framed big-endian fields matching the backend Rust implementation.
- Credentials (`CredentialBundle`, session tokens, passwords) must never be serialized, printed in `toString()`, or included in exception messages.
---

## 4. Testing & Quality Assurance Standards

### 4.1 Automated Shell Testing
- Test suites must be executable via standard Bash (`bash tests/test-*.sh`).
- Automated tests must provide explicit assertion tracking (`assert_test "<description>" "<command>"`).
- Test error handling rigorously: assert that invalid arguments, missing binaries, and unauthorized inputs fail cleanly with expected error codes and standard error messages.
- Tests must be idempotent and non-destructive: do not modify system device files, kill unrelated developer processes, or leave orphan background services running.

### 4.2 Quality Checks
- Syntax checking: All shell scripts must validate cleanly under `bash -n <script>`.
- Linting: Comply with `shellcheck` recommendations where available.

## 5. Security & Isolation Invariants

1. **Zero Secret Leakage**: No API keys, session tokens, passwords, keystores, or private certificates may be committed to Git.
2. **Ignored Configuration Files**: Local user configurations (`local.properties`, `*.keystore`) must never be committed to source control and must be explicitly listed in `.gitignore`.
3. **No Unauthenticated Ports**: Never bind development services, Xpra, or ADB to `0.0.0.0` or public network interfaces.
4. **License Integrity**: Never bypass interactive SDK license reviews with automated `yes` piping (`sdkmanager --licenses` requires explicit manual review).
5. **System Device Safety**: Never modify system device permissions (such as `/dev/kvm`) with `chmod` or `chown`.
