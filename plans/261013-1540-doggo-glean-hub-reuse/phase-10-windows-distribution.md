# Phase 10 — Native Windows DPAPI and installed MSI

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Strict A protocol](reports/strong-sync-design-feasibility.md) · [Isolation and rollback evidence](reports/strong-sync-isolation-feasibility.md).
- [Historical platform/theme evidence](../261013-0914-doggo-compose-todos/phase-10-windows-distribution.md) — only setup/signing/design/packaging reused; old backend/session defaults superseded.

## Parallelization Info
Group G after passing09 native gate; actual interactive Windows host mandatory. Own exclusive Windows provider/descriptor/test/gradle-windows.lockfile/PowerShell/docs/evidence. Do not edit09 Gradle/entrypoint/Linux lock/descriptor; build already selects Windows.02 original owner reviews legitimate verification metadata;08 general-doc updates.

## Overview
- Date: 2026-10-13
- Description: Native Windows DPAPI and installed MSI.
- Priority: P2
- Implementation status: pending (0%)
- Review status: parent plan review complete; implementation/runtime acceptance pending.
- Effort: 6h estimated; total plan148h,uncertainty ±25–35%.

## Key Insights
Manual native packageMsi build/install is Windows-only. Linux skipped task cannot pass. DPAPI user-bound bundle file/user ACL and packaged ServiceLoader need actual Windows tests; unsigned MSI must not be called trusted-signed. Runtime persistence remains offline established account,not clear-on-process-exit.

## Requirements
Actual supported Windows x64/JDK/jpackage/WiX tuple and interactive machine; real user-scoped DPAPI,no machine scope/plaintext fallback,atomic file replacement; native strict target lock,installed self-contained MSI/common feature parity/accessibility/DPAPI/isolation/uninstall-reinstall. No CI/auto updater/public publisher certificate added.

## Architecture
10 contributes src/windowsMain real WindowsDpapiVaultProvider and descriptor through09 host-matched target selection; base MainKt/dispatcher/build unchanged. Windows main compilation excludes src/linuxMain/D-Bus provider.10 dependencyLocking writes only gradle-windows.lockfile,never Linux lock. DPAPI protected bundle at restricted per-user application-data path includes opaque account identity/token pair; reused05 JVM Room/session/wipe handling.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/doggo/desktopApp/gradle-windows.lockfile` — create.
- `/home/loidinh/WS/doggo/desktopApp/src/windowsMain/kotlin/dev/doggo/desktop/windows/windows-dpapi-vault.kt` — create.
- `/home/loidinh/WS/doggo/desktopApp/src/windowsMain/resources/META-INF/services/dev.doggo.desktop.CredentialVaultProvider` — create.
- `/home/loidinh/WS/doggo/desktopApp/src/windowsTest/kotlin/dev/doggo/desktop/windows/windows-dpapi-vault-test.kt` — create.
- `/home/loidinh/WS/doggo/scripts/windows-package-smoke.ps1` — create.
- `/home/loidinh/WS/doggo/docs/windows-desktop-guide.md` — create.
- `/home/loidinh/WS/doggo/docs/windows-acceptance.md` — create.
- `/home/loidinh/WS/doggo/docs/evidence/windows-test-summary.md` — create.
- `/home/loidinh/WS/doggo/docs/evidence/windows-empty.png` — create.
- `/home/loidinh/WS/doggo/docs/evidence/windows-populated.png` — create.
- `/home/loidinh/WS/doggo/docs/evidence/windows-completed.png` — create.
- `/home/loidinh/WS/doggo/docs/evidence/windows-editor.png` — create.
- `/home/loidinh/WS/doggo/docs/evidence/windows-error.png` — create.
- `/home/loidinh/WS/doggo/docs/evidence/windows-conflict.png` — create.
- `/home/loidinh/WS/doggo/docs/evidence/windows-expanded.png` — create.

## File Ownership
Exclusive phase10 owner of all 15 exact absolute paths above; those and only those phase10 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer context links are read-only. New tracked path requires reviewed registry amendment; integration reopens original owner.

## Implementation Steps
1. On actual Windows record OS edition/build/CPU x64,interactive access,approved source commit. Use trusted Git/archive transfer without secrets/private DB/signing material. `Get-Command java,javac,jpackage`; `java -version`; `jpackage --version`; record JAVA_HOME exact compatible02/09 tuple. Official checksum-verified prerequisite installs only.
2. Use selected vendor JDK Windows jpackage prerequisite docs to choose supported WiX/executables; for actual WiX3 tuple `Get-Command candle,light`,otherwise actual supported tools. Do not assume newest WiX or disable policy. If root Android configuration needs SDK,provision exact verified command-line SDK/platform/build-tools/licenses without Studio/emulator; local SDK path untracked,not editing root scripts.
3. Implement windows-dpapi-vault.kt exposing exact dev.doggo.desktop.windows.WindowsDpapiVaultProvider plus real CredentialVault. CryptProtectData/CryptUnprotectData user scope via02 JNA dependency; no CRYPTPROTECT_LOCAL_MACHINE,no UI/log secret. Atomically replace protected pair file with restrictive user ACL; cleanup native buffers safely and delete file on guarded logout. Wrong Windows user/corrupt bundle returns explicit recoverable sign-in/locked state preserving DB,no fallback/no auto-wipe.
4. Create exact Windows META-INF/services descriptor and windowsTest source; no09 file touched. Native vault tests actual DPAPI roundtrip/replacement/deletion/corruption/scope and ACL verification. Different-user inability requires real isolated second Windows user/operator test,not mock user claim. Public-session metadata and account wipe05 reused. Test selected provider exactly one and no Linux/D-Bus classes/resources.
5. 10 owner bootstraps Windows-specific lock through frozen custom lockFile logic: `.\gradlew.bat -PdesktopTarget=windows :desktopApp:dependencies --write-locks`;02 reviews any legitimate shared/platform verification additions. Strict repeat `.\gradlew.bat -PdesktopTarget=windows :desktopApp:test :shared:allTests :desktopApp:createDistributable :desktopApp:packageMsi` with task equivalences02. Actual native tasks execute,no skipped success. Linux gradle-linux.lockfile unchanged; mismatched -PdesktopTarget=linux on Windows fails before native resolution.
6. Permanent PowerShell script explicit MsiPath,current expected product/version/arch, `Get-FileHash -Algorithm SHA256 $MsiPath`; `Get-AuthenticodeSignature $MsiPath`; record unsigned status honestly and trusted source/hash/user acknowledgement. `& .\scripts\windows-package-smoke.ps1 -MsiPath $MsiPath` according to existing trusted organizational execution policy,no weakening/Defender disable/automatic privilege escalation.
7. Install actual MSI `Start-Process msiexec.exe -ArgumentList @('/i',('"{0}"' -f $MsiPath)) -Wait -PassThru`; inspect0/3010 and reboot disposition. Discover ProductCode/version/per-user install/Start menu launcher using supported MSI API or install metadata,no Win32_Product sweep or guessed Program Files path. Start ordinary user from clean environment without developer JAVA_HOME/JDK path; verify bundled JRE/resources/MainKt/Windows descriptor/provider.
8. Installed approved HTTPS app real signup/access pending/approval-refresh/login,two-device Android/Linux strict sync,CRUD/count/stable rowId paging/permanent tombstones/offline restart/expired auth/known grant denial/unknown push/refresh. Verify exact explicit replay acceptance, UnknownSuperseded distinct from known conflict, equal content never acknowledges, same-epoch missing confirmed identity pauses corruption/reset, changed epoch retains old work for explicit review and no legacy fallback. Shared protocol parity through frozen02/04/05/06 implementation; no cross-owned files edited. Leave cancel-sync-discard/account isolation preserved. Session token only DPAPI,not SQLite; app close/restart retains established queue. Guarded leave wipes files; failure blocks next account. Second launch profile guard prevents duplicate refresh. HTTP/CLI/env/property mode override rejected; TLS default/no redirects.
9. Inspect compact/expanded windows,100%/150%/200% scaling,text enlargement,Unicode/Vietnamese/emoji,shared fonts/license view,keyboard/focus,actual Windows Narrator labels/roles/completion. Capture real sanitized empty/populated/completed/editor/error/conflict/expanded screenshots and OS/runtime/commit/MSI/provider/vault test summary. Human UI checklist complements script,not PID/file-exists proof.
10. Uninstall via actual ProductCode `Start-Process msiexec.exe -ArgumentList @('/x',$ProductCode) -Wait -PassThru`, inspect actual removal/reboot disposition with user approval; reinstall same verified MSI and startup/HTTPS/vault-isolation smoke. Do not promise uninstall securely erases per-user data; explicit in-app guarded logout before removal when deletion intended. Same-version reinstall is not future upgrade proof; stable09 upgradeUuid stays frozen.
11. Return Windows native acceptance to parent and08 doc owner; original02/04/05/06/09 owners repair their defects. Routine10 addition requires no09 edits and leaves Linux build target selection unchanged. Overall implementation completes only after required native evidence; unavailable machine is explicit pending prerequisite,not canceled platform.

## Todo list
- [ ] Implement every owned artifact/requirement with real behavior,not scaffolds.
- [ ] Pass designated future command and actual runtime gates; record sanitized evidence.
- [ ] Resolve failures through original owner and complete parent review; do not mark implementation accepted early.

## Success Criteria
Real Windows DPAPI file/user-scope/atomic replacement tests and packaged exactly-one Windows provider; native locked packageMsi executes; installed release runs without developer JDK with durable/shared/offline/auth/sync/account/UI parity. Actual Narrator/scale/provider/installer identity/hash/signature status and uninstall/reinstall evidence complete.09 artifacts untouched by routine10 addition.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
Hardware/architecture/toolchain/WiX/install policy/DPAPI ACL/native module/Skiko/font support may block. Windows verifier hashes may reopen02 only; frozen09 mechanism must already support source/resource/lock isolation. No substitutes from Linux.

## Security Considerations
DPAPI user scope/restricted file ACL,no plaintext,machine scope or password persistence. Approved origin/mode,no secrets transferred/logged/PowerShell arguments. Unsigned MSI disclosed; no security-software disable. Native DPAPI failure preserves local pending work.

## Next steps
Parent final architecture/implementation drift review and08-only final general docs/status after actual all-platform gates. No implementation performed now.

## Unresolved questions
Actual Windows workstation/interactive access,exact supported JDK/WiX tuple,second-user DPAPI test account and native install trust policy remain implementation prerequisites.
