# Phase 03 — Desktop Runtime & Tray Icons

## Overview
- Priority: P2. Status: pending. Effort: 2h. Dependency: Phase 01 G1. Owner: desktop implementer.
- Context: [plan/ownership](plan.md#exclusive-file-ownership), [master/export contract](phase-01-master-vector-icon-design.md), [desktop research](research/researcher-02-desktop-and-metaphor.md), [windows/tray entry point](../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt), [packaging config](../../desktopApp/build.gradle.kts).

## Key insights and requirements
- Both existing 32×32 tray PNGs contain the old blue/violet check and must be replaced together. Their shared lookup name is already correctly selected through host-specific resources.
- Runtime window/taskbar painter and packaged application icon are independent channels. Configure both; PNG replacement alone cannot brand the MSI or RPM.
- Keep `androidx.compose.ui.res.painterResource(String)` and current sourceSets. Resource-library migration, platform targeting changes and dependency updates would expand this task unnecessarily.
- Both main and sticky windows use one shared runtime painter; tray retains its own optical small-size painter. No changes to tray support guards, menu actions, hide/show, recovery, teardown or account lifecycle.

## Exclusive writable files
Create:
- `desktopApp/src/main/resources/tabbyrush-icon.png` — 512×512 RGBA runtime and Linux package export.
- `desktopApp/src/windowsMain/resources/tabbyrush.ico` — real multi-image ICO, not a renamed PNG.

Modify:
- `desktopApp/src/linuxMain/resources/task-surface-tray.png`
- `desktopApp/src/windowsMain/resources/task-surface-tray.png`
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt`
- `desktopApp/build.gradle.kts`

Delete: none. Do not move existing tray resources into common resources or modify service-provider descriptors, locks, security code, tests or docs owned by Phase 04.

## Architecture and implementation steps
1. Export `tabbyrush-icon.png` from G1 master using its approved desktop-only rounded badge framing, transparent corners and sRGB RGBA output. No SVG runtime loader or runtime raster-generation code.
2. Produce the simplified 32×32 RGBA tray export from the same dog/check geometry and copy the identical output bytes to both target-specific tray paths. Preserve alpha rather than flattening against a desktop theme background.
3. Generate ICO frames at 16, 24, 32, 48 and 256 pixels; use the small-size optical drawing for small frames and large drawing at 256. Preserve transparency and encode a real icon directory. Record generator version/settings for Phase 04; no permanent custom converter is needed.
4. In the existing `application {}` composable, obtain one `val appIcon = painterResource("tabbyrush-icon.png")` outside the two Window content blocks. Pass `icon = appIcon` to **both** `Window` declarations. Reuse Compose's resource loading; no per-recomposition ImageIO work, extra cache or decoding helper.
5. Leave `Tray(icon = painterResource("task-surface-tray.png"))`, tooltip, conditional availability, onAction and menu unchanged; the resource replacement supplies branding without affecting behavior.
6. Under existing `nativeDistributions.linux`, add `iconFile.set(project.file("src/main/resources/tabbyrush-icon.png"))`. Under existing `nativeDistributions.windows`, add `iconFile.set(project.file("src/windowsMain/resources/tabbyrush.ico"))`. Paths are relative to `desktopApp`, not repository root.
7. Preserve target formats, package name/version, Windows upgrade UUID, shortcut/menu/per-user settings, source-set selection and host mismatch rejection exactly. No `.icns` or cross-host package workaround.
8. Hand main owner all changed paths and export settings. Do not run build/test/packaging/formatting commands mid-flight; integrated verification belongs to Phase 04.

## Success criteria — G3
- One high-resolution common runtime PNG; two replacement tray files with identical bytes; ICO contains all five required sizes.
- Both Window callsites reference the same painter and existing Tray uses its optical asset. No fallback to the old violet icon.
- Linux PNG and Windows ICO configured independently in their platform distribution blocks.
- Only selected host tray resource enters that target's classpath; no duplicate common tray asset or loss of provider resources.

## Risks and security
- Windows scales tray icons to non-32 sizes at high DPI: inspect at 100/150/200%; 32px is chosen source size, not a platform guarantee. If too soft, return to G1/export owner rather than adding speculative runtime scaling machinery.
- Windows shell/launcher caching can show old artwork; verify a fresh package/shortcut in an approved test environment, not by deleting user caches as an automatic step.
- Java ImageIO does not normally decode ICO containers: Phase 04 must inspect the icon directory and decode generated PNG payloads/frames with the appropriate tool.
- Native packages require matching host tooling; Windows cannot establish Linux RPM acceptance. Keep platform gates separate.
- Assets are bundled public artwork; no credentials, new native APIs, access grants or business-data flow. Runtime behavior remains unchanged.

## Next gate / unresolved questions
G3 joins Phase 02 G2 before Phase 04. Both-window branding and Linux packaging are selected; no product question remains.
