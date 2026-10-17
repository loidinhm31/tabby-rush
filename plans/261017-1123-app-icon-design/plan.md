---
title: "TabbyRush app icon design and platform delivery"
description: "Deliver one canine-and-check identity across Android adaptive icons, desktop windows and tray, and native packages."
status: completed
priority: P2
effort: 10h
branch: main
tags: [feature, frontend, design, android, desktop]
created: 2026-10-17
---

# TabbyRush app icon

## Goal and frozen decisions
- Design **Companion Check**: recognizable TabbyRush head silhouette + unmistakable task checkmark; geometric, calm, no lettering or fine facial detail.
- Palette tokens: `TabbyRushPrimary #004646`, `TabbyRushPrimaryDeep #003838`, `TabbyRushMint #27C4B5`, `TabbyRushCream #F7F4ED`, `TabbyRushWhite #FFFFFF`, and `TabbyRushInk #0F172A`. Mint accents sit against teal/shadow, not white/cream.
- One editable SVG source; Android vectors and desktop PNG/ICO are derived deliverables. Both desktop windows receive the same runtime icon; both target trays receive the same small-size export.
- Keep existing desktop classpath `painterResource(String)` convention for this change. Its deprecation is acknowledged, not a reason for a resource-framework migration.
- Explicitly out of scope: themed/monochrome Android icons, API <=25 rasters (minSdk 26), macOS, notification/widget icon redesign, app logic, dependency upgrades, new asset pipeline.
- Architecture gate: presentation/asset-only change; no component, state-machine, storage, sync, credential, or source-set boundary changes. Architecture diagrams intentionally unchanged.
- Inputs: [Android research](research/researcher-01-android-icons.md), [desktop/concept research](research/researcher-02-desktop-and-metaphor.md), [design guidelines](../../docs/design-guidelines.md), [architecture](../../docs/system-architecture.md#49-native-desktop-vault--distribution-phases-0910).

## Phases and dependency gates
```text
01 master/spec freeze -> [02 Android || 03 Desktop] -> 04 documentation + verification
```
| Phase | Status | Effort | Exit gate |
|---|---|---|---|
| [01 Master Vector Icon Design](phase-01-master-vector-icon-design.md) | Completed | 3h | G1: SVG, concept, palette, safe geometry and export contract frozen |
| [02 Android Adaptive Icon Assets & Manifest Integration](phase-02-android-adaptive-icons.md) | Completed | 1h | G2: four resource XMLs + both manifest references integrated; security attributes untouched |
| [03 Desktop Runtime & Tray Icons](phase-03-desktop-runtime-and-tray-icons.md) | Completed | 2h | G3: runtime PNG, two tray PNGs, multi-size ICO, both windows and native package config integrated |
| [04 Documentation & Verification](phase-04-documentation-and-verification.md) | Delivered (automated unit & decodability passing; native visual acceptance conditional) | 4h | G4: decoding, source security, unit/build checks and native visual evidence recorded without overstating acceptance |

## Exclusive file ownership
All paths repository-relative. `A = androidApp/src/main`; `D = desktopApp/src`; `P = plans/261017-1123-app-icon-design`.
| Owner | Writable files | Action |
|---|---|---|
| Phase 01 | `docs/assets/tabbyrush-app-icon.svg` | Create canonical vector; retain layer IDs/export geometry |
| Phase 02 | `A/res/mipmap-anydpi-v26/ic_launcher.xml`; `A/res/mipmap-anydpi-v26/ic_launcher_round.xml`; `A/res/drawable/ic_launcher_foreground.xml`; `A/res/drawable/ic_launcher_background.xml` | Create adaptive resources and layers |
| Phase 02 | `A/AndroidManifest.xml` | Replace placeholder icon; add roundIcon only |
| Phase 03 | `D/main/resources/tabbyrush-icon.png`; `D/windowsMain/resources/tabbyrush.ico` | Create runtime and Windows packaging assets |
| Phase 03 | `D/linuxMain/resources/task-surface-tray.png`; `D/windowsMain/resources/task-surface-tray.png` | Replace both old blue/violet tray icons |
| Phase 03 | `desktopApp/build.gradle.kts`; `D/main/kotlin/dev/tabbyrush/desktop/main.kt` | Configure Linux PNG/Windows ICO packaging; assign runtime painter to both windows |
| Phase 04 | `D/test/kotlin/dev/tabbyrush/desktop/app-icon-assets-test.kt`; `androidApp/src/test/kotlin/dev/tabbyrush/android/app-icon-resources-test.kt` | Create dependency-free asset/resource contract tests |
| Phase 04 | `docs/design-guidelines.md`; `docs/project-changelog.md`; `docs/development-roadmap.md`; `P/reports/icon-verification.md` | Document final design/export recipe, delivery status and actual evidence |
| Main integration owner only | `P/plan.md`; `P/phase-*.md` | Maintain phase status and frozen contracts |
- No shared writable implementation files between Phases 02/03. No deletion, aliases, placeholder fallbacks, or lockfile/version-catalog changes expected.
- Read-only inputs remain read-only, including research reports, shared code, notification artwork and architecture docs. Out-of-scope edits require an explicit ownership reassignment before work.

## Execution and validation policy
1. Finish Phase 01 and review G1 before downstream work. Freeze layer IDs, geometry, palette and export dimensions; downstream phases consume SVG read-only.
2. If delegation is available, spawn exactly two implementers together for Phases 02/03; wait for both terminal results and changed-file lists, then synthesize. Otherwise execute the same disjoint slices inline.
3. Implementers do not run builds/tests/linters/formatters. G2/G3 are deliverable/source-review gates, not claims of tested rendering. Route design defects to Phase 01 owner, never edit their SVG concurrently.
4. Phase 04 adds tests/docs after both slices land. Main verification owner alone executes its authorized checks, once on the integrated state; failures return to their file owner and targeted reruns follow fixes.
5. Preserve strict dependency verification. A known provenance/environment hold stays a recorded blocker; never silently add `--dependency-verification=off`, change checksums, or claim a skipped test passed.
6. Native launcher/tray/taskbar/installer review is a separate acceptance gate. Missing Android/Linux/Windows access keeps affected rows pending; unit decoding and design previews cannot close them.

## Definition of done
All named assets/configuration and both window callsites delivered; SVG and platform art visually consistent; security invariants unchanged; tests and decodability checks pass; native icon evidence recorded; design/changelog/roadmap truthful. Implementation cannot be marked technically complete while a required gate is unperformed.

## Unresolved questions
- None requiring product clarification: Companion Check, shared round artwork and both-window branding selected. Tool availability, strict-provenance holds and native platform access are execution prerequisites to record in Phase 04, not assumed successes.
