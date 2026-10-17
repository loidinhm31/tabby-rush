package dev.tabbyrush.shared.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ForumFlash measured light palette with functional WCAG AA contrast adjustments
val TabbyRushPrimary = Color(0xFF004646)       // Primary teal (10.69:1 on white)
val TabbyRushPrimaryDeep = Color(0xFF003838)   // Deep primary teal (13.68:1 on white)
val TabbyRushMint = Color(0xFF27C4B5)          // Mint accent (graphical accent only)
val TabbyRushMintSurface = Color(0xFFE6F0F2)   // Mint soft background (L ~ 0.86)
val TabbyRushCream = Color(0xFFF7F4ED)         // Warm cream (L ~ 0.94)
val TabbyRushCoral = Color(0xFFC62828)         // Functional dark coral (5.63:1 on white, 4.90:1 on coralSurface)
val TabbyRushCoralSurface = Color(0xFFFFEBEB)  // Coral soft background (L ~ 0.87)
val TabbyRushLine = Color(0xFFD9E3E4)          // Decorative divider line
val TabbyRushOutline = Color(0xFF687677)       // Functional interactive border (4.68:1 on white, >= 3:1 non-text)
val TabbyRushInk = Color(0xFF0F172A)           // Text ink (17.85:1 on white)
val TabbyRushMuted = Color(0xFF546269)         // Secondary muted text (6.28:1 on white, 5.47:1 on mintSurface)
val TabbyRushWhite = Color(0xFFFFFFFF)         // Pure white
val TabbyRushPage = Color(0xFFF8FBFA)          // Cool pale page surface

@Immutable
data class TabbyRushCustomColors(
    val primaryDeep: Color = TabbyRushPrimaryDeep,
    val mint: Color = TabbyRushMint,
    val mintSurface: Color = TabbyRushMintSurface,
    val cream: Color = TabbyRushCream,
    val coral: Color = TabbyRushCoral,
    val coralSurface: Color = TabbyRushCoralSurface,
    val line: Color = TabbyRushLine,
    val outline: Color = TabbyRushOutline,
    val muted: Color = TabbyRushMuted,
    val page: Color = TabbyRushPage
)

val LocalTabbyRushCustomColors = staticCompositionLocalOf { TabbyRushCustomColors() }

object TabbyRushThemeTokens {
    val SpacingXSmall: Dp = 4.dp
    val SpacingSmall: Dp = 8.dp
    val SpacingMedium: Dp = 12.dp
    val SpacingLarge: Dp = 16.dp
    val SpacingXLarge: Dp = 24.dp
    val SpacingXXLarge: Dp = 32.dp

    val ControlCornerRadius: Dp = 12.dp
    val CardCornerRadius: Dp = 20.dp
    val MinTouchTargetSize: Dp = 48.dp

    val customColors: TabbyRushCustomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTabbyRushCustomColors.current
}

private val TabbyRushColorScheme: ColorScheme = lightColorScheme(
    primary = TabbyRushPrimary,
    onPrimary = TabbyRushWhite,
    primaryContainer = TabbyRushMintSurface,
    onPrimaryContainer = TabbyRushPrimaryDeep,
    secondary = TabbyRushPrimaryDeep,
    onSecondary = TabbyRushWhite,
    secondaryContainer = TabbyRushCream,
    onSecondaryContainer = TabbyRushInk,
    tertiary = TabbyRushMint,
    onTertiary = TabbyRushPrimaryDeep,
    background = TabbyRushPage,
    onBackground = TabbyRushInk,
    surface = TabbyRushWhite,
    onSurface = TabbyRushInk,
    surfaceVariant = TabbyRushMintSurface,
    onSurfaceVariant = TabbyRushPrimaryDeep,
    error = TabbyRushCoral,
    onError = TabbyRushWhite,
    errorContainer = TabbyRushCoralSurface,
    onErrorContainer = TabbyRushCoral,
    outline = TabbyRushOutline,
    outlineVariant = TabbyRushLine
)

val TabbyRushShapes = Shapes(
    small = RoundedCornerShape(TabbyRushThemeTokens.ControlCornerRadius),
    medium = RoundedCornerShape(TabbyRushThemeTokens.ControlCornerRadius),
    large = RoundedCornerShape(TabbyRushThemeTokens.CardCornerRadius)
)

/**
 * Shared Compose theme for TabbyRush on Android, Linux, and Windows.
 * Enforces accessible light palette derived from ForumFlash reference with verified WCAG AA contrast.
 * Dynamic colors are intentionally disabled to guarantee deterministic contrast.
 */
@Composable
fun TabbyRushTheme(
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTabbyRushCustomColors provides TabbyRushCustomColors()
    ) {
        MaterialTheme(
            colorScheme = TabbyRushColorScheme,
            typography = TabbyRushTypography,
            shapes = TabbyRushShapes,
            content = content
        )
    }
}
