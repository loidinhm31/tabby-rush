# Phase 08 — Android alarms, private notifications and restoration

## Context links
[Parent](plan.md) · [Native proof](phase-02-prove-native-delivery-and-dst.md) · [Scheduling gate](phase-07-shared-scheduling-and-account-safety.md) · [Android acceptance](../../docs/android-acceptance.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 20h. Owner: Android lead. Dependencies: phases 02/G2, 05, 07/G5. Final sync cancellation acceptance depends on 06.

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
- [ ] Alarm/posting adapter and bounded receiver.
- [ ] Wipe-first restore across process death/reboot/update/time/access.
- [ ] User permission capability states and private payloads.
- [ ] Actual offline/Doze/reboot/cancellation evidence.

## Success Criteria
Future `./gradlew :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest`; `node scripts/release-security-check.mjs --diagnose-source` plus actual merged release manifest audit. Actual notification observed on emulator/device under required scenarios; generic public lock-screen payload checked. Denied notification state cannot be labeled ready. Receiver with stale generation/wipe/task token cannot post. Device powered off/force-stop limitations explicitly documented.

## Risk Assessment
Idle quotas apply app-wide even if each task repeats >=15min; batch same-time tasks where practical, never promise every alert. Large restore workload must fit bounded receiver work; prove indexed/reconcile work size on representative task dataset rather than adding long-running service.

## Security Considerations
Only necessary system receiver declarations; immutable explicit intents; no task text in OS scheduling payload. Click and restore cannot resurrect wiped account; retain failed wipe marker.

## Next steps
Phase 10 consumes capability/settings flows. Phase 11 records final cross-device and adversarial Android acceptance; phase 12 governs release.

## Unresolved questions
No product questions; actual device/OEM results and permissions remain execution evidence gates.
