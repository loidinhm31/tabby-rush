# Phase 04 — Integration, rendered semantics, and full verification

## Overview
- Priority P2; status completed administratively on 2026-10-17 per request; effort 5h. Acceptance remains incomplete; see the gate below.
- Main integration owner runs verification after changes land; earlier phases must not run competing builds/tests/formatters.
- Existing `TodoSemanticsTest` checks models/validation, not rendered Compose nodes. Preserve existing tests and add real rendered coverage; state-copy assertions alone do not verify this migration.
- Context: [master gates](plan.md), [official common UI testing](https://kotlinlang.org/docs/multiplatform/compose-test.html), [design/testing limitations](../../docs/design-guidelines.md#6-phase-02-frozen-contract-gaps--downstream-deferrals).

## Exclusive ownership
Modify only:
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt`
- `shared/build.gradle.kts`
- `shared/gradle.lockfile` and `gradle/verification-metadata.xml` only where test dependency changes require updates
- `docs/system-architecture.md`, `docs/codebase-summary.md`, `docs/design-guidelines.md`
- `docs/project-changelog.md`, `docs/development-roadmap.md`
No created/deleted production files. Phase 01 retains ownership of `date-time-pickers.kt` and `date-time-pickers-test.kt`; consumer fixes return to their original owner, serially.
Store command/device evidence in the final integration report and existing plan success/evidence sections; do not create miscellaneous scripts or standalone docs.

## Test harness and dependency gate
1. Read actual Compose 1.7.3 test API/artifacts before adding dependencies. Current official docs describe newer versions and `v2` APIs: do not copy their version numbers, Android Gradle DSL, or imports blindly.
2. Add version-aligned `compose.uiTest` to `commonTest` using the Compose plugin's 1.7.3 accessor/experimental opt-in, plus `compose.desktop.currentOs` to `jvmTest` for desktop runtime.
3. Use pinned `androidx.compose.ui.test.runComposeUiTest`/`ExperimentalTestApi` API as provided by 1.7.3, with kotlin.test tests; do not add an Android-only JUnit rule to commonTest.
4. Keep dependency changes test-only; do not upgrade Kotlin/Compose/catalog versions or change production source-set relationships. Reuse existing Android instrumentation suite without introducing a second harness.
5. Resolve/update only affected lock entries; review artifact provenance. Existing verification metadata has an empty components list: do not present generated hashes as an independently trusted baseline or repair unrelated release holds.
6. JVM rendered tests need supported graphics/native runtime and an active Windows desktop or Linux virtual display. Missing runtime/display is a blocker to rendered verification, not grounds to substitute mocks, skip tests, or assert only fake state transitions.

## Authored coverage matrix
Use actual `TabbyRushTheme` and production composables; state-hoist `EditorState` from captured real `UpdateEditorState` callbacks, collecting emitted actions. Settings tests collect actual outer actions. Exercise built-in picker nodes using their pinned semantics; do not call callbacks directly to simulate a user choice.

| Surface | Required assertions |
|---|---|
| Shared date dialog | Valid initial date selected; blank/invalid date has disabled Confirm; choose a real day; Confirm returns canonical date exactly once. Calendar mode only; no input-mode switch. |
| Shared time dialog | Existing `00:00`, `09:05`, `23:59` initialize correctly; clock selection confirms padded 24-hour value; no `TimeInput` or typing mode. |
| Picker lifecycle | Tentative selection then Cancel or dismiss emits no value; reopening returns to committed value. Blank/invalid initial fallback is not committed just by opening. |
| All four triggers | Present label/value, click action and button role, >=48dp target; no `EditableText` or `SetText`. Date/time cannot be changed with text input. |
| Deadline date | Choose calendar day, get one `UpdateEditorState` changing date and clearing offset only; preserve title, zone, reminder rule and other fields. |
| Deadline clock | Toggle timed mode, select hour/minute via clock, get canonical time and cleared offset; cancel/unchanged confirmation preserves previous offset. |
| Date-only anchor | Clock selection changes anchor only plus offset reset; timed field absent; computed deadline has null time and next local-day overdue. |
| Deadline visibility | Deadline disabled hides all triggers; time toggle chooses correct trigger. Closing/changing task or hiding branch disposes active modal without stale actions. |
| Settings draft | Confirm changes displayed anchor but emits no `SaveSettings`; cancel subsequent tentative edit preserves prior confirmed draft. |
| Settings Save/Cancel | Outer Save emits selected canonical anchor with other preferences unchanged; outer Cancel emits only `CloseSettings`; reopening original settings shows original saved value. |
| Settings independence | Anchor visible when default reminders disabled; numeric controls, notification toggles and capability/permission actions retain behavior. |

## Domain regression coverage
- Run Phase 01 utility tests, including UTC epoch fixtures, leap day/year boundaries, pre-1900/post-2100 years, invalid initial strings, and `00:00`/`23:59` formatting.
- Extend existing actual validator tests using picker-produced canonical values; retain impossible calendar date tests and numeric reminder bounds/last-occurrence tests.
- America/New_York `2026-03-08 02:30`: timed and date-only anchor variants reject DST gap without auto-shifting time.
- America/New_York `2026-11-01 01:30`: overlap remains invalid until either explicit offset is chosen; actual date/time/anchor change clears previous choice; cancel/unchanged confirmation does not.
- Preserve date-only 23h/25h calendar-day boundary behavior, strict-v2 save restriction, existing task schedule-token semantics, and settings local-only contract. Reuse existing domain/controller suites rather than modify their production code.
- Inspect semantics of actual picker output and triggering action; do not claim snapshots of manually constructed `editor.copy(...)` validate callback wiring.

## Integration sequence
1. Collect both consumer terminal reports; check changed-file lists against exclusive ownership and re-audit all date/time entry points. Inventory must remain four fields across the two consumers; numeric duration/count and IANA zone inputs are intentional exclusions.
2. Add harness/dependencies and rendered tests; maintain existing unrelated tests. Keep additions in focused test classes/helpers within the owned semantics file rather than refactor its pre-existing large body.
3. Review test/runtime wiring and component/consumer contracts; route production defects to the owner before running final suites. No concurrent owner edits while verifying.
4. Update owned architecture/design docs to describe the new shared picker-to-canonical-string flow, confirmation boundary, UTC date invariant, retained DST resolver, and actual test scope; update summary/changelog/roadmap only with implementation facts.
5. Run complete verification below, sequentially. Record exact commands and failures; fix root cause and rerun impacted checks, never suppress exceptions/tests or weaken assertions.
6. Record native manual acceptance separately. Main owner performs final source/side-effect review and marks gates only when evidence supports them.

## Full verification commands
Run from repository root with JDK 21 and configured Android SDK. Windows shown; use `./gradlew` on Linux. `--dependency-verification=off` matches existing README's diagnostic baseline and does **not** establish dependency/release acceptance.
```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test :androidApp:testDebugUnitTest :androidApp:assembleDebug :desktopApp:classes --dependency-verification=off
node --test scripts/release-security-check.test.mjs scripts/hub-strict-upgrade.test.mjs scripts/debug-fault-proxy.test.mjs
node scripts/strict-v2-vector-runner.mjs
node scripts/hub-live-smoke.mjs --fixture-test
node scripts/strict-v2-cutover-rehearsal.mjs --self-test
node scripts/release-security-check.mjs --diagnose-source
.\gradlew.bat :androidApp:connectedDebugAndroidTest --dependency-verification=off
```
- Execute unfiltered JVM/desktop/Android unit suites, not only new picker classes. `assembleDebug` checks shared Android production compilation; desktop classes check selected host integration.
- Run connected tests on an isolated configured emulator/device. No live Hub writes; use fixture/self-test modes only.
- Desktop target must match host; never fake `-PdesktopTarget=linux` on Windows. Run host-specific suite and rendered acceptance again on Linux runner for Linux evidence.
- Record failures, skipped counts, unavailable devices/displays, and pre-existing infrastructure holds separately; unavailable checks remain incomplete, not passed.

## Native interaction/accessibility acceptance
- Android and desktop: create/edit timed and date-only tasks and default settings using only calendar/clock controls; reopen and inspect persisted values.
- Test mouse/touch, Tab/Shift-Tab/Enter, clock hour/minute precision, Cancel, outside dismissal, Android Back, desktop Escape, and focus return to trigger without closing parent unexpectedly.
- TalkBack on Android; Narrator on Windows and Orca on Linux where supported: labels, current value, modal heading, selected date/time and error announcement; no duplicate/hidden editable target.
- Compact Android portrait/landscape and small desktop viewport at 100%/200% font scale: dial, calendar and dialog actions remain reachable without clipping; no keyboard needed for date/time.
- Test device zones on both sides of UTC, including America/Los_Angeles and Pacific/Kiritimati: chosen calendar date does not shift. Deadline IANA zone remains independently editable.
- Browsing/canceling pickers must not mutate tasks/defaults, rotate tokens, save drafts through a new path, schedule alarms, launch permissions, or send network traffic.

## Success criteria
- All four date/time fields migrated; no manual date/time editing path, stale dialog state, or phase ownership violations.
- Pure tests and actual rendered semantics/interactions pass alongside complete existing regression suites; Android/desktop production compilation passes.
- Exact executed command results and native acceptance evidence recorded; missing platform facilities explicitly keep the affected gate open.
- Architecture, summary, design testing limitations, changelog, and roadmap accurately reflect delivered behavior and evidenced coverage; unrelated release holds unchanged.

## Execution evidence and acceptance hold

*Reported results for 2026-10-17; no checks were rerun during this status update. No supplied Gradle run validates the latest `yearRange = 0..9999` source change.*
- `./gradlew.bat :shared:jvmTest :desktopApp:test :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks` — 372/372: shared 239, desktop 99, Android 34.
- `./gradlew.bat :androidApp:assembleDebug :desktopApp:classes --dependency-verification=off` — reported passed.
- Node fixture/security/rehearsal scripts — 32/32 combined (18 fixture + 6 source audit + 8 rehearsal); see [QA report](../reports/qa-261017-0731-date-time-picker-test-validation.md). Review separately recorded Node test-runner and strict-v2 vector runs; see [code review](../reports/code-review-261017-0745-date-time-pickers.md).
- The earlier QA report recorded two failed shared assertions; later reviewer reruns passed. The cause of the red-to-green transition is undocumented.
- **Acceptance remains incomplete:** no post-range-change Gradle rerun is evidenced; no `runComposeUiTest` rendered picker interaction harness; Android connected tests and native Android/desktop/Linux accessibility checks are not evidenced. Source sets DatePicker `yearRange = 0..9999`, but actual boundary-year selection and dialog interaction are unverified; keep these gates open.

## Risks, security, and unresolved questions
- Nested modal behavior, accessibility and graphics runtime availability are acceptance risks; test real production UI, do not mask failures with a fake picker.
- No account/security/persistence protocol changes; test dependencies/provenance reviewed and no live backend side effects.
- No unresolved product questions. Native device/display availability and pinned test runtime compatibility are execution prerequisites to evidence, not reasons to reduce scope.
