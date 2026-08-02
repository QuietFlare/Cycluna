import SwiftUI

struct RootView: View {
    @Environment(CycleStore.self) private var store
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("appLockEnabled") private var lockEnabled = false
    @State private var lock = AppLock()

    private var locked: Bool { lockEnabled && !lock.isUnlocked }

    var body: some View {
        ZStack {
            Group {
                if store.hasLoggedPeriod {
                    mainTabs
                } else {
                    // No real period logged yet — show only the onboarding flow.
                    OnboardingView()
                }
            }

            if locked {
                LockScreen { lock.authenticate() }
                    .transition(.opacity)
            }
        }
        // Cycluna is a light-themed app. Pinned rather than offered as a choice: the
        // cream/mauve palette is the brand, and a theme switch is a setting nobody needs
        // to make. The launch assets are light-only to match.
        .preferredColorScheme(.light)
        .onAppear {
            if lockEnabled { lock.authenticate() } else { lock.isUnlocked = true }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                if locked { lock.authenticate() }
                refreshReminders()
            case .background:
                if lockEnabled { lock.lock() }
            default:
                break
            }
        }
        .onAppear(perform: refreshReminders)
        // Logging a period moves the anchor, which moves every predicted date. Without this
        // the stale follow-up ("Did your period start?") would still fire tomorrow.
        .onChange(of: store.periodStarts.count) { _, _ in refreshReminders() }
    }

    /// Keep the local reminders aligned with the latest predictions.
    private func refreshReminders() {
        let settings = ReminderSettings.current
        guard settings.anyCycleReminder || settings.anyCheckIn else {
            ReminderManager.cancelAll()
            return
        }
        let logged = store.hasLoggedPeriod
        ReminderManager.reschedule(
            nextPeriod: logged ? store.nextPeriodDate : nil,
            fertileStart: logged ? store.fertileStartDate : nil,
            settings: settings)
    }

    private var mainTabs: some View {
        TabView {
            HomeView()
                .tabItem { Label("Today", systemImage: "moon.stars.fill") }
            PhasesView()
                .tabItem { Label("Phases", systemImage: "chart.xyaxis.line") }
            JournalView()
                .tabItem { Label("Journal", systemImage: "book.closed.fill") }
            MeView()
                .tabItem { Label("Me", systemImage: "person.crop.circle.fill") }
        }
        .tint(Theme.primary)
    }
}
