# Code Review: App Icon Update & Deterministic Asset Generation

**Date:** 2026-10-18  
**Target:** Application icon update from vector SVG and preview PNG  
**Reference Report:** `plans/reports/code-review-261018-2233-app-icon-update.md`

---

## Code Review Summary

### Scope
- Files reviewed:
  - `scripts/generate-app-assets.py` (154 lines)
  - `docs/design-guidelines.md` (lines 178–201)
  - `docs/assets/tabbyrush-app-icon.svg` (25 lines, 33.7 KB)
  - `docs/assets/tabbyrush-vector.svg` (17 lines, 33.4 KB, untracked)
  - `docs/assets/tabbyrush-source.png` (1100×917 RGBA PNG, 109 KB)
  - `desktopApp/src/main/resources/tabbyrush-icon.png` (256×256 RGBA PNG, 30.5 KB)
  - `desktopApp/src/windowsMain/resources/tabbyrush.ico` (multi-resolution ICO, 63.5 KB)
  - `desktopApp/src/linuxMain/resources/task-surface-tray.png` (32×32 RGBA PNG, 2.0 KB)
  - `desktopApp/src/windowsMain/resources/task-surface-tray.png` (32×32 RGBA PNG, 2.0 KB)
  - `androidApp/src/main/res/drawable/ic_launcher_foreground.png` (432×432 RGBA PNG, 38.0 KB)
  - `androidApp/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` (5 raster assets)
  - `androidApp/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png` (5 raster assets)
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/app-icon-assets-test.kt` (validation harness)
  - `androidApp/src/test/kotlin/dev/tabbyrush/android/app-icon-resources-test.kt` (validation harness)
- Lines of code analyzed: ~450 lines script/doc/SVG code + 15 binary/vector asset files.
- Review focus: Vector-to-raster asset pipeline, multiplatform icon integration, geometry constraints, deterministic generation, code quality, security.
- Updated plans: `plans/261017-1123-app-icon-design/plan.md` (no active plan provided; prior completed plan referenced).

### Overall Assessment
Code quality and visual asset update are well-engineered and substantially cleaner than prior implementations. The generator replaces an expensive runtime BFS flood-fill cutout of a 3.6MB raster with clean bounding-box cropping on transparent source PNG (109KB) and true vector path extraction into a 512×512 SVG. Generation is verified byte-for-byte deterministic across runs (identical SHA-256 hashes). Android adaptive foreground strictly adheres to the 66dp safe zone (max radius 32.24dp / 128.95px, 0 clipped pixels). A few maintainability, documentation, and repository-tracking issues require resolution.

---

### Critical Issues
*None.* (No security vulnerabilities or runtime-breaking bugs found in committed code).

---

### High Priority Findings

1. **Untracked Vector Source Breaks Generator on Fresh Clones (`docs/assets/tabbyrush-vector.svg`):**
   - **Problem:** `docs/assets/tabbyrush-vector.svg` is present in working tree as untracked (`??`). `scripts/generate-app-assets.py` line 68 contains `assert os.path.exists(SOURCE_SVG), f"Missing source SVG at {SOURCE_SVG}"`.
   - **Impact:** While compiled application builds consume committed generated raster assets, any developer or CI job executing `python scripts/generate-app-assets.py` on a clean git checkout will fail with `AssertionError`.
   - **Remediation:** Stage and track `docs/assets/tabbyrush-vector.svg` alongside the generator script and other asset files.

2. **Untracked Signing Material Exposure Hazard (`keystore_base64.txt`):**
   - **Observation:** Working tree contains untracked `keystore_base64.txt`.
   - **Requirement:** Must remain strictly untracked and excluded from any commit. Static source audit (`node scripts/release-security-check.mjs --diagnose-source`) does not scan untracked files; developers must ensure signing keys are never staged.

---

### Medium Priority Improvements

1. **Brittle Regex Path Extraction & Hardcoded Bounding Box in Generator (`scripts/generate-app-assets.py:98, 103–107`):**
   - **Problem:**
     ```python
     path_matches = re.findall(r'<path[^>]*/>', f.read())
     ...
     orig_bw, orig_bh = 1266.4, 882.75
     cx_orig, cy_orig = (66.6 + 1333.0) / 2.0, (162.33 + 1045.08) / 2.0
     ```
     - Regex assumes single-line, self-closing path elements without unescaped `>` in attributes. Formatting or multi-line path definitions would break extraction.
     - Magic coordinate constants `(66.6, 1333.0, 162.33, 1045.08)` are hardcoded specifically to `tabbyrush-vector.svg`. If source vector artwork geometry changes, the master SVG squircle composition will become misaligned.
   - **Recommendation:** Use `xml.etree.ElementTree` to parse vector elements; compute path bounding box dynamically or document manual update requirements if SVG paths change.

2. **Documentation Discrepancy for System Tray Content Dimensions (`docs/design-guidelines.md:195`):**
   - **Problem:** `docs/design-guidelines.md` line 195 states tray artwork utilizes `30×21 px`. Actual non-zero alpha bounding box in `task-surface-tray.png` is `30×20 px` (`x in [1, 30], y in [6, 25]`).
   - **Cause:** In `generate-app-assets.py` line 88, `tray_h = int(ch * 30 / cw) = int(709 * 30 / 1015) = int(20.9556) = 20`. Truncating integer division yields 20 rather than 21 (`round()` yields 21).
   - **Impact:** Minor cosmetic documentation mismatch.
   - **Fix:** Update guideline text to `30×20 px` (or use `round()` in generator if 21px intended).

3. **Changelog Geometric Staleness (`docs/project-changelog.md:29`):**
   - **Problem:** Line 29 still cites previous iteration metrics: `radius 124.08 px / 31.02 dp from center`.
   - **Actual Metric:** Current foreground scaling (width 238px) measures `128.95 px / 32.24 dp` from center, leaving `3.05 px / 0.76 dp` margin.
   - **Fix:** Align changelog with `docs/design-guidelines.md:190`.

---

### Low Priority Suggestions

1. **Unused Imports in Generator (`scripts/generate-app-assets.py:8, 11`):**
   - `import io` and `import struct` are declared at top of file but never referenced. Remove dead imports.

2. **PIL Bounding Box Asymmetry in `build_desktop_composite` (`scripts/generate-app-assets.py:38`):**
   - `draw.rounded_rectangle([margin, margin, size - margin, size - margin], ...)` uses inclusive coordinates, producing a rectangle of width `size - 2*margin + 1` (e.g. 477px at 512, 18px left margin vs 17px right margin).
   - In contrast, `build_round_launcher` correctly uses `[margin, margin, d_size - 1 - margin, d_size - 1 - margin]`.
   - Recommendation: Use `size - 1 - margin` for exact pixel symmetry.

3. **Low-Resolution Legibility for Multi-Size ICO & Tray Icons:**
   - 16×16 and 24×24 ICO frames and 32×32 tray PNG scale the full illustrated mascot, road trail, and flag. At 16×16, fine details (checkpoints, flag pole) become sub-pixel noise.
   - Consider evaluating dedicated simplified icon silhouette for 16px/24px/tray frames in future polish cycles.

---

### Positive Observations
- **Asset Weight Reduction:** `docs/assets/tabbyrush-source.png` dropped from 3.6 MB to 109 KB (-97%); `tabbyrush.ico` dropped from 111 KB to 63.5 KB (-43%); `tabbyrush-app-icon.svg` replaced 200k-token base64 payload with clean 33 KB pure vector paths.
- **Strict Safe-Circle Enforcement:** Assertion in `generate-app-assets.py` guarantees 0 clipped pixels outside 66dp Android safe circle; verified 100% compliant across circular and squircle masks.
- **Verified Determinism:** Repeated asset generation produces byte-for-byte identical output (SHA-256 match across all outputs).
- **Comprehensive Desktop ICO:** All 7 standard Windows frame sizes (16, 24, 32, 48, 64, 128, 256) are embedded as 32-bit RGBA and verified by classpath unit tests.
- **Security Posture:** Android security rules verified clean (`usesCleartextTraffic="false"`, `allowBackup="false"`, exported components limited to `MainActivity` and `TabbyRushTaskWidgetReceiver`).

---

### Recommended Actions
1. **Stage `docs/assets/tabbyrush-vector.svg`:** Add untracked vector source to git to protect asset generation repeatability.
2. **Keep `keystore_base64.txt` Untracked:** Ensure key file is never staged or committed.
3. **Correct Documentation Values:** Update `docs/design-guidelines.md:195` to state `30×20 px` tray dimensions, and update `docs/project-changelog.md:29` to state `128.95 px / 32.24 dp`.
4. **Clean Generator Script:** Remove unused `io` and `struct` imports in `scripts/generate-app-assets.py`, and adjust `size - 1 - margin` for squircle symmetry.

---

### Metrics
- **Asset Determinism:** 10/10 sampled assets match SHA-256 across consecutive regeneration runs.
- **Android Safe Zone Radius:** 32.24 dp / 128.95 px (limit: 33.0 dp / 132.0 px; safety margin: 0.76 dp / 3.05 px; clipped pixels: 0).
- **Targeted Unit Tests:**
  - `dev.tabbyrush.desktop.AppIconAssetsTest`: 4/4 passing (with `--dependency-verification=off` disclosed due to JNA checksum hold).
  - `dev.tabbyrush.android.AppIconResourcesTest`: 2/2 passing.
  - `:shared:jvmTest`: 239/239 passing.
- **Static Security Audits:**
  - `release-security-check.mjs --diagnose-source`: CLEAN (0 errors).
  - `release-security-check.test.mjs`: 21/21 tests passing.

---

### Unresolved Questions
1. Should `docs/assets/tabbyrush-vector.svg` be committed directly in this changeset, or is it managed in a separate design repository branch? (Recommendation: commit with current changeset).
2. For system tray icons and 16px/24px ICO frames, is a simplified high-contrast mascot head silhouette preferred over the full mascot + roadmap illustration for OS taskbar legibility?
