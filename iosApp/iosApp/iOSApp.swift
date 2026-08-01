import SwiftUI

@main
struct iOSApp: App {
    @State private var store = CycleStore()
    @Environment(\.scenePhase) private var scenePhase

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
