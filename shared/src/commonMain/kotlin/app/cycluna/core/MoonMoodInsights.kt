package app.cycluna.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * Average mood for one moon phase within the insight window.
 *
 * [bucketKey] is [MoonPhaseKey.slug]. Every bucket is returned, logged or not: [count] `0`
 * means "nothing logged here" and [average] is meaningless in that case — the UI must render
 * those bands as empty rather than as a mood of zero.
 */
data class MoonMood(val bucketKey: String, val average: Double, val count: Int)

/**
 * One logged mood placed on the lunar month.
 *
 * [phaseFraction] is 0.0 at the new moon through to 1.0 at the next — the moon's analogue of
 * a cycle day, so the UI can plot real logs rather than only bucket averages. Three logs
 * should look like three dots, not like a confident average.
 */
data class MoonMoodPoint(val phaseFraction: Double, val mood: Int)

/** One lunation's page, built in a single pass over the logs. [endIso] is exclusive. */
data class LunationPage(
    val startIso: String,
    val endIso: String,
    val length: Int,
    val points: List<MoonMoodPoint>,
    val averages: List<MoonMood>,
    val summary: MoodSummary,
)

/** A confident brightest/lowest moon-phase finding. The UI words it; the core never claims cause. */
data class MoonMoodInsight(
    val brightestKey: String,
    val lowestKey: String,
    val cyclesCovered: Int,
    val totalLogs: Int,
)

/**
 * The user's own logged moods grouped by the moon phase of the day they were logged.
 *
 * Purely descriptive: it reports what someone's own logs contain and nothing more. Research
 * has not established a moon–mood link, so the guardrails here are deliberately stricter than
 * the cycle insight's (three logs per bucket rather than two) — with eight buckets instead of
 * four, noise finds a "pattern" far too easily.
 */
object MoonMoodInsights {
    // Honesty guardrails.
    private const val MIN_TOTAL = 8        // total logged moods in the window
    private const val MIN_PER_BUCKET = 3   // per moon phase, to count toward a claim
    private const val MIN_BUCKETS = 2      // distinct phases meeting that bar
    private const val MIN_GAP = 0.8        // brightest − lowest, on the 1..5 scale

    /** How close two cycle starts' moon phases must be to call the rhythms phase-locked. */
    internal const val ALIGNMENT_TOLERANCE_DAYS = 4.0

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /**
     * The moon phase a date falls in.
     *
     * Reuses [Moon.phase] rather than re-deriving buckets from illumination and waxing, so a
     * bucket can never disagree with the phase label the app shows for the same date — they
     * are the same computation, not two that happen to match.
     */
    fun bucketForDate(date: LocalDate): MoonPhaseKey = Moon.phase(date).key

    /** All eight buckets in synodic order, as stable slugs. */
    fun orderedBucketKeys(): List<String> = MoonPhaseKey.entries.map { it.slug }

    /** Representative illumination (0..1) for a bucket, for drawing its disc. */
    fun bucketIllumination(bucketKey: String): Double {
        val age = centreAge(bucketKey) ?: return 0.0
        return (1 - cos((2 * PI * age) / Moon.SYNODIC_MONTH)) / 2
    }

    /** Whether a bucket sits in the waxing half of the month. */
    fun bucketIsWaxing(bucketKey: String): Boolean =
        (centreAge(bucketKey) ?: 0.0) < Moon.SYNODIC_MONTH / 2

    private fun centreAge(bucketKey: String): Double? {
        val index = MoonPhaseKey.entries.indexOfFirst { it.slug == bucketKey }
        if (index < 0) return null
        return (index / 8.0) * Moon.SYNODIC_MONTH
    }

    // --- Lunation paging ------------------------------------------------------
    //
    // The moon lens pages by LUNATION (new moon to new moon), not by cycle — the two drift
    // against each other, so "previous" has to mean something different here.

    /** The new moon that opened the lunation containing [date]. */
    internal fun lunationStart(date: LocalDate): LocalDate {
        val age = Moon.phase(date).age
        return date.minus(DatePeriod(days = age.toInt()))
    }

    /**
     * Lunation spans ending with the current one, oldest first. Unlike cycles these are pure
     * astronomy, so any number can be generated — the limit is where logs run out, not where
     * knowledge does.
     */
    fun lunationSpans(data: CycleData, count: Int): List<CycleSpan> =
        lunationSpans(count, today())

    internal fun lunationSpans(count: Int, today: LocalDate): List<CycleSpan> {
        if (count <= 0) return emptyList()
        val current = lunationStart(today)
        return (count - 1 downTo 0).map { back ->
            // Step back whole lunations, then re-snap: rounding drift would otherwise
            // accumulate over a year of paging.
            val approx = current.minus(DatePeriod(days = (Moon.SYNODIC_MONTH * back).toInt()))
            val start = lunationStart(approx)
            val end = lunationStart(start.plus(DatePeriod(days = 31)))
            CycleSpan(start.toString(), end.toString(), start.daysUntil(end))
        }
    }

    /**
     * Every lunation page in ONE pass — the moon lens's equivalent of [MoodInsights.cyclePages].
     *
     * Building them with [moonPointsInRange], [moonAveragesInRange] and a summary per span
     * re-parses every logged date and recomputes [Moon.phase] for it once per call. Twelve
     * lunations meant each log's moon phase — an instant conversion and a modulo over the
     * synodic month — computed dozens of times. This does it once per log.
     */
    fun lunationPages(data: CycleData, count: Int): List<LunationPage> =
        lunationPages(data, count, today())

    internal fun lunationPages(data: CycleData, count: Int, today: LocalDate): List<LunationPage> {
        val spans = lunationSpans(count, today)
        if (spans.isEmpty()) return emptyList()

        // date, its moon phase, and the mood — each computed exactly once.
        val parsed = data.moods.mapNotNull { m ->
            val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
            Triple(date, Moon.phase(date), m.mood)
        }

        return spans.map { span ->
            val start = LocalDate.parse(span.startIso)
            val end = LocalDate.parse(span.endIso)
            val inSpan = parsed.filter { it.first >= start && it.first < end }
            val byBucket = inSpan.groupBy({ it.second.key }, { it.third })
            val moods = inSpan.map { it.third }

            LunationPage(
                startIso = span.startIso,
                endIso = span.endIso,
                length = span.length,
                points = inSpan
                    .map { MoonMoodPoint(it.second.age / Moon.SYNODIC_MONTH, it.third) }
                    .sortedBy { it.phaseFraction },
                // All eight buckets, in synodic order — the stripe draws every band.
                averages = MoonPhaseKey.entries.map { key ->
                    val xs = byBucket[key].orEmpty()
                    MoonMood(key.slug, if (xs.isEmpty()) 0.0 else xs.average(), xs.size)
                },
                summary = MoodSummary(moods.size, if (moods.isEmpty()) 0.0 else moods.average()),
            )
        }
    }

    /** Every logged mood in a date range, placed on the lunar month. [toIso] is exclusive. */
    fun moonPointsInRange(data: CycleData, fromIso: String, toIso: String): List<MoonMoodPoint> {
        val from = runCatching { LocalDate.parse(fromIso) }.getOrNull() ?: return emptyList()
        val to = runCatching { LocalDate.parse(toIso) }.getOrNull() ?: return emptyList()
        return data.moods.mapNotNull { m ->
            val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
            if (date < from || date >= to) return@mapNotNull null
            MoonMoodPoint(Moon.phase(date).age / Moon.SYNODIC_MONTH, m.mood)
        }.sortedBy { it.phaseFraction }
    }

    /** Average mood per moon phase within a date range. [toIso] is exclusive. */
    fun moonAveragesInRange(data: CycleData, fromIso: String, toIso: String): List<MoonMood> {
        val from = runCatching { LocalDate.parse(fromIso) }.getOrNull()
        val to = runCatching { LocalDate.parse(toIso) }.getOrNull()
        val byBucket = if (from == null || to == null) emptyMap() else data.moods
            .mapNotNull { m ->
                val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
                if (date < from || date >= to) return@mapNotNull null
                bucketForDate(date) to m.mood
            }
            .groupBy({ it.first }, { it.second })
        return MoonPhaseKey.entries.map { key ->
            val moods = byBucket[key].orEmpty()
            MoonMood(key.slug, if (moods.isEmpty()) 0.0 else moods.average(), moods.size)
        }
    }

    /** Every logged mood in the window, placed on the lunar month (for the scatter plot). */
    fun moonPoints(data: CycleData): List<MoonMoodPoint> = moonPoints(data, today())

    internal fun moonPoints(data: CycleData, today: LocalDate): List<MoonMoodPoint> {
        val from = MoodInsights.windowStart(data, today) ?: return emptyList()
        return data.moods.mapNotNull { m ->
            val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
            if (date < from || date > today) return@mapNotNull null
            MoonMoodPoint(Moon.phase(date).age / Moon.SYNODIC_MONTH, m.mood)
        }.sortedBy { it.phaseFraction }
    }

    /** Average mood per moon phase over the same window the cycle insight uses. */
    fun moonAverages(data: CycleData): List<MoonMood> = moonAverages(data, today())

    internal fun moonAverages(data: CycleData, today: LocalDate): List<MoonMood> {
        val from = MoodInsights.windowStart(data, today)
        val byBucket = if (from == null) emptyMap() else data.moods
            .mapNotNull { m ->
                val date = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
                if (date < from || date > today) return@mapNotNull null
                bucketForDate(date) to m.mood
            }
            .groupBy({ it.first }, { it.second })

        // Always all eight, in synodic order — the stripe renders every band.
        return MoonPhaseKey.entries.map { key ->
            val moods = byBucket[key].orEmpty()
            MoonMood(key.slug, if (moods.isEmpty()) 0.0 else moods.average(), moods.size)
        }
    }

    /**
     * Whether the window holds enough spread to say ANYTHING about moon phases — including
     * that mood looks steady.
     *
     * "Steady across moon phases" is itself a claim about spread: asserting it from logs that
     * all sit in one bucket would be exactly the kind of unsupported statement the thresholds
     * exist to prevent. Below this bar the UI must invite more logging, not conclude.
     */
    fun hasEnoughForClaim(data: CycleData): Boolean = hasEnoughForClaim(data, today())

    internal fun hasEnoughForClaim(data: CycleData, today: LocalDate): Boolean {
        val stats = moonAverages(data, today).filter { it.count > 0 }
        if (stats.sumOf { it.count } < MIN_TOTAL) return false
        return stats.count { it.count >= MIN_PER_BUCKET } >= MIN_BUCKETS
    }

    /** A confident finding, or null when the data doesn't support naming one. */
    fun moonInsight(data: CycleData): MoonMoodInsight? = moonInsight(data, today())

    internal fun moonInsight(data: CycleData, today: LocalDate): MoonMoodInsight? {
        if (!hasEnoughForClaim(data, today)) return null
        val stats = moonAverages(data, today).filter { it.count > 0 }
        val total = stats.sumOf { it.count }
        val eligible = stats.filter { it.count >= MIN_PER_BUCKET }
        val hi = eligible.maxByOrNull { it.average } ?: return null
        val lo = eligible.minByOrNull { it.average } ?: return null
        if (hi.bucketKey == lo.bucketKey || hi.average - lo.average < MIN_GAP) return null
        return MoonMoodInsight(
            hi.bucketKey, lo.bucketKey,
            MoodInsights.cyclesCovered(data, today), total
        )
    }

    /**
     * True when the current cycle started at roughly the same moon phase as the previous one
     * — the two rhythms are momentarily in step. The UI uses this to warn that the cycle and
     * moon views will look alike right now, so neither is evidence for the other.
     */
    fun cycleMoonAligned(data: CycleData): Boolean = cycleMoonAligned(data, today())

    internal fun cycleMoonAligned(data: CycleData, today: LocalDate): Boolean {
        val starts = MoodInsights.sortedStarts(data).filter { it <= today }
        if (starts.size < 2) return false
        val current = Moon.phase(starts[starts.size - 1]).age
        val previous = Moon.phase(starts[starts.size - 2]).age
        return circularAgeGap(current, previous) <= ALIGNMENT_TOLERANCE_DAYS
    }

    /** Distance between two moon ages in days, the short way round the month. */
    private fun circularAgeGap(a: Double, b: Double): Double {
        val raw = abs(a - b)
        return min(raw, Moon.SYNODIC_MONTH - raw)
    }
}
