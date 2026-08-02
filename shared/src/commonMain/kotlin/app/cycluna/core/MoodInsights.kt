package app.cycluna.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** One logged mood placed on the cycle: [cycleDay] 1..cycleLength, [mood] 1..5. */
data class MoodPoint(val cycleDay: Int, val mood: Int)

/** One logged mood on a calendar day (the daily lens). */
data class DailyMood(val dateIso: String, val mood: Int)

/**
 * A plain description of what a span contains — a count and an average, nothing inferred.
 *
 * This is what a page shows when it can't support a *claim*. Descriptions of the visible data
 * need no guardrails; only conclusions drawn beyond it do.
 */
data class MoodSummary(val count: Int, val average: Double)

/** One cycle as a pageable span. [endIso] is exclusive. */
data class CycleSpan(val startIso: String, val endIso: String, val length: Int)

/** Average mood for a phase within the insight window. */
data class PhaseMood(val phase: Phase, val average: Double, val count: Int)

/**
 * A confident brightest/lowest finding (the UI words it — copy stays in the native layer).
 * [cyclesCovered] and [totalLogs] describe the window the finding is drawn from, so the
 * sentence can name its own timeframe instead of implying "all time".
 */
data class MoodInsight(
    val brightest: Phase,
    val lowest: Phase,
    val cyclesCovered: Int,
    val totalLogs: Int,
)

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

    /**
     * How many completed cycles (plus the current one) the insight looks back over.
     * Bounded on purpose: an all-time average silently goes stale after long use, and it
     * cannot be reconciled with the plot, which only ever shows the current cycle.
     */
    internal const val INSIGHT_CYCLE_WINDOW = 6

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun inputOf(data: CycleData) = CycleInput(
        periods = data.periodStarts.map { PeriodEntry(LocalDate.parse(it)) },
        averageCycleLength = data.cycleLengthSetting,
        averagePeriodLength = data.periodLength,
    )

    /** Logged period starts, parsed and sorted. Unreadable entries are skipped, not fatal. */
    internal fun sortedStarts(data: CycleData): List<LocalDate> =
        data.periodStarts.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()

    /**
     * First day the insight considers: the start of the cycle [INSIGHT_CYCLE_WINDOW] cycles
     * before the current one.
     *
     * Uses real logged starts when the history reaches that far back; otherwise falls back to
     * the predicted cycle length for the missing span, so a user with sparse logs still gets a
     * sensible window rather than none. Returns null only when nothing at all is logged.
     */
    internal fun windowStart(data: CycleData, today: LocalDate): LocalDate? {
        val starts = sortedStarts(data)
        if (starts.isEmpty()) return null
        // starts.last() begins the current cycle; step back INSIGHT_CYCLE_WINDOW starts.
        val index = starts.size - 1 - INSIGHT_CYCLE_WINDOW
        if (index >= 0) return starts[index]
        val cycleLength = Cycle.predictedCycleLength(inputOf(data), today)
        return starts.last().minus(DatePeriod(days = INSIGHT_CYCLE_WINDOW * cycleLength))
    }

    /** How many logged cycles the window actually spans (for wording the sentence). */
    internal fun cyclesCovered(data: CycleData, today: LocalDate): Int {
        val from = windowStart(data, today) ?: return 0
        return sortedStarts(data).count { it >= from }.coerceAtLeast(1)
    }

    /** Moods inside the insight window, paired with the phase of the day they were logged. */
    private fun windowedMoods(data: CycleData, today: LocalDate): List<Pair<Phase, Int>> {
        val from = windowStart(data, today) ?: return emptyList()
        val input = inputOf(data)
        return data.moods.mapNotNull { m ->
            val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
            if (date < from || date > today) return@mapNotNull null
            Cycle.phaseForDate(input, date)?.let { it to m.mood }
        }
    }

    // --- Range-based lookups (paging back through history) ---------------------
    //
    // Every lens pages by a different unit — days, cycles, lunations — so the core exposes
    // ranges rather than one fixed window. Dates cross as ISO strings; [toIso] is inclusive.

    /** Logged moods in a date range, oldest first. */
    fun moodsInRange(data: CycleData, fromIso: String, toIso: String): List<DailyMood> {
        val from = runCatching { LocalDate.parse(fromIso) }.getOrNull() ?: return emptyList()
        val to = runCatching { LocalDate.parse(toIso) }.getOrNull() ?: return emptyList()
        return data.moods.mapNotNull { m ->
            val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
            if (date < from || date > to) null else DailyMood(m.date, m.mood)
        }.sortedBy { it.dateIso }
    }

    /**
     * Every cycle that can be paged to, oldest first; the last entry is the current one.
     *
     * Bounded by real logged starts: before the earliest, the app has no idea where a cycle
     * began and must not invent one. The current cycle's end is projected from the predicted
     * length — it hasn't happened yet.
     */
    fun cycleSpans(data: CycleData): List<CycleSpan> = cycleSpans(data, today())

    internal fun cycleSpans(data: CycleData, today: LocalDate): List<CycleSpan> {
        val starts = sortedStarts(data).filter { it <= today }
        if (starts.isEmpty()) return emptyList()
        val predicted = Cycle.predictedCycleLength(inputOf(data), today)
        return starts.mapIndexed { i, start ->
            val end = if (i < starts.lastIndex) starts[i + 1]
                      else start.plus(DatePeriod(days = predicted))
            CycleSpan(start.toString(), end.toString(), start.daysUntil(end))
        }
    }

    /** Moods within one cycle, positioned by cycle day. [endIso] is exclusive. */
    fun cyclePoints(data: CycleData, startIso: String, endIso: String): List<MoodPoint> {
        val start = runCatching { LocalDate.parse(startIso) }.getOrNull() ?: return emptyList()
        val end = runCatching { LocalDate.parse(endIso) }.getOrNull() ?: return emptyList()
        return data.moods.mapNotNull { m ->
            val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
            if (date >= start && date < end) MoodPoint(start.daysUntil(date) + 1, m.mood) else null
        }.sortedBy { it.cycleDay }
    }

    /** Count and average for a span — descriptive, always available, never a claim. */
    fun summaryForRange(data: CycleData, fromIso: String, toIso: String): MoodSummary {
        val moods = moodsInRange(data, fromIso, toIso).map { it.mood }
        return MoodSummary(moods.size, if (moods.isEmpty()) 0.0 else moods.average())
    }

    /**
     * A confident brightest/lowest finding for one span, or null when that span alone can't
     * support it. The same guardrails as [insight] — a single cycle usually will not clear
     * them, which is exactly the point: the UI falls back to [summaryForRange].
     */
    fun insightForRange(data: CycleData, fromIso: String, toIso: String): MoodInsight? {
        val from = runCatching { LocalDate.parse(fromIso) }.getOrNull() ?: return null
        val to = runCatching { LocalDate.parse(toIso) }.getOrNull() ?: return null
        val input = inputOf(data)
        val stats = data.moods.mapNotNull { m ->
            val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
            if (date < from || date > to) return@mapNotNull null
            Cycle.phaseForDate(input, date)?.let { it to m.mood }
        }.groupBy({ it.first }, { it.second })
            .map { (phase, xs) -> PhaseMood(phase, xs.average(), xs.size) }

        val total = stats.sumOf { it.count }
        if (total < MIN_TOTAL) return null
        val eligible = stats.filter { it.count >= MIN_PER_PHASE }
        if (eligible.size < 2) return null
        val hi = eligible.maxByOrNull { it.average } ?: return null
        val lo = eligible.minByOrNull { it.average } ?: return null
        if (hi.phase == lo.phase || hi.average - lo.average < MIN_GAP) return null
        return MoodInsight(hi.phase, lo.phase, 1, total)
    }

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

    /**
     * Average mood per phase within the insight window (only phases that have logs).
     * Deliberately NOT all-time — see [INSIGHT_CYCLE_WINDOW].
     */
    fun phaseAverages(data: CycleData): List<PhaseMood> = phaseAverages(data, today())

    internal fun phaseAverages(data: CycleData, today: LocalDate): List<PhaseMood> =
        windowedMoods(data, today)
            .groupBy({ it.first }, { it.second })
            .map { (phase, xs) -> PhaseMood(phase, xs.average(), xs.size) }

    /** A confident insight, or null when the data doesn't support naming a pattern. */
    fun insight(data: CycleData): MoodInsight? = insight(data, today())

    internal fun insight(data: CycleData, today: LocalDate): MoodInsight? {
        val stats = phaseAverages(data, today)
        val total = stats.sumOf { it.count }
        if (total < MIN_TOTAL) return null
        val eligible = stats.filter { it.count >= MIN_PER_PHASE }
        if (eligible.size < 2) return null
        val hi = eligible.maxByOrNull { it.average } ?: return null
        val lo = eligible.minByOrNull { it.average } ?: return null
        if (hi.phase == lo.phase || hi.average - lo.average < MIN_GAP) return null
        return MoodInsight(hi.phase, lo.phase, cyclesCovered(data, today), total)
    }
}
