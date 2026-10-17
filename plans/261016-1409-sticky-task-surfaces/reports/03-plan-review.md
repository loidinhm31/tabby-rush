# Sticky task surfaces — final planning review

Date: 2026-10-16. Deliverable: implementation plan only. All implementation phases pending / 0%.

## Deliverables
- [Main plan](../plan.md), [concise access point](../evc-cmd-plan.md).
- Eight detailed phase files; architecture contracts, dependency matrix, exhaustive ownership manifest and acceptance matrix.
- Two completed researcher reports, parent codebase analysis and planner brief.
- `docs/system-architecture.md` §7 updated before plan authoring, explicitly intended design/pending implementation. No application source changed.

## Exercised artifact verification
Parent ran a throwaway in-memory Python validator over actual generated Markdown; no script left in repository.
- Eight phase files contain all 15 required ordered sections, plus unresolved questions last.
- `plan.md` YAML contains all required fields. Overview 72 lines after validation summary; access point 34 lines, both under80.
- Phase efforts24+16+20+12+24+16+12+8 =132 estimated labor hours, not deadline/calendar duration.
- Ownership manifest111 exact paths, zero duplicates; every existing M/R path exists.
- Every phase now embeds its explicit owned-path list; lists exactly match its central manifest rows, zero missing/extra assignments.
- All local Markdown links and referenced heading anchors resolve.
- Mandatory acceptance rows are exactly1–12, with native evidence/prerequisites and behavioral race/edge coverage.
- Research reports40 lines each, under150; both terminal results received. Desktop researcher six total calls exceeded max5 by one; Android used5. Exception retained openly, no rerun or fabricated compliance.
- Research release fact checked against official Glance release notes:1.2.0 stable2026-08-26; compileSdk35 change in1.2 alpha notes, minSdk23 in beta notes. Exact TabbyRush compiler/dependency compatibility remains unverified.
- Source check confirmed notifyScheduleChanged only declared/called inside RoomAccountStore; clean private helper replacement in foundation does not require outside caller migration based on current text search. Kotlin LSP absent.

## Execution review
01 foundation precedes02/03/04 concurrent leaves.05 consumes02+03;06 consumes04; integrations can overlap.07 requires both.08 Linux-only preparation after06, final real-session acceptance after07. No fake zero-dependency claims or shared-file dual ownership. Tests belong with module owners; acceptance findings return to original owner.

## Explicit decisions for user review
- Notification uses persisted effective overdue boundary for ordering including date-only tasks; reminders disabled does not exclude deadlines; conflicts shown with indication rather than hidden.
- User dismissal suppresses the same generation/task/full-deadline key across restart/sync/title edits; selection/deadline change or deliberate re-enable resets it.
- Desktop compact quick-add has independent input from an open main editor; one runtime/window/tray, Close hides, explicit Quit drains/exits.
- Unusable tray retains visible restoration/Quit controls. Geometry supports negative monitor origins, work areas/DPI and disconnected monitors. Default sticky hidden; manual launch main visible.

## Evidence limits
No application build, unit/device suite, installation, launcher/shade/window/tray smoke, signing, Linux machine/session or external service operation run. Static plan validation is not runtime acceptance. Exact Glance/session cancellation behavior and platform prerequisites remain implementation gates. Historical native reminder/backend/cutover/supply-chain holds unchanged.

Active-plan helper ran but session variable absent; directory not persisted. Conditional scout skipped because codebase summary generated today. No scout artifacts claimed; reserved scout directory empty.

## Validation interview
User accepted interview; six questions across two batches clarified four decisions. Confirmed selection/deadline-key dismissal reset on Android, conflict indication, desktop sticky initially hidden, and remembered opt-in with prompt on enable/user-invoked permission or Settings guidance after denial. Explicit clarification separated Android notification from widget, desktop and reminder alerts. Answers recorded in plan.md Validation Summary; no phase revisions required. Planning status remains pending; no implementation authorization inferred.

## Unresolved questions
No blocking product question; validation interview complete. Later Linux target machine/distro/session, exact dependency compatibility and native quiescence/dismissal/geometry proof remain execution prerequisites.
