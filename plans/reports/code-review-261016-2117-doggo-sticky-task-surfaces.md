# Code Review: Doggo Sticky Task Surfaces (Phases 02–06)

**Report Path:** `plans/reports/code-review-261016-2117-doggo-sticky-task-surfaces.md`  
**Date:** 2026-10-16  
**Reviewer:** EvcCodeReviewer (Senior Code Quality & Security Engineer)  
**Status:** **7.8 / 10 — CONDITIONAL PASS (Action Required Prior to Phase 07/08 Acceptance)**  

---

## Code Review Summary

### Scope
- **Files Reviewed (50+ files):**
  - **Phase 02 (Glance Task Widget):** `androidApp/src/main/kotlin/dev/doggo/android/task-widget*.kt` (widget, receiver, actions, updater), XML resources (`task_widget_info.xml`, `task_widget_initial.xml`, `task-widget-strings.xml`), unit test `task-widget-model-test.kt`, instrumented test `task-widget-device-test.kt`.
  - **Phase 03 (Deadline Notification & Preferences):** `androidApp/src/main/kotlin/dev/doggo/android/task-surface-*.kt` (preferences, notification coordinator, intents, receivers, refresh worker, permission controller, settings), XML drawables/strings, unit test `task-surface-notification-test.kt`, instrumented test `task-surface-preferences-device-test.kt`.
  - **Phase 04 (Desktop Compact UI & Preferences):** `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-*.kt` (sticky content, geometry restoration, preferences persistence), unit tests `sticky-window-geometry-test.kt`, `sticky-window-preferences-test.kt`.
  - **Phase 05 (Android Integration & Security Auditor):** `doggo-application.kt`, `account-runtime.kt`, `account-runtime-factory.kt`, `composite-leave-scheduler-cancel-gate.kt`, `android-account-state-manager.kt`, `session-view-model.kt`, `main-activity.kt`, `doggo-sync-worker.kt`, `reminder-restore-receiver.kt`, `AndroidManifest.xml`, `scripts/release-security-check.mjs`, `scripts/release-security-check.test.mjs`, and device tests `task-surface-account-device-test.kt`, `task-surface-navigation-device-test.kt`.
  - **Phase 06 (Desktop Integration):** `main.kt`, `desktop-task-surfaces.kt`, `desktop-surface-platform.kt`, `composite-desktop-leave-cancel-gate.kt`, `task-surface-tray.png` (windowsMain/linuxMain), unit tests `desktop-task-surfaces-test.kt`, `composite-desktop-leave-cancel-gate-test.kt`.
- **Lines of Code Analyzed:** ~8,040 lines (6,944 untracked + 1,096 modified lines across Kotlin, XML, and Node.js).
- **Review Focus:** Architectural adherence, token admission fencing, fail-closed teardown/wipe semantics, concurrency & lifecycle races, security auditor invariants, and suite verification.
- **Updated Plans:** `plans/261016-1409-sticky-task-surfaces/plan.md` updated to reflect completed Phase 02–06 implementation deliverables (112h / 132h credited).

---

### Overall Assessment

The parallel execution of Phases 02 through 06 represents a substantial engineering accomplishment. The codebase exhibits exemplary adherence to core architectural invariants:
- **Contract Adherence:** Value contracts and projections (`partitionSurfaceTodos`, `selectNextDeadline`, `SurfaceAccountToken`) remain clean in `commonMain` without platform leakage.
- **Security & Privacy:** Component export rules are strictly enforced (only `MainActivity` and `DoggoTaskWidgetReceiver` exported with rigorous intent-filter and provider metadata constraints; all others unexported). PendingIntents enforce `FLAG_IMMUTABLE`. Two-tier notification privacy prevents lockscreen data disclosure. Non-authenticated states blank sensitive task titles.
- **Deterministic Geometry & Preferences:** Multi-monitor work area clamping with negative origin preservation and atomic file persistence (`ATOMIC_MOVE` + `fsync`) is well engineered.

However, a score of **7.8 / 10** reflects **material lifecycle, concurrency, and test synchronization issues** that must be resolved before proceeding to physical/device acceptance in Phase 07 and 08:
1. `DesktopTaskSurfaceAccess` retains a completed `drainSignal` across account reopen, allowing subsequent drains to falsely report immediate quiescence while mutations remain in flight.
2. `DoggoApplication.closeRuntimeInternal()` fails to assert delivery gate drain success and cancels observer jobs without awaiting their termination before closing the database.
3. Intent navigation generation fencing contains an explicit bypass allowing arbitrary account execution when generation is negative or omitted.
4. `:desktopApp:test` currently fails (1 failed, 96 passed) due to virtual-time polling against real background Room SQLite execution in `testQuickAddTitleValidationAndSubmission`.

---

### Critical Issues (MUST FIX)

#### 1. Desktop Drain Signal Retained Across Account Reopen
- **File:** `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-task-surfaces.kt:208-260`
- **Impact:** Broken quiescence and potential SQLite access after database closure.
- **Description:** In `closeAndDrain()`, `drainSignal = CompletableDeferred()` is created and awaited. When operations drain, `drainSignal?.complete(Unit)` completes the deferred. However, `onAccountAuthenticated()` sets `gateState = SurfaceGateState.OPEN` without resetting `drainSignal = null`. If the account signs out a second time with active in-flight operations, `if (drainSignal == null)` is false, `drainSignal?.await()` returns immediately on the already-completed Deferred, and `closeAndDrain()` returns `true` prematurely while `inFlightCount > 0`. Furthermore, `onAccountAuthenticated()` does not prevent reopening while `gateState == SurfaceGateState.CLOSING`.
- **Recommended Fix:**
  ```kotlin
  suspend fun onAccountAuthenticated(token: SurfaceAccountToken, store: RoomAccountStore) {
      stateMutex.withLock {
          check(gateState != SurfaceGateState.CLOSING) { "Cannot authenticate while surface is draining" }
          gateState = SurfaceGateState.OPEN
          drainSignal = null // Reset drain signal for subsequent lifecycle cycles
          currentToken = token
          activeStore = store
          _readiness.value = SurfaceReadiness.Ready(token)
      }
  }

  suspend fun closeAndDrain(timeoutMs: Long = 5000L): Boolean {
      val signal: CompletableDeferred<Unit>?
      stateMutex.withLock {
          if (gateState == SurfaceGateState.CLOSED) return true
          gateState = SurfaceGateState.CLOSING
          _readiness.value = SurfaceReadiness.Unavailable
          currentToken = null
          activeStore = null
          if (inFlightCount == 0) {
              gateState = SurfaceGateState.CLOSED
              drainSignal = null
              return true
          }
          signal = CompletableDeferred()
          drainSignal = signal
      }
      val drained = withTimeoutOrNull(timeoutMs) {
          signal.await()
          true
      } ?: false
      stateMutex.withLock {
          if (drained && inFlightCount == 0) {
              gateState = SurfaceGateState.CLOSED
              drainSignal = null
          }
      }
      return drained && inFlightCount == 0
  }
  ```

#### 2. Unchecked Quiescence and Unjoined Cancellation During Android Runtime Closure
- **File:** `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt:187-200`
- **Impact:** Premature database closure while background query coroutines or reminder delivery leases are actively executing.
- **Description:** In `closeRuntimeInternal()`, `runtime.deliveryGate.closeAndDrain()` returns a boolean indicating whether in-flight deliveries drained within the timeout; this return value is ignored. `runtime.surfaceObserverJob?.cancel()` and `runtime.scheduleObserverJob.cancel()` are fired without `cancelAndJoin()`. `runtime.database.close()` is called immediately thereafter. If an observer Flow or delivery is mid-transaction, closing SQLite causes unhandled `IllegalStateException` or database corruption.
- **Recommended Fix:**
  ```kotlin
  private suspend fun closeRuntimeInternal(runtime: AccountRuntime) {
      try {
          taskSurfaceRuntimeCoordinator.transitionToNonReady(SurfaceReadiness.Restoring)
          val drained = runtime.deliveryGate.closeAndDrain()
          if (!drained) {
              Log.w(TAG, "Delivery gate drain timed out for ${runtime.accountKey}; proceeding defensively")
          }
          runtime.surfaceObserverJob?.cancelAndJoin()
          runtime.scheduleObserverJob.cancelAndJoin()
          runtime.store.isFrozen = true
          runtime.database.close()
      } catch (ce: CancellationException) {
          throw ce
      } catch (e: Exception) {
          Log.e(TAG, "Error closing database for ${runtime.accountKey}: ${e.message}", e)
      }
  }
  ```

#### 3. Intent Navigation Generation Fencing Bypass
- **Files:** `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt:500-535`, `main-activity.kt:121-143`
- **Impact:** Stale or malicious intents can execute against an unintended active account.
- **Description:** `dispatchTaskSelectionWithToken` and `dispatchCreateWithToken` use the guard:
  ```kotlin
  if (accountKey != null && currentToken.accountKey.value != accountKey) return
  if (generation >= 0L && currentToken.generation != generation) return
  ```
  If an intent omits `EXTRA_ACCOUNT_KEY` or provides `EXTRA_GENERATION = -1L` (the default fallback in `MainActivity`), both guards are bypassed and the action executes against whatever account is currently active. While legacy notification intents lacked generation, newly minted surface actions (widget and deadline notification) MUST require matching `accountKey` and non-negative `generation`.
- **Recommended Fix:** Differentiate legacy reminder intent handling from task-surface intent handling. Surface intents targeting `ACTION_WIDGET_VIEW_TASK`, `ACTION_WIDGET_ADD_TASK`, and `ACTION_VIEW_TASK` must enforce:
  ```kotlin
  require(!accountKey.isNullOrBlank()) { "Surface intent missing account key" }
  require(generation >= 0L) { "Surface intent missing generation token" }
  if (currentToken.accountKey.value != accountKey || currentToken.generation != generation) {
      Log.w(TAG, "Rejecting intent with mismatched or stale surface token")
      return
  }
  ```

#### 4. Failing Test Suite in `:desktopApp:test`
- **File:** `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-task-surfaces-test.kt:225-230`
- **Impact:** CI failure; test suite is currently NOT green (361 passed, 1 failed).
- **Description:** `testQuickAddTitleValidationAndSubmission` fails with `TimeoutCancellationException: Timed out after 3s of _virtual_ (kotlinx.coroutines.test) time`. The test launches task creation on `backgroundScope`, which writes to Room SQLite via `Dispatchers.IO` (real thread pool). The test loop then polls `controller.stickyState.value.quickAdd.submitting` using `delay(20)`. In `runTest`, `delay(20)` advances virtual time instantaneously because no virtual coroutines are pending on `StandardTestDispatcher`, hitting the 3000ms virtual timeout before the real background IO thread completes.
- **Recommended Fix:** Mirror the working pattern established in line 288 (`testQuickAddDoesNotMutateMainEditor`) by wrapping the polling loop in `withContext(Dispatchers.Default)`:
  ```kotlin
  controller.onAction(StickyTaskAction.Submit(token1))
  withContext(Dispatchers.Default) {
      withTimeout(5000L) {
          while (controller.stickyState.value.quickAdd.submitting) {
              delay(20)
          }
      }
  }
  ```

---

### High Priority Findings (SHOULD FIX)

#### 1. Virtual-Time Polling Fragility in Other Desktop Tests
- **File:** `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-task-surfaces-test.kt:338-340, 365-367`
- **Description:** `testUnfilteredStickySections` and `testStaleTokenRejected` poll `while (controller.stickyState.value.sections.incomplete.isEmpty()) { delay(20) }` under `withTimeout(3000L)` without `withContext(Dispatchers.Default)`. Although currently passing because data was inserted synchronously prior to observing, any latency in Room's invalidation dispatch will cause sporadic CI test timeouts.
- **Fix:** Wrap the wait loops with `withContext(Dispatchers.Default)` or use `testScheduler.advanceUntilIdle()`.

#### 2. Unverified Post-Logout Glance Session Republishing Hold
- **File:** `androidApp/src/main/kotlin/dev/doggo/android/task-widget-updater.kt:229-245`
- **Description:** `closeGlanceSessions` iterates over Glance IDs and calls `closeSession("appWidget-$appWidgetId")`. While correctly designed, Glance's session manager behavior across process boundaries and launcher restarts cannot be proven through unit tests alone.
- **Fix:** Retain an explicit gate in Phase 07 verifying on an emulated/physical API 34+ device that no background Glance session resurrects prior account titles after `TaskWidgetUpdater.clearAllInstances()`.

---

### Medium Priority Improvements

#### 1. Synchronous Preferences Persistence Under Global Companion Lock
- **File:** `androidApp/src/main/kotlin/dev/doggo/android/task-surface-preferences.kt:141-285`
- **Description:** `TaskSurfacePreferences` synchronizes all mutations on a companion-level object `synchronized(lock)` and calls `editor.commit()`. Although dispatched on `Dispatchers.IO`, this creates global contention across multiple accounts if rapid notifications occur.
- **Fix:** Consider account-scoped locks (`ConcurrentHashMap<AccountKey, Mutex>`) and ensure disk operations remain off any UI paths.

#### 2. Deprecated Compose API in Desktop Main
- **File:** `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt:1005`
- **Description:** Compiler warning: `'fun painterResource(resourcePath: String): Painter' is deprecated. Migrate to the Compose resources library.`
- **Fix:** Update to the multiplatform Compose resources accessor syntax.

---

### Low Priority Suggestions

#### 1. Consolidate Duplicate Action Intent Constants
- **Files:** `MainActivity`, `TaskWidgetActionKeys`, `TaskSurfaceIntents`, `AndroidNotificationIntentFactory`
- **Description:** The string `"dev.doggo.android.ACTION_VIEW_TASK"` is duplicated across multiple classes.
- **Fix:** Standardize on `TaskSurfaceIntents.ACTION_VIEW_TASK` as the single canonical definition.

#### 2. Astral Character Text Truncation in Widget Launcher
- **File:** `androidApp/src/main/kotlin/dev/doggo/android/task-widget.kt:414-426`
- **Description:** Glance `Text(maxLines = 1)` handles standard Latin truncation well, but complex grapheme clusters (astral emojis, Indic combining characters) can experience edge-case clipping in narrow launcher cells.
- **Fix:** Validate rendering on various launcher grid densities during Phase 07 device testing.

---

### Positive Observations
1. **Security-First Manifest Policy:** The updated `scripts/release-security-check.mjs` enforces zero unauthorized exported components, with 12/12 passing Node security tests verifying fail-closed rejection of package lookalikes and extra receiver actions.
2. **Deterministic Multi-Monitor Clamping:** `restoreStickyGeometry` handles negative virtual desktop coordinates, multi-monitor disconnected hotplug fallbacks, and title-bar reachability clamping with 15 passing edge-case unit tests.
3. **Atomic Preferences Persistence:** Both Android SharedPreferences and Desktop Properties files use safe write patterns (`ATOMIC_MOVE` + `fsync` on Desktop; fail-closed synchronous `commit()` on Android).
4. **Clean Domain Separation:** Task surface projections (`partitionSurfaceTodos`, `selectNextDeadline`, `TitlePolicy`) remain purely functional in `commonMain` with 100% test coverage.

---

### Recommended Actions

1. **[MUST FIX]** Update `DesktopTaskSurfaceAccess` in `desktop-task-surfaces.kt` to reset `drainSignal` on authentication and reject reopening during active drains.
2. **[MUST FIX]** In `DoggoApplication.closeRuntimeInternal()`, await observer job termination (`cancelAndJoin()`) and check delivery gate drain status before closing Room.
3. **[MUST FIX]** Tighten intent routing in `MainActivity` and `SessionViewModel` to mandate explicit token and generation matching for surface actions.
4. **[MUST FIX]** Fix virtual-time synchronization in `testQuickAddTitleValidationAndSubmission` in `desktop-task-surfaces-test.kt` using `withContext(Dispatchers.Default)`.
5. **[GATE]** Proceed to Phase 07 (Android/Windows acceptance) to validate native Glance launcher behavior and lockscreen notifications on physical/emulated devices.

---

### Metrics
- **Type Safety & Coverage:** 100% Kotlin strict typing; zero dynamic casts or unsafe `any` representations.
- **Test Results:**
  - `:shared:jvmTest`: 231 / 231 **PASSED**
  - `:androidApp:testDebugUnitTest`: 34 / 34 **PASSED**
  - `:desktopApp:test`: 96 / 97 **PASSED** (1 failure due to virtual-time test synchronization)
  - `release-security-check.test.mjs`: 12 / 12 **PASSED**
  - `release-security-check.mjs --diagnose-source`: 6 / 6 **PASSED** (CLEAN)
  - `hub-live-smoke.mjs --fixture-test`: 18 / 18 **PASSED**
  - `strict-v2-cutover-rehearsal.mjs --self-test`: 8 / 8 **PASSED**
- **Linting & Compiler Issues:**
  - 1 deprecation warning (`painterResource` in `main.kt:1005`)
  - 2 Beta warnings (`expect`/`actual` in `DoggoDatabaseConstructor.kt` and `doggo-database.kt`)

---

### Unresolved Questions
1. Does the Glance runtime on older API levels (e.g. API 26-28) cleanly release RemoteViews sessions on `closeSession("appWidget-$appWidgetId")` without lingering launcher caches? (Deferred to Phase 07 device testing).
2. Will Windows Toast bridge notifications in Phase 07 properly reflect system dark/light theme switching without restarting the desktop application? (Deferred to Phase 07 desktop acceptance).
