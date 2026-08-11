package app.cycluna.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleDataTest {

    @Test
    fun emptyStateHasNoLoggedPeriod() {
        assertFalse(CycleData.EMPTY.hasLoggedPeriod)
        assertNull(CycleData.EMPTY.lastPeriodStartIso)
        assertEquals(Cycle.DEFAULT_CYCLE_LENGTH, CycleData.EMPTY.cycleLengthSetting)
        assertEquals("", CycleData.EMPTY.displayName)
    }

    @Test
    fun logPeriodAppendsDedupsAndSorts() {
        val d = CycleData.EMPTY
            .logPeriod("2026-03-10")
            .logPeriod("2026-01-05")
            .logPeriod("2026-03-10") // duplicate ignored
        assertEquals(listOf("2026-01-05", "2026-03-10"), d.periodStarts)
        assertEquals("2026-03-10", d.lastPeriodStartIso)
        assertTrue(d.hasLoggedPeriod)
    }

    @Test
    fun withLastPeriodStartEditsMostRecentOnly() {
        val d = CycleData.EMPTY.logPeriod("2026-01-05").logPeriod("2026-03-10")
        val edited = d.withLastPeriodStart("2026-03-12")
        assertEquals(listOf("2026-01-05", "2026-03-12"), edited.periodStarts)
    }

    @Test
    fun withLastPeriodStartOntoAnExistingDateDoesNotDuplicateIt() {
        // Editing the last start back onto an earlier logged date must dedup like logPeriod —
        // a duplicated start showed up as a "0-day cycle" in the history list.
        val d = CycleData.EMPTY.logPeriod("2026-01-05").logPeriod("2026-03-10")
        val edited = d.withLastPeriodStart("2026-01-05")
        assertEquals(listOf("2026-01-05"), edited.periodStarts)
    }

    @Test
    fun persistenceRoundTrips() {
        val original = CycleData(
            periodStarts = listOf("2026-01-05", "2026-03-10"),
            cycleLengthSetting = 30,
            periodLength = 6,
            displayName = "Sam",
        )
        val restored = CyclePersistence.decode(CyclePersistence.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun decodeDropsUnparseablePeriodStartsButKeepsTheRest() {
        // Period starts are plain strings on disk. A partially corrupted or hand-edited file
        // used to sail through decode and then throw when the calendar parsed it as a date.
        val text = """
            {"periodStarts":["2026-07-22","not-a-date","2026-06-24",""],
             "cycleLengthSetting":28,"periodLength":5,"displayName":""}
        """.trimIndent()
        val decoded = assertNotNull(CyclePersistence.decode(text))
        assertEquals(listOf("2026-06-24", "2026-07-22"), decoded.periodStarts.sorted())
        assertEquals("2026-07-22", decoded.lastPeriodStartIso)
    }

    @Test
    fun decodeReturnsNullOnGarbage() {
        assertNull(CyclePersistence.decode("not json"))
    }

    @Test
    fun moodUpsertsAndHeadachesStackPerDay() {
        var d = CycleData.EMPTY
            .withMood("2026-08-01", mood = 4, note = "good")
            .addingHeadache(HeadacheLog("h1", "2026-08-01T09:00", 2))
            .addingHeadache(HeadacheLog("h2", "2026-08-01T18:00", 3, triggers = listOf("Hormones")))
            .addingJournal(JournalEntry("id1", "2026-08-01", "calm day"))
        // Re-logging mood replaces; headaches accumulate.
        d = d.withMood("2026-08-01", mood = 2)
        assertEquals(1, d.moods.size)
        assertEquals(2, d.moodOn("2026-08-01")?.mood)
        assertEquals(2, d.headachesOn("2026-08-01").size)
        assertEquals(1, d.journal.size)

        val restored = CyclePersistence.decode(CyclePersistence.encode(d))
        assertEquals(d, restored)
    }

    @Test
    fun journalAddAndRemove() {
        val d = CycleData.EMPTY
            .addingJournal(JournalEntry("a", "2026-08-01", "one"))
            .addingJournal(JournalEntry("b", "2026-08-02", "two"))
        assertEquals(2, d.journal.size)
        assertEquals(1, d.removingJournal("a").journal.size)
    }

    @Test
    fun oldPersistedDataWithoutLogsStillDecodes() {
        // Backward compatibility: a file written before logs existed.
        val legacy = """{"periodStarts":["2026-07-22"],"cycleLengthSetting":28,"periodLength":5,"displayName":"Sam"}"""
        val d = CyclePersistence.decode(legacy)
        assertNull(d?.moods?.firstOrNull())
        assertEquals("Sam", d?.displayName)
    }

    @Test
    fun exportUsesVersionedSchemaAndSortedStarts() {
        val data = CycleData(
            periodStarts = listOf("2026-03-10", "2026-01-05"),
            cycleLengthSetting = 28,
            periodLength = 5,
            displayName = "Sam",
        )
        val json = CyclePersistence.exportJson(data, exportedAtIso = "2026-07-31T18:59:43Z")
        assertTrue(json.contains("\"schema\": \"cycluna.export.v1\""))
        assertTrue(json.contains("\"exportedAt\": \"2026-07-31T18:59:43Z\""))
        // Sorted chronologically regardless of input order.
        assertTrue(json.indexOf("2026-01-05") < json.indexOf("2026-03-10"))
    }

    @Test
    fun removePeriodDeletesOnlyThatStart() {
        val data = CycleData(periodStarts = listOf("2026-04-01", "2026-06-13", "2026-08-11"))
        assertEquals(
            listOf("2026-04-01", "2026-08-11"),
            data.removePeriod("2026-06-13").periodStarts,
        )
        // An unknown date is a no-op rather than an error.
        assertEquals(data, data.removePeriod("2026-05-05"))
    }

    @Test
    fun importRoundTripsAnExport() {
        val data = CycleData(
            periodStarts = listOf("2026-01-05", "2026-02-02"),
            cycleLengthSetting = 30,
            periodLength = 4,
            displayName = "Sam",
            moods = listOf(MoodLog("2026-02-03", 4, "good day")),
            headaches = listOf(HeadacheLog(id = "h1", at = "2026-02-04T09:30", intensity = 2)),
            journal = listOf(JournalEntry("j1", "2026-02-05", "hello")),
        )
        val imported = CyclePersistence.importJson(
            CyclePersistence.exportJson(data, exportedAtIso = "2026-07-31T18:59:43Z")
        )
        assertEquals(data, imported)
    }

    @Test
    fun importRefusesWhatIsNotACyclunaExport() {
        assertNull(CyclePersistence.importJson("not json"))
        assertNull(CyclePersistence.importJson("{}"))
        // The on-disk format is not the export envelope.
        assertNull(
            CyclePersistence.importJson(
                CyclePersistence.encode(CycleData(periodStarts = listOf("2026-01-05")))
            )
        )
        // A future schema is refused rather than guessed at.
        val wrongSchema = CyclePersistence
            .exportJson(CycleData(), exportedAtIso = "2026-07-31T18:59:43Z")
            .replace("cycluna.export.v1", "cycluna.export.v9")
        assertNull(CyclePersistence.importJson(wrongSchema))
    }

    @Test
    fun importDropsBadDatesLikeDecodeDoes() {
        val text = """
            {"schema":"cycluna.export.v1","exportedAt":"2026-07-31T18:59:43Z",
             "displayName":"","cycleLengthSetting":28,"periodLength":5,
             "periodStarts":["2026-01-05","garbage"],"moods":[],"headaches":[],"journal":[]}
        """.trimIndent()
        assertEquals(listOf("2026-01-05"), CyclePersistence.importJson(text)?.periodStarts)
    }
}
