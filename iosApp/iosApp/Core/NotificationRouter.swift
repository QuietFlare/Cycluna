import Foundation
import UserNotifications

/// The four root tabs, so a notification tap can pick one by name.
enum RootTab {
    case today, phases, journal, me
}

/// Turns a tapped reminder into a tab change. Must be installed as the notification-centre
/// delegate before launch finishes (see `iOSApp.init`), or a tap that cold-starts the app is
/// never delivered.
@Observable
final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationRouter()

    /// The tab the last tapped reminder wants; `RootView` consumes it and clears it.
    var pendingTab: RootTab?

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse) async {
        let id = response.notification.request.identifier
        pendingTab = ReminderManager.opensJournal(id: id) ? .journal : .today
    }
}
