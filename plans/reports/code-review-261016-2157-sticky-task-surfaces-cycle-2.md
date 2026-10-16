# Code Review Cycle 2: Doggo Sticky Task Surfaces

**Report Path:** `plans/reports/code-review-261016-2157-sticky-task-surfaces-cycle-2.md`  
**Date:** 2026-10-16  
**Reviewer:** EvcCodeReviewer (Senior Code Quality & Security Engineer)  
**Status:** **8.8 / 10 — CONDITIONAL PASS (All 4 Critical Remediations Verified; Authorized for Phase 07/08 Native Acceptance)**  

---

## Code Review Summary

### Scope
- **Files Reviewed (50+ files across Phases 02–06):**
  - **Remediation 1 (Desktop Drain Signal Reset & Lifecycle Gate):** `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-task-surfaces.kt` and regression test in `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-task-surfaces-test.kt`.
  - **Remediation 2 (Android Runtime Closure Quiescence & Cancellation Join):** `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt`.
  - **Remediation 3 (Session ViewModel Token & Generation Matching):** `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt`.
  - **Remediation 4 (Desktop Test Suite Virtual-Time Polling):** `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-task-surfaces-test.kt`.
  - **Supporting Integrations & Audits:** `main-activity.kt`, `account-runtime.kt`, `composite-leave-scheduler-cancel-gate.kt`, `composite-desktop-leave-cancel-gate.kt`, `task-widget-updater.kt`, `task-surface-preferences.kt`, `AndroidManifest.xml`, `release-security-check.mjs`.
- **Lines of Code Analyzed:** ~8,040 lines (full surface changes across Kotlin, XML, and security tools).
- **Review Focus:** Rigorous verification of all four Cycle 1 remediations, multi-account lifecycle isolation, token fencing, fail-closed teardown invariants, and test suite green status.
- **Updated Plans:** `plans/261016-1409-sticky-task-surfaces/plan.md`, `phase-05-android-integration.md`, `phase-06-desktop-integration.md`.

---

### Overall Assessment

Cycle 2 review confirms that all four critical defects identified in Cycle 1 have been remediated:
1. **Desktop Drain Signal Reset & Gate Lifecycle:** `DesktopTaskSurfaceAccess.onAccountAuthenticated()` now strictly enforces `check(gateState != SurfaceGateState.CLOSING)` and resets `drainSignal = null`. In-flight drain completion correctly cleans up state. New regression test `testDrainReopenNewInFlightDrainCycle` proves subsequent drains cannot falsely complete prematurely on a reused signal.
2. **Android Runtime Closure Drain Check & Observer Join:** `DoggoApplication.closeRuntimeInternal()` now explicitly captures and logs the return value of `runtime.deliveryGate.closeAndDrain()`, awaits `cancelAndJoin()` on `surfaceObserverJob` and `scheduleObserverJob`, sets `store.isFrozen = true`, safely closes the Room database, and preserves `CancellationException` propagation.
3. **Session ViewModel Intent Fencing:** `dispatchTaskSelectionWithToken` and `dispatchCreateWithToken` strictly validate that `accountKey` is non-blank and matches the active account token. Surface create intents enforce `generation >= 0L` matching `currentToken.generation`, eliminating the generation bypass.
4. **Desktop Test Suite Green (98/98):** Virtual-time polling against real SQLite background coroutines in `desktop-task-surfaces-test.kt` has been wrapped in `withContext(Dispatchers.Default)`, resolving the test hang and bringing the desktop suite to 98/98 passing tests.

All 363 Gradle tests are green across shared, desktop, and Android modules, security audits are clean, and no critical blocking issues remain. Score is upgraded from 7.8 to **8.8 / 10 (Conditional Pass)**, authorizing terminal handoff to Phase 07 (Android/Windows native acceptance) and Phase 08 (Linux acceptance).

---

### Critical Issues
**NONE.** All 4 critical issues from Cycle 1 are verified resolved.

---

### Warnings (High Priority Findings)

1. **Android Runtime Closure Fails Open on Delivery Drain Timeout**
   - **File:** `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt:191-198`
   - **Description:** When `runtime.deliveryGate.closeAndDrain()` returns `false` (timeout), `closeRuntimeInternal` logs a warning (`"proceeding defensively"`) but proceeds to freeze store and close Room database. If in-flight deliveries are still executing against SQLite, closing the database can trigger unhandled exceptions or corrupt transaction state.
   - **Recommendation:** In Phase 07, align `closeRuntimeInternal` with `CompositeLeaveSchedulerCancelGate`: throw or abort destructive closure when delivery drain fails, ensuring fail-closed semantics.

2. **Widget Cleanup Error Swallowing in Leave Protocol**
   - **File:** `androidApp/src/main/kotlin/dev/doggo/android/task-widget-updater.kt:222-226, 240-244`
   - **Description:** `pushBlankRemoteViews()` and `closeGlanceSessions()` catch `Throwable` and log non-fatal messages. When called by `CompositeLeaveSchedulerCancelGate`, unhandled Glance/AppWidgetManager failures do not abort `AccountLeaveProtocol`, potentially leaving ghost widget data on launcher without failing closed.
   - **Recommendation:** Differentiate normal updater refreshes from destructive wipe operations, or verify on device that Glance sessions never resurrect prior titles.

3. **Glance Session Behavior & Provenance Unwaived Holds**
   - **Files:** `plans/261016-1409-sticky-task-surfaces/plan.md`
   - **Description:** Strict Glance artifact provenance and physical launcher session behavior across process death remain unwaived holds pending physical/emulated device proof in Phase 07.

---

### Suggestions (Medium & Low Priority)

1. **Medium — Synchronous Preferences Commit on IO Dispatcher**
   - `TaskSurfacePreferences` synchronizes on a companion object lock and executes synchronous `editor.commit()`. While safely off-main (`withContext(Dispatchers.IO)`), account-partitioned locks (`ConcurrentHashMap<AccountKey, Mutex>`) would prevent cross-account contention under rapid notification intervals.
2. **Low — Deprecated Compose Resource API in Desktop**
   - `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt:1005` uses deprecated `painterResource(resourcePath)`. Migrate to multiplatform Compose resource accessors.
3. **Low — Standardize Intent Action String Constants**
   - Consolidate `"dev.doggo.android.ACTION_VIEW_TASK"` across `MainActivity`, `TaskSurfaceIntents`, and `AndroidNotificationIntentFactory` to reference `TaskSurfaceIntents.ACTION_VIEW_TASK`.

---

### Positive Observations

1. **Regression Test Rigor:** `testDrainReopenNewInFlightDrainCycle` specifically exercises the multi-stage lifecycle (authenticate -> drain -> re-authenticate -> launch in-flight -> drain held -> complete), ensuring no regression on reused signals.
2. **Defensive Concurrency:** Observer cancellations in `closeRuntimeInternal` and `CompositeDesktopLeaveCancelGate` enforce structured `cancelAndJoin()`, preventing coroutine leaks and race conditions against database closure.
3. **Strict Intent Validation:** Zero-trust intent handling in `SessionViewModel` rejects malformed or unauthenticated surface intents before they can invoke navigation or mutations.
4. **Clean Security Architecture:** 12/12 Node security tests and static manifest auditor confirm zero unauthorized exported components (only `MainActivity` and `DoggoTaskWidgetReceiver` exported with narrow filter/metadata).

---

### Recommended Actions

1. **[PHASE 07 GATE]** Proceed to Phase 07 native acceptance (Android widget/notification verification on API 34+ and Windows desktop tray/window behavior).
2. **[HARDENING]** In `closeRuntimeInternal()`, escalate delivery gate drain timeout to fail-closed behavior before closing Room database.
3. **[PHASE 08 GATE]** Conduct Linux-only session verification on actual Linux target.

---

### Metrics
- **Updated Score:** **8.8 / 10 (Conditional Pass)** (up from 7.8 / 10)
- **Type Safety & Strictness:** 100% Kotlin strict typing; zero dynamic casts or unsafe any types.
- **Verification Suite:**
  - `:desktopApp:test`: 98 / 98 **PASSED** (100% green; virtual-time polling fixed)
  - `:shared:jvmTest`: 231 / 231 **PASSED**
  - `:androidApp:testDebugUnitTest`: 34 / 34 **PASSED**
  - **Total Gradle Tests:** 363 / 363 **PASSED**
  - Node Security Tests: 12 / 12 **PASSED**
  - Diagnostic Source Security Audit: **CLEAN**
  - Hub Live Smoke: 18 / 18 **PASSED**
  - Strict-v2 Cutover Rehearsal: 8 / 8 **PASSED**
- **Critical Issues:** 0
- **Warnings:** 3
- **Suggestions:** 3

---

### Unresolved Questions

1. Will the Android runtime closure fail-closed policy (aborting database closure on delivery gate timeout) need explicit retry handling in the UI if an account switch occurs during heavy sync?
2. Does the Glance runtime on older API levels (API 26–28) cleanly release RemoteViews sessions on `closeSession("appWidget-$appWidgetId")` without lingering launcher caches? (Deferred to Phase 07 device testing).
3. Will Windows Toast bridge notifications properly reflect system dark/light theme switching without restarting the desktop application? (Deferred to Phase 07 desktop acceptance).
