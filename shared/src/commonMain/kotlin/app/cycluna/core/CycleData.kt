package app.cycluna.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The canonical, persisted app state — the single source of truth shared by iOS and
 * Android. Only the user's real inputs live here; everything else (phase, moon, fertile
 * window) is DERIVED from these via [Cycle]/[Moon]/[CyclunaCore] and never stored.
 *
 * Period starts are ISO `yyyy-MM-dd` strings, kept sorted (they sort chronologically as
 * plain text). `displayName` defaults to empty — the friendly greeting fallback is UI
 * copy and lives in the native layer, not here.
 */
/** A day's mood (1..5) with an optional note. One per date (latest wins). */
@Serializable
data class MoodLog(val date: String, val mood: Int, val note: String = "")

/**
 * A headache / migraine episode. Multiple per day are supported (migraines recur), so each
 * has its own [id] and [at] timestamp. [symptoms]/[triggers] are optional tag lists (e.g.
 * "One-sided", "Nausea"; "Stress", "Hormones"). Defaults keep old single-per-day files
 * decodable. [date] is the day part, for grouping.
 */
@Serializable
data class HeadacheLog(
    val id: String = "",
    val at: String = "",              // ISO local datetime "yyyy-MM-dd'T'HH:mm"
    val intensity: Int = 1,
    val symptoms: List<String> = emptyList(),
    val triggers: List<String> = emptyList(),
    val note: String = "",
) {
    val date: String get() = at.take(10)
}

/** A journal entry. [id] is a client-generated UUID; [text] is plaintext for now
 *  (an `enc:v1` E2EE port comes later — see the migration plan). */
@Serializable
data class JournalEntry(val id: String, val date: String, val text: String)

@Serializable
data class CycleData(
    val periodStarts: List<String> = emptyList(),
    val cycleLengthSetting: Int = Cycle.DEFAULT_CYCLE_LENGTH,
    val periodLength: Int = Cycle.DEFAULT_PERIOD_LENGTH,
    val displayName: String = "",
    val moods: List<MoodLog> = emptyList(),
    val headaches: List<HeadacheLog> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    /**
     * The last day of bleeding, inclusive, keyed by the period's start — only for periods
     * the user chose to close. A period without an entry is assumed to last [periodLength],
     * the "usual" length. Additive: files without it decode, and older builds ignore it.
     */
    val periodEnds: Map<String, String> = emptyMap(),
) {
    /** True once the user has logged at least one real period. */
    val hasLoggedPeriod: Boolean get() = periodStarts.isNotEmpty()

    /** Most recent period start (the current-cycle anchor), or null if none logged. */
    val lastPeriodStartIso: String? get() = periodStarts.maxOrNull()

    /** Logged end of the most recent period, or null while it is open. */
    val lastPeriodEndIso: String? get() = lastPeriodStartIso?.let { periodEnds[it] }

    /** Comma-separated period starts, for the CSV-based [CyclunaCore] entry points. */
    val periodStartsCsv: String get() = periodStarts.joinToString(",")

    /** `start=end` pairs, comma-separated, the companion of [periodStartsCsv]. */
    val periodEndsCsv: String get() = periodEnds.entries.joinToString(",") { "${it.key}=${it.value}" }

    /** Log a period start — append + dedup + keep sorted. Never resets history. */
    fun logPeriod(iso: String): CycleData =
        if (periodStarts.contains(iso)) this
        else copy(periodStarts = (periodStarts + iso).sorted())

    /** Remove one logged period start — the undo for an accidental log or a backfill
     *  mistake. Unknown dates are a no-op; every other entry stays untouched. */
    fun removePeriod(iso: String): CycleData =
        copy(periodStarts = periodStarts.filterNot { it == iso }, periodEnds = periodEnds - iso)

    /** Edit the most recent period start in place. Dedups like [logPeriod] — editing the
     *  last start onto a date already in history must not create a zero-length cycle.
     *  Reopens the period: a logged end described the old start, and keeping it turned a
     *  corrected start into a closed one-day period with no way back from the hero. */
    fun withLastPeriodStart(iso: String): CycleData {
        val sorted = periodStarts.sorted()
        val rest = sorted.dropLast(1)
        val ends = sorted.lastOrNull()?.let { periodEnds - it } ?: periodEnds
        return copy(periodStarts = (rest + iso).distinct().sorted(), periodEnds = ends)
    }

    /**
     * Close a logged period on its last day of bleeding. Ignored for a start that was never
     * logged or an end before the start, so a stale UI can never write an impossible pair.
     */
    fun withPeriodEnd(startIso: String, endIso: String): CycleData =
        if (startIso !in periodStarts || endIso < startIso) this
        else copy(periodEnds = periodEnds + (startIso to endIso))

    /** Reopen a period — it goes back to the usual length. */
    fun clearingPeriodEnd(startIso: String): CycleData = copy(periodEnds = periodEnds - startIso)

    /**
     * Set the current period to [startIso]..[endIso] (null = still going) in one step. The
     * adjusted dates are what the user just said happened, so any other logged period whose
     * flow overlaps them is absorbed rather than left to shade the calendar beside them.
     */
    fun adjustingCurrentPeriod(startIso: String, endIso: String?): CycleData {
        val moved = withLastPeriodStart(startIso)
            .let { if (endIso == null) it else it.withPeriodEnd(startIso, endIso) }
        val newEnd = endIso ?: moved.flowEndOf(startIso)
        val overlapping = moved.periodStarts.filter {
            it != startIso && it <= newEnd && moved.flowEndOf(it) >= startIso
        }
        return overlapping.fold(moved) { d, s -> d.removePeriod(s) }
    }

    /** Last day of a period's flow: its logged end, else the usual length from its start. */
    private fun flowEndOf(startIso: String): String =
        periodEnds[startIso]
            ?: runCatching { LocalDate.parse(startIso).plus(DatePeriod(days = periodLength - 1)).toString() }
                .getOrDefault(startIso)

    fun withCycleLength(days: Int): CycleData = copy(cycleLengthSetting = days)
    fun withPeriodLength(days: Int): CycleData = copy(periodLength = days)
    fun withDisplayName(name: String): CycleData = copy(displayName = name)

    // --- Daily logs (mood/headache: one per date, latest wins) + journal ---

    fun moodOn(iso: String): MoodLog? = moods.firstOrNull { it.date == iso }

    fun withMood(iso: String, mood: Int, note: String = ""): CycleData =
        copy(moods = moods.filterNot { it.date == iso } + MoodLog(iso, mood, note))

    fun clearingMood(iso: String): CycleData = copy(moods = moods.filterNot { it.date == iso })

    /** All headache episodes on a day, earliest first (multiple per day are allowed). */
    fun headachesOn(iso: String): List<HeadacheLog> = headaches.filter { it.date == iso }.sortedBy { it.at }

    fun addingHeadache(entry: HeadacheLog): CycleData = copy(headaches = headaches + entry)
    fun removingHeadache(id: String): CycleData = copy(headaches = headaches.filterNot { it.id == id })

    fun addingJournal(entry: JournalEntry): CycleData = copy(journal = journal + entry)
    fun removingJournal(id: String): CycleData = copy(journal = journal.filterNot { it.id == id })

    companion object {
        val EMPTY = CycleData()
    }
}

/**
 * On-device persistence + data-export formats. Shared so the two clients read/write the
 * exact same bytes and can never drift. Platform layers own only the raw file I/O (and its
 * file-protection level); the FORMAT is defined here once.
 */
object CyclePersistence {
    const val EXPORT_SCHEMA = "cycluna.export.v1"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serialize the persisted state (compact, internal on-disk format). */
    fun encode(data: CycleData): String = json.encodeToString(CycleData.serializer(), data)

    /**
     * Parse persisted state; returns null on corrupt/absent data (caller falls back to EMPTY).
     *
     * Period starts are validated here rather than trusted. They are plain strings on disk, so
     * a partially corrupted or hand-edited file can carry a non-ISO value — and every read path
     * (`lastPeriodStartIso`, the calendar's CSV) parses them as dates, which would throw while
     * rendering Home. A single bad entry is dropped rather than discarding the whole file:
     * losing one date beats losing the user's entire history.
     */
    fun decode(text: String): CycleData? =
        try {
            sanitised(json.decodeFromString(CycleData.serializer(), text))
        } catch (_: Exception) { null }

    private fun isIsoDate(s: String): Boolean =
        try { LocalDate.parse(s); true } catch (_: Exception) { false }

    /**
     * Drop what the read paths cannot use: non-ISO starts, and ends that are non-ISO, belong
     * to no kept start, or come before their start.
     */
    private fun sanitised(data: CycleData): CycleData {
        val starts = data.periodStarts.filter { isIsoDate(it) }.sorted()
        val ends = data.periodEnds.filter { (start, end) ->
            start in starts && isIsoDate(end) && end >= start
        }
        return data.copy(periodStarts = starts, periodEnds = ends)
    }

    /**
     * The GDPR/CCPA data-portability export: a versioned, human- and machine-readable
     * envelope. [exportedAtIso] is supplied by the platform (its clock).
     */
    fun exportJson(data: CycleData, exportedAtIso: String): String {
        val envelope = ExportEnvelope(
            schema = EXPORT_SCHEMA,
            exportedAt = exportedAtIso,
            displayName = data.displayName,
            cycleLengthSetting = data.cycleLengthSetting,
            periodLength = data.periodLength,
            periodStarts = data.periodStarts.sorted(),
            moods = data.moods,
            headaches = data.headaches,
            journal = data.journal,
            periodEnds = data.periodEnds,
        )
        return json.encodeToString(ExportEnvelope.serializer(), envelope)
    }

    /**
     * Parse a data-portability export back into persisted state — the other half of
     * [exportJson], for restoring a backup or moving between devices. Returns null when
     * the text is not a Cycluna export (not JSON, wrong shape, or wrong schema — a future
     * schema is refused rather than guessed at). Dates get the same validation as
     * [decode]; the export metadata (schema, exportedAt) is dropped.
     */
    fun importJson(text: String): CycleData? =
        try {
            val env = json.decodeFromString(ExportEnvelope.serializer(), text)
            if (env.schema != EXPORT_SCHEMA) {
                null
            } else {
                sanitised(
                    CycleData(
                        periodStarts = env.periodStarts,
                        cycleLengthSetting = env.cycleLengthSetting,
                        periodLength = env.periodLength,
                        displayName = env.displayName,
                        moods = env.moods,
                        headaches = env.headaches,
                        journal = env.journal,
                        periodEnds = env.periodEnds,
                    )
                )
            }
        } catch (_: Exception) { null }

    @Serializable
    private data class ExportEnvelope(
        val schema: String,
        val exportedAt: String,
        val displayName: String,
        val cycleLengthSetting: Int,
        val periodLength: Int,
        val periodStarts: List<String>,
        val moods: List<MoodLog>,
        val headaches: List<HeadacheLog>,
        val journal: List<JournalEntry>,
        /** Added after v1 shipped; exports from before it simply have none. */
        val periodEnds: Map<String, String> = emptyMap(),
    )
}
