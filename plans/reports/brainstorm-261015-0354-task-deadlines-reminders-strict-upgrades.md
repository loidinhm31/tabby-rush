# Brainstorm: task deadlines, reminders, strict-contract upgrades

Date: 2026-10-15
Status: scope agreed; implementation not started
Projects: TabbyRush + adjacent Glean Hub

## Problem and final artifacts

Add optional task deadline date/time and configurable reminders. Android alerts without opening UI; Linux/Windows alert while TabbyRush runs, visible or minimized. Deadline and per-task reminder rules sync through Hub.

Deliverable of this session: this decision report. No code implementation or detailed implementation plan.

Eventual feature artifacts:
- Shared task editor: deadline date, optional time, timezone, reminder enablement, lead time, repeat interval, total count, schedule preview/validation.
- Task list: deadline/date display and overdue indication; accessible labels, not color-only state.
- Settings: device-local notification enablement, new-task reminder defaults, date-only anchor initially 09:00, permission/capability status.
- Explicit Room migration preserving existing tasks; extended sync contract matching server/client.
- Android OS-scheduled notifications with restoration/cancellation lifecycle.
- Native Linux/Windows notifications and process-scoped scheduling; closing exits.
- Hub server explicit strict-version support and controlled data-preserving upgrade operation.
- Hub UI supported-version/schema display and controlled upgrade action.
- Script invoking the same server upgrade operation; shared validation/error semantics with UI.
- Updated contract/schema specifications, migration/runbook, platform acceptance evidence and feature docs.

## Agreed requirements

- Deadline optional; date-only or date+time.
- Date-only reminder anchor uses configurable default, initially 09:00 in task's captured timezone. Store date-only presentation independently of reminder anchor.
- Date-only task becomes overdue at start of the following local calendar day in captured timezone, not at 09:00 and not by adding a fixed 24 hours.
- Resolve reminder anchor to an instant at save; sync it so enabled devices alert at the same moment worldwide. Display timed deadlines in device timezone with editing timezone explicit.
- Copy device-local defaults into new task's synced rule; changing defaults does not rewrite existing tasks. Unrelated title/completion edits must not re-resolve saved instants.
- Rule: enabled, lead time, repeat interval, total alert count. Count includes first opportunity.
- Count 1–10; repeating interval minimum 15 minutes. Interval irrelevant for count 1.
- `first = anchor - lead`; `occurrence(i) = first + i * interval`, for `i = 0..count-1`.
- Reject a rule whose last occurrence exceeds anchor; no truncating/clamping. Lead nonnegative; validate arithmetic overflow and missing deadline before saving.
- Example: anchor 15:00, lead 30 minutes, interval 15 minutes, count 3 → 14:30, 14:45, 15:00.
- Count describes scheduled opportunities, not guaranteed visible notifications. Android app-wide idle quotas can delay/batch alerts from different tasks despite per-task spacing.
- Recovery/delayed callback: five-minute grace. Post at most latest eligible opportunity per task within grace; skip older opportunities; preserve future opportunities. Posting shortly after deadline is allowed within grace, although scheduled times never exceed anchor.
- Complete/delete/remove deadline/disable reminders cancels pending delivery. Logout/account wipe cancels schedules and removes posted notifications owned by account where platform permits.
- Both enabled devices may notify. Local delivery history is not synced. Remote edits/completion affect a device only after it receives sync; no instant cross-device cancellation promise.
- Local saved changes work offline. Unsaved editor state must never create/cancel a schedule.
- Normal Android process death does not require reopening UI. Reboot restoration supported after unlock. Settings force-stop requires app reopening; device powered off cannot alert.
- Linux/Windows notify while visible or minimized; X exits and stops scheduling. No close-to-tray, autostart, or delivery during computer sleep/after exit. On resume apply agreed grace.

## Scope and constraints

In scope: TabbyRush shared model/UI/storage/sync/lifecycle, Android notifications/alarms, Linux/Windows scheduling/native delivery, Hub strict-version handling, UI and script migration entry points, existing-data preservation and verification.

Out of scope: recurring tasks, snooze, repeated overdue campaigns, cloud push/FCM, continuously running Android service, tray lifecycle, desktop daemon/autostart, macOS/iOS, arbitrary strict-schema editing, general-purpose schema migration platform.

Compatibility: retain current Android minimum API 26 and current supported Linux/Windows targets. Existing task records must gain no deadline and disabled reminders by default. Do not silently raise platform minimums for a notification binding.

Operational limits: offline-first; no network required to deliver a locally known reminder; no timing or exactly-once guarantee beyond OS capabilities; respect denied permissions, notification channel settings, DND and battery controls. No delivery bypasses.

Security: account partitions/generation/wipe fences remain authoritative; notification payloads must not leak wiped-account task text. Default Android lock-screen content private, generic public content. Hub upgrade requires app-scoped administrative authorization, not merely a valid ordinary user token.

Timeline: no deadline supplied; do not trade migration/data safety for an assumed release date.

## Repository evidence

TabbyRush:
- `shared/.../contract/todo-models.kt:9-17`: task has no deadline/reminder fields.
- `shared/.../sync/hub-sync-dtos.kt:5-11`: four-field wire payload.
- `shared/.../contract/strict-sync-contract.kt:35-39,89-101`: matching four-field data/digest encoding.
- `integration/glean-hub/tabbyrush-schema.json:5-12`: four-column todos registry schema.
- `shared/.../data/tabbyrush-database.kt:13-23`: Room schema version 1; explicit migration required.
- `shared/.../data/room-account-store.kt:35-75`: visible UI projection overlays drafts; not a safe reminder eligibility query.
- Lifecycle scout: DAO suspend queries have no observable reminder projection; controllers reload UI on local actions/coordinator progress.
- `androidApp/src/main/AndroidManifest.xml:4-29`: no notification/exact-alarm/boot declarations currently.
- Lifecycle scout: `tabbyrush-application.kt` completes pending wipes before local session restore; worker checks account, generation, wipe marker.
- `desktopApp/.../main.kt:434-439`: close disposes runtime and exits.
- `desktopApp/build.gradle.kts:49-54`: existing Linux D-Bus / Windows JNA dependencies; bindings available, notification adapters not implemented.

Glean Hub, paths relative to sibling repository:
- `glean-hub-plugin/glean-hub-sync/src/services/app_service.rs:102-151`: fixed strict schema requires exactly four task fields.
- Same file `:435-477`: ordinary schema update rejects strict/non-legacy apps.
- `.../models/strong_sync.rs:6,43-49`: strict-v1 constant, typed four-field payload denying unknown fields. Backend scout confirms fixed digest and replay metadata.
- `glean-hub-app/src/services/sync-admin-service.ts:117-129`: UI schema update calls ordinary PUT schema endpoint.
- `glean-hub-app/src/pages/admin/edit-app.tsx:47-60`: existing editor uses that endpoint, not a migration operation.
- Backend scout found no implemented versioned strict-payload/schema migration preserving offline operations and receipts. Existing fresh-epoch recovery is not a safe feature migration.

Research: two read-only scouts completed; full reports consumed and key contract/projection/dependency excerpts parent-verified. External API documentation reviewed. No app, migration, or native notification runtime exercised. This is architecture feasibility evidence, not implemented-behavior proof.

## Evaluated approaches

| Approach | Pros | Cons / decision |
|---|---|---|
| Shared rules + native local scheduling | Offline; Android can wake app process; desktop stays process-scoped; fits existing KMP split | Requires platform adapters, permission/lifecycle handling and Hub contract upgrade. Recommended. |
| Android best-effort background jobs | Reuses WorkManager infrastructure; simpler access model | Jobs do not provide required timing accuracy. Not selected for reminder delivery; keep WorkManager for sync. |
| Server-triggered push | Can react to remotely known changes | Additional server/push infrastructure and connectivity; no absolute delivery guarantee. Not selected. |

Local-only metadata would avoid Hub changes but contradict agreed synced scope; not selected. Constant background service costs battery and conflicts with modern Android restrictions; not an appropriate implementation.

## Recommended design

### Shared task state and scheduling

Persist deadline/rule alongside full task intentions across confirmed base, outbox, drafts and scan staging; migrate all projections, conflict handling, tombstones and wire mappings consistently. Keep delivered/skipped occurrence state and device controls local.

One shared schedule calculator and draft-free persisted eligibility projection. Derive from confirmed task plus submitted local outbox intentions; exclude unsubmitted drafts. Publish changes for local mutations, sync/recovery and conflict resolution, not only foreground UI reloads.

Platform adapters consume the same schedule. Stable occurrence identity includes account/generation, task identity, scheduling-rule token and occurrence index. At delivery re-read eligibility: active account, generation, wipe state, task/rule token, completion/deletion, device controls and grace. Serialize delivery against account teardown. Cancel obsolete OS handles and posted notifications where supported.

Durable local occurrence ledger limits duplicate callbacks/restarts. Ledger mutation and OS posting are not atomic; do not promise exactly-once display. Record schedule-level state without relying solely on server revision, which changes on unrelated task edits.

DST recommendation: reject nonexistent local times with explanation; ambiguous times require explicit offset choice. Existing stored instants stay fixed across device timezone/default changes. Date-only boundaries use captured zone's next calendar-day start.

### Android

Use one-shot `AlarmManager` alarms with explicit immutable `PendingIntent` receiver identity, not listener callbacks dependent on process lifetime. Request `setExactAndAllowWhileIdle` when permitted; use inexact idle-capable scheduling otherwise with visible degraded-timing status. No permanent foreground/background service.

Separate gates: Android 13+ `POST_NOTIFICATIONS` and channel availability control ability to display; Android 12+ exact-alarm special access controls requested timing. Notification denial/channel disabled → blocked delivery state; exact access absent → best-effort state, not falsely labeled precise. User-initiated permission requests, no forced loops.

Boot/unlock, app startup/update, clock changes and alarm-access grants rebuild schedules from durable data after pending-wipe recovery. Revocation cancels exact alarms and can stop process; do not assume a revocation callback can immediately repair them. Recheck/rebuild on next permitted startup.

Batch same-time eligible tasks in one wake where practical; app-wide idle quotas still prevent guaranteed cross-task timing. Receiver performs only bounded local eligibility/posting work; no network wait.

### Desktop

One account-scoped process scheduler; OS notifications independent of Compose window visibility. Linux use `org.freedesktop.Notifications` over existing D-Bus transport, not tray availability as proxy for notification capability. Windows use a native supported desktop notification integration; selected binding and installer identity must be proved on packaged Windows build. JNA existence is not notification proof.

Capability failure visible in settings; no fake success/in-app-only fallback reported as system delivery. Closing exits; suspend/resume and wall-clock changes recalculate next opportunity and grace without a catch-up burst.

### Glean Hub strict versions and migrations

Explicit supported source/target contract-version and schema pairs. An extended version such as `strict-v2` is a candidate name, not an existing implemented protocol. Preserve strict CAS, replay, scan and tombstone guarantees; no arbitrary schema or hash edits under unchanged contract semantics.

UI and script call one controlled server operation. Preconditions: app-scoped admin, expected current version, supported target schema/contract, compatible storage state. UI displays current/target version, impact and validation errors; script supplies same preconditions and reads back final version.

Migration requirements: safe retry/idempotency, concurrent migration exclusion, writer fencing, crash/interruption recovery and publish-last contract activation. Readers/writers never see new contract paired with partially transformed data. Preserve collection identity, row IDs, revisions, tombstones and replay receipts. Define rollback/recovery before production cutover; no destructive fresh-epoch reset.

Offline old clients cannot simply be drained globally. Old-version writes must fail clearly without deleting local intentions. Upgraded client must distinguish never-sent vs uncertain/accepted legacy operations and reconcile legacy receipts with their original digest semantics before translating safe remaining intentions. Never rehash a frozen accepted operation under new semantics or blindly assign a fresh opId to an uncertain operation.

Detailed transition algorithm/version-aware replay handling is required planning work; no existing safe mechanism claimed. Do not deploy registry schema additions alone or enable unrestricted strict updates.

## Touchpoints

- Shared: `contract/` task/actions/UI state/account-store/strict-sync; `data/` entities, DAO, Room version/migrations/exported schema; `sync/` DTOs, coordinator, staging, reconciliation and account-leave protocol; `ui/todo-editor.kt`, `ui/todo-list.kt`, `ui/tabbyrush-app.kt` and settings surface.
- Android: manifest, application startup/runtime, session view-model, activity permission flow, alarm/restore receivers, notification channel/adapter, account cancellation gate. Existing WorkManager remains sync-only.
- Desktop: controller/window lifecycle in `main.kt`, account scheduler, target-specific Linux/Windows notification adapters and packaging/dependencies where required.
- Hub server: sync plugin `models/strong_sync.rs`, `models/app.rs`, `models/api.rs`, `services/app_service.rs`, `services/strong_sync_service.rs`, storage admission and routes; version-aware migration operation/state.
- Hub UI: schema service/types, admin edit-app and schema-editor version-aware display/migration action; shared operator feedback.
- Scripts/specs/docs: canonical TabbyRush schema, provisioning/compatibility scripts, controlled migration client, operator runbook and Android/Linux/Windows acceptance docs.
- Existing tests: shared storage/sync/digest/migration suites, server contract/storage/replay suites, Hub admin UI tests where applicable. Add permanent regressions for uncertain behavior, not wiring assertions.
- External systems: deployed Hub service, MongoDB registry/app records, Android OS permissions/alarms, Linux session bus/notification daemon, Windows shell/installer identity. No new cloud notification service.

## Success metrics and acceptance gates

1. Create/edit/clear deadline and rule offline; restart preserves values; unsaved edit/cancel never changes schedules. Existing records survive Room migration with no deadline/reminder.
2. Preview matches shared arithmetic; count 1/10 and minimum interval accepted; count 0/11, negative lead and schedule past anchor rejected. Date-only 09:00 anchor does not mark overdue that morning.
3. Timezone/DST behavior explicit; same saved instant on Android/Linux/Windows; changing timezone/default/title never silently moves it.
4. Same supported strict upgrade succeeds through script and UI; both invoke same server semantics. Unauthorized, stale expected-version and unsupported schema/version requests rejected without mutation.
5. Interrupt/retry migration through both entry points; concurrent requests cannot corrupt state; no partially active contract. Preserve existing data, row revisions, tombstones and legacy replay results.
6. Offline old client returns after upgrade: incompatible write clearly blocked, local unsynced edits retained. Upgrader handles never-sent and uncertain/accepted legacy operations without duplicate creation, lost intention or digest reinterpretation. Rust/Kotlin extended digest vectors agree.
7. Rules round-trip Android ↔ Linux/Windows, including null/removal, completion/deletion and conflicts; stale clients cannot erase new fields.
8. Actual Android notification observed offline after ordinary process kill and screen-off/Doze; restore after reboot/unlock without opening UI. Document observed lateness/cross-task quota behavior, not assert perfect exact delivery.
9. Notification denial/channel off produces blocked status; exact access denial produces degraded timing. Grant/revoke and reopen restore correct state. Force-stop limitation explained.
10. Actual packaged Linux and Windows notifications observed with window visible and minimized; X exits with no subsequent process-scheduled posting. Prove native service/identity integration, not only successful API invocation.
11. Complete/delete/change rule/device disable/logout: obsolete occurrence cannot post; duplicate callbacks suppressed in normal operation; race with account wipe fails closed. Remote completion cancellation proven after sync only.
12. Delayed callbacks and suspend/resume: latest opportunity within five-minute grace posts once per task; older ones skip; future ones retained; no burst. Exactly-once across crash/post boundary not claimed.

## Risks and dependencies

Primary risk: sync-contract evolution and offline uncertain operations; notification UI is not the hard part. Needs coordinated Hub deployment, MongoDB operator procedure and TabbyRush release across platforms.

Other risks: app-wide Doze quotas/OEM restrictions, blocked notifications/DND, Windows packaged notification identity/binding, Linux service absence, scheduler/posting crash boundary, timezone/DST ambiguity, stale remote completion offline.

Detailed plan must put migration/replay design and native notification proofs before broad UI implementation. Separate platform validation evidence; this Linux host alone cannot prove Windows delivery.

## Next steps

Offer detailed cross-repository implementation plan. No code until contract transition and platform proof gates planned. Plan must remain pending until approved/executed; this report is not implementation completion evidence.

## Unresolved implementation questions

- Exact strict version identifier, wire/admission transition, migration recovery state and offline legacy-receipt reconciliation algorithm. Must be designed and validated before migration deployment.
- Windows notification binding/installer identity details and supported Linux runtime matrix. Must pass native packaged-runtime proof; existing dependencies alone insufficient.

No outstanding product-scope choices; no timeline requirement provided.

## Primary sources

- Android alarms: https://developer.android.com/develop/background-work/services/alarms
- Idle exact-alarm restrictions: https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent)
- Doze: https://developer.android.com/training/monitoring-device-state/doze-standby
- Notification permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Linux notifications: https://specifications.freedesktop.org/notification/latest/protocol.html
- Windows desktop toast identity (documented Win32 route): https://learn.microsoft.com/en-us/windows/win32/shell/enable-desktop-toast-with-appusermodelid
- Java tray API caveats (not selected as universal notifier): https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/TrayIcon.html
