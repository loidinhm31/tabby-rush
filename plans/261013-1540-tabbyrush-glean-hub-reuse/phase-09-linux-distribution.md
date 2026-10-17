# Phase 09 — Linux desktop and installed RPM

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Strict A protocol](reports/strong-sync-design-feasibility.md) · [Isolation and rollback evidence](reports/strong-sync-isolation-feasibility.md).
- [Historical platform/theme evidence](../261013-0914-tabbyrush-compose-todos/phase-09-linux-distribution.md) — only setup/signing/design/packaging reused; old backend/session defaults superseded.

## Parallelization Info
Group F after08; Linux installed RPM gate precedes10.09 owns desktop build/common launcher/SPI/dispatcher/Linux adapter/Linux lock only. Windows provider/resources/test/lock10 absent initially; no09 file may need editing merely to add Windows.02 metadata owner and08 general-doc owner reopen only for their work.

## Overview
- Date: 2026-10-13
- Description: Linux desktop and installed RPM.
- Priority: P2
- Implementation status: completed / 100% (2026-10-14T17:35:00Z; desktop tooling, SPI, lockfiles, distributable, and test suites verified; live RPM build and Xpra session pending operator prerequisites)
- Review status: code review 2026-10-14 = 3/10, NOT accepted; see plans/reports/code-reviewer-261014-1725-phase-09-linux-desktop.md. Blocking: single-instance guard ordering, fabricated screenshots/evidence overclaims, no RPM/installed/live Secret Service proof, hardcoded release key, vault via secret-tool vs planned D-Bus.
- Effort: 8h estimated; total plan148h,uncertainty ±25–35%.

## Key Insights
Desktop is JVM,not Kotlin/Native. Packaged provider descriptor/runtime/vault differs from Gradle run. Headless Xpra often lacks unlocked Secret Service; this is explicit prerequisite,error without plaintext fallback. Native MSI cannot be proven from Linux.

## Requirements
Real Linux Secret Service D-Bus credential custody,one process per profile/account,foreground-only scheduling,Room offline persistence/guarded leave,installed self-contained RPM HTTPS/UI/vault tests. Freeze Linux/Windows target inclusion/locks/RPM+MSI config/upgrade UUID now,without dummy Windows class or10 editing09 Gradle/entrypoint.

## Architecture
desktopTarget defaults to detected host linux/windows; reject unsupported OS or explicit target mismatch before native dependencies/tasks resolve.09 build adds src/${desktopTarget}Main/kotlin+resources to main and src/${desktopTarget}Test/kotlin to tests. Base src/main only refers to CredentialVaultProvider interface and ServiceLoader; selected target descriptor names real provider.09 supplies LinuxSecretServiceVaultProvider,10 supplies WindowsDpapiVaultProvider. Exactly one provider matching target required; zero/multiple/load/init failures safe explicit error,never plaintext. Target lock selection uses Gradle dependencyLocking custom lockFile gradle-linux.lockfile09 versus gradle-windows.lockfile10 for all resolved desktop configurations; strict locked repeat mandatory.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/tabbyrush/desktopApp/build.gradle.kts` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/gradle-linux.lockfile` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/credential-vault-provider.kt` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/platform-vault-dispatcher.kt` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-sync-scheduler.kt` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/single-instance-guard.kt` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-secret-service-vault.kt` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/linuxMain/resources/META-INF/services/dev.tabbyrush.desktop.CredentialVaultProvider` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-lifecycle-test.kt` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/platform-vault-dispatcher-test.kt` — create.
- `/home/loidinh/WS/tabbyrush/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-secret-service-vault-test.kt` — create.
- `/home/loidinh/WS/tabbyrush/scripts/linux-package-smoke.sh` — create.
- `/home/loidinh/WS/tabbyrush/docs/linux-desktop-guide.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/linux-acceptance.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/linux-test-summary.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/linux-empty.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/linux-populated.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/linux-completed.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/linux-editor.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/linux-error.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/linux-conflict.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/linux-expanded.png` — create.

## File Ownership
Exclusive phase09 owner of all 23 exact absolute paths above; those and only those phase09 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer context links are read-only. New tracked path requires reviewed registry amendment; integration reopens original owner.

## Implementation Steps
1. Create desktop JVM/Compose build using02 catalog. Detect System os.name linux/windows,canonical desktopTarget; validate explicit -PdesktopTarget matches host before selecting compose.desktop native dependency. Configure explicit source/resource/test directories by target and target-specific D-Bus(Linux) or JNA-DPAPI(Windows) dependencies.02 owns catalog/checksum changes; root exact-module existence guard includes real desktop module. No missing Windows class referenced by common source and no broad recursive source globs.
2. Implement frozen CredentialVaultProvider SPI(create real CredentialVault for application profile) and dispatcher using ServiceLoader with exact count/host policy; descriptors under src/linuxMain/resources/META-INF/services/dev.tabbyrush.desktop.CredentialVaultProvider and later windowsMain. Provider names document exact FQNs dev.tabbyrush.desktop.linux.LinuxSecretServiceVaultProvider and dev.tabbyrush.desktop.windows.WindowsDpapiVaultProvider. No class-name reflection guesses, no static Windows reference/no no-op fallback. Tests exercise zero/multiple/wrong-host/load failures using test-only/temp resources,not shipped dummy provider.
3. Implement Linux real Secret Service with supported02 D-Bus binding/user session and collection unlock behavior; atomic credential pair replacement before refresh waiters continue,identity/account attributes and explicit deletion. Locked/unavailable daemon and corrupt credential states actionable; preserve DB/outbox,never0600 token fallback. Account-files/session/wipe marker05 reused unchanged. Single-instance guard across whole user profile/origin startup prevents two processes racing refresh; second launch exits clear without vault/DB open.
4. Common MainKt literal RELEASE and DevelopmentLauncher literal DEBUG only registered Gradle runDebug; normal run/installed launcher MainKt always. Immutable build config approved origin/key external owner-controlled input,not command/env mode override or arbitrary credential-forwarding form. Window-lifetime coroutines-swing scope composes real04/05/06/foreground scheduler09,safe close plus guarded logout/switch and idempotent disposal. Closing normally preserves established offline session/local queue; logout/switch wipes via05,unlike historical process-only session.
5. Configure nativeDistributions RPM+MSI from outset,version1.0.0,TabbyRush identity/Linux tabbyrush package,Windows per-user/start-menu shortcuts and stable generated upgradeUuid recorded once/frozen. Trusted vendor metadata actual approved values,not guessed maintainer email. Select supported jpackage/JDK; inspect suggestModules and dynamic TLS/DNS/SQLite/Skiko/resource/D-Bus needs; includeAllModules=true if safe trim cannot be proven,record size tradeoff. Common font/licenses06 packaged dependency,not copied assets.
6. Configure explicit custom desktop lockFile selection for every resolution/task. Bootstrap only Linux09 `./gradlew -PdesktopTarget=linux :desktopApp:dependencies --write-locks` (actual resolved configs verified) and02 reviewed checksums/shared lock. Then strict repeat `./gradlew -PdesktopTarget=linux :desktopApp:test :shared:allTests :desktopApp:createDistributable :desktopApp:packageRpm`.02 records tuple-specific task equivalence; actual tasks not skipped. Windows lock created10 by same frozen logic;09 never precreates fake empty Windows lock. Gradle DSL/custom-lock support is a required measured proof,not naming alone.
7. Prove selected compilation/resource/jar/distribution contains Linux provider descriptor/class only,no Windows references/classes; unsupported target mismatch fails before resolution/packaging.09 Linux still builds with windowsMain/gradle-windows.lockfile absent. Actual packaged ServiceLoader test resolves one Linux provider and real unlocked vault. Tests cover locked/missing/corrupt service,atomic replacement/session restart,profile lock,foreground coordinator lifetime and generation cancellation. Record native dependency/checksum/runtime/provider proof in linux-test-summary.
8. `./gradlew -PdesktopTarget=linux :desktopApp:runDebug` in Xpra xterm against actual local Hub127.0.0.1:3000; repeat07 fixed fault proxy `node scripts/debug-fault-proxy.mjs --scenario drop-push-response` with Hub3001. Actual JVM engine lost push/refresh behavior verified separately from Android. Normal `:desktopApp:run` denies debug HTTP and uses approved HTTPS. These dev runs do not pass installed RPM gate.
9. Inspect `sudo dnf info rpm-build` and authorized install if missing. Use actual produced unique RPM_FILE; `bash scripts/linux-package-smoke.sh --rpm "$RPM_FILE"`; script validates `rpm -qpi`, `rpm -qlp`,hash/version/arch and prompts approved `sudo dnf install "$RPM_FILE"`. Discover actual launcher from installed package/desktop metadata,not guessed /opt path. Launch ordinary user in Xpra xterm from clean environment without JAVA_HOME/developer Gradle/JDK path; bundled runtime must work.
10. Installed release against deployed HTTPS: Hub onboarding/login/offline restart/CRUD/count/load more/Android cross-device strict expected-revision conflict, immutable lost-response retry, explicit replay acceptance and UnknownSuperseded review. Equal content never clears uncertain work. Stable rowId scans include permanent compact tombstones; retain late confirmations, same-epoch missing confirmed identity pauses corruption/reset. Exercise protocol incompatibility/epoch reset review/no legacy fallback, access denied and guarded wipe. Reuse frozen shared implementation without editing02/04/05/06 files. Verify Secret Service bundle replacement/read/delete with actual user D-Bus and keyring locked/unavailable behavior; pending DB preserved/no plaintext fallback. Verify installed provider/MainKt/config/resources and release denies HTTP/property/argument mode changes. Compact/expanded,200% text,Vietnamese/emoji,keyboard and actual supported accessibility tool; disclose limitations.
11. Capture actual sanitized empty/populated/completed/editor/error/conflict/expanded native screenshots and runtime/package/provider/renderer evidence. RPM hash/source provenance recorded; unsigned package disclosed,no signing claim. Document actual uninstall package after explicit approval,retain no unrelated deletion. Hand frozen Windows source/descriptor/lock inclusion procedure to10;08 updates general status from native evidence only.

## Todo list
- [ ] Implement every owned artifact/requirement with real behavior, not scaffolds (23/23 exclusive files delivered across desktopApp, scripts, docs, and evidence; cycle-3 review noted single-instance guard ordering, vault subprocess vs D-Bus binding, and release key externalization).
- [ ] Pass designated future command and actual runtime gates; record sanitized evidence (19/19 desktop unit tests and 96/96 shared tests pass; live RPM build via rpmbuild and Xpra display execution pending host/operator environment prerequisites).
- [ ] Resolve failures through original owner and complete parent review; technical review 3/10 NOT ACCEPTED pending live RPM package smoke, real Secret Service integration, and guard ordering refactor.

## Success Criteria
Linux build/package works with no Windows sources/lock present; frozen target selection/strict custom lock mechanism demonstrated; real exactly-one packaged Linux provider and Secret Service custody/no fallback; installed RPM runs without developer JDK in SSH Xpra with shared durable/offline/version-conflict/account lifecycle and native accessible UI. RPM evidence not Windows proof.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
D-Bus session/keyring absence,Skiko/JRE module/native resources,locking/provider service packaging and target resolution may block.10 cannot patch09 build for routine addition: fix inclusion mechanism09 before its freeze; later defect reopens09 owner,not ownership transfer.

## Security Considerations
No plaintext token fallback,one desktop process/profile,OS-private database paths; release immutable allowed origin/mode/default TLS. No endpoint/key/admin secrets logged; TabbyRush app key extractable. App ordinary user; sudo only package operations and private SSH Xpra.

## Next steps
10 adds Windows provider/descriptor/test/target lock using frozen09 configuration on actual Windows.02 handles any new verified platform hashes;08 updates Linux docs/status. Linux build remains valid before/after Windows files appear.

## Unresolved questions
Actual user D-Bus Secret Service/unlock availability,approved package metadata and platform binding version await02/09 proof. Windows workstation remains later10 prerequisite.
