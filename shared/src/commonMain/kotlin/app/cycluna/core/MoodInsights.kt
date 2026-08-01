package app.cycluna.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** One logged mood placed on the cycle: [cycleDay] 1..cycleLength, [mood] 1..5. */
data class MoodPoint(val cycleDay: Int, val mood: Int)

/** Average mood for a phase across all logged history. */
data class PhaseMood(val phase: Phase, val average: Double, val count: Int)

/** A confident brightest/lowest finding (the UI words it — copy stays in the native layer). */
data class MoodInsight(val brightest: Phase, val lowest: Phase)

/**
 * Deterministic, on-device mood analysis — plain statistics, no AI, no network. Positions
 * logged moods on the cycle for the plot, and derives a "you tend to feel…" insight ONLY
 * when the data actually supports it (enough logs, enough per phase, a real gap). Otherwise
 * it returns null and the UI shows an honest empty / low-data state.
 */
object MoodInsights {
    // Honesty guardrails.
    private const val MIN_TOTAL = 8       // total logged moods before any claim
    private const val MIN_PER_PHASE = 2   // per named phase
    private const val MIN_GAP = 0.8       // brightest − lowest, on the 1..5 scale

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun inputOf(data: CycleData) = CycleInput(
        periods = data.periodStarts.map { PeriodEntry(LocalDate.parse(it)) },
        averageCycleLength = data.cycleLengthSetting,
        averagePeriodLength = data.periodLength,
    )

    /** Moods logged in the CURRENT cycle, positioned by cycle day (for the plot). */
    fun currentCyclePoints(data: CycleData): List<MoodPoint> = currentCyclePoints(data, today())

    internal fun currentCyclePoints(data: CycleData, today: LocalDate): List<MoodPoint> {
        val last = data.periodStarts.maxOrNull()?.let { LocalDate.parse(it) } ?: return emptyList()
        val cl = Cycle.predictedCycleLength(inputOf(data), today)
        val anchor = Cycle.mostRecentStart(last, cl, today)
        val end = anchor.plus(DatePeriod(days = cl))
        return data.moods.mapNotNull { m ->
            val d = LocalDate.parse(m.date)
            if (d >= anchor && d < end) MoodPoint(anchor.daysUntil(d) + 1, m.mood) else null
        }.sortedBy { it.cycleDay }
    }

    /** Average mood per phase across ALL logged history (only phases that have logs). */
    fun phaseAverages(data: CycleData): List<PhaseMood> = phaseAverages(data, today())

    internal fun phaseAverages(data: CycleData, today: LocalDate): List<PhaseMood> {
        val input = inputOf(data)
        return data.moods.mapNotNull { m ->
            Cycle.phaseForDate(input, LocalDate.parse(m.date))?.let { it to m.mood }
        }.groupBy({ it.first }, { it.second })
            .map { (phase, xs) -> PhaseMood(phase, xs.average(), xs.size) }
    }

    /** A confident insight, or null when the data doesn't support naming a pattern. */
    fun insight(data: CycleData): MoodInsight? = insight(data, today())

    internal fun insight(data: CycleData, today: LocalDate): MoodInsight? {
        val stats = phaseAverages(data, today)
        if (stats.sumOf { it.count } < MIN_TOTAL) return null
        val eligible = stats.filter { it.count >= MIN_PER_PHASE }
        if (eligible.size < 2) return null
        val hi = eligible.maxByOrNull { it.average } ?: return null
        val lo = eligible.minByOrNull { it.average } ?: return null
        if (hi.phase == lo.phase || hi.average - lo.average < MIN_GAP) return null
        return MoodInsight(hi.phase, lo.phase)
    }
}
