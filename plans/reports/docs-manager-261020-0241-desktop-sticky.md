# Phase 05 — Linux/Windows Grouped Sticky Mode & Deadlines Documentation Report

**Date:** 2026-10-20  
**Agent:** StickyDocsManager (evc-docs-manager)  
**Scope:** Phase 05 documentation updates across `docs/codebase-summary.md`, `docs/system-architecture.md`, `docs/design-guidelines.md`, `docs/linux-acceptance.md`, `docs/windows-acceptance.md`, `docs/project-changelog.md`, `docs/project-roadmap.md`, and `repomix-output.xml`.  
**Outcome:** Complete. Implementation and automated unit test suite delivered; native Linux GUI session and native Windows hardware/stacking acceptance remain formally held at Gate G2 and deferred to Phase 06 acceptance gate.

---

## 1. Documentation Scope & Alignment

Phase 05 implements Compose Multiplatform desktop sticky companion window grouped hierarchy, pure section projection, absolute deadline presentation, and account-scoped controller:
- **Changed Source & Test Files Documented:**
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-model.kt`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt`
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt`
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-controller-env-test.kt`

- **Key Architectural Concepts Documented:**
  - **Pure `projectStickySections` Projection & Whole Compact Groups:** Pure transformation helpers `projectStickySections` and `projectStickyItems` in `sticky-task-model.kt` consuming shared `TaskHierarchyProjector.projectHierarchy` with `TodoFilter.ALL`. Preserves whole compact groups intact within Incomplete (active parents display together with all active and completed sibling children) and Completed sections without splitting groups across sections. Enforces ancestor deletion dominance (suppressing child tasks of deleted parents) and effective parent completion derivation without platform forks.
  - **Absolute Own Deadlines & Overdue Styling:** Evaluates `TaskDeadlinePresentation.formatDeadline` strictly from each task's own stored deadline; zero deadline inheritance between parent and child. Displays `"Due YYYY-MM-DD"` or `"Due YYYY-MM-DD HH:mm (captured zone)"`. Reminder anchor time (09:00 default) never leaks into date-only rows. Overdue active rows render bold error-colored badge text on `TabbyRushCoralSurface` supplementing the absolute date; completed tasks never render overdue; rows lacking deadlines omit deadline metadata entirely.
  - **Compact Grouped Layout & Automatic Parent Progress:** `CompactTaskRow` in `sticky-task-content.kt` applies `SpacingLarge` start indentation for child subtasks (`isChild = true`). Parents with live children render read-only, disabled `Checkbox(enabled = false, onCheckedChange = null)` reflecting automatic completion progress (`Subtasks: X/Y completed`) and TalkBack/screen-reader state descriptions (`"Completed/Active (automatic: X/Y subtasks)"`). Standalone roots and leaf children bind interactive checkboxes. Title text supports 2 lines with ellipsis; deadline and overdue badges layout via `FlowRow`. Controls provide `>=48dp` minimum touch targets as layout provisions (physical touch/font bounds remain subject to native platform verification).
  - **Periodic Deadline Refresh & Clock Ownership:** `StickyTaskContent` launches a 30s periodic ticker (`LaunchedEffect`) dispatching `StickyTaskAction.RefreshDeadlines`. Controller owns an injectable `clock: () -> Long` defaulting to `System.currentTimeMillis()`, updating `projectedSections` without database re-queries.
  - **Independent Root Quick-Add & Fenced Controller Mutations:** Desktop sticky quick-add bar creates independent root tasks only (`createDefaultTodo(title, nowEpochMs = clock())`), even when interacting with a group row; child creation remains main-app-only. Actions validate `activeToken` and `access.getCurrentToken() == action.token` before and after async store admission. Stale-text preservation is retained if the user types while save is pending. Direct parent completion toggles fail closed against shared store admission without mutating store or triggering sync.
  - **Controller Concurrency Distinction:** Controller uses `_stickyState.update` for observer emissions and `refreshDeadlines`; quick-add mutations and readiness collection use read-copy-write updates; controller concurrency edge cases (async readiness collector account-switch race and unsynchronized compound quick-add state updates) are tracked for Phase 06 remediation.
  - **Technical Acceptance Qualifier:** Physical desktop display verification (interactive window stacking, 100%–200% DPI scaling, Wayland/X11 tray, Windows native tray menu, and Orca/Narrator screen-reader audio inspection) remains formally held at Gate G2 and deferred to the Phase 06 acceptance gate.

---

## 2. Changes Made Across Documentation Artifacts

### 2.1 `docs/codebase-summary.md` (765 LOC < 800 LOC)
- **Compaction Metrics**: Updated header to reflect fresh Repomix snapshot metrics: 669 files packed, 1,735,486 tokens, 6 security-scanner exclusions (`docs/security-and-recovery.md`, 4 historical reports, and `endpoint-policy-test.kt`).
- **Module Overview (Section 1.4)**: Updated Desktop Application Module description to document `StickyTaskItem`, `StickyTaskSections`, pure `projectStickySections` projection, automatic parent progress, indented subtasks, and 30s deadline refresh ticker.
- **Directory Tree (Section 2)**: Updated file annotations for `sticky-task-model.kt`, `desktop-task-surfaces.kt`, `sticky-task-content.kt`, and `desktop-task-surfaces-test.kt`.
- **Deliverables Summary (Section 4)**: Added Item 57 providing comprehensive specification of Phase 05 deliverable (pure projection, whole compact groups with `TodoFilter.ALL`, absolute own deadlines via `TaskDeadlinePresentation`, automatic parent progress, 30s deadline refresh ticker, independent root quick-add, strengthened `SurfaceAccountToken` fencing, reported test verification of 127 desktop tests, and native session deferral to Phase 06 / Gate G2).

### 2.2 `docs/system-architecture.md` (795 LOC < 800 LOC)
- **Section 7 Desktop Sticky Window & Tray**: Updated architectural description to document whole compact groups, pure projection, absolute own deadlines, overdue badges, automatic parent progress, 30s ticker, and strengthened account-generation fencing.
- **Section 9 Headings & Opening Status**: Reconciled status and headings to reflect Phases 01–05 Delivered (Contract, Hub, Room v3, Sync, Hierarchy Evaluator, Barrier Scheduling, Main UI, Android Glance Widget, Desktop Sticky Companion; Phase 05 Review Score 8/10, 0 Criticals).
- **Section 9 Acceptance Evidence & Caveats**: Added Phase 04 and Phase 05 reported verification entries, added desktop sticky concurrency caveat (collector account-switch race and compound state update serialization), and reiterated that live cross-device replica set rollout and native platform execution remain tracked in Phase 06.
- **Preserved Safety Findings**: Kept all existing safety disclosures intact (delivery-drain timeout, swallowed widget cleanup, 149-checksum baseline, Glance provenance hold, release holds G2/G3/G6/G7).

### 2.3 `docs/design-guidelines.md` (219 LOC < 800 LOC)
- **Section 7.3 Desktop Sticky Companion Window**: Documented compact grouped hierarchy styling (`SpacingLarge` start indentation for child subtasks), automatic parent progress labeling (`Subtasks: X/Y completed`), `FlowRow` deadline presentation with overdue badge styling, 2-line title clamping, 30s background deadline refresh ticker, and explicit note that physical touch bounds, wrapping, and screen-reader traversal on native desktop environments remain subject to native platform verification.

### 2.4 `docs/linux-acceptance.md` (126 LOC < 800 LOC)
- **Section 2.3 Added**: Documented Phase 05 Grouped Sticky Mode & Deadline Presentation Status in a structured verification matrix, splitting reported unit coverage (`DesktopTaskSurfacesTest` PASS) from source inspection of layout provisions (`sticky-task-content.kt`), and explicitly recording that live Linux GUI session acceptance (always-on-top, Wayland/X11 tray, 200% scaling, Orca screen-reader) is deferred to Phase 06 of plan `plans/261019-1511-parent-child-tasks-deadline-surfaces` (distinguishing from historical Phase 08 of plan `plans/261016-1409-sticky-task-surfaces`).

### 2.5 `docs/windows-acceptance.md` (167 LOC < 800 LOC)
- **Section 2.4 Added**: Documented Phase 05 Grouped Sticky Mode & Deadline Presentation Status in a structured verification matrix, splitting reported unit coverage (`DesktopTaskSurfacesTest` PASS) from source inspection of layout provisions (`sticky-task-content.kt`), and explicitly recording that native Windows desktop acceptance (always-on-top stacking, native tray menu, multi-monitor DPI scaling, Narrator screen-reader) remains held at Gate G2 and deferred to Phase 06 of plan `plans/261019-1511-parent-child-tasks-deadline-surfaces`.

### 2.6 `docs/project-changelog.md` (705 LOC < 800 LOC)
- **Phase 05 Entry Added**: Detailed entry for Phase 05 under `[Parent/Child Tasks & Deadline Surfaces / Phase 05] - 2026-10-20: Linux/Windows Grouped Sticky Mode & Deadlines` covering delivered projection, layout, deadlines, parent progress, quick-add isolation, reported automated evidence (518 Gradle tests, 150 offline mock/vector/fixture/script checks across 6 suites, 5 static audit checks), Cycle 2 review (8/10), and technical acceptance qualifiers.

### 2.7 `docs/project-roadmap.md` (771 LOC < 800 LOC)
- Reconciled with parallel project manager edits: Milestone 7 Phase 05 marked DONE§ (12h credited, 96h / 120h credited total; 80.0%), with Phase 05 deliverable details and changelog entry.

### 2.8 `repomix-output.xml`
- Regenerated full repository compaction snapshot via `repomix --output repomix-output.xml` (669 files packed, 1,735,486 tokens, 6 security-scanner exclusions).

---

## 3. Validation & Quality Assurance

1. **Size Limit Audit (`docs.maxLoc: 800`):**
   - `docs/codebase-summary.md`: 765 LOC
   - `docs/system-architecture.md`: 795 LOC
   - `docs/design-guidelines.md`: 219 LOC
   - `docs/linux-acceptance.md`: 126 LOC
   - `docs/windows-acceptance.md`: 167 LOC
   - `docs/project-roadmap.md`: 771 LOC
   - `docs/project-changelog.md`: 705 LOC
   - *Result*: All documentation files are strictly below the 800 LOC ceiling.

2. **Documentation Validation Tooling (`validate-docs.cjs`):**
   - Executed via host runner: `node /home/loidinh/.omp/agent/evcrate/scripts/validate-docs.cjs docs/`
   - *Result*: **279 internal links working (0 broken)**, 47 code references validated OK, 15 config keys confirmed.
   - Code reference warnings (1,767) are expected scanner heuristics due to multiplatform Kotlin source paths outside standard JS/TS scanner paths; newly referenced symbols verified directly against source files.

3. **Advisor Consultation & Incorporation:**
   - Pre-edit and pre-completion consultations executed with Advisor (`xd://ask_advisor`).
   - Incorporated all strategic recommendations:
     - Reconciled status and headings throughout Section 9 in `docs/system-architecture.md`.
     - Distinguished model/controller unit coverage from source-inspected Compose layout provisions and pending native verification.
     - Enumerated all 150 offline checks (92 vector/smoke/rehearsal checks + 58 script unit tests) and clarified that `hub-live-smoke.mjs --fixture-test` is offline fixture testing.
     - Accurately characterized controller concurrency behavior: `_stickyState.update` for observer/ticker vs. read-copy-write for quick-add/collector, noting tracked edge cases for Phase 06.
     - Preserved all release holds (G2/G3/G6/G7, 149-checksum baseline, Glance provenance hold) and safety disclosures.

---

## 4. Retained Safety Findings & Open Gates

1. **Gate G2 & Native Desktop Acceptance Hold:** Physical desktop window display, DPI scaling (100%–200%), always-on-top window stacking, tray minimization/recovery, and screen-reader traversal (Orca/Narrator) on native Linux and Windows hosts remain held at Gate G2 and deferred to Phase 06 acceptance testing on live host sessions.
2. **Desktop Sticky Controller Concurrency Caveats:** In `DesktopStickyTaskController`, observer cancellation upon `tokenChanged` in the asynchronous readiness collector can race with rapid account switching, and compound read-copy-write state updates across quick-add actions lack mutex serialization; both are tracked for Phase 06 remediation.
3. **Open System Safety Findings:**
   - *Android Delivery Drain Timeout*: `closeRuntimeInternal()` catches and logs drain timeout before observer cancellation and Room closure; `closeActiveRuntime()` clears runtime reference.
   - *Swallowed Android Widget Cleanup*: RemoteViews blanking and Glance session closure catch `Throwable` non-fatally during leave protocol.
   - *Supply-Chain & Provenance Holds*: 149 unmatched checksums in root dependency verification and separate Glance artifact provenance hold remain open.
   - *Backend Release Gates*: Multi-node MongoDB replica set live migration (Gate G3), three-device live sync proof (Gate G6), and production cutover authorization (Gate G7) remain open.

---

## 5. Unresolved Questions

- None. Phase 05 documentation is complete, accurate, verified, and synchronized across all project artifacts.
