package app.cycluna.android.features.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.Preferences
import app.cycluna.android.LocalCycleStore
import app.cycluna.android.LocalSettingsStore
import app.cycluna.android.core.AppSettings
import app.cycluna.android.core.ReminderScheduler
import app.cycluna.android.core.SettingsKeys
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.cyclunaCard
import app.cycluna.android.designsystem.serif
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * How far ahead a cycle reminder can fire. Kept short — beyond a couple of days the
 * prediction isn't precise enough for the reminder to mean much.
 */
private val LEAD_OPTIONS = listOf(2, 1, 0)

private fun leadLabel(days: Int): String = when (days) {
    0 -> "On the day"
    1 -> "Day before"
    else -> "$days days before"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(settings: AppSettings, onAbout: () -> Unit) {
    val store = LocalCycleStore.current
    val settingsStore = LocalSettingsStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var confirmDelete by remember { mutableStateOf(false) }
    var showLastPeriodPicker by remember { mutableStateOf(false) }
    var editingCycleTime by remember { mutableStateOf(false) }
    var editingCheckInTime by remember { mutableStateOf(false) }

    // Android 13+ needs the runtime permission before a notification can be posted. Asked at
    // the same moment iOS asks: the first time a reminder is switched on.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Declining leaves the toggle on but silent; the system settings are the way back. */ }

    fun reschedule(next: AppSettings) {
        val logged = store.hasLoggedPeriod
        ReminderScheduler.reschedule(
            context = context,
            nextPeriod = if (logged) store.nextPeriodDate else null,
            fertileStart = if (logged) store.fertileStartDate else null,
            settings = next,
        )
    }

    fun <T> put(key: Preferences.Key<T>, value: T, next: AppSettings) {
        scope.launch {
            settingsStore.put(key, value)
            if (next.anyCycleReminder || next.anyCheckIn) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            reschedule(next)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard("You") {
            OutlinedTextField(
                value = store.displayName,
                onValueChange = { store.displayName = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsCard("Cycle") {
            SettingRow("Last period start", onClick = { showLastPeriodPicker = true }) {
                Text(store.lastPeriodStart.toString(), fontSize = 15.sp, color = Theme.inkSoft)
            }
            Stepper("Cycle length", store.cycleLengthSetting, 21..45) {
                store.cycleLengthSetting = it
                reschedule(settings)
            }
            Stepper("Period length", store.periodLength, 2..10) {
                store.periodLength = it
                reschedule(settings)
            }
            // Shown only when it's surprising: the app is ignoring the stepper above.
            // Explaining the normal case every time is just noise.
            if (store.cycleLength != store.cycleLengthSetting) {
                Text(
                    "Using ${store.cycleLength} days from your logged periods.",
                    fontSize = 12.sp,
                    color = Theme.inkSoft,
                )
            }
        }

        SettingsCard("Cycle reminders") {
            SwitchRow("Period reminders", settings.periodOn) {
                put(SettingsKeys.PERIOD_REMINDERS, it, settings.copy(periodOn = it))
            }
            if (settings.periodOn) {
                LeadPicker("Remind me", settings.periodLead) {
                    put(SettingsKeys.PERIOD_REMINDER_LEAD, it, settings.copy(periodLead = it))
                }
            }
            SwitchRow("Ovulation reminders", settings.ovulationOn) {
                put(SettingsKeys.OVULATION_REMINDERS, it, settings.copy(ovulationOn = it))
            }
            if (settings.ovulationOn) {
                LeadPicker("Remind me", settings.ovulationLead) {
                    put(SettingsKeys.OVULATION_REMINDER_LEAD, it, settings.copy(ovulationLead = it))
                }
            }
            if (settings.anyCycleReminder) {
                SettingRow("Time", onClick = { editingCycleTime = true }) {
                    Text(formatMinute(settings.cycleMinute), fontSize = 15.sp, color = Theme.inkSoft)
                }
            }
        }

        SettingsCard("Daily check-in") {
            SwitchRow("Mood check-in", settings.moodCheckIn) {
                put(SettingsKeys.MOOD_CHECK_IN, it, settings.copy(moodCheckIn = it))
            }
            SwitchRow("Headache check-in", settings.headacheCheckIn) {
                put(SettingsKeys.HEADACHE_CHECK_IN, it, settings.copy(headacheCheckIn = it))
            }
            if (settings.anyCheckIn) {
                SettingRow("Time", onClick = { editingCheckInTime = true }) {
                    Text(formatMinute(settings.checkInMinute), fontSize = 15.sp, color = Theme.inkSoft)
                }
            }
        }

        SettingsCard("Privacy") {
            SwitchRow("Require unlock to open", settings.appLockEnabled) {
                scope.launch { settingsStore.put(SettingsKeys.APP_LOCK_ENABLED, it) }
            }
            SwitchRow("Discreet reminders", settings.discreet) {
                put(SettingsKeys.DISCREET_REMINDERS, it, settings.copy(discreet = it))
            }
            Text(
                "Reminders arrive as usual, without cycle details.",
                fontSize = 12.sp,
                color = Theme.inkSoft,
            )
        }

        // Required by Play's data-safety rules and by GDPR/CCPA. Both are on-device.
        SettingsCard("Your data") {
            SettingRow("Export my data", onClick = { shareExport(context) }) {}
            SettingRow("Delete all my data", onClick = { confirmDelete = true }, danger = true) {}
        }

        SettingsCard(null) {
            SettingRow("About Cycluna", onClick = onAbout) {
                Text(versionName(context), fontSize = 15.sp, color = Theme.inkSoft)
            }
        }
    }

    if (showLastPeriodPicker) {
        val today = remember { LocalDate.now() }
        val todayMillis = today.toEpochDay() * MILLIS_PER_DAY
        val state = rememberDatePickerState(
            initialSelectedDateMillis = store.lastPeriodStart.toEpochDay() * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                // A period can only have started in the past. A future anchor makes every
                // derived value nonsense — cycle day clamps to 1, so the app would claim
                // "Day 1 · Menstrual" for a period that hasn't happened.
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showLastPeriodPicker = false },
            colors = DatePickerDefaults.colors(containerColor = Theme.surface),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        store.lastPeriodStart =
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        reschedule(settings)
                    }
                    showLastPeriodPicker = false
                }) { Text("Done", color = Theme.primary) }
            },
        ) {
            DatePicker(state = state, title = null, showModeToggle = false)
        }
    }

    if (editingCycleTime) {
        TimeDialog(settings.cycleMinute, onDismiss = { editingCycleTime = false }) { minute ->
            put(SettingsKeys.CYCLE_REMINDER_MINUTE, minute, settings.copy(cycleMinute = minute))
        }
    }
    if (editingCheckInTime) {
        TimeDialog(settings.checkInMinute, onDismiss = { editingCheckInTime = false }) { minute ->
            put(SettingsKeys.CHECK_IN_MINUTE, minute, settings.copy(checkInMinute = minute))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Theme.surface,
            title = { Text("Delete all your data?", style = serif(20).copy(color = Theme.ink)) },
            text = {
                Text(
                    "This erases everything on this device and cannot be undone.",
                    fontSize = 14.sp,
                    color = Theme.inkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Reminders are cancelled here rather than waiting for the next
                    // foreground refresh — otherwise a notification predicted from
                    // now-deleted data could still fire if the app is never reopened.
                    store.deleteAllData()
                    scope.launch {
                        settingsStore.put(SettingsKeys.PERIOD_REMINDERS, false)
                        settingsStore.put(SettingsKeys.OVULATION_REMINDERS, false)
                        settingsStore.put(SettingsKeys.MOOD_CHECK_IN, false)
                        settingsStore.put(SettingsKeys.HEADACHE_CHECK_IN, false)
                    }
                    ReminderScheduler.cancelAll(context)
                }) { Text("Delete everything", color = Theme.phaseMenstrual) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = Theme.inkSoft)
                }
            },
        )
    }
}

// ------------------------------------------------------------------------------------
// Pieces
// ------------------------------------------------------------------------------------

@Composable
private fun SettingsCard(title: String?, content: @Composable () -> Unit) {
    Column(
        Modifier.cyclunaCard(padding = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        title?.let {
            Text(it.uppercase(), fontSize = 11.sp, letterSpacing = 1.0.sp, color = Theme.accentText)
        }
        content()
    }
}

@Composable
private fun SettingRow(
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontSize = 16.sp,
            color = if (danger) Theme.phaseMenstrual else Theme.ink,
        )
        trailing()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = Theme.ink)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Theme.surface,
                checkedTrackColor = Theme.primary,
            ),
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label: $value days", Modifier.weight(1f), fontSize = 16.sp, color = Theme.ink)
        // Material has no stepper; a minus/plus pair is the platform-appropriate stand-in.
        IconButton(
            onClick = { if (value - 1 in range) onChange(value - 1) },
            enabled = value - 1 in range,
        ) {
            Text("−", fontSize = 22.sp, color = Theme.primary)
        }
        IconButton(
            onClick = { if (value + 1 in range) onChange(value + 1) },
            enabled = value + 1 in range,
        ) {
            Icon(Icons.Filled.Add, "Increase $label", tint = Theme.primary)
        }
    }
}

@Composable
private fun LeadPicker(label: String, value: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingRow(label, onClick = { open = !open }) {
        Text(leadLabel(value), fontSize = 15.sp, color = Theme.inkSoft)
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Theme.inkSoft)
    }
    if (open) {
        Column(Modifier.padding(start = 12.dp)) {
            LEAD_OPTIONS.forEach { option ->
                Text(
                    leadLabel(option),
                    Modifier
                        .fillMaxWidth()
                        .clickable { open = false; onChange(option) }
                        .padding(vertical = 10.dp),
                    fontSize = 15.sp,
                    color = if (option == value) Theme.primary else Theme.ink,
                    fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minuteOfDay: Int, onDismiss: () -> Unit, onPicked: (Int) -> Unit) {
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Theme.surface,
        title = { Text("Reminder time", style = serif(20).copy(color = Theme.ink)) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    selectorColor = Theme.primary,
                    containerColor = Theme.background,
                    periodSelectorSelectedContainerColor = Theme.primary.copy(alpha = 0.15f),
                    timeSelectorSelectedContainerColor = Theme.primary.copy(alpha = 0.15f),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onPicked(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text("Done", color = Theme.primary) }
        },
    )
}

private fun formatMinute(minuteOfDay: Int): String {
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val suffix = if (hour < 12) "AM" else "PM"
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "%d:%02d %s".format(h, minute, suffix)
}

private fun versionName(context: android.content.Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

/**
 * Hands the export file to the system share sheet. The data stays on-device until the user
 * picks a destination — that is the one moment it can leave the phone.
 */
private fun shareExport(context: android.content.Context) {
    val app = context.applicationContext as app.cycluna.android.CyclunaApp
    val file = app.store.exportFile() ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export my data"))
}
