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

    // --- Per-period end dates ---------------------------------------------------

    @Test
    fun periodEndIsKeyedByStartAndRoundTrips() {
        val d = CycleData.EMPTY.logPeriod("2026-03-10").withPeriodEnd("2026-03-10", "2026-03-15")
        assertEquals("2026-03-15", d.lastPeriodEndIso)
        assertEquals("2026-03-10=2026-03-15", d.periodEndsCsv)
        assertEquals(d, CyclePersistence.decode(CyclePersistence.encode(d)))
        assertEquals(d, CyclePersistence.importJson(CyclePersistence.exportJson(d, "2026-07-31T18:59:43Z")))
    }

    @Test
    fun periodEndRefusesUnknownStartsAndEndsBeforeStart() {
        val d = CycleData.EMPTY.logPeriod("2026-03-10")
        assertEquals(d, d.withPeriodEnd("2026-02-01", "2026-02-05"))
        assertEquals(d, d.withPeriodEnd("2026-03-10", "2026-03-09"))
        // Same day is a one-day period.
        assertEquals("2026-03-10", d.withPeriodEnd("2026-03-10", "2026-03-10").lastPeriodEndIso)
        assertNull(d.withPeriodEnd("2026-03-10", "2026-03-12").clearingPeriodEnd("2026-03-10").lastPeriodEndIso)
    }

    @Test
    fun removingAPeriodDropsItsEnd() {
        val d = CycleData.EMPTY.logPeriod("2026-03-10").withPeriodEnd("2026-03-10", "2026-03-15")
        assertTrue(d.removePeriod("2026-03-10").periodEnds.isEmpty())
    }

    @Test
    fun movingTheLastStartReopensThePeriod() {
        // The end described the old start. Keeping it turned "move the start back to today"
        // into a closed one-day period, with no way to reopen it from the hero.
        val d = CycleData.EMPTY.logPeriod("2026-01-05").withPeriodEnd("2026-01-05", "2026-01-09")
            .logPeriod("2026-03-10").withPeriodEnd("2026-03-10", "2026-03-15")
        val moved = d.withLastPeriodStart("2026-03-12")
        assertNull(moved.lastPeriodEndIso)
        // Earlier periods keep their ends.
        assertEquals(mapOf("2026-01-05" to "2026-01-09"), moved.periodEnds)
    }

    @Test
    fun adjustingTheCurrentPeriodAbsorbsWhateverItOverlaps() {
        // Testing left two periods a day apart, 7–9 Oct and 8 Oct open. The calendar shaded
        // the union and day 1 sat on the 8th. The dates the user sets in Adjust are the
        // truth; an entry contradicting them cannot stand beside them.
        val d = CycleData.EMPTY
            .logPeriod("2026-08-11")
            .logPeriod("2026-10-07").withPeriodEnd("2026-10-07", "2026-10-09")
            .logPeriod("2026-10-08")
        val adjusted = d.adjustingCurrentPeriod("2026-10-08", "2026-10-13")
        assertEquals(listOf("2026-08-11", "2026-10-08"), adjusted.periodStarts)
        assertEquals(mapOf("2026-10-08" to "2026-10-13"), adjusted.periodEnds)
    }

    @Test
    fun adjustingAnOpenPeriodUsesTheUsualLengthToDecideOverlap() {
        // 8 Oct open → assumed 8–12. Moving the current start onto 10 Oct, still going,
        // overlaps that, so the 8 Oct entry goes; August is untouched.
        val d = CycleData(periodStarts = listOf("2026-08-11", "2026-10-08", "2026-10-12"), periodLength = 5)
        val adjusted = d.adjustingCurrentPeriod("2026-10-10", null)
        assertEquals(listOf("2026-08-11", "2026-10-10"), adjusted.periodStarts)
        assertTrue(adjusted.periodEnds.isEmpty())
        // No overlap, nothing absorbed: a period well clear of the new span stays.
        val apart = d.adjustingCurrentPeriod("2026-10-20", "2026-10-22")
        assertEquals(listOf("2026-08-11", "2026-10-08", "2026-10-20"), apart.periodStarts)
    }

    @Test
    fun decodeDropsEndsItCannotUse() {
        val text = """
            {"periodStarts":["2026-03-10","2026-04-08"],"cycleLengthSetting":28,"periodLength":5,
             "periodEnds":{"2026-03-10":"2026-03-14","2026-04-08":"2026-04-01",
                           "2026-01-01":"2026-01-05","2026-04-08x":"junk"}}
        """.trimIndent()
        val d = assertNotNull(CyclePersistence.decode(text))
        assertEquals(mapOf("2026-03-10" to "2026-03-14"), d.periodEnds)
    }

    @Test
    fun filesAndExportsWithoutEndsStillLoad() {
        val legacy = """{"periodStarts":["2026-07-22"],"cycleLengthSetting":28,"periodLength":5,"displayName":"Sam"}"""
        assertTrue(assertNotNull(CyclePersistence.decode(legacy)).periodEnds.isEmpty())
        val export = """
            {"schema":"cycluna.export.v1","exportedAt":"2026-07-31T18:59:43Z",
             "displayName":"","cycleLengthSetting":28,"periodLength":5,
             "periodStarts":["2026-01-05"],"moods":[],"headaches":[],"journal":[]}
        """.trimIndent()
        assertTrue(assertNotNull(CyclePersistence.importJson(export)).periodEnds.isEmpty())
    }
}
