package app.cycluna.android

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cycluna.android.core.AppLock
import app.cycluna.android.core.AppSettings
import app.cycluna.android.core.ReminderScheduler
import app.cycluna.android.designsystem.Crescent
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.serif
import app.cycluna.android.features.home.HomeScreen
import app.cycluna.android.features.journal.JournalScreen
import app.cycluna.android.features.onboarding.OnboardingScreen
import app.cycluna.android.features.phases.PhasesScreen
import app.cycluna.android.features.settings.AboutPage
import app.cycluna.android.features.settings.AboutScreen
import app.cycluna.android.features.settings.LockScreen
import app.cycluna.android.features.settings.MeScreen

/**
 * The four tabs. Flat, with no cross-tab navigation and no route graph — the iOS app has none
 * either, so `navigation-compose` would be a dependency earning nothing. Only the About stack
 * pushes sub-pages, and it manages its own back stack below.
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
    val settingsStore = LocalSettingsStore.current
    val settings by settingsStore.settings.collectAsStateWithLifecycle(AppSettings())

    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    val lock = remember { AppLock() }
    val locked = settings.appLockEnabled && !lock.isUnlocked

    /** Bring the alarms back in line with whatever is currently stored. */
    fun syncReminders(current: AppSettings) {
        if (current.anyCycleReminder || current.anyCheckIn) {
            val logged = store.hasLoggedPeriod
            ReminderScheduler.reschedule(
                context = context,
                nextPeriod = if (logged) store.nextPeriodDate else null,
                fertileStart = if (logged) store.fertileStartDate else null,
                settings = current,
            )
        } else {
            ReminderScheduler.cancelAll(context)
        }
    }

    // Read through a holder so the observer below always sees current values without being
    // re-registered. Keying the effect on `settings` instead looked equivalent and was not:
    // adding an observer replays ON_START immediately, so every settings change re-ran the
    // foreground path — and "delete everything" cancelled the alarms only to have the replay
    // reschedule them a moment later from the pre-delete snapshot.
    val currentSettings by rememberUpdatedState(settings)

    // The iOS scenePhase handling in Android's vocabulary: authenticate and refresh the
    // reminders on the way in, re-lock on the way out.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (currentSettings.appLockEnabled) {
                        activity?.let { lock.authenticate(it) }
                    } else {
                        lock.isUnlocked = true
                    }
                    syncReminders(currentSettings)
                }
                Lifecycle.Event.ON_STOP -> if (currentSettings.appLockEnabled) lock.lock()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Logging a period moves the anchor, which moves every predicted date; changing a setting
    // moves the reminders themselves. Both converge here — including the all-off state after
    // a delete, which cancels everything.
    LaunchedEffect(settings, store.periodStarts.size) { syncReminders(settings) }

    Box(Modifier.fillMaxSize().background(Theme.background)) {
        if (store.hasLoggedPeriod) {
            MainTabs(settings)
        } else {
            // No real period logged yet — the app shows only onboarding, and never
            // fabricates a cycle to fill the screens behind it.
            OnboardingScreen()
        }

        // An overlay, not a route: the content is covered rather than unmounted, so nothing
        // is rebuilt when the lock lifts.
        if (locked) {
            LockScreen(onUnlock = { activity?.let { lock.authenticate(it) } })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(settings: AppSettings) {
    // rememberSaveable so the selected tab survives a configuration change (rotation, a
    // font-scale change) rather than snapping back to Today.
    var selected by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var aboutPage by rememberSaveable { mutableStateOf<AboutPage?>(null) }

    // Predictive back walks the About stack before it leaves the app.
    BackHandler(enabled = aboutPage != null) {
        aboutPage = if (aboutPage == AboutPage.ROOT) null else AboutPage.ROOT
    }

    Scaffold(
        containerColor = Theme.background,
        // A real top bar, not a scrolling header: the brand stays put and content passes
        // under it. Hiding it was what made the iOS screen read as a web page.
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    when {
                        aboutPage != null -> Text("About", style = serif(20).copy(color = Theme.ink))
                        selected == Tab.TODAY -> Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Crescent(
                                Modifier.size(14.dp),
                                Brush.linearGradient(listOf(Theme.primary, Theme.primary)),
                            )
                            Text("Cycluna", style = serif(17).copy(color = Theme.ink))
                        }
                        else -> Text(selected.label, style = serif(20).copy(color = Theme.ink))
                    }
                },
                navigationIcon = {
                    if (aboutPage != null) {
                        IconButton(onClick = {
                            aboutPage = if (aboutPage == AboutPage.ROOT) null else AboutPage.ROOT
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Theme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Theme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Theme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab; aboutPage = null },
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
        Box(Modifier.fillMaxSize().padding(insets)) {
            val page = aboutPage
            if (page != null) {
                AboutScreen(page) { aboutPage = it }
            } else {
                when (selected) {
                    Tab.TODAY -> HomeScreen()
                    Tab.PHASES -> PhasesScreen()
                    Tab.JOURNAL -> JournalScreen()
                    Tab.ME -> MeScreen(settings) { aboutPage = AboutPage.ROOT }
                }
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
