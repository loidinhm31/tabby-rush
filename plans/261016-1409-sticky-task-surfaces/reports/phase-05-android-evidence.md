# Phase 05 — Android Integration Evidence Report

- **Date:** 2026-10-16
- **Phase:** Phase 05 — Android runtime, navigation, wipe, registration and auditor integration
- **Status:** Implemented (Pending Centralized Phase 07 Android Execution)

---

## 1. Executive Summary

Phase 05 connects completed Phase 02 (Glance Task Widget) and Phase 03 (Task Surface Notification) leaves cleanly into the Android application lifecycle, navigation, persistence, and security auditor roots.

Key deliverables accomplished:
1. **Application & Readiness Host:** `TabbyRushApplication` implements `TaskSurfaceHostOwner` backed by `TaskSurfaceRuntimeCoordinator` and `AndroidTaskSurfaceAccess`. Enforces exact readiness lifecycle states (`Restoring`, `Ready(token)`, `SignedOut`, `Locked`, `Unavailable`) and a 4s bounded cold wait timeout.
2. **Access & Token Fencing:** Real `TaskSurfaceAccess` implementation strictly validates tokens and generations inside `ReminderDeliveryGate` admissions; non-current tokens are rejected; arbitrary action accounts are never opened on demand. OS handoff publication is strictly serialized under `publicationMutex`, marking surfaces non-Ready before draining during account transitions.
3. **Background Observers & Coalesced Refresh:** Process-owned Flow observers on runtime scope for `store.observeTodos()` and `store.observePersistedSchedule()` continue across Activity destruction, coalescing refresh events across Data, Startup, Account, Clock, Capability, and Widget triggers.
4. **Exhaustive Teardown & Wipe Clearing:** `CompositeLeaveSchedulerCancelGate` extends cancellation to surface observation jobs, WorkManager boundary wake, programmatic task notification removal (`cancelForAccount`), all installed launcher widget IDs (`TaskWidgetUpdater.clearAllInstances`), and account-local preferences. Startup wipe recovery clears all external surfaces before removing wipe markers; signed-out cold start blanks launcher widgets.
5. **Authoritative Completion Migration:** `SessionViewModel.toggleCompleted` is migrated from copying stale UI `Todo` instances to atomic `setCompletedLocally(id, desiredCompleted, nowEpochMs)`, triggering sync only on `CompletionChange.Changed`.
6. **Intent Navigation & Permission UX:** `MainActivity` and `SessionViewModel` handle `ACTION_VIEW_TASK` and `ACTION_ADD_TASK` intents with full token and generation re-validation. Registered `ActivityResultContracts.RequestPermission` for `POST_NOTIFICATIONS` integrated with `TaskSurfacePermissionController` and standalone `TaskSurfaceSettings`. Added compact `TaskSurfaceCreateScreen` reusing `TitlePolicy` validation.
7. **Least-Privilege Manifest & Security Auditor:** Registered `TabbyRushTaskWidgetReceiver` (with exact canonical class identity, `APPWIDGET_UPDATE` action in `<intent-filter>`, no unauthorized category/data tags, and `@xml/task_widget_info` provider metadata) and private receivers (`TaskSurfaceActionReceiver`, `TaskSurfaceDismissReceiver`, `TaskSurfaceTimeReceiver`, all `android:exported="false"`). Updated `scripts/release-security-check.mjs` with exact widget allowlist while maintaining fail-closed zero-exported-component policy for all other components.

---

## 2. File Ownership & Modifications

All modifications and creates are strictly within Phase 05 exclusive boundary:

| File Path | Action | Description |
|---|---|---|
| `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt` | Created | Real `TaskSurfaceAccess` implementation with 4s bounded readiness wait, token fencing, leased delivery gate admission, and serialized publication. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-runtime-coordinator.kt` | Created | `AndroidTaskSurfaceHost` coordinator managing publication mutex, non-Ready transitions, and coalesced multi-trigger refresh. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-create-screen.kt` | Created | Compact task creation dialog reusing `TitlePolicy` validation. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt` | Modified | Implements `TaskSurfaceHostOwner`, manages readiness states, cold start blanking, and safe account runtime lifecycle. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime.kt` | Modified | Added `surfaceObserverJob` reference for clean lifecycle cancellation. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt` | Modified | Installs process-owned Flow observers on runtime scope for `observeTodos` and `observePersistedSchedule`. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate.kt` | Modified | Extended teardown to cancel surface observers, boundary wake, widget instances, notifications, and preferences with observable ordering. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/android-account-state-manager.kt` | Modified | Extended startup pending wipe recovery to cancel boundary work and clear widgets, notifications, and preferences. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` | Modified | Migrated completion toggle to `setCompletedLocally`; wired token-fenced intent handling, surface create, and settings. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt` | Modified | Added `POST_NOTIFICATIONS` launcher, handled `ACTION_VIEW_TASK` and `ACTION_ADD_TASK`, and mounted surface settings/dialogs. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-sync-worker.kt` | Modified | Dispatches surface refresh upon successful background sync reconciliation. |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/reminder-restore-receiver.kt` | Modified | Wires boot/unlock/time/capability events to surface refresh; blanks widgets on signed-out boot. |
| `androidApp/src/main/AndroidManifest.xml` | Modified | Registered `TabbyRushTaskWidgetReceiver` (exported with provider info) and private unexported action/dismiss/time receivers. |
| `scripts/release-security-check.mjs` | Modified | Updated exported component auditor to narrowly allow `TabbyRushTaskWidgetReceiver` with canonical class identity, exact action, and provider metadata. |
| `scripts/release-security-check.test.mjs` | Created | Automated Node test suite verifying security auditor invariants (12/12 tests pass). |
| `androidApp/src/test/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate-test.kt` | Modified | Unit tests verifying surface observer cancellation and teardown quiescence ordering with active suspended jobs. |
| `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-surface-account-device-test.kt` | Created | Instrumented device tests for 4s readiness timeout, token fencing, and locked/unlocked storage. |
| `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-surface-navigation-device-test.kt` | Created | Instrumented device tests for intent immutability, intent extra routing, permission evaluation, and title policy. |

---

## 3. Verification & Evidence

### 3.1 Security Auditor Unit Tests (`scripts/release-security-check.test.mjs`)
Executed via `node --test scripts/release-security-check.test.mjs`:
```
▶ Release Security Check: Exported Components Auditor
  ✔ passes on canonical release manifest with MainActivity and verified TabbyRushTaskWidgetReceiver (2.2766ms)
  ✔ rejects foreign-package lookalike for MainActivity (e.g. com.attacker.MainActivity) (0.3003ms)
  ✔ rejects foreign-package lookalike for TabbyRushTaskWidgetReceiver (0.1437ms)
  ✔ rejects any exported service (0.1225ms)
  ✔ rejects any exported content provider (0.1259ms)
  ✔ rejects exported receiver other than TabbyRushTaskWidgetReceiver (0.1107ms)
  ✔ rejects TabbyRushTaskWidgetReceiver missing APPWIDGET_UPDATE action (0.3664ms)
  ✔ rejects TabbyRushTaskWidgetReceiver containing unauthorized category in intent filter (0.2697ms)
  ✔ rejects TabbyRushTaskWidgetReceiver containing unauthorized data in intent filter (0.1602ms)
  ✔ rejects TabbyRushTaskWidgetReceiver missing intent-filter (0.1667ms)
  ✔ rejects TabbyRushTaskWidgetReceiver missing provider meta-data (0.2058ms)
  ✔ ignores unexported private receivers with intent filters (0.9ms)
✔ Release Security Check: Exported Components Auditor (6.3163ms)
ℹ tests 12
ℹ suites 1
ℹ pass 12
ℹ fail 0
```

### 3.2 Diagnostic Source Security Audit (`scripts/release-security-check.mjs --diagnose-source`)
Executed via `node scripts/release-security-check.mjs --diagnose-source`:
```
=== Running Diagnostic Static Source Audit ===
[PASS] Base manifest prohibits cleartext traffic (usesCleartextTraffic="false")
[PASS] Base manifest restricts backup (allowBackup="false")
[PASS] Exported components audit: Only MainActivity and verified TabbyRushTaskWidgetReceiver are exported.
[PASS] XML security configs present (backup_rules.xml, data_extraction_rules.xml)
[PASS] Release Hub origin enforces HTTPS: https://api-glean-hub.qm-hub-v001.cloud
[PASS] Repository source secret audit: No hardcoded private keys or passwords detected in source.

Diagnostic Audit Result: CLEAN (Source meets static security rules)
```

---

## 4. Centralized Verification Commands (for Phase 07 Integration)

When centralized verification is run by the main agent:
```bash
# 1. Unit tests for composite leave cancel gate
./gradlew :androidApp:testDebugUnitTest --tests "dev.tabbyrush.android.CompositeLeaveSchedulerCancelGateTest"

# 2. Security auditor tests & static source check
node --test scripts/release-security-check.test.mjs
node scripts/release-security-check.mjs --diagnose-source

# 3. Android instrumented device tests
./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.tabbyrush.android.TaskSurfaceAccountDeviceTest

./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.tabbyrush.android.TaskSurfaceNavigationDeviceTest
```

---

## 5. Conclusion & Handoff

Phase 05 implementation is submitted for centralized Phase 07 verification. Node auditor tests (12/12 pass) and static source diagnostics (CLEAN) passed. Android Gradle compilation, JVM unit/device tests, release APK audit, and end-to-end lifecycle acceptance remain pending for centralized runner execution.
