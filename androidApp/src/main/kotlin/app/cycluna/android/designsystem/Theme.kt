package app.cycluna.android.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand tokens, shared with the iOS app's `Theme.swift` value for value.
 *
 * The app is light-only: no dynamic colour, no dark scheme, and `isSystemInDarkTheme()` is
 * never read. The cream/mauve palette is the brand, and the manifest theme sets
 * `forceDarkAllowed=false` so the system cannot invert it either.
 */
object Theme {
    val background = Color(0xFFFAF3EC)
    val surface = Color(0xFFFFFDF9)
    val primary = Color(0xFF6B3FA0)
    val secondary = Color(0xFFD4849A)
    val accent = Color(0xFFE8C97E)

    /** Gold tuned for TEXT and icons. Use this, not [accent], which is too pale to read. */
    val accentText = Color(0xFF8A6A1C)

    val ink = Color(0xFF2D2D2D)
    val inkSoft = Color(0xFF6B6B6B)

    val phaseMenstrual = Color(0xFFD96B6B)
    val phaseFollicular = Color(0xFF5CB37E)
    val phaseOvulatory = Color(0xFFE8B84D)
    val phaseLuteal = Color(0xFF8A5FC2)

    val backgroundGradient = Brush.verticalGradient(listOf(background, Color(0xFFF3E7DA)))
}

/**
 * The display serif. Georgia on iOS; on Android the platform serif, which is Noto Serif on
 * every device this app supports. Sizes stay in `sp` so the user's font-size setting scales
 * them, which is the Android counterpart of the iOS Dynamic Type relativeTo.
 */
val CyclunaSerif = FontFamily.Serif

private val CyclunaColors = lightColorScheme(
    primary = Theme.primary,
    onPrimary = Color.White,
    secondary = Theme.secondary,
    onSecondary = Color.White,
    background = Theme.background,
    onBackground = Theme.ink,
    surface = Theme.surface,
    onSurface = Theme.ink,
    surfaceVariant = Theme.surface,
    onSurfaceVariant = Theme.inkSoft,
    error = Theme.phaseMenstrual,
)

@Composable
fun CyclunaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyclunaColors,
        typography = Typography(),
        content = content,
    )
}

/** Display serif at a given size — the counterpart of `Font.cyclunaSerif(_:)`. */
fun serif(size: Int): TextStyle = TextStyle(fontFamily = CyclunaSerif, fontSize = size.sp)

/**
 * Solid cream card with a hairline border and a soft mauve shadow (web `Card shadow-soft`).
 * Order matters: the shadow has to be drawn before the background, or it lands on top of it.
 */
fun Modifier.cyclunaCard(padding: Dp = 20.dp, radius: Dp = 20.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .fillMaxWidth()
        .shadow(elevation = 8.dp, shape = shape, ambientColor = Theme.primary, spotColor = Theme.primary)
        .background(Theme.surface, shape)
        .border(1.dp, Theme.inkSoft.copy(alpha = 0.12f), shape)
        .padding(padding)
}
