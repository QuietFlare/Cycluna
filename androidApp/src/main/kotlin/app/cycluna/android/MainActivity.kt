package app.cycluna.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.cycluna.android.designsystem.CyclunaTheme

/**
 * Scrim for API levels that cannot draw dark icons on the navigation bar (below 27). Matches
 * the value androidx uses for its own default styles.
 */
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {

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
    }
}
