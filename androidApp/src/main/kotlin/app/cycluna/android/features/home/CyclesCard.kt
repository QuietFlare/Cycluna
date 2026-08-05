package app.cycluna.android.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.core.TrackingState
import app.cycluna.android.designsystem.DropGlyph
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val FULL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * Rows shown per section. The past section is otherwise unbounded — it grew one row per
 * logged period, so a year of tracking buried the rest of Home under it. Both sections use
 * this so the card stays symmetrical.
 */
private const val ROWS_PER_SECTION = 3

/**
 * "Your cycles" — real logged history (only what the user actually logged, never
 * fabricated) plus the next predicted cycles with their fertile windows.
 */
@Composable
fun CyclesCard() {
    val store = LocalCycleStore.current
    var confirmReset by remember { mutableStateOf(false) }

    val starts = store.periodStarts.sortedDescending()
    // Map first, THEN truncate: each row's length is measured against the start that
    // follows it, so trimming the source list first would mislabel the last row.
    val past = starts.mapIndexed { i, s ->
        if (i == 0) s to "Current cycle"
        else s to "${ChronoUnit.DAYS.between(s, starts[i - 1])}-day cycle"
    }.take(ROWS_PER_SECTION)

    // An overdue or lost cycle makes every downstream date guesswork: these would be
    // measured from a period that hasn't arrived.
    val predicted: List<Pair<LocalDate, String>> =
        if (store.tracking != TrackingState.NORMAL) emptyList()
        else (1..ROWS_PER_SECTION).map { i ->
            val base = store.currentCycleStart
            val cl = store.cycleLength.toLong()
            // Each row pairs a predicted period with the fertile window that PRECEDES it —
            // the window of the cycle one back. The dates come from the core so they cannot
            // drift from the hero tile or the calendar.
            val period = base.plusDays(cl * i)
            val window = store.fertileWindowForCycleStarting(base.plusDays(cl * (i - 1)))
            period to "${window.first.format(MONTH_DAY)} – ${window.second.format(MONTH_DAY)}"
        }

    Column(
        Modifier.cyclunaCard(padding = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your cycles", Modifier.weight(1f), style = serif(22).copy(color = Theme.ink))
            TextButton(onClick = { confirmReset = true }) {
                Text("Reset all", fontSize = 14.sp, color = Theme.inkSoft)
            }
        }

        if (past.isNotEmpty()) {
            SectionLabel("PAST CYCLES")
            past.forEachIndexed { i, (date, label) ->
                CycleRow(date, label, drop = false)
                if (i < past.size - 1) RowDivider()
            }
        }

        SectionLabel("NEXT PREDICTED CYCLES", Modifier.padding(top = 4.dp))
        if (predicted.isEmpty()) {
            // Every predicted date is measured from a period that hasn't arrived, so the
            // whole list would be fiction. Say why instead of showing invented dates.
            Text(
                if (store.tracking == TrackingState.LATE) {
                    "Your period is late. Predictions start again when you log it."
                } else {
                    "Predictions start again when you log your period."
                },
                fontSize = 13.sp,
                color = Theme.inkSoft,
            )
        } else {
            predicted.forEachIndexed { i, (date, fertile) ->
                CycleRow(date, "Fertile window $fertile", drop = true)
                if (i < predicted.size - 1) RowDivider()
            }
        }

        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryTile("CYCLE LENGTH", "${store.cycleLength} days", Modifier.weight(1f))
            SummaryTile("PERIOD LENGTH", "${store.periodLength} days", Modifier.weight(1f))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Theme.surface,
            title = { Text("Reset all cycle data?", style = serif(20).copy(color = Theme.ink)) },
            text = {
                Text(
                    "This erases everything and returns to setup.",
                    fontSize = 14.sp,
                    color = Theme.inkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    store.deleteAllData()
                }) { Text("Reset everything", color = Theme.phaseMenstrual) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel", color = Theme.inkSoft)
                }
            },
        )
    }
}

@Composable
private fun CycleRow(date: LocalDate, subtitle: String, drop: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                date.format(FULL_DATE),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.ink,
            )
            Text(subtitle, fontSize = 14.sp, color = Theme.inkSoft)
        }
        if (drop) DropGlyph(Theme.phaseMenstrual, 16.dp, filled = false)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = Theme.inkSoft.copy(alpha = 0.12f))
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 11.sp, letterSpacing = 1.0.sp, color = Theme.inkSoft)
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Theme.background, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
            color = Theme.inkSoft,
            textAlign = TextAlign.Center,
        )
        Text(value, style = serif(22).copy(color = Theme.ink))
    }
}
