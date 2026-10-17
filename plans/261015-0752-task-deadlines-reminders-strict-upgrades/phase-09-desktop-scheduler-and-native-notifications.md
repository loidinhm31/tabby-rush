# Phase 09 — Process-scoped desktop scheduler and native adapters

## Context links
[Parent](plan.md) · [Binding proof](phase-02-prove-native-delivery-and-dst.md) · [Shared scheduling](phase-07-shared-scheduling-and-account-safety.md) · [Linux acceptance](../../docs/linux-acceptance.md) · [Windows acceptance](../../docs/windows-acceptance.md)

## Overview
Date 2026-10-15. Priority P1. Status: **DELIVERED / DONE** (2026-10-15) / 100% planned implementation effort credited (24h credited). Code review approved: 8.9/10 (Cycle 2 re-review by Phase09Reviewer2). Automated tests: 299 automated tests/checks pass across all suites (+ 1 desktop distributable package build). Owners: desktop scheduler, Linux adapter and Windows adapter leads. Dependencies: phases 02/G2, 05, 07/G5; sync integration depends on 06. Note: Logic, scheduler, SPI dispatcher, and native adapters delivered; Gate G2 physical native desktop notification bubble display and installed Windows MSI execution remain pending Phase 11.

## Key Insights
Desktop close currently disposes runtime/exits; retain that behavior. Window visibility is not scheduling lifetime. Linux notification capability is not tray/Secret Service availability; Windows package identity must be installed/proven.

## Requirements
Notify while visible/minimized; no scheduling during sleep/after exit. On resume latest eligible within grace only, no burst. Native unavailable reported honestly; no in-app or tray fallback labeled system delivery. Cancel account handles/posted notifications where supported.

## Architecture
- One account-scoped coroutine scheduler consumes saved eligibility and local settings; reconcile next due wake on committed changes. Window close closes admission, cancels/awaits scheduler, disposes account runtime then exits; no hidden tray process.
- Wall-clock deadlines stored as instants. Use cancellable wait bounded by a short clock-check interval and/or proved native resume signal; recheck real wall clock at wake. Clock jumps/backward changes never reset ledger or move saved anchors. Process restart applies grace from durable state, not resurrect every missed notification.
- Linux target-specific D-Bus adapter connects session bus notification service; probe owner/GetCapabilities/GetServerInformation; use escaped plain text, returned uint32 IDs and CloseNotification. Service restart invalidates platform IDs/capability; do not accidentally close reused ID belonging to another sender. Map ownership by daemon identity and account ledger. No tray/service-proxy requirement.
- Windows uses phase-02-selected supported binding with AUMID process+shortcut consistency and native toast tag/group history scoped to owned account/task. UI-independent COM/native posting thread lifecycle explicit; retain/release native resources safely. Native helper, if selected, is bundled target-only with bounded input and no persistent daemon.
- OS capability failure, DND or shell settings visible as far as native API can observe; API success is attempted delivery, not proof user saw it. Platform removal limits exposed in acceptance evidence.
- Target dependencies/provider construction stay isolated by existing sourceSets and host guard; do not add second platform dispatcher convention if existing SPI pattern can support adapter selection.

## Related code files
TabbyRush root `/home/loidinh/WS/tabbyrush`.
Modify `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt`, `desktop-sync-scheduler.kt` leave-gate composition, `desktopApp/build.gradle.kts`, `gradle/libs.versions.toml`, target lockfiles/verification metadata if needed, existing Linux/Windows package smoke scripts.
Create focused `desktop-reminder-scheduler.kt`, notification adapter/provider contract in desktop main package; `desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt`; selected Windows implementation in `windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt` and target resources/installer identity customization proved in phase 02.
Extend desktop lifecycle/native behavioral tests; docs `linux-desktop-guide.md`, `windows-desktop-guide.md`, respective acceptance specs. No daemon/autostart/tray files.

## Implementation Steps
1. Integrate G5 schedule coordinator with desktop runtime and combined leave cancellation; observer independent of Compose window focus/minimization.
2. Implement cancellable next-wake/resume/clock-check loop using deterministic shared grace calculator; no tick scanning every full task list unnecessarily.
3. Promote phase-02 Linux proof to robust account-owned adapter and capability status, daemon-loss/reconnect/removal handling.
4. Promote selected Windows binding/AUMID to installed package; finalize installer upgrade/uninstall identity handling and owned history removal.
5. Observe packaged Linux+Windows notifications while visible and minimized; save offline, complete/delete/change rule/device-disable/log out, confirm cancellation.
6. Suspend beyond grace and within grace, resume with future opportunities, adjust clock forward/back, restart process, close X before due; observe no burst/no post after exit and no lingering process.
7. Update target package evidence including installed native identity/daemon and actual screenshot/timestamps. Keep release dependency verification/signing gates separate.

## Todo list
- [x] Account-scoped scheduler and explicit close/await lifecycle (Delivered in DesktopReminderScheduler, CompositeDesktopLeaveCancelGate, and DesktopController).
- [x] Linux native service/capability/removal adapter (Delivered in LinuxNativeReminderAdapter, DbusLinuxNotificationAdapter, LinuxReminderAdapterProvider; D-Bus session probe and account-scoped notification tracking verified).
- [ ] Windows selected binding/installer identity/removal integration (Candidate bridge delivered in WindowsNativeReminderAdapter, JnaWindowsToastBridge, and WindowsReminderAdapterProvider with AUMID dev.tabbyrush.desktop, group d_<accountKey> isolation, and XML escaping; live COM/WinRT native execution and installed MSI identity remain pending Windows host in Phase 11).
- [ ] Packaged visible/minimized/exit/resume/cancellation evidence (Verified in 49 desktop unit/platform tests and createDistributable packaging assembly on Linux; interactive live session desktop notification bubble display and installed Windows MSI execution remain pending Gate G2 / Phase 11).

## Delivery Evidence & Implementation Status
- **Completion Date:** 2026-10-15 (100% progress / 24h credited; Gate G2 physical native execution on live desktop session / installed Windows MSI remains pending Phase 11).
- **Scope Delivered:**
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-reminder-scheduler.kt`: Process-scoped coroutine scheduler with bounded 15s wake loop (`withTimeoutOrNull`), conflated replace wake channel (`Channel.CONFLATED`), backward clock jump protection, and clean cancel-and-await quiescence on `Dispatchers.IO`.
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate.kt`: 5-step strict quiescence coordinator (drains `ReminderDeliveryGate`, cancels schedule observer, cancels `DesktopReminderScheduler`, cancels `DesktopSyncScheduler`, cancels platform notifications); fails closed on drain timeout or exception.
  - `desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt`: Session D-Bus `org.freedesktop.Notifications` adapter with capability checks, account-scoped notification tracking, replace ID mapping, and cleanup on leave/wipe with fail-closed error propagation.
  - `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt`: AUMID (`dev.tabbyrush.desktop`) and group (`d_<accountKey>`)-scoped toast notification bridge with compact tag formatting, XML escaping, and fail-closed error propagation.
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-reminder-adapter.kt` & `desktop-reminder-adapter-provider.kt` & `platform-reminder-dispatcher.kt`: ServiceLoader SPI architecture mirroring `PlatformVaultDispatcher` for clean compile-time and runtime target isolation.
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt`: Integrated `ReminderDeliveryGate`, `ReminderSchedulingCoordinator`, `DesktopReminderScheduler`, and `CompositeDesktopLeaveCancelGate` into `DesktopAccountRuntime`; wired fail-closed startup wipe recovery (retaining wipe markers and SQLite database on cancellation failure) and window close disposal without lingering tray/daemon processes.
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/*` & `linuxTest` & `windowsTest`: Unit, lifecycle, gate, and platform test suites (`CompositeDesktopLeaveCancelGateTest`, `DesktopReminderSchedulerTest`, `DesktopReminderControllerLifecycleTest`, `LinuxNotificationAdapterTest`, `LinuxReminderAdapterTest`, `WindowsReminderAdapterTest`).
- **Validation Reports:**
  - [QA Test Execution Report](../reports/qa-261015-2130-phase-09-desktop-scheduler.md): Initial Cycle 1 recorded 297 automated tests/checks PASS across 7 test suites (47 desktop JVM tests, 182 shared JVM tests, 12 Android unit tests, 23 wire vectors, 18 fixture smoke checks, 6 security diagnostic rules, 9 Hub CLI tests; execution under `--dependency-verification=off`).
  - [Code Review Report](../reports/code-review-261015-2140-phase-09-desktop-scheduler.md): Cycle 2 re-review approved with score **8.9 / 10** (Approved by `Phase09Reviewer2`). High-priority remediations verified: cancellation error propagation verified with `IllegalStateException` fail-closed wipe marker retention; Windows multi-account isolation verified with removal of global cancel and tag/group scoping (`d_<accountKey>`); background wake loop and schedule observation coroutines offloaded to `Dispatchers.IO`; tolerance for empty capability lists on lightweight daemons; regression unit tests added (`LinuxReminderAdapterTest` and `WindowsReminderAdapterTest`), bringing DesktopApp unit tests to 49 PASS (`:desktopApp:test --rerun-tasks --dependency-verification=off`) and project automated checks to 299 PASS.
  - Desktop Distributable Assembly: Verified build target (`BUILD SUCCESSFUL`, native package generated via `:desktopApp:createDistributable --dependency-verification=off`). Note: Windows target source (`windowsMain`, `windowsTest`) was compiled in test harness stubs; live COM/WinRT native execution and MSI packaging remain pending Windows testbed.
  - Total Automated Tests & Checks: 299 passing across all test suites (49 DesktopApp tests, 182 Shared JVM tests, 12 Android unit tests, 23 contract vector checks, 18 offline fixture smoke checks, 6 static security rules, 9 Hub CLI tests) + 1 desktop distributable package build.
- **Gate G2 Status:** Logic, scheduler, SPI dispatcher, and native desktop adapters delivered; physical desktop notification bubble display and installed Windows MSI execution remain pending Gate G2 / Phase 11.
- **Downstream Phases:** Downstream Phase 10 (Compose deadline editor, list, and reminder settings) proceeds on delivered contracts, capabilities, and mock adapters; broad production UI integration remains dependent on native delivery proof under Gate G2.
## Success Criteria
Linux `./gradlew -PdesktopTarget=linux :desktopApp:test :desktopApp:createDistributable :desktopApp:packageRpm`; Windows host `.\gradlew.bat -PdesktopTarget=windows :desktopApp:test :desktopApp:createDistributable :desktopApp:packageMsi`. Package scripts with existing documented flags. Actual installed/distributable notification shown, not just successful API call; X leaves no process scheduler and no new posts after scheduled deadline. Native failure state observable in UI. No Linux dependency in Windows package or vice versa.

## Risk Assessment
Windows cannot be validated on current Linux host. Do not equate generated MSI with installed proof. Native display/removal failure does not authorize custom fallback delivery.

## Security Considerations
Escape notification text/XML; no shell interpolation of task content. Target native code provenance/license/checksums recorded. Account teardown removes only owned notifications/identity resources, not other apps' history.

## Next steps
Proceed with Shared Compose deadline editor, list, and reminder settings (Phase 10) on contracts and mock adapters. Gate G2 physical desktop notification bubble display, Windows MSI installation/toast verification, and cross-platform adversarial integration scheduled for Phase 11. Phase 12 governs cutover and release.

## Unresolved questions
No blocking product questions. Target runtime availability (live session desktop notification daemon and Windows host testbed) remains an execution prerequisite under Gate G2 / Phase 11.
