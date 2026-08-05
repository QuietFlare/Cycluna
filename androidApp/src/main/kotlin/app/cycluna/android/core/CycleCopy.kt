package app.cycluna.android.core

/**
 * How far Cycluna trusts its own prediction — the Android mirror of the shared core's
 * `CycleTracking`, reached through the String-returning facade the way iOS reaches it.
 */
enum class TrackingState {
    NORMAL, LATE, UNCLEAR;

    companion object {
        fun from(raw: String): TrackingState =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NORMAL
    }
}

/**
 * The user-facing sentences derived from cycle state.
 *
 * Pure functions of plain values — no store, no disk, no clock — so the branching can be
 * tested directly. [CycleStore] supplies the numbers; this decides the wording. Kept
 * character-for-character in step with `CycleCopy.swift`.
 */
object CycleCopy {

    fun lateText(daysLate: Int): String =
        if (daysLate == 1) "1 day late" else "$daysLate days late"

    /** Value for the "Next period" tile. */
    fun nextPeriodShort(tracking: TrackingState, daysLate: Int, daysUntilNextPeriod: Int): String =
        when (tracking) {
            TrackingState.LATE -> lateText(daysLate)
            // Nothing honest to show: the prediction is too stale to name a date.
            TrackingState.UNCLEAR -> "Unknown"
            TrackingState.NORMAL -> when (daysUntilNextPeriod) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> "in $daysUntilNextPeriod days"
            }
        }

    /**
     * The sentence under the hero card.
     *
     * [daysUntilFertileStart] / [daysUntilFertileEnd] are whole days from today — negative
     * once the date has passed.
     */
    fun fertileContext(
        tracking: TrackingState,
        daysLate: Int,
        daysUntilFertileStart: Int,
        daysUntilFertileEnd: Int,
        daysUntilNextPeriod: Int,
    ): String = when (tracking) {
        TrackingState.LATE ->
            "Your period is ${lateText(daysLate)}. Log it when it starts."
        // Deliberately plain, and true in both situations this state occurs in: a long gap
        // with nothing logged, and a brand-new user who onboarded with an old date. "We've
        // lost track of your cycle" read like a malfunction to someone who had just finished
        // setup.
        TrackingState.UNCLEAR ->
            "It's been a while. Log your period when it starts."
        TrackingState.NORMAL -> when {
            // Singular forms match the web app's Home copy exactly.
            daysUntilFertileStart > 0 ->
                if (daysUntilFertileStart == 1) "Your fertile window opens tomorrow."
                else "Your fertile window opens in $daysUntilFertileStart days."
            daysUntilFertileEnd >= 0 -> "You're in your fertile window."
            daysUntilNextPeriod == 0 -> "Your period may start today."
            daysUntilNextPeriod == 1 -> "1 day until your next period."
            else -> "$daysUntilNextPeriod days until your next period."
        }
    }
}
