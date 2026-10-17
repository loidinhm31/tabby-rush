# Phase 06 Desktop Integration Evidence Report

**Status:** DELIVERED / READY FOR CENTRALIZED VERIFICATION  
**Date:** 2026-10-16  
**Author:** Phase 06 Desktop Integration Owner (Wave C)  
**Assigned Plan:** `plans/261016-1409-sticky-task-surfaces/phase-06-desktop-integration.md`  
**Architecture Contracts:** `plans/261016-1409-sticky-task-surfaces/architecture-contracts.md`  

---

## 1. Executed & Delivered Artifacts

Strictly conforming to exclusive file ownership boundaries, the following production and test components were delivered:

### 1.1 `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt`
- **Real `DesktopTaskSurfaceAccess` Implementation:**
  - Implements shared `TaskSurfaceAccess` and `LeaveSchedulerCancelGate`.
  - State tracking: `readiness: StateFlow<SurfaceReadiness>` (starts `Restoring`, transitions to `Ready(token)` on runtime establishment, `SignedOut` on sign-out, `Unavailable` during teardown/drain).
  - Bounded 4-second `awaitReadiness(timeoutMs = 4000L)` resolving immediately when ready, or returning `SurfaceReadiness.Unavailable` on timeout.
  - Non-cancellable admission gating (`withSurfaceAdmission(token)`): atomic verification of gate state (`SurfaceGateState.OPEN`) and matching current `SurfaceAccountToken` under `stateMutex`. Increments `inFlightCount`, captures `RoomAccountStore`, and decrements in-flight count safely in `withContext(NonCancellable)` inside `finally`.
  - `read(token)`: Admitted read of `loadTodos()` (partitioned via `partitionSurfaceTodos`) and `loadPersistedSchedule()`, returning `SurfaceSnapshot` in `SurfaceResult.Value` (or `SurfaceResult.Rejected` on stale token).
  - `setCompleted(token, taskId, completed, nowEpochMs)`: Token-validated call to `store.setCompletedLocally(taskId, completed, nowEpochMs)`, returning `CompletionChange`.
  - `publishIfCurrent(token, publish)`: Serialized under a dedicated `publicationMutex` with atomic current token verification before executing publication block.
  - `validateNavigation(token, taskId)`: Validates current token and task presence for deep-linking.
  - `closeAndDrain(timeoutMs = 5000L)`: Safe teardown gating that transitions state to `CLOSING`, blanks active token/store, awaits in-flight drain signal, and closes to prevent outstanding SQLite access.
- **`DesktopStickyTaskController`:**
  - Manages `stickyState: StateFlow<StickyTaskState>`.
  - Token-safe reactive observation: collects `store.observeTodos()` at runtime scope and partitions unfiltered tasks via `partitionSurfaceTodos(todos)`. Verifies token matches current active token to discard stale emissions across account switches.
  - Immediate state blanking: resets sections and quick-add buffer on transitions away from `Ready`.
  - Independent quick-add: `StickyTaskAction.ChangeTitle` validates title via `TitlePolicy` and sets error without affecting main editor; `StickyTaskAction.Submit` rejects duplicate submissions, validates title, constructs task via `createDefaultTodo(title)` with UUID/timestamps and disabled reminders, saves via admitted store call, clears quick-add buffer only if user hasn't typed new text during async submit, and triggers sync.
  - Action delegation: dispatches `SetCompleted` through `access.setCompleted`, and routes `OpenApp` and `Quit` to window/application lifecycle callbacks.
- **Shared Creation Helper:** `createDefaultTodo(title, nowEpochMs, id)` pure helper used by both sticky quick-add and main editor creation.

### 1.2 `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-surface-platform.kt`
- **Platform Seam Contracts:**
  - `DesktopSurfacePlatform`: `monitorWorkAreas(): List<MonitorWorkArea>`, `traySupported(): Boolean`, `sessionDescription(): String`.
  - `DesktopSurfacePlatformProvider`: Service Provider Interface for pluggable platform providers (ready for Phase 08 Linux StatusNotifierItem).
- **Default Generic AWT Platform (`DefaultAwtDesktopSurfacePlatform`):**
  - Enumerates `GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices`.
  - Computes work-area bounds in user-space logical dp by subtracting `Toolkit.getDefaultToolkit().getScreenInsets(gc)` from `gc.bounds`.
  - Captures display transforms `scaleX` and `scaleY` without applying double-scaling.
  - Determines primary display and queries `SystemTray.isSupported()` with headless environment protection.
- **Provider Resolution (`DesktopSurfacePlatformDispatcher`):**
  - Discovers providers via `ServiceLoader.load(DesktopSurfacePlatformProvider::class.java)`, falling back to `DefaultAwtDesktopSurfacePlatform`.
  - Supports `setForTest(platform)` test overrides for deterministic unit and lifecycle testing.

### 1.3 `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate.kt`
- **Updated Composite Quiescence Order:**
  - Added `surfaceCancelGate: LeaveSchedulerCancelGate? = null` and `surfaceObserverJob: Job? = null` parameters.
  - Enforces strict sequential teardown order on account leave:
    1. Close delivery gate and drain in-flight reminder deliveries (`deliveryGate.closeAndDrain()`)
    2. Drain surface admissions (`surfaceCancelGate?.cancelAndAwait(accountKey)`)
    3. Cancel and join reactive task surface observer job (`surfaceObserverJob?.cancelAndJoin()`)
    4. Cancel and join reactive schedule observer job (`scheduleObserverJob.cancelAndJoin()`)
    5. Cancel desktop reminder wake loop and alarms (`reminderScheduler.cancelAndAwait()`)
    6. Cancel and await sync scheduler (`syncCancelGate.cancelAndAwait()`)
    7. Cancel platform notifications (`reminderCancelGate.cancelAndAwait()`)
  - Fail-closed invariant: any failure or timeout during surface or scheduler drainage throws `IllegalStateException`, leaving wipe markers intact and database files protected.

### 1.4 `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt`
- **Window & Tray Integration:**
  - Singleton retained Main `Window` and Sticky `Window(alwaysOnTop = true)` in a single Compose `application { ... }` block.
  - Visibility state management: `isMainWindowVisible` and `isStickyWindowVisible` controlled independently.
  - Close-to-hide: main window close hides main window without cancelling sync or releasing profile lock; sticky window close hides sticky window and flushes geometry preferences.
  - No usable tray fallback: if tray is unsupported or unavailable (`!isTrayUsable`), refuses to hide the last visible window; closing sticky window restores main window so the process is never stranded invisible.
  - Tray integration: single Tray with "Open TabbyRush", "Show/Hide Sticky Tasks", and "Quit".
  - Geometry restoration and debouncing: loads presentation from `StickyWindowPreferences`, clamps against current monitors via `restoreStickyGeometry()`, observes `WindowState` size/position changes, and flushes to disk on hide and quit.
- **Controller Lifecycle & Teardown:**
  - Injected `accountFiles: JvmAccountFiles = JvmAccountFiles()` enabling explicit test fixture directory isolation.
  - Migrated `toggleCompleted(todo)` to `store.setCompletedLocally(todo.id, desired, nowEpochMs)`.
  - Replaced inline task creation in `submitEditor` with `createDefaultTodo`.
  - Integrated `DesktopTaskSurfaceAccess` and `DesktopStickyTaskController` into `DesktopAccountRuntime`.
  - Unified idempotent suspend quit teardown (`quitSuspend()`): flushes preferences, drains surfaces, cancels jobs, drains schedulers, closes database, cancels scope, and releases `SingleInstanceGuard` file lock.

### 1.5 `desktopApp/src/main/resources/task-surface-tray.png`
- Valid 32×32 RGBA PNG system tray icon created and verified decodable by Java `ImageIO.read` (type `BufferedImage.TYPE_4BYTE_ABGR`).

---

## 2. Automated Test Suites Authored & Updated

### 2.1 `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt` (New Suite, 8 Tests)
- `testReadinessStateFlowAndAwaitReadiness`: Verifies `Restoring` initial state, 50ms timeout to `Unavailable`, immediate resolution to `Ready(token)` on authentication, and transition to `SignedOut`.
- `testStaleTokenRejected`: Verifies stale token operations for read, setCompleted, navigation, and publication are rejected.
- `testTokenValidatedSetCompleted`: Verifies real SQLite atomic completion toggling: incomplete -> completed (`Changed`), duplicate completed (`Unchanged`), and missing ID (`Missing`).
- `testAdmissionDrainingWithInFlightOperation`: Uses a barrier to hold an active admitted operation, calls `closeAndDrain()`, verifies new operations are rejected while in-flight is held, and confirms `closeAndDrain()` succeeds once released.
- `testPublicationAcrossAccountSwitch`: Verifies publication is rejected if account signed out or invalidated prior to/during publication boundary.
- `testQuickAddTitleValidationAndSubmission`: Tests validation error banners on whitespace-only input, submission blocking, valid submit persistence to Room, buffer clearing, and sync trigger.
- `testEditingQuickAddWhileSavePendingPreservesNewText`: Verifies that typing additional text during an async save preserves the newly typed input without erasing it upon save completion.
- `testQuickAddDoesNotMutateMainEditor`: Instantiates `DesktopController`, opens draft in main editor (`TodoAction.OpenCreate("Main draft in progress")`), submits sticky quick-add, and asserts main editor draft remains completely untouched.
- `testUnfilteredStickySections`: Verifies sticky sections project both incomplete and completed tasks in stable order regardless of main filter state.
- `testStateClearedOnSignedOut`: Verifies that on transition away from `Ready`, sticky sections, quick-add text, and error states are cleared immediately.

### 2.2 `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-lifecycle-test.kt` (Updated Suite, 9 Tests)
- `testCloseToHideVsQuit`: Verifies closing main and sticky windows hides them while retaining `SingleInstanceGuard` file lock on disk; verifies second instance cannot launch while first is hidden; verifies `quitSuspend()` disposes controller and releases lock.
- `testNoUsableTrayFallbackRefusesLastVisibleHide`: Verifies that when tray is unusable, closing the last visible window is refused and closing sticky window restores main window.
- Retained existing tests for lock acquire, lock file retention on close, sync scheduler immediate/periodic/cancellation, and loopback endpoint policy.

### 2.3 `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate-test.kt` (Updated Suite, 3 Tests)
- `testSequentialQuiescenceExecution`: Verifies exact quiescence sequence: surface drained -> surface observer cancelled -> schedule observer cancelled -> sync cancelled -> reminders cancelled.
- `testDrainFailureFailsClosedWithoutProceeding`: Verifies delivery gate drain failure stops before cancelling schedulers.
- `testSurfaceDrainFailureFailsClosed`: Verifies surface cancel gate failure/timeout throws `IllegalStateException` without cancelling sync or notifications.

### 2.4 `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-controller-lifecycle-test.kt` (Updated Suite, 4 Tests)
- Injected `accountFiles = accountFiles` into all test cases for complete directory isolation.
- `testWindowHidePreservesReminderSubsystemLifetime`: Injects usable tray platform, closes both windows (asserting both are hidden), confirms reminder delivery gate and schedule observer remain `OPEN` and active, and verifies explicit quit cancels schedule observer and closes delivery gate.
- Retained existing tests for startup wipe recovery cancellation, fail-closed wipe marker retention on cancellation failure, and runtime initialization.

---

## 3. Review of Advisor Recommendations

| Recommendation | Action Taken |
|----------------|--------------|
| Put quick-add and observer behind session boundary | Both admitted under `withSurfaceAdmission(token)` and verified against current token; non-Ready transitions clear sections and quick-add buffer. |
| Make admission validation atomic and cancellation-safe | Gated under `stateMutex`; decrement and drain completion handled in `withContext(NonCancellable)` inside `finally`. |
| Harden quick-add completion behavior | Rejects duplicate submits if `submitting == true`; clears buffer only if user hasn't typed new text during save; resets submitting flag on failure. |
| Close-to-hide vs explicit quit | Ordinary hide preserves sync and schedulers; explicit quit executes bounded suspend teardown and releases profile lock. |
| No usable tray recovery fallback | Refuses to hide last visible window when tray is unusable; restores main window if sticky close would strand process. |
| Import fix in composite cancel gate test | Added `import kotlin.test.assertFalse`. |
| Prevent coroutine hangs in tests | Used `backgroundScope` for controller background jobs in `runTest`. |
| Directory isolation in reminder tests | Passed `accountFiles = accountFiles` to `DesktopController` constructors. |

---

## 4. Verification Status & Future Acceptance

- **Automated Tests:** Authored and updated across 4 desktop test files covering contracts, race conditions, lifecycle transitions, and isolation boundaries.
- **Compilation & Test Execution:** Deferred to centralized verification by the main integration agent per project execution guidelines.
- **Native Acceptance Handoff (Phase 07/08):**
  - Windows: Verify native tray icon presentation, window stacking (`alwaysOnTop = true`), close-to-hide restore from tray, and explicit quit.
  - Linux: Phase 08 will provide native StatusNotifierItem provider via `DesktopSurfacePlatformProvider` SPI.
  - Future command: `gradlew.bat :desktopApp:runDebug -PdesktopTarget=windows`.
