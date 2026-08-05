package app.cycluna.android

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.cycluna.android.core.CycleStore
import app.cycluna.android.core.MoonEventCatalog
import app.cycluna.android.core.ReminderScheduler
import app.cycluna.android.core.SettingsStore
import app.cycluna.core.security.KeyVaultContext

/**
 * Process-wide owner of the store and settings.
 *
 * The store lives here rather than in a ViewModel because things outside the UI need it —
 * the process-lifecycle observer below, and later the alarm receiver — and neither of those
 * has a ViewModelStore to reach into. It also means an activity recreation (rotation, a font
 * scale change) never rebuilds it or re-reads the file.
 */
class CyclunaApp : Application() {

    lateinit var store: CycleStore
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        // Must happen before anything can ask for a vault: the zero-argument
        // `defaultKeyVault()` has no other way to reach a Context.
        KeyVaultContext.install(this)

        ReminderScheduler.createChannel(this)
        store = CycleStore(this)
        settings = SettingsStore(this)
        MoonEventCatalog.load(this)

        // The counterpart of the iOS scenePhase `.background` flush. Android can kill a
        // backgrounded process without further warning, so a debounced write that is still
        // waiting when the app leaves the foreground has to be forced out now.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                store.flush()
            }
        })
    }
}

val LocalCycleStore = staticCompositionLocalOf<CycleStore> {
    error("LocalCycleStore accessed outside CyclunaApp")
}

val LocalSettingsStore = staticCompositionLocalOf<SettingsStore> {
    error("LocalSettingsStore accessed outside CyclunaApp")
}
