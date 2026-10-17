# Desktop Icon Integration & TabbyRush Icon Concepts

**Research date:** 2026-10-17  
**Scope:** Compose Multiplatform desktop `Window`/`Tray` icons, Linux RPM and Windows MSI packaging, asset dimensions/formats, palette, and canine/task metaphors.  
**Project version:** Compose Multiplatform plugin `1.7.3` ([version catalog](../../../gradle/libs.versions.toml#L2-L4)).

## Findings

### Existing integration and assets

- `main.kt` imports the legacy Java-resource API `androidx.compose.ui.res.painterResource` and passes `painterResource("task-surface-tray.png")` to `Tray(icon = ...)` ([source](../../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt#L6-L10), [tray/window calls](../../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt#L1016-L1088)). The main-app and sticky `Window`s do not currently pass an `icon`. Compose Multiplatform 1.7.3 deprecates the Java resource APIs in `compose.ui`; its recommended path is the multiplatform resources library.
- `build.gradle.kts` adds both `src/main/resources` and the selected `src/${desktopTarget}Main/resources` to the desktop runtime classpath ([source](../../../desktopApp/build.gradle.kts#L28-L36)), so the Linux and Windows tray PNGs are target-specific classpath resources with the same lookup name.
- Both [`task-surface-tray.png` files](../../../desktopApp/src/linuxMain/resources/task-surface-tray.png) ([Windows counterpart](../../../desktopApp/src/windowsMain/resources/task-surface-tray.png)) are 32×32 RGBA PNGs and byte-identical. Visual content: blue-violet (`#4F46E5`) circle with white check; no canine cue and no TabbyRush palette color. Replace both target assets together to keep Linux/Windows tray identity aligned.
- `Tray` and `Window` take a `Painter`, not a fixed pixel size. For this project's legacy `painterResource(String)` path, the Compose 1.7.3 migration example handles `.svg` via SVG decoding, `.xml` via vector decoding, and other files via bitmap decoding (PNG/JPEG/BMP/WEBP are documented bitmap inputs). This Java-resource API is deprecated in 1.7.3; the newer `org.jetbrains.compose.resources.painterResource(Res.drawable...)` API is preferred for new Compose resources. PNG remains the simplest compatible raster deliverable; SVG is also usable through the legacy desktop loader, but is not required.

### Runtime versus packaged icons

| Surface | Recommended asset | Integration / constraint |
|---|---|---|
| System tray | Context-optimized **32×32 RGBA PNG** | Keep the existing `Tray(icon = painterResource("task-surface-tray.png"))` integration. 32×32 is a practical, verified source size, not a Compose API requirement. Windows tray presentation varies with scale: Microsoft lists 16, 20, 24, 32, 40, 48, and 64 px. A 32 px PNG can be enlarged at higher scale; an SVG or higher-resolution raster avoids that source-resolution limit. Still draw an optical small-size variant and verify real host rendering. |
| Window and Windows/Linux taskbar | **256×256 minimum high-resolution raster; 512×512 design/export master is useful** | Pass a sufficiently detailed `Painter` as `Window(icon = ...)` on the main and, if desired, sticky windows. Compose documents that this icon supplies the Windows/Linux window and taskbar icon. No fixed pixel size is imposed by the `Window` API. |
| Linux RPM application/launcher | Platform package `.png`; use 256×256 or 512×512 square art | Set `linux { iconFile.set(project.file("...png")) }` under `nativeDistributions`. Freedesktop specifies PNG as the recommended bitmap format and supports nominal sizes/scales rather than one mandatory size. 512 px is a high-resolution export choice, not an RPM/Compose requirement. |
| Windows MSI application/shortcut | Platform package `.ico` with multiple embedded sizes | Set `windows { iconFile.set(project.file("...ico")) }`. Microsoft's Win32 baseline: 16×16, 24×24, 32×32, 48×48, 256×256; include 256 so Windows scales down rather than up. A 512×512 PNG can be retained as the source master, but is not a substitute for the multi-size ICO. |
| macOS Dock (not a current target) | `.icns` distribution asset | Compose’s `Window(icon = ...)` does not set the macOS Dock icon; package config does. This repository targets Linux/Windows only, so Dock is currently out of scope. |

Current `nativeDistributions` targets `TargetFormat.Rpm` and `TargetFormat.Msi`, but does not set `iconFile` ([Gradle config](../../../desktopApp/build.gradle.kts#L61-L90)). Runtime `Painter` resources and distribution icons are separate channels: adding/replacing the PNG alone does not configure RPM/MSI branding. Use `linux.iconFile` for PNG and `windows.iconFile` for ICO. Keep PNG/ICO at stable module-owned paths available to the corresponding packaging build; `project.file(...)` paths are relative to the `desktopApp` Gradle project.

**Dimension interpretation:** Treat 32 px as the tray-optical design target, 256 px as the minimum broad-use Windows/runtime raster master, and 512 px as an optional large master for export and Linux artwork. Compose does not require these sizes. Windows recommends a multi-size ICO including 16, 24, 32, 48, and 256 px; its tray may render above 32 px at elevated scale. A separate small-size drawing is preferable to shrinking detailed artwork blindly.

## Brand and contrast

The design guideline calls for a focused, calm, clean geometric style: deep teal structure, soft mint containers, warm cream surfaces, with restrained coral for alerts ([design guidelines](../../../docs/design-guidelines.md#L9-L36)). Icon guidance:

- **TabbyRushPrimary `#004646` / PrimaryDeep `#003838`:** use as the core silhouette or badge field. White-on-primary is documented at **10.69:1**; white-on-deep is **12.94:1**.
- **TabbyRushMint `#27C4B5`:** use as a restrained collar/tag/check accent, preferably against dark teal. Guideline explicitly warns mint on white is only **2.18:1** and should be limited to graphical accents or paired with dark teal (`docs/design-guidelines.md:62`). Calculated relative-luminance ratios: mint on primary **4.91:1**, mint on primary deep **5.95:1**.
- **TabbyRushCream `#F7F4ED` / TabbyRushWhite `#FFFFFF`:** use for a dog/check cut-out against teal. Cream-on-primary is **9.73:1**; white-on-primary is **10.69:1**. Cream preserves the warm brand tone; white is the crispest check at 32 px.
- Avoid coral `#C62828` as the default mark; it is the functional error/destructive accent, not the normal task-completion brand color.
- Prefer a clear outer silhouette, generous negative space, and no lettering, fine fur, multiple task rows, or subtle gradients. Transparent pixels outside a solid teal badge/silhouette help the icon sit cleanly in variable tray backgrounds. Verify each concept at 32 px: distinguish dog and completion with one high-contrast check/badge, not detail.

## Four distinct concepts

1. **Companion Check — recommended starting direction.** A simple dog-head silhouette with floppy/folded ears; a single mint check becomes the collar tag or negative-space cheek mark. Use teal as the silhouette/badge, cream or white for the dog/check. At 32 px retain only head + one legible check; omit eyes and tiny collar details. Direct, compact pairing of companion and task completion.
2. **Collar-Tag Done.** A geometric dog bust/collar forms a stable frame; a round or softly squared tag at the center contains a bold check. Teal dog/collar, mint tag rim, white/cream check. At 32 px reduce collar to one broad stroke and tag to one check. More emblem-like than a literal checkbox.
3. **Folded-Ear Checklist.** A folded dog ear doubles as the check stroke; the head/neck silhouette aligns with one or two short list bars, reading as a completed list without text. Teal body, cream bars, mint checked row. For tray scale, collapse the list to one checked line so it does not become clutter.
4. **Focus Pup Badge.** A compact circular focus badge integrates a pup profile/ears with a central check, suggesting a calm companion that keeps tasks on track. Teal badge field, cream/white pup or check, a small mint focus ring/tag. Avoid several concentric rings or multiple paw dots at 32 px; keep this more abstract than concept 1.

**Selection guidance:** Start with concept 1 for the best likely read at 32×32 and 256+ px: one dog silhouette plus one unmistakable completion mark. Keep concept 2 as the more ownable alternate if a collar/tag feels like a better TabbyRush-specific motif. Deliver a tray-optimized small-size version and large exports from one vector/master drawing; do not simply scale detailed artwork down.

## Sources

- Project implementation: [`main.kt`](../../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt#L6-L10), [tray/window declarations](../../../desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt#L1016-L1088); [`build.gradle.kts`](../../../desktopApp/build.gradle.kts#L28-L36), [`nativeDistributions`](../../../desktopApp/build.gradle.kts#L61-L90); [`libs.versions.toml`](../../../gradle/libs.versions.toml#L2-L4); [design guidelines](../../../docs/design-guidelines.md#L9-L63).
- JetBrains, [Images and in-app icons](https://kotlinlang.org/docs/multiplatform/compose-desktop-images.html) — `Painter` icons, supported bundled raster formats, Windows/Linux taskbar behavior, macOS Dock packaging distinction.
- JetBrains, [What's new in Compose Multiplatform 1.7.3](https://kotlinlang.org/docs/multiplatform/whats-new-compose-170.html) — Java `compose.ui` resource API deprecation and recommendation to use the multiplatform resources library.
- JetBrains Compose Multiplatform, [resource API migration PR #1457](https://github.com/JetBrains/compose-multiplatform-core/pull/1457) — legacy Java-resource `painterResource(String)` decoding path: SVG, XML vector, and bitmap.
- JetBrains, [Tray and notifications](https://kotlinlang.org/docs/multiplatform/compose-desktop-tray.html) — `Tray(icon: Painter)` and tray availability behavior.
- JetBrains, [Native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html) — `iconFile.set(...)`, `.png` Linux / `.ico` Windows / `.icns` macOS and native package formats.
- Microsoft, [Construct your Windows app's icon](https://learn.microsoft.com/en-us/windows/apps/design/iconography/app-icon-construction) — varying tray/taskbar pixel sizes, Win32 minimum multi-size icon set, transparent background guidance.
- Freedesktop, [Icon Theme Specification](https://specifications.freedesktop.org/icon-theme/latest/) — PNG recommended bitmap format; nominal icon sizes/scales.
## Validation and limitations

Findings were checked against the local Kotlin source, Gradle source-set/package configuration, design tokens, PNG dimensions/alpha, and a SHA-256 comparison of both tray assets; Compose 1.7.3, JetBrains, Microsoft, and Freedesktop documentation were cross-referenced. No build, package generation, or platform-rendering check was run. Treat size/contrast recommendations as design guidance until the actual Linux tray/taskbar and Windows tray/taskbar/package icons are reviewed on host platforms.


## Unresolved questions

- Which of the four concepts best matches the intended balance of playful companion versus productivity tool? Research favors concept 1 for small-size legibility, but final selection is a brand decision.
- Should the main and sticky `Window` both use the same runtime `Painter`, or should sticky tasks remain unbranded at the title-bar level? Current code sets neither window icon.
- Is macOS support intended later? It is not configured as a current desktop target; if added, a separate `.icns` package icon is needed.
