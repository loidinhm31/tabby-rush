# Phase 01 — Master Vector Icon Design

## Overview
- Priority: P2. Status: pending. Effort: 3h. Dependency: research complete. Owner: vector designer.
- Output: `docs/assets/tabbyrush-app-icon.svg` and a frozen concept/export contract below. No application edits or builds.
- Context: [plan](plan.md), [concept research](research/researcher-02-desktop-and-metaphor.md#four-distinct-concepts), [Android geometry research](research/researcher-01-android-icons.md#layer-dimensions-safe-areas-and-artwork-constraints), [brand guidelines](../../docs/design-guidelines.md).

## Key insights and requirements
- Commit to **Companion Check**, not four competing production variants: one friendly dog-head silhouette with broad folded/floppy ears plus a bold completion check. Dog identity must survive removal of the accent.
- Solid teal field `#004646`; cream `#F7F4ED` dog silhouette; white `#FFFFFF` check inset in a teal lower-head/collar area; small mint `#27C4B5` collar/tag accent on teal. Keep the check visually separate from cream so it does not disappear into the head.
- Check shape, not mint hue alone, communicates task completion. No letters, fur, tiny eyes, hairlines, gradients, filters or shadows. Do not use alert coral or introduce additional palette tokens.
- The committed source uses flat paths/groups only: editable, self-contained, no embedded raster, font dependency, scripts, linked images, external styles or network resources. Expand strokes if necessary for equivalent Android paths.

## Architecture and asset contract
- Create only `docs/assets/tabbyrush-app-icon.svg`; delete none. Master has `viewBox="0 0 108 108"` and intrinsic square dimensions.
- Stable group IDs: `icon-background` (opaque teal full 108-square canvas) and `icon-foreground` (transparent outside dog/check/accent geometry). Include a title/description identifying TabbyRush and the companion/completion concept.
- Android layers retain this exact coordinate space. All essential foreground points, including ear/check extrema, fit the centered radius-33 circle at `(54,54)`: `(x-54)^2 + (y-54)^2 <= 33^2`. Its bounding square is not itself safe; check its corner geometry explicitly.
- The central 72-square at coordinates 18..90 is the mask viewport, **not** the guaranteed safe circle. Full background reaches all edges, with no baked launcher rounding/mask or shadow.
- Desktop export projection: center the same foreground with modest uniform enlargement inside an opaque teal rounded badge; outside badge corners are transparent. Record exact scale/inset/radius used when freezing G1. This desktop-only framing must never enter Android layers.
- Runtime PNG: 512×512 RGBA, sRGB. Tray PNG: 32×32 RGBA, optically adjusted from the same source geometry (wider check/negative space, drop mint detail if necessary). ICO: 16, 24, 32, 48 and 256 square RGBA-derived frames; small frames use the tray simplification. Dimensions are selected export targets, not Compose API limits.
- Freeze numeric export settings and small-size simplifications in this phase's handoff; Phase 04 copies final settings into the existing design guidelines. Do not create a second independent brand specification.

## Implementation steps
1. Draw the head, ears and check with generous negative space. Make the silhouette recognizable without facial features and the check legible independently.
2. Put every essential foreground feature within the Android safe circle; keep the teal background edge-to-edge. Review circle/squircle/rounded-square previews, not only a square canvas.
3. Define desktop badge projection and tray simplification from the same foreground paths. Do not add a second hand-maintained vector or a bespoke generator framework.
4. Hand main owner the SVG plus numeric settings and approved small-size treatment. Main owner performs the G1 visual/source review before downstream asset generation; no builds/unit suites during this phase.
5. Record exporter name/version and exact reproducible commands or editor export settings with the handoff; prefer installed vector/raster tooling, never a new application dependency.

## Success criteria — G1
- Master opens as self-contained SVG; approved dog + check visible at 512/256 and small-size previews at 32/24/16. Tiny accent may disappear; core dog/check may not merge or clip.
- Only the four brand colors plus transparency used as authored fills; anti-aliasing blends in derived rasters are expected.
- Full-bleed background, transparent foreground and safe-circle bounds explicit; square/circle/squircle previews do not lose essential shapes.
- Export dimensions, desktop framing, path groups and small-size treatment frozen. Main owner records review outcome, not automatic proof of on-device visibility.

## Risks and security
- Ears/check clipped by OEM masks: enforce circle geometry before export; do not shrink after each downstream failure independently.
- Mint on cream/white loses contrast: keep accent against teal; use white check on teal. Do not claim global WCAG compliance for a launcher logo.
- Format drift: Android conversion must remain filled vector paths; no SVG features unsupported by VectorDrawable.
- Artwork is original repository-owned geometry; no third-party logos, uploaded private screenshots, remote fetching or new runtime library.

## Next gate / unresolved questions
G1 unlocks Phases 02 and 03. No unresolved product questions; exact coordinates and export settings are design outputs, not a prerequisite to start this phase.
