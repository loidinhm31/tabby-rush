# Phase 10 — Desktop process runtime, tray and native inbox activation

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **C**; implementation start: [P05](phase-05-shared-remote-core.md), [P06](phase-06-shared-inbox-ui.md), [P07](phase-07-linux-native-adapter.md), [P08](phase-08-windows-native-packaging.md).
- Additional runtime/acceptance dependencies: [P02](phase-02-backend-canonical-inbox.md), [P03](phase-03-fcm-http-v1-transport.md), [P11](phase-11-operations-config-deployment.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Validated rollout: publish Linux runtime evidence for the restricted pilot independently of Windows acceptance. Code/build prerequisites above remain unchanged; P10 is not fully complete until both platforms pass.
- Exclusive owner **P10**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.
## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented-windows-held; review: approved; status: in-progress; progress: 100% implementation (acceptance pending Windows proof); effort: **24h**.
- Description: Desktop process runtime, tray and native inbox activation implemented; clean process quiescence on full quit; 31 desktop tests pass under strict dependency verification; physical Windows native execution held at announcement Gate G5.

## Key Insights
- Desktop runtime appId already tabby-rush; keep existing account controller ownership.
- Hiding UI is not stopping process; full quit stops streams/timers/native callbacks.

## Requirements
- Open-window and tray live delivery/click restore/inbox, no-tray accessible window and silent reopen catch-up.
- Integrate real adapters and all host callers without source/build overlap.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Host process session owns DefaultRemoteInboxController and native adapter. Compose observes state and emits separate remote actions. Platform providers use07/08 adapters; Swing/Compose UI dispatcher handles restore/navigation and account generation guards.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/tabby-rush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` | modify | Compose process-owned remote runtime/controller/UI and installed AUMID startup/shutdown. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate.kt` | modify | Join remote cancellation/drain without blocking on remote network. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-remote-runtime.kt` | create | Process/session controller scope, activation dispatch, foreground/periodic and quit lifecycle. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-remote-platform.kt` | create | Platform-neutral provider interface consuming target-specific factories. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-remote-platform.kt` | create | Instantiate real Linux adapter for host runtime. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-remote-platform.kt` | create | Instantiate real DLL/JNA adapter and retain native lifetime for host runtime. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-remote-runtime-test.kt` | create | Tray/process vs UI lifetime, quiesce, failed drain and stale activation tests. |

## File Ownership
- P10 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Instantiate remote platform/controller once per authenticated runtime with actual appId, not per Compose screen. Bind separate required state/actions to every TabbyRushApp host call; no compatibility defaults or no-op runtime at cutover.
2. Set Windows AUMID before notifier startup, load packaged DLL through08 factory and clear stale remote group. Linux provider obtains persistent bus adapter. Do not edit desktop build/lock or target adapter source.
3. Keep SSE/timer/controller while window hidden in tray. On foreground trigger silent refresh; activation validates generation, requests restore/unminimize on UI dispatcher then dispatches authorized Open. Do not force-focus beyond OS capability.
4. Preserve existing no-tray visible recovery path; window close cannot hide app irretrievably when tray unavailable. Show native capability limitations/inbox entry, not fake notification success.
5. On account leave advance/fence generation first, quiesce streams/timers/native callbacks and clear only remote notifications/cache/nav. Drain failure retains fenced runtime reference and prevents replacement publication; do not close auth resources still used by surviving work.
6. On full quit close stream/timers, clear remote group/IDs and destroy adapter/callbacks before exit. Do not install cold COM launcher; after reopen silent canonical catch-up, no historical alert replay.
7. Add process-lifetime tests with controlled adapters, then actual Linux open/tray/no-tray/quit/click matrix for P12 pilot gate. Record Windows evidence separately at the full-delivery gate; never label held Windows checks passed. Unit doubles prove lifecycle only, not native feasibility.

## Todo list
- [x] Read owned files/current upstream changes and confirm frozen contract.
- [x] Complete all numbered steps with deterministic failure handling.
- [x] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [x] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- Actual visible/tray process receives; native click opens intended authorized inbox.
- No-tray remains accessible, full quit no live reception, reopening silent catch-up.
- Stale callbacks never populate replacement account; reminders/sticky/task sync unchanged.
- Pilot handoff may credit observed Linux results only; Windows scope, installed MSI activation proof and full P10 completion remain mandatory for full delivery.

Future targeted verification (not executed by this plan):
- `On each host: ./gradlew :desktopApp:test --tests "*DesktopRemoteRuntimeTest" --dependency-verification=strict`
- `On Windows use gradlew.bat and installed08 MSI; real12 live SSE and click while window visible/tray, then full quit/reopen.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- UI dispatcher/platform callbacks race quit; native destroy and generation barriers must both hold.
- Linux-only development cannot clear Windows installed native acceptance.

## Security Considerations
- Never fake task IDs or route remote hints into local reminder schedules.
- No cross-account retained content/nav, no OS group-wide destructive clear of unrelated features.

## Next steps
- Consumers/unblocked phases: [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
