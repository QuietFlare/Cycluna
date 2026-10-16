package app.cycluna.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.max

/** Menstrual-cycle phases. UI copy/colours live in the native layer, not here. */
enum class Phase(val label: String, val emoji: String) {
    MENSTRUAL("Menstrual", "🔴"),
    FOLLICULAR("Follicular", "🌱"),
    OVULATORY("Ovulatory", "✨"),
    LUTEAL("Luteal", "🌙"),
}

/** A logged period; [end] is the last day of bleeding, inclusive, or null while open. */
data class PeriodEntry(val start: LocalDate, val end: LocalDate? = null) {
    /** This period's own length when it was closed, else the usual [fallback]. */
    fun lengthOr(fallback: Int): Int =
        end?.let { (start.daysUntil(it) + 1).coerceAtLeast(1) } ?: fallback
}

/** The logged periods as dates, unreadable entries skipped. Kotlin-side only. */
internal fun CycleData.periodEntries(): List<PeriodEntry> =
    periodStarts.mapNotNull { s ->
        val start = runCatching { LocalDate.parse(s) }.getOrNull() ?: return@mapNotNull null
        val end = periodEnds[s]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        PeriodEntry(start, end?.takeIf { it >= start })
    }.sortedBy { it.start }

/** Lean cycle inputs — the emerging domain model, replacing the old VaultData. */
data class CycleInput(
    val periods: List<PeriodEntry> = emptyList(),
    val averageCycleLength: Int? = null,
    /** The usual period length — what a period without a logged end is assumed to last. */
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
    /** The current period's length: its logged end when closed, else the usual length. */
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

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun normalizeCycleLength(raw: Int?, fallback: Int = DEFAULT_CYCLE_LENGTH): Int {
        val value = raw ?: fallback
        return if (value in MIN_CYCLE_LENGTH..MAX_CYCLE_LENGTH) value else DEFAULT_CYCLE_LENGTH
    }

    /**
     * The cycle length every prediction uses: the user's own setting, clamped to the
     * plausible range. NEVER learned from history — a deliberate divergence from the web
     * app, which relearned the length from logged gaps.
     *
     * Two reasons. Learning absorbed lateness: a period four days late made one long gap,
     * the gap became the new baseline, and the next late period looked normal — so
     * lateness quietly stopped being reported. And learning silently overrode a number the
     * user explicitly chose, surfacing as a confusing "using N days" note. The setting is
     * the contract: history is recorded exactly as logged, and if the rhythm changes, the
     * user changes the number.
     */
    fun predictedCycleLength(input: CycleInput, today: LocalDate = today()): Int =
        normalizeCycleLength(input.averageCycleLength)

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
        val usual = input.averagePeriodLength ?: DEFAULT_PERIOD_LENGTH

        if (last == null) {
            return CycleStatus(1, Phase.FOLLICULAR, cycleLength, cycleLength, usual, null)
        }
        val periodLength = last.lengthOr(usual)

        // The anchor is the period the user actually logged — deliberately NOT rolled forward.
        // Rolling (see [mostRecentStart]) would advance into a fabricated new cycle the moment
        // the predicted date passed, reporting "Day 1 · Menstrual" on a day no period arrived
        // and making lateness impossible to express. Overdue cycles stay open here; the
        // calendar's own projections still roll, so future months keep showing predictions.
        val anchor = last.start
        var cycleDay = anchor.daysUntil(today) + 1
        if (cycleDay < 1) cycleDay = 1

        // Past the predicted length the person is still (predicted) luteal — the `else` branch
        // in [phaseOfDay] catches that, so a late cycle never wraps into MENSTRUAL on its own.
        val phase = phaseOfDay(cycleDay, cycleLength, periodLength)

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

    fun phaseForDate(input: CycleInput, date: LocalDate): Phase? =
        phaseBucket(input)?.phaseOf(date)

    /**
     * The one home for the day → phase boundaries. Only the first boundary depends on the
     * period: bleeding length does not move ovulation, which sits on the cycle length.
     */
    fun phaseOfDay(day: Int, cycleLength: Int, periodLength: Int): Phase = when {
        day <= periodLength -> Phase.MENSTRUAL
        day <= cycleLength / 2 - 2 -> Phase.FOLLICULAR
        day <= cycleLength / 2 + 2 -> Phase.OVULATORY
        else -> Phase.LUTEAL
    }

    /**
     * Everything needed to place any date in a phase, worked out once.
     *
     * [phaseForDate] re-derives it on every call. Bucketing a year of logs called it
     * thousands of times and dominated the app's main-thread cost. Callers that classify
     * many dates should build this once and reuse it.
     *
     * Dates are placed against LOGGED history, the same rules as `CyclunaCore.historyCycleDay`:
     * a past cycle counts from its own start and is never wrapped (day 40 of a long gap is
     * luteal, not a fresh period), while dates from the latest start onward project forward
     * by whole cycles. Each logged period keeps its own length; the usual length covers open
     * periods, projected ones, and anything before the first logged start, which is projected
     * backwards from it.
     */
    class PhaseBucket(
        periods: List<PeriodEntry>,
        val cycleLength: Int,
        private val usualPeriodLength: Int,
    ) {
        private val periods = periods.sortedBy { it.start }
        private val starts = this.periods.map { it.start }

        fun phaseOf(date: LocalDate): Phase {
            val index = starts.indexOfLast { it <= date }
            if (index < 0) {
                val diff = starts.first().daysUntil(date)
                val day = ((diff % cycleLength) + cycleLength) % cycleLength + 1
                return phaseOfDay(day, cycleLength, usualPeriodLength)
            }
            val period = periods[index]
            val daysSince = period.start.daysUntil(date)
            return if (index == periods.lastIndex) {
                val firstCycle = daysSince < cycleLength
                phaseOfDay(daysSince % cycleLength + 1, cycleLength,
                           if (firstCycle) period.lengthOr(usualPeriodLength) else usualPeriodLength)
            } else {
                phaseOfDay(daysSince + 1, cycleLength, period.lengthOr(usualPeriodLength))
            }
        }
    }

    /** null when nothing is logged — there is no cycle to place dates against. */
    fun phaseBucket(input: CycleInput, today: LocalDate = today()): PhaseBucket? {
        if (input.periods.isEmpty()) return null
        return PhaseBucket(input.periods, predictedCycleLength(input, today),
                           input.averagePeriodLength ?: DEFAULT_PERIOD_LENGTH)
    }

    fun cycleDayForDate(input: CycleInput, date: LocalDate): Int? {
        val anchor = input.periods.maxByOrNull { it.start }?.start ?: return null
        val cycleLength = predictedCycleLength(input)
        val diff = anchor.daysUntil(date)
        return ((diff % cycleLength) + cycleLength) % cycleLength + 1
    }
}
