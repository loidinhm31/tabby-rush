# Android Launcher Icon Requirements — TabbyRush

## Summary

TabbyRush can use Android adaptive launcher icons directly: the app's configured `minSdk` is 26 (Android 8.0), the API level that introduced adaptive icons. AGP 8.7.3 and `compileSdk` 35 require no icon-specific plugin setting or dependency. Replace the current platform placeholder with app-owned adaptive icon XML resources under `mipmap-anydpi-v26`; keep foreground and background art as separate drawable/color resources. Declare `android:icon` and, if a round-launcher variant is supplied, `android:roundIcon` on the existing `<application>` element.

## Repository findings

- `androidApp/src/main/AndroidManifest.xml:10-20`: `<application>` currently uses `android:icon="@android:drawable/sym_def_app_icon"`, with no `android:roundIcon`. Existing security settings include `android:allowBackup="false"` and `android:usesCleartextTraffic="false"`; preserve both.
- `androidApp/src/main/res/`: no launcher icon assets exist yet. Existing resources include `drawable/`, `xml/`, `values/`, and task-surface/widget assets.
- `gradle/libs.versions.toml:2`: AGP is pinned to `8.7.3`; `androidApp/build.gradle.kts:47-54` sets `compileSdk = 35`, `minSdk = 26`, and `targetSdk = 35`. `docs/toolchain-versions.md:18-24` records the same compatibility tuple and Gradle/JDK constraints.
- `docs/design-guidelines.md:20-36, 62`: relevant palette: `TabbyRushPrimary #004646`, `TabbyRushPrimaryDeep #003838`, `TabbyRushMint #27C4B5`, `TabbyRushMintSurface #E6F0F2`, `TabbyRushCream #F7F4ED`, `TabbyRushCoral #C62828`, `TabbyRushWhite #FFFFFF`, `TabbyRushPage #F8FBFA`. Mint is an accent rather than small foreground detail on a light background (documented contrast 2.18:1 on white).

## Recommended resource structure

```text
androidApp/src/main/res/
├── mipmap-anydpi-v26/
│   ├── ic_launcher.xml             # Adaptive color icon; manifest android:icon
│   └── ic_launcher_round.xml       # Optional round-launcher adaptive variant
├── drawable/
│   └── ic_launcher_foreground.xml  # Foreground vector, 108dp canvas/viewport
└── values/
    └── colors.xml                  # Optional solid TabbyRush background color
```

A separate `drawable/ic_launcher_background.xml` is also valid when the background needs vector shapes rather than a flat color. If using a single solid color, define a color resource in `values` and reference it from the adaptive icon; do not add an unnecessary background drawable file.

Each adaptive icon resource is a framework `<adaptive-icon>` document with background and foreground layers. Illustrative resource definition:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/tabbyrush_icon_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Use the same definition for `ic_launcher_round.xml` unless the art benefits from a distinct circular composition; the round resource may reference a separately optimized foreground if needed. Adaptive XML belongs in `mipmap-anydpi-v26`, while its vector layers belong in `drawable` and are density-independent.

## Layer dimensions, safe areas, and artwork constraints

- **Both full layers:** 108 × 108 dp. For an Android vector foreground, use `android:width="108dp"`, `android:height="108dp"`, `android:viewportWidth="108"`, and `android:viewportHeight="108"`.
- **Mask viewport:** the central 72 × 72 dp region corresponds to coordinates x/y 18..90 on a 108 × 108 canvas. The outer 18 dp on each edge is reserved for system masking and motion/parallax; do not treat the outer area as essential artwork.
- **Guaranteed-safe artwork:** keep essential artwork inside the centered safe-zone circle, 66 dp in diameter. Its bounding square is x/y 21..87 on the 108 × 108 canvas, but that square's corners are outside the safe circle and may be clipped. Android's current design guidance gives 48 dp as a minimum logo size and 66 dp as the maximum.
- **Terminology correction:** 72 dp is the masked viewport; the guaranteed-safe region is the inner 66 dp-diameter circle. These are not two interchangeable safe-zone measurements. Device/OEM mask paths vary, so the viewport bounds do not guarantee visibility of every point in its square.
- Keep foreground/background layers separately renderable; don't bake a launcher mask, rounded corners, or an outline shadow into either layer. Let Android apply the OEM mask. Background generally fills the full canvas; foreground artwork should be centered with clear edges.
- Suggested TabbyRush starting palette: `TabbyRushPrimary` or `TabbyRushPrimaryDeep` as background, `TabbyRushCream`/`TabbyRushWhite` as primary foreground, and `TabbyRushMint` as a limited accent. `TabbyRushCoral` is documented as alert/destructive accent; avoid making it the dominant mark without design intent.
- Optional Android 13+ themed icon: add a one-color `<monochrome>` layer to each adaptive icon for launchers supporting themed icons. It is optional for API 26 operation and does not replace the normal foreground/background layers. A distinct monochrome artwork is preferable if the color artwork does not reduce cleanly to one silhouette.

## Manifest integration

Replace the placeholder reference on the existing `<application>` element and keep its unrelated security/label attributes intact:

```xml
<application
    ...
    android:label="TabbyRush"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round">
```

`android:icon` is the application-wide icon and default component icon; `@mipmap` is the conventional launcher-resource reference. `android:roundIcon` is optional, used by launchers presenting apps with circular icons. Because TabbyRush supports API 26+ only, the `mipmap-anydpi-v26` adaptive resources cover its whole Android support range; no API ≤25 legacy raster icon set is required. If `minSdk` is later lowered below 26, add legacy density-specific raster mipmap resources without the `v26` qualifier.

## AGP / platform constraints

- Adaptive icons are an Android platform format (`AdaptiveIconDrawable` and `<adaptive-icon>` are API 26); TabbyRush's `minSdk = 26` matches this floor exactly. No compatibility library is needed for adaptive launcher icons.
- AGP 8.7.3 uses the normal Android resource tree and resource references above. No custom Compose Multiplatform configuration is involved; this is an Android manifest/resource concern. Project toolchain documents Gradle 8.9 minimum and JDK 17+ for AGP 8.7.x; the pinned Gradle 8.11.1 / JDK 21 tuple is within that documented range.
- `targetSdk = 35` does not make monochrome mandatory; it makes providing the optional monochrome asset useful for users/launchers with themed-icon support.
- Android Studio's Image Asset Studio can generate adaptive, round, monochrome, and legacy assets; the generated conventional manifest reference is `@mipmap/ic_launcher`. It is a generation option, not a build prerequisite.

## Release security audit impact

Static inspection of `scripts/release-security-check.mjs` shows no icon-specific rule:

- `runSourceDiagnostic()` checks explicit `usesCleartextTraffic="false"` and `allowBackup="false"`, exported-component allowlisting, backup/data-extraction rule files, approved origin, and source secrets. It does not inspect `android:icon`, `android:roundIcon`, or launcher-resource content.
- Release APK inspection checks debuggable status, cleartext, backup, exported components, and absence of `networkSecurityConfig`; it does not assert any icon attribute. `validateExportedComponents()` scans only activity/service/receiver/provider tags, so adding icon attributes on `<application>` does not change its allowlist result.
- Current main manifest already retains the two explicit policy attributes and required existing component declarations. Therefore, changing only the application icon references and adding their drawable resources should not regress this script's checked invariants. This conclusion is from code inspection, not an executed audit; no build or test was run for this research assignment.
- Keep the manifest element well-formed and preserve security attributes/components. The debug-only `networkSecurityConfig` is in `androidApp/src/debug/AndroidManifest.xml`; do not move it into the main manifest as part of icon work.

## Sources

1. Android Developers, [Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) — layer requirements, 108 dp canvases, 66 dp safe artwork circle, 18 dp outer inset, optional monochrome, manifest attributes, and `mipmap-anydpi-v26` location.
2. Android Developers, [AdaptiveIconDrawable API reference](https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable) — API 26 availability; 108 dp layers, inner 72 dp masked viewport, outer 18 dp system-effects area; XML inflation.
3. Android Open Source Project, [Implement adaptive icons](https://source.android.com/docs/core/display/adaptive-icons) — OEM mask path and safezone validation.
4. Android Developers, [Create app icons](https://developer.android.com/studio/write/create-app-icons) — adaptive/legacy icon generation, mipmap placement, manifest mipmap reference, and monochrome asset option.
5. Android Developers, [Vector drawables overview](https://developer.android.com/develop/ui/views/graphics/vector-drawable-resources) — vector XML usage and platform support from API 21.
6. Repository sources: `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/main/res/`, `androidApp/build.gradle.kts`, `gradle/libs.versions.toml`, `docs/toolchain-versions.md`, `docs/design-guidelines.md`, `scripts/release-security-check.mjs`, and `scripts/release-security-check.test.mjs`.

## Unresolved questions

No Android resource/manifest prerequisite is unresolved. Product-design decisions remain whether the round variant merits independent artwork and what the final TabbyRush foreground symbol is; neither changes the technical contract above.
