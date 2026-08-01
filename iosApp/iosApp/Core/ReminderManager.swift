import Foundation
import UserNotifications

/// Schedules gentle local period/ovulation reminders — on-device only, no push server.
/// Reminders are recomputed from the current predictions each time the app foregrounds, so
/// they follow the cycle as it's logged.
enum ReminderManager {
    private static let periodID = "cycluna.reminder.period"
    private static let ovulationID = "cycluna.reminder.ovulation"

    /// Ask for permission (call when the user enables a reminder). Returns whether granted.
    @discardableResult
    static func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// Cancel and re-create the reminders from the latest dates + toggle state.
    static func reschedule(nextPeriod: Date?, fertileStart: Date?, periodOn: Bool, ovulationOn: Bool) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [periodID, ovulationID])

        if periodOn, let period = nextPeriod,
           let dayBefore = Calendar.current.date(byAdding: .day, value: -1, to: period) {
            add(id: periodID,
                title: "Your period may start tomorrow 🌙",
                body: "A gentle heads-up so you can prepare.",
                on: dayBefore, center: center)
        }
        if ovulationOn, let fertile = fertileStart {
            add(id: ovulationID,
                title: "Your fertile window opens today ✨",
                body: "Your most fertile days are around now.",
                on: fertile, center: center)
        }
    }

    /// Clear everything (e.g. on data reset).
    static func cancelAll() {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [periodID, ovulationID])
    }

    private static func add(id: String, title: String, body: String, on date: Date,
                            center: UNUserNotificationCenter) {
        var comps = Calendar.current.dateComponents([.year, .month, .day], from: date)
        comps.hour = 9; comps.minute = 0
        guard let fire = Calendar.current.date(from: comps), fire > Date() else { return }

        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }
}
