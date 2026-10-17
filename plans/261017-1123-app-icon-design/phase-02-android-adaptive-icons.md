# Phase 02 — Android Adaptive Icon Assets & Manifest Integration

## Overview
- Priority: P2. Status: pending. Effort: 1h. Dependency: Phase 01 G1. Owner: Android implementer.
- Context: [plan/ownership](plan.md#exclusive-file-ownership), [master contract](phase-01-master-vector-icon-design.md), [Android research](research/researcher-01-android-icons.md), [manifest](../../androidApp/src/main/AndroidManifest.xml).

## Key insights and requirements
- Existing minSdk 26 covers adaptive icons for every supported Android version. No legacy density rasters, build-plugin change, dependency or compatibility layer needed.
- Current application icon is the platform `sym_def_app_icon` placeholder. Replace it and declare roundIcon; retain all existing security/component configuration.
- Required background drawable is intentional despite a flat color: explicit separate foreground/background files match the delivery contract. Do not also add a duplicate colors resource.
- Round and regular resources share identical adaptive layers; the launcher supplies its mask. Android 13 monochrome is deliberately out of scope, not implied by targetSdk 35.

## Exclusive writable files
Create:
- `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `androidApp/src/main/res/drawable/ic_launcher_foreground.xml`
- `androidApp/src/main/res/drawable/ic_launcher_background.xml`

Modify:
- `androidApp/src/main/AndroidManifest.xml`

Delete: none. Master SVG, Gradle files, notification/widget drawable files and documentation are read-only.

## Architecture and implementation steps
1. Convert the frozen `icon-foreground` filled paths into VectorDrawable groups/paths; retain draw order, winding/fill behavior, palette and geometry. Use width/height `108dp` and viewportWidth/viewportHeight `108`; transparent outside the mark. Do not embed the teal field in the foreground.
2. Create a 108dp/108-viewport background VectorDrawable containing one opaque `#004646` square covering the full viewport. No rounded clipping or transparent margins.
3. Create each mipmap XML with the Android namespace and an `<adaptive-icon>` root. Both reference `@drawable/ic_launcher_background` and `@drawable/ic_launcher_foreground`; no copies of path data in the mipmap files.
4. Replace only `android:icon` with `@mipmap/ic_launcher` and add `android:roundIcon="@mipmap/ic_launcher_round"` on the existing `<application>`. Keep the TabbyRush label and application name.
5. Preserve `allowBackup="false"`, backup rule references, dataExtractionRules, `usesCleartextTraffic="false"`, supportsRtl, every permission/component/export flag/intent filter and the debug-only network-security overlay unchanged.
6. Submit changed-file list and source-review result. No builds/tests/formatters; Phase 04/main owner validates resource linking and rendered launcher behavior after both platform slices land.

## Success criteria — G2
- Exactly the four requested icon XML resources exist and reference app-owned layers, never `@android:drawable/sym_def_app_icon`.
- Foreground/background are independent, valid vector definitions in the frozen coordinate space; safe-circle/mask treatment unchanged from G1.
- Both manifest mipmap references resolve by source inspection; regular and round adaptive files share the same layer references.
- Manifest diff limited to icon attributes. Security audit is a Phase 04 requirement, not satisfied merely by inspection.

## Risks and security
- SVG-to-vector conversion may change clip/fill behavior: use filled path-compatible source and compare conversion previews in integrated verification.
- Incorrect resource directory/namespace causes AAPT failure: use the exact `mipmap-anydpi-v26` and drawable paths above; Phase 04 compiles resources.
- Adaptive icon background must remain opaque and unmasked across OEM masks. Do not add per-OEM artwork or special-case launcher behavior.
- No new permission, exported component, network capability, secret or notification semantics. Do not edit security-check code to accommodate this change.

## Next gate / unresolved questions
G2 joins Phase 03 G3 before Phase 04. No unresolved Android prerequisite; actual device/API coverage is a verification dependency.
