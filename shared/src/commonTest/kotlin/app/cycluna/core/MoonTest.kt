package app.cycluna.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoonTest {
    @Test
    fun referenceDateIsNewMoon() {
        assertEquals(MoonPhaseKey.NEW, Moon.phase(LocalDate(2000, 1, 6)).key)
    }

    @Test
    fun fullMoonAboutHalfCycleLater() {
        assertEquals(MoonPhaseKey.FULL, Moon.phase(LocalDate(2000, 1, 21)).key)
    }

    @Test
    fun illuminationWithinBounds() {
        assertTrue(Moon.illumination(LocalDate(2024, 6, 15)) in 0.0..1.0)
    }

    @Test
    fun newMoonIsDark() {
        assertTrue(Moon.illumination(LocalDate(2000, 1, 6)) < 0.05)
    }
}
