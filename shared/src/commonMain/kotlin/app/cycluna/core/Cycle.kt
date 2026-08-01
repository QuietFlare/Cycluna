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

data class CycleStatus(
    val cycleDay: Int,
    val phase: Phase,
    val daysUntilNextPeriod: Int,
    val cycleLength: Int,
    val periodLength: Int,
    val lastPeriodStart: LocalDate?,
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

    data class Averages(val avgCycle: Int, val avgPeriod: Int)

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // JS Math.round (half-up) — averages are non-negative, so floor(x+0.5) matches.
    private fun jsRound(x: Double): Int = floor(x + 0.5).toInt()

    private fun normalizeCycleLength(raw: Int?, fallback: Int = DEFAULT_CYCLE_LENGTH): Int {
        val value = raw ?: fallback
        return if (value in MIN_CYCLE_LENGTH..MAX_CYCLE_LENGTH) value else DEFAULT_CYCLE_LENGTH
    }

    private fun averageRecentCycleLength(periods: List<PeriodEntry>, today: LocalDate): Int? {
        val windowStart = today.minus(DatePeriod(months = 3))
        val starts = periods.map { it.start }
            .filter { it >= windowStart && it <= today }
            .sorted()
        if (starts.size < 2) return null
        val cycles = mutableListOf<Int>()
        for (i in 1 until starts.size) {
            val gap = starts[i - 1].daysUntil(starts[i])
            if (gap in MIN_CYCLE_LENGTH..MAX_CYCLE_LENGTH) cycles.add(gap)
        }
        return if (cycles.isNotEmpty()) jsRound(cycles.average()) else null
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
     * The UPCOMING fertile window (start..end, inclusive) around ovulation. Rolls the
     * anchor to the current cycle, then advances one more cycle if that window has already
     * passed — so the "next fertile window" never shows a date in the past.
     */
    fun fertileWindow(anchor: LocalDate, cycleLength: Int, today: LocalDate = today()): Pair<LocalDate, LocalDate> {
        val cl = normalizeCycleLength(cycleLength)
        val base = mostRecentStart(anchor, cl, today)
        var start = base.plus(DatePeriod(days = cl / 2 - 3))
        var end = base.plus(DatePeriod(days = cl / 2 + 1))
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

        // Roll the most recent logged start forward to the CURRENT cycle (handles an old
        // onboarding date); the raw logged history is left untouched for the calendar.
        val effectiveStart = mostRecentStart(last.start, cycleLength, today)
        var cycleDay = effectiveStart.daysUntil(today) + 1
        if (cycleDay < 1) cycleDay = 1

        val phase = when {
            cycleDay <= periodLength -> Phase.MENSTRUAL
            cycleDay <= cycleLength / 2 - 2 -> Phase.FOLLICULAR
            cycleDay <= cycleLength / 2 + 2 -> Phase.OVULATORY
            else -> Phase.LUTEAL
        }

        val daysUntilNextPeriod = max(0, cycleLength - cycleDay + 1)
        return CycleStatus(cycleDay, phase, daysUntilNextPeriod, cycleLength, periodLength, effectiveStart)
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

    fun phaseForDate(input: CycleInput, date: LocalDate): Phase? {
        val anchor = input.periods.maxByOrNull { it.start }?.start ?: return null
        val cycleLength = predictedCycleLength(input)
        val periodLength = input.averagePeriodLength ?: DEFAULT_PERIOD_LENGTH
        val diff = anchor.daysUntil(date)
        val day = ((diff % cycleLength) + cycleLength) % cycleLength + 1
        return when {
            day <= periodLength -> Phase.MENSTRUAL
            day <= cycleLength / 2 - 2 -> Phase.FOLLICULAR
            day <= cycleLength / 2 + 2 -> Phase.OVULATORY
            else -> Phase.LUTEAL
        }
    }

    fun cycleDayForDate(input: CycleInput, date: LocalDate): Int? {
        val anchor = input.periods.maxByOrNull { it.start }?.start ?: return null
        val cycleLength = predictedCycleLength(input)
        val diff = anchor.daysUntil(date)
        return ((diff % cycleLength) + cycleLength) % cycleLength + 1
    }
}
