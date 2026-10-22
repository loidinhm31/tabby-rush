# Phase 06: Shared Compose assignee workflows

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: [01](phase-01-canonical-contracts-and-pure-projections.md), [03](phase-03-room-v5-and-access-persistence.md) (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; it is not implementation approval or runtime evidence.

## Parallelization Info
Wave C with05/07/08; construction depends on01/03. Exclusive shared Compose UI/tests. Consumes typed assignment/lookup/coordinator contracts; runtime save/lookup/sync needs05 plus platform controller07/08. Do not edit contract types, store or platform controllers here.

## Overview
- Date: 2026-10-21. Description: Shared Compose assignee workflows. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22**; implementation scope delivered and approved with noted issues per user-provided disposition.
- Reported verification: Cycle 3 review reports `:shared:jvmTest` 179/179 including workflow coverage; selected QA reports the shared JVM suite 329/329 ([review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md), [QA](../../reports/qa-261022-1521-selected-test-suite-results.md)).
- Boundary: reported model/workflow suites do not substitute for native Android/desktop UI journeys or acceptance.
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; no tests were run for this plan reconciliation.

## Key Insights
- Creator/assignee controls differ, but UI hiding alone is not enforcement. Child-only content is a real task with no synthetic parent/context/sibling metadata.
- Current Android/Desktop controllers handle actions outside shared UI; expose frozen actions and require both platform owners to bind them, not a second coordinator inside Compose.

## Requirements
- Creator optional username picker with selected direct assignee, direct versus inherited indicators and version-conflict feedback. Recipient can edit supported content/deadline/completion/reminders only.
- Assignment save is independent relation intent; new task initial assignment awaits acknowledged task create. Keep content edits independent and durable under existing editor flow.
- Canonical keys for lists/editor/conflict/navigation, accessible child-only layout, full-group-only parent completion semantics; generic access-loss notice contains no revoked title/diff.
- Apply the confirmed privacy policy to all UI-held editor/selection/conflict state: clear revoked content and unsent collaborative snapshots, show only a generic changes-not-delivered notice backed by content-free metadata. No recovery/quarantine, copy/export or reapply controls for purged edits, including after regrant.
- For opted-in sync capability denial show an actionable whole-app upgrade-required state, not a private-only sync fallback. Capability loss is not permission to reinterpret shared content as private or claim offline caches were remotely erased.

## Architecture
- Immutable TodoUiState carries mode/capabilities/access revision, TaskRef selection, assignment slot version/recipient state, lookup loading/error/result, and content-free access-loss evidence. TodoAction includes lookup username, select/clear direct assignee and save assignment with expected slot version.
- Creator controls derive from trusted local capabilities; direct/inherited labels are explicit text, not colors. Capability pending/absent disables assignment affordance with upgrade/reconcile guidance.
- Picker never invokes Hub admin API; actions flow to platform controller->05 lookup or03 assignment API. Shared UI has no platform or database ownership.
- ChildOnly row/editor shows shared-child label, its real content/deadline; no parent title/placeholder fake row/sibling counts. FullGroup projection from01 controls completion grouping; staged coverage is not rendered as a full group.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt` — modify; Canonical selection/actions, assignment workflows, child-only UI and privacy-safe review.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt` — modify; Canonical selection/actions, assignment workflows, child-only UI and privacy-safe review.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt` — modify; Canonical selection/actions, assignment workflows, child-only UI and privacy-safe review.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/conflict-review.kt` — modify; Canonical selection/actions, assignment workflows, child-only UI and privacy-safe review.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/sync-feedback.kt` — modify; Canonical selection/actions, assignment workflows, child-only UI and privacy-safe review.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt` — modify; Update meaningful role/permission and canonical selection semantics.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/task-assignee-picker.kt` — create; Username picker, direct/inherited indicators and observable role actions (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/task-assignee-workflow-test.kt` — create; Username picker, direct/inherited indicators and observable role actions (proposed new path).

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt` | modify | 06 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt` | modify | 06 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt` | modify | 06 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/conflict-review.kt` | modify | 06 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/sync-feedback.kt` | modify | 06 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt` | modify | 06 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/task-assignee-picker.kt` | create (proposed) | 06 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/task-assignee-workflow-test.kt` | create (proposed) | 06 exclusively |

## Implementation Steps
1. Use LSP references for affected UI public composables/actions/state and migrate owned list/editor/review callers to TaskRef. Preserve theme/accessibility and existing deadline picker/reminder behavior.
2. Add task-assignee-picker using existing form/feedback patterns: creator opens optional picker, submits username lookup, chooses resolved user, clears direct assignee, and sees direct/inherited labels. Use stable request identity so typed state can ignore obsolete lookup; only controller performs HTTP.
3. Send distinct assignment desired-state action with expected slot version. Keep content save and assignment result independent; display task-create-awaiting-sync state for initial assignment without implying grant exists. A relationship conflict prompts reload/review, never silent last-write-wins.
4. Hide/disable assignment/delete/restore/add-child for assignee and unavailable/unreconciled states; keep existing content/deadline/completion/reminder controls only when capabilities allow. Bind desired completion rather than cached toggle.
5. Render ChildOnly as actual task in list/editor on compact and wide layouts; do not request private parent to satisfy layout. Avoid parent navigation/sibling indicators and automatic group completion from partial subset. Canonical list keys and selected-task comparisons must include owner.
6. On authoritative access loss, clear revoked editor/selection/conflict snapshots, including remembered unsent text/diffs, and show generic unsent-changes-not-delivered notice using identity/version-only evidence. Explicit dismissal removes03 evidence through action. Implement the confirmed no-quarantine/no-recovery policy: no copy/export/reapply path or automatic replay after regrant; fresh task state cannot resurrect old editor values. Do not clear still-readable content merely because an action is OPERATION_FORBIDDEN.
7. Keep conflict review explicit for supported canonical content fields and slot conflicts. Capability denial shows whole-app upgrade-required/configuration guidance with no private-only retry and does not silently switch shared rows to private ownership. New clients in genuine private legacy mode retain current UI without assignment controls; do not imply remote offline-cache erasure.
8. Add behavioral semantics/workflow checks for creator versus Alice/Bob, child-only without parent, canonical duplicate-owner selection, pending task-create assignment, slot CAS conflict, inaccessible editor reset, no revoked content in review and independent content/assignment save. Do not assert source/default copies instead of visible behavior.

## Todo list
- [x] Implement the shared workflows, role-aware controls, child-only rendering and privacy-safe feedback in assigned files.
- [x] Add/update phase-owned behavioral and accessibility semantics coverage.
- [x] Implement the confirmed privacy reset and content-free notice/dismissal flow; preserve whole-app upgrade guidance without private downgrade.
- [x] Hand off implementation and pending native acceptance checks to Main/integration owner.

## Success Criteria
- **Acceptance criterion (scenario evidence not itemized):** shared UI behavioral checks observe correct roles, separate save feedback, real ChildOnly item and generic privacy notice. List/editor keys cannot confuse equal rowIds from different owners. Shared JVM workflow suites are reported passed; results are not broken down by each behavior.
- **Native criterion not run:** Android and desktop UI exercise confirms picker/lookup/control routing, keyboard/focus/touch targets, child-only editing/offline reconnect. These runtime gates belong to Phases 07/08/09, not a shared screenshot/mock claim.
- **Full privacy/UI criterion not evidenced:** UI workflow checks verify revoked titles/text/diffs disappear from open editors and review state, generic notice contains no content and no recovery action, and regrant shows only freshly hydrated state. Forbidden-action feedback preserves readable content; capability denial offers upgrade guidance rather than private-only sync. Native UI proof remains pending.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- Existing editors may retain removed remote state in Compose remember variables; key/reset by TaskRef+access generation and remove content on invalidation. Avoid race between lookup result and changed account/recipient choice.

## Security Considerations
- Treat username lookup as untrusted identity resolution; server chooses eligibility. Never reveal private parent/other recipient via inherited labels. Access-loss notice must omit title/deadline/diff even when editor was open.

## Next steps
Hand off frozen UI actions/state usage to07/08 and lookup feedback binding to05.09 records real platform accessibility and workflow evidence; UI integration defects return here.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
