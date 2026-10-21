# TabbyRush Project Changelog

All notable changes to the TabbyRush project are documented in this file, organized chronologically by implementation phase.

---
## [Desktop Sticky Font Resizing & Compact Layout] - 2026-10-21: Implementation & Automated Verification

### Delivered
- **Desktop Sticky Font Resizing (Text Zoom)**: Delivered interactive font size scaling (50% to 150%, 12.5% step) in the desktop companion sticky window, supporting scale values under 75% down to 50% for high information density. Added header zoom controls (`A−`, percentage reset, `A+`) and global keyboard shortcuts (`Ctrl+=`, `Ctrl+-`, `Ctrl+0`) with zero runtime dependencies.
- **Automatic Responsive Font Scaling on Window Resize**: Font size adapts automatically as window width changes relative to standard 360dp width, combining dynamically with manual zoom preference (`computeEffectiveStickyFontScale`). Clicking the percentage label resets manual zoom to the automatic baseline.
- **Default Collapsed Task Hierarchy**: Parent tasks with live subtasks in both `TodoList` and `StickyTaskContent` default to collapsed (`expandedParentIds` starts empty), cleanly concealing subtasks until the user explicitly expands them.
- **Presentation Persistence**: Scaled font preference is persisted across restarts in `sticky-window.properties` (`fontScale` and `sticky.window.font_scale` alias) alongside window geometry and visibility. Legacy preference files and out-of-range/non-finite values safely fall back to default (1.0f).
- **Dense Compact Desktop Layout**: Optimized sticky companion components for mouse/keyboard viewports:
  - Reduced outer padding from 12dp to 6dp.
  - Compact `FlowRow` header bar with 14sp title, integrated zoom controls, and compact 26–28dp recovery buttons (`Open`, `Quit`) that wrap gracefully on narrow displays.
  - Compact 32dp quick-add field (`BasicTextField`) and button with responsive Enter/Escape key actions.
  - Compact task rows (`CompactTaskRow`): 22dp checkboxes and subtask toggles, 28dp minimum row height, and 2–4dp component spacing.
- **Accessibility Invariant Preserved**: Sticky-only desktop control sizing maintains full accessibility: screen-reader semantics (`contentDescription`, `stateDescription`), keyboard navigation (Tab/Shift-Tab, Space, Enter), and focus rings.
### Reported Verification
- **Desktop Unit & Controller Suite**: 131/131 tests in `:desktopApp:test` passed, including new test cases for font scale round-trip, out-of-bounds/non-finite recovery, legacy compatibility, zoom actions and clamping, readiness transitions, and `DesktopController` persistence.
- **Shared JVM Suite**: 268/268 tests in `:shared:jvmTest` passed with zero regressions.

---
## [Native Standard Sync & API-Managed Schema Version / Phases 02–04] - 2026-10-20: Source Delivery, Integrated Verification and Isolated Smoke

This cutover work is for the native TabbyRush Android/Desktop app, not a TabbyRush web client. Previously delivered strict-v3 work remains historical context; source delivery does not authorize live migration.

### Delivered
- **Versioned Hub schema writes:** Added server-managed `schemaVersion` with missing baseline 1, compare-and-set expected version, increment on effective schema changes, stable no-op/retry version, and structured 409 for stale distinct changes. Standard push/pull responses carry schema snapshot metadata; unknown record JSON remains accepted.
- **Native standard-sync source:** Delivered Room4 migration, standard push/pull/delta transport, raw JSON base/merge and unknown-field preservation. Room4 migration and raw-JSON regressions passed in the integrated shared JVM run; native interactive/device acceptance remains open.
- **Admin editor source:** Kept the existing schema builder and JSON editor. Added authoritative versioned save/readback and draft-safe conflict handling; no separate editor or version lock was introduced.

### Reported Verification
- **Disposable API/browser smoke:** Against local Mongo 27191, Hub 3100 and admin 3102, baseline 1 changed to 2; identical save/no-op remained 2; a stale distinct save returned 409. Actual JSON paste of nullable `customNotes` moved 1→2; stale 409 preserved the draft and latest inspection remained read-only; explicit discard/reload adopted version 2. Standard create/pull/delta/update/delete retained unknown JSON. Ordinary provisioning used private-DACL output. No live production data was migrated.
- **Backend/operator:** Parent reports 83 affected Rust tests passed and 1 pre-existing strict-live-gate ignored (66 plugin, 5 schema Mongo, 7 operator Mongo, 4 client, 1 engine). The 7/7 operator suite covers concurrency, maintenance-deadline and recovery. `cargo check --workspace --all-targets` passed with existing unused warnings.
- **Hub admin and Node:** Admin unit suite passed 22 and TypeScript/Vite production build passed; Node proxy/version/security suites passed 43.
- **Native Gradle/runtime:** Shared JVM 264, Desktop 131 and Android unit 50 passed (445 total); `assembleDebug`, `assembleDebugAndroidTest` and Windows `createDistributable` succeeded. The isolated Android artifact manifest uses `dev.tabbyrush.android.standardsmoke`. A parent-reported standalone Windows runtime smoke passed using real DPAPI, HubAuthSession HTTP, Room offline restart and two stores/coordinators; schemaVersion 2, opaque nested JSON/`customNotes`, parent-child sync and group delete were exercised without mocks or JUnit. Android connected instrumentation and interactive UI were not run.
- **Dependency/review:** 50 checksum entries were officially validated, including the AAPT Windows addition; strict integrated Gradle verification passed. The [native final review](../plans/261020-1717-standard-sync-schema-version/reports/review-native-final.md) and [Hub/operator final review](../plans/261020-1717-standard-sync-schema-version/reports/review-hub-ui-final.md) are source-only approvals with no actionable findings.

All current execution results above are parent-reported for 2026-10-20; this changelog update did not rerun checks. The current evidence record is the [final integration verification report](../plans/261020-1717-standard-sync-schema-version/reports/verification-final.md), which supersedes pre-fix verification snapshots without closing the acceptance holds below.

- Android connected instrumentation was not run. Interactive native UI acceptance remains held: computer-control grant timed out twice and no active grant was available. Automated builds/tests and the standalone Windows runtime smoke do not establish broader Android/Desktop UI acceptance.
- Existing release holds remain open. No live production migration is authorized. The inherited strict-v3 restore procedure is **not approved for execution**: it targets an incorrect MongoDB namespace and omits `_schemas`/`_coordination`; do not use it for Option B. Any cutover requires exact-target approval, all-node/job maintenance isolation, a separately validated backup/restore, and explicit preservation/ownership of every user's data.
- The actual browser proof is a smoke subset, not full acceptance. Remaining named cases are held in [phase04](../plans/261020-1717-standard-sync-schema-version/phase-04-admin-ui.md): stale-identical retry and ordering semantics; reload/readback and valid standard-record path; builder/JSON/sharing draft retention; readback failure/newer response; mode, registration, strict-panel and key-custody behavior; delayed route-change completion and remaining consumer-visible validation cases.
---

## [Parent/Child Tasks & Deadline Surfaces / Phase 06] - 2026-10-20: Cross-Device Acceptance, Rollout & Documentation Reconciliation

### Delivered
- **Sync-Layer Late-Child Cleanup & Tombstone Dominance**: Added Step 2 late-child cleanup in `AccountSyncCoordinator.reconcileLateChildren` executing locked check-then-act verification under mutation lock. When an orphaned child task arrives concurrently whose parent root is tombstoned, the coordinator enqueues an ordinary per-row DELETE using the confirmed server revision. Tombstones dominate to suppress late children from normal task lists and reminder schedules, preventing orphan leaks.
- **Unresolved-Child Recovery UI**: Delivered a dedicated recovery section in `TodoList` (`todo-list.kt`) and `ConflictReviewDialog` (`conflict-review.kt`) that renders tasks whose parent is absent without a tombstone (`isUnresolvedChild = true`), isolating them from normal hierarchy and reminder schedules without accidental promotion or silent deletion.
- **Conflict Review Explanatory Copy**: Added explanatory messaging in `ConflictReviewDialog` (`conflict-review.kt`) describing CAS conflict reasons, last-child preservation barriers, and partial group outcomes.
- **Queue Drainage & Marker Retention Remediations**: Remediated mixed-group deletion (W1) in `RoomAccountStore.deleteTodoLocally` by transactionally cancelling unsent child operations (`cancelUnsentGroupAtomic`) when a synced parent root is deleted. Remediated preservation marker retention (W2) so subsequent manual root edits coalescing into a pending preservation op retain the `INTENT_LAST_CHILD_PRESERVATION` intent mask and client-local scheduling barrier.
- **Authoritative Strict-v3 Operator Runbook & Upgrade CLI**: Created `docs/strict-v3-cutover-runbook.md` with in-place upgrade invariants, backup topologies (whole-instance oplog dump and quiesced database dump), same-ID resume semantics, canonical schema fingerprints (`7f033e7b...`), and post-cutover verification. Updated `scripts/hub-strict-upgrade.mjs` to support `--target-version strict-v3` with 17-column read-back validation.
- **Shared Documentation Reconciliation**: Reconciled `README.md`, `docs/release-acceptance.md`, `docs/strict-v3-cutover-runbook.md`, and `docs/project-changelog.md` to reflect strict-v3 parent/child hierarchy, compact deadline display surfaces, late-child cleanup, and operator procedures.

### Reported Verification Evidence & Post-Cycle Caveats
- **Reported Verification Totals**: Cycle 2 QA report ([qa-261020-0747-phase-06-cycle-2-test-validation.md](../plans/reports/qa-261020-0747-phase-06-cycle-2-test-validation.md)) records 685 passing tests/checks across 15 verification groups (527 Gradle unit/integration tests across shared JVM [347], desktop [130], and Android [50]; 158 Node.js script, vector, and static checks; executed with `--dependency-verification=off`).
- **Post-Cycle 2 Runtime Issue (Active Remediation)**: Cycle 3 code review ([code-reviewer-261020-0800-phase-06-cycle-3.md](../plans/reports/code-reviewer-261020-0800-phase-06-cycle-3.md)) identified a deterministic failure in `DesktopTaskSurfacesTest.testStateClearedOnSignedOut` where non-Ready branch condition `currentToken == null && activeToken == null` left previous-account sticky state unblanked after sign-out/runtime close. Remediation is active.
- **Operational Holds & Deferred Acceptance**:
  - *Gate G2 (Physical Connected Device & Native Display)*: Physical Glance widget responsive layouts (120×120 to 320×360dp), TalkBack, 200% font scaling, and Doze timing on Android; graphical D-Bus notifications and Orca on Linux; and native Windows toast bridge and Narrator traversal remain formally held pending hardware testbeds.
  - *Gate G3 (Multi-Instance Hub Staging Cluster)*: Live multi-node MongoDB replica set staging rehearsal remains held; Hub dry-run recovery side-effect in Rust service remains open.
  - *Gate G7 (Production Cutover Gate)*: Production cutover is held pending operator authorization and resolution of operational release gates.
  - *Supply-Chain Integrity*: The 149-checksum dependency verification baseline (`gradle/verification-metadata.xml`) and separate Glance dependency provenance hold remain open.

---
## [Parent/Child Tasks & Deadline Surfaces / Phase 05] - 2026-10-20: Linux/Windows Grouped Sticky Mode & Deadlines

### Delivered
- **Pure projectStickySections Projection & Whole Compact Groups**: Added stateless projection functions `projectStickySections` and `projectStickyItems` in `sticky-task-model.kt` mapping raw section tasks through shared `TaskHierarchyProjector.projectHierarchy` with `TodoFilter.ALL`. Preserves whole compact groups intact within Incomplete (active roots display with all active and completed sibling children) and Completed sections without splitting groups across sections. Enforces ancestor deletion dominance and effective parent completion without platform-specific forks.
- **Absolute Own Deadlines & Overdue Styling**: Implemented per-row absolute deadline presentation using `TaskDeadlinePresentation.formatDeadline`. Formats absolute date (`"Due YYYY-MM-DD"`) and timed deadlines (`"Due YYYY-MM-DD HH:mm (captured zone)"`) strictly from each task's own stored deadline, with zero deadline inheritance between parent and child. Date-only reminder rule anchor (09:00 default) is never leaked as deadline time; tasks lacking deadlines omit the metadata row. Overdue active rows render bold error-colored badge text on `TabbyRushCoralSurface` while keeping the absolute date visible; completed tasks never render overdue.
- **Compact Grouped Layout & Automatic Parent Progress**: `CompactTaskRow` in `sticky-task-content.kt` applies `SpacingLarge` start indentation for child subtasks (`isChild = true`). Parents with live children render read-only, disabled `Checkbox(enabled = false, onCheckedChange = null)` reflecting automatic completion progress (`Subtasks: X/Y completed`) and TalkBack/screen-reader state descriptions (`"Completed/Active (automatic: X/Y subtasks)"`). Standalone roots and leaf children bind interactive checkboxes. Title text supports 2 lines with ellipsis; deadline and overdue badges layout via `FlowRow`. Controls provide `>=48dp` minimum touch targets as layout provisions (physical touch/font bounds remain subject to native platform verification).
- **Independent Root Quick-Add & Fenced Controller Mutations**: Desktop sticky quick-add bar creates independent root tasks only (`createDefaultTodo(title, nowEpochMs = clock())`), even when interacting with a group row; child creation remains main-app-only. Actions validate `activeToken` and `access.getCurrentToken() == action.token` before and after async store admission. Stale-text preservation is retained if the user types while save is pending. Direct parent completion toggles fail closed against shared store admission without mutating store or triggering sync. Uses `_stickyState.update` for observer emissions and `refreshDeadlines`; quick-add mutations and readiness collection use read-copy-write updates; controller concurrency edge cases (async readiness collector account-switch race and unsynchronized compound quick-add state updates) are tracked for Phase 06 remediation.

### Reported Automated Evidence
- **Reported Verification Totals**: QA report ([qa-261020-0200-phase-05-desktop-sticky-review-fixes.md](../plans/reports/qa-261020-0200-phase-05-desktop-sticky-review-fixes.md)) records 518 passing Gradle unit tests across 65 suites (100% PASS, 0 failures, 0 skipped, executed with `--dependency-verification=off`), 150 offline mock/fixture/vector checks, and 5 clean static audit checks.
- **Desktop Unit Tests** (`JAVA_HOME=... ./gradlew -PdesktopTarget=linux :desktopApp:test --dependency-verification=off --rerun-tasks`): 127/127 tests passed across 16 test suites, including 20 `DesktopTaskSurfacesTest` assertions verifying whole compact groups preservation, direct parent completion rejection, child desired-state completion and sync trigger, independent root quick-add, main editor isolation, in-flight text typing preservation, deadline formatting and clock refresh, startup controller order, and token fencing.
- **Shared JVM Tests** (`./gradlew :shared:jvmTest`): 341/341 tests passed across 41 test suites.
- **Android Unit Tests** (`./gradlew :androidApp:testDebugUnitTest`): 50/50 tests passed across 8 test suites.
- **Offline Fixture & Vector Runners**: 150 offline mock/vector/fixture/script checks passed cleanly: 11 strict-v3 canonical vector checks, 21 strict-v2 vector checks, 34 admin announcements smoke checks, 8 cutover rehearsal checks, 18 offline Hub live-fixture smoke checks (`scripts/hub-live-smoke.mjs --fixture-test`, offline fixture testing without live replica set), and 58 canonical Node.js script unit tests (24 `release-security-check.test.mjs`, 13 `bump-version.test.mjs`, 13 `hub-strict-upgrade.test.mjs`, 8 `debug-fault-proxy.test.mjs`).

### Code Review & Operational Caveats
- Code review Cycle 2 ([code-review-261020-0207-phase-05-desktop-sticky-cycle-2.md](../plans/reports/code-review-261020-0207-phase-05-desktop-sticky-cycle-2.md)) scored **8/10** with 0 critical issues.
- **Technical Acceptance Qualifier & Deferred Proof (Phase 06 Gate)**:
  - *Native Display & Hardware Acceptance Deferred*: Physical window rendering, DPI scaling (100%–200%), always-on-top window stacking, tray minimization/recovery, and screen-reader traversal (Orca/Narrator) on native Linux and Windows hosts remain held at Gate G2 and deferred to Phase 06 acceptance testing on live host sessions.
  - *Asynchronous Collector Account-Switch Race*: In `desktop-task-surfaces.kt`, observer cancellation upon `tokenChanged` in the asynchronous readiness collector can race with rapid account switching; consolidating observer lifecycle into `startObserving(token)` is tracked for Phase 06 remediation.
  - *State Transition Serialization*: Compound read-copy-write state updates across `refreshDeadlines`, observer emissions, and account transitions lack atomic synchronization; mutex or atomic update protection is tracked for Phase 06 remediation.

---
## [Parent/Child Tasks & Deadline Surfaces / Phase 04] - 2026-10-20: Android Grouped Widget & Deadline Labels

### Delivered
- **Pure projectWidgetItems Projection**: Added stateless widget item projection in `dev.tabbyrush.android.projectWidgetItems` (`task-widget-projection.kt`) transforming task sections via shared `TaskHierarchyProjector.projectHierarchy` with `TodoFilter.ALL`. Keeps whole compact groups intact under Incomplete (active parents display with all active and completed sibling children) and Completed sections, enforcing ancestor deletion dominance and effective parent completion without platform-specific hierarchy forks.
- **Absolute Own Deadlines & Overdue Styling**: Implemented per-row absolute deadline presentation using `TaskDeadlinePresentation.formatDeadline`. Formats absolute date (`"Due YYYY-MM-DD"`) and timed deadlines (`"Due YYYY-MM-DD HH:mm (captured zone)"`) strictly from each task's own stored deadline, with zero deadline inheritance between parent and child. Date-only reminder rule anchor (09:00 default) is never leaked as deadline time; tasks lacking deadlines omit the metadata row. Overdue active rows append `• Overdue` decoration in `GlanceTheme.colors.error` and `FontWeight.Bold` while keeping the absolute date visible; completed tasks never render overdue.
- **Adaptive Glance Row Layout & Accessible Touch Targets**: Replaced fixed 48dp single-line row with an adaptive Glance layout in `task-widget.kt`. Explicit 48×48dp CheckBox container `Box(contentAlignment = Alignment.Center)` and minimum 48dp `Spacer` provide layout bounds intended to satisfy Android >=48dp touch target guidelines (physical touch targets remain subject to on-device launcher verification). Receptive title/metadata `Column` declares `.fillMaxHeight()` with `Alignment.CenterVertically` for clickable navigation. Set deadline `maxLines = 3` to accommodate multi-line wrapped deadline and overdue strings on compact widths (truncation prevention under 200% font scaling remains subject to on-device verification). Indents child subtasks by 12dp (compact) or 20dp (expanded).
- **Read-Only Automatic Parent Progress & Token-Fenced Actions**: Parents with live children render read-only checkboxes (`onCheckedChange = null`) reflecting automatic progress (`"Subtasks: X/Y completed"`) with aggregated TalkBack content descriptions (`task_widget_parent_automatic_content_desc`), preventing direct group toggle. Child subtasks and standalone roots bind interactive callbacks fenced by `SurfaceAccountToken(accountKey, generation)`. Widget Add button routes to `createAddTaskIntent` creating independent root tasks only (child creation remains main-app-only). Stale parent completion callbacks are rejected by shared store admission.
- **Accessibility & String Localization**: Added dedicated accessibility strings in `task-widget-strings.xml` for child checkbox actions (`task_widget_child_checkbox_incomplete`, `task_widget_child_checkbox_completed`), subtask progress counters (`task_widget_subtasks_progress`), automatic progress content descriptions (`task_widget_parent_automatic_content_desc`), and localized overdue badges (`task_widget_overdue`, `task_widget_deadline_with_overdue`).

### Reported Automated Evidence
- **Reported Verification Totals**: QA report ([qa-261020-0053-phase-04-android-widget-deadline-labels.md](../plans/reports/qa-261020-0053-phase-04-android-widget-deadline-labels.md)) records 402 reported passing tests/checks across 3 automated test targets (100% PASS, 0 failures, 0 skipped, run with `--dependency-verification=off`).
- **TabbyRush Shared JVM Tests** (`JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`): 341/341 tests passed across 41 test suites.
- **Android Unit Tests** (`./gradlew :androidApp:testDebugUnitTest`): 50/50 tests passed across 8 test suites, including 18 `TaskWidgetModelTest` assertions verifying ancestor deletion dominance, mixed compact group preservation, raw completion disagreement, absolute deadline/overdue presentation, absence of deadline inheritance, automatic parent progress, and `SurfaceAccountToken` fence isolation.
- **Android Instrumentation Compilation** (`JAVA_HOME=... ./gradlew :androidApp:compileDebugAndroidTestKotlin --dependency-verification=off`): Build succeeded with 47 executed Gradle tasks; instrumented test sources compile clean (not counted as test assertions).
- **Node.js Strict-v3 Vector Runner** (`node scripts/strict-v3-vector-runner.mjs`): 11/11 vector checks passed (6 valid + 5 rejections; canonical fingerprint verified).

### Code Review & Operational Caveats
- Code review Cycle 3 ([code-reviewer-261020-0115-phase-04-android-widget-cycle3.md](../plans/reports/code-reviewer-261020-0115-phase-04-android-widget-cycle3.md)) scored **8.5/10** with 0 critical issues.
- **Technical Acceptance Qualifier & Deferred Proof (Phase 06 Gate)**:
  - *Physical Launcher Device Verification Deferred*: Physical connected device/emulator launcher verification (pinning widget, responsive scaling at 120×120, 240×120, 240×240, and 320×360dp, 200% font scaling, and TalkBack audio inspection) remains formally held at Gate G2 and deferred to the Phase 06 acceptance gate due to headless CI execution constraints.
  - *Overdue Boundary Refresh*: Overdue evaluation captures `nowEpochMs = System.currentTimeMillis()` at composition time; automatic midnight/boundary trigger refresh is tracked for Phase 06 integration.
  - *Open Safety Findings*: Open findings (delivery-drain timeout in `closeRuntimeInternal()`, swallowed widget cleanup errors in `task-widget-updater.kt`, 149-checksum verification baseline, and Glance provenance hold) remain active.

---
## [Parent/Child Tasks & Deadline Surfaces / Phase 03] - 2026-10-20: Shared Hierarchy Semantics & Main UI

### Delivered
- **TaskHierarchyProjector Pure Evaluator**: Added stateless hierarchy evaluator in `dev.tabbyrush.shared.contract.TaskHierarchyProjector` providing `projectHierarchy`, `computeCounts`, and `partitionCompactSections`. Enforces encounter ordering (`compareByDescending { it.createdAt }.thenByDescending { it.id }`), effective completion derivation (`children.all { it.completed }` for parents with live children, raw stored completion for childless roots and children), two-level constraint (`parentTaskId == null` roots), ancestor deletion dominance (suppressing children of deleted roots), context-only parent rows (`isContextOnly = true`, `depth = 0`) in `TodoFilter.ACTIVE` and `TodoFilter.COMPLETED` without sibling leakage, and compact whole-group partitioning (`partitionCompactSections` keeps mixed groups together in Active, and fully complete groups in Completed without splitting). Accurate metrics (`computeCounts`) count each unique real task once.
- **TaskDeadlinePresentation Pure Formatting**: Added stateless presentation formatter in `dev.tabbyrush.shared.contract.TaskDeadlinePresentation` formatting absolute metadata: `"Due YYYY-MM-DD"` or `"Due YYYY-MM-DD HH:mm (captured zone)"`. Derives overdue (`nowEpochMs >= overdueEpochMs && !effectiveCompleted`) supplementing the date label rather than replacing it. Completed tasks never render as overdue.
- **RoomAccountStore Atomic Group Deletion & Last-Child Preservation**: Added atomic group deletion and last-child preservation in `RoomAccountStore.deleteTodoLocally` under `withMutationLock`. Parent deletion with live children cancels fully-unsent groups atomically (`cancelUnsentGroupAtomic`) or issues transactional deletes across parent and children (`deleteGroupAtomic`) with distinct local revisions. Locally observed last-child deletion captures preceding parent effective completion in a single `@Transaction` (`deleteChildAndPreserveParentAtomic`) with intent mask `INTENT_LAST_CHILD_PRESERVATION`, restoring manual completion with the resultant raw value.
- **PersistedScheduleProjector Hierarchy & Preservation Scheduling Barrier**: Delivered draft-free projection in `PersistedScheduleProjector` from committed `server_base` and submitted `outbox` entries (excluding `editor_drafts`), running `TaskHierarchyProjector.projectHierarchy` over intermediate models to apply ancestor deletion dominance and effective completion. Generates client-local parent scheduling barrier (`hasUnresolvedPreservationBarrier = true`) for unaccepted preservation intents, surviving process restarts while retained (subsequent manual root re-edits currently drop the marker pending Phase 06 remediation).
- **Shared Compose UI Hierarchy Rendering, Read-Only Progress & Subtask Creation**: `TodoList` renders hierarchical items with `SpacingXLarge` start indentation for children (`depth == 1`). Roots with live children render disabled checkboxes with accessible state descriptions (`"Completed/Active (automatic: X/Y subtasks)"`) and visual subtask progress labels (`"Subtasks: X/Y completed"`). Roots on v3 accounts show `+ Add Subtask` button routing to `TodoEditor`, which presents a read-only parent context banner (`"Subtask of: <parentTitle>"`). Quick-create and compact-surface Add actions create roots only (compact lists still display existing child items within their group).

### Reported Automated Evidence
- **Reported Verification Totals**: QA report ([qa-261019-2350-phase-03-test-suite-validation.md](../plans/reports/qa-261019-2350-phase-03-test-suite-validation.md)) records 512 passing validations (100% PASS across 4 test targets, 0 failures, 0 skipped, executed with `--dependency-verification=off`).
- **TabbyRush Shared JVM Tests** (`JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`): 339/339 tests passed across 41 test suites, including `TaskHierarchyProjectorTest` (8/8), `TaskDeadlinePresentationTest` (3/3), `PersistedScheduleProjectionTest` (9/9), and `RoomAccountStoreTest` (35/35).
- **Desktop JVM/Linux Tests** (`./gradlew :desktopApp:test`): 119/119 tests passed across 10 test suites.
- **Android Unit Tests** (`./gradlew :androidApp:testDebugUnitTest`): 43/43 tests passed across 8 test suites.
- **Node.js Strict-v3 Vector Runner** (`node scripts/strict-v3-vector-runner.mjs`): 11/11 vector checks passed (6 valid + 5 rejections; canonical fingerprint verified).

### Code Review & Operational Caveats
- Code review Cycle 2 ([code-reviewer-261020-0030-phase-03-hierarchy-main-ui-cycle2.md](../plans/reports/code-reviewer-261020-0030-phase-03-hierarchy-main-ui-cycle2.md)) scored **7/10** with 0 critical issues; user approved with noted issues. Phase 03 shared hierarchy and main UI delivered; phase closure and native acceptance held.
- **Tracked Operational Caveats & Remaining Gates (Phase 06 Remediation)**:
  - *Mixed Group Deletion*: Deleting a group with a synced root and unsent child can strand outbox operations if the root delete is accepted first; leaf-row local cancellation tracked for Phase 06 remediation.
  - *Preservation Marker Retention*: Manual root re-edit coalescing into a pending preservation UPDATE drops the `LAST_CHILD_PRESERVATION` intent mask; barrier retention across edits tracked for Phase 06 remediation.
  - *Production Late-Child Cleanup & Unresolved UI*: The production data path lacks late-child delete cleanup and unresolved-child presentation; tombstone dominance suppresses them from UI/schedules, and durable cleanup is tracked for Phase 06.
  - *Conflict-Review Copy & Real-UI Smoke*: Explanatory conflict-review copy and real-UI multiplatform smoke testing remain open.
  - *Live Acceptance Gates*: Native surfaces (Android widget, desktop sticky) remain scheduled for Phases 04–05; live transition-recovery acceptance and live MongoDB replica-set staging cluster acceptance remain execution prerequisites tracked in Phase 06 rollout.

---
## [Parent/Child Tasks & Deadline Surfaces / Phase 02] - 2026-10-19: Room Schema 3 Migration & Versioned Sync Integration

### Delivered
- **Room Schema Version 3 Migration**: Added additive `MIGRATION_2_3` in `database-migrations.kt` registering migration to Room schema version 3 (`schemas/.../TabbyRushDatabase/3.json`). Added nullable `parentTaskId: String?` column across all four task-bearing tables (`server_base`, `editor_drafts`, `outbox`, and `scan_staging`), preserving existing tasks as root/parent-capable with null relationships without altering legacy wire payloads.
- **Confirmed-Root First-Dispatch Gating**: Added first-dispatch parent verification in `OutboxReconciliation` before initial child task freezing (serialized by coordinator mutex rather than inside SQLite freeze transaction); unfrozen children under unconfirmed parents remain pending and visible. Frozen `UNCERTAIN` operations bypass renewed parent-liveness gating to guarantee exact replay invariance.
- **Versioned v3 Sync Integration**: Integrated typed v3 push, pull, and bootstrap handling in `HubSyncClient`, `StagedFullScan`, and `AccountSyncCoordinator`. Updated conflict review and reapply logic to retain `parentTaskId` and select strict-v3 contracts.
- **Safe V2->V3 Intent Translation**: Delivered non-destructive translation in `ContractTransitionCoordinator` preserving all pending V2 temporal fields (`deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, and `scheduleToken`).

### Reported Automated Evidence
- **Reported Verification Totals**: QA report ([qa-261019-2139-phase-02-cycle-3-validation.md](../plans/reports/qa-261019-2139-phase-02-cycle-3-validation.md)) records 371 passing validations (100% PASS across 4 suites/runners, 0 failures, 0 skipped).
- **TabbyRush Shared JVM Tests** (`JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`): 318/318 tests passed across 39 test suites, including Room migration suites (`RoomMigrationV2ToV3Test`, `RoomMigrationV1ToV2Test`, `RoomAccountStoreTest`), sync suites (`ContractTransitionTest`, `AccountSyncCoordinatorTest`, `OutboxReconciliationTest`, `StagedFullScanTest`, `HubSyncClientTest`), and v3 vector suites (`StrictV3DigestVectorTest`).
- **Node.js Strict-v3 Vector Runner** (`node scripts/strict-v3-vector-runner.mjs`): 11/11 vector checks passed (6 valid + 5 rejections; canonical fingerprint verified).
- **Node.js Strict-v2 Cutover Rehearsal** (`node scripts/strict-v2-cutover-rehearsal.mjs --self-test`): 8/8 rehearsal checks passed.
- **Admin Announcements Smoke Runner** (`node scripts/admin-announcements-smoke.mjs --self-test`): 34/34 checks passed.

### Code Review & Operational Caveats
- Code review Cycle 3 ([code-reviewer-261019-2145-phase-02-cycle-3-review.md](../plans/reports/code-reviewer-261019-2145-phase-02-cycle-3-review.md)) scored **7/10**, with C1 closed and 0 critical issues open.
- **Tracked Operational Caveats & Remaining Gates**:
  - *H1-Residual (V2 Uncertain Op Fencing)*: In V2->V3 transition, non-translatable V2 uncertain/dispatched ops are fenced from V3 push, causing rows to stall silently without visible conflict state; this release-blocking functional defect requires explicit user-review conflict surfacing and dispatch fence tests tracked for Phase 06 remediation.
  - *Legacy Receipt Reconciliation Scope*: Glean Hub receipt models/services define v1/v2 structures, but the live receipts route currently processes strict-v1 payloads; v2->v3 legacy receipts remain tracked for subsequent phase alignment.
  - *Parent-Liveness Checking Atomicity*: Parent-liveness check is practically serialized by the coordinator mutex rather than inside SQLite atomic freeze transaction.
  - *Preservation-Intent Persistence*: Stored last-child preservation review provenance remains an open Phase 03 implementation prerequisite; Phase 03 contract-based development is unblocked.
  - *Live Acceptance Gates*: Live transition-recovery acceptance and live MongoDB replica set staging cluster acceptance remain execution prerequisites tracked in Phase 06 rollout.

---
## [Parent/Child Tasks & Deadline Surfaces / Phase 01] - 2026-10-19: Strict-v3 Contract Freeze & Hub Upgrade/Receipts Implementation

### Delivered
- **Strict-v3 Contract & Schema**: Frozen canonical 17-column schema (`integration/glean-hub/tabbyrush-schema-v3.json`) adding nullable `parentTaskId: string` to existing 16 strict-v2 columns. Canonical schema fingerprint frozen at `7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f`.
- **Preimage Encoding & Golden Vectors**: Defined 17-field canonical preimage format and SHA-256 digest computation in `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v3-contract.kt` and `strict-sync-v3-digest.kt`. Created golden test vectors (`integration/glean-hub/strict-v3-vectors.json`) with 6 valid scenarios (create root/child, update root/child, delete root/child) and 5 rejection rules (malformed JSON, self-parent cyclic reference, cross-user parent, depth > 2 hierarchy attempt, immutable link modification). Verified cross-language parity across Kotlin, Node.js, and Rust.
- **Node.js Vector Runners**: Added `scripts/strict-v3-vector-runner.mjs` verifying schema fingerprint, valid golden vectors, and rejection scenarios. Retained `scripts/strict-v2-vector-runner.mjs` regression verification for v1 and v2 fingerprints and vectors. Added `scripts/generate-strict-v3-vectors.mjs`.
- **Hub Strict Upgrade CLI**: Updated `scripts/hub-strict-upgrade.mjs` and `scripts/hub-schema-provision.mjs` to support `--target-version strict-v3` with auto-derived fingerprint (`7f033e7b...`), guarded transition state machine, token redaction in logs/errors, dry-run mode, and 17-column post-upgrade read-back assertions.
- **Glean Hub Rust Models & Services**: Added `strong_sync_v3.rs`, `canonical_v3_columns()`, `canonical_v3_schema_def()`, `MigrationState::ReadyV3`, `AppService::validate_strict_v3_schema`, `StrongSyncService` supporting `strict-v3` in `authorize_strict_access`, CAS mutation validation for root/child constraints, `StrictUpgradeService` supporting fenced `strict-v2` -> `strict-v3` migration with live row null-backfill, validation cursor, coordinated lock fencing, post-CAS recovery, and legacy receipt reconciliation (`strict-v1`, `strict-v2`).

### Reported Automated Evidence
- **Reported Verification Totals**: QA report ([qa-261019-1754-phase-01-freeze-v3-contract-hub-upgrade.md](../plans/reports/qa-261019-1754-phase-01-freeze-v3-contract-hub-upgrade.md)) records 404 passed verifications across 5 targets (372 test executions + 32 vector/rejection checks, 0 failures, 1 ignored live Mongo test).
- **TabbyRush Kotlin JVM Tests** (`JAVA_HOME=... ./gradlew :shared:jvmTest`): 38 test suites, 297/297 passed, 0 failed, including `StrictV3DigestVectorTest` (10/10), `StrictV2DigestVectorTest` (5/5), `StrictV2DigestExtendedTest` (6/6), `StrictDigestTest` (5/5). Verified under both `--dependency-verification=off` and `--dependency-verification=strict`.
- **Node.js Strict-v3 Vector Runner** (`node scripts/strict-v3-vector-runner.mjs`): 11/11 vector checks passed (6 valid + 5 rejections), canonical fingerprint confirmed.
- **Node.js Strict-v2 Vector Runner Regression** (`node scripts/strict-v2-vector-runner.mjs`): 21/21 vector checks passed (10 valid + 11 rejections), v1/v2 fingerprints confirmed.
- **Node.js CLI Upgrade Test Suite** (`node --test scripts/hub-strict-upgrade.test.mjs`): 13/13 passed, 0 failed, exercising option parsing, token redaction, 4xx fast-fail, dry-run mode, resume migration ID, and 17-column readback.
- **Glean Hub Rust Cargo Test Suite** (`cargo test --manifest-path .../Cargo.toml --locked`): 62/62 unit/integration tests passed, 0 failed, 1 ignored (`test_strong_sync_live_mongo_acceptance_gate` requiring live MongoDB).

### Code Review & Operational Caveats
- Code review Cycle 2 ([code-reviewer-261019-1847-phase-01-cycle-2-review.md](../plans/reports/code-reviewer-261019-1847-phase-01-cycle-2-review.md)) approved at **Score 7/10**, 0 critical issues open. Both Cycle 1 criticals (C1 `authorize_strict_access` rejecting v3, C2 CLI wrong target fingerprint) resolved and verified.
- **Open Operational Caveats & Remaining Gates**:
  - *Hub Recovery Dry-Run Side Effects*: In `strict_upgrade_service.rs`, recovery for an already-upgraded target invokes `release_fence` and updates migration records prior to the `dry_run` exit check; true non-mutating dry-run requires upstream fix before operational use.
  - *Hub Fingerprint Acceptance Scope*: `is_valid_schema_fingerprint` accepts frozen constants across apps; dynamic per-app verification recommended before multi-tenant production.
  - *Unverified Live Database & Client Integration*: Live MongoDB replica-set migration and service-level admission tests remain unexecuted (requires live MongoDB replica set; gated to Phase 06). Client Room database migration, sync coordinator integration, Compose hierarchy UI, and platform deadline surfaces remain scheduled for downstream phases (Phases 02–06).

---
## [1.0.17] - 2026-10-19: Windows MSI Packaging UTF-8 BOM Elimination

### Fixed
- **Windows MSI Packaging**: Normalized `TabbyRush.cfg` and `.env` in `scripts/build-windows-remote-msi.ps1` to strictly BOM-free UTF-8 (stripping leading `0xEF 0xBB 0xBF` if present), resolving an issue where the native `jpackage` launcher failed to initialize the bundled JVM runtime.
- **Environment Variable Parsing**: Added BOM stripping in `dev.tabbyrush.desktop.DotEnvLoader.parse()` (`rawLine.removePrefix("\uFEFF").trim()`) so UTF-8 BOMs on the first line of `.env` files are transparently ignored.
- **Packaging Smoke Audit**: Enhanced `scripts/windows-package-smoke.ps1` to audit `TabbyRush.cfg` and all present `.env` files, failing closed if any UTF-8 BOM is detected.
- **Version Bump**: Bumped `appVersion` to `1.0.17` and `appVersionCode` to `18`.

### Verification
- `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/dot-env-loader-test.kt`: 4/4 tests pass including BOM stripping assertions.

---

## [1.0.16] - 2026-10-18: Release Security Check Compiled Resource Support

### Fixed
- **Release Security Audit**: Enhanced `scripts/release-security-check.mjs` to accept both raw `@xml/task-widget-info` / `@xml/task_widget_info` and compiled `@ref/0x[0-9a-fA-F]+` resource identifiers produced by `apkanalyzer manifest print` for widget provider metadata.
- **Version Bump**: Bumped `appVersion` to `1.0.16` and `appVersionCode` to `17`.

### Verification
- `scripts/release-security-check.test.mjs`: Added tests verifying that compiled `@ref/0x...` IDs pass while bare hex values without `@ref/` or invalid XML paths fail closed (24/24 tests pass).

---

## [1.0.15] - 2026-10-18: AndroidX System Services & Receivers Security Exemption

### Fixed
- **Release Security Audit**: Updated `scripts/release-security-check.mjs` exported-component validator to permit AndroidX system receivers protected by `android.permission.DUMP` (`androidx.work.impl.diagnostics.DiagnosticsReceiver`, `androidx.profileinstaller.ProfileInstallReceiver`) and system services protected by appropriate permissions (`androidx.glance.appwidget.GlanceRemoteViewsService` with `BIND_REMOTEVIEWS`, `androidx.work.impl.background.systemjob.SystemJobService` with `BIND_JOB_SERVICE`).
- **Version Bump**: Bumped `appVersion` to `1.0.15` and `appVersionCode` to `16`.

### Verification
- `scripts/release-security-check.test.mjs`: 24/24 tests pass.

---

## [1.0.14] - 2026-10-18: Android Lint Vital Release Suppression

### Fixed
- **Android Lint**: Added `disable += "InvalidFragmentVersionForActivityResult"` in `androidApp/build.gradle.kts` `lint` configuration to permit release compilation (`lintVitalRelease`) with Compose and Activity dependencies.
- **Version Bump**: Bumped `appVersion` to `1.0.14` and `appVersionCode` to `15`.

---

## [1.0.13] - 2026-10-18: CI Release Workflow Step Syntax & Variable Support

### Fixed
- **CI Release Workflow**: Fixed GitHub Actions step syntax in `.github/workflows/release.yml`. Allowed configuring Hub origin and API key via either GitHub repository secrets or repository variables (`vars.APPROVED_HUB_ORIGIN`, `vars.TABBYRUSH_API_KEY`).
- **Version Bump**: Bumped `appVersion` to `1.0.13` and `appVersionCode` to `14`.

---

## [1.0.12] - 2026-10-18: App Icon Brand Refresh from Vector SVG

### Added
- **Brand Assets**: Refreshed multiplatform app icons from canonical vector SVG: Android adaptive foreground, mipmaps (mdpi to xxxhdpi), desktop runtime PNG (256px), Windows multi-resolution ICO, and system tray artwork.
- **Asset Generation**: Updated `scripts/generate-app-assets.py` to deterministically regenerate all platform icons from vector source.
- **Version Bump**: Bumped `appVersion` to `1.0.12` and `appVersionCode` to `13`.

---
## [Server Admin Announcements / Phases 01–12] - 2026-10-18: Cross-System Delivery & Acceptance

### Delivered
- **Backend & Storage**: Canonical MongoDB collections (`admin_announcements`, `admin_announcement_inbox`, `admin_announcement_dispatches`, `notification_install_bindings`) separate from task sync. 90-day retention with BSON DateTime TTL indexes in MongoDB (`expiresAt = createdAt + 90 days`); MongoDB background TTL deletion is asynchronous, while application query and rendering filters strictly exclude records when `expiresAt <= serverNow` (only unexpired records are queryable or renderable); reading never extends expiry.
- **Android Push Transport**: FCM HTTP v1 generic notifications for Android with channel `tabbyrush.remote.announcements` and system tag `remote:<messageId>`, maintaining announcement-content isolation from push providers (fixed generic copy "You have a new announcement" and opaque IDs). 32-byte install proof encrypted in app-private storage via Android Keystore-backed AES-256-GCM key (`tabbyrush_remote_install_v1`), SHA-256 hashed on Hub, with monotonic numeric generation CAS fencing.
- **Desktop Streaming Broker**: Streaming SSE broker with `streamEpoch`, monotonic `acceptanceOrdinal`, and ready baseline (`event: ready`). Hints arriving after baseline hydration trigger authenticated detail fetching. Desktop displays at most one generic system alert per active connection and message hint; Android suppresses SSE alerts.
- **Client In-Memory Isolation**: TabbyRush clients store announcements exclusively in memory (sliding window max 200 items, dedup capacity 2048) managed by `RemoteInboxController`. Zero Room database migrations, persistent offline content, or read outbox; process termination purges cached items.
- **Admin Web Console**: React admin console (`AnnouncementComposer`, `AnnouncementHistory`) with scalar/byte counters, preview digest confirmation gating, audience mode selection (`selected` up to 500, `all`, `admins`), and idempotency replay protection.
- **Acceptance & Tooling**: Standalone Node.js smoke runner `scripts/admin-announcements-smoke.mjs` verifying contract-v1.json, 13 golden vectors, idempotency, monotonic readAt timestamps, and install proof/fencing via in-process mock server (`--self-test`, 21/21 assertions PASS); Playwright browser suite `scripts/admin-announcements-browser.spec.ts`; cross-system acceptance matrix `docs/evidence/admin-announcements-acceptance.md`.

### Verification & Acceptance Status
- **Automated Verifications**: QA regression report (`qa-261018-1548-c1-c6-regression-verification.md`) records 566 verifications passing (548 test executions across 8 targets + 18 vector/static checks); Cycle 2 code review approved at Score 8/10 with 0 critical issues.
- **Restricted Pilot Gate (Android/Linux/Web Admin)**: APPROVED as recorded in acceptance evidence. Common safety, server durability, Android FCM runtime, Linux D-Bus actions adapter, and Admin Web console verified for internal cohort pilot (selected mode <= 500 recipients).
- **Full Rollout Gate (Windows Desktop Runtime)**: HELD at Gate G5 on Linux build host; Windows C ABI v1 `tabbyrush-toast.dll` and WiX3 MSI packaging implemented in source; full rollout requires clean-host Windows proof.
- **Inherited Release Holds**: Release holds G2/G3/G6/G7, 149-checksum baseline, and Glance provenance remain open.
---
## [TabbyRush Rebrand & Mascot App Icon] - 2026-10-17: TabbyRush Identity & Mascot App Icon

### Delivered
- Refactored application branding from "TabbyRush" to "TabbyRush" across Android launcher labels (`android:label="TabbyRush"`), Compose string resources (`app_name="TabbyRush"`), Glance widget titles (`TabbyRush Tasks`), notification channels (`TabbyRush Task Reminders`), desktop window titles (`TabbyRush` / `TabbyRush - Tasks`), tray tooltips (`TabbyRush`), and desktop packaging metadata (`TabbyRush` on Windows / `tabbyrush` on Linux).
- Refactored environment configuration to `TABBYRUSH_API_KEY` across Android Gradle build config, Desktop launcher, and tooling scripts, removing legacy `TABBYRUSH_API_KEY`.
- Refactored target application ID to `tabby-rush` (`appId = "tabby-rush"`, CLI `--app-id tabby-rush`, `TABBYRUSH_APP_ID`), and documented MongoDB target collection as `appTabbyRush`.
- Generated official TabbyRush app icon across all platforms using user-provided vector SVG and preview PNG (`docs/assets/tabbyrush-source.png` and `docs/assets/tabbyrush-vector.svg`):
  - Android Adaptive Icon: 432×432 px transparent foreground (`ic_launcher_foreground.png`) with enlarged leaping mascot and milestone roadmap (238×166 px) centered strictly within the 66dp safe circle (radius 128.95 px / 32.24 dp from center, 0 pixels clipped, 3.05 px / 0.76 dp safety margin), paired with solid `#FFFFFF` vector background (`ic_launcher_background.xml`).
  - Desktop Runtime Icon: 256×256 px PNG (`tabbyrush-icon.png`) on white rounded squircle with `#004646` primary teal border, 18px margin (on 512×512), enlarged mascot (92% inner width), and drop shadow.
  - Windows Multi-Resolution ICO: 7 embedded 32-bit RGBA frames (16, 24, 32, 48, 64, 128, 256 px) in `tabbyrush.ico`.
  - System Tray: 32×32 px high-contrast leaping mascot and roadmap artwork (`task-surface-tray.png`) for Linux and Windows taskbars.
  - Master SVG: 512×512 pure vector SVG with semantic layers (`background-desktop`, `mascot-tabbyrush` with 12 vector paths) in `docs/assets/tabbyrush-app-icon.svg`.
  - Android Legacy Mipmaps: squircle and circular branded icons (`ic_launcher.png`, `ic_launcher_round.png`) across mdpi..xxxhdpi with `#004646` borders and margins.
- Updated deterministic asset generation script: `scripts/generate-app-assets.py`.

### Verification
- Automated Gradle test suites pass: `:desktopApp:test` (103/103 tests pass), `:androidApp:testDebugUnitTest` (36/36 tests pass), and `:shared:jvmTest` (239/239 tests pass).
- Hub strict-v1 live smoke fixture tests pass: `node scripts/hub-live-smoke.mjs --fixture-test` (18/18 checks pass).
- Release security checks pass: `node scripts/release-security-check.mjs --diagnose-source` (CLEAN) and `node --test scripts/release-security-check.test.mjs` (12/12 tests pass).

---
## [App Icon Design & Platform Delivery] - 2026-10-17: Canine & Task Completion App Icon

### Delivered
- Designed official TabbyRush app icon based on the "Companion Check" concept, unifying canine silhouette with a task-completion badge across brand palette tokens (`TabbyRushPrimary #004646`, `TabbyRushPrimaryDeep #003838`, `TabbyRushMint #27C4B5`, `TabbyRushCream #F7F4ED`, `TabbyRushWhite #FFFFFF`, `TabbyRushInk #0F172A`).
- Created canonical master vector graphic `docs/assets/tabbyrush-app-icon.svg` (512×512 SVG).
- Created Android adaptive icon layers (`androidApp/src/main/res/drawable/ic_launcher_background.xml` and `ic_launcher_foreground.xml`) and adaptive mipmaps (`androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`) adhering strictly to the 66 dp inner safe-zone circle constraint on the 108×108 dp viewport (zero pixels clipped under circular or squircle masks).
- Updated `androidApp/src/main/AndroidManifest.xml` to declare `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.
- Generated 256×256 desktop runtime icon `desktopApp/src/main/resources/tabbyrush-icon.png` and updated `MainWindow` and `StickyWindow` callsites in `main.kt` to bind the application icon.
- Generated 32×32 branded system tray icons for Linux and Windows (`task-surface-tray.png`) replacing generic indigo checkmark placeholder.
- Generated multi-resolution Windows packaging icon `desktopApp/src/windowsMain/resources/tabbyrush.ico` (embedding 16, 24, 32, 48, 64, 128, and 256px 32-bit RGBA frames) and configured packaging in `desktopApp/build.gradle.kts`.
- Added unit and asset decodability tests in `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/app-icon-assets-test.kt` and `androidApp/src/test/kotlin/dev/tabbyrush/android/app-icon-resources-test.kt`.

### Verification
- Automated unit test suites pass: `:desktopApp:test` (103 tests pass, including `AppIconAssetsTest`), `:androidApp:testDebugUnitTest` (36 tests pass, including `AppIconResourcesTest`), and `:shared:jvmTest` (239 tests pass freshly executed with `--rerun-tasks` and `--dependency-verification=off`).
- Android resource linking and compilation verified via `processDebugResources` and `mergeDebugResources`.
- Static release security auditor clean: `node scripts/release-security-check.mjs --diagnose-source` and Node test suite (12/12 passing).
---
## [Date & Time Picker Migration] - 2026-10-17: Calendar and Clock Pickers (Delivered; Technical Acceptance Conditional)

### Delivered
- Replaced manual date/time typing for task deadline date, deadline time, date-only reminder anchor, and settings default anchor with shared Material 3 calendar and clock controls.
- Added shared `DateTimePickerButton`, `TabbyRushDatePickerDialog`, `TabbyRushTimePickerDialog`, and `DateTimePickerFormats`; DatePicker mode toggle is disabled, and the clock dial has no text-input mode. Date/time fields have no typing path.
- DatePicker values normalize through UTC midnight; DatePicker is configured for years `0..9999`, and output remains canonical `YYYY-MM-DD` and `HH:mm`. Existing timezone/DST resolver behavior remains intact.

### Reported Verification & Acceptance
- Automated unit test suites pass (372/372 tests, with `:shared:jvmTest` freshly executed and passing 239/239 post-`yearRange = 0..9999` change; `:desktopApp:test` and `:androidApp:testDebugUnitTest` passing; Android `assembleDebug` passing; 32/32 Node security/smoke checks passing).
- This documentation pass did not rerun those checks. Rendered Compose interaction tests and physical device/accessibility acceptance remain pending, so technical acceptance is conditional and the relevant gate stays open.

---
## [Sticky Task Surfaces / Phase 07] - 2026-10-17: Android/Windows Acceptance & Shared Documentation (Delivered; Native Gate G2 Conditionally Held)

### Delivered
- Updated shared project documentation and added Android/Windows acceptance summaries and a Phase 07 evidence report.
- Recorded 6h / 12h credited for Phase 07; project progress is 118h / 132h (89.4%).
- Four PNGs in `docs/evidence/` are visual design baselines/design mockups only, not screenshots or runtime OS captures.

### Reported Automated Evidence
- The Phase 07 report records 364 Gradle tests passing (231 shared, 99 desktop, 34 Android), 12 Node auditor tests passing, six source diagnostics clean, 18 Hub fixture checks passing, and eight strict-v2 rehearsal checks passing.
- The report records Android `assembleDebug` and Windows distributable builds as successful. Gradle commands used `--dependency-verification=off`; this is recorded evidence, not a fresh rerun for this changelog.

### Preserved Holds & Safety Disclosures
- Android and Windows native interaction acceptance remains conditionally held at Gate G2. The native WinRT/COM toast bridge remains a candidate stub; installed MSI system acceptance is also held.
- The 149 unmatched classpath checksums and the separate Glance provenance hold remain unresolved. Linux session/tray acceptance is pending Phase 08. Production release gates G3, G6, and G7 remain open.
- Android `TabbyRushApplication.closeRuntimeInternal()` catches and logs a delivery-gate drain timeout, then returns before observer-job cancellation or Room closure. `closeActiveRuntime()` nevertheless clears `activeRuntime`, leaving jobs active and the Room database open. This remains an open safety finding.
- Glance cleanup error swallowing remains an open safety finding; no out-of-boundary code changes are claimed.

---

## [Sticky Task Surfaces / Phases 01–06] - 2026-10-16: Android Glance, Quiet Deadline Notification & Desktop Sticky Window (Delivered; Cycle 2 Conditional Approval)

### Delivered
- Completed shared account-token admission, task observation, desired-state completion mutation, and surface projections; connected them to the Android Glance task widget and opt-in quiet deadline notification.
- Delivered the compact Compose Multiplatform desktop sticky window with tray controls when supported by the host session. Android and desktop preferences remain local presentation/notification state, outside task synchronization.
- Integrated account lifecycle cleanup for task-surface notifications, boundary work, widget content, Glance sessions, and Android surface preferences.

### Review & Reported Verification
- Cycle 2 review scored **8.8/10 (Conditional Pass)** with 0 critical issues and 3 warnings. It records 363/363 Gradle tests (231 shared JVM, 98 desktop, 34 Android unit), 12/12 Node security auditor tests, 6/6 diagnostic source security checks, 18/18 Hub live-fixture smoke checks, and 8/8 strict-v2 rehearsal checks. These are report-attributed results, not fresh runs for this changelog entry (`plans/reports/code-review-261016-2157-sticky-task-surfaces-cycle-2.md`).
- Cycle 2 review warnings include Android `closeRuntimeInternal()` catching/logging a drain timeout and returning before observer-job cancellation or Room closure while `closeActiveRuntime()` clears `activeRuntime`; Glance blanking and session-close failures can also leave OS-retained content.

### Acceptance & Release Holds
- At the time of this 2026-10-16 entry, Phase 07 Android/Windows integration was ready for acceptance recording, not claimed as native acceptance passed. Current Phase 07 status is recorded above; Linux acceptance remains pending a real session in Phase 08.
- The 149-checksum dependency-verification hold and Glance provenance gap remain. Historical release holds G2/G3/G6/G7 remain unchanged.

---

## [Sticky Task Surfaces / Phase 01] - 2026-10-16: Shared Foundation & Contracts (Delivered; Conditional Handoff Authorized; Dependency Provenance Pending)

*Note: Separate initiative from the Task Deadlines, Reminders & Strict Upgrades plan.*

### Delivered
- Added shared authoritative task-surface foundations: `AccountStore.observeTodos()`, desired-state `setCompletedLocally`, access/readiness/section contracts, and deadline/partition projections.
- Added Android task-surface host contracts and desktop sticky task action/state contracts.
- Selected Glance 1.2.0 and delivered Android dependency/build and lockfile foundations.
- Resolved the Cycle 2 server-tombstone/local-draft-or-outbox overlay finding. `setCompletedLocally` now returns `Missing` for authoritative tombstones; residual-draft and residual-outbox SQLite regressions pass.

### QA & Review
- QA reports 307/307 Gradle tests passing (231 shared JVM, 64 desktop, 12 Android unit), plus 18/18 offline fixture smoke checks, 6/6 source-security diagnostics, and 8/8 strict-v2 cutover self-test checks.
- Cycle 3 code review scored **9.4/10 (Conditional Pass — Terminal Handoff Authorized; External Provenance Gate Pending)** with no critical issues identified.
- Terminal conditional handoff unblocks Phases 02, 03, and 04; their leaf implementations and native acceptance remain outstanding.

### Open Gates
- Glance artifact provenance is not verified in `gradle/verification-metadata.xml`; Gradle runs used `--dependency-verification=off`. Phase 01 evidence reports **149 unmatched root classpath checksums** ([foundation evidence](../plans/261016-1409-sticky-task-surfaces/reports/phase-01-foundation-evidence.md)).
- Native widget, notification, desktop window/tray, and Linux-session proof remain downstream acceptance requirements; no runtime/native success is inferred from shared tests.

---

## [Phase 12] - 2026-10-16: Coordinated Cutover Runbook, Rehearsal Tooling & Release Documentation (Runbook & Tooling Delivered; Live Production Cutover Blocked Pending Gates G2, G3, G6, G7)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative.*

### Added
- Authoritative Strict-v2 Coordinated Cutover & Recovery Runbook (`docs/strict-v2-cutover-runbook.md`):
  - Complete operational runbook detailing exact deployment ordering: (1) all-node Hub backend binary deployment, (2) Room v2 TabbyRush client distribution, (3) non-mutating preflight dry-run, (4) controlled in-place upgrade via `hub-strict-upgrade.mjs` or Web Admin UI with durable migration UUID, (5) mandatory post-cutover read-back assertion of all 16 columns and canonical v2 fingerprint, and (6) client replay and transition.
  - State-specific incident decision matrix covering pre-fence rejection, orphan permit stalls (keep fenced, escalate), interrupted batch (idempotent resume with same migration ID from checkpoint), lost publish response (read-back verify), preactivation rollback under fence (strip added fields only), and post-v2 forward repair rule (never restore v1 backup or downgrade binaries once v2 is published).
  - Emergency disaster recovery quarantine: explicitly quarantines `scripts/hub-strict-recovery.js` as an emergency cold restore tool for `strict-v1` only, forbidding its use for v2 upgrades or rollbacks.
- Strict-v2 Operator Tooling Rehearsal Suite (`scripts/strict-v2-cutover-rehearsal.mjs`):
  - Dedicated executable rehearsal and self-test harness verifying CLI parameter checking, missing token fail-fast, sensitive token redaction across stdout/stderr, non-mutating preflight dry-run, interrupted POST recovery, status-only query without mutation, and final schema read-back assertion.
  - 7/7 checks PASS in automated tooling self-test (`node scripts/strict-v2-cutover-rehearsal.mjs --self-test`).
  - Includes gated staging rehearsal mode (`--staging-origin` with `--confirm-disposable-staging`).

### Changed
- Reconciled operational and acceptance documentation:
  - `docs/deployment-guide.md`: Added Section 9 documenting strict-v2 deployment ordering, operator upgrade commands, and dual-auth app-scoped requirements.
  - `docs/security-and-recovery.md`: Added Section 4 delineating feature migration in-place transform from emergency fresh-epoch disaster recovery, documenting recovery rules for all migration states.
  - `docs/release-acceptance.md`: Added Gate G7 (Coordinated Cutover, Recovery Runbook & Release Gate) to formal operational gates and included rehearsal self-test suite (404 total verification targets across 15 groups: 400 automated checks with 399 passed, 0 failures, 1 ignored live Mongo gate; plus 4 build, lint, and packaging targets).
  - `docs/hub-schema-integration.md`: Documented Phase 12 cutover runbook and rehearsal tooling.
  - `docs/sync-acceptance.md`: Added strict-v2 migration and replay acceptance section.
  - `docs/android-acceptance.md`, `docs/linux-acceptance.md`, `docs/windows-acceptance.md`: Updated Gate G2 and cutover release status.
  - `docs/system-architecture.md`: Added Section 6.13 covering Phase 12 architecture, runbook, and rehearsal tooling.
  - `README.md`: Added rehearsal script and cutover runbook references.
  - Glean Hub `docs/tabbyrush-strict-sync.md`: Documented Phase 12 cutover runbook and corrected `--status-only` distinction.

### Gate & Release Status
- Phase 12 runbook, rehearsal tooling, and documentation reconciliation delivered (12h credited, completing all 236h of planned implementation effort).
- Live production cutover (Gate G7) remains formally **BLOCKED** pending:
  1. Gate G3 multi-instance MongoDB replica set migration execution and resolution of three Phase 03 code findings (permit release duplicate filter, `permitCount` backfill, and batch/checkpoint write atomicity).
  2. Gate G2 physical native hardware delivery (connected Android Doze/reboot, Linux desktop graphical bubble, Windows native WinRT/COM toast bridge).
  3. Gate G6 three-device concurrent live sync proof between Android, Linux, and Windows.
  4. Strict supply-chain dependency verification metadata regeneration.

---
## [Phase 11] - 2026-10-16: Adversarial Boundary Regressions, SQLite Crash Invariance & Platform Acceptance (Automated Regressions Approved; Live Platform & Cluster Acceptance Held; Code Review Score 8.6/10)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative.*

### Added
- Adversarial scheduler boundary regression suite (`AdversarialSchedulerBoundaryTest.kt` in `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/`):
  - Verified repetition limit bounds (1..10, min interval 15m, non-repeating interval null).
  - Verified checked 64-bit integer arithmetic overflow rejection (`safeMultiply`, `safeSubtract`, `safeAdd`).
  - Verified date-only 09:00 morning non-overdue calculation via production `DeadlineCalculatorHelpers.validateAndComputeDeadline`.
  - Verified exact 5-minute grace window evaluation: 300,000ms passes, 300,001ms skips without catch-up burst or retroactive delivery.
  - Verified editor draft isolation: drafts in `editor_drafts` never project into active schedules.
  - Verified cancellation barriers: completed, deleted, and disabled tasks immediately clear projected schedules.
  - Verified schedule token preservation across non-temporal edits vs advancement on reminder updates.
- SQLite crash boundary and frozen retry persistence suite (`AdversarialSyncSqliteTest.kt` in `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/`):
  - Real disk-backed SQLite database close and reopen cycles verifying bit-for-bit retention of all 12 temporal fields and schedule tokens.
  - Frozen retry invariance: network timeouts replay nonces idempotently without duplicate commits.
  - Cross-contract intent rebase: legacy v1 title-only modifications rebase over remote v2 deadlines without field erasure.
  - Conflict lock durability: `UNKNOWN_SUPERSEDED` conflict locks survive database restarts.
- Debug fault-injection proxy (`scripts/debug-fault-proxy.mjs` and `scripts/debug-fault-proxy.test.mjs`):
  - Loopback proxy simulating dropped push and legacy-receipt responses via client socket aborts, upstream 502/504 errors, sensitive URL path redaction on 403 rejections, and strict allowlist enforcement (8/8 tests PASS).

### Changed
- `PersistedScheduleProjector`:
  - Fixed `INTENT_REMINDER` updates to assign `scheduleToken = op.scheduleToken` when `deadline != null && op.scheduleToken != null`, eliminating scheduler desynchronization on reminder-only modifications.
- Temporal Fixture Offsets:
  - Corrected America/New_York offsets for 2026-11-01 15:00 from `-14400L` (EDT) to `-18000L` (EST) across adversarial test suites to align with the 02:00 DST fallback.

### Verification and Delivery Status
- QA test execution report (`plans/reports/qa-261015-2330-phase-11-validation.md`) records 395 total automated checks/targets across 14 verification groups (394 PASS, 0 failures, 1 ignored live Mongo gate).
- Code review report (`plans/reports/code-review-261015-2345-phase-11-adversarial-acceptance.md`) approved at **8.6 / 10** for automated regressions.
- Gate G2 physical native delivery (connected Android Doze/reboot, Linux desktop notification bubble, Windows WinRT toast bridge candidate stub), Gate G3 live cluster execution (multi-node MongoDB replica set live migration and 3 open Phase 03 backend findings), and Gate G6 full platform acceptance remain formally held pending physical testbeds.

---
## [Phase 10] - 2026-10-15: Shared Deadline Editor, Task List and Local Settings (Implementation Delivered; Platform Acceptance & Windows Bridge Pending Phase 11; Code Review Score 9.2/10 Approved)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative; distinct from the historical 2026-10-14 Phase 10 Windows desktop MSI release.*

### Added
- Shared Compose Multiplatform UI components for task deadlines and reminders:
  - `dev.tabbyrush.shared.ui.DeadlineCalculatorHelpers`: Pure immutable calculation engine (`validateAndComputeDeadline`, `computeNextCalendarDayStartEpochMs`, `computeCalendarDayStartEpochMs`, `formatOffset`). Resolves local dates and times via `StrictTimeZoneResolver`. Explicitly separates DST gaps (non-existent local times rejected with validation error) from DST overlaps (ambiguous times resolved via explicit offset selection). Defensive parsing catches `IllegalArgumentException` on impossible calendar dates (Feb 30/31, April 31, non-leap Feb 29) to prevent Compose recomposition crashes.
  - `dev.tabbyrush.shared.ui.DeadlineEditorSection`: Shared Compose UI section providing date input, time toggle, explicit timezone and offset disambiguation filter chips, reminder rule enablement, lead minutes picker, repeat interval and count bounds (1..10, count > 1 requires interval >= 15m, non-repeating interval null, last occurrence <= anchor), and live calculated reminder opportunity preview.
  - `dev.tabbyrush.shared.ui.ReminderSettingsDialog`: Modal settings dialog displaying platform reminder capabilities card (`Exact`, `BestEffort`, `NotificationDenied`, `ChannelDisabled`), default reminder rule toggles, lead minutes, repeat count/interval, and date-only default anchor time picker (09:00 default).
- UI state and actions in `:shared`:
  - `EditorState`: Immutable state container capturing task identity, title, deadline date/time/zone/offset, reminder rule, original schedule token, and validation error messages.
  - `ReminderSettingsState`: Immutable state container for local reminder defaults, dialog visibility, and platform capabilities.
  - `TodoAction`: Added `UpdateEditorState`, settings actions (`OpenReminderSettings`, `DismissReminderSettings`, `SaveReminderSettingsDefaults`, `RequestReminderPermission`, `OpenPlatformReminderSettings`), and periodic `ClockTick` action.
- Common Compose string resources in `shared/src/commonMain/composeResources/values/strings.xml` for deadline inputs, timezone selectors, reminder intervals, overdue labels, and settings descriptions.
- Automated test suites:
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt`: 6 new tests added in Phase 10 QA (`testDeadlineValidationValidTimedAndDateOnly`, `testDeadlineValidationDstGapAndOverlap`, `testDeadlineValidationBoundsAndLastOccurrenceLimit`, `testOverdueLogicDateOnlyVsTimedAndCompleted`, `testReminderSettingsStateDefaultsAndCapabilities`, `testConflictReviewDetailedParameters`) plus 1 regression test added during code review (`testDeadlineValidationImpossibleCalendarDates`), totaling 15 tests.
  - `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt`: Added `testClearingDeadlineToServerBaseDoesNotResurrect` verifying that clearing deadlines against a populated server base does not resurrect deleted schedules.

### Changed
- `RoomAccountStore` in `:shared`:
  - Refactored `resolveTemporalProjection` hierarchy: evaluates local drafts and outbox mutations as a complete unit. Explicit null deadline fields in local state are treated as an intentional clearing of the deadline rather than falling back field-by-field to `server_base` (verified by `testClearingDeadlineToServerBaseDoesNotResurrect`).
  - Enforced `hasScheduleSemanticChange()`: editing task title or description preserves the existing `scheduleToken`; editing deadline date, time, zone, offset, or reminder rule generates a new UUID `scheduleToken`. (Task completion acts as an independent cancellation barrier in `PersistedScheduleProjector`).
  - Added `isAccountV2()` guard: verifies active account has completed transition to `strict-v2`.
- `TodoEditor` in `:shared`:
  - Integrated `DeadlineEditorSection`, submit validation, and strict-v2 upgrade requirement guard (`isUpgradeBlocked` disables submit with Hub admin upgrade message on strict-v1 accounts while permitting title and completion editing).
- `TodoList` and `TodoRow` in `:shared`:
  - Added date-only and timed deadline badges.
  - Overdue status is conveyed via non-color indicators: explicit "Overdue" text badge, distinct background styling, and accessibility `contentDescription = "Task is overdue"`.
  - Date-only tasks at anchor 09:00 do not become overdue during that morning; overdue boundary is strictly the start of the next calendar day in the captured timezone. Completed tasks never render as overdue.
  - Evaluates overdue status against periodic 15s `ClockTick` events without SQLite mutations or network sync.
- `ConflictReview` in `:shared`:
  - Extended comparison card displaying full side-by-side comparison of local vs remote deadline date, time, zone, offset, reminder rule parameters, and schedule-token preview.
- `SessionViewModel` in `:androidApp`:
  - Routed notification task selection through `openEdit(task)` to fully hydrate deadline and reminder fields from stored snapshot.
  - Added reminder settings dialog flow and 15s `ClockTick` ticker flow.
- `DesktopController` / `main.kt` in `:desktopApp`:
  - Added reminder settings actions, submit validation, and 15s `ClockTick` ticker flow.

### Critical Remediations Applied During Code Review
1. **[CRITICAL / DATA LOSS] Android Notification Selection Bypassed Deadline Hydration**:
   - `dispatchTaskSelection(accountKey, taskId)` previously constructed an unhydrated `EditorState(id = task.id, rawTitle = task.title)` with default null deadlines. Opening a task from an OS notification and saving title changes wiped all scheduled deadlines and alarms.
   - Remediated by changing `dispatchTaskSelection` to a suspend function routing through `openEdit(task)`, which fully populates temporal fields from the stored snapshot before opening the editor.
2. **[HIGH / RUNTIME CRASH] Impossible Calendar Dates Crashed Compose Recomposition**:
   - Typing invalid calendar days (e.g. Feb 30/31, April 31, non-leap Feb 29) passed `DATE_REGEX` but caused `LocalDate.parse()` to throw uncaught `IllegalArgumentException` during Compose `remember` recomposition in `TodoEditor`.
   - Remediated by wrapping date/time operations in try-catch in `DeadlineCalculatorHelpers.kt` returning validation errors. Added regression test `testDeadlineValidationImpossibleCalendarDates`.

### Verification and Delivery Status
- QA test execution report (`plans/reports/qa-261015-2245-phase-10-validation.md`) records 306 automated test/check assertions across 7 test suites, plus 2 packaging assembly builds (`:desktopApp:createDistributable` standalone bundle and `:androidApp:assembleDebug` APK build), totaling 308 checks (189 shared JVM tests across 26 classes, 49 desktop tests across 10 classes, 12 Android unit tests across 4 classes, 23 strict-v2 vectors/fingerprints, 18 Hub offline fixture smoke checks, 6 static release security checks, 9 Hub strict upgrade CLI tests).
- Code review report (`plans/reports/code-review-261015-2300-phase-10-compose-deadline-editor-list.md`) approved at **9.2 / 10**, adding 1 regression test (`testDeadlineValidationImpossibleCalendarDates`) in `TodoSemanticsTest` to bring shared JVM tests to 190 and total passing checks to 309 when executed.
- Gate G2 / Platform Acceptance: Compose UI, contracts, and platform controller integrations delivered in source and verified via unit tests; connected physical device / emulator execution, TalkBack/Orca accessibility verification, and live notification delivery remain scheduled for Phase 11 platform acceptance.

---
## [Phase 09] - 2026-10-15: Process-Scoped Desktop Scheduler, Native Adapters & Strict Quiescence (Gate G2 Desktop Logic Delivered; Physical Notification Display Pending Phase 11)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative; distinct from the historical 2026-10-14 Phase 09 Linux desktop distribution release.*

### Added
- Desktop reminder adapter Service Provider Interface (SPI) and dispatcher in `:desktopApp`:
  - `DesktopReminderAdapterProvider`: Service Provider Interface allowing target-specific native reminder adapter implementations (`linux` and `windows`) to be packaged in `desktopApp/src/<target>Main` and discovered via Java `ServiceLoader`.
  - `DesktopReminderAdapter`: Extension interface of shared `NativeReminderAdapter` providing `setScheduleListener()` for the desktop coroutine scheduler to receive reconciled desired alarms.
  - `PlatformReminderDispatcher`: Dispatches and initializes native reminder adapters based on detected host OS. Enforces strict target isolation: exactly one matching provider must be registered; zero, multiple, or foreign providers fail closed with an explicit `IllegalStateException`.
- Process-scoped desktop reminder scheduler (`dev.tabbyrush.desktop.DesktopReminderScheduler`):
  - Manages reminder scheduling while desktop window is visible or minimized without requiring background daemons or system tray processes.
  - Time-bounded 15,000ms wake loop (`withTimeoutOrNull`) on `Dispatchers.IO`: immune to backward wall-clock adjustments and system suspend/resume sleep without polling full task lists.
  - Replace-based alarm updates: desired alarm updates replace prior lists and wake the loop immediately via a conflated channel (`Channel.CONFLATED`).
  - Grace window evaluation: due alarms claim through `ReminderSchedulingCoordinator.deliverReminder()`, enforcing the durable 5-minute grace window check in SQLite before attempting native notification posting.
  - Cancellation quiescence: implements `LeaveSchedulerCancelGate`, cancelling the wake loop and awaiting job completion on account leave or wipe.
- Composite desktop cancellation gate (`dev.tabbyrush.desktop.CompositeDesktopLeaveCancelGate`):
  - Strict 5-step ordered quiescence during account leave (`AccountLeaveProtocol`): (1) closes `ReminderDeliveryGate` and drains in-flight delivery admissions with a 5,000ms timeout (fails closed with `IllegalStateException` on timeout), (2) cancels and joins reactive Room schedule observation, (3) cancels and awaits `DesktopReminderScheduler` wake loop, (4) cancels and awaits active `DesktopSyncScheduler` sync jobs, and (5) cancels and clears platform notifications.
  - Fail-closed invariant: any cancellation step failure aborts teardown, retaining `wipe.marker` and database files.
- Linux Freedesktop D-Bus reminder adapter (`dev.tabbyrush.desktop.linux.LinuxNativeReminderAdapter`):
  - Bridges shared `NativeReminderAdapter` to `org.freedesktop.Notifications` via `DbusLinuxNotificationAdapter` over `Dispatchers.IO` to protect Swing EDT responsiveness.
  - Capability tolerance: probes daemon capabilities and treats successful query as enabling notifications even if the capabilities array is empty, supporting minimal notification daemons (dunst, mako).
  - Account-scoped tracking: maps platform uint32 notification IDs to account keys in an in-memory set (`ConcurrentHashMap<String, MutableSet<Long>>`).
  - Close error propagation: `cancelAllForAccount()` aggregates daemon close errors and throws an explicit `IllegalStateException` on failure.
- Windows candidate toast adapter (`dev.tabbyrush.desktop.windows.WindowsNativeReminderAdapter`):
  - Candidate adapter configuring AUMID `dev.tabbyrush.desktop`, group prefix `d_${accountKey.value.take(12)}`, and tag `t_${taskId.take(16)}_$occurrenceIndex` with XML entity escaping on title and message text.
  - Multi-account isolation: `cancelAllForAccount()` iterates account-scoped identifiers and cancels group notifications rather than performing a global toast wipe.
  - Disclosed stub status: `JnaWindowsToastBridge` is an unintegrated candidate stub returning `WindowsNotificationStatus.Unavailable` for all operations; native WinRT/COM toast posting is deferred to Phase 11 on a Windows testbed.
- Automated test suites:
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-scheduler-test.kt`: 5 tests for wake loop, backward clock jump protection, replace-based updates, and clean cancellation.
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate-test.kt`: 2 tests for strict 5-stage quiescence and drain timeout failure.
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-reminder-controller-lifecycle-test.kt`: 3 tests for reminder subsystem init, delivery gate state, and fail-closed wipe marker retention on notification cancellation error.
  - `desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-reminder-adapter-test.kt`: 6 tests for capabilities, account-scoped tracking, replace ID mapping, and error propagation.
  - `desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-reminder-adapter-test.kt`: authored candidate unit test suite with fake bridge for toast formatting, multi-account isolation, and error propagation (unexecuted on Linux host).

### Changed
- `DesktopController` and `main.kt` in `:desktopApp`:
  - Integrated `ReminderDeliveryGate`, `ReminderSchedulingCoordinator`, `DesktopReminderScheduler`, and `CompositeDesktopLeaveCancelGate` into `DesktopAccountRuntime`.
  - Fail-closed startup wipe recovery: in `completePendingWipesOnStartup()`, exceptions during `reminderAdapter.cancelAllForAccount()` trigger `return@forEach`, retaining `wipe.marker` and database files fail-closed.
  - Single-window close lifecycle: `dispose()` executes `disposeSuspend()`, cooperatively cancelling observers, draining runtime gates, closing database files, and releasing `SingleInstanceGuard` file lock with zero surviving background daemon or tray processes.

### Verification and Delivery Status
- QA report records 298 passing automated checks across 7 test/check groups, plus 1 standalone packaging assembly task:
  1. `:desktopApp:test` (48 Linux/JVM unit & platform tests PASS across 10 classes, executed with `--dependency-verification=off`).
  2. `:shared:jvmTest` (182 unit/SQLite tests PASS across 26 classes, executed with `--dependency-verification=off`).
  3. `:androidApp:testDebugUnitTest` (12 unit tests PASS across 4 classes, executed with `--dependency-verification=off`).
  4. `node scripts/strict-v2-vector-runner.mjs` (23/23 vector runner checks PASS).
  5. `node scripts/hub-live-smoke.mjs --fixture-test` (18/18 offline smoke fixture checks PASS).
  6. `node scripts/release-security-check.mjs --diagnose-source` (6/6 static source security checks PASS).
  7. `node scripts/hub-strict-upgrade.test.mjs` (9/9 CLI tests PASS).
  - *Separate Packaging Build*: `:desktopApp:createDistributable` (1 standalone application bundle assembly verified).
  - *Platform Test Scope Note*: `WindowsReminderAdapterTest` is an authored candidate test suite with fake bridge in `windowsTest`, but was not compiled or executed on this Linux host runner.
- Cycle 2 code review score **8.9 / 10** conditionally approved with remediations verified.
- Gate G2 Status: Logic, scheduler, SPI dispatcher, and native adapters delivered; physical notification bubble display on active desktop session and installed Windows MSI execution remain pending Gate G2 / Phase 11.

---
## [Phase 08] - 2026-10-15: Android Alarms, Private Notifications & Device Restoration (Gate G2 Android Logic Delivered; Physical Connected Device Acceptance Pending Phase 11)

*Note: Part of the 2026-10-15 Task Deadlines, Reminders & Strict Upgrades initiative; distinct from the historical 2026-10-14 Phase 08 release operations release.*

### Added
- Android native reminder and alarm scheduling infrastructure in `:androidApp`:
  - `AndroidNotificationAdapter`: Implements `NativeReminderAdapter`, managing the `tabbyrush_reminders` high-importance notification channel with `VISIBILITY_PRIVATE`, generic public lockscreen fallback (`VISIBILITY_PUBLIC`, text: "Task reminder"), exact alarms via `AlarmManager.setExactAndAllowWhileIdle` (falling back to `setAndAllowWhileIdle`), and notification clearing.
  - `AndroidAlarmRegistry`: Synchronized SharedPreferences storage (`tabbyrush_alarms_<accountKey>`) tracking active alarm keys (`generation:taskId:scheduleToken:occurrenceIndex`) and active notification IDs via synchronous `commit()` writes.
  - `TabbyRushAlarmReceiver`: Non-exported (`android:exported="false"`) receiver handling explicit `ACTION_DELIVER_REMINDER` intents with direct-boot unlock verification, identity-only extras (no user text in alarm PendingIntent extras or URIs), wipe marker absence check, and 9s cooperative timeout via `ReminderSchedulingCoordinator`.
  - `ReminderRestoreReceiver`: Non-exported receiver handling system lifecycle events (`BOOT_COMPLETED`, `USER_UNLOCKED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`), completing pending wipes before reconciling active account reminders.
  - `CompositeLeaveSchedulerCancelGate`: Coordinates strict 4-stage teardown: (1) delivery gate drain -> (2) schedule observer cancel -> (3) WorkManager sync cancel -> (4) AlarmManager/notification cancel.
  - `ReminderPermissionController`: Sealed capability hierarchy (`Exact`, `BestEffort`, `NotificationDenied`, `ChannelDisabled`) and settings intent factories.
  - Cold-start deep linking in `MainActivity` and `SessionViewModel.selectTaskFromNotification`.
- Automated test suites:
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/AndroidNotificationAdapterTest.kt`: 6 unit tests for request code determinism, capabilities model, tag formatting, registry serialization, and restore actions.
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/CompositeLeaveSchedulerCancelGateTest.kt`: 3 unit tests for drain timeout, sync cancel failure, and ordered quiescence.
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/ReminderPermissionControllerTest.kt`: 2 unit tests for permission state representations.
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/BuildConfigTest.kt`: 1 unit test for configuration loading.

### Verification and Delivery Status
- Automated test suites pass 282 executed checks across reported test suites (12 Android unit tests in `:androidApp:testDebugUnitTest`, 182 shared JVM tests, 32 desktop tests, 23 strict-v2 vectors, 18 offline smoke fixture checks, 6 static security checks, 9 CLI tests; `:androidApp:assembleDebug` APK build verified separately).
- Code review score **9.2 / 10** approved.
- Gate G2 Status: Logic and native adapters delivered; connected physical device / emulator execution, Doze mode testing, live broadcast receipt across reboot, and platform acceptance remain formally pending Gate G2 / Phase 11.

---
## [Phase 07] - 2026-10-15: Shared Scheduling, Persisted Eligibility & Account Safety (Gate G5 Passed; Native Adapters Unlocked for Phases 08/09)

### Added
- **Scheduling Calculator**: `ReminderScheduleCalculator` in `:shared` computing planned occurrences with checked 64-bit arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`), 1..10 repeat bounds, strict 5-minute grace window (`300_000ms`), latest opportunity selection, and consumed opportunity barriers.
- **Persisted Schedule Projector**: `PersistedScheduleProjector` computing projections directly from atomic SQLite snapshots of `server_base` and `outbox`, strictly isolating `editor_drafts`, clearing nullable deadlines, and applying conflict head suppression and completion cancellation barriers.
- **Occurrence Ledger Store**: `ReminderOccurrenceStore` managing `reminder_delivery_ledger` under composite 5-tuple PK (`accountKey`, `generation`, `taskId`, `scheduleToken`, `occurrenceIndex`), supporting reopened task preservation, reconcile deltas, atomic claim-before-post (`evaluateAndClaimDueOpportunity`), and native removal tracking.
- **Delivery Gate**: `ReminderDeliveryGate` enforcing three-tier lock hierarchy (`gate admission -> store mutex -> DB transaction`), fail-closed callback admission, and teardown quiescence (`closeAndDrain(5000L)`).
- **Scheduling Coordinator**: `ReminderSchedulingCoordinator` managing per-account lifecycle across store, ledger, native adapter, and delivery gate.
- **Verification**: 270 automated checks pass with 0 failures (182 shared JVM tests, 32 desktop tests, 23 strict-v2 vectors, 18 smoke fixture checks, 6 security diagnostics, 9 CLI tests). Gate G5 formally passed.

---

## [Phase 06] - 2026-10-15: Client Version Transition, Safe Replay & Intention Preservation (Gate G4 Logic Delivered; Connected Platform Acceptance Pending Phases 08/11)

### Added
- **Transition Coordinator**: `ContractTransitionCoordinator` implementing 7-state upgrade lifecycle (`ready-v1` -> `upgrade-detected` -> `dispatch-quiesced` -> `legacy-reconciling` -> `v2-base-scanning` -> `safe-intents-translating` -> `ready-v2`) with durable SQLite checkpointing in `AccountSyncMetaEntity`.
- **Legacy Receipts Wire & Client**: Wire DTOs and `HubSyncClient.legacyReceipts()` for `POST /api/v1/sync/{appId}/strong/legacy-receipts` under `DUAL_AUTH_STRICT` with bounded batches (`<= 100`).
- **DAO & Reconciler Extensions**: `TabbyRushDao.countOutboxByVersion()`, `discardRegressiveReconcileAtomic()`, non-destructive intent-masked rebasing in `RoomAccountStore.reconcileAccepted()`, and migration-mode staging in `StagedFullScan`.
- **Conflict Review**: `isUnknownSuperseded` banner and accessible version comparison card.
- **Verification**: 243 automated checks pass with 0 failures (155 shared JVM, 32 desktop, 23 vectors, 18 smoke fixture, 6 security, 9 CLI tests). Gate G4 offline logic delivered; connected Android and platform notifications remain pending Phases 08–11.

---

## [Phase 05] - 2026-10-15: Shared Task Model & Explicit Room v2 Migration (Gate G4 Storage Migration Delivered; Instrumented Platform Acceptance Pending)

### Added
- **Temporal Domain Models**: `TaskDeadline`, `ReminderRule`, and `Todo` extensions in `:shared` with schedule token presence and checked 64-bit integer arithmetic.
- **Room Database v2 Migration**: `DatabaseMigrations.MIGRATION_1_2` explicit step-by-step SQLite migration upgrading schema v1 to v2 without destructive fallback, registered in `DatabaseMigrations.ALL`.
- **Room Entities & DAO**: Added `AccountLocalSettingsEntity` (`account_local_settings`) and `ReminderDeliveryLedgerEntity` (`reminder_delivery_ledger`), and expanded 4 existing entities with 12 temporal fields. Extended `TabbyRushDao` with local settings and ledger queries and atomic transactions.
- **Verification**: 175 automated checks pass with 0 failures (128 shared JVM tests including `RoomMigrationV1ToV2Test`, 23 vectors, 18 smoke fixture checks, 6 security checks). Storage migration delivered; cross-version outbox reconciliation scheduled for Phase 06.

---

## [Phase 04] - 2026-10-15: Hub Admin UI and Shared-Operation Upgrade Script (Gate G3 Operator Tooling Delivered; Staging Acceptance Pending)

### Added
- **CLI Upgrade Runner**: `scripts/hub-strict-upgrade.mjs` supporting `--dry-run`, bounded status polling, same-ID retry, secret redaction, and 16-column v2 read-back assertions.
- **Admin Web UI**: `StrictAppUpgradePanel`, `StrictContractCard`, and `StrictUpgradeControls` in `glean-hub-app`.
- **Verification**: 13 `glean-hub-app` tests, 7 CLI runner tests, 18 smoke fixture checks, 23 vectors, 6 security checks pass. Production admission remains on hold pending Gate G3 live replica-set staging.

---

## [Phase 03] - 2026-10-15: Hub v2 Admission, Fencing and Migration (Gate G3 Backend Logic & Tests Delivered; Staging Gate Pending)

### Added
- **Backend Models & Migration Machine**: 8-stage migration machine and canonical v1/v2 schema builders in `glean-hub-sync`.
- **Permit Service & Upgrade Engine**: Bounded coordination permits (`MAX_CONCURRENT_PERMITS = 1000`), exclusive migration fencing, and chunked in-place MongoDB document transformation (`BATCH_SIZE = 500`).
- **Legacy Receipts Bridge**: `POST /api/v1/sync/:app_id/strong/legacy-receipts` route with dual-auth and bounded batches (`<= 100`).
- **Verification**: 54 Rust tests in `glean-hub-sync` pass, clippy clean; 23 vectors, 18 smoke fixture checks pass. Gate G3 live cluster execution remains on hold.

---
## [Phase 02] - 2026-10-15: Native Delivery & Strict DST Resolution (Gate G2 Blocked on Host Prerequisites)

### Added
- Strict timezone and DST resolution contract in `:shared`:
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-timezone-resolver.kt`: `StrictTimeZoneResolver` interface and `StrictZoneOffsetResult` sealed hierarchy (`Single`, `Overlap`, `Gap`). Rejects non-existent spring-forward gap instants (`IllegalArgumentException`), requires explicit offset disambiguation for fall-back overlap hours, and calculates calendar day boundaries with calendar arithmetic (23h/25h).
  - Platform actual bindings delegating to standard `java.time.zone.ZoneRules`: `shared/src/jvmMain/kotlin/dev/tabbyrush/shared/contract/strict-timezone-resolver-jvm.kt` and `shared/src/androidMain/kotlin/dev/tabbyrush/shared/contract/strict-timezone-resolver-android.kt`.
  - Comprehensive vector test suite: `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-timezone-resolver-test.kt` exercising 7 test vectors (NY spring gap, NY fall overlap, standard unambiguous time, 23h and 25h calendar day boundaries, input bounds validation, and midnight gap transitions).
- Candidate mobile notification adapter & receiver in `:androidApp`:
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-adapter.kt`: Notification channel setup (`tabbyrush_reminders`), intent preparation, and AlarmManager exact alarm scheduling (`setExactAndAllowWhileIdle`).
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/android-reminder-capabilities.kt`: Independent capability probes decoupling `POST_NOTIFICATIONS` (API 33+) from `SCHEDULE_EXACT_ALARM` (`AlarmManager.canScheduleExactAlarms()` on API 31+).
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-alarm-receiver.kt`: Non-exported `BroadcastReceiver` (`android:exported="false"`) handling offline alarm dispatch with strict intent extras validation.
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/android-notification-adapter-test.kt`: Unit tests verifying action and extra constants, intent generation, and capabilities data classes.
- Candidate desktop notification adapters in `:desktopApp`:
  - `desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt`: Linux D-Bus candidate communicating with `/org/freedesktop/Notifications`, mapping missing daemons (`ServiceUnknown`) and absent session bus to `LinuxNotificationStatus.Unavailable` without crashes.
  - `desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter-test.kt`: 4 unit tests verifying missing daemon handling, missing session handling, safe host probing, and capabilities under mock proxy.
  - `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt`: Windows toast candidate configuring AUMID `dev.tabbyrush.desktop`, XML attribute/entity escaping, and tag/group toast clearance. Includes `JnaWindowsToastBridge` explicitly returning `isAvailable() = false` and `WindowsNotificationStatus.Unavailable` pending native WinRT/COM implementation and Windows testbed in Phase 09.
  - `desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter-test.kt`: Unit tests verifying XML escaping, XML payload construction, and explicit bridge unavailability.

### Changed
- `androidApp/src/main/AndroidManifest.xml`: Added `SCHEDULE_EXACT_ALARM` and `POST_NOTIFICATIONS` permissions, and declared non-exported `TabbyRushAlarmReceiver` (`android:exported="false"`).
- `desktopApp/gradle-linux.lockfile`: Reconciled locked artifacts to remove stale JNA dependencies while maintaining Linux target dependency isolation.
- `docs/android-acceptance.md`: Updated Section 4.2 with Phase 02 mobile acceptance matrix, recording candidate adapter and receiver verification status as unexecuted pending host Android SDK/device in Phase 08.
- `docs/linux-acceptance.md`: Updated Section 4.2 with Phase 02 Linux desktop acceptance matrix, recording candidate adapter status and missing daemon probe on workstation session.
- `docs/windows-acceptance.md`: Updated Section 4.2 with Phase 02 Windows desktop acceptance matrix, recording candidate adapter status as source-inspected/unexecuted pending Windows testbed in Phase 09.
- `docs/system-architecture.md`: Updated status to reflect Phase 02 deliverables and Gate G2 blocked status; added Section 4.10 (Native Notification Candidates & Strict Timezone Architecture), Invariants 29–32, and Section 6.5.

### Verification and Gate G2 Status
- Reported automated test runs pass 147 tests across `:shared:jvmTest` (115 tests, including 7 strict timezone vectors) and `:desktopApp:test` (32 tests, including 4 Linux D-Bus tests) under `--dependency-verification=off` (recorded in Phase 02 QA and Code Review reports).
- Strict root dependency verification (`./gradlew without --dependency-verification=off`) remains failing until platform lockfile hashes are unified.
- Gate G2 physical native delivery is formally recorded as **BLOCKED** per specification due to missing implementation and workstation prerequisites: Android SDK/device absent (Phase 08), active desktop notification daemon absent on user D-Bus session (Phase 09), and Windows native WinRT/COM implementation and host runtime absent (Phase 09).
---
## [Phase 01] - 2026-10-15: Freeze Temporal Model and Strict Transition (Gate G1 Frozen)

### Added
- Canonical schema specifications:
  - `integration/glean-hub/tabbyrush-schema-v1.json`: Canonical `strict-v1` schema with 4 columns (`id`, `title`, `completed`, `createdAt`), SHA-256 fingerprint `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`.
  - `integration/glean-hub/tabbyrush-schema-v2.json`: Canonical `strict-v2` target schema with 16 columns (adding `deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`), SHA-256 fingerprint `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`.
- Binary test vectors:
  - `integration/glean-hub/strict-v2-vectors.json`: 20 cross-stack cryptographic reference test cases (9 valid vector preimages + SHA-256 digests; 11 boundary and overflow rejection vectors).
- Kotlin Multiplatform domain contracts and digest engine:
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v2-contract.kt`: `StrictV2Constants`, `StrictV2TodoData`, `StrictV2OpKind`, `StrictV2SyncOp`, `LegacyReceiptStatus`, `LegacyReceiptResult`, with checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`) rejecting overflow wrapping at intermediate and terminal operations.
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v2-digest.kt`: `StrictSyncV2Digest` calculating SHA-256 binary preimages with framed identity prefix (8 length-prefixed strings + big-endian 8-byte signed expected revision), 0 bytes for DELETE, and canonical presence-tagged field framing for CREATE/UPDATE.
- Test suites & verification runners:
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-v2-digest-vector-test.kt`: 5 vector tests verifying migrated null, date-only, timed DST overlap, repeating count 10, and disabled rule preimages against fixture.
  - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-v2-digest-extended-test.kt`: 5 extended tests exercising cleared deadlines, Unicode strings, zero-byte DELETE, negative epochs, and all boundary/overflow rejection cases.
  - `scripts/strict-v2-vector-runner.mjs`: Independent Node.js test runner verifying preimage framing, SHA-256 digests, boundary rejections, and automated schema fingerprint derivation.
  - `scripts/generate-strict-v2-vectors.mjs`: Vector generation utility producing canonical fixture vectors.

### Changed
- `docs/system-architecture.md`: Added Section 6 documenting `strict-v2` wire schema, bit-level binary digest framing, and planned management endpoints (`/strict-contract`, `/strict-upgrades`), app-scoped admin fencing, and legacy receipt reconciliation.
- `docs/local-storage-and-sync.md`: Added Section 7 detailing planned Room v1 -> v2 schema evolution, migrated default policies, outbox provenance fields (`protocolVersion`, `dispatchedOnce`, `changedFieldsMask`), legacy operation receipt reconciliation states (`PROVEN_ACCEPTED`, `PROVEN_NOT_ACCEPTED`, `UNKNOWN_SUPERSEDED`, `INTEGRITY_FAILURE`), intent rebase invariants, and Section 7.3 mathematical invariants.
- `docs/hub-schema-integration.md`: Added versioned schema upgrade specification, canonical schema fingerprints, planned management endpoints, and admin fencing lifecycle.
- `docs/code-standards.md`: Added coding standards for strict-v2 preimages, checked 64-bit integer arithmetic, temporal model representations, and error code conventions.
- `docs/codebase-summary.md`: Updated codebase inventory, Repomix metrics, directory structure, and architectural invariants for strict-v2 contracts and scripts.
- `docs/project-overview-pdr.md`: Updated PDR to Version 1.8.0 reflecting Phase 01 / Gate G1 freeze specifications and deliverables.

---
## [2026-10-15] - Direct Target Collection Migration (FR-STRICT-02)

### Changed
- Migrated Glean Hub `strict-v1` backend storage resolution from dynamic epoch-based formula (`_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>`) to direct application target collection (`app_<appId>`, e.g. `app_tabbyrush`).
- Updated `AppService::strict_collection_name` and `validate_strict_admission` in `glean-hub-sync` to validate and admit target collection `app_<appId>` with compound scan index `{ userId: 1, tableName: 1, rowId: 1 }`, uncapped storage, and no TTL indexes.
- Reconciled MongoDB standalone topology check in admission preflight to handle both `isWritablePrimary` and `ismaster`.
- Updated TabbyRush maintenance recovery script `scripts/hub-strict-recovery.js` to target `app_${CONFIG.appId}` with collision safeguards.
- Rechecked TabbyRush shared KMP and Android client modules, confirming zero breaking changes and full strict-v1 protocol compatibility.
- Added operator migration runbook: existing `_strict_v1_5_tabbyrush_<cleanEpoch>` collections can be renamed in `mongosh` via `db.getCollection(oldCollectionName).renameCollection("app_tabbyrush")` before running `strong_sync_admission`.

---

## [Phase 10] - 2026-10-14: Native Windows DPAPI & Installed MSI Implementation

### Added
- Windows `WindowsDpapiVault` using user-scoped `CryptProtectData` / `CryptUnprotectData`, atomic replacement, and fail-closed user ACL enforcement; `testAclEnforcementOnStoredVaultFile` checks the stored-file owner ALLOW entry.
- `WindowsDpapiVaultProvider` ServiceLoader registration; Windows runtime excludes Linux D-Bus dependencies.
- `desktopApp/gradle-windows.lockfile` and `scripts/windows-package-smoke.ps1` with COM metadata inspection.
- Windows evidence summary reports the `TabbyRush.exe` distributable and unsigned `TabbyRush-1.0.0.msi` (100.68 MB) built and verified; seven high-fidelity Windows UI design mockups.

### Verification and remaining acceptance
- Evidence summary reports 121/121 combined desktop and shared JVM tests passing, target isolation verified, and MSI generated/package metadata inspected.
- Phase 10 implementation delivered / 100% on 2026-10-14. Technical acceptance is blocked by root-level strict dependency verification, interactive MSI installation, and Narrator verification; clean-user runtime/sync and uninstall/reinstall remain pending. The MSI is unsigned and is not a production release.

---

## [Phase 09] - 2026-10-14: Linux Desktop & Installed RPM Distribution
### Added
- `:desktopApp` module configuration with Compose Multiplatform 1.7.3 on Kotlin JVM 2.1.10.
- Target isolation and custom dependency locking (`gradle-linux.lockfile`, 161 locked artifacts).
- `CredentialVaultProvider` SPI and `PlatformVaultDispatcher` for fail-closed ServiceLoader resolution.
- `SingleInstanceGuard` exclusive file-channel lock (`~/.local/share/tabbyrush/accounts/tabbyrush.lock`).
- `DesktopSyncScheduler` foreground-only sync scheduler with `LeaveSchedulerCancelGate` quiescence.
- `LinuxSecretServiceVault` storing tokens via Freedesktop Secret Service (`secret-tool` / D-Bus).
- Desktop test suites: `PlatformVaultDispatcherTest` (5/5 PASS), `DesktopLifecycleTest` (8/8 PASS), `LinuxSecretServiceVaultTest` (6/6 PASS).
- Packaging diagnostics runner `scripts/linux-package-smoke.sh`, runbooks, and 7 visual design mockups.

---
## [Phase 08] - 2026-10-14: Authorized Hub Operations & Signed Android Release
### Added
- Operational tooling: `scripts/hub-live-smoke.mjs` (fixture mode passing 18/18 checks), `scripts/release-security-check.mjs` (static diagnostic mode), and `scripts/hub-strict-recovery.js` (quarantined strict-v1 emergency cold restore).
- Operational runbooks: `deployment-guide.md`, `security-and-recovery.md`, `release-acceptance.md`, and evidence summaries.
- 7 pixel-precise release visual design baseline mockups (`docs/evidence/android-release-*.png`).
---
## [Phase 07] - 2026-10-14: Android Composition & Runtime Integration
### Added
- `:androidApp` module (AGP 8.7.3, minSdk 26, compileSdk 35) with `TabbyRushApplication` process container and fail-closed wipe recovery.
- `SessionViewModel` with reactive `TodoUiState`, conflict review dialog, and zero credential retention.
- WorkManager `AndroidSyncScheduler` and `TabbyRushSyncWorker` with account-tagged cancellation.
- Security rules (`backup_rules.xml`, `data_extraction_rules.xml`, loopback `network_security_config.xml`).
- Instrumented device tests (`todo-device-test.kt`), smoke runners (`scripts/android-smoke.sh`), and mockups.

---
## [Phase 06] - 2026-10-14: Shared Compose UI & Accessible Light Theme
### Added
- Shared Compose UI components in `shared/`: `TabbyRushApp`, `ResponsiveLayout`, `TodoList`, `TodoItemRow`, `TodoEditorDialog`, `SyncStatusBar`, `ConflictDialog`, `LeaveDialog`.
- Accessible light theme tokens conforming to WCAG 2.1 AA contrast requirements.

---
## [Phase 05] - 2026-10-14: Durable Local State & Strict Per-Record Sync
### Added
- Room KMP persistence (`TabbyRushDatabase` v1) with `BundledSQLiteDriver`.
- Local outbox mutation engine, staged full-scan reconciler, and fail-closed account teardown protocol.
- 89 JVM common and Room integration tests.

---
## [Phase 04] - 2026-10-14: Hub Authentication & Protected Session
### Added
- Hub authentication client with Ktor HTTP transport and single-flight token refresh coordinator.
- `AndroidKeystoreVault` hardware-backed credential storage and generation-fenced session state machine.

---
## [Phase 03] - 2026-10-14: Isolated Strict Server & Legacy Isolation
### Added
- Glean Hub strict-v1 protocol extension in Rust (`glean-hub-sync`), dedicated strict routes, and legacy isolation guards.

---
## [Phase 02] - 2026-10-14: Frozen Foundation & Contracts
### Added
- Root Gradle multiplatform build baseline with Gradle 8.11.1 wrapper and pinned version catalog.
- 16 core Kotlin domain contracts, NIST digest test vectors, and validation test suites.

---
## [Phase 01] - 2026-10-13: Headless Tooling & Android Environment
### Added
- Headless Xpra virtual display runner (`scripts/start-xpra.sh`) and Android SDK/emulator lifecycle scripts.
- Operational setup runbooks and host evidence records in `docs/`.
