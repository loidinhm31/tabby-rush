package dev.doggo.shared.theme

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
val DoggoPrimary = Color(0xFF004646)       // Primary teal (10.69:1 on white)
val DoggoPrimaryDeep = Color(0xFF003838)   // Deep primary teal (13.68:1 on white)
val DoggoMint = Color(0xFF27C4B5)          // Mint accent (graphical accent only)
val DoggoMintSurface = Color(0xFFE6F0F2)   // Mint soft background (L ~ 0.86)
val DoggoCream = Color(0xFFF7F4ED)         // Warm cream (L ~ 0.94)
val DoggoCoral = Color(0xFFC62828)         // Functional dark coral (5.63:1 on white, 4.90:1 on coralSurface)
val DoggoCoralSurface = Color(0xFFFFEBEB)  // Coral soft background (L ~ 0.87)
val DoggoLine = Color(0xFFD9E3E4)          // Decorative divider line
val DoggoOutline = Color(0xFF687677)       // Functional interactive border (4.68:1 on white, >= 3:1 non-text)
val DoggoInk = Color(0xFF0F172A)           // Text ink (17.85:1 on white)
val DoggoMuted = Color(0xFF546269)         // Secondary muted text (6.28:1 on white, 5.47:1 on mintSurface)
val DoggoWhite = Color(0xFFFFFFFF)         // Pure white
val DoggoPage = Color(0xFFF8FBFA)          // Cool pale page surface

@Immutable
data class DoggoCustomColors(
    val primaryDeep: Color = DoggoPrimaryDeep,
    val mint: Color = DoggoMint,
    val mintSurface: Color = DoggoMintSurface,
    val cream: Color = DoggoCream,
    val coral: Color = DoggoCoral,
    val coralSurface: Color = DoggoCoralSurface,
    val line: Color = DoggoLine,
    val outline: Color = DoggoOutline,
    val muted: Color = DoggoMuted,
    val page: Color = DoggoPage
)

val LocalDoggoCustomColors = staticCompositionLocalOf { DoggoCustomColors() }

object DoggoThemeTokens {
    val SpacingXSmall: Dp = 4.dp
    val SpacingSmall: Dp = 8.dp
    val SpacingMedium: Dp = 12.dp
    val SpacingLarge: Dp = 16.dp
    val SpacingXLarge: Dp = 24.dp
    val SpacingXXLarge: Dp = 32.dp

    val ControlCornerRadius: Dp = 12.dp
    val CardCornerRadius: Dp = 20.dp
    val MinTouchTargetSize: Dp = 48.dp

    val customColors: DoggoCustomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDoggoCustomColors.current
}

private val DoggoColorScheme: ColorScheme = lightColorScheme(
    primary = DoggoPrimary,
    onPrimary = DoggoWhite,
    primaryContainer = DoggoMintSurface,
    onPrimaryContainer = DoggoPrimaryDeep,
    secondary = DoggoPrimaryDeep,
    onSecondary = DoggoWhite,
    secondaryContainer = DoggoCream,
    onSecondaryContainer = DoggoInk,
    tertiary = DoggoMint,
    onTertiary = DoggoPrimaryDeep,
    background = DoggoPage,
    onBackground = DoggoInk,
    surface = DoggoWhite,
    onSurface = DoggoInk,
    surfaceVariant = DoggoMintSurface,
    onSurfaceVariant = DoggoPrimaryDeep,
    error = DoggoCoral,
    onError = DoggoWhite,
    errorContainer = DoggoCoralSurface,
    onErrorContainer = DoggoCoral,
    outline = DoggoOutline,
    outlineVariant = DoggoLine
)

val DoggoShapes = Shapes(
    small = RoundedCornerShape(DoggoThemeTokens.ControlCornerRadius),
    medium = RoundedCornerShape(DoggoThemeTokens.ControlCornerRadius),
    large = RoundedCornerShape(DoggoThemeTokens.CardCornerRadius)
)

/**
 * Shared Compose theme for Doggo on Android, Linux, and Windows.
 * Enforces accessible light palette derived from ForumFlash reference with verified WCAG AA contrast.
 * Dynamic colors are intentionally disabled to guarantee deterministic contrast.
 */
@Composable
fun DoggoTheme(
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDoggoCustomColors provides DoggoCustomColors()
    ) {
        MaterialTheme(
            colorScheme = DoggoColorScheme,
            typography = DoggoTypography,
            shapes = DoggoShapes,
            content = content
        )
    }
}
