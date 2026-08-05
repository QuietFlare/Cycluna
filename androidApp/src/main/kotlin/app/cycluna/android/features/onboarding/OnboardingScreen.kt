package app.cycluna.android.features.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.drawCrescent
import app.cycluna.android.designsystem.serif
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * First-run onboarding — four unhurried steps: an atmospheric hero, a trust story, then two
 * warm personalisation steps (last period, cycle rhythm).
 *
 * The app never fabricates a cycle; finishing here logs the user's real first period, which
 * flips `hasLoggedPeriod` and reveals the tabs.
 */
@Composable
fun OnboardingScreen() {
    val store = LocalCycleStore.current
    val today = remember { LocalDate.now() }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var lastPeriodEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var cycleLength by rememberSaveable { mutableIntStateOf(28) }
    var periodLength by rememberSaveable { mutableIntStateOf(5) }

    val lastPeriod = LocalDate.ofEpochDay(lastPeriodEpochDay)

    // Predictive back walks the steps rather than leaving the app mid-setup.
    BackHandler(enabled = step > 0) { step-- }

    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.backgroundGradient)
    ) {
        StarField()

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "onboarding-step",
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 26.dp, vertical = 24.dp),
        ) { current ->
            when (current) {
                0 -> HeroStep(onNext = { step = 1 })
                1 -> TrustStep(onNext = { step = 2 })
                2 -> LastPeriodStep(
                    today = today,
                    selected = lastPeriod,
                    onSelect = { lastPeriodEpochDay = it.toEpochDay() },
                    onNext = { step = 3 },
                    onEstimate = { daysAgo ->
                        lastPeriodEpochDay = today.minusDays(daysAgo.toLong()).toEpochDay()
                        step = 3
                    },
                )
                else -> RhythmStep(
                    cycleLength = cycleLength,
                    periodLength = periodLength,
                    onCycleLength = { cycleLength = it },
                    onPeriodLength = { periodLength = it },
                    onFinish = { store.completeOnboarding(lastPeriod, cycleLength, periodLength) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// Step 1 · Hero
// ------------------------------------------------------------------------------------

@Composable
private fun HeroStep(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        GlowMoon(sizeDp = 156)
        Text(
            "Welcome to\nCycluna",
            style = serif(38).copy(color = Theme.ink, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 26.dp),
        )
        Text(
            "Your rhythm, in tune with\nthe moon and your body.",
            fontSize = 15.sp,
            color = Theme.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        ProgressDots(current = 0)
        PrimaryButton("Begin", onNext, Modifier.padding(top = 18.dp))
    }
}

// ------------------------------------------------------------------------------------
// Step 2 · Trust
// ------------------------------------------------------------------------------------

@Composable
private fun TrustStep(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Eyebrow("YOURS, AND ONLY YOURS")
        Text(
            "Private by design",
            style = serif(34).copy(color = Theme.ink),
            modifier = Modifier.padding(top = 6.dp),
        )

        Column(Modifier.padding(top = 22.dp)) {
            FeatureRow(
                { Icon(Icons.Filled.Lock, null, tint = Theme.accentText) },
                "On your phone, full stop",
                "No account, no cloud, no tracking. Your data never leaves this device.",
            )
            HorizontalDivider(color = Theme.inkSoft.copy(alpha = 0.18f))
            FeatureRow(
                {
                    Canvas(
                        Modifier
                            .size(19.dp)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    ) { drawCrescent(Brush.linearGradient(listOf(Theme.accentText, Theme.accentText))) }
                },
                "Moon-synced phases",
                "See your cycle and the lunar phase side by side, day by day.",
            )
            HorizontalDivider(color = Theme.inkSoft.copy(alpha = 0.18f))
            FeatureRow(
                { Icon(Icons.Filled.DateRange, null, tint = Theme.accentText) },
                "Gentle predictions",
                "Fertile window and next period, refined as you log — never alarmist.",
            )
        }

        Spacer(Modifier.weight(1f))
        ProgressDots(current = 1)
        PrimaryButton("Continue", onNext, Modifier.padding(top = 18.dp, bottom = 14.dp))
    }
}

@Composable
private fun FeatureRow(icon: @Composable () -> Unit, title: String, sub: String) {
    Row(
        Modifier.padding(vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Theme.ink)
            Text(sub, fontSize = 13.sp, color = Theme.inkSoft, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ------------------------------------------------------------------------------------
// Step 3 · Last period
// ------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LastPeriodStep(
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onNext: () -> Unit,
    onEstimate: (Int) -> Unit,
) {
    var showEstimate by remember { mutableStateOf(false) }
    val todayMillis = remember(today) { today.toEpochDay() * MILLIS_PER_DAY }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.toEpochDay() * MILLIS_PER_DAY,
        selectableDates = remember(todayMillis) {
            object : SelectableDates {
                // A period cannot have started in the future.
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            }
        },
    )

    LaunchedEffect(state.selectedDateMillis) {
        state.selectedDateMillis?.let {
            onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Eyebrow("STEP 1 OF 2")
        Text("Your last period", style = serif(31).copy(color = Theme.ink), modifier = Modifier.padding(top = 6.dp))
        Text(
            "Pick the first day it started.",
            fontSize = 15.sp,
            color = Theme.inkSoft,
            modifier = Modifier.padding(top = 8.dp),
        )

        DatePicker(
            state = state,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = Color.Transparent,
                selectedDayContainerColor = Theme.secondary,
                todayDateBorderColor = Theme.primary,
            ),
            modifier = Modifier
                .padding(top = 14.dp)
                .background(Theme.surface.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .border(1.dp, Theme.inkSoft.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
        )

        Spacer(Modifier.weight(1f))
        ProgressDots(current = 2)
        PrimaryButton("Next", onNext, Modifier.padding(top = 18.dp))
        TextButton(onClick = { showEstimate = true }, modifier = Modifier.padding(top = 6.dp)) {
            Text("Not sure of the exact day?", fontSize = 13.sp, color = Theme.inkSoft)
        }
    }

    if (showEstimate) {
        // An explicit estimate, not a guess presented as fact — the copy says so, and the
        // date stays editable in Me.
        AlertDialog(
            onDismissRequest = { showEstimate = false },
            containerColor = Theme.surface,
            title = { Text("About how long ago did it start?", style = serif(20).copy(color = Theme.ink)) },
            text = {
                // The choices live in the content slot, not the button slot: Material aligns
                // buttons to the end, which left these four reading as a ragged right edge.
                Column {
                    Text(
                        "We'll set an approximate date — you can fine-tune it anytime in Me.",
                        fontSize = 14.sp,
                        color = Theme.inkSoft,
                    )
                    listOf(
                        "Within the last week" to 3,
                        "1–2 weeks ago" to 10,
                        "3–4 weeks ago" to 25,
                        "Over a month ago" to 42,
                    ).forEach { (label, daysAgo) ->
                        Text(
                            label,
                            fontSize = 16.sp,
                            color = Theme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showEstimate = false
                                    onEstimate(daysAgo)
                                }
                                // 48dp is the platform minimum for a touch target.
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEstimate = false }) {
                    Text("Cancel", color = Theme.inkSoft)
                }
            },
        )
    }
}

// ------------------------------------------------------------------------------------
// Step 4 · Cycle rhythm
// ------------------------------------------------------------------------------------

@Composable
private fun RhythmStep(
    cycleLength: Int,
    periodLength: Int,
    onCycleLength: (Int) -> Unit,
    onPeriodLength: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Eyebrow("STEP 2 OF 2")
        Text(
            "Your cycle rhythm",
            style = serif(34).copy(color = Theme.ink, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Two quick details — change them\nanytime in Me.",
            fontSize = 15.sp,
            color = Theme.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )

        FieldLabel("CYCLE LENGTH", "days from one period to the next")
        ChipPicker(21..45, cycleLength, onCycleLength)
        FieldLabel("PERIOD LENGTH", "days your period usually lasts")
        ChipPicker(2..10, periodLength, onPeriodLength)

        Spacer(Modifier.weight(1f))
        ProgressDots(current = 3)
        PrimaryButton("Start my journey", onFinish, Modifier.padding(top = 16.dp))
        Text(
            "Wellness & education — not medical advice",
            fontSize = 13.sp,
            color = Theme.inkSoft,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun FieldLabel(title: String, hint: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    ) {
        Text(title, fontSize = 11.sp, letterSpacing = 1.0.sp, color = Theme.accentText)
        Text(hint, fontSize = 11.sp, color = Theme.inkSoft)
    }
}

@Composable
private fun ChipPicker(range: IntRange, selected: Int, onSelect: (Int) -> Unit) {
    val values = remember(range) { range.toList() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Keep the chosen number centred rather than at the row's leading edge, so the values
    // either side of it are both visible. Waits for a measured viewport: on the first pass
    // the width is still 0 and the centring maths would be a no-op.
    LaunchedEffect(selected) {
        val index = values.indexOf(selected).coerceAtLeast(0)
        val viewport = snapshotFlow { listState.layoutInfo.viewportSize.width }.first { it > 0 }
        val chipPx = with(density) { CHIP_WIDTH.roundToPx() }
        listState.animateScrollToItem(index, -((viewport - chipPx) / 2))
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .height(56.dp)
            .selectableGroup(),
    ) {
        items(values.size) { i ->
            val n = values[i]
            val isSelected = n == selected
            Box(
                Modifier
                    .width(CHIP_WIDTH)
                    .height(48.dp)
                    .background(
                        if (isSelected) {
                            Brush.linearGradient(listOf(Theme.accent, Color(0xFFD9B45F)))
                        } else {
                            Brush.linearGradient(listOf(Theme.surface.copy(alpha = 0.7f), Theme.surface.copy(alpha = 0.7f)))
                        },
                        RoundedCornerShape(16.dp),
                    )
                    .border(
                        1.dp,
                        Theme.inkSoft.copy(alpha = if (isSelected) 0f else 0.14f),
                        RoundedCornerShape(16.dp),
                    )
                    // A single-choice row: TalkBack should announce these as radio buttons
                    // and read out which one is chosen, not as twenty-five bare numbers.
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(n) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$n",
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF3A2A08) else Theme.ink,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// Reusable pieces
// ------------------------------------------------------------------------------------

@Composable
private fun Eyebrow(text: String) {
    Text(text, fontSize = 11.sp, letterSpacing = 1.4.sp, color = Theme.accentText)
}

/**
 * The welcome crescent — the same mark as the app icon and the launch screen.
 *
 * Mauve rather than the icon's gold, because gold was chosen to sit on the icon's night sky
 * and is far too pale on cream. It grows in from rest, so the screen assembles rather than
 * appearing all at once.
 */
@Composable
private fun GlowMoon(sizeDp: Int) {
    var arrived by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (arrived) 1f else 0.6f,
        animationSpec = tween(durationMillis = 750),
        label = "glow-moon",
    )
    LaunchedEffect(Unit) { arrived = true }

    Canvas(
        Modifier
            .size(sizeDp.dp)
            .scale(scale)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            // Decorative: the heading beside it already names the app.
            .clearAndSetSemantics {}
    ) {
        drawCrescent(
            Brush.linearGradient(listOf(Color(0xFF8C6BC4), Theme.primary, Theme.secondary))
        )
    }
}

/** Four-dot progress indicator; the active dot is a gold pill. */
@Composable
private fun ProgressDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(4) { i ->
            val active = i == current
            Box(
                Modifier
                    .width(if (active) 22.dp else 7.dp)
                    .height(7.dp)
                    .background(
                        if (active) Theme.accentText else Color.Black.copy(alpha = 0.15f),
                        CircleShape,
                    )
            )
        }
    }
}

/** Primary onboarding call to action — mauve gradient pill. */
@Composable
private fun PrimaryButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Theme.primary, Color(0xFF8F6FD6))),
                CircleShape,
            ),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/** Sparse, deterministic starfield behind the gradient. */
@Composable
private fun StarField() {
    Canvas(Modifier.fillMaxSize()) {
        STARS.forEach { (x, y, r, alpha) ->
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r * density,
                center = androidx.compose.ui.geometry.Offset(x * size.width, y * size.height),
            )
        }
    }
}

private data class Star(val x: Float, val y: Float, val r: Float, val alpha: Float)

private val STARS = listOf(
    Star(0.10f, 0.12f, 1.6f, 0.5f), Star(0.78f, 0.09f, 1.2f, 0.4f), Star(0.88f, 0.20f, 1.6f, 0.35f),
    Star(0.20f, 0.26f, 1.1f, 0.3f), Star(0.55f, 0.06f, 1.0f, 0.28f), Star(0.34f, 0.16f, 1.3f, 0.32f),
    Star(0.66f, 0.30f, 1.2f, 0.30f), Star(0.14f, 0.40f, 1.0f, 0.22f), Star(0.92f, 0.44f, 1.4f, 0.26f),
    Star(0.48f, 0.36f, 1.0f, 0.24f), Star(0.72f, 0.52f, 1.2f, 0.20f), Star(0.28f, 0.55f, 1.1f, 0.22f),
)

private const val MILLIS_PER_DAY = 86_400_000L

private val CHIP_WIDTH = 52.dp
