package app.cycluna.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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
