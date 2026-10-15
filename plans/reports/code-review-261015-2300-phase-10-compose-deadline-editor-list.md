# Code Review Summary: Phase 10 — Shared Deadline Editor, Task List and Local Settings

**Date**: 2026-10-15  
**Reviewer**: Senior Software Engineer (Phase10Reviewer)  
**Target Phase**: `Phase 10 — Shared deadline editor, task list and local settings`  
**Target Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-10-compose-deadline-editor-list-and-settings.md`  
**QA Validation Base**: `plans/reports/qa-261015-2245-phase-10-validation.md` (308/308 checks passed)  
**Report Artifact**: `plans/reports/code-review-261015-2300-phase-10-compose-deadline-editor-list.md`  
**Overall Score**: **9.2 / 10** (Post-remediation; initial score 7.5 / 10 before notification and calendar exception fixes)

---

### Scope
- **Files Reviewed (15 files)**:
  - `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt` (authoritative snapshot projection hierarchy, `isAccountV2`, schedule-token semantic rotation)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-ui-state.kt` (`EditorState`, `ReminderSettingsState`, `TodoUiState`)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-action.kt` (`UpdateEditorState`, settings actions, `ClockTick`)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-calculator-helpers.kt` (`validateAndComputeDeadline`, `formatOffset`)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-editor-section.kt` (Compose deadline/reminder controls, DST gap/overlap UX)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/todo-editor.kt` (submit validation, strict-v2 requirement guard)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/todo-list.kt` (non-color overdue text, deadline badges, `nowEpochMs` clock ticks)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/reminder-settings.kt` (`ReminderSettingsDialog`, platform capabilities card)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/doggo-app.kt` (settings overlay, compact/expanded navigation buttons, clock pass-through)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/conflict-review.kt` (detailed deadline/reminder comparison, schedule-token preview)
  - `shared/src/commonMain/composeResources/values/strings.xml` (deadline & settings localized strings)
  - `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt` (Android controller, notification dispatch, settings, clock ticker)
  - `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt` (Desktop controller, settings, clock ticker, submit validation)
  - `shared/src/commonTest/kotlin/dev/doggo/shared/ui/todo-semantics-test.kt` (semantic tests for deadline, DST gap/overlap, impossible dates, overdue, settings)
  - `shared/src/jvmTest/kotlin/dev/doggo/shared/data/room-account-store-test.kt` (`testClearingDeadlineToServerBaseDoesNotResurrect`)
- **Lines of Code Analyzed**: ~1,850 lines of new and modified code.
- **Review Focus**: Security, Performance, Architecture (DRY, KISS, YAGNI), WCAG 2.1 AA Accessibility, Room outbox/projection consistency, and Task Completeness.
- **Updated Plans**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-10-compose-deadline-editor-list-and-settings.md`.

---

### Overall Assessment
Phase 10 delivers high-fidelity Compose multiplatform UI for task deadlines, reminder rules, platform settings, and conflict inspection across Android and Desktop. State models are clean and immutable. The refactoring of `RoomAccountStore.resolveTemporalProjection` permanently resolves the "Elvis chain resurrection" bug when deadlines are cleared locally against a populated server base. Two critical defects were discovered during this review (Android notification entry bypassing deadline hydration causing silent schedule wipe on edit, and uncaught `IllegalArgumentException` on impossible calendar dates crashing composition in `TodoEditor`). Both issues were surgically remediated and regression-tested during this review pass.

---

### Critical Issues (Remediated)

1. **[CRITICAL / DATA LOSS] Android Notification Selection Bypassed Deadline Hydration**
   - **File**: `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt:469`
   - **Root Cause**: `dispatchTaskSelection(accountKey, taskId)` directly constructed an unhydrated `EditorState(id = task.id, rawTitle = task.title)`. All deadline and reminder fields defaulted to `hasDeadline = false` and `reminderRule = DISABLED`. If user opened a task via notification and saved changes (e.g., editing task description), `submitEditor()` saved `deadline = null`, permanently wiping all scheduled alarms and deadlines from the task.
   - **Impact**: Silent data loss and desynchronization of scheduled alarms upon user interaction with OS notifications.
   - **Remediation Applied**: Changed `dispatchTaskSelection` to suspend function and routed through `openEdit(task)`, which fully populates `hasDeadline`, `deadlineDate`, `reminderRule`, `originalScheduleToken`, and time parameters from the stored snapshot.

2. **[HIGH / RUNTIME CRASH] Uncaught Exception on Impossible Calendar Dates Crashed Compose Recomposition**
   - **File**: `shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-calculator-helpers.kt:76,143`
   - **Root Cause**: While `DATE_REGEX` checked `YYYY-MM-DD` and bounds `month in 1..12, day in 1..31`, impossible calendar days (e.g. Feb 30, Feb 31, non-leap Feb 29 2025, April 31) passed initial checks and reached `resolver.resolveOffset(...)` and `computeNextCalendarDayStartEpochMs(...)`. `LocalDate.parse()` threw `IllegalArgumentException`, which was uncaught. Because `validateAndComputeDeadline` was called inside `remember` during composition in `TodoEditor`, typing an invalid day-of-month triggered an unhandled exception crashing the Compose composition / Activity.
   - **Impact**: UI crash when typing day-of-month > 28 in February or 31 in 30-day months.
   - **Remediation Applied**: Wrapped resolver date/time operations in try-catch in `DeadlineCalculatorHelpers.kt`, returning `DeadlineValidationResult.Invalid("Invalid calendar date or timezone configuration")`. Added regression test `testDeadlineValidationImpossibleCalendarDates` covering Feb 30/31, April 31, and non-leap Feb 29.

---

### High Priority Findings (Addressed / Tracked)

1. **Schedule Token Semantic Change Invariant Verification**
   - **Assessment**: Verified that `RoomAccountStore.saveTodoLocally` strictly enforces `hasScheduleSemanticChange()`. Editing only title or completing task retains the identical `scheduleToken`; editing deadline date, time, zone, offset, or reminder rule triggers UUID regeneration. Native alarm reconciliation will not miss updates or flap tokens.

---

### Medium Priority Improvements

1. **Controller Logic Duplication (DRY Violation)**
   - **Files**: `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt` (472 LOC) vs `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt` (818 LOC)
   - **Issue**: `inspectAndShowConflict()`, `openCreate()`, `openEdit()`, and `submitEditor()` share ~180 lines of identical parsing reconstructing `TaskDeadline` and `ReminderRule` from `ServerBaseEntity` and `OutboxEntity`.
   - **Recommendation**: Extract domain entity projection helpers (`toTaskDeadline`, `toReminderRule`) from `RoomAccountStore` private scope to shared `dev.doggo.shared.contract.TaskDeadlineMapper` or shared controller delegate classes (`ReminderSettingsController`, `TaskEditorController`) before Phase 12.

2. **Schedule Preview Epoch Timestamp Display in UI**
   - **File**: `shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-editor-section.kt:323`
   - **Issue**: Preview displays `"Reminder #${i + 1}: index ${occ.index} at instant ${occ.dueEpochMs}"`. Raw epoch milliseconds are readable for developers, but end users benefit from human-readable local date/time strings or relative countdowns ("in 2 hours").
   - **Recommendation**: Format `dueEpochMs` into local time string matching user's selected zone for preview display in Phase 12 UX refinement.

---

### Low Priority Suggestions

1. **FilterChip Minimum Touch Target Consistency**
   - **File**: `shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-editor-section.kt:200-213`
   - **Issue**: Overlap disambiguation `FilterChip` components rely on standard Material3 32dp visual height with system interactive enforcement. Other Doggo interactive elements explicitly declare `defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)`.
   - **Recommendation**: Add explicit `Modifier.defaultMinSize(minHeight = DoggoThemeTokens.MinTouchTargetSize)` for consistency with project-wide >= 48dp conventions.

2. **KMP Time Utility Portability**
   - **File**: `shared/src/commonMain/kotlin/dev/doggo/shared/ui/todo-list.kt:175`
   - **Issue**: `val currentClockMs = if (nowEpochMs > 0L) nowEpochMs else System.currentTimeMillis()` references `java.lang.System` in `commonMain`. While JVM and Android compile, standard KMP convention prefers `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()`.

---

### Positive Observations
- **Authoritative Snapshot Projection**: Refactored `resolveTemporalProjection` completely eliminates resurrection of cleared deadlines from server base or outbox ops.
- **Strict-v2 Upgrade Gate**: Strict-v2 account check cleanly prevents saving deadlines on v1 accounts (`isUpgradeBlocked` disables submit and renders clear Hub admin upgrade warning) without breaking title editing or local operations.
- **WCAG 2.1 AA Non-Color Semantics**: Overdue status is communicated with explicit text badge ("Overdue"), distinct background container, and accessibility `contentDescription = "Task is overdue"`. Completed items never show overdue.
- **DST Resolver Integration**: Both spring-forward gaps and fall-back overlaps (ambiguous offsets) are cleanly validated and disambiguated with chip selectors.
- **Checked Arithmetic**: Zero unsafe Long arithmetic; uses `safeMultiply`, `safeAdd`, `safeSubtract` throughout calculator and schedule preview.

---

### Metrics & Validation
- **Shared JVM & SQLite Tests**: 190 tests passed (+1 test `testDeadlineValidationImpossibleCalendarDates`, total duration 6.3s).
- **Desktop App Unit & Platform Tests**: 49 tests passed (duration 0.78s).
- **Android Debug Unit Tests**: 12 tests passed (duration 6.6s).
- **Contract Wire Vectors & Fingerprints**: 23/23 passed (v1 `3361a80f...`, v2 `5f539551...`).
- **Hub Offline Protocol Smoke Checks**: 18/18 passed.
- **Static Release Security Audit**: 6/6 passed (no plaintext traffic, backup disabled, no secret leakage).
- **Hub Strict Upgrade CLI**: 9/9 passed.
- **Packaging / Distributable Tasks**: `:desktopApp:createDistributable` & `:androidApp:assembleDebug` succeed cleanly.
- **Linting / Build Status**: 0 compilation errors, 0 test failures.

---

### Recommended Actions
1. **[Completed in Turn]**: Hydrate Android notification task selection via `openEdit(task)` in `session-view-model.kt`.
2. **[Completed in Turn]**: Gracefully catch invalid calendar date exceptions in `DeadlineCalculatorHelpers.kt` and add unit test.
3. **[Phase 11 Platform Acceptance]**: Exercise interactive GUI windowing, TalkBack / Orca screen-reader traversal, dynamic 200% font scaling, and live alarm firing on hardware/emulators.
4. **[Phase 12 / Refactor]**: Extract entity-to-domain temporal mapping (`inspectAndShowConflict`, `openEdit`) into shared delegate to eliminate Android/Desktop controller duplication.

---

### Unresolved Questions
1. Should date-only tasks permit custom per-task reminder anchor times (e.g. 08:00 vs 09:00), or strictly inherit the device-local account settings default?
2. In Phase 11 desktop acceptance, will Linux notification clicks be wired to a DBus action activation handler or remain informational desktop banners?
3. Should Android `session-view-model.kt` (472 LOC) and `desktop/main.kt` (818 LOC) be decomposed into dedicated `ReminderSettingsController` and `TaskEditorController` components prior to Phase 12 release?