import SwiftUI

/// Identifiable wrapper so a file URL can drive `.sheet(item:)`.
private struct ExportItem: Identifiable {
    let id = UUID()
    let url: URL
}

/// Bridges UIKit's share sheet into SwiftUI for exporting the data file.
private struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct MeView: View {
    @Environment(CycleStore.self) private var store
    @AppStorage("appTheme") private var themeRaw = AppTheme.system.rawValue
    @AppStorage("appLockEnabled") private var lockEnabled = false
    @AppStorage("periodReminders") private var periodReminders = false
    @AppStorage("ovulationReminders") private var ovulationReminders = false
    @State private var confirmDelete = false
    @State private var exportItem: ExportItem?
    @State private var pendingExportURL: URL?

    var body: some View {
        @Bindable var store = store
        NavigationStack {
            Form {
                Section {
                    TextField("Your name", text: $store.displayName)
                        .textInputAutocapitalization(.words)
                } header: {
                    Text("You")
                } footer: {
                    Text("We'll greet you by this on Home. Leave it blank for \u{201C}beautiful\u{201D}. Stays on this device.")
                }

                Section {
                    DatePicker("Last period start", selection: $store.lastPeriodStart, displayedComponents: .date)
                    Stepper("Cycle length: \(store.cycleLengthSetting) days", value: $store.cycleLengthSetting, in: 21...45)
                    Stepper("Period length: \(store.periodLength) days", value: $store.periodLength, in: 2...10)
                } header: {
                    Text("Cycle")
                } footer: {
                    Text("Cycle length auto-adjusts from your logged periods once there's enough history; otherwise this setting is used.")
                }

                Section("Appearance") {
                    Picker("Theme", selection: $themeRaw) {
                        Text("System").tag(AppTheme.system.rawValue)
                        Text("Light").tag(AppTheme.light.rawValue)
                        Text("Dark").tag(AppTheme.dark.rawValue)
                    }
                }

                Section {
                    Toggle("Period reminders", isOn: $periodReminders)
                        .onChange(of: periodReminders) { _, _ in remindersChanged() }
                    Toggle("Ovulation reminders", isOn: $ovulationReminders)
                        .onChange(of: ovulationReminders) { _, _ in remindersChanged() }
                } header: {
                    Text("Reminders")
                } footer: {
                    Text("A gentle heads-up the day before your period and when your fertile window opens. Local only — no push server.")
                }

                Section {
                    Toggle("Require Face ID to open", isOn: $lockEnabled)
                    NavigationLink("Privacy Policy") {
                        Text("Privacy policy goes here.").padding()
                    }
                } header: {
                    Text("Privacy")
                } footer: {
                    Text("Locks the app behind Face ID, Touch ID, or your device passcode — so your data stays private even on an unlocked phone.")
                }

                // Required by App Store Guideline 5.1.1(v) + GDPR/CCPA. All on-device.
                Section {
                    Button("Export my data") {
                        let item = store.exportFileURL().map(ExportItem.init)
                        pendingExportURL = item?.url
                        exportItem = item
                    }
                    Button("Delete all my data", role: .destructive) { confirmDelete = true }
                } header: {
                    Text("Your data")
                } footer: {
                    Text("Everything is stored only on this device. Export saves a JSON file you can share or keep; deleting erases it permanently.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Me")
            .confirmationDialog(
                "Delete all your data? This erases everything on this device and cannot be undone.",
                isPresented: $confirmDelete, titleVisibility: .visible
            ) {
                Button("Delete everything", role: .destructive) { store.deleteAllData() }
                Button("Cancel", role: .cancel) {}
            }
            .sheet(item: $exportItem, onDismiss: {
                if let url = pendingExportURL { try? FileManager.default.removeItem(at: url) }
                pendingExportURL = nil
            }) { item in
                ActivityView(items: [item.url])
            }
        }
    }

    /// Ask permission the first time a reminder is switched on, then (re)schedule.
    private func remindersChanged() {
        Task {
            if periodReminders || ovulationReminders {
                await ReminderManager.requestAuthorization()
            }
            let logged = store.hasLoggedPeriod
            ReminderManager.reschedule(
                nextPeriod: logged ? store.nextPeriodDate : nil,
                fertileStart: logged ? store.fertileStartDate : nil,
                periodOn: periodReminders,
                ovulationOn: ovulationReminders)
        }
    }
}
