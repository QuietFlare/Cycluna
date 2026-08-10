import SwiftUI
import UserNotifications

@main
struct iOSApp: App {
    @State private var store = CycleStore()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Installed here, before launch finishes, so tapping a reminder while the app is
        // closed still routes to the right tab once the UI is up.
        UNUserNotificationCenter.current().delegate = NotificationRouter.shared
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
        }
        .onChange(of: scenePhase) { _, phase in
            // Leaving the foreground: force any debounced save to disk now.
            if phase != .active { store.flush() }
        }
    }
}
