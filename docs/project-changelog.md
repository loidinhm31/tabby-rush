# TabbyRush Project Changelog

All notable changes to the TabbyRush project are documented in this file, organized chronologically by implementation phase.

---
## [Server Admin Announcements / Phases 01–12] - 2026-10-18: Cross-System Delivery & Acceptance

### Delivered
- **Backend & Storage**: Canonical MongoDB collections (`admin_announcements`, `admin_announcement_inbox`, `admin_announcement_dispatches`, `notification_install_bindings`) separate from task sync. 90-day retention with BSON DateTime TTL indexes in MongoDB (`expiresAt = createdAt + 90 days`); MongoDB background TTL deletion is asynchronous, while application query and rendering filters strictly exclude records when `expiresAt <= serverNow` (only unexpired records are queryable or renderable); reading never extends expiry.
- **Android Push Transport**: FCM HTTP v1 generic notifications for Android with channel `tabbyrush.remote.announcements` and system tag `remote:<messageId>`, maintaining announcement-content isolation from push providers (fixed generic copy "You have a new announcement" and opaque IDs). 32-byte install proof encrypted in app-private storage via Android Keystore-backed AES-256-GCM key (`tabbyrush_remote_install_v1`), SHA-256 hashed on Hub, with monotonic numeric generation CAS fencing.
- **Desktop Streaming Broker**: Streaming SSE broker with `streamEpoch`, monotonic `acceptanceOrdinal`, and ready baseline (`event: ready`). Hints arriving after baseline hydration trigger authenticated detail fetching. Desktop displays at most one generic system alert per active connection and message hint; Android suppresses SSE alerts.
- **Client In-Memory Isolation**: TabbyRush clients store announcements exclusively in memory (sliding window max 200 items, dedup capacity 2048) managed by `RemoteInboxController`. Zero Room database migrations, persistent offline content, or read outbox; process termination purges cached items.
- **Admin Web Console**: React admin console (`AnnouncementComposer`, `AnnouncementHistory`) with scalar/byte counters, preview digest confirmation gating, audience mode selection (`selected` up to 500, `all`, `admins`), and idempotency replay protection.
- **Acceptance & Tooling**: Standalone Node.js smoke runner `scripts/admin-announcements-smoke.mjs` verifying contract-v1.json, 13 golden vectors, idempotency, monotonic readAt timestamps, and install proof/fencing via in-process mock server (`--self-test`, 21/21 assertions PASS); Playwright browser suite `scripts/admin-announcements-browser.spec.ts`; cross-system acceptance matrix `docs/evidence/admin-announcements-acceptance.md`.

### Verification & Acceptance Status
- **Automated Verifications**: QA regression report (`qa-261018-1548-c1-c6-regression-verification.md`) records 566 verifications passing (548 test executions across 8 targets + 18 vector/static checks); Cycle 2 code review approved at Score 8/10 with 0 critical issues.
- **Restricted Pilot Gate (Android/Linux/Web Admin)**: APPROVED as recorded in acceptance evidence. Common safety, server durability, Android FCM runtime, Linux D-Bus actions adapter, and Admin Web console verified for internal cohort pilot (selected mode <= 500 recipients).
- **Full Rollout Gate (Windows Desktop Runtime)**: HELD at Gate G5 on Linux build host; Windows C ABI v1 `tabbyrush-toast.dll` and WiX3 MSI packaging implemented in source; full rollout requires clean-host Windows proof.
- **Inherited Release Holds**: Release holds G2/G3/G6/G7, 149-checksum baseline, and Glance provenance remain open.
---
## [TabbyRush Rebrand & Mascot App Icon] - 2026-10-17: TabbyRush Identity & Mascot App Icon

### Delivered
- Refactored application branding from "TabbyRush" to "TabbyRush" across Android launcher labels (`android:label="TabbyRush"`), Compose string resources (`app_name="TabbyRush"`), Glance widget titles (`TabbyRush Tasks`), notification channels (`TabbyRush Task Reminders`), desktop window titles (`TabbyRush` / `TabbyRush - Tasks`), tray tooltips (`TabbyRush`), and desktop packaging metadata (`TabbyRush` on Windows / `tabbyrush` on Linux).
- Refactored environment configuration to `TABBYRUSH_API_KEY` across Android Gradle build config, Desktop launcher, and tooling scripts, removing legacy `TABBYRUSH_API_KEY`.
- Refactored target application ID to `tabby-rush` (`appId = "tabby-rush"`, CLI `--app-id tabby-rush`, `TABBYRUSH_APP_ID`), and documented MongoDB target collection as `appTabbyRush`.
- Generated official TabbyRush app icon across all platforms using user-provided illustration (`docs/assets/tabbyrush-source.png`):
  - Android Adaptive Icon: 432×432 px transparent foreground (`ic_launcher_foreground.png`) with mascot and milestone trail centered strictly within the 66dp safe circle (radius 124.08 px / 31.02 dp from center, 0 pixels clipped), paired with solid `#FFFFFF` vector background (`ic_launcher_background.xml`).
  - Desktop Runtime Icon: 256×256 px PNG (`tabbyrush-icon.png`) on white rounded squircle with subtle drop shadow and border.
  - Windows Multi-Resolution ICO: 7 embedded 32-bit RGBA frames (16, 24, 32, 48, 64, 128, 256 px) in `tabbyrush.ico`.
  - System Tray: 32×32 px high-contrast mascot silhouette (`task-surface-tray.png`) for Linux and Windows taskbars.
  - Master SVG: 512×512 SVG with semantic layers (`background-desktop`, `mascot-tabbyrush`) in `docs/assets/tabbyrush-app-icon.svg`.
- Updated deterministic asset generation script: `scripts/generate-app-assets.py`.

### Verification
- Automated Gradle test suites pass: `:desktopApp:test` (103/103 tests pass), `:androidApp:testDebugUnitTest` (36/36 tests pass), and `:shared:jvmTest` (239/239 tests pass).
- Hub strict-v1 live smoke fixture tests pass: `node scripts/hub-live-smoke.mjs --fixture-test` (18/18 checks pass).
- Release security checks pass: `node scripts/release-security-check.mjs --diagnose-source` (CLEAN) and `node --test scripts/release-security-check.test.mjs` (12/12 tests pass).

---
## [App Icon Design & Platform Delivery] - 2026-10-17: Canine & Task Completion App Icon

### Delivered
- Designed official TabbyRush app icon based on the "Companion Check" concept, unifying canine silhouette with a task-completion badge across brand palette tokens (`TabbyRushPrimary #004646`, `TabbyRushPrimaryDeep #003838`, `TabbyRushMint #27C4B5`, `TabbyRushCream #F7F4ED`, `TabbyRushWhite #FFFFFF`, `TabbyRushInk #0F172A`).
- Created canonical master vector graphic `docs/assets/tabbyrush-app-icon.svg` (512×512 SVG).
- Created Android adaptive icon layers (`androidApp/src/main/res/drawable/ic_launcher_background.xml` and `ic_launcher_foreground.xml`) and adaptive mipmaps (`androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`) adhering strictly to the 66 dp inner safe-zone circle constraint on the 108×108 dp viewport (zero pixels clipped under circular or squircle masks).
- Updated `androidApp/src/main/AndroidManifest.xml` to declare `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.
- Generated 256×256 desktop runtime icon `desktopApp/src/main/resources/tabbyrush-icon.png` and updated `MainWindow` and `StickyWindow` callsites in `main.kt` to bind the application icon.
- Generated 32×32 branded system tray icons for Linux and Windows (`task-surface-tray.png`) replacing generic indigo checkmark placeholder.
- Generated multi-resolution Windows packaging icon `desktopApp/src/windowsMain/resources/tabbyrush.ico` (embedding 16, 24, 32, 48, 64, 128, and 256px 32-bit RGBA frames) and configured packaging in `desktopApp/build.gradle.kts`.
- Added unit and asset decodability tests in `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/app-icon-assets-test.kt` and `androidApp/src/test/kotlin/dev/tabbyrush/android/app-icon-resources-test.kt`.

### Verification
- Automated unit test suites pass: `:desktopApp:test` (103 tests pass, including `AppIconAssetsTest`), `:androidApp:testDebugUnitTest` (36 tests pass, including `AppIconResourcesTest`), and `:shared:jvmTest` (239 tests pass freshly executed with `--rerun-tasks` and `--dependency-verification=off`).
- Android resource linking and compilation verified via `processDebugResources` and `mergeDebugResources`.
- Static release security auditor clean: `node scripts/release-security-check.mjs --diagnose-source` and Node test suite (12/12 passing).
---
## [Date & Time Picker Migration] - 2026-10-17: Calendar and Clock Pickers (Delivered; Technical Acceptance Conditional)

### Delivered
- Replaced manual date/time typing for task deadline date, deadline time, date-only reminder anchor, and settings default anchor with shared Material 3 calendar and clock controls.
- Added shared `DateTimePickerButton`, `TabbyRushDatePickerDialog`, `TabbyRushTimePickerDialog`, and `DateTimePickerFormats`; DatePicker mode toggle is disabled, and the clock dial has no text-input mode. Date/time fields have no typing path.
- DatePicker values normalize through UTC midnight; DatePicker is configured for years `0..9999`, and output remains canonical `YYYY-MM-DD` and `HH:mm`. Existing timezone/DST resolver behavior remains intact.

### Reported Verification & Acceptance
- Automated unit test suites pass (372/372 tests, with `:shared:jvmTest` freshly executed and passing 239/239 post-`yearRange = 0..9999` change; `:desktopApp:test` and `:androidApp:testDebugUnitTest` passing; Android `assembleDebug` passing; 32/32 Node security/smoke checks passing).
- This documentation pass did not rerun those checks. Rendered Compose interaction tests and physical device/accessibility acceptance remain pending, so technical acceptance is conditional and the relevant gate stays open.

---
## [Sticky Task Surfaces / Phase 07] - 2026-10-17: Android/Windows Acceptance & Shared Documentation (Delivered; Native Gate G2 Conditionally Held)

### Delivered
- Updated shared project documentation and added Android/Windows acceptance summaries and a Phase 07 evidence report.
- Recorded 6h / 12h credited for Phase 07; project progress is 118h / 132h (89.4%).
- Four PNGs in `docs/evidence/` are visual design baselines/design mockups only, not screenshots or runtime OS captures.

### Reported Automated Evidence
- The Phase 07 report records 364 Gradle tests passing (231 shared, 99 desktop, 34 Android), 12 Node auditor tests passing, six source diagnostics clean, 18 Hub fixture checks passing, and eight strict-v2 rehearsal checks passing.
- The report records Android `assembleDebug` and Windows distributable builds as successful. Gradle commands used `--dependency-verification=off`; this is recorded evidence, not a fresh rerun for this changelog.

### Preserved Holds & Safety Disclosures
- Android and Windows native interaction acceptance remains conditionally held at Gate G2. The native WinRT/COM toast bridge remains a candidate stub; installed MSI system acceptance is also held.
- The 149 unmatched classpath checksums and the separate Glance provenance hold remain unresolved. Linux session/tray acceptance is pending Phase 08. Production release gates G3, G6, and G7 remain open.
- Android `TabbyRushApplication.closeRuntimeInternal()` catches and logs a delivery-gate drain timeout, then returns before observer-job cancellation or Room closure. `closeActiveRuntime()` nevertheless clears `activeRuntime`, leaving jobs active and the Room database open. This remains an open safety finding.
- Glance cleanup error swallowing remains an open safety finding; no out-of-boundary code changes are claimed.

---

## [Sticky Task Surfaces / Phases 01–06] - 2026-10-16: Android Glance, Quiet Deadline Notification & Desktop Sticky Window (Delivered; Cycle 2 Conditional Approval)

### Delivered
- Completed shared account-token admission, task observation, desired-state completion mutation, and surface projections; connected them to the Android Glance task widget and opt-in quiet deadline notification.
- Delivered the compact Compose Multiplatform desktop sticky window with tray controls when supported by the host session. Android and desktop preferences remain local presentation/notification state, outside task synchronization.
- Integrated account lifecycle cleanup for task-surface notifications, boundary work, widget content, Glance sessions, and Android surface preferences.

### Review & Reported Verification
- Cycle 2 review scored **8.8/10 (Conditional Pass)** with 0 critical issues and 3 warnings. It records 363/363 Gradle tests (231 shared JVM, 98 desktop, 34 Android unit), 12/12 Node security auditor tests, 6/6 diagnostic source security checks, 18/18 Hub live-fixture smoke checks, and 8/8 strict-v2 rehearsal checks. These are report-attributed results, not fresh runs for this changelog entry (`plans/reports/code-review-261016-2157-sticky-task-surfaces-cycle-2.md`).
- Cycle 2 review warnings include Android `closeRuntimeInternal()` catching/logging a drain timeout and returning before observer-job cancellation or Room closure while `closeActiveRuntime()` clears `activeRuntime`; Glance blanking and session-close failures can also leave OS-retained content.

### Acceptance & Release Holds
- At the time of this 2026-10-16 entry, Phase 07 Android/Windows integration was ready for acceptance recording, not claimed as native acceptance passed. Current Phase 07 status is recorded above; Linux acceptance remains pending a real session in Phase 08.
- The 149-checksum dependency-verification hold and Glance provenance gap remain. Historical release holds G2/G3/G6/G7 remain unchanged.

---

## [Sticky Task Surfaces / Phase 01] - 2026-10-16: Shared Foundation & Contracts (Delivered; Conditional Handoff Authorized; Dependency Provenance Pending)

*Note: Separate initiative from the Task Deadlines, Reminders & Strict Upgrades plan.*

### Delivered
- Added shared authoritative task-surface foundations: `AccountStore.observeTodos()`, desired-state `setCompletedLocally`, access/readiness/section contracts, and deadline/partition projections.
- Added Android task-surface host contracts and desktop sticky task action/state contracts.
- Selected Glance 1.2.0 and delivered Android dependency/build and lockfile foundations.
- Resolved the Cycle 2 server-tombstone/local-draft-or-outbox overlay finding. `setCompletedLocally` now returns `Missing` for authoritative tombstones; residual-draft and residual-outbox SQLite regressions pass.

### QA & Review
- QA reports 307/307 Gradle tests passing (231 shared JVM, 64 desktop, 12 Android unit), plus 18/18 offline fixture smoke checks, 6/6 source-security diagnostics, and 8/8 strict-v2 cutover self-test checks.
- Cycle 3 code review scored **9.4/10 (Conditional Pass — Terminal Handoff Authorized; External Provenance Gate Pending)** with no critical issues identified.
- Terminal conditional handoff unblocks Phases 02, 03, and 04; their leaf implementations and native acceptance remain outstanding.

### Open Gates
- Glance artifact provenance is not verified in `gradle/verification-metadata.xml`; Gradle runs used `--dependency-verification=off`. Phase 01 evidence reports **149 unmatched root classpath checksums** ([foundation evidence](../plans/261016-1409-sticky-task-surfaces/reports/phase-01-foundation-evidence.md)).
- Native widget, notification, desktop window/tray, and Linux-session proof remain downstream acceptance requirements; no runtime/native success is inferred from shared tests.

---

## [Phase 12] - 2026-10-16: Coordinated Cutover Runbook, Rehearsal Tooling & Release Documentation (Runbook & Tooling Delivered; Live Production Cutover Blocked Pending Gates G2, G3, G6, G7)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative.*

### Added
- Authoritative Strict-v2 Coordinated Cutover & Recovery Runbook (`docs/strict-v2-cutover-runbook.md`):
  - Complete operational runbook detailing exact deployment ordering: (1) all-node Hub backend binary deployment, (2) Room v2 TabbyRush client distribution, (3) non-mutating preflight dry-run, (4) controlled in-place upgrade via `hub-strict-upgrade.mjs` or Web Admin UI with durable migration UUID, (5) mandatory post-cutover read-back assertion of all 16 columns and canonical v2 fingerprint, and (6) client replay and transition.
  - State-specific incident decision matrix covering pre-fence rejection, orphan permit stalls (keep fenced, escalate), interrupted batch (idempotent resume with same migration ID from checkpoint), lost publish response (read-back verify), preactivation rollback under fence (strip added fields only), and post-v2 forward repair rule (never restore v1 backup or downgrade binaries once v2 is published).
  - Emergency disaster recovery quarantine: explicitly quarantines `scripts/hub-strict-recovery.js` as an emergency cold restore tool for `strict-v1` only, forbidding its use for v2 upgrades or rollbacks.
- Strict-v2 Operator Tooling Rehearsal Suite (`scripts/strict-v2-cutover-rehearsal.mjs`):
  - Dedicated executable rehearsal and self-test harness verifying CLI parameter checking, missing token fail-fast, sensitive token redaction across stdout/stderr, non-mutating preflight dry-run, interrupted POST recovery, status-only query without mutation, and final schema read-back assertion.
  - 7/7 checks PASS in automated tooling self-test (`node scripts/strict-v2-cutover-rehearsal.mjs --self-test`).
  - Includes gated staging rehearsal mode (`--staging-origin` with `--confirm-disposable-staging`).

### Changed
- Reconciled operational and acceptance documentation:
  - `docs/deployment-guide.md`: Added Section 9 documenting strict-v2 deployment ordering, operator upgrade commands, and dual-auth app-scoped requirements.
  - `docs/security-and-recovery.md`: Added Section 4 delineating feature migration in-place transform from emergency fresh-epoch disaster recovery, documenting recovery rules for all migration states.
  - `docs/release-acceptance.md`: Added Gate G7 (Coordinated Cutover, Recovery Runbook & Release Gate) to formal operational gates and included rehearsal self-test suite (404 total verification targets across 15 groups: 400 automated checks with 399 passed, 0 failures, 1 ignored live Mongo gate; plus 4 build, lint, and packaging targets).
  - `docs/hub-schema-integration.md`: Documented Phase 12 cutover runbook and rehearsal tooling.
  - `docs/sync-acceptance.md`: Added strict-v2 migration and replay acceptance section.
  - `docs/android-acceptance.md`, `docs/linux-acceptance.md`, `docs/windows-acceptance.md`: Updated Gate G2 and cutover release status.
  - `docs/system-architecture.md`: Added Section 6.13 covering Phase 12 architecture, runbook, and rehearsal tooling.
  - `README.md`: Added rehearsal script and cutover runbook references.
  - Glean Hub `docs/tabbyrush-strict-sync.md`: Documented Phase 12 cutover runbook and corrected `--status-only` distinction.

### Gate & Release Status
- Phase 12 runbook, rehearsal tooling, and documentation reconciliation delivered (12h credited, completing all 236h of planned implementation effort).
- Live production cutover (Gate G7) remains formally **BLOCKED** pending:
  1. Gate G3 multi-instance MongoDB replica set migration execution and resolution of three Phase 03 code findings (permit release duplicate filter, `permitCount` backfill, and batch/checkpoint write atomicity).
  2. Gate G2 physical native hardware delivery (connected Android Doze/reboot, Linux desktop graphical bubble, Windows native WinRT/COM toast bridge).
  3. Gate G6 three-device concurrent live sync proof between Android, Linux, and Windows.
  4. Strict supply-chain dependency verification metadata regeneration.

---
## [Phase 11] - 2026-10-16: Adversarial Boundary Regressions, SQLite Crash Invariance & Platform Acceptance (Automated Regressions Approved; Live Platform & Cluster Acceptance Held; Code Review Score 8.6/10)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative.*

### Added
- Adversarial scheduler boundary regression suite (`AdversarialSchedulerBoundaryTest.kt` in `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/`):
  - Verified repetition limit bounds (1..10, min interval 15m, non-repeating interval null).
  - Verified checked 64-bit integer arithmetic overflow rejection (`safeMultiply`, `safeSubtract`, `safeAdd`).
  - Verified date-only 09:00 morning non-overdue calculation via production `DeadlineCalculatorHelpers.validateAndComputeDeadline`.
  - Verified exact 5-minute grace window evaluation: 300,000ms passes, 300,001ms skips without catch-up burst or retroactive delivery.
  - Verified editor draft isolation: drafts in `editor_drafts` never project into active schedules.
  - Verified cancellation barriers: completed, deleted, and disabled tasks immediately clear projected schedules.
  - Verified schedule token preservation across non-temporal edits vs advancement on reminder updates.
- SQLite crash boundary and frozen retry persistence suite (`AdversarialSyncSqliteTest.kt` in `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/`):
  - Real disk-backed SQLite database close and reopen cycles verifying bit-for-bit retention of all 12 temporal fields and schedule tokens.
  - Frozen retry invariance: network timeouts replay nonces idempotently without duplicate commits.
  - Cross-contract intent rebase: legacy v1 title-only modifications rebase over remote v2 deadlines without field erasure.
  - Conflict lock durability: `UNKNOWN_SUPERSEDED` conflict locks survive database restarts.
- Debug fault-injection proxy (`scripts/debug-fault-proxy.mjs` and `scripts/debug-fault-proxy.test.mjs`):
  - Loopback proxy simulating dropped push and legacy-receipt responses via client socket aborts, upstream 502/504 errors, sensitive URL path redaction on 403 rejections, and strict allowlist enforcement (8/8 tests PASS).

### Changed
- `PersistedScheduleProjector`:
  - Fixed `INTENT_REMINDER` updates to assign `scheduleToken = op.scheduleToken` when `deadline != null && op.scheduleToken != null`, eliminating scheduler desynchronization on reminder-only modifications.
- Temporal Fixture Offsets:
  - Corrected America/New_York offsets for 2026-11-01 15:00 from `-14400L` (EDT) to `-18000L` (EST) across adversarial test suites to align with the 02:00 DST fallback.

### Verification and Delivery Status
- QA test execution report (`plans/reports/qa-261015-2330-phase-11-validation.md`) records 395 total automated checks/targets across 14 verification groups (394 PASS, 0 failures, 1 ignored live Mongo gate).
- Code review report (`plans/reports/code-review-261015-2345-phase-11-adversarial-acceptance.md`) approved at **8.6 / 10** for automated regressions.
- Gate G2 physical native delivery (connected Android Doze/reboot, Linux desktop notification bubble, Windows WinRT toast bridge candidate stub), Gate G3 live cluster execution (multi-node MongoDB replica set live migration and 3 open Phase 03 backend findings), and Gate G6 full platform acceptance remain formally held pending physical testbeds.

---
## [Phase 10] - 2026-10-15: Shared Deadline Editor, Task List and Local Settings (Implementation Delivered; Platform Acceptance & Windows Bridge Pending Phase 11; Code Review Score 9.2/10 Approved)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative; distinct from the historical 2026-10-14 Phase 10 Windows desktop MSI release.*

### Added
- Shared Compose Multiplatform UI components for task deadlines and reminders:
  - `dev.tabbyrush.shared.ui.DeadlineCalculatorHelpers`: Pure immutable calculation engine (`validateAndComputeDeadline`, `computeNextCalendarDayStartEpochMs`, `computeCalendarDayStartEpochMs`, `formatOffset`). Resolves local dates and times via `StrictTimeZoneResolver`. Explicitly separates DST gaps (non-existent local times rejected with validation error) from DST overlaps (ambiguous times resolved via explicit offset selection). Defensive parsing catches `IllegalArgumentException` on impossible calendar dates (Feb 30/31, April 31, non-leap Feb 29) to prevent Compose recomposition crashes.
  - `dev.tabbyrush.shared.ui.DeadlineEditorSection`: Shared Compose UI section providing date input, time toggle, explicit timezone and offset disambiguation filter chips, reminder rule enablement, lead minutes picker, repeat interval and count bounds (1..10, count > 1 requires interval >= 15m, non-repeating interval null, last occurrence <= anchor), and live calculated reminder opportunity preview.
  - `dev.tabbyrush.shared.ui.ReminderSettingsDialog`: Modal settings dialog displaying platform reminder capabilities card (`Exact`, `BestEffort`, `NotificationDenied`, `ChannelDisabled`), default reminder rule toggles, lead minutes, repeat count/interval, and date-only default anchor time picker (09:00 default).
- UI state and actions in `:shared`:
  - `EditorState`: Immutable state container capturing task identity, title, deadline date/time/zone/offset, reminder rule, original schedule token, and validation error messages.
  - `ReminderSettingsState`: Immutable state container for local reminder defaults, dialog visibility, and platform capabilities.
  - `TodoAction`: Added `UpdateEditorState`, settings actions (`OpenReminderSettings`, `DismissReminderSettings`, `SaveReminderSettingsDefaults`, `RequestReminderPermission`, `OpenPlatformReminderSettings`), and periodic `ClockTick` action.
- Common Compose string resources in `shared/src/commonMain/composeResources/values/strings.xml` for deadline inputs, timezone selectors, reminder intervals, overdue labels, and settings descriptions.
- Automated test suites:
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt`: 6 new tests added in Phase 10 QA (`testDeadlineValidationValidTimedAndDateOnly`, `testDeadlineValidationDstGapAndOverlap`, `testDeadlineValidationBoundsAndLastOccurrenceLimit`, `testOverdueLogicDateOnlyVsTimedAndCompleted`, `testReminderSettingsStateDefaultsAndCapabilities`, `testConflictReviewDetailedParameters`) plus 1 regression test added during code review (`testDeadlineValidationImpossibleCalendarDates`), totaling 15 tests.
  - `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt`: Added `testClearingDeadlineToServerBaseDoesNotResurrect` verifying that clearing deadlines against a populated server base does not resurrect deleted schedules.

### Changed
- `RoomAccountStore` in `:shared`:
  - Refactored `resolveTemporalProjection` hierarchy: evaluates local drafts and outbox mutations as a complete unit. Explicit null deadline fields in local state are treated as an intentional clearing of the deadline rather than falling back field-by-field to `server_base` (verified by `testClearingDeadlineToServerBaseDoesNotResurrect`).
  - Enforced `hasScheduleSemanticChange()`: editing task title or description preserves the existing `scheduleToken`; editing deadline date, time, zone, offset, or reminder rule generates a new UUID `scheduleToken`. (Task completion acts as an independent cancellation barrier in `PersistedScheduleProjector`).
  - Added `isAccountV2()` guard: verifies active account has completed transition to `strict-v2`.
- `TodoEditor` in `:shared`:
  - Integrated `DeadlineEditorSection`, submit validation, and strict-v2 upgrade requirement guard (`isUpgradeBlocked` disables submit with Hub admin upgrade message on strict-v1 accounts while permitting title and completion editing).
- `TodoList` and `TodoRow` in `:shared`:
  - Added date-only and timed deadline badges.
  - Overdue status is conveyed via non-color indicators: explicit "Overdue" text badge, distinct background styling, and accessibility `contentDescription = "Task is overdue"`.
  - Date-only tasks at anchor 09:00 do not become overdue during that morning; overdue boundary is strictly the start of the next calendar day in the captured timezone. Completed tasks never render as overdue.
  - Evaluates overdue status against periodic 15s `ClockTick` events without SQLite mutations or network sync.
- `ConflictReview` in `:shared`:
  - Extended comparison card displaying full side-by-side comparison of local vs remote deadline date, time, zone, offset, reminder rule parameters, and schedule-token preview.
- `SessionViewModel` in `:androidApp`:
  - Routed notification task selection through `openEdit(task)` to fully hydrate deadline and reminder fields from stored snapshot.
  - Added reminder settings dialog flow and 15s `ClockTick` ticker flow.
- `DesktopController` / `main.kt` in `:desktopApp`:
  - Added reminder settings actions, submit validation, and 15s `ClockTick` ticker flow.

### Critical Remediations Applied During Code Review
1. **[CRITICAL / DATA LOSS] Android Notification Selection Bypassed Deadline Hydration**:
   - `dispatchTaskSelection(accountKey, taskId)` previously constructed an unhydrated `EditorState(id = task.id, rawTitle = task.title)` with default null deadlines. Opening a task from an OS notification and saving title changes wiped all scheduled deadlines and alarms.
   - Remediated by changing `dispatchTaskSelection` to a suspend function routing through `openEdit(task)`, which fully populates temporal fields from the stored snapshot before opening the editor.
2. **[HIGH / RUNTIME CRASH] Impossible Calendar Dates Crashed Compose Recomposition**:
   - Typing invalid calendar days (e.g. Feb 30/31, April 31, non-leap Feb 29) passed `DATE_REGEX` but caused `LocalDate.parse()` to throw uncaught `IllegalArgumentException` during Compose `remember` recomposition in `TodoEditor`.
   - Remediated by wrapping date/time operations in try-catch in `DeadlineCalculatorHelpers.kt` returning validation errors. Added regression test `testDeadlineValidationImpossibleCalendarDates`.

### Verification and Delivery Status
- QA test execution report (`plans/reports/qa-261015-2245-phase-10-validation.md`) records 306 automated test/check assertions across 7 test suites, plus 2 packaging assembly builds (`:desktopApp:createDistributable` standalone bundle and `:androidApp:assembleDebug` APK build), totaling 308 checks (189 shared JVM tests across 26 classes, 49 desktop tests across 10 classes, 12 Android unit tests across 4 classes, 23 strict-v2 vectors/fingerprints, 18 Hub offline fixture smoke checks, 6 static release security checks, 9 Hub strict upgrade CLI tests).
- Code review report (`plans/reports/code-review-261015-2300-phase-10-compose-deadline-editor-list.md`) approved at **9.2 / 10**, adding 1 regression test (`testDeadlineValidationImpossibleCalendarDates`) in `TodoSemanticsTest` to bring shared JVM tests to 190 and total passing checks to 309 when executed.
- Gate G2 / Platform Acceptance: Compose UI, contracts, and platform controller integrations delivered in source and verified via unit tests; connected physical device / emulator execution, TalkBack/Orca accessibility verification, and live notification delivery remain scheduled for Phase 11 platform acceptance.

---
## [Phase 09] - 2026-10-15: Process-Scoped Desktop Scheduler, Native Adapters & Strict Quiescence (Gate G2 Desktop Logic Delivered; Physical Notification Display Pending Phase 11)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative; distinct from the historical 2026-10-14 Phase 09 Linux desktop distribution release.*

### Added
- Desktop reminder adapter Service Provider Interface (SPI) and dispatcher in `:desktopApp`:
  - `DesktopReminderAdapterProvider`: Service Provider Interface allowing target-specific native reminder adapter implementations (`linux` and `windows`) to be packaged in `desktopApp/src/<target>Main` and discovered via Java `ServiceLoader`.
  - `DesktopReminderAdapter`: Extension interface of shared `NativeReminderAdapter` providing `setScheduleListener()` for the desktop coroutine scheduler to receive reconciled desired alarms.
  - `PlatformReminderDispatcher`: Dispatches and initializes native reminder adapters based on detected host OS. Enforces strict target isolation: exactly one matching provider must be registered; zero, multiple, or foreign providers fail closed with an explicit `IllegalStateException`.
- Process-scoped desktop reminder scheduler (`dev.tabbyrush.desktop.DesktopReminderScheduler`):
  - Manages reminder scheduling while desktop window is visible or minimized without requiring background daemons or system tray processes.
  - Time-bounded 15,000ms wake loop (`withTimeoutOrNull`) on `Dispatchers.IO`: immune to backward wall-clock adjustments and system suspend/resume sleep without polling full task lists.
  - Replace-based alarm updates: desired alarm updates replace prior lists and wake the loop immediately via a conflated channel (`Channel.CONFLATED`).
  - Grace window evaluation: due alarms claim through `ReminderSchedulingCoordinator.deliverReminder()`, enforcing the durable 5-minute grace window check in SQLite before attempting native notification posting.
  - Cancellation quiescence: implements `LeaveSchedulerCancelGate`, cancelling the wake loop and awaiting job completion on account leave or wipe.
- Composite desktop cancellation gate (`dev.tabbyrush.desktop.CompositeDesktopLeaveCancelGate`):
  - Strict 5-step ordered quiescence during account leave (`AccountLeaveProtocol`): (1) closes `ReminderDeliveryGate` and drains in-flight delivery admissions with a 5,000ms timeout (fails closed with `IllegalStateException` on timeout), (2) cancels and joins reactive Room schedule observation, (3) cancels and awaits `DesktopReminderScheduler` wake loop, (4) cancels and awaits active `DesktopSyncScheduler` sync jobs, and (5) cancels and clears platform notifications.
  - Fail-closed invariant: any cancellation step failure aborts teardown, retaining `wipe.marker` and database files.
- Linux Freedesktop D-Bus reminder adapter (`dev.tabbyrush.desktop.linux.LinuxNativeReminderAdapter`):
  - Bridges shared `NativeReminderAdapter` to `org.freedesktop.Notifications` via `DbusLinuxNotificationAdapter` over `Dispatchers.IO` to protect Swing EDT responsiveness.
  - Capability tolerance: probes daemon capabilities and treats successful query as enabling notifications even if the capabilities array is empty, supporting minimal notification daemons (dunst, mako).
  - Account-scoped tracking: maps platform uint32 notification IDs to account keys in an in-memory set (`ConcurrentHashMap<String, MutableSet<Long>>`).
  - Close error propagation: `cancelAllForAccount()` aggregates daemon close errors and throws an explicit `IllegalStateException` on failure.
- Windows candidate toast adapter (`dev.tabbyrush.desktop.windows.WindowsNativeReminderAdapter`):
  - Candidate adapter configuring AUMID `dev.tabbyrush.desktop`, group prefix `d_${accountKey.value.take(12)}`, and tag `t_${taskId.take(16)}_$occurrenceIndex` with XML entity escaping on title and message text.
  - Multi-account isolation: `cancelAllForAccount()` iterates account-scoped identifiers and cancels group notifications rather than performing a global toast wipe.
  - Disclosed stub status: `JnaWindowsToastBridge` is an unintegrated candidate stub returning `WindowsNotificationStatus.Unavailable` for all operations; native WinRT/COM toast posting is deferred to Phase 11 on a Windows testbed.
- Automated test suites:
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-scheduler-test.kt`: 5 tests for wake loop, backward clock jump protection, replace-based updates, and clean cancellation.
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate-test.kt`: 2 tests for strict 5-stage quiescence and drain timeout failure.
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-controller-lifecycle-test.kt`: 3 tests for reminder subsystem init, delivery gate state, and fail-closed wipe marker retention on notification cancellation error.
  - `desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-reminder-adapter-test.kt`: 6 tests for capabilities, account-scoped tracking, replace ID mapping, and error propagation.
  - `desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-reminder-adapter-test.kt`: authored candidate unit test suite with fake bridge for toast formatting, multi-account isolation, and error propagation (unexecuted on Linux host).

### Changed
- `DesktopController` and `main.kt` in `:desktopApp`:
  - Integrated `ReminderDeliveryGate`, `ReminderSchedulingCoordinator`, `DesktopReminderScheduler`, and `CompositeDesktopLeaveCancelGate` into `DesktopAccountRuntime`.
  - Fail-closed startup wipe recovery: in `completePendingWipesOnStartup()`, exceptions during `reminderAdapter.cancelAllForAccount()` trigger `return@forEach`, retaining `wipe.marker` and database files fail-closed.
  - Single-window close lifecycle: `dispose()` executes `disposeSuspend()`, cooperatively cancelling observers, draining runtime gates, closing database files, and releasing `SingleInstanceGuard` file lock with zero surviving background daemon or tray processes.

### Verification and Delivery Status
- QA report records 298 passing automated checks across 7 test/check groups, plus 1 standalone packaging assembly task:
  1. `:desktopApp:test` (48 Linux/JVM unit & platform tests PASS across 10 classes, executed with `--dependency-verification=off`).
  2. `:shared:jvmTest` (182 unit/SQLite tests PASS across 26 classes, executed with `--dependency-verification=off`).
  3. `:androidApp:testDebugUnitTest` (12 unit tests PASS across 4 classes, executed with `--dependency-verification=off`).
  4. `node scripts/strict-v2-vector-runner.mjs` (23/23 vector runner checks PASS).
  5. `node scripts/hub-live-smoke.mjs --fixture-test` (18/18 offline smoke fixture checks PASS).
  6. `node scripts/release-security-check.mjs --diagnose-source` (6/6 static source security checks PASS).
  7. `node scripts/hub-strict-upgrade.test.mjs` (9/9 CLI tests PASS).
  - *Separate Packaging Build*: `:desktopApp:createDistributable` (1 standalone application bundle assembly verified).
  - *Platform Test Scope Note*: `WindowsReminderAdapterTest` is an authored candidate test suite with fake bridge in `windowsTest`, but was not compiled or executed on this Linux host runner.
- Cycle 2 code review score **8.9 / 10** conditionally approved with remediations verified.
- Gate G2 Status: Logic, scheduler, SPI dispatcher, and native adapters delivered; physical notification bubble display on active desktop session and installed Windows MSI execution remain pending Gate G2 / Phase 11.

---
## [Phase 08] - 2026-10-15: Android Alarms, Private Notifications & Device Restoration (Gate G2 Android Logic Delivered; Physical Connected Device Acceptance Pending Phase 11)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative; distinct from the historical 2026-10-14 Phase 08 release operations release.*

### Added
- Android native reminder and alarm scheduling infrastructure in `:androidApp`:
  - `AndroidNotificationAdapter`: Implements `NativeReminderAdapter`, managing the `tabbyrush_reminders` high-importance notification channel with `VISIBILITY_PRIVATE`, generic public lockscreen fallback (`VISIBILITY_PUBLIC`, text: "Task reminder"), exact alarms via `AlarmManager.setExactAndAllowWhileIdle` (falling back to `setAndAllowWhileIdle`), and notification clearing.
  - `AndroidAlarmRegistry`: Synchronized SharedPreferences storage (`tabbyrush_alarms_<accountKey>`) tracking active alarm keys (`generation:taskId:scheduleToken:occurrenceIndex`) and active notification IDs via synchronous `commit()` writes.
  - `TabbyRushAlarmReceiver`: Non-exported (`android:exported="false"`) receiver handling explicit `ACTION_DELIVER_REMINDER` intents with direct-boot unlock verification, identity-only extras (no user text in alarm PendingIntent extras or URIs), wipe marker absence check, and 9s cooperative timeout via `ReminderSchedulingCoordinator`.
  - `ReminderRestoreReceiver`: Non-exported receiver handling system lifecycle events (`BOOT_COMPLETED`, `USER_UNLOCKED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`), completing pending wipes before reconciling active account reminders.
  - `CompositeLeaveSchedulerCancelGate`: Coordinates strict 4-stage teardown: (1) delivery gate drain -> (2) schedule observer cancel -> (3) WorkManager sync cancel -> (4) AlarmManager/notification cancel.
  - `ReminderPermissionController`: Sealed capability hierarchy (`Exact`, `BestEffort`, `NotificationDenied`, `ChannelDisabled`) and settings intent factories.
  - Cold-start deep linking in `MainActivity` and `SessionViewModel.selectTaskFromNotification`.
- Automated test suites:
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/AndroidNotificationAdapterTest.kt`: 6 unit tests for request code determinism, capabilities model, tag formatting, registry serialization, and restore actions.
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/CompositeLeaveSchedulerCancelGateTest.kt`: 3 unit tests for drain timeout, sync cancel failure, and ordered quiescence.
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/ReminderPermissionControllerTest.kt`: 2 unit tests for permission state representations.
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/BuildConfigTest.kt`: 1 unit test for configuration loading.

### Verification and Delivery Status
- Automated test suites pass 282 executed checks across reported test suites (12 Android unit tests in `:androidApp:testDebugUnitTest`, 182 shared JVM tests, 32 desktop tests, 23 strict-v2 vectors, 18 offline smoke fixture checks, 6 static security checks, 9 CLI tests; `:androidApp:assembleDebug` APK build verified separately).
- Code review score **9.2 / 10** approved.
- Gate G2 Status: Logic and native adapters delivered; connected physical device / emulator execution, Doze mode testing, live broadcast receipt across reboot, and platform acceptance remain formally pending Gate G2 / Phase 11.

---
## [Phase 07] - 2026-10-15: Shared Scheduling, Persisted Eligibility & Account Safety (Gate G5 Passed; Native Adapters Unlocked for Phases 08/09)

### Added
- Shared reminder scheduling calculator in `:shared` (`dev.tabbyrush.shared.contract.reminder-schedule-calculator.kt`):
  - `ReminderScheduleCalculator`: Calculates all planned occurrences (`CalculatedReminderOccurrence`) using checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) without floating-point errors.
  - Enforces repeat bounds: `rule.count` in `1..10`, non-repeating rules require null interval, repeating rules require interval >= 15m, and last occurrence cannot exceed anchor instant.
  - Evaluates strict 5-minute grace window (`GRACE_PERIOD_MS = 300_000L`): exactly 300,000ms included, 300,001ms skipped.
  - Latest opportunity selection: selects at most the single latest eligible occurrence per task, marking older unconsumed opportunities `SKIPPED`.
  - Consumed opportunity barrier: if latest eligible occurrence was already consumed (`CLAIMED` or `POSTED`), fallback to older opportunities is strictly blocked (`isLatestConsumed = true`).
  - Preserves all future occurrences (`dueEpochMs > nowEpochMs`) for future alarm scheduling.
- Draft-free persisted schedule projector (`dev.tabbyrush.shared.contract.persisted-schedule-projector.kt`):
  - `PersistedScheduleProjector.computeProjection()`: Computes schedule projection directly from atomic SQLite snapshots of `server_base` overlaid with submitted `outbox` records (`PENDING`, `DISPATCHING`, `UNCERTAIN`).
  - Strict isolation: `editor_drafts` table is never read; uncommitted typing and user edits never schedule alarms or cancel reminders.
  - Explicit nullable clearing: explicit deadline removal clears all deadline fields and scheduleToken, resetting reminder rules to disabled (`ReminderRule.DISABLED`), while updating deadline parameters recomputes schedule projection with the new temporal bounds.
  - Conflict head suppression: suppresses schedule calculations (`hasUnresolvedConflict = true`) when the row's head outbox operation has `deliveryState == STATE_CONFLICT` or `STATE_UNKNOWN_SUPERSEDED`.
  - Cancellation barriers: task completion, local delete, or disabled reminder rule acts as an immediate cancellation barrier even when another rule on the row is conflicted.
- Durable occurrence ledger store (`dev.tabbyrush.shared.data.reminder-occurrence-store.kt`):
  - `ReminderOccurrenceStore`: Manages lifecycle of `reminder_delivery_ledger` entries under composite primary key 5-tuple (`accountKey`, `generation`, `taskId`, `scheduleToken`, `occurrenceIndex`).
  - Monotonic occurrence identity: Schedule tokens rotate exclusively on temporal schedule changes; task title updates modify notification copy without altering schedule tokens or duplicating occurrence opportunities.
  - Reopened task preservation: Reactivates ledger records from `CANCELLED` to `ACTIVE` while preserving prior consumed claim outcomes (`CLAIMED`, `POSTED`).
  - Reconcile delta computation (`syncPlannedOccurrences`): syncs planned occurrences, cancels orphan tasks and stale tokens, identifies future alarms (`dueEpochMs > nowEpochMs`), and detects obsolete posted notifications for removal.
  - Atomic claim-before-post (`evaluateAndClaimDueOpportunity`): evaluates grace window and atomically claims occurrence in Room `@Transaction` (`claimOccurrenceAtomic`), marking older opportunities skipped in the same SQLite transaction.
  - Platform notification acknowledgment: `recordPosted()` commits platform-assigned notification ID; `clearPostedNotificationId()` clears notification reference after native removal acknowledgment.
- Delivery gate concurrency coordinator (`dev.tabbyrush.shared.sync.reminder-delivery-gate.kt`):
  - `ReminderDeliveryGate`: Coordinates concurrency between store mutations, alarm callbacks, and account teardown.
  - Enforces three-tier lock hierarchy: `delivery gate admission -> store mutex -> DB transaction`. Inversion is prohibited; admission tracks in-flight deliveries across native adapter operations so teardown can drain them, while the gate's internal mutex and SQLite transaction locks are never held across platform IPC or network socket calls.
  - Gate lifecycle states: `OPEN`, `CLOSING`, `CLOSED`.
  - Fail-closed callback admission (`withDeliveryAdmission`): verifies gate state `OPEN`, absence of pending `wipe.marker`, and persisted generation equality; fails closed returning `null` on any mismatch.
  - Teardown quiescence (`closeAndDrain(5000L)`): transitions gate to `CLOSING`, blocks new callback admissions, and waits for active in-flight callbacks and mutations to reach 0 before transitioning to `CLOSED`.
- Per-account scheduling coordinator (`dev.tabbyrush.shared.sync.reminder-scheduling-coordinator.kt`):
  - `ReminderSchedulingCoordinator`: Owns per-account scheduling lifecycle binding `RoomAccountStore`, `ReminderOccurrenceStore`, `NativeReminderAdapter`, and `ReminderDeliveryGate`.
  - Mutation-gated reconciliation (`reconcile()`): executes under gate mutation admission, computes planned occurrence delta, reconciles scheduled alarms via `adapter.reconcileSchedule()`, removes obsolete notifications via `adapter.removeOwnedNotification()`, and acknowledges removal in the ledger.
  - Claim-before-post delivery flow (`deliverReminder()`): acquires delivery gate admission, checks capabilities, re-reads current persisted task projection from SQLite (re-validating eligibility and fetching fresh title text), executes atomic SQLite claim, re-checks wipe marker and eligibility pre-post, posts natively via `adapter.postOwnedNotification()`, and commits platform notification ID via `occurrenceStore.recordPosted()`.
  - Opportunity delivery semantics: cross-process OS posting and SQLite commits cannot achieve two-phase atomic commit; claim-before-post enforces at most one posting attempt per durable occurrence identity (assuming atomic claims and preserved ledger state; crashes after claim may forfeit alerts to avoid double posting). Failed native posting is recorded as claimed/attempted with no retry loop.
  - Teardown cancellation: implements `LeaveSchedulerCancelGate`, establishing the required teardown cancellation contract via `adapter.cancelAllForAccount(accountKey)` (physical platform alarm/notification removal verified in Phases 08/09).
- Automated test suites:
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/reminder-schedule-calculator-test.kt`: 9 unit tests for checked arithmetic, 1..10 repeat bounds, exact 300,000ms grace inclusion, 300,001ms exclusion, latest opportunity selection, and consumed barrier blocking.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projection-test.kt`: 5 unit tests for draft-free projection, base + outbox overlay, nullable clearing, conflict head suppression, and completion/delete barriers.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/data/reminder-occurrence-store-test.kt`: 4 unit tests for ledger persistence, atomic claim, grace skip marking, and idempotent sync.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/reminder-delivery-gate-test.kt`: 4 unit tests for mutation/delivery admission, drain signal, wipe marker rejection, and generation mismatch guard.
  - `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-scheduling-coordinator-sqlite-smoke-test.kt`: 2 integration tests on BundledSQLite exercising local save/delete/complete/reopen, title update identity retention, grace bounds, duplicate callback rejection, and wipe race protection.
  - `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-teardown-barrier-sqlite-test.kt`: 1 integration test on BundledSQLite demonstrating teardown gate drain synchronization, fail-closed wipe marker retention on cancel failure, and startup wipe recovery.

### Changed
- `AccountLeaveProtocol` (`dev.tabbyrush.shared.sync.account-leave-protocol.kt`):
  - Integrates `deliveryGate.closeAndDrain(5000L)` into teardown sequence before database closure. Drain timeout aborts fail-closed leaving store frozen.
  - Fail-closed marker retention: if background scheduler cancellation (`schedulerCancelGate.cancelAndAwait()`) fails or throws, teardown retains `wipe.marker` and remains frozen fail-closed (no rollback to unfrozen state).
  - Interrupted startup recovery (`completePendingWipe`): cancels schedulers via `schedulerCancelGate`, deletes remaining database files (`tabbyrush.db`, `tabbyrush.db-wal`, `tabbyrush.db-shm`), clears vault credentials, and clears `wipe.marker` fail-closed.
- `RoomAccountStore` (`dev.tabbyrush.shared.data.room-account-store.kt`):
  - Added `@kotlin.jvm.Volatile` to `var deliveryGate: ReminderDeliveryGate? = null` for safe multi-threaded coroutine visibility.
  - Wrapped all outbox error state transitions (retryable failures, malformed accepted revisions, unrecognized statuses) in `withMutationLock` to ensure gate admission tracking and schedule change notifications.
  - Exposes `getPersistedScheduleProjection()` and `observePersistedSchedule()` emitting updates on committed mutations without polling drafts.
- `TabbyRushDao` (`dev.tabbyrush.shared.data.tabbyrush-dao.kt`):
  - Added `clearPostedNotificationId(accountKey, generation, platformNotificationId, nowEpochMs)` to acknowledged native removals in the SQLite ledger.
  - Added atomic claim and skip transaction methods: `claimOccurrenceAtomic`, `markOccurrencesSkippedAtomic`, `recordPostedAtomic`.

### Verification and Delivery Status
- Automated test suites pass 270 executed checks with 0 failures:
  1. `:shared:jvmTest` (182 tests PASS across 26 classes, executed with `--dependency-verification=off`).
  2. `:desktopApp:test` (32 tests PASS across 6 classes, executed with `--dependency-verification=off`).
  3. `node scripts/strict-v2-vector-runner.mjs` (23/23 PASS).
  4. `node scripts/hub-live-smoke.mjs --fixture-test` (18/18 PASS against offline fixture mock server).
  5. `node scripts/release-security-check.mjs --diagnose-source` (6/6 PASS: static manifest rules, export audit, security XML, HTTPS origin, 0 hardcoded secrets).
  6. `node scripts/hub-strict-upgrade.test.mjs` (9/9 PASS: upgrade CLI runner test suite).
- Code reviews completed with all remediations verified (documented in `plans/reports/code-review-261015-1928-phase-07-shared-scheduling-account-safety.md` and `code-review-261015-1944-phase-07-remediations-rereview.md`).
- **Delivery Boundaries & Downstream Unlocks**:
  - Gate G5 is formally passed for shared scheduling arithmetic, draft-free persisted projection, durable ledger lifecycle, delivery gate concurrency, and account safety.
  - Native platform alarm scheduling and notification display are unlocked for Phase 08 (Android Exact Alarm / Notification Delivery) and Phase 09 (Linux Desktop Delivery).
  - Physical connected platform acceptance and live multi-device sync cutover remain scheduled for Phase 11.

---
## [Phase 06] - 2026-10-15: Client Version Transition, Safe Replay & Intention Preservation (Gate G4 Logic Delivered; Connected Platform Acceptance Pending Phases 08/11)

### Added
- Client transition state coordinator in `:shared` (`dev.tabbyrush.shared.sync.contract-transition-coordinator.kt`):
  - `TransitionPhase` enum: `READY_V1`, `UPGRADE_DETECTED`, `DISPATCH_QUIESCED`, `LEGACY_RECONCILING`, `V2_BASE_SCANNING`, `SAFE_INTENTS_TRANSLATING`, `READY_V2`.
  - `ContractTransitionCoordinator`: Implements durable 7-state / 6-transition upgrade lifecycle (`ready-v1` -> `upgrade-detected` -> `dispatch-quiesced` -> `legacy-reconciling` -> `v2-base-scanning` -> `safe-intents-translating` -> `ready-v2`), persisting active phase in `AccountSyncMetaEntity.syncStatus` (`"TRANSITION:<phase>"`) and `safeErrorCode = "CHECKPOINT_<PHASE>"` for interruption recovery.
  - Quiesces network dispatch and recovers in-flight `DISPATCHING` operations to `UNCERTAIN` via `RoomAccountStore.recoverInterruptedDispatches()`.
  - Bounded legacy receipt matching against `POST /api/v1/sync/{appId}/strong/legacy-receipts`: verifies `opId`, `rowId`, `expectedRevision`, and `digest`, advancing base on `PROVEN_ACCEPTED`, unblocking on `PROVEN_NOT_ACCEPTED`, flagging `STATE_UNKNOWN_SUPERSEDED` on server revision skew, flagging `STATE_CONFLICT` on `INTEGRITY_FAILURE`, and defaulting omitted receipts safely to `STATE_UNCERTAIN` (never treated as non-delivery proof or permitting automatic rehash).
  - Migration-mode base promotion via `StagedFullScan(..., protocol = StrictSyncV2Contract.PROTOCOL_VERSION, isMigration = true)`.
  - Transactional safe intent translation (`translateSafeIntents()`): reuses row identity, applies known changed fields over scanned v2 base, generates fresh v2 UUID `opId`, and marks old intention replaced in SQLite via `replaceOutboxAtomic` (`transitionCheckpoint = "TRANSLATED_V2"`). Rows marked `INTENT_REQUIRES_REVIEW` from Room v1 migration fail closed to `STATE_CONFLICT`.
- Legacy receipt wire models and network client integration (`dev.tabbyrush.shared.sync.hub-sync-dtos.kt` and `hub-sync-client.kt`):
  - `LegacyReceiptItemDto`: Serializes `opId`, `tableName`, `rowId`, `kind`, `expectedRevision`, `digest`, and `data` (`StrictTodoDataDto`).
  - `LegacyReceiptResultDto`: Deserializes `opId`, `rowId`, `status` (`LegacyReceiptStatus`), and nullable `acceptedRevision`.
  - `LegacyReceiptsRequestDto` and `LegacyReceiptsResponseDto`.
  - `HubSyncClient.legacyReceipts(epoch, receipts)`: POSTs to `/api/v1/sync/{appId}/strong/legacy-receipts` with dual-auth and batch size validation (`<= 100`).
  - `HubSyncClient.pushV2(epoch, operations)`: Dispatches `StrictV2OperationDto` batches (`<= 100`) to strict-v2 push endpoint.
  - `HubHttpClient`: Added `/api/v1/sync/{appId}/strong/legacy-receipts` to `RouteClassification.DUAL_AUTH_STRICT` route allowlist.
- Room DAO and Entity extensions in `dev.tabbyrush.shared.data`:
  - `TabbyRushDao.countOutboxByVersion(version: String): Int`: Index-backed outbox query for server version regression detection.
  - `TabbyRushDao.discardRegressiveReconcileAtomic(opId: String, rowId: String, clearDraft: Boolean)`: `@Transaction` method deleting stale outbox and draft atomically on regressive server revision receipt.
- UI conflict review and semantics enhancements (`dev.tabbyrush.shared.contract.todo-ui-state.kt` and `dev.tabbyrush.shared.ui.conflict-review.kt`):
  - `EditorState.isUnknownSuperseded: Boolean = false`: Flags operations with ambiguous delivery history.
  - `ConflictReview`: Renders high-visibility coral warning card (`coralSurface`) on `isUnknownSuperseded` conflicts, explaining potential prior remote acceptance and warning against duplicate reapply.
  - `VersionCard`: Displays formatted deadline date, time, zone, and reminder recurrence details with WCAG 2.1 AA accessible contrast tokens.
- Automated test suites:
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/contract-transition-test.kt`: 4 unit tests covering transition state progression, durable checkpoint resume, unknown-superseded blocking, and user review gating.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator-test.kt`: 9 unit tests for upgrade detection, quiescence, receipt rebase, and v2 push dispatch.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/hub-sync-client-test.kt`: 12 unit tests covering bootstrap v2 negotiation, pushV2, pull v2, and legacy-receipt envelopes.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation-test.kt`: 12 unit tests for optimistic locking, FIFO batches, receipt matching, and CAS acknowledgement.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/staged-full-scan-test.kt`: 13 unit tests for multi-page full scan, monotonic promotion, and migration-mode equal-revision temporal enrichment.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt`: 8 unit tests for semantics, contrast tokens, and conflict warning copy.
  - `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt`: Extended with 7 new tests (21 total) covering non-destructive intent-masked rebasing, atomic cleanup on regressive revisions, and schedule token rotation.

### Changed
- `RoomAccountStore.reconcileAccepted()`:
  - Non-destructive intent-masked rebasing: Acknowledging legacy strict-v1 outbox operations or title/completed-only mutations preserves remote temporal metadata (`deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`) on `server_base`.
  - Atomic regressive cleanup: Invokes `TabbyRushDao.discardRegressiveReconcileAtomic` when received server revision is lower than confirmed base.
- `RoomAccountStore.saveTodoLocally()`:
  - Dynamically assigns `contractVersion = OutboxEntity.CONTRACT_VERSION_V2` when `syncStatus` is `ready-v2` / `READY_V2`.
- `AccountSyncCoordinator.executeSyncRound()`:
  - Intercepts v2 protocol in bootstrap response; triggers `ContractTransitionCoordinator.executeTransition()` if durable phase is not `READY_V2`.
  - Enforces server version compatibility: aborts with `SafeError.ProtocolIncompatible("SERVER_VERSION_REGRESSION")` if connecting to a v1 server while v2 outbox operations exist.
  - Dispatches outbox mutations via `syncClient.pushV2()` when active protocol is strict-v2.
- `StagedFullScan`:
  - Added `protocol: String` parameter (passed to `syncClient.pull`).
  - Added `isMigration: Boolean` parameter: enriches confirmed base with remote temporal metadata when revisions are equal and local base lacks temporal metadata.
  - Added `isFenced: (() -> Boolean)?` parameter: verifies store and coordinator freeze status before invoking `promoteStagingAtomic`.
- `OutboxReconciliation`:
  - Added `toV2Dto()` converting `OutboxEntity` to `StrictV2OperationDto`.
  - Enhanced `wasUncertain` detection to inspect `op.dispatchHistory` and `op.transitionCheckpoint` in addition to `STATE_UNCERTAIN` and failure counts.

### Verification and Delivery Status
- Automated test suites pass 243 executed checks with 0 failures (reported in `plans/reports/qa-261015-1815-phase-06-client-transition-and-replay.md`):
  1. `:shared:jvmTest` (155 tests PASS across 20 test classes, executed with `--dependency-verification=off`).
  2. `:desktopApp:test` (32 tests PASS across 6 test classes, executed with `--dependency-verification=off`).
  3. `node scripts/strict-v2-vector-runner.mjs` (23/23 PASS: v1/v2 schema fingerprints, 10 valid vectors, 11 rejection vectors).
  4. `node scripts/hub-live-smoke.mjs --fixture-test` (18/18 PASS against offline fixture mock server).
  5. `node scripts/release-security-check.mjs --diagnose-source` (6/6 PASS: static manifest rules, export audit, security XML, HTTPS origin, 0 hardcoded secrets).
  6. `node scripts/hub-strict-upgrade.test.mjs` (9/9 PASS: upgrade CLI runner test suite).
- Code review completed with 0 critical/high issues (documented in `plans/reports/code-review-261015-1830-phase-06-client-transition-and-replay.md`).
- **Delivery Boundaries & Remaining Holds**:
  1. Gate G4 offline-client upgrade round-trip and safe replay logic are delivered and verified on JVM BundledSQLite. Android instrumented execution (`:shared:connectedAndroidTest`) remains pending Phase 08 emulator/device testbeds.
  2. Connected native notification delivery (Android AlarmManager, Linux Secret Service / D-Bus notifications, Windows DPAPI / toast bridge) remains scheduled for platform phases 08–10.
  3. Live multi-device mixed-version strict-v2 cutover across network boundaries is scheduled for Phase 11 end-to-end integration.

---
## [Phase 05] - 2026-10-15: Shared Task Model & Explicit Room v2 Migration (Gate G4 Storage Migration Delivered; Instrumented Platform Acceptance Pending)

### Added
- Shared task scheduling models and invariants in `:shared` (`dev.tabbyrush.shared.contract.todo-models.kt`):
  - `TaskDeadline`: Immutable data class (`date`, nullable `time`, `zone`, `offsetSeconds`, nullable `reminderAnchorTime`, `anchorEpochMs`, `overdueEpochMs`). Enforces non-empty date/zone; date-only deadline requires non-null `reminderAnchorTime`; timed deadline requires null `reminderAnchorTime` and `overdueEpochMs == anchorEpochMs`.
  - `ReminderRule`: Immutable data class (`enabled`, non-negative `leadMinutes`, nullable `intervalMinutes`, `count` in 1..10). When `count == 1`, `intervalMinutes` must be null; when `count > 1`, `intervalMinutes` must be non-null and `>= 15`. Provides `ReminderRule.DISABLED`.
  - `Todo` domain entity extensions: Integrates `deadline: TaskDeadline?`, `reminderRule: ReminderRule`, and `scheduleToken: String?`. Validates schedule consistency: null deadline requires disabled reminder (lead 0, count 1, interval null) and null token; present deadline requires non-empty `scheduleToken` and checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) verifying `last <= anchor` (last scheduled reminder occurrence cannot exceed deadline anchor).
  - Model mapping extensions: `Todo.toStrictV2TodoData()`, `StrictV2TodoData.toDeadline()`, `StrictV2TodoData.toReminderRule()`, and `hasScheduleSemanticChange()`.
- Room database version 2 schema and explicit step-by-step migration (`DatabaseMigrations.kt`):
  - `DatabaseMigrations.MIGRATION_1_2`: Explicit SQLite migration upgrading schema version 1 to 2 without destructive fallback. Registered across both JVM and Android database builders via `DatabaseMigrations.ALL`.
  - Exported Room schema artifact: `shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/2.json` (`identityHash: 06a93ffbac4a0570d56446f47e0e3e4c`).
- New Room entities in `dev.tabbyrush.shared.data`:
  - `AccountLocalSettingsEntity` (`account_local_settings`): Partition-local notification and reminder preferences (singleton `id = 'singleton'`, `notificationsEnabled`, `defaultReminderEnabled`, `defaultReminderLeadMinutes`, `defaultReminderIntervalMinutes`, `defaultReminderCount`, `defaultDateOnlyAnchorTime = "09:00"`). Strictly account-local, wiped on logout, never sent to Hub.
  - `ReminderDeliveryLedgerEntity` (`reminder_delivery_ledger`): Durable occurrence state and claim outcomes for native reminders. Composite primary key: 5-tuple (`accountKey`, `generation`, `taskId`, `scheduleToken`, `occurrenceIndex`). Composite index on `[accountKey, generation, taskId]` and index on `[dueEpochMs]`. Tracks `claimOutcome` (`PENDING`, `CLAIMED`, `POSTED`, `MISSED`, `CANCELLED`, `SKIPPED`) and `cancellationState` (`ACTIVE`, `CANCELLED`). Durable across restarts, wiped on logout, never sent over wire.
- New DAO methods in `TabbyRushDao`:
  - `getLocalSettings()`, `upsertLocalSettings()`, `clearLocalSettings()` for `AccountLocalSettingsEntity`.
  - `getLedgerEntry()`, `getLedgerEntriesForTask()`, `getDueLedgerEntries()`, `getAllLedgerEntries()`, `upsertLedgerEntry()`, `upsertLedgerEntries()`, `cancelLedgerEntriesForTask()`, `deleteLedgerForAccount()`, `clearLedger()`, `countLedgerEntries()` for `ReminderDeliveryLedgerEntity`.
  - Updated `wipeAllTablesAtomic()` clearing all 7 tables in a single SQLite transaction.
- Automated test suites:
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-deadline-reminder-models-test.kt`: 6 unit tests covering valid date-only/timed deadlines, last reminder bounds, tokens, overflow arithmetic, and schedule consistency invariants.
  - `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v1-to-v2-test.kt`: Migration test asserting schema upgrade from v1 to v2, verifying column addition, conservative intent mask derivation, default value population, and table creation on BundledSQLite.

### Changed
- `dev.tabbyrush.shared.data.TabbyRushDatabase`: Upgraded to database `version = 2` with 7 registered entities (`ServerBaseEntity`, `OutboxEntity`, `EditorDraftEntity`, `ScanStagingEntity`, `AccountSyncMetaEntity`, `AccountLocalSettingsEntity`, `ReminderDeliveryLedgerEntity`).
- Expanded existing entities with 12 strict-v2 temporal columns (`deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`):
  - `ServerBaseEntity` (`server_base`)
  - `EditorDraftEntity` (`editor_drafts`)
  - `ScanStagingEntity` (`scan_staging`)
  - `OutboxEntity` (`outbox`)
- `OutboxEntity` provenance and versioning fields:
  - `contractVersion: String = CONTRACT_VERSION_V1`: Distinguishes legacy `"strict-v1"` mutations from `"strict-v2"`.
  - `dispatchHistory: String? = null`: JSON log tracking dispatch events and migration provenance.
  - `intentMask: String? = null`: Mutation mask (`TITLE`, `COMPLETED`, `DEADLINE`, `REMINDER`, `DELETE`, `REQUIRES_REVIEW`).
  - `transitionCheckpoint: String? = null`: Migration checkpoint marker (`MIGRATED_FROM_ROOM_V1`).
- `DatabaseMigrations.MIGRATION_1_2` legacy outbox provenance SQL:
  - Updates existing legacy outbox rows to set `transitionCheckpoint = 'MIGRATED_FROM_ROOM_V1'`, `dispatchHistory = '{"event":"ROOM_V1_MIGRATION","priorState":"' || deliveryState || '"}'`, and conservative `intentMask` (`'DELETE'` for deletes, `'TITLE|COMPLETED'` for creates and complete updates, `'REQUIRES_REVIEW'` for partial/ambiguous updates).
- `RoomAccountStore`:
  - `saveTodoLocally`: Dynamically derives `intentMask` by comparing edited fields against `server_base` (defaults to `INTENT_TITLE` when no differences detected), binds `contractVersion = CONTRACT_VERSION_V2`, and computes SHA-256 digest via `StrictSyncV2Digest` for v2 vs `StrictSyncDigest` for v1.
  - Guards unsent create-delete cancellation and outbox coalescing with `transitionCheckpoint == null` to prevent mutating migrated operations.
  - Enforces tombstone non-mutation with `IllegalStateException`.
  - Exposes `getLocalSettings()`, `updateLocalSettings()`, `getLedgerEntriesForTask()`, `upsertLedgerEntry()`, `cancelLedgerEntriesForTask()`.
- `jvm-database-builder.kt`: Verified migration registration through `DatabaseMigrations.ALL`.
- Expanded unit test coverage in `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt` (14 tests covering BundledSQLite persistence, v2 schedule tokens, and draft recovery).

### Verification and Delivery Status
- Shared Kotlin Multiplatform JVM test suite passes 128 tests across 19 classes (`./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` in 6.46s):
  - `RoomMigrationV1ToV2Test` (1/1 PASS): Validates explicit v1->v2 SQLite schema migration and data preservation.
  - `RoomAccountStoreTest` (14/14 PASS): Validates BundledSQLite persistence, draft recovery, and v2 token survival.
  - `TaskDeadlineReminderModelsTest` (6/6 PASS): Validates temporal domain models and checked 64-bit arithmetic.
- Strict-v2 vector runner passes 23/23 checks (`node scripts/strict-v2-vector-runner.mjs`):
  - Schema fingerprints verified: v1 (`3361a80f...`) and v2 (`5f539551...`).
  - 10 valid vectors + 11 boundary/rejection vectors PASS.
- Hub offline smoke fixture self-test passes 18/18 checks (`node scripts/hub-live-smoke.mjs --fixture-test`).
- Static source security diagnostics passes 6/6 checks (`node scripts/release-security-check.mjs --diagnose-source`).
- Total: 175 executed automated checks passed cleanly in a single test run with 0 failures.
- **Coverage Boundaries & Remaining Holds**:
  1. Room v2 schema, explicit migration, and temporal models are delivered and verified on JVM BundledSQLite. Android instrumented execution (`:shared:connectedAndroidTest`) remains pending Phase 08 device/emulator availability.
  2. Storage migration delivery does NOT establish safe end-to-end mixed-version synchronization. Cross-version outbox reconciliation and client sync migration remain scheduled for Phase 06 (`phase-06-client-version-transition-and-replay`). In particular, non-destructive intent-masked rebasing in `reconcileAccepted` (safeguarding remote v2 deadline fields from legacy v1 acknowledgements) and atomic cleanup on regressive server revision (`discardRegressiveReconcileAtomic`) are Phase 06 dependencies.
  3. Production admission and live staging acceptance remain on hold per earlier phase gates (G2 native platform environments, G3 live replica-set cluster).

---
## [Phase 04] - 2026-10-15: Hub Admin UI and Shared-Operation Upgrade Script (Gate G3 Operator Tooling Delivered; Staging Acceptance Pending)

### Added
- Dedicated CLI upgrade runner `scripts/hub-strict-upgrade.mjs`:
  - Implements administrative migration runner for `strict-v1` -> `strict-v2` transitions via Glean Hub Admin API.
  - Preconditions verification querying `GET /api/v1/apps/:appId/strict-contract`, verifying active `syncMode`, canonical v1 schema fingerprint (`3361a80f...`), and matching epoch UUID.
  - Non-mutating preflight dry-run mode (`--dry-run`) issuing `dryRun: true` to discover eligible live records and tombstones without modifying data or schema version.
  - Automated bounded status polling against `GET /api/v1/apps/:appId/strict-upgrades/:migrationId` with configurable intervals and timeouts (default: 1500ms poll, 60s timeout).
  - Single migration ID invariant: interrupted initial POST requests never mint a new ID; recovers status using the active ID.
  - Explicit HTTP 4xx rejection fail-fast preventing misleading polling loops on definitive client errors.
  - Exit code contracts: 0 (success / dry-run / already upgraded), 1 (precondition failure / rejected / terminal error), 2 (polling timeout with resume guidance).
  - Secret redaction scrubbing `Bearer <token>` patterns and bare token substrings from all stdout and stderr logs.
  - Mandatory read-back assertion: verifies `syncMode: strict-v2`, canonical v2 fingerprint (`5f539551...`), and all 16 columns active before reporting completion.
- Automated CLI test suite `scripts/hub-strict-upgrade.test.mjs`:
  - 7 unit/contract tests using local mock HTTP server verifying `--help`, missing token validation, secret log redaction, dry-run preflights, full live upgrade with bounded polling and read-back assertions, network interruption recovery with same-ID polling, and `--status-only` queries.
- Admin Web UI components in `glean-hub-app`:
  - `StrictAppUpgradePanel` (`src/components/organisms/strict-app-upgrade-panel.tsx`): Manages upgrade lifecycle, preflight dry-run discovery, operator confirmation modals, bounded polling, `sessionStorage` resumption (`strict_upgrade_mig_${appId}`), manual migration ID input, and mandatory post-upgrade read-back assertions.
  - `StrictContractCard` (`src/components/organisms/strict-contract-card.tsx`): Read-only view displaying active sync mode, strict epoch UUID, canonical SHA-256 fingerprint, and active column definitions.
  - `StrictUpgradeControls` (`src/components/organisms/strict-upgrade-controls.tsx`): Action buttons for Dry Run Preflight, Execute Upgrade, and Manual Resume.
- Frontend Admin Services & Unit Tests in `glean-hub-app`:
  - `sync-admin-service.ts`: Extended with `getStrictContract`, `startStrictUpgrade`, `getStrictUpgradeStatus`, and `updateAppSchema` guard, with URL encoding for `appId`.
  - `sync-admin-service.test.ts`: 8 unit tests verifying parameter encoding, contract fetching, dry-run payload construction, status queries, and 403 Forbidden handling.
  - Native test runner scripts `scripts/test-loader.mjs` and `scripts/test-register.mjs` supporting TypeScript stripping via Node.js native test runner (`node --test`).
- Gate 5 strict contract metadata validation in `scripts/hub-compatibility-smoke.mjs`.

### Changed
- `glean-hub-app/src/pages/admin/edit-app.tsx`: Added strict application detection and guarding: hides editable legacy schema builder and sharing editor for `strict-v1` and `strict-v2` applications, blocks arbitrary `PUT /api/v1/apps/:appId/schema` calls, and embeds `StrictContractCard` and `StrictAppUpgradePanel`.
- `scripts/hub-schema-provision.mjs`: Updated to support dual-version schema validation (4 columns for `strict-v1`, 16 columns for `strict-v2`), and handles idempotent provisioning of already-upgraded v2 apps without failure.
- `scripts/hub-compatibility-smoke.mjs`: Updated bootstrap protocol assertion to accept both `strict-v1` and `strict-v2`, and added Gate 5 strict contract endpoint verification.
- `scripts/hub-live-smoke.mjs`: Updated to dynamically detect active admitted protocol (`strict-v1` or `strict-v2`), emit schema-compliant payloads for strict-v2 (including `reminderCount: 1`), and retain strict-v1 offline fixture coverage (`--fixture-test`).
- `docs/hub-schema-integration.md`: Updated to Phase 04 Delivered, documenting Operator Surfaces (Admin UI and Upgrade CLI), CLI parameters, exit code contracts, and test verification matrix.
- `../glean-hub/docs/tabbyrush-strict-sync.md`: Sibling runbook updated with Phase 04 Admin UI and CLI upgrade instructions, and same-ID resumption procedures.

### Verification and Production Admission Status
- `glean-hub-app` unit tests pass 13/13 tests across 2 suites (`pnpm test:unit` in 120ms): 8 tests covering `sync-admin-service.ts` HTTP contract methods and parameter encoding, 5 tests covering Vite preload chunk recovery. Interactive browser UI actions and session-storage reload recovery remain unexercised by automated tests.
- `glean-hub-app` production build succeeds with zero TypeScript errors and complete Vite production bundle (`pnpm build`).
- TabbyRush strict upgrade CLI test suite passes 7/7 tests (`node scripts/hub-strict-upgrade.test.mjs` in 10.8s) against in-process mock HTTP server, exercising `--help`, auth guards, bearer redaction, dry-run, live upgrade, interrupted POST recovery, and `--status-only` queries.
- TabbyRush live smoke protocol fixture self-test passes 18/18 checks (`node scripts/hub-live-smoke.mjs --fixture-test`).
- TabbyRush strict-v2 vector suite passes 23/23 checks (`node scripts/strict-v2-vector-runner.mjs`).
- TabbyRush release security check passes 6/6 static checks (`node scripts/release-security-check.mjs --diagnose-source`).
- TabbyRush shared Kotlin Multiplatform JVM tests execute 15 tasks successfully with exit 0 (`./gradlew :shared:jvmTest --dependency-verification=off`).
- Rust Hub backend unit tests pass 54 tests (+1 ignored for live Mongo gate) in `glean-hub-sync` (`cargo test --locked`).
- Documentation internal links validated cleanly (163 links working, 0 broken via `validate-docs.cjs`).
- Production Admission Hold: Phase 04 operator tooling and script suites are verified in unit tests and against local mock fixtures. Production admission remains on hold pending resolution of: (1) Gate G3 physical multi-instance replica-set staging deployment, concurrent client write fence draining, and cross-entrypoint browser/CLI resume, (2) coordination document `permitCount` backfill/compatibility for pre-existing records, (3) exact cumulative accounting across mid-batch crash interruptions during document transformation, and (4) duplicate permit release filtering.
---
## [Phase 03] - 2026-10-15: Hub v2 Admission, Fencing and Migration (Gate G3 Backend Logic & Tests Delivered; Staging Gate Pending)

### Added
- Sibling backend models and migration state machine in `glean-hub-sync` (`models/strict_upgrade.rs`):
  - Canonical schema definitions and JSON builders (`canonical_v1_schema_json`, `canonical_v2_schema_json`) matching fingerprints `3361a80f...` (v1) and `5f539551...` (v2).
  - 8-stage migration transition state machine (`MigrationState`: `ready-v1`, `fencing`, `transforming`, `validating`, `publishing`, `ready-v2`, `operatorRequired`, `rolledBack`).
  - DTOs for contract queries (`StrictContractInfo`), upgrade requests (`StrictUpgradeRequest`), migration records (`MigrationRecord`), coordination documents (`AppCoordinationDoc`, `PermitEntry`), and legacy receipt reconciliation (`LegacyReceiptItem`, `LegacyReceiptsRequest`, `LegacyReceiptStatus`, `LegacyReceiptResult`, `LegacyReceiptsResponse`).
- Durable permit admission and exclusive fencing service in `glean-hub-sync` (`services/permit_service.rs`):
  - `PermitService` managing coordination records in `_coordination` (`permit:<appId>`).
  - Enforces bounded concurrency ceiling `MAX_CONCURRENT_PERMITS = 1000` via atomic CAS filter `permitCount: { $lt: 1000 }` with `$inc: { permitCount: 1 }`.
  - Permit release verifies `activePermits.<permit_id>: { $exists: true }` before decrementing `permitCount: -1`.
  - Exclusive migration fence acquisition (`fenced: true`, `fenceOwner`, incremented `fenceGeneration`) and active permit draining.
- In-place strict upgrade engine in `glean-hub-sync` (`services/strict_upgrade_service.rs`):
  - `StrictUpgradeService` orchestrating contract inspection, dry-run preflights, exclusive fencing, and permit draining.
  - Chunked in-place MongoDB document transformation (`BATCH_SIZE = 500`) setting 12 missing v2 fields to default/null on live rows while preserving `{}` tombstones.
  - Idempotent `$exists: false` matching and resumption passing prior recorded `recordsTransformed` and `tombstonesPreserved` (with exact mid-batch interruption accounting remaining an open item awaiting revalidation).
  - Schema publication via CAS in `_schemas` using majority journaled write concern, followed by exclusive fence release.
- Legacy receipt reconciliation bridge in `glean-hub-sync` (`services/strong_sync_service.rs`):
  - Dedicated reconciliation route for v1 client receipts (`POST /api/v1/sync/:app_id/strong/legacy-receipts`).
  - Bounded batch limit `<= 100` receipts per request (`MAX_BATCH_SIZE`).
  - Protocol validation enforcing `request.protocol == "strict-v1"` (rejecting mismatched protocols with HTTP 426 `protocolUnsupported`).
  - SHA-256 v1 digest recomputation and 4-way receipt status classification (`provenAccepted`, `provenNotAccepted`, `unknownSuperseded`, `integrityFailure`).
  - Durable replay confirmation writes (`confirmationNonce`) for matching `lastOp` without mutating task payload data.
- App-scoped administration routes and authorization middleware in `glean-hub-sync` (`routes/strict_upgrades.rs`, `middleware/dual_auth.rs`):
  - `GET /api/v1/apps/:app_id/strict-contract`, `POST /api/v1/apps/:app_id/strict-upgrades`, `GET /api/v1/apps/:app_id/strict-upgrades/:migration_id`.
  - `app_scoped_admin_middleware` requiring both `is_admin: true` and target app present in `claims.apps` (returning HTTP 401 for non-admin, HTTP 403 for ungranted app).
- Integration test suite in `glean-hub-sync` (`services/strict_upgrade_tests.rs`):
  - 11 unit/contract tests covering app-scoped admin authorization, 401/403 status mappings, route parameter extraction, schema validation, error response codes, request parsing, and coordination document serialization.
- Sibling operator documentation in `glean-hub` (`docs/tabbyrush-strict-sync.md`):
  - Strict-v2 upgrade invariants, management endpoints, multi-node coordination runbook, and migration procedures.

### Changed
- `glean-hub-sync/src/services/strong_sync_service.rs`: Integrated durable permit acquisition and release gates across all strict data access endpoints (`bootstrap`, `push`, `pull`, `legacy-receipts`), rejecting client ingress with HTTP 503 `migrationInProgress` when app is fenced.
- `glean-hub-sync/src/services/app_service.rs`: Extended schema validation to support `strict-v2` 16-column specifications and mode-specific column rules.
- `docs/system-architecture.md`: Updated Section 6 to record Phase 03 delivered logic, documented management endpoints, bounded coordination permits, in-place migration engine, legacy receipt reconciliation, and Invariants 33–36.
- `docs/hub-schema-integration.md`: Updated status to Phase 03 Delivered, detailed upgrade and legacy receipt wire contracts, bounded coordination permit gate, error mappings, and release gating criteria.
- `docs/codebase-summary.md`: Updated Repomix metrics based on fresh compaction (340 files, 656,907 tokens), documented delivered Phase 03 backend architecture and modules, and added Invariants 38–41.

### Verification and Gate G3 Status
- Backend test runner passes 54 tests in Rust `glean-hub-sync` (1 ignored: `test_strong_sync_live_mongo_acceptance_gate` due to missing `MONGO_TEST_URI`), clippy clean (0 compiler warnings).
- Vector runner passes 23/23 checks (`scripts/strict-v2-vector-runner.mjs`).
- Live smoke offline fixture self-test passes 18/18 checks (`node scripts/hub-live-smoke.mjs --fixture-test`).
- Shared Kotlin Multiplatform JVM test suites pass 116 tests (`./gradlew :shared:jvmTest --dependency-verification=off`).
- Production admission remains on hold: Phase 03 backend contracts and logic are delivered with passing unit/contract checks; (1) Gate G3 live multi-instance replica-set acceptance is unexecuted pending staging testbed, (2) coordination-document compatibility/backfill for documents missing `permitCount` requires live verification, and (3) exact cumulative accounting across mid-batch crash interruptions requires formal revalidation.

---
## [Phase 02] - 2026-10-15: Native Delivery & Strict DST Resolution (Gate G2 Blocked on Host Prerequisites)

### Added
- Strict timezone and DST resolution contract in `:shared`:
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-timezone-resolver.kt`: `StrictTimeZoneResolver` interface and `StrictZoneOffsetResult` sealed hierarchy (`Single`, `Overlap`, `Gap`). Rejects non-existent spring-forward gap instants (`IllegalArgumentException`), requires explicit offset disambiguation for fall-back overlap hours, and calculates calendar day boundaries with calendar arithmetic (23h/25h).
  - Platform actual bindings delegating to standard `java.time.zone.ZoneRules`: `shared/src/jvmMain/kotlin/dev/tabbyrush/shared/contract/strict-timezone-resolver-jvm.kt` and `shared/src/androidMain/kotlin/dev/tabbyrush/shared/contract/strict-timezone-resolver-android.kt`.
  - Comprehensive vector test suite: `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-timezone-resolver-test.kt` exercising 7 test vectors (NY spring gap, NY fall overlap, standard unambiguous time, 23h and 25h calendar day boundaries, input bounds validation, and midnight gap transitions).
- Candidate mobile notification adapter & receiver in `:androidApp`:
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-adapter.kt`: Notification channel setup (`tabbyrush_reminders`), intent preparation, and AlarmManager exact alarm scheduling (`setExactAndAllowWhileIdle`).
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/android-reminder-capabilities.kt`: Independent capability probes decoupling `POST_NOTIFICATIONS` (API 33+) from `SCHEDULE_EXACT_ALARM` (`AlarmManager.canScheduleExactAlarms()` on API 31+).
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-alarm-receiver.kt`: Non-exported `BroadcastReceiver` (`android:exported="false"`) handling offline alarm dispatch with strict intent extras validation.
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/android-notification-adapter-test.kt`: Unit tests verifying action and extra constants, intent generation, and capabilities data classes.
- Candidate desktop notification adapters in `:desktopApp`:
  - `desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt`: Linux D-Bus candidate communicating with `/org/freedesktop/Notifications`, mapping missing daemons (`ServiceUnknown`) and absent session bus to `LinuxNotificationStatus.Unavailable` without crashes.
  - `desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter-test.kt`: 4 unit tests verifying missing daemon handling, missing session handling, safe host probing, and capabilities under mock proxy.
  - `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt`: Windows toast candidate configuring AUMID `dev.tabbyrush.desktop`, XML attribute/entity escaping, and tag/group toast clearance. Includes `JnaWindowsToastBridge` explicitly returning `isAvailable() = false` and `WindowsNotificationStatus.Unavailable` pending native WinRT/COM implementation and Windows testbed in Phase 09.
  - `desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter-test.kt`: Unit tests verifying XML escaping, XML payload construction, and explicit bridge unavailability.

### Changed
- `androidApp/src/main/AndroidManifest.xml`: Added `SCHEDULE_EXACT_ALARM` and `POST_NOTIFICATIONS` permissions, and declared non-exported `TabbyRushAlarmReceiver` (`android:exported="false"`).
- `desktopApp/gradle-linux.lockfile`: Reconciled locked artifacts to remove stale JNA dependencies while maintaining Linux target dependency isolation.
- `docs/android-acceptance.md`: Updated Section 4.2 with Phase 02 mobile acceptance matrix, recording candidate adapter and receiver verification status as unexecuted pending host Android SDK/device in Phase 08.
- `docs/linux-acceptance.md`: Updated Section 4.2 with Phase 02 Linux desktop acceptance matrix, recording candidate adapter status and missing daemon probe on workstation session.
- `docs/windows-acceptance.md`: Updated Section 4.2 with Phase 02 Windows desktop acceptance matrix, recording candidate adapter status as source-inspected/unexecuted pending Windows testbed in Phase 09.
- `docs/system-architecture.md`: Updated status to reflect Phase 02 deliverables and Gate G2 blocked status; added Section 4.10 (Native Notification Candidates & Strict Timezone Architecture), Invariants 29–32, and Section 6.5.

### Verification and Gate G2 Status
- Reported automated test runs pass 147 tests across `:shared:jvmTest` (115 tests, including 7 strict timezone vectors) and `:desktopApp:test` (32 tests, including 4 Linux D-Bus tests) under `--dependency-verification=off` (recorded in Phase 02 QA and Code Review reports).
- Strict root dependency verification (`./gradlew without --dependency-verification=off`) remains failing until platform lockfile hashes are unified.
- Gate G2 physical native delivery is formally recorded as **BLOCKED** per specification due to missing implementation and workstation prerequisites: Android SDK/device absent (Phase 08), active desktop notification daemon absent on user D-Bus session (Phase 09), and Windows native WinRT/COM implementation and host runtime absent (Phase 09).
---
## [Phase 01] - 2026-10-15: Freeze Temporal Model and Strict Transition (Gate G1 Frozen)

### Added
- Canonical schema specifications:
  - `integration/glean-hub/tabbyrush-schema-v1.json`: Canonical `strict-v1` schema with 4 columns (`id`, `title`, `completed`, `createdAt`), SHA-256 fingerprint `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`.
  - `integration/glean-hub/tabbyrush-schema-v2.json`: Canonical `strict-v2` target schema with 16 columns (adding `deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`), SHA-256 fingerprint `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`.
- Binary test vectors:
  - `integration/glean-hub/strict-v2-vectors.json`: 20 cross-stack cryptographic reference test cases (9 valid vector preimages + SHA-256 digests; 11 boundary and overflow rejection vectors).
- Kotlin Multiplatform domain contracts and digest engine:
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v2-contract.kt`: `StrictV2Constants`, `StrictV2TodoData`, `StrictV2OpKind`, `StrictV2SyncOp`, `LegacyReceiptStatus`, `LegacyReceiptResult`, with checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) rejecting overflow wrapping at intermediate and terminal operations.
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v2-digest.kt`: `StrictSyncV2Digest` calculating SHA-256 binary preimages with framed identity prefix (8 length-prefixed strings + big-endian 8-byte signed expected revision), 0 bytes for DELETE, and canonical presence-tagged field framing for CREATE/UPDATE.
- Test suites & verification runners:
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-v2-digest-vector-test.kt`: 5 vector tests verifying migrated null, date-only, timed DST overlap, repeating count 10, and disabled rule preimages against fixture.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-v2-digest-extended-test.kt`: 5 extended tests exercising cleared deadlines, Unicode strings, zero-byte DELETE, negative epochs, and all boundary/overflow rejection cases.
  - `scripts/strict-v2-vector-runner.mjs`: Independent Node.js test runner verifying preimage framing, SHA-256 digests, boundary rejections, and automated schema fingerprint derivation.
  - `scripts/generate-strict-v2-vectors.mjs`: Vector generation utility producing canonical fixture vectors.

### Changed
- `docs/system-architecture.md`: Added Section 6 documenting `strict-v2` wire schema, bit-level binary digest framing, and planned management endpoints (`/strict-contract`, `/strict-upgrades`), app-scoped admin fencing, and legacy receipt reconciliation.
- `docs/local-storage-and-sync.md`: Added Section 7 detailing planned Room v1 -> v2 schema evolution, migrated default policies, outbox provenance fields (`protocolVersion`, `dispatchedOnce`, `changedFieldsMask`), legacy operation receipt reconciliation states (`PROVEN_ACCEPTED`, `PROVEN_NOT_ACCEPTED`, `UNKNOWN_SUPERSEDED`, `INTEGRITY_FAILURE`), intent rebase invariants, and Section 7.3 mathematical invariants.
- `docs/hub-schema-integration.md`: Added versioned schema upgrade specification, canonical schema fingerprints, planned management endpoints, and admin fencing lifecycle.
- `docs/code-standards.md`: Added coding standards for strict-v2 preimages, checked 64-bit integer arithmetic, temporal model representations, and error code conventions.
- `docs/codebase-summary.md`: Updated codebase inventory, Repomix metrics, directory structure, and architectural invariants for strict-v2 contracts and scripts.
- `docs/project-overview-pdr.md`: Updated PDR to Version 1.8.0 reflecting Phase 01 / Gate G1 freeze specifications and deliverables.

---
## [2026-10-15] - Direct Target Collection Migration (FR-STRICT-02)

### Changed
- Migrated Glean Hub `strict-v1` backend storage resolution from dynamic epoch-based formula (`_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>`) to direct application target collection (`app_<appId>`, e.g. `app_tabbyrush`).
- Updated `AppService::strict_collection_name` and `validate_strict_admission` in `glean-hub-sync` to validate and admit target collection `app_<appId>` with compound scan index `{ userId: 1, tableName: 1, rowId: 1 }`, uncapped storage, and no TTL indexes.
- Reconciled MongoDB standalone topology check in admission preflight to handle both `isWritablePrimary` and `ismaster`.
- Updated TabbyRush maintenance recovery script `scripts/hub-strict-recovery.js` to target `app_${CONFIG.appId}` with collision safeguards.
- Rechecked TabbyRush shared KMP and Android client modules, confirming zero breaking changes and full strict-v1 protocol compatibility.
- Added operator migration runbook: existing `_strict_v1_5_tabbyrush_<cleanEpoch>` collections can be renamed in `mongosh` via `db.getCollection(oldCollectionName).renameCollection("app_tabbyrush")` before running `strong_sync_admission`.

---

## [Phase 10] - 2026-10-14: Native Windows DPAPI & Installed MSI Implementation

### Added
- Windows `WindowsDpapiVault` using user-scoped `CryptProtectData` / `CryptUnprotectData`, atomic replacement, and fail-closed user ACL enforcement; `testAclEnforcementOnStoredVaultFile` checks the stored-file owner ALLOW entry.
- `WindowsDpapiVaultProvider` ServiceLoader registration; Windows runtime excludes Linux D-Bus dependencies.
- `desktopApp/gradle-windows.lockfile` and `scripts/windows-package-smoke.ps1` with COM metadata inspection.
- Windows evidence summary reports the `TabbyRush.exe` distributable and unsigned `TabbyRush-1.0.0.msi` (100.68 MB) built and verified; seven high-fidelity Windows UI design mockups.

### Verification and remaining acceptance
- Evidence summary reports 121/121 combined desktop and shared JVM tests passing, target isolation verified, and MSI generated/package metadata inspected.
- Phase 10 implementation delivered / 100% on 2026-10-14. Technical acceptance is blocked by root-level strict dependency verification, interactive MSI installation, and Narrator verification; clean-user runtime/sync and uninstall/reinstall remain pending. The MSI is unsigned and is not a production release.

---

## [Phase 09] - 2026-10-14: Linux Desktop & Installed RPM Distribution
### Added
- `:desktopApp` module configuration with Compose Multiplatform 1.7.3 on Kotlin JVM 2.1.10.
- Target isolation and custom dependency locking (`gradle-linux.lockfile`, 161 locked artifacts).
- `CredentialVaultProvider` SPI and `PlatformVaultDispatcher` for fail-closed ServiceLoader resolution.
- `SingleInstanceGuard` exclusive file-channel lock (`~/.local/share/tabbyrush/accounts/tabbyrush.lock`).
- `DesktopSyncScheduler` foreground-only sync scheduler with `LeaveSchedulerCancelGate` quiescence.
- `LinuxSecretServiceVault` storing tokens via Freedesktop Secret Service (`secret-tool` / D-Bus).
- Desktop test suites: `PlatformVaultDispatcherTest` (5/5 PASS), `DesktopLifecycleTest` (8/8 PASS), `LinuxSecretServiceVaultTest` (6/6 PASS).
- Packaging diagnostics runner `scripts/linux-package-smoke.sh`, runbooks, and 7 visual design mockups.

---
## [Phase 08] - 2026-10-14: Authorized Hub Operations & Signed Android Release
### Added
- Operational tooling: `scripts/hub-live-smoke.mjs` (fixture mode passing 18/18 checks), `scripts/release-security-check.mjs` (static diagnostic mode), and `scripts/hub-strict-recovery.js` (quarantined strict-v1 emergency cold restore).
- Operational runbooks: `deployment-guide.md`, `security-and-recovery.md`, `release-acceptance.md`, and evidence summaries.
- 7 pixel-precise release visual design baseline mockups (`docs/evidence/android-release-*.png`).
---
## [Phase 07] - 2026-10-14: Android Composition & Runtime Integration
### Added
- `:androidApp` module (AGP 8.7.3, minSdk 26, compileSdk 35) with `TabbyRushApplication` process container and fail-closed wipe recovery.
- `SessionViewModel` with reactive `TodoUiState`, conflict review dialog, and zero credential retention.
- WorkManager `AndroidSyncScheduler` and `TabbyRushSyncWorker` with account-tagged cancellation.
- Security rules (`backup_rules.xml`, `data_extraction_rules.xml`, loopback `network_security_config.xml`).
- Instrumented device tests (`todo-device-test.kt`), smoke runners (`scripts/android-smoke.sh`), and mockups.

---
## [Phase 06] - 2026-10-14: Shared Compose UI & Accessible Light Theme
### Added
- Shared Compose UI components in `shared/`: `TabbyRushApp`, `ResponsiveLayout`, `TodoList`, `TodoItemRow`, `TodoEditorDialog`, `SyncStatusBar`, `ConflictDialog`, `LeaveDialog`.
- Accessible light theme tokens conforming to WCAG 2.1 AA contrast requirements.

---
## [Phase 05] - 2026-10-14: Durable Local State & Strict Per-Record Sync
### Added
- Room KMP persistence (`TabbyRushDatabase` v1) with `BundledSQLiteDriver`.
- Local outbox mutation engine, staged full-scan reconciler, and fail-closed account teardown protocol.
- 89 JVM common and Room integration tests.

---
## [Phase 04] - 2026-10-14: Hub Authentication & Protected Session
### Added
- Hub authentication client with Ktor HTTP transport and single-flight token refresh coordinator.
- `AndroidKeystoreVault` hardware-backed credential storage and generation-fenced session state machine.

---
## [Phase 03] - 2026-10-14: Isolated Strict Server & Legacy Isolation
### Added
- Glean Hub strict-v1 protocol extension in Rust (`glean-hub-sync`), dedicated strict routes, and legacy isolation guards.

---
## [Phase 02] - 2026-10-14: Frozen Foundation & Contracts
### Added
- Root Gradle multiplatform build baseline with Gradle 8.11.1 wrapper and pinned version catalog.
- 16 core Kotlin domain contracts, NIST digest test vectors, and validation test suites.

---
## [Phase 01] - 2026-10-13: Headless Tooling & Android Environment
### Added
- Headless Xpra virtual display runner (`scripts/start-xpra.sh`) and Android SDK/emulator lifecycle scripts.
- Operational setup runbooks and host evidence records in `docs/`.
