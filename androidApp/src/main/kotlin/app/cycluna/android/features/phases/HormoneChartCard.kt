package app.cycluna.android.features.phases

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import app.cycluna.core.CyclunaCore
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val AXIS_DATE = DateTimeFormatter.ofPattern("M/d", Locale.ENGLISH)

private data class DayHormones(
    val day: Int,
    val estrogen: Double,
    val progesterone: Double,
    val lh: Double,
    val fsh: Double,
)

private data class Band(val startX: Double, val endX: Double, val color: Color)

/**
 * Educational hormone chart — typical reference curves (Speroff, Stricker et al.) for a
 * 28-day cycle, scaled to the user's length. Tap any day to see relative levels.
 *
 * Hand-drawn on a Canvas rather than pulled from a charting library: four smoothed lines,
 * four background bands and a marker is the whole requirement, and no Compose chart library
 * is worth the dependency for it.
 */
@Composable
fun HormoneChartCard() {
    val store = LocalCycleStore.current
    val cycleLength = store.cycleLength
    val currentDay = store.cycleDay.coerceIn(1, cycleLength)
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()

    val data = remember(cycleLength) {
        (1..cycleLength).map { d ->
            val h = CyclunaCore.hormoneLevels(d, cycleLength)
            DayHormones(d, h.estrogen, h.progesterone, h.lh, h.fsh)
        }
    }

    val bands = remember(cycleLength, store.periodLength) {
        val pl = store.periodLength
        val folEnd = cycleLength / 2 - 2
        val ovEnd = cycleLength / 2 + 2
        fun band(s: Int, e: Int, c: Color) =
            Band(s - 0.5, minOf(e, cycleLength) + 0.5, c)
        listOf(
            band(1, pl, Theme.phaseMenstrual),
            band(pl + 1, folEnd, Theme.phaseFollicular),
            band(folEnd + 1, ovEnd, Theme.phaseOvulatory),
            band(ovEnd + 1, cycleLength, Theme.phaseLuteal),
        )
    }

    // ~5 evenly spaced ticks across the cycle.
    val ticks = remember(cycleLength) {
        listOf(1, cycleLength / 4, cycleLength / 2, cycleLength * 3 / 4, cycleLength)
            .map { it.coerceIn(1, cycleLength) }
            .distinct()
            .sorted()
    }

    Column(
        Modifier.cyclunaCard(padding = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Hormone chart", style = serif(22).copy(color = Theme.ink))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(216.dp)
                .padding(top = 8.dp)
                .pointerInput(cycleLength) {
                    fun dayAt(x: Float): Int {
                        val frac = (x / size.width).coerceIn(0f, 1f)
                        return (1 + frac * (cycleLength - 1)).roundToInt().coerceIn(1, cycleLength)
                    }
                    detectTapGestures { selectedDay = dayAt(it.x) }
                }
                .pointerInput(cycleLength) {
                    fun dayAt(x: Float): Int {
                        val frac = (x / size.width).coerceIn(0f, 1f)
                        return (1 + frac * (cycleLength - 1)).roundToInt().coerceIn(1, cycleLength)
                    }
                    detectDragGestures(
                        onDragStart = { selectedDay = dayAt(it.x) },
                        onDrag = { change, _ -> selectedDay = dayAt(change.position.x) },
                    )
                },
        ) {
            val labelHeight = 18.dp.toPx()
            val topPad = 14.dp.toPx()   // room for the "Today" pill
            val plotHeight = size.height - labelHeight - topPad
            fun xOf(day: Double) =
                ((day - 1) / (cycleLength - 1).coerceAtLeast(1)).toFloat() * size.width
            fun yOf(v: Double) = topPad + (1f - v.toFloat()) * plotHeight

            bands.forEach { b ->
                val x0 = xOf(b.startX).coerceAtLeast(0f)
                val x1 = xOf(b.endX).coerceAtMost(size.width)
                if (x1 > x0) {
                    drawRect(
                        color = b.color.copy(alpha = 0.13f),
                        topLeft = Offset(x0, topPad),
                        size = androidx.compose.ui.geometry.Size(x1 - x0, plotHeight),
                    )
                }
            }

            drawSeries(data.map { xOf(it.day.toDouble()) to yOf(it.estrogen) }, Theme.primary, null)
            drawSeries(data.map { xOf(it.day.toDouble()) to yOf(it.progesterone) }, Theme.secondary, null)
            drawSeries(
                data.map { xOf(it.day.toDouble()) to yOf(it.lh) },
                Theme.accent,
                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
            drawSeries(
                data.map { xOf(it.day.toDouble()) to yOf(it.fsh) },
                Theme.inkSoft,
                PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
            )

            // Today's rule, with its label sitting above the plot area.
            val todayX = xOf(currentDay.toDouble())
            drawLine(
                Theme.phaseMenstrual,
                Offset(todayX, topPad),
                Offset(todayX, topPad + plotHeight),
                strokeWidth = 2.dp.toPx(),
            )
            val todayLabel = measurer.measure(
                "Today",
                TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
            )
            val pillW = todayLabel.size.width + 10.dp.toPx()
            val pillX = (todayX - pillW / 2).coerceIn(0f, size.width - pillW)
            drawRoundRect(
                color = Theme.phaseMenstrual,
                topLeft = Offset(pillX, 0f),
                size = androidx.compose.ui.geometry.Size(pillW, todayLabel.size.height + 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
            )
            drawText(todayLabel, topLeft = Offset(pillX + 5.dp.toPx(), 2.dp.toPx()))

            selectedDay?.let { d ->
                val row = data[d - 1]
                val x = xOf(d.toDouble())
                drawLine(
                    Theme.inkSoft.copy(alpha = 0.5f),
                    Offset(x, topPad),
                    Offset(x, topPad + plotHeight),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                )
                listOf(
                    row.estrogen to Theme.primary,
                    row.progesterone to Theme.secondary,
                    row.lh to Theme.accent,
                    row.fsh to Theme.inkSoft,
                ).forEach { (v, c) -> drawCircle(c, 4.dp.toPx(), Offset(x, yOf(v))) }
            }

            // X axis: real calendar dates, so the cycle maps onto the user's month.
            ticks.forEach { day ->
                val label = measurer.measure(
                    store.currentCycleStart.plusDays((day - 1).toLong()).format(AXIS_DATE),
                    TextStyle(fontSize = 10.sp, color = Theme.inkSoft),
                )
                val x = (xOf(day.toDouble()) - label.size.width / 2f)
                    .coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(x, topPad + plotHeight + 4.dp.toPx()))
            }
        }

        SelectionPanel(selectedDay?.let { data[it - 1] })
        Legend()
        Text(
            "Typical 28-day reference curves (Speroff, Stricker et al.), relative not absolute. " +
                "Educational only.",
            Modifier.fillMaxWidth(),
            fontSize = 10.sp,
            color = Theme.inkSoft.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

/** A smoothed polyline through the points, Catmull-Rom converted to cubic Béziers. */
private fun DrawScope.drawSeries(
    points: List<Pair<Float, Float>>,
    color: Color,
    dash: PathEffect?,
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].first, points[0].second)
        for (i in 0 until points.size - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
            cubicTo(
                p1.first + (p2.first - p0.first) / 6f, p1.second + (p2.second - p0.second) / 6f,
                p2.first - (p3.first - p1.first) / 6f, p2.second - (p3.second - p1.second) / 6f,
                p2.first, p2.second,
            )
        }
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))
}

@Composable
private fun SelectionPanel(row: DayHormones?) {
    if (row == null) {
        Text(
            "Tap any day to see hormone levels",
            Modifier.fillMaxWidth(),
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            color = Theme.inkSoft,
            textAlign = TextAlign.Center,
        )
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.background, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Day ${row.day}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Theme.ink)
        LevelRow("Oestrogen", Theme.primary, row.estrogen)
        LevelRow("Progesterone", Theme.secondary, row.progesterone)
        LevelRow("LH", Theme.accent, row.lh)
        LevelRow("FSH", Theme.inkSoft, row.fsh)
    }
}

@Composable
private fun LevelRow(label: String, color: Color, v: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, Modifier.padding(start = 8.dp).weight(1f), fontSize = 13.sp, color = Theme.ink)
        Text("${(v * 100).roundToInt()}%", fontSize = 13.sp, color = Theme.inkSoft)
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendLine("Oestrogen", Theme.primary, dashed = false)
        LegendLine("Progesterone", Theme.secondary, dashed = false)
        LegendLine("LH", Theme.accent, dashed = true)
        LegendLine("FSH", Theme.inkSoft, dashed = true)
    }
}

@Composable
private fun LegendLine(label: String, color: Color, dashed: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 2.dp)) {
            drawLine(
                color = color.copy(alpha = if (dashed) 0.7f else 1f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null,
            )
        }
        Text(label, fontSize = 11.sp, color = Theme.inkSoft)
    }
}
