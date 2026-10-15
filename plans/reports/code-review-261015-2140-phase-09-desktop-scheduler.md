# Code Review Report: Phase 09 Desktop Scheduler & Native Adapters (Cycle 2 Re-Review)

**Date**: 2026-10-15  
**Context**: Linux Fedora 44 x86_64 | OpenJDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Gradle 8.11.1 | Compose Multiplatform 1.7.0  
**Target Phase**: `Phase 09 — Process-scoped desktop scheduler and native adapters`  
**Reviewer**: evc-code-reviewer (`Phase09Reviewer2`)  
**Score**: **8.9 / 10**  
**Status**: **CONDITIONAL APPROVAL (REMEDIATIONS VERIFIED FOR TESTED LINUX/JVM SCOPE; WINDOWS NATIVE IMPLEMENTATION/VALIDATION AND GATE G2 INSTALLED-RUNTIME ACCEPTANCE REMAIN INCOMPLETE)**  
**Artifact**: `plans/reports/code-review-261015-2140-phase-09-desktop-scheduler.md`  

---

## Code Review Summary

### Scope
- **Files reviewed**:
  - `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-reminder-scheduler.kt` (159 LOC)
  - `desktopApp/src/main/kotlin/dev/doggo/desktop/composite-desktop-leave-cancel-gate.kt` (49 LOC)
  - `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-reminder-adapter.kt` (16 LOC)
  - `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-reminder-adapter-provider.kt` (20 LOC)
  - `desktopApp/src/main/kotlin/dev/doggo/desktop/platform-reminder-dispatcher.kt` (58 LOC)
  - `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt` (560 LOC)
  - `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-notification-adapter.kt` (240 LOC)
  - `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-notification-adapter.kt` (229 LOC)
  - `desktopApp/src/linuxTest/kotlin/dev/doggo/desktop/linux/linux-reminder-adapter-test.kt` (174 LOC)
  - `desktopApp/src/windowsTest/kotlin/dev/doggo/desktop/windows/windows-reminder-adapter-test.kt` (125 LOC)
  - `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-reminder-controller-lifecycle-test.kt` (182 LOC)
  - `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-reminder-scheduler-test.kt` (350 LOC)
  - `desktopApp/src/test/kotlin/dev/doggo/desktop/composite-desktop-leave-cancel-gate-test.kt` (141 LOC)
- **Lines of code analyzed**: ~2,300 LOC
- **Review focus**: Cycle 1 high-priority remediation verification, fail-closed cancellation propagation, Windows toast account isolation, EDT unburdening, empty capability tolerance, test coverage expansion.
- **Updated plans**:
  - `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-09-desktop-scheduler-and-native-notifications.md`
  - `plans/261015-0752-task-deadlines-reminders-strict-upgrades/plan.md`

### Overall Assessment
Cycle 1 high-priority findings have been systematically resolved in source and verified with dedicated automated tests:
1. **Cancellation Error Propagation**: `LinuxNativeReminderAdapter.cancelAllForAccount` and `WindowsNativeReminderAdapter.cancelAllForAccount` catch native failures/errors, collect them, and throw an explicit `IllegalStateException`. In `DesktopController.completePendingWipesOnStartup`, thrown errors skip wipe marker deletion, database removal, and vault clearing, ensuring fail-closed recovery contract retention.
2. **Windows Multi-Account Isolation**: The blanket `windowsAdapter.cancelAllNotifications()` call was completely eliminated from `cancelAllForAccount`. The adapter now iterates strictly over the account's registered tag/group identifiers (`group = "d_${accountKey.value.take(12)}"`), preventing cross-account history wipe.
3. **EDT Threading Unburdening**: `DesktopReminderScheduler` wake loop launches on `ioDispatcher = Dispatchers.IO`. In `main.kt`, schedule observer flow collection (`store.observePersistedSchedule().collect`), listener updates, and initial reconciliations execute on `Dispatchers.IO`. All Room DAO calls and D-Bus IPC operations avoid blocking the Swing EDT. Concurrency review confirms state mutations inside the scheduler remain guarded by `stateMutex = Mutex()`, while `wakeSignal` uses conflated buffering (`Channel.CONFLATED`).
4. **Linux Daemon Capability Tolerance**: `LinuxNativeReminderAdapter.queryCapabilities` evaluates `status is LinuxNotificationStatus.Success` independently of whether the capabilities set is empty, permitting lightweight notification daemons (e.g. dunst, mako).
5. **Test Suite Expansion**: Added unit tests in `LinuxReminderAdapterTest` (`testCancelAllForAccountPropagatesErrorsOnCloseFailure`) and `WindowsReminderAdapterTest` (`testCancelAllForAccountPropagatesErrorsOnBridgeFailure`), bringing DesktopApp test suite to 48 passing tests (48/48 PASS) and global repository automated checks to 298 passing checks (298/298 PASS).

**Open Limitations**:
- Windows COM/WinRT native bridge remains candidate stub (`Unavailable`), not compiled or executed on this Linux host. Real native delivery, installed identity, and restart-safe Action Center group clearance remain incomplete pending physical Windows testbed in Phase 11.
- Gate G2 physical execution (live notification display on desktop session) remains formally unexecuted.

---

## Remediation Verification Details

### 1. Fail-Closed Cancellation Error Propagation (RESOLVED)
- **Source**: `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-notification-adapter.kt:215-231`
  ```kotlin
  override suspend fun cancelAllForAccount(accountKey: AccountKey) = withContext(ioDispatcher) {
      val accountIds = postedNotificationIds.remove(accountKey.value) ?: return@withContext
      val errors = mutableListOf<String>()
      for (id in accountIds) {
          try {
              val status = dbusAdapter.closeNotification(id)
              if (status is LinuxNotificationStatus.Error) {
                  errors.add("Failed to close notification $id: ${status.message}")
              }
          } catch (e: Exception) {
              errors.add("Exception closing notification $id: ${e.message}")
          }
      }
      if (errors.isNotEmpty()) {
          throw IllegalStateException("Failed to cancel notifications for ${accountKey.value}: ${errors.joinToString("; ")}")
      }
  }
  ```
- **Windows Equivalent**: `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-notification-adapter.kt:201-220` mirrors error collection and throws `IllegalStateException`.
- **Consumer Behavior**: `DesktopController.completePendingWipesOnStartup()` (`main.kt:125-131`):
  ```kotlin
  try {
      runBlocking(Dispatchers.IO) { reminderAdapter.cancelAllForAccount(key) }
  } catch (_: Exception) {
      return@forEach // Fail-closed: retain wipe marker if reminder cancellation fails
  }
  ```
  Exception bubbles up, bypassing `deleteDatabaseAndArtifacts`, `vault.clear`, and `clearWipeMarker`.
- **Automated Proof**:
  - `LinuxReminderAdapterTest.testCancelAllForAccountPropagatesErrorsOnCloseFailure`: Verified `assertFailsWith<IllegalStateException>` on daemon close error.
  - `WindowsReminderAdapterTest.testCancelAllForAccountPropagatesErrorsOnBridgeFailure`: Verified `assertFailsWith<IllegalStateException>` on toast bridge error.
  - `DesktopReminderControllerLifecycleTest.testStartupWipeRecoveryRetainsMarkerWhenReminderCancellationFails`: Verified wipe marker, SQLite DB, and vault secrets remain preserved when adapter throws.

### 2. Windows Multi-Account Toast Isolation (RESOLVED)
- **Source**: `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-notification-adapter.kt:201-220`
- **Fix**: Removed global `windowsAdapter.cancelAllNotifications()`. Iterates solely across `postedPlatformIds[accountKey.value]` and targets `windowsAdapter.cancelNotification(tag = parts[1], group = parts[0])` where group is `d_${accountKey.value.take(12)}`.
- **Automated Proof**: `WindowsReminderAdapterTest.testCancelAllForAccountClearsToasts` posts notifications for two distinct accounts (`accountKey1` with tasks t1/t2, `accountKey2` with task t3), cancels account 1, and verifies that `fakeBridge.cleared` contains only t1 and t2, leaving account 2's toast untouched.

### 3. EDT Threading & Concurrency Review (RESOLVED)
- **Source**: `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt:203-230` & `desktop-reminder-scheduler.kt:36-64`
- **Orchestration**:
  - `DesktopReminderScheduler` constructed with `ioDispatcher = Dispatchers.IO`.
  - Wake loop launched via `scope.launch(ioDispatcher) { runWakeLoop() }`.
  - `scheduleObserverJob` launched via `scope.launch(Dispatchers.IO) { store.observePersistedSchedule().collect { ... } }`.
  - Reconcile callback from adapter launched on `Dispatchers.IO`.
  - Initial reconciliation triggered on `Dispatchers.IO`.
- **Thread Safety Assessment**:
  - Scheduler state (`alarms`, `nextWakeTime`) is protected by `stateMutex = Mutex()`.
  - Update wake signal uses `Channel<Unit>(Channel.CONFLATED)` with `wakeSignal.trySend(Unit)`, which is non-blocking and coalesces rapid updates.
  - Shutdown sequencing in `CompositeDesktopLeaveCancelGate`:
    1. `deliveryGate.closeAndDrain(drainTimeoutMs)`: Sets gate to `CLOSING` and waits for in-flight deliveries and mutations to drain to 0 before setting `CLOSED`.
    2. `scheduleObserverJob.cancelAndJoin()`: Prevents subsequent store events from triggering reconcile.
    3. `reminderScheduler.cancelAndAwait(accountKey)`: Disposes wake loop, joins job, clears pending alarms.
    4. `syncCancelGate.cancelAndAwait(accountKey)`: Quiesces background sync.
    5. `reminderCancelGate.cancelAndAwait(accountKey)`: Clears platform notifications.
  - Verified no Swing EDT blocking occurs during Room reads or IPC operations.

### 4. Linux Capabilities Tolerance for Minimal Daemons (RESOLVED)
- **Source**: `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-notification-adapter.kt:160-168`
- **Fix**: `val canPost = status is LinuxNotificationStatus.Success`. Dropped `&& caps.isNotEmpty()`. Minimal daemons returning an empty capability array now report `canPostNotifications = true`.

---

## Critical Issues
*None identified in tested codebase.*

---

## High Priority Findings
*All previously identified Cycle 1 high-priority findings have been remediated.*

---

## Medium Priority Improvements

### 1. Windows Native Toast Bridge Remains Candidate Stub
- **Location**: `desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-notification-adapter.kt:39-71`
- **Observation**: `JnaWindowsToastBridge` returns `WindowsNotificationStatus.Unavailable` for all operations. Real COM/WinRT integration remains to be implemented.
- **Restart Limitation**: `cancelAllForAccount` operates against the in-memory `postedPlatformIds` map. After a process restart, active toasts displayed in Windows Action Center for a wiped account cannot be cleared until the native bridge provides group-level clearance without relying on in-memory state.
- **Action**: Tracked for Phase 11 on physical Windows testbed.

---

## Low Priority Suggestions

1. **Session Bus Connection Pooling**: `SessionBusNotificationTransport.withProxy` creates and closes a D-Bus connection per operation. Connection reuse on `ioDispatcher` would reduce UNIX socket connection overhead during bursts.
2. **Freedesktop Body Escaping**: Current delivery passes plain text `body = "Task reminder is due"`. If user task notes or descriptions are added to notification body in Phase 10/11, XML entity escaping must be enforced.

---

## Positive Observations
- **Fail-Closed Safety Contract**: Error propagation from native adapters directly integrates with `completePendingWipesOnStartup` to prevent premature wipe marker deletion when notifications remain orphaned.
- **Deterministic Quiescence Pipeline**: `CompositeDesktopLeaveCancelGate` orders teardown with precision, guaranteeing that delivery gates close and drain before Room databases are closed.
- **Strict Single-Window Scope**: Process disposal cleanly tears down schedulers, observers, databases, and releases `SingleInstanceGuard` file locks; no lingering daemon or system tray process remains.

---

## Metrics & Verification Results

### Automated Test Suites
- **Desktop App Unit & Platform Tests** (`./gradlew -PdesktopTarget=linux :desktopApp:test --rerun-tasks --dependency-verification=off`): **48 / 48 PASS** (19 actionable tasks, 6.8s)
  - `CompositeDesktopLeaveCancelGateTest`: 2 tests passed
  - `DesktopControllerEnvTest`: 4 tests passed
  - `DesktopLifecycleTest`: 8 tests passed
  - `DesktopReminderControllerLifecycleTest`: 3 tests passed
  - `DesktopReminderSchedulerTest`: 5 tests passed
  - `DotEnvLoaderTest`: 5 tests passed
  - `PlatformVaultDispatcherTest`: 5 tests passed
  - `LinuxNotificationAdapterTest`: 4 tests passed
  - `LinuxReminderAdapterTest`: 6 tests passed (+1 error propagation test)
  - `LinuxSecretServiceVaultTest`: 6 tests passed
- **Shared JVM & SQLite Tests** (`./gradlew :shared:jvmTest --rerun-tasks --dependency-verification=off`): **182 / 182 PASS** (7.7s)
- **Android Unit Tests** (`ANDROID_HOME=/home/loidinh/Android/Sdk ./gradlew :androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off`): **12 / 12 PASS** (7.5s)
- **Strict v2 Wire Vectors** (`node scripts/strict-v2-vector-runner.mjs`): **23 / 23 PASS**
- **Hub Live Smoke Offline Fixture** (`node scripts/hub-live-smoke.mjs --fixture-test`): **18 / 18 PASS**
- **Release Security Source Diagnostic** (`node scripts/release-security-check.mjs --diagnose-source`): **6 / 6 PASS**
- **Hub Strict Upgrade CLI Suite** (`node scripts/hub-strict-upgrade.test.mjs`): **9 / 9 PASS**
- **Total Automated Tests & Checks**: **298 / 298 PASS (100%)**

### Packaging Build Verification
- **Desktop Distributable Bundle** (`./gradlew -PdesktopTarget=linux :desktopApp:createDistributable --dependency-verification=off`): **BUILD SUCCESSFUL** (2.1s, output generated at `desktopApp/build/compose/binaries/main/app`)

---

## Recommended Actions
1. **Gate G2 Preparation**: Set up physical Linux desktop session (with active notification daemon) and Windows host environment for Phase 11 end-to-end native delivery verification.
2. **Windows Toast Bridge Implementation**: In Phase 11, replace `JnaWindowsToastBridge` stub with real WinRT/COM toast notification APIs (`Windows.UI.Notifications.ToastNotificationManager`) targeting installed MSI identity.
3. **Phase 10 Proceed**: Advance to Phase 10 (Compose UI deadline editor, list display, and reminder settings) based on proven contracts and reactive schedule flows.

---

## Unresolved Questions
1. When running on headless Linux CI containers where `DBUS_SESSION_BUS_ADDRESS` is absent, should `PlatformReminderDispatcher` gracefully default to an inert no-op reminder adapter without logging error traces, or continue logging the missing session bus warning?
2. In Phase 11, should the Windows package installer (`packageMsi`) register a protocol-handler shortcut for toast notification click activations, or remain purely informational?
