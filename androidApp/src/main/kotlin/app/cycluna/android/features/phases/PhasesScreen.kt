package app.cycluna.android.features.phases

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.serif
import java.time.LocalDate

@Composable
fun PhasesScreen() {
    val store = LocalCycleStore.current

    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HormoneChartCard()
        phasesFromNow(store.phaseLabel).forEach { PhaseCard(it) }
    }
}

/**
 * The four phases rotated so the one you're in leads.
 *
 * Rotated rather than reordered: the cycle runs menstrual → follicular → ovulatory → luteal
 * and wraps, so starting at "now" and continuing round keeps what comes next actually next.
 * Plucking the current phase to the top would scramble that.
 */
private fun phasesFromNow(phaseLabel: String): List<PhaseContent> {
    val all = PhaseContent.entries
    val i = all.indexOfFirst { it.key == phaseLabel }
    if (i < 0) return all
    return all.drop(i) + all.take(i)
}

@Composable
private fun PhaseCard(phase: PhaseContent) {
    val store = LocalCycleStore.current
    val isNow = phase.key == store.phaseLabel
    val shape = RoundedCornerShape(22.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .background(phase.color.copy(alpha = 0.14f), shape)
            .border(1.5.dp, if (isNow) phase.color.copy(alpha = 0.55f) else Color.Transparent, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(phase.emoji, fontSize = 30.sp)
            Text(
                phase.eyebrow.uppercase(),
                Modifier.padding(start = 10.dp, top = 4.dp),
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = phase.color,
            )
            if (isNow) {
                Text(
                    "NOW",
                    Modifier
                        .padding(start = 10.dp, top = 2.dp)
                        .background(phase.color, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    phase.dateRangeText(store.cycleLength, store.periodLength, cycleStartFor(phase)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.ink,
                    textAlign = TextAlign.End,
                )
                Text(
                    phase.rangeText(store.cycleLength, store.periodLength),
                    fontSize = 11.sp,
                    color = Theme.inkSoft,
                )
            }
        }
        Text(phase.key, style = serif(30).copy(color = phase.color))
        Text(phase.blurb, fontSize = 15.sp, color = Theme.ink.copy(alpha = 0.85f))
    }
}

/**
 * Which cycle a card's dates belong to.
 *
 * The list is rotated to start at the phase you're in, so everything after it is what's
 * *coming*. A phase that sits earlier in the cycle's natural order has already been and gone
 * this month, so it is dated from the next cycle — otherwise the cards read forward
 * (Luteal → Menstrual) while their dates run backwards (Aug 3 → Jul 18).
 */
@Composable
private fun cycleStartFor(phase: PhaseContent): LocalDate {
    val store = LocalCycleStore.current
    val all = PhaseContent.entries
    val now = all.indexOfFirst { it.key == store.phaseLabel }
    val i = all.indexOf(phase)
    return if (now >= 0 && i < now) {
        store.currentCycleStart.plusDays(store.cycleLength.toLong())
    } else {
        store.currentCycleStart
    }
}
