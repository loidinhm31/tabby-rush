# Phase 08: Desktop actions and sticky surfaces

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: [01](phase-01-canonical-contracts-and-pure-projections.md), [03](phase-03-room-v5-and-access-persistence.md) (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; it is not implementation approval or runtime evidence.

## Parallelization Info
Wave C with05/06/07; construction depends on01/03. Exclusive desktop controller/sticky/scheduler/adapters/behavioral tests; JVM Room builder remains03 and Gradle smoke wiring09. Runtime needs02/04/05/06 and real supported host session with interactive approval. Windows and Linux host evidence remain distinct.

## Overview
- Date: 2026-10-21. Description: Desktop actions and sticky surfaces. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22**; implementation scope delivered and approved with noted issues per user-provided disposition.
- Reported verification: Cycle 3 review reports `:desktopApp:test` 95/95; earlier Windows-target regression QA separately reports 145/145 ([review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md), [QA](../../reports/qa-261021-2225-critical-fix-regression-tests.md)).
- Boundary: these unit suites do not establish interactive Windows/Linux sticky-surface or native reminder acceptance; no host UI check is claimed.
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; no tests were run for this plan reconciliation.

## Key Insights
- main.kt owns DesktopController and store startup; it currently has a default user fallback and bare-row conflict navigation. Remove inferred owner and route canonical actions end-to-end.
- Sticky surfaces must observe the existing store, not cache collaborative tasks independently. Preserve sticky geometry/font-scale unrelated work; no split ownership of large main.kt.
- Native Windows notification tags truncate task IDs today; owner-qualified canonical identity must reach suppression/cancellation without colliding equal rowIds.

## Requirements
- Desktop main app binds creator assignment/lookup and assignee editing from06 through03/05. Sticky view renders actual ChildOnly item, current capabilities and desired completion; no creator-only operations for recipients.
- Every navigation/action/reminder schedule/delivery uses TaskRef+account generation and re-resolves current rights at admission and pre-publication.
- Authoritative loss clears selected/editor/sticky content, cancels native reminders/notifications, retains generic evidence only and preserves unrelated private tasks/presentation preferences.
- Apply the confirmed privacy-first loss policy to controller/editor/sticky memory and persisted presentation state: no revoked content or unsent collaborative payload retention, recovery quarantine or replay after restart/regrant. Only content-free non-replayable notice metadata survives; fresh TaskRef/current rights govern rehydration. Preserve unrelated private tasks/preferences and do not claim offline remote erasure.

## Architecture
- DesktopController supplies restored AccountIdentity to03 binding, orchestrates TodoAction,03 mutation API and05 sync/lookup, and exposes immutable06 state. No default owner or owner inferred from current actor for received records.
- DesktopTaskSurfaceAccess and StickyTaskAction carry TaskRef; sticky model/content/list keys use owner-qualified identity. Same canonical key appears in main editor, sticky selection and reminder scheduler.
- Runtime observes03 canonical accessible projections;05 access reconciliation gates new shared publication. Delivery rechecks current projection/rights immediately before platform post. Linux/Windows adapters receive TaskRef and derive bounded OS identifier from full canonical identity; not rowId prefix truncation as uniqueness assumption.
- Include01's local accessGeneration in pending sticky/main/reminder publication admission; reject delayed pre-revocation snapshots even within the same account or after a fresh regrant. Current account generation alone is insufficient.
- Existing tray/hide/show/quit/session boundaries and presentation settings remain. Closing sticky hides; explicit Quit performs guarded teardown; no new assignment-specific notification channel.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-model.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-reminder-scheduler.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-reminder-adapter.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-reminder-adapter-provider.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/platform-reminder-dispatcher.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-surface-platform.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate.kt` — modify; Canonical desktop controller/selection/sticky/reminder actions and purge-driven refresh.
- `G:/ws/sharing/doggo/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt` — modify; Owner-qualified OS notification identity and cancellation.
- `G:/ws/sharing/doggo/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt` — modify; Owner-qualified OS notification identity and cancellation.
- `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt` — modify; Desktop canonical action, controller and lifecycle behavioral tests.
- `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-controller-lifecycle-test.kt` — modify; Desktop canonical action, controller and lifecycle behavioral tests.
- `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-controller-env-test.kt` — modify; Desktop canonical action, controller and lifecycle behavioral tests.
- `G:/ws/sharing/doggo/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-reminder-adapter-test.kt` — modify; Migrate actual platform reminder fixtures and cancellation expectations.
- `G:/ws/sharing/doggo/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter-test.kt` — modify; Migrate actual platform reminder fixtures and cancellation expectations.
- `G:/ws/sharing/doggo/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-reminder-adapter-test.kt` — modify; Migrate actual platform reminder fixtures and cancellation expectations.
- `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/collaborative-sticky-access-test.kt` — create; Child-only, duplicate-owner and stale-selection surface tests (proposed new path).
- `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate-test.kt` — modify; Migrate affected runtime/store/reminder contract fixtures under sole desktop owner.
- `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-scheduler-test.kt` — modify; Migrate affected runtime/store/reminder contract fixtures under sole desktop owner.
- `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-remote-runtime-test.kt` — modify; Migrate affected runtime/store/reminder contract fixtures under sole desktop owner.

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-model.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-reminder-scheduler.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-reminder-adapter.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-reminder-adapter-provider.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/platform-reminder-dispatcher.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-surface-platform.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-controller-lifecycle-test.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-controller-env-test.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-reminder-adapter-test.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter-test.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-reminder-adapter-test.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/collaborative-sticky-access-test.kt` | create (proposed) | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate-test.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-scheduler-test.kt` | modify | 08 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-remote-runtime-test.kt` | modify | 08 exclusively |

## Implementation Steps
1. Use LSP references for DesktopController exports, createDefaultTodo, StickyTaskAction, NativeReminderAdapter and runtime/store signatures. Migrate all owned main/sticky/scheduler/adapters/tests; preserve unrelated typography changes and main.kt single ownership.
2. Open03 store with verified restored/authenticated AccountIdentity and await owner binding before shared/sync actions; remove userId default fallback. Keep unresolved-private offline read display and current account-generation protection. Never send a hashed partition as owner.
3. Route username lookup/assignment desired-state actions to05/03, preserving expected slot version and independent content-save feedback. New owner task must be confirmed before relation dispatch; no separate task service, no inline network work in composables.
4. Migrate editor/list/conflict/sticky/deadline/navigation keys and desired completion to TaskRef. At action admission verify active account token/current accessible canonical row; under mutation re-read rights. Only Changed schedules sync; denied/missing/stale actions cannot affect other-owner same rowId.
5. Render Bob ChildOnly in sticky and main UI without root/sibling placeholders or hidden-content lookup. Use01 hierarchy coverage for counts/automatic parent completion; no subset-derived parent mutations. Assignee has no delete/assignment/child-create controls or alternate keyboard bypass.
6. On authoritative access loss clear selected/editor/sticky task, unsent collaborative editor values and any remembered remote snapshot before republishing. Observe03's confirmed payload purge/invalidation, retain generic content-free evidence only, cancel ledger-driven schedule/posted notifications, and check token/accessGeneration/current rights immediately before rendering/publication. No recovery cache/quarantine in controller or presentation files. Regrant requires fresh reconciled state at the same live key, never restores old revoked editor/outbox or auto-replays edits; OPERATION_FORBIDDEN alone preserves readable content.
7. Pass TaskRef through desktop scheduler and native adapter interfaces. Build platform tags/suppression keys from full canonical identity and occurrence/generation within platform constraints; avoid raw rowId prefix as unique ID. Use actual returned platform cancellation handles; preserve per-account isolation and fail/report cancellation failure.
8. Keep existing delivery/leave cancellation barriers, tray recovery and explicit Quit semantics. Do not add a second sticky task store, new assignment alerts, unrelated typography redesign or silent OS support fallback.
9. Add desktop behavioral tests for duplicate-owner selections/actions/tags, revoked sticky pre-publication race, ChildOnly completion/no parent mutation, account switch and unresolved binding. Exercise open unsent editor/sticky content through authoritative denial, process restart and fresh regrant: content-free notice only, no recovery cache or discarded-intent replay, unrelated private data unchanged. Migrate affected composite/remote-runtime reminder test doubles to real frozen signatures without using them as native proof.
10. Prepare09 actual Windows and supported Linux UI sequence: main+sticky visible, creator/Alice/Bob controls, lookup, child-only edit, offline process restart and reconnect after revoke, notification cancellation and stale-click rejection. Test native host-specific adapters only on matching OS; no headless screenshot implies working tray/toast.

## Todo list
- [x] Implement the desktop actions, controller, sticky surfaces and adapters in assigned files.
- [x] Add/update phase-owned behavioral and lifecycle coverage.
- [x] Implement privacy-safe access-loss handling, access-generation fencing and no-replay behavior in the desktop implementation.
- [x] Hand off implementation and pending supported-host acceptance checks to Main/integration owner.

## Success Criteria
- **Acceptance criterion:** desktop controller/surface/reminder tests reject stale generation and equal-rowId cross-owner mutation, clear revoked remembered state and preserve independent private rows. **Reported evidence:** Cycle 3 review reports 95/95 Desktop tests (earlier Windows-target regression QA reports 145/145 in a separate run); summaries do not itemize these named outcomes.
- **Native criterion not run:** actual desktop main+sticky+native reminder exercise against disposable Hub proves assignment controls, child-only editing, offline restart/reconnect and cancellation. Windows/Linux evidence must be distinguished; real native session/provider approval required.
- **Host inspection not reported:** desktop runtime/host inspection finds no revoked editor/sticky/presentation payload or recovery quarantine after authoritative loss; fresh regrant does not restore discarded edits. Generic notices, observed native cancellation and old-accessGeneration rejection must be evidenced separately from preserved private rows/preferences and OPERATION_FORBIDDEN readable state.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- OS identifier size constraints can tempt row-prefix collisions; derive from complete canonical key and preserve cancellation handles. Native toast/window lifetime differs per host; report limitations, never blanket claim erasure.
- main.kt is a large exclusive-owner integration point;06/05 must pass contract issues back rather than edit it concurrently.

## Security Considerations
- Never trust sticky cached role or caller owner alone. Account identity supplied at construction must match partition. Presentation files cannot contain task title/deadline/recipient content; privacy loss clears active/review in-memory surfaces too.

## Next steps
Deliver desktop runtime/test handoff to09; route defects to original source owner. Root/native smoke wiring belongs09. Supported-host UI approval and OS runtime availability remain explicit acceptance prerequisites.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
