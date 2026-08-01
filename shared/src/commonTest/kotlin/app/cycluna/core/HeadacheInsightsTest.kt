package app.cycluna.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class HeadacheInsightsTest {

    // anchor 2026-01-01, 28-day cycle: day 22 = luteal, day 2 = menstrual, day 14 = ovulatory.
    private fun data(vararg dates: String) = CycleData(
        periodStarts = listOf("2026-01-01"), cycleLengthSetting = 28, periodLength = 5,
        headaches = dates.mapIndexed { i, day -> HeadacheLog("h$i", "${day}T10:00", 2) },
    )

    @Test
    fun clustersInDominantPhase() {
        // 5 headaches, 4 in luteal (day 22 across cycles) → luteal dominates.
        val d = data("2026-01-22", "2026-02-19", "2026-03-19", "2026-04-16", "2026-01-14")
        val ins = HeadacheInsights.insight(d, LocalDate(2026, 4, 20))
        assertNotNull(ins)
        assertEquals(Phase.LUTEAL, ins!!.phase)
    }

    @Test
    fun noInsightWithTooFew() {
        assertNull(HeadacheInsights.insight(data("2026-01-22", "2026-02-19"), LocalDate(2026, 3, 1)))
    }

    @Test
    fun noInsightWhenSpreadEvenly() {
        // 6 headaches, no phase over 40%: menstrual×2, follicular×2, ovulatory×1, luteal×1.
        val d = data("2026-01-02", "2026-01-30", "2026-01-08", "2026-02-05", "2026-01-14", "2026-01-22")
        assertNull(HeadacheInsights.insight(d, LocalDate(2026, 2, 10)))
    }
}
