# Phase 02 — Prove native delivery and strict DST resolution

## Context links
[Parent](plan.md) · [Platform research/sources](research/reminder-platform-research.md) · [Temporal decisions](phase-01-freeze-contract-and-transition-design.md) · [Windows acceptance](../../docs/windows-acceptance.md)

## Overview
Date 2026-10-15. Priority P1. Status: Logic and contracts DELIVERED (2026-10-15); Gate G2 physical native delivery formally BLOCKED on missing host prerequisites (Android SDK/device, desktop notification daemon, Windows runtime). Review completed (Score: 7.5/10 APPROVED). Estimate 16h. Owner: platform leads. Dependencies: agreed brainstorm; can run beside phase 01. Gate G2 blocks broad shared UI (Phase 10) and final native adapters.
## Key Insights
D-Bus/JNA presence is not delivery proof. Windows host-matched target guard prevents validating Windows on Linux. kotlinx-datetime default local conversion may normalize gaps or choose an overlap offset silently.

## Requirements
- Prove native offline posting, stable identity and removal on current supported targets without raising Android minSdk26 or existing desktop minimums.
- Explicitly select Windows binding/installer identity based on installed runtime, not source compilation.
- Prove strict gap/overlap detection and calendar-day boundary algorithm before editor implementation.

## Architecture
- Android candidate: AlarmManager explicit immutable PendingIntent + BroadcastReceiver + NotificationManager channel. SCHEDULE_EXACT_ALARM, not restricted USE_EXACT_ALARM entitlement. Distinguish notification permission/channel from timing access.
- Linux candidate: existing D-Bus Java session transport; `/org/freedesktop/Notifications`, `org.freedesktop.Notifications`, Notify/GetCapabilities/CloseNotification.
- Windows first candidate: narrow JNA-backed WinRT/COM toast bridge using documented desktop AUMID route; stable AUMID `dev.doggo.desktop`, installer-set Start-menu shortcut property and process identity. Prove HRESULT/interface ownership, COM initialization/threading, XML escaping, tag/group history removal. If JNA bridge cannot be supported, choose a small bundled native helper/library on Windows only; same native contract, no tray substitution or raised OS minimum. Record selected dependency/license/checksum and packaging changes.
- Proof harness uses generic content and local input, no network; retain only reusable proven adapter parts, remove spike entrypoints afterward.
- Strict resolver returns zero/one/two valid zone offsets for local anchor. Existing library preferred; narrow expect/actual JVM/Android ZoneRules permissible if common API cannot enumerate. Test runtime tzdb coverage on API26 and desktop.

## Related code files
Modify during execution:
- `/home/loidinh/WS/doggo/desktopApp/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, target lockfiles if selected binding changes.
- `/home/loidinh/WS/doggo/androidApp/src/main/AndroidManifest.xml`; preserve hardened manifest/export/backup policy.
Create candidates under existing roots: Android `androidApp/src/main/kotlin/dev/doggo/android/` alarm/posting components; Linux `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-notification-adapter.kt`; Windows matching `windows-notification-adapter.kt`; shared strict timezone resolver contract/actuals. Final placement confirmed by phase 08/09 ownership.
Evidence: existing `docs/android-acceptance.md`, `docs/linux-acceptance.md`, `docs/windows-acceptance.md` (mark proof vs final acceptance separately).

## Implementation Steps
1. Inventory actual Android API levels/device, Linux session bus/daemon, Windows installed test machine/MSI tools; record missing prerequisites as blocked gates, not passing.
2. Android post/clear channel notification offline, schedule one-shot then ordinary process kill (not force-stop); observe receiver launches and notification. Probe exact denial/inexact status.
3. Linux call native daemon, observe bubble/history, capture returned ID, close it; repeat from packaged distributable while minimized; test missing daemon.
4. Windows build/install on Windows using existing MSI path. Inspect shortcut AUMID and selected native binding in package; observe toast visible/minimized and remove owned history. Upgrade/uninstall must not leave stale identity/helper.
5. Prove DST cases: New York 2026-03-08 02:30 rejected; 2026-11-01 01:30 requires two offset choices producing different instants; date-only next-day boundaries across 23/25-hour days; timezone-midnight transitions. Use fixed vectors, no live-clock assertions.
6. Decide binding/resolver and supported runtime matrix; attach sanitized OS/build versions, observed timestamps/screenshots, native IDs and failure statuses. Delete throwaway harnesses after reusable code retained.

## Todo list
- [x] Deliver strict DST resolver contract and platform actuals (`StrictTimeZoneResolver`, `StrictZoneOffsetResult`, `StrictTimeZoneResolverTest` 7/7 vectors passing; JVM `java.time.zone.ZoneRules` binding selected; physical Android API 26 execution pending SDK in Phase 08).
- [x] Deliver candidate native notification adapters, capabilities checkers, and manifest entries across Android, Linux, and Windows; repair Linux dependency lockfile.
- [ ] Observe Android receiver-based offline notification and clear (BLOCKED: Candidate adapter & receiver delivered in androidApp; physical execution pending Android SDK & device in Phase 08).
- [ ] Observe packaged Linux native notification and absence status (PARTIAL/BLOCKED: Absence handling and D-Bus fallback verified in tests; visual bubble delivery pending notification daemon in user session in Phase 09).
- [ ] Observe installed Windows toast and AUMID/history removal (BLOCKED: Candidate adapter & XML builder delivered; installed MSI toast execution pending Windows host in Phase 09).
- [ ] Complete Gate G2 physical native delivery (BLOCKED on missing host prerequisites: Android SDK/device, desktop notification daemon, Windows runtime).
## Success Criteria
G2 only passes with actual visible native evidence from all three platforms and strict gap/overlap results. API invocation/build alone fails gate. Permission-disabled delivery is a supported blocked state, not fake success.
Future commands: `./gradlew :androidApp:assembleDebug`; Linux `./gradlew -PdesktopTarget=linux :desktopApp:createDistributable`; Windows `.\gradlew.bat -PdesktopTarget=windows :desktopApp:packageMsi`. Read existing smoke script help for runtime/package options.

## Risk Assessment
Windows proof may require helper/packaging work; keep phase blocked until concrete support exists. Xpra without a notification daemon cannot prove Linux native display.

## Security Considerations
Generic proof payloads; no account text/secrets. User-initiated permission access. No bypass of DND/channel controls; installer changes remain per-user and signed-release policy unchanged.

## Next steps
1. Retain Gate G2 status as BLOCKED for physical device delivery while unblocking independent contracts.
2. Proceed with Phase 03 (Hub Versioned Contract & Migration) and Phase 05 (Shared Model & Room Migration).
3. Fulfill physical delivery prerequisites in Phase 08 (Android device testbed) and Phase 09 (Linux desktop notification daemon and Windows MSI testbed).
4. Addressed code review recommendations: eliminated stub fake success in Windows toast bridge (returns safe `Unavailable`), documented duplicate timezone resolver code between jvmMain and androidMain for Phase 05/07 intermediate source-set unification, and fixed duplicate section header in docs/android-acceptance.md.
## Unresolved questions
1. Selected Windows binding/native helper and installed AUMID identity remain an open implementation decision pending Phase 09 Windows testbed; current candidate bridge returns safe `Unavailable` stub.
2. Physical delivery on Android API 26 (Phase 08), Linux session notification daemon (Phase 09), and Windows MSI host (Phase 09) are prerequisites to clear Gate G2.
