package app.cycluna.android.core

import androidx.compose.ui.graphics.Color

/** Shared vocabulary for the mood scale. */
object MoodScale {
    val emojis = listOf("😫", "😕", "😐", "🙂", "😄")
    val labels = listOf("Rough", "Meh", "Mid", "Good", "Lit")

    fun emoji(v: Int): String = emojis[(v - 1).coerceIn(0, 4)]
    fun label(v: Int): String = labels[(v - 1).coerceIn(0, 4)]

    /**
     * Diverging valence ramp: deep red fades to a NEUTRAL midpoint, then greens deepen.
     * These five are validated for adjacent-step distinguishability, including colour-blind
     * simulation, against the cream card surface.
     *
     * Colour is never the only carrier: the legend labels each dot, charts encode mood by
     * position, and the sheets pair every value with its emoji.
     */
    fun color(v: Int): Color = when (v) {
        1 -> Color(0xFFB03A3A)   // rough — deep red
        2 -> Color(0xFFEC9A82)   // meh — washed salmon
        3 -> Color(0xFF847A6B)   // mid — neutral warm grey (the midpoint)
        4 -> Color(0xFF6DBA7F)   // good — light green
        else -> Color(0xFF2E7B50) // lit — deep green
    }
}

object HeadacheScale {
    val labels = listOf("", "Mild", "Moderate", "Severe")
    fun label(v: Int): String = labels[v.coerceIn(0, 3)]
}
