# Phase 10 — Native Windows MSI on later workstation

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Linux packaging/configuration](phase-09-linux-distribution.md) · [Release/security](phase-08-deployment-security-release.md)
- [Compose native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)

## Parallelization Info
Group G after complete09; actual later Windows machine is mandatory. No native Windows build/install from Linux. Own Windows PowerShell/runbook/evidence only.09 reopens desktop build/lock for Windows corrections;02 verification metadata/catalog,08 general docs. Unavailable hardware leaves this gate pending, not cancelled/completed by Linux evidence.

## Overview
- Date: 2026-10-13
- Description: Build, install and inspect real Windows MSI with shared feature parity.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 4h

## Key Insights
Native installer builds are OS-specific; skipped packageMsi on Linux proves nothing. Bundled Windows Skiko/JRE/font/TLS, scaling and keyboard differ. WiX requirement depends on the chosen JDK jpackage; do not blindly mandate obsolete/incorrect WiX3/4 tuple. Desktop launcher remains literal RELEASE from09.

## Requirements
Supported actual Windows OS/architecture/JDK/packager prerequisites recorded, real native MSI build/install/uninstall with version/upgrade identity, actual GUI CRUD/HTTPS/error/conflict/lifecycle/accessibility/resize evidence and self-contained runtime. No Linux cross-compilation, generic Java JAR substitute or unsigned-installer false trust claim.

## Architecture
Same dev.doggo.desktop.MainKt, shared UI/controller/resources and frozen09 TargetFormat.Msi package configuration. Windows installed launcher RELEASE only; runDebug optional developer task, not an installer switch. Backend already deployed08; test data unique/sanitized. Manual workstation release is proposed default, CI not silently added.

## Related code files
Future create only, no existing application files modified/deleted:
- `scripts/windows-package-smoke.ps1`
- `docs/windows-desktop-guide.md`
- `docs/windows-acceptance.md`
- `docs/evidence/windows-empty.png`
- `docs/evidence/windows-populated.png`
- `docs/evidence/windows-completed.png`
- `docs/evidence/windows-editor.png`
- `docs/evidence/windows-error.png`
- `docs/evidence/windows-expanded.png`

## File Ownership
Only phase10 exact registry rows. Consumer reads desktop source/config/lock without editing them. Windows-specific native dependency/checksum changes reopen09/02; general README/changelog/status08. MSI/install logs/transient signing/runtime outputs ignored/local, no unowned workflow/catalog/manifest/icon files.

## Implementation Steps
1. On actual workstation record Windows edition/build, CPU architecture and interactive desktop access; baseline Windows x64 supported by selected JVM/Compose/Skiko. If different arch, verify native artifact/JDK/packager support with09/02 before work, no copying Linux natives. Transfer exact approved repository commit through trusted Git/source archive, no keys/.dev.vars/private data. Use PowerShell and selected compatible JDK matching02/09 major/runtime constraints; verify `java -version`, `javac -version`, `jpackage --version`, `$env:JAVA_HOME`. Target machine running installed app must not require JDK.
2. Read that JDK vendor/version's Windows jpackage prerequisites; install the supported WiX/toolchain version from official verified source, record checksum/version/PATH and license. For tuples requiring WiX3 verify `Get-Command candle, light`; for newer supported tooling use documented executable names. Do not install random latest WiX assuming compatibility. Resolve Java/Gradle/Compose/WiX tuple with02/09 evidence. Native desktop build does not inherently need Android tooling, but root includes androidApp: provision selected command-line SDK/compile/build-tools/licenses if AGP configuration requires them, using official Windows downloads and interactive license review; no Studio/emulator requirement just to build desktop. Keep local.properties/environment machine-local.
3.09 owner reviews/updates Windows entries in desktop lock;02 owner reviews platform verification metadata. Bootstrap locking only under those owners, then locked Windows build from repo: `.\gradlew.bat :desktopApp:test :desktopApp:createDistributable :desktopApp:packageMsi`. Verify actual tasks executed (not SKIPPED) and native Windows dependencies. Config stays MainKt/RELEASE, version1.0.0 and09 frozen upgradeUuid. Do not invent an upgrade pass from same-version reinstall; a future real version increment/release needs09 owner and separate upgrade test.
4. Implement permanent smoke PowerShell: explicit `-MsiPath` parameter, require exactly one actual generated MSI and expected product/version/arch; `Get-FileHash -Algorithm SHA256 $MsiPath`, `Get-AuthenticodeSignature $MsiPath`. Record unsigned installer honestly; manual private distribution requires trusted source/hash/user acknowledgement, not security bypass/disabled Defender. Public trusted distribution would require user-approved certificate/signing custody and scope revision, not a pretend signature. No execution-policy weakening from script; invoke trusted local script per organizational policy.
5. Native install: from actual `$MsiPath`, `Start-Process msiexec.exe -ArgumentList @('/i', ('"{0}"' -f $MsiPath)) -Wait -PassThru`, inspect exit0/3010 and actual installation (3010 requires restart disposition). Smoke script records MSI ProductCode/version through supported MSI API/installation metadata, discovers installed launcher/Start menu entry and actual per-user path, handles spaces with literal paths. No guessed Program Files path, Win32_Product sweep or silent privilege escalation. Start installed Doggo as ordinary user. Verify launcher/resources/bundled runtime, then run from fresh shell without developer JAVA_HOME/PATH dependence.
6. Against deployed HTTPS/token manually entered masked: actual create/rename/complete/reopen, all filters/count/page, edit/delete cancel/confirm, wrong token401 and new-entry flow, network loss/stale/retained draft/explicit reconciliation. Android or API second client edits same version ->409 conflict retained, refresh/reapply/cancel. Close/relaunch/force-stop -> session/token gone, late callbacks cannot repopulate. Use no real private titles in evidence. Existing07/08 backend fixture/security scripts may be consumed unchanged; defects return original owners.
7. Installed MainKt must reject local HTTP127.0.0.1/localhost/10.0.2.2 and arbitrary LAN HTTP before sending credentials; debug-looking CLI/property/env values cannot change mode. Deployed HTTPS uses normal Windows/JVM trust; invalid/untrusted certificate must not be accepted, no added release trust store bypass. Verify installed launcher config targets MainKt. Optional local development uses09 runDebug plus private Windows Worker loopback, distinct dev token/ignored state; it is not installed-MSI acceptance and not required to repeat Fedora headless setup.
8. Actual GUI inspect compact/expanded >=840dp, resize, 100%/150%/200% Windows scaling, text enlargement, long Unicode/Vietnamese/emoji, font licenses/resources and visible keyboard focus/Tab/ShiftTab/Enter/Escape. Use Windows Narrator or actual supported screen-reader tool; record announced roles/labels/completion and limitations truthfully. Capture real empty/populated/completed/editor/error/expanded screenshot files with local GUI capture without secret form/notifications. Record OS/arch/runtime/commit/MSI hash/device dimensions and executed test outcomes in Windows acceptance doc.
9. Uninstall through Apps settings or actual discovered ProductCode: `Start-Process msiexec.exe -ArgumentList @('/x', $ProductCode) -Wait -PassThru`; inspect exit/result and launcher removal, no unrelated product removal. Reinstall same verified MSI and repeat startup/token-not-retained/HTTPS smoke. This tests install lifecycle, not future-version upgrade. Freeze stable upgradeUuid for future releases. Script exits nonzero on failed install/metadata/process checks; human UI checklist/evidence required, not process-only success.
10.09/02/05/06 owners repair any package/transport/UI defects; rerun affected permanent tests plus Windows actual gate. Reopen08 for final README/summary/roadmap/changelog platform status only after native evidence complete. Record all acceptance artifacts; do not mark a release complete merely because MSI file exists.

## Todo list
- [ ] Record real Windows/JDK/WiX/SDK prerequisites and native locked build.
- [ ] Install actual MSI and pass real HTTPS/security/lifecycle/accessible UI scenarios.
- [ ] Capture evidence, verify uninstall/reinstall and update08-owned general status through owner.

## Success Criteria
Actual Windows packageMsi builds native installer; real installed app runs without developer JDK, full shared CRUD/error/conflict/page/lifecycle parity, correct fonts/resources/scale/keyboard/screen-reader inspection. HTTP/property override rejected, tokens never persisted/logged. Installer hash/signature status/identity and uninstall/reinstall recorded. Only then10 and overall implementation may complete; future upgrade/publisher signing not claimed exercised.

## Conflict Prevention
No desktop build/lock edits by10. Reopen09 for Windows DSL/upgrade/native dependency configuration,02 for metadata/catalog,05/06 behavior,08 general docs. New tracked resources/scripts need exclusive registry amendment before creation.

## Risk Assessment
Unavailable hardware, architecture mismatch, WiX/JDK mismatch, SDK root configuration, MSI policy/permissions/SmartScreen and high-DPI/Skiko can block. Resolve supported tuple and trusted package provenance; no disabling security software or replacing native gate with Linux build.

## Security Considerations
No cloud/app/signing secrets shipped during transfer or placed in PowerShell arguments/logs. Session process-only; installed launcher immutable RELEASE with normal TLS validation. Trusted official prerequisite downloads; unsigned MSI disclosed, no signing claim. Sanitized screenshots and user-approved install/uninstall scope.

## Next steps
Return full actual native acceptance to parent reviewer and08 doc owner; compare implementation/architecture through02. Overall release complete only when all required platform gates passed; absence of later machine is an explicit remaining prerequisite, not scope removal.

## Unresolved questions
Actual Windows workstation OS/arch/JDK/WiX availability and local package trust policy remain implementation prerequisites. Manual release vs additional CI remains unapproved default; public distribution/signing not added without approval.
