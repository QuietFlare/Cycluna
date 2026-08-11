package app.cycluna.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.exp

/**
 * Relative hormone levels (0..1) for one cycle day. Educational reference curves
 * for a typical 28-day cycle (Speroff / Stricker), scaled to the user's length.
 * Values are relative for visual comparison — NOT absolute IU/L, not diagnostic.
 */
data class HormoneLevels(
    val estrogen: Double,
    val progesterone: Double,
    val lh: Double,
    val fsh: Double,
)

/**
 * Swift-friendly entry points. Returns plain String/Int/Double so SwiftUI can
 * call the shared core without touching kotlinx-datetime types directly. As the
 * app grows we'll expose richer models; this is the first end-to-end bridge.
 */
object CyclunaCore {
    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // --- Moon ---
    fun todayMoonSymbol(): String = Moon.phase(today()).symbol
    fun todayMoonLabel(): String = Moon.phase(today()).label
    fun todayMoonIlluminationPercent(): Int = (Moon.illumination(today()) * 100).toInt()

    // Per-date moon values for the "moon this week" strip + alignment insight.
    fun moonLabelForDate(dateIso: String): String = Moon.phase(LocalDate.parse(dateIso)).label
    fun moonIlluminationForDate(dateIso: String): Double = Moon.illumination(LocalDate.parse(dateIso))
    fun moonIsWaxingForDate(dateIso: String): Boolean = Moon.isWaxing(LocalDate.parse(dateIso))
    fun nextFullMoonIso(fromIso: String): String = Moon.nextFullMoon(LocalDate.parse(fromIso)).toString()
    fun daysUntilNextFullMoon(fromIso: String): Int =
        LocalDate.parse(fromIso).daysUntil(Moon.nextFullMoon(LocalDate.parse(fromIso)))

    /**
     * On-device principal-phase marker for a calendar date. One of:
     * "" · "new" · "first-quarter" · "full" · "last-quarter" · "blue-moon" · "black-moon".
     * Special events (eclipses/supermoons) are separate bundled data, not computed here.
     */
    fun moonPhaseMarker(dateIso: String): String {
        val d = LocalDate.parse(dateIso)
        return when (Moon.principalPhase(d)) {
            Moon.PrincipalPhase.FULL -> if (Moon.isBlueMoon(d)) "blue-moon" else "full"
            Moon.PrincipalPhase.NEW -> if (Moon.isBlackMoon(d)) "black-moon" else "new"
            Moon.PrincipalPhase.FIRST_QUARTER -> "first-quarter"
            Moon.PrincipalPhase.LAST_QUARTER -> "last-quarter"
            null -> ""
        }
    }

    // --- Cycle (last-period start given as ISO yyyy-MM-dd) ---
    private fun inputFrom(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int) =
        CycleInput(
            periods = listOf(PeriodEntry(LocalDate.parse(lastPeriodStartIso))),
            averageCycleLength = cycleLength,
            averagePeriodLength = periodLength,
        )

    fun cyclePhaseLabel(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int): String =
        Cycle.status(inputFrom(lastPeriodStartIso, cycleLength, periodLength)).phase.label

    fun cyclePhaseEmoji(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int): String =
        Cycle.status(inputFrom(lastPeriodStartIso, cycleLength, periodLength)).phase.emoji

    fun cycleDay(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int): Int =
        Cycle.status(inputFrom(lastPeriodStartIso, cycleLength, periodLength)).cycleDay

    fun daysUntilNextPeriod(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int): Int =
        Cycle.status(inputFrom(lastPeriodStartIso, cycleLength, periodLength)).daysUntilNextPeriod

    /** Days past the predicted start with nothing logged; 0 when not overdue. */
    fun daysLate(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int): Int =
        Cycle.status(inputFrom(lastPeriodStartIso, cycleLength, periodLength)).daysLate

    /**
     * How far Cycluna trusts its prediction: `"normal"`, `"late"`, or `"unclear"`.
     * A String (not the enum) so Swift/Kotlin interop stays on simple types.
     */
    fun trackingState(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int): String =
        when (Cycle.status(inputFrom(lastPeriodStartIso, cycleLength, periodLength)).tracking) {
            CycleTracking.NORMAL -> "normal"
            CycleTracking.LATE -> "late"
            CycleTracking.UNCLEAR -> "unclear"
        }

    /**
     * Roll an onboarding-selected date forward to the current cycle's start (see
     * [Cycle.mostRecentStart]). Input/return are ISO `yyyy-MM-dd`.
     */
    fun mostRecentPeriodStartIso(selectedIso: String, cycleLength: Int): String =
        Cycle.mostRecentStart(LocalDate.parse(selectedIso), cycleLength).toString()

    // --- Predicted dates (ISO yyyy-MM-dd), computed in the shared core so iOS
    //     and Android stay identical. Fertile window ≈ the ovulatory segment. ---
    // These are "today" projections, so the anchor is rolled forward to the current cycle
    // first (an old logged/onboarding date otherwise pins the fertile window to months ago).
    private fun anchorPlus(startIso: String, cycleLength: Int, days: Int): String =
        Cycle.mostRecentStart(LocalDate.parse(startIso), cycleLength)
            .plus(DatePeriod(days = days)).toString()

    /**
     * The start of the period the user is currently waiting for — one cycle on from the
     * logged anchor, deliberately NOT rolled forward. Rolling would skip an overdue period
     * and point a full cycle ahead, hiding the very lateness the UI needs to report. When
     * tracking is `"unclear"` this date is in the past and the UI stops showing it.
     */
    fun nextPeriodIso(lastPeriodStartIso: String, cycleLength: Int): String =
        LocalDate.parse(lastPeriodStartIso).plus(DatePeriod(days = cycleLength)).toString()

    fun fertileStartIso(lastPeriodStartIso: String, cycleLength: Int): String =
        Cycle.fertileWindow(LocalDate.parse(lastPeriodStartIso), cycleLength).first.toString()

    fun fertileEndIso(lastPeriodStartIso: String, cycleLength: Int): String =
        Cycle.fertileWindow(LocalDate.parse(lastPeriodStartIso), cycleLength).second.toString()

    // Un-rolled variant for list UIs (the predicted-cycles card): the window of the ONE
    // cycle starting at the given date. Keeps future windows on the same offsets as the
    // hero tile and calendar — a native re-derivation drifted by a day on odd lengths.
    fun fertileStartForCycleIso(cycleStartIso: String, cycleLength: Int): String =
        Cycle.fertileWindowForCycle(LocalDate.parse(cycleStartIso), cycleLength).first.toString()

    fun fertileEndForCycleIso(cycleStartIso: String, cycleLength: Int): String =
        Cycle.fertileWindowForCycle(LocalDate.parse(cycleStartIso), cycleLength).second.toString()

    // --- Per-date lookups for the calendar ---
    fun phaseLabelForDate(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int, dateIso: String): String =
        Cycle.phaseForDate(inputFrom(lastPeriodStartIso, cycleLength, periodLength), LocalDate.parse(dateIso))?.label ?: ""

    fun cycleDayForDate(lastPeriodStartIso: String, cycleLength: Int, periodLength: Int, dateIso: String): Int =
        Cycle.cycleDayForDate(inputFrom(lastPeriodStartIso, cycleLength, periodLength), LocalDate.parse(dateIso)) ?: 0

    /**
     * Cycle day for a calendar date, measured against LOGGED history. The anchor is the
     * latest logged start on or before the date. A past cycle counts plainly from its own
     * start — day 40 of a long gap is day 40, never wrapped, because the past is history —
     * while dates from the LATEST start onward project forward by whole cycles, matching
     * [dayMarker]. Before the first logged start there is no cycle to speak of → 0
     * (callers hide the number).
     */
    fun historyCycleDay(periodStartsCsv: String, cycleLength: Int, dateIso: String): Int {
        val date = LocalDate.parse(dateIso)
        val starts = parsePeriods(periodStartsCsv).map { it.start }.sorted()
        val anchor = starts.lastOrNull { it <= date } ?: return 0
        val daysSince = anchor.daysUntil(date)
        return if (anchor == starts.last()) (daysSince % cycleLength) + 1 else daysSince + 1
    }

    /**
     * Phase label for a calendar date, on [historyCycleDay]'s anchor rules — same
     * boundaries as `Cycle.status`. "" before the first logged start.
     */
    fun historyPhaseLabel(periodStartsCsv: String, cycleLength: Int, periodLength: Int, dateIso: String): String {
        val cycleDay = historyCycleDay(periodStartsCsv, cycleLength, dateIso)
        if (cycleDay == 0) return ""
        val half = cycleLength / 2
        return when {
            cycleDay <= periodLength -> Phase.MENSTRUAL
            cycleDay <= half - 2 -> Phase.FOLLICULAR
            cycleDay <= half + 2 -> Phase.OVULATORY
            else -> Phase.LUTEAL
        }.label
    }

    /**
     * Predicted cycle length from logged period starts (comma-separated ISO dates).
     * Uses the recent-gap average when there are ≥2 valid periods, else the fallback.
     */
    fun predictedCycleLength(periodStartsCsv: String, cycleLengthFallback: Int): Int =
        Cycle.predictedCycleLength(CycleInput(periods = parsePeriods(periodStartsCsv), averageCycleLength = cycleLengthFallback))

    /**
     * Calendar marker for a date. Marks EVERY logged period (from all starts),
     * plus the current cycle's fertile window + next predicted period.
     * One of: period, predicted-period, fertile-peak/high/medium, none.
     */
    fun dayMarker(periodStartsCsv: String, cycleLength: Int, periodLength: Int, dateIso: String): String {
        val date = LocalDate.parse(dateIso)
        val starts = parsePeriods(periodStartsCsv).map { it.start }.sorted()
        // Any logged period (past or current) → solid period.
        for (s in starts) {
            val d = s.daysUntil(date)
            if (d in 0 until periodLength) return "period"
        }
        val anchor = starts.lastOrNull() ?: return "none"
        val daysSince = anchor.daysUntil(date)
        // The past is history, not prediction: before the latest logged start only the
        // logged periods themselves mark the calendar (handled above). Projected periods
        // and fertile days exist from the current anchor FORWARD only — projecting them
        // backwards drew phantom predictions between real logged periods.
        if (daysSince < 0) return "none"
        val cycleDay = (daysSince % cycleLength) + 1
        val half = cycleLength / 2
        if (cycleDay <= periodLength) return "predicted-period"
        return when {
            cycleDay == half -> "fertile-peak"
            cycleDay == half - 1 || cycleDay == half + 1 -> "fertile-high"
            cycleDay == half - 2 || cycleDay == half + 2 -> "fertile-medium"
            else -> "none"
        }
    }

    /**
     * Defence in depth alongside `CyclePersistence.decode`, which already drops non-ISO
     * entries: an unparseable date here would throw straight out of a calendar cell.
     * Skip what can't be read rather than taking the screen down with it.
     */
    private fun parsePeriods(csv: String): List<PeriodEntry> =
        csv.split(",").mapNotNull { s ->
            s.trim().takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { PeriodEntry(it) }
        }

    // --- Mood × moon (see MoonMoodInsights) ---
    // Buckets cross the bridge as stable slugs ("waxing-gibbous"), never as enums or dates;
    // the native layer maps them to its own wording.

    /** The eight moon buckets in synodic order. */
    fun moonBucketOrder(): List<String> = MoonMoodInsights.orderedBucketKeys()

    /** The bucket a date falls in — always agrees with [moonLabelForDate] for that date. */
    fun moonBucketForDate(dateIso: String): String =
        MoonMoodInsights.bucketForDate(LocalDate.parse(dateIso)).slug

    /** Representative illumination (0..1) for a bucket, for drawing its disc. */
    fun moonBucketIllumination(bucketKey: String): Double =
        MoonMoodInsights.bucketIllumination(bucketKey)

    /** Whether a bucket sits in the waxing half of the month. */
    fun moonBucketIsWaxing(bucketKey: String): Boolean =
        MoonMoodInsights.bucketIsWaxing(bucketKey)

    /**
     * Typical hormone reference curves (Speroff / Stricker), 0..1 relative.
     * Ported from the web `LinearHormoneChart`. Educational only — not diagnostic.
     */
    fun hormoneLevels(day: Int, cycleLength: Int): HormoneLevels {
        // Map the given cycle length onto the canonical 28-day landmarks.
        val r = ((day - 1.0) / (cycleLength - 1.0)) * 27 + 1
        fun gauss(x: Double, mu: Double, sigma: Double) = exp(-((x - mu) * (x - mu)) / (2 * sigma * sigma))
        fun ramp(x: Double, a: Double, b: Double): Double {
            val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
            return t * t * (3 - 2 * t)
        }
        val estrogen = (0.12 + 0.30 * ramp(r, 4.0, 12.0) + 0.55 * gauss(r, 13.0, 1.4) -
            0.14 * gauss(r, 14.5, 0.8) + 0.42 * gauss(r, 21.0, 3.0) - 0.35 * ramp(r, 25.0, 28.0)).coerceIn(0.05, 0.97)
        val progesterone = (0.05 + 0.85 * ramp(r, 14.5, 21.0) - 0.78 * ramp(r, 23.0, 28.0)).coerceIn(0.04, 0.95)
        val lh = (0.06 + 0.85 * gauss(r, 13.0, 0.8)).coerceIn(0.05, 0.95)
        val fsh = (0.10 + 0.45 * gauss(r, 3.0, 2.5) + 0.35 * gauss(r, 13.0, 1.0)).coerceAtLeast(0.08)
        return HormoneLevels(estrogen, progesterone, lh, fsh)
    }
}
