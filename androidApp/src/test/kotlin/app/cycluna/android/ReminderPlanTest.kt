package app.cycluna.android

import app.cycluna.android.core.AppSettings
import app.cycluna.android.core.Planned
import app.cycluna.android.core.ReminderPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What Cycluna decides to schedule, and what it says. Ported from `ReminderPlanTests.swift`:
 * the planner is pure on both platforms so the same cases pin both.
 */
class ReminderPlanTest {

    private val period = LocalDate.of(2026, 8, 20)
    private val fertile = LocalDate.of(2026, 8, 8)

    private fun settings(
        periodOn: Boolean = false,
        ovulationOn: Boolean = false,
        periodLead: Int = 1,
        ovulationLead: Int = 0,
        moodCheckIn: Boolean = false,
        headacheCheckIn: Boolean = false,
    ) = AppSettings(
        periodOn = periodOn,
        ovulationOn = ovulationOn,
        periodLead = periodLead,
        ovulationLead = ovulationLead,
        moodCheckIn = moodCheckIn,
        headacheCheckIn = headacheCheckIn,
    )

    private fun List<Planned>.byId(id: String): Planned? = firstOrNull { it.id == id }

    @Test
    fun nothingIsScheduledWhenEveryReminderIsOff() {
        assertTrue(ReminderPlan.plan(period, fertile, settings()).isEmpty())
    }

    @Test
    fun periodReminderFiresTheChosenNumberOfDaysAhead() {
        val plan = ReminderPlan.plan(period, fertile, settings(periodOn = true, periodLead = 2))
        assertEquals(LocalDate.of(2026, 8, 18), plan.byId(ReminderPlan.PERIOD_ID)?.date)
    }

    @Test
    fun aLeadOfZeroFiresOnTheDayItself() {
        val plan = ReminderPlan.plan(period, fertile, settings(periodOn = true, periodLead = 0))
        assertEquals(period, plan.byId(ReminderPlan.PERIOD_ID)?.date)
    }

    @Test
    fun theFollowUpAsksOnceTheDayAfterAPredictedStart() {
        val plan = ReminderPlan.plan(period, fertile, settings(periodOn = true))
        val followUp = plan.byId(ReminderPlan.FOLLOW_UP_ID)
        assertEquals(LocalDate.of(2026, 8, 21), followUp?.date)
        assertEquals("Did your period start? 🌙", followUp?.title)
    }

    @Test
    fun theFollowUpOnlyExistsAlongsideThePeriodReminder() {
        val plan = ReminderPlan.plan(period, fertile, settings(ovulationOn = true))
        assertNull(plan.byId(ReminderPlan.FOLLOW_UP_ID))
    }

    @Test
    fun nothingCycleRelatedIsScheduledWithoutAPrediction() {
        // An overdue or brand-new cycle has no dates to measure from, so there is nothing
        // honest to schedule.
        val plan = ReminderPlan.plan(null, null, settings(periodOn = true, ovulationOn = true))
        assertTrue(plan.isEmpty())
    }

    @Test
    fun ovulationFiresRelativeToTheFertileWindowOpening() {
        val plan = ReminderPlan.plan(period, fertile, settings(ovulationOn = true, ovulationLead = 2))
        assertEquals(LocalDate.of(2026, 8, 6), plan.byId(ReminderPlan.OVULATION_ID)?.date)
    }

    @Test
    fun bothCheckInsCollapseIntoOneNotification() {
        val plan = ReminderPlan.plan(
            null, null,
            settings(moodCheckIn = true, headacheCheckIn = true),
        )
        assertEquals(1, plan.size)
        val checkIn = plan.byId(ReminderPlan.CHECK_IN_ID)
        assertEquals("How was today? 🌙", checkIn?.title)
        assertEquals("Log your mood and any headaches in Journal.", checkIn?.body)
        assertTrue(checkIn?.repeats == true)
        // A repeat has no date; the time of day is the whole schedule.
        assertNull(checkIn?.date)
    }

    @Test
    fun theCheckInWordingFollowsWhichOnesAreOn() {
        assertEquals("Any headaches today? 🌙", ReminderPlan.checkInTitle(mood = false, headache = true))
        assertEquals("Log any headaches in Journal.", ReminderPlan.checkInBody(mood = false, headache = true))
        assertEquals("How are you feeling today? 🌙", ReminderPlan.checkInTitle(mood = true, headache = false))
        assertEquals("Log your mood in Journal.", ReminderPlan.checkInBody(mood = true, headache = false))
    }

    @Test
    fun titlesUseSingularFormsRatherThanInOneDays() {
        assertEquals("Your period may start today 🌙", ReminderPlan.periodTitle(0))
        assertEquals("Your period may start tomorrow 🌙", ReminderPlan.periodTitle(1))
        assertEquals("Your period may start in 2 days 🌙", ReminderPlan.periodTitle(2))
        assertEquals("Your fertile window opens today ✨", ReminderPlan.ovulationTitle(0))
        assertEquals("Your fertile window opens tomorrow ✨", ReminderPlan.ovulationTitle(1))
        assertEquals("Your fertile window opens in 3 days ✨", ReminderPlan.ovulationTitle(3))
    }

    @Test
    fun everyReminderCarriesTheTimeItsSectionConfigures() {
        val plan = ReminderPlan.plan(
            period, fertile,
            AppSettings(
                periodOn = true,
                ovulationOn = true,
                moodCheckIn = true,
                cycleMinute = 7 * 60 + 30,
                checkInMinute = 21 * 60,
            ),
        )
        assertEquals(450, plan.byId(ReminderPlan.PERIOD_ID)?.minuteOfDay)
        assertEquals(450, plan.byId(ReminderPlan.OVULATION_ID)?.minuteOfDay)
        assertEquals(1260, plan.byId(ReminderPlan.CHECK_IN_ID)?.minuteOfDay)
    }
}
