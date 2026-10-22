# Code Review Cycle 2 — Phase 05 Desktop Sticky

Score: **8/10** — no critical. Cycle 1 critical fixed. Few warnings (state-update atomicity, stale-observer branch, weak regression test).

## Scope
- desktopApp main: sticky-task-model.kt (92), sticky-task-content.kt (586), desktop-task-surfaces.kt (482)
- desktopApp test: desktop-task-surfaces-test.kt (914), desktop-controller-env-test.kt (117)
- Plan phase-05 todo left open (native Linux/Windows evidence belongs to Phase 06).

## Cycle 1 fix verification
| C1 item | Status |
| --- | --- |
| Critical 1 readiness collector wipes/cancels observer on first Ready | FIXED. `tokenChanged` now requires prev Ready token non-null. Temp stress (Dispatchers.Default, controller-before-auth, 30 runs): 0/30 fail. |
| W1/W2 triple projection, dead `projectedSections`, clock split | FIXED. UI reads `state.projectedSections`; 30s ticker dispatches `RefreshDeadlines`; controller owns injectable `clock`. |
| W4 volatile fields | FIXED (`currentToken`, `activeToken`, `activeObserverJob`). |
| W5 submit stuck `submitting=true` on fence | FIXED (reset when `activeToken == action.token`). |
| `nowEpochMs` YAGNI param, FlowRow semantics | FIXED (`clearAndSetSemantics`, param removed). |
| W6 env-test DRY | Partially (helper extracted, literal still duplicated). |

## Critical
None.

## Warnings
1. **Lost-update race in `refreshDeadlines`** (surfaces:361-367). `current = value; value = current.copy(projectedSections = project(current.sections))` can overwrite a concurrent observer emission with stale sections/projection until next DB change. Repro (temp test, removed): refresh hot-loop + 20 inserts → **20/20** ended with stale sections. Real 30s tick = much rarer, but trivial fix: `_stickyState.update { if (it.readiness is Ready) it.copy(projectedSections = projectStickySections(it.sections, now)) else it }`. Same read-modify-write pattern in observer collect (:345) and quick-add (:384, 401, 408, 421, 432…) can lose quickAdd/section updates; convert all to `MutableStateFlow.update {}`.
2. **`tokenChanged` branch in readiness collector is redundant + hazardous** (surfaces:301-313). `startObserving` already cancels prior job and resets state on token change synchronously. If StateFlow conflates Ready(A)→Ready(B) (collector slower than SignedOut→Ready gap), collector cancels the *new* B observer job after `startObserving(B)`. Repro Ready(A)→Ready(B) w/o intermediate non-Ready: **1/30** sticky left empty. Prod path normally goes SignedOut between (main.kt:490-494), so low likelihood. Fix: collector on Ready = `update { it.copy(readiness = r) }` only; keep reset in `startObserving`.
3. **Non-Ready no longer cancels observer** (behaviour change vs HEAD). Old code cancelled `activeObserverJob` on any non-Ready; now only on tokenChanged. On `Unavailable` (closeAndDrain, DB about to close) job keeps collecting Room flow until `stopObserving` (main.kt:490/983 do call it, so bounded). Restore cancel in non-Ready branch (safe: Ready→non-Ready→Ready is always followed by `startObserving`).
4. **Prod-order regression test is weak** (`testStartupOrderControllerConstructedBeforeAccountAuthentication`, test:880-913). Uses `runTest` `backgroundScope` (single-threaded test dispatcher) so the collector/observer interleaving is deterministic; test passes even for race-prone ordering → does not guard Critical 1. Use `Dispatchers.Default` scope (+ loop ~20x) or explicit `UnconfinedTestDispatcher`. Add Ready(A)→Ready(B) switch case for W2.
5. **Unused imports** sticky-task-content.kt: `Todo`, `mutableStateOf`, `remember`, `getValue`, `setValue` (each occurs once, import only) — leftover from removed ticker state. Kotlin warns; remove.

## Suggestions
- `projectStickyItems` calls `TaskHierarchyProjector.projectHierarchy(section, TodoFilter.ALL)` per section on every DB emission + tick; fine for sticky sizes. Optional: skip recompute in `refreshDeadlines` when no item has a deadline (cheap guard) — not needed now (YAGNI).
- `LaunchedEffect` ticker runs while non-Ready (no-op via guard). Could scope inside `ReadyStickyContent`; trivial.
- Dead semantics: overdue badge `.semantics { contentDescription = "Task is overdue" }` is cleared by parent `clearAndSetSemantics` (content:529-531) → remove.
- Parent row: checkbox cd "Automatic subtask progress…" and "Subtasks: x/y" Text cd both announce progress; fold into one for screen readers.
- `testQuickAddAlwaysCreatesIndependentRoot` near-tautological; keep or drop.
- env-test: API key literal `XoaJ…` repeated 4x in test (already public default in CI/gradle/androidApp, no new exposure); use one `private const` or dummy value. Note env-test change is unrelated to Phase 05 → mention in changelog/commit.
- Style: sticky-task-model.kt missing blank line after imports (line 10/11); sticky-task-content.kt `@OptIn` lacks blank line before (390-391); compose-runtime imports not in sorted order; stray double blank in `ReadinessStatusView` (576-577); desktop-task-surfaces.kt lost blank line before KDoc (line 85).

## Security
No new listeners/IO/secrets. Account fences strengthened (`access.getCurrentToken()` in ChangeTitle/Submit/SetCompleted + post-admission). `currentToken` now `@Volatile`. Labels contain date/zone only (no task titles in native ids). No injection surface. Pass.

## Performance
Projection once per DB emission/tick off composition thread; UI consumes precomputed state; `LazyColumn` keyed. Pass.

## Architecture / YAGNI / KISS / DRY
- Shared `TaskHierarchyProjector`/`TaskDeadlinePresentation` reused, no desktop fork. Pass.
- Single projection path now. `StickyTaskState.projectedSections` default `= projectStickySections(sections)` still allows stale on `copy(sections=…)`; mitigated since controller always passes both. Consider derive-only (compute in one `publish(sections, now)` helper) — optional.

## Positive
Parent checkbox read-only with "automatic" semantics; child/standalone toggle via shared admission; direct-parent rejection test covers no-mutation/no-sync; quick-add always root; stale-text preservation retained; injectable clock enables deterministic overdue test.

## Validation
| Command | Result |
| --- | --- |
| `JAVA_HOME=…/jdk-21 ./gradlew -PdesktopTarget=linux :desktopApp:test --dependency-verification=off --rerun-tasks` | BUILD SUCCESSFUL (incl. my temp race test, since deleted) |
| Temp `TmpReviewRaceTest` (Dispatchers.Default): prod order | 0/30 failures (C1 fixed) |
| Temp: Ready(A)→Ready(B) without non-Ready | 1/30 failures (W2) |
| Temp: refreshDeadlines hot-loop vs inserts | 20/20 stale (W1) |
| Tester report `qa-261020-0200-phase-05-desktop-sticky-review-fixes.md` (not re-run): desktop 127, shared 341, android 50 pass | Pass |
Temp test file removed; working tree otherwise untouched by review.

## Recommended actions
1. `MutableStateFlow.update {}` everywhere in controller (W1).
2. Collector: Ready → readiness copy only; non-Ready → blank + cancel job (W2/W3).
3. Strengthen startup test with Default dispatcher + switch case (W4).
4. Remove 5 unused imports + dead overdue semantics (W5/suggestions).
5. Keep plan todos open until Phase 06 Linux/Windows native evidence.

## Unresolved questions
- Env-test (`desktop-controller-env-test.kt`) intended in Phase 05 scope or split commit?
- Must reset-on-switch live in `startObserving` only (recommended) or keep a collector fallback?
