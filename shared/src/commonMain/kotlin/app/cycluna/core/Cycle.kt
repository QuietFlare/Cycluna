package app.cycluna.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.floor
import kotlin.math.max

/** Menstrual-cycle phases. UI copy/colours live in the native layer, not here. */
enum class Phase(val label: String, val emoji: String) {
    MENSTRUAL("Menstrual", "🔴"),
    FOLLICULAR("Follicular", "🌱"),
    OVULATORY("Ovulatory", "✨"),
    LUTEAL("Luteal", "🌙"),
}

/** A logged period; [end] is null while it is still open/ongoing. */
data class PeriodEntry(val start: LocalDate, val end: LocalDate? = null)

/** Lean cycle inputs — the emerging domain model, replacing the old VaultData. */
data class CycleInput(
    val periods: List<PeriodEntry> = emptyList(),
    val averageCycleLength: Int? = null,
    val averagePeriodLength: Int? = null,
)

/**
 * How much Cycluna can currently trust its own prediction.
 *
 * [NORMAL]  the predicted period hasn't come due yet.
 * [LATE]    it came due and nothing was logged — say so plainly, keep the cycle open.
 * [UNCLEAR] it's been overdue so long that continuing to count would be noise (and, for a
 *           cycle tracker, needlessly alarming). Stop predicting and ask for a fresh log.
 */
enum class CycleTracking { NORMAL, LATE, UNCLEAR }

data class CycleStatus(
    val cycleDay: Int,
    val phase: Phase,
    val daysUntilNextPeriod: Int,
    val cycleLength: Int,
    val periodLength: Int,
    val lastPeriodStart: LocalDate?,
    /** Days past the predicted start with nothing logged; 0 when not overdue. */
    val daysLate: Int = 0,
    val tracking: CycleTracking = CycleTracking.NORMAL,
)

/**
 * Cycle prediction ported from `cycle.ts`. All functions are pure and take an
 * explicit [today] so they are deterministic and testable.
 */
object Cycle {
    const val DEFAULT_CYCLE_LENGTH = 28
    const val DEFAULT_PERIOD_LENGTH = 5
    const val MIN_CYCLE_LENGTH = 21
    const val MAX_CYCLE_LENGTH = 45

    /**
     * How many days a period may be overdue before Cycluna stops counting and admits it has
     * lost the thread. Two weeks: long enough to cover ordinary variation (stress, illness,
     * travel routinely shift a cycle by a week), short enough that we aren't still displaying
     * "47 days late" months later.
     */
    const val UNCLEAR_AFTER_LATE_DAYS = 14

    data class Averages(val avgCycle: Int, val avgPeriod: Int)

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // JS Math.round (half-up) — averages are non-negative, so floor(x+0.5) matches.
    private fun jsRound(x: Double): Int = floor(x + 0.5).toInt()

    private fun normalizeCycleLength(raw: Int?, fallback: Int = DEFAULT_CYCLE_LENGTH): Int {
        val value = raw ?: fallback
        return if (value in MIN_CYCLE_LENGTH..MAX_CYCLE_LENGTH) value else DEFAULT_CYCLE_LENGTH
    }

    /**
     * How many recent gaps between period starts must agree before logged history replaces
     * the user's own cycle-length setting.
     *
     * The web app (`cycle.ts`) relearns from a SINGLE gap. That is too eager, and actively
     * harmful once lateness is modelled: a period that arrives four days late produces one
     * long gap, which becomes the new baseline, against which the next late period looks
     * normal. Lateness would quietly ratchet into the prediction and stop being reported.
     * Two gaps is the same "don't conclude from one data point" bar the mood and moon
     * insights hold themselves to.
     */
    const val MIN_GAPS_TO_LEARN = 2

    private fun averageRecentCycleLength(periods: List<PeriodEntry>, today: LocalDate): Int? {
        val windowStart = today.minus(DatePeriod(months = 3))
        val starts = periods.map { it.start }
            .filter { it >= windowStart && it <= today }
            .sorted()
        if (starts.size < MIN_GAPS_TO_LEARN + 1) return null
        val cycles = mutableListOf<Int>()
        for (i in 1 until starts.size) {
            val gap = starts[i - 1].daysUntil(starts[i])
            if (gap in MIN_CYCLE_LENGTH..MAX_CYCLE_LENGTH) cycles.add(gap)
        }
        return if (cycles.size >= MIN_GAPS_TO_LEARN) jsRound(cycles.average()) else null
    }

    fun predictedCycleLength(input: CycleInput, today: LocalDate = today()): Int =
        averageRecentCycleLength(input.periods, today) ?: normalizeCycleLength(input.averageCycleLength)

    /**
     * The most recent projected period start on or before [today], rolling [from] forward
     * by whole [cycleLength] cycles. Used at onboarding: if the user picks a date weeks or
     * months ago, we assume regular cycles of the chosen length and land them in their
     * CURRENT cycle, so the "today" status isn't perpetually "late". Never rolls past today.
     */
    fun mostRecentStart(from: LocalDate, cycleLength: Int, today: LocalDate = today()): LocalDate {
        val days = from.daysUntil(today)
        if (days <= 0) return from
        val cl = normalizeCycleLength(cycleLength)
        val cycles = days / cl
        return from.plus(DatePeriod(days = cycles * cl))
    }

    /**
     * The fertile window (start..end, inclusive) of the SINGLE cycle starting at [start] —
     * no rolling. The one home for the day offsets: [fertileWindow], the calendar's
     * markers, and the predicted-cycles list must all agree on these dates.
     */
    fun fertileWindowForCycle(start: LocalDate, cycleLength: Int): Pair<LocalDate, LocalDate> {
        val cl = normalizeCycleLength(cycleLength)
        return start.plus(DatePeriod(days = cl / 2 - 3)) to start.plus(DatePeriod(days = cl / 2 + 1))
    }

    /**
     * The UPCOMING fertile window (start..end, inclusive) around ovulation. Rolls the
     * anchor to the current cycle, then advances one more cycle if that window has already
     * passed — so the "next fertile window" never shows a date in the past.
     */
    fun fertileWindow(anchor: LocalDate, cycleLength: Int, today: LocalDate = today()): Pair<LocalDate, LocalDate> {
        val cl = normalizeCycleLength(cycleLength)
        val base = mostRecentStart(anchor, cl, today)
        var (start, end) = fertileWindowForCycle(base, cl)
        if (end < today) {
            start = start.plus(DatePeriod(days = cl))
            end = end.plus(DatePeriod(days = cl))
        }
        return start to end
    }

    fun status(input: CycleInput, today: LocalDate = today()): CycleStatus {
        val sorted = input.periods.sortedBy { it.start }
        val last = sorted.lastOrNull()
        val cycleLength = predictedCycleLength(input, today)
        val periodLength = input.averagePeriodLength ?: DEFAULT_PERIOD_LENGTH

        if (last == null) {
            return CycleStatus(1, Phase.FOLLICULAR, cycleLength, cycleLength, periodLength, null)
        }

        // The anchor is the period the user actually logged — deliberately NOT rolled forward.
        // Rolling (see [mostRecentStart]) would advance into a fabricated new cycle the moment
        // the predicted date passed, reporting "Day 1 · Menstrual" on a day no period arrived
        // and making lateness impossible to express. Overdue cycles stay open here; the
        // calendar's own projections still roll, so future months keep showing predictions.
        val anchor = last.start
        var cycleDay = anchor.daysUntil(today) + 1
        if (cycleDay < 1) cycleDay = 1

        // Past the predicted length the person is still (predicted) luteal — the `else` branch
        // catches that, so a late cycle never wraps around into MENSTRUAL on its own.
        val phase = when {
            cycleDay <= periodLength -> Phase.MENSTRUAL
            cycleDay <= cycleLength / 2 - 2 -> Phase.FOLLICULAR
            cycleDay <= cycleLength / 2 + 2 -> Phase.OVULATORY
            else -> Phase.LUTEAL
        }

        val daysLate = max(0, cycleDay - 1 - cycleLength)
        val tracking = when {
            daysLate == 0 -> CycleTracking.NORMAL
            daysLate <= UNCLEAR_AFTER_LATE_DAYS -> CycleTracking.LATE
            else -> CycleTracking.UNCLEAR
        }

        val daysUntilNextPeriod = max(0, cycleLength - cycleDay + 1)
        return CycleStatus(
            cycleDay, phase, daysUntilNextPeriod, cycleLength, periodLength, anchor,
            daysLate = daysLate, tracking = tracking
        )
    }

    fun recomputeAverages(periods: List<PeriodEntry>, today: LocalDate = today()): Averages {
        val sorted = periods.sortedBy { it.start }
        val periodLengths = sorted.filter { it.end != null }
            .map { it.start.daysUntil(it.end!!) + 1 }
        val avgPeriod = if (periodLengths.isNotEmpty()) jsRound(periodLengths.average()) else DEFAULT_PERIOD_LENGTH
        val avgCycle = averageRecentCycleLength(sorted, today) ?: DEFAULT_CYCLE_LENGTH
        return Averages(avgCycle, avgPeriod)
    }

    fun isIrregular(periods: List<PeriodEntry>, today: LocalDate = today()): Boolean {
        val sorted = periods.sortedBy { it.start }
        if (sorted.size < 3) return false
        val cutoff = today.minus(DatePeriod(months = 3))
        val last3 = sorted.filter { it.start >= cutoff }.takeLast(3)
        if (last3.size < 3) return false
        val cycles = mutableListOf<Int>()
        for (i in 1 until last3.size) {
            cycles.add(last3[i - 1].start.daysUntil(last3[i].start))
        }
        return cycles.any { it < MIN_CYCLE_LENGTH || it > 35 }
    }

    fun phaseForDate(input: CycleInput, date: LocalDate): Phase? =
        phaseBucket(input)?.phaseOf(date)

    /**
     * The anchor and lengths needed to place any date in a phase, worked out once.
     *
     * [phaseForDate] recomputes [predictedCycleLength] — which filters, sorts and averages the
     * whole period history — on every call. Bucketing a year of logs called it thousands of
     * times and dominated the app's main-thread cost. Callers that classify many dates should
     * build this once and reuse it.
     */
    class PhaseBucket(
        private val anchor: LocalDate,
        val cycleLength: Int,
        private val periodLength: Int,
    ) {
        fun phaseOf(date: LocalDate): Phase {
            val diff = anchor.daysUntil(date)
            val day = ((diff % cycleLength) + cycleLength) % cycleLength + 1
            return when {
                day <= periodLength -> Phase.MENSTRUAL
                day <= cycleLength / 2 - 2 -> Phase.FOLLICULAR
                day <= cycleLength / 2 + 2 -> Phase.OVULATORY
                else -> Phase.LUTEAL
            }
        }
    }

    /** null when nothing is logged — there is no cycle to place dates against. */
    fun phaseBucket(input: CycleInput, today: LocalDate = today()): PhaseBucket? {
        val anchor = input.periods.maxByOrNull { it.start }?.start ?: return null
        return PhaseBucket(anchor, predictedCycleLength(input, today),
                           input.averagePeriodLength ?: DEFAULT_PERIOD_LENGTH)
    }

    fun cycleDayForDate(input: CycleInput, date: LocalDate): Int? {
        val anchor = input.periods.maxByOrNull { it.start }?.start ?: return null
        val cycleLength = predictedCycleLength(input)
        val diff = anchor.daysUntil(date)
        return ((diff % cycleLength) + cycleLength) % cycleLength + 1
    }
}
