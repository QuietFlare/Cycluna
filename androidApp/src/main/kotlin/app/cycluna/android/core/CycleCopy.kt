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
     * The sentence under the hero card — only when something needs saying. Empty in normal
     * tracking on purpose: everything it used to say there (fertile countdown, days to the
     * next period) duplicated the tiles directly above it. Text appears only when the tiles
     * can't carry the state: a late period, or tracking gone unclear.
     */
    fun fertileContext(tracking: TrackingState, daysLate: Int): String = when (tracking) {
        TrackingState.LATE ->
            "Your period is ${lateText(daysLate)}. Log it when it starts."
        // Deliberately plain, and true in both situations this state occurs in: a long gap
        // with nothing logged, and a brand-new user who onboarded with an old date. "We've
        // lost track of your cycle" read like a malfunction to someone who had just finished
        // setup.
        TrackingState.UNCLEAR ->
            "It's been a while. Log your period when it starts."
        TrackingState.NORMAL -> ""
    }
}
