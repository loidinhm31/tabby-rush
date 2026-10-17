# Phase 04 — Documentation & Verification

## Overview
- Priority: P2. Status: pending. Effort: 4h. Dependencies: Phase 02 G2 **and** Phase 03 G3, complete terminal handoffs if delegated.
- Owner: documentation/test author for owned files; **main integration owner alone runs verification after all edits land**. These are future implementation checks, not checks authorized or performed during planning.
- Context: [plan](plan.md), [design guidelines](../../docs/design-guidelines.md), [security research boundary](research/researcher-01-android-icons.md#release-security-audit-impact), [desktop host limitations](research/researcher-02-desktop-and-metaphor.md#validation-and-limitations), [existing desktop unit pattern](../../desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt).

## Exclusive writable files
Create:
- `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/app-icon-assets-test.kt`
- `androidApp/src/test/kotlin/dev/tabbyrush/android/app-icon-resources-test.kt`
- `plans/261017-1123-app-icon-design/reports/icon-verification.md`

Modify:
- `docs/design-guidelines.md`
- `docs/project-changelog.md`
- `docs/development-roadmap.md`

Delete: none. Main owner alone updates phase/plan status. Do not modify asset files, production sources, security-check implementation, Gradle/test dependencies or verification metadata under this phase's ownership; route failures to the corresponding owner.

## Key insights and architecture
- Test source/resource contracts with existing `kotlin.test` + JDK XML/ImageIO/ByteBuffer APIs. No Robolectric, Compose rendering harness, image library, Gradle task, Node package or production asset-loader abstraction needed.
- JVM asset decoding proves packaged bytes are readable, not that a native launcher/taskbar/tray chose or rendered the right icon.
- Existing security diagnostic does not validate icons. Run it to protect manifest/security invariants, then separately check icon resource structure, decoding and native presentation.
- Keep verification evidence in one report, linked from design guidelines; do not duplicate the concept spec across multiple permanent documents.

## Implementation steps
1. Add desktop asset tests following existing package/naming/assertion patterns:
   - Load `tabbyrush-icon.png` and `task-surface-tray.png` with the test classloader. Assert a resource exists, ImageIO returns a non-null image, expected dimensions (512-square/32-square), alpha channel, at least one transparent pixel and at least one visible pixel.
   - Enumerate classloader resources for each name and require exactly one of each; this detects common/target resource collisions in the selected host build.
   - Read both source tray files relative to the desktop module test working directory; assert their bytes identical and decode each independently. This verifies the unselected target's source asset as well as selected-host loading.
   - Read the Windows ICO source file on either host. Parse little-endian ICONDIR: reserved 0, type 1, count >=5; bounds-check each directory entry offset/length without integer overflow. Treat zero width/height bytes as 256; require square frames covering 16/24/32/48/256. Do not mistake this structure test for full DIB/PNG frame decoding.
2. Add Android JVM source-contract tests without Android framework resource inflation:
   - Parse the main manifest and all four icon XMLs using a namespace-aware JDK parser with DOCTYPE/external entity/network access disabled.
   - Resolve module-relative files using the established Gradle module test working directory; no absolute workstation paths. Assert the application icon and roundIcon references, adaptive root/layer references, and that every referenced file exists.
   - Assert both foreground/background vector roots use `108dp` dimensions and `108` viewports; background is opaque teal and foreground retains distinct paths. Assert no old platform icon reference.
   - Protect existing manifest `allowBackup=false` and `usesCleartextTraffic=false`; rely on the existing security suite for exported-component policy rather than duplicating its implementation.
   - Clearly label these as source-contract tests. Android framework inflation/OEM masking remain build/device gates.
3. Update the existing design guidelines with an App Icon section: Companion Check rationale, the four color roles, master SVG path/layer IDs, Android 108/72/66 geometry distinction, both windows, tray optical treatment, runtime versus distribution channels, exact export tool version/settings/commands, output dimensions and ICO frame list. Retain mint contrast warning and existing accessibility evidence boundaries.
4. Read current changelog/roadmap before editing. Add the narrowly scoped icon-delivery entry and actual status only; do not mark unrelated acceptance/provenance holds resolved or claim release readiness. Do not mark planned native checks passed.
5. Main owner executes the integrated gates below after edits land, records command/host/date/exit code/test counts/artifact paths and separates pass/fail/blocked/not-run. Attach original-art previews and native captures only with clear labels; never call a preview a device screenshot.

## Concrete validation gates

### G4a — source/security regression
Run from repository root:
```text
node scripts/release-security-check.mjs --diagnose-source
node --test scripts/release-security-check.test.mjs
```
Expected: successful source diagnostic and existing security unit suite. If approved-origin/environment configuration is required, use operator-provided approved values without printing credentials. Existing failures stay explicit blockers; do not weaken audit rules, permissions, backup, network or dependency policy to obtain green output.

### G4b — vector/raster/container integrity
- Main owner opens/renders the committed SVG using the recorded vector exporter; verifies its two group IDs, only authored palette fills, no external references, independent transparent foreground and radius-33 safe-circle geometry. Render circle/squircle/rounded-square previews to inspect clipping.
- Use ImageMagick (or an installed equivalent with ICO support) for actual full-image decoding, not just extension/header recognition. Example commands from repository root; record the actual tool version:
```text
magick -version
magick identify desktopApp/src/main/resources/tabbyrush-icon.png desktopApp/src/linuxMain/resources/task-surface-tray.png desktopApp/src/windowsMain/resources/task-surface-tray.png desktopApp/src/windowsMain/resources/tabbyrush.ico
magick desktopApp/src/main/resources/tabbyrush-icon.png null:
magick desktopApp/src/linuxMain/resources/task-surface-tray.png null:
magick desktopApp/src/windowsMain/resources/task-surface-tray.png null:
magick desktopApp/src/windowsMain/resources/tabbyrush.ico null:
```
- Require all PNGs and every ICO frame to decode, dimensions match contract, ICO includes all five sizes, alpha is preserved and neither tray is the old violet check. Anti-aliased pixels may contain blended colors; do not assert that every raster pixel equals an authored palette hex.
- If decoder/exporter missing, record prerequisite and use a real installed equivalent; do not treat ICO directory parsing alone as frame decodability or download arbitrary tooling silently.

### G4c — integrated unit/resource compilation
On Windows with the pinned JDK/Android SDK configured:
```text
.\gradlew.bat :shared:jvmTest :desktopApp:test :androidApp:testDebugUnitTest :androidApp:assembleDebug
```
On Linux use `./gradlew` with the same tasks. Run once on the integrated state, preserving default strict dependency verification and the host's normal desktop target selection. This covers new source/asset tests, existing lifecycle/unit regressions, desktop Kotlin compilation and Android AAPT/resource/manifest linking. Record each task outcome; task skips/up-to-date status do not imply newly exercised native UI acceptance.
- Require one Windows and one Linux `:desktopApp:test` result for target-specific resource classpaths; the second host need not repeat unchanged shared/Android suites. Do not force `-PdesktopTarget=linux` on Windows or the inverse.
- Existing dependency/provenance holds may prevent execution. Report exact failure and leave gate open rather than editing metadata, adding bypass flags, or rerunning merely to reconfirm a known failure.

### G4d — native icon and packaging acceptance
The main owner/operator schedules actual platform sessions; interactive install/launch actions require appropriate user authorization. No installs or cache deletion are authorized by this planning assignment.
| Surface | Concrete review | Required evidence |
|---|---|---|
| Android API 26 and API 35 launcher | Install debug artifact in an approved test device/profile; review launcher/app drawer with circle, squircle and rounded-square mask previews/available launchers; confirm TabbyRush + check not cropped, full-bleed field and no default Android icon | API/launcher/mask plus actual capture; distinguish preview-only masks from device results |
| Windows main/sticky windows and tray | Review both window/title/taskbar icons; tray at 100/150/200% scaling against light/dark host taskbars; exercise Open TabbyRush, sticky hide/show and Quit; verify no-tray recovery remains unchanged | Native session/scales/captures and behavior notes |
| Linux main/sticky windows and tray | Repeat runtime/taskbar/tray review in a real supported session; record desktop environment/tray support. Tray-unavailable session must preserve visible recovery controls | Native session/captures, not a Windows cross-target build |
| Windows package | On Windows run `.\gradlew.bat :desktopApp:packageMsi`; inspect generated package/application image icon, and in an approved installation session confirm Start menu/desktop shortcut/EXE use TabbyRush rather than Java/Compose default | MSI/artifact path and fresh shortcut/EXE capture; cached pinned icons are not sole evidence |
| Linux package | On Linux run `./gradlew :desktopApp:packageRpm`; inspect packaged launcher PNG/desktop-entry integration and launcher identity in approved test session | RPM/artifact path plus package/launcher evidence |
- Native packaging commands are separate host prerequisites, not evidence of a successful build now. Missing packaging tools, signing/provenance constraints or native sessions keep the relevant acceptance row blocked/pending.

## Success criteria — G4 / completion
- All requested assets/configuration, both Window callsites, both target trays and package channels accounted for; SVG source and derived artwork consistent.
- Source security audit, security unit suite, new asset/XML tests, existing unit suites and Android resource linking pass; actual PNG/ICO frame decoding evidenced.
- Design guidelines are the durable final specification/export recipe; changelog and roadmap match delivered work. Evidence report lists exact exercised checks and remaining holds.
- Native mask/host/package acceptance cannot be replaced by tests or design previews. Main owner may record implementation delivered with acceptance pending, but must not claim this plan's technical definition of done until required native gates close.

## Risks and security
- Source-only assertions miss rendering defects: retain build and device gates rather than expanding unit mocks.
- High-DPI blur/ICO corruption: decode all frames and inspect target hosts before acceptance; route export fixes to Phase 03, geometry fixes to Phase 01.
- Existing large platform/provenance acceptance holds are not caused or resolved by app-icon work. Keep these distinct in reporting.
- Verification should not publish private account/task content, secrets or signed keystore data; use clean approved test profiles and redact evidence as needed.

## Unresolved questions
- None for implementation design. Execution prerequisites to establish: strict dependency provenance, available SVG/ICO tooling, Android API 26/35 device profiles, Windows/Linux native sessions and native packaging tools. Report exact missing prerequisite if a gate cannot run.
