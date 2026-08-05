package app.cycluna.android.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns a [ReminderPlan] into real alarms. On-device only — no push server is involved.
 *
 * Uses `setWindow` rather than an exact alarm. Exact alarms need `SCHEDULE_EXACT_ALARM`,
 * which is denied by default from Android 14 and is meant for alarm-clock and calendar apps;
 * these are gentle nudges, and a fifteen-minute window is the honest fit. WorkManager was the
 * other option and is worse here: under Doze it can drift by hours, which turns "09:00" into
 * lunchtime.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "reminders"

    private const val EXTRA_ID = "id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private const val EXTRA_MINUTE = "minuteOfDay"
    private const val EXTRA_REPEATS = "repeats"

    /** A fifteen-minute delivery window; see the class note on why this is not exact. */
    private const val WINDOW_MILLIS = 15 * 60 * 1000L

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cycle reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Period, ovulation and daily check-in reminders."
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /** Cancel and re-create every reminder from the latest dates and settings. */
    fun reschedule(
        context: Context,
        nextPeriod: LocalDate?,
        fertileStart: LocalDate?,
        settings: AppSettings,
    ) {
        cancelAll(context)
        ReminderPlan.plan(nextPeriod, fertileStart, settings).forEach { schedule(context, it) }
    }

    fun cancelAll(context: Context) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        ReminderPlan.ALL_IDS.forEach { id ->
            alarms.cancel(pendingIntent(context, id, null, flagsForCancel = true))
        }
    }

    /**
     * Schedules one reminder. A one-shot whose moment has already passed is skipped — it
     * would never fire. A repeat simply rolls to tomorrow.
     */
    fun schedule(context: Context, planned: Planned) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val now = LocalDateTime.now()
        val hour = planned.minuteOfDay / 60
        val minute = planned.minuteOfDay % 60

        val fireAt = if (planned.repeats) {
            val today = now.toLocalDate().atTime(hour, minute)
            if (today.isAfter(now)) today else today.plusDays(1)
        } else {
            val date = planned.date ?: return
            val at = date.atTime(hour, minute)
            if (at.isBefore(now)) return
            at
        }

        val millis = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setWindow(
            AlarmManager.RTC_WAKEUP,
            millis,
            WINDOW_MILLIS,
            pendingIntent(context, planned.id, planned),
        )
    }

    private fun requestCode(id: String): Int = ReminderPlan.ALL_IDS.indexOf(id) + 1

    private fun pendingIntent(
        context: Context,
        id: String,
        planned: Planned?,
        flagsForCancel: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            // The action has to differ per reminder, or the four PendingIntents compare equal
            // and every schedule overwrites the last.
            action = "app.cycluna.REMIND.$id"
            putExtra(EXTRA_ID, id)
            planned?.let {
                putExtra(EXTRA_TITLE, it.title)
                putExtra(EXTRA_BODY, it.body)
                putExtra(EXTRA_MINUTE, it.minuteOfDay)
                putExtra(EXTRA_REPEATS, it.repeats)
            }
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (flagsForCancel) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
        // FLAG_NO_CREATE returns null when nothing is scheduled; cancelling nothing is fine.
        return PendingIntent.getBroadcast(context, requestCode(id), intent, flags)
            ?: PendingIntent.getBroadcast(
                context,
                requestCode(id),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }

    internal fun plannedFrom(intent: Intent): Planned? {
        val id = intent.getStringExtra(EXTRA_ID) ?: return null
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
        return Planned(
            id = id,
            title = title,
            body = intent.getStringExtra(EXTRA_BODY).orEmpty(),
            date = null,
            minuteOfDay = intent.getIntExtra(EXTRA_MINUTE, 9 * 60),
            repeats = intent.getBooleanExtra(EXTRA_REPEATS, false),
        )
    }
}
