package app.cycluna.android.features.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.LocalSettingsStore
import app.cycluna.android.core.AppSettings
import app.cycluna.android.core.MoodScale
import app.cycluna.android.designsystem.DropGlyph
import app.cycluna.android.designsystem.HeartGlyph
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
private val MONTH_DAY = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)

/**
 * Month calendar. Period days tint pink, fertile days show a heart, and every other day
 * shows its cycle-day number. The markers come from the core so they cannot drift from the
 * hero tile.
 */
@Composable
fun CalendarCard() {
    val store = LocalCycleStore.current
    val settings by LocalSettingsStore.current.settings.collectAsStateWithLifecycle(AppSettings())
    val today = remember { LocalDate.now() }
    var monthAnchor by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toEpochDay()) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }

    val firstOfMonth = LocalDate.ofEpochDay(monthAnchor).withDayOfMonth(1)
    val selected = LocalDate.ofEpochDay(selectedDay)
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    Column(
        Modifier.cyclunaCard(padding = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your cycle calendar", style = serif(22).copy(color = Theme.ink))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthAnchor = firstOfMonth.minusMonths(1).toEpochDay() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = Theme.primary)
            }
            Text(
                firstOfMonth.format(MONTH_YEAR),
                Modifier.weight(1f),
                color = Theme.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { monthAnchor = firstOfMonth.plusMonths(1).toEpochDay() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = Theme.primary)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            orderedWeekdays(firstDayOfWeek).forEach { label ->
                Text(
                    label,
                    Modifier.weight(1f).clearAndSetSemantics {},
                    fontSize = 11.sp,
                    color = Theme.inkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Hand-rolled rows rather than a LazyVGrid: the whole month is on screen at once,
        // so laziness buys nothing and a nested lazy layout inside the page's scroll is a
        // measurement problem waiting to happen.
        val leading = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val daysInMonth = firstOfMonth.lengthOfMonth()
        val cells: List<LocalDate?> = buildList {
            repeat(leading) { add(null) }
            for (d in 0 until daysInMonth) add(firstOfMonth.plusDays(d.toLong()))
            while (size % 7 != 0) add(null)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { date ->
                        if (date == null) {
                            Box(Modifier.weight(1f).height(44.dp))
                        } else {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selected,
                                fertility = settings.fertility,
                                onClick = { selectedDay = date.toEpochDay() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        Legend(fertility = settings.fertility)
        SelectedPanel(selected, fertility = settings.fertility)
    }
}

private data class DayStyle(
    val bg: Color,
    val marker: String,
    val iconColor: Color,
    val label: String,
)

private fun styleFor(marker: String): DayStyle = when (marker) {
    "period" -> DayStyle(Theme.phaseMenstrual.copy(alpha = 0.28f), "drop-fill", Theme.phaseMenstrual, "period day")
    "predicted-period" -> DayStyle(Theme.phaseMenstrual.copy(alpha = 0.10f), "drop", Theme.phaseMenstrual.copy(alpha = 0.7f), "predicted period")
    "fertile-peak" -> DayStyle(Theme.phaseOvulatory.copy(alpha = 0.45f), "heart-fill", Theme.phaseOvulatory, "peak fertile day")
    "fertile-high" -> DayStyle(Theme.phaseOvulatory.copy(alpha = 0.28f), "heart-fill", Theme.phaseOvulatory.copy(alpha = 0.9f), "high fertility")
    "fertile-medium" -> DayStyle(Theme.phaseOvulatory.copy(alpha = 0.15f), "heart", Theme.phaseOvulatory.copy(alpha = 0.85f), "fertile")
    else -> DayStyle(Color.Transparent, "", Color.Transparent, "")
}

/**
 * Friendly note for the selected-day panel, keyed off the same marker. A logged period gets
 * no note — the pink cell with its drop already says it, and the red text read as alarm.
 */
private fun dayNote(marker: String): Pair<String, Color>? = when (marker) {
    "predicted-period" -> "Predicted period" to Theme.inkSoft
    "fertile-peak" -> "Peak fertile day" to Theme.accentText
    "fertile-high" -> "High fertility" to Theme.accentText
    "fertile-medium" -> "Fertile window" to Theme.accentText
    else -> null
}

/** Fertile-day markers are withheld entirely when fertility insights are off. */
private fun visibleMarker(marker: String, fertility: Boolean): String =
    if (!fertility && marker.startsWith("fertile")) "none" else marker

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    fertility: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = LocalCycleStore.current
    val iso = date.toString()
    val style = styleFor(visibleMarker(store.dayMarker(iso), fertility))
    val cycleDay = store.linearCycleDay(iso)
    val shape = RoundedCornerShape(10.dp)

    val spoken = buildString {
        append(date.format(MONTH_DAY))
        if (style.label.isNotEmpty()) append(", ${style.label}")
        else if (cycleDay > 0) append(", cycle day $cycleDay")
        if (store.hasLog(iso)) append(", has a log")
    }

    Box(
        modifier
            .height(44.dp)
            .background(style.bg, shape)
            .border(1.5.dp, if (isSelected) Theme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 14.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = Theme.ink,
            )
            // Fixed height so a glyph and a cycle-day number occupy the same slot: without
            // it the two kinds of cell centre differently and every row reads as ragged.
            Box(Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                when (style.marker) {
                    "drop-fill" -> DropGlyph(style.iconColor, 9.dp)
                    "drop" -> DropGlyph(style.iconColor, 9.dp, filled = false)
                    "heart-fill" -> HeartGlyph(style.iconColor, 9.dp)
                    "heart" -> HeartGlyph(style.iconColor, 9.dp, filled = false)
                    else -> Text(
                        if (cycleDay > 0) "$cycleDay" else " ",
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        color = Theme.inkSoft.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // A small dot on days with a log — coloured by mood if one exists, neutral otherwise.
        logDotColor(iso)?.let { dot ->
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(6.dp)
                    .background(dot, CircleShape)
            )
        }
    }
}

@Composable
private fun logDotColor(iso: String): Color? {
    val store = LocalCycleStore.current
    store.mood(iso)?.let { return MoodScale.color(it.mood) }
    if (store.hasHeadache(iso) || store.hasNote(iso)) return Theme.inkSoft
    return null
}

@Composable
private fun Legend(fertility: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(Theme.phaseMenstrual.copy(alpha = 0.55f), "Period")
            LegendDot(Theme.phaseMenstrual.copy(alpha = 0.22f), "Predicted")
            if (fertility) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.3f, 0.5f, 0.75f).forEach {
                        Box(Modifier.size(9.dp).background(Theme.phaseOvulatory.copy(alpha = it), CircleShape))
                    }
                    Text("Fertile", fontSize = 11.sp, color = Theme.inkSoft)
                }
            }
        }
        // No key for the cycle-day numbers or the logged dot — "14 Cycle day" read as a
        // puzzle, and tapping a day already explains itself in the panel below.
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, fontSize = 11.sp, color = Theme.inkSoft)
    }
}

@Composable
private fun SelectedPanel(selected: LocalDate, fertility: Boolean) {
    val store = LocalCycleStore.current
    val iso = selected.toString()
    val phase = store.phaseForDate(iso)
    val note = dayNote(visibleMarker(store.dayMarker(iso), fertility))
    val mood = store.mood(iso)
    val headacheCount = store.headaches(iso).size
    val noteCount = store.journalEntries(iso).size

    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            selected.format(LONG_DAY).uppercase(),
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = Theme.inkSoft,
        )
        // The phase alone — the cycle-day number already sits in the day cell itself, and
        // "Cycle day 1" beside "Menstrual phase" read as a puzzle, not a summary.
        if (phase.isNotEmpty()) {
            Text("$phase phase", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Theme.ink)
        }
        note?.let { (text, color) ->
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
        }

        if (mood != null || headacheCount > 0 || noteCount > 0) {
            Row(
                Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                mood?.let { LoggedChip("${MoodScale.emoji(it.mood)} ${MoodScale.label(it.mood)}") }
                if (headacheCount > 0) {
                    LoggedChip("$headacheCount headache${if (headacheCount == 1) "" else "s"}")
                }
                if (noteCount > 0) {
                    LoggedChip("$noteCount note${if (noteCount == 1) "" else "s"}")
                }
            }
        }
    }
}

@Composable
private fun LoggedChip(text: String) {
    Text(
        text,
        Modifier
            .background(Theme.background, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 11.sp,
        color = Theme.ink,
    )
}

private fun orderedWeekdays(firstDayOfWeek: DayOfWeek): List<String> =
    (0..6).map {
        firstDayOfWeek.plus(it.toLong())
            .getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
    }
