package app.cycluna.android.core

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.util.AtomicFile
import app.cycluna.core.CycleData
import app.cycluna.core.CyclePersistence
import app.cycluna.core.CyclunaCore
import app.cycluna.core.DailyMood
import app.cycluna.core.HeadacheInsight
import app.cycluna.core.HeadacheInsights
import app.cycluna.core.HeadacheLog
import app.cycluna.core.JournalEntry
import app.cycluna.core.MoodInsight
import app.cycluna.core.MoodInsights
import app.cycluna.core.MoodLog
import app.cycluna.core.MoodPoint
import app.cycluna.core.MoodSummary
import app.cycluna.core.MoonMood
import app.cycluna.core.MoonMoodInsight
import app.cycluna.core.MoonMoodInsights
import app.cycluna.core.MoonMoodPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Compose-facing adapter over the KMP `CycleData`. The shared core owns the state model, the
 * persistence/export FORMATS, and the period-logging RULES — this class only holds the
 * observable copy and does the platform-specific bits: debounced off-main-thread file
 * writes, and the export file for the share sheet.
 *
 * Everything lives on-device: no accounts, no server. This is the deliberate twin of
 * `CycleStore.swift`; the two are meant to stay diffable, so resist "improving" one alone.
 *
 * Owned by the Application, not by a ViewModel: the alarm receiver and the process-lifecycle
 * observer both need it, and neither has a ViewModelStore.
 */
@Stable
class CycleStore(context: Context) {

    /**
     * Canonical state, straight from the shared core.
     *
     * Mutated only through [apply], never assigned directly: a blanket "something changed"
     * signal cannot tell what changed, so every edit rebuilt every aggregate. Renaming
     * yourself recomputed a year of cycle and moon analysis on each keystroke.
     */
    private var data by mutableStateOf(CycleData.EMPTY)

    private val file = File(context.filesDir, "cycle-store.json")
    private val exportDir = File(context.cacheDir, "exports")

    /** Which families of derived value an edit invalidates. */
    @JvmInline
    value class Affects(val bits: Int) {
        /** True when this shares any bit with [other] — the "is not disjoint" test. */
        infix fun touches(other: Affects): Boolean = bits and other.bits != 0
        operator fun plus(other: Affects): Affects = Affects(bits or other.bits)

        companion object {
            /** Period starts, cycle length, period length — everything dated from a cycle. */
            val CYCLE = Affects(1 shl 0)
            val MOODS = Affects(1 shl 1)
            val HEADACHES = Affects(1 shl 2)
            val JOURNAL = Affects(1 shl 3)

            /** Nothing derived depends on it (the display name). */
            val NOTHING = Affects(0)
            val EVERYTHING = CYCLE + MOODS + HEADACHES + JOURNAL
        }
    }

    /**
     * The single place `data` changes: mutate, persist, then recompute only what the edit
     * can actually have invalidated.
     */
    private fun apply(affects: Affects, transform: (CycleData) -> CycleData) {
        data = transform(data)
        save()
        refresh(affects)
    }

    // ---------------------------------------------------------------------------------
    // Derived aggregates (computed once per data change, not per recomposition)
    //
    // These each walk the whole log history, parsing a date per entry. Left as plain `get()`
    // properties they would run on every recomposition of every composable that read them.
    // `apply` is the single choke point through which state changes, so `refresh` is the
    // only place they need invalidating — do not add a cache here without wiring it there.
    // ---------------------------------------------------------------------------------

    var moodCyclePoints by mutableStateOf<List<MoodPoint>>(emptyList())
        private set
    var moodInsight by mutableStateOf<MoodInsight?>(null)
        private set
    var headacheInsight by mutableStateOf<HeadacheInsight?>(null)
        private set
    var moonMoodPoints by mutableStateOf<List<MoonMoodPoint>>(emptyList())
        private set
    var moonMoodAverages by mutableStateOf<List<MoonMood>>(emptyList())
        private set
    var moonMoodInsight by mutableStateOf<MoonMoodInsight?>(null)
        private set

    /** Whether there's enough spread to say anything at all — including "steady". */
    var moonMoodReady by mutableStateOf(false)
        private set
    var cycleMoonAligned by mutableStateOf(false)
        private set

    /**
     * Recompute only the families an edit can have invalidated.
     *
     * The dependencies are narrow and worth stating: mood and moon analysis read the logs
     * *and* the cycle they're positioned against, so either invalidates them. Cycle/moon
     * alignment reads period starts alone. The day index is a lookup over the logs and never
     * touches cycle maths.
     */
    private fun refresh(affects: Affects) {
        if (affects touches (Affects.MOODS + Affects.CYCLE)) {
            moodCyclePoints = MoodInsights.currentCyclePoints(data)
            moodInsight = MoodInsights.insight(data)
            moonMoodPoints = MoonMoodInsights.moonPoints(data)
            moonMoodAverages = MoonMoodInsights.moonAverages(data)
            moonMoodInsight = MoonMoodInsights.moonInsight(data)
            moonMoodReady = MoonMoodInsights.hasEnoughForClaim(data)
            rebuildMoodPages()
        }
        if (affects touches (Affects.HEADACHES + Affects.CYCLE)) {
            headacheInsight = HeadacheInsights.insight(data)
        }
        if (affects touches Affects.CYCLE) {
            cycleMoonAligned = MoonMoodInsights.cycleMoonAligned(data)
        }
        if (affects touches (Affects.MOODS + Affects.HEADACHES + Affects.JOURNAL)) {
            rebuildDayIndex()
        }
    }

    // ---------------------------------------------------------------------------------
    // Mood patterns: lenses and pageable history
    //
    // Each lens pages by a different unit — days, cycles, lunations — so pages are rebuilt
    // whenever the lens or the data changes, never per recomposition. Page state lives here
    // rather than in the composable precisely so it shares that one invalidation point.
    // ---------------------------------------------------------------------------------

    enum class MoodLens { DAILY, PHASE, MOON }

    private var lens by mutableStateOf(MoodLens.PHASE)

    var moodLens: MoodLens
        get() = lens
        set(value) {
            if (value != lens) {
                lens = value
                rebuildMoodPages()
            }
        }

    /** Index into [moodPages]; the last page is the present. */
    var moodPageIndex by mutableIntStateOf(0)

    var moodPages by mutableStateOf<List<MoodPage>>(emptyList())
        private set

    val isOnCurrentMoodPage: Boolean get() = moodPageIndex >= moodPages.size - 1

    /**
     * Deliberately NOT a `data class`.
     *
     * A generated `equals` over the page's identity would report "unchanged" while the logs
     * inside it change — a page keeps its id (the span's start date) for its whole life.
     * Compose skips recomposition when inputs compare equal, so the chart would keep drawing
     * yesterday's points until the composable was rebuilt from scratch. The iOS twin carries
     * the same warning about Equatable.
     */
    class MoodPage(
        val startIso: String,
        val endIso: String,          // exclusive
        val title: String,
        val spanDays: Int,
        val daily: List<DailyMood> = emptyList(),
        val cycle: List<MoodPoint> = emptyList(),
        val moon: List<MoonMoodPoint> = emptyList(),
        val moonAverages: List<MoonMood> = emptyList(),
        val insight: MoodInsight? = null,
        val summary: MoodSummary = MoodSummary(0, 0.0),
    )

    private fun rebuildMoodPages() {
        val pages = when (lens) {
            MoodLens.DAILY -> {
                val today = LocalDate.now()
                (DAILY_PAGE_COUNT - 1 downTo 0).map { back ->
                    val end = today.minusDays((back.toLong()) * DAILY_PAGE_DAYS)
                    val start = end.minusDays(DAILY_PAGE_DAYS - 1L)
                    val s = start.toString()
                    val e = end.toString()
                    MoodPage(
                        startIso = s,
                        endIso = end.plusDays(1).toString(),
                        title = "${pretty(start)} – ${pretty(end)}",
                        spanDays = DAILY_PAGE_DAYS.toInt(),
                        daily = MoodInsights.moodsInRange(data, s, e),
                        summary = MoodInsights.summaryForRange(data, s, e),
                        insight = MoodInsights.insightForRange(data, s, e),
                    )
                }
            }

            // One core call builds every page in a single pass over the logs. Asking for
            // points/summary/insight per cycle re-parsed every logged date once per call —
            // ~14,000 date parses to draw one screen after a year of daily logging.
            MoodLens.PHASE -> MoodInsights.cyclePages(data).takeLast(PHASE_PAGE_COUNT).map { cycle ->
                MoodPage(
                    startIso = cycle.startIso,
                    endIso = cycle.endIso,
                    title = "Cycle of ${pretty(cycle.startIso)}",
                    spanDays = cycle.length,
                    cycle = cycle.points,
                    summary = cycle.summary,
                    insight = cycle.insight,
                )
            }

            MoodLens.MOON -> MoonMoodInsights.lunationPages(data, LUNATION_PAGE_COUNT).map { lunation ->
                MoodPage(
                    startIso = lunation.startIso,
                    endIso = lunation.endIso,
                    title = "Moon from ${pretty(lunation.startIso)}",
                    spanDays = lunation.length,
                    moon = lunation.points,
                    moonAverages = lunation.averages,
                    summary = lunation.summary,
                )
            }
        }

        moodPages = pages
        // Land on the present, and never leave the index dangling past a shorter list.
        moodPageIndex = maxOf(0, pages.size - 1)
    }

    /** The eight moon buckets in synodic order — stable slugs from the core. */
    val moonBucketOrder: List<String> = CyclunaCore.moonBucketOrder()

    fun moonBucketIllumination(key: String): Double = CyclunaCore.moonBucketIllumination(key)
    fun moonBucketIsWaxing(key: String): Boolean = CyclunaCore.moonBucketIsWaxing(key)

    // ---------------------------------------------------------------------------------
    // First-run gate
    // ---------------------------------------------------------------------------------

    /** True once the user has logged a real period. Until then the app shows only Welcome. */
    val hasLoggedPeriod: Boolean get() = data.hasLoggedPeriod

    // ---------------------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------------------

    var cycleLengthSetting: Int
        get() = data.cycleLengthSetting
        set(value) = apply(Affects.CYCLE) { it.withCycleLength(value) }

    var periodLength: Int
        get() = data.periodLength
        set(value) = apply(Affects.CYCLE) { it.withPeriodLength(value) }

    var displayName: String
        get() = data.displayName
        set(value) = apply(Affects.NOTHING) { it.withDisplayName(value) }

    // ---------------------------------------------------------------------------------
    // History & anchor
    // ---------------------------------------------------------------------------------

    /** Logged period start dates (for the calendar). */
    val periodStarts: List<LocalDate> get() = data.periodStarts.map { parseIso(it) }

    /** Most recent period start (the current cycle anchor). Setting it edits the latest entry. */
    var lastPeriodStart: LocalDate
        get() = data.lastPeriodStartIso?.let { parseIso(it) } ?: LocalDate.now()
        set(value) = apply(Affects.CYCLE) { it.withLastPeriodStart(value.toString()) }

    /** Log a new period start — appends to history via the shared rule (dedup + sorted). */
    fun startPeriod(on: LocalDate = LocalDate.now()) {
        apply(Affects.CYCLE) { it.logPeriod(on.toString()) }
    }

    /**
     * Remove one logged period start — the undo for an accidental log or a backfill
     * mistake. Removing the only one returns the app to onboarding.
     */
    fun removePeriodStart(on: LocalDate) {
        apply(Affects.CYCLE) { it.removePeriod(on.toString()) }
    }

    /**
     * Restores a data-portability export, REPLACING everything on this device. Returns
     * false when the file is not a Cycluna export; nothing changes in that case.
     */
    fun importData(text: String): Boolean {
        val imported = CyclePersistence.importJson(text) ?: return false
        apply(Affects.EVERYTHING) { imported }
        return true
    }

    /**
     * One-line what-if for a candidate anchor date — "Day 5 · Menstrual · next period
     * around Sep 3" — so the date sheet can show what a choice means before it's saved.
     * Pure preview: nothing is stored.
     */
    fun previewLine(anchor: LocalDate): String {
        val a = anchor.toString()
        val day = CyclunaCore.cycleDay(a, cycleLength, periodLength)
        val phase = CyclunaCore.cyclePhaseLabel(a, cycleLength, periodLength)
        val next = LocalDate.parse(CyclunaCore.nextPeriodIso(a, cycleLength))
        return "Day $day · $phase · next period around ${next.format(MONTH_DAY)}"
    }

    /**
     * Finish onboarding: set the chosen lengths and log the user's ACTUAL selected
     * last-period date as truth (an explicitly logged period → shown solid on the calendar).
     * An old date is handled at read time by the core, which rolls the anchor into the
     * current cycle for "today" values without fabricating a stored date.
     */
    fun completeOnboarding(lastPeriod: LocalDate, cycleLength: Int, periodLength: Int) {
        apply(Affects.CYCLE) {
            it.withCycleLength(cycleLength)
                .withPeriodLength(periodLength)
                .logPeriod(lastPeriod.toString())
        }
    }

    // ---------------------------------------------------------------------------------
    // Bridging helpers
    // ---------------------------------------------------------------------------------

    private val startIso: String get() = data.lastPeriodStartIso ?: LocalDate.now().toString()
    private val periodsCsv: String get() = data.periodStartsCsv

    private fun parseIso(s: String): LocalDate =
        runCatching { LocalDate.parse(s) }.getOrDefault(LocalDate.now())

    private fun daysBetween(from: LocalDate, to: LocalDate): Int =
        ChronoUnit.DAYS.between(from, to).toInt()

    private fun pretty(date: LocalDate): String = date.format(DAY_MONTH)
    private fun pretty(iso: String): String = pretty(parseIso(iso))

    /**
     * The start of the cycle the user is actually in — the period they logged.
     *
     * Deliberately NOT rolled forward. `Cycle.status()` stopped rolling when the late model
     * landed; leaving this rolling meant a screen could date its content from a cycle that
     * never began while the "NOW" badge beside it came from the real one.
     */
    val currentCycleStart: LocalDate
        get() = data.lastPeriodStartIso?.let { parseIso(it) } ?: LocalDate.now()

    /** Effective cycle length — recomputed from recent history when available. */
    val cycleLength: Int
        get() = CyclunaCore.predictedCycleLength(periodsCsv, cycleLengthSetting)

    // ---------------------------------------------------------------------------------
    // Derived cycle values
    // ---------------------------------------------------------------------------------

    val phaseLabel: String get() = CyclunaCore.cyclePhaseLabel(startIso, cycleLength, periodLength)
    val phaseEmoji: String get() = CyclunaCore.cyclePhaseEmoji(startIso, cycleLength, periodLength)
    val cycleDay: Int get() = CyclunaCore.cycleDay(startIso, cycleLength, periodLength)
    val daysUntilNextPeriod: Int
        get() = CyclunaCore.daysUntilNextPeriod(startIso, cycleLength, periodLength)

    fun linearCycleDay(dateIso: String): Int =
        CyclunaCore.historyCycleDay(periodsCsv, cycleLength, dateIso)

    fun dayMarker(dateIso: String): String =
        CyclunaCore.dayMarker(periodsCsv, cycleLength, periodLength, dateIso)

    fun phaseForDate(dateIso: String): String =
        CyclunaCore.historyPhaseLabel(periodsCsv, cycleLength, periodLength, dateIso)

    /**
     * On-device principal moon phase for a date: "", "new", "first-quarter", "full",
     * "last-quarter", "blue-moon", "black-moon". Eclipses come from the event catalogue.
     */
    fun moonPhaseMarker(dateIso: String): String = CyclunaCore.moonPhaseMarker(dateIso)

    // ---------------------------------------------------------------------------------
    // Dates & fertility
    // ---------------------------------------------------------------------------------

    val todayLong: String get() = LocalDate.now().format(LONG_DATE)

    val fertileStartDate: LocalDate
        get() = parseIso(CyclunaCore.fertileStartIso(startIso, cycleLength))
    val fertileEndDate: LocalDate
        get() = parseIso(CyclunaCore.fertileEndIso(startIso, cycleLength))
    val nextPeriodDate: LocalDate
        get() = parseIso(CyclunaCore.nextPeriodIso(startIso, cycleLength))

    val fertileWindowText: String
        get() {
            val s = fertileStartDate
            val e = fertileEndDate
            // Compact so it fits the tile: "Aug 12–16" within a month, else "Aug 30 – Sep 3".
            return if (s.month == e.month && s.year == e.year) {
                "${s.format(MONTH_DAY)}–${e.dayOfMonth}"
            } else {
                "${s.format(MONTH_DAY)} – ${e.format(MONTH_DAY)}"
            }
        }

    // ---------------------------------------------------------------------------------
    // Late / missed periods
    // ---------------------------------------------------------------------------------

    /** How far the core trusts its own prediction (see `CycleTracking` in the shared core). */
    val tracking: TrackingState
        get() = TrackingState.from(CyclunaCore.trackingState(startIso, cycleLength, periodLength))

    /** Days past the predicted start with nothing logged; 0 when not overdue. */
    val daysLate: Int get() = CyclunaCore.daysLate(startIso, cycleLength, periodLength)

    /**
     * Fertile window of the SINGLE cycle starting on [start] — un-rolled, straight from the
     * core, so future windows sit on exactly the same offsets as the hero tile and calendar.
     * A native re-derivation drifted by a day on odd lengths.
     */
    fun fertileWindowForCycleStarting(start: LocalDate): Pair<LocalDate, LocalDate> {
        val s = start.toString()
        return parseIso(CyclunaCore.fertileStartForCycleIso(s, cycleLength)) to
            parseIso(CyclunaCore.fertileEndForCycleIso(s, cycleLength))
    }

    val nextPeriodShort: String
        get() = CycleCopy.nextPeriodShort(tracking, daysLate, daysUntilNextPeriod)

    /**
     * Fertile-window predictions are only meaningful while the cycle is on track. Once a
     * period is overdue, ovulation has either not happened on schedule or happened late —
     * either way the next window can't be dated until the period actually starts.
     */
    val showsFertileWindow: Boolean get() = tracking == TrackingState.NORMAL

    /** Today falls inside the logged period — the cycle is normal and still in its first days. */
    val isInLoggedPeriod: Boolean get() = tracking == TrackingState.NORMAL && cycleDay <= periodLength

    /** "Period started 10 Aug" — shown in place of the start button while the period is on. */
    val periodStartedText: String get() = "Period started ${lastPeriodStart.format(DAY_MONTH)}"

    val fertileContext: String
        get() = CycleCopy.fertileContext(tracking, daysLate)

    // ---------------------------------------------------------------------------------
    // Moon
    // ---------------------------------------------------------------------------------

    val moonSymbol: String get() = CyclunaCore.todayMoonSymbol()
    val moonLabel: String get() = CyclunaCore.todayMoonLabel()
    val moonIllumination: Int get() = CyclunaCore.todayMoonIlluminationPercent()

    fun moonIllumination(onIso: String): Double = CyclunaCore.moonIlluminationForDate(onIso)
    fun moonWaxing(onIso: String): Boolean = CyclunaCore.moonIsWaxingForDate(onIso)
    fun moonLabel(onIso: String): String = CyclunaCore.moonLabelForDate(onIso)

    val lastPeriodMoonLabel: String
        get() = data.lastPeriodStartIso?.let { CyclunaCore.moonLabelForDate(it) } ?: ""

    val daysUntilNextFullMoon: Int
        get() = CyclunaCore.daysUntilNextFullMoon(LocalDate.now().toString())
    val nextFullMoonDate: LocalDate
        get() = parseIso(CyclunaCore.nextFullMoonIso(LocalDate.now().toString()))
    val phaseAtNextFullMoon: String
        get() = CyclunaCore.phaseLabelForDate(
            startIso, cycleLength, periodLength,
            CyclunaCore.nextFullMoonIso(LocalDate.now().toString()),
        )

    // ---------------------------------------------------------------------------------
    // Persistence (on-device only)
    //
    // The FORMAT lives in the shared core (`CyclePersistence`). This layer only decides WHEN
    // to write. Internal storage is encrypted at rest by file-based encryption, which is the
    // platform's answer to iOS complete file protection.
    // ---------------------------------------------------------------------------------

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSave: Job? = null

    private fun load(): CycleData? = runCatching {
        CyclePersistence.decode(String(AtomicFile(file).readFully(), Charsets.UTF_8))
    }.getOrNull()

    /**
     * Debounced save. Edits that fire in bursts — a name field being typed into, a stepper
     * held down — collapse into a SINGLE disk write ~0.4s after they settle.
     *
     * The encode happens here, on the calling thread, and only the write is delayed. Encoding
     * inside the coroutine would serialise whatever `data` happened to be 400ms later, which
     * silently reorders edits and can resurrect data that a delete has just cleared.
     */
    private fun save() {
        pendingSave?.cancel()
        val text = CyclePersistence.encode(data)
        pendingSave = io.launch {
            delay(SAVE_DEBOUNCE_MS)
            write(text)
        }
    }

    /**
     * Force any pending change to disk immediately. Called when the app leaves the
     * foreground, so a debounced write is never lost to the process being killed.
     * Synchronous by design — the file is a few kilobytes.
     */
    fun flush() {
        pendingSave?.cancel()
        pendingSave = null
        write(CyclePersistence.encode(data))
    }

    private fun write(text: String) {
        val atomic = AtomicFile(file)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: java.io.IOException) {
            stream?.let { atomic.failWrite(it) }
        }
    }

    // ---------------------------------------------------------------------------------
    // Data portability & deletion (on-device, user-initiated)
    // ---------------------------------------------------------------------------------

    /**
     * Writes the shared-core export (`cycluna.export.v1`) to a cache file for the share
     * sheet and returns it. The data stays on-device until the user picks a destination —
     * that is the one moment it can leave the phone.
     */
    fun exportFile(): File? {
        val text = CyclePersistence.exportJson(data, Instant.now().toString())
        return runCatching {
            // Clear previous exports rather than accumulating health data in the cache.
            exportDir.listFiles()?.forEach { it.delete() }
            exportDir.mkdirs()
            File(exportDir, "cycluna-export-${LocalDate.now()}.json").apply {
                writeText(text)
            }
        }.getOrNull()
    }

    /**
     * Permanently erases all stored data and returns the app to a fresh first-run state,
     * which drops the UI back to onboarding — no fabricated cycle.
     */
    fun deleteAllData() {
        // Not routed through `apply`: that would schedule a debounced save, and "delete"
        // must truly leave no file behind until the user next changes something.
        pendingSave?.cancel()
        pendingSave = null
        AtomicFile(file).delete()
        exportDir.listFiles()?.forEach { it.delete() }
        data = CycleData.EMPTY
        refresh(Affects.EVERYTHING)
    }

    // ---------------------------------------------------------------------------------
    // Daily logs (mood / headache / journal) — on-device only
    // ---------------------------------------------------------------------------------

    // Day lookups are indexed rather than scanned. A month grid asks about 42 days at a
    // time, several times each; scanning the whole history per question is ~250 passes over
    // every log to draw one calendar.

    var moodByDay by mutableStateOf<Map<String, MoodLog>>(emptyMap())
        private set
    var headacheDays by mutableStateOf<Set<String>>(emptySet())
        private set
    var noteDays by mutableStateOf<Set<String>>(emptySet())
        private set

    private fun rebuildDayIndex() {
        moodByDay = data.moods.associateBy { it.date }
        // Headaches are timestamped `yyyy-MM-dd'T'HH:mm`; the day is the leading 10 chars.
        headacheDays = data.headaches.map { it.at.take(10) }.toSet()
        noteDays = data.journal.map { it.date }.toSet()
    }

    fun mood(onIso: String): MoodLog? = moodByDay[onIso]
    fun headaches(onIso: String): List<HeadacheLog> = data.headachesOn(onIso)
    fun journalEntries(onIso: String): List<JournalEntry> = data.journal.filter { it.date == onIso }

    fun hasHeadache(onIso: String): Boolean = onIso in headacheDays
    fun hasNote(onIso: String): Boolean = onIso in noteDays

    /** True if anything at all is logged on the given day. */
    fun hasLog(onIso: String): Boolean =
        moodByDay.containsKey(onIso) || onIso in headacheDays || onIso in noteDays

    fun logMood(mood: Int, note: String = "", onIso: String) {
        apply(Affects.MOODS) { it.withMood(onIso, mood, note) }
    }

    fun clearMood(onIso: String) {
        apply(Affects.MOODS) { it.clearingMood(onIso) }
    }

    fun addHeadache(
        intensity: Int,
        symptoms: List<String>,
        triggers: List<String>,
        note: String,
        at: java.time.LocalDateTime,
    ) {
        apply(Affects.HEADACHES) {
            it.addingHeadache(
                HeadacheLog(UUID.randomUUID().toString(), at.format(ISO_MINUTE), intensity, symptoms, triggers, note)
            )
        }
    }

    fun updateHeadache(
        id: String,
        intensity: Int,
        symptoms: List<String>,
        triggers: List<String>,
        note: String,
        at: java.time.LocalDateTime,
    ) {
        apply(Affects.HEADACHES) {
            it.removingHeadache(id).addingHeadache(
                HeadacheLog(id, at.format(ISO_MINUTE), intensity, symptoms, triggers, note)
            )
        }
    }

    fun deleteHeadache(id: String) {
        apply(Affects.HEADACHES) { it.removingHeadache(id) }
    }

    fun addJournalEntry(text: String, onIso: String) {
        apply(Affects.JOURNAL) {
            it.addingJournal(JournalEntry(UUID.randomUUID().toString(), onIso, text))
        }
    }

    fun updateJournalEntry(id: String, text: String, onIso: String) {
        apply(Affects.JOURNAL) {
            it.removingJournal(id).addingJournal(JournalEntry(id, onIso, text))
        }
    }

    fun deleteJournalEntry(id: String) {
        apply(Affects.JOURNAL) { it.removingJournal(id) }
    }

    /**
     * Must stay the LAST thing in the class body. Kotlin runs initialisers in declaration
     * order, so `refresh` here would assign through `by mutableStateOf` delegates that have
     * not been constructed yet if this sat any higher — a null-pointer crash before the
     * first frame, not a compile error.
     */
    init {
        data = load() ?: CycleData.EMPTY
        refresh(Affects.EVERYTHING)
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 400L

        /**
         * How far back each lens offers. Cycles are bounded by real logged starts, but that
         * grows for as long as the app is used — three years of short cycles is ~50 pages.
         * Paging back that far is not a feature anyone wants, so all three are capped.
         */
        const val DAILY_PAGE_DAYS = 14L
        const val DAILY_PAGE_COUNT = 12
        const val LUNATION_PAGE_COUNT = 12
        const val PHASE_PAGE_COUNT = 24

        // English-only by decision, so the patterns are pinned to English rather than
        // following the device locale — the copy around them is English either way.
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
        val ISO_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH)
    }
}
