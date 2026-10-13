# Phase 01 — Headless tooling and Android visual environment

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Selected strict-sync evidence](reports/strong-sync-feasibility.md) · [Mongo prerequisites](reports/mongodb-feasibility-evidence.md) — application changes do not alter01 setup.
- [Historical platform/theme evidence](../261013-0914-doggo-compose-todos/phase-01-headless-tooling.md) — only setup/signing/design/packaging reused; old backend/session defaults superseded.

## Parallelization Info
Group A; first implementation phase, no predecessors. Host setup only; no application/module code. Requires authorized package access and graphical SSH Xpra client or authorized phone.

## Overview
- Date: 2026-10-13
- Description: Headless tooling and Android visual environment.
- Priority: P2
- Implementation status: completed (100% - 2026-10-13)
- Review status: passed (2026-10-13)
- Effort: 6h completed

## Key Insights
Fedora remains headless. Prior KVM observation is historical, not proof of current permissions or Xpra graphics. Java runtime and bytecode target differ; do not globally replace the host JDK.

## Requirements
Studio plus native emulator visible through SSH-only Xpra; verified stable downloads, SDK/license inventory, bounded boot check, explicit ADB serial. No GNOME/KDE install, public Xpra listener, legacy adb tcpip5555, blanket licenses or secret files.

## Architecture
Per-user Xpra virtual display :100 and xterm carry display/auth to Studio/emulator. SDK at $HOME/Android/Sdk; one AVD doggo_android, emulator-5554. Python/Node scripts used later require actual supported runtime inventory; select Node24 LTS exact patch/checksum for .mjs gates. Generated setup state stays outside Git.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/doggo/scripts/start-xpra.sh` — create.
- `/home/loidinh/WS/doggo/scripts/android-sdk-env.sh` — create.
- `/home/loidinh/WS/doggo/scripts/start-android-emulator.sh` — create.
- `/home/loidinh/WS/doggo/docs/headless-android-setup.md` — create.
- `/home/loidinh/WS/doggo/docs/host-tooling-evidence.md` — create.
- `/home/loidinh/WS/doggo/docs/evidence/android-tooling-launcher.png` — create.

## File Ownership
Exclusive phase01 owner of all 6 exact absolute paths above; those and only those phase01 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer context links are read-only. New tracked path requires reviewed registry amendment; integration reopens original owner.

## Implementation Steps
1. Re-observe `command -v xpra xterm adb emulator java node`; `test -r /dev/kvm && test -w /dev/kvm`; inspect `sudo dnf info xpra xterm java-21-openjdk-devel`. Authorized install only after reviewing transaction. Validate Studio Linux native prerequisites from current official release docs. No chmod system state/KVM or desktop boot-target change.
2. Verify SSH host key through trusted channel. Server `xpra start :100 --start-child=xterm --exit-with-children=no --html=off`; graphical client `xpra attach ssh://loidinh@<verified-host>/100` using actual verified host/port. `xpra list` and `xpra stop :100` document teardown. Script rejects public bind options; no bind-tcp.
3. Download stable Studio from https://developer.android.com/studio, compare official checksum, unpack to user-owned $HOME/.local/share/android-studio; launch actual shipped studio executable from Xpra xterm as ordinary user. Complete wizard interactively. Native screenshot must show usable Studio/emulator, not headless boot alone.
4. Generate disposable current-stable KMP Android+JVM project outside repository for02 compatibility proof. Record candidate Kotlin/Compose/AGP/Gradle/JDK/Room/KSP/SQLite/SDK versions, not an invented pinned tuple. Baseline min26, bytecode17; runtime selected by demonstrated compatibility.
5. Environment script sets ANDROID_HOME=$HOME/Android/Sdk, command-line tools/platform-tools/emulator PATH and explicit compatible JAVA_HOME. `sdkmanager --list`; choose actual stable API, BUILD_TOOLS and IMAGE_API then `sdkmanager "platform-tools" "emulator" "platforms;android-$API" "build-tools;$BUILD_TOOLS" "system-images;android-$IMAGE_API;google_apis;x86_64"`. Read `sdkmanager --licenses` interactively. Never commit local.properties.
6. Choose DEVICE_ID from `avdmanager list device`; `avdmanager create avd --name doggo_android --package "system-images;android-$IMAGE_API;google_apis;x86_64" --device "$DEVICE_ID"`; `emulator -accel-check`; `emulator -avd doggo_android -port 5554 -gpu auto` inside Xpra. If observed GL failure, inspect `emulator -help-gpu` and use documented software renderer; no assumed GPU diagnosis/deprecated default.
7. `adb -s emulator-5554 wait-for-device`; bounded poll of `adb -s emulator-5554 shell getprop sys.boot_completed` must reach1. Inspect real launcher/graphics. Authorized USB alternative selects actual serial via `adb devices -l` and phone RSA prompt; modern wireless uses interactive `adb pair` then `adb connect`, trusted network only. App execution waits07.
8. Record versions/package revisions/licenses/KVM/renderer/SSH exposure/native visual evidence in owned docs and android-tooling-launcher.png. Record executable scripts using actual selected runtime paths; no placeholder success. Keep one emulator plus Studio within available RAM.

## Todo list
- [x] Implement every owned artifact/requirement with real behavior, not scaffolds. (2026-10-13)
- [x] Pass designated future command and actual runtime gates; record sanitized evidence. (2026-10-13)
- [x] Resolve failures through original owner and complete parent review; do not mark implementation accepted early. (2026-10-13)

## Success Criteria
Usable SSH-only Xpra terminal and Studio; real emulator visually booted or authorized physical visual session ready; exact tool/license evidence and sanitized native screenshot. Disposable tuple candidate supplied02. No app build/install now.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
Package permission, graphical client, GL/KVM/device authorization may block. Approved software renderer or phone is alternative; no public GUI/ADB exposure to bypass blockers.

## Security Considerations
Trust SSH keys; user-only sockets; official checksums/license review. No secrets in scripts/evidence. Sudo only reviewed package transactions, not Studio/emulator.

## Next steps
02 consumes host evidence and disposable candidate. Gate remains incomplete until native visual access exists.

## Unresolved questions
Actual graphical client, authorized package access and device selection are environment prerequisites; resolve during01, not by weakening transport.
