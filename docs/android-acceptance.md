# Android Composition, Reminder Alarms & Runtime Acceptance Specification

**Document Version**: 1.2.0  
**Phase**: 08 — Android alarms, private notifications and restoration  
**Status**: Logic & Native Adapters Implemented & Unit Verified (Physical Connected Device / Emulator & Doze Mode Acceptance Formally Pending Gate G2 / Phase 11)  
**Related Documents**: [Project Overview PDR](./project-overview-pdr.md) · [System Architecture](./system-architecture.md) · [Authentication & Credentials](./authentication-and-credentials.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Design Guidelines](./design-guidelines.md)

---

## 1. Overview & Architectural Boundaries

Phase 08 delivers the native Android reminder scheduling, private notification delivery, device restoration receivers, and account lifecycle coordination for the `:androidApp` module, building on the shared Room v2 storage, outbox sync coordinator, and shared scheduling ledger from Phases 05–07.

```
                      ┌──────────────────────────────────────────────┐
                      │              DoggoApplication                │
                      │  (Process Singletons & Account State Mgr)    │
                      └──────────────────────┬───────────────────────┘
                                             │
             ┌───────────────────────────────┴───────────────────────────────┐
             ▼                                                               ▼
┌─────────────────────────┐                                     ┌─────────────────────────┐
│     AccountRuntime      │                                     │   Receivers & Adapters  │
│ ┌─────────────────────┐ │                                     │ ┌─────────────────────┐ │
│ │  DoggoDatabase      │ │                                     │ │ DoggoAlarmReceiver  │ │
│ │  RoomAccountStore   │ │                                     │ │ (goAsync / 9s limit)│ │
│ ├─────────────────────┤ │                                     │ ├─────────────────────┤ │
│ │  ReminderDelivery-  │ │                                     │ │ReminderRestore-     │ │
│ │  Gate               │ │                                     │ │Receiver (Boot/Rest.)│ │
│ ├─────────────────────┤ │                                     │ ├─────────────────────┤ │
│ │  ReminderScheduling-│◄┼─────── delivers / reconciles ───────┤ │AndroidNotification- │ │
│ │  Coordinator        │ │                                     │ │Adapter              │ │
│ ├─────────────────────┤ │                                     │ ├─────────────────────┤ │
│ │  CompositeLeave-    │ │                                     │ │AndroidAlarmRegistry │ │
│ │  CancelGate         │ │                                     │ │(doggo_alarms_*)     │ │
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
1. **Single Process Container**: `DoggoApplication` manages process-wide singletons: `HubConfig` (parameterized for debug loopback vs production HTTPS origin), `AndroidKeystoreVault`, `AndroidAccountFiles`, `HubAuthSession`, and `AndroidSyncScheduler`.
2. **Account Runtime Isolation**: `AccountRuntime` encapsulates `DoggoDatabase`, `RoomAccountStore`, `AccountSyncCoordinator`, `ReminderSchedulingCoordinator`, `ReminderDeliveryGate`, `scheduleObserverJob`, and `AccountLeaveProtocol` bound to a specific `AccountKey` and generation counter. Foreground UI (`SessionViewModel`) and background receivers/workers obtain the same runtime instance via `DoggoApplication.getOrCreateAccountRuntime(accountKey)`, protected by `runtimeMutex`.
3. **Direct-Boot & Locked Storage Barrier**: Both `DoggoAlarmReceiver` and `ReminderRestoreReceiver` check `UserManager.isUserUnlocked`. If credential-encrypted storage is locked, alarms and restorations refuse database access fail-closed.
4. **Identity-Only Intent Payloads**: Intents passed through Android OS `AlarmManager` and `PendingIntent` transport only opaque identity extras (`accountKey`, `generation`, `taskId`, `scheduleToken`, `occurrenceIndex`) and URI identifiers. No user text (titles, descriptions) is embedded in alarm or click `PendingIntent` extras or data URIs; task details are loaded freshly from local SQLite under `ReminderDeliveryGate` at fire time, and posted notifications configure `VISIBILITY_PRIVATE` with generic public fallbacks (`VISIBILITY_PUBLIC`).
5. **Fail-Closed Wipe Recovery**: Startup recovery (`completePendingWipesOnStartup` / `completePendingWipesSuspend`) executes synchronously in `Application.onCreate` and prior to any receiver restoration. If `wipe.marker` exists for an account, all scheduled WorkManager sync tasks and AlarmManager alarms are cancelled, active notifications cleared, database files deleted, vault credentials wiped, and marker removed fail-closed.
6. **Teardown Quiescence Pathways**:
   - **Composite Leave Gate (`CompositeLeaveSchedulerCancelGate`)**: Used during `AccountLeaveProtocol.executeLeave()`, enforcing a strict 4-stage ordering: (1) `deliveryGate.closeAndDrain()` (throws `IllegalStateException` on timeout), (2) `scheduleObserverJob.cancelAndJoin()`, (3) `syncCancelGate.cancelAndAwait(accountKey)` (WorkManager), and (4) `reminderCancelGate.cancelAndAwait(accountKey)` (AlarmManager & notifications).
   - **Runtime Teardown (`closeRuntimeInternal`)**: Local runtime replacement path initiates `deliveryGate.closeAndDrain()`, cancels `scheduleObserverJob`, marks `store.isFrozen = true`, and closes the Room database. Note: unlike the composite leave gate, `closeRuntimeInternal()` does not join the observer job or assert drain boolean return.

---

## 2. Security & Manifest Configuration

| Item | Requirement | Implementation / Verification Method | Status |
|---|---|---|---|
| **Exported Components** | Only `MainActivity` is exported (`android:exported="true"`). `DoggoAlarmReceiver` and `ReminderRestoreReceiver` are unexported (`android:exported="false"`). Zero public services or content providers. | Manifest source audit & `scripts/release-security-check.mjs --diagnose-source` | PASS (Verified) |
| **Backup Exclusion** | `android:allowBackup="false"`, `android:fullBackupContent="@xml/backup_rules"`, `android:dataExtractionRules="@xml/data_extraction_rules"`. Excludes databases, shared preferences, and `accounts/` partition. | XML configuration inspection | PASS (Verified) |
| **Cleartext Restrictions** | `android:usesCleartextTraffic="false"` in base manifest. Debug manifest overrides with `network_security_config.xml` strictly allowing `127.0.0.1`, `localhost`, and `10.0.2.2`. Global cleartext and custom CAs are rejected. | XML audit & release security diagnostic | PASS (Verified) |
| **Notification Privacy** | Notification channel `doggo_reminders` configures `lockscreenVisibility = Notification.VISIBILITY_PRIVATE`. Notification builder attaches generic public version (`VISIBILITY_PUBLIC`, text: "Task reminder") via `setPublicVersion()`. Task titles are hidden on locked screens when user configured. | `AndroidNotificationAdapter.kt` source audit | PASS (Configured behavior) |
| **Intent Data URIs** | Explicit intents use unique data URIs (`doggo://alarm/<accountKey>/<generation>/<taskId>/<scheduleToken>/<index>` and `doggo://task/<accountKey>/<taskId>`) for intent filter equality without embedding sensitive text. | `AndroidNotificationIntentFactory.kt` source audit | PASS (Verified) |
| **PendingIntent Flags** | All PendingIntents declare `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`. | `AndroidNotificationIntentFactory.kt` source audit | PASS (Verified) |
| **Release Signing** | Release task fails closed without `DOGGO_KEYSTORE`, `DOGGO_KEY_ALIAS`, `DOGGO_STORE_PASSWORD`, and `DOGGO_KEY_PASSWORD`. Debug builds require no secrets. | Gradle task graph listener | PASS (Verified) |

---

## 3. Alarm Scheduling, Native Notifications & Restoration Architecture

### 3.1 AndroidNotificationAdapter & Channel Setup
- **Channel**: ID `doggo_reminders`, Name `Doggo Task Reminders`, `IMPORTANCE_HIGH`, lights, vibration, and `lockscreenVisibility = VISIBILITY_PRIVATE`.
- **Exact vs Inexact Scheduling**: Evaluates `canScheduleExactAlarms(context)`. If permitted, schedules via `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pendingIntent)`. Falls back gracefully to `setAndAllowWhileIdle` upon missing permission or `SecurityException`.
- **Private / Public Notification Pairing**:
  ```kotlin
  val publicVersion = Notification.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("Doggo")
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
- **Notification ID & Tagging**: Scoped by account tag `doggo_<accountKey>` and deterministic integer ID `(accountKey.value + taskId).hashCode() and 0x7FFFFFFF`.

### 3.2 AndroidAlarmRegistry
- **Persistence**: SharedPreferences file `doggo_alarms_<accountKey>` tracking:
  - `scheduled_alarms`: Set of serialized alarm keys `generation:taskId:scheduleToken:occurrenceIndex`.
  - `active_notifications`: Set of string platform notification IDs.
- **Thread Safety & Durability**: Guarded by companion-level lock `registryLock` and writes with synchronous `commit()`.
- **Limitation**: While writes use `commit()`, calling adapters log failure warnings rather than raising exceptions or performing transactional rollback.

### 3.3 DoggoAlarmReceiver
- **Action**: `dev.doggo.android.ACTION_DELIVER_REMINDER` (explicit intent).
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
- **Click Intent**: Notification `contentIntent` targets `MainActivity` with `ACTION_VIEW_TASK`, data URI `doggo://task/<accountKey>/<taskId>`, and extras `extra_account_key` and `extra_task_id`.
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
  - `ReminderPermissionState.ChannelDisabled`: `doggo_reminders` channel muted/blocked.
- **Intent Factories**: Pre-configures `Settings` intents for app notifications, channel settings, exact alarm permission settings (API 31+), and application details.
- **Limitation**: `MainActivity.onResume()` currently evaluates the state but discards the return value; reactive UI reflection and auto-prompting are deferred to Compose UI integrations.

---

## 4. Implementation, Verification & Acceptance Status Matrix

### 4.1 Component Status Summary

| Component | Implemented | Verified (Automated) | Pending or Limited |
|---|:---:|:---:|---|
| **`DoggoAlarmReceiver`** | Yes | Yes (Unit: payload parsing, extras constants) | Connected device AlarmManager wake & Doze delivery pending Phase 11 / Gate G2. 9s timeout cooperative. |
| **`ReminderRestoreReceiver`** | Yes | Yes (Unit: action set, wipe-first call) | Live OS boot & timezone broadcast reception pending Phase 11 / Gate G2. Action does not detect exact alarm revocation. |
| **`AndroidNotificationAdapter`** | Yes | Yes (Unit: request code determinism, tags, key formatting) | Notification display and visual lockscreen appearance pending Phase 11 / Gate G2. Registry write failures not raised. |
| **`AndroidAlarmRegistry`** | Yes | Yes (Unit & Instrumented: key serialization, startup wipe clearing) | Synchronous `commit()` used, but calling adapter ignores boolean return value. |
| **`CompositeLeaveSchedulerCancelGate`** | Yes | Yes (Unit: 4-stage ordering, timeout exception) | Production gate verified; `closeRuntimeInternal()` follows separate unjoined teardown. |
| **`ReminderPermissionController`** | Yes | Yes (Unit: sealed class hierarchy, permission constants) | `MainActivity.onResume()` discards evaluation result; UI prompt deferred to Phase 10. |
| **Cold-Start Navigation** | Yes | Yes (Unit / Code review: `pendingTarget` queueing) | Live activity transition testing pending connected device testbed. |

### 4.2 Automated Verification Evidence

The following test suites, security checks, and build outputs were executed and verified (276 test assertions + 6 diagnostic source security checks = 282 total automated checks passing with 0 failures; debug APK assembly verified separately as a successful build artifact):

1. **Android Unit Test Suite (`:androidApp:testDebugUnitTest`)**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ANDROID_HOME=/home/loidinh/Android/Sdk ./gradlew :androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` (12/12 unit tests passed, 0 failed).
   - Covered Classes:
     - `AndroidNotificationAdapterTest`: Request code collision resistance, alarm key formatting/parsing, notification tag scoping.
     - `CompositeLeaveSchedulerCancelGateTest`: 4-stage teardown execution order, timeout on stuck delivery drain, exception propagation.
     - `ReminderPermissionControllerTest`: Permission string constants (`POST_NOTIFICATIONS`) and sealed state type completeness.
2. **Android Debug APK Compilation (`:androidApp:assembleDebug`)**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ANDROID_HOME=/home/loidinh/Android/Sdk ./gradlew :androidApp:assembleDebug --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` (Generated `androidApp/build/outputs/apk/debug/androidApp-debug.apk`).
3. **Diagnostic Source Security Audit**:
   - Command: `node scripts/release-security-check.mjs --diagnose-source`
   - Outcome: `CLEAN` (6/6 checks PASS: prohibits cleartext, disables backup, asserts only `MainActivity` exported, backup XMLs present, HTTPS release origin, 0 hardcoded secrets).
4. **Shared KMP JVM Test Suite (`:shared:jvmTest`)**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` (182/182 tests passed, including scheduling calculator, persisted projector, delivery gate, and teardown barrier SQLite tests).
5. **Desktop JVM Test Suite (`:desktopApp:test`)**:
   - Command: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :desktopApp:test --dependency-verification=off`
   - Outcome: `BUILD SUCCESSFUL` (32/32 tests passed across 6 classes).
6. **Cryptographic & Protocol Verification**:
   - `node scripts/strict-v2-vector-runner.mjs`: 23/23 checks PASS.
   - `node scripts/hub-live-smoke.mjs --fixture-test`: 18/18 offline smoke-fixture self-checks PASS.
   - `node scripts/hub-strict-upgrade.test.mjs`: 9/9 CLI test suites PASS.
7. **Note on Unavailable Tools**:
   - `.omp/evcrate/scripts/validate-docs.cjs` is absent from repo environment and was not executed.

### 4.3 Gate G2 Physical Execution Status
- **Current Position**: Gate G2 native Android logic, adapters, receivers, intent factories, and cancel gates are fully implemented and verified via unit tests and debug APK assembly.
- **Formal Hold**: Physical connected device/emulator execution, Doze mode battery optimization bypass (`setExactAndAllowWhileIdle`), real lock-screen private notification masking, and system reboot restoration remain formally pending Gate G2 acceptance testing in **Phase 11 (Adversarial Integration and Platform Acceptance)**. Deferral to Phase 11 does not constitute technical acceptance of Gate G2.

---

## 5. UI Accessibility & Acceptance Checklist

| Check | Specification | Evidence / Tracking |
|---|---|---|
| **Touch Targets** | All interactive controls (checkboxes, buttons, filter chips) meet >= 48dp touch target boundaries. | Verified in `TodoItem`, `TodoFilterChips` |
| **Contrast** | Light theme tokens exceed WCAG 2.1 AA (4.5:1 text, 3.0:1 controls). | Verified in Phase 06 design spec |
| **Typography** | Delegates to platform `FontFamily.SansSerif` for system font scaling up to 200%. | Verified in `DoggoTypography` |
| **Screen States** | 7 core UI states documented: Empty, Populated, Completed, Editor, Error, Conflict, Access-Pending. | High-fidelity design baseline renders in `docs/evidence/`; live device framebuffer capture scheduled for Phase 11. |
