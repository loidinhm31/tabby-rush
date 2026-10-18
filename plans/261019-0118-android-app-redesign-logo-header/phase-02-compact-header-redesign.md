# Phase 02: Compact Header Redesign

## Context Links
- Parent Plan: [plan.md](plan.md)
- Root UI Composable: [shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt](../../shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt)
- Design Tokens: [shared/src/commonMain/kotlin/dev/tabbyrush/shared/theme/tabbyrush-theme.kt](../../shared/src/commonMain/kotlin/dev/tabbyrush/shared/theme/tabbyrush-theme.kt)

## Overview
- Date: 2026-10-19
- Description: Replace text title with logo icon and optimize top action button padding and layout in `<840dp` compact header.
- Priority: P2
- Implementation Status: Completed
- Review Status: Accepted

## Key Insights
- The text "TabbyRush" currently consumes ~110dp of width. Replacing it with a 36×36dp logo frees ~74dp of horizontal room.
- Default Material 3 `TextButton` and `Button` padding adds 24dp of horizontal padding per control, which causes unnecessary wrapping. Setting `PaddingValues(horizontal = 8.dp, vertical = 0.dp)` maintains touch bounds while preserving text readability.
- Adding `Modifier.horizontalScroll(rememberScrollState())` to the buttons row protects narrow screens (320dp) and 200% font scale from truncation.
- The logo requires explicit TalkBack accessibility semantics: `contentDescription = "TabbyRush"` and heading role.
- User identity label (e.g. "Offline" or username) can be displayed cleanly beneath the logo with `maxLines = 1` and ellipsis.

## Requirements
1. Render `Image(painter = painterResource(Res.drawable.tabbyrush_logo), contentDescription = "TabbyRush", modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).semantics { heading() })`.
2. Keep user label compact beneath the logo.
3. Optimize button internal padding to `PaddingValues(horizontal = 8.dp, vertical = 0.dp)`.
4. Ensure all action buttons retain `>= 48dp` touch target height (`defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize)`).
5. Ensure unread count badge on "Inbox" button renders cleanly with correct contrast and no clipping.
6. Support narrow viewports gracefully with a horizontal scroll fail-safe.

## Architecture
Updates in `AuthenticatedWorkspace` compact branch (`else` branch of `isExpanded` in `tabbyrush-app.kt`).

## Related Code Files
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt`

## Implementation Steps
1. Import `org.jetbrains.compose.resources.painterResource` and `dev.tabbyrush.shared.resources.Res` and `dev.tabbyrush.shared.resources.tabbyrush_logo`.
2. Replace title `Text("TabbyRush")` with logo `Image` in compact Scaffold `topBar`.
3. Wrap buttons in an alignment row with tuned padding and horizontal scroll.
4. Verify compiler output on shared and android targets.

## Todo List
- [x] Implement logo replacing text in compact header
- [x] Tune action button content padding and row layout
- [x] Add horizontal scroll fail-safe for narrow viewports
- [x] Preserve accessibility semantics and touch targets

## Success Criteria
- Top bar displays logo instead of text title on compact viewports.
- Action button labels ("Add", "Inbox"/"Tasks", "Settings", "Notices", "Leave") are fully legible without truncation on 360dp screens.
- All touch targets remain >= 48dp.

## Risk Assessment
- Low risk: Visual layout change in compact viewport branch only.

## Security Considerations
- Zero auth or network impact.

## Next Steps
- Proceed to Phase 03: Multiplatform Validation & Review.
