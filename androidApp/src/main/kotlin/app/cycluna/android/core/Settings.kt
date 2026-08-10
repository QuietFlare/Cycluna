package app.cycluna.android.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cycluna-settings")

/**
 * Preference keys, deliberately spelled exactly as the iOS `UserDefaults` keys.
 *
 * Nothing syncs between the platforms — there is no server — but a settings name that means
 * one thing on iOS and another on Android is the kind of drift that only shows up years
 * later in a support thread. Same name, same default, same meaning.
 */
object SettingsKeys {
    val APP_LOCK_ENABLED = booleanPreferencesKey("appLockEnabled")
    val PERIOD_REMINDERS = booleanPreferencesKey("periodReminders")
    val OVULATION_REMINDERS = booleanPreferencesKey("ovulationReminders")
    val PERIOD_REMINDER_LEAD = intPreferencesKey("periodReminderLead")
    val OVULATION_REMINDER_LEAD = intPreferencesKey("ovulationReminderLead")
    val CYCLE_REMINDER_MINUTE = intPreferencesKey("cycleReminderMinute")
    val MOOD_CHECK_IN = booleanPreferencesKey("moodCheckInReminder")
    val HEADACHE_CHECK_IN = booleanPreferencesKey("headacheCheckInReminder")
    val CHECK_IN_MINUTE = intPreferencesKey("checkInReminderMinute")
    val DISCREET_REMINDERS = booleanPreferencesKey("discreetReminders")
    val FERTILITY_INSIGHTS = booleanPreferencesKey("fertilityInsights")
}

/**
 * Everything the reminder planner and the lock need, as one immutable snapshot.
 * Minutes are minutes from midnight, matching iOS.
 */
data class AppSettings(
    val appLockEnabled: Boolean = false,
    val periodOn: Boolean = false,
    val ovulationOn: Boolean = false,
    val periodLead: Int = 1,
    val ovulationLead: Int = 0,
    val cycleMinute: Int = 9 * 60,
    val moodCheckIn: Boolean = false,
    val headacheCheckIn: Boolean = false,
    val checkInMinute: Int = 20 * 60,
    val discreet: Boolean = false,
    /**
     * Fertile window, ovulation reminders, and fertile calendar days. ON by default; the
     * off state turns Cycluna into a plain period-and-wellbeing tracker — for anyone (a
     * teen especially) for whom fertility predictions are unwanted or, given irregular
     * cycles, simply wrong. Deliberately not an age question: that would mean collecting
     * exactly the data this app is built to never hold.
     */
    val fertility: Boolean = true,
) {
    val anyCycleReminder: Boolean get() = periodOn || ovulationOn
    val anyCheckIn: Boolean get() = moodCheckIn || headacheCheckIn

    companion object {
        fun from(prefs: Preferences) = AppSettings(
            appLockEnabled = prefs[SettingsKeys.APP_LOCK_ENABLED] ?: false,
            periodOn = prefs[SettingsKeys.PERIOD_REMINDERS] ?: false,
            ovulationOn = prefs[SettingsKeys.OVULATION_REMINDERS] ?: false,
            periodLead = prefs[SettingsKeys.PERIOD_REMINDER_LEAD] ?: 1,
            ovulationLead = prefs[SettingsKeys.OVULATION_REMINDER_LEAD] ?: 0,
            cycleMinute = prefs[SettingsKeys.CYCLE_REMINDER_MINUTE] ?: (9 * 60),
            moodCheckIn = prefs[SettingsKeys.MOOD_CHECK_IN] ?: false,
            headacheCheckIn = prefs[SettingsKeys.HEADACHE_CHECK_IN] ?: false,
            checkInMinute = prefs[SettingsKeys.CHECK_IN_MINUTE] ?: (20 * 60),
            discreet = prefs[SettingsKeys.DISCREET_REMINDERS] ?: false,
            fertility = prefs[SettingsKeys.FERTILITY_INSIGHTS] ?: true,
        )
    }
}

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map(AppSettings::from)

    suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
