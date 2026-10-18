# Phase 01: Logo Asset Provisioning

## Context Links
- Parent Plan: [plan.md](plan.md)
- Design Guidelines: [docs/design-guidelines.md](../../docs/design-guidelines.md)
- Shared build configuration: [shared/build.gradle.kts](../../shared/build.gradle.kts)

## Overview
- Date: 2026-10-19
- Description: Provision deterministic logo PNG asset in shared Compose Multiplatform resources.
- Priority: P2
- Implementation Status: Completed
- Review Status: Accepted

## Key Insights
- JetBrains Compose Multiplatform in `shared` has `compose.components.resources` enabled with package `dev.tabbyrush.shared.resources`.
- `desktopApp/src/main/resources/tabbyrush-icon.png` is already a 256×256 squircle branded badge.
- Provisioning `shared/src/commonMain/composeResources/drawable/tabbyrush_logo.png` allows `Res.drawable.tabbyrush_logo` generation across all targets.

## Requirements
1. Copy or generate `tabbyrush_logo.png` into `shared/src/commonMain/composeResources/drawable/`.
2. Ensure file permissions and git tracking are clean.
3. Verify Gradle resource generator detects the asset without error.

## Architecture
Compose Multiplatform Resource Generator processes `composeResources/drawable/` and produces strongly-typed accessors on `dev.tabbyrush.shared.resources.Res.drawable`.

## Related Code Files
- `shared/src/commonMain/composeResources/drawable/tabbyrush_logo.png`
- `shared/build.gradle.kts`

## Implementation Steps
1. Create directory `shared/src/commonMain/composeResources/drawable` if not present.
2. Copy `desktopApp/src/main/resources/tabbyrush-icon.png` to `shared/src/commonMain/composeResources/drawable/tabbyrush_logo.png`.
3. Verify compilation and accessor generation.

## Todo List
- [x] Create drawable resource directory
- [x] Place `tabbyrush_logo.png`
- [x] Verify Gradle resource accessor generation

## Success Criteria
- `:shared:generateComposeResClass` succeeds and `Res.drawable.tabbyrush_logo` is available.

## Risk Assessment
- Low risk: Resource addition only; does not affect existing strings or resources.

## Security Considerations
- Pure visual raster asset, zero dynamic execution or secrets.

## Next Steps
- Proceed to Phase 02: Compact Header Redesign.
