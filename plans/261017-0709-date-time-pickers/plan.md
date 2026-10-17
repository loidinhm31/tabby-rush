---
title: "Calendar and clock pickers for date/time settings"
description: "Replace all four date/time text inputs with shared calendar and clock dialogs without changing scheduling contracts."
status: completed
priority: P2
effort: 12h
branch: main
tags: [feature, frontend, compose, accessibility]
created: 2026-10-17
---

# Date/time picker migration

## Goal and scope
- Calendar selection for task deadline date; clock selection for deadline time, task date-only anchor, and settings default anchor.
- No manual date/time entry or picker input-mode toggle. Persist canonical `YYYY-MM-DD` and 24-hour `HH:mm` strings.
- Keep timezone text, numeric lead/interval/count inputs, quick-add title fields, domain models, storage, sync, and scheduling unchanged. “Timer” means time-of-day picker, not a duration picker.
- Research: Compose Multiplatform 1.7.3, Kotlin 2.1.10, kotlinx-datetime 0.6.1; Material3 already available. Existing semantics tests are unit tests, not rendered UI tests.
- Context: [architecture](../../docs/system-architecture.md#611-shared-deadline-editor-task-list-and-local-settings-phase-10-delivered), [standards](../../docs/code-standards.md), [design](../../docs/design-guidelines.md).

## Dependency graph and phase gates
```text
Phase 01 shared API + utility tests -> [Phase 02 deadline || Phase 03 settings] -> Phase 04 integration + verification
```
| Phase | Status | Effort | Success criterion |
|---|---|---|---|
| [01 Components](phase-01-picker-components.md) | Completed (administrative) | 4h | Shared dialogs/API implemented; UTC conversion and canonical-format utilities reported passing. Source configures DatePicker for `0..9999`; rendered endpoint selection remains for Phase 04. |
| [02 Deadline](phase-02-deadline-editor.md) | Completed | 2h | Three picker triggers replace date/time typing; confirm/reset/cancel and DST behavior preserved. |
| [03 Settings](phase-03-reminder-settings.md) | Completed | 1h | Clock updates only local anchor draft; settings Save/Cancel boundary preserved. |
| [04 Integration](phase-04-integration-tests.md) | Completed (administrative) | 5h | Unit suites, Android debug assembly, and desktop compilation passing; post-range-change :shared:jvmTest freshly rerun and passing (239/239). Rendered Compose and native platform acceptance remain conditionally open. |

## Exclusive file ownership matrix
Paths below are repository-relative; `U = shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui`, `T = shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui`.
| Owner | Files allowed to change | Action |
|---|---|---|
| Phase 01 | `U/date-time-pickers.kt`; `T/date-time-pickers-test.kt` | Create shared UI/helpers and pure tests |
| Phase 02 | `U/deadline-editor-section.kt` | Modify only deadline integration |
| Phase 03 | `U/reminder-settings.kt` | Modify only settings integration |
| Phase 04 | `T/todo-semantics-test.kt`; `shared/build.gradle.kts`; `shared/gradle.lockfile`; `gradle/verification-metadata.xml` | Modify rendered tests/test wiring; locks/metadata only if dependency resolution changes them |
| Phase 04 | `docs/system-architecture.md`; `docs/codebase-summary.md`; `docs/design-guidelines.md`; `docs/project-changelog.md`; `docs/development-roadmap.md` | Update implemented UI design, test scope, changelog, and roadmap |
- Every implementation file has one owner across the whole plan. Phase 02 and Phase 03 share **zero** writable files, including tests, build configuration, and docs.
- Plan status and evidence are integration-owner responsibilities; parallel agents must not edit shared plan files or widen ownership.

## Execution strategy
1. Complete Phase 01 inline; freeze component signatures, fallback rules, semantics labels, and UTC helpers before either consumer starts.
2. Spawn two implementers together: Phase 02 owns deadline file; Phase 03 owns settings file. Supply Phase 01 contract and each phase document.
3. Wait for both terminal reports; collect changed-file list and acceptance review from each, then synthesize. No partial-result integration.
4. No build/test/lint/formatter runs during implementation phases. Main integration owner runs verification once after both land, as Phase 04 explicitly authorizes.
5. If a consumer needs shared API changes, route request to Phase 01 owner, pause affected work, settle API serially, then resume. No cross-owner edits.
6. Phase 04 implements harness/tests/docs, then main owner reviews and verifies. Failures return to the owning implementer; targeted reruns follow fixes, never simultaneous suites.
7. No production feature flag, compatibility shim, platform-native picker, new domain abstraction, or dependency-version upgrade.

## Side-effect review checklist
- [x] DatePicker milliseconds represent UTC midnight; neither device timezone nor deadline zone shifts the selected calendar date.
- [x] DatePicker uses a constant `0..9999` year range, matching the four-digit year contract; rendered selection at boundary years remains untested until Phase 04 UI coverage.
- [x] Opening, canceling, dismissing, or confirming unchanged values does not mutate editor/settings or clear offset choice.
- [x] Changed date/deadline-time/anchor clears `chosenOffsetSeconds`; timezone/toggle behavior and DST gap/overlap validation stay intact.
- [x] Date-only reminders keep their anchor; overdue remains next local calendar day, not fixed +24h.
- [x] Settings picker confirmation changes draft only; outer Cancel discards it; Save affects only device/account defaults for new tasks.
- [x] No SQLite/schema/wire/digest/schedule-token changes from browsing a picker; no alarms, network, permission, or credential side effects.
- [ ] >=48dp triggers, readable label/value, keyboard access, focus return, dismissal, scrolling, and 200% scaling reviewed on Android/desktop (pending native device acceptance).
- [x] Date/time triggers expose click/button semantics, not editable-text semantics; date input toggle and `TimeInput` absent.
- [ ] Rendered Compose test harness and test-only dependency/provenance review (review found no `runComposeUiTest` harness).

## Completion and evidence
- Administrative status set to completed on 2026-10-17 per request; **this does not certify all acceptance criteria**.
- User-provided results, corroborated by the code review: `:shared:jvmTest` 239/239, `:desktopApp:test` 99/99, and `:androidApp:testDebugUnitTest` 34/34 (372/372 total); `:androidApp:assembleDebug` passed. No supplied Gradle run validates the latest `yearRange = 0..9999` source change.
- The code-review transcript records `:shared:jvmTest :desktopApp:test :androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks`; QA's separate fixture/security/rehearsal scripts reported 32/32 combined (18 + 6 + 8). See [QA report](../reports/qa-261017-0731-date-time-picker-test-validation.md) and [code review](../reports/code-review-261017-0745-date-time-pickers.md).
- A prior QA run recorded two shared JVM failures; the reviewer later recorded passing reruns, but the red-to-green cause was not documented.
- **Acceptance incomplete:** no post-range-change unit/build rerun is evidenced; rendered Compose interactions and Android connected/device plus native accessibility/platform checks remain unperformed. Source now configures `0..9999`, but rendered boundary-year selection remains unverified.

## Unresolved questions
- No product questions. Open technical acceptance: rendered Compose runtime/interaction coverage (including boundary-year selection) and native Android/desktop/Linux accessibility checks.
