package app.cycluna.android.core

import android.content.Context
import app.cycluna.core.MoonEvent
import app.cycluna.core.MoonEvents

/**
 * Loads the bundled `moon-events.json` once and indexes it by ISO date. Fully offline — the
 * file ships in the APK's assets and the shared core (`MoonEvents`) owns the format.
 *
 * These are the special events (eclipses / "blood moons") that cannot be derived from phase
 * maths; everyday phases come from `CyclunaCore.moonPhaseMarker`.
 */
object MoonEventCatalog {

    private var byDate: Map<String, MoonEvent> = emptyMap()

    fun load(context: Context) {
        byDate = runCatching {
            val text = context.assets.open("moon-events.json").bufferedReader().use { it.readText() }
            MoonEvents.parse(text).associateBy { it.date }
        }.getOrDefault(emptyMap())
    }

    fun event(iso: String): MoonEvent? = byDate[iso]
}
