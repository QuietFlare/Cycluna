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
    fun predictedCycleLengthIgnoresUnparseableEntries() {
        // Two good starts 27 days apart, plus junk that must not break the average.
        val csv = "2026-06-27,oops,2026-07-24"
        assertEquals(27, CyclunaCore.predictedCycleLength(csv, 28))
    }

    @Test
    fun predictedCycleLengthFallsBackWhenNothingParses() {
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
