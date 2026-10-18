# Plan: Android App Header Redesign

## Access Point
This plan replaces the "TabbyRush" brand text title with an accessible compact brand logo in the compact mobile top bar (<840dp) to maximize space for top action buttons.

## Implementation Phases
- [x] Phase 00: Discovery & Preflight (Completed)
- [x] [Phase 01: Logo Asset Provisioning](phase-01-logo-asset-provisioning.md) - Completed
- [x] [Phase 02: Compact Header Redesign](phase-02-compact-header-redesign.md) - Completed
- [x] [Phase 03: Multiplatform Validation & Review](phase-03-validation-and-review.md) - Completed

## Key Architectural Decisions
- Use Compose Multiplatform resources (`Res.drawable.tabbyrush_logo`) under `shared/src/commonMain/composeResources/drawable/`.
- Provide high-resolution 256×256 PNG logo badge for cross-platform stability (Android, Desktop JVM).
- Enforce >=48dp touch targets (`TabbyRushThemeTokens.MinTouchTargetSize`) while adjusting internal padding.
- Retain desktop expanded layout (>=840dp) unchanged.
