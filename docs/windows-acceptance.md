# Windows Desktop & Installed MSI Acceptance Specification

**Document Version**: 1.3.0
**Phase**: Sticky Task Surfaces Phase 07 documentation and automated verification evidenced; native Android/Windows acceptance conditionally held at Gate G2; Linux acceptance pending Phase 08
**Status**: Parent reports a standalone Windows runtime smoke passed against disposable loopback services. Interactive native UI acceptance remains unpassed; connected-Android acceptance was not run. Windows sticky-surface, installed-MSI, and other native acceptance remain distinct holds; release remains blocked and production migration unauthorized.
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
   - `SingleInstanceGuard` acquires an OS file-channel lock on `tabbyrush.lock` BEFORE initializing database or vault.
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

The following matrix is a historical evidence snapshot transcribed from `docs/evidence/windows-test-summary.md`; its old counts and statuses are not current acceptance results. The 149-unmatched-checksum hold shown below is a historical finding. Parent-reported validation of 50 official dependency checksum entries is separate evidence; the counts are not directly comparable, so do not infer current status from this old matrix. See the [current verification report](../plans/261020-1717-standard-sync-schema-version/reports/verification-final.md) for the parent-reported current evidence.

| Gate | Requirement | Suggested Verification / Reported Result | Status |
|---|---|---|---|
| **Compilation** | Kotlin JVM 2.1.10, Compose 1.7.3, Room KMP | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:compileKotlin` | PASS (Zero errors) |
| **Unit Test Suite** | Desktop task surfaces, geometry, preferences, single instance guard, Windows DPAPI vault | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:test --rerun-tasks --dependency-verification=off` | PASS (99/99 passing across 13 test classes; 330/330 total with shared JVM) |
| **ACL Enforcement** | Stored vault file contains an ALLOW ACL entry for the current owner on ACL-capable filesystems | `WindowsDpapiVaultTest.testAclEnforcementOnStoredVaultFile` | PASS (Live JNA DPAPI verified on Windows host) |
| **Shared Multiplatform** | Core contracts, digests, Room SQLite, outbox, temporal model | `.\gradlew.bat :shared:jvmTest --rerun-tasks --dependency-verification=off` | PASS (231/231 passing) |
| **Dependency Locking** | `gradle-windows.lockfile` generated and verified | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:dependencies` | PASS (Verified) |
| **Root-Level Strict Dependency Verification** | Windows target dependencies verified against Gradle verification metadata | Root Gradle strict dependency verification on Windows | FAILED / HELD (149 unmatched classpath checksums in `gradle/verification-metadata.xml`; external hold) |
| **Target Isolation** | Unsupported target mismatch rejection | `-PdesktopTarget=linux` on Windows | PASS (Fails immediately) |
| **App Distributable** | Self-contained bundled runtime image | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:createDistributable` | PASS (TabbyRush.exe launcher verified in `desktopApp/build/compose/binaries/main/app/TabbyRush/`) |
| **Package Smoke** | Diagnostics, JAR inspection, absence of Linux D-Bus | `powershell -File scripts\windows-package-smoke.ps1 -DistDir ...` | PASS |
| **MSI Packaging** | Native `.msi` package generation | `.\gradlew.bat -PdesktopTarget=windows :desktopApp:packageMsi` | PASS (TabbyRush-1.0.0.msi, 100.68 MB, SHA-256 verified) |
| **Installed MSI Test** | System installation via `msiexec.exe` | `msiexec /i TabbyRush-1.0.0.msi` | PENDING (Package structure verified; clean-host system installation remains a release hold) |
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


### 2.3 Phase 07 — Sticky Task Surfaces & Desktop Companion Window Status

| Gate | Requirement | Verification Method | Status |
|---|---|---|---|
| **Always-On-Top Companion Window** | Pinned Compose window (`alwaysOnTop = true`), stable `createdAt DESC, id DESC` task order | Desktop and geometry unit suites | Automated tests recorded; native stacking pending Gate G2 |
| **Independent Quick-Add** | Inline title creation validates through `TitlePolicy` and does not mutate the main editor | `DesktopTaskSurfacesTest` | Automated test recorded; native interaction pending Gate G2 |
| **Close-to-Hide & Tray Integration** | Close hides windows without terminating the process; sync continues; tray provides Open/Hide/Quit | Lifecycle tests and source | Automated coverage recorded; native tray/window flow pending Gate G2 |
| **No-Tray Fallback Protection** | Unsupported/unavailable tray must retain a visible recovery path | Lifecycle tests and source | Automated coverage recorded; native no-tray/init-failure exercise pending Gate G2 |
| **Geometry Clamping & Multi-Monitor** | Restore geometry within work areas, including insets, DPI changes, and negative origins | `StickyWindowGeometryTest` (15 tests in reported suite) | Unit coverage recorded; real monitor/DPI exercise pending Gate G2 |
| **Unified Suspend Teardown & Locking** | Quit drains work, closes Room, and releases `tabbyrush.lock` | `DesktopLifecycleTest`, `CompositeDesktopLeaveCancelGateTest` | Automated coverage recorded; native Quit/relaunch not observed |
| **Visual Evidence Baselines** | Window and tray design references | `docs/evidence/sticky-surfaces-windows-window.png`, `docs/evidence/sticky-surfaces-windows-tray.png` | Design baselines only; not runtime captures |
| **AC 12 Regression & Security** | Shared, desktop, Android and security regression suites | Phase 07 report | 364 Gradle and 12 Node checks recorded passing with dependency verification off; 149-checksum hold remains |

Phase 07 credit is 6h / 12h; project progress is 118h / 132h (89.4%). Native Android and Windows acceptance remains conditionally held at Gate G2. The 149 unmatched classpath checksums and separate Glance provenance hold remain open; Linux session acceptance is pending Phase 08, and production release gates G3, G6, and G7 remain open.
---

### 2.4 Phase 05 — Grouped Sticky Mode & Deadline Presentation Status (Parent/Child Tasks)

| Gate | Requirement | Verification Method | Status |
|---|---|---|---|
| **Whole Compact Groups (Model)** | Mixed groups intact under Active; all-done groups under Completed; pure projection | `DesktopTaskSurfacesTest` | Reported unit coverage PASS (`testWholeCompactGroupsMixedGroupIntactInActive`, `testFullyCompleteGroupMigratesToCompletedSection`) |
| **Grouped Row Layout (UI)** | `SpacingLarge` child indentation, 2-line title clamping, >=48dp touch bounds | Source inspection (`sticky-task-content.kt`) | Layout provisions implemented; rendered Compose layout and native visual scaling deferred to Phase 06 |
| **Absolute Own Deadlines (Model)** | Stored date/time/zone; no inheritance; overdue badge supplements date | `DesktopTaskSurfacesTest` | Reported unit coverage PASS (`testIndependentOwnDeadlinesAndClockRefresh`) |
| **Deadline & Overdue Badging (UI)** | `FlowRow` layout, error-colored overdue text on `TabbyRushCoralSurface`, full semantics | Source inspection (`sticky-task-content.kt`) | Implemented with `clearAndSetSemantics`; screen-reader audio traversal deferred to Phase 06 |
| **Automatic Parent Completion (Model)** | Read-only checkbox on parent with live children; store admission rejects direct parent toggle | `DesktopTaskSurfacesTest` | Reported unit coverage PASS (`testDirectParentCompletionRejectedNoMutationOrSync`) |
| **Automatic Parent Semantics (UI)** | Disabled checkbox, `"Subtasks: X/Y completed"` visual label, Narrator descriptions | Source inspection (`sticky-task-content.kt`) | Implemented in source; assistive technology verification deferred to Phase 06 |
| **Child Desired-State Toggle** | Leaf child completion toggle updates parent effective status and triggers sync | `DesktopTaskSurfacesTest` | Reported unit coverage PASS (`testChildDesiredStateCompletionUpdatesParentAndTriggersSync`) |
| **Independent Root Quick-Add** | Creates independent root (`parentTaskId = null`); does not mutate main editor; preserves typing | `DesktopTaskSurfacesTest` | Reported unit coverage PASS (`testQuickAddAlwaysCreatesIndependentRoot`, `testQuickAddDoesNotMutateMainEditor`, `testEditingQuickAddWhileSavePendingPreservesNewText`) |
| **Admission & Token Fencing** | Actions fenced by `SurfaceAccountToken`; stale token rejected fail-closed | `DesktopTaskSurfacesTest` | Reported unit coverage PASS (`testStaleTokenRejected`, `testAccountSwitchingWhileSubmitPendingRejectsStalePublish`) |
| **Periodic Deadline Refresh** | 30s Compose ticker dispatches `RefreshDeadlines`; controller updates projection with clock | Unit suite & source | Reported unit coverage PASS (controller clock/update unit tested; Compose 30s ticker inspected in source) |
| **Native Windows Session Acceptance** | Native window stacking, tray operation, multi-monitor/DPI restoration, and Narrator semantics | Interactive Windows x64 session | HELD at Gate G2 / DEFERRED to Phase 06 (requires physical Windows testbed) |

Reported verification: 127 `:desktopApp:test` unit tests passed (including 20 `DesktopTaskSurfacesTest` assertions), 341 `:shared:jvmTest`, and 50 `:androidApp:testDebugUnitTest` (518 Gradle unit tests total, executed with `--dependency-verification=off`; see QA report `plans/reports/qa-261020-0200-phase-05-desktop-sticky-review-fixes.md`). Native Windows desktop acceptance (always-on-top stacking, native tray menu, multi-monitor DPI scaling, Narrator screen-reader) remains held at Gate G2 and deferred to Phase 06 of plan `plans/261019-1511-parent-child-tasks-deadline-surfaces`.
---

## 3. UI Evidence Matrix

The Phase 10 visual baseline consists of seven high-fidelity 960x720 design renders displaying the Windows 11 Fluent chrome and ForumFlash light theme tokens. These serve as styling and color-contrast references; live interactive screen captures and Windows Narrator audio verification remain pending human operator runtime execution:
| State | Evidence File | Description | WCAG 2.1 AA Status |
|---|---|---|---|
| **Empty State** | `docs/evidence/windows-empty.png` | Zero-task initial state with illustration, prompt, and sync pill | 17.15:1 (AAA) |
| **Populated** | `docs/evidence/windows-populated.png` | Active task list with checkboxes, tags, and action buttons | 17.85:1 (AAA) |
| **Completed** | `docs/evidence/windows-completed.png` | Filtered completed tasks with strikethrough and timestamps | 6.31:1 (AA) |
| **Task Editor** | `docs/evidence/windows-editor.png` | Modal dialog for inline editing with character count and actions | 10.69:1 (AAA) |
| **Error Alert** | `docs/evidence/windows-error.png` | Non-fatal network error banner with TabbyRushCoralSurface styling | 4.91:1 (AA) |
| **Conflict Review** | `docs/evidence/windows-conflict.png` | Strict-v1 UnknownSuperseded side-by-side comparison modal | 15.40:1 (AAA) |
| **Expanded Layout** | `docs/evidence/windows-expanded.png` | Two-pane view with fixed navigation rail and details workspace | 12.94:1 (AAA) |

---

## 4. Known Limitations & Prerequisites

1. **WiX 3.11 Toolset Prerequisite**:
   - The native WiX packaging toolchain is required by JDK 21 `jpackage` to produce an MSI unless available through the Compose Desktop packaging setup. The Windows test summary reports successful `TabbyRush-1.0.0.msi` generation using the plugin-provided WiX 3.11 binaries.
   - MSI generation does not establish installed-runtime acceptance. Available evidence reports package-structure checks for the launcher, bundled JRE, Windows provider, and absence of Linux D-Bus jars; clean-user launch and application workflow testing remain pending.
2. **Authenticode Signing**:
   - Development builds are unsigned. Production releases should use an organizational code-signing certificate to establish publisher identity and integrity; signing does not guarantee SmartScreen warnings are suppressed.
3. **Root-level strict dependency verification (open technical gate)**:
   - Root Gradle strict dependency verification is not reported complete; it must be run in the Windows root-project context, and any verification metadata gaps must be resolved. Passing JVM tests and generating the MSI do not close this gate.
4. **ACL failure-path test coverage**:
   - The current implementation fails closed on Windows when ACL support, owner resolution, or ACL assignment fails. The available ACL test checks the stored-file owner ALLOW entry, but does not exercise failure paths or establish owner-only access; unexpected or inherited grants remain unproven.
5. **MSI source revision provenance (open evidence gap)**:
   - The evidence summary records the MSI SHA-256 but not the source revision associated with it; confirm the artifact includes the current ACL fix before release acceptance.

---

## 5. Server Admin Announcements Windows Desktop Acceptance (Phase 12)

### 5.1 Native WinRT C ABI v1 Architecture
- **Native DLL Bridge**: `:desktopApp` integrates `tabbyrush-toast.dll` implementing C ABI version 1 (`tr_toast_abi_version() == 1`).
- **Exports & Callbacks**: Exposes `tr_toast_create`, `tr_toast_setting`, `tr_toast_show`, `tr_toast_show_local`, `tr_toast_remove`, `tr_toast_clear_group`, and `tr_toast_destroy`.
- **Apartment Threading**: Dedicated native MTA thread initializes WinRT and serializes toast operations. Commands timeout after 2000ms if WinRT becomes unresponsive without leaking native references.
- **Tagging & Groups**: Remote toasts use group `tr-remote` and tag derived from the first 16 hex characters of `sha256(messageId)`.
- **Installed MSI AUMID**: Packaging script `scripts/build-windows-remote-msi.ps1` injects WiX3 `MsiShortcutProperty` setting `System.AppUserModel.ID` to `dev.tabbyrush.desktop`.

### 5.2 Full-Quit, Crash & Restart Safety
- **Normal Quit**: Invokes `tr_toast_clear_group("tr-remote")` followed by `tr_toast_destroy()`, removing pending remote notifications from Action Center and terminating the native MTA thread.
- **Click After Quit**: No cold COM activator is registered. Clicking an un-cleared toast after process crash does not launch an unintended process or leak private announcement content.
- **Silent Reopen Catch-up**: On restart, the app clears the `tr-remote` group before establishing the SSE connection. Active unexpired announcements are caught up silently via baseline hydration without re-toasting historical alerts.

### 5.3 Gate G5 Held Status & Staged Pilot Policy
- **Environment Hold**: Linux host environment cannot compile MSVC C++20 WinRT or execute MSI installations. Gate G5 (Windows Desktop Runtime) is formally HELD.
- **Staged Rollout Policy**: In accordance with the execution plan (report04/report05), restricted internal pilot enablement on Android and Linux does NOT require clearing Gate G5. Full organization-wide delivery remains blocked until Gate G5 is executed and evidenced on an installed Windows x64 testbed.
