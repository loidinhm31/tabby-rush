# Code Review Report: Phase 10 — Native Windows DPAPI and Installed MSI

**Date:** 2026-10-14 19:00  
**Reviewer:** Phase10Reviewer (Senior Software Engineer / Code Reviewer)  
**Target:** Phase 10 — Native Windows DPAPI and Installed MSI  
**Verdict:** **DO NOT MARK ACCEPTED YET (Score: 7.5/10)** — High quality cryptographic and desktop implementation with 120/120 passing tests, but blocked by a critical target isolation leak (Linux D-Bus JARs packaged into Windows bundle), a false-pass regex vulnerability in the package audit script, and pending live runtime/narrator installation verification.

---

## 1. Scope & Reviewed Artifacts

| Category | File Path | Status | LOC |
|---|---|---|---|
| **Core Vault** | `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-dpapi-vault.kt` | Created | 246 |
| **Service SPI** | `desktopApp/src/windowsMain/resources/META-INF/services/dev.doggo.desktop.CredentialVaultProvider` | Created | 1 |
| **Unit Tests** | `desktopApp/src/windowsTest/kotlin/dev/doggo/desktop/windows/windows-dpapi-vault-test.kt` | Created | 235 |
| **Integration Fix** | `desktopApp/src/test/kotlin/dev/doggo/desktop/platform-vault-dispatcher-test.kt` | Modified | 88 |
| **Lockfile** | `desktopApp/gradle-windows.lockfile` | Created | 160 entries |
| **Smoke Harness** | `scripts/windows-package-smoke.ps1` | Created | 279 |
| **Documentation** | `docs/windows-desktop-guide.md` | Created | 138 |
| **Acceptance Spec**| `docs/windows-acceptance.md` | Created | 79 |
| **Test Summary** | `docs/evidence/windows-test-summary.md` | Created | 82 |
| **UI Mockups** | `docs/evidence/windows-*.png` (7 files: empty, populated, completed, editor, error, conflict, expanded) | Created | 960x720 PNGs |
| **Plan Tracking** | `plans/261013-1540-doggo-glean-hub-reuse/phase-10-windows-distribution.md` | Updated | 89 |

---

## 2. Overall Assessment

The core Windows DPAPI credential vault demonstrates strong adherence to Windows security best practices:
- **DPAPI Key Custody:** Enforces user scope via JNA `CryptProtectData` with `CRYPTPROTECT_UI_FORBIDDEN`; strictly forbids `CRYPTPROTECT_LOCAL_MACHINE`.
- **NTFS Owner-Only ACL:** Explicitly restricts file permissions using `AclFileAttributeView`, stripping all 9 inherited entries to a single owner entry.
- **Fail-Closed Semantics:** Corrupted files, tampering, or logon credential mismatch transition status to `VaultStatus.CORRUPT` and preserve local SQLite databases.
- **Memory Hygiene:** Plaintext byte buffers are explicitly wiped via `ByteArray.fill(0)` in finally blocks.
- **Atomic Replacement:** Vault writes use temporary files and `StandardCopyOption.ATOMIC_MOVE` with `REPLACE_EXISTING`.
- **Automated Verification:** 120/120 tests pass cleanly (24 desktop, 96 shared), including live native DPAPI roundtrip on Windows 11. Standalone `Doggo.exe` distributable (547.5 KB) and native MSI `Doggo-1.0.0.msi` (105,894,224 bytes, 100.99 MB) build cleanly.

However, cross-phase target isolation is violated: two Linux D-Bus libraries leaked into the Windows distribution, and the smoke script failed to catch them due to an improper regex pattern.

---

## 3. Critical Issues

### CRIT-1: Target Isolation Violation — Linux D-Bus JARs Packaged in Windows Distribution
- **Impact:** `dbus-java-core-5.1.0-*.jar` and `dbus-java-transport-native-unixsocket-5.1.0-*.jar` are present in `desktopApp/build/compose/binaries/main/app/Doggo/app/` and included in the MSI installer.
- **Root Cause:** In Phase 02 (`shared/build.gradle.kts:57`), `implementation(libs.dbus.java)` was added unconditionally to `jvmMain.dependencies`. Because `:desktopApp` depends on `:shared`, Gradle pulls D-Bus transitively into `:desktopApp` runtimeClasspath.
- **Remediation:**
  1. Phase 02 owner removes `implementation(libs.dbus.java)` from `shared/build.gradle.kts` `jvmMain` (D-Bus is unused in `:shared`).
  2. Keep D-Bus dependency strictly inside `desktopApp/build.gradle.kts` under `if (desktopTarget == "linux")`.
  3. Re-lock `desktopApp/gradle-windows.lockfile` and rebuild `:desktopApp:createDistributable` and `:desktopApp:packageMsi`.

### CRIT-2: Package Smoke Audit False Negative on Target Isolation
- **Impact:** `scripts/windows-package-smoke.ps1` reported `[PASS] Target Isolation: Zero Linux D-Bus / SecretService classes leaked into Windows bundle` despite the presence of the two D-Bus JARs.
- **Root Cause:** Line 159 checked `$entry.FullName -like "*dbus/java*"`. D-Bus Java classes reside in `org/freedesktop/dbus/`, so the entry names never contain `dbus/java`. The script also failed to check JAR file names.
- **Remediation:** Update `scripts/windows-package-smoke.ps1` to inspect both JAR file names and class entry prefixes:
  ```powershell
  if ($jar.Name -like "*dbus-java*" -or $entry.FullName -like "*org/freedesktop/dbus*" -or $entry.FullName -like "*LinuxSecretServiceVault*") {
      $foundLinuxDbus = $true
  }
  ```
  Ensure the script fails with exit code 1 if any forbidden artifact is detected. Add a negative test case to verify rejection.

---

## 4. Warnings

### WARN-1: MSI Metadata COM Inspection Failed in Package Smoke Script
- **Impact:** In `scripts/windows-package-smoke.ps1`, MSI property querying threw COM exception `OpenView,Sql` and logged `[WARN] Could not inspect MSI properties via COM: Exception calling "InvokeMember" with "5" argument(s): "OpenView,Sql"`. The script continued and returned exit code 0.
- **Root Cause:** Reflection call used `WHERE Property IN ('...')`. MSI SQL engine does not support SQL `IN` syntax.
- **Remediation:** Query `SELECT Property, Value FROM Property` directly without reflection via PowerShell COM:
  ```powershell
  $installer = New-Object -ComObject WindowsInstaller.Installer
  $db = $installer.OpenDatabase($Path, 0)
  $view = $db.OpenView("SELECT Property, Value FROM Property")
  $view.Execute()
  $rec = $view.Fetch()
  while ($null -ne $rec) {
      $p = $rec.StringData(1)
      $v = $rec.StringData(2)
      if ($p -in @('ProductName', 'ProductVersion', 'ProductCode', 'UpgradeCode', 'Manufacturer')) {
          Write-Host "   - ${p}: ${v}"
      }
      $rec = $view.Fetch()
  }
  ```
  And assert that all required properties exist and match expectations, failing if missing.

### WARN-2: WiX Prerequisite Documentation Discrepancy & QA Confusion
- **Impact:** Smoke harness diagnostic reported WiX not in `PATH` as a hard blocker, leading Phase10Tester to report MSI packaging as blocked.
- **Clarification:** The JetBrains Compose Multiplatform plugin (`org.jetbrains.compose`) automatically downloads WiX 3.11 binaries (`wix311-binaries.zip`) to the Gradle cache during `:desktopApp:packageMsi` if WiX is not in system `PATH`.
- **Remediation:** Update `scripts/windows-package-smoke.ps1` and `docs/windows-desktop-guide.md` to clarify that Compose Desktop auto-provisions WiX 3.11 for Gradle tasks, while system PATH is only mandatory for standalone `jpackage` executions.

### WARN-3: MSI File Size Documentation Inconsistency
- **Finding:** `docs/evidence/windows-test-summary.md` and `docs/windows-acceptance.md` record the MSI size as `105,897,984 bytes` (100.99 MB). The actual filesystem size is `105,894,224 bytes` (SHA-256 `84E58505C0B1E8D51FE0B17A661907676EE473F63DA7895FEE55407F7D3BE8A6` matches).
- **Remediation:** Correct the exact byte count in `docs/evidence/windows-test-summary.md` to `105,894,224`.

---

## 5. Suggestions

### SUGG-1: Stricter Account Key Validation on Load
- In `WindowsDpapiVault.kt:123`, the check `if (payload.accountKey.isNotBlank() && payload.accountKey != accountKey.value)` permits a blank account key in stored JSON. Since `AccountKey` requires a 64-character hex string, change to `if (payload.accountKey != accountKey.value)` to ensure blank or missing keys fail closed immediately.

### SUGG-2: Add Windows Host Detection Assertion in Dispatcher Test
- In `desktopApp/src/test/kotlin/dev/doggo/desktop/platform-vault-dispatcher-test.kt`, `testDetectHostTargetOnLinux` was made conditional for Linux. Add a symmetrical test `testDetectHostTargetOnWindows` asserting that `PlatformVaultDispatcher.detectHostTarget() == "windows"` when running on Windows.

### SUGG-3: Replace Deprecated URL Constructor in Test Classloader Mock
- In `platform-vault-dispatcher-test.kt:69`, `new URL(...)` constructor is deprecated in Java 20+. Replace with `URI.create("mock://localhost/").toURL()` or use standard URI creation.

### SUGG-4: Clarify UI Mockup Evidence vs Interactive Screenshots
- Clearly document in `docs/windows-acceptance.md` that `docs/evidence/windows-*.png` are design mockups/baselines for WCAG contrast and styling compliance, and note that runtime screenshot capture with live Narrator execution remains pending interactive operator verification.

---

## 6. Positive Observations

- **Rigorous DPAPI Scope:** Proper use of `WinCrypt.CRYPTPROTECT_UI_FORBIDDEN` and deliberate absence of `CRYPTPROTECT_LOCAL_MACHINE`.
- **NTFS ACL Hardening:** Tested and confirmed to strip inherited ACL entries down to a single owner entry on NTFS.
- **Fail-Closed Architecture:** Unprotect errors or corrupted JSON transition to `VaultStatus.CORRUPT` and preserve local SQLite database files, preventing data loss.
- **Memory Zeroing:** Explicit `ByteArray.fill(0)` on plaintext byte arrays in finally blocks.
- **Atomic File Writes:** Safe write-to-temp and atomic move prevents file corruption during sudden shutdown.
- **Complete Test Suite:** 120/120 tests green across desktop and shared suites.

---

## 7. Metrics & Validation Results

| Metric | Result |
|---|---|
| **Desktop Tests (`:desktopApp:test`)** | 24 / 24 PASSED (100%) |
| **Shared Tests (`:shared:jvmTest`)** | 96 / 96 PASSED (100%) |
| **Total Tests** | 120 / 120 PASSED (100%) |
| **Live Native DPAPI Roundtrip** | Verified PASSED on Windows 11 Build 26200 |
| **Distributable Image** | `Doggo.exe` launcher (547.5 KB) + Bundled JRE verified |
| **MSI Installer Package** | `Doggo-1.0.0.msi` (105,894,224 bytes, 100.99 MB) |
| **MSI SHA-256 Digest** | `84E58505C0B1E8D51FE0B17A661907676EE473F63DA7895FEE55407F7D3BE8A6` |
| **Authenticode Status** | NotSigned (Disclosed developmental build) |
| **UI Design Baselines** | 7 PNGs (960x720) in `docs/evidence/` verified |
| **Review Score** | **7.5 / 10** |

---

## 8. Recommended Actions (Prioritized)

1. **[CRITICAL] Remediate D-Bus Leak:**
   - Coordinate with Phase 02 owner to remove `libs.dbus.java` from `shared/build.gradle.kts` `jvmMain`.
   - Ensure D-Bus is only included in `desktopApp/build.gradle.kts` under `desktopTarget == "linux"`.
2. **[CRITICAL] Harden Smoke Audit Script:**
   - Update `scripts/windows-package-smoke.ps1` to detect `dbus-java-*` JARs and `org/freedesktop/dbus/` package.
   - Replace reflection-based MSI COM SQL query with direct `SELECT Property, Value FROM Property` and validate property values.
3. **[HIGH] Rebuild and Audit Artifacts:**
   - Regenerate `desktopApp/gradle-windows.lockfile`.
   - Re-execute `:desktopApp:packageMsi` and run the hardened smoke script to confirm zero D-Bus artifacts.
4. **[HIGH] Update Documentation:**
   - Fix MSI byte count to `105,894,224` in `windows-test-summary.md`.
   - Clarify Compose WiX auto-download behavior in `windows-desktop-guide.md`.
5. **[MEDIUM] Execute Interactive Installation Acceptance:**
   - Run `msiexec /i` and `msiexec /x` on a clean Windows machine and record Narrator/startup evidence.

---

## 9. Unresolved Questions

1. When will Phase 02 owner execute the cross-phase repair to remove `libs.dbus.java` from `shared/build.gradle.kts`?
2. Will interactive operator acceptance testing (`msiexec /i`, Windows Narrator verification, and uninstallation) be scheduled before final parent plan closure?