package app.cycluna.android.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.core.MoodScale
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * "How you've been" — a quick seven-day glance: one coloured mood bubble per day, a dashed
 * empty circle for days you didn't log, today ringed.
 */
@Composable
fun MoodStripCard() {
    val today = remember { LocalDate.now() }

    Column(
        Modifier.cyclunaCard(padding = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("How you've been", style = serif(22).copy(color = Theme.ink))
        Text(
            "Your mood over the last seven days",
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic,
            color = Theme.inkSoft,
        )

        // Seven cells share the card's width rather than scrolling. Fixed-width cells
        // overflowed by just enough to push today — the one cell that always matters — off
        // the right edge.
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (6 downTo 0).forEach { back ->
                MoodDayCell(today.minusDays(back.toLong()), today, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MoodDayCell(date: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val store = LocalCycleStore.current
    val mood = store.mood(date.toString())
    val isToday = date == today

    Column(
        modifier
            .background(
                if (isToday) Theme.primary.copy(alpha = 0.07f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (mood != null) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(MoodScale.color(mood.mood).copy(alpha = 0.22f), CircleShape)
                )
                Text(MoodScale.emoji(mood.mood), fontSize = 19.sp)
            } else {
                Canvas(Modifier.size(36.dp)) {
                    drawCircle(
                        color = Theme.inkSoft.copy(alpha = 0.3f),
                        radius = size.minDimension / 2f - 1.dp.toPx(),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(3.dp.toPx(), 3.dp.toPx())
                            ),
                        ),
                    )
                }
            }
            if (isToday) {
                Canvas(Modifier.size(36.dp)) {
                    drawCircle(
                        color = Theme.primary,
                        radius = size.minDimension / 2f - 1.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        Text(
            if (isToday) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            fontSize = 9.sp,
            maxLines = 1,
            color = if (isToday) Theme.primary else Theme.inkSoft,
        )
        Text(
            "${date.dayOfMonth}",
            fontSize = 12.sp,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isToday) Theme.primary else Theme.ink,
        )
    }
}
