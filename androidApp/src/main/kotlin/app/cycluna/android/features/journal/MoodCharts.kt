package app.cycluna.android.features.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.core.CycleStore
import app.cycluna.android.core.MoodScale
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.features.home.MoonDisc
import app.cycluna.android.features.phases.PhaseContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos

/**
 * The three chart bodies behind "Mood patterns". Each takes one prepared page and draws it —
 * no aggregation here, that all lives in the shared core.
 *
 * Shared conventions: y is mood 1..5 (bottom to top), a dot per real log, and a connecting
 * line only once there are enough points for the shape to mean something.
 */
private object Plot {
    /**
     * Wide enough that a dot on the first or last position isn't clipped by the Canvas edge.
     * At 6dp a day-1 log was drawn half outside the chart and read as missing.
     */
    val PAD_X = 12.dp
    val PAD_TOP = 12.dp
    val PAD_BOTTOM = 10.dp

    /** Below this, joining dots implies a trend the data doesn't show. */
    const val MIN_POINTS_FOR_LINE = 4

    fun DrawScope.yOf(mood: Double): Float {
        val h = size.height - PAD_TOP.toPx() - PAD_BOTTOM.toPx()
        return PAD_TOP.toPx() + ((5 - mood) / 4).toFloat() * h
    }

    /**
     * One mood dot, ringed in the card colour so overlapping dots stay countable —
     * consecutive days sit only ~11dp apart on the moon lens, closer than a dot is wide.
     */
    fun DrawScope.moodDot(at: Offset, color: Color) {
        drawCircle(Theme.surface, 6.5.dp.toPx(), at)
        drawCircle(color, 5.dp.toPx(), at)
    }

    fun DrawScope.connect(points: List<Offset>, color: Color) {
        if (points.size < MIN_POINTS_FOR_LINE) return
        val path = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(
            path,
            color,
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private val DAY_OF_MONTH = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun parse(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

// ------------------------------------------------------------------------------------
// Daily
// ------------------------------------------------------------------------------------

@Composable
fun DailyMoodChart(page: CycleStore.MoodPage, modifier: Modifier = Modifier) {
    val start = parse(page.startIso)
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width - Plot.PAD_X.toPx() * 2
        val span = (page.spanDays - 1).coerceAtLeast(1)
        fun xOf(dayOffset: Int) = Plot.PAD_X.toPx() + (dayOffset.toFloat() / span) * w

        // Faint gridlines at each mood level, so height is readable without an axis.
        with(Plot) {
            for (level in 1..5) {
                val ly = yOf(level.toDouble())
                drawLine(
                    Theme.inkSoft.copy(alpha = if (level == 3) 0.14f else 0.07f),
                    Offset(Plot.PAD_X.toPx(), ly),
                    Offset(size.width - Plot.PAD_X.toPx(), ly),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val points = page.daily.mapNotNull { d ->
                val date = parse(d.dateIso) ?: return@mapNotNull null
                val offset = start?.let { ChronoUnit.DAYS.between(it, date).toInt() } ?: return@mapNotNull null
                Offset(xOf(offset), yOf(d.mood.toDouble())) to d.mood
            }
            connect(points.map { it.first }, Theme.primary.copy(alpha = 0.55f))
            points.forEach { (p, mood) -> moodDot(p, MoodScale.color(mood)) }
        }
    }
}

@Composable
fun DailyMoodAxis(page: CycleStore.MoodPage, modifier: Modifier = Modifier) {
    val start = parse(page.startIso)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Plot.PAD_X)
            .semantics { contentDescription = "${page.title}, ${page.summary.count} logs" },
    ) {
        (0 until page.spanDays).forEach { offset ->
            Text(
                // Only every other day, or fourteen labels collide.
                if (offset % 2 == 0) start?.plusDays(offset.toLong())?.format(DAY_OF_MONTH) ?: "" else " ",
                Modifier.weight(1f),
                fontSize = 8.sp,
                color = Theme.inkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------------------------
// Phase
// ------------------------------------------------------------------------------------

@Composable
fun PhaseMoodChart(page: CycleStore.MoodPage, periodLength: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width - Plot.PAD_X.toPx() * 2
        val h = size.height - Plot.PAD_TOP.toPx() - Plot.PAD_BOTTOM.toPx()
        val length = page.spanDays.coerceAtLeast(1)
        fun xOf(day: Int) =
            Plot.PAD_X.toPx() + ((day - 1).toFloat() / (length - 1).coerceAtLeast(1)) * w

        // Phase bands sized to THIS cycle's real length, not the predicted one.
        PhaseContent.entries.forEach { phase ->
            val r = phase.dayRange(length, periodLength)
            val x0 = xOf(r.first)
            val x1 = xOf(minOf(r.last + 1, length))
            if (x1 > x0) {
                drawRect(
                    phase.color.copy(alpha = 0.12f),
                    Offset(x0, Plot.PAD_TOP.toPx()),
                    Size(x1 - x0, h),
                )
            }
        }

        with(Plot) {
            val points = page.cycle.map { Offset(xOf(it.cycleDay), yOf(it.mood.toDouble())) to it.mood }
            connect(points.map { it.first }, Theme.primary.copy(alpha = 0.6f))
            points.forEach { (p, mood) -> moodDot(p, MoodScale.color(mood)) }
        }
    }
}

/** Roughly this many date ticks across the cycle — enough to locate yourself, few enough
 *  that they don't collide on a narrow screen. */
private const val DATE_TICKS = 5

@Composable
fun PhaseMoodAxis(page: CycleStore.MoodPage, periodLength: Int, modifier: Modifier = Modifier) {
    val span = page.spanDays.coerceAtLeast(1)
    val start = parse(page.startIso)
    val step = (span / (DATE_TICKS - 1)).coerceAtLeast(1)
    val tickDays = (1..span step step).toList()

    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${page.title}, ${page.spanDays} days, ${page.summary.count} logs"
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Both rows size to their own text. Pinning them to 13dp and 12dp clipped the
        // descenders off "Menstrual" and "28 Jun" — and would have sliced the labels in half
        // for anyone running a larger font scale, where the text grows but a fixed dp box
        // does not. `lineHeight` keeps the two rows tight without cropping them.
        Row(Modifier.fillMaxWidth()) {
            PhaseContent.entries.forEach { phase ->
                val r = phase.dayRange(span, periodLength)
                val days = (r.last - r.first + 1).coerceAtLeast(0)
                if (days > 0) {
                    Text(
                        phase.key,
                        Modifier.weight(days.toFloat()),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = Theme.inkSoft,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Real calendar dates as well as cycle phases — "day 14" is hard to place in a month
        // without them.
        Row(Modifier.fillMaxWidth()) {
            tickDays.forEachIndexed { i, day ->
                Text(
                    start?.plusDays((day - 1).toLong())?.format(DAY_MONTH) ?: "",
                    Modifier.weight(1f),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    color = Theme.inkSoft.copy(alpha = 0.8f),
                    maxLines = 1,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        tickDays.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// Moon
// ------------------------------------------------------------------------------------

private const val MOON_BANDS = 8

@Composable
fun MoonMoodChart(page: CycleStore.MoodPage, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width - Plot.PAD_X.toPx() * 2
        val h = size.height - Plot.PAD_TOP.toPx() - Plot.PAD_BOTTOM.toPx()
        val bandW = w / MOON_BANDS

        // Lit like the month itself: darkest at the new moon, brightest at full.
        for (i in 0 until MOON_BANDS) {
            val centre = (i + 0.5) / MOON_BANDS
            val illum = (1 - cos(2 * Math.PI * centre)) / 2
            drawRoundRect(
                color = Theme.accent.copy(alpha = (0.06 + 0.16 * illum).toFloat()),
                topLeft = Offset(Plot.PAD_X.toPx() + i * bandW, Plot.PAD_TOP.toPx()),
                size = Size(bandW - 1f, h),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }

        with(Plot) {
            // Within one page every dot belongs to the same lunation, so lunar order is date
            // order — the line means exactly what it means in the daily view.
            val points = page.moon.map {
                Offset(Plot.PAD_X.toPx() + it.phaseFraction.toFloat() * w, yOf(it.mood.toDouble())) to it.mood
            }
            connect(points.map { it.first }, Theme.primary.copy(alpha = 0.55f))
            points.forEach { (p, mood) -> moodDot(p, MoodScale.color(mood)) }
        }
    }
}

@Composable
fun MoonMoodAxis(page: CycleStore.MoodPage, modifier: Modifier = Modifier) {
    val store = LocalCycleStore.current
    Row(modifier.fillMaxWidth().padding(horizontal = Plot.PAD_X)) {
        page.moonAverages.forEach { band ->
            val count = band.count
            val spoken = if (count == 0) {
                "${MoonNames.full(band.bucketKey)}, no logs"
            } else {
                val level = MoodScale.label(band.average.toInt().coerceIn(1, 5))
                "${MoonNames.full(band.bucketKey)}, average mood $level, " +
                    "$count log${if (count == 1) "" else "s"}"
            }
            Column(
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = spoken },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MoonDisc(
                    illumination = store.moonBucketIllumination(band.bucketKey),
                    waxing = store.moonBucketIsWaxing(band.bucketKey),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    MoonNames.short(band.bucketKey),
                    fontSize = 7.5.sp,
                    lineHeight = 9.sp,
                    color = Theme.inkSoft,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Bucket slugs → display names. Copy lives in the native layer, never in the shared core. */
object MoonNames {
    fun full(key: String): String = when (key) {
        "new" -> "New moon"
        "waxing-crescent" -> "Waxing crescent"
        "first-quarter" -> "First quarter"
        "waxing-gibbous" -> "Waxing gibbous"
        "full" -> "Full moon"
        "waning-gibbous" -> "Waning gibbous"
        "last-quarter" -> "Last quarter"
        else -> "Waning crescent"
    }

    fun short(key: String): String = when (key) {
        "new" -> "New"
        "waxing-crescent" -> "Wax\ncres"
        "first-quarter" -> "First\nqtr"
        "waxing-gibbous" -> "Wax\ngib"
        "full" -> "Full"
        "waning-gibbous" -> "Wan\ngib"
        "last-quarter" -> "Last\nqtr"
        else -> "Wan\ncres"
    }
}
