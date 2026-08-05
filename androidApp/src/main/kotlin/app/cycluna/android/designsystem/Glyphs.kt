package app.cycluna.android.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The handful of glyphs the app needs that `material-icons-core` does not carry — a drop, a
 * heart, a leaf, a sun. Drawn rather than pulled in as `material-icons-extended`, which is
 * thousands of icons for these four.
 *
 * All are decorative: every one sits beside text that already says what it means, so they
 * clear their semantics rather than announcing a second time to TalkBack.
 */

/**
 * A teardrop, built the way one actually is: a circle for the bowl, and two straight sides
 * running from the apex down to where they meet that circle *tangentially*.
 *
 * Freehand cubics were tried first and read as a garlic bulb — the sides flared wide
 * immediately below the point, so the shape was squat and the tip stubby. Tangency is what
 * makes the silhouette look like a drop: the straight edge and the curve join with no corner.
 *
 * With the bowl at (0.5, 0.68) r=0.30 and the apex at (0.5, 0.02), the tangent points work
 * out at x = 0.5 ± 0.267, y = 0.544 — i.e. −27° and 207° around the bowl, leaving a 234°
 * sweep across the bottom.
 */
private const val BOWL_CX = 0.5f
private const val BOWL_CY = 0.68f
private const val BOWL_R = 0.30f
private const val TANGENT_START_DEG = -27f
private const val TANGENT_SWEEP_DEG = 234f

private fun DrawScope.dropPath(): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(w * BOWL_CX, h * 0.02f)
        lineTo(w * 0.767f, h * 0.544f)
        arcTo(
            rect = Rect(
                left = w * (BOWL_CX - BOWL_R),
                top = h * (BOWL_CY - BOWL_R),
                right = w * (BOWL_CX + BOWL_R),
                bottom = h * (BOWL_CY + BOWL_R),
            ),
            startAngleDegrees = TANGENT_START_DEG,
            sweepAngleDegrees = TANGENT_SWEEP_DEG,
            forceMoveTo = false,
        )
        close()
    }
}

private fun DrawScope.heartPath(): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(w * 0.5f, h * 0.92f)
        cubicTo(w * 0.1f, h * 0.62f, w * 0.02f, h * 0.36f, w * 0.18f, h * 0.19f)
        cubicTo(w * 0.32f, h * 0.05f, w * 0.5f, h * 0.14f, w * 0.5f, h * 0.30f)
        cubicTo(w * 0.5f, h * 0.14f, w * 0.68f, h * 0.05f, w * 0.82f, h * 0.19f)
        cubicTo(w * 0.98f, h * 0.36f, w * 0.9f, h * 0.62f, w * 0.5f, h * 0.92f)
        close()
    }
}

private fun DrawScope.leafPath(): Path {
    val w = size.width
    val h = size.height
    // A pointed oval leaning right, the classic leaf silhouette.
    return Path().apply {
        moveTo(w * 0.12f, h * 0.88f)
        cubicTo(w * 0.1f, h * 0.4f, w * 0.4f, h * 0.1f, w * 0.9f, h * 0.1f)
        cubicTo(w * 0.9f, h * 0.6f, w * 0.6f, h * 0.9f, w * 0.12f, h * 0.88f)
        close()
    }
}

@Composable
fun DropGlyph(tint: Color, size: Dp = 16.dp, filled: Boolean = true, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val path = dropPath()
        if (filled) drawPath(path, tint) else drawPath(path, tint, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
fun HeartGlyph(tint: Color, size: Dp = 16.dp, filled: Boolean = true, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val path = heartPath()
        if (filled) drawPath(path, tint) else drawPath(path, tint, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
fun LeafGlyph(tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        drawPath(leafPath(), tint)
        // The midrib, which is what stops the silhouette reading as a generic blob.
        drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = Offset(this.size.width * 0.16f, this.size.height * 0.84f),
            end = Offset(this.size.width * 0.82f, this.size.height * 0.18f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
fun SunGlyph(tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val core = this.size.minDimension * 0.26f
        drawCircle(tint, radius = core, center = c)
        val inner = core * 1.45f
        val outer = this.size.minDimension * 0.48f
        repeat(8) { i ->
            val a = (i * 45.0) * Math.PI / 180.0
            drawLine(
                color = tint,
                start = Offset(c.x + (inner * kotlin.math.cos(a)).toFloat(), c.y + (inner * kotlin.math.sin(a)).toFloat()),
                end = Offset(c.x + (outer * kotlin.math.cos(a)).toFloat(), c.y + (outer * kotlin.math.sin(a)).toFloat()),
                strokeWidth = 1.4.dp.toPx(),
            )
        }
    }
}
