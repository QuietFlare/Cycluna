package app.cycluna.android.features.phases

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cycluna.android.designsystem.Crescent
import app.cycluna.android.designsystem.DropGlyph
import app.cycluna.android.designsystem.LeafGlyph
import app.cycluna.android.designsystem.SunGlyph
import app.cycluna.android.designsystem.Theme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Single source of truth for phase display copy and colours. UI copy lives in the native
 * layer — the KMP core stays copy-free — and BOTH Home and Phases read from here so the
 * wording can never drift between screens.
 */
enum class PhaseContent(
    val key: String,        // matches the core's phase label: "Menstrual", "Follicular", …
    val emoji: String,
    val eyebrow: String,
    val blurb: String,
    val color: Color,
) {
    MENSTRUAL(
        "Menstrual", "🔴", "Rest & restore",
        "Hormones at their lowest — rest, restore, reflect.", Theme.phaseMenstrual,
    ),
    FOLLICULAR(
        "Follicular", "🌱", "Rising energy",
        "Oestrogen rising — you're entering your power week.", Theme.phaseFollicular,
    ),
    OVULATORY(
        "Ovulatory", "✨", "Peak & magnetic",
        "Peak oestrogen, LH surging — you're magnetic right now.", Theme.phaseOvulatory,
    ),
    LUTEAL(
        "Luteal", "🌙", "Slow & tend",
        "Progesterone leading — slow down and tend to yourself.", Theme.phaseLuteal,
    );

    /** 1-based day range for this phase, given the user's own cycle + period length. */
    fun dayRange(cycleLength: Int, periodLength: Int): IntRange {
        val folEnd = maxOf(periodLength + 1, cycleLength / 2 - 2)
        val ovEnd = cycleLength / 2 + 2
        return when (this) {
            MENSTRUAL -> 1..maxOf(1, periodLength)
            FOLLICULAR -> (periodLength + 1)..folEnd
            OVULATORY -> (folEnd + 1)..ovEnd
            LUTEAL -> (ovEnd + 1)..cycleLength
        }
    }

    fun rangeText(cycleLength: Int, periodLength: Int): String {
        val r = dayRange(cycleLength, periodLength)
        return if (r.first == r.last) "D${r.first}" else "D${r.first}–${r.last}"
    }

    /**
     * The phase's real calendar dates for a cycle (compact: "Aug 25–28", or
     * "Aug 30 – Sep 3" across a month boundary).
     */
    fun dateRangeText(cycleLength: Int, periodLength: Int, cycleStart: LocalDate): String {
        val r = dayRange(cycleLength, periodLength)
        val start = cycleStart.plusDays((r.first - 1).toLong())
        val end = cycleStart.plusDays((r.last - 1).toLong())
        if (r.first == r.last) return start.format(MONTH_DAY)
        return if (start.month == end.month && start.year == end.year) {
            "${start.format(MONTH_DAY)}–${end.dayOfMonth}"
        } else {
            "${start.format(MONTH_DAY)} – ${end.format(MONTH_DAY)}"
        }
    }

    companion object {
        fun of(phase: String): PhaseContent? = entries.firstOrNull { it.key == phase }
        fun blurb(phase: String): String = of(phase)?.blurb ?: ""

        private val MONTH_DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    }
}

/**
 * The phase's own mark, mirroring the emoji. Used where an emoji would be too loud — the
 * hand-written phase copy read as generated blurb when it sat behind a glitter glyph.
 */
@Composable
fun PhaseIcon(phase: PhaseContent?, size: Dp = 18.dp) {
    val flat = { c: Color -> Brush.linearGradient(listOf(c, c)) }
    when (phase) {
        PhaseContent.MENSTRUAL -> DropGlyph(Theme.phaseMenstrual, size)
        PhaseContent.FOLLICULAR -> LeafGlyph(Theme.phaseFollicular, size)
        PhaseContent.OVULATORY -> SunGlyph(Theme.phaseOvulatory, size)
        PhaseContent.LUTEAL -> Crescent(Modifier.size(size), flat(Theme.phaseLuteal))
        null -> Crescent(Modifier.size(size), flat(Theme.primary))
    }
}
