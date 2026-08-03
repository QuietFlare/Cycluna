package app.cycluna.core

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** How many headache episodes fell in a phase. */
data class PhaseCount(val phase: Phase, val count: Int)

/** A confident "headaches cluster in [phase]" finding (the UI words it). */
data class HeadacheInsight(val phase: Phase, val count: Int, val total: Int)

/**
 * On-device analysis of when headaches happen across the cycle — the hormonal-migraine
 * signal. Plain counting by phase, no AI/network. Names a clustering phase ONLY when there
 * are enough episodes and one phase clearly dominates; otherwise returns null.
 */
object HeadacheInsights {
    private const val MIN_TOTAL = 5
    private const val MIN_TOP = 2
    private const val MIN_SHARE = 0.4   // top phase must hold ≥40% (uniform would be 25%)

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun inputOf(data: CycleData) = CycleInput(
        periods = data.periodStarts.map { PeriodEntry(LocalDate.parse(it)) },
        averageCycleLength = data.cycleLengthSetting,
        averagePeriodLength = data.periodLength,
    )

    fun byPhase(data: CycleData): List<PhaseCount> = byPhase(data, today())

    internal fun byPhase(data: CycleData, today: LocalDate): List<PhaseCount> {
        val input = inputOf(data)
        return data.headaches
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .mapNotNull { Cycle.phaseForDate(input, it) }
            .groupingBy { it }.eachCount()
            .map { (phase, count) -> PhaseCount(phase, count) }
    }

    fun insight(data: CycleData): HeadacheInsight? = insight(data, today())

    internal fun insight(data: CycleData, today: LocalDate): HeadacheInsight? {
        val counts = byPhase(data, today)
        val total = counts.sumOf { it.count }
        if (total < MIN_TOTAL) return null
        val top = counts.maxByOrNull { it.count } ?: return null
        if (top.count < MIN_TOP || top.count.toDouble() / total < MIN_SHARE) return null
        return HeadacheInsight(top.phase, top.count, total)
    }
}
