package app.cycluna.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MoonPhaseDayTest {

    @Test
    fun fullMoonDayIsNearFullyIlluminated() {
        var found: LocalDate? = null
        var d = LocalDate(2026, 1, 1)
        for (i in 0 until 40) {
            if (Moon.principalPhase(d) == Moon.PrincipalPhase.FULL) { found = d; break }
            d = d.plus(DatePeriod(days = 1))
        }
        assertNotNull(found, "expected a full moon within 40 days")
        assertTrue(Moon.illumination(found!!) > 0.96, "full-moon day should be almost fully lit")
    }

    @Test
    fun newMoonDayIsNearlyDark() {
        var found: LocalDate? = null
        var d = LocalDate(2026, 1, 1)
        for (i in 0 until 40) {
            if (Moon.principalPhase(d) == Moon.PrincipalPhase.NEW) { found = d; break }
            d = d.plus(DatePeriod(days = 1))
        }
        assertNotNull(found, "expected a new moon within 40 days")
        assertTrue(Moon.illumination(found!!) < 0.04, "new-moon day should be almost dark")
    }

    @Test
    fun mostDaysHaveNoPrincipalPhase() {
        // In any lunation only 4 days are principal phases; the rest are null.
        var nulls = 0
        var d = LocalDate(2026, 3, 1)
        for (i in 0 until 20) {
            if (Moon.principalPhase(d) == null) nulls++
            d = d.plus(DatePeriod(days = 1))
        }
        assertTrue(nulls >= 15, "most days are ordinary, got $nulls/20 null")
    }
}
