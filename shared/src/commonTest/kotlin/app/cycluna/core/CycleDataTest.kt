package app.cycluna.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
