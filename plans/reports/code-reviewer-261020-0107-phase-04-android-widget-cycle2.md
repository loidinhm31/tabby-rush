# Code Review — Phase 04 Android grouped widget + deadline labels (Cycle 2)

Score: 7.5/10

## Prior findings
| # | Finding | Status |
|---|---|---|
| 1 | 48dp row touch target (`Spacer(height 48dp)` in Row, task-widget.kt:401) | PARTIAL — Row min height ok (48+8 pad); clickable open-task Column + CheckBox bounds NOT enlarged |
| 2 | Deadline clipping (`maxLines = 3`, :504) | RESOLVED (compact title 1 line, deadline 3 in both modes) |
| 3 | Column contentDescription aggregates title + subtasks + deadline (:442-462) | RESOLVED |
| 4 | Real entry latch (`readStarted` completed inside `read`, awaited before clear) | RESOLVED mechanically; test value weak (see W1) |
| 5 | Row spec assertions (`testWidgetTaskItemRowSpecifications`) | RESOLVED (model-level only) |

## Critical (MUST FIX)
1. task-widget.kt:394-463 — 48dp spacer only sizes Row container. Clickable Column (`defaultWeight().clickable`) wraps content: compact, one-line title, no metadata ≈ 20dp tall. CheckBox bounds are Glance-default. Plan Success Criteria require >=48dp interactive controls. Fix: add `.fillMaxHeight()` to the clickable Column (Row already >=56dp), and verify the CheckBox hit area on device (or wrap in `Box(GlanceModifier.size(48.dp), contentAlignment = Center)`). [INFERENCE: not verified on device; Glance AAR layouts not inspected.] Needs runtime proof either way.

## Warnings (SHOULD FIX)
1. W1 task-widget-model-test.kt `testClearDuringReadNeutralizesPublication` — fence lives in the mock (`publishIfCurrent` now checks readiness inside the mock) and the read/publish flow is hand-copied from `TaskWidgetUpdater`. Test exercises the mock, not production `TaskWidgetUpdater`/`task-surface-access.kt:166`. Regression in production fence would not fail it. Drive the real updater (`refreshAll(context, host)` with controlled access) or test the real access impl.
2. W2 Same test: `access.readStarted.await()` / `readJob.join()` have no timeout; a failure before `read` hangs `runBlocking`. Wrap in `withTimeout(5_000)`.
3. W3 Overdue staleness: `System.currentTimeMillis()` captured at composition (task-widget.kt:253). No time-boundary refresh scheduled, so a deadline passing does not flip a row to Overdue until next update. Plan (Architecture) says "reuses existing lifecycle-owned time/boundary refresh" — not wired. Document as known gap or hook the existing refresh.
4. W4 Plan step 3 says "render deadline parts using Android copy/resources". "Due …" label and row/deadline `contentDescription` come from shared hardcoded English (`task-deadline-presentation.kt:32-44`); only the on-screen "Overdue" suffix uses `task_widget_overdue`. Inconsistent: visual says "• Overdue" (resource), a11y says ", Overdue" (shared). Also `". "` separators hardcoded (:447, :453).
5. W5 No instrumented/render test for grouped rows, deadline lines or touch target; device test only compile-checked. Plan todo "Verify real launcher behavior" remains unchecked; Phase 04 plan todo list not updated; do not mark phase complete.

## Suggestions (NICE TO HAVE)
1. Deadline `Text` `semantics{contentDescription}` (:510) is redundant: parent Column description already covers it (YAGNI); remove or keep only if the Text is separately focusable.
2. Subtasks-progress string composed twice (visible Text :477 and description :446) — hoist to one local `val`.
3. `itemId = { it.todo.id.hashCode().toLong() }` (:327/:350) — collision-prone for stable IDs; consider a stable 64-bit hash of id (pre-existing pattern; verify not changed by this diff).
4. Missing blank line between `testWidgetTaskItemRowSpecifications` and `testAwaitQuiescenceCompletesCleanly` (model test).
5. `projectWidgetItems(nowEpochMs = System.currentTimeMillis())` default — drop default, pass explicitly to keep the pure function deterministic.
6. Spacer-in-Row trick for min height is non-obvious; if kept, comment is good (present).

## Positive
- Pure `task-widget-projection.kt` separates projection from Glance; consumes shared `TaskHierarchyProjector` and `TaskDeadlinePresentation` (no Android fork); `TodoFilter.ALL` keeps whole compact groups.
- Parent with live children: CheckBox `onCheckedChange = null` (no completion callback), automatic-progress a11y string.
- Tests cover ancestor-deletion, mixed/complete groups, raw-vs-effective disagreement, own deadlines, overdue, completed-never-overdue, no inheritance.
- Privacy: no titles/deadlines in PendingIntent extras or logs (only ids/tokens passed). No new exported components.
- Security: no new risk found; token fencing path unchanged.

## Reviewed files
- androidApp/src/main/res/values/task-widget-strings.xml
- androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-projection.kt
- androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget.kt
- androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-widget-device-test.kt (only a named-args edit)
- androidApp/src/test/kotlin/dev/tabbyrush/android/task-widget-model-test.kt
- Context: shared task-deadline-presentation.kt, task-widget-updater.kt, phase-04-android-widget.md

## Validation
- `JAVA_HOME=…/jdk-21 ./gradlew :androidApp:testDebugUnitTest --tests '*TaskWidgetModelTest*' :androidApp:compileDebugAndroidTestKotlin --dependency-verification=off` → BUILD SUCCESSFUL; visible PASSED: 11 TaskWidgetModelTest cases (incl. RowSpecifications, ancestor deletion, deadlines); androidTest Kotlin compiles. (Output tail truncated; race-test result line not seen, build green.)
- Prior QA report (plans/reports/qa-261020-0053-…): 341 shared / 50 android unit tests green (not rerun here).
- Not run: device/launcher verification, `assembleDebug`.

## Unresolved questions
- Is there a connected device/emulator to verify hit-area of the clickable Column and CheckBox?
- Is time-boundary overdue refresh in scope for Phase 04 or deferred to Phase 06?
