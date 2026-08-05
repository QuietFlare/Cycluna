package app.cycluna.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cycluna.android.designsystem.Crescent
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.serif
import app.cycluna.android.features.onboarding.OnboardingScreen

/**
 * The four tabs. Flat, with no cross-tab navigation and no route graph — the iOS app has
 * none either, so `navigation-compose` would be a dependency earning nothing. Only the About
 * stack pushes sub-pages, and it manages its own back stack.
 */
private enum class Tab(val label: String) {
    TODAY("Today"),
    PHASES("Phases"),
    JOURNAL("Journal"),
    ME("Me"),
}

@Composable
fun RootScreen() {
    val store = LocalCycleStore.current

    Box(Modifier.fillMaxSize().background(Theme.background)) {
        if (store.hasLoggedPeriod) {
            MainTabs()
        } else {
            // No real period logged yet — the app shows only onboarding, and never
            // fabricates a cycle to fill the screens behind it.
            OnboardingScreen()
        }
    }
}

@Composable
private fun MainTabs() {
    // rememberSaveable so the selected tab survives a configuration change (rotation, a
    // font-scale change) rather than snapping back to Today.
    var selected by rememberSaveable { mutableStateOf(Tab.TODAY) }

    Scaffold(
        containerColor = Theme.background,
        bottomBar = {
            NavigationBar(containerColor = Theme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { TabIcon(tab, active = selected == tab) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Theme.primary,
                            selectedTextColor = Theme.primary,
                            unselectedIconColor = Theme.inkSoft,
                            unselectedTextColor = Theme.inkSoft,
                            indicatorColor = Theme.primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
            when (selected) {
                Tab.TODAY -> Placeholder("Today")
                Tab.PHASES -> Placeholder("Phases")
                Tab.JOURNAL -> Placeholder("Journal")
                Tab.ME -> Placeholder("Me")
            }
        }
    }
}

@Composable
private fun TabIcon(tab: Tab, active: Boolean) {
    val tint = if (active) Theme.primary else Theme.inkSoft
    when (tab) {
        // The brand crescent doubles as the Today icon, so the mark the user met on the
        // launch screen is the one they tap to come home.
        Tab.TODAY -> Crescent(
            modifier = Modifier.size(24.dp),
            brush = Brush.linearGradient(listOf(tint, tint)),
        )
        Tab.PHASES -> PhasesGlyph(tint)
        Tab.JOURNAL -> Icon(Icons.Filled.Edit, contentDescription = null)
        Tab.ME -> Icon(Icons.Filled.Person, contentDescription = null)
    }
}

/** A rising line, for the Phases tab. Hand-drawn because material-icons-core has no chart. */
@Composable
private fun PhasesGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val points = listOf(0f to 0.75f, 0.33f to 0.45f, 0.66f to 0.6f, 1f to 0.2f)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            points.forEachIndexed { i, (fx, fy) ->
                if (i == 0) moveTo(fx * w, fy * h) else lineTo(fx * w, fy * h)
            }
        }
        drawPath(path, tint, style = stroke)
    }
}

@Composable
private fun Placeholder(name: String) {
    Text(name, style = serif(28).copy(color = Theme.ink))
}
