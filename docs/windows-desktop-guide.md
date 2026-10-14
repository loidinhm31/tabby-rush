# Doggo Windows Desktop Guide & Runbook

**Document Version**: 1.1.0
**Target Operating System**: Windows (Windows 10 Build 19041+, Windows 11 Build 22000+)
**Architecture**: x86_64 (AMD64)
**Distribution Format**: MSI (`.msi`) and self-contained runtime directory (`createDistributable`)

---

## 1. Overview

The Doggo Windows desktop application is built with Compose Multiplatform 1.7.3 and targets the Java 21 LTS runtime. It brings the full offline-first personal task management experience and strict-v1 Glean Hub synchronization to Windows workstations.

### Key Architectural Invariants
1. **OS-Protected Credential Custody**: Tokens are stored through Windows DPAPI using JNA `CryptProtectData` and `CryptUnprotectData` in current-user scope; `CRYPTPROTECT_LOCAL_MACHINE` and plaintext fallback are prohibited.
2. **Atomic Replacement & ACL Attempt**: `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-dpapi-vault.kt` writes encrypted token payloads to a temporary file and atomically moves them into place. `applyRestrictiveUserAcl()` attempts an owner ACL through `AclFileAttributeView`, but returns if the view/owner is unavailable and suppresses exceptions; effective owner-only ACL enforcement is not guaranteed.
3. **Single Instance Guard**: A file-channel lock on `%LOCALAPPDATA%\doggo\accounts\doggo.lock` prevents multiple running processes from racing token refresh or corrupting local SQLite databases.
4. **Fail-Closed Session Lifecycle**: DPAPI unprotect/decode failures and nonblank account-key mismatches transition to a recoverable sign-in state without destroying local offline databases. Missing or zero-length files are treated as no saved credentials; a blank serialized account key is not rejected by the current implementation.
5. **Foreground Sync Scheduling**: Sync intervals run in the application scope and quiesce prior to any account leave or database teardown.
6. **No Cross-Platform Leakage**: Packaging and dependency resolution strictly isolate Windows artifacts. Zero Linux D-Bus jars or Secret Service classes are included in the Windows distribution.

---

## 2. Prerequisites & Host Setup

### System Requirements
- **Operating System**: 64-bit Windows 10 or Windows 11.
- **Java Development Kit**: JDK 21 LTS (Oracle JDK 21, Eclipse Temurin 21, or Microsoft OpenJDK 21).
- **WiX Toolset**: WiX Toolset 3.11+ (requires `candle.exe` and `light.exe` in `PATH` for native `.msi` generation via `jpackage`).

### Environment Verification
Run the diagnostics script from PowerShell:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows-package-smoke.ps1 -Diagnose
```

Sample expected output:
```
=== Doggo Windows Desktop Environment Diagnostics ===
1. Host OS: Windows 10.0.26200.0 (AMD64)
2. Java Runtime: PRESENT (java version "21.0.9" 2025-10-21 LTS) at ...\bin\java.exe
3. jpackage tool: PRESENT (21.0.9) at ...\bin\jpackage.exe
4. WiX Toolset (candle / light): PRESENT or NOT INSTALLED
5. PowerShell Version: 5.1+
=== Diagnostics Complete ===
```

---

## 3. Building Doggo Desktop

### Build Commands (Windows Command Prompt or PowerShell)

#### 1. Compile Kotlin Desktop Application
```cmd
.\gradlew.bat -PdesktopTarget=windows :desktopApp:compileKotlin
```

#### 2. Execute Desktop Unit Test Suite
```cmd
.\gradlew.bat -PdesktopTarget=windows :desktopApp:test
```

#### 3. Create Self-Contained Distributable Bundle
Generates a standalone application directory with a bundled JRE, required DLLs, and the `Doggo.exe` launcher:
```cmd
.\gradlew.bat -PdesktopTarget=windows :desktopApp:createDistributable
```
Output path: `desktopApp\build\compose\binaries\main\app\Doggo\`

#### 4. Package Windows MSI Installer
Packages a per-user MSI installer with Start Menu shortcut and upgrade GUID:
```cmd
.\gradlew.bat -PdesktopTarget=windows :desktopApp:packageMsi
```
*Note: JetBrains Compose Multiplatform plugin automatically provisions WiX 3.11 binaries into the Gradle cache if WiX is not in system PATH.*

*Operational Note on Root Verification Metadata:* If root buildscript classpath artifacts fail Gradle dependency verification due to unrecorded Android Gradle Plugin / KSP buildscript hashes in `gradle/verification-metadata.xml` (owned by Phase 02), `--dependency-verification=off` may be used temporarily for diagnostic runs until Phase 02 updates the shared verification metadata.

---

## 4. Dependency Locking

The Windows target-specific lockfile is `desktopApp\gradle-windows.lockfile`; ordinary builds consume it without `--write-locks`.
- **Regenerate Lockfile**:
  ```cmd
  .\gradlew.bat -PdesktopTarget=windows :desktopApp:dependencies --write-locks
  ```
---

## 5. Security & DPAPI Storage Architecture

### File Layout
Per-user account data is located under `%LOCALAPPDATA%\doggo\accounts\<accountKeyHash>\`:
```
%LOCALAPPDATA%\doggo\accounts\
├── doggo.lock                          # Single instance OS file lock
└── <accountKeyHash>\                   # SHA-256 hash of AccountKey
    ├── credentials.dpapi               # User-scoped DPAPI encrypted token payload
    ├── doggo.db                        # SQLite Room database (tasks, outbox)
    ├── doggo.db-wal                    # SQLite WAL journal
    ├── generation.dat                  # Durable generation counter
    └── wipe.marker                     # Fail-closed account wipe marker (transient)
```

### Encryption Invariants
- **Function**: `CryptProtectData` with `CRYPTPROTECT_UI_FORBIDDEN` (`0x1`).
  - **Scope**: Current Windows user DPAPI scope; `CRYPTPROTECT_LOCAL_MACHINE` is not used.
- **Format**: JSON serialized `StoredCredentialPayload(accountKey, accessToken, refreshToken)`.
- **Memory Hygiene**: Byte arrays containing plaintext tokens are explicitly zeroed out via `ByteArray.fill(0)` in finally blocks.
- **Fail-Closed Policy**: DPAPI unprotect failures or malformed payloads set `VaultStatus.CORRUPT` and throw, stopping token refresh without wiping offline tasks. Missing or zero-byte files return no stored credential.

---

## 6. Verification & Smoke Testing

Audit the distributable application bundle using the PowerShell verification harness:
```powershell
powershell -File scripts\windows-package-smoke.ps1 -DistDir desktopApp\build\compose\binaries\main\app\Doggo
```
*(Note: Under strict enterprise policies, ensure scripts are signed or execution policy allows local scripts, e.g. `Set-ExecutionPolicy RemoteSigned -Scope CurrentUser`)*

Audited checks include:
1. `Doggo.exe` launcher existence and size.
2. Bundled Java runtime existence (`runtime\bin\java.exe`).
3. Verification that `WindowsDpapiVault` and `WindowsDpapiVaultProvider` are packaged.
4. Target Isolation: Complete absence of Linux D-Bus and Secret Service classes.

Phase 10 implementation is recorded at **100% (2026-10-14)**; technical acceptance remains blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not guaranteed by the current source, and clean-user runtime/sync plus uninstall/reinstall evidence remain open. A successful bundle/MSI audit is not evidence of installed-runtime or accessibility acceptance.

---

## 7. Troubleshooting & FAQ

### 1. WiX Toolset Missing During `:desktopApp:packageMsi`
- **Symptom**: `jpackage failed with error: candle.exe or light.exe not found`.
- **Resolution**: Install WiX Toolset 3.11 from `https://wixtoolset.org/` or via Chocolatey (`choco install wixtoolset`). Ensure `C:\Program Files (x86)\WiX Toolset v3.11\bin` is in system `PATH`.

### 2. Multi-User Access
- **Behavior**: Credentials are protected with current-user DPAPI scope. Cross-user behavior should be verified using an isolated Windows user profile; static package inspection and mock-protector tests do not establish this native boundary or a specific Windows error code.
