# Phase08 — later Linux-only adaptation and real-session acceptance gate

## Context links
[Overview](plan.md) · [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md) · [desktop research](research/researcher-02-desktop-surfaces.md) · [analysis](reports/01-codebase-analysis.md) · [contracts](architecture-contracts.md) · [ownership](file-ownership.md#phase08--later-linux-only-implementationsession-gate) · [acceptance](acceptance-matrix.md) · [dependencies](dependency-matrix.md).

## Parallelization Info
Group E later; **preparation hard dependency06** (transitive01+04); **completion hard dependencies06+07 + actual Linux machine/graphical session**. Not runnable on Windows through host override. May prepare Linux-only provider/tests after06 without touching shared paths, but no final acceptance before07. If01 actually needs affected Linux lock resolution, that prerequisite remains01's ownership and matching-host gate.

## Overview
- Date: 2026-10-16. Priority: P2. Effort: **8h**.
- Implementation status: **pending / 0%**. Review status: pending.
- Complete Linux-specific session/capability adaptation through06's generic seam and observe actual Linux windows/tray/focus/recovery/geometry/runtime. Missing machine/session explicitly leaves phase pending.

## Key Insights
Generic compact/window/runtime logic already exists after04/06. Linux tray and always-on-top/focus vary by distribution, desktop/window manager and X11/Wayland. Headless build or Windows success cannot establish native behavior. Existing Linux reminder bubble/Orca/production release holds are distinct and must stay unchanged.

## Requirements
1. Only Linux-specific provider/service-registration/tests/docs/evidence edits; no duplicate generic implementation or ownership of main.kt/shared/geometry/build/metadata files.
2. Real adapter reports target session/tray capability using supported runtime environment/native AWT behavior; select generic available-tray or no-tray recovery policy truthfully. No fake always-supported flag, task daemon or second window manager/runtime.
3. Native repeat acceptance8–10 and relevant2/3/7/12, explicitly satisfy11 with distro/session/WM/tray/stacking/focus/restore evidence.
4. Native tray absent/failing => actual last-visible recovery/quit controls and no stranded hidden process; generic behavior must already be complete, not deferred from06.
5. Keep Linux pending until actual machine/session and required scenarios observed; record exact unsupported capability instead of claiming universal always-on-top.

## Architecture
Host-selected linuxMain source set + ServiceLoader registration supplies at most one `DesktopSurfacePlatformProvider` to06 generic shell. Provider specializes capability/session/usable-display information only; all account admission, task controller, singleton windows, close-to-hide/Quit, compact input and preference geometry rules remain generic. Existing default handles absence safely and fully; Linux provider is real adaptation/environment reporting, not required runtime scaffold completion.

## Related code files
**Exclusive:** complete08 table names proposed linux-task-surface-platform.kt, exact Linux provider service resource, Linux adapter test, linux-acceptance.md/linux-desktop-guide.md, Linux evidence summary/two actual captures and08 report.
**Read-only:**06 platform seam/main/controller/tray/teardown/tests,04 geometry/preferences/content,01 shared/access contracts and all lockfiles, existing Linux notification/vault code and build host guard. No Linux reminder-system redesign.

## File Ownership
Only08 exact Linux-specific paths, including tests. Common defects return04/06 (or shared01); Linux metadata updates return01. Docs07 owns shared roadmap/changelog/architecture;08 records final Linux verdict in its own docs/evidence and reports it to main for original doc owner updates, never duplicate writes.

Explicit owned paths (repository-relative; actions/reasons in central manifest):
- `desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-task-surface-platform.kt`
- `desktopApp/src/linuxMain/resources/META-INF/services/dev.tabbyrush.desktop.DesktopSurfacePlatformProvider`
- `desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-task-surface-platform-test.kt`
- `docs/linux-acceptance.md`
- `docs/linux-desktop-guide.md`
- `docs/evidence/sticky-surfaces-linux-summary.md`
- `docs/evidence/sticky-surfaces-linux-window.png`
- `docs/evidence/sticky-surfaces-linux-recovery.png`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-08-linux-evidence.md`

## Implementation Steps
1. Obtain actual Linux target machine and graphical session; record distribution/version/kernel/JDK, desktop/WM, X11/Wayland, scales/work areas, tray availability and runtime environment. No machine/session => retain pending0%, no fabricated acceptance or host mismatch bypass.
2. Consume terminal06 complete generic seam; final native run also consumes07 Android/Windows acceptance baseline. Review exact source-set loading/service provider signatures before implementation; no assumed current API.
3. Implement Linux-only provider using real environment/AWT session/capability/display data with unsupported states explicit. Register only in declared linuxMain service file; no Windows/common build changes or new unverified native dependency. If session API unavailable, use existing complete no-tray recovery with documented capability, not fake positive result.
4. Author Linux-only provider boundary tests exercising actual environment/capability handling and production state, no hard-coded distro assumptions/source-string tests. Common lifecycle tests remain06-owned. Compile/run only on Linux with `-PdesktopTarget=linux`, honor existing host guard.
5. Run planned Linux strict shared/desktop tests/distributable and existing `:desktopApp:runDebug`; record actual output and any baseline checksum/dependency gate. No `--dependency-verification=off` acceptance, arbitrary metadata regeneration or Windows cross-compilation as Linux proof.
6. In actual session verify one guarded process/main/sticky/tray if available, repeated Show singleton, ordinary-window stacking/focus, close/hide/restore and visible no-tray fallback. Test capability failure/absence; do not equate current Tray API with usable native icon.
7. Exercise offline compact creation/desired completion/order/filter independence and main-editor isolation, hidden-main local/sync convergence, account change clears content and stale event rejects. Only explicit Quit drains background/runtime/releases profile lock; manual relaunch reacquires.
8. Exercise sticky shown/hidden manual-launch restoration, actual available multi-monitor/negative-origin/DPI changes, monitor removal and work-area reachability. No autostart/IPC registration. Record limitations if target session cannot supply a requested monitor scenario; acceptance remains pending for that scenario, not silently reduced.
9. Route common defect to original owner, await corrected terminal handoff and repeat affected acceptance.08 must not patch main.kt/helper/tests/build metadata as convenience or fork common logic.
10. Update only declared Linux guide/acceptance/evidence summary and real captures, plus <=150-line08 report. Every relevant matrix row has pass/fail/pending + direct evidence. Report final Linux result to main; original07 doc owner may update its own shared feature status after genuine evidence, not08 editing that file.

## Todo list
- [ ] Secure/record real Linux target/session; otherwise remain pending.
- [ ] Implement real Linux-only provider/service registration/tests through completed generic seam.
- [ ] Run scheduled matching-host checks and actual native desktop scenarios.
- [ ] Return generic defects to original owners; retest coherent corrections.
- [ ] Record Linux-only guide/acceptance/evidence verdict without closing unrelated holds.

## Success Criteria
Acceptance11 observed on actual named Linux session, relevant desktop scenarios complete with native proof and honest supported limits. One runtime/window/tray policy, no hidden orphan process, current-account isolation and manual reachable restore. Tests/builds alone insufficient. If machine, tray/recovery, stacking/focus or geometry prerequisite missing/unproven, phase stays pending/failed rather than completed. Existing release/native reminder holds unchanged.

## Conflict Prevention
No main.kt/shared/geometry/Gradle/lock/catalog/verification/shared-doc writes. Sole Linux-specific manifest rows; original module owners retain fixes even during this later gate. No second Linux sticky shell/store to avoid common code defects.

## Risk Assessment
Wayland/WM may not honor requested stacking/focus; verify target session and document real limitation, do not claim universal support. Tray absence must leave visible generic recovery. Missing graphical/multi-monitor testbed is explicit pending acceptance. New native dependency would require01 contract/metadata review, not08 unilateral scope expansion.

## Security Considerations
Same current-account token/generation gates and local-only geometry/no credentials apply on Linux. No background daemon/auto-start added. Safe disposable test account for wipe/sync; redact captures. Existing Secret Service/session and native reminder availability remain unchanged and not implicitly certified.

## Next steps
Once actual evidence satisfies11 and relevant desktop rows, main may mark this feature's Linux gate complete via original doc owners. Production G2/G3/G6/G7/supply-chain and native reminder holds remain separately held; no release/cutover authorization.

## Unresolved questions
Target Linux distribution, desktop/window manager/session, machine and monitor/tray availability. These are real later acceptance prerequisites, not unresolved common architecture/product semantics.
