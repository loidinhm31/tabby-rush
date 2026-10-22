# Code Review — Phase 05 Desktop Sticky (grouped + own deadlines)

Score: **5/10** — BLOCKED by 1 critical (account-ready race wipes/cancels sticky observer).

## Scope
- desktopApp main: sticky-task-model.kt, sticky-task-content.kt, desktop-task-surfaces.kt
- desktopApp test: desktop-task-surfaces-test.kt, desktop-controller-env-test.kt
- Plan: plans/261019-1511-parent-child-tasks-deadline-surfaces/phase-05-desktop-sticky.md (not edited; todo stays open, see Critical)

## Critical
1. **Readiness collector cancels/blanks live observer on first Ready (regression)** — desktop-task-surfaces.kt:297-316.
   - New `tokenChanged` branch (prev Ready token null -> new token) resets state to empty AND cancels `activeObserverJob`. Old code did nothing on Ready except copy readiness.
   - Production order (main.kt:142 controller built at startup in Restoring; :372-373 `onAccountAuthenticated` then `startObserving`; scope = Dispatchers.Default, multi-thread): collector runs async after Ready emission. If it runs after `startObserving` -> real observer job cancelled, sticky never populates. If it runs after first DB emission -> sections blanked until next DB change.
   - Repro (temp test, since deleted): controller created, then auth + startObserving on Dispatchers.Default scope, wait 1.5s, 1 task seeded → **18/30 runs ended with empty sticky** (`RACE_RESULT failures=18/30`).
   - Why 126/126 tests pass: every test authenticates BEFORE constructing controller, so initial readiness already Ready(token1), `tokenChanged=false`. Production ordering never exercised.
   - Fix: on Ready only `copy(readiness=r)`; keep blank+cancel for non-Ready (old behaviour). Account switch A→B is already handled by `startObserving` (cancels prior job, resets activeToken) + token-checked emissions; if explicit reset on token change wanted, do it in `startObserving` (synchronously, same call that starts the job), never in the async collector. Add regression test using prod ordering (controller first, then auth+startObserving) with `Dispatchers.Default`/`UnconfinedTestDispatcher`.

## Warnings
1. **Projection computed 3x; controller `projectedSections` is dead for UI** — `partitionSurfaceTodos` (buildGroups) → controller `projectStickySections` (buildGroups per section) → UI `ReadyStickyContent` re-runs `projectStickyItems(state.sections.*)` (sticky-task-content.kt:322-327). UI never reads `state.projectedSections`; `refreshDeadlines()` is never called in production (only test). Tests assert `projectedSections`, so they validate a path the window does not use. DRY/KISS violation + test–prod divergence. Pick one: (a) UI consumes `state.projectedSections`, 30s ticker calls `controller.refreshDeadlines()`; or (b) drop `projectedSections`/`refreshDeadlines`/`clock` projection from controller and test the pure `projectStickyItems` + UI clock. (a) better: projection off the composition thread and tests match prod.
2. **Clock split** — controller has injectable `clock`; UI ticks `System.currentTimeMillis()` every 30s internally. Overdue tests use controller clock, UI doesn't. Resolved by W1(a).
3. **`StickyTaskState.projectedSections` default `= projectStickySections(sections)`** — `copy(sections=…)` without passing `projectedSections` leaves stale projection; controller currently hand-maintains both fields in 3 places. Make projected derive-only (single writer helper) or remove field.
4. **Unsynchronized cross-thread fields** — controller `activeToken`/`activeObserverJob` (plain vars) now written by collector + `startObserving` + UI thread; `DesktopTaskSurfaceAccess.currentToken` read lock-free via `getCurrentToken()` in new fences, not `@Volatile`. Fence checks are only best-effort without it. Mark `@Volatile` (cheap) or serialize.
5. **Post-admission fence early-return leaves quick-add `submitting=true`** (surfaces:410-412) when `activeToken` transiently null (`stopObserving`) without a readiness reset. Low likelihood; reset state in that branch or on `stopObserving`.
6. **Scope creep** — desktop-controller-env-test.kt rewrite is unrelated to Phase 05 (hermetic .env fix); two near-identical 30-line tests (DRY: extract helper). Keep only if intentional; note in changelog/commit. Pre-existing literal API key `XoaJ…` repeated in tests — use a dummy.

## Suggestions
- Accessibility: deadline `FlowRow` sets `contentDescription` on a non-merging container while child `Text`s still exposed → double reads in Orca/Narrator; use `clearAndSetSemantics` (or `mergeDescendants`) on the FlowRow. Overdue badge cd "Task is overdue" duplicates parent cd "…, Overdue". Parent checkbox cd + "Subtasks" Text both announce progress; fold into one.
- `StickyTaskContent(nowEpochMs: Long? = null)` param is unused by main.kt and has no UI tests → YAGNI; remove unless UI test added (preview/test seam only).
- `CompactTaskRow` title `maxLines=2` + ellipsis: plan says wrap with min-height; acceptable but 200% scale may hide the title tail; consider allowing 3 lines or tooltip.
- `testQuickAddAlwaysCreatesIndependentRoot` is near-tautological (controller has no group-focus concept); fine as guard but low value.
- Import hygiene: sticky-task-model.kt missing blank line after imports (line 10/11); sticky-task-content.kt compose-runtime imports appended out of order; stray blank line in `ReadinessStatusView`; missing blank line before `@OptIn` (content:398-399).
- Parent hardcoded 48dp min-size sticky rows with `Surface.border`: ok; consider `Modifier.semantics(mergeDescendants = true)` on whole row for 1-stop traversal.
- No UI/screenshot evidence yet (plan steps 7-8: Linux launch, Windows native) — remain open for Phase 06.

## Security
- No new listeners/exports; no task text in native ids; labels contain date/zone only. Account fences strengthened (`access.getCurrentToken()` in ChangeTitle/Submit/SetCompleted) — good direction but see W4 volatility. No injection/secret issues in changed main code.

## Positive
- Shared `TaskHierarchyProjector`/`TaskDeadlinePresentation` reused; no desktop forks of hierarchy/date logic.
- Parent checkbox read-only w/ "automatic" semantics; child/standalone desired-state via shared admission; direct-parent rejection test covers no-mutation/no-sync.
- Quick-add always root (`createDefaultTodo`), stale-text preservation retained.
- Group move tests (mixed group intact in Active, fully complete → Completed) match contract.

## Validation
| Command | Result |
| --- | --- |
| `./gradlew -PdesktopTarget=linux :desktopApp:test --tests '*TmpReviewRaceTest*' --dependency-verification=off` (temp prod-order repro, removed) | FAIL 18/30 → confirms Critical 1 |
| Tester report plans/reports/qa-261020-0146-phase-05-desktop-sticky-test-validation.md (not re-run by me): desktop 126, shared 341, android 50 all pass; vector/smoke scripts pass | Pass, but does not exercise prod init order |
Temp test file deleted; working tree otherwise untouched by review.

## Recommended actions
1. Fix Critical 1 + add prod-order regression test; rerun `:desktopApp:test`.
2. Resolve W1 (single projection path) and W3.
3. `@Volatile` on token fields (W4); W5 reset.
4. Decide on env-test scope (W6); semantics cleanup.
5. Keep plan Todo items open until Phase 06 native evidence.

## Unresolved questions
- Intentional to reset sticky state on token-change (account switch) inside the readiness collector? If needed, confirm desired location (`startObserving` vs collector).
- Is env-test change meant for this phase (unrelated to sticky)?
- Should UI keep its own 30s ticker or delegate to controller `refreshDeadlines`?
