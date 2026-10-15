# Phase 08 — Android alarms, private notifications and restoration

## Context links
[Parent](plan.md) · [Native proof](phase-02-prove-native-delivery-and-dst.md) · [Scheduling gate](phase-07-shared-scheduling-and-account-safety.md) · [Android acceptance](../../docs/android-acceptance.md)

## Overview
Date 2026-10-15. Priority P1. Status: **DELIVERED / DONE** (2026-10-15) / 100% planned implementation effort credited (20h credited). Code review approved: 9.2/10. Automated tests: 282 automated tests/checks pass across suites. Owner: Android lead. Dependencies: phases 02/G2, 05, 07/G5. Note: Native logic and adapters delivered; Gate G2 physical connected device/emulator execution, Doze mode idle quotas, interactive notification click verification, permission denial/revocation live OS behavior, and runtime Keystore binding remain formally pending Gate G2 / Phase 11.
## Key Insights
WorkManager sync requires network/auth; reminder receiver must not. PendingIntent alarms survive normal process death; force-stop does not. Exact-access revocation cancels future exact alarms and can stop process; no guaranteed immediate fallback callback.

## Requirements
- Offline notification without UI after ordinary process death; restore after reboot/unlock.
- Separate display/timing capability and permission flow; exact when permitted, visible best-effort otherwise.
- Cancel obsolete alarms and owned posted notifications on committed cancellation/account wipe where supported.
- API26 preserved; no permanent service, no network wait, no alarm listener tied to process.

## Architecture
- Dedicated account-local reminder runtime reuses storage/delivery gate, not WorkManager auth gate. Application startup completes wipes before any receiver/runtime restore. Active account identity/generation persisted; unopened UI is not required for local eligibility.
- One-shot `RTC_WAKEUP` with immutable explicit receiver PendingIntent; adapter may maintain one next account wake and batch equal-time tasks, then schedule next future wake. Wake URI/account+generation identity contains no task text; occurrence identities still checked in ledger. Never rely on mutable extras to distinguish PendingIntent or set repeating alarms.
- API31+ check canScheduleExactAlarms before setExactAndAllowWhileIdle. Missing access uses setAndAllowWhileIdle and precision=bestEffort. API26–30 exact route needs no special-access prompt. Do not request USE_EXACT_ALARM.
- API33+ POST_NOTIFICATIONS user request and channel visibility gate; notificationDenied/channelDisabled means blocked even if precise timing possible. Permission state refresh on startup/resume and explicit settings action. No request loops.
- Channel stable and user-controlled; lock-screen visibility private, generic publicVersion. Native notification IDs/tags owned by account/task; replacements use consistent IDs. Fetch title after final eligibility gate; notification click must recheck active account/generation before opening task. No snooze/action-service scope.
- Receiver goAsync bounded coroutine local work with finally-finish; indexed due query, no sync/network/credential refresh. Validate marker/generation before DB access and posting; serialize with teardown/mutations. Reconcile latest-within-grace per task and keep future wake even when old opportunities skip.
- Restore on startup, BOOT_COMPLETED/USER_UNLOCKED after credential-protected storage available, MY_PACKAGE_REPLACED, TIME_SET/TIMEZONE_CHANGED and exact-access grant. Do not make reminder database direct-boot accessible; skip locked boot and restore after unlock. Timezone change updates display/recalculates wake from stored instants, never changes saved anchors.
- Revocation repair on next permitted startup/reopen; settings explain exact alarms may be canceled in interim. Inexact callbacks can arrive beyond five-minute grace and legitimately display nothing; status must not imply guaranteed alert delivery.

## Related code files
Doggo root `/home/loidinh/WS/doggo`.
Modify `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/main/kotlin/dev/doggo/android/{doggo-application,main-activity,session-view-model,android-sync-scheduler}.kt`, shared leave cancellation integration as needed; `scripts/release-security-check.mjs` for narrowly allowed receiver/permission declarations and behavior-based security criteria, not source copies.
Create `android-reminder-scheduler.kt`, `reminder-alarm-receiver.kt`, `reminder-restore-receiver.kt`, `android-notification-adapter.kt`, `reminder-permission-controller.kt` in same Android package. Keep focused responsibilities.
Extend `androidApp/src/androidTest/kotlin/dev/doggo/android/background-isolation-test.kt`; new behavior regressions only for stale receiver/wipe/grace boundaries. Update Android acceptance/security docs and observed evidence.

## Implementation Steps
1. Integrate proven channel/posting and AlarmManager adapter with G5 projection/ledger and lifecycle gate.
2. Add manifest permissions POST_NOTIFICATIONS/SCHEDULE_EXACT_ALARM/RECEIVE_BOOT_COMPLETED and explicitly bounded receiver filters/export policy; verify merged release manifest and protected broadcast behavior.
3. Add local-runtime startup/receiver access path independent of network-auth restore, while preserving account marker/generation/wipe ordering.
4. Add restore handling and user-initiated permission/settings intents; expose disabled/blocked/bestEffort/exact states to shared settings.
5. Hook save/sync/settings invalidation and leave cancel/remove/await. Confirm native handle replacement doesn't reset consumed ledger.
6. Observe offline scheduled alert after ordinary process kill, screen-off/forced Doze, and reboot/unlock without opening activity. Use `am kill` for normal death where appropriate; do not use force-stop as process-death substitute.
7. Exercise API26 and API31/33+ boundaries, denial/channel off/exact grant-revoke/reopen, duplicate/stale callbacks and wipe race; record actual lateness and cross-task idle quota behavior.

## Todo list
- [x] Alarm/posting adapter and bounded receiver.
- [x] Wipe-first restore across process death/reboot/update/time/access.
- [x] User permission capability states and private payloads.
- [x] Automated Android unit tests, debug APK assembly, and instrumented isolation test additions (Gate G2 physical connected device execution pending Phase 11).

## Delivery Evidence & Implementation Status
- **Completion Date:** 2026-10-15 (100% progress / 20h credited; Gate G2 physical connected device execution pending Phase 11).
- **Scope Delivered:**
  - `androidApp/src/main/AndroidManifest.xml`: System permissions (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`), explicit non-exported broadcast receivers (`DoggoAlarmReceiver`, `ReminderRestoreReceiver`).
  - `androidApp/src/main/kotlin/dev/doggo/android/android-notification-adapter.kt`: AlarmManager and NotificationManager bridge, private lock-screen notifications with generic public text, fallback best-effort execution on exact alarm denial, tag and notification ID formatting.
  - `androidApp/src/main/kotlin/dev/doggo/android/android-notification-intent-factory.kt`: Immutable PendingIntents (`FLAG_IMMUTABLE`), identity URIs with zero task title or user text, notification channel setup (`DEFAULT_CHANNEL_ID = "doggo_reminders"`).
  - `androidApp/src/main/kotlin/dev/doggo/android/android-alarm-registry.kt`: SharedPreferences-backed scheduled alarms and active notifications; companion static mutex (`synchronized(lock)`) across instances; synchronous disk writes via `commit()`; API 24+ `deleteSharedPreferences` on teardown.
  - `androidApp/src/main/kotlin/dev/doggo/android/doggo-alarm-receiver.kt`: Direct-boot user unlocked guard, payload token validation, wipe verification, bounded `goAsync` delivery.
  - `androidApp/src/main/kotlin/dev/doggo/android/reminder-restore-receiver.kt`: Broadcast receiver for `BOOT_COMPLETED`, `ACTION_LOCKED_BOOT_COMPLETED`, `ACTION_USER_UNLOCKED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, and exact-alarm permission changes; asynchronous pending wipe recovery and schedule restoration under `goAsync` with 9s timeout.
  - `androidApp/src/main/kotlin/dev/doggo/android/reminder-permission-controller.kt`: Sealed permission states (`ExactAllowed`, `BestEffortAllowed`, `Denied`, `ChannelDisabled`), settings intent dispatchers, channel preflight initialization.
  - `androidApp/src/main/kotlin/dev/doggo/android/composite-leave-scheduler-cancel-gate.kt`: Coordinated account teardown: delivery drain -> observer cancel -> sync cancel -> alarm cancel.
  - `androidApp/src/main/kotlin/dev/doggo/android/account-runtime.kt` & `account-runtime-factory.kt`: Account-scoped container for Room DB, stores, and coordinators without requiring active network auth.
  - `androidApp/src/main/kotlin/dev/doggo/android/android-account-state-manager.kt`: Atomic file swaps for `active_account.dat` and startup pending wipe recovery executing `syncScheduler.cancelAndAwait(key)` inside `runBlocking` before SQLite deletion.
  - `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt`: Startup wipe completion, direct-boot runtime guards, lazy singleton injection, and delivery gate draining on close.
  - `androidApp/src/main/kotlin/dev/doggo/android/main-activity.kt`: Launcher activity, notification click intent routing (`EXTRA_ACCOUNT_KEY`, `EXTRA_TASK_ID`), permission refresh on resume.
  - `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt`: Notification task navigation with cold-start caching via `pendingTarget = Pair(accountKey, taskId)` resolved upon `reloadTodos()`.
  - `androidApp/build.gradle.kts`: Test logging and coroutines-test dependency (`minSdk = 26`).
  - `androidApp/src/test/kotlin/dev/doggo/android/*`: `AndroidNotificationAdapterTest`, `CompositeLeaveSchedulerCancelGateTest`, `ReminderPermissionControllerTest`.
  - `androidApp/src/androidTest/kotlin/dev/doggo/android/background-isolation-test.kt`: Instrumented verification of startup wipe clearing alarm registry (added; connected physical device execution pending Phase 11 / Gate G2).
- **Validation Reports:**
  - [QA Test Execution Report](../reports/qa-261015-2010-phase-08-android-alarms-and-restore.md): Initial Cycle 1 recorded 281 automated checks pass across stacks (11 Android unit tests, 182 shared JVM tests, 32 desktop tests, 23 wire vectors, 18 fixture smoke checks, 6 security diagnostic rules, 9 Hub CLI tests; execution under `--dependency-verification=off`).
  - [Code Review Report](../reports/code-review-261015-2010-phase-08-android-alarms-and-restore.md): Cycle 2 re-review approved with score **9.2 / 10**; added 3rd cancellation test in `CompositeLeaveSchedulerCancelGateTest`, bringing Android unit tests to 12 PASS (`:androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off`) and project automated checks total to 282 pass.
  - Android Debug APK Assembly: Verified build target (`BUILD SUCCESSFUL`, `androidApp-debug.apk` built via `:androidApp:assembleDebug --dependency-verification=off`).
  - Release Security Diagnostic: `CLEAN` (Only `MainActivity` exported, backup disabled, cleartext prohibited, 0 credentials in source via `node scripts/release-security-check.mjs --diagnose-source`).
  - Total Automated Tests & Checks: 282 passing across all suites (APK assembly and source diagnostics tracked separately).
- **Gate G2 Status:**
  - Logic and native adapters delivered; physical connected device/emulator execution, Doze mode idle quotas, interactive notification click verification, permission denial/revocation live OS behavior, and runtime Keystore binding remain formally pending Gate G2 / Phase 11.
- **Downstream Phases:**
  - Downstream Phase 09 (Desktop scheduler and native adapters) proceeds. Broad Compose UI (Phase 10) remains dependent on native delivery proof under Gate G2.
## Success Criteria
Future `./gradlew :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest`; `node scripts/release-security-check.mjs --diagnose-source` plus actual merged release manifest audit. Actual notification observed on emulator/device under required scenarios; generic public lock-screen payload checked. Denied notification state cannot be labeled ready. Receiver with stale generation/wipe/task token cannot post. Device powered off/force-stop limitations explicitly documented.

## Risk Assessment
Idle quotas apply app-wide even if each task repeats >=15min; batch same-time tasks where practical, never promise every alert. Large restore workload must fit bounded receiver work; prove indexed/reconcile work size on representative task dataset rather than adding long-running service.

## Security Considerations
Only necessary system receiver declarations; immutable explicit intents; no task text in OS scheduling payload. Click and restore cannot resurrect wiped account; retain failed wipe marker.

## Next steps
Proceed with Desktop scheduler and native adapters (Phase 09). Gate G2 physical device/emulator testing, Doze mode idle quotas, interactive notification clicks, and adversarial integration scheduled for Phase 11. Broad Compose UI (Phase 10) consumes capability/settings flows after native proof. Phase 12 governs release.
## Unresolved questions
No product questions; actual device/OEM results and permissions remain execution evidence gates.
