# Phase 09 — Linux desktop and installed RPM over Xpra

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Android release gate](phase-08-deployment-security-release.md) · [Headless setup](phase-01-headless-tooling.md) · [Theme](phase-06-compose-ui-theme.md)
- [Compose native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)

## Parallelization Info
Group F after complete08. Own desktop entrypoint/build/lock/test/Linux docs and evidence. Configure RPM and MSI once now;10 builds MSI natively later, cannot edit this build script.02 reopens shared/root metadata for target dependencies;08 updates its general docs after this gate.

## Overview
- Date: 2026-10-13
- Description: Real common UI on Linux with native installed self-contained RPM and immutable release policy.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 5h

## Key Insights
Compose desktop is JVM, not Kotlin/Native Linux. Running Gradle with full developer JDK is not proof a trimmed packaged runtime/resources work. Xpra terminal must supply display/auth; no desktop installation or public GUI port. Native MSI task on Linux is skipped/not proof of Windows.

## Requirements
Shared feature parity, keyboard/focus/window resize and common fonts/notices; lifecycle cleanup; self-contained RPM with real installed UI through SSH Xpra, deployed HTTPS access; explicit development-only localhost mode. Both RPM/MSI configuration, stable Windows upgrade UUID and package metadata frozen for10. No app-store/automatic updater/CI added.

## Architecture
`MainKt.main` calls a common window helper with literal RELEASE; `DevelopmentLauncher.main` calls same helper with literal DEBUG only through custom Gradle runDebug. Neither reads endpoint mode from env/property/arguments. Normal run/native distributions/installed launchers select MainKt. Endpoint/token still manually entered in process-only form. Window-lifetime main coroutine scope uses coroutines-swing; controller/client close on window disposal. One real entrypoint file, not copied platform UI.

## Related code files
Future create only, no existing application files modified/deleted:
- `desktopApp/build.gradle.kts`
- `desktopApp/gradle.lockfile`
- `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt`
- `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-lifecycle-test.kt`
- `scripts/linux-package-smoke.sh`
- `docs/linux-desktop-guide.md`
- `docs/linux-acceptance.md`
- `docs/evidence/linux-empty.png`
- `docs/evidence/linux-populated.png`
- `docs/evidence/linux-completed.png`
- `docs/evidence/linux-editor.png`
- `docs/evidence/linux-error.png`
- `docs/evidence/linux-expanded.png`

## File Ownership
Only exact phase09 registry rows. No new icons/resources/license duplicates: common06 resources/notices packaged through dependency, default generated package icon permitted. Build-generated/native outputs untracked.02 owns root inclusion rule/catalog/shared lock/verification metadata;08 owns general changelog/README/summary/roadmap. Windows packaging repairs reopen09, never shared ownership.

## Implementation Steps
1. Create desktop JVM/Compose module using02 catalog and compatible runtime JDK/jpackage >=17, bytecode17 approved tuple. Dependencies shared, compose.desktop.currentOs and coroutines-swing; no Android class imports. Root documented existence-guard now includes actual desktop module. Pin OS-specific native dependencies through02 verification metadata;09 generates/reviews desktop lock and records Windows extension procedure (10 reopens09 for Windows lock entries,02 for checksums), never override Linux-only native artifact on Windows.
2. Create desktop main.kt package dev.doggo.desktop with explicit stable MainKt facade if necessary. MainKt literal RELEASE for ordinary and installed app; DevelopmentLauncher object JVM static main literal DEBUG. Register custom JavaExec runDebug using development launcher and main runtime classpath; no debug system property/package heuristic. Native application mainClass always dev.doggo.desktop.MainKt. No release endpoint fixed to a secret/nonpublic URL: user enters HTTPS origin each process. Endpoint policy still validates before attaching token.
3. Compose Window(title Doggo) uses shared theme/app, window-lifetime controller/scope and close/dispose; retain state through resize/recomposition, cancel/close exactly once on exit, main Swing dispatcher. Dirty/uncertain close confirms loss/possible server commit before disposal. No disk preferences/session token/drafts or rememberSaveable secret. Document OS kill necessarily loses process state.
4. Configure nativeDistributions TargetFormat.Rpm and TargetFormat.Msi, packageName Doggo, version1.0.0, descriptive vendor/product metadata from approved identity. Linux packageName doggo, stable menu entry; Windows per-user install, start-menu/shortcut and one freshly generated upgradeUuid recorded09/frozen for10 (not a credential). No guessed maintainer email or borrowed logo. Include required runtime modules for TLS/DNS/AWT/resource loading: inspect suggestModules against pinned jpackage/JDK; use includeAllModules=true as explicit first-release reliability choice if dynamic loading cannot be proven safely trimmed. Record size tradeoff, actual bundled runtime version and native arch. No shrink/obfuscation required.
5. Check Fedora RPM build dependencies with `sudo dnf info rpm-build`; approved `sudo dnf install rpm-build` if missing, no global Java replacement. Use selected JAVA_HOME, `java -version`, `jpackage --version`. One integrator runs owned desktop tests/full changed shared gates: `./gradlew :desktopApp:test :shared:allTests :desktopApp:createDistributable :desktopApp:packageRpm`; locking/bootstrap writes handled only by09/02 before locked repeat. Never count skipped MSI task as success. Record actual artifacts/task output rather than guessing package filename.
6. Local development check in Xpra xterm: local Worker127.0.0.1:8787 dev token, `./gradlew :desktopApp:runDebug` -> connect explicit http://127.0.0.1:8787 and perform real CRUD/error/reconcile. Normal `./gradlew :desktopApp:run` -> release rejects same HTTP; deployed HTTPS works. This dev run is not installed-RPM acceptance. Permanent lifecycle test exercises real launcher policy helper/controller lifetime and late-response cancellation with controlled transport; behavioral tests, not code text.
   Reuse07's owned debug-fault-proxy unchanged: stop Worker8787, start it on127.0.0.1:8788, run proxy127.0.0.1:8787, then exercise runDebug create/edit/delete with one dropped upstream response per scenario. Verify actual CIO sends one mutation only, shows uncertain state and performs explicit GET reconciliation; no automatic replay. Restore normal Worker8787 afterward. This is required engine evidence, not inferred from Android's OkHttp test or MockEngine.
7. Use produced unique RPM path as `$RPM_FILE`; smoke script accepts `--rpm "$RPM_FILE"`, rejects ambiguous/missing/stale inputs, runs `rpm -qpi "$RPM_FILE"`, `rpm -qlp "$RPM_FILE"` and computes checksum. Confirm generated package name/installed paths and transaction before `sudo dnf install "$RPM_FILE"`. Query installed package files/version using actual package name, discover launcher from package metadata/desktop entry rather than assumed /opt path. Launch `$DOGGO_INSTALLED_LAUNCHER` **in Xpra xterm** as ordinary user. Do not run app as root. Self-contained runtime check in a shell without JAVA_HOME/developer Gradle on PATH; retain Xpra display/auth/system utilities.
8. Against deployed HTTPS origin, real installed UI: sanitized create/rename/complete/reopen/filter/count/page/delete flows; concurrent Android/API same-version change -> conflict/draft retention and explicit reapply; stop network/wrong token -> actionable state and no replay. Close/reopen -> token/session gone. Attempt HTTP127.0.0.1/localhost/10.0.2.2 -> rejected before any request; launch with mode-like args or JAVA_TOOL_OPTIONS debug-looking property -> still rejected, no property can choose DevelopmentLauncher. Inspect package launcher config confirms MainKt, no development task wired into distribution.
9. Visually inspect compact and >=840dp expanded layout, long Unicode/Vietnamese titles, bundled font/glyph/license view, 200% text/desktop scale, keyboard Tab/ShiftTab/Enter/Escape/visible focus and accessible labels. Screen-reader check using available desktop accessibility tooling/client; report actual tool/support, do not label TalkBack as Linux test. Capture real empty/populated/completed/editor/error/expanded screenshots through Xpra client or private same-display tool (confirmed executable), not web preview. Record window size/host/renderer/runtime/commit/RPM hash and actual gate outcomes.
10. Smoke script checks installed version/launcher/runtime/resources and guides actual UI evidence, exits failure on failed checks, never treats process existence as UI success. Document uninstall using actual package name after explicit approval, no wholesale dependency removal; sign/trust provenance by checksum if package unsigned, never claim cryptographic RPM signing. Reopen08 to update general Linux status/changelog; keep Windows pending.

## Todo list
- [ ] Implement real desktop composition, literal release/dev launchers and frozen RPM/MSI configuration.
- [ ] Pass desktop tests and installed self-contained RPM HTTPS/policy/lifecycle/UI gate over Xpra.
- [ ] Capture actual Linux/accessibility evidence and return general doc updates to08.

## Success Criteria
Actual installed RPM runs common app in SSH Xpra without system developer JDK, full feature/error/conflict parity and licensed resources. Installed/normal launcher immutable RELEASE rejects all debugHTTP/property attempts; only explicit Gradle runDebug supports localhost. Window close clears session/releases work; real responsive keyboard/accessibility inspection and screenshots recorded. MSI configuration complete but Windows runtime still pending10.

## Conflict Prevention
No root/catalog/metadata/shared resource edits from09; reopen02/05/06. Windows-specific build/lock repairs remain09 ownership.08 alone updates general docs. New paths/metadata/assets need registry amendment.

## Risk Assessment
Xpra renderer/native Skiko, packaging runtime modules/TLS, RPM dependencies or font rendering can differ from Gradle run. Installed-package gate catches them. Permissions/client accessibility tooling may require authorized setup, not public GUI exposure. Later Windows tuple/WiX differences can reopen09.

## Security Considerations
No token/endpoint mode in JVM args/preferences/installer; packaged MainKt constant RELEASE. HTTPS default trust/no redirects, session memory-only and sanitized evidence. Sudo only package transaction, never app. Private SSH/Xpra, RPM hash/provenance disclosed; no claimed package signing unless performed.

## Next steps
After complete installed-RPM gate, hand frozen native config/version/upgrade UUID/lock procedure to10 on actual Windows machine. Reopen08 status docs; Windows remains required pending until native installed-MSI evidence.

## Unresolved questions
Later Windows machine/arch and compatible JDK/WiX packaging requirements verified10. Linux client/accessibility tooling and approved vendor metadata resolved from operator at implementation; font audit owned06.
