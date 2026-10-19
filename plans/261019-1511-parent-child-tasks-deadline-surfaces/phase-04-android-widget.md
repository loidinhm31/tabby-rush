# Phase 04 — Android grouped widget and deadline labels

## Context links
[Parent plan](./evc-cmd-plan.md), [behavior contract](./design-contract.md), [Phase 03 (shared contracts & evaluator)](./phase-03-hierarchy-and-main-ui.md), [surface research](./research/surface-ui.md), [Android acceptance](../../docs/android-acceptance.md).

Dependency: Depends on Phase 3 settled shared evaluator, admission, deadline presentation, and committed scheduling. Consumes shared projection without a separate Android store or protocol fork.

## Overview
Date: 2026-10-19. Priority: P2. Implementation: pending. Review: pending. Provisional effort: 12h (subtotal 48h for surface & rollout phases 4–6). Render the shared group projection and actual deadline dates in the native launcher widget.

## Key Insights
- Widget already receives full `Todo` deadlines; current `TaskRow` has fixed 48dp height and one-line title. Adding text without changing layout risks clipping.
- Android widgets are Glance, not shared Compose UI. Shared business/format contracts, evaluator, and store admission must be consumed directly; Android render/action resources stay native.
- Notification formatter replaces dates with “Overdue”; that is not the requested widget display. Plain absolute dates require no periodic polling; overdue decoration supplements rather than replaces the date.
- Child creation and confirmed group deletion belong exclusively to the main application; widget Add button creates independent root tasks only (even when viewing a group).

## Requirements
- **Main-app-only child creation**: Widget Add action creates independent root tasks only; no child creation, reparenting, or compact editor expansion from widget (even when navigating or viewing a group).
- **Whole compact groups**: Root followed by indented children; mixed groups remain intact under Active (including completed sibling children); fully complete groups under Completed; never split a group across sections or omit nonmatching siblings in compact view (contrasting with main app matching-only filters with parent context).
- **Independent own deadlines**: Every dated row shows its own stored absolute date and, for timed deadlines, time and captured IANA zone; no deadline inheritance from parent; date-only reminder anchor is never displayed as deadline time; rows without deadlines omit deadline metadata.
- **Automatic parent completion**: Parent state is read-only/automatic progress while live children exist (all done => done; add/reopen incomplete child => active); child and standalone root checkboxes are interactive; no group toggle; deleting last child via main app restores parent to manual completion with resulting raw state (no compact delete expansion).
- **Shared effective and suppression semantics**: Widget consumes shared Phase 3 evaluator and admission; local parent deletion immediately suppresses group descendants; no Android-specific hierarchy or scheduling forks.
- **Token and security fences**: Preserve identity-only navigation/actions, `SurfaceAccountToken(accountKey, generation)` fencing, privacy placeholders (signed-out/locked), and >=48dp touch targets.

## Architecture
Shared snapshot/group projection (Phase 3 evaluator) → account-token-fenced widget model/publication (`SurfaceAccountToken(accountKey, generation)`) → adaptive Glance row layout → existing completion/navigation callbacks → fresh shared store admission.

Absolute metadata updates with task/deadline snapshots. No new periodic polling for dates; optional overdue decoration reuses existing lifecycle-owned time/boundary refresh and never replaces the date. Deletion suppression, effective state, and committed scheduling follow shared contracts without platform forks.

## Related code files
Prefix `S` = `/home/loidinh/WS/tabby-rush`.
- Modify `S/androidApp/src/main/kotlin/dev/tabbyrush/android/{task-widget,task-widget-actions,task-widget-updater,task-surface-access,task-surface-runtime-coordinator}.kt` as required for changed shared models/admission.
- Modify `S/androidApp/src/main/res/values/task-widget-strings.xml` for group/automatic/deadline accessibility copy.
- Inspect existing `S/androidApp/src/main/res/xml/task_widget_info.xml` and initial layout; change only if demonstrated responsive layout needs it. No new exported receiver/activity/service.
- Update existing `S/androidApp/src/test/kotlin/dev/tabbyrush/android/task-widget-model-test.kt` for changed behavioral grouping/action semantics; remove tests that merely pin obsolete flat incidental ordering.
- Use/extend `S/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/{task-widget-device-test,task-surface-account-device-test,task-surface-navigation-device-test}.kt` only for consumer-visible transitions not covered by existing boundaries.
- Shared files are Phase 3 integration-owner territory; no separate Android hierarchy rules.

## Implementation Steps
1. Adapt widget state to Phase 3 shared groups/effective rows, preserving signed-out/loading/locked/unavailable placeholders without private title/deadline leakage. Keep whole compact groups intact in Active (showing parent plus all active and completed siblings).
2. Replace fixed single-row height with Glance-supported adaptive title+metadata layout. Retain >=48dp interactive bounds, readable metadata, and child indentation at every responsive size.
3. Render shared deadline parts using Android copy/resources. Row with no deadline omits metadata line; date-only shows `Due YYYY-MM-DD`; timed shows `Due YYYY-MM-DD HH:mm (captured zone)`. Absolute date remains visible for overdue rows; date-only reminder anchor is never shown as deadline time.
4. Render parent progress/automatic semantics without a completion callback when children exist. Child actions pass desired state plus existing account/generation token and revalidate in shared store admission. Stale cached parent completion actions are rejected by store admission.
5. Preserve stable row IDs/group adjacency, all-child scrolling and section placement. Widget Add action creates independent root tasks only (even when viewing a group); no compact child creation, reparenting, or delete expansion.
6. Wire publication to group/deadline changes and remote promotion. Ensure parent deletion suppresses group rows immediately; deleting last child in main app restores parent manual toggle with resulting raw state on widget refresh. Keep callbacks identity-only; never embed titles/deadlines in PendingIntent extras or logs.
7. Future verification: Install and launch real debug app, authenticate test account, pin real widget through launcher, create grouped tasks with date-only/timed/overdue/no-deadline cases, and observe actual rendered labels/actions/refresh.
8. Future verification: Exercise responsive 120×120, 240×120, 240×240 and 320×360dp layouts plus 200% text. Observe after child completion/reopen, remote sync, app restart and account switch. Capture runtime screenshots, not Compose/design mockups.

## Todo list
- [ ] Adapt native widget model to shared grouped/effective state.
- [ ] Implement adaptive own-deadline rows and accessibility labels.
- [ ] Preserve automatic-parent and token-fenced child actions.
- [ ] Verify real launcher behavior, refresh/privacy and responsive scaling.

## Success Criteria
- Real launcher screenshot shows whole compact groups (mixed group shows parent + active and completed children intact under Active) with each row's own stored deadline date/optional time/zone; no inheritance; date remains present when overdue; row with no deadline omits deadline metadata line entirely.
- Widget Add button creates an independent root task only, even when a group is displayed; child creation remains main-app-only.
- Checkbox actions complete/reopen a child and automatically refresh parent state; parent has no active toggle while children exist; stale cached parent completion action is rejected by store admission.
- Parent completion restored to manual with resulting raw state after last child is deleted in main app, accurately reflected in widget.
- Date-only row never shows reminder anchor time; timed zone/date does not drift after device timezone change; clearing/editing deadline refreshes the widget.
- Narrow/large-font layout keeps deadline readable/scrollable and controls accessible (>=48dp), without detached child rows or clipped-only dates.
- Cold load, logout/switch and stale callbacks do not reveal or mutate another account's rows (`SurfaceAccountToken` enforced).
- Future commands: `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug`, `just run-android`; instrumented execution on connected device through existing Gradle task. Compile/tests do not substitute for pinning/using the launcher widget.

## Risk Assessment
Glance rendering/font constraints and launcher refresh/session behavior need runtime proof. Do not reuse main matching-only filters to drop completed siblings from compact groups or mistake a restored manual root for permission to override conflict admission. Existing widget cleanup and provenance holds remain separate; do not silently fix or declare them resolved in this feature.

## Security Considerations
No exported-component changes. Keep account/generation/locked-storage barriers and generic placeholders. Runtime evidence uses non-sensitive test data.

## Next steps
Feed Android screenshots/commands/device details into Phase 6. Phase 5 can run independently after shared contracts settle. Unresolved prerequisites: connected launcher-capable Android testbed, not verified in this planning run.
