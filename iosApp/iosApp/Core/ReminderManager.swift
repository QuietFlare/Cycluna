import Foundation
import UserNotifications

/// Everything the user can configure about reminders. Read straight from `UserDefaults`
/// (where `@AppStorage` writes) so RootView and MeView can't drift out of sync — there is
/// exactly one place that knows the keys and their defaults.
struct ReminderSettings {
    var periodOn: Bool
    var ovulationOn: Bool
    /// Days BEFORE the predicted date to fire. 0 = on the day itself.
    var periodLeadDays: Int
    var ovulationLeadDays: Int
    /// Minutes from midnight for the cycle reminders (e.g. 9 * 60 = 09:00).
    var cycleMinute: Int

    var moodCheckIn: Bool
    var headacheCheckIn: Bool
    var checkInMinute: Int

    var anyCycleReminder: Bool { periodOn || ovulationOn }
    var anyCheckIn: Bool { moodCheckIn || headacheCheckIn }

    enum Key {
        static let periodOn = "periodReminders"
        static let ovulationOn = "ovulationReminders"
        static let periodLead = "periodReminderLead"
        static let ovulationLead = "ovulationReminderLead"
        static let cycleMinute = "cycleReminderMinute"
        static let moodCheckIn = "moodCheckInReminder"
        static let headacheCheckIn = "headacheCheckInReminder"
        static let checkInMinute = "checkInReminderMinute"
    }

    /// Defaults: period a day ahead so there's time to prepare, ovulation on the day it
    /// opens, cycle reminders at 09:00, check-ins at 20:00 (when the day is done).
    static let defaultPeriodLead = 1
    static let defaultOvulationLead = 0
    static let defaultCycleMinute = 9 * 60
    static let defaultCheckInMinute = 20 * 60

    /// Current values as the user has them set.
    static var current: ReminderSettings {
        let d = UserDefaults.standard
        func int(_ key: String, _ fallback: Int) -> Int {
            (d.object(forKey: key) as? Int) ?? fallback
        }
        return ReminderSettings(
            periodOn: d.bool(forKey: Key.periodOn),
            ovulationOn: d.bool(forKey: Key.ovulationOn),
            periodLeadDays: int(Key.periodLead, defaultPeriodLead),
            ovulationLeadDays: int(Key.ovulationLead, defaultOvulationLead),
            cycleMinute: int(Key.cycleMinute, defaultCycleMinute),
            moodCheckIn: d.bool(forKey: Key.moodCheckIn),
            headacheCheckIn: d.bool(forKey: Key.headacheCheckIn),
            checkInMinute: int(Key.checkInMinute, defaultCheckInMinute)
        )
    }
}

/// Schedules gentle local reminders — on-device only, no push server.
///
/// Two kinds:
///  • **Cycle reminders** fire once, on a date derived from the current predictions, so they
///    are recomputed every time the app foregrounds and follow the cycle as it's logged.
///  • **Daily check-ins** repeat every day at a fixed time to nudge mood/headache logging.
enum ReminderManager {
    static let periodID = "cycluna.reminder.period"
    static let followUpID = "cycluna.reminder.periodFollowUp"
    static let ovulationID = "cycluna.reminder.ovulation"
    static let checkInID = "cycluna.reminder.checkin"

    private static let allIDs = [periodID, followUpID, ovulationID, checkInID]

    /// Days after the predicted start to ask whether the period arrived.
    static let followUpDelayDays = 1

    /// Ask for permission (call when the user enables a reminder). Returns whether granted.
    @discardableResult
    static func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// One reminder Cycluna intends to schedule. `date` is nil for a daily repeat.
    struct Planned: Equatable {
        let id: String
        let title: String
        let body: String
        let date: Date?
        let minuteOfDay: Int
        let repeats: Bool
    }

    /// Everything that should be scheduled for the given dates and settings.
    ///
    /// Pure — no notification centre, no wall clock — so the decisions (which reminders, what
    /// dates, what wording) are testable on their own. `reschedule` just carries it out.
    static func plan(nextPeriod: Date?, fertileStart: Date?, settings: ReminderSettings,
                     calendar: Calendar = .current) -> [Planned] {
        var out: [Planned] = []

        if settings.periodOn, let period = nextPeriod {
            if let fire = calendar.date(byAdding: .day, value: -settings.periodLeadDays, to: period) {
                out.append(Planned(id: periodID,
                                   title: periodTitle(leadDays: settings.periodLeadDays),
                                   body: "A gentle heads-up so you can prepare.",
                                   date: fire, minuteOfDay: settings.cycleMinute, repeats: false))
            }
            // If the predicted date passes with nothing logged, ask once rather than going quiet
            // exactly when it matters most. Logging a period moves the anchor, so the next
            // reschedule replaces this with one a cycle later — it never becomes a daily nag.
            if let fire = calendar.date(byAdding: .day, value: followUpDelayDays, to: period) {
                out.append(Planned(id: followUpID,
                                   title: "Did your period start? 🌙",
                                   body: "Log it to keep your predictions accurate.",
                                   date: fire, minuteOfDay: settings.cycleMinute, repeats: false))
            }
        }

        if settings.ovulationOn, let fertile = fertileStart,
           let fire = calendar.date(byAdding: .day, value: -settings.ovulationLeadDays, to: fertile) {
            out.append(Planned(id: ovulationID,
                               title: ovulationTitle(leadDays: settings.ovulationLeadDays),
                               body: "Your most fertile days are around now.",
                               date: fire, minuteOfDay: settings.cycleMinute, repeats: false))
        }

        if settings.anyCheckIn {
            out.append(Planned(id: checkInID,
                               title: checkInTitle(mood: settings.moodCheckIn, headache: settings.headacheCheckIn),
                               body: checkInBody(mood: settings.moodCheckIn, headache: settings.headacheCheckIn),
                               date: nil, minuteOfDay: settings.checkInMinute, repeats: true))
        }

        return out
    }

    /// Cancel and re-create every reminder from the latest dates + settings.
    static func reschedule(nextPeriod: Date?, fertileStart: Date?, settings: ReminderSettings) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: allIDs)
        for p in plan(nextPeriod: nextPeriod, fertileStart: fertileStart, settings: settings) {
            add(id: p.id, title: p.title, body: p.body, on: p.date,
                minuteOfDay: p.minuteOfDay, repeats: p.repeats, center: center)
        }
    }

    /// Clear everything (e.g. on data reset).
    static func cancelAll() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: allIDs)
    }

    // MARK: - Copy

    static func periodTitle(leadDays: Int) -> String {
        switch leadDays {
        case 0: return "Your period may start today 🌙"
        case 1: return "Your period may start tomorrow 🌙"
        default: return "Your period may start in \(leadDays) days 🌙"
        }
    }

    static func ovulationTitle(leadDays: Int) -> String {
        switch leadDays {
        case 0: return "Your fertile window opens today ✨"
        case 1: return "Your fertile window opens tomorrow ✨"
        default: return "Your fertile window opens in \(leadDays) days ✨"
        }
    }

    /// One notification covers both check-ins when both are on — two separate pings at the
    /// same time of day would just read as a duplicate.
    static func checkInTitle(mood: Bool, headache: Bool) -> String {
        if mood && headache { return "How was today? 🌙" }
        if headache { return "Any headaches today? 🌙" }
        return "How are you feeling today? 🌙"
    }

    static func checkInBody(mood: Bool, headache: Bool) -> String {
        switch (mood, headache) {
        case (true, true): return "Log your mood and any headaches in Journal."
        case (false, true): return "Log any headaches in Journal."
        default: return "Log your mood in Journal."
        }
    }

    // MARK: - Scheduling

    /// Schedules one notification. `date` is the day to fire on for one-shot reminders, or
    /// nil for a daily repeat. `minuteOfDay` sets the time in both cases.
    private static func add(id: String, title: String, body: String, on date: Date?,
                            minuteOfDay: Int, repeats: Bool, center: UNUserNotificationCenter) {
        var comps = DateComponents()
        if let date {
            let day = Calendar.current.dateComponents([.year, .month, .day], from: date)
            comps.year = day.year; comps.month = day.month; comps.day = day.day
        }
        comps.hour = minuteOfDay / 60
        comps.minute = minuteOfDay % 60

        // A one-shot whose moment has already passed would never fire — skip it. Repeating
        // triggers have no such problem; they simply roll to tomorrow.
        if !repeats {
            guard let fire = Calendar.current.date(from: comps), fire > Date() else { return }
        }

        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: repeats)
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }
}
