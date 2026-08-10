package app.cycluna.android.features.journal

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val SYMPTOM_OPTIONS = listOf(
    "Throbbing", "One-sided", "Nausea", "Light sensitivity",
    "Sound sensitivity", "Visual aura", "Tingling",
)
private val TRIGGER_OPTIONS = listOf(
    "Poor sleep", "Skipped meal", "Stress", "Dehydration",
    "Caffeine", "Alcohol", "Weather", "Strong smells", "Hormones",
)

/** UI-level cap only. The core stays permissive so an imported entry is never truncated. */
private const val MAX_NOTE_LENGTH = 2_000

// ------------------------------------------------------------------------------------
// Mood
// ------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSheet(dateIso: String, onDismiss: () -> Unit) {
    val store = LocalCycleStore.current
    val existing = store.mood(dateIso)
    var vibe by remember { mutableStateOf(existing?.mood) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.surface,
    ) {
        SheetFrame(
            title = "Mood",
            saveEnabled = vibe != null,
            onCancel = onDismiss,
            onSave = {
                vibe?.let { store.logMood(it, note.trim(), dateIso) }
                onDismiss()
            },
        ) {
            SectionLabel("How's the vibe?")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { v ->
                    val chosen = vibe == v
                    val scale by animateFloatAsState(if (chosen) 1.12f else 1f, label = "mood-$v")
                    Column(
                        Modifier
                            .weight(1f)
                            .background(
                                if (chosen) Theme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(role = Role.RadioButton) { vibe = v }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            MoodScale.emoji(v),
                            Modifier
                                .scale(scale)
                                // Once a value is picked the others dim rather than vanish:
                                // the scale has to stay readable while you compare it.
                                .alpha(if (vibe == null || chosen) 1f else 0.4f),
                            fontSize = 28.sp,
                        )
                        Text(
                            MoodScale.label(v),
                            fontSize = 11.sp,
                            color = if (chosen) Theme.primary else Theme.inkSoft,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            SectionLabel("Note (optional)")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("What's on your mind?", color = Theme.inkSoft) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            )

            if (existing != null) {
                TextButton(onClick = { store.clearMood(dateIso); onDismiss() }) {
                    Text("Remove mood", color = Theme.phaseMenstrual)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// Headache
// ------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadacheSheet(dateIso: String, existing: HeadacheLog?, onDismiss: () -> Unit) {
    val store = LocalCycleStore.current
    var intensity by remember { mutableIntStateOf(existing?.intensity ?: 2) }
    val symptoms = remember { existing?.symptoms.orEmpty().toMutableStateList() }
    val triggers = remember { existing?.triggers.orEmpty().toMutableStateList() }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    val initialTime = remember(existing) {
        runCatching { LocalDateTime.parse(existing?.at).toLocalTime() }.getOrDefault(LocalTime.of(9, 0))
    }
    // Follow the phone's own 12/24-hour setting rather than forcing one.
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val timeState = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = use24Hour)

    // Collapsed until asked for. TimeInput grabs focus the moment it composes, throwing the
    // numeric keyboard over the symptom and trigger chips on every open; the onset time is
    // usually near enough already, so it stays a tappable summary until it isn't.
    var editingTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.surface,
    ) {
        SheetFrame(
            title = if (existing == null) "Headache" else "Edit headache",
            saveEnabled = true,
            onCancel = onDismiss,
            onSave = {
                // Combine the browsed day with the chosen time.
                val day = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
                val at = day.atTime(timeState.hour, timeState.minute)
                if (existing == null) {
                    store.addHeadache(intensity, symptoms.toList(), triggers.toList(), note.trim(), at)
                } else {
                    store.updateHeadache(existing.id, intensity, symptoms.toList(), triggers.toList(), note.trim(), at)
                }
                onDismiss()
            },
        ) {
            SectionLabel("Intensity")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..3).forEach { v ->
                    SegmentedButton(
                        selected = intensity == v,
                        onClick = { intensity = v },
                        shape = SegmentedButtonDefaults.itemShape(v - 1, 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Theme.primary.copy(alpha = 0.15f),
                            activeContentColor = Theme.primary,
                            inactiveContentColor = Theme.inkSoft,
                        ),
                    ) { Text(HeadacheScale.label(v)) }
                }
            }

            SectionLabel("Time")
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Theme.background, RoundedCornerShape(12.dp))
                    .clickable { editingTime = true }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Onset", Modifier.weight(1f), fontSize = 16.sp, color = Theme.ink)
                Text(
                    formatTime(timeState.hour, timeState.minute, use24Hour),
                    Modifier
                        .background(Theme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 16.sp,
                    color = Theme.ink,
                )
            }

            SectionLabel("Symptoms")
            ChipWrap(SYMPTOM_OPTIONS, symptoms)

            SectionLabel("Possible triggers")
            ChipWrap(TRIGGER_OPTIONS, triggers)

            SectionLabel("Note (optional)")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Anything to remember", color = Theme.inkSoft) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            )

            if (existing != null) {
                TextButton(onClick = { store.deleteHeadache(existing.id); onDismiss() }) {
                    Text("Delete this episode", color = Theme.phaseMenstrual)
                }
            }
        }
    }

    // The dial in a dialog, rather than a text field inline: the inline numeric input takes
    // focus the moment it appears, so the keyboard covered the symptom and trigger chips and
    // the sheet had to be scrolled blind. The dial needs no keyboard at all.
    if (editingTime) {
        AlertDialog(
            onDismissRequest = { editingTime = false },
            containerColor = Theme.surface,
            title = { Text("Onset time", style = serif(20).copy(color = Theme.ink)) },
            text = {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = Theme.primary,
                        containerColor = Theme.background,
                        periodSelectorSelectedContainerColor = Theme.primary.copy(alpha = 0.15f),
                        timeSelectorSelectedContainerColor = Theme.primary.copy(alpha = 0.15f),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { editingTime = false }) {
                    Text("Done", color = Theme.primary)
                }
            },
        )
    }
}

/**
 * A wrapping set of multi-select chips, two equal columns wide — the same tidy grid the iOS
 * sheet uses. Ragged widths made a list of nine triggers hard to scan.
 */
@Composable
private fun ChipWrap(options: List<String>, selection: MutableList<String>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        options.forEach { option ->
            val on = option in selection
            Text(
                option,
                Modifier
                    .weight(1f)
                    .background(if (on) Theme.primary else Theme.background, CircleShape)
                    .clickable(role = Role.Checkbox) {
                        if (on) selection.remove(option) else selection.add(option)
                    }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                fontSize = 13.sp,
                color = if (on) Color.White else Theme.ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatTime(hour: Int, minute: Int, use24Hour: Boolean): String {
    if (use24Hour) return "%02d:%02d".format(hour, minute)
    val suffix = if (hour < 12) "AM" else "PM"
    val h = when {
        hour % 12 == 0 -> 12
        else -> hour % 12
    }
    return "%d:%02d %s".format(h, minute, suffix)
}

// ------------------------------------------------------------------------------------
// Note
// ------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(dateIso: String, existing: JournalEntry?, onDismiss: () -> Unit) {
    val store = LocalCycleStore.current
    var text by remember { mutableStateOf(existing?.text ?: "") }
    val remaining = MAX_NOTE_LENGTH - text.length

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.surface,
    ) {
        SheetFrame(
            title = if (existing == null) "New note" else "Edit note",
            saveEnabled = true,
            onCancel = onDismiss,
            onSave = {
                val t = text.trim()
                if (t.isNotEmpty()) {
                    if (existing == null) store.addJournalEntry(t, dateIso)
                    else store.updateJournalEntry(existing.id, t, dateIso)
                }
                onDismiss()
            },
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_NOTE_LENGTH) text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                textStyle = serif(16).copy(color = Theme.ink),
            )
            // Only surface the limit as it comes into view — a counter on an empty note is
            // just clutter.
            if (remaining <= 200) {
                Text(
                    "$remaining characters left",
                    Modifier.fillMaxWidth(),
                    fontSize = 11.sp,
                    color = if (remaining <= 0) Theme.phaseMenstrual else Theme.inkSoft,
                    textAlign = TextAlign.End,
                )
            }
            if (existing != null) {
                TextButton(onClick = { store.deleteJournalEntry(existing.id); onDismiss() }) {
                    Text("Delete", color = Theme.phaseMenstrual)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// Shared sheet chrome
// ------------------------------------------------------------------------------------

@Composable
private fun SheetFrame(
    title: String,
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // The header stays put while the fields scroll under it, so Save is always reachable
        // — the headache sheet is taller than the sheet itself on a small phone.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel", color = Theme.inkSoft) }
            Text(
                title,
                Modifier.weight(1f),
                style = serif(20).copy(color = Theme.ink, textAlign = TextAlign.Center),
            )
            TextButton(onClick = onSave, enabled = saveEnabled) {
                Text(
                    "Save",
                    color = if (saveEnabled) Theme.primary else Theme.inkSoft.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(top = 6.dp),
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        color = Theme.accentText,
    )
}

/** "Headaches & your cycle" — the hormonal-cluster insight, only shown when confident. */
@Composable
fun HeadacheInsightCard(phaseLabel: String, count: Int, total: Int) {
    Row(
        Modifier.cyclunaCard(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp)) { HeartGlyph(Theme.secondary, 18.dp) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Headaches & your cycle", style = serif(19).copy(color = Theme.ink))
            Text(
                "Your headaches tend to cluster in your ${phaseWord(phaseLabel)}, " +
                    "a common hormonal pattern ($count of $total so far).",
                fontSize = 13.sp,
                color = Theme.inkSoft,
            )
        }
    }
}

/** Phase label → the phrase the sentences use. */
fun phaseWord(phaseLabel: String): String = when (phaseLabel) {
    "Ovulatory" -> "ovulation window"
    "Follicular" -> "follicular phase"
    "Luteal" -> "luteal phase"
    else -> "period"
}
