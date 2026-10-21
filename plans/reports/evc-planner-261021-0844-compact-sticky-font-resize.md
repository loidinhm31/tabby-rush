# Planner Report — Desktop Sticky Font Resizing and Compact UI

Date: 2026-10-21 · Role: Planner · Branch from supplied context: `feature/parent-child-tasks-deadline-surfaces`.

## Deliverables

- [Implementation overview](../261021-0844-compact-sticky-font-resize/plan.md)
- [Phase 01 — Preferences and model](../261021-0844-compact-sticky-font-resize/phase-01-preferences-and-model.md)
- [Phase 02 — Compact components and font scaling](../261021-0844-compact-sticky-font-resize/phase-02-compact-components-and-font-scaling.md)
- [Phase 03 — Tests, native verification and documentation](../261021-0844-compact-sticky-font-resize/phase-03-tests-and-verification.md)
- [Architecture design entry](../../docs/system-architecture.md#21-proposed-desktop-sticky-typography-and-compact-controls): added proposed, explicitly unimplemented design before plan creation. No Mermaid changes.

Plan status pending; implementation estimate 12h (3h + 5h + 4h), not a commitment to native platform availability. No application source, build files, tests, roadmap, changelog, or acceptance records changed by planner.

## Research Method and Rule Resolution

- Read README first, then published AGENTS/primary workflow/development/documentation-management rules and relevant repository architecture, codebase summary, code/design standards, roadmap/changelog and desktop guides.
- Repository `.omp/evcrate/` resources and `.omp/skills/` are absent. Required published workflow fallback files were readable under `~/.omp/agent/evcrate/`; followed them. No `docs/development-rules.md` found; code standards and published development rules provide current constraints.
- Inspected published skill catalog. Read `planning` SKILL.md and its six research/codebase/architecture/design/organization/output references; applied architecture-first and concise phase structure. Read `docs-seeker` SKILL.md and error handling guidance for external API research. No automatic skill activation claimed.
- Docs-seeker topic detector returned general lookup; fetch script reported `Documentation not found on context7.com`. Used its recommended web-search fallback and primary Android Developers references. This is a lookup miss, not an application/tool verification failure.
- Used explicit assignment directory/report names `261021-0844`, overriding later generic hook example `0848`. Dynamic Bash date returned `261021` (PowerShell `Get-Date` unavailable in Bash; switched to required Bash date command).
- Used published `set-active-plan.cjs` because local helper absent. It returned: `EVCRATE_SESSION_ID not set - session state will not persist`; `Would set active plan to: plans/261021-0844-compact-sticky-font-resize`. Files exist, but active-plan session registration was **not persisted**. Main must pass plan path explicitly or register it from a session with the real session ID; do not invent one.
- No delegation used: agent spawning unavailable; inline scope contained to listed desktop files/docs. No UI control, builds, test runs, linting, formatting or application smoke executed.

## Observed Current Architecture

| Evidence | Current behavior / planning consequence |
|---|---|
| `sticky-window-geometry.kt:34–44` | Presentation is visible + geometry only; width 360dp/default, 280dp/min; add defaulted font field without changing geometry algorithm. |
| `sticky-window-preferences.kt:26–48,55–102,110–154` | Version-1 Properties, independent visibility/geometry parsing, instance lock, temp+fsync+atomic move/fallback and best-effort failures. Extend optional key, retain behavior. |
| `sticky-task-model.kt:32–37,81–92` | Immutable state/action flow exists; append live zoom state and three presentation actions. Leave task projection pure. |
| `desktop-task-surfaces.kt:273–334,341–403,405–530` | Controller owns sticky StateFlow; lifecycle paths construct fresh state. Preserve zoom during privacy clearing; no presentation I/O in this controller. |
| `main.kt:141–157,182–195,226–257,1054–1068,1126–1163` | DesktopController owns window persistence; launch restores/clamps geometry; sticky Window is independent of main. Seed scale, persist changed actions in complete snapshots, scope density only to sticky content. |
| `sticky-task-content.kt:91–107,156–211` | 12dp outer padding; header is a rigid Row with explicitly ≥48dp recovery buttons. New controls require wrapping, not more rigid children. |
| `sticky-task-content.kt:222–295,411–593,596–641` | Quick-add uses Material-2 OutlinedTextField and 48dp minimums; rows/checkboxes/expanders also 48dp; status/recovery uses roomy gaps. Compact every sticky-only surface. |
| `desktopApp/build.gradle.kts:10–24,45–66,121–131` | Native host-target selection enforced; Kotlin/JUnit/coroutines tests exist, no rendered Compose test harness; test host environment sanitized. |
| `gradle/libs.versions.toml:2–5` | Kotlin 2.1.10 and Compose Multiplatform 1.7.3 pinned. No dependency upgrade justified. |
| Existing preference and controller test files | Real temp files/in-memory Room, lifecycle/account fences and hierarchy/deadline regression coverage already available. Extend rather than narrow suites. |

## Proposed Architecture and Component Interactions

Presentation load → defaulted model field → DesktopController seeds sticky controller → StateFlow scale → sticky-local LocalDensity provider → all sticky text.

Header and keyboard → identical presentation actions → atomic font-only state copy → DesktopController detects changed value → full presentation save including current geometry and visibility.

- Font range 0.75–1.50, step 0.125, default/reset 1.0. Display 75%, 87.5%, 100%, 112.5%, etc.
- Stored invalid/missing font defaults independently. Valid non-grid stored values retained; interactive action adds/subtracts step and clamps at bounds. Version 1 unchanged; no migration or old-key rewrite.
- Base display density retained; inherited font scale multiplied by sticky zoom exactly once. No dp/window/DPI transform or main-window typography change.
- Profile-local non-sensitive preference survives readiness/account task clearing. Task data/token fences, independent quick-add, hierarchy/deadlines and main editor unchanged.
- Full snapshot persistence serialized at existing DesktopController presentation boundary; no second mutable font state, background persistence service, new DB table, new shared token or account setting.
- Root preview shortcuts remain sticky-only; no global hooks. Ctrl combinations work with focused editor while normal typing and Enter/Escape stay intact.

## Chosen Design Versus Alternatives

| Choice | Rationale / rejected alternative |
|---|---|
| One LocalDensity fontScale override | Scales all sp text consistently and preserves display density. Reject graphics transforms and per-Text multiplication: distort controls/double-scale or miss components. |
| Append defaulted field, optional key | Old constructors/files work without migration. Reject mandatory new version/schema and separate settings service: unnecessary complexity. |
| Existing immutable sticky StateFlow | Reuses action architecture; avoids duplicate remembered header/window zoom. Must preserve field in privacy resets. |
| Compact BasicTextField decoration | Small editable field without Material internal text-field padding; keep real text/IME/selection semantics. Reject just forcing current OutlinedTextField to 32dp: risks clipped baseline/cursor. |
| Wrapping header, flexible heights | Recovery actions remain reachable at min width/large text. Reject fixed single header Row or reducing every dimension regardless of scaled text. |
| Sticky-local Material-2 interaction minimum override | Avoid global Android/main-app regressions. [INFERENCE] Exact opt-in/provider availability must be checked against resolved pinned Material-2 API during implementation; current official minimum-size docs confirm extra layout minimums, not every pinned provider signature. |
| Existing JVM tests + mandatory native evidence | Fits current harness; tests actual persistence/controller logic. Reject adding a general Compose UI harness solely for this feature or claiming model tests prove rendering. |

## File Ownership and Implementation Sequence

1. Phase 01 owns four presentation/model/controller files: geometry, preferences, task model, desktop surfaces. Freeze constants/actions/state contract; no touch to native access gates.
2. Phase 02 owns main.kt and sticky content: seed state, save complete snapshots, scoped density/shortcuts, compact header/field/rows/status/metadata.
3. Phase 03 owns two named test files plus current docs/plan evidence. Main runs integrated checks after edits, reviews source and records native Windows/Linux outcomes separately.

Eight requested target files all accounted for. New application/dependency files: none. Obsolete flush name migrated at every call site, no compatibility alias. No unrelated file splitting or new design-system abstraction.

## Success Criteria and Verification Summary

- Backward compatibility: existing constructor/file default 1.0; correct property key; corruption isolated by field; valid font round-trips with geometry/visibility unchanged.
- Interactive behavior: buttons/shortcuts, exact reset, disabled endpoints, no unrelated key capture, stable draft/selection/focus/collapse/scroll, immediate save on changed scale.
- Compact runtime targets: outer 6dp; 14sp header/task text, 12sp metadata; 28dp+ compact header actions; 36dp+ quick-add; 24dp checkbox/expander; 32dp+ plain row; text-aware growth.
- Native acceptance: 280×240 and 360×520dp, 75/100/150% zoom, inherited 200% text, DPI 100/150/200%, rich tasks/deadlines, all readiness states, tray recovery, restart and Narrator/Orca keyboard traversal.
- Automated gate: full desktop compile/test invocation on matching Windows/Linux hosts with strict dependency verification. No cross-target override, no old test totals reused.
- Scope gates: no source/test/build/UI verification run by planner. Phase 03 contains exact commands and evidence template, not passing results. Historical release/UI holds stay open.

## Risk Assessment and Mitigation

| Priority | Risk | Mitigation |
|---|---|---|
| High | Global code/design standards require ≥48dp; user explicitly requests 24dp desktop sticky controls | Update both standards with named desktop-sticky exception; retain Android/shared defaults, semantics, keyboard navigation and native accessibility review. |
| High | Zoom resets on sign-out/readiness/observation teardown | Copy presentation scale through every sensitive-state clear; existing account/race regressions extended. |
| High | 48/56dp internal Material constraints remain or fixed field clips text | Sticky-local minimum enforcement, explicit button sizing/padding, BasicTextField decoration, runtime measurement at extremes. |
| High | New controls push Open App/Quit off narrow header | Wrapping complete action groups, title ellipsis only, flexible min heights and persistent recovery area. |
| Medium | Older geometry save overwrites newer zoom | Complete serialized presentation snapshot, no independent zoom cache, hide/quit/restart integration tests. |
| Medium | DPI or inherited scale multiplied incorrectly | Retain density, multiply fontScale once, test main-window isolation; distinguish OS DPI from inherited text scale. |
| Medium | Native approval/platform access missing | Finish reachable implementation/test/docs work, report exact blocked matrix rows; never infer acceptance from design PNG/unit results. |
| Low | Preference I/O fails | Existing best-effort behavior retained; in-session state remains functional, previous file not knowingly replaced with invalid scale; no new retry/telemetry subsystem. |

## Security, Performance, and Scope

- Presentation property stores no task/account/credential data and is never synchronized. Existing account fences and readiness privacy must remain intact.
- Zoom copies state only; no task reprojection, store read, sync call or main editor update. Remember Density by inherited density/fontScale and zoom.
- Persist only changed font actions, not every recompose or endpoint no-op. Reuse existing save implementation; no new queue/debouncer/retry system.
- Native evidence uses disposable profile/account; human computer-control approval and exact-target confirmation required for consequential task mutations/Quit/restart.
- Documentation updates after implementation cover architecture, code/design standards, platform guides/acceptance, roadmap and changelog; distinguish implemented/automated/native status, no broad release approval.

## Unresolved Questions

No blocking product/design questions. Execution prerequisites: real Windows and Linux graphical/screen-reader sessions, monitor/text-scale setups for named native scenarios. Active-plan session registration remains unavailable because `EVCRATE_SESSION_ID` was absent; published helper could not persist state. Main can use the explicit plan path immediately.
