package app.cycluna.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleTest {
    private fun input(vararg starts: LocalDate) =
        CycleInput(periods = starts.map { PeriodEntry(it) })

    @Test
    fun noPeriodsDefaultsToFollicularDayOne() {
        val s = Cycle.status(CycleInput(), today = LocalDate(2024, 1, 1))
        assertEquals(1, s.cycleDay)
        assertEquals(Phase.FOLLICULAR, s.phase)
        assertEquals(28, s.cycleLength)
    }

    @Test
    fun earlyDaysAreMenstrual() {
        val s = Cycle.status(input(LocalDate(2024, 1, 1)), today = LocalDate(2024, 1, 3))
        assertEquals(3, s.cycleDay)
        assertEquals(Phase.MENSTRUAL, s.phase)
        assertEquals(26, s.daysUntilNextPeriod) // 28 - 3 + 1
    }

    @Test
    fun ovulationAroundMidCycle() {
        val s = Cycle.status(input(LocalDate(2024, 1, 1)), today = LocalDate(2024, 1, 14))
        assertEquals(Phase.OVULATORY, s.phase)
    }

    @Test
    fun pastCycleLengthIsLateLuteal() {
        val s = Cycle.status(input(LocalDate(2024, 1, 1)), today = LocalDate(2024, 2, 15))
        assertEquals(Phase.LUTEAL, s.phase)
    }

    // --- Late / missed periods -------------------------------------------------
    // The rule: an overdue cycle stays OPEN. It must never roll into a fabricated new
    // cycle, which would report "Day 1 · Menstrual" on a day no period arrived.

    @Test
    fun onDueDateIsNotYetLate() {
        // 28-day cycle anchored Jan 1 → the period is due Jan 29.
        val s = Cycle.status(input(LocalDate(2024, 1, 1)), today = LocalDate(2024, 1, 29))
        assertEquals(0, s.daysLate)
        assertEquals(CycleTracking.NORMAL, s.tracking)
    }

    @Test
    fun overdueCycleReportsDaysLateInsteadOfRestarting() {
        val s = Cycle.status(input(LocalDate(2024, 1, 1)), today = LocalDate(2024, 2, 1))
        assertEquals(3, s.daysLate)
        assertEquals(CycleTracking.LATE, s.tracking)
        // The regression this guards: cycleDay used to roll back to 1 here.
        assertEquals(32, s.cycleDay)
        assertEquals(Phase.LUTEAL, s.phase)
        assertEquals(LocalDate(2024, 1, 1), s.lastPeriodStart)
    }

    @Test
    fun stillLateOnTheFinalGraceDay() {
        val due = LocalDate(2024, 1, 29)
        val s = Cycle.status(
            input(LocalDate(2024, 1, 1)),
            today = due.plus(DatePeriod(days = Cycle.UNCLEAR_AFTER_LATE_DAYS))
        )
        assertEquals(Cycle.UNCLEAR_AFTER_LATE_DAYS, s.daysLate)
        assertEquals(CycleTracking.LATE, s.tracking)
    }

    @Test
    fun becomesUnclearOneDayPastTheGrace() {
        val due = LocalDate(2024, 1, 29)
        val s = Cycle.status(
            input(LocalDate(2024, 1, 1)),
            today = due.plus(DatePeriod(days = Cycle.UNCLEAR_AFTER_LATE_DAYS + 1))
        )
        assertEquals(CycleTracking.UNCLEAR, s.tracking)
    }

    @Test
    fun missedPeriodStaysUnclearRatherThanInventingCycles() {
        // ~3 months on with nothing logged: previously this rolled 3 whole cycles and
        // claimed a normal mid-cycle day.
        val s = Cycle.status(input(LocalDate(2024, 1, 1)), today = LocalDate(2024, 4, 1))
        assertEquals(CycleTracking.UNCLEAR, s.tracking)
        assertTrue(s.daysLate > Cycle.UNCLEAR_AFTER_LATE_DAYS, "was ${s.daysLate}")
    }

    @Test
    fun loggingAPeriodClearsLateness() {
        // Same overdue situation, but the user logs the period that finally arrived.
        val s = Cycle.status(
            input(LocalDate(2024, 1, 1), LocalDate(2024, 2, 1)),
            today = LocalDate(2024, 2, 2)
        )
        assertEquals(0, s.daysLate)
        assertEquals(CycleTracking.NORMAL, s.tracking)
        assertEquals(2, s.cycleDay)
    }

    @Test
    fun mostRecentStartRollsOldDateIntoCurrentCycle() {
        // Picked 60 days ago, 28-day cycle → 2 whole cycles (56 days) forward = 4 days ago.
        val picked = LocalDate(2024, 1, 1)
        val today = LocalDate(2024, 3, 1) // 60 days later
        val rolled = Cycle.mostRecentStart(picked, cycleLength = 28, today = today)
        assertEquals(LocalDate(2024, 2, 26), rolled)
        // And that rolled anchor puts "today" on a sane cycle day (5), not day 61.
        val s = Cycle.status(input(rolled), today = today)
        assertEquals(5, s.cycleDay)
    }

    @Test
    fun fertileWindowRollsForwardOncePassed() {
        val anchor = LocalDate(2026, 7, 22) // 28-day cycle → fertile ~Aug 2–6
        val today = LocalDate(2026, 8, 14)  // that window is already past
        val (start, end) = Cycle.fertileWindow(anchor, 28, today)
        assertTrue(start > today, "fertile start should be upcoming, was $start")
        assertTrue(end > start)
    }

    @Test
    fun fertileWindowShowsCurrentWhenNotYetPassed() {
        val anchor = LocalDate(2026, 7, 22)
        val today = LocalDate(2026, 7, 30) // before this cycle's fertile window
        val (start, _) = Cycle.fertileWindow(anchor, 28, today)
        assertEquals(LocalDate(2026, 8, 2), start)
    }

    @Test
    fun mostRecentStartLeavesRecentDateUnchanged() {
        val picked = LocalDate(2024, 2, 26)
        val today = LocalDate(2024, 3, 1) // 4 days later, within one cycle
        assertEquals(picked, Cycle.mostRecentStart(picked, cycleLength = 28, today = today))
    }

    // --- isIrregular ----------------------------------------------------------
    // NOTE: nothing in the app calls this yet. Covered so its behaviour is known before it
    // gets wired to UI — it needs 3 starts inside the last 3 months and flags any gap
    // outside 21..35 days (narrower on the high side than MAX_CYCLE_LENGTH's 45).

    private fun startsBackFrom(today: LocalDate, vararg gaps: Int): List<PeriodEntry> {
        // Build starts ending near `today`, spaced by the given gaps (most recent last).
        var d = today.minus(DatePeriod(days = gaps.sum()))
        val out = mutableListOf(PeriodEntry(d))
        for (g in gaps) {
            d = d.plus(DatePeriod(days = g))
            out.add(PeriodEntry(d))
        }
        return out
    }

    @Test
    fun regularCyclesAreNotFlagged() {
        val today = LocalDate(2026, 8, 1)
        assertFalse(Cycle.isIrregular(startsBackFrom(today, 28, 29), today))
    }

    @Test
    fun aLongGapIsFlaggedAsIrregular() {
        val today = LocalDate(2026, 8, 1)
        assertTrue(Cycle.isIrregular(startsBackFrom(today, 28, 40), today))
    }

    @Test
    fun aShortGapIsFlaggedAsIrregular() {
        val today = LocalDate(2026, 8, 1)
        assertTrue(Cycle.isIrregular(startsBackFrom(today, 28, 18), today))
    }

    @Test
    fun fewerThanThreeRecentPeriodsIsNeverIrregular() {
        val today = LocalDate(2026, 8, 1)
        // Not enough history to judge — must stay false rather than guess.
        assertFalse(Cycle.isIrregular(startsBackFrom(today, 40), today))
        assertFalse(Cycle.isIrregular(emptyList(), today))
    }

    @Test
    fun nextPeriodIsoDoesNotRollPastAnOverduePeriod() {
        // One cycle on from the logged anchor, full stop. This used to roll the anchor to the
        // current cycle first, which skipped an overdue period and pointed a whole cycle ahead
        // — the bug that made lateness impossible to show.
        assertEquals("2026-08-19", CyclunaCore.nextPeriodIso("2026-07-22", 28))
        // Even for an anchor months back, the answer stays anchor + one cycle (the UI switches
        // to "unclear" there rather than trusting this date).
        assertEquals("2024-01-29", CyclunaCore.nextPeriodIso("2024-01-01", 28))
    }

    // --- JS parity + clamping -------------------------------------------------
    // CLAUDE.md: JS `Math.round` is half-up, Kotlin's `roundToInt` is half-to-EVEN. The port
    // uses floor(x + 0.5). These cases are chosen so the two disagree — a silent switch back
    // to roundToInt would fail here and nowhere else.

    // --- Learning cycle length from history ------------------------------------

    @Test
    fun oneObservedGapDoesNotOverrideTheUsersSetting() {
        // The reported bug: onboard with 1 July, log a period on 2 August (four days late),
        // and the single 32-day gap silently became the predicted cycle length — so the next
        // period showed "in 32 days" and the lateness was absorbed into the baseline.
        val d = CycleInput(
            periods = listOf(PeriodEntry(LocalDate(2026, 7, 1)), PeriodEntry(LocalDate(2026, 8, 2))),
            averageCycleLength = 28,
        )
        assertEquals(28, Cycle.predictedCycleLength(d, today = LocalDate(2026, 8, 2)))
    }

    @Test
    fun twoAgreeingGapsDoOverrideTheSetting() {
        // Three starts, two gaps of 31 and 31 → a real pattern, worth learning.
        val d = CycleInput(
            periods = listOf(
                PeriodEntry(LocalDate(2026, 6, 1)),
                PeriodEntry(LocalDate(2026, 7, 2)),
                PeriodEntry(LocalDate(2026, 8, 2)),
            ),
            averageCycleLength = 28,
        )
        assertEquals(31, Cycle.predictedCycleLength(d, today = LocalDate(2026, 8, 2)))
    }

    @Test
    fun gapsOutsideThePlausibleRangeDoNotCountTowardLearning() {
        // Three starts, but one gap is 60 days (outside 21..45), leaving a single usable
        // gap — not enough to relearn.
        val d = CycleInput(
            periods = listOf(
                PeriodEntry(LocalDate(2026, 5, 5)),
                PeriodEntry(LocalDate(2026, 7, 4)),   // +60, rejected
                PeriodEntry(LocalDate(2026, 8, 2)),   // +29, the only usable gap
            ),
            averageCycleLength = 28,
        )
        assertEquals(28, Cycle.predictedCycleLength(d, today = LocalDate(2026, 8, 2)))
    }

    @Test
    fun averageCycleLengthRoundsHalfUpLikeJavaScript() {
        // Gaps of 26 and 27 → mean 26.5. Half-up = 27; half-to-even would give 26.
        val starts = listOf(
            LocalDate(2026, 6, 1),
            LocalDate(2026, 6, 27),   // +26
            LocalDate(2026, 7, 24),   // +27
        )
        val predicted = Cycle.predictedCycleLength(
            CycleInput(periods = starts.map { PeriodEntry(it) }),
            today = LocalDate(2026, 8, 1)
        )
        assertEquals(27, predicted)
    }

    @Test
    fun averagePeriodLengthRoundsHalfUpLikeJavaScript() {
        // Lengths of 4 and 5 (inclusive of both ends) → mean 4.5. Half-up = 5, half-even = 4.
        val periods = listOf(
            PeriodEntry(LocalDate(2026, 6, 1), LocalDate(2026, 6, 4)),
            PeriodEntry(LocalDate(2026, 7, 1), LocalDate(2026, 7, 5)),
        )
        val averages = Cycle.recomputeAverages(periods, today = LocalDate(2026, 8, 1))
        assertEquals(5, averages.avgPeriod)
    }

    @Test
    fun cycleLengthOutsideTheAllowedRangeFallsBackToDefault() {
        fun predicted(setting: Int) = Cycle.predictedCycleLength(
            CycleInput(averageCycleLength = setting), today = LocalDate(2026, 8, 1)
        )
        assertEquals(Cycle.MIN_CYCLE_LENGTH, predicted(Cycle.MIN_CYCLE_LENGTH))
        assertEquals(Cycle.MAX_CYCLE_LENGTH, predicted(Cycle.MAX_CYCLE_LENGTH))
        // Out of range in either direction → the 28-day default, never the bad value.
        assertEquals(Cycle.DEFAULT_CYCLE_LENGTH, predicted(Cycle.MIN_CYCLE_LENGTH - 1))
        assertEquals(Cycle.DEFAULT_CYCLE_LENGTH, predicted(Cycle.MAX_CYCLE_LENGTH + 1))
        assertEquals(Cycle.DEFAULT_CYCLE_LENGTH, predicted(0))
    }

    @Test
    fun projectedCycleDayNumbersEveryMonthNotJustTheAnchorsOwn() {
        val anchor = "2026-07-22"
        fun day(iso: String) = CyclunaCore.projectedCycleDay(anchor, 28, iso)

        // The anchor's own cycle.
        assertEquals(1, day("2026-07-22"))
        assertEquals(11, day("2026-08-01"))
        // Future cycles wrap forward.
        assertEquals(1, day("2026-08-19"))
        assertEquals(11, day("2026-08-29"))
        // ...and past months must wrap BACKWARD rather than going blank, or the calendar
        // shows cycle-day numbers only from the anchor onward.
        assertEquals(1, day("2026-06-24"))  // exactly one cycle before
        assertEquals(8, day("2026-07-01"))   // 21 days before → 28 - 21 + 1
        assertEquals(4, day("2026-05-30"))   // 53 days before → 53 = 28 + 25, so day 4
    }

    @Test
    fun dayMarkerProjectsPeriodsBothDirections() {
        val anchor = "2026-07-22" // rolled current-cycle start
        // One cycle earlier (June 24) is projected as a period day, not blank.
        assertEquals("predicted-period",
            CyclunaCore.dayMarker(anchor, cycleLength = 28, periodLength = 5, dateIso = "2026-06-24"))
        // Two cycles earlier still lands on a period day.
        assertEquals("predicted-period",
            CyclunaCore.dayMarker(anchor, cycleLength = 28, periodLength = 5, dateIso = "2026-05-27"))
        // A luteal day in a past cycle (cycle day 20) is not a period/fertile marker.
        assertEquals("none",
            CyclunaCore.dayMarker(anchor, cycleLength = 28, periodLength = 5, dateIso = "2026-07-13"))
    }

    @Test
    fun phaseForDateWrapsAcrossCycles() {
        // one full 28-day cycle after the anchor → back to menstrual day 1
        assertEquals(Phase.MENSTRUAL, Cycle.phaseForDate(input(LocalDate(2024, 1, 1)), LocalDate(2024, 1, 29)))
    }

    @Test
    fun cycleDayNullWithoutPeriods() {
        assertNull(Cycle.cycleDayForDate(CycleInput(), LocalDate(2024, 1, 1)))
    }
}
