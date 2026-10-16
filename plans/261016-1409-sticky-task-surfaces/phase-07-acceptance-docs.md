# Phase07 — integrated Android/Windows acceptance and affected shared documentation

## Context links
[Overview](plan.md) · [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md) · [analysis](reports/01-codebase-analysis.md) · [Android research](research/researcher-01-android-surfaces.md) · [desktop research](research/researcher-02-desktop-surfaces.md) · [contracts](architecture-contracts.md) · [ownership](file-ownership.md#phase07--integrated-androidwindows-acceptance-and-affected-shared-docs) · [acceptance](acceptance-matrix.md) · [dependencies](dependency-matrix.md).

## Parallelization Info
Group D; **hard dependencies05+06**, transitively01–04. Main integrator schedules one final coherent aggregate verification after owner edits land; no build storms or acceptance against half-integrated code. Linux-only preparation may coexist in08's own files after06; final Linux verdict is later and depends07. This phase owns no production/test/build file.

## Overview
- Date: 2026-10-16. Priority: P2. Effort: **12h**.
- Implementation status: **pending / 0%**. Review status: pending.
- Exercise actual Android launcher/shade and Windows windows/tray, reconcile affected docs/evidence with delivered architecture; keep Linux and historical release holds truthful.

## Key Insights
Compilation and unit tests cannot certify native launcher scrolling, API34 dismissal, Windows stacking/tray or per-monitor restore. Existing release documents contain separate G2/G3/G6/G7/supply-chain holds; this feature must not turn them green. PDR100%-shared-UI claim becomes inaccurate when native Glance/platform shells ship; adjust only affected description, not historical evidence.

## Requirements
1. Every applicable acceptance1–10 and12 plus race/semantic edge row receives direct pass/fail/pending evidence.11 stays pending08; Android/Windows cannot be silently reduced to a build-only gate.
2. Run future commands on correct hosts with JDK21/SDK35, real Android API26–32/33/34+ states and interactive Windows. Strict verification baseline/signing/device shortfalls remain explicit prerequisites, not fake pass.
3. Native proof includes launcher sections/all IDs/scroll/actions, quiet shade selection/privacy/permission/channel/dismissal, cold/account/wipe races; singleton sticky/tray/Close recovery/hidden sync/Quit/profile lock and real monitor/DPI restoration.
4. Update all affected exact docs in07 ownership, no unowned source/tests/backend/history. Evidence screenshots are actual captures, redacted, never mockups.
5. Preserve unrelated production release holds/native reminder gaps and separate pending Linux machine gate.

## Architecture
Validation consumes terminal owner reports/tests and actual integrated runtime. Findings route back to original file owner (01 shared/build,02 widget,03 notification,04 geometry,05 Android roots/auditor,06 desktop roots); corrected terminal handoffs precede final check.07 owns shared documentation and Android/Windows evidence only. Architecture§7 changes from intended to delivered solely where actual implementation/evidence supports it; platform-specific pending items stay pending.

## Related code files
**Exclusive modifications/creates:** complete07 manifest lists README, PDR, architecture, codebase summary, standards/design/storage/auth/security, Android/Windows acceptance and Windows guide, deployment/toolchain docs, both existing roadmaps/changelog, two evidence summaries/four actual captures and07 report.
**Read-only:** every production/module test/build path01–06 and historical release-acceptance/strict-v2/backend docs; current Linux acceptance/guide owned08. Re-read actual affected doc sections before editing; no broad historical overclaim cleanup.

## File Ownership
Exactly07 doc/evidence paths, each one owner. No omnibus late test phase, no production “quick fixes,” no new capture filename without manifest amendment.08 owns Linux-specific docs/evidence. Source defects retain original ownership even if sequential or platform-discovered.

Explicit owned paths (repository-relative; actions/reasons in central manifest):
- `README.md`
- `docs/project-overview-pdr.md`
- `docs/system-architecture.md`
- `docs/codebase-summary.md`
- `docs/code-standards.md`
- `docs/design-guidelines.md`
- `docs/local-storage-and-sync.md`
- `docs/authentication-and-credentials.md`
- `docs/security-and-recovery.md`
- `docs/android-acceptance.md`
- `docs/windows-acceptance.md`
- `docs/windows-desktop-guide.md`
- `docs/deployment-guide.md`
- `docs/toolchain-versions.md`
- `docs/project-changelog.md`
- `docs/project-roadmap.md`
- `docs/development-roadmap.md`
- `docs/evidence/sticky-surfaces-android-summary.md`
- `docs/evidence/sticky-surfaces-windows-summary.md`
- `docs/evidence/sticky-surfaces-android-widget.png`
- `docs/evidence/sticky-surfaces-android-notification.png`
- `docs/evidence/sticky-surfaces-windows-window.png`
- `docs/evidence/sticky-surfaces-windows-tray.png`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-07-acceptance-evidence.md`

## Implementation Steps
1. Collect terminal05+06 handoffs,01 compatibility/provenance and all module test scope. Confirm actual contracts/callers/teardown implemented, no runtime stubs/fakes/placeholder UI and no new schema/backend/auto-start/exact permission. Pending testbed is a gate, not approval to narrow deliverable.
2. Main integrator schedules future acceptance-matrix command set once on coherent snapshot: shared JVM, Android unit/assemble/connected, Windows desktop tests/distributable, auditor behavior/source+merged release audit when signing custody exists. Record actual results and inherited metadata blockers separately; never use broad verification-off to certify provenance.
3. Native Android: install on controlled actual launcher/API matrix, add multiple IDs, small/large sizes and enough rows, scroll both sections, desired-state complete/reopen/Add/Open. Offline save then normal reconnect/completed sync must converge without Activity alive. Capture actual launcher and native shade, not only app UI.
4. Exercise notification oldest-overdue sequence/no-result/date-only equality/ties/conflict/reminders-disabled, saved vs unsaved drafts, Complete twice after process recreation, stale generation and correct Open. Real opt-in ActivityResult API33+, deny/app-off/channel-off/resume and private/generic lock screen; reminder settings remain independent. API34+ swipe persists same-key suppression across title edit/sync/clock/restart, proper key reset and delayed delete/programmatic cancel behavior.
5. Exercise process kill separately from force-stop/manual restart, cold timeout/locked/no-account, normal logout, pending-wipe crash/failure and same-account new generation. Coordinate delayed real Glance update handoff against account replacement; final launcher/shade must be blank/new account, never stale. Confirm cleanup failure retains marker and no doomed DB opening.
6. Native Windows: one main/sticky/tray/process profile; repeated Show singleton, above ordinary app and focus behavior, close each independently and restore, local/sync updates while main hidden, explicit Quit and lock reacquire. Native no-tray/init-failure route retains visible recovery/quit. No secure-screen/fullscreen guarantee.
7. Test manual relaunch sticky shown/hidden, real negative-origin monitors/taskbar insets, unplug saved display and per-monitor DPI, corrupt prefs/tiny work area. Check title bar and>=48dp content reachable and no autostart registration. Keep unsaved main edit open while invalid/valid compact add to prove isolated input.
8. For failures, issue exact original-owner finding with scenario/evidence and await corrected terminal handoff;07 does not edit source/test/build. Re-run affected final verification only after coherent corrected snapshot; never count unobserved scenario as passed.
9. Update exact affected docs: runtime/observer/admission/atomic completion, independent settings/dismissal/availability, intentional launcher exposure, close-to-hide/process reminder lifetime/explicit Quit, presentation-only prefs, native-vs-shared UI, supported versions and commands. Include new feature status/evidence in roadmaps/changelog without changing unrelated G2/G3/G6/G7 or Windows reminder-toast status.
10. Write redacted actual Android/Windows evidence summaries and declared captures, plus <=150-line07 report mapping1–12 and edges to evidence/result/prerequisite.11 explicitly pending Linux. Mark only observed Android/Windows criteria complete; unmet required platform scenario keeps corresponding gate pending/failed.

## Todo list
- [ ] Collect complete owner handoffs and run scheduled coherent aggregate verification.
- [ ] Exercise actual Android launcher/shade/account/permission/dismissal race scenarios.
- [ ] Exercise actual Windows singleton/lifetime/input/geometry/no-tray scenarios.
- [ ] Return defects to original owners and obtain corrected terminal handoffs.
- [ ] Update exact affected docs/evidence; preserve pending Linux and historical release holds.

## Success Criteria
Acceptance1–10/12 fully evidenced on Android/Windows, or report exact gate failure without claiming phase complete. Actual native surfaces and real SQLite boundaries exercised; tests/build logs/captures tied to snapshot/versions. Shared docs match actual implementation and platform limits.11 remains pending until08 machine/session evidence; no historical release gate closed by this feature.

## Conflict Prevention
Docs/evidence only, disjoint from08 Linux docs and01–06 code/tests. Every source finding returns to original owner. Documentation owner never “fixes” test expectations to force pass or invents screenshots/results. Changes outside manifest require reviewed amendment.

## Risk Assessment
Device/session/signed-release prerequisites can block native/security gate; record exact missing resource and preserve pending status, do not simulate success. Existing checksum failure can block build configuration; treat external hold. OS-specific dismissal/monitor behavior needs repeated actual evidence rather than API plausibility.

## Security Considerations
Use disposable test accounts and explicit scope for logout/wipe; no personal account deletion. No credentials/private task content in screenshots/logs/reports. Verify immutable action generation fencing, merged exported-component policy, generic public notification and wipe-content clearing. Preserve release signing/security controls.

## Next steps
Completed Android/Windows acceptance unblocks08 final Linux verdict, not production release/cutover. Main retains all baseline release holds and missing native reminder implementation gates. No implementation or verification was run by planner.

## Unresolved questions
Android device set, signing custody/trusted dependency baseline and actual Windows multi-monitor testbed availability require execution prerequisites. Later Linux distro/session remains08 gate; no product questions.
