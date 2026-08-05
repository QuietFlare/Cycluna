package app.cycluna.android.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val LIT = Color(0xFFEAD59B)
private val SHADOW = Color(0xFF20152A)
private val HIGHLIGHT = Color(0xFFFFF6E0)
private val LIMB = Color(0xFFC9A24A)
private val EDGE = Color(0xFFB48C32)

/**
 * A real moon-phase disc: a lit gold sphere with the shaded portion drawn from the true
 * illumination fraction. The terminator is a proper half-ellipse rather than a flat symbol,
 * so crescents and gibbous phases read correctly. Waxing lights the right limb, waning the
 * left.
 */
@Composable
fun MoonDisc(
    illumination: Double,   // 0 (new) .. 1 (full)
    waxing: Boolean,
    modifier: Modifier = Modifier,
    lit: Color = LIT,
) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)

        // Soft sphere shading on the lit base, lit from the upper left.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(HIGHLIGHT, lit, LIMB),
                center = Offset(c.x - r * 0.28f, c.y - r * 0.3f),
                radius = r * 1.15f,
            ),
            radius = r,
            center = c,
        )

        drawPath(
            path = shadowPath(c, r, illumination.coerceIn(0.0, 1.0).toFloat(), waxing),
            color = SHADOW.copy(alpha = 0.9f),
        )

        drawCircle(
            color = EDGE.copy(alpha = 0.35f),
            radius = r,
            center = c,
            style = Stroke(width = 0.75.dp.toPx()),
        )
    }
}

/**
 * The unlit region: the dark limb's semicircle, closed by the terminator ellipse.
 *
 * The terminator is two cubic quarter-ellipses using the circle-approximation constant —
 * the same construction as the iOS twin. Getting this right is what makes a waxing gibbous
 * look like a sphere rather than a pac-man.
 */
private const val KAPPA = 0.5523f

private fun shadowPath(c: Offset, r: Float, k: Float, waxing: Boolean): Path {
    // Terminator's horizontal offset at mid-height: +r at new, -r at full.
    val a = (1f - 2f * k) * r * (if (waxing) 1f else -1f)
    val topY = c.y - r

    return Path().apply {
        moveTo(c.x, topY)
        // The dark limb: the semicircle on the shadow side (left when waxing).
        arcTo(
            rect = Rect(c.x - r, c.y - r, c.x + r, c.y + r),
            startAngleDegrees = -90f,
            sweepAngleDegrees = if (waxing) -180f else 180f,
            forceMoveTo = false,
        )
        // Terminator back up to the top.
        cubicTo(
            c.x + a * KAPPA, c.y + r,
            c.x + a, c.y + r * KAPPA,
            c.x + a, c.y,
        )
        cubicTo(
            c.x + a, c.y - r * KAPPA,
            c.x + a * KAPPA, c.y - r,
            c.x, topY,
        )
        close()
    }
}
