# Windows Desktop & Installed MSI Acceptance Specification

**Document Version**: 1.1.0
**Phase**: 10 (Historical) — Native Windows DPAPI & Installed MSI; Phase 09 (Current) — Process-scoped desktop scheduler and Windows reminder adapter
**Status**: Desktop Multiplatform Scheduler & Windows Candidate Adapter Delivered; Native WinRT/COM Toast Bridge Unimplemented (Stub); Gate G2 Live Installed MSI & Toast Verification Blocked on Windows Host Runner
**Related Documents**: [Plan](../plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-09-desktop-scheduler-and-native-notifications.md) · [System Architecture](./system-architecture.md) · [Windows Desktop Guide](./windows-desktop-guide.md) · [Local Storage & Sync](./local-storage-and-sync.md)

---

## 1. Scope & Acceptance Criteria

Phase 10 implementation scope includes the desktop application module (`:desktopApp`) configured for Windows x64 with Compose Multiplatform 1.7.3, user-scoped credential protection via Windows DPAPI (`CryptProtectData`/`CryptUnprotectData`), foreground sync coordination, and single-instance locking. Fail-closed account leave is an acceptance requirement; live Windows lifecycle acceptance remains pending.

### Acceptance Invariants
1. **Target Isolation**:
   - `desktopTarget` defaults to detected host OS (`windows` on Windows).
   - Mismatched or unsupported target requests fail immediately before dependency resolution.
   - Compiled classes and packaged jars contain `WindowsDpapiVaultProvider` and zero references to Linux D-Bus or Secret Service classes.
2. **Deterministic Locking**:
   - `desktopApp/gradle-windows.lockfile` locks the target dependency configurations.
   - Builds reproduce cleanly without `--write-locks`.
3. **User-Scoped Credential Custody**:
   - `CredentialVaultProvider` SPI discovered via Java `ServiceLoader`.
   - Dispatcher enforces exactly one matching provider; fails closed with explicit error if zero or multiple providers are present.
   - DPAPI encryption uses user scope (`CRYPTPROTECT_LOCAL_MACHINE` is prohibited).
   - Plaintext memory is zeroed out immediately after cryptographic operations.
   - Never falls back to plaintext token files.
   - On Windows, missing ACL support, owner resolution, or ACL assignment failure causes vault storage to fail closed.
4. **Single Instance Safety**:
   - `SingleInstanceGuard` acquires an OS file-channel lock on `doggo.lock` BEFORE initializing database or vault.
   - Second launch detects lock and exits cleanly without racing sync or touching database.
   - Lock file is retained on exit to prevent race conditions.
5. **Foreground Sync & Quiescence**:
   - `DesktopSyncScheduler` coordinates periodic (15-min) and immediate sync rounds within window scope.
   - Implements `LeaveSchedulerCancelGate` to cancel and await active sync jobs before account leave or wipe.
6. **Immutable Mode & Launchers**:
   - `MainKt.main` enforces `BuildMode.RELEASE` (HTTPS only, strict endpoint validation).
   - `DevelopmentLauncher.main` enables `BuildMode.DEBUG` (HTTP 127.0.0.1:3000 permitted) via custom `runDebug` task.

---

## 2. Verification Evidence Matrix

Status cells are transcribed from `docs/evidence/windows-test-summary.md`; commands in the matrix are suggested verification methods, not recorded invocations. The report records 121/121 combined desktop and shared JVM tests passing but does not record exact invocations or dependency-verification mode; no clean-user MSI installation is recorded.

| Gate | Requirement | Suggested Verification / Reported Result | Status |
|---|---|---|---|
| **Compilation** | Kotlin JVM 2.1.10, Compose 1.7.3, Room KMP | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:compileKotlin` | PASS (Zero errors) |
| **Unit Test Suite** | Dispatcher, Lifecycle, Single Instance Guard, Windows DPAPI Vault | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:test` | PASS (25/25 passing; 121/121 total with shared) |
| **ACL Enforcement** | Stored vault file contains an ALLOW ACL entry for the current owner on ACL-capable filesystems | `WindowsDpapiVaultTest.testAclEnforcementOnStoredVaultFile` | PASS (reported; see Windows test summary) |
| **Shared Multiplatform** | Core contracts, digests, Room SQLite | `.\gradlew.bat :shared:jvmTest` | PASS (96/96 passing) |
| **Dependency Locking** | `gradle-windows.lockfile` generated and verified | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:dependencies` | PASS (reported in Windows test summary) |
| **Root-Level Strict Dependency Verification** | Windows target dependencies verified against Gradle verification metadata | Root Gradle strict dependency verification on Windows | PENDING (not completed; run on Windows and resolve any metadata gaps) |
| **Target Isolation** | Unsupported target mismatch rejection | `-PdesktopTarget=linux` on Windows | PASS (Fails immediately) |
| **App Distributable** | Self-contained bundled runtime image | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:createDistributable` | PASS (Doggo.exe launcher verified) |
| **Package Smoke** | Diagnostics, JAR inspection, absence of Linux D-Bus | `powershell -File scripts\windows-package-smoke.ps1 -DistDir ...` | PASS |
| **MSI Packaging** | Native `.msi` package generation | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:packageMsi` | PASS (Doggo-1.0.0.msi, 100.68 MB, SHA-256 verified) |
| **Installed MSI Test** | System installation via `msiexec.exe` | `msiexec /i Doggo-1.0.0.msi` | PENDING (package structure/metadata verified; interactive installation and installed-runtime acceptance not reported) |

### 2.1 Phase 02 — Native Toast & AUMID Proof Status

| Gate | Requirement | Verification Method | Status |
|---|---|---|---|
| **Toast Adapter Candidate** | XML escaping, toast structure, tag/group history clearance, and `WindowsToastBridge` interface | `WindowsNotificationAdapterTest` (tested with fake bridge) | SOURCE INSPECTED / UNEXECUTED (XML escaping, structure, and safe unavailability verified; native toast delivery and Action Center history clearance unexecuted pending Windows runner in Phase 09) |
| **Live Windows AUMID & Toast Execution** | Installed MSI shortcut AUMID registration and Action Center toast observation | Windows test environment | BLOCKED (Workstation is Linux x86_64; Windows runtime and MSI tools absent; adapter remains Candidate / Unproven until installed Windows verification; G2 gate prerequisite remaining) |
---
### 2.2 Phase 09 — Process-Scoped Desktop Scheduler & Windows Toast Candidate Status

| Gate | Requirement | Suggested Verification / Reported Result | Status |
|---|---|---|---|
| **Windows Reminder Adapter Candidate** | XML escaping, toast formatting, group/tag generation, error collection, and SPI provider discovery | `WindowsReminderAdapterTest` (6 unit tests in `windowsTest` with fake bridge) | AUTHORED CANDIDATE (Authored unit tests in `windowsTest` with fake bridge; not compiled or executed on Linux host runner) |
| **Desktop Reminder Scheduler Integration** | Process-scoped wake loop, replace-based deadline wake, cancel quiescence | JVM Desktop tests (`DesktopReminderSchedulerTest`) | PASS (5/5 tests passing in shared desktop suite) |
| **Multi-Account Toast Isolation** | Account-scoped clearance by group (`d_<accountKey>`) and tag (`t_<taskId>_<index>`), without global toast wipe | `WindowsReminderAdapterTest.testCancelAllForAccountClearsToasts` | AUTHORED CANDIDATE (Authored test against fake bridge in `windowsTest`; unexecuted on Linux host) |
| **Error Propagation on Cancellation Failure** | Bridge cancellation failure collects errors and throws `IllegalStateException` | `WindowsReminderAdapterTest.testCancelAllForAccountPropagatesErrorsOnBridgeFailure` | AUTHORED CANDIDATE (Authored test against fake bridge in `windowsTest`; unexecuted on Linux host) |
| **ServiceLoader SPI Provider** | `WindowsReminderAdapterProvider` resolves under target `windows` | ServiceLoader configuration in `META-INF/services` | DELIVERED (Source declared) |
| **Native WinRT/COM Toast Bridge** | Native toast posting via `Windows.UI.Notifications.ToastNotificationManager` | JNA / COM bridge execution | UNIMPLEMENTED STUB (`JnaWindowsToastBridge` returns `WindowsNotificationStatus.Unavailable`; deferred to Phase 11) |
| **Live Windows AUMID & Toast Delivery** | Installed MSI shortcut AUMID registration and Action Center toast observation | Windows test environment | BLOCKED (Requires physical Windows testbed; Gate G2 prerequisite remaining) |

#### Disclosed Limitations for Phase 09 Windows Reminders
1. **Native Toast Bridge Unimplemented**: `JnaWindowsToastBridge` is an unintegrated candidate stub that returns `WindowsNotificationStatus.Unavailable` for all operations. Windows toast display is not functional on any platform yet. Wrapper classes and fake-bridge unit tests verify API shape and account isolation only. Real COM/WinRT native delivery is deferred to Phase 11 on a physical Windows testbed.
2. **Process-Memory Tracking Limitation**: Active toast references are tracked in an in-memory map (`ConcurrentHashMap<String, MutableSet<String>>`). If the application exits or crashes while toasts are active in Action Center, subsequent startup wipe recovery cannot discover or remove those orphaned toasts through this adapter.
3. **Truncated Identity Scoping**: Windows group identifier `d_${accountKey.value.take(12)}` truncates the 64-hex account key to 12 characters, and tag `t_${taskId.take(16)}_$occurrenceIndex` truncates the task UUID to 16 characters while omitting generation and schedule token. These identifiers are not globally collision-free.
4. **Individual Removal Status**: `removeOwnedNotification()` calls `windowsAdapter.cancelNotification()` and immediately removes the local tracking entry without evaluating whether the bridge succeeded.

---

## 3. UI Evidence Matrix

The Phase 10 visual baseline consists of seven high-fidelity 960x720 design renders displaying the Windows 11 Fluent chrome and ForumFlash light theme tokens. These serve as styling and color-contrast references; live interactive screen captures and Windows Narrator audio verification remain pending human operator runtime execution:
| State | Evidence File | Description | WCAG 2.1 AA Status |
|---|---|---|---|
| **Empty State** | `docs/evidence/windows-empty.png` | Zero-task initial state with illustration, prompt, and sync pill | 17.15:1 (AAA) |
| **Populated** | `docs/evidence/windows-populated.png` | Active task list with checkboxes, tags, and action buttons | 17.85:1 (AAA) |
| **Completed** | `docs/evidence/windows-completed.png` | Filtered completed tasks with strikethrough and timestamps | 6.31:1 (AA) |
| **Task Editor** | `docs/evidence/windows-editor.png` | Modal dialog for inline editing with character count and actions | 10.69:1 (AAA) |
| **Error Alert** | `docs/evidence/windows-error.png` | Non-fatal network error banner with DoggoCoralSurface styling | 4.91:1 (AA) |
| **Conflict Review** | `docs/evidence/windows-conflict.png` | Strict-v1 UnknownSuperseded side-by-side comparison modal | 15.40:1 (AAA) |
| **Expanded Layout** | `docs/evidence/windows-expanded.png` | Two-pane view with fixed navigation rail and details workspace | 12.94:1 (AAA) |

---

## 4. Known Limitations & Prerequisites

1. **WiX 3.11 Toolset Prerequisite**:
   - The native WiX packaging toolchain is required by JDK 21 `jpackage` to produce an MSI unless available through the Compose Desktop packaging setup. The Windows test summary reports successful `Doggo-1.0.0.msi` generation using the plugin-provided WiX 3.11 binaries.
   - MSI generation does not establish installed-runtime acceptance. Available evidence reports package-structure checks for the launcher, bundled JRE, Windows provider, and absence of Linux D-Bus jars; clean-user launch and application workflow testing remain pending.
2. **Authenticode Signing**:
   - Development builds are unsigned. Production releases should use an organizational code-signing certificate to establish publisher identity and integrity; signing does not guarantee SmartScreen warnings are suppressed.
3. **Root-level strict dependency verification (open technical gate)**:
   - Root Gradle strict dependency verification is not reported complete; it must be run in the Windows root-project context, and any verification metadata gaps must be resolved. Passing JVM tests and generating the MSI do not close this gate.
4. **ACL failure-path test coverage**:
   - The current implementation fails closed on Windows when ACL support, owner resolution, or ACL assignment fails. The available ACL test checks the stored-file owner ALLOW entry, but does not exercise failure paths or establish owner-only access; unexpected or inherited grants remain unproven.
5. **MSI source revision provenance (open evidence gap)**:
   - The evidence summary records the MSI SHA-256 but not the source revision associated with it; confirm the artifact includes the current ACL fix before release acceptance.
