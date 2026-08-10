package app.cycluna.android.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset

/**
 * The Cycluna crescent — the same mark as the app icon and the launch screen, so the three
 * read as one identity rather than three different moons.
 *
 * Geometry is copied from the icon: a disc with a second disc punched out of it, offset up
 * and to the right. Punching requires an offscreen layer, otherwise `BlendMode.Clear` has
 * nothing to clear and erases the whole window.
 */
private const val PUNCH_DIAMETER = 0.806f
private const val PUNCH_OFFSET_X = 0.355f
private const val PUNCH_OFFSET_Y = -0.274f

fun DrawScope.drawCrescent(brush: Brush) {
    val d = size.minDimension
    val r = d / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    drawCircle(brush = brush, radius = r, center = centre)
    drawCircle(
        color = Color.Black,
        radius = d * PUNCH_DIAMETER / 2f,
        center = Offset(centre.x + d * PUNCH_OFFSET_X, centre.y + d * PUNCH_OFFSET_Y),
        blendMode = BlendMode.Clear,
    )
}

/** The crescent as a standalone composable, for tab icons and decorative marks. */
@androidx.compose.runtime.Composable
fun Crescent(modifier: Modifier = Modifier, brush: Brush = Brush.linearGradient(listOf(Theme.primary, Theme.secondary))) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawCrescent(brush)
    }
}
