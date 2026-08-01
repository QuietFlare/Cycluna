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

    @Test
    fun currentCyclePointsAreWithinTheCycle() {
        val d = data(MoodLog("2026-01-14", 5), MoodLog("2026-01-22", 2))
        val pts = MoodInsights.currentCyclePoints(d, LocalDate(2026, 1, 25))
        assertEquals(2, pts.size)
        assertTrue(pts.all { it.cycleDay in 1..28 })
    }
}
