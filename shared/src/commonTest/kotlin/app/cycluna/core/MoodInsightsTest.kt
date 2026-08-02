package app.cycluna.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MoodInsightsTest {

    // anchor 2026-01-01, 28-day cycle: day 14 = ovulatory, day 22 = luteal, day 2 = menstrual.
    private fun data(vararg moods: MoodLog) =
        CycleData(periodStarts = listOf("2026-01-01"), cycleLengthSetting = 28,
                  periodLength = 5, moods = moods.toList())

    @Test
    fun insightNamesBrightestAndLowestPhases() {
        val d = data(
            MoodLog("2026-01-14", 5), MoodLog("2026-02-11", 5), MoodLog("2026-03-10", 5), // ovulatory, high
            MoodLog("2026-01-22", 2), MoodLog("2026-02-19", 2), MoodLog("2026-03-19", 2), // luteal, low
            MoodLog("2026-01-02", 3), MoodLog("2026-01-30", 3),                            // menstrual, mid
        )
        val ins = MoodInsights.insight(d, LocalDate(2026, 3, 20))
        assertNotNull(ins)
        assertEquals(Phase.OVULATORY, ins!!.brightest)
        assertEquals(Phase.LUTEAL, ins.lowest)
    }

    @Test
    fun noInsightWithTooFewLogs() {
        val d = data(MoodLog("2026-01-14", 5), MoodLog("2026-01-22", 2))
        assertNull(MoodInsights.insight(d, LocalDate(2026, 1, 25)))
    }

    @Test
    fun noInsightWhenMoodIsFlat() {
        // 8 logs but no meaningful gap between phases → no claim.
        val d = data(
            MoodLog("2026-01-14", 3), MoodLog("2026-02-11", 3), MoodLog("2026-03-10", 3),
            MoodLog("2026-01-22", 3), MoodLog("2026-02-19", 3), MoodLog("2026-03-19", 3),
            MoodLog("2026-01-02", 3), MoodLog("2026-01-30", 3),
        )
        assertNull(MoodInsights.insight(d, LocalDate(2026, 3, 20)))
    }

    // --- Insight window (last INSIGHT_CYCLE_WINDOW cycles + current) ------------
    // Eight starts 28 days apart, so the window begins at the 2nd start and the 1st
    // cycle's logs fall outside it.

    private val eightStarts = listOf(
        "2026-01-01", "2026-01-29", "2026-02-26", "2026-03-26",
        "2026-04-23", "2026-05-21", "2026-06-18", "2026-07-16",
    )
    private val windowed = LocalDate(2026, 8, 1)

    private fun longHistory(vararg moods: MoodLog) =
        CycleData(periodStarts = eightStarts, cycleLengthSetting = 28,
                  periodLength = 5, moods = moods.toList())

    @Test
    fun windowStartsAtTheCycleSixBeforeTheCurrentOne() {
        val d = longHistory()
        assertEquals(LocalDate(2026, 1, 29), MoodInsights.windowStart(d, windowed))
    }

    @Test
    fun insightIgnoresMoodsOlderThanTheWindow() {
        // Out-of-window: a whole cycle of HIGH luteal moods (Jan 17-20, first cycle).
        // In-window: bright ovulatory, low luteal. If the old logs leaked in, luteal would
        // average up and `lowest` would land on the menstrual logs instead.
        val d = longHistory(
            MoodLog("2026-01-17", 5), MoodLog("2026-01-18", 5),
            MoodLog("2026-01-19", 5), MoodLog("2026-01-20", 5),
            // in-window ovulatory (bright)
            MoodLog("2026-06-30", 5), MoodLog("2026-07-01", 5), MoodLog("2026-07-28", 5),
            // in-window luteal (low)
            MoodLog("2026-07-05", 2), MoodLog("2026-07-06", 2), MoodLog("2026-06-10", 2),
            // in-window menstrual (mid)
            MoodLog("2026-07-17", 3), MoodLog("2026-06-19", 3),
        )
        val ins = assertNotNull(MoodInsights.insight(d, windowed))
        assertEquals(8, ins.totalLogs, "the four out-of-window logs must not be counted")
        assertEquals(Phase.OVULATORY, ins.brightest)
        assertEquals(Phase.LUTEAL, ins.lowest)
    }

    @Test
    fun theOldestInWindowDayIsIncluded() {
        // A mood dated exactly on the window start counts — the boundary is inclusive.
        val onBoundary = longHistory(MoodLog("2026-01-29", 4))
        assertEquals(1, MoodInsights.phaseAverages(onBoundary, windowed).sumOf { it.count })

        val dayBefore = longHistory(MoodLog("2026-01-28", 4))
        assertEquals(0, MoodInsights.phaseAverages(dayBefore, windowed).sumOf { it.count })
    }

    @Test
    fun guardrailsStillApplyWithinTheWindow() {
        // Plenty of history, but only three logs inside the window → no claim.
        val d = longHistory(
            MoodLog("2026-01-17", 5), MoodLog("2026-01-18", 5), MoodLog("2026-01-19", 5),
            MoodLog("2026-07-28", 5), MoodLog("2026-07-05", 2), MoodLog("2026-07-17", 3),
        )
        assertNull(MoodInsights.insight(d, windowed))
    }

    @Test
    fun insightReportsHowManyCyclesItCovers() {
        val d = longHistory(
            MoodLog("2026-06-30", 5), MoodLog("2026-07-01", 5), MoodLog("2026-07-28", 5),
            MoodLog("2026-07-05", 2), MoodLog("2026-07-06", 2), MoodLog("2026-06-10", 2),
            MoodLog("2026-07-17", 3), MoodLog("2026-06-19", 3),
        )
        val ins = assertNotNull(MoodInsights.insight(d, windowed))
        // Starts from 2026-01-29 onward, inclusive: seven of the eight.
        assertEquals(7, ins.cyclesCovered)
    }

    // --- Paging back through history -------------------------------------------

    @Test
    fun cycleSpansCoverEveryLoggedCycleNewestLast() {
        val d = longHistory()
        val spans = MoodInsights.cycleSpans(d, windowed)
        assertEquals(eightStarts.size, spans.size)
        assertEquals("2026-01-01", spans.first().startIso)
        assertEquals("2026-07-16", spans.last().startIso)
        // Completed cycles end where the next begins.
        assertEquals("2026-01-29", spans.first().endIso)
        assertEquals(28, spans.first().length)
    }

    @Test
    fun theCurrentCyclesEndIsProjectedNotObserved() {
        val d = longHistory()
        val current = MoodInsights.cycleSpans(d, windowed).last()
        // 2026-07-16 + 28 predicted days; it hasn't happened yet.
        assertEquals("2026-08-13", current.endIso)
    }

    @Test
    fun pagingCannotGoBackBeforeTheEarliestLoggedPeriod() {
        // Two starts → exactly two pages. The app cannot know where earlier cycles began.
        val d = CycleData(periodStarts = listOf("2026-07-01", "2026-08-02"),
                          cycleLengthSetting = 28, periodLength = 5)
        assertEquals(2, MoodInsights.cycleSpans(d, LocalDate(2026, 8, 2)).size)
        assertTrue(MoodInsights.cycleSpans(CycleData(), windowed).isEmpty())
    }

    @Test
    fun cyclePointsCoverOnlyTheRequestedCycle() {
        val d = longHistory(
            MoodLog("2026-06-19", 4),   // day 2 of the 2026-06-18 cycle
            MoodLog("2026-06-30", 5),   // day 13 of the same cycle
            MoodLog("2026-07-17", 2),   // next cycle — must not appear
        )
        val pts = MoodInsights.cyclePoints(d, "2026-06-18", "2026-07-16")
        assertEquals(listOf(2, 13), pts.map { it.cycleDay })
    }

    @Test
    fun summaryDescribesASpanWithoutGuardrails() {
        // Two logs is far too thin for a claim, but describing them is always honest.
        val d = longHistory(MoodLog("2026-07-17", 2), MoodLog("2026-07-18", 4))
        val s = MoodInsights.summaryForRange(d, "2026-07-16", "2026-08-01")
        assertEquals(2, s.count)
        assertEquals(3.0, s.average)
        // ...and the same span supports no claim at all.
        assertNull(MoodInsights.insightForRange(d, "2026-07-16", "2026-08-01"))
    }

    @Test
    fun emptySpanSummarisesAsZeroRatherThanFailing() {
        val s = MoodInsights.summaryForRange(longHistory(), "2026-03-01", "2026-03-28")
        assertEquals(0, s.count)
        assertEquals(0.0, s.average)
    }

    @Test
    fun currentCyclePointsAreWithinTheCycle() {
        val d = data(MoodLog("2026-01-14", 5), MoodLog("2026-01-22", 2))
        val pts = MoodInsights.currentCyclePoints(d, LocalDate(2026, 1, 25))
        assertEquals(2, pts.size)
        assertTrue(pts.all { it.cycleDay in 1..28 })
    }
}
