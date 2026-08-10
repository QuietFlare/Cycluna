package app.cycluna.android.features.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.core.CycleStore
import app.cycluna.android.core.MoodScale
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import app.cycluna.core.MoodInsight

/** A pager needs one fixed height, so all three lenses share it. */
private val CHART_HEIGHT = 150.dp

/**
 * The axis differs per lens — one row of dates (daily), phase names + dates (phase),
 * disc + name + date (moon). Sizing all three to the tallest left the daily page with a
 * band of dead space between its dates and whatever came next.
 */
private fun axisHeight(lens: CycleStore.MoodLens): Dp = when (lens) {
    CycleStore.MoodLens.DAILY -> 16.dp
    CycleStore.MoodLens.PHASE -> 30.dp
    CycleStore.MoodLens.MOON -> 42.dp
}

/**
 * "Mood patterns" — the same logged moods seen through three lenses: by day, by cycle phase,
 * and by moon phase. Swipe the chart to page back through history.
 *
 * Two rules this card exists to keep:
 *  - Every page states the span it covers. The chart and the sentence beneath it must never
 *    describe different timeframes.
 *  - A page shows a *claim* only when its own data clears the core's guardrails; otherwise it
 *    describes what's there. Descriptions need no guardrails, conclusions do.
 */
@Composable
fun MoodPatternsCard() {
    val store = LocalCycleStore.current
    val pages = store.moodPages

    Column(
        Modifier.cyclunaCard(padding = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Mood patterns", style = serif(22).copy(color = Theme.ink))
            Text(
                when (store.moodLens) {
                    CycleStore.MoodLens.DAILY -> "How you've felt day by day"
                    CycleStore.MoodLens.PHASE -> "How you feel through each cycle phase"
                    CycleStore.MoodLens.MOON -> "How you feel through the lunar month"
                },
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = Theme.inkSoft,
            )
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CycleStore.MoodLens.entries.forEachIndexed { i, lens ->
                SegmentedButton(
                    selected = store.moodLens == lens,
                    onClick = { store.moodLens = lens },
                    shape = SegmentedButtonDefaults.itemShape(i, CycleStore.MoodLens.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Theme.primary.copy(alpha = 0.15f),
                        activeContentColor = Theme.primary,
                        inactiveContentColor = Theme.inkSoft,
                    ),
                ) {
                    Text(lens.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }

        if (pages.isEmpty()) {
            Text(
                "Log your mood a few times and your patterns appear here.",
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                fontSize = 15.sp,
                color = Theme.inkSoft,
                textAlign = TextAlign.Center,
            )
        } else {
            val pagerState = rememberPagerState(
                initialPage = store.moodPageIndex.coerceIn(0, pages.lastIndex),
                pageCount = { pages.size },
            )

            // The store decides which page is "the present" whenever the data or the lens
            // changes; the pager follows it rather than holding a stale index.
            LaunchedEffect(pages) {
                pagerState.scrollToPage(store.moodPageIndex.coerceIn(0, pages.lastIndex))
            }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { store.moodPageIndex = it }
            }

            // The paging affordance lives ON the chart — a row of its own was just a gap.
            // The chevrons sit in the plot's own edge padding, level with the chart's
            // midline.
            Box {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.height(CHART_HEIGHT + axisHeight(store.moodLens) + 5.dp),
                ) { index ->
                    val page = pages[index]
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val chartModifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)
                        when (store.moodLens) {
                            CycleStore.MoodLens.DAILY -> DailyMoodChart(page, chartModifier)
                            CycleStore.MoodLens.PHASE -> PhaseMoodChart(page, store.periodLength, chartModifier)
                            CycleStore.MoodLens.MOON -> MoonMoodChart(page, chartModifier)
                        }
                        val axisModifier = Modifier.fillMaxWidth().height(axisHeight(store.moodLens))
                        when (store.moodLens) {
                            CycleStore.MoodLens.DAILY -> DailyMoodAxis(page, axisModifier)
                            CycleStore.MoodLens.PHASE -> PhaseMoodAxis(page, store.periodLength, axisModifier)
                            CycleStore.MoodLens.MOON -> MoonMoodAxis(page, axisModifier)
                        }
                    }
                }
                if (pagerState.currentPage > 0) {
                    PagingChevron("‹", Modifier.align(Alignment.CenterStart))
                }
                if (pagerState.currentPage < pages.lastIndex) {
                    PagingChevron("›", Modifier.align(Alignment.CenterEnd))
                }
            }
        }

        Narrative(pages.getOrNull(store.moodPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (1..5).forEach { v ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(MoodScale.color(v), CircleShape))
                    Text(MoodScale.label(v).lowercase(), fontSize = 10.sp, color = Theme.inkSoft)
                }
            }
        }
    }
}

/**
 * A claim only when the page's own data earns one, and only about the span on screen.
 *
 * There is deliberately nothing to say otherwise: a running "n logs · averaging mid" restated
 * what the chart already showed, and an empty page stays visually quiet — the blank chart is
 * the empty state. Plain text, no emoji prefix.
 */
@Composable
private fun Narrative(page: CycleStore.MoodPage?) {
    val store = LocalCycleStore.current
    if (page == null || page.summary.count == 0) return

    page.insight?.let {
        Text(insightSentence(it, store.moodLens), fontSize = 13.sp, color = Theme.inkSoft)
    }
}

@Composable
private fun PagingChevron(glyph: String, modifier: Modifier) {
    Text(
        glyph,
        // Centred on the chart, not on chart + axis: the axis sits below the plot, so the
        // chevron is nudged up by half its height. On a small surface chip because the
        // daily line's first dot lands exactly here — a bare glyph vanished into the data.
        modifier
            .padding(horizontal = 2.dp)
            .offset(y = (-14).dp)
            .background(Theme.surface.copy(alpha = 0.92f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        fontSize = 16.sp,
        color = Theme.inkSoft,
    )
}

private fun insightSentence(insight: MoodInsight, lens: CycleStore.MoodLens): String {
    val brightest = phaseWord(insight.brightest.label)
    // The moon lens names only the high point: its buckets are not phases, and pairing a
    // "lower" claim with them overstates what one lunation can show.
    if (lens == CycleStore.MoodLens.MOON) {
        return "On this page you felt brightest around your $brightest."
    }
    return "On this page you felt brightest around your $brightest, " +
        "and lower during your ${phaseWord(insight.lowest.label)}."
}

