# Code Review Summary: Date & Time Picker Migration

## Scope
- Files reviewed:
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/date-time-pickers.kt` (New, 180 lines)
  - `shared/src/commonTest/kotlin/dev/doggo/shared/ui/date-time-pickers-test.kt` (New, 111 lines)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/deadline-editor-section.kt` (Modified, lines 40–397)
  - `shared/src/commonMain/kotlin/dev/doggo/shared/ui/reminder-settings.kt` (Modified, lines 40–309)
  - `shared/src/commonTest/kotlin/dev/doggo/shared/ui/todo-semantics-test.kt` (Modified, lines 605–673)
  - Working tree configuration check: `gradle.properties` (Modified outside plan ownership)
- Lines of code analyzed: ~820 lines across target files + full test/build inspection
- Review focus: Date & time picker migration, UTC roundtrip invariants, DST gap/overlap preservation, Compose Multiplatform best practices, accessibility (touch targets >=48dp, contrast), and test coverage completeness against Phase 01–04 plan requirements
- Updated plans: `plans/261017-0709-date-time-pickers/plan.md`

---

## Overall Assessment
**Recommendation: REQUEST CHANGES (Conditional Hold on Integration Gate)**.

Core implementation quality of the shared components and consumer integrations is solid:
- Replaced 4 text-typing inputs with calendar and clock pickers without breaking domain scheduling contracts.
- Strictly preserved UTC roundtrip conversion via `TimeZone.UTC`.
- Preserved DST gap rejection and fall-back overlap disambiguation (clearing `chosenOffsetSeconds` on value changes, preserving it on cancel/unchanged confirm).
- Accessible touch targets (`>=48.dp` via `DoggoThemeTokens.MinTouchTargetSize`), role button semantics, and WCAG AA contrast (DoggoMuted 6.28:1, DoggoInk 17.85:1).
- Unfiltered test suites pass: `:shared:jvmTest`, `:desktopApp:test`, `:androidApp:testDebugUnitTest` (372 unit tests total), plus 32 Node test-runner checks and operator self-tests.
- Android debug assembly (`:androidApp:assembleDebug`) and desktop compilation (`:desktopApp:classes`) build cleanly.

However, the change set cannot be approved for final integration completion yet because:
1. **Rendered UI test gate omitted**: Phase 04 explicitly required rendered Compose node tests (`runComposeUiTest`) exercising actual picker interaction, semantics trees, and modal dismissals. Only model-level unit tests were added in `todo-semantics-test.kt`.
2. **Untracked shared files & ownership boundary violation**: `date-time-pickers.kt` and `date-time-pickers-test.kt` are untracked; `gradle.properties` contains an unreviewed modification (`org.gradle.tooling.parallel=true`).
3. **Open native acceptance gates**: Android connected tests, Linux host verification, and physical screen reader/font-scaling verification remain unexecuted.

---

## Critical Issues
None. Zero OWASP vulnerabilities, secret leaks, breaking database migrations, or scheduling contract regressions found.

---

## High Priority Findings

### 1. Missing Rendered Compose UI Tests (Phase 04 Incomplete)
- **Problem**: `phase-04-integration-tests.md` mandated adding version-aligned `compose.uiTest` and implementing rendered Compose node tests using `runComposeUiTest` to verify:
  - Button role, click semantics, and `stateDescription` on all 4 triggers.
  - Dialog launch, cancellation without state leakage, and confirmed selection dispatch.
  - No `EditableText` / typing path exposed to assistive technology.
- **Current State**: `shared/build.gradle.kts` lacks `compose.uiTest`, and tests added in `TodoSemanticsTest` (lines 605–673) only invoke `DateTimePickerFormats` and `DeadlineCalculatorHelpers` directly on synthetic `EditorState` instances.
- **Impact**: Picker composables, dialog presentation, and button click handlers have zero automated interaction coverage; regressions in Compose rendering or event dispatch will not be caught in CI.
- **Action**: Add version-aligned `compose.uiTest` in `commonTest` / `jvmTest` and author rendered tests verifying actual node semantics and click interactions.

### 2. Reconciliation of Prior Red Suite in QA Report
- **Problem**: Preceding QA report (`qa-261017-0731-date-time-picker-test-validation.md`) reported two test failures:
  - `DateTimePickersTest.testDateRoundTripUtc` at line 26
  - `TodoSemanticsTest.testDeadlinePickerCanonicalFormatsAndOffsetClearing` at line 628
- **Current State**: Full reruns with `--rerun-tasks` now pass 100%. The root cause of the earlier failures (e.g. initial timestamp arithmetic or timezone offset expectation discrepancy) was not documented in git or report commits.
- **Action**: Verify the exact fix was intentional (America/New_York date 2026-11-01 fall-back vs epoch calculation) and document why the suite transitioned from red to green.

---

## Medium Priority Improvements

### 3. Hardcoded Fallback Year in `DoggoDatePickerDialog`
- **Location**: `shared/src/commonMain/kotlin/dev/doggo/shared/ui/date-time-pickers.kt:86`
- **Code**:
  ```kotlin
  val yearRange = remember(parsedDate) {
      val targetYear = parsedDate?.year ?: 2026
      minOf(1900, targetYear)..maxOf(2100, targetYear)
  }
  ```
- **Problem**:
  1. `2026` is hardcoded magic constant. If uninitialized in future years, fallback targets 2026.
  2. If `initialDate` is blank, `yearRange` defaults to `1900..2100`. A user creating a new task with date > 2100 cannot select it via the date picker grid (though existing tasks with year > 2100 expand the range dynamically).
- **Recommendation**:
  Use `Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year` as fallback target year.

### 4. Untracked Working Tree Files & Out-of-Scope `gradle.properties` Edit
- **Files**:
  - `gradle.properties`: Modified with `org.gradle.tooling.parallel=true`. Outside Phase 01–04 ownership matrix.
  - `date-time-pickers.kt` and `date-time-pickers-test.kt`: Untracked in git.
- **Action**: Revert `gradle.properties` if unrelated, or separate into an explicit build tooling commit. Track `date-time-pickers.kt` and `date-time-pickers-test.kt` in git.

---

## Low Priority Suggestions

### 5. `stateDescription` vs Visual Label for Unset Value
- **Location**: `DateTimePickerButton` lines 53, 63.
- **Detail**: When `value.isBlank()`, visual button text is `"Choose $label"`, but `stateDescription` semantics is `"Not set"`.
- **Recommendation**: Align semantics so TalkBack/Narrator announce clearly: e.g., `stateDescription = if (value.isNotBlank()) value else "Not set"`, and set button `contentDescription = "$label: ${if (value.isNotBlank()) value else "Not set"}"`.

### 6. Compose Callback Closure Captures
- **Location**: `DeadlineEditorSection.kt:333-377`
- **Detail**: Confirm lambdas capture `editor` from the enclosing composition turn. Because `DeadlineEditorSection` recomposes on any `editor` modification, this is currently safe. However, standard Compose practice is to wrap action handlers or state snapshots in `rememberUpdatedState` when passed into long-lived or asynchronous dialogs.

---

## Positive Observations
1. **Strict UTC Invariance**: `DateTimePickerFormats.utcMillisToDate` and `dateToUtcMillisOrNull` explicitly convert via `TimeZone.UTC`. DatePicker epoch millis never drift across local timezones.
2. **DST State Safety**: Opening, canceling, or confirming unchanged values does not clear `chosenOffsetSeconds`. Value changes explicitly reset `chosenOffsetSeconds = null`, cleanly forcing DST gap/overlap recalculation.
3. **Accessibility**: All buttons and interactive controls satisfy `>=48.dp` touch targets via `DoggoThemeTokens.MinTouchTargetSize`. Color contrast exceeds WCAG AA (DoggoMuted 6.28:1, DoggoInk 17.85:1).
4. **Input Mode Defense**: `showModeToggle = false` on `DatePicker` and `TimePicker` dial ensure users cannot toggle into manual typing mode, satisfying the core PDR requirement.
5. **Lifecycle Isolation**: Modal state `activePicker` is keyed on `remember(editor.id, editor.hasDeadline, editor.hasTime)`, ensuring dialogs dispose immediately when tasks switch or features are toggled.
6. **Local Settings Isolation**: Settings time picker updates local draft `defaultAnchorTime` only. `TodoAction.SaveSettings` is emitted solely on outer dialog "Save Settings" click. Outer "Cancel" cleanly drops drafts.

---

## Recommended Actions
1. **Implement Phase 04 Rendered UI Tests**:
   - Add `compose.uiTest` dependency in `shared/build.gradle.kts` aligned with Compose 1.7.3.
   - Author rendered Compose tests using `runComposeUiTest` verifying trigger nodes, button semantics, dialog display, confirmation, and offset clearing.
2. **Track New Files & Clean Git Tree**:
   - Stage `shared/src/commonMain/kotlin/dev/doggo/shared/ui/date-time-pickers.kt` and `shared/src/commonTest/kotlin/dev/doggo/shared/ui/date-time-pickers-test.kt`.
   - Revert or isolate `gradle.properties` tooling flag.
3. **Dynamic Year Fallback**:
   - Replace hardcoded `2026` in `DoggoDatePickerDialog` with dynamic year lookup.
4. **Documentation Updates**:
   - Complete required Phase 04 documentation updates in `docs/system-architecture.md`, `docs/codebase-summary.md`, `docs/design-guidelines.md`, and `docs/project-changelog.md`.
5. **Complete Platform Acceptance Gates**:
   - Execute connected Android test (`:androidApp:connectedDebugAndroidTest`) and native accessibility checks (TalkBack, Windows Narrator, 200% font scaling).

---

## Metrics
- **Compilation**: Clean (`assembleDebug`, `desktopApp:classes`)
- **Unit Tests**: 372/372 passing across `:shared:jvmTest` (239), `:desktopApp:test` (99), `:androidApp:testDebugUnitTest` (34)
- **Node Integration & Security Checks**: 32/32 tests passing; strict-v2 vector runner (10/10 vectors, 11/11 rejections), live smoke (18/18), rehearsal (8/8), manifest security audit (CLEAN)
- **Lint / Code Smells**: 0 critical, 0 high, 2 medium, 2 low

---

## Unresolved Questions
1. Why was `org.gradle.tooling.parallel=true` added to `gradle.properties`, and is it intended for source control or local development only?
2. Is Compose Multiplatform 1.7.3 UI testing runtime available in this headless CI/development environment, or is Phase 04 rendered verification blocked on display/graphics driver capabilities?
