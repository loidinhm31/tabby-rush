# Phase 04 — Android Grouped Widget & Deadline Labels Documentation Report

**Date:** 2026-10-20  
**Agent:** Phase04DocsManager  
**Scope:** Phase 04 documentation updates across `docs/android-acceptance.md`, `docs/codebase-summary.md`, `docs/project-changelog.md`, `docs/project-roadmap.md`, and `repomix-output.xml`.  
**Outcome:** Complete. Implementation delivered; native launcher/device execution remains formally held at Gate G2, with execution tracked in the parent/child plan's Phase 06 acceptance gate.

---

## 1. Documentation Scope & Alignment

Phase 04 implements native Android Glance widget grouped hierarchy and absolute deadline presentation:
- **Changed Files Documented:**
  - `androidApp/src/main/res/values/task-widget-strings.xml`
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-projection.kt`
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget.kt`
  - `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-widget-device-test.kt`
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/task-widget-model-test.kt`

- **Key Architectural Concepts Documented:**
  - **Native Glance Adaptive Layout:** `TaskRow` replaces fixed-height single-line rows with adaptive Glance Column and Box compositions. Explicit 48×48dp CheckBox container `Box(contentAlignment = Alignment.Center)` with minimum 48dp `Spacer` serves as a layout provision intended to meet Android >=48dp touch target guidelines (physical touch targets remain subject to on-device launcher verification). Navigable title/metadata `Column` sets `.fillMaxHeight()` with `Alignment.CenterVertically`. Title supports 1 line (compact) or 2 lines; deadline text configures `maxLines = 3` to accommodate multi-line wrapped deadline and overdue strings on compact widths (truncation prevention under 200% font scaling remains subject to on-device verification). Child subtasks indent by 12dp (compact) or 20dp (expanded).
  - **Pure `projectWidgetItems` Projection:** Pure transformation helper in `dev.tabbyrush.android.projectWidgetItems` (`task-widget-projection.kt`) consuming shared `TaskHierarchyProjector.projectHierarchy` with `TodoFilter.ALL`. Preserves whole compact groups intact within the Incomplete section (active parent displays together with both active and completed sibling children) and Completed section without splitting groups. Enforces ancestor deletion dominance (suppressing child tasks of deleted parents) and effective parent completion derivation.
  - **Absolute Own Deadlines & Overdue Styling:** Evaluates `TaskDeadlinePresentation.formatDeadline` strictly from each task's own stored deadline; zero deadline inheritance between parent and child. Displays `"Due YYYY-MM-DD"` or `"Due YYYY-MM-DD HH:mm (captured zone)"`. Reminder anchor time (09:00 default) never leaks into date-only rows. Overdue active rows append `• Overdue` decoration in `GlanceTheme.colors.error` with `FontWeight.Bold` without replacing the absolute date; completed tasks never render overdue; rows lacking deadlines omit metadata lines entirely.
  - **Read-Only Automatic Parent Progress & Token Fencing:** Roots with live children render read-only `CheckBox(onCheckedChange = null)` reflecting automatic child completion progress (`Subtasks: X/Y completed`) and aggregated TalkBack content descriptions (`task_widget_parent_automatic_content_desc`), preventing direct group toggle. Standalone roots and leaf children bind interactive callbacks fenced by `SurfaceAccountToken(accountKey, generation)`. Quick Add action routes to `createAddTaskIntent` creating independent root tasks only (child creation remains main-app-only). Stale parent completion callbacks fail closed against shared store admission.
  - **Technical Acceptance Qualifier:** Physical connected device/emulator launcher verification (pinning widget, responsive scaling across 120×120 to 320×360dp, 200% font scaling, and TalkBack audio inspection) remains formally held at Gate G2 and deferred to the Phase 06 acceptance gate due to headless CI execution constraints.

---

## 2. Changes Made

### 2.1 `docs/android-acceptance.md`
- **Section 3.7**: Annotated flat task partitioning as historical Phase 07 behavior, referencing Section 3.8 for Phase 04 grouped hierarchy.
- **Section 3.8 Added**: Comprehensive architectural specification of Grouped Glance Widget & Deadline Presentation (pure projection, absolute own deadlines, adaptive Glance layout, automatic parent progress, token fencing).
- **Section 4.1 Component Matrix**: Added rows for `projectWidgetItems (Projection)` and `TabbyRushTaskWidget (Grouped & Deadlines)`.
- **Section 4.2 Restructured**: Separated historical Phase 07 evidence (Section 4.2.1) and added Phase 04 evidence (Section 4.2.2) reporting 402 passing tests/checks (341 `:shared:jvmTest`, 50 `:androidApp:testDebugUnitTest` including 18 `TaskWidgetModelTest` assertions, 11 Node v3 vectors; 47 executed tasks in `:androidApp:compileDebugAndroidTestKotlin` reported separately as compilation evidence; all run under `--dependency-verification=off`).
- **Section 4.3**: Updated Gate G2 physical execution status and cutover integration with the Phase 04 technical acceptance qualifier deferring launcher verification to Phase 06, while retaining open safety findings (delivery-drain timeout, swallowed widget cleanup, 149-checksum baseline, Glance provenance hold).

### 2.2 `docs/codebase-summary.md`
- **Compaction Metrics**: Updated header to Repomix snapshot metrics (664 files packed, 6 exclusions: `docs/security-and-recovery.md`, 4 historical reports, `endpoint-policy-test.kt`).
- **Module Inventory**: Added `task-widget-projection.kt` to Android module description (Section 1) and directory tree (Section 2).
- **Section 56 Added**: Added Phase 04 deliverable summary (pure projection, own deadlines, adaptive layout, automatic parent semantics, and acceptance boundary).
- **LOC Constraint**: Maintained file size strictly at 758 LOC (< 800 LOC limit).

### 2.3 `docs/project-changelog.md`
- **Phase 04 Entry Added**: Detailed entry for Phase 04 under `[Parent/Child Tasks & Deadline Surfaces / Phase 04] - 2026-10-20: Android Grouped Widget & Deadline Labels` covering delivered projection, layout, deadlines, parent progress, strings, reported test evidence (402 passing checks, 47 executed compilation tasks), Cycle 3 review (8.5/10), and technical acceptance qualifier.
- **LOC Constraint**: Maintained file size strictly at 682 LOC (< 800 LOC limit).

### 2.4 `docs/project-roadmap.md`
- **String Resource Corrections**: Corrected string resource identifiers in Phase 04 deliverable entries to match actual XML names (`task_widget_subtasks_progress`, `task_widget_parent_automatic_content_desc`, `task_widget_overdue`, `task_widget_deadline_with_overdue`).
- **LOC Constraint**: Maintained file size at 751 LOC (< 800 LOC limit).

### 2.5 `repomix-output.xml`
- **Regenerated**: Full repository compaction regenerated via `repomix --output repomix-output.xml` (664 files packed, 1,716,676 tokens, 6 security-scanner exclusions).

---

## 3. Validation & Quality Assurance

1. **Size Limit Audit (`docs.maxLoc: 800`):**
   - `docs/system-architecture.md`: 790 LOC
   - `docs/codebase-summary.md`: 758 LOC
   - `docs/project-roadmap.md`: 751 LOC
   - `docs/project-changelog.md`: 682 LOC
   - `docs/android-acceptance.md`: 320 LOC
   - *Result*: All files strictly below the 800 LOC limit.

2. **Documentation Validation Tooling (`validate-docs.cjs`):**
   - Executed via host runner: `node /home/loidinh/.omp/agent/evcrate/scripts/validate-docs.cjs docs/`
   - *Result*: **273 internal links verified (0 broken)**, 47 code references validated OK, 15 config keys confirmed.
   - Code reference warnings (1,702) are normal heuristics due to Kotlin Multiplatform paths outside default JS/TS scanner paths; all newly documented symbols (`projectWidgetItems`, `WidgetTaskItem`, `TaskWidgetActionKeys`, `SurfaceAccountToken`, `TaskHierarchyProjector`, `TaskDeadlinePresentation`) verified directly against source.

3. **Advisor Consultation:**
   - Pre-edit and pre-completion consultations executed with Advisor.
   - Incorporated all feedback: decoupled compilation tasks from test assertions, preserved Gate G2 holding status, kept historical evidence historical, toned layout provisions to match evidence bounds, and recorded functional caveats (overdue composition-time evaluation without automatic midnight refresh).

---

## 4. Retained Safety Findings & Open Gates

1. **Gate G2 Acceptance Hold:** Physical connected device/emulator execution, interactive launcher widget pinning, responsive scaling (120×120, 240×120, 240×240, 320×360dp), 200% font scaling, and TalkBack audio verification remain held at Gate G2 and deferred to Phase 06 acceptance gate.
2. **Overdue Refresh Limitation:** Overdue evaluation captures `nowEpochMs = System.currentTimeMillis()` at composition time without automatic midnight/boundary trigger refresh; periodic/boundary wake is tracked as an acceptance gap for Phase 06.
3. **Open Android Safety Findings:**
   - *Delivery Drain Timeout*: `closeRuntimeInternal()` catches and logs drain timeout before observer cancellation and Room closure; `closeActiveRuntime()` still clears active runtime.
   - *Swallowed Widget Cleanup*: RemoteViews blanking and Glance session closure catch `Throwable` non-fatally during leave protocol, risking ghost widget state on launcher if AppWidgetManager fails.
   - *Supply-Chain & Provenance Holds*: 149 unmatched checksums in root dependency verification and separate Glance artifact provenance hold remain open.

---

## 5. Unresolved Questions

- None. Phase 04 documentation is complete, accurate, verified, and aligned across all artifacts.
