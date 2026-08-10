package app.cycluna.android

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.cycluna.android.designsystem.CyclunaTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The reminder id from a just-tapped notification, waiting for the tab UI to consume it.
 * A flow rather than an intent read in composition: the tap can arrive through `onCreate`
 * (cold start) or `onNewIntent` (already running), and both funnel here.
 */
object PendingReminderTap {
    val id = MutableStateFlow<String?>(null)
}

/**
 * Scrim for API levels that cannot draw dark icons on the navigation bar (below 27). Matches
 * the value androidx uses for its own default styles.
 */
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * A FragmentActivity rather than a plain ComponentActivity: `BiometricPrompt` shows itself
 * through a fragment and will not accept anything less.
 */
class MainActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_REMINDER_ID = "reminderId"

        /** The content intent for a reminder notification, carrying which reminder it was. */
        fun tapIntent(context: Context, reminderId: String): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_REMINDER_ID, reminderId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Both bars are pinned to the LIGHT style — dark icons on our cream background.
        //
        // The no-argument enableEdgeToEdge() picks icon colour from the system dark-mode
        // setting, which is wrong for a light-only app: on a phone in dark mode the status
        // bar drew white icons over the cream background and all but disappeared. The app
        // never renders a dark surface behind these bars, so the choice is not conditional.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, DARK_SCRIM),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, DARK_SCRIM),
        )
        super.onCreate(savedInstanceState)

        val app = application as CyclunaApp
        setContent {
            CompositionLocalProvider(
                LocalCycleStore provides app.store,
                LocalSettingsStore provides app.settings,
            ) {
                CyclunaTheme {
                    RootScreen()
                }
            }
        }

        // Restores re-deliver the launch intent, so only a fresh launch routes; a tap while
        // running arrives via onNewIntent (the activity is singleTop).
        if (savedInstanceState == null) consumeReminderTap(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeReminderTap(intent)
    }

    private fun consumeReminderTap(intent: Intent) {
        intent.getStringExtra(EXTRA_REMINDER_ID)?.let { PendingReminderTap.id.value = it }
    }
}
