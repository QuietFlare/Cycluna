package app.cycluna.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoonMoodInsightsTest {

    private val today = LocalDate(2026, 8, 1)

    /** A year of starts 28 days apart, so the insight window comfortably covers 2026. */
    private val starts = generateSequence(LocalDate(2025, 9, 1)) { it.plus(DatePeriod(days = 28)) }
        .takeWhile { it <= LocalDate(2026, 7, 20) }
        .map { it.toString() }
        .toList()

    private fun data(vararg moods: MoodLog) =
        CycleData(periodStarts = starts, cycleLengthSetting = 28, periodLength = 5,
                  moods = moods.toList())

    /** The first [count] dates on/after [from] that fall in [bucket]. */
    private fun datesIn(bucket: MoonPhaseKey, from: LocalDate, count: Int): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = from
        while (out.size < count && d <= today) {
            if (MoonMoodInsights.bucketForDate(d) == bucket) out.add(d)
            d = d.plus(DatePeriod(days = 1))
        }
        check(out.size == count) { "only found ${out.size} $bucket dates" }
        return out
    }

    private fun logs(bucket: MoonPhaseKey, from: LocalDate, count: Int, mood: Int) =
        datesIn(bucket, from, count).map { MoodLog(it.toString(), mood) }

    // --- Bucketing ------------------------------------------------------------

    @Test
    fun bucketAlwaysAgreesWithTheLabelTheAppShowsForThatDate() {
        // The contract that matters: a band can never claim a phase the rest of the app
        // disagrees with. Checked across a full lunation plus change.
        var d = LocalDate(2026, 6, 1)
        repeat(40) {
            val iso = d.toString()
            val slug = CyclunaCore.moonBucketForDate(iso)
            val fromSlug = MoonPhaseKey.entries.first { it.slug == slug }
            assertEquals(CyclunaCore.moonLabelForDate(iso), fromSlug.label, "on $iso")
            d = d.plus(DatePeriod(days = 1))
        }
    }

    @Test
    fun everyBucketIsReturnedInSynodicOrder() {
        val keys = MoonMoodInsights.orderedBucketKeys()
        assertEquals(8, keys.size)
        assertEquals("new", keys.first())
        assertEquals("full", keys[4])
        assertEquals(keys, MoonMoodInsights.moonAverages(data(), today).map { it.bucketKey })
    }

    @Test
    fun bucketsWithNoLogsReportZeroCountRatherThanAMoodOfZero() {
        val averages = MoonMoodInsights.moonAverages(data(), today)
        assertTrue(averages.all { it.count == 0 })
        // The UI keys off count; average is meaningless here and must not be rendered.
        assertTrue(averages.all { it.average == 0.0 })
    }

    @Test
    fun illuminationAndWaxingMatchThePhaseTheBucketNames() {
        assertTrue(MoonMoodInsights.bucketIllumination("new") < 0.05)
        assertTrue(MoonMoodInsights.bucketIllumination("full") > 0.95)
        assertTrue(MoonMoodInsights.bucketIsWaxing("waxing-gibbous"))
        assertFalse(MoonMoodInsights.bucketIsWaxing("waning-crescent"))
    }

    // --- Aggregation thresholds ----------------------------------------------

    @Test
    fun noInsightBelowTheLogThreshold() {
        val d = data(*(logs(MoonPhaseKey.FULL, LocalDate(2026, 2, 1), 3, 5) +
                       logs(MoonPhaseKey.NEW, LocalDate(2026, 2, 1), 3, 1)).toTypedArray())
        // 6 logs — under MIN_TOTAL of 8, however clean the split looks.
        assertNull(MoonMoodInsights.moonInsight(d, today))
    }

    @Test
    fun noInsightWhenABucketIsUnderRepresented() {
        // 9 logs, but only one bucket reaches three: not enough to compare.
        val d = data(*(logs(MoonPhaseKey.FULL, LocalDate(2026, 2, 1), 7, 5) +
                       logs(MoonPhaseKey.NEW, LocalDate(2026, 2, 1), 2, 1)).toTypedArray())
        assertNull(MoonMoodInsights.moonInsight(d, today))
    }

    @Test
    fun noInsightWhenMoodIsSteadyAcrossPhases() {
        // Plenty of data, no real gap — must not manufacture a lunar pattern.
        val d = data(*(logs(MoonPhaseKey.FULL, LocalDate(2026, 2, 1), 5, 3) +
                       logs(MoonPhaseKey.NEW, LocalDate(2026, 2, 1), 5, 3)).toTypedArray())
        assertNull(MoonMoodInsights.moonInsight(d, today))
    }

    @Test
    fun namesBrightestAndLowestWhenTheGapIsReal() {
        val d = data(*(logs(MoonPhaseKey.FULL, LocalDate(2026, 2, 1), 5, 5) +
                       logs(MoonPhaseKey.NEW, LocalDate(2026, 2, 1), 5, 2)).toTypedArray())
        val ins = assertNotNull(MoonMoodInsights.moonInsight(d, today))
        assertEquals("full", ins.brightestKey)
        assertEquals("new", ins.lowestKey)
        assertEquals(10, ins.totalLogs)
        assertTrue(ins.cyclesCovered > 0)
    }

    @Test
    fun everyWindowedLogBecomesAPointOnTheLunarMonth() {
        val full = logs(MoonPhaseKey.FULL, LocalDate(2026, 2, 1), 3, 5)
        val new = logs(MoonPhaseKey.NEW, LocalDate(2026, 2, 1), 2, 2)
        val pts = MoonMoodInsights.moonPoints(data(*(full + new).toTypedArray()), today)

        assertEquals(5, pts.size, "one point per logged mood, not one per bucket")
        assertTrue(pts.all { it.phaseFraction in 0.0..1.0 })
        assertEquals(pts.sortedBy { it.phaseFraction }, pts, "points arrive in lunar order")
        // New moon sits at the ends of the month, full moon near the middle.
        assertTrue(pts.filter { it.mood == 5 }.all { it.phaseFraction in 0.4..0.6 })
        assertTrue(pts.filter { it.mood == 2 }.all { it.phaseFraction < 0.1 || it.phaseFraction > 0.9 })
    }

    @Test
    fun pointsRespectTheWindowLikeTheAverages() {
        val old = logs(MoonPhaseKey.FULL, LocalDate(2024, 1, 1), 3, 5)
        val d = CycleData(periodStarts = starts, cycleLengthSetting = 28, periodLength = 5,
                          moods = old)
        assertTrue(MoonMoodInsights.moonPoints(d, today).isEmpty())
    }

    @Test
    fun logsCrowdedIntoOneBucketCannotSupportEvenASteadyClaim() {
        // The reported bug: three logs on consecutive days all land in one moon phase, and
        // the card announced "steady across moon phases" — a claim about spread, made from
        // data with none.
        val d = data(*logs(MoonPhaseKey.WANING_GIBBOUS, LocalDate(2026, 2, 1), 3, 4).toTypedArray())
        assertFalse(MoonMoodInsights.hasEnoughForClaim(d, today))
        assertNull(MoonMoodInsights.moonInsight(d, today))
    }

    @Test
    fun steadyMoodAcrossTwoBucketsIsEnoughToSaySteady() {
        val d = data(*(logs(MoonPhaseKey.FULL, LocalDate(2026, 2, 1), 5, 3) +
                       logs(MoonPhaseKey.NEW, LocalDate(2026, 2, 1), 5, 3)).toTypedArray())
        assertTrue(MoonMoodInsights.hasEnoughForClaim(d, today))
        // Enough to judge, but no real gap → still no brightest/lowest claim.
        assertNull(MoonMoodInsights.moonInsight(d, today))
    }

    @Test
    fun moodsOutsideTheWindowAreExcludedHereToo() {
        // Same window as the cycle insight: logs from well before it must not count.
        val old = logs(MoonPhaseKey.FULL, LocalDate(2024, 1, 1), 5, 5)
        val d = CycleData(periodStarts = starts, cycleLengthSetting = 28, periodLength = 5,
                          moods = old)
        assertTrue(MoonMoodInsights.moonAverages(d, today).all { it.count == 0 })
    }

    // --- Lunation paging ------------------------------------------------------

    @Test
    fun lunationSpansAreConsecutiveAndAboutAMonthLong() {
        val spans = MoonMoodInsights.lunationSpans(6, today)
        assertEquals(6, spans.size)
        spans.forEach { assertTrue(it.length in 28..31, "lunation was ${it.length} days") }
        // Each ends exactly where the next begins — no gaps, no overlap to double-count logs.
        spans.zipWithNext().forEach { (a, b) -> assertEquals(a.endIso, b.startIso) }
    }

    @Test
    fun theLastLunationContainsToday() {
        val current = MoonMoodInsights.lunationSpans(3, today).last()
        assertTrue(LocalDate.parse(current.startIso) <= today)
        assertTrue(LocalDate.parse(current.endIso) > today)
    }

    @Test
    fun steppingBackAYearDoesNotAccumulateDrift() {
        // Re-snapping each span to a real new moon keeps them lunations, not 29-day blocks.
        MoonMoodInsights.lunationSpans(13, today).forEach {
            assertTrue(it.length in 28..31, "drifted to ${it.length} days at ${it.startIso}")
        }
    }

    @Test
    fun rangeAggregationCountsOnlyThatLunationsLogs() {
        val spans = MoonMoodInsights.lunationSpans(3, today)
        val target = spans[1]
        val all = data(*(logs(MoonPhaseKey.FULL, LocalDate(2026, 2, 1), 5, 5)).toTypedArray())

        val inRange = MoonMoodInsights.moonPointsInRange(all, target.startIso, target.endIso)
        val everywhere = MoonMoodInsights.moonPoints(all, today)
        assertTrue(inRange.size <= everywhere.size)
        // A lunation holds at most one full moon, so at most one full-moon log per span.
        val averages = MoonMoodInsights.moonAveragesInRange(all, target.startIso, target.endIso)
        assertEquals(8, averages.size)
        assertTrue(averages.sumOf { it.count } == inRange.size)
    }

    @Test
    fun onePassLunationPagesAgreeWithThePerCallPath() {
        // lunationPages exists purely for speed; it must produce exactly what the separate
        // range calls did, or the moon lens quietly changes meaning.
        val d = data(*(logs(MoonPhaseKey.FULL, LocalDate(2026, 2, 1), 5, 5) +
                       logs(MoonPhaseKey.NEW, LocalDate(2026, 2, 1), 4, 2) +
                       logs(MoonPhaseKey.WANING_GIBBOUS, LocalDate(2026, 3, 1), 3, 3)).toTypedArray())

        val pages = MoonMoodInsights.lunationPages(d, 12, today)
        val spans = MoonMoodInsights.lunationSpans(12, today)
        assertEquals(spans.size, pages.size)

        for ((page, span) in pages.zip(spans)) {
            assertEquals(span.startIso, page.startIso)
            assertEquals(span.endIso, page.endIso)
            assertEquals(span.length, page.length)
            assertEquals(
                MoonMoodInsights.moonPointsInRange(d, span.startIso, span.endIso), page.points,
                "points differ for ${span.startIso}"
            )
            assertEquals(
                MoonMoodInsights.moonAveragesInRange(d, span.startIso, span.endIso), page.averages,
                "averages differ for ${span.startIso}"
            )
            assertEquals(
                page.points.size, page.summary.count,
                "summary count must match the points on the page"
            )
        }
    }

    // --- Cycle/moon alignment -------------------------------------------------

    @Test
    fun alignedWhenConsecutiveCyclesStartAtTheSameMoonPhase() {
        // A 29-day cycle tracks the synodic month almost exactly, so consecutive starts
        // land on nearly the same phase.
        val a = LocalDate(2026, 5, 1)
        val b = a.plus(DatePeriod(days = 29))
        val d = CycleData(periodStarts = listOf(a.toString(), b.toString()),
                          cycleLengthSetting = 29, periodLength = 5)
        assertTrue(MoonMoodInsights.cycleMoonAligned(d, today))
    }

    @Test
    fun notAlignedWhenTheCycleDriftsAgainstTheMoon() {
        // ~15 days is half a lunation out of step — the opposite phase.
        val a = LocalDate(2026, 5, 1)
        val b = a.plus(DatePeriod(days = 44)) // 29 + 15
        val d = CycleData(periodStarts = listOf(a.toString(), b.toString()),
                          cycleLengthSetting = 28, periodLength = 5)
        assertFalse(MoonMoodInsights.cycleMoonAligned(d, today))
    }

    @Test
    fun notAlignedWithoutTwoCyclesToCompare() {
        val d = CycleData(periodStarts = listOf("2026-05-01"), cycleLengthSetting = 28,
                          periodLength = 5)
        assertFalse(MoonMoodInsights.cycleMoonAligned(d, today))
        assertFalse(MoonMoodInsights.cycleMoonAligned(CycleData(), today))
    }
}
