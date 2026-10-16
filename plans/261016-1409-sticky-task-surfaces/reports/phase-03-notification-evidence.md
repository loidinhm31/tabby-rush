# Phase 03 Android Quiet Notification and Settings Leaf Evidence Report

**Status:** DELIVERED / READY FOR PHASE 05 INTEGRATION (CENTRAL VERIFICATION PENDING)
**Date:** 2026-10-16
**Author:** Phase 03 Android Notification Leaf Owner (Group B)

---

## 1. Authored Production Components & Specifications

### 1.1 Preferences & Versioned Codec (`task-surface-preferences.kt`)
- **Account Isolation**: SharedPreferences partitioned per account (`doggo_task_surface_<accountKey>`), completely decoupled from Room and remote sync.
- **Durable State Contract**: `TaskSurfacePreferenceState(enabled, suppressed, postedKey, postedReceipt, hasCorruptSuppression)`. Default opt-in is `false`.
- **Versioned Non-Leaking Codec**: `DeadlineSelectionKeyCodec` uses a versioned (`v1:`) URL-encoded parameter serialization that captures every `TaskDeadline` field (`date`, `time`, `zone`, `offsetSeconds`, `reminderAnchorTime`, `anchorEpochMs`, `overdueEpochMs`) along with account token and task ID, while strictly omitting user task titles to prevent credential/data leakage. Rejects malformed or corrupted payloads and flags conservative suppression rather than silently reviving dismissed tasks.
- **Fail-Closed Synchronous Persistence**: All mutations execute off the main thread under a shared synchronization lock and enforce synchronous `commit()` writes. If a commit fails, an exception is thrown and the coordinator aborts without posting.
- **Suppression Invariants**:
  - `setEnabled(false)` and deliberate `false -> true` re-enables clear suppression and active post receipts.
  - `reconcileSelection` preserves suppression if the selected key is identical; clears suppression and invalidates active receipts if the selection changes or disappears (`key == null`).
  - `suppressIfCurrent` requires the incoming delete receipt to match the active `postedReceipt` and `postedKey` before persisting suppression.
  - `invalidatePost` wipes active post metadata, neutralizing delayed delete callbacks from superseded instances.
  - `clear(accountKey)` removes all preferences and deletes the file on Android N+.

### 1.2 Quiet Notification Coordinator (`task-surface-notification.kt`)
- **Dedicated Quiet Channel**: `doggo_task_surface_v1` initialized with `NotificationManager.IMPORTANCE_LOW`, lights disabled, vibration disabled, null sound, badge disabled, and `VISIBILITY_PRIVATE`.
- **Shared Coordinator Serialization**: Entire notification lifecycle (`refresh`, `dismiss`, and `cancelForAccount`) is guarded by a companion-level `Mutex`, preventing race conditions between concurrent refreshes, user dismissals, and account cancellations. Dismissal calls route directly through `coordinator.dismiss(key, receipt)`.
- **Stale Token Fencing**: Validates current host readiness (`host.access.readiness.value is SurfaceReadiness.Ready(token)`) before any destructive side effects or preference mutations. If publication admission fails (`!published`), invalidates only local post receipt without cancelling global notification ID (which another token may have published).
- **Stable Post Receipt**: Reuses active post receipt across identical-selection refreshes, preventing race conditions where a routine refresh replaces the receipt of a visible notification before the user swipes it away.
- **Deadline Selection**: Evaluates `selectNextDeadline(snapshot.schedules, nowEpochMs)` (includes incomplete deadlines and unresolved-conflict projections with `" (Sync conflict)"` indication; independent of reminder rule enablement; excludes drafts).
- **Two-Tier Privacy**:
  - Private notification: displays actual task title, conflict indicator, formatted due/overdue status, direct Activity launch intent, Complete broadcast intent, and DeleteIntent.
  - Generic public notification (`setPublicVersion`): displays generic `"Next task deadline"` title and `"Open Doggo to view"` text on secure lock screens, disclosing zero task text.
- **Pre-Persisted Receipt & Token Gate**: Generates a random UUID receipt and persists it via `recordPost` **before** calling `NotificationManager.notify()`. Post operation is admitted through `host.access.publishIfCurrent(token)`.
- **Boundary Wake Scheduling**: If the selected deadline has a future overdue boundary, schedules inexact WorkManager wake via `TaskSurfaceRefreshWorker`; cancels wake if already overdue or selection is absent.
- **Account Cancellation**: `cancelForAccount(accountKey)` cancels scheduled boundary work, invalidates receipts, removes OS notifications if posted by this account, and wipes account preferences.

### 1.3 Immutable Identity-Bound Intents (`task-surface-intents.kt`)
- **Flags**: All PendingIntents enforce `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`.
- **Filter Equality & Escaping**: Explicit data URIs constructed with `Uri.Builder().appendPath(...)` to safely encode delimiter-bearing task IDs and receipts.
- **Operations**:
  - Complete: broadcast intent targeting `TaskSurfaceActionReceiver` (`ACTION_COMPLETE_SURFACE_TASK`).
  - Open: direct Activity launch targeting `MainActivity` (`ACTION_VIEW_TASK`) with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`.
  - Dismiss: broadcast intent targeting `TaskSurfaceDismissReceiver` (`ACTION_DISMISS_SURFACE_NOTIFICATION`) carrying active receipt and serialized selection key.

### 1.4 BroadcastReceivers (`action`, `dismiss`, `time`)
- **`TaskSurfaceActionReceiver`**: Resolves `TaskSurfaceHostOwner`, validates host readiness and matching token generation, and calls `host.access.setCompleted(token, taskId, completed = true, nowEpochMs)` before refreshing host with `SurfaceRefreshReason.Data`. Stale callbacks harmlessly dropped.
- **`TaskSurfaceDismissReceiver`**: Resolves host, decodes selection key, and routes dismissal through `coordinator.dismiss(selectionKey, receipt)`. Serialized inside coordinator mutex; stale receipts safely ignored.
- **`TaskSurfaceTimeReceiver`**: Handles `ACTION_TIME_SET`, `ACTION_TIMEZONE_CHANGED`, `ACTION_DATE_CHANGED` with an atomic 500ms debounce window before dispatching `host.refresh(SurfaceRefreshReason.Clock)`.
- *Note on Receiver Export*: Receivers are designed as private and unexported. Phase 05 holds explicit ownership of `AndroidManifest.xml` to declare them with `android:exported="false"`.

### 1.5 Boundary Wake Worker (`task-surface-refresh-worker.kt`)
- **`TaskSurfaceRefreshWorker`**: Inexact `CoroutineWorker` scheduled with `ExistingWorkPolicy.REPLACE` and account tag `doggo_task_surface_<accountKey>`.
- **Catch-up Guard**: Enforces minimum delay of 15 minutes (`MIN_DELAY_MS = 900_000L`).
- **Readiness Check**: Awaits host readiness up to 4s, verifies account key and generation match input data, and triggers `host.refresh(SurfaceRefreshReason.Clock)`. Propagates cancellation cleanly.

### 1.6 Capability Controller & Settings UI (`task-surface-permission-controller.kt`, `task-surface-settings.kt`)
- **`TaskSurfacePermissionController`**: Evaluates `TaskSurfaceAvailability` (`OptedOut`, `PermissionDenied`, `AppDisabled`, `ChannelDisabled`, `Available`). Provides system intent builders for app-level notification settings and channel-specific settings.
- **`TaskSurfaceSettings`**: Material 3 Composable leaf with TopAppBar, opt-in toggle switch, rationale description, and responsive warning cards with direct action buttons (`onRequestPermission`, `onOpenSystemSettings`, `onClose`).

### 1.7 Dedicated Resource Artifacts
- **Vector Icon**: `androidApp/src/main/res/drawable/task-surface-notification-icon.xml` (24x24dp white silhouette vector; coordinator uses runtime identifier with fallback to prevent resource missing crashes).
- **Strings**: `androidApp/src/main/res/values/task-surface-strings.xml` providing full coverage for channel name, description, privacy versions, actions, and settings rationale.

---

## 2. Authored Test Suites (Execution Pending Orchestrator Verification)

### 2.1 Unit Suite (`task-surface-notification-test.kt`)
- `testDeadlineSelectionKeyCodecRoundTripTimedDeadline`: Full field round-trip preservation and zero title leakage.
- `testDeadlineSelectionKeyCodecRoundTripDateOnlyDeadline`: Anchor time and null time preservation.
- `testDeadlineSelectionKeyCodecRejectsMalformedData`: Version and malformed format rejection.
- `testDataUriEncodingWithDelimiterBearingTaskIds`: Uri.Builder path encoding for complex task IDs and receipts.
- `testIntentConstantsAndRequestCodeDeterminism`: Request code distribution across operations, tasks, and receipts.
- `testSelectionLogicEligibilityAndConflictKnowledge`: Incomplete/conflict projection selection and reminder independence.
- `testSelectionIgnoresCompletedAndDeleted`: Filtering invariants.
- `testTimeReceiverSupportedActions`: Supported actions and debounce constant.
- `testRefreshWorkerInvariants`: WorkManager tag and minimum delay invariants.
- `testTaskSurfaceAvailabilityHierarchy`: Availability states.

### 2.2 Instrumented Device Suite (`task-surface-preferences-device-test.kt`)
- `testInitialStateDefaultOff`: Verifies fresh account preferences default to disabled with null suppression.
- `testSetEnabledPersistence`: Verifies opt-in toggles persist synchronously to disk.
- `testSetEnabledClearsSuppression`: Verifies disable and subsequent enable clear suppression.
- `testReconcileSelectionClearsOnDisappearance`: Verifies disappearing deadline clears suppression and active post state.
- `testReconcileSelectionClearsSuppressionOnKeyChange`: Verifies switching to another task clears suppression.
- `testRecordPostAndSuppressIfCurrent`: Verifies post recording and valid dismissal persistence.
- `testSuppressIfCurrentRejectsStaleReceipt`: Verifies rejection of non-matching delete receipts.
- `testInvalidatePostPreventsDelayedDismissal`: Verifies programmatic removal invalidates receipts before delete intent fires.
- `testReopenInstancePreservesDurableState`: Verifies durable persistence across fresh instances.
- `testClearRemovesAllAccountData`: Verifies account wipe cleanly removes all preference data.

*Execution Notice*: In compliance with Phase 03 orchestrator rules, Gradle builds and tests were not run mid-flight. Verification is deferred to the parent orchestrator's central verification gate.

---

## 3. Strict File Ownership Compliance

Every file created/modified strictly matches the exclusive ownership list of Phase 03:
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-preferences.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-notification.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-intents.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-action-receiver.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-dismiss-receiver.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-time-receiver.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-refresh-worker.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-permission-controller.kt`
- `androidApp/src/main/kotlin/dev/doggo/android/task-surface-settings.kt`
- `androidApp/src/main/res/drawable/task-surface-notification-icon.xml`
- `androidApp/src/main/res/values/task-surface-strings.xml`
- `androidApp/src/test/kotlin/dev/doggo/android/task-surface-notification-test.kt`
- `androidApp/src/androidTest/kotlin/dev/doggo/android/task-surface-preferences-device-test.kt`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-03-notification-evidence.md`

Zero shared root files, manifests, application classes, or Gradle files were touched.

---

## 4. Integration Handoff to Phase 05

The following contracts and components are ready for Phase 05 wiring:
1. **Manifest Registration**: Phase 05 registers `TaskSurfaceActionReceiver`, `TaskSurfaceDismissReceiver`, and `TaskSurfaceTimeReceiver` as unexported (`android:exported="false"`) broadcast receivers in `AndroidManifest.xml`.
2. **Application Host**: Phase 05 implements `TaskSurfaceHostOwner` on `DoggoApplication` and instantiates `TaskSurfaceNotificationCoordinator`.
3. **Settings Navigation & Permission Flow**: Phase 05 embeds `TaskSurfaceSettings` into the settings navigation hierarchy and registers an `ActivityResultLauncher` with `ActivityResultContracts.RequestPermission()` for `POST_NOTIFICATIONS` (API 33+).
4. **Account Lifecycle & Wipe**: Phase 05 calls `coordinator.cancelForAccount(accountKey)` during account logout, account replacement, or wipe marker recovery.
5. **Runtime Device Acceptance**: Phase 05 / 07 verify actual API 34+ swipe dismissal delivery behavior and lock-screen privacy rendering on physical/emulated devices.
