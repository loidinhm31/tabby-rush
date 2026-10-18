# Phase 08 — Windows C++/WinRT bridge, JNA and MSI identity

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **B**; implementation start: [P01](phase-01-contract-build-gate.md).
- Additional runtime/acceptance dependencies: [P05](phase-05-shared-remote-core.md), [P10](phase-10-desktop-runtime-integration.md), [P11](phase-11-operations-config-deployment.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P08**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented-native-held; review: approved; status: in-progress; progress: 100% implementation (runtime acceptance held); effort: **36h**.
- Description: Windows C++/WinRT bridge, JNA bindings and MSI identity implemented; non-Windows stub tests pass; physical Windows native execution and MSI packaging held at announcement Gate G5 per restricted pilot staged rollout.

## Key Insights
- Current Jna name is a PowerShell typealias in source, not proof of working native activation.
- Owned native compiler/build/install path is part of delivery, not optional spike.

## Requirements
- Implement exact real C ABI+JNA callback and correct lifetime, AUMID and clean-host installed MSI.
- No AWT/PowerShell fallback, fake bridge, blanket history clear or unmeasured DLL size claim.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Owned C++/WinRT DLL runs notifier on MTA thread; C ABI handles retain event revokers and callback state. Kotlin pins JNA callback until drain/destroy. WiX3 per-user MSI installed shortcut sets System.AppUserModel.ID. Desktop runtime host wiring stays10.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/tabby-rush/desktopApp/build.gradle.kts` | modify | Sole desktop build owner: native DLL resources, Windows MSI task and both platform dependency wiring. |
| `/home/loidinh/WS/tabby-rush/desktopApp/gradle-windows.lockfile` | modify | Windows lock resolution only owner; request checksum additions from01. |
| `/home/loidinh/WS/tabby-rush/desktopApp/gradle-linux.lockfile` | modify | Linux lock resolution only owner for any build requests from07. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-toast-bridge.kt` | modify | Replace PowerShell/typealias with real JNA native handle/callback and typed local methods. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt` | modify | Migrate all local reminder wrapper calls to typed native functions, remove global clearing/dead XML builders. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-remote-notification-adapter.kt` | create | RemoteNotificationAdapter with generation callback/tag/group/lifetime mapping. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/fake-windows-toast-bridge.kt` | modify | Migrate fixture to actual typed local bridge contract, no PowerShell executor fiction. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter-test.kt` | modify | Replace obsolete PowerShell-specific tests with native API boundary/local reminder regression tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-remote-notification-adapter-test.kt` | create | Callback drain, ABI mismatch, tag collision and scoped clear tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/native/windows/CMakeLists.txt` | create | Real x64 Windows SDK/C++20 WinRT DLL build and native test target. |
| `/home/loidinh/WS/tabby-rush/desktopApp/native/windows/tabbyrush-toast.h` | create | Frozen C ABI v1 exported signatures and ownership comments. |
| `/home/loidinh/WS/tabby-rush/desktopApp/native/windows/tabbyrush-toast.cpp` | create | Dedicated MTA WinRT implementation, real Activated handlers and HRESULT boundary. |
| `/home/loidinh/WS/tabby-rush/desktopApp/native/windows/tabbyrush-toast-test.cpp` | create | Native XML escaping, handler/drain and command-error tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/packaging/windows/installer.wxs` | create | Owned per-user MSI WiX3 installer/shortcut AUMID identity preserving upgrade UUID. |
| `/home/loidinh/WS/tabby-rush/scripts/build-windows-remote-msi.ps1` | create | Build x64 DLL and Compose app image, heat/candle/light MSI using pinned local toolchain. |
| `/home/loidinh/WS/tabby-rush/scripts/windows-package-smoke.ps1` | modify | Verify installed shortcut/AUMID, DLL ABI/load and native activation without skipped assertions. |
| `/home/loidinh/WS/tabby-rush/desktopApp/build/native/windows/Release/tabbyrush-toast.dll` | generate | Native output handoff; generated, not source-controlled binary. |
| `/home/loidinh/WS/tabby-rush/desktopApp/build/windows-installer/application-files.wxs` | generate | Heat generated app-image file manifest; exclude from source control. |
| `/home/loidinh/WS/tabby-rush/desktopApp/build/compose/binaries/main/msi/TabbyRush-1.0.0.msi` | generate | Canonical per-user MSI artifact with correct shortcut; not checked in. |

## File Ownership
- P08 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Prove toolchain first on Windows x64: MSVC C++20/Windows SDK C++/WinRT, JDK21 and WiX3 heat/candle/light. Build actual small native source against SDK, not downloaded opaque bridge. Missing toolchain blocks native gate, not scope.
2. Implement header ABI version1 and all functions in03 including typed local reminder display. Catch C++ exceptions ->HRESULT, copy input buffers, initialize WinRT on owned MTA worker and serialize bounded calls.
3. Implement real Activated registration per toast and native UUID/generation callback; revoke handlers/drain in-flight callbacks on destroy. Keep native handle on shutdown timeout, reject new work; never free before callback drain.
4. Implement JNA cdecl signatures, ABI/version/architecture verification, retained callback and UI-dispatch handoff. Expose unavailable diagnostics if missing DLL/OS setting; this is not accepted delivery or AWT fallback.
5. Replace PowerShell class/typealias and migrate existing notification wrapper/fixture/tests to typed local methods. Delete dead arbitrary XML builder and unused global cancellation methods; preserve reminder group behavior and no remote-reminder collision.
6. Implement tr-remote group, deterministic16char tags with collision mapping, process/generation capture, scoped clear and stale callback fencing. Normal quit clears group; no cold COM registration. Startup clears old remote group before stream; test click after quit/crash cannot expose content.
7. Own all desktop build changes; package DLL in application image with absolute app-owned load path, no unsafe PATH lookup. Add packageRemoteMsi task invoking owned build script/WiX source; standard packageMsi for Windows must fail with directed command until it produces identical identity-safe artifact, not silently ship wrong shortcut. Linux packaging unchanged.
8. WiX per-user installer uses existing upgrade UUID a6c507c8-88fe-4e49-8f43-a6125039281a, app-image files harvested into generated fragment, Start Menu shortcut plus MsiShortcutProperty System.AppUserModel.ID=dev.tabbyrush.desktop. Sign/review installer using existing release custody, no credentials in source.
9. Build native and JVM tests in owned files. Run installed clean-host smoke with real notification display and actual click while minimized/tray, callback intended UUID and window restoration request. Missing DLL test must show clear capability without misreporting success. Send build/ABI artifact hashes to10/12.

## Todo list
- [ ] Read owned files/current upstream changes and confirm frozen contract.
- [ ] Complete all numbered steps with deterministic failure handling.
- [ ] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [ ] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- Installed MSI on clean Windows displays actual generic toast and running Activated callback opens intended inbox.
- Native destroy guarantees no later callback on success; timeout retains fenced handle.
- Reminder behavior preserved with actual native API; no PowerShell/typealias/AWT or global clear remains.

Future targeted verification (not executed by this plan):
- `On Windows: cmake -S desktopApp/native/windows -B desktopApp/build/native/windows -A x64; cmake --build desktopApp/build/native/windows --config Release; ctest --test-dir desktopApp/build/native/windows -C Release`
- `On Windows: .\gradlew.bat :desktopApp:test --tests "*Windows*Notification*Test" --dependency-verification=strict`
- `On Windows: .\gradlew.bat :desktopApp:packageRemoteMsi --dependency-verification=strict; scripts/windows-package-smoke.ps1 against actual per-user install.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- MSI identity/SDK/COM activation feasibility is high-risk; run native install proof early in wave B.
- Native callback lifetime bugs can crash JVM; dedicated thread, strong references and explicit drain barrier mandatory.

## Security Considerations
- Load DLL from verified application resource path only; no shell XML/PowerShell injection.
- Callback identifiers authorize nothing; current detail fetch required; after quit no cold activation exposing account state.

## Next steps
- Consumers/unblocked phases: [P10](phase-10-desktop-runtime-integration.md), [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
