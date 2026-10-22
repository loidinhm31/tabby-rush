# Code Review — Phase 04 Android grouped widget + deadline labels (Cycle 3)

Score: 8.5/10 | Critical: 0

## Cycle-3 checklist
| Item | Status | Evidence |
|---|---|---|
| Row min height 48dp Spacer | OK (redundant, see S1) | task-widget.kt:402 |
| CheckBox container 48x48 Box | OK | :404-409 |
| Column `.fillMaxHeight()` clickable | OK | :469-474 |
| Deadline maxLines=3 | OK | :512 |
| TalkBack aggregated desc (title + subtasks + deadline) | OK | :457-474 |
| withTimeout on latch + join | OK | model-test :455, :467 |
| Row spec assertions | OK (model-level) | `testWidgetTaskItemRowSpecifications` |

## Warnings
1. Column fillMaxHeight w/o `verticalAlignment` → text top-aligned vs vertically centered checkbox in 48dp+ row. Add `verticalAlignment = Alignment.CenterVertically` to Column. [INFERENCE: visual only, not device-verified]
2. Race test (`testClearDuringReadNeutralizesPublication`) still exercises mock + hand-copied flow, not real `TaskWidgetUpdater`/task-surface-access fence (cycle-2 W1 carried). Timeouts now fixed; value still weak.
3. Overdue staleness: `System.currentTimeMillis()` captured at composition (:254); no boundary refresh (cycle-2 W3 carried). Document as Phase 06 gap.
4. A11y/visual copy split: "Due …" + deadline contentDescription from shared hardcoded English; ". " separators hardcoded (:460,:464); only "Overdue" suffix is a resource (cycle-2 W4 carried).
5. Parent `CheckBox(onCheckedChange = null)` — Glance/RemoteViews may still toggle visually on tap until next refresh. [INFERENCE, unverified]; confirm on device.
6. No device/launcher proof of 48dp hit areas, grouped render, 200% text. Plan todos remain unchecked — do not mark Phase 04 complete.

## Suggestions
1. Spacer(height 48dp) (:402) redundant now Box is 48dp tall (YAGNI) — remove or keep w/ comment.
2. Redundant `projectWidgetItems` default `nowEpochMs` — pass explicitly (determinism).
3. `itemId = hashCode().toLong()` collision-prone (pre-existing).
4. `testAwaitQuiescenceCompletesCleanly` ends with `assertTrue(true)` — no-op assertion; drop it.

## Security / perf / arch
- No new exported components; PendingIntent/action params carry only token+id; titles/deadlines not logged. Placeholders unchanged.
- Projection called twice per composition (incomplete/completed) — O(n), fine.
- Pure `task-widget-projection.kt` consumes shared `TaskHierarchyProjector`/`TaskDeadlinePresentation`; no Android fork. DRY/KISS ok.

## Validation
- `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :androidApp:testDebugUnitTest --tests '*TaskWidgetModelTest*' :androidApp:compileDebugAndroidTestKotlin --dependency-verification=off --offline` → BUILD SUCCESSFUL (TaskWidgetModelTest cases PASSED; androidTest compiles).
- Not run: assembleDebug, device/launcher checks.

## Unresolved questions
- Device/emulator available for hit-area + render verification?
- Overdue boundary refresh: Phase 04 or Phase 06?
