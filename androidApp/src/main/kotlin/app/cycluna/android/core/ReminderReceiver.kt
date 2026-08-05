package app.cycluna.android.core

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.cycluna.android.CyclunaApp
import app.cycluna.android.MainActivity
import app.cycluna.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Posts a reminder when its alarm fires.
 *
 * Android has no repeating-notification primitive, so the daily check-in re-arms itself here
 * for tomorrow. If a firing is ever missed — the device was off, the app force-stopped — the
 * chain self-heals the next time the app reaches the foreground and reschedules everything.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val planned = ReminderScheduler.plannedFrom(intent) ?: return

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(planned.title)
                .setContentText(planned.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(planned.body))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            NotificationManagerCompat.from(context).notify(planned.id.hashCode(), notification)
        }

        if (planned.repeats) {
            ReminderScheduler.schedule(context, planned)
        }
    }
}

/**
 * Alarms do not survive a reboot, so everything is rebuilt from the stored predictions and
 * settings once the device comes back up.
 *
 * The work happens off the main thread behind `goAsync()`. `onReceive` runs on the main
 * thread, and reading the settings means reading a file — at boot, with every installed app
 * contending for I/O, blocking there risks an ANR. The cost of that would be silent: no UI
 * to show it, just reminders that never come back until the app is next opened by hand.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? CyclunaApp ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = app.settings.settings.first()
                if (!settings.anyCycleReminder && !settings.anyCheckIn) return@launch
                val logged = app.store.hasLoggedPeriod
                ReminderScheduler.reschedule(
                    context = context,
                    nextPeriod = if (logged) app.store.nextPeriodDate else null,
                    fertileStart = if (logged) app.store.fertileStartDate else null,
                    settings = settings,
                )
            } finally {
                // Releases the wake lock the broadcast holds; skipping it leaks it.
                pending.finish()
            }
        }
    }
}
