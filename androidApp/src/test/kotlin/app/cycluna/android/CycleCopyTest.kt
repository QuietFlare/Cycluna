package app.cycluna.android

import app.cycluna.android.core.CycleCopy
import app.cycluna.android.core.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The wording Home shows for a normal, late, or lost cycle. These are the sentences that
 * decide whether the app tells the truth about an overdue period, so each branch is pinned.
 * Ported case for case from `CycleCopyTests.swift` — the two platforms must say the same
 * thing in the same situation.
 */
class CycleCopyTest {

    // ---- "Next period" tile -----------------------------------------------------------

    @Test
    fun nextPeriodCountsDownWhileOnTrack() {
        assertEquals(
            "in 14 days",
            CycleCopy.nextPeriodShort(TrackingState.NORMAL, daysLate = 0, daysUntilNextPeriod = 14),
        )
    }

    @Test
    fun nextPeriodSaysTodayRatherThanInZeroDays() {
        assertEquals(
            "Today",
            CycleCopy.nextPeriodShort(TrackingState.NORMAL, daysLate = 0, daysUntilNextPeriod = 0),
        )
    }

    @Test
    fun nextPeriodSaysTomorrowRatherThanInOneDays() {
        assertEquals(
            "Tomorrow",
            CycleCopy.nextPeriodShort(TrackingState.NORMAL, daysLate = 0, daysUntilNextPeriod = 1),
        )
    }

    @Test
    fun nextPeriodReportsLatenessInsteadOfACountdown() {
        assertEquals(
            "3 days late",
            CycleCopy.nextPeriodShort(TrackingState.LATE, daysLate = 3, daysUntilNextPeriod = 0),
        )
    }

    @Test
    fun latenessIsSingularOnTheFirstDay() {
        assertEquals(
            "1 day late",
            CycleCopy.nextPeriodShort(TrackingState.LATE, daysLate = 1, daysUntilNextPeriod = 0),
        )
    }

    @Test
    fun nextPeriodNamesNoDateOnceTrackingIsLost() {
        assertEquals(
            "Unknown",
            CycleCopy.nextPeriodShort(TrackingState.UNCLEAR, daysLate = 40, daysUntilNextPeriod = 0),
        )
    }

    // ---- Hero card sentence -----------------------------------------------------------

    @Test
    fun contextCountsDownToTheFertileWindow() {
        assertEquals(
            "Your fertile window opens in 4 days.",
            CycleCopy.fertileContext(TrackingState.NORMAL, 0, 4, 8, 18),
        )
    }

    @Test
    fun contextUsesSingularFormsOnTheLastDay() {
        // The web app's exact wording — never "in 1 days" / "1 days until".
        assertEquals(
            "Your fertile window opens tomorrow.",
            CycleCopy.fertileContext(TrackingState.NORMAL, 0, 1, 5, 15),
        )
        assertEquals(
            "1 day until your next period.",
            CycleCopy.fertileContext(TrackingState.NORMAL, 0, -9, -5, 1),
        )
    }

    @Test
    fun contextRecognisesTheWindowIsOpen() {
        // Start has passed (0 or negative), end has not.
        assertEquals(
            "You're in your fertile window.",
            CycleCopy.fertileContext(TrackingState.NORMAL, 0, 0, 4, 14),
        )
        assertEquals(
            "the final day of the window still counts as inside it",
            "You're in your fertile window.",
            CycleCopy.fertileContext(TrackingState.NORMAL, 0, -2, 0, 12),
        )
    }

    @Test
    fun contextFallsBackToTheNextPeriodOnceTheWindowHasPassed() {
        assertEquals(
            "6 days until your next period.",
            CycleCopy.fertileContext(TrackingState.NORMAL, 0, -9, -5, 6),
        )
    }

    @Test
    fun contextStatesLatenessPlainly() {
        assertEquals(
            "lateness must win over any fertile-window sentence",
            "Your period is 3 days late. Log it when it starts.",
            CycleCopy.fertileContext(TrackingState.LATE, 3, 5, 9, 0),
        )
    }

    @Test
    fun contextAsksForAFreshLogOnceTrackingIsLost() {
        // Plain wording on purpose: this same sentence greets a new user who onboarded with
        // an old date, where anything alarming reads as the app being broken.
        assertEquals(
            "It's been a while. Log your period when it starts.",
            CycleCopy.fertileContext(TrackingState.UNCLEAR, 40, 3, 7, 0),
        )
    }

    // ---- Mapping from the shared core -------------------------------------------------

    @Test
    fun trackingStateParsesEveryValueTheCoreCanReturn() {
        assertEquals(TrackingState.NORMAL, TrackingState.from("normal"))
        assertEquals(TrackingState.LATE, TrackingState.from("late"))
        assertEquals(TrackingState.UNCLEAR, TrackingState.from("unclear"))
        // Swift returns nil and the call site defaults; Kotlin folds the default in here, so
        // an unknown value must still land on the honest, non-alarming state.
        assertEquals(TrackingState.NORMAL, TrackingState.from("something-else"))
    }
}
