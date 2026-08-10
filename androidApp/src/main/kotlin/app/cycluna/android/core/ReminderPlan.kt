package app.cycluna.android.core

import java.time.LocalDate

/**
 * One reminder Cycluna intends to schedule. [date] is null for a daily repeat.
 */
data class Planned(
    val id: String,
    val title: String,
    val body: String,
    val date: LocalDate?,
    val minuteOfDay: Int,
    val repeats: Boolean,
)

/**
 * What should be scheduled, given the current predictions and settings.
 *
 * Pure — no alarm manager, no wall clock — so the decisions (which reminders, what dates,
 * what wording) are testable on their own. [ReminderScheduler] just carries it out. Kept in
 * step with `ReminderManager.swift`; both platforms must say the same thing at the same time.
 */
object ReminderPlan {
    const val PERIOD_ID = "cycluna.reminder.period"
    const val FOLLOW_UP_ID = "cycluna.reminder.periodFollowUp"
    const val OVULATION_ID = "cycluna.reminder.ovulation"
    const val CHECK_IN_ID = "cycluna.reminder.checkin"

    val ALL_IDS = listOf(PERIOD_ID, FOLLOW_UP_ID, OVULATION_ID, CHECK_IN_ID)

    /** Days after the predicted start to ask whether the period arrived. */
    const val FOLLOW_UP_DELAY_DAYS = 1L

    /**
     * Where a tapped notification lands: the daily check-in opens Journal, where mood and
     * headaches are logged; the cycle reminders stay on Today, where the predictions live.
     */
    fun opensJournal(id: String): Boolean = id == CHECK_IN_ID

    /**
     * What every reminder says in discreet mode: the schedule and tap routing are unchanged,
     * but nothing about cycles, moods or headaches is visible on the lock screen.
     */
    const val DISCREET_TITLE = "Cycluna"
    const val DISCREET_BODY = "A gentle check-in when you have a moment."

    fun plan(
        nextPeriod: LocalDate?,
        fertileStart: LocalDate?,
        settings: AppSettings,
    ): List<Planned> = buildPlan(nextPeriod, fertileStart, settings).let { plan ->
        if (settings.discreet) {
            plan.map { it.copy(title = DISCREET_TITLE, body = DISCREET_BODY) }
        } else {
            plan
        }
    }

    private fun buildPlan(
        nextPeriod: LocalDate?,
        fertileStart: LocalDate?,
        settings: AppSettings,
    ): List<Planned> = buildList {
        if (settings.periodOn && nextPeriod != null) {
            add(
                Planned(
                    id = PERIOD_ID,
                    title = periodTitle(settings.periodLead),
                    body = "A little notice so you can be ready and gentle with yourself.",
                    date = nextPeriod.minusDays(settings.periodLead.toLong()),
                    minuteOfDay = settings.cycleMinute,
                    repeats = false,
                )
            )
            // If the predicted date passes with nothing logged, ask once rather than going
            // quiet exactly when it matters most. Logging a period moves the anchor, so the
            // next reschedule replaces this with one a cycle later — never a daily nag.
            add(
                Planned(
                    id = FOLLOW_UP_ID,
                    title = "Did your period start?",
                    body = "If it has, log it and Cycluna will stay in tune with you.",
                    date = nextPeriod.plusDays(FOLLOW_UP_DELAY_DAYS),
                    minuteOfDay = settings.cycleMinute,
                    repeats = false,
                )
            )
        }

        // No ovulation reminder when fertility insights are off — the stored toggle keeps
        // its value, but nothing fertility-shaped may reach the lock screen.
        if (settings.ovulationOn && settings.fertility && fertileStart != null) {
            add(
                Planned(
                    id = OVULATION_ID,
                    title = ovulationTitle(settings.ovulationLead),
                    body = "Your body is moving into its most fertile days.",
                    date = fertileStart.minusDays(settings.ovulationLead.toLong()),
                    minuteOfDay = settings.cycleMinute,
                    repeats = false,
                )
            )
        }

        if (settings.anyCheckIn) {
            add(
                Planned(
                    id = CHECK_IN_ID,
                    title = checkInTitle(settings.moodCheckIn, settings.headacheCheckIn),
                    body = checkInBody(settings.moodCheckIn, settings.headacheCheckIn),
                    date = null,
                    minuteOfDay = settings.checkInMinute,
                    repeats = true,
                )
            )
        }
    }

    fun periodTitle(leadDays: Int): String = when (leadDays) {
        0 -> "Your period may start today"
        1 -> "Your period may start tomorrow"
        else -> "Your period may start in $leadDays days"
    }

    fun ovulationTitle(leadDays: Int): String = when (leadDays) {
        0 -> "Your fertile window opens today"
        1 -> "Your fertile window opens tomorrow"
        else -> "Your fertile window opens in $leadDays days"
    }

    /**
     * One notification covers both check-ins when both are on — two separate pings at the
     * same time of day would just read as a duplicate.
     */
    fun checkInTitle(mood: Boolean, headache: Boolean): String = when {
        mood && headache -> "How was today?"
        headache -> "Any headaches today?"
        else -> "How are you feeling today?"
    }

    fun checkInBody(mood: Boolean, headache: Boolean): String = when {
        mood && headache -> "Take a moment for yourself and note how today felt, head and heart."
        headache -> "Take a moment to note how your head has been today."
        else -> "Take a moment for yourself and note how today felt."
    }
}
