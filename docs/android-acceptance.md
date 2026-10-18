# Android Composition, Reminder Alarms & Runtime Acceptance Specification

**Document Version**: 1.3.0
**Phase**: 12 — Coordinated Cutover, Recovery Runbook & Release Documentation
**Status**: Logic & Native Adapters Implemented & Unit Verified (Physical Connected Device / Emulator & Doze Mode Acceptance Formally Held Pending Gate G2 / Hardware Testbed)
**Related Documents**: [Project Overview PDR](./project-overview-pdr.md) · [System Architecture](./system-architecture.md) · [Authentication & Credentials](./authentication-and-credentials.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Design Guidelines](./design-guidelines.md)

---

## 1. Overview & Architectural Boundaries

Phase 08 delivers the native Android reminder scheduling, private notification delivery, device restoration receivers, and account lifecycle coordination for the `:androidApp` module, building on the shared Room v2 storage, outbox sync coordinator, and shared scheduling ledger from Phases 05–07.

```
                      ┌──────────────────────────────────────────────┐
                      │              TabbyRushApplication                │
                      │  (Process Singletons & Account State Mgr)    │
                      └──────────────────────┬───────────────────────┘
                                             │
             ┌───────────────────────────────┴───────────────────────────────┐
             ▼                                                               ▼
┌─────────────────────────┐                                     ┌─────────────────────────┐
│     AccountRuntime      │                                     │   Receivers & Adapters  │
│ ┌─────────────────────┐ │                                     │ ┌─────────────────────┐ │
│ │  TabbyRushDatabase      │ │                                     │ │ TabbyRushAlarmReceiver  │ │
│ │  RoomAccountStore   │ │                                     │ │ (goAsync / 9s limit)│ │
│ ├─────────────────────┤ │                                     │ ├─────────────────────┤ │
│ │  ReminderDelivery-  │ │                                     │ │ReminderRestore-     │ │
│ │  Gate               │ │                                     │ │Receiver (Boot/Rest.)│ │
│ ├─────────────────────┤ │                                     │ ├─────────────────────┤ │
│ │  ReminderScheduling-│◄┼─────── delivers / reconciles ───────┤ │AndroidNotification- │ │
│ │  Coordinator        │ │                                     │ │Adapter              │ │
│ ├─────────────────────┤ │                                     │ ├─────────────────────┤ │
│ │  CompositeLeave-    │ │                                     │ │AndroidAlarmRegistry │ │
│ │  CancelGate         │ │                                     │ │(tabbyrush_alarms_*)     │ │
│ └─────────────────────┘ │                                     │ └─────────────────────┘ │
└────────────┬────────────┘                                     └─────────────────────────┘
             │
             ▼
┌─────────────────────────┐
│      MainActivity       │
│  (Single Exported UI)   │
│  SessionViewModel       │
│  pendingTarget Nav      │
└─────────────────────────┘
```

### 1.1 Process & State Invariants
1. **Single Process Container**: `TabbyRushApplication` manages process-wide singletons: `HubConfig` (parameterized for debug loopback vs production HTTPS origin), `AndroidKeystoreVault`, `AndroidAccountFiles`, `HubAuthSession`, and `AndroidSyncScheduler`.
2. **Account Runtime Isolation**: `AccountRuntime` encapsulates `TabbyRushDatabase`, `RoomAccountStore`, `AccountSyncCoordinator`, `ReminderSchedulingCoordinator`, `ReminderDeliveryGate`, `scheduleObserverJob`, and `AccountLeaveProtocol` bound to a specific `AccountKey` and generation counter. Foreground UI (`SessionViewModel`) and background receivers/workers obtain the same runtime instance via `TabbyRushApplication.getOrCreateAccountRuntime(accountKey)`, protected by `runtimeMutex`.
3. **Direct-Boot & Locked Storage Barrier**: Both `TabbyRushAlarmReceiver` and `ReminderRestoreReceiver` check `UserManager.isUserUnlocked`. If credential-encrypted storage is locked, alarms and restorations refuse database access fail-closed.
4. **Identity-Only Intent Payloads**: Intents passed through Android OS `AlarmManager` and `PendingIntent` transport only opaque identity extras (`accountKey`, `generation`, `taskId`, `scheduleToken`, `occurrenceIndex`) and URI identifiers. No user text (titles, descriptions) is embedded in alarm or click `PendingIntent` extras or data URIs; task details are loaded freshly from local SQLite under `ReminderDeliveryGate` at fire time, and posted notifications configure `VISIBILITY_PRIVATE` with generic public fallbacks (`VISIBILITY_PUBLIC`).
5. **Fail-Closed Wipe Recovery**: Startup recovery (`completePendingWipesOnStartup` / `completePendingWipesSuspend`) executes synchronously in `Application.onCreate` and prior to any receiver restoration. If `wipe.marker` exists for an account, all scheduled WorkManager sync tasks and AlarmManager alarms are cancelled, active notifications cleared, database files deleted, vault credentials wiped, and marker removed fail-closed.
6. **Teardown Quiescence Pathways**:
   - **Composite Leave Gate (`CompositeLeaveSchedulerCancelGate`)**: Used during `AccountLeaveProtocol.executeLeave()`, enforcing a strict 4-stage ordering: (1) `deliveryGate.closeAndDrain()` (throws `IllegalStateException` on timeout), (2) `scheduleObserverJob.cancelAndJoin()`, (3) `syncCancelGate.cancelAndAwait(accountKey)` (WorkManager), and (4) `reminderCancelGate.cancelAndAwait(accountKey)` (AlarmManager & notifications).
   - **Runtime Teardown (`closeRuntimeInternal`)**: On the success path, the local runtime replacement path drains delivery, cancels the schedule observer, freezes the store, and closes Room. On drain timeout, it catches/logs the exception and returns before observer cancellation or Room closure; `closeActiveRuntime()` nevertheless clears `activeRuntime`.

---

## 2. Security & Manifest Configuration

| Item | Requirement | Implementation / Verification Method | Status |
|---|---|---|---|
| **Exported Components** | Only `MainActivity` and canonical `TabbyRushTaskWidgetReceiver` (with verified provider meta-data and `APPWIDGET_UPDATE` action) are exported. All other receivers (`TabbyRushAlarmReceiver`, `ReminderRestoreReceiver`, `TaskSurfaceActionReceiver`, `TaskSurfaceDismissReceiver`, `TaskSurfaceTimeReceiver`) are private (`android:exported="false"`). Zero public services or content providers. | Manifest source audit, `scripts/release-security-check.test.mjs` (12/12 PASS), and `scripts/release-security-check.mjs --diagnose-source` | PASS (Verified) |
| **Backup Exclusion** | `android:allowBackup="false"`, `android:fullBackupContent="@xml/backup_rules"`, `android:dataExtractionRules="@xml/data_extraction_rules"`. Excludes databases, shared preferences, and `accounts/` partition. | XML configuration inspection | PASS (Verified) |
| **Cleartext Restrictions** | `android:usesCleartextTraffic="false"` in base manifest. Debug manifest overrides with `network_security_config.xml` strictly allowing `127.0.0.1`, `localhost`, and `10.0.2.2`. Global cleartext and custom CAs are rejected. | XML audit & release security diagnostic | PASS (Verified) |
| **Notification Privacy** | Notification channel `tabbyrush_reminders` configures `lockscreenVisibility = Notification.VISIBILITY_PRIVATE`. Notification builder attaches generic public version (`VISIBILITY_PUBLIC`, text: "Task reminder") via `setPublicVersion()`. Task titles are hidden on locked screens when user configured. | `AndroidNotificationAdapter.kt` source audit | PASS (Configured behavior) |
| **Intent Data URIs** | Explicit intents use unique data URIs (`tabbyrush://alarm/<accountKey>/<generation>/<taskId>/<scheduleToken>/<index>` and `tabbyrush://task/<accountKey>/<taskId>`) for intent filter equality without embedding sensitive text. | `AndroidNotificationIntentFactory.kt` source audit | PASS (Verified) |
| **PendingIntent Flags** | All PendingIntents declare `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`. | `AndroidNotificationIntentFactory.kt` source audit | PASS (Verified) |
| **Release Signing** | Release task fails closed without `TABBYRUSH_KEYSTORE`, `TABBYRUSH_KEY_ALIAS`, `TABBYRUSH_STORE_PASSWORD`, and `TABBYRUSH_KEY_PASSWORD`. Debug builds require no secrets. | Gradle task graph listener | PASS (Verified) |

---

## 3. Alarm Scheduling, Native Notifications & Restoration Architecture

### 3.1 AndroidNotificationAdapter & Channel Setup
- **Channel**: ID `tabbyrush_reminders`, Name `TabbyRush Task Reminders`, `IMPORTANCE_HIGH`, lights, vibration, and `lockscreenVisibility = VISIBILITY_PRIVATE`.
- **Exact vs Inexact Scheduling**: Evaluates `canScheduleExactAlarms(context)`. If permitted, schedules via `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pendingIntent)`. Falls back gracefully to `setAndAllowWhileIdle` upon missing permission or `SecurityException`.
- **Private / Public Notification Pairing**:
  ```kotlin
  val publicVersion = Notification.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("TabbyRush")
      .setContentText("Task reminder")
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .build()

  val notification = Notification.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title)
      .setContentText("Task reminder: $title")
      .setVisibility(Notification.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion)
      .setContentIntent(clickPendingIntent)
      .setAutoCancel(true)
      .build()
  ```
- **Notification ID & Tagging**: Scoped by account tag `tabbyrush_<accountKey>` and deterministic integer ID `(accountKey.value + taskId).hashCode() and 0x7FFFFFFF`.

### 3.2 AndroidAlarmRegistry
- **Persistence**: SharedPreferences file `tabbyrush_alarms_<accountKey>` tracking:
  - `scheduled_alarms`: Set of serialized alarm keys `generation:taskId:scheduleToken:occurrenceIndex`.
  - `active_notifications`: Set of string platform notification IDs.
- **Thread Safety & Durability**: Guarded by companion-level lock `registryLock` and writes with synchronous `commit()`.
- **Limitation**: While writes use `commit()`, calling adapters log failure warnings rather than raising exceptions or performing transactional rollback.

### 3.3 TabbyRushAlarmReceiver
- **Action**: `dev.tabbyrush.android.ACTION_DELIVER_REMINDER` (explicit intent).
- **Security & Lifecycle Checks**:
  1. Verifies `UserManager.isUserUnlocked`.
  2. Parses identity-only extras: `extra_account_key`, `extra_generation`, `extra_task_id`, `extra_schedule_token`, `extra_occurrence_index`. Drops malformed payloads.
  3. Checks `app.accountFiles.hasPendingWipe(accountKey)`. Drops if pending wipe.
  4. Checks `app.accountFiles.readGeneration(accountKey) == generation`. Drops stale generation triggers.
  5. Checks `app.readActiveAccountSync()?.first == accountKey`. Drops background alarms for inactive accounts.
- **Bounded Delivery**: Uses `goAsync()` with coroutine launch on `app.applicationScope` bounded by `withTimeoutOrNull(9_000L)` to guarantee completion within Android's 10-second broadcast receiver ANR window. Invokes `reminderCoordinator.deliverReminder(...)` and `reconcile()`, ensuring `pendingResult.finish()` runs in `finally`.
- **Limitation**: Coroutine timeout is cooperative; it does not forcibly interrupt blocking I/O or platform operations.

### 3.4 ReminderRestoreReceiver
- **Actions**: `ACTION_BOOT_COMPLETED`, `ACTION_USER_UNLOCKED`, `ACTION_MY_PACKAGE_REPLACED`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, and `android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
- **Wipe-First Restoration**:
  1. Re-asserts `UserManager.isUserUnlocked` (defers non-unlock actions if storage locked).
  2. Executes `app.completePendingWipesSuspend()` before accessing any database or account state.
  3. Identifies active account from `readActiveAccountSync()` (refuses fallback to arbitrary accounts).
  4. Invokes `runtime.reminderCoordinator.reconcile()` within 9-second bounded `goAsync()` scope.
- **Limitation**: `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` is broadcast by the Android OS upon permission grants, not revocations.

### 3.5 Cold-Start Deep Linking & Navigation
- **Click Intent**: Notification `contentIntent` targets `MainActivity` with `ACTION_VIEW_TASK`, data URI `tabbyrush://task/<accountKey>/<taskId>`, and extras `extra_account_key` and `extra_task_id`.
- **SessionViewModel Routing**:
  - `MainActivity.onCreate()` and `onNewIntent()` invoke `viewModel.selectTaskFromNotification(accountKey, taskId)`.
  - If auth session is hydrating or `isTodosLoaded` is false (cold start), target is stored in `pendingTarget = Pair(accountKey, taskId)`.
  - Once todos load, `dispatchTaskSelection` verifies `currentKey == accountKey`, checks `!hasPendingWipe`, locates task in `allTodos`, and opens the editor state.
  - If user is logged out, `pendingTarget` is cleared.

### 3.6 ReminderPermissionController
- **Sealed State Hierarchy**:
  - `ReminderPermissionState.Exact`: Exact alarms and notifications enabled.
  - `ReminderPermissionState.BestEffort`: Inexact delivery active (`SCHEDULE_EXACT_ALARM` denied/revoked).
  - `ReminderPermissionState.NotificationDenied`: Notifications disabled globally or app-level.
  - `ReminderPermissionState.ChannelDisabled`: `tabbyrush_reminders` channel muted/blocked.
- **Intent Factories**: Pre-configures `Settings` intents for app notifications, channel settings, exact alarm permission settings (API 31+), and application details.
- **Limitation**: `MainActivity.onResume()` currently evaluates the state but discards the return value; reactive UI reflection and auto-prompting are deferred to Compose UI integrations.


### 3.7 Sticky Task Surfaces & Glance Widget Architecture (Phase 07)
- **Glance Task Widget (`TabbyRushTaskWidget`)**:
  - Implemented using Jetpack Glance 1.2.0-beta01 with `SizeMode.Responsive` supporting 120×120dp (2×2), 240×120dp (3×2), 240×240dp (3×3), and 320×360dp (4×4+) layouts.
  - Presentation state `WidgetDisplayState`: non-ready states (`Loading`, `SignedOut`, `Locked`, `Unavailable`) display generic fallback copy with zero private task titles exposed.
  - Single scrollable list partitioned into Incomplete tasks and Completed tasks in stable `createdAt DESC, id DESC` order.
  - Interactive atomic completion toggle routes through `TaskSurfaceActionReceiver` and `SessionViewModel.setCompletedLocally()`; quick add dialog routes to `MainActivity` with `ACTION_ADD_TASK`.
- **Quiet Task Surface Deadline Notification (`TaskSurfaceNotificationCoordinator`)**:
  - Channel `tabbyrush_task_surfaces`: `IMPORTANCE_LOW`, lights/sound/vibration disabled, `lockscreenVisibility = Notification.VISIBILITY_PRIVATE`.
  - Selects the single next overdue or upcoming deadline across active tasks, resolving ties deterministically by `taskId ASC`. Includes saved conflicted deadlines; independent of reminder alert preferences.
  - Attaches `setPublicVersion()` generic public fallback ("Task deadline reminder", text: "Unlock device to view task title and details") hiding sensitive titles on locked screens.
  - API 34+ user swipe dismissal records durable suppression key (`TaskSurfacePreferences`), preventing re-posting of the same task and deadline across background sync, clock drift, or process restarts until selection or deadline changes.
- **Access Gating & Token Fencing (`AndroidTaskSurfaceAccess`)**:
  - Enforces bounded 4-second cold-start wait (`awaitReadiness(timeoutMs = 4000L)`).
  - Admitted calls verify `SurfaceGateState.OPEN` and match current `SurfaceAccountToken`.
  - Non-current tokens and negative generations are rejected fail-closed; arbitrary accounts cannot be opened on demand.
  - Publication to OS surfaces is strictly serialized under a dedicated `publicationMutex`, transitioning surfaces to non-Ready prior to account teardown.
- **Exhaustive Teardown & Wipe Clearing**:
  - `CompositeLeaveSchedulerCancelGate` cancels surface observers, boundary wake, active notifications (`cancelForAccount`), and clears all installed launcher widget IDs (`TaskWidgetUpdater.clearAllInstances`).
  - Startup wipe recovery blanks all external surfaces before removing wipe markers.
- **Disclosed Safety Findings & Operational Risks (Review Cycle 2)**:
  1. *Delivery Drain Timeout*: If `deliveryGate.closeAndDrain()` times out in `TabbyRushApplication.closeRuntimeInternal()`, the method catches and logs the exception, then returns before observer-job cancellation and Room closure. `closeActiveRuntime()` nevertheless clears `activeRuntime`, leaving coroutine jobs running and Room unclosed. This remains an open safety finding.
  2. *Widget Cleanup Error Swallowing*: RemoteViews blanking and Glance session closure catch `Throwable` non-fatally during leave protocol, risking ghost widget state on launcher if AppWidgetManager fails.
  3. *Glance Process Death & Provenance Holds*: Launcher session survival across OS process death and Glance artifact provenance remain physical execution holds.
---

## 4. Implementation, Verification & Acceptance Status Matrix

### 4.1 Component Status Summary

| Component | Implemented | Verified (Automated) | Pending or Limited |
|---|:---:|:---:|---|
| **`TabbyRushAlarmReceiver`** | Yes | Yes (Unit: payload parsing, extras constants) | Connected device AlarmManager wake & Doze delivery pending Phase 11 / Gate G2. 9s timeout cooperative. |
| **`ReminderRestoreReceiver`** | Yes | Yes (Unit: action set, wipe-first call) | Live OS boot & timezone broadcast reception pending Phase 11 / Gate G2. Action does not detect exact alarm revocation. |
| **`AndroidNotificationAdapter`** | Yes | Yes (Unit: request code determinism, tags, key formatting) | Notification display and visual lockscreen appearance pending Phase 11 / Gate G2. Registry write failures not raised. |
| **`AndroidAlarmRegistry`** | Yes | Yes (Unit & Instrumented: key serialization, startup wipe clearing) | Synchronous `commit()` used, but calling adapter ignores boolean return value. |
| **`CompositeLeaveSchedulerCancelGate`** | Yes | Yes (Unit: 4-stage ordering, timeout exception) | Production gate verified; `closeRuntimeInternal()` follows separate unjoined teardown. |
| **`ReminderPermissionController`** | Yes | Yes (Unit: sealed class hierarchy, permission constants) | `MainActivity.onResume()` discards evaluation result; UI prompt deferred to Phase 10. |
| **Cold-Start Navigation** | Yes | Yes (Unit / Code review: `pendingTarget` queueing) | Live activity transition testing pending connected device testbed. |

### 4.2 Automated Verification Evidence

The following are recorded Phase 07 results, not fresh runs performed for this documentation update. The Phase 07 aggregate is 364 Gradle tests, 12 Node security auditor tests, 6 source diagnostics, 18 Hub fixture checks, and 8 strict-v2 rehearsal checks; build outputs are recorded separately.

1. **Android Unit Test Suite (`:androidApp:testDebugUnitTest`)**:
   - Command: `./gradlew.bat :androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` — **34 / 34 PASSED (100% Green)** across 6 test classes:
     - `AndroidNotificationAdapterTest` (6/6): Request code collision resistance, alarm key formatting/parsing, notification tag scoping.
     - `BuildConfigTest` (1/1): Verified debug variant configuration.
     - `CompositeLeaveSchedulerCancelGateTest` (4/4): 4-stage teardown execution order, timeout on stuck delivery drain, exception propagation, and observer job cancellation.
     - `ReminderPermissionControllerTest` (2/2): Permission string constants (`POST_NOTIFICATIONS`) and sealed state type completeness.
     - `TaskSurfaceNotificationTest` (10/10): Oldest-overdue selection, date-only boundaries, tie breaking (`taskId ASC`), conflict display, and swipe dismissal suppression.
     - `TaskWidgetModelTest` (11/11): Unfiltered task partitioning, stable `createdAt DESC, id DESC` ordering, empty states, and zero title leakage in non-ready states.
2. **Security Auditor Suite (`scripts/release-security-check.test.mjs`)**:
   - Command: `node --test scripts/release-security-check.test.mjs`
   - Outcome: **12 / 12 PASSED (100% Green)** verifying canonical exported policy for `MainActivity` and `TabbyRushTaskWidgetReceiver`, while rejecting foreign lookalikes, exported services, content providers, and unauthorized filters.
3. **Android Debug APK Compilation (`:androidApp:assembleDebug`)**:
   - Command: `./gradlew.bat :androidApp:assembleDebug --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` (Generated `androidApp/build/outputs/apk/debug/androidApp-debug.apk` with bundled SQLite native driver).
4. **Root-Level Strict Dependency Verification**:
   - Command: `./gradlew.bat :shared:jvmTest --dependency-verification=strict`
   - Outcome: `BUILD FAILED` (149 unmatched classpath checksums in `gradle/verification-metadata.xml`). Retained as an external supply-chain hold; builds executed with `--dependency-verification=off`.
5. **Visual Evidence Baselines (`docs/evidence/`)**:
   - `docs/evidence/sticky-surfaces-android-widget.png`: Visual design baseline/design mockup of the responsive Glance launcher widget; not a runtime device capture.
   - `docs/evidence/sticky-surfaces-android-notification.png`: Visual design baseline/design mockup of the notification shade and generic keyguard public view; not a runtime OS capture.
   - Summary Report: Detailed traceability documented in `docs/evidence/sticky-surfaces-android-summary.md`.
6. **Diagnostic Source Security Audit**:
   - Command: `node scripts/release-security-check.mjs --diagnose-source`
   - Outcome: `CLEAN` (6/6 checks PASS: prohibits cleartext, disables backup, asserts only `MainActivity` and `TabbyRushTaskWidgetReceiver` exported, backup XMLs present, HTTPS release origin, 0 hardcoded secrets).
7. **Shared KMP JVM Test Suite (`:shared:jvmTest`)**:
   - Command: `./gradlew.bat :shared:jvmTest --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` (231/231 tests passed, including scheduling calculator, persisted projector, delivery gate, and teardown barrier SQLite tests).
8. **Desktop JVM Test Suite (`:desktopApp:test`)**:
   - Command: `./gradlew.bat :desktopApp:test --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` (99/99 tests passed across 13 classes).
9. **Cryptographic & Protocol Verification**:
   - `node scripts/hub-live-smoke.mjs --fixture-test`: 18/18 offline smoke-fixture self-checks PASS.
   - `node scripts/strict-v2-cutover-rehearsal.mjs --self-test`: 8/8 in-process tooling rehearsal checks PASS.
10. **Note on Unavailable Tools**:
   - `.omp/evcrate/scripts/validate-docs.cjs` is absent from repo environment and was not executed.

### 4.3 Gate G2 Physical Execution Status & Cutover Integration
- **Current Position**: Android implementations and automated checks are recorded, including unit tests and debug APK assembly. These do not establish native launcher, shade, or device acceptance.
- **Formal Hold**: Physical connected device/emulator execution, launcher interaction, Doze behavior, real lock-screen privacy, and reboot restoration remain held at Gate G2. The Android evidence PNGs are visual design baselines, not runtime captures.
- **Phase 07 Record**: Credited 6h / 12h; overall progress 118h / 132h (89.4%). See the [Android sticky-surface evidence summary](./evidence/sticky-surfaces-android-summary.md) and [Phase 07 evidence report](../plans/261016-1409-sticky-task-surfaces/reports/phase-07-acceptance-evidence.md). Phase 08 Linux acceptance and production release gates G3, G6, and G7 remain pending; the 149 unmatched dependency checksums and separate Glance provenance hold remain open.
---

## 5. UI Accessibility & Acceptance Checklist

| Check | Specification | Evidence / Tracking |
|---|---|---|
| **Touch Targets** | All interactive controls (checkboxes, buttons, filter chips) meet >= 48dp touch target boundaries. | Verified in `TodoItem`, `TodoFilterChips` |
| **Contrast** | Light theme tokens exceed WCAG 2.1 AA (4.5:1 text, 3.0:1 controls). | Verified in Phase 06 design spec |
| **Typography** | Delegates to platform `FontFamily.SansSerif` for system font scaling up to 200%. | Verified in `TabbyRushTypography` |
| **Screen States** | 7 core UI states documented: Empty, Populated, Completed, Editor, Error, Conflict, Access-Pending. | Design mockups are in `docs/evidence/`; live device evidence remains pending Gate G2. |

---

## 6. Server Admin Announcements Android Acceptance (Phase 12)

### 6.1 Architecture & Implementation Status
- **Module Integration**: `:androidApp` implements `RemoteNotificationManager` and `RemoteInstallCoordinator` for Firebase Cloud Messaging (HTTP v1) and install lifecycle fencing.
- **Generic Wire Payload**: The FCM payload contains only fixed generic copy (`title: "TabbyRush"`, `body: "You have a new announcement."`) and data keys (`type: "admin_announcement"`, `appId: "tabby-rush"`, `messageId: "<UUID>"`). Zero user IDs, private titles, bodies, or deep links enter the wire.
- **Display Channel & Tagging**:
  - Background: Auto-rendered by Firebase Messaging SDK.
  - Foreground: Posted via local `NotificationManagerCompat` on channel `tabbyrush.remote.announcements` (default importance) with tag `remote:<messageId>` and notification ID 0.
  - No collision with task deadline reminders (`tabbyrush_task_surfaces`).
- **Tap Navigation & Authorization**:
  - Cold start (`onCreate`) and warm start (`onNewIntent`) parse exact intent extras, consuming the message ID once.
  - Intent routes to `RemoteInboxController.onNativeOpen(messageId, generation)`. The controller reauthorizes against current active user grant before opening detail view.
  - If the user is signed out or switched to another account, the app shows safe login/inbox; old content is never displayed.
- **Merged Manifest Security Audit**:
  - `FirebaseMessagingService` is private (`android:exported="false"`) with action `com.google.firebase.MESSAGING_EVENT`.
  - `FirebaseInitProvider` is private (`android:exported="false"`) with authority `dev.tabbyrush.android.firebaseinitprovider`.
  - `FirebaseInstanceIdReceiver` requires `com.google.android.c2dm.permission.SEND` and action `com.google.android.c2dm.intent.RECEIVE`.
  - `AppMeasurementReceiver` is forbidden (no Analytics).

### 6.2 Acceptance & Pilot Gate Status
- **Automated Checks**: Smoke runner self-test passes 21/21 assertions (`scripts/admin-announcements-smoke.mjs --self-test`).
- **Hardware Hold**: Physical connected Android API 33+ device verification (actual notification shade capture and tap navigation) remains held at Gate G2.
- **Pilot Readiness**: Software runtime and security manifest invariants are validated; restricted operator pilot approved for Android and Linux, while Windows desktop remains held at Gate G5.
