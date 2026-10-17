# TabbyRush sticky task surfaces — agreed brainstorm

- Status: solution approved; no implementation authorized by this report.
- Deliverable: this decision report. Future feature artifact: Android home-screen task widget, optional next-deadline notification, one desktop always-on-top task window and tray.
- Decision basis: user discovery/approval, two completed read-only platform scouts, targeted source inspection, official platform documentation. No code changes or runtime validation.

## Problem and agreed requirements

Expose existing TabbyRush tasks without requiring the full app to remain visible. These are task surfaces, not a new free-text note feature.

### Shared behavior

- Current active account only. No independent account selection per surface.
- Widget and sticky window: Incomplete section above Completed section, in one scrollable list. Preserve existing task ordering inside each section.
- Toggle completion both ways. Quick-add follows current task creation and validation rules.
- Android quick-add opens a small app creation screen; desktop quick-add lives in the compact window.
- Existing local-first persistence and strict-sync mutations remain authoritative. No separate task database, backend endpoint, protocol change, or database schema migration.
- Update after successful local mutations and background sync. Respect existing network/background scheduling; no real-time cloud or exact refresh timing guarantee.
- Logout/account replacement clears old-account task content. Stale external actions must not mutate a replacement account.

### Android home-screen widget

- Jetpack Glance app widget, not an overlay above other applications.
- Scrollable task sections with completion controls and Add/Open entry points.
- Responsive sizing using launcher-provided bounds. Regular Compose app UI cannot simply be embedded in Glance.
- Render from account-safe local state; never depend on an existing Activity/ViewModel or an already-restored in-memory session.
- Apply the same active-account behavior to launcher-created widget instances; no per-widget customization requirement.

### Android next-deadline notification

- Optional, quiet ongoing notification; one selected incomplete task, not a scrolling list or count summary.
- Selection: oldest overdue task first; if none overdue, earliest upcoming deadline. No notification if no incomplete task has a deadline.
- Task completion/removal/deadline edit or sync recomputes the selected task.
- Actions: Complete and Open task. Quick-add remains in widget/app.
- Separate channel and identity from reminder alerts; enabling this surface does not enable deadline reminder alerts.
- Proposed implementation detail: deadline eligibility does not require the reminder rule to be enabled. Do not reuse `isEligibleForScheduling` blindly: that predicate specifically requires enabled reminders.
- Reuse existing persisted deadline/time-zone semantics, including date-only deadlines. Proposed deterministic tie-break: task ID. Do not reimplement deadline calculations from display strings.
- Notification permission/channel denial is an explicit unavailable state, not a fatal error. Request permission through a real runtime flow where required.
- Android 14+ permits users to dismiss most ongoing notifications. “Always present” is not a guarantee. Respect dismissal; no immediate repost loop.
- Use ordinary notification posting and supported background refresh mechanisms. No foreground service solely to pin this static task display; no exact-alarm permission added solely for this surface.
- Preserve current reminder privacy treatment: task title private on lock screen, generic public text. A home-screen widget intentionally exposes task titles on the launcher.

### Desktop window and tray

- Exactly one sticky task window per running TabbyRush profile, alongside the normal main window. Same process, controller/runtime, database, and single-instance guard.
- Movable/resizable, always-on-top compact window. Not attached to wallpaper or integrated with Windows Sticky Notes storage.
- Main-window Close hides main UI. Sticky-window Close hides sticky UI. Neither closes the account database or stops sync.
- Tray commands: Open app, Show/Hide sticky, Quit. Quit performs actual scheduler/runtime teardown and process exit.
- Restore sticky visibility, size, and position on manual app launch; keep restored bounds reachable after monitor changes. Store presentation preferences locally, not in task sync/schema.
- No sign-in auto-start.
- Android and Windows first. Linux implementation/validation is a separate phase on another machine; not implicitly certified by Windows success.

## Scope boundary and constraints

In scope: task presentation, completion/quick-add entry points, refresh integration, current-account isolation, notification opt-in/permission, desktop lifecycle/tray/geometry, validation and affected documentation.

Out of scope: free-text notes, rich text, multiple desktop sticky windows, per-surface task selection/saved filters, floating Android overlays, iOS/macOS, wallpaper integration, new backend/schema, sign-in auto-start, broader reminder-system redesign, guaranteed uninterrupted notifications or instantaneous remote sync.

Constraints: keep current Android compatibility (source declares minSdk 26, compile/target 35); Kotlin 2.1.10 and Compose Multiplatform 1.7.3 are current catalog versions. Select compatible stable Glance dependency during implementation rather than adopting latest alpha APIs. No fixed deadline supplied. Linux machine/desktop session required for its later acceptance.

## Evaluated approaches

| Approach | Advantages | Costs / risks | Decision |
| --- | --- | --- | --- |
| Glance widget + existing Android notification APIs + Compose Desktop window/tray | Fits current Kotlin stack; scrollable widget and action APIs; desktop uses existing controller | Glance-specific UI; explicit account-safe refresh; desktop lifecycle refactor | Approved |
| Classic Android RemoteViews/XML + same desktop architecture | Native widget capabilities; direct lower-level layout control | More XML/adapters/manual state plumbing; no requirement demands it | Viable, not selected |

A separate desktop companion executable is unnecessary: duplicates account/session/runtime ownership and complicates local database locking. Sharing one rendered UI across launcher widget and desktop is also the wrong boundary. Share task behavior/projection, keep platform presentation native.

## Architecture and implementation considerations

### Source of truth and updates

- Keep existing `AccountStore` mutation paths so completion/add preserve outbox, sync, account lifecycle, and reminder invariants. Never write task DAO rows directly from a widget.
- `loadTodos()` is currently a suspend snapshot query, not a task-list Flow. Reminder schedule observation is not a general task-list observation API.
- Make local mutation, sync completion, account transition, and startup restoration explicit refresh touchpoints. Avoid Activity-only hooks or a permanent polling loop.
- Desktop main/sticky consume the same authoritative task state; sticky grouping must not inherit the main screen's Active/Completed filter accidentally.
- For OS-visible deadline selection, use committed persisted task state plus saved pending local changes, not unsubmitted editor drafts. Existing persisted schedule projection explicitly excludes uncommitted drafts; reminder delivery eligibility is a separate predicate.
- Re-evaluate notification after supported time/date/time-zone and lifecycle wakeups as well as data changes. A task can become overdue without a database write. OS background restrictions still apply; do not promise precise rollover while force-stopped.
- Notification completion must carry account/task identity and set completed explicitly; repeated stale Complete actions must not toggle back to incomplete.

### Lifecycle and security

- Android session restore is asynchronous. Cold-start widget/action/worker paths must wait for safe account readiness and honor wipe markers/generation barriers.
- Extend normal leave and pending-wipe recovery cleanup to the new task notification and OS-retained widget content. Do not open a different account runtime as a convenience for stale actions.
- On desktop, move final controller disposal to application Quit, not visibility changes or main-window close. Existing process-scoped reminder behavior changes with this lifetime; update its documentation and validation.
- Linux tray support and window-manager always-on-top/focus behavior vary by environment. Verify actual target desktop session. Never hide every recovery control when no usable tray exists; retain a visible path to restore or quit.
- Always-on-top is normal desktop stacking behavior, not precedence over secure OS screens or every exclusive-fullscreen application.

## Existing touchpoints

| Surface | Relevant existing paths | Reason |
| --- | --- | --- |
| Android startup/account | `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt:22-134`; `account-runtime-factory.kt`; `android-account-state-manager.kt` | Active runtime, asynchronous restore, wipe recovery |
| Android task actions/navigation | `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:291-345,442-471`; `main-activity.kt` | Existing task mutation/reload and account-checked task navigation |
| Android sync | `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-sync-worker.kt:11-76`; `android-sync-scheduler.kt` | Background reconcile, per-account work, future refresh hooks |
| Android notifications | `androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-adapter.kt:75-142`; `android-notification-intent-factory.kt`; `reminder-permission-controller.kt` | Reminder-only channel/identity today; reuse patterns, not reminder-specific ownership |
| Android registration/build | `androidApp/src/main/AndroidManifest.xml`; `androidApp/build.gradle.kts`; `gradle/libs.versions.toml` | Add widget component/metadata and compatible dependency; no widget exists today |
| Desktop application lifetime | `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:766-835` | Current Close disposes controller and exits; one Window today |
| Desktop state/actions | `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:163-236,295-405`; `single-instance-guard.kt`; `desktop-reminder-scheduler.kt` | Runtime reuse, state reloads, profile lock, process scheduling |
| Shared storage/contracts | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-store.kt`; `todo-models.kt`; `persisted-task-schedule.kt`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt` | Task projection/mutations and existing deadline semantics |
| Shared presentation | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt`; `todo-editor.kt`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt`; `todo-action.kt` | Reuse task behavior and suitable presentation parts; avoid coupling filters |
| Account teardown | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol.kt`; Android `composite-leave-scheduler-cancel-gate.kt` | Quiescence, stale-action fencing, external-content cleanup |
| Acceptance/docs | `docs/android-acceptance.md`; `docs/windows-acceptance.md`; `docs/linux-acceptance.md`; `docs/system-architecture.md`; `docs/project-changelog.md` | Record new surfaces, close/tray behavior and platform evidence |

No external service migration or database tooling required. Research helpers: mobile-development/docs-seeker guidance and two read-only scout agents. Implementation planning is a separate opt-in command; no implementation agents have been authorized.

## Success metrics and observable acceptance

1. Android launcher shows Incomplete then Completed; scrolling reaches both sections at supported sizes. Completing/reopening moves a task between sections without duplicates.
2. Widget quick-add opens creation UI; a saved offline task appears locally and follows existing sync when connectivity returns. Desktop quick-add produces the same persisted task behavior.
3. Main UI and pinned surface converge after local mutation and completed background sync, including title/deadline edits and deletion. No new refresh-time SLA.
4. With two overdue tasks and one future task, notification selects the oldest overdue; completing it selects the next overdue, then the future task. No eligible task means notification removed. Include date-only and equal-deadline cases.
5. Complete action survives process recreation, cannot mutate another account, and cannot reopen a task when delivered twice. Tap opens the selected task in the correct account context.
6. Permission denial, disabled notification channel, and user dismissal do not break the widget or task actions. Reminder alert settings remain independent.
7. Process death/restart restores safe local content; logout/wipe removes old-account widget content and task notification. Stale pending actions cannot resurrect wiped state.
8. Windows sticky remains above ordinary application windows; exactly one sticky and one tray icon exist. Repeated Show never duplicates the sticky window.
9. Closing main or sticky hides only that UI; tray can restore each. Task changes/sync remain available while main UI is hidden. Quit terminates process and releases runtime/profile lock.
10. Manual relaunch restores sticky visibility/geometry; disconnected monitors do not strand the window off-screen. No auto-start registration is created.
11. Linux acceptance repeats relevant desktop scenarios on the target machine, recording desktop/session, tray availability, focus/stacking behavior, and restore behavior. Until then Linux is unverified, not complete.
12. Existing task persistence, sync, account isolation, and reminder behavior remain valid. Test behavioral boundaries above; verify actual launcher, notification shade, window, and tray surfaces, not just unit tests.

## Feasibility evidence and limits

- [Android Glance UI](https://developer.android.com/develop/ui/compose/glance/build-ui): LazyColumn supports scrollable widget content; responsive size modes available.
- [Glance interactions](https://developer.android.com/develop/ui/compose/glance/user-interaction): activity launch and callback actions support quick-add entry and task mutations.
- [Android 14 ongoing notification changes](https://developer.android.com/about/versions/14/behavior-changes-all): ongoing flag does not guarantee non-dismissible task notification.
- [Compose Desktop windows/tray](https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html): multiple windows, visibility control, WindowState, and tray integration.
- [Desktop Window API source](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/desktopMain/kotlin/androidx/compose/ui/window/Window.desktop.kt): alwaysOnTop API exists; exact pinned-version behavior remains an implementation check.
- Repository evidence: Android minSdk 26; no existing widget or ongoing task notification. Desktop currently exits on main Close. Shared task store has no task-list observation Flow.
- Exact Glance/Kotlin/Compose dependency combination, device rendering, Windows tray/stacking, and Linux session behavior were not built or exercised. Architectural feasibility supported; runtime acceptance still required during implementation.
- Documentation helper ran but Context7 had no matching documentation; official Android and Kotlin documentation used instead.

## Next steps and dependencies

1. User declined detailed implementation planning; brainstorm complete. No plan command invoked.
2. If planning is requested later, use this report for shared behavior/account safety, Android surfaces, Windows window/tray lifetime, then separate Linux machine acceptance. These are boundaries, not an implementation plan or authorization to code.
3. Implementation must verify compatible Glance dependencies, actual Android launcher/notification behavior, and Windows lifecycle/geometry; later Linux needs its machine and desktop session.

## Unresolved questions

No blocking product questions remain. Non-blocking implementation details: compatible Glance release, target Linux distribution/desktop/session, and exact platform-supported notification rollover scheduling. Proposed deadline eligibility/tie-break details above should be carried into future plan validation. User declined detailed planning for this session.
