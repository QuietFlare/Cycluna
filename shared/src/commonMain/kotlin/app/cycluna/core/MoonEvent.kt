package app.cycluna.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A special lunar event that can't be derived from phase alone (lunar eclipses /
 * "blood moons", supermoons, …). These are shipped as a bundled JSON dataset and loaded
 * offline — no network, no tracking. Everyday phases (new/quarter/full, blue/black moon)
 * are computed on-device in [Moon] and are NOT stored here.
 *
 * [date] is ISO `yyyy-MM-dd` (UTC date of the event). [type] is a stable slug the UI maps
 * to an icon/emphasis; [title]/[detail] are display copy.
 */
@Serializable
data class MoonEvent(
    val date: String,
    val type: String,
    val title: String,
    val detail: String = "",
)

/** Parses the bundled moon-events JSON. Platform layers load the file; the FORMAT is here. */
object MoonEvents {
    @Serializable
    private data class File(
        val schema: String = "",
        val source: String = "",
        val events: List<MoonEvent> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<MoonEvent> =
        try { json.decodeFromString(File.serializer(), text).events } catch (_: Exception) { emptyList() }
}
