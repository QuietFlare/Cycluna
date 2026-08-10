package app.cycluna.android.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.core.TrackingState
import app.cycluna.android.designsystem.DropGlyph
import app.cycluna.android.designsystem.HeartGlyph
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import app.cycluna.android.features.phases.PhaseContent
import app.cycluna.android.features.phases.PhaseIcon

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

    Column(
        Modifier.cyclunaCard(padding = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        MoonWheel(
            cycleDay = store.cycleDay,
            cycleLength = store.cycleLength,
            periodLength = store.periodLength,
            phaseLabel = store.phaseLabel,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Once tracking is unclear the fertile window is guesswork — showing a confident
            // date range would be the misleading part, so it goes away entirely.
            if (store.showsFertileWindow) {
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

        Text(
            store.fertileContext,
            fontSize = 14.sp,
            color = if (store.tracking == TrackingState.NORMAL) Theme.inkSoft else Theme.accentText,
            textAlign = TextAlign.Center,
        )

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
