package app.cycluna.android.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.core.MoonEventCatalog
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val BLOOD = Color(0xFFC0473F)

/**
 * "The moon this week" — a horizontal strip of real rendered moon discs around today, in its
 * own card so the period calendar stays clean. Blood moons (bundled eclipses) are tinted
 * red. Educational, on-device, offline.
 */
@Composable
fun MoonWeekCard() {
    val store = LocalCycleStore.current
    val today = remember { LocalDate.now() }

    Column(
        Modifier.cyclunaCard(padding = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("The moon this week", style = serif(22).copy(color = Theme.ink))
        Text(
            "The lunar rhythm around today",
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic,
            color = Theme.inkSoft,
        )

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (-2..4).forEach { offset -> MoonDayCell(today.plusDays(offset.toLong()), today) }
        }
    }
}

@Composable
private fun MoonDayCell(date: LocalDate, today: LocalDate) {
    val store = LocalCycleStore.current
    val iso = date.toString()
    val isToday = date == today
    val event = MoonEventCatalog.event(iso)
    val isBlood = event?.type == "eclipse-total"
    val phaseName = event?.title ?: store.moonLabel(iso)
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .width(76.dp)
            .background(if (isToday) Theme.primary.copy(alpha = 0.07f) else Color.Transparent, shape)
            .border(1.dp, if (isToday) Theme.primary.copy(alpha = 0.25f) else Color.Transparent, shape)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoonDisc(
            illumination = store.moonIllumination(iso),
            waxing = store.moonWaxing(iso),
            modifier = Modifier.size(46.dp),
            lit = if (isBlood) BLOOD else Color(0xFFEAD59B),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                if (isToday) "TODAY" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
                color = if (isToday) Theme.primary else Theme.inkSoft,
            )
            Text("${date.dayOfMonth}", style = serif(17).copy(color = Theme.ink))
            Text(
                phaseName,
                fontSize = 10.5.sp,
                color = if (isBlood) Theme.phaseMenstrual else Theme.inkSoft,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/**
 * "Your cycle & the moon" — the signature educational insight linking the ~29.5-day cycle to
 * the ~29.53-day lunar month. Every value is computed on-device from the `Moon` core.
 *
 * The copy describes coincidence, never causation: research has not established a moon–mood
 * or moon–cycle link, and this card must not imply one.
 */
@Composable
fun MoonAlignmentCard() {
    val store = LocalCycleStore.current
    val today = remember { LocalDate.now() }

    val startedOn = store.lastPeriodMoonLabel.lowercase().replace(" moon", "")
    val atFullMoon = store.phaseAtNextFullMoon.lowercase()
    // Never "in 0 days" or "in 1 days".
    val fullMoonWhen = when (val d = store.daysUntilNextFullMoon) {
        0 -> "is today"
        1 -> "is tomorrow"
        else -> "is in $d days"
    }

    Row(
        Modifier.cyclunaCard(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MoonDisc(
            illumination = store.moonIllumination / 100.0,
            waxing = store.moonWaxing(today.toString()),
            modifier = Modifier.size(66.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Your cycle & the moon", style = serif(19).copy(color = Theme.ink))
            Text(
                "Your last period began on a $startedOn moon. " +
                    "The next full moon $fullMoonWhen, during your $atFullMoon phase.",
                fontSize = 13.5.sp,
                color = Theme.inkSoft,
            )
            Text(
                "Next full moon · ${store.nextFullMoonDate.format(MONTH_DAY)}",
                Modifier.padding(top = 2.dp),
                fontSize = 11.sp,
                color = Theme.accentText,
            )
        }
    }
}
