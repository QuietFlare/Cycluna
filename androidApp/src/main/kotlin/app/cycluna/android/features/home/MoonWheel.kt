package app.cycluna.android.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.serif
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private data class Segment(val from: Double, val to: Double, val name: String, val color: Color)

/**
 * Circular cycle ring — phase arcs with the active one thicker, predicted ovulation and
 * period markers, the current-day dot, and a serif "Day N" centre.
 */
@Composable
fun MoonWheel(
    cycleDay: Int,
    cycleLength: Int,
    periodLength: Int,
    phaseLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
) {
    // Same boundaries as the core's phase model (`Cycle.status`) — the arc under the
    // current-day dot must be the phase the label names, whatever the period length.
    // Menstrual wins overlaps there too, so later phases start no earlier than it ends;
    // a squeezed-out phase becomes a zero-width segment, which the drawing loop skips.
    val half = (cycleLength / 2).toDouble()
    val len = cycleLength.toDouble()
    val period = periodLength.toDouble()
    val folEnd = max(period, half - 2)
    val ovEnd = max(folEnd, half + 2)
    val segments = listOf(
        Segment(0.0, period, "Menstrual", Theme.phaseMenstrual),
        Segment(period, folEnd, "Follicular", Theme.phaseFollicular),
        Segment(folEnd, ovEnd, "Ovulatory", Theme.phaseOvulatory),
        Segment(ovEnd, len, "Luteal", Theme.phaseLuteal),
    )
    val activeColor = segments.firstOrNull { it.name == phaseLabel }?.color ?: Theme.primary

    Box(
        modifier
            .size(size)
            // A Canvas is invisible to TalkBack — expose one meaningful element instead.
            .semantics {
                contentDescription = "Cycle day $cycleDay of $cycleLength, $phaseLabel phase"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.width / 2f - 20.dp.toPx()
            val c = Offset(this.size.width / 2f, this.size.height / 2f)

            // Centre glow in the active phase colour.
            val glowR = r - 30.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.28f),
                        activeColor.copy(alpha = 0.06f),
                        activeColor.copy(alpha = 0f),
                    ),
                    center = c,
                    radius = glowR,
                ),
                radius = glowR,
                center = c,
            )

            // Outer track.
            drawCircle(Theme.inkSoft.copy(alpha = 0.22f), r, c, style = Stroke(1.dp.toPx()))

            // Phase arcs. Zero-width segments are skipped — a degenerate arc with a round
            // cap renders as a stray dot.
            segments.filter { it.to > it.from }.forEach { s ->
                val active = s.name == phaseLabel
                drawPath(
                    path = arcPath(s.from, s.to, c, r, cycleLength),
                    color = s.color.copy(alpha = if (active) 1f else 0.35f),
                    style = Stroke(
                        width = (if (active) 8 else 4).dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }

            // Predicted ovulation + period markers.
            drawMarker((cycleLength / 2).toDouble(), c, r, cycleLength, Theme.phaseOvulatory)
            drawMarker(cycleLength.toDouble(), c, r, cycleLength, Theme.phaseMenstrual)

            // Current-day dot, with a soft outer ring.
            val p = pointOnRing(((cycleDay - 1).toDouble() / len) * 360.0 - 90.0, c, r)
            drawCircle(Theme.accent.copy(alpha = 0.5f), 14.dp.toPx(), p, style = Stroke(1.dp.toPx()))
            drawCircle(Theme.background, 10.5.dp.toPx(), p)
            drawCircle(Theme.accent, 9.dp.toPx(), p)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Day $cycleDay", style = serif(48).copy(color = Theme.ink))
            Text(
                phaseLabel.uppercase(),
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                color = Theme.inkSoft,
            )
        }
    }
}

private fun pointOnRing(degrees: Double, center: Offset, radius: Float): Offset {
    val rad = degrees * Math.PI / 180.0
    return Offset(
        center.x + radius * cos(rad).toFloat(),
        center.y + radius * sin(rad).toFloat(),
    )
}

/** The arc as a polyline — dense enough that the curve reads as smooth at any ring size. */
private fun arcPath(from: Double, to: Double, center: Offset, radius: Float, cycleLength: Int): Path {
    val steps = max(2, ((to - from) * 4).toInt())
    return Path().apply {
        for (i in 0..steps) {
            val day = from + (to - from) * i / steps
            val pt = pointOnRing((day / cycleLength) * 360.0 - 90.0, center, radius)
            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
    }
}

private fun DrawScope.drawMarker(day: Double, center: Offset, radius: Float, cycleLength: Int, color: Color) {
    val p = pointOnRing(((day - 1) / cycleLength) * 360.0 - 90.0, center, radius)
    drawCircle(Theme.background, 6.dp.toPx(), p)
    drawCircle(color, 6.dp.toPx(), p, style = Stroke(2.dp.toPx()))
    drawCircle(color, 2.5.dp.toPx(), p)
}
