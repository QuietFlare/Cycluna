package app.cycluna.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Swift-facing facade. Everything iOS calls goes through here, so these cover the
 * String/Int boundary itself — especially inputs that are malformed rather than merely
 * unusual, since a throw here surfaces as an app crash while rendering Home.
 */
class CyclunaCoreTest {

    private val anchor = "2026-07-22"

    // --- Malformed input ------------------------------------------------------

    @Test
    fun dayMarkerSurvivesAGarbledPeriodList() {
        // A hand-edited or partially corrupted cycle-store.json can carry non-ISO entries:
        // `decode` does not validate them, so the facade must not throw on one.
        val csv = "2026-07-22,not-a-date,,2026-06-24"
        val marker = CyclunaCore.dayMarker(csv, 28, 5, "2026-07-23")
        assertEquals("period", marker)
    }

    @Test
    fun dayMarkerWithNothingUsableReportsNoMarkerRatherThanThrowing() {
        assertEquals("none", CyclunaCore.dayMarker("nonsense,also-nonsense", 28, 5, "2026-07-23"))
        assertEquals("none", CyclunaCore.dayMarker("", 28, 5, "2026-07-23"))
    }

    @Test
    fun dayMarkerNeverPredictsIntoThePast() {
        val csv = "2026-05-17,2026-08-11"
        // Logged periods still mark their own days.
        assertEquals("period", CyclunaCore.dayMarker(csv, 28, 5, "2026-05-18"))
        // But no predicted periods or fertile days before the latest logged start —
        // the past is history, not prediction.
        assertEquals("none", CyclunaCore.dayMarker(csv, 28, 5, "2026-06-14"))
        assertEquals("none", CyclunaCore.dayMarker(csv, 28, 5, "2026-05-31"))
        // Forward from the anchor, prediction is unchanged.
        assertEquals("predicted-period", CyclunaCore.dayMarker(csv, 28, 5, "2026-09-08"))
    }

    @Test
    fun historyCycleDayCountsPastCyclesFromTheirOwnStart() {
        val csv = "2026-05-17,2026-08-11"
        // 5 Jun sits in the May cycle: its own day 20, not a projection from August.
        assertEquals(20, CyclunaCore.historyCycleDay(csv, 28, "2026-06-05"))
        // A long gap stays honest: day 40 of that cycle, never wrapped.
        assertEquals(40, CyclunaCore.historyCycleDay(csv, 28, "2026-06-25"))
        // From the latest start it projects forward by whole cycles.
        assertEquals(1, CyclunaCore.historyCycleDay(csv, 28, "2026-09-08"))
        // Before any logged start there is no cycle day to speak of.
        assertEquals(0, CyclunaCore.historyCycleDay(csv, 28, "2026-05-01"))
    }

    @Test
    fun historyPhaseLabelFollowsTheSameAnchorRules() {
        val csv = "2026-05-17,2026-08-11"
        assertEquals("Menstrual", CyclunaCore.historyPhaseLabel(csv, 28, 5, "2026-05-18"))
        assertEquals("Luteal", CyclunaCore.historyPhaseLabel(csv, 28, 5, "2026-06-25"))
        assertEquals("", CyclunaCore.historyPhaseLabel(csv, 28, 5, "2026-05-01"))
    }

    @Test
    fun predictedCycleLengthIsAlwaysTheConfiguredSetting() {
        // History never overrides the setting — even a clean run of 27-day gaps.
        val csv = "2026-05-31,2026-06-27,2026-07-24"
        assertEquals(28, CyclunaCore.predictedCycleLength(csv, 28))
    }

    @Test
    fun predictedCycleLengthClampsAnImplausibleSetting() {
        assertEquals(30, CyclunaCore.predictedCycleLength("junk", 30))
        assertEquals(Cycle.DEFAULT_CYCLE_LENGTH, CyclunaCore.predictedCycleLength("", 99))
    }

    // --- Tracking state mapping ----------------------------------------------

    @Test
    fun trackingStateMapsEachCoreStateToTheStringSwiftExpects() {
        // Swift parses these into TrackingState; an unrecognised value silently degrades
        // to "normal" there, so the exact spellings matter.
        val fresh = CyclunaCore.trackingState(todayMinusDays(3), 28, 5)
        assertEquals("normal", fresh)

        val overdue = CyclunaCore.trackingState(todayMinusDays(28 + 3), 28, 5)
        assertEquals("late", overdue)
        assertEquals(3, CyclunaCore.daysLate(todayMinusDays(28 + 3), 28, 5))

        val lost = CyclunaCore.trackingState(todayMinusDays(28 + Cycle.UNCLEAR_AFTER_LATE_DAYS + 1), 28, 5)
        assertEquals("unclear", lost)
    }

    private fun todayMinusDays(days: Int): String =
        Clock.System.todayIn(TimeZone.currentSystemDefault())
            .minus(DatePeriod(days = days))
            .toString()

    // --- Hormone curves (drive the Phases chart) ------------------------------

    @Test
    fun hormoneLevelsStayInRangeAcrossEveryCycleDayAndLength() {
        for (length in Cycle.MIN_CYCLE_LENGTH..Cycle.MAX_CYCLE_LENGTH) {
            for (day in 1..length) {
                val h = CyclunaCore.hormoneLevels(day, length)
                for ((name, v) in listOf(
                    "estrogen" to h.estrogen, "progesterone" to h.progesterone,
                    "lh" to h.lh, "fsh" to h.fsh,
                )) {
                    assertTrue(v in 0.0..1.0, "$name was $v on day $day of $length")
                }
            }
        }
    }

    @Test
    fun lhPeaksAroundOvulationNotDuringThePeriod() {
        val atOvulation = CyclunaCore.hormoneLevels(13, 28).lh
        assertTrue(atOvulation > CyclunaCore.hormoneLevels(3, 28).lh)
        assertTrue(atOvulation > CyclunaCore.hormoneLevels(25, 28).lh)
    }

    // --- Moon marker ----------------------------------------------------------

    @Test
    fun moonPhaseMarkerReturnsAKnownValue() {
        val allowed = setOf("", "new", "first-quarter", "full", "last-quarter", "blue-moon", "black-moon")
        var sawSomething = false
        // A full synodic month must contain at least one principal phase.
        for (d in 1..30) {
            val iso = "2026-08-%02d".format(d)
            val m = CyclunaCore.moonPhaseMarker(iso)
            assertTrue(m in allowed, "unexpected marker '$m' on $iso")
            if (m.isNotEmpty()) sawSomething = true
        }
        assertTrue(sawSomething, "a whole month with no principal moon phase looks wrong")
    }

    @Test
    fun nextFullMoonIsAlwaysInTheFuture() {
        val from = "2026-08-01"
        val next = CyclunaCore.nextFullMoonIso(from)
        assertNotNull(next)
        assertTrue(next > from, "next full moon $next should be after $from")
        assertTrue(CyclunaCore.daysUntilNextFullMoon(from) in 0..31)
    }
}
