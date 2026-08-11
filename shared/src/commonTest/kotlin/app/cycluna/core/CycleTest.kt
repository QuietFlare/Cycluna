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
    fun fertileWindowForCycleMatchesTheRollingWindowsOffsets() {
        // The un-rolled per-cycle window (used by the predicted-cycles list) must sit on
        // exactly the same day offsets as the rolling one — including odd lengths, where a
        // native re-derivation from the NEXT period's date drifted by a day.
        val start = LocalDate(2026, 7, 22)
        for (cl in listOf(28, 29)) {
            val (a, b) = Cycle.fertileWindowForCycle(start, cl)
            val (ra, rb) = Cycle.fertileWindow(start, cl, today = start)
            assertEquals(ra, a, "start, cl=$cl")
            assertEquals(rb, b, "end, cl=$cl")
        }
        // Pinned absolute dates for the odd case: days 12–16 of a 29-day cycle.
        val (s29, e29) = Cycle.fertileWindowForCycle(start, 29)
        assertEquals(LocalDate(2026, 8, 2), s29)
        assertEquals(LocalDate(2026, 8, 6), e29)
    }

    @Test
    fun mostRecentStartLeavesRecentDateUnchanged() {
        val picked = LocalDate(2024, 2, 26)
        val today = LocalDate(2024, 3, 1) // 4 days later, within one cycle
        assertEquals(picked, Cycle.mostRecentStart(picked, cycleLength = 28, today = today))
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

    // --- The configured cycle length is the contract ----------------------------

    @Test
    fun historyNeverOverridesTheUsersCycleLengthSetting() {
        // Whatever the logged gaps say — one late period, or several agreeing 31-day
        // cycles — the prediction always uses the length the user configured. Learning
        // from history absorbed lateness into the baseline and silently overrode an
        // explicit setting; the history itself stays recorded exactly as logged.
        val oneLateGap = CycleInput(
            periods = listOf(PeriodEntry(LocalDate(2026, 7, 1)), PeriodEntry(LocalDate(2026, 8, 2))),
            averageCycleLength = 28,
        )
        assertEquals(28, Cycle.predictedCycleLength(oneLateGap, today = LocalDate(2026, 8, 2)))

        val threeAgreeingGaps = CycleInput(
            periods = listOf(
                PeriodEntry(LocalDate(2026, 6, 1)),
                PeriodEntry(LocalDate(2026, 7, 2)),
                PeriodEntry(LocalDate(2026, 8, 2)),
            ),
            averageCycleLength = 28,
        )
        assertEquals(28, Cycle.predictedCycleLength(threeAgreeingGaps, today = LocalDate(2026, 8, 2)))
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

    // The old projectedCycleDay wrapped BACKWARD from the anchor, numbering past months as
    // projections. That drew phantom predictions between real logged periods once history
    // could be backfilled — replaced by CyclunaCore.historyCycleDay (tested in
    // CyclunaCoreTest), which counts past cycles from their own logged starts.

    @Test
    fun dayMarkerProjectsPeriodsForwardOnly() {
        val anchor = "2026-07-22"
        // Forward projection is unchanged: one cycle on lands on a predicted period day.
        assertEquals("predicted-period",
            CyclunaCore.dayMarker(anchor, cycleLength = 28, periodLength = 5, dateIso = "2026-08-19"))
        // Before the anchor there are no projections — the past is history, not
        // prediction, and phantom "predicted" days collided with backfilled real periods.
        assertEquals("none",
            CyclunaCore.dayMarker(anchor, cycleLength = 28, periodLength = 5, dateIso = "2026-06-24"))
        // A luteal day in the current cycle is not a period/fertile marker.
        assertEquals("none",
            CyclunaCore.dayMarker(anchor, cycleLength = 28, periodLength = 5, dateIso = "2026-08-10"))
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
