# Code Review Report: Phase 08 Android Alarms, Private Notifications & Restoration (Cycle 2 Re-Review)

**Date**: 2026-10-15  
**Reviewer**: Senior Software Engineer / Code Quality Reviewer  
**Target Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-08-android-alarms-notifications-and-restore.md`  
**Quality Score**: 9.2 / 10  
**Verdict**: Approved (All Cycle 1 Critical and Warning issues resolved; zero critical issues remain; ready for Gate G2 physical device acceptance)

---

## 1. Executive Summary

Cycle 2 re-review evaluates the remediations applied to Phase 08 after initial findings. All previous findings have been remediated:
1. Cold-start notification routing now caches pending target payloads in `SessionViewModel.pendingTarget` and automatically transitions UI state to `EditorState` upon SQLite task load in `reloadTodos()`.
2. Startup wipe recovery in `AndroidAccountStateManager.completePendingWipes` now calls `syncScheduler.cancelAndAwait(key)` inside `runBlocking` before deleting database files and clearing markers.
3. `AndroidAlarmRegistry` employs account-wide static synchronization (`synchronized(lock)`), synchronous `commit()` disk persistence, and deletes SharedPreferences XML files on API 24+.
4. `CompositeLeaveSchedulerCancelGateTest` exercises the production `CompositeLeaveSchedulerCancelGate` directly across three scenarios (ordering, drain timeout failure, and sync exception propagation).
5. Channel preflight in `ReminderPermissionController.evaluateState()` creates the reminder channel before capability queries.

Additionally, runtime replacement now drains in-flight reminder deliveries (`closeRuntimeInternal`), and `ReminderRestoreReceiver` executes wipe recovery inside `goAsync()` to avoid blocking the main UI thread. All 16 production files conform strictly to the architectural constraint (<200 LOC per file).

---

## 2. Status of Previous Findings

### Critical Issues (from Cycle 1)

#### 3.1. Cold-Start Notification Tap Drops Navigation
- **Initial Status**: CRITICAL.
- **Remediation Verified**:
  - In `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt`:
    - Added `private var pendingTarget: Pair<String, String>? = null`.
    - `selectTaskFromNotification()` checks if `currentAccountKey` is null or `allTodos.isEmpty()`. If so, stores `pendingTarget = Pair(accountKey, taskId)`.
    - Once session is restored and `reloadTodos()` loads tasks from `RoomAccountStore`, it triggers `dispatchTaskSelection(k, t)`.
    - `dispatchTaskSelection()` validates active account key match, checks pending wipe marker, resolves task from `allTodos`, transitions UI state to `EditorState`, and resets `pendingTarget = null`.
    - `onUnauthenticated()` clears `pendingTarget = null`.
- **Status**: **RESOLVED**.

---

### Warnings (from Cycle 1)

#### 4.1. Startup Wipe Recovery Does Not Await Sync Quiescence
- **Initial Status**: WARNING.
- **Remediation Verified**:
  - In `androidApp/src/main/kotlin/dev/doggo/android/android-account-state-manager.kt`:
    - `completePendingWipes()` calls `syncScheduler.cancelAndAwait(key)` inside `runBlocking(Dispatchers.Default)` prior to deleting database artifacts and clearing wipe markers.
    - If work cancellation times out or fails, exception aborts wipe completion, maintaining fail-closed marker state for retry on subsequent launch.
- **Status**: **RESOLVED**.

#### 4.2. Concurrency Race and Non-Durable Writes in `AndroidAlarmRegistry`
- **Initial Status**: WARNING.
- **Remediation Verified**:
  - In `androidApp/src/main/kotlin/dev/doggo/android/android-alarm-registry.kt`:
    - Added companion static mutex `lock` synchronizing all read/write methods across any registry instance for the same account.
    - Switched from asynchronous `.apply()` to synchronous `.commit()`, returning status and ensuring durable disk writes before receiver returns.
    - Added API 24+ `context.deleteSharedPreferences(prefName)` in `clearAll()`.
- **Status**: **RESOLVED**.

#### 4.3. Unit Test Substitutes Anonymous Implementation for Production Cancel Gate
- **Initial Status**: WARNING.
- **Remediation Verified**:
  - In `androidApp/src/test/kotlin/dev/doggo/android/composite-leave-scheduler-cancel-gate-test.kt`:
    - Replaced anonymous test objects with direct instantiations of the production `CompositeLeaveSchedulerCancelGate` class.
    - Implemented 3 comprehensive tests: `testProductionGateDrainsAndCancelsInOrder`, `testProductionGateFailsClosedWhenDrainTimesOut`, and `testProductionGateFailsClosedWhenSyncCancelThrows`.
- **Status**: **RESOLVED**.

---

### Suggestions (from Cycle 1)

#### 5.1. Channel Importance Preflight in `ReminderPermissionController.evaluateState()`
- **Remediation Verified**: `ReminderPermissionController.evaluateState()` explicitly calls `AndroidNotificationIntentFactory.ensureNotificationChannel(context)` before checking `isChannelEnabled`.
- **Status**: **RESOLVED**.

#### 5.2. Delete SharedPreferences File on Account Wipe
- **Remediation Verified**: API 24+ `context.deleteSharedPreferences()` integrated in `AndroidAlarmRegistry.clearAll()`.
- **Status**: **RESOLVED**.

#### 5.3. Discarded Return Value in `MainActivity.onResume()`
- **Status**: Documented for Phase 10 integration (UI settings and permission warning banners).

---

## 3. Additional Enhancements Applied in Cycle 2

1. **Non-Blocking Wipe Recovery in Broadcast Receivers**:
   - In `ReminderRestoreReceiver.kt`, moved `app.completePendingWipesOnStartup()` inside the `goAsync()` coroutine block under `EXECUTION_TIMEOUT_MS` (9s) timeout. This guarantees `onReceive()` returns immediately without blocking Android's main UI thread on sync quiescence.
2. **Delivery Quiescence on Runtime Teardown**:
   - In `DoggoApplication.closeRuntimeInternal()`, added `runtime.deliveryGate.closeAndDrain()` before database closure and store freeze. This prevents races between background delivery admissions and database connection teardown during runtime replacement or explicit leave.

---

## 4. Critical Issues (MUST FIX)

*None. Zero critical issues remain.*

---

## 5. Warnings (SHOULD FIX)

### 5.1. Physical OEM Battery Optimization & Exact Quotas (Gate G2 Acceptance)
- **Problem**: Stock Android emulator behaviors differ from aggressive OEM battery savers (Xiaomi MIUI/HyperOS, Samsung OneUI, Huawei EMUI) which throttle or defer `setExactAndAllowWhileIdle` alarms when in deep sleep.
- **Mitigation / Next Step**: Must be validated on physical hardware testbeds during Gate G2 / Phase 11 cross-device verification.

---

## 6. Suggestions (NICE TO HAVE)

### 6.1. Notification Grouping for Multi-Task Reminders
- **Suggestion**: When multiple reminders fire concurrently or accumulate while device is idle, configure `setGroup("doggo_reminders_group")` with an optional summary notification on API 24+ to keep the notification shade compact.

---

## 7. Reviewed Files

| File | Status | LOC | Responsibilities / Evaluation |
|---|:---:|:---:|---|
| `androidApp/src/main/AndroidManifest.xml` | Modified | 51 | Declares permissions (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`), unexported receivers. |
| `androidApp/src/main/kotlin/dev/doggo/android/android-notification-adapter.kt` | Modified | 189 | AlarmManager & NotificationManager bridge; private lock-screen notifications; fallback logic. |
| `androidApp/src/main/kotlin/dev/doggo/android/android-notification-intent-factory.kt` | Added | 112 | Immutable PendingIntents, identity URIs without user text, channel setup. |
| `androidApp/src/main/kotlin/dev/doggo/android/android-alarm-registry.kt` | Added | 90 | SharedPreferences-backed scheduled alarms and active notifications; companion lock synchronization; commit writes. |
| `androidApp/src/main/kotlin/dev/doggo/android/doggo-alarm-receiver.kt` | Modified | 98 | Direct-boot check, payload/generation/wipe verification, bounded `goAsync` delivery. |
| `androidApp/src/main/kotlin/dev/doggo/android/reminder-restore-receiver.kt` | Added | 87 | Broadcast receiver for boot, unlock, upgrade, timezone changes; async wipe completion under `goAsync`. |
| `androidApp/src/main/kotlin/dev/doggo/android/reminder-permission-controller.kt` | Added | 106 | Sealed permission states, settings intents, channel preflight, POST_NOTIFICATIONS checks. |
| `androidApp/src/main/kotlin/dev/doggo/android/composite-leave-scheduler-cancel-gate.kt` | Added | 42 | Coordinated teardown: delivery drain -> observer cancel -> sync cancel -> alarm cancel. |
| `androidApp/src/main/kotlin/dev/doggo/android/account-runtime.kt` | Added | 26 | Account-scoped container for Room DB, stores, and coordinators. |
| `androidApp/src/main/kotlin/dev/doggo/android/account-runtime-factory.kt` | Added | 126 | Instantiates isolated runtime components without requiring active network auth. |
| `androidApp/src/main/kotlin/dev/doggo/android/android-account-state-manager.kt` | Added | 92 | Manages `active_account.dat` atomic file swaps and startup pending wipe recovery with `cancelAndAwait`. |
| `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt` | Modified | 136 | Startup wipe ordering, direct-boot runtime guards, lazy singletons, delivery gate draining on close. |
| `androidApp/src/main/kotlin/dev/doggo/android/main-activity.kt` | Modified | 64 | Launcher activity, notification click intent routing, permission refresh. |
| `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt` | Modified | 199 | Handles notification task selection with `pendingTarget` cold-start caching, authentication lifecycle, UI state. |
| `androidApp/build.gradle.kts` | Modified | 189 | Added test logging and coroutines-test dependency; verified `minSdk = 26`. |
| `androidApp/src/test/kotlin/dev/doggo/android/android-notification-adapter-test.kt` | Modified | 113 | Request code determinism, tag formatting, key serialization, capability models. |
| `androidApp/src/test/kotlin/dev/doggo/android/composite-leave-scheduler-cancel-gate-test.kt` | Added | 117 | Direct production gate tests: order, drain timeout, throw. |
| `androidApp/src/test/kotlin/dev/doggo/android/reminder-permission-controller-test.kt` | Added | 33 | Asserts permission string constants and sealed class type hierarchy. |
| `androidApp/src/androidTest/kotlin/dev/doggo/android/background-isolation-test.kt` | Modified | 132 | Instrumented verification of startup wipe clearing alarm registry. |

---

## 8. Validation Commands & Results

1. **Android Unit Tests**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ANDROID_HOME=/home/loidinh/Android/Sdk ./gradlew :androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off`
   - Result: `BUILD SUCCESSFUL in 21s` (45 actionable tasks, 12 unit tests passed, 0 failed).
2. **Android Debug APK Assembly**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ANDROID_HOME=/home/loidinh/Android/Sdk ./gradlew :androidApp:assembleDebug --dependency-verification=off`
   - Result: `BUILD SUCCESSFUL in 2s` (`androidApp-debug.apk` built and validated).
3. **Release Security Diagnostic**:
   - Command: `node scripts/release-security-check.mjs --diagnose-source`
   - Result: `CLEAN` (Only `MainActivity` exported, backup disabled, cleartext prohibited, 0 credentials in source).
4. **Shared KMP JVM Tests**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off`
   - Result: `BUILD SUCCESSFUL in 725ms` (182 tests passing).

---

## 9. Unresolved Questions

1. In multi-user / work profile Android configurations, should `ReminderRestoreReceiver` check `UserManager.isUserUnlocked()` unconditionally, even if the broadcast action is `ACTION_USER_UNLOCKED`?
2. Should recurring reminder notifications accumulate separate status-bar entries per task or collapse into a grouped notification with a summary tag on API 24+?
3. On Android 14+ (API 34+), should user-initiated revocation of `SCHEDULE_EXACT_ALARM` trigger a user-visible in-app banner explaining fallback to best-effort delivery, or remain silent until next task creation?
