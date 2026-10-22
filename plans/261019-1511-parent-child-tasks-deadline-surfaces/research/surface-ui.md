# Main UI, widget and desktop sticky research

Date: 2026-10-19. Source: terminal read-only `SurfaceUiResearch` report plus parent reads of rendering/projection/recipes. No runtime screenshots or tests produced.

## Findings
- Main UI already renders deadlines; compact surfaces do not. Deadline data is already present in `Todo` consumed by the widget and sticky rows.
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt:218-246` formats stored ISO date/optional time/zone inline and separately renders “Overdue.”
- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget.kt:376-427`: fixed 48dp row, one-line title, checkbox, token-fenced navigation; no metadata row.
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt:377-439`: min-height row, checkbox/title only.
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-surface-projector.kt:16-32` partitions individual rows by completion; no hierarchy.
- `TaskSurfaceAccess` and Android callbacks/Desktop controller already fence by account and generation. Keep those paths; UI disabling alone cannot enforce automatic parent completion.
- Main list filtering/counting/mutations are implemented separately by Android `SessionViewModel` and desktop `main.kt`; business rules must live in shared store/projection to prevent drift.
- No current row cap/truncation found. Do not introduce a cap that detaches children or hides child deadlines.
- Android notification formatter (`task-surface-notification.kt:91-108`) replaces dates with “Overdue.” Do not reuse that output for compact-row deadlines: absolute date must remain visible.
- ISO calendar/date-time strings are existing convention. No need to add a locale engine, relative-day polling, or alter scheduler timing for this request.

## Existing files and owners
| Area | Files |
|---|---|
| Shared task actions/editor/state | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/{todo-action,todo-ui-state}.kt`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/{todo-list,todo-editor,conflict-review,tabbyrush-app}.kt` |
| Android main host | `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` (`:130-177` list/filter/count; `:345-435` mutations) |
| Desktop main host | `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` (`:500-548`, `:700-792`) |
| Widget | `androidApp/src/main/kotlin/dev/tabbyrush/android/{task-widget,task-widget-actions,task-widget-updater,task-surface-runtime-coordinator,task-surface-access}.kt` |
| Widget copy/resources | `androidApp/src/main/res/values/task-widget-strings.xml`; `androidApp/src/main/res/xml/task_widget_info.xml` (change dimensions only if actual layout needs it) |
| Sticky mode | `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/{sticky-task-model,sticky-task-content,desktop-task-surfaces}.kt` |
| Shared surface contracts | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/{task-surface-contracts,task-surface-projector}.kt` |
| Tests | `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-surface-projector-test.kt`; `androidApp/src/test/kotlin/dev/tabbyrush/android/task-widget-model-test.kt`; `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-widget-device-test.kt`; `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt` |

## Proposed presentation
- Compact surfaces (Android widget and desktop sticky mode) group roots and children before sectioning; mixed groups remain intact under Active (containing parent, active children, and completed children together); Completed contains fully complete groups only; never split a group across sections or omit nonmatching siblings in compact view.
- Contrast with main app Active/Completed filters: main app shows matching rows plus parent context, NOT all siblings. For example, for root R with active child C1 and completed child C2: R's effective state is active (since C1 is incomplete). Main Active filter shows R (matches directly) and C1 (matches directly) with match count = 2, while C2 is omitted. Main Completed filter shows C2 (matches directly, match count = 1) with R appearing solely as context without inflating match counts, while C1 is omitted.
- All compact group children remain visible and scrollable. Root checkbox is read-only whenever live children exist (automatic progress only); child toggles update parent; locally observed last-child deletion without concurrent conflict in main app preserves prior effective completion into raw state and restores parent manual toggle with resulting raw value (no compact delete expansion).
- Child creation and confirmed group deletion belong exclusively to main UI. Existing widget and desktop sticky quick-add remain independent root creation (even when navigating or viewing a group); no compact child creation, reparenting, or compact delete expansion.
- Use title + independent deadline line, child indentation, explicit automatic parent state, >=48dp controls, readable metadata, and full accessibility labels.
- Rows without deadlines omit deadline metadata line entirely (no placeholder or "No deadline" text). Date-only shows `Due YYYY-MM-DD`; timed shows `Due YYYY-MM-DD HH:mm (captured zone)`. Never render date-only reminder anchor as deadline time; no deadline inheritance from parent. Overdue text supplements, never replaces, absolute date.
- Unsubmitted editor drafts never affect committed scheduling or quiet deadline selection. Shared effective, suppression, and account-token semantics (`SurfaceAccountToken`) are consumed without platform forks; do not impose a new draft-publication policy on existing UI observation.

## Commands verified in justfile, not run
- `just run-desktop` / `just run-desktop-debug`: Gradle desktop launch with host target.
- `just run-android`: installs debug APK then launches `dev.tabbyrush.android/.MainActivity`.
- `just test`: shared JVM + desktop tests; Android unit tests are separate.
- `./gradlew :androidApp:testDebugUnitTest`, `./gradlew :androidApp:assembleDebug`.
- Linux target: `./gradlew -PdesktopTarget=linux :desktopApp:test`; Windows requires Windows host, not cross-targeting this Linux build.

## Acceptance boundaries
- Existing `docs/evidence/sticky-surfaces-*.png` assets are design baselines, not observed runtime screenshots.
- `docs/android-acceptance.md`, `docs/linux-acceptance.md`, `docs/windows-acceptance.md` retain native/platform acceptance holds.
- Verify pre-existing standalone root receives newly created child in main app, with reparenting, grandchildren, and self-referencing strictly rejected.
- Verify root-only compact Add on widget and sticky surfaces while main app supports child creation.
- Verify exact mixed group filter behavior (matching child + parent context only) vs compact whole-group behavior (parent + all siblings intact under Active).
- Verify locally observed last-child deletion preserves immediately preceding effective completion into raw state atomically in SQLite and restores manual completion with resulting raw state (whether last child was completed or incomplete); qualify that this is local atomicity, not a cross-device global guarantee (disjoint deletions across devices without a last-child observation leave childless root with authoritative stored manual completion without synthetic CAS).
- Verify preservation-intent scheduling suppression is client-local and restart-durable until accepted/discarded; reapply retains provenance/barrier while a new preservation intent remains unresolved. Ordinary pending parent edits do not acquire it. Root-delete discard restores surviving rows only, never acknowledged child deletions.
- Verify unsubmitted editor drafts never affect committed scheduling or quiet deadline selection.
- Verify root confirmation in the current account/epoch before a fresh child's initial freeze/dispatch, while already-admitted frozen/`UNCERTAIN` replay bypasses renewed live-parent gating and evaluates exact receipts first without bypassing authorization/integrity checks.
- Verify actual launcher widget at responsive sizes, date-only/timed/overdue/no-deadline rows, child completion, refresh/restart, privacy, and 200% font.
- Verify actual Linux and Windows sticky windows, keyboard/screen-reader traversal, scaling, tray/no-tray recovery and account switches. Linux compile/unit success does not prove Windows runtime.

## Unresolved prerequisites
Interactive platform testbeds are not verified available. Overflow/filter policies are specified in the design contract rather than left to individual platform implementations.
