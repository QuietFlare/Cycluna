package app.cycluna.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/**
 * The eight canonical moon phases, in synodic order starting at the new moon.
 * [slug] is the stable identifier that crosses to Swift/Android — never a display string,
 * so the native layer owns its own wording.
 */
enum class MoonPhaseKey(val label: String, val symbol: String, val slug: String) {
    NEW("New moon", "●", "new"),
    WAXING_CRESCENT("Waxing crescent", "☽", "waxing-crescent"),
    FIRST_QUARTER("First quarter", "◐", "first-quarter"),
    WAXING_GIBBOUS("Waxing gibbous", "◔", "waxing-gibbous"),
    FULL("Full moon", "○", "full"),
    WANING_GIBBOUS("Waning gibbous", "◕", "waning-gibbous"),
    LAST_QUARTER("Last quarter", "◑", "last-quarter"),
    WANING_CRESCENT("Waning crescent", "☾", "waning-crescent"),
}

data class MoonPhase(val key: MoonPhaseKey, val age: Double) {
    val label: String get() = key.label
    val symbol: String get() = key.symbol
}

/**
 * Moon phase from a simple synodic-month model. Ported from the original
 * `moon.ts`; uses UTC noon of the given calendar date, anchored to a known
 * new moon, so results match the web app exactly.
 */
object Moon {
    internal const val SYNODIC_MONTH = 29.530588853
    private const val DAY_MS = 86_400_000.0

    // Reference new moon: 2000-01-06 18:14 UTC.
    private val knownNewMoonMs =
        LocalDateTime(2000, 1, 6, 18, 14).toInstant(TimeZone.UTC).toEpochMilliseconds()

    fun phase(date: LocalDate): MoonPhase {
        val utcNoonMs =
            LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0)
                .toInstant(TimeZone.UTC).toEpochMilliseconds()
        val daysSinceKnownNew = (utcNoonMs - knownNewMoonMs) / DAY_MS
        val age = ((daysSinceKnownNew % SYNODIC_MONTH) + SYNODIC_MONTH) % SYNODIC_MONTH
        // floor(x + 0.5) reproduces JS Math.round (half-up) for non-negative x.
        val index = floor((age / SYNODIC_MONTH) * 8 + 0.5).toInt() % 8
        return MoonPhase(MoonPhaseKey.entries[index], age)
    }

    /** Fraction of the disc illuminated, 0.0 (new) .. 1.0 (full). */
    fun illumination(date: LocalDate): Double {
        val age = phase(date).age
        return (1 - cos((2 * PI * age) / SYNODIC_MONTH)) / 2
    }

    /** The four principal lunar phases (the ones that fall on a single day). */
    enum class PrincipalPhase(val label: String) {
        NEW("New moon"),
        FIRST_QUARTER("First quarter"),
        FULL("Full moon"),
        LAST_QUARTER("Last quarter"),
    }

    // The synodic month split into four quarters: 0=new→FQ, 1=FQ→full, 2=full→LQ, 3=LQ→new.
    private fun quarterSegment(date: LocalDate): Int =
        floor((phase(date).age / SYNODIC_MONTH) * 4).toInt() % 4

    /**
     * The principal phase that falls on [date], or null on ordinary days. A principal
     * phase "lands" on the day its quarter boundary is crossed (accurate to about ±1 day,
     * which is all a calendar glyph needs) — computed on-device, no data or network.
     */
    fun principalPhase(date: LocalDate): PrincipalPhase? {
        val seg = quarterSegment(date)
        if (seg == quarterSegment(date.minus(DatePeriod(days = 1)))) return null
        return when (seg) {
            0 -> PrincipalPhase.NEW
            1 -> PrincipalPhase.FIRST_QUARTER
            2 -> PrincipalPhase.FULL
            else -> PrincipalPhase.LAST_QUARTER
        }
    }

    /** True if [date] is the SECOND full moon in its calendar month (a "blue moon"). */
    fun isBlueMoon(date: LocalDate): Boolean = isSecondInMonth(date, PrincipalPhase.FULL)

    /** True if [date] is the SECOND new moon in its calendar month (a "black moon"). */
    fun isBlackMoon(date: LocalDate): Boolean = isSecondInMonth(date, PrincipalPhase.NEW)

    private fun isSecondInMonth(date: LocalDate, target: PrincipalPhase): Boolean {
        if (principalPhase(date) != target) return false
        var d = LocalDate(date.year, date.monthNumber, 1)
        while (d < date) {
            if (principalPhase(d) == target) return true
            d = d.plus(DatePeriod(days = 1))
        }
        return false
    }

    /** Waxing (growing toward full) if the moon is in the first half of its cycle. */
    fun isWaxing(date: LocalDate): Boolean = phase(date).age < SYNODIC_MONTH / 2

    /** The next full-moon date on or after [from] (scans forward; a lunation is ~29.5d). */
    fun nextFullMoon(from: LocalDate): LocalDate {
        var d = from
        repeat(40) {
            if (principalPhase(d) == PrincipalPhase.FULL) return d
            d = d.plus(DatePeriod(days = 1))
        }
        return from
    }
}
