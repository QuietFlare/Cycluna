package app.cycluna.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.cycluna.android.designsystem.CyclunaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
