# Phase 01 — Headless tooling and Android environment

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Authoritative decisions](reports/design-decisions.md) · [Planning brief](reports/planning-brief.md)

## Parallelization Info
No phase dependencies; group A. Setup first; no package/source lane until02 freezes tuple. Requires sudo authorization or administrator-assisted installs.

## Overview
- Date: 2026-10-13
- Description: Headless tooling and Android environment.
- Priority: P2
- Implementation status: DONE (2026-10-13)
- Review status: passed (2026-10-13)
- Effort: 4h

## Key Insights
Host KVM API12 already verified; GPU through Xpra not verified. Java25 is not automatically wrong. Unsafe/superseded researcher01 commands must not be reused.

## Requirements
Keep Fedora server boot mode; no GNOME/KDE, public Xpra port, legacy adb tcpip5555 or blanket license acceptance. Android Studio GUI and emulator must be usable through SSH Xpra.

## Architecture
SSH transports virtual X11 display :100; Xpra child xterm supplies display/auth to Studio/emulator. SDK under $HOME/Android/Sdk. Node24 LTS supplies node:sqlite test support; exact patch/version/checksum recorded.

## Related code files
Future create only; no existing source modified or deleted:
- `scripts/start-xpra.sh` — create.
- `scripts/android-sdk-env.sh` — create.
- `scripts/start-android-emulator.sh` — create.
- `docs/headless-android-setup.md` — create.
- `docs/host-tooling-evidence.md` — create.

## File Ownership
Only exact paths listed above and phase01 rows in registry; exclude every other file. Machine-local generated state not tracked. Reopen original owner for out-of-scope changes.

## Implementation Steps
1. Re-observe installed tools and permissions; `command -v xpra xterm adb emulator java node`, `test -r /dev/kvm && test -w /dev/kvm`; use `sudo dnf info xpra xterm java-21-openjdk-devel` (do not chmod DNF system state). Review package transactions, then `sudo dnf install xpra xterm java-21-openjdk-devel`; use vendor-supported Node24 LTS install with checksum and record patch. Do not globally replace Java25. Read current Studio/SDK Linux requirements and package any missing native libraries via Fedora names confirmed by DNF.
2. Verify SSH host key from trusted administrator channel. Server: `xpra start :100 --start-child=xterm --exit-with-children=no --html=off`; no bind-tcp, socket directory user-only. Client on graphical workstation: `xpra attach ssh://loidinh@<verified-host>/100`; adjust actual SSH port via client documented syntax. A local graphical Xpra client can attach `:100`. If workstation lacks Xpra, install supported client or private SSH-forwarded authenticated alternative only after security review—not public HTML5. Check `xpra list`; `xpra stop :100` for teardown. Script encapsulates these safe flags, never installs a desktop/changes systemd default target.
3. Download stable Android Studio Linux archive from https://developer.android.com/studio ; verify official checksum, unpack to user-owned `$HOME/.local/share/android-studio` (record actual version). In **Xpra xterm**, launch `$HOME/.local/share/android-studio/bin/studio` (inspect shipped executable; some versions use studio.sh). Do not set DISPLAY alone or run Studio as root. Complete first-run SDK wizard interactively.
4. Follow current KMP wizard in Studio or https://kmp.jetbrains.com/ with Android + Desktop. Create a disposable compatibility project outside repository. Proposed starting policies: Android min26, Java/Kotlin bytecode17, compiler plugin equals Kotlin, shared modern Android library plugin, Gradle runtime JBR/compatible JDK. Record candidate exact versions; phase02 must prove selected AGP/Gradle/JDK/Kotlin/Compose/SDK tuple before pinning. Ranges/old researcher versions are not proof.
5. Install command-line tools via Studio SDK manager or verified official ZIP arranged `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager`. Environment script: `export ANDROID_HOME="$HOME/Android/Sdk"`; `export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"`. Set JAVA_HOME to selected compatible JBR/LTS directory explicitly; record runtime version, not bytecode target. Never commit local.properties.
6. Choose `API`, `BUILD_TOOLS`, `IMAGE_API` matching selected stable wizard compile/target SDK and available x86_64 image; verify `sdkmanager --list`. Example executable form after assigning measured selections: `sdkmanager "platform-tools" "emulator" "platforms;android-$API" "build-tools;$BUILD_TOOLS" "system-images;android-$IMAGE_API;google_apis;x86_64"`. `sdkmanager --licenses` requires interactive reading/review. Record exact package revisions and decisions; not blanket `yes`.
7. `avdmanager list device`; select actual listed device ID (baseline `pixel_6` only if present) as `$DEVICE_ID`; `avdmanager create avd --name tabbyrush_android --package "system-images;android-$IMAGE_API;google_apis;x86_64" --device "$DEVICE_ID"`. Run `emulator -accel-check`; do not chmod /dev/kvm, adjust approved group membership/re-login if necessary. Initial emulator in Xpra terminal: `emulator -avd tabbyrush_android -port 5554 -gpu auto`. Hardware mode first; if visual/GL failure inspect `emulator -help-gpu`, select documented `software` or `swiftshader`; never baseline deprecated swiftshader_indirect. Record actual result, not assumed Radeon failure.
8. In second Xpra terminal `adb -s emulator-5554 wait-for-device`; inspect `adb -s emulator-5554 shell getprop sys.boot_completed` until1 with bounded wait, inspect real launcher visibly, record acceleration/renderer/emulator revision. No-window boot alone cannot pass visual gate. Keep Studio+one AVD within memory; stop unused AVDs.
9. Physical alternative: enable developer USB debugging, inspect authorization RSA prompt on phone, select actual serial from `adb devices -l` as `$ANDROID_SERIAL`. Modern wireless: `adb pair PHONE:PAIR_PORT` interactive code, then `adb connect PHONE:CONNECT_PORT` on trusted network; no `adb tcpip`. Use USB `adb -s "$ANDROID_SERIAL" reverse tcp:8787 tcp:8787` for local Worker; reverse removed after smoke. App install/launch waits07.
10. Record actual commands/versions, SSH-only exposure, boot/visual evidence and troubleshooting in owned docs. Host evidence may say KVM previously verified; new Xpra/emulator graphics must be observed. Phase01 is incomplete if no usable emulator/authorized physical visual session.

## Todo list
- [x] Complete requirements and owned implementation/runbooks. (2026-10-13)
- [x] Record designated gate evidence; all failures resolved through original owners. (2026-10-13)
- [x] Review frozen contract/ownership compliance; no runtime placeholders. (2026-10-13)

## Success Criteria
Xpra SSH terminal and Studio visible; SDK packages/licensing recorded; real AVD boots and is visually inspected or authorized USB device ready; no GUI desktop/public port. Scripts contain actual resolved executable/package choices, not placeholders.

## Conflict Prevention
No edits outside listed rows, including catalog/build/lock/docs/tests. New paths require registry amendment first. Integration defects reopen owner, then rerun gate.

## Risk Assessment
Xpra client absent, package rights, Studio download, GPU mode or device authorization can block; use documented software renderer/USB alternative without weakening security. Exact tuple proof is02 gate.

## Security Considerations
Trust SSH host keys; per-user sockets. Review licenses; validate downloads. Never expose Xpra/ADB/Worker LAN ports. No credentials in scripts.

## Next steps
Proceed02 with host evidence and disposable wizard candidate; keep emulator serial emulator-5554/AVD tabbyrush_android consistent.

## Unresolved questions
Actual graphical workstation client and install permissions/device choice; resolve from environment/administrator access during implementation.
