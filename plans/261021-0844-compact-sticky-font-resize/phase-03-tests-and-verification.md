# Phase 03 — Tests, Native Verification, and Documentation

## Context Links

- [Overview](./plan.md) · [Preferences/model](./phase-01-preferences-and-model.md) · [UI/integration](./phase-02-compact-components-and-font-scaling.md)
- [Planner report](../reports/evc-planner-261021-0844-compact-sticky-font-resize.md)
- [Preference tests](../../desktopApp/src/test/kotlin/dev/tabbyrush/desktop/sticky-window-preferences-test.kt)
- [Surface/controller tests](../../desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt)
- [Windows guide](../../docs/windows-desktop-guide.md) · [Windows acceptance](../../docs/windows-acceptance.md)
- [Linux guide](../../docs/linux-desktop-guide.md) · [Linux acceptance](../../docs/linux-acceptance.md)
- [Development roadmap](../../docs/development-roadmap.md) · [Changelog](../../docs/project-changelog.md)

## Overview

Priority P2 · Status complete (automated verification complete; native UI matrix pending) · Effort 4h · Owner main integration/verification owner.

Tests may be authored after phase 01 contract freeze; executable verification waits until all implementation lands. This assignment creates a plan only: **none of the checks below has been executed by the planner**. Do not recycle historical passing totals as evidence for this change.

## Key Insights

- Existing desktop tests use Kotlin test/JUnit and real temporary preference files/in-memory Room. `desktopApp/build.gradle.kts` has no Compose UI test dependency/harness.
- `DesktopTaskSurfacesTest` already covers account fences, quick-add isolation, automatic parent progress, desired-state completion, deadlines, startup and account-switch races. Extend these tests rather than replacing them with a narrowed zoom suite.
- Existing preferences tests exercise missing/corrupt files, concurrent writers and temp cleanup, but no font scale.
- Current desktop runtime acceptance is held. Design baseline PNGs, model tests and compiled packages cannot prove rendered height, keyboard focus, Narrator/Orca, or monitor behavior.
- Desktop target selection rejects cross-host builds. Windows verification must use Windows; Linux verification must use Linux.

## Requirements

- Real preference round-trip/backward-compatibility cases and controller/action integration coverage.
- Existing lifecycle/privacy/task tests remain intact; test font retention across the same transitions without broad refactoring.
- Explicit measured compactness, interactive text sizing, keyboard/semantics, persistence and recovery on running Windows/Linux apps.
- No new UI-test framework for this small change. Native observation is required; if automation is later introduced, it must test the actual composables and is separate approved scope, not a substitute for missing results here.
- Record command, host, dependency-verification mode, pass/fail/skip counts, artifacts, scenario results and unresolved blockers. Never silently turn missing graphical access into a pass.

## Architecture and Test Boundaries

1. Pure/persistence layer: `StickyWindowPresentation`, scale helpers and real `.properties` files.
2. Reactive model layer: `DesktopStickyTaskController` actions and scale retention without task side effects.
3. Integration layer: real `DesktopController` using injected temp preferences/profile and existing fake platform seam only where OS isolation is needed; no mocks replacing the preference implementation or feature logic.
4. Rendered native layer: actual sticky Window and Compose controls, pointer/keyboard/semantics, native restart and DPI observation. This layer is not covered by JVM assertions on string descriptions or constants.

No test should contact production Hub, use developer account storage, or mutate a real user's tasks. Reuse controlled in-memory Room and temporary files for automated tests; use a disposable profile/account for native interaction.

## File Ownership and Changes

All paths repo-root-relative under `G:/ws/sharing/doggo/`.

| File | Action | Owner / change |
|---|---|---|
| `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/sticky-window-preferences-test.kt` | Modify | 03: font field defaults, key, recovery, cross-field independence, round-trip and save cleanup coverage. |
| `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt` | Modify | 03: bounded actions, zero task side effects, lifecycle retention, DesktopController save/hide/quit/restart integration. |
| `docs/code-standards.md` | Modify | 03: §3.5 explicit desktop-sticky compact control/body typography exception; keep 48dp default elsewhere. |
| `docs/design-guidelines.md` | Modify | 03: §3 and §7.3 resolve current universal 48dp/body-size conflict; sticky-only 24dp controls/14sp title/body/12sp metadata, zoom range, text-aware min heights and keyboard/semantics contract. |
| `docs/windows-desktop-guide.md`, `docs/linux-desktop-guide.md` | Modify | 03: user zoom controls/shortcuts, range/default, profile-local preference key/file, compact behavior, restart and DPI distinctions. |
| `docs/windows-acceptance.md`, `docs/linux-acceptance.md` | Modify | 03: add exact native matrix/results, distinguish observed vs pending checks. |
| `docs/system-architecture.md` | Modify | 03: reconcile proposed §2.1 with implemented flow and actual evidence; retain release holds. |
| `docs/development-roadmap.md`, `docs/project-changelog.md` | Modify | 03: dated feature entry/progress linked to evidence, not unsupported release completion. |
| `plans/261021-0844-compact-sticky-font-resize/plan.md`, three phase files | Modify | 03: status/effort/evidence links reflecting actual work, no false completion while acceptance blocked. |
| `plans/261021-0844-compact-sticky-font-resize/reports/verification.md` | Create during execution | 03: integrated commands, results and native scenario table. Do not create passing evidence during planning. |
| New/deleted application/test source files or Gradle dependencies | None | Add assertions in named existing suites; retain current test conventions. |

## Specific Implementation Steps — Automated Coverage

1. Preference tests: extend missing-file/default constructor and round-trip tests to assert fontScale; inspect serialized Properties for exact `sticky.window.font_scale` key and unchanged version 1.
2. Add an explicit old-file fixture without the new property, including non-default visible/geometry/display-scale fields; assert they remain identical and zoom is 1.0. Keep existing visibility-alias compatibility intact.
3. Table-drive valid values 0.75, 0.875, 1.0, 1.125, 1.5 and non-grid 1.1. Assert exact/tolerance-appropriate float round-trip independently from monitor savedScaleX/Y.
4. Table-drive absent/empty/text/NaN/±Infinity/0/negative/below-min/above-max values with otherwise valid fields; only zoom defaults. Combine invalid geometry with valid 1.25 zoom to verify font/visibility survive geometry fallback.
5. Save invalid in-memory scale values; assert serialized/loaded zoom is safe default. Extend successive and concurrent existing save cases to assert final presentation is a coherent snapshot and no temporary files remain; do not test only file existence.
6. Controller tests: initialize with non-default scale; apply +/- through each step and past bounds, reset repeatedly, and validate state. Include in-range non-grid input and invalid initial input boundary recovery.
7. Assert zoom preserves sections/projection/quick-add content and submitting/error state; zero sync/OpenApp/Quit callbacks, zero store mutation. Reference identity checks on unchanged projections may establish zoom does not rebuild task lists.
8. Extend Ready-token-switch, non-Ready, stop/start, deadline refresh, pending submit, and sign-out scenarios to assert scale survives while old account tasks/input still clear. Exercise atomic update interleavings using existing coroutines test patterns; no sleep-only race proof.
9. Real DesktopController integration: inject `JvmAccountFiles(tempDir)`, preferences temp file, explicit initial presentation, isolated lock and platform seam. Dispatch zoom via `onStickyAction`, load file immediately, and assert full latest geometry/visibility/scale tuple.
10. Update geometry after zoom, hide/show, final flush and orderly dispose/quit; assert none overwrite scale with default. Instantiate a fresh controller/prefs from the saved presentation and verify restored live zoom. Release scopes/locks/Room and temp files in finally, following existing fixtures.
11. Cover no-op bound/reset actions: state does not change; integration save path avoids unnecessary writes. Inspect source/callback seam rather than adding timing-sensitive timestamp assertions or opening new production instrumentation APIs.
12. Main owner reviews actual implementation against architecture/invariants, then executes one integrated host test/build run after all edits land. If a run fails, diagnose/fix the reported failure; a subsequent run is post-fix verification, not a confirmation rerun of known failures.

## Verification Commands — Main Owner Only

Prerequisites: repository-supported JDK/Gradle/SDK configuration per [toolchain guide](../../docs/toolchain-versions.md), host-native desktop dependencies, sanitized test configuration. Keep dependency verification enabled; do not copy historical `--dependency-verification=off` commands.

Windows PowerShell, from repository root:

```powershell
.\gradlew.bat -PdesktopTarget=windows -PisolatedTestConfig=true --dependency-verification=strict :desktopApp:compileKotlin :desktopApp:test
```

Linux graphical host, from repository root:

```bash
./gradlew -PdesktopTarget=linux -PisolatedTestConfig=true --dependency-verification=strict :desktopApp:compileKotlin :desktopApp:test
```

Use the full desktop suite, not only new tests. No shared or Android code changes are planned, so their suites/packaging are intentionally not feature gates; if implementation touches them, revisit scope and run the affected suites. No MSI/RPM install, release build, secret provisioning, or production sync needed.

For native observation, launch the actual existing debug entry point (`:desktopApp:runDebug`) with the matching desktop target and strict dependency verification in a controlled profile. Confirm isolation before launch: the Gradle `isolatedTestConfig` flag alone must not be assumed to relocate real app storage. Use existing profile/home configuration or a separate OS test user, never developer profile files.

## Required Native Acceptance Matrix

Execute each applicable row on Windows and Linux; record OS/session, app revision, window content size, display DPI, inherited font setting, sticky zoom, action and result. Screenshots must come from the running app, not generated design baselines. AX/semantic evidence and keyboard observation supplement screenshots.

| Scenario | Required observation |
|---|---|
| Compact baseline at 360×520dp, 100% | Outer 6dp inset, 24dp checkbox/expander layouts, ≥32dp plain single-line row, ≥36dp quick add, compact header; measure screenshot bounds using actual display density or layout inspector. Compare same seeded task list before/after. |
| Zoom endpoints/intermediate/reset | A−/A+/percentage changes 75 → 87.5 → 100 → 112.5 … → 150%, exact reset; disabled endpoints, no stale indicator; task/header/field/metadata text all scale. |
| Shortcut focus behavior | Ctrl plus/equals/minus/0 while field focused and task/button focused; normal characters still type; Enter/IME submits once, Escape clears focus, unrelated Ctrl editing keys unaffected. |
| Narrow viewport | At 280×240dp and 75/100/150%, title may ellipsize, action groups wrap; Open App/Quit/zoom remain reachable, quick-add readable, task list scrollable. |
| Inherited large text | Inherited 200% font scale × sticky 100/150%: text-aware heights grow, no clipped cursor/buttons/deadline text; recovery still reachable. If host does not expose system fontScale, record limitation and use an isolated temporary test fixture for inherited-density observation; remove fixture before delivery. No fabricated OS-scaling claim. |
| Rich task content | Long/multilingual two-line titles, children, auto-completed parents/progress, date-only/timed IANA-zone deadlines, overdue badges, empty lists and quick-add error/submitting state all fit/scroll with semantics intact. |
| Keyboard and screen reader | Tab/Shift-Tab order, visible focus, Enter/Space activation; labels, checked/disabled automatic parent state, expanded/collapsed state, editable text and reset percentage announced in Narrator/Orca. No overlapping neighbor hit targets. |
| Persistence | Change zoom without hiding first; persisted property updated. Hide/show, close-to-hide, explicit Quit, restart restore zoom plus latest position/size/visibility. Perform only against disposable app/profile with appropriate side-effect confirmation. |
| Recovery/readiness privacy | Restoring/SignedOut/Locked/Unavailable still expose zoom + Open App/Quit; no prior tasks/input. With tray available/unavailable, existing close/recovery behavior unchanged. |
| State continuity | Main editor draft, sticky draft/selection, focus, list scroll, collapsed parent IDs remain through zoom. Account switch clears private content but retains profile-local zoom. |
| DPI/main-window isolation | 100/150/200% display DPI, move across monitors when available: dp geometry and main-window typography not multiplied by sticky zoom; host monitor-change behavior recorded separately from inherited font setting. |

If unavailable platform/accessibility/monitor scenario is required, mark blocked with missing prerequisite, retain technical acceptance as conditional, and list exact pending evidence. Do not weaken the matrix or broaden prior release approval.

## Documentation and Review Steps

1. Read current docs before editing; preserve unrelated roadmap/changelog claims and historical evidence attribution.
2. Resolve standards conflict explicitly: 48dp remains universal default **except desktop sticky mouse/keyboard controls** with named sizes and keyboard/semantic requirements. Explain base 14sp sticky task/title and 12sp metadata are scalable, user-adjustable desktop exceptions; no claim of automatic WCAG/native accessibility compliance.
3. Add platform guide usage: buttons/percentage reset, exact supported shortcuts, 75–150% range, 12.5-percentage-point step, system-relative reset, presentation-only `sticky.window.font_scale`, no task sync.
4. Update architecture design status to match source/evidence. Changelog and roadmap distinguish implemented, automated verified, and native acceptance blocked/passed. Do not mark entire pre-existing release gates complete.
5. Write `reports/verification.md` with separate automated/native sections, artifact links and blocked/not-run cases. Update phase/overview status only to supported completion level.
6. Main owner performs final code review (or repository reviewer role where available) for API compatibility, no duplicated zoom state, lifecycle safety, genuine compact measured bounds, semantics, and absence of unrelated source churn. No extra formatter/linter introduced solely for this change.

## Success Criteria

- Full native-host desktop compile/test run passes with explicit counts and strict dependency mode; all added matrices/assertions pass, no narrowed/suppressed existing tests.
- Real runtime proves compact baseline, visible independent font scaling, immediate preference save and restart restoration, keyboard navigation, semantic states, recovery controls, and no task/data regressions.
- Windows and Linux evidence clearly separated. Missing UI access is not a feature pass; any conditional handoff names exact unmet platform prerequisites.
- Current architecture/standards/guides/roadmap/changelog align with source and evidence, linked plan remains accurate, no throwaway fixture/scripts left behind.

## Risk Assessment and Mitigation

| Risk | Severity | Mitigation |
|---|---|---|
| Model tests mistaken for UI proof | High | Native matrix mandatory; measured screenshots/AX/keyboard evidence; explicit conditional acceptance. |
| Real account/profile affected by tests | High | Temp files/Room for automation; isolated disposable native profile; verify exact app target before mutations/Quit. |
| Linux checks attempted on Windows | Medium | Host-target guard respected; separate Linux session/CI owner, never cross-target override. |
| New metadata field breaks old concurrent save behavior | Medium | Full-tuple round-trip/snapshot checks with font included; retain existing lock and cleanup tests. |
| SDK/dependency/UI approval unavailable | Medium | Record concrete failed prerequisite; finish reachable tests/docs; do not disable integrity or fabricate approvals. |
| Global standards silently contradicted | Medium | Named sticky-only exception in both code and design standards; shared/Android tokens unchanged. |

## Security Considerations

No production credentials, backend access, file deletion outside temp fixtures, or new permissions required. Protect controlled test-profile data in screenshots. Human interactive grant is required for desktop control; external/task mutations and Quit/restart confirm exact disposable target at point of risk unless already explicitly authorized.

## Next Steps

Hand off source changes plus verification evidence and accurately qualified acceptance to main owner. No separate cleanup backlog or unrelated platform-release work is created.

## Unresolved Questions

No unresolved product/design choices. Windows/Linux graphical and screen-reader access, multi-monitor setup, and inherited-font-scale exposure must be confirmed during execution; unavailable cases remain named blockers.
