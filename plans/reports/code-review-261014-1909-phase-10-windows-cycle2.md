# Code Review Report: Phase 10 — Native Windows DPAPI and Installed MSI (Cycle 2)

**Date:** 2026-10-14 19:09  
**Reviewer:** Phase10ReviewerCycle2 (Senior Software Engineer / Code Reviewer)  
**Target:** Phase 10 — Native Windows DPAPI and Installed MSI  
**Verdict:** **RECOMMENDED FOR OPERATOR ACCEPTANCE (Score: 8.5/10)** — All critical target isolation leaks, false-negative audit bugs, and COM inspection failures have been remediated and verified. Live negative injection tests prove audit hardening. Interactive Windows GUI installation, Narrator verification, and cross-phase Gradle verification metadata remain pending.

---

## 1. Scope & Reviewed Artifacts

| Category | File Path | Status | LOC |
|---|---|---|---|
| **Build Decoupling** | `shared/build.gradle.kts` | Modified | 97 |
| **Target Mapping** | `desktopApp/build.gradle.kts` | Inspected | 110 |
| **Lockfile** | `desktopApp/gradle-windows.lockfile` | Verified | 160 entries |
| **Audit Script** | `scripts/windows-package-smoke.ps1` | Hardened | 344 |
| **Test Summary** | `docs/evidence/windows-test-summary.md` | Updated | 81 |
| **Runbook Guide** | `docs/windows-desktop-guide.md` | Updated | 138 |
| **Acceptance Spec** | `docs/windows-acceptance.md` | Updated | 79 |
| **Plan Tracking** | `plans/261013-1540-tabbyrush-glean-hub-reuse/phase-10-windows-distribution.md` | Updated | 88 |
| **Distributable** | `desktopApp/build/compose/binaries/main/app/TabbyRush/` | Verified | 67 JARs |
| **MSI Package** | `desktopApp/build/compose/binaries/main/msi/TabbyRush-1.0.0.msi` | Verified | 100.68 MB |

---

## 2. Overall Assessment

Cycle 2 review confirms that all critical issues raised in Cycle 1 have been completely and robustly resolved:
- **CRIT-1 (Target Isolation Leak):** Decoupled `libs.dbus.java` and `libs.jna` from `shared/build.gradle.kts` `jvmMain`. Linux D-Bus is now strictly restricted to `desktopApp/build.gradle.kts` under `desktopTarget == "linux"`. Regenerated `desktopApp/gradle-windows.lockfile` contains 0 D-Bus entries. Distributable bundle now contains 67 JARs (down from 69), with zero D-Bus artifacts.
- **CRIT-2 (Smoke Audit False Negative):** Hardened `scripts/windows-package-smoke.ps1` to inspect both JAR file names (`*dbus*`, `*secret-service*`) and package entries (`*org/freedesktop/dbus/*`, `*LinuxSecretServiceVault*`). Added fail-closed behavior on corrupted archives and strict single-provider assertion (`WindowsDpapiVaultProvider`). Validated with a live negative injection test: injecting `dbus-java-core-5.1.0.jar` immediately triggers `[FAIL] Target Isolation Violated` and returns exit code 1.
- **WARN-1 (MSI COM Inspection Failure):** Replaced unsupported `WHERE Property IN (...)` MSI SQL query with `SELECT Property, Value FROM Property` and PowerShell dictionary filtering. Verified live COM extraction of `ProductName`, `ProductVersion`, `ProductCode`, `UpgradeCode`, and `Manufacturer` with assertion against expected values. Safe COM disposal added in `finally`.
- **WARN-3 (MSI Byte Count Inconsistency):** Reconciled exact byte count (`105,566,496 bytes`) and SHA-256 (`2AE585934644BBE356BB7A3CD2562BA25635A26595FE1C8E01CE7FB8C0086137`) across `windows-test-summary.md` and acceptance documentation.
- **Runbook Hardening:** Updated `docs/windows-desktop-guide.md` to document canonical Gradle tasks and remove blanket bypass recommendations, properly contextualizing temporary verification bypass as a root Phase 02 dependency.

Score calibrated to **8.5/10**: Core implementation, target isolation, native DPAPI security, packaging, and audit scripts are production-grade. Final closure requires live interactive Windows operator testing (clean install, Narrator accessibility, uninstall) and Phase 02 root verification metadata alignment.

---

## 3. Remediation Verification Matrix

| Issue ID | Description | Severity | Remediation Taken | Verification Result | Status |
|---|---|---|---|---|---|
| **CRIT-1** | Linux D-Bus JARs packaged into Windows distributable and MSI | Critical | Removed `libs.dbus.java` and `libs.jna` from `shared/build.gradle.kts` jvmMain; regenerated lockfile; rebuilt distributable & MSI | Distributable JAR count reduced from 69 to 67; zero D-Bus artifacts present in package | **RESOLVED** |
| **CRIT-2** | Smoke audit false negative on target isolation | Critical | Added JAR filename pattern inspection and class path scanning; added fail-closed archive handling and single-provider assertion | Live negative injection test with `dbus-java-core` correctly rejected with exit code 1; corrupt archive negative test rejected | **RESOLVED** |
| **WARN-1** | MSI COM metadata inspection threw SQL syntax exception | Warning | Replaced unsupported `IN` clause with `SELECT Property, Value FROM Property` and strict property assertions | COM extraction succeeded without exception; all 5 properties matched expected values | **RESOLVED** |
| **WARN-2** | WiX prerequisite confusion in guide | Warning | Clarified that Compose Multiplatform automatically provisions WiX 3.11 binaries for Gradle tasks | Documented in `windows-desktop-guide.md` and acceptance spec | **RESOLVED** |
| **WARN-3** | MSI file size discrepancy in test summary | Warning | Replaced outdated byte count with exact byte length `105,566,496` | Documented in `windows-test-summary.md` | **RESOLVED** |

---

## 4. Current Findings & Observations

### Critical Issues
*None remaining.* Target isolation leak and script audit vulnerabilities fully resolved.

### High Priority Findings
*None blocking automated verification.*

### Medium Priority Findings
1. **Root Dependency Verification Metadata (Cross-Phase):**
   - Running `./gradlew.bat -PdesktopTarget=windows :desktopApp:test` without `--dependency-verification=off` fails on root `classpath` configuration because `gradle/verification-metadata.xml` (owned by Phase 02) lacks entries for 149 buildscript artifacts (Android Gradle Plugin, KSP, etc.).
   - While `:desktopApp` dependencies are strictly locked by `desktopApp/gradle-windows.lockfile`, root project buildscript verification requires Phase 02 to update `gradle/verification-metadata.xml`.

2. **Pending Interactive Operator Acceptance:**
   - In accordance with the Phase 10 specification, live interactive installation via `msiexec /i`, clean-environment execution (without developer JDK), Windows Narrator screen reader verification, and `msiexec /x` uninstallation must be executed in a dedicated user session. Visual mockups in `docs/evidence/` serve as design/contrast references only.

### Low Priority Suggestions
1. **Dispatcher Test Java 20 Deprecation Warning:**
   - In `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/platform-vault-dispatcher-test.kt:69`, `new URL(...)` is deprecated in Java 20+. Replace with `URI.create("mock://localhost/").toURL()`.

---

## 5. Automated Validation & Evidence

```powershell
# 1. Distributable & MSI Smoke Audit
powershell -ExecutionPolicy Bypass -Command '
  $b = "desktopApp\build\compose\binaries\main";
  & .\scripts\windows-package-smoke.ps1 -DistDir "$b\app\TabbyRush" -MsiPath "$b\msi\TabbyRush-1.0.0.msi"
'
# Result:
# [PASS] Launcher executable found: TabbyRush.exe (547.5 KB)
# [PASS] Bundled Java runtime found: ...\jvm.dll
# [INFO] Found 67 packaged JAR artifact(s) in ...\app
# [PASS] WindowsDpapiVault class packaged in desktop bundle
# [PASS] WindowsDpapiVaultProvider registered in META-INF/services
# [PASS] Exactly one CredentialVaultProvider registered: dev.tabbyrush.desktop.windows.WindowsDpapiVaultProvider
# [PASS] Target Isolation: Zero Linux D-Bus / SecretService classes leaked into Windows bundle
# === Distributable Audit Passed ===
# 1. File: TabbyRush-1.0.0.msi (100.68 MB)
# 2. SHA-256 Digest: 2AE585934644BBE356BB7A3CD2562BA25635A26595FE1C8E01CE7FB8C0086137
# 3. Authenticode Status: NotSigned (Disclosed development build)
# 4. MSI Metadata Properties:
#    - UpgradeCode: {A6C507C8-88FE-4E49-8F43-A6125039281A}
#    - Manufacturer: TabbyRush Team
#    - ProductCode: {94C33C36-3F43-3D32-8E9B-6501B53FFCEE}
#    - ProductName: TabbyRush
#    - ProductVersion: 1.0.0
# [PASS] All mandatory MSI metadata properties validated successfully
# === MSI Audit Complete ===

# 2. Negative Injection Rejection Test
# Injected fake dbus-java-core-5.1.0.jar into cloned application bundle
# Result: [FAIL] Target Isolation Violated: Linux D-Bus classes detected in Windows bundle (Exit code 1)

# 3. Corrupted Archive Rejection Test
# Injected corrupted JAR archive into cloned application bundle
# Result: [FAIL] Corrupted or unreadable JAR file detected (Exit code 1)

# 4. Automated Unit Tests (:desktopApp:test, :shared:jvmTest)
# Result: 120 / 120 tests PASSED (24 desktop, 96 shared), execution time 12s
```

---

## 6. Recommended Next Steps

1. **Phase 02 Action:** Coordinate with Phase 02 owner to regenerate root `gradle/verification-metadata.xml` to include buildscript classpath artifacts, allowing `--dependency-verification=strict` across the whole repository.
2. **Interactive Acceptance:** Operator executes interactive MSI install on a test Windows workstation, validates clean startup without developer JDK, verifies Windows Narrator accessibility announcements, and confirms clean uninstallation.
3. **Parent Plan Signoff:** Once interactive evidence is logged, mark Phase 10 100% complete.

---

## 7. Metrics

- **Desktop App Tests (`:desktopApp:test`):** 24 / 24 PASSED (100%)
- **Shared Multiplatform Tests (`:shared:jvmTest`):** 96 / 96 PASSED (100%)
- **Total Test Count:** 120 / 120 PASSED (100%)
- **Packaged JAR Artifacts:** 67 (0 D-Bus, 0 SecretService)
- **MSI Installer:** `TabbyRush-1.0.0.msi` (105,566,496 bytes, SHA-256 `2AE585934644BBE356BB7A3CD2562BA25635A26595FE1C8E01CE7FB8C0086137`)
- **Review Score:** **8.5 / 10**

---

## 8. Unresolved Questions

1. When will Phase 02 owner update `gradle/verification-metadata.xml` to record hashes for the root buildscript classpath artifacts?
2. In which operator session will the physical/VM interactive `msiexec /i` and Windows Narrator verification be executed?
