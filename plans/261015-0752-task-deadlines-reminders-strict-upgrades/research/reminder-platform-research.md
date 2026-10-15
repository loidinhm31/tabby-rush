# Reminder/platform research

Date: 2026-10-15. Source: terminal report from `ReminderLifecycleResearch`; parent verified datetime dependency, migration registry and desktop packaging. Status: read-only feasibility evidence, no notification runtime proof.

## Current source map
Paths relative to `/home/loidinh/WS/doggo`.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-models.kt`, `strict-sync-contract.kt:35-39,89-101`; `sync/hub-sync-dtos.kt`: no deadline/reminder fields.
- `shared/src/commonMain/kotlin/dev/doggo/shared/data/doggo-database.kt:9-24`, `database-migrations.kt:1-17`: Room v1; empty explicit migration registry.
- `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt:35-115,118-203`: UI reads draft overlays; save atomically persists draft+submitted outbox. Use outbox intentions, not drafts, for eligibility.
- `shared/src/commonMain/kotlin/dev/doggo/shared/data/doggo-dao.kt`: suspend queries/atomic transactions; no observable reminder projection.
- `shared/src/commonMain/kotlin/dev/doggo/shared/sync/{staged-full-scan,outbox-reconciliation,account-sync-coordinator,account-leave-protocol}.kt`: scan/ack/conflict changes and wipe cancellation gates.
- `shared/build.gradle.kts:24-38`: kotlinx-datetime already in commonMain. Strict gap/overlap resolution requires proof; default conversion must not silently pick an offset.
- `androidApp/src/main/AndroidManifest.xml:1-29`: no current reminder/boot/exact/notification declarations.
- `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt:55-189`: wipe recovery precedes restored runtime; dedicated local reminder path needed.
- `androidApp/src/main/kotlin/dev/doggo/android/doggo-sync-worker.kt:18-78`: network/auth-oriented sync worker; not offline reminder receiver.
- `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt`: account controller/window disposal; X exits.
- `desktopApp/build.gradle.kts:18-24,49-54,79-86`: host-matched target guard; Linux D-Bus, Windows JNA; per-user MSI/shortcut, no observed AUMID toast registration.

## Proposed design
- Shared date-only/timed model, captured zone/offset, saved anchor instant and schedule token. Date-only label independent of anchor; next-calendar-day overdue.
- Draft-free observable persisted projection: base plus submitted applicable outbox; suppress conflicting rules, still honor saved completion/delete barriers.
- Local occurrence ledger; one delivery gate serialized with saved mutations/account teardown. No atomicity between SQLite and OS posting; choose at-most-once attempt after durable claim, explicitly possible loss at crash boundary.
- Device-local controls/defaults in account-local storage; copy defaults at creation, never rewrite existing rules.
- Android immutable PendingIntent AlarmManager receiver, no network/auth refresh; boot-after-unlock restore after wipe recovery; display permission/channel and exact-access states separate.
- Linux direct session-bus org.freedesktop.Notifications; Windows native Win32 toast/AUMID route first candidate. Packaged proof required before UI expansion; dependency presence is not proof.

## Primary sources reviewed by parent
- [Android alarms](https://developer.android.com/develop/background-work/services/alarms): alarms outside process lifetime; SCHEDULE_EXACT_ALARM user grant; revocation stops app/cancels future exact alarms; inexact idle alarms may be much later than five-minute grace.
- [Linux notification protocol](https://specifications.freedesktop.org/notification/latest/protocol.html): GetCapabilities, Notify, returned notification ID, CloseNotification; optional interaction support.
- [Windows desktop AUMID](https://learn.microsoft.com/en-us/windows/win32/shell/enable-desktop-toast-with-appusermodelid): valid Start-menu shortcut identity required for documented route; installer registration recommended.
- Additional agreed sources: [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby), [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [idle exact restrictions](https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent)).
- docs-seeker topic detector ran; Context7 fetch reported no documentation. Primary official URLs used instead; no automatic skill discovery claim.

## Future execution commands
- JDK21: `./gradlew :shared:jvmTest`; `./gradlew :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest` with SDK/device.
- Linux: `./gradlew -PdesktopTarget=linux :desktopApp:test :desktopApp:createDistributable :desktopApp:packageRpm`.
- Windows host: `.\gradlew.bat -PdesktopTarget=windows :desktopApp:test :desktopApp:createDistributable :desktopApp:packageMsi`.
- Existing acceptance/package scripts remain authoritative for invocation options. Normal dependency verification retained; diagnostic verification-off commands do not satisfy release gate.

## Unresolved questions
- Windows binding, COM/WinRT lifecycle and MSI AUMID details: phase-02 packaged proof decides; not selectable by JNA presence.
- Strict DST offset enumeration with kotlinx-datetime 0.6.1: prove shared/expect-actual implementation before calendar UI.
- Runtime prerequisites remain separate: session notification daemon for Linux, Android device/OS controls, installed Windows environment.
