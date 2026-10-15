# Phase 09 — Process-scoped desktop scheduler and native adapters

## Context links
[Parent](plan.md) · [Binding proof](phase-02-prove-native-delivery-and-dst.md) · [Shared scheduling](phase-07-shared-scheduling-and-account-safety.md) · [Linux acceptance](../../docs/linux-acceptance.md) · [Windows acceptance](../../docs/windows-acceptance.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 24h. Owners: desktop scheduler, Linux adapter and Windows adapter leads. Dependencies: phases 02/G2, 05, 07/G5; sync integration depends on 06. One integration owner for main.kt/build config.

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
Doggo root `/home/loidinh/WS/doggo`.
Modify `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt`, `desktop-sync-scheduler.kt` leave-gate composition, `desktopApp/build.gradle.kts`, `gradle/libs.versions.toml`, target lockfiles/verification metadata if needed, existing Linux/Windows package smoke scripts.
Create focused `desktop-reminder-scheduler.kt`, notification adapter/provider contract in desktop main package; `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-notification-adapter.kt`; selected Windows implementation in `windowsMain/kotlin/dev/doggo/desktop/windows/windows-notification-adapter.kt` and target resources/installer identity customization proved in phase 02.
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
- [ ] Account-scoped scheduler and explicit close/await lifecycle.
- [ ] Linux native service/capability/removal adapter.
- [ ] Windows selected binding/installer identity/removal integration.
- [ ] Packaged visible/minimized/exit/resume/cancellation evidence.

## Success Criteria
Linux `./gradlew -PdesktopTarget=linux :desktopApp:test :desktopApp:createDistributable :desktopApp:packageRpm`; Windows host `.\gradlew.bat -PdesktopTarget=windows :desktopApp:test :desktopApp:createDistributable :desktopApp:packageMsi`. Package scripts with existing documented flags. Actual installed/distributable notification shown, not just successful API call; X leaves no process scheduler and no new posts after scheduled deadline. Native failure state observable in UI. No Linux dependency in Windows package or vice versa.

## Risk Assessment
Windows cannot be validated on current Linux host. Do not equate generated MSI with installed proof. Native display/removal failure does not authorize custom fallback delivery.

## Security Considerations
Escape notification text/XML; no shell interpolation of task content. Target native code provenance/license/checksums recorded. Account teardown removes only owned notifications/identity resources, not other apps' history.

## Next steps
Capability flows feed phase 10; full cross-device race/resume acceptance in phase 11.

## Unresolved questions
None after G2 passes; target runtime availability is an execution prerequisite, not a platform scope reduction.
