package app.cycluna.android.features.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.core.HeadacheScale
import app.cycluna.android.core.MoodScale
import app.cycluna.android.designsystem.HeartGlyph
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import app.cycluna.core.HeadacheLog
import app.cycluna.core.JournalEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FULL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The logging hub — mood, headache and notes in one place, with a date-browsable timeline of
 * what's been logged. Everything is on-device; the tiles log the selected day (today by
 * default) so a missed day can still be filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(selectedDay: Long, onSelectDay: (Long) -> Unit) {
    val store = LocalCycleStore.current
    val today = remember { LocalDate.now() }
    val selected = LocalDate.ofEpochDay(selectedDay)
    val iso = selected.toString()
    val isToday = selected == today

    var moodOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var headacheOpen by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<JournalEntry?>(null) }
    var editingHeadache by remember { mutableStateOf<HeadacheLog?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Mood", Theme.secondary, Modifier.weight(1f)) { moodOpen = true }
            Tile("Headache", Theme.accentText, Modifier.weight(1f)) { headacheOpen = true }
            Tile("Note", Theme.primary, Modifier.weight(1f)) { noteOpen = true }
        }

        TimelineCard(
            selected = selected,
            isToday = isToday,
            onToday = { onSelectDay(today.toEpochDay()) },
            onPickDate = { showDatePicker = true },
            onEditMood = { moodOpen = true },
            onEditHeadache = { editingHeadache = it },
            onEditNote = { editingNote = it },
        )

        MoodPatternsCard()

        store.headacheInsight?.let {
            HeadacheInsightCard(it.phase.label, it.count, it.total)
        }
    }

    if (moodOpen) MoodSheet(iso) { moodOpen = false }
    if (noteOpen) NoteSheet(iso, null) { noteOpen = false }
    if (headacheOpen) HeadacheSheet(iso, null) { headacheOpen = false }
    editingNote?.let { entry -> NoteSheet(iso, entry) { editingNote = null } }
    editingHeadache?.let { log -> HeadacheSheet(iso, log) { editingHeadache = null } }

    if (showDatePicker) {
        val todayMillis = today.toEpochDay() * MILLIS_PER_DAY
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDay * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                // Nothing can be logged in the future.
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Theme.surface),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onSelectDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                    showDatePicker = false
                }) { Text("Done", color = Theme.primary) }
            },
        ) {
            DatePicker(state = state, title = null, showModeToggle = false)
        }
    }
}

@Composable
private fun Tile(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .height(96.dp)
            .background(color.copy(alpha = 0.12f), shape)
            .border(1.dp, color.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (label) {
            "Mood" -> Icon(Icons.Filled.Face, null, tint = color, modifier = Modifier.size(26.dp))
            "Headache" -> HeartGlyph(color, 26.dp)
            else -> Icon(Icons.Filled.Edit, null, tint = color, modifier = Modifier.size(26.dp))
        }
        Text(label, Modifier.padding(top = 10.dp), style = serif(17).copy(color = Theme.ink))
    }
}

@Composable
private fun TimelineCard(
    selected: LocalDate,
    isToday: Boolean,
    onToday: () -> Unit,
    onPickDate: () -> Unit,
    onEditMood: () -> Unit,
    onEditHeadache: (HeadacheLog) -> Unit,
    onEditNote: (JournalEntry) -> Unit,
) {
    val store = LocalCycleStore.current
    val iso = selected.toString()
    val mood = store.mood(iso)
    val headaches = store.headaches(iso)
    val notes = store.journalEntries(iso)
    val empty = mood == null && headaches.isEmpty() && notes.isEmpty()

    Column(
        Modifier.cyclunaCard(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isToday) "Today" else selected.format(FULL_DATE),
                Modifier.weight(1f),
                style = serif(22).copy(color = Theme.ink),
            )
            if (!isToday) {
                IconButton(onClick = onToday) {
                    Icon(Icons.Filled.Clear, "Back to today", tint = Theme.inkSoft)
                }
            }
            IconButton(onClick = onPickDate) {
                Icon(Icons.Filled.DateRange, "Browse by day", tint = Theme.primary)
            }
        }

        if (empty) {
            Text(
                if (isToday) "Nothing logged today — tap a tile above to start."
                else "Nothing logged on this day.",
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                color = Theme.inkSoft,
                textAlign = TextAlign.Center,
            )
        } else {
            mood?.let {
                EntryRow(
                    emoji = MoodScale.emoji(it.mood),
                    title = "Mood · ${MoodScale.label(it.mood)}",
                    note = it.note,
                    onClick = onEditMood,
                )
            }
            headaches.forEach { h ->
                EntryRow(
                    emoji = "🤕",
                    title = headacheTitle(h),
                    note = headacheDetail(h),
                    onClick = { onEditHeadache(h) },
                )
            }
            notes.forEach { n ->
                EntryRow(emoji = "📝", title = "Note", note = n.text, onClick = { onEditNote(n) })
            }
        }
    }
}

@Composable
private fun EntryRow(emoji: String, title: String, note: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Theme.ink)
            if (note.isNotEmpty()) {
                Text(note, fontSize = 13.sp, color = Theme.inkSoft, maxLines = 3)
            }
        }
    }
}

private fun headacheTitle(h: HeadacheLog): String {
    val parts = mutableListOf("Headache")
    runCatching { LocalDateTime.parse(h.at) }.getOrNull()?.let { parts.add(it.format(CLOCK)) }
    parts.add(HeadacheScale.label(h.intensity))
    return parts.joinToString(" · ")
}

private fun headacheDetail(h: HeadacheLog): String {
    val bits = mutableListOf<String>()
    if (h.symptoms.isNotEmpty()) bits.add(h.symptoms.joinToString(", "))
    if (h.triggers.isNotEmpty()) bits.add("Triggers: " + h.triggers.joinToString(", "))
    if (h.note.isNotEmpty()) bits.add(h.note)
    return bits.joinToString(" · ")
}
