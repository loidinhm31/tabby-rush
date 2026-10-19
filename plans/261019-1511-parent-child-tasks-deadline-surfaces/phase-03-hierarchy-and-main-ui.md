# Phase 03 — Shared hierarchy semantics and main UI

## Context links
[Parent plan](./evc-cmd-plan.md), [behavior contract](./design-contract.md), [Phase 02](./phase-02-room-and-sync.md), [surface research](./research/surface-ui.md), [design guidelines](../../docs/design-guidelines.md).

## Overview
Date: 2026-10-20. Priority: P2. Status: DONE (2026-10-20), credited 24h.
Implementation: marked DONE administratively on 2026-10-20 per user approval after Cycle 2 code review (score 7/10, 0 critical issues; see `plans/reports/code-reviewer-261020-0030-phase-03-hierarchy-main-ui-cycle2.md`). Centralized effective group behavior, atomic local cascade, scheduling barrier, and shared main-app UX.
- Effort: 24h credited (100% planned effort roll-up).
- Automated Verification: QA report records 512 passing validations (339 `:shared:jvmTest`, 119 `:desktopApp:test`, 43 `:androidApp:testDebugUnitTest`, 11 canonical strict-v3 vectors; 515 passing validations cited in main-agent aggregation under `--dependency-verification=off`).
- Review & Approval: Cycle 2 review scored 7/10 (0 critical issues). Approved by user with noted issues on 2026-10-20.
- Tracked Follow-up Remediations: High-severity warnings (W1: mixed-group queue drainage where synced root + unsent child delete can strand operations; W2: preservation UPDATE bypasses coalescing and subsequent manual root edit drops `LAST_CHILD_PRESERVATION` barrier), along with deferred items (Step 7 late-child sync cleanup & tombstone evidence path, unresolved-section UI, conflict-review explanatory copy, and native UI smoke testing) are formally transferred to and tracked as release-blocking remediation gates in Phase 06 (`phase-06-acceptance-and-rollout.md`).
- Dependency: Shared hierarchy interfaces freeze, unblocking Phase 04 (Android widget) and Phase 05 (Desktop sticky) surface adaptations. Matching strict-v3 Hub runtime acceptance and Phase 06 remediations remain rollout gates.
## Key Insights
- Android and desktop hosts currently duplicate mutation/filter/count handling; both must call shared semantics, not independently recompute aggregates.
- Raw wire completion and displayed effective completion must stay distinct. An edit must not accidentally persist an aggregate from a projected `Todo.copy`.
- Locally observed last-child deletion captures immediately preceding effective parent completion in a transactional parent UPDATE and restores manual completion with that resultant raw value, not the pre-children value or a conflict override. User chose local atomicity + conflict review for cross-device races, not globally ordered group mutations; see exact outcomes in the design contract.

## Requirements
- Only the main app creates children under a selected root, including an existing pre-upgrade task; child editor shows read-only parent context. Android widget and Linux/Windows sticky Add create roots only.
- Parent-with-children completion is automatic only: all live children done → done; add/reopen an incomplete child → active. Fresh store admission rejects stale manual/group-toggle actions. After locally observed last-child deletion, the childless root is manually completable again using the captured effective value.
- Exactly two levels with immutable links: no moving existing tasks under another task, detaching/reparenting children, or inherited deadline. Evaluate the full snapshot before filtering/counting; main Active/Completed shows only matching real rows plus necessary parent context, never nonmatching siblings. Compact sections retain whole groups.
- Effective completion/deletion controls persisted reminders, quiet selection, deep links and recovery as well as UI. An unresolved preserving parent UPDATE creates a client-local scheduling barrier surviving restart until accepted/discarded; reapply retains it while a new preservation intent is unresolved. Ordinary pending parent UPDATEs and other clients' authoritative schedules do not acquire this special barrier.

## Architecture
- One indexed shared evaluator over complete snapshot yields raw/effective task state, ordered groups, context/match rows and unresolved/deleted classifications.
- Shared store owns group mutation admission under existing delivery-gate→store-mutex→DB-transaction order; DAO commits each multi-row user action once and publishes/invalidate once afterward.
- Scheduling reuses hierarchy evaluation on committed base/submitted outbox, never unsubmitted editor drafts. Reload preservation-intent provenance/barrier after restart until accepted/discarded; reapply carries both into a new unresolved preservation intent. Other clients may schedule authoritative state; ordinary pending parent edits do not acquire this special barrier. Existing occurrence identities/grace/consumed-state preservation remain unchanged.
- Late-child cleanup resolves exact old operation state first and uses ordinary per-row DELETE; conflict/uncertainty survives in recovery even when group disappears normally. Pending root-delete suppression is reversible; confirmed tombstone dominance is permanent.

## Related code files
Prefix `S` = `/home/loidinh/WS/tabby-rush`.
- Modify `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/{todo-models,todo-ui-state,todo-action,account-store,task-surface-contracts,task-surface-projector,persisted-schedule-projector,persisted-task-schedule}.kt`.
- Create `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-hierarchy-projector.kt` for pure indexed hierarchy/effective-state calculation; reuse in UI and persisted schedules.
- Create `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-deadline-presentation.kt` for pure absolute deadline metadata; reuse main list and both compact renderers without notification-specific “Overdue”-only copy.
- Modify `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/{room-account-store,tabbyrush-dao,persisted-schedule-snapshot}.kt` and `sync/{account-sync-coordinator,reminder-scheduling-coordinator}.kt` where shared invalidation/recovery requires it.
- Modify `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/{todo-list,todo-editor,conflict-review,tabbyrush-app}.kt` and corresponding Compose strings.
- Modify `S/androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt`, `task-surface-access.kt`; `S/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` and `desktop-task-surfaces.kt` admission adapters.
- Extend existing `room-account-store-test.kt`, `task-surface-store-sqlite-test.kt`, `task-surface-sync-sqlite-test.kt`, shared projector and scheduler tests; create focused `task-hierarchy-projector-test.kt` for uncertain grouping/aggregate edges. Do not add model-copy/wiring tests.

## Implementation Steps
1. Run symbol references before exported changes. Define explicit projected versus stored state and automatic-parent rejection outcome; migrate all exhaustive action/result consumers without compatibility aliases.
2. Build complete-snapshot indexed grouping in stable encounter order. Aggregate effective state over all applicable children before sections/filtering; expose unresolved parent rows separately and ancestor deletion dominance. Do not aggregate a filtered/paginated child subset.
3. Add main-app-only child-create action/editor context with read-only parent identity; validate current same-account root (including an existing task), immutable two-level linkage and strict-v3 readiness at save, not only when editor opens. Main quick-create and compact Add remain root-only; edit/reapply cannot move existing tasks.
4. Guard parent completion at mutation boundary; child desired-state writes remain idempotent and preserve concurrent title/deadline fields. No parent write per child toggle; invalidate parent effective state after committed mutation/promotion/reconciliation.
5. Implement confirmed parent-group deletion with a single DAO transaction over all current known members. Allocate distinct revisions and retain per-row frozen/conflicted heads. Do not recursively acquire the existing non-reentrant store lock.
6. Implement locally observed last-child deletion + parent raw-completion capture in one transaction; retain immediately preceding effective state, enqueue necessary parent intent, and preserve its schedule token. Restore manual completion with that resultant raw value, not historical pre-children state or an override of unresolved CAS review. Concurrent disjoint deletions with no parent UPDATE use authoritative raw completion once childless; do not synthesize a conflict. Preserving parent-intent CAS conflicts remain reviewable; remote children re-establish derived state.
7. Apply deleted-parent suppression across counts/surfaces/navigation/reminder claims and durable late-child cleanup through existing outbox/recovery. Distinguish reversible pending root deletion from confirmed tombstone dominance: discarding conflicted root DELETE restores only surviving authoritative rows and re-evaluates completion/schedules, never acknowledged child deletes. Do not cancel possible-remote group creates as if every row were unsent.
8. Reuse evaluator in committed schedule projection. Persist/reload unresolved preservation-intent provenance and its client-local parent scheduling barrier until accepted/discarded; reapply retains provenance/barrier while a new preservation intent is unresolved. Recompute after discard under independent blockers; never suppress every ordinary pending parent UPDATE or claim cross-client suppression. Reconcile parent alarms/quiet selection after completion/reopen, reverse partial acceptance and root-delete discard/restart; no ledger duplication/token rotation from non-temporal changes.
9. Update both hosts and shared list/editor/filter/count/conflict rendering. Main All shows normal groups; Active/Completed selects matching real rows and adds parent context for matching children, without nonmatching siblings. A matching parent may appear alone. Compact Active retains every child in mixed groups; Completed retains fully complete groups. All metrics count unique real tasks, not context copies.
10. Add pure shared absolute deadline metadata using existing ISO convention; keep date visible for overdue, optional time only for timed deadlines, captured zone preserved. Do not introduce broad locale refactor or new polling.
11. Smoke real main UI on Android and desktop: add a child to an existing root, complete/reopen/add incomplete child, delete a completed and an incomplete last child then manually change the root, confirm/cancel group deletion, switch filters and inspect counts. Use existing behavioral suites for edge/race/recovery coverage; these are future executions.

## Todo list
- [x] Implement shared complete-snapshot evaluator and stored/effective distinction (`TaskHierarchyProjector`, `TaskDeadlinePresentation`).
- [x] Implement child creation, automatic-state admission and transactional deletion semantics (`RoomAccountStore`, `TabbyRushDao`).
- [x] Integrate recovery, committed scheduling and invalidation (`PersistedScheduleProjector` hierarchy + preservation barrier).
- [x] Migrate both hosts and shared main UI/filter/count/conflict workflows (`TodoList`, `TodoEditor`, `TabbyRushApp`, Android `SessionViewModel`, Desktop `main.kt`).
- [~] Exercise real UI and store/scheduler boundary cases (512 passing unit/contract validations; native UI smoke and boundary cases transferred to Phase 06).
- [!] Tracked Phase 06 remediations: mixed-group queue drainage (W1), preservation-marker retention during edit coalescing (W2), Step 7 late-child cleanup, unresolved-section recovery UI, and conflict-review copy.
## Success Criteria
- Every child transition, remote promotion and conflict resolution updates parent state identically in main app, widget/sticky snapshots and committed schedules.
- Parent cannot be toggled via stale UI/surface action once it has children; identical child desired state produces no extra write.
- Completed last-child case: start with raw root false and one completed child; local deletion captures true, DB reopen retains true, and manual set false succeeds once no unresolved preservation conflict remains.
- Incomplete last-child case: start with raw root true and one incomplete child; local deletion captures false, DB reopen retains false, and manual set true succeeds once no unresolved preservation conflict remains. Neither case restores the earlier pre-children raw value.
- An existing standalone root accepts a new incomplete child, becomes automatically active and rejects manual completion; all children done completes it and reopening one activates it. Moving that existing task, reparenting/detaching a child or adding a grandchild is rejected; main-only child creation and root-only compact Add remain enforced.
- Ordinary child completion produces no persisted aggregate writer. Exercise disjoint/mixed-completion deletes and preserving parent UPDATE conflicts using per-row outcomes, not global ordering/synthetic CAS. After restart, unresolved preservation intent still suppresses scheduling on its client; reapply keeps the barrier until its new intent is accepted/discarded. Ordinary pending parent edits and other clients' authoritative schedules are not subject to it.
- Reverse partial acceptance: parent preservation UPDATE accepted, child DELETE uncertain/conflicted. Restart retains both independent outcomes; discarding the child deletion never rolls back the accepted parent update. A surviving applicable child makes completion derived even if raw parent differs; after recovery/discard, UI and reminders reconcile from surviving committed/submitted state, respecting existing local deletion suppression.
- Group deletion is all-or-nothing locally; partial remote outcomes remain recoverable; late children do not reappear or schedule. If child deletes are accepted and root DELETE conflicts, discard/restart restores only surviving authoritative rows, recomputes completion/schedules, and never resurrects accepted child deletions.
- With one completed and one active child, main Active shows the active child plus parent and excludes the completed sibling; Completed shows the completed child plus context parent and excludes the active sibling. Parent aggregate/counts still use the full snapshot. Compact Active keeps both children with their parent; no group splits across compact sections.
- Parent reminder cancellation/re-enable is observed through actual coordinator/adapter boundary, preserving consumed ledger outcomes.
- No-deadline/date-only/timed metadata preserves original date, optional time and zone without leaking date-only reminder anchors.

## Risk Assessment
Derived-vs-stored confusion, filtered-subset aggregation, nested mutex acquisition, half-transaction invalidation and schedule/UI divergence are primary risks. Recovery must not convert local suppression into permanent deletion, resurrect acknowledged children, or generalize preservation-conflict barriers to all parent edits/clients. Test boundaries with real SQLite and deterministic snapshot/reconciliation cases, not assertions on implementation text.

## Security Considerations
Preserve generation admission, account isolation and wipe barriers for group reads/mutations/recovery. Missing-parent recovery must not fetch across accounts. Preserve direct-boot/privacy rules.

## Next steps
Phase 03 shared hierarchy semantics and main UI are marked DONE administratively on 2026-10-20 (24h credited). Shared interfaces freeze, unblocking Phase 04 (Android widget) and Phase 05 (Desktop sticky) surface adaptations. Technical acceptance remains qualified: W1 mixed-group queue drainage, W2 preservation-marker retention during edit coalescing, Step 7 late-child cleanup, unresolved recovery UI, conflict-review copy, and native UI smoke are tracked in Phase 06 (`phase-06-acceptance-and-rollout.md`). Unresolved product questions: none.
