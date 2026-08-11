package app.cycluna.android.features.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.LocalSettingsStore
import app.cycluna.android.core.AppSettings
import app.cycluna.android.core.TrackingState
import app.cycluna.android.designsystem.DropGlyph
import app.cycluna.android.designsystem.HeartGlyph
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import app.cycluna.android.features.phases.PhaseContent
import app.cycluna.android.features.phases.PhaseIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun HomeScreen() {
    val store = LocalCycleStore.current

    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Header()
        HeroCard()
        HighlightCard()
        MoodStripCard()
        CalendarCard()
        CyclesCard()
        MoonWeekCard()
        MoonAlignmentCard()
    }
}

@Composable
private fun Header() {
    val store = LocalCycleStore.current
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Hello, ${store.displayName.ifEmpty { "beautiful" }}",
            style = serif(34).copy(color = Theme.ink, textAlign = TextAlign.Center),
        )
        Text(store.todayLong, fontSize = 14.sp, color = Theme.inkSoft)
    }
}

@Composable
private fun HeroCard() {
    val store = LocalCycleStore.current
    val settings by LocalSettingsStore.current.settings.collectAsStateWithLifecycle(AppSettings())

    var adjustOpen by remember { mutableStateOf(false) }

    // The dot sweeps to its new place when a date changes instead of teleporting — the
    // feedback that makes an edit in the sheet feel understood.
    val dayProgress by animateFloatAsState(
        targetValue = store.cycleDay.toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "wheelDay",
    )

    val wheelPress = remember { MutableInteractionSource() }
    val pressed by wheelPress.collectIsPressedAsState()
    val wheelScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "wheelScale",
    )

    Column(
        Modifier.cyclunaCard(padding = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // The wheel is the way in to fixing the dates it draws: tap it (or the chip, which
        // is the visible hint that this is possible) to adjust the last period start.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .graphicsLayer { scaleX = wheelScale; scaleY = wheelScale }
                .clickable(
                    interactionSource = wheelPress,
                    indication = null,
                    onClickLabel = "Adjust when your last period started",
                ) { adjustOpen = true },
        ) {
            MoonWheel(
                cycleDay = store.cycleDay,
                cycleLength = store.cycleLength,
                periodLength = store.periodLength,
                phaseLabel = store.phaseLabel,
                dayProgress = dayProgress,
                showOvulation = settings.fertility,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Theme.primary.copy(alpha = 0.10f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = Theme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    "Adjust dates",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Theme.primary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Once tracking is unclear the fertile window is guesswork — showing a confident
            // date range would be the misleading part, so it goes away entirely. It also
            // hides with fertility insights switched off.
            if (store.showsFertileWindow && settings.fertility) {
                InfoTile(
                    icon = { HeartGlyph(Theme.phaseOvulatory, 16.dp) },
                    eyebrow = "FERTILE WINDOW",
                    value = store.fertileWindowText,
                    modifier = Modifier.weight(1f),
                )
            }
            InfoTile(
                icon = { DropGlyph(Theme.phaseMenstrual, 16.dp) },
                eyebrow = "NEXT PERIOD",
                value = store.nextPeriodShort,
                modifier = Modifier.weight(1f),
            )
        }

        // Speaks only when the tiles can't: a late period, or tracking gone unclear.
        if (store.fertileContext.isNotEmpty()) {
            Text(
                store.fertileContext,
                fontSize = 14.sp,
                color = Theme.accentText,
                textAlign = TextAlign.Center,
            )
        }

        // While the logged period is on, offering "Start period today" only invites an
        // accidental second log that corrupts the cycle history — a quiet confirmation
        // replaces it. The button returns the moment it's needed again, including the
        // late/unclear states, which is exactly when logging matters most.
        if (store.isInLoggedPeriod) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 14.dp),
            ) {
                DropGlyph(Theme.phaseMenstrual, 16.dp)
                Text(
                    store.periodStartedText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Theme.inkSoft,
                )
            }
        } else {
            Button(
                onClick = { store.startPeriod() },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Theme.primary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 26.dp,
                    vertical = 14.dp,
                ),
                modifier = Modifier.semantics { onClick(label = "Logs a new period starting today", action = null) },
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DropGlyph(Color.White, 16.dp)
                    Text("Start period today", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }

    if (adjustOpen) {
        PeriodDateDialog(
            question = "When did your last period start?",
            initial = store.lastPeriodStart,
            preview = store::previewLine,
            onConfirm = { store.lastPeriodStart = it },
            onDismiss = { adjustOpen = false },
        )
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * The shared period-date dialog behind both Home entry points. Past-only, like every period
 * date in the app: a future anchor makes all the derived values nonsense. The preview line
 * updates as a date is picked, so the choice's meaning is visible before it's saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodDateDialog(
    question: String,
    initial: LocalDate,
    preview: (LocalDate) -> String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    // The newest selectable day. Defaults to today; the log-past flow passes the day
    // before the current anchor, so a backfilled entry can only ever be history.
    latest: LocalDate = LocalDate.now(),
) {
    val latestMillis = latest.toEpochDay() * MILLIS_PER_DAY
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latestMillis
            override fun isSelectableYear(year: Int) = year <= latest.year
        },
    )
    val selected = state.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
    } ?: initial

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = Theme.surface),
        confirmButton = {
            TextButton(onClick = { onConfirm(selected); onDismiss() }) {
                Text("Save", color = Theme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Theme.inkSoft) }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DatePicker(
                state = state,
                title = {
                    Text(
                        question,
                        style = serif(18).copy(color = Theme.ink),
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                    )
                },
                // No headline: Material's default echoes the current selection in large
                // type ("Jul 4, 2026"), which read as a meaningful date rather than just
                // the pre-selected guess. iOS shows none either — the grid is the truth.
                headline = null,
                showModeToggle = false,
            )
            Text(
                preview(selected),
                fontSize = 12.sp,
                color = Theme.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun InfoTile(
    icon: @Composable () -> Unit,
    eyebrow: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(Theme.background, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(eyebrow, fontSize = 11.sp, letterSpacing = 0.6.sp, color = Theme.inkSoft)
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Theme.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HighlightCard() {
    val store = LocalCycleStore.current
    // The phase's own mark and colour, from the same source the Phases tab reads, so the
    // wording here can never drift from the phase cards.
    val phase = PhaseContent.of(store.phaseLabel)

    Row(
        Modifier.cyclunaCard(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 3.dp)) { PhaseIcon(phase) }
        Text(
            PhaseContent.blurb(store.phaseLabel),
            style = serif(20).copy(color = Theme.ink),
        )
    }
}
