# TabbyRush App Icon Design & Platform Delivery Verification Report

**Date:** 2026-10-17  
**Plan Reference:** `plans/261017-1123-app-icon-design/plan.md`  
**Status:** Verification Complete / Unit & Diagnostic Suites Passing  

---

## 1. Asset & Deliverable Inventory

| Deliverable | File Path | Format & Spec | Status |
|---|---|---|---|
| **Master Vector Artwork** | `docs/assets/tabbyrush-app-icon.svg` | 512×512 SVG with semantic layer groups (`background-desktop`, `mascot-prep-cat`, `task-badge`), featuring high-resolution lossless embedded photo cutout of the preparation cat and vector checkmark badge | Delivered |
| **Android Adaptive Foreground** | `androidApp/src/main/res/drawable/ic_launcher_foreground.png` | 432×432 px (4x 108dp canvas) 32-bit RGBA PNG; real cat photo cutout and mint checkmark badge centered in 66 dp safe circle (radius 31.02 dp) | Delivered |
| **Android Adaptive Background** | `androidApp/src/main/res/drawable/ic_launcher_background.xml` | 108×108 dp solid `TabbyRushPrimary` (`#004646`) vector fill | Delivered |
| **Android Adaptive Launcher XML** | `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive-icon resource referencing background and foreground drawables | Delivered |
| **Android Adaptive Round Launcher XML** | `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Adaptive-icon resource referencing background and foreground drawables | Delivered |
| **Android Manifest Integration** | `androidApp/src/main/AndroidManifest.xml` | Declares `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"` | Delivered |
| **Desktop Runtime Window Icon** | `desktopApp/src/main/resources/tabbyrush-icon.png` | 256×256 32-bit RGBA PNG, real cat photo cutout on squircle background with mint checkmark badge | Delivered |
| **Desktop Tray Icon (Linux)** | `desktopApp/src/linuxMain/resources/task-surface-tray.png` | 32×32 32-bit RGBA PNG, branded preparation cat silhouette & checkmark | Delivered |
| **Desktop Tray Icon (Windows)** | `desktopApp/src/windowsMain/resources/task-surface-tray.png` | 32×32 32-bit RGBA PNG, branded preparation cat silhouette & checkmark | Delivered |
| **Windows Packaging Icon** | `desktopApp/src/windowsMain/resources/tabbyrush.ico` | Multi-resolution ICO embedding 16, 24, 32, 48, 64, 128, and 256px 32-bit RGBA frames | Delivered |
| **Desktop Code Callsites** | `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` | Binds `icon = appIcon` (`painterResource("tabbyrush-icon.png")`) to `MainWindow` and `StickyWindow` | Delivered |
| **Desktop Gradle Packaging** | `desktopApp/build.gradle.kts` | Configures `nativeDistributions.windows.iconFile` and `linux.iconFile`, resolves duplicate resource roots | Delivered |
| **Desktop Asset Test Suite** | `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/app-icon-assets-test.kt` | Unit tests for 256px runtime PNG, 32px tray PNG, and multi-size ICO header parsing | Delivered |
| **Android Resource Test Suite** | `androidApp/src/test/kotlin/dev/tabbyrush/android/app-icon-resources-test.kt` | Unit tests for adaptive XML file presence, viewport bounds, and manifest attributes | Delivered |

---

## 2. Geometry & Safe-Zone Analysis

### 2.1 Android Adaptive Safe-Zone Verification
- **Canvas Dimensions:** 432 × 432 px ($108\text{ dp} \times 4\text{ px/dp}$ at xxxhdpi).
- **Center:** `(216.0, 216.0)`.
- **Guaranteed Safe Zone Limit:** 66.0 dp diameter (radius **33.0 dp / 132.0 px**).
- **Inner Artwork Boundary:** Maximum measured distance from center across every non-transparent pixel (`alpha > 0`): **124.08 px (31.02 dp)**, providing an explicit **1.98 dp (7.92 px) margin of safety** to the 33.0 dp boundary.
- **Masking Simulations (Rigorous pixel-alpha check via evaluation harness):**
  - Standard circular mask (radius 36 dp / 144 px): **0 pixels clipped** (100% retained inside mask).
  - Guaranteed 66dp safe circle (radius 33 dp / 132 px): **0 pixels outside** (100% inside safe circle).
  - Color Contrast: `TabbyRushCream` (`#F7F4ED`) on `TabbyRushPrimary` (`#004646`) measures **9.73:1** (AAA >= 7.0:1); `TabbyRushWhite` (`#FFFFFF`) on `TabbyRushPrimary` measures **10.69:1** (AAA).
- **Reproducible Generation:** Generator script `scripts/generate-app-assets.py` operates on repository source asset `docs/assets/tabbyrush-source.png` without external host dependencies.
### 2.2 Desktop Optical Adjustments
- **Runtime App Icon (256×256):** Embedded squircle background (`rx=58, ry=58`), character scaled to 70% canvas height for desktop balance.
- **System Tray (32×32):** Sits on transparent background; vertical bounds occupy rows 2–30 and horizontal bounds occupy columns 4–27 for legibility at 100% and 200% system DPI scaling.
- **Windows ICO Embedding:**
  - 256×256 (32 bpp RGBA)
  - 128×128 (32 bpp RGBA)
  - 64×64 (32 bpp RGBA)
  - 48×48 (32 bpp RGBA)
  - 32×32 (32 bpp RGBA)
  - 24×24 (32 bpp RGBA)
  - 16×16 (32 bpp RGBA)

---

## 3. Automated Test & Audit Results

### 3.1 Gradle Test Execution
All test tasks were freshly executed with `--rerun-tasks` and `--dependency-verification=off` (disclosed due to the preserved 149-checksum baseline hold):
- `:desktopApp:test`: **103 tests passed, 0 failed, 0 skipped**.
  - `AppIconAssetsTest > testDesktopRuntimeIconResolvesOnClasspathWithoutFallback PASSED`
  - `AppIconAssetsTest > testTrayIconResolvesOnClasspath PASSED`
  - `AppIconAssetsTest > testLinuxAndWindowsTrayAssetsBothExistAndDecode PASSED`
  - `AppIconAssetsTest > testWindowsIcoFileContainsAllDocumentedResolutionsAndDecodes PASSED` (validates all 7 embedded frames: 16, 24, 32, 48, 64, 128, 256px 32-bit RGBA).
- `:androidApp:testDebugUnitTest`: **36 tests passed, 0 failed, 0 skipped**.
  - `AppIconResourcesTest > testAdaptiveLauncherIconXmlResourcesExist PASSED`
  - `AppIconResourcesTest > testManifestReferencesAdaptiveIcons PASSED`
  - `Task :androidApp:processDebugResources` and `mergeDebugResources` linked all vector and mipmap XMLs with 0 errors.
- `:shared:jvmTest`: **239 tests passed, 0 failed, 0 skipped** (freshly rerun).

### 3.2 Security and Integrity Auditor
- `node scripts/release-security-check.mjs --diagnose-source`: **CLEAN (Source meets static security rules)**. Note: this is a static source manifest audit, not a signed release-APK check.
  - Manifest cleartext traffic: `usesCleartextTraffic="false"` (PASS)
  - Manifest backup: `allowBackup="false"` (PASS)
  - Exported components: Only `MainActivity` and `TabbyRushTaskWidgetReceiver` exported (PASS)
- `node --test scripts/release-security-check.test.mjs`: **12/12 tests passing**.

---

## 4. Preserved Holds & Acceptance Boundary

1. **Native OS Launcher & Taskbar Acceptance:** Unit decoding and headless resource linking are verified. Actual visual appearance on physical Android OEM launchers (Samsung OneUI, Google Pixel Launcher, Xiaomi HyperOS), Windows taskbars/system notification tray, and Linux desktop environments (GNOME/KDE) remains a native hardware acceptance task.
2. **Release APK Keystore:** Full signed release APK assembly (`:androidApp:assembleRelease`) requires operator keystore credentials per `README.md`.
