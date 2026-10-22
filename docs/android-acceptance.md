# Android Composition, Reminder Alarms & Runtime Acceptance Specification

**Document Version**: 1.5.0
**Last Updated**: 2026-10-21
**Status**: Collaborative task assignees and Room v5 delivered (Phases 01–08). Android unit/package and offline evidence reported; connected-device and interactive UI acceptance NOT RUN. Native acceptance is not passed. Production migration is unauthorized.
**Related Documents**: [Project Overview PDR](./project-overview-pdr.md) · [System Architecture](./system-architecture.md) · [Authentication & Credentials](./authentication-and-credentials.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Design Guidelines](./design-guidelines.md) · [Release Acceptance](./release-acceptance.md) · [Integrated Acceptance Evidence](../plans/261021-1552-tabbyrush-task-assignees/reports/04-integrated-acceptance-evidence.md)

---

## 1. Overview & Architectural Boundaries

The Android app integrates platform lifecycle, reminders, notifications, task surfaces, and account coordination with Room v5 local storage and the current standard Hub sync client. It does not use strict-v1/v2/v3 task-sync routes. Historical Android phase evidence below is dated and must not be treated as a fresh run or connected-device acceptance.

### 1.0 Collaborative Task Assignees & Surface Invariants
1. **Canonical Identity in Intents & Reminders**: Android `AlarmManager` alarms, notification tags, and `PendingIntent` extras transport canonical `TaskRef(ownerUserId, rowId)` rather than bare row IDs.
2. **Admission Re-Authorization**: Widgets (`GlanceAppWidget`), alarm receivers (`TabbyRushAlarmReceiver`), and notification click handlers re-resolve `TaskRef` and current `accessGeneration` at admission and immediately before local mutation or delivery. If authoritative access was lost, delivery is suppressed and alarms are cancelled fail-closed.
3. **Child-Only Privacy**: Assigned child tasks render flat in the Android task list and Glance widget without synthetic parent rows or sibling disclosures.
4. **Revocation Purge & Notification Cancellation**: When access is revoked, active notifications for the task are cancelled, scheduled alarms are cleared from `AndroidAlarmRegistry`, and active widget state is refreshed.
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
- Single scrollable list partitioned into Incomplete tasks and Completed tasks (historically flat `createdAt DESC, id DESC` in Phase 07; superseded by Phase 04 grouped hierarchy projection in §3.8).
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

### 3.8 Grouped Glance Widget & Deadline Presentation Architecture (Phase 04)

Phase 04 updates the native Android Glance launcher widget (`TabbyRushTaskWidget`) to render hierarchical parent-child groups and explicit absolute deadlines directly from shared Phase 03 evaluators without platform-specific hierarchy forks:

1. **Pure `projectWidgetItems` Projection (`task-widget-projection.kt`)**:
   - Consumes `TaskHierarchyProjector.projectHierarchy(section, TodoFilter.ALL)` to produce `WidgetTaskItem` models.
   - `TodoFilter.ALL` preserves whole compact groups intact within the Incomplete section (active parent displays together with both active and completed sibling children). Fully completed groups partition under Completed.
   - Enforces ancestor deletion dominance (suppressing child tasks of locally or remotely deleted parents) and effective parent completion derivation.
2. **Absolute Own Deadlines & Overdue Decoration**:
   - Formats each item's deadline via `TaskDeadlinePresentation.formatDeadline(deadline, effectiveCompleted, nowEpochMs)`.
   - Every row displays its own stored absolute date (`"Due YYYY-MM-DD"`) and, if timed, captured time and IANA zone (`"Due YYYY-MM-DD HH:mm (captured zone)"`). Zero deadline inheritance occurs between parent and child.
   - Reminder rule anchor time (default 09:00 for date-only tasks) never leaks into deadline displays. Tasks lacking deadlines omit the metadata line entirely.
   - Overdue decoration (`• Overdue`) renders in `GlanceTheme.colors.error` with `FontWeight.Bold` supplementing the absolute date rather than replacing it. Completed tasks never render as overdue.
3. **Adaptive Glance Row Layout & Minimum Hit Targets (`task-widget.kt`)**:
   - Replaces fixed-height single-line rows with adaptive Glance Column and Box compositions.
   - Explicit 48×48dp CheckBox container `Box(contentAlignment = Alignment.Center)` accompanied by a minimum 48dp `Spacer` serves as a layout provision intended to meet Android >=48dp touch target guidance; physical touch bounds remain subject to runtime device verification.
   - Navigable title/metadata `Column` declares `.fillMaxHeight()` with `Alignment.CenterVertically` and click action (`createOpenTaskIntent`).
   - Title text allows 1 line (compact) or 2 lines; deadline text configures `maxLines = 3` to accommodate multi-line wrapped deadline and overdue labels on narrow devices, though text clipping prevention remains subject to runtime verification under 200% font scaling.
   - Child rows indent by 12dp (compact) or 20dp (expanded).
4. **Read-Only Automatic Parent Progress & Fenced Actions**:
   - Parents with live children render read-only checkboxes (`onCheckedChange = null`) displaying automatic child completion progress (`"Subtasks: X/Y completed"`) and aggregated accessibility descriptions (`task_widget_parent_automatic_content_desc`), preventing direct group toggle.
   - Standalone roots and leaf children bind interactive callbacks fenced by `SurfaceAccountToken(accountKey, generation)` via `TaskWidgetActionKeys.toggleTaskParameters`. Stale parent completion actions fail closed in shared store admission.
   - Widget Add button invokes `createAddTaskIntent`, creating independent root tasks only (child creation remains strictly main-app-only).
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
| **`projectWidgetItems` (Projection)** | Yes | Yes (Unit: whole compact group preservation, ancestor deletion dominance, raw completion disagreement) | Launcher rendering and responsive scaling held at Gate G2 / Phase 06. |
| **`TabbyRushTaskWidget` (Grouped & Deadlines)** | Yes | Yes (Unit: absolute own deadline formatting, overdue decoration, TalkBack description assembly) | Connected launcher execution and TalkBack audio verification held at Gate G2 / Phase 06. |

### 4.2 Automated Verification Evidence

#### 4.2.1 Historical Phase 07 Automated Verification Evidence

The following are historical Phase 07 results from earlier runs (2026-10-20 and prior), not fresh verification for this update. Their counts are snapshots only. The former strict-v2 rehearsal runner referenced below has been removed; its recorded result is not a current executable check.

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
   - Historical report claim: 8/8 strict-v2 rehearsal checks passed. `strict-v2-cutover-rehearsal.mjs` has been removed; do not run or cite it as current verification.
10. **Note on Unavailable Tools**:
   - `.omp/evcrate/scripts/validate-docs.cjs` is absent from repo environment and was not executed.

#### 4.2.2 Phase 04 Grouped Widget & Deadline Automated Verification Evidence

Recorded from QA validation report (`plans/reports/qa-261020-0053-phase-04-android-widget-deadline-labels.md`):

1. **TabbyRush Shared JVM Tests (`:shared:jvmTest`)**:
   - Command: `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`
   - Outcome: `BUILD SUCCESSFUL` — **341 / 341 PASSED (100% Green)** across 41 test suites.
2. **Android Unit Test Suite (`:androidApp:testDebugUnitTest`)**:
   - Command: `JAVA_HOME=... ./gradlew :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks`
   - Outcome: `BUILD SUCCESSFUL` — **50 / 50 PASSED (100% Green)** across 8 test suites, including 18 `TaskWidgetModelTest` assertions verifying:
     - Pure projection with `TodoFilter.ALL` keeping mixed groups intact under Incomplete.
     - Ancestor deletion dominance suppressing children of deleted roots.
     - Fully completed groups placed under Completed with effective completion.
     - Absolute own deadline formatting with overdue decoration and no anchor leakage.
     - No deadline inheritance between parent and child.
     - Read-only automatic parent progress and absence of toggle callback.
     - Request code determinism, action parameter serialization, and `SurfaceAccountToken` fence isolation.
3. **Historical Strict-v3 Vector Result**:
   - Prior Phase report recorded 11/11 vector checks. `strict-v3-vector-runner.mjs` has been removed; this result is historical strict-protocol evidence, not current standard-sync verification.
4. **Android Instrumentation Compilation (`:androidApp:compileDebugAndroidTestKotlin`)**:
   - Command: `JAVA_HOME=... ./gradlew :androidApp:compileDebugAndroidTestKotlin --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` (build succeeded with 47 executed Gradle tasks; instrumented test sources compile clean). Not counted as test assertions.
5. **Combined Reported Verification Total**: **402 reported passing tests/checks** (`341 + 50 + 11`).
6. **Documentation Tooling & Acceptance Caveats**:
   - Documentation link validation for this phase was executed via host path `/home/loidinh/.omp/agent/evcrate/scripts/validate-docs.cjs`, verifying 273 internal links (0 broken) and 47 code references.
   - *Overdue Refresh Limitation*: Overdue evaluation captures `nowEpochMs = System.currentTimeMillis()` at composition time without automatic midnight/boundary trigger refresh; periodic/boundary wake is tracked as an acceptance gap for Phase 06.
### 4.3 Gate G2 Physical Execution Status & Cutover Integration
- **Current Position**: UI provider approval timed out twice; connected Android device/emulator acceptance was not run. Unit/package and standalone runtime-harness evidence do not establish native launcher, notification shade, accessibility, Doze, reboot, or device acceptance.
- **Production Boundary**: No live production database migration has been authorized. Any future operator conversion requires separate exact-target approval and its own safety gates.
- **Historical Gate Notes**: Earlier Phase 07 reports, phase-hour totals, Gate labels, and claims about dependency checksums are dated snapshots. Retain them as history only; they do not establish current acceptance status.
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
- **Collaborative Assignees Gate (G4)**: Connected Android verification of collaborative task assignees (assignee picker, child-only Glance widget, alarm re-authorization, revocation cancellation) is **NOT RUN / pending**.
- **Pilot & Release Boundary**: The earlier restricted operator pilot statement applies only to announcements scope. It is not approval for collaborative sync release or production migration. Full delivery remains blocked until Gate G4 and interactive UI acceptance are executed and evidenced.
