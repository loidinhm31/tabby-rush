# Phase 07 — Linux persistent D-Bus remote alert adapter

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **B**; implementation start: [P01](phase-01-contract-build-gate.md).
- Additional runtime/acceptance dependencies: [P05](phase-05-shared-remote-core.md), [P10](phase-10-desktop-runtime-integration.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P07**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: completed; review: pending; status: completed; progress: 100%; effort: **20h**.
- Description: Linux persistent D-Bus remote alert adapter implemented.

## Key Insights
- Existing transport disconnects each call; action signals need persistent owned connection.
- Wayland focus is compositor-controlled; actions capability is not universal.

## Requirements
- Real Notify+ActionInvoked with current generation; persistent bus and daemon owner lifecycle.
- Separate remote IDs/maps/cleanup, capability limitations and no-tray accessible recovery.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Linux remote adapter owns persistent connection/listeners; existing local reminder adapter can keep its short-lived transport behavior. Shared low-level interface reuse is explicit, not a second fake reminder route.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt` | modify | Reuse low-level transport and expose persistent lifecycle/signals without reminder ID collision. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-remote-notification-adapter.kt` | create | Remote capability/action mapping/daemon epoch and scoped cleanup implementation. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-remote-notification-adapter-test.kt` | create | Signal mapping, daemon restart, missing actions and teardown tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter-test.kt` | modify | Migrate transport fixtures if needed while preserving existing reminder assertions. |

## File Ownership
- P07 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Add persistent session-bus connection with exact org.freedesktop.Notifications proxy and ActionInvoked/NotificationClosed/NameOwnerChanged subscriptions; own handler removal and disconnect.
2. Probe capabilities and daemon owner. No daemon ->Unavailable; no actions ->display possible but capability shows nonclickable and inbox recovery, never claim default click guaranteed.
3. Post fixed generic title/body and remote-only native-ID map, actions default/Open inbox when supported. Map key includes daemon epoch and account generation, cap200; close oldest owned ID when cap reached.
4. Filter action callbacks to known current remote IDs and default action; marshal through host activation callback/UI dispatcher, not fake task ID or direct network. Preserve reminder IDs and settings.
5. On daemon restart/bus failure remove stale mappings/listeners and reconnect bounded backoff. New live hint can post after reconnection; do not replay old notifications.
6. Implement clearRemote/close draining callbacks and closing only remote-owned IDs. Test stale signals/id reuse/reconnect/action absence and request real GNOME/KDE or supported daemon smoke from10/12.

## Todo list
- [x] Read owned files/current upstream changes and confirm frozen contract.
- [x] Complete all numbered steps with deterministic failure handling.
- [x] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [x] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- Supported daemon actual click calls expected message route while visible/tray.
- No actions/daemon clearly degrades to accessible inbox; daemon ID reuse cannot open stale account.
- Local reminder transport tests remain valid.

Future targeted verification (not executed by this plan):
- `On Linux: ./gradlew :desktopApp:test --tests "*LinuxRemoteNotificationAdapterTest" --tests "*LinuxNotificationAdapterTest" --dependency-verification=strict`
- `Live10/12: supported daemon Notify -> actual action click; restart daemon; test no-actions daemon and no tray.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- Session bus availability cannot be inferred in headless CI; live gate remains pending.
- Focus may be refused: ensure visible entry/window restore request and record compositor result honestly.

## Security Considerations
- Only opaque UUID/generation in native map; generic OS copy.
- Late signals fenced and remote cleanup never closes reminders.

## Next steps
- Consumers/unblocked phases: [P10](phase-10-desktop-runtime-integration.md), [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
